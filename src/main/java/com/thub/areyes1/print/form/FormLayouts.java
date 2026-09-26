package com.thub.areyes1.print.form;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.thub.areyes1.settings.SettingsRepository;

/**
 * Stores the pre-printed form layout in the settings table. Fields are kept one per
 * line ("BUSINESS_NAME on 10.9 53.3 35.3 10"), so a layout can be read and fixed by
 * hand if ever needed; fields added in later versions start at their defaults.
 */
@Repository
public class FormLayouts {

	static final String MODE = "form_mode";
	static final String SHIFT = "form_shift";
	static final String FIELDS = "form_fields";
	static final String BACKGROUND = "form_background";

	/** A scan of the blank form, shown behind the layout editor, as a data: URL. */
	public static final int MAX_BACKGROUND_BYTES = 8 * 1024 * 1024;

	private final SettingsRepository settings;

	public FormLayouts(SettingsRepository settings) {
		this.settings = settings;
	}

	public FormLayout load() {
		boolean preprinted = settings.get(MODE).map("preprinted"::equals).orElse(false);
		double[] shift = settings.get(SHIFT).map(FormLayouts::numbers).filter(n -> n.length == 2)
				.orElse(new double[] { 0, 0 });
		Map<FormField, FormLayout.Placement> fields = new EnumMap<>(FormField.class);
		settings.get(FIELDS).ifPresent(text -> text.lines().forEach(line -> parse(line, fields)));
		return new FormLayout(preprinted, shift[0], shift[1], fields);
	}

	@Transactional
	public void save(FormLayout layout) {
		settings.put(MODE, layout.preprinted() ? "preprinted" : "plain");
		settings.put(SHIFT, format(layout.shiftX()) + " " + format(layout.shiftY()));
		StringBuilder text = new StringBuilder();
		for (FormField f : FormField.values()) {
			FormLayout.Placement p = layout.placement(f);
			text.append(f.name()).append(' ').append(p.on() ? "on" : "off").append(' ')
					.append(format(p.x())).append(' ').append(format(p.y())).append(' ')
					.append(format(p.width())).append(' ').append(format(p.fontSize())).append('\n');
		}
		settings.put(FIELDS, text.toString());
	}

	public Optional<String> background() {
		return settings.get(BACKGROUND);
	}

	public void saveBackground(String dataUrl) {
		settings.put(BACKGROUND, dataUrl);
	}

	private static void parse(String line, Map<FormField, FormLayout.Placement> fields) {
		String[] parts = line.strip().split("\\s+");
		if (parts.length != 6) {
			return;
		}
		double[] n = numbers(parts[2] + " " + parts[3] + " " + parts[4] + " " + parts[5]);
		if (n.length != 4) {
			return;
		}
		try {
			fields.put(FormField.valueOf(parts[0]),
					new FormLayout.Placement("on".equals(parts[1]), n[0], n[1], n[2], n[3]));
		} catch (IllegalArgumentException e) {
			// A field this version doesn't know: ignore it.
		}
	}

	/** The numbers in the text, or none if any of them can't be read. */
	private static double[] numbers(String text) {
		String[] parts = text.strip().split("\\s+");
		double[] n = new double[parts.length];
		try {
			for (int i = 0; i < parts.length; i++) {
				n[i] = Double.parseDouble(parts[i]);
			}
		} catch (NumberFormatException e) {
			return new double[0];
		}
		return n;
	}

	static String format(double v) {
		return String.format(Locale.ROOT, "%.1f", v);
	}
}
