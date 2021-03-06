package de.bund.bfr.knime.openkrise.out;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.glassfish.jersey.client.JerseyClient;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.glassfish.jersey.client.JerseyWebTarget;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.image.ImageContent;
import org.knime.core.data.image.ImageValue;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.image.ImagePortObject;
import org.knime.core.node.workflow.FlowVariable;

import de.bund.bfr.knime.IO;
import de.bund.bfr.knime.openkrise.TracingColumns;
import de.bund.bfr.knime.openkrise.db.imports.custom.nrw.out.NRW_Exporter;
import de.nrw.verbraucherschutz.idv.daten.Analyseergebnis;
import de.nrw.verbraucherschutz.idv.daten.Bewertung;
import de.nrw.verbraucherschutz.idv.daten.Content;
import de.nrw.verbraucherschutz.idv.daten.Dokument;
import de.nrw.verbraucherschutz.idv.daten.KatalogWert;
import de.nrw.verbraucherschutz.idv.daten.Kontrollpunktbewertung;
import de.nrw.verbraucherschutz.idv.daten.Meldung;
import de.nrw.verbraucherschutz.idv.daten.Metadaten;
import de.nrw.verbraucherschutz.idv.daten.Referenz;
import de.nrw.verbraucherschutz.idv.daten.Warenbewegungsbewertung;

import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;


/**
 * This is the model implementation of TracingXmlOut.
 * 
 *
 * @author BfR
 */
public class TracingXmlOutNodeModel extends NodeModel {

	private TracingXmlOutNodeSettings set;

    /**
     * Constructor for the node model.
     */
    protected TracingXmlOutNodeModel() {   
		super(new PortType[] {BufferedDataTable.TYPE, BufferedDataTable.TYPE, ImagePortObject.TYPE},
				new PortType[] {});
		set = new TracingXmlOutNodeSettings();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BufferedDataTable[] execute(final PortObject[] inObjects,
            final ExecutionContext exec) throws Exception {
		BufferedDataTable nodeTable = (BufferedDataTable) inObjects[0]; // Stations
		//BufferedDataTable edgeTable = (BufferedDataTable) inObjects[1]; // Deliveries
		ImagePortObject imageObj = (ImagePortObject) inObjects[2];
		DataCell imageCellDC = imageObj.toDataCell();

	    if (!(imageCellDC instanceof ImageValue)) {
	        throw new InvalidSettingsException("Image object does not produce"
	                + " valid image object but "
	                + imageCellDC.getClass().getName());
	    }

	    ImageValue v = (ImageValue) imageCellDC;
	    ImageContent m_content = v.getImageContent();
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    m_content.save(baos);
	    baos.close();

		Map<String, FlowVariable> fvm = this.getAvailableInputFlowVariables();
		Analyseergebnis ae = new Analyseergebnis();
		
		// Report hierrein
		Dokument doc = new Dokument();
		Content content = new Content();
		content.setContentType("image/png");//"application/pdf");
		content.setValue(baos.toByteArray());
		doc.setContent(content);
		ae.getDokument().add(doc);
		Metadaten md = new Metadaten();
		String auftragsNummer = "";
		for (int i=0;;i++) {
			if (!fvm.containsKey("Auftragsnummer_" + i)) break;
			FlowVariable fv = fvm.get("Auftragsnummer_" + i);
			Referenz r = new Referenz();
			auftragsNummer = fv.getStringValue(); // "2016-93"
			r.setKey("AUFTRAGNR"); r.setValue(auftragsNummer);
			md.getReferenzen().add(r);
		}
		md.setAutor("BfR");
		md.setDokId(null);
		md.setDokumentName("Analyse Auftrag Nr. " + auftragsNummer + ".png");
		md.setBeschreibung("Analyseergebnis des BfRs für den Auftrag " + auftragsNummer);
		md.setFormat(".png"); // .pdf
		md.setKategorie("Analyseergebnis");
		md.setLokation("");
		md.setMimeType("image/png");//"application/pdf");
		md.setOrganisation("BfR");
		md.setTitel("Analyse Ergebnis Auftrag Nr. " + auftragsNummer);
		md.setVersion(getReadableDate()); // "1.0", hier sollte hochgezählt werden
		md.setPubliziertAm(getDate());
		md.setUploadAm(getDate());
		doc.setMetadaten(md);
		
		ae.setMeldung(getMeldung(fvm, auftragsNummer));

		// Scores hierrein
		Kontrollpunktbewertung kpb = new Kontrollpunktbewertung(); 
		kpb.setNummer(auftragsNummer);
		int idIndex = nodeTable.getSpec().findColumnIndex(TracingColumns.STATION_ID);
		int nameIndex = nodeTable.getSpec().findColumnIndex(TracingColumns.STATION_NAME);
		int tobIndex = nodeTable.getSpec().findColumnIndex(TracingColumns.STATION_TOB);
		int scoreIndex = nodeTable.getSpec().findColumnIndex(TracingColumns.SCORE);
		for (DataRow row : nodeTable) {
			Double score = scoreIndex < 0 ? 0.0 : IO.getDouble(row.getCell(scoreIndex));
			String name = nameIndex < 0 ? "" : IO.getCleanString(row.getCell(nameIndex));
			String id = idIndex < 0 ? "" : IO.getCleanString(row.getCell(idIndex));
			String tob = tobIndex < 0 ? "" : IO.getCleanString(row.getCell(tobIndex));
			Warenbewegungsbewertung wbb = new Warenbewegungsbewertung();
			wbb.setValue(new BigDecimal(score));
			wbb.setBetrieb(name);
			wbb.setId(id);
			wbb.setTyp(tob);
			kpb.getWarenbewegungsbewertung().add(wbb);
		}		
		Bewertung b = new Bewertung();
		ae.setBewertung(b);
		b.setKontrollpunktbewertung(kpb);
		NRW_Exporter e = new NRW_Exporter();
		ByteArrayOutputStream soap = e.doExport(ae, true);
		if (soap != null) {
		    File tempFile = File.createTempFile("bfr_report", ".soap");
		    FileOutputStream fos = new FileOutputStream(tempFile); 
			try {
			    soap.writeTo(fos);
			} catch(IOException ioe) {
				this.setWarningMessage(ioe.getMessage());
			    ioe.printStackTrace();
			} finally {
			    fos.close();
			}
			upload(tempFile);
		}
		else {
			this.setWarningMessage("soap is null");
		}

		return null;
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
    protected DataTableSpec[] configure(final PortObjectSpec[] inSpecs)
            throws InvalidSettingsException {
        return null;
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
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
		set.loadSettings(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File internDir,
            final ExecutionMonitor exec) throws IOException,
            CanceledExecutionException {
    }
    
    private Meldung getMeldung(Map<String, FlowVariable> fvm, String auftragsNummer) throws DatatypeConfigurationException {		
    	Meldung meldung = new Meldung();
		FlowVariable fv = fvm.get("Fallbezeichnung");
    	meldung.setFallBezeichnung(fv == null ? null : fv.getStringValue());
		fv = fvm.get("Fallnummer");
    	meldung.setFallNummer(fv == null ? null : fv.getStringValue());
    	meldung.setNummer(auftragsNummer);
    	meldung.setStatus("GUELTIG");
    	
    	KatalogWert kw = new KatalogWert();
    	kw.setCode("001");
    	kw.setKatalog("AMTSKENNUNG");
    	kw.setScope("BUND");
    	kw.setValue("Bundesinstitut für Risikobewertung");
    	kw.setVersion("1.0");
    	kw.setVerz("BFR");
    	meldung.setMeldendeBehoerde(kw);
    	meldung.setMeldungVom(getDate());
    	return meldung;
    }
    private XMLGregorianCalendar getDate() throws DatatypeConfigurationException {
    	GregorianCalendar c = new GregorianCalendar();
    	c.setTimeInMillis(System.currentTimeMillis());
    	XMLGregorianCalendar date2 = DatatypeFactory.newInstance().newXMLGregorianCalendar(c);
    	return date2;
    }
    private String getReadableDate() {
    	SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    	Date dt = new Date();
    	String S = sdf.format(dt);
    	return S;
    }

	private void upload(File file) throws Exception {
	    JerseyClient client = new JerseyClientBuilder()
	    		.register(HttpAuthenticationFeature.basic(set.getUser(), set.getPass()))
	    		.register(MultiPartFeature.class)
	    		.build();
	    JerseyWebTarget t = client.target(UriBuilder.fromUri(set.getServer()).build()).path("rest").path("items").path("upload");

	    FileDataBodyPart filePart = new FileDataBodyPart("file", file);
	    filePart.setContentDisposition(FormDataContentDisposition.name("file").fileName("report.soap").build()); // file.getName()

	    FormDataMultiPart formDataMultiPart = new FormDataMultiPart();
	    MultiPart multipartEntity = formDataMultiPart.field("comment", "Analysis from BfR").bodyPart(filePart);

	    Response response = t.request().post(Entity.entity(multipartEntity, MediaType.MULTIPART_FORM_DATA));
	    System.out.println(response.getStatus() + " \n" + response.readEntity(String.class));

	    response.close();
	    formDataMultiPart.close();
	    multipartEntity.close();
	}
	
}

