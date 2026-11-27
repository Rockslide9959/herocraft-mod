# Black Panther reference

Hero-Tier Primary power, **Street tier** (melee 7-10, abilities 8-10, ultimate 15-20, `MAX_HIT` 20). Package
`com.projecthero.mod.blackpanther`, client `client/blackpanther`. Numbers live in `BlackPantherConfig`; state in the
`projecthero:black_panther_state` attachment (`BlackPantherState`, synced, copy-on-death). Tests: `BlackPantherGameTests`,
`BlackPantherV0177GameTests`, `BlackPantherSuitedV0177GameTests` (the v0.17.7 suited kit and the no-decay Kinetic bar),
`BlackPantherPlaneV0177GameTests` (the Ancestral Plane).

## Getting it

Heart-Shaped Herbs grow in jungles. 3 herbs + a bowl = Heart-Shaped Herb Soup (recipe unchanged). v0.17.7: drinking it gives
**Bast's Blessing** for 60 s and nothing else (no power, no 10 s vision); falling asleep in a bed while blessed opens the
**Ancestral Plane** (below), whose test grants the power on waking. It replaces the Primary power. The suit is crafted separately
(the necklace below). A legacy save caught mid-vision (`visionUntil` set by 0.17.3-0.17.6) still gets the power when it runs out.

## The Ancestral Plane (v0.17.7)

The user: "to get this power you need to craft the heart shaped herb soup / drinking this gives you Bast's Blessing for 1 minute /
then player needs to sleep, upon falling asleep player is transported to the ancestral plain, it looks like a savana ... their is
a warm orange sunset, player will spawn a distance away from their ancenstors, they will need to walk up to their ancestors and they
will then take you to a panther shrine, in there player must right click the panther shrine and then the player will be tested,
player will have to beat a minigame and do a 100 block parkour course and once they complete that they will get the powers ...
incorporate cutscenes, this must all take place in the players mind palace thing similar to hulk and doctor fate".

Package `blackpanther.plane`, client `client/blackpanther/plane`. Built on the **Shazam journey framework**
(`shazam.journey`, see FATE_SHAZAM_DESIGN "how players get Shazam"): a private slot per dreamer in a datapack dimension, a saved
return point, server-authoritative stages, the client shooting every cutscene from shared beats, the shared `CutsceneHands`, the
journey camera / GUI / "Loading terrain" mixins (they now also ask `AncestralPlaneClient`), and the same guards.

- **Bast's Blessing** (`BastBlessing`, `projecthero:bast_blessing`): a beneficial MobEffect, 1200 ticks, ambient (soft
  particles), colour #9A5BD6, icon `textures/mob_effect/bast_blessing.png` (gold-rimmed black panther head on a violet glow). No
  effect of its own. `BlackPanther.canDrink` refuses a second soup while blessed (message) or mid-dream.
- **Sleep**: `AncestralPlane.tick` takes any player asleep (`getSleepTimer() >= 80`, vanilla's screen is nearly black at 100,
  before a night skip at 100) with the blessing into the plane: the blessing is spent, the bed position and where they lay are saved
  (`AncestralPlane.RETURN`, `PlanePayloads.Return` with an optional bed). **Day sleeping:** `EntitySleepEvents.ALLOW_SLEEP_TIME`
  returns SUCCESS for a blessed player (so a bed works at noon), and also for a player being woken back in bed (so the day doesn't
  throw them out of it at once). Every other bed rule (monsters nearby, obstructed, too far, the Nether) is vanilla's.
- **The plane** (`PlaneRealm`): `projecthero:ancestral_plane`, a flat datapack dimension (bedrock, stone, dirt, grass; skylight,
  fixed noon light, no weather, no mobs; biome `projecthero:ancestral_plane` with golden grass #C8A048, olive-gold foliage,
  orange fog/sky, a few drifting ash motes). One slot per dreamer, 1000 blocks apart along Z; dressed once in slices (grass tufts and
  tall grass in drifts over x -56..268, z +-84; lilacs and alliums round the ancestors; 46 flat-topped acacias round the horizon;
  the great acacia with a 2x2 trunk and four crowns; the shrine) and reused (bedrock marker 12 under the spawn). Fallback for the
  GameTest server: the same in the overworld sky at x 1,250,000, y 150 on its own grass floor.
- **The sky** (`AncestralSky`, effects `projecthero:ancestral_plane`): a gradient dome (violet zenith, magenta, rose, a blazing
  orange-gold horizon), a golden bloom round a huge sun 4 degrees up in the east (+X: behind the ancestors, the shrine and the end of
  the path, so they stand dark against it), thin pink-gold cloud streaks near the horizon, four slow purple aurora curtains overhead
  (additive, rippling), faint stars at the dark top away from the sun. Fog = the horizon orange, pulled in to 22-98% of the render
  distance (`FogRendererMixin`) so the grass melts into the glow; no clouds or weather. A warm film tint over the HUD in the plane.
  Ambience: wind gusts (elytra, low and quiet) every 6-15 s, a distant big cat (polar bear warning / cat purreow, pitched down)
  every 25-60 s.
- **Layout** (`PlaneGeometry`, offsets from the slot origin, +X toward the sun): spawn x 0; the five ancestors x 70-74 under the
  great acacia (80, 13), the eldest at (70.5, 0.5) -- 70 blocks; the shrine centred x 110; the spirit path from x 128 to 241.

### The story (`AncestralPlane`, cutscenes and beats in `PlaneScene`)

1. **ARRIVAL** (170 t, cutscene): black, then the savanna fades in from high above looking into the sunset, the camera glides down
   behind the dreamer and into their eyes; "The Ancestral Plane" title. Resonant chime, wind, a far purr.
2. **WANDER** (free): a trail of gold and violet motes (sent only to the dreamer) drifts from a few blocks ahead toward the eldest;
   the ancestors turn their heads to watch; HUD hint. Within 9 blocks of the eldest (or right-clicking any ancestor) ->
3. **GREETING** (330 t): placed 6 blocks before her; she steps forward. Shots: behind the dreamer with the group and tree against
   the sun, over the dreamer's shoulder, over hers, a rising wide. Lines (`subtitle.projecthero.ancestral_plane.greet1-4`, original).
4. **PROCESSION** (250 t): they walk (scripted, a walking pace with soft start/stop, staggered) 24 blocks to the shrine's west
   entrance and stand either side of it; the camera walks backward ahead of them, then looks down from over the shrine. Two lines.
   (Waits for the shrine on a brand-new slot.)
5. **SHRINE** (free): right-click the statue, its pedestal or the altar (`PlaneGeometry.isShrineHeart`; every other block use is
   refused in the plane). A violet shimmer at the altar.
6. **TRIAL_INTRO** (210 t): the statue's obsidian eyes turn to crying obsidian (beacon, a high dragon growl, violet burst, camera
   shake), Bast speaks three lines from a low hero shot, a close-up of the face, over the statue's shoulder and a circling wide as
   shadow wisps gather.
7. **TRIAL** -- the **Trial of the Shades** (`ShadeTrial`, pure rules): see below.
8. **PATH** (170 t): Bast's line; the spirit path forms platform by platform (amethyst chime rising in pitch, violet bursts) while the
   camera flies along it, then returns to the start.
9. **PARKOUR**: the 113-block spirit path (`ParkourCourse`), see below.
10. **FINALE** (320 t): on the finish platform facing the sun, the ancestors either side; **Bast's spirit** (a panther shade at 9x, full-bright,
    gold-violet starlit hide, burning gold eyes) rises hovering beyond the end of the path; her two lines, the eldest's farewell, a
    gold flash into black (totem, beacon) ->
11. **Waking**: back at the saved spot, lying in the bed again (`startSleeping`) for 2 s then up (`stopSleepInBed`); the dark lifts
    (WAKE scene); `BlackPanther.grant` exactly as before (`HeroTiers.claimPrimary`, the title / subtitle and chat lines). No bed
    (the test command, or the bed broken): just back where they were.

**Skipping:** each cutscene watched to the end sets a bit in `AncestralPlane.SEEN` (persistent, copy-on-death); a seen scene shows
"[Space] Skip" and Space sends `PlanePayloads.Skip` -> the stage jumps to its end (the next stage's start is idempotent: ancestors
placed, eyes lit, path built). The first viewing can't be skipped.

### The Trial of the Shades

Ghostly panther shades (`PantherShadeEntity`: the vanilla ocelot model at 1.6x, a translucent near-black hide with violet rim light,
glowing violet eyes; never a custom body) on the shrine floor. Three rounds: **4 shades one at a time, 6, then 8 two at a time**
(the second from across the floor). Each: appears on the rim 8.5 blocks out (never on the statue's side; it grows in, a hiss), stalks
in to 4 blocks (0.13-0.16 b/t, circling a little), **crouches with its eyes flaring for 1.2 / 1.0 / 0.9 s** (the tell: a hiss and a
heartbeat), then pounces (10 t arc) at where the player stood + 0.8. One hit from anything strikes it down (it dissolves in shadow
and violet, the kill counts). A pounce landing within 1.6 blocks is a **wound** (no damage: a hurt sound, a red vignette, a shove, a
shake; the shade springs back to the rim and stalks again); a sidestep is a miss and it stalks again. **3 wounds lose the round**: the
shades dissolve, a 3 s breather, the round starts over (wounds reset each round, won rounds stay won). Winning round 3 -> PATH. The
player is kept on the floor (back to the arena past 12 blocks from the centre). Beatable in about a minute.

### The spirit path

26 platforms from x 0 to 113 (offsets from x 128, 3 above the grass), rising 12: stone, acacia planks, amethyst, 1x1 panther totems
(chiseled blackstone tops with a gold-banded hanging totem), one 6-block stripped-acacia log beam. Every jump is 1-2 blocks edge to
edge with at most a 1-block rise (gametest-checked). **Checkpoints** (5x5 polished blackstone bricks, gold centre, purple candles, a
hanging amethyst crystal) at x 0, 20, 43, 65, 86; the **finish** (7x7, gold centre, a gold and crying-obsidian panther head facing the
sun) at x 107. Standing on a later checkpoint saves it (chime, gold burst, "Checkpoint reached"); falling 4 blocks below it, or onto
the grass, puts you back on it (no fall damage; "The plane catches you"). HUD: checkpoint n/5 and a Hairline progress bar with
checkpoint ticks. Built only when the trial is won (`PlaneRealm.buildPath`), cleared at the next visit (`resetForVisit`, which also
puts the statue's eyes back to obsidian).

### The shrine and the ancestors

- **Shrine** (built in code): a round floor of polished blackstone bricks (top 2 above the grass, half-slab steps so it walks up),
  cracked bricks here and there, an amethyst ring at radius 6, a gold claw sigil of three slashes at the centre; eight pillars
  (radius 11, none on the east-west axis) with brick bases, a chiseled band, gilded capitals, gold tops and lit purple candles,
  amethyst clusters at their feet; a crescent crown of bricks rimmed in gilded blackstone over them, open behind the statue; two
  braziers (campfires on walls) at the west entrance; the altar (chiseled blackstone, purple candles, amethyst clusters); a 13x11
  pedestal trimmed in gilded blackstone; the **seated black panther statue** (`PlaneRealm.statue`, voxels from ellipsoids:
  haunches, chest, neck, head with muzzle and ears, straight front legs, paws, tucked hind feet, the tail curled round to the front;
  polished blackstone, smooth black concrete on top, blackstone at the base, a gold necklace and pendant on the chest), built at 1.4x its base shape (about 14 tall),
  facing west over the altar.
- **Ancestors** (`AncestorEntity`, player model, never saved, can't be hurt or pushed, moved only by the story; the walk animates
  from the movement): five robed royals in skins from `scratchpad/v0177/gen_ancestor_skins.js` (original, no likeness): the eldest
  in white and gold with a headwrap and violet sash, a king in purple with a gold crown band and grey beard, a warrior queen in violet
  with white bands and a circlet, a scholar in plum with braids, a guardian in indigo with bronze trim and a purple wrap. Gold and
  violet motes drift round them. Preview `docs/screenshots/v0177/bp_ancestral/ancestor_skins_preview.png`.

### Safety

Nothing in a dream (or anywhere in the plane dimension) takes damage (`ALLOW_DAMAGE`, /kill still works), builds, breaks or uses
items; only the shrine's heart answers a right-click. Straying out of the box (x -60..300, |z| <= 110) draws you back to the current
stage's spot. **Hold Sneak 5 s** in a free stage (HUD bar after 1 s) = wake early, no power. Logout: the dream ends (ancestors, shades,
spirit discarded), the return point stays and `check` (every 10 t, any player with no dream) sends them back to bed next login; a player
found in the plane with nothing saved goes to world spawn. Death ends it and clears the return point on respawn. Taken out of the plane
another way: the dream ends quietly. Server stop: `ServerStateReset` -> `BlackPantherMod.clearSessionState` ->
`AncestralPlane.clearSessionState` (dreams and pending wake-ups are player UUIDs, builds are dimension keys; no worlds or entities held).
Client: every scene times out by itself, movement keys are held during scenes, hotbar/crosshair/chat/effects hidden, first-person hands
hidden (`CutsceneHands`), "Loading terrain" drawn black, everything clears on disconnect.

### Test commands (op)

`/projecthero power tune black_panther blessing` (Bast's Blessing 60 s; then sleep), `plane` (fall into it now from where you stand,
no bed), `shrine`, `trial`, `path`, `parkour`, `finale` (jump to that stage, starting a dream if needed), `leave` (wake, no power).
Refused if you already have the power.

**Tests:** `BlackPantherPlaneV0177GameTests` (the soup's blessing, day sleeping, a real bed -> the plane -> hold Sneak -> back in bed
with no power, leaving early, logout + check, the whole dream stage by stage -> the power, the finale granting once, the trial's rules,
shades striking, the course's rules and fairness, a fall reset on the live path). `BlackPantherGameTests.soupOpensTheAncestralPlane...`
updated for the blessing.

## The suit: the Black Panther Necklace (v0.17.7)

The user, first: "make the suit craftable instead please like the flash suit and you need to equip it, when the suit is worn you
get the new moveset" (built as four armour pieces), then the same day: "Make the suit just a necklace, player crafts the necklace,
equips it and presses H to make it transform into the full suit" and "Make the necklace model look like the necklace on the suit
please, don't make the player have the necklace model on them all the time, only when theyre wearing the necklace and suit".

- **Item:** `BlackPantherItems.NECKLACE` (`projecthero:black_panther_necklace`, `BlackPantherNecklaceItem`): stack of 1, EPIC,
  fire resistant, no durability. `Equipable` with slot **CHEST**, exactly like the Flash Ring holding the Flash Suit
  (`FlashRingItem`): right-click it (only the Black Panther may, others get a refusal), shift-click it or drag it into the
  chestplate slot. Death / keepInventory / graves treat it like armour. **Black Panthers only** (v0.17.7, user: "black panther necklace can only be worn by players with black panther ability"): `LivingEntitySupermanSuitMixin` returns MAINHAND as its equipment slot for anyone without the power (armour slot, shift-click, hotbar swap, creative screen refuse it, both sides), `use` refuses right-click, `BlackPantherNecklaceItem.DISPENSE` fits it only onto a Panther with the chest slot free (else shoots it out), and `BlackPanther.ejectNecklace` (every tick, every player, and from `revoke`) pops a worn one off into the inventory (dropped if full) with the refusal message. A chestplate cannot be worn with it (the suit gives its own
  armour).
- **Recipe** (shaped, `recipe/black_panther_necklace.json`): `VAV` / `VWV` / `VHV`. V = Vibranium Ingot (6, the old set's cost),
  A = Amethyst Shard, W = Black Wool, H = Heart-Shaped Herb. JEI shows it plus an info page (`projecthero.jei.black_panther_necklace`
  / `2`).
- **H** (`BlackPantherSuitPayload` -> `BlackPanther.toggleSuit`, server re-validates, `PowerSuppression` respected): suited -> the
  suit collapses back into the necklace (`suitDown`, `SUIT_DOWN_TICKS` 30); not suited + necklace worn -> `suitUp` (the nanotech
  texel build out of the necklace, `SUIT_UP_TICKS` 36; see "Nanotech build" below); a necklace only in the inventory is put on first (swapped with whatever was in the chest slot, the Flash
  Ring's `equipFromInventory`); no necklace -> "Wear the Black Panther Necklace to suit up". One change at a time
  (`transitioning`). Client: `BlackPantherClient.ownsH` = has the power AND (suited OR necklace worn OR necklace in inventory),
  checked in `ProjectHeroModClient.handlePowerSelect` right after the Flash Ring and listed in `primaryOwnsPlainH`. Plain H only;
  Shift+H always opens the wheel; with no necklace in play H falls through to the power selector as before.
- **Unequip:** `BlackPanther.syncSuit` (every tick, join, respawn) runs `suitDown` when suited without the necklace worn (or as a
  spectator). Nothing ever suits him up on its own.
- **Stats while suited:** `reconcile` adds `black_panther_armor` +20 armour and `black_panther_toughness` +8 toughness
  (`SUIT_ARMOR` / `SUIT_TOUGHNESS`, full diamond; user: "make the black panther armour diamond level armour", was 12 / 4) and
  Regeneration II; both modifiers are removed when the suit goes.
- **Necklace model** (`BlackPantherRender.renderNecklace`, drawn by `BlackPantherLayer` while `BlackPanther.showsNecklace` =
  necklace worn in the chest slot AND not spectator -- suited or not, all through the build / retract, drawn before the layer's
  has-power check so any wearer shows it; user: "make the black panther necklace model visible when not transformed, should be
  visible when the necklace is equipped"). On the bare skin it clears the vanilla jacket (2.25 front / back, 4.25 sides) with its
  inside faces at 2.36 / 4.36; its side runs sit inside the arm cubes (inner faces at x 4, wide and slim; a slim sleeve's top is at
  y 0.25, so a sliver of the top tier shows above a slim shoulder), and the chest slot never holds a chestplate with it. The gold necklace painted on the comics skin, in 3D (rebuilt for the comics skin, user:
  "Make the necklace model look like the necklace on the suit please"). On `black_panther.png` body row 20 is a gold band all the way
  round (chest texels 20 / 22-25 / 27 gold, near-black gaps at 21 and 26; sides solid; back mirrored), with claw-teeth at 21, 23, 24, 26
  on row 21 and a centre point at 23-24 on row 22; the jacket layer raises gold over rows 0-2 of the chest; the arms carry no gold. The
  model, all in the body's space: a band at y 0.08-1.18 of gold links (bright top bevel, mid face, darker lower lip, each tier a step
  further in), links at x -4.68..-3.04 / 3.04..4.68 (wrapping round the corners to a solid side run) and a bolder centre plate at
  -1.96..1.96, dark cord set back in the gaps at x = +-2.5, chest and back alike; on the chest four teeth (x +-2.5 hanging from the gap
  cords, x +-1.2 under the plate) and the long centre fang at 0 down to y 3.4, each root / middle / bright tip curling forward. Inside
  faces at 2.36 (chest / back) and 4.36 (sides), outside up to 2.88: never coplanar with the skin, the jacket (2.29) or the sleeves;
  clear of the high collar (its yoke is at y -0.73, its wall 6.35 px round the neck and open at the front, its back skirt 3.7 px+ behind).
  Texture `necklace.png` (64x64 flat gold swatches: #E7C76E, #D19C4C, #C18039, #11101C gap, #EACC71 tips, #DAAD59 plate) and the gold
  item icon (band, plate, four teeth and the fang, no gem) come from `scratchpad/bp_v0177_necklace_textures.js`. With Kinetic stored the
  gold takes the trims' violet glow (`necklace_glow.png`, same swatches in violet, alpha = the Kinetic bar). The skin is unchanged (its painted band stays under the model).
- **Migration** (`BlackPanther.migrate`, flag `v177`): a Black Panther who had the suit before v0.17.7 (habit bound in
  0.17.4-0.17.6, or any 0.17.3 power) gets ONE necklace: worn (and still suited) if he was suited and the chest slot is free,
  otherwise into the inventory (dropped if full). Old `habit`, `necklaceOff`, `clawsOut` flags are cleared.
  `BlackPanther.convertLegacyItems` (run by the legacy items' own `inventoryTick`, on join, and when a habit is used) turns the
  four armour pieces of the first v0.17.7 build into necklaces, one per full set of four (at least one for any pieces) -- a worn
  legacy chestplate becomes a worn necklace, so a player suited in the pieces stays suited -- and each Panther Habit into one
  necklace. The legacy items (`black_panther_helmet/_chestplate/_leggings/_boots`, `panther_habit`) stay registered so old stacks
  load (`PantherLegacySuitItem`, plain items, necklace icon), have no recipe, are not in the creative tab and are hidden from JEI.
- Removed: the four armour pieces as armour (material, armour-layer textures, item icons, recipes, JEI page), the always-on
  necklace model. The hold-H necklace toggle and the N claw toggle stay removed.

## Nanotech build (v0.17.7)

The user: "when transforming into the suit remove the animation for it, the suit must just build itself onto the player pixel by pixel
spreading out from the necklace, add a nanotech sound effect for the building please, make the cape build itself pixel by pixel
aswell please like how it does for thor".

- **No body clip**: `suitUp` / `suitDown` set `animId = ANIM_NONE` (cutting any running clip); the `SUIT_UP` / `SUIT_DOWN` clips and
  their first-person clips are deleted from `BlackPantherPose` (ids 1 / 2 retired). He keeps his pose and full movement; only the
  moves wait (`changing` -> "The suit is still forming") and the kit / HUD switch at the end as before.
- **Order** (`BlackPantherRender.order`): each texel's distance over the body surface from the necklace's centre plate
  (`NECKLACE_SEED` = 0, 0.8, -2 px, the Blue Beetle geodesic: texel to texel round the body, up the neck, over the shoulders, down the
  arms and legs), 90% distance + 10% per-texel noise (`ORDER_NOISE`) so the front is ragged. No separate head pass. Both skin layers
  (the base frames + `OverlayStrips` with the same order), and the first-person arm (same frames: it fills from the shoulder down). The
  64 pre-built frames keep the violet frontier glow. Retract = the same frames backwards (collapses into the necklace).
- **Cape and collar** (`BlackPantherClothReveal`, the `ThorSuitReveal#capeTexture` approach): 24 pre-built frames of each cloth
  texture with the unformed texels cleared (both draw cut-out), newest texels violet. The cloth stands at full length / height the
  whole time. Cape: collar to hem (v) + scatter; collar: from its open front ends round to the back of the neck (u) and yoke to rim.
  Timing from the body build (`BlackPantherRender.collarProgress` / `capeProgress`): the collar from the first top-of-torso texel's
  order to the last + 0.25, the cape from the top of the back to 0.96 of the build. Reverse on retract (cape hem-first, then the collar).
- **Sound** (`BlackPantherSounds`, events `black_panther_nanotech_*` in `sounds.json`, vanilla recordings pitched up, no new files):
  start = beacon activate (pitch 1.9: a short rising whir); skitter = sculk spread 1-5 (pitch ~1.75: tiny plates crawling) every 2 ticks,
  rising 0.9 -> 1.15 over the build; shimmer = amethyst resonate 1-4 / shimmer (pitch ~1.8) every 6 ticks; seal = beacon power 1-3
  (pitch 1.9) + a light netherite clasp 2 ticks before the end. Retract: beacon deactivate (pitch 1.7), skitter every 4 ticks falling in
  pitch at half volume, shimmer every 8, an amethyst click at the end. Played into the world at the necklace (everyone near hears);
  step 0 from `suitUp` / `suitDown`, the rest from `BlackPanther.tick` off the synced suit clock. Subtitles "Nanotech suit builds /
  seals / retracts".

## Suit look: comics skin, cape and high collar (v0.17.7)

The user: "make this the new black panther suit: https://www.planetminecraft.com/skin/black-panther-marvel-comics-6828688/ Remove the
cape and and high collar from the skin and make it a model that reacts with the players movement, make it look cool".

- **Skin**: "Black Panther [Marvel Comics]" (PlanetMinecraft file 19479117, Steve / wide arms; borrowed, ships publicly). Original kept
  untouched at `scratchpad/v0177/bp_skin_original.png`; `node scratchpad/v0177/make_bp_suit_skin.js` writes `black_panther.png`,
  `black_panther_glow.png`, `black_panther_cape.png`, `black_panther_collar.png` (all in `textures/entity/black_panther/`) and the
  previews in `docs/screenshots/v0177/bp_suit/`. One wide texture for every wearer (the suit model is always wide).
- Black suit with gold trims: gold eye lenses, a gold neck band with four hanging claw-teeth and a centre point on the upper chest
  (rows 20-22 of the body front, plus the front half of the shoulders' top), a gold V belt at the waist, gold clawed bracers on both
  forearms. Cat-ear tips on the mask's top corners.
- **Removed from the paint**: the purple-grey cape (body back, back of the shoulders, body sides' back columns, the legs' backs, the
  cape fold highlights on the second layer) and the high collar (the light texels round the back / sides of the head's lower five rows,
  the underside ring, the hat layer's copies).
- **Filled**: the back is the front seen from behind without the hanging teeth, so the gold neck band and the V belt run all the way
  round; sides and leg backs mirror their own fronts (boots included); a vibranium spine + shoulder-blade seam (a Y) on the back; the
  bracers' gap on the inner back of each forearm closed; the mask's sides / back repainted as a dark hood. Every base texel is opaque.
- **Glow map** (Kinetic bar): the gold trims and the back seams, violet.
- **Cape** (`BlackPantherCapeLayer`, on the shared cloth `FlowingCapeLayer`): full length (1.25 blocks, to the shins, as painted),
  purple-grey with folds, a dark-gold hem, violet-black lining. Swings with walking / sprinting / crouching like the Thor and Shazam
  capes; streams up behind him while falling faster than 0.45 blocks/tick (or elytra gliding). Builds pixel by pixel (see "Nanotech
  build"), vanishes hem-first on the way back.
- **High collar** (`BlackPantherCollarLayer`): a curved standing wall round the back and sides of the neck (110 deg either side of
  straight behind, open at the front), 5 px tall behind the head down to 2 px at the front ends, flaring out 14 deg, on a yoke over the
  shoulders, with a short skirt behind that runs down over the cape's top. Radius 6.35 px so its inside clears the suit head's corners
  however the head turns. Body frame (torso turn, crouch); turns 30% with the head (max 20 deg), leans back with 60% of an upward look
  (max 35 deg), flares with the cape's lagging swing (+ side sway twist) and opens 10 deg more in a fast drop. Builds pixel by pixel
  as the spread reaches the shoulders (see "Nanotech build"). Purple-grey ribs, lining inside, gold top edge.
- Both layers draw only while `BlackPantherRender.anim(player).anything()` (the suit is out or building), never in first person.

## Passives

| Passive | Unsuited | Suited |
|---|---|---|
| Melee (bare fist) | 8 (v0.17.7 user: "make his melee deal 8 damage and 10 with the suit please so its more similar to super soldier", was 5) | 10 (claws always out) |
| Regeneration | I | II |
| Resistance (v0.17.7) | I | I |
| Herb | Speed I; jump 2 blocks (jump strength +0.19, peak 2.44) | Speed I; jump 3 blocks (+0.32, peak 3.46) |
| Falls | half (Cat's Landing) | none |
| Night Eyes | in the dark | always |
| Hunter's Instinct | creatures hunting him within 20 outlined | same |
| Kinetic Absorption | -- | 40% of a hit soaked, 1 damage = 4 Kinetic |
| Vibranium Weave | -- | 20 armour / 8 toughness, full diamond (attribute modifiers while suited); shots under 15 bounce off |
| Silent Paws | -- | no vibrations |

The melee is one attribute modifier (`black_panther_herb`): `UNSUITED_MELEE - 1` or `SUITED_MELEE - 1`, set through the shared melee rule (`combat.MeleeBonus.fist`; v0.17.7 user: "+5 on top of tools and weapons ... do this for black panthers"): a punch (empty hand or a non-weapon item) deals 8 / 10, a weapon or tool its own damage + 8 / + 10 (the shared `melee_bonus_weapon` +1 while one is held). The jump is a `JUMP_STRENGTH` modifier (`black_panther_jump`, `JUMP_UNSUITED` / `JUMP_SUITED`; the old endless Jump Boost is cleared). Regeneration and
Resistance I (`RESISTANCE_LEVEL`, user: "give player passive resistance one") are infinite `PowerToggles.effect`s refreshed in
`reconcile` (every 20 ticks), cleared by `revoke`.

## Unsuited kit (T'Challa)

| Key | Move | Damage / effect | Cooldown |
|---|---|---|---|
| R | Warrior's Combo | 5 / 7 / 10 (jab on the press, kick shoves) | 1.5 s |
| Shift+R | Leg Sweep | 7 within 2.6, trip 1.5 s | 6 s |
| G | Takedown | grab within 3 blocks, slam 12 (v0.17.7 user), daze 2 s | 8 s |
| Shift+G | Ground Pound (new) | hop, dive, slam: 15 (v0.17.7 user) within 3.5, knock-up, Slowness II 1.5 s | 8 s |
| X | Vault | front flip, no fall damage | 2.5 s |
| Shift+X | Flying Kick (new) | dash ~8 blocks along the full look (aim up = kick upward, aim down = dive; a downward aim from the ground kicks level), 10 to the first creature reached, rebound; a wall / ceiling / the ground one step ahead stops it dead, soft landing (`FLIPS` 60 ticks, refreshed when it ends); the body tilts with the look pitch (`BlackPantherPose.flipDegrees`) | 6 s |
| Z | Throwing Knife | 8, returns (renderer: sprite plane along the flight, tumbling forward end over end, shared with the suited dagger) | 2 s (v0.17.7 user: "Lower dagger throw cooldown", was 4 s) |
| Shift+Z | Kimoyo Scan | mark 10 s, +25% from him | 15 s |
| V | Rising Uppercut (new) | 9 within 3, launched + Slowness III 1.5 s | 6 s |
| Shift+V | Hunter's Tracking | toggle, outlines + footprints, 24 | -- |
| C (hold) | Block (new) | every hit from the front -60%, no time limit (as long as C is held) | 6 s, from release |
| Shift+C | Wakanda Forever (was Ancestral Resolve; id still `ancestral_resolve`) | Speed II, Strength II, Regeneration II, Resistance II for 50 s (the endless passives wait behind them as vanilla hidden effects and return after); purple aura every 3 ticks for every viewer (`WAKANDA` map). Shared code `BlackPantherUnsuited.wakanda` with the suited Shift+V | 100 s, from activation, ONE cooldown shared with the suited cast |

Bosses only take the damage of the control moves. The new moves were designed for the user's requests: Ground Pound replaces
Disarm, Flying Kick replaces Backflip, Block replaces Parry (no perfect parry / riposte), Rising Uppercut replaces Shadow Step.

## Suited kit (v0.17.7, user-approved)

The user approved this moveset ("build this please") and asked that the Kinetic bar never dissipate. Rule: **plain keys are free
and fill a little Kinetic when they land; Shift keys spend Kinetic for a charged violet version.** The numbers are placeholders
from the approved table: the user dropped power-tier balancing ("i want to do all the damage numbers myself"), so every damage,
cost, gain, duration and cooldown is a named constant in `BlackPantherConfig` (section "v0.17.7 suited kit", one block per move),
and the new moves have no tier cap (`BlackPantherCombat.strike(..., tierCap = false)`, boss cap only: 8% of a boss's max health a
hit; bosses are never pinned or knocked back, and they keep their threat table under Stalk).

| Key | Move | Kinetic | Damage / effect | Cooldown |
|---|---|---|---|---|
| R | Vibranium Combo | +5 per strike landed | claw strikes 8 / 9 / 10 (6 ticks apart, the first on the press); the 3rd leaves a violet slash mark (three claw lines of violet dust) | 1.5 s |
| Shift+R | Kinetic Combo | costs 25 | three fast charged slashes, 10 each (4 ticks apart); the finisher releases a forward cone shockwave (4 blocks, 60 deg either side): 8 to everything the slash itself missed | 2 s |
| G | Pounce | +10 on a hit | always fires (user: "dont make it need an enemy in order to activate, give it slight aim assist tho so that it cant track targets still"): at launch only, `assistTarget` picks the living target nearest the crosshair within 8 deg (`POUNCE_ASSIST_DEGREES`), in range (8) and in sight, and aims the leap at where it stands; else the leap goes along the aim (pitch included; off the ground a flat / low aim rises at least `POUNCE_MIN_RISE`). The direction is fixed: driven along it at 1.6 b/t for the distance, then ballistic; no steering. The first target within contact (2, or its width + 1.2) is landed on: **12** (user, was 9), pinned (Slowness VII) 1 s. Nothing reached in 16 ticks / back on the ground: it just ends | 6 s |
| Shift+G | Kinetic Pounce | costs 30 (refused when short) | the same always-firing leap and launch-only assist, ending in a violet ground burst: **25** (user, was 10) to everything within 3 blocks, knocked away; the creature landed on pinned 1 s. With nothing reached it bursts where he comes down | 8 s |
| X | Panther Leap | -- | (user: "make it dash player in direction theyre aiming in instead of just up and forward please") launched at 1.62 b/t along the full look, pitch included (`leapVelocity`: straight up is still ~12 blocks; off the ground a flat / low aim keeps 0.42 up, `LEAP_MIN_LIFT`); ledge catch; no fall damage (suited) | 1.5 s |
| Shift+X | Kinetic Dash | costs 20 | ~10 blocks straight along the look (pitch included) over 5 ticks, moved by the server in 0.25-block collision-checked steps (stops flush at a wall); no damage reaches him for the dash + 2 ticks; each creature passed through takes **18** once (user, was 8); no fall damage; violet afterimages | 3 s |
| Z | Vibranium Dagger | +5 per target hit | 8, 3 hops | 3 s (user: "Lower dagger throw cooldown", was 7 s) |
| Shift+Z | Kinetic Overload (ULTIMATE) | the whole bar, must be FULL | leap + ground slam: 60 at the centre falling straight to 45 at 10 blocks; big violet dome + ring + sonic boom | 5 s |
| V | Stalk | -- | lose mobs past 6 while sneaking; backstab x3 (x1.75 players), max 20 | 10 s |
| Shift+V | Wakanda Forever (id `ancestral_resolve`, `BlackPantherAbilities.wakandaForever`) | free; spends 30 only if stored | user: "Kinetic Shroud: Make this the Wakanda forever ability, can be activated whenever, if player has 30 kinetic energy then activate the violet shimmer and 50% less damage and the other stuff ...". Always usable: the unsuited Wakanda Forever (Speed / Strength / Regeneration / Resistance II 50 s, purple aura). With >= 30 Kinetic at the cast it spends 30 and adds the old Kinetic Shroud for the same 50 s (`SHROUD_TICKS` = `RESOLVE_TICKS`): every hit 50% less, a living attacker that lands one takes 4 back (thorns; never for thorns), violet energy shimmer over the suit. Under 30: the buffs alone, nothing spent, no refusal. The separate Kinetic Shroud move is gone | 100 s from the cast, the same cooldown entry as unsuited Shift+C |
| C (hold) | Absorb Stance | fills | frontal hits into Kinetic, 40 a stance | 4 s |
| Shift+C | Kinetic Return | spends up to 40 | max(8, shot x1.25) + 0.25 per Kinetic spent, max 20 | 6 s |
| Shift+N | Mask | -- | toggle | -- |
| H | Suit Up / Away | -- | the necklace worn: the suit forms / drains back (both kits) | one change at a time |

- **Refusal:** a Shift move with too little Kinetic fires nothing, spends nothing and starts no cooldown; a dull note (note-block
  bass + amethyst hit, to him only) plays, the action bar says how much is needed, and the HUD's Kinetic Meter flashes red / white
  for 12 ticks (`abilityReadyAt["kinetic_denied"]`, synced). Kinetic Overload one short of full is refused the same way.
- **Gains** only from a strike that really landed on a living target (`strike` returns whether `hurt` took); a whiff adds nothing.
  Shift moves never add Kinetic back.
- **Removed from the suited kit (v0.17.7):** Kimoyo Flash (and the Kimoyo bead entity), Panther Senses, the Kinetic Pulse, the
  Claw Combo (its bleed), Drop Strike, the suited Warrior's Combo. Hunter's Instinct (passive), Kimoyo Scan and Hunter's Tracking
  (unsuited) stay. The `kimoyo_bead` item stays registered (unused) so old stacks load.
- Shroud timer: `abilityReadyAt["kinetic_shroud_active"]` (synced, so every viewer sees the shimmer); cleared when the suit goes (the
  Wakanda Forever buffs carry on).
- Effects across the suit change: the suit's endless Regeneration II swallows Wakanda Forever's timed II (same level); when `reconcile`
  takes the endless II off after the suit goes, it puts the rest of the timed II back (`BlackPantherUnsuited.wakandaRemaining`), so
  the passives' refresh never cuts the buff short. Resistance / Speed II simply sit over the endless I (vanilla hidden effects).

## Kinetic Energy (v0.17.7)

0-100. Fills from Kinetic Absorption (40% of each hit soaked, 1 damage = 4 Kinetic), Absorb Stance and the plain moves' on-hit
gains above. **It never decays while suited** (user: "make it so that the kinetic energy bar doesnt dissipate, it doesnt dissipate in
the movies"). Later the same day (user: "make kinetic energy dissipate slowly when suit is in necklace mode please"): with the suit
away (in the necklace, or no suit) it leaks `KINETIC_UNSUITED_DRAIN` = 1 every `KINETIC_UNSUITED_DRAIN_EVERY` = 40 ticks (game time
divisible by 40, `BlackPanther.kineticDrains`; a full bar lasts ~3 min 20 s), only while he is online (`BlackPanther.tick`); a relog
keeps the bar (the attachment is persistent). The HUD keeps the Kinetic Meter on show unsuited while anything is left. **A death
empties it** (`BlackPanther.onDeath`, from `AFTER_DEATH`, before the copy-on-death copy).

## HUD

Bottom-right `VanillaHud.Stack`: title always "Black Panther" (no T'Challa title, no passive status words, user v0.17.7), the
Kinetic bar in the **Meter** style (suited, or unsuited while it still holds something; flashes red / white on a refused Shift move),
the six key boxes for the live kit (V lit while Stalk or the Shroud runs, C while absorbing; suited Shift+V shows Wakanda Forever's
shared cooldown). No Absorb bar (v0.17.7, user: "remove the absorb bar please when using absorb stance please"): the stance still
takes at most 40 a stance (`ABSORB_MAX`), and the Kinetic Meter filling is the only feedback. Left Alt lists both moves per key (Overload under Z), H
Suit Up / Away (necklace worn or suited) and Shift+N Mask in the suit.

## Animations (BlackPantherPose)

Anim ids keep their numbers: 13 = Ground Pound (was Disarm), 15 = Flying Kick (was Backflip), 18 = Rising Uppercut (was the
riposte). v0.17.7 suited kit: 5 = Vibranium Combo (the claw clip, `VIBRANIUM_COMBO_STEP`), 6 = Pounce leap, 22 = Pounce landing
rake, 23 = Kinetic Combo (two cross slashes then a two-handed thrust lunge), 24 = Kinetic Pounce burst (both fists down, a knee),
25 = Kinetic Dash (low, chest forward, arms swept back, then a skid), 26 = the Shroud (fists crossed, then flung open; played by the suited Wakanda Forever when it adds the Shroud, else 19 = Wakanda Forever), 1 / 2 (suit up / down) retired in v0.17.7, 4 =
Overload rewritten as a ground slam (sink, fists overhead, hammer down on a knee). Each has a first-person hand clip. 3 / 7 / 21
(Pulse, Drop Strike, Kimoyo Flash) are retired. `BlackPantherEffectsRenderer`: the ring, plus Overload's violet dome (latitude
bands, brightest at the rim) and the Dash's afterimages (translucent violet silhouettes every 0.7 blocks, fading over 10 ticks,
client-only map). `BlackPantherRender.renderShroud`: the charged creeper's swirl in violet on a slightly larger shell while shrouded. Block uses the held stance `BLOCK` (forearms crossed in front of the face) and `F_BLOCK` in first person. The Ground
Pound's slam also sets the shockwave ring (`pulseStart/pulseRadius/pulsePos`, drawn by `BlackPantherEffectsRenderer`).
