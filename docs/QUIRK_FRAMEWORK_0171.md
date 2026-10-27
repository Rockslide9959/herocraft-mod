# Quirk framework (v0.17.1, wave 0 of the Quirks revamp)

The shared layer every Quirk kit builds on, from section 2 of `docs/QUIRKS_REVAMP_PROPOSAL.md` (user-approved; every open
question resolved to the doc's recommendation, custom sounds included). The seven kit builds (Hellflame, Overhaul, Engine,
Dark Shadow, Erasure, Explosion, Zero Gravity) plug into it. One For All is standalone and is skipped by all of it.

User rule for the whole revamp: **"i dont want like heat or darkness or whatever to build up too quick, make it work well
please."** Meters build gently (section 6 below).

Rules that still hold: H = power selector only, N only where the user assigned it (Dark Shadow: N = hide). No H/N HUD boxes.
X = movement, V = utility / mode, R G Z C = attacks. All HUD / guide text wraps.

## Where things are

| Piece | Server (main) | Client |
|---|---|---|
| Effect entity + builders | `quirk/fx/QuirkFx.java`, `QuirkFxEntity.java` | `client/quirk/fx/QuirkFxRenderer.java`, `QuirkFxDraw.java` |
| Particles | `quirk/fx/QuirkParticles.java` | `client/quirk/fx/QuirkParticleClient.java` |
| Camera juice | `quirk/fx/QuirkCamera.java`, `QuirkCameraPayload.java` | `client/quirk/QuirkCameraFx.java`, `mixin/QuirkCameraShakeMixin`, `mixin/QuirkFovPunchMixin` |
| Sounds | `quirk/fx/QuirkSounds.java`, `sounds.json` (`quirk_*`) | `assets/projecthero/sounds/quirk/*.ogg` |
| Meter bands + pacing | `quirk/meter/QuirkMeterSpec.java`, `QuirkMeters.java`, `QuirkBand.java`, `<kit>/<Kit>Meter.java` | `client/quirk/QuirkHud.java` (`drawBands`) |
| Plus Ultra | `quirk/PlusUltra.java` (+ `QuirkKit#ultimate`) | `QuirkHud` (gauge, C box), `QuirkCuePopup` (banner) |
| Combo routes | `quirk/QuirkCombos.java`, `QuirkCuePayload.java` | `client/quirk/QuirkCuePopup.java` |
| Poses | -- | `client/quirk/QuirkKeyframes.java`, `QuirkFirstPersonPose.java`, `mixin/QuirkFirstPersonPoseMixin` |
| Suit perk / tells | `quirk/QuirkPerks.java` (+ `QuirkKit#onSuitChanged`) | `client/quirk/QuirkTellLayer.java` (+ `QuirkClientKit#renderTell`) |
| Wiring | `quirk/QuirkFramework.java` (from `QuirkMod`, `Quirks.tick`, `Quirks.forget`) | `QuirkClient.initializeFramework` |
| Tests | `src/gametest/.../QuirkFrameworkGameTests.java` | |
| Generators | `scratchpad/gen_quirk_framework_textures.js`, `scratchpad/synth_quirk_framework_sounds.js` | |

## 1. VFX toolkit (`quirk/fx`)

One unsaved entity, `QuirkFxEntity`, carries a few synced numbers (kind, life, colour ramp A to B, two sizes, easing,
style, count, flags, a vector, a followed entity, a block state). Everything is set before it is added, so the whole effect
travels in the one spawn + entity-data packet; `updateInterval` is 40 and the client animates it off its own age. Spawn on
the server:

```java
ServerLevel l = p.serverLevel();
QuirkFx.blast(l, hit).colours(0xFFFFFF, 0xFF7A1A).size(3.5f).life(18).spawn();          // flash + fire shell + ground ring + smoke crown
QuirkFx.ring(l, feet).colours(0xFFFFFF, 0xFF8FC8).size(6f).thickness(0.6f).ground().spawn();
QuirkFx.ring(l, chest).flags(QuirkFx.F_VERTICAL).vec(look).size(2.5f).spawn();           // a sonic-boom ring facing the look
QuirkFx.ring(l, feet).style(QuirkFx.RING_STEAM).colours(0xE8F0FF, 0xC8D0DA).spawn();     // steam puffs (Heat Vent)
QuirkFx.ribbon(l, hand, target).style(QuirkFx.RIBBON_CLOTH).colours(0x3A3A40, 0x2A2A30).size(0.18f).spawn();   // A to B
QuirkFx.trail(l, player).vec(new Vec3(0.2, 0.3, -0.1)).size2(12f).life(40).spawn();    // follows; draws the last 12 ticks of path
QuirkFx.cone(l, palm, look).colours(0xFFF4C0, 0xFF4A10).size(2f).length(8f).spawn();    // flame cone
QuirkFx.cone(l, palm, look).style(QuirkFx.CONE_BEAM).size(0.25f).length(30f).spawn();  // beam
QuirkFx.spike(l, groundPos, Vec3.UP).block(groundState).size(2.4f).size2(0.45f).spawn();
QuirkFx.shards(l, at).block(state).size(2f).count(24).reassemble().spawn();            // burst, hang, fly back
QuirkFx.decal(l, at).style(QuirkFx.DECAL_SCORCH).colour(0x1A120C).size(3f).life(300).spawn();
QuirkFx.flipbook(l, at).style(QuirkFx.BOOK_FIREBALL).size(1.4f).life(16).spawn();
```

| Kind | Draws | Styles / flags |
|---|---|---|
| `BLAST` | white flash billboard, two noise-wobbled fire shells cooling A to B, 8 flipbook fireballs bursting out, a ground shock ring (`F_NO_RING` off), a dark smoke crown (`F_NO_SMOKE` off), a spark / ember burst | |
| `RING` | expanding ring, bright leading edge + soft halo | `RING_SHOCK`, `RING_STEAM`, `RING_SOFT`; `F_GROUND`, `F_VERTICAL` (faces `vec`) |
| `RIBBON` | tapered camera-facing strip, head shoots out then the tail follows; or `F_TRAIL`: a followed entity's recorded path (`vec` = offset right/up/forward, `size2` = ticks of history) | `RIBBON_FLAME`, `RIBBON_LIGHT` (glow), `RIBBON_CLOTH`, `RIBBON_SHADOW` (lit) |
| `CONE` | flickering flame cone (white-hot apex) + flipbook fireballs, or a debris / dust cone, or a straight beam | `CONE_FLAME`, `CONE_DEBRIS`, `CONE_BEAM` |
| `SPIKE` | jagged, kinked 6-sided spike from the ground block's texture, rises with an overshoot, sinks in the last 20%, crack decal at its base | `F_GROUND` (default) |
| `SHARDS` | `count` block-textured fragments: burst, hang, then fall or fly back (`F_REASSEMBLE`) | |
| `DECAL` | ground mark that fades in, holds, fades out over its life | `DECAL_SCORCH`, `DECAL_CRACK`, `DECAL_POOL`, `DECAL_PRINT` (glowing fingertip pads) |
| `FLIPBOOK` | 8-frame animated billboard | `BOOK_FIREBALL` (glow), `BOOK_SMOKE`, `BOOK_INK` |

Easing (`QuirkFx.Ease`): `LINEAR`, `OUT_QUAD`, `OUT_CUBIC` (default), `OUT_EXPO`, `IN_OUT`, `OUT_BACK`. Glow geometry is drawn in
the additive `eyes` buffer (no depth write, double-sided); smoke, cloth, ink and decals are lit translucent. Kits drawing
their own one-off geometry can use `QuirkFxDraw` (billboards, rings, noise shells, cubes, `gquad`).

**Particles** (`QuirkParticles`, full-bright where marked): `EMBER`*, `SPARK`*, `SMOKE_PUFF`, `SHADOW_MOTE`, `STEAM`,
`PINK_SPARKLE`*, `DUST_CHUNK`. Each look is one `Look` line in `QuirkParticleClient`; sprites come from the texture generator.

## 2. Camera juice (`QuirkCamera`, server; `QuirkCameraFx`, client)

```java
QuirkCamera.preset(attacker, QuirkCamera.Preset.HEAVY_HIT);   // LIGHT_HIT, HEAVY_HIT, FINISHER, DASH, ULTIMATE
QuirkCamera.shakeAround(level, blastPos, 16, 0.6f, 10);       // everyone near, falling off with distance
QuirkCamera.hitStop(p, 3);                                    // capped at 4 ticks
QuirkCamera.fovPunch(p, 0.12f, 8);                            // +12%, springs back
QuirkCamera.vignette(p, 0xFF7A1A, 0.6f, 20);                  // heat orange edge glow
```

- Shake: noise on the camera yaw / pitch, decaying linearly (a stronger shake replaces a weaker one).
- Hit-stop: the client level's tick freeze for a blink -- mobs and their animations stop dead, your own player and camera
  keep moving -- then it lets go. Restores whatever the server's tick state was.
- FOV punch: a 2-tick kick that springs back, on the world view only (not the hand).
- Vignette: a coloured screen edge drawn by the Quirk HUD.
- **Accessibility**: there was no mod-wide camera-shake setting (Titan / Hulk have their own config booleans for their own
  shakes), so the framework uses vanilla's sliders: "Distortion Effects" scales the shake and turns the hit-stop off below
  5%; "FOV Effects" scales the FOV punch.

## 3. Poses (`QuirkKeyframes`, `QuirkFirstPersonPose`)

**One keyframe format.** Every Quirk pose table is now the 16-column `QuirkKeyframes` format
`{tick, rArm xyz, lArm xyz, body xy, rLeg xyz, lLeg xyz, headX}` (`f(...)` / `rest(t)`). Hellflame and Zero Gravity moved
from 12 columns; Overhaul and Erasure from 13 (their crouch is the optional 17th column: `fc(...)` / `restC(t)`); Dark
Shadow's per-limb tracks became 16-column tables played with a column mask (`applyMasked`), so his arm-only commands leave
the walk cycle's legs alone. Each pose still runs its own held / procedural layer on top (flight lean, the beam, Fusion).
New helpers: `sample(frames, tick, aimX, aimY)` with `QuirkKeyframes.AIM` in an arm column ("at the target"), `legTwist`,
`crouch`, `applyMasked`, and `from12(...)` for any old-order frame still built in code.

**First-person arms.** Register a 13-column clip for one of the kit's synced anim ids:

```java
// columns: tick, mainPitch, mainYaw, mainRoll, mainX, mainY, mainZ, offPitch, offYaw, offRoll, offX, offY, offZ
// (degrees about the arm's first-person shoulder; pitch negative = the fist rises into view; yaw + = toward the centre;
//  offsets in blocks: x toward the centre, y up, z negative = into the screen; mirrored for left-handed players)
QuirkFirstPersonPose.register(Quirk.HELLFLAME, Hellflame.ANIM_SPIDER, QuirkFirstPersonPose.clip(
        QuirkFirstPersonPose.f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        QuirkFirstPersonPose.f(3, -38, -24, -16, -0.04f, 0.34f, -0.12f, 0, 0, 0, 0, 0, 0),
        QuirkFirstPersonPose.f(6, -42, 34, 14, 0.24f, 0.36f, -0.2f, 0, 0, 0, 0, 0, 0),
        QuirkFirstPersonPose.f(16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));      // .twoHanded() also draws the off arm
```

Bare hands only (a held item keeps vanilla's hand). Hell Spider's flick is the shipped example (`HellflameClient`).

**Full body in first person** (`FirstPersonBody`): `QuirkFirstPersonPose.fullBody(Quirk.EXPLOSION, Explosion.ANIM_HOWITZER)`
marks an anim; `QuirkClientKit#fullBodyFirstPerson(player)` covers a kit's running state. The Plus Ultra hold and the first
30 ticks after an ultimate always use it. (`EngineFirstPersonLegs` stays as it is; the Engine build can fold it in.)

**Plus Ultra hold pose**: the framework plays a braced "power up" stance while C is held (`QuirkClient.ultimateHoldPose`);
override with `QuirkClientKit#ultimateHoldPose`.

## 4. Plus Ultra (`PlusUltra`)

A second 0-100 gauge per kit Quirk, separate from the kit's meter, saved in the synced `QuirkState` vals (`qf_pu`, x10).

- **Fills from fighting**: a landed hit (any damage the holder deals, through `AFTER_DAMAGE`) gives `0.5 + 0.08 x damage`
  (max 2), credited at most once per 8 ticks (a blast hitting ten mobs counts once); each HP taken 0.3; a combo route 3; a
  kit's `PlusUltra.add(p, PlusUltra.ALLY_SAVE)` 10. Erased holders gain nothing.
- **Pacing**: the reference fight (an 8-damage hit every second, 1 HP taken a second) fills it in **69 s**
  (`PlusUltra.secondsToFill`); huge hits every second still take 50 s+.
- **Decay**: after 30 s with no hit either way it drains 1 a second.
- **Hold C (20 ticks) at 100 = the ultimate.** At 100 a plain C press starts the hold instead of reaching the kit; a release
  before the hold completes plays the kit's C as a normal tap (press + release) and spends nothing; Shift+C is always the
  kit's Shift+C. A completed hold spends the gauge, shows the "PLUS ULTRA!" banner + flash, plays the sting, and calls the
  kit; the release that follows is swallowed. Below 100 nothing changes.
- **Kit hook** (`QuirkKit`):

```java
@Override
public void ultimate(ServerPlayer p) {
    // the gauge is already 0 and the banner / sting are playing: do the move, end with its drawback
}
@Override public int ultimateHoldTicks() { return 20; }                       // optional
@Override public String ultimateLangKey(Quirk q) { return "projecthero.quirk.ultimate." + q.id; }   // default
```

  Until a kit overrides it, `ultimate` is `PlusUltra.placeholderUltimate`: a white flash and a Quirk-coloured blast, a
  ground ring, the ULTIMATE camera preset, sparks, the "PLUS ULTRA!" shout and a shove on nearby mobs -- no damage.
  `hasUltimate()` false opts a kit out. Hellflame's old Shift+V "Plus Ultra: Full Power" is untouched (the Hellflame build
  replaces it).
- HUD: a gold hairline between the meter and the keys, pulsing when full; the C box lit gold at 100 and ringed white while
  C is held.

## 5. Three-band meters (`QuirkMeterSpec`, `QuirkMeters`)

Each kit registers one spec from its init (`<Kit>Meter.register()`, called in `<Kit>Mod.initialize`):

```java
QuirkMeterSpec.builder(Quirk.HELLFLAME)
        .bands(40f, 80f)                                   // LOW 0-39, SWEET 40-79, RED 80-100
        .names("kindle", "blaze", "inferno", "overheat")   // projecthero.quirk.band.<quirk>.<name>
        .damage(0.85f, 1.0f, 1.3f, 1.5f)                   // per band; read with QuirkMeters.damageMult(p)
        .pushPast(100, 1.0f, 160)                          // optional: 5 s past 100 at 1 HP/s, then an 8 s crash
        .onCrash(p -> burnout(p))                          // optional: default Quirks.overload(p, crashTicks)
        .pacing(0.12f, 1.0f)                               // build x0.12, recovery x1.0
        .suitBuild(0.75f)                                  // optional: the full suit builds it slower (Uravity)
        .heavyUse(h -> h.sustained(5f).move(10f, 30).move(12f, 50).recovery(4f, 30))   // the pacing test's script
        .register();
```

- `Direction.BUILDS` (default: use fills it, the RED band is the danger) or `Direction.SPENDS` (Focus / Matter: use empties
  it, the LOW band is the danger, the top band glows gold).
- `channel(...)` paces something other than the meter (Explosion: its Sweat is fuel and is NOT paced; Arm Strain is the
  danger channel, `danger(70)`, `safe(30)`).
- **Kit code**: natural build and recovery go through `Quirks.addMeterPaced(p, delta)` (paced by the spec: toward the danger
  x `buildMult` (x `suitBuildMult` in the full set), away from it x `recoverMult`); deliberate costs, vents, resets stay on
  `Quirks.addMeter` / `setMeter` (raw). `QuirkMeters.pace(p, delta)` paces any other value.
- **Read the band**: `QuirkMeters.band(p)` (server), `QuirkMeters.clientBand(p)`, `QuirkMeters.damageMult(p)`.
- **Push past 100**: `QuirkMeters.pushPast(p)` at 100 (false if the spec has none, under 100, already pushing or
  overloaded) -> the BEYOND band for `pushTicks`, the meter pinned at 100, HP drained (never below 1, never counted as a hit
  for Plus Ultra), then the crash. Synced as `qf_beyond`.
- Entering the danger zone plays the soft `quirk_meter_warning` to the holder, once.
- **HUD**: `QuirkHud.drawBands` draws the band tints and ticks, the fill, the sweet-spot shimmer, the red-zone pulse (fill
  toward red, box edge flashing) and past 100 a white-hot flicker with the push's time left as a sliver. The band's name
  shows on the Quirk-name line in its colour. A kit's own widget replaces the bar:

```java
@Override
public boolean drawMeterWidget(GuiGraphics g, Player p, QuirkHud.MeterView v) {
    drawThermometer(g, v.x(), v.y() - 6, v.w(), v.h() + 6, v.value());
    QuirkHud.drawBands(g, v);        // reuse the shared ticks / shimmer / pulse on any box
    return true;
}
```

  Other HUD hooks: `QuirkClientKit#holdCharge(player, slot)` (0..1 rings that key box), `drawStatus` (a whole status block,
  as One For All does).

## 6. Meter pacing (the user's rule)

"Steady heavy use" is scripted per kit from its own config (`QuirkMeterSpec.HeavyUse`): the kit's sustained move running
the whole time plus an attack every 2 s, the next ready one in rotation (worst case: no recovery during use, except a
SPENDS meter's refill). `QuirkFrameworkGameTests.heavyUseNeverReachesTheRedZoneWithin45Seconds` drives that script through
the real paced channel for every kit Quirk and fails if any reaches its danger zone inside 45 s of game time (and checks
three minutes of it does get there, so the meters still matter). `restingBringsEveryMeterBackToSafeQuickly` checks the kit's
own out-of-action recovery (after its delay) gets from the danger threshold to safe in at most 15 s.

| Quirk | Heavy-use script | Build mult | Time to the danger zone, before -> after | Recovery mult | Danger -> safe at rest, before -> after |
|---|---|---|---|---|---|
| Hellflame (Heat, red 80) | sprint flight 5/s + Spider, Jet Burn, Barrage, Pillar, Vanish, Wall, Mesh | **x0.12** | 6 s -> **52 s** | x1.0 | 80 -> 40: 11.5 s (4/s vent after 1.5 s), unchanged |
| Engine (Heat, red 80) | Gear 3 sprint + the nine kicks | **x0.30** on top of the suit factor (0.6 out of the suit, 0.5 in it) | 10 s (v0.16.7) / 16 s (with the v0.17.1 x0.6) -> **52 s** | **x1.4** | 80 -> 30 idling in gear: 20 s -> **14.3 s** (out of gear 8.3 -> 6.0 s) |
| Dark Shadow (Shadow, red 75) | pitch dark (5/s) + Strike, Fang, Grab, Fetch, Scout, Nova | **x0.20** | 10 s -> **51 s** (no moves: 15 -> 75 s) | x1.0 | 75 -> 30 in light: 4.5 s, unchanged |
| Erasure (Dry Eyes, red 90) | a held stare (10/s); v0.17.1 Erasure build: + a 3 s Reel Lock (5/s) every time Capture Bind is ready | **x0.18**; Erasure build: **x0.15** | 9 s -> 50 s; Erasure build: **49 s** | x1.0 | 90 -> 50: 3.3 s, unchanged |
| Explosion (Arm Strain, danger 70) | AP Shot, Punch, Dash, Cluster, Auto-Cannon, Blast Rush, Stun (Sweat unpaced) | **x0.28** | 12 s -> **50 s** | x1.0 | 70 -> 30: 7.7 s (7/s after 2 s), unchanged |
| Zero Gravity (Nausea, red 85) | herself + 3 mobs + 5 rubble afloat (ramping x2.5 over 30 s) + touches; v0.17.1 ZG build: + Gravity Bomb (6), Weightless Push (1.5), the Gunhead throw (3) in the rotation | **x0.20** (Uravity suit x0.75 more) | 14 s -> **52 s**; ZG build: **51.5 s** | x1.0 | 85 -> 50: 4.4 s, unchanged |
| Overhaul (Focus, SPENDS, danger <= 25) | the eight costed moves, 12/s refill between | x1.0 | never -> never | x1.0 | 25 -> 80: 4.8 s, unchanged |
| Overhaul, wave 3 (**Matter**, SPENDS) | every Matter move in rotation (spends 6-45, gains -5 / -8 / -10), the 10/s stone refill between | x1.0 | never (lowest ~41) | x1.0 | 25 -> 80 on stone: 5.7 s |
| Dark Shadow, wave 3 | + Shadow Lunge 3, Abyssal Rampage 5, Shadow Toss 4 | x0.20 | **51 s** (3 min: reaches it) | x1.0 | 4.5 s, unchanged |
| Engine, wave 3 | + the Charged Kick (4.5 heat) | x0.30 | **54 s** (3 min: reaches it) | x1.4 | 14.3 s, unchanged |

(The wave-3 rows were checked with `scratchpad/sim_quirk_kits3_pacing.js`, a copy of the gametest's loop; CI runs the real
one. `QuirkFrameworkGameTests.theDefaultUltimateIsTheFrameworkPlaceholder` now stands a default-ultimate kit in for Overhaul,
whose own ultimate is real.)

Engine is not double-cut: the user's x0.6 (out of the suit) stays as the kit's own factor and is part of the "before"
16 s; the framework's x0.30 is only the part still needed to reach the shared target (effective x0.18 of the v0.16.7 rate
out of the suit, x0.15 in it). Its cooling got x1.4 so the red-to-safe rest stays under 15 s. `EngineConfig.SHIFT_COOLDOWN`
is not touched here (master has it at 0).

What changed in feel: per-move costs are much smaller now (Hell Spider ~1.2 Heat, an AP Shot ~1.7 strain), sustained moves
dominate, and Prominence Burn no longer overheats in 3 s (about 30 Heat over its 8 s maximum). The kit builds can retune
their own per-move numbers against the same test.

## 7. Combo routes (`QuirkCombos`)

```java
public static QuirkCombos.Route STUN_SHOT = QuirkCombos.route(Quirk.EXPLOSION, "stun_shot",
        ExplosionConfig.STUN_GRENADE, ExplosionConfig.AP_SHOT).window(24).bonus(1.5f).register();

QuirkCombos.used(p, ExplosionConfig.STUN_GRENADE);                 // where the opener fires
QuirkCombos.Route r = QuirkCombos.used(p, ExplosionConfig.AP_SHOT);  // where the finisher fires (null: no route)
float damage = base * QuirkCombos.mult(r);

QuirkCombos.route(Quirk.ERASURE, "bind_spin", ErasureConfig.BIND, ErasureConfig.SPIN).sameTarget().register();
QuirkCombos.hit(p, target, ErasureConfig.SPIN);                    // same-target routes report the creature
```

A landed route: the combo popup (the route's name in the Quirk's colour, its `.desc` under it, wrapped to 220 px),
`quirk_combo`, a ring at the user and +3 Plus Ultra. While an opener's window is open the finisher's key box flashes. Lang:
`projecthero.quirk.combo.<quirk>.<id>` and `.desc`. Routes are listed automatically in the Quirk's guide chapter. Starter
routes registered (moves not yet wired -- the kit builds do that): Explosion `stun_shot`, Erasure `bind_spin`.

## 8. Costume set perk and tells

- `QuirkPerks.fullSet(player)` (client-safe): holds a kit Quirk and wears that Quirk's own full suit. The framework calls
  `QuirkKit#onSuitChanged(p, fullSet)` when it changes (checked every 10 ticks), so a kit switches its perk on / off.
  Numeric meter perks are one spec line (`suitBuild`): Uravity's -25% Nausea is live. Ingenium's half heat stays in the
  Engine kit. The other doc perks (Bakugo's gauntlet bank, Endeavor's +15% headroom, Aizawa's goggles, Overhaul's mask,
  Tokoyami's cloak) are the kit builds'.
- Tells: `QuirkTellLayer` (every player renderer) calls `QuirkClientKit#renderTell(...)`; return true to draw your own.
  The generic tell: a soft pulsing glow at both hands in the Quirk's colour while a BUILDS meter is in its danger zone or
  pushed past 100, and while C is held for Plus Ultra.

## 9. Sounds

`quirk_whoosh` (2 takes), `quirk_impact` (2), `quirk_impact_heavy` (sub-bass thump), `quirk_charge_up` (one-shot rise, the
Plus Ultra hold), `quirk_charge_loop` (seamless 2 s hum for long holds), `quirk_ultimate_sting`, `quirk_meter_warning`,
`quirk_combo`, `quirk_ultra_ready`. Synthesised by `scratchpad/synth_quirk_framework_sounds.js` (mono, needs an ffmpeg with
libvorbis: ffmpeg's built-in `vorbis` encoder is stereo-only and Minecraft needs mono for positional sound; the copy in
`%LOCALAPPDATA%\Medal\recorder-*\ffmpeg7.exe` has it). A kit adds `quirk_<kit>_*` events the same way: a `QuirkSounds`
constant, a `sounds.json` entry, a `subtitles.projecthero.*` lang line. Play with `QuirkSounds.play(level, pos, sound, vol,
pitch)`. (Wave 3 kept its constants in its own packages -- `OverhaulSounds`, `DarkShadowSounds`, `EngineSounds`, registered
from each kit's init -- so parallel kit builds never edit the same file; generator `scratchpad/synth_quirk_kits3_sounds.js`.)

## 10. State and resets

Static per-player state: `PlusUltra` (gauge cache, last combat, hit credit, hold, swallowed release), `QuirkCombos`
(marks, last route), `QuirkMeters` (danger-entry flag), `QuirkPerks` (suit flag). All cleared by `ServerStateReset` ->
`QuirkMod.clearSessionState` -> `QuirkFramework.clearSessionState`, and per player on revoke / death / logout / dimension
change through `Quirks.forget` -> `QuirkFramework.forget`. The Plus Ultra gauge itself survives those (it is saved in the
synced vals); revoke and respawn clear it with the rest of the Quirk state.

## 10b. Kit-build extensions (v0.17.1 Erasure + Zero Gravity build, all backwards-compatible)

- **`QuirkKit#holdIdOf(slot)`** (default null): a key's HOLD move beside its tap and Shift moves (Erasure: hold R Reel Lock, hold X
  Scarf Swing; Zero Gravity: hold X Float Self, hold V Catch). The guide lists it as "Hold R" (`projecthero.guide.quirks.hold_key`).
  The kit tells a tap from a hold itself (press / release timing: a press-time map per player, cleared in `forget` /
  `clearSessionState`); C's hold at 100 stays the framework's.
- **`quirk/QuirkThreat.add(boss, attacker, amount)`**: credits threat on a boss's shared `BossThreat` (or the Oathbreaker's
  table) for moves that do no damage (a stare, a bind, a lift), looked up by the boss's public `threat()` method.
- **`GeoTerrain.commit(level, pos)`**: drops a lifted cell from the ledger without refilling it (Zero Gravity's Crane sets a
  touched block down elsewhere for good).
- **Kit sounds** live in the kit's own package (`ErasureSounds`, `ZeroGravitySounds`, registered from the kit's init) so
  parallel kit builds never edit `QuirkSounds`; the `sounds.json` entries are one appended block per kit.
- **Whole-body turns** for Zero Gravity are its own mixin (`ZeroGravityBodyTurnMixin`), like `ErasureSpinMixin`, leaving the shared
  `QuirkBodyTurnMixin` to the Engine / Explosion builds.

## 11. Guide

The Quirks overview has three new sections (Meter bands, Plus Ultra, Combo routes); every Quirk chapter lists its bands
(from the spec), its Plus Ultra ultimate and its combo routes.

## 12. Screenshots

`docs/screenshots/v0171/quirk_framework_*.png` (harness: `scratchpad/QuirkFrameworkHarness.v0171.java.txt`, enabled by
`run/quirkfw.flag`).
