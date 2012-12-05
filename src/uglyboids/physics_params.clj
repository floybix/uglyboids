(ns uglyboids.physics-params)

(def px-height 768)
(def px-width 1244)
(def ground-level 610)

(def aspect-ratio (/ px-width px-height))

;; size of world (in m) can vary across levels!
(def base-world-width 100)
(def world-width (atom base-world-width))
(def world-height (atom (/ @world-width aspect-ratio)))

(def bird-types
  #{:red-bird :blue-bird :yellow-bird})

;; note radius is in pixels
(def bird-attrs
  {:red-bird {:radius 0.56
              :radius-px 7
              :density 5
              :restitution 0.4
              :rgb [214 0 45]}
   :blue-bird {:radius 0.32
               :radius-px 4
               :density 5
               :restitution 0.4
               :rgb [99 170 197]}
   :yellow-bird {:radius 0.80
                 :radius-px 10
                 :density 5
                 :restitution 0.2
                 :rgb [241 219 32]}})

(def initial-resistances
  {:wood 2000
   :glass 2000
   :stone 5000
   :pig 50})
;; TODO: helmet-pig, tiny-pig

(def materials
  {:static {:density 1
            :friction 1.0}
   :wood {:density 2}
   :glass {:density 3
           :restitution 0}
   :stone {:density 8}
   :pig {:density 2
         :restitution 0}
   })

(def launch-speed 27.4)

(def linear-damping 0.1)
(def angular-damping 0.01)
