(ns uglyboids.vision
  (:gen-class)
  (:use (uglyboids.vision params floodfill shape-detection)
        uglyboids.physics-params
        seesaw.core
        seesaw.graphics)
  (:import (java.io File)
           (java.awt Dimension Color Graphics)
           (java.awt.image BufferedImage)
           (javax.imageio ImageIO)
           (javax.swing JFrame JPanel)))

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
(def max-y (- ground-level 2))

;; for tracking assignment of pixels to shapes
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
                 :radius (:radius-px (:red-bird bird-attrs))
                 :pos mid-pt}
      :blue-bird {:shape :circle
                  :radius (:radius-px (:blue-bird bird-attrs))
                  :pos mid-pt}
      :yellow-bird {:shape :circle
                    :radius (:radius-px (:yellow-bird bird-attrs))
                    :pos mid-pt}
      :pig {:shape :circle
            :radius (quot (max (- x-hi x-lo) (- y-hi y-lo)) 2)
            :pos mid-pt}
      :static-surface (shape-from-coords coords false x-range y-range ground-level)
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
    (loop [pts (for [y (reverse (range min-y (inc max-y)))
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
  [shapes fuzz]
  (dbg "ADJUSTING SHAPES")
  ;; loop through shapes sorted by bottom position going upwards.
  ;; REMEMBER: higher y coordinates are further "down" in the world!
  ;; TODO: would be a bit cleaner if went through and deref'd all the :geoms first
  (loop [upward (sort-by #(- (second (:y-range %)))
                         shapes)
         downward []]
    (if (seq upward)
      (let [shape (first upward)
            [x-lo x-hi] (:x-range shape)
            [y-lo y-hi] (:y-range shape)
            x-span (- x-hi x-lo)
            ;; initial filter only based on bounding box
            ;; NOTE: we will only adjust down to objects with larger x-span
            ;; NOTE: this is an approximation, bounding box won't get all candidates
            below (filter (fn [s]
                            (let [[s-x-lo s-x-hi] (:x-range s)
                                  [s-y-lo s-y-hi] (:y-range s)
                                  s-x-span (- s-x-hi s-x-lo)]
                              (and (>= s-x-hi x-lo) ;; not off to left
                                   (<= s-x-lo x-hi) ;; not off to right
                                   (>= s-x-span x-span) ;; wider than current
                                   (<= (abs (- s-y-lo y-hi)) fuzz)))) ;; not far below
                          downward)
            above (filter (fn [s]
                            (let [[s-x-lo s-x-hi] (:x-range s)
                                  [s-y-lo s-y-hi] (:y-range s)
                                  s-x-span (- s-x-hi s-x-lo)]
                              (and (>= s-x-hi x-lo) ;; not off to left
                                   (<= s-x-lo x-hi) ;; not off to right
                                   (>= s-x-span x-span) ;; wider than current
                                   (<= (abs (- y-lo s-y-hi)) fuzz)))) ;; not far above
                          (next upward))
            ;; for each 'below' object,
            ;;   for each of the two lowest vertices of 'shape'
            ;;     find the top edge below (closest vertex, and opposite closest)
            ;;     intersect vertex with the edge straight down
            ;;     if drop < threshold, modify our coordinates (don't bother with bounds)
            ;; similarly for wider shapes above, pull our top vertices up
            geom @(:geom shape)
            low-verts (if (= :circle (:shape geom))
                        (list (v-add (:pos geom) [0 (:radius geom)]))
                        (take 2 (sort-by #(- (second %)) (:coords geom))))
            high-verts (if (= :circle (:shape geom))
                         (list (v-add (:pos geom) [0 (- (:radius geom))]))
                         (take 2 (sort-by #(second %) (:coords geom))))
            shape-adj (loop [others (reverse below) ;; closest first
                             shape shape]
                        (if-let [one (first others)]
                          ;; TODO - static surface is a list of geoms
                          (if (= (:type one) :static-surface)
                            (recur (next others) shape)
                            (let [g1 @(:geom one)
                                  sh (:shape g1)
                                  cc (:coords g1)]
                              (if (= sh :circle) ;; TODO - skip for now
                                (recur (next others) shape)
                                ;; ok, go: check each of the low-verts
                                (let [new-lo (snap-vertices-to-other-shape low-verts cc fuzz *debug*)]
                                  ;; update vertices in shape
                                  (let [old-with-new (zipmap low-verts new-lo)
                                        new-geom (if (= :circle (:shape geom))
                                                   (let [[_ o-y] (first low-verts)
                                                         [_ n-y] (first new-lo)
                                                         shift (- n-y o-y)]
                                                     (update-in geom [:pos] v-add [0 shift]))
                                                   (update-in geom [:coords] #(replace old-with-new %)))]
                                    (recur (next others)
                                           (assoc shape :geom (atom new-geom))))))))
                          ;; done
                          shape))
            shape-adj (loop [others above ;; closest first
                             shape shape-adj]
                        (if-let [one (first others)]
                          ;; TODO - static surface is a list of geoms
                          (if (= (:type one) :static-surface)
                            (recur (next others) shape)
                            (let [g1 @(:geom one)
                                  sh (:shape g1)
                                  cc (:coords g1)]
                              (if (= sh :circle) ;; TODO - skip for now
                                (recur (next others) shape)
                                ;; ok, go: check each of the high-verts
                                (let [new-hi (snap-vertices-to-other-shape high-verts cc fuzz *debug*)]
                                  ;; update vertices in shape
                                  (let [old-with-new (zipmap high-verts new-hi)
                                        new-geom (if (= :circle (:shape geom))
                                                   (let [[_ o-y] (first high-verts)
                                                         [_ n-y] (first new-hi)
                                                         shift (- n-y o-y)]
                                                     (update-in geom [:pos] v-add [0 shift]))
                                                   (update-in geom [:coords] #(replace old-with-new %)))]
                                    (recur (next others)
                                           (assoc shape :geom (atom new-geom))))))))
                          ;; done
                          shape))
            ;; TODO: if nothing below, test ground level
            ]
        (dbg {:type (:type shape)
              :x-range (:x-range shape)
              :y-range (:y-range shape)
              :below (map :type below)
              :above (map :type above)})
        ;(dbg "low-verts: " low-verts)
        ;(dbg "adj-geom: " (:geom shape-adj))
        (recur (next upward) (conj downward shape-adj)))
      ;; finished
      downward)))

(defn scene-from-shapes
  [shapes]
  ;; check for level failed screen (big pig)
  (if (some (fn [s]
              (when (= (:type s) :pig)
                (let [[x-lo x-hi] (:x-range s)
                      [y-lo y-hi] (:y-range s)
                      span (max (- x-hi x-lo)
                                (- y-hi y-lo))]
                  (when (>= span 90) true))))
            shapes)
    {:state :failed}
    (let [all-birds (filter #(bird-types (:type %)) shapes)
          ;; birds can remain to right of screen after landing
          birds (filter #(< (first (:mid-pt %))
                            (/ px-width 3))
                        all-birds)
          pigs (filter #(= (:type %) :pig) shapes)]
      (if (zero? (count pigs))
        {:state :success}
        (if (zero? (count birds))
          {:state :failed}
          ;; have pigs and birds, so in-play
          ;; method to find focus point: the highest bird
          (let [launch-bird (apply min-key #(second (:mid-pt %)) birds)
                launch-pt (if (= (:type launch-bird) :red-bird)
                           (v-add (:mid-pt launch-bird) [0 6])
                           (:mid-pt launch-bird))
                ;; order by distance from slingshot
                bird-order (sort-by (fn [s]
                                      (let [pos (:pos @(:geom s))]
                                        (abs (- (first pos) (first launch-pt)))))
                                    birds)
                ;; get rid of any objects around sling as can interfere with birds
                sling-stuff (filter (fn [s]
                                      (let [[x-lo x-hi] (:x-range s)
                                            [y-lo y-hi] (:y-range s)]
                                        (and (<= (- x-lo 20) (first launch-pt) (+ x-hi 20))
                                             (<= (- y-lo 20) (second launch-pt) (+ y-hi 20)))))
                                    shapes)
                ;; find slingshot - this is a standard to detect world scale
                ;; level 1-1 has large slingshot (small world) vs level 1-3
                sling (some (fn [s]
                              (and (= (:type s) :static-wood)
                                   (let [height (abs (apply - (:y-range s)))]
                                     (when (> height 40) s))))
                            sling-stuff)
                sling-width (when sling
                              (let [span (abs (apply - (:x-range sling)))]
                                (when (<= 20 span 40) (max 25 span))))
                world-scale (if sling-width (/ 30.0 sling-width) 1.0)
                ;; more consistent: set focus point relative to sling top
                focus-pt (if sling-width
                           (let [sling-top [(first (:mid-pt sling))
                                            (first (:y-range sling))]
                                 descent (int (Math/round (* 16 world-scale)))]
                             (v-add sling-top [0 descent]))
                           launch-pt)
                special-ids (set (map :id (concat all-birds sling-stuff)))
                normal-shapes (remove #(special-ids (:id %)) shapes)
                objs (map (fn [s]
                            (let [otype (:type s)
                                  type (if (#{:static-wood :static-surface :ground} otype)
                                         :static otype)
                                  rgb (first (:colors (get object-params otype)))
                                  geom @(:geom s)]
                              (if (= otype :static-surface)
                                (assoc geom
                                  :type type :rgb rgb
                                  :shape :polyline
                                  :coords (concat (:coords geom)
                                                  (take 1 (:coords geom))))
                                (assoc geom
                                  :type type :rgb rgb))))
                             normal-shapes)]
            (println "focus pt:" focus-pt " launch pt:" launch-pt
                     "sling-width:" sling-width "scale:" world-scale)
            {:state :in-play
             :world-scale world-scale
             :start focus-pt
             :birds (map :type bird-order)
             :objs objs}))))))

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
    (let [adj-shapes (adjust-shapes blobs 6)]
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
