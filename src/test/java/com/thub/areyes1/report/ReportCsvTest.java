package com.thub.areyes1.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.thub.areyes1.clearance.Building;
import com.thub.areyes1.clearance.Clearance;
import com.thub.areyes1.clearance.ClearanceType;

class ReportCsvTest {

	@Test
	void writesAnExcelFriendlyFile() {
		Clearance c = new Clearance(1L, ClearanceType.RENEWAL, 2026001, LocalDate.of(2026, 3, 2), "Niño's \"Best\" Store",
				"12 Rizal St., Purok 3", "=HYPERLINK(\"x\")", null, Building.OWNED, true, false, true, false,
				"-Juan", null, null, null, 556677, new BigDecimal("1250.5"));
		Clearance bare = new Clearance(2L, ClearanceType.NEW, null, LocalDate.of(2026, 3, 3), "Bare", null, null, null,
				null, false, false, false, false, null, null, null, null, null, null);
		byte[] csv = ReportCsv.write(new ClearanceReport(ReportPeriod.month(YearMonth.of(2026, 3)), null,
				List.of(c, bare), 0));

		assertThat(csv).startsWith(0xEF, 0xBB, 0xBF);
		String[] lines = new String(csv, StandardCharsets.UTF_8).substring(1).split("\r\n", -1);
		assertThat(lines).containsExactly(
				"Control no.,Date issued,Type,Business name,Address,Type of business,Owner / manager,Kind of ownership,O.R. no.,Amount paid",
				"2026001,2026-03-02,Renewal,\"Niño's \"\"Best\"\" Store\",\"12 Rizal St., Purok 3\",\"'=HYPERLINK(\"\"x\"\")\",'-Juan,\"Corporation, Partnership\",556677,1250.50",
				",2026-03-03,New,Bare,,,,,,",
				"");
	}
}
