package org.example;

import dev.dbos.transact.DBOS;
import dev.dbos.transact.config.DBOSConfig;

import java.util.Objects;
import clojure.java.api.Clojure;
import clojure.lang.IFn;

final class ClojureFacade {
  private static final String WORKFLOW_NS = "dbos-starter.core";
  private static final IFn REQUIRE = Clojure.var("clojure.core", "require");
  private static final Object WORKFLOW_NS_SYMBOL = Clojure.read(WORKFLOW_NS);
  private static final Object RELOAD_ALL = Clojure.read(":reload-all");
  private static volatile IFn runExampleWorkflow;
  private static volatile IFn bankTransferWorkflow;
  private static volatile IFn startServerFn;
  private static volatile IFn stopServerFn;

  static {
    requireNamespaces(false);
  }

  private ClojureFacade() {
  }


  static synchronized void requireNamespaces(boolean reload) {
    if (reload) {
      REQUIRE.invoke(WORKFLOW_NS_SYMBOL, RELOAD_ALL);
    } else {
      REQUIRE.invoke(WORKFLOW_NS_SYMBOL);
    }
    runExampleWorkflow = Clojure.var(WORKFLOW_NS, "run-example-workflow");
    bankTransferWorkflow = Clojure.var(WORKFLOW_NS, "bank-transfor-workflow");
    startServerFn = Clojure.var(WORKFLOW_NS, "start-server!");
    stopServerFn = Clojure.var(WORKFLOW_NS, "stop-server!");
  }

  private static Object invokeWorkflow(IFn workflowFn, DBOS dbos) throws Exception {
    if (reloadEnabled()) {
      requireNamespaces(true);
    }
    try {
      return workflowFn.invoke(dbos);
    } catch (RuntimeException e) {
      if (e.getCause() instanceof Exception cause) {
        throw cause;
      }
      throw e;
    }
  }

  static Object invokeExampleWorkflow(DBOS dbos) throws Exception {
    return invokeWorkflow(runExampleWorkflow, dbos);
  }

  static Object invokeBankTransfer(DBOS dbos) throws Exception {
    return invokeWorkflow(bankTransferWorkflow, dbos);
  }

  public static Object startServer(DBOS dbos, Object proxy, int port) {
    return startServerFn.invoke(dbos, proxy, port);
  }

  public static void stopServer(Object server) {
    stopServerFn.invoke(server);
  }

  private static boolean reloadEnabled() {
    return Boolean.getBoolean("clojure.reload");
  }
}

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
