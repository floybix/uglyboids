(ns uglyboids.vision
  (use uglyboids.vision-colors
       seesaw.core
       seesaw.graphics)
  (import [java.io File]
          [java.awt Dimension Color Graphics]
          [java.awt.image BufferedImage]
          [javax.imageio ImageIO]
          [javax.swing JFrame JPanel]))

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

;(def cells2d (to-array-2d cells))

(def objs (atom {}))

(defn get-cell
  [[x y]]
  (-> cells (nth y) (nth x)))

(defn r-g-b
  [int-color]
  (let [c (Color. int-color)]
    [(.getRed c) (.getGreen c) (.getBlue c)]))

(defn rgb-int
  [[r g b]]
  (.getRGB (Color. (int r) (int g) (int b))))

(defn color-within-tol?
  [[r g b] [r2 g2 b2] tol]
  (let [dist (+ (. Math pow (- r r2) 2)
                (. Math pow (- g g2) 2)
                (. Math pow (- b b2) 2))]
    (<= dist (* tol tol))))

(defn classify-img!
  []
  (let [^BufferedImage img @orig-img]
    (doseq [[type seed-rgb] seed-colors
            :let [[r g b] seed-rgb
                  seed-int (rgb-int seed-rgb)
                  pxx (atom 0)
                  tol (get color-tolerance type)]
            :when (not= type :sky)]
      (println "Going to classify " type)
      (doseq [x (range min-x (inc max-x))
              y (range min-y (inc max-y))
              :let [cell (get-cell [x y])]
              :when (nil? (:type @cell))]
        (let [xyint (.getRGB img x y)]
          (when (color-within-tol? (r-g-b xyint) seed-rgb tol)
            (swap! cell assoc :type type)
            (swap! pxx inc)
            (.setRGB class-img x y seed-int))))
      (reset! display-img class-img)
      (invoke-later (repaint! (select @the-frame [:#canvas])))
      (println "found " @pxx " pixels within tol " tol))))

(defn neighbour-idx
  [[x y]]
  (set (for [[xd yd] (list [-1 0]
                           [0 -1]
                           [1 0]
                           [0 1])
             :let [xi (+ x xd)
                   yi (+ y yd)]
             :when (and (< -1 xi px-width)
                        (< -1 yi px-height))]
         [xi yi])))

(defn test-xy
  [xy type]
  (let [cell (get-cell xy)
        {t :type
         i :id} @cell]
    (when (and (= t type) (nil? i))
      cell)))

(defn scanline-r
  "adapted from Recursive Scanline Method at
   http://www.codeproject.com/Articles/6017/QuickFill-An-efficient-flood-fill-algorithm"
  [x1 x2 y test mark]
  (when (<= min-y y max-y)
    ;; scan left
    (let [left-xs (doall (for [x (range x1 min-x -1)
                               :while (test [x y])]
                           (do (mark [x y])
                               (dec x))))
          leftmost (if (empty? left-xs) x1 (last left-xs))]
      ;(swank.core/break)
      (when (< leftmost x1)
        (scanline-r leftmost x1 (dec y) test mark)
        (scanline-r leftmost x1 (inc y) test mark))
      ;; scan right
      (let [right-xs (doall (for [x (range x2 max-x)
                                  :while (test [x y])]
                              (do (mark [x y])
                                  (inc x))))
            rightmost (if (empty? right-xs) x2 (last right-xs))]
        (when (> rightmost x2)
          (scanline-r x2 rightmost (dec y) test mark)
          (scanline-r x2 rightmost (inc y) test mark))
        ;; scan betweens
        (let [x1* (if (< leftmost x1) (inc x1) x1)
              x2* (if (> rightmost x2) (dec x2) x2)]
          (loop [x-from x1*
                 x x1*]
            (when (<= x (min x2* max-x))
              ;(println "scanning betweens: " {:x x :x-from x-from :x1* x1*})
              (if (test [x y])
                (do
                  (mark [x y])
                  (recur x-from (inc x)))
                (do
                  (when (> x x-from)
                    (scanline-r x-from (dec x) (dec y) test mark)
                    (scanline-r x-from (dec x) (inc y) test mark))
                  (let [skip-xs (doall (for [x (range (inc x) (inc (min x2* max-x)))
                                             :while (not (test [x y]))]
                                         x))
                        nextx (if (empty? skip-xs) (inc x) (last skip-xs))]
                    (recur nextx nextx)))))))))))

(defn scanline-r-cells
  [x y id type]
  (let [obj (atom {:type type
                   :coords []
                   :x-range [x x]
                   :y-range [y y]})
        test (fn [xy]
               (test-xy xy type))
        minmax (fn [[omin omax] new]
                 [(min omin new) (max omax new)])
        mark (fn [[x y]]
               ;(println "marking cell " x y " id " id " type " type)
               (let [cell (get-cell [x y])]
                 (swap! cell assoc :id id))
               (swap! obj (fn [o]
                            (-> o
                                (update-in [:coords] conj [x y])
                                (update-in [:x-range] minmax x)
                                (update-in [:y-range] minmax y)))))]
    (scanline-r x x y test mark)
    obj))

(defn identify-objects!
  []
  (let [nobj (atom 0)
        ^BufferedImage class-img (BufferedImage. px-width px-height
                                                 BufferedImage/TYPE_INT_RGB)
        black-int (.getRGB Color/BLACK)]
    (doseq [x (range 0 px-width)
            y (range 0 px-height)]
      (.setRGB class-img x y black-int))
    (doseq [[type [r g b]] seed-colors
            :let [my-colors (get ab-colors type)
                  [min-px max-px] (get size-range type)]
            :when (not= type :sky)]
      (println "Going to floodfill " type)
      (doseq [x (range min-x (inc max-x))
              y (range min-y (inc max-y))
              :let [cell (get-cell [x y])]
              :when (test-xy [x y] type)]
        (swap! nobj inc)
        (let [id @nobj
              obj (scanline-r-cells x y id type)
              pxx (count (:coords @obj))]
          (when (<= min-px pxx max-px)
            (swap! objs assoc id obj)
          ;(let [obj (flood-out [x y] type id)]
            (println "object id " id " type " type " was size " pxx
                     " x-range " (:x-range @obj)
                     " y-range " (:y-range @obj))))))))

(defn segment-img!
  []
  (classify-img!)
  (identify-objects!))

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
    (invoke-later
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
                 show!)))))
