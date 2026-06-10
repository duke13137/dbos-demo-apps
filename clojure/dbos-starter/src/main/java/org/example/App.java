package org.example;

import dev.dbos.transact.DBOS;
import dev.dbos.transact.config.DBOSConfig;
import java.util.Objects;

public class App {
  public static void main(String[] args) {

    var dbUrl = System.getenv("DBOS_SYSTEM_JDBC_URL");
    if (dbUrl == null || dbUrl.isEmpty()) {
      dbUrl = "jdbc:postgresql://localhost:5432/dbos_starter_java";
    }
    var dbUser = Objects.requireNonNullElse(System.getenv("PGUSER"), "postgres");
    var dbPassword = Objects.requireNonNullElse(System.getenv("PGPASSWORD"), "dbos");

    var dbosConfig = DBOSConfig.defaults("dbos-starter-java")
        .withDatabaseUrl(dbUrl)
        .withDbUser(dbUser)
        .withDbPassword(dbPassword)
        .withAppVersion("0.2.0");

    var dbos = new DBOS(dbosConfig);

    var proxy = dbos.registerProxy(DurableWorkflowService.class, new DurableWorkflowServiceImpl(dbos));

    var server = ClojureFacade.startServer(dbos, proxy, 7070);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      ClojureFacade.stopServer(server);
      dbos.shutdown();
    }));
  }
}
