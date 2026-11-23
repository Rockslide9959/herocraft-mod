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

**Placed so far (user, 2026-11-23):** Street = Super Soldier, Punisher, Black Panther, Power Armour.
Every other power is unplaced (no badge) until the user assigns it. Don't place powers yourself.

## Rules used for the pass

- **Per hit.** Each hit of a multi-hit move sits in the range (combo slashes count as melee hits, 7-10).
- **Buffs may exceed the base range but never the tier's ultimate max** (Street: 20). Each power has a
  `MAX_HIT` constant applied after its multipliers (Onslaught, Tactical Focus, Stalk, Kimoyo Scan, Target Designation,
  Kinetic Return). Boss caps still apply on top.
- **Main fighting form only.** Black Panther's unsuited (T'Challa) kit was left as it was: weaker by design.
- **Toughness ~ full diamond armour** (takes ~30-40% of a 10-damage hit), at most +10 max health.
- Gear anyone can use (the Punisher guns) was tuned too, since it is the Punisher's kit.

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
| Onslaught burst | 8 (x1.25 = 10) | 16 (x1.25 = 20) |
| Onslaught punch bonus / shock | +5 / 4 | +2 / 6 |
| Cap | none | 20 per move hit |
Toughness unchanged (+10 HP, 30% less, suit 18 armour).

### Black Panther, suited (`BlackPantherConfig`)
| Hit | Before | After |
|---|---|---|
| Claw Combo | 5 / 5 / 8 | 7 / 7 / 9 |
| Pounce strike | 12 | 10 |
| Kinetic Overload | 30 -> 15 at the edge | 20 -> 15 at the edge |
| Kinetic Return | max(4, 1.25x shot) + up to 10, no ceiling | max(8, 1.25x shot) + up to 10, max 20 |
| Stalk / Scan stacking | uncapped (x3 x1.25) | capped at 20 in `BlackPantherCombat.capped` |
| Suit armour / toughness | +8 / +2 | +12 / +4 |

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
