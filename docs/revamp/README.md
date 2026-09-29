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
| Speed + Electrokinesis | Thunder Sprint | 14-block lightning dash, 18 + heavy slow on the path | 30 s |
| Water + Electrokinesis | Storm Surge | electrified tidal ring, 22 + slow | 35 s |
| Cryokinesis + Water | Glacier Flood | 16 + freeze + root within 8 blocks | 35 s |
| Pyrokinesis + Flight | Comet Dash | burn 16 blocks forward, 16 + ignite | 30 s |
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
