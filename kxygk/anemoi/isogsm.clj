(ns kxygk.anemoi.isogsm
  (:require [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
            [tick.core                    :as tick]
            [tech.v3.dataset              :as ds]
            [tock]
            [kxygk.pathmore.core :as pathmore]
            [kxygk.anemoi.util]))

(pathmore/clean-ns!)

(def $state$
  "for testing only"
  {::dirstr (str "/home/kxygk/Data/IsoGSM/csv/")} )

(pco/defresolver $read-dir
  [{::keys [dirstr
            ]}]
  {::file| (->> dirstr
                    clojure.java.io/file
                    file-seq
                    sort
                    rest)}) ;; `first` is dir path
#_
(-> ::file|
    pathmore/check
    first
    str)
;;"/home/kxygk/Data/IsoGSM/csv/Lon_98.90618_Lat_8.005731_IsoGSM.HR_2011_6hourly_tmp2m.precip.sh.csv"

(pco/defresolver $read-tables
  [{::keys [file|
            ]}]
  {::pco/output [::raw-table]}
  {::raw-table (->> file|
                    rest
                    (mapv (fn [fileobj]
                            (ds/->dataset fileobj
                                          {:dataset-name        "Regional Climate Index"
                                           :header-row?         true ;; doesn't work b/c of an empty line after the header..
                                           :n-initial-skip-rows 1
                                           :bad-row-policy      :error
                                           :key-fn              kxygk.anemoi.util/normalize-colname})))
                    (apply ds/concat)
                    ;; cryptic column names detailed here:
                    ;; https://zenodo.org/records/14681370
                    ;;
                    ;; `tmp2m`: 2m temperature (°C)
                    ;;
                    ;; `pr` (H₂¹⁶O), `pr1` (H₂¹⁸O), `pr2` (HD¹⁶O):
                    ;; Precipitation isotopes (mm/day) (raw data, non-filtered data)
                    ;;
                    ;; `sh` (H₂¹⁶O), `sh1` (H₂¹⁸O), `sh2` (HD¹⁶O):
                    ;; Near-surface specific humidity isotopes (kg/kg)
                    ;;
                    (#(ds/rename-columns %
                                         {:column-0 #_:tmp2m :Temperature
                                          :column-1 #_:pr    :Rain-H2-16O
                                          :column-2 #_:pr1   :Rain-H2-18O
                                          :column-3 #_:pr2   :Rain-D2-16O
                                          :column-4 #_:sh    :Vapor-H2-16O
                                          :column-5 #_:sh1   :Vapor-H2-18O
                                          :column-6 #_:sh2   :Vapor-D2-16O})))})
#_
(pathmore/check ::raw-table)

(defn get-file-year [file-path]
  (let [pattern #"IsoGSM\.HR_(\d{4})"]
    (some->> file-path
             (re-find pattern)
             second)))
;;(int "2022")
#_
(->> ::file|
     pathmore/check
     first
     str
     get-file-year
     Integer/parseInt
     inc
     (str "01-01-"))

(pco/defresolver $get-time-range
  [{::keys [file|
            ]}]
  {::start-date (-> file|
                     first
                     str
                     get-file-year
                     (str "-01-01")
                     tick/date)
   ::ended-date (-> file|
                     last
                     str
                     get-file-year
                     Integer/parseInt
                     inc
                     (str "-01-01")
                     tick/date)})
#_
(pathmore/check ::start-date)
#_
(pathmore/check ::ended-date)

(pco/defresolver $-all-dates-vec
  [{::keys [start-date
           ended-date]}]
  {::pco/output [::-all-dates-vec]}
  {::-all-dates-vec (->> (tick/range
                           (tick/at start-date (tick/midnight))
                           (tick/at ended-date (tick/midnight)) ;; doesn't include last value
                           (tick/new-duration 6 :hours))
                         #_
                         (mapv tick/date)
                         #_
                         (mapv #(tick/in %
                                         "UTC"))
                         #_
                         (mapv #(tick/format (tick/formatter "yyyy-MM-dd")
                                             %)))})
#_
(-> ::-all-dates-vec
    pathmore/check
    last)

(pco/defresolver $add-dates ;; TODO Should also add the `:Day-from-start`.. since there are no skipped days 
  [{::keys [raw-table
            -all-dates-vec]}]
  {::pco/output [::table]}
  {::table (assoc raw-table
                  :Date
                  (->> -all-dates-vec
                       ;; depends on if index has them or not! Should add back TODO!!!!!!
                       ;; Ideally this should be diabled
                       ;;#_
                       ;;tock/remove-leapdays
                       (take (ds/row-count raw-table))))})
#_
(pathmore/check ::table)

(pco/defresolver $extract-table-columns
  [{::keys [table
            start-date]}]
  {::pco/output [{::data [:Date
                          :start-date
                          :Temperature
                          :Rain-H2-16O
                          :Rain-H2-18O
                          :Rain-D2-16O
                          :Vapor-H2-16O
                          :Vapor-H2-18O
                          :Vapor-D2-16O]}]}
  {::data (merge {:start-date start-date}
                 (update-vals (into {}
                                    table)
                              vec))})
#_
(pathmore/check [{::data [:Temperature]}])

(defn
  calc-d18O
  "Calculation is described here:
  https://zenodo.org/records/14681370
  In the CSV file:
  `pr` is `H2-16O`
  and
  `pr1` is `H2-18O`
  When looking at precipitation
  (for vapor,
  or `specific humidity isotopes` this is:
  `sh` and `sh1`"
  [H2-16O
   H2-18O]
  (if (or (zero? H2-16O)
          (zero? H2-18O))
    nil 
    (* 1000
       (- (/ H2-18O
             H2-16O)
          1))))

;; Available keys
;; (:Temperature :Rain-H2-16O :Rain-H2-18O :Rain-D2-16O :Vapor-H2-16O :Vapor-H2-18O :Vapor-D2-16O)
(pco/defresolver $d18O
  "The conversion formula is given at the dataset:
https://zenodo.org/records/14681370
(Scroll down to Format -> 3. CSV)
"
  [{:keys [Rain-H2-16O
           Rain-H2-18O
           Vapor-H2-16O
           Vapor-H2-18O]}]
  {:Rain-mm    Rain-H2-16O
   :Rain-d18O  (mapv calc-d18O
                     Rain-H2-16O
                     Rain-H2-18O)
   :Vapor-d18O (mapv calc-d18O
                     Vapor-H2-16O
                     Vapor-H2-18O)})
#_
(pathmore/check [{::data [:Vapor-H2-18O]}]
                  {:start-date #time/date "2011-01-01"
                   :end-date   #time/date "2021-01-01"})

(def $resolvers$
  (->> (pathmore/find-resolvers)
       (mapv pathmore/inject-simple-cache)))

(def $env$
  (pci/register {::p.a.eql/parallel? true}
                $resolvers$))
