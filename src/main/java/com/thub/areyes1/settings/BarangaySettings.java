package com.thub.areyes1.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Details of the barangay, printed on every clearance.
 *
 * @param barangayName   e.g. "Poblacion"
 * @param municipality   city or municipality, e.g. "Cebu City"
 * @param province       province, e.g. "Cebu"
 * @param punongBarangay name of the punong barangay who signs clearances
 * @param secretary      name of the barangay secretary, if printed
 */
public record BarangaySettings(
		@NotBlank(message = "Enter the barangay name") @Size(max = 120) String barangayName,
		@Size(max = 120) String municipality,
		@Size(max = 120) String province,
		@NotBlank(message = "Enter the punong barangay's name") @Size(max = 120) String punongBarangay,
		@Size(max = 120) String secretary) {

	public static BarangaySettings empty() {
		return new BarangaySettings("", "", "", "", "");
	}

	/** True once the details needed for a proper printout have been entered. */
	public boolean isConfigured() {
		return barangayName != null && !barangayName.isBlank();
	}
}
