(ns kxygk.anemoi.enso
  (:require [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
            [tech.v3.dataset              :as ds]
            [tick.core                    :as tick]
            [kxygk.anemoi.util]))

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


(pco/defresolver $read-table
  [{::keys [filestr]}]
  {::pco/output [::table]}
  {::table (-> filestr
               (ds/->dataset {:dataset-name "ENSO Index (Nino 3.4)"
                              :key-fn       kxygk.anemoi.util/normalize-colname})
               (ds/rename-columns [:Dates
                                   :EnsoIndex])
               (ds/filter-column :EnsoIndex
                                 #(not= %
                                        -9999.00))
               #_
               (ds/row-map (fn [enso-row]
                             (let [adjusted-date (tick/<< (enso-row :Dates)
                                                          (tick/new-period 0
                                                                           :months))]
                               {:Year  (tick/year adjusted-date )
                                :Month (tick/month adjusted-date)}))))})
#_
(->> @(p.a.eql/process env
                      {::filestr (str "/home/kxygk/Data/enso/"
                                      "nina34.anom.csv")}
                      [::table])
     ::table
     (into {}))

;; {:RefDate #tech.v3.dataset.column<packed-local-date>[905]
;; :RefDate
;; [1950-01-01, 1950-02-01, 1950-03-01, 1950-04-01, 1950-05-01, 1950-06-01, 1950-07-01, 1950-08-01, 1950-09-01, 1950-10-01, 1950-11-01, 1950-12-01, 1951-01-01, 1951-02-01, 1951-03-01, 1951-04-01, 1951-05-01, 1951-06-01, 1951-07-01, 1951-08-01...], :EnsoIndex #tech.v3.dataset.column<float64>[905]
;; :EnsoIndex
;; [-1.990, -1.690, -1.420, -1.540, -1.750, -1.270, -1.010, -0.9700, -0.9800, -1.030, -1.230, -1.310, -1.300, -1.040, -0.3800, -0.2300, -0.01000, 0.000, 0.3000, 0.1700...], :Year #tech.v3.dataset.column<object>[905]
;; :Year
;; [1950, 1950, 1950, 1950, 1950, 1950, 1950, 1950, 1950, 1950, 1950, 1950, 1951, 1951, 1951, 1951, 1951, 1951, 1951, 1951...], :Month #tech.v3.dataset.column<object>[905]
;; :Month
;; [JANUARY, FEBRUARY, MARCH, APRIL, MAY, JUNE, JULY, AUGUST, SEPTEMBER, OCTOBER, NOVEMBER, DECEMBER, JANUARY, FEBRUARY, MARCH, APRIL, MAY, JUNE, JULY, AUGUST...]}
;; ENSO Index (Nino 3.4) [905 4]:

;; |   :RefDate | :EnsoIndex | :Year |    :Month |
;; |------------|-----------:|-------|-----------|
;; | 1950-01-01 |      -1.99 |  1950 |   JANUARY |
;; | 1950-02-01 |      -1.69 |  1950 |  FEBRUARY |
;; | 1950-03-01 |      -1.42 |  1950 |     MARCH |
;; | 1950-04-01 |      -1.54 |  1950 |     APRIL |
;; | 1950-05-01 |      -1.75 |  1950 |       MAY |
;; | 1950-06-01 |      -1.27 |  1950 |      JUNE |
;; | 1950-07-01 |      -1.01 |  1950 |      JULY |
;; | 1950-08-01 |      -0.97 |  1950 |    AUGUST |
;; | 1950-09-01 |      -0.98 |  1950 | SEPTEMBER |
;; | 1950-10-01 |      -1.03 |  1950 |   OCTOBER |
;; |        ... |        ... |   ... |       ... |
;; | 2024-07-01 |       0.04 |  2024 |      JULY |
;; | 2024-08-01 |      -0.12 |  2024 |    AUGUST |
;; | 2024-09-01 |      -0.26 |  2024 | SEPTEMBER |
;; | 2024-10-01 |      -0.27 |  2024 |   OCTOBER |
;; | 2024-11-01 |      -0.25 |  2024 |  NOVEMBER |
;; | 2024-12-01 |      -0.60 |  2024 |  DECEMBER |
;; | 2025-01-01 |      -0.74 |  2025 |   JANUARY |
;; | 2025-02-01 |      -0.43 |  2025 |  FEBRUARY |
;; | 2025-03-01 |       0.01 |  2025 |     MARCH |
;; | 2025-04-01 |      -0.14 |  2025 |     APRIL |
;; | 2025-05-01 |      -0.16 |  2025 |       MAY |

(pco/defresolver $extract-table-columns
  [{::keys [table]}]
  {::pco/output [:Dates
                 :EnsoIndex]}
  ;;Should just be
  #_
  (into {}
        table)
  ;; but there is a bug: https://github.com/techascent/tech.ml.dataset/issues/479
  ;; Use this for now
  ;;#_
  (update-vals (into {}
                     table)
               vec))
#_
@(p.a.eql/process env
                  {::filestr (str "/home/kxygk/Data/enso/"
                                  "nina34.anom.csv")}
                  [:EnsoIndex])

;; (:RefDate :EnsoIndex :Year :Month)

(def env
  (pci/register {::p.a.eql/parallel? true}
                [$read-table
                 $extract-table-columns]))
