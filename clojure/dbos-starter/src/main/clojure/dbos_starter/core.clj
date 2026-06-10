(ns dbos-starter.core
  (:require [clojure.java.io :as io]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty])
  (:import
   [dev.dbos.transact DBOS StartWorkflowOptions]
   [dev.dbos.transact.execution ThrowingRunnable ThrowingSupplier]
   [java.time Duration]
   [org.slf4j LoggerFactory]))

(def ^:const steps-event "steps_event")

(def ^:private logger (LoggerFactory/getLogger "dbos-starter.core"))

(defn current-workflow-id []
  (DBOS/workflowId))

(defn run-step! [^DBOS dbos step name]
  (.runStep dbos step name))

(defn set-event! [^DBOS dbos event value]
  (.setEvent dbos event value))

(defn- log-step [workflow-id step-number]
  (.info logger (format "Workflow %s step %s completed via REPL demo!" workflow-id step-number)))

(defn step-one [workflow-id]
  (Thread/sleep 1000)
  (log-step workflow-id 1)
  "step-one-done")

(defn step-two [workflow-id]
  (Thread/sleep 2000)
  (log-step workflow-id 2)
  "step-two-done")

(defn step-three [workflow-id]
  (Thread/sleep 3000)
  (log-step workflow-id 3)
  "step-three-done")

(defn run-example-workflow [^DBOS dbos]
  (let [workflow-id (current-workflow-id)]
    (run-step! dbos #(step-one workflow-id) "stepOne")
    (set-event! dbos steps-event (Integer/valueOf 1))
    (run-step! dbos #(step-two workflow-id) "stepTwo")
    (set-event! dbos steps-event (Integer/valueOf 2))
    (run-step! dbos #(step-three workflow-id) "stepThree")
    (set-event! dbos steps-event (Integer/valueOf 3))
    "workflow-completed"))

(defn bank-transfor-workflow [^DBOS dbos]
  (let [workflow-id (current-workflow-id)]
    (run-step! dbos #(step-one workflow-id) "debt 100")
    (set-event! dbos steps-event (Integer/valueOf 100))
    (run-step! dbos #(step-two workflow-id) "depot 200")
    (set-event! dbos steps-event (Integer/valueOf 200))
    "bank-transfre-completed"))

(defn- make-app [^DBOS dbos workflow]
  (ring/ring-handler
   (ring/router
    [["/" {:get {:handler (fn [_]
                            {:status  200
                             :headers {"Content-Type" "text/html"}
                             :body    (io/input-stream (io/resource "index.html"))})}}]
     ["/workflow/:task-id"
      {:get {:handler (fn [{{:keys [task-id]} :path-params}]
                        (future (^[ThrowingRunnable _] DBOS/.startWorkflow dbos
                                 #(.exampleWorkflow workflow)
                                 (StartWorkflowOptions. task-id)))
                        {:status 200 :body ""})}}]
     ["/bank-transfer/:task-id"
      {:post {:handler (fn [{{:keys [task-id]} :path-params}]
                         (future (^[ThrowingRunnable _] DBOS/.startWorkflow dbos
                                  #(.bankTransferWorkflow workflow)
                                  (StartWorkflowOptions. task-id)))
                         {:status 200 :body ""})}}]
     ["/last_step/:task-id"
      {:get {:handler (fn [{{:keys [task-id]} :path-params}]
                        (let [step (-> (.getEvent dbos task-id steps-event (Duration/ofSeconds 0))
                                       (.orElse (Integer/valueOf 0)))]
                          {:status 200 :body (str step)}))}}]
     ["/crash"
      {:post {:handler (fn [_]
                         (.warn logger "Crash endpoint called - terminating application")
                         (.. Runtime getRuntime (halt 0))
                         {:status 200 :body ""})}}]])
   (ring/create-default-handler)))

(defn start-server! [^DBOS dbos workflow port]
  (.launch dbos)
  (let [handler (make-app dbos workflow)
        server  (jetty/run-jetty handler {:port port :join? false})]
    server))

(defn stop-server! [server]
  (.stop server))
