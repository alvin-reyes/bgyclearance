package com.thub.areyes1.print.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import com.thub.areyes1.clearance.Building;
import com.thub.areyes1.clearance.Clearance;
import com.thub.areyes1.clearance.ClearanceType;
import com.thub.areyes1.print.PaperSize;
import com.thub.areyes1.settings.BarangaySettings;

class FormOverlayPrinterTest {

	static final BarangaySettings SETTINGS = new BarangaySettings("San Isidro", "Municipality of Liloan", "Cebu",
			"Hon. Ramon Cruz", "Liza Tan");

	static Clearance clearance() {
		return new Clearance(7L, ClearanceType.RENEWAL, 2026001, LocalDate.of(2026, 3, 14), "Tomas Store",
				"45 Bonifacio Ave.", "Sari-sari store", "75,000", Building.RENTED, true, false, true, false,
				"Tomás Reyes", "Poblacion HOA", "Maria Santos", 12, 556677, new BigDecimal("1250.5"));
	}

	/** A printed value, where it starts and its baseline, in points from the page's top-left corner. */
	record Run(String text, float x, float baseline) {
	}

	/**
	 * Every value on the page, with the position of its first character. The printer
	 * draws each value with one text operation, so a value is a run of characters on
	 * one baseline that follow each other without a gap.
	 */
	static List<Run> runs(byte[] pdf) throws IOException {
		List<TextPosition> chars = new ArrayList<>();
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			PDFTextStripper stripper = new PDFTextStripper() {
				@Override
				protected void processTextPosition(TextPosition text) {
					chars.add(text);
				}
			};
			stripper.getText(doc);
		}
		List<Run> runs = new ArrayList<>();
		StringBuilder text = new StringBuilder();
		TextPosition first = null;
		TextPosition last = null;
		for (TextPosition c : chars) {
			boolean continues = last != null && Math.abs(c.getYDirAdj() - last.getYDirAdj()) < 0.01
					&& Math.abs(c.getXDirAdj() - (last.getXDirAdj() + last.getWidthDirAdj())) < 0.5;
			if (!continues && first != null) {
				runs.add(run(text.toString(), first));
				text.setLength(0);
				first = null;
			}
			if (first == null) {
				first = c;
			}
			text.append(c.getUnicode());
			last = c;
		}
		if (first != null) {
			runs.add(run(text.toString(), first));
		}
		return runs;
	}

	private static Run run(String text, TextPosition first) {
		return new Run(text.strip(), first.getXDirAdj(), first.getYDirAdj());
	}

	static Run find(List<Run> runs, String text) {
		return runs.stream().filter(r -> r.text().equals(text)).findFirst()
				.orElseThrow(() -> new AssertionError("\"" + text + "\" not printed; got " + runs));
	}

	@Test
	void defaultsPutTheValuesWhereTheJasperTemplateDid() throws IOException {
		byte[] pdf = FormOverlayPrinter.print(clearance(), SETTINGS, FormLayout.defaults(), PaperSize.LONG, false);

		try (PDDocument doc = Loader.loadPDF(pdf)) {
			assertThat(doc.getNumberOfPages()).isEqualTo(1);
			assertThat(doc.getPage(0).getMediaBox().getHeight()).isEqualTo(936f); // the template's page height
		}
		List<Run> runs = runs(pdf);
		// Page positions from report/bgyclearance_report.jrxml: x + 20 pt margin, y + 20 pt margin
		// (title band) or + 134 pt (detail band, below the 79 pt title and 35 pt page header).
		Map<String, float[]> jrxml = Map.of(
				"San Isidro", new float[] { 268, 43 },
				"2026001", new float[] { 56, 58 },
				"556677", new float[] { 473, 43 },
				"Tomas Store", new float[] { 31, 151 },
				"45 Bonifacio Ave.", new float[] { 31, 189 },
				"Sari-sari store", new float[] { 358, 151 },
				"Rented", new float[] { 285, 191 },
				"75,000", new float[] { 228, 232 },
				"Maria Santos", new float[] { 20, 232 },
				"1,250.50", new float[] { 132, 265 });
		jrxml.forEach((text, xy) -> {
			Run run = find(runs, text);
			assertThat(run.x()).as(text + " x").isCloseTo(xy[0], within(0.5f));
			// The text sits inside the field's box (1.25 lines of 10 pt): its baseline is below the top.
			assertThat(run.baseline() - xy[1]).as(text + " baseline below the top").isBetween(5f, 12.5f);
		});
		assertThat(find(runs, "12").x()).isCloseTo(358, within(0.5f));
		assertThat(runs).extracting(Run::text).contains("Corporation, Partnership")
				// Declared but not placed by the template, so off by default.
				.doesNotContain("Renewal", "Poblacion HOA", "March 14, 2026", "Tomás Reyes", "X");
	}

	@Test
	void fieldsCanBeTurnedOnMovedAndResized() throws IOException {
		Map<FormField, FormLayout.Placement> fields = new EnumMap<>(FormField.class);
		fields.put(FormField.DATE_ISSUED, new FormLayout.Placement(true, 100, 20, 60, 12));
		fields.put(FormField.TICK_RENEWAL, new FormLayout.Placement(true, 150, 30, 6, 12));
		fields.put(FormField.TICK_NEW, new FormLayout.Placement(true, 140, 30, 6, 12));
		fields.put(FormField.BARANGAY, new FormLayout.Placement(false, 0, 0, 10, 10));
		FormLayout layout = new FormLayout(true, 0, 0, fields);

		List<Run> runs = runs(FormOverlayPrinter.print(clearance(), SETTINGS, layout, PaperSize.LETTER, false));

		float mm = 72f / 25.4f;
		Run date = find(runs, "March 14, 2026");
		assertThat(date.x()).isCloseTo(100 * mm, within(0.5f));
		Run tick = find(runs, "X");
		assertThat(tick.x()).as("only the renewal box is ticked").isCloseTo(150 * mm, within(0.5f));
		assertThat(runs).extracting(Run::text).doesNotContain("San Isidro");
	}

	@Test
	void theShiftMovesEverything() throws IOException {
		FormLayout shifted = new FormLayout(true, 5, -3, Map.of());
		Run plain = find(runs(FormOverlayPrinter.print(clearance(), SETTINGS, FormLayout.defaults(), PaperSize.LONG,
				false)), "Tomas Store");
		Run moved = find(runs(FormOverlayPrinter.print(clearance(), SETTINGS, shifted, PaperSize.LONG, false)),
				"Tomas Store");

		float mm = 72f / 25.4f;
		assertThat(moved.x() - plain.x()).isCloseTo(5 * mm, within(0.1f));
		assertThat(moved.baseline() - plain.baseline()).isCloseTo(-3 * mm, within(0.1f));
	}

	@Test
	void longValuesShrinkAndUnprintableCharactersAreDropped() throws IOException {
		Clearance c = clearance();
		Clearance longName = new Clearance(c.id(), c.type(), c.controlNumber(), c.issuedOn(),
				"Tindahan ni Mang Tomas Sari-Sari Store and General Merchandise 🏪 店", c.address(), c.typeOfBusiness(),
				c.capitalization(), c.building(), false, false, false, false, null, null, null, null, null, null);

		List<Run> runs = runs(FormOverlayPrinter.print(longName, SETTINGS, FormLayout.defaults(), PaperSize.LONG, false));

		assertThat(runs).extracting(Run::text)
				.contains("Tindahan ni Mang Tomas Sari-Sari Store and General Merchandise");
	}

	@Test
	void layoutValuesAreKeptWithinSensibleLimits() {
		FormLayout.Placement p = new FormLayout.Placement(true, -5, 9999, 0, 200);
		assertThat(p.x()).isZero();
		assertThat(p.y()).isEqualTo(FormLayout.MAX_POSITION);
		assertThat(p.width()).isEqualTo(1);
		assertThat(p.fontSize()).isEqualTo(FormLayout.MAX_FONT);
		FormLayout layout = new FormLayout(true, 80, Double.NaN, null);
		assertThat(layout.shiftX()).isEqualTo(FormLayout.MAX_SHIFT);
		assertThat(layout.shiftY()).isEqualTo(-FormLayout.MAX_SHIFT);
		assertThat(layout.fields()).hasSize(FormField.values().length);
	}

	@Test
	void theDefaultPlacementsAreTheTemplatesInMillimetres() {
		FormLayout.Placement name = FormField.BUSINESS_NAME.defaultPlacement();
		assertThat(name).isEqualTo(new FormLayout.Placement(true, 10.9, 53.3, 35.3, 10));
		assertThat(FormField.CLEARANCE_TYPE.defaultPlacement().on()).isFalse();
		assertThat(FormField.TICK_NEW.isTick()).isTrue();
		assertThat(FormField.AMOUNT_PAID.valueFor(clearance(), SETTINGS)).isEqualTo("1,250.50");
		assertThat(FormField.OWNERSHIP.valueFor(clearance(), SETTINGS)).isEqualTo("Corporation, Partnership");
		assertThat(FormField.TICK_RENTED.valueFor(clearance(), SETTINGS)).isEqualTo("X");
		assertThat(FormField.TICK_OWNED.valueFor(clearance(), SETTINGS)).isEmpty();
	}
}
