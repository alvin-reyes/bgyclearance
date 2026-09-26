package com.thub.areyes1;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Barangay Business Clearance: a locally hosted web app for registering
 * business clearances and printing them as PDFs.
 */
@SpringBootApplication
public class BgyClearanceApplication {

	/** The system clock; tests replace it to control "today". */
	@Bean
	Clock clock() {
		return Clock.systemDefaultZone();
	}

	public static void main(String[] args) {
		SpringApplication.run(BgyClearanceApplication.class, args);
	}
}
