(defproject uglyboids "0.1.0-SNAPSHOT"
  :description "A program to play Angry Birds from screenshots.
                An entry in the AI12 Angry Birds Challenge."
  :url "http://github.com/floybix/uglyboids"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[cljbox2d "0.2.0-SNAPSHOT"]
                 [seesaw "1.4.2"]
                 [org.clojure/clojure "1.4.0"]]
  :resource-paths ["lib-extras"
                   "lib-extras/marvin/framework/marvin_1.4.2.jar"])
