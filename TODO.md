
## Priorities ##

* get my own naive agent working
  * raycast to check impact angles (esp. static barriers)
  * adjust shapes to stability
  * basic taps (just before impact)
* send for compatability testing

## Vision ##

* make sure we have correct y-coordinate interpretation!
  * from image, and in GIEngine, top left is 0,0
  * but in world (as in cartesian plane), increasing y goes "up" (i.e. gravity points negative)
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
