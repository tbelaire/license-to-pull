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


;; Pullbot auth, for doing pull requests and creating forks.
(def pullbot-auth {:auth ["pullbot" (environ.core/env :pullbot-password)]})
;; Temporary auth, will be derived from oauth and session cookie.
(def user-auth {:auth ["pullbot" (environ.core/env :pullbot-password)]})
;; For testing
(def tbelaire-auth {:oauth-token (environ.core/env :ltp-tbelaire-code)})
;; Oauth client information
(def gh-ltp-auth {:client-id (environ.core/env :license-to-pull-client-id)
                  :client-secret (environ.core/env :license-to-pull-client-secret)})

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
         (rest contents))))

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
(def licenses
  {:mit {:title "MIT Licence", :body "An Open Licence",
         :content "MIT License word words words"}})

(defn open-pull-license
  "Assume there isn't a fork already, fork it and PR a branch with license"
  [user repo auth license-type]
  (let [fork (tentacles.repos/create-fork user repo {:auth auth})
        license (license-type licenses)
        license-commit (create-new-file "pullbot" repo "LICENCE" "BEEP BOOP COMMIT"
                                      (:content license))

        branch (tentacles.data/create-reference "pul" repo "license"
                                                (:sha license-commit)
                                                {:auth auth})
        pull (open-pull user repo auth
                        ":user/master" ;; TODO
                        "pullbot/license"
                        (:title license)
                        (:content license))]
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
  ;; This api function is about ready.
  (GET "/lookup/:userid/" [userid]
       (json-response (map :name (rest (repos/user-repos userid user-auth)))))
  (GET "/license/:userid/:repo/" [userid repo]
       (json-response (fuzzy-search "license" (ls-root userid repo user-auth))))
  (POST "/pull-request/:userid/:repo/:license/" [userid repo license pullbot-auth]
        (json-response ["Not implemented... Yet"
                        {:userid userid,
                        :repo repo,
                        :license license}])))

(defroutes site-routes
  (GET "/" [] (resp/redirect "/index.html"))
  (GET "/login" [] (resp/redirect (str "https://github.com/login/oauth/authorize"
                                       "?client_id="
                                       (:client-id gh-ltp-auth)
                                       "&redirect_uri=http://localhost:3000/oauth-callback"
                                       "&state=4")))

  (GET "/oauth-callback" {params :params}
       (json-response (:body (call-gh-login (:code params)))))
  ;; I think this is only called by githubs callback, and ignored.
  (GET "/oauth-callback/phase2" {params :params}
       (json-response params))

  (route/resources "/")
  (route/not-found "Page not found"))

(def app
  (routes (handler/api (context "/api" [] api-routes))
          (handler/site site-routes)))
