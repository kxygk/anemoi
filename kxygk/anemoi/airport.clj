(ns kxygk.anemoi.airport
  (:require [kxygk.anemoi.stat :as stat]
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
                      @kxygk.anemoi.central/*state
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

(def atan8
  (clojure.math/atan 8))

(def cos-atan8
  (clojure.math/cos atan8))

(def sin-atan8
  (clojure.math/sin atan8))

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
                                                       dD   (row-day :dD)
                                                       atan8 (clojure.math/atan 8)
                                                       cos-atan8 (clojure.math/cos atan8)]
                                                   (if (or (nil? d18O)
                                                           (nil? dD))
                                                     nil
                                                     (let [on-gmwl (+ (* cos-atan8
                                                                         d18O)
                                                                      (* sin-atan8
                                                                         (- dD
                                                                            10)))]
                                                     {:D-excess (- dD
                                                                   (* 8.0
                                                                      d18O))
                                                      :GMWL-proj on-gmwl
                                                      :GMWL-d18O (* on-gmwl
                                                                    cos-atan8)}))))))}))
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


;; TODO Table stuff can be spead up significantly if I use `ds/set-index`
;; The `:Days` column can be the index in some tables
;; This allows faster joins and stuff
(pco/defresolver $full-table
  [{::keys [climate-index-table
            enso-table
            isotopes-table]}]
  {::pco/output [::full-table]} ;; simplified for brevity
  (p/vthread {::full-table (let [with-index  (tech.v3.dataset.join/left-join :Date
                                                                             climate-index-table
                                                                             isotopes-table)
                                 with-extras (ds/row-map with-index
                                                         (fn [row]
                                                           {:Year   (-> row
                                                                        :Date
                                                                        tick/year)
                                                            :Month  (-> row
                                                                        :Date
                                                                        tick/month)
                                                            :Above? (-> row
                                                                        :Above-Index
                                                                        zero?
                                                                        not)}))
                                 with-enso   (-> (tech.v3.dataset.join/left-join [:Year
                                                                                  :Month]
                                                                                 with-extras
                                                                                 enso-table)
                                                 (ds/rename-columns {"EnsoIndex" :ENSO})
                                                 (ds/sort-by-column :Date))]
                             (assoc with-enso
                                    :Day
                                    (range 1
                                           (-> with-enso
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

#_#_
(defn make-isotope-node [node-key filter-fn]
  (pco/resolver (symbol (name node-key))
    {::pco/input [:$full-table]
     ::pco/output [node-key]}
    (fn [_ {:keys [$full-table]}]
      {node-key 
       {::stat/data-vec   (mapv :d18O (filter filter-fn $full-table))
        ::stat/weight-vec (mapv :rain (filter filter-fn $full-table))}})))

(make-isotope-node :$d18O-above-node #(> (:index %) 0))

(pco/defresolver $nakhon-filestr->table
  [{::keys [nakhon-filestr]}]
  {::pco/output [::nakhon-table]}
  (p/vthread {::nakhon-table
              (let [raw-table (-> nakhon-filestr
                                  (ds/->dataset {:dataset-name "Nakhon GHCNd"
                                                 :key-fn normalize-colname}))]
                (assoc raw-table
                       :Day
                       (range 1
                              (-> raw-table
                                  ds/row-count
                                  inc))))}))
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

(pco/defresolver $nakhon-by-year
  [{::keys [nakhon-table]}]
  {::pco/output [::nakhon-by-year]}
  (p/vthread {::nakhon-by-year (-> nakhon-table
                                 (ds/add-or-update-column :YEAR
                                                          (->> nakhon-table
                                                               :DATE
                                                               (mapv tick/year)))
                                 (ds/group-by :YEAR)
                                 set
                                 (update-keys #(-> %
                                                   str
                                                   Integer/parseInt)))}))
#_
(-> @(p.a.eql/process env
                      {::nakhon-filestr (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                             "TH000048552.csv")}
                      [::nakhon-by-year])
    ::nakhon-by-year
    keys)

(first {:hello "world"
        :how   "are"})


(pco/defresolver $nakhon-annual-rain-totals
  [{::keys [nakhon-by-year]}]
  {::pco/output [::nakhon-annual-rain-totals]}
  (p/vthread {::nakhon-annual-rain-totals (->> (update-vals nakhon-by-year
                                                         #(->> %
                                                                 :PRCP
                                                                 (filterv some?)
                                                                 (apply +)
                                                                 (* 0.1)))
                                                         ;; GHCNd data in "(tenths of mm)"
                                                         ;; see:
                                                         ;; https://www.ncei.noaa.gov/pub/data/ghcn/daily/readme.txt
                                               (mapv identity)
                                               sort)}))
#_
(-> @(p.a.eql/process env
                      {::nakhon-filestr (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                             "TH000048552.csv")}
                      [::nakhon-annual-rain-totals])
    ::nakhon-annual-rain-totals)



(pco/defresolver $nakhon-annual-winter-storm-count
  [{::keys [nakhon-by-year
            big-storm-mm]}]
  {::pco/output [::nakhon-annual-winter-storm-count]}
  (p/vthread {::nakhon-annual-winter-storm-count
              (->> (update-vals nakhon-by-year
                                (fn [year-table]
                                  (->> year-table
                                       :PRCP
                                       (filterv some?)
                                       (filterv #(> %
                                                    (* 10
                                                       big-storm-mm)))
                                       count)))
                   (mapv identity)
                   sort)}))
#_
(-> @(p.a.eql/process env
                      {::nakhon-filestr (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                             "TH000048552.csv")}
                      [::nakhon-annual-winter-storm-count])
    ::nakhon-annual-winter-storm-count)


(pco/defresolver $nakhon-annual-winter-storm-fraction
  [{::keys [nakhon-by-year
            big-storm-mm]}]
  {::pco/output [::nakhon-annual-winter-storm-fraction]}
  (p/vthread {::nakhon-annual-winter-storm-fraction
              (->> (update-vals nakhon-by-year
                                (fn [year-table]
                                  (let [split-table (->> year-table
                                                         :PRCP
                                                         (filterv some?)
                                                         (group-by #(> %
                                                                       (* 10
                                                                          big-storm-mm))))]
                                    (let [big-winter-rains (apply +
                                                                  (get split-table
                                                                       true))
                                          other-rains (apply +
                                                             (get split-table
                                                                  false))]
                                      (/ big-winter-rains
                                         (+ big-winter-rains
                                            other-rains))))))
                   (mapv identity)
                   sort)}))
#_
(-> @(p.a.eql/process env
                      {::nakhon-filestr (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                             "TH000048552.csv")}
                      [::nakhon-annual-winter-storm-count])
    ::nakhon-annual-winter-storm-count)



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
                 $nakhon-filestr->table
                 $nakhon-by-year
                 $nakhon-annual-rain-totals
                 $nakhon-annual-winter-storm-fraction
                 $nakhon-annual-winter-storm-count
                 $klang-table
                 $klang-year-d18O
                 ]))
