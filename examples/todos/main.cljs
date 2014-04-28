(ns todos.main
  (:require [dar.ui :refer [render!]]
            [dar.ui.dom :as dom :refer [to to* classes]]
            [dar.ui.dom.util :refer [tick]]
            [dar.ui.frp :as frp])
  (:require-macros [dar.ui.frp :refer [transform]]
                   [dar.ui.dom :as dom]
                   [dar.ui.dom.elements :refer [DIV SPAN A H1 UL LI INPUT LABEL BUTTON HEADER SECTION FOOTER STRONG]]))

(enable-console-print!)

(def commands (frp/new-event))

(def todos (frp/automaton
            {}
            {:new (fn [todos text]
                    (let [id (js/Date.now)]
                      (assoc todos id {:text text
                                       :completed? false})))

             :delete (fn [todos id]
                       (dissoc todos id))

             :change-text (fn [todos id text]
                            (assoc-in todos [id :text] text))

             :toggle (fn [todos id completed?]
                       (assoc-in todos [id :completed?] completed?))

             :toggle-all (fn [todos completed?]
                           (reduce (fn [todos k]
                                     (assoc-in todos [k :completed?] completed?))
                                   todos
                                   (keys todos)))

             :clear-completed (fn [todos]
                                (reduce (fn [todos [k {completed? :completed?}]]
                                          (if completed?
                                            (dissoc todos k)
                                            todos))
                                        todos todos))}
            commands))

(def stats (transform [todos todos]
             (let [completed (count (filter #(-> % second :completed?) todos))
                   all (count todos)]
               {:all-completed? (= all completed)
                :all all
                :completed completed
                :left (- all completed)})))

(def mode (frp/new-signal :all))

(defn todo-item-sf [todo]
  (let [editing? (frp/new-signal false)]
    (transform [[id {:keys [text completed?]}] todo
                e? editing?
                mode mode]
      (LI {:key id
           :class (classes :completed completed? :editing e?
                           :hidden (or (and completed? (= mode :active))
                                       (and (not completed?) (= mode :completed))))}
        (DIV {:class "view"}
          (INPUT {:class "toggle" :type "checkbox"
                  :value completed?
                  :ev-change (to commands (fn [c?]
                                            [:toggle id c?]))})
          (LABEL {:ev-dblclick (to editing? true)}
            text)
          (BUTTON {:class "destroy" :ev-click (to commands [:delete id])}))
        (INPUT {:class "edit"
                ::enter [text e?]
                ::ev-text (to* (fn [text]
                                 [(if text
                                    [commands [:change-text id text]])
                                  [editing? false]]))})))))

(def enter-new (frp/new-event))

(def main
  (transform [items (frp/map-switch todo-item-sf todos)
              enter enter-new
              {:keys [left all-completed? all completed]} stats
              mode mode]
    (let [items (->> items (sort-by first) (map second))]
      (SECTION {:id "todoapp"}
        (HEADER {:id "header"}
          (H1 "todos")
          (INPUT {:id "new-todo"
                  :placeholder "What needs to be done?"
                  :autofocus true
                  ::enter enter
                  ::ev-text (to* (fn [text]
                                   [(if text
                                      [commands [:new text]])
                                    [enter-new ["" true]]]))}))

        (SECTION {:id "main" :class (classes :hidden (= all 0))}
          (INPUT {:id "toggle-all" :type "checkbox"
                  :value all-completed?
                  :ev-change (to commands (fn [c?]
                                            [:toggle-all c?]))})
          (UL {:id "todo-list"}
            [items]))

        (FOOTER {:id "footer" :class (classes :hidden (= all 0))}
          (SPAN {:id "todo-count"}
            (STRONG nil (str left))
            (if (= left 1)
              " item left"
              " items left"))
          (UL {:id "filters"}
            (filter-link mode :all "All")
            (filter-link mode :active "Active")
            (filter-link mode :completed "Completed"))
          (BUTTON {:id "clear-completed"
                   :class (classes :hidden (= 0 completed))
                   :ev-click (to commands [:clear-completed])}
            (str "Clear completed (" completed ")")))))))

(defn filter-link [m type text]
  (LI nil
    (A {:href "#"
        :class (classes :selected (= m type))
        :ev-click (to mode type)}
      text)))

(defn -main []
  (render! main (.getElementById js/document "todoapp")))

(dom/install-event! ::ev-text :keydown (fn [e]
                                         (let [key (.-keyCode e)
                                               el (.-target e)
                                               text (-> el .-value .trim)]
                                           (condp = key
                                             13 text
                                             27 false
                                             nil))))

(dom/install-plugin! ::enter {:on (fn [el [text focus?]]
                                    (set! (.-value el) text)
                                    (when focus?
                                      (tick #(.select el))))})
