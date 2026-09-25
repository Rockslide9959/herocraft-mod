# Project Hero

**A large single-player-and-multiplayer superhero mod for Minecraft 1.21.1 (Fabric).** Become Thor,
Iron Man, Spider-Man, Max Steel, the Punisher, Wolverine or a giant Titan Shifter, bond with an alien symbiote, mutate one of 27
experimental superpowers, then put them to the test against world raids and a giant world boss.

Every hero is a full progression system, not a creative-only toy — you earn each one in survival,
power it, upgrade it, and can lose it again.

**Primary & Secondary powers.** Every power is either *Primary* — who you are: Thor, Iron Man,
Spider-Man, Max Steel, the Punisher, Green Lantern, Wolverine, the Titan Shifter and the mutations — or *Secondary* — an add-on that
rides on top. **You can hold two Primary powers at once** (v0.11.15): each hero power takes a slot, and
your mutations (up to 3, stacking as before) share one. Gaining a hero power strips every mutation and,
if you already hold two heroes, **replaces the oldest**; gaining a mutation keeps at most one hero beside
it. The Symbiote is the only Secondary power for now, and because it only works properly with
Spider-Man, gaining any other Primary power removes it.

**Keybinds (v0.11.17).** The six ability keys are now named **Ability 1–6** in Options › Controls and
default to **R, G, Z, X, C, V** (Ability 1 = R, 2 = G, 3 = Z, 4 = X, 5 = C, 6 = V — every move stays on the physical key it has always used; only the names changed); **H** is *Utility 1* (as Wolverine it deploys / retracts your claws) and **N** is *Utility 2*; **P** opens the squad
menu. If your saved controls still show the old letters, press *Reset* on those keys.

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
lift the hammer. Craters are now **very rare**, so villagers will help: **Sneak + right-click a villager
while holding a Lightning Rod** and trade **25 emeralds** for the direction (North, North East, …) of the
nearest free Mjolnir — walk that way and it leads you there. The hammer only budges for a **Hero of the
Village**: lift it while you have the effect and Mjolnir binds itself to you on the spot, the effect is
used up, and you become Thor (taking a Primary slot). Lift a hammer that is *already bound to another
player* and you can carry it while the effect lasts, but it does **not** bind to you and its owner is
untouched. Without the effect — or once it runs out — it will not move for anyone who isn't already Thor.
Creative players bypass it.

- **Mjolnir** flies straight and true when thrown, phases through terrain on the way home, stands on
  its head when it lands, and is **recalled to your hand from anywhere** — even out of another
  player's grip or an unloaded chunk.
- Bind a hammer to yourself with shift-right-click so it always answers your call.
- **Abilities:** Lightning Strike (with Chain Lightning), Lightning Laser (held beam), Thunderclap
  shockwave, Storm Call (a personal storm that follows you), Mjolnir Parry, and flight via
  double-tap-jump while holding the hammer, with speed-based flight poses.
- **Passives:** the Power of Thor while the hammer is bound to you — **+11 melee** bare-handed (Mjolnir itself
  hits for **11**), **+10 hearts**, **80% less damage from everything**, permanent **Regeneration I**, and immunity to
  falls and lightning. Your ability bar stays on screen while you are bound, even when the hammer is not in your hand.
- **Thor's Armour (v0.12.32):** press **H** and lightning strikes down on you as a black-and-crimson 3D GeckoLib
  armour set forms on your body (Shift+H still opens the power wheel). Press **H** again to dismiss it. It is
  conjured, never crafted: if it falls out of your inventory or you die, it despawns.

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
rare **symbiote meteor**, a rarer buried **containment lab**, or on a rare **symbiote-infected mob** you have to
hunt down — then right-click to bond.

- Toggle the **black suit** on and off (**H**) with a progressive, particle-covered suit-up.
- **Tendril abilities:** Tendril Strike, a hold-to-raise **Symbiote Shield**, a Spike volley, a
  20-block **Symbiote Lunge** (Ability 4 / X — you keep your momentum when it ends), **Symbiote Onslaught** (a charged Wither/Blind/Slow AoE
  ultimate), a hardened tendril **Blade**, a body-**Spikes** toggle, and a 25-block **Grapple** (sneak +
  Ability 4 / X) that needs something to hold onto.
- A **Normal Host** gets a **Biomass** health bar that drains alongside every hit and regenerates out of
  combat (70% slower while the suit is on); Regeneration II heals you whenever you are hurt, at a small Biomass cost. Symbiote Spider-Man gets none of
  these passives -- just the black suit, his extra abilities and a doubled Web Reserve. The suit is unbreakable while worn.
- **It protects its host.** A hit over 3 damage, a hit that would leave you under 5 hearts, or a fatal
  hit makes the Symbiote wrap you in the suit on its own — and if you *would* die it resurrects you for
  half its Biomass, hurls everything within 20 blocks away with massive tendrils and gives you
  Resistance for 20 seconds. Its voice appears above your hotbar. The suit stays on until you retract it.
- **Living armour:** wearing the suit cuts all damage you take by 10%. The suit forms over **2 seconds**, piece by
  piece, and a normal host visibly grows to **150% size** as it does (shrinking back when it retracts; Symbiote Spider-Man stays his own size). Crouch for
  5 seconds with the suit on and the Symbiote **camouflages** you completely.
- **Predator Vision:** a bonded host sees living things within 20 blocks outlined (hostile red, players
  dark purple, everything else dark blue) — visible to **you only**. **N** toggles it off and on.
- **Weaknesses:** sound attacks (Warden boom, bells, goat horns) tear the suit off and disable every
  Symbiote ability for 5 seconds; burning for more than 2 seconds deactivates the suit.
- **Symbiote Vial:** craft one from Iron Blocks and Glass. Sneak-use to bottle your own Symbiote, or
  right-click a free one; use the filled vial to bond again.

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

### 💚 Green Lantern
A permanent **Primary** power. Find a **Fallen Lantern Site** — a rare, damaged crater holding a
Dormant Power Ring — and pass the **Will Trial** (three enemy waves inside a green-particle boundary
that keeps everyone else out). Win, and the ring asks *"Are you afraid?"* — answer **no** to earn the
ring (right-click it to put it on; the item is used up), answer **yes** and the trial is cancelled so
someone else can try.

- A **Ring Charge** pool (10,000 points) fuels everything: ranged Ring Bolt/Continuous Beam, a
  Construct Fist and War Hammer Slam, flight, a directional shield or a 10-block Protective Dome that
  pushes out anyone who isn't in your squad, and **14 hard-light constructs** shaped on the fly — every
  one available from the moment you bond, with no cap on how many can be active at once.
- Hold **X to recite the Oath** ("Green Lantern's Light!") for a 22-second empowerment: double melee,
  double ability damage, double construct strength — at double the Ring Charge cost for everything.
- **No passive recharge at all.** The only way to refill the ring is a Personal Power Battery: recite
  the on-screen Oath — four lines, one at a time, in green — without moving, turning, taking damage or
  using any ability, and the ring fills instantly the moment you finish.
- A permanent passive Resistance I, suited or not. Suiting up also grants full **diamond-level armour**
  plus a flat melee damage bonus; flight trails a green hard-light streak the whole time it's active.
- Constructs are cheap to make and maintain, temporary rather than permanent building blocks — Mining
  Drill, Energy Blade and Carry Platform equip for free and only cost anything once toggled on. The Mining
  Drill breaks a 2x2 patch every **0.5 s** (v0.12.31).

### 🐺 Wolverine
A permanent **Primary** power — the **ascension of Super Regeneration**. Own Super Regeneration, craft an
**Adamantium Serum** (4 diamonds, 2 netherite ingots, 2 blaze powder, a golden apple) and use it: your
regenerative mutation is consumed and rebuilt as Wolverine.

- **Healing factor:** 4 HP/s, 8 HP/s below half health, 12 HP/s below a quarter — it never slows in combat.
  Below 15% health (or on a lethal hit) an **emergency surge** restores 30% of your health over 2 seconds
  (60 s cooldown).
- **Built to last:** 35% less physical damage, 75% knockback resistance, 75% less fall damage, strong Poison
  and Wither resistance, 4 melee damage (12 unarmed with claws out), +20% speed, +25% jump. Enemy monsters within 12 blocks glow for you
  alone.
- **Adamantium claws:** three curved blades per hand, deployed with **H** (Shift+H still opens the power
  wheel); no items can be held while they are out. R, G and V are area attacks. A lethal hit triggers a 3-minute
  death resurrection (10 s invulnerable but slowed and blinded, in a raw flesh body, then the skin grows back). A custom model that other players see, 12 damage bare-handed, and they break blocks like a sword
  (cobwebs at once, plants and leaves faster). Craft the yellow-and-blue **Wolverine Suit** costume, and
  when a Wolverine dies his body gives way to raw flesh and bone.
- **Six abilities on the standard keys:** **R** Claw Slash · **G** Cross Slash · **Z** Claw Dash (21-block
  lunge that grabs and drags the first enemy you hit) · **X** Berserker Rage (12 s of +50% damage, +30% speed, double healing; the bar fills 1% per hit dealt or taken) · **C** Frenzy (five rapid
  strikes) · **V** Adamantium Execution (60-damage finisher).

### 🗿 Titan Shifter
A permanent **Primary** power (v0.12.31, reworked in v0.12.32). Craft a **Titan Serum** (4 titanium-gold plates, 2
netherite ingots, 2 magma blocks, a nether star) and use it to unlock Titan Shifting — it never transforms you by
itself. Press **H** (with at least **90% Titan Energy**) to burst into an **11-block Titan** — a regular player-shaped
body and hit-box scaled up to eleven blocks (lightning, steam, a 3 s transformation); press **H** again to change back.
The old J key is gone.

- **A real creature, not a big player.** The Titan is its own entity with **500 HP, 25 armour, 8 toughness** and
  full knockback immunity; you ride it as its controller and take no damage yourself — the Titan takes the hits.
  Everyone in the world sees and can fight it. Fall damage ×0.1, fire ×0.2, explosions ×0.5; tiny hits are shrugged off.
- **Titan Energy (v0.12.32):** a 100-point bar. You need **90%** to transform; changing back (or being defeated)
  empties it and it refills **1% a second** while you are human. Inside the Titan the bar is spent by the Titan's
  **base regeneration** — 3 HP per second whenever it is hurt, costing 2 energy a second.
- **Abilities:** **R** Titan Punch (20; third swing of a combo = Heavy Punch 35; **Shift+R** = Titan Kick 30) ·
  **G** Heavy Smash (charge, then 50 in an area) · **Z** Titan Stomp (25, 6 blocks) · **X** Titan Leap (~3× a jump,
  landing 20 in 5 blocks; **Shift+X** = Titan Roar) · **V** Titan Roar (12 blocks: slow, weaken, scatter; bosses resist) ·
  **C** Titan Regeneration (10 HP/s for 10 s; **Shift+C** = Titan Hardening: 60% less damage for 8 s, crystal skin).
- **Heavy by design:** slow ground-shaking footsteps with dust and camera tremors, steam off the shoulders when hurt
  or healing, no terrain digging (it tramples leaves and plants; a server option lets attacks break weak blocks).
- **State machine:** human → transforming → Titan → reverting → human, or Titan → defeated → recovering → human.
  There is no lockout any more — the empty Titan Energy bar is the limit. Logging out inside a Titan drops you safely on the ground.
- Server-authoritative, GeckoLib-animated (idle, walk, run, punch, kick, heavy punch, smash, stomp, leap, landing, roar,
  hurt, regeneration, hardening, transformation, reversion, death), built to add more Titan types later. Everything is in
  `config/projecthero_titan_shifter.json`.

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

Every power can be given up in survival. Craft a **Power Suppressor** and sneak-use it to strip
**every** power you have — all hero powers, all experimental mutations, Mjolnir worthiness and a bonded
Symbiote (or bottle the Symbiote alone with a Symbiote Vial).

---

## Notes

- **Creative tabs:** Superheroes, Iron Man, Punisher.
- **`/squad`** is the one player-facing command (see Squads above); everything else is admin tooling.
- **Admin commands** (op-only) are just three, under `/projecthero`: `power grant|remove|stack <power>`
  (every power in the mod), `locate <structure>` (every mod structure) and
  `raid start|end|removetimer|advancetimer <supervillain|gravebound>`.
- Built on a shared GeckoLib armour-model pipeline and backed by 200+ automated in-game tests.
- **License:** All Rights Reserved. This is a personal project shared as-is; please don't redistribute
  or reupload the jar.

---

*Project Hero started as "just Thor and Mjolnir" and kept growing. Bug reports and feedback welcome.*
