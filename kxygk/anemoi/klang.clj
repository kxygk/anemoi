(ns kxygk.anemoi.klang
  (:require [kxygk.anemoi.enso :as enso]
            [kxygk.pathmore.core :as pathmore]
            kxygk.mathom.core
            ;;[kxygk.anemoi.index :as index]
            kxygk.anemoi.util
            [clojure.math]
            [clojure.string]
            [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.operation :as pco]
            ;;[com.wsscode.pathom3.interface.smart-map :as psm]
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

(pathmore/clean-ns!)

(def $state$
  "for testing only"
  {::filestr  (str "/home/kxygk/Data/Tan2019/"
                   "modern-part.csv")})

(pco/defresolver $klang-table
  [{::keys [filestr]}]
  {::pco/output [::table]}
  {::table (-> filestr
               (ds/->dataset {:dataset-name "Klang speleothem d18O"
                              :key-fn       kxygk.anemoi.util/normalize-colname})
               (ds/rename-columns [:Year-Decimal
                                   :d18O]))})
#_
(pathmore/check ::table)

(pco/defresolver $extract-table-columns
  "Extract the columsn from the table.
Note that unfortunately they can't be treated as collections directly b/c of bug
See: https://github.com/techascent/tech.ml.dataset/issues/479
So they need to coerced to `vec`"
  [{::keys [table]}] ;; Forwarded deeper to convert `Date` to `Days..` .. TODO make optional
  {::pco/output [{::data [{:Year-Decimal [:data|]}
                          {:d18O [:data|]}]}]}
  {::data (merge {} ;; injection point (unused)
                 (update-vals (into {} ;; turns it into a map of TMD cols
                                    table)
                              (fn convert-tmd-cols
                                [tmd-col]
                                {:data| (vec tmd-col)})))})  ;; turns TMD cols in to normal vecs
#_
(pathmore/check [{::data [:Year-Decimal]}])
#_
(pathmore/check [{::data [:Date]}])
#_
(-> (pathmore/check [{::data [:Date]}])
    ::data
    :Date
    :data|
    last)

(pco/defresolver $klang-year-d18O
  [{::keys [klang-table]}]
  {::pco/output [::klang-year-d18O]}
  (p/vthread {::klang-year-d18O (sort-by first
                                         (mapv vector
                                               (:Year klang-table)
                                               (:d18O klang-table)))}))

(def $resolvers$
  (->> [(pathmore/find-resolvers)
        kxygk.mathom.core/$resolvers$]
       flatten
       (mapv pathmore/inject-simple-cache)))

(def $env$
  (pci/register {::p.a.eql/parallel? true}
                $resolvers$))
