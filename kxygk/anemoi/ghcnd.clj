(ns kxygk.anemoi.ghcnd
  "The main input key is: `raingauge-filestr`
  You then need to specify either a `full` or `modern` input key.
  The output will be a table
  {::tmd/x-key :Day
   ::tmd/y-key :PRCP
   ::tmd/table _]
  Which can be unpacked by the `tmd` namespace
  "
  (:require [kxygk.anemoi.stat :as stat]
            [kxygk.anemoi.tmd  :as tmd]
            ;;[kxygk.dripsplit.central :as central]
            kxygk.pathomfx.core
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


(true? nil)

(pco/defresolver $filestr->table
  [{::keys [raingauge-filestr
            start-date
            end-date]
    :or    {start-date nil
            end-date   nil}}]
  {::pco/output [::table]}
  (p/vthread {::table
              (let [raw-table (-> raingauge-filestr
                                  (ds/->dataset {:dataset-name "Nakhon GHCNd"
                                                 :key-fn       normalize-colname})
                                  ;; GHCNd data in "(tenths of mm)"
                                  ;; see:
                                  ;; https://www.ncei.noaa.gov/pub/data/ghcn/daily/readme.txt
                                  (ds/row-map (fn [row-data]
                                                (assoc row-data
                                                       :PRCP
                                                       (if (nil? (:PRCP row-data))
                                                         nil
                                                         (* (:PRCP row-data)
                                                            0.1)))))
                                  ;; optionally filter on start/end dates
                                  (ds/filter-column :DATE
                                                    #(if start-date
                                                       (tick/> %
                                                               (tick/date start-date))
                                                       true))
                                  (ds/filter-column :DATE
                                                    #(if end-date
                                                       (tick/< %
                                                               (tick/date end-date))
                                                       true)))]
                (assoc raw-table
                       :Day
                       (range 1
                              (-> raw-table
                                  ds/row-count
                                  inc ))))}))
#_
(-> @(p.a.eql/process env
                      {::raingauge-filestr (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                             "TH000048552.csv")}
                      [::table]))
#_
(-> @(p.a.eql/process env
                      {::nakhon-filestr (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                             "TH000048552.csv")}
                      [::nakhon-table])
    ::nakhon-table
    :Day
    vec)
;; (:STATION :DATE :LATITUDE :LONGITUDE :ELEVATION :NAME :PRCP :PRCP_ATTRIBUTES :TMAX :TMAX_ATTRIBUTES :TMIN :TMIN_ATTRIBUTES :TAVG :TAVG_ATTRIBUTES)
#_
(-> @(p.a.eql/process env
                      {::nakhon-filestr (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                             "TH000048552.csv")}
                      [::nakhon-table])
    ::nakhon-table
    :DATE
    first
    tick/year)

(pco/defresolver $daily-rain
  [{::keys [table]}]
  {::pco/output [{::daily-rain [::tmd/x-key
                                ::tmd/y-key
                                ::tmd/table]}]}
  {::daily-rain {::tmd/x-key :Day
                 ::tmd/y-key :PRCP
                 ::tmd/table table}})
#_
(-> @(p.a.eql/process env
                      {::raingauge-filestr (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                             "TH000048552.csv")}
                      [{::daily-rain [::tmd/table]}]))

(pco/defresolver $by-year
  [{::keys [table]}]
  {::pco/output [::by-year]}
  (p/vthread {::by-year (-> table
                            (ds/add-or-update-column :YEAR
                                                     (->> table
                                                          :DATE
                                                          (mapv tick/year)))
                            (ds/group-by :YEAR)
                            set
                            (update-keys #(-> %
                                              str
                                              Integer/parseInt)))}))
#_
(-> @(p.a.eql/process env
                      {::raingauge-filestr (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                             "TH000048552.csv")}
                      [::by-year])
    ::by-year
    keys)

(pco/defresolver $annual-rain
  [{::keys [by-year]}]
  {::pco/output [{::annual-rain [:xy-all]}]}
  {::annual-rain {:xy-all (->> (update-vals by-year
                                            #(->> %
                                                  :PRCP
                                                  (filterv some?)
                                                  (apply +)))
                               (mapv identity)
                               sort)}})
#_
(-> @(p.a.eql/process env
                      {::raingauge-filestr (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                "TH000048552"
                                ".csv")}
                      [{::annual-rain [:y]}]))


(pco/defresolver $annual-storm-count
  [{::keys [by-year
            storm-threshold-mm]}]
  {::pco/output [{::annual-storm-count [:xy-all]}]}
  {::annual-storm-count {:xy-all (->> (update-vals by-year
                                                   (fn [year-table]
                                                     (->> year-table
                                                          :PRCP
                                                          (filterv some?)
                                                          (filterv #(> %
                                                                       storm-threshold-mm))
                                                          count)))
                                      (mapv identity)
                                      sort)}})
#_
(-> @(p.a.eql/process env
                      {::raingauge-filestr (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                                "TH000048552"
                                                ".csv")
                       ::storm-threshold-mm      100}
                      [{::annual-storm-count [:y]}]))

(pco/defresolver $annual-storm-fraction
  [{::keys [by-year
            storm-threshold-mm]}]
  {::pco/output [{::annual-storm-fraction [:xy-all]}]}
  {::annual-storm-fraction {:xy-all (->> (update-vals by-year
                                                      (fn [year-table]
                                                        (let [split-table (->> year-table
                                                                               :PRCP
                                                                               (filterv some?)
                                                                               (group-by #(> %
                                                                                             storm-threshold-mm)))]
                                                          (let [big-winter-rains (apply +
                                                                                        (get split-table
                                                                                             true))
                                                                other-rains      (apply +
                                                                                        (get split-table
                                                                                             false))]
                                                            (/ big-winter-rains
                                                               (+ big-winter-rains
                                                                  other-rains))))))
                                         (mapv identity)
                                         sort)}})
#_
(-> @(p.a.eql/process env
                      {::raingauge-filestr (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                "TH000048552"
                                ".csv")
                       ::storm-threshold-mm      100}
                      [{::annual-storm-fraction [:y]}]))

(pco/defresolver $annual-storm-rain
  [{::keys [by-year
            storm-threshold-mm]}]
  {::pco/output [{::annual-storm-rain [:xy-all]}]}
  {::annual-storm-rain {:xy-all (->> (update-vals by-year
                                                  (fn [year-table]
                                                    (let [split-table (->> year-table
                                                                           :PRCP
                                                                           (filterv some?)
                                                                           (group-by #(> %
                                                                                         storm-threshold-mm)))]
                                                      (let [big-winter-rains (apply +
                                                                                    (get split-table
                                                                                         true))
                                                            #_#_
                                                            other-rains      (apply +
                                                                                    (get split-table
                                                                                         false))]
                                                        big-winter-rains
                                                        #_
                                                        (/ big-winter-rains
                                                           (+ big-winter-rains
                                                              other-rains))))))
                                     (mapv identity)
                                     sort)}})
#_
(-> @(p.a.eql/process env
                      {::raingauge-filestr (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                                "TH000048552"
                                                ".csv")
                       ::storm-threshold-mm      100}
                      [{::annual-storm-rain [:y]}]))

(def env
  (pci/register {::p.a.eql/parallel? true}
                [$filestr->table
                 $daily-rain
                 $by-year
                 $annual-rain
                 $annual-storm-count
                 $annual-storm-fraction
                 $annual-storm-rain]))
