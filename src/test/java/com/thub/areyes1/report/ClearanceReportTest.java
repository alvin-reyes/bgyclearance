package com.thub.areyes1.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.thub.areyes1.clearance.Clearance;
import com.thub.areyes1.clearance.ClearanceType;
import com.thub.areyes1.report.ClearanceReport.Bucket;
import com.thub.areyes1.report.ClearanceReport.BusinessType;
import com.thub.areyes1.report.ClearanceReport.Grouping;

class ClearanceReportTest {

	static Clearance issued(String day, ClearanceType type, String business, String amount) {
		return new Clearance(1L, type, 1, LocalDate.parse(day), "Store", null, business, null, null, false, false,
				false, false, null, null, null, null, null, amount == null ? null : new BigDecimal(amount));
	}

	static final ReportPeriod MARCH = ReportPeriod.month(YearMonth.of(2026, 3));

	@Test
	void totalsSplitNewAndRenewal() {
		ClearanceReport r = new ClearanceReport(MARCH, null, List.of(
				issued("2026-03-02", ClearanceType.NEW, "Bakery", "250.50"),
				issued("2026-03-02", ClearanceType.RENEWAL, "Bakery", "300"),
				issued("2026-03-09", ClearanceType.NEW, "Eatery", null)), 2);

		assertThat(r.count()).isEqualTo(3);
		assertThat(r.collected()).isEqualByComparingTo("550.50");
		assertThat(r.newCount()).isEqualTo(2);
		assertThat(r.renewalCount()).isEqualTo(1);
		assertThat(r.newCollected()).isEqualByComparingTo("250.50");
		assertThat(r.renewalCollected()).isEqualByComparingTo("300");
		assertThat(r.share(1)).isCloseTo(33.33, org.assertj.core.data.Offset.offset(0.01));
		assertThat(r.typeLabel()).isEqualTo("All clearances");
		assertThat(new ClearanceReport(MARCH, ClearanceType.RENEWAL, List.of(), 0).typeLabel()).isEqualTo("Renewals");
	}

	@Test
	void aMonthIsGroupedByDayIncludingDaysWithNothingIssued() {
		ClearanceReport r = new ClearanceReport(MARCH, null, List.of(
				issued("2026-03-02", ClearanceType.NEW, "Bakery", "250.50"),
				issued("2026-03-02", ClearanceType.NEW, "Bakery", "100"),
				issued("2026-03-31", ClearanceType.NEW, "Bakery", "50")), 0);

		assertThat(r.grouping()).isEqualTo(Grouping.DAY);
		List<Bucket> days = r.buckets();
		assertThat(days).hasSize(31);
		assertThat(days.get(1)).isEqualTo(new Bucket(LocalDate.of(2026, 3, 2), "Mon, Mar 2, 2026", "Mar 2", 2,
				new BigDecimal("350.50")));
		assertThat(days.get(2).count()).isZero();
		assertThat(days.get(30).amount()).isEqualByComparingTo("50");
	}

	@Test
	void longerPeriodsAreGroupedByMonthThenYear() {
		ReportPeriod quarter = new ReportPeriod(LocalDate.of(2025, 11, 15), LocalDate.of(2026, 2, 10));
		ClearanceReport r = new ClearanceReport(quarter, null, List.of(
				issued("2025-11-20", ClearanceType.NEW, "Bakery", "10"),
				issued("2026-02-10", ClearanceType.NEW, "Bakery", "20")), 0);

		assertThat(r.grouping()).isEqualTo(Grouping.MONTH);
		assertThat(r.buckets()).extracting(Bucket::label, Bucket::axisLabel, Bucket::count).containsExactly(
				org.assertj.core.groups.Tuple.tuple("November 2025", "Nov 2025", 1L),
				org.assertj.core.groups.Tuple.tuple("December 2025", "Dec 2025", 0L),
				org.assertj.core.groups.Tuple.tuple("January 2026", "Jan 2026", 0L),
				org.assertj.core.groups.Tuple.tuple("February 2026", "Feb 2026", 1L));

		ClearanceReport years = new ClearanceReport(new ReportPeriod(LocalDate.of(2020, 6, 1),
				LocalDate.of(2026, 3, 15)), null, List.of(issued("2021-01-01", ClearanceType.NEW, "x", "5")), 0);
		assertThat(years.grouping()).isEqualTo(Grouping.YEAR);
		assertThat(years.buckets()).extracting(Bucket::label)
				.containsExactly("2020", "2021", "2022", "2023", "2024", "2025", "2026");
	}

	@Test
	void chartUsesRoundGridlinesAboveTheTallestColumn() {
		ClearanceReport r = new ClearanceReport(MARCH, null, List.of(
				issued("2026-03-02", ClearanceType.NEW, "Bakery", "1250.50"),
				issued("2026-03-03", ClearanceType.NEW, "Bakery", "300")), 0);

		ClearanceReport.Chart chart = r.chart();
		assertThat(chart.ticks()).extracting(t -> t.value().toPlainString())
				.containsExactly("0", "500", "1000", "1500");
		assertThat(chart.ticks().getLast().position()).isEqualTo(100.0);
		assertThat(chart.bars()).hasSize(31);
		assertThat(chart.bars().get(1).height()).isCloseTo(1250.5 / 15, org.assertj.core.data.Offset.offset(0.001));
		// About eight labels on the axis, and half of those on phones.
		assertThat(chart.bars().stream().filter(ClearanceReport.Bar::showLabel).count()).isEqualTo(8);
		assertThat(chart.bars().stream().filter(ClearanceReport.Bar::major).count()).isEqualTo(4);

		assertThat(new ClearanceReport(MARCH, null, List.of(), 0).chart()).isNull();
	}

	@Test
	void niceSteps() {
		assertThat(ClearanceReport.niceStep(new BigDecimal("250"), 4).toPlainString()).isEqualTo("100");
		assertThat(ClearanceReport.niceStep(new BigDecimal("8951.50"), 4).toPlainString()).isEqualTo("2500");
		assertThat(ClearanceReport.niceStep(new BigDecimal("40"), 4).toPlainString()).isEqualTo("10");
		assertThat(ClearanceReport.niceStep(new BigDecimal("0.30"), 4).toPlainString()).isEqualTo("0.1");
	}

	@Test
	void typesOfBusinessAreMergedIgnoringCaseAndSpacing() {
		ClearanceReport r = new ClearanceReport(MARCH, null, List.of(
				issued("2026-03-02", ClearanceType.NEW, "Sari-sari store", "250"),
				issued("2026-03-02", ClearanceType.NEW, " sari-sari  STORE ", "250"),
				issued("2026-03-03", ClearanceType.NEW, "Bakery", "900"),
				issued("2026-03-03", ClearanceType.NEW, "  ", "100"),
				issued("2026-03-03", ClearanceType.NEW, null, "100")), 0);

		assertThat(r.byTypeOfBusiness()).containsExactly(
				new BusinessType("Sari-sari store", 2, new BigDecimal("500"), false),
				new BusinessType("Not stated", 2, new BigDecimal("200"), false),
				new BusinessType("Bakery", 1, new BigDecimal("900"), false));
	}

	@Test
	void theLessCommonTypesAreCombined() {
		List<Clearance> many = new ArrayList<>();
		for (int i = 0; i < 14; i++) {
			for (int n = 0; n <= 14 - i; n++) {
				many.add(issued("2026-03-02", ClearanceType.NEW, "Type " + (char) ('A' + i), "10"));
			}
		}
		List<BusinessType> types = new ClearanceReport(MARCH, null, many, 0).byTypeOfBusiness();

		assertThat(types).hasSize(ClearanceReport.TOP_BUSINESS_TYPES + 1);
		assertThat(types.getFirst().name()).isEqualTo("Type A");
		// Types K to N: 5 + 4 + 3 + 2 clearances.
		assertThat(types.getLast()).isEqualTo(new BusinessType("4 other types", 14, new BigDecimal("140"), true));
	}
}
