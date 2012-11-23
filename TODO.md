
* use collinear trick on general polygons too
* backup
* split up static-surface blobs to better define polygons
  * if width > 200px split horiz.
  * get segment minima for upward angles only (otherwise grass interferes)
* find ground level
* pull all vertices within a distance to the ground level
* loop through objects from ground level up:
  * find other objects within x range and close to y top
  * align vertices down
  * wood/stone/glass rods:
    * expand?
    * snap to standard widths?
    * snap to standard lengths?
  * pigs: snap to standard sizes
* test more screenshots
* work out number and order of birds remaining
* find focus point
* use size of slingshot as scale factor for world
* contact handler to destroy pigs
  * different pigs? helmeted, tiny
* contact handler to destroy wood/glass/stone
* estimate take-off angle to hit a given point - within angle range
* tap effects for blue and yellow birds
* estimate take-off angle and tap-time to hit point(s) for blue and yellow
* heuristic strategies
  * aim for highest / closest / furthest pig first
  * aim for object with largest potential energy
  * planning: birds vs materials
    * estimate ''expected'' final score
* interface with game server
