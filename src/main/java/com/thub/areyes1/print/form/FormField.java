package com.thub.areyes1.print.form;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.BiFunction;

import com.thub.areyes1.clearance.Building;
import com.thub.areyes1.clearance.Clearance;
import com.thub.areyes1.clearance.ClearanceType;
import com.thub.areyes1.settings.BarangaySettings;

/**
 * A value printed onto the office's pre-printed clearance form.
 *
 * <p>The default positions of the first fields come from the desktop app's Jasper
 * template ({@code report/bgyclearance_report.jrxml}, 612 × 936 pt long bond with
 * 20 pt margins): each element's page position is its band position plus the
 * margin, plus the title (79 pt) and page header (35 pt) bands for detail elements.
 * The template declared the clearance type and "applicant member of" without placing
 * them, so those start switched off, as do the fields it didn't have.
 *
 * <p>Tick fields print an "X" when their condition holds, for forms with boxes.
 */
public enum FormField {

	// ---- Placed in the original template (jrxml points: x, y, width) ----
	BARANGAY("Barangay", pt(268), pt(43), pt(100), true, (c, s) -> s.barangayName()),
	CONTROL_NUMBER("Control no.", pt(56), pt(58), pt(146), true, (c, s) -> number(c.controlNumber())),
	OR_NUMBER("O.R. no.", pt(473), pt(43), pt(100), true, (c, s) -> number(c.orNumber())),
	BUSINESS_NAME("Business name", pt(31), pt(151), pt(100), true, (c, s) -> c.businessName()),
	ADDRESS("Address", pt(31), pt(189), pt(100), true, (c, s) -> c.address()),
	TYPE_OF_BUSINESS("Type of business", pt(358), pt(151), pt(159), true, (c, s) -> c.typeOfBusiness()),
	BUILDING("Building (owned / rented)", pt(285), pt(191), pt(100), true,
			(c, s) -> c.building() == null ? "" : c.building() == Building.OWNED ? "Owned" : "Rented"),
	CAPITALIZATION("Capitalization", pt(228), pt(232), pt(100), true, (c, s) -> c.capitalization()),
	OWNERSHIP("Kind of ownership", pt(493), pt(217), pt(100), true, (c, s) -> c.ownershipKinds()),
	HOA_PRESIDENT("Homeowners’ association president", pt(20), pt(232), pt(208), true, (c, s) -> c.hoaPresident()),
	SECOND_ENDORSEMENT("2nd endorsement no.", pt(358), pt(252), pt(201), true,
			(c, s) -> number(c.secondEndorsementNumber())),
	AMOUNT_PAID("Amount paid", pt(132), pt(265), pt(100), true, (c, s) -> amount(c.amountPaid())),

	// ---- Declared in the template but never placed ----
	CLEARANCE_TYPE("Type (new / renewal)", 10, 110, 40, false, (c, s) -> c.type().label()),
	APPLICANT_MEMBER_OF("Applicant is a member of", 10, 118, 70, false, (c, s) -> c.applicantMemberOf()),

	// ---- Not in the template ----
	DATE_ISSUED("Date issued", 10, 126, 50, false,
			(c, s) -> date(c.issuedOn())),
	OWNER_NAME("Owner / manager", 10, 134, 70, false, (c, s) -> c.ownerName()),
	PUNONG_BARANGAY("Punong barangay", 10, 142, 70, false, (c, s) -> s.punongBarangay()),
	SECRETARY("Barangay secretary", 10, 150, 70, false, (c, s) -> s.secretary()),
	TICK_NEW("Tick: new business", 100, 110, 6, false, (c, s) -> tick(c.type() == ClearanceType.NEW)),
	TICK_RENEWAL("Tick: renewal", 110, 110, 6, false, (c, s) -> tick(c.type() == ClearanceType.RENEWAL)),
	TICK_OWNED("Tick: owned", 100, 118, 6, false, (c, s) -> tick(c.building() == Building.OWNED)),
	TICK_RENTED("Tick: rented", 110, 118, 6, false, (c, s) -> tick(c.building() == Building.RENTED)),
	TICK_CORPORATION("Tick: corporation", 100, 126, 6, false, (c, s) -> tick(c.corporation())),
	TICK_SINGLE("Tick: single proprietorship", 110, 126, 6, false, (c, s) -> tick(c.singleProprietorship())),
	TICK_PARTNERSHIP("Tick: partnership", 120, 126, 6, false, (c, s) -> tick(c.partnership())),
	TICK_OTHERS("Tick: others", 130, 126, 6, false, (c, s) -> tick(c.otherOwnership()));

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

	private final String label;
	private final double x;
	private final double y;
	private final double width;
	private final boolean onByDefault;
	private final BiFunction<Clearance, BarangaySettings, String> value;

	FormField(String label, double x, double y, double width, boolean onByDefault,
			BiFunction<Clearance, BarangaySettings, String> value) {
		this.label = label;
		this.x = x;
		this.y = y;
		this.width = width;
		this.onByDefault = onByDefault;
		this.value = value;
	}

	public String label() {
		return label;
	}

	public boolean isTick() {
		return name().startsWith("TICK_");
	}

	/** Where the field goes on a fresh install, in millimetres from the page's top-left corner. */
	public FormLayout.Placement defaultPlacement() {
		return new FormLayout.Placement(onByDefault, round(x), round(y), round(width),
				isTick() ? 12 : FormLayout.DEFAULT_FONT_SIZE);
	}

	/** The text printed for this clearance; empty when there is nothing to print. */
	public String valueFor(Clearance clearance, BarangaySettings settings) {
		String v = value.apply(clearance, settings);
		return v == null ? "" : v.strip();
	}

	/** Jasper positions are in points (1/72 in); layouts are kept in millimetres. */
	private static double pt(double points) {
		return points * 25.4 / 72;
	}

	private static double round(double mm) {
		return Math.round(mm * 10) / 10.0;
	}

	private static String number(Integer n) {
		return n == null || n == 0 ? "" : n.toString();
	}

	private static String amount(BigDecimal a) {
		return a == null ? "" : String.format(Locale.ENGLISH, "%,.2f", a);
	}

	private static String date(LocalDate d) {
		return d == null ? "" : DATE.format(d);
	}

	private static String tick(boolean on) {
		return on ? "X" : "";
	}
}
