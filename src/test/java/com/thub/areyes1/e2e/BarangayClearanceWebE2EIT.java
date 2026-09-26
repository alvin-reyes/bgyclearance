/**
 * Class File Name: BarangayClearanceWebE2EIT.java
 * Description: End-to-end tests for the web app, driven through a headless browser.
 */

package com.thub.areyes1.e2e;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.context.ConfigurableApplicationContext;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import com.gargoylesoftware.htmlunit.html.HtmlTableRow;
import com.thub.areyes1.web.WebApplication;

/**
 * Starts the real web app (Spring Boot, embedded Tomcat, SQLite, Jasper) on a
 * random port with a fresh database per test, and uses it like a person would:
 * following links, filling in forms and pressing buttons.
 */
public class BarangayClearanceWebE2EIT {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File db;
	private ConfigurableApplicationContext app;
	private WebClient browser;
	private String baseUrl;

	@Before
	public void setUp() throws Exception {
		db = E2eEnvironment.useFreshDatabase(tmp.getRoot());
		startApp();
	}

	private void startApp() {
		app = WebApplication.start("--server.port=0");
		baseUrl = "http://127.0.0.1:" + app.getEnvironment().getProperty("local.server.port");
		browser = new WebClient();
		browser.getOptions().setJavaScriptEnabled(false);
		browser.getOptions().setCssEnabled(false);
		browser.getOptions().setThrowExceptionOnFailingStatusCode(false);
	}

	@After
	public void tearDown() {
		if (browser != null) {
			browser.close();
		}
		if (app != null) {
			app.close();
		}
	}

	private HtmlPage open(String path) throws Exception {
		return browser.getPage(baseUrl + path);
	}

	@Test
	public void homeShowsAnEmptyRegistry() throws Exception {
		HtmlPage page = open("/");

		assertEquals(baseUrl + "/clearances", page.getUrl().toString());
		assertEquals(200, page.getWebResponse().getStatusCode());
		assertNotNull(page.getElementById("empty"));
		assertEquals("0 clearances", text(page, "result-count"));
	}

	@Test
	public void registeringANewClearanceSavesEveryField() throws Exception {
		HtmlPage form = open("/clearances").getHtmlElementById("new-clearance").click();
		assertEquals("New clearance", form.getTitleText().split(" · ")[0]);

		set(form, "controlNumber", "1001");
		set(form, "businessName", "Tomas Store");
		set(form, "address", "12 Rizal St.");
		set(form, "typeOfBusiness", "Sari-sari");
		set(form, "capitalization", "75,000");
		radio(form, "building", "RENTED");
		check(form, "singleProprietorship");
		set(form, "ownership", "Tomas Reyes");
		set(form, "applicantMemberOf", "Poblacion HOA");
		set(form, "assocHomeOwnerPresident", "Maria Santos");
		set(form, "secondEndorsmentNumber", "12");
		set(form, "orNumber", "556677");
		set(form, "amountPaid", "250.50");
		HtmlPage detail = form.getHtmlElementById("save").click();

		assertTrue(detail.getUrl().getPath().matches("/clearances/\\d+"));
		assertEquals("Clearance for Tomas Store saved.", text(detail, "flash"));
		assertEquals("Tomas Store", text(detail, "business-name"));
		assertEquals("1001", text(detail, "control-number"));
		assertEquals("New", text(detail, "clearance-type"));
		assertEquals("250.50", text(detail, "amount-paid"));
		assertEquals("Single proprietorship", text(detail, "ownership-kinds"));
		assertFalse(detail.asNormalizedText().contains("null"));

		List<Map<String, String>> rows = E2eEnvironment.rows(db);
		assertEquals(1, rows.size());
		Map<String, String> row = rows.get(0);
		assertEquals("Tomas Store", row.get("name"));
		assertEquals("12 Rizal St.", row.get("address"));
		assertEquals("Sari-sari", row.get("activity"));
		assertEquals("75,000", row.get("capitalization"));
		assertEquals("Tomas Reyes", row.get("ownership"));
		assertEquals("Poblacion HOA", row.get("applicant_member_of"));
		assertEquals("Maria Santos", row.get("assoc_president"));
		assertEquals("12", row.get("second_endorsment"));
		assertEquals("556677", row.get("or_number"));
		assertEquals("250.5", row.get("amount_paid"));
		assertEquals("1", row.get("new"));
		assertEquals("1", row.get("rented"));
		assertEquals("0", row.get("owned"));
		assertEquals("1", row.get("singleprop"));
		assertEquals("0", row.get("corporation"));
	}

	@Test
	public void invalidInputIsRejectedWithFriendlyMessages() throws Exception {
		HtmlPage form = open("/clearances/new");
		set(form, "amountPaid", "abc");
		set(form, "orNumber", "12x");

		HtmlPage result = form.getHtmlElementById("save").click();

		assertEquals("/clearances", result.getUrl().getPath());
		assertNotNull(result.getElementById("errors"));
		String text = result.asNormalizedText();
		assertTrue(text, text.contains("Enter the business name"));
		assertTrue(text, text.contains("Enter the control number"));
		assertTrue(text, text.contains("Enter an amount, for example 250.50"));
		assertTrue(text, text.contains("Enter a whole number"));
		assertFalse(text, text.contains("Exception"));
		assertEquals("typed values are kept", "abc", form(result).getInputByName("amountPaid").getValueAttribute());
		assertEquals(0, E2eEnvironment.rows(db).size());
	}

	@Test
	public void listShowsNewestFirstAndSearchFilters() throws Exception {
		E2eEnvironment.insertRow(db, "Aling Nena Store", "12 Rizal St.", 2001, "150.0");
		E2eEnvironment.insertRow(db, "Kusina ni Lola", "3 Mabini St.", 2002, "300.0");
		E2eEnvironment.insertRow(db, "Rizal Pharmacy", "8 Luna St.", 2003, "99.5");

		HtmlPage list = open("/clearances");
		assertEquals("3 clearances", text(list, "result-count"));
		List<HtmlTableRow> rows = tableRows(list);
		assertEquals("Rizal Pharmacy", rows.get(0).getCell(1).asNormalizedText());
		assertEquals("Aling Nena Store", rows.get(2).getCell(1).asNormalizedText());
		assertEquals("99.50", rows.get(0).getCell(3).asNormalizedText());

		HtmlForm search = list.getFirstByXPath("//form[@role='search']");
		search.getInputByName("q").setValueAttribute("rizal");
		HtmlPage filtered = ((HtmlElement) search.getFirstByXPath(".//button")).click();
		assertEquals("2 of 3 match “rizal”", text(filtered, "result-count"));
		assertEquals(2, tableRows(filtered).size());

		HtmlPage byControlNo = open("/clearances?q=2002");
		assertEquals(1, tableRows(byControlNo).size());
		assertEquals("Kusina ni Lola", tableRows(byControlNo).get(0).getCell(1).asNormalizedText());

		HtmlPage none = open("/clearances?q=zzz");
		assertNotNull(none.getElementById("empty"));
	}

	@Test
	public void editingUpdatesTheRecordInPlace() throws Exception {
		E2eEnvironment.insertRow(db, "Old Bakery Name", "7 Luna St.", 4002, "99.0");
		HtmlPage list = open("/clearances");
		HtmlPage detail = ((HtmlAnchor) list.getAnchorByText("Old Bakery Name")).click();
		HtmlPage form = detail.getHtmlElementById("edit").click();
		assertEquals("Old Bakery Name", form(form).getInputByName("businessName").getValueAttribute());
		assertEquals("4002", form(form).getInputByName("controlNumber").getValueAttribute());

		set(form, "businessName", "New Bakery Name");
		set(form, "amountPaid", "120");
		set(form, "orNumber", "9001");
		radio(form, "type", "RENEWAL");
		check(form, "corporation");
		HtmlPage saved = form.getHtmlElementById("save").click();

		assertEquals("Changes saved.", text(saved, "flash"));
		assertEquals("New Bakery Name", text(saved, "business-name"));
		assertEquals("Renewal", text(saved, "clearance-type"));
		List<Map<String, String>> rows = E2eEnvironment.rows(db);
		assertEquals("editing must not insert a new row", 1, rows.size());
		assertEquals("New Bakery Name", rows.get(0).get("name"));
		assertEquals("7 Luna St.", rows.get(0).get("address"));
		assertEquals("120.0", rows.get(0).get("amount_paid"));
		assertEquals("9001", rows.get(0).get("or_number"));
		assertEquals("4002", rows.get(0).get("control_no"));
		assertEquals("0", rows.get(0).get("new"));
		assertEquals("1", rows.get(0).get("corporation"));
	}

	@Test
	public void deletingRemovesTheRecord() throws Exception {
		E2eEnvironment.insertRow(db, "Keep Me", "1 Main St.", 5001, "10.0");
		E2eEnvironment.insertRow(db, "Delete Me", "2 Main St.", 5002, "10.0");

		HtmlPage detail = open("/clearances").getAnchorByText("Delete Me").click();
		HtmlPage list = detail.getHtmlElementById("delete").click();

		assertEquals("/clearances", list.getUrl().getPath());
		assertEquals("Clearance for Delete Me deleted.", text(list, "flash"));
		assertEquals(1, tableRows(list).size());
		assertEquals(1, E2eEnvironment.rows(db).size());
		assertEquals("Keep Me", E2eEnvironment.rows(db).get(0).get("name"));
	}

	@Test
	public void printingProducesAPdf() throws Exception {
		E2eEnvironment.insertRow(db, "Tomas Store", "12 Rizal St.", 6001, "250.5");

		HtmlPage detail = open("/clearances").getAnchorByText("Tomas Store").click();
		Page pdf = detail.getHtmlElementById("print").click();

		WebResponse response = pdf.getWebResponse();
		assertEquals(200, response.getStatusCode());
		assertEquals("application/pdf", response.getContentType());
		assertTrue(response.getResponseHeaderValue("Content-Disposition").contains("clearance-6001.pdf"));
		byte[] body = readAll(response.getContentAsStream());
		assertTrue(body.length > 500);
		assertEquals("%PDF-", new String(body, 0, 5, "ISO-8859-1"));
	}

	@Test
	public void unknownClearanceShowsNotFound() throws Exception {
		HtmlPage page = open("/clearances/999");

		assertEquals(404, page.getWebResponse().getStatusCode());
		assertTrue(page.asNormalizedText().contains("That clearance doesn't exist"));
		assertNull(page.getElementById("business-name"));
	}

	@Test
	public void dataSurvivesARestartAndWorksWithTheShippedDatabase() throws Exception {
		tearDown();
		File sample = E2eEnvironment.useShippedSampleDatabase(tmp.getRoot());
		startApp();

		HtmlPage list = open("/clearances");
		int shipped = E2eEnvironment.rows(sample).size();
		assertEquals(shipped + " clearances", text(list, "result-count"));

		HtmlPage form = open("/clearances/new");
		set(form, "controlNumber", "7001");
		set(form, "businessName", "After Upgrade");
		set(form, "orNumber", "42");
		set(form, "amountPaid", "5");
		form.getHtmlElementById("save").click();

		tearDown();
		startApp();
		assertEquals((shipped + 1) + " clearances", text(open("/clearances"), "result-count"));
	}

	// ---- helpers -------------------------------------------------------

	private static byte[] readAll(InputStream in) throws Exception {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			for (int n; (n = in.read(buf)) != -1;) {
				out.write(buf, 0, n);
			}
			return out.toByteArray();
		} finally {
			in.close();
		}
	}

	private static String text(HtmlPage page, String id) {
		return page.getHtmlElementById(id).asNormalizedText().trim();
	}

	private static HtmlForm form(HtmlPage page) {
		return page.getFirstByXPath("//form[@method='post']");
	}

	private static void set(HtmlPage page, String name, String value) {
		form(page).getInputByName(name).setValueAttribute(value);
	}

	private static void radio(HtmlPage page, String name, String value) {
		for (Object o : form(page).getRadioButtonsByName(name)) {
			HtmlRadioButtonInput r = (HtmlRadioButtonInput) o;
			r.setChecked(value.equals(r.getValueAttribute()));
		}
	}

	private static void check(HtmlPage page, String name) {
		((HtmlCheckBoxInput) form(page).getInputByName(name)).setChecked(true);
	}

	private static List<HtmlTableRow> tableRows(HtmlPage page) {
		return page.getByXPath("//table[@id='clearances']/tbody/tr");
	}
}
