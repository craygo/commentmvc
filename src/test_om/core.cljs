(ns test-om.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! <! chan]]
            [test-om.utils :refer [guid]]
            [clojure.browser.net :as net]
            [clojure.browser.event :as gevent]
            [clojure.browser.repl :as repl]
            [rohm.core :as rohm :include-macros true] 
            ))

;; state
(def app-state (atom {:url "comments.json" :poll-interval 10000 
                      :comments [{:id (guid) :author "a1" :text "t1"} {:id (guid) :author "a2" :text "t2"}]}))

;; validator
;; can reject changes that would violate global constraints
(defn valid? [state]
  (and (->> state :comments (filter #(:editing %)) count (>= 1)) ; only a single comment may be editing at any time
       ; ...
       ))

(set-validator! app-state valid?)

;; Pedestal style input message transformer functions
(defn start-edit [old-value message]
  (assoc old-value :editing true))

(defn update-comment [old-value {:keys [author] :as message}]
  (dissoc (assoc old-value :author author) :editing))

(defn add-comment [old-value {:keys [author text] :as message}]
  (conj old-value {:id (guid) :author author :text text}))

(defn reset-comments [_ message]
  (:comments message)
  )

;; Pedestal style routes
(def routes [[:start-edit [:comments :*] start-edit]
             [:update-comment [:comments :*] update-comment]
             [:add [:comments] add-comment]
             [:reset [:comments] reset-comments]
             ])

;; Pedestal style effect functions
;; return collection of messages for the effect queue
(defn get-server-comments-effect [url]
  [{:type :read :topic [:comments] :url url}])

;; components
(def ENTER-KEY 13)

(defn comment-form [comments]
  (letfn [(handle-submit [_ owner]
            (rohm/put-msg :add comments (rohm/extract-refs owner))
            false)]
    (rohm/component-o
      (dom/form #js {:className "commentForm" :onSubmit #(handle-submit % owner)}
                (dom/input #js {:ref "author" :type "text" :placeholder "Your name" })
                (dom/input #js {:ref "text" :type "text" :placeholder "Say something..."})
                (dom/input #js {:type "submit" :value "Post"})))))

(defn comment [cmt]
  (letfn [(update [e cmt owner] 
            (when (= (.-which e) ENTER-KEY)
              (let [node (om/get-node owner "edit")]
                (rohm/put-msg :update-comment cmt {:author (.-value node)}))))]
    (rohm/component-o
      (dom/div #js {:className "comment"}
               (dom/h2 #js {:ref "author" :onClick #(rohm/put-msg :start-edit cmt) } 
                       (if-not (:editing cmt) 
                         (:author cmt)
                         (dom/input #js {:ref "edit" :onKeyDown #(update % cmt owner) :defaultValue (:author cmt)})))
               (dom/span nil (:text cmt))))))

(defn comment-list [comments]
  (om/component
    (dom/div #js {:className "commentList"}
             (rohm/list-of comment comments))))

(defn fail [ev]
  (.error js/console "fail: " ev))

(defn comment-box [comments]
  (om/component
    (dom/div #js {:className "commentBox"} 
             (dom/h1 nil "Comments")
             (om/build comment-list comments)
             (om/build comment-form comments))))

;; service, can go into separate namespace
;; input-queue not used directly but via rohm/put-msg
(defn comment-service [message input-queue]
  (letfn [(server-res [ev]
            (let [res (js->clj (.getResponseJson (.-target ev)) :keywordize-keys true)
                  res (vec (map #(assoc % :id (guid)) res))]
              (rohm/put-msg :reset [:comments] {:comments res})))
          (get-from-server [url]
            (let [xhr (net/xhr-connection)]
              (gevent/listen xhr "success" server-res)
              (gevent/listen xhr "error" fail)
              (net/transmit xhr url)))]
    ; need some kind of routing here too
    (if (and (= (:type message) :read) (:url message))
      (get-from-server (:url message))
      (.warn js/console (pr-str "comment-service: don't know " message)))))

(defn comment-app [app]
  (reify
    om/IWillMount
    (will-mount [this owner]
      (let [{:keys [url poll-interval]} app]
        (rohm/effect-messages (get-server-comments-effect url))
        (js/setInterval #(rohm/effect-messages (get-server-comments-effect %)) poll-interval url)
        (rohm/handle-messages app-state routes comment-service)
        (repl/connect "http://localhost:9000/repl")))
    om/IRender
    (render [_ _]
      (om/build comment-box app {:path [:comments]}))))

(om/root app-state comment-app (.getElementById js/document "container"))
