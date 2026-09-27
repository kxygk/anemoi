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
            [kxygk.pathmore.core :as pathmore]
            [kxygk.anemoi.util :as util]
            ;;[kxygk.dripsplit.central :as central]
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
  {::raingauge-filestr (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                            "TH000048552.csv")})

(defn date2cycle-fraction
  [date]
  (let [
        start-of-year   (-> date
                            tick/first-day-of-year)
        ended-of-year   (-> date
                            tick/last-day-of-year)
        days-in-year    (tick/between start-of-year
                                      ended-of-year
                                      :days)
        day-num-of-date (tick/between start-of-year
                                      date
                                      :days)]
    (/ day-num-of-date
       days-in-year))) 

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
                                                 :key-fn       util/normalize-colname})
                                  ;; GHCNd data in "(tenths of mm)"
                                  ;; see:
                                  ;; https://www.ncei.noaa.gov/pub/data/ghcn/daily/readme.txt
                                  (ds/row-map (fn rain-gauge-reformat
                                                [row-data]
                                                (-> row-data
                                                    (assoc :PRCP
                                                           (if (nil? (:PRCP row-data))
                                                             nil
                                                             (* (:PRCP row-data)
                                                                0.1)))
                                                    (assoc :fill
                                                           (-> row-data
                                                               :DATE
                                                               date2cycle-fraction
                                                               quickthing/color-cycle)))))
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
(pathmore/check ::table)

(pco/defresolver $daily-rain
  [{::keys     [table]
    ::tmd/keys [meta-keys]}]
  {::pco/input  [::table
                 (pco/? ::tmd/meta-keys)]
   ::pco/output [{::daily-rain [::tmd/x-key
                                ::tmd/y-key
                                ::tmd/table]}]}
  {::daily-rain {::tmd/x-key     :Day
                 ::tmd/y-key     :PRCP
                 ::tmd/meta-keys meta-keys
                 ::tmd/table     table}})
#_
(pathmore/check [{::daily-rain [::tmd/table]}])

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
(pathmore/check ::by-year)

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
(pathmore/check [{::annual-rain [:y]}]) ;; BROKEN, missing :xy-all resolver. Where is it..?

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
(pathmore/check [{::annual-storm-count [:xy-all]}]
                {::storm-threshold-mm 100})

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
(pathmore/check [{::annual-storm-fraction [:xy-all]}]
                {::storm-threshold-mm 100})

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
(pathmore/check [{::annual-storm-rain [:xy-all]}]
                {::storm-threshold-mm 100})

(defn classify-winter
  "Winter classified to the closest new-year
  Other are `nil`
  OCT NOV DEC of Year 1987
  is classified as WINTER 1988
  JAN FEB MAR of Year 1967
  is claffified as WINTER 1967
  APR MAY JUN JUL AUG SEP of year 1956
  are classified as nil"
  [date]
  (let [month (tick/month date)
        year  (tick/int (tick/year date))]
    (condp =
        month
      tick.core/JANUARY   year
      tick.core/FEBRUARY  year
      tick.core/MARCH     year
      tick.core/APRIL     nil
      tick.core/MAY       nil
      tick.core/JUNE      nil
      tick.core/JULY      nil
      tick.core/AUGUST    nil
      tick.core/SEPTEMBER nil
      tick.core/OCTOBER   (inc year)
      tick.core/NOVEMBER  (inc year)
      tick.core/DECEMBER  (inc year)
      (do (println "Date/Month unrecognized!")
          nil))))
#_
(->> ::by-year
     pathmore/check
     first
     second
     :DATE
     (mapv classify-winter))

(pco/defresolver $by-winter
  [{::keys [table]}]
  {::pco/output [::by-winter]}
  (p/vthread {::by-winter (-> table
                              (ds/add-or-update-column :WINTER
                                                       (->> table
                                                            :DATE
                                                            (mapv classify-winter)))
                              (ds/group-by :WINTER))}))
#_
(pathmore/check ::by-winter)

(pco/defresolver $winter-storm-rain
  [{::keys [by-winter
            storm-threshold-mm]}]
  {::pco/output [{::winter-storm-rain [:xy-all]}]}
  {::winter-storm-rain {:xy-all (->> (-> by-winter
                                         (update-vals (fn [year-table]
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
                                         (dissoc nil))
                                     (mapv identity) ;; make it into [x y] pairs for platting
                                     sort)}})
#_
(pathmore/check ::winter-storm-rain
                {::storm-threshold-mm 100})

(pco/defresolver $winter-storm-count
  [{::keys [by-winter
            storm-threshold-mm]}]
  {::pco/output [{::winter-storm-count [:xy-all]}]}
  {::winter-storm-count {:xy-all (->> (update-vals by-winter
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
(pathmore/check ::winter-storm-count
                {::storm-threshold-mm 100})

(def $resolvers$
  (->> [(pathmore/find-resolvers)
        kxygk.anemoi.stat/$resolvers$]
       flatten
       (mapv pathmore/inject-simple-cache)))

(def $env$
  (pci/register {::p.a.eql/parallel? true}
                $resolvers$))
