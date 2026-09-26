package com.thub.areyes1.clearance;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.thub.areyes1.db.SchemaMigrator;

/**
 * Stores clearances in the {@code bgy_clearance} table.
 *
 * <p>Column names and value formats are kept from the original desktop app, so
 * existing databases work unchanged: flags are TEXT ("1"/"0", older rows may
 * hold "true"), and the amount is TEXT holding a decimal number.
 */
@Repository
public class ClearanceRepository {

	private static final String COLUMNS = """
			id, name, address, activity, ownership, assoc_president, second_endorsment, control_no,
			owned, rented, singleprop, partnership, others, corporation, "new", amount_paid,
			capitalization, or_number, applicant_member_of, issued_on""";

	private static final String IS_NEW = "LOWER(CAST(\"new\" AS TEXT)) IN ('1', 'true')";

	private final JdbcClient jdbc;
	private final Clock clock;

	public ClearanceRepository(JdbcClient jdbc, Clock clock, SchemaMigrator schema) {
		// Depending on SchemaMigrator guarantees the schema is current before first use.
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public Optional<Clearance> findById(long id) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM bgy_clearance WHERE id = ?")
				.param(id)
				.query(MAPPER)
				.optional();
	}

	public PageResult<Clearance> search(ClearanceQuery query) {
		StringBuilder where = new StringBuilder(" WHERE 1 = 1");
		List<Object> params = new ArrayList<>();
		if (!query.text().isEmpty()) {
			String like = "%" + escapeLike(query.text().toLowerCase(Locale.ROOT)) + "%";
			where.append(" AND (LOWER(COALESCE(name, '')) LIKE ? ESCAPE '\\'"
					+ " OR LOWER(COALESCE(address, '')) LIKE ? ESCAPE '\\'"
					+ " OR CAST(control_no AS TEXT) = ?)");
			params.add(like);
			params.add(like);
			params.add(query.text());
		}
		if (query.type() == ClearanceType.NEW) {
			where.append(" AND ").append(IS_NEW);
		} else if (query.type() == ClearanceType.RENEWAL) {
			where.append(" AND NOT (").append(IS_NEW).append(")");
		}

		long total = jdbc.sql("SELECT COUNT(*) FROM bgy_clearance" + where)
				.params(params)
				.query(Long.class)
				.single();

		List<Object> pageParams = new ArrayList<>(params);
		pageParams.add(query.size());
		pageParams.add((long) query.page() * query.size());
		List<Clearance> items = jdbc.sql("SELECT " + COLUMNS + " FROM bgy_clearance" + where
				+ " ORDER BY " + query.sort().orderBy(query.descending()) + " LIMIT ? OFFSET ?")
				.params(pageParams)
				.query(MAPPER)
				.list();
		return new PageResult<>(items, query.page(), query.size(), total);
	}

	/** Most recently added clearances, newest first. */
	public List<Clearance> recent(int limit) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM bgy_clearance ORDER BY id DESC LIMIT ?")
				.param(limit)
				.query(MAPPER)
				.list();
	}

	public ClearanceStats stats() {
		int year = LocalDate.now(clock).getYear();
		record Row(boolean isNew, BigDecimal amount, LocalDate issuedOn) {
		}
		List<Row> rows = jdbc.sql("SELECT \"new\", amount_paid, issued_on FROM bgy_clearance")
				.query((rs, n) -> new Row(flag(rs.getString(1)), amount(rs.getString(2)), date(rs.getString(3))))
				.list();

		long newCount = rows.stream().filter(Row::isNew).count();
		BigDecimal collected = rows.stream().map(Row::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
		List<Row> thisYear = rows.stream()
				.filter(r -> r.issuedOn() != null && r.issuedOn().getYear() == year)
				.toList();
		BigDecimal yearCollected = thisYear.stream().map(Row::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
		return new ClearanceStats(rows.size(), collected, newCount, rows.size() - newCount, year,
				thisYear.size(), yearCollected);
	}

	/** Name of another clearance that already uses this control number, if any. */
	public Optional<String> controlNumberUsedBy(int controlNumber, Long excludeId) {
		return jdbc.sql("SELECT COALESCE(name, '') FROM bgy_clearance WHERE control_no = ? AND id <> ? LIMIT 1")
				.params(controlNumber, excludeId == null ? -1L : excludeId)
				.query(String.class)
				.optional();
	}

	/** Inserts a new clearance and returns it with its generated id. */
	public Clearance insert(Clearance c) {
		String now = Instant.now(clock).toString();
		Long id = jdbc.sql("""
				INSERT INTO bgy_clearance (
					name, address, activity, ownership, assoc_president, second_endorsment, control_no,
					owned, rented, singleprop, partnership, others, corporation, "new", amount_paid,
					capitalization, or_number, applicant_member_of, issued_on, created_at, updated_at)
				VALUES (:name, :address, :activity, :ownership, :hoa, :second, :control,
					:owned, :rented, :single, :partnership, :others, :corporation, :isNew, :amount,
					:capitalization, :orNumber, :memberOf, :issuedOn, :now, :now)
				RETURNING id""")
				.params(bind(c))
				.param("now", now)
				.query(Long.class)
				.single();
		return c.withId(id);
	}

	/** Updates an existing clearance; returns false if it no longer exists. */
	public boolean update(Clearance c) {
		return jdbc.sql("""
				UPDATE bgy_clearance SET
					name = :name, address = :address, activity = :activity, ownership = :ownership,
					assoc_president = :hoa, second_endorsment = :second, control_no = :control,
					owned = :owned, rented = :rented, singleprop = :single, partnership = :partnership,
					others = :others, corporation = :corporation, "new" = :isNew, amount_paid = :amount,
					capitalization = :capitalization, or_number = :orNumber,
					applicant_member_of = :memberOf, issued_on = :issuedOn, updated_at = :now
				WHERE id = :id""")
				.params(bind(c))
				.param("now", Instant.now(clock).toString())
				.param("id", c.id())
				.update() == 1;
	}

	public boolean delete(long id) {
		return jdbc.sql("DELETE FROM bgy_clearance WHERE id = ?").param(id).update() == 1;
	}

	private static Map<String, Object> bind(Clearance c) {
		Map<String, Object> p = new HashMap<>();
		p.put("name", c.businessName());
		p.put("address", c.address());
		p.put("activity", c.typeOfBusiness());
		p.put("ownership", c.ownerName());
		p.put("hoa", c.hoaPresident());
		p.put("second", c.secondEndorsementNumber());
		p.put("control", c.controlNumber());
		p.put("owned", flagText(c.building() == Building.OWNED));
		p.put("rented", flagText(c.building() == Building.RENTED));
		p.put("single", flagText(c.singleProprietorship()));
		p.put("partnership", flagText(c.partnership()));
		p.put("others", flagText(c.otherOwnership()));
		p.put("corporation", flagText(c.corporation()));
		p.put("isNew", flagText(c.type() != ClearanceType.RENEWAL));
		p.put("amount", c.amountPaid() == null ? null : c.amountPaid().toPlainString());
		p.put("capitalization", c.capitalization());
		p.put("orNumber", c.orNumber());
		p.put("memberOf", c.applicantMemberOf());
		p.put("issuedOn", c.issuedOn() == null ? null : c.issuedOn().toString());
		return p;
	}

	private static final RowMapper<Clearance> MAPPER = (rs, n) -> {
		boolean owned = flag(rs.getString("owned"));
		boolean rented = flag(rs.getString("rented"));
		return new Clearance(
				rs.getLong("id"),
				flag(rs.getString("new")) ? ClearanceType.NEW : ClearanceType.RENEWAL,
				integer(rs, "control_no"),
				date(rs.getString("issued_on")),
				rs.getString("name"),
				rs.getString("address"),
				rs.getString("activity"),
				rs.getString("capitalization"),
				owned ? Building.OWNED : rented ? Building.RENTED : null,
				flag(rs.getString("corporation")),
				flag(rs.getString("singleprop")),
				flag(rs.getString("partnership")),
				flag(rs.getString("others")),
				rs.getString("ownership"),
				rs.getString("applicant_member_of"),
				rs.getString("assoc_president"),
				integer(rs, "second_endorsment"),
				integer(rs, "or_number"),
				amount(rs.getString("amount_paid")));
	};

	static boolean flag(String value) {
		return value != null && ("1".equals(value.strip()) || "true".equalsIgnoreCase(value.strip()));
	}

	private static String flagText(boolean value) {
		return value ? "1" : "0";
	}

	/** Amounts are TEXT; blank or unparseable legacy values count as zero. */
	static BigDecimal amount(String value) {
		if (value == null || value.isBlank()) {
			return BigDecimal.ZERO;
		}
		try {
			return new BigDecimal(value.strip());
		} catch (NumberFormatException e) {
			return BigDecimal.ZERO;
		}
	}

	private static LocalDate date(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(value.strip());
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	/** Reads an optional whole number; legacy TEXT columns may hold non-numeric values. */
	private static Integer integer(ResultSet rs, String column) throws SQLException {
		String value = rs.getString(column);
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Integer.valueOf(value.strip());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String escapeLike(String s) {
		return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
