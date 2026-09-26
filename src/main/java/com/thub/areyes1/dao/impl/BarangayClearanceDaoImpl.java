/**
 * Class File Name: BarangayClearanceDaoImpl.java
 * Author: alvinreyes
 * Date Generate: Jun 14, 2015
 * Description
 */

package com.thub.areyes1.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.thub.areyes1.dao.BarangayClearanceDao;
import com.thub.areyes1.dao.BaseDao;
import com.thub.areyes1.exception.BarangayClearanceServiceException;
import com.thub.areyes1.exception.BarangayClearanceValidationException;
import com.thub.areyes1.obj.BarangayClearance;
import com.thub.areyes1.obj.BarangayClearanceReport;
import com.thub.areyes1.obj.BarangayClearanceType;
import com.thub.areyes1.obj.BuildingType;

 
/**
 * The Class BarangayClearanceDaoImpl.
 */
@Repository
public class BarangayClearanceDaoImpl extends BaseDao
		implements
			BarangayClearanceDao {

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.thub.areyes1.dao.BarangayClearanceDao#saveClearance(com.thub.areyes1
	 * .obj.BarangayClearance)
	 */
	public BarangayClearance saveClearance(BarangayClearance barangayClearance)
			throws BarangayClearanceServiceException {

		PreparedStatement ps;
		Connection conn = null;
		try {
			// Check if id exist, if it does then it's an update
			if (barangayClearance.getId() == 0) {
				String insertSql = ""
						+ "INSERT INTO "
						+ " bgy_clearance "
						+ " ("
						+ "	name,"
						+ "	address,"
						+ "	activity,"
						+ "	building,"
						+ "	ownership,"
						+ "	manager_owner,"
						+ "	assoc_president,"
						+ "	second_endorsment,"
						+ "	seconde_location,"
						+ "	rented,"
						+ "	new,"
						+ "	partnership,"
						+ "	singleprop,"
						+ " corporation,"
						+ "	others,"
						+ " amount_paid,"
						+ " control_no,"
						+ " owned,"
						+ " capitalization,"
						+ " or_number,"
						+ " applicant_member_of"
						+ ")"
						+ "VALUES "
						+ " (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
				conn = this.getConnection();
				ps = conn.prepareStatement(
						insertSql, Statement.RETURN_GENERATED_KEYS);
				ps.setString(1, barangayClearance.getBusinessName());
				ps.setString(2, barangayClearance.getAddress());
				ps.setString(3, barangayClearance.getTypeOfBusiness());
				ps.setString(4, "");
				ps.setString(5, barangayClearance.getOwnership());
				ps.setString(6, barangayClearance.getAssocHomeOwnerPresident());
				ps.setString(7, barangayClearance.getAssocHomeOwnerPresident());
				setNullableInt(ps, 8, barangayClearance.getSecondEndorsmentNumber());
				ps.setString(9, barangayClearance.getAddress());
				ps.setBoolean(10, barangayClearance.isRented());
				ps.setBoolean(11, barangayClearance.isForNew());
				ps.setBoolean(12, barangayClearance.isParntership());
				ps.setBoolean(13, barangayClearance.isSingleProprietorship());
				ps.setBoolean(14, barangayClearance.isCorporation());
				ps.setBoolean(15, barangayClearance.isOthers());
				ps.setString(16, String.valueOf(barangayClearance.getAmountPaid()));
				setNullableInt(ps, 17, barangayClearance.getControlNumber());
				ps.setBoolean(18, barangayClearance.isOwned());
				ps.setString(19, barangayClearance.getCapitalization());
				setNullableInt(ps, 20, barangayClearance.getOrNumber());
				ps.setString(21, barangayClearance.getApplicantMemberOf());

			}else {
				String updateSql = ""
						+ "UPDATE "
						+ " bgy_clearance "
						+ " set "
						+ "name = ?,"
						+ "address = ?,"
						+ "activity = ?,"
						+ "building =? ,"
						+ "ownership =?,"
						+ "manager_owner =?,"
						+ "assoc_president =? ,"
						+ "second_endorsment =?,"
						+ "seconde_location =?,"
						+ "amount_paid = ?,"
						+ "capitalization = ?,"
						+ "or_number = ?,"
						+ "applicant_member_of = ?,"
						+ "control_no = ?,"
						+ "new = ?,"
						+ "owned = ?,"
						+ "rented = ?,"
						+ "singleprop = ?,"
						+ "partnership = ?,"
						+ "corporation = ?,"
						+ "others = ? "
						+ "WHERE  "
						+ " id = ? ";
				conn = this.getConnection();
				ps = conn.prepareStatement(
						updateSql);
				ps.setString(1, barangayClearance.getBusinessName());
				ps.setString(2, barangayClearance.getAddress());
				ps.setString(3, barangayClearance.getTypeOfBusiness());
				ps.setString(4, "");
				ps.setString(5, barangayClearance.getOwnership());
				ps.setString(6, barangayClearance.getAssocHomeOwnerPresident());
				ps.setString(7, barangayClearance.getAssocHomeOwnerPresident());
				setNullableInt(ps, 8, barangayClearance.getSecondEndorsmentNumber());
				ps.setString(9, barangayClearance.getAddress());
				ps.setString(10, String.valueOf(barangayClearance.getAmountPaid()));
				ps.setString(11, barangayClearance.getCapitalization());
				setNullableInt(ps, 12, barangayClearance.getOrNumber());
				ps.setString(13, barangayClearance.getApplicantMemberOf());
				setNullableInt(ps, 14, barangayClearance.getControlNumber());
				ps.setBoolean(15, barangayClearance.isForNew());
				ps.setBoolean(16, barangayClearance.isOwned());
				ps.setBoolean(17, barangayClearance.isRented());
				ps.setBoolean(18, barangayClearance.isSingleProprietorship());
				ps.setBoolean(19, barangayClearance.isParntership());
				ps.setBoolean(20, barangayClearance.isCorporation());
				ps.setBoolean(21, barangayClearance.isOthers());
				ps.setInt(22, barangayClearance.getId());
				
			}
			ps.execute();
			if (barangayClearance.getId() == 0) {
				ResultSet keys = ps.getGeneratedKeys();
				if (keys.next()) {
					barangayClearance.setId(keys.getInt(1));
				}
				keys.close();
			}
		} catch (SQLException ex) {
			System.out.println(ex);
			throw new BarangayClearanceServiceException();
		} finally {
			closeQuietly(conn);
		}

		return barangayClearance;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.thub.areyes1.dao.BarangayClearanceDao#removeClearance(com.thub.areyes1
	 * .obj.BarangayClearance)
	 */
	public boolean removeClearance(BarangayClearance barangayClearance)
			throws BarangayClearanceServiceException {
		
		Connection conn = null;
		try {
			// Only persisted clearances (non-zero id) can be removed.
			if (barangayClearance.getId() != 0) {
				String insertSql = ""
						+ "DELETE FROM "
						+ " bgy_clearance "
						+ " WHERE id = ?";
				conn = this.getConnection();
				PreparedStatement ps = conn.prepareStatement(
						insertSql);
				ps.setInt(1, barangayClearance.getId());
				ps.execute();

			}
		} catch (SQLException ex) {
			System.out.println(ex);
			return false;
			
		} finally {
			closeQuietly(conn);
		}

		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.thub.areyes1.dao.BarangayClearanceDao#generateBarangayReport(com.
	 * thub.areyes1.obj.BarangayClearance)
	 */
	public BarangayClearanceReport generateBarangayReport(
			BarangayClearance barangayClearance)
			throws BarangayClearanceServiceException {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.thub.areyes1.dao.BarangayClearanceDao#generateAndSaveBarangayReport
	 * (com.thub.areyes1.obj.BarangayClearance)
	 */
	public BarangayClearanceReport generateAndSaveBarangayReport(
			BarangayClearance barangayClearance)
			throws BarangayClearanceServiceException {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	/* (non-Javadoc)
	 * @see com.thub.areyes1.dao.BarangayClearanceDao#getAllBarangayClearance()
	 */
	public List<BarangayClearance> getAllBarangayClearance()
			throws BarangayClearanceServiceException {
		
		List<BarangayClearance> listOfBgyClearance = new ArrayList<BarangayClearance>();
		Connection conn = null;
		try {
			// Check if id exist, if it does then it's an update
			
				String getAllsql = ""
						+ "SELECT * FROM "
						+ " bgy_clearance "
						+ " ";
				conn = this.getConnection();
				PreparedStatement ps = conn.prepareStatement(getAllsql);
				ps.execute();
				
				ResultSet rs = ps.getResultSet();
				while(rs.next()) {
					BarangayClearance bgyClearance = new BarangayClearance();
					bgyClearance.setId(rs.getInt("id"));
					bgyClearance.setControlNumber(rs.getInt("control_no"));
					bgyClearance.setBusinessName(rs.getString("name"));
					bgyClearance.setAddress(rs.getString("address"));
					bgyClearance.setAmountPaid(Float.valueOf((
							rs.getString("amount_paid") == null) ? "0": rs.getString("amount_paid")));
					
					//bgyClearance.setApplicantMemberOf(rs.getString(""));
					//address = ?,activity = ?,building =? ,ownership =?,manager_owner =?,assoc_president =? ,second_endorsment =?,"
					listOfBgyClearance.add(bgyClearance);
				}

			
		} 
		catch (SQLException ex) {	System.out.println(ex);return null;}
		catch(BarangayClearanceValidationException bvex) {
			System.out.println(bvex);
			return null;
		} finally {
			closeQuietly(conn);
		}
		
		return listOfBgyClearance;
	}
	
	/* (non-Javadoc)
	 * @see com.thub.areyes1.dao.BarangayClearanceDao#getAllBarangayClearanceInRange(int, int)
	 */
	public List<BarangayClearance> getAllBarangayClearanceInRange(int from,
			int to) throws BarangayClearanceServiceException {
		// TODO Auto-generated method stub
		return null;
	}
	
	/* (non-Javadoc)
	 * @see com.thub.areyes1.dao.BarangayClearanceDao#getBarangayClearanceData(int)
	 */
	public BarangayClearance getBarangayClearanceData(int id)
			throws BarangayClearanceServiceException {
		
		BarangayClearance bgyClearance = new BarangayClearance();
		Connection conn = null;
		try {
			// Check if id exist, if it does then it's an update
			
				String getAllsql = ""
						+ "SELECT * FROM "
						+ " bgy_clearance "
						+ "WHERE id = ?";
				conn = this.getConnection();
				PreparedStatement ps = conn.prepareStatement(getAllsql);
				ps.setInt(1, id);
				ps.execute();
				
				ResultSet rs = ps.getResultSet();
				while(rs.next()) {
					bgyClearance.setId(rs.getInt("id"));
					
					if(isFlagSet(rs.getString("new"))) {
						bgyClearance.setForNew(true);
					}else {
						bgyClearance.setForRenewal(true);
					}
					
					bgyClearance.setControlNumber(rs.getInt("control_no"));
					bgyClearance.setAddress(rs.getString("address"));
					bgyClearance.setOwnership(rs.getString("ownership"));
					bgyClearance.setSingleProprietorship(isFlagSet(rs.getString("singleprop")));
					bgyClearance.setParntership(isFlagSet(rs.getString("partnership")));
					bgyClearance.setCorporation(isFlagSet(rs.getString("corporation")));
					bgyClearance.setOthers(isFlagSet(rs.getString("others")));
					bgyClearance.setOwned(isFlagSet(rs.getString("owned")));
					bgyClearance.setRented(isFlagSet(rs.getString("rented")));
					bgyClearance.setBarangayClearanceType(bgyClearance.isForNew()
							? BarangayClearanceType.NEW : BarangayClearanceType.RENEWAL);
					if (bgyClearance.isOwned()) {
						bgyClearance.setBuildingType(BuildingType.OWNED);
					} else if (bgyClearance.isRented()) {
						bgyClearance.setBuildingType(BuildingType.RENTED);
					}
					bgyClearance.setAssocHomeOwnerPresident(rs.getString("assoc_president"));
					int secondEndorsment = rs.getInt("second_endorsment");
					bgyClearance.setSecondEndorsmentNumber(rs.wasNull() ? null : secondEndorsment);
					bgyClearance.setTypeOfBusiness(rs.getString("activity"));
					bgyClearance.setCapitalization(rs.getString("capitalization"));
					bgyClearance.setApplicantMemberOf(rs.getString("applicant_member_of"));
					int orNumber = rs.getInt("or_number");
					bgyClearance.setOrNumber(rs.wasNull() ? null : orNumber);
					
/*
 * 
 * CREATE TABLE "bgy_clearance" (
	`id`	INTEGER PRIMARY KEY AUTOINCREMENT,
	`name`	TEXT,
	`address`	TEXT,
	`activity`	TEXT,
	`building`	TEXT,
	`ownership`	TEXT,
	`manager_owner`	TEXT,
	`assoc_president`	TEXT,
	`second_endorsment`	TEXT,
	`seconde_location`	TEXT,
	`control_no`	INTEGER,
	`owned`	TEXT,
	`rented`	TEXT,
	`singleprop`	TEXT,
	`partnership`	TEXT,
	`others`	TEXT,
	`new`	TEXT,
	`amount_paid`	TEXT
)

 */
					
					bgyClearance.setControlNumber(rs.getInt("control_no"));
					bgyClearance.setBusinessName(rs.getString("name"));
					bgyClearance.setAmountPaid(Float.valueOf((
							rs.getString("amount_paid") == null) ? "0": rs.getString("amount_paid")));
					
				}	
		} 
		catch (SQLException ex) {	System.out.println(ex);return null;}
		catch(BarangayClearanceValidationException bvex) {
			System.out.println(bvex);
			return null;
		} finally {
			closeQuietly(conn);
		}
		
		return bgyClearance;
	}

	/**
	 * Boolean columns are TEXT; setBoolean stores them as "1"/"0".
	 *
	 * @param value the column value
	 * @return true, if the flag is set
	 */
	private static boolean isFlagSet(String value) {
		return "1".equals(value) || "true".equalsIgnoreCase(value);
	}

	/**
	 * Closes the connection (and with it any statements and result sets).
	 *
	 * @param conn the connection, may be null
	 */
	private static void closeQuietly(Connection conn) {
		if (conn != null) {
			try {
				conn.close();
			} catch (SQLException ex) {
				System.out.println(ex);
			}
		}
	}

	/**
	 * Binds an optional integer, storing NULL when it is not set.
	 *
	 * @param ps the statement
	 * @param index the parameter index
	 * @param value the value, may be null
	 * @throws SQLException the SQL exception
	 */
	private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
		if (value == null) {
			ps.setNull(index, Types.INTEGER);
		} else {
			ps.setInt(index, value);
		}
	}

}
