# v0.13.22 mutation revamp — Batch D

Powers: **10 Telekinesis**, **11 Teleportation**, **15 Invisibility / Light**, **19 Shadow Manipulation**,
**23 Gravity Manipulation**, **26 Magnetic Manipulation**.

Every power now has 8 abilities (R, G, X, Z, V, C + the utility slots H / N). Global tuning versus v0.13.21:
roughly **+20% damage**, **−15% cooldowns**, **+15% resource-bar capacity**. Every move plays a synced body
animation (`MutationVisuals.play/ensure/stopIf`), and each power has at least one visual identity (overlay, entity
or particle language) that every nearby player sees.

Code map:

| What | Where |
|---|---|
| Ability handlers | `hero/power/p10`, `p11`, `p15`, `p19`, `p23`, `p26` |
| Entities / item / world rules | `hero/revamp/d/` (`BatchDContent`, `MirrorImageEntity`, `ShadowServantEntity`, `HardLightBladeItem`, `BatchDFx`) |
| Flags, HUD meters, world tick, cleanup | `hero/revamp/RevampBatchD` |
| Poses, overlays, renderers, emissive item model | `client/mutation/RevampClientD`, `client/mutation/d/` |
| Held-item hiding while cloaked | `client/mixin/ItemInHandLayerCloakMixin` (new, registered in `projecthero.client.mixins.json`) |
| Textures | `scratchpad/build_batch_d_textures.js` → `textures/entity/mutation/p19_*`, `p23_gravity_field`, `p26_field`, `textures/item/hard_light_blade` |
| Lang | `scratchpad/lang_v01322_d.js` |
| Tests | `gametest/RevampBatchDGameTests` |

---

## 10 Telekinesis — signature: juggling

Up to **3** grabbed objects (creatures, dropped items, plucked blocks) orbit the telekinetic at chest height
(radius 2.4, each costs 0.45 Psi/tick to hold). Thrown objects are tracked for 2.5 s and hit the first creature
they reach. **Psi** is the fuel: 1150 max (was 1000), 1.1/tick regen after a 1 s hold-off, 10 s burnout at empty
(burnout also drops the orbit gently).

| Key | Move | Numbers |
|---|---|---|
| R | Force Push (Sneak: Force Pull) | 12 dmg + shove, 3-block splash, 50 blocks, 0.85 s cd, 35 Psi (pull 25) |
| G | Telekinetic Barrier (toggle) | immune while up; 1.6 Psi/tick + 4 Psi per damage point |
| X | Psychic Flight (toggle) | 0.4 Psi/tick, stops at a soft floor |
| Z | Psychic Detonation (hold 5 s) | pulls everything in 50 blocks, then 66 dmg; 76 s cd; 320 Psi; regen ×0.2 for 10 s |
| V | Telekinetic Grab | aimed creature (55 Psi) or item (20 Psi) joins the orbit; orbit full / nothing grabbable → throws the orbiter nearest your aim (creature 14 + 8 to itself, item 9, block 16); Sneak: set the orbit down gently, or with an empty orbit Force Crush (12 dmg/s, 2.5 Psi/tick, 8 s); 0.75 s cd |
| C | Block Manipulation (hold) | hold = lift & steer, release = throw; **quick tap (≤7 ticks) plucks the block into the orbit**; Sneak = 3×3 chunk (31 on impact) |
| H | Launch Orbit | throws the whole orbit at the crosshair, +20% damage each; 30 Psi; 3 s cd |
| N | Mind Lock | aimed creature frozen 1.5 blocks up for 4 s (no AI target, Slowness/Weakness X, drifts down after); no bosses (>200 HP); 90 Psi; 12 s cd |

Visuals: **purple glowing eyes** overlay (`p10.eyes`) whenever anything is being channelled or held (orbit,
lock, crush, block, detonation charge, barrier, flight); purple Psi dust on everything held, tethers, rings.
Poses: `p10.barrier`, `p10.detonate_charge` (loop), `p10.detonate`, `p10.set_down`, `p10.crush` (loop),
`p10.launch`, `p10.mind_lock`, plus library casts/throws.
HUD: Psi METER (always), burnout HAIRLINE, Orbit GAUGE (0–3), Detonation SLAB, Force Crush HAIRLINE.

## 11 Teleportation — light pass

All jumps keep the `SafeTeleport` checks. New visual language: **purple smoke puff** at every departure and arrival
plus a **particle body-silhouette afterimage** at the departure point.

| Key | Move | Numbers |
|---|---|---|
| R | Blink (hold to aim, release; Sneak: Phase Jump) | 75 blocks; 2.55 s cd |
| G | Target Teleport | behind the target (falls back to either side / in front if unsafe), faces it; 6 s cd |
| X | Escape Blink | 9.5 blocks back + Resistance III 1.2 s; 3.4 s cd |
| Z | Portal (hold 5 s → picker screen) | unchanged; 51 s cd |
| V | Teleport Mark / Recall | unchanged, 17 s cd after recall |
| C | Portal Anchor | unchanged, 2.55 s cd |
| H | **Bamf Strike** | appear behind the aimed target (30 blocks) and hit for 11, then every 6 ticks hop behind the nearest un-hit enemy within 10 blocks, up to 4 targets; 12 s cd |
| N | **Swap** | trade places with the aimed creature or a squadmate (40 blocks); both spots must be safe for both bodies; enemy gets 1.5 s Slowness II; no bosses; 6 s cd |

Visuals: violet eyes overlay (`p11.charge`) while a portal charges / a blink is aimed / a Bamf chain runs.

## 15 Invisibility / Light — signature: refraction

Sunlight: +10% damage, +20% range (unchanged). Night vision passive unchanged.

| Key | Move | Numbers |
|---|---|---|
| R | Light Blast (hold to charge; Sneak: 5-blast volley) | 10 dmg + 4 s blind; +7/s charged (max 3 s); 0.85 s + 0.85 s/s cd; volley 9.35 s cd |
| G | **Flash** (Sneak: Radiant Lance) | 8-block burst: 12 dmg, 10 s Blindness + Slowness II, mobs drop target; 8.5 s cd. Lance: 24 dmg piercing, blind + glow; 10.2 s cd |
| X | Sparkling Flight (hold) | bar 115 (was 100), ~29 s; 2.55 s cd |
| Z | Holy Light (hold 5 s, 3 s channel) | 14 beam + 29 burst every 10 ticks; 64 s cd |
| V | **Mirror Images** | 2 images (3 in sunlight) with your skin + illusion copies of your gear, scatter & wander, lure hostiles that were after you (65%/0.5 s within 16 blocks), you vanish 1.2 s on cast; any hit shatters an image and blinds the attacker 3 s; 10 s life; 16 s cd |
| C | Cloak (toggle) | full invisibility; armour **and held items** hidden for every viewer; attacking reveals 2 s |
| H | **Hard-Light Blade** | conjured into your hand for 30 s: 9 attack dmg, 1.8 speed, glowing hits; emissive model; vanishes on slot switch / drop / container open / death / expiry; press again to dismiss; 20 s cd from when it fades |
| N | **Prism Shield** (hold) | frontal projectiles within 5 blocks reversed back (`ProjectileDeflection.REVERSE`, +20% speed); frontal ranged/beam damage negated and 60% reflected into the sender; melee unaffected; −35% move speed; own Prism bar 115 (0.7/tick + 8 per reflect), regen over 12 s |

Visuals: the **prism pane** overlay (three stacked emissive panes cyan / magenta / gold in front of the body),
a soft light halo on the blade hand, gold/prism dust everywhere; the Mirror Image renderer uses the owner's real
player skin (wide/slim). Poses: `p15.holy_charge` (loop), `p15.cloak`, `p15.refract`, `p15.blade_summon`.

Safety rules for conjured things (see `BatchDContent`): any blade or illusion-stamped stack that appears as an item
entity is deleted on `ENTITY_LOAD`; `ALLOW_DEATH` purges blades; blades can't be used on item frames / armour stands
/ allays; images and servants never save, never drop loot or XP.

Fix along the way: `hideArmor` now also reads the all-viewer `MutationVisuals` flags (`p15.cloak`,
`p19.shadow_walk`) — the old check read the owner-only experimental state, so other players still saw a cloaked
player's armour.

## 19 Shadow Manipulation — signature: shadow travel

Light tier unchanged (50% bright / 75% indoor / 100% dark / 130% Deep Dark) scaling damage and most cooldowns.

| Key | Move | Numbers (at 100% tier) |
|---|---|---|
| R | Shadow Bolt (Sneak: 5-bolt fan) | 13 dmg + 4 s blind; 1.7 s cd; volley 12.75 s |
| G | Shadow Tendrils (Sneak: rooting cone) | 18 dmg + 8 s blind; 6.8 s cd; cone roots 8 s, 17 s cd |
| X | Shadow Step | 75-block blink, 6 dmg at landing; 2.55 s cd (Sneak dark-seek 6.8 s) |
| Z | Shadow Zone (hold 5 s) | 20-block zone, 11 s, 10 dmg/s; 51 s cd |
| V | **Shadow Bind** (Sneak: Shadow Grab) | aimed target + up to 2 others within 2.5 blocks: 9 dmg, rooted + Weakness II 5 s (×tier), blind ≤3 s, spiralling chain particles; 10 s cd (÷tier) |
| C | Shadow Form: Shadow Cloak / Sneak: stealth Form | +12 ability / +8 melee, slow/blind aura, bar 115 (~40 s), 17 s cd |
| H | **Shadow Walk** (toggle) | sink into a moving shadow puddle: invisible (armour + items hidden for all), Speed III, no fall damage, 50% damage taken, mobs drop aggro; can't melee (attack cancelled) and any attack ability surfaces you; bar 115 ≈ 10 s (×1.3 indoor, ×2 bright), regen 25 s |
| N | **Shadow Servant** | 20 s humanoid shadow minion: 30 HP, 8 dmg × tier, targets your attacker → your victim → nearest hostile; never targets/attacks you, your squad (`Squads.areAllies`), your pets, your other shadows/images, or players unless PvP ability damage is on; owner + squad can't hurt it; one at a time; 30 s cd |

Visuals: **Shadow Form** = THICK black silhouette shell (`p19_shadow_form.png`) + glowing purple eyes; the servant
renders as a black silhouette with emissive purple eyes (`EyesLayer`); the walk puddle is a black dust disc with
flickering violet eye-motes. Poses: `p19.gather` (loop), `p19.cloak`, `p19.sink`, `p19.emerge`.

## 23 Gravity Manipulation — signature: changing which way gravity pulls

| Key | Move | Numbers |
|---|---|---|
| R | Gravity Push (Sneak: grab / throw) | 12 dmg, 50 blocks, 1.7 s cd; throw 10 |
| G | Gravity Crush (hold; Sneak: small Gravity Well) | 5 dmg/s up to 8 s, 17 s cd; well 2.5 dmg/s 6 s |
| X | Zero-G (toggle; Sneak also repulses) | −85% gravity; repulse 8.5 s cd |
| Z | Black Hole (hold 5 s) | 30-block pull, 7 dmg/s inside 10, terrain damage, 15 s; 102 s cd |
| V | Gravity Lift (Sneak: slam) | mark 10 targets 20 s; slam 17 dmg; 21 s cd |
| C | Gravitational Nexus (Gravity Field) | +12 ability / +8 melee, Speed II, Resistance I, 3-block Slowness IV aura 2.5 dmg/s; bar 115; 17 s cd |
| H | **Invert** | aimed target falls UP for 1.5 s (6 dmg if it hits a ceiling), then slams down at ≥1.2 blocks/tick: 10 dmg + fall damage, 5 dmg to others within 3; no bosses; 9 s cd |
| N | **Heavy Ground** | 7-block circle at aim (24 blocks) for 8 s: no jumping, Slowness II, 3 dmg/s, anything airborne up to 14 blocks above (flying mobs included) dragged down at ≥0.6/tick; 17 s cd |

Visuals: **thin translucent violet shell** (`p23_gravity_field.png`, pulsing) while Gravity Field is on, dark violet
eyes while a black hole charges, dark-purple distortion dust + reverse-portal everywhere, falling obsidian-tear
"rain" in Heavy Ground. Poses: `p23.crush` (loop), `p23.well_charge` (loop), `p23.lift`, `p23.invert`.

## 26 Magnetic Manipulation — light pass

All magnetic impacts ×1.2 (Ferrous Shot, grip throws, Metal Storm with the per-target cap raised 30 → 36,
Magnetic Crush). Copper/gold/Mjolnir exclusions untouched (`MagneticMaterials`).

| Key | Move | Numbers |
|---|---|---|
| R | Ferrous Shot | mass-scaled ×1.2 (≈7 / 11 / 22 / 34); 2.55 s cd |
| G | Magnetic Grip | 1.25 s between grips |
| X | Polarity Leap | 1.7 s cd |
| Z | Metal Storm | 15 s cd; cap 36 per target |
| V | Magnetic Crush | (3 + 4/piece) ×1.2; 7.6 s cd |
| C | Magnetic Sense | unchanged |
| H | **Magneto Hover** (toggle) | creative-style flight while a magnetic block is within 2 horizontally / 6 below / 1 above, or iron is held; drift down (Slow Falling) without a source; bar 115 ≈ 15 s, 20 s regen; landing cushioned 3 s |
| N | **Disarm** | aimed creature (20 blocks): rips the first magnetic item (main hand, off hand, chest, head, legs, feet) and drops it as a real item flung toward you (moved, never copied); 4 dmg unless netherite; players only with PvP ability damage on; no metal → no cooldown; 10 s cd |

Visuals: emissive steel-blue **field-line shell** (`p26_field.png`) while hovering, sparks from the metal source
to your feet; poses `p26.clench`, `p26.sense`.

---

## Unverified in-game (needs a client playtest)

* All poses and overlays were only compile-checked — frame values were written by hand; the prism pane's size /
  offset, the shell tints and the blade halo may need nudging.
* The **emissive Hard-Light Blade** relies on the Fabric Renderer API (Indigo, or Sodium's FRAPI). Without a FRAPI
  renderer it renders normally lit.
* Mirror Image skins come from the client's player list (`PlayerInfo`); if the owner isn't in the tab list the
  default skin is used.
* Orbiting creatures are repositioned server-side every tick; living orbiters use normal entity interpolation and
  may look slightly jittery at high ping. Items / blocks get explicit teleport packets.
* Magneto Hover grants vanilla flight (`mayfly`) — it cooperates with creative / Thor / HeroFlight by never touching
  the flag while one of those owns it, but a second mutation that also toggles `mayfly` (Light's Sparkling Flight)
  can end the other's flight if both are used at once.
* Prism "beam" reflection is heuristic: projectiles, magic, sonic boom, lightning, dragon breath, or any hit whose
  direct source is more than 4 blocks away counts as a beam.
