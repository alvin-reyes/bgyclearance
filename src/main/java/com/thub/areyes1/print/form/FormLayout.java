package com.thub.areyes1.print.form;

import java.util.EnumMap;
import java.util.Map;

/**
 * Where each value goes on the pre-printed form, in millimetres from the top-left
 * corner of the paper, plus a shift applied to everything to make up for how a
 * particular printer feeds the paper.
 *
 * @param preprinted true to print clearances onto pre-printed forms (values only);
 *                   false to print the complete clearance on plain paper
 * @param shiftX     millimetres to move every value right (negative: left)
 * @param shiftY     millimetres to move every value down (negative: up)
 * @param fields     a placement for every field
 */
public record FormLayout(boolean preprinted, double shiftX, double shiftY, Map<FormField, Placement> fields) {

	/** Jasper's default text size, which the original template used. */
	public static final double DEFAULT_FONT_SIZE = 10;

	/** The limits the editor accepts, in millimetres and points. */
	public static final double MAX_POSITION = 400;
	public static final double MAX_SHIFT = 50;
	public static final double MIN_FONT = 5;
	public static final double MAX_FONT = 36;

	/**
	 * @param on       whether the value is printed
	 * @param x        distance from the left edge of the paper to the start of the value
	 * @param y        distance from the top edge of the paper to the top of the value
	 * @param width    space available; longer values are printed smaller to fit
	 * @param fontSize text size in points
	 */
	public record Placement(boolean on, double x, double y, double width, double fontSize) {

		public Placement {
			x = clamp(x, 0, MAX_POSITION);
			y = clamp(y, 0, MAX_POSITION);
			width = clamp(width, 1, MAX_POSITION);
			fontSize = clamp(fontSize, MIN_FONT, MAX_FONT);
		}
	}

	public FormLayout {
		shiftX = clamp(shiftX, -MAX_SHIFT, MAX_SHIFT);
		shiftY = clamp(shiftY, -MAX_SHIFT, MAX_SHIFT);
		EnumMap<FormField, Placement> all = new EnumMap<>(FormField.class);
		for (FormField f : FormField.values()) {
			Placement p = fields == null ? null : fields.get(f);
			all.put(f, p == null ? f.defaultPlacement() : p);
		}
		fields = Map.copyOf(all);
	}

	/** Plain paper, and every field where the original template put it. */
	public static FormLayout defaults() {
		return new FormLayout(false, 0, 0, Map.of());
	}

	public Placement placement(FormField field) {
		return fields.get(field);
	}

	public FormLayout withFields(Map<FormField, Placement> changed) {
		return new FormLayout(preprinted, shiftX, shiftY, changed);
	}

	private static double clamp(double v, double min, double max) {
		if (Double.isNaN(v)) {
			return min;
		}
		return Math.max(min, Math.min(max, v));
	}
}
