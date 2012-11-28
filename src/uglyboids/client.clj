(ns uglyboids.client
  (:import (ab.framework.ai ClientActionRobot)
           (ab.framework.other Shot)
           (ab.framework.player Configuration)
           (java.util ArrayList))
  (:use uglyboids.core
        [uglyboids.vision :only [scene-from-image-file]]
        [cljbox2d.vec2d :only [TWOPI PI in-pi-pi polar-xy]]))

;(def robot (atom nil))

(def env-path "vision/Matlab/")

(def shots-left (atom -1))

(defn level-end-state?
  []
  ;; check number of birds and number of pigs
  (let [n-pigs (count @pigs)
        n-birds (+ (count @bird-queue)
                   (if (nil? @bird) 0 1))]
    (if (zero? n-pigs)
      "success"
      ;; check for giant level-failed pig:
      (if false ;(and (= n-pigs 1)
          ;     (> (radius (fixture (first @pigs)))
          ;        10))
        "failure"
        (if (zero? n-birds)
          "failure"
          ;; else
          false)))))

(defn scene-snapshot!
  [robot]
  (.screenShot robot "im.png")
  (let [new-scene (scene-from-image-file (str env-path "im.png"))]
    (reset! scene new-scene)
    (println "building world...")
    (setup-world!)
    (when (neg? @shots-left)
      (reset! shots-left (count (:birds scene))))))

(defn do-shots!
  [robot shots]
  (let [al (ArrayList.)
        [x0-px y0-px] (world-to-px @focus-world)
        mag-px (- x0-px 10)]
    (doseq [shot shots
            :let [ang (:angle shot)
                  tap (:tap-t shot)
                  tap-ms (* tap 1000)]]
      (let [[dx dy] (polar-xy mag-px ang)
            ;; drag opposite to launch angle
            drag-x (- dx)
            ;; y is double negated since y in pixels is flipped
            drag-y (- (- dy))]
        (.add al (Shot. x0-px y0-px drag-x drag-y 0 tap-ms))
        (swap! shots-left dec)))
    (.shoot robot al)))

(defn bangbangbang!
  [robot]
  (if (zero? @shots-left)
    (do
      (.finishRun robot)
      (println "no shots left")
      false)
    (do
      (scene-snapshot! robot)
      (if-let [end-state (level-end-state?)]
        (do
          (.finishRun robot)
          (println "level ended:" end-state)
          false)
        ;; still in game
        (do
          (println "naively choosing a shot...")
          (let [shot (choose-shot)]
            (do-shots! robot [shot])
            true))))))

(defn -main
  [& args]
  (let [serverip (if (seq args) (first args) "localhost")
        robot (ClientActionRobot. (into-array [serverip]))]
    ;; TODO - how to pass in team id?
    (.configure robot)
    ;; nextLevel does not the require segmentation. However, nextLevel can only be called when
    ;; a level is completed. loadLevel can be called during any time in the game.
    ;; ar.loadLevel(conf.getMax_level() will achieve the same result as nextLevel method
    ;(.nextLevel robot)
    (loop [i 0]
      (if (bangbangbang! robot)
        ;; keep going
        (recur (inc i))
        ;; level finished
        (let [conf (.getConfiguration robot)
              ;;(.nextLevel robot)
              new-ok? (.loadLevel robot (.getMax_level conf))]
          (println "configuration: " conf)
          (if new-ok?
            (do
              (reset! shots-left -1)
              (println "next level command ok.")
              (recur (inc i)))
            ;; finish up
            (do
              (println (.getConfiguration robot))
              (.finishPlay robot))))))))
