(ns kxygk.anemoi.nakhon
  (:require [clojure.math]
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


(pco/defresolver $nakhon-filestr->table
  [{:kxygk.anemoi.central/keys [nakhon-filestr]}]
  {::pco/output [::nakhon-table]}
  (p/vthread {::nakhon-table
              (let [raw-table (-> nakhon-filestr
                                  (ds/->dataset {:dataset-name "Nakhon GHCNd"
                                                 :key-fn       normalize-colname}))]
                (-> raw-table
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
                    (assoc :Day
                           (range 1
                                  (-> raw-table
                                      ds/row-count
                                      inc )))))}))
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
                                                                  (apply +)))
                                               (mapv identity)
                                               sort)}))
#_
(-> @(p.a.eql/process env
                      {::nakhon-filestr (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                             "TH000048552.csv")}
                      [::nakhon-annual-rain-totals])
    ::nakhon-annual-rain-totals)



(pco/defresolver $nakhon-annual-winter-storm-count
  [{:kxygk.anemoi.central/keys [big-storm-mm]
    ::keys                     [nakhon-by-year]}]
  {::pco/output [::nakhon-annual-winter-storm-count]}
  (p/vthread {::nakhon-annual-winter-storm-count
              (->> (update-vals nakhon-by-year
                                (fn [year-table]
                                  (->> year-table
                                       :PRCP
                                       (filterv some?)
                                       (filterv #(> %
                                                    big-storm-mm))
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
  [{:kxygk.anemoi.central/keys [big-storm-mm]
    ::keys                     [nakhon-by-year]}]
  {::pco/output [::nakhon-annual-winter-storm-fraction]}
  (p/vthread {::nakhon-annual-winter-storm-fraction
              (->> (update-vals nakhon-by-year
                                (fn [year-table]
                                  (let [split-table (->> year-table
                                                         :PRCP
                                                         (filterv some?)
                                                         (group-by #(> %
                                                                       big-storm-mm)))]
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
                   sort)}))
#_
(-> @(p.a.eql/process env
                      {::nakhon-filestr (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                             "TH000048552.csv")}
                      [::nakhon-annual-winter-storm-count])
    ::nakhon-annual-winter-storm-count)



(pco/defresolver $nakhon-modern-table
  [{:kxygk.anemoi.central/keys [start-date
                                end-date]
    ::keys                     [nakhon-table]}]
  {::pco/output [::nakhon-modern-table]}
  (p/vthread {::nakhon-modern-table (let [modern-entries (-> nakhon-table
                                                             (ds/filter-column :DATE
                                                                               #(tick/> %
                                                                                        (tick/date start-date)))
                                                             (ds/filter-column :DATE
                                                                               #(tick/< %
                                                                                        (tick/date end-date))))]
                                      (assoc modern-entries
                                             :Day      ;; renumbers them with a rezeroes Day-0
                                             (range 1
                                                    (-> modern-entries
                                                        ds/row-count
                                                        inc))))}))

(def env
  (pci/register {::p.a.eql/parallel? true}
                [$nakhon-filestr->table
                 $nakhon-by-year
                 $nakhon-annual-rain-totals
                 $nakhon-annual-winter-storm-fraction
                 $nakhon-annual-winter-storm-count
                 $nakhon-modern-table]))
