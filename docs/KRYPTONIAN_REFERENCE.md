# The Kryptonian (Hero-Tier power)

Package `com.projecthero.mod.kryptonian` (+ client `com.projecthero.mod.client.kryptonian`). Hero-Tier key `kryptonian`.
Added in v0.14.8. Every number is a static final in `KryptonianConfig`.

## Architecture

| Class | Role |
|---|---|
| `data/KryptonianState` | The one attachment (`projecthero:kryptonian_state`): persistent, `copyOnDeath`, synced to all. `hasPower`, `solar`, `flying`, `weakened`, `depoweredUntil`, `xray` (v0.14.16 toggle), `heatVision`, `breathing`, `lastDrain`, `animId/animStart`, `flareChargeStart`, `abilityReadyAt`. |
| `Kryptonian` | The API: state access, `grant` (claims the ONE Primary slot), `revoke`, `reconcile` (fixed-id transient modifiers from `empowered()`), the per-tick body (Solar Energy once a second, sun healing / feeding, fire + air), lifecycle. |
| `KryptonianDamage` | ALLOW_DAMAGE: /kill + void pass; weakened/burnt out = full damage; falls, fly-into-wall, fire, lava, hot floor, drowning, freezing, suffocation, cactus/berry, starving = none; everything else x0.2 (cancel-and-reissue); v0.14.16: a carried creature deals no damage and takes none from the carry, a set-down one no fall damage. AFTER_DAMAGE: melee knockback. |
| `Kryptonite` | Every 10 ticks: ore/block within a 5-block cube (chunk-section palette pre-check), shard item entities or anyone holding one within 6, a shard in his own inventory. Sets `weakened`; lingers 2 s. Weakness II + Slowness II, 1 magic dmg/s, -5 solar/s. |
| `KryptonianFlight` | Server half of flight: `mayfly/flying`, lands on ground contact (10-tick lift-off grace) or when the client drops vanilla flight, trail particles, sonic boom (position-delta speed >= 1.5 b/t, re-arms under 0.9). |
| `KryptonianAbilities` | The twelve moves (v0.14.16); one `begin()` gate (power, kryptonite, burn-out, cooldown, solar); per-player sessions for held / multi-tick moves; `clear()` / `clearSessionState()`. |
| `KryptonianAbilityManager` | Slot dispatch; Shift read server-side (`isShiftKeyDown`). `idsOf(slot)` = [plain, shift] for the HUD. |
| `KryptonianCombat` | Targets (never self, squad-mates, creative players; players only with PvP), boss cap (8% max HP, no knockback), cone, radial, crater (needs `abilityTerrainDamage`, hardness <= 5, no block entities), shake. |
| `worldgen/KryptoniteCrater{Structure,Piece}` | v0.14.13: the rare world-generated crater (Meteor Core + ore), placed like Mjolnir's crater (`structure_set/kryptonite_crater.json`, spacing 100 / separation 40 / frequency 0.5). |
| `meteor/MeteorManager` | (v0.14.13: no nightly roll any more) `summonNear`, `launch`, scheduled impacts, `impact` (crater + core + ore). |
| `meteor/KryptoniteMeteorEntity` | The fireball: moves along a straight line, particles, never saved. Purely visual. |
| `item/*`, `block/*` | Kryptonite ore / block, Meteor Core, Kryptonite Shard, Kryptonian Crystal. |
| `KryptonianMod` | One `initialize()` from `ProjectHeroMod`: items, damage, payload + receiver, JOIN / AFTER_RESPAWN / AFTER_DEATH / world change / DISCONNECT hooks, meteor tick. `clearSessionState()` from `ServerStateReset`. |
| `KryptonianCommand` | `/projecthero meteor [here]`, `/projecthero kryptonian solar <n>` (op). |

Client: `KryptonianHud` (mono style, Hairline bars only), `flight/DirectionalFlight` + `mixin/DirectionalFlightTravelMixin`
(v0.14.16 shared directional flight, local player), `KryptonianBeamRenderer` (heat vision, `BeamDraw` ribbons like `LaserBeamRenderer`),
`KryptonianPose` (from `HumanoidModelMixin`), `mixin/KryptonianXRayGlowMixin` (per-viewer outlines),
`KryptoniteMeteorRenderer` (a tumbling 2x kryptonite-ore block + vanilla fire overlay). `FlightPoseHelper` counts
Kryptonian flight as hero flight (body lean); the double-tap is in `ProjectHeroModClient.handleDoubleJump`.

## Passives (always on while `empowered`)

40 max HP (+20), 15 fist damage (+14), knockback resistance 1.0, +40% speed, +0.5 step, +1 reach, ~4-block jump,
safe-fall 1000, 80% damage reduction, the immunities above, no air loss, fire cleared. Sun: `DIRECT` (day, sky at the
eyes, no rain) heals 1 HP / 10 ticks, feeds 1 food / 10 s. (v0.14.16: the 1 HP / 40 ticks shade trickle is gone.)

**Regeneration III (v0.14.16)** replaces v0.14.15's permanent Regeneration I: `Kryptonian.tickRegeneration` (every
tick) adds one infinite, ambient, particle-less Regeneration (amplifier 2) while empowered AND health < max AND solar > 0,
and removes it the tick any of those stops being true. `tickSolar` charges 1 solar a second while our instance is on.
An old save's infinite ambient amplifier-0 instance is cleared by `reconcile`. A potion's Regeneration is left alone.

Solar Energy per second (v0.14.18, +50%): DIRECT 6, SHADE (day, no direct sun) 1.5, NIGHT 0.75, DARK (underground, Nether, End) 0.375.
**v0.14.16:** max 100 (saved values above are clamped on join and in `tickSolar`); `KryptonianState.lastDrain` is set
by every drain (`spendSolar` with a cost > 0, flight 0.1/s, Regeneration III 1/s, kryptonite 5/s, the Solar Flare).
**v0.14.17:** the refill delay is gone (`SOLAR_REGEN_DELAY` / `solarRegenPaused` removed): every second `tickSolar` adds
the sun's rate minus the running drains (flight, Regeneration III); `lastDrain` is now informational only. Gametests pin
the rate per player with `Kryptonian.setSolarGainForTests`. Flying with an empty bar drops him
(`flight_no_solar`), and the take-off payload is refused on an empty bar (`KryptonianMod`). HUD: the Solar Hairline is
always the plain gold (green near kryptonite).

## Moves (v0.14.16 layout)

| Key | Move | Numbers | Cost | Cooldown |
|---|---|---|---|---|
| R | Kryptonian Punch | 32 to the aimed target (6 blocks), pushed 3.0 along the look + 0.6 up; 12 in 3 blocks round it. Air punch: 14 in a 7-block 40-deg cone | 5 | 3 s |
| Shift+R | Thunderclap | 20-block 80-deg cone, up to 20 dmg (50% at the far end), knockback 3.0 + 0.4 up, Slowness VII + Weakness II 2 s | 5 | 8 s |
| G | Heat Vision (hold) | 32 blocks from the eyes; 4 dmg every 5 ticks (burst), 5 s fire; sets blocks alight every second (terrain damage on); max 10 s; needs 1 to open | 1/s (0.5 per 10 ticks from age 0) | 3 s after release |
| Shift+G | Ground Pound (id `ground_slam`) | grounded: slam now (75%); airborne: dive at 2.6 b/t, slam on landing (75% -> 100% after 20 ticks of dive). 30 dmg in 7 blocks, lift 1.0; 2.5-block crater (30 blocks max) | 10 | 8 s |
| Z | Freeze Breath (hold) | 12-block 60-deg cone, 5 dmg every 5 ticks, frozen + Slowness IV 6 s; source water -> frosted ice, fire out; max 6 s; `breathing` flag drives the pose | 1/s | 4 s after release |
| Shift+Z | SOLAR FLARE | needs a FULL 100, spends all of it at the start; 40-tick charge; 120 in 12 blocks (falloff to 60%), knockback 3.5, lift 1.0, 8 s fire; 4-block crater (80 max); then **powerless 30 s** (`depoweredUntil`: `empowered()` false = no moves / flight / Regeneration / damage reduction / passives / solar) with Slowness IV, Weakness IV and Blindness for the first 6 s; X-Ray switched off | 100 | 90 s |
| X | Super Dash | 28 blocks at 2 b/t along the look (flat-ish on foot), 20 dmg + 2.5 knockback to everything within 1.8; resumes flight if flying; 24-tick cap. In flight: Flight Boost toggle (free) | 3 | 3 s |
| Shift+X | Sky Launch | 8 dmg / lift 0.8 in 4 blocks at the base, launched ~40 blocks, auto-flight at the apex (if he has solar) | 3 | 6 s |
| C | Super-Speed Barrage | 8 hits, one every 3 ticks, into a 4.5-block 70-deg cone: 3 each (targets' motion damped, "pinned"), the 8th a 12-dmg haymaker, knockback 2.8 + 0.5 up | 5 | 6 s |
| Shift+C | Meteor Strike | grounded: launched 12 blocks up, dive at the apex (or after 30 ticks); airborne/flying: dive at once. Dive target = looked-at block below him (48 range), else the ground 16 blocks ahead; 3 b/t; impact on ground / wall / water / within 1.2 / after 60 ticks: 28 in 6 blocks (falloff), knockback 2.2, lift 1.1, 4 s fire, 2.5-block crater (24 max) | 10 | 12 s |
| V | X-Ray Vision (toggle) | `KryptonianState.xray`; living things within 48 outlined via `mixin/KryptonianXRayGlowMixin` on `Minecraft#shouldEntityAppearGlowing` (owner's client only) + topped-up ambient Night Vision; off on kryptonite / Solar Flare / revoke / respawn | free | none |
| Shift+V | Pick Up / Set Down | pick up (6 blocks, width <= 4.5, not bosses / riders / squad-mates / already carried), no time limit; held at eye + 2.2 + width/2 along the look (pitch-limited so it never sinks into his feet), half the gap closed per tick (snap beyond 8), faces him, navigation / target cleared, creepers defused. Carried: its damage to anyone and its in-wall / cramming / fall damage are vetoed (`KryptonianDamage`). Shift+V again: set down on the first free floor in front (ahead, +1, +2, -0.5, own column; 64 blocks down), zero velocity, 30 s fall-damage immunity (`SAFE_LANDING`). V or attacking it (`AttackEntityCallback`): throw 3.0 b/t -> 24 to it, 18 in 3 blocks | free | 4 s from a throw, 1 s from a set-down |

Key-repeat: `KryptonianAbilityManager` ignores a "pressed" for a slot still held (no release since, within 25 ticks), so
a held V cannot flicker X-Ray. Bosses (max HP >= 300 or `TitanCombat.isBoss`) take at most 8% of max HP per hit and are
never knocked back.

## Flight

Double-tap jump in the air -> `KryptonianActionPayload.TOGGLE_FLIGHT`. Client model (v0.14.16: the shared
`client/flight/DirectionalFlight`, numbers in `flight/DirectionalFlightModel#kryptonian`): W / S fly forward / backward
along the look (v0.15.15: 1.0 b/t = 20 b/s, Sprint 2.25 b/t = 45 b/s, Flight Boost 2.75 = 55 b/s), turning around at the old brake rate (0.35 of the gap/tick),
A/D strafe (70%), Space/Sneak 0.6 b/t vertical, coast to a hover (0.08). Big outside pushes (> 0.6 b/t off the model, or
any server velocity packet) are adopted. Dash / Slam / Launch stop flight while they
run (the server launches him) and Dash / Launch restore it.

## The Kryptonite Meteor

**v0.14.13:** the nightly meteor described below is gone -- the meteor is now the rare world-generated
`KryptoniteCraterStructure`. The falling-meteor code remains only for the operator command `/projecthero meteor`.

Overworld dusk (day time 13000-14000, once per day): 20% chance (guaranteed on the 6th meteor-less night) to schedule
one 400-8400 ticks later near a random online player, 80-200 blocks away (up to 8 tries for dry land). Chat line to
the whole Overworld + direction/distance to that player. Fall 100 ticks from ~140 above. Impact (scheduled by game time,
independent of the entity): `explode` power 3 with `ExplosionInteraction.NONE`, bowl radius 4 x 2.8 deep + the column
above cleared (never block entities, unbreakable blocks, fluids), floor scorched (magma / blackstone / basalt / coarse
dirt), some fire, **one Meteor Core** at the centre (loot: exactly one Kryptonian Crystal, never itself) and up to 10
kryptonite ore around it. Static state (pending impacts, roll day, pity counter) is dropped by `ServerStateReset`; a
meteor in flight at shutdown never lands.

## The Superman Suit (v0.14.9)

A craftable four-piece armour set from the user's `superman.bbmodel` (`3d minecraft models/kryptonian/`) that **only a
Kryptonian can wear**. No powers of its own.

| What | Where |
|---|---|
| Material, items, the wear rule, the pop-off tick, the dispenser behaviour | `SupermanSuit` (items registered from `KryptonianItems.initialize`, creative tab after the meteor blocks) |
| Item (right-click refusal, tooltips) | `item/SupermanSuitItem` (`SuperheroArmorItem`, set id `superman`) |
| Armour-slot / shift-click / creative-screen refusal | `mixin/LivingEntitySupermanSuitMixin`: a non-Kryptonian player's `getEquipmentSlotForItem` for a suit piece is `MAINHAND`, so `ArmorSlot.mayPlace` says no; both sides (the power is synced) |
| The cape | client `kryptonian/SupermanCapeLayer` -- since v0.14.21 a thin subclass of the shared `render/FlowingCapeLayer` (Thor's cape uses the same), registered in `KryptonianClient` via `FlowingCapeLayer.register`; when it shows / streams: `SupermanSuit.wearsCape` / `SupermanSuit.capeInWind` |
| Assets / recipes | `scratchpad/gen_superman_suit.js` (re-runnable); lang `scratchpad/lang_v0149_kryptonian.js` |

- **Stats:** netherite: 3 / 6 / 8 / 3 armour, toughness 3.0, knockback resistance 0.1 (per piece), enchantability 15,
  durability x37 (netherite), fire-resistant item, repaired with diamonds. Leather equip sound (it is cloth).
- **Recipes** (shaped; never kryptonite): Helmet `BDB / G G` (B blue wool, D diamond, G gold ingot); Chestplate
  `R R / DSD / BBB` (R red wool, S gold block); Leggings `GRG / B B / D D`; Boots `R R / D D`. 7 diamonds for the set.
- **Kryptonians only:** armour slots / shift-click / hotbar swap / creative screen refused (mixin above); right-click
  refused with an action-bar line; dispensers fit it only onto a Kryptonian (or an armour stand) with that slot free,
  otherwise shoot it out (vanilla's armour dispense would have put it in a non-Kryptonian's main hand). Anything that
  still gets it onto a non-Kryptonian (commands, losing the power while wearing it) is caught by `SupermanSuit.tick`
  (top of `Kryptonian.tick`, every player every tick): the piece goes into the inventory, or drops at his feet if it
  is full, with "The Superman Suit slips off". Mobs are not policed (only players can be Kryptonians).
- **Model:** `geo/superman.geo.json` = `moon_knight.geo.json`'s skin rig (base +0.3, layers +0.3 over the model's
  0.5 / 0.25) plus boot cubes (bottom 4 px of each leg, 0.35 / 0.6). The skin has **no head art**, so the helmet is
  invisible (tooltip says so): the wearer's face shows.
- **The painted cape removed:** the skin painted a flat red cape with a yellow shield on the second layer -- the whole
  Body Layer (uv 16,32: back face, side-face edges, top face's back edge + shoulder straps, bottom edge) and the back of
  both leg layers (uv 0,32 / 0,48: back face, the column either side, the top face's back row). The generator clears
  those pixels, so the blue base layer shows through. What is left on layer 2: the arm bands and the red boot cuffs.
- **The real cape** (`textures/entity/superman_cape.png`, 64x48, the removed cape's red 168,17,53 and gold 251,171,52;
  left half outside with the shield, stamped mirrored because the cape's u runs from the wearer's right; right half the
  lining): Moon Knight's mesh (`MoonKnightCapeLayer.drawCape`, 1.25 blocks, centre + curled side panels) with
  `arcLengthU = true` (texels by real width, so the shield is not stretched), no hood / block / glide, plus a collar
  strip over the shoulders. Shows whenever the Superman chestplate is worn (not when invisible).
  - Ground: vanilla's swing exactly (`MoonKnightCapeLayer.cloakSwing`); crouching shifts the anchor like vanilla's
    cloak (v0.14.21, from the shared layer) so the cape stays on the back.
  - Kryptonian flight (v0.14.21: also vanilla flight / elytra, as Thor's cape): from the wind of his own flight -- world angle `atan2(forward speed, 0.2 + vertical speed)`
    from straight down, minus the body lean (`FlightPoseHelper.lean`), clamped 6..120 deg, plus a speed-scaled flap;
    eased per player (rate 9/s), blended in/out over ~0.2 s. Hover: hangs; cruise (0.9 b/t, 25 deg lean): ~50 deg off
    the back; super-speed (2 b/t, flat): along the legs, near-horizontal; climbing: trails below; diving: streams up.

## Wiring checklist used

ModAttachments, HeroTiers (hasHeroTier, HERO_KEYS, holdsHero, revokeHero, hasIncompatibleWith), PowerGrants (key +
grant), AbilityRouter (branch + serverTick), HeroCommand (revoke + error text), ProjectHeroCommand, ProjectHeroMod
(one `KryptonianMod.initialize()`), ServerStateReset, ModCreativeTab, HeroIdentity + SquadScreen colour,
PowerInfoScreen, HeroPackGuide (CH_KRYPTONIAN, CHAPTER_POWER_BASE +1, `HeroPackGameTests` count), client mixins json,
FlightPoseHelper, HumanoidModelMixin, ProjectHeroModClient (init + double-tap), lang via
`scratchpad/lang_v0148_kryptonian.js`, assets/data via `scratchpad/gen_kryptonian.js`.
