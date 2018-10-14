(ns re-integrant-app.module.firebase
  (:require [integrant.core :as ig]
            [re-frame.core :as re-frame]
            [day8.re-frame.tracing :refer-macros [fn-traced]]
            [clojure.core.async :refer [chan go go-loop >! <! timeout close!]]
            [cljsjs.firebase]))

(defn- create-loop [f tick]
  (let [timing? (chan)
        kick #(do (f)
                  (go
                    (<! (timeout tick))
                    (>! timing? true)))]
    (go-loop []
      (when (<! timing?)
        (kick)
        (recur)))
    (kick)
    #(close! timing?)))

;; Initial DB
(def initial-db {::now nil})

;; Subscriptions
(defmulti reg-sub identity)

;; Events
(defmulti reg-event identity)
(defmethod reg-event ::init [k]
  (re-frame/reg-event-db
   k [re-frame/trim-v]
   (fn-traced
    [db _]
    (-> db
        (merge initial-db)))))
(defmethod reg-event ::halt [k]
  (re-frame/reg-event-db
   k [re-frame/trim-v]
   (fn-traced
    [db _]
    (->> db
         (filter #(not= (namespace (key %)) (namespace ::x)))
         (into {})))))
(defmethod reg-event ::login [k]
  (re-frame/reg-event-fx
   k [re-frame/trim-v]
   (fn-traced
    [{:keys [:db]} [user password]]
    (println user password)
    {:db db
     ::sign-in-with-email-and-password {:user user
                                        :password password
                                        :on-success [::check]
                                        :on-error [::check]}})))
(defmethod reg-event ::check [k]
  (re-frame/reg-event-db
   k [re-frame/trim-v]
   (fn-traced
    [db [result]]
    (println result)
    db)))

;; Effects
(defmulti reg-fx identity)
(defmethod reg-fx ::sign-in-with-email-and-password [k app]
  (re-frame/reg-fx
   k (fn [{:keys [:user :password :on-success :on-error] :as params}]
       ;;       (js/console.log (.signInWithEmailAndPassword (js-invoke app "auth")))
       (.. app
           (auth)
           (signInWithEmailAndPassword user password)
           (then #(re-frame/dispatch (vec (conj on-success %))))
           (catch #(re-frame/dispatch (vec (conj on-error %))))))))

;; Init
(defmethod ig/init-key :re-integrant-app.module/firebase
  [k {:keys [:config]}]
  (js/console.log (str "Initializing " k))
  (let [app (js/firebase.initializeApp (clj->js config)
                                       (str (random-uuid)))
        subs (->> reg-sub methods (map key))
        events (->> reg-event methods (map key))
        effects (->> reg-fx methods (map key))]
    (->> subs (map reg-sub) doall)
    (->> events (map reg-event) doall)
    (->> effects (map #(reg-fx % app)) doall)
    (re-frame/dispatch-sync [::init])
    {:subs subs :events events :app app}))

;; Halt
(defmethod ig/halt-key! :re-integrant-app.module/firebase
  [k {:keys [:subs :events :effects :app]}]
  (js/console.log (str "Halting " k))
  (js-invoke app "delete")
  (re-frame/dispatch-sync [::halt])
  (->> subs (map re-frame/clear-sub) doall)
  (->> events (map re-frame/clear-event) doall)
  (->> effects (map re-frame/clear-fx) doall))
