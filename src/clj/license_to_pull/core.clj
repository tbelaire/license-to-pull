(ns license-to-pull.core
    (:require [compojure.handler :as handler]
              [compojure.route :as route]
              [compojure.core :refer [GET POST ANY defroutes]]
              [ring.util.response :as resp]
              [cheshire.core :as json]
              [clojure.contrib.core :refer [-?>]]
              [clojure.java.io :as io]))

(defn json-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string data)})

(defn str->int [str]
  (if (re-matches (re-pattern "-?\\d+") str)
    (read-string str)))

(defroutes app-routes
  (GET "/" [] (resp/redirect "/index.html"))

  (GET "/test" [] (json-response
                   {:message "You made it!"}))

  (POST "/test" req (json-response
                     {:message "Doing something something important..."}))

  (ANY "/add" [num] (json-response
                    (let [n (str->int num)]
                      (if (nil? n)
                        "NOPE"
                        (+ 1 n)))))


  (route/resources "/")
  (route/not-found "Page not found"))

(def app
  (-> #'app-routes
      (handler/api)))
