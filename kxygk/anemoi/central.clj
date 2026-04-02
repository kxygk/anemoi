(ns kxygk.anemoi.central
  (:require [kxygk.anemoi.airport :as airport]
            [kxygk.anemoi.plot :as plot]
            [kxygk.anemoi.stat :as stat]
            [kxygk.anemoi.tmd :as tmd]
            kxygk.pathmore.cache
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


(pco/defresolver $days-vs-d18O
  [{::airport/keys [full-table]}]
  {::pco/input  [::airport/full-table]
   ::pco/output [{::days-vs-d18O [:x
                                  :y]}]}
  {::days-vs-d18O {:kxygk.dripsplit.tmd/x-key :Day
                   :kxygk.dripsplit.tmd/y-key :d18O
                   :kxygk.dripsplit.tmd/table full-table}})

(pco/defresolver $days-vs-rain
  [{::airport/keys [full-table]}]
  {::pco/input  [::airport/full-table]
   ::pco/output [::days-vs-rain]}
  {::days-vs-rain {:kxygk.dripsplit.tmd/x-key :Day
                   :kxygk.dripsplit.tmd/y-key :Rain-mm
                   :kxygk.dripsplit.tmd/table full-table}})
#_
(-> @(p.a.eql/process env
                      @*state
                       [{::days-vs-d18O [:xy-nonil]}]))
#_
(-> @(p.a.eql/process env
                      @*state
                      [{::days-vs-d18O [:y]}
                       {::days-vs-rain [:y]}]))
#_
(-> @(p.a.eql/process env
                      @*state
                      [{::days-vs-rain [:x
                                        :y]}]))

(pco/defresolver $days-vs-index-above
  [{::airport/keys [full-table]}]
  {::pco/input  [::airport/full-table]
   ::pco/output [::days-vs-index-above]}
  {::days-vs-index-above {:kxygk.dripsplit.tmd/x-key :Day
                          :kxygk.dripsplit.tmd/y-key :Above-Index
                          :kxygk.dripsplit.tmd/table full-table}})
#_
(-> @(p.a.eql/process env
                      @*state
                      [{::days-vs-index-above [:x :y]}])
    ::days-vs-index-above
    :y)

(pco/defresolver $days-vs-index-below
  [{::airport/keys [full-table]}]
  {::pco/input  [::airport/full-table]
   ::pco/output [::days-vs-index-below]}
  {::days-vs-index-below {:kxygk.dripsplit.tmd/x-key :Day
                          :kxygk.dripsplit.tmd/y-key :Below-Index
                          :kxygk.dripsplit.tmd/table full-table}})
#_
(-> @(p.a.eql/process env
                      @*state
                       [{::days-vs-index-below [:x :y]}]))


(pco/defresolver $days-vs-above?
  [{::airport/keys [full-table]}]
  {::pco/input  [::airport/full-table]
   ::pco/output [{::days-vs-above? [:x
                                    :y]}]}
  {::days-vs-above? {:kxygk.dripsplit.tmd/x-key :Day
                     :kxygk.dripsplit.tmd/y-key :Above?
                     :kxygk.dripsplit.tmd/table full-table}})
#_
(-> @(p.a.eql/process env
                      @*state
                       [{::days-vs-above? [:y]}]))


(pco/defresolver $test-above
  [{::airport/keys [full-table]}]
  {::pco/input  [::airport/full-table]
   ::pco/output [::test-above]}
  {::test-above {::tmd/table-to-filter full-table
                 ::tmd/col-filter-fn :Above?}})
 
#_
(->> [::test-above]
     (p.a.eql/process env
                      @*state)
     deref
     ::test-above
     keys)
#_
(->> [{::test-above [::tmd/table]}]
     (p.a.eql/process env
                      @*state)
     deref
     ::test-above)

(pco/defresolver $single-figures
  [inputs]
  {::pco/input  [::airport/full-table
                 :above-index-threshold
                 :below-index-threshold
                 ::days-vs-rain
                 ::days-vs-d18O
                 ::days-vs-above?
                 ::days-vs-index-above
                 ::days-vs-index-below
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
(->> [{::timeseries-figure [{::plot/d18O-rain-above [::tmd/table]}]}]
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


;; print data tables
#_
(let [{::airport/keys [full-table
                       above-table
                       below-table]} (->> [::airport/full-table
                                           ::airport/above-table
                                           ::airport/below-table]
                                          (p.a.eql/process env
                                                           @*state)
                                          deref) ]
  (tech.v3.dataset/write! full-table
                          "full-table.txt")
  (tech.v3.dataset/write! above-table
                          "above-table.txt")
  (tech.v3.dataset/write! below-table
                          "below-table.txt"))

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



;; Plot requirements
#_
[rain-datavec
 monsoon-winter-datavec
 monsoon-summer-datavec
 missing-days-datavec
 d18O-datavec
 stat-rain-weighted-d18O
 stat-rain-weighted-d18O-above
 stat-rain-weighted-d18O-below
 stat-index-weighted-d18O-above
 stat-index-weighted-d18O-below
 cycle-start-value
 cycle-length
 cycle-phase
 data-span-days
 climate-index-max]

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
                     $days-vs-d18O
                     $days-vs-rain
                     $days-vs-index-above
                     $days-vs-index-below
                     $days-vs-above?
                     $test-above ;;remove
                     $single-figures
                     $rain-monsoon-figure
                     #_
                     $days-vs-rain-figure])
      (pcp/with-plan-cache plan-cache*)
      kxygk.pathmore.cache/inject-for-all-resolvers))






;; OLD STUFF FOR REFERENCE


#_
(pco/defresolver user-by-id
  [{::keys [id]}]        ; INPUTS
  {::pco/input  [::id]
   ::pco/output [::name  ; OUTPUTS
                 ::email
                 ::birthday]}
  ;; we'll run this on a thread in the background
  (p/vthread (do (println (str "Going in to the DB and getting user: "
                               id))
                 (Thread/sleep 2345)
                 (get user-db
                      id))))

;; Resolvers can be executed like a function.
;; However,
;; this is effectively only useful during testing
;; (normal, non-threaded resolvers can be chained in pipelines as well)
#_@(user-by-id {::id 1})
;; {:name "Alice", :email "alice@example.com", :birthday "1989-10-25"}

#_
(pco/defresolver birth-year
  [{::keys [birthday]}]
  {::pco/input  [::birthday]
   ;; ::pco/cache-store ::my-cache   ; you can also designate a cache (memoization)
   ::pco/output [::birth-year]}
  (p/vthread (do (println (str "Extracting a Birth Year from the BDay: "
                               birthday))
                 (Thread/sleep 3141)
                 {::birth-year (-> birthday
                                   (clojure.string/split #"-")
                                   first)})))
#_
@(birth-year {::birthday "2012-12-12"})

#_#_
(defonce plan-cache*
  (atom {}))

(def env
  (-> (pci/register {::p.a.eql/parallel? true}
                    [user-by-id
                     birth-year])
      (pcp/with-plan-cache plan-cache*)
      kxygk.pathmore.cache/inject-for-all-resolvers))
#_
@(p.a.eql/process env
                 {::id 2} ;; input map
                 [::birth-year])
#_
@(p.a.eql/process env
                 {::id 2} ;; input map
                 [::id])
