/**
 * Class File Name: WebApplication.java
 * Description: Entry point for the locally hosted web version of the app.
 */

package com.thub.areyes1.web;

import java.io.File;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

import com.thub.areyes1.config.DaoConfig;
import com.thub.areyes1.config.DataSourceFactoryConfig;
import com.thub.areyes1.config.ServiceConfig;

/**
 * Serves the clearance registry in a browser at http://localhost:8080, using
 * the same service, DAO, SQLite database and Jasper report as the desktop app.
 */
@SpringBootApplication(scanBasePackageClasses = WebApplication.class)
@Import({DataSourceFactoryConfig.class, DaoConfig.class, ServiceConfig.class})
public class WebApplication {

	/** Database used when DB_LOCATION is not set; created on first start. */
	public static final String DEFAULT_DB = "clearances.db";

	/**
	 * Starts the web app.
	 *
	 * @param args the arguments
	 */
	public static void main(String[] args) {
		start(args);
	}

	/**
	 * Starts the web app and returns its context (used by tests).
	 *
	 * @param args the arguments
	 * @return the application context
	 */
	public static ConfigurableApplicationContext start(String... args) {
		if (System.getProperty("DB_LOCATION") == null) {
			System.setProperty("DB_LOCATION", new File(DEFAULT_DB).getAbsolutePath());
		}
		return SpringApplication.run(WebApplication.class, args);
	}
}
