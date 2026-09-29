# v0.13.22 mutation revamp — batch B

Powers: **05 Geokinesis**, **06 Crystalkinesis**, **08 Pyrokinesis**, **09 Cryokinesis**, **25 Water Manipulation**.

Every power now has 8 abilities (R, G, X, Z, V, C + the utility slots H / N). General tuning versus v0.13.21:
roughly +20% damage, -15% cooldowns, +15% resource capacity (500 -> 575 on every strain / cold / water bar).
Cooldowns below are base values (scaled by `HeroConfig.cooldownMultiplier`). "+10 / +8" = the shared stance bonus
(`StanceMode`: +10 ability damage, +8 melee, 20 s cooldown after dropping the stance).

Code: handlers in `hero/power/p05|p06|p08|p09|p25`, registration in `hero/revamp/RevampBatchB` (+ `BatchBEntities`,
`BatchBItems`, `BatchBScheduler`, `BatchBUtil`), client in `client/mutation/RevampClientB` (+ `client/mutation/batchb`).
Textures from `scratchpad/gen_v01322_b_textures.js`, lang from `scratchpad/lang_v01322_b.js`.

Shared infrastructure added in batch B:
- `BatchBScheduler` — a per-tick effect scheduler (travelling spike lines, rolling waves, whips, tracked fireballs,
  prisons) and a ledger of short-lived block changes (`removeTemporarily` for the Sinkhole, `placeTemporarily` for
  water). Each entry expires on its own clock (unlike `TempBlocks`' FIFO, where a short entry waits behind any longer
  one queued ahead of it). Everything is restored on `SERVER_STOPPING` and dropped on `SERVER_STOPPED`, so batch B
  never needed to touch `ServerStateReset`.
- `BatchBUtil` — change-only resource writes (each `setResource` re-syncs the whole state), the hold-to-charge
  ultimate pattern, and retiring resource names older kits left behind (so no stale HUD bar lingers).

---

## 05 Geokinesis — *the ground under you is your ammo*

`GroundType` samples the block under you (or up to 3 below while airborne). Rocks, spikes, quakes, pillars and walls
are made of it, and it changes the hit. The HUD shows a `Ground: ...` line naming the current material.

| ground | damage | R/G/H/N cooldowns | rider |
|---|---|---|---|
| stone / dirt | x1.0 | x1.0 | — |
| deepslate, tuff, basalt, blackstone, obsidian | x1.35 | x1.25 | extra knockback |
| sand, gravel, sandstone | x0.85 | x0.9 | Blindness 3 s (+ dust cloud around impacts) |
| netherrack, magma, soul sand, nylium | x1.0 | x1.0 | sets alight 5 s |
| ores, raw-ore / amethyst blocks | x1.45 | x1.0 | glitter |
| ice, snow | x1.0 | x1.0 | Slowness II 4 s |
| nothing earthy within 3 blocks | x0.75 | x1.0 | — |

| key | move | numbers |
|---|---|---|
| R | **Rock Shot** — a real projectile (`GeoRockEntity`) rendered as the ground block | 11 x ground, 1.7 s. Sneak: ground-shake cone 10 blocks, 11 + Slowness II, 8.5 s |
| G | **Earth Spike** — a line of spikes racing 16 blocks along the ground, 2 blocks/tick | 15 x ground once per target, launch + slow; spikes (local stone + pointed dripstone) last 2.5 s; 4.25 s. Sneak: auto-tracking cone 18, 19 s |
| X | **Rock Surf** (toggle) — ride a slab of the ground, carried the way you face, hopping 1-block steps | 0.72 b/t (ice 0.95, sand 0.78, deepslate 0.62); ploughing into things 7 x ground; max 15 s; ends in water / 1.5 s airborne; 4.25 s cd. **Sneak + X: Earth Swim** (kept) 12 s, own 25 s cooldown |
| Z | **Earthquake** (hold 4.25 s) | 54 x ground, r 25 with falloff, ravines; 55 s. **Sneak at the press: Colossal Rock** (Boulder Lift folded in) — boulder gathers overhead, hurled `ColossalRockEntity` 60 dmg; 75 s |
| V | **Stone Wall** — wall / dome (look up) / bridge (look down) of the local stone (sandstone, netherrack, cobbled deepslate, packed ice by ground) | 7 s. Sneak + V: Seismic Sense |
| C | **Earth Armor** — THICK stone-skin shell tinted by ground | Resistance II, 60% KB res, -25% speed (was -35%), +10/+8; strain 575 (~29 s) |
| H | **Sinkhole** — the ground under your target (20 blocks) drops into a pit 3 deep, sized to the target | 10 x ground, dragged down, rooted 3 s; pit refills after 6 s bottom-up, lifting anything inside; never under the caster; players / 200+ HP bosses are rooted without the dig; 12 s |
| N | **Tectonic Pillar** — a column under the target (launch, 12 x ground) or, looking at nothing, under you (~9 blocks up, 8 s no fall damage) | column stands 4 s; 7.65 s |

Passives kept: iron-tool hands, faster earth mining, bare-hand vein mining, Haste on earth, Seismic Sense.
Dropped: Boulder Lift (V) and rock flight (Sneak + X look down) — any left running by an old save is torn down.

Visuals: `p05.stone_skin` (THICK shell, 7 ground tints via synced value `p05.ground`), `p05.rock_surf` (the actual
ground block squashed into a board under the feet, block id synced as `p05.surf_block`). Poses: `p05.surf` (loop),
`p05.heave`, `p05.sinkhole`, plus throw_right / stomp / ground_pound / slam_two_hand / summon_ground / leap.

## 06 Crystalkinesis — *plant crystal nodes, then shatter them*

`CrystalNodeEntity`: an amethyst cluster (real block model, turned to grow out of the face it was planted on,
full-bright). 30 s life, never saved, max 8 per caster (a 9th cracks the oldest), cracks away if the owner leaves.

| key | move | numbers |
|---|---|---|
| R | **Crystal Shard** — flying shard (`CrystalShardEntity`), plants a node where it lands (block face or target's feet) | 13 + Slowness II, 1.7 s. Sneak: 5-shard volley 7 each (5 nodes), 8.5 s |
| G | **Shatter** — detonate every node you own | 16 per node in r 3.2 (spire x1.5 dmg, x1.4 radius); 4 s; needs a node |
| X | **Crystal Path** (toggle) — glide along your aim (pitch clamped ±35°), an amethyst rail grows beneath you (solid 3 s) with a light line ahead | 0.72 b/t, max 10 s, 3 s no-fall after, 5.1 s cd |
| Z | **Crystal Eruption** (hold 4.25 s) | 48 r 20 + pillar field, **and every node shatters**; 60 s. Sneak: Colossal Crystal 66, leaves 3 nodes; 85 s |
| V | **Crystal Prison** | 7 s lock (Slowness X, jump lock, amethyst cage) + a node beside the cage; 10.2 s |
| C | **Crystal Armor** — THICK amethyst shell + 3 glowing spikes (shoulders, spine) | Resistance I, KB res, +10/+8; **reflects projectiles**: the hit is cancelled and a shard is fired back at the shooter (30 strain each); strain 575 |
| H | **Resonance Spire** — a 2.2x spire node at your aim; fires shards at hostiles it can see | 12 s, 8 dmg per shard every 0.6 s, range 14; counts as a node for Shatter; 17 s |
| N | **Refract** — hitscan beam; a node it passes through splits it into 3 beams at the nearest visible enemies and splits into 3 nodes | 11 direct / 9 per split beam, 32 blocks; 4.25 s |

Dropped: Crystal Spikes, Crystal Barrier (wall/dome/bridge — Crystal no longer shares Geo's builds), Crystal Skate.
HUD: `Crystal Nodes` GAUGE (0-8), Crystal Armor strain, ult charge.
Visuals: `p06.crystal_armor`, `p06.crystal_path` (glowing amethyst board under the feet). Poses: `p06.shatter`,
`p06.glide` (loop).

## 08 Pyrokinesis — *one Heat bar*

Heat 0-100 (always-on GAUGE). Damage x(1 + 0.4 x heat). Blue at >= 75%. At 100%: **overheated** — 1 generic
(non-fire) damage per second and no heat-building move until below 60%. Cools 4 %/s whenever not channelling
(x3 in water or rain). The user is never set alight (fire is snuffed every tick; fire immunity kept).

| key | move | heat | numbers |
|---|---|---|---|
| R | **Fireball** (ghast fireball, power 3/4 Nether) — blue: **Flame Laser** hitscan 40 blocks | +9 / +12 | 14 x heat direct (+5 Nether, +10 Flame Body); laser 22 x heat r 3 + blocks-only blast; 1.7 s |
| G | **Flamethrower** (hold) | +0.35/tick (~14 s to overheat) | 7 x heat per hit, 7-block stream, lights surfaces (terrain-gated) |
| X | **Jet Flight** (hold) — thrust along your aim | +0.45/tick | ~0.9 b/t steady, 5 s no-fall after; water cuts it; Flight combo leaves fire |
| Z | **Inferno** (hold 4.25 s) — colossal fireball; blue: bigger blast | +30 | afterburn 14 x heat r 5.5 + 7 s burning where it bursts; 47 s |
| V | **Flame Spark** (kept) — light / Sneak: absorb fire (+3 heat each), cook, smelt | | 1 s |
| C | **Flame Body** — emissive THIN + THICK flame shells, orange -> white-gold -> blue with heat | holds heat >= 50% | +10/+8 (+5 Nether), ignite on hit, aura 3 (4 blue); blue: burning enemies within 24 take 3 every 8 ticks |
| H | **Heat Wave** — vent all heat as a rolling ring | -> 0, ends overheat | r 4 + 6h, 8 + 24h dmg, 5 s burn; needs 10%; 10 s |
| N | **Fire Whip** — 9-block S-curve lash | +7 | 11 x heat, 4 s burn, pull; 2.55 s |

Dropped: Flame Dash + flame flight, Lightning Arc (Blue Flame is now heat-driven, not a Flame Body mode), the two
old gauges (`flamethrower`, `flame_body` — zeroed and hidden). Fire spread still gated on `abilityFireSpread`
(the flamethrower stream only on `abilityTerrainDamage`, as before).
Visuals: `p08.flame_body` (value `p08.heat` drives the tint), `p08.jet` (flame cones at the feet), `p08.blue`
(blue glowing eyes). Poses: `p08.jet` (loop), `p08.heat_wave`.

## 09 Cryokinesis — *frost stacks, then frozen solid*

`FrostStacks`: hits add stacks (max 5; shown as vanilla freezing + a snowflake ring; one decays per 3 s idle).
At 5: **frozen solid** 3 s (players 1.5 s) — packed-ice shell of temporary blocks around mobs (terrain-gated),
the `p09.frozen_solid` ice-shell overlay on players; pinned, no jump, Weakness X, 6 shatter damage; 2 s immunity after.

| key | move | numbers |
|---|---|---|
| R | **Ice Bolt** | 8.5 (+10 cold biome/snow, +10 armor) + 1 stack; 1.7 s. Sneak: ice-tool wheel (kept; now puts R on a 2 s cooldown) |
| G | **Freeze Beam** (hold) | 6 + 1 stack every 10 ticks, heavy slow, lava -> obsidian, powder snow; frostbite gauge 575 (~4.8 s) |
| X | **Ice Ramp** (toggle, the Ice Slide improved) | +150% speed on a 3x3 packed-ice platform; look up > 20° while moving -> rising ramp; Sneak drops a level |
| Z | **Absolute Zero** (hold 4.25 s) | 42 r 20, everything frozen solid, snow blanket, ice sheets; 38 s |
| V | **Glacier Wall** — 7-wide curved arc, 3 high, blue-ice crest, 15 s; +1 stack and a shove to anything where it rises. Sneak: ice-spike field 10 + 2 stacks | 7 s |
| C | **Frozen Armor** — THICK frost shell + icicle spikes | Resistance I, KB res, +10/+8, aura +1 stack r 4 every 2 s, attackers +1 stack, frost walker, snow; reserve 575 |
| H | **Flash Freeze** | r 8: exposed water -> ice / lava -> obsidian 20 s, all fire out (and burning allies), 5 dmg + 2 stacks; 12 s |
| N | **Ice Blade** — new item `projecthero:ice_blade` (own texture) conjured into the main hand | 10 attack dmg, +1 stack per hit, melts after 30 s; press N again to shatter it; 32 s |

Ice Blade can't be kept: stamped with owner + melt time; deleted the moment it is not the owner's main-hand stack
(`inventoryTick`), when dropped (`ENTITY_LOAD` discard), from any open container / crafting grid / cursor (swept
every tick), and on forgetting the power. Whatever was in the hand moves to a free slot; no free slot = refused.
Dropped: Sneak + V mass freeze (now H), the wall/dome/bridge copy. Visuals: `p09.frozen_armor`,
`p09.frozen_solid`. Poses: `p09.slide` (loop), `p09.blade_draw`.

## 25 Water Manipulation — *a carried water supply*

`water_supply` 0-575, SLAB, always shown. Refill: in water +10/tick, rain +2/tick, air +0.15/tick (not in the
Nether), right-click water bottle +20% (-> glass bottle), **Sneak** + right-click water bucket +50% (-> bucket; plain
right-click still places water), siphoning a source while shaping (+20%). Aquatic Form halves costs, refills 50% faster.

| key | move | cost | numbers |
|---|---|---|---|
| R | **Water Shot** — hitscan jet; Sneak + hold: spray | 25 / 2.5 per tick | 10 (+5 wet, +10 form) + knockback, soaks; spray 6/hit; 1.7 s |
| G | **Water Whip** — a visible particle tendril uncurling 12 blocks, swaying, snapping | 40 | 19, pull toward you, soak; 5.1 s |
| X | **Riptide** | free when wet, else 30 | dash (3.4 wet / 2.6 dry), 7 bump; 5.1 s |
| Z | **Tidal Wave** (hold 4.25 s) — a wall of real water 7 wide x 2 high rolling 18 blocks, draining 6 ticks behind the crest | 200 | 42, carries targets; 38 s |
| V | **Water Prison** — shell of real (non-flowing) water, 8 s | 60 | pinned, weakened, never drowned; press again to release; 10.2 s. Sneak + hold: shape a *temporary* (60 s) source / siphon one |
| C | **Aquatic Form** — shimmering THIN water film | | +10/+8, doubled buffs, half costs |
| H | **Healing Water** — self + squadmates + your tamed pets within 6 | 120 | 6 HP, Regeneration I 5 s, extinguish; 12 s |
| N | **Geyser** — under the target (12, launch) or under you (~8 blocks up, 8 s no-fall) | 50 | 7.5 s |

All placed water goes through `BatchBScheduler.placeTemporarily` (air cells only, no placement callback so it
doesn't flow, own expiry clock). The old `water` build-up gauge is zeroed and hidden. Visuals: `p25.aquatic`.
Poses: `p25.wave_push`.

---

## Unverified / notes

- **Nothing was playtested in a client.** `compileClientJava` is clean and the gametests cover the server logic; the
  overlays, poses and entity renderers are unverified visually. Specific risks:
  - `p05.rock_surf` / `p06.crystal_path` draw a block under the feet from inside the player render layer (flip back
    to world orientation at y = 1.5 in layer space) — orientation / height may need a nudge.
  - The node renderer's grow-in anchor for wall / ceiling nodes; spire scale.
  - Flame-body tint strength with `RenderType.eyes` on a near-white texture; THICK spike offsets on the arms.
- Movement moves (Rock Surf, Crystal Path, Jet Flight, Ice Ramp ramping) are server-driven velocity each tick
  (`launchSelf`), like the old Crystal Skate — expect slight rubber-banding on high ping.
- Crystal Armor's reflect uses `ALLOW_DAMAGE`; it could not be gametested with a mock player (spawn invulnerability
  swallows the hit before the event).
- Tidal Wave's travelling water and the prison shell are real blocks for a few ticks: things standing in them are
  briefly "in water" (the intended look), and a wave crossing a player's own feet can slow them.
- Sinkhole refuses bosses (> 200 max HP) and players for the dig itself, and never digs under the caster.
