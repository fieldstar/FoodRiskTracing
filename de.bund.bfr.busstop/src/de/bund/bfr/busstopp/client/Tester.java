package de.bund.bfr.busstopp.client;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class Tester {
  public static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {

	  File file = new File("userdata.xml");
	  DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
	  DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
	  Document document = documentBuilder.parse(file);
	  String usr = document.getElementsByTagName("user").item(0).getTextContent();
	  String pwd = document.getElementsByTagName("password").item(0).getTextContent();	  
	  
    ClientConfig config = new ClientConfig();
    config.register(MultiPartFeature.class);
    Client client = ClientBuilder.newClient(config);
    client.register(HttpAuthenticationFeature.digest(usr, pwd));
    WebTarget service = client.target(getBaseURI());
    
    // Get the Todos
    System.out.println(service.path("rest").path("items").request().accept(MediaType.TEXT_XML).get(String.class));

    // Get XML for application
    System.out.println(service.path("rest").path("items").request().accept(MediaType.APPLICATION_XML).get(String.class));

    //Delete ItemLoader with id 1
    Response response = service.path("rest").path("items").path("1459283002443").request().delete();
    System.out.println("Form response " + response.getStatus() + "\n" + response.readEntity(String.class));

    //Create a ItemLoader
    upload(service, "C:/Users/Armin/Desktop/Pressemitteilung.docx");

  }
  private static void upload(WebTarget service, String fileName) throws IOException {
	    final FileDataBodyPart filePart = new FileDataBodyPart("file", new File(fileName));
	    FormDataMultiPart formDataMultiPart = new FormDataMultiPart();
	    final FormDataMultiPart multipart = (FormDataMultiPart) formDataMultiPart.field("foo", "bar").bodyPart(filePart);
	      
	    final Response response = service.path("rest").path("items").path("upload").request().post(Entity.entity(multipart, multipart.getMediaType()));
	     
	    System.out.println("Form response " + response.getStatus() + "\n" + response.readEntity(String.class));
	     
	    formDataMultiPart.close();
	    multipart.close();	    
	}
  private static URI getBaseURI() {
    return UriBuilder.fromUri("http://localhost:8080/de.bund.bfr.busstop").build();
  }
} 