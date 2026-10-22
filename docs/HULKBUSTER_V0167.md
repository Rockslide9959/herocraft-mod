# Hulkbuster I "Veronica" overhaul (v0.16.7, user)

Four user requests plus three follow-up fixes for the Hulkbuster I (`mark_8`). The earlier v0.16.7 Hulkbuster work
(Titan-style hit soaking, wreck, climb-out, drop crate, Uplink tab) is in `IRONMAN_ERA2_FOLD.md` and the classes it names.

## 1. The Uplink is single use

"make it a one use item, it gets used up once you call in the armour and thats it".

* Calling the suit spends the Uplink: the crate call (`HulkbusterCrate.callTo` -> `consume`) and the mid-air build
  (`HulkbusterCrate.buildInAir`). Racking a loaded Uplink on a Suit Platform (`storeUplink`) uses it up too.
* **Where the suit goes afterwards (decision):** home to its Suit Platform. The platform's Uplink tab
  (`IronManSuitPlatformBlockEntity.packUplink` -> `HulkbusterCrate.mintUplink`) programs a new Uplink and stamps every
  piece with that platform (`IronManPlatformReturn.tagHome`). When a crate reaches the top of its lift (a fold, a recall,
  an empty crate is never involved), `HulkbusterCrate.toOrbit` now flies the pieces home:
  1. the platform on the pieces' home stamp; else the owner's nearest platform with room (`StarkPlatformRegistry.nearestDockFor`);
  2. racked now, loading the chunk if needed (`IronManPlatformReturn.depositNow`), or queued (`StarkSuitReturnQueue`) if
     the dimension isn't loaded;
  3. with no platform at all, the four pieces come back to the owner loose (right-clicking one with all four packs a
     new Uplink, the old loose-piece rule); with nobody online they drop where the crate stood. Never lost.
  So the loop is: rack -> Uplink tab -> call (Uplink gone) -> fold -> racked again -> Uplink tab.
* The crafting recipe was removed (a blank Uplink has no use: the platform programs them). A never-used creative Uplink
  still stands for a fresh suit. An old emptied ("deployed") Uplink from before this change is inert: right-click says
  it's spent.
* Tooltip: "Single use: used up when you call the suit" plus the wrapped hint.

## 2. Per-part damage

`ironman/HulkbusterParts`. Six parts: head, torso, left arm, right arm, left leg, right leg. The damage (0 whole .. 1
broken) lives on the CHESTPLATE stack (`ModDataComponents.HULKBUSTER_PARTS`, synced), so it travels with the suit
everywhere (worn, Sentry, crate, platform, Uplink).

* **Routing:** `HulkbusterShield.take` (the suit soaks every hit) calls `HulkbusterParts.onHit`. The part is picked by
  the hit's height on the body (a projectile's position, a melee attacker's eyes, the source position) and the side it
  came from; falls and fire go to the legs; no position = weighted random.
* **Integrity still drives the suit.** Each part owns a share (head 12%, torso 28%, arms / legs 15%) and breaks after
  soaking 70% of it (`BREAK_SHARE`). `effective()` never shows more damage than the integrity actually missing, so a
  platform repair (integrity only) scales the parts back and a full suit is always whole. 0 integrity still wrecks it.
* **Broken arm:** that fist / palm is dead. The Punch Combo's blows with it are dead swings (sparks), the Rocket Fist
  flies from the other hand, the Palm Cannon fires one palm. Both arms gone: every fist move offline. The two-fisted
  Pile Driver needs both arms.
* **Broken leg:** -25% walking speed per leg (attribute modifier), boot-jet flight x0.7 (x0.45 both; the suit had no
  sprint flight already), no Earthquake Stomp / Pile Driver (a leap), the `limp_l` / `limp_r` clips.
* **Head:** no Missile Swarm (no locks), the server targeting and the target-scanner HUD / world reticle are off, the
  Mega Beam's aim wobbles (`beamWobble`).
* **Torso:** no Mega Beam, the reactor leaks 4 energy/s.
* **Visible:** each part is drawn at its own battle-damage tier (`client/ironman/HulkbusterPartRender`), the same nine
  procedural tiers every Iron Man suit uses (`IronManBattleDamage.hulkbusterBody` builds them from the Hulkbuster body
  texture). A broken part is tier 9: missing plates showing the inner frame with glowing torn edges. The body's base
  pass draws only the clean parts, then the part layer re-renders once per tier in use. Broken parts smoke and spark
  (`HulkbusterVeronicaClient`). Applies to the worn body and the Sentry.
* **HUD:** in the status block the generic suit wireframe is replaced by a Hulkbuster one: six parts coloured by their
  own health, a broken part dark red with a blinking cross. Offline moves' key boxes are greyed with a red cross.
* **First person:** a broken arm's gauntlet uses its damaged texture.

## 3. Replacement Pod

`ironman/entity/HulkbusterPodEntity` (MODE_REPAIR), drawn by `client/ironman/HulkbusterPodRenderer` with the Mark VII
delivery pod's model and clips at 1.6x.

* Shift+V (unchanged key, 180 s cooldown, works disabled / browned out): the pod streaks in over **3 s**
  (`ARRIVE_TICKS` = 60) and hovers **10 blocks above** where it was called (`HOVER_UP`), opening.
* Then over **8 s** (`REPAIR_TICKS` = 160) 6..12 replacement plates leave it one at a time, worst part first (round
  robin), each arcing 0.7 s to its part, lying flush while it is welded (sparks, flashes, a hiss) and melting into the
  armour. The part's damage falls over the weld (its texture clears tier by tier) and the integrity climbs in step; at
  the end the suit is full and whole. Then it shuts and climbs away.
* **Pilot movement (decision):** free. The plates home on the part wherever the suit goes, so the pilot can keep
  fighting; holding still is not forced.
* **Sentry / wreck:** sneak + right-click your wrecked Hulkbuster to call its pod (`IronManSentryEntity.callPod`, the
  same cooldown); repaired, the wreck reboots at once.
* If the suit goes away mid-repair (folded, stepped out of) the pod leaves with what it already did.

## 4. Worn over another suit (Age of Ultron)

`ironman/HulkbusterInnerSuit`, attachment `ModAttachments.HULKBUSTER_INNER_SUIT` (persistent, kept through death).

* The Hulkbuster may be called / stepped into wearing any Iron Man suit or any armour (`canWrap`, the crate walk-in,
  the Uplink no longer refuse a worn suit; only a worn Hulkbuster piece blocks). When it goes on (`IronManSentryEntity.equipOnto`,
  the build's `finishBuild`) the worn armour is stashed whole: every component, an Iron Man suit's live integrity and
  energy stamped on, its running systems and flight stopped. Its kit, flight and drain don't run inside.
* **One rule restores it** (`HulkbusterInnerSuit.tick`, every player tick): as soon as no Hulkbuster piece is worn, by
  any path (fold, Sentry climb-out, wreck eject, a platform taking it, a revoke, pieces pulled off by hand), the inner
  suit goes back on through `receivePart` (whole, integrity and energy loaded, active suit set). So you climb out in
  your own suit. A slot already taken gets its stack in the pack instead; without Tony Stark's power the Iron Man
  pieces go to the pack. Nothing is ever deleted. It waits while a build is running.
* **Death:** keepInventory keeps the stash (copied to the respawned player, who still wears the Hulkbuster); otherwise
  the stash drops where they died, like worn armour.
* **Logout / login / server stop / dimension change:** the attachment is persistent and the Hulkbuster stays on, so
  nothing changes until it comes off.
* **Suit Platform / Gantry:** the Gantry already refuses a worn Hulkbuster. The inner suit is never in an armour slot,
  so nothing can strip it from inside; whatever takes the Hulkbuster off triggers the restore.

### Mid-air build

`HulkbusterPodEntity` (MODE_BUILD). The Uplink used while flying (or with a 2.5-block drop under you,
`HulkbusterUplinkItem.airborne`) calls it instead of the crate.

* The caller rides the pod, held standing at the spot they called from (`positionRider`, a `HumanoidModelMixin` hook
  keeps them out of the sitting pose). The pod comes in above and behind them (2.8 up, 2.6 back) in 1.5 s.
* The legs, torso, arms and helmet leave it 0.6 s apart and each flies 0.8 s onto the body, eased with an arc and a
  decaying tumble, then clamps on with a small settle (sparks, clang). Every viewer sees the same frames: the client
  draws the Hulkbuster body round the player with only the arrived / flying pieces (bone offsets in
  `IronManHulkbusterModel.setCustomAnimations`, visibility in the renderer). In first person the head and torso are
  left out (they would fill the view).
* At `BUILD_DONE` (5.6 s) the real pieces go on (the worn suit is stashed), the pilot is let go flying and the pod
  leaves. Sneaking out early snaps the rest on; a logout finishes it at once (disconnect hook); a portal mid-build
  finishes it where they went; a death drops the pieces. On the ground the drop crate is used as before.

## Follow-up fixes

* **Stance:** `idle`, `walk` and `run` in `hulkbuster_body.animation.json` re-keyed (script
  `scratchpad/hb_stance_v0167.js`): upright, arms hanging close (2.5 degrees out, a slight elbow bend), no forward lean;
  a player-like walk with a natural opposite arm swing and a heavy weight shift (the body drops on each heel strike and
  rolls onto the standing leg); the run bends the elbows like a runner. The other clips blend from it through the
  controller's 4-tick transition. New clips `limp_l` / `limp_r`.
* **First-person fists:** drawn 1.4x (`IronManHulkbusterFirstPerson.FIST_SCALE`), pushed out into the corners.
* **Crate walls on uneven ground:** `HulkbusterCrateEntity.wallLimit` sweeps each wall's arc against the terrain and
  stops it where it first touches a block (bisected), or lets it tip on down a slope to 120 degrees; the renderer
  multiplies the fall by it. The landing room check for the crate's own box is unchanged.

## Static state

`HulkbusterPodEntity.BUILDING` (cleared in `ServerStateReset`). The inner suit is an attachment, not a static map.

## Tests

`gametest/IronManHulkbusterVeronicaGameTests` (11): the Uplink used up and the suit flying home to its platform, broken
parts taking moves offline (and the slow / leak / full-repair rules), the pod's 3 s arrival and ~8 s repair, the pod on
a wrecked Sentry, the inner suit across the Sentry step-out, the wreck, death with and without keepInventory, a save /
load, a revoke and a taken slot, the mid-air build, a logout mid-build. Updated: `IronManHulkbusterCrateGameTests`,
`IronManHulkbusterFixesGameTests`, `IronManComicV0161GameTests` (the pod now restores the whole suit).

## Not done / known limits

* The build's flying pieces start from the hatch using each part's rest pivot; the start point can be a little off
  for the arms (the mirrored-X convention), which is not visible at that speed.
* A player who pulls off only some Hulkbuster pieces by hand gets the inner suit back only once all four are off.
* The platform repair does not have its own plate animation; parts just scale back with the integrity.
