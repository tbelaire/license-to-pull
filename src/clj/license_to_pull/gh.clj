(ns license-to-pull.gh
  (:require
    [base64-clj.core :as base64]
    [cheshire.core :as json]
    [tentacles.repos]
    [tentacles.data]
    [tentacles.pulls])
  (:use
    [license-to-pull.utils]))


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
