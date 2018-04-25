package com.dt209.sfdcclient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TimeZone;
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
import org.apache.commons.text.StringEscapeUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class SfdcClient {
    private static final String SFDC_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZZZ";

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
    public static final String SANDBOX_HOST = "test.salesforce.com";
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
        String responseBody = response.body().string();
        logger.fine("Login response body: " + responseBody);
        NodeList nList = getNodeList(new ByteArrayInputStream(responseBody.getBytes()), "result", loginXml);

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

    /**
     * @param objectName
     * @param fields
     * @param condition The WHERE clause without the word WHERE. Please escape the clause if needed. (Exapmle code: StringEscapeUtils.escapeXml10(fieldValueToEscape)))
     * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.soql_sosl.meta/soql_sosl/sforce_api_calls_soql_select_examples.htm#!">https://developer.salesforce.com/docs/atlas.en-us.soql_sosl.meta/soql_sosl/sforce_api_calls_soql_select_examples.htm#!</a>
     */
    public List<Map<String, String>> selectFields(String objectName, Collection<String> fields, String condition) throws IOException {
        final StringJoiner joiner = new StringJoiner(",");
        fields.forEach(field -> joiner.add(field));
        String queryString = "SELECT " +joiner.toString()+ " FROM " +objectName+ " WHERE " + condition;
        return query(queryString);
    }

    /**
     * @param queryString Please escape anything if needed. (Exapmle code: StringEscapeUtils.escapeXml10(fieldValueToEscape)))
     * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.soql_sosl.meta/soql_sosl/sforce_api_calls_soql_select_examples.htm#!">https://developer.salesforce.com/docs/atlas.en-us.soql_sosl.meta/soql_sosl/sforce_api_calls_soql_select_examples.htm#!</a>
     */
    public List<Map<String, String>> query(String queryString) throws IOException {
        String xml = "<n1:query><n1:queryString>" +queryString+ "</n1:queryString></n1:query>";
        List<Map<String, String>> result = new ArrayList<>(); // Keeps order of results

        NodeList nList = runRequest(xml, "records");

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
            throw new IOException("The Salesforce Object does not have an \"id\" key. Please add one and submit it again");
        }

        Map<String, Object> map = new HashMap<>(sfdcObject);
        String id = map.get("id").toString();
        map.remove("id");

        StringBuilder objectFields = makeObjectFieldXml(map);

        String xml = "<n1:update>"
                + "<n1:sObjects>"
                + "     <n2:type>"+ sfdcObjectType+ "</n2:type>"
                + "     <n2:id>"+ id +"</n2:id>"
                +       objectFields
                + "</n1:sObjects>"
                + "</n1:update>";

        runRequest(xml, "Body");
    }



    /**
     * Insert or Update the specified sfdcObject into the Salesforce.
     * @param externalIdFieldName Used to identify the primary key field for the object, can be any field marked unique for the Salesforce Object Type
     * @param sfdcObjectType Salesforce Type
     * @param sfdcObject an object with field names that exactly match those in Sales Force and with values that are in a valid format for the Saleseforce object that is being upserted into.
     * @return ID of object updated or inserted.
     */
    public String upsert(String externalIdFieldName, String sfdcObjectType, Map<String,Object> sfdcObject)
            throws IOException {
        StringBuilder objectFields = makeObjectFieldXml(sfdcObject);

        String xml = "<n1:upsert>"
                +"<n1:externalIDFieldName>"+ externalIdFieldName+ "</n1:externalIDFieldName>"
                + "<n1:sObjects>"
                + "     <n2:type>"+ sfdcObjectType+ "</n2:type>"
                +       objectFields
                + "</n1:sObjects>"
                + "</n1:upsert>";

        return runRequest(xml, "id").item(0).getTextContent();
    }

    /**
     * @param id The ID of the sfdcObject to be delted
     */
    public boolean delete(String id) throws IOException {
        String xml = "<n1:delete><n1:ids>"+ id+ "</n1:ids></n1:delete>";
         return Boolean.parseBoolean(runRequest(xml, "success").item(0).getTextContent());
    }

    /**
     * @param date
     * @return Date formatted per Salesforce rules
     * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.soql_sosl.meta/soql_sosl/sforce_api_calls_soql_select_dateformats.htm">https://developer.salesforce.com/docs/atlas.en-us.soql_sosl.meta/soql_sosl/sforce_api_calls_soql_select_dateformats.htm</a>
     */
    public String toSfdcFormat(Date date) {
        if (date == null) return null;

        TimeZone timeZone = TimeZone.getTimeZone("UTC");
        DateFormat dateFormat = new SimpleDateFormat(SFDC_DATE_TIME_FORMAT);
        dateFormat.setTimeZone(timeZone);

        return dateFormat.format(date);
    }

    /**
     * @param date in "yyyy-MM-dd'T'HH:mm:ssZZZ" format
     * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.soql_sosl.meta/soql_sosl/sforce_api_calls_soql_select_dateformats.htm">https://developer.salesforce.com/docs/atlas.en-us.soql_sosl.meta/soql_sosl/sforce_api_calls_soql_select_dateformats.htm</a>
     */
    public static Date fromSfdcFormat(String date) throws IOException {
        if (date == null) return null;

        DateFormat dateFormat = new SimpleDateFormat(SFDC_DATE_TIME_FORMAT);

        try {
            return dateFormat.parse(date);
        } catch (ParseException e) {
            throw new IOException("Could not parse \"" +date+ "\" using the following format: " + SFDC_DATE_TIME_FORMAT);
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

        String responseBody = response.body().string();
        logger.fine("Request response body: " + responseBody);
        try {
            return getNodeList(new ByteArrayInputStream(responseBody.getBytes()), resultTagName, request);
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

    private String getText(Element eElement, String tagName) {
        return eElement.getElementsByTagName(tagName).item(0).getTextContent();
    }

    private NodeList getNodeList(InputStream inputStream, String tagToGet, String request) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(inputStream);
        doc.getDocumentElement().normalize();

        NodeList faultStrings = doc.getElementsByTagName("faultstring");
        if (faultStrings.getLength() > 0) {
            throw new IOException("Call to Salesforce failed with message: " + faultStrings.item(0).getTextContent() + " with request: " + request);
        }

        return doc.getElementsByTagName(tagToGet);
    }

    private StringBuilder makeObjectFieldXml(Map<String, Object> map) {
        StringBuilder objectFields = new StringBuilder();
        map.forEach((key, value) -> {
            Class<?> valueClass = value.getClass();
            if (String.class == valueClass || ClassUtils.isPrimitiveOrWrapper(valueClass)) {
                objectFields.append("<");
                objectFields.append(key);
                objectFields.append(">");
                objectFields.append(StringEscapeUtils.escapeXml10(value.toString()));
                objectFields.append("</");
                objectFields.append(key);
                objectFields.append(">");
            }
        });
        return objectFields;
    }

}
