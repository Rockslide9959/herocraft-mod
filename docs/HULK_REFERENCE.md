# The Hulk (Hero-Tier power)

Package `com.projecthero.mod.hulk`. Hero-Tier key `hulk` (the spec's "Gamma power"). Built in five phases:

| Phase | Scope | Release |
|---|---|---|
| 1 | Core: synced data, rage loop, transform / revert, stats, rage HUD bar, test commands | v0.13.11 |
| 2 | Abilities: Thunderclap, Ground Smash, Super Leap, Sprint Smash, keys, cooldowns + HUD, JSON config | v0.13.12 |
| 3 | Looks: the user's Hulk model (GeckoLib, drawn in place of the player), animations, hidden armour, roar + shake | v0.13.12 |
| 4 | Origin: Gamma Serum (loot only), rare Gamma Lab ruin with a glowing Gamma Reactor block | v0.13.12 |
| - | v0.13.21: the serum only doses you (`GAMMA_DOSED` attachment); right-clicking a Gamma Reactor then overloads it (`hulk/GammaOverload`: 3 s charge, core blast power 18, ring of 8 power-9 blasts at 14 blocks, power-12 after-blast; BLOCK interaction so drop decay applies). The power is granted at detonation with a 10 s explosion/fall/fire shield, and the Hulk comes out | v0.13.21 |
| 5 | Balance + polish: Thor / Mjolnir rules, lifecycle, conflicts with other growing powers | v0.13.12 |
| - | Overhaul: new kit, passives, death save, riding, calm minigame, control / rampage, HUD | v0.13.14 |
| - | Willing vs unwilling change, kneeling change, cross-fade, rage glow, Banner HUD | v0.13.15 |

The spec asked for Fabric 1.21.11 + GeckoLib 5; the project is (and stays) on **Fabric 1.21.1 + GeckoLib 4.9**.

## v0.15.3: Gladiator Hulk (gear, menu, damage, looks)

Package `com.projecthero.mod.hulk.gladiator`. Thor: Ragnarok's arena Hulk (with Planet Hulk touches).

- **Items** (`GladiatorItems`, plain `GladiatorGearItem`s, stack 1, fire resistant, in the creative tab after the Hulk's
  items): `gladiator_helmet`, `gladiator_pauldron`, `gladiator_harness`, `gladiator_bracers`, `gladiator_kilt` (iron,
  gold, leather, red wool) and the end-game `gladiator_hammer` / `gladiator_axe` (netherite ingot + iron blocks + sticks).
  Shaped crafting-table recipes in `data/projecthero/recipe/gladiator_*.json`. Icons + the 3D hammer / axe item models
  (`models/item/gladiator_hammer|axe.json`, texture `item/gladiator_weapons.png`) and the recipes come from
  `scratchpad/gen_gladiator_items_v0153.js`.
- **Slots**: `ModAttachments.GLADIATOR_GEAR` -- a 7-entry `List<ItemStack>` (helmet, pauldron, harness, bracers, kilt,
  hammer, axe = `GladiatorGear.HELMET..AXE`), persistent, synced to everyone, **copyOnDeath (kept on death, with or
  without keepInventory)**. Each slot accepts only its own piece (`GladiatorGear.accepts`).
- **Screen**: tap **N as Banner** -> `GladiatorGear.OpenPayload` -> `GladiatorGear.openMenu` (refused with a message for
  non-Gamma players and for the Hulk -- the gear is locked on while he is out). `GladiatorGearMenu` (slots read / write the
  attachment directly; `locked()` while Hulk: no place / pickup / shift-click, `stillValid` false so vanilla closes it
  when he changes). Client: `client/hulk/GladiatorGearScreen` (5 armour slots left, hammer + axe right, "N/7 equipped"
  chip, hint wrapped to 4 lines), layout numbers in the common `GladiatorGearLayout` (gametested). The N chain branch is
  in `ProjectHeroModClient.handleMaxSteelTransform`; it only claims the press -- `client/hulk/GladiatorGearClient` sends
  the request when N comes back up within 8 ticks, so holding N (2 s) is still the calm-down for Banner, and in Hulk form
  N is only the calm-down.
- **API** (`GladiatorGear`): `hasFullKit(p)`, `isGladiator(p)` (full kit AND Hulk form), `equippedCount(p)`,
  `weaponAway(p, axe)` / `setWeaponAway(sp, axe, away)` (attachment `GLADIATOR_WEAPONS_AWAY`, bit 0 hammer / bit 1 axe,
  synced, not persistent; cleared on death, on login and every tick he is not the Hulk), `reduce(p, amount)`.
- **Damage**: Gladiator Hulk takes `DAMAGE_FACTOR` (0.9) of every hit that gets past the Hulk's own rules -- in
  `HulkDamage.allowDamage` (ordinary hits are now vetoed and re-applied at 90% for him; lava / explosions get both cuts).
  Nothing else comes from the gear.
- **Looks**: new bones in `geo/hulk.geo.json`, all named `gladiator*` and added by
  `scratchpad/gen_hulk_gladiator_v0153.js` (idempotent -- re-run it after `gen_hulk.js`): `gladiator_helmet` +
  `gladiator_paint` (head), `gladiator_pauldron` (left_arm), `gladiator_harness` + `gladiator_kilt` (body),
  `gladiator_kilt_right` / `_left` (legs), `gladiator_bracer_right` / `_left`, and the hand weapons **`gladiator_hammer`
  (child of right_arm)** and **`gladiator_axe` (child of left_arm)**. Their per-face UVs point into their own texture
  `textures/entity/hulk_gladiator.png` (256x256 over the 64-unit UV space, 2 texels per model pixel), so `hulk.png` (also
  the first-person arm skin) is untouched. `HulkRenderer.preRender` hides every gladiator bone in the main pass;
  `client/hulk/HulkGladiatorLayer` re-renders the same animated model with only the gladiator bones (minus a thrown
  weapon) and the gladiator texture, only while `isGladiator`, fading with the Hulk. A Hulk without the full kit looks
  exactly as before. Animations that key `gladiator_hammer` / `gladiator_axe` work (they are real bones of the model).
- Tests: `gametest/GladiatorGearV0153GameTests`.
/^>>>>>>> 92dc6c03/d
## v0.15.3: Gladiator Hulk -- the 12 moves

The Hulk wearing the **full gladiator gear** (`hulk/gladiator/GladiatorGear#isGladiator` -- the gear, its items and the
weapon bones are the gear side of v0.15.3) fights with an **axe in the left hand and a hammer in the right**. While
`GladiatorAbilities#active` (Hulk form + full kit; tests use `forceKitForTests`), `HulkAbilityManager#handle` sends
R/G/X/Z/V/C to `GladiatorAbilities#handle` instead of the bare-handed kit; Shift+key is the second move. Rage,
control / rampage, the death save, Calm Down, riding and every passive are unchanged. Take a piece off (or change back)
and the bare-handed kit returns at once; anything running stops and thrown weapons come home.

| Key | Tap | Shift + key |
|---|---|---|
| R | **Axe Cleave** -- 160 deg arc, 5.5 blocks, 34, knockback 2.4, bleed 2/s x 4 s (5 s) | **Hammer Uppercut** -- the one target in front (5 blocks), 36, launched up 1.6 (6 s) |
| G | **Hammer Quake** -- 60 deg cone, 14 blocks, 36 (falls to 60% at the end), fissure cracks blocks <= hardness 0.8 (7 s) | **Earthsplitter** -- an 18 x 3 line erupting 2 blocks a tick, 30, lift 1.2 (9 s) |
| Z | **Champion's Roar** -- 8 s +6 attack, +0.1 knockback resistance (= 1.0), +15 rage; 12 blocks: Weakness II 5 s, Slowness IV 1 s, mobs flee (20 s) | **Weapon Clash** -- 7 blocks, 18, stun 1.5 s (Slowness X, Weakness III, Mining Fatigue III) (12 s) |
| X | **Arena Leap** -- `AbilityHelpers.ballisticLaunch` 28 blocks, lands in a slam: 32 in 6 + crater 3 (8 s) | **Meteor Dive** -- straight up (2.0), dive at 2.8 steered every tick at the look point (60 blocks): 55 in 3.5 + crater 2.5 (15 s) |
| C | **Axe Throw** -- `GladiatorAxeEntity`, 24 blocks at 1.8, 26 each way, comes back to the hand (5 s) | **Hammer Hurl** -- `GladiatorHammerEntity`, arc, 30 in 4.5 on landing, stuck; any C recalls it (20 on the way home); comes home alone after 30 s (8 s) |
| V | **Gladiator Whirlwind** -- 3 s spin (you can move): 8 every 5 ticks within 3.5 (hurt window reset), pull within 7, ends with a slam 26 in 5 (12 s after) | **Arena Grapple** -- `HulkGrab#grabbable` target pinned in front, 5 blows of 9 every 10 ticks; Shift+V again throws it (2.0, +14) (10 s after) |

- **Numbers**: `config/projecthero_hulk.json` -> `gladiator` section (`HulkConfig.Gladiator`); config version 4 only
  introduces the section (nothing a server set is reset).
- **Targets / damage**: everything goes through `HulkCombat` (`targets` = `HeroTargets#canHarm`, squads / PvP,
  `strike` = `AbilityHelpers#hurtLands`, bosses take damage but are never shoved). Grapple uses `HulkGrab#grabbable`
  (V's rules: no bosses, nothing over 2.5 wide / 3.5 tall, no squad-mates). The quake fissure respects
  `HulkCombat#canBreakBlocks` (the world `blockBreaking` / `respectMobGriefing` switches) and `mayInteract`.
- **Weapons in hand**: a move that swings a weapon that is away is refused with a message (Earthsplitter / Cleave /
  Whirlwind / Clash / leaps need the axe; Quake / Uppercut / Grapple / Whirlwind / Clash / leaps need the hammer).
  `GladiatorGear#setWeaponAway(p, axe, true)` from the press (the hand empties), cleared on catch
  (`GladiatorAbilities#weaponHome`), on a lost entity (`tickAway`), on revert / revoke / death / logout / dimension
  change (`GladiatorAbilities#clear` from `Hulk`), and on join (`onJoin`).
- **Thrown entities** (`GladiatorEntities`: `thrown_gladiator_axe`, `thrown_gladiator_hammer`; `noSave`, server-driven,
  `GladiatorThrownWeapon` base): swept `Level#clip` + segment hits; never step into a non-entity-ticking chunk
  (`isPositionEntityTicking`): out-bound they turn round / land, home-bound they are simply back. Drawn by
  `client/hulk/GladiatorWeaponRenderer`: the gear's item (`GladiatorItems.AXE` / `HAMMER`) via `ItemRenderer`, 2.2x,
  aligned to the heading and tumbling; a stuck hammer stands head-down.
- **Busy**: Whirlwind, Grapple and the two leaps block the other moves (`GladiatorAbilities#busy`); a rampage or the
  calm-down ends them.
- **Animations** (`hulk.animation.json`, generated by `scratchpad/add_gladiator_moves_anims_v0153.js`, existing bones
  only, every bone keyed at the first and last frame): `gladiator_axe_cleave`, `_hammer_uppercut`, `_hammer_quake`,
  `_earthsplitter`, `_champions_roar`, `_weapon_clash`, `_arena_leap` (hold), `_meteor_dive` (hold), `_slam`,
  `_axe_throw`, `_hammer_hurl`, `_hammer_recall`, `_whirlwind` (loop, root spins 360 per 0.5 s), `_grapple` (loop,
  one blow per 0.5 s). Ids 40-53 in `GladiatorAnims` (HulkState's own are 0-9), on the synced `animId` / `animStart`.
- **HUD** (`HulkHud#renderGladiatorKeys`): "Gladiator Hulk", six gold boxes; each shows the tap move's cooldown (the
  Shift move's while Shift is held, marked with a dot) and a strip for the other move; Shift / Alt list the names
  above the boxes. No H/N boxes.
- Guidebook: "Gladiator Hulk -- Moves" in the Hulk chapter (also the I screen). Lang: `scratchpad/lang_v0153_gladiator_moves.js`.
- Tests: `gametest/GladiatorMovesGameTests` (14: kit switch, cooldown / weapon gating, each of the 12 moves on a husk,
  axe return, hammer stuck + recall, revert / clearTransient clean-up).

## v0.13.17: rage rules, death save, carrying, webs

- **Rage**: 1 per point of damage TAKEN in both forms (`RAGE_PER_DAMAGE_TAKEN` / `HULK_RAGE_PER_DAMAGE_TAKEN`). Banner gains
  nothing from damage he deals; he cools 2/s once 5 s pass since `Combat.lastHurtAt` (NOT `lastCombatAt`). The Hulk gains
  `HULK_RAGE_PER_HIT` (2) per AFTER_DAMAGE event he causes, and burns 0.75/s only after `HULK_OUT_OF_COMBAT_TICKS` (5 s)
  since max(lastCombatAt, end of the change).
- **Death save**: no cooldown. Banner always changes (unwilling) instead of dying; a Hulk who takes a killing blow dies.
  Not saved: /kill, the void, inside a Titan, All Might Power Form. `Combat.deathSaveReadyAt` is now unused (kept for codec).
- **Carrying squad-mates** (`HulkGrab`): allies bypass the `HulkCombat.targets` filter; Shift+V / the mate sneaking =
  `setDown`; thrown mates skip the impact strike; `SAFE_LANDING` (8 s) vetoes their fall damage in `HulkDamage`.
- **Webs**: `EntityWebMixin` skips cobweb slowdown for a Hulk; `HulkCombat.breakable` counts cobweb soft, and
  `breakAhead` breaks it despite its empty collision shape.
- **Rampage** targets anything within `HulkControl.RAMPAGE_RANGE` (100) that is `aboveGround` (within 2 blocks of the
  MOTION_BLOCKING_NO_LEAVES heightmap).
- **Thunderclap** range 12 -> 25; config v3 moves an old 12.0 up to 25 (a server-chosen value is kept).
- **Head spin on throw/pickup**: those clips do not key the head, so GeckoLib left last frame's value in the bone and
  `HulkModel.setCustomAnimations` kept adding the look offset on top (logged: -10 deg per frame). It now subtracts its own
  previous offset when the bone still holds exactly what it wrote. `GeoBone.hasRotationChanged()` is false even for
  keyed bones at that point -- do not use it for this.

## v0.13.15: willing and unwilling changes

- **Willing** (H at 75+): `Hulk.transform(p, false)` -- 30-tick growth, `HulkState.Combat.unwilling = false`, and
  `HulkControl.tick` does nothing for him: no prompts, no rampage, ever.
- **Unwilling** (rage 100, or the death save): `transform(p, true)` sets `unwilling`. `Hulk.changing` is true for
  `HulkConfig.FORCED_CHANGE_TICKS` (20 kneel + 60 growth + 24 rise): movement / jump multiplied to 0 (transient
  modifiers, so the client stops too), abilities refused (`HulkAbilities.canAct`), melee refused, all damage vetoed
  (`HulkDamage`), no calm-down. Growth waits for the kneel, then runs over 60 ticks. Roar + shake at the rise
  (`Hulk.roar`); heartbeats / tearing sounds / gamma while kneeling (`tickForcedChange`). Control grace starts when the
  change ends.
- **Cross-fade**: `Hulk.visibility(player, partial)` 0..1 on the growth clock. `PlayerRendererHulkMixin` lets vanilla
  draw Banner first (see-through + no render layers via `client/mixin/LivingEntityHulkFadeMixin`), then draws the Hulk on
  top at RETURN (`HulkRenderer.renderFaded`: `entityTranslucent` + alpha in `getRenderColor`); solid = HEAD-cancel as
  before. **Draw order matters**: drawing the GeckoLib Hulk *before* the vanilla player (at HEAD) visibly sheared
  Banner's model mid-fade -- keep the fading Hulk after vanilla. During the unwilling change the Hulk is drawn from tick
  0 at alpha 0 so its GeckoLib clip starts in sync.
- **Kneel**: Banner = `client/hulk/HulkPose` (vanilla model, from `HumanoidModelMixin`: hips down 8 px, legs folded
  back 1.23 rad, 0.38 rad hunch from the hips, hands on the head, a shiver); Hulk = `animation.hulk.transform_forced`
  (5.2 s, generated by `scratchpad/add_hulk_forced_anim_v01315.js`: same kneel, fists into the ground, rise, roar).
- **Rage glow**: Banner above 75 -- `Hulk.rageGlow`, green dust every 3 ticks, denser and with a faster heartbeat toward 100.
- **HUD**: Banner shows "Rage N%" + bar (throbbing past 75), or "Exhausted Ns" with the bar running the timer down;
  "THE HULK IS TAKING OVER..." + progress mid-screen during the unwilling change.
- Verified in-client with the screenshot harness (`scratchpad/V01315DebugHarness.java.txt` + the temporary
  `HarnessCameraMixin.java.txt` camera orbit -- a detached camera entity hides the local player).

## v0.13.14 overhaul

New kit (keys -> `HulkAbilityManager`): **R Power Punch** (3 wide x 6 long, 30, 5 s), **G Ground Smash** (30 in 7 blocks +
crater, 5 s), **Z Thunderclap** (22, 8 s) / **Shift+Z HULK SMASH** (hold 5 s, 100 forward 25 + round 30, crater, 60 s),
**X Super Leap** (15-70 blocks, 2 s), **C Charge** (8 s forced run, 20 to everything run through, breaks soft blocks, 12 s
after), **V Grab** (`HulkGrab`: throw / Shift+V crush 26 / Shift+V empty-handed = `HulkBoulderEntity` earth chunk, 30
in 4 on impact; 8 s). Sprint Smash is now a passive (config switch). All numbers: `config/projecthero_hulk.json` (config
version 2 resets a 0.13.13 file's abilities section).

Passives: +19 attack (20 punches), +1.5 attack knockback, +50% speed, +20 armour / +8 toughness, 0.75 HP per 5 ticks,
fire / arrow / fall immune, lava x0.25, explosions x0.5 (`HulkDamage.allowDamage`), stone-tool hands (`HulkBareHands`
via `PlayerMixin`), armour torn off on the change (-50 durability, dropped) and bounced while out.

Systems:
- **Death save** (`Hulk.tryDeathSave`, ALLOW_DEATH): every 3 min a fatal hit brings the Hulk out (or back) at full HP and
  100 rage; the 3 px dot left of the rage bar shows it. /kill and the void still kill.
- **Riding** (`HulkRiding`, `mixin/EntityHulkRideMixin`): a squad-mate right-clicks to ride his back, one at a time.
- **Calm down** (`HulkCalm` + `client/hulk/HulkCalmScreen`): hold N 2 s after 3 s out of combat; a breathing exercise
  (hold Space as the guide ring swells, let go as it shrinks). The client reports in/out-of-rhythm ticks once a second;
  the server caps them. Hurt = broken. Calmed to 0 as the Hulk = revert without exhaustion.
- **Control / rampage** (`HulkControl`): 8 s without dealing damage -> control drains 6/s, W/A/S/D prompts every 2.5 s
  (1.5 s window, +25 / -15). 0 -> 15 s rampage (targets nearest living thing, attacks, leaps, stomps, smashes blocks;
  abilities, riders and calm locked out), then 60 control back.
- State: `HulkState.Combat` (nested codec -- the record codec caps at 16 fields).
- HUD (`HulkHud`): Banner = "Rage N%" + bar; Hulk = keys, "Hulk Form", "Rage N%" + bar, control bar only while slipping;
  prompts / rampage / HULK SMASH / calm-hold above the crosshair.
- Animations added: power_punch, hulk_smash_charge, hulk_smash, charge, hold (arms layer), pickup, throw, crush.
- Tests: `HulkGameTests` (28; 31 as of v0.13.15).

## Files

| Role | Where |
|---|---|
| State (persistent, `copyOnDeath`, synced to all) | `hulk/data/HulkState` -> `ModAttachments.HULK_STATE` |
| API: grant / revoke, rage, the change, stats, tick, lifecycle | `hulk/Hulk` |
| Numbers | `hulk/HulkConfig`: core loop = static finals; abilities + world rules = `config/projecthero_hulk.json` |
| The four abilities (task queue, cooldowns, landing, Sprint Smash) | `hulk/HulkAbilities` |
| Keys -> abilities | `hulk/HulkAbilityManager` (routed from `AbilityRouter`; while he is out the Hulk takes the keys first) |
| Rage hooks, fists-only, no fall damage | `hulk/HulkDamage` |
| H key | `network/HulkActionPayload` + `ProjectHeroModClient#handlePowerSelect` |
| Guns refused | `ModNetworking` firearm-fire receiver |
| HUD | `client/gui/HulkHud` -- R / G / X cooldown boxes + C Sprint Smash state, leap charge hairline, rage bar in Thor's meter spot |
| Model | `client/hulk/HulkAnimatable` (GeoReplacedEntity), `HulkModel`, `HulkRenderer`, `client/mixin/PlayerRendererHulkMixin`; first-person arm skin in `PlayerRendererFleshHandMixin` |
| Assets | `geo/hulk.geo.json`, `animations/hulk.animation.json`, `textures/entity/hulk.png` -- `scratchpad/gen_hulk.js` from `3d minecraft models/hulk/hulk.bbmodel` |
| Items / block | `hulk/item/HulkItems` (`gamma_serum`, `gamma_reactor`), `hulk/item/GammaSerumItem`, `hulk/block/GammaReactorBlock`; textures `scratchpad/gen_gamma.js` |
| Structure | `hulk/worldgen/GammaLabStructure` + `GammaLabPiece`; `worldgen/structure(_set)/gamma_lab.json`; loot `chests/gamma_lab.json` (always one serum) |
| Commands | `command/HulkCommand` -- `/hulk grant|revoke|setrage` (op 2, kept as testing helpers); `/projecthero power grant hulk` |
| Tests | `gametest/HulkGameTests` (18) |

## Replacing the model in Blockbench

Keep these names and the files drop straight in (re-run nothing):

- **Geometry** `geo/hulk.geo.json`, identifier `geometry.hulk`, 64x64 texture. Bones: `root` > `body` > `head`,
  `right_arm`, `left_arm`; `root` > `right_leg`, `left_leg`. Player-sized -- the 1.8x comes from the scale attribute.
  `head` is turned to the look direction on top of the animation.
- **Animations** `animations/hulk.animation.json`: `animation.hulk.idle` (loop), `walk` (loop), `run` (loop, while
  sprinting), `clap` (Thunderclap; impact at 0.3 s), `smash` (Ground Smash; impact at 0.5 s), `leap_charge` (hold),
  `leap` (hold, in the air), `transform` (1.5 s), `punch` (melee swing, right arm only -- layered over the rest).
- **Texture** `textures/entity/hulk.png`.

## Rules

- **Rage (0-100)**, Gamma players only. Banner: +2.5 per damage taken, +0.8 per damage dealt, -0.5/s after 15 s calm.
  A Gamma Reactor within 4 blocks: +3/s. Exhausted Banner builds none.
- **H** at 75+ lets the Hulk out; 100 forces it. Hulk: -1 rage/s, +1.5 per damage taken. 0 -> Banner, Weakness +
  Slowness 8 s.
- **Stats:** scale 1.8x (eased over 30 ticks), +12 attack, +40 max health (+20 HP on the way in), 0.9 knockback
  resistance, +8 toughness, +0.5 step, +1.5 reach, 2 HP/s regeneration, no fall damage.
- **Abilities** (Hulk only): R Thunderclap (12 dmg cone, 12 blocks, 70 degrees, shatters glass / ice / leaves /
  plants, 8 s), G Ground Smash (16 dmg, 7 blocks, 10 s), X Super Leap (hold 1.5 s: 10-45 blocks; landing 8 dmg 4.5
  blocks; 6 s), C Sprint Smash toggle (sprinting breaks blocks up to hardness 3 in front; no block entities).
  Bosses (`TitanCombat.isBoss`) take damage but are never shoved.
- **World switches** (json): `blockBreaking`, `sprintSmashEnabled`, `maxBreakableHardness`, `dropBrokenBlocks`,
  `respectMobGriefing`, `screenShake`.
- **Thor:** never both -- claiming Hulk revokes Thor (worthy or bound), claiming Thor revokes Hulk. The Hulk form can
  never lift Mjolnir (`Worthiness.canLift` / `wouldAscend`, `ItemEntityMixin`, `WorthinessEnforcer` ejects it), even a
  Hero of the Village. Creative still bypasses worthiness, as for everyone.
- **Other growing powers:** no Hulk inside a Titan or in All Might's Power Form, and no Titan shift / All Might form
  change while he is out (the scale bonuses would stack).
- **Lifecycle:** death -> respawn is a calm Banner; logout -> join comes back as Banner with rage kept (a relog cannot
  restore health above 20); dimension change keeps the Hulk.

## Not covered by tests / known limits
- Hulk + Wolverine held together: plain H goes to the Hulk (Wolverine's claw toggle unreachable).
- Another client's Hulk model and animations only checked in single player (the harness), not on a server.
- Sprint Smash measures speed from position change on the server; a slow walk into a wall does nothing by design.

## v0.14.3

A rampaging Hulk hits his squadmates. `Squads#shields(dealer, victim)` is the one squad-protection check (same squad AND
the dealer is not `HulkControl.rampaging`); used by the friendly-fire veto, `AbilityHelpers#hurtLands` and
`HulkCombat#ally` (so the rampage AI also targets them). PvP / `abilityPvpDamage` still apply. The squadmate still
can't hurt the Hulk back.

## v0.14.4

The squad can fight back. While a Hulk rampages, squad protection between him and his squad-mates is lifted in **both**
directions: `Squads#shields(dealer, victim)` is false when *either* side is `HulkControl.rampaging`, so the
friendly-fire veto (`ALLOW_DAMAGE`), `Squads#isFriendlyFire` and `AbilityHelpers#hurtLands` let a squad-mate's sword,
arrows and abilities land on him. `Squads#areAllies(a, b)` is also false when either is rampaging, so every ability that
skips squad-mates (AoE blasts, grabs, turrets, summoned servants, Symbiote / Telekinesis / Gravity handlers, ...)
treats him as a target. The ability-specific checks that used `SquadManager#sameSquad` directly for *offensive*
filtering now go through `Squads.areAllies` too: Mjolnir's volley, Wolverine's grab, the Omega Beam, Titan Shifter
combat, Green Lantern constructs, Moon Knight / Khonshu, and `BatchA#isAlly` (so his squad's buffs also skip him while he
rampages). Narrow on purpose: only the rampaging Hulk loses ally status. His squad-mates stay allies of each other, and
it all reverts the tick the rampage ends. The server PvP setting and `abilityPvpDamage` still gate everything. Riding
(`HulkRiding`) and Healing Water still use plain squad membership. Test:
`V0144GraveHulkGameTests#aRampagingHulksSquadCanFightBack`. The v0.14.3 test now asserts the mate *can* hit back.

