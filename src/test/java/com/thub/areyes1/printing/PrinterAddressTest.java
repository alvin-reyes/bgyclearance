package com.thub.areyes1.printing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.junit.jupiter.api.Test;

class PrinterAddressTest {

	@Test
	void plainAddressesTryTheCommonIppPaths() {
		assertThat(Printers.candidates(" 192.168.1.20 ")).containsExactly(
				URI.create("ipp://192.168.1.20:631/ipp/print"), URI.create("ipp://192.168.1.20:631/ipp"),
				URI.create("ipp://192.168.1.20:631/ipp/printer"), URI.create("ipp://192.168.1.20:631/"));
		assertThat(Printers.candidates("printer.local:8631").getFirst())
				.isEqualTo(URI.create("ipp://printer.local:8631/ipp/print"));
	}

	@Test
	void fullAddressesAreUsedAsGiven() {
		assertThat(Printers.candidates("ipp://10.0.0.5/printers/front")).containsExactly(URI.create("ipp://10.0.0.5/printers/front"));
		assertThat(Printers.candidates("http://10.0.0.5:631/ipp/print")).containsExactly(URI.create("ipp://10.0.0.5:631/ipp/print"));
		assertThat(Printers.candidates("ipp://10.0.0.5")).hasSize(4);
	}

	@Test
	void rejectsThingsThatAreNotAddresses() {
		assertThatThrownBy(() -> Printers.candidates("  ")).hasMessageContaining("Enter the printer's IP address");
		assertThatThrownBy(() -> Printers.candidates("not an address")).hasMessageContaining("doesn't look like a printer address");
		assertThatThrownBy(() -> Printers.candidates("10.0.0.5/ipp")).hasMessageContaining("doesn't look like");
	}
}
