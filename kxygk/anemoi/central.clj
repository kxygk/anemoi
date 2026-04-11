(ns kxygk.anemoi.central
  (:require [kxygk.anemoi.airport :as airport]
            [kxygk.anemoi.plot :as plot]
            [kxygk.anemoi.stat :as stat]
            [kxygk.anemoi.tmd :as tmd]
            kxygk.pathmore.cache
            ;;
            [criterium.core :refer [bench]]
            [clj-async-profiler.core :as prof]
            ;;
            [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
            [com.wsscode.pathom3.connect.planner :as pcp]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [promesa.core :as p]))


(def *state
  (atom {::enso-filestr          (str "/home/kxygk/Data/enso/"
                                      "nina34.anom.csv")
         ::isotopes-filestr      (str "/home/kxygk/Data/airport/"
                                      "first-sheet-extracted.csv")
         ::climate-index-filestr (str "/home/kxygk/Projects/imergination.wiki/krabdaily/"
                                      "climate-index.csv")
         ::crazy-dates           #{#time/date "2017-07-30"}
         ::start-date            #inst"2011-01-01"
         ::end-date              #inst"2021-01-01"
         ::cycle-start-value     2011
         ::cycle-length          365
         ::cycle-phase           0
         :above-index-threshold  99.9    ;; default to no threshold
         :below-index-threshold  99.9}))

#_
(-> @(p.a.eql/process env
                      @*state
                      [::plot/rain-data]))

(pco/defresolver $single-figures
  [inputs]
  {::pco/input  [::airport/full-table
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
                       ::plot/width              1800
                       ::plot/height             1300 ;;650
                       ::plot/scale              100
                       ::plot/margin-frac        0.1
                       ;;
                       ::plot/d18O-rain          {::tmd/x-key           :d18O
                                                  ::tmd/y-key           :Rain-mm
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   both-filt}
                       ::plot/rain-data          {::tmd/x-key           :Day
                                                  ::tmd/y-key           :Rain-mm
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   both-filt}
                       ::plot/d18O-data          {::tmd/x-key           :Day
                                                  ::tmd/y-key           :d18O
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   both-filt}
                       ::plot/d18O-above-data    {::tmd/x-key           :Day
                                                  ::tmd/y-key           :d18O
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   above-filt}
                       ::plot/d18O-below-data    {::tmd/x-key           :Day
                                                  ::tmd/y-key           :d18O
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   below-filt}
                       ::plot/d18O-rain-above    {::tmd/x-key           :d18O
                                                  ::tmd/y-key           :Rain-mm
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   above-filt}
                       ::plot/d18O-rain-below    {::tmd/x-key           :d18O
                                                  ::tmd/y-key           :Rain-mm
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   below-filt}
                       ;;
                       ;; d18O vs Monsoon
                       ::plot/d18O-monsoon-above {::tmd/x-key           :d18O
                                                  ::tmd/y-key           :Above-Index
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   above-filt}
                       ::plot/d18O-monsoon-below {::tmd/x-key           :d18O
                                                  ::tmd/y-key           :Below-Index
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   below-filt}
                       ::plot/d18O-range-min     -25
                       ::plot/d18O-range-max     10
                       ::plot/above?-data        {::tmd/x-key :d18O
                                                  ::tmd/y-key :Above?
                                                  ::tmd/table (::airport/full-table inputs)}
                       ::plot/index-above        {::tmd/x-key           :Day
                                                  ::tmd/y-key           :Above-Index
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   above-filt}
                       ::plot/index-below        {::tmd/x-key           :Day
                                                  ::tmd/y-key           :Below-Index
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   below-filt}
                       ::plot/cycle-start-value  (::cycle-start-value inputs)
                       ::plot/cycle-length       (::cycle-length inputs)
                       ::plot/cycle-phase        (::cycle-phase inputs)}}))
#_
(let [figs (->> [{::single-figures [{::plot/rain-subplot [::plot/svg]}
                                        {::plot/index-subplot [::plot/svg]}
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
                                     @*state)
                    deref
                    ::single-figures)]
      (->> figs
           (mapv (fn [[key
                       value]]
                   (->> value
                        ::plot/svg
                        (spit (str "./out/all-"
                                   (name key)
                                   ".svg")))))))
#_
(let [figs (->> [{::single-figures [{::plot/rain-subplot [::plot/svg]}
                                    {::plot/index-subplot [::plot/svg]}
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
                                        {:above-index-threshold 0.99
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
     deref
     ::timeseries-figure)

(pco/defresolver $single-gmwl-figures
  [inputs]
  {::pco/input  [::airport/full-table
                 :above-index-threshold
                 :below-index-threshold
                 ::cycle-start-value
                 ::cycle-length
                 ::cycle-phase]
   ::pco/output [::single-gmwl-figures]}
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
    {::single-gmwl-figures {
                       ::plot/width              1800
                       ::plot/height             1300 ;;650
                       ::plot/scale              100
                       ::plot/margin-frac        0.1
                       ;;
                       ::plot/d18O-rain          {::tmd/x-key           :GMWL-d18O
                                                  ::tmd/y-key           :Rain-mm
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   both-filt}
                       ::plot/rain-data          {::tmd/x-key           :Day
                                                  ::tmd/y-key           :Rain-mm
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   both-filt}
                       ::plot/d18O-data          {::tmd/x-key           :Day
                                                  ::tmd/y-key           :GMWL-d18O
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   both-filt}
                       ::plot/d18O-above-data    {::tmd/x-key           :Day
                                                  ::tmd/y-key           :GMWL-d18O
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   above-filt}
                       ::plot/d18O-below-data    {::tmd/x-key           :Day
                                                  ::tmd/y-key           :GMWL-d18O
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   below-filt}
                       ::plot/d18O-rain-above    {::tmd/x-key           :GMWL-d18O
                                                  ::tmd/y-key           :Rain-mm
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   above-filt}
                       ::plot/d18O-rain-below    {::tmd/x-key           :GMWL-d18O
                                                  ::tmd/y-key           :Rain-mm
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   below-filt}
                       ;;
                       ;; d18O vs Monsoon
                       ::plot/d18O-monsoon-above {::tmd/x-key           :GMWL-d18O
                                                  ::tmd/y-key           :Above-Index
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   above-filt}
                       ::plot/d18O-monsoon-below {::tmd/x-key           :GMWL-d18O
                                                  ::tmd/y-key           :Below-Index
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   below-filt}
                       ::plot/d18O-range-min     -25
                       ::plot/d18O-range-max     10
                       ::plot/above?-data        {::tmd/x-key :GMWL-d18O
                                                  ::tmd/y-key :Above?
                                                  ::tmd/table (::airport/full-table inputs)}
                       ::plot/index-above        {::tmd/x-key           :Day
                                                  ::tmd/y-key           :Above-Index
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   above-filt}
                       ::plot/index-below        {::tmd/x-key           :Day
                                                  ::tmd/y-key           :Below-Index
                                                  ::tmd/table-to-filter (::airport/full-table inputs)
                                                  ::tmd/col-filter-fn   below-filt}
                       ::plot/cycle-start-value  (::cycle-start-value inputs)
                       ::plot/cycle-length       (::cycle-length inputs)
                       ::plot/cycle-phase        (::cycle-phase inputs)}}))
#_
(let [figs (->> [{::single-gmwl-figures [{::plot/rain-subplot [::plot/svg]}
                                        {::plot/index-subplot [::plot/svg]}
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
                                     @*state)
                    deref
                    ::single-gmwl-figures)]
      (->> figs
           (mapv (fn [[key
                       value]]
                   (->> value
                        ::plot/svg
                        (spit (str "./out/gmwl-"
                                   (name key)
                                   ".svg")))))))


(pco/defresolver $rain-monsoon-figure
  [inputs]
  {::pco/input  [::airport/full-table
                 ::airport/above-table
                 ::airport/below-table
                 ::days-vs-rain
                 ::days-vs-d18O
                 ::days-vs-above?
                 ::days-vs-index-above
                 ::days-vs-index-below
                 ::cycle-start-value
                 ::cycle-length
                 ::cycle-phase]
   ::pco/output [::rain-monsoon-figure]}
  {::rain-monsoon-figure {::plot/width        1800
                          ::plot/height       650
                          ::plot/scale        75
                          ::plot/margin-frac  0.1
                          ;;
                          ::plot/d18O-rain {::tmd/x-key :d18O
                                            ::tmd/y-key :Rain-mm
                                            ::tmd/table (::airport/full-table inputs)}
                          ::plot/rain-data (::days-vs-rain inputs)
                          ::plot/d18O-data (::days-vs-d18O inputs)
                          ::plot/d18O-above-data {::tmd/x-key :Day
                                                  ::tmd/y-key :d18O
                                                  ::tmd/table (::airport/above-table inputs)}
                          ::plot/d18O-below-data {::tmd/x-key :Day
                                                  ::tmd/y-key :d18O
                                                  ::tmd/table (::airport/below-table inputs)}
                          ::plot/d18O-rain-above {::tmd/x-key :d18O
                                                  ::tmd/y-key :Rain-mm
                                                  ::tmd/table (::airport/above-table inputs)}
                          ::plot/d18O-rain-below {::tmd/x-key :d18O
                                                  ::tmd/y-key :Rain-mm
                                                  ::tmd/table (::airport/below-table inputs)}
                          ::plot/above?-data (::days-vs-above? inputs)
                          ::plot/index-above (::days-vs-index-above inputs)
                          ::plot/index-below (::days-vs-index-below inputs)
                          ::plot/cycle-start-value (::cycle-start-value inputs)
                          ::plot/cycle-length (::cycle-length inputs)
                          ::plot/cycle-phase  (::cycle-phase inputs)}})
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



(def plan-cache*
  (atom {}))

(def env
  (-> (pci/register {::p.a.eql/parallel? true}
                    [airport/env
                     stat/env
                     tmd/env
                     (pbir/equivalence-resolver ::airport/enso-filestr
                                                ::enso-filestr)
                     (pbir/equivalence-resolver ::airport/isotopes-filestr
                                                ::isotopes-filestr)
                     (pbir/equivalence-resolver ::airport/climate-index-filestr
                                                ::climate-index-filestr)
                     (pbir/equivalence-resolver ::airport/crazy-dates
                                                ::crazy-dates)
                     (pbir/equivalence-resolver ::airport/start-date
                                                ::start-date)
                     (pbir/equivalence-resolver ::airport/end-date
                                                ::end-date)
                     plot/env
                     $single-figures
                     $rain-monsoon-figure])
      (pcp/with-plan-cache plan-cache*)
      kxygk.pathmore.cache/inject-for-all-resolvers))

;; PRINT TABLES TO FILES
(let [{::airport/keys [full-table]} (->> [::airport/full-table]
                                          (p.a.eql/process env
                                                           @*state)
                                          deref) ]
  (tech.v3.dataset/write! full-table
                          "full-table.txt"))



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
(prof/profile {:event :wall} ;; this flag makes the flamegraph capture wall time and shows waiting for threads
  (bench (with-out-str (let [figs (->> [{::single-figures [{::plot/rain-subplot [::plot/svg]}
                                                           {::plot/index-subplot [::plot/svg]}
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
                                                        @*state)
                                       deref
                                       ::single-figures)]
                         (->> figs
                              (mapv (fn [[key
                                          value]]
                                      (->> value
                                           ::plot/svg))))))))


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
