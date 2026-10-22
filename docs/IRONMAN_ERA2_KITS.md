# Iron Man Era II -- kits (v0.16.7, stage 3)

Scope (the lead's correction, 2026-10-22): the Mk VI / Mk VII / Mk VIII / Hulkbuster already had full R / G / X / Z / V
tap + Shift kits from v0.16.1 (`IronManComicKit`, `IronManComicMoves`). They stay exactly as they are, no energy (the
v0.16.1 shim). Stage 3 adds only the **Mk IX Inferno** (`mark_9`) kit, in the same `kit(...)` form, with no energy, on
the same damage / cooldown scale. The existing kits are listed at the end for reference.

## Mk IX Inferno (`mark_9`, ids `mk9_*`): the Nether suit

Code: `ironman/ability/IronManMark9.java` (moves, heat sink, held stream, magma pools, server tick). Client:
`client/ironman/IronManMk9Client.java` (lava swimming, lava visor). Damage is per hit; cd = cooldown in seconds; heat =
what the move puts into the heat sink.

| Key | Tap | Shift |
|---|---|---|
| R | **Fire Blast** (`mk9_fire_blast`): an orange plasma bolt, 21 + 4 s fire, a 2-block splash of 8 + fire. cd 0.6, heat +3 | **Inferno Stream** (`mk9_inferno_stream`, hold): a flame cone 8 blocks long, 9 every 0.5 s + fire. heat +10/s; cd 4 after you let go; stops when the sink is full |
| G | **Magma Fist** (`mk9_magma_fist`): a red-hot punch 4 blocks out, 26 + 5 s fire + big knockback. cd 1.5, heat +3 | **Eruption** (`mk9_eruption`): both fists into the ground: 30 in 5 blocks, mobs thrown up and set burning, a ring of fire and lava. cd 12, heat +12 |
| X | **Afterburner** (`mk9_afterburner`): a jet dash (the shared dash, 24 to what you pass through) that leaves a fire trail. cd 7, heat +6 | **Lava Geyser** (`mk9_lava_geyser`): rocket straight up (about 10 blocks, no fall damage after) on a column of flame: 16 + fire to everything within 3 below you. cd 8, heat +6 |
| Z | **Magma Bomb** (`mk9_magma_bomb`): a lobbed glob of magma. On impact 24 in 3.5 blocks and a magma pool for 5 s (4 every 0.5 s + fire, Slowness). cd 10, heat +8 | **Firestorm** (`mk9_firestorm`): 8 incendiary micro-missiles from the shoulders, 16 each, every blast sets things burning. cd 18, heat +10 |
| V | **Heat Dump** (`mk9_heat_dump`): vents the whole heat sink as a fire nova. Radius 4 + heat x 0.08 (up to 12), damage 10 + heat x 0.36 (up to 46) + fire + knockback. Needs 20 heat; empties the sink. cd 6 | **Thermal Siphon** (`mk9_thermal_siphon`): draws the heat out of everything within 8 blocks: each burning mob, fire block, lava block (up to 12 sources) puts +5 into the sink (max +40), fire blocks go out, burning mobs are put out and take 6. The suit repairs 15 integrity per source. Nothing hot nearby: refused, no cooldown. cd 10 |
| C | (unchanged, as on its Era II siblings) plain C: the fold hint; hold Shift+C: fold into the Heat Case | |

### Heat sink (0-100)

* Synced to the wearer (`ModAttachments.IRON_MAN_HEAT_SINK`), shown in the HUD as the **HEAT SINK** meter (top-right
  meters column) while the Mk IX is worn.
* **Fills:** +4/s in lava, +2/s in fire / on magma / a lit campfire, +1.5 per point of fire damage the suit shrugs off
  (blaze fireballs etc.), and each move's heat above.
* **Vents:** -3/s when nothing is heating it, -10/s in water or rain (steam puffs).
* **Hot Plating (50+):** every Mk IX fire move hits 25% harder (the HUD meter turns red and says HOT).
* **Saturated (100):** the moves that add heat (R, Shift+R, G, Shift+G, Z, Shift+Z) refuse, "HEAT SINK FULL -- V to vent",
  until the sink is back under 85. X / Shift+X (thrusters) and V / Shift+V always work.

### Passives

* **Lava swimming:** with the full suit on (fire / lava immune since stage 1), you swim through lava at up to about 8 blocks/s (in-client: 6 blocks in the first second from a standstill; vanilla is under 1)
  in the look direction (Space up, Shift down), with a client-side lava visor (lava fog pushed out to 24 blocks).
* Fire / lava immunity, the orange jet and flight are stage 1 and unchanged.

### Poses and VFX

| Move | Pose (`IronManAbilityFx`) | VFX / SFX |
|---|---|---|
| Fire Blast | `MK9_FIRE_BLAST`: right palm thrust, hard recoil, left hand braced back | orange beam kind 12 with a flare ring, flame + lava burst at the hit, blaze shoot |
| Inferno Stream | `FLAME` (right arm held on aim; existing flame cone drawn by `IronManAbilityVisuals`) | flame / small-flame cone particles, fire ambient loop |
| Magma Fist | `MK9_MAGMA_FIST`: wind-up then an uppercut-straight with a body twist | lava + flame burst at the fist, anvil-ish crunch + fire charge |
| Eruption | `MK9_ERUPTION`: fists raised overhead, then both slammed down, hunched | expanding orange ground ring (client) + server lava / flame ring, explosion + lava pop |
| Afterburner | `DASH` | flame trail along the path, firecharge whoosh |
| Lava Geyser | `COMIC_LAUNCH` | flame column below, large smoke, blaze shoot |
| Magma Bomb | `MK9_LOB`: underarm lob | lava drip trail on the arc, pool of flame / lava particles |
| Firestorm | `MISSILES` | red-tinted missiles + flame bursts |
| Heat Dump | `MK9_HEAT_DUMP`: crouch in, then arms and chest flung open | two expanding orange / red shock rings + a vertical vent column (client), server flame nova + one explosion flash, big fire whoosh |
| Thermal Siphon | `MK9_SIPHON`: arms spread low, palms down, drawing in | flame particles streaming from every source into the chest |

## Existing Era II kits (unchanged, for reference)

Plain C / Shift+C on Era II: Shift+C (hold) folds the suit into its portable form on every Era II suit
(`IronManAbilityManager`, checked before any C move); C with no suit on deploys a carried form. Plain C while worn:
Mk VI + Mk IX (hand forms) show the fold hint; Mk VII + Mk VIII (back packs) keep their store (platform / pod /
bracelets); the Hulkbuster keeps Sentry Mode.

| Suit | R | G | X | Z | V |
|---|---|---|---|---|---|
| Mk VI Suitcase (`mark_2`) | Repulsor (hold = charged) / Dash | Tractor Beam / Pressor Blast | Flares / Supersonic | Unibeam / Unibeam Flash | Highlight / JARVIS Scan |
| Mk VII Silver Centurion (`mark_6`) | Repulsor / Dash | Pulse Bolts / Pulse Storm | Ricochet / Chameleon | Heavy Unibeam / Unibeam Overcharge | Energy Shield / Overload Burst |
| Mk VIII Space (`mark_vii`) | Repulsor / Dash | Vacuum Missiles / Orbital Strike | Booster Rockets / Meteor Dive | Solar Beam / Solar Flare | Gravity Tether / Zero-G Field |
| Hulkbuster I (`mark_8`) | Punch Combo / Rocket Fist | Pile Driver / Earthquake Stomp | Grab + Pound / Grab + Throw | Mega Beam / Missile Swarm | Hydraulic Brace / Replacement Pod |

### Collision check with the stage 2 fold / deploy

* None of the R / G / X / Z / V moves use slot 6, so none collides with the fold or the deploy.
* The Hulkbuster's old Shift+C (the Call Armour picker in `IronManMark8.trigger`) can no longer be reached: the fold
  check in `IronManAbilityManager.handle` runs first for every suit with a portable form. That is the intended stage 2
  behaviour (Shift+C = fold), so nothing was changed; the picker is still available unsuited with the Stark Glasses.

## Tests

`gametest/IronManMk9KitGameTests` (9): kit shape / no energy / Era II scale, heat sink fill + vent + Hot Plating + sync +
fire-hit heat, saturation lock + Heat Dump nova, the 20-heat minimum, Fire Blast + splash, Inferno Stream hold / release
cooldown / choke at 100, Magma Bomb + pool + `ServerStateReset`, Thermal Siphon, UI text widths.
`IronManComicV0161GameTests` now expects the Mk IX kit (stage 1 asserted it was empty).

## Design choices (made without the user, 2026-10-22)

* C stays the suit toggle as on the Mk VI (the other hand-carried form): plain C = the fold hint, so the Mk IX's
  signature (Heat Dump) sits on V, as the other Era II kits keep their big moves on R-V.
* No energy (the lead's scope correction): the heat sink is the Mk IX's resource instead. It rewards fighting in
  fire / lava (Hot Plating), the dump turns it into damage, and saturation stops endless fire-move spam.
* The heat value lives on the server (`IronManMark9`) and is mirrored into a target-only synced attachment only when
  it moves by 0.5, so a held stream does not send a packet every tick.
* The Heat Dump's radius rides in its pose length, so every viewer's client draws the nova at the right size
  without a new packet.
