# Power Tiers (v0.17.7) — reference

The user's balance framework. Damage in health points (2 = one heart). Code: `hero/PowerRank.java` (ranges, colours,
badge, `ofGuideKey` placement). The Guidebook chapter builder (`HeroPackGuide.chapter`) inserts the tier line under a
placed power's "Primary Power" line, so the Guidebook and the I menu both show it. The Overview chapter has a
"Power Tiers" section listing all four.

| Tier | Melee | Abilities | Ultimate | Colour |
|---|---|---|---|---|
| Street | 7-10 | 8-10 | 15-20 | gray |
| City | 11-14 | 11-15 | 25-30 | aqua |
| Planet | 16-19 | 16-20 | 32-40 | green |
| Cosmic | 21-28 | 22-30 | 45-60 | light purple |

**Placed so far (user, 2026-11-23):** Street = Super Soldier, Punisher, Black Panther, Power Armour. Planet = Shazam (user: "hes A tier", v0.17.7). Cosmic = Thor (user, v0.17.7 Thor revamp). City (user's "B", v0.17.7) = Spider-Man, all Miraculous (Ladybug, Cat Noir, Rena Rouge, Carapace, Queen Bee, Viperion, Pegasus, Ryuko, Multimouse, Hawk Moth), Symbiote (secondary, incl. Symbiote Spider-Man), Falcon (secondary), Beast Boy, Quirks, Max Steel, Spin, Undead, Mutation (all 27), Titan Shifter.

## In the Guidebook (v0.17.7 remake)

- **Power Tiers chapter**: its own home tile (second, after Getting Started) and category (`Category.TIERS`). The chapter is the
  last in the flat list (`HeroPackGuide.powerTiersChapterIndex()`, so no earlier index moves). It lists the four tiers with ranges
  and blurbs, what a tier means, then every power under its tier as a link line (style insertion `HeroPackGuide.LINK + index`) and
  the rest under "Not yet ranked", grouped by franchise. Built from `PowerRank.ofGuideKey`, so a new `case` shows up by itself.
- **Powers by Tier**: a home tile opening every power in one list grouped by tier (`HeroPackGuide.powersByTier()`).
- **Tier pills** on every placed power in the chapter lists and search results; a **tier filter** (All / Street / City / Planet /
  Cosmic / Unranked, with counts) on every power list.
- **Tier banner**: `chapter()` marks the tier line with the insertion `HeroPackGuide.TIER_LINE + id`; the book draws it as a coloured
  ribbon under the title (click it for the Power Tiers chapter) and the I menu as a coloured ribbon (`GlassPage.band`).
- Test: `GuidebookStabilityGameTests.everyPlacedPowerIsListedUnderItsTier`.
Every other power is unplaced (no badge) until the user assigns it. Don't place powers yourself.

## Rules used for the pass

- **Per hit.** Each hit of a multi-hit move sits in the range (combo slashes count as melee hits, 7-10).
- **Buffs may exceed the base range but never the tier's ultimate max** (Street: 20). Each power has a
  `MAX_HIT` constant applied after its multipliers (Super Soldier: Assemble on the marked -- was Onslaught / Tactical Focus; Stalk, Kimoyo Scan, Target Designation,
  Kinetic Return). Boss caps still apply on top.
- **Main fighting form only.** Black Panther's unsuited (T'Challa) kit was left as it was: weaker by design. (v0.17.7: the user then set
  both kits' numbers himself, see the Black Panther v0.17.7 table below and `docs/BLACKPANTHER_REFERENCE.md`.)
- **Toughness ~ full diamond armour** (takes ~30-40% of a 10-damage hit), at most +10 max health.
- Gear anyone can use (the Punisher guns) was tuned too, since it is the Punisher's kit.

## Planet: Shazam (v0.17.7 kit, `ShazamConfig`, `MAX_HIT` 40)

A new S-H-A-Z-A-M kit with hold moves (see `docs/SHAZAM_REFERENCE.md`). Every hit whose source is a transformed Shazam passes
`ShazamCombat.enter` (Wisdom x1.25, then the 40 cap, then the boss cap: 6% of max health, never launched / lifted). Family: half.

| Hit | Before (v0.17.3 kit) | After |
|---|---|---|
| Bare fist | 11 (+10) | 17 (+16) |
| R Hercules Combo | -- (R was Lightning Hand 9, chains 9) | 17 / 17 / 19 |
| Shift+R Herculean Uppercut | -- (Call the Lightning 22) | 18 |
| Hold R Haymaker of Hercules | -- | 16 -> 20, shockwave 16 |
| G Lightning of Zeus / chain | -- (G was Hercules Punch 16 + wall 8) | 18 / 16 |
| Shift+G Thunderhead Slam | -- (Atlas Lift throw 10) | 18 |
| Hold G Zeus's Wrath | -- | 16 every 5 ticks |
| C Mercury Blitz | -- (C was Thunderclap 12) | 16 x up to 3 |
| Shift+C Thunderclap | 12 (on C) | 16 |
| Hold C Mercury Rush | -- | 16 x up to 5 |
| Z Atlas Quake | -- (Z was Zeus Storm 10 a bolt) | 19 |
| Hold Z Titan's Grip | Atlas Lift 10 on landing | 20 |
| Shift+Z ultimate | Eternity Strike 50 within 10 | "SHAZAM!" Thunderstrike 40 within 8, then 8 s chain 16 |
| Shift+X | Thunder Tackle 18 | Sonic Leap (movement) |
| V Wisdom crit | -- | x1.25, capped 40 |
| Transform bolt | 12 | 12 (unchanged, the word is not part of the kit) |

Toughness: netherite-level -- armour +20, toughness +12, knockback resistance 0.4 (was a flat 35% cut); 40 health (+20) and magic
x1.5 unchanged. Courage of Achilles is Resistance II + no knockback for 8 s (was Achilles Stand's total immunity). Flight 18 / 34 b/s
(was 22 / 44).

## Cosmic: Thor (v0.17.7 revamp, `power/ThorKit`, `MAX_HIT` 60)

Four weapon states from the hands (see `docs/THOR_REFERENCE.md`). Every move hit goes through `ThorKit.hit` (capped 60); the storm
buff (+30%) and Sunder (+20%) are capped at 60 in the hurt hook. Bosses keep their per-hit caps.

| Hit | Before | After |
|---|---|---|
| Melee (fist / Mjolnir / Stormbreaker / both) | 12 / 22 / 25 / main-hand | 24 / 24 / 26 / 27 |
| Combo finisher area (Mjolnir shock / Stormbreaker cleave) | 4 / 6 | 22 / 22 (finisher hit itself max 28) |
| Thrown Mjolnir / Stormbreaker | 18 / 20 | 22 / 24 |
| Lightning Strike / chain | 22 / 11 | 26 / 22 |
| Chain Lightning | 18 | 24 |
| Lightning Beam | 8 every tick | 22 every 5 ticks (Stormbreaker Beam 26, Twin Beam 28) |
| Thunderclap | 22 | 26 |
| Thunder Field pulse | 4 every 10 ticks | 22 every 20 |
| Twin Thunder slam / ring | 20 / 8 | 30 / 22 |
| Storm Call bolt | 8 | 22 |
| God of Thunder's Wrath | 100 | removed (four new ultimates, 45-60) |
| New ability hits | -- | 22-30 (Executioner's Drop x1.5 under 30% health, max 30) |
| Ultimates | -- | Incarnate aura 22 + burst 60; Wrath rain 45 + bolt 60; Judgement 60 + 45; Ragnarok rain 45 + 60 |

Toughness unchanged (takes 20%, +10 hearts).

## Street pass: before -> after

### Super Soldier (`SuperSoldierConfig`)
| Hit | Before | After |
|---|---|---|
| Fist bonus | +7 whatever is held (sword 15) | +7 bare-handed, +2 holding anything (`ARMED_BONUS`) |
| Combo Strike | 5 / 5 / 7 | 7 / 7 / 9 |
| Uppercut | 10 | 10 |
| Flying Kick | 12 | 10 |
| Judo Takedown | 14 | 10 |
| Leaping Slam | 12 (edge 60%) | 10 |
| Shield throw: other / Adamantium / Vibranium | 6 / 9 / 12 | 8 / 9 / 10 |
| Onslaught burst | 8 (x1.25 = 10) | 16 (x1.25 = 20); removed in the v0.17.7 rework below |
| Onslaught punch bonus / shock | +5 / 4 | +2 / 6; now Assemble's shock (6) |
| Cap | none | 20 per move hit (punches too while Assemble boosts them) |
Toughness unchanged (+10 HP, 30% less, suit 18 armour).

**v0.17.7 kit rework (after the pass):** Onslaught, Battle Cry and Tactical Focus merged into **Assemble** (Shift+V: buffs, debuffs,
marks; x1.25 on the marked, punch shock 6; no damage of its own). V is **Counter Stance** (counter 10 -- ability range). Shift+Z is two
ultimates on one 95 s cooldown: **Peak Human Barrage** (6 x 7.5 melee-range blows, then a 20 haymaker; burst 10) and **Shield Storm**
(ricochets 15 each, up to 8; star slam 20 -> 12 at 6 blocks). Ultimate hits sit in 15-20; every hit stays at or under `MAX_HIT` 20 with
Assemble on top; bosses keep the 6% cap and are never launched.

| Hit (v0.17.7) | Damage |
|---|---|
| Counter Stance counter | 10 |
| Barrage flurry blow / haymaker / burst | 7.5 x 6 / 20 / 10 |
| Shield Storm ricochet / star slam | 15 (up to 8) / 20 -> 12 |
| Assemble punch shock | 6 (7.5 on a marked enemy) |

### Black Panther, suited (`BlackPantherConfig`)
| Hit | Before | After |
|---|---|---|
| Claw Combo | 5 / 5 / 8 | 7 / 7 / 9 |
| Pounce strike | 12 | 10 |
| Kinetic Overload | 30 -> 15 at the edge | 20 -> 15 at the edge |
| Kinetic Return | max(4, 1.25x shot) + up to 10, no ceiling | max(8, 1.25x shot) + up to 10, max 20 |
| Stalk / Scan stacking | uncapped (x3 x1.25) | capped at 20 in `BlackPantherCombat.capped` |
| Suit armour / toughness | +8 / +2 | +12 / +4 |

### Black Panther v0.17.7 (user's numbers, both kits; the suit is now crafted armour)
| Hit | Before | After |
|---|---|---|
| Bare fist unsuited / suited | 3 (+2 herb) / 8 (+5 claws, N toggle) | 5 / 10 (claws always out) |
| Regeneration unsuited / suited | Herb Vitality 1 hp / 3 s | Regeneration I / II |
| Warrior's Combo unsuited | 3 / 3 / 5 | 5 / 7 / 10 |
| Warrior's Combo suited (new on R) | -- (R was Kinetic Pulse) | 8 / 9 / 10 |
| Kinetic Pulse | 8 on R | 10 on Shift+R (Overload on a full bar) |
| Leg Sweep | 4 | 7 |
| Takedown | 7, grab 3.5 | 10, grab 3 |
| Ground Pound (was Disarm 2) | -- | 9 within 3.5 |
| Flying Kick (was Backflip) | -- | 10 |
| Throwing Knife | 5 | 8 |
| Rising Uppercut (was Shadow Step) | -- | 9 |
| Block (was Parry) | front melee -60%, perfect parry + riposte 3 | every front hit -60% |

### Punisher (`PunisherConfig`, `Firearms`, `PunisherWeaponAbilities`)
| Hit | Before | After |
|---|---|---|
| Bare fist | 1 | 7 (`UNARMED_BONUS` 6, Combat Training) |
| Brutal Strike / Breach Kick | 10 / 15 | 10 / 10 |
| Frag Grenade | 12 + a vanilla blast (~23, hurt the thrower too) | 10 (vanilla blast now breaks blocks only) |
| Warzone per missile | 30 | 18 |
| Target Designation | +30%, no ceiling | +30%, never lifts a hit past 20 |
| Arrows | x2 | x1.5 |
| Shotgun pellet | 3 / 4.5, falloff silently disabled | 1.6 / 2.4, falloff works (range 20) |
| Sniper | 28 / 40 | 10 / 15 |
| Pistol Shift+V | 18 | 10 |
| Shotgun V / Shift+V pellet | 15 / 10 | 1.65 / 2.5 |
| Steady Shot | x2 headshot (80) | x1.33 headshot (20) |
| Armour toughness per piece | 3 | 2 |
Pistol and rifle unchanged (5 / 7.5 and 4 / 6).

### Power Armour (`ArmourTier`, `PowerArmourConfig`)
Tier damage x1.00 / 0.95 / 1.05 / 1.10 / 1.15 / 1.20 / 1.25 (was up to 1.80); armour 12 / 10 / 14 / 16 / 18 / 19 / 20
and toughness 0 / 0 / 1 / 2 / 4 / 6 / 8 (was up to 26 / 12). Integrity, slots, speed unchanged. Kit hits are capped by
`PowerArmourAbilities.melee / ability / ultimate` (10 / 10 / 20), so Celestial + Overclock lands at the top of each range.
| Hit | Before (Iron) | After (Iron) |
|---|---|---|
| Servo Punch tap / full charge | 8 / 16 | 7 / 10 |
| Power Fist | x1.5 | x1.25 (capped 10) |
| Ground Stomp | 7 | 8 |
| Shoulder Charge | 9 | 8 |
| Tesla Coil | 9 | 8 |
| Piledriver | 14 | 9 |
| Core Overload | 24 | 16 (cap 20) |
| Rail Cannon | 28 | 15 (cap 20) |
