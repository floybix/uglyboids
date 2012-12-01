
## Priorities ##

* raycast to check impact angles (esp. static barriers)?
* adjust shapes to stability
* impact effects
* estimate pigs killed

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

* scale birds to world scale
* fix up bad struts e.g. level 1-5, 1-21
* detect overlapping pigs/birds as segmentation slips
* static shapes are general polygons
  * use edge point arrays (top, right, bottom, left), follow around.
    * sample every n pixels, check collinearity.
* loop through objects from ground level up:
  * find other objects within x range and close to y top
  * align vertices down (thin on wide) or up (wide on thin)
  * wood/stone/glass rods:
    * snap to standard widths?
    * snap to standard lengths?
  * pigs: snap to standard sizes

## Other ##

* draw colors to indicate object types in world
* contact handler to destroy pigs
  * different pigs? helmeted, tiny
* contact handler to hit wood/glass/stone
* estimate take-off angle to hit a given point - within impact angle range
* tap effects for blue and yellow birds
* estimate take-off angle and tap-time to hit point(s) for blue and yellow
* heuristic strategies
  * aim for highest / closest / furthest pig first
  * aim for object with largest potential energy
  * planning: birds vs materials
    * estimate ''expected'' final score
* interface with game server
* robustify: use (try) on each shape detection, etc.
* can scale of world vary?
  * use size of slingshot as scale factor for world?
* need to destroy World when creating a new one to avoid overflow?
