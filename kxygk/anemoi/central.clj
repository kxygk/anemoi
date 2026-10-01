(ns kxygk.anemoi.central
  (:require [kxygk.anemoi.airport :as airport]
            [kxygk.anemoi.enso :as enso]
            [kxygk.anemoi.index :as index]
            [kxygk.anemoi.isogsm :as isogsm]
            [kxygk.anemoi.ghcnd :as ghcnd]
            [kxygk.anemoi.klang :as klang]
            [kxygk.anemoi.nakhon :as nakhon]
            [kxygk.anemoi.plot :as plot]
            [kxygk.pathmore.core :as pathmore]
            kxygk.mathom.core
            kxygk.pathmore.cache
            ;;
            [criterium.core :refer [bench]]
            [clj-async-profiler.core :as prof]
            ;;
            [medley.core :as medley]
            [tick.core                    :as tick]
            [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [com.wsscode.pathom3.interface.smart-map :as psm]
            [com.wsscode.pathom3.connect.runner :as pcr]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [com.wsscode.pathom3.connect.planner :as pcp]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [promesa.core :as p]))

(pathmore/clean-ns!)

(def *state
  (atom {::plot/width                    2800
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
         ::index/start-date              #time/date"2011-01-01"
         ::index/ended-date              #time/date"2031-01-01"
         :nakhon-gauge                   {::ghcnd/raingauge-filestr  (str "/home/kxygk/Data/GHCNd/daily-summaries-latest/"
                                                                          "TH000048552"
                                                                          ".csv")
                                          #_#_#_#_#_#_
                                          ::ghcnd/storm-threshold-mm (-> inputs
                                                                         ::big-storm-mm)
                                          ::ghcnd/start-date         (-> inputs
                                                                         ::start-date)
                                          ::ghcnd/end-date           (-> inputs
                                                                         ::end-date)}
         #_#_#_#_
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

#_
(time (gen-plots))


(pco/defresolver $global-time-bounds
  [{::keys [day-zero
            stop-day]}]
  {::pco/output [::airport/day-zero
                 ::enso/day-zero
                 ::index/day-zero
                 ::isogsm/day-zero
                 ::ghcnd/day-zero
                 ::klang/day-zero
                 ::plot/day-zero]}
  {::airport/day-zero day-zero
   ::enso/day-zero day-zero
   ::index/day-zero day-zero
   ::isogsm/day-zero day-zero
   ::ghcnd/day-zero day-zero
   ::klang/day-zero day-zero
   ::plot/day-zero day-zero
   :time-window-days (tick/days (tick/between day-zero
                                              stop-day))})

(pco/defresolver $repacked
  [inputs]
  {::pco/input  [{:nakhon-gauge [::ghcnd/raingauge-filestr]}
                 ::plot/width
                 ::plot/height
                 ::plot/scale
                 ::plot/margin-frac
                 ::plot/cycle-start-value
                 ::plot/cycle-length
                 ::plot/cycle-phase
                 ::airport/filestr
                 ::airport/start-date
                 ::airport/ended-date
                 ::airport/crazy-dates
                 ::index/filestr
                 ::index/start-date
                 ::index/ended-date
                 ::isogsm/dirstr]
   ::pco/output [{::figures [{::modern   [{:nakhon-gauge [::ghcnd/raingauge-filestr]}
                                          ::plot/width
                                          ::plot/height
                                          ::plot/scale
                                          ::plot/margin-frac
                                          ::plot/cycle-start-value
                                          ::plot/cycle-length
                                          ::plot/cycle-phase
                                          ::airport/filestr
                                          ::airport/crazy-dates
                                          ::index/filestr
                                          ::index/start-date
                                          ::index/end-date
                                          ::isogsm/dirstr]
                              ::historic [:nakhon-gauge
                                          ::plot/width
                                          ::plot/height
                                          ::plot/scale
                                          ::plot/margin-frac
                                          ::plot/cycle-start-value
                                          ::plot/cycle-length
                                          ::plot/cycle-phase
                                          ::airport/filestr
                                          ::airport/crazy-dates
                                          ::index/filestr
                                          ::index/start-date
                                          ::index/end-date
                                          ::isogsm/dirstr]}]}]}
  (println (str "Airport Dates - Start: "
                (::airport/start-date inputs)
                " End: "
                (::airport/ended-date  inputs)))
  (let [modern-zero-day (-> inputs
                            ::airport/start-date)
        modern-stop-day (-> inputs
                            ::airport/ended-date)]
  {::figures (medley/deep-merge {::modern (medley/deep-merge inputs
                                                             {::day-zero modern-zero-day})
                                ::historical (medley/deep-merge inputs)})}))

#_
(-> @(p.a.eql/process env
                      @*state
                      [::figures]))
#_
(pathmore/check ::figures)

(def plan-cache*
  (atom {}))


;;(pathmore/dedupe-resolvers

(def env
  (-> (pci/register {::p.a.eql/parallel? true}
                    (pathmore/dedupe-resolvers [$repacked                   
                                                kxygk.mathom.core/$resolvers$
                                                airport/$resolvers$
                                                enso/$resolvers$
                                                index/$resolvers$
                                                isogsm/$resolvers$
                                                ghcnd/$resolvers$
                                                plot/$resolvers$]))
      (pcp/with-plan-cache plan-cache*)
      pathmore/inject-simple-cache-for-all-resolvers
      pathmore/wrap-all-resolvers-async))

#_
(-> @(p.a.eql/process env
                      @*state
                      [{::figures [{::modern [::isogsm/table]}]}]))


#_
(-> @(p.a.eql/process env
                      @*state
                      [{::figures [{::modern [:nakhon-gauge]}]}]))
;;#:kxygk.anemoi.central{:figures #:kxygk.anemoi.central{:modern {:nakhon-gauge #:kxygk.anemoi.ghcnd{:raingauge-filestr "/home/kxygk/Data/GHCNd/daily-summaries-latest/TH000048552.csv"}}}}


#_
(-> @(p.a.eql/process env
                      @*state
                      [{::figures [{::modern {:nakhon-gauge [::plot/width]}}]}]))

#_
(-> @(p.a.eql/process env
                      @*state
                      [{::figures [{::modern [{:nakhon-gauge [{::ghcnd/data [{:Days-from-start [:data|]}]}]}]}]}]))
#_
(-> @(p.a.eql/process env
                      @*state
                      [{::figures [{::modern [{:nakhon-gauge [{::ghcnd/data [{:Rain-mm [:data|]}]}]}]}]}]))




#_
(-> @(p.a.eql/process env
                      @*state
                      [{::figures [{::modern [{:nakhon-gauge [{::ghcnd/data [{:Days-from-start [:data|]}
                                                                             {:Rain-mm [:data|]}]}]}]}]}]))
#_
(-> @(p.a.eql/process env
                      @*state
                      [{::figures [{::modern [{:nakhon-gauge [{::ghcnd/daily-rain []}]}]}]}]))

(-> @(p.a.eql/process env
                      @*state
                      [{::figures [{::modern [{:nakhon-gauge [::ghcnd/daily-rain]}]}]}]))

#_
(-> @(p.a.eql/process env
                      @*state
                      [{::figures [{::modern [:kxygk.anemoi.ghcnd/raingauge-filestr]}]}]))
#_
(-> @(p.a.eql/process env
                      @*state
                      [{::figures [{::modern [{::airport/data []}]}]}]))
#_
(-> @(p.a.eql/process env
                      @*state
                      [{::figures [{::modern [{::plot/d18O-rain [:x]}]}]}]))
#_
(-> @(p.a.eql/process env
                      @*state
                      [{::figures [{::modern [{::plot/day-isogsm-rain-d18O [:meta]}]}]}]))
#_
(-> @(p.a.eql/process env
                      @*state
                      [{::figures [{::modern [{::plot/d18O-below-select-layer [::plot/svg]}]}]}]))

;;(pathmore/check ::day-isogsm-rain-d18O)


;;#_
(defn gen-plots []
  (let [figs (->> [{::figures [{::modern     [{::plot/meteoric-water-line-subplot [::plot/svg]}
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
                                               {::plot/isogsm-rain-d18O-subplot [::plot/svg]}
                                               {::plot/nakhon-d18O-classified-average-subplot [::plot/svg]}
                                              #_
                                              {::plot/d18O-axis [::plot/svg]}
                                              #_
                                              {::plot/isotope-d18O-classified-average-subplot [::plot/svg]}]
                                ::historical [{::plot/meteoric-water-line-subplot [::plot/svg]}
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
                                              {::plot/isogsm-rain-d18O-subplot [::plot/svg]}]}]}]
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

#_
(time (gen-plots))


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
(->> [{::rain-monsoon-figure [{::plot/rain-subplot [::plot/svg]}]}]
     (p.a.eql/process env
                      @*state)
     deref
     ::rain-monsoon-figure
     ::plot/rain-subplot
     ::plot/svg
     (spit "test-only.svg"))



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
