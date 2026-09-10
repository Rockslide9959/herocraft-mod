# Project Hero

**A large single-player-and-multiplayer superhero mod for Minecraft 1.21.1 (Fabric).** Become Thor,
Iron Man, Spider-Man, Max Steel or the Punisher, bond with an alien symbiote, mutate one of 27
experimental superpowers, then put them to the test against world raids and a giant world boss.

Every hero is a full progression system, not a creative-only toy — you earn each one in survival,
power it, upgrade it, and can lose it again.

---

## Requirements

| | |
| --- | --- |
| **Minecraft** | 1.21.1 |
| **Mod loader** | Fabric (Loader 0.16+) |
| **Java** | 21 or newer |
| **Required dependencies** | [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api) · [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) |

Works in single-player and on dedicated servers. Power state is per-player and fully synced, so
several players can run different heroes at once. Almost everything is tunable through generated
config files (`config/projecthero*.json`).

---

## The Heroes

### ⚡ Thor
Find a naturally generated **Mjolnir crater** (locatable, with its own ambient lightning) and try to
lift the hammer. Only the **worthy** can pick it up or use it — worthiness is enforced everywhere,
and creative players bypass it.

- **Mjolnir** flies straight and true when thrown, phases through terrain on the way home, stands on
  its head when it lands, and is **recalled to your hand from anywhere** — even out of another
  player's grip or an unloaded chunk.
- Bind a hammer to yourself with shift-right-click so it always answers your call.
- **Abilities:** Lightning Strike (with Chain Lightning), Lightning Laser (held beam), Thunderclap
  shockwave, Storm Call (a personal storm that follows you), Mjolnir Parry, and flight via
  double-tap-jump while holding the hammer, with speed-based flight poses.
- **Passives:** the Power of Thor — bonus strength, damage resistance, and fire immunity while the
  hammer is bound to you.
- Craftable **Thor armour** rendered as a full 3D GeckoLib model.

### 🔴 Iron Man / Tony Stark
A permanent **Hero-Tier** power. Build an **Arc Reactor**, craft the **Stark Fabricator** and a
**Suit Platform**, then work up the mark ladder with **Blank Blueprints** (a linear
Mk1 → Mk2 → Mk3 → Mk4 → Mk5 → Mk6 → Mk7 progression).

- **Seven suits**, each with its own stats — energy capacity, integrity, flight speed, strength,
  self-repair, air supply — from the primitive cave-built Mark 1 to the movie-style Mark 5 suitcase
  and the top-tier Mark 7.
- **Call your armour** in from a Suit Platform: the pieces fly to you and assemble in sequence, even
  across distance or unloaded chunks.
- **Weapons & systems:** repulsors and charged repulsors, Unibeam, rockets and one-at-a-time
  micro-missiles, flares, a terrain-cutting wrist laser, the Repulsor Shield, a Mark 7 weapon wheel,
  tiered flight energy costs, helmet night vision, air tanks for diving, a toggleable mob-highlight
  targeting view, and a retractable helmet faceplate (**H**).
- **Suit energy and integrity** are real resources — a depleted suit slows and weakens you until it
  recharges on a platform. **Protocol Phoenix** is a passive emergency resurrection that recalls your
  best available suit when you would otherwise die.
- The **Repulsor** doubles as a wearable gadget: put one in your boots slot for flight and a repulsor
  blast with no suit at all.
- Everything is fully survival-craftable and shows up in JEI/EMI.

### 🕷️ Spider-Man
Not granted directly — it is what the **Spider Climbing / Adhesion** experimental mutation *grows
into*. Craft an **Arachnid Mutagen** and use it while you already carry that adaptation.

- **Six web abilities** paid for out of an organic **Web Reserve**, not an ammo item: Web Swing, Web
  Zip, Web Shot, Web Yank, Web Net, and a wall-crawl toggle.
- **Hybrid web-swinging** physics — real anchors on terrain give long fast arcs; open ground still
  gives you something to swing from so traversal never stalls, and you keep every bit of momentum on
  release.
- Full **wall and ceiling crawling** (your model flips upside-down for everyone to see), a double
  jump, and a sneak-jump super-leap.
- **Spider-Sense:** a directional threat warning HUD, a chance to auto-dodge incoming attacks, catch
  or deflect projectiles, hit enemies through their own cobwebs, and a per-viewer red glow on any mob
  hunting you.
- **Zero fall damage.** Craftable **Spider-Man Suit** with a removable mask (**H**).

### 🖤 The Symbiote
An **upgrade for Spider-Man**, or a standalone power for a **Normal Host**. Find it in a crashed
**symbiote meteor**, a buried **containment lab**, or on a rare **symbiote-infected mob** you have to
hunt down — then right-click to bond.

- Toggle the **black suit** on and off (**H**) with a progressive, particle-covered suit-up.
- **Tendril abilities:** Tendril Strike, a hold-to-raise **Symbiote Shield**, a Spike volley, a
  directional **Leap**, **Symbiote Onslaught** (a charged Wither/Blind/Slow AoE ultimate), a hardened
  tendril **Blade**, a body-**Spikes** toggle, and a **Grapple** (sneak + X).
- A **Normal Host** gets a **Biomass** health bar that soaks part of every hit and regenerates out of
  combat; a Spider-Man host keeps their own Web Reserve and doubles it. The suit is unbreakable while
  worn.
- **Weakness:** sustained fire or lava severs the bond — about five seconds of continuous burning and
  the symbiote lets go.

### 🔵 Max Steel
Find **Steel** — a floating alien companion hovering over a **crash site** — and bond by spending 30
experience levels (costs 5) or a crafted **T.U.R.B.O. Stabilizer**.

- **Go Turbo** to summon the nanotech suit (**N**), running on a rechargeable **T.U.R.B.O. energy**
  pool.
- **Six Turbo modes / abilities**, each with its own suit model: Turbo Blast (a fast energy bolt,
  tap or charged), Turbo Strength (heavy punch + shockwave, crouch-block), Turbo Speed (dash), Turbo
  Flight (with blue elytra wings), Turbo Stealth, and the chargeable **Turbo Cannon** that launches
  you as a guided projectile.
- Running out of energy forces an **overload** and a lockout. An **emergency totem** revive fires
  once you have enough energy banked. Retractable faceplate (**H**).

### 💀 The Punisher
A **non-superhuman** Hero-Tier power: firearms, explosives, gear and training. Complete the
**Vigilante Training Manual** objectives, or find a **Vigilante Safehouse** bunker.

- **A full firearm engine:** Pistol, Rifle, Shotgun and a scoped Sniper. **Left-click fires,
  right-click aims**, tap **R** to reload / hold for the Tactical Satchel. Server-authoritative
  hitscan bullets with spread, recoil bloom, range falloff, **headshots**, and a hurt-cooldown bypass
  so every shotgun pellet and fast rifle round lands full damage.
- **Abilities:** Tactical Satchel, Frag Grenade (cook-and-throw), Tactical Roll (with i-frames),
  Suppressive Fire, **Adrenaline** (regen/haste/speed burst with a nausea crash), and remote **C4
  charges** (place with C, detonate with Shift+C).
- **Passives:** a per-gun regenerating ammo reserve (no ammo item to carry), plus infinite arrows and
  double bow/crossbow damage while powered.
- Netherite/diamond-tier **tactical armour** (no helmet), craftable only by Punisher players.

---

## 27 Experimental Powers · 162 Abilities

A whole second progression system layered on top of the named heroes. **Mutate** a power by brewing
and drinking an **experimental serum**, studying **research notes**, surviving an **exposure event**,
or using a **lab device**. Rare **research site** structures hold the recipes and hints.

Powers **stack** — own up to three at once and all their passives and toggled modes run permanently;
the one you have *selected* drives your six ability keys. A **HeroPack Guide** book documents every
one, and there is an in-game "your power" screen on **I**.

<details>
<summary><b>The full list</b></summary>

Super Strength · Laser Vision · Flight · Super Speed · Geokinesis · Crystalkinesis · Electrokinesis ·
Pyrokinesis · Cryokinesis · Telekinesis · Teleportation · Super Regeneration · Super Durability · Sonic
Scream · Invisibility & Light Manipulation · Spider Climbing / Adhesion · Elasticity · Density
Manipulation · Shadow Manipulation · Energy Absorption · Shockwave Manipulation · Plant Manipulation
(Chlorokinesis) · Gravity Manipulation · Wind Manipulation · Water Manipulation · Magnetic
Manipulation · Size Manipulation

</details>

Each power has six abilities across the universal slot roles (Primary / Secondary / Movement /
Ultimate / Utility / Special), plus passives, plus **power combos** when the right two are owned
together (meteor slam, wet-electric, and more). Powers you don't have a full handler for simply say
so — they never break your game.

---

## World Events & Bosses

### The Zombie Raid
Catch the 20-minute **Gravebound Curse** from a naturally generated **Graveyard** or a rare **Cursed
Zombie**. Then either burn an Enchanted Golden Apple to escape it, or survive **twelve escalating
waves** and three **Powered Zombie Bosses** — each carrying a real experimental power and fighting
with it (telegraphed casts, aerial fights, blitz charges). The raid darkens the sky around you and
tracks progress on a boss bar.

### The Supervillain Village Raid
A rare **Pillager Spy** that attacks you inside a village triggers a countdown, then **six waves** —
five escalating raider waves, then the **Empowered boss** itself, with a random power and a random
cosmetic variant (Chimera, Arsenal, Omega Mage). Rewards include a Villain Cache, power fragments,
and variant trophies.

### The Titan
A world boss that starts as an ordinary-looking zombie and, at low health, transforms into an
**~18-block giant**. It actively hunts players and fights with a telegraphed attack state machine —
every attack winds up for two full seconds with its own distinct charge-up particle tell before it
lands. Its moves: a single-target **Punch**, a wide backhand **Sweep**, ground-shaking **Stomp** and
**Slam**, a long-range ground **Shockwave**, a player **Grab**, an **AoE boulder throw**, a full-tilt
**Charge**, and an always-on melee swipe so you can't just hug its leg. Every blow it lands is
scaled to your health so a healthy, armoured player is hit hard but never one-shot. Strong, but
readable; tunable in `config/projecthero_titan.json`.

---

## Squads

These powers are built to level a hillside, which makes playing together awkward without a way to say
"not them". **`/squad create <name>`** starts a squad and **`/squad invite <player>`** offers a place;
squadmates simply cannot hurt each other — not with a sword, an arrow, a grenade, a repulsor beam or a
55-damage Psychic Detonation. Press **P** for the roster: every teammate's health, whichever hero
identity or mutation currently holds their ability slots, their coordinates, and how far away they are
and in which direction. Squads hold up to 12 and survive a restart.

---

## Removing powers

Every power can be given up in survival. Craft a **Power Suppressor** and sneak-use it to strip Tony
Stark, Max Steel, the Punisher, all experimental mutations, and reset Mjolnir worthiness. The
Symbiote is removed by fire.

---

## Notes

- **Creative tabs:** Superheroes, Iron Man, Punisher.
- **`/squad`** is the one player-facing command (see Squads above); everything else is admin tooling.
- **Testing/admin commands** all live under one root: `/projecthero <thor|ironman|spiderman|symbiote|`
  `maxsteel|punisher|titan|power|hero|raid|supervillainraid|zombieraid> ...` (`/projecthero hero` is the
  survival self-service branch any player can use; the rest are op-only). Plus `/locate structure`
  support for all mod structures.
- Built on a shared GeckoLib armour-model pipeline and backed by 200+ automated in-game tests.
- **License:** All Rights Reserved. This is a personal project shared as-is; please don't redistribute
  or reupload the jar.

---

*Project Hero started as "just Thor and Mjolnir" and kept growing. Bug reports and feedback welcome.*
