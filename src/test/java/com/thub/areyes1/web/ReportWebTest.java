package com.thub.areyes1.web;

import static com.thub.areyes1.web.WebAppTest.click;
import static com.thub.areyes1.web.WebAppTest.text;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlRadioButtonInput;
import org.htmlunit.html.HtmlSelect;
import org.htmlunit.html.HtmlTableRow;
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
import com.thub.areyes1.settings.BarangaySettings;
import com.thub.areyes1.settings.SettingsRepository;

/** Opens reports for different periods, downloads them, and prints one to a (fake) network printer. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabase.FixedClock.class)
class ReportWebTest {

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

	@Autowired
	SettingsRepository settings;

	WebClient browser;

	@BeforeEach
	void setUp() {
		TestDatabase.clear(jdbc);
		settings.save(new BarangaySettings("San Isidro", "Municipality of Liloan", "Cebu", "Hon. Ramon Cruz", "Liza Tan"));
		browser = new WebClient();
		browser.getOptions().setJavaScriptEnabled(false);
		browser.getOptions().setCssEnabled(false);
		browser.getOptions().setThrowExceptionOnFailingStatusCode(false);
	}

	@AfterEach
	void tearDown() {
		browser.close();
	}

	<P extends Page> P open(String path) throws IOException {
		return browser.getPage("http://localhost:" + port + path);
	}

	Clearance add(String issuedOn, ClearanceType type, int controlNo, String name, String business, String amount) {
		return clearances.insert(new Clearance(null, type, controlNo, issuedOn == null ? null : LocalDate.parse(issuedOn),
				name, "12 Rizal St.", business, null, null, false, true, false, false, "Juan Dela Cruz", null, null,
				null, 556600 + controlNo, new BigDecimal(amount)));
	}

	/** Today in tests is March 15, 2026. */
	void seed() {
		add("2026-02-27", ClearanceType.NEW, 1, "February Store", "Sari-sari store", "100");
		add("2026-03-01", ClearanceType.NEW, 2, "Tomas Store", "Sari-sari store", "250.50");
		add("2026-03-01", ClearanceType.RENEWAL, 3, "Lola Eatery", "Eatery", "300");
		add("2026-03-14", ClearanceType.NEW, 4, "Rizal Pharmacy", "Pharmacy", "1000");
		add("2026-04-01", ClearanceType.NEW, 5, "April Store", "Sari-sari store", "100");
		add(null, ClearanceType.RENEWAL, 6, "Old Record", "Bakery", "50");
	}

	static List<String> names(HtmlPage page) {
		List<HtmlTableRow> rows = page.getByXPath("//table[@id='report-clearances']/tbody/tr");
		return rows.stream().map(r -> r.getCell(2).getFirstElementChild().asNormalizedText()).toList();
	}

	@Test
	void theNavigationOpensThisMonthsReport() throws IOException {
		seed();
		HtmlPage dashboard = open("/");
		HtmlPage report = dashboard.getAnchorByText("Reports").click();

		assertThat(text(report, "report-period")).isEqualTo("March 2026");
		assertThat(text(report, "report-scope")).isEqualTo("All clearances · 3 clearances issued");
		assertThat(names(report)).containsExactly("Tomas Store", "Lola Eatery", "Rizal Pharmacy");
		assertThat(text(report, "report-total")).isEqualTo("₱1,550.50");
		assertThat(text(report, "stat-collected")).contains("₱1,550.50", "average ₱516.83");
		assertThat(text(report, "stat-new")).contains("2", "₱1,250.50 collected");
		assertThat(text(report, "stat-renewal")).contains("1", "₱300.00 collected");
		assertThat(text(report, "undated")).startsWith("1 older clearance has no date issued");
		assertThat(report.getAnchorByText("This month").getAttribute("aria-current")).isEqualTo("true");

		List<HtmlTableRow> types = report.getByXPath("//table[@id='business-types']/tbody/tr");
		assertThat(types).extracting(r -> r.asNormalizedText().replaceAll("\\s+", " ")).containsExactly(
				"Pharmacy 1 33% ₱1,000.00", "Eatery 1 33% ₱300.00", "Sari-sari store 1 33% ₱250.50");

		List<HtmlTableRow> days = report.getByXPath("//table[@id='buckets']/tbody/tr");
		assertThat(days).hasSize(31);
		assertThat(days.getFirst().asNormalizedText().replaceAll("\\s+", " ")).isEqualTo("Sun, Mar 1, 2026 2 ₱550.50");
		assertThat(report.getByXPath("//div[contains(@class,'bar-slot')]")).hasSize(31);
	}

	@Test
	void presetsAndCustomRanges() throws IOException {
		seed();
		HtmlPage lastMonth = ((HtmlPage) open("/reports")).getAnchorByText("Last month").click();
		assertThat(text(lastMonth, "report-period")).isEqualTo("February 2026");
		assertThat(names(lastMonth)).containsExactly("February Store");

		HtmlPage year = lastMonth.getAnchorByText("This year").click();
		assertThat(text(year, "report-period")).isEqualTo("2026");
		assertThat(text(year, "chart-heading")).isEqualTo("Collections by month");
		assertThat(names(year)).hasSize(5);

		HtmlForm range = year.getFirstByXPath("//form[@class='range']");
		range.getInputByName("from").setValue("2026-03-01");
		range.getInputByName("to").setValue("2026-02-27");
		((HtmlSelect) range.getSelectByName("type")).setSelectedAttribute("NEW", true);
		HtmlPage custom = click(year, "apply");

		assertThat(text(custom, "report-period")).isEqualTo("Feb 27, 2026 – Mar 1, 2026");
		assertThat(text(custom, "report-scope")).isEqualTo("New businesses · 2 clearances issued");
		assertThat(names(custom)).containsExactly("February Store", "Tomas Store");
		// The presets keep the chosen type.
		assertThat(custom.getAnchorByText("Today").getHrefAttribute()).isEqualTo("/reports?from=2026-03-15&to=2026-03-15&type=NEW");
	}

	@Test
	void aPeriodWithNothingIssuedSaysSo() throws IOException {
		seed();
		HtmlPage empty = open("/reports?from=2025-01-01&to=2025-01-31");

		assertThat(text(empty, "empty")).startsWith("No clearances were issued in January 2025.");
		assertThat(empty.getElementById("report-clearances")).isNull();
		assertThat(empty.getElementById("collections")).isNull();
	}

	@Test
	void theReportOpensAsAPdf() throws IOException {
		seed();
		HtmlPage report = open("/reports?from=2026-01-01&to=2026-12-31");
		HtmlAnchor pdfLink = (HtmlAnchor) report.getElementById("report-pdf");
		WebResponse pdf = ((Page) open(pdfLink.getHrefAttribute())).getWebResponse();

		assertThat(pdf.getContentType()).isEqualTo("application/pdf");
		assertThat(pdf.getResponseHeaderValue("Content-Disposition")).contains("clearance-report-2026.pdf");
		try (PDDocument doc = Loader.loadPDF(pdf.getContentAsStream().readAllBytes())) {
			String text = new PDFTextStripper().getText(doc);
			assertThat(text.replaceAll("\\s", "")).contains("BARANGAYSANISIDRO", "BUSINESSCLEARANCEREPORT");
			assertThat(text).contains("2026 · All clearances", "Collections by month", "February 2026", "April 2026",
					"By type of business", "Sari-sari store", "Tomas Store", "Total, 5 clearances", "₱1,750.50",
					"1 older clearance has no date issued and is not included.", "LIZA TAN", "HON. RAMON CRUZ",
					"Page 1 of ");
			assertThat(text).doesNotContain("Old Record");
		}
	}

	@Test
	void theClearancesDownloadAsASpreadsheet() throws IOException {
		seed();
		HtmlPage report = open("/reports?type=RENEWAL");
		WebResponse csv = ((Page) open(((HtmlAnchor) report.getElementById("report-csv")).getHrefAttribute()))
				.getWebResponse();

		assertThat(csv.getContentType()).isEqualTo("text/csv");
		assertThat(csv.getResponseHeaderValue("Content-Disposition")).isEqualTo("attachment; filename=\"clearances-2026-03.csv\"");
		String body = new String(csv.getContentAsStream().readAllBytes(), StandardCharsets.UTF_8);
		assertThat(body.lines()).hasSize(2);
		assertThat(body.lines().toList().get(1)).isEqualTo(
				"3,2026-03-01,Renewal,Lola Eatery,12 Rizal St.,Eatery,Juan Dela Cruz,Single proprietorship,556603,300.00");
	}

	@Test
	void theReportPrintsToTheChosenPrinter() throws IOException {
		seed();
		try (FakeIppPrinter printer = new FakeIppPrinter("/ipp/print")) {
			HtmlPage settingsPage = open("/settings");
			HtmlForm add = settingsPage.getFirstByXPath("//form[contains(@action,'/settings/printers/add')]");
			add.getInputByName("address").setValue(printer.address());
			HtmlPage added = click(settingsPage, "add-printer");
			HtmlRadioButtonInput radio = added.getFirstByXPath(
					"//label[.//span[@class='printer-name' and text()='Office LaserJet']]/input");
			radio.setChecked(true);
			click(added, "save-printer");

			HtmlPage report = open("/reports?from=2026-03-01&to=2026-03-31&type=NEW");
			assertThat(text(report, "print-now")).isEqualTo("Print to Office LaserJet");
			assertThat(text(report, "report-pdf")).isEqualTo("Open PDF");
			HtmlPage printed = click(report, "print-now");

			assertThat(text(printed, "flash")).isEqualTo("Sent to Office LaserJet (job 41).");
			assertThat(text(printed, "report-scope")).isEqualTo("New businesses · 2 clearances issued");
			FakeIppPrinter.Job job = printer.printJobs().getFirst();
			assertThat(job.attributes().get("job-name")).containsExactly("Clearance report March 2026");
			try (PDDocument doc = Loader.loadPDF(job.document())) {
				assertThat(new PDFTextStripper().getText(doc)).contains("March 2026 · New businesses", "Rizal Pharmacy")
						.doesNotContain("Lola Eatery");
			}
		}
	}

	@Test
	void undatedRecordsAreCountedButNeverPlaced() {
		seed();
		assertThat(clearances.countUndated()).isEqualTo(1);
		assertThat(clearances.issuedBetween(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1), null))
				.extracting(Clearance::businessName)
				.containsExactly("February Store", "Tomas Store", "Lola Eatery", "Rizal Pharmacy", "April Store");
		assertThat(clearances.issuedBetween(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1), ClearanceType.RENEWAL))
				.extracting(Clearance::businessName).containsExactly("Lola Eatery");
	}
}
