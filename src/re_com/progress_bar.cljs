(ns re-com.progress-bar
  (:require-macros
    [re-com.core :refer [handler-fn]]
    [re-com.validate :refer [validate-args-macro]])
  (:require [re-com.util     :refer [deref-or-value px]]
            [re-com.popover  :refer [popover-tooltip]]
            [re-com.box      :refer [h-box v-box box gap line flex-child-style align-style]]
            [re-com.validate :refer [input-status-type? input-status-types-list regex? string-or-hiccup? css-style? html-attr? parts?
                                     number-or-string? string-or-atom? nillable-string-or-atom? throbber-size? throbber-sizes-list]]
            [reagent.core    :as    reagent]))

;; ------------------------------------------------------------------------------------
;;  Component: progress-bar
;; ------------------------------------------------------------------------------------

(def progress-bar-args-desc
  [{:name :model     :required true                  :type "double | string | atom" :validate-fn number-or-string? :description "current value of the slider. A number between 0 and 100"}
   {:name :width     :required false :default "100%" :type "string"                 :validate-fn string?           :description "a CSS width"}
   {:name :striped?  :required false :default false  :type "boolean"                                               :description "when true, the progress section is a set of animated stripes"}
   {:name :bar-class :required false                 :type "string"                 :validate-fn string?           :description "CSS class name(s) for the actual progress bar itself, space separated"}
   {:name :class     :required false                 :type "string"                 :validate-fn string?           :description "CSS class names, space separated (applies to the progress-bar, not the wrapping div)"}
   {:name :style     :required false                 :type "CSS style map"          :validate-fn css-style?        :description "CSS styles to add or override (applies to the progress-bar, not the wrapping div)"}
   {:name :attr      :required false                 :type "HTML attr map"          :validate-fn html-attr?        :description [:span "HTML attributes, like " [:code ":on-mouse-move"] [:br] "No " [:code ":class"] " or " [:code ":style"] "allowed (applies to the progress-bar, not the wrapping div)"]}
   {:name :parts     :required false                 :type "map"                    :validate-fn (parts? #{:wrapper :segment}) :description "See Parts section below."}])

(defn progress-bar
  "Render a bootstrap styled progress bar"
  [& {:keys [model width striped? class bar-class style attr parts]
      :or   {width "100%"}
      :as   args}]
  {:pre [(validate-args-macro progress-bar-args-desc args "progress-bar")]}
  (let [model (deref-or-value model)]
    [box
     :class (str "rc-progress-bar-wrapper " (get-in parts [:wrapper :class]))
     :style (get-in parts [:wrapper :style] {})
     :attr  (get-in parts [:wrapper :attr] {})
     :align :start
     :child [:div
             (merge
               {:class (str "progress rc-progress-bar " class)
                :style (merge (flex-child-style "none")
                              {:width width}
                              style)}
               attr)
             [:div
              {:class (str "progress-bar " (when striped? "progress-bar-striped active rc-progress-bar-portion ") bar-class)
               :role  "progressbar"
               :style {:width      (str model "%")
                       :transition "none"}}                 ;; Default BS transitions cause the progress bar to lag behind
              (str model "%")]]]))