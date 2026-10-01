(ns kxygk.anemoi.plot
  (:require [clojure.math]
            [clojure.string]
            [kxygk.anemoi.ghcnd :as ghcnd]
            [kxygk.anemoi.airport :as airport]
            [kxygk.anemoi.index :as index]
            [kxygk.anemoi.isogsm :as isogsm]
            [kxygk.pathmore.core :as pathmore]
            [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.interface.smart-map :as psm]
            [com.wsscode.pathom3.connect.planner :as pcp]
            [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
            [promesa.core :as p]
            [tock]
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
            #_[injest.classical]
            #_[clojure.data.csv]))

(pathmore/clean-ns!)

(def $state$
  "for testing only"
  {::airport/filestr     (str "/home/kxygk/Data/airport/"
                              "first-sheet-extracted.csv")
   ::airport/crazy-dates #{#time/date "2017-07-30"}
   ::index/filestr       (str "/home/kxygk/Projects/imergination.wiki/krabdaily/"
                              "climate-index.csv") ;; gets `joined` in to the airport table
   ;; for testing manual override of dates
   ::index/start-date    #time/date"2011-01-01"
   ::index/ended-date    #time/date"2031-01-01"})


(def summer-color "#aa8800")
(def winter-color "#00aa88")
(def secondary-color "#33ff")


(pco/defresolver $hiccup2svg
  [{::keys [hiccup]}]
  {::pco/output [::svg]}
  {::svg (-> hiccup
             quickthing/svg2xml)})

;; Data Vectorization
;;
;;
#_#_
:airport-data
:start-date
#_
[:Date
 :Rain-mm
 :d18O
 :dD
 :Comment]
;;

(pco/defresolver $aiport-repack-mini
  [{::airport/keys [data]}]
  {::pco/input  [{::airport/data [:Date
                                  :Rain-mm
                                  :d18O
                                  :dD
                                  :Comment]}]
   ::pco/output [{::d18O-rain [{:x [:data|]}
                               {:y [:data|]}]}
                 {::d18O-dD [{:x [:data|]}
                             {:y [:data|]}]}]}
  {::d18O-rain {:x (:d18O data)
                :y (:Rain-mm data)}
   ::d18O-dD   {:x (:d18O data)
                :y (:dD data)}})
#_
(pathmore/check [{::d18O-rain [:xy|]}])

(pco/defresolver $meteoric-water-line-subplot
  "Amount weighted averages"
  [{::keys [width
            height
            scale
            margin-frac
            d18O-dD]}]
  {::pco/input  [::width
                 ::height
                 ::scale
                 ::margin-frac
                 {::d18O-dD [:xy-nonil|]}]
   ::pco/output [{::meteoric-water-line-subplot [::hiccup]}]}
  {::meteoric-water-line-subplot {::hiccup (-> (quickthing/primary-axis [[-22.5
                                                                          -150]
                                                                         [5
                                                                          50]] #_d18O-dD
                                                                        {:width       width
                                                                         :height      height
                                                                         :title       "Meteoric Water Line"
                                                                         #_#_
                                                                         :legend      [["" #_"ALL RAINY DAYS"
                                                                                        {:fill   "lightgrey"
                                                                                         :stroke "lightgrey"}]
                                                                                       ["SUMMER MONSOON"
                                                                                        {:fill   summer-color
                                                                                         :stroke nil}]
                                                                                       ["WINTER MONSOON"
                                                                                        {:fill   winter-color
                                                                                         :stroke nil}]]
                                                                         :x-name      "d18O"
                                                                         :y-name      "dD"
                                                                         :scale       scale
                                                                         :margin-frac margin-frac
                                                                         #_#_
                                                                         :color       "#0008"})
                                               (update :data
                                                       #(into %
                                                              (quickthing/circles (:xy-nonil| d18O-dD)
                                                                                  {:scale   (/ scale
                                                                                               10)
                                                                                   :attribs {:fill "blue"}})))
                                               viz/svg-plot2d-cartesian
                                               (quickthing/svg-wrap [width
                                                                     height]
                                                                    width))}})

(pco/defresolver $amount-effect-subplot
  "Amount weighted averages"
  [{::keys [width
            height
            scale
            margin-frac
            d18O-rain
            #_
            rain-d18O-line]}]
  {::pco/input  [::width
                 ::height
                 ::scale
                 ::margin-frac
                 {::d18O-rain [:xy-nonil|]}]
   ::pco/output [{::amount-effect-subplot [::hiccup]}]}
  {::amount-effect-subplot {::hiccup (-> (quickthing/primary-axis (:xy-nonil| d18O-rain)
                                                                  {:width       width
                                                                   :height      height
                                                                   :title       "Amount Effect"
                                                                   #_#_
                                                                   :legend      [["" #_"ALL RAINY DAYS"
                                                                                  {:fill   "lightgrey"
                                                                                   :stroke "lightgrey"}]
                                                                                 ["SUMMER MONSOON"
                                                                                  {:fill   summer-color
                                                                                   :stroke nil}]
                                                                                 ["WINTER MONSOON"
                                                                                  {:fill   winter-color
                                                                                   :stroke nil}]]
                                                                   :x-name      "Rain per day (mm)"
                                                                   :y-name      "d18O"
                                                                   :scale       scale
                                                                   :margin-frac margin-frac
                                                                   #_#_
                                                                   :color       "#0008"})
                                         (assoc :grid
                                                {:major-y true
                                                 :major-x false})
                                         (assoc-in [:y-axis
                                                    :minor]
                                                   (range -20
                                                          10
                                                          1))
                                         (assoc-in [:y-axis
                                                    :major]
                                                   [10,0,-4,-6,-10,-20]
                                                   #_
                                                   (range 0
                                                          -11
                                                          -2))
                                         (assoc-in [:y-axis
                                                    :label-dist]
                                                   (/ scale
                                                      2.0))
                                         (assoc-in [:y-axis
                                                    :label-y]
                                                   (/ scale
                                                      6.0))
                                         (assoc-in [:y-axis
                                                    :label-style]
                                                   {:fill        "black"
                                                    :stroke      "none"
                                                    :font-family "Arial, sans-serif"
                                                    :font-size   (/ scale
                                                                    2.0)
                                                    :text-anchor "end"})
                                         (update :data
                                                 #(into %
                                                        (quickthing/circles (:xy-nonil| d18O-rain)
                                                                            {:scale   (/ scale
                                                                                         10)
                                                                             :attribs {:fill "blue"}})))
                                         #_
                                         (update :data
                                                 #(into %
                                                        (quickthing/solid-line rain-d18O-line
                                                                               {:scale   (/ scale
                                                                                            3)
                                                                                :attribs {:stroke       "red"
                                                                                          :stroke-width (/ scale
                                                                                                           10)}})))
                                         ;;#_#_
                                         viz/svg-plot2d-cartesian
                                         (quickthing/svg-wrap [width
                                                               height]
                                                              width))}})


(pco/defresolver $aiport-repack
  [{::airport/keys [data]}]
  {::pco/input  [{::airport/data [:Days-from-start
                                  :Rain-mm
                                  :d18O
                                  :Date
                                  #_#_
                                  :dD
                                  :Comment]}]
   ::pco/output [{::day-rain [{:x [:data|]}
                              {:y [:data|]}]}
                 {::day-d18O [{:x [:data|]}
                              {:y [:data|]}
                              :meta]}]}
  (println (str "Last Point During Repack: "
                (last (:data| (:Days-from-start data)))
                " First Date: "
                (first (:data| (:Date data)))
                " Last Date: "
                (last (:data| (:Date data)))
                ))
  {::day-rain {:x (:Days-from-start data)
               :y (:Rain-mm data)}
   ::day-d18O {:x (:Days-from-start data)
               :y (:d18O data)}
   :meta      (mapv (fn [rain-mm]
                      {:Rain-mm rain-mm})
                    (:Rain-mm data))})
#_
(pathmore/check [{::day-d18O [:xy|]}])
#_
(pathmore/check [{::day-d18O [:hist]}])

(pco/defresolver $rain-axis
  [{::keys [width
            height
            scale
            margin-frac
            num-days
            day-rain]}]
  {::pco/input  [::width
                 ::height
                 ::scale
                 ::margin-frac
                 ::num-days
                 {::day-rain [{:y [:max]}]}]
   ::pco/output [::rain-axis]}
  {::rain-axis (-> (quickthing/primary-axis [[0 ;; negative days are cut off!
                                              0.0] ;; least amount of rain is zero..
                                             [num-days
                                              (-> day-rain
                                                  :y
                                                  :max)]]
                                            {:width       width
                                             :height      height
                                             :title       "Rain"
                                             :y-name      "Rain (mm)"
                                             :scale       scale
                                             :margin-frac margin-frac})
                   (assoc :grid
                          nil)
                   (assoc-in [:x-axis
                              :visible]
                             false)
                   (assoc-in [:y-axis
                              :visible]
                             true))})

(pco/defresolver $d18O-axis
  [{::keys [width
            height
            scale
            margin-frac
            num-days
            day-d18O]}]
  {::pco/input  [::width
                 ::height
                 ::scale
                 ::margin-frac
                 ;;
                 {::day-d18O [{:y [:max
                                   :min]}]}]
   ::pco/output [::d18O-axis]}
  (println (str "Number of Daus"
                num-days))
  {::d18O-axis (-> (quickthing/secondary-axis [[0  ;; negative days are cut off!
                                                (-> day-d18O
                                                    :y
                                                    :min
                                                    (* 1.2))]
                                               [num-days
                                                (-> day-d18O
                                                    :y
                                                    :max)]]
                                              {:width       width
                                               :height      height
                                               :scale       scale
                                               :y-name      "d18O"
                                               :margin-frac margin-frac
                                               :color       "#33ff"})
                   (assoc :grid
                          nil)
                   (assoc-in [:x-axis
                              :major]
                             [])
                   (assoc-in [:x-axis
                              :visible]
                             false) ;; can also be made on..
                   (assoc-in [:y-axis
                              :visible]
                             true))})

(pco/defresolver $year-ticks
  [{::keys [jan1st-days]}]
  {::pco/input  [{:jan1st-days [:year|
                                :jan1st|
                                :days-from-day-zero|]}]
   ::pco/output [::year-ticks]}
  {::year-ticks (:days-from-day-zero jan1st-days)})

(pco/defresolver $grid-layer
  [{::keys [width
            height
            scale
            margin-frac
            num-days
            jan1st-day-to-year]}]
  {::pco/input  [::width
                 ::height
                 ::scale
                 ::margin-frac
                 ::num-days
                 ::jan1st-day-to-year]
   ::pco/output [::grid-layer]}
  {::grid-layer (-> (quickthing/primary-axis [[0 ;; negative days are cut off!
                                               0.0]
                                              [num-days
                                               1.0]]
                                             {:width       width
                                              :height      height
                                              :x-name      "Years"
                                              :scale       scale
                                              :margin-frac margin-frac})
                    (assoc-in [:x-axis
                               :visible]
                              true)
                    (assoc-in [:y-axis
                               :visible]
                              false)
                    (assoc-in [:x-axis
                               :label]
                              (thi.ng.geom.viz.core/default-svg-label jan1st-day-to-year))
                    (assoc-in [:x-axis
                               :major]
                              (keys jan1st-day-to-year))
                    (assoc-in [:y-axis
                               :major]
                              [])
                    viz/svg-plot2d-cartesian)})


(pco/defresolver $rain-layer
  [{::keys [width
            num-days
            rain-axis
            day-rain]}]
  {::pco/input  [::width
                 ::num-days
                 ::rain-axis
                 {::day-rain [:xy-nonil|]}]
   ::pco/output [::rain-layer]}
  (println (str "Peek points building plot: "
                (last (:xy-nonil| day-rain))))
  {::rain-layer (-> rain-axis
                    (update :data
                            #(into %
                                   (quickthing/bars (:xy-nonil| day-rain)
                                                    {:attribs {:stroke-width (/ width
                                                                                num-days)
                                                               :stroke       "#000000"}})))
                    #_
                    (update :data
                            #(into %
                                   (quickthing/circles (->> missing-days-datavec
                                                            (mapv (fn [coord]
                                                                    coord)))
                                                       {:scale   6
                                                        :attribs {:fill "#f00"}})))
                    viz/svg-plot2d-cartesian)})

(pco/defresolver $d18O-layer
  [{::keys [width
            scale
            d18O-axis
            day-d18O]}]
  {::pco/input  [::width
                 ::scale
                 ::d18O-axis
                 {::day-d18O [:xy-nonil|]}]
   ::pco/output [::d18O-layer]}
  {::d18O-layer (-> d18O-axis
                    (update :data
                            #(into %
                                   (quickthing/circles (mapv (fn [[x
                                                                   y
                                                                   {:keys [Rain-mm]
                                                                    :as   meta}]]
                                                               [x
                                                                y
                                                                (if Rain-mm
                                                                  (merge meta
                                                                         {:radius (-> Rain-mm
                                                                                      Math/sqrt)})
                                                                  (merge meta ;; rain unknown
                                                                         {:radius       (/ scale
                                                                                           10);; fixed
                                                                          :stroke-width (/ scale
                                                                                           20)
                                                                          :stroke       "grey"}))])
                                                             (-> day-d18O
                                                                 :xy-nonil|))
                                                       {:scale   10
                                                        :attribs {:fill secondary-color}})))
                    viz/svg-plot2d-cartesian)})

(pco/defresolver $rain-subplot
  [{::keys [width
            height
            grid-layer
            d18O-layer
            rain-layer]}]
  {::pco/output [{::rain-subplot [::hiccup]}]}
  {::rain-subplot {::hiccup (-> (svg/group {}
                                           grid-layer
                                           rain-layer)
                                (quickthing/svg-wrap [width
                                                      height]
                                                     width))}})

(pco/defresolver $rain-d18O-subplot
  [{::keys [width
            height
            d18O-layer
            rain-subplot]}]
  {::pco/output [{::rain-d18O-subplot [::hiccup]}]}
  {::rain-d18O-subplot {::hiccup (-> (svg/group {}
                                                (::hiccup rain-subplot)
                                                d18O-layer)
                                     (quickthing/svg-wrap [width
                                                           height]
                                                          width))}})

(pco/defresolver $index-repack
  [{::index/keys [data]}]
  {::pco/input  [{::index/data [:Date
                                :Days-from-start
                                :Above-Index
                                :Below-Index]}]
   ::pco/output [{::day-above [{:x [:data|]}
                               {:y [:data|]}]}
                 {::day-below [{:x [:data|]}
                               {:y [:data|]}]}]}
  {::day-above {:x (:Days-from-start data)
                :y (:Above-Index data)}
   ::day-below {:x (:Days-from-start data)
                :y (:Below-Index data)}})



(pco/defresolver $airport-classified
  [{::airport/keys [data-classified]}]
  {::pco/input  [{::airport/data-classified [:Days-from-start
                                             :Rain-mm
                                             :d18O
                                             :dD
                                             :Comment
                                             :Date
                                             :Above-Index
                                             :Below-Index
                                             :Above?]}]
   ::pco/output [{::day-d18O-classified [:xy|]}
                 {::day-d18O-above [:xy|]}
                 {::day-d18O-below [:xy|]}
                 {::d18O-rain-above [:xy|]}
                 {::d18O-rain-below [:xy|]} ]}
  (let [collated-daily        (mapv (fn [day
                                         O18
                                         rain-mm
                                         above-flag]
                                      [day
                                       O18
                                       {:Rain-mm   rain-mm
                                        :AboveFlag above-flag}])
                                    (:data| (:Days-from-start data-classified))
                                    (:data| (:d18O data-classified))
                                    (:data| (:Rain-mm data-classified))
                                    (:data| (:Above? data-classified)))
        for-weighted-averages (mapv (fn [O18
                                         rain-mm
                                         above-flag]
                                      [O18
                                       rain-mm
                                       {:AboveFlag above-flag}])
                                    (:data| (:d18O data-classified))
                                    (:data| (:Rain-mm data-classified))
                                    (:data| (:Above? data-classified)))]
    (let [grouped-daily    (group-by (fn [entry]
                                       (-> entry
                                           (nth 2)
                                           :AboveFlag))
                                     collated-daily)
          grouped-averages (group-by (fn [entry]
                                       (-> entry
                                           (nth 2)
                                           :AboveFlag))
                                     for-weighted-averages)]
      {::day-d18O-classified {:xy| collated-daily}
       ::day-d18O-above      {:xy| (get grouped-daily
                                        true)}
       ::day-d18O-below      {:xy| (get grouped-daily
                                        false)}
       ::d18O-rain-above     {:xy| (get grouped-averages
                                        true)}
       ::d18O-rain-below     {:xy| (get grouped-averages
                                        false)}})))
#_
(pathmore/check ::d18O-rain-above)

(pco/defresolver $d18O-classified-layer ;; reuse resolver
  [{::keys [day-d18O-above
            day-d18O-below
            scale
            width
            d18O-axis]}]
  {::pco/input  [{::day-d18O-above [:xy-nonil|]}
                 {::day-d18O-below [:xy-nonil|]}
                 ::scale
                 ::width
                 ::d18O-axis] ;;
   ::pco/output [::d18O-classified-layer]}
  {::d18O-classified-layer (-> d18O-axis
                               (update :data
                                       #(into %
                                              (quickthing/circles (mapv (fn [[x
                                                                              y
                                                                              {:keys [Rain-mm]
                                                                               :as   meta}]]
                                                                          [x
                                                                           y
                                                                           (if Rain-mm
                                                                             (-> meta
                                                                                 (merge {:radius (-> Rain-mm
                                                                                                     Math/sqrt)})
                                                                                 (dissoc :Above?))
                                                                             (-> meta
                                                                                 (merge {:radius       (/ scale
                                                                                                          10);; fixed
                                                                                         :stroke-width (/ scale
                                                                                                          20)
                                                                                         :stroke       "grey"})
                                                                                 (dissoc :Above?)))])
                                                                        (-> day-d18O-above
                                                                            :xy-nonil|))
                                                                  {:scale   10
                                                                   :attribs {:fill summer-color}})))
                               (update :data
                                       #(into %
                                              (quickthing/circles (mapv (fn [[x
                                                                              y
                                                                              meta]]
                                                                          (let [rain (-> meta
                                                                                         :Rain-mm)]
                                                                            [x
                                                                             y
                                                                             (if rain
                                                                               (-> meta
                                                                                   (merge {:radius (-> rain
                                                                                                       Math/sqrt)})
                                                                                   (dissoc :Above?))
                                                                               (-> meta
                                                                                   (merge {:radius       (/ scale
                                                                                                            10) ;; fixed
                                                                                           :stroke-width (/ scale
                                                                                                            20)
                                                                                           :stroke       "grey"})
                                                                                   (dissoc :Above?)))]))
                                                                        (:xy-nonil| day-d18O-below))
                                                                  {:scale   10
                                                                   :attribs {:fill winter-color}})))
                               viz/svg-plot2d-cartesian)})


(pco/defresolver $d18O-averages-layer
  [{::keys [d18O-axis
            scale
            day-d18O
            d18O-rain
            d18O-rain-above
            d18O-rain-below
            width]}]
  {::pco/input  [{::day-d18O [{:x [:max] }]}
                 {::d18O-rain [{:x [:max]}
                               :weighted-mean]}
                 {::d18O-rain-above [:weighted-mean]}
                 {::d18O-rain-below [:weighted-mean]}
                 ::scale
                 ::width
                 ::d18O-axis]
   ::pco/output [::d18O-total-average-layer
                 ::d18O-above-average-layer
                 ::d18O-below-average-layer]}
  (let [day-num-max     (-> day-d18O
                            :x
                            :max)
        d18O-total-mean (-> d18O-rain ;; could be done earlier in a separate resolver
                            :weighted-mean)
        d18O-above-mean (-> d18O-rain-above
                            :weighted-mean)
        d18O-below-mean (-> d18O-rain-below
                            :weighted-mean)
        ]
    (let [static-font-size       (* 0.008
                                    day-num-max)
          d18O-total-mean-coords [[1.0
                                   d18O-total-mean]
                                  [(dec day-num-max)
                                   d18O-total-mean]]
          d18O-above-mean-coords [[1.0
                                   d18O-above-mean]
                                  [(dec day-num-max)
                                   d18O-above-mean]]
          d18O-below-mean-coords [[1.0
                                   d18O-below-mean]
                                  [(dec day-num-max)
                                   d18O-below-mean]]]
      {::d18O-total-average-layer (-> d18O-axis
                                      (update :data
                                              #(into %
                                                     (quickthing/dashed-line d18O-total-mean-coords
                                                                             {:attribs {:stroke-width   10.0
                                                                                        :stroke         "#3333ff"
                                                                                        :stroke-opacity 0.3}})))
                                      (update :data
                                              #(into %
                                                     (quickthing/labels [[(* 0.33 ;; x offset
                                                                             day-num-max)
                                                                          (-> d18O-rain
                                                                              :x
                                                                              :max
                                                                              (* 1.1))
                                                                          {:text              (str "WEIGHTED AVERAGE d18O: "
                                                                                                   (format "%.2f" d18O-total-mean))
                                                                           :dy                (- static-font-size)
                                                                           :fill              "#3333ff"
                                                                           :stroke-opacity    0.3
                                                                           :text-anchor       "beginning"
                                                                           :font-size         static-font-size
                                                                           #_#_
                                                                           :dominant-baseline "hanging"}]])))
                                      viz/svg-plot2d-cartesian)
       ::d18O-above-average-layer (-> d18O-axis
                                      (update :data
                                              #(into %
                                                     (quickthing/dashed-line d18O-above-mean-coords
                                                                             {:attribs {:stroke-width 10.0
                                                                                        :stroke       "#aa8800"}})))
                                      (update :data
                                              #(into %
                                                     (quickthing/labels [[(* 0.33 ;; x offset
                                                                             day-num-max)
                                                                          (-> d18O-rain
                                                                              :x
                                                                              :max
                                                                              (* 1.1))
                                                                          {:text              (str "SUMMER MONSOON d18O: "
                                                                                                   (format "%.2f" d18O-above-mean))
                                                                           :dy                static-font-size
                                                                           :fill              summer-color
                                                                           :text-anchor       "beginning"
                                                                           :font-size         static-font-size
                                                                           #_#_
                                                                           :dominant-baseline "hanging"}]])))
                                      viz/svg-plot2d-cartesian)
       ::d18O-below-average-layer (-> d18O-axis
                                      (update :data
                                              #(into %
                                                     (quickthing/dashed-line d18O-below-mean-coords
                                                                             {:attribs {:stroke-width 10.0
                                                                                        :stroke       "#00aa88"}})))
                                      (update :data
                                              #(into %
                                                     (quickthing/labels [[(* 0.33 ;; x offset
                                                                             day-num-max)
                                                                          (-> d18O-rain
                                                                              :x
                                                                              :max
                                                                              (* 1.1))
                                                                          {:text              (str "WINTER MONSOON d18O: "
                                                                                                   (format "%.2f" d18O-below-mean))
                                                                           :fill              winter-color
                                                                           :text-anchor       "beginning"
                                                                           :font-size         static-font-size
                                                                           #_#_
                                                                           :dominant-baseline "hanging"}]])))
                                      viz/svg-plot2d-cartesian)})))


(pco/defresolver $rain-d18O-average-subplot
  [{::keys [width
            height
            rain-d18O-subplot
            d18O-total-average-layer]}]
  {::pco/output [{::rain-d18O-average-subplot [::hiccup]}]}
  {::rain-d18O-average-subplot {::hiccup (-> (svg/group {}
                                                        (::hiccup rain-d18O-subplot)
                                                        d18O-total-average-layer)
                                             (quickthing/svg-wrap [width
                                                                   height]
                                                                  width))}})


(pco/defresolver $rain-d18O-classified-subplot
  [{::keys [width
            height
            d18O-classified-layer
            #_
            rain-d18O-subplot
            rain-subplot]}]
  {::pco/output [{::rain-d18O-classified-subplot [::hiccup]}]}
  {::rain-d18O-classified-subplot {::hiccup (-> (svg/group {}
                                                           (::hiccup rain-subplot)
                                                           d18O-classified-layer)
                                                (quickthing/svg-wrap [width
                                                                      height]
                                                                     width))}})


(pco/defresolver $rain-d18O-classified-average-subplot
  [{::keys [width
            height
            rain-d18O-classified-subplot
            d18O-above-average-layer
            d18O-below-average-layer]}]
  {::pco/output [{::rain-d18O-classified-average-subplot [::hiccup]}]}
  {::rain-d18O-classified-average-subplot {::hiccup (-> (svg/group {}
                                                                   (::hiccup rain-d18O-classified-subplot)
                                                                   d18O-above-average-layer
                                                                   d18O-below-average-layer)
                                                        (quickthing/svg-wrap [width
                                                                              height]
                                                                             width))}})




(pco/defresolver $index-axis
  [{::keys [width
            height
            scale
            margin-frac
            num-days
            day-above
            day-below
            ]}]
  {::pco/input  [::width
                 ::height
                 ::scale
                 ::margin-frac
                 ::num-days
                 {::day-above [{:x [:max]}
                               {:y [:max]}]}
                 {::day-below [#_{:x [:max]} ;; should be the same
                               {:y [:max]}]}]
   ::pco/output [::index-axis]}
  {::index-axis (-> (quickthing/primary-axis [[0
                                               0.0] ;; index min is always zero
                                              [num-days
                                               (max (-> day-above
                                                        :y
                                                        :max)
                                                    (-> day-below
                                                        :y
                                                        :max))]]
                                             {:width       width
                                              :height      height
                                              :title       "Monsoon"
                                              :legend      [["SUMMER MONSOON"
                                                             {:fill   summer-color
                                                              :stroke nil}]
                                                            ["WINTER MONSOON"
                                                             {:fill   winter-color
                                                              :stroke nil}]]
                                              :y-name      "Index (unitless)"
                                              :scale       scale
                                              :margin-frac margin-frac
                                              :color       "#0008"})
                    (assoc :grid
                           nil)
                    (assoc-in [:x-axis
                               :visible]
                              false)
                    (assoc-in [:y-axis
                               :visible]
                              false))})



(pco/defresolver $index-layer
  [{::keys [day-above
            day-below
            width ;; needs default??
            index-axis
            num-days]}]
  {::pco/input  [{::day-above [:xy-nonil|]}
                 {::day-below [:xy-nonil|]}
                 ::width ;; needs default??
                 ::index-axis
                 ::num-days]
   ::pco/output [::index-layer]}
  {::index-layer (-> index-axis
                     (update :data
                             #(into %
                                    (quickthing/bars (:xy-nonil| day-below)
                                                     {:attribs {:stroke-width (/ width
                                                                                 num-days)
                                                                :stroke       winter-color}})))
                     (update :data
                             #(into %
                                    (quickthing/bars (:xy-nonil| day-above)
                                                     {:attribs {:stroke-width (/ width
                                                                                 num-days)
                                                                :stroke       summer-color}})))
                     viz/svg-plot2d-cartesian)})

(pco/defresolver $index-subplot
  [{::keys [width
            height
            grid-layer
            index-layer]}]
  {::pco/output [{::index-subplot [::hiccup]}]}
  {::index-subplot {::hiccup (-> (svg/group {}
                                            grid-layer
                                            index-layer)
                                 (quickthing/svg-wrap [width
                                                       height]
                                                      width))}})

(pco/defresolver $index-d18O-subplot
  [{::keys [width
            height
            grid-layer
            d18O-classified-layer
            index-layer]}]
  {::pco/output [{::index-d18O-subplot [::hiccup]}]}
  {::index-d18O-subplot {::hiccup (-> (svg/group {}
                                                 grid-layer
                                                 index-layer
                                                 d18O-classified-layer)
                                      (quickthing/svg-wrap [width
                                                            height]
                                                           width))}})

(pco/defresolver $isotope-d18O-classified-average-subplot
  [{::keys [width
            height
            d18O-classified-layer
            grid-layer
            index-layer
            d18O-above-average-layer
            d18O-below-average-layer]}]
  {::pco/output [{::isotope-d18O-classified-average-subplot [::hiccup]}]}
  {::isotope-d18O-classified-average-subplot {::hiccup (-> (svg/group {}
                                                                      grid-layer
                                                                      index-layer
                                                                      d18O-classified-layer
                                                                      d18O-above-average-layer
                                                                      d18O-below-average-layer)
                                                           (quickthing/svg-wrap [width
                                                                                 height]
                                                                                width))}})



(pco/defresolver $hist-rain-classified-subplot
  [{::keys [width
            height
            scale
            margin-frac
            ;;
            d18O-rain
            d18O-rain-above
            d18O-rain-below]}]
  {::pco/input  [::width
                 ::height
                 ::scale
                 {::d18O-rain [{:hist [:xy-nonil|
                                       {:y [:max]}]}
                               {:x [:min
                                    :max]}]}
                 {::d18O-rain-above [{:hist [:xy-nonil|]}
                                     {:x [:max]}]}
                 {::d18O-rain-below [{:hist [:xy-nonil|]}
                                     {:x [:max]}]}]
   ::pco/output [{::hist-rain-classified-subplot [::hiccup]}]}
  (let [d18O-range-min (-> d18O-rain
                           :x
                           :min)
        d18O-range-max (-> d18O-rain
                           :x
                           :max)]
    {::hist-rain-classified-subplot {::hiccup (-> (quickthing/primary-axis [[d18O-range-min
                                                                             0.0]
                                                                            [d18O-range-max
                                                                             (-> d18O-rain
                                                                                 :hist
                                                                                 :y
                                                                                 :max)]]
                                                                           {:width       width
                                                                            :height      height
                                                                            :title       ""
                                                                            :legend      [["" #_"ALL RAINY DAYS"
                                                                                           {:fill   "lightgrey"
                                                                                            :stroke "lightgrey"}]
                                                                                          ["SUMMER MONSOON"
                                                                                           {:fill   summer-color
                                                                                            :stroke nil}]
                                                                                          ["WINTER MONSOON"
                                                                                           {:fill   winter-color
                                                                                            :stroke nil}]]
                                                                            :x-name      "d18O"
                                                                            :y-name      "Rain (mm) per bin"
                                                                            :scale       scale
                                                                            :margin-frac margin-frac
                                                                            :color       "#0008"})
                                                  (assoc :grid
                                                         nil)
                                                  (update :data
                                                          #(into %
                                                                 (quickthing/bars (->> d18O-rain-above
                                                                                       :hist
                                                                                       :xy-nonil|
                                                                                       (mapv (fn [[x-coord
                                                                                                   y-coord]]
                                                                                               [(- x-coord
                                                                                                   0.125)
                                                                                                y-coord])))
                                                                                  {:attribs {:stroke-width (/ width
                                                                                                              (* 6.0
                                                                                                                 (- d18O-range-max
                                                                                                                    d18O-range-min)))
                                                                                             :stroke       summer-color}})))
                                                  (update :data
                                                          #(into %
                                                                 (quickthing/bars (->> d18O-rain-below
                                                                                       :hist
                                                                                       :xy-nonil|
                                                                                       (mapv (fn [[x-coord
                                                                                                   y-coord]]
                                                                                               [(+ x-coord
                                                                                                   0.125)
                                                                                                y-coord])))
                                                                                  {:attribs {:stroke-width (/ width
                                                                                                              (* 6.0
                                                                                                                 (- d18O-range-max
                                                                                                                    d18O-range-min)))
                                                                                             :stroke       winter-color}})))
                                                  viz/svg-plot2d-cartesian
                                                  (quickthing/svg-wrap [width
                                                                        height]
                                                                       width))}}))

#_
(pco/defresolver $hist-rain-subplot
  [{::keys [width
            height
            hist-rain-all-subplot
            hist-rain-classified-subplot]}]
  {::pco/input  [::hist-rain-all-subplot
                 ::hist-rain-classified-subplot]
   ::pco/output [::hist-rain-subplot]}
  {::hist-rain-subplot {::hiccup (-> (svg/group {}
                                                (::hiccup hist-rain-all-subplot)
                                                (::hiccup hist-rain-classified-subplot))
                                     (quickthing/svg-wrap [width
                                                           height]
                                                          width))}})

(pco/defresolver $isogsm-repack
  [{::isogsm/keys [data]}]
  {::pco/input  [{::isogsm/data [:Days-from-start
                                 :Rain-d18O
                                 :Vapor-d18O
                                 :Rain-mm]}]
   ::pco/output [{::day-isogsm-rain-d18O [{:x [:data|]}
                                          {:y [:data|]}
                                          :meta]}
                 {::day-isogsm-vapor-d18O [{:x [:data|]}
                                           {:y [:data|]}]}]}
  #_
  (println (str "Last Point During Repack: "
                (last (:Days-from-start data))
                " First Date: "
                (first (:Date data))
                " Last Date: "
                (last (:Date data))
                
                ))
  {::day-isogsm-rain-d18O  {:x    (:Days-from-start data)
                            :y    (:Rain-d18O data)
                            :meta (->> data
                                       :Rain-mm
                                       :data|
                                       (mapv (fn [rain-mm]
                                               {:Rain-mm rain-mm})))}
   ::day-isogsm-vapor-d18O {:x (:Days-from-start data)
                            :y (:Vapor-d18O data)}})
#_
(pathmore/check ::day-isogsm-rain-d18O)

(pco/defresolver $isogsm-rain-d18O-layer
  [{::keys [width
            scale
            d18O-axis
            day-isogsm-rain-d18O]}]
  {::pco/input  [::width
                 ::scale
                 ::d18O-axis
                 {::day-isogsm-rain-d18O [:xy-nonil|]}]
   ::pco/output [::isogsm-rain-d18O-layer]}
  {::isogsm-rain-d18O-layer (-> d18O-axis
                                (update :data
                                        #(into %
                                               (quickthing/circles (mapv (fn [[x
                                                                               y
                                                                               {:keys [Rain-mm]
                                                                                :as   meta}]]
                                                                           [x
                                                                            y
                                                                            (if Rain-mm
                                                                              (merge meta
                                                                                     {:radius (-> Rain-mm
                                                                                                  Math/sqrt)})
                                                                              (merge meta ;; rain unknown
                                                                                     {:radius       (/ scale
                                                                                                       10);; fixed
                                                                                      :stroke-width (/ scale
                                                                                                       20)
                                                                                      :stroke       "grey"}))])
                                                                         (-> day-isogsm-rain-d18O
                                                                             :xy-nonil|))
                                                                   {:scale   10
                                                                    :attribs {:fill "red"}})))
                                viz/svg-plot2d-cartesian)})

(pco/defresolver $isogsm-vapor-d18O-layer
  [{::keys [width
            scale
            d18O-axis
            day-isogsm-vapor-d18O]}]
  {::pco/input  [::width
                 ::scale
                 ::d18O-axis
                 {::day-isogsm-vapor-d18O [:xy-nonil|]}]
   ::pco/output [::isogsm-vapor-d18O-layer]}
  {::isogsm-vapor-d18O-layer (-> d18O-axis
                                 (update :data
                                         #(into %
                                                (quickthing/solid-line (-> day-isogsm-vapor-d18O
                                                                           :xy-nonil|)
                                                                       {:scale   5
                                                                        :attribs {:stroke "green"}})))
                                 viz/svg-plot2d-cartesian)})


(pco/defresolver $isogsm-rain-d18O-subplot
  [{::keys [width
            height
            rain-axis
            d18O-layer
            isogsm-vapor-d18O-layer
            isogsm-rain-d18O-layer
            rain-subplot]}]
  {::pco/output [{::isogsm-rain-d18O-subplot [::hiccup]}]}
  {::isogsm-rain-d18O-subplot {::hiccup (-> (svg/group {}
                                                       rain-axis
                                                       isogsm-vapor-d18O-layer
                                                       isogsm-rain-d18O-layer
                                                       d18O-layer)
                                            (quickthing/svg-wrap [width
                                                                  height]
                                                                 width))}})

#_
(def env
  (-> (pci/register {::p.a.eql/parallel? true}
                    [$hiccup2svg
                     $day-minmax
                     $aiport-repack-mini ;; No Days
                     $meteoric-water-line-subplot
                     $amount-effect-subplot
                     $aiport-repack
                     $rain-axis
                     $d18O-axis
                     $grid-layer
                     $rain-layer
                     $d18O-layer
                     $rain-subplot
                     $rain-d18O-subplot
                     $index-repack
                     $index-axis
                     $airport-classified
                     $d18O-classified-layer
                     $d18O-averages-layer
                     $rain-d18O-average-subplot
                     $rain-d18O-classified-subplot
                     $rain-d18O-classified-average-subplot
                     $index-layer
                     $index-subplot
                     $index-d18O-subplot
                     $isotope-d18O-classified-average-subplot
                     $hist-rain-classified-subplot
                     $isogsm-repack
                     $isogsm-rain-d18O-layer
                     $isogsm-vapor-d18O-layer
                     $isogsm-rain-d18O-subplot
                     ])
      (pcp/with-plan-cache plan-cache*)
      kxygk.pathmore.cache/inject-for-all-resolvers))


(pco/defresolver $nakhon-rain-layer
  "Rains in Nakhon"
  [{:keys  [nakhon-gauge]
    ::keys [width
            height
            scale
            margin-frac]}]
  {::pco/input  [::width
                 ::height
                 ::scale
                 ::margin-frac
                 {:nakhon-gauge [{::ghcnd/daily-rain [:xy-nonil|
                                                      {:y [:data|]}]}]}]
   ::pco/output [{::nakhon-modern-rain-layer [::hiccup]}]}
  (let [rain-xy (-> nakhon-gauge
                    ::ghcnd/daily-rain
                    :xy-nonil|)]
    {::nakhon-modern-rain-layer {::hiccup (-> (quickthing/primary-axis rain-xy
                                                                       {:width       width
                                                                        :height      height
                                                                        :y-name      "Rain (mm)"
                                                                        :scale       scale
                                                                        :margin-frac margin-frac
                                                                        #_#_
                                                                        :color       "#0008"})
                                              (assoc-in [:x-axis
                                                         :visible]
                                                        false)
                                              (assoc-in [:y-axis
                                                         :visible]
                                                        true)
                                              (assoc-in [:grid]
                                                        nil)
                                              (update :data
                                                      #(into %
                                                             (quickthing/bars rain-xy
                                                                              {:attribs {:stroke       "black"
                                                                                         :stroke-width (/ width
                                                                                                          (count rain-xy))}})))
                                              viz/svg-plot2d-cartesian
                                              (quickthing/svg-wrap [width
                                                                    height]
                                                                   width))}}))

(pco/defresolver $nakhon-d18O-classified-average-subplot
  [{::keys [width
            height
            grid-layer
            d18O-classified-layer
            nakhon-modern-rain-layer
            d18O-above-average-layer
            d18O-below-average-layer]}]
  {::pco/output [{::nakhon-d18O-classified-average-subplot [::hiccup]}]}
  {::nakhon-d18O-classified-average-subplot {::hiccup (-> (svg/group {}
                                                                     grid-layer
                                                                     (::hiccup nakhon-modern-rain-layer)
                                                                     d18O-classified-layer
                                                                     #_#_
                                                                     d18O-above-average-layer
                                                                     d18O-below-average-layer)
                                                          (quickthing/svg-wrap [width
                                                                                height]
                                                                               width))}})



#_
((ds/filter-column glued
                   "ENSO"
                   #(pos? %)))

#_
(pco/defresolver $d18O-below-select-layer
  [{::keys [width
            scale
            d18O-axis
            d18O-extremes-data
            d18O-other-data]}]
  {::pco/input  [::width
                 ::d18O-axis
                 {::d18O-extremes-data [:xy-nonil|]}
                 {::d18O-other-data [:xy-nonil|]}] ;;
   ::pco/output [::d18O-below-select-layer]}
  {::d18O-below-select-layer (let [last-day                 (apply max ;; find last day
                                                                   (into (mapv first
                                                                               (:xy-nonil| d18O-extremes-data))
                                                                         (mapv first
                                                                               (:xy-nonil| d18O-other-data))))
                                   max-val                  (apply max
                                                                   (into (mapv second
                                                                               (:xy-nonil| d18O-extremes-data))
                                                                         (mapv second
                                                                               (:xy-nonil| d18O-other-data))))
                                   ;; EXTREME
                                   extreme-volume           (->> d18O-extremes-data
                                                                 :xy-nonil|
                                                                 (mapv last)
                                                                 (mapv :Rain-mm)
                                                                 (filterv some?)
                                                                 (apply +))
                                   extreme-weighted-average (/ (->> d18O-extremes-data
                                                                    :xy-nonil|
                                                                    (mapv (fn [[_
                                                                                d18O
                                                                                {:keys [Rain-mm]}]]
                                                                            (if (nil? Rain-mm)
                                                                              0
                                                                              (* d18O
                                                                                 Rain-mm))))
                                                                    (apply +))
                                                               extreme-volume)
                                   ;; OTHER
                                   other-volume             (->> d18O-other-data
                                                                 :xy-nonil|
                                                                 (mapv last)
                                                                 (mapv :Rain-mm)
                                                                 (filterv some?)
                                                                 (apply +))
                                   other-weighted-average   (/ (->> d18O-other-data
                                                                    :xy-nonil|
                                                                    (mapv (fn [[_
                                                                                d18O
                                                                                {:keys [Rain-mm]}]]
                                                                            (if (nil? Rain-mm)
                                                                              0
                                                                              (* d18O
                                                                                 Rain-mm))))
                                                                    (apply +))
                                                               other-volume)
                                   #_#_
                                   average-d18O-other       (/ (apply + (mapv *
                                                                              (vec (:Rain-mm other-events-table))
                                                                              (vec (:d18O other-events-table))))
                                                               (apply + (vec (:Rain-mm other-events-table))))]
                               (-> d18O-axis
                                   (assoc :legend
                                          [["WINTER STORM"
                                            {:fill   "red"
                                             :stroke nil}]])
                                   (update :data
                                           #(into %
                                                  (quickthing/circles (:xy-nonil| d18O-extremes-data)
                                                                      {:scale   (/ scale
                                                                                   4.0)
                                                                       :attribs {:stroke       "red"
                                                                                 :stroke-width (/ scale
                                                                                                  20.0)
                                                                                 :fill         "none"}})))
                                   (update :data
                                           #(into %
                                                  (quickthing/dashed-line [[0.0
                                                                            extreme-weighted-average]
                                                                           [last-day
                                                                            extreme-weighted-average]]
                                                                          {:attribs {:stroke-width 10.0
                                                                                     :stroke       "red"}})))
                                   #_
                                   (update :data
                                           #(into %
                                                  (quickthing/circles points-other
                                                                      {:scale   (/ scale
                                                                                   4.0)
                                                                       :attribs {:stroke       "blue"
                                                                                 :stroke-width (/ scale
                                                                                                  20.0)
                                                                                 :fill         "none"}})))
                                   (update :data
                                           #(into %
                                                  (quickthing/dashed-line [[0.0
                                                                            other-weighted-average]
                                                                           [last-day
                                                                            other-weighted-average]]
                                                                          {:attribs {:stroke-width 10.0
                                                                                     :stroke       "blue"}})))
                                   (update :data
                                           #(into %
                                                  (quickthing/labels [[(* 0.33 ;; x offset
                                                                          last-day)
                                                                       (* max-val
                                                                          1.0)
                                                                       {:text              (str "WINTER STORMS: "
                                                                                                (format "%.2f"
                                                                                                        extreme-weighted-average)
                                                                                                " ("
                                                                                                (format "%.2f" (* 100
                                                                                                                  (/ extreme-volume
                                                                                                                     (+ extreme-volume
                                                                                                                        other-volume))))
                                                                                                "% of total)")
                                                                        :fill              "red"
                                                                        :text-anchor       "beginning"
                                                                        :font-size         (/ scale
                                                                                              2)
                                                                        #_#_
                                                                        :dominant-baseline "hanging"}]])))
                                   (update :data
                                           #(into %
                                                  (quickthing/labels [[(* 0.33 ;; x offset
                                                                          last-day)
                                                                       (* max-val
                                                                          1.2)
                                                                       {:text              (str "NORMAL RAINS: "
                                                                                                (format "%.2f"
                                                                                                        other-weighted-average)
                                                                                                " ("
                                                                                                (format "%.2f" (* 100
                                                                                                                  (/ other-volume
                                                                                                                     (+ extreme-volume
                                                                                                                        other-volume))))
                                                                                                "% of total)")
                                                                        :fill              "blue"
                                                                        :text-anchor       "beginning"
                                                                        :font-size         (/ scale
                                                                                              2)
                                                                        #_#_
                                                                        :dominant-baseline "hanging"}]])))
                                   viz/svg-plot2d-cartesian))})

(def $resolvers$
  (->> (pathmore/find-resolvers)
       (mapv pathmore/inject-simple-cache)))

(def $env$
  (pci/register {::p.a.eql/parallel? true}
                (pathmore/dedupe-resolvers [$resolvers$
                                            kxygk.mathom.core/$resolvers$
                                            airport/$resolvers$])))

(pco/defresolver $index-d18O-big-events-subplot
  [{::keys [width
            height
            grid-layer
            d18O-classified-layer
            d18O-below-select-layer
            index-layer]}]
  {::pco/output [{::index-d18O-big-events-subplot [::hiccup]}]}
  {::index-d18O-big-events-subplot {::hiccup (-> (svg/group {}
                                                            grid-layer
                                                            index-layer
                                                            d18O-classified-layer
                                                            d18O-below-select-layer)
                                                 (quickthing/svg-wrap [width
                                                                       height]
                                                                      width))}})

#_#_
(pco/defresolver $rain-d18O-index-2stack
  [{::keys [width
            height
            rain-d18O-subplot
            index-subplot]}]
  {::pco/output [{::rain-d18O-index-2stack [::hiccup]}]}
  {::rain-d18O-index-2stack {::hiccup (-> (quickthing/group-plots-grid [[(::hiccup rain-d18O-subplot)]
                                                                        [(::hiccup index-subplot)]])
                                          (quickthing/svg-wrap [width
                                                                (* 2.0
                                                                   height)]
                                                               width))}})


(pco/defresolver $rain-d18O-classified-index-2stack
  [{::keys [width
            height
            rain-d18O-classified-subplot
            index-subplot]}]
  {::pco/output [{::rain-d18O-classified-index-2stack [::hiccup]}]}
  (identity  {::rain-d18O-classified-index-2stack {::hiccup (-> (quickthing/group-plots-grid [[(::hiccup rain-d18O-classified-subplot)]
                                                                                              [(::hiccup index-subplot)]])
                                                                (quickthing/svg-wrap [width
                                                                                      (* 2.0
                                                                                         height)]
                                                                                     width))}}))

(pco/defresolver $hist-count-all-subplot
  [{::keys [width
            height
            scale
            margin-frac
            ;;
            d18O-range-min
            d18O-range-max
            d18O-rain]}]
  {::pco/input  [::width
                 ::height
                 ::scale
                 ::d18O-range-min
                 ::d18O-range-max
                 {::d18O-rain [{:x [{:hist [:xy-nonil|
                                            {:y [:max]}]}
                                    :max]}]}]
   ::pco/output [{::hist-count-all-subplot [::hiccup]}]}
  {::hist-count-all-subplot {::hiccup (-> (quickthing/primary-axis [[d18O-range-min
                                                                     0.0]
                                                                    [d18O-range-max
                                                                     (-> d18O-rain
                                                                         :x
                                                                         :hist
                                                                         :y
                                                                         :max)]]
                                                                   {:width       width
                                                                    :height      height
                                                                    :title       ""
                                                                    :legend      [["ALL RAINY DAYS"
                                                                                   {:fill   "lightgrey"
                                                                                    :stroke "lightgrey"}]
                                                                                  #_#_
                                                                                  ["SUMMER MONSOON"
                                                                                   {:fill   "#aa8800"
                                                                                    :stroke nil}]
                                                                                  ["WINTER MONSOON"
                                                                                   {:fill   "#00aa88"
                                                                                    :stroke nil}]]
                                                                    :x-name      "d18O"
                                                                    :y-name      "Counts"
                                                                    :scale       scale
                                                                    :margin-frac margin-frac
                                                                    :color       "#0008"})
                                          (assoc :grid
                                                 nil)
                                          (update :data
                                                  #(into %
                                                         (quickthing/bars (->> d18O-rain
                                                                               :x
                                                                               :hist
                                                                               :xy-nonil|)
                                                                          {:attribs {:stroke-width (/ width
                                                                                                      (* 3.0
                                                                                                         (- d18O-range-max
                                                                                                            d18O-range-min)))
                                                                                     :stroke       "lightgrey"}})))
                                          viz/svg-plot2d-cartesian
                                          (quickthing/svg-wrap [width
                                                                height]
                                                               width))}})

(pco/defresolver $hist-count-classified-subplot
  [{::keys [width
            height
            scale
            margin-frac
            ;;
            d18O-range-min
            d18O-range-max
            d18O-rain
            d18O-rain-above
            d18O-rain-below]}]
  {::pco/input  [::width
                 ::height
                 ::scale
                 ::d18O-range-min
                 ::d18O-range-max
                 {::d18O-rain [{:x [{:hist [:xy-nonil|
                                            {:y [:max]}]}
                                    :max]}]}
                 {::d18O-rain-above [{:x [{:hist [:xy-nonil|]}
                                          :max]}]}
                 {::d18O-rain-below [{:x [{:hist [:xy-nonil|]}
                                          :max]}]}]
   ::pco/output [{::hist-count-classified-subplot [::hiccup]}]}
  {::hist-count-classified-subplot {::hiccup (-> (quickthing/primary-axis [[d18O-range-min
                                                                            0.0]
                                                                           [d18O-range-max
                                                                            (-> d18O-rain
                                                                                :x
                                                                                :hist
                                                                                :y
                                                                                :max)]]
                                                                          {:width       width
                                                                           :height      height
                                                                           :title       ""
                                                                           :legend      [["" #_"ALL RAINY DAYS"
                                                                                          {:fill   "lightgrey"
                                                                                           :stroke "lightgrey"}]
                                                                                         ["SUMMER MONSOON"
                                                                                          {:fill   summer-color
                                                                                           :stroke nil}]
                                                                                         ["WINTER MONSOON"
                                                                                          {:fill   winter-color
                                                                                           :stroke nil}]]
                                                                           :x-name      "d18O"
                                                                           :y-name      "Counts"
                                                                           :scale       scale
                                                                           :margin-frac margin-frac
                                                                           :color       "#0008"})
                                                 (assoc :grid
                                                        nil)
                                                 (update :data
                                                         #(into %
                                                                (quickthing/bars (->> d18O-rain-above
                                                                                      :x
                                                                                      :hist
                                                                                      :xy-nonil|
                                                                                      (mapv (fn [[x-coord
                                                                                                  y-coord]]
                                                                                              [(- x-coord
                                                                                                  0.125)
                                                                                               y-coord])))
                                                                                 {:attribs {:stroke-width (/ width
                                                                                                             (* 6.0
                                                                                                                (- d18O-range-max
                                                                                                                   d18O-range-min)))
                                                                                            :stroke       summer-color}})))
                                                 (update :data
                                                         #(into %
                                                                (quickthing/bars (->> d18O-rain-below
                                                                                      :x
                                                                                      :hist
                                                                                      :xy-nonil|
                                                                                      (mapv (fn [[x-coord
                                                                                                  y-coord]]
                                                                                              [(+ x-coord
                                                                                                  0.125)
                                                                                               y-coord])))
                                                                                 {:attribs {:stroke-width (/ width
                                                                                                             (* 6.0
                                                                                                                (- d18O-range-max
                                                                                                                   d18O-range-min)))
                                                                                            :stroke       winter-color}})))
                                                 viz/svg-plot2d-cartesian
                                                 (quickthing/svg-wrap [width
                                                                       height]
                                                                      width))}})


(pco/defresolver $hist-count-subplot
  [{::keys [width
            height
            hist-count-all-subplot
            hist-count-classified-subplot]}]
  {::pco/input  [::hist-count-all-subplot
                 ::hist-count-classified-subplot]
   ::pco/output [::hist-count-subplot]}
  {::hist-count-subplot {::hiccup (-> (svg/group {}
                                                 (::hiccup hist-count-all-subplot)
                                                 (::hiccup hist-count-classified-subplot))
                                      (quickthing/svg-wrap [width
                                                            height]
                                                           width))}})

;; Rain weighted histogram




(pco/defresolver $hist-rain-all-subplot
  [{::keys [width
            height
            scale
            margin-frac
            ;;
            d18O-range-min
            d18O-range-max
            d18O-rain]}]
  {::pco/input  [::width
                 ::height
                 ::scale
                 ::d18O-range-min
                 ::d18O-range-max
                 {::d18O-rain [{:hist [:xy-nonil|
                                       {:y [:max]}]}
                               {:x [:max]}]}]
   ::pco/output [{::hist-rain-all-subplot [::hiccup]}]}
  {::hist-rain-all-subplot {::hiccup (-> (quickthing/primary-axis [[d18O-range-min
                                                                    0.0]
                                                                   [d18O-range-max
                                                                    (-> d18O-rain
                                                                        :hist
                                                                        :y
                                                                        :max)]]
                                                                  {:width       width
                                                                   :height      height
                                                                   :title       ""
                                                                   :legend      [["ALL RAINY DAYS"
                                                                                  {:fill   "lightgrey"
                                                                                   :stroke "lightgrey"}]
                                                                                 #_#_
                                                                                 ["SUMMER MONSOON"
                                                                                  {:fill   "#aa8800"
                                                                                   :stroke nil}]
                                                                                 ["WINTER MONSOON"
                                                                                  {:fill   "#00aa88"
                                                                                   :stroke nil}]]
                                                                   :x-name      "d18O"
                                                                   :y-name      "Rain (mm) per bin"
                                                                   :scale       scale
                                                                   :margin-frac margin-frac
                                                                   :color       "#0008"})
                                         (assoc :grid
                                                nil)
                                         (update :data
                                                 #(into %
                                                        (quickthing/bars (->> d18O-rain
                                                                              :hist
                                                                              :xy-nonil|)
                                                                         {:attribs {:stroke-width (/ width
                                                                                                     (* 3.0
                                                                                                        (- d18O-range-max
                                                                                                           d18O-range-min)))
                                                                                    :stroke       "lightgrey"}})))
                                         viz/svg-plot2d-cartesian
                                         (quickthing/svg-wrap [width
                                                               height]
                                                              width))}})




(pco/defresolver $hist-monsoon-classified-subplot
  [{::keys [width
            height
            scale
            margin-frac
            ;;
            d18O-range-min
            d18O-range-max
            d18O-monsoon-above
            d18O-monsoon-below]}]
  {::pco/input  [::width
                 ::height
                 ::scale
                 ::d18O-range-min
                 ::d18O-range-max
                 {::d18O-monsoon-above [{:hist [:xy-nonil|
                                                {:y [:max]}]}
                                        {:x [:max]}]}
                 {::d18O-monsoon-below [{:hist [:xy-nonil|
                                                {:y [:max]}]}
                                        {:x [:max]}]}]
   ::pco/output [{::hist-monsoon-classified-subplot [::hiccup]}]}
  {::hist-monsoon-classified-subplot {::hiccup (-> (quickthing/primary-axis [[d18O-range-min
                                                                              0.0]
                                                                             [d18O-range-max
                                                                              (max (-> d18O-monsoon-above
                                                                                       :hist
                                                                                       :y
                                                                                       :max)
                                                                                   (-> d18O-monsoon-below
                                                                                       :hist
                                                                                       :y
                                                                                       :max))]]
                                                                            {:width       width
                                                                             :height      height
                                                                             :title       ""
                                                                             :legend      [["" #_"ALL RAINY DAYS"
                                                                                            {:fill   "lightgrey"
                                                                                             :stroke "lightgrey"}]
                                                                                           ["SUMMER MONSOON"
                                                                                            {:fill   summer-color
                                                                                             :stroke nil}]
                                                                                           ["WINTER MONSOON"
                                                                                            {:fill   winter-color
                                                                                             :stroke nil}]]
                                                                             :x-name      "d18O"
                                                                             :y-name      "Monsoon per bin"
                                                                             :scale       scale
                                                                             :margin-frac margin-frac
                                                                             :color       "#0008"})
                                                   (assoc :grid
                                                          nil)
                                                   (update :data
                                                           #(into %
                                                                  (quickthing/bars (->> d18O-monsoon-above
                                                                                        :hist
                                                                                        :xy-nonil|
                                                                                        (mapv (fn [[x-coord
                                                                                                    y-coord]]
                                                                                                [(- x-coord
                                                                                                    0.125)
                                                                                                 y-coord])))
                                                                                   {:attribs {:stroke-width (/ width
                                                                                                               (* 6.0
                                                                                                                  (- d18O-range-max
                                                                                                                     d18O-range-min)))
                                                                                              :stroke       summer-color}})))
                                                   (update :data
                                                           #(into %
                                                                  (quickthing/bars (->> d18O-monsoon-below
                                                                                        :hist
                                                                                        :xy-nonil|
                                                                                        (mapv (fn [[x-coord
                                                                                                    y-coord]]
                                                                                                [(+ x-coord
                                                                                                    0.125)
                                                                                                 y-coord])))
                                                                                   {:attribs {:stroke-width (/ width
                                                                                                               (* 6.0
                                                                                                                  (- d18O-range-max
                                                                                                                     d18O-range-min)))
                                                                                              :stroke       winter-color}})))
                                                   viz/svg-plot2d-cartesian
                                                   (quickthing/svg-wrap [width
                                                                         height]
                                                                        width))}})


(pco/defresolver $monthly-tables
  [{::keys [table]}]
  {::pco/output [::monthly-tables]}
  {::monthly-tables (ds/group-by table
                                 :Month)})




(pco/defresolver $monthly-averages
  "Amount weighted averages"
  [{::keys [monthly-tables]}]
  {::pco/output [::monthly-averages]}
  {::monthly-averages (into (sorted-map-by #(compare (.getValue %1)
                                                     (.getValue %2)))
                            (update-vals monthly-tables
                                         (fn calculate-table-avergage
                                           [table-for-a-month]
                                           (let [valid-d18O-rain-pairs (filterv (fn [[d18O-val
                                                                                      Rain-val]]
                                                                                  (and (some? d18O-val)
                                                                                       (some? Rain-val)))
                                                                                (mapv vector
                                                                                      (-> table-for-a-month
                                                                                          :d18O
                                                                                          vec)
                                                                                      (-> table-for-a-month
                                                                                          :Rain-mm
                                                                                          vec)))]
                                             (/ (->> valid-d18O-rain-pairs
                                                     (mapv (fn [[d18O-val
                                                                 Rain-val]]
                                                             (* d18O-val
                                                                Rain-val)))
                                                     (apply +))
                                                (->> valid-d18O-rain-pairs
                                                     (mapv second)
                                                     (apply +)))))))})



(pco/defresolver $monthly-averages-subplot
  "Amount weighted averages"
  [{::keys [width
            height
            scale
            margin-frac
            monthly-averages]}]
  {::pco/output [{::monthly-averages-subplot [::hiccup]}]}
  {::monthly-averages-subplot {::hiccup (let [month-labels   (->> monthly-averages
                                                                  keys
                                                                  (mapv #(-> %
                                                                             str
                                                                             (subs 0
                                                                                   3)))
                                                                  )
                                              monthly-values (->> monthly-averages
                                                                  vals
                                                                  vec)]
                                          (-> (quickthing/primary-axis [[0.0
                                                                         0.0]
                                                                        [12.0
                                                                         -10.0]]
                                                                       {:width       width
                                                                        :height      height
                                                                        :title       "Monthly Avrg"
                                                                        #_#_
                                                                        :legend      [["" #_"ALL RAINY DAYS"
                                                                                       {:fill   "lightgrey"
                                                                                        :stroke "lightgrey"}]
                                                                                      ["SUMMER MONSOON"
                                                                                       {:fill   summer-color
                                                                                        :stroke nil}]
                                                                                      ["WINTER MONSOON"
                                                                                       {:fill   winter-color
                                                                                        :stroke nil}]]
                                                                        :x-name      ""
                                                                        :y-name      "d18O"
                                                                        :scale       scale
                                                                        :margin-frac margin-frac
                                                                        #_#_
                                                                        :color       "#0008"})
                                              (assoc :grid
                                                     nil)
                                              (assoc-in [:y-axis
                                                         :minor]
                                                        (range -1
                                                               -10
                                                               -2))
                                              (assoc-in [:y-axis
                                                         :major]
                                                        #_
                                                        [0
                                                         (apply min
                                                                monthly-values)
                                                         (apply max
                                                                monthly-values)
                                                         10]
                                                        (range 0
                                                               -11
                                                               -2))
                                              (assoc-in [:x-axis
                                                         :major]
                                                        (range 0.5
                                                               12
                                                               1.0))
                                              (assoc-in [:x-axis
                                                         :label-style]
                                                        {:stroke      "none"
                                                         :font-family "Arial, sans-serif"
                                                         :font-size   (/ scale
                                                                         2)
                                                         :text-anchor "middle"})
                                              (assoc-in [:x-axis
                                                         :label]
                                                        (viz/default-svg-label (fn [x-val]
                                                                                 (get month-labels
                                                                                      (-> x-val
                                                                                          (- 0.5)
                                                                                          int)))))
                                              (update :data
                                                      #(into %
                                                             (quickthing/circles (mapv vector
                                                                                       (range 0.5
                                                                                              12
                                                                                              1.0)
                                                                                       monthly-values)
                                                                                 {:scale   scale
                                                                                  :attribs {:fill "blue"}})))
                                              ;;#_#_
                                              viz/svg-plot2d-cartesian
                                              (quickthing/svg-wrap [width
                                                                    height]
                                                                   width)))}})

(pco/defresolver $rain-d18O
  "Amount weighted averages"
  [{::keys [table]}]
  {::pco/output [::rain-d18O]}
  {::rain-d18O (filterv  (fn [[Rain-val
                               d18O-val]]
                           (and (some? d18O-val)
                                (some? Rain-val)))
                         (mapv vector
                               (-> table
                                   :Rain-mm
                                   vec)
                               (-> table
                                   :d18O
                                   vec)))})

(defn- least-squares
  "Line is:
  y = mx + b
  Returns `:m` and `:b`"
  [points]
  (let [n         (count points)
        sum-x     (reduce + (map first points))
        sum-y     (reduce + (map second points))
        sum-xy    (reduce + (map (fn [[x y]] (* x y)) points))
        sum-xx    (reduce + (map (fn [[x y]] (* x x)) points))
        slope     (/ (- (* n sum-xy) (* sum-x sum-y))
                     (- (* n sum-xx) (* sum-x sum-x)))
        intercept (/ (- sum-y (* slope sum-x)) n)]
    {:m slope :b intercept}))

(pco/defresolver $rain-d18O-line
  "Amount weighted averages"
  [{::keys [rain-d18O]}]
  {::pco/output [::rain-d18O-line]}
  {::rain-d18O-line (let [x-min       0.0
                          x-max       (->> rain-d18O
                                           (mapv first)
                                           (apply max))
                          {:keys [m
                                  b]} (least-squares rain-d18O)]
                      [[x-min
                        (+ (* m
                              x-min)
                           b)]
                       [x-max
                        (+ (* m
                              x-max)
                           b)]])})
#_
(pco/defresolver $amount-effect-subplot
  "Amount weighted averages"
  [{::keys [width
            height
            scale
            margin-frac
            rain-d18O
            rain-d18O-line]}]
  {::pco/output [{::amount-effect-subplot [::hiccup]}]}
  {::amount-effect-subplot {::hiccup (-> (quickthing/primary-axis rain-d18O
                                                                  {:width       width
                                                                   :height      height
                                                                   :title       "Amount Effect"
                                                                   #_#_
                                                                   :legend      [["" #_"ALL RAINY DAYS"
                                                                                  {:fill   "lightgrey"
                                                                                   :stroke "lightgrey"}]
                                                                                 ["SUMMER MONSOON"
                                                                                  {:fill   summer-color
                                                                                   :stroke nil}]
                                                                                 ["WINTER MONSOON"
                                                                                  {:fill   winter-color
                                                                                   :stroke nil}]]
                                                                   :x-name      "Rain per day (mm)"
                                                                   :y-name      "d18O"
                                                                   :scale       scale
                                                                   :margin-frac margin-frac
                                                                   #_#_
                                                                   :color       "#0008"})
                                         (assoc :grid
                                                {:major-y true
                                                 :major-x false})
                                         (assoc-in [:y-axis
                                                    :minor]
                                                   (range -20
                                                          10
                                                          1))
                                         (assoc-in [:y-axis
                                                    :major]
                                                   [10,0,-4,-6,-10,-20]
                                                   #_
                                                   (range 0
                                                          -11
                                                          -2))
                                         (assoc-in [:y-axis
                                                    :label-dist]
                                                   (/ scale
                                                      2.0))
                                         (assoc-in [:y-axis
                                                    :label-y]
                                                   (/ scale
                                                      6.0))
                                         (assoc-in [:y-axis
                                                    :label-style]
                                                   {:fill        "black"
                                                    :stroke      "none"
                                                    :font-family "Arial, sans-serif"
                                                    :font-size   (/ scale
                                                                    2.0)
                                                    :text-anchor "end"})
                                         (update :data
                                                 #(into %
                                                        (quickthing/circles rain-d18O
                                                                            {:scale   (/ scale
                                                                                         10)
                                                                             :attribs {:fill "blue"}})))
                                         (update :data
                                                 #(into %
                                                        (quickthing/solid-line rain-d18O-line
                                                                               {:scale   (/ scale
                                                                                            3)
                                                                                :attribs {:stroke       "red"
                                                                                          :stroke-width (/ scale
                                                                                                           10)}})))
                                         ;;#_#_
                                         viz/svg-plot2d-cartesian
                                         (quickthing/svg-wrap [width
                                                               height]
                                                              width))}})

(pco/defresolver $d18O-dD
  "Amount weighted averages"
  [{::keys [table]}]
  {::pco/output [::d18O-dD]}
  {::d18O-dD (filterv  (fn [[d18O-val
                             dD-val]]
                         (and (some? d18O-val)
                              (some? dD-val)))
                       (mapv (fn [a
                                  b
                                  c
                                  d]
                               [a
                                b
                                (if c
                                  {:fill   summer-color
                                   :radius (if d
                                             (/ d
                                                4.0))}
                                  {:fill   winter-color
                                   :radius (if d
                                             (/ d
                                                4.0))})])
                             (-> table
                                 :d18O
                                 vec)
                             (-> table
                                 :dD
                                 vec)
                             (-> table
                                 :Above?
                                 vec)
                             (-> table
                                 :Rain-mm
                                 vec)))})


(type (tick/between (tick/date "1951-01-01")
                    (tick/date "2021-12-31")
                    :days))

(pco/defresolver $nakhon-rain-layer
  ""
  [{::keys [width
            height
            scale
            margin-frac
            nakhon-gauge]}]
  {::pco/input  [::width
                 ::height
                 ::scale
                 ::margin-frac
                 {::nakhon-gauge [{::ghcnd/daily-rain [:xy-nonil|
                                                       :y]}]}]
   ::pco/output [{::nakhon-rain-layer [::hiccup]}]}
  {::nakhon-rain-layer {::hiccup (-> (quickthing/primary-axis [[0.0
                                                                0.0]
                                                               [(tick/between (tick/date "1951-01-01") ;;UGLY!
                                                                              (tick/date "2005-01-01")
                                                                              :days)
                                                                (apply max
                                                                       (filterv some?
                                                                                (:data| (-> nakhon-gauge
                                                                                            ::ghcnd/daily-rain
                                                                                            :y))))]]
                                                              {:width       width
                                                               :height      height
                                                               :x-name      "Year"
                                                               :y-name      "Rain (mm)"
                                                               :scale       scale
                                                               :margin-frac margin-frac
                                                               #_#_
                                                               :color       "#0008"})
                                     (assoc-in [:x-axis
                                                :visible]
                                               false)
                                     (assoc-in [:y-axis
                                                :visible]
                                               true)
                                     (assoc-in [:x-axis
                                                :major]
                                               [])
                                     (update :data
                                             #(into %
                                                    (quickthing/bars (-> nakhon-gauge
                                                                         ::ghcnd/daily-rain
                                                                         :xy-nonil|)
                                                                     {:attribs {:stroke       "black"
                                                                                :stroke-width (/ width
                                                                                                 (count (-> nakhon-gauge
                                                                                                            ::ghcnd/daily-rain
                                                                                                            :y
                                                                                                            :data|)))}})))
                                     #_
                                     (update :data
                                             #(into %
                                                    (quickthing/circles (-> nakhon-gauge
                                                                            ::ghcnd/daily-rain
                                                                            :xy-nonil|)
                                                                        {:stroke-width 0.0 #_ "none"
                                                                         :scale        (/ scale
                                                                                          10)}
                                                                        #_
                                                                        {:attribs {:stroke       "black"
                                                                                   :stroke-width (/ width
                                                                                                    (count (-> nakhon-gauge
                                                                                                               ::ghcnd/daily-rain
                                                                                                               :y
                                                                                                               :data|)))}})))
                                     viz/svg-plot2d-cartesian
                                     (quickthing/svg-wrap [width
                                                           height]
                                                          width))}})


(pco/defresolver $nakhon-big-rain-fraction-layer
  ""
  [{::keys [width
            height
            scale
            margin-frac
            nakhon-gauge]}]
  {::pco/input  [::width
                 ::height
                 ::scale
                 ::margin-frac
                 {::nakhon-gauge [{::ghcnd/annual-storm-fraction [:xy-nonil|]}]}]
   ::pco/output [{::nakhon-big-rain-fraction-layer [::hiccup]}]}
  (let [fraction-xy (-> nakhon-gauge
                        ::ghcnd/annual-storm-fraction
                        :xy-nonil|)]
    (println (str "IN: "
                  "$nakhon-big-rain-fraction-layer "
                  "Fractions: "
                  fraction-xy))
    {::nakhon-big-rain-fraction-layer
     {::hiccup (-> (quickthing/primary-axis [[1951
                                              0]
                                             [2005
                                              1.0]]
                                            {:width       width
                                             :height      height
                                             :x-name      "Year"
                                             :y-name      "Rain (mm)"
                                             :scale       scale
                                             :margin-frac margin-frac
                                             #_#_
                                             :color       "#0008"})
                   (assoc-in [:y-axis
                              :visible]
                             false)
                   (assoc-in [:y-axis
                              :major]
                             [])
                   (assoc-in [:x-axis
                              :minor] 
                             (range 1951
                                    2005))
                   ;;#_
                   (assoc :grid
                          nil)
                   (update :data
                           #(into %
                                  (quickthing/bars (mapv (fn [year]
                                                           [year
                                                            1.0])
                                                         (range 1951.5
                                                                2005.5))
                                                   {:attribs {:stroke       "whitesmoke"
                                                              :stroke-width (/ (* width
                                                                                  1.0
                                                                                  (- 1.0
                                                                                     (* 2.2
                                                                                        margin-frac)))
                                                                               (- 2005
                                                                                  1951))}})))
                   (update :data
                           #(into %
                                  (quickthing/bars (->> fraction-xy
                                                        (mapv (fn [[year
                                                                    amount]]
                                                                [(+ year
                                                                    0.5)
                                                                 amount])))
                                                   {:attribs {:stroke       "lightgrey"
                                                              :stroke-width (/ (* width
                                                                                  1.0
                                                                                  (- 1.0
                                                                                     (* 2.1
                                                                                        margin-frac)))
                                                                               (- 2005
                                                                                  1951))}})))
                   viz/svg-plot2d-cartesian
                   (quickthing/svg-wrap [width
                                         height]
                                        width))}}))


(pco/defresolver $nakhon-big-rain-count-layer
  ""
  [{::keys [width
            height
            scale
            margin-frac
            nakhon-gauge]}]
  {::pco/input  [::width
                 ::height
                 ::scale
                 ::margin-frac
                 {::nakhon-gauge [{::ghcnd/winter-storm-count [:xy-nonil|]}]}]
   ::pco/output [{::nakhon-big-rain-count-layer [::hiccup]}]}
  (let [count-xy (-> nakhon-gauge
                     ::ghcnd/winter-storm-count
                     :xy-nonil|)]
    {::nakhon-big-rain-count-layer
     {::hiccup (-> (quickthing/primary-axis [[1951.0
                                              0]
                                             [2005.0
                                              20]]
                                            {:width       width
                                             :height      height
                                             :x-name      "Year"
                                             :y-name      "Rain (mm)"
                                             :scale       scale
                                             :margin-frac margin-frac
                                             #_#_
                                             :color       "#0008"})
                   (assoc-in [:y-axis
                              :visible]
                             false)
                   (assoc-in [:y-axis
                              :major]
                             [])
                   (assoc-in [:x-axis
                              :minor] 
                             (range 1951
                                    2005))
                   ;;#_
                   (assoc :grid
                          nil)
                   (update :data
                           #(into %
                                  (quickthing/bars (->> count-xy
                                                        (mapv (fn [[year
                                                                    amount]]
                                                                [(+ year
                                                                    0.5)
                                                                 amount])))
                                                   {:attribs {:stroke       "lightgrey"
                                                              :stroke-width (/ (* width
                                                                                  (- 1.0
                                                                                     margin-frac))
                                                                               (- 2005
                                                                                  1951))}})))
                   viz/svg-plot2d-cartesian
                   (quickthing/svg-wrap [width
                                         height]
                                        width))}}))

(-> quickthing/rainbow
    rest
    butlast
    vec
    (get (Math/round (* 254
                        (mod 190.3
                             1.0)))))

(pco/defresolver $klang-d18O-layer
  "Amount weighted averages"
  [{::keys [width
            height
            scale
            margin-frac
            klang-year-d18O]}]
  {::pco/output [{::klang-d18O-layer [::hiccup]}]}
  {::klang-d18O-layer
   {::hiccup
    (-> (quickthing/secondary-axis [[1951
                                     -6.0]
                                    [2005
                                     -4.0]]
                                   {:width       width
                                    :height      height
                                    :x-name      "Year"
                                    :y-name      "d18O"
                                    :scale       scale
                                    :margin-frac margin-frac
                                    #_#_
                                    :color       "#0008"})
        #_#_#_
        (assoc :grid
               {:minor-x true
                :minor-y false
                :major-y false})
        (assoc-in [:x-axis
                   :visible]
                  false)
        (assoc-in [:x-axis
                   :major]
                  (range 1951
                         2005))
        (update :data
                #(into %
                       (quickthing/dashed-line klang-year-d18O
                                               {:scale   (/ scale
                                                            10)
                                                :attribs {:stroke "blue"}})))
        (update :data
                #(into %
                       (quickthing/circles (->> klang-year-d18O
                                                (mapv (fn [[x
                                                            y
                                                            meta]]
                                                        [x
                                                         y
                                                         {:radius (/ scale
                                                                     10)
                                                          :fill
                                                          ;;#_
                                                          "blue"
                                                          #_
                                                          (-> quickthing/rainbow
                                                              rest
                                                              butlast
                                                              vec
                                                              (get (Math/round (* 254
                                                                                  (mod x
                                                                                       1.0)))))}])))
                                           {:scale (/ scale
                                                      10)})))
        viz/svg-plot2d-cartesian
        (quickthing/svg-wrap [width
                              height]
                             width))}})

(pco/defresolver $phuket-nakhon-annual-rain-layer
  ""
  [{::keys [width
            height
            scale
            margin-frac
            nakhon-gauge
            phuket-gauge
            #_
            big-rain-fraction]}]
  {::pco/input  [::width
                 ::height
                 ::scale
                 ::margin-frac
                 {::nakhon-gauge [{::ghcnd/annual-rain [:xy|]}
                                  {::ghcnd/winter-storm-rain [:xy|]}]}
                 {::phuket-gauge [{::ghcnd/annual-rain [:xy|]}]}]
   ::pco/output [{::phuket-nakhon-annual-rain-layer [::hiccup]}]}
  (let [phuket-rain-xy (-> phuket-gauge
                           ::ghcnd/annual-rain
                           :xy|)
        nakhon-rain-xy (-> nakhon-gauge
                           ::ghcnd/winter-storm-rain
                           :xy|)]
    {::phuket-nakhon-annual-rain-layer
     {::hiccup (-> (quickthing/primary-axis (into nakhon-rain-xy
                                                  phuket-rain-xy)
                                            {:width            width
                                             :height           height
                                             :x-name           "Year"
                                             :y-name           ""
                                             :y-breathing-room 1.0
                                             :scale            scale
                                             :margin-frac      margin-frac
                                             :legend           [["Phuket Rain"
                                                                 {:fill   "green"
                                                                  :stroke nil}]
                                                                ["Nakhon Rain"
                                                                 {:fill   "red"
                                                                  :stroke nil}]
                                                                #_
                                                                ["WINTER MONSOON"
                                                                 {:fill   winter-color
                                                                  :stroke nil}]]
                                             #_#_
                                             :color            "#0008"})
                   (assoc-in [:y-axis
                              :visible]
                             true)
                   #_
                   (assoc-in [:y-axis
                              :major]
                             [])
                   (assoc-in [:x-axis
                              :minor] 
                             (range 1951
                                    2005))
                   ;;#_
                   (assoc :grid
                          nil)
                   ;; Nakhon
                   (update :data
                           #(into %
                                  (quickthing/dashed-line nakhon-rain-xy
                                                          {:scale   (/ scale
                                                                       10)
                                                           :attribs {:stroke "red"}})))
                   (update :data
                           #(into %
                                  (quickthing/circles (->> nakhon-rain-xy
                                                           (mapv (fn [[x
                                                                       y
                                                                       meta]]
                                                                   [x
                                                                    y
                                                                    {:radius (/ scale
                                                                                10)
                                                                     :fill   "red"}])))
                                                      {:scale (/ scale
                                                                 10)})))
                   ;; Phuket
                   (update :data
                           #(into %
                                  (quickthing/dashed-line phuket-rain-xy
                                                          {:scale   (/ scale
                                                                       10)
                                                           :attribs {:stroke "green"}})))
                   (update :data
                           #(into %
                                  (quickthing/circles (->> phuket-rain-xy
                                                           (mapv (fn [[x
                                                                       y
                                                                       meta]]
                                                                   [x
                                                                    y
                                                                    {:radius (/ scale
                                                                                10)
                                                                     :fill   "green"
                                                                     #_      (-> quickthing/rainbow
                                                                                 rest
                                                                                 butlast
                                                                                 vec
                                                                                 (get (Math/round (* 254
                                                                                                     (mod x
                                                                                                          1.0)))))}])))
                                                      {:scale (/ scale
                                                                 10)})))
                   viz/svg-plot2d-cartesian
                   (quickthing/svg-wrap [width
                                         height]
                                        width))}}))


(pco/defresolver $phuket-nakhon-annual-fraction-layer
  ""
  [{::keys [width
            height
            scale
            margin-frac
            nakhon-gauge
            phuket-gauge
            #_
            big-rain-fraction]}]
  {::pco/input  [::width
                 ::height
                 ::scale
                 ::margin-frac
                 {::nakhon-gauge [{::ghcnd/annual-rain [:xy|]}
                                  {::ghcnd/winter-storm-rain [:xy|]}]}
                 {::phuket-gauge [{::ghcnd/annual-rain [:xy|]}]}
                 #_#_#_#_
                 {::nakhon-winter-rain-totals [:xy-nonil|]}
                 {::nakhon-annual-rain-totals [:xy-nonil|]}
                 {::phuket-annual-rain-totals [:xy-nonil|]}
                 {::big-rain-fraction [:xy-nonil|]}]
   ::pco/output [{::phuket-nakhon-annual-fraction-layer [::hiccup]}]}
  (let [phuket-rain-xy  (-> phuket-gauge
                            ::ghcnd/annual-rain
                            :xy|)
        nakhon-rain-xy  (-> nakhon-gauge
                            ::ghcnd/winter-storm-rain
                            :xy|)
        winter-fraction (mapv (fn [[year
                                    phuket-rain]
                                   [_
                                    nakhon-rain]]
                                [year
                                 (+ 0.0
                                    (/ nakhon-rain
                                       (+ phuket-rain
                                          nakhon-rain)))])
                              phuket-rain-xy
                              nakhon-rain-xy)]
    {::phuket-nakhon-annual-fraction-layer
     {::hiccup (-> (quickthing/primary-axis (into [[1951 0.0]]
                                                  winter-fraction)
                                            {:width            width
                                             :height           height
                                             :x-name           "Year"
                                             :y-name           "Non-Storm Fraction (est)"
                                             :y-breathing-room 0.5
                                             :scale            scale
                                             :margin-frac      margin-frac
                                             #_#_
                                             :color            "#0008"})
                   (assoc-in [:y-axis
                              :visible]
                             false)
                   #_
                   (assoc-in [:y-axis
                              :major]
                             [])
                   (assoc-in [:x-axis
                              :minor] 
                             (range 1951
                                    2005))
                   ;;#_
                   (assoc :grid
                          nil)
                   ;; Winter Fraction Calc
                   (update :data
                           #(into %
                                  (quickthing/dashed-line winter-fraction
                                                          {:scale   (/ scale
                                                                       10)
                                                           :attribs {:stroke "black"}})))
                   (update :data
                           #(into %
                                  (quickthing/circles (->> winter-fraction
                                                           (mapv (fn [[x
                                                                       y
                                                                       meta]]
                                                                   [x
                                                                    y
                                                                    {:radius (/ scale
                                                                                10)
                                                                     :fill   "black"}])))
                                                      {:scale (/ scale
                                                                 10)})))
                   viz/svg-plot2d-cartesian
                   (quickthing/svg-wrap [width
                                         height]
                                        width))}}))



(pco/defresolver $klang-vs-nakhon-subplot
  "Amount weighted averages"
  [{::keys [width
            height
            scale
            margin-frac
            nakhon-rain-layer
            nakhon-big-rain-count-layer
            nakhon-big-rain-fraction-layer
            klang-d18O-layer]}]
  {::pco/output [{::klang-vs-nakhon-subplot [::hiccup]}]}
  {::klang-vs-nakhon-subplot {::hiccup (-> (svg/group {}
                                                      #_(::hiccup nakhon-big-rain-fraction-layer)
                                                      #_(::hiccup nakhon-big-rain-count-layer)
                                                      (::hiccup nakhon-rain-layer)
                                                      (::hiccup klang-d18O-layer))
                                           (quickthing/svg-wrap [width
                                                                 height]
                                                                width))}})



(pco/defresolver $klang-vs-nakhon-bigrain-fraction-subplot
  "Amount weighted averages"
  [{::keys [width
            height
            scale
            margin-frac
            nakhon-rain-layer
            nakhon-big-rain-fraction-layer
            #_
            nakhon-big-rain-count-layer
            klang-d18O-layer]}]
  {::pco/output [{::klang-vs-nakhon-bigrain-fraction-subplot [::hiccup]}]}
  {::klang-vs-nakhon-bigrain-fraction-subplot {::hiccup (-> (svg/group {}
                                                                       (::hiccup nakhon-big-rain-fraction-layer)
                                                                       #_(::hiccup nakhon-big-rain-count-layer)
                                                                       (::hiccup nakhon-rain-layer)
                                                                       (::hiccup klang-d18O-layer))
                                                            (quickthing/svg-wrap [width
                                                                                  height]
                                                                                 width))}})


(pco/defresolver $klang-vs-nakhon-bigrain-count-subplot
  "Amount weighted averages"
  [{::keys [width
            height
            scale
            margin-frac
            nakhon-rain-layer
            #_
            nakhon-big-rain-fraction-layer
            nakhon-big-rain-count-layer
            klang-d18O-layer]}]
  {::pco/output [{::klang-vs-nakhon-bigrain-count-subplot [::hiccup]}]}
  {::klang-vs-nakhon-bigrain-count-subplot {::hiccup (-> (svg/group {}
                                                                    #_(::hiccup nakhon-big-rain-fraction-layer)
                                                                    (::hiccup nakhon-big-rain-count-layer)
                                                                    (::hiccup nakhon-rain-layer)
                                                                    (::hiccup klang-d18O-layer))
                                                         (quickthing/svg-wrap [width
                                                                               height]
                                                                              width))}})


(pco/defresolver $klang-vs-phuket-nakhon-subplot
  "Amount weighted averages"
  [{::keys [width
            height
            scale
            margin-frac
            #_#_#_
            nakhon-rain-layer
            nakhon-big-rain-count-layer
            nakhon-big-rain-fraction-layer
            phuket-nakhon-annual-rain-layer
            phuket-nakhon-annual-fraction-layer
            klang-d18O-layer]}]
  {::pco/output [{::klang-vs-phuket-nakhon-subplot [::hiccup]}]}
  {::klang-vs-phuket-nakhon-subplot {::hiccup (-> (svg/group {}
                                                             #_(::hiccup nakhon-big-rain-fraction-layer)
                                                             #_(::hiccup nakhon-big-rain-count-layer)
                                                             #_(::hiccup nakhon-rain-layer)
                                                             #_q
                                                             (::hiccup phuket-nakhon-annual-rain-layer)
                                                             ;;#_
                                                             (::hiccup phuket-nakhon-annual-fraction-layer)
                                                             (::hiccup klang-d18O-layer))
                                                  (quickthing/svg-wrap [width
                                                                        height]
                                                                       width))}})
#_#_
(def plan-cache*
  (atom {}))

(def env
  (-> (pci/register {::p.a.eql/parallel? true}
                    [$days-min-max
                     $rain-min-max
                     $index-max
                     $grid-layer
                     $rain-axis
                     $rain-layer
                     $rain-subplot
                     $rain-d18O-subplot
                     $rain-d18O-average-subplot
                     $rain-d18O-classified-subplot
                     $rain-d18O-classified-average-subplot
                     $index-axis
                     $index-layer
                     $index-subplot
                     $isotope-d18O-classified-average-subplot
                     $nakhon-modern-rain-layer
                     $nakhon-d18O-classified-average-subplot
                     $d18O-below-select-layer
                     $index-d18O-subplot
                     $index-d18O-big-events-subplot
                     $d18O-axis
                     $d18O-layer
                     $d18O-classified-layer
                     $d18O-averages-layer
                     $hiccup2svg
                     $rain-d18O-index-2stack
                     $rain-d18O-classified-index-2stack
                     $hist-count-all-subplot
                     $hist-count-classified-subplot
                     $hist-count-subplot
                     $hist-rain-all-subplot
                     $hist-rain-classified-subplot
                     $hist-rain-subplot
                     $hist-monsoon-classified-subplot
                     $monthly-tables
                     $monthly-averages
                     $monthly-averages-subplot
                     $rain-d18O
                     $rain-d18O-line
                     $amount-effect-subplot
                     $d18O-dD
                     $meteoric-water-line-subplot
                     $nakhon-rain-layer
                     $nakhon-big-rain-count-layer
                     $nakhon-big-rain-fraction-layer
                     $phuket-nakhon-annual-fraction-layer
                     $phuket-nakhon-annual-rain-layer
                     $klang-d18O-layer
                     $klang-vs-nakhon-subplot
                     $klang-vs-nakhon-bigrain-fraction-subplot
                     $klang-vs-nakhon-bigrain-count-subplot
                     $klang-vs-phuket-nakhon-subplot])
      (pcp/with-plan-cache plan-cache*)
      kxygk.pathmore.cache/inject-for-all-resolvers))

