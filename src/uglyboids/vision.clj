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

;; ## debugging stuff

(def ^:dynamic *debug* false)

(defn dbg [& args]
  (when *debug* (apply println args)))

(def display-img (atom nil))
(def the-frame (atom nil))
(def -blobs (atom nil))

;; pixel ranges within which to look for shapes

(def min-x 5)
(def max-x (- px-width 10))
(def min-y 150)
(def max-y (+ ground-level 2))

(def cells (vec (for [y (range 0 px-height)]
                  (vec (for [x (range 0 px-width)]
                         (atom nil))))))

(defn get-cell
  [[x y]]
  (-> cells (nth y) (nth x)))

(defn reset-cells!
  []
  (doseq [cell (flatten cells)]
    (reset! cell nil)))

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
  (let [cell (get-cell [x y])]
    (when (nil? @cell)
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

(defn scan-blob
  [img [x y] id type colors tol]
  (let [test (fn [xy]
               (test-xy xy img colors tol))
        mark (fn [[x y]]
               (reset! (get-cell [x y]) id))
        coords (scanline x y test mark [min-x max-x] [min-y max-y])
        coords (dilate-blob coords [min-x max-x] [min-y max-y])
        [[x0 y0] [x1 y1]] (bounding-box coords)]
    (when (seq coords)
      {:type type
       :id id
       :coords coords
       :x-range [x0 x1]
       :y-range [y0 y1]
       :mid-pt [(quot (+ x0 x1) 2)
                (quot (+ y0 y1) 2)]
       })))

(defn static-shapes-from-coords
  "Returns a list of shapes, to be treated as static polylines."
  [coords [x-lo x-hi] [y-lo y-hi]]
  ;; note static shape can be split up arbitrarily, built as polyline
  (let [x-mid (quot (+ x-lo x-hi) 2)
        y-mid (quot (+ y-lo y-hi) 2)
        x-span (- x-hi x-lo)
        y-span (- y-hi y-lo)
        max-span (max x-span y-span)]
    ;; always split if one dimension exceeds some size
    (if (>= max-span 80)
      ;; split it up
      (let [split-fn (if (>= x-span y-span)
                       #(> (first %) x-mid)
                       #(> (second %) y-mid))
            cc-split (group-by split-fn coords)]
        (mapcat (fn [[_ cc]]
                  (let [[[x0 y0] [x1 y1]] (bounding-box cc)]
                    (dbg "SPLIT poly from" [x-lo x-hi] [y-lo y-hi] "at mid-pt" [x-mid y-mid]
                         "into" [x0 x1] [y0 y1])
                    (static-shapes-from-coords cc [x0 x1] [y0 y1])))
                cc-split))
      ;; no need to split, proceed with shape
      (list (shape-from-coords coords false [x-lo x-hi] [y-lo y-hi] ground-level)))))

(defn shape-from-blob
  [{:keys [type coords x-range y-range mid-pt]}]
  (let [[x-lo x-hi] x-range
        [y-lo y-hi] y-range]
    (case type
      :ground (do
                ;; update ground level, used for other objects
                ;(swap! ground-level min y-lo)
                {:shape :poly
                 :coords (list [x-hi y-lo]
                               [x-lo y-lo]
                               [x-lo y-hi]
                               [x-hi y-hi])})
      :red-bird {:shape :circle
                 :radius (:radius (:red-bird bird-attrs))
                 :pos mid-pt}
      :blue-bird {:shape :circle
                  :radius (:radius (:blue-bird bird-attrs))
                  :pos mid-pt}
      :yellow-bird {:shape :circle
                    :radius (:radius (:yellow-bird bird-attrs))
                    :pos mid-pt}
      :pig {:shape :circle
            :radius (quot (max (- x-hi x-lo) (- y-hi y-lo)) 2)
            :pos mid-pt}
      :static-surface (static-shapes-from-coords coords x-range y-range)
      :static-wood (shape-from-coords coords true x-range y-range ground-level)
      ;; else - dynamic blocks
      ;; do not drop edges to ground level
      (shape-from-coords coords true x-range y-range max-y))))

(defn deepCopyBI
  [^BufferedImage bi]
  (let [cm (.getColorModel bi)
        raster (.copyData bi nil)]
    (BufferedImage. cm raster (.isAlphaPremultiplied cm) nil)))

(defn identify-shapes
  [^BufferedImage img]
  (let [id-counter (atom 0)
        ^BufferedImage class-img (deepCopyBI img)
        ok-params (dissoc object-params :tap :trajectory :sky :ground)]
    (when *debug*
      (doseq [y (range 0 px-height)
              x (range 0 px-width)]
        (.setRGB class-img x y
                 (-> (.getRGB class-img x y)
                     (Color.)
                     .darker
                     .darker
                     .getRGB)))
      (reset! display-img class-img)
      (repaint! @the-frame))
    (reset-cells!)
    ;; recursively build up 'blobs'
    (loop [pts (for [y (range min-y (inc max-y))
                     x (range min-x (inc max-x))] [x y])
           blobs []]
      (if (seq pts)
        (let [[x y] (first pts)
              cell (get-cell [x y])]
          (if (nil? @cell)
            (let [xy-rgb (r-g-b (.getRGB img x y))
                  type (detect-type-from-color xy-rgb ok-params)]
              (if (nil? type)
                ;; no detected type here, go to next pixel
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
                           (>= (- x-hi x-lo) 4)
                           (>= (- y-hi y-lo) 4))
                    (do
                      (when *debug*
                        (let [seed-int (rgb-int (first my-colors))]
                          (doseq [[x y] coords]
                            (.setRGB class-img x y seed-int)))
                        (invoke-later (repaint! @the-frame))
                        (dbg "FOUND" type "(" id "), of" pxx "px. "
                             "x-range" (:x-range blob)
                             "y-range" (:y-range blob)))
                      ;; detect shape, but farm the work off to another thread
                      (let [augmented-blob (assoc blob
                                             :geom (if *debug* ;; concurrency is hard to debug
                                                     (atom (shape-from-blob blob))
                                                     (future (shape-from-blob blob))))]
                        (recur (next pts) (conj blobs augmented-blob))))
                    ;; out of size range, ignore
                    (recur (next pts) blobs)))))
            ;; cell already identified, skip
            (recur (next pts) blobs)))
        ;; end of points, return:
        blobs))))

(defn draw-shapes!
  [blobs]
  (let [g (.getGraphics ^BufferedImage @display-img)
        sty (style :foreground Color/YELLOW)]
    (doseq [blob blobs
            :let [type (:type blob)
                  geoms (flatten (list @(:geom blob)))]]
      (doseq [geom geoms
              :let [cc (:coords geom)
                    r (:radius geom)
                    [x y] (:pos geom)]]
        (dbg type geom)
        (case (:shape geom)
          :poly (do (draw g (apply polygon cc) sty)
                    (doseq [[cx cy] cc] (draw g (circle cx cy 2) sty)))
          :circle (draw g (circle x y r) sty)
          nil nil)))
    (repaint! @the-frame)))

(defn adjust-shapes
  [shapes]
  (dbg "ADJUSTING SHAPES")
  shapes)

(defn scene-from-shapes
  [shapes]
  ;; TODO: check for success screen
  (if (some (fn [s]
              (when (= (:type s) :pig)
                (let [[x-lo x-hi] (:x-range s)
                      [y-lo y-hi] (:y-range s)
                      span (max (- x-hi x-lo)
                                (- y-hi y-lo))]
                  (when (>= span 90) true))))
            shapes)
    {:state :failed}
    (let [sling-like (filter (fn [s]
                               (and (= (:type s) :static-wood)
                                    (let [height (abs (apply - (:y-range s)))]
                                      (> height 60))))
                             shapes)
          sling (when (seq sling-like)
                  (apply min-key #(first (:mid-pt %)) sling-like))
          focus-pt (when sling [(first (:mid-pt sling))
                                (+ 10 (first (:y-range sling)))])
          birds (filter #(bird-types (:type %)) shapes)
          ;; alternative method to find focus point: the highest bird
          focus-pt (if (or (nil? sling)
                           (let [span (abs (apply - (:x-range sling)))]
                             (> span 33)))
                     (min-key first (map :mid-pt birds))
                     focus-pt)
          ;; order by distance from slingshot
          bird-order (sort-by (fn [s]
                                (let [pos (:pos @(:geom s))]
                                  (abs (- (first pos) (first focus-pt)))))
                              birds)
          ;; get rid of any mess around sling as can interfere with birds
          sling-mess (filter (fn [s]
                               (let [[x-lo x-hi] (:x-range s)
                                     [y-lo y-hi] (:y-range s)]
                                 (and (<= (- x-lo 10) (first focus-pt) (+ x-hi 10))
                                      (<= (- y-lo 10) (second focus-pt) (+ y-hi 10)))))
                             shapes)
          pigs (filter #(= (:type %) :pig) shapes)
          special-ids (set (map :id (concat birds sling-mess)))
          normal-shapes (remove #(special-ids (:id %)) shapes)
          objs (mapcat (fn [s]
                         (let [otype (:type s)
                               type (if (#{:static-wood :static-surface :ground} otype)
                                      :static otype)]
                           (if (= otype :static-surface)
                             (for [geom-i (flatten (list @(:geom s)))]
                               {:type :static, :shape :polyline
                                :coords (:coords geom-i)})
                             (list (assoc @(:geom s) :type type)))))
                       normal-shapes)
          ]
      {:state (cond (zero? (count pigs)) :success
                    (zero? (count birds)) :failed
                    :else :in-play)
       :start focus-pt
       :birds (map :type bird-order)
       :objs objs})))

(defn paint
  [c g]
  (let [^BufferedImage img @display-img]
    (draw g (image-shape 0 0 img)
          (style :background :black))))

(defn scene-from-image
  [img]
  (when *debug*
    (reset! display-img img)
    (reset! the-frame
            (-> (frame :title "Ugly Boids vision",
                       :width px-width
                       :height px-height
                       :content (border-panel :hgap 5 :vgap 5 :border 5
                                              :center (canvas :id :canvas
                                                              :paint paint))
                       :on-close :dispose)
                show!)))
  (println "scanning image for shapes...")
  (let [blobs (identify-shapes img)]
    (when *debug*
      (reset! -blobs blobs)
      (println "drawing shapes...")
      (draw-shapes! blobs))
    (let [adj-shapes (adjust-shapes blobs)]
      (println "converting to scene...")
      (scene-from-shapes adj-shapes))))

(defn scene-from-image-file
  [img-file]
  (let [img (ImageIO/read (File. img-file))]
    (scene-from-image img)))

(defn -main
  [screenshot & args]
  (binding [*debug* true]
    (println (time (scene-from-image-file screenshot)))))
