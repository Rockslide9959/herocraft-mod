# The Ultron Uprising (v0.15.12)

A Hero-tier raid built on Stark tech: "my own creation turned on me". Ultron is a **network**, not one body -- relay
pylons repair his robots and give him spare bodies, so players cut the network before they can finish the real Ultron.

**Progression:** after the Sentinel Purge (its Master Mold Core builds the beacon), before the Oathbreaker / Apokolips
Invasion. Anyone can run it; Iron Man players get the most out of it (Vibranium Plating, JARVIS, lock-on).

Code: `com.projecthero.mod.ultron` (server) and `com.projecthero.mod.client.ultron` (client). Tuning:
`config/projecthero_ultron.json` (`UltronConfig`, VersionedConfig v1). Gametests: `UltronV01512GameTests`.

## 1. Starting it
- **Ultron Beacon** (block, shaped recipe `ultron_beacon`):
  ```
  N A N      N = netherite scrap, A = Arc Reactor (projecthero:arc_reactor)
  I M I      I = iron block, M = Master Mold Core (Sentinel Purge reward)
  N R N      R = redstone block
  ```
  Place it, right-click it in Survival (never on Peaceful). It becomes the **uplink** (`ACTIVE=true`: red, light 12,
  unminable) and the arena is centred on it. One uprising per `minDistanceBetweenEvents` (256).
- `/projecthero raid start|end|advancetimer ultron` (op). `start` places an uplink at your feet and puts you 2 blocks
  south of it. `advancetimer`: countdown -> wave 1, wave -> cleared, breather -> next, pylon gate -> all pylons killed,
  Prime -> reserve pylons removed and he falls, Sentry rise -> Sentry, Sentry -> killed, purged -> victory.
- **Natural trigger** (`trigger.naturalTriggerEnabled`, **default false**): once a minute every Tony Stark with
  `minimumMarks` (3) built marks rolls `dailyChance / 20` (0.05 a day). JARVIS: "an unauthorised process is running in the
  Fabricator network" and an Ultron Beacon appears in their inventory. Marks 2+ are Fabricator-built, so "3 marks built"
  stands in for "owns a Stark Fabricator".

## 2. The arena
- Radius **40** round the uplink, edge drawn every half second as alternating red / cyan dust (forced long-range).
  Red-tinted sky (`ZombieRaidNetworking.pushSky`, 0x3A0808).
- Everyone eligible inside at the start is a fighter; anyone who walks in joins; a fighter who leaves (up to 48 past the
  edge) is pulled back in. A fighter who dies is **down** until they walk back in; all fighters down at once = **defeat**
  (the uplink burns out -- block removed). `end` (abort) just switches the uplink back off.
- **Relay pylons** (`UltronPylonEntity`, 1.4 x 4.2, no AI, never moves): 3 on a 22-block ring, rising out of the ground
  over 2 s. Health 300 + 100 per extra fighter, 4 armour. Red pulsing eye at 3.75, thin red beam to the uplink.
  - Ultron's units (not the bosses) within **16** blocks of a standing pylon repair **2 HP/s**.
  - Destroyed: an EMP (cyan burst) stuns every robot within **12** blocks for **3 s**; JARVIS "Relay down".
  - Snipers perch on top of them; Iron Man's targeting locks onto them (they're `Monster`s).

## 3. Enemies (player-model robots, Skindex skins)
| Unit | HP | Armour | Behaviour |
|---|---|---|---|
| Ultron Drone | 30 | 2 | Flies 4-8 up circling at 7 blocks; raises a palm 10 ticks, then a hit-scan red bolt (4 dmg, 32 range) every ~2-3 s. |
| Ultron Sentinel Drone | 45 | 4 | Ground runner (0.34 speed), leaps in, 5-damage claws. Destroyed: beeps 3x over 0.9 s then explodes (6 dmg, 3.5 radius, no terrain). |
| Ultron Heavy (x1.3) | 120 | 10 | Slow walker, 9 melee. 6-32 blocks: arms up 1 s (shoulder sparks, rising beeps) then 3 red homing missiles (6 / 3 splash) every ~7 s. Under 3.5 blocks: stamp, 4-block shockwave, 8 dmg + knock-up. |
| Ultron Sniper Frame | 40 | 2 | Perched on a pylon (drops when it dies). Laser sight 30 ticks (1.5 s), then a 14-damage shot along the line. Breaking line of sight cancels it. |

- **Damage taken** (`UltronCombat.multiplier`): lightning damage type or an attacker with Electrokinesis active
  **x1.5**; a Hulk, or any direct non-projectile hit of 12+ **x1.25**. Immune to poison, wither (and regeneration).
  No fall damage. Ultron's robots and missiles never hurt each other.
- **Waves**: 5, scaled `round(base x (1 + 0.5 x (fighters - 1)))`, cap 30 alive, 4 arrivals per framework tick.
  Solo: (drones / sentinel drones / heavies / snipers) w1 5/0/0/0, w2 7/0/0/0, w3 5/4/0/0, w4 5/3/1/2, w5 6/4/2/2.
  6 s countdown, 8 s breathers. Drones arrive from 10 up on a 12-34 ring; ground units on the ring.
- **Gate**: after wave 5, any pylon standing -> "CUT THE NETWORK" (bar: pylons standing). A trickle of drones every
  15 s while they stand. Prime only comes when all are down (`UltronUprising.tryStartBoss`).

## 4. Ultron
**Body 1 -- Ultron Prime** (x1.4, flies). 900 HP + 300 per extra fighter, cap 3000; 12 armour / 6 toughness; own boss
bar. On arrival he re-raises **2 reserve pylons** at the relay sites (150 HP each solo) -- his spare bodies.
Moves (every ~2.5-3.75 s, x0.6 when enraged at 33%):
- **Repulsor barrage**: 5 hit-scan red beams (5 dmg each), one every 3 ticks, spread across the fighters.
- **Encephalo-beam**: 1 s red telegraph line from his face, then a 2.5 s, 70-degree sweep; 12 dmg/s (3 every 5 ticks)
  to anything on the line.
- **Drone swarm**: 4 + fighters Ultron Drones (not while 10+ drones are near).
- **Magnetic pull** (phase 2, below 66%, only if someone near wears iron / chainmail / netherite / Iron Man armour):
  yanked to him for 0.7 s, then a 10-damage punch with knockback.
- **Dive bomb** (phase 3, below 33%): up for 0.9 s, then straight down at the target -- 6-block shockwave, up to 14 dmg.

**Body jump**: at 0 HP, if any pylon of his uprising stands, he does not die: the body breaks up, a red comet + streak
beam flies to the nearest pylon over 1.5 s (invisible, invulnerable meanwhile), the pylon is **consumed** (no EMP) and he
rebuilds on top of it at **50%** HP (`bodyJumpHealth`). With no pylon left he falls (2 s death), the remaining network
collapses, and after 3 s a red column rises from the uplink:

**Body 2 -- Ultron Sentry** (x3.4, ~6.1 blocks, slim skin). 1500 HP + 400 per extra fighter, cap 4000; 14 armour / 8
toughness; slow (0.2), 12 melee.
- **Arm slam** (close): 1.1 s fists-up telegraph, 16 dmg in a 4.5-block circle in front, big knock-up.
- **Chest cannon**: 2 s charge (light gathers into the chest, last 0.6 s shows the locked red line), then a 1.25 s wide
  beam down the locked line: 6 dmg every 5 ticks within 1.2 blocks of it, 40 range.
- **Missile rain**: 8 red missiles (7 / 4 splash) dropping from 22 up round random fighters.
- **Shield phase** at 50%: invulnerable, a red shell; 3 shield drones orbit him with red tethers. Kill all three ->
  shield breaks, he **staggers 4 s** taking x1.5 damage. Enrage at 25%.
- **Death**: 3.5 s collapse, his head cracks twice (red bursts, glass / anvil sounds), then he blows apart. Every other
  robot left is scrapped, the title reads "PURGED -- Ultron has been purged from the network." and JARVIS says it to
  every Tony Stark fighter.

**Dialogue** (chat, rate-limited 8 s per body, original lines): intro, swarm, pull, dive, enrage, jump, jump_done, fall,
sentry_intro, cannon, rain, shield, shield_break, sentry_enrage, death (`boss.projecthero.ultron.say.*`).

**Iron Man tie-ins**: targeting locks drones and pylons; JARVIS lines `ultron_pylons` (start), `ultron_pylon_down`,
`ultron_shield`, `ultron_purged`, `ultron_process` (natural trigger) via `JarvisDialogue.speak`; every Ultron hit on a
suited Tony Stark wears the suit's hull an extra **10%** (`damage.ironManDrainBonus`).

**Aggro (Prime and the Sentry):** v0.15.19: aggro is the shared boss threat table (`BossThreat`, see TARGETING.md) -- whoever is hurting it most takes its attention (damage-built threat halving every 10 s, 20% margin to switch). Prime's old "nearest player every 5 s" re-pick only runs while the table is empty.

## 5. Rewards
The uplink becomes a chest (`UltronRewards`):
- **Vibranium Plating** x (2 + fighters)
- 1 **Ultron Core** (trophy block: a pedestal whose red eye slowly turns; light 7)
- 1-3 netherite scrap; diamonds 6-12, redstone 20-36, gold 12-20, emeralds 12-20 (x party scale x valuablesMultiplier);
  12-20 bottles o' enchanting (x party scale); a level-30 enchanted book. Party scale = `Valuables.partyScale`.
- **Mind Stone**: handed straight to each winner on their **first ever** clear (persistent `ultron_clears` attachment).
  Never in the shared chest.

**Vibranium Plating** (Iron Man armour only): smithing table, base = an unplated Iron Man piece, addition = the plating,
**no template**. Result: +2 armour and +1 toughness on that piece's slot (extra attribute modifiers on the stack's own)
and the `projecthero:vibranium_plated` component; shown in the tooltip and on the I-key spec sheet ("Vibranium plating
n / 4"). TODO: a Vibranium Shield variant.

**Mind Stone** (`MindStoneItem`): off hand = Night Vision + hostiles within 24 blocks outlined gold, in the holder's own
view only (client-side `EntityGlowMixin` decision, never a server glowing flag -- the v0.15.4 privacy rule).
Right-click a mob within 24 blocks: charmed 10 s (`MindStoneCharm`: it targets the nearest hostile within 16, never a
player, then reverts); 30 s cooldown; mobs with 100+ max health resist (3 s cooldown).

## 6. Models and visuals
- All robots: vanilla player model (wide, or slim for the Sentry) via `UltronRobotRenderer`, posed by the synced action
  byte (`UltronModel`), scaled by the SCALE attribute. Emissive layer = `<skin>_eyes.png` (every strongly red texel of
  the skin drawn full-bright).
- Skins (from The Skindex, minecraftskins.com -- ids):
  | Body | Skindex id | File |
  |---|---|---|
  | Ultron Prime | 6870675 | `textures/entity/ultron/prime.png` |
  | Ultron Sentry | 23814202 | `sentry.png` (slim) |
  | Ultron Drone | 23128749 | `drone.png` |
  | Ultron Sentinel Drone | 5830525 | `sentinel_drone.png` |
  | Ultron Heavy | 6722436 | `heavy.png` |
  | Ultron Sniper Frame | 23173990 | `sniper.png` (palette PNG converted to RGBA) |
  Regenerate with `node scratchpad/gen_v01512_ultron_assets.js <root>` (reads the originals from the main checkout's
  `scratchpad/ultron_skins/`). Transparent base-layer texels on real faces are filled from the nearest opaque texel of
  the same face (sentinel_drone head 24, sentry arms 36).
- Pylons: `UltronPylonRenderer` stacks the render-only `ultron_pylon_segment` / `ultron_pylon_head` block models.
- Beams: `UltronBeamPayload` -> `UltronBeamClient`, glow into `debugQuads` first, every core into `lightning` second
  (never interleaved). Missiles: `IronManMissileEntity#withRedTint` (red vertex tint + red dust trail).
- Bars: the event bar (red, notched) shows countdown / waves (+ pylons standing) / the gate / boss stage; Prime and the
  Sentry each have their own `ServerBossEvent` (startSeenByPlayer / stopSeenByPlayer).

## 7. Engineering notes
- Save/load: phase, phase start, wave, wave total, owed per kind, pylon UUIDs, pylon sites, boss UUID, reserve flag,
  fighters, down (`UltronUprising.saveExtra`). Prime saves its body index / enrage, the Sentry its shield state and
  shield drones. Every robot carries its uprising id and discards itself if the uprising is gone (orphan guard).
- Static state: only `MindStoneCharm.CHARMED` (cleared in `ServerStateReset`).
- Guide chapter `CH_ULTRON` = 31 (`CHAPTER_POWER_BASE` = 32).
