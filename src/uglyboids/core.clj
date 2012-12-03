(ns uglyboids.core
  (:use cljbox2d.core
        [cljbox2d.vec2d :only [TWOPI PI in-pi-pi polar-xy
                               v-mag v-angle v-sub v-scale]]
        uglyboids.physics-params)
  (:import (org.jbox2d.callbacks ContactListener)
           (org.jbox2d.collision WorldManifold)))

(def scene (atom nil))

(def pigs (atom #{}))

(def bird-queue (atom []))

(def bird (atom nil))

(def focus-world (atom nil))

(def ground-body (atom nil))

(defn world-to-px-scale
  []
  (let [xscale (/ px-width @world-width)
        yscale (/ px-height @world-height)]
    (min xscale yscale)))

(defn world-to-px
  "Convert a point in Box2d world coordinates to screen pixels."
  [[x y]]
  (let [scale (world-to-px-scale)]
    [(* x scale)
     ;; pixels have flipped y (0px at top)
     (* (- @world-height y) scale)]))

(defn px-to-world
  "Convert a point in screen pixels to Box2d world coordinates."
  [[xp yp]]
  (let [scale (world-to-px-scale)]
    [(/ xp scale)
     ;; pixels have flipped y (0px at top)
     (- @world-height (/ yp scale))]))

(defn poly-edges
  [vertices attrs]
  (for [[v0 v1] (partition 2 1 vertices)]
    (merge {:shape (edge v0 v1)} attrs)))

(def contact-buffer
  "Holds a sequence of contacts for the last time step, each
represented as `[fixture-a fixture-b point normal impulse]`.
The normal points from A to B."
  (atom []))

(defn set-buffering-contact-listener!
  "A ContactListener which populates `contact-buffer`."
  []
  (let [world-manifold (WorldManifold.)
        lstnr (reify ContactListener
                (beginContact [_ _])
                (endContact [_ _])
                (postSolve [_ contact c-impulse]
                  (let [manifold (.getManifold contact)
                        pcount (.pointCount manifold)]
                    (when (pos? pcount)
                      ;; mutates its argument:
                      (.getWorldManifold contact world-manifold)
                      (let [fixt-a (.getFixtureA contact)
                            fixt-b (.getFixtureB contact)
                            -points (.points world-manifold)
                            -imps (.normalImpulses c-impulse)
                            pts (map v2xy (take pcount -points))
                            imps (take pcount -imps)
                            imp (apply max imps)
                            pt (nth pts (if (apply > imps) 0 1))
                            ;; world vector pointing from A to B
                            normal (v2xy (.normal world-manifold))]
                        (swap! contact-buffer conj
                               [fixt-a fixt-b pt normal imp]))))))]
    (.setContactListener *world* lstnr)))

(def destroyed (atom #{}))

(def pig-tally (atom 0))
(def block-tally (atom 0))
(def pig-damage (atom 0.0))
(def block-damage (atom 0.0))

(defn bird-hit
  [bird-info oth-info]
  (let [bird (:body bird-info)
        btyp (:type bird-info)
        otyp (:type oth-info)
        oth (:body oth-info)
        odat @(user-data oth)
        resist (:resistance odat)
        imp (:imp oth-info)
        ;; fraction of total force (twice impulse) to be applied to target
        ;; (partially applied if target destroyed)
        effectiveness (cond
                       (= :btyp :yellow-bird)
                       (if (= :type :wood)
                         0.95
                         0.25)
                       (and (= :type :glass)
                            (= :btyp :blue-bird))
                       0.95
                       :else 0.5)
        eff-imp (* 2 imp effectiveness) ;; to target (damage)
        eff-react (* 2 imp (- 1 effectiveness))] ;; to bird (bounce)
    ;; first undo the impulses already applied to bird and block
    (apply-impulse! bird (v-scale (:normal oth-info) imp)
                    (:pt bird-info))
    (apply-impulse! oth (v-scale (:normal bird-info) imp)
                    (:pt oth-info))
    (if (>= eff-imp resist)
      ;; destroyed. calculate reaction to bird.
      ;; effective resistance impulse
      (let [net-eff-react (- eff-react resist)]
          (apply-impulse! bird (v-scale (:normal bird-info) net-eff-react)
                          (:pt bird-info))
          (if (:pig? oth-info)
            (swap! pig-tally inc)
            (swap! block-tally inc))
          (swap! destroyed conj (:fixt oth-info))
          (destroy! oth))
      ;; not destroyed - but apply damage
      (let [new-resist (- resist eff-imp)]
        (apply-impulse! bird (v-scale (:normal bird-info) eff-react)
                        (:pt bird-info))
        (apply-impulse! oth (v-scale (:normal oth-info) eff-imp)
                        (:pt oth-info))
        (swap! (user-data oth) assoc :resistance new-resist)
        (if (:pig? oth-info)
          (swap! pig-damage + eff-imp)
          (swap! block-damage + eff-imp))))))

(defn pig-hit
  [pig-info]
  (let [bod (:body pig-info)
        dat @(user-data bod)
        resist (:resistance dat)
        imp (:imp pig-info)]
    (if (>= imp resist)
      ;; destroyed
      (do
        (swap! pig-tally inc)
        (swap! destroyed conj (:fixt pig-info))
        (destroy! bod))
      ;; not destroyed - but apply damage
      (let [new-resist (- resist imp)]
        (swap! (user-data bod) assoc :resistance new-resist)
        (swap! pig-damage + imp)))))

(defn block-hit
  [info]
  (when-not (or (@destroyed (:body info))
                (= :static (body-type (:body info))))
    (let [bod (:body info)
          typ (:type info)
          dat @(user-data bod)
          resist (:resistance dat)
          imp (:imp info)]
      (if (>= imp resist)
        ;; destroyed
        (do
          (swap! block-tally inc)
          (swap! destroyed conj (:fixt info))
          (destroy! bod))
        ;; not destroyed - but apply damage
        (let [new-resist (- resist imp)]
          ;; TODO - limit reaction force
          (swap! (user-data bod) assoc :resistance new-resist)
          (swap! block-damage + imp))))))

(defn make-bird!
  [bird-type]
  (let [attr (bird-type bird-attrs)
        radius (:radius attr)
        udat {:type bird-type
              :rgb (:rgb attr)}
        bod (body! {:position @focus-world
                    :bullet true
                    :user-data (atom udat)}
                   {:shape (circle radius)
                    :density (:density attr)
                    :restitution (:restitution attr)
                    :friction 1.0})]
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
  [scene]
  (create-world!)
  ;(set-buffering-contact-listener!)
  ;; establish world scale
  (reset! world-width (* base-world-width (:world-scale scene)))
  (reset! world-height (/ @world-width aspect-ratio))
  (reset! pigs #{})
  (reset! bird nil)
  (reset! bird-queue (:birds scene))
  (reset! focus-world (px-to-world (:start scene)))
  (reset! destroyed #{})
  (reset! pig-tally 0)
  (reset! block-tally 0)
  (reset! pig-damage 0.0)
  (reset! block-damage 0.0)
  ;; note, Box2D requires vertices in counter-clockwise (angle increasing) order
  ;; however, the pixel y scale is flipped relative to world y scale, so reverse!
  (reset! ground-body
          (body! {:type :static
                  :user-data (atom {:type :ground})}
                 {:shape (polygon (map px-to-world
                                       (reverse (list [0 px-height]
                                                      [0 ground-level]
                                                      [px-width ground-level]
                                                      [px-width px-height]))))
                  :friction 1.0}))
  (doseq [obj (:objs scene)]
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
                :poly (polygon (map px-to-world (reverse (:coords obj))))
                :polyline (poly-edges (map px-to-world (:coords obj))
                                      fixt-attr))
          udat {:type type
                :rgb (:rgb obj)
                :resistance (initial-resistances type)}
          body-attr {:type bodytype
                     :user-data (atom udat)
                     :position pos
                     :linear-damping linear-damping
                     :angular-damping angular-damping}
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

(defn shot-at
  [target-pt direct? tap-frac]
  (let [angs (calculate-launch-angles-for @focus-world
                                          target-pt
                                          launch-speed
                                          10.0)
        ang (if direct? (apply min angs) (apply max angs))
        sim-flight (calculate-flight-time @focus-world target-pt
                                          launch-speed 10.0 ang)
        ;; to match up to Angry Birds
        ab-flight (* sim-flight 0.7)]
    {:angle ang
     :direct? direct?
     :sim-flight sim-flight
     :ab-flight ab-flight
     :sim-tap-t (* sim-flight tap-frac)
     :ab-tap-t (* ab-flight tap-frac)
     :target-pt target-pt}))

(defn choose-shot
  []
  (let [dynamics (remove #(= :static (body-type %)) (bodyseq))
        poss (concat @pigs (take 2 (shuffle dynamics)))
        pig (first (shuffle poss)) ;(apply min-key #(first (position %)) @pigs)
        target-pt (position pig)
        shot (shot-at (position pig)
                      (< (rand) 0.5) 0.9)]
    (assoc shot
      :target-body pig
      :target-type (:type @(user-data pig)))))

(defn game-step!
  [tstep]
  (reset! contact-buffer [])
  (step! tstep)
  (doseq [ctct  @contact-buffer] 
    (let [[fixt-a fixt-b pt normal imp] ctct
          fixts (list fixt-a fixt-b)]
      (when-not (some @destroyed fixts)
        (let [infos (map (fn [fx]
                           (let [bod (body fx)
                                 dat @(user-data bod)
                                 type (:type dat)
                                 bird (bird-types type)
                                 pig? (= :pig type)]
                             {:fixt fx :body bod
                              :type type :bird bird :pig? pig?}))
                         fixts)
              info-a (assoc (first infos) :pt pt :imp imp
                            :normal (mapv - normal)) ;; pointing from B to A
              info-b (assoc (second infos) :pt pt :imp imp
                            :normal normal)]
          (cond
           (:bird info-a) (bird-hit info-a info-b)
           (:bird info-b) (bird-hit info-b info-a)
           (:pig? info-a) (do
                            (pig-hit info-a)
                            (when (:pig? info-b)
                              (pig-hit info-b)))
           (:pig? info-b) (pig-hit info-b)
           :else (do
                   (block-hit info-a)
                   (block-hit info-b))))))))

(defn simulate-for
  [dur]
  (let [start-t @world-time]
    (while (< @world-time (+ start-t dur))
      (step! (/ 1.0 20.0)))))
