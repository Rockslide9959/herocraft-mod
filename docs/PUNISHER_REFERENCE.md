# Punisher — Project Hero reference

Hero-Tier power, peer of Thor / Tony Stark / Spider-Man / Max Steel. The Punisher is **not**
superhuman: everything comes from firearms, explosives, tactical gear and training. Built across
**v0.8.1 – v0.8.5**. Package `com.projecthero.mod.firearm` (the generic gun engine, usable by any
player) and `com.projecthero.mod.punisher` (the power). Power id `punisher`.

## Build phases

| Version | Scope |
|---|---|
| v0.8.1 | Firearm engine + Punisher Pistol |
| v0.8.2 | Assault Rifle, Shotgun, Sniper (+ scope system) |
| v0.8.3 | Punisher power + 6 abilities + passives |
| v0.8.4 | Tactical armour + Abandoned Vigilante Safehouse + Vigilante Training |
| v0.8.5 | Guide, docs, gametests, polish |
| v0.9.3 | Tactical Satchel (replaces Arsenal on R); hold-Alt move names on the HUD; infinite arrows + double arrow damage |
| v0.15.18 | New kit: R/G/Z/X/C each with a Shift move, satchel on N; Suppressive Fire, Adrenaline and C4 removed |

## v0.15.18 kit

Shift = sneaking at the moment the key goes down (`player.isShiftKeyDown()`); every Shift move has its **own**
cooldown id in `PunisherState.abilityReadyAt`. Routing in `PunisherAbilityManager.handle`; numbers in `PunisherConfig`.

| Key | Move | Shift + key | Move |
|---|---|---|---|
| R | **Target Designation** (`PunisherMark`) -- entity raycast 48, never a squadmate / own pet; marked 30 s, +30% from every hit the marker deals it (`LivingEntityPunisherMarkMixin`, `hurt` HEAD); one mark per player. 5 s | Shift+R | **Threat Assessment** -- every living thing within 18 glows 10 s. 12 s |
| G | **Brutal Strike** (`PunisherMelee`) -- target within 5: 10 dmg, 2 s stun. 2 s (a whiff costs nothing) | Shift+G | **Breach Kick** -- 15 dmg, launched ~10 blocks (1.35 b/t + 0.42 lift, scaled by KB resistance), 5 s stun. 5 s |
| Z | **Frag Grenade** -- unchanged, moved from G (hold to cook). 12 s | Shift+Z | **Warzone** (`PunisherWarzone`) -- hold 5 s (let go of Z or Shift = cancelled, free) to call a barrage on the aimed block (100): 15-block zone marked with red smoke for everyone, ~3 missiles/s for 10 s, 30 dmg in 5 blocks (40% at the edge), no block damage, never the caller / squad / pets. 120 s |
| X | **Tactical Roll** -- unchanged. 4 s | Shift+X | **Tactical Advance** -- Speed IV 30 s. 60 s (no cooldown was specified; chosen) |
| C | **Smoke Screen** (`PunisherSmoke`) -- 5-block cloud 6 s; mobs inside lose / cannot take a target, other harmable players inside get Blindness I. 12 s | Shift+C | **Flashbang** (`FlashbangEntity`, 1.5 s fuse) -- within 6 (not thrower / squad): Blindness, Slowness II, Nausea 8 s (softened on players by `applyControl`), mobs drop their target + 3 s no-target. 12 s |
| V | weapon abilities (`PunisherWeaponAbilities`, a separate change) | | |
| N | **Tactical Satchel** (`PunisherActionPayload.OPEN_SATCHEL`; not an ability slot, never on the HUD) | | |

- **Stun / no target** (`PunisherControl`): stunned = Slowness X + Weakness III, path dropped every tick, no target;
  the no-target window is refused at `Mob#setTarget` (`MobMindLockMixin`). Static maps, `ServerStateReset`-cleared.
- **Private glow**: the mark (red) and Threat Assessment (orange hostiles / grey others) are ids sent to the Punisher
  only (`PunisherIntelPayload` -> `PunisherIntelClient`, drawn by `EntityGlowMixin`), never a GLOWING effect or
  flag; the red dust marker over the mark is sent with `sendParticles(owner, ...)` only.
- **Warzone missiles** are simulated server-side (no entity, nothing saved); trail / ring / blast particles are
  force-sent to every player within 192 blocks so a far-off barrage is visible.
- **Punch / kick animations** (playtest fix): `PunisherMelee.animate` stamps the synced, non-persistent
  `ModAttachments.PUNISHER_MELEE_ANIM` (`startTick * 4 + kind`, kind 1 punch / 2 kick) on every press, hit or whiff;
  every client reads it through `GunAnim.melee` / `meleeKind`. Third person (`PunisherGunPose.punchPose` / `kickPose`
  + `meleeLean` in `PlayerRendererMixin`): Brutal Strike = 8-tick right jab (wind-up, fist out at tick 2, shoulders
  twisted into it like vanilla's attack swing, lead foot forward, off hand up in a guard, lean in); Breach Kick =
  11-tick high front kick (chamber, leg out at tick 3, arms out for balance, lean back). First person: the gun rig
  (`GunFirstPerson`) jabs forward with the support hand off in a guard; bare-handed / other items the arm jabs
  (`ItemInHandRendererGunMixin.projecthero$punisherJab`); the kick switches on the full first-person body
  (`FirstPersonBodySequences`) and `PunisherKickCameraMixin` dips the rendered view ~55 degrees for the kick (never
  the player's look / aim) so the real boot comes up into the screen. The damage is still instant on
  the press -- the short wind-ups put the impact frame about where the hit lands on the clients.
- **HUD** key letters sit inside each box's top-left corner and follow the actual key bindings (they were drawn above
  the boxes from `AbilitySlot.defaultKey()`); a cooldown's seconds sit bottom-right.
- With a gun in hand, **tap R reloads** and holding R (8 ticks) sends R -- so R / Shift+R need a short hold there.
- Suited as **Agent Venom**, Sneak+X / Z / V are the Symbiote extras: they shadow Tactical Advance and Warzone.
- **Removed**: Suppressive Fire, Adrenaline, Explosive Charge. `C4ChargeEntity` / `projecthero:c4_charge` stays
  registered for save safety (a leftover discards itself on its first tick, `NoopRenderer`); the `c4_charge` and
  `adrenaline_syringe` items stay as plain items. `PunisherState.adrenalineUntil` / `suppressiveUntil` /
  `adrenalineCrashAt` stay in the codec, unused. The client Adrenaline stab animation (`GunAnim.stab`,
  `GunFirstPerson`, `PunisherGunPose`, the `ScopeOverlay` pulse, `PUNISHER_STAB_AT`) is dormant -- nothing sets it.

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
  slots into their inventory each tick, with a throttled `message.projecthero.punisher.armor_locked` —
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

A persistent 9-slot personal container (`com.projecthero.mod.punisher.satchel.PunisherSatchel`), like an
Ender Chest bound to the power. Contents live on `PunisherState.satchel` (an `ItemContainerContents`,
codec field 13) — persistent, `copyOnDeath`, synced — so they survive death, power swaps and dimension
changes. Opened with **R**; server checks `PunisherArmorSet.fullSet` and, if the player is not wearing
the full tactical set, shows an action-bar message (`message.projecthero.punisher.satchel_locked`) and
nothing opens. The GUI is a vanilla one-row chest screen (`MenuType.GENERIC_9x1` + `ChestMenu` over a
custom `SimpleContainer`) — no bespoke menu type, texture or networking. Every edit autosaves straight
back to `PunisherState` (`setChanged` override); the container's `stillValid` re-checks power + armour
each tick, so stripping the vest closes it.

## Controls

Firearms are held items, not abilities:

- **Left-click** — fire (suppressed vanilla attack via `FirearmAttackMixin`).
- **Hold right-click** — aim down sights / scope. Sniper: tap the scope-cycle gesture for 3×/6×/10×.
- **Tap R** — reload. **Hold R** — R's ability (v0.15.18: Target Designation; the satchel moved to N).
- An empty magazine auto-reloads when the trigger is released.

Punisher abilities use the six universal slots -- see **v0.15.18 kit** above for the current map.

## Firearm engine (`com.projecthero.mod.firearm`)

- `FirearmData` / `Firearms` — one immutable stat block per weapon id; the whole balance table.
- `FirearmItem` — base held item (no durability, not enchantable, stacks to 1).
- `FirearmStack` — per-stack state on `ModDataComponents` (`FIREARM_MAGAZINE`, `FIREARM_RELOAD_END`,
  `FIREARM_LAST_FIRED`) — survives drop / death / relog with no extra bookkeeping.
- `FirearmShooting` — **server-authoritative hitscan** (no bullet entity). Spread cone, recoil bloom,
  range falloff, headshots, knockback. Shotgun = N rays per trigger pull.
  - **Bullet holes (v0.10.1):** a shot that ends on a solid block sends `BulletHolePayload` to players
    within 64 blocks. Client-side `BulletHoleRenderer` keeps a bounded list (max 96, oldest dropped)
    and draws a fading `textures/misc/bullet_hole.png` decal on the hit face via
    `WorldRenderEvents.AFTER_TRANSLUCENT`; each hole lasts 60 s, fading over its last 6 s. No entity,
    no block change — purely cosmetic, cleared on world unload.
- `HeadshotResolver` — head zone from the target's eye height / bbHeight, so it scales to any mob;
  per-`EntityType` overrides in `OVERRIDES`.
- `FirearmReload` — magazine reload, or interruptible shell-by-shell for the shotgun.
- `FirearmAmmo` — reserve logic: a personal per-gun pool for a Punisher (`FirearmHooks.usesPersonalReserve` -> `PunisherAmmoReserve`), otherwise
  the matching ammo item, consumed only as far as it takes to top off (partial reloads allowed).
- `FirearmManager` — per-player tick: trigger-held flag (server-timed auto fire), recoil decay,
  reload tick, empty auto-reload, lowering the sights on stow. Registered in `ServerStateReset`.
- `FirearmHooks` — the seam to the Punisher power (installed in Phase 3): personal reserve pool, faster
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

1. Find a rare **Abandoned Vigilante Safehouse** (`/locate structure projecthero:vigilante_safehouse`)
   — a buried stone-brick bunker with a weapon workbench, ammo + supply chests, target boards, and a
   guaranteed **Vigilante Training Manual** in a barrel. (v0.15.11 rebuild: the roof is two blocks
   under the ground, the way in is a cobblestone-ringed spruce hatch over a lined ladder shaft -- the
   ladder hangs on the south wall facing north, so it climbs properly -- and the room has a west-wall
   workbench, east-wall bed + chests, and a firing range with a bullseye, hay backstop and targets.)
2. Right-click the Manual → **Vigilante Training** begins. Objectives: defeat 25 hostiles, 10 of them
   at range, land 5 firearm headshots, craft a firearm, defeat a Pillager Captain.
3. All objectives done → *HERO POWER UNLOCKED — PUNISHER*, permanent, via `Punisher.grant` (the same
   underlying system as Thor / Iron Man / Spider-Man / Max Steel).

Primary-power exclusivity is enforced through `HeroTiers` (v0.11.14) — a Punisher is a *Primary* power,
so `Punisher.grant` replaces any experimental mutation or other Primary power (and a bonded Symbiote)
instead of being refused. Admin (v0.10.1, all under `/projecthero`): `/projecthero punisher
power grant|revoke`, `/projecthero power grant hero punisher`, `/projecthero punisher training start`,
`/projecthero punisher arsenal all|<weapon>`, `/projecthero punisher status`. The craftable **Power
Suppressor** strips it like any other power.

## Abilities (`com.projecthero.mod.punisher.ability`) -- the pre-v0.15.18 kit, kept for history

| Key | Ability | Summary |
|---|---|---|
| R | **Arsenal** | Hold for a weapon wheel over unlocked firearms (pistol always available; others unlock on craft). Tap R = reload. |
| G | **Frag Grenade** | Hold to cook (max 3 s → detonates in hand), release to throw. Bounces, ~12 dmg falloff + knockback, small block damage. 12 s cd. |
| X | **Tactical Roll** | Dive in movement direction, brief KB-resist + 40% damage reduction, cancels reload. Vanilla collision prevents wall-clip. 4 s cd. |
| Z | **Suppressive Fire** | Needs the rifle unlocked. 8 s: −70% recoil, −50% spread, ×0.7 fire interval, Slowness on hits, −25% self speed. 20 s cd. |
| V | **Adrenaline** | 20 s: Regen V (first 3 s), Resistance II, Haste II, Speed II, ×0.75 reload (supersedes the passive), +15% firearm dmg. Own audio dulled 30%. Nausea I for 10 s crash when it ends. 30 s cd. |
| C | **Explosive Charge** | Place a C4 charge on a surface (max 3, owner-stamped). Sneak+C detonates only yours. ~20 dmg, moderate terrain. |

## Weapon Ability (V) — v0.15.18 (`PunisherWeaponAbilities`)

V depends on the gun in the main hand; **Shift+V** (sneak held at the press) is a second move with its own
cooldown. Not holding a gun → "Hold a gun". Anything still running (pistol charges, Rapid Fire, a chambered
piercing round, a lock-on, a Steady Shot) **cancels the moment that gun is put away** — another hotbar slot or another item in
the slot. Transient (static map, `ServerStateReset` + disconnect cleanup); cooldowns live in
`PunisherState.abilityReadyAt` as `weapon_<gun>` / `weapon_<gun>_shift`.

| Gun | V | Shift+V |
|---|---|---|
| Pistol | Next 3 shots ×2 damage. 12 s cd. | One instant shot (ignores the fire-rate gate): 18 damage + Slowness II 4 s. 20 s cd. |
| Shotgun | Needs ≥3 shells loaded; spends 3 on one blast, 15 dmg/pellet (6 pellets), 5-block range. 12 s cd. | Same 3 shells, 12 pellets fanned evenly over a 90° arc, 10 dmg/pellet, 5 blocks. 20 s cd. *(user gave no numbers — chosen)* |
| Assault Rifle | 15 s: fire interval ×0.5 (double rate) and reload time ×0.5. 20 s cd. | Lock onto the target under / nearest (10° cone) the crosshair, ≤40 blocks, line of sight; auto-fires the magazine at its upper body (spread ×0.25) at the gun's own rate; reload blocked until the magazine is empty or the target dies / is lost / leaves LOS. 25 s cd *(chosen)*. |
| Sniper | Next shot pierces every living thing on its line and ignores 40% of each target's armour value. 12 s cd. **Steady Shot** (playtest fix: no auto-aim, no lock-on, the camera stays the player's): scope raised if it was not up (`PunisherLockOnPayload.SCOPE_ONLY`); 3 s (`SNIPER_STEADY_TICKS`) later one zero-spread shot along the player's own look, paid as a headshot ×2 (80) wherever it lands (also pierces if a V round was chambered). Needs a round loaded and no reload running; reload blocked while steadying. Cancels if the gun is swapped. 30 s cd. |

Plumbing — every shot is a real `FirearmShooting.fire` (ammo, sounds, flash, tracers, recoil, headshots, hooks):
- `firearm/ShotSpec` — per-pull override: aim direction, spread factor, 90° fan, pellets, ammo cost, range,
  fixed damage, damage multiplier, forced headshot, pierce, armour-ignore (a temporary −X% `ARMOR` modifier on
  the target around the one `hurt`), skip fire-rate gate, on-hit / on-fired callbacks.
- `FirearmHooks.nextShot` (an armed special shot for the next ordinary pull — pistol charges, sniper pierce) and
  `FirearmHooks.reloadBlocked` (checked in `FirearmReload.start`). `PunisherWeaponAbilities.initialize()` wraps
  the installed hooks (call it after `Punisher.initialize()`) and folds Rapid Fire into `fireIntervalFactor` /
  `reloadSpeedFactor`. A new `FirearmHooks` method must also be delegated in that wrapper.
- Lock-on camera (Assault Rifle only): `PunisherLockOnPayload(entityId, scope)` → `client/punisher/PunisherLockOnClient`;
  `PunisherLockOnMouseMixin` drops mouse look while locked and turns the view onto the target every frame. The server
  aims every rifle-lock shot itself; the camera is presentation only. The sniper's Steady Shot sends
  `entityId = SCOPE_ONLY (-2)`: no lock, only `FirearmClient.forceAim` (server sets `FIREARM_AIMING`); -1 lowers it.
- HUD: the V box shows the held gun's V cooldown, with a thin bar along its bottom for the Shift+V cooldown.
- Tests: `PunisherWeaponAbilityV01518GameTests` (batch `punisher_weapon_v01518`).

## Passives (`PunisherPassives.Hooks`)

- **Weapon Proficiency** — a regenerating personal reserve (3 mags/gun), ×0.6 recoil, faster handling.
- **Faster Reloading** — ×0.85 reload.
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

Renders through the shared GeckoLib armour path (`geo/punisher.geo.json` + `textures/armor/punisher.png`,
64×64). **Full-set bonus, only while holding the power:** 20% projectile-damage reduction, 10%
knockback resistance, ×0.9 recoil. Never exceeds Iron Man.

**Model history:** v0.9.25 added a second "Mark 2" model for side-by-side comparison; v0.10.1 the user
picked Mark 2, so `geo/punisher.geo.json` + `textures/armor/punisher.png` were *replaced in place* with
that model (merged from the GeckoLib-4 bundle at `.../Punisher/punisher_armor_geckolib4_bundle`;
`armorRightLeg`/`armorLeftLeg` → `armorRightBoot`/`armorLeftBoot` for the boots slice; arms are one
identical 4×6×4 shoulder-to-elbow cube so the sleeves match and the forearms render as skin). The
`_2` items/recipes/models and the original model were deleted. The `"punisher"` set id is unchanged.

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
`.sounds(...)` in `Firearms`). Replace with bespoke `.ogg` at `assets/projecthero/sounds/firearm/`
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

v0.15.18: `PunisherKitGameTests` (16) covers the new kit; the Adrenaline / Suppressive / C4 tests are gone.
Run `./gradlew runGameTest -PtestFilter=punisher -PfastTicks=true --offline`.


`./gradlew build` — green after every phase (v0.8.1 → v0.8.5). `src/gametest/.../PunisherGameTests.java`
(11 tests): magazine drain + reload, Punisher-consumes-no-ammo vs normal-player-consumes-ammo,
personal-reserve hook gating + depletion/regen, headshot resolver scaling, Adrenaline modifier apply + clean teardown,
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

## Agent Venom (v0.13.11) -- Punisher + Symbiote

The Symbiote no longer purges the Punisher (`SymbioteCompatibility.COMPATIBLE_HERO_KEYS` = spider_man, punisher;
`HeroTiers.claimPrimary` keeps the bond for either). `SymbioteHostType.AGENT_VENOM` = bonded + Punisher and not
Spider-Man (Spider-Man wins if both are held). H toggles the Symbiote as for every host; `SymbioteSuit` synthesises
`AgentVenomArmorItem` pieces (own `AGENT_VENOM` material since v0.13.21, was the Black Suit's, geo `agent_venom.geo.json` = the Symbiote host's skin rig,
texture = the user's `3d minecraft models/agent venom/agentvenom.bbmodel` skin; `scratchpad/gen_agent_venom.js`).
Losing the Punisher demotes the host to a Normal one and `Symbiote.tick` swaps the suit.

`symbiote/SymbioteAgentVenomAbilities` -- sneak-modified Punisher keys, consumed at the top of
`PunisherAbilityManager.handle`; cooldowns live in the Punisher's synced `abilityReadyAt`:
- Sneak+X Tendril Swing (block raycast 36, launch toward it, 3 s fall guard) -- 3 s.
- Sneak+Z Tendril Snatch (entity raycast 18, 5 dmg, Slowness III 3 s, pull + disarm a Mob's main hand; bosses only held) -- 8 s.
- Sneak+V Symbiote Unleashed (10 s: +50% attack, +20% speed, 20% melee life steal via AFTER_DAMAGE; 30% before v0.13.21) -- 45 s from activation.
- Suit stats (`reconcile`, every second from `AbilityRouter.serverTick`): +25% attack, +15% speed, +15% jump, +0.15 knockback resistance (0.25 before v0.13.21).
- v0.13.21: the suit has its own `ModArmorMaterials.AGENT_VENOM` (3/7/9/3 = 22, toughness 2.5; was the Black Suit's 24 / 3),
  and the Symbiote revive waits 20 minutes for Agent Venom (`SymbioteVitalsManager.HERO_HOST_RESURRECT_COOLDOWN_TICKS`).
- Symbiote Rounds (`PunisherPassives.Hooks#damageFactor` +0.20, `#onHit` Slowness I 1.5 s), Living Ammunition
  (`PunisherAmmoReserve.tickRegen` x3, reload factor x0.75).
- HUD: v0.13.21 panel above the Punisher row (`SymbioteHud#renderHeroHostPanel`): title, the three Sneak extras
  (draining cooldowns, V glows with the Unleashed timer), the Symbiote revive timer and a revive Hairline; Left Alt lists them.
- The Symbiote's weaknesses and instincts (fire / lava / sonic retreat, low-health auto-wrap) are unchanged. Like
  the Black Suit, Agent Venom has none of the Normal host's Biomass passives.
Tests: `gametest/AgentVenomGameTests`.
