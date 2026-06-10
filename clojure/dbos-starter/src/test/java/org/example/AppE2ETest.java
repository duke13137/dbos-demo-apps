package org.example;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.dbos.transact.DBOS;
import dev.dbos.transact.config.DBOSConfig;
import dev.dbos.transact.StartWorkflowOptions;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AppE2ETest {
  private static final String BASE_URL = "http://127.0.0.1:7777";
  private static final String WORKFLOW_ID = "e2e-test-001";
  private static final String BANK_TRANSFER_WORKFLOW_ID = "e2e-test-bt-002";
  private static final String DEFAULT_DATABASE = "dbos_starter_java";

  private static DBOS dbos;
  private static DurableWorkflowService proxy;
  private static Object server;

  @BeforeAll
  static void startEnvironment() throws Exception {
    Assumptions.assumeTrue(
        hasRequiredPgEnv(),
        "PGHOST, PGUSER, and PGPASSWORD must be set for AppE2ETest.");

    var env = System.getenv();
    var dbUrl = buildJdbcUrl(env);
    var dbUser = env.get("PGUSER");
    var dbPassword = env.get("PGPASSWORD");

    dbos = new DBOS(
        DBOSConfig.defaults("dbos-starter-java")
            .withDatabaseUrl(dbUrl)
            .withDbUser(dbUser)
            .withDbPassword(dbPassword)
            .withAppVersion("0.2.0"));

    proxy = dbos.registerProxy(DurableWorkflowService.class, new DurableWorkflowServiceImpl(dbos));

    server = ClojureFacade.startServer(dbos, proxy, 7777);
  }

  @AfterAll
  static void stopEnvironment() {
    if (server != null) {
      ClojureFacade.stopServer(server);
    }
  }

  @Test
  void workflowCompletesAllSteps() throws Exception {
    given().when().get(BASE_URL + "/workflow/" + WORKFLOW_ID).then().statusCode(200);

    var deadline = System.currentTimeMillis() + 30_000;
    String lastStep = "";
    while (System.currentTimeMillis() < deadline) {
      var response = given().when().get(BASE_URL + "/last_step/" + WORKFLOW_ID);
      lastStep = response.getBody().asString();
      if ("3".equals(lastStep)) {
        break;
      }
      Thread.sleep(500);
    }
    assertEquals("3", lastStep, "Expected final step 3");
  }

  @Test
  void bankTransferCompletesAllSteps() throws Exception {
    given().when()
        .post(BASE_URL + "/bank-transfer/" + BANK_TRANSFER_WORKFLOW_ID)
        .then().statusCode(200);

    var deadline = System.currentTimeMillis() + 30_000;
    String lastStep = "";
    while (System.currentTimeMillis() < deadline) {
      var response = given().when()
          .get(BASE_URL + "/last_step/" + BANK_TRANSFER_WORKFLOW_ID);
      lastStep = response.getBody().asString();
      if ("200".equals(lastStep)) {
        break;
      }
      Thread.sleep(500);
    }
    assertEquals("200", lastStep, "Expected final step 200 for bank transfer");
  }

  private static boolean hasRequiredPgEnv() {
    return isPresent(System.getenv("PGHOST"))
        && isPresent(System.getenv("PGUSER"))
        && isPresent(System.getenv("PGPASSWORD"));
  }

  private static boolean isPresent(String value) {
    return value != null && !value.isBlank();
  }

  private static String buildJdbcUrl(Map<String, String> env) {
    var host = env.get("PGHOST");
    var port = env.getOrDefault("PGPORT", "5432");
    var database = env.getOrDefault("PGDATABASE", DEFAULT_DATABASE);
    return "jdbc:postgresql://" + host + ":" + port + "/" + database;
  }
}
