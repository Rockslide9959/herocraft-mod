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

## Engine (Tenya Iida, Ingenium): engines in the calves, built around RUNNING
- **Gears (the core mechanic).**
  - **R** shifts up a gear and **Shift+R** shifts down: 0, 1, 2, 3.
  - Each gear is a sustained sprint speed that builds momentum (Gear 1 ≈ Speed III, 2 ≈ V, 3 ≈ VII) with exhaust trails
    from the calves and engine sound rising in pitch.
  - Higher gears heat the engine faster.
  - Turning sharply at speed slides. Running into mobs at Gear 2+ bowls them over.
- **Recipro Burst (G):** 10 s of Recipro, double the current gear speed, with blue exhaust flames.
- **Recipro Extend (Shift+G):** a longer, faster burst (Iida's upgrade). When it ends the engines **stall**: 6 s of
  Slowness III, as in the anime.
- **Recipro Turbo (V):** the ultimate. A blinding straight-line rush that strikes everything along the path. It overheats
  the engine to 100.
- **Kicks (X / Shift+X):**
  - X is **Recipro Kick**, whose damage scales with your current speed.
  - Shift+X is **Spin Kick**.
- **Mobility (Z / Shift+Z):**
  - Z is **Wall Run / Engine Jump**: run up walls while in gear, and a high engine-boosted jump.
  - Shift+Z is the **Rescue Carry** sprint.
- **Cool Down (C / Shift+C):**
  - C is **Radiator Vent**: vents heat, steaming.
  - Shift+C is **Cool Down**, the full stop.
- The HUD shows the gear (0–3), a speed readout and engine heat.

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
- **Sweat:** builds up as you fight, move and get hot. More sweat means bigger blasts; deserts and Nether are faster,
  water and cold are slower.
- **Explosion Propulsion (R, hold):** sustained palm-blast flight/boost in the look direction. This is Bakugo's core
  movement: you fly with explosions.
- **Explosion Dash (Shift+R):** a quick blast dash.
- **AP Shot (G):** a focused piercing blast.
- **AP Shot: Auto-Cannon (Shift+G):** a rapid stream.
- **Stun Grenade (X):** a blinding flash.
- **Cluster (Shift+X):** a ring of blasts round you.
- **Howitzer Impact (V):** a spinning dive into a massive explosion; Bakugo's ultimate.
- **Howitzer Impact: Cluster (Shift+V):** a smaller, faster version.
- **Explosive Punch (Z):** a punch with a blast.
- **Blast Rush (Shift+Z):** a chain of blasts down a line.
- **Gauntlet Grenade (C):** fires all stored sweat in one cannon blast. It needs a lot of sweat and has recoil.
- **Shift+C:** cools off, drying the sweat to lower overload risk.
- **Overload:** too much at once hurts your arms (Weakness), like the anime's arm strain.

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
