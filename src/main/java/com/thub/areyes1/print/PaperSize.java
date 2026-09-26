package com.thub.areyes1.print;

import java.util.Arrays;
import java.util.Optional;

/**
 * Paper that clearances and reports are printed on. Barangay offices commonly use
 * long bond (8.5 × 13 in, also called folio), which the desktop app's original
 * clearance template was laid out for; letter is the default.
 */
public enum PaperSize {
	LETTER("Letter", "8.5 × 11 in", "letter"),
	LONG("Long bond", "8.5 × 13 in", "8.5in 13in");

	/** Settings key the choice is stored under. */
	public static final String SETTING = "paper_size";

	private final String label;
	private final String dimensions;
	private final String css;

	PaperSize(String label, String dimensions, String css) {
		this.label = label;
		this.dimensions = dimensions;
		this.css = css;
	}

	public String label() {
		return label;
	}

	public String dimensions() {
		return dimensions;
	}

	/** The value for a CSS {@code @page { size: … }} rule. */
	public String css() {
		return css;
	}

	public static Optional<PaperSize> parse(String value) {
		return Arrays.stream(values()).filter(p -> p.name().equalsIgnoreCase(value == null ? "" : value.strip()))
				.findFirst();
	}
}
