# Sentry & the Void (v0.17.5) — reference

Design: `docs/SENTRY_VOID_DESIGN.md` (approved 2026-11-15). Code: `com.projecthero.mod.sentry` (server/common),
`com.projecthero.mod.client.sentry` (client). Every number lives in `SentryConfig`. Tests: `SentryGameTests`,
`VoidTakeoverGameTests`. Assets: `node scratchpad/gen_sentry_v0175.js` (all original pixel art, no borrowed skins);
lang: `node scratchpad/lang_sentry_v0175.js` (key by key). Films/screens: `scratchpad/SentryHarness.v0175.java.txt`
-> `docs/screenshots/v0175/sentry/`.

Key `sentry`. Hero-Tier Primary power (claims the Primary slot like every grant; `projectheroUniquePowers` applies).
Not from random serums (`PowerGrants.NOT_FROM_SERUMS`); the operator grant still works.

## Getting it

**Sentry Serum** (`sentry_serum`, epic, stacks to 1, drink 32 ticks, leaves a bottle). Only source: the **O.X.E. serum vat**
(`oxe_serum_vat`, unbreakable, facing + `open`) in The Vault's vault room, `VaultBuilder.SERUM_VAT` = local (0, F3+1, 21),
on a gold plinth between the arena door and the three Dungeon Vaults. It opens with the Dungeon Vaults when Kross dies
(`VaultClearing.unsealVaults` -> `SerumVatBlock.open`), or when used with no Kross within 96 (marks the dungeon cleared, same
rule as the seals). Open, it gives **one serum per player** (`SerumVatBlockEntity` keeps the UUIDs). No recipe, no loot table.

## Meters (0..100, synced in `SentryState`, attachment `projecthero:sentry_state`)

| | Sun (gold **Gauge**) | Void (black **Hairline** under it) |
|---|---|---|
| Direct sunlight | +6/s | -1/s (not in Void Form) |
| Daytime shade | +2/s | 0 |
| Night, under the sky | +0.5/s | +0.25/s |
| Dark (no sky, block + sky light < 8; Nether/End) | 0 | +0.5/s |
| Calm (hold V) | | -6/s |
| Void Form | | +1/s |
| Heavy hit taken (>= 8 after the cut) | | +0.4 per point, max 6 a hit |
| Kill (player) | | +2 (+6) |
| Sunbeam | -5/s | +0.5/s |
| A Million Suns | -60 | +25 |
| Molecular Grip tear | (grip 10) | +5 |
| Unmake | -8 | +3 |
| Healing Light | -20 | -8, -1 per other healed |
| Void Form moves | | Lash +1, Erase +6, Grasp +2, Rift +5, Nightfall +6, Step +1 |

Sun-fed healing: 1 HP every 40 ticks while Sun > 0 (0.5 Sun each). Creative: no costs, the Void never climbs.

| Void | Effect |
|---|---|
| 50 | whispers (soul sand valley mood, every 10-25 s), black veins creep from the screen edges, the suit's emissive glow flickers dark (20-65% of frames) |
| 75 | 12% of Sentry attack presses (R, Shift+R, G, Z, Shift+Z) misfire into a Void move (Lash / Grasp / Rift); client aim drifts up to ~0.8°/tick toward the nearest living thing within 12 ahead; veins thicker + branching |
| 90 | heartbeat (warden heartbeat every 24 ticks) + darkness pulse; client sky dims to 45%; every hit leaves a burnt shadow |
| 100 | the takeover (not creative / spectator; inside spawn protection it holds at 99) |

## Body

| | Sentry | Void Form / takeover |
|---|---|---|
| Max health | +30 | +40 |
| Damage taken | -70% | -75% |
| Melee | +12 | +16 |
| Speed | +30% | +30% |

No hunger. No fall damage while flying, diving or in the takeover. (Kryptonian: -80%, punch 32, Solar Flare 120.)

## Sentry kit (H = power selector only; N unused)

| Key | Move | Numbers |
|---|---|---|
| R | Golden Strike | 24, 3rd hit 30 + shockwave 14 (r 4.5); reach 4.5; 8-tick cd; combo window 30 ticks; 2 Sun |
| Hold Shift+R | Sunbeam | 6 every 4 ticks + 2 s fire, 32 blocks, max 120 ticks, 60-tick cd after; 5 Sun/s; `BeamClash.report(power 1.4, gold)` every tick; renderer checks `BeamClashClient.hides` |
| G (hold) | Molecular Grip | lift within 20 (held 4 blocks ahead); release = throw (14, 2.4 b/t); hold 30 ticks = tear 55; max 100 ticks; 10 Sun; 80-tick cd. Bosses (>= 300 HP or > 4 tall) are held in place, not lifted |
| Shift+G | Unmake | 3x3 on the looked-at face (24), into light, no drops; never bedrock / unbreakable / block entities / fluids / portals; respects `abilityTerrainDamage` and `mayInteract`; 8 Sun; 40-tick cd |
| Z | Sun Dive | air only; 2.6 b/t dive (max 60 ticks), crater 32 in r 5; 12 Sun; 120-tick cd |
| Shift+Z | A Million Suns | 60-tick charge (hovers), then 140 at centre -> 70 at r 12, knockback, 4 s fire; 60 Sun; 1200-tick cd |
| C | Healing Light | r 10: self, squadmates, own pets, villagers, golems: +8 HP, harmful effects cleared; 20 Sun; 240-tick cd |
| Shift+C | Sentinel Shield | r 5 dome, 120 ticks, discards non-friendly projectiles; 15 Sun; 400-tick cd |
| X | Flight | toggle, 22 b/s (44 sprint), vanilla mayfly; ends on landing |
| 2x X (7 ticks) | Sonic Boom | 18 blocks in 8 ticks, 14 to each thing passed once; 5 Sun; 80-tick cd |
| Hold V | Calm | -6 Void/s, no attacks (moves + melee blocked), floats |
| Shift+V | Let It Out | Void >= 50 and not locked -> Void Form |

## Void Form kit (no Sun cost)

| Key | Move | Numbers |
|---|---|---|
| R | Shadow Lash | 20 to all in a 100° cone out to 6; 12-tick cd |
| Shift+R | Erase | 100 to the looked-at creature (24); a kill leaves a burnt shadow; 400-tick cd |
| G | Grasp | up to 5 within 14 dragged in, 6 each; 120-tick cd |
| Z | Void Rift | black hole at the look point (24) for 80 ticks, pulls within 7, 6 every 10 ticks; 360-tick cd |
| Shift+Z | Nightfall | dome r 10 for 200 ticks: enemies Darkness + Weakness (+ short Blindness), you Regeneration II; 800-tick cd |
| X | Shadow Step | teleport to a looked-at spot (40) with raw brightness <= 7; 60-tick cd |
| V / Shift+V | Return | only below 90 Void |
| C | — | |

## The takeover (`VoidTakeover`)

- Starts at 100 Void: Void Form, flying (mayfly), Void held at 100, keys dead (`SentryAbilityManager`), own attacks / uses /
  block breaking refused (`SentryMod` callbacks). Others within 96 get a chat warning.
- **Targets everything**: `canHit = HeroTargets.canHarm(null, target)` minus the body (squadmates, pets, villagers, players;
  never spectators / creative / armour stands). `Squads.areAllies`, `shields` and `protectsPet` are broken for a body in the
  takeover, both ways, like a rampaging Hulk. Server PvP off still stops player damage (vanilla).
- Brain: retarget every 10 ticks — the last helper (60 ticks) first, else the nearest living within 24. Flies to 3 blocks
  (0.55 b/t). Fists 18 every 10 ticks within 3.5. Moves by range on its own cooldowns: Lash (<= 6, 16 ticks), Erase
  (<= 20 + sight, 240), Grasp (6..14, 100), Rift (<= 24, 300), Nightfall (2+ victims in 10, 600). Nothing near: toward the
  nearest player within 128, else a village meeting POI within 128, else a random 24-block heading (every 100 ticks),
  hovering 4 above the ground. Kills are absorbed: +4 HP.
- **World consumption** (`VoidMatter`): ring radius 3 + 0.25/s, max 16; up to 8 blocks a tick (24 tries) on the surface
  under the ring (within 10 of the body's height). Block -> `void_matter` (records first) -> air after 30-40 ticks
  (scheduled tick, no neighbour shape updates so torches/flowers don't pop). Never: bedrock & unbreakables, any block
  entity (all containers skipped), fluids, portals/frames, spawn protection, `!mayInteract`, unloaded chunks.
- **Restore**: per-dimension SavedData `projecthero_void_matter` (pos, block state, owner, dueAt). On end, the owner's
  entries are due in 1200 ticks; 24 a tick come back into air / void matter / replaceable only, loaded chunks only (an
  unloaded one waits). Orphans (owner offline or not in a takeover: a crash) are adopted every 100 ticks.
  `projectheroVoidGriefing` (default false): nothing recorded, nothing returns (entries of that owner are dropped at end).
- **Sky** (client only): `VoidTakeoverClient.skyStrength` = full within 24 of a takeover, gone at 72; sky + fog colour to
  near-black, fog in to 40 blocks; >= 0.5 the client's day time reads as midnight (`SentryMidnightMixin`). 0.45 around
  a Sentry past 90 Void (no midnight). World time never changes.
- **View** (owner's client): forced third person (restored after), movement/jump/sprint/sneak/attack/use keys released
  every tick, body faces the synced `takeoverTarget`, black bleed + gold rim, "THE VOID HAS YOU", countdown, rooms/grip.

## Getting control back

1. Nothing for 600 ticks (`mindLockedUntil`).
2. **Shame Rooms** (`ShameRoomsScreen`, client; `ShameRooms`, server). 3 rooms, 45 s each. Light bar starts 30; a light
   click +9/(1+0.15·room); a darkness shape touching the cursor drains 22·d/s. Lights: max(3, 6-room), speed (25..45)·d px/s.
   Shapes: 2 + room + floor(d), speed (40..65)·d px/s. d = (1 + 0.32·room) · (1 + min(1, minutes·0.25)) · (0.6 + 0.4·grip/100).
   Server accepts a win only for the current room, with the mind open, >= 80 ticks into the room and >= 5 clicks; a loss:
   room - 1 (min 0) and the mind shut 400 ticks more. Esc closes it; it reopens after 5 s.
3. **Outside help**: hold right click on the body with an empty hand within 6 (`UseEntityCallback`): grip -2 per
   interaction (x1.2 by day, x0.8 at night); the Void targets the helper. Grip regains +0.25/s by day, +1/s at night.
   Grip 0 -> the current room breaks open, grip back to 60.
- **Win** (room 3): golden flash (`THROWN_OUT` fx), title, kneel 60 ticks, Void 40, Shift+V locked 6000 ticks.
- **Death**: ends it (`AFTER_DEATH`), Void 60. **Logout / dimension change / server stop**: ends it (stop: on next join),
  Void 60, world scheduled back.

## Files outside the packages

`ProjectHeroMod`, `AbilityRouter` (context after the Hulk + tick), `HeroTiers` (4), `PowerGrants` (4), `HeroCommand`,
`ProjectHeroCommand`, `ServerStateReset`, `ModCreativeTab`, `HeroIdentity`, `Squads` (takeover breaks squad protection),
`SupplyTargetDefaults` (energy cell -> Sun; cooldown reset), `VaultBuilder` (vat), `VaultClearing` (opens the vat),
`HeroPackGuide` (new chapter `CH_SENTRY` = 79, power chapters now start at 80; `SENTRY_CONTROLS`, `VOID_CONTROLS`).
Client: `ProjectHeroModClient`, `DirectionalFlight`, `FlightFeel`, `FlightPoseHelper`, `PowerInfoScreen`, `SquadScreen`,
`PlayerModelMixin`, `SuitFirstPersonArmMixin`, `HumanoidModelMixin`, `ClientLevelMixin`, `FogRendererMixin`, new
`SentryMidnightMixin`, `ProjectHeroJeiPlugin`. Gametests: chapter count 79 -> 80 in `HeroPackGameTests` and
`MiraculousTurtleHorseGameTests`.

## Static state

All session maps key by player UUID only (combo, beam / grip keys, dives, dash hits, brains, zones store UUID + dimension
key + Vec3); cleared by `SentryMod.clearSessionState` from `ServerStateReset`. The eaten world is SavedData, never static.
