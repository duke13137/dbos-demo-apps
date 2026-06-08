(ns dbos-starter.core
  (:require [clojure.java.io :as io]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty])
  (:import
   [dev.dbos.transact DBOS StartWorkflowOptions]
   [java.time Duration]
   [java.util.function BiConsumer]
   [org.slf4j LoggerFactory]))

(def ^:const steps-event "steps_event")

(def ^:private logger (LoggerFactory/getLogger "dbos-starter.core"))

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

(defn run-example-workflow [^BiConsumer run-step ^BiConsumer set-event workflow-id]
  (run-step #(step-one workflow-id) "stepOne")
  (set-event steps-event (Integer/valueOf 1))
  (run-step #(step-two workflow-id) "stepTwo")
  (set-event steps-event (Integer/valueOf 2))
  (run-step #(step-three workflow-id) "stepThree")
  (set-event steps-event (Integer/valueOf 3))
  "workflow-completed")

(defn- make-app [^DBOS dbos start-workflow]
  (ring/ring-handler
   (ring/router
    [["/" {:get {:handler (fn [_]
                            {:status  200
                             :headers {"Content-Type" "text/html"}
                             :body    (io/input-stream (io/resource "index.html"))})}}]
     ["/workflow/:task-id"
      {:get {:handler (fn [{{:keys [task-id]} :path-params}]
                        (future (start-workflow task-id))
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

(defn start-server! [^DBOS dbos start-workflow port]
  (.launch dbos)
  (let [handler (make-app dbos start-workflow)
        server  (jetty/run-jetty handler {:port port :join? false})]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. ^Runnable (fn []
                                           (.stop server)
                                           (.shutdown dbos))))
    server))

(defn stop-server! [server]
  (.stop server))
