# Simple Salesforce Client (sfdcclient)
This is the simplest possible SFDC client, it does proper authentication and allows for basic CRUD operations.

## Building
Build:
```
./gradlew build -x test
```

Assemble all artifacts:
```
./gradlew assemble
```

List all gradle tasks available:
```
./gradlew tasks
```
## Including in your project:
Please see [Maven Central](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.github.dt209%22%20AND%20a%3A%22sfdcclient%22)

## Testing
Before running the tests you **must** have a valid Salesforce API Enabled username/account.
This account must have privelages to read/write/update/delete Salesforce Account objects

- Set an environment variable of `u` with Salesforce username
- Set an environment variable of `p` with Salesforce password for the user above (case-sensitive)
- Set an environment variable of `t` with Security token for the user above(case-sensitive)

```
./gradlew test
```

## Architectural Decisions
Adhere to the [KISS principle](https://en.wikipedia.org/wiki/KISS_principle) as much as possible.
Choosing to use simple highly maintainable code rather than being fancy
Reduce number of dependencies

## Examples:
```java
// Create client (Note: that the constants are just defaults, feel free
// to find the best suited SOAP Path for your Org similarly if you decide
// to use domains and want to use a different host.  The default
// HOSTS are login.salesforce.coma and test.salesforce.com,
// the default SOAP_PATH is (or was at some point) /services/Soap/u/42.0)
SfdcClient sfdcClient = new SfdcClient(
        SfdcClient.SANDBOX_HOST,
        SfdcClient.SOAP_PATH,
        salesForceUsername,
        salesForcePassword,
        salesForceSecurityToken
    );

// Get list of Account objects (any SFDC object will work):
List<Map<String, String>> queryResult = sfdcClient.query("SELECT Id, Name FROM Account");

// Select specific fields, useful for more generic code, where clause is required here. ID should be escaped!
Collection<String> fields = new ArrayList<>();
fields.add("Id");
fields.add("Name");
List<Map<String, String>> accounts = sfdcClient.selectFields("Account", fields, "Id = '" + id + "'");

// Change name of Account (any SFDC object will work)
Map<String, Object> sfdcObject = new HashMap<>();
sfdcObject.put("id", id); // Required!
sfdcObject.put("Name", newName);
sfdcClient.update("Account", sfdcObject);

// Create Account object (any SFDC object will work):
Map<String,Object> sfdcObject = new HashMap<>();
sfdcObject.put("Name", accountName);
String id = sfdcClient.upsert("id", "Account", sfdcObject);

// Delete SFDC Object by ID:
sfdcClient.delete(id);
```

### Logging
Uses Java's internal logging framework as to minimize dependencies.

```java
Logger logger = Logger.getLogger(sfdcClient.getClass().getName());
logger.addHandler(new ConsoleHandler());
logger.setLevel(Level.ALL);
```

### Depenedencies
Currently this project only has three dependencies (`okhttp`, `commons-lang3`
makes use of `ClassUtils`, `commons-text` makes use of `StringEscapeUtils`),
and their version isn't super important so feel free to replace them as long as
they basically funciton the same there should be no issues.

### Releases
Bump up the release number then issue:
```
./gradlew clean uploadArchives closeAndReleaseRepository
```

#### Background on releases
- Instructions: http://central.sonatype.org/pages/gradle.html
- Maven Repo: https://oss.sonatype.org
- Ticket for access: https://issues.sonatype.org/browse/OSSRH-39366
