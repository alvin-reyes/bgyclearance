package com.thub.areyes1.clearance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.thub.areyes1.TestDatabase;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestDatabase.FixedClock.class)
class ClearanceRepositoryTest {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		TestDatabase.register(registry);
	}

	@Autowired
	ClearanceRepository repo;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void clean() {
		TestDatabase.clear(jdbc);
	}

	static Clearance clearance(String name, int controlNo, String amount, LocalDate issuedOn, ClearanceType type) {
		return new Clearance(null, type, controlNo, issuedOn, name, "12 Rizal St.", "Sari-sari", "75,000",
				Building.RENTED, false, true, false, false, "Juan Dela Cruz", "Poblacion HOA", "Maria Santos",
				12, 556677, new BigDecimal(amount));
	}

	static Clearance clearance(String name, int controlNo) {
		return clearance(name, controlNo, "250.50", LocalDate.of(2026, 3, 1), ClearanceType.NEW);
	}

	@Test
	void insertAssignsIdAndEveryFieldRoundTrips() {
		Clearance saved = repo.insert(clearance("Tomas Store", 1001));

		assertThat(saved.id()).isPositive();
		assertThat(repo.findById(saved.id())).contains(saved);
	}

	@Test
	void updateChangesEveryFieldInPlace() {
		Clearance saved = repo.insert(clearance("Old Name", 1002));
		Clearance changed = new Clearance(saved.id(), ClearanceType.RENEWAL, 2002, LocalDate.of(2026, 1, 5),
				"New Name", "7 Luna St.", "Bakery", "10,000", Building.OWNED, true, false, true, true, "Ana Cruz",
				null, null, null, null, new BigDecimal("120.00"));

		assertThat(repo.update(changed)).isTrue();

		assertThat(repo.findById(saved.id())).contains(changed);
		assertThat(repo.search(ClearanceQuery.all()).total()).isEqualTo(1);
	}

	@Test
	void updateAndDeleteReportMissingRecords() {
		assertThat(repo.update(clearance("Ghost", 1).withId(999L))).isFalse();
		assertThat(repo.delete(999)).isFalse();
		assertThat(repo.findById(999)).isEmpty();
	}

	@Test
	void deleteRemovesOnlyThatRecord() {
		Clearance keep = repo.insert(clearance("Keep", 1));
		Clearance gone = repo.insert(clearance("Gone", 2));

		assertThat(repo.delete(gone.id())).isTrue();

		assertThat(repo.findById(gone.id())).isEmpty();
		assertThat(repo.findById(keep.id())).isPresent();
	}

	@Test
	void searchMatchesNameAddressOrExactControlNumber() {
		repo.insert(clearance("Aling Nena Store", 2001));
		repo.insert(clearance("Kusina ni Lola", 2002));
		repo.insert(clearance("Rizal Pharmacy", 2003));

		assertThat(names(new ClearanceQuery("rizal", null, ClearanceSort.NAME, false, 0, 25)))
				.containsExactly("Aling Nena Store", "Kusina ni Lola", "Rizal Pharmacy"); // all share the address
		assertThat(names(new ClearanceQuery("pharm", null, null, true, 0, 25))).containsExactly("Rizal Pharmacy");
		assertThat(names(new ClearanceQuery("2002", null, null, true, 0, 25))).containsExactly("Kusina ni Lola");
		assertThat(names(new ClearanceQuery("200", null, null, true, 0, 25))).isEmpty();
		assertThat(names(new ClearanceQuery("100%", null, null, true, 0, 25))).isEmpty();
	}

	@Test
	void filtersByType() {
		repo.insert(clearance("New One", 1, "10", LocalDate.of(2026, 1, 1), ClearanceType.NEW));
		repo.insert(clearance("Renewed One", 2, "10", LocalDate.of(2026, 1, 1), ClearanceType.RENEWAL));

		assertThat(names(new ClearanceQuery("", ClearanceType.NEW, null, true, 0, 25))).containsExactly("New One");
		assertThat(names(new ClearanceQuery("", ClearanceType.RENEWAL, null, true, 0, 25)))
				.containsExactly("Renewed One");
	}

	@Test
	void sortsByEachColumnInBothDirections() {
		repo.insert(clearance("Bravo", 30, "9.50", LocalDate.of(2026, 2, 1), ClearanceType.NEW));
		repo.insert(clearance("alpha", 10, "100", LocalDate.of(2026, 3, 1), ClearanceType.NEW));
		repo.insert(clearance("Charlie", 20, "20", LocalDate.of(2026, 1, 1), ClearanceType.NEW));

		assertThat(names(sorted(ClearanceSort.ISSUED, true))).containsExactly("alpha", "Bravo", "Charlie");
		assertThat(names(sorted(ClearanceSort.NAME, false))).containsExactly("alpha", "Bravo", "Charlie");
		assertThat(names(sorted(ClearanceSort.CONTROL, false))).containsExactly("alpha", "Charlie", "Bravo");
		assertThat(names(sorted(ClearanceSort.AMOUNT, true))).containsExactly("alpha", "Charlie", "Bravo");
	}

	@Test
	void pagesThroughResults() {
		for (int i = 1; i <= 7; i++) {
			repo.insert(clearance("Shop " + i, i));
		}

		PageResult<Clearance> page2 = repo.search(new ClearanceQuery("", null, ClearanceSort.CONTROL, false, 1, 3));

		assertThat(page2.total()).isEqualTo(7);
		assertThat(page2.totalPages()).isEqualTo(3);
		assertThat(page2.items()).extracting(Clearance::controlNumber).containsExactly(4, 5, 6);
		assertThat(page2.firstItem()).isEqualTo(4);
		assertThat(page2.lastItem()).isEqualTo(6);
		assertThat(page2.hasPrevious()).isTrue();
		assertThat(page2.hasNext()).isTrue();
	}

	@Test
	void statsTotalAllTimeAndThisYear() {
		repo.insert(clearance("A", 1, "100.25", LocalDate.of(2026, 2, 1), ClearanceType.NEW));
		repo.insert(clearance("B", 2, "50", LocalDate.of(2026, 3, 1), ClearanceType.RENEWAL));
		repo.insert(clearance("C", 3, "30", LocalDate.of(2025, 12, 31), ClearanceType.NEW));

		ClearanceStats stats = repo.stats();

		assertThat(stats.total()).isEqualTo(3);
		assertThat(stats.totalCollected()).isEqualByComparingTo("180.25");
		assertThat(stats.newCount()).isEqualTo(2);
		assertThat(stats.renewalCount()).isEqualTo(1);
		assertThat(stats.year()).isEqualTo(2026);
		assertThat(stats.yearCount()).isEqualTo(2);
		assertThat(stats.yearCollected()).isEqualByComparingTo("150.25");
	}

	@Test
	void recentReturnsNewestFirst() {
		repo.insert(clearance("First", 1));
		repo.insert(clearance("Second", 2));
		repo.insert(clearance("Third", 3));

		assertThat(repo.recent(2)).extracting(Clearance::businessName).containsExactly("Third", "Second");
	}

	@Test
	void suggestsTheNextControlNumber() {
		assertThat(repo.nextControlNumber()).isEmpty();
		repo.insert(clearance("Zero", 0));
		assertThat(repo.nextControlNumber()).isEmpty();
		repo.insert(clearance("A", 2026007));
		repo.insert(clearance("B", 2026003));
		assertThat(repo.nextControlNumber()).contains(2026008);
	}

	@Test
	void suggestsTypesOfBusinessMostUsedFirst() {
		for (String t : new String[] { "Retail", "Food service", "retail ", "Retail", "  ", "Hardware" }) {
			repo.insert(new Clearance(null, ClearanceType.NEW, 1, null, "X", null, t, null, null, false, false, false,
					false, null, null, null, null, null, BigDecimal.ONE));
		}

		List<String> types = repo.typesOfBusiness(10);

		assertThat(types).hasSize(3);
		assertThat(types.getFirst()).isEqualToIgnoringCase("retail");
		assertThat(types).contains("Food service", "Hardware");
	}

	@Test
	void readsValuesWrittenByTheDesktopApp() {
		jdbc.sql("""
				INSERT INTO bgy_clearance (name, "new", corporation, owned, amount_paid, control_no, second_endorsment)
				VALUES ('Legacy', 'true', '1', '1', 'not a number', '77', 'Legacy')""").update();

		Clearance c = repo.search(ClearanceQuery.all()).items().getFirst();

		assertThat(c.type()).isEqualTo(ClearanceType.NEW);
		assertThat(c.corporation()).isTrue();
		assertThat(c.building()).isEqualTo(Building.OWNED);
		assertThat(c.amountPaid()).isEqualByComparingTo("0");
		assertThat(c.controlNumber()).isEqualTo(77);
		assertThat(c.secondEndorsementNumber()).isNull();
		assertThat(c.issuedOn()).isNull();
	}

	private ClearanceQuery sorted(ClearanceSort sort, boolean desc) {
		return new ClearanceQuery("", null, sort, desc, 0, 25);
	}

	private List<String> names(ClearanceQuery q) {
		return repo.search(q).items().stream().map(Clearance::businessName).toList();
	}
}
