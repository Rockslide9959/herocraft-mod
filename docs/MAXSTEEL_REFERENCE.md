# Max Steel — Project Hero reference

Added across **v0.6.7 – v0.6.13** (Phases 1–8). Package `com.projecthero.mod.maxsteel` (+ `client.maxsteel`).
Power id `max_steel`. A Hero Tier power, peer of Thor / Tony Stark / Spider-Man: its own attachment,
granted server-authoritatively when Steel bonds, permanent, survives death / relog / restart.

## v0.9.12 changes (at a glance)

- **Fixed a duplication exploit**: a player could shift-click/drag a suit piece out of its armour slot;
  the mod's own "keep the suit worn" logic would synthesise a replacement for the emptied slot, leaving
  the original piece behind as a real, storable item -- repeat to get "infinite netherite-look armour".
  Every synthesised piece is now enchanted with the real `minecraft:binding_curse` on creation via new
  `com.projecthero.mod.armor.PowerEquipmentLock.bind` (confirmed via `javap` on vanilla `ArmorSlot.mayPickup`
  that this is exactly what blocks shift-click/drag/number-key-swap/drop-key removal from an armour
  slot); the glint and the "Curse of Binding" tooltip line are suppressed
  (`ENCHANTMENT_GLINT_OVERRIDE=false`, `HIDE_ADDITIONAL_TOOLTIP`) so the suit still just looks like the
  suit. `MaxSteelSuitArmor` gained `reequipMissing` (fills an empty slot only, never clobbers) and
  `deleteLoose` (strips a stray piece anywhere else on the player), both now run every tick while
  transformed from `MaxSteelAbilityManager.serverTick`; a new `MaxSteel.enforce`, wired into
  `AbilityRouter.serverTick`, does the same cleanup for a non-suited player who somehow has a piece.
  Death already stripped the suit before vanilla's equipment-drop code ran (`MaxSteel.clearTransient` ->
  `MaxSteelSuitArmor.strip`), so Curse of Binding never causes a death-drop. This was prompted by the
  same fix landing on the Spider-Man Symbiote suit (see [[project-spiderman-system]] /
  `docs/SPIDERMAN_REFERENCE.md`), which had the identical problem.

## v0.9.4 changes (at a glance)

- **Turbo Blast bolt held at a constant 4.0 blocks/tick** the entire flight. `TurboBoltEntity.tick`
  re-normalises `deltaMovement` to `BLAST_PROJECTILE_SPEED` each server tick, `getInertia()` → `1.0`,
  and `accelerationPower` is zeroed in both constructors (so the client's own simulation doesn't
  accelerate-from-slow either). An arrow leaves a bow at ~3.0 and only decays, so a flat 4.0 reads as
  unmistakably fast. `BLAST_PROJECTILE_LIFE_TICKS` 22 → 18.
- **Strength Mode's standing Resistance effect removed.** `MaxSteelStrength.tickResistance` /
  `clearResistance` deleted and unwired from `MaxSteelModeRuntime` / `MaxSteelModes`. Strength Mode's
  defence is now just the `+KNOCKBACK_RESISTANCE` attribute and the crouch shield block
  (`STRENGTH_SHIELD_BLOCK` in `MaxSteelDamage`).

## v0.9.3 changes (at a glance)

- **Turbo Blast bolt is much faster.** `BLAST_PROJECTILE_SPEED` 2.2 → **3.5** blocks/tick,
  `TurboBoltEntity.getInertia()` 0.95 → **0.99** (barely any decay), `BLAST_PROJECTILE_LIFE_TICKS`
  40 → **22** (keeps the effective range near `BLAST_RANGE`). Still a visible travelling projectile,
  not a hitscan laser.
- **Turbo Cannon charge bar.** New target-only synced attachment `MAX_STEEL_CANNON_CHARGE` (int ticks),
  written each tick by `MaxSteelCannon` while charging and cleared on release / flight end.
  `MaxSteelHud.renderCannonCharge` draws a horizontal fill bar above the bottom-right ability HUD
  (`y0 - 40`) that climbs over the 5-second (`CANNON_MAX_CHARGE_TICKS` = 100) window and flashes gold
  at full. Lang `hud.projecthero.max_steel.cannon_charge` / `.cannon_full`.

## v0.9.2 changes (at a glance)

- **Base Mode damage resistance removed.** `MaxSteelDamage` no longer applies a flat 25% Base-Mode
  reduction (`BASE_DAMAGE_REDUCTION` kept as `0`). Protection is now the suit's raw armour only.
- **Suit armour is diamond level.** `ModArmorMaterials.MAX_STEEL` → 3/6/8/3 (= 20, full diamond),
  toughness `2.0`, knockback resistance `0.0`, diamond equip sound + repair (was netherite-ish).
- **Passive Regeneration I** while transformed (`MaxSteelPassives.tick`, short refreshed instance).
- **T.U.R.B.O. regen 10% slower**: `OUT_OF_COMBAT_REGEN_PER_SEC` 20 → 18, `COMBAT_REGEN_PER_SEC` 10 → 9.
- **Overload lock-out is energy-gated, not timed.** `MaxSteelEnergy.triggerOverloadLockout` zeroes the
  pool and sets a marker; `isLockedOut` stays true (blocking *every* ability via `MaxSteel.abilityReady`,
  not just modes) until natural regen reaches `OVERLOAD_RECOVER_ENERGY` (150), when `tickRegen` clears
  it. `OVERLOAD_LOCKOUT_TICKS` deleted.
- **Turbo Blast is a real projectile** (`maxsteel/entity/TurboBoltEntity` extends
  `AbstractHurtingProjectile`, type `projecthero:turbo_bolt`, `TurboBoltRenderer` = empty/particle-driven
  like `IronManMissileRenderer`). Thicker cyan trail, ~0.55 hitbox, flies straight, full-charge impact
  burst unchanged. `MaxSteelBlast.fire` spawns it instead of hitscanning.
- **Unarmed melee bonuses**: Base/Speed/Flight stay **+4**; Strength is now **+8** total
  (`STRENGTH_MELEE_BONUS` 10 → 4, additive on the +4 base).
- **Strength Mode**: real **Resistance I** effect (`MaxSteelStrength.tickResistance`, cleared on mode
  exit) — **removed in v0.9.4**; crouch = **50% shield block** (`STRENGTH_SHIELD_BLOCK`, in `MaxSteelDamage`). The old flat 35%
  reduction bonus is gone. **Turbo Slam** on pressing G again in Strength Mode (`MaxSteelStrength.slam`,
  15 dmg / 5 blocks / 10 e / 3 s cd). **Shift+G** returns to Base Turbo Mode.
- **Speed Mode**: **Shift+X** returns to Base Turbo Mode (plain X still dashes).
- **Turbo Cannon**: charge time 1.75 s → **5 s**; instant-cast **12** dmg, **+5 per extra second**
  charged (`CANNON_PER_SECOND_DAMAGE`), cap **37** at 5 s; cost scales **18 → 60** and is capped to
  what the pool can afford so a nuke never fires for free.
- **Flight speed** unified with Thor / the experimental Flight power at `HeroFlight.HERO_FLYING_SPEED`
  (0.06; vanilla doubles to ~0.12 sprinting). `MaxSteelFlight` dropped its bespoke 0.055/0.09 pair.
- **HUD rebuilt like Thor's** (`client/gui/MaxSteelHud`): six keybind boxes bottom-right with cooldown
  shading and an active-mode highlight, T.U.R.B.O. meter + recovery mark beneath, mode name + IN
  COMBAT / READY / OVERLOAD line.

## v0.6.20 changes (at a glance)

- **T.U.R.B.O. pool 500 → 250**, `LOW_ENERGY_WARN` 75 → 50.
- **Turbo Modes no longer time-capped** — `modeEndsAt` is always 0; a mode ends only on re-press,
  mode-switch, power-down, or 0-energy overload. `maxDuration()` and the `*_MAX_DURATION_TICKS`
  constants deleted; `MaxSteelModeRuntime` no longer has a duration branch.
- **Sounds toned down**: suit up/down, mode reconfigure, overload, and Turbo Blast all fire a single
  `NOTE_BLOCK_BIT` beep now (no more layered conduit/trident/warden or block-slam stacks).
- **Flight trail** streams from the feet + both wing tips (`MaxSteelFlightFxClient.emitTrail`/`emitFrom`).

## v0.6.17 changes (at a glance)

- **T.U.R.B.O. pool 100 → 500.** (v0.6.20: → 250.) Regen scaled ×5 (20/s o.o.c, 10/s combat);
  `LOW_ENERGY_WARN` 15 → 75 (v0.6.20: → 50).
  Flat ability costs unchanged, so abilities are cheap relative to the pool now (by design).
- **Turbo Flight**: `MAX_STEEL_FLYING` now feeds `FlightPoseHelper` (superman lean pose); a client
  **cyan energy trail** (`MaxSteelFlightFxClient`) and **blue extended elytra wings**
  (`MaxSteelWingsModel` / `MaxSteelWingsLayer`, vanilla elytra geometry + `elytra.png`, tinted,
  registered via `EntityModelLayerRegistry` + `LivingEntityFeatureRendererRegistrationCallback`).
- **Turbo Blast**: thicker multi-line energy beam + `ELECTRIC_SPARK`/`END_ROD` particles; sound is
  now conduit-zap + trident-thunder (+ sonic boom at full charge). **Charge-up FX**: while Ability 1
  is held suited, `MaxSteelFlightFxClient` converges spark particles on the firing hand, denser with
  hold time (client-only, no packet).
- **Strength Mode**: player is **20% larger** (`Attributes.SCALE` modifier in `MaxSteelAttributes`).
- **HUD**: an `IN COMBAT` / `READY` indicator (right end of the T.U.R.B.O. bar, off `combatUntil`).
- **Controls**: **N = Go Turbo only** (`MaxSteelTransform.goTurbo`); **power down is Shift+H**
  (`MaxSteelActionPayload.POWER_DOWN` → `MaxSteelTransform.powerDown`); plain H still toggles the
  helmet. (v0.6.21: hold-R no longer powers down — hold-R while unsuited still Goes Turbo.)
- **Suit-up into a mode**: a mode key (G/X/Z/V) pressed while unsuited armours up straight into that
  mode — `MaxSteelState.pendingMode`, consumed by `MaxSteelTransform.tick` when the suit-up settles.
- **Emergency totem** (`MaxSteelTransform.tryEmergencyRevive`, hooked in `Project HeroMod` `ALLOW_DEATH`
  before Iron Man's suit recovery): dying **while unsuited** cancels the death, forces the suit
  online, and grants a totem-style effect burst. Costs **150** energy, **20-minute** cooldown
  (`EMERGENCY_REVIVE` key, persisted).

## v0.6.18

- **Power Suppressor now strips Max Steel** (`PowerSuppressorItem` — refused while transformed, like
  the Iron Man suit check; calls `MaxSteel.revoke`). The docs claimed this all along; the code
  never did it.
- Wings hold vanilla's **fully-deployed glide pose** (`MaxSteelWingsModel.render`: `xRot 0.349`,
  `zRot ∓π/2`) instead of the folded near-vertical pose, and render at the model root like vanilla's
  `ElytraLayer` (no `body.translateAndRotate`) so the whole-body flight lean carries them.
- Emergency totem now has its **own guide section** (`projecthero.guide.max_steel.emergency`).

---

## 1. Getting it

Find a **Steel Crash Site** (`projecthero:steel_crash_site`) — a rare above-ground crater with the
**Steel** entity (`projecthero:steel`) floating at its centre. `/locate structure projecthero:steel_crash_site`.

Right-click Steel to bond. Two routes, either one is enough:
- **Experience Level 30+** → consumes **5** levels.
- Carrying a **T.U.R.B.O. Stabilizer** → consumes one, no level requirement.

Recipe (`turbo_stabilizer`): `D C D / R E R / D C D` = 4 diamond, 2 copper block, 2 redstone block,
1 echo shard.

Bonding is single-claim (two players can't bond one Steel), atomic (cost taken before grant), and
plays the ~4 s first-bond suit-up. Steel is indestructible until it bonds and never despawns.

---

## 2. Controls

The six universal HeroPack slots, assigned in spec order to the mod's existing keys:

| Key | Slot | Ability |
|-----|------|---------|
| **R** | 1 | Turbo Blast · hold-unsuited = Go Turbo (hold-suited = charged blast; v0.6.21 removed hold-to-power-down) |
| **G** | 2 | Turbo Strength Mode (toggle) |
| **X** | 3 | Turbo Speed Mode (toggle) · tap again = Turbo Dash |
| **Z** | 4 | Turbo Flight Mode (toggle) |
| **V** | 5 | Turbo Stealth Mode (toggle) |
| **C** | 6 | Turbo Cannon (hold to charge, release to launch) |

Plus (v0.6.16): **N** (`key.projecthero.max_steel_transform`, rebindable) = Go Turbo / power down —
`MaxSteelActionPayload.TRANSFORM_TOGGLE` → `MaxSteelTransform.toggle`. **H** while transformed =
retract / seal the helmet (`MaxSteelFaceplate`, exactly the Iron Man faceplate pattern —
`MAX_STEEL_FACEPLATE_OPEN` synced attachment, `SuperheroArmorRenderer.setHelmetHidden` +
`PlayerModelMixin.helmetRetracted`). H still opens the power wheel when not transformed. Power down
is **N** or **Shift+H** only — v0.6.21 removed the hold-R suit-down gesture (it fought with the
charged Turbo Blast). Hold-R while unsuited still Goes Turbo.

Router priority: Thor → Iron Man → Spider-Man → **Max Steel** → experimental power.

---

## 3. T.U.R.B.O. Energy (`MaxSteelEnergy`)

Pool of **100**, regenerates on its own: **4/s** out of combat, **2/s** in combat (a hit given or
taken, 5 s window), **0** while a specialised mode drains it, while the Cannon charges, or for 1 s
after a high-cost (≥15% of pool) ability. At **0**: hard drop to Base Mode + 4 s specialised-mode
lock-out + harmless spark FX. HUD (`client/gui/MaxSteelHud`): compact cyan bar above the hotbar,
pulses below 15, shows the current mode + a directional threat marker.

---

## 4. Modes (`MaxSteelModes` / `MaxSteelModeRuntime`)

One specialised mode at a time; Base is the fallback. Each switch ends the previous, checks the
lock-out (and the stealth cooldown), pays the activation cost, and plays a reconfiguration flourish.
`MaxSteelModeRuntime` bleeds the per-tick drain and drops to Base on 0 energy (overload).
**v0.6.20: no time cap — a mode stays up until re-pressed, switched, powered down, or the pool runs
dry.** **Model swap**: `MaxSteelArmorItem.armorSetId()` returns `max_steel_<mode>`
while a specialised mode is active — resolved from the wearer via `ArmorRenderContext` — so the geo +
skin swap to the matching form (`geo/max_steel_<mode>.geo.json`, `textures/armor/max_steel_<mode>.png`).

(No Max column — v0.6.20 removed the time caps.)

| Mode | Activate | Drain | Effect |
|------|----------|-------|--------|
| Strength | 12 | 2/s | +10 melee, +40% kb-resist, +50% kb dealt, −20% move speed, −20% attack speed, ×0.65 more dmg reduction; Heavy Punch on sprint+melee (+8, 3-block shockwave, 4 s icd) |
| Speed | 10 | 2.5/s | ×1.10 movement, step assist, 60% less fall dmg; Turbo Dash (6 blocks, 8 dmg, 6 e, 1.5 s cd) |
| Flight | 8 | 1.5 / 2 / 4 per s (hover / move / boost) | vanilla `flying` with tuned speed; `mayfly` always cleaned up; 2 s fall grace on end |
| Stealth | 15 | 2.5/s | hidden INVISIBILITY, hostiles drop their target, suit model hidden; breaks on any offensive action or a >6 raw hit → Base + 12 s cooldown |

---

## 5. Abilities

- **Turbo Blast** (`MaxSteelBlast`) — hitscan, server-authoritative. Tap: 10 dmg, 4 e, 0.45 s cd.
  Hold ≤1.5 s: scales to 22 dmg / 16 e, full charge adds a 2.5-block entity-only burst. Terrain
  never damaged. Breaks Stealth.
- **Turbo Cannon** (`MaxSteelCannon`) — hold ≤1.75 s (braced, slowed), release: the player becomes a
  projectile at 2.6 b/t with Resistance 4 + `IN_FLIGHT`. `MaxSteelDamage` gives full self-immunity.
  `tickFlight` re-asserts speed vs drag, detects wall / entity / 40-tick timeout → impact:
  direct `lerp(18,36)` + AoE `lerp(…,16)` in 4 blocks, strong knockback, no terrain damage. Cost
  `lerp(22,38)`, 8 s cd. Returns to Base after.

---

## 6. Base Mode passives

Attributes (`MaxSteelAttributes`, `PowerToggles` fixed-id transient modifiers): +4 melee, +0.25
knockback resistance, +20% jump, safe-fall +6, ×0.5 fall-damage multiplier. `MaxSteelDamage`: 25%
incoming reduction (cancel-and-re-apply-smaller). `MaxSteelPassives`: breathes underwater while
suited. `MaxSteelSense`: a 4-tick scan for a hostile `Projectile` closing from outside view → a
chime + `MaxSteelWarningPayload` (yaw) → HUD marker. **Not** Spider-Sense — never dodges or catches.

---

## 7. Rendering (`SuperheroArmorRenderer`)

The suit is a `SuperheroArmorItem` armour set. The base model is **`max_steel_slim_suit.geo.json`**
(v0.6.16 — the slimmer fitted model; replaced the earlier bulkier `max_steel_armor.geo.json`), which
like the forms was authored to GeckoLib's armour rig, so it renders through the **existing** renderer
with no changes. `scratchpad/convert_maxsteel_geo.js` did the bone rename/reparent + base/shell
split + inflate (it now also maps the slim model's extra `chin_guard` bone → `armorHead`). `MaxSteelSuitArmor` synthesises the 4
pieces at Go Turbo (real armour worn is pushed to inventory) and deletes them on suit-down / death /
logout / power loss. The pixel-by-pixel reveal (`client/maxsteel/MaxSteelReveal`) is ~28 bone
thresholds in spec order, driven off the synced transform clock, reversed for suit-down; the base
renderer hides the not-yet-revealed bones per frame. `PlayerModelMixin` keeps the player's skin
visible under the forming suit. (The true per-texel shader is not implemented — the mod has no
shader pipeline — this is the spec's sanctioned grouped-bone fallback.)

---

## 8. Lifecycle

`MaxSteel.clearTransient` → `MaxSteelModes.clearAll` (attributes / flight / stealth / speed /
cannon teardown) + `MaxSteelSuitArmor.strip` + `MaxSteelCannon.endFlight` + `MaxSteelAbilityManager.onCleanup`.
Called on JOIN / AFTER_RESPAWN / DISCONNECT / AFTER_PLAYER_CHANGE_WORLD / ALLOW_DEATH. Static scratch
(`ABILITY1_PRESSED`, `ABILITY6_PRESSED`, cannon maps, `SteelCrashAmbience.PENDING`) is registered in
`ServerStateReset`. The power itself is never removed on death; the suit state is (respawn = unsuited
Base).

---

## 9. Admin

```
/maxsteel power grant|revoke [player]
/maxsteel energy <0-100>
/maxsteel transform on|off
/maxsteel spawnsteel
/maxsteel status
```

The Power Suppressor strips Max Steel along with every other power.
