# Frost Giant Assault: master prompt

Paste everything under **PROMPT** into a new session to build it. The notes above that line are for you.

## Design notes (the short version)
- **What it is:** a Hero-tier siege raid. A rift to Jotunheim tears open above your base during a blizzard and the
  Frost Giants of King Laufey march through it. It's the Thor-flavoured raid (Jotunheim is Asgard's old enemy), but
  anyone can run it.
- **Where it sits on the threat ladder:** after the Ultron Uprising, alongside the Apokolips Invasion. It's a "big
  bodies" fight: few enemies, each one huge, slow and dangerous, the opposite of Ultron's swarms.
- **The hook: cold.** The blizzard freezes you. **Rune Braziers** around the arena keep you warm, and the giants
  go out of their way to stomp them out. You fight the giants *and* hold your fires.
- **Arc:** 5 waves (Rime Hounds → Jotun Raiders → Frost Shamans → an Ice Brute → a Frost Giant Warlord) →
  **King Laufey** (3 phases) → in phase 3 **Ymir** (a glacier-sized colossus) reaches through the rift. You end it by
  smashing the **Casket of Ancient Winters** while Laufey is stunned.
- **Rewards:** the **Casket of Ancient Winters** (an off-hand artifact with frost powers), **Rime Shards** (a
  material for a frost armour set and weapon upgrades), a **Jotun Skull** trophy and loot.

### The models: big custom models, NOT player models, and NOT the Titan
Build them the way the **Titan boss** was built: a GeckoLib model of its own, written by a Node generator script
(geo + texture + glowmask + animations), with procedurally painted, shaded texture at 3 texels per model pixel, and
posed preview sheets rendered by the generator. The Skindex skins below are **colour and pattern references only**.
Sample their palettes (skin blues, ridge patterns, red eyes, fur and leather colours) and paint the new textures from
them. Do **not** wrap a skin onto a player-shaped model.

How the Frost Giants must look different from the Titan:

| | Titan (existing) | Frost Giants (new) |
|---|---|---|
| Posture | hunched, knuckle-dragging, ape-like | **upright and proud**, chest out, Norse warrior stance |
| Build | massive slab of muscle, short legs, huge forearms | **tall and long-limbed**: long legs, narrow waist, broad shoulders, long fingers |
| Skin | rotting green-grey, gashes, run-off blood | **deep frost-blue to slate**, with raised Jotun **ridge patterns** (lighter blue lines across the face, chest and arms) |
| Growths | bone spikes from the spine, torn ribcage with embers | **ice crystal clusters** growing from the shoulders, back and forearms (translucent, pale cyan, glowing edges) |
| Eyes | ember-glow | **solid red, glowing** (Marvel Jotun) |
| Gear | iron shackles, broken chains, rags | **fur mantles, leather straps, bone and iron jewellery, braided beards, horned or antlered helms**, ice weapons |
| Feel | feral monster | **a warrior people**: they shout war cries and move in formation |

### Skins found on The Skindex (minecraftskins.com)
These are references only. **Ask the user before downloading any**, then record the uploader names in the reference
doc when you do.

| Use for | Skin | Skindex id | Why |
|---|---|---|---|
| **Jotun Raider** (main palette) | "frost giant (grunt)" | 13665610 | clean pale-blue Jotun grunt; base skin tone and ridges |
| Raider (alternative) | "frost giant ymir" | 22579058 | blue skin, dark loincloth and fur; good for fur/leather tones |
| **Ice Brute** | "ice beast" | 23493172 | icy, yeti-like, crystalline; brute texture and ice growths |
| **Frost Shaman** | "iceodrom" | 14202173 | blue with purple robes; caster palette |
| Shaman (alternative) | "falgroth jarvil (rp character)" | 21944469 | dark gear with ice detailing |
| **Frost Giant Warlord** | "frost giant ymir 2" | 22579060 | heavier Ymir variant; warlord armour tones |
| Warlord (alternative) | "jotuun" | 13470791 | pale giant with gold jewellery |
| **King Laufey** (main) | "laufey (thor movie) [updated]" | 18743054 | dark blue, red eyes, movie-style regal Laufey |
| Laufey (alternative) | "laufey (comics) [updated]" | 18743021 | comic version, for the crown and robes |
| Ridge patterns / red eyes | "loki the frost giant" | 21701524 | strong Jotun markings and red eyes |
| Ymir apparition | "frost giant" | 15597735 | grey-armoured giant; glacier-grey palette for the colossus |

The Rime Hounds (frost wolves) and the Casket have no skin: paint them procedurally in the same palette.

---

## PROMPT

Build **The Frost Giant Assault** for Project Hero (Fabric 1.21.1, mod id `projecthero`, package
`com.projecthero.mod`, repo at `C:\Users\ethan\OneDrive\Desktop\Coding Projects\Superhero Mod`). It's a new raid event
with its own Frost Giant enemies and a three-phase boss, **King Laufey**. Release it as the next patch version
(check `gradle.properties` **and the existing git tags** first, and bump `version=`).

Read these before writing code:
- `docs/TITAN_BOSS_REFERENCE.md` and `scratchpad/gen_v01420_titan_model.js`: **the model pipeline to copy**
  (generator writes geo + texture + glowmask + animations; preview sheets; strike keyframes pinned to server ticks;
  code-driven neck and flinch bones that are never keyed; `MODEL_SCALE` maps the rest pose to the hit-box height).
- `docs/ULTRON_REFERENCE.md`, `docs/CARNAGE_REFERENCE.md`, `docs/DARKSEID_RAID_REFERENCE.md`,
  `docs/SYNDICATE_REFERENCE.md`: raid and boss patterns (arena border, fighters and downs, waves, gate, boss bars,
  reward chest, save/load, orphan guard).
- `com.projecthero.mod.event` (EventInstance, EventManager, EventBossBar, EventTypes) and
  `com.projecthero.mod.ultron.UltronUprising` (the newest raid; copy its structure).
- `BossThreat` and `docs/TARGETING.md`: **every boss uses the shared threat table** (user rule).
- `com.projecthero.mod.horde` (`HordeRaid.drawBorder`) and `ZombieRaidNetworking.pushSky` (sky tint).
- Thor code (`power/ThorPowers`, `thorarmor`, Stormbreaker, the Bifrost menu): for the Thor tie-ins.
- Your memory files for this project, **especially the gotchas** (stale config migration via VersionedConfig, static
  state goes in `ServerStateReset`, GeckoLib busy-flag / head-accumulation, the private-highlight rule, the harness
  flag-file trick for screenshots).

### Skins (ask first)
Show the user the Skindex skins listed in the design notes (ids 13665610, 22579058, 23493172, 14202173, 21944469,
22579060, 13470791, 18743054, 18743021, 21701524, 15597735) with a screenshot contact sheet, and **ask before
downloading**. The Skindex blocks curl behind Cloudflare, so fetch them through the browser. Big PNGs got corrupted
when transcribed last time, so verify each one with a hash or a dimension check. They are palette and pattern
references for the generator, never textures on a player model. Credit the uploaders in the reference doc.

### 1. How it starts
- **Jotunheim Rune Stone** (block). Shaped recipe:
  ```
  B P B      B = blue ice, P = packed ice, S = Heart of the Sea,
  P S P      L = lodestone, C = chiseled stone bricks
  C L C
  ```
  Place it and right-click it at night in Survival (never on Peaceful). It becomes the **Rift Anchor**: blue-white,
  light 10, unminable while active, and the reward chest on victory (the Horde-block / Ultron-uplink pattern).
- **Natural trigger** (config flag `trigger.naturalTriggerEnabled`, **default false**): in a snowy biome, at night,
  during a thunderstorm, any player who is Thor-worthy or holds Mjolnir/Stormbreaker rolls a small chance per minute.
  Heimdall's voice (chat, gold): "The Jotnar march on Midgard." A Rune Stone appears in their inventory.
- `/projecthero raid start|end|advancetimer frost_giants`, added to `RaidAdminCommand`'s RAIDS list.
- One active Frost Giant event per `minDistanceBetweenEvents` (256).

### 2. The arena: the Frost Rift
- Radius **56** around the anchor (bigger than Ultron's 40: the giants need room). Border = pale blue / white snow
  particles (`drawBorder` style). Fighters are locked in like the Ultron raid; anyone who walks in joins; a fighter who
  dies is down until they walk back in; all down = defeat (the anchor shatters).
- **The rift:** a huge swirling blue-white tear in the sky 40 blocks above the anchor (render-only: a billboard
  vortex with particles plus lightning-coloured edges). Giants climb down out of it on a beam of frost light.
- **Blizzard:** sky tint (0x9FC6E8, pale), fog closes in to ~48 blocks, snow particles everywhere, and **temporary
  snow layers** build up on the ground in the arena (only on air above solid blocks, capped at 2 layers, max N blocks
  per tick). **Every placed snow layer is recorded and removed when the event ends** (win, loss or abort). Never touch
  player blocks.
- **Cold (the core mechanic):** fighters outside warmth build up vanilla freeze ticks (`setTicksFrozen`, the
  powder-snow effect: slowness, the frosted screen, then freeze damage). Full frost = 1 damage/s. Fire resistance,
  a lit Pyrokinesis / fire power, standing in lava or being on fire all stop it.
- **Rune Braziers** (entity, 1 x 2 blocks, no AI, 4 of them on a 20-block ring): each warms a **7-block** radius
  (thaws you quickly, +regeneration I while you stand in one). They have HP (200 + 60 per extra fighter).
  - Giants **target braziers** whenever no fighter is within 8 blocks of them. Ice Brutes prefer them.
  - A brazier at 0 HP goes **out** (cold smoke). Any fighter can **relight it** by holding right-click on it for 3 s
    (flint and steel or fire charge instantly, Pyrokinesis/fire powers instantly). It comes back at 50% HP.
  - All 4 out = the blizzard intensifies: freeze builds twice as fast. That's the pressure, not an instant loss.
- Event bar (pale blue, notched): countdown → waves (+ braziers lit x/4) → boss.

### 3. Enemies: custom GeckoLib models (see "The models" in the design notes)
Make **one shared Jotun rig** (bones: root, hips, waist, chest, flinch, neck, head, jaw, beard, L/R shoulder, upper arm,
forearm, hand, fingers, weapon socket, L/R thigh, shin, foot, plus attachment bones for crystal clusters, mantle, helm)
used by the Raider, Shaman and Warlord with different geometry, gear and textures, and one shared animation set
(idle, walk, run, war cry, attack clips per unit, hurt flinch, death). The Hound and Laufey get their own rigs.
Generator: `scratchpad/gen_frost_giant_models.js` (edit the generator, never the outputs). It writes every geo,
texture, glowmask (red eyes, ridge glow, crystal edges) and animation file, renders posed preview sheets, and fails if a
strike keyframe is off its tick.

| Unit | Size | HP | Armour | Behaviour |
|---|---|---|---|---|
| **Rime Hound** (frost wolf, quadruped, its own rig) | 1.6 tall | 40 | 2 | Packs of 3-5. Fast (0.38). Circles, then darts in. Bite 5 + 40 freeze ticks. Howl when the pack spots you (they all rush). |
| **Jotun Raider** | 4.5 tall | 140 | 6 | Ice spear. Melee 10 with reach 4. 8-24 blocks: 1 s wind-up (spear raised, ice forming) then **ice javelin** (projectile, 9 dmg, 60 freeze ticks, shatters into shards). Raiders advance in pairs and war-cry before charging. |
| **Frost Shaman** | 5 tall, robed | 160 | 4 | Staff with a glowing crystal. Keeps 14+ blocks away. **Ice Wall** (a 5-wide, 3-tall line of temporary ice in front of fighters, 8 s, breakable, recorded and removed). **Rime Ward**: encases a wounded giant in ice for 4 s (invulnerable, then heals 25%; break the ice to cancel). **Frost Nova** if a fighter gets within 4 blocks: 6 dmg + knockback + heavy freeze. Priority target. |
| **Ice Brute** (Ice Beast palette) | 7 tall, hulking but upright | 450 | 12 | Ice gauntlets. **Brazier smasher** (prefers braziers). Charge: 1.2 s paw-the-ground telegraph, then a straight rush, 16 dmg + launch. **Glacier Spikes**: slams both fists, a line of ice spikes erupts toward the target (telegraphed by cracking frost along the ground for 1 s, 12 dmg each). Takes x1.5 damage from fire. |
| **Frost Giant Warlord** (mini-boss, own boss bar) | 11 tall, horned helm, ice greatsword | 1200 + 300 per extra fighter | 14 | Wave 5. **Cleave** (a wide 1 s telegraphed arc, 18 dmg). **Sword plant**: drives the sword into the ground and a ring of ice spikes runs outward (jump or be out of range). **Rally**: a war cry that gives every giant within 24 blocks +20% damage for 8 s (shows frost runes on them). Drops to one knee at 30% for 3 s (takes x1.5). |

- **Damage rules** (`FrostCombat.multiplier`): fire / lava / burning attackers / Pyrokinesis **x1.5**; Thor's lightning
  **x1.25** (Thor is Jotunheim's old enemy); giants are immune to freezing, slowness and fall damage. Giants' hits never
  hurt each other.
- **Ice is temporary:** every block any unit or the boss places (walls, spikes, prisons) is tracked by the event and
  removed on expiry and on event end. Spikes are entities or blocks, as long as they're cleaned up.
- **Waves:** 5, scaled `round(base x (1 + 0.5 x (fighters - 1)))`, cap 16 alive (the giants are big).
  Solo (hounds / raiders / shamans / brutes / warlord): w1 6/0/0/0/0, w2 4/3/0/0/0, w3 4/3/2/0/0, w4 3/3/2/1/0,
  w5 3/2/2/1/1. 10 s countdown, 10 s breathers. Arrivals: down the frost beam from the rift, landing with a snow-burst
  and a ground shake.

### 4. The boss: King Laufey (three phases)
**Look:** 14 blocks tall (shorter than the Titan's 18 and nothing like it): a tall, gaunt, regal Frost Giant king.
Long robes of dark frost-blue furs over a bare ridged chest, a jagged **ice crown** fused with his skull, a long beard
of icicles, very long fingers, and glowing red eyes. He carries the **Casket of Ancient Winters** (a glowing blue
ornate box, its own bone) in his left hand and an ice spear-staff in his right. He moves slowly and deliberately and
speaks in chat (original lines, rate-limited, `boss.projecthero.frost_giants.laufey.say.*`).

Health **2500 + 600 per extra fighter, cap 6000**; 14 armour / 8 toughness; own boss bar; uses `BossThreat`.

**Phase 1: The King's Court** (100-66%)
- **Spear of Jotunheim:** throws three ice spears in a fan (telegraph: spears form one by one above his hand), 10
  each + freeze.
- **Royal Guard:** summons 2 Jotun Raiders from the rift (not while 4+ giants are alive).
- **Frozen Grasp:** points at the fighter with the most threat. After a 1.5 s ring telegraph under them, that
  fighter is **encased in an ice prison** for up to 5 s (can't move or attack; allies break it by hitting it, or the
  prisoner mashes attack to crack out faster). It's deliberately a team mechanic, and it's softened when solo.

**Phase 2: The Casket Opens** (66-33%)
- Laufey opens the Casket: the blizzard doubles, every brazier loses 30% HP, and the sky darkens.
- **Winter's Breath:** a 3 s cone beam from the Casket (2 s telegraph: frost gathering in the lid), 8 dmg every
  5 ticks + max freeze, and it **freezes water and puts out braziers it touches**. Hide behind ice walls or braziers.
- **Ice Pillars:** 6 tall ice pillars burst up around the arena (telegraphed by cracking ground, 14 dmg if under one).
  They stay for 20 s and **block line of sight**, so use them to dodge Winter's Breath.
- **Avalanche Slam:** raises the staff and slams it down: a 10-block shockwave, 16 dmg, knock-up.

**Phase 3: Ymir's Wrath** (33-0%)
- The rift tears wide open and **Ymir**, the first Frost Giant, reaches through: an enormous glacier-grey **arm
  and hand** (a render-only GeckoLib model, ~40 blocks long, from the Ymir reference palette) comes down out of the
  sky. Ymir is scenery and a hazard. He can't be killed, and his attacks are big telegraphs:
  - **Palm Slam:** the hand's shadow grows on the ground over 2 s (a widening dark circle with frost runes), then a
    slam, 30 dmg in a 7-block circle. Move.
  - **Glacial Sweep:** the hand drags across the arena along a telegraphed line (3 s warning), 20 dmg and launch.
- Laufey enrages: his cooldowns x0.6, and the Casket floats above him, pulsing.
- **How you win: break the Casket.** The Casket (its own hit-box, 400 HP + 100 per extra fighter) can only be
  damaged while Laufey is **staggered**. Laufey staggers for 5 s when:
  - he's hit by lightning (Thor, Electrokinesis), **or**
  - a fighter breaks a Frozen Grasp prison in under 1.5 s, **or**
  - he takes 15% of his max health within 6 s.
  When the Casket breaks: Ymir's arm is torn back through the rift, Laufey is drained (takes x2 damage, no more
  summons) and the blizzard ends. If he reaches 0 HP before the Casket breaks, he **refuses to fall** at 1 HP (kneels,
  invulnerable, rebuilding) until it does.
- **Death:** a 4 s collapse. He kneels, his crown cracks, he turns to ice from the feet up, and shatters into shards
  and snow. The rift collapses with a flash, the sky clears and the snow layers melt away (the cleanup).

### 5. Rewards
The Rift Anchor becomes the reward chest:
- **Rime Shards** x (4 + 2 per fighter): the new material.
- 1 **Jotun Skull** (trophy block: a horned frost-giant skull with glowing red eyes, light 6, frost particles).
- Diamonds, blue ice, packed ice, emeralds, bottles o' enchanting and a level-30 enchanted book, scaled with
  `Valuables.partyScale` (copy `UltronRewards`).
- **Casket of Ancient Winters**: handed to each winner on their **first ever** clear (persistent attachment like
  `ultron_clears`). Never in the shared chest.

**Casket of Ancient Winters** (off-hand artifact):
- Passive (held in the off hand): immune to freezing and powder snow, and you walk on powder snow.
- Right-click: **Winter's Breath**, a 2 s frost cone (4 dmg every 5 ticks + slowness III), 20 s cooldown.
- Sneak + right-click: **Ice Prison** on one mob (5 s; bosses resist), 40 s cooldown.
- Worthy Thor players: **Jotunheim Storm**. Mjolnir/Stormbreaker lightning near a Casket holder becomes frost-lightning
  (adds freeze), purely a bonus.

**Rime Shards:**
- **Frostforged gear** at the smithing table, with a netherite piece as the base and a shard as the addition: "Rimed"
  netherite armour (+1 toughness per piece, freeze and powder snow immunity on the full set, a blue frost trim).
- **Thor:** a shard on Mjolnir or Stormbreaker (smithing table) gives a "Rime-touched" variant: its thrown hits add
  freeze. Cosmetic frost glint.
- Shards also go into the Jotunheim Rune Stone recipe (in place of the Heart of the Sea) for repeat runs.

### 6. Sounds, visuals and polish
- War cries, heavy footsteps that shake the camera slightly when a giant over 7 tall lands near you, ice cracking and
  shattering, Laufey's voice lines in chat, horn blasts from the rift between waves.
- Frost breath particles from every giant's mouth. Crystal clusters and red eyes glow (glowmask).
- First kill of each unit type unlocks its Guidebook entry (Guide chapter: next free `CH_*` id; bump
  `CHAPTER_POWER_BASE`). Update the Threat Ladder artifact and the in-game guide progression page.

### 7. Engineering checklist
- `com.projecthero.mod.frostgiant` (server) and `com.projecthero.mod.client.frostgiant` (client).
- `FrostGiantConfig` = a `VersionedConfig` (v1), `config/projecthero_frost_giants.json`. Every number above lives there.
- **Lag:** cap the snow/ice blocks placed per tick, never place outside the arena or into loaded-but-frozen chunks,
  and throttle particles (the Titan v0.9.22 lessons).
- **Cleanup:** every temporary block (snow, walls, spikes, prisons) is tracked in the event's saved state and removed
  on win, loss, abort and **on load if the event is gone** (orphan guard), plus every unit discards itself if its event
  is gone.
- Save/load: phase, wave, owed units, brazier UUIDs/HP/lit state, boss UUID, Casket HP, stagger state, fighters, down,
  placed blocks.
- Static caches go in `ServerStateReset`.
- **Private highlight rule:** never set a server glowing flag on giants. Iron Man / Mind Stone highlights must keep
  working through the existing client-side path.
- Gametests (`FrostGiantGameTests`): event starts and ends; waves scale; braziers warm and go out; giants target unlit
  braziers; temporary blocks are all removed on end, on loss and on abort; a Casket break needs a stagger; Laufey holds at
  1 HP until the Casket breaks; the first-clear Casket is granted once; every model clip exists and strike keyframes land
  on damage ticks; the never-keyed bones are never keyed. **v0.15.20+ test policy:** no local gametest runs. Compile, push
  master, and let CI run the suite while you screenshot-check.
- `docs/FROST_GIANT_REFERENCE.md` (the reference doc, in the same style as `ULTRON_REFERENCE.md`), guide and lang entries,
  `/projecthero raid` docs.
- **Screenshots:** use the client harness (flag-file) to capture every unit, Laufey in each phase, Ymir's arm, the rift
  and the braziers, and **show them to the user in Claude Code** before releasing.
- **Release:** tag `v<version>` with the full changelog as the annotation (it publishes to CurseForge, GitHub and
  Discord as multiple messages), verify all three arrived, then `./gradlew copyToModpack`.
