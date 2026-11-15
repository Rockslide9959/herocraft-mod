# Structures, Progression & Loot (v0.17.5) — DRAFT, awaiting sign-off

User's picks (2026-11-15): all four structure kinds (big dungeons, villain hideouts, upgrade the old 20 sites, small
world flavour); progression = **gear tiers + a loose structure chain**; powers stay obtainable exactly as now;
loot = **unique artifacts, rarity tiers, hero supplies**.

## 1. The loose chain (four tiers of places)

Each tier's loot vault drops a **Locator** (an item like a treasure map / tracker) pointing at a place of the next
tier. Nothing is hard-locked: you can stumble into a higher-tier place early, it's just much harder. The chain gives
direction, the gear tiers give the power curve.

| Tier | Places | Danger | Drops a Locator to |
|---|---|---|---|
| 1 Finds | crashed tech, supply drops, hidden caches, abandoned hero outposts | none or a few mobs | an upgraded old site |
| 2 Sites | the existing 20 sites, rebuilt bigger with guards and a vault | guard squad | a villain hideout |
| 3 Hideouts | one lair per major villain faction | guards, traps, mini-boss | the dungeon |
| 4 Dungeon | big multi-floor dungeons | full gauntlet + boss | (repeatable, best loot) |

## 2. Structures

**Tier 1 Finds (common, small, several kinds):** Crashed Drone, Supply Drop (with smoke, lands near players
occasionally), Hidden Cache (buried chest + marker), Abandoned Hero Outpost (ruined hideout, notes in a lectern).

**Tier 2 Old sites, upgraded:** the 20 existing sites keep their purpose (origins stay as they are) but get more
rooms, guards matching the site (soldiers at Government Site, cultists at Tomb of Nabu...), and a **vault room**
with a tiered chest.

**Tier 3 Villain hideouts (one each):**
- Kingpin's Penthouse Front (city), Ultron Forge (mountain bunker), Apokolips Outpost (scorched crater),
  Symbiote Hive (deep cave), Frost Giant Hall (snow biome).
- Clearing one (mini-boss dead + core broken) **calms that faction's raids nearby for a while** and drops its Locator.
- Mini-bosses use the shared threat table.

**Tier 4 Dungeons (rare, big):**
- **The Vault** (underground super-prison: cell blocks, security floors, riot wardens, warden boss).
- More can follow later (sky fortress, ancient temple). v0.17.5 ships one, done properly.

## 3. Gear tiers (for anyone, power or not)

| Tier | Material | Found in | Gear |
|---|---|---|---|
| I | Salvaged Tech | Finds | basic gadgets (grapple line, smoke bomb, flash) |
| II | Hero Alloy | Sites | alloy armour set, alloy weapons, utility belt |
| III | Vibranium (shards from hideouts) | Hideouts | vibranium armour/weapons, gadget upgrades |
| IV | Celestial Core | Dungeons | top armour/weapon set + gadget mastery |

Gear sits beside powers: a powered player still benefits (armour, gadgets), an unpowered one has a real path.

## 4. Loot

- **Rarity tiers:** Common (white), Uncommon (green), Rare (blue), Epic (purple), Legendary (gold). Coloured names
  and a rarity line in tooltips (wrapped). Chest quality rises with the place's tier.
- **Unique artifacts** (~10, each from specific places): e.g. Warden's Keyring, Ultron's Cortex Shard,
  Kingpin's Cane, Hive Heart, Frostbrand, Mother Box Fragment, Cracked Mjolnir Shard... Each has one passive or
  active effect, never a full power.
- **Hero supplies:** Energy Cell (refills the current power's meter), Cooldown Stim, Suit Repair Kit, Med Kit,
  and the existing random serums in high-tier vaults.

## 5. Build order (each copied to the modpack as it lands)

1. Loot framework: rarity, tooltips, tiered chest tables, supplies.
2. Tier 1 Finds + Locators.
3. Gear tiers I–II, upgraded old sites (in waves).
4. Hideouts (one at a time) + vibranium gear.
5. The Vault dungeon + Celestial gear + artifacts.
6. Guidebook chapter, JEI, en_us, CurseForge description.
