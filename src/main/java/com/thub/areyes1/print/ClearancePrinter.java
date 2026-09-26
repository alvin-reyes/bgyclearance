package com.thub.areyes1.print;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;

import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.thub.areyes1.clearance.Clearance;
import com.thub.areyes1.print.form.FormLayout;
import com.thub.areyes1.print.form.FormLayouts;
import com.thub.areyes1.print.form.FormOverlayPrinter;
import com.thub.areyes1.print.form.FormSample;
import com.thub.areyes1.report.ClearanceReport;
import com.thub.areyes1.settings.BarangaySettings;
import com.thub.areyes1.settings.SettingsRepository;

/**
 * Prints a clearance: renders {@code templates/print/clearance.html} and converts
 * it to PDF. Edit that template (plain HTML and CSS) to change the layout.
 */
@Component
public class ClearancePrinter {

	/** Font families used by the print stylesheets (Source Serif 4, SIL Open Font License). */
	static final String FONT = "Source Serif 4";
	static final String DISPLAY_FONT = "Source Serif 4 Display";

	private final ITemplateEngine templates;
	private final Clock clock;
	private final SettingsRepository settingsStore;
	private final FormLayouts forms;

	public ClearancePrinter(ITemplateEngine templates, Clock clock, SettingsRepository settingsStore,
			FormLayouts forms) {
		this.templates = templates;
		this.clock = clock;
		this.settingsStore = settingsStore;
		this.forms = forms;
	}

	/** The paper chosen in Settings; letter unless long bond was chosen. */
	public PaperSize paperSize() {
		return settingsStore.get(PaperSize.SETTING).flatMap(PaperSize::parse).orElse(PaperSize.LETTER);
	}

	public void setPaperSize(PaperSize paper) {
		settingsStore.put(PaperSize.SETTING, paper.name());
	}

	/**
	 * The clearance as it should be printed: only its values, placed on the office's
	 * pre-printed form, if Settings says forms are pre-printed; otherwise the complete
	 * clearance on plain paper.
	 */
	public byte[] print(Clearance clearance, BarangaySettings settings) {
		FormLayout layout = forms.load();
		if (layout.preprinted()) {
			return FormOverlayPrinter.print(clearance, settings, layout, paperSize(), false);
		}
		return printPlain(clearance, settings);
	}

	/**
	 * A sample clearance printed with a layout, with a box drawn around each field,
	 * to check how it lines up with the pre-printed form.
	 */
	public byte[] formTest(FormLayout layout, BarangaySettings settings) {
		return FormOverlayPrinter.print(FormSample.clearance(LocalDate.now(clock)), FormSample.settings(settings),
				layout, paperSize(), true);
	}

	/** The complete clearance, letterhead and all, for plain paper. */
	public byte[] printPlain(Clearance clearance, BarangaySettings settings) {
		Context ctx = context(settings);
		ctx.setVariable("c", clearance);
		return render("print/clearance", ctx, "clearance " + clearance.id());
	}

	/** A one-page test print showing the barangay details and which printer was used. */
	public byte[] testPage(BarangaySettings settings, String printerName) {
		Context ctx = context(settings);
		ctx.setVariable("printerName", printerName);
		return render("print/test-page", ctx, "test page");
	}

	/** A report of the clearances issued in a period: totals, by type of business, and the full list. */
	public byte[] report(ClearanceReport report, BarangaySettings settings) {
		Context ctx = context(settings);
		ctx.setVariable("r", report);
		return render("print/report", ctx, "report for " + report.period().label());
	}

	private Context context(BarangaySettings settings) {
		Context ctx = new Context(Locale.ENGLISH);
		ctx.setVariable("s", settings);
		ctx.setVariable("printedOn", LocalDate.now(clock));
		PaperSize paper = paperSize();
		ctx.setVariable("paper", paper);
		ctx.setVariable("pageSize", paper.css());
		// A blank to write on by hand. Passed in because "__" is Thymeleaf preprocessing syntax.
		ctx.setVariable("blank", "______________");
		return ctx;
	}

	private byte[] render(String template, Context ctx, String what) {
		String html = templates.process(template, ctx);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PdfRendererBuilder pdf = new PdfRendererBuilder();
		pdf.useFastMode();
		pdf.withHtmlContent(html, null);
		// Embedded so the peso sign and accented names print the same on any machine.
		pdf.useFont(() -> font("SourceSerif4-Regular"), FONT, 400, FontStyle.NORMAL, true);
		pdf.useFont(() -> font("SourceSerif4-It"), FONT, 400, FontStyle.ITALIC, true);
		pdf.useFont(() -> font("SourceSerif4-Semibold"), FONT, 600, FontStyle.NORMAL, true);
		pdf.useFont(() -> font("SourceSerif4-Bold"), FONT, 700, FontStyle.NORMAL, true);
		pdf.useFont(() -> font("SourceSerif4Display-Semibold"), DISPLAY_FONT, 600, FontStyle.NORMAL, true);
		pdf.toStream(out);
		try {
			pdf.run();
		} catch (IOException e) {
			throw new UncheckedIOException("Could not print " + what, e);
		}
		return out.toByteArray();
	}

	private static java.io.InputStream font(String file) {
		return ClearancePrinter.class.getResourceAsStream("/fonts/" + file + ".ttf");
	}
}
