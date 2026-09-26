/**
 * Class File Name: E2eEnvironment.java
 * Description: Shared setup for the end-to-end tests.
 */

package com.thub.areyes1.e2e;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintFrame;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperPrint;

/**
 * Builds the same runtime environment the packaged app expects: a SQLite file
 * pointed to by the DB_LOCATION system property, and the compiled Jasper
 * report directory pointed to by REPORT_LOCATION.
 */
public final class E2eEnvironment {

	private E2eEnvironment() {
	}

	static {
		// ReportConstants reads REPORT_LOCATION once, when the class is first
		// loaded, so it has to be set before any test touches the report code.
		System.setProperty("REPORT_LOCATION", reportDirectory());
	}

	/**
	 * Creates an empty database with the production schema and points the app at it.
	 */
	public static File useFreshDatabase(File dir) throws Exception {
		File db = new File(dir, "e2e.db");
		Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
		try {
			Statement st = conn.createStatement();
			st.executeUpdate(readResource("/db/schema.sql"));
			st.close();
		} finally {
			conn.close();
		}
		System.setProperty("DB_LOCATION", db.getAbsolutePath());
		return db;
	}

	/**
	 * Copies the SampleDB.db that ships with the app and points the app at the copy.
	 */
	public static File useShippedSampleDatabase(File dir) throws Exception {
		File db = new File(dir, "SampleDB.db");
		InputStream in = E2eEnvironment.class.getResourceAsStream("/SampleDB.db");
		try {
			Files.copy(in, db.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} finally {
			in.close();
		}
		System.setProperty("DB_LOCATION", db.getAbsolutePath());
		return db;
	}

	/**
	 * Reads every row of bgy_clearance straight from the database, bypassing the app.
	 */
	public static List<Map<String, String>> rows(File db) throws SQLException {
		List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
		Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
		try {
			PreparedStatement ps = conn.prepareStatement("SELECT * FROM bgy_clearance ORDER BY id");
			ResultSet rs = ps.executeQuery();
			int cols = rs.getMetaData().getColumnCount();
			while (rs.next()) {
				Map<String, String> row = new LinkedHashMap<String, String>();
				for (int i = 1; i <= cols; i++) {
					row.put(rs.getMetaData().getColumnName(i), rs.getString(i));
				}
				rows.add(row);
			}
			rs.close();
			ps.close();
		} finally {
			conn.close();
		}
		return rows;
	}

	/**
	 * Inserts a row directly, the way a record created by an older build would look.
	 */
	public static void insertRow(File db, String name, String address, int controlNo, String amountPaid)
			throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
		try {
			PreparedStatement ps = conn.prepareStatement(
					"INSERT INTO bgy_clearance (name, address, control_no, amount_paid, new) VALUES (?,?,?,?,1)");
			ps.setString(1, name);
			ps.setString(2, address);
			ps.setInt(3, controlNo);
			ps.setString(4, amountPaid);
			ps.execute();
			ps.close();
		} finally {
			conn.close();
		}
	}

	/**
	 * Collects all text rendered on every page of a filled report.
	 */
	public static String text(JasperPrint print) {
		StringBuilder sb = new StringBuilder();
		for (JRPrintPage page : print.getPages()) {
			appendText(page.getElements(), sb);
		}
		return sb.toString();
	}

	private static void appendText(List<JRPrintElement> elements, StringBuilder sb) {
		for (JRPrintElement e : elements) {
			if (e instanceof JRPrintText) {
				sb.append(((JRPrintText) e).getFullText()).append('\n');
			} else if (e instanceof JRPrintFrame) {
				appendText(((JRPrintFrame) e).getElements(), sb);
			}
		}
	}

	/**
	 * Runs one SQL statement straight against the database, bypassing the app.
	 */
	public static void execute(File db, String sql) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
		try {
			Statement st = conn.createStatement();
			st.executeUpdate(sql);
			st.close();
		} finally {
			conn.close();
		}
	}

	private static String reportDirectory() {
		try {
			File jasper = new File(E2eEnvironment.class.getResource("/report/bgyclearance_report.jasper").toURI());
			return jasper.getParentFile().getAbsolutePath() + File.separator;
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String readResource(String path) {
		Reader reader = new InputStreamReader(E2eEnvironment.class.getResourceAsStream(path), StandardCharsets.UTF_8);
		Scanner scanner = new Scanner(reader).useDelimiter("\\A");
		try {
			return scanner.next();
		} finally {
			scanner.close();
		}
	}
}
