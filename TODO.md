
## Priorities ##

* cache initial scene for each level
  * if vision or sim fails, try up to 1 more time

* for qualification: meta-strategy:
* first try to complete all levels
  * up to half the time
* then go back to other levels where
  * we are being beaten by 10k
  * completed with 3+ shots
  * have only used a simple approach so far
* each level
  * first try to simulate shots:
    * each pig * (direct, lob)
    * object highest off the ground * (direct, lob)
    * object with max potential energy: mass * height
    * direct shot just under highest pig
  * rank by (1) kills (2) objects (choose best)
  * if simulation fails, choose randomly
  * taps: randomise time 0.85-1.05
  * check already done shot as first shot on level (choose next)
    * TODO - same first shot ok if later shots differ.
  * record shot, and choice type
  * scan, and record actual kills
* second shot:
  * if remaining pigs same number and about same pos as expected
    * use simulation to choose
      * includes remaining resistance
  * otherwise:record mismatch and use screenshot scene
    * but if shape detection fails, do use simulation?
* im-feeling-lucky - once
  * unrealistic? assumes simulation very good (e.g. not for taps)
  * greedy select best shot, then recur to find full sequence
    * need to rebuild world for multiple sims after first shot
  * submit list of shots

* report history of choices on each level

* impact effects
* estimate pigs killed

* ROBUSTIFY - various levels of detail
  * use (try) on each shape detection, etc.
  * need to destroy World when creating a new one to avoid overflow?
  * do not try to create empty shapes (JBox2D IndexOutOfBoundsException)

## Vision ##

* fix up bad struts e.g. level 1-5, 1-21
  * for wood: need to give up on estimating surface angles
* detect overlapping pigs/birds as segmentation slips
* detect helmeted pig as small stone above

## Other ##

* contact handler to destroy pigs
  * different pigs? helmeted, tiny
* contact handler to hit wood/glass/stone
* tap effects for blue and yellow birds
* interface with game server
* --explain flag to show whole sequence
