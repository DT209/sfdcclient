package com.dt209.sfdcclient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SfdcClientTest {

    private SfdcClient sfdcClient;

    @Before
    public void before() {
        String username = System.getenv().get("u");
        assertNotNull("Username must be passed in as an environment variable to this test. Make sure that the user used has ability to Create/Update/Delete Salesforce Account objects. Set it as -Du=MyUserName. Env has: " + System.getenv(), username);
        String password = System.getenv().get("p");
        assertNotNull("Password must be passed in as an environment variable to this test. Make sure that the user used has ability to Create/Update/Delete Salesforce Account objects. Set it as -Dp=MyPassWord. Env has: " + System.getenv(), password);
        String securityToken = System.getenv().get("t");
        assertNotNull("securityToken must be passed in as an environment variable to this test. Make sure that the user used has ability to Create/Update/Delete Salesforce Account objects. Set it as -Dt=ToKen. Env has: " + System.getenv(), securityToken);

        sfdcClient = new SfdcClient(SfdcClient.PROD_HOST, SfdcClient.SOAP_PATH, username, password, securityToken);

        ConsoleHandler handler = new ConsoleHandler();
        // PUBLISH this level
        handler.setLevel(Level.ALL);
        Logger.getGlobal().setLevel(Level.ALL);
        Logger.getGlobal().addHandler(handler);
        Logger.getAnonymousLogger().addHandler(handler);
        Logger logger = Logger.getLogger(sfdcClient.getClass().getName());
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
    }

    /**
     * This test renames 5 random SalesForce Account objects, then puts the name back.
     */
    @Test
    public void querySelectByFieldUpdateTest() throws IOException {
        List<Map<String, String>> queryResult = sfdcClient.query("SELECT Id, Name FROM Account");
        assertFalse(queryResult.isEmpty());

        int size = queryResult.size();
        for (int i=0; i < size && i < 5; i++) {
            Map<String,String> row = queryResult.get(i);

            String id = row.get("Id");

            String originalName = row.get("Name");

            assertNotNull(row.toString(), id);
            assertNotNull(row.toString(), originalName);

            String tempName = Long.toString(System.currentTimeMillis());

            Map<String, Object> sfdcObject = new HashMap<>();
            sfdcObject.put("id", id);
            sfdcObject.put("Name", tempName);

            sfdcClient.update("Account", sfdcObject);

            Collection<String> fields = new ArrayList<>();
            fields.add("Id");
            fields.add("Name");
            List<Map<String, String>> accounts = sfdcClient.selectFields("Account", fields, "Id = '" + id + "'");
            assertEquals(accounts.toString(), 1, accounts.size());
            assertEquals(accounts.get(0).toString(), tempName, accounts.get(0).get("Name"));

            sfdcObject.put("id", id);
            sfdcObject.put("Name", originalName);
            sfdcClient.update("account", sfdcObject);

            accounts = sfdcClient.selectFields("Account", fields, "Id = '" + id + "'");
            assertEquals(accounts.toString(), 1, accounts.size());
            assertEquals(accounts.get(0).toString(), originalName, accounts.get(0).get("Name"));
        }
    }

    /**
     * This test creates a random SalesForce Account object, then deletes it.
     */
    @Test
    public void insertDeleteTest() throws IOException {
        String tempName = Long.toString(System.currentTimeMillis());
        Map<String,Object> sfdcObject = new HashMap<>();
        sfdcObject.put("Name", tempName);
        String id = sfdcClient.upsert("id", "Account", sfdcObject);
        assertTrue(sfdcClient.delete(id));
    }
}

