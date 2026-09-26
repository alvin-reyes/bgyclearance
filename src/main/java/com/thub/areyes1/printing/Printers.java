package com.thub.areyes1.printing;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.thub.areyes1.settings.SettingsRepository;

/**
 * Every printer the app knows about, from three places: printers installed on this
 * computer, printers found on the network, and printers someone added by IP address.
 * Also remembers which one is the default for printing clearances.
 */
@Service
public class Printers {

	static final String ADDED_PREFIX = "added:";
	private static final String DEFAULT_KEY = "default_printer";
	private static final String ADDED_KEY = "added_printers";
	/** Paths printers commonly serve IPP on, tried in order when adding by address. */
	private static final List<String> COMMON_PATHS = List.of("/ipp/print", "/ipp", "/ipp/printer", "/");

	private final InstalledPrinters installed;
	private final NetworkPrinterDiscovery discovery;
	private final SettingsRepository settings;
	private final IppClient ipp;
	private final String jobUser;

	public Printers(InstalledPrinters installed, NetworkPrinterDiscovery discovery, SettingsRepository settings,
			@Value("${bgy.printers.timeout:3s}") Duration timeout) {
		this.installed = installed;
		this.discovery = discovery;
		this.settings = settings;
		this.ipp = new IppClient(timeout);
		this.jobUser = System.getProperty("user.name", "barangay");
	}

	/** Installed printers first, then network printers, without duplicates. */
	public List<Printer> list() {
		Map<String, Printer> byKey = new LinkedHashMap<>();
		for (Printer p : installed.list()) {
			byKey.putIfAbsent(p.id(), p);
		}
		for (Printer p : added()) {
			byKey.putIfAbsent(p.uri().toString(), p);
		}
		for (Printer p : discovery.lastScan().printers()) {
			byKey.putIfAbsent(p.uri().toString(), p); // an added printer with the same address wins
		}
		return new ArrayList<>(byKey.values());
	}

	public Optional<Printer> find(String id) {
		return list().stream().filter(p -> p.id().equals(id)).findFirst();
	}

	public Optional<Printer> defaultPrinter() {
		return settings.get(DEFAULT_KEY).flatMap(this::find);
	}

	/** The saved default printer id, even if that printer can't be seen right now. */
	public Optional<String> defaultPrinterId() {
		return settings.get(DEFAULT_KEY);
	}

	public void setDefault(String id) {
		if (id != null && !id.isBlank() && find(id).isEmpty()) {
			throw new PrintFailure("That printer is no longer available. Scan again and choose another.", null);
		}
		settings.put(DEFAULT_KEY, id == null ? "" : id);
	}

	/**
	 * Adds a network printer by IP address or host name (optionally with a port or a
	 * full ipp:// address). Asks the printer for its name and whether it takes PDF.
	 */
	public Printer addByAddress(String address) {
		List<URI> candidates = candidates(address);
		PrintFailure lastFailure = null;
		for (URI uri : candidates) {
			try {
				Map<String, List<String>> attrs = ipp.printerAttributes(uri);
				String name = InstalledPrinters.firstNonBlank(first(attrs, "printer-info"), first(attrs, "printer-name"),
						first(attrs, "printer-make-and-model"), uri.getHost());
				String detail = InstalledPrinters.firstNonBlank(first(attrs, "printer-make-and-model"),
						first(attrs, "printer-location"), uri.getHost());
				List<String> formats = attrs.getOrDefault("document-format-supported", List.of());
				Boolean pdf = formats.isEmpty() ? null : formats.contains("application/pdf");
				Printer printer = new Printer(ADDED_PREFIX + uri, name, detail, Printer.Source.ADDED, uri, pdf);
				saveAdded(printer);
				return printer;
			} catch (PrintFailure e) {
				lastFailure = e;
				if (e.getMessage().startsWith("Could not connect") || e.getMessage().contains("did not answer")) {
					break; // nothing is listening; other paths won't help
				}
			}
		}
		throw lastFailure != null ? lastFailure
				: new PrintFailure("No printer answered at " + address + ".", null);
	}

	public void remove(String id) {
		List<Printer> kept = added().stream().filter(p -> !p.id().equals(id)).toList();
		settings.put(ADDED_KEY, encode(kept));
		if (defaultPrinterId().filter(id::equals).isPresent()) {
			settings.put(DEFAULT_KEY, "");
		}
	}

	/** Sends a PDF to the printer. Returns a short confirmation for the person who printed. */
	public String print(String id, byte[] pdf, String jobName) {
		Printer printer = find(id).orElseThrow(() -> new PrintFailure(
				"The chosen printer can't be found. Check it is on, or choose another printer in Settings.", null));
		if (!printer.canPrint()) {
			throw new PrintFailure(printer.name() + " doesn't accept PDF files directly. "
					+ "Add it to this computer's printers, then choose it from the \"Installed\" list.", null);
		}
		if (printer.source() == Printer.Source.INSTALLED) {
			installed.print(printer.name(), pdf, jobName);
			return "Sent to " + printer.name() + ".";
		}
		int job = ipp.printPdf(printer.uri(), pdf, jobName, jobUser);
		return "Sent to " + printer.name() + (job > 0 ? " (job " + job + ")." : ".");
	}

	// ---- added printers, stored one per line: uri \t name \t detail \t pdf ---------------

	List<Printer> added() {
		List<Printer> result = new ArrayList<>();
		for (String line : settings.get(ADDED_KEY).orElse("").split("\n")) {
			String[] f = line.split("\t", -1);
			if (f.length == 4 && !f[0].isBlank()) {
				URI uri = URI.create(f[0]);
				Boolean pdf = f[3].isEmpty() ? null : Boolean.valueOf(f[3]);
				result.add(new Printer(ADDED_PREFIX + uri, f[1], f[2], Printer.Source.ADDED, uri, pdf));
			}
		}
		return result;
	}

	private void saveAdded(Printer printer) {
		List<Printer> all = new ArrayList<>(added().stream().filter(p -> !p.uri().equals(printer.uri())).toList());
		all.add(printer);
		settings.put(ADDED_KEY, encode(all));
	}

	private static String encode(List<Printer> printers) {
		StringBuilder sb = new StringBuilder();
		for (Printer p : printers) {
			sb.append(p.uri()).append('\t').append(clean(p.name())).append('\t').append(clean(p.detail())).append('\t')
					.append(p.acceptsPdf() == null ? "" : p.acceptsPdf()).append('\n');
		}
		return sb.toString();
	}

	private static String clean(String s) {
		return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ');
	}

	/** "192.168.1.20", "printer.local:631" or "ipp://192.168.1.20/ipp/print" -> addresses to try. */
	static List<URI> candidates(String address) {
		String a = address == null ? "" : address.strip();
		if (a.isEmpty()) {
			throw new PrintFailure("Enter the printer's IP address, for example 192.168.1.20.", null);
		}
		try {
			if (a.matches("(?i)^(ipp|ipps|http|https)://.*")) {
				// http://host/ipp/print and https:// are the same IPP endpoints as ipp:// and ipps://
				URI given = new URI(a.replaceFirst("(?i)^http", "ipp"));
				if (given.getHost() == null) {
					throw new URISyntaxException(a, "no host");
				}
				String path = given.getPath();
				if (path != null && !path.isEmpty() && !path.equals("/")) {
					return List.of(given);
				}
				return COMMON_PATHS.stream().map(p -> given.resolve(p)).toList();
			}
			URI base = new URI("ipp://" + a);
			if (base.getHost() == null || a.contains("/")) {
				throw new URISyntaxException(a, "not a host");
			}
			String hostPort = base.getHost() + ":" + (base.getPort() == -1 ? 631 : base.getPort());
			return COMMON_PATHS.stream().map(p -> URI.create("ipp://" + hostPort + p)).toList();
		} catch (URISyntaxException | IllegalArgumentException e) {
			throw new PrintFailure("\"" + a + "\" doesn't look like a printer address. Use an IP address such as 192.168.1.20.", e);
		}
	}

	private static String first(Map<String, List<String>> attrs, String name) {
		List<String> values = attrs.get(name);
		return values == null || values.isEmpty() ? null : values.getFirst();
	}
}
