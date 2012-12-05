(ns uglyboids.vision.shape-detection
  (:require clojure.set))

(def ^:const ^{:doc "Pi (180 degrees)."} PI (. Math PI))
(def ^:const ^{:doc "2 Pi (360 degrees)."} TWOPI (* PI 2.0))
(def ^:const ^{:doc "Pi/2 (90 degrees)."} PI_2 (* PI 0.5))

(defn abs
  "Absolute value; avoids reflection from overloaded Math/abs"
  [x]
  (if (neg? x) (- x) x))

(defn in-pi-pi
  "Returns the angle expressed in the range -pi to pi."
  [angle]
  (cond
   (> angle PI) (in-pi-pi (- angle TWOPI))
   (< angle (- PI)) (in-pi-pi (+ angle TWOPI))
   :else angle))

(defn polar-xy
  "Convert polar coordinates (magnitude, angle) to cartesian
   coordinates (x, y)."
  [mag angle]
  [(* mag (Math/cos angle))
   (* mag (Math/sin angle))])

(defn v-angle
  "Angle of a 2d geometric vector in radians in range -pi to pi."
  [[x y]]
  (Math/atan2 y x))

(defn v-mag
  "Magnitude of a 2d geometric vector"
  [[x y]]
  (Math/sqrt (+ (* x x) (* y y))))

(defn v-mag2
  "Squared magnitude of a 2d geometric vector"
  [[x y]]
  (+ (* x x) (* y y)))

(defn v-sub
  "Subtract a 2d geometric vector from another (v1 - v2)."
  [v1 v2]
  (mapv - v1 v2))

(defn v-add
  "Add a 2d geometric vector to another."
  [v1 v2]
  (mapv + v1 v2))

(defn v-dist
  "Distance from one 2d point to another."
  [v1 v2]
  (v-mag (v-sub v1 v2)))

(defn bounding-box
  [coords]
  (let [[x0 y0] (first coords)]
    (loop [x-lower x0
           y-lower y0
           x-upper x0
           y-upper y0
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
        [[x-lower y-lower] [x-upper y-upper]]))))

(defn neighbours
  [[x y] [min-x max-x] [min-y max-y]]
;  (for [xi [(dec x) x (inc x)]
;        yi [(dec y) y (inc y)]
;        :when (and (<= min-x xi max-x)
;                   (<= min-y yi max-y)
;                   (not= [x y] [xi yi]))]
;    [xi yi]))
  (filter (fn [[x y]] (and (<= min-x x max-x)
                           (<= min-y y max-y)))
          (list [(inc x) y]
                [x (inc y)]
                [(dec x) y]
                [x (dec y)])))

(defn dilate-blob
  [coords [min-x max-x] [min-y max-y]]
  (into (set coords)
        (mapcat #(neighbours % [min-x max-x] [min-y max-y]) coords)))

(defn each-edge-points
  "Return a sequence of points which are on each edge of a shape in
keys :left :right :top :bottom. The horizontal sides are ordered by
increasing x value, vertical sides are ordered by increasing y value.
NOTE: here :top has the highest y values, which may actually be the
bottom if pixel origin is at top-left."
  [coords [x-lo x-hi] [y-lo y-hi] ground-level]
  (let [gl-ok (min y-hi (max y-lo ground-level))
        rng-x (inc (- x-hi x-lo))
        rng-y (inc (- y-hi y-lo))
        min-ok (fn [a b] (if (nil? a) b (min a b)))
        max-ok (fn [a b] (if (nil? a) b (max a b)))]
    (loop [pts coords
           left-xs (vec (repeat rng-y nil))
           right-xs (vec (repeat rng-y nil))
           bot-ys (vec (repeat rng-x nil))
           top-ys (vec (repeat rng-x nil))]
      (if (seq pts)
        (let [[x y] (first pts)
              iy (- y y-lo)
              ix (- x x-lo)
              ;; drop bottom edge to ground level
              yb (if (< (abs (- y ground-level)) 25)
                   gl-ok y)]
          (recur (next pts)
                 (update-in left-xs [iy] min-ok x)
                 (update-in right-xs [iy] max-ok x)
                 (update-in bot-ys [ix] min-ok y)
                 (update-in top-ys [ix] max-ok yb))) ;; since y in pixels increases "down"
        (let [to-points-lr (fn [i x] [x (+ i y-lo)])
              to-points-tb (fn [i y] [(+ i x-lo) y])
              nnil (fn [x] (filter #(not-any? nil? %) x))]
          {:left   (nnil (map-indexed to-points-lr left-xs))
           :right  (nnil (map-indexed to-points-lr right-xs))
           :top    (nnil (map-indexed to-points-tb top-ys))
           :bottom (nnil (map-indexed to-points-tb bot-ys))})))))

(defn edge-points
  "Return a sequence of all points which are on the edge of a shape:
specifically the left-/right-most or top-/bottom-most points on each
horizontal or vertical coordinate. The result is ordered
counter-clockwise (assuming that increasing y is 'up'):
* increasing x along :bottom
* increasing y along :right STARTING from the last y from bottom, then
* decreasing x along :top STARTING from the last x from right
* decreasing y along :left STARTING from the last y from top"
  [coords [x-lo x-hi] [y-lo y-hi] ground-level]
  (let [edges (each-edge-points coords [x-lo x-hi] [y-lo y-hi] ground-level)
        bottom (:bottom edges)
        bottom-last-y (second (last bottom))
        right (filter (fn [[x y]] (>= y bottom-last-y))
                      (:right edges))
        right-last-x (first (last right))
        top (filter (fn [[x y]] (<= x right-last-x))
                    (reverse (:top edges)))
        top-last-y (second (last top))
        bottom-first-y (second (first bottom))
        left (filter (fn [[x y]] (and (<= y top-last-y)
                                      (>= y bottom-first-y)))
                     (reverse (:left edges)))]
    (distinct (concat bottom right top left))))

(defn segment-extrema
  "Finds the nearest and furthest points from a center point in each
   angle segment. Returns a vector of length n-segments, each with 0
   to 2 points (0 points if no points were found in that angle
   segment) in local polar coordinates as {:mag :ang}.
   Ordered by angle, from -pi to pi."
  [edge-pts cent-pt n-segments
   & {:keys [maxima-only?] :or {maxima-only? false}}]
  (let [seg-ang (/ TWOPI n-segments)]
    (loop [pts (seq edge-pts)
           seg-extrema (vec (repeat n-segments
                                    {:far nil :near nil}))]
      (if (seq pts)
        (let [pt (first pts)
              dxy (v-sub pt cent-pt)
              ang (v-angle dxy)
              mag2 (v-mag2 dxy) ;; distance squared
              pt-polar {:mag mag2 :ang ang}
              iseg (mod (int (quot (+ ang PI) seg-ang)) n-segments)]
          (recur (next pts)
                 (update-in seg-extrema [iseg]
                            (fn [{:keys [far near]}]
                              (let [is-far? (or (nil? far)
                                                (> mag2 (:mag far)))
                                    is-near? (and (> mag2 4) ;; ignore within 2px
                                                  (or (nil? near)
                                                      (< mag2 (:mag near))))]
                                {:far (if is-far? pt-polar far)
                                 :near (if is-near? pt-polar near)})))))
        ;; return from loop:
        (map (fn [{:keys [far near]}]
               ;; we know: both near and far will be nil, or both not.
               (if (nil? near)
                 (list)
                 (let [near (update-in near [:mag] #(Math/sqrt %))
                       far (update-in far [:mag] #(Math/sqrt %))]
                   (if maxima-only?
                     (list far)
                     (distinct (if (> (:ang far) (:ang near))
                                 (list near far)
                                 (list far near)))))))
             seg-extrema)))))

(defn collinear-d?
  "Determine whether point p1 is on a line between points p0 and
   p2. Uses the distance test for collinearity:
   (dist p0 p2) ~= (dist p0 p1) + (dist p1 p2)
   The relative error in distances must be less than tol."
  [p0 p1 p2 tol]
  (let [d-direct (v-dist p0 p2)
        d-indirect (+ (v-dist p0 p1)
                      (v-dist p1 p2))
        d-err (- d-indirect d-direct)]
    (<= (/ d-err d-direct) tol)))

(defn collinear?
  "Determine whether point p1 is on a line between points p0 and
   p2. The difference in angles between p0--p1 and p1--p2 must be less
   than tol radians."
  [p0 p1 p2 tol]
  (let [ang0 (v-angle (v-sub p1 p0))
        ang1 (v-angle (v-sub p2 p1))
        ang-err (abs (in-pi-pi (- ang0 ang1)))]
    (<= ang-err tol)))

(defn triples-wrapped
  "Returns each sub-sequence of three elements of coll, centered
around the corresponding index of coll. So the first triple is
for (n-1,0,1) and the last is for (n-2,n-1,0)."
  [coll]
  (partition 3 1 (concat (take-last 1 coll) coll (take 1 coll))))

(defn backward-pairs-wrapped
  [coll]
  (partition 2 1 (concat (take-last 1 coll) coll)))

(defn median
  [coll]
  (let [n (count coll)
        i (quot (dec n) 2)]
    (if (<= n 1)
      (first coll)
      (let [scoll (sort coll)]
        (if (odd? n)
          (nth scoll i)
          ;; average middle two:
          (let [[x1 x2] (take 2 (drop i scoll))]
            (/ (+ x1 x2) 2)))))))

(defn median-angle
  [angs]
  ;; correct for discontinuity at -pi/+pi
  (let [angs* (if (> (median (map abs angs)) (* 0.8 PI))
                (map #(if (neg? %) (+ % TWOPI) %) angs)
                angs)]
    (in-pi-pi (median angs*))))

(defn v-avg
  [pts]
  (let [n (count pts)]
    (mapv #(/ % n) (reduce v-add pts))))

(defn vertical-angle?
  "Within +/- 1.5 degrees."
  ([ang]
     (vertical-angle? ang (* 1.5 (/ PI 180.0))))
  ([ang tol]
     (< (abs (- (abs (in-pi-pi ang)) PI_2)) tol)))

(defn horizontal-angle?
  "Within +/- 1.5 degrees."
  ([ang]
     (vertical-angle? (in-pi-pi (+ ang PI_2))))
  ([ang tol]
     (vertical-angle? (in-pi-pi (+ ang PI_2)) tol)))

(defn interior-angle
  "Interior angle of a triple of points using law of cosines:
     cosC = (a^2 + b^2 - c^2) / (2ab)
   where, in a triangle, angle C is opposite side c."
  [[v0 v1 v2]]
  (let [a (v-dist v0 v1)
        b (v-dist v2 v1)
        c (v-dist v0 v2)]
    (Math/acos (/ (+ (* a a)
                     (* b b)
                     (- (* c c)))
                  (* 2 a b)))))

(defn line-intersection
  "Point of intersection of two lines in the plane.
   First line passes through [x0 y0] with gradient m0.
   Other line passes through [x1 y1] with gradient m1.
   Gradient can be passed as nil for vertical lines."
  [[x0 y0] m0 [x1 y1] m1]
  (cond
   (= m0 m1) nil
   ;; vertical line at x = x0
   (nil? m0)
   (let [y (+ (* m1 (- x0 x1)) y1)]
     [x0 y])
   ;; vertical line at x = x1
   (nil? m1)
   (let [y (+ (* m0 (- x1 x0)) y0)]
     [x1 y])
   ;; general equation
   ;; x = m0.x0 - m1.x1 + y1 - y0
   ;;     (m0 - m1)
   ;; y = m0 (x - x0) + y0
   :else
   (let [x (/ (+ (* m0 x0) (* -1 m1 x1) y1 (- y0))
              (- m0 m1))
         y (+ (* m0 (- x x0)) y0)]
     [x y])))

(defn angle-to-gradient
  [ang]
  (cond (vertical-angle? ang) nil
        (horizontal-angle? ang) 0.0
        :else (Math/tan ang)))

(defn angle-intersection
  "Point of intersection of two lines in the plane."
  [xy0 ang0 xy1 ang1]
  (let [m0 (angle-to-gradient ang0)
        m1 (angle-to-gradient ang1)]
    (line-intersection xy0 m0 xy1 m1)))

(defn snap-vertices-to-other-shape
  [verts coords fuzz debug]
  (loop [vv verts
         n-vv []]
    (if (seq vv)
      (let [v (first vv)
            [v-x v-y] v
            ;; closest vertex
            ov1 (apply min-key #(v-mag2 (v-sub % v)) coords)
            [ov1-x ov1-y] ov1
            ;; vertices on the other (x) side
            opp-coords (filter #(not= (< v-x ov1-x)
                                      (< v-x (first %))) coords)]
        (if (seq opp-coords)
          (let [ov2 (apply min-key #(v-mag2 (v-sub % v)) opp-coords)
                ov-ang (v-angle (v-sub ov2 ov1))
                [new-x new-y] (angle-intersection ov1 ov-ang v PI_2)
                shift (- new-y v-y)]
            (if (<= (abs shift) fuzz)
              (do
                (when debug (println "SHIFTED vertex" v "by" shift))
                (recur (next vv) (conj n-vv [new-x new-y])))
              (recur (next vv) (conj n-vv v))))
          ;; no opposite points, so v hanging off the edge. drop to ov1?
          (let [shift (- ov1-y v-y)]
            (if (<= (abs shift) fuzz)
              (do
                (when debug (println "SHIFTED vertex" v "by" shift "(overhang)"))
                (recur (next vv) (conj n-vv [v-x ov1-y])))
              (recur (next vv) (conj n-vv v))))))
      n-vv)))

(defn find-vertices-tri-quad
  [edge-pts cent-pt type]
  (let [
        n (count edge-pts)
        lpts (map #(v-sub % cent-pt) edge-pts)
        [near-i near-pt] (apply min-key (fn [[i pt]] (v-mag2 pt))
                                (map-indexed vector lpts))
        [far-i far-pt] (apply max-key (fn [[i pt]] (v-mag2 pt))
                              (map-indexed vector lpts))
        far-pt (apply max-key v-mag2 lpts)
        far-flip-pt (mapv - far-pt)
        near-flip-pt (mapv - near-pt)
;        near-opp-pt (nth (mod (+ near-i (quot n 2)) n) lpts)
        ;; for a rect, the near point will be rotated about 90 degrees
        ;; from far point -- but for a triangle it would be 60 or 180.
        ;; maximum distance around is (~ 180 degrees) n/2
        near-to-far-ii (mod (- near-i far-i) (quot n 2))
        near-to-far-frac (/ near-to-far-ii (/ n 2))
        elongation (/ (v-mag far-pt) (v-mag near-pt))
        strut? (and (>= elongation 3.0)
                    (= type :wood))
        rect? true] ;(or (>= elongation 1.9)
              ;    (<= (abs (- near-to-far-frac 0.5)) 0.1))]
    (if rect?
      (if strut?
        (let [long-angle (v-angle (v-sub far-pt far-flip-pt))
              perp-angle (+ long-angle PI_2)
              near-pt (polar-xy 4 perp-angle)
              near-flip-pt (polar-xy -4 perp-angle)
              ;; we have all we need: the long angle, short angle assumed perpendicular,
              ;; and points on long edge and far edges.
              ;; find vertices
              vtx [(angle-intersection near-pt long-angle far-pt perp-angle)
                   (angle-intersection far-pt perp-angle near-flip-pt long-angle)
                   (angle-intersection near-flip-pt long-angle far-flip-pt perp-angle)
                   (angle-intersection far-flip-pt perp-angle near-pt long-angle)]
              vtx (sort-by v-angle vtx)]
          ;; return: convert back to global coordinates
          (map #(v-add % cent-pt) vtx))
        ;; rect
        (let [n-segments 24
              angle-tol (/ PI 9) ;; 20 degrees
              segx (segment-extrema edge-pts cent-pt n-segments
                                    :maxima-only? true)
              ;; collapse to a flat sequence of polar points (from segments)
              all-pp (apply concat segx)
              ;; find index of single closest point
              i0 (apply min-key (fn [i] (:mag (nth all-pp i)))
                        (range (count all-pp)))
              ;; rotate points sequence to start from closest point
              pp (concat (drop i0 all-pp) (take i0 all-pp))
              ;; convert back to local rectangular coordinates (cent)
              pts (map (fn [{:keys [mag ang]}] (polar-xy mag ang)) pp)
              n (count pts)
              ;; find index of point in opposite direction
              near-pt (first pts)
              near-dir (:ang (first pp))
              i-opp (apply max-key
                           (fn [i] (abs (in-pi-pi (- near-dir (:ang (nth pp i))))))
                           (range 3 (- n 2)))
              near-opp-pt (nth pts i-opp)
              ;; find index of single furthest point
              iz (apply max-key (fn [i] (:mag (nth pp i)))
                        (range (count pp)))
              ;; rotate points sequence to start from furthest point
              pp-z (concat (drop iz pp) (take iz pp))
              pts-z (concat (drop iz pts) (take iz pts))
              far-pt (first pts-z)
              far-dir (:ang (first pp-z))

              ;; estimate angle of surface at closest point
              angs (map (fn [[p0 p1]] (v-angle (v-sub p1 p0)))
                        (backward-pairs-wrapped pts))
              ;; find angles between pairs of points, skipping over one
              angs-skip1 (map (fn [[p0 p1 p2]] (v-angle (v-sub p2 p0)))
                              (triples-wrapped pts))
              ;; angle between the points around closest point
                                        ;angle-0 (v-angle (v-sub (second pts) (last pts)))
              angs-near (concat         ;(take-last 1 angs-skip1)
                         (take 1 angs-skip1) ;2 angs-skip1)
                         (take 2 angs))
              angle-near (median-angle angs-near)
              ;; angle between the points around opposite point
                                        ;angle-opp (v-angle (v-sub (nth pts (inc i-opp)) (nth pts (dec i-opp))))
              angs-opp (concat (take 1 (drop i-opp angs-skip1))
                                        ;(take 3 (drop (dec i-opp) angs-skip1))
                               (take 2 (drop i-opp angs)))
              ;; flip since opposite angle
              angs-opp (map #(in-pi-pi (+ % PI)) angs-opp)
              angle-opp (median-angle angs-opp)
              
              long-angle (median-angle (concat angs-near angs-opp))
              far-opp-pt (mapv - far-pt)
                
              long-angle (if strut? (v-angle (v-sub far-pt far-opp-pt))
                             long-angle)
              perp-angle (+ long-angle PI_2)
              
              vtx [(angle-intersection near-pt long-angle far-pt perp-angle)
                   (angle-intersection far-pt perp-angle near-opp-pt long-angle)
                   (angle-intersection near-opp-pt long-angle far-opp-pt perp-angle)
                   (angle-intersection far-opp-pt perp-angle near-pt long-angle)]
              vtx (sort-by v-angle vtx)]
          ;; return: convert back to global coordinates
          (map #(v-add % cent-pt) vtx)))
      ;; otherwise: is triangle.
      ;; find furthest points in 120-degree segments opposite furthest
      (let [n-segments 24
            angle-tol (/ PI 9) ;; 20 degrees
            segx (segment-extrema edge-pts cent-pt n-segments
                                  :maxima-only? true)
            ;; collapse to a flat sequence of polar points (from segments)
            all-pp (apply concat segx)
            ;; find index of single closest point
            i0 (apply min-key (fn [i] (:mag (nth all-pp i)))
                      (range (count all-pp)))
            ;; rotate points sequence to start from closest point
            pp (concat (drop i0 all-pp) (take i0 all-pp))
            ;; convert back to local rectangular coordinates (cent)
            pts (map (fn [{:keys [mag ang]}] (polar-xy mag ang)) pp)
            n (count pts)
            ;; find index of point in opposite direction
            near-pt (first pts)
            near-dir (:ang (first pp))
            i-opp (apply max-key
                         (fn [i] (abs (in-pi-pi (- near-dir (:ang (nth pp i))))))
                         (range 3 (- n 2)))
            near-opp-pt (nth pts i-opp)
            ;; find index of single furthest point
            iz (apply max-key (fn [i] (:mag (nth pp i)))
                      (range (count pp)))
            ;; rotate points sequence to start from furthest point
            pp-z (concat (drop iz pp) (take iz pp))
            pts-z (concat (drop iz pts) (take iz pts))
            far-pt (first pts-z)
            far-dir (:ang (first pp-z))
            
            ;; find index of point in 120-degree segment offset 60-degrees above the furthest
            i-rot-fwd (apply max-key (fn [i]
                                       (let [rot (in-pi-pi (- (:ang (nth pp i)) far-dir))]
                                         (if (>= rot (/ PI 3))
                                           (:mag (nth pp i)) 0)))
                             (range 2 (- n 1)))
            ;; find index of point in 120-degree segment offset 60-degrees below the furthest
            i-rot-back (apply max-key (fn [i]
                                        (let [rot (in-pi-pi (- (:ang (nth pp i)) far-dir))]
                                          (if (<= rot (- (/ PI 3)))
                                            (:mag (nth pp i)) 0)))
                              (range 2 (- n 1)))
            vtx [(nth pts i-rot-back)
                 (nth pts iz)
                 (nth pts i-rot-fwd)]]
        ;; return: convert back to global coordinates
        (map #(v-add % cent-pt) vtx)))))


(defn find-vertices-tri-quad-OLD
  [edge-pts cent-pt]
  (let [n-segments 24
        angle-tol (/ PI 9) ;; 20 degrees
        segx (segment-extrema edge-pts cent-pt n-segments
                              :maxima-only? true)
        n-segs-ok (count (filter seq segx))]
;    (swank.core/break)
    (if (<= n-segs-ok 2)
      ;; should not happen, but return:
      nil
      (let [;; collapse to a flat sequence of polar points (from segments)
            all-pp (apply concat segx)
            ;; find index of single closest point
            i0 (apply min-key (fn [i] (:mag (nth all-pp i)))
                      (range (count all-pp)))
            ;; rotate points sequence to start from closest point
            pp (concat (drop i0 all-pp) (take i0 all-pp))
            ;; convert back to local rectangular coordinates (cent)
            pts (map (fn [{:keys [mag ang]}] (polar-xy mag ang)) pp)
            n (count pts)
            ;; find index of point in opposite direction
            near-pt (first pts)
            near-dir (:ang (first pp))
            i-opp (apply max-key
                         (fn [i] (abs (in-pi-pi (- near-dir (:ang (nth pp i))))))
                         (range 3 (- n 2)))
            near-opp-pt (nth pts i-opp)
            ;; find index of single furthest point
            iz (apply max-key (fn [i] (:mag (nth pp i)))
                      (range (count pp)))
            ;; rotate points sequence to start from furthest point
            pp-z (concat (drop iz pp) (take iz pp))
            pts-z (concat (drop iz pts) (take iz pts))
            far-pt (first pts-z)
            far-dir (:ang (first pp-z))

            ;; estimate angle of surface at closest point
            angs (map (fn [[p0 p1]] (v-angle (v-sub p1 p0)))
                      (backward-pairs-wrapped pts))
            ;; find angles between pairs of points, skipping over one
            angs-skip1 (map (fn [[p0 p1 p2]] (v-angle (v-sub p2 p0)))
                            (triples-wrapped pts))
            ;; angle between the points around closest point
            ;angle-0 (v-angle (v-sub (second pts) (last pts)))
            angs-near (concat ;(take-last 1 angs-skip1)
                           (take 1 angs-skip1);2 angs-skip1)
                           (take 2 angs))
            angle-near (median-angle angs-near)
            ;; angle between the points around opposite point
            ;angle-opp (v-angle (v-sub (nth pts (inc i-opp)) (nth pts (dec i-opp))))
            angs-opp (concat (take 1 (drop i-opp angs-skip1))
                      ;(take 3 (drop (dec i-opp) angs-skip1))
                             (take 2 (drop i-opp angs)))
            ;; flip since opposite angle
            angs-opp (map #(in-pi-pi (+ % PI)) angs-opp)
            angle-opp (median-angle angs-opp)
            long-angle (median-angle (concat angs-near angs-opp))
            
            
            ;; check for straight edges - TODO - too rigid?
;            collin-0? (collinear? (last pts) (first pts) (second pts) angle-tol)
;            collin-opp? (collinear? (nth pts (dec i-opp)) (nth pts i-opp) (nth pts (inc i-opp)) angle-tol)
            ;; check for long thin shapes - always rects
            extrusion (/ (:mag (first pp-z)) (:mag (first pp)))
            strut? (>= extrusion 4.0)
            rect? (or (>= extrusion 1.9)
                      (horizontal-angle? (- angle-near angle-opp) angle-tol))
                  ;     collin-0? collin-opp?)
            ]
        (if rect?
          (let [;; find index of point opposite to the furthest
                ;i-opp-z (apply max-key
                ;               (fn [i] (abs (in-pi-pi (- far-dir (:ang (nth pp-z i))))))
                ;               (range 3 (- n 2)))
                ;; more robust? - get opposite point by flipping around cent
                far-opp-pt (mapv - far-pt)
                
                long-angle (if strut? (v-angle (v-sub far-pt far-opp-pt))
                               long-angle)
                perp-angle (+ long-angle PI_2)
                near-pt (if strut? (polar-xy 4 perp-angle)
                            near-pt)
                near-opp-pt (if strut? (polar-xy -4 perp-angle)
                                near-opp-pt)
;                _ (swank.core/break)
                ;; we have all we need: the long angle, short angle assumed perpendicular,
                ;; and points on long edge and far edges.
                ;; find vertices
                vtx [(angle-intersection near-pt long-angle far-pt perp-angle)
                     (angle-intersection far-pt perp-angle near-opp-pt long-angle)
                     (angle-intersection near-opp-pt long-angle far-opp-pt perp-angle)
                     (angle-intersection far-opp-pt perp-angle near-pt long-angle)]
                vtx (sort-by v-angle vtx)]
            ;; return: convert back to global coordinates
            (map #(v-add % cent-pt) vtx))
          ;; otherwise: is triangle.
          ;; find furthest points in 120-degree segments opposite furthest
          (let [;; find index of point in 120-degree segment offset 60-degrees above the furthest
                i-rot-fwd (apply max-key (fn [i]
                                           (let [rot (in-pi-pi (- (:ang (nth pp i)) far-dir))]
                                             (if (>= rot (/ PI 3))
                                               (:mag (nth pp i)) 0)))
                                 (range 2 (- n 1)))
                ;; find index of point in 120-degree segment offset 60-degrees below the furthest
                i-rot-back (apply max-key (fn [i]
                                            (let [rot (in-pi-pi (- (:ang (nth pp i)) far-dir))]
                                              (if (<= rot (- (/ PI 3)))
                                                (:mag (nth pp i)) 0)))
                                  (range 2 (- n 1)))
                vtx [(nth pts i-rot-back)
                     (nth pts iz)
                     (nth pts i-rot-fwd)]]
            ;; return: convert back to global coordinates
            (map #(v-add % cent-pt) vtx)))))))

(defn find-vertices-polygon
  [edge-pts cent-pt]
  (let [distance-tol 5
        angle-tol (/ PI 15)
        ;; sample points to be separated enough from each other
        tol2 (* distance-tol distance-tol)
        pts (loop [more (drop 1 edge-pts)
                   pts (vec (take 1 edge-pts))]
              (if (seq more)
                (let [curr (first more)
                      prev (peek pts)
                      d2 (v-mag2 (v-sub prev curr))]
                  (if (>= d2 tol2)
                    (recur (next more) (conj pts curr))
                    (recur (next more) pts)))
                pts))]
    ;; we know edge-pts are ordered counter-clockwise around shape.
    ;; sample by distance-tol
    ;; and eliminate points that are redundant due to collinearity
    (loop [more (drop 1 pts)
           vtx (vec (take 1 pts))]
      (let [p0 (peek vtx)
            [p1 p2] (take 2 more)]
        (if p2
          (if (collinear? p0 p1 p2 angle-tol)
            (recur (next more) vtx)
            (recur (next more) (conj vtx p1)))
          vtx)))))

(defn snap-horizontals
  [coords angle-tol]
  (loop [more coords
         vtx []]
    (let [[p1 p2] (take 2 more)
          [p1-x p1-y] p1
          [p2-x p2-y] p2]
      (if p2
        (let [ang (v-angle (v-sub p2 p1))]
          (if (and (not= p1-y p2-y)
                   (horizontal-angle? ang angle-tol))
            ;; snap to horizontal - by inserting a new point
            ;; (since adjusting prev could disturb a previous horizontal)
            (recur (next more)
                   (into vtx [p1 [p1-x p2-y]]))
            (recur (next more) (conj vtx p1))))
        vtx))))

(defn is-circular?
  [edge-pts cent-pt [x-lo x-hi] [y-lo y-hi]]
  (let [x-span (- x-hi x-lo)
        y-span (- y-hi y-lo)]
    (if (< (abs (- x-span y-span)) 8)
      ;; squarish.
      ;; simplest would be to test ratio r-max / rmin ~= 1 on edge points.
      ;; but detected shapes are noisy so can't distinguish squares.
      ;; instead we test in each of four segments and average the ratio.
      (let [segx (segment-extrema edge-pts cent-pt 4 :maxima-only? false)
            r-max (apply max (map :mag (apply concat segx)))
            rratios (map (fn [pp]
                           (if (= 2 (count pp))
                             (let [rr (map :mag pp)]
                               (/ (apply max rr) (apply min rr)))
                             0)) segx)
            avg-ratio (/ (reduce + rratios)
                         (count (remove zero? rratios)))]
        ;; return radius (logical true) if all distances about equal.
        ;; for a square we expect rmax / rmin = 1.41; for circles = 1
        (when (<= avg-ratio 1.275)
          r-max))
      ;; not squarish, so not a circle
      false)))

(defn shape-from-coords
  [coords convex? [x-lo x-hi] [y-lo y-hi] ground-level type]
  (let [edge-pts (edge-points coords [x-lo x-hi] [y-lo y-hi] ground-level)
        cent-pt [(quot (+ x-lo x-hi) 2)
                 (quot (+ y-lo y-hi) 2)]]
    ;; first, test for a circle
    (if-let [rad (is-circular? edge-pts cent-pt [x-lo x-hi] [y-lo y-hi])]
      {:shape :circle
       :radius rad
       :pos cent-pt}
      ;; not circular
      (if convex?
        (let [vtx (find-vertices-tri-quad edge-pts cent-pt type)]
          {:shape :poly
           :coords vtx})
        ;; for non-convex go straight to polygon
        (let [vtx (find-vertices-polygon edge-pts cent-pt)
              vtx-hsnap (snap-horizontals vtx (/ PI 15))]
          {:shape :poly
           :coords vtx-hsnap})))))
