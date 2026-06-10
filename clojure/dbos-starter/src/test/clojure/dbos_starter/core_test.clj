(ns dbos-starter.core-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [dbos-starter.core :as core])
  (:import [dev.dbos.transact DBOS StartWorkflowOptions]
           [dev.dbos.transact.config DBOSConfig]
           [java.time Duration]
           [java.util UUID]
           [org.example DurableWorkflowService DurableWorkflowServiceImpl]))

(def ^:dynamic *dbos* nil)
(def ^:dynamic *workflow-proxy* nil)

(defn- jdbc-url []
  (str "jdbc:postgresql://"
       (or (System/getenv "PGHOST") "localhost")
       ":"
       (or (System/getenv "PGPORT") "5432")
       "/"
       (or (System/getenv "PGDATABASE") "dbos_starter_java")))

(defn- dbos-config []
  (-> (DBOSConfig/defaults "dbos-starter-clj-test")
      (.withDatabaseUrl (jdbc-url))
      (.withDbUser (or (System/getenv "PGUSER") "postgres"))
      (.withDbPassword (or (System/getenv "PGPASSWORD") "dbos"))
      (.withAppVersion "0.2.0")))

(defn- with-live-dbos [f]
  (let [dbos (DBOS. (dbos-config))
        proxy (.registerProxy dbos DurableWorkflowService (DurableWorkflowServiceImpl. dbos))]
    (try
      (.launch dbos)
      (binding [*dbos* dbos
                *workflow-proxy* proxy]
        (f))
      (finally
        (.shutdown dbos)))))

(use-fixtures :once with-live-dbos)

(defn- execute-workflow! [workflow-id workflow-call]
  (let [handle (.startWorkflow *dbos*
                               #(workflow-call *workflow-proxy*)
                               (StartWorkflowOptions. workflow-id))]
    {:workflow-id workflow-id
     :result (.getResult handle)
     :step (.orElse (.getEvent *dbos* workflow-id core/steps-event (Duration/ofSeconds 0))
                    (Integer/valueOf 0))}))

(deftest example-workflow-test
  (let [{:keys [result step workflow-id]}
        (execute-workflow! (str "clj-example-" (UUID/randomUUID))
                           DurableWorkflowService/.exampleWorkflow)]
    (is (= "workflow-completed" result))
    (is (= (Integer/valueOf 3) step))
    (is (string? workflow-id))))

(deftest bank-transfer-workflow-test
  (let [{:keys [result step workflow-id]}
        (execute-workflow! (str "clj-bank-transfer-" (UUID/randomUUID))
                           DurableWorkflowService/.bankTransferWorkflow)]
    (is (= "bank-transfre-completed" result))
    (is (= (Integer/valueOf 200) step))
    (is (string? workflow-id))))
