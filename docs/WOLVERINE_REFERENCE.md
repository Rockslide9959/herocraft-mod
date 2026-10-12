# Wolverine — Hero-Tier ascension of Super Regeneration (v0.12.1)

Package `com.projecthero.mod.wolverine` (+ `client.wolverine`, `command.WolverineCommand`,
`network.WolverineActionPayload`). Wolverine is a **Primary** power that *ascends* the Super Regeneration
mutation the same way Spider-Man ascends Spider Adhesion. It is not a second regeneration system: it
reuses Super Regeneration's base heal tick and debuff-shortening mixin and scales them.

> **v0.16.1 (claws, combo, VFX, tools, suit, sniff):**
> - **Claw model** (`client.wolverine.WolverineClawsModel`): no longer ModelParts/boxes (the `wolverine_claws` model layer
>   is gone) -- a procedural mesh. Per hand 3 blades, each 16 segments along a centreline that bends toward the body
>   (`CURVE` 0.42 rad, eased u^2), every ring a 4-point diamond (sharp edge on the concave side, thicker spine, two
>   bevels), tapering to a needle point; real per-face normals. Deploying slides the blades out of the fist (rings
>   inside the fist are clipped at `ROOT_Y`). Length 16 px first person, 8 px third person (`WORLD_LENGTH`, so a hanging
>   arm never digs them into the ground). Texture `textures/entity/wolverine_claws.png` (32x128 brushed-metal strip,
>   one column per face) from `scratchpad/gen_wolverine_claws_v0161.js`. There is no bone tier (the tier system was
>   removed in v0.12.30) -- only adamantium.
> - **Melee combo** (`WolverineClawCombo`): left click = right slash, left slash, overhead cross (both arms), repeat.
>   `WolverineAttackMixin` now picks `WolverineClawCombo.nextHand` instead of a random hand; `client.mixin.WolverineSwingMixin`
>   (TAIL of `LivingEntity.swing`, client only) records each swing start, so every viewer derives the same step from the
>   swing packet's hand (off hand = step 2; main hand within 20 ticks of an off-hand swing = cross). No new packets; the
>   hit is still vanilla's.
> - **Poses** (`WolverineClawPose`, one key-frame table drives third person via `HumanoidModelMixin` and first person via
>   `ItemInHandRendererClawsMixin`, which now draws BOTH arms while the claws are out): combo slashes / cross; R = wide
>   horizontal sweep; G = two diagonal slashes; V = fast slashes; Z = arms drawn back and low while charging
>   (`chargeStartedAt`), then a two-claw thrust; C = a roar (arms flung out, head back). Abilities key off the synced
>   `lastAction` / `lastActionTick`. The guard pose still wins; the Claw Dash keeps its own lunge pose.
> - **VFX** (`WolverineClawFx`): additive slash-trail ribbons swept by the outer 60% of each blade during the cutting
>   phase (third person in world space from the layer, drawn after translucents; first person in the hand's camera
>   space), fading over 3.5 ticks, red-tinged in Rage; strike sparks (`ELECTRIC_SPARK` + a crit, plus a vanilla sweep for
>   combo slashes) once per strike, client side, skipped on Minimal particles / halved on Decreased / not beyond 32
>   blocks. Server: `WolverineAbilities.strike` adds 4 `ELECTRIC_SPARK`s per hit. Blood-free.
> - **Claws as tools** (`WolverineBareHands`): iron shovel + iron axe + (as before) sword -- the best tool speed scales
>   vanilla's bare-hand speed (so haste / water / airborne / `BLOCK_BREAK_SPEED` still apply), drops if any of the
>   three is the correct tool. The claws are not items, so right-click on blocks does nothing a tool would (no paths,
>   no stripping) -- gametest `clawsDoNothingWhenRightClickingBlocks` pins that.
> - **Melee bonus:** the +3 `wolverine_attack` modifier was already an `ATTACK_DAMAGE` modifier, i.e. it applies with any
>   weapon (iron sword 6 + 3 = 9); now documented as his strength. The +8 `wolverine_claw_attack` is the claws' own
>   weapon damage and only applies with claws out + empty hand (nothing can be held with them out), so the two never
>   double-dip: claws = 1 + 3 + 8 = 12. Numbers unchanged.
> - **Suit mend** (`WolverineSuitMend`): 1 durability / s (worn or carried) once the holder has been out of combat 30 s
>   (no damage dealt or taken; unsaved `wolverine_suit_last_combat` attachment set from `AFTER_DAMAGE` for victim and
>   attacker, claw-deploy wound excluded). Replaces v0.15.21's 1 per 5 s.
> - **Sniff sound:** `WolverineSounds.SNIFF` (`projecthero:wolverine_sniff`, 3 synthesised takes
>   `sounds/wolverine/sniff1..3.ogg`, from `scratchpad/synth_wolverine_sniff.js` + ffmpeg) replaces the Sniffer mob noises.

> **v0.16.1 deploy v3:** the motion is now forearms raised in front of the face (fists at eye height, 5 t), then
> thrown down and out to his sides (arms angled down/out from the hips, chest out) as the blades pop at 9.5 t, settle
> by 20 t; first person mirrors it (fists up in view, then down to the lower edges). Everything else as v2.

> **v0.16.1 deploy v2:** H only (`Wolverine.setClaws(player, out, manual)`; `toggleClaws` passes manual): fists in to
> the chest (4.5 t), arms thrown out to the sides (9.5 t, blades pop: `DEPLOY_DELAY_TICKS` 5.5 + extend 4, snikt
> scheduled at `CLAW_POP_TICKS` 9 via `WolverineScheduler`), settle by 20 t. Arm roll is the natural one per pose
> (edge inward at the chest, down when raised sideways), fixing the upside-down fists. The client treats a deploy as
> manual when no ability stamp / Z charge came at or after `clawsChangedAt` (`WolverineClawsModel.manualDeploy`):
> ability auto-deploys play no deploy motion and the blades come out at once (sound immediate). No retract animation.

> **v0.16.1 animation redo (after playtest):** `WolverineClawPose` was rebuilt as key-framed clips in ticks
> (anticipation, a SNAP segment with a strong ease-out, follow-through, quick ease-out recovery). Each key holds the
> torso twist (yaw) and lean (pitch) -- applied about the hips like `HipBend` but generalised to a twisted torso
> (`torso()` moves the neck / head / shoulder pivots) -- both arms as aim + blade-edge directions in torso space
> (`aim()` builds the arm rotation so the curved blades cut edge-first), leg pitches for a step, and head pitch.
> Combo: 1 = rising diagonal right (`DIAG`, 8 t), 2 = horizontal left (`HORIZ`, 8 t), 3 = both arms high outside
> the shoulders raked down into an X in front of the chest with a hip crunch (`CROSS`, 10 t); the off arm guards low
> (never in front of the face) then counter-pulls. Abilities reuse the clips: R = `HORIZ` sweep, G = `DIAG` slashes,
> V = `DIAG` at 2x speed, Z = `CHARGE` (held) then `THRUST`, C = `ROAR`; lead arms mirror automatically. Claw deploy:
> `DEPLOY` (arms flung out to the sides, chest out, head back), the blades pop at full extension
> (`WolverineClawsModel.extension` waits `DEPLOY_DELAY_TICKS` 3 then extends over 4) and the server's snikt
> (`Wolverine.clawSound`) plays at `CLAW_POP_TICKS` 6 from `WolverinePassives`; retract = `RETRACT` (arms pull in
> low). First person has matching `F_*` clips in `firstPerson()` (rotations pivot about the hand; yaw/pitch aim the
> claws across the view since a Z roll only twists the forearm) and draws both hands while
> `WolverineClawsModel.handsShown`. Vanilla's punch swing is switched off while the claws are out
> (`HumanoidModelMixin` zeroes `attackTime` at HEAD; first person passes swing 0 and equip 0), and the third-person
> pose skips vanilla's first-person `renderHand` setupAnim (`WolverineClawPose.inFirstPersonHand`, set by
> `PlayerRendererClawsMixin`). Combo steps are never cut before their impact (`commitTicks`), so held mining no longer
> restarts them every 3 ticks. Trails are now thin camera-facing arcs along the blade TIPS only, during the snap,
> fading over 4.5 ticks (no translucent sheets); the vanilla sweep particle on the finisher is gone.
> **Gotcha:** JOML 1.10.5's `Quaternionf.getEulerAnglesZYX` does NOT round-trip with `rotationZYX` -- `set()` extracts
> the ModelPart angles from the matrix by hand. The ascension's Resistance II no longer shows swirl particles (the purple
> glyphs in early screenshots). Frame-strip harness: `scratchpad/V0161WolvStripHarness*.java.txt` (grabs the previous
> frame at the head of `Minecraft.runTick` once its game time passes each beat, tick rate 4), sheets via
> `scratchpad/compose_wolv_sheets.js`.

> **v0.15.18 (Death Surge rework):** no cooldown / one-use limit (`EMERGENCY_COOLDOWN_TICKS`, `EMERGENCY_HEAL_TICKS`,
> `FLESH_HOLD_TICKS`, `FLESH_FADE_TICKS` deleted; `emergencyReadyAt` / `emergencyHealUntil` are no longer written). Every
> lethal hit fires it (`WolverineDamage.allowDeath` -> `WolverinePassives.tryEmergency`) but each one drains
> `SURGE_HEAL_POOL_COST` (30) Healing Factor; with less than 30 in the pool it does not fire and he dies. A surge during
> the recovery restarts it. The flesh state is `WolverineState.skinRecovery` (0 raw flesh .. 1 skin; in `Extra`,
> optional `skin_recovery` default 1.0 so old saves load clean). `WolverinePassives.tickEmergency` advances it every
> 5 ticks by 5/`SKIN_RECOVERY_TICKS` (400) ONLY while health >= max (counted in whole ticks, so 0.5 / 1.0 are exact);
> below full HP it pauses. Slowness III + Blindness + Weakness I are re-applied every 5 ticks while progress <
> `SURGE_DEBUFF_UNTIL` (0.5) and removed when it gets there; `Wolverine.resurrecting` (red border, shredded suit) =
> progress < 0.5, `surgeRecovering` = progress < 1. Invulnerability (first `EMERGENCY_INVULN_TICKS` 5 s) and the bleed
> (`EMERGENCY_BLEED_TICKS` 15 s) still key off `fleshStartedAt`. There is no regeneration penalty (none since v0.12.20).
> Client: `WolverineFlesh.skinAlpha` = synced progress (all viewers); HUD shows "Skin N%" / "Skin N% - Full HP to heal"
> + a 2 px bar (white tick at 50%) (the Healing Factor bar has no surge pixel or 30-cost tick any more).
> Respawn / re-ascension reset progress to 1.

> **v0.13.9 (mouse buttons):** the right-click off-hand strike is gone (`WolverineSense.offHandStrike` and
> `OFFHAND_STRIKE_*` deleted). **Left click** with the claws out and both hands empty swings a random hand:
> `client.mixin.WolverineAttackMixin` `@ModifyArg`s the `LocalPlayer.swing(hand)` call in
> `Minecraft.startAttack` -- the swing packet carries the hand, so every viewer sees the same one; the hit is
> vanilla's either way. **Holding right click** raises a claw guard (`WolverineBlock`): the client sends
> `WolverineActionPayload` `BLOCK_START`/`BLOCK_STOP` on the press edges (`ProjectHeroModClient.handleWolverineOffHand`),
> the server re-validates (`canBlock` = claws out + empty hands) and sets the synced, non-persistent
> `ModAttachments.WOLVERINE_BLOCKING`; `WolverineBlock.tick` drops it when the guard stops being legal.
> `WolverineDamage` multiplies by `1 - BLOCK_DAMAGE_REDUCTION` (0.30) for anything not tagged
> `minecraft:bypasses_shield` (so falls, fire, drowning, magic go through), on top of the passive 35% --
> harness: a raw 10 hit costs 6.50 unguarded, 4.55 guarded; a fall is unchanged. Like a raised shield he
> can't attack while guarding (the mixin cancels `startAttack`/`continueAttack`). Poses: `HumanoidModelMixin`
> raises both arms crossed in front of the face (every viewer); `ItemInHandRendererClawsMixin` tips both
> first-person forearms 60 degrees in so the claws cross above the crosshair. Screenshot-verified.
>
> **v0.12.43:** `WolverineDamage.survivedLethalFall`: a FALL that would kill (after the 75% reduction) leaves 1 HP, the pool loses min(damage, `FALL_SURVIVE_MAX_ABSORB` 100), Slowness VI 20 s (`WolverinePassives.tickLegFlesh` keeps it up) and `WolverineState.legFleshStartedAt` drives `client.wolverine.WolverineLegFleshLayer` (legs-only flesh texture, hold 20 s then fade 20 s). Needs pool > 0. The state codec is at 16 fields, so healPool/lastHurtAt/legFleshStartedAt now live in the nested `Extra` record. Death Surge HUD marker is 3 px.
>
> **v0.12.41:** pool max 250, refill 5 HP/s, HUD shows only the percentage and the Death Surge marker is 2 px wide.
>
> **v0.12.40:** the healing factor is paid from `WolverineState.healPool` (`HEAL_POOL_MAX` 120 HP). `WolverinePassives.healingFactor` heals `REGEN_HP` (1.5) every 5 ticks and subtracts the HP actually healed; at 0 it does nothing. The pool refills `HEAL_POOL_REGEN_PER_SECOND` (3) only once `lastHurtAt` (set by `Wolverine.markHurt` when he TAKES damage) is `HEAL_POOL_REGEN_DELAY_TICKS` (100) old. The Death Surge is untouched by the pool. The HUD's rage bar is replaced by "Healing Factor N HP P%" over `[Death Surge pixel][1 px gap][pool bar]` (pixel orange when `emergencyReadyAt` has passed, dark grey while it recharges); the Berserker Rage meter still gates C and is shown as the fill of the C box.

## Getting it

1. Own **Super Regeneration** (power 12). Without it the serum does nothing and is not consumed.
2. Craft an **Adamantium Serum** (`DND / BGB / DND`: D diamond, N netherite ingot, B blaze powder, G golden
   apple) and right-click it. It consumes Super Regeneration and, like every Primary power, replaces the
   mutation group (`HeroTiers.claimPrimary(player, "wolverine")`) — the oldest hero is replaced if two are held.
3. Admin: `/wolverine power grant|revoke [player]`, `/wolverine claws deploy|retract`, `/wolverine status`
   (also `/heropower grant hero wolverine`). `grant` gives Super Regeneration first if it is missing.

## Controls

| Key | Slot | Move | Damage | Cooldown |
|---|---|---|---|---|
| R | Ability 1 | Claw Slash | 18, 3.5-block arc | 1.5 s |
| G | Ability 2 | Cross Slash | 15 + 15, 3 blocks | 4 s |
| Z | Ability 3 | Claw Dash | 18 to each enemy cut through, 21-block launch; grabs the first enemy hit and drags it in front of you until you land; lunge pose | 6 s |
| X | Ability 4 | Berserker Rage | 12 s: +50% dmg, +30% speed, 2x regen, -20% dmg taken, ~100% KB resist | 45 s |
| C | Ability 5 | Frenzy | 5 strikes x 8 (0.2 s apart), enemies within 4 blocks | 15 s |
| V | Ability 6 | Adamantium Execution | 60, 10 blocks, direct lunge onto the locked (red-glow) target, needs a target | 30 s (8 s on a whiff) |
| H | Utility 1 | Claws deploy / retract | — | 0.4 s spam guard |

There are **no new keybinds**. R/G/Z/X/C/V are the mod's existing Ability 1–6 keys; `AbilityRouter` hands
them to `WolverineAbilityManager` only while the player holds Wolverine and has no mutation selected (same
terms as every other Hero-Tier power), so every other power keeps its own meaning for those keys. H is
Utility 1 (it already toggles Iron Man's faceplate, Max Steel's helmet, the Symbiote); as Wolverine it toggles
the claws, and **Shift+H** still opens the power wheel.

Claw moves deploy the claws for you if they are retracted; Rage does not need them.

## Passives (all in `WolverineConfig`)

- **Healing factor:** Super Regeneration's base heal (`SuperRegenerationHandlers.tickBaseRegen`) at 1x / 2x / 3x
  → 4 / 8 / 12 HP/s above 50% / below 50% / below 25% health, ignoring combat; doubled in Rage.
- **Survivability:** 35% less physical damage (fire, magic, poison, wither, hunger are not reduced), a further
  20% in Rage (multiplicative, 0.65 x 0.8), 75% less fall damage, 75% knockback resistance (~100% in Rage / mid-dash),
  Poison and Wither last a quarter as long, the other Super Regeneration debuff cuts (50%) still apply.
- **Physique:** melee damage 4 (claws out and empty hand: +8 = 12), +20% speed (+30% more in Rage), +25% jump, mining
  1.5x with claws out (1.2x passive from strength). All are fixed-id attribute modifiers, reconciled every tick
  and removed with the power.
- **Enhanced senses:** hostile mobs within 12 blocks are outlined *for the Wolverine's own client only*
  (`EntityGlowMixin`; nothing is set on the mob).
- **Emergency heal:** below 15% health (or on a would-be-lethal hit) restore 30% of max health over 2 s, then a
  60 s internal cooldown. `/kill` and the void still kill.
- No bleeding mechanic exists in the mod, so none was added and there is no bleed chance / immunity.

## Architecture

| Piece | File |
|---|---|
| All numbers | `WolverineConfig` |
| Attachment (persistent, copy-on-death, synced to all) | `data/WolverineState`, `ModAttachments.WOLVERINE_STATE` |
| Server API: state, ascension, claws, cooldowns, lifecycle | `Wolverine` |
| Stat modifiers, healing factor, emergency heal, timer expiry | `WolverinePassives` |
| Damage reduction, fall reduction, emergency triggers | `WolverineDamage` (`ALLOW_DAMAGE` cancel-and-reapply with a re-entrancy guard, like `PunisherDamage`) |
| The six abilities | `WolverineAbilities` (delayed hits via `WolverineScheduler`) |
| Router bridge, per-tick upkeep | `WolverineAbilityManager` |
| Ascension item | `item/AdamantiumSerumItem`, `item/WolverineItems` |
| H toggle packet | `network/WolverineActionPayload` |

Everything gameplay-critical is server-side: the client only sends "slot N pressed" (`AbilityInputPayload`) and
the H toggle; damage, healing, cooldowns, targeting and Rage state are never taken from the client. Cooldowns
live in `WolverineState.abilityReadyAt` (same scheme as the Punisher / Spider-Man) and are synced so the HUD can
draw them. `WolverineScheduler` is a static cache and is emptied by `ServerStateReset` and `Wolverine.clearTransient`.

## Client

- `WolverineClawsModel` — code-built model: 3 curved blades per hand, each a chain of four tapering segments on
  nested child parts (12 boxes a hand), texture `textures/entity/wolverine_claws.png`. Extension animates over
  6 ticks from `WolverineState.clawsChangedAt`, so every viewer sees the same deploy / retract.
- `WolverineClawsLayer` — third person and other players (registered as a `PlayerRenderer` feature layer).
- `PlayerRendererClawsMixin` + `WolverineClawsRenderer` — first-person hand.
- `WolverineHud` — the six keys + cooldowns (Left-Alt shows names), `WOLVERINE` label, claw state, Rage timer.
- Guide chapter (`HeroPackGuide.wolverineChapter`), "Your Power" (I) screen, squad identity.

Sounds and particles are vanilla (sweep attack, crit, enchanted hit, angry villager, crimson spore, wolf growl,
chain, anvil, phantom swoop). No animation files: the swing uses the vanilla arm swing.

## v0.12.1 companion changes

- **Symbiote** suit growth is now +50% (1.8 → 2.7 blocks, Iron Golem height).
- **Symbiote Lunge** is a single ballistic launch (`AbilityHelpers.ballisticLaunch`) sized to carry the host
  20 blocks, instead of a held push.
- **Size Manipulation** forms ease in and out over 1 second (`SizeHandlers.SCALE_ANIM`).

## Tests

`gametest/WolverineGameTests`: prerequisite gate, stat apply/remove/idempotence, claws toggle, Rage (12 s,
no stacking, cleanup), R routing + cooldown gate, Frenzy target rule, emergency heal + cooldown, other powers
untouched.

## v0.12.10 kit changes

- **Keys:** R Claw Slash (18) · G Cross Slash (12 + 12) · X Claw Dash (18) · Z Adamantium Execution · V Frenzy · C Berserker Rage · N Sniff · H claws.
- **Z Execution:** hold 5 s to charge (Slowness II while charging), release to fire. Early release cancels with no cooldown. v0.13.4: no more wind-up freeze -- release immediately marks whatever's in the 10-block look-cone (red glow, caster-only) and lunges straight at them for 60 damage; 30 s cooldown.
- **C Rage:** gated by a rage bar (0-100), no cooldown. +2.5 per HP taken, +1.5 per HP dealt (`WolverineConfig`), not gained while raging. Full bar -> 30 s of rage, bar empties. Bar resets on respawn.
- **N Sniff:** every living thing within 40 blocks highlighted for 20 s, no cooldown (10-tick anti-spam), sniffer sound. Client-only render via `WolverineSensePayload`.
- **Hunters:** any mob targeting the Wolverine within 40 blocks glows orange at all times (replaces the old 12-block hostile glow). Only the Wolverine's client draws it.

## v0.12.12

- **Left hand = right hand:** (superseded in v0.13.9 -- see the top) the right-click off-hand strike now deals a fixed 12 (+50% in Rage) at full
  strength (`WolverineSense.offHandStrike`), no longer scaled by the swing cooldown or a held item.
- **Claw Dash grab:** the first enemy struck is pinned 1.3 blocks ahead and carried with the player's velocity
  until the dash ends (landing; 30-tick safety cap), never through walls (`WolverineAbilities.dragGrabbed`).
- **Lunge pose:** `WolverineDashPose` + `PlayerRendererMixin` / `HumanoidModelMixin` lean the body 55° forward
  with both arms thrust ahead while `dashUntil` is active (visible to every viewer).

## v0.12.13

- Claw Dash range 7 -> 21 blocks. The grabbed enemy is now placed ahead of the player by 2 ticks of their velocity
  (1.6 blocks in front) so it stays in front instead of trailing.
- Claw moves alternate right / left hand (`WolverineAbilities.swing`); (v0.13.9: there is no right-click strike any more -- right click blocks).
- Symbiote suit growth is now base-host only (`Symbiote.tickGrowth`); Symbiote Spider-Man keeps his size.

## v0.12.14

- **Claws mine like swords:** `WolverineBareHands` (hooked in `PlayerMixin`) gives claws-out + empty-hand a netherite
  sword's destroy speed and drop rules (cobweb near-instant + drops, sword-efficient blocks 1.5x), on top of the 1.5x.
- **Wolverine Suit:** 4 craftable costume pieces (`WolverineItems.SUIT_*`, set id `wolverine`, `geo/wolverine.geo.json`,
  `textures/armor/wolverine.png`, recipes: leather + yellow/blue dye). Built from `WolverinArmour.bbmodel`.
- **Death flesh model:** `PlayerRendererFleshMixin` swaps the skin to `textures/entity/wolverine_flesh.png`
  (from `Flesh.bbmodel`) while a Wolverine `isDeadOrDying()`.
- **Symbiote Spider-Man** no longer gets the base host's passives: `Symbiote.isNormalHost` now gates the Biomass bar,
  damage rules, Resistance I, tool-hands, raw-food stomach and Predator Vision.

## v0.12.15

- **Emergency resurrection cooldown 60 s -> 180 s.** It now stamps `WolverineState.fleshStartedAt`; every client
  (`WolverineFlesh`) shows full flesh for 10 s then fades the real skin back over the flesh for 10 s
  (`PlayerRendererFleshMixin` picks the flesh texture, `WolverineFleshLayer` draws the skin at rising alpha).
- **Rage bar:** fills 50% slower (0.375 / 0.225 per damage taken / dealt) and drains 2/s after 10 s with no damage
  dealt or taken (`WolverineState.lastCombatAt`, `WolverinePassives.drainRage`).
- **Symbiote:** Resistance I removed; wearing the suit cuts all damage taken by 10%.

## v0.12.16

- **Death resurrection** (replaces the <15% emergency heal): a lethal hit no longer kills a Wolverine whose
  resurrection is ready (3 min cooldown). He rises at 30% health for 10 s of total invulnerability (only `/kill` and
  the void get through), Slowness III + Blindness + Weakness I (re-applied every 5 ticks so the debuff-halving
  mixin cannot shorten them, removed at the end), in the flesh model; then the skin fades back over the next 10 s.
  On cooldown he can die normally. `WolverinePassives.tryEmergency`, `Wolverine.resurrecting`.
- **R (Claw Slash, radius 4.5), G (Cross Slash, radius 4) and V (Frenzy, every strike hits everything within 4 blocks)
  are now area attacks** around him, not frontal.
- **Rage bar:** +1% per hit dealt or taken (flat, `RAGE_PER_HIT`), -5%/s after 10 s out of combat.
- **No held items with the claws out:** both hands are cleared each tick into free inventory slots (claws retract if
  there is no room). `WolverinePassives.clearHands`.
- **Healing factor nerfed:** 3 / 6 / 9 HP/s (was 4 / 8 / 12) at full / below half / below quarter health.
- **Suit:** leather-level (1/3/2/1); `SuperheroArmorModel` swaps to `wolverine_damaged_1..3.png` as the wearer's
  health falls below 75 / 50 / 25%.
- **Fix:** the boots recipe listed an unused blue-dye key, so it never loaded in 0.12.14/0.12.15.
- **Super Regeneration:** Cellular Surge now adds +8 HP/s (was +4). Base 4 HP/s and Regeneration Mode +4 unchanged.
- **Commands:** `/projecthero` is now only `power grant|remove|stack`, `locate <structure>` and
  `raid start|end|removetimer|advancetimer <supervillain|gravebound>` (op-only). `/squad` stays.
- **Thor:** releasing the hammer marks the player (`HAMMER_RELEASED`); gaining any other power afterwards zeroes
  their worthiness so they cannot lift Mjolnir and stack it.

## v0.12.17

- Death Surge visuals: red screen-edge shadow for the 10 s window (WolverineSurgeOverlay); first-person hand uses the flesh texture and the skin fades back over it (PlayerRendererFleshHandMixin); HUD line is now "Death Surge: <s>".
- R / G are frontal again but hit a box 3 blocks wide (inBox, reach 3.5 / 3.0); V (Frenzy) is back to its old nearest-target spread.
- Suit: chainmail protection (1/4/5/2), iron durability (multiplier 15); four damage textures (below 90 / 65 / 40% health, plus a near-fully-torn-off one during the Death Surge) with whole chunks removed to bare the skin.

## v0.12.18

- Suit damage lingers: the shown tear stage jumps up with damage but repairs one stage every 5 s (20 s from fully shredded) - WolverineSuitWear.
- Death Surge: debuffs / red border / torn suit last 20 s, the flesh->skin fade another 20 s; damage immunity stays the first 10 s; healing factor halved for all 40 s.
- Deploying the claws costs 4 armour-bypassing damage (never lethal, not in creative).
- Claw Dash sweeps a box 3 blocks wide along its line of travel instead of a 2-block-radius sphere.

## v0.12.19

- Death Surge bursts into a spray of blood (red dust + redstone-block chips) and keeps bleeding through the first ~15 s.
- Claws deploy with the retract sound. Suit durability is now diamond level (multiplier 33).

## v0.12.20

- Healing factor is a flat **1.5 HP every 5 ticks** (no health tiers, no Rage bonus, no Death Surge halving).
- Death Surge invulnerability is **5 s** (the surge itself, debuffs and the bleed are unchanged).
- Deploying the claws still costs 4 HP but is not combat: `Wolverine.deployingClaws()` keeps it out of the Rage bar /
  combat timer, and `WolverineSuitWear` ignores that wound for 3 s so the suit never tears from it.
- Claw Dash damage 18 -> 12.

## v0.12.21

- The passive +40% movement speed is removed (`SPEED_BONUS = 0`); Berserker Rage's +30% speed remains.

## v0.12.22

- Claw Dash now properly grabs players: a player target is seized even when the hit is swallowed by their damage cooldown
  (never a squadmate, PvP rules still apply), and is dragged by velocity + a catch-up teleport, because a player's own
  client overrides `setPos`.
