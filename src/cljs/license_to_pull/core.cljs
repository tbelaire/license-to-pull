(ns license-to-pull.core
    (:require-macros [cljs.core.async.macros :refer [go alt!]])
    (:require [goog.events :as events]
              [cljs.core.async :refer [put! <! >! chan timeout]]
              [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [cljs-http.client :as http]
              [license-to-pull.utils :refer [guid]]))

;; Lets you do (prn "stuff") to the console
(enable-console-print!)

(def app-state
  (atom {:things []}))

(defn license-to-pull-app [app owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
               (dom/h1 nil "license-to-pull is working!")))))

(om/root app-state license-to-pull-app (.getElementById js/document "content"))
