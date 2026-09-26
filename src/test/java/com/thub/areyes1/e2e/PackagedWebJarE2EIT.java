/**
 * Class File Name: PackagedWebJarE2EIT.java
 * Description: Launches the distributable web jar the way a user would.
 */

package com.thub.areyes1.e2e;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Runs {@code java -jar ...-web.jar} in a separate JVM from an empty working
 * directory, with no REPORT_LOCATION, so it must create its own database and
 * load the report from inside the jar.
 */
public class PackagedWebJarE2EIT {

	private static final long STARTUP_TIMEOUT_MS = 60000;
	private static final Pattern PORT = Pattern.compile("Tomcat started on port\\(s\\): (\\d+)");

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private Process app;

	@After
	public void stop() throws Exception {
		if (app != null) {
			app.destroyForcibly().waitFor();
		}
	}

	@Test
	public void webJarServesPagesAndPdfsFromAnEmptyDirectory() throws Exception {
		File jar = new File(System.getProperty("web.jar", "target/bgybus-clearance-0.0.1-SNAPSHOT-web.jar"));
		assertTrue("web jar not found at " + jar + "; run `mvn verify`", jar.isFile());
		File workDir = tmp.newFolder("work");
		File log = tmp.newFile("web.log");
		String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
		app = new ProcessBuilder(java, "-jar", jar.getAbsolutePath(), "--server.port=0")
				.directory(workDir)
				.redirectErrorStream(true)
				.redirectOutput(log)
				.start();

		String base = "http://127.0.0.1:" + waitForPort(log);

		File db = new File(workDir, "clearances.db");
		assertTrue("default database should be created in the working directory", db.isFile());
		assertEquals(200, get(base + "/clearances").status);

		Response created = post(base + "/clearances",
				"type=NEW&controlNumber=8001&businessName=Jar+Store&address=1+Main+St.&amountPaid=10.00");
		assertEquals(302, created.status);
		String location = created.location;
		assertNotNull(location);
		assertTrue(location, location.matches(".*/clearances/\\d+$"));

		Response detail = get(location);
		assertEquals(200, detail.status);
		assertTrue(detail.body.contains("Jar Store"));

		Response pdf = get(location + "/report.pdf");
		assertEquals(200, pdf.status);
		assertEquals("application/pdf", pdf.contentType);
		assertTrue(pdf.body.startsWith("%PDF-"));

		String output = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
		assertFalse("app logged an exception:\n" + output, output.contains("Exception"));
	}

	private String waitForPort(File log) throws Exception {
		long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS;
		while (System.currentTimeMillis() < deadline) {
			String output = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
			Matcher m = PORT.matcher(output);
			if (m.find()) {
				return m.group(1);
			}
			if (!app.isAlive()) {
				throw new AssertionError("web app exited during startup:\n" + output);
			}
			Thread.sleep(200);
		}
		throw new AssertionError("web app did not start within " + STARTUP_TIMEOUT_MS + "ms:\n"
				+ new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8));
	}

	private static Response get(String url) throws Exception {
		return send((HttpURLConnection) new URL(url).openConnection());
	}

	private static Response post(String url, String form) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);
		conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		OutputStream out = conn.getOutputStream();
		out.write(form.getBytes(StandardCharsets.UTF_8));
		out.close();
		return send(conn);
	}

	private static Response send(HttpURLConnection conn) throws Exception {
		conn.setInstanceFollowRedirects(false);
		Response r = new Response();
		r.status = conn.getResponseCode();
		r.location = conn.getHeaderField("Location");
		r.contentType = conn.getContentType();
		InputStream in = r.status >= 400 ? conn.getErrorStream() : conn.getInputStream();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		if (in != null) {
			byte[] buf = new byte[8192];
			for (int n; (n = in.read(buf)) != -1;) {
				bytes.write(buf, 0, n);
			}
			in.close();
		}
		r.body = new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1);
		return r;
	}

	private static final class Response {
		int status;
		String location;
		String contentType;
		String body;
	}
}
