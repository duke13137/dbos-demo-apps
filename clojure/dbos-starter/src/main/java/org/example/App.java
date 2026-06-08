package org.example;

import dev.dbos.transact.DBOS;
import dev.dbos.transact.StartWorkflowOptions;
import dev.dbos.transact.config.DBOSConfig;
import dev.dbos.transact.execution.ThrowingRunnable;
import dev.dbos.transact.execution.ThrowingSupplier;
import dev.dbos.transact.workflow.Workflow;

import java.util.Objects;
import java.util.function.BiConsumer;

import clojure.java.api.Clojure;
import clojure.lang.AFn;
import clojure.lang.IFn;

interface DurableStarterService {
  Object exampleWorkflow() throws Exception;
}

final class ClojureFacade {
  private static final String WORKFLOW_NS = "dbos-starter.core";
  private static final IFn REQUIRE = Clojure.var("clojure.core", "require");
  private static final Object WORKFLOW_NS_SYMBOL = Clojure.read(WORKFLOW_NS);
  private static final Object RELOAD_ALL = Clojure.read(":reload-all");
  private static volatile IFn runExampleWorkflow;
  private static volatile IFn startServerFn;
  private static volatile IFn stopServerFn;

  static {
    requireWorkflowNamespace(false);
  }

  private ClojureFacade() {
  }

  private static final class CallableBiConsumer<T, U> extends AFn implements BiConsumer<T, U> {
    private final BiConsumer<T, U> delegate;

    private CallableBiConsumer(BiConsumer<T, U> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void accept(T first, U second) {
      delegate.accept(first, second);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object first, Object second) {
      accept((T) first, (U) second);
      return null;
    }
  }

  static synchronized void requireWorkflowNamespace(boolean reload) {
    if (reload) {
      REQUIRE.invoke(WORKFLOW_NS_SYMBOL, RELOAD_ALL);
    } else {
      REQUIRE.invoke(WORKFLOW_NS_SYMBOL);
    }
    runExampleWorkflow = Clojure.var(WORKFLOW_NS, "run-example-workflow");
    startServerFn = Clojure.var(WORKFLOW_NS, "start-server!");
    stopServerFn = Clojure.var(WORKFLOW_NS, "stop-server!");
  }

  static Object invokeExampleWorkflow(
      BiConsumer<IFn, String> runStep, BiConsumer<String, Object> setEvent, String workflowId)
      throws Exception {
    if (reloadEnabled()) {
      requireWorkflowNamespace(true);
    }
    try {
      return runExampleWorkflow.invoke(
          new CallableBiConsumer<>(runStep), new CallableBiConsumer<>(setEvent), workflowId);
    } catch (RuntimeException e) {
      if (e.getCause() instanceof Exception cause) {
        throw cause;
      }
      throw e;
    }
  }

  private static boolean reloadEnabled() {
    return Boolean.getBoolean("clojure.reload");
  }

  public static Object startServer(DBOS dbos, IFn startWorkflow, int port) {
    return startServerFn.invoke(dbos, startWorkflow, port);
  }

  public static void stopServer(Object server) {
    stopServerFn.invoke(server);
  }
}

class DurableStarterServiceImpl implements DurableStarterService {

  public static final String STEPS_EVENT = "steps_event";

  private final DBOS dbos;

  public DurableStarterServiceImpl(DBOS dbos) {
    this.dbos = dbos;
  }

  private Object runStep(IFn step, String name) {
    try {
      return dbos.runStep((ThrowingSupplier<Object, Exception>) step::invoke, name);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Workflow
  @Override
  public Object exampleWorkflow() throws Exception {
    return ClojureFacade.invokeExampleWorkflow(this::runStep, dbos::setEvent, DBOS.workflowId());
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

    var proxy = dbos.registerProxy(DurableStarterService.class, new DurableStarterServiceImpl(dbos));

    AFn startWorkflow = new AFn() {
      @Override
      public Object invoke(Object taskId) {
        dbos.startWorkflow(
            () -> proxy.exampleWorkflow(),
            new StartWorkflowOptions((String) taskId));
        return null;
      }
    };

    ClojureFacade.startServer(dbos, startWorkflow, 7070);
  }
}
