package com.thub.areyes1.printing;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A minimal client for the Internet Printing Protocol (IPP/1.1, RFC 8010/8011): enough
 * to ask a network printer about itself and to send it a PDF. Most printers made in
 * the last decade speak IPP on port 631 ("IPP Everywhere" / AirPrint).
 */
public class IppClient {

	private static final int PRINT_JOB = 0x0002;
	private static final int GET_PRINTER_ATTRIBUTES = 0x000B;

	private static final int TAG_OPERATION = 0x01;
	private static final int TAG_END = 0x03;
	private static final int TAG_INTEGER = 0x21;
	private static final int TAG_BOOLEAN = 0x22;
	private static final int TAG_ENUM = 0x23;
	private static final int TAG_KEYWORD = 0x44;
	private static final int TAG_URI = 0x45;
	private static final int TAG_CHARSET = 0x47;
	private static final int TAG_LANGUAGE = 0x48;
	private static final int TAG_MIME = 0x49;
	private static final int TAG_NAME = 0x42;

	private static final AtomicInteger REQUEST_IDS = new AtomicInteger(1);

	private final HttpClient http;
	private final Duration timeout;

	public IppClient(Duration timeout) {
		this.timeout = timeout;
		this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
	}

	/** Printer attributes such as printer-make-and-model and document-format-supported. */
	public Map<String, List<String>> printerAttributes(URI printer) {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(body)) {
			header(out, GET_PRINTER_ATTRIBUTES, printer);
			List<String> wanted = List.of("printer-name", "printer-make-and-model", "printer-location",
					"printer-info", "printer-state", "document-format-supported");
			for (int i = 0; i < wanted.size(); i++) {
				// Extra values of a multi-valued attribute are written with an empty name.
				attribute(out, TAG_KEYWORD, i == 0 ? "requested-attributes" : "", wanted.get(i));
			}
			out.writeByte(TAG_END);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return send(printer, body.toByteArray()).attributes();
	}

	/** Sends a PDF to the printer and returns the job id it assigned. */
	public int printPdf(URI printer, byte[] pdf, String jobName, String user) {
		ByteArrayOutputStream body = new ByteArrayOutputStream(pdf.length + 512);
		try (DataOutputStream out = new DataOutputStream(body)) {
			header(out, PRINT_JOB, printer);
			attribute(out, TAG_NAME, "requesting-user-name", user);
			attribute(out, TAG_NAME, "job-name", jobName);
			attribute(out, TAG_MIME, "document-format", "application/pdf");
			out.writeByte(TAG_END);
			out.write(pdf);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		Response response = send(printer, body.toByteArray());
		List<String> jobId = response.attributes().get("job-id");
		return jobId == null || jobId.isEmpty() ? 0 : Integer.parseInt(jobId.getFirst());
	}

	private static void header(DataOutputStream out, int operation, URI printer) throws IOException {
		out.writeByte(1); // version 1.1
		out.writeByte(1);
		out.writeShort(operation);
		out.writeInt(REQUEST_IDS.getAndIncrement());
		out.writeByte(TAG_OPERATION);
		attribute(out, TAG_CHARSET, "attributes-charset", "utf-8");
		attribute(out, TAG_LANGUAGE, "attributes-natural-language", "en");
		attribute(out, TAG_URI, "printer-uri", printer.toString());
	}

	private static void attribute(DataOutputStream out, int tag, String name, String value) throws IOException {
		byte[] n = name.getBytes(StandardCharsets.UTF_8);
		byte[] v = value.getBytes(StandardCharsets.UTF_8);
		out.writeByte(tag);
		out.writeShort(n.length);
		out.write(n);
		out.writeShort(v.length);
		out.write(v);
	}

	private Response send(URI printer, byte[] body) {
		HttpRequest request = HttpRequest.newBuilder(httpUri(printer))
				.timeout(timeout.multipliedBy(6))
				.header("Content-Type", "application/ipp")
				.POST(HttpRequest.BodyPublishers.ofByteArray(body))
				.build();
		HttpResponse<byte[]> response;
		try {
			response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
		} catch (ConnectException e) {
			throw new PrintFailure("Could not connect to the printer at " + printer.getHost()
					+ ". Check that it is switched on and connected to the network.", e);
		} catch (HttpTimeoutException e) {
			throw new PrintFailure("The printer at " + printer.getHost() + " did not answer in time.", e);
		} catch (IOException e) {
			throw new PrintFailure("Could not talk to the printer at " + printer.getHost() + ": " + e.getMessage(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new PrintFailure("Printing was interrupted.", e);
		}
		if (response.statusCode() != 200) {
			throw new PrintFailure("The printer at " + printer.getHost() + " answered HTTP " + response.statusCode()
					+ ". It may not support IPP at " + printer.getPath() + ".", null);
		}
		Response parsed = parse(response.body());
		if (parsed.status() >= 0x0100) {
			throw new PrintFailure(describe(parsed.status()), null);
		}
		return parsed;
	}

	/** ipp://host:631/ipp/print is sent as an HTTP POST to http://host:631/ipp/print. */
	static URI httpUri(URI ipp) {
		String scheme = "ipps".equalsIgnoreCase(ipp.getScheme()) ? "https" : "http";
		int port = ipp.getPort() == -1 ? 631 : ipp.getPort();
		String path = ipp.getRawPath() == null || ipp.getRawPath().isEmpty() ? "/" : ipp.getRawPath();
		return URI.create(scheme + "://" + ipp.getHost() + ":" + port + path);
	}

	record Response(int status, Map<String, List<String>> attributes) {
	}

	/** Parses an IPP response: status code and every attribute (all groups flattened). */
	static Response parse(byte[] bytes) {
		try (DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(bytes))) {
			in.readUnsignedShort(); // version
			int status = in.readUnsignedShort();
			in.readInt(); // request id
			Map<String, List<String>> attributes = new LinkedHashMap<>();
			String current = null;
			while (in.available() > 0) {
				int tag = in.readUnsignedByte();
				if (tag == TAG_END) {
					break;
				}
				if (tag < 0x10) {
					continue; // start of an attribute group
				}
				String name = new String(in.readNBytes(in.readUnsignedShort()), StandardCharsets.UTF_8);
				byte[] value = in.readNBytes(in.readUnsignedShort());
				if (!name.isEmpty()) {
					current = name;
				}
				if (current != null) {
					attributes.computeIfAbsent(current, k -> new ArrayList<>()).add(decode(tag, value));
				}
			}
			return new Response(status, attributes);
		} catch (IOException e) {
			throw new PrintFailure("The printer sent a response that could not be read.", e);
		}
	}

	private static String decode(int tag, byte[] value) {
		if ((tag == TAG_INTEGER || tag == TAG_ENUM) && value.length == 4) {
			return String.valueOf(((value[0] & 0xff) << 24) | ((value[1] & 0xff) << 16) | ((value[2] & 0xff) << 8)
					| (value[3] & 0xff));
		}
		if (tag == TAG_BOOLEAN && value.length == 1) {
			return value[0] == 0 ? "false" : "true";
		}
		return new String(value, StandardCharsets.UTF_8);
	}

	private static String describe(int status) {
		return switch (status) {
			case 0x0400 -> "The printer rejected the request.";
			case 0x0401, 0x0402, 0x0403 -> "The printer refused the job: it requires a login or permission.";
			case 0x0406 -> "The printer at that address was not found.";
			case 0x040A -> "This printer does not accept PDF files. Add it to the computer's printers instead.";
			case 0x0500 -> "The printer reported an internal error.";
			case 0x0506 -> "The printer is busy. Try again in a moment.";
			case 0x0507 -> "The printer is not accepting jobs right now.";
			default -> "The printer reported an error (IPP status 0x" + Integer.toHexString(status) + ").";
		};
	}
}
