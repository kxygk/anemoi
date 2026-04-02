(ns kxygk.anemoi.airport
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

(pco/defresolver $enso-filestr->table
  [{::keys [enso-filestr]}]
  {::pco/output [::enso-table]}
  (p/vthread {::enso-table (-> enso-filestr
                               (ds/->dataset {:dataset-name "ENSO Index (Nino 3.4)"
                                              :key-fn normalize-colname})
                               (ds/rename-columns [:RefDate
                                                   :EnsoIndex])
                               (ds/filter-column :EnsoIndex
                                                 #(not= %
                                                        -9999.00))
                               (ds/row-map (fn [enso-row]
                                             (let [adjusted-date (tick/<< (enso-row :RefDate)
                                                                          (tick/new-period 0
                                                                                           :months))]
                                               {:Year  (tick/year adjusted-date )
                                                :Month (tick/month adjusted-date)}))))}))
#_
(-> @(p.a.eql/process env
                      @central/*state
                      [::enso-table])
    ::enso-table
    ds/column-names)
;; (:RefDate :EnsoIndex :Year :Month)

(pco/defresolver $isotopes-filestr->raw
  [{::keys [isotopes-filestr]}]
  {::pco/output [::isotopes-raw]}
  (p/vthread {::isotopes-raw  (-> isotopes-filestr
                      (ds/->dataset {:column-whitelist ["Date"
                                                        "Rain (mm)"
                                                        "d18O"
                                                        "dD"
                                                        "Comment"]
                                     :parser-fn        {"Date" #_string
                                                        [:local-date "yyyy-MM-dd"]
                                                        "dD"
                                                        :float64}
                                     :key-fn normalize-colname}))}))
#_
(-> @(p.a.eql/process env
                      @central/*state
                      [::isotopes-raw])
    ::isotopes-raw
    ds/column-names)
;; (:Date :Rain-mm :d18O :dD :Comment)

(pco/defresolver $isotopes->problematic-d18O
  [{::keys [isotopes-raw]}]
  {::pco/output [::problematic-d18O]}
  (p/vthread {::problematic-d18O (-> isotopes-raw
                                     (ds/filter-column :d18O
                                                       some?)
                                     (ds/filter-column :d18O
                                                       #(-> %
                                                            parse-double
                                                            nil?))
                                     (get :Date)
                                     flatten
                                     set)}))
#_
(-> @(p.a.eql/process env
                      @central/*state
                      [::problematic-d18O])
    ::problematic-d18O)
;;#{#time/date "2013-09-08" #time/date "2013-09-25"}

(pco/defresolver $isotopes->problematic-dates
  [{::keys [isotopes-raw]}]
  {::pco/output [::problematic-dates]}
  (p/vthread {::problematic-dates (->> (get  isotopes-raw
                                             :Date)
                                       (partition 2
                                                  1)
                                       (filter (fn [date-pair]
                                                 (not (tick/< (first date-pair)
                                                              (second date-pair)))))
                                       flatten
                                       set)}))
#_
(-> @(p.a.eql/process env
                      @central/*state
                      [::problematic-dates])
    ::problematic-dates)
;; #{#time/date "2019-01-03" #time/date "2012-04-04" #time/date "2012-05-05" #time/date "2012-06-05" #time/date "2012-09-05" #time/date "2015-09-05" #time/date "2014-09-06" #time/date "2020-04-08" #time/date "2015-12-08" #time/date "2015-07-09" #time/date "2014-12-09" #time/date "2023-10-10" #time/date "2010-10-15" #time/date "2018-10-16" #time/date "2020-09-18" #time/date "2019-10-22" #time/date "2012-05-24" #time/date "2012-02-25" #time/date "2023-11-25" #time/date "2019-12-25" #time/date "2020-04-26" #time/date "2021-11-27" #time/date "2021-06-29" #time/date "2022-09-29" #time/date "2020-10-29"}

(pco/defresolver $isotopes-table 
  [{::keys [isotopes-raw
            crazy-dates
            problematic-d18O
            problematic-dates]}]
  {::pco/output [::isotopes-table]}
  (p/vthread {::isotopes-table (-> isotopes-raw
                                   (ds/filter-column :Date
                                                     (fn [row-date]
                                                       (and (not (contains? problematic-d18O
                                                                            row-date))
                                                            (not (contains? crazy-dates
                                                                            row-date))
                                                            (not (contains? problematic-dates
                                                                            row-date)))))
                                   #_(ds/column-cast "dD"
                                                     :float64)
                                   (ds/column-cast :d18O
                                                   :float64)
                                   (update :Rain-mm
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
                                                   :float64)
                                   (ds/row-map (fn [row-day]
                                                 (let [d18O (row-day :d18O)
                                                       dD   (row-day :dD)]
                                                   (if (or (nil? d18O)
                                                           (nil? dD))
                                                     nil
                                                     {:D-excess (- dD
                                                                   (* 8.0
                                                                      d18O))})))))}))
#_
(-> @(p.a.eql/process env
                      @central/*state
                      [::isotopes-table])
    ::isotopes-table
    ds/column-names)
;; (:Date :Rain-mm :d18O :dD :Comment :D-excess)


(defn-
  leap-day?
  [date]
  (and (= (tick/day-of-month date)
          29)
       (= (tick/month date)
          tick/FEBRUARY)))
#_
(leap-day? (tick/instant #inst"2012-02-29"))

(defn-
  remove-leapdays
  [dates]
  (->> dates
       (filter #(-> %
                    leap-day?
                    not))))
#_
(-> (gen-dates #inst"2011-01-01"
               #inst"2021-01-01")
    remove-leapdays
    count)
;; => 3650

(pco/defresolver $all-dates-vec
  [{::keys [start-date
            end-date]}]
  {::pco/output [::all-dates-vec]}
  (p/vthread {::all-dates-vec (->> (tick/range
                                     (tick/instant start-date)
                                     (tick/instant end-date ) ;; doesn't include last value
                                     (tick/new-period 1 :days)) #_
                                   (mapv tick/date)
                                   (mapv #(tick/in %
                                                   "UTC"))
                                   (mapv #(tick/format (tick/formatter "yyyy-MM-dd")
                                                       %)))}))
#_
(-> ($all-dates-vec {::start-date #inst"2011-01-01"
                     ::end-date   #inst"2021-01-01"})
    deref
    ::all-dates-vec
    remove-leapdays
    count)
;; => 3650


(pco/defresolver $leapless-dates-table
  [{::keys [all-dates-vec]}]
  {::pco/output [::leapless-dates-table]}
  (p/vthread {::leapless-dates-table (ds/->dataset (->> all-dates-vec
                                                        remove-leapdays
                                                        (mapv #(assoc {}
                                                                      :Date
                                                                      %)))
                                                   {:parser-fn {:Date [:local-date "yyyy-MM-dd"]}})}))


(pco/defresolver $climate-index-table
  [{::keys [leapless-dates-table
            climate-index-filestr]}]
  {::pco/output [::climate-index-table]}
  (p/vthread {::climate-index-table (assoc (-> (ds/->dataset climate-index-filestr
                                                             {:header-row? false
                                                              :key-fn normalize-colname})
                                               (ds/rename-columns [:Above-Index
                                                                   :Below-Index]))
                                           :Date
                                           (get leapless-dates-table
                                                :Date))}))
#_
(-> @(p.a.eql/process env
                      @central/*state
                      [::climate-index-table])
    ::climate-index-table
    ds/column-names)

(pco/defresolver $full-table
  [{::keys [climate-index-table
            enso-table
            isotopes-table]}]
  {::pco/output [::full-table [:Date
                               :Above-Index
                               :Below-Index
                               :Rain-mm
                               :d18O
                               :dD
                               :Comment
                               :D-excess]]}
  (p/vthread {::full-table (-> (tech.v3.dataset.join/left-join :Date
                                                               climate-index-table
                                                               isotopes-table)
                               (ds/row-map (fn [row-day]
                                             (let [year  (tick/year (row-day :Date))
                                                   month (tick/month (row-day :Date))]
                                               {:Above? (not (zero? (row-day :Above-Index)))
                                                :ENSO   (-> enso-table
                                                             (ds/filter-column :Month #(= %
                                                                                           month))
                                                             (ds/filter-column :Year #(= %
                                                                                          year))
                                                             ds/rows
                                                             first
                                                             (get "EnsoIndex"))})))
                               (ds/sort-by-column :Date)
                               (assoc :Day
                                      (range 1
                                             (-> climate-index-table
                                                 ds/row-count
                                                 inc))))}))
#_
(-> @(p.a.eql/process env
                      @central/*state
                      [::full-table])
    ::full-table
    ds/column-names)
;; (:Date :Above-Index :Below-Index :/home/kxygk/Data/airport/first-sheet-extracted.csv.Date :Rain-mm :d18O :dD :Comment :D-excess)

;;("Date" "column-0" "column-1" "/home/kxygk/Data/airport/first-sheet-extracted.csv.Date" "Rain (mm)" "d18O" "dD" "Comment" "D-excess" "Above?" "ENSO" "Day")

;;("Date" "column-0" "column-1" "/home/kxygk/Data/airport/first-sheet-extracted.csv.Date" "Rain (mm)" "d18O" "dD" "Comment" "D-excess" "Above?" "ENSO" "Day")
#_
(-> @(p.a.eql/process env
                      @central/*state
                      [::full-table]))


(pco/defresolver $classified-tables
  [{::keys [full-table]}]
  {::pco/output [{::above-table [:Date
                                 :Above-Index
                                 :Below-Index
                                 :Rain-mm
                                 :d18O
                                 :dD
                                 :Comment
                                 :D-excess]}
                 {::below-table [:Date
                                 :Above-Index
                                 :Below-Index
                                 :Rain-mm
                                :d18O
                                 :dD
                                 :Comment
                                 :D-excess]}]}
  (p/vthread (let [split-table-map (ds/group-by full-table
                                                :Above?)]
               {::above-table  (get split-table-map
                                                   true)
                ::below-table  (get split-table-map
                                                    false)})))

#_
(pco/defresolver $column-extractor
  [{::keys [extraction-table
            x-key
            y-key]}]
  {::pco/input  [::extraction-table
                 ::x-key
                 ::y-key]
   ::pco/output [:x
                 :y]}
  {:x {:data-vec (vec (get extraction-table
                           x-key))}
   :y {:data-vec (vec (get extraction-table
                           y-key))}})



#_
(kxygk.pathomfx.core/get-actual-dependencies env
                                             @central/*state
                                             [{::days-vs-d18O [:y]}])
#_
@(p.a.eql/process env
                   @central/*state
                   [::full-table])

#_
(def $day-vs-d18O
  (pbir/single-attr-resolver ::full-table
                             ::day-vs-d18O
                             (fn [full-table]
                               (p/vthread (-> full-table
                                              (ds/drop-missing :Rain-mm)
                                              (ds/drop-missing :d18O)
                                              #_(ds/filter (fn [row-datapoint]
                                                           (> (row-datapoint "Rain (mm)")
                                                              30.0))))))))
#_
@(p.a.eql/process env
                  @central/*state
                  [::day--d18O])

(def $d18O-above
  (pbir/single-attr-resolver ::d18O
                             ::d18O-above
                             #(p/vthread (ds/filter %
                                                    (fn [row-datapoint]
                                                      (row-datapoint :Above?))))))
#_
@(p.a.eql/process env
                  @central/*state
                  [::d18O-above])

(def $d18O-below
  (pbir/single-attr-resolver ::d18O
                             ::d18O-below
                             #(p/vthread (ds/filter %
                                                    (fn [row-datapoint]
                                                      (not (row-datapoint :Above?)))))))
#_
@(p.a.eql/process env
                  @central/*state
                  [::d18O-below])

(defn stat
  [data-vec]
  (let [num (count data-vec)]
    (let [average (/ (apply +
                            data-vec)
                     num)]
    (let [std (clojure.math/sqrt (/ (->> data-vec
                                         (mapv #(clojure.math/pow (- %
                                                                     average)
                                                                  2.0))
                                         (reduce +))
                                    (dec num)))]
          {:mean average
           :std std
           :sdom (/ std
                    (clojure.math/sqrt num))
           :min (apply min
                       data-vec)
           :max (apply max
                       data-vec)}))))

(defn stat-weighted
  "This was taken from here:
  https://en.wikipedia.org/wiki/Weighted_arithmetic_mean
  As I'm a bit unclear on how to derive these values"
  [data-vec
   weight-vec]
  (let [num (count data-vec) ;; should be same for `weight-vec`
        sum-of-weights (reduce +
                               weight-vec)
        sum-of-squared-weights (->> weight-vec
                                       (mapv #(clojure.math/pow %
                                                                2.0))
                                       (reduce +))
        weighted-sum (->> (mapv *
                                data-vec
                                weight-vec)
                          (reduce +))]
    (let [mean (/ weighted-sum
                  sum-of-weights)]
      (let [sum-of-residuals-squared (->> (mapv -
                                                data-vec
                                                (repeat num
                                                        mean))
                                          (mapv #(clojure.math/pow %
                                                                   2.0))
                                          (reduce +))]
        (let [variance (-> sum-of-residuals-squared
                           (/ (dec num))
                           (* (/ (/ sum-of-squared-weights
                                    num)
                                 (clojure.math/pow (/ sum-of-weights
                                                      num)
                                                   2.0))))]
          {:mean mean
           :std (clojure.math/sqrt variance)
           :sdom (* (clojure.math/sqrt variance)
                    (->> weight-vec
                         (mapv #(/ %
                                   sum-of-weights))
                         (mapv #(clojure.math/pow %
                                                  2.0))
                         (reduce +)
                         clojure.math/sqrt))
           :min (apply min
                       data-vec)
           :max (apply max
                       data-vec)})))))

(pco/defresolver $stat-rain-weighted-d18O
  [{::keys [d18O]}]
  {::pco/output [::stat-rain-weighted-d18O]}
  (p/vthread {::stat-rain-weighted-d18O (stat-weighted (d18O :d18O)
                                                       (d18O :Rain-mm))}))
#_
@(p.a.eql/process env
                  @central/*state
                  [::stat-rain-weighted-d18O])

(pco/defresolver $stat-rain-weighted-d18O-above
  [{::keys [d18O-above]}]
  {::pco/output [::stat-rain-weighted-d18O-above]}
  (p/vthread {::stat-rain-weighted-d18O-above (stat-weighted (d18O-above :d18O)
                                                             (d18O-above :Rain-mm))}))
#_
@(p.a.eql/process env
                  @central/*state
                  [::d18O-above])

(pco/defresolver $stat-rain-weighted-d18O-below
  [{::keys [d18O-below]}]
  {::pco/output [::stat-rain-weighted-d18O-below]}
  (p/vthread {::stat-rain-weighted-d18O-below (stat-weighted (d18O-below :d18O)
                                                             (d18O-below :Rain-mm))}))

(pco/defresolver $stat-index-weighted-d18O-above
  [{::keys [d18O-above]}]
  {::pco/output [::stat-index-weighted-d18O-above]}
  (p/vthread {::stat-index-weighted-d18O-above (stat-weighted (d18O-above :d18O)
                                                              (d18O-above :Above-Index))}))


(pco/defresolver $stat-index-weighted-d18O-below
  [{::keys [d18O-below]}]
  {::pco/output [::stat-index-weighted-d18O-below]}
  (p/vthread {::stat-index-weighted-d18O-below (stat-weighted (d18O-below :d18O)
                                                              (d18O-below :Below-Index))}))

(pco/defresolver $rain-datavec
  [{::keys [full-table]}]
  {::pco/output [::rain-datavec]}
  (p/vthread {::rain-datavec (filterv #(-> %
                                           second
                                           some?)
                                      (mapv vector
                                            (full-table :Day)
                                            (full-table :Rain-mm)))}))

(pco/defresolver $monsoon-winter-datavec
  [{::keys [full-table]}]
  {::pco/output [::monsoon-winter-datavec]}
  (p/vthread {::monsoon-winter-datavec (mapv vector
                                             (full-table :Day)
                                             (full-table :Below-Index))}))

(pco/defresolver $monsoon-summer-datavec
  [{::keys [full-table]}]
  {::pco/output [::monsoon-summer-datavec]}
  (p/vthread {::monsoon-summer-datavec (mapv vector
                                             (full-table :Day)
                                             (full-table :Above-Index))}))

(pco/defresolver $data-span-days
  "The days in the full table are numbered.
Get the number of the last day"
  [{::keys [full-table]}]
  {::pco/output [::data-span-days]}
  (p/vthread {::data-span-days (last (full-table :Day))}))

(pco/defresolver $climate-index-max
  "The days in the full table are numbered.
Get the number of the last day"
  [{::keys [full-table]}]
  {::pco/output [::climate-index-max]}
  (p/vthread {::climate-index-max (apply max
                                         (into (full-table :Above-Index)
                                               (full-table :Below-Index)))}))

(pco/defresolver $missing-days-datavec
  "The days in the full table are numbered.
Get the number of the last day"
  [{::keys [full-table
            problematic-dates
            problematic-d18O
            crazy-dates]}]
  {::pco/output [::missing-days-datavec]}
  (p/vthread {::missing-days-datavec (->> (ds/filter-column full-table
                                                            :Date
                                                            (fn [given-date]
                                                              (some? ((clojure.set/union problematic-dates
                                                                                         problematic-d18O
                                                                                         crazy-dates) given-date))))
                                          ds/rows
                                          (mapv (fn [data-row]
                                                  [(data-row :Day)
                                                   0])))}))
#_
@(p.a.eql/process env
                  @central/*state
                  [::missing-days-datavec])


(pco/defresolver $d18O-datavec
  "The days in the full table are numbered.
Get the number of the last day"
  [{::keys [d18O]}]
  {::pco/output [::d18O-datavec]}
  (p/vthread {::d18O-datavec (->> d18O
                                  ds/rows
                                  (map (fn [row-data]
                                         [(row-data :Day)
                                          (row-data :d18O)
                                          ;;nil
                                          {:tooltip (str (row-data :Date)
                                                         \newline
                                                         (row-data :Comment))
                                           :fill
                                           #_
                                           "#33ff"
                                           ;;#_
                                           (if (row-data :Above?)  ;;
                                             "#aa8800";;summer
                                             "#00aa88");;winter
                                           }])))}))


(defn make-isotope-node [node-key filter-fn]
  (pco/resolver (symbol (name node-key))
    {::pco/input [:$full-table]
     ::pco/output [node-key]}
    (fn [_ {:keys [$full-table]}]
      {node-key 
       {::stat/data-vec   (mapv :d18O (filter filter-fn $full-table))
        ::stat/weight-vec (mapv :rain (filter filter-fn $full-table))}})))

(make-isotope-node :$d18O-above-node #(> (:index %) 0))

(def env
  (pci/register {::p.a.eql/parallel? true}
                [$enso-filestr->table
                 $isotopes-filestr->raw
                 $isotopes->problematic-d18O
                 $isotopes->problematic-dates
                 $isotopes-table
                 $all-dates-vec
                 $leapless-dates-table
                 $climate-index-table
                 $full-table
                 $classified-tables
                 $missing-days-datavec
                 #_#_
                 $days-vs-d18O
                 $days-vs-rain
                 #_#_#_#_#_#_#_#_#_#_#_#_#_#_
                 $d18O
                 $d18O-above
                 $d18O-below
                 $stat-rain-weighted-d18O
                 $stat-rain-weighted-d18O-above
                 $stat-rain-weighted-d18O-below
                 $stat-index-weighted-d18O-above
                 $stat-index-weighted-d18O-below
                 $rain-datavec
                 $data-span-days
                 $climate-index-max
                 $monsoon-winter-datavec
                 $monsoon-summer-datavec
                 $d18O-datavec]))

#_
@(p.a.eql/process env
                  @central/*state
                  [::enso-table
                   ::isotopes-raw
                   ::problematic-d18O
                   ::problematic-dates
                   ::isotopes-table
                   ::all-dates-vec
                   ::leapless-dates-table
                   ::climate-index-table
                   ::full-table
                   ::d18O
                   ::d18O-above
                   ::d18O-below
                   ::stat-rain-weighted-d18O
                   ::stat-rain-weighted-d18O-above
                   ::stat-rain-weighted-d18O-below
                   ::stat-index-weighted-d18O-above
                   ::stat-index-weighted-d18O-below
                   ::rain-datavec
                   ::data-span-days
                   ::climate-index-max
                   ::missing-days-datavec
                   ::monsoon-winter-datavec
                   ::monsoon-summer-datavec
                   ::d18O-datavec])
#_
@(p.a.eql/process env
                 {::enso-filestr          "/home/kxygk/Data/enso/nina34.anom.csv"
                  ::isotopes-filestr      "/home/kxygk/Data/airport/first-sheet-extracted.csv"}
                 [::enso-table
                  ::isotopes-raw])
#_#_
(def smap (psm/smart-map env
                         central/*state))
(comment
  (::enso-table smap)
  (::isotopes-raw smap)
  (::problematic-d18O smap)
  (::problematic-dates smap)
  (::isotopes-table smap)
  (::all-dates-vec smap)
  (::leapless-dates-table smap)
  (::climate-index-table smap)
  (::full-table smap)
  (ds/write! (::full-table smap)
             "joined.csv")
  (::d18O smap)
  (::d18O-above smap)
  (::d18O-below smap)
  (::stat-rain-weighted-d18O smap)
  (::stat-rain-weighted-d18O-above smap)
  (::stat-rain-weighted-d18O-below smap)
  (::stat-index-weighted-d18O-above smap)
  (::stat-index-weighted-d18O-below smap)
  (::rain-datavec smap)
  (::data-span-days smap)
  (::climate-index-max smap)
  (::missing-days-datavec smap)
  (::monsoon-winter-datavec smap)
  (::monsoon-summer-datavec smap)
  (::d18O-datavec smap)
  )
