(ns license-to-pull.views
  (:use hiccup.core
        hiccup.element
        hiccup.page
        hiccup.util))

(defn view-body
  [& body]
  (html5 {:lang "en"}
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:http-equiv "X-UA-Compatible", :content "IE=edge"}]
     (include-css
       "http://yui.yahooapis.com/pure/0.5.0/pure-min.css"
       "/css/base.css"
       "//netdna.bootstrapcdn.com/font-awesome/4.1.0/css/font-awesome.css")]
     [:body body]))

(def landing
  (view-body
     [:div.h.header.centered
      [:div.home-menu.pure-menu.pure-menu-open.pure-menu-horizontal
       [:h1 "License To Pull"]]]
     [:div.centered
      [:a {:href "/login", :class "pure-button"}
       [:i.fa.fa-github]
       [:span " Sign in with GitHub"]]]))

;; For listing repos
(defn repo-item
  [username repo]
  (html
        [:span (str username "/" repo)]))

(defn listing-page
  "repo-list is [{:name .., :licenses ..}]"
  [user repo-list]
  (view-body
    [:h1 (str (:name user) "'s repositories")]
    (unordered-list
      (for [repo repo-list]
        (repo-item (:username user) (:name repo))))))
