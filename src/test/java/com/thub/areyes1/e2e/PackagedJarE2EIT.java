/**
 * Class File Name: PackagedJarE2EIT.java
 * Description: Launches the distributable jar the way a user would.
 */

package com.thub.areyes1.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Runs {@code java -jar ...-jar-with-dependencies.jar} in a separate JVM, which
 * catches packaging problems (manifest, missing dependencies, JVM flags) that
 * in-process tests cannot see.
 */
public class PackagedJarE2EIT {

	private static final long STARTUP_WAIT_SECONDS = 8;

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void jarStartsAndStaysUp() throws Exception {
		assumeFalse("no display available; run under xvfb-run", GraphicsEnvironment.isHeadless());
		File jar = new File(System.getProperty("app.jar", "target/bgybus-clearance-0.0.1-SNAPSHOT-jar-with-dependencies.jar"));
		assertTrue("packaged jar not found at " + jar + "; run `mvn verify`", jar.isFile());

		File db = E2eEnvironment.useShippedSampleDatabase(tmp.getRoot());
		File log = tmp.newFile("app.log");
		String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
		Process app = new ProcessBuilder(java,
				"-DDB_LOCATION=" + db.getAbsolutePath(),
				"-DREPORT_LOCATION=" + System.getProperty("REPORT_LOCATION"),
				"-jar", jar.getAbsolutePath())
				.redirectErrorStream(true)
				.redirectOutput(log)
				.start();
		try {
			boolean exited = app.waitFor(STARTUP_WAIT_SECONDS, TimeUnit.SECONDS);
			String output = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);

			assertFalse("app exited during startup:\n" + output, exited);
			assertFalse("app logged an exception during startup:\n" + output, output.contains("Exception"));
		} finally {
			app.destroyForcibly().waitFor();
		}
	}
}
