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


(defn get-readme
  [user repo]
  (let [master-tree-sha (:sha (:object (gh-api/reference user repo "heads/master")))
        root-tree       (gh-api/tree user repo master-tree-sha)
        readme-sha      (:sha (first (filter #(= (:path %) "README.md") (:tree root-tree))))
        readme-blob     (gh-api/blob user repo readme-sha {:accept "application/vnd.github.v3.raw"})]
    readme-blob))
    ;(base-64-decode (:content readme-blob))))

(defn make-readme-commit
  [user repo auth]
  (let [master-json     (dbg (gh-api/reference user repo "heads/master"))
        master-tree-sha (:sha (:object master-json))
        root-tree       (dbg (gh-api/tree user repo master-tree-sha))
        new-blob-readme (dbg (gh-api/create-blob user repo "this is a readme" "utf-8" {:auth auth, :throw-exceptions true}))
        new-tree-readme (dbg {:path "README.txt", :mode "100644", :type "blob" :sha (:sha new-blob-readme)})
        new-tree-root   (dbg (gh-api/create-tree user repo [new-tree-readme] {:auth auth,
                                                                            ; :base-tree (:sha root-tree)
                                                                            :throw-exceptions true,
                                                                            }))
        commit (dbg (tentacles.core/api-call :post "repos/%s/%s/git/commits" [user repo]
                  (dbg {:message "New commit"
                        :tree {:sha (:sha (:object master-json))}
                        :parents [ (:sha master-json) ]
                        :auth auth
                   })))]
        ; commit          (dbg (gh-api/create-commit user repo "Auto commit" (dbg {:sha (:sha new-tree-root)}) (dbg {:parents [(:sha master-json)] :auth auth})))]
    commit))

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
       (json-response (repos/user-repos userid)))
  (GET "/mkreadme/:message/:content" [message content] (json-response (update-file "pullbot" "sandbox" auth "README.md" message content)))
  (GET "/readme/" []
       (json-response (get-readme "pullbot" "sandbox"))))



(defroutes site-routes
  (GET "/" [] (resp/redirect "/index.html"))

  (route/resources "/")
  (route/not-found "Page not found"))

(def app
  (routes (handler/api (context "/api" [] api-routes))
          (handler/site site-routes)))
