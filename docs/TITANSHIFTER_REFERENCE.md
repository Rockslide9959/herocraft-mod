# Titan Shifter — reference (v0.12.31)

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
| **J** (`key.projecthero.titan_shift`) | transform (human) / revert (Titan) — only sends a request |
| **R** Ability 1 | Titan Punch — 20 dmg, 0.8 s. 3rd swing of a combo = Heavy Punch (35). **Shift+R** = Titan Kick (30, low wide sweep) |
| **G** Ability 2 | Heavy Smash — 1 s charge (slowed), 50 dmg in a 3.5-block area, huge knockback, 8 s |
| **X** Ability 3 | Titan Stomp — 25 dmg, 6 blocks, 6 s |
| **Z** Ability 4 | Titan Leap — ~3× jump (measured ~8 blocks up, ~20 forward), landing 20 dmg in 5 blocks, 5 s |
| **V** Ability 5 | Titan Roar — 12 blocks, Slowness III + Weakness II 10 s, mobs thrown back and flee; bosses take 25% duration and no knockback; 15 s |
| **C** Ability 6 | Titan Regeneration — 10 HP/s for 10 s, 45 s |
| **H** Utility 1 | Titan Hardening — 60% less damage 8 s, crystal texture, 30 s (`controls.slot6IsHardening` swaps C and H) |

## Stats (`stats`, `resistances`)

500 HP · armour 25 · toughness 8 · knockback resistance 1.0 · speed attribute 0.45 (ridden ×0.55 ≈ 9 blocks/s measured) · step height 2.5 ·
11 × 4 blocks · fall damage ×0.1 · fire ×0.2 · explosions ×0.5 · hits under 4 damage ×0.25. Damage vs players ×0.6; bosses
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
`titan_form_hardened.png`, `textures/item/titan_serum.png`. All generated by `scratchpad/gen_titan.js` (original design,
hand-rolled PNG writer; the model is packed into a 256×128 atlas at 3 model-pixels per texel). Animation convention:
negative X swings a hanging limb forward.

## Tests

`TitanShifterGameTests` (server): unlock once, the phase table, transform → real form entity → revert, ability routing +
cooldowns + punch damage, defeat → recovery lockout, forced end / revoke cleanup, refusal with no room / no unlock.
