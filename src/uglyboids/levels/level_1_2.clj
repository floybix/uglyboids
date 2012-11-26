(ns uglyboids.levels.level-1-2)

;; All coordinates here are in pixels from a screenshot 1920x1080.
;; The origin pixel [0 0] is at top left - from Gimp.

(def level
  {:start [501 699]
   :birds [:red-bird :red-bird :red-bird :red-bird :red-bird]
   :objs [{:type :static
           :shape :box
           :wh [1920 230]
           :pos [960 965]}
          {:type :static
           :shape :polyline
           :coords (list [400 810]
                         [526 810]
                         [566 850]
                         [386 850]
                         [400 810])}
          {:type :static
           :shape :polyline
           :coords (list [1137 788]
                         [1538 788]
                         [1790 534]
                         [1878 622]
                         [1731 768]
                         [1812 848]
                         [1074 850]
                         [1137 788])}
         
          {:type :stone
           :shape :box
           :wh [12 52]
           :pos [1157 760]}
          {:type :wood
           :shape :box
           :wh [52 12]
           :pos [1157 728]}
          {:type :pig
           :shape :circle
           :radius 13
           :pos [1157 710]}
         
          {:type :stone
           :shape :box
           :wh [12 52]
           :pos [1242 760]}
          {:type :wood
           :shape :box
           :wh [52 12]
           :pos [1242 728]}
          {:type :pig
           :shape :circle
           :radius 13
           :pos [1242 710]}

          {:type :stone
           :shape :box
           :wh [12 52]
           :pos [1330 760]}
          {:type :wood
           :shape :box
           :wh [52 12]
           :pos [1330 728]}
          {:type :pig
           :shape :circle
           :radius 13
           :pos [1330 710]}

          {:type :stone
           :shape :box
           :wh [12 102]
           :pos [1467 735]}
          {:type :wood
           :shape :box
           :wh [52 12]
           :pos [1467 678]}
          {:type :pig
           :shape :circle
           :radius 13
           :pos [1467 660]}
          ]
   })
