# v0.14.1 — the mutation revamp

Every one of the 27 experimental mutations was rebuilt around one signature mechanic, made roughly 20% stronger
(about +20% damage, −15% cooldowns, +15% resource bars — all still below the Hero-Tier powers), given two new
abilities on the **H** and **N** utility keys (8 per power, 216 in all), a body animation for every move, and a
visible look (body shells, glowing eyes, new entities and items). Every way of obtaining a mutation was audited and
fixed.

| File | Contents |
|---|---|
| [acquisition.md](acquisition.md) | Every obtainment path, what was broken, the six new lab devices |
| [batch_a.md](batch_a.md) | Super Strength, Laser Vision, Flight, Super Speed, Super Regeneration, Super Durability |
| [batch_b.md](batch_b.md) | Geokinesis, Crystalkinesis, Pyrokinesis, Cryokinesis, Water Manipulation |
| [batch_c.md](batch_c.md) | Electrokinesis, Sonic Scream, Energy Absorption, Shockwave Manipulation, Wind Manipulation |
| [batch_d.md](batch_d.md) | Telekinesis, Teleportation, Invisibility / Light, Shadow, Gravity, Magnetic |
| [batch_e.md](batch_e.md) | Spider Climbing, Elasticity, Density, Plant, Size |

## Keys

R, G, X, Z, V, C are the six ability keys as before. New:

- **H — Utility 1** and **N — Utility 2**: each mutation's two new abilities. They fire on plain H / N when no
  Hero-Tier power uses those keys (Iron Man, the Symbiote, Wolverine, Max Steel ... keep theirs), and **Alt+H /
  Alt+N always** reach the mutation. With a mutation selected the power wheel moves to **Shift+H**.
- **Sneak+N — combo move**, when you own a matching pair of mutations:

| Pair | Combo | Effect | Cooldown |
|---|---|---|---|
| Strength + Flight | Meteor Slam | rocket up, crash down: 26 in a 6-block crater | 40 s |
| Durability + Size | Colossus Stomp | 24 in a ring that grows with your size | 40 s |
| Geokinesis + Strength | Boulder Barrage | three boulders hurled forward | 30 s |
| Speed + Electrokinesis | Thunder Sprint | 14-block lightning dash, 15 + heavy slow on the path (v0.17.7: was 18) | 30 s |
| Water + Electrokinesis | Storm Surge | electrified tidal ring, 25 + slow (v0.17.7: was 22) | 35 s |
| Cryokinesis + Water | Glacier Flood | 15 + freeze + root within 8 blocks (v0.17.7: was 16) | 35 s |
| Pyrokinesis + Flight | Comet Dash | burn 16 blocks forward, 15 + ignite (v0.17.7: was 16) | 30 s |
| Wind + Pyrokinesis | Fire Tornado | burning vortex at your aim, pulls enemies in for 6 s | 45 s |
| Energy Absorption + Laser | Solar Lance | 40-block, 30-damage beam | 40 s |
| Shadow + Teleportation | Umbral Leap | vanish up to 28 blocks, blind + hit near the landing | 25 s |

## Framework (for future work)

- `AbilitySlot.SLOT_7 / SLOT_8` (H / N); `Power` takes 6 or 8 abilities.
- `hero/visual/MutationVisuals` — synced-to-everyone move animation (`play / ensure / stopIf`) and overlay flags
  (`registerFlag`, `registerValue`); client `client/mutation/MutationPose` + `MutationPoseLibrary` (≈30 generic poses)
  and `MutationOverlays` / `MutationRender` (`shell`, `eyes`, `onPart`, `bake`).
- `hero/visual/MutationMeters` — each power registers its own HUD bars (Meter / Hairline / Slab / Gauge).
- `hero/power/ComboMoves` — the Sneak+N combos.

## v0.17.7 City tier

The user placed Mutation in the **City** tier (`PowerRank`, `docs/POWER_TIERS_REFERENCE.md`): melee 11-14, abilities 11-15,
ultimates 25-30, toughness about netherite (40-50% off a hit), at most +16 max health. Only the 13 enabled mutations
(`Powers.ENABLED`) were retuned; the disabled ones still go through the shared cap below.

**Shared cap (`hero/power/MutationDamage`, `HeroConfig`).** Every mutation ability hit (`AbilityHelpers.hurt / hurtBurst /
hurtLands` calls in `hero/power/p*`, `hero/revamp`, `ComboMoves`, `GrabHelper`, plus the thorn, clone and ambush hits) goes
through `MutationDamage`, which caps it at `mutationMaxHit` (30) after every multiplier: Maximum Effort x2, Overdrive x2,
Heat +40%, living ground +20%, the stacked combo bonuses (Meteor Slam +12, Geo + Strength +4, wet x1.6), Ambush x2. Plain
melee: the attack-damage modifiers mutations add (`MutationDamage.MELEE_MODIFIERS`) never lift a swing past 30
(`PlayerAttackComboMixin` brackets the swing, `LivingEntityMutationHitMixin` trims it; a swing already over 30 without them is
left alone). Boss caps still apply on top.

**Toughness.** `HeroDamageRules` floors the combined reduction of all owned mutations at `mutationMaxDamageReduction` (half off;
falls and immunities excluded), so stacked passives/modes can't go past netherite. Size's max-health bonus is capped at
`mutationMaxBonusHealth` (16). Verdant Form +6 / +2 -> +14 armour / +6 toughness. Rubber Shield 60% -> 50%.

**Ultimates.** No mutation kit labels an ultimate. Moves already above the ability range that are the kit's biggest commitment
(30+ s cooldown or a 60-point meter cost) went to 25-30: Bull Rush, Overgrowth, Tsunami and the Sneak+N combos. Everything
else went to 11-15. Held beams / streams / DoTs keep their per-tick numbers (listed below as DoT).

**Super Speed:** the Speed Force Speedster and XLR8 borrow these moves. They keep the old constants (`BLITZ_DAMAGE` etc.) and
are not capped as a mutation; the mutation uses `SuperSpeedMoves.CITY_*` (`SuperSpeedMoves.city(...)`).

| Power | Move | Before | After |
|---|---|---|---|
| Super Strength | Fist / Max Effort fist | 11 / 22 | 11 / 22 |
| | Charged Punch | melee +15 (26) | melee +4 (15) |
| | R Haymaker | 10 / 12 / 22 | 11 / 12 / 14 |
| | G Ground Slam / dive | 20 / 20-34 | 13 / 13-15 |
| | Shift+G Thunderclap | 9 | 11 |
| | X Power Leap landing | 7-22 | 11-15 |
| | V thrown creature / bystanders | 12 / 9 | 13 / 11 |
| | V thrown block / splash | 19 / 8 | 14 / 11 |
| | Shift+V Rip & Hurl / splash | 26 / 14 | 15 / 12 |
| | Z Bull Rush (ultimate) | 24 (x1.25) | 26 (x1.25, capped 30) |
| Laser Vision | R Heat Vision (per target, every 10 ticks) | 4.8 -> 12 | 5.6 -> 14 |
| | Shift+R Piercing Blast | 29 | 15 |
| | G Sweeping Arc | 14.4 | 13 |
| | X Recoil Blast | 9.6 | 11 |
| | Z Maximum Output (10 s channel) | 14 + 12 burst | unchanged (held) |
| Flight | R Flying Punch | 14 | 14 |
| | Shift+R Dive Bomb | 12-30 | 11-15 |
| | Z Sonic Boom | 16 | 15 |
| Super Speed | R Rapid Assault | 4 x 5 | 4 x 11 |
| | Shift+R Mach Punch / ring | 20 / 6 | 15 / 11 |
| | G Blitz / ring | 20 / 10 | 15 / 11 |
| | Shift+G Vortex | 3 DoT + 8 | 3 DoT + 11 |
| | Shift+X Speed Sweep | 15 | 14 |
| | Shift+Z Lightning Throw / arc | 24 / 12 | 15 / 11 |
| Geokinesis | R Rock Shot | 12 | 12 |
| | Shift+R Boulder | 26 | 15 |
| | Hold R Stone Barrage | 5 x 8 | 5 x 11 |
| | Z Earthquake | 14 | 14 |
| | Shift+Z Earth Spikes | 18 | 15 |
| | Hold X Burrow burst | 10 | 11 |
| | Hold Z Fissure | 16 | 15 |
| | Hold C Avalanche | 6 a rock | 6 (rain) |
| Pyrokinesis | Fireball / Whip / Overheat nova | 14 / 12 / 12 | unchanged |
| | Z Meteor | 16-30 | 11-15 |
| | Shift+Z Inferno | up to 30 | up to 15 |
| | Hold R Blue Flame | 24 | 15 |
| | Hold G Flame Serpent | 18 | 15 |
| | Hold X Phoenix Dive | 20 | 15 |
| | Hold Z Meteor Shower | 6 x 10 | 6 x 11 |
| | Flamethrower / Lasso / Wall / Ring | 4 / 3 / 3 / 4 DoT | unchanged |
| Cryokinesis | R Ice Bolt | 10 | 11 |
| | Shift+R Ice Shards | 7 x 4 | 7 x 4 (shotgun pellets) |
| | Z Glacier Spikes | 16 | 15 |
| | C Ice Blade | +8 melee | +11 (12 bare-handed) |
| | Shift+C Shatter / frozen | 20 / 40 | 15 / 30 |
| | Hold R Ice Javelin | 18 | 15 |
| | Hold C Ice Golem punch | 10 | 11 |
| Teleportation | Bamf Strike | 11 | 11 |
| | Ambush (first hit after a jump) | x2 | x2, never past 30 |
| Super Regeneration | (no damage) | | |
| Elasticity | R Stretch Punch | 12-24 | 11-15 |
| | Shift+R Gatling (6 a second) | 12.8 | 12.8 |
| | G hurl / into a wall / bystander | 10 / 16 / 6 | 11 / 15 / 11 |
| | Shift+X Rubber Ball | 6 | 11 |
| | Z Hammer Fist | 20-34 | 12-15 |
| | Shift+Z Spring Stomp | 20 | 15 |
| | Wrap & Squeeze | 4 DoT | unchanged |
| Plant | R Thorn Volley | 3 x 5 | 3 x 11 |
| | Shift+R Vine Lash (+boss) | 12 (+4) | 12 (+3) |
| | G Root Snare | 6 + 2 DoT | 11 + 2 DoT |
| | Z Overgrowth (ultimate) | 30 (36 on living ground) | 25 (30) |
| | Shift+Z Treant slam | 10 | 11 |
| | Thorn Sentry / Bramble / Verdant thorns | 5 / 3 / 3 | unchanged |
| Wind | R Wind Blade | 12 | 12 |
| | Shift+G Wind Push / wall slam | 6 / 8 | 11 / 11 |
| | Hold R Cyclone Blade | 22 | 15 |
| | Hold C Downburst | 18 | 15 |
| | Flurry / Tornado / Twin / Suffocate / ride | 6 / 4 / 4 / 3 / 6 DoT | unchanged |
| Water | Water Shot / Whip / Tidal Wave | 12 / 12 / 14 | unchanged |
| | X Riptide spin | 8 | 11 |
| | Hold R Water Dragon | 20 (splash 10) | 15 (7.5) |
| | Hold Z Tsunami (ultimate) | 22 | 25 |
| | C Aquatic Form | +6 melee | +10 (11 bare-handed) |
| | Hydro Jet / Prison / Whirlpool | 8/s / 4/s / 3/s DoT | unchanged |
| Combos | Meteor Slam / Solar Lance | 26 / 30 | 26 / 30 |
| | Storm Surge | 22 | 25 |
| | Thunder Sprint / Glacier Flood / Comet Dash | 18 / 16 / 16 | 15 / 15 / 15 |
| | Boulder Barrage (a boulder) / Umbral Leap | up to 22 / 10 | up to 15 / 11 |
