# Undead — reference

Package `com.projecthero.mod.undead` (+ `client.undead`). Power id `undead` (was Andy, renamed in v0.17.3). Numbers live in
`UndeadConfig`, `UndeadBulletConfig` (R, head throw, Division Bullet), `UndeadEjectConfig` (G / Shift+G / X / Shift+X / C) and
`UndeadKitConfig` (Z, Shift+Z, V, Shift+V, the katana). Every move hit goes through `UndeadAbilities.strike` (Victor's x1.35, then the cap,
then the 8% boss cap).

## v0.17.7 City tier

Undead moved into the City tier (melee 11-14, abilities 11-15, ultimate 25-30). `UndeadConfig.MAX_HIT` 30 is applied in `strike` after
Victor's multiplier, and `UndeadDamage` re-applies any other hit from an Undead above 30 at 30 (a shoulder-sword punch with
sharpness and a crit). The Unluck Meteor (Shift+Z) is the ultimate.

| Hit | Before | After |
|---|---|---|
| Fist (Bloody Arts) / Victor fist | 4 (+3) / 8 (+7) | 11 (+10) / 14 (+13) |
| Repair Headbutt | 8 | 11 |
| R Finger / Shift+R Hand / hold R Arm | 7 / 12 / 20 | 11 / 13 / 15 |
| G Blood Spray (every 0.5 s) | 10 | 10 (tick damage, unchanged) |
| Shift+G Blood Whirlwind | 25 | 15 |
| Shift+X Combustion Punch | 22 | 15 |
| C Deadline (every 0.5 s) | 12 | 12 (unchanged) |
| Shift+C clone hit / seed finger | 8 / 2 | 11 / 2 (the seed is not an attack) |
| Z Vortex Bullet / end burst | 18 / 6 | 15 / 11 |
| Shift+Z Unluck Meteor (centre -> edge) | 40 -> 8 | 30 -> 25 |
| V Rib Shotgun (7 spikes) | 6 each | 2 each (14 if all seven land, like the Punisher's shotgun pellets) |
| Shift+V Spine Whip | 14 | 14 |
| Katana melee / sweep | 9 / 4 | 13 / 11 |
| Katana R Crescent / G Great Crescent / Z Crimson Flash / C Blood Whirl | 13 / 24 / 16 / 10 x2 | 13 / 15 / 15 / 11 x2 |
| Keyless old moves (G crescents, Head Bullet / Launch, Leg Launch) | 11 / 18 / 18 / 6 | 11 / 15 / 15 / 11 |
| Victor's Dead Blade (Victor is off) | 22 | 15 (Vulcan 4 x10, Dead Road 7, Deadline rolls left alone) |

Toughness: unchanged. Undead grants no armour, resistance or extra health; its defence is the regeneration design (1 HP every 5 ticks,
the part system, shattering instead of dying), which was left as it is. The shoulder sword still adds the sword's own damage to the 11-damage
fist (a netherite sword: 19), never past the 30 cap.
