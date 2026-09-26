package com.thub.areyes1.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

class ReportPeriodTest {

	static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

	@Test
	void withoutDatesTheReportCoversThisMonth() {
		assertThat(ReportPeriod.parse(null, "", TODAY)).isEqualTo(ReportPeriod.month(YearMonth.of(2026, 3)));
		assertThat(ReportPeriod.parse("not a date", "13/45/2026", TODAY))
				.isEqualTo(new ReportPeriod(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));
	}

	@Test
	void oneDateMeansThatDayAndReversedDatesAreSwapped() {
		assertThat(ReportPeriod.parse("2026-02-10", null, TODAY))
				.isEqualTo(new ReportPeriod(LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 10)));
		assertThat(ReportPeriod.parse(" 2026-02-20 ", "2026-02-01", TODAY))
				.isEqualTo(new ReportPeriod(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 20)));
	}

	@Test
	void labelsNameWholeMonthsAndYears() {
		assertThat(ReportPeriod.month(YearMonth.of(2026, 2)).label()).isEqualTo("February 2026");
		assertThat(ReportPeriod.year(2025).label()).isEqualTo("2025");
		assertThat(new ReportPeriod(TODAY, TODAY).label()).isEqualTo("Mar 15, 2026");
		assertThat(new ReportPeriod(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 2, 3)).label())
				.isEqualTo("Jan 5, 2026 – Feb 3, 2026");
	}

	@Test
	void slugsAreSafeInFileNames() {
		assertThat(ReportPeriod.month(YearMonth.of(2026, 2)).slug()).isEqualTo("2026-02");
		assertThat(ReportPeriod.year(2025).slug()).isEqualTo("2025");
		assertThat(new ReportPeriod(TODAY, TODAY).slug()).isEqualTo("2026-03-15");
		assertThat(new ReportPeriod(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 2, 3)).slug())
				.isEqualTo("2026-01-05_to_2026-02-03");
	}

	@Test
	void presetsAreRelativeToToday() {
		assertThat(ReportPeriod.presets(TODAY)).extracting(p -> p.label() + " " + p.period().label())
				.containsExactly("Today Mar 15, 2026", "This month March 2026", "Last month February 2026",
						"This year 2026", "Last year 2025");
		assertThat(ReportPeriod.presets(LocalDate.of(2026, 1, 2)).get(2).period().label()).isEqualTo("December 2025");
	}
}
