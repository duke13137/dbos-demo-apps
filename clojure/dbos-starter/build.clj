(ns build
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [clojure.tools.build.api :as b]))

(def lib 'dbos-starter/dbos-starter)
(def version "0.1.0")
(def class-dir "target/classes")
(def app-class-dir "classes")
(def app-main-class "org.example.App")
(def clojure-source-dir "src/main/clojure")
(def java-source-dir "src/main/java")
(def test-java-source-dir "src/test/java")
(def resource-dir "src/main/resources")
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"})
  (b/delete {:path app-class-dir}))

(defn- java-sources [source-dir]
  (->> (file-seq (io/file source-dir))
       (filter #(.isFile %))
       (filter #(str/ends-with? (.getName %) ".java"))
       (mapv #(.getPath %))))

(defn- run-command! [cmd]
  (let [{:keys [exit out err]} (apply sh/sh cmd)]
    (when (seq out)
      (print out))
    (when (seq err)
      (binding [*out* *err*]
        (print err)))
    (when-not (zero? exit)
      (throw (ex-info "Command failed" {:cmd cmd :exit exit})))))

(defn- project-classpath
  [& aliases]
  (let [cmd               (into ["clj"] (concat aliases ["-Spath"]))
        {:keys [exit out err]} (apply sh/sh cmd)]
    (when-not (zero? exit)
      (throw (ex-info "Could not build project classpath" {:cmd cmd :exit exit :err err})))
    (str/trim out)))

(defn- compile-java-sources! [cp source-dirs]
  (let [sources (mapcat java-sources source-dirs)]
    (when (seq sources)
      (run-command! (into ["javac" "-cp" cp "-d" app-class-dir] sources)))))

(defn compile-java [_]
  (b/delete {:path app-class-dir})
  (b/copy-dir {:src-dirs [resource-dir] :target-dir app-class-dir})
  (compile-java-sources! (project-classpath) [java-source-dir]))

(defn compile-e2e-java [_]
  (compile-java nil)
  (compile-java-sources! (project-classpath "-A:e2e-test") [test-java-source-dir]))

(defn run-app [_]
  (compile-java nil)
  (let [cp (str (project-classpath)
                java.io.File/pathSeparator
                app-class-dir)]
    (run-command! ["java" "-cp" cp app-main-class])))


(defn uber [_]
  (clean nil)
  (compile-java nil)
  (let [basis (b/create-basis {:project "deps.edn"})]
    (b/copy-dir {:src-dirs [clojure-source-dir resource-dir app-class-dir] :target-dir class-dir})
    (b/uber {:class-dir class-dir
             :uber-file uber-file
             :basis     basis
             :main      (symbol app-main-class)})))
