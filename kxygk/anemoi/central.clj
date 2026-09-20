(ns kxygk.anemoi.central
  (:require [kxygk.anemoi.airport :as airport]
            [kxygk.anemoi.enso :as enso]
            [kxygk.anemoi.index :as index]
            [kxygk.anemoi.isogsm :as isogsm]
            [kxygk.anemoi.generic :as generic]
            [kxygk.anemoi.ghcnd :as ghcnd]
            [kxygk.anemoi.nakhon :as nakhon]
            [kxygk.anemoi.plot :as plot]
            [kxygk.anemoi.stat :as stat]
            [kxygk.anemoi.tmd :as tmd]
            kxygk.pathmore.cache
            ;;
            [criterium.core :refer [bench]]
            [clj-async-profiler.core :as prof]
            ;;
            [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [com.wsscode.pathom3.interface.smart-map :as psm]
            [com.wsscode.pathom3.connect.runner :as pcr]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [com.wsscode.pathom3.connect.planner :as pcp]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [promesa.core :as p]))


(def *state
  (atom {::plot/width                    1800
         ::plot/height                   1300
         ::plot/scale                    100
         ::plot/margin-frac              0.1
         ::airport/enso-filestr          (str "/home/kxygk/Data/enso/"
                                              "nina34.anom.csv")
         ::airport/isotopes-filestr      (str "/home/kxygk/Data/airport/"
                                              "first-sheet-extracted.csv")
         ::airport/climate-index-filestr (str "/home/kxygk/Projects/imergination.wiki/krabdaily/"
                                              "climate-index.csv")
         ::airport/nakhon-filestr        (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                              ;;#_
                                              "TH000048552"
                                              ;; NAKHON - Winter storm target location
                                              #_
                                              "TH000048564"
                                              ;;PHUKET - Exposed West Coast            ***
                                              #_
                                              "TH000048565"
                                              ;;PHUKET AIRPORT - Shielded West Coast
                                              #_
                                              "TH000048551"
                                              ;;SURAT THANI - Midpoint (middle of map) ***
                                              #_
                                              "TH000048567"
                                              ;;TRANG - West Side of the Nakhon Range  ***
                                              #_
                                              "TH000048568"
                                              ;;SONGKHLA - Southern East Coast
                                              ".csv")
         ;; *** -> start on Jan 1st 1951
         ;;
         ;; From: https://www.ncei.noaa.gov/pub/data/ghcn/daily/ghcnd-stations.txt
         ;; TH000048551   9.1170   99.1500   11.0    SURAT THANI   
         ;; TH000048552   8.5330   99.9500    9.0    NAKHON SI THAMMARAT
         ;; TH000048564   7.8830   98.4000    3.0    PHUKET
         ;; TH000048565   8.1320   98.3170    9.0    PHUKET AIRPORT
         ;; TH000048567   7.5170   99.6170   16.0    TRANG
         ;; TH000048568   7.2000  100.6170    9.0    SONGKHLA
         ;; TH000048569   6.9170  100.4330   35.0    HAT YAI
         ::klang-filestr                 (str "/home/kxygk/Data/Tan2019/"
                                              "modern-part.csv")
         ;; New format
         ::enso-filestr                  (str "/home/kxygk/Data/enso/"
                                              "nina34.anom.csv")
         ::airport/filestr               (str "/home/kxygk/Data/airport/"
                                              "first-sheet-extracted.csv")
         ::index/filestr                 (str "/home/kxygk/Projects/imergination.wiki/krabdaily/"
                                              "climate-index.csv")
         ::isogsm/dirstr                 (str "/home/kxygk/Data/IsoGSM/"
                                              "csv/"
                                              #_"csv-nakhon/")
         ::airport/crazy-dates           #{#time/date "2017-07-30"}
         :start-date                     #time/date"2011-01-01"
         :end-date                       #time/date"2031-01-01" ;; Set to 2031 for now
         ::plot/cycle-start-value        2011
         ::plot/cycle-length             365
         ::plot/cycle-phase              0
         ::big-storm-mm                  80.0
         :above-index-threshold          99.9    ;; default to no threshold
         :below-index-threshold          99.9}))

#_
(-> @(p.a.eql/process env
                      @*state
                      [::airport/filestr]))

#_
(-> @(p.a.eql/process env
                      @*state
                      [:start-date]))

(pco/defresolver $repacked
  [{::plot/keys    [width
                    height
                    scale
                    margin-frac
                    cycle-start-value
                    cycle-length
                    cycle-phase]
    ::airport/keys [filestr
                    crazy-dates]
    ::index/keys   [filestr]
    ::isogsm/keys  [dirstr]
    :keys          [start-date
                    end-date]
    :as            inputs}]
  {::figures (merge {} #_inputs
                    {::modern (merge inputs
                                     #_
                                     {::airport/data {:start-date start-date}})}
                    #_#_
                    {::plot/airport-classified inputs}
                    {::plot/index-data inputs})})

(def plan-cache*
  (atom {}))

(def env
  (-> (pci/register {::p.a.eql/parallel? true}
                    [$repacked
                     airport/env
                     enso/env
                     index/env
                     isogsm/env
                     ghcnd/env
                     stat/env
                     tmd/env
                     plot/env
                     generic/env])
      (pcp/with-plan-cache plan-cache*)
      kxygk.pathmore.cache/inject-for-all-resolvers))

;;#_
(-> @(p.a.eql/process env
                      @*state
                      [{::figures [{::modern [::isogsm/table]}]}]))

#_
(-> @(p.a.eql/process env
                      @*state
                      [{::figures [{::modern [{::isogsm/data [:Date]}]}]}]))

#_
(-> @(p.a.eql/process env
                      @*state
                      [{::figures [{::modern [{::isogsm/data [:Days-from-start]}]}]}]))


#_
(-> @(p.a.eql/process env
                      @*state
                      [{::figures [{::modern [{::plot/day-isogsm-rain-d18O [:y]}]}]}]))
#_
(-> @(p.a.eql/process env
                      @*state
                      [{::figures [{::modern [{::plot/day-isogsm-rain-d18O [:xy-nonil]}]}]}]))
#_
(-> @(p.a.eql/process env
                      @*state
                      [{::figures [{::modern [{::plot/day-isogsm-vapor-d18O [:xy-nonil]}]}]}]))

#_
(-> @(p.a.eql/process env
                      @*state
                      [{::figures [{::modern [::plot/isogsm-vapor-d18O-layer]}]}]))


#_
(-> @(p.a.eql/process env
                      @*state
                      [{::figures [{::modern [::plot/day-num-max]}]}]))


;;#_
(defn gen-plots []
  (let [figs (->> [{::figures [{::modern [{::plot/meteoric-water-line-subplot [::plot/svg]}
                                          {::plot/amount-effect-subplot [::plot/svg]}
                                          {::plot/rain-subplot [::plot/svg]}
                                          {::plot/rain-d18O-subplot [::plot/svg]}
                                          {::plot/rain-d18O-average-subplot [::plot/svg]}
                                          {::plot/rain-d18O-classified-subplot [::plot/svg]}
                                          {::plot/rain-d18O-classified-average-subplot [::plot/svg]}
                                          {::plot/index-subplot [::plot/svg]}
                                          {::plot/index-d18O-subplot [::plot/svg]}
                                          {::plot/isotope-d18O-classified-average-subplot [::plot/svg]}
                                          {::plot/hist-rain-classified-subplot [::plot/svg]}
                                          {::plot/isogsm-rain-d18O-subplot [::plot/svg]}
                                          #_
                                          {::plot/isotope-d18O-classified-average-subplot [::plot/svg]}]}]}]
                  (p.a.eql/process env
                                   (merge @*state
                                          {::big-storm-mm 0.0}))
                  deref
                  ::figures
                  ::modern)]
    (->> figs
         (mapv (fn [[key
                     value]]
                 (->> value
                      ::plot/svg
                      (spit (str "./out/all-"
                                 (name key)
                                 ".svg"))))))))

(time (gen-plots))

#_
(pco/defresolver $single-figures
  [inputs]
  {::pco/input  [::start-date
                 ::end-date
                 ::big-storm-mm
                 ::airport/full-table
                 #_#_#_#_#_
                 ::nakhon/nakhon-table
                 ::nakhon/nakhon-modern-table
                 ::nakhon/nakhon-annual-rain-totals
                 ::nakhon/nakhon-annual-winter-storm-fraction
                 ::nakhon/nakhon-annual-winter-storm-count
                 ::airport/klang-year-d18O
                 :above-index-threshold
                 :below-index-threshold
                 ::cycle-start-value
                 ::cycle-length
                 ::cycle-phase]
   ::pco/output [::single-figures]}
  (let [above-cutoff (:above-index-threshold inputs)
        below-cutoff (:below-index-threshold inputs)
        above-filt   #(and (> (:Above-Index %)
                              0.0)
                           (< (:Above-Index %)
                              above-cutoff))
        below-filt   #(and (> (:Below-Index %)
                              0.0)
                           (< (:Below-Index %)
                              below-cutoff))
        both-filt    #(or (above-filt %)
                          (below-filt %))]
    {::single-figures {
                       ::plot/width               1800
                       ::plot/height              1300
                       ::plot/scale               100
                       ::plot/margin-frac         0.1
                       #_#_
                       ::plot/table               (::airport/full-table inputs)
                       ;;
                       ::plot/d18O-rain           {::tmd/x-key           :d18O
                                                   ::tmd/y-key           :Rain-mm
                                                   ::tmd/table-to-filter (::airport/full-table inputs)
                                                   ::tmd/col-filter-fn   both-filt}
                       ::plot/rain-data           {::tmd/x-key           :Day
                                                   ::tmd/y-key           :Rain-mm
                                                   ::tmd/table-to-filter (::airport/full-table inputs)
                                                   ::tmd/col-filter-fn   both-filt}
                       ::plot/d18O-data           {::tmd/x-key           :Day
                                                   ::tmd/y-key           :d18O
                                                   ::tmd/meta-keys       [:Rain-mm]
                                                   ::tmd/table-to-filter (::airport/full-table inputs)
                                                   ::tmd/col-filter-fn   both-filt}
                       ::plot/d18O-extremes-data  {::tmd/x-key           :Day
                                                   ::tmd/y-key           :d18O
                                                   ::tmd/meta-keys       [:Rain-mm]
                                                   ::tmd/table-to-filter (::airport/full-table inputs)
                                                   ::tmd/col-filter-fn   #(> (:Below-Index %)
                                                                             0.06)}
                       ::plot/d18O-other-data     {::tmd/x-key           :Day
                                                   ::tmd/y-key           :d18O
                                                   ::tmd/meta-keys       [:Rain-mm]
                                                   ::tmd/table-to-filter (::airport/full-table inputs)
                                                   ::tmd/col-filter-fn   #(< (:Below-Index %)
                                                                             0.06)}
                       ::plot/d18O-above-data     {::tmd/x-key           :Day
                                                   ::tmd/y-key           :d18O
                                                   ::tmd/meta-keys       [:Rain-mm]
                                                   ::tmd/table-to-filter (::airport/full-table inputs)
                                                   ::tmd/col-filter-fn   above-filt}
                       ::plot/d18O-below-data     {::tmd/x-key           :Day
                                                   ::tmd/y-key           :d18O
                                                   ::tmd/meta-keys       [:Rain-mm]
                                                   ::tmd/table-to-filter (::airport/full-table inputs)
                                                   ::tmd/col-filter-fn   below-filt}
                       ::plot/d18O-rain-above     {::tmd/x-key           :d18O
                                                   ::tmd/y-key           :Rain-mm
                                                   ::tmd/table-to-filter (::airport/full-table inputs)
                                                   ::tmd/col-filter-fn   above-filt}
                       ::plot/d18O-rain-below     {::tmd/x-key           :d18O
                                                   ::tmd/y-key           :Rain-mm
                                                   ::tmd/table-to-filter (::airport/full-table inputs)
                                                   ::tmd/col-filter-fn   below-filt}
                       ;;
                       ;; d18O vs Monsoon
                       ::plot/d18O-monsoon-above  {::tmd/x-key           :d18O
                                                   ::tmd/y-key           :Above-Index
                                                   ::tmd/meta-keys       [:Rain-mm]
                                                   ::tmd/table-to-filter (::airport/full-table inputs)
                                                   ::tmd/col-filter-fn   above-filt}
                       ::plot/d18O-monsoon-below  {::tmd/x-key           :d18O
                                                   ::tmd/y-key           :Below-Index
                                                   ::tmd/meta-keys       [:Rain-mm]
                                                   ::tmd/table-to-filter (::airport/full-table inputs)
                                                   ::tmd/col-filter-fn   below-filt}
                       ::plot/d18O-range-min      -25
                       ::plot/d18O-range-max      10
                       ::plot/above?-data         {::tmd/x-key :d18O
                                                   ::tmd/y-key :Above?
                                                   ::tmd/table (::airport/full-table inputs)}
                       ::plot/index-above         {::tmd/x-key           :Day
                                                   ::tmd/y-key           :Above-Index
                                                   ::tmd/table-to-filter (::airport/full-table inputs)
                                                   ::tmd/col-filter-fn   above-filt}
                       ::plot/index-below         {::tmd/x-key           :Day
                                                   ::tmd/y-key           :Below-Index
                                                   ::tmd/table-to-filter (::airport/full-table inputs)
                                                   ::tmd/col-filter-fn   below-filt}
                       ::plot/cycle-start-value   (::cycle-start-value inputs)
                       ::plot/cycle-length        (::cycle-length inputs)
                       ::plot/cycle-phase         (::cycle-phase inputs)
                       ::plot/klang-year-d18O     (::airport/klang-year-d18O inputs)
                       ::plot/nakhon-gauge        {::ghcnd/raingauge-filestr  (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                                                                   "TH000048552"
                                                                                   ".csv")
                                                   ::tmd/meta-keys            [:fill] ;; Should add a date!
                                                   ::ghcnd/storm-threshold-mm (-> inputs
                                                                                  ::big-storm-mm)}
                       ::plot/nakhon-gauge-modern {::ghcnd/raingauge-filestr  (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                                                                   "TH000048552"
                                                                                   ".csv")
                                                   ::ghcnd/storm-threshold-mm (-> inputs
                                                                                  ::big-storm-mm)
                                                   ::ghcnd/start-date         (-> inputs
                                                                                  ::start-date)
                                                   ::ghcnd/end-date           (-> inputs
                                                                                  ::end-date)}
                       ::plot/phuket-gauge        {::ghcnd/raingauge-filestr  (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                                                                   "TH000048565"
                                                                                   ".csv")
                                                   ::ghcnd/storm-threshold-mm (-> inputs
                                                                                  ::big-storm-mm)}
                       ::plot/phuket-gauge-modern {::ghcnd/raingauge-filestr  (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                                                                   "TH000048565"
                                                                                   ".csv")
                                                   ::ghcnd/storm-threshold-mm (-> inputs
                                                                                  ::big-storm-mm)
                                                   ::ghcnd/start-date         (-> inputs
                                                                                  ::start-date)
                                                   ::ghcnd/end-date           (-> inputs
                                                                                  ::end-date)}
                       }}))

#_
(let [figs (->> [{::single-figures [{::plot/nakhon-gauge [{::ghcnd/daily-rain [;;#_
                                                                               :xy-all
                                                                               #_
                                                                               ::tmd/meta-keys]}
                                                          {::ghcnd/annual-storm-fraction [:xy-nonil]}]}]}]
                (p.a.eql/process env
                                 (merge @*state
                                        {::big-storm-mm 100.0}))
                deref
                ::single-figures)]
  (->> figs
       ::plot/nakhon-gauge
       
       ::ghcnd/annual-storm-fraction))


#_
(defn gen-plots []
  (let [figs (->> [{::single-figures [{::plot/rain-subplot [::plot/svg]}
                                      {::plot/index-subplot [::plot/svg]}
                                      {::plot/rain-d18O-subplot [::plot/svg]}
                                      {::plot/rain-d18O-average-subplot [::plot/svg]}
                                      {::plot/rain-d18O-classified-subplot [::plot/svg]}
                                      {::plot/rain-d18O-classified-average-subplot [::plot/svg]}
                                      {::plot/isotope-d18O-classified-average-subplot [::plot/svg]}
                                      {::plot/nakhon-d18O-classified-average-subplot [::plot/svg]}
                                      {::plot/index-d18O-subplot [::plot/svg]}
                                      {::plot/index-d18O-big-events-subplot [::plot/svg]}
                                      {::plot/hist-count-all-subplot [::plot/svg]}
                                      {::plot/hist-count-classified-subplot [::plot/svg]}
                                      {::plot/hist-count-subplot [::plot/svg]}
                                      {::plot/hist-rain-all-subplot [::plot/svg]}
                                      {::plot/hist-rain-classified-subplot [::plot/svg]}
                                      {::plot/hist-rain-subplot [::plot/svg]}
                                      {::plot/hist-monsoon-classified-subplot [::plot/svg]}
                                      {::plot/klang-vs-nakhon-subplot [::plot/svg] }      ;; $
                                      {::plot/klang-vs-nakhon-bigrain-fraction-subplot [::plot/svg] }
                                      {::plot/klang-vs-nakhon-bigrain-count-subplot [::plot/svg] }
                                      {::plot/klang-vs-phuket-nakhon-subplot [::plot/svg] }]}]
                  (p.a.eql/process env
                                   (merge @*state
                                          {::big-storm-mm 0.0}))
                  deref
                  ::single-figures)]
    (->> figs
         (mapv (fn [[key
                     value]]
                 (->> value
                      ::plot/svg
                      (spit (str "./out/all-"
                                 (name key)
                                 ".svg"))))))))
#_
(time (gen-plots))

#_
(let [figs (->> [{::single-figures [{::plot/nakhon-gauge [{::ghcnd/annual-rain [:xy-all]}
                                                          {::ghcnd/annual-storm-rain [:y]}]}]}]
                (p.a.eql/process env
                                 (merge @*state
                                        {::big-storm-mm 100.0}))
                deref
                ::single-figures)]
  (->> figs
       ::plot/nakhon-gauge
       ::ghcnd/annual-storm-rain
       :y))


#_
(let [figs (->> [{::single-figures [{::plot/phuket-annual-rain-totals [:xy-nonil]}]}]
                (p.a.eql/process env
                                 (merge @*state
                                        {::big-storm-mm 100.0}))
                deref
                ::single-figures)]
  (->> figs
       ::plot/phuket-annual-rain-totals))


#_
(defn process-my-plots
  []
  (->> [{::single-figures [{::plot/rain-subplot [::plot/svg]}
                           {::plot/index-subplot [::plot/svg]}
                           {::plot/rain-d18O-subplot [::plot/svg]}
                           {::plot/rain-d18O-average-subplot [::plot/svg]}
                           {::plot/rain-d18O-classified-subplot [::plot/svg]}
                           {::plot/rain-d18O-classified-average-subplot [::plot/svg]}
                           {::plot/isotope-d18O-classified-average-subplot [::plot/svg]}
                           {::plot/nakhon-d18O-classified-average-subplot [::plot/svg]}
                           {::plot/index-d18O-subplot [::plot/svg]}
                           {::plot/index-d18O-big-events-subplot [::plot/svg]}
                           {::plot/hist-count-all-subplot [::plot/svg]}
                           {::plot/hist-count-classified-subplot [::plot/svg]}
                           {::plot/hist-count-subplot [::plot/svg]}
                           {::plot/hist-rain-all-subplot [::plot/svg]}
                           {::plot/hist-rain-classified-subplot [::plot/svg]}
                           {::plot/hist-rain-subplot [::plot/svg]}
                           {::plot/hist-monsoon-classified-subplot [::plot/svg]}
                           {::plot/klang-vs-nakhon-subplot [::plot/svg] }      ;; $
                           {::plot/klang-vs-nakhon-bigrainfraction-subplot [::plot/svg] }
                           {::plot/klang-vs-phuket-nakhon-subplot [::plot/svg] }]}]
       (p.a.eql/process env
                        (merge @*state
                               {::big-storm-mm 40.0}))))
#_
(time (deref (process-my-plots)))

#_
(prof/profile  (process-my-plots))
#_
(prof/profile (dotimes [_ 20] (process-my-plots)))


#_
(spit "meta.edn"
      (-> (process-my-plots)
          deref
          meta))
#_
(def remove-keys
  #{:com.wsscode.pathom3.connect.runner/node-resolver-input
    :com.wsscode.pathom3.connect.runner/node-resolver-output})
#_
(defn walk-safe [f x]
  (cond
    (map? x)
    (f (into {} (map (fn [[k v]] [k (walk-safe f v)]) x)))

    (sequential? x)
    (mapv #(walk-safe f %) x)

    (set? x)
    (into #{} (map #(walk-safe f %) x))

    :else
    x))
#_
(defn strip-pathom-noise [x]
  (walk-safe
    (fn [m]
      (if (map? m)
        (reduce dissoc m remove-keys)
        m))
    x))
#_
(->> (process-my-plots)
     deref
     meta
     ::pcr/run-stats
     psm/smart-run-stats
     strip-pathom-noise
     clojure.pprint/pprint
     with-out-str
     (spit "stat.edn")
     #_
     ::pcr/process-run-duration-ms)

#_
(prof/profile  
    (dotimes [_ 20]
      (gen-plots)))

#_
(prof/profile  {:event :wall}
  (dotimes [_ 20]
    (gen-plots)))
#_
(let [pid (.pid (java.lang.ProcessHandle/current))]
  (clojure.java.shell/sh "jcmd"
                         (str pid)
                         "JFR.start"
                         "name=test"
                         "settings=profile"
                         "filename=./from-repl.jfr")
  (Thread/sleep 200)
  (gen-plots)q
  (Thread/sleep 800)
  (clojure.java.shell/sh "jcmd"
                         (str pid)
                         "JFR.stop"
                         "name=test"))
#_
(let [figs (->> [{::single-figures [{::plot/rain-subplot [::plot/svg]}
                                    {::plot/index-subplot [::plot/svg]}
                                    {::plot/index-d18O-subplot [::plot/svg]}
                                    {::plot/rain-d18O-subplot [::plot/svg]}
                                    {::plot/rain-d18O-average-subplot [::plot/svg]}
                                    {::plot/rain-d18O-classified-subplot [::plot/svg]}
                                    {::plot/rain-d18O-classified-average-subplot [::plot/svg]}
                                    {::plot/hist-count-all-subplot [::plot/svg]}
                                    {::plot/hist-count-classified-subplot [::plot/svg]}
                                    {::plot/hist-count-subplot [::plot/svg]}
                                    {::plot/hist-rain-all-subplot [::plot/svg]}
                                    {::plot/hist-rain-classified-subplot [::plot/svg]}
                                    {::plot/hist-rain-subplot [::plot/svg]}
                                    {::plot/hist-monsoon-classified-subplot [::plot/svg]}]}]
                (p.a.eql/process env
                                 (merge @*state
                                        {:above-index-threshold 0.99   ;; <------- This one adds thresholds
                                         :below-index-threshold 0.05}))
                deref
                ::single-figures)]
  (->> figs
       (mapv (fn [[key
                   value]]
               (->> value
                    ::plot/svg
                    (spit (str "./out/bigmonsoon-"
                               (name key)
                               ".svg")))))))
#_
(->> [::airport/below-table]
     (p.a.eql/process env
                      @*state)
     deref
     ::timeseries-figure)
#_
(->> [{::timeseries-figure [{::plot/d18O-data [{:y [{::stat/hist [{:y [::stat/max]}]}]}]}]}]
     (p.a.eql/process env
                      @*state)
     deref)

#_
(pco/defresolver $simple-figures
  [inputs]
  {::pco/input  [::airport/full-table]
   ::pco/outout [::simple-figures]}
  {::simple-figures {::plot/width           1800
                     ::plot/height          1300 #_2900 ;;650
                     ::plot/scale           100
                     ::plot/margin-frac     0.1
                     ::plot/table           (::airport/full-table inputs)
                     ::plot/rain-totals     (::nakhon/nakhon-annual-rain-totals inputs)
                     ::plot/big-rain-totals (::nakhon/nakhon-annual-rain-totals inputs)
                     ::plot/big-storm-mm    80.0}})
(identity @*state)

#_
(->> [::nakhon/nakhon-annual-winter-storm-fraction]
     (p.a.eql/process env
                      @*state)
     deref
     ::nakhon/nakhon-annual-winter-storm-fraction
     vec)

#_
(->> [::nakhon/nakhon-annual-winter-storm-count]
     (p.a.eql/process env
                      @*state)
     deref
     ::nakhon/nakhon-annual-winter-storm-count
     vec)

#_
(->> [::nakhon/nakhon-by-year]
     (p.a.eql/process env
                      @*state)
     deref
     ::nakhon/nakhon-by-year
     vec)

#_
(->> [::nakhon/nakhon-table]
     (p.a.eql/process env
                      @*state)
     deref
     ::nakhon/nakhon-table)
#_
(->> [::nakhon/nakhon-modern-table]
     (p.a.eql/process env
                      @*state)
     deref
     ::nakhon/nakhon-modern-table)
#_
(->> [::airport/klang-table
      ::airport/klang-year-d18O]
     (p.a.eql/process env
                      @*state)
     deref
     ::airport/klang-year-d18O)
#_
(->> [{::simple-figures [{::plot/meteoric-water-line-subplot [::plot/svg]}]}]
     (p.a.eql/process env
                      @*state)
     deref
     ::simple-figures
     ::plot/meteoric-water-line-subplot
     ::plot/svg
     (spit "./out/meteoric-water-line.svg"))
#_
(->> [{::simple-figures [{::plot/monthly-averages-subplot [::plot/svg]}]}]
     (p.a.eql/process env
                      @*state)
     deref
     ::simple-figures
     ::plot/monthly-averages-subplot
     ::plot/svg
     (spit "./out/monthly-averages.svg"))
#_
(->> [{::simple-figures [{::plot/amount-effect-subplot [::plot/svg]}]}]
     (p.a.eql/process env
                      @*state)
     deref
     ::simple-figures
     ::plot/amount-effect-subplot
     ::plot/svg
     (spit "./out/amount-effect.svg"))


#_{#time/month "MAY" -4.39515931116292, #time/month "DECEMBER" -4.87025263610812, #time/month "NOVEMBER" -5.892095496817324, #time/month "JUNE" -4.061993906191126, #time/month "OCTOBER" -5.551088488343817, #time/month "AUGUST" -4.740541788139581, #time/month "FEBRUARY" -3.773729056965054, #time/month "APRIL" -5.222508679794652, #time/month "JANUARY" -8.81139040857962, #time/month "SEPTEMBER" -5.313021538364952, #time/month "JULY" -3.983077143718893, #time/month "MARCH" -7.510604884306495}

#_
(->> [{::rain-monsoon-figure [{::plot/rain-subplot [::plot/svg]}]}]
     (p.a.eql/process env
                      @*state)
     deref
     ::rain-monsoon-figure
     ::plot/rain-subplot
     ::plot/svg
     (spit "test-only.svg"))



#_
(pco/defresolver $rain-monsoon-figure
  [inputs]
  {::pco/input  [::airport/full-table
                 ::days-vs-rain
                 ::days-vs-d18O
                 ::days-vs-above?
                 ::days-vs-index-above
                 ::days-vs-index-below
                 ::cycle-start-value
                 ::cycle-length
                 ::cycle-phase]
   ::pco/output [::rain-monsoon-figure]}
  {::rain-monsoon-figure {::plot/width             1800
                          ::plot/height            650
                          ::plot/scale             75
                          ::plot/margin-frac       0.1
                          ;;
                          ::plot/d18O-rain         {::tmd/x-key :d18O
                                                    ::tmd/y-key :Rain-mm
                                                    ::tmd/table (::airport/full-table inputs)}
                          ::plot/rain-data         (::days-vs-rain inputs)
                          ::plot/d18O-data         (::days-vs-d18O inputs)
                          ::plot/d18O-above-data   {::tmd/x-key :Day
                                                    ::tmd/y-key :d18O
                                                    ::tmd/table (::airport/above-table inputs)}
                          ::plot/d18O-below-data   {::tmd/x-key :Day
                                                    ::tmd/y-key :d18O
                                                    ::tmd/table (::airport/below-table inputs)}
                          ::plot/d18O-rain-above   {::tmd/x-key :d18O
                                                    ::tmd/y-key :Rain-mm
                                                    ::tmd/table (::airport/above-table inputs)}
                          ::plot/d18O-rain-below   {::tmd/x-key :d18O
                                                    ::tmd/y-key :Rain-mm
                                                    ::tmd/table (::airport/below-table inputs)}
                          ::plot/above?-data       (::days-vs-above? inputs)
                          ::plot/index-above       (::days-vs-index-above inputs)
                          ::plot/index-below       (::days-vs-index-below inputs)
                          ::plot/cycle-start-value (::cycle-start-value inputs)
                          ::plot/cycle-length      (::cycle-length inputs)
                          ::plot/cycle-phase       (::cycle-phase inputs)}})
#_
(let [figs (->> [{::rain-monsoon-figure [{::plot/rain-d18O-index-2stack [::plot/svg]}
                                         {::plot/rain-d18O-classified-index-2stack [::plot/svg]}]}]
                (p.a.eql/process env
                                 @*state)
                deref
                ::rain-monsoon-figure)]
  (->> figs
       (mapv (fn [[key
                   value]]
               (->> value
                    ::plot/svg
                    (spit (str (name key)
                               ".svg")))))))



#_
(->> [{::rain-monsoon-figure [{::plot/rain-subplot [::plot/svg]}]}]
     (p.a.eql/process env
                      @*state)
     deref
     ::rain-monsoon-figure
     ::plot/rain-subplot
     ::plot/svg
     (spit "test-only.svg"))



#_
(->> [{:x [::stat/standard-mean]}]
     (p.a.eql/process env
                      {:x {:data-vec [1
                                      2
                                      nil
                                      3
                                      4]}})
     deref)
#_
(->> [{::rain-monsoon-figure [{::plot/rain-index-stack [::plot/svg]}]}]
     (p.a.eql/process env
                      @*state)
     deref)
#_
(-> @(p.a.eql/process env
                      @*state
                      [{::days-vs-rain-figure [{::plot/index-above [:xy-nonil]}]}]))
#_
(->> @(p.a.eql/process env
                       (assoc @*state
                              ::plot/width
                              1000)
                       [{::days-vs-rain-figure [{::plot/rain-subplot [::plot/svg]}]}])
     ::days-vs-rain-figure
     ::plot/rain-subplot
     ::plot/svg
     (spit "test-rain.svg"))
#_
(->> @(p.a.eql/process env
                       (assoc @*state
                              ::plot/width
                              1000)
                       [{::days-vs-rain-figure [{::plot/index-subplot [::plot/svg]}]}])
     ::days-vs-rain-figure
     ::plot/index-subplot
     ::plot/svg
     (spit "test-index.svg"))

#_#_

(def plan-cache*
  (atom {}))

(def env
  (-> (pci/register {::p.a.eql/parallel? true}
                    [airport/env
                     enso/env
                     index/env
                     ghcnd/env
                     stat/env
                     tmd/env
                     tock/env
                     plot/env
                     generic/env
                     $single-figures
                     $simple-figures])
      (pcp/with-plan-cache plan-cache*)
      kxygk.pathmore.cache/inject-for-all-resolvers))


#_
(-> @(p.a.eql/process env
                      @*state
                      [:Months]))
#_
(-> @(p.a.eql/process env
                      {::airport/filestr     (str "/home/kxygk/Data/airport/"
                                                  "first-sheet-extracted.csv")
                       ::enso/filestr        (str "/home/kxygk/Data/enso/"
                                                  "nina34.anom.csv")
                       ::index/filestr       (str "/home/kxygk/Projects/imergination.wiki/krabdaily/"
                                                  "climate-index.csv")
                       ::index/start-date    #inst"2011-01-01"
                       ::index/end-date      #inst"2021-01-01"
                       ::airport/crazy-dates #{#time/date "2017-07-30"}}
                      [::airport/full-table]))











;; PRINT TABLES TO FILES
#_
(let [{::airport/keys [full-table]} (->> [::airport/full-table]
                                         (p.a.eql/process env
                                                          @*state)
                                         deref) ]
  (tech.v3.dataset/write! full-table
                          "full-table.txt"))


;; Look at very depleted Summer Monsoon samples
;; (I don't see any trend or reason)
#_
(let [{::airport/keys [full-table]} (->> [::airport/full-table]
                                         (p.a.eql/process env
                                                          @*state)
                                         deref) ]
  (-> full-table
      (tech.v3.dataset/sort-by-column :GMWL-d18O)
      (tech.v3.dataset/filter-column :Above?)))



;; PROFILING
;; Current issue is that if you profile the wall time,
;; the code is spawning so many threads that 2/3rd of the time is spent sitting aroundw waiting on locks.
;; Likely issue is TMD spawning VThreads and Pathom spawning it's own thread pool
;; see: https://github.com/techascent/tech.ml.dataset/issues/475
;; Performance is a non-issue for this project,
;; so this is on the backburner..
#_
(prof/profile ;; {:event :wall} ;; this flag makes the flamegraph capture wall time and shows waiting for threads
    (bench (with-out-str (let [figs (->> [{::single-figures [{::plot/rain-subplot [::plot/svg]}
                                                             {::plot/index-subplot [::plot/svg]}
                                                             {::plot/rain-d18O-subplot [::plot/svg]}
                                                             {::plot/rain-d18O-average-subplot [::plot/svg]}
                                                             {::plot/rain-d18O-classified-subplot [::plot/svg]}
                                                             {::plot/rain-d18O-classified-average-subplot [::plot/svg]}
                                                             {::plot/isotope-d18O-classified-average-subplot [::plot/svg]}
                                                             {::plot/nakhon-d18O-classified-average-subplot [::plot/svg]}
                                                             {::plot/index-d18O-subplot [::plot/svg]}
                                                             {::plot/index-d18O-big-events-subplot [::plot/svg]}
                                                             {::plot/hist-count-all-subplot [::plot/svg]}
                                                             {::plot/hist-count-classified-subplot [::plot/svg]}
                                                             {::plot/hist-count-subplot [::plot/svg]}
                                                             {::plot/hist-rain-all-subplot [::plot/svg]}
                                                             {::plot/hist-rain-classified-subplot [::plot/svg]}
                                                             {::plot/hist-rain-subplot [::plot/svg]}
                                                             {::plot/hist-monsoon-classified-subplot [::plot/svg]}
                                                             {::plot/klang-vs-nakhon-subplot [::plot/svg] }      ;; $
                                                             {::plot/klang-vs-nakhon-bigrainfraction-subplot [::plot/svg] }
                                                             {::plot/klang-vs-phuket-nakhon-subplot [::plot/svg] }]}]
                                         (p.eql/process env
                                                        @*state)
                                         ;;deref
                                         ::single-figures)]
                           (->> figs
                                (mapv (fn [[key
                                            value]]
                                        (->> value
                                             ::plot/svg))))))))
;; Single run
#_
(prof/profile ;; {:event :wall} ;; this flag makes the flamegraph capture wall time and shows waiting for threads
    (dotimes [_ 200]
      (with-out-str (let [figs (->> [{::single-figures [{::plot/rain-subplot [::plot/svg]}
                                                        {::plot/index-subplot [::plot/svg]}
                                                        {::plot/rain-d18O-subplot [::plot/svg]}
                                                        {::plot/rain-d18O-average-subplot [::plot/svg]}
                                                        {::plot/rain-d18O-classified-subplot [::plot/svg]}
                                                        {::plot/rain-d18O-classified-average-subplot [::plot/svg]}
                                                        {::plot/isotope-d18O-classified-average-subplot [::plot/svg]}
                                                        {::plot/nakhon-d18O-classified-average-subplot [::plot/svg]}
                                                        {::plot/index-d18O-subplot [::plot/svg]}
                                                        {::plot/index-d18O-big-events-subplot [::plot/svg]}
                                                        {::plot/hist-count-all-subplot [::plot/svg]}
                                                        {::plot/hist-count-classified-subplot [::plot/svg]}
                                                        {::plot/hist-count-subplot [::plot/svg]}
                                                        {::plot/hist-rain-all-subplot [::plot/svg]}
                                                        {::plot/hist-rain-classified-subplot [::plot/svg]}
                                                        {::plot/hist-rain-subplot [::plot/svg]}
                                                        {::plot/hist-monsoon-classified-subplot [::plot/svg]}
                                                        {::plot/klang-vs-nakhon-subplot [::plot/svg] }      ;; $
                                                        {::plot/klang-vs-nakhon-bigrainfraction-subplot [::plot/svg] }
                                                        {::plot/klang-vs-phuket-nakhon-subplot [::plot/svg] }]}]
                                    (p.a.eql/process env
                                                     @*state)
                                    ;;deref
                                    ::single-figures)]
                      (->> figs
                           (mapv (fn [[key
                                       value]]
                                   (->> value
                                        ::plot/svg))))))))

;; WITH THREAD
;; Evaluation count : 120 in 60 samples of 2 calls.
;;              Execution time mean : 609.654037 ms
;;     Execution time std-deviation : 27.645423 ms
;;    Execution time lower quantile : 566.101235 ms ( 2.5%)
;;    Execution time upper quantile : 654.923436 ms (97.5%)
;;                    Overhead used : 9.451493 ns

;; Found 1 outliers in 60 samples (1.6667 %)
;; 	low-severe	 1 (1.6667 %)
;;  Variance from outliers : 31.9262 % Variance is moderately inflated by outliers


;; WITH VTHREAD

;; Evaluation count : 180 in 60 samples of 3 calls.
;;              Execution time mean : 567.565452 ms
;;     Execution time std-deviation : 43.409156 ms
;;    Execution time lower quantile : 491.394603 ms ( 2.5%)
;;    Execution time upper quantile : 650.390960 ms (97.5%)
;;                    Overhead used : 9.432664 ns


;; Evaluation count : 120 in 60 samples of 2 calls.
;;              Execution time mean : 581.320123 ms
;;     Execution time std-deviation : 47.226751 ms
;;    Execution time lower quantile : 507.845698 ms ( 2.5%)
;;    Execution time upper quantile : 668.803132 ms (97.5%)
;;                    Overhead used : 12.174187 ns

;; Found 2 outliers in 60 samples (3.3333 %)
;; 	low-severe	 1 (1.6667 %)
;; 	low-mild	 1 (1.6667 %)
;;  Variance from outliers : 60.1662 % Variance is severely inflated by outliers


;; After threading `plot`
;; Evaluation count : 180 in 60 samples of 3 calls.
;;              Execution time mean : 494.456310 ms
;;     Execution time std-deviation : 35.742829 ms
;;    Execution time lower quantile : 434.094657 ms ( 2.5%)
;;    Execution time upper quantile : 574.932220 ms (97.5%)
;;                    Overhead used : 9.814428 ns

;; Found 2 outliers in 60 samples (3.3333 %)
;; 	low-severe	 2 (3.3333 %)
;;  Variance from outliers : 53.4924 % Variance is severely inflated by outliers
