(ns uglyboids.client
  (:gen-class)
  (:import (ab.framework.ai ClientActionRobot)
           (ab.framework.other Shot)
           (ab.framework.player Configuration)
           (java.util ArrayList))
  (:use uglyboids.core
        uglyboids.physics-params
        [uglyboids.vision :only [scene-from-image-file]]
        [cljbox2d.vec2d :only [TWOPI PI in-pi-pi polar-xy]]))

(def env-path "vision/Matlab/")

(def shots-left (atom -1))

(defn scene-snapshot!
  [robot]
  (.screenShot robot "im.png")
  (let [new-scene (scene-from-image-file (str env-path "im.png"))]
    (reset! scene new-scene)))

(defn do-shots!
  [robot shots]
  (let [al (ArrayList.)
        [x0-px y0-px] (world-to-px @focus-world)
        mag-px 100]
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

(defn simulate-shot!
  [shot]
  (shoot! (:angle shot))
  (simulate-for (+ (:flight shot) 3)))

(defn bangbangbang!
  [robot]
  (if (zero? @shots-left)
    (do
      (.finishRun robot)
      (println "no shots left")
      false)
    (do
      (scene-snapshot! robot)
      (if (= (:state @scene) :in-play)
        (do
          (println "building world...")
          (setup-world!)
          (when (neg? @shots-left)
            (reset! shots-left (count (:birds scene))))
          (println "naively choosing a shot...")
          (let [shot (choose-shot)]
            (println "estimating effects...")
            (try
              (let [foo (future (simulate-shot! shot))]
                (deref foo 1000 nil))
              (catch Exception e (println (.getMessage e))))
            (do-shots! robot [shot])
            true))
        ;; end state
        (do
          (.finishRun robot)
          (println "level ended:" (:state @scene))
          false)))))

(defn -main
  [& args]
  (let [serverip (if (seq args) (first args) "localhost")
        robot (ClientActionRobot. (into-array [serverip]))]
    ;; TODO - how to pass in team id?
    (.configure robot)
    (.loadLevel robot (int-array [1]))
    (loop [i 0]
      (if (bangbangbang! robot)
        ;; keep going
        (recur (inc i))
        ;; level finished
        (let [conf (.getConfiguration robot)
              ;;(.nextLevel robot)
              new-ok? (.loadLevel robot (int-array (.getMax_level conf)))]
          (println "configuration: " conf)
          (if new-ok?
            (do
              (reset! shots-left -1)
              (println "next level command ok.")
              (recur (inc i)))
            ;; finish up
            (do
              (println "next level command failed, exiting.")
              (println (.getConfiguration robot))
              (.finishPlay robot))))))))
