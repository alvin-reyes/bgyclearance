package com.thub.areyes1.clearance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.thub.areyes1.TestDatabase;

/** Opens the SampleDB.db written by the original desktop app. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LegacyDatabaseTest {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		TestDatabase.registerLegacy(registry);
	}

	@Autowired
	ClearanceRepository repo;

	@Autowired
	JdbcClient jdbc;

	@Test
	void upgradesTheSchemaAndKeepsEveryRecord() {
		List<String> columns = jdbc.sql("PRAGMA table_info(bgy_clearance)").query((rs, n) -> rs.getString("name")).list();
		assertThat(columns).contains("capitalization", "or_number", "applicant_member_of", "issued_on", "created_at");

		assertThat(repo.search(ClearanceQuery.all()).total()).isEqualTo(59);
		assertThat(repo.stats().total()).isEqualTo(59);
	}

	@Test
	void newRecordsCanBeAddedAfterTheUpgrade() {
		Clearance saved = repo.insert(new Clearance(null, ClearanceType.NEW, 9001, LocalDate.of(2026, 3, 1),
				"After Upgrade", null, null, null, null, false, false, false, false, null, null, null, null, 42,
				new BigDecimal("5")));

		try {
			assertThat(repo.findById(saved.id())).contains(saved);
		} finally {
			repo.delete(saved.id()); // tests in this class share one copy of the legacy file
		}
	}
}
