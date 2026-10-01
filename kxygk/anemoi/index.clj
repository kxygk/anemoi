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
  [{::keys [start-date
            ended-date]}]
  {::pco/output [::-all-dates-vec]}
  {::-all-dates-vec (->> (tick/range
                           (tick/at start-date (tick/midnight))
                           (tick/at ended-date (tick/midnight)) ;; doesn't include last value
                           (tick/new-duration 24 :hours)))})
#_
(pathmore/check ::-all-dates-vec
                {::start-date #time/date"2011-01-01"
                 ::ended-date   #time/date"2031-01-01"})

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
                       (take (ds/row-count raw-table))))})#_
(pathmore/check ::table
                {::start-date #time/date"2011-01-01"
                 ::ended-date   #time/date"2031-01-01"})

(pco/defresolver $default-day-zero
  [{::keys [start-date]}]
  {::pco/output [::day-zero]}
  {::day-zero start-date})
#_
(pathmore/check ::-all-dates-vec
                {::start-date #time/date"2011-01-01"
                 ::ended-date   #time/date"2031-01-01"})


(pco/defresolver $extract-table-columns
  [{::keys [table
            day-zero]}]
  {::pco/output [{::data [:day-zero
                          {:Date  [:data|]}
                          {:Above-Index  [:data|]}
                          {:Below-Index  [:data|]}]}]}
  ;;Should just be
  #_
  (into {}
        table)
  ;; but there is a bug: https://github.com/techascent/tech.ml.dataset/issues/479
  ;; Use this for now
  {::data (merge {:day-zero day-zero}
                 (update-vals (into {}
                                    table)
                              (fn convert-tmd-cols
                                [tmd-col]
                                {:data| (vec tmd-col)})))})
#_
(pathmore/check [{::data [:Days-from-start]}]
                {::start-date #time/date"2011-01-01"
                 ::ended-date   #time/date"2031-01-01"})

(pco/defresolver $is-above?
  "Maybe move to a generic ns?"
  [{:keys [Above-Index]}]
  {::pco/input  [{:Above-Index [:data|]}]
   ::pco/output [{:Above? [:data|]}]}
  {:Above? {:data| (->> Above-Index
                        :data|
                        (mapv #(-> %
                                   zero?
                                   not)))}})
#_
(pathmore/check [{::data [:Above?]}]
                {::start-date #time/date"2011-01-01"
                 ::ended-date   #time/date"2031-01-01"})


(def $resolvers$
  (->> [(pathmore/find-resolvers)
        kxygk.mathom.core/$resolvers$]
       (mapv pathmore/inject-simple-cache)))

(def $env$
  (pci/register {::p.a.eql/parallel? true}
                $resolvers$))
