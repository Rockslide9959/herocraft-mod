# Titan Shifter — reference (v0.12.32)

> **v0.12.38:** the "Titan Shift Ready [H]" HUD text is yellow (same as "Revert Form [H]").
>
> **v0.12.36 changes (supersede below):** the Titan Serum uses a **golden apple** instead of the nether star. Changing back is instant: `TitanShifter.finishRevert` spawns a `TitanCorpseEntity` (sliced `titan_form_corpse` geo + atlas from `scratchpad/gen_titan_corpse.js`, plays `animation.titan.death`), ejects the shifter at the nape of the neck (up 0.42, back 0.28, Slow Falling 14 s) and the corpse steams every `corpseSteamTicks` (60) and dissolves over `corpseTicks` (1200): `TitanCorpseRenderer` hides one `piece_*` bone after another (order/timing in `TitanCorpseEntity`), shrinking and sinking each for 90 ticks first. The Titan takes no fall damage. Bite (N) also eats: food +8, saturation full, and `biteRegenPerSecond` 5 HP/s for `biteRegenTicks` 100. HUD shows Titan HP x / max.
>
> **v0.12.35 changes (supersede below):** new player skin (same rig; the Hardening recolour is derived from it). Base-form regeneration is **Regeneration II** and drains **1.5 Titan Energy/s** while it heals (recharge paused meanwhile, stops at 0). Leap while sprinting/running: horizontal 3.0 -> 6.5, vertical 1.26 -> 1.5 (`leapSprint*`). Roar: radius 12 -> 32 in a cylinder (12 below the feet to 12 above the head, so ground mobs are always inside), effects Weakness II 12 s, Slowness III 12 s, Nausea 3 s, Blindness 2 s, Mining Fatigue 3 s (bosses x0.25). Low-energy transform attempt shows red action-bar text "Titan Form exhausted, Recharge energy". HUD: borderless bars, yellow energy bar. Config version 4 migrates `baseFormRegenAmplifier` and `roarRadius`.
>
> **v0.12.34 changes (supersede above where they differ):** passive regeneration is base-form (human) only (Regeneration III, free); Titan has none. Ordinary mobs ignore Titan armour and hurt it (mobs targeting the shifter are re-aimed at the Titan). Hold Sprint 3 s while walking = run (x1.8). Squad-mates right-click to ride a shoulder (2 seats). N grab / N bite (26 dmg, 10 s) / Shift+N lower. Transform needs a full (100%) bar. HUD redesigned. Command: `/projecthero power grant titan_shifter` only.


> **v0.12.32 changes** (everything below is current): the body is now a regular player model (the supplied
> `aot/titanshifter.bbmodel` skin) scaled to 11 blocks — hit-box 3.67 × 11; **H** transforms / detransforms (the J key
> is gone); new key layout (X Leap, Z Stomp, Shift+X Roar, Shift+C Hardening); the **Titan Energy** bar (100 max, 90% to
> transform, emptied on reverting, +1%/s as a human, spent by a 3 HP/s base regeneration); the 60 s shift cooldown is now
> 0 (the bar is the limit). Assets come from `scratchpad/gen_titan_v2.js` (the old `gen_titan.js` generator is obsolete
> for the model/animations/textures but still owns the serum icon).

A Hero-Tier **Primary** power. Package `com.projecthero.mod.titanshifter`; client half in
`com.projecthero.mod.client.titanshifter`. Config: `config/projecthero_titan_shifter.json` (rewritten with any new
keys on every start).

## Getting it

- **Titan Serum** (`projecthero:titan_serum`, recipe `PNP / MSM / PNP`: titanium-gold plate, netherite ingot, magma
  block, nether star). Using it calls `TitanShifter.grant`: `HeroTiers.claimPrimary(player, "titan_shifter")` (so the
  usual two-slot replace-the-oldest rule applies), sets `unlocked`, prints the hint. It does **not** transform the
  player (config `transformation.serumTransformsImmediately` can change that).
- Commands (op 2): `/projecthero titanshifter grant|remove|transform|revert [player]`, `spawn` (an ownerless test
  Titan), `status`; also `/projecthero power grant|remove titan_shifter`.

## Architecture

| Piece | Role |
|---|---|
| `TitanPhase` | `HUMAN, TRANSFORMING, TITAN, REVERTING, DEFEATED, RECOVERING` + `canGoTo` transition table |
| `TitanType` | `GENERIC_TITAN` — model/texture/animation names, size, stats, damage scale. **Add a constant to add a Titan.** |
| `data.TitanShifterState` | persistent + copyOnDeath attachment `projecthero:titan_shifter_state`, synced to the owner only |
| `TitanShifter` | the whole server API: grant/revoke, transform, revert, defeat, `forceEnd`, per-tick phase machine, join/respawn/logout hooks |
| `entity.TitanFormEntity` | the Titan: a `LivingEntity` (`noSave`, `noSummon`), GeckoLib `GeoEntity`, rider = owner |
| `TitanAbilities` / `TitanShifterAbilityManager` | the seven abilities and the slot bridge (router branch before Thor) |
| `TitanCombat` | targeting (no self / rider / squad / pets), damage scaling, AoE, foliage breaking, impact FX, camera shake |
| `TitanShifterDamage` | `ALLOW_DAMAGE`: the rider takes no damage while inside (void / `/kill` still work) |
| `TitanShifterConfig` | GSON balance config |

### Why the Titan is ridden

The shifter is the Titan's controlling passenger (hidden client-side by `PlayerRendererTitanMixin`, first-person hands
by `ItemInHandRendererTitanMixin`, sneaking cannot dismount — `PlayerMixin#wantsToStopRiding`). `isControlledByLocalInstance`
is `false` on the client, so **the server reads the rider's input** (`xxa/zza/jumping`, sent by vanilla for any passenger)
and drives the Titan through the stock ridden-mob path; clients only interpolate. Jump uses `LivingEntityAccessor`.
The seat height follows `visualScale()` (swelling during the transformation, shrinking on revert/defeat) so the camera
never floats over a small body. `TitanCameraMixin` pulls the third-person camera back and applies shake.

### Never persistent

The Titan is `noSave`; a logout / server stop runs `forceEnd` (rider put on the ground where the Titan stood, form
discarded); a join finding a non-HUMAN phase snaps the player to the ground; a Titan whose owner is gone or no longer
riding it removes itself after 30 ticks. So a reconnect can never duplicate a Titan.

## Controls

| Key | Action |
|---|---|
| **H** (`key.projecthero.power_select`) | Titan Shift: transform (human, needs 90% Titan Energy) / revert (Titan) — only sends a request. Shift+H (human) still opens the power wheel |
| **R** Ability 1 | Titan Punch — 20 dmg, 0.8 s. 3rd swing of a combo = Heavy Punch (35). **Shift+R** = Titan Kick (30, low wide sweep) |
| **G** Ability 2 | Heavy Smash — 1 s charge (slowed), 50 dmg in a 3.5-block area, huge knockback, 8 s |
| **X** Ability 3 | Titan Leap — ~3× jump (measured ~8 blocks up, ~20 forward), landing 20 dmg in 5 blocks, 5 s. **Shift+X** = Titan Roar |
| **Z** Ability 4 | Titan Stomp — 25 dmg, 6 blocks, 6 s |
| **V** Ability 5 | Titan Roar — 12 blocks, Slowness III + Weakness II 10 s, mobs thrown back and flee; bosses take 25% duration and no knockback; 15 s (shared cooldown with Shift+X) |
| **C** Ability 6 | Titan Regeneration — 10 HP/s for 10 s, 45 s. **Shift+C** = Titan Hardening — 60% less damage 8 s, crystal texture, 30 s |

## Titan Energy (v0.12.32)

`TitanShifterState.energy` (float, synced to the owner, codec default 100 so v0.12.31 saves start full). Config `energy`:
`max` 100, `regenPerSecond` 1 (human / recovering only, applied in whole steps every 20 ticks), `transformMinFraction` 0.9,
`baseRegenHpPerSecond` 3, `baseRegenEnergyPerSecond` 2 (a 10-tick step in `TitanShifter#tickBaseRegen`, only while the Titan is below
full health and energy > 0). `grant` fills the bar; `finishToHuman`, `forceEnd`, `revoke` and a join with a stale phase all zero it. The
HUD (`TitanShifterHud`) draws it under the status line with a white tick at 90%.

## Stats (`stats`, `resistances`)

500 HP · armour 25 · toughness 8 · knockback resistance 1.0 · speed attribute 0.45 (ridden ×0.55 ≈ 9 blocks/s measured) · step height 2.5 ·
11 × 3.67 blocks (a player's 0.6 × 1.8 hit-box scaled by 6.11; eye height 0.9 × height) · fall damage ×0.1 · fire ×0.2 · explosions ×0.5 · hits under 4 damage ×0.25. Damage vs players ×0.6; bosses
(max health ≥ 300, Wither, Ender Dragon) lose at most 6% of their max health per hit. The hit-box size is baked at
registration (restart to change).

## Effects

Transformation: a visual-only lightning bolt (no fire/damage), explosion-emitter + flash particles, bystanders shoved back
(no damage), foliage in the footprint cleared, camera shake. Footsteps are event-driven (every 5.5 blocks moved): sound, dust
in the block underfoot's texture, a small tremor for players within 40 blocks. Steam off the shoulders when transforming,
regenerating, hardened, low on health or recently hurt. Weak-block breaking by abilities is **off** by default
(`world.abilityBlockDestruction`); trampling foliage is on.

## Assets

`geo/titan_form.geo.json`, `animations/titan_form.animation.json` (idle, walk, run, punch, kick, heavy_punch, smash, stomp,
leap, landing, roar, hurt, regeneration, hardening, transformation, reversion, death), `textures/entity/titan_form.png` +
`titan_form_hardened.png`, `textures/item/titan_serum.png`. Model, animations and both textures are generated by
`scratchpad/gen_titan_v2.js`: a vanilla-layout player rig (`root > body > head / right_arm / left_arm`, `root > right_leg / left_leg`,
box UVs on the 64×64 skin, second-layer cubes inflated 0.25 / 0.5) drawn at `5.5x` (`TitanType#modelHeight` = 2 blocks); the skin is
copied byte-for-byte out of the `.bbmodel` and the hardened texture is a crystal-blue recolour of it. Animation convention: negative
X swings a hanging limb forward; position keyframes are in model px (×5.5 in game).

## Tests

`TitanShifterGameTests` (server): unlock once, the phase table, transform → real form entity → revert, ability routing +
cooldowns + punch damage, defeat → recovery lockout, forced end / revoke cleanup, refusal with no room / no unlock.
