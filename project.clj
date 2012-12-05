(defproject uglyboids "0.9.2-SNAPSHOT"
  :description "A program to play Angry Birds from screenshots.
                An entry in the AI12 Angry Birds Challenge."
  :url "http://github.com/floybix/uglyboids"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[cljbox2d "0.2.0-SNAPSHOT"]
                 [seesaw "1.4.2"]
                 [org.clojure/clojure "1.4.0"]]
  :java-source-paths ["java-src"]
  :main uglyboids.client
  :aot [uglyboids.client uglyboids.vision uglyboids.interactive])
