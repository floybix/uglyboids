(ns uglyboids.vision.params)

;; based on files
;; vision/Matlab/segmentationColorInitialization.m
;; vision/Matlab/segmentationThresholdInitialization.m
;; vision/Matlab/segmentationBodyPixelLimitInitialization.m
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

(def object-params
  (array-map
   :sky {:colors (list [148,206,222]
                       [169,215,229]
                       [206,233,242]
                       [220,240,255]
                       [187,203,241]
                       [243,250,255])
         :tolerance -20
         :size [2 1e7]}
   :red-bird {:colors (list [214,0,45]  ; red
                            [226,196,168] ; bottom
                            [206,179,153] ; bottom
                            [202,131,26]  ; dark beak
                            [85,0,18]     ; borders
                            [255,255,255] ; eyes
                            [30,25,30]    ; borders
                            [0,0,0]       ; black border
                            [251,186,31]  ; black beak
                            [51,8,17]     ; black border
                            [204,196,197]) ; eye shade grey
              :tolerance 10
              :size [90 1000]}          ; was 0
   :wood {:colors (list [226,145,38]
                        [210,127,31]
                        [255,190,99]
                        [183,106,38])
                        ;[144,72,19])    ; border
          :tolerance 18
          :size [10 5000]}
   :ground {:colors (list [10,19,57]    ; blue
                          [21,32,83]    ; blue
                          [14,25,17]    ; blue
                          [31,45,103])   ; blue
                          ;[51,110,21]   ; green
                          ;[82,153,11]   ; green
                          ;[194,254,13]) ; green
            :tolerance 20
            :size [200 1e7]}
   :trajectory {:colors (list [255,255,255]
                              [239,239,239])
                :tolerance 3
                :size [10 30]}
   :tap {:colors (list [243,243,243]
                       [220,220,220])
         :tolerance 6
         :size [60 800]}
   :pig {:colors (list [109,226,73]     ;
                       [165,233,0]      ; nose
                       [202,251,16]     ; nose
                       [116,182,6]      ; nose
                       [92,189,48]      ; around eyes
                       [138,202,0])      ; border
                       ;[111,228,74]     ; border
                       ;[0,0,0]          ; border black
                       ;[5,39,17]        ; border black
                       ;[135,190,107])   ; border black
         :tolerance 18 ;28
         :size [90 2e9]}
   :glass {:colors (list [113,206,248]
                         [99,194,245]
                         [148,218,250]
                         [130,209,248])
                         ;[231,247,254]) ; ice-white shine, same as sky
           :tolerance 13
           :size [20 5000]}
   :stone {:colors (list [160,160,160]
                         [130,130,130]
                         [148,148,148])
           :tolerance 5
           :size [20 5000]}
   :static-wood {:colors (list [202,151,94]
                               [127,65,32]
                               [166,112,53]
                               [48,23,8])
                 :tolerance 24
                 :size [90 5000]}
   :static-surface {:colors (list [52 34 19]
                                  [65 42 24]
                                  [126 92 66]
                                  [162 122 91])
                    :tolerance 28
                    :size [100 1e9]}
   :blue-bird {:colors (list [99,170,197]
                             [255,174,0])
               :tolerance 20
               :size [20 1000]}
   :yellow-bird {:colors (list [241,219,32])
                 :tolerance 20
                 :size [50 1000]}
   ))

(def bird-types
  #{:red-bird :blue-bird :yellow-bird})
