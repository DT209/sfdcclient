package com.dt209.sfdcclient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class SfdcClientTest {

    @Test
    public void happyPathTest() throws IOException {
        String username = System.getenv().get("u");
        assertNotNull("Username must be passed in as an environment variable to this test. Set it as -Du=MyUserName. Env has: " + System.getenv(), username);
        String password = System.getenv().get("p");
        assertNotNull("Password must be passed in as an environment variable to this test. Set it as -Dp=MyPassWord. Env has: " + System.getenv(), password);
        String securityToken = System.getenv().get("t");
        assertNotNull("securityToken must be passed in as an environment variable to this test. Set it as -Dt=ToKen. Env has: " + System.getenv(), securityToken);

        SfdcClient sfdcClient = new SfdcClient(SfdcClient.PROD_HOST, SfdcClient.SOAP_PATH, username, password, securityToken);

        List<Map<String, String>> queryResult = sfdcClient.query("SELECT Id, Name FROM Account");
        assertFalse(queryResult.isEmpty());

        for(Map<String,String> row: queryResult) {
            String id = row.get("Id");

            String originalName = row.get("Name");

            assertNotNull(row.toString(), id);
            assertNotNull(row.toString(), originalName);

            String tempName = Long.toString(System.currentTimeMillis());

            Map<String, Object> sfdcObject = new HashMap<>();
            sfdcObject.put("id", id);
            sfdcObject.put("Name", tempName);

            sfdcClient.update("Account", sfdcObject);

            Collection fields = new ArrayList();
            fields.add("Id");
            fields.add("Name");
            List<Map<String, String>> accounts = sfdcClient.selectFields("Account", fields, "WHERE Id = '" + id + "'");
            assertEquals(accounts.toString(), 1, accounts.size());
            assertEquals(accounts.get(0).toString(), tempName, accounts.get(0).get("Name"));

            sfdcObject.put("id", id);
            sfdcObject.put("Name", originalName);
            sfdcClient.update("Account", sfdcObject);

            accounts = sfdcClient.selectFields("Account", fields, "WHERE Id = '" + id + "'");
            assertEquals(accounts.toString(), 1, accounts.size());
            assertEquals(accounts.get(0).toString(), originalName, accounts.get(0).get("Name"));

        }
    }
}

