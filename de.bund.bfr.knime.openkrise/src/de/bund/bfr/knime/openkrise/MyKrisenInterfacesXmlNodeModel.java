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
package de.bund.bfr.knime.openkrise;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipInputStream;

import javax.ws.rs.core.MediaType;
import javax.xml.soap.SOAPException;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.JerseyClient;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.glassfish.jersey.client.JerseyWebTarget;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.RowKey;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;

import com.google.common.io.Files;

import de.bund.bfr.knime.openkrise.db.imports.custom.nrw.in.Fall;
import de.bund.bfr.knime.openkrise.db.imports.custom.nrw.in.NRW_Importer;
import de.nrw.verbraucherschutz.idv.daten.Betrieb;
import de.nrw.verbraucherschutz.idv.daten.Kontrollpunktmeldung;
import de.nrw.verbraucherschutz.idv.daten.Lieferung;
import de.nrw.verbraucherschutz.idv.daten.Produkt;
import de.nrw.verbraucherschutz.idv.daten.Produktion;
import de.nrw.verbraucherschutz.idv.daten.Warenausgang;
import de.nrw.verbraucherschutz.idv.daten.Wareneingang;
import de.nrw.verbraucherschutz.idv.daten.WareneingangVerwendet;
import de.nrw.verbraucherschutz.idv.daten.Warenumfang;

/**
 * This is the model implementation of MyKrisenInterfaces.
 * 
 * 
 * @author draaw
 */
public class MyKrisenInterfacesXmlNodeModel extends NodeModel {

	private MyKrisenInterfacesXmlSettings set;

	/**
	 * Constructor for the node model.
	 */
	protected MyKrisenInterfacesXmlNodeModel() {
		super(0, 3);
		set = new MyKrisenInterfacesXmlSettings();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected BufferedDataTable[] execute(final BufferedDataTable[] inData, final ExecutionContext exec) throws Exception {
		return handleNRWXml(exec);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void reset() {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected DataTableSpec[] configure(final DataTableSpec[] inSpecs) throws InvalidSettingsException {
		return new DataTableSpec[] { null, null, null };
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveSettingsTo(final NodeSettingsWO settings) {
		set.saveSettings(settings);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
		set.loadSettings(settings);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void loadInternals(final File internDir, final ExecutionMonitor exec)
			throws IOException, CanceledExecutionException {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void saveInternals(final File internDir, final ExecutionMonitor exec)
			throws IOException, CanceledExecutionException {
	}

	private static void fillCell(DataTableSpec spec, DataCell[] cells, String columnname, DataCell value) {
		int index = spec.findColumnIndex(columnname);

		if (index >= 0) {
			cells[index] = value;
		}
	}

	private static void addSpec(Collection<DataColumnSpec> specs, String name, DataType type) {
		specs.add(new DataColumnSpecCreator(name, type).createSpec());
	}

	private static DataCell createCell(String s) {
		return s != null ? new StringCell(clean(s)) : DataType.getMissingCell();
	}

	private static DataCell createCell(Date d) {
		return d != null ? new StringCell(d.toString()) : DataType.getMissingCell();
	}

	private static DataCell createCell(Integer i) {
		return i != null ? new IntCell(i) : DataType.getMissingCell();
	}

	private static DataCell createCell(Double d) {
		return d != null ? new DoubleCell(d) : DataType.getMissingCell();
	}

	private static String clean(String s) {
		if (s == null || s.equalsIgnoreCase("null")) {
			return null;
		}

		return s.replace("\n", "|").replaceAll("\\p{C}", "").replace("\u00A0", "").replace("\t", " ").trim();
	}

	private BufferedDataTable[] handleNRWXml(final ExecutionContext exec) throws CanceledExecutionException, IOException, SOAPException {
		File tempDir = null;
		String xmlFolder = set.getXmlPath();
		if (set.isBusstop()) {
		    ClientConfig config = new ClientConfig();
		    config.register(MultiPartFeature.class);
		    config.property(ClientProperties.FOLLOW_REDIRECTS, true);
		    JerseyClient client = new JerseyClientBuilder().withConfig(config).build();
		    client.register(HttpAuthenticationFeature.basic(set.getUser(), set.getPass()));
		    JerseyWebTarget service = client.target(set.getServer());
		    InputStream stream = service.path("rest").path("items").path("files").request().accept(MediaType.APPLICATION_OCTET_STREAM).get(InputStream.class);
	        ZipInputStream zipIn = new ZipInputStream(stream);
	        
		    tempDir = Files.createTempDir();
	        UnzipUtility unzipper = new UnzipUtility();
            unzipper.unzip(zipIn, tempDir.getAbsolutePath());
            zipIn.close();
            xmlFolder = tempDir.getAbsolutePath();
		}
		String caseNumber = set.getCaseNumber();
		if (caseNumber != null && caseNumber.trim().isEmpty()) caseNumber = null;
		NRW_Importer nrw = new NRW_Importer();
		String lastFallNummer = nrw.doImport(xmlFolder, caseNumber);
		if (caseNumber == null) caseNumber = lastFallNummer;
		this.pushFlowVariableString("Fallnummer", caseNumber);
		if (caseNumber != null) {
			Fall fall = nrw.getFaelle().get(caseNumber);
			this.pushFlowVariableString("Fallbezeichnung", fall.getFallBezeichnung());
			Set<String> s = fall.getAuftragsnummern();
			int i=0;
			for (String an : s) {
				this.pushFlowVariableString("Auftragsnummer_" + i, an);
				i++;
			}
			//fall.getCommHeader();
		}
		if (tempDir != null) {
			deleteDir(tempDir);
		}
		
		// Station specs
		List<DataColumnSpec> columns = new ArrayList<>();
		addSpec(columns, TracingColumns.ID, StringCell.TYPE);
		addSpec(columns, TracingColumns.STATION_ID, StringCell.TYPE);
		addSpec(columns, TracingColumns.STATION_NAME, StringCell.TYPE);
		addSpec(columns, TracingColumns.STATION_STREET, StringCell.TYPE);
		addSpec(columns, TracingColumns.STATION_HOUSENO, IntCell.TYPE);
		addSpec(columns, TracingColumns.STATION_ZIP, StringCell.TYPE);
		addSpec(columns, TracingColumns.STATION_CITY, StringCell.TYPE);
		//addSpec(columns, TracingColumns.STATION_STATE, StringCell.TYPE);
		addSpec(columns, TracingColumns.STATION_COUNTRY, StringCell.TYPE);
		
		addSpec(columns, "Bemerkung", StringCell.TYPE);
		addSpec(columns, "EgZulassungsnummer", StringCell.TYPE);
		addSpec(columns, "Lat", DoubleCell.TYPE);
		addSpec(columns, "Lon", DoubleCell.TYPE);

		DataTableSpec specS = new DataTableSpec(columns.toArray(new DataColumnSpec[0]));
		BufferedDataContainer stationContainer = exec.createDataContainer(specS);
		DataCell[] cells = new DataCell[specS.getNumColumns()];
		
		if (caseNumber != null) {
			Collection<Betrieb> betriebe = nrw.getFaelle().get(caseNumber).getBetriebe();
			if (betriebe != null) {
				// Station fill cells
				long index = 0;
				for (Betrieb b : betriebe) {
					if (b != null) {
						fillCell(specS, cells, TracingColumns.ID, createCell(b.getBetriebsnummer()));
						fillCell(specS, cells, TracingColumns.STATION_ID, createCell(b.getBetriebsnummer()));
						fillCell(specS, cells, TracingColumns.STATION_NAME, createCell(b.getBetriebsname()));
						fillCell(specS, cells, TracingColumns.STATION_STREET, createCell(b.getStrasse()));
						fillCell(specS, cells, TracingColumns.STATION_HOUSENO, createCell(b.getHausnummer()));
						fillCell(specS, cells, TracingColumns.STATION_ZIP, createCell(b.getPlz()));
						fillCell(specS, cells, TracingColumns.STATION_CITY, createCell(b.getOrt()));
						fillCell(specS, cells, TracingColumns.STATION_COUNTRY, createCell(b.getLand()));
						//fillCell(specS, cells, TracingColumns.STATION_COUNTRY, createCell("DE"));
						
						fillCell(specS, cells, "Bemerkung", createCell(b.getBemerkung()));
						fillCell(specS, cells, "EgZulassungsnummer", createCell(b.getEgZulassungsnummer()));
						fillCell(specS, cells, "Lat", createCell(b.getGeoPositionLatitude() == null ? null : b.getGeoPositionLatitude().doubleValue()));
						fillCell(specS, cells, "Lon", createCell(b.getGeoPositionLongitude() == null ? null : b.getGeoPositionLongitude().doubleValue()));
						
						stationContainer.addRowToTable(new DefaultRow(RowKey.createRowKey(index++), cells));				
					}
				}
			}
		}
		exec.checkCanceled();
		stationContainer.close();

		// Delivery specs
		columns = new ArrayList<>();
		//addSpec(columns, TracingColumns.DELIVERY_ID, StringCell.TYPE);
		addSpec(columns, TracingColumns.ID, StringCell.TYPE);
		addSpec(columns, TracingColumns.FROM, StringCell.TYPE);
		addSpec(columns, TracingColumns.TO, StringCell.TYPE);
		addSpec(columns, TracingColumns.NAME, StringCell.TYPE);
		addSpec(columns, TracingColumns.PRODUCT_NUMBER, StringCell.TYPE);
		addSpec(columns, "EAN", StringCell.TYPE);
		addSpec(columns, TracingColumns.LOT_NUMBER, StringCell.TYPE);
		addSpec(columns, "Chargennummer", StringCell.TYPE);
		addSpec(columns, "Bezeichnung", StringCell.TYPE);
		addSpec(columns, TracingColumns.DELIVERY_NUM_PU, DoubleCell.TYPE);
		addSpec(columns, TracingColumns.DELIVERY_TYPE_PU, StringCell.TYPE);
		addSpec(columns, "AnzahlGebinde", IntCell.TYPE);
		addSpec(columns, "Gebinde", StringCell.TYPE);
		addSpec(columns, TracingColumns.DELIVERY_DEPARTURE, StringCell.TYPE);
		addSpec(columns, "Lieferscheinnummer", StringCell.TYPE);

		DataTableSpec specD = new DataTableSpec(columns.toArray(new DataColumnSpec[0]));
		BufferedDataContainer deliveryContainer = exec.createDataContainer(specD);
		cells = new DataCell[specD.getNumColumns()];
		
		HashMap<String, List<String>> identicalLieferungen = new HashMap<>();
		List<String[]> linkList = new ArrayList<>();
		if (caseNumber != null) {
			Collection<Kontrollpunktmeldung> kpms = nrw.getFaelle().get(caseNumber).getKpms();
			if (kpms != null) {
				// Delivery fill cells
				long index = 0;
				HashMap<String, String> weIDs = new HashMap<>();
				for (Kontrollpunktmeldung kpm : kpms) {
					if (kpm.getWareneingaenge() != null) {
						for (Wareneingang we : kpm.getWareneingaenge().getWareneingang()) {
							for (Betrieb b : we.getBetrieb()) {
								if (b.getTyp().equals("LIEFERANT") && b.getBetriebsnummer() != null && kpm.getBetrieb().getBetriebsnummer() != null) {							
									String deliveryKey = fillDeliveries(specD, cells, "D" + index, b.getBetriebsnummer(), kpm.getBetrieb().getBetriebsnummer(), we.getProdukt(), we.getWarenumfang(), we.getLieferung());
									if (!identicalLieferungen.containsKey(deliveryKey)) {
										List<String> l = new ArrayList<String>();
										l.add("D" + index);								
										identicalLieferungen.put(deliveryKey, l);
										deliveryContainer.addRowToTable(new DefaultRow(RowKey.createRowKey(index), cells));
									}
									else {
										List<String> l = identicalLieferungen.get(deliveryKey);
										l.add("D" + index);								
									}
									weIDs.put(we.getId(), "D" + index);
									index++;
								}
							}
						}									
					}
					HashMap<String, Produktion> prods = new HashMap<>();
					if (kpm.getProduktionen() != null) {
						for (Produktion p : kpm.getProduktionen().getProduktion()) {
							if (p.getWareneingaengeVerwendet() != null) prods.put(p.getId(), p);
						}
					}
					if (kpm.getWarenausgaenge() != null) {
						for (Warenausgang wa : kpm.getWarenausgaenge().getWarenausgang()) {
							for (Betrieb b : wa.getBetrieb()) {
								if (b.getTyp().equals("KUNDE") && b.getBetriebsnummer() != null && kpm.getBetrieb().getBetriebsnummer() != null) {
									String deliveryKey = fillDeliveries(specD, cells, "D" + index, kpm.getBetrieb().getBetriebsnummer(), b.getBetriebsnummer(), wa.getProdukt(), wa.getWarenumfang(), wa.getLieferung());
									if (!identicalLieferungen.containsKey(deliveryKey)) {
										List<String> l = new ArrayList<String>();
										l.add("D" + index);								
										identicalLieferungen.put(deliveryKey, l);
										deliveryContainer.addRowToTable(new DefaultRow(RowKey.createRowKey(index), cells));
									}
									else {
										List<String> l = identicalLieferungen.get(deliveryKey);
										l.add("D" + index);								
									}
									if (prods.containsKey(wa.getProduktionId())) {
										Produktion p = prods.get(wa.getProduktionId());
										for (WareneingangVerwendet wev : p.getWareneingaengeVerwendet().getWareneingangVerwendet()) {
											if (weIDs.containsKey(wev.getWareneingangId())) {
												String[] link = new String[2];
												link[0] = weIDs.get(wev.getWareneingangId());
												link[1] = "D" + index;
												linkList.add(link);
											}
										}
									}
									index++;
								}
							}
						}	
					}
				}
			}
		}
		deliveryContainer.close();
		
		DataTableSpec specL = new DataTableSpec(new DataColumnSpecCreator(TracingColumns.ID, StringCell.TYPE).createSpec(),
				new DataColumnSpecCreator(TracingColumns.NEXT, StringCell.TYPE).createSpec());
		BufferedDataContainer linkContainer = exec.createDataContainer(specL);
		DataCell[] cellsL = new DataCell[specL.getNumColumns()];

		HashMap<String, String> mapLieferung = new HashMap<>();
		for (String key : identicalLieferungen.keySet()) {
			List<String> l = identicalLieferungen.get(key);
			for (int i=0;i<l.size();i++) {
				mapLieferung.put(l.get(i), l.get(0));
				if (i > 0) System.err.println("removed delivery: " + l.get(i));
			}
		}
		// Link fill cells
		for (String[] link : linkList) {
			fillCell(specL, cellsL, TracingColumns.ID, createCell(mapLieferung.get(link[0])));
			fillCell(specL, cellsL, TracingColumns.NEXT, createCell(mapLieferung.get(link[1])));
			linkContainer.addRowToTable(new DefaultRow(RowKey.createRowKey(linkContainer.size()), cellsL));
		}
		linkContainer.close();
		
		return new BufferedDataTable[] { stationContainer.getTable(), deliveryContainer.getTable(), linkContainer.getTable() };		
	}
	
	private String fillDeliveries(DataTableSpec specD, DataCell[] cells, String deliveryId, String from, String to, Produkt p, Warenumfang wu, Lieferung l) {
		fillCell(specD, cells, TracingColumns.ID, createCell(deliveryId));
		fillCell(specD, cells, TracingColumns.FROM, createCell(from));
		fillCell(specD, cells, TracingColumns.TO, createCell(to));

		String los = p == null ? null : p.getLosNummer() == null ? p.getChargenNummer() : p.getLosNummer();
		fillCell(specD, cells, TracingColumns.NAME, createCell(p == null ? null : p.getHandelsname()));
		fillCell(specD, cells, TracingColumns.PRODUCT_NUMBER, createCell(p == null ? null : p.getArtikelnummer()));
		fillCell(specD, cells, "EAN", createCell(p == null ? null : p.getEan()));
		fillCell(specD, cells, TracingColumns.LOT_NUMBER, createCell(los));
		fillCell(specD, cells, "Chargennummer", createCell(p == null ? null : p.getChargenNummer()));
		fillCell(specD, cells, "Bezeichnung", createCell(p == null ? null : p.getProduktBezeichnung()));

		fillCell(specD, cells, TracingColumns.DELIVERY_NUM_PU, createCell(wu == null || wu.getMengeEinheit() == null ? null : wu.getMengeEinheit().getMenge().doubleValue()));
		fillCell(specD, cells, TracingColumns.DELIVERY_TYPE_PU, createCell(wu == null || wu.getMengeEinheit() == null ? null : wu.getMengeEinheit().getEinheit()));
		fillCell(specD, cells, "AnzahlGebinde", createCell(wu == null || wu.getAnzahlGebinde() == null ? null : wu.getAnzahlGebinde().getAnzahl()));
		fillCell(specD, cells, "Gebinde", createCell(wu == null || wu.getAnzahlGebinde() == null ? null : wu.getAnzahlGebinde().getGebinde()));

		Date ld = l == null || l.getAusgeliefertAm() == null ? null : l.getAusgeliefertAm().toGregorianCalendar().getTime();
		fillCell(specD, cells, TracingColumns.DELIVERY_DEPARTURE, createCell(ld));
		fillCell(specD, cells, "Lieferscheinnummer", createCell(l == null ? null : l.getLieferscheinNummer()));
		
		return from + ";;;" + to + ";;;" + los + ";;;" + ld;
	}	
	private boolean deleteDir(File folder) {
	    File[] contents = folder.listFiles();
	    if (contents != null) {
	        for(int i = contents.length-1;i>=0;i--) {
	        	contents[i].delete();
	        }
	    }
	    return folder.delete();
	}		
}
