package org.example;

import dev.dbos.transact.DBOS;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.RT;
import clojure.lang.Var;

final class ClojureFacade {
  private static final String WORKFLOW_NS = "dbos-starter.core";
  private static final IFn REQUIRE = Clojure.var("clojure.core", "require");
  private static final Object WORKFLOW_NS_SYMBOL = Clojure.read(WORKFLOW_NS);
  private static final Object RELOAD_ALL = Clojure.read(":reload-all");
  private static volatile Var dbosVar;
  private static volatile IFn runExampleWorkflow;
  private static volatile IFn bankTransferWorkflow;
  private static volatile IFn debouncerWorkflow;
  private static volatile IFn queueWorkflow;
  private static volatile IFn queueChildWorkflow;
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
    dbosVar = (Var) Clojure.var(WORKFLOW_NS, "*dbos*");
    runExampleWorkflow = Clojure.var(WORKFLOW_NS, "run-example-workflow");
    bankTransferWorkflow = Clojure.var(WORKFLOW_NS, "bank-transfor-workflow");
    debouncerWorkflow = Clojure.var(WORKFLOW_NS, "debouncer-workflow");
    queueWorkflow = Clojure.var(WORKFLOW_NS, "queue-workflow");
    queueChildWorkflow = Clojure.var(WORKFLOW_NS, "queue-child-workflow");
    startServerFn = Clojure.var(WORKFLOW_NS, "start-server!");
    stopServerFn = Clojure.var(WORKFLOW_NS, "stop-server!");
  }

  private static Object invokeWorkflow(DBOS dbos, IFn workflowFn, Object... args) throws Exception {
    if (reloadEnabled()) {
      requireNamespaces(true);
    }
    Var.pushThreadBindings(RT.map(dbosVar, dbos));
    try {
      return workflowFn.applyTo(RT.seq(args));
    } catch (RuntimeException e) {
      if (e.getCause() instanceof Exception cause) {
        throw cause;
      }
      throw e;
    } finally {
      Var.popThreadBindings();
    }
  }

  static Object invokeExampleWorkflow(DBOS dbos) throws Exception {
    return invokeWorkflow(dbos, runExampleWorkflow);
  }

  static Object invokeBankTransfer(DBOS dbos) throws Exception {
    return invokeWorkflow(dbos, bankTransferWorkflow);
  }

  static Object invokeDebouncerWorkflow(DBOS dbos, String key) throws Exception {
    return invokeWorkflow(dbos, debouncerWorkflow, key);
  }

  static Object invokeQueueWorkflow(DBOS dbos, DurableWorkflowService workflow) throws Exception {
    return invokeWorkflow(dbos, queueWorkflow, workflow);
  }

  static Object invokeQueueChildWorkflow(DBOS dbos, String workflowId, int step) throws Exception {
    return invokeWorkflow(dbos, queueChildWorkflow, workflowId, step);
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
