package com.thub.areyes1.report;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * The dates a report covers, from {@code from} to {@code to}, both inclusive.
 */
public record ReportPeriod(LocalDate from, LocalDate to) {

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

	public ReportPeriod {
		if (from.isAfter(to)) {
			LocalDate swap = from;
			from = to;
			to = swap;
		}
	}

	public static ReportPeriod month(YearMonth month) {
		return new ReportPeriod(month.atDay(1), month.atEndOfMonth());
	}

	public static ReportPeriod year(int year) {
		return new ReportPeriod(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
	}

	/**
	 * The period asked for in a link or form. With only one date, the report covers
	 * that day; with neither, the current month.
	 */
	public static ReportPeriod parse(String from, String to, LocalDate today) {
		LocalDate start = date(from);
		LocalDate end = date(to);
		if (start == null && end == null) {
			return month(YearMonth.from(today));
		}
		return new ReportPeriod(start == null ? end : start, end == null ? start : end);
	}

	private static LocalDate date(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(value.strip());
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	/** The quick choices shown above a report. */
	public static List<Preset> presets(LocalDate today) {
		YearMonth thisMonth = YearMonth.from(today);
		return List.of(
				new Preset("Today", new ReportPeriod(today, today)),
				new Preset("This month", month(thisMonth)),
				new Preset("Last month", month(thisMonth.minusMonths(1))),
				new Preset("This year", year(today.getYear())),
				new Preset("Last year", year(today.getYear() - 1)));
	}

	public record Preset(String label, ReportPeriod period) {
	}

	public long days() {
		return ChronoUnit.DAYS.between(from, to) + 1;
	}

	/** "September 2026", "2026", "Sep 26, 2026" or "Sep 1, 2026 – Oct 15, 2026". */
	public String label() {
		if (from.equals(to)) {
			return DAY.format(from);
		}
		if (isWholeMonth()) {
			return MONTH.format(from);
		}
		if (isWholeYear()) {
			return String.valueOf(from.getYear());
		}
		return DAY.format(from) + " – " + DAY.format(to);
	}

	/** A short name for file names, e.g. "2026-09", "2026" or "2026-09-01_to_2026-10-15". */
	public String slug() {
		if (isWholeMonth()) {
			return YearMonth.from(from).toString();
		}
		if (isWholeYear()) {
			return String.valueOf(from.getYear());
		}
		return from.equals(to) ? from.toString() : from + "_to_" + to;
	}

	private boolean isWholeMonth() {
		return from.getDayOfMonth() == 1 && to.equals(YearMonth.from(from).atEndOfMonth());
	}

	private boolean isWholeYear() {
		return from.getDayOfYear() == 1 && to.equals(Year.of(from.getYear()).atMonth(12).atEndOfMonth());
	}
}
