# Quirk revamp (v0.16.7, user request 2026-10-22)

The user: "revamp the other quirks in the mod ... right now the quirks are not good at all, engine should have an ability
where they engage the engines to run faster and not just like 2 short dash moves, look at references and the anime to
decide the abilities".

## Rules for every Quirk
- Every key R / G / X / Z / V / C has a **tap** move and a **Shift** move. H stays the power selector. N is not used.
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
- **Fire jets:**
  - **R** is **Flashfire Fist: Jet Burn**, a fire-boosted punch dash.
  - **Shift+R** is **Fire Jet Flight**, a sustained flame-propelled hover/flight.
- **Hell Spider (G):** five thin cutting fire strings sweep out from the fingers.
- **Hell Spider: Mesh (Shift+G):** a fire-string net that traps and burns.
- **Flashfire Fist: Prominence Burn (V):** a huge sustained fire beam that ramps body heat fast.
- **Vanishing Fist (Shift+V):** a flame decoy, then a blink-strike from behind.
- **Fire Wall (X):** a wall of fire.
- **Flame Cloak (Shift+X):** a burning aura that damages anything nearby.
- **Hellflame Barrage (Z):** rapid fire jabs.
- **Fire Pillar (Shift+Z):** a pillar of fire under a target.
- **Cool down (C):** vent body heat.
- **Plus Ultra: Full Power (Shift+C):** for 15 s all fire moves are doubled; afterwards heat is pinned at max.

## Overhaul (Kai Chisaki): disassemble and reassemble by touch
- **Focus** is the meter; every touch costs focus.
- **Disassemble (R):** a touch that deals heavy damage, plus disassemble particles.
- **Reassemble (Shift+R):** instantly heal yourself or an ally to full, once per 60 s.
- **Ground Spikes (G):** touch the floor and the terrain erupts in a line of stone spikes toward the target. The spikes
  are temporary blocks and clean themselves up.
- **Spike Field (Shift+G):** spikes in a ring around you.
- **Wall Burst (X):** raise a temporary stone wall.
- **Disassemble Terrain (Shift+X):** break the floor out from under mobs, a pit that closes again later.
- **Overhaul Strike (Z):** a dash and touch.
- **Disarm (Shift+Z):** destroy a mob's held item or armour piece.
- **Chronostasis Fusion (V):** a fused form. You grow, gain stone armour and spike limbs, and get extra reach for 20 s.
- **Restore (Shift+V):** repairs blocks broken nearby.
- **Gloves (C):** puts your gloves on or takes them off. Gloves on, touches don't trigger, so you can be in melee safely.
  This is the Quirk's toggle; Shift+C cleans off contamination (vents focus).

## Dark Shadow (Fumikage Tokoyami, Tsukuyomi): a living shadow companion
- **The shadow:** Dark Shadow is a visible shadow-bird creature attached to your back, a GeckoLib or particle-built
  entity. It is weak in daylight and strong in darkness. In **pitch dark it goes berserk**: huge, strong, and attacking
  everything near you, including you, until light returns. Light falling on it (torches, day) shrinks it.
- **Shadow meter:** rises in darkness and when you push it; at the top it goes berserk.
- **Shadow Strike (R):** a beak or claw strike at range.
- **Dark Fang (Shift+R):** a lunging multi-bite.
- **Shadow Grab (G):** grab a mob and slam it.
- **Shadow Shield (Shift+G):** it wraps round you and blocks hits.
- **Black Ankh (X):** an armour mode. Dark Shadow coats you as armour: melee claws, damage reduction and a dash.
- **Abyssal Black Ankh (Shift+X):** Black Ankh plus gliding flight on shadow wings.
- **Shadow Scout (Z):** it flies out and marks mobs.
- **Fetch (Shift+Z):** it carries you or an item to it.
- **Sunshine Ankh (V):** a short shadow nova.
- **Berserk on purpose (Shift+V):** an intentional berserk that you can only stop by lighting it.
- **Recall (C):** tuck the shadow away; it can't be used, but it calms and the meter drains.
- **Shift+C:** light flare, which calms it (it needs a light source: a torch in hand or day).

## Erasure (Shota Aizawa, Eraser Head): the capture weapon plus the stare
- **Erasure Stare (R, hold):** look at a powered player or a mob, and while held its powers, abilities and special
  attacks (mob ranged attacks, Quirks, hero powers) are switched off.
  - Your eyes dry out: the **Dry Eyes** meter fills.
  - Blinking (letting go) resets the stare.
  - At max, your eyes are forced shut and you can't use the stare until they recover. Eye drops (an item) refill.
- **Capture Weapon:** the scarf.
  - **G:** whip-grab a mob or player and bind them (rooted, tied).
  - **Shift+G:** pull them to you.
  - **X:** **Scarf Swing**, latch onto a block and swing like a grapple.
  - **Shift+X:** **Scarf Pull**, yank yourself to the latched point.
  - **Z:** **Bind and Spin**, swing a bound mob into others.
  - **Shift+Z:** **Disarm Throw**, rip a held item away.
- **Combat:**
  - **V:** **Close Combat**, a fast martial-arts combo.
  - **Shift+V:** **Counter**, a parry window.
- **Goggles:**
  - **C:** put the goggles on or off (hides where you're looking; the stare can't be dodged by mobs).
  - **Shift+C:** **Eye Drops**, refill the meter from an item.

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
- **Zero Gravity Touch (R):** touch a mob, an item or a block, and it floats.
- **Release (Shift+R):** fingertips together; everything you floated drops, taking fall damage from the height.
- **Float Self (G):** toggle. You drift and glide with low gravity, filling nausea.
- **Gunhead Martial Arts (Shift+G):** a grapple-throw combo.
- **Meteor Shower (X):** float a field of rubble, ripped-up blocks, then Release rains it on a target area. Her
  Sports Festival finisher.
- **Debris Shield (Shift+X):** orbiting floated blocks that block projectiles.
- **Comet Toss (Z):** throw a floated thing hard.
- **Zero Gravity Slam (Shift+Z):** float a mob up and release it at height.
- **Catch (V):** float anyone falling nearby (rescue), preventing fall damage.
- **Zero Gravity Field (Shift+V):** a short area where everything floats.
- **Settle (C):** vent nausea slowly while grounded.
- **Shift+C:** release everything at once safely, with no fall damage.

## Build notes
- Keep each Quirk's package, id, serum and saves. Migrate old configs through the VersionedConfig.
- Moves that place blocks (spikes, walls, pits) use the mod's existing temporary-block / `ConjuredStructures` path and
  obey the terrain-damage config and gamerules.
- Dark Shadow's creature needs a real model: GeckoLib, a bird-like shadow made of dark purple-black cubes with glowing
  eyes, scaling with darkness.
