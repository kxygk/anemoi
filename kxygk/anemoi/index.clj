(ns kxygk.anemoi.index
  (:require [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
            [tick.core                    :as tick]
            [tech.v3.dataset              :as ds]
            [tock]
            [kxygk.anemoi.util]))

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
(->> @(p.a.eql/process env
                       {::filestr (str "/home/kxygk/Projects/imergination.wiki/krabdaily/"
                                       "climate-index.csv")} 
                       [::raw-table])
     ::raw-table)

(pco/defresolver $-all-dates-vec
  [{:keys [start-date
           end-date]}]
  {::pco/output [::-all-dates-vec]}
  {::-all-dates-vec (->> (tick/range
                           (tick/at start-date (tick/midnight))
                           (tick/at end-date (tick/midnight)) ;; doesn't include last value
                           (tick/new-duration 24 :hours)))})
#_
(->> @(p.a.eql/process env
                       {:start-date #time/date"2011-01-01"
                        :end-date   #time/date"2031-01-01"}
                       [::-all-dates-vec])
     ::-all-dates-vec
     (take 5))
#_
(-> ($-all-dates-vec {::start-date #time/date"2011-01-01"
                     ::end-date   #time/date"2021-01-01"})
    deref
    ::all-dates-vec
    count)
;; => 3650

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
(->> @(p.a.eql/process env
                       {::filestr    (str "/home/kxygk/Projects/imergination.wiki/krabdaily/"
                                          "climate-index.csv")
                        ::start-date #time/date "2011-01-01"
                        ::end-date   #time/date "2021-01-01"} 
                       [::table])
     ::table)

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
(-> @(p.a.eql/process env
                      {::filestr    (str "/home/kxygk/Projects/imergination.wiki/krabdaily/"
                                         "climate-index.csv")
                       ::start-date #inst"2011-01-01"
                       ::end-date   #inst"2021-01-01"}
                      [:Above-Index]))

(pco/defresolver $is-above?
  [{:keys [Above-Index]}]
  {:Above? (->> Above-Index
                (mapv #(-> %
                           zero?
                           not)))})
#_
(-> @(p.a.eql/process env
                      {::filestr    (str "/home/kxygk/Projects/imergination.wiki/krabdaily/"
                                         "climate-index.csv")
                       ::start-date #inst"2011-01-01"
                       ::end-date   #inst"2021-01-01"}
                      [:Above?]))
  
(def env
  (pci/register {::p.a.eql/parallel? true}
                [$read-table
                 $-all-dates-vec
                 $add-dates
                 $extract-table-columns
                 $is-above?]))


 
(let [pr1 8.416052 ;; 69.62872;;36.26312 
      pr 8.509018 ;;70.23905 ;;36.58453
      ]

  (* 1000
     (- (/ pr1
           pr)
        1)))

#_#_#_
(defn my-function
  [{:keys! [username]
    :keys  [firstname
           lastname]}]
  (do-stuff username
            firstname
            lastname))

(my function {:firstname "John"})

(my function {:firstname "John"
              :username nil})
