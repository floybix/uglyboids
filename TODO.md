
* make sure we have consistent y-coordinate interpretation!
  * from image, top left is 0,0
  * in chrome, top left is also 0,0
  * but in world (as in cartesian plane), increasing y goes "up" (i.e. gravity points negative)
  * so use increasing y system for shapes as soon as we get out of image
* treat static surface differently from dynamic blocks
  * for dynamic blocks, everything not a circle or triangle is a rect
  * detect circles from edge pts
  * find closest edge pt
    * check is collinear and opposite is collinear
      * otherwise may be triangle
    * assume is rect, extend up to furthest perp points
  * detect triangles by checking collinearity of long edges (like struts)
* static shapes are general polygons
  * go back to joining vertices, not intersecting lines. sigh.
  * split up static-surface blobs to better define polygons (for non-convex)
    * if midpt not within points then split
  * include segment minima
* find ground level
  * pull all lower edge points within a distance to the ground level
* loop through objects from ground level up:
  * find other objects within x range and close to y top
  * align vertices down (thin on wide) or up (wide on thin)
  * wood/stone/glass rods:
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
