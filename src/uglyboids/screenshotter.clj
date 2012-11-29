(ns uglyboids.screenshotter
  (:import (ab.framework.ai ClientActionRobot)
           (ab.framework.player Configuration)))

;; need to run like this:
;; lein trampoline run -m uglyboids.screenshotter

(defn -main
  [& args]
  (let [serverip (if (seq args) (first args) "localhost")
        robot (ClientActionRobot. (into-array [serverip]))]
    (.configure robot)
    (loop [i 0]
        (println)
        (print "level number for screenshot (x-y): ")
        (flush)
        (let [this (read-line)
              f (str "level_" this ".png")]
          (if (pos? (count this))
            (do
              (.zoomingOut robot)
              (println "saving" f)
              (.screenShot robot f)
              (recur (inc i)))
            (println "exiting"))))))
