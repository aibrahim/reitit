(ns frontend-re-frame.core
  (:require
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [reitit.core :as r]
   [reitit.coercion :as rc]
   [reitit.coercion.spec :as rss]
   [reitit.frontend :as rf]
   [reitit.frontend.controllers :as rfc]
   [reitit.frontend.easy :as rfe]
   [frontend-re-frame.db :refer [db->local-store]]))

;;; Interceptors ;;;
(def ->local-store (re-frame/after db->local-store))

;;; Events ;;;

(re-frame/reg-event-fx
 ::initialize-db
 [(re-frame/inject-cofx :local-store-db)]
 (fn [{:keys [db local-store-db]}]
   {:db (merge db local-store-db)}))

(re-frame/reg-event-fx
 ::navigate
 [ ->local-store ]
 (fn [db [_ route]]
   ;; See `navigate` effect in routes.cljs
   {::navigate! route}))

(re-frame/reg-event-db
 ::navigated
 [ ->local-store ]
 (fn [db [_ new-match]]
   (let [old-match   (:current-route db)
         controllers (rfc/apply-controllers (:controllers old-match) new-match)]
     (assoc db :current-route (assoc new-match :controllers controllers)))))

(re-frame/reg-event-db
 ::set-input-value
 [ ->local-store ]
 (fn [db [_ v]]
   (assoc db :current-input-value v)))

;;; Subscriptions ;;;

(re-frame/reg-sub
 ::current-route
 (fn [db]
   (:current-route db)))

(re-frame/reg-sub
 ::get-input-value
 (fn [db]
   (:current-input-value db)))

;;; Views ;;;

(defn home-page []
  [:div
   [:h1 "This is home page"]
   [:button
    ;; Dispatch navigate event that triggers a (side)effect.
    {:on-click #(re-frame/dispatch [::navigate ::sub-page2])}
    "Go to sub-page 2"]])

(defn sub-page1 []
  [:div
   [:h1 "This is sub-page 1"]
   [:input {:type "text"
            :value @(re-frame/subscribe [::get-input-value])
            :on-change #(re-frame/dispatch [::set-input-value (.. % -target -value)])}]])

(defn sub-page2 []
  [:div
   [:h1 "This is sub-page 2"]])

;;; Effects ;;;

;; Triggering navigation from events.
(re-frame/reg-fx
 ::navigate!
 (fn [k params query]
   (rfe/push-state k params query)))

;;; Coeffects ;;;

(re-frame/reg-cofx
 :local-store-db
 (fn [cofx _]
   (assoc cofx :db
          (into (sorted-map)
                (some->> (.getItem js/localStorage "app-db")
                         (cljs.reader/read-string))))))

;;; Routes ;;;

(defn href
  "Return relative url for given route. Url can be used in HTML links."
  ([k]
   (href k nil nil))
  ([k params]
   (href k params nil))
  ([k params query]
   (rfe/href k params query)))

(def routes
  ["/"
   [""
    {:name      ::home
     :view      home-page
     :link-text "Home"
     :controllers
     [{;; Do whatever initialization needed for home page
       ;; I.e (re-frame/dispatch [::events/load-something-with-ajax])
       :start (fn [& params](js/console.log "Entering home page"))
       ;; Teardown can be done here.
       :stop  (fn [& params] (js/console.log "Leaving home page"))}]}]
   ["sub-page1"
    {:name      ::sub-page1
     :view      sub-page1
     :link-text "Sub page 1"
     :controllers
     [{:start (fn [& params] (js/console.log "Entering sub-page 1"))
       :stop  (fn [& params] (js/console.log "Leaving sub-page 1"))}]}]
   ["sub-page2"
    {:name      ::sub-page2
     :view      sub-page2
     :link-text "Sub-page 2"
     :controllers
     [{:start (fn [& params] (js/console.log "Entering sub-page 2"))
       :stop  (fn [& params] (js/console.log "Leaving sub-page 2"))}]}]])

(defn on-navigate [new-match]
  (when new-match
    (re-frame/dispatch [::navigated new-match])))

(def router
  (rf/router
   routes
   {:data {:coercion rss/coercion}}))

(defn init-routes! []
  (js/console.log "initializing routes")
  (rfe/start!
   router
   on-navigate
   {:use-fragment true}))

(defn nav [{:keys [router current-route]}]
  [:ul
   (for [route-name (r/route-names router)
         :let       [route (r/match-by-name router route-name)
                     text (-> route :data :link-text)]]
     [:li {:key route-name}
      (when (= route-name (-> current-route :data :name))
        "> ")
      ;; Create a normal links that user can click
      [:a {:href (href route-name)} text]])])

(defn router-component [{:keys [router]}]
  (let [current-route @(re-frame/subscribe [::current-route])]
    [:div
     [nav {:router router :current-route current-route}]
     (when current-route
       [(-> current-route :data :view)])]))

;;; Setup ;;;

(def debug? ^boolean goog.DEBUG)

(defn dev-setup []
  (when debug?
    (enable-console-print!)
    (println "dev mode")))

(defn mount-root []
  (re-frame/clear-subscription-cache!)
  (init-routes!) ;; Reset routes on figwheel reload
  (reagent/render [router-component {:router router}]
                  (.getElementById js/document "app")))

(defn ^:export init []
  (re-frame/dispatch-sync [::initialize-db])
  (dev-setup)
  (mount-root))

