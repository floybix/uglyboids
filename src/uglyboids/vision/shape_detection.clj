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


(defn edge-points
  "Return the set of points which are on the edge of a shape:
specifically the left-/right-most or top-/bottom-most points on each
horizontal or vertical coordinate."
  [coords [min-x max-x] [min-y max-y]]
  (let [rng-x (inc (- max-x min-x))
        rng-y (inc (- max-y min-y))
        min-ok (fn [a b] (if (nil? a) b (min a b)))
        max-ok (fn [a b] (if (nil? a) b (max a b)))]
    (loop [pts coords
           left-xs (vec (repeat rng-y nil))
           right-xs (vec (repeat rng-y nil))
           bot-ys (vec (repeat rng-x nil))
           top-ys (vec (repeat rng-x nil))]
      (if (seq pts)
        (let [[x y] (first pts)
              iy (- y min-y)
              ix (- x min-x)]
          (recur (next pts)
                 (update-in left-xs [iy] min-ok x)
                 (update-in right-xs [iy] max-ok x)
                 (update-in bot-ys [ix] min-ok y)
                 (update-in top-ys [ix] max-ok y)))
        ;; merge sets of points
        (let [to-points-lr (fn [i x] [x (+ i min-y)])
              to-points-tb (fn [i y] [(+ i min-x) y])
              point-sets [(set (map-indexed to-points-lr left-xs))
                          (set (map-indexed to-points-lr right-xs))
                          (set (map-indexed to-points-tb bot-ys))
                          (set (map-indexed to-points-tb top-ys))]
              points (reduce clojure.set/union point-sets)]
          (clojure.set/select #(not-any? nil? %) points))))))

(defn segment-extrema
  "Finds the nearest and furthest points from the mid-point in each
   angle segment. Returns a vector of length n-segments, each with 0
   to 2 points (0 points if no points were found in that angle
   segment) in local polar coordinates as {:mag :ang}.
   Ordered by angle, from -pi to pi."
  [edge-pts mid-pt n-segments
   & {:keys [maxima-only?] :or {maxima-only? false}}]
  (let [seg-ang (/ TWOPI n-segments)]
    (loop [pts (seq edge-pts)
           seg-extrema (vec (repeat n-segments
                                    {:far nil :near nil}))]
      (if (seq pts)
        (let [pt (first pts)
              dxy (v-sub pt mid-pt)
              ang (v-angle dxy)
              mag2 (v-mag2 dxy) ;; distance squared
              pt-polar {:mag mag2 :ang ang}
              iseg (mod (int (quot (+ ang PI) seg-ang)) n-segments)]
          (recur (next pts)
                 (update-in seg-extrema [iseg]
                            (fn [{:keys [far near]}]
                              (let [is-far? (or (nil? far)
                                                (> mag2 (:mag far)))
                                    is-near? (or (nil? near)
                                                 (< mag2 (:mag near)))]
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

(defn median
  [coll]
  (nth (sort coll) (quot (count coll) 2)))

(defn v-avg
  [pts]
  (let [n (count pts)]
    (mapv #(/ % n) (reduce v-add pts))))

(defn vertical-angle?
  "Within +/- 1.5 degrees."
  [ang]
  (let [tol-degrees 1.5]
    (< (abs (- (abs (in-pi-pi ang)) PI_2))
       (* tol-degrees (/ PI 180.0)))))

(defn horizontal-angle?
  "Within +/- 1.5 degrees."
  [ang]
  (vertical-angle? (in-pi-pi (+ ang PI_2))))

(defn line-intersection
  "Point of intersection of two lines in the plane.
   First line passes through [x0 y0] with gradient m0.
   Other line passes through [x1 y1] with gradient m1.
   Gradient can be passed as nil for vertical lines."
  [[x0 y0] m0 [x1 y1] m1]
  (cond
   ;; vertical line at x = x0
    (nil? m0)
    (let [y (+ (* m1 (- x0 x1)) y1)]
      [x0 y])
    ;; vertical line at x = x1
    (nil? m1)
    (let [y (+ (* m0 (- x1 x0)) y0)]
      [x1 y])
    ;; general equation
    ;; x = (m0.x0 + y0 - y1) /
    ;;      (m1 - m0)
    ;; y = m0 (x - x0) + y0
    :else
    (let [x (/ (+ (* m0 x0) y0 (- y1))
               (- m1 m0))
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

(defn find-vertices
  [edge-pts mid-pt angle-tol distance-tol convex?]
  (let [n-segments 32
        segx (segment-extrema edge-pts mid-pt n-segments
                              :maxima-only? convex?)
        n-segs-ok (count (filter seq segx))]
    (when (>= n-segs-ok (quot n-segments 2))
      (let [;; collapse to a flat sequence of polar points (from segments)
            all-pp (apply concat segx)
            ;; find index of single furthest point (TODO: use reduce?)
            i0 (apply max-key (fn [i] (:mag (nth all-pp i)))
                      (range (count all-pp)))
            ;; start from most distant point as it must be a true vertex
            pp (concat (drop i0 all-pp) (take i0 all-pp))
            ;; convert back to local rectangular coordinates (origin=mid-pt)
            pts (map (fn [{:keys [mag ang]}] (polar-xy mag ang)) pp)
            ;; eliminate points too close to each other, keeping furthest
            too-close? (mapv (fn [i [xy0 xy1 xy2]]
                               ;; decide whether to omit the middle point xy1
                               ;; only consider excluding every second point
                               (and (= (mod i 2) 1)
                                    (or
                                     (< (v-dist xy1 xy0) distance-tol)
                                     (< (v-dist xy1 xy2) distance-tol))))
                             (range (count pts))
                             (triples-wrapped pts))
            pp-s (keep-indexed (fn [i x] (when-not (nth too-close? i) x)) pp)
            pts-s (keep-indexed (fn [i x] (when-not (nth too-close? i) x)) pts)
                                        ;pp-s (if convex? pp pp-s)
                                        ;pts-s (if convex? pts pts-s)
            n (count pts-s)
            ;; check for a long thin shape since it can be assumed a rect.
            ang0 (:ang (first pp-s))
            ;; find index of point with opposite angle
            i-opp (apply max-key
                         (fn [i] (abs (in-pi-pi (- ang0 (:ang (nth pp-s i))))))
                         (range 3 (- n 2)))
                                        ;i-opp (/ n 2)
            i-perp-a (quot i-opp 2)
            i-perp-b (quot (+ i-opp n) 2)
            longways (+ (:mag (first pp-s)) (:mag (nth pp-s i-opp)))
            sideways (+ (:mag (nth pp-s i-perp-a)) (:mag (nth pp-s i-perp-b)))
            is-strut? (and (>= longways 42)
                           (<= sideways 42)
                           (>= (/ longways sideways) 3.1))]
        (if is-strut?
          ;; in Angry Birds long sides are always parallel.
          ;; for struts (long thin rects) assume right angles
          (let [pad-a (if (>= i-opp 6) 2 1)
                pad-b (if (>= (- n i-opp) 6) 2 1)
                pts-side-a (drop pad-a (drop-last pad-a (take i-opp pts-s)))
                pts-side-b (drop pad-b (drop-last pad-b (drop i-opp pts-s)))
                angs-side-a (map (fn [[p1 p2]] (v-angle (v-sub p2 p1)))
                                 (partition 2 1 pts-side-a))
                angs-side-b (map (fn [[p1 p2]] (v-angle (v-sub p1 p2))) ;; reverse points for other side
                                 (partition 2 1 pts-side-b))
                angs-all (concat angs-side-a angs-side-b)
                ;; estimate angle of the long sides (parallel) as median of each pair
                ;; correct for discontinuity at -pi/+pi
                angs-all* (if (> (median (map abs angs-all)) (* 0.8 PI))
                            (map #(if (neg? %) (+ % TWOPI) %) angs-all)
                            angs-all)
                long-ang (in-pi-pi (median angs-all*))
                ;; vertical represented as nil gradient
                long-grad (angle-to-gradient long-ang)
                perp-grad (angle-to-gradient (+ long-ang PI_2))
                ;_ (swank.core/break)
                med-pt-a (v-avg pts-side-a)
                med-pt-b (v-avg pts-side-b)
                far-pt (first pts-s)
                opp-pt (mapv - far-pt) ;; flip around mid-pt (local coords)
                ;; start from far-pt and follow original order (increasing angle)
                vtx [(line-intersection far-pt perp-grad med-pt-a long-grad)
                     (line-intersection med-pt-a long-grad opp-pt perp-grad)
                     (line-intersection opp-pt perp-grad med-pt-b long-grad)
                     (line-intersection med-pt-b long-grad far-pt perp-grad)]]
            ;; return: convert back to global coordinates
            (map #(v-add % mid-pt) vtx))
          ;; otherwise, general shapes
                                        ;(map #(v-add % mid-pt) pts-s))))
          ;; eliminate points that are redundant due to collinearity
          (loop [lines []
                 more-pts (concat pts-s (take 1 pts-s))
                 cur-angle (v-angle (v-sub (second pts-s) (first pts-s)))
                 cur-anchor (first pts-s)]
            (let [[p0 p1 p2] (take 3 more-pts)]
              (if p2
                (if (collinear? p0 p1 p2 angle-tol)
                  (recur lines
                         (next more-pts)
                         (v-angle (v-sub p1 p0))
                         p1)
                  ;; not collinear, so is a vertex: store the line up to here
                  (recur (conj lines {:ang cur-angle :pt cur-anchor})
                         (next more-pts)
                         (v-angle (v-sub p2 p1))
                         (v-avg [p1 p2])))
                ;; return: find vertices as intersections of lines
                (loop [vtx []
                       lines (concat lines (take 1 lines))]
                  (let [[line0 line1] (take 2 lines)]
                                        ;(swank.core/break)
                    (println line0 line1)
                    (if line1
                      (let [ang-diff (- (:ang line1) (:ang line0))
                            bad-spike? (horizontal-angle? ang-diff)
                            new-vtx (if bad-spike? (:pt line1)
                                        (angle-intersection (:pt line0)
                                                            (:ang line0)
                                                            (:pt line1)
                                                            (:ang line1)))]
                        (recur (conj vtx new-vtx)
                               (next lines)))
                      ;; return: convert back to global coordinates
                      (map #(v-add % mid-pt) vtx))))))))))))


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

(defn shape-from-coords
  [coords [min-x max-x] [min-y max-y] convex?]
  (let [edge-pts (edge-points coords [min-x max-x] [min-y max-y])
        mid-pt [(* 0.5 (+ min-x max-x))
                (* 0.5 (+ min-y max-y))]
        angle-tol (/ PI 8)
        distance-tol 6
        vtx (find-vertices edge-pts mid-pt
                           angle-tol distance-tol convex?)
        n-vtx (count vtx)
        int-angles (map interior-angle (triples-wrapped vtx))
        int-degrees (map #(* 180 (/ % PI)) int-angles)]
    (cond
     (<= n-vtx 2) {:shape nil
                   :coords vtx}
     (= n-vtx 3) {:shape :poly
                  :coords vtx
                  :angles int-degrees}
     ;; quadrilateral.
     (= n-vtx 4) (let [fwd-dists (map (fn [[p0 p1 p2]] (v-dist p1 p2))
                                      (triples-wrapped vtx))
                       length (apply max fwd-dists)
                       width (second (sort fwd-dists))]
                   {:shape :poly
                    :coords vtx
                    :length length
                    :width width
                    :angles int-degrees})
     ;; if all vertices are same distance from mid-pt then circle.
     (>= n-vtx 5) (let [dists (map #(v-dist % mid-pt) vtx)
                        r-max (apply max dists)
                        r-min (apply min dists)]
                    (if (< (- r-max r-min) 16)
                      {:shape :circle
                       :radius r-max
                       :pos mid-pt}
                      {:shape :poly
                       :coords vtx
                       :angles int-degrees})))))
