package com.thub.areyes1.printing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IppClientTest {

	FakeIppPrinter printer;
	IppClient client = new IppClient(Duration.ofSeconds(2));

	@BeforeEach
	void start() throws IOException {
		printer = new FakeIppPrinter("/ipp/print");
	}

	@AfterEach
	void stop() {
		printer.close();
	}

	@Test
	void readsPrinterAttributes() {
		Map<String, List<String>> attrs = client.printerAttributes(printer.uri());

		assertThat(attrs.get("printer-info")).containsExactly("Office LaserJet");
		assertThat(attrs.get("printer-make-and-model")).containsExactly("HP LaserJet Pro M404");
		assertThat(attrs.get("printer-state")).containsExactly("3");
		assertThat(attrs.get("document-format-supported")).containsExactly("application/octet-stream", "application/pdf");

		FakeIppPrinter.Job request = printer.jobs().getFirst();
		assertThat(request.operation()).isEqualTo(0x000B);
		assertThat(request.attributes().get("printer-uri")).containsExactly(printer.uri().toString());
		assertThat(request.attributes().get("requested-attributes")).contains("printer-make-and-model",
				"document-format-supported");
	}

	@Test
	void sendsThePdfAsAPrintJob() {
		byte[] pdf = "%PDF-1.7 pretend".getBytes(StandardCharsets.ISO_8859_1);

		int jobId = client.printPdf(printer.uri(), pdf, "Clearance 2026001", "clerk");

		assertThat(jobId).isEqualTo(41);
		FakeIppPrinter.Job job = printer.printJobs().getFirst();
		assertThat(job.attributes().get("attributes-charset")).containsExactly("utf-8");
		assertThat(job.attributes().get("document-format")).containsExactly("application/pdf");
		assertThat(job.attributes().get("job-name")).containsExactly("Clearance 2026001");
		assertThat(job.attributes().get("requesting-user-name")).containsExactly("clerk");
		assertThat(job.document()).isEqualTo(pdf);
	}

	@Test
	void explainsPrinterErrorsInPlainWords() {
		printer.failWith(0x040A);
		assertThatThrownBy(() -> client.printPdf(printer.uri(), new byte[] { 1 }, "x", "y"))
				.isInstanceOf(PrintFailure.class).hasMessageContaining("does not accept PDF");

		printer.failWith(0x0506);
		assertThatThrownBy(() -> client.printPdf(printer.uri(), new byte[] { 1 }, "x", "y"))
				.hasMessageContaining("busy");
	}

	@Test
	void explainsWhenNothingIsListening() throws IOException {
		int freePort;
		try (ServerSocket s = new ServerSocket(0)) {
			freePort = s.getLocalPort();
		}
		URI nowhere = URI.create("ipp://127.0.0.1:" + freePort + "/ipp/print");

		assertThatThrownBy(() -> client.printerAttributes(nowhere))
				.isInstanceOf(PrintFailure.class).hasMessageContaining("Could not connect to the printer at 127.0.0.1");
	}

	@Test
	void mapsIppAddressesToHttp() {
		assertThat(IppClient.httpUri(URI.create("ipp://192.168.1.20/ipp/print")))
				.isEqualTo(URI.create("http://192.168.1.20:631/ipp/print"));
		assertThat(IppClient.httpUri(URI.create("ipps://printer.local:443/ipp")))
				.isEqualTo(URI.create("https://printer.local:443/ipp"));
	}
}
