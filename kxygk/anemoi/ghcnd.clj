(ns kxygk.anemoi.ghcnd
  "The main input key is: `raingauge-filestr`
  You then need to specify either a `full` or `modern` input key.
  The output will be a table
  {::tmd/x-key :Day
   ::tmd/y-key :PRCP
   ::tmd/table _]
  Which can be unpacked by the `tmd` namespace
  "
  (:require kxygk.mathom.core
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
  [{::keys [raingauge-filestr]}]
  {::pco/output [::table
                 ::start-date
                 ::ended-date]}
  (let [raw-table (-> raingauge-filestr
                      (ds/->dataset {:dataset-name "Nakhon GHCNd"
                                     :key-fn       util/normalize-colname})
                      ;; GHCNd data in "(tenths of mm)"
                      ;; see:
                      ;; https://www.ncei.noaa.gov/pub/data/ghcn/daily/readme.txt
                      (ds/row-map (fn rain-gauge-reformat
                                    [row-data]
                                    (-> row-data
                                        (assoc :PRCP ;; rescale existing col
                                               (if (nil? (:PRCP row-data))
                                                 nil
                                                 (* (:PRCP row-data)
                                                    0.1)))
                                        (assoc :fill| ;; new col
                                               (-> row-data
                                                   :DATE
                                                   date2cycle-fraction
                                                   quickthing/color-cycle)))))
                      (ds/rename-columns [:Station
                                          :Date
                                          :Lat
                                          :Lon
                                          :Elevation
                                          :Name
                                          :Rain-mm ;; was PRCP
                                          :Rain-mm-attribs ;; was PRCP_ATTRIBUTES
                                          :T-max
                                          :T-max-attribs
                                          :T-min
                                          :T-min-attribs
                                          :T-avg
                                          :T-avg-attribs
                                          :fill
                                          #_
                                          :Day])
                      ;; optionally filter on start/end dates
                      #_#_
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
    {::table      raw-table #_ (assoc raw-table
                                      :Day
                                      (range 1
                                             (-> raw-table
                                                 ds/row-count
                                                 inc )))
     ::start-date (->> raw-table
                       :Date
                       first)
     ::ended-date (->> raw-table
                       :Date
                       last)}
    ))
#_
(ds/column-names (pathmore/check ::table))
#_(:STATION :DATE :LATITUDE :LONGITUDE :ELEVATION :NAME :PRCP :PRCP_ATTRIBUTES :TMAX :TMAX_ATTRIBUTES :TMIN :TMIN_ATTRIBUTES :TAVG :TAVG_ATTRIBUTES :fill :Day)
#_
(pathmore/check ::start-date)
#_
(pathmore/check ::ended-date)

(pco/defresolver $default-day-zero
  [{::keys [start-date]}]
  {::pco/output [::day-zero]}
  {::day-zero start-date})
#_
(pathmore/check ::day-zero)

(pco/defresolver $extract-table-columns
  "Extract the columsn from the table.
Note that unfortunately they can't be treated as collections directly b/c of bug
See: https://github.com/techascent/tech.ml.dataset/issues/479
So they need to coerced to `vec`"
  [{::keys [table
            day-zero]}] ;; Forwarded deeper to convert `Date` to `Days..` .. TODO make optional
  {::pco/output [{::data [:day-zero
                          {:Station [:data|]}
                          {:Date [:data|]}
                          {:Lat [:data|]}
                          {:Lon [:data|]}
                          {:Elevation [:data|]}
                          {:Name [:data|]}
                          {:Rain-mm [:data|]}
                          {:Rain-mm-attribs [:data|]}
                          {:T-max [:data|]}
                          {:T-max-attribs [:data|]}
                          {:T-min [:data|]}
                          {:T-min-attribs [:data|]}
                          {:T-avg [:data|]}
                          {:T-avg-attribs [:data|]}
                          {:fill [:data|]}]}]}
  (println (str "GHCND Start Date: "
                day-zero))
  {::data (merge {:day-zero day-zero}
                 (update-vals (into {}
                                    table)
                              (fn wrap-with-data-key
                                [given-column]
                                {:data| (-> given-column
                                            vec)})))})
#_
(pathmore/check [{::data [:Date]}])
#_
(pathmore/check [{::data [:Days-from-start]}])
#_
(let [pack (pathmore/check [{::data [:Days-from-start|
                                     :Rain-mm|]}])]
  (mapv vector
        (-> pack
            :data
            :Days-from-start)))

#_
(-> (pathmore/check [{::data [:Days-from-start]}]
                    {::start-date "2022-01-01"})
    ::data
    :Days-from-start
    last)
;;1331.0

(pco/defresolver $daily-rain
  [{::keys [data]}]
  {::pco/input  [{::data [{:Days-from-start [:data|]}
                          {:Rain-mm [:data|]}]}]
   ::pco/output [{::daily-rain [{:x [:data|]}
                                {:y [:data|]}]}]}
  {::daily-rain {:x (:Days-from-start data)
                 :y (:Rain-mm data)}})
#_
(pathmore/check ::data)
#_
(pathmore/check ::daily-rain)
#_
(pathmore/check [{::daily-rain [:xy-nonil|]}])

;; from `generic` ns
#_
(pathmore/check [{::data [:Year|]}])

(pco/defresolver $by-year
  "Ugly thing that works with the original table"
  [{::keys [table
            start-date]}]
  {::pco/output [{::by-year| [::year
                              ::table
                              ::start-date]}]}
  {::by-year| (let [map-of-tables (-> table
                                      (ds/add-or-update-column :Year
                                                               (->> table
                                                                    :Date
                                                                    (mapv tick/year)))
                                      (ds/group-by :Year))]
                (map (fn rebuild-into-vec-of-maps
                       [[given-year
                         annual-table]]
                       {::year       given-year
                        ::table      annual-table
                        ::start-date start-date})
                     map-of-tables))})

;; #_#_
;;                         (update-keys #(-> %
;;                                           str
;;                                           Integer/parseInt))
;;                         (update-vals #({:table      %
;;                                         :start-date start-date})))})
#_
(-> [{::by-year| [{::data [{:Rain-mm [:sum]}]}]}]
    pathmore/check)

(tick/int (tick/year #inst"2021-01-01"))

(pco/defresolver $annual-rain
  [{::keys [by-year|]}]
  {::pco/input [{::by-year| [::year
                             {::data [{:Rain-mm [:sum]}]}]}]
   ::pco/output [{::annual-rain [{:x [:data|]}
                                 {:y [:data|]}]}]}
  {::annual-rain {:x {:data| (->> by-year|
                                  (mapv ::year)
                                  (mapv tick/int))}
                  :y {:data| (->> by-year|
                                  (mapv ::data)
                                  (mapv :Rain-mm)
                                  (mapv :sum))}}})
#_
(pathmore/check [{::annual-rain [:xy|]}]) ;; BROKEN, missing :xy-all resolver. Where is it..?

(pco/defresolver $annual-storm-count
  [{::keys [by-year|
            storm-threshold-mm]}]
  {::pco/input [::storm-threshold-mm
                {::by-year| [::year
                             {::data [{:Rain-mm [:data-nonil|]}]}]}]
   ::pco/output [{::annual-storm-count [{:x [:data|]}
                                        {:y [:data|]}]}]}
  {::annual-storm-count {:x {:data| (->> by-year|
                                         (mapv ::year)
                                         (mapv tick/int))}
                         :y {:data| (->> by-year|
                                         (mapv ::data)
                                         (mapv :Rain-mm)
                                         (mapv (fn chec-for-storms
                                                   [one-year-Rain-mm]
                                                   (->> one-year-Rain-mm
                                                        :data-nonil|
                                                        (filterv (fn is-rain-storm?
                                                                   [one-rain]
                                                                   (> one-rain
                                                                      storm-threshold-mm)))
                                                        count))))}}})
#_
(pathmore/check [{::annual-storm-count [:xy|]}]
                {::storm-threshold-mm 100})

(pco/defresolver $annual-storm-fraction
  [{::keys [by-year|
            storm-threshold-mm]}]
  {::pco/input [::storm-threshold-mm
                {::by-year| [::year
                             {::data [{:Rain-mm [:data-nonil|]}]}]}]
   ::pco/output [{::annual-storm-fraction [{:x [:data|]}
                                        {:y [:data|]}]}]}
  {::annual-storm-fraction {:x {:data| (->> by-year|
                                         (mapv ::year)
                                         (mapv tick/int))}
                         :y {:data| (->> by-year|
                                         (mapv ::data)
                                         (mapv :Rain-mm)
                                         (mapv (fn chec-for-storms
                                                   [one-year-Rain-mm]
                                                 (let [total-rains-mm (apply +
                                                                             (->> one-year-Rain-mm
                                                                                  :data-nonil|))
                                                       big-rains-mm (->> one-year-Rain-mm
                                                                      :data-nonil|
                                                                      (filterv (fn is-rain-storm?
                                                                                 [one-rain]
                                                                                 (> one-rain
                                                                                    storm-threshold-mm)))
                                                                      (apply +))]
                                                   (/ big-rains-mm
                                                      total-rains-mm)))))}}})
#_
(pathmore/check [{::annual-storm-fraction [:xy|]}]
                {::storm-threshold-mm 100})

(pco/defresolver $annual-storm-rain
  [{::keys [by-year|
            storm-threshold-mm]}]
  {::pco/input  [::storm-threshold-mm
                 {::by-year| [::year
                              {::data [{:Rain-mm [:data-nonil|]}]}]}]
   ::pco/output [{::annual-storm-rain [{:x [:data|]}
                                       {:y [:data|]}]}]}
  {::annual-total-rain {:x {:data| (->> by-year|
                                        (mapv ::year)
                                        (mapv tick/int))}
                        :y {:data| (->> by-year|
                                        (mapv ::data)
                                        (mapv :Rain-mm)
                                        (mapv (fn chec-for-storms
                                                [one-year-Rain-mm]
                                                (let [total-rains-mm (apply +
                                                                            (->> one-year-Rain-mm
                                                                                 :data-nonil|))
                                                      #_#_
                                                      big-rains-mm   (->> one-year-Rain-mm
                                                                        :data-nonil|
                                                                        (filterv (fn is-rain-storm?
                                                                                   [one-rain]
                                                                                   (> one-rain
                                                                                      storm-threshold-mm)))
                                                                        (apply +))]
                                                  total-rains-mm
                                                  #_
                                                  (/ big-rains-mm
                                                     total-rains-mm)))))}}
   ::annual-storm-rain {:x {:data| (->> by-year|
                                        (mapv ::year)
                                        (mapv tick/int))}
                        :y {:data| (->> by-year|
                                        (mapv ::data)
                                        (mapv :Rain-mm)
                                        (mapv (fn chec-for-storms
                                                [one-year-Rain-mm]
                                                (let [#_#_
                                                      total-rains-mm (apply +
                                                                            (->> one-year-Rain-mm
                                                                                 :data-nonil|))
                                                      big-rains-mm   (->> one-year-Rain-mm
                                                                        :data-nonil|
                                                                        (filterv (fn is-rain-storm?
                                                                                   [one-rain]
                                                                                   (> one-rain
                                                                                      storm-threshold-mm)))
                                                                        (apply +))]
                                                  big-rains-mm
                                                  #_
                                                  (/ big-rains-mm
                                                     total-rains-mm)))))}}})

#_
(pathmore/check [{::annual-storm-rain [:xy|]}]
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
  {::by-winter (-> table
                   (ds/add-or-update-column :WINTER
                                            (->> table
                                                 :DATE
                                                 (mapv classify-winter)))
                   (ds/group-by :WINTER))})
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
        kxygk.mathom.core/$resolvers$]
       flatten
       (mapv pathmore/inject-simple-cache)))

(def $env$
  (pci/register {::p.a.eql/parallel? true}
                $resolvers$))
