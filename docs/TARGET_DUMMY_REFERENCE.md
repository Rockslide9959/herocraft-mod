# Target Dummy (v0.17.5) — reference

User ask: "my own target dummy that I can use for testing, place with right click, only picked up by shift right
clicking it again, infinite hp, shows damage stats like the Target Dummy mod".

Code: `com.projecthero.mod.targetdummy` (entity, item, labels, payload), `com.projecthero.mod.client.targetdummy`
(model, renderer, client stats). Tests: `src/gametest/.../TargetDummyGameTests`. Assets/data:
`scratchpad/gen_target_dummy_v0175.js` (entity texture, icon, item model, recipe, unlock); lang:
`scratchpad/lang_target_dummy_v0175.js` (key by key). Films/screens: `scratchpad/TargetDummyHarness.v0175.java.txt` +
`scratchpad/target_dummy_v0175_films.js` -> `docs/screenshots/v0175/target_dummy/`.

## Item and controls

| Action | Result |
|---|---|
| Right-click a block top/side with the item | places the dummy facing you (22.5° steps); refused inside blocks or another dummy |
| Hit it (anything) | measured and recorded; never dies, never moves |
| Right-click with an armour piece | equips it (the old piece comes back to you) |
| Right-click empty-handed on a worn piece | takes that piece off (head ≥1.5, chest ≥0.85, legs ≥0.4, else feet) |
| Right-click empty-handed where it wears nothing | toggles **hit cooldown** (action-bar message), clears stats |
| Sneak + right-click (any hand item) | picks it up: item keeps `custom_name`, armour in `container` (feet, legs, chest, head), `custom_data` HitCooldown |
| `/kill`, the void | removed, no drop |

Recipe: ` W / SHS / F ` — W any wool, S stick, H hay bale, F any wooden fence. Unlocks on picking up a hay bale.
Superheroes creative tab. Guidebook: Devices chapter, "Target Dummy" section. JEI info page.

## Entity rules

- `LivingEntity` + `Enemy` (not a `Mob`): no AI, no despawn. `HeroTargets.canHarm` and `isHostile` both accept it
  (auto-targeting powers, summons and pets will attack it too). Vanilla golems hunt `Mob`s only, so they ignore it;
  `BossTargets.isVictim` rejects every `Enemy`, so boss threat tables and boss AoE ignore it.
  `AbilityHelpers.isValidGrabTarget` refuses it (grabs can't carry it off).
- Max health 20 (percent-of-health effects behave as against a player). `actuallyHurt` measures raw → after armour
  (vanilla `CombatRules`, **no armour durability loss**) → after `getDamageAfterMagicAbsorb` (Resistance, Protection),
  records it, never lowers health. `setHealth` lower than current is recorded as "True damage" (kind DIRECT) and undone.
- Ignored: fall, in-wall, cramming, drown, starve, fly-into-wall. Fire, lava, wither, poison, explosions all count.
- Knockback, pushes, launches and riding are refused (`setDeltaMovement` keeps only downward Y).
- **Hit cooldown off (default):** `invulnerableTime = 0` after each hit, so rapid multi-hit powers all register.
  Per-tick channels (flamethrower-style beams) that rely on the 10-tick window to throttle themselves will show more
  DPS here than they deal to mobs. **On:** vanilla window, mob-accurate.
- Fabric `AFTER_DAMAGE` is not fired for dummy hits (no Willpower XP / lifesteal farming off an immortal target);
  `hurt` still returns true, so on-hit meters that read that still charge.
- Stats are instance fields; nothing saved; a combat ends 100 ticks after the last hit (next hit starts a new one).

## Labels and colours

`TargetDummyLabels.describe`: melee swing (marked by `AttackEntityCallback`, same tick, `player_attack`) → held item
name or "Fist", crit by vanilla's rule; projectile/other direct entity → its name; player-caused power-type source
within 60 ticks of an ability-key press (the universal `AbilityInputPayload`) → "R ability" / "Shift+R ability";
otherwise `projecthero.target_dummy.source.<msgId>` with a tidied-msgId fallback ("magic" with Poison on → "Poison").
Powers with their own packets (Iron Man, Spider-Man...) show the damage type, not the key.

| Kind | Colour |
|---|---|
| Physical | white `F2F2F2` |
| Projectile | green `8EE36B` |
| Magic | purple `C58CFF` |
| Fire | orange `FF8C2E` |
| Explosion | red `FF4B4B` |
| Lightning | cyan `7FE9FF` |
| Freeze | ice `B9F0FF` |
| Power | blue `52A8FF` |
| True damage | pink `FF5FD2` |
| Crit | gold `FFD23C`, bold, bigger, "!" |

## Client

`DummyHitPayload` (S2C, tracking players) carries the hit + combat totals. `DummyClientStats` lives on the entity
(`clientData`): floating numbers (26-tick life, max 40, nudged toward the camera), wobble (damped sine, amplitude
5 + 1.6×damage up to 30°, leaning along the hit direction, pivoting on the planted base plate), readout. Readout shows
in combat, for 5 s after (fading over the last second) or while looked at: combat time, DPS (live during combat,
first-to-last hit after, min 1 s), total, hits, biggest, last hit + label (clipped to 96 px), raw vs through + total
armour % when mitigation happened, "Hit cooldown on".
