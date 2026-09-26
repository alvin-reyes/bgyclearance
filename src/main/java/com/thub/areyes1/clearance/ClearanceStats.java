package com.thub.areyes1.clearance;

import java.math.BigDecimal;

/**
 * Totals for the dashboard.
 *
 * @param total             all clearances
 * @param totalCollected    sum of amounts paid
 * @param newCount          clearances for new businesses
 * @param renewalCount      renewals
 * @param year              the current year
 * @param yearCount         clearances issued this year
 * @param yearCollected     amounts paid for clearances issued this year
 */
public record ClearanceStats(long total, BigDecimal totalCollected, long newCount, long renewalCount, int year,
		long yearCount, BigDecimal yearCollected) {
}
