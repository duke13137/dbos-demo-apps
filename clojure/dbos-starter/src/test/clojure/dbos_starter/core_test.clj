(ns dbos-starter.core-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [dbos-starter.core :as core])
  (:import [dev.dbos.transact DBOS]
           [dev.dbos.transact.config DBOSConfig]
           [dev.dbos.transact.workflow QueueOptions]
           [java.util UUID]
           [org.example DurableWorkflowService DurableWorkflowServiceImpl]))

(def ^:dynamic *workflow-proxy* nil)

(defn- jdbc-url []
  (str "jdbc:postgresql://"
       (or (System/getenv "PGHOST") "localhost")
       ":"
       (or (System/getenv "PGPORT") "5432")
       "/"
       "dbos_starter_clojure"))

(defn- dbos-config []
  (-> (DBOSConfig/defaults "dbos-starter-clj-test")
      (.withDatabaseUrl (jdbc-url))
      (.withDbUser (or (System/getenv "PGUSER") "postgres"))
      (.withDbPassword (or (System/getenv "PGPASSWORD") "dbos"))
      (.withAppVersion "0.2.0")))

(defn- with-live-dbos [f]
  (let [dbos (DBOS. (dbos-config))
        impl (DurableWorkflowServiceImpl. dbos)
        proxy (.registerProxy dbos DurableWorkflowService impl)]
    (try
      (.setSelf impl proxy)
      (.launch dbos)
      (.registerQueue dbos "example-queue" (QueueOptions/empty))
      (binding [core/*dbos* dbos
                *workflow-proxy* proxy]
        (f))
      (finally
        (.shutdown dbos)))))

(use-fixtures :once with-live-dbos)

(deftest example-workflow-test
  (let [{:keys [result step workflow-id]}
        (core/execute-workflow! *workflow-proxy*
                                (str "clj-example-" (UUID/randomUUID))
                                DurableWorkflowService/.exampleWorkflow)]
    (is (= "workflow-completed" result))
    (is (= (Integer/valueOf 3) step))
    (is (string? workflow-id))))

(deftest bank-transfer-workflow-test
  (let [{:keys [result step workflow-id]}
        (core/execute-workflow! *workflow-proxy*
                                (str "clj-bank-transfer-" (UUID/randomUUID))
                                DurableWorkflowService/.bankTransferWorkflow)]
    (is (= "bank-transfre-completed" result))
    (is (= (Integer/valueOf 200) step))
    (is (string? workflow-id))))

(deftest debouncer-workflow-test
  (let [{:keys [result workflow-id]}
        (core/execute-workflow! *workflow-proxy*
                                (str "clj-debouncer-" (UUID/randomUUID))
                                #(.debouncerWorkflow % "demo"))]
    (is (= "debouncer-completed" result))
    (is (string? workflow-id))))

(deftest queue-workflow-test
  (let [{:keys [result step workflow-id]}
        (core/execute-workflow! *workflow-proxy*
                                (str "clj-queue-" (UUID/randomUUID))
                                DurableWorkflowService/.queueWorkflow)]
    (is (= "queue-completed" result))
    (is (= (Integer/valueOf 10) step))
    (is (string? workflow-id))))
