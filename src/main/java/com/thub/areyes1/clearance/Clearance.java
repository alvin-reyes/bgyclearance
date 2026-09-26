package com.thub.areyes1.clearance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A barangay business clearance.
 *
 * @param id                      database id, or null before it is saved
 * @param type                    new business or renewal
 * @param controlNumber           control number printed on the clearance
 * @param issuedOn                date issued; null for records from the desktop app
 * @param businessName            business (trade) name
 * @param address                 business address
 * @param typeOfBusiness          type of business or activity
 * @param capitalization          declared capitalization, as entered
 * @param building                whether the building is owned or rented, if known
 * @param corporation             ownership kind: corporation
 * @param singleProprietorship    ownership kind: single proprietorship
 * @param partnership             ownership kind: partnership
 * @param otherOwnership          ownership kind: others
 * @param ownerName               owner or manager
 * @param applicantMemberOf       association the applicant is a member of
 * @param hoaPresident            homeowners' association president
 * @param secondEndorsementNumber 2nd endorsement number
 * @param orNumber                official receipt number
 * @param amountPaid              clearance fee paid
 */
public record Clearance(
		Long id,
		ClearanceType type,
		Integer controlNumber,
		LocalDate issuedOn,
		String businessName,
		String address,
		String typeOfBusiness,
		String capitalization,
		Building building,
		boolean corporation,
		boolean singleProprietorship,
		boolean partnership,
		boolean otherOwnership,
		String ownerName,
		String applicantMemberOf,
		String hoaPresident,
		Integer secondEndorsementNumber,
		Integer orNumber,
		BigDecimal amountPaid) {

	public Clearance withId(Long newId) {
		return new Clearance(newId, type, controlNumber, issuedOn, businessName, address, typeOfBusiness,
				capitalization, building, corporation, singleProprietorship, partnership, otherOwnership,
				ownerName, applicantMemberOf, hoaPresident, secondEndorsementNumber, orNumber, amountPaid);
	}

	/** Ownership kinds that apply, in display order, e.g. "Corporation, Partnership". */
	public String ownershipKinds() {
		List<String> kinds = new ArrayList<>();
		if (corporation) {
			kinds.add("Corporation");
		}
		if (singleProprietorship) {
			kinds.add("Single proprietorship");
		}
		if (partnership) {
			kinds.add("Partnership");
		}
		if (otherOwnership) {
			kinds.add("Others");
		}
		return String.join(", ", kinds);
	}

	/** Business name, or a placeholder for legacy records saved without one. */
	public String displayName() {
		return businessName == null || businessName.isBlank() ? "(no name)" : businessName;
	}
}
