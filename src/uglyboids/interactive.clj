(ns uglyboids.interactive
  (:gen-class)
  (:use uglyboids.core
        uglyboids.physics-params
        cljbox2d.core
        [cljbox2d.testbed :exclude [world-to-px-scale
                                    world-to-px
                                    px-to-world
                                    ground-body
                                    set-buffering-contact-listener!
                                    contact-buffer]]
        [cljbox2d.vec2d :only [v-mag v-angle v-sub]])
  (:require [quil.core :as quil]
            uglyboids.vision
            uglyboids.levels.level-1-2))

(def aiming? (atom false))

(defn my-key-press []
  (case (quil/raw-key)
    \n (next-bird!)
    \r (setup-world! @scene)
    \s (let [shot (choose-shot)]
         (println "chose angle" (:angle shot)
                  "with flight time" (:sim-flight shot))
         (wake! (:target-body shot))
         (shoot! (:angle shot)))
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
    (let [offs (v-sub @focus-world (mouse-world))
          mag (v-mag offs)
          ang (v-angle offs)]
      (shoot! ang)
      (reset! aiming? false))))

(defn draw-more []
  (when @aiming?
    (quil/stroke (quil/color 255 255 255))
    (quil/line (cljbox2d.testbed/world-to-px (mouse-world))
               (cljbox2d.testbed/world-to-px @focus-world))))

(defn setup []
  (quil/frame-rate (/ 1 *timestep*))
  (println "building world...")
  ;(setup-world!)
  (reset! camera
          {:width @world-width :height @world-height :x-left 0 :y-bottom 0})
  (reset! draw-more-fn draw-more))

(defn -main
  "Run the sketch."
  [& args]
  (let [load-scene (if-let [screenshot (first args)]
                     (uglyboids.vision/scene-from-image-file screenshot)
                     ;; default
                     uglyboids.levels.level-1-2/level)]
    (reset! scene load-scene)
    (setup-world! load-scene))
  (quil/defsketch the-sketch
    :title "Ugly boids"
    :setup setup
    :draw draw
    :key-typed my-key-press
    :mouse-pressed my-mouse-pressed
    :mouse-released my-mouse-released
    :mouse-dragged my-mouse-dragged
    :size [px-width px-height]))
