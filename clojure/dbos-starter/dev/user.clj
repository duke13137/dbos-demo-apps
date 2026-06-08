(in-ns 'user)

((requiring-resolve 'clojure.repl.deps/add-libs)
 '{io.github.tonsky/clj-reload   {:mvn/version "RELEASE"}
   io.github.tonsky/clojure-plus {:mvn/version "RELEASE"}
   virgil/virgil                 {:mvn/version "RELEASE"}})

((requiring-resolve 'clojure+.hashp/install!))

(let [reload-init (requiring-resolve 'clj-reload.core/init)
      reload   (requiring-resolve 'clj-reload.core/reload)]
  (reload-init {:dirs ["src/main/clojure" "src/test/clojure"]})
  (require 'virgil)
  ((requiring-resolve 'virgil/watch-and-recompile)
   ["src/main/java"]
   :options ["--release" "25" "-Xlint:unchecked"]
   :post-hook #(reload {:only :loaded})
   :verbose true))
