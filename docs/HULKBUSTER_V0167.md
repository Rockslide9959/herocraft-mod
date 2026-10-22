# Hulkbuster I "Veronica" overhaul (v0.16.7, user)

Four user requests plus three follow-up fixes for the Hulkbuster I (`mark_8`). The earlier v0.16.7 Hulkbuster work
(Titan-style hit soaking, wreck, climb-out, drop crate, Uplink tab) is in `IRONMAN_ERA2_FOLD.md` and the classes it names.

## 1. The Uplink is reusable; the suit lives in orbit (replaces the single-use Uplink)

The user's model: take the Uplink from the Suit Platform (the suit goes to orbit) -> call it any time, the Uplink is NOT
used up -> exit (fold, recall) and it goes back up to ORBIT as it is -> only putting the Uplink back into a Suit Platform
brings it home (racked, repaired there, the Uplink used up).

* **Where the orbiting suit is stored (decision): a server-side orbit store**, `ironman/crate/HulkbusterOrbit` (SavedData
  on the overworld, file `projecthero_hulkbuster_orbit`). One *slot* per Hulkbuster: the four real stacks (integrity,
  per-part damage on the chest, charge, every component), the integrity, `inOrbit`, the owner and a key generation.
  The Uplink is only a **key**: `custom_data` holds the slot id + generation (and a cached view for the tooltip,
  refreshed by `inventoryTick`). Every piece of the suit carries the slot id too, so whatever brings the suit back up
  knows its slot without needing the Uplink. Why not the item itself: a fold has to reach orbit when the Uplink is in a
  chest, the owner is offline, or the suit was recalled from somewhere else; the server store handles all of those.
* **Anti-dupe:** the stacks are only ever *moved* (slot -> crate / pod -> worn / Sentry -> crate -> slot), never copied;
  a slot either holds them or it doesn't. A copied Uplink copies a key, not a suit: the second key finds the suit gone
  (or the slot closed). A set of pieces arriving for a slot that is already full is never merged: it comes back loose and
  unlinked (logged).
* **Call** (`HulkbusterCrate.use`, from right-click or C): suit in orbit -> the drop crate on the ground / the pod in the
  air (as before); the slot goes "down". **Suit already down -> recall it to orbit:** standing in its open crate, the
  crate shuts and lifts; standing free (Sentry Mode, a wreck), a crate comes down open round it
  (`HulkbusterCrateEntity.pickUp`, the fold's drop), shuts its back and lifts it; in a crate / pod on its way: "in
  transit"; worn by someone: says who; pieces scattered: "gather all four and right-click one".
* **Back to orbit** (`HulkbusterCrate.toOrbit`): a fold or recall reaching the top of the lift, a crate cleared away
  (`kill`), a pod build cut short (pod removed / pilot died) and the loose-piece right-click all put the stacks back
  into their slot **unhealed**. Pieces with no slot (a suit from before keys, one put on by hand) open a new slot; an
  online owner gets its new Uplink. Nothing flies to a platform any more; Send Home (Sneak+C picker) refuses the
  Hulkbuster with a hint.
* **Platform:** the Uplink tab (`packUplink` -> `mintUplink`) sends the racked suit up and hands over its key (a suit that
  already had a slot goes back into it with the key re-cut). The Uplink back into a platform (right-click the pad /
  shift-click in the screen, `storeUplink`): only while the suit is **in orbit** (else "fold or recall it first") -> the
  stacks come off the slot onto the rack (as damaged as they were; the platform repairs them as usual), the slot is
  closed, the pieces lose their slot stamp, the Uplink is used up.
* **Lost Uplink (decision):** the suit stays in orbit. Any platform of the owner shows a **Re-link** tab (same spot as
  the Uplink tab, a per-player menu data slot) while they own a slot whose live key they don't carry: it cuts a new key
  and bumps the slot's generation, so the lost key goes dead (tooltip "Unlinked"; a platform recycles it). Only ever one
  live key. No crafting recipe.
* **Wreck (decision):** a wreck stays in the world as a Sentry (pod-repairable as before). The Uplink can recall it: it
  goes up still at 0 integrity (no healing in orbit) and comes back down as the rebooted-at-0 suit; the Replacement Pod
  or a platform repairs it.
* Older Uplinks: one still holding its four stacks (the pre-single-use rule) is bound on first sight: the stacks move up
  into a new slot. A never-used creative Uplink becomes a brand-new suit in orbit on first use. One emptied by the
  single-use rules stays spent.
* Tooltip (wrapped): "Calls the Hulkbuster from orbit. Put it back in a Suit Platform to bring the suit home." + the
  suit's state (in orbit with integrity %, down, unlinked) + the hint.

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
  finishes it where they went; a death (or the pod being removed) sends the pieces still aboard back up to orbit
  (v0.16.7 user rework; they used to drop). On the ground the drop crate is used as before.

### The inner frame: the pilot is never seen inside the half-built suit

"you dont really see the player while the suits building itself ... not just making the player invisible".
`client/ironman/HulkbusterBuildClient` (`frameTextures`, `innerFrame`) + `IronManHulkbusterRenderer.renderInnerFrame`.

* The first thing that forms round the pilot (pod ticks 14..24, `FRAME_START` / `FRAME_TICKS`, while the pod comes in
  and before the first piece leaves it) is the Hulkbuster's dark **inner frame**: the body model drawn again at full
  size with every part at rest (no flying offsets) in a procedural gunmetal texture made from the body texture's own
  texels -- vertical struts, rib lines, rivets where they cross, a little of the plate shading, bronze beds with **amber
  seams** that glow (an `eyes` pass, brightest the moment the frame locks). The struts climb up from the feet first,
  then the panels fill in; thin jets of white steam vent at the seams, a piston hiss as it starts and a hiss + clamp as
  it shuts.
* It is the *complement* of the plates: a frame texel disappears the moment the plate texel over it is on (per texel in
  the build-on, per part when the pieces arrive whole), so through every gap you see frame, never the player, and frame
  and plates never z-fight. The raised outer plates' frame clears as each part clamps on so the base build-on shows.
* The pilot is drawn tucked a little narrower once the frame is up (`PlayerRendererHulkbusterMixin.tuckPilot`, X x0.7,
  Z x0.66, Y x0.95, eased in over 6 ticks under the climbing struts): the Hulkbuster's torso is 6 px deep, a player's
  head 8, so without it the faceplate and elbows poked through the frame. They are not hidden or made invisible:
  before the frame is up they are drawn as normal, after it they are simply inside it.
* First person keeps the head + torso out of the frame too (the same `hideNext` as the body).

### The build-on plays only for the call over another suit

"the hulkbuster seems to rebuild itself everytime you enter the suit, thats not supposed to happen". The gantry-style
per-texel build-on (`HulkbusterBuildClient.VERONICA`) now plays only for the **pod build onto a player wearing another
Iron Man suit** (`HulkbusterPodEntity.buildsOnOverArmour`, decided once when the build starts). A pod build onto a bare
player keeps the inner frame and the pieces fly on whole (`PLAIN`). Stepping into the Sentry / crate or swapping on no
longer builds at all (the old `STEP_IN` schedule is gone): the climb-in, the back plate shutting and the suit closing
round you are the whole show.

## Fall landings (every Iron Man suit)

"make it so that if the player falls a large distance they still do the iron man landing pose if wearing an armour".
`ironman/IronManFallLanding` (replaces the Hulkbuster-only fall tracker in `HulkbusterLanding`).

* Any whole Iron Man suit (Era I, Era II, the Hulkbuster) not flying (nor elytra, creative flight, water, a ladder, a
  ride, the carried Thruster Pack) that falls **5+ blocks** (`FALL_MIN`, decision) lands with the landing pose.
* Scaled by the height like a flight landing by its speed: intensity `(h - 2.5) / 11.5`. Below 0.4 (~7 blocks) a
  **light touchdown** (the three-point pose at 0.3..0.75 depth, played 1.45x quicker, a low dust ring and a muffled
  thud, a faint shock ring for the bigger drops); 0.4+ the **full superhero landing** with the ground impact and the
  same area slam as flying into the ground (`IronManLandingSlam.trySlam`). The Hulkbuster plays its own heavy / light
  clips (`HulkbusterLanding.land`).
* Era I / II: the server sends `IronManAbilityFx.IM_LAND_BASE + intensity tenths` (130..140) to everyone;
  `IronManAbilityPose` hands it to `IronManFlightPose.onFallLanding`, which plays the same `applyLanding` pose with a
  depth / speed scale (`landScale`, `landRate`) and raises the hard-landing FX or the new light `fallTouchdown`.
* **No double landing:** a flight that touches down (`IronManLandingSlam.onLanded`) is recorded as THE landing
  (`noteFlightLanding`) and clears any fall in progress; one touchdown only lands once (10 ticks); the client ignores
  a fall landing within 10 ticks of its own flight landing.

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
`IronManFallLanding` FALL_PEAK / LAST (cleared in `ServerStateReset`). The orbit store is SavedData (per world).

## Tests

`gametest/IronManHulkbusterVeronicaGameTests` (16): the Uplink reusable and the suit back to orbit unhealed (crate
cleared, fold; per-part damage kept, no platform), the Uplink back in a platform racks it and is used up (refused while
the suit is down), no duplication (a copied key, a second set of pieces, Re-link kills the lost key), the Uplink
recalling a wreck to orbit as a wreck, the build-on only over another suit (and a step-in never builds), a build cut
short going back to orbit, broken parts, the pod's timing, the pod on a wreck, the inner suit across every way out, the
mid-air build (Uplink kept), a logout mid-build. `IronManBoostLandingPackGameTests`: a big fall lands in Mk III, Mk VIII
Space and the Hulkbuster (heavy at 9, light at 5.5, none at 3, none without a suit), and a flight landing is never
doubled by a fall landing. Updated: `IronManHulkbusterCrateGameTests` (Uplink stays, recall to orbit, fold to orbit),
`IronManHulkbusterFixesGameTests` (platform round trip), `IronManFoldPolishGameTests` (loose Hulkbuster pieces go to
orbit; no Uplink recipe), `IronManSentryV01515GameTests` (Send Home refuses the Hulkbuster).

Real client (temp harness, removed): the over-armour pod build with the frame closing and the plates building on, the
same build onto a bare player, a Sentry step-out and step-in (no rebuild), fall landings in a Mk III, the Hulkbuster
(heavy) and a Mk VIII Space (light).

## Not done / known limits

* The build's flying pieces start from the hatch using each part's rest pivot; the start point can be a little off
  for the arms (the mirrored-X convention), which is not visible at that speed.
* A player who pulls off only some Hulkbuster pieces by hand gets the inner suit back only once all four are off.
* The platform repair does not have its own plate animation; parts just scale back with the integrity.
* Other suits' builds (the Era I gantry, the Era II portable forms) draw the armour on the player model itself, so the
  "player inside a half-built suit" problem doesn't arise there the same way; only the Hulkbuster was changed.
* Sentry Mode (C in the suit) still leaves the Hulkbuster standing where you are: it is "down", not in orbit, until the
  Uplink recalls it or you step back in and fold.
* A recall of a free-standing Sentry needs open sky over it (the crate's rule); under a roof, walk it out first.
