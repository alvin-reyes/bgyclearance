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
import com.thub.areyes1.settings.BarangaySettings;

/**
 * Prints a clearance: renders {@code templates/print/clearance.html} and converts
 * it to PDF. Edit that template (plain HTML and CSS) to change the layout.
 */
@Component
public class ClearancePrinter {

	/** Font family name used by the print stylesheet. */
	static final String FONT = "Liberation Serif";

	private final ITemplateEngine templates;
	private final Clock clock;

	public ClearancePrinter(ITemplateEngine templates, Clock clock) {
		this.templates = templates;
		this.clock = clock;
	}

	public byte[] print(Clearance clearance, BarangaySettings settings) {
		Context ctx = new Context(Locale.ENGLISH);
		ctx.setVariable("c", clearance);
		ctx.setVariable("s", settings);
		ctx.setVariable("printedOn", LocalDate.now(clock));
		// A blank to write on by hand. Passed in because "__" is Thymeleaf preprocessing syntax.
		ctx.setVariable("blank", "______________");
		String html = templates.process("print/clearance", ctx);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PdfRendererBuilder pdf = new PdfRendererBuilder();
		pdf.useFastMode();
		pdf.withHtmlContent(html, null);
		// Embedded so the peso sign and accented names print on any machine.
		pdf.useFont(() -> font("Regular"), FONT, 400, FontStyle.NORMAL, true);
		pdf.useFont(() -> font("Bold"), FONT, 700, FontStyle.NORMAL, true);
		pdf.useFont(() -> font("Italic"), FONT, 400, FontStyle.ITALIC, true);
		pdf.toStream(out);
		try {
			pdf.run();
		} catch (IOException e) {
			throw new UncheckedIOException("Could not print clearance " + clearance.id(), e);
		}
		return out.toByteArray();
	}

	private static java.io.InputStream font(String style) {
		return ClearancePrinter.class.getResourceAsStream("/fonts/LiberationSerif-" + style + ".ttf");
	}
}
