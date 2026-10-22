# Iron Man Era II -- kits (v0.16.7)

Stage 3 (2026-10-22) added the **Mk IX Inferno** kit next to the v0.16.1 Mk VI / Mk VII / Mk VIII / Hulkbuster kits.
The **energy + C utility round** (2026-10-22, below) then gave every Era II suit a reactor and a six-key kit, and the
Silver Centurion its blades. The final key tables for all five suits are in "The five Era II kits (final)".

## Energy + C utility round (v0.16.7 (user), 2026-10-22)

The user: "why do the era 2 armours not have energy like the era 1 armours, all the armours should have energy levels";
"for the iron man era 2 armours shift+c is to detransform it into the suitcase or backpack or whatever, keep it that
way but move one of the utility abilities of the suit to C while giving the suits another attack ability"; and "the
silver centurion armour should have a blade ability where it deploys blades to use".

### Energy: the Era I rules, an Arc Reactor Mk2 per suit

| Suit | id | Capacity | Regen /s | Flight /s (sprint) | Rack charge /s |
|---|---|---|---|---|---|
| Mk VI Suitcase | `mark_2` | 1400 | 2.5 | 2 (4) | 25 (50 by a Stark Gantry) |
| Mk VII Silver Centurion | `mark_6` | 1800 | 2.5 | 2 (4) | 25 (50) |
| Mk VIII Space | `mark_vii` | 1600 | 2.5 | 2 (4) | 25 (50) |
| Mk IX Inferno | `mark_9` | 1700 | 2.5 | 2 (4) | 25 (50) |
| Hulkbuster I | `mark_8` | 2500 | 2.5 | 2 (no sprint flight) | 25 (50) |

* `IronManEraOne.ERA_TWO` / `hasReactor(id)` (= Era I or Era II) gate the energy rules; `IronManSuit.hasReactor()`;
  `IronManEnergy.real()` is true for both eras. The Gantry-only rules (suit up / off only in a Gantry, the Shift+C
  eject) stay Era I's (`eraOne()`).
* Every move pays its kit cost when it goes off (`IronManComicKit.en` / `enPerSecond`, router `IronManEraOne.canPay`
  / `pay` / `drainTick`). Not enough = "LOW POWER", the move does not fire.
* **Brownout** at 0: every move refused, flight lands and will not restart, a held stream / the blades stop, until 10% is
  back (armour and absorb still work). Same code as Era I (`IronManEnergy.setEnergy` -> `onBrownout`).
* Regen 2.5/s worn; standing on a Stark Gantry floor +50/s; racked on any Suit Platform +25/s
  (`IronManEnergy.ERA_TWO_PLATFORM_CHARGE_PER_SECOND`), +50/s in range of a complete Gantry. Racks still repair 5/s.
* **Stored suits keep the charge:** `IronManEnergy.stampStack` now also resolves a packed form's suit
  (`PortableForms.byItem`), so the briefcase / backpack / thruster pack / heat case / Hulkbuster Uplink carry the
  charge and a deploy restores it. Loose-piece packing (`IronManArmorItem.packLoose`) and the Hulkbuster crate's trip to
  orbit (`HulkbusterCrate.toOrbit`) stamp the emptiest piece's charge (they used to stamp 0). The sentry
  (`IronManSentryEntity`) keeps the suit's real charge in a separate `reactor` field (NBT `Reactor`) -- its own
  `energy` stays the always-full value its modes run on -- and hands it back on step-in / to the stacks.
* **Mk IX:** the reactor plus the heat sink as a second meter. The Inferno Stream drains 4/s while held; a dry reactor
  chokes it like a full sink.
* **HUD / screens:** the top-right status block shows the REACTOR gauge (LOW POWER / BROWNOUT / regen / Gantry) for
  every reactor suit; the suit list (Call Armour cards), the Stark Gantry menu, the Suit Platform screen, the spec sheet
  (I) and the comic stats show REACTOR / energy for Era II instead of ABSORB (absorb stays in the gauge's right-hand note
  and the comic stats).

### C: a utility moved off the kit, plus a new attack in the slot it left

* **Shift+C (hold) is unchanged: the fold** into the portable form (`IronManAbilityManager` checks it before any C
  move). The C box shows the packed item and "FOLD" while Shift is held.
* **Plain C (tap)** is now a kit key (`IronManComicKit.SLOT_C`, `kit6`), with a utility from the suit's own kit; the
  slot it vacated holds a new attack.

| Suit | C (tap) | Moved from | New attack (slot) |
|---|---|---|---|
| Mk VI | **Mob Highlight** (toggle) | V tap | **Wrist Laser** (V tap) |
| Mk VII | **Energy Shield**; **hold 1 s: Overload Burst** | V tap / Shift+V | **Blade Whirl** (Shift+V), with **Deploy Blades** on V tap |
| Mk VIII | **Zero-G Field** | Shift+V | **Singularity** (Shift+V) |
| Mk IX | **Thermal Siphon** | Shift+V | **Molten Lash** (Shift+V) |
| Hulkbuster | **Hydraulic Brace**; **hold 1 s: Sentry Mode** | V tap | **Palm Cannon** (V tap) |

What happened to the old plain-C behaviours:
* **Mk VI / Mk IX** (the fold hint): gone -- the C box and the move list say "Shift (hold): fold".
* **Mk VII / Mk VIII** (store to the inventory, "the pod / bracelets" store): plain C no longer takes the suit off. Hold
  Shift+C folds it into its backpack / thruster pack (C with no suit on deploys it again), the Stark Gantry menu (H on
  the floor) still has Remove / Swap, and every call path is untouched: the Colantotte Bracelets' plain-C call (unsuited)
  and the orbital pod / Stark Glasses picker work exactly as before.
* **Hulkbuster** (Sentry Mode): kept on C as the **hold** (1 s). A tap is the Hydraulic Brace. Sentry Mode ignores the
  brace cooldown and works on a dead reactor (it is how you get out). `IronManMark8.trigger` still exists (tests, old
  binds).
* **Mk VII** C uses the same tap / hold split: tap = Energy Shield, hold = Overload Burst (both kept, each with its own
  cooldown and cost, `IronManComicMoves.shieldOrBurst`), because V became the blades.

### The Silver Centurion's blades (`IronManCenturionBlades`, client `CenturionBladeLayer`)

* **V tap -- Deploy Blades** (mode toggle): 10 energy to deploy, then 1/s while out (`IronManBlade.tick`); V again
  retracts (free). A dry reactor, a brownout, 0 integrity or taking the suit off pulls them in.
* While out: melee **+6** (the shared `IRON_MAN_BLADES` modifier) and every connected swing **sweeps**: enemies within
  2.5 blocks of the target take 6 (silver sweep mark + sparks + a blade ring).
* **Shift+V -- Blade Whirl** (16 x 2, cd 7 s, 25 energy): snaps the blades out if they are in, a short lunge, then two
  spinning slashes round you (4.5 blocks, knockback, the second harder), a ring of sweep marks and sparks each time.
* **Look:** a silver housing with a red stripe on the outside of each gauntlet; a thin silver blade plate with a red
  spine slides out along the forearm and past the fist (eased over `IronManBladeLook.EXTEND_TICKS`), tapering to a
  point, with a soft additive cyan-white glow along the cutting edge that fades in over the last third of the slide and
  shimmers. Drawn in arm space for every viewer (follows every pose) and on the first-person arm
  (`PlayerRendererHandMixin`). SFX: the blade extend / ring / retract sounds.
* **Poses:** `COMIC_BLADE_DEPLOY` (forearms snapped up, fists clenched, then flicked out to the sides as the blades
  slide) and `COMIC_BLADE_WHIRL` (a low lunge with the blades drawn back, then two scything twists, arms flung level).

### The new attacks

| Move | Numbers | Pose / VFX / SFX |
|---|---|---|
| Mk VI **Wrist Laser** `mk2_wrist_laser` | 18 + 2 s fire to up to 3 enemies in a 32-block line; cd 5, en 15 | `COMIC_LASER`: right arm locked out, left hand clamped on the wrist, a small kick; thin hot-red beam (kind 13), flame + sparks on each hit, red dust at the end; repulsor + high beacon whine |
| Mk VII **Blade Whirl** `mk6_blade_whirl` | see above | see above |
| Mk VIII **Singularity** `mk7_singularity` | thrown up to 24 blocks (the lock first); pulls enemies within 7 to its core for 1.6 s (not bosses), then implodes for 22 in 4.5; cd 14, en 35 | `COMIC_SINGULARITY`: orb cupped high, overhand pitch; a reverse-portal streak along the arc, a shrinking reverse-portal ring + dark core while it pulls, flash + sonic boom + end-rod burst on the implosion; ender-eye launch, beacon hum, explosion |
| Mk IX **Molten Lash** `mk9_molten_lash` | 22 (x1.25 Hot Plating) + 5 s fire in a 7-block, 120-degree arc; enemies yanked toward you; heat +6; cd 5, en 20 | `MK9_LASH`: arm cocked high behind, whipped across the body; lava drips off the wrist, then a flame / lava arc across the front; sweep + blaze + fire-charge |
| Hulkbuster **Palm Cannon** `mk8_palm_cannon` | both palms, one blast each: 22 + a 3-block splash of 10; cd 9, en 35 | new GeckoLib clip `palm_cannon` (`HB_PALM_CANNON`, both palms thrust out, fire at 0.6 s, recoil back, settle); sparks gathering at the palms, two fat white-gold beams (kind 15), explosions at the ends, a small recoil shove |

## The five Era II kits (final)

Damage per hit; cd = cooldown s; en = energy (one-off) or en/s (held / toggled). Shift+C (hold) = fold, on every suit.

### Mk VI Suitcase (`mark_2`)
| Key | Tap | Shift |
|---|---|---|
| R | Repulsor (hold = charged) 11 / 20; en 5 | Dash 16; cd 8, en 15 |
| G | Tractor Beam (hold) 8; cd 9, en 10 | Pressor Blast 10; cd 7, en 10 |
| X | Flares 5; cd 10, en 10 | Supersonic; cd 25, en 20 |
| Z | Unibeam 26; cd 12, en 35 | Unibeam Flash 8; cd 10, en 20 |
| V | **Wrist Laser** 18; cd 5, en 15 | JARVIS Scan; cd 15, en 10 |
| C | **Mob Highlight**; cd 1, en 2 | (fold) |

### Mk VII Silver Centurion (`mark_6`)
| Key | Tap | Shift |
|---|---|---|
| R | Repulsor 17; cd 0.5, en 5 | Dash 22; cd 7, en 15 |
| G | Pulse Bolts 10; cd 1.2, en 8 | Pulse Storm 8; cd 12, en 30 |
| X | Ricochet 20; cd 6, en 15 | Chameleon; cd 20, en 20 |
| Z | Heavy Unibeam 40; cd 12, en 40 | Unibeam Overcharge 75; cd 25, en 50 |
| V | **Deploy Blades** (toggle); cd 1, en 10 + 1/s | **Blade Whirl** 16 x 2; cd 7, en 25 |
| C | **Energy Shield** (80%, 6 s); cd 15, en 25 -- **hold 1 s: Overload Burst** 30 in 8; cd 14, en 30 | (fold) |

### Mk VIII Space (`mark_vii`)
| Key | Tap | Shift |
|---|---|---|
| R | Repulsor 20; cd 0.5, en 5 | Dash 25; cd 7, en 15 |
| G | Vacuum Missiles 20; cd 10, en 25 | Orbital Strike 18; cd 22, en 45 |
| X | **Flight Boost** (toggle: x2 speed while sprint-flying, off when you stop; flight drain x2.5); cd 1, en 0 | Meteor Dive 15; cd 10, en 20 |
| Z | Solar Beam 30; cd 10, en 30 | Solar Flare 22; cd 16, en 30 |
| V | Gravity Tether 6; cd 12, en 15 | **Singularity** 22; cd 14, en 35 |
| C | **Zero-G Field**; cd 20, en 20 | (fold) |

### Mk IX Inferno (`mark_9`)
| Key | Tap | Shift |
|---|---|---|
| R | Fire Blast 21; cd 0.6, en 5 | Inferno Stream (hold) 9 / 0.5 s; cd 4, 4 en/s |
| G | Magma Fist 26; cd 1.5, en 10 | Eruption 30; cd 12, en 35 |
| X | Afterburner 24; cd 7, en 15 | Lava Geyser 16; cd 8, en 15 |
| Z | Magma Bomb 24; cd 10, en 25 | Firestorm 16; cd 18, en 40 |
| V | Heat Dump up to 46; cd 6, en 20 | **Molten Lash** 22; cd 5, en 20 |
| C | **Thermal Siphon** 6; cd 10, en 10 | (fold) |

### Hulkbuster I (`mark_8`)
| Key | Tap | Shift |
|---|---|---|
| R | Punch Combo 26; cd 2, en 10 | Rocket Fist 45; cd 8, en 30 |
| G | Pile Driver 40; cd 10, en 30 | Earthquake Stomp 32; cd 14, en 40 |
| X | Grab + Pound 20; cd 12, en 20 | Grab + Throw 18; cd 10, en 20 |
| Z | Chest Mega-Beam 18 / pulse; cd 20, en 50 | Missile Swarm 18; cd 18, en 45 |
| V | **Palm Cannon** 22 x 2; cd 9, en 35 | Replacement Pod; cd 180, free (works wrecked / browned out) |
| C | **Hydraulic Brace** (70%, 5 s); cd 25, en 15 -- **hold 1 s: Sentry Mode** (free) | (fold) |

The house rule says X = movement; the v0.16.1 kits keep some attacks on X (Ricochet, the Hulkbuster grabs, Flares);
this round did not move them (the user asked only for C).

### Tests

`gametest/IronManEraTwoEnergyGameTests` (12): caps / regen / rack charge, every move priced on the Era I scale, a cost
paid + LOW POWER + brownout + recovery at 10%, flight 2/s and 4/s sprinting, the folded briefcase keeps the charge,
every C move + the new attack slots, the Wrist Laser, the Singularity implosion, the Molten Lash, the Palm Cannon +
the Hulkbuster's C tap (brace, cost, cooldown) / hold (Sentry Mode), the Centurion's C tap / hold, and the blades
(deploy cost, 1/s drain, +6 melee, sweep, free retract, the whirl deploying them, the brownout pulling them in).
Updated: `IronManComicV0161GameTests` (six-key kits, every suit has a reactor), `IronManMk9KitGameTests`,
`IronManMk9InfernoGameTests`, `IronManMk67V0154GameTests`, `IronManMark8V0159GameTests`, `IronManSentryV0159GameTests`
(Sentry via the C hold), `IronManSentryV01515GameTests`, `DirectionalFlightGameTests`, `IronManBackpackFormGameTests`,
the portable-form / heat case / crate / fold-polish helpers (packs stamped with a full reactor), `HeroPackGameTests`,
`IronManUiV01421GameTests`.

### Design choices (made without the user)

* V is the utility key, so each suit's C is a utility from V (or Shift+V); the new attack goes where it left. Mk VII is
  the exception the blades forced: V became the blade mode + blade attack (the new attack), and both of V's old moves
  went to C as tap / hold.
* Hold-C on the Hulkbuster and the Mk VII (1 s, `SENTRY_HOLD_TICKS`; the C box fills while held) keeps every existing
  move reachable without inventing an H / N bind.
* Costs follow the approved Era I scale (5-50 per move, single digits per second); the big reactors (1400-2500) last a
  long mission at 2.5/s regen.

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
| C | (v0.16.7 (user) energy + C round: **Thermal Siphon** moved here from Shift+V, and Shift+V is the new **Molten Lash**; hold Shift+C: fold into the Heat Case) | |

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

## The v0.16.1 Era II kits before the energy + C round (history)

Plain C / Shift+C on Era II: Shift+C (hold) folds the suit into its portable form on every Era II suit
(`IronManAbilityManager`, checked before any C move); C with no suit on deploys a carried form. Plain C while worn:
Mk VI + Mk IX (hand forms) show the fold hint; Mk VII + Mk VIII (back packs) keep their store (platform / pod /
bracelets); the Hulkbuster keeps Sentry Mode.

| Suit | R | G | X | Z | V |
|---|---|---|---|---|---|
| Mk VI Suitcase (`mark_2`) | Repulsor (hold = charged) / Dash | Tractor Beam / Pressor Blast | Flares / Supersonic | Unibeam / Unibeam Flash | Highlight / JARVIS Scan |
| Mk VII Silver Centurion (`mark_6`) | Repulsor / Dash | Pulse Bolts / Pulse Storm | Ricochet / Chameleon | Heavy Unibeam / Unibeam Overcharge | Energy Shield / Overload Burst |
| Mk VIII Space (`mark_vii`) | Repulsor / Dash | Vacuum Missiles / Orbital Strike | Flight Boost / Meteor Dive | Solar Beam / Solar Flare | Gravity Tether / Zero-G Field |
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
