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
(.mkdirs (java.io.File. env-path))

(def ^ClientActionRobot robot nil)

(def team "Zonino")

(def curr-level (atom -1))
(def max-level (atom 1))

;; stores info about each level by index
;; each as an (atom) map, with
(defn blank-level-info []
  {:init-scene nil
   :first-shots #{} ;; shots taken from initial state
   :shot-lists [] ;; seq of each attempt: lists of shots
   :solution nil ;; list of shots of best successful attempt
   :attempts 0
   :failures 0})

(def memory (vec (repeatedly 20 #(atom (blank-level-info)))))

(defn curr []
  (nth memory @curr-level))

(def curr-shots (atom []))

(defn scene-snapshot!
  []
  (.screenShot robot "im.png")
  (let [new-scene (scene-from-image-file (str env-path "im.png"))]
    (reset! scene new-scene)
    new-scene))

(defn refresh-configuration!
  []
  (let [conf (.getConfiguration robot team)]
    (swap! max-level max (.getMax_level conf))
    (reset! curr-level (.getCurrent_level conf))
    (println "current level " @curr-level ", max" @max-level)))

(defn load-level!
  "Note i can be -1 to load the current level, e.g. at start.
   This only sets 'scene', it does not build the world."
  [i]
  (let [ok? (try
              (println "requesting to load level" i)
              (.loadALevel robot i)
              (catch Exception e
                (println (.getMessage e))
                false))]
    ;; important - need this to update curr-level
    (refresh-configuration!)
    (when ok?
      (let [cached-scene (:init-scene @(curr))]
        (if (or (nil? cached-scene)
                (= (mod (:failures @(curr)) 2) 1))
          (scene-snapshot!)
          (reset! scene (:init-scene @(curr)))))
      (reset! curr-shots []))))

(defn choose-and-load-a-level!
  []
  (let [ok? (load-level! @max-level)]
    (when-not ok?
      (refresh-configuration!)
      (swap! max-level dec)
      (recur))))

(defn do-shots!
  [shots]
  (let [al (ArrayList.)
        [x0-px y0-px] (world-to-px @focus-world)
        mag-px 100]
    (doseq [shot shots
            :let [ang (:angle shot)
                  tap (:ab-tap-t shot)
                  tap-ms (* tap 1000)]]
      (let [[dx dy] (polar-xy mag-px ang)
            ;; drag opposite to launch angle
            drag-x (- dx)
            ;; y is double negated since y in pixels is flipped
            drag-y (- (- dy))]
        (.add al (Shot. x0-px y0-px drag-x drag-y 0 tap-ms))))
    (.shoot robot al)))

(defn -main
  [& args]
  (let [serverip (if (seq args) (first args) "localhost")
        rbt (ClientActionRobot. (into-array [serverip]))
        start-level -1]
    (alter-var-root (var robot) (fn [_] rbt))
    (.configure robot team)
    (load-level! start-level)
    (while true
      (if (and (= :in-play (:state @scene))
               (<= (count @curr-shots) 8)) ;; give up after 8 shots
        (do
          (println "building world...")
          (setup-world! @scene)
          ;; TODO: select from a list of possible shots, exclude those already tried
          (let [attempts (:attempts @(curr))
                shot (try (choose-shot attempts)
                          (catch Exception e
                            (println (.getMessage e))
                            (choose-shot-naive attempts)))]
            (swap! curr-shots conj shot)
            (when (= 1 (count @curr-shots))
              (swap! (curr) update-in [:first-shots] conj shot))
            (println "taking" (if (:direct? shot) "direct" "mortar")
                     "shot at" (:target-type shot) (:target-pt shot))
            (do-shots! [shot])
            (scene-snapshot!)))
        (do
          (println "level ended with" (:state @scene))
          (swap! (curr) update-in [:attempts] inc)
          (swap! (curr) update-in [:shot-lists] conj @curr-shots)
          (if (= :success (:state @scene))
            (do
              (swap! (curr) assoc
                     :solution @curr-shots)
              (swap! max-level max (inc @curr-level)))
            ;; otherwise must be failure
            (do
              (swap! (curr) update-in [:failures] inc)
              ))
          (refresh-configuration!)
          (choose-and-load-a-level!))))))

