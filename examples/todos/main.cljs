(ns todos.main
  (:require [dar.ui :refer [render!]]
            [dar.ui.dom :as dom :refer [to to*]]
            [dar.ui.frp :as frp])
  (:require-macros [dar.ui.dom.elements :refer [DIV H1 UL LI INPUT LABEL BUTTON HEADER SECTION]]
                   [dar.ui.dom.macro :refer [event!]]
                   [dar.ui.frp :refer [transform]]))

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
                       (assoc-in todos [id :completed?] completed?))}
            commands))

(defn todo-item-sf [todo]
  (let [editing? (frp/new-signal false)]
    (transform [[id {:keys [text completed?]}] todo
                e? editing?]
      (LI {:key id
           :class (dom/classes :completed completed? :editing e?)}
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
              enter enter-new]
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
        (SECTION {:id "main"}
          (UL {:id "todo-list"}
            [items]))))))

(defn -main []
  (render! main (.getElementById js/document "todoapp")))

(event! ::ev-text :keydown (fn [e]
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
                                      (dom/tick #(.select el))))})
