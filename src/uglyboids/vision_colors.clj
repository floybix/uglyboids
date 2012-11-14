(ns uglyboids.vision-colors)

;; based on file vision/Matlab/segmentationColorInitialization.m
;;
;;% This software is distributed under terms of the BSD license.
;;% See the LICENSE file for details.
;;% SEGMENTATION-COLOR-INITIALIZATION
;;% ZAIN UL HASSAN <zainulhassan4@gmail.com>
;;%
;;% Matlab function to add body colors each detected body can have
;;% from a given screenshot of the game ANGRY BIRDS
;;% to detect supervised objects.


;; Note that the first color in each list is the seed color.
(def ab-colors
  {:sky (list [148,206,222]
              [169,215,229]
              [206,233,242]
              [220,240,255]
              [187,203,241]
              [243,250,255])
   :red-bird (list [214,0,45]           ; red
                   [226,196,168]        ; bottom
                   [206,179,153]        ; bottom
                   [202,131,26]         ; dark beak
                   [85,0,18]            ; borders
                   [255,255,255]        ; eyes
                   [30,25,30]           ; borders
                   [0,0,0]              ; black border
                   [251,186,31]         ; black beak
                   [51,8,17]            ; black border
                   [204,196,197])       ; eye shade grey
   :wood (list [226,145,38]
               [210,127,31]
               [255,190,99]
               [183,106,38]
               [144,72,19])             ; border ;; TODO skip
   :ground (list [10,19,57]             ; blue
                 [21,32,83]             ; blue
                 [14,25,17]             ; blue
                 [31,45,103]            ; blue
                 [51,110,21]            ; green
                 [82,153,11]            ; green
                 [194,254,13])          ; green
   :trajectory (list [255,255,255]
                     [239,239,239])
   :tap (list [243,243,243]
              [220,220,220])
   :pig (list [109,226,73]              ;
              [165,233,0]               ; nose
              [202,251,16]              ; nose
              [116,182,6]               ; nose
              [92,189,48]               ; around eyes
              [138,202,0]               ; border
              [111,228,74]              ; border
              [0,0,0]                   ; border black
              [5,39,17]                 ; border black
              [135,190,107])            ; border black
   :glass (list [99,194,245]
                [113,206,248]
                [148,218,250]
                [130,209,248]
                [231,247,254])          ; ice-white shine, same as sky
   :stone (list [160,160,160]
                [130,130,130]
                [148,148,148])
   :static-wood (list [202,151,94]
                      [127,65,32]
                      [166,112,53]
                      [48,23,8])
   :blue-bird (list [99,170,197]
                    [255,174,0])
   :yellow-bird (list [241,219,32])
   })

(def seed-colors (into {} (map (fn [[k v]] [k (first v)]) ab-colors)))


;; based on file vision/Matlab/segmentationThresholdInitialization.m

;;% Matlab function to add color threshold each detected body can have
;;% from a given screenshot of the game ANGRY BIRDS to detect supervised
;;% objects. ie how much difference in color is regarded as the shade of
;;% the same object

(def color-tolerance
  {:sky        -20
   :red-bird    20
   :wood        17
   :ground     -10
   :trajectory   3
   :tap          6
   :pig         28
   :glass       15
   :stone        5
   :static-wood 20
   :blue-bird   20
   :yellow-bird 20})


;; based on file vision/Matlab/segmentationBodyPixelLimitInitialization.m

(def size-range
  {:sky         [2 1e7]
   :red-bird    [90 1000] ; was 0
   :wood        [10 5000]
   :ground      [200 1e7] ; was 2
   :trajectory  [5 30]
   :tap         [60 800]
   :pig         [90 2e9]
   :glass       [20 5000]
   :stone       [20 5000] ; was 10
   :static-wood [200 5000]
   :blue-bird   [90 1000]
   :yellow-bird [90 1000]})
