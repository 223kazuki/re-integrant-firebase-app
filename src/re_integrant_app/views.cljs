(ns re-integrant-app.views
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [cljsjs.semantic-ui-react]
            [cljsjs.react-transition-group]
            [soda-ash.core :as sa]
            [re-integrant-app.module.router :as router]
            [re-integrant-app.module.firebase :as firebase]))

(defn home-panel []
  (let [form (reagent/atom {:user ""
                            :password ""})]
    (fn []
      (let [{:keys [:user :password]} @form
            input-text-handler
            (fn [el]
              (let [n (aget (.-target el) "name")
                    v (aget (.-target el) "value")]
                (swap! form assoc-in [(keyword n)] v)))]
        [:div
         [:h1 "Home"]
         [sa/Segment
          [sa/Form
           [sa/FormField
            [:label "User"]
            [:input {:placeholder "User" :name "user"
                     :maxLength 50 :value (:user @form "")
                     :on-change input-text-handler}]]
           [sa/FormField
            [:label "Password"]
            [:input {:placeholder "Password" :name "password"
                     :maxLength 50 :value (:password @form "")
                     :on-change input-text-handler}]]
           [sa/Button {:disabled (or (empty? user)
                                     (empty? password))
                       :on-click
                       #(re-frame/dispatch [::firebase/login user password])}
            "Login"]]]]))))

(defn about-panel []
  (fn [] [:div "About"]))

(defn none-panel []
  [:div])

(defmulti  panels identity)
(defmethod panels :home-panel [] #'home-panel)
(defmethod panels :about-panel [] #'about-panel)
(defmethod panels :none [] #'none-panel)

(def transition-group
  (reagent/adapt-react-class js/ReactTransitionGroup.TransitionGroup))
(def css-transition
  (reagent/adapt-react-class js/ReactTransitionGroup.CSSTransition))

(defn app-container []
  (let [title (re-frame/subscribe [:re-integrant-app.module.app/title])
        active-panel (re-frame/subscribe [::router/active-panel])]
    (fn []
      [:div
       [sa/Menu {:fixed "top" :inverted true}
        [sa/Container
         [sa/MenuItem {:as "span" :header true} @title]
         [sa/MenuItem {:as "a" :href "/"} "Home"]
         [sa/MenuItem {:as "a" :href "/about"} "About"]]]
       [sa/Container {:className "mainContainer" :style {:margin-top "7em"}}
        (let [panel @active-panel]
          [transition-group
           [css-transition {:key panel
                            :classNames "pageChange" :timeout 500 :className "transition"}
            [(panels panel)]]])]])))
