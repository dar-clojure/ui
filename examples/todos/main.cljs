(ns todos.main
  (:require [dar.ui :refer [render!]]
            [dar.ui.dom :as dom]
            [dar.ui.frp :as frp])
   (:require-macros [dar.ui.dom.elements :refer [DIV LI INPUT LABEL BUTTON HEADER SECTION]]))

(def commands (new-event))

(def todos (frp/state-machine
            commands
            {}
            {:new (fn [todos text]
                    (let [id (js/Date.)]
                      (assoc todos id {:id id
                                       :text text
                                       :completed? false})))

             :delete (fn [todos id]
                       (dissoc todos id))

             :change-text (fn [todos id text]
                            (assoc-in todos [id :text] text))

             :toggle (fn [todos id completed?]
                       (assoc-in todos [id :completed?] completed?))}))

(defn todo-item-sf [todo]
  (let [editing? (new-signal false)]
    (transform [{:keys [id text completed?]} todo
                e? editing?
                input-focus (to-event editing?)]
      (LI {:class (dom/classes :completed completed? :editing e?)}
        (DIV {:class "view"}
          (INPUT {:class "toggle" :type "checkbox"
                  :checked completed?
                  :ev-change (to commands (fn [c?]
                                            [:toggle id c?]))})
          (LABEL {:ev-dblclink (to editing? true)}
            text))
        (INPUT {:class "edit"
                :value text
                :focus input-focus
                ::text (to commands (fn [text]
                                      [:change-text id text]))})))))

(def main
  (transform [todo-items (-> (map-switch todo-item-sf todos)
                             ((lift second))
                             ((lift (partial sort :id))))]
    (SECTION {:id "todoapp"}
      (HEADER {:id "header"}
        (H1 "todos")
        (INPUT {:id "new-todo"
                :placeholder "What needs to be done?"
                :autofocus true
                ::text (to commands (fn [text]
                                      [:new-todo text]))}))
      (SECTION {:id "main"}
        (UL {:id "todo-list"}
          [todo-items])))))

(defn -main []
  (render! main (.getElementById js/document "todoapp")))
