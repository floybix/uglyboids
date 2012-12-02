
## Priorities ##

* version 1.2s issues
* impact effects
* estimate pigs killed
* ROBUSTIFY - various levels of detail
  * use (try) on each shape detection, etc.
  * need to destroy World when creating a new one to avoid overflow?
  * if shape detect fails after shooting, use simulation from initial

## Client Strategy ##
* robot/configure /getConfiguration name?
* first try to complete all levels
  * up to 40 minutes
* then go back to other levels where
  * we are being beaten by 10k
  * completed with 3+ shots
* cache each level scene to avoid screenshots
* first time, try launching all shots at once
  * if that doesn't work, do one at a time
* first time, go for
  * (1) closest pig direct shot
  * (2) furthest pig mortar shot
* later, simulate permutations

## Vision ##

* fix up bad struts e.g. level 1-5, 1-21
  * for wood: need to give up on estimating surface angles
* detect overlapping pigs/birds as segmentation slips
* pigs: snap to standard sizes?

## Other ##

* contact handler to destroy pigs
  * different pigs? helmeted, tiny
* contact handler to hit wood/glass/stone
* tap effects for blue and yellow birds
* heuristic strategies
  * aim for highest / closest / furthest pig first
  * aim for object with largest potential energy
  * planning: birds vs materials
    * estimate ''expected'' final score
* interface with game server
* --debug flag to show whole sequence
