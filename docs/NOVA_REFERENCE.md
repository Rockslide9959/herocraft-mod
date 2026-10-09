# Nova (Richard Rider) -- reference (v0.15.13, retuned v0.15.15)

Hero-Tier **Primary** power. Every number lives in `com.projecthero.mod.nova.NovaConfig` (static finals); keep this
file in step with it.

## Getting it

- **Crashed Nova Corps Pod** (`projecthero:nova_pod_site`): a rare surface structure -- a scorched 15-block impact bowl
  with a long, low Nova Corps fighter lying in it at an angle (15 blocks long, 5 wide): gold nose cone ploughed into
  the floor, blue fuselage with sloping shoulders and a gold spine stripe, a glass cockpit canopy and windscreen, a gold
  Nova star on each flank, the hatch torn open, two swept tail fins and a dorsal fin, a glowing engine with a smouldering
  exhaust, and a 12-block skid furrow of scorched earth and hull plates running out of the crater behind it. Biomes =
  the Mjolnir crater list (`#projecthero:nova_pod_site_biomes`); placement `random_spread`, spacing 110, separation 40,
  frequency 0.5 (about as rare as the Kryptonite crater). `/locate structure projecthero:nova_pod_site`.
- **The dying Centurion** (`projecthero:nova_centurion`) sits slumped against the open hatch: not hostile, never moves
  (turns his head to you), cannot be hurt (only `/kill`), never despawns. Within 8 blocks he mutters a line, at most once
  every 10 s per player. **Right-click** him: he hands over the **Nova Corps Helmet** and fades away in golden light over
  2 s. Someone who already carries the Nova Force gets a line instead and he stays.
- **Nova Corps Helmet**: right-click to take up the Nova Force (used up; kept in creative). Replaces whatever Primary
  power you held (the one-Primary rule, `HeroTiers.claimPrimary`).
- Creative tab: the helmet and the Centurion spawn egg. Commands: `/projecthero power grant nova [player]`,
  `/projecthero power revoke ...`, `/projecthero nova force <0-100>`, `/projecthero nova site` (builds a pod with its
  Centurion 14 blocks in front of you). The random Heroic serum can roll Nova like any other hero.

## The uniform (H)

- **H** puts the Nova Corps uniform on / takes it off (10-tick debounce; Shift+H still opens the power wheel).
- Suit-up (v0.15.15, **46 ticks**): the **Nova Corps Helmet** (the suit model's own head + helmet layer) materialises in
  both hands in front of the chest, is raised overhead (ticks 0-12) and lowered onto the head (12-22, the arms aimed at
  it the whole way, the head levelled so it settles square; a flash and a clank as it lands), then the rest of the uniform
  materialises **from the neck down** (22-46) with a bright gold scan line on the newest texel rows and a gold helix
  running down the body. Suit-down (**38 ticks**): the body dematerialises from the feet up to the neck (0-16), the hands
  come up to the helmet (16-21), lift it off overhead (21-31) and it dissolves there in gold (31-38).
  Timing: `NovaConfig.SUIT_*`; the client keyframes are `NovaSuitRender.anim` (+ `NovaPose.holdHelmet`).
- The uniform is not an item: drawn from the synced state as a second wide player model a hair over the wearer (base
  +0.04, outer +0.29, helmet +0.54). The wearer's own skin overlay and worn armour are hidden while it is on (the armour
  still protects). Transparent texels stay transparent, so the wearer's face shows in the helmet opening.
- Emissive: the cyan chest star and eye lenses (139, 248, 255) glow; during NOVA OVERLOAD the whole suit glows gold.
- Moves, flight and passives work **only while suited**. The uniform comes off on respawn.

## Nova Force (Slab bar)

- 0-100, starts full. Refills **3 a second**; **half that (1.5/s) while flying**.
- Flying faster than 25 b/s (i.e. sprint flight) also drains **2 a second** (net -0.5/s).
- After a NOVA OVERLOAD the bar is **empty** and refills at **half speed for 60 s** (1.5/s, 0.75/s flying -- the two
  slowdowns multiply). The HUD shows it: the bar dimmed, a red line along its bottom draining away, "slow refill Ns".
- During the Overload the bar is **infinite** (shown full and pulsing, "OVERLOAD: infinite Force N s"); it does not
  refill underneath.
- The HUD Slab bar outline turns cyan when full (NOVA OVERLOAD ready).

## Passives (while suited)

- **+8 melee damage** (an `ATTACK_DAMAGE` modifier, `projecthero:nova_melee`).
- **Diamond-level suit armour** (+20 armour, +8 toughness attribute modifiers while suited) and a **20% damage reduction** (was 60% before v0.15.19) on everything except `/kill` / the void.
- **No fall damage** (nor flying into walls).
- **Worldmind**: every mob within a **32-block sphere** is outlined gold -- through walls, for the Nova alone (viewer-only,
  never a glowing flag).

## Flight

Double-tap jump in the air while suited (again to drop out; touching the ground lands you). Shared directional flight
(`DirectionalFlightModel.nova`): **20 b/s** cruising, **45 b/s** sprinting (steady state), 12 b/s straight up/down,
strafe 80%. The golden trail (v0.15.15) is drawn client-side (`NovaEffectsRenderer`): a tapering gold ribbon with a hot
core and a cyan thread from the **interpolated feet** back through the last 11 ticks' positions, plus a few sparks. The
flight lean pivots round the soles (`PlayerRendererMixin`), so the feet are the entity position in every pose --
sprint-flying flat out included.

## Moves

| Key | Move | Effect | Cost | Cooldown |
|---|---|---|---|---|
| R (hold) | **Nova Blast** | Golden beam from the hand, 32 blocks, **hits the instant it touches, then 5 dmg every 5 ticks per target (20 dmg/s; players 10 every 10 ticks); hitbox +0.6** (v0.15.19); **no time limit** -- fires while held until the Force runs out | 6/s (needs 3 to open) | 1.5 s after release |
| Shift+R | **Nova Bolt Volley** | **5 homing bolts, 8 dmg each** (seek the enemies nearest the crosshair, 24 blocks, 3 s life) | 20 | 6 s |
| G | **Gravimetric Pulse** | **6-block** shockwave round you, **20 dmg** (70% at the edge), knock-up 0.9 | 20 | 8 s |
| Shift+G | **Gravity Slam** | From 2+ blocks up: dive, then an **8-block** crater shockwave, **10 + 1/block dropped, max 30** (at 20 blocks); particles only, no blocks broken | 25 | 12 s |
| Z (hold) | **Force Field** | Golden **ForceBubble** (1.8 blocks) **for as long as Z is held**: absorbs every projectile (deleted on entry) and every melee blow (attacker pushed back); flickers when under 8 Force | **8/s** while up (needs 8 to raise; free in the Overload) | 1 s after release |
| Shift+Z | **NOVA OVERLOAD** (ultimate) | Needs a **full bar** and takes all of it: **15 s** of **double damage** on every move, **infinite Nova Force** and halved cooldowns, then a **30-dmg nova burst** (10 blocks). When it ends the bar is set to **0** and refills at **half speed for 60 s** (also if the uniform comes off mid-Overload, without the burst) | 100 (all) | **75 s** (from activation) |
| X | **Comet Dash** | **20-block** ram along the look (flat on foot, any direction in flight), **20 dmg** to everything within 1.5 blocks of the path, once each | 15 | 6 s |
| Shift+X | **Orbital Launch** | Grab the nearest hostile mob within 8 (not players, not bosses), climb **30 blocks** with it at 1.5 b/tick, spike it down at 3 b/tick: **20 dmg on impact + the fall**, 6 dmg splash (3 blocks); you hover at the top | 30 | 20 s |
| C | **Gravity Well** | Singularity at the crosshair (24 blocks): **3 s** pulling mobs within 8 in, then collapses for **35 dmg** (4 blocks) | 25 | **25 s** |
| Shift+C | **Gravity Lock** | Every mob within **10** lifted **3 blocks** and frozen (Slowness X / Weakness X) for **4 s**, then dropped | 35 | 20 s |
| V | **Worldmind Scan** | 48-block pulse: everything found outlined cyan for **10 s** (Nova only); the strongest (highest max health, hostiles first) marked red and takes **+25% from every source** | 15 | 20 s |
| Shift+V | **Nova Force Transfer** | Heal yourself and every squadmate within 8 for **6 hearts** | 40 (40%) | 20 s |

Bosses (max health >= 300, or a Titan-class boss) take at most 8% of their max health per hit and are never knocked
back, grabbed, lifted or pulled. Every move uses `HeroTargets`: never yourself, squadmates, squadmates' pets or your own
pets; other players only with PvP on. Gravity moves only ever move mobs, never players.

## The Force Field bubble (shared)

`com.projecthero.mod.shield.ForceBubble` (server: `blocks(source)`, `absorb(...)`, `tick(...)`, `raise` / `drop`) and
`client.shield.ForceBubbleRenderer.draw(...)` (a fresnel-rim shell, a slowly turning latitude / longitude lattice and
three spinning rings, one accent-coloured; drawn fainter for the wearer). Styles: `ForceBubble.Style.NOVA` (gold,
cyan accent) and `Style.GREEN_LANTERN` (green) -- built so Green Lantern's Z shield can switch to the same bubble.
Nova keeps the "is it up" state (`NovaState.shieldUntil = Long.MAX_VALUE` while Z is held, 0 when let go) and the upkeep.

## HUD

Bottom right: NOVA, the Nova Force **Slab** bar (9 px, border, numbers inside; full and pulsing with "OVERLOAD: infinite
Force N s" while it runs; dimmed with a draining red line and "slow refill Ns" during the 60 s after), and
six boxes R G Z X C V (moved up above the hotbar / health / food / air rows whenever the GUI is too narrow for them to sit beside the hotbar). Each box shows the tap move's cooldown, or with Shift held the Shift move's (gold dot); a strip
along the bottom shows the other move's readiness; a red strip = not enough Force. Shift or Left-Alt lists the moves.
No H / N boxes.

## Poses (keyframed, every viewer)

Suit-up / suit-down (both hands on the helmet as it is raised and lowered, or lifted off), Flight (main fist forward
overhead), Nova Blast (arm along the look, other hand braced), Force Field (forearms crossed),
Comet Dash (fist forward, other arm back), Gravity Slam (fists overhead through the dive, landing crouch), Overload (arms
flung wide, head back; wider for the burst), plus short frames for volley, pulse, launch, well, lock, scan and transfer.

## Credits

Suit skin: **"Nova Richard Rider 2.0"**, The Skindex skin #17170737 (teal Nova Prime style), with its gold brightened to
match skin #21825678 and the bare-skin texels (mouth / chin, head underside) made transparent -- chosen by the user.
Source files: `scratchpad/nova_skins/`.

## Code map

- `nova/` -- `Nova` (API, suit, force, lifecycle), `NovaAbilities` (the twelve moves), `NovaAbilityManager` (slots),
  `NovaFlight`, `NovaCombat`, `NovaDamage`, `NovaConfig`, `NovaCommand`, `NovaMod`; `data/NovaState` (attachment
  `projecthero:nova_state`); `item/` (helmet, registry), `entity/NovaCenturionEntity`, `worldgen/NovaPodSite*`,
  `network/` (`NovaActionPayload` C2S H / flight, `NovaScanPayload` S2C to the caster only).
- `client/nova/` -- `NovaSuitRender` (model, reveal frames, glow), `NovaSuitLayer`, `NovaHud`, `NovaPose`,
  `NovaEffectsRenderer` (beam, Force Field bubble, well, flight trail), `NovaWorldmindClient`, `NovaCenturionRenderer`,
  `NovaClient`.
- `shield/ForceBubble` + `client/shield/ForceBubbleRenderer` -- the reusable bubble shield (v0.15.15).
- Mixins: `NovaWorldmindGlowMixin`, `NovaWorldmindColorMixin`, `NovaFirstPersonArmMixin`; hooks in `PlayerModelMixin`,
  `HumanoidArmorLayerMixin`, `HumanoidModelMixin`, `LevelRendererHighlightMixin`.
- Tests: `NovaGameTests`.
