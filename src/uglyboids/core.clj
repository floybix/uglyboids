(ns uglyboids.core
  (:use cljbox2d.core
        [cljbox2d.vec2d :only [TWOPI PI in-pi-pi
                               v-mag v-angle v-sub polar-xy]]
        ;[uglyboids.vision :only []]
        uglyboids.physics-params))

(def scene (atom nil))

(def pigs (atom #{}))

(def bird-queue (atom []))

(def bird (atom nil))

(def focus-world (atom nil))

(def ground-body (atom nil))

(def world-to-px-scale
  (let [xscale (/ px-width world-width)
        yscale (/ px-height world-height)]
    (min xscale yscale)))

(defn world-to-px
  "Convert a point in Box2d world coordinates to screen pixels."
  [[x y]]
  (let [scale world-to-px-scale]
    [(* x scale)
     ;; pixels have flipped y (0px at top)
     (* (- world-height y) scale)]))

(defn px-to-world
  "Convert a point in screen pixels to Box2d world coordinates."
  [[xp yp]]
  (let [scale world-to-px-scale]
    [(/ xp scale)
     ;; pixels have flipped y (0px at top)
     (- world-height (/ yp scale))]))

(defn poly-edges
  [vertices attrs]
  (for [[v0 v1] (partition 2 1 vertices)]
    (merge {:shape (edge v0 v1)} attrs)))

(defn make-bird!
  [bird-type]
  (let [attr (bird-type bird-attrs)
        radius (/ (:radius attr) world-to-px-scale)
        bod (body! {:position @focus-world
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

(defn setup-world!
  []
  (create-world!)
  (reset! pigs #{})
  (reset! bird nil)
  (reset! bird-queue (:birds @scene))
  (reset! focus-world (px-to-world (:start @scene)))
  ;; note, Box2D requires vertices in counter-clockwise (angle increasing) order
  ;; however, the pixel y scale is flipped relative to world y scale, so reverse!
  (reset! ground-body
          (body! {:type :static}
                 {:shape (polygon (map px-to-world
                                       (reverse (list [0 px-height]
                                                      [0 ground-level]
                                                      [px-width ground-level]
                                                      [px-width px-height]))))}))
  (doseq [obj (:objs @scene)]
    (let [type (:type obj)
          bodytype (if (= type :static) :static :dynamic)
          pos (if (:pos obj)
                (px-to-world (:pos obj))
                [0 0])
          fixt-attr (get materials type)
          shp (case (:shape obj)
                :circle (circle (/ (:radius obj) world-to-px-scale))
                :box (let [[w-px h-px] (:wh obj)
                           w (/ w-px world-to-px-scale)
                           h (/ h-px world-to-px-scale)]
                       (box (/ w 2) (/ h 2)))
                :poly (polygon (map px-to-world (reverse (:coords obj))))
                :polyline (poly-edges (map px-to-world (:coords obj))
                                      fixt-attr))
          body-attr {:type bodytype
                     :position pos
                     :linear-damping linear-damping
                     :angular-damping angular-damping
                     :user-data {:type type}}
          bod (if (= (:shape obj) :polyline)
                (apply body! body-attr shp)
                (body! body-attr (merge {:shape shp} fixt-attr)))]
      (sleep! bod)
      (when (= type :pig)
        (swap! pigs conj bod))))
  (next-bird!)
  {:pigs pigs
   :bird bird
   :bird-queue bird-queue})

(defn calculate-launch-angles-for
  [[x0 y0] [xT yT] v g]
  ;; http://en.wikipedia.org/wiki/Trajectory_of_a_projectile#Angle_required_to_hit_coordinate_.28x.2Cy.29
  ;; Angle to hit a target [x y] from launch point [0 0]
  ;; theta = atan( v^2 +/- sqrt( v^4 - g(g.x^2 + 2.y.v^2 )) )
  ;;             ( g.x )
  ;; translate coordinates to launch point
  (let [x (- xT x0)
        y (- yT y0)
        term (Math/sqrt (- (Math/pow v 4)
                           (* g (+ (* g x x)
                                   (* 2 y v v)))))
        numer1 (+ (* v v) term)
        numer2 (- (* v v) term)
        denom (* g x)]
    [(Math/atan (/ numer1 denom))
     (Math/atan (/ numer2 denom))]))

(defn calculate-flight-time
  [[x0 y0] [xT yT] v g theta]
  ;; t = x / ( v cos(theta) )
  ;; translate coordinates to launch point
  (let [x (- xT x0)]
    (/ x (* v (Math/cos theta)))))

;; TODO: taps
(defn shoot!
  [ang]
  (let [vel (polar-xy launch-speed ang)]
    (.setLinearVelocity @bird (vec2 vel))))

(defn choose-shot
  []
  (let [pig (apply min-key #(first (position %)) @pigs)
        target-pt (position pig)
        angs (calculate-launch-angles-for @focus-world
                                          target-pt
                                          launch-speed
                                          10.0)
        ang (first (shuffle angs))
        flight (calculate-flight-time @focus-world target-pt
                                      launch-speed 10.0 ang)]
    (println "chose angle" ang "with flight time" flight)
    {:angle ang
     :flight flight
     :tap-t (* flight 0.85)
     :target-body pig}))

(defn simulate-for
  [dur]
  (let [start-t @world-time]
    (loop [i 0]
      (if (< @world-time (+ start-t dur))
        (do
          (step! (/ 1.0 15.0))
          (recur (inc i)))
        (println "i =" i)))))
