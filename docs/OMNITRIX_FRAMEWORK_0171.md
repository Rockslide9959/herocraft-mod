# Omnitrix framework (v0.17.1)

Ben 10's Omnitrix as a Hero-Tier power (`omnitrix`), the way to get it (a meteor / the Meteor Scanner), the only way to
lose it (the Omnitrix Containment Pod), and a reusable alien framework. Heatblast and Four Arms ship with it; the other
eight of the original ten (XLR8, Diamondhead, Upgrade, Ghostfreak, Stinkfly, Wildmutt (Wildvine until v0.17.1), Ripjaws, Grey Matter) are on the
dial as "coming soon" until their kits register.

Rules that hold: H = power selector only, never a HUD box. X = movement, V = each alien's utility / mode move, R G Z C = attacks,
N = turn back (the Omnitrix's; no box). (v0.17.1, user: "make the detransform keybind N and add abilities to the V key for every
alien" -- V turned back and N was unused before; see "Key layout" at the end.)

## Where things are

| Piece | Server (main) | Client |
|---|---|---|
| The power, the lock, transform / revert / timeout, the watch up (select, cycle, slap) | `omnitrix/Omnitrix.java` | |
| State (one synced, persistent, copy-on-death attachment) | `omnitrix/data/OmnitrixState.java`, `OmnitrixAttachments.java` | |
| Keys (N + the kit's R G X Z V C) | `omnitrix/OmnitrixAbilityManager.java` (from `AbilityRouter`) | scroll / clicks: `client/omnitrix/OmnitrixClient.java`, `mixin/OmnitrixInputMixin`, `mixin/OmnitrixScrollMixin` |
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

* **R as a human** (v0.17.1 user: was V) raises the wrist (first person: the forearm comes in from the left, the chunky watch's face toward the
  eye; third person: the forearm comes up level in front of the chest with the face up, the head bows to it and the other
  hand grips the dial). The core pops up (`omnitrix_core_up`), the face dims and shows the selected alien's silhouette
  glowing on it, a faint copy hovering just over the face (the big third-person hologram was removed in the polish pass).
* **The scroll wheel** (while raised) turns the dial to the next alien that has a kit (aliens without one are skipped);
  the hotbar does not move. Each step clicks, the core twists a notch, the name shows on the action bar and in the HUD's title row.
* **A left- or right-click** slaps the core down (`Omnitrix.slap`; the click never attacks, uses or mines) and
  `Omnitrix.SLAP_TICKS` later the green flash and Kirby dots burst (`OmnitrixTransformFx`) and the alien takes over.
* **R again** lowers the watch: no cost, no cooldown. **N while transformed** reverts early (10 s recharge); Shift+N is
  the same key (v0.17.1 user: was V). **R during a recharge** flashes the face red with the time left (the dial stays locked).
* The selected alien persists. A transformation lasts 3 minutes (the watch light flashes red and beeps for the last 15 s),
  then the timeout chime, the red flash and Kirby dots, and a 90 s recharge.

Server entry points (tests and kits use them): `Omnitrix.activate / deactivate / cycle(p, dir) / slap / transform /
revert(p, Reason) / select(p, id)`; `OmnitrixAbilityManager.pressR(p)` (human) / `pressN(p)` (alien: turn back). Client to server: `OmnitrixActionPayload`
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

## Stinkfly, Wildvine, Ripjaws and Grey Matter (the second batch; Wildvine was later replaced by Wildmutt, below -- its kit, client, assets and tests are gone)

Kits: `alien/stinkfly/StinkflyKit`, `alien/wildvine/WildvineKit`, `alien/ripjaws/RipjawsKit`, `alien/greymatter/GreyMatterKit`;
clients `client/omnitrix/alien/StinkflyClient`, `WildvineClient`, `RipjawsClient`, `GreyMatterClient`. "Very basic models
for now" (user): the player body with a generated skin plus `AlienParts.box` cubes from an `<alien>_extras.png` sheet; a kit
that replaces a limb hides it in `pose()` (`visible = false` -- `PlayerRenderer.setModelProperties` makes everything visible
again before the next player renders) and redraws it in `renderExtras` on the same bone.

| | Stinkfly | Wildvine | Ripjaws | Grey Matter |
|---|---|---|---|---|
| Body | 14 HP, x1.1 speed, x0.9 size (0.54 x 1.62, eyes 1.46), armour 1, fist +1; **custom GeckoLib body** (v0.17.1, "Stinkfly's body" below), claws hold nothing (pawsOnly) | 28 HP, x1.1 size, armour 3, fist +2, KB res 0.2, reach +1.5 | 30 HP, x0.9 speed, x1.05 size, armour 4 (+1), fist +3, KB res 0.2 | 8 HP, x1.35 speed, **x0.33 size** (0.59-block hitbox, eyes at 0.53), safe fall +1 |
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

(XLR8's column is the wave-A kit; his moves were replaced by the revamp -- see "XLR8 revamp" at the end.)

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
* **`OmnitrixHud`** -- bottom-right in the Quirk HUD's mono style: R G Z X C boxes (no V, never H / N -- v0.17.1: R G Z X C V now; the plain move's
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
* **The two-hand press** (`OmnitrixHands`, user: "use their right hand to press the button on the omnitrix to pop up the
  dial ... press the button again to deactivate it"): one client rig off the synced state drives the wrist, the core, the face
  and the right hand. Ticks after R: the wrist rises (0-5), the right hand comes in from the bottom right to the cuff's side
  button (5.5) and presses it (`Omnitrix.PRESS_TICKS` = 7: the core pops on the press and `omnitrix_core_up` plays then), backs
  off (8.5) and settles on the dial's lower-right rim (11.5), the face morphs (9-15) and the icon fades in (14-17). The server
  only lets the dial turn / the slap land from `Omnitrix.CORE_UP_TICKS` = 11 (an earlier scroll is ignored, an earlier click
  is held and lands then). Scrolls twist the gripping hand with the ring (a slower spring trailing it, so it turns and
  re-grips, either way); the click lifts the palm and slams it on the core (`OmnitrixHands.SLAP_HIT`). R again: the hand goes
  back to the button (2.5), presses (3.5: the core sinks, the face goes back), pulls away down-right (8.5), then the wrist
  comes down (7-12); R mid-sequence starts the other way from wherever the rig is. First person draws the right arm itself
  (`OmnitrixFirstPerson.renderRaised`: the player's own `renderRightHand`, placed by the hand's fingertip frame in the watch's
  space) and drops the usual right-side arm / item out of view meanwhile; third person blends the right hand to the button
  (`OmnitrixPose.HAND_BUTTON`, `scratchpad/omnitrix_button_pose_solver.js`). Harness:
  `scratchpad/OmnitrixPressHarness.v0171.java.txt`; shots `docs/screenshots/v0171/omnitrix_press_*.png`.
* **The alien badge** is the show's white Omnitrix symbol (`textures/entity/omnitrix/badge.png`,
  `scratchpad/gen_omnitrix_badge.js`: a white ring, a black field, a white hourglass); the red beep / timeout flash still shows
  the red watch face. Shots `docs/screenshots/v0171/omnitrix_badge_*.png`.
* Harness: `scratchpad/OmnitrixPolishHarness.v0171.java.txt`; shots `docs/screenshots/v0171/omnitrix_polish_*.png`.

## Wildmutt (the first "real" alien; replaces Wildvine)

User: "replace wildvine with wildmutt ... wildvine wasnt in the original 10, wildmutt was ... we're gonna try to move out of demo and
build real aliens now". Kit `alien/wildmutt/WildmuttKit`, client `client/omnitrix/alien/WildmuttClient` + `client/omnitrix/alien/wildmutt/`
(`WildmuttAnimatable`, `WildmuttModel`, `WildmuttRenderer`, `WildmuttFirstPerson`, `WildmuttSense`), mixins `WildmuttSenseGlowMixin` /
`WildmuttSenseColorMixin` (client) and `OmnitrixAlienBodyMixin` (main). Dial slot: Wildvine's (font glyph and `OmnitrixInfo.DIAL_ORDER`
index 7), colour `#F08A24`, icon from the user's art (`gen_omnitrix_dial_icons.js`). Tests: `OmnitrixWildmuttGameTests` (7).

**Saves**: `OmnitrixState.RENAMED` (`wildvine` -> `wildmutt`) is applied in the codec to `selected`, `active` and `flicker`; a migrated
`active` also drops the kit bits and the pose, and every `wildvine.*` timer is dropped.

| | Wildmutt |
|---|---|
| Body | 34 HP, speed x1.35 (+20% more galloping = sprinting), armour 4 (+1), KB res 0.3, claws +4, step +0.4 (a full block), jump +0.12, safe fall +4; a **0.9 x 1.55** hitbox (crouching 1.3), eyes at 1.25 (rework 2: the slim, leggy, tail-less body); paws -- he holds and uses no items (`AlienKit.pawsOnly`) |
| Passives | Scent Sense: creatures within 24 outlined amber for him only (invisible ones too), 48 while sniffing (+ dropped items), the tracked one red at any range; no eyes: Blindness / Darkness removed every tick; no fall damage during his own leaps / pounce / with Wall Climb on |
| Weaknesses | underwater: senses 6 blocks, air runs out twice as fast; an explosion deafens the sense for 3 s |
| R / Shift+R | Pounce (lunge at the look target within 12, else a bound; contact bite 9, pinned 1.25 s, heals 1; 3 s) / Maul (rears up: rakes of 3.5 at ticks 4, 7, 10, a double slam of 6 + knockback at 16, cone 70 deg, 3.4; 7 s) |
| G / Shift+G | Quill Volley (7 quills in a 45 deg fan from his back at tick 9, 3.5 each, `wildmutt_quill`; 5 s) / Savage Roar (tick 6: cone 50 deg, 9 blocks, 3 + Slowness II + Weakness 3.5 s + a 0.8 s stun; 15 s) |
| X / Hold X / Shift+X | Leap (~8 blocks, landing shock ring; 1.5 s) / Coiled Leap (the first hold move: coil up to 1.5 s, up to 1.8x as far; 3 s) / Wall Climb toggle (`climbs`: pressed into a wall, not sneaking; 0.5 s) |
| Z / Shift+Z | Ground Slam (tick 9, r 4.5 ahead: 8 near -> 5 at the edge, launch; 8 s) / Quill Roll (1.5 s spiked ball: 5 to each creature once, -40% taken; 10 s) |
| C / Shift+C | Feral Frenzy (8 s: +25% speed, +4 claws, +1 attack speed, claw hits heal 1.5; 30 s) / Scent Track (look target within 32 or the nearest within 48, players first: 30 s, red outline + his own scent trail to it, the HUD's Scent row shows the distance; 15 s) |
| Attack / Use | Claw Swipe (vanilla hit + a half-damage cleave beside the target, alternating paws) / Sniff (empty hand on air: 6 s; 6 s) |

Sounds (OmnitrixSfx cues, vanilla only): GROWL (wolf growl .55 x0.6, ravager ambient .25 x1.3, gap 30), BITE (ravager attack .5 x1.35, fox
bite .5 x0.7), CLAW (sweep .35 x1.3, strong attack .25 x0.7), ROAR (ravager roar .65 x1.1, polar bear warning .5 x0.65, wolf growl .4 x0.5
@4), BRISTLE (wolf shake .55 x0.75, growl .3 x0.8), QUILLS (arrow shoot .4 x1.5, sweep .25 x1.8), SLAM (ravager step .72 x0.5, explode .18
x1.6, polar bear warning .3 x0.9), LEAP (ravager step .45 x1.2, wolf pant .35 x0.75), LAND (ravager step .7 x0.8, polar bear step .5 x0.7),
SNIFF (sniffer sniffing .45 x1.3, fox sniff .4 x0.75), FRENZY_CUE (growl .6 x0.9, ravager roar .35 x1.5 @6), ROLL_CUE (armadillo roll .5
x0.7, ravager step .3 x1.1).

### The custom-body framework (new, backwards-compatible; every default is "off")

* `AlienClientKit.customBody()` / `buildBody(context)` / `renderBody(...)`: the alien's own renderer replaces the player model
  (`OmnitrixPlayerRendererMixin`: built from the player renderers' context at `<init>`, drawn at `render` HEAD -- the Hulk's approach).
  Armour, cape, held items and `OmnitrixLayer` are not drawn: the kit draws its badge (Wildmutt: `WildmuttRenderer.Badge`, the white
  `OmnitrixLayer.BADGE` / the red watch face on the plate of the strap round his left upper arm).
* `AlienClientKit.renderFirstPersonHand(pose, buffers, light, p, right)` replaces the first-person arm (`renderRightHand` /
  `renderLeftHand` HEAD); `bothHandsInFirstPerson(p)` draws the off arm whenever the off hand is empty (`OmnitrixItemInHandMixin`).
  Wildmutt's arms reset the pose to camera space and apply the kit clip themselves.
* `AlienKit.body(Player, Pose)`: the alien's own `EntityDimensions` for the poses it answers (`OmnitrixAlienBodyMixin` on
  `Player.getDefaultDimensions`; the player re-measures whenever the answer changes, so turning back restores the player's box).

### The model (`scratchpad/wildmutt_v0171/`)

`gen_wildmutt.js` builds the rig with `viperion/lib_concept_models.js` (bones + boxes in model pixels, a procedural painter: mottled
orange fur -- a top-lit gradient, soft blotches, 2 x 2 texel light / dark flecks, a fine grain, a few fur strokes, darker face rims --
a lighter belly, dark brown square claws, the black mouth band (its upper half laps the lower, so the closed mouth shows no red line) with
uneven cream teeth over it, gill slits behind it, a dark red mouth and pink tongue for the open jaw, the navy Omnitrix cuff with its pale
rim and dark plate). Rework 3 (the third model): rework 2's reference shape (a boxy eyeless head carried low on a thick neck that fills
the join, no tail, long angled front legs, dog-like hind legs) with rework 1's bulk (thick legs, a deep chest) and tapered quill tufts.
It writes `geo/omnitrix/wildmutt.geo.json` (24 bones, 144 cubes) + `textures/entity/omnitrix/wildmutt.png` (512 x 256);
`anims.js` samples 19 clips into `animations/omnitrix/wildmutt.animation.json` (idle, idle_sniff, walk, sneak, gallop, air, leap_charge,
pounce, maul, quills, roar, slam, roll, frenzy, track, climb, swipe_r / swipe_l, hurt); `--sheets <dir>` renders preview sheets offline
(`preview.js`). `lang_wildmutt.js` writes his ONE contiguous lang block at the end of `en_us.json`. Rig: root > spin (the roll) > hurt (its
own controller) > hips > chest > neck > look (the head-turn, set in code, never keyed) > head > jaw; arms (arm > forearm > paw, the badge on
the left arm), legs (thigh > shin > foot), the quills_f / quills_b spike groups (bristle by scale); no tail. Drawn 1:1
(~1.52 blocks at the hump, 2.55 long).

**GeckoLib gotcha**: keyframe times must not be written as integer-like keys (`"1"`): JavaScript lists those first in an object, so the
JSON had every clip over 1 s out of order (it played as a still pose). `anims.js` writes `"1.0"`. Clip speeds are fixed (GeckoLib rescales
a clip's whole elapsed time when its speed changes): walk (0.56 s), sneak (1.0 s) and gallop (0.42 s) are authored for his speeds.

Harness: `scratchpad/WildmuttHarness.v0171.java.txt` (flag `run/wildmutt.flag`); shots `docs/screenshots/v0171/omnitrix_wildmutt_*.png`.

## Stinkfly's body (the second custom-body alien)

User: a proper, good-looking custom GeckoLib body from the reference images, built like Wildmutt. Client
`client/omnitrix/alien/StinkflyClient` + `client/omnitrix/alien/stinkfly/` (`StinkflyAnimatable`, `StinkflyModel`, `StinkflyRenderer`,
`StinkflyFirstPerson`); the kit is unchanged but for `pawsOnly` (claws hold nothing; the Use goo spit fires whatever is in the slot).
Hitbox: the player's, scaled by the 0.9 size stat (0.54 x 1.62, eyes at 1.46 -- the middle of the model's head).

`scratchpad/stinkfly_v0171/gen_stinkfly.js` (same library) writes `geo/omnitrix/stinkfly.geo.json` (34 bones, 137 cubes),
`textures/entity/omnitrix/stinkfly.png` + `stinkfly_glowmask.png` (512 x 144; the old player skin and `stinkfly_extras.png` are gone)
and, via `anims.js`, `animations/omnitrix/stinkfly.animation.json` (27 clips); `--sheets <dir>` renders preview sheets (`preview.js`).
The look: a black rounded-boxy head with a pale grey mouth plate and small fangs, four green-black eye-stalks (two a side, up and out)
with yellow-orange bulb eyes (pupils forward, glowing); a narrow white chest and waist with a black stripe down each side, black bands
and a black V; four thin mottled olive limbs (mantis arms off the shoulders with black three-clawed hands and elbow / forearm spikes;
legs with sharp knees, shin spikes and spiky black feet); a long white tail in four segments curving down and back to a black
arrowhead stinger fin with a white stripe; two pairs of dragonfly wings (pale yellow-green, see-through, veins, a pointed swept tip,
round notches in the trailing edge; upper 21 px and up-and-out, lower 16 px). The Omnitrix sits on a plate on the chest
(`StinkflyRenderer.Badge`, the framework badge, red while it beeps).

Rendering: the whole body uses `RenderType.entityTranslucent` (no culling, so the flat wing plates show from both sides and their
see-through texels let the world through); `AutoGlowingGeoLayer` + the glowmask keep the eyes bright and give the wings a faint glow.
The `look` bone turns the head (the head-minus-body yaw is wrapped first: GeckoLib's is not).

Rig: root > hurt > hips > chest > neck > look > head > jaw / 4 stalks; arm > forearm > hand (x2), wing_u / wing_d (x2) and badge on
the chest; thigh > shin > foot (x2) and tail1 > tail2 > tail3 > tail4 > fin on the hips. Controllers (later ones win per bone):
`main` (idle, walk, hover, fly; the held dive; dash, takeoff, splash, lift, lift_hold), `move` (shot, barrage, net, gas, stab, jab,
gust, storm: head / jaw / chest / arms / tail only, so they play over a hover or a walk; never the neck, which holds the flight's
head-up), `wings` (wings_fold, wings_walk, wings_hover, wings_fly, wings_dive, wings_gust), `swing` (jab_r / jab_l on Attack) and
`hurt`. Flight is read from the synced `F_FLYING` / `F_DIVING` / `F_LIFT` flags; fly vs hover by speed. First person: the forearm and
hand bones, laid out from vanilla's hand pose, both always shown, a clip for every move (`StinkflyClient.firstPerson`).

Harness: `scratchpad/StinkflyHarness.v0171.java.txt` (flag `run/stinkfly.flag`; "fp" = first person only); shots
`docs/screenshots/v0171/omnitrix_stinkfly2_*.png`. Test: `OmnitrixAliens2GameTests.stinkflysClawsHoldNothingButStillSpit`.


## Key layout: N turns back, V is every alien's utility move (v0.17.1)

User: "for the ben 10 power make the detransform keybind N and add abilities to the V key for every alien please".

* **N** (`AbilitySlot.SLOT_8`): the client sends it straight to the server while transformed (`ProjectHeroModClient.handleMaxSteelTransform`,
  first branch -- the watch locks every other power out, so nothing else wants N); `AbilityRouter`'s utility branch hands it to
  `OmnitrixAbilityManager`, which calls `pressN` -> `Omnitrix.revertEarly` (the power-down beeps, the short recharge). Shift+N is
  the same. N as a human does nothing (R raises the watch). `Omnitrix.Reason.SHIFT_V` keeps its old name (it is the early-revert reason).
* **V** (`SLOT_5`) is an ordinary kit key now: tap / Shift / hold like R G X Z C, a HUD box (R G Z X C V, as the Quirk HUD), a row
  in the Left-Alt list, the info page and the guide (`OmnitrixInfo.KIT_SLOTS` = R G X Z V C). `OmnitrixInfoGameTests` fails if a
  playable alien (but Wildmutt, rebuilt separately) has no tap V move.

| Alien | V | What it does |
|---|---|---|
| Heatblast | Heat Aura (toggle) | creatures within 3.5 catch fire (+1 a second), snow / ice melt, dropped items smelt (tagged, once); water puts it out |
| Four Arms | Wall Climb (toggle) | `climbs()`: pressed into a wall he climbs it, hand over hand (both arm pairs); no fall damage |
| Diamondhead | Crystal Regrowth | 3 s rooted, +1.5 every half second (9), half damage meanwhile; refused at full health; 20 s |
| Ghostfreak | Spirit Sight (toggle) | night vision + every living thing within 32 outlined (`AlienClientKit.glows`, `client/mixin/OmnitrixGlowMixin`, the wearer's screen only) |
| Stinkfly | Lift | grab the creature in front (4.5; not bosses / big ones) and carry it under you (in front on the ground); V again drops it |
| Ripjaws | Water Bubble | 30 s of a water globe round his head: dry land counts as rain (the meter refills); 60 s |
| Grey Matter | Pocket Workshop | a crafting grid anywhere (`CraftingMenu` with `stillValid` true; closes when he turns back) |
| Upgrade | Overclock | 15 s merged with the held tool: +100% mining speed, +4 damage, mends a point a second; needs a main-hand item; 30 s |
| XLR8 | Speed Mode (toggle) / Shift+V Afterimage Decoys | see below |
| Wildmutt | -- | not yet: his rebuild is in progress elsewhere |

Each V move has a third-person pose and a first-person clip in its client kit (Four Arms also a climbing pose for both arm pairs,
Ripjaws a translucent bubble round the head). Tests: `OmnitrixVKeyGameTests` (N through the router, one V move per alien, XLR8's revamp).

## XLR8 revamp (v0.17.1)

User: "work on the xlr8 revamp please: (planetminecraft XLR8 skin) ... Give him a new moveset thats similar to the regular super
speed powers moveset please, i want him to be really cool and i want the power set to be on par with the other abilities in the mod".

* **Skin**: the user's planetminecraft skin (`scratchpad/xlr8_v0171/xlr8_src.png` -> `textures/entity/omnitrix/xlr8.png`) and a
  painted closed-mask copy (`xlr8_mask.png`: the hat layer's front is the blue-black visor slid down to the jaw, a gloss streak,
  the eyes dim green through it). `AlienClientKit.skin(player)` (new, default `skin()`) picks it: `Xlr8Kit.visorDown` -- Speed Mode,
  a dash, the tornado, Phase, or a speed move in the last 1.5 s. The eyes glow (`xlr8_eyes.png` / `xlr8_mask_eyes.png`, drawn with
  `RenderType.eyes` on the head and hat parts). Extras recoloured to the skin (`xlr8_extras.png`): a swept helmet crest and ear fins,
  the striped tail, rolling ball feet, pale claws; the old visor box and the fake ghost copies are gone. Generator:
  `scratchpad/xlr8_v0171/gen_xlr8_skins.js` (uses `scratchpad/xlr8_mask.png` instead when the user supplies one).
* **Moves**: the plain Speedster's, borrowed (`SuperSpeedHandlers.rapidAssaultCore`, `SuperSpeedMoves.machPunchCore / blitzCore /
  beginSweep / sweepTick / endSweep / decoysCore / streak`, `SuperSpeedHandlers.settleOutsideBlocks`) on the kit's own cooldowns, plus
  his own.

| Key | Tap | Shift | Hold |
|---|---|---|---|
| R | Rapid Assault (4 x 5 in front), 2 s | Mach Punch (20 + shockwave 6), 10 s | |
| G | Blitz (zip 24 blocks, 20; 10 round it), 5 s | Tornado Run (3 s twister), 15 s | |
| X | Blur Dash (afterimages, 3), 2 s | Speed Sweep (every foe within 50, 15 each, then home), 15 s | Phase (through walls, untouchable, 5 s max), 5 s |
| Z | Whirlwind (r 6, 4), 10 s | Kick Barrage (8 x 1.5), 7 s | |
| V | Speed Mode (toggle) | Afterimage Decoys (3 for 6 s), 20 s | |
| C | Time Burst (r 12 slow 5 s), 30 s | Speed Mine (1x2 tunnel 16), 15 s | |
| Attack / Use | Double Jab (+1.5) / Item Sweep (14) | | Use on a mob: Snatch (its held item) |

* **Speed Mode**: +100% speed on top of the body's 1.9x and the sprint bonus (~16 b/s walking, 30+ sprinting), +0.5 step, the
  plain Speedster's after-image trail (`SuperSpeedClientV0145` asks `Xlr8Client.trails`; an Omnitrix alien's copies now wear the
  alien's skin on the wide model at the body's scale), wall running (`climbs`) and water running (`standsOnFluid`), no fall damage,
  slow regeneration, blue static. `SuperSpeedMoves.color` streaks XLR8's borrowed moves in his blue.
* **Phase** (hold X): `phasing()` + `allowDamage` false unless the source bypasses invulnerability; the body flickers see-through
  (`bodyAlpha`) and shivers; letting go (or 5 s) settles him outside the blocks. Other keys are swallowed while it runs.
* Resets: Speed Mode / Phase / step modifiers come off in `onRevert`, a running sweep is ended (he is brought home); the sweep's own
  map is cleared by `SuperSpeedMoves.clearSessionState` / `onDisconnect` as before.
* Screenshots: `docs/screenshots/v0171/omnitrix_xlr8v2_*.png` and `omnitrix_vkey_*.png` (harness:
  `scratchpad/xlr8_v0171/AlienLookHarness.xlr8.v0171.java.txt`, flag `run/alienlook.flag` = "xlr8 vkey").
