/*******************************************************************************
 * Copyright (c) 2016 German Federal Institute for Risk Assessment (BfR)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Department Biological Safety - BfR
 *******************************************************************************/
package de.bund.bfr.knime.openkrise.db.imports.custom.bfrnewformat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import de.bund.bfr.knime.openkrise.db.DBKernel;
import de.bund.bfr.knime.openkrise.db.MyDBI;

public class Station {

	private static HashMap<String, Station> gathereds = new HashMap<>();
	private HashMap<String, String> flexibles = new HashMap<>();
	private List<Exception> exceptions = new ArrayList<>();

	public List<Exception> getExceptions() {
		return exceptions;
	}

	public static void reset() {
		gathereds = new HashMap<>();
	}
	public void addFlexibleField(String key, String value) {
		flexibles.put(key, value);
	}
	
	private String id;
	public String getId() {
		return id;
	}
	public Station setId(String id) {
		this.id = id;
		gathereds.put(id, this);
		return this;
	}
	public String getName() {
		return name;
	}
	public Station setName(String name) {
		this.name = name;
		return this;
	}
	public String getStreet() {
		return street;
	}
	public Station setStreet(String street) {
		this.street = street;
		return this;
	}
	public String getNumber() {
		return number;
	}
	public Station setNumber(String number) {
		this.number = number;
		return this;
	}
	public String getZip() {
		return zip;
	}
	public Station setZip(String zip) {
		this.zip = zip;
		return this;
	}
	public String getCity() {
		return city;
	}
	public Station setCity(String city) {
		this.city = city;
		return this;
	}
	public String getDistrict() {
		return district;
	}
	public Station setDistrict(String district) {
		this.district = district;
		return this;
	}
	public String getState() {
		return state;
	}
	public Station setState(String state) {
		this.state = state;
		return this;
	}
	public String getCountry() {
		return country;
	}
	public Station setCountry(String country) {
		this.country = country;
		return this;
	}
	public String getTypeOfBusiness() {
		return typeOfBusiness;
	}
	public Station setTypeOfBusiness(String typeOfBusiness) {
		this.typeOfBusiness = typeOfBusiness;
		return this;
	}
	private String name;
	private String street;
	private String number;
	private String zip;
	private String city;
	private String district;
	private String state;
	private String country;
	private String typeOfBusiness;
	
	private Integer dbId;
	public void setDbId(Integer dbId) {
		this.dbId = dbId;
	}
	public Integer getDbId() {
		return dbId;
	}
	
	public Integer getID(Integer miDbId, MyDBI mydbi) throws Exception {
		if (gathereds.get(id).getDbId() != null) dbId = gathereds.get(id).getDbId();
		if (dbId != null) {
			handleFlexibles(mydbi);
			return dbId;
		}
		Integer retId = getID(new String[]{"Name","Strasse","Hausnummer","PLZ","Ort","District","Bundesland","Land","Betriebsart","Serial"},
				new String[]{name,street,number,zip,city,district,state,country,typeOfBusiness,id}, miDbId, mydbi);
		dbId = retId;
		gathereds.get(id).setDbId(dbId);
		
		if (retId != null) {
			handleFlexibles(mydbi);
		}
		return retId;
	}
	public void handleFlexibles(MyDBI mydbi) {
		// Further flexible cells
		for (Entry<String, String> es : flexibles.entrySet()) {
			if (es.getValue() != null && !es.getValue().trim().isEmpty()) {
				String sql = "DELETE FROM " + MyDBI.delimitL("ExtraFields") +
						" WHERE " + MyDBI.delimitL("tablename") + "='Station'" +
						" AND " + MyDBI.delimitL("id") + "=" + dbId +
						" AND " + MyDBI.delimitL("attribute") + "='" + es.getKey() + "'";
				if (mydbi != null) mydbi.sendRequest(sql, false, false);
				else DBKernel.sendRequest(sql, false);
				sql = "INSERT INTO " + MyDBI.delimitL("ExtraFields") +
						" (" + MyDBI.delimitL("tablename") + "," + MyDBI.delimitL("id") + "," + MyDBI.delimitL("attribute") + "," + MyDBI.delimitL("value") +
						") VALUES ('Station'," + dbId + ",'" + es.getKey() + "','" + es.getValue() + "')";
				if (mydbi != null) mydbi.sendRequest(sql, false, false);
				else DBKernel.sendRequest(sql, false);
			}
		}
	}
	private Integer getID(String[] feldnames, String[] feldVals, Integer miDbId, MyDBI mydbi) throws Exception {
		Integer result = null;
		String sql = "SELECT " + MyDBI.delimitL("ID") + " FROM " + MyDBI.delimitL("Station") + " WHERE TRUE ";
		String in = MyDBI.delimitL("ImportSources");
		String iv =  "';" + miDbId + ";'";
		String serialWhere = "";
		for (int i=0;i<feldnames.length;i++) {
			if (feldVals[i] != null) {
				sql += " AND UCASE(" + MyDBI.delimitL(feldnames[i]) + ")='" + feldVals[i].toUpperCase() + "'";
				in += "," + MyDBI.delimitL(feldnames[i]);
				iv += ",'" + feldVals[i] + "'";
				if (feldnames[i].equalsIgnoreCase("Serial")) serialWhere = "UCASE(" + MyDBI.delimitL(feldnames[i]) + ")='" + feldVals[i].toUpperCase() + "'";
			}
		}
		int intId = 0;
		try {
			intId = Integer.parseInt(feldVals[feldVals.length-1]);
			int numIdsPresent = (mydbi != null ? mydbi.getRowCount("Station", " WHERE " + MyDBI.delimitL("ID") + "=" + intId) : DBKernel.getRowCount("Station", " WHERE " + MyDBI.delimitL("ID") + "=" + intId));
			if (numIdsPresent == 0) {
				in += "," + MyDBI.delimitL("ID");
				iv += "," + intId;
			}
		}
		catch (Exception e) {}

		ResultSet rs = (mydbi != null ? mydbi.getResultSet(sql, false) : DBKernel.getResultSet(sql, false));

		if (rs != null && rs.first()) {
			result = rs.getInt(1);
			rs.close();
		}

		if (result != null) {
			sql = "UPDATE " + MyDBI.delimitL("Station") + " SET " + MyDBI.delimitL("ImportSources") + "=CASEWHEN(INSTR(';" + miDbId + ";'," + MyDBI.delimitL("ImportSources") + ")=0,CONCAT(" + MyDBI.delimitL("ImportSources") + ", '" + miDbId + ";'), " + MyDBI.delimitL("ImportSources") + ") WHERE " + MyDBI.delimitL("ID") + "=" + result;
			if (mydbi != null) mydbi.sendRequest(sql, false, false);
			else DBKernel.sendRequest(sql, false);
		}
		else if (!iv.isEmpty()) {
			sql = "INSERT INTO " + MyDBI.delimitL("Station") + " (" + in + ") VALUES (" + iv + ")";
			@SuppressWarnings("resource")
			Connection conn = (mydbi != null ? mydbi.getConn() : DBKernel.getDBConnection());
			PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			//System.err.println(in + "\t" + iv);
			try {
				if (ps.executeUpdate() > 0) {
					result = (mydbi != null ? mydbi.getLastInsertedID(ps) : DBKernel.getLastInsertedID(ps));
					if (serialWhere.length() == 0) {
						sql = "UPDATE " + MyDBI.delimitL("Station") + " SET " + MyDBI.delimitL("Serial") + "=" + MyDBI.delimitL("ID") + " WHERE " + MyDBI.delimitL("ID") + "=" + result;
						if (mydbi != null) mydbi.sendRequest(sql, false, false);
						else DBKernel.sendRequest(sql, false);
						serialWhere = "UCASE(" + MyDBI.delimitL("Serial") + ")='" + result + "'";
					}
					int numSameSerials = (mydbi != null ? mydbi.getRowCount("Station", " WHERE " + serialWhere) : DBKernel.getRowCount("Station", " WHERE " + serialWhere));
					if (numSameSerials > 1) {
						sql = "UPDATE " + MyDBI.delimitL("Station") + " SET " + MyDBI.delimitL("Serial") + "=CONCAT(" + MyDBI.delimitL("Serial") + ",'_" + result + "') WHERE " + MyDBI.delimitL("ID") + "=" + result;
						if (mydbi != null) mydbi.sendRequest(sql, false, false);
						else DBKernel.sendRequest(sql, false);
					}
				}
			}
			catch (SQLException e) {
				if (e.getMessage().startsWith("integrity constraint violation")) throw new Exception("Station ID " + intId + " is already assigned");
				else throw e;
			}
		}

		return result;
	}
}
