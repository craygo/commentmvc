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

;; state
(def app-state (atom {:url "comments.json" :poll-interval 4000 
                      :comments [{:id (guid) :author "a1" :text "t1"} {:id (guid) :author "a2" :text "t2"}]}))

;; event handlers
(defn start-edit [old-value message]
  (assoc old-value :editing true))

(defn update-comment [old-value {:keys [author] :as message}]
  (dissoc (assoc old-value :author author) :editing))

(defn add-comment [old-value {:keys [author text] :as message}]
  ;(.info js/console (pr-str "add-comment " old-value message))
  (conj old-value {:id (guid) :author author :text text}))

(def routes [[:start-edit [:comments :*] start-edit]
             [:update-comment [:comments :*] update-comment]
             [:add-comment [:comments] add-comment]
             ])

;; TODO move to ohm
(def input-queue (chan))

(defn de-route [type topic]
  ; TODO implement topic based
  (-> (filter #(= (first %) type) routes) first last))

(defn handle-mesg [{:keys [type topic] :as mesg}]
  (let [old-value (get-in @app-state topic)]
    (if-let [func (de-route type topic)]
      (let [new-value (func old-value mesg)]
        (swap! app-state update-in topic (fn [_ nv] nv) new-value)
        )
      (.warn js/console (pr-str "handle-mesg: no func for " type topic)))))

(defn put-msg [type cursor & opts]
  ;(.info js/console (pr-str "put-msg " type cursor opts))
  (put! input-queue (merge {:type type :topic (:om.core/path (meta cursor))} opts)))

(defn extract-refs [owner]
  (let [ks (keys (js->clj (.-refs owner)))
        m (into {} (map #(vector (keyword %) (.-value (om/get-node owner %))) ks))]
    ;(.info js/console (pr-str "handle-submit " ks m))
    m))

;; components
(def ENTER-KEY 13)

(defn comment-form [comments]
  (letfn [(handle-submit [_ owner]
            (put-msg :add-comment comments (extract-refs owner))
            false)]
    (ohm/component-o
      (dom/form #js {:className "commentForm" :onSubmit #(handle-submit % owner)}
                (dom/input #js {:ref "author" :type "text" :placeholder "Your name" })
                (dom/input #js {:ref "text" :type "text" :placeholder "Say something..."})
                (dom/input #js {:type "submit" :value "Post"})))))

(defn comment [cmt]
  (letfn [(update [e cmt owner] 
            (when (= (.-which e) ENTER-KEY)
              (let [node (om/get-node owner "edit")]
                (put-msg :update-comment cmt {:author (.-value node)}))))]
    (ohm/component-o
      (dom/div #js {:className "comment"}
               (dom/h2 #js {:ref "author" :onClick #(put-msg :start-edit cmt) } 
                       (if-not (:editing cmt) 
                         (:author cmt)
                         (dom/input #js {:ref "edit" :onKeyDown #(update % cmt owner) :defaultValue (:author cmt)})))
               (dom/span nil (:text cmt))))))

(defn comment-list [comments]
  (om/component
    (dom/div #js {:className "commentList"}
             (ohm/list-of comment comments))))

(defn fail [ev]
  (.error js/console "fail: " ev))

(defn comment-box [app]
  ; TODO put in service
  (letfn [(server-res [ev]
            (let [res (js->clj (.getResponseJson (.-target ev)) :keywordize-keys true)
                  res (map #(assoc % :id (guid)) res)]
              (om/update! app [:comments] (comp vec (fn [a b] b)) res)))
          (get-from-server [url]
            (let [xhr (net/xhr-connection)]
              (gevent/listen xhr "success" server-res)
              (gevent/listen xhr "error" fail)
              (net/transmit xhr url))) ]
    (reify
      om/IWillMount
      (will-mount [this owner]
        (let [{:keys [url poll-interval]} app]
          (get-from-server url)
          (js/setInterval get-from-server poll-interval url)
          (go (while true
                (handle-mesg (<! input-queue))))))
      om/IRender
      (render [_ _]
        (dom/div #js {:className "commentBox"} 
                 (dom/h1 nil "Comments")
                 (om/build comment-list app {:path [:comments]})
                 (om/build comment-form app {:path [:comments]}))))))

(om/root app-state comment-box (.getElementById js/document "container"))
