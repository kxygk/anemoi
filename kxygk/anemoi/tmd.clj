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
            y-key]}]
  {::pco/input  [::table
                 ::x-key
                 ::y-key]
   ::pco/output [{:x [:data-vec]}
                 {:y [:data-vec]}]}
  {:x {:data-vec (vec (get table
                           x-key))}
   :y {:data-vec (vec (get table
                           y-key))}})


(pco/defresolver $table-column-filter
  [{::keys [table-to-filter
            col-filter-fn]}]
  {::pco/input  [::table-to-filter
                 ::col-filter-fn]
   :pco/priority 1
   ::pco/output [::table]}
  (p/vthread (do (println "filtering column!")
                 {::table (ds/filter table-to-filter
                                     (fn [row-datapoint-map]
                                       (col-filter-fn row-datapoint-map)))})))

(pco/defresolver $vectorizer
  [{:keys [x
            y]}]
  {::pco/input  [:x
                 :y]
   ::pco/output [:xy-all]}
  {:xy-all (mapv vector
                 (-> x
                     :data-vec)
                 (-> y
                     :data-vec))})

(pco/defresolver $no-nil-filter
  [{:keys [xy-all]}]
  {::pco/input  [:xy-all]
   ::pco/output [:xy-nonil]}
  {:xy-nonil (filterv (fn [[x-coord
                             y-coord]]
                         (and x-coord
                              y-coord))
                      xy-all)})

(def env
  (pci/register [$column-extractor
                 $table-column-filter
                 $vectorizer
                 $no-nil-filter]))


