(ns uglyboids.vision
  (use [uglyboids.vision params floodfill shape-detection]
       uglyboids.physics-params
       seesaw.core
       seesaw.graphics)
  (import [java.io File]
          [java.awt Dimension Color Graphics]
          [java.awt.image BufferedImage]
          [javax.imageio ImageIO]
          [javax.swing JFrame JPanel]))

(def ^:dynamic *debug* true)

(defn dbg [& args]
  (when *debug* (apply println args)))

(def orig-img (atom nil))
(def display-img (atom nil))
(def the-frame (atom nil))

(def min-x 0)
(def max-x 1885)
(def min-y 200)
(def max-y 1000)

(def cells (vec (for [y (range 0 px-height)]
                  (vec (for [x (range 0 px-width)]
                         (atom {:type nil :id nil}))))))

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

(defn detect-type-from-color
  [rgb type-params]
  (loop [params type-params]
    (if (seq params)
      (let [[type my-params] (first params)
            seed-rgb (first (:colors my-params))
            tol (:tolerance my-params)]
        (if (color-within-tol? rgb seed-rgb tol)
          type
          (recur (next params))))
      ;; exhausted types, return:
      nil)))

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

(defn scan-blob
  [img [x y] id type colors tol]
  (let [test (fn [xy]
               (test-xy xy img colors tol))
        mark (fn [[x y]]
               (let [cell (get-cell [x y])]
                 (swap! cell assoc :id id :type type)))
        coords (scanline x y test mark [min-x max-x] [min-y max-y])
        [[x0 y0] [x1 y1]] (bounding-box coords)]
    {:type type
     :id id
     :coords coords
     :x-range [x0 x1]
     :y-range [y0 y1]
     :mid-pt [(quot (+ x0 x1) 2)
              (quot (+ y0 y1) 2)]
     }))

(defn shape-from-blob
  [{:keys [type coords x-range y-range mid-pt]}]
  (let [[min-x max-x] x-range
        [min-y max-y] y-range]
    (case type
      :red-bird {:shape :circle
                 :radius (:radius (:red bird-attrs))
                 :pos mid-pt}
      :blue-bird {:shape :circle
                  :radius (:radius (:blue bird-attrs))
                  :pos mid-pt}
      :pig {:shape :circle
            :radius (/ (max (- max-x min-x) (- max-y min-y)) 2)
            :pos mid-pt}
      ;; else
      (shape-from-coords coords x-range y-range true))))

(defn deepCopyBI
  [^BufferedImage bi]
  (let [cm (.getColorModel bi)
        raster (.copyData bi nil)]
    (BufferedImage. cm raster (.isAlphaPremultiplied cm) nil)))

(defn identify-blobs
  [^BufferedImage img]
  (let [id-counter (atom 0)
        ^BufferedImage class-img (deepCopyBI img)
        canv (select @the-frame [:#canvas])
        ok-params (dissoc object-params :tap :trajectory :sky
                          :blue-bird :yellow-bird)]
    (when *debug*
      (doseq [y (range 0 px-height)
              x (range 0 px-width)]
        (.setRGB class-img x y (.getRGB (.darker (.darker (Color. (.getRGB class-img x y)))))))
      (reset! display-img class-img)
      (invoke-later (repaint! canv)))
    ;; recursively build up 'blobs'
    (loop [pts (for [y (range 0 px-height)
                     x (range 0 px-width)] [x y])
           blobs []]
      (if (seq pts)
        (let [[x y] (first pts)
              cell (get-cell [x y])]
          (if (nil? (:type @cell))
            (let [xy-rgb (r-g-b (.getRGB img x y))
                  type (detect-type-from-color xy-rgb ok-params)]
              (if (nil? type)
                ;; no detected type here, continue
                (recur (next pts) blobs)
                ;; detected the seed color of an object
                (let [my-params (get ok-params type)
                      my-colors (:colors my-params)
                      tol (:tolerance my-params)
                      id (swap! id-counter inc)
                      blob (scan-blob img [x y] id type my-colors tol)
                      coords (:coords blob)
                      pxx (count coords)
                      [min-px max-px] (:size my-params)
                      [x-lo x-hi] (:x-range blob)
                      [y-lo y-hi] (:y-range blob)]
                  ;; check within allowed size range
                  (if (and (<= min-px pxx max-px)
                           (>= (- x-hi x-lo) 5)
                           (>= (- y-hi y-lo) 5))
                    (do
                      (when *debug*
                        (let [seed-int (rgb-int (first my-colors))]
                          (doseq [[x y] coords]
                            (.setRGB class-img x y seed-int)))
                        (invoke-later (repaint! canv))
                        (dbg "FOUND" type "(" id "), of" pxx "px. "
                             "x-range" (:x-range blob)
                             "y-range" (:y-range blob)))
                      ;; detect shape, but farm the work off to another thread
                      (let [well-known-blob (assoc blob
                                              :geom (if *debug* ;; concurrency is hard to debug
                                                      (atom (shape-from-blob blob))
                                                      (future (shape-from-blob blob))))]
                        (recur (next pts) (conj blobs well-known-blob))))
                    ;; out of size range, ignore
                    (recur (next pts) blobs)))))
            ;; cell already identified, skip
            (recur (next pts) blobs)))
        ;; end of points, return:
        blobs))))

(defn draw-shapes!
  [blobs]
  (dbg "DRAWING SHAPES")
  (let [g (.getGraphics ^BufferedImage @display-img)
        sty (style :foreground Color/YELLOW)]
    (doseq [blob blobs
            :let [type (:type blob)
                  geom @(:geom blob)
                  cc (:coords geom)
                  r (:radius geom)
                  [x y] (:pos geom)]]
            (dbg type geom)
            (case (:shape geom)
              :poly (do (draw g (apply polygon cc) sty)
                        (doseq [[cx cy] cc] (draw g (circle cx cy 2) sty)))
              :circle (draw g (circle x y r) sty)
              nil nil)))
  (repaint! @the-frame))

(defn adjust-shapes
  [blobs]
  (dbg "ADJUSTING SHAPES"))

(defn segment-img
  [img]
  (let [blobs (identify-blobs img)]
    (when *debug*
      (draw-shapes! blobs))
    (let [adj-shapes (adjust-shapes blobs)]
      adj-shapes)))

(defn paint
  [c g]
  (let [^BufferedImage img @display-img]
    (draw g (image-shape 0 0 img)
          (style :background :black))))

(defn -main [& args]
  (let [default "/home/felix/devel/uglyboids/screenshots/angrybirds_1_2.png"
        screenshot (if (seq args) (first args) default)
        img (ImageIO/read (File. screenshot))]
    (reset! orig-img img)
    (reset! display-img img)
    (when *debug*
     (reset! the-frame
             (-> (frame :title "Hello",
                        :width px-width
                        :height (+ px-height 30)
                        :content (border-panel :hgap 5 :vgap 5 :border 5
                                               :center (canvas :id :canvas :background "#BBBBDD"
                                                               :paint paint)
                                               :south (horizontal-panel :items ["Uglyboids..."]))
                        :on-close :dispose)
                 show!)))
    (time (segment-img @orig-img))))
