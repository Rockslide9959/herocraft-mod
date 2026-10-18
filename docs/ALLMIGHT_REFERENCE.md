# All Might / One For All — reference (v0.12.33)

> **v0.16.6 revamp (user-approved; supersedes everything below about OFA):** the 300-point OFA timer is GONE (`OFA_MAX` and the drain/regen constants removed; an old save's `ofa` key is ignored by the codec, so it loads at 20% with no Strain -- `onPlayerJoin` also clears a stale old-style Plus Ultra).
>
> **Output** (`AllMightState.output` 0/1/2 = 20/45/100%, `outputProgress`, `lastHitAt`): landed hits charge the next level -- `OUTPUT_HITS_TO_45` 4, `OUTPUT_HITS_TO_100` 6; a Smash charges once per `Wave` however many it hits (`Wave.charged`), punches charge too (throttled to one per `OUTPUT_PUNCH_SPACING_TICKS` 6). `OUTPUT_DAMAGE` x1.0 / x1.25 / x1.6 (`AllMight.smashMultiplier`), `OUTPUT_SIZE` x1.0 / x1.2 / x1.4 on every shockwave width / radius (`AllMight.sizeScale`). Drops one level after `OUTPUT_DECAY_TICKS` 8 s without a hit. Every form change resets to 20%.
>
> **Strain** (`strain` 0..100, `recoilUntil`): +1.5/s at 45%, +5/s at 100%, none during Plus Ultra; recovers 3/s at 20% (Power Form) and 5/s in the Base Form; applied in 5-tick steps. United States of Smash adds 30 (`STRAIN_UNITED_STATES`, no OFA cost now). Full Strain = `AllMight.recoil`: forced to 20%, Plus Ultra ends, every Smash (not the Leap) locked `RECOIL_TICKS` 20 s, Weakness II + Mining Fatigue I, limp-arm pose (`AllMightPose.applyRecoil`), smoking arms. Warning at 75 (`STRAIN_WARNING`).
>
> **Plus Ultra (C)**: free; 100% (a borrower: 45%) for 22 s, no Strain while it lasts, Strain set to 75 when it ends, 45 s cooldown (was 20), locked in the recoil.
>
> **Shift moves** (`AllMightAbilityManager.handle` reads `isShiftKeyDown`): Shift+G **Delaware** (3 air bullets 4 ticks apart, 30 blocks at 6/tick, 10 each, kb 3.4, `Wave.burst()` so all three land; 7 s), Shift+X **Manchester** (hop 5 + flip 12 ticks, axe-kick dive at 2.6/tick, 6-block crater 22, `Kind.SLAM`; 8 s), Shift+Z **Oklahoma** (14-tick spin, radius 7, 16, outward kb 3.2 + lift 0.9; 10 s), Shift+V **Missouri** (lunge + 12-block line of air 2.5 wide, 20, bosses staggered Slowness VI + Weakness 2 s via `Wave.then`; 8 s), Shift+C **St. Louis** (crescent 20 blocks at 2.5/tick, 4 -> 10 wide, 18; 9 s). Whole-body spin/flip in `AllMightSpinMixin` (`PlayerRenderer.setupRotations`).
>
> **Symbol of Peace**: `AllMightDamage.symbolOfPeace` -- any squadmate (`Squads.areAllies`) within 16 of an All Might in the Power Form takes 15% less (cancel-and-reissue; several All Mights do not stack).
>
> **Visuals**: `client.allmight.AllMightGlowLayer` -- additive (`RenderType.lightning()`) shells on both arms, strength 0.16 / 0.4 / 0.72 by Output, gold -> gold-white; at 100% / Plus Ultra a torso shell and jagged gold arcs re-struck every 2 ticks, plus server arm steam (`AllMight.armSteam`) and sparks. No glow in the recoil.
>
> **Passing on / lending** (`AllMightPassOn`): sneak + right-click another player with an empty hand -> `client.allmight.AllMightOfferScreen` (Pass On / Lend) -> `AllMightActionPayload.PASS_ON|LEND` (now carries the target entity id) -> chat offer with clickable `/oneforall accept|refuse`, 30 s (`OFFER_TICKS`), within 8 blocks. Everything re-checked on accept; UniquePowers checked with the giver's own slot excluded BEFORE anything changes. Pass On = `revoke(giver)` then `grant(receiver)` (`claimPrimary`), "Eat this" + strand-of-hair chat, a travelling gold glow. Lend = lender keeps state but `hasPower=false`, `loanRole=LENDER` (Weakness I + Slowness I refreshed all loan); borrower = fresh state, `loanRole=BORROWER`, cap 45% (`BORROWED_OUTPUT_CAP`), Strain x2, cannot pass on/lend. **Existing-Primary rule: loans are refused to anyone holding any power** (hero, mutation or Symbiote) -- there is no generic save/restore for other powers. Ends on time (5 min), borrower death (`AFTER_DEATH`), either side's logout, lender's sneak-right-click recall, borrower full Strain, or the borrower being revoked any other way (`revoke` -> `returnToLender`). The lender is restored through `claimPrimary` and gets `LEND_COOLDOWN_TICKS` 60 s. A loan never survives a logout; `onJoin` resolves a crash-interrupted loan per side, never leaving both holding it. Static offers/glows cleared via `AllMight.clearSessionState`. HUD shows the lender "Lent to X -- m:ss" and the borrower's time left.
>
> **HUD**: title + Output %, a three-segment Output gauge (next level filling), Strain hairline (red/blinking from 75), boxes crossed out in the recoil, status line (recoil seconds / Plus Ultra seconds / form). Left-Alt list shows every key's Shift move plus H and N.
>
> **Tests**: `AllMightRevampGameTests` (16) + the updated `AllMightGameTests`.

> **v0.16.6 (user):** Carolina Smash launches him further: `CAROLINA_DISTANCE` 30 (was 15), `CAROLINA_SPEED` 2.0 (was 1.5), so the slide is 15 ticks.

> **v0.13.5:** Carolina Smash's `tickMove` DASH branch now re-reads `flatLook(p)` every tick instead of
> locking to the heading captured once at launch -- the slide follows your aim continuously rather than
> just the direction you were facing the instant it started. Leap (`AllMightAbilities.leap`) passes
> cooldown `0` to `begin(...)` whenever `AllMight.state(p).plusUltra` is true, so it has no cooldown at
> all while Plus Ultra is active (its OFA cost, still 0, is unchanged).
>
> **v0.12.44 (ranges):** Smashes reach further (air pressure, not melee). `AllMightConfig`: `DETROIT_RANGE` 10 (width 3.5), `TEXAS_RANGE` 18 (width 4 -> `TEXAS_END_WIDTH` 10, swept per tick), `CAROLINA_DISTANCE` 15 (a physical slide, radial hits along the path), New Hampshire adds a launch-time forward sweep (`NEW_HAMPSHIRE_RANGE` 20 x `_WIDTH` 6 x `_BLAST_HEIGHT` 8, own hit set so the landing still hits), United States = `UNITED_STATES_PRIMARY_RANGE` 25 cone (width 8 -> 14) + `UNITED_STATES_RANGE` 35 expanding radial wave, sharing one hit set (no double damage). Plus Ultra unchanged (self-buff). Damage/cost/cooldown untouched. Wolverine lethal-fall chat message removed.
>
> **v0.12.43:** particles are gold (`Vector3f(1.0, 0.78, 0.2)` dust). `AllMightSuit.tearOffRegularArmour` (on transform: each non-All-Might armour piece takes `ARMOUR_TEAR_DAMAGE` 50 durability, is destroyed if that exceeds what it has, else unequipped and dropped with a 3 s pickup delay) and `bounceRegularArmour` (every 5 ticks in the Power Form: regular armour is moved back to the inventory).
>
> **v0.12.39 changes (supersede below):** `OFA_MAX` = 300 and OFA is a TIMER. The Power Form drains `FORM_DRAIN_AMOUNT` (1) every `FORM_DRAIN_INTERVAL_TICKS` (20) in `AllMight.tickOfa`; ONLY the Base Form refills, 1 per 40 ticks (combat lockout gone). Hitting 0 in the Power Form calls `exhaust` (back to Base Form); entering the Power Form needs >= 1 OFA. Every ability cost is 0 except Plus Ultra (`PLUS_ULTRA_COST` 50, paid on press, needs cost+1) and United States of Smash (100, paid in `fireUnitedStates`). Plus Ultra is a fixed 22 s (`PLUS_ULTRA_DURATION_TICKS`, new synced `AllMightState.plusUltraUntil`), cannot be toggled off, and `AllMight.endPlusUltra` starts the 20 s cooldown when it ends. The Regeneration I effect is gone: `tickPowerFormRegen` heals `FULL_REGEN_AMOUNT` (1 HP) every `FULL_REGEN_INTERVAL_TICKS` (80). HUD: the OFA readout is `m:ss` (OFA seconds) instead of a percentage; the Plus Ultra box shows its seconds left.
>
> **v0.12.38 changes (supersede below):** transform text "I am HERE", nothing shown on detransform. Costume locker is a custom two-slot menu (`AllMightLockerMenu`/`AllMightLockerScreen`, menu type `all_might_locker`). Power Form entity reach +3 (6 blocks, `FULL_REACH_BONUS`). Carolina is a level ground slide along `flatLook` (gravity kept, steps up ledges). Leap launches along the look vector (min lift 0.45, speed 2.4), cooldown 30 ticks. United States of Smash: charging is free (`begin` with cost 0 / cooldown 0); the 100 OFA and the 75 s cooldown are applied in `fireUnitedStates`; it is now an all-round expanding full-damage shockwave to 20 blocks (the cone + 30% outer wave are gone); the synced `chargeStart` drives the HUD hairline charge bar. HUD: "One For All NN%" on one line, then the bar.
>
> **v0.12.36 changes (supersede below):** the Vestige uses a **totem of undying** instead of the nether star. The costume is CRAFTABLE (`all_might_chestplate` = costume + arms, `all_might_leggings` = trousers; recipes in `data/projecthero/recipe`) and nobody starts with it. N in the Power Form (`AllMightActionPayload.OPEN_LOCKER`) opens the costume locker (`AllMightSuit.openLocker`, a vanilla one-row chest menu whose first two slots only accept the matching piece; contents on the persistent, unsynced `ALL_MIGHT_LOCKER` attachment). Transforming puts the locker's pieces on (`AllMightSuit.equip`), changing back returns them (`strip`), revoke returns everything, closing the locker in the Power Form wears them at once. The costume has no helmet (the model's head is empty), so the wearer's head and hat layer show.
>
> **v0.12.35 changes (supersede below):** the costume model is built from the supplied `AllMight.bbmodel` (`geo/all_might.geo.json`, `textures/armor/all_might.png`, generated by `scratchpad/gen_allmight_armor_v2.js`). The Power Form takes **no fall damage** (`fallReduction` = 1.0). HUD bar has no border.
>
> **v0.12.34 rework (supersedes the tables below where they differ):** costume removed (own skin). Two forms: Base Form (plain player, no abilities) and Power Form (H): 2.7 blocks tall (eased over 1 s, steam), 13 melee, 40 max HP (health % carries), 50% less damage, Speed III, Regeneration I, 3-block jump. Keys: R Detroit 18, Shift+R New Hampshire 20, G Texas 24, X Leap, Z United States of Smash (hold 5 s, 75 dmg, large crater), V Carolina 20 (26-block dash), C Plus Ultra (toggle: drains 5 OFA/s, all moves +30%, 20 s cooldown after; 0 OFA drops to Base Form; steam below 30 OFA). N no longer used. Command: `/projecthero power grant all_might` only.


A Hero-Tier **Primary** power. Package `com.projecthero.mod.allmight` (+ `.data`, `.item`); client half in
`com.projecthero.mod.client.allmight` (`AllMightPose`) and `client.gui.AllMightHud`. **Every tunable number is a constant in
`AllMightConfig`** — resource, costs, cooldowns, damage, ranges, both forms, Full Cowl, landing impacts, block destruction, particle
intensity and screen shake. Nothing else in the package hard-codes balance.

## Getting it

- **Vestige of One For All** (`projecthero:one_for_all_vestige`): shaped `P E P / D N D / P E P` — titanium-gold plates (P), enchanted
  golden apples (E), diamond blocks (D), a nether star (N). Using it calls `AllMight.grant` → `HeroTiers.claimPrimary` (so the usual
  two-slot replace-the-oldest rule applies), sets the state, fills OFA, equips the costume. `/projecthero allmight grant|remove|form|ofa|status`
  and `/projecthero power grant all_might` for admins.

## Keys

| Key | Ability | OFA | Cooldown | Notes |
|---|---|---:|---:|---|
| **H** | Transform (Full Power) | 0 | none (8-tick debounce) | persistent toggle; 1.5 s damage-proof transformation, 0.5 s to change back |
| **R** (Ability 1) | Detroit Smash | 10 | 3 s | 50 dmg, 5 long × 3 wide, wind-up 6 ticks |
| **G** (Ability 2) | Texas Smash | 15 | 6 s | 70 dmg, 10 long × 5 wide travelling wave (2 blocks/tick), wind-up 8 |
| **X** (Ability 3) | New Hampshire Smash | 25 | 8 s | launch ~15 blocks up + forward, 80 dmg to everything flown through, 6-block landing burst; adapts when airborne |
| **Z** (Ability 4) | Carolina Smash | 20 | 5 s | 10-block dash at 1.5 blocks/tick, 60 dmg along the path, stops at walls / unloaded chunks |
| **C** (Ability 6) | Full Cowl | 20 | 20 s | 10 s buff; cannot be re-cast while active (no OFA charged) |
| **V** (Ability 5) | United States of Smash | 100 | 60 s | 30-tick charge, 250 dmg 15-block cone, 25-block secondary wave, crater |
| **N** | All Might Leap | 5 | 5 s | ~17 blocks up + forward; the landing shockwave is the generic hard-landing impact |

(The slot numbers follow the mod's key layout: Ability 3 = X, 4 = Z, 5 = V, 6 = C.) N was implemented: the mod has no reusable enhanced-leap
for this kit and every other key is an attack, so a cheap traversal move genuinely adds something.

## Forms (stats recomputed from `(fullPower, cowl)` by `AllMight.reconcile`)

| | Contained | Full Power | Full Cowl (on top) |
|---|---|---|---|
| Max health | +40 | +40 | — |
| Melee damage | +25 | +35 | ×1.5 |
| Speed | +25% | +40% | +75% (replaces the form's if higher) |
| Knockback resistance | 80% | 100% | 100% |
| Jump height | 2.0× | 2.5× | +1.0× |
| Fall damage | −75% | −90% | — |
| Damage taken | −35% | −50% | ×0.8 more (multiplicative) |
| Smash damage | ×1 | ×1.15 | ×1.10 |

Jump values are *height* multipliers, converted to a jump-velocity modifier (`sqrt(h) − 1`). All modifiers are fixed-id transient attribute
modifiers set idempotently, so pressing H / C any number of times cannot stack anything.

## Architecture

| Piece | Role |
|---|---|
| `AllMightConfig` | every tunable |
| `data.AllMightState` | one persistent + `copyOnDeath` attachment `projecthero:all_might_state`, synced to all clients: power, form, OFA, Cowl / transformation / busy / no-fall clocks, cooldown map, current pose animation |
| `AllMight` | the server API: grant/revoke, OFA (`spendOfa` never goes negative), `reconcile`, `toggleForm`, per-tick regen / aura / landing detection, join / respawn / death hooks |
| `AllMightAbilities` | the seven abilities behind one gate (`begin`: power → alive → not locked → cooldown → OFA → spend); a per-player task queue lands each hit on its animation's impact frame; `Move` sessions for the Carolina dash and New Hampshire flight |
| `AllMightShockwave` | the reusable air-pressure utility: `sweep` (directional slab), `radial`, `strike`, `breakBlocks`, particles, shake. Boss-capped (10% max health per hit, no knockback) |
| `AllMightDamage` | `ALLOW_DAMAGE` cancel-and-reissue for the reductions; own launches never cause fall damage; passive-punch knockback / air-burst hook |
| `AllMightSuit` + `item.*` | the costume: four Curse-of-Binding-locked pieces synthesised on the wearer, rendered through the shared GeckoLib armour path; `armorSetId()` is resolved from the wearer's form (`all_might_base` / `all_might_full`) |
| `AllMightAbilityManager` | slot bridge (router branch after Wolverine) + tick |
| `network.AllMightActionPayload` | H and N requests; the server re-validates everything |

Server-authoritative: the client only sends key presses; damage, knockback, movement, OFA, cooldowns, the form and every buff are decided
server-side. The synced attachment is read client-side only for the HUD and the poses.

## Poses / animation

Player-shaped, so the Smash poses are keyframed limb rotations (`AllMightPose`) applied to the vanilla `HumanoidModel` from a `HumanoidModelMixin`
inject, driven by the synced `animId` / `animStart` (the same game-time clock the server schedules hits with — the fist is fully extended on the
keyframe where the hit lands). The GeckoLib costume copies its bones from the vanilla model, so it follows every pose. The body itself is GeckoLib
geometry: `geo/all_might_base.geo.json` (lean) and `geo/all_might_full.geo.json` (heavily inflated arms/chest/legs, extra shoulder cubes), both with a
cape bone and two hair-antenna bones; texture `textures/armor/all_might.png` (blond hair, face, blue suit with white V collar, yellow belt, white gloves,
red boots and cape). All generated by `scratchpad/gen_allmight.js`.

## Landing impacts

`AllMight.tickLanding` tracks the peak fall distance and, on touching ground: ≥ 6 blocks small (2 dmg, r 2), ≥ 12 medium (8, r 4), ≥ 20 heavy (20, r 7) —
knockback, dust, sound, shake on the bigger two. New Hampshire makes its own (bigger) burst and suppresses the generic one.

## Block destruction

`AllMightShockwave.breakBlocks`: needs `AllMightConfig.BLOCK_DESTRUCTION` and the mod's `AbilityHelpers.canGrief()`; never breaks unbreakable blocks
(hardness < 0), fluids, anything harder than the ability's cap, or a block `level.mayInteract` refuses; capped per ability (Detroit 12, Texas 30, Carolina 4,
New Hampshire 36, United States 160), nearest first, no drops.

## Tests

`AllMightGameTests`: grant + stats + costume, H toggling without stacking, damage/fall factors incl. Full Cowl multiplication, OFA never negative and gating,
OFA regeneration, Detroit landing at its impact frame (and a spammed R costing nothing), United States of Smash staged and lethal, Full Cowl not stacking and
expiring, codec persistence + revoke cleanup, Leap cost/cooldown.
