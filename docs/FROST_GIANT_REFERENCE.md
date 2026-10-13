# The Frost Giant Assault (v0.16.1)

A Hero-tier siege raid, the Thor-flavoured one: a rift to Jotunheim tears open above your base during a blizzard and
King Laufey's Frost Giants march through it. The hook is **cold** -- four Rune Braziers keep the fighters alive and the
giants go out of their way to stamp them out. A "big bodies" fight: few enemies, each one huge, slow and dangerous.

**Progression / threat ladder:** after the Ultron Uprising, alongside the Apokolips Invasion. Anyone can run it; Thor
players get the most out of it (lightning staggers Laufey, Rime-touched hammers, Jotunheim Storm, Heimdall's warning).

Code: `com.projecthero.mod.frostgiant` (server) and `com.projecthero.mod.client.frostgiant` (client). Tuning:
`config/projecthero_frost_giants.json` (`FrostGiantConfig`, VersionedConfig v1). Gametests: `FrostGiantGameTests`.
Design brief: `docs/FROST_GIANT_ASSAULT_MASTER_PROMPT.md` (main checkout).

## 1. Starting it
- **Jotunheim Rune Stone** (block, shaped recipe `jotunheim_rune_stone`):
  ```
  B P B      B = blue ice, P = packed ice, S = Heart of the Sea,
  P S P      L = lodestone, C = chiseled stone bricks
  C L C
  ```
  Repeat runs: `jotunheim_rune_stone_from_shard` -- the same with a **Rime Shard** in place of the Heart of the Sea.
  Place it and right-click it **at night**, in Survival (never on Peaceful). It becomes the **Rift Anchor**
  (`ACTIVE=true`: blue-white runes, light 10, unminable) and the arena is centred on it. One assault per
  `minDistanceBetweenEvents` (256).
- `/projecthero raid start|end|advancetimer frost_giants` (op). `start` places a Rune Stone at your feet (any time of day)
  and puts you 2 blocks south of it. `advancetimer`: countdown -> wave 1, a wave -> cleared, breather -> next wave / Laufey,
  Laufey -> his next phase -> the Casket breaks -> he falls, victory -> the chest. `removetimer` has nothing to do.
- **Natural trigger** (`trigger.naturalTriggerEnabled`, **default false**): once a minute every player who is Thor-worthy
  or holds Mjolnir / Stormbreaker, in a snowy biome (snow precipitation at their feet), at night, during a thunderstorm,
  rolls `chancePerMinute` (0.02). Heimdall (gold, chat): "The Jotnar march on Midgard." and a Rune Stone appears in their
  inventory.

## 2. The arena: the Frost Rift
- Radius **56** round the anchor, edged every half second by pale blue / white dust, snowflakes and full-bright end-rod points (so it reads at night; forced long-range).
  Fighters are kept inside (pulled back if they leave, up to 48 past the edge); anyone who walks in joins; a fighter who
  dies is **down** until they walk back in; all down at once = **defeat** (the anchor shatters -- block removed).
  `end` (abort) just switches the anchor back to dormant.
- **The rift** (render-only, `FrostBlizzardClient`): a swirling blue-white vortex 40 blocks over the anchor -- a ring of
  translucent spiral arms (`debugQuads`) with a white-hot core and flickering lightning-white edges (`lightning`), plus
  spark / snowflake particles at its rim. Radius 10; 17 when Ymir tears it wide; it collapses to nothing over 4 s on
  victory. Giants climb down out of it on a **beam of frost light** (white dust line + end-rod sparkles, 40 ticks, Laufey
  70) and land with a snow-burst (the big ones shake the camera).
- **Blizzard** (`FrostBlizzardPayload`, de-duplicated, re-sent every 2 s or on change): sky tint 0x9FC6E8 (darker
  0x4E6A8A while the Casket is open) through the shared raid sky (`ZombieRaidNetworking.pushSky`), fog pulled in to
  **48** blocks (**32** with the Casket open) by `FogRendererMixin`, snow and ash driven round the player.
- **Snow layers** (`FrostGiantAssault.snowfall`): `snowLayersPerTick` (6) per framework tick (x2 with the Casket open),
  only on air above a sturdy-topped block, only in loaded **and ticking** chunks, inside the arena, up to 2 layers deep (1 on grass / mycelium -- vanilla turns them to dirt under 2+ layers, a permanent change),
  capped at `maxSnowLayers` (1600). Every layer is recorded and melts away on the victory (gradually through the 5 s
  victory beat) and on any end.
- **Cold** (`FrostGiantAssault.tickCold`, every server tick, fighters inside only): outside warmth, vanilla freeze ticks
  build at `buildPerSecond` (14/s; +2 a tick to cancel vanilla's own thaw), capped a little over full frost, so you get
  the powder-snow slowness and frosted screen; at full frost an extra freeze hit every 2 s (with vanilla's own, 1 damage a
  second). Inside a lit brazier's warmth: thaw 60/s and Regeneration I. No cold for: Fire Resistance, being on fire, lava,
  Pyrokinesis active, the Casket in the off hand, a full Rimed netherite set. **All braziers out: x2. Casket open: x2.**
  The blizzard (and with it the cold and the snowfall) ends when the Casket breaks.
- **Rune Braziers** (`RuneBrazierEntity`, 1 x 2, no AI, never moves): 4 on a 20-block ring (at 45 / 135 / 225 / 315
  degrees). Health 200 + 60 per extra fighter. Warm radius **7**.
  - Only Jotunheim can hurt them (giants, their projectiles, freeze); fighters' blows pass through.
  - Giants that hunt braziers (Raiders, Brutes, the Warlord) go for the nearest **lit** one whenever no fighter is within
    **8** blocks (Brutes: 4). Hounds, Shamans and Laufey hunt fighters.
  - At 0 health it **goes out** (cold white smoke, no warmth) and the giants lose interest. Relight: hold right-click 3 s
    (progress synced for the HUD bar), flint and steel / a fire charge / Pyrokinesis instantly. Comes back at 50%.
  - Drawn as a chiseled-deepslate stand with a campfire (soul campfire, unlit, when out) and a glowing rune band.
- Bars: the event bar (blue, notched) -- countdown / "wave n/5 - x giants - braziers lit x/4" / breather / boss stage /
  victory; the Warlord, Laufey and the floating Casket each have their own `ServerBossEvent`.

## 3. Enemies (custom GeckoLib models -- placeholders until the generator's are approved)
| Unit | Size | HP | Armour | Behaviour |
|---|---|---|---|---|
| **Rime Hound** | 1.6 | 40 | 2 | Packs of 3 (spawned together). Speed 0.38. Circles at ~5 blocks, darts in to **bite** (5 + 40 freeze) or **pounces** from 4-7 blocks (6). The first to spot a fighter **howls**: every hound within 24 gets the target and Speed II for 4 s. |
| **Jotun Raider** | 4.5 | 140 | 6 | Arrives in **pairs**; **war-cries** before its first charge (Speed II 4 s). Spear thrust 10, reach 4. 8-24 blocks with line of sight: 1 s wind-up (spear raised, snowflakes forming) then an **ice javelin** (9 + 60 freeze, shatters), 4.5 s cooldown. Hunts braziers. |
| **Frost Shaman** | 5 | 160 | 4 | Keeps 14+ away. **Ice Wall** (11 s cooldown): 5 wide x 3 tall packed ice 3 blocks in front of a fighter, 8 s. **Rime Ward** (13 s): a giant under half health (not Laufey / hounds) is encased (`IcePrisonEntity` WARD) -- invulnerable for 4 s, then heals 25%; break the ice (players' hits) to cancel. **Frost Nova** (5 s) when a fighter is within 4: 6 + knockback + 120 freeze. |
| **Ice Brute** | 7 | 450 | 12 | Brazier smasher (ignores fighters further than 4). **Charge**: 1.2 s pawing the ground, then a locked straight rush (0.85 b/t, up to 1.4 s, stops on a wall): 16 + launch (40 to a brazier). **Glacier Spikes**: 1 s of cracking frost along the line, fists slam, packed/blue-ice spikes erupt outward 2 blocks a tick to 16 blocks, 12 each (+30 freeze), 3 s blocks. Melee / brazier smash 14 / 40. |
| **Frost Giant Warlord** | 11 | 1200 + 300 / extra | 14 | Wave 5 mini-boss, own boss bar, `BossThreat`. War cry on first sight. **Cleave**: 1 s arc telegraph, 18 in a 110-degree arc to 7 blocks. **Sword Plant**: a ring of ice spikes runs outward 1.1 blocks a tick to 12 -- 14 + freeze to anyone **on the ground** as it passes. **Rally** (2+ giants near, 20 s cooldown): every giant within 24 hits +20% for 8 s (frost runes swirl). At 30% he **kneels** 3 s taking x1.5. |

- **Damage rules** (`FrostCombat.multiplier`): fire, lava, a burning attacker or projectile, Pyrokinesis **x1.5**;
  lightning / Electrokinesis **x1.25** (bigger wins, no stacking). Immune to freezing (`canFreeze` false), Slowness and fall
  damage; Jotunheim never hurts its own (or its projectiles). Rally adds +20% to their outgoing hits.
- **Waves**: 8, `round(base x (1 + 0.5 x (fighters - 1)))` (the Warlord never multiplies), cap 16 alive, 3 arrivals per
  framework tick (+ the pair / pack partners). Solo hounds / raiders / shamans / brutes / warlord: w1 6/0/0/0/0,
  w2 4/3/0/0/0, w3 4/3/2/0/0, w4 3/3/2/1/0, w5 3/2/2/1/1, w6 4/4/2/2/0, w7 3/4/3/2/0, w8 3/3/3/2/2 (8 waves since the later v0.16.1 pass; config v2 migrates the wave keys). 10 s countdown, 10 s breathers; a raid horn from the rift at
  each wave and when Laufey comes. Stragglers (3 or fewer, after 3 minutes) are removed (never the Warlord).
- **Temporary ice**: every wall / spike / pillar / frozen-water block goes through `FrostGiantAssault.placeIce` --
  inside the arena, at most `iceBlocksPerTick` (48) a server tick, never into a cell a living thing stands in, tracked in
  the assault's saved state (`FrostTempBlocks`) and taken back on expiry and on every end. A giant with no assault
  (spawn egg) uses the mod's global `TempBlocks` instead.
- Frost breath puffs from every giant's mouth every 2-4 s; giants 7+ tall shake the camera a little as they walk
  (Titan Shifter `TitanShakePayload`) and stomp audibly.

## 4. King Laufey
14 blocks tall, gaunt and regal: robes of dark frost-blue fur, a jagged ice crown, a beard of icicles, very long fingers,
glowing red eyes, the **Casket of Ancient Winters** in his left hand (its own `casket` bone) and an ice spear-staff in his
right. **2500 + 600 per extra fighter, cap 6000**; 14 armour / 8 toughness; own boss bar (renamed per phase); shared
`BossThreat` table (players only; a move in flight finishes first). Moves every 3 s (x0.6 enraged in phase 3), never the
same twice in a row; he keeps 7-18 blocks from his target. Chat lines (`boss.projecthero.frost_giants.laufey.say.*`,
original, rate-limited 10 s): intro, spear, guard, grasp, casket_open, breath, pillars, slam, ymir, stagger, refuse,
casket_broken, death.

**Phase 1 -- The King's Court** (100-66%)
- **Spear of Jotunheim**: three spears form above his hand (clip ticks 8 / 16 / 24), thrown on tick 30 in a 12-degree fan,
  10 + 60 freeze each.
- **Royal Guard**: 2 Jotun Raiders down the beam beside him (not while 4+ giants stand, never once drained).
- **Frozen Grasp**: the top-threat fighter -- a frost ring under them that follows for 1 s, then locks for 0.5 s (a quick
  dash escapes), then an **ice prison** (`IcePrisonEntity` PRISON): 5 s (3 s solo), can't move or attack (attacks are
  cancelled by `ALLOW_DAMAGE`); the prisoner's hits crack it 1, an ally's 2; 8 cracks (5 solo).

**Phase 2 -- The Casket Opens** (66-33%): `casket_open` clip; on its hit the blizzard doubles, every brazier loses 30% of
its health, the sky darkens.
- **Winter's Breath**: 2 s of frost gathering in the lid (aim settles 0.5 s before), then a 3 s cone (22 blocks,
  22-degree half-angle) from the Casket: 8 damage every 5 ticks + maximum freeze to anything with a clear line (blocks --
  ice walls, pillars -- stop it). It **puts out** braziers it touches and **freezes water** (temporary ice, 30 s).
- **Ice Pillars**: six 2x2 x 7 packed / blue ice pillars (half of them by fighters), 1.4 s of cracking ground, then they
  burst up: 14 + knock-up to anyone on the spot; they stand 20 s and block line of sight.
- **Avalanche Slam**: staff raised, slammed down on tick 24: 16 + knock-up + freeze within 10 blocks.

**Phase 3 -- Ymir's Wrath** (33-0%)
- The rift tears wide and **Ymir** reaches through (`YmirArmEntity`, render-only, ~40 blocks of glacier-grey arm, never
  saved -- re-summoned after a reload). Every 10 s (after a 5 s reach-in) the assault picks:
  - **Palm Slam** (60%): the hand's shadow -- a widening dark ring with frost runes -- grows over the target for 2 s, then
    30 damage + 80 freeze within 7 blocks.
  - **Glacial Sweep**: a dark line across the arena through a fighter for 3 s, then the hand drags along it over 1 s:
    20 damage + launch to anyone within 4.5 of its path (once each).
- Laufey's cooldowns x0.6; the **Casket floats** above him (`IceCasketEntity`, 2.4 x 1.8, its own boss bar,
  400 + 100 per extra fighter). His held Casket bone is hidden.
- **It can only be damaged while Laufey is staggered** (otherwise "The Casket is sealed"). He staggers for **5 s**
  (`stagger` clip, white bar, title) when:
  - he's hit by lightning (Thor, Electrokinesis), **or**
  - a fighter breaks a Frozen Grasp prison within **1.5 s** of it closing, **or**
  - he takes **15%** of his max health within **6 s**;
  then a 3 s grace before the next.
- **The Casket breaks**: Ymir's arm is torn back up the rift (`retract`), the blizzard ends (no more cold or snow), Laufey
  is **drained** -- x2 damage taken, no more summons, red bar.
- **He refuses to fall**: while the Casket stands his health never drops below 1 (`/kill`-type damage excepted) -- he
  kneels (`kneel_hold`), invulnerable, rebuilding, and the floating Casket stays exposed until it breaks.
- **Death**: a 4 s collapse (`death`, 80 ticks): he kneels, his crown cracks twice (ice bursts, glass sounds), he pales to
  ice from the feet up (client tint), then shatters into shards and snow. Every other giant left breaks with him, the
  rift collapses with a flash, the sky clears and the snow melts over the 5 s victory beat.

## 5. Rewards
The Rift Anchor becomes a chest (`FrostGiantRewards`):
- **Rime Shards** x (4 + 2 per fighter);
- 1 **Jotun Skull** (trophy block: a horned packed-ice skull with redstone-red eyes, light 6, breathes frost);
- diamonds 6-12, blue ice 8-16, packed ice 16-32, emeralds 12-20 (x party scale x valuablesMultiplier), 12-20 bottles o'
  enchanting (x party scale), a level-30 enchanted book. Party scale = `Valuables.partyScale`.
- **Casket of Ancient Winters**: handed straight to each winner on their **first ever** clear (persistent
  `frost_giant_clears` attachment). Never in the shared chest.

**Casket of Ancient Winters** (`CasketOfAncientWintersItem`, off-hand artifact):
- In the off hand: immune to freezing and powder snow (`LivingEntityFrostMixin` -> `canFreeze` false), walk on powder
  snow (`PowderSnowBlockMixin`).
- Right-click: **Winter's Breath** -- a 2 s use (10-block, 25-degree cone): 4 damage every 5 ticks + Slowness III. 20 s.
- Sneak + right-click: **Ice Prison** on the mob you look at (16 blocks): 5 s, 6 cracks; bosses (`bossHealth`
  300+ max health, the Warlord, Laufey) resist (3 s retry). 40 s.
- The two cooldowns are on the stack (`casket_breath_ready` / `casket_prison_ready`).
- **Jotunheim Storm**: a worthy Thor's lightning near a Casket holder (theirs, or anyone's within 16 of the victim) adds
  120 freeze.

**Rime Shards** (`FrostGear`, smithing table, **no template**, recipe `projecthero:rime_forging`):
- Netherite armour piece + shard -> **Rimed**: +1 armour toughness on its slot, a diamond-coloured Coast trim, the
  `projecthero:rimed` component. The **full Rimed set**: immune to freezing, walk on powder snow. Once per piece.
- Mjolnir / Stormbreaker + shard -> **Rime-touched** (`projecthero:rime_touched`, enchant glint): its thrown hits add 120
  freeze. Tooltip lines via `ItemTooltipCallback`.
- In the Rune Stone recipe in place of the Heart of the Sea.

## 6. Models, the animation contract and visuals
- One renderer for every giant (`FrostGiantRenderer`): GeckoLib model + texture + glowmask (`AutoGlowingGeoLayer`) from
  `geo|textures/entity|animations / frost_giant / <name>`; scaled so the rest pose fills the hit-box height
  (`FrostModelFit` measures the baked cubes once -- swapping in the real models needs no code change; extra secondary-motion bones are fine -- the code only looks bones up by name, null-checked; the texture size comes from each geo's own description); `neck` (head look)
  and `flinch` (hurt) are posed in code and **never keyed**; no vanilla tip-over on death.
- Asset names: `rime_hound`, `jotun_raider`, `frost_shaman`, `ice_brute`, `frost_giant_warlord`, `king_laufey`,
  `ymir_arm` (render-only), `ice_casket` (the floating Casket, drawn x1.6).
- **`FrostGiantAnims`** holds every clip: its length (ticks the server plays it) and its hit tick. Convention (the Titan's):
  the controller blends in for `TRANSITION` = 4 ticks, so a strike keyframe sits at `(hit - 4) / 20` s and a play-once
  clip is `(length - 4) / 20` s long; loops are `length / 20`.

  | Clip | Length | Hit | | Clip | Length | Hit |
  |---|---|---|---|---|---|---|
  | rime_hound.idle | 40 loop | - | | ice_brute.charge_windup | 24 hold | - |
  | rime_hound.walk / run | 16 / 10 loop | - | | ice_brute.charge | 16 loop | - |
  | rime_hound.war_cry | 30 | 10 | | ice_brute.glacier_spikes | 40 | 24 |
  | rime_hound.hurt / death | 10 / 30 hold | - | | ice_brute.brazier_smash | 26 | 16 |
  | rime_hound.bite | 14 | 8 | | frost_giant_warlord.idle / walk / run | 70 / 40 / 24 loop | - |
  | rime_hound.howl | 40 | 12 | | frost_giant_warlord.war_cry | 34 | 12 |
  | rime_hound.pounce | 24 | 12 | | frost_giant_warlord.hurt / death | 10 / 60 hold | - |
  | jotun_raider.idle / walk / run | 60 / 30 / 18 loop | - | | frost_giant_warlord.cleave | 34 | 24 |
  | jotun_raider.war_cry | 30 | 10 | | frost_giant_warlord.sword_plant | 40 | 24 |
  | jotun_raider.hurt / death | 10 / 50 hold | - | | frost_giant_warlord.rally | 36 | 14 |
  | jotun_raider.spear_thrust | 20 | 12 | | frost_giant_warlord.kneel | 60 hold | - |
  | jotun_raider.javelin_windup | 20 hold | - | | king_laufey.idle / walk / run | 80 / 48 / 30 loop | - |
  | jotun_raider.javelin_throw | 16 | 6 | | king_laufey.war_cry | 40 | 14 |
  | frost_shaman.idle / walk / run | 60 / 34 / 20 loop | - | | king_laufey.hurt / death | 10 / 80 hold | - |
  | frost_shaman.war_cry | 30 | 10 | | king_laufey.spear_fan | 40 | 30 (forms 8/16/24) |
  | frost_shaman.hurt / death | 10 / 50 hold | - | | king_laufey.summon | 36 | 20 |
  | frost_shaman.ice_wall | 28 | 16 | | king_laufey.frozen_grasp | 40 | 30 |
  | frost_shaman.rime_ward | 30 | 18 | | king_laufey.casket_open | 50 | 30 |
  | frost_shaman.frost_nova | 22 | 12 | | king_laufey.winters_breath | 100 | 40 (beam 40-100) |
  | ice_brute.idle / walk / run | 60 / 36 / 20 loop | - | | king_laufey.ice_pillars | 40 | 28 |
  | ice_brute.war_cry | 34 | 12 | | king_laufey.avalanche_slam | 36 | 24 |
  | ice_brute.hurt / death | 10 / 56 hold | - | | king_laufey.stagger / kneel_hold | 100 / 20 hold | - |
  | ymir_arm.idle | 60 loop | - | | ymir_arm.palm_slam | 50 | 40 |
  | ymir_arm.reach_in / retract | 60 / 50 hold | - | | ymir_arm.glacial_sweep | 80 | 60 (drag 60-80) |
  | ice_casket.idle | 40 loop | - | | | | |

- Bones (contract): Jotun rig (Raider, Shaman, Brute, Warlord, Laufey) root, hips, waist, flinch, chest, neck, head, jaw,
  beard, helm, shoulder/arm/forearm/hand/fingers _l/_r, weapon_r, thigh/shin/foot _l/_r, crystals, mantle; Laufey adds
  `casket` (under hand_l). Hound: root, body, chest, neck, head, jaw, tail, leg_fl/fr/bl/br, paw_*.
- Placeholder assets: `node scratchpad/gen_frost_giant_placeholders.js` (box figures from a 64x64 palette sheet; parses
  `FrostGiantAnims.java` and fails if a strike keyframe is off its tick). The real models come from
  `scratchpad/gen_frost_giant_models.js` with the same names. Item / block resources: `scratchpad/gen_frost_giant_items.js`.
  Lang: `scratchpad/lang_v0161_frost.js`.
- Props: Rune Brazier (block-model stack, full-bright while lit), ice prison (an ice block scaled to its prisoner; frosted
  ice once over 60% cracked), the Casket (spins, pulses brighter while it can be hurt), Ymir (never culled), ice javelins
  (`ThrownItemRenderer` of the `ice_javelin` item).
- HUD (`FrostBlizzardClient.hud`, inside the arena): "Warm -- Rune Brazier" or "Cold n%" under the crosshair, a relight hint
  when looking at a cold brazier, and the relight progress bar.
- **Highlights**: giants are Monsters, so Iron Man / Mind Stone outlines work through the existing client-side
  `EntityGlowMixin` path. Nothing here ever sets a server glowing flag (stragglers are not glowed either).

## 7. Guidebook
Chapter `CH_FROST_GIANTS` = 33 (`CHAPTER_POWER_BASE` = 34), linked under World Events after the Ultron Uprising. The raid
sections (where it sits on the ladder, starting it, the arena, the cold, braziers, waves, Laufey, Ymir, breaking the
Casket, weaknesses, rewards, gear, Thor, commands) and an "Enemies" section describing all six units always show. (The
bestiary that unlocked the unit entries on first kill was removed later in v0.16.1 at the user's request.)

## 8. Engineering notes
- Save/load (`FrostGiantAssault.saveExtra`): phase, phase start, wave, wave total, owed per kind, brazier UUIDs, boss and
  Warlord UUIDs, Casket-opened / Ymir-awake / Casket-broken flags, Ymir's cooldown, fighters, down, every placed block
  (position, placed state, previous state, expiry). Laufey saves his phase, stagger / grace, drained, holding, Casket id;
  braziers their lit state; the Casket its Laufey and assault. Ice prisons and Ymir's arm are never saved.
- **Orphan guard**: every giant / brazier carries its assault id and discards itself if the assault is gone; every placed
  block is mirrored into `FrostCleanup` (overworld SavedData) and put back (if still ours) once its assault no longer
  exists and its chunk is loaded -- swept every 10 s.
- Lag: snow 6 per half-second, ice 48 per tick, only loaded + ticking chunks inside the arena; particle bursts throttled;
  the cold pass is a loop over the fighters and four braziers.
- Static state: only `IcePrisonEntity.IMPRISONED` (cleared in `ServerStateReset`). Client: `FrostBlizzardClient` (reset on
  disconnect), `FrostModelFit` (weak map).
- Gametests (`FrostGiantGameTests`): registration + recipes, stats and sizes, damage rules, wave mix / scaling, start /
  braziers / abort, brazier warmth / out / relight, brazier targeting, temporary blocks removed on win / loss / abort (and a
  player's block never touched), the Casket needs a stagger, Laufey holds at 1 HP, first-clear Casket once, Rime forging +
  Casket immunity, config migration, save round trip, every clip present with its strike on the damage tick,
  the contract bones present and never keyed.
- **No Skindex skins** (user decision). The design notes' skin list was a palette reference only; none were downloaded.
