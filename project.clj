(defproject license-to-pull "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.reader "0.8.2"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 ;; CLJ
                 [ring/ring-core "1.2.0"]
                 [compojure "1.1.6"]
                 [cheshire "5.2.0"]
                 [base64-clj "0.1.1"]
                 [clj-http "0.9.2"]
                 [hiccup "1.0.5"]
                 ;; Other
                 [tentacles "0.2.5"]
                 ;;; For something like secrets.py, but with env vars
                 [environ "0.5.0"]
                 ;; CLJS
                 [reagent "0.4.2"]
                 [org.clojure/clojurescript "0.0-2173"]
                 ]

  :plugins [[lein-ring "0.8.7"]
            [lein-pdo "0.1.1"]
            [lein-lesscss "1.2"]
            [lein-environ "0.5.0"]
            [lein-cljsbuild "1.0.2"]]

  :lesscss-paths ["less"]
  :lesscss-output-path "resources/public/css"

  :aliases {"dev" ["pdo" "cljsbuild" "auto" "dev,","ring" "server-headless"]}
  ; :hooks [leiningen.cljsbuild]

  :ring {:handler license-to-pull.core/app
         :init    license-to-pull.core/init}

  ; :profiles {:prod {:cljsbuild
  ;                   {:builds
  ;                    {:client {:compiler
  ;                              {:optimizations :advanced
  ;                               :preamble ^:replace ["reagent/react.min.js"]
  ;                               :pretty-print false}}}}}
  ;            :srcmap {:cljsbuild
  ;                     {:builds
  ;                      {:client {:compiler
  ;                                {:source-map "client.js.map"
  ;                                 :source-map-path "resources/public/js/"}}}}}}

  :cljsbuild
  {:builds
   {:dev {:source-paths ["src/cljs"]
          :notify-command ["growlnotify" "-m"]
             :compiler
             {:preamble ["reagent/react.js"]
              :output-to "resources/public/js/main.js"
              :output-dir "resources/public/js/main"
              :pretty-print true
              :print-input-delimiter true
              :source-map "resources/public/js/main.js.map"}}}}

  :source-paths ["src/clj", "src/cljs"])
