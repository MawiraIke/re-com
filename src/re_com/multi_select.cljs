(ns re-com.multi-select
  (:require-macros [re-com.core :refer [handler-fn]])
  (:require
    [clojure.set                 :as set]
    [clojure.string              :as string]
    [goog.string                 :as gstring]
    [re-com.box                  :as box]
    [re-com.text                 :as text]
    [re-com.buttons              :as buttons]
    [re-com.close-button         :as close-button]
    [re-com.misc                 :as misc]
    [re-com.util                 :as rc.util]
    [re-com.validate             :as validate :refer [string-or-hiccup?] :refer-macros [validate-args-macro]]
    [reagent.core                :as reagent]))

(defn items-with-group-headings
  "Split a list of maps by a group key then return both the group"
  [items group-fn id-fn]
  (let [groups         (partition-by group-fn items)
        group-headers  (->> groups
                            (map first)
                            (map #(hash-map :id    [(group-fn %) (id-fn %)]
                                            :group (group-fn %))))]
    ;; Sample output:
    ;; group-headers    ({:group "Fruits"     :id "Fruits"}
    ;;                   {:group "Vegetables" :id "Vegetables"})
    ;;
    ;; groups           (({:short "Watermelon" :id "0001" :display-type "fruit"     :sort 110 ...}
    ;;                    {:short "Strawberry" :id "0002" :display-type "fruit"     :sort 120 ...}
    ;;                    {:short "Cherry"     :id "0003" :display-type "fruit"     :sort 130 ...})
    ;;                   ({:short "Corn"       :id "0004" :display-type "vegetable" :sort 430 ...}))
    [group-headers groups]))


(defn filter-items
  "Filter a list of items based on a filter string using plain string searches (case insensitive). Less powerful
   than regex's but no confusion with reserved characters"
  [group-fn label-fn filter-text]
  (let [lower-filter-text (string/lower-case filter-text)]
    (fn [item]
      (let [group (or (group-fn item) "")
            label (str (label-fn item))] ;; Need str for non-string labels like hiccup
        (or
          (string/includes? (string/lower-case group) lower-filter-text)
          (string/includes? (string/lower-case label) lower-filter-text))))))


(defn filter-items-regex
  "Filter a list of items based on a filter string using regex's (case insensitive). More powerful but can cause
   confusion for users entering reserved characters such as [ ] * + . ( ) etc."
  [group-fn label-fn filter-text]
  (let [re (try
             (js/RegExp. filter-text "i")
             (catch js/Object e nil))]
    (partial (fn [re item]
               (when-not (nil? re)
                 (or (.test re (group-fn item)) (.test re (label-fn item)))))
             re)))


(defn filter-text-box
  "Base function (before lifecycle metadata) to render a filter text box"
  [*filter-text placeholder *warning-message disabled?]
  [box/h-box
   :width    "100%"
   :align    :center
   :style    {:position "relative"}
   :children [[misc/input-text
               :model           *filter-text
               :change-on-blur? false
               :placeholder     placeholder
               ;:disabled?       disabled? ;; Left here just in case we DO want to prevent searches while disabled
               :width           "100%"
               :height          "28px"
               :style           {:padding "3px 4px"}
               :on-change       #(do (reset! *filter-text %)
                                     (reset! *warning-message nil))]
              [close-button/close-button
               :on-click    #(reset! *filter-text "")
               :div-size    0
               :font-size   20
               :left-offset -13]]])


(defn group-heading-item
  "Render a group heading and set up appropriate mouse events"
  []
  (let [*mouse-over? (reagent/atom false)]
    (fn group-heading-render
      [& {:keys [heading disabled? click-callback double-click-callback selected-item-id]}]
      (let [id        (:id heading)
            selected? (= selected-item-id id)
            class     (if selected?
                        "highlighted"
                        (when @*mouse-over? "mouseover"))]
        [:li.group-result
         {:class           class
          :style           {:padding-left "6px"
                            :cursor       (when-not disabled? "pointer")
                            :color        (if selected? "white" "#444")}
          :on-mouse-over   (handler-fn (reset! *mouse-over? true))
          :on-mouse-out    (handler-fn (reset! *mouse-over? false))
          :on-click        (when-not disabled? (handler-fn (click-callback id true))) ;; true = group-heading item selected
          :on-double-click (when-not disabled? (handler-fn (double-click-callback id)))}
         (:group heading)]))))


(defn list-item
  "Render a list item and set up appropriate mouse events"
  []
  (let [*mouse-over? (reagent/atom false)]
    (fn list-item-render
      [& {:keys [item id-fn label-fn disabled? click-callback double-click-callback selected-item-id group-selected?]}]
      (let [id              (id-fn item)
            selected?       (= id selected-item-id)
            class           (if selected?
                              "highlighted"
                              (when @*mouse-over? "mouseover"))]
        [:li
         {:class           (str "active-result group-option " class)
          :style           (merge (when group-selected? {:background-color "hsl(208, 56%, 92%)"})
                                  (when disabled? {:cursor "default"}))
          :on-mouse-over   (handler-fn (reset! *mouse-over? true))
          :on-mouse-out    (handler-fn (reset! *mouse-over? false))
          :on-click        (when-not disabled? (handler-fn (click-callback id false))) ;; false = group-heading item NOT selected
          :on-double-click (when-not disabled? (handler-fn (double-click-callback id)))}
         (label-fn item)]))))


(defn list-box
  "Render a list box which can be a single list or a grouped list"
  [& {:keys [items id-fn label-fn group-fn disabled? *current-item-id group-heading-selected? click-callback double-click-callback filter-choices-text]}]
  (let [[group-names group-item-lists] (items-with-group-headings items group-fn id-fn)
        has-group-names?               (not (and (nil? (:group (first group-names))) (= 1 (count group-item-lists)))) ;; if 0 or 1 group names, no headings to display
        make-list-item                 (fn [item]
                                         ^{:key (str (id-fn item))}
                                         [list-item
                                          :item                  item
                                          :id-fn                 id-fn
                                          :label-fn              label-fn
                                          :disabled?             disabled?
                                          :click-callback        click-callback
                                          :double-click-callback double-click-callback
                                          :selected-item-id      @*current-item-id
                                          :group-selected?       (when group-heading-selected? (= (first @*current-item-id) (group-fn item)))]) ;; for group headings, group-label is the first item in the vector
        make-items                     (fn [items] (doall (map make-list-item items)))
        make-group-heading-item        (fn [heading]
                                         ^{:key (:id heading)}
                                         [group-heading-item
                                          :heading               heading
                                          :disabled?             disabled?
                                          :click-callback        click-callback
                                          :double-click-callback double-click-callback
                                          :selected-item-id      @*current-item-id])
        make-heading-then-items        (fn [heading items]
                                         (cons (make-group-heading-item heading) (make-items items)))]
    [box/box
     :size  "1"
     :class (if disabled? "bm-multi-select-list-disabled" "bm-multi-select-list")
     :style {:background-color "#fafafa"
             :border           "1px solid #ccc"
             :border-radius    "4px"}
     :child [:ul.chosen-results
             {:style {:max-height "none"}} ;; Override the 240px in the class
             (if (-> items count pos?)
               (if has-group-names?
                 (apply concat (doall (map make-heading-then-items group-names group-item-lists)))
                 (make-items (first group-item-lists)))
               (if (string/blank? filter-choices-text)
                 ""
                 [:li.no-results (str "No results match \"" filter-choices-text "\"")]))]]))


;;--------------------------------------------------------------------------------------------------
;; Component: multi-select
;;--------------------------------------------------------------------------------------------------

;; Set of candidates (sorted externally)
;; a set of ids

;; LHS: set of candidates with selected id set removed, sorted/grouped by fn
;; RHS: set of candidates selecting on id, sorted/grouped by fn

(def multi-select-args-desc
  [{:name :choices            :required true                      :type "vector of choices | atom"          :validate-fn validate/vector-of-maps?   :description [:span "Each is expected to have an id, label and, optionally, a group, provided by " [:code ":id-fn"] ", " [:code ":label-fn"] " & " [:code ":group-fn"]]}
   {:name :model              :required true                      :type "a set of ids from choices | atom"                                          :description [:span "a set of selected ids from choice. If nil, " [:code ":placeholder"] " text is shown"]}
   {:name :required?          :required false :default false      :type "boolean | atom"                                                            :description "when true, at least one item must be selected"}
   {:name :max-selected-items :required false :default nil        :type "integer"                                                                   :description "maximum number of items allowed to be transferred from the left list to the right list"}
   {:name :left-label         :required false                     :type "string | hiccup"                   :validate-fn string-or-hiccup?          :description "heading for the left list"}
   {:name :right-label        :required false                     :type "string | hiccup"                   :validate-fn string-or-hiccup?          :description "heading for the right list"}
   {:name :on-change          :required true                      :type "id -> nil"                         :validate-fn fn?                        :description [:span "called when a new choice is selected. Passed the id of new choice"]}
   {:name :id-fn              :required false :default :id        :type "choice -> anything"                :validate-fn ifn?                       :description [:span "given an element of " [:code ":choices"] ", returns its unique identifier (aka id)"]}
   {:name :label-fn           :required false :default :label     :type "choice -> string | hiccup"         :validate-fn ifn?                       :description [:span "given an element of " [:code ":choices"] ", returns its displayable label"]}
   {:name :group-fn           :required false :default :group     :type "choice -> anything"                :validate-fn ifn?                       :description [:span "given an element of " [:code ":choices"] ", returns its group identifier"]}
   {:name :sort-fn            :required false :default "identity" :type "choice -> anything"                :validate-fn ifn?                       :description "Sorting function"}
   {:name :disabled?          :required false :default false      :type "boolean | atom"                                                            :description "if true, no user selection is allowed"}
   {:name :filter-box?        :required false :default false      :type "boolean"                                                                   :description "if true, a filter text field is placed at the bottom of the component"}
   {:name :regex-filter?      :required false :default false      :type "boolean | atom"                                                            :description "if true, the filter text field will support JavaScript regular expressions. If false, just plain text"}
   {:name :placeholder        :required false                     :type "string"                            :validate-fn string?                    :description "background text when no selection"}
   {:name :width              :required false :default "100%"     :type "string"                            :validate-fn string?                    :description "the CSS width. e.g.: \"500px\" or \"20em\""}
   {:name :height             :required false                     :type "string"                            :validate-fn string?                    :description "the specific height of the component"}
   {:name :max-height         :required false                     :type "string"                            :validate-fn string?                    :description "the maximum height of the component"}
   {:name :tab-index          :required false                     :type "integer | string"                  :validate-fn validate/number-or-string? :description "component's tabindex. A value of -1 removes from order"}
   {:name :class              :required false                     :type "string"                            :validate-fn string?                    :description "CSS class names, space separated"}
   {:name :style              :required false                     :type "CSS style map"                     :validate-fn validate/css-style?        :description "CSS styles to add or override"}
   {:name :attr               :required false                     :type "HTML attr map"                     :validate-fn validate/html-attr?        :description [:span "HTML attributes, like " [:code ":on-mouse-move"] [:br] "No " [:code ":class"] " or " [:code ":style"] "allowed"]}])


(defn multi-select
  "Render a multi-select component which emulates the bootstrap-choosen style. Sample choices object:
  [{:id \"AU\" :label \"Australia\"      :group \"Group 1\"}
   {:id \"US\" :label \"United States\"  :group \"Group 1\"}
   {:id \"GB\" :label \"United Kingdom\" :group \"Group 1\"}
   {:id \"AF\" :label \"Afghanistan\"    :group \"Group 2\"}]"
  [& {:keys [model sort-fn]
      :or   {sort-fn identity}
      :as   args}]
  "Internal glossary:
  LHS - choices    - comes from choices                 - the full list of items to select from
  RHS - selections - comes from model => internal-model - the selected items from choices collection
  "
  ;{:pre [(validate/validate-args-macro multi-select-args-desc args "multi-select")]}
  (let [*external-model                    (reagent/atom (rc.util/deref-or-value model)) ;; Holds the last known external value of model, to detect external model changes
        *internal-model                    (reagent/atom @*external-model) ;; Create a new atom from the model to be used internally
        *current-choice-id                 (reagent/atom nil)
        *current-selection-id              (reagent/atom nil)
        *choice-group-heading-selected?    (reagent/atom false)
        *selection-group-heading-selected? (reagent/atom false)
        *warning-message                   (reagent/atom nil)
        *filter-choices-text               (reagent/atom "")
        *filter-selections-text            (reagent/atom "")
        button-style                       {:width        "86px"
                                            :height       "24px"
                                            :padding      "0px 8px 2px 8px"
                                            :margin       "8px 6px"
                                            :text-align   "left"
                                            :font-variant "small-caps"
                                            :font-size    11}]
    (fn [& {:keys [choices model required? max-selected-items left-label right-label on-change disabled? filter-box? regex-filter?
                   placeholder width height max-height tab-index id-fn label-fn group-fn sort-fn class style attr]
            :or   {id-fn     :id
                   label-fn  :label
                   group-fn  :group
                   sort-fn   identity
                   required? false}
            :as   args}]
      {:pre [(validate-args-macro multi-select-args-desc args "multi-select")]}
      (let [min-msg                "Must have at least one"
            max-msg                (str "Max items allowed is " max-selected-items)
            group-fn               (or group-fn ::$$$) ;; TODO: If nil is passed because of a when, this will prevent exceptions...smelly!
            choices                (set (rc.util/deref-or-value choices))
            disabled?              (rc.util/deref-or-value disabled?)
            regex-filter?          (rc.util/deref-or-value regex-filter?)
            *latest-ext-model      (reagent/atom (rc.util/deref-or-value model))
            _                      (when (not= @*external-model @*latest-ext-model) ;; Has model changed externally?
                                     (reset! *external-model @*latest-ext-model)
                                     (reset! *internal-model @*latest-ext-model))
            changeable?            (and on-change (not disabled?))
            excludable?            (and @*current-selection-id (> (count @*internal-model) (if required? 1 0)))
            choices-filter-fn      (if regex-filter?
                                     (filter-items-regex group-fn label-fn @*filter-choices-text)
                                     (filter-items group-fn label-fn @*filter-choices-text))
            filtered-choices       (into []
                                         (->> choices
                                              (remove #(contains? @*internal-model (id-fn %)))
                                              (filter choices-filter-fn)
                                              (sort-by sort-fn)))
            selections             (into []
                                         (->> @*internal-model
                                              (map #(rc.util/item-for-id % choices :id-fn id-fn))
                                              (sort-by sort-fn)))
            selections-filter-fn   (if regex-filter?
                                     (filter-items-regex group-fn label-fn @*filter-selections-text)
                                     (filter-items group-fn label-fn @*filter-selections-text))
            filtered-selections    (into []
                                         (->> selections
                                              (filter selections-filter-fn)
                                              (sort-by sort-fn)))
            potential-count        (->> @*internal-model
                                        (set/difference (set (map id-fn choices)))
                                        count)
            chosen-count           (count selections)
            choice-click           (fn [id group-heading-selected?]
                                     (reset! *current-choice-id id)
                                     (reset! *choice-group-heading-selected? group-heading-selected?)
                                     (reset! *warning-message nil))
            selection-click        (fn [id group-heading-selected?]
                                     (reset! *current-selection-id id)
                                     (reset! *selection-group-heading-selected? group-heading-selected?)
                                     (reset! *warning-message nil))
            include-filtered-click #(do (if (and (some? max-selected-items) (> (+ (count @*internal-model) (count filtered-choices)) max-selected-items))
                                          (reset! *warning-message max-msg)
                                          (do
                                            (reset! *internal-model (set (concat @*internal-model (map id-fn filtered-choices))))
                                            (reset! *warning-message nil)))
                                        (when (and changeable? (not= @*internal-model @*latest-ext-model))
                                          (reset! *external-model @*internal-model)
                                          (on-change @*internal-model))
                                        (reset! *current-choice-id nil))
            include-click          #(do (if @*choice-group-heading-selected?
                                          (let [choices-to-include (->> filtered-choices
                                                                        (filter (fn [item] (= (first @*current-choice-id) (group-fn item))))
                                                                        (map id-fn) ;; TODO: Need to realise map output for prod build (dev doesn't need it). Why?
                                                                        set)]       ;; TODO: See https://github.com/day8/apps-lib/issues/35
                                            (if (and (some? max-selected-items) (> (+ (count @*internal-model) (count choices-to-include)) max-selected-items))
                                              (reset! *warning-message max-msg)
                                              (do
                                                (reset! *internal-model (set (concat @*internal-model choices-to-include)))
                                                (reset! *choice-group-heading-selected? false))))
                                          (if (and (some? max-selected-items) (>= (count @*internal-model) max-selected-items))
                                            (reset! *warning-message max-msg)
                                            (do
                                              (swap! *internal-model conj @*current-choice-id)
                                              (reset! *warning-message nil))))
                                        (when (and changeable? (not= @*internal-model @*latest-ext-model))
                                          (reset! *external-model @*internal-model)
                                          (on-change @*internal-model))
                                        (reset! *current-choice-id nil))
            exclude-click          #(do (if excludable?
                                          (if @*selection-group-heading-selected?
                                            (let [new-internal-model (->> filtered-selections
                                                                          (filter (fn [item] (= (first @*current-selection-id) (group-fn item))))
                                                                          (map id-fn)
                                                                          set
                                                                          (set/difference @*internal-model))]
                                              (if (and required? (empty? new-internal-model))
                                                (do
                                                  (reset! *internal-model (hash-set (first @*internal-model)))
                                                  (reset! *warning-message min-msg))
                                                (do
                                                  (reset! *internal-model new-internal-model)
                                                  (reset! *selection-group-heading-selected? false)
                                                  (reset! *warning-message nil))))
                                            (do
                                              (swap! *internal-model disj @*current-selection-id)
                                              (reset! *warning-message nil)))
                                          (reset! *warning-message min-msg))
                                        (when (and changeable? (not= @*internal-model @*latest-ext-model))
                                          (reset! *external-model @*internal-model)
                                          (on-change @*internal-model))
                                        (reset! *current-selection-id nil))
            exclude-filtered-click #(let [new-internal-model (set/difference @*internal-model (set (map id-fn filtered-selections)))]
                                      (if (and required? (zero? (count new-internal-model)))
                                        (do
                                          (reset! *internal-model (hash-set (first @*internal-model)))
                                          (reset! *warning-message min-msg))
                                        (do
                                          (reset! *internal-model new-internal-model)
                                          (reset! *warning-message nil)))
                                      (when (and changeable? (not= @*internal-model @*latest-ext-model))
                                        (reset! *external-model @*internal-model)
                                        (on-change @*internal-model))
                                      (reset! *current-selection-id nil))]
        [:div ;; TODO: Convert to box ?
         (merge
           {:class (str "rc-multi-select noselect chosen-container chosen-container-single " class)
            :style (merge (box/flex-child-style (if width "0 0 auto" "auto"))
                          (box/align-style :align-self :start)
                          {:overflow "hidden"
                           :width    width}
                          style)}
           attr) ;; Prevent user text selection
         [box/h-box
          :height     height
          :max-height max-height
          :gap        "4px"
          :children   [[box/v-box
                        :size     "50%"
                        :gap      "4px"
                        :children [(when left-label
                                     (if (string? left-label)
                                       [box/h-box
                                        :justify  :between
                                        :children [[:span
                                                    {:style {:font-size   "small"
                                                             :font-weight "bold"}}
                                                    left-label]
                                                   [:span
                                                    {:style {:font-size "smaller"}}
                                                    (if (string/blank? @*filter-choices-text)
                                                      (rc.util/pluralize potential-count "item")
                                                      (str "showing " (count filtered-choices) " of " potential-count))]]]
                                       left-label))
                                   [list-box
                                    :items                   filtered-choices
                                    :id-fn                   id-fn
                                    :label-fn                label-fn
                                    :group-fn                group-fn
                                    :disabled?               disabled?
                                    :*current-item-id        *current-choice-id
                                    :group-heading-selected? @*choice-group-heading-selected?
                                    :click-callback          choice-click
                                    :double-click-callback   include-click
                                    :filter-choices-text     @*filter-choices-text]
                                   (when filter-box?
                                     [:<>
                                      [box/gap :size "4px"]
                                      [filter-text-box *filter-choices-text placeholder *warning-message disabled?]
                                      [box/gap :size "4px"]
                                      (if (string/blank? @*filter-choices-text)
                                        [text/label
                                         :label (gstring/unescapeEntities "&nbsp;")
                                         :style {:font-size "smaller"}]
                                        [text/label
                                         :label [:span "Found " (rc.util/pluralize (count filtered-choices) "match" "matches") " containing " [:strong @*filter-choices-text]]
                                         :style {:font-size "smaller"}])])]]
                       [box/v-box
                        :justify  :between
                        :children [[box/box
                                    :size "0 1 22px" ;; 22 = (+ 18 4) - height of the top components
                                    ;:style {:background-color "lightblue"}
                                    :child ""]
                                   [box/v-box
                                    :justify  :center
                                    ;:style {:background-color "lightgreen"}
                                    :children [[buttons/button
                                                :label     [:span
                                                            [:i {:class (str "zmdi zmdi-hc-fw-rc zmdi-fast-forward")}]
                                                            [:span
                                                             {:style {:position "relative" :top "-1px"}}
                                                             (str " include " (if (string/blank? @*filter-choices-text) potential-count (count filtered-choices)))]]
                                                :disabled? (or disabled? (zero? (count filtered-choices)))
                                                :style     button-style
                                                :on-click  include-filtered-click]
                                               [buttons/button
                                                :label     [:span
                                                            [:i {:class (str "zmdi zmdi-hc-fw-rc zmdi-play")}]
                                                            [:span
                                                             {:style {:position "relative" :top "-1px"}}
                                                             (str " include " (when @*choice-group-heading-selected?
                                                                                (->> filtered-choices ;; TODO: Inefficient
                                                                                     (filter (fn [item] (= (first @*current-choice-id) (group-fn item))))
                                                                                     count)))]]
                                                :disabled? (or disabled? (not @*current-choice-id))
                                                :style     button-style
                                                :on-click  include-click]
                                               [buttons/button
                                                :label     [:span
                                                            [:i {:class (str "zmdi zmdi-hc-fw-rc zmdi-play zmdi-hc-rotate-180")}]
                                                            [:span
                                                             {:style {:position "relative" :top "-1px"}}
                                                             (str " exclude " (when @*selection-group-heading-selected?
                                                                                (->> filtered-selections ;; TODO: Inefficient
                                                                                     (filter (fn [item] (= (first @*current-selection-id) (group-fn item))))
                                                                                     count)))]]
                                                :disabled? (or disabled? (not excludable?))
                                                :style     button-style
                                                :on-click  exclude-click]
                                               [buttons/button
                                                :label     [:span
                                                            [:i {:class (str "zmdi zmdi-hc-fw-rc zmdi-fast-rewind")}]
                                                            [:span
                                                             {:style {:position "relative" :top "-1px"}}
                                                             (str " exclude " (if (string/blank? @*filter-selections-text) chosen-count (count filtered-selections)))]]
                                                :disabled? (or disabled? (zero? (count filtered-selections)) (not (> (count @*internal-model) (if required? 1 0))))
                                                :style     button-style
                                                :on-click  exclude-filtered-click]]]
                                   [box/box
                                    :size (str "0 2 " (if filter-box? "55px" "0px")) ;; 55 = (+ 4 4 28 4 15) - height of the bottom components
                                    ;:style {:background-color "lightblue"}
                                    :child ""]]]
                       [box/v-box
                        :size     "50%"
                        :gap      "4px"
                        :style    {:position "relative"}
                        :children [^{:key (gensym)}
                                   [text/label
                                    :label @*warning-message
                                    :style (when @*warning-message
                                             {:color            "white"
                                              :background-color "green"
                                              :border-radius    "0px"
                                              :opacity            "0"
                                              :position           "absolute"
                                              :right              "0px"
                                              :z-index            1
                                              :height             "25px"
                                              :padding            "3px 6px"
                                              :animation-name     "rc-multi-select-fade-warning-msg"
                                              :animation-duration "5000ms"})]
                                   (when right-label
                                     (if (string? right-label)
                                       [box/h-box
                                        :justify  :between
                                        :children [[:span
                                                    {:style {:font-size "small"
                                                             :font-weight "bold"}}
                                                    right-label]
                                                   [:span
                                                    {:style {:font-size "smaller"}}
                                                    (if (string/blank? @*filter-selections-text)
                                                      (rc.util/pluralize chosen-count "item")
                                                      (str "showing " (count filtered-selections) " of " chosen-count))]]]
                                       right-label))
                                   [list-box
                                    :items                   filtered-selections
                                    :id-fn                   id-fn
                                    :label-fn                label-fn
                                    :group-fn                group-fn
                                    :disabled?               disabled?
                                    :*current-item-id        *current-selection-id
                                    :group-heading-selected? @*selection-group-heading-selected?
                                    :click-callback          selection-click
                                    :double-click-callback   exclude-click
                                    :filter-choices-text     @*filter-selections-text]
                                   (when filter-box?
                                     [:<>
                                      [box/gap :size "4px"]
                                      [filter-text-box *filter-selections-text placeholder *warning-message disabled?]
                                      [box/gap :size "4px"]
                                      (if (string/blank? @*filter-selections-text)
                                        [text/label
                                         :label (gstring/unescapeEntities "&nbsp;")
                                         :style {:font-size "smaller"}]
                                        [text/label
                                         :label [:span "Found " (rc.util/pluralize (count filtered-selections) "match" "matches") " containing " [:strong @*filter-selections-text]]
                                         :style {:font-size "smaller"}])])]]]]]))))