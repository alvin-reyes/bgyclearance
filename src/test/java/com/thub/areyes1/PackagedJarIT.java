package com.thub.areyes1;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the packaged jar with {@code java -jar} from an empty folder, the way a
 * user would, so packaging problems (missing resources, fonts, templates) fail
 * the build.
 */
class PackagedJarIT {

	private static final Pattern PORT = Pattern.compile("Tomcat started on port (\\d+)");

	@TempDir
	Path workDir;

	private Process app;

	@AfterEach
	void stop() throws InterruptedException {
		if (app != null) {
			app.destroy();
			app.waitFor();
		}
	}

	@Test
	void startsFromAnEmptyFolderAndServesPagesAndPdfs() throws Exception {
		Path jar = Path.of(System.getProperty("app.jar", "target/bgyclearance.jar")).toAbsolutePath();
		assertThat(jar).as("run `./mvnw verify` to build the jar").isRegularFile();
		Path log = workDir.resolve("app.log");
		String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
		app = new ProcessBuilder(java, "-jar", jar.toString(), "--server.port=0")
				.directory(workDir.toFile())
				.redirectErrorStream(true)
				.redirectOutput(log.toFile())
				.start();
		String base = "http://127.0.0.1:" + waitForPort(log);
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

		assertThat(workDir.resolve("clearances.db")).as("default database is created").isRegularFile();
		assertThat(get(http, base + "/").statusCode()).isEqualTo(200);
		assertThat(get(http, base + "/css/app.css").statusCode()).isEqualTo(200);

		HttpResponse<String> created = http.send(HttpRequest.newBuilder(URI.create(base + "/clearances"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(
						"type=NEW&controlNumber=8001&issuedOn=2026-03-01&businessName=Jar+Store&amountPaid=10.00"))
				.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(created.statusCode()).isEqualTo(302);
		String location = created.headers().firstValue("Location").orElseThrow();
		assertThat(location).matches(".*/clearances/\\d+$");

		HttpResponse<byte[]> pdf = http.send(HttpRequest.newBuilder(URI.create(location + "/clearance.pdf")).build(),
				HttpResponse.BodyHandlers.ofByteArray());
		assertThat(pdf.statusCode()).isEqualTo(200);
		assertThat(pdf.headers().firstValue("Content-Type")).contains("application/pdf");
		assertThat(new String(pdf.body(), 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

		assertThat(Files.readString(log)).doesNotContain("Exception", "ERROR");
	}

	private HttpResponse<String> get(HttpClient http, String url) throws IOException, InterruptedException {
		return http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
	}

	private String waitForPort(Path log) throws Exception {
		long deadline = System.currentTimeMillis() + 60_000;
		while (System.currentTimeMillis() < deadline) {
			String output = Files.exists(log) ? Files.readString(log) : "";
			Matcher m = PORT.matcher(output);
			if (m.find()) {
				return m.group(1);
			}
			if (!app.isAlive()) {
				throw new AssertionError("app exited during startup:\n" + output);
			}
			Thread.sleep(200);
		}
		throw new AssertionError("app did not start within 60s:\n" + Files.readString(log));
	}
}
