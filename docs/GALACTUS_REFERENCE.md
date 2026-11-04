# Galactus: The Devourer Descends (raid v2, v0.17.2)

The endgame world event, rebuilt as a **three-stage super raid with five cutscenes** (design and the user's approval:
`docs/GALACTUS_RAID_V2.md`; the friend's notes: `docs/reference/galactus_revamp/friend_notes.png`). The duel with the Silver
Surfer happens at the Herald's Beacon; the three stages are fought **in orbit**, in a dimension of their own.

Code: `com.projecthero.mod.galactus` (event, config, items), `galactus.orbit` (the orbit, stages, the Devourer),
`galactus.cinematic` (scene timings, the hold), client `client.galactus` (sky, cutscene camera/HUD, renderers). Tuning:
`config/projecthero_galactus.json` (`GalactusConfig`, VersionedConfig **v4**: new `orbit`, `stage1`, `stage2`, `stage3`
sections; Nullifier 4 s; chest x1.5). Gametests: `GalactusGameTests`. Lang: `scratchpad/lang_galactus_raid_v2.js`. Item
icons: `scratchpad/gen_galactus_raid_v2_items.js`. Harness: `scratchpad/GalactusRaidHarness.v0172.java.txt`.

## 1. Flow (`GalactusEvent.Phase`)
| Phase | Where | What |
|---|---|---|
| ARRIVAL | overworld | **C1** (160 t): the beam turns violet, a star grows and falls -- the Herald streaks down from orbit (`GalactusScenes.heraldPos`), a sonic boom at t70, he pulls up over the beacon at t112, title |
| HERALD | overworld | the v0.16 Surfer duel (blast / dash / shield / lift, `BossThreat`); he can't be killed |
| KNEEL | overworld | at 10% he kneels (`SilverSurferEntity.holdTurn`: no auto-ally) -- 70 t |
| DEVOURER | overworld | **C2** (280 t): the sky darkens, a 230-block Galactus head rises over the horizon 105 blocks out, eyes ignite (t148 / t160), **the roar** (t172), the party levitates (t205) into violet light, warp streaks (t232), white-out, teleport |
| ASCENT | orbit | arrival sweep over the meteor field (70 t) |
| STAGE1 | orbit | **The Shattered Orbit** (`StageIslands`) |
| PULL | orbit | **C3** (200 t): the Worldship slides in; island rock flies onto its hull (t50 -> t130, block displays); Galactus moves to kneel over the Earth; the Surfer turns |
| STAGE2 | orbit | **The Feast** (`StageFeast`) |
| RISE | orbit | **C4** (180 t): he lets go of the Earth and rises to 100 blocks beside the deck, plants a hand on its rim (t132) |
| STAGE3 | orbit | **The Devourer** (`StageDevourer`); at 5% the finale (the Nullifier) |
| ENDING | orbit | **C5** (240 t): the Nullifier beam, his helmet cracks, he comes apart into stars (t120), the Worldship leaves, white-out; everyone falls home (HOME fade) and the beacon becomes the chest |
| CONSUMED | orbit | the loss (World Consumption 100%): the Earth drains grey, everyone home |

`/projecthero raid advance galactus` steps through every beat (works from the orbit too). `end` aborts (everyone home, the
instance wiped).

## 2. Cutscenes
`GalactusCinematics.play(players, scene, anchor, yaw, elapsed, bossId, extra)` sends `CinematicPayload` and **holds** the
players for the scene (sideways only -- the ascension's lift is real; unhurtable). The client (`GalactusCinematicClient`)
flies the camera per scene (`GalactusCinematicCameraMixin`), draws letterbox bars, subtitles, fades, the warp, and fires
`QuirkCameraFx` shakes / FOV punches on cue. **Hold Sneak** skips your own view. Timings live in `GalactusScenes` (both
sides). Late joiners and re-loggers mid-scene get the scene from where it is (`elapsed`).

## 3. The orbit (`GalactusOrbit`)
- `projecthero:galactus_orbit`: void, fixed night, ambient light 0.55, effects id `projecthero:galactus_orbit` (no clouds,
  no weather). Sky: `GalactusOrbitSky` (Fabric sky renderer) -- violet-black wash, 4200 stars + 260 twinkling, two nebula
  bands, the sun, **the Earth** (lit sphere + clouds + blue rim, procedural textures made at start-up), a grey "consumed"
  Earth fading in with World Consumption, a violet feed ribbon in Stage 2 (`GalactusSkyPayload` stage 4).
- Instances: slot `n` at x = 20000 (n + 1), plane y 128; a region chunk ticket (not saved) keeps it loaded; `wipe` clears
  every non-air section of its 224x216x224 box and every non-player entity -- before building and after the raid.
- Saved data `projecthero_galactus_orbit`: per-player **return point** (set when first lifted) and **grave** (a death up
  there stashes the inventory -- the Nullifier drops on the deck instead, Cosmic Cells / parts vanish -- and returns it on
  respawn; keepInventory respected).
- A logout up there: back to the raid on login (`rejoinOrbit`), or home if it ended. Strays are swept home every 5 s.
- **Rules** (`OrbitRules`): Jump Boost II; fall 14 below the plane -> the Surfer catches you onto safe ground (5 damage);
  Stage 1 flyers (over 6 above the plane, or flying/elytra/board/levitation) get a red lock-on at 30 t and the beam at 50 t
  (12 damage, slammed down, grounded 5 s); gravity pads launch along an arc (`OrbitArena.launch`); no building or breaking.
- The beacon is a **Rift** while the fight is up there: right-click it to go up.

## 4. The Devourer (`DevourerEntity`, `GalactusRig`, `DevourerPartEntity`)
Our own `galactus` GeckoLib model at any height. Pose = synced numbers (height, lean, look, a target + weight per arm, eye
glow, charge, flags); **`GalactusRig.solve`** (two-bone IK, rest = arms straight down) is used by the server (where fists land,
where hit-boxes go) and the renderer (`DevourerRenderer`: bone rotations from inputs interpolated between ticks), so they
always agree. Render space: X mirrored, bone matrix Rz*Ry*Rx; a positive waist rotX tips back, so leaning forward is
negative. Eyes and the Planet Cracker sphere are additive billboards. Parts (head, chest, upper/fore/hand per arm) are
living hit-boxes resized to the skeleton every tick; they take no damage -- `onPartHit` hands every hit to the raid.

## 5. Stage 1: the Shattered Orbit (`StageIslands`)
11 islands (+1 per extra fighter, 19 max): the Anchor (r7, never smashed) + a hex ring of 6 + outer ring, 5-block gaps
(`OrbitIsland.field`; each island a pure function of its seed). Gravity pads: Anchor -> each outer island and back. Galactus
(180 blocks) stands 78 out, 125 below, **drifts round toward the party**, leans in to reach. Smash: the island glows red for
3 s (`windTicks` 60), the fist comes down (8 t), the island shatters into flying rock (14 damage + flung), **the fist stays
stuck 4 s** (its hit-box takes damage into the **Stagger** bar: 220 +80/extra, x1.25 each time), then rips free. 3 staggers
end the stage. After stagger 1: double smashes. After stagger 2: backhand sweeps along a row (10, 40 t warning). Islands
re-form after 20 s. Each stagger pays everyone who hit the fist a **Cosmic Shard** at the end.

## 6. Stage 2: the Feast (`StageFeast`)
The hull (`OrbitArena.hull`, r34). Galactus kneels over the Earth 118 out. 3 converters (+1 per two extra, max 5), each
shielded while its 2 Punishers live, **only killable by an overload**. Feed beams throw off a **Cosmic Cell** every 20 s
(2 loose/carried max). The **Carrier** glows, is slowed, can't fly, drops it to any 6+ hit; Punishers chase Carriers.
Right-click an unshielded converter with a cell: **overload** 6 s (Punishers within 7 stall it; he bolts it once at
+1.5 s), then its beam reverses into his chest, it blows apart and drops a Nullifier Part. Eye beams sweep the deck (Carriers
first), Punishers drop every 25 s (cap 4). World Consumption +4%/min per live converter; 100% = CONSUMED.

## 7. Stage 3: the Devourer (`StageDevourer`)
100 blocks, 50 out from the deck centre (drifts toward the party), 8000 health (+2000/extra) on the raid's boss bar.
Parts: head x2 (x3 while gagging), arms/hands x1, chest core x1.5 **sealed until both gauntlets break** (600 +150/extra damage
to one arm = limp 25 s). Moves (60 t cooldown, x0.8 after the helmet cracks at 50%): Hammer Fist (stuck 3 s, a hard hand
hit in the wind-up cancels it), Palm Sweep (6 cover pillars block it, 2 hits collapse one), Eye Beams, the Grab (grip
80 +30/extra on the hand; victim held at his face), **DEVOUR** (every 5th move: head down at the deck edge, vacuum 6 s,
pillars shelter you, head hits fill the choke 150 +60/extra -> a 6 s gag at x3), Meteor Rain, Punisher drops, **PLANET
CRACKER** (below 40%, then every 90 s: 12 s charge between his hands, break both hands (300 +100/extra) or everyone takes
70% of max health). Four shoulder pads launch onto his nearest shoulder. **Finale** at 5%: he kneels, hands on the rim; the
Surfer gives the **Ultimate Nullifier** to the top damage dealer; hold right-click 4 s on his head (`UltimateNullifierItem`);
he beams the holder every 3.5 s; a dropped / lost Nullifier comes back.

## 8. Rewards
The chest (`GalactusRewards`, x1.5), first-clear Board + Power Cosmic, Cosmic Shards (4 + a beacon = a Herald's Beacon:
`heralds_beacon_from_shards`), and a chat line naming who used the Nullifier.

## 9. Rules kept
Every boss on the shared threat table (`BossThreat` on the Devourer, the Surfer); static scratch in `GalactusRaidStatics`
(cleared by `ServerStateReset`); config versioned; GeckoLib for the boss only (islands/hull are blocks, debris is block
displays). `EventInstance.offsitePresent` keeps the overworld-anchored event running while everyone is in orbit. v0.16
saves load into KNEEL (the reveal). The v0.16 Galactus / pylon / weak point entities still exist (old worlds) but are never
spawned.
