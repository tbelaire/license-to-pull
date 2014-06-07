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
            [clj-http.client :as http]
            [clojure.pprint]
            [environ.core])
  (:use license-to-pull.utils
        license-to-pull.gh))


(defn json-response [data & [status]]
  {:status (or status 200),
   :headers {"Content-Type" "application/json"},
   :body (json/generate-string data {:pretty true})})

;; Pullbot auth, for doing pull requests and creating forks.
(def pullbot-auth {:auth ["pullbot" (environ.core/env :pullbot-password)]})
;; Temporary auth, will be derived from oauth and session cookie.
(def user-auth {:auth ["pullbot" (environ.core/env :pullbot-password)]})
;; For testing
(def tbelaire-auth {:oauth-token (environ.core/env :ltp-tbelaire-code)})
;; Oauth client information
(def gh-ltp-auth {:client-id (environ.core/env :license-to-pull-client-id)
                  :client-secret (environ.core/env :license-to-pull-client-secret)})


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
                        :license license}]))
  ;; These are not stable
  (GET "/readme/" []
       (json-response
         (base-64-decode (:contents (read-file "pullbot" "sandbox" pullbot-auth "README.md")))))
  (GET "/mkreadme/:message/:content" [message content]
       (json-response (update-file "pullbot" "sandbox" pullbot-auth "README.md" message content))))

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
