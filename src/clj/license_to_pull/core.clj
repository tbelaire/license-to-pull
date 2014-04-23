(ns license-to-pull.core
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer [GET POST ANY defroutes routes context]]
            [base64-clj.core :as base64]
            [ring.util.response :as resp]
            [cheshire.core :as json]
            [clojure.contrib.core :refer [-?>]]
            [clojure.java.io :as io]
            [tentacles.repos :as repos]
            [tentacles.data :as gh-api]
            [tentacles.pulls :as pulls]
            [clojure.pprint]))

(defmacro dbg[x] `(let [x# ~x] (println "dbg:" '~x "=")
                               (clojure.pprint/pprint x#)
                    x#))

(defn json-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string data)})

(defn str->int [str]
  (if (re-matches (re-pattern "-?\\d+") str)
    (read-string str)))

(defn base-64-decode [string]
  (base64/decode (clojure.string/replace string "\n" "")))

(def auth ["pullbot" "notachance"])


(defn read-file
  [user repo auth path]
  (tentacles.core/api-call :get "/repos/%s/%s/contents/%s" [user repo path]
                           {:auth auth}))

(defn create-new-file
  [user repo auth path message content]
  (tentacles.core/api-call :put "/repos/%s/%s/contents/%s" [user repo path]
                           {:message message
                            :content (base64/encode content)
                            :auth auth}))

(defn update-file
  [user repo auth path message content]
  (let [prev-contents (tentacles.core/api-call :get "/repos/%s/%s/contents/%s" [user repo path] {:auth auth})]
    (tentacles.core/api-call :put "/repos/%s/%s/contents/%s" [user repo path]
                             {:message message
                              :content (base64/encode content)
                              :sha (:sha prev-contents)
                              :auth auth})))

(defn open-pull
  [user repo auth base head title body]
  (tentacles.core/api-call :put "/repos/%s/%s/pulls" [user repo]
                           {:base base
                            :head head
                            :title title
                            :body body
                            :auth auth}))
(def licences
  {:mit {:title "MIT Licence", :body "An Open Licence",
         :content "MIT License word words words"}})

(defn open-pull-license
  "Assume there isn't a fork already, fork it and PR a branch with licence"
  [user repo auth licence-type]
  (let [fork (tentacles.repos/create-fork user repo {:auth auth})
        licence (licence-type licences)
        licence-commit (create-new-file "pullbot" repo "LICENCE" "BEEP BOOP COMMIT"
                                      (:content licence))

        branch (tentacles.data/create-reference "pullbot" repo "licence"
                                                (:sha licence-commit)
                                                {:auth auth})
        pull (open-pull user repo auth
                        ":user/master" ;; TODO
                        "pullbot/licence"
                        (:title licence)
                        (:content licence))]
    pull))

(defroutes api-routes
  (GET "/test" [] (json-response
                    {:message "You are testing number: "}))

  (POST "/test" req (json-response
                      {:message "Doing something something important..."}))

  (ANY "/add" [num] (json-response
                      (let [n (str->int num)]
                        (if (nil? n)
                          "NOPE"
                          (+ 1 n)))))

  (GET "/lookup/:userid/" [userid]
       (json-response (rest (repos/user-repos userid))))
  (GET "/mkreadme/:message/:content" [message content] (json-response (update-file "pullbot" "sandbox" auth "README.md" message content)))
  (GET "/readme/" []
       (json-response (read-file "pullbot" "sandbox" auth "README.md"))))



(defroutes site-routes
  (GET "/" [] (resp/redirect "/index.html"))
  (GET "/login" [] (resp/redirect (str "https://github.com/login/oauth/authorize"
                                       "?client_id=e4bdd8487db3f8ecbc7a"
                                       "&http://localhost:3000/oauth-callback")))

  (GET "/oauth-callback" {params :params} (resp/redirect 
                                            (str "/repos?code=" (params :code))))

  (route/resources "/")
  (route/not-found "Page not found"))

(def app
  (routes (handler/api (context "/api" [] api-routes))
          (handler/site site-routes)))
