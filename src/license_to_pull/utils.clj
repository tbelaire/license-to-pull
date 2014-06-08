(ns license-to-pull.utils
  (:require [base64-clj.core :as base64]
            [clojure.contrib.string :refer [substring?]]))

(defn str->int [str]
  (if str
    (if (re-matches (re-pattern "-?\\d+") str)
      (read-string str))))

(defn base-64-decode [string]
  (base64/decode (clojure.string/replace string "\n" "")))

(defmacro dbg[x] `(let [x# ~x] (println "dbg:" '~x "=")
                               (clojure.pprint/pprint x#)
                    x#))

(defn fuzzy-search
  [needle haystack]
  (let [lc-needle (clojure.string/lower-case needle)
        lc-haystack (map #(update-in %1 [:name] clojure.string/lower-case)
                         haystack)]
    (for [hay lc-haystack
          :when (substring? lc-needle (:name hay))]
      (:path hay))))


