/**
 * Class File Name: DatabaseSchema.java
 * Description: Creates and upgrades the bgy_clearance table on startup.
 */

package com.thub.areyes1.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

/**
 * Makes sure the database the app points at has the current schema: creates
 * the table in a new database, and adds columns introduced after older
 * databases (such as SampleDB.db) were created.
 */
@Component
public class DatabaseSchema extends BaseDao {

	/** Columns added after the original schema, with their SQL types. */
	private static final Map<String, String> ADDED_COLUMNS = new LinkedHashMap<String, String>();
	static {
		ADDED_COLUMNS.put("corporation", "TEXT");
		ADDED_COLUMNS.put("capitalization", "TEXT");
		ADDED_COLUMNS.put("or_number", "INTEGER");
		ADDED_COLUMNS.put("applicant_member_of", "TEXT");
	}

	/**
	 * Creates the table if missing and adds any missing columns.
	 *
	 * @throws SQLException the SQL exception
	 */
	@PostConstruct
	public void upgrade() throws SQLException {
		Connection conn = getConnection();
		try {
			ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/schema.sql"));

			Set<String> existing = new HashSet<String>();
			Statement st = conn.createStatement();
			ResultSet rs = st.executeQuery("PRAGMA table_info(bgy_clearance)");
			while (rs.next()) {
				existing.add(rs.getString("name"));
			}
			rs.close();
			for (Map.Entry<String, String> column : ADDED_COLUMNS.entrySet()) {
				if (!existing.contains(column.getKey())) {
					st.executeUpdate("ALTER TABLE bgy_clearance ADD COLUMN " + column.getKey() + " " + column.getValue());
				}
			}
			st.close();
		} finally {
			conn.close();
		}
	}
}
