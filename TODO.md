
* functional style: pass objs as arg, not global atom
* farm out shape detection to futures, for speed
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
* contact handler to destroy wood/glass/stone
* estimate take-off angle to hit a given point - within angle range
* set up GI_Engine / ActionRobot
  * command-line interface
* tap effects for blue and yellow birds
* heuristic strategies
  * aim for highest / closest / furthest pig first
  * aim for object with largest potential energy
  * planning: birds vs materials
    * estimate ''expected'' final score
* check for level-failed big pig
* check for level-completed screen
