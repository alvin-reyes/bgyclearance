/**
 * Class File Name: ClearanceForm.java
 * Description: Web form backing object for registering or editing a clearance.
 */

package com.thub.areyes1.web;

import java.math.BigDecimal;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;

import com.thub.areyes1.exception.BarangayClearanceValidationException;
import com.thub.areyes1.obj.BarangayClearance;
import com.thub.areyes1.obj.BarangayClearanceType;
import com.thub.areyes1.obj.BuildingType;

/**
 * Flat, validatable view of a {@link BarangayClearance} for the HTML form.
 */
public class ClearanceForm {

	/** Values of the "type" radio group. */
	public static final String NEW = "NEW";
	public static final String RENEWAL = "RENEWAL";

	/** Values of the "building" radio group. */
	public static final String OWNED = "OWNED";
	public static final String RENTED = "RENTED";

	private int id;

	@NotBlank(message = "Choose New or Renewal")
	private String type = NEW;

	@NotNull(message = "Enter the control number")
	@PositiveOrZero(message = "Must be zero or more")
	private Integer controlNumber;

	@NotBlank(message = "Enter the business name")
	@Size(max = 200)
	private String businessName;

	@Size(max = 300)
	private String address;

	@Size(max = 200)
	private String typeOfBusiness;

	@Size(max = 100)
	private String capitalization;

	private String building = "";

	private boolean corporation;
	private boolean singleProprietorship;
	private boolean partnership;
	private boolean others;

	@Size(max = 200)
	private String ownership;

	@Size(max = 200)
	private String applicantMemberOf;

	@Size(max = 200)
	private String assocHomeOwnerPresident;

	@PositiveOrZero(message = "Must be zero or more")
	private Integer secondEndorsmentNumber;

	@PositiveOrZero(message = "Must be zero or more")
	private Integer orNumber;

	@NotNull(message = "Enter the amount paid")
	@DecimalMin(value = "0", message = "Must be zero or more")
	@Digits(integer = 9, fraction = 2, message = "Use at most 2 decimal places")
	private BigDecimal amountPaid;

	/**
	 * Builds a form from a stored clearance.
	 *
	 * @param c the clearance
	 * @return the form
	 */
	public static ClearanceForm from(BarangayClearance c) {
		ClearanceForm f = new ClearanceForm();
		f.id = c.getId();
		f.type = c.isForRenewal() && !c.isForNew() ? RENEWAL : NEW;
		f.controlNumber = c.getControlNumber();
		f.businessName = c.getBusinessName();
		f.address = c.getAddress();
		f.typeOfBusiness = c.getTypeOfBusiness();
		f.capitalization = c.getCapitalization();
		f.building = c.isOwned() ? OWNED : c.isRented() ? RENTED : "";
		f.corporation = c.isCorporation();
		f.singleProprietorship = c.isSingleProprietorship();
		f.partnership = c.isParntership();
		f.others = c.isOthers();
		f.ownership = c.getOwnership();
		f.applicantMemberOf = c.getApplicantMemberOf();
		f.assocHomeOwnerPresident = c.getAssocHomeOwnerPresident();
		f.secondEndorsmentNumber = c.getSecondEndorsmentNumber();
		f.orNumber = c.getOrNumber();
		f.amountPaid = c.getAmountPaid() == null ? null : new BigDecimal(c.getAmountPaid().toString());
		return f;
	}

	/**
	 * Converts the form into a clearance ready to save and print.
	 *
	 * @return the clearance
	 * @throws BarangayClearanceValidationException the barangay clearance validation exception
	 */
	public BarangayClearance toClearance() throws BarangayClearanceValidationException {
		BarangayClearance c = new BarangayClearance();
		c.setId(id);
		boolean isNew = !RENEWAL.equals(type);
		c.setForNew(isNew);
		c.setForRenewal(!isNew);
		c.setBarangayClearanceType(isNew ? BarangayClearanceType.NEW : BarangayClearanceType.RENEWAL);
		c.setControlNumber(controlNumber);
		c.setBusinessName(trim(businessName));
		c.setAddress(trim(address));
		c.setTypeOfBusiness(trim(typeOfBusiness));
		c.setCapitalization(trim(capitalization));
		c.setOwned(OWNED.equals(building));
		c.setRented(RENTED.equals(building));
		if (OWNED.equals(building)) {
			c.setBuildingType(BuildingType.OWNED);
		} else if (RENTED.equals(building)) {
			c.setBuildingType(BuildingType.RENTED);
		}
		c.setCorporation(corporation);
		c.setSingleProprietorship(singleProprietorship);
		c.setParntership(partnership);
		c.setOthers(others);
		c.setOwnership(trim(ownership));
		c.setApplicantMemberOf(trim(applicantMemberOf));
		c.setAssocHomeOwnerPresident(trim(assocHomeOwnerPresident));
		c.setSecondEndorsmentNumber(secondEndorsmentNumber);
		c.setOrNumber(orNumber);
		c.setAmountPaid(amountPaid == null ? null : amountPaid.floatValue());
		return c;
	}

	private static String trim(String s) {
		return s == null || s.trim().isEmpty() ? null : s.trim();
	}

	public int getId() { return id; }
	public void setId(int id) { this.id = id; }
	public String getType() { return type; }
	public void setType(String type) { this.type = type; }
	public Integer getControlNumber() { return controlNumber; }
	public void setControlNumber(Integer controlNumber) { this.controlNumber = controlNumber; }
	public String getBusinessName() { return businessName; }
	public void setBusinessName(String businessName) { this.businessName = businessName; }
	public String getAddress() { return address; }
	public void setAddress(String address) { this.address = address; }
	public String getTypeOfBusiness() { return typeOfBusiness; }
	public void setTypeOfBusiness(String typeOfBusiness) { this.typeOfBusiness = typeOfBusiness; }
	public String getCapitalization() { return capitalization; }
	public void setCapitalization(String capitalization) { this.capitalization = capitalization; }
	public String getBuilding() { return building; }
	public void setBuilding(String building) { this.building = building; }
	public boolean isCorporation() { return corporation; }
	public void setCorporation(boolean corporation) { this.corporation = corporation; }
	public boolean isSingleProprietorship() { return singleProprietorship; }
	public void setSingleProprietorship(boolean singleProprietorship) { this.singleProprietorship = singleProprietorship; }
	public boolean isPartnership() { return partnership; }
	public void setPartnership(boolean partnership) { this.partnership = partnership; }
	public boolean isOthers() { return others; }
	public void setOthers(boolean others) { this.others = others; }
	public String getOwnership() { return ownership; }
	public void setOwnership(String ownership) { this.ownership = ownership; }
	public String getApplicantMemberOf() { return applicantMemberOf; }
	public void setApplicantMemberOf(String applicantMemberOf) { this.applicantMemberOf = applicantMemberOf; }
	public String getAssocHomeOwnerPresident() { return assocHomeOwnerPresident; }
	public void setAssocHomeOwnerPresident(String assocHomeOwnerPresident) { this.assocHomeOwnerPresident = assocHomeOwnerPresident; }
	public Integer getSecondEndorsmentNumber() { return secondEndorsmentNumber; }
	public void setSecondEndorsmentNumber(Integer secondEndorsmentNumber) { this.secondEndorsmentNumber = secondEndorsmentNumber; }
	public Integer getOrNumber() { return orNumber; }
	public void setOrNumber(Integer orNumber) { this.orNumber = orNumber; }
	public BigDecimal getAmountPaid() { return amountPaid; }
	public void setAmountPaid(BigDecimal amountPaid) { this.amountPaid = amountPaid; }
}
