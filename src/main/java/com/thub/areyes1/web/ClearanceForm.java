package com.thub.areyes1.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

import com.thub.areyes1.clearance.Building;
import com.thub.areyes1.clearance.Clearance;
import com.thub.areyes1.clearance.ClearanceType;

/** What the New / Edit clearance form submits. */
public class ClearanceForm {

	@NotNull(message = "Choose New or Renewal")
	private ClearanceType type = ClearanceType.NEW;

	@NotNull(message = "Enter the control number")
	@PositiveOrZero(message = "Must be zero or more")
	private Integer controlNumber;

	@NotNull(message = "Enter the date issued")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate issuedOn;

	@NotBlank(message = "Enter the business name")
	@Size(max = 200, message = "Keep it under 200 characters")
	private String businessName;

	@Size(max = 300, message = "Keep it under 300 characters")
	private String address;

	@Size(max = 200, message = "Keep it under 200 characters")
	private String typeOfBusiness;

	@Size(max = 100, message = "Keep it under 100 characters")
	private String capitalization;

	private Building building;

	private boolean corporation;
	private boolean singleProprietorship;
	private boolean partnership;
	private boolean otherOwnership;

	@Size(max = 200, message = "Keep it under 200 characters")
	private String ownerName;

	@Size(max = 200, message = "Keep it under 200 characters")
	private String applicantMemberOf;

	@Size(max = 200, message = "Keep it under 200 characters")
	private String hoaPresident;

	@PositiveOrZero(message = "Must be zero or more")
	private Integer secondEndorsementNumber;

	@PositiveOrZero(message = "Must be zero or more")
	private Integer orNumber;

	@NotNull(message = "Enter the amount paid")
	@DecimalMin(value = "0", message = "Must be zero or more")
	@Digits(integer = 9, fraction = 2, message = "Use at most 2 decimal places")
	private BigDecimal amountPaid;

	public static ClearanceForm newFor(LocalDate today) {
		ClearanceForm f = new ClearanceForm();
		f.issuedOn = today;
		return f;
	}

	public static ClearanceForm from(Clearance c) {
		ClearanceForm f = new ClearanceForm();
		f.type = c.type();
		f.controlNumber = c.controlNumber();
		f.issuedOn = c.issuedOn();
		f.businessName = c.businessName();
		f.address = c.address();
		f.typeOfBusiness = c.typeOfBusiness();
		f.capitalization = c.capitalization();
		f.building = c.building();
		f.corporation = c.corporation();
		f.singleProprietorship = c.singleProprietorship();
		f.partnership = c.partnership();
		f.otherOwnership = c.otherOwnership();
		f.ownerName = c.ownerName();
		f.applicantMemberOf = c.applicantMemberOf();
		f.hoaPresident = c.hoaPresident();
		f.secondEndorsementNumber = c.secondEndorsementNumber();
		f.orNumber = c.orNumber();
		f.amountPaid = c.amountPaid();
		return f;
	}

	public Clearance toClearance(Long id) {
		return new Clearance(id, type, controlNumber, issuedOn, trim(businessName), trim(address),
				trim(typeOfBusiness), trim(capitalization), building, corporation, singleProprietorship, partnership,
				otherOwnership, trim(ownerName), trim(applicantMemberOf), trim(hoaPresident),
				secondEndorsementNumber, orNumber, amountPaid == null ? null : amountPaid.setScale(2));
	}

	/** Blank text is stored as null so it prints and displays as "—". */
	private static String trim(String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}

	public ClearanceType getType() { return type; }
	public void setType(ClearanceType type) { this.type = type; }
	public Integer getControlNumber() { return controlNumber; }
	public void setControlNumber(Integer controlNumber) { this.controlNumber = controlNumber; }
	public LocalDate getIssuedOn() { return issuedOn; }
	public void setIssuedOn(LocalDate issuedOn) { this.issuedOn = issuedOn; }
	public String getBusinessName() { return businessName; }
	public void setBusinessName(String businessName) { this.businessName = businessName; }
	public String getAddress() { return address; }
	public void setAddress(String address) { this.address = address; }
	public String getTypeOfBusiness() { return typeOfBusiness; }
	public void setTypeOfBusiness(String typeOfBusiness) { this.typeOfBusiness = typeOfBusiness; }
	public String getCapitalization() { return capitalization; }
	public void setCapitalization(String capitalization) { this.capitalization = capitalization; }
	public Building getBuilding() { return building; }
	public void setBuilding(Building building) { this.building = building; }
	public boolean isCorporation() { return corporation; }
	public void setCorporation(boolean corporation) { this.corporation = corporation; }
	public boolean isSingleProprietorship() { return singleProprietorship; }
	public void setSingleProprietorship(boolean singleProprietorship) { this.singleProprietorship = singleProprietorship; }
	public boolean isPartnership() { return partnership; }
	public void setPartnership(boolean partnership) { this.partnership = partnership; }
	public boolean isOtherOwnership() { return otherOwnership; }
	public void setOtherOwnership(boolean otherOwnership) { this.otherOwnership = otherOwnership; }
	public String getOwnerName() { return ownerName; }
	public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
	public String getApplicantMemberOf() { return applicantMemberOf; }
	public void setApplicantMemberOf(String applicantMemberOf) { this.applicantMemberOf = applicantMemberOf; }
	public String getHoaPresident() { return hoaPresident; }
	public void setHoaPresident(String hoaPresident) { this.hoaPresident = hoaPresident; }
	public Integer getSecondEndorsementNumber() { return secondEndorsementNumber; }
	public void setSecondEndorsementNumber(Integer secondEndorsementNumber) { this.secondEndorsementNumber = secondEndorsementNumber; }
	public Integer getOrNumber() { return orNumber; }
	public void setOrNumber(Integer orNumber) { this.orNumber = orNumber; }
	public BigDecimal getAmountPaid() { return amountPaid; }
	public void setAmountPaid(BigDecimal amountPaid) { this.amountPaid = amountPaid; }
}
