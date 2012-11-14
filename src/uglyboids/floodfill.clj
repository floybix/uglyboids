(ns uglyboids.floodfill)

(defn scanline
  "Scanline Floodfill Algorithm. Adapted from
   http://lodev.org/cgtutor/floodfill.html#Scanline_Floodfill_Algorithm_With_Stack"
  [x0 y0 test mark [min-x max-x] [min-y max-y]]
  (loop [stack (conj [] [x0 y0])]
    (when (seq stack)
      (let [[x y] (peek stack)
            ;; follow y pixels down until find an edge
            y1 (loop [y* y]
                 (if (and (>= y* min-y) (test [x y*]))
                   (recur (dec y*))
                   (inc y*)))
            more (loop [y1 y1
                        span-left false
                        span-right false
                        sub-stack []]
                   (if (and (<= y1 max-y) (test [x y1]))
                     (do
                       (mark [x y1])
                       (let [go-left (and (not span-left)
                                          (> x min-x)
                                          (test [(dec x) y1]))
                             stop-left (and span-left
                                            (> x min-x)
                                            (not (test [(dec x) y1])))
                             go-right (and (not span-right)
                                           (< x max-x)
                                           (test [(inc x) y1]))
                             stop-right (and span-right
                                             (< x max-x)
                                             (not (test [(inc x) y1])))
                             new-left (if go-left true
                                          (if stop-left false
                                              span-left))
                             new-right (if go-right true
                                           (if stop-right false
                                               span-right))
                             to-push (concat
                                      (when go-left (list [(dec x) y1]))
                                      (when go-right (list [(inc x) y1])))]
                         (recur (inc y1) new-left new-right
                                (into sub-stack to-push))))
                     ;; return from inner loop
                     sub-stack))]
        (recur (into (pop stack) more))))))

(defn scanline-r
  "Recursive Scanline Method. Adapted from
   http://www.codeproject.com/Articles/6017/QuickFill-An-efficient-flood-fill-algorithm"
  [x1 x2 y test mark [min-x max-x] [min-y max-y]]
  (when (<= min-y y max-y)
    ;; scan left
    (let [left-xs (doall (for [x (range x1 min-x -1)
                               :while (test [x y])]
                           (do (mark [x y])
                               (dec x))))
          leftmost (if (empty? left-xs) x1 (last left-xs))]
      (when (< leftmost x1)
        (scanline-r leftmost x1 (dec y) test mark [min-x max-x] [min-y max-y])
        (scanline-r leftmost x1 (inc y) test mark [min-x max-x] [min-y max-y]))
      ;; scan right
      (let [right-xs (doall (for [x (range x2 max-x)
                                  :while (test [x y])]
                              (do (mark [x y])
                                  (inc x))))
            rightmost (if (empty? right-xs) x2 (last right-xs))]
        (when (> rightmost x2)
          (scanline-r x2 rightmost (dec y) test mark [min-x max-x] [min-y max-y])
          (scanline-r x2 rightmost (inc y) test mark [min-x max-x] [min-y max-y]))
        ;; scan betweens
        (let [x1* (if (< leftmost x1) (inc x1) x1)
              x2* (if (> rightmost x2) (dec x2) x2)]
          (loop [x-from x1*
                 x x1*]
            (when (<= x (min x2* max-x))
              (if (test [x y])
                (do
                  (mark [x y])
                  (recur x-from (inc x)))
                (do
                  (when (> x x-from)
                    (scanline-r x-from (dec x) (dec y) test mark [min-x max-x] [min-y max-y])
                    (scanline-r x-from (dec x) (inc y) test mark [min-x max-x] [min-y max-y]))
                  (let [skip-xs (doall (for [x (range (inc x) (inc (min x2* max-x)))
                                             :while (not (test [x y]))]
                                         x))
                        nextx (if (empty? skip-xs) (inc x) (last skip-xs))]
                    (recur nextx nextx)))))))))))
