(ns uglyboids.physics-params)

(def px-height 1080)
(def px-width 1920)

;; note radius is in pixels
(def bird-attrs
  {:red {:radius 12
         :density 5
         :restitution 0.4}
   :blue {:radius 6
          :density 5
          :restitution 0.4}})

(def materials
  {:static {:density 1}
   :wood {:density 1}
   :glass {:density 1}
   :stone {:density 6}
   :pig {:density 2}
   })

(def launch-speed 27)

(def linear-damping 0.2)
