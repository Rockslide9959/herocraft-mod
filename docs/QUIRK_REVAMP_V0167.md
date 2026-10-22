# Quirk revamp (v0.16.7, user request 2026-10-22)

The user: "revamp the other quirks in the mod ... right now the quirks are not good at all, engine should have an ability
where they engage the engines to run faster and not just like 2 short dash moves, look at references and the anime to
decide the abilities".

## Rules for every Quirk
- Every key R / G / X / Z / V / C has a **tap** move and a **Shift** move. H stays the power selector. N is not used.
- **Slot rule (user, 2026-10-22; overrides the per-Quirk letters below where they differ):** X = the MOVEMENT ability, V = the
  UTILITY / mode ability, and the rest (R, G, Z, C and their Shifts) are mainly attacks.
- Each Quirk keeps its own meter (heat, focus, engine, shadow, dry eyes, sweat, nausea) and its Overload. They are
  re-tuned so the **signature sustained mechanic** below is the heart of the kit, not a row of one-shot moves.
- Every move gets a proper keyframed player pose, plus VFX and SFX in the style of the One For All moves.
- One For All is not part of this revamp; it was reworked separately.

## Engine (Tenya Iida, Ingenium): engines in the calves, built around RUNNING and KICKING
Built version. Two later user rules override the first draft: "give it 1 dash ability and the rest should be attacks and
a mode ability", and "for engine using v to shift up and down gears makes more sense" (X = movement, V = utility).

| Key | Tap | Shift |
|---|---|---|
| **V** (the mode) | **Gear Up** (0 to 3). At Gear 3: **Recipro Burst**. In Burst: **Recipro Extend** | **Gear Down**. In Recipro it ends it (Extend still stalls) |
| **X** (the one dash) | **Recipro Dash**: 7 blocks, +2 per gear, no damage | **Rising Kick**: a flip kick, 10 + 0.4 per b/s, launches the target up |
| **R** | **Recipro Kick**: 5 + 0.55 per b/s of speed (max 28) | **Spin Kick**: 360°, 7 + 0.3 per b/s, radius 3.5 |
| **G** | **Kick Barrage**: 6 kicks, 3.5 + 0.15 per b/s each | **Recipro Extend Kick**: a 0.5 s charge, then 14 + 0.5 per b/s (max 32) |
| **Z** | **Axe Kick**: the heel lands 6 ticks later, 12; slams down, Slowness II | **Sweep Kick**: a low spinning sweep in front, 7; trips (lift, Slowness II) |
| **C** | **Recipro Turbo** (ultimate): a 28-block straight-line strike, 18 to everything in the path; heat goes to 100, so you stall | **Ingenium Finisher**: kick 6, spin kick 7, then an afterburner launch kick 12 + 0.5 per b/s |

- **The gears (the mode).**
  - Top speed per gear: +60% / +100% / +140% (Speed III / V / VII).
  - A new gear starts at 40% of its top speed. **Momentum** builds the rest while you sprint: 1.25 s in Gear 1, 1.9 s in
    Gear 2, 2.5 s in Gear 3. Shifting keeps your current speed.
  - Recipro Burst is 10 s at 2x the gear. Recipro Extend is 15 s at 2.6x the gear, then a **6 s stall** (Slowness III).
  - Sprinting in gear heats the engines: 1 / 2.5 / 5 per second. Recipro adds 3 (Burst) or 2 (Extend) per second.
  - **Cooling is passive**: 6/s in neutral, 2.5/s walking or standing in gear, 8/s while stalled, and double in water.
  - At 100 heat you stall: 6 s of Slowness III, no moves, back to neutral.
  - **Sliding turns**: in gear with momentum, the client limits how fast the run can turn (26° / 17° / 11° a tick, less
    in Recipro), so it drifts. The server adds skid sparks and sound, and the slide costs 25% momentum.
  - **Bowling**: at Gear 2+ (or in Recipro) with 45% momentum, running into a hostile mob hits it for 3 + 0.35 per b/s,
    knocks it back and lifts it.
- **Passives**: one-block step assist and passive cooling. Wall Run, Engine Jump, Rescue Carry and Radiator Vent were
  dropped.
- **Visuals and sound**:
  - Calf exhaust trails grow with gear and momentum. Recipro adds blue flames, Turbo adds a white streak.
  - A looping engine rumble and whine rise in pitch with the gear, momentum and speed.
  - Every move has a keyframed pose. Spin, Sweep and the Finisher spin the whole body; the Rising Kick is a back flip.
- **HUD**: the Recipro timer, three gear cells with a momentum hairline, speed in b/s, and the heat bar (blue, then
  orange, then red).

## Hellflame (Enji Todoroki, Endeavor): fire, held back by body heat
- **Body heat** is the meter. Big moves raise it; at 100 you overheat, losing fire moves and taking damage until you cool.
- Slot rule (user, later in v0.16.7, for every Quirk): **X = movement**, **V = utility / mode**, R / G / Z / C = attacks.
- **Hell Spider (R):** five thin cutting fire strings sweep out from the fingers.
- **Hell Spider: Mesh (Shift+R):** a fire-string net that traps and burns.
- **Flashfire Fist: Prominence Burn (G, hold):** a huge sustained fire beam that ramps body heat fast.
- **Vanishing Fist (Shift+G):** a flame decoy, then a blink-strike from behind.
- **Fire Jet Flight (X, hold):** a sustained flame-propelled hover/flight (the movement key).
- **Flashfire Fist: Jet Burn (Shift+X):** a fire-boosted punch dash (works in flight).
- **Hellflame Barrage (Z):** rapid fire jabs.
- **Fire Pillar (Shift+Z):** a pillar of fire under a target.
- **Cool down (V):** vent body heat (works while overheated; the utility key).
- **Plus Ultra: Full Power (Shift+V):** for 15 s all fire moves are doubled; afterwards heat is pinned at max.
- **Fire Wall (C):** a wall of fire.
- **Flame Cloak (Shift+C):** a burning aura that damages anything nearby.
- As built: Body Heat numbers live in `config/projecthero_quirk_hellflame.json` (VersionedConfig v1). Overheat drains to 40
  over 6 s while he burns (1/s; fire damage, so Fire Resistance helps); ordinary fire never hurts him otherwise (lava does).
  Plus Ultra = double damage, 1.4x size, half cooldowns, no Heat; burnout = 10 s pinned at 100 with every key locked
  (even Cool Down). Fire blocks (Fire Wall, Pillar ring, beam impact) are temporary (GeoTerrain ledger), need the
  terrain-damage config + `mobGriefing`, sit on solid ground and are never lit within 2 blocks of anything flammable.

## Overhaul (Kai Chisaki): disassemble and reassemble by touch
As built (v0.16.7). Slot rule: X = movement, V = mode / utility, the rest attacks.

| Key | Tap | Shift |
|---|---|---|
| R | **Disassemble**: a bare-hand touch, 18 damage; a body it kills bursts on everything within 3.5 blocks (6). 14 Focus. | **Disarm**: touch what a mob holds (or, empty-handed, an armour piece) and it is destroyed; a player's is knocked from their grip instead. Not bosses. 20 Focus. |
| G | **Ground Spikes**: palm to the floor, a jagged line of stone spikes erupts toward the target, 14 blocks: 10 and a toss. 18 Focus. | **Spike Field**: both palms down, two rings of spikes round you: 9 to all within 5 blocks. 28 Focus. |
| X | **Terrain Surf** (movement): the ground breaks and reforms a step ahead and carries you the way you look for ~1.7 s, a ridge of earth rising in your wake, throwing aside what it runs over (5). X again: off. No fall damage. 14 Focus. | **Pillar Launch** (movement): a 3-block stone pillar shoots up under you and flings you high; no fall damage for 5 s. 12 Focus. |
| Z | **Overhaul Strike**: a low dash, bare hand out: the first enemy touched takes 14 and is thrown. 16 Focus. | **Disassemble Terrain**: the floor breaks out from under up to 6 creatures near where you aim; each drops into its own 2-deep pit, a lid closes over it, and 4 s later the ground closes back up and pushes it out. Not bosses / big mobs. 30 Focus. |
| C | **Wall Burst**: the ground ahead bursts into a 5x3 wall for 10 s, throwing back whatever stood there (5). 18 Focus. | **Gloves** (toggle; works while spent): on, no touch triggers (safe melee), every other key is locked and Focus refills twice as fast; off, the moves work and a bare-handed punch disassembles (+4 for 3 Focus). |
| V | **Chronostasis Fusion** (mode): absorb the ground for 20 s: bigger (x1.5), stone-plated, spiked limbs (thorns on gauntlets, shoulders, back) and two extra spiked arms; +8 damage, +12 armour, +2.5 reach. V again: end. 45 Focus. | **Reassemble** (utility): you, or the ally you look at, to full health with debuffs gone, and **Restore**: every block an explosion broke within 12 blocks in the last 5 minutes rebuilds as a wave, its dropped debris drawn back in. Once a minute. 30 Focus. |

- **Focus** (meter, starts full): every touch spends it; it refills 6/s a second after the last spend (12/s gloved).
  Spent to nothing: 2.5 s with no moves but the gloves.
- Every block goes through the Geokinesis temporary-block ledger (`GeoTerrain`): the terrain-damage config, build rights,
  claims, never into a living thing, always put back on its own clock and on server stop. Restore's memory is
  `OverhaulRestore` (recorded only while an Overhaul holder is in that level; block entities are never rebuilt).
- Dropped from the first draft by the slot rule: Decontaminate (Shift+C) and a separate Restore key (folded into
  Reassemble).

## Dark Shadow (Fumikage Tokoyami, Tsukuyomi): a living shadow companion
User rule (all Quirks): **X = movement, V = the mode / utility, R G Z C (and their Shifts) = mainly attacks.**
- **The shadow:** Dark Shadow is a visible shadow-bird spirit growing out of your back: a GeckoLib model (a long
  serpentine body, a teardrop hooded head with a pointed beak and two big glowing almond eyes, long thin arms with big
  taloned hands, a frayed smoky tail). Every player sees it (it is drawn from the synced Quirk state).
- **Light and darkness:** its size and strength follow the light at it (the brighter of the block and sky light, a light
  source held in either hand, a flare). Daylight: small, timid and weak (x0.4). Night: big and aggressive. Pitch dark
  (light 3 or less): the meter climbs toward Berserk.
- **Shadow meter:** rises in the pitch dark (fast) and in dim light (slowly, to 85 at most), falls in bright light, and
  every move pushes it up. At 100 it goes **berserk**: huge, spiked, red-eyed, attacking everything near you, **you
  included** (its hits on you never take you below 2 HP), until light returns. Light on it calms it: a light source held,
  daylight, the flare.
- **R / Shift+R:** Shadow Strike (a long-reach lunging claw strike; in Black Ankh, a clawed dash instead) / Dark Fang (a
  lunging rapid multi-bite).
- **G / Shift+G:** Shadow Grab (it wraps a mob in shadow, lifts it and slams it) / Shadow Shield (hold: it wraps round you
  and soaks hits from every side).
- **X / Shift+X (movement):** Abyssal Black Ankh (armour plus great shadow wings: elytra-style gliding flight, boosted in
  the dark) / Fetch (it carries you to the point you look at, or snatches the items there for you).
- **Z / Shift+Z:** Shadow Scout (it flies out along your look and marks every mob near its path with Glowing) / Sunshine
  Ankh (a burst of shadow feathers all round you).
- **V / Shift+V (mode):** Black Ankh (armour mode on / off: it coats you -- bird-skull helm, clawed gauntlets, shoulder
  plumes; armour, claws, reach, and the meter rises at half speed) / Recall (tuck it away: no moves, the meter drains
  fast, it can't go berserk; Shift+V again calls it back).
- **C / Shift+C:** Berserk on purpose (only in darkness; it stops only when light falls on it) / Light Flare (needs a
  light source in hand or daylight: a flash that blinds nearby mobs, calms it and ends Berserk; works while berserk).
- **N (user-assigned, v0.16.7 fix): hide / show.** N toggles the Recall state: it flinches, folds its arms in, ducks
  its head and pours back into your back (a 0.6 s `recall` clip with shadow streaming into you), then isn't drawn for
  anyone (synced); no move works but the Light Flare, the meter drains (0.75/tick) and it can't go berserk. N again and
  it boils back out (`summon`). A berserk shadow won't hide (only light calms it); hiding waits out the 1 s Recall
  cooldown, coming out never waits; Erased, N does nothing. **Decision:** Shift+V stays Recall (the same toggle; V is
  the mode key and hiding is a mode), so the slot didn't change and no new move was invented. The move is now named
  "Hide" in the HUD / guide; the HUD shows "N: hide it" while it's out and "Hidden: N calls it out" while hidden.
  Plain N only (Shift+N keeps its other owners). Wiring: `DarkShadowHidePayload` (C2S) to `DarkShadowMoves.toggleHidden`.
- **Arms (v0.16.7 fix, user: "dark shadows hands are in his face"):** the arms rested reaching forward, which put both
  clawed hands beside Tokoyami's face. They now hang down from the shoulders, a little back and out (`ARM_REST` /
  `FOREARM_REST` in `scratchpad/gen_dark_shadow_v0167.js`). The reaching clips (strike, bite, grab, slam, nova, scout,
  fetch, summon) are re-based by `keep()` so their poses are unchanged and they settle back to the hanging rest; timid
  clutches its own chest, shield wraps low round his waist, calm hugs itself and turns its head away (no hands over its
  face), and berserk rakes overhead and out to the sides. The generator's preview now renders each pose next to a
  player stand-in placed the way `DarkShadowRender` places it (`ds_user_*.png`).

## Erasure (Shota Aizawa, Eraser Head): the capture weapon plus the stare
As built (v0.16.7). Slot rule: X = movement, V = utility, the rest attacks.

| Key | Tap | Shift |
|---|---|---|
| R | **Capture Bind**: the scarf whips round the target and ties it up: rooted 4 s (players: the softened crowd-control rule). Not bosses. | **Capture Pull**: yank the bound target (or the looked-at one) in to you, 3 damage. |
| G | **Close Combat**: a four-strike combo -- jab, elbow, knee, spinning kick (3, 3, 4, 7), re-aimed each strike, the kick knocks back. | **Counter**: a 0.6 s parry stance; the next hit is caught (0 damage) and a close attacker takes 8 and is tied up 2 s. |
| X | **Scarf Swing** (hold, movement): latch a block and swing like a grapple (Spider-Man's rope model); let go to fly on. | **Scarf Pull** (movement): yank yourself to the latched point -- up the swing line mid-swing, or to the block / creature you look at (4 on arrival at a creature). |
| Z | **Bind and Spin**: the bound (or looked-at) target is swung round you twice on the scarf (1.5 s, radius 3), smashing everyone it passes (6 each), then flung the way you look (6). Your whole body turns with it. Not bosses. | **Disarm Throw**: the scarf rips the held item out of a mob's (or a PvP player's) hand and throws it back to your feet. |
| C | **Scarf Whip**: a cracking lash, 10 blocks: 5 damage and a stagger. | **Goggles** (toggle): night vision, powered players / special mobs glow (only for you), the red eyes are hidden, the stare locks on (a held target can't dodge out of it inside 45 degrees) and your eyes dry 25% slower. |
| V | **Erasure Stare** (hold, utility): the one you look at (24 blocks, narrow cone) has its powers switched off while you hold; letting go is the blink. Hair rises, eyes glow red. | **Eye Drops**: uses an Eye Drops item from your inventory: Dry Eyes to 0, shut eyes open. |

- **Dry Eyes** (meter, 0 = fresh): fills 10/s while staring (7.5/s goggled), drains 12/s otherwise. At 100 the eyes are
  **forced shut**: the stare ends and can't restart until they recover (at least 3 s and back to 30); every other move
  still works. Screen: the lids close to a sliver, a red rim while staring. Old saves (the meter used to count down)
  restart fresh.
- **Eye Drops** item (`projecthero:eye_drops`): glass bottle + sugar + kelp = 2. Right-click works too (non-Erasure
  players: clears Blindness / Darkness).
- **What the stare switches off** (`PowerSuppression`): every power key and action payload, hero / Thor / Kryptonian /
  Nova / Green Lantern / Max Steel flight (each stopped through its own stop on the first erased tick), wall-crawling
  (Spider adhesion), mutation passives, the Quirk kit's tick; on mobs ranged / swell / spell / beam goals, spawned
  projectiles and summons, creeper explosions, enderman / shulker teleports, bosses' special moves. Tech (Iron Man, the
  Punisher) keeps working.
- Dropped from the first draft by the slot rule: Erase Glare, Capture Hoist, Bind Slam and Double Bind.

## Explosion (Katsuki Bakugo, Ground Zero): nitroglycerin sweat
Built version. User rule: X = movement, V = utility, and every other key is an attack.

| Key | Tap | Shift |
|---|---|---|
| **X** (movement) | **Explosion Propulsion** (hold to fly): W/S along the look, A/D strafe, Space/Sneak up/down, sprint for faster blasts | **Explosion Dash**: one back-blast throws you about 12 blocks |
| **V** (utility) | **Stun Grenade**: blinds everything within 8 blocks for 5 s, slows it and makes it lose its target | **Cool Off**: -40 Sweat, -50 Arm Strain. Works while strained |
| **R** | **AP Shot**: a piercing palm blast, 10, 32 blocks | **AP Shot: Auto-Cannon**: 8 rapid shots, 4 each |
| **G** | **Explosive Punch**: lunge in, point-blank blast, 14, heavy knockback | **Blast Rush**: 6 blasts march down a line along the ground, 8 each |
| **Z** | **Cluster**: two rings of blasts round you, 9 | **Howitzer Impact: Cluster**: a quick spinning dive (14 ticks), 18; needs 20 Sweat |
| **C** | **Howitzer Impact** (ultimate): blast up spinning for 8 ticks, then corkscrew dive at the target and detonate, 30 in a radius of about 6 (BIG, may break soft blocks); needs 30 Sweat and spends 70% | **Gauntlet Grenade**: pull the pin (0.7 s), then all your Sweat goes down the aim in one cannon line, 28; needs 50 Sweat; big recoil |

- **Sweat** builds while you move, fight and get hot.
  - It builds 1.75x faster in deserts, 2x in the Nether and 2x on fire, and 0.6x in the cold. Water and rain wash it off.
  - Blast power runs from 0.6x when dry to 1.4x when soaked.
  - Propulsion burns 0.3 a tick. Flying sweats back 0.14 a tick, so the net cost is about 30 s of flight from full. At
    0 the blasts sputter out.
  - The HUD marks the Howitzer (30) and Gauntlet (50) thresholds on the bar.
- **Overload (Arm Strain)**: every blast adds strain. At full strain you get 5 s of Weakness II and no moves. The HUD's
  Arms gauge shows the strain.
- **Terrain**: blasts go through `ExplosionBlast.grief`, which needs both the terrain-damage config and mobGriefing.
  Only a few soft blocks break, never a block entity.
- **Visuals**:
  - Every move has a keyframed pose. The Gauntlet pose has the pin pull, the aim and the recoil.
  - Howitzer Impact spins the whole body about the vertical while rising, then turns head-first along the dive and
    corkscrews (`QuirkBodyTurnMixin`).
- Dropped from the first draft: Explosion Uppercut, Smoke Blast, Max Shot and Explosive Counter. Max Shot became the
  Gauntlet Grenade.

## Zero Gravity (Ochaco Uraraka, Uravity): touch to float, release to drop
- **Nausea** is the meter. Floating your own body and heavy loads fill it fast; at the top you're sick (Nausea, no moves).
- Slot rule (user, later in v0.16.7, for every Quirk): **X = movement**, **V = utility**, R / G / Z / C = attacks. Touch /
  Release read best as the utility pair (they set up and finish every other move), so they moved to V.
- **Meteor Shower (R):** float a field of rubble, ripped-up blocks, into a cloud overhead; Release (Shift+V, or R again)
  rains it on the area she aims at. Her Sports Festival finisher.
- **Debris Shield (Shift+R):** orbiting floated blocks that block projectiles.
- **Comet Toss (G):** throw a floated thing hard.
- **Zero Gravity Slam (Shift+G):** float a mob up and release it at height.
- **Float Self (X):** toggle. You drift and glide with low gravity, filling nausea (the movement key).
- **Zero-G Leap (Shift+X):** a huge, nearly weightless leap; no fall damage.
- **Gunhead Martial Arts (Z):** a grapple-throw combo.
- **Zero Gravity Field (Shift+Z):** a short area where everything floats.
- **Zero Gravity Touch (V):** touch a mob, an item or a block, and it floats (the utility key).
- **Release (Shift+V):** fingertips together; everything you floated drops, taking fall damage from the height.
- **Catch (C):** float anyone falling nearby (rescue), preventing fall damage.
- **Settle (Shift+C):** release everything at once safely, with no fall damage, then vent nausea while grounded. Works while
  sick. (The doc's separate "Shift+C safe release" and "Settle" are one move: twelve slots, thirteen ideas.)

## Build notes
- Keep each Quirk's package, id, serum and saves. Migrate old configs through the VersionedConfig.
- Moves that place blocks (spikes, walls, pits) use the mod's existing temporary-block / `ConjuredStructures` path and
  obey the terrain-damage config and gamerules.
- Dark Shadow's creature needs a real model: GeckoLib, a bird-like shadow made of dark purple-black cubes with glowing
  eyes, scaling with darkness.
