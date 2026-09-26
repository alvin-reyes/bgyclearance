package com.thub.areyes1.web;

import static com.thub.areyes1.web.WebAppTest.click;
import static com.thub.areyes1.web.WebAppTest.text;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlCheckBoxInput;
import org.htmlunit.html.HtmlFileInput;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
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
import com.thub.areyes1.clearance.Building;
import com.thub.areyes1.clearance.Clearance;
import com.thub.areyes1.clearance.ClearanceRepository;
import com.thub.areyes1.clearance.ClearanceType;
import com.thub.areyes1.print.form.FormField;
import com.thub.areyes1.print.form.FormLayout;
import com.thub.areyes1.print.form.FormLayouts;
import com.thub.areyes1.printing.FakeIppPrinter;
import com.thub.areyes1.settings.BarangaySettings;
import com.thub.areyes1.settings.SettingsRepository;

/** Sets up printing onto pre-printed forms through Settings, the way an office would. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabase.FixedClock.class)
class FormLayoutWebTest {

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

	@Autowired
	FormLayouts forms;

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

	Clearance seed() {
		return clearances.insert(new Clearance(null, ClearanceType.NEW, 7001, LocalDate.of(2026, 3, 1), "Tomas Store",
				"12 Rizal St.", "Sari-sari store", "75,000", Building.OWNED, false, true, false, false, "Tomás Reyes",
				null, null, null, 556677, new BigDecimal("250.50")));
	}

	static String pdfText(byte[] pdf) throws IOException {
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(doc);
		}
	}

	byte[] download(String path) throws IOException {
		WebResponse r = ((Page) open(path)).getWebResponse();
		assertThat(r.getStatusCode()).as(path).isEqualTo(200);
		return r.getContentAsStream().readAllBytes();
	}

	HtmlForm layoutForm(HtmlPage page) {
		return (HtmlForm) page.getElementById("layout-form");
	}

	@Test
	void switchingToPreprintedFormsPrintsOnlyTheValues() throws IOException {
		Clearance c = seed();
		String plain = pdfText(download("/clearances/" + c.id() + "/clearance.pdf"));
		assertThat(plain.replaceAll("\\s", "")).contains("BARANGAYBUSINESSCLEARANCE");

		HtmlPage settingsPage = open("/settings");
		assertThat(text(settingsPage, "preprinted-status")).startsWith("Off");
		HtmlPage editor = ((HtmlPage) settingsPage.getElementById("open-form-layout").click());
		assertThat(editor.getTitleText()).startsWith("Pre-printed form");
		assertThat(((HtmlRadioButtonInput) editor.getElementById("mode-plain")).isChecked()).isTrue();

		((HtmlRadioButtonInput) editor.getElementById("mode-preprinted")).setChecked(true);
		HtmlPage saved = click(editor, "save-layout");

		assertThat(text(saved, "flash")).isEqualTo("Form layout saved.");
		assertThat(text(open("/settings"), "preprinted-status")).startsWith("On");
		assertThat(text(open("/clearances/" + c.id()), "print-mode")).startsWith("Prints the values only");
		String values = pdfText(download("/clearances/" + c.id() + "/clearance.pdf"));
		assertThat(values).contains("Tomas Store", "12 Rizal St.", "Sari-sari store", "Owned", "7001", "556677",
				"250.50", "San Isidro");
		assertThat(values.replaceAll("\\s", "")).doesNotContain("BARANGAYBUSINESSCLEARANCE", "TOWHOMITMAYCONCERN");
	}

	@Test
	void fieldsAreMovedSwitchedAndNudgedFromTheTable() throws IOException {
		HtmlPage editor = open("/settings/form");
		HtmlForm form = layoutForm(editor);
		((HtmlCheckBoxInput) editor.getElementById("DATE_ISSUED-on")).setChecked(true);
		form.getInputByName("DATE_ISSUED.x").setValue("120.5");
		form.getInputByName("DATE_ISSUED.y").setValue("20");
		form.getInputByName("DATE_ISSUED.size").setValue("99");
		((HtmlCheckBoxInput) editor.getElementById("BARANGAY-on")).setChecked(false);
		form.getInputByName("BUSINESS_NAME.width").setValue("not a number");
		form.getInputByName("shiftX").setValue("-2.5");
		form.getInputByName("shiftY").setValue("1");

		HtmlPage saved = click(editor, "save-layout");

		FormLayout layout = forms.load();
		assertThat(layout.placement(FormField.DATE_ISSUED)).isEqualTo(new FormLayout.Placement(true, 120.5, 20, 50, 36));
		assertThat(layout.placement(FormField.BARANGAY).on()).isFalse();
		assertThat(layout.placement(FormField.BUSINESS_NAME)).isEqualTo(FormField.BUSINESS_NAME.defaultPlacement());
		assertThat(layout.shiftX()).isEqualTo(-2.5);
		assertThat(layout.shiftY()).isEqualTo(1);
		assertThat(((HtmlInput) saved.getElementById("DATE_ISSUED-x")).getValue()).isEqualTo("120.5");
		assertThat(((HtmlCheckBoxInput) saved.getElementById("BARANGAY-on")).isChecked()).isFalse();
		assertThat(saved.getElementById("box-DATE_ISSUED").getAttribute("style")).contains("--x:120.5", "--y:20.0");

		String test = pdfText(download("/settings/form/test.pdf"));
		assertThat(test).contains("Tindahan ni Mang Tomas", "March 15, 2026").doesNotContain("San Isidro");

		HtmlPage reset = ((HtmlPage) ((org.htmlunit.html.HtmlElement) saved.getElementById("reset-layout")).click());
		assertThat(text(reset, "flash")).startsWith("Every field is back");
		assertThat(forms.load().placement(FormField.DATE_ISSUED)).isEqualTo(FormField.DATE_ISSUED.defaultPlacement());
		assertThat(forms.load().shiftX()).isZero();
	}

	@Test
	void theAlignmentTestPrintsOnTheChosenPrinter() throws IOException {
		assertThat(((HtmlPage) open("/settings/form")).getElementById("print-test")).isNull();
		try (FakeIppPrinter printer = new FakeIppPrinter("/ipp/print")) {
			HtmlPage settingsPage = open("/settings");
			HtmlForm add = settingsPage.getFirstByXPath("//form[contains(@action,'/settings/printers/add')]");
			add.getInputByName("address").setValue(printer.address());
			HtmlPage added = click(settingsPage, "add-printer");
			((HtmlRadioButtonInput) added.getFirstByXPath(
					"//label[.//span[@class='printer-name' and text()='Office LaserJet']]/input")).setChecked(true);
			click(added, "save-printer");

			HtmlPage editor = open("/settings/form");
			layoutForm(editor).getInputByName("OR_NUMBER.x").setValue("150");
			HtmlPage printed = click(editor, "print-test");

			assertThat(text(printed, "flash")).isEqualTo("Layout saved. Alignment test: Sent to Office LaserJet (job 41).");
			assertThat(forms.load().placement(FormField.OR_NUMBER).x()).isEqualTo(150);
			FakeIppPrinter.Job job = printer.printJobs().getFirst();
			assertThat(job.attributes().get("job-name")).containsExactly("Form alignment test");
			assertThat(pdfText(job.document())).contains("Tindahan ni Mang Tomas", "556677");
		}
	}

	@Test
	void aPictureOfTheBlankFormCanBeAddedAndRemoved() throws IOException {
		Path png = Files.createTempFile("blank-form", ".png");
		// A 1x1 PNG.
		Files.write(png, java.util.Base64.getDecoder().decode(
				"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="));
		HtmlPage editor = open("/settings/form");
		assertThat(editor.getElementById("remove-scan")).isNull();
		((HtmlFileInput) editor.getElementById("scan")).setFiles(png.toFile());
		((HtmlFileInput) editor.getElementById("scan")).setContentType("image/png");

		HtmlPage uploaded = click(editor, "upload-scan");

		assertThat(text(uploaded, "flash")).startsWith("Blank form added.");
		WebResponse image = ((Page) open("/settings/form/background")).getWebResponse();
		assertThat(image.getContentType()).isEqualTo("image/png");
		assertThat(image.getContentAsStream().readAllBytes()).isEqualTo(Files.readAllBytes(png));

		HtmlPage removed = click(uploaded, "remove-scan");
		assertThat(text(removed, "flash")).isEqualTo("Blank form picture removed.");
		assertThat(((Page) open("/settings/form/background")).getWebResponse().getStatusCode()).isEqualTo(404);
	}

	@Test
	void onlyPicturesAreAccepted() throws IOException {
		Path txt = Files.createTempFile("not-a-picture", ".txt");
		Files.writeString(txt, "hello");
		HtmlPage editor = open("/settings/form");
		((HtmlFileInput) editor.getElementById("scan")).setFiles(txt.toFile());
		((HtmlFileInput) editor.getElementById("scan")).setContentType("text/plain");

		HtmlPage refused = click(editor, "upload-scan");

		assertThat(text(refused, "print-error")).startsWith("Choose a photo or scan");
		assertThat(forms.background()).isEmpty();
	}

	@Test
	void storedLayoutsSurviveOddValues() {
		forms.save(FormLayout.defaults());
		settings.put("form_fields", "BUSINESS_NAME on 1 2 3 11\nNOT_A_FIELD on 1 2 3 4\nADDRESS on x y z w\ngarbage\n");
		settings.put("form_shift", "oops");

		FormLayout layout = forms.load();

		assertThat(layout.placement(FormField.BUSINESS_NAME)).isEqualTo(new FormLayout.Placement(true, 1, 2, 3, 11));
		assertThat(layout.placement(FormField.ADDRESS)).isEqualTo(FormField.ADDRESS.defaultPlacement());
		assertThat(layout.shiftX()).isZero();
		assertThat(FormLayoutController.read(Map.of("mode", "preprinted"), layout).preprinted()).isTrue();
		assertThat(FormLayoutController.number(" 3,5 ", 0)).isEqualTo(3.5);
		assertThat(FormLayoutController.number("Infinity", 7)).isEqualTo(7);
	}
}
