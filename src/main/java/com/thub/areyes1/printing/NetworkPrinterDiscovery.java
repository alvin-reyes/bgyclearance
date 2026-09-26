package com.thub.areyes1.printing;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Finds IPP printers on the local network the way phones and laptops do (mDNS /
 * DNS-SD, also called Bonjour or AirPrint discovery). Scans once when the app starts
 * and again whenever someone presses "Scan again"; results are kept until the next scan.
 */
@Component
public class NetworkPrinterDiscovery implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(NetworkPrinterDiscovery.class);

	static final String PREFIX = "ipp:";
	private static final String IPP = "_ipp._tcp.local.";
	private static final String IPPS = "_ipps._tcp.local.";

	/** Result of the latest scan. */
	public record Scan(List<Printer> printers, Instant finishedAt, boolean running) {
		static final Scan NEVER = new Scan(List.of(), null, false);
	}

	private final boolean enabled;
	private final Duration wait;
	private final IppClient ipp;
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
	private final AtomicReference<Scan> last = new AtomicReference<>(Scan.NEVER);
	private final AtomicReference<CompletableFuture<Scan>> running = new AtomicReference<>();

	public NetworkPrinterDiscovery(@Value("${bgy.printers.discovery:true}") boolean enabled,
			@Value("${bgy.printers.scan-time:4s}") Duration wait) {
		this.enabled = enabled;
		this.wait = wait;
		this.ipp = new IppClient(Duration.ofSeconds(3));
	}

	public boolean enabled() {
		return enabled;
	}

	public Scan lastScan() {
		return last.get();
	}

	@EventListener(ApplicationReadyEvent.class)
	void scanOnStartup() {
		if (enabled) {
			scanAsync();
		}
	}

	/** Starts a scan, or joins the one already running. */
	public CompletableFuture<Scan> scanAsync() {
		if (!enabled) {
			return CompletableFuture.completedFuture(last.get());
		}
		CompletableFuture<Scan> fresh = new CompletableFuture<>();
		CompletableFuture<Scan> existing = running.compareAndExchange(null, fresh);
		if (existing != null) {
			return existing;
		}
		last.updateAndGet(s -> new Scan(s.printers(), s.finishedAt(), true));
		executor.submit(() -> {
			try {
				Scan scan = new Scan(scan(), Instant.now(), false);
				last.set(scan);
				fresh.complete(scan);
			} catch (RuntimeException e) {
				log.warn("Printer scan failed: {}", e.getMessage());
				Scan scan = new Scan(last.get().printers(), Instant.now(), false);
				last.set(scan);
				fresh.complete(scan);
			} finally {
				running.set(null);
			}
		});
		return fresh;
	}

	/** Browses every network interface for IPP printers for {@code wait}. */
	List<Printer> scan() {
		List<InetAddress> addresses = localAddresses();
		Map<String, Printer> found = Collections.synchronizedMap(new LinkedHashMap<>());
		List<CompletableFuture<Void>> jobs = new ArrayList<>();
		for (InetAddress address : addresses) {
			jobs.add(CompletableFuture.runAsync(() -> browse(address, found), executor));
		}
		CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new)).join();
		// The DNS-SD record is only a hint (TXT records are size-limited and often list a
		// subset of formats), so ask each printer directly what it accepts.
		List<CompletableFuture<Printer>> confirmed = found.values().stream()
				.map(p -> CompletableFuture.supplyAsync(() -> confirm(p), executor))
				.toList();
		List<Printer> printers = new ArrayList<>(confirmed.stream().map(CompletableFuture::join).toList());
		printers.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
		log.info("Found {} network printer(s) on {} interface(s)", printers.size(), addresses.size());
		return printers;
	}

	private void browse(InetAddress address, Map<String, Printer> found) {
		try (JmDNS mdns = JmDNS.create(address)) {
			// Browse both service types at the same time; each waits for answers for `wait`.
			CompletableFuture<ServiceInfo[]> ipp = CompletableFuture.supplyAsync(() -> mdns.list(IPP, wait.toMillis()), executor);
			CompletableFuture<ServiceInfo[]> ipps = CompletableFuture.supplyAsync(() -> mdns.list(IPPS, wait.toMillis()), executor);
			// Plain IPP first: when a printer offers both, it is the one we can talk to.
			add(IPP, ipp.join(), found);
			add(IPPS, ipps.join(), found);
		} catch (IOException | RuntimeException e) {
			log.debug("mDNS browse on {} failed: {}", address, e.getMessage());
		}
	}

	/** Refines a discovered printer with what it reports about itself over IPP. */
	Printer confirm(Printer p) {
		if (!"ipp".equals(p.uri().getScheme())) {
			return p;
		}
		try {
			Map<String, List<String>> attrs = ipp.printerAttributes(p.uri());
			List<String> formats = attrs.getOrDefault("document-format-supported", List.of());
			Boolean pdf = formats.isEmpty() ? p.acceptsPdf() : formats.contains("application/pdf");
			String model = attrs.getOrDefault("printer-make-and-model", List.of()).stream().findFirst().orElse(null);
			String detail = InstalledPrinters.firstNonBlank(known(model), known(p.detail()), p.host());
			return new Printer(p.id(), p.name(), detail, p.source(), p.uri(), pdf);
		} catch (PrintFailure e) {
			log.debug("Could not confirm {}: {}", p.uri(), e.getMessage());
			return p;
		}
	}

	/** "Unknown" is what some printers put in their model name when they don't know it. */
	private static String known(String value) {
		return value == null || value.isBlank() || value.equalsIgnoreCase("unknown") ? null : value;
	}

	private static void add(String type, ServiceInfo[] infos, Map<String, Printer> found) {
		for (ServiceInfo info : infos) {
			Printer p = fromService(type, info);
			if (p != null) {
				found.merge(p.name().toLowerCase(Locale.ROOT), p,
						(a, b) -> "ipp".equals(a.uri().getScheme()) ? a : b);
			}
		}
	}

	private static Printer fromService(String type, ServiceInfo info) {
		Inet4Address[] v4 = info.getInet4Addresses();
		String host = v4.length > 0 ? v4[0].getHostAddress()
				: info.getInetAddresses().length > 0 ? info.getInetAddresses()[0].getHostAddress() : null;
		if (host == null) {
			return null;
		}
		Map<String, String> txt = new LinkedHashMap<>();
		for (var names = info.getPropertyNames(); names.hasMoreElements();) {
			String key = names.nextElement();
			txt.put(key.toLowerCase(Locale.ROOT), info.getPropertyString(key));
		}
		return toPrinter(IPPS.equals(type) ? "ipps" : "ipp", info.getName(), host, info.getPort(), txt);
	}

	/**
	 * Builds a printer from a DNS-SD record. TXT keys used: {@code rp} (resource path,
	 * e.g. "ipp/print"), {@code ty} (make and model), {@code note} (location) and
	 * {@code pdl} (document formats the printer accepts).
	 */
	static Printer toPrinter(String scheme, String instanceName, String host, int port, Map<String, String> txt) {
		String rp = txt.getOrDefault("rp", "ipp/print");
		String hostPart = host.contains(":") ? "[" + host + "]" : host;
		URI uri = URI.create(scheme + "://" + hostPart + ":" + (port > 0 ? port : 631) + "/" + rp.replaceFirst("^/", ""));
		String pdl = txt.get("pdl");
		Boolean pdf = pdl == null ? null : pdl.toLowerCase(Locale.ROOT).contains("application/pdf");
		if ("ipps".equals(scheme)) {
			pdf = Boolean.FALSE; // secure-only printers need to be added to the computer
		}
		String detail = InstalledPrinters.firstNonBlank(known(txt.get("ty")), txt.get("note"), host);
		return new Printer(PREFIX + uri, instanceName, detail, Printer.Source.DISCOVERED, uri, pdf);
	}

	/** Up, non-loopback IPv4 addresses that can do multicast: one mDNS browser each. */
	static List<InetAddress> localAddresses() {
		List<InetAddress> result = new ArrayList<>();
		try {
			for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (!nic.isUp() || nic.isLoopback() || !nic.supportsMulticast() || nic.isVirtual()) {
					continue;
				}
				for (InetAddress a : Collections.list(nic.getInetAddresses())) {
					if (a instanceof Inet4Address) {
						result.add(a);
					}
				}
			}
		} catch (SocketException e) {
			log.debug("Could not list network interfaces: {}", e.getMessage());
		}
		return result;
	}

	@Override
	public void destroy() {
		executor.shutdownNow();
	}
}
