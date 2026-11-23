# Alarm & stealth framework (v0.17.7) — reference

Design: `docs/STRUCTURES_V0177_DESIGN.md` §1. Code: `com.projecthero.mod.alarm` (+ `.block`, `.network`),
client `com.projecthero.mod.client.alarm`. Tests: `AlarmGameTests` (+ the alarm checks in `VillainHideoutGameTests`).
Assets: `node scratchpad/v0177/gen_alarm_v0177.js`; lang: `scratchpad/v0177/alarm_lang.json` via `lang_add.js`.

## How a place plugs in (wave-2 checklist)

1. **Profile** — once, from your feature's `initialize()`:
   ```java
   AlarmProfiles.register(new AlarmProfile("sky_fortress", 4, 0x6FA8DC,
           Component.translatable("structure.projecthero.sky_fortress"),
           (level, spot, random) -> MyGuards.reinforcement(level, spot, random), // returns the mob, already added
           SoundEvents.BELL_BLOCK, 0.6f));                                     // siren, pitch (loops every 2 s on Alert)
   AlarmProfiles.registerStructure(ProjectHeroMod.id("sky_fortress"), "sky_fortress");
   ```
   `tier` sets the wave size: 2 / 3 / 4 / 6 mobs for tiers 1-4, +1 per extra player in the zone, max 10
   (`AlarmProfiles.waveSize`). Two waves per alarm (`AlarmProfiles.WAVES`). A null reinforcer = no waves.
2. **Zone** — automatic for a registered structure: the first time a player stands inside a start's bounding box
   (`AlarmZones.find`, every 20 ticks per player) a zone is made from that box (id = the box's min corner). For anything
   built on a live level (gametests, a harness, `buildAll`) register it yourself:
   `AlarmZones.get(level).register(boundingBox, "sky_fortress")` (call `removeWithin(box)` first on a rebuild).
   Nested boxes: the smallest containing box wins (`zoneAt`).
3. **Blocks** — just place them in the structure; the zone scans each loaded chunk column of its box once for them
   (`AlarmZones.scan`), and blocks placed later in a live level file themselves (`onPlace` -> `noteBlock`):

   | Block | Id | Place it | Notes |
   |---|---|---|---|
   | Alarm Panel | `alarm_panel` (`facing`, `style`, `sabotaged`, `alert`) | where a runner can stand next to it | Unbreakable. Runner within 2.6 blocks -> Alert. Hold right-click 3 s while Calm -> sabotaged for good. |
   | Alarm Lamp | `alarm_lamp` (`style`, `lit`) | ceilings / walls | Lit while Alert (light 13). Breakable. |
   | Blast Door | `blast_door` (`style`, `open`, `locked`) | fill a doorway (connected panels = one door) | Unbreakable. `locked=true`: keycard (or a cleared wave) only. Unlocked doors shut on Alert, open when a wave dies / on calm. Never shuts on an entity. |
   | Security Camera | `security_camera` (`facing`, `live`) | on a wall, `facing` = out of the wall | Sweeps ±50° over 8 s, 25° down; 1 s of a player in its 50° / 12-block view (sneak/invis/dark rules) trips it. |
   | Laser Tripwire | `laser_tripwire` (`facing` any of 6, `live`) | in the corridor, `facing` = beam direction | Beam to the first collision block (max 12). Emitter in block y+1 = knee height (jump it), y+2 = head height (crouch under). |
   | Spawn point | `alarm_spawn_point` | the feet position where waves arrive | Invisible, no collision, replaceable. No spawn points: waves come round live panels, then lamps, then 8-14 blocks from the intruders. |

   `style` is `tech` (steel, red lights) or `ancient` (carved stone, rune glyphs, braziers) — same behaviour.
   On a `HideoutCanvas`: `alarmPanel / alarmLamp / blastDoor / camera / laser / spawnPoint`, and
   `CrawlWing.panel / door / lampAt(c, theme, ...)` for a styled one.
4. **Sensors** — anything can raise the alarm:
   `AlarmDirector.raise(level, pos, AlarmDirector.Cause.SENSOR, player)` (finds/creates the zone; ignored when the
   sensor is dead). A sensor is **wired to the nearest Alarm Panel in its zone** (`AlarmZone.wiredPanel`); it is live
   while that panel is (`AlarmZone.sensorLive(level, pos)`) — show that on your block if you can (the cameras / lasers
   refresh a `live` property every second; extend `SensorBlockEntity` to get that for free). The Temple's gong tripwire
   = a block that calls `raise(..., SENSOR, ...)` when struck/powered. The v0.17.5 `hideout_alarm` tripwires already do.
   Use `Cause.SCRIPT` for scripted alarms (no wiring check).
5. **Lamps of your own** (braziers, warning lights): `AlarmBlocks.registerLamp(myBlock, MyBlock.LIT)` at start-up —
   any registered block with that boolean is lit on Alert.
6. **Guards** — any mob tagged `projecthero.alarm_guard` (`AlarmGuards.makeGuard(mob)`) or the v0.17.5 hideout tag is a
   guard; bosses (`HideoutBoss`) never are. Inside a zone an unalerted guard loses its `NearestAttackableTargetGoal`s
   (`AlarmGuards.pacify`) so it only fights after seeing you, being hit, or an Alert. Keep player-model mobs with
   skins (CLAUDE.md); roles are only a tag + behaviour.
7. **Roles** — `AlarmGuards.setRole(mob, GuardRole.X)` **before** the mob is added in worldgen (a WorldGenRegion
   saves an added entity straight into the chunk; `HideoutGuards.spawn(..., alarmRole)` / `HideoutCanvas.guard(...,
   role)` do it right). Sets the tag `projecthero.role.<name>`, a name tag, and the kit:

   | Role | Kit | Behaviour |
   |---|---|---|
   | `CAPTAIN` | gold helmet, +50% health | Drops the zone's Keycard on death (once). Guards within 8 deal +20% (melee and projectiles, `LivingEntityAlarmDamageMixin`). One per place. |
   | `RUNNER` | +30% speed, no armour | Once any guard is alerted, paths (speed 1.2) to the nearest **live** panel and drops its target; reaching it raises the alarm. No live panel = fights. |
   | `MEDIC` | — | Every 2 s heals the most-hurt guard within 8 by 4. |
   | `SHIELD` | shield in the off hand | 70% of damage from within 60° of its front is blocked; an axe hit or a 10+ hit from the front knocks the shield away for good. |
   | `SNIPER` | +24 follow range | While it can see its player target within 40: red laser line (client), a 3.2 b/t arrow (~9) every 2.5 s, laser brighter in the last second. |
8. **Vault hook (Ghost)** — nothing to do: `HeroVault.open` calls `AlarmGhost.claim(level, pos, player)`. A Hero Vault
   inside a zone that was **never** Alert gives one extra roll (once per player per zone) and `projecthero:ghost`.
   If your place's vault is not a `HeroVault`, call `AlarmGhost.claim` yourself and roll again when it returns true.
9. **Keycards** — `AlarmKeycards.create(zone)` makes one (custom data `ZoneId`, `Place`, `X`, `Z`). Using it on a
   Blast Door of that zone unlocks and opens the whole connected door (`useOnDoor`), even during an Alert. A card
   with no `ZoneId` (creative) opens any door. Fortress Pass-style items for other mechanics can reuse `zoneOf(stack)`.

## States and timing

- **Calm -> Suspicious**: any guard's meter > 0.05, or any guard alerted (fighting) while not Alert.
- **Spotting** (`AlarmVision`): 70° cone round the head's yaw, 16 blocks (sneak 8, invisible 4, dark — raw light < 7
  at the player's eyes — halves again), clear line of sight. Meter fill per tick: 1/6 point-blank to 1/40 at the edge;
  walking (not sneaking) within 3.5 blocks is heard at 1/50 even outside the cone; drains 1/120 per tick. Meter >= 0.15
  shows "?"; full = alerted ("!"): the guard targets you and **shouts** (guards within 12 join in). A guard with a player
  target (hit, or pulled in by its friends' `HurtByTargetGoal`) counts as alerted. Suspicious guards turn to look,
  above 0.6 they walk toward you.
- **Alert** (`AlarmDirector.raise`): everAlert = true, Blast Doors shut, lamps lit, panels' screens red, every guard
  targets the nearest intruder, wave 1 spawns; siren every 2 s while waves are out. A wave's last mob dying opens the
  doors (and unlocks keycard doors), wave 2 follows 5 s later. After wave 2: 60 s (`AlarmZone.CALM_DOWN_TICKS`) with no
  alerted guard seeing a player -> `calm`: Calm, lamps off, doors open, guards forget, sabotaged panels stay dead.
- Zones tick (every 2 ticks) only while a player is within 8 blocks of the box or the zone is Alert.

## Persistence and statics

- `AlarmZones` is per-dimension SavedData (`data/projecthero_alarm_zones.dat`): box, profile, state, everAlert, wave,
  doorsShut, panel / lamp / door / spawn positions, keycard-opened doors, scanned chunk columns, Ghost takers.
- Per-guard meters, alerted set, guard list, wave mobs, sabotage progress, sniper aim: transient fields on the
  `AlarmZone` object (owned by the level's SavedData — no static maps, nothing for `ServerStateReset`).
- Statics: `AlarmProfiles` (profiles + structure ids) and the lamp registry — start-up descriptions only.
  Client: `AlarmClient` (state, entity ids to marks, laser id triples), cleared on disconnect / after 2 s of silence.

## Network

`AlarmStatusPayload` (S2C, every 4 ticks to each player inside a zone, once with state -1 on leaving): zone state,
marks as `entityId * 4 + (1 = "?", 2 = "!")` for guards within 48, sniper lasers as `[sniperId, targetId, bright]`.

## Client

- HUD eye (`AlarmHud`): 15x9 pixel eye right of the boss-bar column (or the right edge on narrow screens), shut/grey
  Calm, half/amber Suspicious, open/flashing red Alert; the label is scaled to 0.6 and shrunk further to fit.
- `AlarmWorldRender`: the "?" / "!" over guards (see-through, bobbing) and sniper beams (`RenderType.lightning`).
- `SecurityCameraRenderer` (moving head, red LED, faint view wedge), `LaserTripwireRenderer` (beam).

## Test seams

`AlarmDirector.stepNow(level, zone)`, `raise(level, zone, cause, pos, player)`, `calm(level, zone)`,
`alert(level, zone, guard, player, shout)`, `AlarmSabotage.use(...)`, `AlarmRoles.modifyIncoming / heal`,
`AlarmZones.scan`, `AlarmZone.suspicion(guard) / isAlerted / waveMobCount / quietTicks`.
