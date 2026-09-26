package com.thub.areyes1.print.form;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import com.thub.areyes1.clearance.Clearance;
import com.thub.areyes1.print.PaperSize;
import com.thub.areyes1.settings.BarangaySettings;

/**
 * Prints only the values of a clearance, each at its place on the office's
 * pre-printed form, the way the desktop app's Jasper template was meant to.
 * Positions are exact: text is placed directly on the PDF page, not laid out.
 */
public final class FormOverlayPrinter {

	private static final float POINTS_PER_MM = 72f / 25.4f;
	/** A value too long for its space is printed smaller, but never below this share of its size. */
	private static final float MIN_SHRINK = 0.6f;

	private FormOverlayPrinter() {
	}

	/**
	 * @param outlines draw a thin box around each field's space, for lining the layout
	 *                 up against a blank form
	 */
	public static byte[] print(Clearance clearance, BarangaySettings settings, FormLayout layout, PaperSize paper,
			boolean outlines) {
		try (PDDocument doc = new PDDocument()) {
			PDRectangle size = paper == PaperSize.LONG ? new PDRectangle(612, 936) : PDRectangle.LETTER;
			PDPage page = new PDPage(size);
			doc.addPage(page);
			PDType0Font font;
			try (InputStream in = FormOverlayPrinter.class.getResourceAsStream("/fonts/SourceSerif4-Regular.ttf")) {
				font = PDType0Font.load(doc, in);
			}
			try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
				for (FormField field : FormField.values()) {
					FormLayout.Placement p = layout.placement(field);
					if (!p.on()) {
						continue;
					}
					float x = mm(p.x() + layout.shiftX());
					float top = size.getHeight() - mm(p.y() + layout.shiftY());
					float width = mm(p.width());
					float fontSize = (float) p.fontSize();
					if (outlines) {
						content.setLineWidth(0.4f);
						content.setStrokingColor(0.6f, 0.6f, 0.6f);
						content.addRect(x, top - fontSize * 1.25f, width, fontSize * 1.25f);
						content.stroke();
					}
					String text = printable(font, field.valueFor(clearance, settings));
					if (text.isEmpty()) {
						continue;
					}
					float natural = font.getStringWidth(text) / 1000 * fontSize;
					if (natural > width) {
						fontSize = Math.max(fontSize * MIN_SHRINK, fontSize * width / natural);
					}
					float ascent = font.getFontDescriptor().getAscent() / 1000 * fontSize;
					content.beginText();
					content.setFont(font, fontSize);
					content.setNonStrokingColor(0f, 0f, 0f);
					content.newLineAtOffset(x, top - ascent);
					content.showText(text);
					content.endText();
				}
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			doc.save(out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("Could not print the form for clearance " + clearance.id(), e);
		}
	}

	/** Drops characters the font can't draw (and line breaks) instead of failing the whole print. */
	static String printable(PDType0Font font, String text) {
		StringBuilder out = new StringBuilder();
		text.replaceAll("\\s+", " ").codePoints().forEach(cp -> {
			String ch = new String(Character.toChars(cp));
			try {
				font.encode(ch);
				out.append(ch);
			} catch (IOException | IllegalArgumentException e) {
				// not in the font
			}
		});
		return out.toString().strip();
	}

	private static float mm(double millimetres) {
		return (float) millimetres * POINTS_PER_MM;
	}
}
