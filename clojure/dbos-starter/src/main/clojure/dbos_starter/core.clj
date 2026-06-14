(ns dbos-starter.core
  (:require [clojure.java.io :as io]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty])
  (:import
   [dev.dbos.transact DBOS StartWorkflowOptions]
   [dev.dbos.transact.workflow QueueOptions]
   [java.time Duration]
   [org.slf4j LoggerFactory]))

(def ^:const steps-event "steps_event")

(def ^:dynamic *dbos* nil)

(def ^:private logger (LoggerFactory/getLogger "dbos-starter.core"))

(defn current-workflow-id []
  (DBOS/workflowId))

(defn run-step! [^DBOS dbos step name]
  (.runStep dbos step name))

(defn set-event! [^DBOS dbos event value]
  (.setEvent dbos event value))

(defn execute-workflow!
  ([workflow-proxy workflow-id workflow-call]
   (execute-workflow! workflow-proxy workflow-id workflow-call (StartWorkflowOptions. workflow-id)))
  ([workflow-proxy workflow-id workflow-call options]
   (let [handle (.startWorkflow *dbos*
                                #(workflow-call workflow-proxy)
                                options)]
     {:workflow-id workflow-id
      :result (.getResult handle)
      :step (.orElse (.getEvent *dbos* workflow-id steps-event (Duration/ofSeconds 0))
                     (Integer/valueOf 0))})))

(defn- workflow-response [result]
  {:status 200
   :headers {"Content-Type" "application/edn"}
   :body (pr-str result)})

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

(defn run-example-workflow []
  (let [workflow-id (current-workflow-id)]
    (run-step! *dbos* #(step-one workflow-id) "stepOne")
    (set-event! *dbos* steps-event (Integer/valueOf 1))
    (run-step! *dbos* #(step-two workflow-id) "stepTwo")
    (set-event! *dbos* steps-event (Integer/valueOf 2))
    (run-step! *dbos* #(step-three workflow-id) "stepThree")
    (set-event! *dbos* steps-event (Integer/valueOf 3))
    "workflow-completed"))

(defn bank-transfor-workflow []
  (let [workflow-id (current-workflow-id)]
    (run-step! *dbos* #(step-one workflow-id) "debt 100")
    (set-event! *dbos* steps-event (Integer/valueOf 100))
    (run-step! *dbos* #(step-two workflow-id) "depot 200")
    (set-event! *dbos* steps-event (Integer/valueOf 200))
    "bank-transfre-completed"))

(defn debouncer-workflow [key]
  (.info logger (format "Debounced workflow executing for key '%s'" key))
  "debouncer-completed")

(defn queue-child-workflow [workflow-id step]
  (.info logger (format "Running workflow %s queued child step %s" workflow-id step))
  (Thread/sleep 500)
  (.info logger (format "Workflow %s queued child step %s completed!" workflow-id step))
  "queue-child-completed")

(defn queue-workflow [workflow]
  (let [workflow-id (current-workflow-id)]
    (.info logger (format "Enqueueing steps workflow %s" workflow-id))
    (let [handles (doall
                   (for [step (range 10)]
                     (let [child-workflow-id (format "%s-step-%s" workflow-id step)
                           options (-> (StartWorkflowOptions. child-workflow-id)
                                       (.withQueue "example-queue"))]
                       (.startWorkflow *dbos*
                                       #(.queueChildWorkflow workflow workflow-id step)
                                       options))))
          results (doall (map #(.getResult %) handles))]
      (.info logger (format "Workflow %s successfully completed %s queued steps"
                            workflow-id
                            (count results))))
    (set-event! *dbos* steps-event (Integer/valueOf 10))
    "queue-completed"))

(defn- make-app [^DBOS dbos workflow debouncer]
  (let [handler (ring/ring-handler
                 (ring/router
                  [["/" {:get {:handler (fn [_]
                                          {:status  200
                                           :headers {"Content-Type" "text/html"}
                                           :body    (io/input-stream (io/resource "index.html"))})}}]
                   ["/workflow/:task-id"
                    {:get {:handler (fn [{{:keys [task-id]} :path-params}]
                                      (workflow-response
                                       (execute-workflow! workflow
                                                          task-id
                                                          #(.exampleWorkflow %))))}}]
                   ["/bank-transfer/:task-id"
                    {:post {:handler (fn [{{:keys [task-id]} :path-params}]
                                       (workflow-response
                                        (execute-workflow! workflow
                                                           task-id
                                                           #(.bankTransferWorkflow %))))}}]
                   ["/queue/:task-id"
                    {:post {:handler (fn [{{:keys [task-id]} :path-params}]
                                       (workflow-response
                                        (execute-workflow! workflow
                                                           task-id
                                                           #(.queueWorkflow %))))}}]
                   ["/debounce/:key"
                    {:get {:handler (fn [{{:keys [key]} :path-params}]
                                      (.info logger (format "Debounce endpoint called for key '%s'" key))
                                      (.debounce debouncer
                                                 key
                                                 (Duration/ofSeconds 5)
                                                 #(do (.debouncerWorkflow workflow key)
                                                      nil))
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
                 (ring/create-default-handler))]
    (fn [request]
      (binding [*dbos* dbos]
        (handler request)))))

(defn start-server! [^DBOS dbos workflow port]
  (.launch dbos)
  (.registerQueue dbos "example-queue" (QueueOptions/empty))
  (let [debouncer (.. dbos debouncer (withDebounceTimeout (Duration/ofMinutes 1)))
        handler (make-app dbos workflow debouncer)
        server  (jetty/run-jetty handler {:port port :join? false})]
    server))

(defn stop-server! [server]
  (.stop server))
