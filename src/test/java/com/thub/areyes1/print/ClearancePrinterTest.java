package com.thub.areyes1.print;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.thub.areyes1.TestDatabase;
import com.thub.areyes1.clearance.Building;
import com.thub.areyes1.clearance.Clearance;
import com.thub.areyes1.clearance.ClearanceType;
import com.thub.areyes1.settings.BarangaySettings;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestDatabase.FixedClock.class)
class ClearancePrinterTest {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		TestDatabase.register(registry);
	}

	@Autowired
	ClearancePrinter printer;

	static final BarangaySettings SETTINGS = new BarangaySettings("San Isidro", "Municipality of Liloan", "Cebu",
			"Hon. Ramon Cruz", "Liza Tan");

	static Clearance full() {
		return new Clearance(7L, ClearanceType.NEW, 2026001, LocalDate.of(2026, 3, 14),
				"Tindahan ni Mang Tomas Sari-Sari Store and General Merchandise", "45 Bonifacio Ave., Purok 3",
				"Sari-sari store", "75,000", Building.RENTED, false, true, false, false, "Tomás Reyes",
				"Poblacion HOA", "Maria Santos", 12, 556677, new BigDecimal("1250.50"));
	}

	@Test
	void printsEveryDetailOnOneLetterPage() throws IOException {
		byte[] pdf = printer.print(full(), SETTINGS);

		try (PDDocument doc = Loader.loadPDF(pdf)) {
			assertThat(doc.getNumberOfPages()).isEqualTo(1);
			assertThat(doc.getPage(0).getMediaBox().getWidth()).isEqualTo(612f); // 8.5in
			assertThat(doc.getPage(0).getMediaBox().getHeight()).isEqualTo(792f); // 11in
			String text = text(doc);

			assertThat(text).contains(
					"Republic of the Philippines", "Province of Cebu", "Municipality of Liloan", "New business",
					"Control No.: 2026001", "Date issued: March 14, 2026",
					// long names are printed in full, not truncated
					"TINDAHAN NI MANG TOMAS SARI-SARI STORE AND GENERAL MERCHANDISE",
					"45 Bonifacio Ave., Purok 3", "Tomás Reyes", "Sari-sari store", "75,000", "Rented",
					"Single proprietorship", "Poblacion HOA", "Maria Santos", "556677", "₱1,250.50",
					"HON. RAMON CRUZ", "Punong Barangay", "LIZA TAN", "Barangay Secretary",
					"Printed March 15, 2026");
			assertThat(text).doesNotContain("null", "AAAA", "BBB");
			// Letter-spaced headings come out of text extraction as "B A R A N G A Y".
			assertThat(text.replace(" ", "")).contains("BARANGAYSANISIDRO", "OFFICEOFTHEPUNONGBARANGAY",
					"BARANGAYBUSINESSCLEARANCE");
		}
	}

	@Test
	void embedsTheFontSoThePesoSignPrints() throws IOException {
		try (PDDocument doc = Loader.loadPDF(printer.print(full(), SETTINGS))) {
			for (var name : doc.getPage(0).getResources().getFontNames()) {
				PDFont font = doc.getPage(0).getResources().getFont(name);
				assertThat(font.getName()).contains("SourceSerif4");
				assertThat(font.isEmbedded()).isTrue();
			}
		}
	}

	@Test
	void printsRenewalsAndLeavesBlanksWhenDetailsAreMissing() throws IOException {
		Clearance minimal = new Clearance(8L, ClearanceType.RENEWAL, null, null, "Bare Minimum", null, null, null,
				null, false, false, false, false, null, null, null, null, null, BigDecimal.ZERO);

		try (PDDocument doc = Loader.loadPDF(printer.print(minimal, BarangaySettings.empty()))) {
			String text = text(doc);
			assertThat(text).contains("Renewal", "renewal of its Barangay Business Clearance", "BARE MINIMUM",
					"₱0.00", "Punong Barangay");
			assertThat(text).doesNotContain("null", "Barangay Secretary", "Province of");
		}
	}

	/**
	 * The desktop app's Jasper template (report/bgyclearance_report.jrxml, removed in
	 * the move to the web app) declared these parameters but was never finished: it
	 * printed placeholders ("AAAA", "BBB"). Every one of them is on the new printout.
	 */
	@Test
	void printsEveryFieldOfTheOriginalJasperTemplate() throws IOException {
		Map<String, String> jrxmlParameters = new LinkedHashMap<>();
		jrxmlParameters.put("BARANGAY", "BARANGAY SAN ISIDRO");
		jrxmlParameters.put("CONTROL_NUMBER", "Control No.: 2026001");
		jrxmlParameters.put("CLEARANCE_TYPE", "New business");
		jrxmlParameters.put("BUSINESS_NAME", "Business name Tindahan ni Mang Tomas Sari-Sari Store and General Merchandise");
		jrxmlParameters.put("ADDRESS", "Business address 45 Bonifacio Ave., Purok 3");
		jrxmlParameters.put("TYPE_OF_BUSINESS", "Type of business / activity Sari-sari store");
		jrxmlParameters.put("CAPITALIZATION", "Capitalization 75,000");
		jrxmlParameters.put("BUILDING_TYPE", "Building Rented");
		jrxmlParameters.put("OWNERSHIP", "Kind of ownership Single proprietorship");
		jrxmlParameters.put("APPLICANT_MEMBER_OF", "Applicant is a member of Poblacion HOA");
		jrxmlParameters.put("ASSOC_HOME_OWNER_PRESIDENT", "Homeowners' association president Maria Santos");
		jrxmlParameters.put("SECOND_ENDORSMENT_NUMBER", "2nd endorsement no. 12");
		jrxmlParameters.put("OR_NUMBER", "O.R. No.: 556677");
		jrxmlParameters.put("AMOUNT_PAID", "Amount paid: ₱1,250.50");

		try (PDDocument doc = Loader.loadPDF(printer.print(full(), SETTINGS))) {
			String text = text(doc).replace("B A R A N G A Y S A N I S I D R O", "BARANGAY SAN ISIDRO");
			jrxmlParameters.forEach((parameter, printed) -> assertThat(text).as(parameter).contains(printed));
		}
	}

	@Test
	void printsOnLongBondPaperWhenChosen() throws IOException {
		printer.setPaperSize(PaperSize.LONG);

		try (PDDocument clearance = Loader.loadPDF(printer.print(full(), SETTINGS));
				PDDocument test = Loader.loadPDF(printer.testPage(SETTINGS, "Office LaserJet"))) {
			assertThat(clearance.getNumberOfPages()).isEqualTo(1);
			assertThat(clearance.getPage(0).getMediaBox().getWidth()).isEqualTo(612f); // 8.5in
			assertThat(clearance.getPage(0).getMediaBox().getHeight()).isEqualTo(936f); // 13in
			assertThat(test.getPage(0).getMediaBox().getHeight()).isEqualTo(936f);
			assertThat(text(test)).contains("Long bond (8.5 × 13 in)");
		}
	}

	@AfterEach
	void backToLetter() {
		printer.setPaperSize(PaperSize.LETTER);
	}

	/** Writes a sample to target/ so the layout can be eyeballed after a build. */
	@Test
	void writesSampleForReview() throws IOException {
		Path out = Path.of("target", "sample-clearance.pdf");
		Files.write(out, printer.print(full(), SETTINGS));
		assertThat(out).isNotEmptyFile();
	}

	/** Page text in reading order, with runs of whitespace collapsed. */
	private static String text(PDDocument doc) throws IOException {
		PDFTextStripper stripper = new PDFTextStripper();
		stripper.setSortByPosition(true);
		return stripper.getText(doc).replaceAll("\\s+", " ");
	}
}
