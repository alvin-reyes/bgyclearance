package com.thub.areyes1.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlCheckBoxInput;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
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
import com.thub.areyes1.clearance.Building;
import com.thub.areyes1.clearance.Clearance;
import com.thub.areyes1.clearance.ClearanceQuery;
import com.thub.areyes1.clearance.ClearanceRepository;
import com.thub.areyes1.clearance.ClearanceType;
import com.thub.areyes1.settings.BarangaySettings;
import com.thub.areyes1.settings.SettingsRepository;

/**
 * Runs the real app (embedded Tomcat, SQLite, PDF printing) on a random port and
 * uses it through a headless browser, the way a person would: following links,
 * filling in forms and pressing buttons.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabase.FixedClock.class)
class WebAppTest {

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
		browser = new WebClient();
		browser.getOptions().setJavaScriptEnabled(false);
		browser.getOptions().setCssEnabled(false);
		browser.getOptions().setThrowExceptionOnFailingStatusCode(false);
	}

	@AfterEach
	void tearDown() {
		browser.close();
	}

	HtmlPage open(String path) throws IOException {
		return browser.getPage("http://localhost:" + port + path);
	}

	Clearance seed(String name, int controlNo, String amount, LocalDate issuedOn, ClearanceType type) {
		return clearances.insert(new Clearance(null, type, controlNo, issuedOn, name, "12 Rizal St.", "Retail",
				null, null, false, false, false, false, null, null, null, null, null, new BigDecimal(amount)));
	}

	// ---- Dashboard ----------------------------------------------------------

	@Test
	void dashboardStartsEmptyAndPromptsForSetup() throws IOException {
		HtmlPage page = open("/");

		assertThat(page.getWebResponse().getStatusCode()).isEqualTo(200);
		assertThat(page.getElementById("empty")).isNotNull();
		assertThat(page.getElementById("setup-banner")).isNotNull();
		assertThat(text(page, "stat-total")).contains("0");
	}

	@Test
	void dashboardShowsTotalsAndRecentClearances() throws IOException {
		settings.save(new BarangaySettings("San Isidro", "", "", "Hon. Ramon Cruz", ""));
		seed("Old One", 1, "100.25", LocalDate.of(2025, 12, 1), ClearanceType.NEW);
		seed("This Year New", 2, "50", LocalDate.of(2026, 2, 1), ClearanceType.NEW);
		seed("This Year Renewal", 3, "30", LocalDate.of(2026, 3, 1), ClearanceType.RENEWAL);

		HtmlPage page = open("/");

		assertThat(page.getElementById("setup-banner")).isNull();
		assertThat(page.asNormalizedText()).contains("Barangay San Isidro");
		assertThat(text(page, "stat-total")).contains("3");
		assertThat(text(page, "stat-collected")).contains("₱180.25");
		assertThat(text(page, "stat-year")).contains("Issued in 2026", "2", "₱80.00 collected");
		assertThat(text(page, "stat-mix")).contains("2 / 1");
		assertThat(page.asNormalizedText()).containsSubsequence("This Year Renewal", "This Year New", "Old One");
	}

	// ---- Register -----------------------------------------------------------

	@Test
	void registeringAClearanceSavesEveryField() throws IOException {
		HtmlPage form = click(open("/"), "new-clearance");
		assertThat(input(form, "issuedOn").getValue()).isEqualTo("2026-03-15"); // defaults to today

		radio(form, "type", "RENEWAL");
		set(form, "controlNumber", "1001");
		set(form, "issuedOn", "2026-03-14");
		set(form, "businessName", "  Tomas Store  ");
		set(form, "address", "12 Rizal St.");
		set(form, "typeOfBusiness", "Sari-sari");
		set(form, "capitalization", "75,000");
		radio(form, "building", "RENTED");
		check(form, "singleProprietorship");
		check(form, "partnership");
		set(form, "ownerName", "Tomás Reyes");
		set(form, "applicantMemberOf", "Poblacion HOA");
		set(form, "hoaPresident", "Maria Santos");
		set(form, "secondEndorsementNumber", "12");
		set(form, "orNumber", "556677");
		set(form, "amountPaid", "250.5");
		HtmlPage detail = click(form, "save");

		assertThat(detail.getUrl().getPath()).matches("/clearances/\\d+");
		assertThat(text(detail, "flash")).isEqualTo("Clearance for Tomas Store saved.");
		assertThat(text(detail, "business-name")).isEqualTo("Tomas Store");
		assertThat(text(detail, "clearance-type")).isEqualTo("Renewal");
		assertThat(text(detail, "amount-paid")).isEqualTo("₱250.50");
		assertThat(text(detail, "ownership-kinds")).isEqualTo("Single proprietorship, Partnership");
		assertThat(text(detail, "issued-on")).isEqualTo("March 14, 2026");

		long id = Long.parseLong(detail.getUrl().getPath().replaceAll("\\D", ""));
		assertThat(clearances.findById(id)).contains(new Clearance(id, ClearanceType.RENEWAL, 1001,
				LocalDate.of(2026, 3, 14), "Tomas Store", "12 Rizal St.", "Sari-sari", "75,000", Building.RENTED,
				false, true, true, false, "Tomás Reyes", "Poblacion HOA", "Maria Santos", 12, 556677,
				new BigDecimal("250.50")));
	}

	@Test
	void invalidInputIsRejectedWithFriendlyMessagesAndKept() throws IOException {
		seed("Existing", 1001, "10", LocalDate.of(2026, 1, 1), ClearanceType.NEW);
		HtmlPage form = open("/clearances/new");
		set(form, "controlNumber", "1001");
		set(form, "issuedOn", "");
		set(form, "amountPaid", "abc");
		set(form, "orNumber", "12x");

		HtmlPage result = click(form, "save");

		assertThat(result.getElementById("errors")).isNotNull();
		assertThat(result.asNormalizedText()).contains("Control no. 1001 is already used by Existing", "Enter the business name",
				"Enter the date issued", "Enter an amount, for example 250.50", "Enter a whole number")
				.doesNotContain("Exception", "Failed to convert");
		assertThat(input(result, "amountPaid").getValue()).isEqualTo("abc");
		assertThat(clearances.search(ClearanceQuery.all()).total()).isEqualTo(1);
	}

	@Test
	void amountsMayBeTypedWithCommasAndPesoSign() throws IOException {
		HtmlPage form = open("/clearances/new");
		set(form, "controlNumber", "3001");
		set(form, "businessName", "Comma Store");
		set(form, "amountPaid", "₱1,250.50");

		HtmlPage detail = click(form, "save");

		assertThat(text(detail, "amount-paid")).isEqualTo("₱1,250.50");
	}

	@Test
	void formOffersNextControlNumberAndBusinessTypeSuggestions() throws IOException {
		Clearance a = seed("A", 2026004, "10", LocalDate.of(2026, 1, 1), ClearanceType.NEW);
		seed("B", 2026009, "10", LocalDate.of(2026, 1, 1), ClearanceType.NEW);

		HtmlPage form = open("/clearances/new");

		HtmlElement fill = form.querySelector("[data-fill=controlNumber]");
		assertThat(fill.getAttribute("data-value")).isEqualTo("2026010");
		assertThat(text(form, "controlNumber-hint")).isEqualTo("Next available: 2026010");
		assertThat(input(form, "typeOfBusiness").getAttribute("list")).isEqualTo("business-types");
		assertThat(form.querySelectorAll("#business-types option").size()).isEqualTo(1); // both seeded as "Retail"
		DomNode editFill = open("/clearances/" + a.id() + "/edit").querySelector("[data-fill]");
		assertThat(editFill == null).as("only offered when creating").isTrue();
	}

	@Test
	void invalidFieldsAreMarkedForScreenReadersAndListedWithLinks() throws IOException {
		HtmlPage form = open("/clearances/new");
		HtmlInput amount = input(form, "amountPaid");
		assertThat(amount.getAttribute("aria-required")).isEqualTo("true");
		assertThat(amount.getAttribute("aria-describedby")).isEqualTo("amountPaid-error");
		assertThat(amount.getAttribute("inputmode")).isEqualTo("decimal");
		assertThat(input(form, "controlNumber").getAttribute("aria-describedby"))
				.isEqualTo("controlNumber-error"); // no hint yet: nothing saved
		assertThat(amount.getAttribute("aria-invalid")).isEmpty();

		HtmlPage result = click(form, "save");

		HtmlInput name = input(result, "businessName");
		assertThat(name.getAttribute("aria-invalid")).isEqualTo("true");
		assertThat(text(result, "businessName-error")).isEqualTo("Enter the business name");
		List<String> links = result.querySelectorAll("#errors a").stream().map(a -> ((HtmlAnchor) a).getHrefAttribute()).toList();
		assertThat(links).contains("#businessName", "#controlNumber", "#amountPaid");
		for (String link : links) {
			assertThat(result.getElementById(link.substring(1))).as(link).isNotNull();
		}
	}

	// ---- List ---------------------------------------------------------------

	@Test
	void listSortsFiltersSearchesAndPages() throws IOException {
		for (int i = 1; i <= 27; i++) {
			seed("Shop " + (char) ('A' + (i % 26)) + i, 2000 + i, String.valueOf(i * 10),
					LocalDate.of(2026, 1, 1).plusDays(i), i % 3 == 0 ? ClearanceType.RENEWAL : ClearanceType.NEW);
		}

		HtmlPage page = open("/clearances");
		assertThat(text(page, "result-count")).isEqualTo("Showing 1–25 of 27");
		assertThat(firstCell(page, 0)).isEqualTo("2027"); // newest issue date first

		HtmlPage page2 = click(page, "next");
		assertThat(text(page2, "result-count")).isEqualTo("Showing 26–27 of 27");
		assertThat(page2.getElementById("next")).isNull();
		assertThat(click(page2, "prev").getUrl().getQuery()).isNull();

		HtmlPage byAmount = ((HtmlAnchor) page.getAnchorByText("Amount")).click();
		assertThat(firstCell(byAmount, 0)).isEqualTo("2027");
		HtmlPage byAmountAsc = ((HtmlAnchor) byAmount.getAnchorByText("Amount")).click();
		assertThat(firstCell(byAmountAsc, 0)).isEqualTo("2001");
		assertThat(byAmountAsc.getUrl().getQuery()).contains("sort=amount", "dir=asc");

		HtmlForm filters = page.getFirstByXPath("//form[@role='search']");
		((HtmlSelect) filters.getSelectByName("type")).setSelectedAttribute("RENEWAL", true);
		HtmlPage renewals = click(page, "search");
		assertThat(text(renewals, "result-count")).isEqualTo("Showing 1–9 of 9");

		HtmlPage search = open("/clearances?q=2013");
		assertThat(rows(search)).hasSize(1);
		assertThat(open("/clearances?q=zzz").getElementById("empty")).isNotNull();
	}

	// ---- Detail, edit, delete -----------------------------------------------

	@Test
	void editingUpdatesTheRecordInPlace() throws IOException {
		Clearance c = seed("Old Bakery", 4002, "99", LocalDate.of(2026, 1, 5), ClearanceType.NEW);
		HtmlPage detail = ((HtmlAnchor) open("/clearances").getAnchorByText("Old Bakery")).click();
		HtmlPage form = click(detail, "edit");
		assertThat(input(form, "businessName").getValue()).isEqualTo("Old Bakery");
		assertThat(input(form, "controlNumber").getValue()).isEqualTo("4002");

		set(form, "businessName", "New Bakery");
		set(form, "amountPaid", "120");
		set(form, "orNumber", "9001");
		radio(form, "type", "RENEWAL");
		check(form, "corporation");
		HtmlPage saved = click(form, "save");

		assertThat(text(saved, "flash")).isEqualTo("Changes saved.");
		assertThat(text(saved, "clearance-type")).isEqualTo("Renewal");
		Clearance updated = clearances.findById(c.id()).orElseThrow();
		assertThat(updated.businessName()).isEqualTo("New Bakery");
		assertThat(updated.amountPaid()).isEqualByComparingTo("120");
		assertThat(updated.orNumber()).isEqualTo(9001);
		assertThat(updated.corporation()).isTrue();
		assertThat(updated.controlNumber()).isEqualTo(4002);
		assertThat(clearances.search(ClearanceQuery.all()).total()).isEqualTo(1);
	}

	@Test
	void controlNumberZeroMayRepeatLikeInTheDesktopApp() throws IOException {
		seed("Legacy A", 0, "0", LocalDate.of(2026, 1, 1), ClearanceType.NEW);
		Clearance b = seed("Legacy B", 0, "0", LocalDate.of(2026, 1, 1), ClearanceType.NEW);

		HtmlPage form = open("/clearances/" + b.id() + "/edit");
		set(form, "amountPaid", "5");
		HtmlPage saved = click(form, "save");

		assertThat(text(saved, "flash")).isEqualTo("Changes saved.");
	}

	@Test
	void deletingRemovesTheRecord() throws IOException {
		seed("Keep Me", 1, "10", LocalDate.of(2026, 1, 1), ClearanceType.NEW);
		Clearance gone = seed("Delete Me", 2, "10", LocalDate.of(2026, 1, 2), ClearanceType.NEW);

		HtmlPage list = click(open("/clearances/" + gone.id()), "delete");

		assertThat(list.getUrl().getPath()).isEqualTo("/clearances");
		assertThat(text(list, "flash")).isEqualTo("Clearance for Delete Me deleted.");
		assertThat(rows(list)).hasSize(1);
		assertThat(clearances.findById(gone.id())).isEmpty();
	}

	@Test
	void unknownClearanceShowsNotFound() throws IOException {
		HtmlPage page = open("/clearances/999");

		assertThat(page.getWebResponse().getStatusCode()).isEqualTo(404);
		assertThat(page.asNormalizedText()).contains("Not found");
		assertThat(open("/clearances/999/edit").getWebResponse().getStatusCode()).isEqualTo(404);
		assertThat(browser.getPage("http://localhost:" + port + "/clearances/999/clearance.pdf")
				.getWebResponse().getStatusCode()).isEqualTo(404);
	}

	// ---- Printing and settings ----------------------------------------------

	@Test
	void printingUsesTheBarangaySettings() throws IOException {
		HtmlPage settingsPage = ((HtmlAnchor) open("/").getAnchorByText("Settings")).click();
		set(settingsPage, "barangayName", "San Isidro");
		set(settingsPage, "municipality", "Municipality of Liloan");
		set(settingsPage, "province", "Cebu");
		set(settingsPage, "punongBarangay", "Hon. Ramon Cruz");
		HtmlPage saved = click(settingsPage, "save");
		assertThat(text(saved, "flash")).startsWith("Barangay details saved");

		Clearance c = seed("Tomas Store", 6001, "250.5", LocalDate.of(2026, 3, 1), ClearanceType.NEW);
		Page pdf = click(open("/clearances/" + c.id()), "print");

		assertThat(pdf.getWebResponse().getStatusCode()).isEqualTo(200);
		assertThat(pdf.getWebResponse().getContentType()).isEqualTo("application/pdf");
		assertThat(pdf.getWebResponse().getResponseHeaderValue("Content-Disposition"))
				.contains("clearance-6001.pdf");
		try (InputStream in = pdf.getWebResponse().getContentAsStream();
				PDDocument doc = Loader.loadPDF(in.readAllBytes())) {
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(true);
			assertThat(stripper.getText(doc).replaceAll("\\s+", " ")).contains("BARANGAY SAN ISIDRO",
					"Province of Cebu", "TOMAS STORE", "Control No.: 6001", "₱250.50", "HON. RAMON CRUZ");
		}
	}

	@Test
	void settingsRequireBarangayAndPunongBarangay() throws IOException {
		HtmlPage page = click(open("/settings"), "save");

		assertThat(page.asNormalizedText()).contains("Enter the barangay name", "Enter the punong barangay's name");
		assertThat(settings.load().isConfigured()).isFalse();
	}

	// ---- helpers ------------------------------------------------------------

	static String text(HtmlPage page, String id) {
		return page.getHtmlElementById(id).asNormalizedText().strip();
	}

	static HtmlForm form(HtmlPage page) {
		return page.getFirstByXPath("//form[@method='post']");
	}

	static HtmlInput input(HtmlPage page, String name) {
		return form(page).getInputByName(name);
	}

	static void set(HtmlPage page, String name, String value) {
		input(page, name).setValue(value);
	}

	static void radio(HtmlPage page, String name, String value) {
		for (HtmlRadioButtonInput r : form(page).getRadioButtonsByName(name)) {
			r.setChecked(value.equals(r.getValueAttribute()));
		}
	}

	static void check(HtmlPage page, String name) {
		((HtmlCheckBoxInput) input(page, name)).setChecked(true);
	}

	static <P extends Page> P click(HtmlPage page, String id) throws IOException {
		return ((HtmlElement) page.getHtmlElementById(id)).click();
	}

	static List<HtmlTableRow> rows(HtmlPage page) {
		return page.getByXPath("//table[@id='clearances']/tbody/tr");
	}

	static String firstCell(HtmlPage page, int row) {
		return rows(page).get(row).getCell(0).asNormalizedText().strip();
	}
}
