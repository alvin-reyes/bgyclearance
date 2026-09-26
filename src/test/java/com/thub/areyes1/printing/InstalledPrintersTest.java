package com.thub.areyes1.printing;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.print.PrinterIOException;
import java.io.IOException;

import org.junit.jupiter.api.Test;

class InstalledPrintersTest {

	@Test
	void explainsTheRootCauseInsteadOfNull() {
		PrinterIOException wrapped = new PrinterIOException(new IOException("Printer is offline"));

		assertThat(wrapped.getMessage()).isNull();
		assertThat(InstalledPrinters.explain(wrapped)).isEqualTo("Printer is offline");
	}

	@Test
	void explainsAMissingLprCommand() {
		PrinterIOException wrapped = new PrinterIOException(
				new IOException("Cannot run program \"/usr/bin/lpr\": error=2, No such file or directory"));

		assertThat(InstalledPrinters.explain(wrapped)).contains("missing the \"lpr\" command", "cups-bsd");
	}
}
