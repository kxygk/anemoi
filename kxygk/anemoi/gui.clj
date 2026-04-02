(ns kxygk.anemoi.gui
  "A GUI template using `cljfx` for the GUI,
  and Pathom for the state managment"
  (:require #_[promesa.core :as promesa]
            [kxygk.anemoi.central :as central]
            kxygk.pathmore.cache
            kxygk.pathomfx.core
            [cljfx.api :as fx]))

(defn ui-root
  [{:keys [value]}]
  {:fx/type :stage
   :showing true
   :scene   {:fx/type :scene
             :root    {:fx/type  :v-box
                       :children [{:fx/type        kxygk.pathomfx.core/pathprom
                                   :env            central/env
                                   :inputmap       value
                                   :tx             [::central/birth-year]
                                   :loading-ui     {:fx/type :label
                                                    :text    "Loading..."}
                                   :realized-ui-fn (fn [pathom-map]
                                                     {:fx/type :label
                                                      :text    (str "Born: "
                                                                    (-> pathom-map
                                                                        ::central/birth-year))})}
                                  {:fx/type   :button
                                   :text      "Next User"
                                   :on-action (fn [_]
                                                (swap! central/*state
                                                       update
                                                       ::central/id
                                                       #(if (= %
                                                               3)
                                                          1
                                                          (inc %))))}]}}})

(defn root-state-watcher
  "This `fx/ext-watcher` is an element that just watches an IRef.
  In this case it's watching our core state.
  This setup obviates the need for a renderer.
  You could have parts of the UI tree have their own states/`fx/ext-watcher`"
  [{:keys [state]}]
  {:fx/type fx/ext-watcher
   :ref     state
   :desc    {:fx/type ui-root}})

(def app
  (-> {:fx/type root-state-watcher
       :state   central/*state}
      fx/create-component
      fx/on-fx-thread))
