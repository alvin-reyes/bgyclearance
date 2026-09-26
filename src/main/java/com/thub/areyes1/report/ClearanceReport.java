package com.thub.areyes1.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.thub.areyes1.clearance.Clearance;
import com.thub.areyes1.clearance.ClearanceType;

/**
 * Clearances issued in a period, with their totals, collections over time and a
 * breakdown by type of business.
 *
 * @param period      the dates covered
 * @param type        only this type, or null for both
 * @param clearances  the clearances issued in the period, oldest first
 * @param undated     clearances with no date issued, which no report can include
 */
public record ClearanceReport(ReportPeriod period, ClearanceType type, List<Clearance> clearances, long undated) {

	/** Types of business listed by name; the rest are combined into one row. */
	static final int TOP_BUSINESS_TYPES = 10;

	private static final Locale EN = Locale.ENGLISH;

	public int count() {
		return clearances.size();
	}

	public BigDecimal collected() {
		return sum(clearances);
	}

	public long newCount() {
		return clearances.stream().filter(c -> c.type() == ClearanceType.NEW).count();
	}

	public long renewalCount() {
		return count() - newCount();
	}

	public BigDecimal newCollected() {
		return sum(clearances.stream().filter(c -> c.type() == ClearanceType.NEW).toList());
	}

	public BigDecimal renewalCollected() {
		return collected().subtract(newCollected());
	}

	/** "All clearances", "New businesses" or "Renewals". */
	public String typeLabel() {
		return type == null ? "All clearances" : type == ClearanceType.NEW ? "New businesses" : "Renewals";
	}

	// ---- Collections over time -------------------------------------------------

	public enum Grouping {
		DAY("day"), MONTH("month"), YEAR("year");

		private final String noun;

		Grouping(String noun) {
			this.noun = noun;
		}

		public String noun() {
			return noun;
		}
	}

	/** By day for up to two months, by month for up to three years, otherwise by year. */
	public Grouping grouping() {
		long days = period.days();
		return days <= 62 ? Grouping.DAY : days <= 1096 ? Grouping.MONTH : Grouping.YEAR;
	}

	/**
	 * @param start     first day of the day, month or year
	 * @param label     full name, e.g. "Sep 3, 2026" or "September 2026"
	 * @param axisLabel short name for the chart's axis, e.g. "Sep 3" or "Sep"
	 */
	public record Bucket(LocalDate start, String label, String axisLabel, long count, BigDecimal amount) {
	}

	/** One entry per day, month or year of the period, including those with nothing issued. */
	public List<Bucket> buckets() {
		Grouping grouping = grouping();
		Map<LocalDate, List<Clearance>> byStart = new LinkedHashMap<>();
		for (LocalDate d = startOf(period.from(), grouping); !d.isAfter(period.to()); d = next(d, grouping)) {
			byStart.put(d, new ArrayList<>());
		}
		for (Clearance c : clearances) {
			byStart.get(startOf(c.issuedOn(), grouping)).add(c);
		}
		boolean manyYears = period.from().getYear() != period.to().getYear();
		List<Bucket> buckets = new ArrayList<>();
		byStart.forEach((start, items) -> buckets.add(new Bucket(start, label(start, grouping),
				axisLabel(start, grouping, manyYears), items.size(), sum(items))));
		return buckets;
	}

	private static LocalDate startOf(LocalDate d, Grouping g) {
		return switch (g) {
			case DAY -> d;
			case MONTH -> d.withDayOfMonth(1);
			case YEAR -> d.withDayOfYear(1);
		};
	}

	private static LocalDate next(LocalDate d, Grouping g) {
		return switch (g) {
			case DAY -> d.plusDays(1);
			case MONTH -> d.plusMonths(1);
			case YEAR -> d.plusYears(1);
		};
	}

	private static String label(LocalDate start, Grouping g) {
		return switch (g) {
			case DAY -> DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", EN).format(start);
			case MONTH -> DateTimeFormatter.ofPattern("MMMM yyyy", EN).format(start);
			case YEAR -> String.valueOf(start.getYear());
		};
	}

	private static String axisLabel(LocalDate start, Grouping g, boolean manyYears) {
		return switch (g) {
			case DAY -> DateTimeFormatter.ofPattern("MMM d", EN).format(start);
			case MONTH -> DateTimeFormatter.ofPattern(manyYears ? "MMM yyyy" : "MMM", EN).format(start);
			case YEAR -> String.valueOf(start.getYear());
		};
	}

	/**
	 * A column chart of the amount collected per bucket.
	 *
	 * @param bars  one per bucket, with its height as a percentage of the axis
	 * @param ticks gridline values from zero up to the top of the axis
	 */
	public record Chart(List<Bar> bars, List<Tick> ticks) {
	}

	/**
	 * @param showLabel whether its axis label is printed; with many columns, only some are
	 * @param major     whether the label is kept on narrow screens, where every other one is dropped
	 */
	public record Bar(Bucket bucket, double height, boolean showLabel, boolean major) {
	}

	public record Tick(BigDecimal value, double position) {
	}

	/** The chart, or null when nothing was collected. */
	public Chart chart() {
		List<Bucket> buckets = buckets();
		BigDecimal max = buckets.stream().map(Bucket::amount).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
		if (max.signum() <= 0) {
			return null;
		}
		BigDecimal step = niceStep(max, 4);
		int tickCount = max.divide(step, 0, RoundingMode.CEILING).intValue();
		BigDecimal top = step.multiply(BigDecimal.valueOf(tickCount));
		List<Tick> ticks = new ArrayList<>();
		for (int i = 0; i <= tickCount; i++) {
			ticks.add(new Tick(step.multiply(BigDecimal.valueOf(i)), 100.0 * i / tickCount));
		}
		// Label about eight columns, always including the first.
		int every = Math.max(1, (int) Math.ceil(buckets.size() / 8.0));
		List<Bar> bars = new ArrayList<>();
		for (int i = 0; i < buckets.size(); i++) {
			Bucket b = buckets.get(i);
			double height = b.amount().doubleValue() * 100.0 / top.doubleValue();
			bars.add(new Bar(b, height, i % every == 0, i % (every * 2) == 0));
		}
		return new Chart(bars, ticks);
	}

	/** A round step (1, 2, 2.5 or 5 × a power of ten) giving about {@code target} gridlines up to max. */
	static BigDecimal niceStep(BigDecimal max, int target) {
		double rough = max.doubleValue() / target;
		double magnitude = Math.pow(10, Math.floor(Math.log10(rough)));
		for (double m : new double[] { 1, 2, 2.5, 5, 10 }) {
			if (m * magnitude >= rough) {
				return BigDecimal.valueOf(m * magnitude).stripTrailingZeros();
			}
		}
		return BigDecimal.valueOf(10 * magnitude);
	}

	// ---- By type of business ---------------------------------------------------

	/** @param other true for the row that combines the less common types */
	public record BusinessType(String name, long count, BigDecimal amount, boolean other) {
	}

	/** Types of business, most clearances first; spelling and case differences are merged. */
	public List<BusinessType> byTypeOfBusiness() {
		Map<String, List<Clearance>> groups = new LinkedHashMap<>();
		Map<String, String> names = new LinkedHashMap<>();
		for (Clearance c : clearances) {
			String name = c.typeOfBusiness() == null || c.typeOfBusiness().isBlank() ? "Not stated"
					: c.typeOfBusiness().strip().replaceAll("\\s+", " ");
			String key = name.toLowerCase(EN);
			names.putIfAbsent(key, name);
			groups.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
		}
		List<BusinessType> all = new ArrayList<>();
		groups.forEach((key, items) -> all.add(new BusinessType(names.get(key), items.size(), sum(items), false)));
		all.sort(Comparator.comparingLong(BusinessType::count).reversed()
				.thenComparing(BusinessType::amount, Comparator.reverseOrder())
				.thenComparing(t -> t.name().toLowerCase(EN)));
		if (all.size() <= TOP_BUSINESS_TYPES + 1) {
			return all;
		}
		List<BusinessType> rest = all.subList(TOP_BUSINESS_TYPES, all.size());
		List<BusinessType> shown = new ArrayList<>(all.subList(0, TOP_BUSINESS_TYPES));
		shown.add(new BusinessType(rest.size() + " other types",
				rest.stream().mapToLong(BusinessType::count).sum(),
				rest.stream().map(BusinessType::amount).reduce(BigDecimal.ZERO, BigDecimal::add), true));
		return shown;
	}

	/** Share of this report's clearances, as a percentage. */
	public double share(long part) {
		return count() == 0 ? 0 : part * 100.0 / count();
	}

	private static BigDecimal sum(List<Clearance> items) {
		return items.stream()
				.map(c -> c.amountPaid() == null ? BigDecimal.ZERO : c.amountPaid())
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}
}
