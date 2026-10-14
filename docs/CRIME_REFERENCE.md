# Street Crime (v0.16.2)

Small, frequent, low-stakes crimes that happen on their own near villages. Any player can stop them, and no powers
are needed. Package `com.projecthero.mod.crime`.

## Pieces

| Class | Job |
|---|---|
| `Crimes` | Entry point (`initialize`, called from `ProjectHeroMod`). Registers the four event types and the hooks. Has `start(...)`, `endAll`, and `/projecthero crime`. |
| `CrimeScheduler` | The natural trigger and the two cooldown maps (static; cleared by `ServerStateReset`). |
| `CrimeEvent` | Abstract `EventInstance` shared by every kind. Handles the alert UI, the time limit, crook spawning and roles, contributions, rewards, clean-up and save/load. |
| `MuggingCrime`, `BreakInCrime`, `GetawayCrime`, `ArsonCrime` | The four kinds. |
| `CrimeAI` | Rewrites a Syndicate crook's goals in memory (through `MobGoalsAccessor`) for its role. |
| `CrimeVillages` | Finds the bell (the `MEETING` POI), makes the village name and the "~40 m NE" hint, rings the bell, and finds ground to stand on. |
| `CrimeType` | The kinds, their ids (`mugging`, `break_in`, `getaway`, `arson`) and their event type ids (`crime_<id>`). |
| `CrimeConfig` | `config/projecthero_crime.json` (`VersionedConfig`, v1). |

The crooks are the Syndicate's own `syndicate_thug` and `syndicate_gunman`. They are player-model mobs with Skindex
skins, so there are no new entities, renderers or textures. A crime only changes their goals:

| Role | Behaviour |
|---|---|
| `MUGGER` | Normal thug melee. Its player target goal is replaced by one that only fires within 6 blocks. The hurt-by goal stays. |
| `RUNNER` | Every goal except float and open-door is removed, and so is every target goal. The crime moves him with navigation calls. He never fights. |
| `GUARD` | A pistol gunman on his normal gun AI. The crime walks him back to the runner whenever he has no target. |

Roles are saved on the event, not on the mob. The `ENTITY_LOAD` hook re-applies a reloaded crook's role. A crook whose
crime is gone is discarded on load, so there are no orphans.

## Natural trigger (`CrimeScheduler.tick`)

- Runs every `trigger.checkIntervalSeconds` (10). It looks at every non-spectator player in the Overworld.
- A player's first check is `firstCrimeDelayMinutes` (4) after they are first seen. After that they are skipped while
  their cooldown runs.
- `tryStartNear` looks for the nearest bell within `villageSearchRadius` (96). It gives up when any of these is true:
  - the difficulty is Peaceful
  - the village is on cooldown
  - the bell is not entity-ticking
  - a vanilla raid is running there (`isRaided`)
  - another world event of any kind is within 160 blocks
- If a check finds nothing, that player is checked again 30 s later.
- The kind is a weighted pick from `weightMugging` / `weightBreakIn` / `weightGetaway` / `weightArson` (35 / 30 / 20 / 15).
  A mugging is only picked if a grown villager is within 32 blocks of the bell.
- When a crime starts:
  - the player's cooldown is set to `playerCooldownMinutes` (8)
  - the village's cooldown is set to a random time between `minIntervalMinutes` and `maxIntervalMinutes` (10-20)
- All times are real minutes of play (1200 ticks a minute).

## Common lifecycle (`CrimeEvent`)

- **Start.** The crime is added to `EventSavedData` (the `EventManager.start` 256-block same-type rule is not used).
  Its centre is the scene:
  - the victim, for a mugging
  - the bell, for the other kinds
- **First step.** The first step (the next framework tick with a player within `alertRadius + 32`) calls `begin`,
  which spawns the crooks. Then it announces the crime:
  - every player within `alertRadius` (96) gets a red chat line: "[Crime] Mugging at Oakford! Thugs are cornering a
    villager. (~40 m NE)"
  - a villager scream plays from 12 blocks along the line to the crime, so it sounds like it comes from that side
  - the bell rings (`BellBlock.attemptToRing`), and villagers within 32 blocks run indoors
- **Every step (0.5 s).**
  - A red boss bar shows "Mugging at Oakford - <status>" to players within `alertRadius` of the scene.
  - Once a second, every player more than 12 m from the scene gets the action-bar hint "Crime in progress: Mugging at
    Oakford (~40 m NE)".
- **Framework overrides.**
  - Presence radius is `alertRadius + 32`.
  - Players are never told they are leaving.
  - With nobody present, the crime pauses after `abandonSeconds / 2` and ends after `abandonSeconds` (60). These are
    the new overridable `EventInstance` hooks.
- **Time limit** (per kind): the crooks get away (failed).
- **Ending.**
  - Success: rewards are paid, then `complete`.
  - Failure: a red message, then `abort`. A failed crime has no extra cost.
  - Either way, the live crooks vanish in a puff of smoke, and `release` undoes the crime's effects (victim tag, open
    chest, fires).
- **Server restart.** `SERVER_STARTED` ends every crime that was running. Crooks in chunks that were not loaded are
  caught by the `ENTITY_LOAD` orphan check.

## Kinds

| Kind | Crooks | Win | Lose | Time limit |
|---|---|---|---|---|
| Mugging | 1-3 thugs (`MUGGER`) | every thug down | the victim's hit counter runs out (knocked out) | 180 s |
| Break-in | 2-4 thugs (`RUNNER`) | every crook down or gone, and at least as many down as got away (reward x stopped/total) | more got away than were stopped | 240 s (anyone left gets away) |
| Getaway | 1 runner thug + 1-2 pistol gunmen (`GUARD`) | the runner down | the runner reaches `escapeRadius` (80) from the bell | 240 s |
| Arson | 1 thug with flint and steel (`RUNNER`) | the firebug down and his fires out (or `fireOutGraceSeconds`, 30, later) | he lights `maxFires` (8) | 180 s |

### Mugging

- **The victim** is the villager given to `start`, or a random grown villager within 32 blocks. It is tagged
  `projecthero_crime_victim`.
- **The thugs** spawn 2.5-5 blocks from the victim and target it. Each step re-targets a thug to the victim unless he
  is fighting a non-creative player.
- **The hit counter.** The victim can take `victimHits + victimHitsPerThug * thugs` hits (24 + 8 a thug). The
  `ALLOW_DAMAGE` hook turns each crook's hit on the victim into one step of that counter, and the real damage is
  cancelled. The villager still reacts:
  - it shows the hurt flash (`broadcastDamageEvent`) and plays the hurt sound
  - it takes knockback
  - it gets `HURT_BY` / `HURT_BY_ENTITY` brain memories, so it panics and runs
  
  Damage from anything else still hurts it as normal.
- **Knocked out** (the counter reaches 0):
  - Slowness VII, Weakness II and Regeneration I for `knockoutSeconds` (45)
  - angry-villager particles
  - the crime fails

  The villager is never killed by the crooks.

### Break-in

- **The target** is a random `ChestBlockEntity` within 40 blocks of the bell. Only the block entities of
  already-loaded chunks within 2 chunks are checked. If there is no chest, the target is the bell square.
- **Each crook** starts `approachDistance` (22) +-4 blocks out and goes through three steps:

  | Step | What happens | Next |
  |---|---|---|
  | APPROACH | Walks in at speed 0.85. | LOOT once within 2.5 blocks, or after 25 s. |
  | LOOT | Stands at the target for `lootSeconds` (15). The chest lid opens and leather rustles. | FLEE with a sack (a chest in his off hand). |
  | FLEE | Re-targets a waypoint 12 blocks further from the bell at speed `fleeSpeed` (0.95), wobbling sideways when stuck. | Escapes (vanishes) at `escapeRadius` (72) from the bell. |

- **Hit before he is done.** If a player hit him in the last 2 s:
  - during APPROACH, he runs empty-handed
  - during LOOT, he runs with the sack only if he was more than halfway through
- **Spilled goods.** A crook killed while carrying a sack spills it: 1-3 emeralds, 1-4 bread, and one of iron, wheat,
  carrots, potatoes, books or gold. **Nothing is ever taken from the real chest.** A village chest may be a player's
  storage, and a crime must never cost anybody their things.

### Getaway

- **Loading up.** The runner spawns 3-8 blocks from the bell with a sack and stands at the bell square for
  `lootSeconds` (20). He starts running early if a player hits him.
- **The gunmen.** 1-2 pistol gunmen spawn next to him and use their normal gun AI (bullets you can dodge). If one has
  no target and is more than 4 blocks from the runner, he jogs after him.
- **The run.** The runner uses the same waypoint run as a burglar, at speed `runnerSpeed` (1.0). That is about
  4.5 blocks/s, a little slower than a sprinting player.
- **Stopped.** Kill the runner and his sack spills, the crime is stopped, and the gunmen vanish.

### Arson

- **Wandering.** The firebug walks between random spots 3-22 blocks from the bell.
- **Lighting.** Every `fireIntervalSeconds` (8; the first fire comes after 4 s) he lights a fire within 2 blocks of
  himself, but only on a `safeFireSpot`:
  - the spot is air
  - the floor below is sturdy and not flammable
  - nothing that can burn (`ignitedByLava`), no other fire and no hay is within the 5x4x5 box around it
- **Fires are tracked.** Every fire he lit is removed when the crime ends, either way.
- **`mobGriefing` off.** He lights no real fire. He still makes flame and smoke particles, and the count still goes up.

## Rewards

These go to every online player who damaged or killed a crook. Contribution is the damage taken by crooks (the
`AFTER_DAMAGE` hook) plus 10 a kill (the `AFTER_DEATH` hook).

| Who | Reward |
|---|---|
| Every helper | `emeraldsMin`-`emeraldsMax` (3-6) emeralds, times the share (break-in), at least 1. `experience` (20) XP times the share. Villager gossip `MINOR_POSITIVE +gossipHelpers` (10) with every villager within 48 blocks of the bell. |
| The top helper | `+topHelperBonusEmeralds` (2). Hero of the Village I for `heroOfTheVillageSeconds` (180) (cheaper trades). Gossip `+gossipTopHelper` (25) instead. |

**Iron golems can't solve crimes.** The `ALLOW_DAMAGE` hook cancels any golem hit on a crime crook and drops the golem's target. Golems may still walk up and swing; it does nothing. Guards only target players. The first harness run showed why: a village golem flattened the burglars and the firebug before a player could get there.

If a crook dies with no player involved (a fall, lava...), the crime is still stopped, with the message "the village dealt with it".

## Commands (op)

| Command | What it does |
|---|---|
| `/projecthero crime start <mugging\|break_in\|getaway\|arson>` | Starts one in the nearest village (bell within 96 blocks). With no bell nearby, it uses the spot 10 blocks in front of you. A mugging with no villager about spawns one in front of you. The 96-block spacing rule is not applied. |
| `/projecthero crime end` | Ends every crime (crooks vanish, fires go out). |
| `/projecthero crime trigger` | Runs the natural trigger for you now, ignoring the cooldowns. |
| `/projecthero crime status` | Lists running crimes. |

## Config: `config/projecthero_crime.json`

- **`trigger`** is the server's (it is never reset by a migration):

  | Key | Default |
  |---|---|
  | `enabled` | `true` |
  | `checkIntervalSeconds` | 10 |
  | `villageSearchRadius` | 96 |
  | `minIntervalMinutes` / `maxIntervalMinutes` | 10 / 20 |
  | `playerCooldownMinutes` | 8 |
  | `firstCrimeDelayMinutes` | 4 |
  | `weightMugging` / `weightBreakIn` / `weightGetaway` / `weightArson` | 35 / 30 / 20 / 15 |
  | `alertRadius` | 96 |
  | `minDistanceBetweenCrimes` | 96 |
  | `abandonSeconds` | 60 |

- **Balance sections**, with their keys:

  | Section | Keys |
  |---|---|
  | `mugging` | `minThugs`, `maxThugs`, `victimHits`, `victimHitsPerThug`, `knockoutSeconds`, `timeLimitSeconds` |
  | `breakIn` | `minCrooks`, `maxCrooks`, `approachDistance`, `lootSeconds`, `escapeRadius`, `fleeSpeed`, `timeLimitSeconds` |
  | `getaway` | `minGunmen`, `maxGunmen`, `lootSeconds`, `escapeRadius`, `runnerSpeed`, `timeLimitSeconds` |
  | `arson` | `fireIntervalSeconds`, `maxFires`, `fireOutGraceSeconds`, `timeLimitSeconds` |
  | `rewards` | `emeraldsMin`, `emeraldsMax`, `topHelperBonusEmeralds`, `experience`, `heroOfTheVillageSeconds`, `gossipTopHelper`, `gossipHelpers` |

## Shared-file touches

- **`EventInstance`** has four new overridable hooks: `presenceRadius`, `warnRadius`, `pauseAfterEmptyTicks` and
  `failAfterEmptyTicks`. Their defaults are the framework config, so other events are unchanged.
- **Wiring:**
  - `ProjectHeroMod`: config load, `Crimes.initialize`, and `CrimeScheduler.tick` in the server tick
  - `ProjectHeroCommand`: the `crime` subtree
  - `ServerStateReset`: `CrimeScheduler.clearSessionState`
- **Guide:** chapter `CH_CRIME = 36` (the power chapters now start at 37). `HeroPackGameTests` now expects 37 framing
  chapters.

## Design notes

- **No bosses.** There is no boss, so the shared threat table does not apply.
- **Victim damage.** A mugging is a hit counter rather than real health, so a thug's stone sword can't end it before a
  player 90 blocks away can get there.
- **Ending a failed crime.** A failed crime uses `abort` after its own message. The framework's `fail` would also
  broadcast an empty line.
- **Cooldowns are not saved.** A restart can only bring the next crime a little sooner.

## Tests

`CrimeV0162GameTests` checks:

- config defaults and migration
- the cooldowns
- directions and names
- a mugging spawning and cleaning up (and golem hits doing nothing to a crook)
- a mugging paying the top helper (emeralds, Hero of the Village, gossip)
- a mugging knockout (no real damage, never killed)
- a break-in sack spilling on death
- a getaway escape failing and cleaning up
- arson fire safety and its time-out
- the natural trigger (needs a bell, never on Peaceful, never a mugging without villagers, cooldown set, no stacking)

The natural-trigger test runs in its own `crime_natural` batch.
