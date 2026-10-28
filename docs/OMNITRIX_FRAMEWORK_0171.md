# Omnitrix framework (v0.17.1)

Ben 10's Omnitrix as a Hero-Tier power (`omnitrix`), the way to get it (a meteor / the Meteor Scanner), the only way to
lose it (the Omnitrix Containment Pod), and a reusable alien framework. Heatblast and Four Arms ship with it; the other
eight of the original ten (XLR8, Diamondhead, Upgrade, Ghostfreak, Stinkfly, Wildvine, Ripjaws, Grey Matter) are on the
dial as "coming soon" until their kits register.

Rules that hold: H = power selector only, N unused, no H / N HUD boxes. X = movement, V = the Omnitrix, R G Z C = attacks.

## Where things are

| Piece | Server (main) | Client |
|---|---|---|
| The power, the lock, transform / revert / timeout, the watch up (select, cycle, slap) | `omnitrix/Omnitrix.java` | |
| State (one synced, persistent, copy-on-death attachment) | `omnitrix/data/OmnitrixState.java`, `OmnitrixAttachments.java` | |
| Keys (V + the kit's R G X Z C) | `omnitrix/OmnitrixAbilityManager.java` (from `AbilityRouter`) | scroll / clicks: `client/omnitrix/OmnitrixClient.java`, `mixin/OmnitrixInputMixin`, `mixin/OmnitrixScrollMixin` |
| The alien body (attributes, health by fraction) | `omnitrix/OmnitrixBody.java` | skin swap, wide arms, no armour / cape: `mixin/OmnitrixPlayerRendererMixin`, `OmnitrixHideGearMixin`, `OmnitrixCapeMixin` |
| Alien registry and kit API | `omnitrix/alien/Aliens.java`, `AlienKit.java`, `AlienStats.java`, `AlienMove.java` | `client/omnitrix/AlienClients.java`, `AlienClientKit.java`, `AlienParts.java`, `FirstPersonClip.java` |
| Shared projectile | `omnitrix/entity/AlienProjectileEntity.java` | `OmnitrixRenderers.Projectile` |
| Damage types + the kit damage hooks | `omnitrix/OmnitrixDamage.java`, `data/projecthero/damage_type/*.json` | |
| Sounds | `omnitrix/OmnitrixSounds.java`, `sounds.json` (`omnitrix_*`, `heatblast_*`, `four_arms_*`) | `assets/projecthero/sounds/omnitrix/*.ogg` |
| The watch model, poses, first person, HUD | | `OmnitrixWatch`, `OmnitrixPose` (+ `mixin/OmnitrixPoseMixin`), `OmnitrixFirstPerson` (+ `mixin/OmnitrixItemInHandMixin`), `OmnitrixHud`, `OmnitrixLayer` |
| Meteor, crater, the fallen pod, the Meteor Scanner | `OmnitrixMeteors`, `entity/OmnitrixMeteorEntity` (built on the Carnage meteor), `entity/OmnitrixPodEntity`, `item/OmnitrixItems` | `OmnitrixRenderers.Meteor/Pod` |
| The Containment Pod | `pod/OmnitrixPodBlock`, `pod/OmnitrixPodBlockEntity`, `OmnitrixConfig.EXTRACTION_PULSES` | `OmnitrixRenderers.PodField` |
| Unique powers for a loose watch | `OmnitrixWorld` | |
| Kits shipped | `alien/heatblast/HeatblastKit`, `alien/fourarms/FourArmsKit` | `client/omnitrix/alien/HeatblastClient`, `FourArmsClient` |
| Tests | `src/gametest/.../OmnitrixGameTests.java` | |
| Generators | `scratchpad/gen_omnitrix_textures.js` (skins, watch, pod, items, dial silhouettes), `gen_omnitrix_data.js` (recipes, tags, models), `gen_omnitrix_lang.js` (the one lang block), `synth_omnitrix_sounds.js` | |

## The watch (Alien Evo style, user: "no dial screen")

* **V as a human** raises the wrist (first person: the forearm comes in from the left, the chunky watch's face toward the
  eye; third person: the forearm comes up level in front of the chest with the face up, the head bows to it and the other
  hand grips the dial). The core pops up (`omnitrix_core_up`), the face dims and shows the selected alien's silhouette
  glowing on it, a faint copy hovering just over the face (the big third-person hologram was removed in the polish pass).
* **The scroll wheel** (while raised) turns the dial to the next alien that has a kit (aliens without one are skipped);
  the hotbar does not move. Each step clicks, the core twists a notch, the name shows on the action bar and in the HUD's title row.
* **A left- or right-click** slaps the core down (`Omnitrix.slap`; the click never attacks, uses or mines) and
  `Omnitrix.SLAP_TICKS` later the green flash and Kirby dots burst (`OmnitrixTransformFx`) and the alien takes over.
* **V again** lowers the watch: no cost, no cooldown. **V while transformed** reverts early (10 s recharge); Shift+V is
  the same key. **V during a recharge** flashes the face red with the time left and does not raise the watch.
* The selected alien persists. A transformation lasts 3 minutes (the watch light flashes red and beeps for the last 15 s),
  then the timeout chime, the red flash and Kirby dots, and a 90 s recharge.

Server entry points (tests and kits use them): `Omnitrix.activate / deactivate / cycle(p, dir) / slap / transform /
revert(p, Reason) / select(p, id)`; `OmnitrixAbilityManager.pressV(p)`. Client to server: `OmnitrixActionPayload`
`CYCLE` ("1" / "-1"), `SLAP`, `SELECT` (an id), `USE_AIR`.

## Adding an alien (the other eight)

1. **Server kit** -- `src/main/java/com/projecthero/mod/omnitrix/alien/<alien>/<Alien>Kit.java`:

```java
public final class XlrKit extends AlienKit {
    public static void register() { Aliens.register(new XlrKit()); }       // id() must be a declared Aliens id
    @Override public String id() { return Aliens.XLR8.id(); }
    @Override public AlienStats stats() {
        return AlienStats.builder().health(22).speed(1.9f).scale(0.95f).armor(2, 0).step(0.6f).build();
    }
    @Override protected List<AlienMove> declareMoves() {
        return List.of(AlienMove.tap("dash_strike", AbilitySlot.SLOT_1, 20), AlienMove.shift("tornado", AbilitySlot.SLOT_1, 200),
                AlienMove.tap("sprint", AbilitySlot.SLOT_3, 10) /* ... */, AlienMove.vanilla("fast_punch", "Attack", 0));
    }
    @Override public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
        if (slot == AbilitySlot.SLOT_1 && !shift && use(p, "dash_strike")) {      // use(): cooldown gate + "not ready" line
            anim(p, ANIM_DASH);                                                // synced pose id (1..999) for the client kit
            for (LivingEntity e : around(p, p.position(), 3)) hurt(p, e, 6f, false);
        }
    }
}
```

   Then one line in `OmnitrixMod.initialize` next to Heatblast's and Four Arms'. Hooks you can override:
   `onTransform`, `onRevert` (stop running moves, give back flight), `tick` (every tick while transformed), `holdable(slot)`
   + `holdStart` / `holdEnd` (hold moves; a tap then fires on the release), `release`, the vanilla keys `onAttack`
   (return true to replace the punch), `onUse` (right-click on air, either hand), `onUseBlock`, `onUseEntity`, and the
   damage hooks `allowDamage` (immunities) / `incomingDamage` (resistances; re-issued behind a guard).
   Helpers: `use` / `ready` / `cooldown` / `cooldownLeft`, `anim`, `flag` / `setFlag` (synced kit bits), `timer` /
   `setTimer` (kit timers in the synced state), `lookTarget`, `around` (never the wearer or squad allies), `hurt` (the
   `alien_strike` / `alien_fire` damage types: player attacks, so boss threat tables count them; for no-damage moves use
   `QuirkThreat.add`), `shove`.
   Projectiles: `AlienProjectileEntity.of(p, "xlr8_spark").colour(..).size(..).damage(..).speed(..).spawn()`, optional
   `.blast(radius, dmg)`, `.gravity(g)`, `.fire(seconds)`, and per-kind extra behaviour with `AlienProjectileEntity.onHit`.
   Static per-player maps in a kit: clear them in `onRevert` (called on timeout, Shift+V, death, logout, the pod) and in a
   `clearSessionState` called from `OmnitrixMod.clearSessionState` (the `ServerStateReset` hook).

2. **Client kit** -- `src/client/java/com/projecthero/mod/client/omnitrix/alien/<Alien>Client.java implements AlienClientKit`,
   one line in `OmnitrixClient.initialize`:
   * `skin()` -- a 64x64 WIDE player skin (the framework always uses the wide arm model for aliens). Generate it with a
     script like `scratchpad/gen_omnitrix_textures.js` (`SKIN` boxes, `paintBox`).
   * `renderExtras` -- extra cubes on the posed bones: `AlienParts.bake(64, 64, root -> ...)` for ModelParts (UVs on the
     skin itself, as Four Arms' second pair of arms does, or an extras sheet), `AlienParts.on(pose, model.body, ...)`, and
     `AlienParts.box(...)` for quick textured / emissive cubes (Heatblast's flame tongues). Grey Matter: just `scale(0.3f)`
     in the stats; Stinkfly's wings, Ripjaws' tail, XLR8's visor and tail, Wildvine's vines, Diamondhead's crystal head:
     a few boxes on `body` / `head`. Ghostfreak floats: `gravity(0.25f)` in the stats and the legs posed together.
   * `pose(p, model, animId, ticks, age)` -- third-person keyframes, usually `QuirkKeyframes.play(m, FRAMES, ticks, 1f)`
     (16-column `QuirkKeyframes.f(...)` frames, radians).
   * `firstPerson(animId)` -- a `FirstPersonClip` (`f(tick, pitch, yaw, roll, x, y, z)` frames; `.both()` for two hands).
   * `renderFirstPersonArm` -- extra geometry on the bare first-person arm (Four Arms draws his lower arm there).
   * `tick(p)` -- ambient particles for everyone who can see the alien. `badge()` -- where the Omnitrix sits on the chest.
3. **Lang** (in the Omnitrix block: `scratchpad/gen_omnitrix_lang.js`): `projecthero.omnitrix.<alien>.<move>` and `.desc`
   for every move, `projecthero.guide.omnitrix.alien.<alien>` (the guide's line about it). The guide chapter, the HUD key
   boxes and the "Your Power" page are built from the registry and `moves()`; nothing else to edit.
4. **Sounds**: a constant in the kit's own class (`SoundEvent.createVariableRangeEvent`), one `sounds.json` block, a
   subtitle line, mono OGGs from a `scratchpad/synth_*` script (Medal's `ffmpeg7.exe` has libvorbis).
5. **Tests**: copy the Heatblast / Four Arms blocks of `OmnitrixGameTests` (body, immunities, a move's damage + cooldown).

## The lock

`Omnitrix.rejects(player, key)` answers "The Omnitrix rejects it." for any power but `omnitrix` while it is worn. It is
asked by `UniquePowers.refuse` (every hero's grant routine and acquisition item), `PowerGrants.grantHero` (the admin grant,
the heroic / prismatic serums), `PowerGrants.grantExperimental` and `ExperimentalPowers.grant` (mutation serums, the
stack command), `MutationManager.attemptExposure` (before `claimMutation` could strip it), the random serum item (the
bottle is kept), the power wheel's `PowerSelectPayload`, and `HeroCommand` grant / stack without `force`. An operator's
`force` (`UniquePowers.forced`) is the one override. It survives death (`copyOnDeath`); a death while transformed turns the
wearer back. Serums never roll it (`PowerGrants.NOT_FROM_SERUMS`).

## Getting it

* **The natural meteor**: once a minute each surface player in the Overworld rolls 1/3000 (about once in 50 hours; never
  Peaceful, creative or spectator), with a 2-hour lull after any Omnitrix meteor. The meteor is the Carnage meteor's fall
  (3 s, slanted, batched forced trail) in green and white; it digs a radius-4 bowl lined with blackstone, basalt and magma,
  some fire on the rim, and leaves `OmnitrixPodEntity` at the bottom. 3 s later the pod hisses open; the first player to
  touch it (walk into it or right-click it) gets the watch.
* **The Meteor Scanner** (`IDI / RCR / IAI`: iron, a diamond, redstone, a compass, an amethyst shard): drops the meteor
  within 50 blocks on open, dry, sky-lit ground; consumed; refuses while an Omnitrix meteor, or a pod still holding its watch, is within 160.
* **Unique powers**: a falling meteor, a pod in its crater and a popped-off Omnitrix item count as a pseudo holder
  ("a loose Omnitrix", `OmnitrixWorld.LOOSE`) in the unique-powers table. With the gamerule on no new meteor falls and
  nobody gains the power another way while one exists; whoever puts THAT watch on clears the entry first.
  `/projecthero uniquepowers release omnitrix` clears it (e.g. a lost item).

## The Containment Pod (removal)

Recipes: **Plumber Alloy** `ECE / NSN / ECE` (echo shard, crying obsidian, netherite ingot, nether star) -> **Containment
Core** `RER / AHA / RER` (redstone block, eye of ender, Plumber Alloy, heart of the sea) -> **Omnitrix Containment Pod**
`TTT / ICI / OLO` (tinted glass, iron block, the core, crying obsidian, lodestone). A two-block-tall machine (only the base
plate is solid). Fuel: Redstone Block 1, Echo Shard 2; an extraction burns 8 (max 32).

Stand inside as a human, right-click with an empty hand: 40 s. Damage is `projecthero:omnitrix_extraction` (in
`bypasses_armor`, `bypasses_enchantments`, `bypasses_effects`, `bypasses_resistance`, `bypasses_invulnerability` -- so a
totem never pops -- and `no_knockback`; Absorption still soaks it and healing works). The schedule (ticks since the start):

| Tick | Damage |
|---|---|
| 20 | 24 (strips Absorption IV's 16 and 8 HP) |
| 40, 60, ..., 600 (29 pulses) | 0.5 each |
| 620, 640, ..., 780 (9 pulses) | 0.6 each |
| 800 | 3.1 |
| **total** | **47** |

Calibration (user: "enough damage to bring a player that just ate an enchanted golden apple to 2 hearts"): Regeneration II
heals 1 HP whenever its remaining duration is a multiple of 25 ticks (vanilla `50 >> 1`): at 400, 375, ..., 25 = 16 heals.
The one at 400 lands the tick after eating at full health (wasted); the other 15 land below full because the first pulse
already took 8 HP. 20 + 16 + 15 - 47 = **4 HP**. Health peaks at 18.5 (never full again), so no heal is wasted. Measured in
`OmnitrixGameTests.extractionLeavesAGoldenAppleEaterOnTwoHearts`: **4.0 HP**. Starting later than 5 ticks after eating
loses one heal per 25 ticks. A player without the apple dies at the first pulse; the watch stays on.

The Omnitrix fights back: forced transforms (a 24-tick flicker into a playable alien -- shown only, the body never changes,
so the calibration holds), energy surges every 3.5-6.5 s (an upward jolt, camera shake; no damage), and the red
SELF-DESTRUCT box + red screen edges on the wearer's HUD. Stepping out (more than 0.8 from the centre) fails it and the field
lashes out for 6; dying fails it. Success: the watch pops off as an item at the top of the pod and the power is cleared.

## Heatblast and Four Arms

| | Heatblast | Four Arms |
|---|---|---|
| Body | 26 HP, speed x1.05, armour 4, fist +2, KB res 0.1 | 40 HP, speed x0.85, size x1.4, armour 8 (+2 toughness), KB res 0.6, fist +6, step +0.6 |
| Passive | immune to fire, lava, magma; water fizzles his fire | no fall damage from his own leaps / dives; what he holds cannot hurt him |
| R / Shift+R | Fire Blast (6 + alight) / Fire Stream (2.5 s cone) | Haymaker (12, launch) / Quad Barrage (4 x 5) |
| G / Shift+G | Pyro Bomb (3.5 r, 8) / Heat Shield (5 s, -60%, burns attackers, melts arrows) | Thunder Clap (cone 9, 8, slow) / Grab (G again throws: 12) |
| X / Shift+X | Fire Jet (flight toggle) / Jet Dash | Super Leap (landing 6) / Charge (7) |
| Z / Shift+Z | Flame Ring (r 4, 4 s) / Flare (blind + singe, r 8) | Ground Pound (r 6, up to 10) / Meteor Drop (8-18) |
| C / Shift+C | Fireball Nova (r 6, 10) / Supernova (1.25 s charge, r 9, 18) | War Cry (weaken / slow r 10, Strength) / Iron Stance (4 s, -70%, no knockback) |
| Attack / Use | flaming punch (+2 fire) / fire bolt (empty hand), melt a block, Shift+Use smelts 8 of the held item | heavy punch (shove) / grab a creature, throw what you hold |

## Stinkfly, Wildvine, Ripjaws and Grey Matter (the second batch)

Kits: `alien/stinkfly/StinkflyKit`, `alien/wildvine/WildvineKit`, `alien/ripjaws/RipjawsKit`, `alien/greymatter/GreyMatterKit`;
clients `client/omnitrix/alien/StinkflyClient`, `WildvineClient`, `RipjawsClient`, `GreyMatterClient`. "Very basic models
for now" (user): the player body with a generated skin plus `AlienParts.box` cubes from an `<alien>_extras.png` sheet; a kit
that replaces a limb hides it in `pose()` (`visible = false` -- `PlayerRenderer.setModelProperties` makes everything visible
again before the next player renders) and redraws it in `renderExtras` on the same bone.

| | Stinkfly | Wildvine | Ripjaws | Grey Matter |
|---|---|---|---|---|
| Body | 14 HP, x1.1 speed, x0.9 size, armour 1, fist +1 | 28 HP, x1.1 size, armour 3, fist +2, KB res 0.2, reach +1.5 | 30 HP, x0.9 speed, x1.05 size, armour 4 (+1), fist +3, KB res 0.2 | 8 HP, x1.35 speed, **x0.33 size** (0.59-block hitbox, eyes at 0.53), safe fall +1 |
| Passive | real flight (mayfly while transformed; X or double-tap jump), no fall / fly-into-wall damage | fire x1.35; sneak still 0.75 s = a bush (mobs hunting him lose track every tick) | water: Dolphin's Grace + Conduit Power, never drowns, moves x1.5, fist +3, swim / underwater dig modifiers; land: the dry-out meter | mobs hunting him lose sight (30% per second beyond 2.5 blocks; never bosses) |
| R / Shift+R | Slime Shot (3, roots 1.5 s) / Slime Net (r 2.75, roots 3 s) | Seed Bomb (r 3, 7, no block damage) / Thorn Whip (cone, 6) | Bite (7, heals 1) / Jaw Lunge (first in the way: 10) | Nerve Pinch (stun 3 s) / Weak-Spot Strike (12) |
| G / Shift+G | Toxic Gas (5 s cloud, 1.5 / 0.5 s + poison, nausea) / Stinger Stab (6 + Poison II) | Vine Bind (18 r, roots 4 s, 2) / Pollen Burst (r 5: nausea, slow, weak, 2) | Tail Slam (r 3.5, r 5 in water, 8) / Fin Slash (r 3.25, 6, slow) | Analyze (24 r: health / armour / weakness in chat; Glowing; **+35% damage from everyone** 10 s) / Decoy (5 s, mobs go to it) |
| X / Shift+X | Wing Flight (take off / land) / Wing Dash | Vine Grapple (28 r, reel in; X again lets go) / Stretch Climb (a wall's top, 12 max) | Water Dash (land: hop) / Breach (land: back-flip) | Big Leap (no fall damage) / Scurry (5 s +60% speed, hunters lose you) |
| Z / Shift+Z | Dive Bomb (hop, plunge, r 3.5 splash up to 8) / Wing Gust (cone: 2, shove, deflects projectiles) | Root Wall (8 s barrier: pushes creatures, stops shots) / Regrowth (10 s, you + squad: 2 HP/s in sun, 0.5 in shade) | Whirlpool (water: 5 s vortex r 6) / Mud Splash (land: cone, 3, blind, slow) | Snare Trap (roots 4 s, 3 max, 60 s) / Smoke Bomb (blind r 5, everyone loses track) |
| C / Shift+C | Goo Storm (6 s, r 6: slow + 1.5 / s) / Slime Barrage (5 x 2.5) | Overgrowth (8 s field r 7: roots, 1 / s) / Root Eruption (line of 6 root spikes, 7, launch) | Feeding Frenzy (10 s: +30%, +3 fist, +20% speed, bites heal 3) / Drench (half the meter back) | Genius Repair (anvils, cracked bricks; furnace / brewing stand +10 s of work; else 30% durability of held gear; nothing to fix = no cooldown) / Mind Map (ores, chests, spawners within 20 through walls, 8 s) |
| Attack / Use | poison jab / goo spit (empty hand) | vine lash (reach) / bonemeal touch (empty hand on a block) | jaw punch / dig sand, gravel, dirt, clay, mud, snow (one block per use) | precision jab (+2 on a marked target) / quick read (use on a creature) |

**Ripjaws' dry-out** (comic-accurate, tuned to be usable): `MAX_DRY` = 2400 ticks (2 minutes) of dry land are free; at 75% the
action bar warns, at 100% he chokes for 1 HP every 2 s (`dryOut` damage, short hidden Slowness / Weakness). Water refills 8x as
fast as it drains (empty to full in 15 s, choke effects cleared on contact), rain stops the drain and refills 2x, Drench gives
half back. Stored as `ripjaws.dry_base` / `ripjaws.dry_since` kit timers + the `F_WET` / `F_RAIN` flags, written only when the
rate changes, so the HUD meter (`RipjawsKit.dryness(player, now)`) needs no packet per tick. `inWater` also reads the fluid at the
feet / eyes (holds for an unticked mock player).

**Grey Matter's size**: `scale(0.33)` only. Vanilla refreshes the hitbox and the eye height on the next `LivingEntity.tick`
(`getScale() != appliedScale`), both ways; tests call `refreshDimensions()` for an unticked mock. The big head is the skin's head
re-baked and drawn x1.55 on the head bone, with bulging eye cubes.

**Framework extensions** (backwards-compatible): `AlienClientKit.hud(g, player, x, y, w, partial)` -- a default no-op, called by
`OmnitrixHud` for the transformed wearer with the spot above the alien's name (Ripjaws' hydration meter, drawn with
`OmnitrixHud.meter` so it matches the time bar); `alien/AlienStatus` --
`root` (Slowness X, a quarter as long on bosses, a QuirkThreat credit), `stun`, `loseTrack` (never a boss), `isBoss`, and
`sound(path)` for kit-owned SoundEvent constants.

Static per-player state (cleared in `onRevert` and `clearSessionState`): Stinkfly gas clouds / goo storms, Wildvine overgrowth
fields / root walls / last positions (the bush's "standing still"), Ripjaws whirlpools, Grey Matter snares / decoys / marks
(a mark is keyed by the creature, dropped with its owner's revert, expired lazily). Generators: `scratchpad/
gen_omnitrix_aliens_b_textures.js` (skins + extras), `synth_omnitrix_aliens_b_sounds.js` (24 mono OGGs), `gen_omnitrix_aliens_b_lang.js`
(their own contiguous lang block at the END of `en_us.json` and sounds block at the end of `sounds.json`, kept apart from
`gen_omnitrix_lang.js`'s block). Tests: `OmnitrixAliens2GameTests`. Harness: `scratchpad/OmnitrixAliens2Harness.v0171.java.txt`.

## Info pages: the Left-Alt list, the I screen, the guide

* **Left-Alt** (`OmnitrixHud.moveRows`, drawn with the shared `MoveListOverlay` above the bottom-right HUD). Human: Scroll and Click
  while the watch is up, the selected alien, V (raise / lower, the recharge left) last. Alien: every kit key's tap / Shift / hold
  move from `moves()` on the live bindings, the Attack / Use / Sneak moves (grouped by the first word of `vanillaKey`), V = turn
  back with the time left. Never H / N. A kit whose `AlienClientKit.hud` draws over the name says how tall in `hudHeight()`.
* **`omnitrix/OmnitrixInfo`** builds every line of reference text (client-safe; the caller wraps): `status` (live), `controls`,
  `rules` (from `OmnitrixConfig`), `containment`, `roster` (all ten: role, the five tap moves, the core stats) and `alienPage`
  (role + the guide line, the body from `AlienStats`, passives, weaknesses, every move per key with cooldown and description).
  The I screen (`PowerInfoScreen.omnitrixBody`, rebuilt every 10 ticks) shows the alien's page while transformed, the watch +
  roster as a human; the guide chapter calls the same builder with `OmnitrixInfo.LETTERS`.
* Lang: `scratchpad/gen_omnitrix_info_lang.js` -- one block right after the main Omnitrix block (`_comment_omnitrix_info_*`):
  the labels, and per alien `projecthero.omnitrix.info.<id>.role`, `.passive.<n>`, `.weakness.<n>`. A new alien needs those
  three; its moves' names and `.desc` already come from step 3 above. Test: `OmnitrixInfoGameTests`. Harness:
  `scratchpad/OmnitrixInfoHarness.v0171.java.txt`; shots `docs/screenshots/v0171/omnitrix_info_*.png`.

## State and resets

Static per-player state: `Omnitrix` (the "rejects" throttle), `OmnitrixAbilityManager` (keys down), `HeatblastKit`
(Flame Ring centre, Jet Dash hits), `FourArmsKit` (Charge hits, thrown creatures), `OmnitrixMeteors` (the natural cooldown
per dimension). All cleared by `ServerStateReset -> OmnitrixMod.clearSessionState`, and per player on logout / death /
dimension change (`Omnitrix.onLogout / onDeath / onDimensionChange`) and on revert (`AlienKit.onRevert`). A logout turns a
transformed wearer back (attribute modifiers are transient). The extraction attachment is never saved: a relog aborts it.

## Wave A: XLR8, Diamondhead, Upgrade, Ghostfreak

Kits: `alien/xlr8/Xlr8Kit`, `alien/diamondhead/DiamondheadKit` (+ `DiamondheadBlocks`), `alien/upgrade/UpgradeKit` (+ `UpgradeEmp`),
`alien/ghostfreak/GhostfreakKit`; clients `client/omnitrix/alien/Xlr8Client`, `DiamondheadClient`, `UpgradeClient`, `GhostfreakClient`.
Generators: `scratchpad/gen_omnitrix_wave_a_textures.js` (skins, extras sheets, `upgrade_glow.png`, the crystal block),
`synth_omnitrix_wave_a_sounds.js` (19 OGGs: `xlr8_*`, `diamondhead_*`, `upgrade_*`, `ghostfreak_*`), the `WAVE_A` block in
`gen_omnitrix_lang.js`. Tests: `OmnitrixWaveAGameTests` (10). Sounds are constants in each kit's own class.

| | XLR8 | Diamondhead | Upgrade | Ghostfreak |
|---|---|---|---|---|
| Body | 22 HP, speed x1.9 (+50% more sprinting), size 0.95, armour 2, fist +1, attack speed +2, step +0.65 | 34 HP, speed x0.88, size 1.1, armour 12 (+4), KB res 0.5, fist +4 | 28 HP, speed x1.05, armour 6, KB res 0.2, fist +3 | 24 HP, armour 2, fist +2, gravity x0.25 (drifts down), no fall damage to speak of |
| Passive | Shift+X: wall run (climb while pressed into a wall) + water run | reflects 40% of projectiles (an entity tag marks each judged once); Crystal Armour: all | merged: half damage (a golem takes the hit instead); no suffocation while sliding | half damage from physical blows (`GhostfreakKit.physical`), never drowns, sun burns 1 every 2 s |
| R / Shift+R | Dash Flurry (6 x 2) / Tornado Run (3 s twister, 4 at the end) | Shard Volley (5 x 3) / Crystal Spear (12) | Optic Beam (6) / Charged Beam (1 s, pierces 14) | Spectral Claw (7, wither) / Scream (3, fear 5 s) |
| G / Shift+G | Sonic Punch (close in, 8) / Snatch (the mob's held item) | Blade Arm (toggle +6, +1 reach) / Spike Line (12 blocks, 6) | Tech Grab (stun 3 s) / Circuit Lash (pull, 4) | Possess (10 s: the mob fights for you) / Soul Siphon (6, heals 3) |
| X / Shift+X | Blur Dash (afterimages, 3) / Wall Run toggle | Crystal Slide (path / ramp) / Crystal Vault (pillar) | Liquid Slide (scale 0.45 = 0.81 tall, 2 s) / Stretch Grapple (22) | Phase (5 s, through walls, then 10 s cooldown) / Ghost Flight toggle |
| Z / Shift+Z | Whirlwind (r 6, 4) / Kick Barrage (8 x 1.5) | Crystal Wall (5x3, 8 s) / Crystal Dome (r 3, 7 s) | Overload (golem offline / block: redstone jammed 5 s) / EMP Burst (r 8, redstone r 6 for 6 s) | Fade (invisible 10 s) / Shadow Tendrils (3 s, r 6) |
| C / Shift+C | Time Burst (r 12 slowness V 5 s) / Speed Mine (1x2 tunnel, 16) | Diamond Barrage (2 s shard storm) / Crystal Armour (6 s, -50%) | Merge (furnace x4, dispenser rapid fire, piston hurl; pilot golem / minecart / boat, unseen, 20 s) / Upgrade Tool (repair half, else +1 Unbreaking) | Fright Aura (10 s panic r 7) / Haunt (darkness + nausea r 10) |
| Attack / Use | Double Jab (+1.5) / Item Sweep (pull drops within 14) | Crystal Edge (blade: sweeps) / a crystal block on a face (30 s) | Circuit Jolt (+2) / opens iron doors and trapdoors | Chilling Touch (+1, slow) / Spectral Bolt (3) |

Bosses: every "is it a boss" check is `QuirkThreat.add(e, p, x) || maxHealth >= 150` -- a boss's threat table hears the move
(fear, possession, stuns and the time burst are refused or weakened on it). Diamondhead's constructs go through `TempBlocks`
(they revert, and respect `abilityTerrainDamage`), made of `projecthero:diamondhead_crystal` (translucent, no item, no drops).

### Framework extensions (backwards-compatible; every default is "off")

* `AlienKit.phasing(Player)` -- horizontal movement ignores blocks (`mixin/OmnitrixAlienCollideMixin`, both sides) and the client
  no longer shoves the player out of walls (`client/mixin/OmnitrixAlienPhaseClientMixin`). The Silver Surfer's Molecular Phase
  approach. End it with `SuperSpeedHandlers.settleOutsideBlocks`.
* `AlienKit.standsOnFluid(Player, FluidState)` -- water (or any fluid) holds the wearer up (`mixin/OmnitrixAlienLivingMixin`,
  `canStandOnFluid`); `AlienKit.climbs(Player)` -- reported as on a ladder (`onClimbable`).
* `AlienKit.activeKit(Player)` -- the kit of the alien a player is right now, client-safe (mixins read synced state only).
* `AlienClientKit.bodyAlpha(player)` -- the body's opacity; below 255 the body uses vanilla's translucent entity type
  (`client/mixin/OmnitrixAlienTranslucentMixin`, the `LivingEntityPhaseMixin` technique). Ghostfreak: 150 (90 phased).
* `AlienProjectileEntity.trail(kind, particle)` -- a client particle trail per projectile kind (registered once at init).
* `mixin/OmnitrixUpgradeEmpMixin` (Upgrade only): `BlockStateBase.getSignal / getDirectSignal` return 0 inside a live
  `UpgradeEmp` zone; one volatile "any zone" check first, so it costs nothing otherwise. A jam and its end re-update the zone.
* `gen_omnitrix_lang.js` now keeps the file's line endings (the checkout is CRLF: the old START marker never matched and the
  block was duplicated).
* Two `OmnitrixGameTests` checks no longer assume exactly two playable aliens (the dial wrap and "coming soon").

### Wave A state and resets

Static per-player state: `Xlr8Kit` (the twister centre, Blur Dash hits), `DiamondheadKit` (the Spike Line, the Barrage storm),
`UpgradeKit` (the merge), `GhostfreakKit` (the possessed mob, feared mobs, the tendril pit) -- each cleared in `onRevert` (timeout,
Shift+V, death, logout, the pod) and in its `clearSessionState` (from `OmnitrixMod.clearSessionState`). `UpgradeEmp`'s jam zones are
server-wide and short-lived (they lapse on their own) and are dropped on server stop. Transient attribute modifiers outside the
body (XLR8's sprint and attack speed, Diamondhead's blade, Upgrade's slide scale and speed) are removed in `onRevert`; Fade's and
Merge's invisibility is our own 40-tick effect, removed on revert (a longer potion of the player's own is left alone).

## Polish pass (v0.17.1, user: "green vfx flash and kirby dots", "move the aliens HUDS to the right side", "not so bulky")

* **`client/omnitrix/OmnitrixTransformFx`** -- the transformation look, all client-side from the synced anim
  (`ANIM_TRANSFORM` green; `ANIM_REVERT` / `ANIM_TIMEOUT` red, the timeout longer and denser). The wearer's screen: a flat
  `#00A000` (`#C00000`) field from a hot lime flash, with clumps of black Kirby dots (a big dot plus two or three bubbling off
  it, on a jittered 4x3 grid) that pop in with an overshoot, drift outward and shrink away; 16 / 14 / 20 ticks. The world
  (everyone, not the wearer's own first person): an additive glow on the body and camera-facing black dots on green / red rims
  bursting outward. Seeded from the anim start, so every viewer sees the same burst; `LIVE` (per player) is cleared on
  disconnect and whenever the client level changes. Textures: `gui/omnitrix/fx_dot.png`, `fx_glow.png` (premultiplied:
  `RenderType.eyes` is additive) from `scratchpad/gen_omnitrix_fx_textures.js`. The server's white `FLASH` particle and the
  dust helix are gone (the pod's flicker keeps its flash).
* **`OmnitrixHud`** -- bottom-right in the Quirk HUD's mono style: R G Z X C boxes (no V, never H / N; the plain move's
  cooldown, the Shift move's while sneaking), a `Time` meter (green, red in the last 15 s, the clock right-aligned), the alien's
  name (its colour lifted to read on any sky) and the kit's rows above (`AlienClientKit.hud`). Human: an `Omnitrix` title row
  with the selected alien, a red `Recharge` meter with the seconds, the scroll / click hint wrapped above it while the watch is
  up. The bottom-left Omnitrix dial badge is gone. `OmnitrixHud.meter` is the shared row.
* **`OmnitrixModel`** -- rebuilt from swept ribbons and cylinders instead of boxes: one rounded cuff band ~0.45 px proud of the
  sleeve, a low black bezel with four lime lights, the grey core (`CORE_R` 1.5, pops 1.0), four white prongs that run from
  under the bezel over the top edge, down the sides and curl under the wrist (lifted a little off the side like claws), and the
  front button. Long edges carry the swatch's outline + bevel (fixed 0.1 px), so bands read as one piece. Same texture.
* **`OmnitrixPose`** -- the raised watch: rotations solved with `scratchpad/omnitrix_pose_solver.js` (left forearm level at
  chest height, face up; the left shoulder drops 6 px and the right 2.5 px since the arm has no elbow), the head bowed, the
  right hand gripping the dial's near rim; each scroll rolls the hand (the twist), the click winds up and slaps the core.
* Harness: `scratchpad/OmnitrixPolishHarness.v0171.java.txt`; shots `docs/screenshots/v0171/omnitrix_polish_*.png`.
