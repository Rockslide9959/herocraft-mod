# Nova (Richard Rider) -- reference (v0.15.13)

Hero-Tier **Primary** power. Every number lives in `com.projecthero.mod.nova.NovaConfig` (static finals); keep this
file in step with it.

## Getting it

- **Crashed Nova Corps Pod** (`projecthero:nova_pod_site`): a rare surface structure -- a scorched 15-block impact bowl
  with a gold-and-blue pod nose-down in the middle (cyan canopy, gold Nova star on each flank, glowing engine with a
  smouldering exhaust) and hull plates scattered round it. Biomes = the Mjolnir crater list
  (`#projecthero:nova_pod_site_biomes`); placement `random_spread`, spacing 110, separation 40, frequency 0.5 (about as
  rare as the Kryptonite crater). `/locate structure projecthero:nova_pod_site`.
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
- Suit-up: golden energy wraps the body from the feet up over **24 ticks** (1.2 s); suit-down unravels from the head
  over **14 ticks**.
- The uniform is not an item: drawn from the synced state as a second wide player model a hair over the wearer (base
  +0.04, outer +0.29, helmet +0.54). The wearer's own skin overlay and worn armour are hidden while it is on (the armour
  still protects). Transparent texels stay transparent, so the wearer's face shows in the helmet opening.
- Emissive: the cyan chest star and eye lenses (139, 248, 255) glow; during NOVA OVERLOAD the whole suit glows gold.
- Moves, flight and passives work **only while suited**. The uniform comes off on respawn.

## Nova Force (Slab bar)

- 0-100, starts full. Refills **4 a second, all the time**.
- Flying faster than 25 b/s (i.e. sprint flight) drains **2 a second** (net +2).
- The HUD Slab bar outline turns cyan when full (NOVA OVERLOAD ready).

## Passives (while suited)

- **60% damage reduction** on everything except `/kill` / the void.
- **No fall damage** (nor flying into walls).
- **Worldmind**: every mob within a **32-block sphere** is outlined gold -- through walls, for the Nova alone (viewer-only,
  never a glowing flag).

## Flight

Double-tap jump in the air while suited (again to drop out; touching the ground lands you). Shared directional flight
(`DirectionalFlightModel.nova`): **20 b/s** cruising, **40 b/s** sprinting, 12 b/s straight up/down, strafe 80%; a
golden trail.

## Moves

| Key | Move | Effect | Cost | Cooldown |
|---|---|---|---|---|
| R (hold) | **Nova Blast** | Golden beam from the hand, 32 blocks, **8 dmg/s** (4 every 10 ticks), up to 8 s | 6/s (needs 3 to open) | 1.5 s after release |
| Shift+R | **Nova Bolt Volley** | **5 homing bolts, 6 dmg each** (seek the enemies nearest the crosshair, 24 blocks, 3 s life) | 20 | 6 s |
| G | **Gravimetric Pulse** | **6-block** shockwave round you, **10 dmg** (70% at the edge), knock-up 0.9 | 20 | 8 s |
| Shift+G | **Gravity Slam** | From 2+ blocks up: dive, then an **8-block** crater shockwave, **6 + 0.6/block dropped, max 18**; particles only, no blocks broken | 25 | 12 s |
| Z | **Force Shield** | **4 s** golden bubble (1.8 blocks): absorbs every projectile (deleted on entry) and every melee blow (attacker pushed back) | 25 | 15 s |
| Shift+Z | **NOVA OVERLOAD** (ultimate) | Needs a **full bar** and takes all of it: **10 s** of free moves at **+50%** damage with halved cooldowns, then a **30-dmg nova burst** (10 blocks) | 100 (all) | **90 s** (from activation) |
| X | **Comet Dash** | **20-block** ram along the look (flat on foot, any direction in flight), **14 dmg** to everything within 1.5 blocks of the path, once each | 15 | 6 s |
| Shift+X | **Orbital Launch** | Grab the nearest hostile mob within 8 (not players, not bosses), climb **30 blocks** with it at 1.5 b/tick, spike it down at 3 b/tick: **20 dmg on impact + the fall**, 6 dmg splash (3 blocks); you hover at the top | 30 | 20 s |
| C | **Gravity Well** | Singularity at the crosshair (24 blocks): **3 s** pulling mobs within 8 in, then collapses for **20 dmg** (4 blocks) | 30 | 14 s |
| Shift+C | **Gravity Lock** | Every mob within **10** lifted **3 blocks** and frozen (Slowness X / Weakness X) for **4 s**, then dropped | 35 | 20 s |
| V | **Worldmind Scan** | 48-block pulse: everything found outlined cyan for **10 s** (Nova only); the strongest (highest max health, hostiles first) marked red and takes **+25% from every source** | 15 | 20 s |
| Shift+V | **Nova Force Transfer** | Heal yourself and every squadmate within 8 for **6 hearts** | 40 (40%) | 20 s |

Bosses (max health >= 300, or a Titan-class boss) take at most 8% of their max health per hit and are never knocked
back, grabbed, lifted or pulled. Every move uses `HeroTargets`: never yourself, squadmates, squadmates' pets or your own
pets; other players only with PvP on. Gravity moves only ever move mobs, never players.

## HUD

Bottom right: NOVA (OVERLOAD + seconds while it runs), the Nova Force **Slab** bar (9 px, border, numbers inside), and
six boxes R G Z X C V. Each box shows the tap move's cooldown, or with Shift held the Shift move's (gold dot); a strip
along the bottom shows the other move's readiness; a red strip = not enough Force. Shift or Left-Alt lists the moves.
No H / N boxes.

## Poses (keyframed, every viewer)

Flight (main fist forward overhead), Nova Blast (arm along the look, other hand braced), Force Shield (forearms crossed),
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
  `NovaEffectsRenderer` (beam, bubble, well), `NovaWorldmindClient`, `NovaCenturionRenderer`, `NovaClient`.
- Mixins: `NovaWorldmindGlowMixin`, `NovaWorldmindColorMixin`, `NovaFirstPersonArmMixin`; hooks in `PlayerModelMixin`,
  `HumanoidArmorLayerMixin`, `HumanoidModelMixin`, `LevelRendererHighlightMixin`.
- Tests: `NovaGameTests`.
