(ns uglyboids.core
  (:use cljbox2d.core
        [cljbox2d.vec2d :only [TWOPI PI in-pi-pi polar-xy
                               v-mag v-angle v-sub v-scale]]
        uglyboids.physics-params)
  (:import (org.jbox2d.callbacks ContactListener)
           (org.jbox2d.collision WorldManifold)
           (org.jbox2d.collision AABB)))

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
                (preSolve [_ _ _])
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
                         0.20)
                       (and (= :type :glass)
                            (= :btyp :blue-bird))
                       0.95
                       (= :type :glass)
                       0.2
                       :else 0.5)
        eff-imp (* 2 imp effectiveness) ;; to target (damage)
        eff-react (* 2 imp (- 1 effectiveness))] ;; to bird (bounce)
    (when (pos? imp)
      (when resist
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
            (when (:pig? oth-info)
                                        ;(swap! pigs dissoc oth))
              (println "pig destroyed")
              (reset! pigs (set (remove #(= oth %) @pigs))))
            (when-not (:pig? oth-info)
              (println "block destroyed"))
            (swap! destroyed conj (:fixt oth-info))
            (destroy! oth))
          ;; not destroyed - but apply damage
          (let [new-resist (- resist eff-imp)]
            (apply-impulse! bird (v-scale (:normal bird-info) eff-react)
                            (:pt bird-info))
            (apply-impulse! oth (v-scale (:normal oth-info) eff-imp)
                            (:pt oth-info))
            (swap! (user-data oth) update-in [:resistance] - eff-imp)
            (if (:pig? oth-info)
              (swap! pig-damage + eff-imp)
              (swap! block-damage + eff-imp))))))))

(defn pig-hit
  [pig-info]
  (let [bod (:body pig-info)
        dat @(user-data bod)
        resist (:resistance dat)
        imp (:imp pig-info)]
    (when (pos? imp)
      ;(println "pig hit, resistance" resist "impact" imp)
      (if (>= imp resist)
        ;; destroyed
        (do
          (swap! pig-tally inc)
                                        ;(swap! pigs dissoc bod)
          (reset! pigs (set (remove #(= bod %) @pigs)))
          (swap! destroyed conj (:fixt pig-info))
          (println "pig destroyed")
          (destroy! bod))
        ;; not destroyed - but apply damage
        (let [new-resist (- resist imp)]
          (swap! (user-data bod) update-in [:resistance] - imp)
          (swap! pig-damage + imp))))))

(defn block-hit
  [info]
  (when-not (or (@destroyed (:body info))
                (= :static (body-type (:body info))))
    (let [bod (:body info)
          typ (:type info)
          dat @(user-data bod)
          resist (:resistance dat)
          imp (:imp info)]
      (when (pos? imp)
                                        ;(println "block hit, resistance" resist "impact" imp)
        (if (>= imp resist)
          ;; destroyed
          (do
            (swap! block-tally inc)
            (swap! destroyed conj (:fixt info))
            (println "block destroyed")
            (destroy! bod))
          ;; not destroyed - but apply damage
          (let [new-resist (- resist imp)]
            ;; TODO - limit reaction force
            (swap! (user-data bod) update-in [:resistance] - imp)
            (swap! block-damage + imp)))))))

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
  (set-buffering-contact-listener!)
  (reset! contact-buffer [])
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

(defn game-step!
  []
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
                   (block-hit info-b)))))))
    (reset! contact-buffer []))

(defn simulate-for
  [dur]
  (let [start-t @world-time]
    (while (< @world-time (+ start-t dur))
      (step! (/ 1.0 20.0))
      (game-step!))))

(defn simulate-shot! 
  [shot] 
  (try 
    (shoot! (:angle shot)) 
    (let [eff-f (future (simulate-for (+ (:sim-flight shot) 3.0)))]
      (deref eff-f 2000 {:pigs-done @pig-tally 
                        :pig-damage @pig-damage 
                        :blocks-gone @block-tally}))
    (catch Exception e 
      (println "simulate-shot!:" (.getMessage e))))
  {:pigs-done @pig-tally 
   :pig-damage @pig-damage 
   :blocks-gone @block-tally})

(defn poss-targets
  []
  (let [world-ground (second (px-to-world [0 ground-level]))
        poss (remove #((conj bird-types :ground :static-wood)
                       (:type @(user-data %))) (bodyseq))
        by-potential (sort-by (fn [bod] 
                              (let [y (second (center bod)) 
                                    h (- y world-ground)]
                                (* h (Math/sqrt (mass bod))))) 
                              poss)
        ;; TODO: wood for yellow, glass for blue 
        targets (concat @pigs (take-last 1 by-potential)) 
        targets-info (map (fn [o] 
                            {:pt (center o) 
                             :type (:type @(user-data o))}) 
                          targets)
        ;; try ends of horizontal beams
        beam-pts (map (fn [fx]
                        (let [o (body fx)]
                        (when (= :dynamic (body-type o))
                          (let [ab (aabb fx)
                                [x-span y-span] (v2xy (.getExtents ab))]
                            (when (> (/ x-span y-span) 3)
                              (let [[xc yc] (center o)
                                    x-lo (- xc (/ x-span 2))
                                    x-hi (+ xc (/ x-span 2))
                                    y-lo (- yc (/ y-span 2))
                                    y-hi (+ yc (/ y-span 2))]
                                (println [[x-lo x-hi] [y-lo y-hi] world-ground])
                                (when (> y-lo (+ world-ground 0.2))
                                  [x-lo (/ (+ y-lo y-hi) 2)])))))))
                      (fixtureseq))
        beam-info (map (fn [pt] {:pt pt :type :beam})
                       (remove nil? beam-pts))
        targets-info (concat targets-info (take 3 beam-info))]
    targets-info))

(defn ranked-shots 
  [] 
  (let [targets-info (poss-targets)
        eval-shots (doall (for [{:keys [pt type]} targets-info
                                direct? [true false]]
                            (let [tap-frac (+ 0.8 (rand 0.25))
                                  shot (shot-at pt direct? tap-frac)]
                              (println "simulating" (if direct? "direct" "mortar")
                                       "shot at" type "angle" (:angle shot))
                              (let [effects (simulate-shot! shot)]
                                (println "EFFECTS:" effects)
                                ;; hoping we can reset everything this way. 
                                ;; problem might be any stored JBox2D objects?
                                (setup-world! @scene)
                                (merge shot effects {:target-type type})))))]
    (reverse (sort-by (juxt :pigs-done :pig-damage :blocks-gone)
                      eval-shots))))

(defn choose-shot
  [i]
  (let [rs (ranked-shots)
        i-mod (mod i (- (count rs) 5))]
    (nth rs (+ i-mod (rand-int 3)))))

(defn choose-shot-naive
  [i]
  (let [ti (poss-targets)
        {:keys [pt type]} (rand-nth ti)
        direct? (> (rand) 0.7)
        tap-frac (+ 0.8 (rand 0.25))
        shot (shot-at pt direct? tap-frac)]
    (assoc shot :target-type type)))

(defn choose-shot-simple
  []
  (let [world-ground (second (px-to-world [0 ground-level]))
        poss (remove #(bird-types (:type @(user-data %))) (bodyseq))
        by-height (sort-by (fn [bod] 
                             (let [y (second (center bod)) 
                                   h (- y world-ground)] 
                               h))
                           poss)
        targets (concat [(rand-nth (seq @pigs)) (rand-nth (seq @pigs))]
                        (take-last 2 by-height))
        target (rand-nth targets)
        type (:type @(user-data target))
        direct? (> (rand) 0.6)
        tap-frac (+ 0.8 (rand 0.25))
        shot (shot-at (center target) direct? tap-frac)
        effects (simulate-shot! shot)]
    (println "simulating" (if direct? "direct" "mortar")
             "shot at" type "angle" (:angle shot))
    (let [effects (simulate-shot! shot)]
      (println "EFFECTS:" effects)
      ;; hoping we can reset everything this way. 
      ;; problem might be any stored JBox2D objects?
      (setup-world! @scene)
      (println "simulated shot effects: " effects)
      (merge shot effects {:target-type type}))))
