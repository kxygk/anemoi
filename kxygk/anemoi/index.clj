(ns kxygk.anemoi.index
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
  {::filestr (str "/home/kxygk/Projects/imergination.wiki/krabdaily/"
                  "climate-index.csv")})

(pco/defresolver $read-table
  [{::keys [filestr
            ]}]
  {::pco/output [::raw-table]}
  {::raw-table (-> filestr
                   (ds/->dataset {:dataset-name "Regional Climate Index"
                                  :key-fn       kxygk.anemoi.util/normalize-colname})
                   (ds/rename-columns [:Above-Index
                                       :Below-Index]))})
#_
(pathmore/check ::raw-table)

(pco/defresolver $-all-dates-vec
  [{:keys [start-date
           end-date]}]
  {::pco/output [::-all-dates-vec]}
  {::-all-dates-vec (->> (tick/range
                           (tick/at start-date (tick/midnight))
                           (tick/at end-date (tick/midnight)) ;; doesn't include last value
                           (tick/new-duration 24 :hours)))})
#_
(pathmore/check ::-all-dates-vec
                {:start-date #time/date"2011-01-01"
                 :end-date   #time/date"2031-01-01"})

(pco/defresolver $add-dates
  [{::keys [raw-table
            -all-dates-vec]}]
  {::pco/output [::table]}
  {::table (assoc raw-table
                  :Date
                  (->> -all-dates-vec
                      ;; depends on if index has them or not! Should add back TODO!!!!!!
                      ;; Ideally this should be diabled
                      ;;#_
                       tock/remove-leapdays
                       (take (ds/row-count raw-table))))})
#_
(pathmore/check ::table
                {:start-date #time/date"2011-01-01"
                 :end-date   #time/date"2031-01-01"})

(pco/defresolver $extract-table-columns
  [{::keys [table]
   :keys [start-date]}]
  {::pco/output [{::data [:start-date
                          :Date
                          :Above-Index
                          :Below-Index]}]}
  ;;Should just be
  #_
  (into {}
        table)
  ;; but there is a bug: https://github.com/techascent/tech.ml.dataset/issues/479
  ;; Use this for now
  {::data (merge {:start-date start-date}
                (update-vals (into {}
                                   table)
                             vec))})
#_
(pathmore/check [{::data [:Above-Index]}]
                {:start-date #time/date"2011-01-01"
                 :end-date   #time/date"2031-01-01"})

(pco/defresolver $is-above?
  [{:keys [Above-Index]}]
  {:Above? (->> Above-Index
                (mapv #(-> %
                           zero?
                           not)))})
#_
(pathmore/check [{::data [:Above?]}]
                {:start-date #time/date"2011-01-01"
                 :end-date   #time/date"2031-01-01"})
  

(def $resolvers$
  (->> (pathmore/find-resolvers)
       (mapv pathmore/inject-simple-cache)))

(def $env$
  (pci/register {::p.a.eql/parallel? true}
                $resolvers$))
