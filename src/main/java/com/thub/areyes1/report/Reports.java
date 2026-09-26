package com.thub.areyes1.report;

import org.springframework.stereotype.Service;

import com.thub.areyes1.clearance.ClearanceRepository;
import com.thub.areyes1.clearance.ClearanceType;

/** Builds reports from the stored clearances. */
@Service
public class Reports {

	private final ClearanceRepository clearances;

	public Reports(ClearanceRepository clearances) {
		this.clearances = clearances;
	}

	public ClearanceReport build(ReportPeriod period, ClearanceType type) {
		return new ClearanceReport(period, type, clearances.issuedBetween(period.from(), period.to(), type),
				clearances.countUndated());
	}
}
