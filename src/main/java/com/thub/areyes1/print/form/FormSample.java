package com.thub.areyes1.print.form;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.thub.areyes1.clearance.Building;
import com.thub.areyes1.clearance.Clearance;
import com.thub.areyes1.clearance.ClearanceType;
import com.thub.areyes1.settings.BarangaySettings;

/** Made-up values with every field filled in, for alignment test prints. */
public final class FormSample {

	private FormSample() {
	}

	public static Clearance clearance(LocalDate today) {
		return new Clearance(0L, ClearanceType.NEW, 2026001, today, "Tindahan ni Mang Tomas",
				"45 Bonifacio Ave., Purok 3", "Sari-sari store", "75,000", Building.OWNED, false, true, false, false,
				"Tomas Reyes", "Poblacion HOA", "Maria Santos", 12, 556677, new BigDecimal("1250.50"));
	}

	/** The office's own details where entered, so the test looks like a real clearance. */
	public static BarangaySettings settings(BarangaySettings s) {
		return new BarangaySettings(orElse(s.barangayName(), "San Isidro"), s.municipality(), s.province(),
				orElse(s.punongBarangay(), "Hon. Juan Dela Cruz"), orElse(s.secretary(), "Liza Tan"));
	}

	private static String orElse(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}
}
