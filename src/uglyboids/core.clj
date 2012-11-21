(ns uglyboids.core
  (:use [cljbox2d.core]
        [cljbox2d.testbed :exclude [world-to-px-scale world-to-px px-to-world]] ;:only [*timestep* info-text ground-body]]
        [cljbox2d.vec2d :only [TWOPI PI in-pi-pi
                               v-mag v-angle v-sub polar-xy]]
        uglyboids.physics-params
        [uglyboids.levels.level-1-2])
  (:require [quil.core :as quil]))

(def pigs (atom []))

(def bird-queue (atom []))

(def bird (atom nil))

(def aiming? (atom false))

(def aspect-ratio (/ px-width px-height))

(reset! camera
        {:width 100 :height (/ 100 aspect-ratio) :x-left 0 :y-bottom 0})

(defn world-to-px-scale
  "A scaling factor on world coordinates to give pixels.
Fits the camera bounds into the window, expanding these
bounds if necessary to ensure an isometric aspect ratio."
  []
  (let [cam @camera
        xscale (/ px-width (:width cam))
        yscale (/ px-height (:height cam))]
    (min xscale yscale)))

(defn world-to-px
  "Convert a point in Box2d world coordinates to screen pixels."
  [[x y]]
  (let [cam @camera
        scale (world-to-px-scale)
        x-left (:x-left cam)
        y-bottom (:y-bottom cam)
        y-top (+ y-bottom (:height cam))]
    [(* (- x x-left) scale)
     ;; Gimp has flipped y (0px at top)
     (* (- y-top y) scale)]))

(defn px-to-world
  "Convert a point in screen pixels to Box2d world coordinates."
  [[xp yp]]
  (let [cam @camera
        scale (world-to-px-scale)
        x-left (:x-left cam)
        y-bottom (:y-bottom cam)
        y-top (+ y-bottom (:height cam))]
    [(+ (/ xp scale) x-left)
     ;; Gimp has flipped y (0px at top)
     (- y-top (/ yp scale))]))

(def focus-world (px-to-world (:start level)))

(defn poly-edges
  [vertices attrs]
  (for [[v0 v1] (partition 2 1 vertices)]
    (merge {:shape (edge v0 v1)} attrs)))

(defn make-bird!
  [bird-type]
  (let [attr (bird-type bird-attrs)
        radius (/ (:radius attr) (world-to-px-scale))
        bod (body! {:position focus-world
                    :bullet true}
                   {:shape (circle radius)
                    :density (:density attr)
                    :restitution (:restitution attr)})]
    (sleep! bod)
    bod))

(defn next-bird!
  []
  (when-not (nil? @bird)
    (destroy! @bird)
    (reset! bird nil))
  (when-let [bird-type (first @bird-queue)]
    (reset! bird (make-bird! bird-type))
    (swap! bird-queue next)))

(defn setup-world! []
  (create-world!)
  (reset! pigs [])
  (reset! bird nil)
  (reset! bird-queue (:birds level))
  (doseq [obj (:objs level)]
    (let [type (:type obj)
          bodytype (if (= type :static) :static :dynamic)
          pos (if (:pos obj)
                (px-to-world (:pos obj))
                [0 0])
          fixt-attr (get materials type)
          shp (case (:shape obj)
                :circle (circle (/ (:radius obj) (world-to-px-scale)))
                :box (let [[w-px h-px] (:wh obj)
                           w (/ w-px (world-to-px-scale))
                           h (/ h-px (world-to-px-scale))]
                       (box (/ w 2) (/ h 2)))
                :poly (polygon (map px-to-world (:coords obj)))
                :polyline (poly-edges (map px-to-world (:coords obj))
                                      fixt-attr))
          body-attr {:type bodytype
                     :position pos
                     :linear-damping linear-damping
                     :user-data {:type type}}
          bod (if (= (:shape obj) :polyline)
                (apply body! body-attr shp)
                (body! body-attr (merge {:shape shp} fixt-attr)))]
      (sleep! bod)
      (when (= type :static)
        (reset! ground-body bod))
      (when (= type :pig)
        (swap! pigs conj bod))))
  (next-bird!))

(defn my-key-press []
  (case (quil/raw-key)
    \n (next-bird!)
    \r (setup-world!)
    \? (println "TODO")
    ;; otherwise pass on to testbed
    (key-press)))

(defn my-mouse-pressed []
  (let [pt (mouse-world)
        fixt (first (query-at-point pt 1))]
    (when fixt
      (let [bod (body fixt)]
        (when (= bod @bird)
          (reset! aiming? true))))))

(defn my-mouse-dragged []
  )

(defn my-mouse-released []
  (when @aiming?
    (let [offs (v-sub focus-world (mouse-world))
          mag (v-mag offs)
          ang (v-angle offs)
          vel (polar-xy launch-speed ang)]
      (.setLinearVelocity @bird (vec2 vel))
      ;(apply-impulse! @bird impulse (position @bird))
      (reset! aiming? false))))

(defn draw-more []
  (when @aiming?
    (quil/stroke (quil/color 255 255 255))
    (quil/line (cljbox2d.testbed/world-to-px (mouse-world))
               (cljbox2d.testbed/world-to-px focus-world))))

(defn setup []
  (quil/frame-rate (/ 1 *timestep*))
  (setup-world!)
  (reset! draw-more-fn draw-more))

(defn -main
  "Run the sketch."
  [& args]
  (quil/defsketch the-sketch
    :title "Ugly boids"
    :setup setup
    :draw draw
    :key-typed my-key-press
    :mouse-pressed my-mouse-pressed
    :mouse-released my-mouse-released
    :mouse-dragged my-mouse-dragged
    :size [(/ px-width 2) (/ px-height 2)]))
