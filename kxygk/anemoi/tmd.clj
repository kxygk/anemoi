(ns kxygk.anemoi.tmd
  "Stuff that's still too small to put in a separate lib"
  (:require [kxygk.anemoi.stat :as stat]
            ;;[kxygk.dripsplit.central :as central]
            kxygk.pathomfx.core
            [clojure.math]
            [clojure.string]
            [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.interface.smart-map :as psm]
            [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
            [promesa.core :as p]
            ;;            [tock]
            [tick.core                    :as tick]
            [tick.locale-en-us]
            [tick.alpha.interval          :as interval]
            [quickthing]
            [thi.ng.geom.viz.core :as viz]
            [thi.ng.geom.svg.core :as svg]
            [dk.ative.docjure.spreadsheet :as docjure]
            [tech.v3.dataset              :as ds]
            [tech.v3.libs.fastexcel]
            [tech.v3.dataset.join         :as tjoin]
            ))



(pco/defresolver $column-extractor
  [{::keys [table
            x-key
            y-key
            meta-keys]}]
  {::pco/input  [::table
                 ::x-key
                 ::y-key
                 (pco/? ::meta-keys)]
   ::pco/output [{:x [:data-vec]}
                 {:y [:data-vec]}
                 :meta]}
  {:x    {:data-vec (vec (get table
                              x-key))}
   :y    {:data-vec (vec (get table
                              y-key))}
   :meta (when (seq meta-keys)
           (mapv #(zipmap meta-keys
                          %)
                 (apply map
                        vector
                        (vals (select-keys table
                                           meta-keys)))))})

(pco/defresolver $table-column-filter
  [{::keys [table-to-filter
            col-filter-fn]}]
  {::pco/input   [::table-to-filter
                  ::col-filter-fn]
   :pco/priority 1
   ::pco/output  [::table]}
  (p/vthread (do (println "filtering column!")
                 {::table (ds/filter table-to-filter
                                     (fn [row-datapoint-map]
                                       (col-filter-fn row-datapoint-map)))})))

(pco/defresolver $vectorizer
  [{:keys [x
           y
           meta]}]
  {::pco/input  [{:x [:data-vec]}
                 {:y [:data-vec]}
                 :meta] ;; why is it not a `data-vec`?
   ::pco/output [:xy-all]}
  {:xy-all (if meta
             (mapv vector
                   (-> x
                       :data-vec)
                   (-> y
                       :data-vec)
                   meta)
             (mapv vector
                   (-> x
                       :data-vec)
                   (-> y
                       :data-vec)))})

(pco/defresolver $devectorizer
  [{:keys [xy-all]}]
  {::pco/inout   [:xy-all]
   ::pco/output  [{:x [:data-vec]}
                  {:y [:data-vec]}
                 :meta]}
  (let [[xs
         ys
         metas] (apply mapv
                       vector
                       xy-all)]
    {:x {:datavec xs}
     :y {:datavec ys}
     :meta metas}))
    

;; From ChatGPT...
;; Probably more performant,
;; but I don't understand it
#_
(defn split-triples [triples]
  (let [[xs ys zs]
        (reduce
          (fn [[xs ys zs] [x y z]]
            [(conj! xs x)
             (conj! ys y)
             (conj! zs z)])
          [(transient [])
           (transient [])
           (transient [])]
          triples)]
    [(persistent! xs)
     (persistent! ys)
     (persistent! zs)]))

(pco/defresolver $no-nil-filter
  [{:keys [xy-all]}]
  {::pco/input  [:xy-all]
   ::pco/output [:xy-nonil]}
  {:xy-nonil (filterv (fn [[x-coord
                            y-coord
                            ;; meta?
                            ]]
                        (and x-coord
                             y-coord))
                      xy-all)})

(def env
  (pci/register [$column-extractor
                 $table-column-filter
                 $vectorizer
                 $devectorizer
                 $no-nil-filter]))


