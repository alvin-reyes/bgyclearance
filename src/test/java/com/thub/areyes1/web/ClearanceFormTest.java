package com.thub.areyes1.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.thub.areyes1.clearance.Building;
import com.thub.areyes1.clearance.Clearance;
import com.thub.areyes1.clearance.ClearanceType;

class ClearanceFormTest {

	@Test
	void newFormDefaultsToANewBusinessIssuedToday() {
		ClearanceForm f = ClearanceForm.newFor(LocalDate.of(2026, 3, 15));

		assertThat(f.getType()).isEqualTo(ClearanceType.NEW);
		assertThat(f.getIssuedOn()).isEqualTo(LocalDate.of(2026, 3, 15));
	}

	@Test
	void roundTripsAClearance() {
		Clearance c = new Clearance(5L, ClearanceType.RENEWAL, 1001, LocalDate.of(2026, 1, 2), "Store", "Addr",
				"Retail", "10,000", Building.OWNED, true, false, true, false, "Owner", "HOA", "President", 3, 44,
				new BigDecimal("12.50"));

		assertThat(ClearanceForm.from(c).toClearance(5L)).isEqualTo(c);
	}

	@Test
	void trimsTextBlanksBecomeNullAndAmountsGetTwoDecimals() {
		ClearanceForm f = new ClearanceForm();
		f.setBusinessName("  Tomas Store ");
		f.setAddress("   ");
		f.setAmountPaid(new BigDecimal("250.5"));

		Clearance c = f.toClearance(null);

		assertThat(c.businessName()).isEqualTo("Tomas Store");
		assertThat(c.address()).isNull();
		assertThat(c.amountPaid()).isEqualTo(new BigDecimal("250.50"));
	}
}
