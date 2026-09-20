(ns kxygk.anemoi.isogsm
  (:require [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
            [tick.core                    :as tick]
            [tech.v3.dataset              :as ds]
            [tock]
            [kxygk.anemoi.util]))

(pco/defresolver $read-tables
  [{::keys [dirstr
            ]}]
  {::pco/output [::raw-table]}
  {::raw-table (->> dirstr
                     clojure.java.io/file
                     file-seq
                     sort
                     rest
                     (mapv (fn [fileobj]
                             (ds/->dataset fileobj
                                           {:dataset-name "Regional Climate Index"
                                            :header-row? true ;; doesn't work b/c of an empty line after the header..
                                            :n-initial-skip-rows 1
                                            :bad-row-policy :error
                                            :key-fn       kxygk.anemoi.util/normalize-colname})))
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
(->> @(p.a.eql/process env
                       {::dirstr (str "/home/kxygk/Data/IsoGSM/csv/")} 
                       [::raw-table])
     ::raw-table)
;; (:Temperature :Rain-H2-16O :Rain-H2-18O :Rain-D2-16O :Vapor-H2-16O :Vapor-H2-18O :Vapor-D2-16O)


#_ 
(let [pr1 8.416052 ;; 69.62872;;36.26312 
      pr 8.509018 ;;70.23905 ;;36.58453
      ]

  (* 1000
     (- (/ pr1
           pr)
        1)))



#_
(->> "/home/kxygk/Data/IsoGSM/csv/"
     clojure.java.io/file
     file-seq
     sort
     rest
    )
(tick/new-duration 6 :hours)

;;#object[java.io.File 0x25eb6076 "/home/kxygk/Data/IsoGSM/csv"]

(tick/midnight)

(pco/defresolver $-all-dates-vec
  [{:keys [start-date
           end-date]}]
  {::pco/output [::-all-dates-vec]}
  {::-all-dates-vec (->> (tick/range
                           (tick/at start-date (tick/midnight))
                           (tick/at end-date (tick/midnight)) ;; doesn't include last value
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
(->> @(p.a.eql/process env
                       {:start-date #time/date "2011-01-01"
                        :end-date   #time/date "2021-01-01"}
                       [::-all-dates-vec])
     ::-all-dates-vec
     (take 5))



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
(->> @(p.a.eql/process env
                       {::dirstr (str "/home/kxygk/Data/IsoGSM/csv/")
                        :start-date #time/date "2011-01-01"
                        :end-date   #time/date "2021-01-01"} 
                       [::table])
     ::table)

(pco/defresolver $extract-table-columns
  [{::keys [table]
   :keys [start-date]}]
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
(-> @(p.a.eql/process env
                       {::dirstr (str "/home/kxygk/Data/IsoGSM/csv/")
                        :start-date #time/date "2011-01-01"
                        :end-date   #time/date "2021-01-01"} 
                      [{::data [:Temperature]}]))

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
  {:Rain-mm Rain-H2-16O
   :Rain-d18O (mapv calc-d18O
                   Rain-H2-16O
                   Rain-H2-18O)
   :Vapor-d18O (mapv calc-d18O
                   Vapor-H2-16O
                   Vapor-H2-18O)})
#_
(-> @(p.a.eql/process env
                      {::dirstr    (str "/home/kxygk/Data/IsoGSM/csv/")
                       :start-date #time/date "2011-01-01"
                       :end-date   #time/date "2021-01-01"} 
                      [{::data [:d18O-estimate]}]))
  
(def env
  (pci/register {::p.a.eql/parallel? true}
                [$read-tables
                 $-all-dates-vec
                 $add-dates
                 $extract-table-columns
                 $d18O]))


#_
(-> @(p.a.eql/process env
                      {::dirstr    (str "/home/kxygk/Data/IsoGSM/portland/extracted/")
                       :start-date #time/date "2011-01-01"
                       :end-date   #time/date "2025-01-01"} 
                      [{::data [:Rain-H2-16O]}]))
#_
(let [data @(p.a.eql/process env
                             {::dirstr    (str "/home/kxygk/Data/IsoGSM/csv/"
                                               #_"/home/kxygk/Data/IsoGSM/portland/extracted/")
                                         :start-date #time/date "2011-01-01"
                                         :end-date   #time/date "2025-01-01"} 
                                        [::table
                                         {::data [:Rain-d18O
                                                  :Vapor-d18O]}])
      table (-> data
                ::table)
      Rain-d18O (-> data
                    ::data
                    :Rain-d18O)
      Vapor-d18O (-> data
                     ::data
                     :Vapor-d18O)]
  (-> table
      (assoc :Rain-d18O
             Rain-d18O)
      (assoc :Vapor-d18O
             Vapor-d18O)
      (ds/write! "isogsm-krabis.csv")))
