# Quirks (v0.16.7) -- reference

Seven My Hero Academia Quirks (Hellflame, Overhaul, Engine, Dark Shadow, Erasure, Explosion, Zero Gravity). Each is a
**Primary power** (one at a time), all share one core (`com.projecthero.mod.quirk`), each has a 12-move kit in
`quirk/<kit>/**` (+ `client/quirk/<kit>/**`). Design and per-kit move tables: `docs/QUIRKS_PLAN.md`.

## Architecture

| Piece | Where | What |
|---|---|---|
| `Quirk` enum | `quirk/Quirk.java` | id, `heroKey()` = `quirk_<id>`, meter id + colour, `animBase` |
| `QuirkState` | `quirk/data/QuirkState.java` | the synced attachment `projecthero:quirk_state` (hasPower, quirk, costume (unused), meter, overloadUntil, anim, cooldowns, kit `vals` / `vecs`) |
| `Quirks` | `quirk/Quirks.java` | the core API (below); `tick` runs from `AbilityRouter.serverTick` |
| `QuirkKit` / `QuirkKits` | `quirk/QuirkKit*.java` | a kit's 12 moves; each kit registers itself from `<Kit>Mod.initialize()` |
| `QuirkAbilityManager` | `quirk/` | routes R G X Z V C (+ Shift) to the kit; key-repeat filter; Erased / overloaded presses refused |
| `QuirkCommand` | `quirk/` | `/projecthero quirk meter <0-100> \| erase <seconds> \| info` (also under `power tune quirk`) |
| `QuirkItems`, `QuirkSerumItem`, `QuirkSuits`, `QuirkSuitItem` | `quirk/item/` | the 7 serums + the Quirk Trigger, loot; the 7 craftable hero suits (28 armour pieces) |
| `PowerSuppression` | `hero/PowerSuppression.java` | Erasure: cancels ANY power (see below) |
| `QuirkClient` | `client/quirk/` | the client core + kit dispatch (`QuirkClientKit`: poses, hidesSkinOverlay) |
| `QuirkHud` | `client/quirk/` | meter bar, OVERLOADED line, the six key boxes, Alt move list, the ERASED banner |
| `QuirkCostume` / `QuirkCostumeLayer` / `QuirkFirstPersonArmMixin` | `client/quirk/`, `client/mixin/` | the worn hero-suit pieces + Dark Shadow bird head (third person + first-person sleeve); `QuirkSuitArmourMixin` hides their vanilla armour model |
| `PowerSuppressionClient` | `client/quirk/` | receives the Erased sync for the local player |

Slot mapping: `SLOT_1 = R, SLOT_2 = G, SLOT_3 = X, SLOT_4 = Z, SLOT_5 = V, SLOT_6 = C`; Shift = sneaking.

## Core API (`Quirks`)

- state: `state` / `peek` (client-safe) / `edit(player, n -> ...)` (always a copy) / `save`
- who: `hasPower`, `quirk`, `holds`, `activeAs` (holds AND not Erased -- passives / visuals check this)
- cooldowns: `cooldownRemaining` (client-safe), `ready`, `setCooldown`
- poses: `setAnim(player, animId)` (ids from `quirk.animBase`); kit numbers `setVal` / `setVec` / `vec`
- the meter (0..100): server `meter` / `setMeter` / `addMeter` (a map, cheap every tick); flushed into the synced state
  every `QuirkConfig.METER_SYNC_TICKS` when it moved `METER_SYNC_STEP`, or at once at 0 / 100; client `clientMeter`
- overload: `overload(player, ticks)` (stops the kit's moves), `overloaded`, `overloadLeft`; keys refuse except
  `QuirkKit.usableWhileOverloaded`
- `say(player, key, colour, args)` (action bar, rate limited), `quirkName` / `heroName`
- `grant(player, quirk)` (refused if already held or by the unique-powers rule; replaces any Primary power through
  `HeroTiers.claimPrimary`), `revoke`
- lifecycle (wired in `QuirkMod`): join resets the anim; respawn resets meter / cooldowns / overload / kit values; death,
  logout and dimension change end running moves (`QuirkKit.stopAll`)

## Wiring (the Primary / Hero-Tier checklist)

- `HeroTiers`: `hasHeroTier`, `HERO_KEYS` (the 7 `quirk_<id>` keys), `holdsHero` / `revokeHero` (default branch reads
  `Quirk.byHeroKey`), `hasIncompatibleWith`
- `PowerGrants`: `HERO_TIER_KEYS`, `grantHero` / `revokeHeroTier` (so `/projecthero power grant|remove quirk_<id>`, the
  Heroic / Prismatic random serums and `UniquePowers` all know them); `HeroCommand.revokeHeroTier`
- `HeroIdentity` (squad roster: the hero name), `PowerInfoScreen` (I key: intro, meter, the 12 moves from
  `QuirkKits.of(q).idsOf(slot)` + lang), `ModCreativeTab` (`QuirkItems.addToCreativeTab`)
- `SpawnCallings`: after the ring and the Miraculous miss, `QuirkConfig.SPAWN_CHANCE` (5%) a random Quirk offer
  (`Kind.QUIRK`, `Offer.quirk`); same Accept / Refuse flow, gamerule and fake-player skip
- lang: `projecthero.hero_tier.quirk_<id>`, `projecthero.quirk.<id>` / `.hero` / `.intro`, `projecthero.quirk.meter.<meterId>`,
  `projecthero.quirk.ability.<abilityId>` (+ `.desc`, kit agents), `message.projecthero.quirk.overloaded.<id>`
- The guidebook chapters are built by the guide (not the core) from `QuirkKits` + lang.

## Erasure (`PowerSuppression`) -- every power system

User decision: Erasure disables ALL powers, players included. `suppress(target, ticks)` (extends), `release`,
`isSuppressed` (server; client for the local player via `clientUntil` / `clientPlayer`), `remaining`.

For a suppressed **player**:
- `AbilityRouter.handleInput` refuses every key PRESS of every system (`blocksAbility`); releases pass so held moves end.
- every power's action payload receiver starts with `PowerSuppression.refuse(player[, action])` (an action named
  `*_STOP` / `*_OFF` / `*RELEASE` passes): Thor, Green Lantern (+ sniper charge), Spider-Man (actions, freerun, glide,
  takedown / dodge), Super Strength, Max Steel (not POWER_DOWN), Titan Shifter (not LET_DOWN), All Might, Moon Knight,
  Hulk (TRANSFORM / ROAR / PUSH only -- the mind-room answers pass), Wolverine, Teleportation portal, Bifrost, Blue
  Beetle, Cyborg (+ Boom Tube), Flash Ring, mutation Flight, Ghost Rider (not SIGN_CONTRACT), Kryptonian, Nova, the
  Miraculous (transforming only; detransforming works; the spoken phrase too via `Miraculous.transform`), Pegasus Voyage.
- `tickPlayer` (every player, last in `AbilityRouter.serverTick`): power flight is cut and kept off (`abilities.flying /
  mayfly = false`, `HeroFlight` stopped), mutation passives off (`PowerPassives.reconcileActive` checks it, re-run on
  both edges), the display-only **Erased** status effect (`projecthero:erased`) shows the time left, and the owner's
  client gets a `PowerSuppression.SyncPayload` (padded 20 ticks so a stare renewed every tick never flickers; `0` on end).
- the Quirk core: the kit's `stopAll` runs once and its tick pauses (`Quirks.tick`).
- **Machines are not powers**: an Iron Man suit (keys, flight, Call Armour) and the Punisher's kit / guns keep working
  (`PowerSuppression.isTech`); Stark gadgets / gantry / drones / firearms payloads are not gated.
- Not paused: forms already on (a transformed Hulk / Max Steel / Ghost Rider stays transformed, their keys just refuse)
  and mutation TOGGLES already on keep running until switched off -- each power would need its own pause.

What Erasure does to **mobs** (creepers, skeletons, endermen, bosses' special moves) belongs to the Erasure kit.
Static state is cleared by `ServerStateReset` (`QuirkMod.clearSessionState` -> `PowerSuppression.clearSessionState`).

## Hero suits (craftable armour) -- v0.16.7 user rework

**Getting a Quirk never changes your look.** The costumes are seven craftable armour sets (`quirk/item/QuirkSuits`,
`QuirkSuitItem`), worn by anyone, Quirk or not:

| Suit | Quirk skin | Items | Recipe (I = iron ingot, A, B) |
|---|---|---|---|
| Endeavor Costume | `hellflame_*` | `endeavor_suit_<piece>` | blaze powder, magma cream |
| Overhaul Costume | `overhaul_*` | `overhaul_suit_<piece>` | green wool, bone |
| Ingenium Armour | `engine_*` | `ingenium_suit_<piece>` | quartz, blue dye |
| Tsukuyomi Costume | `dark_shadow_*` | `tsukuyomi_suit_<piece>` | black wool, phantom membrane |
| Eraser Head Costume | `erasure_*` | `eraser_head_suit_<piece>` | black wool, string |
| Ground Zero Costume | `explosion_*` | `ground_zero_suit_<piece>` | gunpowder, orange dye |
| Uravity Costume | `zero_gravity_*` | `uravity_suit_<piece>` | pink wool, white wool |

`<piece>` = helmet / chestplate / leggings / boots. Shapes: helmet `IAI / B B`, chestplate `I I / ABA / III`, leggings
`IAI / B B / I I`, boots `B B / I I`. Stats: one shared material `projecthero:quirk_suit` -- 2 / 6 / 5 / 2 armour, no
toughness, durability multiplier 17 (iron 15), enchantability 12, repaired with iron. Recipe unlocks: holding A or B.
Icons / models / recipes / advancements are generated by `scratchpad/quirk_suits/gen_quirk_suits.js` (the icons are
armour silhouettes filled from the suit's own skin); lang by `scratchpad/quirk_suits/lang_quirk_suits.js`.

Rendering (`client/quirk/QuirkCostume`, `QuirkCostumeLayer` on every humanoid renderer -- players, armour stands,
mannequins, zombies): the costume skin `textures/entity/quirk/<quirk>_wide.png` / `_slim.png` (picked by the wearer's
skin model; never change these, the user approved them), base layer +0.02 px, outer layer at vanilla's overlay offsets,
**only the worn piece's parts**: helmet = head + hat; chestplate = body + jacket + arms + sleeves (+ the first-person
sleeve, `QuirkFirstPersonArmMixin`); leggings = the leg texels above side row `BOOT_ROW` (7) + leg tops; boots = rows 7-11
+ soles (two masked `DynamicTexture` copies per skin). `QuirkCostume.hideCoveredOverlay` (from `PlayerModelMixin`) hides
the wearer's own outer layer only where a piece covers it (a helmet whose skin has an empty hat region keeps the
wearer's hair). `QuirkSuitArmourMixin` skips the vanilla armour model for these items slot by slot. Pieces of
different suits mix. `QuirkState.costume` is unused (kept for old saves); `/projecthero quirk costume` only says the
costume is a craftable suit.

**Dark Shadow bird head** (the one exception, user): holding Dark Shadow (Erased or not) draws
`textures/entity/quirk/dark_shadow_head.png` (head + hat only) over the head for every viewer and hides the wearer's hat
layer; it wins over any Quirk suit helmet.

## Quirk Serums

`quirk_serum_<id>` (7, Rare) and `quirk_serum_random` "Quirk Trigger" (a random Quirk you don't have). Drink (32 ticks)
-> `Quirks.grant` (kept if nothing happens), leaves a glass bottle. Recipes (shapeless): glass bottle + diamond + amethyst
shard + two themed items (blaze powder + magma cream; glistering melon + flint; sugar + piston; ink sac + phantom
membrane; spider eye + string; gunpowder + fire charge; feather + pink dye; Trigger: redstone + fermented spider eye).
Loot: one of the eight in 1/12 monster-room dungeon chests, 1/10 stronghold corridor, 1/8 ancient city. Textures /
models / recipes are generated by `scratchpad/gen_quirk_serums.js`.

## Adding a Quirk

1. Add it to `Quirk` (id, names, meter id + colour, a free `animBase`).
2. A kit package `quirk/<id>/` with `<Kit>Mod.initialize()` calling `QuirkKits.register`, called from `QuirkMod.initialize`;
   client `client/quirk/<id>/<Kit>Client.initialize()` calling `QuirkClient.register`, called from `QuirkClient.initialize`.
3. Add `quirk_<id>` to `HeroTiers.HERO_KEYS` and `PowerGrants.HERO_TIER_KEYS`, and to the case lists in
   `PowerGrants.grantHero` / `revokeHeroTier` and `HeroCommand.revokeHeroTier`.
4. A serum: an entry in `gen_quirk_serums.js` (colours + two themed ingredients); lang for the item.
5. Lang: name, hero, intro, meter, overload message, `projecthero.hero_tier.quirk_<id>`, every move + `.desc`.
6. The costume textures, a `QuirkSuits.Suit` entry (+ generator rows), a gametest class listed in `src/gametest/resources/fabric.mod.json`.
