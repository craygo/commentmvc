(ns test-om.core
  (:require-macros [cljs.core.async.macros :refer [go alt!]]
                   [ohm.core :as ohm ])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! <! chan]]
            [test-om.utils :refer [guid]]
            [clojure.browser.net :as net]
            [clojure.browser.event :as gevent]
            ))


(def app-state (atom {:url "comments.json" :poll-interval 2000 
                      :comments [{:id (guid) :author "a1" :text "t1"} {:id (guid) :author "a2" :text "t2"}]}))

(defn comment-form [{:keys [add-comment ]}]
  (letfn [(handle-submit [ev owner]
            (let [author (.-value (om/get-node owner "author"))
                  text (.-value (om/get-node owner "text"))]
              (add-comment author text)
              false))]
    (ohm/component-o
      (dom/form #js {:className "commentForm" :onSubmit #(handle-submit % owner)}
                (dom/input #js {:ref "author" :type "text" :placeholder "Your name" })
                (dom/input #js {:ref "text" :type "text" :placeholder "Say something..."})
                (dom/input #js {:type "submit" :value "Post"})))))

(def ENTER-KEY 13)

(defn comment [m]
  (letfn [(start-edit [e m owner]
            (om/update! m [:editing] assoc true))
          (update [e m owner] 
            (when (= (.-which e) ENTER-KEY)
              (let [node (om/get-node owner "edit")
                    nm (dissoc (assoc m :author (.-value node)) :editing)]
                (om/update! m (constantly nm)))))]
    (ohm/component-o
      (dom/div #js {:className "comment"}
               (dom/h2 #js {:ref "author" :onClick #(start-edit % m owner) } 
                       (if-not (:editing m) 
                         (:author m)
                         (dom/input #js {:ref "edit" :onKeyDown #(update % m owner) :defaultValue (:author m)})))
               (dom/span nil (:text m))))))

#_(defn list-of [component list-in-model]
  (into-array (map #(om/build component list-in-model {:path [%] :key :id}) (range (count list-in-model)))))

(defn comment-list [comments]
  (om/component
    (dom/div #js {:className "commentList"}
             (ohm/list-of comment comments))))

(defn fail [ev]
  (.error js/console "fail: " ev))

(defn comment-box [app]
  (letfn [(server-res [ev]
            (let [res (js->clj (.getResponseJson (.-target ev)) :keywordize-keys true)
                  res (map #(assoc % :id (guid)) res)]
              (om/update! app [:comments] (comp vec (fn [a b] b)) res)))
          (get-from-server [url]
            (let [xhr (net/xhr-connection)]
              (gevent/listen xhr "success" server-res)
              (gevent/listen xhr "error" fail)
              (net/transmit xhr url)))
          (add-comment [author text]
            (om/update! app [:comments] conj {:id (guid) :author author :text text}))]
    (reify
      om/IWillMount
      (will-mount [this owner]
        (let [{:keys [url poll-interval]} app]
          (get-from-server url)
          (js/setInterval get-from-server poll-interval url)))
      om/IRender
      (render [_ _]
        (dom/div #js {:className "commentBox"} 
                 (dom/h1 nil "Comments")
                 (om/build comment-list app {:path [:comments]})
                 (om/build comment-form {:add-comment add-comment}))))))

(om/root app-state comment-box (.getElementById js/document "container"))
