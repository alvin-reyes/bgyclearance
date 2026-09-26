package com.thub.areyes1.web;

import static com.thub.areyes1.web.WebAppTest.click;
import static com.thub.areyes1.web.WebAppTest.text;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlRadioButtonInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.thub.areyes1.TestDatabase;
import com.thub.areyes1.clearance.Clearance;
import com.thub.areyes1.clearance.ClearanceRepository;
import com.thub.areyes1.clearance.ClearanceType;
import com.thub.areyes1.printing.FakeIppPrinter;

/** Adds a (fake) network printer through Settings and prints to it from the clearance page. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabase.FixedClock.class)
class PrintingWebTest {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		TestDatabase.register(registry);
	}

	@LocalServerPort
	int port;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ClearanceRepository clearances;

	WebClient browser;
	FakeIppPrinter printer;

	@BeforeEach
	void setUp() throws IOException {
		TestDatabase.clear(jdbc);
		printer = new FakeIppPrinter("/ipp/print");
		browser = new WebClient();
		browser.getOptions().setJavaScriptEnabled(false);
		browser.getOptions().setCssEnabled(false);
		browser.getOptions().setThrowExceptionOnFailingStatusCode(false);
	}

	@AfterEach
	void tearDown() {
		browser.close();
		printer.close();
	}

	HtmlPage open(String path) throws IOException {
		return browser.getPage("http://localhost:" + port + path);
	}

	HtmlPage addPrinter(String address) throws IOException {
		HtmlPage settings = open("/settings");
		HtmlForm add = settings.getFirstByXPath("//form[contains(@action,'/settings/printers/add')]");
		add.getInputByName("address").setValue(address);
		return click(settings, "add-printer");
	}

	HtmlPage makeDefault(HtmlPage settings, String printerName) throws IOException {
		HtmlRadioButtonInput radio = settings.getFirstByXPath(
				"//label[.//span[@class='printer-name' and text()='" + printerName + "']]/input");
		radio.setChecked(true);
		return click(settings, "save-printer");
	}

	@Test
	void withoutAPrinterThePrintButtonOpensThePdf() throws IOException {
		Clearance c = seed();
		HtmlPage detail = open("/clearances/" + c.id());

		assertThat(detail.getElementById("print-now")).isNull();
		assertThat(text(detail, "print")).isEqualTo("Print clearance");
		assertThat(text(open("/settings"), "no-printers")).contains("No printers found yet");
	}

	@Test
	void addChooseAndPrintToANetworkPrinter() throws IOException {
		HtmlPage added = addPrinter(printer.address());
		assertThat(text(added, "flash")).isEqualTo("Added Office LaserJet (127.0.0.1).");
		assertThat(added.asNormalizedText()).contains("Office LaserJet", "Added by address", "HP LaserJet Pro M404");

		HtmlPage chosen = makeDefault(added, "Office LaserJet");
		assertThat(text(chosen, "flash")).isEqualTo("Clearances will print to Office LaserJet.");

		Clearance c = seed();
		HtmlPage detail = open("/clearances/" + c.id());
		assertThat(text(detail, "print-now")).isEqualTo("Print to Office LaserJet");
		assertThat(text(detail, "print")).isEqualTo("Open PDF");

		HtmlPage printed = click(detail, "print-now");

		assertThat(text(printed, "flash")).isEqualTo("Sent to Office LaserJet (job 41).");
		FakeIppPrinter.Job job = printer.printJobs().getFirst();
		assertThat(job.attributes().get("job-name")).containsExactly("Clearance 7001 Tomas Store");
		assertThat(job.attributes().get("document-format")).containsExactly("application/pdf");
		try (PDDocument doc = Loader.loadPDF(job.document())) {
			assertThat(new PDFTextStripper().getText(doc)).contains("BARANGAY BUSINESS CLEARANCE", "TOMAS STORE");
		}
	}

	@Test
	void printsATestPage() throws IOException {
		HtmlPage settings = addPrinter(printer.address());

		HtmlPage result = ((org.htmlunit.html.HtmlElement) settings
				.getFirstByXPath("//button[contains(@formaction,'/settings/printers/test')]")).click();

		assertThat(text(result, "flash")).isEqualTo("Test page: Sent to Office LaserJet (job 41).");
		try (PDDocument doc = Loader.loadPDF(printer.printJobs().getFirst().document())) {
			assertThat(new PDFTextStripper().getText(doc)).contains("Printer test page", "Office LaserJet", "₱1,250.50");
		}
	}

	@Test
	void explainsWhenThePrinterIsUnreachableOrRefuses() throws IOException {
		makeDefault(addPrinter(printer.address()), "Office LaserJet");
		Clearance c = seed();

		printer.failWith(0x0506);
		HtmlPage busy = click(open("/clearances/" + c.id()), "print-now");
		assertThat(text(busy, "print-error")).isEqualTo("The printer is busy. Try again in a moment.");

		printer.close();
		HtmlPage down = click(open("/clearances/" + c.id()), "print-now");
		assertThat(text(down, "print-error")).startsWith("Could not connect to the printer at 127.0.0.1");
	}

	@Test
	void badOrSilentAddressesAreExplainedAndKept() throws IOException {
		HtmlPage bad = addPrinter("not an address");
		assertThat(text(bad, "print-error")).contains("doesn't look like a printer address");
		assertThat(((org.htmlunit.html.HtmlInput) bad.getElementById("address")).getValue()).isEqualTo("not an address");

		printer.close();
		HtmlPage silent = addPrinter(printer.address());
		assertThat(text(silent, "print-error")).startsWith("Could not connect to the printer at 127.0.0.1");
	}

	@Test
	void printersThatDontTakePdfCannotBeChosen() throws IOException {
		printer.acceptsPdf(false);

		HtmlPage settings = addPrinter(printer.address());

		assertThat(text(settings, "flash")).contains("doesn't accept PDF files");
		HtmlRadioButtonInput radio = settings.getFirstByXPath(
				"//label[.//span[@class='printer-name' and text()='Office LaserJet']]/input");
		assertThat(radio.isDisabled()).isTrue();
		assertThat(settings.asNormalizedText()).contains("Doesn't take PDF files directly");
	}

	@Test
	void removingTheDefaultPrinterFallsBackToThePdf() throws IOException {
		HtmlPage settings = makeDefault(addPrinter(printer.address()), "Office LaserJet");

		HtmlPage removed = ((org.htmlunit.html.HtmlElement) settings
				.getFirstByXPath("//button[contains(@formaction,'/settings/printers/remove')]")).click();

		assertThat(text(removed, "flash")).isEqualTo("Office LaserJet was removed.");
		assertThat(removed.getElementById("no-printers")).isNotNull();
		assertThat(open("/clearances/" + seed().id()).getElementById("print-now")).isNull();
	}

	Clearance seed() {
		return clearances.insert(new Clearance(null, ClearanceType.NEW, 7001, LocalDate.of(2026, 3, 1), "Tomas Store",
				"12 Rizal St.", null, null, null, false, false, false, false, null, null, null, null, null,
				new BigDecimal("250.50")));
	}
}
