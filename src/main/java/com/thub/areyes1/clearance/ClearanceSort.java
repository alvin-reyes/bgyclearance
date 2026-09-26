package com.thub.areyes1.clearance;

/** Columns the clearance list can be sorted by. */
public enum ClearanceSort {
	ISSUED("COALESCE(issued_on, '')"),
	CONTROL("control_no"),
	NAME("LOWER(COALESCE(name, ''))"),
	AMOUNT("CAST(amount_paid AS REAL)");

	private final String expression;

	ClearanceSort(String expression) {
		this.expression = expression;
	}

	/** ORDER BY clause; the id breaks ties so paging is stable. */
	String orderBy(boolean descending) {
		String dir = descending ? "DESC" : "ASC";
		return expression + " " + dir + ", id " + dir;
	}

	/** Parses a request parameter, falling back to the default sort. */
	public static ClearanceSort from(String value) {
		if (value != null) {
			for (ClearanceSort s : values()) {
				if (s.name().equalsIgnoreCase(value)) {
					return s;
				}
			}
		}
		return ISSUED;
	}
}
