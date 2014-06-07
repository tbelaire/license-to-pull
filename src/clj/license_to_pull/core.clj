(ns license-to-pull.core
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer [GET POST ANY defroutes routes context]]
            [base64-clj.core :as base64]
            [ring.util.response :as resp]
            [cheshire.core :as json]
            [clojure.contrib.core :refer [-?>]]
            [clojure.contrib.string :refer [substring?]]
            [clojure.java.io :as io]
            [tentacles.repos :as repos]
            [tentacles.data :as gh-api]
            [tentacles.pulls :as pulls]
            [clj-http.client :as http]
            [clojure.pprint]
            [environ.core]))

(defmacro dbg[x] `(let [x# ~x] (println "dbg:" '~x "=")
                               (clojure.pprint/pprint x#)
                    x#))

(defn json-response [data & [status]]
  {:status (or status 200),
   :headers {"Content-Type" "application/json"},
   :body (json/generate-string data {:pretty true})})

(defn str->int [str]
  (if (re-matches (re-pattern "-?\\d+") str)
    (read-string str)))

(defn base-64-decode [string]
  (base64/decode (clojure.string/replace string "\n" "")))

(def auth {:auth ["pullbot" (environ.core/env :pullbot-password)]})
(def gh-ltp-auth {:client-id (environ.core/env :licence-to-pull-client-id)
                  :client-secret (environ.core/env :licence-to-pull-client-secret)})
(def tbelaire-auth {:oauth-token (environ.core/env :ltp-tbelaire-code)})

(defn read-file
  [user repo auth path]
  (tentacles.core/api-call :get "/repos/%s/%s/contents/%s" [user repo path]
                           auth))

(defn create-new-file
  [user repo auth path message content]
  (tentacles.core/api-call :put "/repos/%s/%s/contents/%s" [user repo path]
                           (merge {:message message
                                   :content (base64/encode content)}
                                  auth)))

(defn update-file
  [user repo auth path message content]
  (let [prev-contents (tentacles.core/api-call :get "/repos/%s/%s/contents/%s" [user repo path] auth)]
    (tentacles.core/api-call :put "/repos/%s/%s/contents/%s" [user repo path]
                             (merge {:message message
                                     :content (base64/encode content)
                                     :sha (:sha prev-contents)}
                                    auth))))
(defn ls-root
  [user repo auth]
  (let [contents (tentacles.core/api-call
                   :get "repos/%s/%s/contents/" [user repo] auth)]
    (map (fn [m] {:name (:name m)
                  :path (:path m)})
         ; TODO drop the {:XRate-limiting ..} element
         (dbg contents))))

(defn fuzzy-search
  [needle haystack]
  (let [lc-needle (clojure.string/lower-case needle)
        lc-haystack (map #(update-in %1 [:name] clojure.string/lower-case)
                         haystack)]
    (for [hay lc-haystack
          :when (substring? lc-needle (:name hay))]
      (:path hay))))

(defn open-pull
  [user repo auth base head title body]
  (tentacles.core/api-call :put "/repos/%s/%s/pulls" [user repo]
                           (merge {:base base
                                   :head head
                                   :title title
                                   :body body}
                                  auth)))
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

        branch (tentacles.data/create-reference "pul" repo "licence"
                                                (:sha licence-commit)
                                                {:auth auth})
        pull (open-pull user repo auth
                        ":user/master" ;; TODO
                        "pullbot/licence"
                        (:title licence)
                        (:content licence))]
    pull))

(defn call-gh-login
  [code]
    (http/post "https://github.com/login/oauth/access_token"
               {:accept :json
                :query-params {:client_id (:client-id gh-ltp-auth)
                               :client_secret (:client-secret gh-ltp-auth)
                               :code code
                               :redirect_uri "http://localhost:3000/oauth-callback/phase2"}
                :as :json}))


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
  (GET "/mkreadme/:message/:content" [message content]
       (json-response (update-file "pullbot" "sandbox" auth "README.md" message content)))
  (GET "/readme/" []
       (json-response (read-file "pullbot" "sandbox" auth "README.md"))))

(defroutes site-routes
  (GET "/" [] (resp/redirect "/index.html"))
  (GET "/login" [] (resp/redirect (str "https://github.com/login/oauth/authorize"
                                       "?client_id="
                                       (:client-id gh-ltp-auth)
                                       "&redirect_uri=http://localhost:3000/oauth-callback"
                                       "&state=4")))

  (ANY "/echo" {params :params}
       (json-response params))
  (GET "/oauth-callback" {params :params}
       (json-response (:body (call-gh-login (:code params)))))
  (GET "/oauth-callback/phase2" {params :params}
       (json-response params))

  (route/resources "/")
  (route/not-found "Page not found"))

(def app
  (routes (handler/api (context "/api" [] api-routes))
          (handler/site site-routes)))
