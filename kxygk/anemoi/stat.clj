(ns kxygk.anemoi.stat
  (:require [clojure.math]
            [clojure.string]
            kxygk.pathmore.cache
            [kxygk.pathmore.core :as pathmore]
            [com.wsscode.pathom3.connect.planner :as pcp]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
            [tick.core                    :as tick]
            [tick.locale-en-us]
            [promesa.core :as p]))

(pathmore/clean-ns!)

(pco/defresolver $data-vec-identity
  [input]
  {::pco/input  [:data-vec]
   ::pco/output [:data-vec]}
  input)

(pco/defresolver $filter-nils
  [{:keys [data-vec]}]
  {::pco/input [:data-vec]
   ::pco/output [:data-vec-nonil]}
  (p/vthread {:data-vec-nonil (->> data-vec
                                   (filterv some?))}))

(pco/defresolver $minmax
  [{:keys [data-vec-nonil]}]
  {::pco/input [:data-vec-nonil]
   ::pco/output [::min
                 ::max]}
  (p/vthread {::min (apply min
                           data-vec-nonil)
              ::max (apply max
                           data-vec-nonil)}))

(pco/defresolver $standard
  [{:keys [data-vec-nonil]}]
  {::pco/input  [:data-vec-nonil]
   ::pco/output [::standard-mean
                 ::standard-std
                 ::standard-sdom]}
  (p/vthread (let [num (count data-vec-nonil)]
               (let [average (/ (apply +
                                       data-vec-nonil)
                                num)]
                 (let [std (clojure.math/sqrt (/ (->> data-vec-nonil
                                                      (mapv #(clojure.math/pow (- %
                                                                                  average)
                                                                               2.0))
                                                      (reduce +))
                                                 (dec num)))]
                   {::standard-mean average
                    ::standard-std  std
                    ::standard-sdom (/ std
                                       (clojure.math/sqrt num))})))))

(pco/defresolver $weighted
  "This was taken from here:
  https://en.wikipedia.org/wiki/Weighted_arithmetic_mean
  As I'm a bit unclear on how to derive these values.
  Here `x` values are weighted by `y` values
  Note: if either `x` or `y` is `nil`, this point is treated as if it doesn't exist!"
  [{:keys [x
           y]}]
  {::pco/input  [{:x [:data-vec]}
                 {:y [:data-vec]}]
   ::pco/output [::weighted-mean
                 ::weighted-std
                 ::weighted-sdom]}
  (p/vthread (let [nonils (filterv some?
                                   (mapv (fn [x-coord
                                              y-coord]
                                           (if (and x-coord
                                                    y-coord)
                                             [x-coord
                                              y-coord]
                                             nil))
                                         (:data-vec x)
                                         (:data-vec y)))
                   x-vec (mapv first
                               nonils)
                   y-vec (mapv second
                               nonils)]
               (let [num                    (count x-vec) ;; same as `weight-vec`
                     sum-of-weights         (reduce +
                                                    y-vec)
                     sum-of-squared-weights (->> y-vec
                                                 (mapv #(clojure.math/pow %
                                                                          2.0))
                                                 (reduce +))
                     weighted-sum           (->> (mapv *
                                                       x-vec
                                                       y-vec)
                                                 (reduce +))]
                 (let [mean (/ weighted-sum
                               sum-of-weights)]
                   (let [sum-of-residuals-squared (->> (mapv -
                                                             x-vec
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
                       {::weighted-mean mean
                        ::weighted-std  (clojure.math/sqrt variance)
                        ::weighted-sdom (* (clojure.math/sqrt variance)
                                           (->> y-vec
                                                (mapv #(/ %
                                                          sum-of-weights))
                                                (mapv #(clojure.math/pow %
                                                                         2.0))
                                                (reduce +)
                                                clojure.math/sqrt))})))))))

(defn-
  bin-to-range
  [minimum
   bin-size
   data-vec
   weights]
  (update-keys (update-vals (->> data-vec
                                 (mapv (fn [value]
                                         (- value
                                            minimum)))
                                 (mapv (fn [shifted-value]
                                         (-> shifted-value
                                             (/ bin-size)
                                             clojure.math/floor ;; this bins it
                                             int)))
                                 (mapv (fn [weight
                                            bin-index]
                                         [bin-index
                                          weight])
                                       weights)
                                 (group-by first))
                            (fn [points-in-bin-vec]
                              (->> points-in-bin-vec
                                   (mapv second)
                                   (apply +))))
               (fn [bin-index]
                 (+ (* bin-index
                       bin-size)
                    (/ bin-size
                       2.0)
                    minimum))))
#_
(vec (bin-to-range 0
                   3
                   [1.1 2.2 3.4 5.5 6.7 8.8 9.9 4.4 3.4 5.6]
                   [1.0 2.0 3.0 1.0 1.0 1.0 1.0 1.0 1.0 1.0]))

(pco/defresolver $hist-standard
  [{:keys [data-vec-nonil]}]
  {::pco/input  [:data-vec-nonil]
   ::pco/output [{::hist [:xy-nonil
                          {:x [:data-vec]}
                          {:y [:data-vec]}]}]}
  (let [xy-nonil (into []
                       (bin-to-range -20
                                     0.5
                                     data-vec-nonil
                                     (repeat 1.0)))]
    {::hist {:xy-nonil xy-nonil
             :x {:data-vec (->> xy-nonil
                                (mapv first))}
             :y {:data-vec (->> xy-nonil
                                (mapv second))} }}))
#_
($hist-standard {:data-vec-nonil [1.1 2.2 3.4 5.5 6.7 8.8 9.9 4.4 3.4 5.6]})
                #_
                   [1.0 2.0 3.0 1.0 1.0 1.0 1.0 1.0 1.0 1.0]


(pco/defresolver $hist-weighted
  [{:keys [x
           y]}]
  {::pco/input  [{:x [:data-vec-nonil]}
                 {:y [:data-vec-nonil]}]
   ::pco/output [{::hist [:xy-nonil
                          {:x [:data-vec]}
                          {:y [:data-vec]}]}]}
  (let [xy-nonil (into []
                       (bin-to-range -20
                                     0.5
                                     (-> x
                                         :data-vec-nonil)
                                     (-> y
                                         :data-vec-nonil)))]
    {::hist {:xy-nonil xy-nonil
             :x {:data-vec (->> xy-nonil
                                (mapv first))}
             :y {:data-vec (->> xy-nonil
                                (mapv second))} }}))

  #_
  (p/vthread (let [num (count data-vec-nonil)]
               (let [average (/ (apply +
                                       data-vec-nonil)
                                num)]
                 (let [std (clojure.math/sqrt (/ (->> data-vec-nonil
                                                      (mapv #(clojure.math/pow (- %
                                                                                  average)
                                                                               2.0))
                                                      (reduce +))
                                                 (dec num)))]
                   {::standard-mean average
                    ::standard-std  std
                    ::standard-sdom (/ std
                                       (clojure.math/sqrt num))}))))
(def $year-fraction
  (pbir/single-attr-resolver :Date
                             :cycle-fraction
                             (fn [date]
                               (let [start-of-given-year (-> date
                                                             tick/first-day-of-year)
                                     ended-of-given-year (-> date
                                                             tick/first-day-of-year)
                                     days-in-year        (tick/between start-of-given-year
                                                                       ended-of-given-year
                                                                       :days)
                                     day-num-of-date     (tick/between start-of-given-year
                                                                       date
                                                                       :days)]
                                 (/ day-num-of-date
                                    days-in-year)))))

(def $resolvers$
  (->> (pathmore/find-resolvers)
       (mapv pathmore/inject-simple-cache)))

(def $env$
  (pci/register {::p.a.eql/parallel? true}
                $resolvers$))

#_
(def env
  (-> (pci/register {::p.a.eql/parallel? true}
                    [$data-vec-identity
                     $filter-nils
                     $minmax
                     $standard
                     $weighted
                     $hist-standard
                     $hist-weighted
                     $year-fraction])
      (pcp/with-plan-cache plan-cache*)
      kxygk.pathmore.cache/inject-for-all-resolvers))

#_
(-> @(p.a.eql/process env
                      {:x {:data-vec [1.0
                                        2.0
                                        3.0
                                        4.0]}
                       :y {:data-vec [4.0
                                      3.0
                                      2.0
                                      1.0]}}
                      [{:x [::standard-mean ::min ::max]}
                       ::weighted-sdom]))
;; #:kxygk.dripsplit.stat{:x #:kxygk.dripsplit.stat{:standard-mean 2.5, :min 1.0, :max 4.0}, :weighted-sdom 0.8485281374238571}
