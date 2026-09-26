package com.thub.areyes1;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Test helpers: a throwaway SQLite file per test class and a fixed clock. */
public final class TestDatabase {

	/** "Today" in tests: 15 March 2026, Manila time. */
	public static final Instant NOW = Instant.parse("2026-03-15T02:00:00Z");
	public static final ZoneId ZONE = ZoneId.of("Asia/Manila");

	private TestDatabase() {
	}

	/** Points the app at a new, empty database file. */
	public static Path register(DynamicPropertyRegistry registry) {
		Path db = newFile("test");
		registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
		return db;
	}

	/** Points the app at a copy of the database saved by the original desktop app. */
	public static Path registerLegacy(DynamicPropertyRegistry registry) {
		Path db = newFile("legacy");
		try (InputStream in = TestDatabase.class.getResourceAsStream("/legacy/SampleDB.db")) {
			Files.copy(in, db, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
		return db;
	}

	public static void clear(JdbcClient jdbc) {
		jdbc.sql("DELETE FROM bgy_clearance").update();
		jdbc.sql("DELETE FROM settings").update();
	}

	private static Path newFile(String prefix) {
		try {
			Path dir = Files.createTempDirectory("bgyclearance-" + prefix);
			dir.toFile().deleteOnExit();
			return dir.resolve("clearances.db");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@TestConfiguration
	public static class FixedClock {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(NOW, ZONE);
		}
	}
}
