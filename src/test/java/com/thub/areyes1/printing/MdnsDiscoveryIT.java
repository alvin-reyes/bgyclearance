package com.thub.areyes1.printing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import org.junit.jupiter.api.Test;

/**
 * Advertises a pretend printer over real mDNS and checks the scanner finds it.
 * Skipped on machines without a multicast-capable network interface.
 */
class MdnsDiscoveryIT {

	@Test
	void findsAPrinterAdvertisedOnTheLocalNetwork() throws Exception {
		List<InetAddress> addresses = NetworkPrinterDiscovery.localAddresses();
		assumeFalse(addresses.isEmpty(), "no multicast-capable network interface");

		try (JmDNS advertiser = JmDNS.create(addresses.getFirst(), "fake-printer")) {
			advertiser.registerService(ServiceInfo.create("_ipp._tcp.local.", "Test Printer 2F", 8631, 0, 0,
					Map.of("rp", "ipp/print", "ty", "Test Model 9000", "pdl", "application/pdf,image/urf")));

			NetworkPrinterDiscovery discovery = new NetworkPrinterDiscovery(true, Duration.ofSeconds(4));
			NetworkPrinterDiscovery.Scan scan = discovery.scanAsync().get();
			discovery.destroy();

			assertThat(scan.running()).isFalse();
			assertThat(scan.finishedAt()).isNotNull();
			Printer found = scan.printers().stream().filter(p -> p.name().equals("Test Printer 2F")).findFirst()
					.orElseThrow(() -> new AssertionError("not found; saw " + scan.printers()));
			assertThat(found.detail()).isEqualTo("Test Model 9000");
			assertThat(found.acceptsPdf()).isTrue();
			assertThat(found.uri().getPort()).isEqualTo(8631);
			assertThat(found.uri().getPath()).isEqualTo("/ipp/print");
		}
	}
}
