package com.thub.areyes1.db;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Brings the database up to the current schema on startup.
 *
 * <p>New databases get the full table. Databases created by earlier versions of
 * the app (including the original desktop app) keep their data: the table and
 * column names are unchanged, and missing columns are added.
 */
@Component
public class SchemaMigrator implements InitializingBean {

	/** Columns added after the original desktop schema, with their SQL types. */
	private static final Map<String, String> ADDED_COLUMNS = new LinkedHashMap<>();
	static {
		ADDED_COLUMNS.put("corporation", "TEXT");
		ADDED_COLUMNS.put("capitalization", "TEXT");
		ADDED_COLUMNS.put("or_number", "INTEGER");
		ADDED_COLUMNS.put("applicant_member_of", "TEXT");
		ADDED_COLUMNS.put("issued_on", "TEXT");
		ADDED_COLUMNS.put("created_at", "TEXT");
		ADDED_COLUMNS.put("updated_at", "TEXT");
	}

	private final JdbcClient jdbc;

	public SchemaMigrator(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void afterPropertiesSet() {
		jdbc.sql("""
				CREATE TABLE IF NOT EXISTS bgy_clearance (
					id                  INTEGER PRIMARY KEY AUTOINCREMENT,
					name                TEXT,
					address             TEXT,
					activity            TEXT,
					building            TEXT,
					ownership           TEXT,
					manager_owner       TEXT,
					assoc_president     TEXT,
					second_endorsment   TEXT,
					seconde_location    TEXT,
					control_no          INTEGER,
					owned               TEXT,
					rented              TEXT,
					singleprop          TEXT,
					partnership         TEXT,
					others              TEXT,
					"new"               TEXT,
					amount_paid         TEXT
				)""").update();

		Set<String> existing = jdbc.sql("PRAGMA table_info(bgy_clearance)")
				.query((rs, n) -> rs.getString("name"))
				.list().stream().collect(Collectors.toSet());
		ADDED_COLUMNS.forEach((column, type) -> {
			if (!existing.contains(column)) {
				jdbc.sql("ALTER TABLE bgy_clearance ADD COLUMN " + column + " " + type).update();
			}
		});

		jdbc.sql("""
				CREATE TABLE IF NOT EXISTS settings (
					key   TEXT PRIMARY KEY,
					value TEXT
				)""").update();
	}
}
