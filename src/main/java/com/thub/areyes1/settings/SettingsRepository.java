package com.thub.areyes1.settings;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.thub.areyes1.db.SchemaMigrator;

/** Stores {@link BarangaySettings} as key/value rows in the {@code settings} table. */
@Repository
public class SettingsRepository {

	private final JdbcClient jdbc;

	public SettingsRepository(JdbcClient jdbc, SchemaMigrator schema) {
		this.jdbc = jdbc;
	}

	public BarangaySettings load() {
		Map<String, String> values = jdbc.sql("SELECT key, value FROM settings")
				.query((rs, n) -> Map.entry(rs.getString(1), rs.getString(2) == null ? "" : rs.getString(2)))
				.list().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		return new BarangaySettings(
				values.getOrDefault("barangay_name", ""),
				values.getOrDefault("municipality", ""),
				values.getOrDefault("province", ""),
				values.getOrDefault("punong_barangay", ""),
				values.getOrDefault("secretary", ""));
	}

	@Transactional
	public void save(BarangaySettings s) {
		put("barangay_name", s.barangayName());
		put("municipality", s.municipality());
		put("province", s.province());
		put("punong_barangay", s.punongBarangay());
		put("secretary", s.secretary());
	}

	/** A single stored value, or empty if never set or blank. */
	public Optional<String> get(String key) {
		return jdbc.sql("SELECT value FROM settings WHERE key = ?")
				.param(key)
				.query(String.class)
				.optional()
				.filter(v -> !v.isBlank());
	}

	public void put(String key, String value) {
		jdbc.sql("INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")
				.params(key, value == null ? "" : value.strip())
				.update();
	}
}
