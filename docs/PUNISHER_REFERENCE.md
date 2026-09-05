# Punisher — HeroCraft reference

Hero-Tier power, peer of Thor / Tony Stark / Spider-Man / Max Steel. The Punisher is **not**
superhuman: everything comes from firearms, explosives, tactical gear and training. Built across
**v0.8.1 – v0.8.5**. Package `com.herocraft.mod.firearm` (the generic gun engine, usable by any
player) and `com.herocraft.mod.punisher` (the power). Power id `punisher`.

## Build phases

| Version | Scope |
|---|---|
| v0.8.1 | Firearm engine + Punisher Pistol |
| v0.8.2 | Assault Rifle, Shotgun, Sniper (+ scope system) |
| v0.8.3 | Punisher power + 6 abilities + passives |
| v0.8.4 | Tactical armour + Abandoned Vigilante Safehouse + Vigilante Training |
| v0.8.5 | Guide, docs, gametests, polish |
| v0.9.3 | Tactical Satchel (replaces Arsenal on R); hold-Alt move names on the HUD; infinite arrows + double arrow damage |

## v0.9.5 changes

- **Tactical armour → diamond level** (see the table below). `ModArmorMaterials.PUNISHER` toughness
  4→**3**, knockback resistance 0.13→**0**, netherite equip/repair → **diamond**. `PunisherItems`
  drops the boots attribute override (the material now gives boots 4 / 3 / 0 natively) and the
  `.fireResistant()` flag; durabilities are explicit per piece (680 / 600 / 529) via
  `PunisherItems.durabilityFor`.
- **Adrenaline particles persist.** The `ANGRY_VILLAGER` activation burst now also emits off the
  player every 5 ticks the whole time Adrenaline is active (`PunisherPassives.tick`).

## v0.9.4 changes

- **Muzzle FX moved off the shooter's face.** `FirearmShooting` spawns the muzzle flash / smoke /
  tracers at the gun in the shooter's hand (`eye + look·0.9 + right·0.30 − 0.35y`) instead of dead
  centre in front of the eyes, so rapid fire no longer washes out the shooter's own view. Purely a
  spawn-position change; downrange impact particles are unchanged.
- **Adrenaline reworked.** Now 20 s (was 8), 30 s cooldown (was 45). On activation: Regeneration V
  for the first 3 s, then Resistance II / Haste II / Speed II for the full 20 s (real effects; Speed II
  replaces the old +20% movement-speed attribute modifier, and the old +50% KB-resist modifier is
  dropped). The firearm perks (+25% reload, +15% damage) stay, gated by `adrenalineUntil`. Client game
  audio is dulled 30% while it runs (`SoundEngineMixin` on `calculateVolume`). When it wears off the
  player gets **Nausea I for 10 s** once — a new persisted `PunisherState.adrenalineCrashAt` (codec
  field 14) drives it from `PunisherPassives.tickAdrenalineCrash`; cleared by `clearTransient`.
- **Only a Punisher may wear the Punisher armour.** New `PunisherArmorGate.enforce` (called for every
  player from `AbilityRouter.serverTick`) ejects any `PunisherArmorItem` from a non-Punisher's armour
  slots into their inventory each tick, with a throttled `message.herocraft.punisher.armor_locked` —
  the same continuous-eject discipline Mjolnir worthiness uses. The crafting gate (`CraftingMenuMixin`)
  already existed.

## v0.9.3 changes

- **`R` now opens the Tactical Satchel** (below), not the Arsenal weapon wheel. `PunisherArsenal` /
  `ArsenalWheelScreen` / `PunisherArsenalOpenPayload` are kept in the codebase but no longer bound to
  a key.
- **Hold Left-Alt** shows the six ability names beside the HUD boxes, exactly like Thor / Spider-Man /
  the experimental HUD (`PunisherHud`, no "hold alt" hint text).
- **Punisher bows / crossbows never spend arrows** and their arrows deal **double damage** — an
  inherent effect for this player only (NOT the Infinity enchantment; a bow the Punisher lends out is
  normal). `ProjectileWeaponItemMixin` short-circuits `ProjectileWeaponItem.useAmmo` for a Punisher
  shooter (returns an un-consumed `INTANGIBLE_PROJECTILE` copy — the fired arrow also can't be picked
  up); `AbstractArrowMixin` doubles `baseDamage` at `shoot(...)` when the owner is a Punisher.

## Tactical Satchel

A persistent 9-slot personal container (`com.herocraft.mod.punisher.satchel.PunisherSatchel`), like an
Ender Chest bound to the power. Contents live on `PunisherState.satchel` (an `ItemContainerContents`,
codec field 13) — persistent, `copyOnDeath`, synced — so they survive death, power swaps and dimension
changes. Opened with **R**; server checks `PunisherArmorSet.fullSet` and, if the player is not wearing
the full tactical set, shows an action-bar message (`message.herocraft.punisher.satchel_locked`) and
nothing opens. The GUI is a vanilla one-row chest screen (`MenuType.GENERIC_9x1` + `ChestMenu` over a
custom `SimpleContainer`) — no bespoke menu type, texture or networking. Every edit autosaves straight
back to `PunisherState` (`setChanged` override); the container's `stillValid` re-checks power + armour
each tick, so stripping the vest closes it.

## Controls

Firearms are held items, not abilities:

- **Left-click** — fire (suppressed vanilla attack via `FirearmAttackMixin`).
- **Hold right-click** — aim down sights / scope. Sniper: tap the scope-cycle gesture for 3×/6×/10×.
- **Tap R** — reload. **Hold R** — open the Tactical Satchel (Punisher only; needs the full armour set).
- An empty magazine auto-reloads when the trigger is released.

Punisher abilities use the six universal slots (R/G/X/Z/V/C), assigned in order:
`R` Tactical Satchel · `G` Frag Grenade · `X` Tactical Roll · `Z` Suppressive Fire · `V` Adrenaline ·
`C` Explosive Charge.

## Firearm engine (`com.herocraft.mod.firearm`)

- `FirearmData` / `Firearms` — one immutable stat block per weapon id; the whole balance table.
- `FirearmItem` — base held item (no durability, not enchantable, stacks to 1).
- `FirearmStack` — per-stack state on `ModDataComponents` (`FIREARM_MAGAZINE`, `FIREARM_RELOAD_END`,
  `FIREARM_LAST_FIRED`) — survives drop / death / relog with no extra bookkeeping.
- `FirearmShooting` — **server-authoritative hitscan** (no bullet entity). Spread cone, recoil bloom,
  range falloff, headshots, knockback. Shotgun = N rays per trigger pull.
- `HeadshotResolver` — head zone from the target's eye height / bbHeight, so it scales to any mob;
  per-`EntityType` overrides in `OVERRIDES`.
- `FirearmReload` — magazine reload, or interruptible shell-by-shell for the shotgun.
- `FirearmAmmo` — reserve logic: infinite for a Punisher (`FirearmHooks.infiniteReserve`), otherwise
  the matching ammo item, consumed only as far as it takes to top off (partial reloads allowed).
- `FirearmManager` — per-player tick: trigger-held flag (server-timed auto fire), recoil decay,
  reload tick, empty auto-reload, lowering the sights on stow. Registered in `ServerStateReset`.
- `FirearmHooks` — the seam to the Punisher power (installed in Phase 3): infinite reserve, faster
  handling, damage bonus, headshot / kill / craft callbacks. Default impl = a plain player.

## Firearm stats

| Weapon | Mag | Body / Head | Rate | Reload | Mode | Ammo |
|---|---|---|---|---|---|---|
| Punisher Pistol | 12 | 5 / 7.5 | ~3/s | 2.2 s | semi | Handgun Rounds |
| Assault Rifle | 30 | 4 / 6 | 5/s | 3 s | auto (bloom) | Rifle Rounds |
| Shotgun | 6 | 3 / 4.5 ×6 pellets | ~1/s | 1 s/shell | pump | Shotgun Shells |
| Sniper | 8 | 28 / 40 | 3 s | 5 s | bolt, 3×/6×/10× scope | Sniper Rounds |

## Obtainment

No potion, no accident — a trained human:

1. Find a rare **Abandoned Vigilante Safehouse** (`/locate structure herocraft:vigilante_safehouse`)
   — a buried stone-brick bunker with a weapon workbench, ammo + supply chests, target boards, and a
   guaranteed **Vigilante Training Manual** in a barrel.
2. Right-click the Manual → **Vigilante Training** begins. Objectives: defeat 25 hostiles, 10 of them
   at range, land 5 firearm headshots, craft a firearm, defeat a Pillager Captain.
3. All objectives done → *HERO POWER UNLOCKED — PUNISHER*, permanent, via `Punisher.grant` (the same
   underlying system as Thor / Iron Man / Spider-Man / Max Steel).

Hero-Tier exclusivity is enforced through `HeroTiers` — a Punisher cannot also hold an experimental
mutation or another Hero-Tier power. Admin: `/punisher power grant|revoke`, `/heropower grant hero
punisher`, `/punisher training start`, `/punisher arsenal all|<weapon>`, `/punisher status`. The
craftable **Power Suppressor** strips it like any other power.

## Abilities (`com.herocraft.mod.punisher.ability`)

| Key | Ability | Summary |
|---|---|---|
| R | **Arsenal** | Hold for a weapon wheel over unlocked firearms (pistol always available; others unlock on craft). Tap R = reload. |
| G | **Frag Grenade** | Hold to cook (max 3 s → detonates in hand), release to throw. Bounces, ~12 dmg falloff + knockback, small block damage. 12 s cd. |
| X | **Tactical Roll** | Dive in movement direction, brief KB-resist + 40% damage reduction, cancels reload. Vanilla collision prevents wall-clip. 4 s cd. |
| Z | **Suppressive Fire** | Needs the rifle unlocked. 4 s: −70% recoil, −50% spread, ×0.7 fire interval, Slowness on hits, −25% self speed. 20 s cd. |
| V | **Adrenaline** | 20 s: Regen V (first 3 s), Resistance II, Haste II, Speed II, ×0.75 reload (supersedes the passive), +15% firearm dmg. Own audio dulled 30%. Nausea I for 10 s crash when it ends. 30 s cd. |
| C | **Explosive Charge** | Place a C4 charge on a surface (max 3, owner-stamped). Sneak+C detonates only yours. ~20 dmg, moderate terrain. |

## Passives (`PunisherPassives.Hooks`)

- **Weapon Proficiency** — infinite reserve ammo, ×0.6 recoil, faster handling.
- **Faster Reloading** — ×0.85 reload; Adrenaline's ×0.75 *supersedes* it (no compounding).
- **Ballistic Expertise** — ×0.85 (ADS) / ×0.9 (hip) spread.
- **No Mercy** — +25% firearm damage to a non-boss hostile below 15% health (+10% to a boss,
  `maxHealth ≥ 150`).
- **Headshot Feedback** — `FirearmHeadshotPayload` → a brief "HEADSHOT" cue by the crosshair.

## Tactical armour (chest / legs / boots — no helmet)

`ModArmorMaterials.PUNISHER` — **diamond level** (v0.9.5): diamond equip sound + diamond repair,
**3.0 toughness**, **no knockback resistance** from the plate. Per-piece (set on the items in
`PunisherItems.durabilityFor`, no `.fireResistant()`):

| Piece | Armour | Toughness | Durability |
|---|---|---|---|
| Tactical Vest (chest) | 9 | 3 | 680 |
| Tactical Leggings | 7 | 3 | 600 |
| Tactical Boots | 4 | 3 | 529 |

Renders through the shared GeckoLib armour path (`geo/punisher.geo.json`, from the supplied model via
`scratchpad/convert_punisher_geo.js`). **Full-set bonus, only while holding the power:** 20%
projectile-damage reduction, 10% knockback resistance, ×0.9 recoil. Never exceeds Iron Man.

## Crafting

| Result | Recipe |
|---|---|
| Weapon Parts ×2 | `I·I / C R C / I·I` (iron, copper, redstone) |
| Gun Barrel ×2 | `III / C·C` |
| Weapon Scope | `I G I / C R C` (I iron, G glass pane, C copper, R redstone) |
| Punisher Pistol | `W W I / _ W R / _ _ C` |
| Punisher Assault Rifle | `W W B / _ W R / _ G C` (B gun barrel, G gunpowder) |
| Punisher Shotgun | `W W B / P W G / P _ _` (P oak planks) |
| Punisher Sniper Rifle | `W B S / W W R / G C P` (S weapon scope, G glass) — most expensive |
| Pistol Ammunition ×12 | shapeless: gunpowder + copper ingot + 2 iron nuggets |
| Rifle Ammunition ×12 | shapeless: 2 gunpowder + copper ingot + iron nugget |
| Shotgun Shells ×8 | shapeless: gunpowder + copper ingot + iron nugget + flint |
| Sniper Ammunition ×6 | shapeless: 2 gunpowder + iron ingot + copper ingot |
| Punisher Tactical Vest | `W·W / IWI / III` |
| Punisher Tactical Leggings | `III / W·W / I·I` |
| Punisher Tactical Boots | `W·W / I·I` |

## Custom sound assets still to record

Every firearm currently borrows vanilla sounds (`FirearmData.Builder` defaults + per-weapon
`.sounds(...)` in `Firearms`). Replace with bespoke `.ogg` at `assets/herocraft/sounds/firearm/`
and a `sounds.json` block, then point each `FirearmData` entry at the new event:

- `firearm/pistol_fire`, `firearm/rifle_fire`, `firearm/shotgun_fire`, `firearm/sniper_fire`
- `firearm/mag_out`, `firearm/mag_in`, `firearm/pump`, `firearm/bolt`, `firearm/shell_insert`
- `firearm/dry_fire` (empty click), `firearm/weapon_select`

## Multiplayer

Server-authoritative for: firing (hitscan), damage, ammo, reload state, magazine count, fire rate,
grenade + C4 spawning / damage / ownership / detonation, cooldowns, power ownership, headshots,
ability effects. The client only sends edge-triggered requests (`FirearmFirePayload`,
`FirearmActionPayload`, `PunisherArsenalPayload`). Held weapon, fire swing, muzzle/impact particles,
grenade and C4 entities, and the roll all replicate to other clients through normal entity/animation
sync + the S2C effect payloads.

## Testing

`./gradlew build` — green after every phase (v0.8.1 → v0.8.5). `src/gametest/.../PunisherGameTests.java`
(11 tests): magazine drain + reload, Punisher-consumes-no-ammo vs normal-player-consumes-ammo,
infinite-reserve hook gating, headshot resolver scaling, Adrenaline modifier apply + clean teardown,
Suppressive-needs-rifle gate, Tactical Roll cooldown, C4 max-3 + owner separation, Hero-Tier
exclusivity, Vigilante Training grant. **All 193 mod gametests pass** (Thor / Iron Man / Spider-Man /
Max Steel / raids regression-clean). `runClient` boot log clean (0 model / texture / mixin errors).

No `run/eula.txt` in this environment, so **the following need the user's in-world playtest**:
weapon in-hand poses (first/third person — the `display` blocks are a by-eye first pass, geometry is
final), scope overlay + zoom feel, grenade/C4 explosions in practice, live worldgen of the Vigilante
Safehouse, the GeckoLib armour model in-world, and multiplayer with a Punisher + a normal player.

## Known simplifications

- Weapon in-hand `display` transforms are a first pass by eye — expect one screenshot-tuning pass in
  first / third person (see the Mjolnir history). Model geometry is final.
- No skeletal reload / pump / bolt / grenade-throw animations — vanilla held-item display transforms
  can't do that. Feedback is camera kick + muzzle flash + sound + HUD reload bar + a CYCLE indicator.
  Full weapon animations would need GeckoLib item models (future polish).
- Tracer / muzzle / impact are server-sent vanilla particles (subtle, 1-tick); no dedicated
  first-person muzzle-flash render.
- Grenade cook is tracked by ability-key press-time, not a visible in-hand pin animation. C4 renders
  as a small rotating item model without placed-on-wall orientation.
- All firearm sounds are vanilla placeholders (see the list above).
