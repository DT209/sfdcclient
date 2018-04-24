package com.dt209.sfdcclient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import okhttp3.HttpUrl;
import okhttp3.HttpUrl.Builder;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.ClassUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class SfdcClient {
    private final Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * This is the default path to use, there may be a newer version of the API, please log on and check
     * To determin your version, Click on the "gear Icon" [ Setup ] | In Quick Find, search for
     * "Company Information" | You will see the current edition in the "Organization Edition" field
     */
    public static final String SOAP_PATH = "/services/Soap/u/42.0"; // Current as of Spring 2018
    /**
     * This is the production only host to use
     */
    public static final String PROD_HOST = "login.salesforce.com";
    /**
     * This is the sandbox host to use (works with all sandboxes or NONE PRODUCTION environments)
     */
    public static final String SANDBOX_HOST = "login.salesforce.com";
    /**
     * This is the end of a domain that was enabled for your organization.
     * If your organization enabled ABC as the domain, it would be "abc.my.salesforce.com", this is
     * the last part of that host name
     */
    public static final String DOMAIN_END_OF_HOST = "my.salesforce.com";
    //https://login.salesforce.com/services/Soap/c/24.0/
    //https://test.salesforce.com/services/Soap/c/24.0/
    //https://MyDomain.my.salesforce.com/services/Soap/c/24.0/

    private static final MediaType MEDIA_TYPE = MediaType.parse("text/xml; charset=UTF-8");

    /**
     * NOTE THIS IS SHARED AMONGST INSTANCES OF THIS CLASS!
     */
    private static String sessionId;
    /**
     * NOTE THIS IS SHARED AMONGST INSTANCES OF THIS CLASS!
     */
    private static String serverUrl;

    private final String username;
    private final String password;
    private final HttpUrl loginUrl;
    private final String soapPath;
    private final String securityToken;

    /**
     *
     * @param host  Can be one of the constants above or one of your own making (allowing for proxying)
     * @param soapPath See above constant as a default
     * @param username Username with enabled API access
     * @param password Password for username with enabled API access
     */
    public SfdcClient(String host, String soapPath, String username, String password, String securityToken) {
        loginUrl = new Builder()
                .scheme("https")
                .host(host)
                .addPathSegments(soapPath)
                .build();
        this.username = username;
        this.password = password;
        this.securityToken = securityToken;
        this.soapPath = soapPath;
    }

    protected void login() throws IOException, ParserConfigurationException, SAXException {
        String loginXml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n"
                + "<env:Envelope xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"\n"
                + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "    xmlns:n1=\"urn:partner.soap.sforce.com\""
                + "    xmlns:env=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
                + "  <env:Body>\n"
                + "    <n1:login>\n"
                + "      <n1:username>" +this.username+ "</n1:username>\n"
                + "      <n1:password>" +this.password+this.securityToken+ "</n1:password>\n"
                + "    </n1:login>\n"
                + "  </env:Body>\n"
                + "</env:Envelope>";
        RequestBody body = RequestBody.create(MEDIA_TYPE, loginXml);

        Request request = new Request.Builder()
                .url(loginUrl)
                .addHeader("SOAPAction", "login")
                .post(body)
                .build();

        Response response = new OkHttpClient().newCall(request).execute();

        NodeList nList = getNodeList(response, "result");

        for (int temp = 0; temp < nList.getLength(); temp++) {
            Node nNode = nList.item(temp);
            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) nNode;
                if (Boolean.parseBoolean(getText(eElement, "passwordExpired"))){
                    throw new IOException("Password has expired for SalesForce User " + username + " on host " + loginUrl.host());
                }

                serverUrl = getText(eElement, "serverUrl");
                sessionId = getText(eElement, "sessionId");
            }
        }
        if (serverUrl == null || sessionId == null) {
            throw new IOException("Was not able to obtain Salesforce server URL nor Session ID from " + loginUrl.host() + " when trying to log in as " + username);
        }
        // Now logged in.
    }

    public List<Map<String, String>> selectFields(String objectName, Collection<String> fields, String condition) throws IOException {
        final StringJoiner joiner = new StringJoiner(",");
        fields.forEach(field -> {joiner.add(field);});
        String queryString = "SELECT " +joiner.toString()+ " FROM " +objectName+ " WHERE " + condition;
        return query(queryString);
    }

    public List<Map<String, String>> query(String queryString) throws IOException {
        String queryXml = "<n1:query><n1:queryString>" +queryString+ "</n1:queryString></n1:query>";
        List<Map<String, String>> result = new ArrayList<>(); // Keeps order of results
        NodeList nList = runRequest(queryXml, "records");
        for (int outer = 0; outer < nList.getLength(); outer++) {
            Node nNode = nList.item(outer);
            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) nNode;
                NodeList childNodes = eElement.getChildNodes();
                Map<String, String> row = new HashMap<>();
                for (int inner = 0; inner < childNodes.getLength(); inner++) {
                    Node childNode = childNodes.item(inner);
                    if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element childElement = (Element) childNode;
                        String key = childElement.getNodeName();
                        if (key.contains(":")) {
                            key = key.substring(key.indexOf(":")+1);
                        }
                        String value = childElement.getTextContent();
                        row.put(key, value);
                    }
                }
                result.add(row);
            }
        }
        return result;
    }

    public void update(final String sfdcObjectType, final Map<String,Object> sfdcObject) throws IOException {

        if (!sfdcObject.containsKey("id")) {
            throw new IOException("The Salesforce Object subitted does not have an \"id\" key. Please add one and submit it again");
        }

        Map<String,Object> map = new HashMap();
        map.putAll(sfdcObject);
        String id = map.get("id").toString();
        map.remove("id");

        StringBuilder objectFields = new StringBuilder();
        map.forEach((key, value) -> {
            Class<?> valueClass = value.getClass();
            if (String.class == valueClass || ClassUtils.isPrimitiveOrWrapper(valueClass)) {
                objectFields.append("<");
                objectFields.append(key);
                objectFields.append(">");
                objectFields.append(value);
                objectFields.append("</");
                objectFields.append(key);
                objectFields.append(">");
            }
        });

        String updateXml = "<n1:update>"
                + "<n1:sObjects>"
                + "     <n2:type>"
                + "     " + sfdcObjectType
                + "     </n2:type>"
                + "     <n2:id>"
                + "     " + id
                + "     </n2:id>"
                +       objectFields.toString()
                + "</n1:sObjects>"
                + "</n1:update>";

        NodeList result = runRequest(updateXml, "result");

        for (int outer = 0; outer < result.getLength(); outer++) {
            Node nNode = result.item(outer);
            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) nNode;
                String success = getText(eElement, "success");
                if (success == null || !Boolean.parseBoolean(success)) {
                    String message = getText(eElement, "message");
                    throw new IOException("Could not update Salesforce Object of type " + sfdcObjectType + " with values " + sfdcObject.toString() + ". Response " + message);
                }
            }
        }
    }


    protected NodeList runRequest(String request, String resultTagName) throws IOException {
        if (sessionId == null) {
            try {
                login();
            } catch (ParserConfigurationException | SAXException e) {
                throw new IOException("Failed to Login to SFDC " + e.getMessage(), e);
            }
        }
        String xml = getFullXml(request);

        RequestBody body = RequestBody.create(MEDIA_TYPE, xml);

        Request post = new Request.Builder()
                .url(serverUrl)
                .addHeader("SOAPAction", "login")
                .post(body)
                .build();

        Response response = new OkHttpClient().newCall(post).execute();
        try {
            return getNodeList(response, resultTagName);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Could not run request " + request +" with error message: " + e.getMessage(),e);
        }
    }

    protected String getFullXml(String request) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n"
                    + "<env:Envelope xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"\n"
                    + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                    + "    xmlns:env=\"http://schemas.xmlsoap.org/soap/envelope/\""
                    + "    xmlns:n1=\"urn:partner.soap.sforce.com\""
                    + "    xmlns:n2=\"urn:sobject.partner.soap.sforce.com\""
                    + ">\n"

                    + "  <env:Header>\n"
                    + "    <n1:SessionHeader>\n"
                    + "      <n1:sessionId>" +sessionId+ "</n1:sessionId>\n"
                    + "    </n1:SessionHeader>\n"
                    + "  </env:Header>\n"

                    + "  <env:Body>\n"
                    +       request
                    + "  </env:Body>\n"
                    + "</env:Envelope>";
    }

    private String getText(Element eElement, String serverUrl) {
        return eElement.getElementsByTagName(serverUrl).item(0).getTextContent();
    }

    private NodeList getNodeList(Response response, String tagToGet) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(response.body().byteStream());
        doc.getDocumentElement().normalize();

        return doc.getElementsByTagName(tagToGet);
    }

}
