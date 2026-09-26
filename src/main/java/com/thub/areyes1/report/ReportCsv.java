package com.thub.areyes1.report;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.thub.areyes1.clearance.Clearance;

/**
 * Writes a report's clearances as a CSV file that opens directly in Excel or
 * Google Sheets: UTF-8 with a byte order mark (so "₱" and "ñ" survive), commas,
 * CRLF line endings, and plain numbers for amounts so they can be summed.
 */
public final class ReportCsv {

	private static final List<String> HEADER = List.of("Control no.", "Date issued", "Type", "Business name",
			"Address", "Type of business", "Owner / manager", "Kind of ownership", "O.R. no.", "Amount paid");

	private ReportCsv() {
	}

	public static byte[] write(ClearanceReport report) {
		StringBuilder csv = new StringBuilder("﻿");
		line(csv, HEADER);
		for (Clearance c : report.clearances()) {
			line(csv, List.of(
					c.controlNumber() == null ? "" : c.controlNumber().toString(),
					c.issuedOn().toString(),
					c.type().label(),
					text(c.businessName()),
					text(c.address()),
					text(c.typeOfBusiness()),
					text(c.ownerName()),
					text(c.ownershipKinds()),
					c.orNumber() == null ? "" : c.orNumber().toString(),
					c.amountPaid() == null ? "" : c.amountPaid().setScale(2, RoundingMode.HALF_UP).toPlainString()));
		}
		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static void line(StringBuilder csv, List<String> cells) {
		for (int i = 0; i < cells.size(); i++) {
			if (i > 0) {
				csv.append(',');
			}
			csv.append(quote(cells.get(i)));
		}
		csv.append("\r\n");
	}

	/**
	 * Text typed by people. A leading = + - @ would make a spreadsheet treat it as a
	 * formula, so such values get a leading apostrophe and are shown as typed.
	 */
	static String text(String value) {
		if (value == null) {
			return "";
		}
		String v = value.strip();
		return !v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0 ? "'" + v : v;
	}

	private static String quote(String value) {
		if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
			return value;
		}
		return '"' + value.replace("\"", "\"\"") + '"';
	}
}
