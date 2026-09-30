# Moon Knight (v0.13.20; reworked in v0.13.21 -- see the last section for the current keys and numbers)

A Hero-Tier power built in 8 phases, one at a time, each handed back as a jar for testing before the next starts.
Every tunable number lives in `moonknight/MoonKnightConfig.java` (base values: damage / range / duration are multiplied
by the lunar power, cooldowns divided by it).

| Phase | Scope | Status |
| --- | --- | --- |
| 1 | Player data, lunar power helper, Vengeance meter, Fracture, Resurrection charge, debug commands, HUD section | **done (v0.13.19)** |
| 2 | Armour item + GeckoLib renderer (user's `moonknight.bbmodel`), H transformation with armour storing/restoring, the cape renderer | **done** |
| 3 | R Crescent Darts, X Cape Glide / Shroud / Shadow Step | **done** |
| 4 | G Grappling Line, Z Truncheon / Staff | **done** |
| 5 | C Alters (passives + specials + radial picker) | **done** |
| 6 | V Moonbeam / Eye of Khonshu / Judgement + Khonshu's Resurrection (the actual death save) | **done** |
| 7 | Temple of Khonshu, Scarab of Khonshu, the night ritual, advancement | **done** |
| 8 | Balance pass, multiplayer sync check, particle / sound polish, final jar | **done (v0.13.20)** |

Released in v0.13.20 (`PowerGrants.IN_DEVELOPMENT` is empty again, so the Heroic / Prismatic serums can roll it).

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
## Phase 7: the Temple of Khonshu, the Scarab, the ritual, the advancement

Code: `moonknight/temple/` (`KhonshuTemple` registry + hooks, `ScarabOfKhonshuItem`, `KhonshuAltarBlock` +
`KhonshuAltarBlockEntity`, `KhonshuRitual`, `MoonKnightRitualFadePayload`, `TempleOfKhonshuStructure` + `TempleOfKhonshuPiece`);
client `client/moonknight/KhonshuTempleClient` (fade overlay + receiver) and `KhonshuAltarRenderer`. Tests:
`MoonKnightTempleGameTests`.

**The structure** (`projecthero:temple_of_khonshu`, desert only via `#projecthero:has_structure/temple_of_khonshu`):
`random_spread` spacing 110 / separation 40 / frequency 0.6 (salt 1978040621) -- vanilla desert pyramids are 32 / 8,
so it is far rarer, about as rare as the Gamma Lab (120 / 45 / 0.8). One code-generated piece, 31 x 31 (fits in
3 x 3 chunks), entrance to the south, levels fixed from the generator's estimated height at construction:

- sand-blown smooth-sandstone forecourt, two lantern-topped obelisks, a flight of sandstone steps up a one-block podium
  with two campfire braziers by the door and sand drifted against the walls;
- a 21 x 21 hall, walls 7 high (cut-sandstone base and cornice, a chiselled band, pilasters every four blocks, window
  slits, a few sand-blasted gaps high up), four corner towers with lanterns, a crenellated roof, and over the door a
  raised pediment with a quartz **crescent**;
- inside: eight columns, hanging lanterns, a quartz crescent in the floor with its horns toward the altar, a 5 x 5 dais
  with a campfire at each corner and the **Altar of Khonshu** on top, directly under a round **oculus** (radius 3.5,
  chiselled rim) so the altar always sees the sky;
- the **hidden chamber** six blocks down (7 x 7 x 3): behind the dais an iron hatch in the floor, flanked by two urns,
  opens with the lever on the wall above it; a ladder shaft leads down to a room with a second quartz crescent pointing
  at the one chest (`chests/temple_of_khonshu`: pool 1 = exactly one Scarab of Khonshu, pool 2 = 4-8 rolls of bones,
  rotten flesh, gold, sand, nuggets, string, spider eyes, emeralds, lapis, iron, quartz, a saddle, a golden apple
  (weight 3) or a diamond (2)), soul lanterns, candles, urns, a bone block and a skull.

**Items / blocks.** `scarab_of_khonshu`: epic, always glinting, stack 1, fire-resistant, tooltip; loot-only (no recipe,
no other loot table). `khonshu_altar`: sandstone/quartz crescent texture, `spent` blockstate -> cracked texture, light
6 while unspent; **unbreakable in survival** (bedrock strength, no loot table, pistons blocked) so a spent altar stays
spent. Block entity: `AltarState` DORMANT_READY / HOLDING_SCARAB / SPENT, the ritual player's UUID, kneel progress,
rebirth ticks. Both are in the Superheroes creative tab.

**The ritual** (`KhonshuRitual`, all server-side):
1. Right-click the altar holding the Scarab. Refused (action-bar message) if the altar is spent or busy, the player
   already has the pact, it is not night (`MoonKnightLunar.isMoonNight`) or the altar cannot see the sky
   (`MoonKnightLunar.hasSky(level, altar.above())`). Otherwise the scarab is consumed into the altar (the renderer
   shows it turning and bobbing on top, full-bright).
2. The player has 30 s to stand on the altar and sneak ("kneel"), then 200 unbroken ticks of kneeling: action-bar
   `Kneel before Khonshu ▮▮▮▯▯▯`, a moonbeam of end-rod / white dust particles falling onto the altar, a rising beacon
   tone, and Khonshu speaking five lines in chat. Standing up, stepping off, dawn, the sky being covered, dying,
   wandering 24+ blocks away or logging out cancels: the scarab pops back out on top of the altar.
3. At 200 ticks: a white flash, thunder, the fade to white (`MoonKnightRitualFadePayload(70)`: in ~8 ticks, held,
   out ~24); for 40 ticks the player is invisible, pinned to the altar top and cannot take damage
   (`ServerLivingEntityEvents.ALLOW_DAMAGE`) -- no real death, no drops, no death screen. Then they rise on the altar
   with a burst of particles and the totem sound, `MoonKnight.grant(player, fullMoon)` (full moon = 100 Vengeance) and
   the advancement `projecthero:moon_knight/pact` "Fist of Khonshu" (its own tab, `minecraft:impossible` awarded via
   `GraveboundCurse.award`). The altar cracks: SPENT forever. One ritual per altar at a time; different temples are
   independent.
- A ritual cannot survive a reload: an altar read back from disk while holding a scarab (server stopped, chunk
  unloaded) cancels on its first tick and returns the scarab. The only static state (`KhonshuRitual.REBORN`, players
  mid-rebirth) is cleared on disconnect and in `ServerStateReset`.

**Shared files touched (one line or entry each):** `ProjectHeroMod` (init), `ModNetworking` (payload),
`ProjectHeroModClient` (client init), `ModStructureTypes` / `ModStructurePieceTypes`, `ModCreativeTab`,
`ServerStateReset`, `HeroPackGuide` (structures chapter), gametest `fabric.mod.json`; lang via
`scratchpad/lang_mk_temple.js`; textures via `scratchpad/build_khonshu_textures.js`.

**GameTest hooks:** `KhonshuAltarBlockEntity.testDriven` (the level ticker skips it; tests call `KhonshuRitual.tick`),
`forcedNight` / `forcedSky` (per altar, instead of changing the shared world clock).

## Phases 3-6 (abilities)

- R `ability/MoonKnightDarts` + `entity/CrescentDartEntity` (+ `client/moonknight/CrescentDartRenderer`); X `MoonKnightCape` (glide velocity is applied client-side from the synced FLAG_GLIDING by `MoonKnightCombatClient`, the Spider-Man split); G `MoonKnightGrapple` (+ `MoonKnightLineRenderer` rope); Z `MoonKnightTruncheon` + `item/MoonKnightTruncheonItem` (staff via the `projecthero:staff` model predicate); shared helpers in `MoonKnightCombat`.
- C `MoonKnightAlters` (passives as transient modifiers, Steven loot + `mixin/MoonKnightVillagerPricesMixin`, Jake `mixin/MoonKnightVisibilityMixin`, radial picker `client/moonknight/MoonKnightAlterPicker` + `MoonKnightPickerMouseMixin`, Scholar's Sight `MoonKnightScholarSightRenderer`); V `MoonKnightKhonshu` (Moonbeam beacon-beam FX, Eye of Khonshu skull `MoonKnightSkull` + `MoonKnightKhonshuFxPayload`, Judgement, Khonshu's Resurrection).
- Tests: `MoonKnightAbilityGameTests`, `MoonKnightPowerGameTests`, `MoonKnightTempleGameTests`, `MoonKnightGameTests`.

## Phase 8 (v0.13.20)

- The suit materialises one pixel at a time (the suit is equipped at the start of H and revealed with `SymbioteDissolve` over the transformation clock -- `client/moonknight/MoonKnightReveal`; H again dissolves it before it is stripped).
- Screenshot-checked in the dev client: suit, cape (idle / shroud / glide), pixel reveal + dissolve, Temple exterior / hall / oculus, darts, truncheon, staff spin, Moonbeam, the Eye of Khonshu skull. Harness kept at `scratchpad/MkDebugHarness.java.txt`.
- Not verified: real multiplayer, glide feel under lag, the radial picker and Scholar's Sight outlines in play, natural temple generation in a desert.

## v0.13.21 -- new keys, the suit's gifts, three alter suits, the glide rework

The sections above describe v0.13.20; where they disagree, this section wins.

**Key layout** (`MoonKnightAbilityManager.moveFor`; cooldown ids in brackets; base values -- the lunar power still
multiplies damage / duration and divides cooldowns):

| Key | Tap | Hold | Sneak+key |
| --- | --- | --- | --- |
| R | Crescent Dart, 15 dmg, 1 s (`darts`) | Crescent Fan, 3.25 s (`darts_hold`) | Moon Mark, 8 s (`darts_sneak`) |
| G | Grapple Kick, 60 blocks, 5 s (`kick`) -- fires on press | -- | Shadow Step, 8 s (`kick_sneak`) |
| X | Dash, ~7 blocks, 1.5 s (`dash`) -- fires on press | -- | Grappling Line, 60 blocks, 2 s (`dash_sneak`) |
| Z | Moonbeam, 10 s (`khonshu`) | Eye of Khonshu (2 s hold, once per night) | Khonshu's Judgement, 13 s (`khonshu_sneak`) |
| C | Truncheon summon / stow | Staff Spin, 4 s (`truncheon_hold`) | Ground / Dive Slam, 6.5 s (`truncheon_sneak`) |
| V | next alter, 1.4 s (`alter`) | radial alter picker | alter special, 20 s (`alter_sneak`) |
| jump, then hold Sneak | Cape Glide (no cooldown) | | |
| hold right click | Cape Block (no cooldown, no time limit) | | |

Keys with no hold move (G, X) fire on the press (`MoonKnightMove.firesOnPress`). The old V (Khonshu) moved to Z, the
old C (Alters) to V, the old Z (Truncheon) to C; the Cape left the keys entirely; the old G tap (grapple to a block)
and Sneak+G Yank merged into Sneak+X; Shadow Step moved from Sneak+X to Sneak+G.

**Cooldowns cut ~35%** (old -> new ticks): fan 100 -> 65, Moon Mark 240 -> 160, Shadow Step 240 -> 160, grappling
line 60 -> 40, grapple kick 160 -> 100, staff spin 120 -> 80, slam 200 -> 130, alter switch 40 -> 28, alter special
600 -> 400, Moonbeam 300 -> 200, Judgement 400 -> 260. The shroud's 160 cooldown and 5 s cap are gone (Cape Block).
Dart damage 5 -> 15 (cooldown stays 20 = 1 s). Grapple range 24 x power -> a flat 60.

**The suit** (`MoonKnightConfig`, "the suit's own gifts"):
- Regenerates 1 HP every 5 ticks while transformed (`MoonKnight.regenerate`, from `MoonKnight.tick`).
- +7 attack damage (`MoonKnightAlters.SUIT_STRENGTH`, a transient modifier kept by `MoonKnightAlters.reconcile`, so it
  goes the moment the suit comes off).
- Every hit x0.8 (`MoonKnightDamage.incomingFactor`), stacking with the Cape Block (x0.7) and Steven (melee x0.85).
- Auto suit-up (`MoonKnightTransform.autoSuit`, from `AFTER_DAMAGE`): an unsuited pact-holder hit for more than 10 in
  one hit **or** left under 8 HP starts the normal 1.5 s suit-up (invulnerable while it forms). The user wrote "more
  than 10 damage and below 4 hearts"; either condition triggers (`callsTheSuit`). The hit size is the hit's base
  damage (before armour). Not within 3 s (`AUTO_SUIT_GRACE_TICKS`) of taking it off with H, so it can still come off.
- Suit up and suit down both take exactly 30 ticks; `MoonKnightReveal` maps the pixel reveal / dissolve onto the whole
  30 (the v0.13.20 reveal finished 4 ticks early and the dissolve took 10).

**Three alter suits.** `scratchpad/gen_moonknight_alters.js` converts the user's `moonknightmarc.bbmodel`,
`moonknightsteven.bbmodel` (Mr. Knight) and `moonknightJake.bbmodel`. The script checks the three rigs are identical
(12 cubes, same from / to / inflate / uv) -- the same 64x64 player-skin rig as the v0.13.20 model -- so all three
share `geo/moon_knight.geo.json` and each gets its embedded PNG byte for byte:
`textures/armor/moon_knight_{marc,steven,jake}.png` (Jake's is the old `moon_knight.png`, which is removed). Sets
`moon_knight_<alter>` are registered in `MoonKnightItems`; `MoonKnightArmorItem.armorSetId` picks the wearer's alter.
Item icons are re-cut from Marc's skin. Capes per alter (`MoonKnightAlter.capeTexture`): Marc the original off-white,
Steven crisp white without crescents, Jake charcoal with pale crescents.

**Alter swap rematerialise.** `MoonKnightAction.swapFrom / swapStart` (synced; set by `MoonKnightAnim.markSwap` on a
switch, a Fracture and a Fracture ending). For `ALTER_SWAP_TICKS` (30) the armour renderer draws
`client/moonknight/MoonKnightSuitSwap`'s composite texture: 32 frames per (old, new) pair, each taking a few more
pixels from the new suit (the same chest -> limbs -> head random sweep as the H reveal). One shared geometry means no
second, z-fighting pass. The cape changes over halfway through.

**Cape Glide rework.** Server: `MoonKnightCape.tickGlide` -- airborne 3+ ticks with Sneak held starts it; releasing
Sneak, landing, water or a ladder ends it; gliding into a mob kicks it (`glideKick`: 12 x power, knockback, one kick
per 10 ticks, `GLIDE_KICK` pose). Client: `mixin/MoonKnightGlidePoseMixin` switches the model's crouch off (and the
crouch render offset) and tips the whole body forward about 72 deg (up to 92 looking down) about the middle of the
body; `MoonKnightPose` spreads the arms (Z roll 1.3) and legs and takes the lean back out of the head. The cape becomes
webbing (`MoonKnightCapeLayer.drawWebbing`): columns right wrist / right shoulder / left shoulder / left wrist, all
running down to the ankles, so each side is a wrist-shoulder-ankle triangle with the back panel between; the corners
are read from the model's own arm / leg / body parts every frame, and the wings belly out and ripple.

**Cape Block.** `MoonKnightCombatClient.tickCapeBlock` sends `MoonKnightActionPayload.CAPE_BLOCK_START / STOP` while
right click is held with the main hand empty or holding the Truncheon, nothing being used, and (if the off hand holds
something) the crosshair on nothing -- so using items, placing an off-hand torch, eating etc. are untouched. Server
`MoonKnightCape.startBlock / stopBlock` re-validate (`canBlock`), keep `FLAG_CAPE_BLOCK` (the old `FLAG_SHROUD` bit and
its wrapped-cape / crossed-arms visuals) and the half-speed modifier.

**Grappling Line (Sneak+X).** `MoonKnightGrapple.fireLine`: a mob under the crosshair is reeled in (`REELS`: dragged at
1.2 blocks/tick, Slowness IV while dragged, stunned 1.5 s x power on arrival about 2 blocks away); a boss or anything
`isValidGrabTarget` refuses pulls the player to it instead; otherwise the block pull as before. Pulls and reels last at
most 70 ticks.

**Dash (X).** `ability/MoonKnightDash`: 1.5 blocks/tick along the flattened look for 5 ticks (server velocity re-sent
each tick), then bled to 30%; no fall damage during it and for 1 s after.

Tests: `MoonKnightAbilityGameTests` (layout, cape block, glide start / stop, glide kick, Shadow Step on Sneak+G, grapple
kick, dash, the line to a block, reeling a mob), `MoonKnightGameTests` (regen / +7 / -20% on and off with the suit,
auto suit-up and its grace), `MoonKnightPowerGameTests` (swap marks, per-alter visual sets).

## v0.14.3

- Suit passives (`MoonKnightAlters#reconcile`): +30% movement speed (`SUIT_SPEED_BONUS`, ADD_MULTIPLIED_BASE), jump
  strength +0.16 (0.58 total, ~2.2 blocks -- clears two), safe fall +1 block.
- Grappling Line / Grapple Kick range 60 -> 100 (`GRAPPLE_RANGE`); pull 1.3 -> 1.8 blocks/tick and max pull 70 -> 100
  ticks so a full-length line still arrives.
- Cape Glide speed 0.55 -> 0.85 (`GLIDE_SPEED`).
- X Dash follows the full 3D aim (`MoonKnightDash#dash` / `#push`) -- it used to be flattened to the horizontal.

## v0.14.4 -- truncheon

Numbers in the `// v0.14.4 truncheon` block at the end of `MoonKnightConfig` plus three in-place value changes
(`TRUNCHEON_DAMAGE` 6 -> 7, `STAFF_SPIN_DAMAGE` 6 -> 15, `TRUNCHEON_SLAM_BONUS` 4 -> 6). Sneak+C (ground / dive slam)
is untouched here.

**C summons on the press.** `MoonKnightMove#press` (new, called by `MoonKnightAbilityManager.handle` on every
non-sneak press edge) lets `MoonKnightTruncheon` put the truncheon in the main hand the moment C goes down; that
press's release is swallowed (`SUMMONED_ON_PRESS`) so it doesn't stow it again, and holding on into a HOLD still spins
the staff. A later tap with it out stows it. The held item handling is the v0.13.21 one, tightened:
- whatever was in the hand moves to the first free inventory slot (`Inventory#getFreeSlot`, hotbar first); a full
  inventory refuses with the action-bar message rather than deleting anything;
- on stow the item goes back to the hotbar slot the truncheon is in (so scrolling away and back doesn't matter), or,
  if the truncheon was dropped / lost, to the slot it was summoned into -- only if that slot is empty and the item is
  still the same stack in the slot it was moved to (otherwise it just stays where it is: never overwritten, never
  duplicated);
- the truncheon itself still can't exist anywhere else: stow / drop / container / un-suit / revoke / death delete it
  (`removeAll`, `sweepContainer`, `keepOne`, `MoonKnightTruncheonItem#inventoryTick`, the dropped-item discard).

**Damage.** The truncheon item's attack damage is 7 (tooltip "7 Attack Damage"); the suit's own +7 melee bonus and the
alter bonuses still stack on top, as for fists. Staff Spin is 15 x lunar power to everything within 3.5 x power.

**3-hit combo** (`ability/MoonKnightTruncheonCombo`, fed by `MoonKnightTruncheon.onMeleeHit` from `AFTER_DAMAGE`):

| Step | Move | Extra (ability hit, x power) | Knockback | Pose |
| --- | --- | --- | --- | --- |
| 1 | forehand, right to left | -- | vanilla | `TRUNCHEON_HIT_1` (60) |
| 2 | backhand, left to right | +3 (`TRUNCHEON_BACKHAND_BONUS`) | 0.6 x power | `TRUNCHEON_HIT_2` (61) |
| 3 | two-handed overhead smash | +6 (`TRUNCHEON_SLAM_BONUS`) | 1.2 x power + 0.3 lift | `TRUNCHEON_SLAM` (25) |

After the smash it goes round again. A hit more than `TRUNCHEON_COMBO_WINDOW` (30 ticks, 1.5 s) after the last one
starts over at step 1; a hit less than `TRUNCHEON_COMBO_MIN_GAP` (6 ticks) after the last counted one doesn't advance
it (a full-strength swing is 10 ticks at the truncheon's attack speed), so click-spamming can't reach the smash.
Missed swings don't touch it; stowing clears it. Night healing per hit is unchanged. Only hits with the summoned
truncheon in the main hand count (bare hands never do).

**Animations** (all from the synced `MoonKnightAction` anim id + start, so every viewer sees them):
- third person, `client/moonknight/MoonKnightTruncheonPose` (hooked from `MoonKnightPose.apply`; the old truncheon /
  spin / slam poses moved out of `MoonKnightPose`): draw (raised to the moon, swung down to ready), stow (new id
  `TRUNCHEON_STOW` 62, tucked to the hip), forehand, backhand, overhead smash with a lunge, and the Staff Spin, where
  both hands hold the staff out level and the whole body turns one full circle over `STAFF_SPIN_TURN_TICKS` (10)
  (`mixin/MoonKnightTruncheonSpinMixin`, a Y rotation in `PlayerRenderer.setupRotations`, so suit and cape turn with
  it). While the torso is twisted the shoulder pivots follow it (as vanilla's attack swing does);
- first person, `mixin/ItemInHandRendererTruncheonMixin` (before `renderItem`, inside vanilla's push / pop): the held
  truncheon sweeps left / right for the forehand / backhand, rises and chops down for the smash, twirls in the middle
  of the view for the spin, and flicks up on the draw -- on top of vanilla's swing.
- The combo poses are stamped when the server registers the hit, so they begin at the strike (1 tick fade-in) rather
  than with a slow wind-up.

Screenshot-checked with `scratchpad/TruncheonDebugHarness.v0144.java.txt` (pins each pose at a chosen tick; needs the
usual `HarnessCameraMixin` pointed at it): draw, forehand, backhand, smash raised / down, stow, spin at 1 / 3 / 6 / 9
ticks, and the first-person frames. Tests: `MoonKnightTruncheonGameTests` (press summons + release keeps it + tap
stows, item back in its slot, one copy only, revoke removes it; 7 / 15 numbers; combo steps, bonuses, anti-spam gap,
window reset, stow reset; bare hands never combo).

## v0.14.4 -- balance pass, three lunar states, AoE Moonbeam, the minute-long Eye, Steven's Fortune

Where this disagrees with the sections above, this section wins. New numbers live in the `// v0.14.4` block at the
end of `MoonKnightConfig`; changed ones are edited in place with a `// v0.14.4` comment. Tests:
`MoonKnightV0144GameTests` (+ updated `MoonKnightGameTests` / `MoonKnightAbilityGameTests` / `MoonKnightPowerGameTests`).
Lang: `scratchpad/lang_v0144_moonknight.js` (idempotent, re-runnable after a merge).

**Lunar power: exactly three states** (`MoonKnightLunar.State`, pure resolver `MoonKnightLunar.resolve(hasMoon, night,
phase)`): DAY x0.7 (daytime, and always in the Nether and the End -- `hasMoon` is false for a fixed-time or
skylight-less dimension), NIGHT x1.0 (any night that isn't a full moon), FULL MOON x1.5 (night, moon phase 0). The
per-phase table (gibbous 1.3 / quarter 1.15 / crescent 1.0 / new 0.8) and the no-sky -0.15 penalty (min 0.6) are gone,
so the base numbers everywhere are now the night numbers. Night-only behaviour is unchanged and keys off
`State.isNight()` (NIGHT or FULL MOON): homing darts, Moonbeam, Truncheon night heal, +3 kill Vengeance, longer glides;
FULL MOON alone opens the Eye and recharges the Resurrection. HUD: the sun (DAY), tonight's moon (NIGHT) or a haloed
full moon (FULL MOON) with `x0.7` / `x1.0` / `x1.5`; Left Alt shows the state's name
(`hud.projecthero.moon_knight.lunar.*`).

**Vengeance.** Kills: protector 5 -> 6, night 2 -> 3, day 1 -> 2. The two-idle-days drain is gone; instead it
regenerates 0.5% of the meter (0.5 points) a second while **out of combat** = no damage dealt to or taken from anything
for 5 s (`OUT_OF_COMBAT_TICKS`). Tracked in `MoonKnight.LAST_COMBAT` (a static map, cleared by `ServerStateReset`),
stamped from `MoonKnightDamage`'s `AFTER_DAMAGE` hook for any hit a pact-holder takes or deals (fall / fire damage count
as "taken"); applies to every pact-holder, suited or not, in `MoonKnight.tickSecond`.

**Suit passives.** Falls: x0.5 on top of the suit's x0.8 (`SUIT_FALL_DAMAGE_TAKEN`, in `MoonKnightDamage.incomingFactor`,
so a suited fall does 40% of vanilla; the glide / dash / grapple fall immunities still apply first). Step assist:
`Attributes.STEP_HEIGHT` +0.4 (0.6 -> 1.0) with the fixed id `projecthero:moon_knight_suit_step`, a transient modifier
kept by `MoonKnightAlters.reconcile` like the other suit passives.

**Alters.** (The "strength mode gets Resistance I" request meant Max Steel's Turbo Strength Mode -- see
MAXSTEEL_REFERENCE v0.14.4; Marc briefly had it during development and does not.) Steven Grant: no cape (`MoonKnightAlter.hasCape()`; `MoonKnightCapeLayer` skips
him, changing over halfway through a swap like the textures did), so no Cape Glide (`MoonKnightCape.canGlide`, ends one in
progress) and no Cape Block (`canBlock`); and **Fortune III** on everything he mines: `mixin/MoonKnightStevenFortuneMixin`
swaps the TOOL handed to `Block.getDrops(state, level, pos, be, miner, tool)` for `MoonKnightAlters.fortuneTool` -- a copy
with Fortune upgraded to at least III (the higher level wins, no stacking), a stick carrying it when bare-handed.

**R.** Crescent Fan: no charge -- the moment the hold registers, 5 darts (`DART_FAN_COUNT`, was 3 / 5 under a full moon)
each lock on (`CrescentDartEntity.lockOn`, synced `LOCKED` flag, 25 deg/tick turn, no boomerang while locked) to one of
the 5 closest hostiles within 32 blocks in line of sight (`MoonKnightDarts.fanTargets` -- the Green Lantern Missile
Barrage's selection without its in-front cone), a normal dart's damage each; spare darts double up, none = the old
spread. Moon Mark cooldown 8 s -> 5 s.

**G.** Grapple Kick 8 -> 20. Aim assist (`MoonKnightAim.kickTarget`, side-neutral): the crosshair ray first (squad-mates /
own pets are see-through), else the best target within 9 deg of the crosshair (body angular size subtracted; the angle
decides, distance breaks near-ties), in line of sight, up to 100 blocks. Lock-on preview, owner only:
`client/moonknight/MoonKnightKickPreviewClient` runs the same selection every other client tick and draws a turning ring
of moonlight over the pick (grey while the kick recharges); the HUD writes `[G] Zombie 23m` under the crosshair. In
flight the pull steers at the target's current body centre led by its horizontal velocity (`kickAnchor`, up to 8 ticks),
and the kick lands when the player's box grown by 0.8 touches the target's (`kickConnects`, or the old reach). With no
target it still does nothing but say so (the old behaviour).

**X.** Dash 5 -> 8 ticks (~7 -> ~12 blocks). Sneak+X Grappling Line: a squad-mate (`Squads.areAllies`) or your own pet on
the end of the line is reeled in too (`MoonKnightGrapple.pullableFriend`), with no Slowness and no stun; a reeled player
gets `ClientboundSetEntityMotionPacket` every tick (`drag`) as well as `hurtMarked`. A non-squad player while PvP is off
still counts as friendly-but-not-pullable and the line goes to the block behind.

**C.** Crescent Slam (Sneak+C) 6 -> 18; the dive slam is now 9 + 1.5 per block dived (18 from 6 blocks), max 36 (config
only -- the Truncheon code itself belongs to the truncheon rework).

**Z.** Moonbeam: 35 (was 10), 5 s cooldown (was 10 s), 10% Vengeance -- and an **AoE**: every foe (`isFoe`: never the
player, never a squad-mate) within 4.5 blocks of the strike point (was a 2-block column), full damage at the centre
falling off linearly to 60% at the edge (`moonbeamFalloff`), undead x2 as before. Eye of Khonshu: lasts a minute (was
30 s); the area is 30 blocks round the player and follows him; every second (`eyePulse`) the player's Strength II +
Speed II and every hostile's Glowing + Weakness II in the area are topped up to the Eye's remaining time (so late
arrivals are caught and the debuffs cover the whole minute; players are never debuffed), and every 2 s (`eyeStrike`) a
free Moonbeam (the same AoE, no casting pose) falls on a random foe in the area. Khonshu's Judgement: 15 s (was 10 s),
10% Vengeance (was free), 20 s cooldown (was 13 s); the target burns for 10 x lunar power a second (`judgementBurn`,
indirect magic credited to the player, boss-capped), and every point of damage the player deals it while judged -- the
burn and his own hits, via `MoonKnightKhonshu.onJudgedDamaged` from the `AFTER_DAMAGE` hook -- heals him. The old kill
refund (+20 Vengeance, 3 hearts) is gone; the target dying just ends it.

Not verified in a real client: the HUD sun / moon icons and kick label layout, the lock-on ring, the capeless Steven
render and swap, how the locked fan darts look in flight, the step assist feel, the Eye's strike cadence in a big fight.

