# HeroCraft

A Minecraft superhero mod (Fabric, 1.21.1), starting with Thor and Mjolnir. See
[THOR_DESIGN.md](THOR_DESIGN.md) for the full design doc.

## Download

**Go to [Releases](../../releases/latest) and download the `herocraft-<version>.jar` file** —
that's the mod itself. Drop it into your Minecraft `mods` folder along with matching versions of
[Fabric Loader](https://fabricmc.net/use/), [Fabric API](https://modrinth.com/mod/fabric-api) and
[GeckoLib](https://modrinth.com/mod/geckolib) for Minecraft 1.21.1 (exact versions are listed on
each release).

Everything else in this repository is source code, not something you need to download to play.

## Repo layout

| Path | What it is |
| --- | --- |
| `src/main/java/com/herocraft/mod/` | All mod source code, one package per power/system (e.g. `ironman/`, `punisher/`, `symbiote/`, `event/`) |
| `src/main/resources/` | Assets and data the mod ships: textures, models, lang files, loot tables, recipes |
| `docs/` | Design/reference docs per power (e.g. [PUNISHER_REFERENCE.md](docs/PUNISHER_REFERENCE.md), [ARMOR_MODELS.md](docs/ARMOR_MODELS.md)) |
| `scratchpad/` | One-off dev scripts used to generate assets (textures, models) — not part of the mod |
| `gradle/`, `gradlew*`, `build.gradle`, `settings.gradle`, `gradle.properties` | Gradle build configuration |

## Status

**v0.6.3 — Spider-Man, and a full rebuild of Spider Adhesion.**

- **New Hero Class: Spider-Man.** You cannot get it directly — it is what the existing **Spider
  Climbing / Adhesion** mutation grows into. Craft an **Arachnid Mutagen** (phantom membrane, an echo
  shard, spider eyes, fermented spider eyes, amethyst, a golden apple) and use it; without the
  adaptation already in you it does nothing and is not consumed. The evolution replaces Adhesion
  outright and hands its mutation slot back. Full writeup:
  [docs/SPIDERMAN_REFERENCE.md](docs/SPIDERMAN_REFERENCE.md).
- **Six web abilities on the existing keys** — R Web Swing, G Web Zip, Z Web Shot, X Web Yank,
  C Web Cocoon, V Web Net — paid for out of a 100-point organic **Web Reserve** rather than any
  ammunition item. No new keybindings.
- **Hybrid web swinging.** Real terrain is always preferred, so a city, a mountainside or a forest
  canopy gives longer and faster arcs; where there is genuinely nothing to catch, the web goes up and
  ahead anyway so traversal never stops in plains or desert. Those swings will only carry you ~12
  blocks above where the run began, so open-country webbing stays swinging rather than becoming
  flying — and climbing real terrain raises that limit with you. Releasing keeps every bit of the
  speed the arc built.
- **Spider Adhesion rebuilt from scratch.** The old version was an `onClimbable` override, which is why
  it could only do what a *ladder* can: you slid down whenever you stopped pressing forward, strafing
  across a wall did nothing, ceilings were impossible, and a one-tick gap at a block boundary dropped
  you. It now tracks a real surface, moves relative to it, and holds on across corners and seams —
  vertical climbing, descent, holding still, horizontal crawling, full upside-down ceiling crawling,
  and transitions in **both** directions between wall and ceiling. Existing Spider Adhesion saves need
  no migration: same power, same two toggles, better engine.
- **Passives:** Spider Sense (a 50% chance to dodge any real attack, decided server-side), catching
  the arrows you dodge, wall and ceiling crawling, a 2-second-cooldown double jump, enhanced strength,
  speed, jump and footing, and far softer landings.
- **Ceiling crawling shows.** The player model flips upside down while on a ceiling, for everyone who
  can see them. The camera is deliberately left alone.

<details>
<summary>v0.6.0 — Iron Man tuning pass, wearable Repulsors, and the accidental-summon fix.</summary>


- **The Repulsor is a gadget, not just a component.** Wear one in the **boots** slot to fly at half
  a Mark II's speed — no suit, no Tony Stark power — and right-click-and-hold for one second to fire
  the Mark II's repulsor blast. It is still the same stackable Fabricator component it always was.
- **Fixed: your armour got called in when you were just moving.** The ground half of the
  double-tap-jump summon fired on *any* two jumps inside a third of a second — running, or mashing
  jump after a knockback. It now needs **sneak +** double-tap jump. The C key is unchanged.
- **Flamethrowers actually set fire to things now.** Both of them (the Mark I's and Pyrokinesis's)
  had their surface-fire gated behind a config flag that defaults to off, so the code had never once
  run. Spraying a wall now lights it.
- **Iron Man suits shrug off fire.** Burning was quietly eating armour condition at ~1.8/second for
  1 damage a tick; fire now costs a twentieth of that.
- **Suit Platforms charge and repair at 3× the suit's own Arc Reactor** (the Mark I and II, which
  have no self-repair, get a small platform-only trickle), and deploying no longer silently restores
  a wrecked suit to perfect condition.
- **The Stark Fabricator powers itself** — a full recharge every five minutes — and one armour piece
  now costs the *entire* buffer, so the machine paces the build.
- **Components are cheaper.** Same ingredients, bigger stacks out: plates, servos, circuits,
  thrusters, stabilisers, targeting modules and missile modules all yield double, on both the
  crafting table and the Fabricator.
- **Component textures redrawn.** Eighteen sprites that were all the same flat rounded square are now
  actually distinguishable at a glance.
- **The raid / curse HUD moved to the bottom-right**, out of the way of status effects and other
  mods' readouts — and the curse's final-stage "watcher" zombie no longer burns up in daylight
  before you see it.

</details>

<details>
<summary>v0.5.0 — the Zombie Raid, and a long-session memory-leak fix.</summary>

- **New world event: the Zombie Raid.** Catch the twenty-minute **Gravebound Curse** from a naturally
  generated **Graveyard** or a rare **Cursed Zombie**, then either burn an Enchanted Golden Apple to
  escape it or survive twelve waves and three **Powered Zombie Bosses** — each carrying a real
  Experimental Power and fighting with it. Full writeup:
  [docs/ZOMBIE_RAID_REFERENCE.md](docs/ZOMBIE_RAID_REFERENCE.md).
- Built on a small reusable **world-event framework** (`com.herocraft.mod.event`) so future events
  (End invasion, Nether corruption, robot uprising, world bosses) reuse waves, participants,
  boundaries and persistence rather than reimplementing them.
- **Fixed: the game got progressively choppier over a long session.** Every static server-side cache
  in the mod was outliving the server that filled it, pinning the previous world — levels, chunk map
  and all its entities — in memory each time you quit to the title screen and opened another world.
  `ServerStateReset` now drops all of it on server stop, and sweeps two marker maps that were only
  ever pruned while somebody happened to have the owning power selected.
- **Fixed: calling armour from an unloaded Suit Platform stopped working.** The registry entry that
  exists specifically to make that possible was being deleted whenever the platform's chunk unloaded,
  because the block entity dropped it from `setRemoved()` — which vanilla also calls on chunk unload,
  not just on the block being broken. Removal now happens from the block's `onRemove`, which fires
  only when the block genuinely changes.

</details>

<details>
<summary>v0.4.3 — GeckoLib leg-render fix, Mark I/II suits, Iron Man mechanics rework ("changes 12")</summary>


- **Fixed: GeckoLib armour never rendered the leg plates** (helmet/chest/boots worked, legs read as
  bare skin — including in the inventory preview doll). Root cause was in GeckoLib itself: legs are
  the one slot vanilla hands it a different `HumanoidModel` instance for, whose flags GeckoLib
  otherwise blindly trusts. `SuperheroArmorRenderer` now decides bone visibility itself instead.
- Suit-store (`C`) now goes to the main inventory only, refusing with a message if there's no room.
- Recharge timing overhauled: a worn suit trickle-charges over a flat 10 minutes; a Suit Platform
  fills both energy and integrity over a flat 5 minutes (integrity still only heals on a platform).
- Suit integrity reworked: absorbs 90% of every hit while intact (10% gets through); once it fails,
  the wearer is Slowed + Weakened and now takes 80% of every hit instead of the old 100%.
- Death recovery now reaches a suit sitting in the pack, not just a worn one, and the crash damage is
  a flat 250 (50% of max) rather than a percentage of whatever integrity was left.
- **Two new suits**, both craftable at a normal table (no Fabricator/blueprint): **Mark I** — grey,
  25% bigger, never leans in flight, Strong Punch / Flamethrower / a 20-second flight burst / Rocket.
  **Mark II** — grey, freezes and force-lands above Y150, a 1-second-windup Repulsor / Rocket / Flare
  / a weaker Unibeam.
- Coordinates added to the Iron Man HUD.

See [docs/IRONMAN_REFERENCE.md §17f](docs/IRONMAN_REFERENCE.md) for the full writeup.

</details>

---

<details>
<summary>v0.4.2 — launch fix, new Mjolnir model, Iron Man durability pass.</summary>

- **Fixed: the client could not start.** `PlayerModelMixin` shadowed `hat`, a field declared on the
  superclass `HumanoidModel`; Mixin only resolves `@Shadow` against the target class, so applying the
  mixin threw during the first resource reload and the game died before the title screen. It reads the
  overlay parts through a cast now.
- **New Mjolnir model** (from `minecraftmodels/model.json`), with its own `mjolnir_metal` /
  `mjolnir_leather` textures replacing the old `mjolnir_parts` palette. The mesh is uniformly refitted
  into the exact bounding box the old one occupied (x/z centred on 8, y 0.5..14.375), which is what
  lets every existing display transform, the held-item -45° diagonal bake, and
  `MjolnirEntityRenderer`'s resting/flight poses keep working untouched. See the `credit` field in
  each model JSON.
- Mjolnir's three sound events finally have audio (built from verified vanilla sound files) instead of
  printing "file does not exist" on every launch.
- **Iron Man:** six item-loss / targeting bugs fixed — see
  [docs/IRONMAN_REFERENCE.md §17e](docs/IRONMAN_REFERENCE.md).

</details>

---

**Phase 3:** bug fixes from Phase 2 testing, four new abilities, and a real Thor
armor look.

- Mjolnir's flight speed is much slower (bias toward trackable, not fast) and it now orients
  head-forward in flight via a custom renderer instead of the generic billboard one.
- Worthiness enforcement is airtight: unworthy pickup is cancelled (mixin into `ItemEntity`, with
  a redundant per-tick inventory sweep as a backstop for commands/chests/trades/other players/a
  worthiness change while already holding it). Creative-mode players bypass worthiness entirely.
- Every ability (Lightning Strike, Throw & Return, Flight, Thunderclap, Storm Call, Parry) tracks
  its own cooldown independently instead of sharing vanilla's item-keyed cooldown.
- New abilities: Lightning Laser (held-key continuous beam), Chain Lightning (Lightning Strike
  now jumps to nearby hostiles), Thunderclap Shockwave (short-range panic-button knockback+stun),
  Storm Call (long-cooldown personal storm that follows the player, buffing Storm Energy regen and
  periodically striking nearby hostiles), and Mjolnir Parry (brief window that blocks incoming
  projectile damage).
- Flight now has a particle trail, a looping wind ambience sound, and (via two client-side mixins,
  unverified in-game -- flag for a playtest) a forward-lean body pose with the hammer arm
  extended.
- Mjolnir's item model was regenerated from a hand-authored OBJ (47 individually-colored cuboid
  parts -- head, rune insets, studded band, wrapped grip, pommel, strap) converted into Minecraft's
  native cuboid model format, with materials baked into a small solid-color texture atlas.
- Thor armor textures were repainted for an MCU-style look: silver-blue breastplate, gold
  pauldrons/vambraces/belt/trim, dark navy under-armor, and a painted-on cape effect on the
  chestplate's back layer (a flat 2D substitute for a physics cape -- see the design doc addendum
  below).

Worthiness is still wired up with a `/thor worthy|unworthy|status [player]` testing command (no
shrine yet). Suit-up is a keybind toggle that swaps in/out the Thor armor and remembers what you
were wearing. Cross-dimension summon (drop-and-summon) and the shrine structure remain deferred
-- see the design doc's "Implementation Plan" for build order.

**Phase 4:** bug-fix/polish pass from in-game testing of Phase 3.

- Mjolnir once again phases straight through terrain during throw/return/summon flight (a
  block-hit was zeroing its velocity and re-triggering every tick, freezing it at walls); a hammer
  that's just resting on the ground (see below) still respects normal collision, so it doesn't
  sink through the floor.
- The in-hand, dropped-on-ground, and GUI/inventory model sizes were all noticeably too small --
  bumped independently (they're separate display transforms in the item model).
- Mjolnir dropped via the vanilla drop key (Q) -- and any other way it ends up loose in the world
  (worthiness ejection, a failed return-to-hand) -- now stays a real, trackable, summon-able
  `MjolnirEntity` instead of reverting to an inert vanilla item, per THOR_DESIGN.md section 3.
  Shift-right-click now explicitly **binds** Mjolnir to whoever does it (requires worthiness) as
  its permanent summon target, taking priority over the "whoever last threw/dropped it" default.
- Worthiness no longer resets on death/respawn (a missing `copyOnDeath()` on the attachment was
  silently clearing it) -- required for the eventual cross-dimension summon design to make sense.
- Flight animation reworked: the lean direction was backwards (fixed), and there are now three
  speed-based poses while flying -- hovering spins Mjolnir above the player's head, walking-speed
  gives a slight lean, sprinting-speed gives the full dive-forward pose with the hammer arm
  extended.
- Lightning Laser's beam now renders from chest height instead of eye height so it doesn't block
  the caster's own first-person aim (hit detection is unchanged, still eye/look-based).
- Thor armor textures were replaced with a hand-drawn set (bronze-steel plate, blue trim, rune
  glow accents); `_emissive` glow-mask variants are included in the resources but not wired up to
  any renderer yet (no shader/Iris integration in this project) -- future phase if wanted.

**Phase 5:** bug-fix pass from Phase 4 testing, including two Phase 4 fixes that overshot.

- **Dropping works again.** A hammer dropped with `Q` used to fly straight back into your hand:
  it landed inside the pickup radius of the player who'd just dropped it and was collected on the
  next tick. Walk-over pickup now needs a vanilla-style 40-tick delay *and* for everyone to have
  stepped away from it at least once, so dropping and summoning stay two separate deliberate
  actions. Walking back over a dropped hammer still picks it up normally.
- **Phase-through is scoped to the return trip only.** Phase 4 made block collision a blanket
  no-op; it's now conditional on the entity's state (`OUTBOUND` / `RETURNING` / `RESTING`). A
  thrown hammer collides normally and stops at the wall it hits, where it stays until summoned; a
  hammer flying home (throw timeout, post-impact, or the `J` keybind) is unstoppable and phases
  through terrain. Resting hammers also keep the orientation they landed in.
- **Duplicate hammers on `/give` fixed at the root.** `GiveCommand` calls `Player.drop()` with a
  real stack purely to spawn the short-lived decorative item that flies into you on a successful
  give, then flags it with `makeFakeItem()`. Phase 4's `Player.drop` mixin hijacked that call and
  turned the decoration into a second, genuine hammer. That mixin is gone: loose Mjolnir item
  entities are now promoted to `MjolnirEntity` on their first tick instead, which both skips fake
  items (they're flagged by then) and is a single catch-all covering the drop key, the inventory
  screen, death drops and dispensers, rather than intercepting each drop call site.
- **Hover pose.** The hammer arm now raises straight overhead while hovering, so the player is
  visibly holding the spinning hammer up instead of it floating on its own. The in-hand copy is
  hidden for the duration so only one hammer is on screen.
- **Sprint pose.** Replaced with a full horizontal superman pose: body laid out flat along the
  direction of travel, hammer arm extended straight ahead, hammer rotated so its head leads. The
  walking-speed lean is unchanged.
- **Smooth pose transitions.** Poses are no longer read straight off the entity at render time
  (which snapped the model the instant a speed threshold was crossed). Each visible player gets an
  eased animation state, ticked client-side and interpolated across the partial tick, so
  hover/walk/sprint and take-off/landing all blend.
- Fixes found while auditing the above: a thrown hammer no longer loses its bound owner (the throw
  spawned a fresh stack, wiping the binding); resting hammers survive a world reload as resting
  instead of coming back as freshly-thrown projectiles that fly at their owner unprompted; a
  hammer that reached the ground with no owner (a death drop) can be summoned by any worthy player
  instead of being stranded forever; the summon picks the nearest matching hammer; Mjolnir is
  fire-immune; a resting hammer no longer damages whoever walks into it; switching to creative
  while Thor-flying no longer strips creative flight; logging out or dying mid-flight no longer
  leaves survival flight permanently enabled; and logging out or dying while suited up now restores
  your real armor first instead of destroying it.

**Phase 6 (this commit):** orientation, controls and tooltip pass from Phase 5 testing.

- **The hammer hangs head-down in the hand** instead of jutting forward head-first, in both third
  and first person -- the item model's hand transforms were rotated a quarter turn so the model's
  head end (which is built along +Y) points at the ground.
- **A thrown hammer flies head-first, not backwards.** The renderer was using vanilla's
  `ThrownTridentRenderer` rotation formula verbatim, but a trident's model is built point-*down*
  (spikes at negative Y) while Mjolnir's is built head-*up*, so that formula aimed the grip along
  the direction of travel. The pitch offset is negated to match.
- **A thrown hammer flies dead straight**, at 1.2 blocks/tick rather than 0.9. It used to arc like
  a trident: gravity is now switched off while airborne (it comes back the moment the hammer
  settles, so a dropped one still falls), and the throw speed is re-asserted every tick instead of
  bleeding away to `ThrowableProjectile`'s 1%-per-tick air drag.
- **A resting hammer stands upright on its head**, grip pointing at the sky, rather than lying flat
  at whatever angle it stopped at. It keeps the yaw it landed with; the pitch is discarded.
- **Mjolnir has a real tool tooltip.** It carries genuine attack-damage/attack-speed attribute
  modifiers (+9 damage, -3 attack speed, so the game renders its own "When in Main Hand" block),
  epic rarity, and hover text listing who it's bound to plus every ability and the key that
  triggers it. Keybind lines are `Component.keybind` so they follow rebinds in Options > Controls.
  Binding a hammer now also records the owner's *name* on the stack (a new `bound_owner_name`
  data component) -- a client has no way to resolve a UUID to a name, so the tooltip couldn't
  otherwise say who it answers to.
- **The hover hammer stays in the hand.** It used to be hidden from the hand and redrawn small and
  detached above the player's head; now it stays gripped and whirls on its own handle axis,
  full-size, in the raised fist.
- **Flight is no longer a keybind.** Double-tap the vanilla jump key in mid-air while holding
  Mjolnir to take off; double-tap again, or touch the ground, to land -- the same gesture creative
  flight uses. `G` is now unbound and free.
- **A sprint-flying player looks where they're going.** The 90-degree body tilt was dragging the
  head around with it, aiming the face at the ground; the head part now has that angle taken back
  out of it.

Default keybinds (Options > Controls > HeroCraft - Thor): `H` lightning strike, `J` call
Mjolnir, `K` suit up/down, `L` lightning laser (hold), `V` thunderclap, `B` storm call, `N`
parry. Flight is double-tap jump while holding Mjolnir. Shift-right-click Mjolnir to bind it to
yourself as its summon target.

## Dev loop

Run the dev client (hot-reloads resources, gives you a normal debug Minecraft window):

```
./gradlew runClient
```

Build a distributable jar:

```
./gradlew build
```

The jar lands in `build/libs/herocraft-<version>.jar`.

### Auto-copy to a CurseForge modpack instance

`gradlew build` will also copy the jar into a CurseForge App modpack's `mods` folder, if
configured. Set `modpackModsDir` in `gradle.properties` to the instance's mods folder, e.g.:

```
modpackModsDir=C:\\Users\\ethan\\curseforge\\minecraft\\Instances\\<InstanceName>\\mods
```

Leave it unset/commented to skip the copy step (default).

## Project layout

- `src/main/java` — common (client + server) code
- `src/client/java` — client-only code (rendering, keybinds, HUD)
- `src/main/resources/assets/herocraft` — textures, models, lang
- `src/gametest/java` — automated in-server tests (`./gradlew runGameTest`)
- Package root: `com.herocraft.mod`

Reference docs: [Thor](THOR_DESIGN.md) · [HeroPack powers](docs/HEROPACK_CONTENT_REFERENCE.md) ·
[Iron Man](docs/IRONMAN_REFERENCE.md) · [armour models](docs/ARMOR_MODELS.md) ·
[Zombie Raid](docs/ZOMBIE_RAID_REFERENCE.md) ·
[Spider-Man](docs/SPIDERMAN_REFERENCE.md)
