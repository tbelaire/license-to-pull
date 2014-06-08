(ns license-to-pull.core
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer [GET POST ANY defroutes routes context]]
            [base64-clj.core :as base64]
            [ring.util.response :as resp]
            [cheshire.core :as json]
            [clojure.contrib.core :refer [-?>]]
            [clojure.java.io :as io]
            [clj-http.client :as http]
            [clojure.pprint]
            [environ.core]
            [license-to-pull.gh :as gh]
            [license-to-pull.views :as views])
  (:use license-to-pull.utils
        license-to-pull.spy))


(defn json-response [data & [status]]
  {:status (or status 200),
   :headers {"Content-Type" "application/json"},
   :body (json/generate-string data {:pretty true})})

;; Pullbot auth, for doing pull requests and creating forks.
(def pullbot-auth {:auth ["pullbot" (environ.core/env :pullbot-password)]})
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
  (GET "/lookup/:userid/" [userid :as {session :session}]
       (if-let [user-auth (:auth session)]
         (json-response (map :name (gh/list-repos userid user-auth)))
         (json-response "Y U NO login" 400)))
  (GET "/license/:userid/:repo/" [userid repo :as {session :session}]
       (if-let [user-auth (:auth session)]
         (json-response (fuzzy-search "license" (gh/ls-root userid repo user-auth)))
         (json-response "Y U NO login" 400)))
  (POST "/pull-request/:userid/:repo/:license/" [userid repo license pullbot-auth]
        (json-response ["Not implemented... Yet"
                        {:userid userid,
                        :repo repo,
                        :license license}]))
  ;; These are not stable
  (GET "/me/" ;{{{username :username} :user} :session}
       {session :session}
       (let [username (get-in session [:user :username])]
         (resp/redirect (str "/api/lookup/" username "/"))))
  (GET "/readme/" []
       (json-response
         (base-64-decode (:contents (gh/read-file "pullbot" "sandbox" pullbot-auth "README.md")))))
  (GET "/mkreadme/:message/:content" [message content]
       (json-response (gh/update-file "pullbot" "sandbox" pullbot-auth "README.md" message content)))
  (GET "/error/" []
       (/ 1 0)))

(defroutes site-routes
  (GET "/" []
       views/landing)
  (GET "/login" []
       (let [state (rand-int 9999)] ;; TODO upper bound?
         (assoc
           (resp/redirect (str "https://github.com/login/oauth/authorize"
                               "?client_id="
                               (:client-id gh-ltp-auth)
                               "&redirect_uri=http://localhost:3000/oauth-callback"
                               "&state="
                               state))
           :session {:state state})))

  ;; A simple experiment with the sessions.
  (GET "/session/" {session :session}
       (if-let [num (:id session)]
         (assoc (json-response session)
                :session (assoc session :id (+ 1 num)))
         (assoc (json-response session)
                :session (assoc session :id 1))))
  (GET "/oauth-callback" {{code :code, gh-state :state} :params,
                          session :session}
       (if-let [stored-state (:state session)]
         (if (= stored-state (str->int gh-state))
           (let [gh-response (call-gh-login code)
                 auth {:oauth-token (:access_token (:body gh-response))}
                 username (gh/get-user auth)]
             (assoc (json-response {:user username})
                    :session {:auth auth
                              :user username}))
           ;; Stored state is different
           (json-response ["The states are different", stored-state, gh-state] 400))
         ;; No stored session state
         (json-response "Something went wrong, you don't have a state" 400)))

  ;; I think this is only called by githubs callback, and ignored.
  (GET "/oauth-callback/phase2" {params :params}
       (json-response params))

  (GET "/listing/" {session :session}
       (if-let [user-auth (:auth session)]
         (let [user (:user session)]
           (views/listing-page
             user
             (for [{repo :name} (take 3 (gh/list-repos (:username user) user-auth))]
             {:name repo
              :licenses (fuzzy-search
                          "license"
                          (gh/ls-root (:username user) repo user-auth))})))
         "Y U NO login"))

  (route/resources "/")
  (route/not-found "Page not found"))

(def app
  (->
      (routes (context "/api" [] api-routes)
              site-routes)
    ; (wrap-spy "After session")
    ring.middleware.session/wrap-session
    ; ring.middleware.nested-params/wrap-nested-params
    ; (wrap-spy "After keyword")
    (ring.middleware.keyword-params/wrap-keyword-params)
    ; (wrap-spy "After params")
    (ring.middleware.params/wrap-params)
    ; (wrap-spy "First")
    ))

