# Super Soldier (Hero-Tier power)

Package `com.projecthero.mod.supersoldier` (client: `com.projecthero.mod.client.supersoldier`). Hero-Tier key
`super_soldier`. Added in v0.14.8; v0.14.9 gave him bare-handed G moves, the craftable Adamantium Shield (thrown with C)
and the craftable Captain America suit. A Captain-America-style peak human: always on (no transformation, H stays the power
selector), strong but deliberately below the Hulk / Thor / All Might.

## Files

| File | What |
|---|---|
| `SuperSoldier` | The API: state access, grant / revoke, `reconcile` (every attribute modifier), the passive tick, lifecycle hooks |
| `SuperSoldierAbilities` | The eleven moves, targeting (`canTarget`: no self / squadmates / own pets / creative players; players only with PvP), `strike` (Onslaught x1.25, Focus crit x1.5, boss cap), the per-player move tables |
| `SuperSoldierAbilityManager` | Slot dispatch (Shift read server-side), per-tick upkeep (incl. the suit gate, for every player), cooldown lookups for the HUD |
| `SuperSoldierArmorGate` | v0.14.9: only a Super Soldier may wear the suit -- ejects it from anyone else every tick |
| `SuperSoldierDamage` | ALLOW_DAMAGE: 30% reduction, roll i-frames, no fall damage from his leaps, Focus crits on punches; AFTER_DAMAGE: Onslaught punch shock; the `projecthero:serum_rejection` damage type key |
| `SuperSoldierSerum` | Drink outcome with an injected roll (`drink(player, refined, roll)`) |
| `SuperSoldierConfig` | Every number |
| `SuperSoldierSetup` | One init call: items, recipe serializer, entity type, damage rules |
| `data/SuperSoldierState` | The synced, persistent, copy-on-death attachment `projecthero:super_soldier_state` |
| `entity/SoldierShieldEntity` | The thrown ricochet shield (server-driven). v0.14.9: carries the real thrown `ItemStack` (synced for rendering, saved in NBT) and hands the same stack back |
| `item/SuperSoldierItems`, `item/SuperSoldierSerumItem` | Unrefined / refined serum (drinkable, 32 ticks, leaves a bottle); `soldier_shield` (display-only, the thrown shield's fallback look); the suit's armour material |
| `item/AdamantiumShieldItem` | v0.14.9: `adamantium_shield`, extends vanilla `ShieldItem`, no durability (unbreakable), fire resistant |
| `item/SuperSoldierArmorItem` | v0.14.9: the four `captain_america_*` pieces (`SuperheroArmorItem`, set id `captain_america`); `use()` refuses a non-Super-Soldier |
| `recipe/SuperSoldierSerumRecipe` | `CustomRecipe` + `SimpleCraftingRecipeSerializer`, type `projecthero:super_soldier_serum` |
| client `SuperSoldierHud`, `SoldierShieldRenderer`, `AdamantiumShieldRenderer`, `SuperSoldierClient` | Mono HUD (R G Z X V + C, no bars), the spinning thrown shield (the real stack: the stepped dish with after-images / vanilla shield with its banner), the shield item renderer (`BuiltinItemRendererRegistry`: GUI, ground, frame), registration |
| `CapShieldStow` (common) | v0.17.5: which Captain America shield is slung on a player's back (first one in the hotbar while neither hand holds one); synced attachment `projecthero:cap_shield_stowed` (all trackers, not saved), re-checked every 5 ticks |
| client `CapShieldModel`, `CapShieldPose`, `CapShieldBackLayer`; mixins `CapShieldPoseMixin`, `CapShieldHeldLayerMixin`, `CapShieldFirstPersonMixin` | v0.17.5: the stepped-dish model and its three textures; the strapped-forearm placement, arm poses (eased block, bash, throw, reach, catch) and first-person placement; the back sling |

## Obtaining

- **Unrefined Super Soldier Serum**: crafting table, shapeless special recipe
  (`data/projecthero/recipe/unrefined_super_soldier_serum.json`): exactly one drinkable Potion of Strength, one of
  Swiftness and one of Leaping (long / strong variants accepted; splash, lingering, custom-effect and other potions
  rejected). Special recipes are not in the recipe book -- the guide says so.
- Drinking it: **10%** grant, **90%** death (`projecthero:serum_rejection`, tagged `bypasses_armor`, `bypasses_effects`,
  `bypasses_enchantments`, `bypasses_invulnerability`, `bypasses_resistance`, `no_knockback`; message "X's body
  rejected the unrefined serum"). Creative included. A power's own death save (Symbiote, Khonshu) may still intervene.
  The roll is the server level's RNG.
- **Refined**: blasting recipe, `cookingtime` 12000 (10 minutes), `refined_super_soldier_serum_blasting.json`. Always
  grants.
- Granting calls `HeroTiers.claimPrimary(player, "super_soldier")` (one Primary power: the old one is replaced) and
  shows a title + chat message. Also: `/projecthero power grant super_soldier [player]`, and the Heroic random serum.

## Passives (fixed-id transient modifiers, reconciled every tick)

| | Value |
|---|---|
| Movement speed | +50% (ADD_MULTIPLIED_BASE 0.5) |
| Max health | +10 (+5 hearts) |
| Unarmed melee | +7 while the main hand is empty |
| Damage taken | x0.7 (cancel-and-reissue; not for /kill, the void, bypass-invulnerability) |
| Jump | apex 2.25 blocks (JUMP_STRENGTH x(v/0.42 - 1), v solved by simulation) |
| Safe fall | +3 blocks |
| Knockback resistance | +0.2 |
| Attack speed | +15% |
| Regeneration | 1 HP / 2 s after 5 s out of combat (food > 6) |
| Immunities | Poison, Nausea |

## Moves (R / G / Z / X / V, each with a Shift variant; C the shield throw; H / N never)

| Key | Move | Numbers | Cooldown |
|---|---|---|---|
| R | Combo Strike | 3 punches, 5 ticks apart: 5 + 5 + 7 (finisher knockback 1.0). Needs a target within 4 blocks | 5 s |
| Shift+R | Uppercut Launcher | 10 damage, target launched ~5 blocks up (vy 1.05) | 8 s |
| G | Flying Kick | lunges at the enemy in front within 8 blocks (1.15 b/t, up to 10 ticks), lands within 2.4 blocks (at once if already that close): 12 damage, knockback 1.5, lift 0.3, then a small hop back; no fall damage for 2 s. No target = no cooldown | 8 s |
| Shift+G | Judo Takedown | grabs the enemy in front within 3.5 blocks, hoists it over his shoulder, 5 ticks later slams it to the ground behind him (in front if a wall is behind): 14 damage, Slowness IV + Weakness I 1.5 s. Bosses are not moved, just hit. No target = no cooldown | 10 s |
| Z | Leaping Slam | leap 4 blocks up / 0.8 forward, lands (or after 3 s) for 12 damage in 5 blocks (60% at the edge), knockback 1.2, lift 0.4 | 12 s |
| Shift+Z | **Super Soldier Onslaught** (ultimate) | 8-damage shockwave in 6 blocks, then 10 s: +5 melee, Resistance I, Speed I, moves x1.25, each punch shocks enemies within 3 blocks of the target for 4 | 100 s |
| X | Tactical Roll | 1.35 b/t dash in the movement (or look) direction, 10 ticks of invulnerability | 4 s |
| Shift+X | High Leap | 5 blocks up, 1.25 forward, no fall damage for 5 s | 8 s |
| V | Battle Cry | self + squadmates in 16: Strength I + Speed I 10 s; hostiles (and enemy players) in 12: Weakness 8 s + Slowness I 4 s | 30 s |
| C | Shield Throw | needs a shield in either hand (main first). **Adamantium Shield**: 9 per hit, up to 4 enemies, range 24, 5 s. **Any other shield** (`ShieldItem`): 6 per hit, up to 3 enemies, range 16, 6 s. Homes on the enemy in the crosshair / cone first; ricochets to the nearest unhit hostile within 12 blocks (line of sight from its centre or 0.5 above to the target's centre or eyes; else the nearest within 6 blocks blind), homing through blocks once chosen; glances off walls only while flying free; returns through blocks. No shield = an action-bar hint, no cooldown | 5 s / 6 s |
| Shift+V | Tactical Focus | every hostile / enemy player in 30 blocks glows 10 s; 8 s of x1.5 crits (punches and moves) on the marked | 25 s |

Bosses (max health >= 300, or `TitanCombat.isBoss`) take at most 6% of their max health per hit and are never knocked
back or launched. A 4-tick global lock stops moves stacking; the combo locks for 12 ticks, the kick for 12, the takedown for 9.

## HUD

`SuperSoldierHud`: bottom right, mono (black / gray) boxes in the order R G Z X V; each box shows the longer of its two
moves' cooldowns. v0.14.9: **no bars** (the ultimate and Tactical Focus hairlines were removed; Z's box is outlined white
while Onslaught runs, V's while Focus runs). A sixth box, **C**, appears stacked on top of V only while he holds a
shield (or while a throw is cooling down, so the timer does not vanish while the shield is in the air); the five fixed
boxes never move (a C box left of R ran under the hotbar on small screens). Left-Alt shows short move names.

## The shield item and the throw's item safety (v0.14.9)

- **Adamantium Shield** `projecthero:adamantium_shield`: `AdamantiumShieldItem extends ShieldItem`, stack 1, no durability
  (vanilla only damages / axe-disables `minecraft:shield` itself, so it is never worn down or disabled), fire resistant,
  epic. Recipe (shaped): `IRI / WNW / IBI` -- I iron block, R red dye, W white dye, N netherite ingot, B blue dye.
- Look: see **The shield model, holding and animations (v0.17.5)** below (v0.14.9 was a flat 1 px disc held like a vanilla
  shield with a `blocking` model override; both are gone).
- The thrown shield is drawn flat, front (star / banner) UP. v0.14.8 drew it upside down: both shield models face their
  front along +Z after their `scale(1, -1, -1)` flip, and the renderer's `XP(+90)` turned +Z to -Y (front down, grip up);
  it is `XP(-90)` now (verified from above and the side in the v0.14.9 harness, adamantium and a red vanilla shield).
- The throw **moves** the stack out of the hand into the entity (`ITEM` synced data + NBT `Item`). Caught: the same
  stack back into the hand it left if empty, else `Inventory.add`, else an item at his feet. Thrower logged out / dead:
  drops as an item where the shield is. Another dimension / lost power / 12 s without being caught: delivered straight
  back. `/kill` or any destroying removal drops the stack. Unloaded with its chunk (server stop): saved with the stack;
  on reload it heads home if the thrower is online, otherwise drops. `canChangeDimensions` is false.

## The shield model, holding and animations (v0.17.5)

User: "make the captain america shield models, poses and positioning and animations better". Gameplay untouched (damage,
blocking, range, return, cooldowns); the server changes are two synced fields on the thrown shield, spark particles and the
back-sling attachment.

- **Model** (`CapShieldModel`, all three shields): a round dish built from five stepped rings, drawn in code (32 sides).
  Shield space: model pixels, disc in XY, +Y = the star's point, **front +Z**. Front convex (rim 0.8, outer ring 1.1, white
  1.45, inner ring 1.8, centre 2.1 px), every step's outward wall drawn; back concave (rim lip -0.1 down to 1.4) with its
  inward step walls; two vertical leather straps on rivet posts bridge the hollow at z -0.45 (`CONTACT_Z`: what rests on
  the arm or the back), a leather pad between them. Radius 8 px; drawn at 0.85 on a body, 0.9 in flight. Ring radii
  3 / 4.5 / 6 / 7 / 8 px match the art (2 texels a pixel): front and back UVs are planar from X / Y.
- **Art** (`scratchpad/gen_cap_shields_v0175.js`, original): `textures/entity/{soldier,vibranium,adamantium}_shield.png`
  64x64 -- front disc (0,0), back disc (32,0), step-wall colour rows (0,32), strap / rivet / pad (0,48). Soldier's: matte
  painted red / white / blue, a few chips. Vibranium: deeper, saturated paint, a specular arc top-left, spin marks.
  Adamantium: unpainted -- gunmetal and brushed-silver rings, dark centre, polished star (it was red / white / blue before;
  the brief asked for a distinct steel variant). `textures/item/*_shield.png` are 16x16 flat versions (particles).
- **Item models**: `builtin/entity` for all three (the Soldier's Shield was a flat generated sprite), GUI 14 / -22 tilt so
  the dish shows its depth, ground 0.45, frame front-on. No `blocking` overrides or predicates any more (the `*_blocking.json`
  models are deleted): the shield never jumps between transforms.
- **Strapped on the forearm** (`CapShieldHeldLayerMixin` -> `CapShieldPose.renderStrapped`): for any humanoid model the
  held shield is drawn in the arm's frame (`translateToHand`), straps against the outer side of the forearm (3 px out from
  the pivot line on wide arms, 2.5 on slim), 6 px down the arm, face outward, star toward the arm's front. Either hand,
  mirrored. It never moves on the arm: raising it is the arm moving.
- **Arm poses** (`CapShieldPoseMixin`, `HumanoidModel.setupAnim` TAIL, players only; rotations built as quaternions and
  written back as the Z-Y-X Euler angles `ModelPart` uses):
  - Block: forearm across the chest, the outer side (shield face) forward, shoulder pivot 2.5 px forward (1 down) so it clears the
    chest; slerped from the plain carry pose over 3.5 ticks up / 4.5 down (eased) -- vanilla snaps.
  - Bash: a main-hand attack swing with a shield is a shove of the block pose 6 px out, peaking at a quarter of the swing.
    (There is no shield-bash move; this is the look of an ordinary attack with the shield in hand.)
  - Throw: when a thrown shield appears within 3 blocks of its thrower (client), his arm snaps across the chest (1.2 ticks,
    torso turned in), whips out forward (to 3.5 ticks, ease-out, torso turned through) and settles (9). The throw itself is
    still instant on the key press (no wind-up delay was added to gameplay), so the wind-up plays as the shield leaves.
  - Reach and catch: while his shield flies home within 8 blocks the empty arm comes up toward it (harder the closer); when
    it vanishes within ~5 blocks while returning, the arm takes the hit (driven back, 2 ticks) and settles (7).
  - Synced for this: `SoldierShieldEntity` `OWNER_ID` (int, re-set on the server after a reload) and `OFF_HAND`.
- **First person** (`CapShieldFirstPersonMixin`): low at the side with the strapped back toward the eye; eased up into the
  lower middle to block (same blend); shoved forward on a swing; dropping in from above on a catch (equip bob suppressed).
  The empty main hand sweeps across and out during a throw and rises while the shield flies home. The thrown shield is
  not drawn in its own thrower's first-person view for its first 2 ticks or its last 1.3 blocks (it filled the screen).
- **Flight** (`SoldierShieldRenderer`): flat, face up, a slight 8-degree bank, 62 degrees a tick (was 40), three
  see-through after-images (`entityTranslucent`) trailing along the last tick's travel; the dust streak takes the shield's
  colours. Sparks (`ELECTRIC_SPARK` + crits) on every ricochet and wall glance, crits at the catch.
- **On the back** (`CapShieldStow` + `CapShieldBackLayer`): a Captain America shield in the hotbar while neither hand holds
  one is slung between the shoulder blades, star up, face out, following the torso; 1.1 px further out over a chestplate.
  Any player, not only Super Soldiers; the vanilla shield never.

## Captain America suit (v0.14.9)

- Items `captain_america_helmet` / `_chestplate` / `_leggings` / `_boots` (`SuperSoldierArmorItem`, set id
  `captain_america`). Material `projecthero:captain_america`: 3 / 7 / 6 / 2 = 18 armour (iron 15, diamond 20), toughness
  1.0, no knockback resistance, enchantability 12, durability multiplier 25, repaired with iron ingots, iron equip sound.
- Recipes (shaped): helmet `BWB / I I`; chestplate `I I / BWB / RBR`; leggings `BIB / B B / L L`; boots `R R / L L`
  (B blue wool, W white wool, R red wool, I iron ingot, L leather).
- Model: the user's `captainamerica.bbmodel` converted by `scratchpad/gen_supersoldier_v0149.js` into
  `geo/captain_america.geo.json` (Head/Hat Layer -> armorHead, Body/Body Layer -> armorBody, arms, legs; boots = the bottom
  4 px of each leg; X mirrored as Blockbench's own export does; the bbmodel's posed group rotations dropped; +0.3 inflate on
  every cube on top of the layer inflation: bases 0.3, layers 0.55, hat 0.8, boots +0.05) and
  `textures/armor/captain_america.png` (the embedded skin, byte-for-byte). Its head base region is empty in the skin, so the
  helmet is the hat layer alone. Shares `SuperheroArmorVisuals.SHARED_ANIMATION`.
- Only a Super Soldier may wear it: `SuperSoldierArmorItem.use` refuses a right-click equip; `SuperSoldierArmorGate`
  ejects any worn piece (inventory, else dropped) from anyone without the power every tick (drag-equip, dispenser, `/item`,
  losing the power), with the action-bar message `message.projecthero.super_soldier.armor_locked`.

## State / lifecycle

`SuperSoldierState`: `hasPower`, `busyUntil`, `iframeUntil`, `noFallUntil`, `onslaughtUntil`, `focusUntil`,
`lastCombatTick`, `abilityReadyAt`. Join / respawn clear the timers (game time is per-world) and re-reconcile; death,
logout and dimension change call `clearTransient`. Static maps (move tasks, kicks, slam, Focus marks, message throttle)
are cleared by `ServerStateReset` via `SuperSoldierAbilityManager.clearSessionState`.

## Tests

`SuperSoldierGameTests` (listed in `src/gametest/resources/fabric.mod.json`). v0.17.5 adds the back-sling rules
(`carriedShieldIsSlungOnTheBackOnlyWhileNeitherHandHoldsOne`) and the synced thrower / hand
(`thrownShieldCarriesItsThrowerAndHandForTheClient`).

## Not done / untested in a real client

- No custom player poses for the moves (they use arm swings, particles and sounds only); v0.17.5 gave the shield its own
  (block, bash, throw, reach, catch). Seen in a v0.17.5 client harness (`scratchpad/ShieldHarness.v0175.java.txt`, stills
  and films in `docs/screenshots/v0175/cap_shields`); not seen: multiplayer (another player's throw / catch / back sling),
  a mob holding one, an item frame, the Captain America suit with the shield on its back.
- Screenshot-verified in v0.14.9 (client harness): the suit front / back / side, the round shield held and blocking in
  third and first person, the hotbar icon, both thrown shields front-up mid-flight, the HUD with the C box. In-world the
  adamantium throw hit all three zombies for 9, the vanilla one for 6, and both came back to the hand. Not seen: the
  inventory armour icons close up, multiplayer, a real logout mid-throw.
- The Adamantium Shield's flight renders the vanilla shield model (banner included) for a vanilla shield; another mod's
  `ShieldItem` falls back to its item model.
