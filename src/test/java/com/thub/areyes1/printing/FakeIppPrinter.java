package com.thub.areyes1.printing;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;

/**
 * A pretend network printer for tests: speaks just enough IPP over HTTP to answer
 * Get-Printer-Attributes and accept Print-Job, and records every job it receives.
 */
public class FakeIppPrinter implements AutoCloseable {

	/** A job as the printer received it. */
	public record Job(int operation, Map<String, List<String>> attributes, byte[] document) {
	}

	private final HttpServer server;
	private final String path;
	private final List<Job> jobs = new CopyOnWriteArrayList<>();
	private volatile boolean acceptsPdf = true;
	private volatile int failWith = 0;
	private volatile String name = "Office LaserJet";

	public FakeIppPrinter(String path) throws IOException {
		this.path = path;
		this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext(path, exchange -> {
			byte[] body = exchange.getRequestBody().readAllBytes();
			Job job = parse(body);
			byte[] reply = reply(job);
			exchange.getResponseHeaders().add("Content-Type", "application/ipp");
			exchange.sendResponseHeaders(200, reply.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(reply);
			}
		});
		server.start();
	}

	public URI uri() {
		return URI.create("ipp://127.0.0.1:" + server.getAddress().getPort() + path);
	}

	/** "127.0.0.1:port", as a person would type it. */
	public String address() {
		return "127.0.0.1:" + server.getAddress().getPort();
	}

	public List<Job> jobs() {
		return jobs;
	}

	public List<Job> printJobs() {
		return jobs.stream().filter(j -> j.operation() == 0x0002).toList();
	}

	public FakeIppPrinter acceptsPdf(boolean value) {
		this.acceptsPdf = value;
		return this;
	}

	public FakeIppPrinter failWith(int ippStatus) {
		this.failWith = ippStatus;
		return this;
	}

	public FakeIppPrinter named(String value) {
		this.name = value;
		return this;
	}

	private byte[] reply(Job job) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		out.writeShort(0x0101);
		out.writeShort(failWith != 0 ? failWith : 0x0000);
		out.writeInt(1);
		out.writeByte(0x01);
		write(out, 0x47, "attributes-charset", "utf-8");
		write(out, 0x48, "attributes-natural-language", "en");
		if (failWith == 0 && job.operation() == 0x000B) {
			out.writeByte(0x04); // printer attributes
			write(out, 0x42, "printer-name", "fake");
			write(out, 0x41, "printer-info", name);
			write(out, 0x41, "printer-make-and-model", "HP LaserJet Pro M404");
			write(out, 0x41, "printer-location", "Front desk");
			write(out, 0x23, "printer-state", new byte[] { 0, 0, 0, 3 });
			write(out, 0x49, "document-format-supported", "application/octet-stream");
			write(out, 0x49, "", acceptsPdf ? "application/pdf" : "image/urf");
		}
		if (failWith == 0 && job.operation() == 0x0002) {
			jobs.add(job);
			out.writeByte(0x02); // job attributes
			write(out, 0x21, "job-id", new byte[] { 0, 0, 0, (byte) (40 + printJobs().size()) });
			write(out, 0x23, "job-state", new byte[] { 0, 0, 0, 3 });
		} else if (job.operation() != 0x0002) {
			jobs.add(job);
		}
		out.writeByte(0x03);
		return bytes.toByteArray();
	}

	private static void write(DataOutputStream out, int tag, String name, String value) throws IOException {
		write(out, tag, name, value.getBytes(StandardCharsets.UTF_8));
	}

	private static void write(DataOutputStream out, int tag, String name, byte[] value) throws IOException {
		out.writeByte(tag);
		byte[] n = name.getBytes(StandardCharsets.UTF_8);
		out.writeShort(n.length);
		out.write(n);
		out.writeShort(value.length);
		out.write(value);
	}

	/** Parses an IPP request: operation, attributes, and the document after the end tag. */
	static Job parse(byte[] body) throws IOException {
		DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(body));
		in.readUnsignedShort();
		int operation = in.readUnsignedShort();
		in.readInt();
		Map<String, List<String>> attrs = new LinkedHashMap<>();
		String current = null;
		while (true) {
			int tag = in.readUnsignedByte();
			if (tag == 0x03) {
				break;
			}
			if (tag < 0x10) {
				continue;
			}
			String name = new String(in.readNBytes(in.readUnsignedShort()), StandardCharsets.UTF_8);
			String value = new String(in.readNBytes(in.readUnsignedShort()), StandardCharsets.UTF_8);
			if (!name.isEmpty()) {
				current = name;
			}
			attrs.computeIfAbsent(current, k -> new ArrayList<>()).add(value);
		}
		return new Job(operation, attrs, in.readAllBytes());
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
