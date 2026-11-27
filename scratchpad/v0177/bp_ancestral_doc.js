// v0.17.7: writes the Ancestral Plane section into docs/BLACKPANTHER_REFERENCE.md (CRLF kept). Run from the repo root.
const fs = require('fs');
const p = 'docs/BLACKPANTHER_REFERENCE.md';
let s = fs.readFileSync(p, 'utf8');
const nl = s.includes('\r\n') ? '\r\n' : '\n';
const oldGet = 'Heart-Shaped Herbs grow in jungles. 3 herbs + a bowl = Heart-Shaped Herb Soup. Drinking it runs the 10 s Ancestral Plane, then' + nl +
	'grants the power. It replaces the Primary power. The suit is crafted separately (the necklace below).';
if (!s.includes(oldGet)) throw new Error('Getting it paragraph not found');
const newGet = `Heart-Shaped Herbs grow in jungles. 3 herbs + a bowl = Heart-Shaped Herb Soup (recipe unchanged). v0.17.7: drinking it gives
**Bast's Blessing** for 60 s and nothing else (no power, no 10 s vision); falling asleep in a bed while blessed opens the
**Ancestral Plane** (below), whose test grants the power on waking. It replaces the Primary power. The suit is crafted separately
(the necklace below). A legacy save caught mid-vision (\`visionUntil\` set by 0.17.3-0.17.6) still gets the power when it runs out.

## The Ancestral Plane (v0.17.7)

The user: "to get this power you need to craft the heart shaped herb soup / drinking this gives you Bast's Blessing for 1 minute /
then player needs to sleep, upon falling asleep player is transported to the ancestral plain, it looks like a savana ... their is
a warm orange sunset, player will spawn a distance away from their ancenstors, they will need to walk up to their ancestors and they
will then take you to a panther shrine, in there player must right click the panther shrine and then the player will be tested,
player will have to beat a minigame and do a 100 block parkour course and once they complete that they will get the powers ...
incorporate cutscenes, this must all take place in the players mind palace thing similar to hulk and doctor fate".

Package \`blackpanther.plane\`, client \`client/blackpanther/plane\`. Built on the **Shazam journey framework**
(\`shazam.journey\`, see FATE_SHAZAM_DESIGN "how players get Shazam"): a private slot per dreamer in a datapack dimension, a saved
return point, server-authoritative stages, the client shooting every cutscene from shared beats, the shared \`CutsceneHands\`, the
journey camera / GUI / "Loading terrain" mixins (they now also ask \`AncestralPlaneClient\`), and the same guards.

- **Bast's Blessing** (\`BastBlessing\`, \`projecthero:bast_blessing\`): a beneficial MobEffect, 1200 ticks, ambient (soft
  particles), colour #9A5BD6, icon \`textures/mob_effect/bast_blessing.png\` (gold-rimmed black panther head on a violet glow). No
  effect of its own. \`BlackPanther.canDrink\` refuses a second soup while blessed (message) or mid-dream.
- **Sleep**: \`AncestralPlane.tick\` takes any player asleep (\`getSleepTimer() >= 80\`, vanilla's screen is nearly black at 100,
  before a night skip at 100) with the blessing into the plane: the blessing is spent, the bed position and where they lay are saved
  (\`AncestralPlane.RETURN\`, \`PlanePayloads.Return\` with an optional bed). **Day sleeping:** \`EntitySleepEvents.ALLOW_SLEEP_TIME\`
  returns SUCCESS for a blessed player (so a bed works at noon), and also for a player being woken back in bed (so the day doesn't
  throw them out of it at once). Every other bed rule (monsters nearby, obstructed, too far, the Nether) is vanilla's.
- **The plane** (\`PlaneRealm\`): \`projecthero:ancestral_plane\`, a flat datapack dimension (bedrock, stone, dirt, grass; skylight,
  fixed noon light, no weather, no mobs; biome \`projecthero:ancestral_plane\` with golden grass #C8A048, olive-gold foliage,
  orange fog/sky, a few drifting ash motes). One slot per dreamer, 1000 blocks apart along Z; dressed once in slices (grass tufts and
  tall grass in drifts over x -56..268, z +-84; lilacs and alliums round the ancestors; 46 flat-topped acacias round the horizon;
  the great acacia with a 2x2 trunk and four crowns; the shrine) and reused (bedrock marker 12 under the spawn). Fallback for the
  GameTest server: the same in the overworld sky at x 1,250,000, y 150 on its own grass floor.
- **The sky** (\`AncestralSky\`, effects \`projecthero:ancestral_plane\`): a gradient dome (violet zenith, magenta, rose, a blazing
  orange-gold horizon), a golden bloom round a huge sun 4 degrees up in the east (+X: behind the ancestors, the shrine and the end of
  the path, so they stand dark against it), thin pink-gold cloud streaks near the horizon, four slow purple aurora curtains overhead
  (additive, rippling), faint stars at the dark top away from the sun. Fog = the horizon orange, pulled in to 22-98% of the render
  distance (\`FogRendererMixin\`) so the grass melts into the glow; no clouds or weather. A warm film tint over the HUD in the plane.
  Ambience: wind gusts (elytra, low and quiet) every 6-15 s, a distant big cat (polar bear warning / cat purreow, pitched down)
  every 25-60 s.
- **Layout** (\`PlaneGeometry\`, offsets from the slot origin, +X toward the sun): spawn x 0; the five ancestors x 70-74 under the
  great acacia (80, 13), the eldest at (70.5, 0.5) -- 70 blocks; the shrine centred x 110; the spirit path from x 128 to 241.

### The story (\`AncestralPlane\`, cutscenes and beats in \`PlaneScene\`)

1. **ARRIVAL** (170 t, cutscene): black, then the savanna fades in from high above looking into the sunset, the camera glides down
   behind the dreamer and into their eyes; "The Ancestral Plane" title. Resonant chime, wind, a far purr.
2. **WANDER** (free): a trail of gold and violet motes (sent only to the dreamer) drifts from a few blocks ahead toward the eldest;
   the ancestors turn their heads to watch; HUD hint. Within 9 blocks of the eldest (or right-clicking any ancestor) ->
3. **GREETING** (330 t): placed 6 blocks before her; she steps forward. Shots: behind the dreamer with the group and tree against
   the sun, over the dreamer's shoulder, over hers, a rising wide. Lines (\`subtitle.projecthero.ancestral_plane.greet1-4\`, original).
4. **PROCESSION** (250 t): they walk (scripted, a walking pace with soft start/stop, staggered) 24 blocks to the shrine's west
   entrance and stand either side of it; the camera walks backward ahead of them, then looks down from over the shrine. Two lines.
   (Waits for the shrine on a brand-new slot.)
5. **SHRINE** (free): right-click the statue, its pedestal or the altar (\`PlaneGeometry.isShrineHeart\`; every other block use is
   refused in the plane). A violet shimmer at the altar.
6. **TRIAL_INTRO** (210 t): the statue's obsidian eyes turn to crying obsidian (beacon, a high dragon growl, violet burst, camera
   shake), Bast speaks three lines from a low hero shot, a close-up of the face, over the statue's shoulder and a circling wide as
   shadow wisps gather.
7. **TRIAL** -- the **Trial of the Shades** (\`ShadeTrial\`, pure rules): see below.
8. **PATH** (170 t): Bast's line; the spirit path forms platform by platform (amethyst chime rising in pitch, violet bursts) while the
   camera flies along it, then returns to the start.
9. **PARKOUR**: the 113-block spirit path (\`ParkourCourse\`), see below.
10. **FINALE** (320 t): on the finish platform facing the sun, the ancestors either side; **Bast's spirit** (a panther shade at 7x,
    gold-violet starlit hide, burning gold eyes) rises hovering beyond the end of the path; her two lines, the eldest's farewell, a
    gold flash into black (totem, beacon) ->
11. **Waking**: back at the saved spot, lying in the bed again (\`startSleeping\`) for 2 s then up (\`stopSleepInBed\`); the dark lifts
    (WAKE scene); \`BlackPanther.grant\` exactly as before (\`HeroTiers.claimPrimary\`, the title / subtitle and chat lines). No bed
    (the test command, or the bed broken): just back where they were.

**Skipping:** each cutscene watched to the end sets a bit in \`AncestralPlane.SEEN\` (persistent, copy-on-death); a seen scene shows
"[Space] Skip" and Space sends \`PlanePayloads.Skip\` -> the stage jumps to its end (the next stage's start is idempotent: ancestors
placed, eyes lit, path built). The first viewing can't be skipped.

### The Trial of the Shades

Ghostly panther shades (\`PantherShadeEntity\`: the vanilla ocelot model at 1.6x, a translucent near-black hide with violet rim light,
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
checkpoint ticks. Built only when the trial is won (\`PlaneRealm.buildPath\`), cleared at the next visit (\`resetForVisit\`, which also
puts the statue's eyes back to obsidian).

### The shrine and the ancestors

- **Shrine** (built in code): a round floor of polished blackstone bricks (top 2 above the grass, half-slab steps so it walks up),
  cracked bricks here and there, an amethyst ring at radius 6, a gold claw sigil of three slashes at the centre; eight pillars
  (radius 11, none on the east-west axis) with brick bases, a chiseled band, gilded capitals, gold tops and lit purple candles,
  amethyst clusters at their feet; a crescent crown of bricks rimmed in gilded blackstone over them, open behind the statue; two
  braziers (campfires on walls) at the west entrance; the altar (chiseled blackstone, purple candles, amethyst clusters); a 9x9
  pedestal trimmed in gilded blackstone; the **seated black panther statue** (\`PlaneRealm.statue\`, voxels from ellipsoids:
  haunches, chest, neck, head with muzzle and ears, straight front legs, paws, tucked hind feet, the tail curled round to the front;
  polished blackstone, smooth black concrete on top, blackstone at the base, a gold necklace and pendant on the chest), about 10 tall,
  facing west over the altar.
- **Ancestors** (\`AncestorEntity\`, player model, never saved, can't be hurt or pushed, moved only by the story; the walk animates
  from the movement): five robed royals in skins from \`scratchpad/v0177/gen_ancestor_skins.js\` (original, no likeness): the eldest
  in white and gold with a headwrap and violet sash, a king in purple with a gold crown band and grey beard, a warrior queen in violet
  with white bands and a circlet, a scholar in plum with braids, a guardian in indigo with bronze trim and a purple wrap. Gold and
  violet motes drift round them. Preview \`docs/screenshots/v0177/bp_ancestral/ancestor_skins_preview.png\`.

### Safety

Nothing in a dream (or anywhere in the plane dimension) takes damage (\`ALLOW_DAMAGE\`, /kill still works), builds, breaks or uses
items; only the shrine's heart answers a right-click. Straying out of the box (x -60..300, |z| <= 110) draws you back to the current
stage's spot. **Hold Sneak 5 s** in a free stage (HUD bar after 1 s) = wake early, no power. Logout: the dream ends (ancestors, shades,
spirit discarded), the return point stays and \`check\` (every 10 t, any player with no dream) sends them back to bed next login; a player
found in the plane with nothing saved goes to world spawn. Death ends it and clears the return point on respawn. Taken out of the plane
another way: the dream ends quietly. Server stop: \`ServerStateReset\` -> \`BlackPantherMod.clearSessionState\` ->
\`AncestralPlane.clearSessionState\` (dreams and pending wake-ups are player UUIDs, builds are dimension keys; no worlds or entities held).
Client: every scene times out by itself, movement keys are held during scenes, hotbar/crosshair/chat/effects hidden, first-person hands
hidden (\`CutsceneHands\`), "Loading terrain" drawn black, everything clears on disconnect.

### Test commands (op)

\`/projecthero power tune black_panther blessing\` (Bast's Blessing 60 s; then sleep), \`plane\` (fall into it now from where you stand,
no bed), \`shrine\`, \`trial\`, \`path\`, \`parkour\`, \`finale\` (jump to that stage, starting a dream if needed), \`leave\` (wake, no power).
Refused if you already have the power.

**Tests:** \`BlackPantherPlaneV0177GameTests\` (the soup's blessing, day sleeping, a real bed -> the plane -> hold Sneak -> back in bed
with no power, leaving early, logout + check, the whole dream stage by stage -> the power, the finale granting once, the trial's rules,
shades striking, the course's rules and fairness, a fall reset on the live path). \`BlackPantherGameTests.soupOpensTheAncestralPlane...\`
updated for the blessing.`.split('\n').join(nl);
s = s.replace(oldGet, newGet);
s = s.replace('`BlackPantherV0177GameTests`, `BlackPantherSuitedV0177GameTests` (the v0.17.7 suited kit and the no-decay Kinetic bar).',
	'`BlackPantherV0177GameTests`, `BlackPantherSuitedV0177GameTests` (the v0.17.7 suited kit and the no-decay Kinetic bar),' + nl +
	'`BlackPantherPlaneV0177GameTests` (the Ancestral Plane).');
fs.writeFileSync(p, s);
console.log('ok');
