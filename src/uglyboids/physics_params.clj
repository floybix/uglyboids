(ns uglyboids.physics-params)

(def px-height 768)
(def px-width 1244)
(def ground-level 612)

(def aspect-ratio (/ px-width px-height))

(def world-width 100)
(def world-height (/ world-width aspect-ratio))

;; note radius is in pixels
(def bird-attrs
  {:red-bird {:radius 9;12
              :density 5
              :restitution 0.4}
   :blue-bird {:radius 4;6
               :density 5
               :restitution 0.4}
   :yellow-bird {:radius 10
                 :density 5
                 :restitution 0.2}})

(def materials
  {:static {:density 1}
   :wood {:density 1}
   :glass {:density 1}
   :stone {:density 6}
   :pig {:density 2}
   })

(def launch-speed 27)

(def linear-damping 0.2)
(def angular-damping 0.01)
