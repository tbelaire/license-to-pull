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
                 ;; Other
                 [tentacles "0.2.5"]
                 ;;; For something like secrets.py, but with env vars
                 [environ "0.5.0"]]

  :plugins [[lein-ring "0.8.7"]
            [lein-pdo "0.1.1"]
            [lein-lesscss "1.2"]
            [lein-environ "0.5.0"]]

  :lesscss-paths ["less"]
  :lesscss-output-path "resources/public/css"

  :aliases {"dev" ["pdo" "auto" "dev," "ring" "server-headless"]}

  :ring {:handler license-to-pull.core/app
         :init    license-to-pull.core/init}

  :source-paths ["src/clj" "test/clj"])
