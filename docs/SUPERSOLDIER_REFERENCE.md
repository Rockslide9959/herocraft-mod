# Super Soldier (Hero-Tier power)

> **v0.17.7 Street-tier balance pass:** damage / toughness numbers below are superseded where they differ -- see docs/POWER_TIERS_REFERENCE.md.
> **v0.17.7 kit pass (the user's own numbers, no tier caps):** see **v0.17.7 kit pass: Dash, Bash, Roundhouse, Assemble buffs** below; the
> moves table is current.

Package `com.projecthero.mod.supersoldier` (client: `com.projecthero.mod.client.supersoldier`). Hero-Tier key
`super_soldier`. Added in v0.14.8; v0.14.9 gave him bare-handed G moves, the craftable Adamantium Shield (thrown with C)
and the craftable Captain America suit. A Captain-America-style peak human: always on (no transformation, H stays the power
selector), strong but deliberately below the Hulk / Thor / All Might.

## Files

| File | What |
|---|---|
| `SuperSoldier` | The API: state access, grant / revoke, `reconcile` (every attribute modifier), the passive tick, lifecycle hooks |
| `SuperSoldierAbilities` | The moves, targeting (`canTarget`: no self / squadmates / own pets / creative players; players only with PvP), `strike` (`MAX_HIT` 20, boss cap), the per-player move tables (incl. the Barrage, Storm and Dash / Bash `Charge` state machines), `cooldownKey` (both ultimates share `ultimate`, both bashes `bash`), `shieldLanding` |
| `SuperSoldierAbilityManager` | Slot dispatch (Shift read server-side), per-tick upkeep (incl. the suit gate, for every player), cooldown lookups for the HUD |
| `SuperSoldierArmorGate` | v0.14.9: only a Super Soldier may wear the suit -- ejects it from anyone else every tick |
| `SuperSoldierDamage` | ALLOW_DAMAGE: 30% reduction, the Bash's i-frames, no fall damage from his leaps / dashes / bashes or while blocking with a shield and looking down; AFTER_DAMAGE: combat timer; the `projecthero:serum_rejection` damage type key |
| `SuperSoldierSerum` | Drink outcome with an injected roll: `take(player, refined, roll)` (the item; v0.17.7 starts the transformation) and `drink(...)` (instant grant, admin paths / tests) |
| `SerumTransformation` | v0.17.7: the one-minute transformation after a serum takes -- attachment `projecthero:serum_transformation`, tick, sting, death rules, the `projecthero:serum_transformation` damage type key |
| client `SerumTransformationHud` | v0.17.7: the unlabeled blue Slab bar, bottom right, for that minute (and the `muteOthers` chain that hides the Primary HUD meanwhile) |
| `SuperSoldierConfig` | Every number |
| `SuperSoldierSetup` | One init call: items, recipe serializer, entity type, damage rules |
| `data/SuperSoldierState` | The synced, persistent, copy-on-death attachment `projecthero:super_soldier_state` |
| `entity/SoldierShieldEntity` | The thrown ricochet shield (server-driven). v0.14.9: carries the real thrown `ItemStack` (synced for rendering, saved in NBT) and hands the same stack back |
| `item/SuperSoldierItems`, `item/SuperSoldierSerumItem` | Unrefined / refined serum (drinkable, 32 ticks, leaves a bottle); `soldier_shield` (display-only, the thrown shield's fallback look); the suit's armour material |
| `item/AdamantiumShieldItem` | v0.14.9: `adamantium_shield`, extends vanilla `ShieldItem`, no durability (unbreakable), fire resistant |
| `item/SuperSoldierArmorItem` | v0.14.9: the four `captain_america_*` pieces (`SuperheroArmorItem`, set id `captain_america`); `use()` refuses a non-Super-Soldier |
| `recipe/SuperSoldierSerumRecipe` | `CustomRecipe` + `SimpleCraftingRecipeSerializer`, type `projecthero:super_soldier_serum` |
| client `SuperSoldierPose`; mixins `ItemInHandRendererSuperSoldierMixin`, `SuperSoldierBodyMixin`, a `HumanoidModelMixin` hook | v0.17.7: the move animations (key-framed clips on the synced anim id, third person, the Storm's whole-body spin / flip, first-person hands); see **v0.17.7 rework** |
| client `SuperSoldierHud`, `SoldierShieldRenderer`, `AdamantiumShieldRenderer`, `SuperSoldierClient` | Mono HUD (R G Z X V + C, no bars), the spinning thrown shield (the real stack: the stepped dish with after-images / vanilla shield with its banner), the shield item renderer (`BuiltinItemRendererRegistry`: GUI, ground, frame), registration |
| `CapShieldStow` (common) | v0.17.5: which Captain America shield is slung on a player's back (first one in the hotbar while neither hand holds one); synced attachment `projecthero:cap_shield_stowed` (all trackers, not saved), re-checked every 5 ticks |
| `item/CapBattleSuitItem`, `CapShieldSlot` (common); client `CapSuitClient` | v0.17.5: the Captain America Battle Suit (anyone can wear it) and the top's shield slot -- N, the throw from / back into the slot, the right-click guard, upkeep; see **Captain America Battle Suit and the shield slot** |
| client `CapShieldModel`, `CapShieldPose`, `CapShieldBackLayer`; mixins `CapShieldPoseMixin`, `CapShieldHeldLayerMixin`, `CapShieldFirstPersonMixin` | v0.17.5: the stepped-dish model and its three textures; the strapped-forearm placement (turned square to the front when raised), arm poses (eased block, bash, throw, reach, catch) and first-person placement; the back sling |

## Obtaining

- **Unrefined Super Soldier Serum** (v0.17.7, the user's recipe): crafting table, shapeless special recipe
  (`data/projecthero/recipe/unrefined_super_soldier_serum.json`): exactly one drinkable Potion of Strength, one of
  Swiftness and one of Regeneration (long / strong variants accepted; splash, lingering, custom-effect and other potions
  rejected), one **Golden Apple** (plain only -- the enchanted golden apple is refused) and one empty **Glass Bottle**, any
  slots. Five items, so 3x3 only. Leaping is no longer used (it was Strength + Swiftness + Leaping before v0.17.7). Special
  recipes are not in the recipe book; JEI gets a display-only shapeless copy in its crafting category
  (`ProjectHeroJeiPlugin.superSoldierSerum`, potions cycling through their long / strong variants) plus info pages on both serums.
- Drinking it: **10%** the transformation (below), **90%** death (`projecthero:serum_rejection`, tagged `bypasses_armor`, `bypasses_effects`,
  `bypasses_enchantments`, `bypasses_invulnerability`, `bypasses_resistance`, `no_knockback`; message "X's body
  rejected the unrefined serum"). Creative included. A power's own death save (Symbiote, Khonshu) may still intervene.
  The roll is the server level's RNG.
- **Refined** -- display name **"Super Soldier Serum"** since v0.17.7 (item id still `refined_super_soldier_serum`): a plain
  **Furnace** (smelting) recipe, `cookingtime` 12000 (10 minutes), `refined_super_soldier_serum_smelting.json` (v0.17.7; the
  Blast Furnace recipe is gone). Always takes.
- **The transformation** (v0.17.7, `SerumTransformation`; user: "upon drinking the super soldier serum you get nauseous for 1
  minute, you get weak and slow aswell and you keep taking tick damage but you wont drop below half a heart ... after the minute
  you get the powers"): a serum that takes (refined, or the lucky unrefined roll -- my call: the same minute for both) starts
  **1200 ticks**. Elapsed ticks in attachment `projecthero:serum_transformation` (persistent -> survives a relog; NOT
  copyOnDeath; synced to the drinker only). Ticked from `END_SERVER_TICK` for every online player who has it.
  - Nausea, Weakness I, Slowness I (non-ambient, icons shown) for the rest of the minute, re-applied the tick one is missing or
    shorter than what is left -- milk does not skip it.
  - Every 40 ticks (2 s) a sting of 1 (half a heart), damage type `projecthero:serum_transformation` (tagged
    `bypasses_armor`, `bypasses_effects`, `bypasses_enchantments`, `no_knockback`; not invulnerability, so creative is spared;
    death message "X's body gave out mid-transformation"), cut to `health - 1` so it never takes him below 1 health; an
    `ALLOW_DEATH` guard sets health 1 if that damage type would ever be fatal.
  - Super Soldier passives are not on yet (no power until the end).
  - At 1200: the three effects removed, the attachment removed (bar hidden), `SuperSoldier.grant` (one-Primary rule, title, chat,
    full health). If the power is granted another way mid-way (admin command), the transformation just ends.
  - Death (`AFTER_DEATH`, or a dead player on tick): attachment removed, nothing granted.
  - One at a time: `take` returns `BUSY`, the item's `use` fails with "Your body is still changing." and `finishUsingItem`
    keeps the stack -- a second serum is never consumed.
  - HUD (`SerumTransformationHud`): one bare blue **Slab** bar (boss-bar blue, no label, no numbers), `VanillaHud.width(6)` = 122 px
    wide, its top 6 px above the bottom-right power column's bottom edge (`Stack.bottomRightOwned`: 4 px from the right / bottom
    edges, lifted to 41 px above the bottom in a window too narrow to clear the hotbar). Fills over the minute, interpolated with the
    partial tick. While it runs, `VanillaHud.muteOthers` also answers true (chained on `CLIENT_STARTED`, after Power Armour sets
    it), so the Primary power about to be replaced hides its HUD instead of overlapping the bar.
- Granting calls `HeroTiers.claimPrimary(player, "super_soldier")` (one Primary power: the old one is replaced) and
  shows a title + chat message. Also: `/projecthero power grant super_soldier [player]`, and the Heroic random serum.

## Passives (fixed-id transient modifiers, reconciled every tick)

| | Value |
|---|---|
| Movement speed | +50% (ADD_MULTIPLIED_BASE 0.5) |
| Max health | +10 (+5 hearts) |
| Melee | v0.17.7 (user: "their melee should deal 5 damage and should be +5 on top of tools and weapons and stuff"): **N = 5** (`SuperSoldierConfig.MELEE`) under the shared rule (`combat.MeleeBonus`): a punch -- empty hand or a non-weapon item, a shield included -- deals 5; a weapon or tool (anything with a main-hand attack-damage modifier) deals its own damage + 5 (a netherite sword 13). `MeleeBonus.fist(player, super_soldier_unarmed, 5)` sets the modifier to 4 and the shared `melee_bonus_weapon` +1 is added while a weapon is held. Was +7 bare / +2 holding anything. `ARMED_BONUS` (= `MELEE`) stays for `worthy.WorthyMoves`, which tunes a lent Mjolnir / Stormbreaker hit to an exact total |
| Damage taken | x0.7 (cancel-and-reissue; not for /kill, the void, bypass-invulnerability) |
| Jump | v0.17.7: apex 2.4 blocks (was 2.25) -- clears a 2-block wall (feet above it for 6 ticks), never a 3-block one (JUMP_STRENGTH x(v/0.42 - 1), v solved by simulation) |
| Resistance | v0.17.7: Resistance I always (`SuperSoldier.passiveResistance`, every tick from `tick`): ambient, 300 ticks, topped up under 220; a stronger Resistance is left alone; `reconcile` removes ours (ambient, amplifier 0, <= 300 ticks) when the power goes |
| Regeneration, Speed | v0.17.7 (user: "player should have passive regen 1" / "passive speed 1"): Regeneration I and Speed I always (`SuperSoldier.passiveEffect`): **infinite**, ambient, re-given the tick they go missing; anything stronger (a potion, Assemble's II) is left alone -- vanilla keeps ours underneath a stronger timed effect and restores it when that ends; an equal-strength timed one is replaced by ours. Infinite rather than a topped-up timer because Regeneration heals on its effect clock (a timer reset every few seconds heals unevenly). `reconcile` removes ours (ambient, infinite, amplifier 0) when the power goes |
| Safe fall | +3 blocks |
| Knockback resistance | +0.2 |
| Attack speed | +15% |
| Out-of-combat healing | 1 HP / 2 s after 5 s out of combat (food > 6), on top of Regeneration I |
| Immunities | Poison, Nausea |

## Moves (R / G / Z / X / V, each with a Shift variant; C the shield throw; H / N never)

| Key | Move | Numbers | Cooldown |
|---|---|---|---|
| R | Combo Strike | 3 punches, 5 ticks apart: **5 + 7 + 10** (v0.17.7, Black Panther's unsuited Warrior's Combo; finisher knockback 1.0). Needs a target within 4 blocks | 5 s |
| Shift+R | Uppercut Launcher | 10 damage, target launched ~5 blocks up (vy 1.05) | 8 s |
| G | Flying Kick | lunges at the enemy in front within 8 blocks (1.15 b/t, up to 10 ticks), lands within 2.4 blocks (at once if already that close): **12** damage (v0.17.7 user; the Street pass had 10), knockback 1.5, lift 0.3, then a small hop back; no fall damage for 2 s. No target = no cooldown | 8 s |
| Shift+G | Judo Takedown | grabs the enemy in front within 3.5 blocks, hoists it over his shoulder, 5 ticks later slams it to the ground behind him (in front if a wall is behind): **15** damage (v0.17.7 user; the Street pass had 10), Slowness IV + Weakness I 1.5 s. Bosses are not moved, just hit. No target = no cooldown | 10 s |
| Z | Leaping Slam | leap 4 blocks up / 0.8 forward, lands (or after 3 s) for 12 damage in 5 blocks (60% at the edge), knockback 1.2, lift 0.4 | 12 s |
| Shift+Z | **Peak Human Barrage** (ultimate, no shield) | v0.17.7: dash onto the enemy in front within 6 blocks (1.5 b/t, up to 8 ticks), both rooted; six blows 3 ticks apart for 7.5 each (right straight, left hook, right body, knee, left elbow, right uppercut), a 7-tick wind-up, then the haymaker for 20 (knockback 2.4, lift 0.9) with a 4-block burst for 10 on everyone else. No target = no cooldown | 95 s, shared |
| Shift+Z + shield | **Shield Storm** (ultimate, a shield in either hand or slotted) | v0.17.7: leap 4.5 up; at tick 7 the shield is hurled where he aims by the C rules below (speed 1.3, was 2.1; light aim assist; up to 5 bounces to enemies within 6 blocks of the last one hit -- was 8 ricochets within 16 from a lock on the nearest hostile) for 15 each, a bright red / white / blue trail; he hangs in the air until it is back (max 5 s), catches it, front-flips and dives (2.2 b/t): a five-point star shockwave, 20 at the centre to 12 at 6 blocks, knockback 0.8, lift 0.85. No fall damage. No hostile within 16 = no cooldown | 95 s, shared |
| X | **Dash** | v0.17.7: 1.6 b/t for 5 ticks (~8 blocks) along his full look, pitch included (on the ground looking down: level); a wall ends it; no fall damage for 3 s. Was the Tactical Roll | 3 s |
| Shift+X | **Shoulder Bash** (no shield) | v0.17.7: a charge along his look, 1.35 b/t for 6 ticks (~8 blocks); everything within 1.2 of his body / next step is hit once for **10**, knocked along the charge (1.4, lift 0.35); invulnerable for the charge + 2 ticks; no fall damage. Was the High Leap | 8 s, shared |
| Shift+X + shield | **Shield Bash** (a shield in either hand or slotted -- `hasShield`) | v0.17.7: the same charge behind the shield for **14** | 8 s, shared |
| V | **Spinning Roundhouse** | v0.17.7 (replaced the Counter Stance): a 5-tick chamber and full spin, then the kick: everything in a 150-degree arc in front within 3.5 blocks takes **11**, knockback 1.2, lift 0.3, Slowness II 1 s. No target needed | 6 s |
| C | Shield Throw | needs a shield in either hand (main first) or slotted. v0.17.7 aim rework (see **Shield throw: aim, assist and bounces**): flies along his look (pitch included) at 1.0 b/t (was 1.6); light aim assist (8-degree cone); bounces only off an enemy it hit, to the nearest unhit hostile within 6 blocks of it in sight, at most 5 bounces; walls stop it. v0.17.7 (user: "Captain America shield should do 6 damage"): **every shield hits for 6** (`SHIELD_THROW_DAMAGE`; the first hit and each bounce). **Adamantium Shield**: 6 per hit (was 9), 5 bounces, range 25. **Vibranium**: 6 (was 10; `VIBRANIUM_SHIELD_DAMAGE`), 5 bounces, 30. **Any other shield** (`ShieldItem`): 6 (was 8), 2 bounces, 25. Shield Storm (15 a hit, 20 slam) and Shield Bash (14) unchanged; the shield has no melee of its own (it is not a weapon, so a punch holding it is his plain 5). No shield = an action-bar hint, no cooldown | none |
| Shift+V | **Assemble** | v0.17.7 kit pass: "Assemble!" on the action bar; **50 s: self + squadmates within 24 blocks get Strength II, Resistance II, Regeneration II, Speed II**. The gold / blue rally ring, the raised-fist animation and the sounds stay (visual only). The old ring knockback, debuffs, marks, x1.25 on the marked and punch shock are gone | **100 s** from the call |

Bosses (max health >= 300, or `TitanCombat.isBoss`) take at most 6% of their max health per hit and are never knocked
back or launched. A 4-tick global lock stops moves stacking; the combo locks for 12 ticks, the kick for 12, the takedown for 9.

## HUD

`SuperSoldierHud`: bottom right, mono (black / gray) boxes in the order R G Z X V; each box shows the longer of its two
moves' cooldowns. v0.14.9: **no bars** (the ultimate and Tactical Focus hairlines were removed). v0.17.7: Z's box is lit while
an ultimate plays out, X's while a Dash / Bash carries him, V's while Assemble runs; Shift+Z's and Shift+X's names follow the shield
(Barrage / Shield Storm, Shoulder Bash / Shield Bash). A sixth box, **C**, appears stacked on top of V only while he holds a
shield (or while a throw is cooling down, so the timer does not vanish while the shield is in the air); the five fixed
boxes never move (a C box left of R ran under the hotbar on small screens). Left-Alt shows short move names.

## Shield throw: aim, assist and bounces (v0.17.7)

User: "change the shield targeting please its too op right now, make it so that the shield throws where the player aims with some
aim assist, the shield can bounce between enemies if they are in close proximity to whats being hit, maximum 5 bounces, make the
shield get thrown slower please". The old throw locked onto anything in a ~57-degree cone (`facing`, dot 0.55), ricocheted to any
hostile within 12 blocks (6 blind, homing through blocks) and flew at 1.6. Now (`SoldierShieldEntity`, numbers in `SuperSoldierConfig`):

- **Every throw path** uses it: C from either hand, C from the Battle Suit slot, and the Shield Storm (`stormThrow`; the Storm keeps its
  own damage 15, trail, hover / dive / slam and its "an enemy within 16" gate, but no longer locks onto the nearest hostile).
- **Aim**: `launch(owner, from, look, ...)` -- along the look vector, pitch included. `SHIELD_SPEED` 1.0 b/t (62.5% of the old 1.6);
  `STORM_SHIELD_SPEED` 1.3 (was 2.1).
- **Aim assist** (`assistTarget`): a hostile (`HeroTargets` rule 2) the shield may hit, in range, in his line of sight, whose centre is
  within `SHIELD_ASSIST_CONE_DEG` 8 degrees of the aim (or whose box the crosshair is on) -- the closest to the aim wins. The launch
  points at its centre; in flight it turns at most `SHIELD_ASSIST_TURN_DEG` 3 degrees a tick toward it and lets go when it is more than
  `SHIELD_ASSIST_KEEP_DEG` 16 degrees off the heading or out of sight. Nothing in the cone: straight. `upgrade` / `storm` re-aim
  (their range / speed differ).
- **Walls** always stop the outbound flight (a glance sends it home); only the flight home passes through blocks.
- **Bounces** (`pickBounce`): only off an enemy it struck -- the nearest unhit hostile within `SHIELD_BOUNCE_RANGE` 6 blocks of that
  enemy's centre, in that enemy's line of sight (from its centre or 0.5 above). It then homes on it exactly; if the target dies or gets
  more than 12 blocks away the shield goes home. `SHIELD_MAX_BOUNCES` 5 (Adamantium, Vibranium `THROW_BOUNCES` 5, `STORM_MAX_BOUNCES`
  5), `NORMAL_SHIELD_MAX_BOUNCES` 2. No enemy close = no bounce.
- **Never** the thrower, squadmates (friendly fire or not: `canHit` adds `Squads.areAllies`) or anyone's protected pets; bosses are
  capped by `strike` as before.
- **Return**: `SHIELD_RETURN_SPEED` 1.2 b/t, rising with distance (x0.12) to at most `SHIELD_RETURN_MAX_SPEED` 2.0 (was up to 3.0).
- Tests: `SuperSoldierShieldAimGameTests` (aim + pitch + speed, the cone in and out, an assisted hit, an unassisted miss, the 5-bounce
  cap with seven zombies, no bounce to an enemy 7.5 blocks away, the jump, Resistance I).
- Seen in a real client (`scratchpad/SoldierShieldBounceHarness.v0177.java.txt`, `scratchpad/v0177/ss_shield_harness.js` / `ss_shield_film.js`;
  MP4 + frame sheets in `docs/screenshots/v0177/ss_shield/`): a C throw into seven husks hit six for 9 each (first hit + 5 bounces) and
  never the husk 8+ blocks off (wide / side / first person); aimed 6 degrees off a lone husk it bent on and hit; 16 degrees off it flew past.
  Not seen: the Shield Storm with the new rules, multiplayer, a squadmate in the group.

## Thor's weapons: the lent-weapon movesets and flight (v0.17.7) -- `com.projecthero.mod.worthy`

User: "When super solider teammate has stormbreaker or Mjolnir they should have 2 movesets with the weapon, a moveset with just Mjolnir
or stormbreaker and a moveset for dual wielding their shield and Mjolnir or stormbreaker ... super soldier should be able to fly with
Mjolnir or stormbreaker". How a Thor lends the weapon (and calls it back) is in THOR_REFERENCE.md ("lending Mjolnir / Stormbreaker").

**Files** (all new, `com.projecthero.mod.worthy`): `WorthyConfig` (EVERY number -- v0.17.7 on Shazam's scale, see below; no tier cap
and not his own MAX_HIT on top, only the 6%-of-max-health boss cap), `WorthyKit` (which kit, the key tables, routing, the right-click), `WorthyMoves` (the moves; timed
moves and the thrown weapon run from `WorthyState`, no static collections), `WorthyFlight`, `WorthyLending` / `WorthyLoans` (the loans),
`WorthyState` / `WorthyAttachments` (synced, persistent per-player state, a CompoundTag codec), `WorthyFx`, `WorthyActionPayload`,
`WorthyMod` (init from `ProjectHeroMod`). Client `client.worthy`: `WorthyHud`, `WorthyPose` (+ `mixin/WorthyPoseMixin`,
`mixin/ItemInHandRendererWorthyMixin`), `WorthyClient` (the lend click, the double-tap, the thrown weapon's flight drawn from the state).

**Hooks in Super Soldier code** (one line each): `SuperSoldierAbilityManager.handle` asks `WorthyKit.handle` first (while he has a kit it
owns R / G / Z / X / V / C -- even an empty key, so a bare-handed move never fires mid-kit; H / N unchanged); `SuperSoldierHud.render`
hands over to `WorthyHud` while `WorthyHud.owns`, and otherwise asks `WorthyHud.callWeapon` for the R box / tag / Alt row of the call.

**Lent into the pack; R = Call Mjolnir / Call Stormbreaker** (v0.17.7, user: "When player is lent the powers their regular r ability
should be replaced with a weapon call ability and once they get the hammer or axe they have that moveset"). A lend puts the weapon in a
free pack slot that is NOT the selected one (backpack rows first; full pack -> at his feet as the callable entity), never his hand.
While a weapon lent to him is not in his hands (`WorthyKit.kit` = NONE), `WorthyKit.handle` takes plain R (`WorthyLending.callTarget` /
`callLent`; Shift+R and every other key stay his own kit): "Call Mjolnir" / "Call Stormbreaker", cooldown `CALL_WEAPON_CD` 1 s (key
`call_weapon`, refunded if nothing answers). It goes through Thor's own recall, `MjolnirRecall.summonLent` (= `callWeapon` with the
ownership check relaxed: his pack -> swapped into the main hand at once; a loaded entity (Q-dropped, resting) -> `recall(him)` flies to
him; another player (its Thor too), a loaded chest / frame / minecart, another dimension -> moved and flown in; unreachable -> rebuilt at
a new generation). His own throw counts as in hand (no call). The arrival: `ThorWeaponHands.plan` / `place` send a lent weapon called by
its Super Soldier (`WorthyLending.borrowsKind`) to his MAIN hand (an empty hotbar slot is selected, else the held item steps into the
pack). Both lent and neither in hand: R calls the one it did not call last (`WorthyState.lastCall`). Once held, the weapon's kit owns the
keys. HUD: the R box shows the call's cooldown and is lit, a gold tag "R: Call Mjolnir", Left Alt's R row reads the call (Shift+R his own).

**Which kit** (`WorthyKit.kit`, both sides): a weapon lent to him in either hand (or out on his own throw, `ThorThrown`) -> the weapon-only
kit of that weapon; a shield (any `ShieldItem`) in the other hand, in the Battle Suit slot, or out on a throw of his -> the shield + weapon
kit. Both weapons lent to him: the main-hand one's kit. An unlent Stormbreaker (from a chest) gives nothing.

| Key | Mjolnir only | Stormbreaker only | Shield + weapon |
|---|---|---|---|
| R | Hammer Combo: 16, 16, 18 + finisher sparks 14 in 2.5 / 5 s | Axe Cleaves: 100-degree sweeps 3.5 blocks, 17 then 19 / 6 s | Shield and Weapon: bash 16 (staggered) + weapon 18 (axe 19) / 6 s |
| Shift+R | Thunder Uppercut 17, launch / 8 s | Rising Cleave 18, launch / 9 s | Bash Launcher: 16 launch + spike 17 (axe 18) / 9 s |
| G | Call Lightning (24 blocks): 18 + jolt (Slowness IV 0.5 s), 12 within 2.5 / 6 s | Lightning Cleave: line 12 x 1.2, 19 each, bolt at the end / 7 s | Charged Shield Throw: his normal shield throw, every enemy it hits chains 16 to 2 more within 5 (4 s window) / 8 s |
| Shift+G | Thunderclap: cone 7, 120 degrees, 16, knockback 1.4 / 9 s | Executioner's Chop: leap within 9, 20 (x1.25 under 30% hp) / 11 s | Twin Throw: shield + weapon together / 10 s |
| Z | Thunder Slam: hop, 18 within 5 (60% edge) / 11 s | Storm Crash: 19 within 4.5, knockback 1.6, a bolt / 12 s | Lightning Shield Slam: 17 within 5 (axe 18), two side bolts / 11 s |
| Shift+Z (ult, one shared 90 s) | Worthy Storm: bolt into the hammer t10, 5 bolts t14-30 (16 each, within 14), burst t36 40 within 6 | Stormbreaker's Verdict: axe hurled t6 into the aimed foe (24), strike t14 40 (pinned), burst t20 18 within 6, home t34 | Thunder Clap ("Endgame"): weapon struck into the shield t12, cone 10 / ~140 degrees, 40 (axe 41, 60% edge), knockback 2.0, lift 0.5, 3 bolts |
| X | Hammer Dash (1.6 b/t) / Flight Boost x1.5 2.5 s while flying / 4 s | Axe Dash (1.35) / Boost / 4 s | the weapon's dash / boost |
| Shift+X | Hammer Launch: weapon raised (no whirl since v0.17.7), launch at t4, flight at t8 / 6 s | Storm Leap (same) / 6 s | the weapon's launch |
| V | Whirl Guard 1.5 s: front hits blocked, projectiles knocked back, melee attackers shocked 16 / 10 s | Axe Guard (same) / 10 s | Lightning Guard 2.5 s, shock 17 / 10 s |
| Shift+V | Rally Bolt: bolt on himself, self + squad within 10 Strength I + Speed I 8 s, hostiles within 6 pushed / 40 s | same | same |
| C | Hammer Throw: 20 blocks, pierces 3, 17 each, back in 18 ticks / 3 s | Axe Throw: 18 blocks, pierces 2, 18 + Slowness II 2 s / 4 s | weapon throw; **Shift+C** his own shield throw |
| Right click | = C (the throw) | = C | passes -- the shield in the other hand blocks |

**Damage (v0.17.7, user: "the scaling for these movesets should be powerful, it should be comparable to shazam since the powers are
being lent to you by a god")**: on `ShazamConfig`'s scale (fist 17, combo 17/17/19, abilities 16-20, ultimate 40) -- left click 17 /
18, combo hits 16-19, abilities 16-20, each ultimate 40; Stormbreaker a little heavier than Mjolnir, the shield + weapon kit in between.
Every worthy hit goes through `WorthyMoves.hit` (squad-safe, boss cap 6%), never `SuperSoldierAbilities.strike`, so his own `MAX_HIT`
(20) does not clamp them (the Charged Shield Throw's shield itself is still his own throw). Melee (left click) with the lent weapon in the
main hand: exactly 17 (Mjolnir) / 18 (Stormbreaker) (`WorthyMoves.reconcileMelee`, one transient modifier, made up from his own
held-item bonus as it stands, so retuning that bonus never changes these). Shared cooldown keys: `worthy_ultimate`, `worthy_dash`, `worthy_launch`, `worthy_guard`. 4-tick busy lock. Moves
with nothing to hit cost nothing. Squad-safe (`HeroTargets.canHarm`). Thrown weapons really leave the hand through Thor's `ThorThrown`
(persistent, handed back at the head of `die`, never rebuilt) and the client draws the flight (out, hang, home to the live hand,
spinning, a jagged lightning trail). Look: visual-only bolts, electric sparks (Stormbreaker adds white dust, never tinted), Thor's
sounds, screen shake on the slams and ultimates -- used more sparingly than Thor.

**HUD** (`WorthyHud`): the same mono strip; title = the kit ("Mjolnir", "Stormbreaker", "Shield + Mjolnir", "Shield + Stormbreaker"); tags:
"<owner>'s", Flying / Boost, Charged; V lit while guarding, X while boosting, C while the weapon is out, Z while an ultimate plays;
Left Alt lists the kit + "2x Space: Fly" + "RMB: Throw". **Guidebook / I menu**: the Super Soldier chapter's "Thor's weapons" section
(body, the R call, flight, the Shazam-scale damage, a key list per kit built from `WorthyKit.guideRows`).

**Flight** (`WorthyFlight`): double-tap Space in the air with a lent weapon in hand (client `ProjectHeroModClient`, right after Thor's
branch; `WorthyActionPayload(TOGGLE_FLIGHT)`), vanilla mayfly / flying, a little lift and straight into the flight pose (v0.17.7, user: "remove the hammer spinning animation
when flight starts": no spin-up; `WorthyFlight.start` sets no animation and clears a Launch's; `ANIM_TAKEOFF` is retired). Shared
directional flight (`DirectionalFlight.tune` -> `WorthyFlight.tune`): Mjolnir 15 cruise / 30 sprint b/s, Stormbreaker 13 / 26 (Thor 18 /
36), x1.5 Flight Boost. `FlightPoseHelper` counts it as Thor flight while a Thor weapon is in hand (Thor's raised-weapon pose). Free (no
Storm Energy). Ends on landing (`FlightLanding.landed`, after a 10-tick grace), another double-tap, or no lent weapon in hand (his own
throw keeps him up); no fall damage for 5 s after it ends.

**Poses** (`WorthyPose`, the SuperSoldierPose clip technique; weapon arm = lead, shield / free arm = the other, mirrored for a left lead
or a weapon in the off hand): hammer combo (forehand, backhand, overhead smash), uppercut, the weapon raised to the sky (Call Lightning
/ Worthy Storm / Rally), the drive into the ground (Thunderclap / slams / Lightning Cleave), the throw, the axe's two cleaves, the
overhead two-handed chop, the Verdict hurl / point / catch, shield bash + strike, bash launcher, shield fling, twin throw, the clap,
the lightning guard; the Whirl Guard is a procedural spin on top (the take-off / Launch whirl was removed in v0.17.7; the Launch
just raises the weapon). First person: weapon (and shield)
moved just before vanilla draws them.

**Tests -- `WorthyLendV0177GameTests`** (one batch each, `worthy_v0177_*`): lend only to a Super Soldier squadmate, into the PACK (his
torches stay in his hand, no kit, R = the call), owner tracked, not ejected; the Call back brings one copy home and R never takes it; leaving the squad / losing
the power / dying send it home, one copy; SavedData + attachment round trips and the view rebuilt after a blank "relog"; the kit
follows the hands (none / Mjolnir / shield + Mjolnir / Stormbreaker, an unlent axe gives none), R brings each lent weapon from the pack (alternating with both lent), C throws the axe out of
his hand and it comes back; `worthy_v0177_call_pack` (pack -> main hand, Shift+R not the call, kit after), `_call_ground` (a resting
Mjolnir flies to his hand), `_call_axe` (a dropped Stormbreaker into a main hand full of torches, none lost), `_numbers` (the Shazam-scale
constants; a 40 lands whole on a 100-health Ravager), `_takeoff` (no spin-up anim on take-off or a Launch's flight); flight only with a lent weapon and it ends when the weapon goes; a stranger can't lift a lent weapon off the ground, its
Super Soldier can.

**Seen in a real client** (`scratchpad/WorthyFilmHarness.v0177.java.txt`, `scratchpad/v0177/worthy_harness.js` / `worthy_film.js`; the Thor
is a Fabric FakePlayer off camera; MP4 + frame sheet each in `docs/screenshots/v0177/worthy/`): `lend`, `mjolnir_throw_storm`,
`mjolnir_combo_fp`, `stormbreaker_cleave_verdict`, `shield_mjolnir_clap`, `flight`. v0.17.7 round 2 (flag `worthy_out|call`, `planCall`): `call_r` (lent into
the pack with a torch in hand, R from the pack, set down 6 blocks ahead, R: it flies back to his hand) and `flight_start` (take-off
straight into the flight pose, no whirl). Not seen: two real players (the lend click itself,
the Call back screen), the Charged Shield Throw's chains, multiplayer sync of the poses.


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
  held shield is drawn in the arm's frame (`translateToHand`), straps on the forearm's surface 6 px down the arm on the side
  its face points (wide 2 px / slim 1.5 px half width), either hand, mirrored. At rest the painted face points out from the
  body and 55 degrees toward the front, star up (seen at an angle from the front, square-on from the side). Raised or
  swung it turns on the forearm -- the arm's own rotation undone -- until the face points along the body's facing, star
  up (round 2, coordinator review: round 1 kept it fixed on the arm, so it sat edge-on at rest with its straps to the
  front camera and never squared up to block). The turn uses the same eased blend as the arm.
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
- **First person** (`CapShieldFirstPersonMixin`): low at the side with the strapped back toward the eye; eased up to fill the
  lower centre, face forward, to block (same blend); shoved forward on a swing; dropping in from above on a catch (equip bob suppressed).
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
  `captain_america`). Material `projecthero:captain_america`: **v0.17.7 diamond level**, 3 / 8 / 6 / 3 = 20 armour, toughness
  2.0 each (was 3 / 7 / 6 / 2, 1.0), no knockback resistance, enchantability 12, durability multiplier **33 = diamond** (v0.17.7 user:
  "The armour should have diamond level durability"; `SUIT_DURABILITY_MULTIPLIER`, was 25), repaired with iron
  ingots, iron equip sound.
- **v0.17.7 look** (user: "make this the new captain America armour please:
  https://www.planetminecraft.com/skin/captain-america-classic-6692155/ Remove the shield from the back of the suit and dont make the
  armour bulky please like how it is right now, armour should be diamond level"): the bulky GeckoLib model below is gone
  (`geo/captain_america.geo.json`, `textures/armor/captain_america.png` deleted; `SuperSoldierArmorItem` is a plain `ArmorItem` now).
  The suit is a skin costume drawn skin-tight by `QuirkCostume` (the Battle Suit path: base layer 0.02 px over the body, outer layer
  at vanilla's hat / jacket / sleeve / trouser offsets; helmet = head + hat, chestplate = body + arms + first-person sleeve,
  leggings = legs above leg row 7, boots = row 7 down; the wearer's own outer layer hidden only under worn pieces);
  `QuirkSuitArmourMixin` skips the vanilla armour layer. Textures `textures/entity/supersoldier/captain_america_wide/_slim.png` from
  `scratchpad/v0177/make_cap_suit.js` (source `scratchpad/v0177/cap_skin_original.png`, PMC file 19513766, wide arms, never
  modified): the shield painted on the jacket's back (u 32-39, v 37-44, second layer only) cleared, the plain strap band under it
  (base v 21) repainted with the back's scale-mail row; skin-tone texels of the head / hat and the mask's painted eyes cleared so the
  wearer's eyes, nose and mouth show in the cowl's opening (the cowl's hat layer hides the wearer's hair); slim = each 4-wide arm face
  drops its most-alike middle column. Previews: `docs/screenshots/v0177/cap_suit/`. Borrowed skin, ships publicly.
- (v0.14.9, replaced in v0.17.7) The old look:
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

## Captain America Battle Suit and the shield slot (v0.17.5)

User: "Super Soldier: add this captain america suit to the game please: https://namemc.com/skin/63b230543038f21b . when players with
super soldier wear this they can press N and put the shield into a shield slot. the shield will always count as "held" when this
happens". N is user-assigned here (the H / N rule otherwise stands: no N HUD box).

- **Items** (`item/CapBattleSuitItem`): `cap_battle_cowl` / `cap_battle_suit_top` / `_trousers` / `_boots`. Material
  `projecthero:cap_battle_suit`: 3 / 7 / 6 / 3 = 19 armour, toughness 1.5, enchantability 12, durability multiplier 30, repaired with
  leather, leather equip sound. **Anyone can wear it** (no armour gate -- unlike the v0.14.9 suit); the slot is Super Soldier only.
  Recipes (shaped, B blue wool, W white dye, R red dye, L leather, I iron ingot, H hero alloy ingot): cowl `BWB / L L`; top
  `B B / WHW / RLR`; trousers `BLB / B B / B B`; boots `L L / I I`. JEI info page on all four; Guidebook section "The Battle Suit and the
  Shield Slot" in the Super Soldier chapter; N listed in the chapter's controls and the Left-Alt move list.
- **Look**: a skin costume drawn by `QuirkCostume` (the Gyro / Undead path: base layer 0.02 px over the body, outer layer at vanilla's
  offsets, cowl = head + hat, top = body + arms, trousers = legs above row 7, boots = below); `QuirkSuitArmourMixin` skips the vanilla
  armour model. The cowl keeps the face open: the wearer's own eyes, nose and mouth show; the cowl's hat layer hides the wearer's hair.
- **Textures** (`scratchpad/supersoldier/make_cap_suit_textures.js`): from `docs/reference/supersoldier/cap_suit.png` (the user's NameMC
  skin, never modified; `SOURCE.txt`) -> `textures/entity/supersoldier/cap_battle_suit_wide/_slim.png`: skin-tone texels of the head and
  the eye whites / irises in the face opening cleared, bare skin on body / legs cleared, the wrist band and finger tips on the arms
  repainted in the gloves' browns (so no stripe of the wearer's own sleeve shows through the gauntlet); slim made by dropping the
  most-alike middle column. **Held back**: those two files are `.gitignore`d (author unknown, no licence stated). Without them the
  client draws `cap_battle_suit_original_wide/_slim.png`, original art from the same script (navy, white star, red / white stripes,
  brown gloves / belt / boots, a cowl with a white "A" and wings, a leather harness on the back). Item icons are original. Preview:
  `docs/screenshots/v0175/cap_suit/texture_preview.png` (source, borrowed front / back, original front / back over a test skin).
- **The slot** (`CapShieldSlot`): a Super Soldier wearing the **top** (`hasSlot`). Stored on the top as the `projecthero:cap_shield_slot`
  component (`ItemContainerContents`, one item -- compares by contents, so equipment sync doesn't resend it every tick): survives death and
  relogs, drops with the top. Allowed in it: the three Captain America shields (`CapShieldStow.isCapShield`: Adamantium, Vibranium, the
  display-only Soldier's Shield -- the last for looks only, it can't block or be thrown in the hand either). A vanilla shield never.
- **N** (`CapShieldSlot.toggle`, routed by `AbilityRouter`'s H / N branch -> `SuperSoldierAbilityManager.ownsN/handleN`; the client sends
  N from `ProjectHeroModClient.handleMaxSteelTransform` when `hasSlot`): slot empty + a Captain America shield in hand (main first) ->
  into the slot, hand free; slot full + main hand empty -> out into the main hand (else the empty off hand; both busy: "Free a hand");
  slot full + another Captain America shield in hand -> swap; slot empty and the slotted shield in flight -> a hint. Sounds: netherite
  equip in, iron equip out. Action-bar messages `message.projecthero.super_soldier.slot_*`.
- **Always counts as held**:
  - **C** (`SuperSoldierAbilities.shieldThrow`): with no shield in either hand, the slotted one (a real `ShieldItem`) is thrown with its
    own numbers (Adamantium 9 x 4, Vibranium upgraded); `SoldierShieldEntity.returnToSlot()` (saved as `ToSlot`) sends it home **into the
    slot** (`CapShieldSlot.putBack`), else -- no slot any more / slot refilled -- the old rule (main hand, inventory, feet). The main arm
    plays the throw animation.
  - **Block**: hold right-click with the main hand empty (the off hand empty or holding something with no use -- a totem, a map; not food,
    a block, a bow or a shield) -> `CapSuitClient` sends `CapShieldSlot.GuardPayload` press / release; the server keeps the synced
    `projecthero:cap_shield_guard` attachment (the tick it went up). After `GUARD_DELAY` = 5 ticks (vanilla's) it stops everything a raised
    vanilla shield stops: not `bypasses_shield`, not a piercing arrow, source position in the front half (`LivingEntity.isDamageSourceBlocked`'s
    test); handled in `SuperSoldierDamage` ALLOW_DAMAGE (a full cancel: arrows bounce off), clang + crits, a melee attacker knocked back
    0.5. No attacking / mining while it is up (`WolverineAttackMixin` checks `CapSuitClient.guardSent` too). The guard drops the moment it
    stops being legal (item picked up, slot emptied, suit off, power lost).
  - The HUD's C key is lit (`hasThrowable`).
- **Display**: idle, the slotted shield hangs on the back (`CapShieldBackLayer` reads it from the worn top -- every viewer; a hotbar shield is
  never slung with it; 2.15 px out, the Battle Suit is skin-tight). Raised (and while lowering) it is strapped to the **off-hand forearm**
  with the existing block pose / blend (`CapShieldPose.raised` counts the guard; `lastBlockHand` falls back to the off hand), and in first
  person drawn as the off-hand shield (`CapShieldFirstPersonMixin`). Thrown: the flying shield as usual.
- **Taking it off / no slot** (`CapShieldSlot.tick`, every player, from `SuperSoldierAbilityManager.serverTick`): a top worn by someone
  without the slot gives its shield up at once; every 5 ticks a top anywhere in the inventory / off hand / cursor does too -- into the
  inventory, else dropped at his feet.
- **HUD**: no N box; a status word above the title -- "Shield on back" (blue), "Slot empty" (grey), "Shield raised".
- No static state (component + attachment), nothing for `ServerStateReset`.
- **Seen in a real client** (`scratchpad/CapSuitHarness.v0175.java.txt`, output in `docs/screenshots/v0175/cap_suit`): the suit front /
  back / side / face close-up (wide) and front (slim); the real N key stowing and retrieving (`n_stow`, `n_retrieve` MP4 + sheets); the
  slotted shield on the back (Adamantium wide, Vibranium slim) and the HUD word with C lit; C from the slot -> three husks -> home into the
  slot (`throw_from_slot`); right-click raising it on the off-hand arm in third person (`block_tp`, `block_tp_side`) and first person
  (`block_fp`); a husk hitting the raised shield four times, 30 -> 30 health (`block_hits`). **Not seen**: the original fallback suit
  in game (only `texture_preview.png`), multiplayer (another player's slot / guard), the Soldier's Shield in the slot, an inventory swap
  of the top while the shield flies.

## State / lifecycle

`SuperSoldierState`: `hasPower`, `busyUntil`, `iframeUntil` (the Bash), `noFallUntil`, `assembleUntil`, `counterUntil` (unused since
the Counter Stance went; kept for the save format), `lastCombatTick`, `animId` / `animStart` / `animOff` (v0.17.7), `abilityReadyAt`. Join / respawn clear
the timers and the animation (game time is per-world) and re-reconcile; death, logout and dimension change call `clearTransient`. Static
maps (move tasks, kicks, slam, the Barrage, Storm and Dash / Bash in progress -- entity ids only, message throttle) are cleared by
`ServerStateReset` via `SuperSoldierAbilityManager.clearSessionState`.

## v0.17.7 rework: Assemble, Counter Stance, two ultimates

User: "I want the Onslaught ability, battle cry and tactical focus power to be merged into one power, make it the shift+V power and name
it assemble, give the other keys new attacking powers please, make the ultimate better, make an unarmed ultimate and a shield ultimate,
cool animations and stuff make it good please". R, G, X, C, N and their Shift moves are unchanged. Numbers: the moves table above and
`SuperSoldierConfig` (every hit still goes through `strike`: Assemble x1.25 on the marked, then `MAX_HIT` 20, then the boss cap).

- **Shift+V Assemble** (`assemble`): replaces Battle Cry (V), Tactical Focus (Shift+V) and the Onslaught buff. The shock moved from
  Onslaught to Assemble's punches (`assembleShock`); the opening damage burst moved to the ultimates (the ring does none). The old
  Onslaught attack-damage modifier id is still stripped by `reconcile` so an old save carries nothing. 60 s: ten seconds of team
  buffs, debuffs and marks (one sixth uptime), about the old Battle Cry (30 s) and Focus (25 s) together.
- **V Counter Stance** (`counterStance`, `tryCounter` from ALLOW_DAMAGE, `counterReflectTick` every tick with a shield): V is an attack
  with a defensive side. Melee = `mob_attack`, `mob_attack_no_aggro` or `player_attack` whose direct source is the attacker. The stance ends
  before the counter lands, so two braced Super Soldiers cannot ping-pong. Projectiles are reflected the Carapace way (`deflect(REVERSE)`,
  re-aimed at the shooter, owner = him, arrows not pick-up-able), proactively within 1.6 blocks + their speed, and as a fallback when one
  still hits him in the window.
- **Shift+Z** (`ultimate`): `hasShield` (either hand or a throwable slotted shield -- like C) picks **Shield Storm**, else **Peak Human
  Barrage**. Both write the `ultimate` cooldown key (`cooldownKey`), so the HUD, the cooldown message and `maxCooldown` read the one timer.
  - Barrage (`Barrage` table): dash -> arrived (rooted: halted every tick -- a -100% speed modifier was tried and zoomed his camera right in -- the target
    held in place and Slowness VI unless it is a boss) -> six blows -> wind-up sound at tick 19 -> haymaker at 24. A target that dies mid-
    flurry hands the rest to the next enemy in front; none -> the haymaker still goes off where it would have landed. The attacker's own
    camera shakes a little on each blow (`QuirkCamera.shake`, 0.11-0.16) and harder on the haymaker (0.4) with a small FOV punch.
  - Storm (`Storm` table): RISE (the leap) -> HOVER (the shield out; a slow 0.05 b/t sink so a dedicated server's anti-fly check never sees
    him "floating"; the shield's `deliver` calls `stormCaught`; after 5 s it is recalled) -> DIVE (a hop for the flip, then 2.2 b/t down)
    -> `stormSlam` on landing or after 30 ticks. The thrown shield is the ordinary `SoldierShieldEntity` with `storm(...)`: its own damage,
    hits, ricochet reach (16) and speed, and a synced `STORM` flag the client draws as a thick red / white / blue dust ribbon + END_ROD
    sparkle and five brighter, full-bright after-images. The shield goes home to the hand (or slot) it left, as with C.
- **Animations** (`SuperSoldierPose`, the Black Panther clip system: keys with snap / smooth easing, faded over vanilla's pose; anim id,
  start and lead arm synced in the state; `animOff` mirrors a clip so the off-hand shield arm leads):
  - Assemble: fist gathered to the chest in a crouch, then punched straight up, feet planted wide, chest out, head back (the shield arm
    when the shield is in the off hand), held 2/3 s, eased down. Rings of gold / blue dust roll out over three ticks, END_ROD column, flash.
  - Counter Stance: forearms crossed in front of the face, low, weight back (held for the window); the counter is a palm strike / shield
    bash straight out with the hips through it. Sparks, crits, a sweep at the contact point.
  - Barrage: the dash (right hand cocked, leaning in, sprinting stride); right straight, left hook (flat across), right body shot
    (dipping), the clinch and knee, the left elbow, the right uppercut, each snapped in on its hit tick with a guard between; the right hand
    drawn right back with the chest turned away for the wind-up; the haymaker lunge. Hit sparks (crit + electric) at each blow's height,
    a sonic-boom ring, explosion puffs and two dust rings on the haymaker.
  - Shield Storm: the spring, arms up as he rises, the shield arm wound across the chest and flung out while the whole body spins a full
    turn (`SuperSoldierBodyMixin`), the hover with arms spread and head down watching; the catch overhead, a tucked front flip about the
    hips, the dive with the shield held overhead; the slam -- both arms driving down, one knee to the ground. The star grows out in three
    waves (white, red, blue) with flash, explosion puffs, block dust and an END_ROD burst.
  - First person (`ItemInHandRendererSuperSoldierMixin`, empty main hand only): every clip has hand keys -- the blows, the wind-up and
    haymaker, the fist rising into view, the crossed guard and the strike, the throw sweep, the catch, the hands driving down. While a clip
    plays `CapShieldPose` stands aside (third-person arm poses and the first-person empty-hand throw), so the two never fight.
- No new static collection holds worlds or entities (the two new tables keep entity ids and are cleared by `clearSessionState`).

## v0.17.7 kit pass: Dash, Bash, Roundhouse, Assemble buffs

User's list (numbers are the user's, as named constants in `SuperSoldierConfig`): suit durability diamond; passive Regeneration I and
Speed I; Combo 5 / 7 / 10 ("match black panther combo damage" -- `BlackPantherConfig.COMBO_JAB / CROSS / KICK`, copied, not linked);
Flying Kick 12; Judo Takedown 15; X Dash; Shift+X Shoulder / Shield Bash 10 / 14; no fall damage blocking with a shield and looking down;
V a new attack instead of the Counter Stance; Assemble 50 s of level II buffs, 100 s cooldown.

- **What left**: the **Tactical Roll** (old X: a 1.35 b/t roll the way he moved, 10 ticks of invulnerability, 4 s) and the **High Leap**
  (old Shift+X: 5 blocks up, no fall damage, 8 s) are gone -- nothing else in the kit does either (the Leaping Slam Z still leaps 4 up, and
  the Bash keeps an invulnerability window). The **Counter Stance** (old V: brace 1 s, negate + answer a melee hit for 10, reflect
  projectiles with a shield) is gone with its reflect code. Assemble's marks, x1.25, punch shock, debuffs and ring knockback are gone
  (`strike` no longer multiplies; the `MARKS` table is removed).
- **Dash / Bash** share one `Charge` table (`CHARGES`, entity ids only, cleared by `clearSessionState` / `clear`): direction fixed at
  the start (`dashDirection`: the look, or level when standing and looking down), the velocity re-sent every tick (`launchSelf`, which
  also resets the fall distance), a step that would enter a block is tried level (skimming a floor / ceiling), else the charge ends with
  most of its speed taken off. The Bash sweeps his box plus the next step (inflated to 1.2) for targets each tick -- each hit once
  (`Charge.hit`), knocked along the charge (`strike` with the knockback origin behind the target on the charge line). Sounds: the
  Bash's shield clang + anvil / the shoulder's knockback thump + iron golem hit, sparks / cloud, an explosion puff and blue dust per hit.
- **Shield landing** (`shieldLanding`, from ALLOW_DAMAGE on FALL): a shield in use in either hand (`isUsingItem` with a `ShieldItem`)
  or the Battle Suit guard (`CapShieldSlot.isGuarding`), and pitch >= `SHIELD_LANDING_MIN_PITCH` 60. A shield clang, anvil thud, crits,
  cloud and the ground's dust.
- **Animations** (`SuperSoldierPose`, anim ids 9-12; 2 / 3 retired): **Dash** -- leaning hard in, arms swept back, long stride; first
  person both hands dip back. **Shoulder Bash** -- lead shoulder turned forward (twist -0.95) and dropped, the arm tucked across, the
  other fist on the chest; first person the lead forearm low across the view. **Shield Bash** -- the shield arm thrust straight out, the
  other braced behind it (the off arm leads with an off-hand or slotted shield); `shieldBashWeight` makes `CapShieldPose` square the
  strapped shield to the front in third person and shove it out in first person. **Spinning Roundhouse** -- knee chambered, a full
  body turn (`spinDegrees`, ticks 1.5-6, via `SuperSoldierBodyMixin`), the leg swept out sideways at hip height with the arms flung
  wide, back into a guard; first person the guard, then both arms out. A red / white / blue arc of dust at hip height marks the kick.
- **Assemble**: radius 24 (`ASSEMBLE_ALLY_RADIUS`), amplifier 1, 1000 ticks, cooldown 2000. With the passives: Assemble's
  Regeneration II / Speed II go over our infinite level I (vanilla's hidden-effect chain brings ours back at the end); Resistance II
  outlasts the passive's 300-tick timer, which is re-given when Assemble ends.
- **Tests**: `SuperSoldierKitV0177GameTests` -- suit durability = diamond (all four), the passives (+ milk, a stronger potion kept,
  revoke), combo 5 then 22 total + kick 12 + takedown 15, Dash velocity along the pitch (up and down) + cooldown + no-fall + clip, Dash
  stopped by a wall, Shoulder Bash 10 / Shield Bash 14 + knocked along +Z + invulnerable (a generic 10 does nothing) + frames = charge + 2
  + hit once + shared cooldown, fall damage cancelled blocking + looking down (not looking ahead, not without blocking), the Roundhouse
  (11 in front and 60 degrees aside, nothing behind, Slowness II, lands after the chamber), Assemble (him + a squadmate: four level II
  effects for 50 s, an outsider nothing, no marks / debuffs, the passive tick keeps the II, 100 s cooldown not restarted).
- **Seen in a real client** (`scratchpad/SoldierKitHarness.v0177.java.txt`, `scratchpad/v0177/ss_kit_harness.js` / `ss_kit_film.js`; MP4 + frame
  sheets in `docs/screenshots/v0177/ss_kit/`): the Dash up-and-forward then level (side) and first person; the Shoulder Bash through three
  husks (10 each) and the Shield Bash (14 each), side / front / first person; the Roundhouse hitting three husks in front for 11 and not
  the one behind (wide / side / first person). The C throw still bounced through six husks for 9 under the new code. Not seen: the shield
  landing, Assemble on a squadmate, multiplayer, a dash into a real wall in a client.

## Tests

`SuperSoldierGameTests` and (v0.17.7 throw / jump / Resistance) `SuperSoldierShieldAimGameTests` (both listed in `src/gametest/resources/fabric.mod.json`). v0.17.5 adds the back-sling rules
(`carriedShieldIsSlungOnTheBackOnlyWhileNeitherHandHoldsOne`) and the synced thrower / hand
(`thrownShieldCarriesItsThrowerAndHandForTheClient`). v0.17.7 rework: `assembleBuffsDebuffsMarksAndBoostsHitsOnTheMarked`,
`barragePicksWithoutAShieldAndHitsForSixFlurryPlusTheHaymaker` (incl. the shared cooldown holding the Storm),
`shieldStormPicksWithAShieldRicochetsThenStarSlams`, `bossCapHoldsForTheUltimatesAndBossesAreNeverLaunched`,
`counterStanceNegatesAMeleeHitAndCounters`, `counterStanceWithAShieldSendsAnArrowBack`; `everyMoveActivatesAndStartsItsCooldown` covers
Counter / Assemble / Shield Storm. Seen in a real client (`scratchpad/SuperSoldierReworkHarness.v0177.java.txt`, installed by
`scratchpad/v0177/super_soldier_rework_harness.js`, films by `super_soldier_rework_film.js`; output in
`docs/screenshots/v0177/super_soldier_rework`): Assemble (bare-handed film, shield stills, HUD), the Counter Stance answering a husk, the
arrow reflect (hard to read at that distance), the Barrage (two third-person angles + first person), the Shield Storm (wide, front,
first person). Harness cameras run with FOV effects off (his +50% speed widens a fixed camera).

## Not done / untested in a real client

- v0.17.7: the new moves have their own poses (`SuperSoldierPose`); R, G, Z and X still use arm swings, particles and sounds only.
  Not seen for the rework: multiplayer (another viewer's copy of the clips, a squadmate getting Assemble's buffs), a left-handed player,
  the Storm thrown from the Battle Suit slot, a Storm whose shield is lost mid-flight, the dedicated-server anti-fly check during a long
  hover. v0.17.5 gave the shield its own
  (block, bash, throw, reach, catch). Seen in a v0.17.5 client harness (`scratchpad/ShieldHarness.v0175.java.txt`, stills
  and films in `docs/screenshots/v0175/cap_shields`); not seen: multiplayer (another player's throw / catch / back sling),
  a mob holding one, an item frame, the Captain America suit with the shield on its back. The Soldier's Shield is
  display-only (a plain item, not a `ShieldItem`), so it cannot be raised: its `*_block_*` stills show it at rest.
  The throw films were shot before the round-2 rest-angle change (the flight / throw / catch code is the same).
- Screenshot-verified in v0.14.9 (client harness): the suit front / back / side, the round shield held and blocking in
  third and first person, the hotbar icon, both thrown shields front-up mid-flight, the HUD with the C box. In-world the
  adamantium throw hit all three zombies for 9, the vanilla one for 6, and both came back to the hand. Not seen: the
  inventory armour icons close up, multiplayer, a real logout mid-throw.
- The Adamantium Shield's flight renders the vanilla shield model (banner included) for a vanilla shield; another mod's
  `ShieldItem` falls back to its item model.
