# Moon Knight (in development, v0.13.19+)

A Hero-Tier power built in 8 phases, one at a time, each handed back as a jar for testing before the next starts.
Every tunable number lives in `moonknight/MoonKnightConfig.java` (base values: damage / range / duration are multiplied
by the lunar power, cooldowns divided by it).

| Phase | Scope | Status |
| --- | --- | --- |
| 1 | Player data, lunar power helper, Vengeance meter, Fracture, Resurrection charge, debug commands, HUD section | **done (v0.13.19)** |
| 2 | Armour item + GeckoLib renderer (user's `moonknight.bbmodel`), H transformation with armour storing/restoring, the cape renderer | **done** |
| 3 | R Crescent Darts, X Cape Glide / Shroud / Shadow Step | |
| 4 | G Grappling Line, Z Truncheon / Staff | |
| 5 | C Alters (passives + specials + radial picker) | |
| 6 | V Moonbeam / Eye of Khonshu / Judgement + Khonshu's Resurrection (the actual death save) | |
| 7 | Temple of Khonshu, Scarab of Khonshu, the night ritual, advancement | |
| 8 | Balance pass, multiplayer sync check, particle / sound polish, final jar | |

While it is in development Moon Knight is in `PowerGrants.IN_DEVELOPMENT`: operators can grant it, but the random
power serums never roll it.

## How it plugs into the existing hero systems (no parallel systems)

| Concern | Existing system reused | Moon Knight piece |
| --- | --- | --- |
| Hero registration | `HeroTiers` (`HERO_KEYS`, `holdsHero`, `revokeHero`, `hasIncompatibleWith`, `claimPrimary` -- 2 Primary slots) | key `moon_knight` |
| Grants | `PowerGrants.grantHero` (`/projecthero power grant moon_knight`), `HeroCommand` revoke | `MoonKnight.grant/revoke` |
| Player data | Fabric Data Attachment API, `persistent + copyOnDeath + syncWith(all)` in `ModAttachments` | `MOON_KNIGHT_STATE` -> `moonknight/data/MoonKnightState` |
| Keys | The shared, rebindable ability keys (`ModKeyBindings.ABILITY_SLOTS`, Controls > "Project Hero Abilities": R G X Z V C) + H (`POWER_SELECT`) | routed by `AbilityRouter` to `MoonKnightAbilityManager` from Phase 3 |
| Client -> server | `AbilityInputPayload(slot, pressed)` press / release edges; the server times holds and reads sneak (`isShiftKeyDown`) | same -- TAP / HOLD / SNEAK+KEY all decided server-side |
| HUD | `HudRenderCallback`, bottom-right boxed-key row, Hairline bars, Hold Left Alt for names | `client/gui/MoonKnightHud` |
| Cooldowns | per-hero `Map<String, Long> abilityReadyAt` of absolute game time in the synced state | `MoonKnightState.abilityReadyAt`, ids `<ability>`, `<ability>_hold`, `<ability>_sneak` |
| Server tick | `AbilityRouter.serverTick` | `MoonKnight.tick` |
| Lifecycle | `ProjectHeroMod` ALLOW_DEATH / JOIN / world change / DISCONNECT hooks | `MoonKnight.clearTransient`, `onPlayerJoin` |
| Kill tracking | `ServerLivingEntityEvents.AFTER_DEATH` | `MoonKnight.onEntityKilled` |
| Armour (Phase 2) | `SuperheroArmorItem` + `SuperheroArmorVisuals` + the one `SuperheroArmorRenderer` (GeckoLib) + `PowerEquipmentLock` (binding) | |
| Structure (Phase 7) | code-generated `StructurePiece` + `ModStructureTypes` / `ModStructurePieceTypes` + datapack `structure` / `structure_set` / biome tag, chest via `setLootTable` (the Gamma Lab is the model) | |
| Advancement (Phase 7) | `minecraft:impossible` criterion awarded from code (`GraveboundCurse.award`) | |

Notes on the user's spec vs. this codebase:
- **Thor has no shrine and no moral score.** Thor's structure is the Mjolnir crater (no chest, it spawns the hammer),
  and worthiness is a plain 0-100 flag earned by lifting Mjolnir with Hero of the Village. The Temple follows the
  Gamma Lab's structure + loot-chest pattern instead.
- **The mod runs Minecraft 1.21.1 with GeckoLib 4.9.2** (not 1.21.11 / GeckoLib 5); the 1.21.1 API names are used
  (`Level.isNight()`, `getMoonPhase()`, `canSeeSky(BlockPos)`).
- **Keys:** every hero shares the six rebindable ability keys rather than having its own Controls category, and H is
  the shared "Utility 1" key. Moon Knight uses the same keys, so rebinding them in Controls > Project Hero Abilities
  rebinds his too, and the HUD always shows the bound key. Vanilla's creative-mode hotbar save / load (C / X) is not
  suppressed anywhere yet -- Phase 3 adds that for a transformed Moon Knight.

## Phase 1 (v0.13.19)

- **Lunar power** (`MoonKnightLunar.power`): night + full moon 1.5; gibbous 1.3, quarter 1.15, crescent 1.0, new
  moon 0.8; day (and the Nether / End) 0.7; no sky above -0.15 (min 0.6).
- **Vengeance** (0-100): +5 for killing a hostile mob that was targeting a villager / wandering trader / iron golem /
  another player, else +2 at night / +1 by day. After two in-game days without a hostile kill it drains one point
  every 30 s. Starts at 50 (100 if the pact is sealed under a full moon).
- **Fracture:** Vengeance hitting 0 while transformed forces a random other alter for 20 s with 3 s of Nausea; at most
  once every 10 minutes.
- **Khonshu's Resurrection charge:** starts charged; once spent it recharges at the start of a full moon night in a
  later lunar cycle (the death save itself is Phase 6). Shown as the crescent on the HUD.
- **HUD** (only while transformed): moon phase icon + live multiplier, alter name (and Fracture timer), the six keys
  with their real bindings and cooldowns, Vengeance % + Hairline bar, resurrection crescent, GLIDE indicator.
- **Commands** (op): `/moonknight grant|revoke [players]`, `vengeance <0-100> [players]`, `locate_temple` (reports
  "not built yet" until Phase 7), `moon <0-7> [day|night]`, `suit on|off` (Phase 1 test hook: flips the transformed
  flag without a suit so the HUD / Fracture can be tested), `status [player]`.
- Tests: `MoonKnightGameTests`.

## The user's model (for Phase 2)

`C:/Users/ethan/OneDrive/Desktop/3d minecraft models/moon knight/moonknight.bbmodel`: a GeckoLib-format player-skin
rig, 64x64 texture, bones `Head`, `Body`, `Right Arm`, `Left Arm`, `Right Leg`, `Left Leg` under `armor`, each with a
base cube and a second-layer cube (inflate 0.25 / 0.5), no animations, one texture (so one texture for all three
alters, with `MoonKnightAlter.suitTexture()` as the per-alter hook). Phase 2 converts it onto the shared player-armour
rig (as Thor's and the Hulk's skins were) and hangs the cape off `Body` (-> `armorBody`).

## Phase 2 -- the suit, H and the cape

- **Model:** `scratchpad/gen_moonknight.js` converts the user's `moonknight.bbmodel` (a 64x64 player-skin rig) onto the shared
  GeckoLib armour rig (`geo/moon_knight.geo.json` = the Symbiote host's rig, renamed; `textures/armor/moon_knight.png` = the
  embedded skin byte for byte; icons cut from the skin) and paints the original cape texture.
- **Items:** `moonknight/item/MoonKnightArmorItem` (set id `moon_knight`; resolves `moon_knight_<alter>` first if registered --
  the per-alter texture hook) + `MoonKnightItems`. Pieces are synthesised by `MoonKnightSuit`: Curse of Binding, unbreakable,
  deleted if found anywhere but the armour slots (inventory, cursor, a dropped item entity).
- **H:** `MoonKnightTransform.toggle` (client -> `MoonKnightActionPayload.TOGGLE_SUIT`). 30 ticks of spiralling bandage
  particles, invulnerable (`MoonKnightDamage`), then `MoonKnightSuit.suitUp` stows the worn armour in `MoonKnightState.storedArmor`.
  H again (or death / revoke) strips the suit and hands the armour back into the slots (on death before vanilla drops, so
  keepInventory rules apply to the player's own gear).
- **Cape:** `client/moonknight/MoonKnightCapeLayer` -- vanilla CapeLayer physics (cloak lag, body yaw, bob, crouch), 20 px long,
  a centre panel + two folding two-segment side panels, a hood flap, spread for FLAG_GLIDING, wrapped round the front for
  FLAG_SHROUD. Screenshot-checked in the dev client (front / back / side / shroud / glide).
- **Key framework:** `ability/MoonKnightAbilityManager` (press/release edges -> TAP / HOLD (10 ticks) / SNEAK+KEY, timed on the
  server), `MoonKnightMove`, `MoonKnightAbilities` (lunar-scaled cooldowns, Vengeance costs). Synced live state in
  `data/MoonKnightAction` (flags, pose, charge, rope). Poses in `client/moonknight/MoonKnightPose`.
- **Vanilla C / X:** `client/mixin/MinecraftHotbarKeysMixin` makes the creative hotbar save / load activators read as not held
  while a Moon Knight is transformed.
