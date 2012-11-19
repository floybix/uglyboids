(ns uglyboids.vision
  (use [uglyboids.vision params floodfill shape-detection]
       seesaw.core
       seesaw.graphics)
  (import [java.io File]
          [java.awt Dimension Color Graphics]
          [java.awt.image BufferedImage]
          [javax.imageio ImageIO]
          [javax.swing JFrame JPanel]))

(def ^:dynamic *debug* false)

(defn dbg [& args]
  (when *debug* (apply println args)))

(def orig-img (atom nil))
(def display-img (atom nil))
(def the-frame (atom nil))

(def px-height 1080)
(def px-width 1920)

(def min-x 0)
(def max-x 1885)
(def min-y 120)
(def max-y 1050)

(def cells (vec (for [y (range 0 px-height)]
                  (vec (for [x (range 0 px-width)]
                         (atom {:type nil :id nil}))))))

(def objs (atom {}))

(defn get-cell
  [[x y]]
  (-> cells (nth y) (nth x)))

(defn r-g-b
  [int-color]
;  (let [c (Color. int-color)]
;    [(.getRed c) (.getGreen c) (.getBlue c)]))
  [(bit-and (bit-shift-right int-color 16) 0xFF)
   (bit-and (bit-shift-right int-color 8) 0xFF)
   (bit-and int-color 0xFF)])

(defn rgb-int
  [[r g b]]
  (.getRGB (Color. (int r) (int g) (int b))))

(defn color-within-tol?
  [[r g b] [r2 g2 b2] tol]
  (let [dist (+ (. Math pow (- r r2) 2)
                (. Math pow (- g g2) 2)
                (. Math pow (- b b2) 2))]
    (<= dist (* tol tol))))

(def blob-info (atom {}))

(defn test-xy
  "Tests pixel [x y] that it
   (a) is not already classified and
   (b) matches given colors within tolerance."
  [[x y] ^BufferedImage img colors tol]
  (let [cell (get-cell [x y])
        id (:id @cell)]
    (when (nil? id)
      (let [xy-rgb (r-g-b (.getRGB img x y))]
        (loop [colors-to-go colors]
          (when-let [test-rgb (first colors-to-go)]
            (if (color-within-tol? xy-rgb test-rgb tol)
              true
              (recur (next colors-to-go)))))))))

(defn bounding-box
  [coords]
  (loop [x-lower px-width
         y-lower px-height
         x-upper 0
         y-upper 0
         pts coords]
    (if (seq pts)
      (let [[x y] (first pts)
            x (int x)
            y (int y)]
        (recur (min x-lower x)
               (min y-lower y)
               (max x-upper x)
               (max y-upper y)
               (next pts)))
      ;; return:
      [[x-lower y-lower] [x-upper y-upper]])))

(defn scan-object!
  [img [x y] id type colors tol]
  (let [test (fn [xy]
               (test-xy xy img colors tol))
        mark (fn [[x y]]
               (let [cell (get-cell [x y])]
                 (swap! cell assoc :id id :type type)))
        coords (scanline x y test mark [min-x max-x] [min-y max-y])
        [[x0 y0] [x1 y1]] (bounding-box coords)]
    {:type type
     :coords coords
     :x-range [x0 x1]
     :y-range [y0 y1]}))

(defn identify-blobs!
  []
  (let [id-counter (atom 0)
        ^BufferedImage img @orig-img
        ^BufferedImage class-img (BufferedImage. px-width px-height
                                                 BufferedImage/TYPE_INT_RGB)
        black-int (.getRGB Color/BLACK)
        canv (select @the-frame [:#canvas])]
    (reset! objs {})
    (doseq [x (range 0 px-width)
            y (range 0 px-height)]
      (.setRGB class-img x y black-int))
    (reset! display-img class-img)
    (doseq [[type params] object-params
            :let [my-colors (:colors params)
                  seed-rgb (first my-colors)
                  seed-int (rgb-int seed-rgb)
                  tol (:tolerance params)
                  pxx (atom 0)
                  [min-px max-px] (:size params)]
            :when (not (contains? #{:tap :trajectory :sky
                                    :blue-bird :yellow-bird} type))]
      (println "Identifying objects of type " type)
      (doseq [x (range min-x (inc max-x))
              y (range min-y (inc max-y))
              :let [cell (get-cell [x y])]
              :when (nil? (:type @cell))]
        (let [xy-rgb (r-g-b (.getRGB img x y))]
          (when (color-within-tol? xy-rgb seed-rgb tol)
            ;; detected the seed color of this object type
            (let [id (swap! id-counter inc)
                  obj (scan-object! img [x y] id type my-colors tol)
                  coords (:coords obj)
                  pxx (count coords)]
              (if (<= min-px pxx max-px)
                (do
                  (swap! objs assoc id obj)
                  (doseq [[x y] coords]
                    (.setRGB class-img x y seed-int))
                  (dbg "object id " id " type " type " was size " pxx
                       " x-range " (:x-range obj)
                       " y-range " (:y-range obj)))
                ;; otherwise ignore it
                nil
                ;; reset the cells?
                                        ;(doseq [[x y] (:coords @obj)]
                                        ;(reset! (get-cell [x y]) {:id nil :type nil})))
                )))))
      (invoke-later (repaint! canv)))
    (count @objs)))

(defn segment-img!
  []
  (identify-blobs!)
  (binding [*debug* true]
    (doseq [[id obj] @objs]
      (dbg "object of id " id " type " (:type obj))
      (let [type (:type obj)
            shp (shape-from-blob (:coords obj)
                                 (:x-range obj)
                                 (:y-range obj)
                                 true)]
        (dbg shp)))))

(defn paint
  [c g]
  (let [^BufferedImage img @display-img]
    (draw g (image-shape 0 0 img)
          (style :background :black))))

(defn -main [& args]
  (let [screenshot "/home/felix/devel/uglyboids/screenshots/angrybirds_1_2.png"
        img (ImageIO/read (File. screenshot))]
    (reset! orig-img img)
    (reset! display-img img)
    ;(invoke-later
     (reset! the-frame
             (-> (frame :title "Hello",
                        :width px-width
                        :height (+ px-height 30)
                        :content (border-panel :hgap 5 :vgap 5 :border 5
                                               :center (canvas :id :canvas :background "#BBBBDD"
                                                               :paint paint)
                                        ; Some buttons
                                               :south (horizontal-panel :items ["Process the image: "
                                                                                (action :name "Segment it"
                                                                                        :handler (fn [_] (segment-img!)))]))
                        :on-close :dispose)
                                        ;pack!
                 show!))
    (segment-img!)))
