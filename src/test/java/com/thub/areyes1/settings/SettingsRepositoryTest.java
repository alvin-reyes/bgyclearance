package com.thub.areyes1.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.thub.areyes1.TestDatabase;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SettingsRepositoryTest {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		TestDatabase.register(registry);
	}

	@Autowired
	SettingsRepository settings;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void clean() {
		TestDatabase.clear(jdbc);
	}

	@Test
	void startsEmptyAndUnconfigured() {
		assertThat(settings.load()).isEqualTo(BarangaySettings.empty());
		assertThat(settings.load().isConfigured()).isFalse();
	}

	@Test
	void savesAndOverwrites() {
		settings.save(new BarangaySettings("Poblacion", "Cebu City", "Cebu", "Hon. Ramon Cruz", ""));
		settings.save(new BarangaySettings(" San Isidro ", "Cebu City", "Cebu", "Hon. Ramon Cruz", "Liza Tan"));

		BarangaySettings loaded = settings.load();
		assertThat(loaded).isEqualTo(new BarangaySettings("San Isidro", "Cebu City", "Cebu", "Hon. Ramon Cruz", "Liza Tan"));
		assertThat(loaded.isConfigured()).isTrue();
	}
}
