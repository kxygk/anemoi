(ns kxygk.anemoi.airport
  (:require [kxygk.anemoi.stat :as stat]
            [kxygk.anemoi.enso :as enso]
            kxygk.anemoi.generic
            [kxygk.pathmore.core :as pathmore]
            [kxygk.anemoi.index :as index]
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
  {::filestr (str "/home/kxygk/Data/airport/"
                  "first-sheet-extracted.csv")})

(pbir/constantly-resolver :math/PI
                          3.1415)

(defn normalize-colname
  [colname-str]
  (-> colname-str
      (clojure.string/replace " "
                              "-")
      (clojure.string/replace "("
                              "")
      (clojure.string/replace ")"
                              "")
      keyword))
#_
(normalize-colname "Rain (mm)")
(let [d1 (tick/date "2024-01-01")
      d2 (tick/date "2024-01-05")]
  (compare d1 d2))

(pco/defresolver $read-file
  [{::keys [filestr]}]
  {::raw-table (-> filestr
                   (ds/->dataset {:column-whitelist ["Date"
                                                     "Rain (mm)"
                                                     "d18O"
                                                     "dD"
                                                     "Comment"]
                                  :parser-fn        {"Date" #_string
                                                     [:local-date "yyyy-MM-dd"]
                                                     "dD"
                                                     :float64}
                                  :key-fn           normalize-colname}))})
#_
(-> ::raw-table
    pathmore/check
    ds/column-names)
#_
(-> ::raw-table
    pathmore/check
    :Date
    vec)


(pco/defresolver $nil-d18O-dates
  [{::keys [raw-table]}]
  {::nil-d18O-dates (-> raw-table
                        (ds/filter-column :d18O
                                          some?)
                        (ds/filter-column :d18O
                                          #(-> %
                                               parse-double
                                               nil?))
                        (get :Date)
                        flatten
                        set)})
#_
(-> ::nil-d18O-dates
    pathmore/check)

(pco/defresolver $outoforder-dates
  [{::keys [raw-table]}]
  {::outoforder-dates (->> (get  raw-table
                                 :Date)
                           (partition 2
                                      1)
                           (filter (fn [date-pair]
                                     (not (tick/< (first date-pair)
                                                  (second date-pair)))))
                           flatten
                           set)})
#_
(pathmore/check ::outoforder-dates)

(pco/defresolver $clean-table
  [{::keys [raw-table
            crazy-dates ;; external (from `central`). Maybe should be optional
            nil-d18O-dates
            outoforder-dates]}]
  {::table (-> raw-table
               (ds/filter-column :Date
                                 (fn [row-date]
                                   (and (not (contains? crazy-dates
                                                        row-date))
                                        (not (contains? nil-d18O-dates
                                                        row-date))
                                        (not (contains? outoforder-dates
                                                        row-date))
                                        #_ ;; maybe already manually removed in data table??
                                        (not (tock/leap-day? row-date)))))
               (ds/update-column :Date
                                 (fn [col]
                                   (mapv #(tick/at %
                                                   tick/midnight)
                                         col)))
               (ds/column-cast :d18O
                               :float64)
               (ds/update-column :Rain-mm
                                 (fn [rainmm-col]
                                   (->> rainmm-col
                                        (mapv (fn [rainmm-str]
                                                (if (nil? rainmm-str)
                                                  nil
                                                  (-> rainmm-str
                                                      (clojure.string/replace "m"
                                                                              "")
                                                      (clojure.string/replace "m"
                                                                              ""))))))))
               (ds/column-cast :Rain-mm
                               :float64))})
#_
(pathmore/check ::table
                {::crazy-dates #{#time/date "2017-07-30"}})

(pco/defresolver $extract-table-columns
  "Extract the columsn from the table.
Note that unfortunately they can't be treated as collections directly b/c of bug
See: https://github.com/techascent/tech.ml.dataset/issues/479
So they need to coerced to `vec`"
  [{::keys [table]
    :keys  [start-date]}] ;; Forwarded deeper to convert `Date` to `Days..` .. TODO make optional
  {::pco/output [{::data [:start-date
                          :Date
                          :Rain-mm
                          :d18O
                          :dD
                          :Comment]}]}
  {::data (merge {:start-date start-date}
                 (update-vals (into {}
                                    table)
                              vec))})
#_
(pathmore/check [{::data [:Days-from-start]}]
                {:start-date   #time/date "2011-01-01"
                 ::crazy-dates #{#time/date "2017-07-30"}})

(pco/defresolver $full-table
  [{airport-table ::table
    #_#_
    enso-table    ::enso/table
    index-table   ::index/table }]
  {::table-classified (tech.v3.dataset.join/left-join :Date
                                                      index-table
                                                      airport-table)})

(pco/defresolver $extract-table-classified-columns
  "Extract the columsn from the table.
Note that unfortunately they can't be treated as collections directly b/c of bug
See: https://github.com/techascent/tech.ml.dataset/issues/479
So they need to coerced to `vec`"
  [{::keys [table-classified]
    :keys  [start-date]}] ;; Forwarded deeper to convert `Date` to `Days..` .. TODO make optional
  {::pco/output [{::data-classified [:start-date
                                     :Date
                                     :Rain-mm
                                     :d18O
                                     :dD
                                     :Comment
                                     :Above-Index
                                     :Below-Index]}]}
  {::data-classified (merge {:start-date start-date}
                            (update-vals (into {}
                                               table-classified)
                                         vec))})

;;###########################################
;;###########################################


(pco/defresolver $klang-table
  [{::keys [klang-filestr]}]
  {::pco/output [::klang-table]}
  (p/vthread {::klang-table (-> klang-filestr
                                (ds/->dataset {:dataset-name "Klang speleothem d18O"
                                               :key-fn       normalize-colname}))}))
#_
(-> @(p.a.eql/process env
                      {::nakhon-filestr (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                             "TH000048552.csv")}
                      [::nakhon-annual-rain-totals])
    ::nakhon-annual-rain-totals)

(pco/defresolver $klang-year-d18O
  [{::keys [klang-table]}]
  {::pco/output [::klang-year-d18O]}
  (p/vthread {::klang-year-d18O (sort-by first
                                         (mapv vector
                                               (:Year klang-table)
                                               (:d18O klang-table)))}))

(def $resolvers$
  (->> [(pathmore/find-resolvers)
        kxygk.anemoi.generic/$resolvers$]
       flatten
       (mapv pathmore/inject-simple-cache)))

(def $env$
  (pci/register {::p.a.eql/parallel? true}
                $resolvers$))
