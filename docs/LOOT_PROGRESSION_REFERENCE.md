# Loot & Progression framework (v0.17.5) — reference

The foundation the structures / gear / artifacts work builds on. Design: `docs/STRUCTURES_PROGRESSION_DESIGN.md`.
Code: `com.projecthero.mod.loot` (server/common), `com.projecthero.mod.client.loot.LootClient` (client).
Tests: `src/gametest/.../LootProgressionGameTests`. Asset generator: `scratchpad/gen_loot_foundation_v0175.js`,
lang: `scratchpad/lang_loot_foundation_v0175.js` (adds keys one by one; never rewrites `en_us.json`).

## Rarity

`HeroRarity { COMMON (white), UNCOMMON (green), RARE (aqua), EPIC (light purple), LEGENDARY (gold) }`.

- Component `projecthero:hero_rarity` = `HeroRarity.COMPONENT` (persistent + synced; JSON value is the lower-case name).
- `HeroRarity.of(stack)` -> rarity or `null`; `HeroRarity.orCommon(stack)`.
- An item class declares its default rarity in its properties:
  ```java
  new MyItem(HeroRarity.props(HeroRarity.RARE).stacksTo(1))
  // or: HeroRarity.with(existingProps, HeroRarity.EPIC)
  ```
- A loot table can stamp any stack (vanilla too):
  `{"function": "minecraft:set_components", "components": {"projecthero:hero_rarity": "legendary"}}`
- Effects: the name is coloured everywhere (`mixin/ItemStackHeroRarityMixin` on `ItemStack.getHoverName`), and the
  client adds a coloured "Rare" line right under the name (`LootClient`, registered before `TooltipWrap` so all lines wrap).
- Keys: `projecthero.rarity.<name>`.

## Hero Vault

Block `projecthero:hero_vault`, block states `facing` (horizontal) + `tier` 1-4:
1 Hero Cache (iron trim), 2 Site Vault (gold), 3 Hideout Vault (purple), 4 Dungeon Vault (aqua).
Per-player loot like the vanilla trial vault: each player right-clicks once and gets their own roll, popped out of the door
with sound + particles; the block entity remembers looted UUIDs (NBT `looted`, capped at 512, synced to clients so a
looted vault stops shimmering for that reader). Strength 50 / resistance 1200, drops nothing.

```java
HeroVault.place(level, pos, tier, facing);   // LevelAccessor: works from StructurePiece.postProcess (WorldGenLevel) too
HeroVault.state(tier, facing);               // the BlockState, if you place it yourself / in a template
HeroVault.open(serverLevel, pos, player);    // what right-click does -> Opened(result, loot)
HeroVault.lootTable(tier);                   // ResourceKey projecthero:chests/vault_tier<N>
HeroVault.tierAt(level, pos), HeroVault.trimColour(tier), HeroVault.tierName(tier)
LootItems.vaultStack(tier)                   // creative/operator item that places that tier (block_state component)
```
`facing` = the side the door looks out of (towards the room the player enters from).
`HeroVaultBlockEntity.reset()` forgets all looters (operator refill / tests).

### Loot tables (`data/projecthero/loot_table/chests/`)

| Table | Guaranteed | Supplies | Valuables | Extras | Locator |
|---|---|---|---|---|---|
| `vault_tier1` | 3-7 Salvaged Tech | 1 roll (med kit, energy cell, repair kit) | iron, gold nuggets, copper, bread, arrows, torches, redstone | | tier 2, 35% |
| `vault_tier2` | 4-9 Salvaged Tech; Hero Alloy Scrap 3-8 or 1-2 Ingots | 1-2 rolls | iron, gold, emerald, lapis, diamond (rare), XP bottles | enchanted book 35% | tier 3, 45% |
| `vault_tier3` | 2-4 Hero Alloy Ingots, 1-3 Vibranium Shards | 2-3 rolls | diamonds, gold, emeralds, XP, obsidian | golden apples 50%, level 20-30 book 60%, random serum 8% (rare) | tier 4, 55% |
| `vault_tier4` | 3-6 Vibranium Shards, 3-6 Alloy Ingots, Celestial Core 60% | 3-4 rolls | diamonds, netherite scrap, ancient debris, emeralds, XP | 2-3 golden apples, notched apple 8%, 2 level-30 books, random serum 30% (uncommon) | none (repeatable) |

Serums are the existing `mutagenic_serum` (6) / `heroic_serum` (3) / `prismatic_serum` (2) by weight.

**Hook points for later features.** Every vault table has two pools that pull a sub-table:
`chests/vault_tier<N>_artifacts` and `chests/vault_tier<N>_gear`. They ship as empty tables (`"pools": []`).
Add artifacts / gear by giving those files pools (one roll each per vault open) — don't edit the main tables for that.
In code you can also use Fabric `LootTableEvents.MODIFY` on those keys.

## Materials (gear tiers)

| Id | Tier | Rarity | Notes |
|---|---|---|---|
| `salvaged_tech` | I | Common | 4 + iron ingot -> 1 Hero Alloy Ingot (shapeless) |
| `hero_alloy_scrap` | II | Uncommon | 9 <-> 1 Hero Alloy Ingot |
| `hero_alloy_ingot` | II | Uncommon | |
| `vibranium_shard` | III | Rare | 4 -> 1 existing `vibranium_ingot` (`recipe/vibranium_ingot_from_shards`) |
| `celestial_core` | IV | Epic | enchantment glint, stacks to 16, fire resistant |

Class `LootMaterialItem(gearTier, props)`; tooltip = "Tier N material" + `item.projecthero.<id>.hint`.
The existing Vibranium Ingot's recipes/uses are unchanged (it had no recipe producing it before; Ultron Contagion still drops it).

## Hero supplies

`SupplyItem(Kind, props)`, stack 16, hold right-click (short use), sound, item cooldown after a success.
A supply that would do nothing is refused with a message and **not** consumed.

| Item | Use | Cooldown | Effect |
|---|---|---|---|
| `energy_cell` (Uncommon) | 1 s | 20 s | +40% of the current power's main meter (`EnergyCellTargets`) |
| `cooldown_stim` (Rare) | 0.8 s | 30 s | every remaining cooldown x0.5 (`CooldownTargets`) |
| `suit_repair_kit` (Uncommon) | 1.6 s | 5 s | worn armour +50% max durability each, + `SuitRepairTargets` (Iron Man integrity +50%) |
| `med_kit` (Common) | 1.2 s | 10 s | 16 HP over 80 ticks on our own tick (`MedKitHealing`, no effect icon), cures Poison + Wither |

### Registering targets

```java
// Energy Cell: first applicable target that isn't full wins (registration order; Hero-Tier meters come first)
EnergyCellTargets.register("my_power", MyPower::hasPower,
        EnergyCellTargets.meter(MyPower::meter, MyPower::setMeter, p -> MyConfig.METER_MAX));
EnergyCellTargets.register("my_power", pred, EnergyCellTargets.adder(get, add, max)); // when only "add" exists
EnergyCellTargets.register("my_power", pred, (player, fraction) -> { ...; return changed; });
EnergyCellTargets.registerExperimental("my_resource", 500f); // a HeroPack mutation reserve <active>/<name>

// Cooldown Stim: every shortener runs; returns how many cooldowns it shortened
CooldownTargets.register("my_power",
        CooldownTargets.readyAtMap(ModAttachments.MY_STATE, MyState::copy, s -> s.abilityReadyAt));
CooldownTargets.register("custom", (player, factor) -> { ...; return n; });
// helpers: CooldownTargets.scaled(now, readyAt, factor), isLive(now, readyAt), scaleMap(map, now, factor)

// Suit Repair Kit: worn armour is built in; add suit-health systems
SuitRepairTargets.register("my_suit", (player, fraction) -> { ...; return repaired; });
```
Re-registering an id replaces it in place.

**Built in** (`SupplyTargetDefaults`):
- Energy Cell: Black Panther (Kinetic), Kryptonian (Solar), Nova (Force), Saiyan (Ki), Ghost Rider (Hellfire), Cyborg (Core),
  Blue Beetle, Star Platinum (Stamina), Hulk (Rage, Hulk form only), Moon Knight (Vengeance), Raven, Vision, Starfire,
  Shazam, the Spin, Beast Boy, Falcon (Thrust), Silver Surfer (Cosmic), Speed Force, All Might (OFA timer), Green Lantern
  (ring charge), Spider-Man (web reserve), Thor (Storm Energy), Max Steel (Turbo), Titan Shifter (Steam), Symbiote
  (Biomass), Omnitrix (transform time, while transformed), HeroPack reserves psi / ecell / flight / phase / sparkle, Quirks
  (a "builds" meter is eased, a "spends" meter refilled).
- Cooldown Stim: the `abilityReadyAt` map of 33 power states (Experimental, All Might, Beast Boy, Black Panther, Blue Beetle,
  Cyborg, Doctor Fate, Falcon, Ghost Rider, Green Lantern, Hulk, Iron Man, Kryptonian, Max Steel, Miraculous, Moon Knight,
  Nova, Punisher, Quirk, Raven, Saiyan, Shazam, Silver Surfer, Speed Force, Spider-Man, Spin, Stand, Starfire, Super Soldier,
  Titan Shifter, Undead, Vision, Wolverine), Thor's `CooldownState`, Symbiote slot cooldowns, Omnitrix alien moves + recharge.
  Sentinels (values far from now, `Long.MAX_VALUE`, small ints) and effect keys (`~*`, `*pending*`, `*_until`) are skipped.
- Not covered: vanilla `ItemCooldowns` (no public read of remaining time), the private static cooldown maps
  (`SaiyanKit.READY`, a few Green Lantern / Iron Man / Symbiote maps).
- Suit Repair Kit: Iron Man worn-suit integrity.

## Locator

Item `projecthero:locator`, stacks to 1, component `projecthero:locator_target` = `LootItems.LOCATOR_TARGET`
(`LocatorTarget(tier 2-4, Optional<GlobalPos> pos, Optional<ResourceLocation> structure)`; JSON `{"tier": 3}`).
Name "Locator: Site / Hideout / Dungeon"; rarity by tier (Uncommon / Rare / Epic).

- Right-click: searches `#projecthero:locator/tier<N>` 100 chunks out (vanilla nearest-map-structure search) and binds
  like a lodestone compass (needle = vanilla compass angle predicate, glint when bound). Bound: right-click shows name +
  distance; sneak + right-click re-searches from here. Empty tag / nothing in range: message, stays unbound, nothing used.
- Tooltip: tracked place's name (`structure.<ns>.<path>`, falls back to the path) + distance (client).
- HUD: while held, a line above the hotbar "<name>  <distance> m".

```java
LocatorItem.forTier(3)                         // a fresh stack (tier + rarity components)
LocatorItem.bind(stack, GlobalPos, structureId) // pre-aim one (e.g. a quest reward)
LocatorItem.locate(serverPlayer, stack)        // what right-click does -> BOUND / NOT_FOUND / NONE_EXIST
LocatorSearch.tag(tier), LocatorSearch.members(registryAccess, tier), LocatorSearch.search(level, origin, tier)
```
Loot-table entry (as the vault tables do):
`{"function": "minecraft:set_components", "components": {"projecthero:locator_target": {"tier": 3}, "projecthero:hero_rarity": "rare"}}`

**Add a structure to a tier:** list its id in `data/projecthero/tags/worldgen/structure/locator/tier<N>.json`
(and add `structure.projecthero.<id>` to `en_us.json` for its display name). Tier 2 holds the 19 old sites (all but `city`);
tier 3 and tier 4 are empty until the hideouts / the Vault exist (a tier-3/4 Locator says "No Hideout can be tracked yet").

## Guidebook / JEI

- Guide chapter `projecthero.guide.loot` ("Loot & Progression", `HeroPackGuide.CH_LOOT` = 77, in the Items, Places &
  Crafting category, icon `hero_vault`). Framing chapters are now 78 (`HeroPackGameTests`, `MiraculousTurtleHorseGameTests`).
  New guide chapters go before `CHAPTER_POWER_BASE` and bump both counts.
- JEI info pages for every material, supply, the four vaults and the three Locators (`ProjectHeroJeiPlugin.lootInfo`).

## Static state

`MedKitHealing` keeps `UUID -> ticks left` (no world refs); cleared via `LootMod.clearSessionState()` from
`ServerStateReset.clearAll`. The target registries hold registration-time lambdas only. `LocatorSearch.finder` is a test
seam (gametests swap it, then restore `DEFAULT_FINDER`).
