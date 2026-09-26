package com.thub.areyes1.printing;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;

class NetworkPrinterDiscoveryTest {

	@Test
	void buildsAPrinterFromADnsSdRecord() {
		Printer p = NetworkPrinterDiscovery.toPrinter("ipp", "HP LaserJet M404 [A1B2C3]", "192.168.1.20", 631,
				Map.of("rp", "ipp/print", "ty", "HP LaserJet Pro M404", "note", "Front desk",
						"pdl", "application/octet-stream,application/pdf,image/urf"));

		assertThat(p.uri()).isEqualTo(URI.create("ipp://192.168.1.20:631/ipp/print"));
		assertThat(p.name()).isEqualTo("HP LaserJet M404 [A1B2C3]");
		assertThat(p.detail()).isEqualTo("HP LaserJet Pro M404");
		assertThat(p.source()).isEqualTo(Printer.Source.DISCOVERED);
		assertThat(p.acceptsPdf()).isTrue();
		assertThat(p.id()).isEqualTo("ipp:ipp://192.168.1.20:631/ipp/print");
	}

	@Test
	void handlesMissingTxtKeysAndPdfOnlyViaDriver() {
		Printer unknown = NetworkPrinterDiscovery.toPrinter("ipp", "Epson", "10.0.0.5", 0, Map.of());
		assertThat(unknown.uri()).isEqualTo(URI.create("ipp://10.0.0.5:631/ipp/print"));
		assertThat(unknown.acceptsPdf()).isNull();
		assertThat(unknown.canPrint()).isTrue();
		assertThat(unknown.detail()).isEqualTo("10.0.0.5");

		Printer rasterOnly = NetworkPrinterDiscovery.toPrinter("ipp", "Brother", "10.0.0.6", 631,
				Map.of("rp", "/ipp", "pdl", "image/urf,image/pwg-raster"));
		assertThat(rasterOnly.uri().getPath()).isEqualTo("/ipp");
		assertThat(rasterOnly.canPrint()).isFalse();

		Printer secureOnly = NetworkPrinterDiscovery.toPrinter("ipps", "Canon", "10.0.0.7", 443,
				Map.of("pdl", "application/pdf"));
		assertThat(secureOnly.canPrint()).isFalse();
	}

	@Test
	void asksThePrinterWhatItAcceptsBecauseTheRecordIsOnlyAHint() throws Exception {
		try (FakeIppPrinter printer = new FakeIppPrinter("/printers/front")) {
			// Record says octet-stream only and "Unknown" model, like a CUPS raw queue.
			Printer hinted = NetworkPrinterDiscovery.toPrinter("ipp", "Front Desk @ hall", "127.0.0.1",
					printer.uri().getPort(), Map.of("rp", "printers/front", "ty", "Unknown", "pdl", "application/octet-stream"));
			assertThat(hinted.canPrint()).isFalse();
			assertThat(hinted.detail()).isEqualTo("127.0.0.1");

			Printer confirmed = new NetworkPrinterDiscovery(true, java.time.Duration.ofSeconds(1)).confirm(hinted);

			assertThat(confirmed.acceptsPdf()).isTrue();
			assertThat(confirmed.detail()).isEqualTo("HP LaserJet Pro M404");
		}
	}

	@Test
	void keepsTheHintWhenThePrinterDoesNotAnswer() {
		Printer hinted = NetworkPrinterDiscovery.toPrinter("ipp", "Gone", "127.0.0.1", 1, Map.of("pdl", "image/urf"));

		assertThat(new NetworkPrinterDiscovery(true, java.time.Duration.ofSeconds(1)).confirm(hinted)).isEqualTo(hinted);
	}

	@Test
	void disabledDiscoveryFindsNothingAndReturnsImmediately() {
		NetworkPrinterDiscovery off = new NetworkPrinterDiscovery(false, java.time.Duration.ofSeconds(4));

		assertThat(off.scanAsync().join().printers()).isEmpty();
		assertThat(off.lastScan().finishedAt()).isNull();
	}
}
