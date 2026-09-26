/**
 * Class File Name: BarangayClearanceServiceE2EIT.java
 * Description: End-to-end tests for the clearance service against a real SQLite
 * database and the real compiled Jasper report.
 */

package com.thub.areyes1.e2e;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Map;

import net.sf.jasperreports.engine.JasperPrint;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.thub.areyes1.config.DaoConfig;
import com.thub.areyes1.config.DataSourceFactoryConfig;
import com.thub.areyes1.config.ServiceConfig;
import com.thub.areyes1.obj.BarangayClearance;
import com.thub.areyes1.obj.BarangayClearanceReport;
import com.thub.areyes1.obj.BarangayClearanceType;
import com.thub.areyes1.obj.BuildingType;
import com.thub.areyes1.service.BarangayClearanceService;

/**
 * Boots the same Spring configuration the app uses (minus the Swing beans) and
 * drives the service the way the UI does.
 */
public class BarangayClearanceServiceE2EIT {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File db;
	private AnnotationConfigApplicationContext ctx;
	private BarangayClearanceService service;
	private ConnectionTracker tracker;

	@Before
	public void setUp() throws Exception {
		db = E2eEnvironment.useFreshDatabase(tmp.getRoot());
		startContext();
	}

	@After
	public void tearDown() {
		if (ctx != null) {
			assertEquals("app left JDBC connections open", 0, tracker.openConnections());
			ctx.close();
		}
	}

	private void startContext() {
		ctx = new AnnotationConfigApplicationContext();
		ctx.register(DataSourceFactoryConfig.class, DaoConfig.class, ServiceConfig.class);
		tracker = new ConnectionTracker();
		ctx.getBeanFactory().addBeanPostProcessor(tracker);
		ctx.refresh();
		service = ctx.getBean(BarangayClearanceService.class);
	}

	private BarangayClearance findByName(String name) throws Exception {
		for (BarangayClearance c : service.getAllBarangayClearance()) {
			if (name.equals(c.getBusinessName())) {
				return c;
			}
		}
		return null;
	}

	private static BarangayClearance newClearance(String name, int controlNo) throws Exception {
		BarangayClearance c = new BarangayClearance();
		c.setForNew(true);
		c.setBusinessName(name);
		c.setAddress("12 Rizal St., Poblacion");
		c.setOwnership("Juan Dela Cruz");
		c.setAssocHomeOwnerPresident("Maria Santos");
		c.setControlNumber(controlNo);
		c.setAmountPaid(250.5f);
		c.setSingleProprietorship(true);
		c.setRented(true);
		return c;
	}

	@Test
	public void emptyDatabaseListsNothing() throws Exception {
		List<BarangayClearance> all = service.getAllBarangayClearance();
		assertNotNull(all);
		assertTrue(all.isEmpty());
	}

	@Test
	public void savedClearanceIsPersistedAndListed() throws Exception {
		service.saveClearance(newClearance("Aling Nena Sari-Sari Store", 1001));

		List<Map<String, String>> rows = E2eEnvironment.rows(db);
		assertEquals(1, rows.size());
		assertEquals("Aling Nena Sari-Sari Store", rows.get(0).get("name"));
		assertEquals("12 Rizal St., Poblacion", rows.get(0).get("address"));
		assertEquals("1001", rows.get(0).get("control_no"));

		List<BarangayClearance> all = service.getAllBarangayClearance();
		assertEquals(1, all.size());
		BarangayClearance listed = all.get(0);
		assertTrue(listed.getId() > 0);
		assertEquals("Aling Nena Sari-Sari Store", listed.getBusinessName());
		assertEquals(Integer.valueOf(1001), listed.getControlNumber());
		assertEquals(250.5f, listed.getAmountPaid(), 0.001f);
	}

	@Test
	public void clearanceRoundTripsThroughGetById() throws Exception {
		service.saveClearance(newClearance("Kusina ni Lola", 1002));
		int id = service.getAllBarangayClearance().get(0).getId();

		BarangayClearance loaded = service.getBarangayClearanceData(id);

		assertEquals(id, loaded.getId());
		assertEquals("Kusina ni Lola", loaded.getBusinessName());
		assertEquals("12 Rizal St., Poblacion", loaded.getAddress());
		assertEquals("Juan Dela Cruz", loaded.getOwnership());
		assertEquals("Maria Santos", loaded.getAssocHomeOwnerPresident());
		assertEquals(Integer.valueOf(1002), loaded.getControlNumber());
		assertEquals(250.5f, loaded.getAmountPaid(), 0.001f);
		assertTrue("saved as new", loaded.isForNew());
		assertFalse("saved as new, not renewal", loaded.isForRenewal());
		assertTrue(loaded.isSingleProprietorship());
		assertFalse(loaded.isParntership());
		assertFalse(loaded.isCorporation());
		assertTrue(loaded.isRented());
		assertFalse(loaded.isOwned());
	}

	@Test
	public void savingWithAnExistingIdUpdatesInPlace() throws Exception {
		service.saveClearance(newClearance("Old Name Bakery", 1003));
		BarangayClearance existing = service.getBarangayClearanceData(
				service.getAllBarangayClearance().get(0).getId());

		existing.setBusinessName("New Name Bakery");
		existing.setAmountPaid(500f);
		service.saveClearance(existing);

		List<BarangayClearance> all = service.getAllBarangayClearance();
		assertEquals("update must not insert a second row", 1, all.size());
		assertEquals("New Name Bakery", all.get(0).getBusinessName());
		assertEquals(500f, all.get(0).getAmountPaid(), 0.001f);
	}

	@Test
	public void removedClearanceIsGone() throws Exception {
		service.saveClearance(newClearance("Keep Me", 1004));
		service.saveClearance(newClearance("Delete Me", 1005));
		BarangayClearance toDelete = findByName("Delete Me");
		assertNotNull(toDelete);

		assertTrue(service.removeClearance(toDelete));

		List<BarangayClearance> remaining = service.getAllBarangayClearance();
		assertEquals(1, remaining.size());
		assertEquals("Keep Me", remaining.get(0).getBusinessName());
	}

	@Test
	public void saveAndGenerateReportProducesAPrintableReport() throws Exception {
		BarangayClearance clearance = newClearance("Tindahan ni Mang Tomas", 1006);

		BarangayClearanceReport report = service.generateAndSaveBarangayReport(clearance);

		assertNotNull(report);
		assertEquals(clearance, report.getBarangayClearance());
		JasperPrint print = report.getBarangayClearancePrint();
		assertNotNull("Jasper report failed to fill; see stack trace above", print);
		assertFalse(print.getPages().isEmpty());
		assertEquals(1, E2eEnvironment.rows(db).size());
	}

	@Test
	public void reportShowsTheClearanceDetails() throws Exception {
		// The draft template's fields are ~100px wide and truncate longer values,
		// so keep the inputs short enough to render in full.
		BarangayClearance clearance = newClearance("Tomas Store", 1006);
		clearance.setAddress("12 Rizal St.");
		clearance.setTypeOfBusiness("Sari-sari");
		clearance.setCapitalization("75000");
		clearance.setOrNumber(556677);
		clearance.setSecondEndorsmentNumber(12);
		clearance.setBarangayClearanceType(BarangayClearanceType.NEW);
		clearance.setBuildingType(BuildingType.RENTED);

		JasperPrint print = service.generateAndSaveBarangayReport(clearance).getBarangayClearancePrint();

		assertNotNull("Jasper report failed to fill; see stack trace above", print);
		String text = E2eEnvironment.text(print);
		for (String expected : new String[] {"Tomas Store", "12 Rizal St.", "1006", "250.5", "Sari-sari",
				"75000", "556677", "12", "Rented", "Juan Dela Cruz", "Maria Santos"}) {
			assertTrue("report is missing '" + expected + "':\n" + text, text.contains(expected));
		}
	}

	@Test
	public void renewalRoundTripsAsRenewal() throws Exception {
		BarangayClearance renewal = newClearance("Renewed Hardware", 1008);
		renewal.setForNew(false);
		renewal.setForRenewal(true);
		service.saveClearance(renewal);

		BarangayClearance loaded = service.getBarangayClearanceData(findByName("Renewed Hardware").getId());

		assertFalse(loaded.isForNew());
		assertTrue(loaded.isForRenewal());
	}

	@Test
	public void updateKeepsTheControlNumber() throws Exception {
		service.saveClearance(newClearance("Stable Control No", 1009));
		BarangayClearance existing = service.getBarangayClearanceData(findByName("Stable Control No").getId());

		existing.setAddress("99 New Address");
		service.saveClearance(existing);

		BarangayClearance reloaded = service.getBarangayClearanceData(existing.getId());
		assertEquals("99 New Address", reloaded.getAddress());
		assertEquals(Integer.valueOf(1009), reloaded.getControlNumber());
	}

	@Test
	public void secondEndorsementNumberIsSavedAndUpdated() throws Exception {
		BarangayClearance clearance = newClearance("Endorsed Eatery", 1011);
		clearance.setSecondEndorsmentNumber(77);
		service.saveClearance(clearance);
		BarangayClearance saved = service.getBarangayClearanceData(findByName("Endorsed Eatery").getId());
		assertEquals(Integer.valueOf(77), saved.getSecondEndorsmentNumber());

		saved.setSecondEndorsmentNumber(88);
		service.saveClearance(saved);

		assertEquals("88", E2eEnvironment.rows(db).get(0).get("second_endorsment"));
		assertEquals(Integer.valueOf(88), service.getBarangayClearanceData(saved.getId()).getSecondEndorsmentNumber());
	}

	@Test
	public void savingANewClearanceAssignsItsId() throws Exception {
		BarangayClearance first = service.saveClearance(newClearance("First", 1101));
		BarangayClearance second = service.saveClearance(newClearance("Second", 1102));

		assertTrue(first.getId() > 0);
		assertEquals(first.getId() + 1, second.getId());
		assertEquals("Second", service.getBarangayClearanceData(second.getId()).getBusinessName());
	}

	@Test
	public void businessDetailsAndPaymentFieldsRoundTrip() throws Exception {
		BarangayClearance c = newClearance("Full Details Store", 1103);
		c.setTypeOfBusiness("Hardware");
		c.setCapitalization("120,000");
		c.setOrNumber(889900);
		c.setApplicantMemberOf("Poblacion HOA");
		c.setCorporation(true);
		int id = service.saveClearance(c).getId();

		BarangayClearance loaded = service.getBarangayClearanceData(id);
		assertEquals("Hardware", loaded.getTypeOfBusiness());
		assertEquals("120,000", loaded.getCapitalization());
		assertEquals(Integer.valueOf(889900), loaded.getOrNumber());
		assertEquals("Poblacion HOA", loaded.getApplicantMemberOf());
		assertTrue(loaded.isCorporation());
		assertEquals(BarangayClearanceType.NEW.toString(), loaded.getData().get("CLEARANCE_TYPE"));
		assertEquals(BuildingType.RENTED.toString(), loaded.getData().get("BUILDING_TYPE"));

		loaded.setOrNumber(null);
		loaded.setTypeOfBusiness("Hardware & Paint");
		loaded.setForNew(false);
		loaded.setForRenewal(true);
		loaded.setCorporation(false);
		loaded.setControlNumber(1104);
		service.saveClearance(loaded);

		BarangayClearance updated = service.getBarangayClearanceData(id);
		assertEquals(null, updated.getOrNumber());
		assertEquals("Hardware & Paint", updated.getTypeOfBusiness());
		assertTrue(updated.isForRenewal());
		assertFalse(updated.isCorporation());
		assertEquals(Integer.valueOf(1104), updated.getControlNumber());
	}

	@Test
	public void reportLeavesUnsetFieldsBlank() throws Exception {
		BarangayClearance minimal = new BarangayClearance();
		minimal.setBusinessName("Minimal");

		String text = E2eEnvironment.text(service.generateAndSaveBarangayReport(minimal).getBarangayClearancePrint());

		assertTrue(text, text.contains("Minimal"));
		assertFalse("report printed 'null':\n" + text, text.contains("null"));
	}

	@Test
	public void removingAnUnsavedClearanceDeletesNothing() throws Exception {
		service.saveClearance(newClearance("Untouched", 1010));

		assertTrue(service.removeClearance(new BarangayClearance()));

		assertEquals(1, service.getAllBarangayClearance().size());
	}

	@Test
	public void loadingAMissingIdReturnsAnEmptyClearance() throws Exception {
		BarangayClearance missing = service.getBarangayClearanceData(12345);

		assertNotNull(missing);
		assertEquals(0, missing.getId());
		assertEquals(null, missing.getBusinessName());
	}

	@Test
	public void manyOperationsDoNotLeakConnections() throws Exception {
		for (int i = 0; i < 50; i++) {
			service.saveClearance(newClearance("Bulk " + i, 5000 + i));
			service.getAllBarangayClearance();
		}
		BarangayClearance any = findByName("Bulk 7");
		service.getBarangayClearanceData(any.getId());
		service.removeClearance(any);

		assertEquals(49, service.getAllBarangayClearance().size());
		assertEquals(0, tracker.openConnections());
	}

	@Test
	public void dataSurvivesAnAppRestart() throws Exception {
		service.saveClearance(newClearance("Persistent Pharmacy", 1007));
		ctx.close();

		startContext();

		List<BarangayClearance> all = service.getAllBarangayClearance();
		assertEquals(1, all.size());
		assertEquals("Persistent Pharmacy", all.get(0).getBusinessName());
	}

	@Test
	public void shippedSampleDatabaseIsReadableAndUpgraded() throws Exception {
		ctx.close();
		File sample = E2eEnvironment.useShippedSampleDatabase(tmp.getRoot());
		startContext();

		List<BarangayClearance> all = service.getAllBarangayClearance();

		assertNotNull("app could not read the SampleDB.db it ships with", all);
		assertEquals(E2eEnvironment.rows(sample).size(), all.size());
		Map<String, String> row = E2eEnvironment.rows(sample).get(0);
		for (String column : new String[] {"capitalization", "or_number", "applicant_member_of"}) {
			assertTrue("missing column " + column, row.containsKey(column));
		}

		BarangayClearance c = newClearance("After Upgrade", 1201);
		c.setOrNumber(42);
		int id = service.saveClearance(c).getId();
		assertEquals(Integer.valueOf(42), service.getBarangayClearanceData(id).getOrNumber());
	}
}
