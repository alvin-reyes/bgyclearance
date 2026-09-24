/**
 * Class File Name: BarangayClearanceTest.java
 * Description: Unit tests for the report parameter map built by BarangayClearance.
 */

package com.thub.areyes1.obj;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

/**
 * The setters double as the source of the Jasper report parameters, so the map
 * keys and values must line up with bgyclearance_report.jrxml.
 */
public class BarangayClearanceTest {

	@Test
	public void newClearanceHasEmptyReportData() {
		assertTrue(new BarangayClearance().getData().isEmpty());
	}

	@Test
	public void textFieldsAreCopiedIntoReportData() throws Exception {
		BarangayClearance c = new BarangayClearance();
		c.setBusinessName("Aling Nena Sari-Sari Store");
		c.setAddress("12 Rizal St.");
		c.setTypeOfBusiness("Retail");
		c.setCapitalization("50,000");
		c.setOwnership("Nena Reyes");
		c.setAssocHomeOwnerPresident("Maria Santos");
		c.setApplicantMemberOf("Poblacion HOA");

		Map<String, Object> data = c.getData();
		assertEquals("Aling Nena Sari-Sari Store", data.get("BUSINESS_NAME"));
		assertEquals("12 Rizal St.", data.get("ADDRESS"));
		assertEquals("Retail", data.get("TYPE_OF_BUSINESS"));
		assertEquals("50,000", data.get("CAPITALIZATION"));
		assertEquals("Nena Reyes", data.get("OWNERSHIP"));
		assertEquals("Maria Santos", data.get("ASSOC_HOME_OWNER_PRESIDENT"));
		assertEquals("Poblacion HOA", data.get("APPLICANT_MEMBER_OF"));
	}

	@Test
	public void numericFieldsAreStoredAsStringsForTheReport() throws Exception {
		// Every parameter in the .jrxml is declared java.lang.String.
		BarangayClearance c = new BarangayClearance();
		c.setControlNumber(1001);
		c.setAmountPaid(250.5f);
		c.setOrNumber(778899);
		c.setSecondEndorsmentNumber(42);

		Map<String, Object> data = c.getData();
		assertEquals("1001", data.get("CONTROL_NUMBER"));
		assertEquals("250.5", data.get("AMOUNT_PAID"));
		assertEquals("778899", data.get("OR_NUMBER"));
		assertEquals("42", data.get("SECOND_ENDORSMENT_NUMBER"));
	}

	@Test
	public void enumFieldsAreStoredAsStringsForTheReport() throws Exception {
		BarangayClearance c = new BarangayClearance();
		c.setBarangayClearanceType(BarangayClearanceType.RENEWAL);
		c.setBuildingType(BuildingType.values()[0]);

		assertEquals(BarangayClearanceType.RENEWAL.toString(), c.getData().get("CLEARANCE_TYPE"));
		assertEquals(BuildingType.values()[0].toString(), c.getData().get("BUILDING_TYPE"));
	}

	@Test
	public void flagsAreStoredAsYesNo() throws Exception {
		BarangayClearance c = new BarangayClearance();
		c.setForNew(true);
		c.setForRenewal(false);
		c.setOwned(false);
		c.setRented(true);
		c.setCorporation(true);
		c.setSingleProprietorship(false);
		c.setParntership(true);

		Map<String, Object> data = c.getData();
		assertEquals("Y", data.get("FORNEW"));
		assertEquals("N", data.get("FORRENEWAL"));
		assertEquals("N", data.get("OWNED"));
		assertEquals("Y", data.get("RENTED"));
		assertEquals("Y", data.get("CORPORATION"));
		assertEquals("N", data.get("SINGLEPROP"));
		assertEquals("Y", data.get("PARTNERSHIP"));
	}

	@Test
	public void flagGettersReflectSetters() throws Exception {
		BarangayClearance c = new BarangayClearance();
		c.setOthers(true);
		c.setOwned(true);
		assertTrue(c.isOthers());
		assertTrue(c.isOwned());
		assertFalse(c.isRented());
	}
}
