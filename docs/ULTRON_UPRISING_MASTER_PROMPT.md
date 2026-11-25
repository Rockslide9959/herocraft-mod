# Ultron Uprising: master prompt

Paste everything under **PROMPT** into a new session to build it. The notes above that line are for you.

## Design notes (the short version)
- **What it is:** a Hero-tier raid built on Stark tech. It sits after the Sentinel Purge and before the Oathbreaker / Apokolips Invasion. It's the "my own creation turned on me" fight for Iron Man players, but anyone can run it.
- **How it starts:** craft an **Ultron Beacon** from a Master Mold Core (Sentinel Purge drop) + an Arc Reactor + a redstone block + netherite scrap. Place it and right-click it. Or use `/projecthero raid start ultron`.
- **The hook:** Ultron is a **network**, not one body. Relay pylons keep repairing his drones and let him jump bodies. Players have to cut the network before they can kill the real Ultron.
- **Arc:** waves of drones → three pylons to destroy → **Ultron Prime** (3 phases) → he jumps to a spare body if any pylon still stands → final phase in the "Ultron Sentry" giant body.
- **Rewards:** a **Vibranium Plating** crafting material (Iron Man armour upgrade), an **Ultron Core**, and a **Mind Stone** if the Infinity-stone plan is still on.

---

## PROMPT

Build **The Ultron Uprising** for Project Hero (Fabric 1.21.1, mod id `projecthero`, package `com.projecthero.mod`, repo at `C:\Users\ethan\OneDrive\Desktop\Coding Projects\Superhero Mod`). It's a new raid event with its own robot enemies and a multi-phase boss, **Ultron**. Release it as the next patch version (check `gradle.properties` and existing git tags first, and bump `version=`).

Read these before writing code:
- `docs/SYNDICATE_REFERENCE.md`, `docs/CARNAGE_REFERENCE.md` and `docs/DARKSEID_RAID_REFERENCE.md` (raid and boss patterns)
- `com.projecthero.mod.sentinel` (closest relative: robot raid, beams, Master Mold)
- `com.projecthero.mod.event` (EventInstance, EventManager, EventBossBar, EventTypes)
- `com.projecthero.mod.syndicate.SyndicateBust` (wave queue, spawn points, rewards chest, save/load)
- `com.projecthero.mod.ironman` (IronManTargeting, IronManAbilityFx, drones, JARVIS dialogue)
- Your memory files for this project, especially the gotchas.

### 1. How it starts
- **Ultron Beacon** item, crafted (shaped): Master Mold Core (`SentinelItems.MASTER_MOLD_CORE`) in the centre, Arc Reactor on top, redstone block below, netherite scrap on the four corners, iron blocks on the sides. Place it as a block. Right-clicking it in Survival starts the event centred on it. The beacon becomes the **Ultron Uplink** core of the arena: it can't be mined while active, and it turns into the reward chest on victory (Horde-block pattern).
- `/projecthero raid start|end|advancetimer ultron`, added to `RaidAdminCommand`'s RAIDS list next to `syndicate` / `sentinel_purge`.
- Never on Peaceful. One active Ultron event per `minDistanceBetweenEvents`.
- **Optional natural trigger** (make it a config flag): a player who has built 3+ Iron Man marks and owns a Stark Fabricator has a small daily chance of JARVIS warning "unauthorised process detected in the Fabricator network". The Beacon then appears in that player's inventory. Default: off.

### 2. The arena
- **No terrain building.** It's fought where the beacon is placed (unlike the Syndicate warehouse).
- **Border:** a 40-block arena with a cyan-red particle ring border, like `HordeRaid.drawBorder`. Fighters are locked in like a horde, and anyone who walks in joins.
- **Relay pylons:** at the start, 3 pylons rise out of the ground on a 22-block ring around the uplink. Each is a block-entity or entity "tower", ~4 blocks tall, with a pulsing red eye and 300 HP each (+100 per extra fighter).
  - While any pylon stands, drones within 16 blocks of it slowly self-repair (2 HP/s).
  - Each pylon shows a thin red beam to the uplink.
  - Destroying a pylon makes an EMP burst that stuns nearby drones for 3 s.

### 3. Enemies (robot mobs; procedural/GeckoLib models, see section 6)
| Unit | HP | Behaviour |
|---|---|---|
| **Ultron Drone** | 30 | Flying, hovers at 4–8 blocks, fires red repulsor bolts (hit-scan like `SyndicateGunfire` / `SentinelAimedShot`, visible beam). Comes in swarms. |
| **Ultron Sentinel Drone** (melee) | 45 | Ground runner, dives in for claw strikes, explodes for 6 damage on death (warn with a beep). |
| **Ultron Heavy** | 120, 10 armour | Walks slowly, shoulder rocket barrage (3 missiles reusing `IronManMissileEntity`, telegraphed), then a ground stomp. |
| **Ultron Sniper Frame** | 40 | Perches on pylons, laser sight 1.5 s (copy the Syndicate sniper), 14 damage. |

- Robots take **+50% from electric/lightning** (Thor, Electrokinesis) and **+25% from Hulk / heavy melee**. They're immune to poison and wither.
- **Waves:** 5, scaled ×(1 + 0.5 × (players − 1)). Waves 1–2 are drones only. Wave 3 adds melee drones. Wave 4 adds a Heavy and snipers. Wave 5 is everything.
- **Gate:** while any pylon stands, the boss phase can't begin. After wave 5 the title says "DESTROY THE RELAY PYLONS".

### 4. The boss: Ultron (three bodies)
**Body 1: Ultron Prime** (player-sized ×1.4; 900 HP + 300 per extra fighter, cap 3000; 12 armour; flies)
- **Repulsor barrage:** 5 quick red beams at different players.
- **Encephalo-beam:** a held face laser that sweeps (telegraph a red line for 1 s, damage 12/s, dodge it).
- **Drone swarm:** calls 4 + players drones.
- **Magnetic pull:** yanks metal-armoured players (any iron/netherite/Iron Man armour) toward him, then punches.
- **Dive bomb:** flies up, slams down with a 6-block shockwave.

**Body jump:** at 0 HP, if any spare pylon is alive, his head "uploads". A red streak flies to a pylon, a new Ultron Prime body assembles there at 50% HP, and the pylon is consumed. With no pylon left, go to body 2.

**Body 2: Ultron Sentry** (the giant, 6 blocks tall; 1500 HP + 400 per extra fighter)
- Slower, heavy hits.
- **Unibeam-style chest cannon** (charge 2 s, wide beam).
- **Arm slam.**
- **Missile rain** (reuse `IronManMissileEntity` with a red tint).
- **Shield phase:** at 50% he goes invulnerable and 3 drones carry the shield. Kill them to break it.

**Final words + death:** he collapses, his head cracks, and the screen-wide JARVIS / FRIDAY line plays: "Ultron has been purged from the network." The uplink turns into the reward chest.

**Dialogue** (rate-limited chat like Kingpin/Carnage): intro, body jump, shield, enrage, death. Write original lines. Don't quote the films.

**Iron Man tie-ins:**
- `IronManTargeting` (Mark III+) locks onto drones and pylons.
- JARVIS (`JarvisDialogue`) comments on pylons ("Three relay signatures, sir").
- Ultron's attacks drain 10% more Iron Man suit energy on hit ("he's in your systems").

### 5. Rewards (in the uplink chest; party scaling via `Valuables.partyScale`)
- **Vibranium Plating** ×(2 + party): a new crafting material.
  - Use it in a smithing table to give an Iron Man armour piece +2 armour and +1 toughness. Store it as a data component and show it in the I-key spec sheet and the tooltip.
  - It also crafts a **Vibranium Shield** variant later (leave that as a TODO).
- **Ultron Core:** a trophy. Placed, it's a decorative block with a slowly rotating red eye.
- **Mind Stone:** guaranteed on the first clear per player. It's a placeholder item for the planned Infinity-stone chain, with no power yet, just tooltip lore.
- Netherite scrap 1–3, diamonds, redstone, gold, emeralds, an enchanted book, XP bottles.
- **Config** `config/projecthero_ultron.json` (VersionedConfig, see `docs/CONFIGS.md`): every HP/damage/count above, the natural trigger flag and the reward counts.

### 6. Models and visuals
- **Ask me first** whether I'm supplying Blockbench / GeckoLib models for Ultron Prime, the Sentry, the drones and the pylon. If I am, wait for them and use them (put them in `assets/projecthero/geo|animations|textures`). If not, build:
  - Drones: procedural meshes (copy the approach of `IronManMissileRenderer` / `TurboBoltRenderer`).
  - Ultron Prime: the player model with a custom silver-and-red texture made with the Node PNG generator in `scratchpad/png_v01425.js` (glowing red eyes and mouth emissive layer).
  - Ultron Sentry: the player model scaled ×3.4 via the `SCALE` attribute (remember `refreshDimensions()` in the constructor).
- **Beams:** use `BeamDraw` (glow into debugQuads first, every core into lightning second; never interleave). All Ultron energy is **red**.
- **Body-jump upload streak:** a red particle comet plus a beam from the body to the pylon.
- **Boss bars:** the event bar shows the waves; each Ultron body has its own bar (`startSeenByPlayer` / `stopSeenByPlayer` pattern, as in `KingpinEntity`).

### 7. Engineering checklist (match the house patterns)
- **Package:** `com.projecthero.mod.ultron` (Ultron, UltronEntityTypes, UltronItems, UltronUprising extends EventInstance, UltronRewards, UltronConfig, UltronCommand, entity/, item/). Client: `com.projecthero.mod.client.ultron`.
- **Registration:**
  - Init from `ProjectHeroMod.onInitialize` next to the Syndicate/Sentinel inits.
  - Event type in `EventTypes`.
  - Renderers in the client init.
  - Creative tab entries.
  - Spawn eggs for every mob.
  - Any static map (locks, cooldowns) must be cleared in `ServerStateReset`.
- **Save/load:** phase, wave, queue, pylon UUIDs, boss UUID, body index, fighters. The event must survive a save mid-raid.
- **Lang:**
  - Every entity, item and block, plus titles, subtitles and the bar.
  - All dialogue keys.
  - A **guidebook chapter** in `HeroPackGuide`: add `CH_ULTRON` and bump `CHAPTER_POWER_BASE`, and update the HeroPackGameTests chapter-count assertion.
- **Docs:**
  - `docs/ULTRON_REFERENCE.md`: numbers, flow, commands, credits.
  - An "Ultron Uprising" line in the progression notes.
- **Gametests** (new `V0xxxxGameTests`, registered in `src/gametest/resources/fabric.mod.json`):
  - wave-mix scaling
  - pylon gate (boss can't start with a pylon alive)
  - body-jump consumes a pylon and restores 50% HP
  - damage multipliers (lightning ×1.5)
  - rewards contain Vibranium Plating ×(2 + party), and the Mind Stone only on a first clear
  - config migration
  - the Vibranium smithing upgrade adds +2 armour
- **Gotchas:**
  - Use mock players: survival mode, `Difficulty.HARD` for Monster tests.
  - `SCALE` mobs need `refreshDimensions()`.
  - Forced long-range particles need `sendParticles(player, opts, true, …)`.
  - Watch for CRLF files when editing with Node scripts.
  - The flaky test `graveboundbosspowergametests.bossabilitieshurtandmarkaplayerspet` passes on re-run.
- **Visual check:**
  - Write a temp client harness: copy `scratchpad/V01426DebugHarness.java.txt`, enable it with a `run/<ver>.flag` file and mark it `TEMP-HARNESS`.
  - Screenshot the uplink + pylons, each enemy, every Ultron move, the body jump, the Sentry, the shield phase and the reward chest.
  - **Show me the important screenshots in the chat (SendUserFile).**
  - Remove the harness before committing.
- **Release:**
  - Run a full `./gradlew build` (all gametests must pass).
  - Commit, write a detailed changelog in the tag annotation (Discord splits long messages automatically), then `git tag -a v<ver> -F <file>` and `git push origin master --tags`.
  - Watch the workflow until it finishes, then confirm all three: CurseForge, GitHub Release and Discord (HTTP 204 for every message). If a run fails on a flaky test, re-run it.
  - Run `./gradlew copyToModpack`, then check the test modpack folder has only one projecthero jar.
  - Update the memory files.

### 8. Ask me first, before building
- Am I supplying models for Ultron Prime, the Sentry, the drones and the pylon (and should you wait for them)?
- Should the Mind Stone reward and the natural JARVIS trigger be in, or left out for now?
- Should Vibranium Plating also apply to non-Iron Man armour?
