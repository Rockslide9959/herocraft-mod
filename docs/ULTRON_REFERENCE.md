# The Ultron Uprising (v0.15.12, made harder in v0.16.1)

> **v0.16.1 (Part A of `docs/ULTRON_CONTAGION_MASTER_PROMPT.md`):** 7 waves, 4 pylons on a 24-block ring with a guard
> shield, tougher units, two new units (the **Lancer** and the **Hijacker**), new boss moves, every Ultron hit **x1.3**,
> and **no damage adaptation** anywhere. `UltronConfig` is VersionedConfig **v2**: the v2 step rewrites every changed key
> in an existing file (section 8). The world-level first-defeat flag (section 9) is the hook for Part B, the Contagion.

A Hero-tier raid built on Stark tech: "my own creation turned on me". Ultron is a **network**, not one body -- relay
pylons repair his robots and give him spare bodies, so players cut the network before they can finish the real Ultron.

**Progression:** after the Sentinel Purge (its Master Mold Core builds the beacon), before the Oathbreaker / Apokolips
Invasion. Anyone can run it; Iron Man players get the most out of it (Vibranium Plating, JARVIS, lock-on).

Code: `com.projecthero.mod.ultron` (server) and `com.projecthero.mod.client.ultron` (client). Tuning:
`config/projecthero_ultron.json` (`UltronConfig`, VersionedConfig v2). Gametests: `UltronV01512GameTests`, `UltronV0161GameTests`.

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
- **Relay pylons** (`UltronPylonEntity`, 1.4 x 4.2, no AI, never moves): **4 on a 24-block ring**, rising out of the
  ground over 2 s. Health **450 + 150** per extra fighter, 4 armour. Red pulsing eye at 3.75, thin red beam to the uplink.
  - Ultron's units (not the bosses) within **16** blocks of a standing pylon repair **3 HP/s**.
  - **Guard shield** (v0.16.1): invulnerable (a faint red shell, synced `DATA_SHIELDED`) while **3+** Ultron units of its
    uprising stand within **8** blocks (`pylonShieldGuards` / `pylonShieldRadius`; pylons and Fist Rockets don't count).
    Prime's reserve pylons are also shielded while he is above 50%. Re-decided every 10 ticks by the pylon
    (`refreshShield`). `kill()` / generic-kill bypass it.
  - Destroyed: an EMP (cyan burst) stuns every robot within **12** blocks for **3 s**; JARVIS "Relay down".
  - Snipers perch on top of them; Iron Man's targeting locks onto them (they're `Monster`s).

## 3. Enemies (player-model robots, Skindex skins)
| Unit | HP | Armour | Behaviour |
|---|---|---|---|
| Ultron Drone | 40 | 2 | Flies 4-8 up circling at 7 blocks; raises a palm 10 ticks, then a hit-scan red bolt (5 dmg, 32 range) every ~2-3 s. |
| Ultron Sentinel Drone | 60 | 4 | Ground runner (0.34 speed), leaps in, 5-damage claws. Destroyed: beeps 3x over 0.9 s then explodes (6 dmg, 3.5 radius, no terrain). |
| Ultron Heavy (x1.3) | 180 | 10 | Slow walker, 9 melee. 6-32 blocks: arms up 1 s (shoulder sparks, rising beeps) then 4 red homing missiles (6 / 3 splash) every ~7 s. Under 3.5 blocks: stamp, 4-block shockwave, 8 dmg + knock-up. |
| Ultron Sniper Frame | 40 | 2 | Perched on a pylon (drops when it dies). Laser sight 30 ticks (1.5 s), then a 16-damage shot along the line, then it **relocates** to another free pylon (a red streak). Breaking line of sight cancels it. |
| **Ultron Lancer** (v0.16.1) | 90 | 8 | Front energy shield (red hex plane, the `LancerShield` render layer): stops ranged hits -- projectiles, explosions, and any hit whose attacker is beyond 4.5 blocks (every beam) -- within 70 degrees of where its body faces, unless they come from steeply above. Melee lands. Lancers advance in a line (slots 2.2 apart across the line of advance) and lunge: 10-tick wind-up, then a lance thrust (10 dmg, reach 4, a narrow cone). The shield drops for **2 s** after each lunge (and while EMP-stunned). |
| **Ultron Hijacker** (v0.16.1, slim) | 50 | 2 | Fast (0.36). Cloaked (synced; vanilla's 15% ghost pass + faint ash = the shimmer, eyes off) until within **6** blocks or hurt. Latches onto a player within 1.9 blocks for up to **4 s** (clings over the right shoulder): **one random ability key** (slots 1-6) jammed for **6 s** (`UltronJam`), a worn Iron Man suit drained **15%** of its capacity. Any hit breaks the latch (5 s cooldown). The Mind Stone / Iron Man highlight reveal it (`UltronClient.revealsCloaked`, client-side only). |

- **Damage taken** (`UltronCombat.multiplier`): lightning damage type or an attacker with Electrokinesis active
  **x1.5**; a Hulk, or any direct non-projectile hit of 12+ **x1.25**. Immune to poison, wither (and regeneration).
  **No adaptation** (v0.16.1 user decision): repeated hits of one type always do the same damage; no per-damage-type
  scaling anywhere.
- **Damage dealt** (v0.16.1): every hit whose attacker or direct source is an Ultron robot (melee, bolts, beams, missiles,
  the Fist Rocket) is **x1.3** (`damage.globalMultiplier`), applied by `LivingEntityUltronDamageMixin` at the top of
  `LivingEntity#hurt` (server side). Numbers in this document are before the x1.3.
  No fall damage. Ultron's robots and missiles never hurt each other.
- **Waves**: **7**, scaled `round(base x (1 + 0.5 x (fighters - 1)))`, cap **36** alive, 4 arrivals per framework tick
  (heavies, snipers, lancers, hijackers first). Solo (drones / sentinel drones / heavies / snipers / lancers / hijackers):
  w1 6/0/0/0/0/0, w2 8/2/0/0/0/0, w3 6/4/1/0/0/0, w4 6/4/1/2/1/0, w5 6/4/2/2/1/1, w6 8/5/2/2/2/1, w7 8/6/3/3/2/2.
  6 s countdown, 8 s breathers. Drones arrive from 10 up on a 12-34 ring; ground units on the ring.
- **Gate**: after wave 7, any pylon standing -> "CUT THE NETWORK" (bar: pylons standing). A trickle of drones every
  15 s while they stand. Prime only comes when all are down (`UltronUprising.tryStartBoss`).

## 4. Ultron
**Body 1 -- Ultron Prime** (x1.4, flies). **1600 HP + 450** per extra fighter, cap **5000**; 12 armour / 6 toughness; own
boss bar. On arrival he re-raises **2 reserve pylons** at the relay sites (**300 HP** each solo = 2/3 of a relay pylon) --
his spare bodies -- **shielded while he is above 50%** (`reserveShieldAbove`).
Moves (every ~2.5-3.75 s, x0.6 when enraged at 33%):
- **Repulsor barrage**: 5 hit-scan red beams (5 dmg each), one every 3 ticks, spread across the fighters.
- **Encephalo-beam**: 1 s red telegraph line from his face, then a 2.5 s, 70-degree sweep; 12 dmg/s (3 every 5 ticks)
  to anything on the line. **Phase 3: it sweeps twice** (across and back, 5 s).
- **Overclock** (v0.16.1, phase 2+, not while overclocked): 12-tick wind-up (fists clenched, sparks), then for **6 s** he
  moves **50% faster** and his barrage fires **8 beams**.
- **Network Override** (v0.16.1): marks the **highest-threat fighter** (`BossThreat.strongest()`, else his target; players
  only, 48 blocks): a red targeting reticle hangs over their head for **1 s** (`UltronSignalPayload.RETICLE`, drawn by
  `UltronJamClient`), then, if he still has line of sight, **every ability key (slots 1-8) is jammed for 4 s** and their
  screen fills with red static (`UltronJam.jam(..., ALL, 80, true)`). Breaking line of sight during the reticle dodges it.
- **Drone swarm**: 4 + fighters Ultron Drones (not while 10+ drones are near).
- **Magnetic pull** (phase 2, below 66%, only if someone near wears iron / chainmail / netherite / Iron Man armour):
  yanked to him for 0.7 s, then a 10-damage punch with knockback.
- **Dive bomb** (phase 3, below 33%): up for 0.9 s, then straight down at the target -- 6-block shockwave, up to 14 dmg.

**Body jump**: at 0 HP, if any pylon of his uprising stands, he does not die: the body breaks up, a red comet + streak
beam flies to the nearest pylon over 1.5 s (invisible, invulnerable meanwhile), the pylon is **consumed** (no EMP) and he
rebuilds on top of it at **50%** HP (`bodyJumpHealth`). With no pylon left he falls (2 s death), the remaining network
collapses, and after 3 s a red column rises from the uplink:

**Body 2 -- Ultron Sentry** (x3.4, ~6.1 blocks, slim skin). **2600 HP + 600** per extra fighter, cap **7000**; 14
armour / 8 toughness; slow (0.2), 12 melee.
- **Arm slam** (close): 1.1 s fists-up telegraph, 16 dmg in a 4.5-block circle in front, big knock-up.
- **Chest cannon**: 2 s charge (light gathers into the chest, last 0.6 s shows the locked red line), then a 1.25 s wide
  beam: 6 dmg every 5 ticks within 1.2 blocks of it, 40 range. **v0.16.1: the beam tracks** -- it turns toward the target
  at **20 degrees a second** while firing (`turnToward`, a slerp capped at 1 degree a tick).
- **Missile rain**: 8 red missiles (7 / 4 splash) dropping from 22 up round random fighters.
- **Fist Rocket** (v0.16.1; target over 6 blocks away, one at a time): a 16-tick wind-up (arm aimed), then his right
  forearm launches (`UltronFistRocketEntity`; the model hides his arm while `armOut`): it homes for up to **2 s** and bursts
  for **18** within 2.2 blocks. **60 HP** -- shoot it down and it breaks up harmlessly. Never saved.
- **Reconstruct** (v0.16.1) at **50%** and again at **25%**: a **6 s** channel (arms flung wide, red light pouring in). He
  calls 4 drones in from the arena edge (`reconstructCallDrones`), and every drone of his uprising within 48 blocks (shield
  carriers aside) flies into his chest; each one that arrives is absorbed and heals **2%** of his max health. JARVIS warns.
  The channel runs through a stagger.
- **Shield phase** after each Reconstruct (so at 50%, and **once more at 25%**): invulnerable, a red shell; **4** shield
  drones orbit him with red tethers. Kill them all -> the shield breaks, he **staggers 4 s** taking x1.5 damage. Enrage at
  25%.
- **Death**: on the world's **first** clear (the Sentry of a running uprising) his last line is **"You can't kill an
  idea. I'll see you soon."** (`say.death_first`) and `UltronWorldState` records the time (section 9); otherwise "There
  is... no... upload...". 3.5 s collapse, his head cracks twice (red bursts, glass / anvil sounds), then he blows apart. Every other
  robot left is scrapped, the title reads "PURGED -- Ultron has been purged from the network." and JARVIS says it to
  every Tony Stark fighter.

**Dialogue** (chat, rate-limited 8 s per body, original lines): intro, swarm, pull, dive, enrage, jump, jump_done, fall,
sentry_intro, cannon, rain, shield, shield_break, sentry_enrage, death; v0.16.1: overclock, override, fist, reconstruct,
reshield, death_first (`boss.projecthero.ultron.say.*`).

**Iron Man tie-ins**: targeting locks drones and pylons; JARVIS lines `ultron_pylons` (start), `ultron_pylon_down`,
`ultron_shield`, `ultron_purged`, `ultron_process` (natural trigger), `ultron_reconstruct` (v0.16.1) via `JarvisDialogue.speak`; every Ultron hit on a
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
- v0.16.1 (user decision: no Skindex downloads): the **Lancer** (`lancer.png`, wide) and **Hijacker** (`hijacker.png`, slim)
  skins are painted by `node scratchpad/gen_v0161_ultron_units.js [root]` from the shipped `heavy.png` / `sniper.png`:
  Lancer = a bright silver plate ramp with red shoulder pads, chest stripe and arc plate, belt, knee guards and a T-visor;
  Hijacker = a dark gunmetal ramp with glowing red seams and a visor slit, converted to the slim arm layout. The script
  also writes their `_eyes` glow textures and the 32x32 translucent hex `lancer_shield.png`. The Lancer's lance is a
  BeamDraw rod out of its right hand (long while it lunges); the Fist Rocket is the slim player arm in the Sentry skin at
  3.4x (`UltronFistRocketRenderer`) with a thruster flare.
- Pylons: `UltronPylonRenderer` stacks the render-only `ultron_pylon_segment` / `ultron_pylon_head` block models
  (v0.16.1: plus a faint red shell while the guard shield is up).
- Beams: `UltronBeamPayload` -> `UltronBeamClient`, glow into `debugQuads` first, every core into `lightning` second
  (never interleaved). Missiles: `IronManMissileEntity#withRedTint` (red vertex tint + red dust trail).
- Bars: the event bar (red, notched) shows countdown / waves (+ pylons standing) / the gate / boss stage; Prime and the
  Sentry each have their own `ServerBossEvent` (startSeenByPlayer / stopSeenByPlayer).

## 7. Engineering notes
- Save/load: phase, phase start, wave, wave total, owed per kind, pylon UUIDs, pylon sites, boss UUID, reserve flag,
  fighters, down (`UltronUprising.saveExtra`). Prime saves its body index / enrage, the Sentry its shield state and
  shield drones. Every robot carries its uprising id and discards itself if the uprising is gone (orphan guard).
- Static state: `MindStoneCharm.CHARMED` and (v0.16.1) `UltronJam.JAMMED`, both cleared in `ServerStateReset` (a jam is
  also lifted when the player dies). The client's `UltronJamClient` state is cleared on disconnect.
- v0.16.1: `UltronUprising` has 6 unit kinds (`DRONE..HIJACKER`, `KINDS`); `liveCounts` returns 7 entries (the last is the
  bosses); a v0.15.x save's 4-entry owed array still loads. The Sentry also saves its Reconstruct / re-shield progress.
- v0.16.1 jams: `AbilityRouter.handleInput` asks `UltronJam.blocks(player, slot, pressed)` first -- a press on a jammed slot
  is refused (with an action-bar note); releases always pass, so a held ability always ends.
- Guide chapter `CH_ULTRON` = 31 (`CHAPTER_POWER_BASE` = 32); v0.16.1 adds its Lancer and Hijacker sections.

## 8. Config v2 (v0.16.1)
The `reset(2, ...)` step rewrites exactly the keys this balance pass changed, on any file older than v2 (a file with no
`configVersion` counts as v0): `arena.pylons / pylonRing / pylonHealth / pylonHealthPerExtraFighter / repairPerSecond /
reservePylonHealthFraction`, `waves.waveCount / enemyCap / drones / sentinelDrones / heavies / snipers`,
`drone.health / boltDamage`, `sentinelDrone.health`, `heavy.health / barrageMissiles`, `sniper.shotDamage`,
`prime.health / healthPerExtraFighter / maxHealth`, `sentry.health / healthPerExtraFighter / maxHealth / shieldDrones`,
`damage.globalMultiplier`. New keys (the `lancer` and `hijacker` sections, the pylon shield / Overclock / Network Override /
Fist Rocket / Reconstruct / cannon-turn tuning, `waves.lancers / hijackers`) arrive with their defaults like any missing
key. Everything else a server set (the natural trigger, rewards, the arena radius, ...) is kept.

## 9. The first-defeat flag (the hook for Part B, the Contagion)
`UltronWorldState` (`SavedData` on the overworld, `data/projecthero_ultron_world.dat`) holds `ultron_first_defeat_time` --
the overworld `getGameTime()` (ticks) when the Sentry of a running uprising first fell in this world, `-1` if never -- and
`ultron_defeats` (a count). A spawn-egg (unbound) Sentry never counts. Public API:
- `UltronWorldState.firstDefeatTime(ServerLevel)` -> `long` (-1 if never)
- `UltronWorldState.defeatedOnce(ServerLevel)` -> `boolean`
- `UltronWorldState.defeats(ServerLevel)` -> `int`
- `UltronWorldState.recordDefeat(ServerLevel)` -> `true` on the world's first (sets the flag; it never moves after)
- `UltronWorldState.resetForTest(ServerLevel)`
