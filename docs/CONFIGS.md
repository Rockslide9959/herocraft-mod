# Config files

Every `config/projecthero*.json` file is loaded through one helper, `com.projecthero.mod.config.VersionedConfig`
(v0.14.21). It exists because of the "unbeatable Behemoth" bug: config loaders used to read whatever was on disk, so
a file written by an old release kept its old balance numbers forever, however often the default in the code was
changed.

## The rule

**When you change a balance default, bump that config's version.** Add a step to its `SPEC` naming the keys you
changed:

```java
public static final VersionedConfig<TitanConfig> SPEC = VersionedConfig.builder(TitanConfig.class, "projecthero_titan.json", TitanConfig::new)
		.balance("stats", "attacks", "cooldowns", "aggro")
		.reset(1, "v0.10.11 Titan damage cut", "attacks.punchDamage", /* ... */ "attacks.chargeDamage")
		.reset(2, "v0.14.30 Titan health 1800 -> 1500", "stats.health")   // <- the new step
		.build();
```

- `reset(v, note, keys...)` puts exactly those keys back to the new defaults. Every other key keeps what the server
  set. Use this one in most cases.
- `resetBalance(v, note)` resets every **balance-owned** key (the `balance(...)` list). Use it for a big rebalance
  where you don't want to list every key.
- `custom(v, note, (file, defaults) -> ...)` is for a conditional move, like Hulk v3: "a range of 12, the old default,
  becomes 25, but a range the server chose stays".
- `introduce(v, note)` only stamps the version and resets nothing.

You don't need a step to **add** a key or a section, because missing keys always get their defaults. Removed keys
also need no step: they are dropped from the file. Renaming a key does need one (the old name is dropped and the new
one gets its default), so add a `custom` step if the old value should carry over.

Keys are dotted paths (`"stats.health"`), whole sections (`"abilities"`) or `"*"` (the whole file). A key that names
nothing throws at class load, so a typo fails the first gametest run.

## What a load does

1. **No file:** today's defaults are written, stamped with the current version.
2. **Not valid JSON / not an object:** the file is copied to `<name>.corrupt-<yyyyMMdd-HHmmss>.bak` next to it, and a
   fresh file is written from the defaults. A broken file never crashes the game.
3. **Every step newer than the file's `configVersion` runs, in order.** A file with no `configVersion` counts as
   version 0, so every step runs.
4. **Repair:**
   - missing or `null` keys get their defaults;
   - a value of the wrong kind (`"health": "lots"`, `12.5` for an int, an object where a number belongs) gets its
     default;
   - numbers written as text (`"8"`) are accepted;
   - unknown keys are dropped.
5. The result is stamped with the current version and written back.

A file from a **newer** release (its version is above ours, e.g. after a downgrade) runs no steps and keeps its
values and version.

## The files (v0.14.21 audit)

| File | Class | Version | Steps | Stale-prone before v0.14.21? |
|---|---|---|---|---|
| `projecthero.json` | `hero/HeroConfig` | 1 | 1 stamp | No. It had no version, but no default has changed since v0.9.19. Now versioned. |
| `projecthero_events.json` | `event/EventConfig` | 1 | 1 Grave Essence drops (v0.14.4) | Fixed in v0.14.4 (v0.13.6 drops). No later default changes. |
| `projecthero_titan.json` | `titan/TitanConfig` | **1 (new)** | 1 v0.10.11 damage cut | **Yes.** It had no version, so a file written by v0.9.22-v0.10.10 kept punch 26 / melee 22 / stomp 40 / slam 34 / grab 8 / hold 5 / throw 14 / boulder 36 / charge 44 instead of 20 / 16 / 30 / 26 / 6 / 4 / 10 / 28 / 34. Older changes don't matter, because v0.9.22 renamed the file. |
| `projecthero_titan_shifter.json` | `titanshifter/TitanShifterConfig` | 4 | 2 body + energy bar, 3 full-bar transform, 4 regen II + roar 32 | No. Already migrated. |
| `projecthero_hulk.json` | `hulk/HulkConfig` | 3 | 2 kit rebuilt, 3 Thunderclap 12 -> 25 if unchanged | No. Already migrated. |
| `projecthero_behemoth.json` | `behemoth/BehemothConfig` | 2 | 1 full reset (v0.13.7), 2 health 1000 -> 3000 | No. Already migrated (this is the original bug). |
| `projecthero_horde.json` | `horde/HordeConfig` | 1 | 1 boss health | No. New in v0.14.20 and versioned from the start. |
| `projecthero_darkseid.json` | `darkseid/DarkseidConfig` | 4 | 1 full reset, 2 v0.13.19 balance, 3 Parademons restored, 4 introduce rewards.valuablesMultiplier | No. Already migrated. |
| `projecthero_sentinel.json` | `sentinel/SentinelConfig` | 1 | 1 introduce (v0.15.1 Sentinel Purge) | No. New in v0.15.1 and versioned from the start. |

The audit compared the `public ... = <default>` lines in `git log -p` of each config class against its migration
steps. The Oathbreaker has no config file: its numbers are code constants, so they can't go stale.

Every existing migration keeps its behaviour; the steps above are the same changes, now in one place. The
gametests are in `ConfigMigrationGameTests`, plus `DarkseidRaidGameTests`, `RaidRepeatGameTests` and
`HordeGameTests`. They write old files under `projecthero_gametest_*` names, so the live configs (and anything
left in `build/run/gameTest/config`) can't affect them.

## Adding a new config file

1. Add a `public Integer configVersion;` field. Keep it boxed and uninitialised, so a file that predates it reads as
   null and counts as version 0.
2. Give the class a no-arg constructor that builds today's defaults.
3. Add a `SPEC` with `balance(...)` and `introduce(1, ...)`, and make `load()` just `instance = SPEC.load();`.
4. Add the spec to `everyConfigIsVersionedAndStamped` in `ConfigMigrationGameTests`.
