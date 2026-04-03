(ns kxygk.anemoi.plot
  (:require [clojure.math]
            [clojure.string]
            [kxygk.anemoi.stat :as stat]
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

(def summer-color "#aa8800")
(def winter-color "#00aa88")


(pco/defresolver $days-min-max
  [{::keys [rain-data]}]
  {::pco/input  [{::rain-data [:x]}] ;; day
   ::pco/output [::day-num-min
                 ::day-num-max]}
  (p/vthread {::day-num-min 0
              ::day-num-max (->> rain-data
                                 :x
                                 :data-vec
                                 (apply max))}))

(pco/defresolver $rain-min-max
  [{::keys [rain-data]}]
  {::pco/input  [{::rain-data [:y]}] ;; mm of rain
   ::pco/output [::rain-min
                 ::rain-max]}
  (p/vthread {::rain-min 0.0
              ::rain-max (->> rain-data
                              :y
                              :data-vec
                              (filterv some?)
                              (apply max))}))


(pco/defresolver $index-max
  [{::keys [index-above
            index-below]}]
  {::pco/input  [{::index-above [:y]}
                 {::index-below [:y]}] ;; day
   ::pco/output [::index-max]}
  (p/vthread {::index-max (max (->> index-above
                                    :y
                                    :data-vec
                                    (apply max))
                               (->> index-below
                                    :y
                                    :data-vec
                                    (apply max)))}))


#_
(pco/defresolver $rain-vec
  [{::keys [rain-data]}]
  {::pco/input [{::rain-data [:x ;; need to specify shape to convert
                              :y]}]
   ::pco/output [::rain-vec]}
  {::rain-vec (filterv (fn [[x-coord
                             y-coord]]
                         (and x-coord
                              y-coord))
                       (mapv vector
                             (-> rain-data
                                 :x
                                 :data-vec)
                             (-> rain-data
                                 :y
                                 :data-vec)))})

(pco/defresolver $rain-axis
  [{::keys [width
            height
            scale
            margin-frac
            ;;
            day-num-min ;; should be zero
            day-num-max
            rain-min
            rain-max]}]
  {::pco/output [::rain-axis]}
    (p/vthread {::rain-axis (-> (quickthing/primary-axis [[day-num-min
                                              rain-min]
                                             [day-num-max
                                              rain-max]]
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
                             true))}))

(pco/defresolver $d18O-axis
  [{::keys [width
            height
            scale
            margin-frac
            ;;
            d18O-rain
            day-num-min ;; should be zero
            day-num-max
            rain-min
            rain-max]}]
  {::pco/input [::width
                 ::height
                 ::scale
                 ::margin-frac
                 ;;
                 {::d18O-rain [{:x [::stat/max
                                    ::stat/min]}]}
                 ::day-num-min
                 ::day-num-max]
   ::pco/output [::d18O-axis]}
   (p/vthread  {::d18O-axis (-> (quickthing/secondary-axis [[day-num-min
                                                (-> d18O-rain
                                                    :x
                                                    ::stat/min
                                                    (* 1.2))]
                                               [day-num-max
                                                (-> d18O-rain
                                                    :x
                                                    ::stat/max)]]
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
                             true))}))

(pco/defresolver $index-axis
  [{::keys [width
            height
            scale
            margin-frac
            ;;
            day-num-min
            day-num-max
            index-max
            cycle-start-value
            cycle-length
            cycle-phase]}]
  {::pco/output [::index-axis]}
   (p/vthread  {::index-axis (-> (quickthing/primary-axis [[day-num-min
                                               0.0] ;; index min is always zero
                                              [day-num-max
                                               index-max]]
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
                              false))}))

(pco/defresolver $grid-layer
  [{::keys [width
            height
            scale
            margin-frac
            day-num-min
            day-num-max
            cycle-start-value
            cycle-length
            cycle-phase]}]
  {::pco/output [::grid-layer]}
  (p/vthread   {::grid-layer (-> (quickthing/primary-axis [[day-num-min
                                               0.0]
                                              [day-num-max
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
                              (thi.ng.geom.viz.core/default-svg-label #(+ cycle-start-value
                                                                          (/ %
                                                                             cycle-length))))
                    (assoc-in [:x-axis
                               :major]
                              (range cycle-phase ;; TODO: If plot doesn't start at day 0 FIX
                                     (- day-num-max
                                        day-num-min)
                                     cycle-length))
                    (assoc-in [:y-axis
                               :major]
                              [])
                    viz/svg-plot2d-cartesian)}))


(pco/defresolver $rain-layer
  [{::keys [rain-data
            width
            rain-axis
            day-num-min
            day-num-max]}]
  {::pco/input [{::rain-data [:xy-nonil]}
                ::width
                ::rain-axis
                ::day-num-min
                ::day-num-max]
   ::pco/output [::rain-layer]}
   (p/vthread   {::rain-layer (-> rain-axis
                    (update :data
                            #(into %
                                   (quickthing/bars (:xy-nonil rain-data)
                                                    {:attribs {:stroke-width (/ width
                                                                                (- day-num-min
                                                                                   day-num-max))
                                                               :stroke       "#000000"}})))
                    #_
                    (update :data
                            #(into %
                                   (quickthing/circles (->> missing-days-datavec
                                                            (mapv (fn [coord]
                                                                    coord)))
                                                       {:scale   6
                                                        :attribs {:fill "#f00"}})))
                    viz/svg-plot2d-cartesian)}))

(pco/defresolver $d18O-layer
  [{::keys [d18O-data
            width
            d18O-axis
            day-num-min
            day-num-max]}]
  {::pco/input [{::d18O-data [:xy-nonil]}
                ::width
                ::d18O-axis
                ::day-num-min
                ::day-num-max]
   ::pco/output [::d18O-layer]}
  (p/vthread   {::d18O-layer (-> d18O-axis
                    (update :data
                            #(into %
                                   (quickthing/circles (->> (:xy-nonil d18O-data)
                                                            (mapv (fn [point]
                                                                    (update point
                                                                            2
                                                                            (fn [attribs]
                                                                              (merge attribs
                                                                                     {:fill "#33ff"}))))))
                                                       {:scale   10
                                                        :attribs {:fill "#33ff"}})))
                    viz/svg-plot2d-cartesian)}))


(pco/defresolver $d18O-classified-layer ;; reuse resolver
  [{::keys [d18O-above-data
            d18O-below-data
            width
            d18O-axis]}]
  {::pco/input  [{::d18O-above-data [:xy-nonil]}
                 {::d18O-below-data [:xy-nonil]}
                 {::above?-data [:y]}
                 ::width
                 ::d18O-axis] ;;
   ::pco/output [::d18O-classified-layer]}
   (p/vthread  {::d18O-classified-layer (-> d18O-axis
                               (update :data
                                       #(into %
                                              (quickthing/circles (->> d18O-above-data
                                                                       :xy-nonil)
                                                                  {:scale   10
                                                                   :attribs {:fill summer-color}})))
                               (update :data
                                       #(into %
                                              (quickthing/circles (->> d18O-below-data
                                                                       :xy-nonil)
                                                                  {:scale   10
                                                                   :attribs {:fill winter-color}})))
                               viz/svg-plot2d-cartesian)}))

(pco/defresolver $d18O-averages-layer
  [{::keys [d18O-axis
            scale
            d18O-rain
            d18O-rain-above
            d18O-rain-below
            above?-data
            width
            day-num-min
            day-num-max]}]
  {::pco/input  [{::d18O-rain [{:x [::stat/standard-mean
                                    ::stat/max] }
                               ::stat/weighted-mean]}
                 {::d18O-rain-above [{:x [::stat/standard-mean
                                          ::stat/max] }
                                     ::stat/weighted-mean]}
                 {::d18O-rain-below [{:x [::stat/standard-mean
                                          ::stat/max] }
                                     ::stat/weighted-mean]}
                 ::above?-data
                 ::scale
                 ::width
                 ::d18O-axis
                 ::day-num-min
                 ::day-num-max]
   ::pco/output [::d18O-total-average-layer
                 ::d18O-above-average-layer
                 ::d18O-below-average-layer]}
   (p/vthread  (let [d18O-total-mean (-> d18O-rain
                            ::stat/weighted-mean
                            #_#_
                            :y
                            ::stat/standard-mean)
        d18O-above-mean (-> d18O-rain-above
                            ::stat/weighted-mean
                            #_#_
                            :x
                            ::stat/standard-mean)
        d18O-below-mean (-> d18O-rain-below
                            ::stat/weighted-mean
                            #_#_
                            :x
                            ::stat/standard-mean)
        ]
    (let [static-font-size (* 0.008
                              (- day-num-max
                                 day-num-min))
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
                                                                             {:attribs {:stroke-width 10.0
                                                                                        :stroke       "#33f5"}})))
                                      (update :data
                                              #(into %
                                                     (quickthing/labels [[(* 0.33 ;; x offset
                                                                             (- day-num-max
                                                                                day-num-min))
                                                                          (-> d18O-rain
                                                                              :x
                                                                              ::stat/max
                                                                              (* 1.1))
                                                                          {:text              (str "WEIGHTED AVERAGE d18O: "
                                                                                                   (format "%.2f" d18O-total-mean))
                                                                           :dy (- static-font-size)
                                                                           :fill              "#33f5"
                                                                           :text-anchor       "beginning"
                                                                           :font-size        static-font-size
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
                                                                             (- day-num-max
                                                                                day-num-min))
                                                                          (-> d18O-rain
                                                                              :x
                                                                              ::stat/max
                                                                              (* 1.1))
                                                                          {:text              (str "SUMMER MONSOON d18O: "
                                                                                                   (format "%.2f" d18O-above-mean))
                                                                           :dy static-font-size
                                                                           :fill              summer-color
                                                                           :text-anchor       "beginning"
                                                                           :font-size        static-font-size
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
                                                                             (- day-num-max
                                                                                day-num-min))
                                                                          (-> d18O-rain
                                                                              :x
                                                                              ::stat/max
                                                                              (* 1.1))
                                                                          {:text              (str "WINTER MONSOON d18O: "
                                                                                                   (format "%.2f" d18O-below-mean))
                                                                           :fill              winter-color
                                                                           :text-anchor       "beginning"
                                                                           :font-size         static-font-size
                                                                           #_#_
                                                                           :dominant-baseline "hanging"}]])))
                                      viz/svg-plot2d-cartesian)}))))

(pco/defresolver $index-layer
  [{::keys [index-above
            index-below
            width ;; needs default??
            index-axis
            day-num-min
            day-num-max]}]
  {::pco/input  [{::index-above [:xy-nonil]}
                 {::index-below [:xy-nonil]}
                 ::width ;; needs default??
                 ::index-axis
                 ::day-num-min
                 ::day-num-max]
   ::pco/output [::index-layer]}
   (p/vthread  {::index-layer (-> index-axis
                     (update :data
                             #(into %
                                    (quickthing/bars (:xy-nonil index-below)
                                                     {:attribs {:stroke-width (/ width
                                                                                 (- day-num-min
                                                                                    day-num-max))
                                                                :stroke       winter-color}})))
                     (update :data
                             #(into %
                                    (quickthing/bars (:xy-nonil index-above)
                                                     {:attribs {:stroke-width (/ width
                                                                                 (- day-num-min
                                                                                    day-num-max))
                                                                :stroke       summer-color}})))
                     viz/svg-plot2d-cartesian)}))

(pco/defresolver $hiccup2svg
  [{::keys [hiccup]}]
  {::pco/output [::svg]}
   (p/vthread  {::svg (-> hiccup
             quickthing/svg2xml)}))

(pco/defresolver $rain-subplot
  [{::keys [width
            height
            grid-layer
            d18O-layer
            d18O-classified-layer
            d18O-total-average-layer
            d18O-above-average-layer
            d18O-below-average-layer
            rain-layer]}]
  {::pco/output [{::rain-subplot [::hiccup]}]}
  (p/vthread   {::rain-subplot {::hiccup (-> (svg/group {}
                                           grid-layer
                                           #_#_#_#_
                                           d18O-classified-layer
                                           d18O-total-average-layer
                                           d18O-above-average-layer
                                           d18O-below-average-layer
                                           rain-layer)
                        (quickthing/svg-wrap [width
                                              height]
                                             width))}}))

(pco/defresolver $rain-d18O-subplot
  [{::keys [width
            height
            d18O-layer
            rain-subplot]}]
  {::pco/output [{::rain-d18O-subplot [::hiccup]}]}
    (p/vthread {::rain-d18O-subplot {::hiccup (-> (svg/group {}
                                                (::hiccup rain-subplot)
                                                d18O-layer)
                                     (quickthing/svg-wrap [width
                                                           height]
                                                          width))}}))


(pco/defresolver $rain-d18O-average-subplot
  [{::keys [width
            height
            rain-d18O-subplot
            d18O-total-average-layer]}]
  {::pco/output [{::rain-d18O-average-subplot [::hiccup]}]}
   (p/vthread  {::rain-d18O-average-subplot {::hiccup (-> (svg/group {}
                                                        (::hiccup rain-d18O-subplot)
                                                        d18O-total-average-layer)
                                             (quickthing/svg-wrap [width
                                                                   height]
                                                                  width))}}))

(pco/defresolver $rain-d18O-classified-subplot
  [{::keys [width
            height
            d18O-classified-layer
            rain-subplot]}]
  {::pco/output [{::rain-d18O-classified-subplot [::hiccup]}]}
   (p/vthread  {::rain-d18O-classified-subplot {::hiccup (-> (svg/group {}
                                                (::hiccup rain-subplot)
                                                d18O-classified-layer)
                                     (quickthing/svg-wrap [width
                                                           height]
                                                          width))}}))



(pco/defresolver $rain-d18O-classified-average-subplot
  [{::keys [width
            height
            rain-d18O-classified-subplot
            d18O-above-average-layer
            d18O-below-average-layer]}]
  {::pco/output [{::rain-d18O-classified-average-subplot [::hiccup]}]}
   (p/vthread  {::rain-d18O-classified-average-subplot {::hiccup (-> (svg/group {}
                                                                   (::hiccup rain-d18O-classified-subplot)
                                                                   d18O-above-average-layer
                                                                   d18O-below-average-layer)
                                                        (quickthing/svg-wrap [width
                                                                              height]
                                                                             width))}}))


(pco/defresolver $index-subplot
  [{::keys [width
            height
            grid-layer
            index-layer]}]
  {::pco/output [{::index-subplot [::hiccup]}]}
   (p/vthread  {::index-subplot {::hiccup (-> (svg/group {}
                                   grid-layer
                                   index-layer)
                        (quickthing/svg-wrap [width
                                              height]
                                             width))}}))

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
   (p/vthread  {::rain-d18O-classified-index-2stack {::hiccup (-> (quickthing/group-plots-grid [[(::hiccup rain-d18O-classified-subplot)]
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
                 {::d18O-rain [{:x [{::stat/hist [:xy-nonil
                                                  {:y [::stat/max]}]}
                                    ::stat/max]}]}]
   ::pco/output [{::hist-count-all-subplot [::hiccup]}]}
   (p/vthread  {::hist-count-all-subplot {::hiccup (-> (quickthing/primary-axis [[d18O-range-min
                                                                       0.0]
                                                                      [d18O-range-max
                                                                       (-> d18O-rain
                                                                           :x
                                                                           ::stat/hist
                                                                           :y
                                                                           ::stat/max)]]
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
                                                                                 ::stat/hist
                                                                                 :xy-nonil)
                                                                            {:attribs {:stroke-width (/ width
                                                                                                        (* 3.0
                                                                                                           (- d18O-range-max
                                                                                                              d18O-range-min)))
                                                                                       :stroke       "lightgrey"}})))
                                            viz/svg-plot2d-cartesian
                                            (quickthing/svg-wrap [width
                                                       height]
                                                      width))}}))

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
                 {::d18O-rain [{:x [{::stat/hist [:xy-nonil
                                                  {:y [::stat/max]}]}
                                    ::stat/max]}]}
                 {::d18O-rain-above [{:x [{::stat/hist [:xy-nonil]}
                                          ::stat/max]}]}
                 {::d18O-rain-below [{:x [{::stat/hist [:xy-nonil]}
                                          ::stat/max]}]}]
   ::pco/output [{::hist-count-classified-subplot [::hiccup]}]}
   (p/vthread  {::hist-count-classified-subplot {::hiccup (-> (quickthing/primary-axis [[d18O-range-min
                                                             0.0]
                                                            [d18O-range-max
                                                             (-> d18O-rain
                                                                 :x
                                                                 ::stat/hist
                                                                 :y
                                                                 ::stat/max)]]
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
                                                    :x-name "d18O"
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
                                                                            ::stat/hist
                                                                            :xy-nonil
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
                                                                            ::stat/hist
                                                                            :xy-nonil
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


(pco/defresolver $hist-count-subplot
  [{::keys [width
            height
            hist-count-all-subplot
            hist-count-classified-subplot]}]
  {::pco/input [::hist-count-all-subplot
                ::hist-count-classified-subplot]
   ::pco/output [::hist-count-subplot]}
   (p/vthread  {::hist-count-subplot {::hiccup (-> (svg/group {}
                                                 (::hiccup hist-count-all-subplot)
                                                 (::hiccup hist-count-classified-subplot))
                                      (quickthing/svg-wrap [width
                                                            height]
                                                           width))}}))

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
                 {::d18O-rain [{::stat/hist [:xy-nonil
                                             {:y [::stat/max]}]}
                               {:x [::stat/max]}]}]
   ::pco/output [{::hist-rain-all-subplot [::hiccup]}]}
   (p/vthread  {::hist-rain-all-subplot {::hiccup (-> (quickthing/primary-axis [[d18O-range-min
                                                                       0.0]
                                                                      [d18O-range-max
                                                                       (-> d18O-rain
                                                                           ::stat/hist
                                                                           :y
                                                                           ::stat/max)]]
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
                                                                                 ::stat/hist
                                                                                 :xy-nonil)
                                                                            {:attribs {:stroke-width (/ width
                                                                                                        (* 3.0
                                                                                                           (- d18O-range-max
                                                                                                              d18O-range-min)))
                                                                                       :stroke       "lightgrey"}})))
                                            viz/svg-plot2d-cartesian
                                            (quickthing/svg-wrap [width
                                                       height]
                                                      width))}}))

(pco/defresolver $hist-rain-classified-subplot
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
                 {::d18O-rain [{::stat/hist [:xy-nonil
                                             {:y [::stat/max]}]}
                               {:x [::stat/max]}]}
                 {::d18O-rain-above [{::stat/hist [:xy-nonil]}
                                     {:x [::stat/max]}]}
                 {::d18O-rain-below [{::stat/hist [:xy-nonil]}
                                     {:x [::stat/max]}]}]
   ::pco/output [{::hist-rain-classified-subplot [::hiccup]}]}
   (p/vthread  {::hist-rain-classified-subplot {::hiccup (-> (quickthing/primary-axis [[d18O-range-min
                                                             0.0]
                                                            [d18O-range-max
                                                             (-> d18O-rain
                                                                 ::stat/hist
                                                                 :y
                                                                 ::stat/max)]]
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
                                                            :x-name "d18O"
                                                            :y-name      "Rain (mm) per bin"
                                                    :scale       scale
                                                    :margin-frac margin-frac
                                                    :color       "#0008"})
                                                 (assoc :grid
                                                        nil)
                                                 (update :data
                                                         #(into %
                                                      (quickthing/bars (->> d18O-rain-above
                                                                            ::stat/hist
                                                                            :xy-nonil
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
                                                                            ::stat/hist
                                                                            :xy-nonil
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


(pco/defresolver $hist-rain-subplot
  [{::keys [width
            height
            hist-rain-all-subplot
            hist-rain-classified-subplot]}]
  {::pco/input [::hist-rain-all-subplot
                ::hist-rain-classified-subplot]
   ::pco/output [::hist-rain-subplot]}
   (p/vthread  {::hist-rain-subplot {::hiccup (-> (svg/group {}
                                                 (::hiccup hist-rain-all-subplot)
                                                 (::hiccup hist-rain-classified-subplot))
                                      (quickthing/svg-wrap [width
                                                            height]
                                                           width))}}))



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
                 {::d18O-monsoon-above [{::stat/hist [:xy-nonil
                                                      {:y [::stat/max]}]}
                                     {:x [::stat/max]}]}
                 {::d18O-monsoon-below [{::stat/hist [:xy-nonil
                                                      {:y [::stat/max]}]}
                                     {:x [::stat/max]}]}]
   ::pco/output [{::hist-monsoon-classified-subplot [::hiccup]}]}
   (p/vthread  {::hist-monsoon-classified-subplot {::hiccup (-> (quickthing/primary-axis [[d18O-range-min
                                                             0.0]
                                                            [d18O-range-max
                                                             (max (-> d18O-monsoon-above
                                                                 ::stat/hist
                                                                 :y
                                                                 ::stat/max)
                                                                  (-> d18O-monsoon-below
                                                                      ::stat/hist
                                                                      :y
                                                                      ::stat/max))]]
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
                                                            :x-name "d18O"
                                                            :y-name      "Monsoon per bin"
                                                    :scale       scale
                                                    :margin-frac margin-frac
                                                    :color       "#0008"})
                                                 (assoc :grid
                                                        nil)
                                                 (update :data
                                                         #(into %
                                                      (quickthing/bars (->> d18O-monsoon-above
                                                                            ::stat/hist
                                                                            :xy-nonil
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
                                                                            ::stat/hist
                                                                            :xy-nonil
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
                     $hist-monsoon-classified-subplot])
      (pcp/with-plan-cache plan-cache*)
      kxygk.pathmore.cache/inject-for-all-resolvers))


#_#_#_#_#_#_
    (spit "rain.svg"
            (-> (svg/group {}
                           grid-plot
                           rain-plot)
                (quickthing/svg-wrap [width
                                      height]
                                     width)
                quickthing/svg2xml))
      (spit "rain-d18O.svg"
            (-> (svg/group {}
                           grid-plot
                           rain-plot
                           d18O-bare-plot)
                (quickthing/svg-wrap [width
                                      height]
                                     width)
                quickthing/svg2xml))
      (spit "rain-d18O-classified.svg"
            (-> (svg/group {}
                           grid-plot
                           rain-plot
                           d18O-classified-plot)
                (quickthing/svg-wrap [width
                                      height]
                                     width)
                quickthing/svg2xml))
      (spit "rain-d18O-averages.svg"
            (-> (svg/group {}
                           grid-plot
                           rain-plot
                           d18O-classified-plot
                           averages-rain-weighted-plot)
                (quickthing/svg-wrap [width
                                      height]
                                     width)
                quickthing/svg2xml))
      (spit "monsoon-d18O.svg"
            (-> (svg/group {}
                           grid-plot
                           index-plot
                           d18O-classified-plot)
                (quickthing/svg-wrap [width
                                      height]
                                     width)
                quickthing/svg2xml))
      (spit "monsoon-d18O-averages.svg"
            (-> (svg/group {}
                           grid-plot
                           index-plot
                           d18O-classified-plot
                           averages-index-weighted-plot)
                (quickthing/svg-wrap [width
                                      height]
                                     width)
                quickthing/svg2xml))






#_{::grid (let [rain-axis (-> (quickthing/primary-axis (into [[0,0]]
                                                             rain-datavec)
                                                       {:width       width
                                                        :height      height
                                                        :x-name      "Years"
                                                        :y-name      "Rain (mm)"
                                                        :scale       scale
                                                        :margin-frac margin-frac})
                              (assoc-in [:x-axis
                                         :label]
                                        (thi.ng.geom.viz.core/default-svg-label #(+ cycle-start-value
                                                                                    (/ %
                                                                                       cycle-length))))
                              (assoc-in [:x-axis
                                         :major]
                                        (range cycle-phase
                                               data-span-days
                                               cycle-length)))]
            (let [grid-plot (-> rain-axis
                                (assoc-in [:y-axis
                                           :major]
                                          [])
                                (assoc-in [:y-axis
                                           :major]
                                          [])
                                (assoc-in [:x-axis
                                           :visible]
                                          false)
                                (assoc-in [:y-axis
                                           :visible]
                                          false)
                                viz/svg-plot2d-cartesian)]
              (spit "grid.svg"
                    (-> (svg/group {}
                                   grid-plot)
                        (quickthing/svg-wrap [width
                                              height]
                                             width)
                        quickthing/svg2xml))))}



#_
;; Original
(let [width                        1800
      height                       900
      scale                        100
      margin-frac                  0.1
      {::keys [rain-datavec
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
               climate-index-max]} smap]
  (let [scale      50
        bar-width  (/ width
                      data-span-days)
        rain-axis  (-> (quickthing/primary-axis (into [[0,0]]
                                                      rain-datavec)
                                                {:width       width
                                                 :height      height
                                                 :x-name      "Years"
                                                 :y-name      "Rain (mm)"
                                                 #_#_
                                                 :legend      [[(str "SUMMER d18O: "
                                                                     (->> above-stat
                                                                          :mean 
                                                                          (format (str "%.3g"))))
                                                                {:fill   "#aa8800"
                                                                 :stroke nil}]
                                                               [(str "WINTER d18O: "
                                                                     (->> below-stat
                                                                          :mean 
                                                                          (format (str "%.3g"))))
                                                                {:fill   "#00aa88"
                                                                 :stroke nil}]]
                                                 :scale       scale
                                                 :margin-frac margin-frac
                                                 #_#_
                                                 :title       "Rain Amount"})
                       (assoc-in [:x-axis
                                  :label]
                                 (thi.ng.geom.viz.core/default-svg-label #(+ cycle-start-value
                                                                             (/ %
                                                                                cycle-length))))
                       (assoc-in [:x-axis
                                  :major]
                                 (range cycle-phase
                                        data-span-days
                                        cycle-length)))
        d18O-axis  (-> (quickthing/secondary-axis (conj d18O-datavec
                                                        [0
                                                         (second (first d18O-datavec))
                                                         nil
                                                         {:fill "transparent"}]
                                                        [data-span-days
                                                         (second (first d18O-datavec))
                                                         nil
                                                         {:fill "transparent"}])
                                                  {:width       width
                                                   :height      height
                                                   :scale       scale
                                                   :y-name      "d18O"
                                                   :margin-frac margin-frac
                                                   :color       "#33ff"})
                       (assoc-in [:x-axis
                                  :visible]
                                 false)
                       (assoc-in [:x-axis
                                  :label]
                                 (thi.ng.geom.viz.core/default-svg-label #(+ cycle-start-value
                                                                             (/ %
                                                                                cycle-length))))
                       (assoc-in [:x-axis
                                  :major]
                                 (range cycle-phase
                                        data-span-days
                                        cycle-length)))
        index-axis (-> (quickthing/primary-axis [[0
                                                  0]
                                                 [data-span-days
                                                  climate-index-max]]
                                                {:width       width
                                                 :height      height
                                                 :scale       scale
                                                 :title       "Monsoon"
                                                 :legend      [["SUMMER MONSOON" #_"First Singular Vector"
                                                                {:fill   "#aa8800"
                                                                 :stroke nil}]
                                                               ["WINTER MONSOON" #_"Second Singular Vector"
                                                                {:fill   "#00aa88"
                                                                 :stroke nil}]]
                                                 :margin-frac margin-frac
                                                 :color       "#0008"})
                       (assoc-in [:y-axis
                                  :visible]
                                 false)
                       (assoc-in [:x-axis
                                  :label]
                                 (thi.ng.geom.viz.core/default-svg-label #(+ cycle-start-value
                                                                             (/ %
                                                                                cycle-length))))
                       (assoc-in [:y-axis
                                  :major]
                                 [])
                       (assoc-in [:x-axis
                                  :major]
                                 (range cycle-phase
                                        data-span-days
                                        cycle-length)))]
    (let [grid-plot                    (-> rain-axis
                                           (assoc-in [:y-axis
                                                      :major]
                                                     [])
                                           (assoc-in [:y-axis
                                                      :major]
                                                     [])
                                           (assoc-in [:x-axis
                                                      :visible]
                                                     false)
                                           (assoc-in [:y-axis
                                                      :visible]
                                                     false)
                                           viz/svg-plot2d-cartesian)
          rain-plot                    (-> rain-axis
                                           (assoc :grid
                                                  nil)
                                           (update :data
                                                   #(into %
                                                          (quickthing/bars rain-datavec
                                                                           {:attribs {:stroke-width (/ width
                                                                                                       data-span-days)
                                                                                      :stroke       "#000000"}})))
                                           (update :data
                                                   #(into %
                                                          (quickthing/circles (->> missing-days-datavec
                                                                                   (mapv (fn [coord]
                                                                                           coord)))
                                                                              {:scale   6
                                                                               :attribs {:fill "#f00"}})))
                                           viz/svg-plot2d-cartesian)
          index-plot                   (-> index-axis
                                           (update :data
                                                   #(into %
                                                          (quickthing/bars monsoon-winter-datavec
                                                                           {:attribs {:stroke-width bar-width
                                                                                      :stroke       "#00aa88"}})))
                                           (update :data
                                                   #(into %
                                                          (quickthing/bars monsoon-summer-datavec
                                                                           {:attribs {:stroke-width bar-width
                                                                                      :stroke       "#aa8800"}})))
                                           viz/svg-plot2d-cartesian)
          d18O-bare-plot               (-> d18O-axis
                                           (update :data
                                                   #(into %
                                                          (quickthing/circles (mapv (fn [point]
                                                                                      (update point
                                                                                              2
                                                                                              (fn [attribs]
                                                                                                (merge attribs
                                                                                                       {:fill "#33ff"}))))
                                                                                    d18O-datavec)
                                                                              {:scale   10
                                                                               :attribs {:fill "#33ff"}})))
                                           viz/svg-plot2d-cartesian)
          d18O-classified-plot         (-> d18O-axis
                                           (update :data
                                                   #(into %
                                                          (quickthing/circles d18O-datavec
                                                                              {:scale   10
                                                                               :attribs {:fill "#33ff"}})))
                                           viz/svg-plot2d-cartesian)
          averages-rain-weighted-plot  (-> d18O-axis
                                           ;;#_#_
                                           (update :data
                                                   #(into %
                                                          (quickthing/labels [[800
                                                                               (- (:max stat-rain-weighted-d18O)
                                                                                  0.0)
                                                                               {:text              (str "SUMMER d18O: "
                                                                                                        (->> stat-rain-weighted-d18O-above
                                                                                                             :mean
                                                                                                             (format (str "%.3g"))))
                                                                                :fill              "#aa8800"
                                                                                :font-size         (* 0.8
                                                                                                      scale)
                                                                                :text-anchor       "beginning"
                                                                                :dominant-baseline "hanging"}]
                                                                              [800
                                                                               (- (:max stat-rain-weighted-d18O)
                                                                                  2.0)
                                                                               {:text              (str "WINTER d18O: "
                                                                                                        (->> stat-rain-weighted-d18O-below
                                                                                                             :mean
                                                                                                             (format (str "%.3g"))))
                                                                                :fill              "#00aa88"
                                                                                :font-size         (* 0.8
                                                                                                      scale)
                                                                                :text-anchor       "beginning"
                                                                                :dominant-baseline "hanging"}]])))
                                           (update :data
                                                   #(into %
                                                          (quickthing/dashed-line [[1.0
                                                                                    (:mean stat-rain-weighted-d18O-above)]
                                                                                   [(dec data-span-days)
                                                                                    (:mean stat-rain-weighted-d18O-above)]]
                                                                                  {:attribs {:stroke-width 10.0
                                                                                             :stroke       "#aa8800ff"}})))
                                           (update :data
                                                   #(into %
                                                          (quickthing/dashed-line [[1.0
                                                                                    (:mean stat-rain-weighted-d18O-below)]
                                                                                   [(dec data-span-days)
                                                                                    (:mean stat-rain-weighted-d18O-below)]]
                                                                                  {:attribs {:stroke-width 10.0
                                                                                             :stroke       "#00aa88ff"}})))
                                           viz/svg-plot2d-cartesian)
          averages-index-weighted-plot (-> d18O-axis
                                           ;;#_#_
                                           (update :data
                                                   #(into %
                                                          (quickthing/labels [[800
                                                                               (- (:max stat-rain-weighted-d18O)
                                                                                  0.0)
                                                                               {:text              (str "SUMMER d18O: "
                                                                                                        (->> stat-index-weighted-d18O-above
                                                                                                             :mean
                                                                                                             (format (str "%.3g"))))
                                                                                :fill              "#aa8800"
                                                                                :font-size         (* 0.8
                                                                                                      scale)
                                                                                :text-anchor       "beginning"
                                                                                :dominant-baseline "hanging"}]
                                                                              [800
                                                                               (- (:max stat-rain-weighted-d18O)
                                                                                  2.0)
                                                                               {:text              (str "WINTER d18O: "
                                                                                                        (->> stat-index-weighted-d18O-below
                                                                                                             :mean
                                                                                                             (format (str "%.3g"))))
                                                                                :fill              "#00aa88"
                                                                                :font-size         (* 0.8
                                                                                                      scale)
                                                                                :text-anchor       "beginning"
                                                                                :dominant-baseline "hanging"}]])))
                                           (update :data
                                                   #(into %
                                                          (quickthing/dashed-line [[1.0
                                                                                    (:mean stat-index-weighted-d18O-above)]
                                                                                   [(dec data-span-days)
                                                                                    (:mean stat-index-weighted-d18O-above)]]
                                                                                  {:attribs {:stroke-width 10.0
                                                                                             :stroke       "#aa8800ff"}})))
                                           (update :data
                                                   #(into %
                                                          (quickthing/dashed-line [[1.0
                                                                                    (:mean stat-index-weighted-d18O-below)]
                                                                                   [(dec data-span-days)
                                                                                    (:mean stat-index-weighted-d18O-below)]]
                                                                                  {:attribs {:stroke-width 10.0
                                                                                             :stroke       "#00aa88ff"}})))
                                           viz/svg-plot2d-cartesian)]
      (spit "rain.svg"
            (-> (svg/group {}
                           grid-plot
                           rain-plot)
                (quickthing/svg-wrap [width
                                      height]
                                     width)
                quickthing/svg2xml))
      (spit "rain-d18O.svg"
            (-> (svg/group {}
                           grid-plot
                           rain-plot
                           d18O-bare-plot)
                (quickthing/svg-wrap [width
                                      height]
                                     width)
                quickthing/svg2xml))
      (spit "rain-d18O-classified.svg"
            (-> (svg/group {}
                           grid-plot
                           rain-plot
                           d18O-classified-plot)
                (quickthing/svg-wrap [width
                                      height]
                                     width)
                quickthing/svg2xml))
      (spit "rain-d18O-averages.svg"
            (-> (svg/group {}
                           grid-plot
                           rain-plot
                           d18O-classified-plot
                           averages-rain-weighted-plot)
                (quickthing/svg-wrap [width
                                      height]
                                     width)
                quickthing/svg2xml))
      (spit "monsoon-d18O.svg"
            (-> (svg/group {}
                           grid-plot
                           index-plot
                           d18O-classified-plot)
                (quickthing/svg-wrap [width
                                      height]
                                     width)
                quickthing/svg2xml))
      (spit "monsoon-d18O-averages.svg"
            (-> (svg/group {}
                           grid-plot
                           index-plot
                           d18O-classified-plot
                           averages-index-weighted-plot)
                (quickthing/svg-wrap [width
                                      height]
                                     width)
                quickthing/svg2xml)))))




#_

(let [width                        1800
      height                       900
      scale                        40
      margin-frac                  0.1
      {::keys [rain-datavec
               monsoon-winter-datavec
               monsoon-summer-datavec
               missing-days-datavec
               d18O-datavec
               above-stat
               below-stat
               cycle-start-value
               cycle-length
               cycle-phase
               data-span-days
               climate-index-max]} smap]
  (let [bar-width  (/ width
                      data-span-days)
        rain-axis  (-> (quickthing/primary-axis (into [[0,0]]
                                                      rain-datavec)
                                                {:width       width
                                                 :height      (/ height
                                                                 2.0)
                                                 :y-name      "Rain (mm)"
                                                 ;;#_#_
                                                 :title       "Rain Amount"
                                                 #_#_
                                                 :legend      [[(str "SUMMER d18O: "
                                                                     (->> above-stat
                                                                          :mean 
                                                                          (format (str "%.3g"))))
                                                                {:fill   "#aa8800"
                                                                 :stroke nil}]
                                                               [(str "WINTER d18O: "
                                                                     (->> below-stat
                                                                          :mean 
                                                                          (format (str "%.3g"))))
                                                                {:fill   "#00aa88"
                                                                 :stroke nil}]]
                                                 :scale       scale
                                                 :margin-frac 0.08})
                       (assoc-in [:x-axis
                                  :label]
                                 (fn [_
                                      _]
                                   ""))
                       (assoc-in [:x-axis
                                  :major]
                                 (range cycle-phase
                                        data-span-days
                                        cycle-length)))
        d18O-axis  (-> (quickthing/secondary-axis (conj d18O-datavec
                                                        [0
                                                         (second (first d18O-datavec))
                                                         nil
                                                         {:fill "transparent"}]
                                                        [data-span-days
                                                         (second (first d18O-datavec))
                                                         nil
                                                         {:fill "transparent"}])
                                                  {:width       width
                                                   :height      (/ height
                                                                   2.0)
                                                   :y-name "d18O"
                                                   :scale       scale
                                                   :margin-frac 0.08
                                                   :color       "#33ff"})
                       (assoc-in [:x-axis
                                  :visible]
                                 false)
                       (assoc-in [:x-axis
                                  :label]
                                 (thi.ng.geom.viz.core/default-svg-label #(+ cycle-start-value
                                                                             (/ %
                                                                                cycle-length))))
                       (assoc-in [:x-axis
                                  :major]
                                 (range cycle-phase
                                        data-span-days
                                        cycle-length)))
        index-axis (-> (quickthing/primary-axis [[0
                                                  0]
                                                 [data-span-days
                                                  climate-index-max]]
                                                {:width       width
                                                 :height      (/ height
                                                                 2.0)
                                                 :scale       scale
                                                 :title       "Monsoon"
                                                 :legend      [["SUMMER MONSOON" #_"First Singular Vector"
                                                                {:fill   "#aa8800"
                                                                 :stroke nil}]
                                                               ["WINTER MONSOON" #_"Second Singular Vector"
                                                                {:fill   "#00aa88"
                                                                 :stroke nil}]]
                                                 :margin-frac 0.08
                                                 :color       "#0008"})
                       (assoc-in [:y-axis
                                  :visible]
                                 false)
                       (assoc-in [:x-axis
                                  :label]
                                 (thi.ng.geom.viz.core/default-svg-label #(+ cycle-start-value
                                                                             (/ %
                                                                                cycle-length))))
                       (assoc-in [:y-axis
                                  :major]
                                 [])
                       (assoc-in [:x-axis
                                  :major]
                                 (range cycle-phase
                                        data-span-days
                                        cycle-length)))]
    (let [grid-plot  (-> rain-axis
                         (assoc-in [:y-axis
                                    :major]
                                   [])
                         (assoc-in [:y-axis
                                    :major]
                                   [])
                         (assoc-in [:x-axis
                                    :visible]
                                   false)
                         (assoc-in [:y-axis
                                    :visible]
                                   false)
                         viz/svg-plot2d-cartesian)
          rain-plot  (-> rain-axis
                         (assoc :grid
                                nil)
                         (update :data
                                 #(into %
                                        (quickthing/bars rain-datavec
                                                         {:attribs {:stroke-width bar-width
                                                                    :stroke       "#000000"}})))
                         (update :data
                                 #(into %
                                        (quickthing/circles (->> missing-days-datavec
                                                                 (mapv (fn [coord]
                                                                         coord #_
                                                                         (conj coord
                                                                               "x"))))
                                                            {:scale   6
                                                             :attribs {:fill "#f00"
                                                                       #_#_
                                                                       :stroke
                                                                       "#aa8800"}})))
                         viz/svg-plot2d-cartesian)
          d18O-plot  (-> d18O-axis
                         #_#_
                         (update :data
                                 #(into %
                                        (quickthing/dashed-line [[1.0
                                                                  (:mean above-stat)]
                                                                 [(dec data-span-days)
                                                                  (:mean above-stat)]]
                                                                {:attribs {:stroke-width (* 2.0
                                                                                            (:std above-stat))
                                                                           :stroke       "#aa880066"}})))
                         (update :data
                                 #(into %
                                        (quickthing/dashed-line [[1.0
                                                                  (:mean below-stat)]
                                                                 [(dec data-span-days)
                                                                  (:mean below-stat)]]
                                                                {:attribs {:stroke-width (* 2.0
                                                                                            (:std below-stat))
                                                                           :stroke       "#00aa88aa"}})))
                         (update :data
                                 #(into %
                                        (quickthing/circles d18O-datavec
                                                            {:scale   5})))
                         viz/svg-plot2d-cartesian)
          index-plot (-> index-axis
                         (update :data
                                 #(into %
                                        (quickthing/bars monsoon-winter-datavec
                                                         {:attribs {:stroke-width bar-width
                                                                    :stroke       "#00aa88"}})))
                         (update :data
                                 #(into %
                                        (quickthing/bars monsoon-summer-datavec
                                                         {:attribs {:stroke-width bar-width
                                                                    :stroke       "#aa8800"}})))
                         viz/svg-plot2d-cartesian)]
      (spit "rain-monsoon.svg"
            (-> (quickthing/group-plots-grid [[(-> (svg/group {}
                                                              grid-plot
                                                              rain-plot
                                                              d18O-plot)
                                                   (quickthing/svg-wrap [width
                                                                         (/ height
                                                                            2.0)]
                                                                        width))]
                                              [(-> (svg/group {}
                                                              index-plot)
                                                   (quickthing/svg-wrap [width
                                                                         (/ height
                                                                            2.0)]
                                                                        width))]])
                quickthing/svg2xml))
      (spit "rain-monsoon-double.svg"
            (-> (quickthing/group-plots-grid [[(-> (svg/group {}
                                                              grid-plot
                                                              rain-plot
                                                              d18O-plot)
                                                   (quickthing/svg-wrap [width
                                                                         (/ height
                                                                            2.0)]
                                                                        width))]
                                              [(-> (svg/group {}
                                                              index-plot
                                                              d18O-plot)
                                                   (quickthing/svg-wrap [width
                                                                         (/ height
                                                                            2.0)]
                                                                        width))]])
                quickthing/svg2xml)))))
#_
(defn
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


#_
(let [width                 1800
      height                900
      right-to-left-ecdf?   true
      scale                 50
      bar-width             15
      margin-frac           0.1
      {::keys [d18O
               stat-rain-weighted-d18O
               d18O-above
               d18O-below]} smap]
  (let [binned-d18O       (bin-to-range -20.0
                                        0.5
                                        (-> d18O
                                            (get "d18O")
                                            seq)
                                         (repeat 1.0))
        binned-d18O-above  (bin-to-range -20.1
                                         0.5
                                         (-> d18O-above
                                             (get "d18O")
                                             seq)
                                         (repeat 1.0))
        binned-d18O-below (bin-to-range -19.9
                                        0.5
                                        (-> d18O-below
                                            (get "d18O")
                                            seq)
                                        (repeat 1.0))
        d18O-axis         (-> (quickthing/primary-axis (into [[-20 0]
                                                              [10 0]]
                                                             binned-d18O)
                                                       {:width       width
                                                        :height      height
                                                        :x-name      "d18O"
                                                        :y-name      "Count (rainy days in bin)"
                                                        :title       (str "d18O Hist")
                                                        :legend      [["SUMMER MONSOON" #_"First Singular Vector"
                                                                {:fill   "#aa8800"
                                                                 :stroke nil}]
                                                               ["WINTER MONSOON" #_"Second Singular Vector"
                                                                {:fill   "#00aa88"
                                                                 :stroke nil}]
                                                               ["TOTAL" #_"Second Singular Vector"
                                                                {:fill   "#0004"
                                                                 :stroke nil}]]
                                                        :scale       scale
                                                        :margin-frac margin-frac})
                              #_
                              (assoc-in [:x-axis
                                         :label]
                                        (fn [_
                                             _]
                                          "")))
        ;;#_#_
        ecdf-axis         (-> (quickthing/secondary-axis [[(:min stat-rain-weighted-d18O)
                                                           0.0]
                                                          [(:max stat-rain-weighted-d18O)
                                                           1.0]]
                                                         {:width            width
                                                          :height           height
                                                          :scale            scale
                                                          :margin-frac      margin-frac
                                                          :y-name           "eCDF"
                                                          :y-breathing-room 0.0})
                              (assoc-in [:x-axis
                                         :visible]
                                        false)) ]
    (let [d18O-plot       (-> d18O-axis
                              (update :data
                                      #(into %
                                             (quickthing/bars binned-d18O
                                                              {:attribs {:stroke-width bar-width
                                                                         :stroke       "#0002"
                                                                         :fill         "#0000"}})))
                              viz/svg-plot2d-cartesian)
          d18O-above-plot (-> d18O-axis
                              (update :data
                                      #(into %
                                             (quickthing/bars (->> binned-d18O-above
                                                                  (mapv (fn [point]
                                                                          (update point
                                                                                  1
                                                                                  (partial *
                                                                                           1.5)))))
                                                              {:attribs {:stroke-width bar-width
                                                                         :opacity      0.5
                                                                         :stroke       "#aa8800"}})))
                              viz/svg-plot2d-cartesian)
          d18O-below-plot (-> d18O-axis
                              (update :data
                                      #(into %
                                             (quickthing/bars binned-d18O-below
                                                              {:attribs {:stroke-width bar-width
                                                                         :opacity      0.5
                                                                         :stroke       "#00aa88"}})))
                              viz/svg-plot2d-cartesian)
          ecdf-plot       (-> ecdf-axis
                              (update :data
                                      #(into %
                                             (quickthing/solid-line (quickthing/ecdf (-> d18O-above
                                                                                         (get "d18O"))
                                                                                     {:reversed? right-to-left-ecdf?})
                                                                    {:attribs {#_#_:stroke-width bar-width
                                                                               :stroke           "#aa8800"}})))
                              (update :data
                                      #(into %
                                             (quickthing/solid-line (quickthing/ecdf  (-> d18O-below
                                                                                          (get "d18O"))
                                                                                      {:reversed? right-to-left-ecdf?})
                                                                    {:attribs {#_#_:stroke-width bar-width
                                                                               :stroke           "#00aa88"}})))
                              (update :data
                                      #(into %
                                             (quickthing/solid-line (quickthing/ecdf (-> d18O
                                                                                         (get "d18O"))
                                                                                     {:reversed? right-to-left-ecdf?})
                                                                    {:attribs {#_#_:stroke-width bar-width
                                                                               :stroke           "#0007"
                                                                               :fill             "#0000"}})))
                              viz/svg-plot2d-cartesian)]
      (spit "hist-d18O.svg"
            (-> (quickthing/group-plots-grid [[(-> (svg/group {}
                                                              d18O-plot
                                                              d18O-above-plot
                                                              d18O-below-plot
                                                              ecdf-plot)
                                                   (quickthing/svg-wrap [width
                                                                         height]
                                                                        width))]])
                quickthing/svg2xml))
      )))







#_
(let [width                 1800
      height                900
      right-to-left-ecdf?   true
      scale                 50
      bar-width             15
      margin-frac           0.1
      {::keys [d18O
               stat-rain-weighted-d18O
               d18O-above
               d18O-below]} smap]
  (let [binned-d18O     (bin-to-range -20
                                      0.5
                                      (-> d18O
                                          (get "d18O")
                                          seq)
                                      (-> d18O
                                          (get "Rain (mm)")
                                          seq))
        binned-d18O-above  (bin-to-range -20.1
                                         0.5
                                         (-> d18O-above
                                             (get "d18O")
                                             seq)
                                         (-> d18O-above
                                             (get "Rain (mm)")
                                             seq))
        binned-d18O-below  (bin-to-range -19.9
                                         0.5
                                         (-> d18O-below
                                             (get "d18O")
                                             seq)
                                         (-> d18O-below
                                             (get "Rain (mm)")
                                             seq))
        ecdf-coords-weighted (quickthing/ecdf-weighted (-> d18O
                                                           (get "d18O"))
                                                       (-> d18O
                                                           (get "Rain (mm)"))
                                                       {:reversed? right-to-left-ecdf?})
        ecdf-coords-weighted-above (quickthing/ecdf-weighted (-> d18O-above
                                                                 (get "d18O"))
                                                             (-> d18O-above
                                                                 (get "Rain (mm)"))
                                                             {:reversed? right-to-left-ecdf?})
        ecdf-coords-weighted-below (quickthing/ecdf-weighted (-> d18O-below
                                                                 (get "d18O"))
                                                             (-> d18O-below
                                                                 (get "Rain (mm)"))
                                                             {:reversed? right-to-left-ecdf?})
        d18O-axis         (-> (quickthing/primary-axis (into [[-20 0]
                                                              [10 0]]
                                                             binned-d18O)
                                                       {:width       width
                                                        :height      height
                                                        :x-name      "d18O"
                                                        :y-name      "Rain (mm) per bin"
                                                        :title       (str "d18O Hist - Rain weighted")
                                                        :legend      [["SUMMER MONSOON" #_"First Singular Vector"
                                                                {:fill   "#aa8800"
                                                                 :stroke nil}]
                                                               ["WINTER MONSOON" #_"Second Singular Vector"
                                                                {:fill   "#00aa88"
                                                                 :stroke nil}]
                                                               ["TOTAL" #_"Second Singular Vector"
                                                                {:fill   "#0004"
                                                                 :stroke nil}]]
                                                        :scale       scale
                                                        :margin-frac margin-frac})
                              #_
                              (assoc-in [:x-axis
                                         :label]
                                        (fn [_
                                             _]
                                          "")))
        ;;#_#_
        ecdf-axis         (-> (quickthing/secondary-axis [[(:min stat-rain-weighted-d18O)
                                                           0.0]
                                                          [(:max stat-rain-weighted-d18O)
                                                           1.0]]
                                                         {:width            width
                                                          :height           height
                                                          :scale            scale
                                                          :margin-frac      margin-frac
                                                          :y-name           "eCDF (fraction of rain heavier than given amount)"
                                                          :y-breathing-room 0.0})
                              (assoc-in [:x-axis
                                         :visible]
                                        false)) ]
    (let [d18O-plot       (-> d18O-axis
                              (update :data
                                      #(into %
                                             (quickthing/bars binned-d18O
                                                              {:attribs {:stroke-width bar-width
                                                                         :stroke       "#0002"
                                                                         :fill         "#0000"}})))
                              viz/svg-plot2d-cartesian)
          d18O-above-plot (-> d18O-axis
                              (update :data
                                      #(into %
                                             (quickthing/bars (->> binned-d18O-above
                                                                  (mapv (fn [point]
                                                                          (update point
                                                                                  1
                                                                                  (partial *
                                                                                           1.0)))))
                                                              {:attribs {:stroke-width bar-width
                                                                         :opacity      0.5
                                                                         :stroke       "#aa8800"}})))
                              viz/svg-plot2d-cartesian)
          d18O-below-plot (-> d18O-axis
                              (update :data
                                      #(into %
                                             (quickthing/bars binned-d18O-below
                                                              {:attribs {:stroke-width bar-width
                                                                         :opacity      0.5
                                                                         :stroke       "#00aa88"}})))
                              viz/svg-plot2d-cartesian)
          ecdf-plot       (-> ecdf-axis
                              (update :data
                                      #(into %
                                             (quickthing/solid-line ecdf-coords-weighted-above
                                                                    {:attribs {#_#_:stroke-width bar-width
                                                                               :stroke           "#aa8800"}})))
                              (update :data
                                      #(into %
                                             (quickthing/solid-line ecdf-coords-weighted-below
                                                                    {:attribs {#_#_:stroke-width bar-width
                                                                               :stroke           "#00aa88"}})))
                              (update :data
                                      #(into %
                                             (quickthing/solid-line ecdf-coords-weighted
                                                                    {:attribs {#_#_:stroke-width bar-width
                                                                               :stroke           "#0007"
                                                                               :fill             "#0000"}})))
                              viz/svg-plot2d-cartesian)]
      (spit "hist-amount-weighted-d18O.svg"
            (-> (quickthing/group-plots-grid [[(-> (svg/group {}
                                                              d18O-plot
                                                              d18O-above-plot
                                                              d18O-below-plot
                                                              ecdf-plot)
                                                   (quickthing/svg-wrap [width
                                                                         height]
                                                                        width))]])
                quickthing/svg2xml))
      )))

#_
(let [width                 1800
      height                900
      right-to-left-ecdf?   true
      scale                 50
      bar-width             15
      margin-frac           0.1
      {::keys [d18O
               stat-rain-weighted-d18O
               d18O-above
               d18O-below]} smap]
  (let [#_#_
        binned-d18O     (bin-to-range -20
                                      0.5
                                      (-> d18O
                                          (get "d18O")
                                          seq)
                                      (-> d18O
                                          (get "Rain (mm)")
                                          seq))
        binned-d18O-above  (bin-to-range -20.1
                                         0.5
                                         (-> d18O-above
                                             (get "d18O")
                                             seq)
                                         (-> d18O-above
                                             (get "column-0")
                                             seq))
        binned-d18O-below  (bin-to-range -19.9
                                         0.5
                                         (-> d18O-below
                                             (get "d18O")
                                             seq)
                                         (-> d18O-below
                                             (get "column-1")
                                             seq))
        #_#_
        ecdf-coords-weighted (quickthing/ecdf-weighted (-> d18O
                                                           (get "d18O"))
                                                       (-> d18O
                                                           (get "Rain (mm)"))
                                                       {:reversed? right-to-left-ecdf?})
        ecdf-coords-weighted-above (quickthing/ecdf-weighted (-> d18O-above
                                                                 (get "d18O"))
                                                             (-> d18O-above
                                                                 (get "column-0"))
                                                             {:reversed? right-to-left-ecdf?})
        ecdf-coords-weighted-below (quickthing/ecdf-weighted (-> d18O-below
                                                                 (get "d18O"))
                                                             (-> d18O-below
                                                                 (get "column-1"))
                                                             {:reversed? right-to-left-ecdf?})
        d18O-axis         (-> (quickthing/primary-axis (into [[-20 0]
                                                              [10 0]]
                                                             (into binned-d18O-above
                                                                   binned-d18O-below))
                                                       {:width       width
                                                        :height      height
                                                        :x-name      "d18O"
                                                        :y-name      "Monsoon fraction per bin"
                                                        :title       (str "d18O Hist - Index weighted")
                                                        :legend      [["SUMMER MONSOON" #_"First Singular Vector"
                                                                {:fill   "#aa8800"
                                                                 :stroke nil}]
                                                               ["WINTER MONSOON" #_"Second Singular Vector"
                                                                {:fill   "#00aa88"
                                                                 :stroke nil}]
                                                                      #_
                                                               ["TOTAL" #_"Second Singular Vector"
                                                                {:fill   "#0004"
                                                                 :stroke nil}]]
                                                        :scale       scale
                                                        :margin-frac margin-frac})
                              #_
                              (assoc-in [:x-axis
                                         :label]
                                        (fn [_
                                             _]
                                          "")))
        ;;#_#_
        ecdf-axis         (-> (quickthing/secondary-axis [[(:min stat-rain-weighted-d18O)
                                                           0.0]
                                                          [(:max stat-rain-weighted-d18O)
                                                           1.0]]
                                                         {:width            width
                                                          :height           height
                                                          :scale            scale
                                                          :margin-frac      margin-frac
                                                          :y-name           "eCDF (fraction of rain heavier than given amount)"
                                                          :y-breathing-room 0.0})
                              (assoc-in [:x-axis
                                         :visible]
                                        false)) ]
    (let [#_#_d18O-plot       (-> d18O-axis
                              (update :data
                                      #(into %
                                             (quickthing/bars binned-d18O
                                                              {:attribs {:stroke-width bar-width
                                                                         :stroke       "#0002"
                                                                         :fill         "#0000"}})))
                              viz/svg-plot2d-cartesian)
          d18O-above-plot (-> d18O-axis
                              (update :data
                                      #(into %
                                             (quickthing/bars (->> binned-d18O-above
                                                                  (mapv (fn [point]
                                                                          (update point
                                                                                  1
                                                                                  (partial *
                                                                                           1.0)))))
                                                              {:attribs {:stroke-width bar-width
                                                                         :opacity      0.5
                                                                         :stroke       "#aa8800"}})))
                              viz/svg-plot2d-cartesian)
          d18O-below-plot (-> d18O-axis
                              (update :data
                                      #(into %
                                             (quickthing/bars binned-d18O-below
                                                              {:attribs {:stroke-width bar-width
                                                                         :opacity      0.5
                                                                         :stroke       "#00aa88"}})))
                              viz/svg-plot2d-cartesian)
          ecdf-plot       (-> ecdf-axis
                              (update :data
                                      #(into %
                                             (quickthing/solid-line ecdf-coords-weighted-above
                                                                    {:attribs {#_#_:stroke-width bar-width
                                                                               :stroke           "#aa8800"}})))
                              (update :data
                                      #(into %
                                             (quickthing/solid-line ecdf-coords-weighted-below
                                                                    {:attribs {#_#_:stroke-width bar-width
                                                                               :stroke           "#00aa88"}})))
                              #_
                              (update :data
                                      #(into %
                                             (quickthing/solid-line ecdf-coords-weighted
                                                                    {:attribs {#_#_:stroke-width bar-width
                                                                               :stroke           "#0007"
                                                                               :fill             "#0000"}})))
                              viz/svg-plot2d-cartesian)]
      (spit "hist-index-weighted-d18O.svg"
            (-> (quickthing/group-plots-grid [[(-> (svg/group {}
                                                              #_
                                                              d18O-plot
                                                              d18O-above-plot
                                                              d18O-below-plot
                                                              ecdf-plot)
                                                   (quickthing/svg-wrap [width
                                                                         height]
                                                                        width))]])
                quickthing/svg2xml))
      )))



#_
(let [width                 1800
      height                900
      right-to-left-ecdf?   false
      scale                 50
      bar-width             15
      margin-frac           0.1
      {::keys [d18O
               stat-rain-weighted-d18O
               d18O-above
               d18O-below]} smap]
  (let [summermonsoon-d18O  (->> d18O-above
                                 ds/rows
                                 (map (fn [row-data]
                                        [(row-data "Rain (mm)")
                                         (row-data "d18O")])))
        wintermonsoon-d18O  (->> d18O-below
                                 ds/rows
                                 (map (fn [row-data]
                                        [(row-data "Rain (mm)")
                                         (row-data "d18O")])))
        rain-d18O-axis         (-> (quickthing/primary-axis (into summermonsoon-d18O
                                                                  wintermonsoon-d18O)
                                                       {:width       width
                                                        :height      height
                                                        :x-name      "Rain (mm)"
                                                        :y-name      "d18O"
                                                        :title       (str "Isotope - Rain amount")
                                                        #_#_
                                                        :legend      [[(str "SUMMER d18O: "
                                                                            (->> above-stat
                                                                                 :mean
                                                                                 (format (str "%.3g"))))
                                                                       {:fill   "#aa8800"
                                                                        :stroke nil}]
                                                                      [(str "WINTER d18O: "
                                                                            (->> below-stat
                                                                                 :mean
                                                                                 (format (str "%.3g"))))
                                                                       {:fill   "#00aa88"
                                                                        :stroke nil}]]
                                                        :scale       scale
                                                        :margin-frac margin-frac})
                              #_
                              (assoc-in [:x-axis
                                         :label]
                                        (fn [_
                                             _]
                                          "")))]
    (let [rain-d18O-plot       (-> rain-d18O-axis
                                   (update :data
                                           #(into %
                                                  (quickthing/circles wintermonsoon-d18O
                                                                      {:scale   7
                                                                       :attribs {:fill "#00aa88"}})))
                                   (update :data
                                           #(into %
                                                  (quickthing/circles summermonsoon-d18O
                                                                      {:scale   7
                                                                       :attribs {:fill "#aa8800"}})))
                                   viz/svg-plot2d-cartesian)]
      (spit "rain-vs-d18O.svg"
            (-> (quickthing/group-plots-grid [[(-> (svg/group {}
                                                              rain-d18O-plot)
                                                   (quickthing/svg-wrap [width
                                                                         height]
                                                                        width))]])
                quickthing/svg2xml))
      )))





#_
(let [width                 1800
      height                900
      right-to-left-ecdf?   false
      scale                 50
      bar-width             15
      margin-frac           0.1
      {::keys [d18O
               stat-rain-weighted-d18O
               d18O-above
               d18O-below]} smap]
  (let [summermonsoon-d18O  (->> d18O-above
                                 ds/rows
                                 (map (fn [row-data]
                                        [(row-data "column-0")
                                         (row-data "d18O")])))
        wintermonsoon-d18O  (->> d18O-below
                                 ds/rows
                                 (map (fn [row-data]
                                        [(row-data "column-1")
                                         (row-data "d18O")])))
        rain-d18O-axis         (-> (quickthing/primary-axis (into summermonsoon-d18O
                                                                  wintermonsoon-d18O)
                                                       {:width       width
                                                        :height      height
                                                        :x-name      "Climate Index"
                                                        :y-name      "d18O"
                                                        :title       (str "Isotope - Index magnitude")
                                                        #_#_
                                                        :legend      [[(str "SUMMER d18O: "
                                                                            (->> above-stat
                                                                                 :mean
                                                                                 (format (str "%.3g"))))
                                                                       {:fill   "#aa8800"
                                                                        :stroke nil}]
                                                                      [(str "WINTER d18O: "
                                                                            (->> below-stat
                                                                                 :mean 
                                                                                 (format (str "%.3g"))))
                                                                       {:fill   "#00aa88"
                                                                        :stroke nil}]]
                                                        :scale       scale
                                                        :margin-frac margin-frac})
                              #_
                              (assoc-in [:x-axis
                                         :label]
                                        (fn [_
                                             _]
                                          "")))]
    (let [rain-d18O-plot       (-> rain-d18O-axis
                                   (update :data
                                           #(into %
                                                  (quickthing/circles wintermonsoon-d18O
                                                                      {:scale   7
                                                                       :attribs {:fill "#00aa88"}})))
                                   (update :data
                                           #(into %
                                                  (quickthing/circles summermonsoon-d18O
                                                                      {:scale   7
                                                                       :attribs {:fill "#aa8800"}})))
                                   viz/svg-plot2d-cartesian)]
      (spit "monsoon-vs-d18O.svg"
            (-> (quickthing/group-plots-grid [[(-> (svg/group {}
                                                              rain-d18O-plot)
                                                   (quickthing/svg-wrap [width
                                                                         height]
                                                                        width))]])
                quickthing/svg2xml))
      )))





;;; UNUSED BELOW
#_
(let [plot-title "d18O-by-enso"
      right-to-left-ecdf? false
      width      1500
      height     1500
      max-rain   (->> "d18O"
                      glued
                      (filter some?)
                      (apply max))
      d18O       (->> "d18O"
                      glued
                      (filter some?)
                      sort)
      d18O-above (->> "d18O"
                      ((ds/filter-column glued
                                         "ENSO"
                                         #(pos? %)))
                      (filter some?)
                      sort)
      d18O-below (->> "d18O"
                      ((ds/filter-column glued
                                         "ENSO"
                                         #(neg? %)))
                      (filter some?)
                      sort)]
  (let [d18O-binned       (-> d18O
                              quickthing/bin-data)
        d18O-above-binned (->> d18O-above
                              quickthing/bin-data
                              (mapv (fn [[coord
                                          value]]
                                      [coord
                                       (* 1.0 ;; fudge factor...
                                          value)])))
        d18O-below-binned (-> d18O-below
                              quickthing/bin-data)]
    (let [bar-width (* 0.5
                       (/ width
                          (->> d18O-binned
                               count)))
          d18O-axis (-> (quickthing/primary-axis d18O-binned
                                                 {:width  width
                                                  :height height
                                                  :x-name "d18O"
                                                  :y-name "dD"
                                                  :title  plot-title
                                                  :legend [["Rain Amount" {:fill (last quickthing/red-blue-colors)}]
                                                           ["(sqrt of normalized amount)" {:stroke "none"}]]})
                        #_#_(assoc-in [:x-axis
                                       :visible]
                                      false)
                        (assoc-in [:y-axis
                                   :visible]
                                  false))
          ecdf-axis (-> (quickthing/secondary-axis [[(first d18O)
                                                     0.0]
                                                    [(last d18O)
                                                     1.0]]
                                                   {:width            width
                                                    :height           height
                                                    :y-name           "Cumulative Probability"
                                                    :y-breathing-room 0.0}))]
      (let [excess-plot (-> d18O-axis
                            (update :data
                                    #(into %
                                           (quickthing/bars d18O-binned
                                                            {:attribs {:stroke-width bar-width
                                                                       :stroke       "#0002"
                                                                       :fill         "#0000"}})))
                            (update :data
                                    #(into %
                                           (quickthing/bars d18O-above-binned
                                                            {:attribs {:stroke-width bar-width
                                                                       :stroke       "#00f2"
                                                                       :fill         "#0000"}})))
                            (update :data
                                    #(into %
                                           (quickthing/bars d18O-below-binned
                                                            {:attribs {:stroke-width bar-width
                                                                       :stroke       "#f002"
                                                                       :fill         "#0000"}})))
                            #_
                            (update :data
                                    #(into %
                                           (quickthing/adjustable-text d18O-vs-dD-text
                                                                       {:scale 7})))
                            viz/svg-plot2d-cartesian)
            ecdf-plot   (-> ecdf-axis
                            (update :data
                                    #(into %
                                           (quickthing/solid-line (quickthing/ecdf d18O-above
                                                                                   {:reversed? right-to-left-ecdf?})
                                                                  {:attribs {#_#_:stroke-width bar-width
                                                                             :stroke           "#00f7"
                                                                             :fill             "#0000"}})))
                            (update :data
                                    #(into %
                                           (quickthing/solid-line (quickthing/ecdf d18O-below
                                                                                   {:reversed? right-to-left-ecdf?})
                                                                  {:attribs {#_#_:stroke-width bar-width
                                                                             :stroke           "#f007"
                                                                             :fill             "#0000"}})))
                            (update :data
                                    #(into %
                                           (quickthing/solid-line (quickthing/ecdf d18O
                                                                                   {:reversed? right-to-left-ecdf?})
                                                                  {:attribs {#_#_:stroke-width bar-width
                                                                             :stroke           "#0007"
                                                                             :fill             "#0000"}})))
                            viz/svg-plot2d-cartesian)]
        (spit (str plot-title
                   ".svg")
              (-> (svg/group {}
                             ecdf-plot
                             excess-plot)
                  (quickthing/svg-wrap [width
                                        height]
                                       width)
                  quickthing/svg2xml))))))



#_
(let [{glued             ::full-table
       problematic-dates ::problematic-dates
       problematic-d18O  ::problematic-d18O
       crazy-values      ::crazy-dates
       above-stat        ::above-stat
       below-stat        ::below-stat} smap ]
  (let [width                3000
        height               500
        cycle-length         365
        cycle-phase          0
        cycle-start-value    2011
        d18O-max             (->> "d18O"
                                  glued
                                  (filter some?)
                                  (apply max))
        dD-max               (->> "dD"
                                  glued
                                  (filter some?)
                                  (apply max))
        d18O-min             (->> "d18O"
                                  glued
                                  (filter some?)
                                  (apply min))
        dD-min               (->> "dD"
                                  glued
                                  (filter some?)
                                  (apply min))
        index-max            (apply max
                                    (into (glued "column-0")
                                          (glued "column-1")))
        x-max                (last (glued "Day"))
        d18O-datavec         (->> glued
                                  ds/rows
                                  (filter (fn [row-data]
                                            (some? (row-data "d18O"))))
                                  (map (fn [row-data]
                                         [(row-data "Day")
                                          (row-data "d18O")
                                          nil
                                          {:tooltip (str (row-data "Date")
                                                         \newline
                                                         (row-data "Comment"))}])))
        dD-datavec           (->> glued
                                  ds/rows
                                  (filter (fn [row-data]
                                            (some? (row-data "dD"))))
                                  (map (fn [row-data]
                                         [(row-data "Day")
                                          (row-data "dD")
                                          nil
                                          {:tooltip (str (row-data "Date")
                                                         \newline
                                                         (row-data "Comment"))}])))
        rain-datavec         (filterv #(-> %
                                           second
                                           some?)
                                      (mapv vector
                                            (glued "Day")
                                            (glued "Rain (mm)")))
        d-excess-datavec     (->> glued
                                  ds/rows
                                  (filter (fn [row-data]
                                            (some? (row-data "dD"))))
                                  (map (fn [row-data]
                                         [(row-data "Day")
                                          (row-data "D-excess")
                                          nil
                                          {:tooltip (str (row-data "Date")
                                                         \newline
                                                         (row-data "Comment"))}])))
        missing-days-datavec (->> (ds/filter-column glued
                                                    "Date"
                                                    (fn [given-date]
                                                      (some? ((clojure.set/union problematic-dates
                                                                                 problematic-d18O
                                                                                 crazy-values) given-date))))
                                  ds/rows
                                  (mapv (fn [data-row]
                                          [(data-row "Day")
                                           0])))
        enso-datavec         (mapv vector
                                   (glued "Day")
                                   (glued "ENSO"))]
    (let [d18O-dummy-range  [[0
                              d18O-min]
                             [x-max
                              d18O-max]]
          dD-dummy-range    [[0
                              dD-min]
                             [x-max
                              dD-max]]
          index-dummy-range [[0
                              0]
                             [x-max
                              index-max]]
          bar-width         (* 0.5
                               (/ width
                                  x-max))]
      (let [d18O-axis     (-> (quickthing/primary-axis d18O-dummy-range
                                                       {:width       width
                                                        :height      height #_#_#_#_
                                                        :margin-frac 0.07
                                                        :color       "#0008"})
                              (assoc-in [:x-axis
                                         :label]
                                        (thi.ng.geom.viz.core/default-svg-label #(+ cycle-start-value
                                                                                    (/ %
                                                                                       cycle-length))))
                              (assoc-in [:x-axis
                                         :major]
                                        (range cycle-phase
                                               x-max
                                               cycle-length)))
            dD-axis       (-> (quickthing/secondary-axis dD-dummy-range
                                                         {:width       width
                                                          :height      height #_#_#_#_
                                                          :margin-frac 0.07
                                                          :color       "#0008"})
                              (assoc-in [:x-axis
                                         :label]
                                        (thi.ng.geom.viz.core/default-svg-label #(+ cycle-start-value
                                                                                    (/ %
                                                                                       cycle-length))))
                              (assoc-in [:x-axis
                                         :major]
                                        (range cycle-phase
                                               x-max
                                               cycle-length)))
            index-axis    (-> (quickthing/secondary-axis index-dummy-range
                                                         {:width       width
                                                          :height      height #_#_#_#_
                                                          :margin-frac 0.07
                                                          :color       "#0008"})
                              (assoc-in [:y-axis
                                         :visible]
                                        false)
                              (assoc-in [:grid]
                                        nil)
                              (assoc-in [:x-axis
                                         :label]
                                        (thi.ng.geom.viz.core/default-svg-label #(+ cycle-start-value
                                                                                    (/ %
                                                                                       cycle-length))))
                              (assoc-in [:x-axis
                                         :major]
                                        (range cycle-phase
                                               x-max
                                               cycle-length)))
            rain-axis     (-> (quickthing/primary-axis (into [[0,0]]
                                                             rain-datavec)
                                                       {:width  width
                                                        :height (/ height
                                                                   2.0)
                                                        :x-name "Rain (mm)"
                                                        #_#_
                                                        :legend [["+   El Nino" nil]
                                                                 ["-   La Nina" nil]]
                                                        :title  "Rain Amount"})
                              #_
                              (assoc-in [:y-axis
                                         :major]
                                        [-2,0,2])
                              (assoc-in [:grid]
                                        nil)
                              (assoc-in [:x-axis
                                         :label]
                                        (fn [_
                                             _]
                                          "") #_
                                        (thi.ng.geom.viz.core/default-svg-label #(+ cycle-start-value
                                                                                    (/ %
                                                                                       cycle-length))))
                              (assoc-in [:x-axis
                                         :major]
                                        (range cycle-phase
                                               x-max
                                               cycle-length)))
            d-excess-axis (-> (quickthing/secondary-axis (into [[0,0]]
                                                               d-excess-datavec)
                                                         {:width  width
                                                          :height (/ height
                                                                     2.0)
                                                          :color  "#8008"
                                                          :y-name "Deuterium Excess"
                                                          #_#_
                                                          :legend [["+   El Nino" nil]
                                                                   ["-   La Nina" nil]]})
                              (assoc-in [:y-axis
                                         :major]
                                        [0,10])
                              (assoc-in [:grid]
                                        {#_#_#_#_:minor-y true
                                         :minor-x         true})
                              (assoc-in [:x-axis
                                         :label]
                                        (fn [_
                                             _]
                                          "") #_
                                        (thi.ng.geom.viz.core/default-svg-label #(+ cycle-start-value
                                                                                    (/ %
                                                                                       cycle-length))))
                              (assoc-in [:x-axis
                                         :major]
                                        (range cycle-phase
                                               x-max
                                               cycle-length)))
            enso-axis     (-> (quickthing/zero-axis enso-datavec
                                                    {:width  width
                                                     :height (/ height
                                                                2.0)
                                                     :legend [["+   El Nino" nil]
                                                              ["-   La Nina" nil]]
                                                     :title  "ENSO Nino 3.4 Index"})
                              (assoc-in [:y-axis
                                         :major]
                                        [-2,0,2])
                              #_
                              (assoc-in [:grid]
                                        nil)
                              (assoc-in [:x-axis
                                         :label]
                                        (fn [_
                                             _]
                                          "") #_
                                        (thi.ng.geom.viz.core/default-svg-label #(+ cycle-start-value
                                                                                    (/ %
                                                                                       cycle-length))))
                              (assoc-in [:x-axis
                                         :major]
                                        (range cycle-phase
                                               x-max
                                               cycle-length)))
            isotope-axis  nil]
        (let [d18O-plot     (-> d18O-axis
                                (update :data
                                        #(into %
                                               (quickthing/dashed-line [[1.0
                                                                         (:mean above-stat)]
                                                                        [(-> (glued "Day")
                                                                             sort
                                                                             last
                                                                             dec)
                                                                         (:mean above-stat)]]
                                                                       {:attribs {:stroke-width (* 2.0
                                                                                                   (:std above-stat))
                                                                                  :stroke       "#aa880066"}})))
                                (update :data
                                        #(into %
                                               (quickthing/dashed-line [[1.0
                                                                         (:mean below-stat)]
                                                                        [(-> (glued "Day")
                                                                             sort
                                                                             last
                                                                             dec)
                                                                         (:mean below-stat)]]
                                                                       {:attribs {:stroke-width (* 2.0
                                                                                                   (:std below-stat))
                                                                                  :stroke       "#00aa88aa"}})))
                                (update :data
                                        #(into %
                                               (quickthing/circles d18O-datavec
                                                                   {:scale   5
                                                                    :attribs {:fill "#0008"}})))
                                viz/svg-plot2d-cartesian #_
                                (quickthing/svg-wrap [width
                                                      height]
                                                     width))
              dD-plot       (-> dD-axis
                                (update :data
                                        #(into %
                                               (quickthing/circles dD-datavec
                                                                   {:scale   5
                                                                    :attribs {:fill "#0088"}})))
                                viz/svg-plot2d-cartesian #_
                                (quickthing/svg-wrap [width
                                                      height]
                                                     width))
              index-plot    (-> index-axis
                                (update :data
                                        #(into %
                                               (quickthing/bars (mapv vector
                                                                      (glued "Day")
                                                                      (glued "column-1"))
                                                                {:attribs {:stroke-width bar-width
                                                                           :stroke       "#00aa88"}})))
                                (update :data
                                        #(into %
                                               (quickthing/bars (mapv vector
                                                                      (glued "Day")
                                                                      (glued "column-0"))
                                                                {:attribs {:stroke-width bar-width
                                                                           :stroke       "#aa8800"}})))
                                (update :data
                                        #(into %
                                               (quickthing/circles (->> missing-days-datavec
                                                                        (mapv (fn [coord]
                                                                                coord #_
                                                                                (conj coord
                                                                                      "x"))))
                                                                   {:scale   6
                                                                    :attribs {:fill   "#f00" #_#_
                                                                              :stroke "#aa8800"}})))                           
                                viz/svg-plot2d-cartesian #_
                                (quickthing/svg-wrap [width
                                                      height]
                                                     width))
              rain-plot     (-> rain-axis
                                (update :data
                                        #(into %
                                               (quickthing/bars rain-datavec
                                                                {:attribs {:stroke-width bar-width
                                                                           :stroke       "#000000"}})))
                                viz/svg-plot2d-cartesian)
              d-excess-plot (-> d-excess-axis
                                (update :data
                                        #(into %
                                               (quickthing/circles d-excess-datavec
                                                                   {:scale   6
                                                                    :attribs {#_#_:stroke-width bar-width
                                                                              :fill             "#a008"}})))
                                viz/svg-plot2d-cartesian)
              enso-plot     (-> enso-axis
                                (update :data
                                        #(into %
                                               (quickthing/solid-line (mapv vector
                                                                            (glued "Day")
                                                                            (glued "ENSO")) #_
                                                                      {:attribs {:stroke-width bar-width
                                                                                 :stroke       "#00aa88"}})))
                                viz/svg-plot2d-cartesian #_
                                (quickthing/svg-wrap [width
                                                      height]
                                                     width))]
          (spit "istopes-vs-index.svg"
                (-> (quickthing/group-plots-grid [[(-> (svg/group {}
                                                                  #_
                                                                  dD-plot ;; a bit cluttered with this
                                                                  d18O-plot
                                                                  index-plot)
                                                       (quickthing/svg-wrap [width
                                                                             height]
                                                                            width))]
                                                  [(-> (svg/group {}
                                                                  rain-plot
                                                                  d-excess-plot)
                                                       (quickthing/svg-wrap [width
                                                                             (/ height
                                                                                2.0)]
                                                                            width))]
                                                  [(-> enso-plot
                                                       (quickthing/svg-wrap [width
                                                                             (/ height
                                                                                2.0)]
                                                                            width))]])
                    quickthing/svg2xml)))))))


#_
(let [width           1500
      height          1500
      d18O-vs-dD      (->> glued
                           ds/rows
                           (map (fn [row-data]
                                  (let [dD   (row-data "dD")
                                        d18O (row-data "d18O")]
                                    (if (or (nil? dD)
                                            (nil? d18O))
                                      nil
                                      [d18O
                                       dD
                                       nil
                                       {:tooltip (str (row-data "Date"))
                                        :fill    (quickthing/color-cycle (/ (mod (row-data "Day")
                                                                                 365)
                                                                            365))}]))))
                           (filter some?))
      d18O-vs-dD-text (->> glued
                           ds/rows
                           (map (fn [row-data]
                                  (let [dD   (row-data "dD")
                                        d18O (row-data "d18O")]
                                    (if (or (nil? dD)
                                            (nil? d18O))
                                      nil
                                      [d18O
                                       dD
                                       (tick/format (tick/formatter "yy")
                                                    (row-data "Date"))
                                       {:tooltip (str (row-data "Date"))
                                        #_#_#_#_
                                        :stroke  (if (row-data "Above?")
                                                   "#8008"
                                                   "#0808")
                                        :fill    (quickthing/color-cycle (/ (mod (row-data "Day")
                                                                                 365)
                                                                            365))}]))))
                           (filter some?))]
  (let [isotope-axis (-> (quickthing/primary-axis d18O-vs-dD
                                                  {:width       width
                                                   :height      height
                                                   :margin-frac 0.04
                                                   :x-name      "d18O"
                                                   :y-name      "dD"
                                                   :legend      [["January" {:fill (-> 15
                                                                                       (/ 365)
                                                                                       quickthing/color-cycle)}]
                                                                 ["February" {:fill (-> 46
                                                                                        (/ 365)
                                                                                        quickthing/color-cycle)}]
                                                                 ["March" {:fill (-> 74
                                                                                     (/ 365)
                                                                                     quickthing/color-cycle)}]
                                                                 ["Arpril" {:fill (-> 105
                                                                                      (/ 365)
                                                                                      quickthing/color-cycle)}]
                                                                 ["May" {:fill (-> 135
                                                                                   (/ 365)
                                                                                   quickthing/color-cycle)}]
                                                                 ["June" {:fill (-> 166
                                                                                    (/ 365)
                                                                                    quickthing/color-cycle)}]
                                                                 ["July" {:fill (-> 196
                                                                                    (/ 365)
                                                                                    quickthing/color-cycle)}]
                                                                 ["August" {:fill (-> 227
                                                                                      (/ 365)
                                                                                      quickthing/color-cycle)}]
                                                                 ["September" {:fill (-> 2288
                                                                                         (/ 365)
                                                                                         quickthing/color-cycle)}]
                                                                 ["October" {:fill (-> 315
                                                                                       (/ 365)
                                                                                       quickthing/color-cycle)}]
                                                                 ["November" {:fill (-> 319
                                                                                        (/ 365)
                                                                                        quickthing/color-cycle)}]
                                                                 ["December" {:fill (-> 349
                                                                                        (/ 365)
                                                                                        quickthing/color-cycle)}]]})
                         #_#_(assoc-in [:x-axis
                                        :visible]
                                       false)
                         (assoc-in [:y-axis
                                    :visible]
                                   false))]
    (let [isotope-plot (-> isotope-axis
                           (update :data
                                   #(into %
                                          (quickthing/circles d18O-vs-dD
                                                                         {:scale 7})))
                           #_
                           (update :data
                                   #(into %
                                          (quickthing/adjustable-text d18O-vs-dD-text
                                                                      {:scale 7})))
                           viz/svg-plot2d-cartesian)]
      (spit "d18O-vs-dD-months.svg"
            (-> (svg/group {}
                           isotope-plot)
                (quickthing/svg-wrap [width
                                      height]
                                     width)
                quickthing/svg2xml)))))

#_
(let [plot-title "d18O vs dD (by month)"
      width           1500
      height          1500
      d18O-vs-dD      (->> glued
                           ds/rows
                           (map (fn [row-data]
                                  (let [dD   (row-data "dD")
                                        d18O (row-data "d18O")]
                                    (if (or (nil? dD)
                                            (nil? d18O))
                                      nil
                                      [d18O
                                       dD
                                       nil
                                       {:tooltip (str (row-data "Date"))
                                        :fill    (quickthing/color-cycle (/ (mod (row-data "Day")
                                                                                 365)
                                                                            365))}]))))
                           (filter some?))
      d18O-vs-dD-text (->> glued
                           ds/rows
                           (map (fn [row-data]
                                  (let [dD   (row-data "dD")
                                        d18O (row-data "d18O")]
                                    (if (or (nil? dD)
                                            (nil? d18O))
                                      nil
                                      [d18O
                                       dD
                                       (tick/format (tick/formatter "yy")
                                                    (row-data "Date"))
                                       {:tooltip (str (row-data "Date"))
                                        #_#_#_#_
                                        :stroke  (if (row-data "Above?")
                                                   "#8008"
                                                   "#0808")
                                        :fill    (quickthing/color-cycle (/ (mod (row-data "Day")
                                                                                 365)
                                                                            365))}]))))
                           (filter some?))]
  (let [isotope-axis (-> (quickthing/primary-axis d18O-vs-dD
                                                  {:width       width
                                                   :height      height
                                                   :margin-frac 0.04
                                                   :x-name      "d18O"
                                                   :y-name      "dD"
                                                   :title  plot-title
                                                   :legend      [["January" {:fill (-> 15
                                                                                       (/ 365)
                                                                                       quickthing/color-cycle)}]
                                                                 ["February" {:fill (-> 46
                                                                                        (/ 365)
                                                                                        quickthing/color-cycle)}]
                                                                 ["March" {:fill (-> 74
                                                                                     (/ 365)
                                                                                     quickthing/color-cycle)}]
                                                                 ["Arpril" {:fill (-> 105
                                                                                      (/ 365)
                                                                                      quickthing/color-cycle)}]
                                                                 ["May" {:fill (-> 135
                                                                                   (/ 365)
                                                                                   quickthing/color-cycle)}]
                                                                 ["June" {:fill (-> 166
                                                                                    (/ 365)
                                                                                    quickthing/color-cycle)}]
                                                                 ["July" {:fill (-> 196
                                                                                    (/ 365)
                                                                                    quickthing/color-cycle)}]
                                                                 ["August" {:fill (-> 227
                                                                                      (/ 365)
                                                                                      quickthing/color-cycle)}]
                                                                 ["September" {:fill (-> 2288
                                                                                         (/ 365)
                                                                                         quickthing/color-cycle)}]
                                                                 ["October" {:fill (-> 315
                                                                                       (/ 365)
                                                                                       quickthing/color-cycle)}]
                                                                 ["November" {:fill (-> 319
                                                                                        (/ 365)
                                                                                        quickthing/color-cycle)}]
                                                                 ["December" {:fill (-> 349
                                                                                        (/ 365)
                                                                                        quickthing/color-cycle)}]]})
                         #_#_(assoc-in [:x-axis
                                        :visible]
                                       false)
                         (assoc-in [:y-axis
                                    :visible]
                                   false))]
    (let [isotope-plot (-> isotope-axis
                           (update :data
                                   #(into %
                                          (quickthing/circles d18O-vs-dD
                                                                         {:scale 35})))
                           (update :data
                                   #(into %
                                          (quickthing/labels d18O-vs-dD-text
                                                                      {:scale 35})))
                           viz/svg-plot2d-cartesian)]
      (spit "d18O-vs-dD-months-years.svg"
            (-> (svg/group {}
                           isotope-plot)
                (quickthing/svg-wrap [width
                                      height]
                                     width)
                quickthing/svg2xml)))))

#_
(let [plot-title "d18O vs dD (by ENSO) by monsoon strength"
      width           1500
      height          1500
      max-above       (->> "column-0"
                           glued
                           (apply max))
      max-below       (->> "column-1"
                           glued
                           (apply max))
      d18O-vs-dD      (->> glued
                           ds/rows
                           (map (fn [row-data]
                                  (let [dD         (row-data "dD")
                                        d18O       (row-data "d18O")
                                        above-norm (-> "column-0"
                                                       row-data
                                                       (/ max-above)
                                                       clojure.math/sqrt)
                                        below-norm (-> "column-1"
                                                       row-data
                                                       (/ max-above)
                                                       clojure.math/sqrt)]
                                    (if (or (nil? dD)
                                            (nil? d18O))
                                      nil
                                      [d18O
                                       dD
                                       nil
                                       {:tooltip (str (row-data "Date"))
                                        :fill    (if #_(not (row-data "Above?"))
                                                     false ;; dummy to insert filters
                                                     "none"
                                                     (quickthing/from-colorvec quickthing/red-blue-colors
                                                                               (+ 0.5
                                                                                  (* 0.5
                                                                                     (- above-norm
                                                                                        below-norm)))))}]))))
                           (filter some?))
      d18O-vs-dD-text (->> glued
                           ds/rows
                           (map (fn [row-data]
                                  (let [dD   (row-data "dD")
                                        d18O (row-data "d18O")]
                                    (if (or (nil? dD)
                                            (nil? d18O))
                                      nil
                                      [d18O
                                       dD
                                       (tick/format (tick/formatter "yy")
                                                    (row-data "Date"))
                                       {:tooltip (str (row-data "Date"))
                                        #_#_#_#_
                                        :stroke  (if (row-data "Above?")
                                                   "#8008"
                                                   "#0808")
                                        :fill    (quickthing/color-cycle (/ (mod (row-data "Day")
                                                                                 365)
                                                                            365))}]))))
                           (filter some?))]
  (let [isotope-axis (-> (quickthing/primary-axis d18O-vs-dD
                                                  {:width  width
                                                   :height height
                                                   :x-name "d18O"
                                                   :y-name "dD"
                                                   :title  plot-title
                                                   :legend [["SummerMonsoon" {:fill (last quickthing/red-blue-colors)}]
                                                            ["WinterMonsoon" {:fill (first quickthing/red-blue-colors)}]
                                                            ["(sqrt of index values)" {:stroke "none"}]]})
                         #_#_(assoc-in [:x-axis
                                        :visible]
                                       false)
                         (assoc-in [:y-axis
                                    :visible]
                                   false))]
    (let [isotope-plot (-> isotope-axis
                           (update :data
                                   #(into %
                                          (quickthing/adjustable-circles d18O-vs-dD
                                                                         {:scale 7})))
                           #_
                           (update :data
                                   #(into %
                                          (quickthing/adjustable-text d18O-vs-dD-text
                                                                      {:scale 7})))
                           viz/svg-plot2d-cartesian)]
      (spit "d18O-vs-dD-monsoons.svg"
            (-> (svg/group {}
                           isotope-plot)
                (quickthing/svg-wrap [width
                                      height]
                                     width)
                quickthing/svg2xml)))))

#_
(let [plot-title "d18O vs dD (by ENSO)"
      width           1500
      height          1500
      max-enso       (->> "ENSO"
                          glued 
                          (mapv abs)
                          (apply max))
      d18O-vs-dD      (->> glued
                           ds/rows
                           (map (fn [row-data]
                                  (let [dD         (row-data "dD")
                                        d18O       (row-data "d18O")
                                        enso-norm (-> "ENSO"
                                                       row-data
                                                       (/ max-enso))]
                                    (if (or (nil? dD)
                                            (nil? d18O))
                                      nil
                                      [d18O
                                       dD
                                       nil
                                       {:tooltip (str (row-data "Date"))
                                        :fill    (if #_(not (row-data "Above?"))
                                                     false ;; dummy to insert filters
                                                     "none"
                                                     (quickthing/from-colorvec quickthing/red-blue-colors
                                                                               (->> enso-norm
                                                                                    abs
                                                                                    clojure.math/sqrt
                                                                                    (* (if (pos? enso-norm)
                                                                                         1.0
                                                                                         -1.0))
                                                                                    (* 0.5)
                                                                                    (+ 0.5))))}]))))
                           (filter some?))
      d18O-vs-dD-text (->> glued
                           ds/rows
                           (map (fn [row-data]
                                  (let [dD   (row-data "dD")
                                        d18O (row-data "d18O")]
                                    (if (or (nil? dD)
                                            (nil? d18O))
                                      nil
                                      [d18O
                                       dD
                                       (tick/format (tick/formatter "yy")
                                                    (row-data "Date"))
                                       {:tooltip (str (row-data "Date"))
                                        #_#_#_#_
                                        :stroke  (if (row-data "Above?")
                                                   "#8008"
                                                   "#0808")
                                        :fill    (quickthing/color-cycle (/ (mod (row-data "Day")
                                                                                 365)
                                                                            365))}]))))
                           (filter some?))]
  (let [isotope-axis (-> (quickthing/primary-axis d18O-vs-dD
                                                  {:width  width
                                                   :height height
                                                   :x-name "d18O"
                                                   :y-name "dD"
                                                   :title  plot-title ;;"d18O vs dD (by climate index)"
                                                   :legend [["El Nino" {:fill (last quickthing/red-blue-colors)}]
                                                            ["La Nina" {:fill (first quickthing/red-blue-colors)}]
                                                            ["(sqrt of index values)" {:stroke "none"}]]})
                         #_#_(assoc-in [:x-axis
                                        :visible]
                                       false)
                         (assoc-in [:y-axis
                                    :visible]
                                   false))]
    (let [isotope-plot (-> isotope-axis
                           (update :data
                                   #(into %
                                          (quickthing/adjustable-circles d18O-vs-dD
                                                                         {:scale 7})))
                           #_
                           (update :data
                                   #(into %
                                          (quickthing/adjustable-text d18O-vs-dD-text
                                                                      {:scale 7})))
                           viz/svg-plot2d-cartesian)]
      (spit "d18O-vs-dD-enso.svg"
            (-> (svg/group {}
                           isotope-plot)
                (quickthing/svg-wrap [width
                                      height]
                                     width)
                quickthing/svg2xml)))))

#_
(->> "Rain (mm)"
     glued
     (filter some?)
     (apply max))
;; => 137.6

#_
(let [plot-title "d18O vs dD (by rain amount - mm)"
      width           1500
      height          1500
      max-rain        (->> "Rain (mm)"
                          glued
                          (filter some?)
                          (apply max))
      d18O-vs-dD      (->> glued
                           ds/rows
                           (map (fn [row-data]
                                  (let [dD         (row-data "dD")
                                        d18O       (row-data "d18O")
                                        rain-norm  (some-> (row-data "Rain (mm)")
                                                           (/ max-rain)
                                                           (clojure.math/sqrt)
                                                           (/ 2.0)
                                                           (+ 0.5))]
                                    (if (or (nil? dD)
                                            (nil? d18O))
                                      nil
                                      [d18O
                                       dD
                                       nil
                                       {:tooltip (str (row-data "Date"))
                                        :fill    (if (nil? rain-norm)
                                                     "none"
                                                     (quickthing/from-colorvec quickthing/red-blue-colors
                                                                               rain-norm))}]))))
                           (filter some?))
      d18O-vs-dD-text (->> glued
                           ds/rows
                           (map (fn [row-data]
                                  (let [dD   (row-data "dD")
                                        d18O (row-data "d18O")]
                                    (if (or (nil? dD)
                                            (nil? d18O))
                                      nil
                                      [d18O
                                       dD
                                       (tick/format (tick/formatter "yy")
                                                    (row-data "Date"))
                                       {:tooltip (str (row-data "Date"))
                                        #_#_#_#_
                                        :stroke  (if (row-data "Above?")
                                                   "#8008"
                                                   "#0808")
                                        :fill    (quickthing/color-cycle (/ (mod (row-data "Day")
                                                                                 365)
                                                                            365))}]))))
                           (filter some?))]
  (let [isotope-axis (-> (quickthing/primary-axis d18O-vs-dD
                                                  {:width  width
                                                   :height height
                                                   :x-name "d18O"
                                                   :y-name "dD"
                                                   :title  plot-title
                                                   :legend [["Rain Amount" {:fill (last quickthing/red-blue-colors)}]
                                                            ["(sqrt of normalized amount)" {:stroke "none"}]]})
                         #_#_(assoc-in [:x-axis
                                        :visible]
                                       false)
                         (assoc-in [:y-axis
                                    :visible]
                                   false))]
    (let [isotope-plot (-> isotope-axis
                           (update :data
                                   #(into %
                                          (quickthing/adjustable-circles d18O-vs-dD
                                                                         {:scale 7})))
                           #_
                           (update :data
                                   #(into %
                                          (quickthing/adjustable-text d18O-vs-dD-text
                                                                      {:scale 7})))
                           viz/svg-plot2d-cartesian)]
      (spit "d18O-vs-dD-rainmm.svg"
            (-> (svg/group {}
                           isotope-plot)
                (quickthing/svg-wrap [width
                                      height]
                                     width)
                quickthing/svg2xml)))))

#_
(let [excess (->> "D-excess"
                  glued
                  (into [])
                  (filter some?)
                  sort
                  (into []))]
  (get excess (int (/ (count excess)
                      2))))

#_
(->> "D-excess"
     glued
     vec)

#_
(let [plot-title "D-excess"
      width           1500
      height          1500
      max-rain        (->> "Rain (mm)"
                          glued
                          (filter some?)
                          (apply max))
      excess-binned (->> "D-excess"
                         glued
                         (filter some?)
                         sort
                         (drop 20)
                         (drop-last 25)
                         quickthing/bin-data)
      #_#_#_#_
      d18O-vs-dD      (->> glued
                           ds/rows
                           (map (fn [row-data]
                                  (let [dD         (row-data "dD")
                                        d18O       (row-data "d18O")
                                        rain-norm  (some-> (row-data "Rain (mm)")
                                                           (/ max-rain)
                                                           (clojure.math/sqrt)
                                                           (/ 2.0)
                                                           (+ 0.5))]
                                    (if (or (nil? dD)
                                            (nil? d18O))
                                      nil
                                      [d18O
                                       dD
                                       nil
                                       {:tooltip (str (row-data "Date"))
                                        :fill    (if (nil? rain-norm)
                                                     "none"
                                                     (quickthing/from-colorvec quickthing/red-blue-colors
                                                                               rain-norm))}]))))
                           (filter some?))
      d18O-vs-dD-text (->> glued
                           ds/rows
                           (map (fn [row-data]
                                  (let [dD   (row-data "dD")
                                        d18O (row-data "d18O")]
                                    (if (or (nil? dD)
                                            (nil? d18O))
                                      nil
                                      [d18O
                                       dD
                                       (tick/format (tick/formatter "yy")
                                                    (row-data "Date"))
                                       {:tooltip (str (row-data "Date"))
                                        #_#_#_#_
                                        :stroke  (if (row-data "Above?")
                                                   "#8008"
                                                   "#0808")
                                        :fill    (quickthing/color-cycle (/ (mod (row-data "Day")
                                                                                 365)
                                                                            365))}]))))
                           (filter some?))]
  (let [bar-width         (* 0.5
                             (/ width
                                (->> excess-binned
                                     count)))
        excess-axis (-> (quickthing/primary-axis excess-binned
                                                 {:width  width
                                                  :height height
                                                  :x-name "d18O"
                                                  :y-name "dD"
                                                  :title  plot-title
                                                  :legend [["Rain Amount" {:fill (last quickthing/red-blue-colors)}]
                                                           ["(sqrt of normalized amount)" {:stroke "none"}]]})
                         #_#_(assoc-in [:x-axis
                                        :visible]
                                       false)
                         (assoc-in [:y-axis
                                    :visible]
                                   false))]
    (let [excess-plot (-> excess-axis
                           (update :data
                                   #(into %
                                          (quickthing/bars excess-binned
                                                           {:attribs {:stroke-width bar-width
                                                                      :stroke       "#000000"}})))
                           #_
                           (update :data
                                   #(into %
                                          (quickthing/adjustable-text d18O-vs-dD-text
                                                                      {:scale 7})))
                           viz/svg-plot2d-cartesian)]
      (spit (str plot-title
                 ".svg")
            (-> (svg/group {}
                           excess-plot)
                (quickthing/svg-wrap [width
                                      height]
                                     width)
                quickthing/svg2xml)))))


#_(let [plot-title          "d18O-by-monsoons"
      right-to-left-ecdf? false
      width               1500
      height              1500
      max-rain            (->> "d18O"
                               glued
                               (filter some?)
                               (apply max))
      d18O                (->> "d18O"
                               glued
                               (filter some?)
                               sort)
      d18O-above          (->> "d18O"
                               ((ds/filter-column glued
                                                  "Above?"
                                                  #(identity %)))
                               (filter some?)
                               sort) ;;699 points
      d18O-below          (->> "d18O"
                               ((ds/filter-column glued
                                                  "Above?"
                                                  #(not %)))
                               (filter some?)
                               sort)] ;;911 points
  (let [d18O-binned       (-> d18O
                              quickthing/bin-data)
        d18O-above-binned (->> d18O-above
                               quickthing/bin-data
                               (mapv (fn [[coord
                                           value]]
                                       [coord
                                        (* 1.0 ;; fudge factor...
                                           value)])))
        d18O-below-binned (-> d18O-below
                              quickthing/bin-data)]
    (let [bar-width (* 0.5
                       (/ width
                          (->> d18O-binned
                               count)))
          d18O-axis (-> (quickthing/primary-axis d18O-binned
                                                 {:width  width
                                                  :height height
                                                  :x-name "d18O"
                                                  :y-name "dD"
                                                  :title  plot-title
                                                  :legend [["SummerMonsoon" {:fill (last quickthing/red-blue-colors)}]
                                                           ["WinterMonsoon" {:fill (first quickthing/red-blue-colors)}]]})
                        (assoc-in [:x-axis
                                   :major]
                                  (range -19
                                         8))
                        (assoc :grid
                               nil))
          ecdf-axis (-> (quickthing/secondary-axis [[(first d18O)
                                                     0.0]
                                                    [(last d18O)
                                                     1.0]]
                                                   {:width            width
                                                    :height           height
                                                    :y-name           "Cumulative Probability"
                                                    :y-breathing-room 0.0})
                        (assoc-in [:x-axis
                                   :major]
                                  [])
                        (assoc-in [:y-axis
                                   :major]
                                  [0.0
                                   0.5
                                   1.0])
                        (assoc :grid {}))]
                        (let [excess-plot (-> d18O-axis
                                              (update :data
                                                      #(into %
                                                             (quickthing/bars d18O-binned
                                                                              {:attribs {:stroke-width bar-width
                                                                                         :stroke       "#0001"
                                                                                         :fill         "#0000"}})))
                                              (update :data
                                                      #(into %
                                                             (quickthing/bars d18O-above-binned
                                                                              {:attribs {:stroke-width bar-width
                                                                                         :stroke       "#f003"
                                                                                         :fill         "#0000"}})))
                                              (update :data
                                                      #(into %
                                                             (quickthing/bars d18O-below-binned
                                                                              {:attribs {:stroke-width bar-width
                                                                                         :stroke       "#00f3"
                                                                                         :fill         "#0000"}})))
                                              #_
                                              (update :data
                                                      #(into %
                                                             (quickthing/adjustable-text d18O-vs-dD-text
                                                                                         {:scale 7})))
                                              viz/svg-plot2d-cartesian)
                              ecdf-plot   (-> ecdf-axis
                                              (update :data
                                                      #(into %
                                                             (quickthing/solid-line (quickthing/ecdf d18O-above
                                                                                                     {:reversed? right-to-left-ecdf?})
                                                                                    {:attribs {#_#_:stroke-width bar-width
                                                                                               :stroke           "#00f7"
                                                                                               :fill             "#0000"}})))
                                              (update :data
                                                      #(into %
                                                             (quickthing/solid-line (quickthing/ecdf d18O-below
                                                                                                     {:reversed? right-to-left-ecdf?})
                                                                                    {:attribs {#_#_:stroke-width bar-width
                                                                                               :stroke           "#f007"
                                                                                               :fill             "#0000"}})))
                                              (update :data
                                                      #(into %
                                                             (quickthing/solid-line (quickthing/ecdf d18O
                                                                                                     {:reversed? right-to-left-ecdf?})
                                                                                    {:attribs {#_#_:stroke-width bar-width
                                                                                               :stroke           "#0007"
                                                                                               :fill             "#0000"}})))
                                              viz/svg-plot2d-cartesian)]
                          (spit (str plot-title
                                     ".svg")
                                (-> (svg/group {}
                                               ecdf-plot
                                               excess-plot)
                                    (quickthing/svg-wrap [width
                                                          height]
                                                         width)
                                    quickthing/svg2xml))))))


#_
(let [plot-title "d18O-by-enso"
      right-to-left-ecdf? false
      width      1500
      height     1500
      max-rain   (->> "d18O"
                      glued
                      (filter some?)
                      (apply max))
      d18O       (->> "d18O"
                      glued
                      (filter some?)
                      sort)
      d18O-above (->> "d18O"
                      ((ds/filter-column glued
                                         "ENSO"
                                         #(pos? %)))
                      (filter some?)
                      sort)
      d18O-below (->> "d18O"
                      ((ds/filter-column glued
                                         "ENSO"
                                         #(neg? %)))
                      (filter some?)
                      sort)]
  (let [d18O-binned       (-> d18O
                              quickthing/bin-data)
        d18O-above-binned (->> d18O-above
                              quickthing/bin-data
                              (mapv (fn [[coord
                                          value]]
                                      [coord
                                       (* 1.0 ;; fudge factor...
                                          value)])))
        d18O-below-binned (-> d18O-below
                              quickthing/bin-data)]
    (let [bar-width (* 0.5
                       (/ width
                          (->> d18O-binned
                               count)))
          d18O-axis (-> (quickthing/primary-axis d18O-binned
                                                 {:width  width
                                                  :height height
                                                  :x-name "d18O"
                                                  :y-name "dD"
                                                  :title  plot-title
                                                  :legend [["Rain Amount" {:fill (last quickthing/red-blue-colors)}]
                                                           ["(sqrt of normalized amount)" {:stroke "none"}]]})
                        #_#_(assoc-in [:x-axis
                                       :visible]
                                      false)
                        (assoc-in [:y-axis
                                   :visible]
                                  false))
          ecdf-axis (-> (quickthing/secondary-axis [[(first d18O)
                                                     0.0]
                                                    [(last d18O)
                                                     1.0]]
                                                   {:width            width
                                                    :height           height
                                                    :y-name           "Cumulative Probability"
                                                    :y-breathing-room 0.0}))]
      (let [excess-plot (-> d18O-axis
                            (update :data
                                    #(into %
                                           (quickthing/bars d18O-binned
                                                            {:attribs {:stroke-width bar-width
                                                                       :stroke       "#0002"
                                                                       :fill         "#0000"}})))
                            (update :data
                                    #(into %
                                           (quickthing/bars d18O-above-binned
                                                            {:attribs {:stroke-width bar-width
                                                                       :stroke       "#00f2"
                                                                       :fill         "#0000"}})))
                            (update :data
                                    #(into %
                                           (quickthing/bars d18O-below-binned
                                                            {:attribs {:stroke-width bar-width
                                                                       :stroke       "#f002"
                                                                       :fill         "#0000"}})))
                            #_
                            (update :data
                                    #(into %
                                           (quickthing/adjustable-text d18O-vs-dD-text
                                                                       {:scale 7})))
                            viz/svg-plot2d-cartesian)
            ecdf-plot   (-> ecdf-axis
                            (update :data
                                    #(into %
                                           (quickthing/solid-line (quickthing/ecdf d18O-above
                                                                                   {:reversed? right-to-left-ecdf?})
                                                                  {:attribs {#_#_:stroke-width bar-width
                                                                             :stroke           "#00f7"
                                                                             :fill             "#0000"}})))
                            (update :data
                                    #(into %
                                           (quickthing/solid-line (quickthing/ecdf d18O-below
                                                                                   {:reversed? right-to-left-ecdf?})
                                                                  {:attribs {#_#_:stroke-width bar-width
                                                                             :stroke           "#f007"
                                                                             :fill             "#0000"}})))
                            (update :data
                                    #(into %
                                           (quickthing/solid-line (quickthing/ecdf d18O
                                                                                   {:reversed? right-to-left-ecdf?})
                                                                  {:attribs {#_#_:stroke-width bar-width
                                                                             :stroke           "#0007"
                                                                             :fill             "#0000"}})))
                            viz/svg-plot2d-cartesian)]
        (spit (str plot-title
                   ".svg")
              (-> (svg/group {}
                             ecdf-plot
                             excess-plot)
                  (quickthing/svg-wrap [width
                                        height]
                                       width)
                  quickthing/svg2xml))))))


#_
(let [plot-title          "d18O-vs-rainfall"
      right-to-left-ecdf? false
      width               1500
      height              1500
      data-table          (-> glued
                              (ds/drop-missing "Rain (mm)")
                              (ds/drop-missing "d18O"))
      max-above           (->> "column-0"
                               data-table
                               (apply max))
      max-below           (->> "column-1"
                               data-table
                               (apply max))] 
  (let [d18O-vs-rain (->> data-table
                         ds/rows
                         (mapv (fn [row-data]
                                (let [above-norm (-> "column-0"
                                                             row-data
                                                             (/ max-above)
                                                             clojure.math/sqrt)
                                      below-norm         (-> "column-1"
                                                     row-data
                                                     (/ max-above)
                                                     clojure.math/sqrt)]
                                  [(row-data #_"D-excess" "d18O")
                                   (row-data "Rain (mm)")
                                   nil
                                   {:tooltip (str (row-data "Date"))
                                    :fill    (if (> 0.5
                                                    (abs (- above-norm
                                                            below-norm)))
                                               #_
                                                 false ;; dummy to insert filters
                                                 "none"
                                                 (quickthing/from-colorvec quickthing/red-blue-colors
                                                                           (+ 0.5
                                                                              (* 0.5
                                                                                 (- above-norm
                                                                                    below-norm)))))}]))))]
        (let [axis (-> (quickthing/primary-axis d18O-vs-rain
                                                {:width  width
                                                 :height height
                                                 :x-name "d18O"
                                                 :y-name "dD"
                                                 :title  plot-title
                                                 :legend [["Rain Amount" {:fill (last quickthing/red-blue-colors)}]
                                                          ["(sqrt of normalized amount)" {:stroke "none"}]]})
                       #_#_(assoc-in [:x-axis
                                      :visible]
                                     false)
                       (assoc-in [:y-axis
                                  :visible]
                                 false))] 
          (let [d18O-rain-plot (-> axis
                                   (update :data
                                           #(into %
                                                  (quickthing/adjustable-circles d18O-vs-rain 
                                                                                 {:scale   20
                                                                                  :attribs {#_#_#_#_:stroke "#0002"
                                                                                            :fill           "#0000"}})))
                                   viz/svg-plot2d-cartesian)]
            (spit (str plot-title
                       ".svg")
                  (-> (svg/group {}
                                 d18O-rain-plot)
                      (quickthing/svg-wrap [width
                                            height]
                                           width)
                      quickthing/svg2xml))))))
