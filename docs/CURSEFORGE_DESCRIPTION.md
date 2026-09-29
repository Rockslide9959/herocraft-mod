# Project Hero

**A large single-player-and-multiplayer superhero mod for Minecraft 1.21.1 (Fabric).** Become Thor,
Iron Man, Spider-Man, Max Steel, the Punisher, Wolverine, All Might or a giant Titan Shifter, bond with an alien symbiote, mutate one of 27
experimental superpowers, then put them to the test against world raids and a giant world boss.

Every hero is a full progression system, not a creative-only toy — you earn each one in survival,
power it, upgrade it, and can lose it again.

**Primary & Secondary powers.** Every power is either *Primary* — who you are: Thor, Iron Man,
Spider-Man, Max Steel, the Punisher, Green Lantern, Wolverine, the Titan Shifter, All Might and the mutations — or *Secondary* — an add-on that
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

- **Mjolnir** flies straight and true when thrown (v0.13.4: 10% faster both ways; v0.13.6: a thrown hit
  now deals **18**, up from 11), phases through terrain
  on the way home, stands on its head when it lands, and is **recalled to your hand from anywhere** —
  even out of another player's grip or an unloaded chunk. Attack speed raised to 1.1.
- Bind a hammer to yourself with shift-right-click so it always answers your call.
- **Abilities (v0.13.4 damage pass):** Lightning Strike (22) and God of Thunder's Wrath (100, drains 100
  Storm Energy, now calls down a barrage of bolts instead of one) both snap onto a nearby enemy on a
  near-miss rather than just the raw crosshair point; Lightning Beam (8/tick) and Chain Lightning (18)
  render as a genuine crackling, jagged bolt instead of a line of particles, and (v0.13.6) crackle from the
  hammer in your hand rather than your chest, with a thicker, blockier arc closer to vanilla lightning;
  Thunderclap (22 damage) moved
  to **Shift+V**, and plain **V** is a new ability, **Hammer Volley** (v0.13.6: 20% slower in flight, and
  circles you at a 3-block radius with nothing in range): the hammer flies out of your hand
  and autonomously strikes every enemy within 25 blocks in sequence (orbiting a lone target between hits)
  for 12 seconds, or until you call it back early — 32 s cooldown. Storm Energy raised to 300 to fuel it
  all. Plus Storm Call (a personal storm that follows you), Mjolnir Parry, and flight via double-tap-jump
  while holding the hammer, with speed-based flight poses.
- **Passives:** the Power of Thor while the hammer is bound to you — **+11 melee** bare-handed (Mjolnir itself
  hits for **11**), **+10 hearts**, **80% less damage from everything**, permanent **Regeneration I**, and immunity to
  falls and lightning. Your ability bar stays on screen while you are bound, even when the hammer is not in your hand.
- **Thor's Armour (redesigned v0.13.3):** press **H** and lightning strikes down on you as a black-and-crimson
  3D GeckoLib armour set forms on your body (Shift+H still opens the power wheel). Press **H** again to
  dismiss it. It is conjured, never crafted: if it falls out of your inventory or you die, it despawns.

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
An **upgrade for Spider-Man**, or a standalone power for a **Normal Host**. It is a living creature now (v0.13.19): a
pool of **black goo** that crawls along the ground, climbs walls, flees fire and hunts for a host -- reach a mob and it
**takes control of it**, turning it into a Symbiote Host until that mob dies and it crawls free again. Find it by
breaking the **Symbiote Meteorite** at the heart of a crashed meteor (it escapes into the world), trapped in the cell of
a buried **containment lab** (it can't get out), or riding a **symbiote-infected mob** you have to hunt down -- then
right-click it to bond.

- Toggle the **black suit** on and off (**H**): the living armour materialises over you **one pixel at a time**, chest
  first, then limbs, then head, while you throw your arms out.
- **Every move comes out of your hands** as a real living **tendril** or **spike**, with its own animation:
  **Tendril Strike** (R, 15 damage, 30 blocks -- it can miss), **Tendril Sweep** (Sneak+R), **Symbiote Spike** (G, a real
  projectile: 14 damage + Wither V for 5 s, 4 s cooldown) and the **Spike Fan** (Sneak+G, five spikes, 10 s), the
  20-block **Symbiote Lunge** (X) and **Grapple** (Sneak+X), **Tendril Barrage** (Z, a flurry of tendrils down your aim
  -- it can miss), the **Symbiote Blade** (V -- a real blade grows out of your hand) and **Symbiote Shield** (Sneak+V),
  **Symbiote Spikes** (C) and **Tendril Grab** (Sneak+C, C again to throw).
- **Symbiote Onslaught** (Sneak + hold Z): a pool of living black spreads under you while tendrils claw up out of it,
  then it erupts -- a crown of huge tendrils and a tendril spearing up into every enemy within 9 blocks: 20 damage,
  Wither III, Blindness, Slowness IV and Weakness II. 90-second cooldown.
- A **Normal Host** gets a **Biomass** bar (shown as "Biomass %" under the ability keys). It drains alongside every hit,
  regenerates out of combat, and **feeds the Blade (0.3/s) and Shield (0.5/s)**, which have no time limit any more.
  Dying no longer refills it. Suited, your unarmed hits deal +5. Symbiote Spider-Man gets none of these passives -- just
  the black suit, his extra abilities and a doubled Web Reserve.
- **It protects its host.** A heavy or fatal hit makes the Symbiote wrap you in the suit on its own -- and if you *would*
  die it resurrects you, hurls everything within 20 blocks away with massive tendrils and gives you Resistance for 20
  seconds. That costs no Biomass, but it can only happen **once every 10 minutes**.
- **Living armour:** the suit cuts all damage you take by 10% and a normal host grows to **150% size**. Crouch for 5
  seconds with the suit on and the Symbiote **camouflages** you completely.
- **Predator Vision:** a bonded host sees living things within 20 blocks outlined -- visible to **you only**. **N**
  toggles it.
- **Weaknesses:** sound attacks (Warden boom, bells, goat horns) tear the suit off and disable every Symbiote ability for
  5 seconds; burning for more than 2 seconds deactivates the suit.
- **Agent Venom (v0.13.11):** the Symbiote also shares a host with **the Punisher** (see the Punisher below).
- **Symbiote Vial:** craft one from Iron Blocks and Glass. Sneak-use to bottle your own Symbiote, or right-click a free
  one; use the filled vial to bond again.

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
- **Agent Venom (Punisher + Symbiote, v0.13.11):** bond with a Symbiote as the Punisher and **H** wraps you in the
  **Agent Venom suit** (diamond-plus protection, +25% melee, +15% speed and jump). Your guns and gadgets all still
  work, and the suit adds: **Sneak+X Tendril Swing** (haul yourself to any block up to 36 away), **Sneak+Z Tendril
  Snatch** (drag a mob to you, bound, and rip the weapon out of its hand), **Sneak+V Symbiote Unleashed** (10 s of
  +50% melee, +20% speed and life-stealing punches), **Symbiote Rounds** (+20% gun damage, bullets lash targets with
  tendrils) and **Living Ammunition** (reserve regenerates 3x, reloads 25% faster). Fire and loud noises still drive
  the Symbiote off.

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

- **Healing factor (v0.12.40):** heals 6 HP/s, but every HP is paid from a **250 HP Healing Factor pool** (green bar on the HUD with its %). At 0 the healing factor is off until the pool recharges; it refills **5 HP/s, only after 5 s without taking damage**. A 3-pixel HUD marker beside the bar shows the **Death Surge**: orange = ready, dark grey = recharging. A lethal hit triggers the Death Surge (3-minute cooldown).
- **Lethal falls (v0.12.43):** a fall that would kill you leaves you at half a heart; the Healing Factor pool soaks up to **100** of the damage, your legs turn to raw flesh and you get **Slowness VI for 20 s**, then your skin phases back in over another 20 s (needs pool left).
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
  strikes) · **V** Adamantium Execution -- hold 5 s to charge, release to fire (v0.13.4: no more wind-up
  freeze): whatever you're looking at within 10 blocks glows red for you alone and Wolverine launches
  straight at them for a 60-damage finisher.

### 🗿 Titan Shifter
A permanent **Primary** power (v0.12.31, reworked in v0.12.32, updated in v0.12.34, v0.12.35 and v0.12.36). Craft a **Titan Serum** (4 titanium-gold plates, 2
netherite ingots, 2 magma blocks, a **golden apple**) and use it to unlock Titan Shifting — it never transforms you by
itself. Press **H** (with a **full Titan Energy bar**) to burst into an **11-block Titan** — a regular player-shaped
body (new skin in v0.12.35) and hit-box scaled up to eleven blocks (lightning, steam, a 3 s transformation); press **H** again to change back.

- **A real creature, not a big player.** The Titan is its own entity with **500 HP, 25 armour, 8 toughness** and
  full knockback immunity; you ride it as its controller and take no damage yourself — the Titan takes the hits.
  Everyone in the world sees and can fight it. Fall damage ×0.1, fire ×0.2, explosions ×0.5; tiny hits from players are shrugged off,
  but **ordinary mobs ignore its armour and hurt it normally** (mobs hunting you attack the Titan).
- **Titan Energy:** a 100-point bar shown as a percentage. **Titan Shift Ready [H]** appears at 100%; changing back (or being defeated)
  empties it and it refills **1% a second** while you are human. Your **base form** heals with **Regeneration II** (v0.12.35), which **drains 1.5% Titan Energy a second** while it is healing you (the bar does not refill meanwhile, and at 0% the healing stops) — the Titan has none. Trying to shift on a partly empty bar flashes **"Titan Form exhausted, Recharge energy"** in red above your hotbar.
- **Emergency Titan (v0.12.39):** with at least **30%** Titan Energy (but under a full bar), **hold H for 5 seconds** — electricity crackles around you — and you shift into a **pale, 7-block Emergency Titan that is 40% weaker in everything** (health, armour, speed, damage). It lasts **at most 2 minutes**: a hairline bar above the *Titan Shifter* text drains, and you are forced out when it empties. Afterwards Titan Energy refills **3× slower**, you get **no passive regeneration until 50%**, and you cannot shift again (normally or in an emergency) until the bar is back at **100%**.
- **Roar sight & regen toggle (v0.12.43):** the Titan Roar outlines everything within **50 blocks in blue** for you alone (10 s), and **Shift+N** (plain **N** if no other power claims it) in human form toggles the passive regeneration on/off.
- **Changing back (v0.12.36):** you climb out through the back of the Titan's neck and drift down; the Titan's body stays where it stood, **steams every 3 seconds and dissolves over one minute**, breaking apart piece by piece (v0.12.39: each piece now shrinks fully away before it is removed). The Titan takes **no fall damage**.
- **Running (v0.12.34):** hold **Sprint** while walking for 3 seconds and the Titan breaks into a run.
- **Shoulder ride (v0.12.34):** a squad-mate can right-click the Titan to sit on its shoulder (two seats) and ride along; sneak to hop off.
- **Grab & bite (v0.12.34):** **N** picks up the mob you are looking at, **N** again eats it (26 damage, 10 s recharge; restores your hunger and saturation and the Titan heals **5 HP/s for 5 s**), **Shift+N** sets it gently down.
- **Abilities:** **R** Titan Punch (20; third swing of a combo = Heavy Punch 35; **Shift+R** = Titan Kick 30) ·
  **G** Heavy Smash (charge, then 50 in an area) · **Z** Titan Stomp (25, 6 blocks) · **X** Titan Leap (~3× a jump,
  landing 20 in 5 blocks; leap while **sprinting** to launch about twice as far; **Shift+X** = Titan Roar) · **V** Titan Roar (32 blocks, ground level and up: Weakness II + Slowness III 12 s, Nausea 3 s, Blindness 2 s, Mining Fatigue 3 s, mobs scattered; bosses resist) ·
  **C** Titan Regeneration (10 HP/s for 10 s; **Shift+C** = Titan Hardening: 60% less damage for 8 s, crystal skin).
- **Yellow lightning (v0.13.11):** the bolt that strikes when you transform is golden yellow, and the electricity that builds while you hold **H** for an Emergency Shift is yellow too.
- **HUD (v0.12.34, restyled v0.12.35/36):** bars have no border and the Titan Energy bar is yellow; base form shows Titan Energy (thin bar, %); Titan form shows the six ability keys, a thin Titan HP bar with **HP / 500** and **Revert Form [H]**.
- **Heavy by design:** slow ground-shaking footsteps with dust and camera tremors, steam off the shoulders when hurt
  or healing, no terrain digging (it tramples leaves and plants; a server option lets attacks break weak blocks).
- Server-authoritative, GeckoLib-animated, built to add more Titan types later. Everything is in
  `config/projecthero_titan_shifter.json`. Admins: `/projecthero power grant titan_shifter`.

### 💪 All Might / One For All
A permanent **Hero-Tier Primary** power (v0.12.33, reworked in v0.12.34, craftable costume in v0.12.36) — the mod's strongest pure-strength hero. Craft a **Vestige of One For All** (4 titanium-gold
plates, 2 enchanted golden apples, 2 diamond blocks, a **totem of undying**) and use it. The **All Might costume** (a new armour model: All Might's Costume + All Might's Trousers, both craftable from wool) is not given to you: in the **Power Form** press **N** to open the **costume locker**, leave the pieces in its two slots (costume and trousers), and they are put on automatically every time you transform (and go back into the locker when you change back). Your own head and hat layer stay visible.

- **Two forms (H):** **Base Form** is a plain player (no abilities, no bonuses). **Power Form**: you grow to **2.7 blocks** over one second, venting steam, with
  **13 melee, a 6-block hit range, 40 max HP** (health % carries over), **50% less damage, no fall damage, Speed III, slow self-healing (1 HP / 4 s), a 3-block jump**.
- **OFA (v0.12.39):** a **300-point timer** (5:00, shown as m:ss on the HUD). **Being in the Power Form drains 1 a second**; only the **Base Form** refills it, **1 every 2 seconds**. The Smashes and Leap are free; only **Plus Ultra (50)** and **United States of Smash (100)** spend OFA. Power Form self-healing is now a slow **1 HP every 4 s**. Below 30 you vent steam.
- **Power Form abilities:** **R Detroit Smash** (18 dmg, 10-block air-pressure reach) · **Shift+R New Hampshire Smash** (20, plus a 20-block forward blast) · **G Texas Smash** (24, an 18-block widening wave) · **X Leap** (launches along your look direction, 1.5 s cooldown -- **no cooldown while Plus Ultra is active**) ·
  **Z United States of Smash** (hold 5 s with a charge bar; an AoE: 75 dmg: a 25-block forward air blast, then a 35-block shockwave all around you and a large crater; 100 OFA (once cast) and a **75 s cooldown**, both paid only once it is cast) · **V Carolina Smash** (a 15-block ground slide that follows your look direction the whole way, 20 dmg) ·
  **C Plus Ultra** (50 OFA, lasts exactly **22 s**, every move +30%; 20 s cooldown after it ends; running out of OFA drops you back to Base Form).
- **Armour (v0.12.43):** the Power Form wears only the All Might armour — regular armour tears off when you transform (**-50 durability**, unequipped and dropped) and is refused afterwards. Particles are now **gold**.
- **Passives (Power Form):** air-burst punches, hard-landing shockwaves, **no fall damage at all**, bosses lose at most 10% per Smash. Admins: `/projecthero power grant all_might`.
- Server-authoritative; every number is in `AllMightConfig`. Full details in `docs/ALLMIGHT_REFERENCE.md`.

### 💚 The Hulk
A **Hero-Tier Primary** power. Find a rare **Gamma Lab** ruin in the overworld and drink the **Gamma Serum** from its chest.

- **Rage (0-100):** every point of damage you take is **1% rage**; as Banner you cool off **2% a second** after 5 s
  without being hurt. Past **75** Banner starts pouring off green gamma. At **75** press **H** to let the Hulk out -- a
  quick change, and he is **yours to command**. At **100** he comes out on his own: Banner **drops to his knees**
  clutching his head and the Hulk slowly takes him over, then rises roaring. The Hulk **phases onto you as you grow** and
  back off as you shrink. As the Hulk, every hit you land adds **2%**, and rage only burns down (0.75% a second) once
  you have been out of combat for 5 s.
- **The Hulk:** your own Hulk model at 1.8x size, +50% speed, **20-damage punches** with big knockback, +40 health,
  **diamond-level armour of his own**, fast regeneration, **immune to fire, arrows and falls**, tough against lava and
  explosions, **never caught in webs** (sprinting tears them down), and hands that dig like stone tools. Armour you wear
  bursts off when he comes out.
- **R Power Punch** (30) · **G Ground Smash** (30 + a crater) · **Z Thunderclap** (22, 25-block cone) · **Shift+Z HULK SMASH** (hold 5 s:
  100 damage, a huge crater) · **X Super Leap** (up to 70 blocks) · **C Charge** (8 s rampaging run, 20 to everything in
  the way) · **V Grab** (pick up and throw a mob, Shift+V crush it, or Shift+V tear up a chunk of earth to throw -- and
  **carry or throw your squad-mates**, who land unhurt).
- **The Hulk refuses to die:** Banner **can't be killed** -- every fatal hit brings the Hulk bursting out at full
  health, no cooldown (so the HUD no longer shows a death-save dot). To kill a Gamma player you have to beat the Hulk.
- **Keep control:** only a Hulk who came out **on his own** fights you -- stop hitting things and he starts to take over;
  answer the key prompts or he goes on a **rampage**, hunting anything out in the open up to **100 blocks** away. Hold
  **N** to calm down with a breathing minigame. Squad-mates can **ride his back**.
- The Hulk can't lift Mjolnir, and nobody is both Thor and the Hulk. Everything is in `config/projecthero_hulk.json`.

---

### 🌙 Moon Knight (coming soon)
Khonshu's fist is being built right now. v0.13.19 lays the foundations -- the pact with Khonshu, a **lunar power** that
makes him strongest under a full moon (and weaker by day or underground), a **Vengeance** meter fed by protecting
villagers and travellers at night, the **Fracture** between his three alters and **Khonshu's Resurrection**. The suit,
the cape, the six abilities and the desert **Temple of Khonshu** arrive in the next updates.

## 27 Experimental Powers · 162 Abilities

**Random-power serums (v0.13.18):** the **Mutagenic Serum** grants a random Experimental power, the **Heroic Serum** a random
Hero-Tier power and the **Prismatic Serum** any power at all -- always one you don't have yet (kept if nothing new fits).

A whole second progression system layered on top of the named heroes. **Mutate** a power by brewing
and drinking an **experimental serum**, studying **research notes**, surviving an **exposure event**,
or using a **lab device**. Rare **research site** structures hold the recipes and hints.

Powers **stack** — own up to three at once and all their passives and toggled modes run permanently;
the one you have *selected* drives your six ability keys. The **Guidebook** (Book + Feather + Spider Eye + Emerald; new players are reminded to craft it when they first spawn into a world) documents every
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

### The Abyssal Behemoth
A very rare, naturally-spawning **endgame Nether boss** — a nine-block, floating, horned, ancient
mutated Ghast with a fully custom model, animations and 1,000 HP, never just "a Ghast with bigger
numbers." No damage resistance — every hit you land counts, and the boss bar tracks its real HP.
Nine attacks: **Abyssal Fireball** and **Hellfire Barrage** at range, **Magma Rain** and
**Netherstorm** across the whole battlefield, a sweeping **Abyssal Beam**, **Cinder Tether** and
**Sovereign Descent** to force it down for a real melee window, **Hellwind** to punish standing
underneath it, and **Skyfall** to punish hovering too high above it for too long. Three phases —
Netherfury at 60% health, Cataclysm at 25% — make it faster and angrier as the fight goes on, and a
five-minute Abyssal Enrage keeps a long fight from being cheesed. You do not need flight to beat it.
Defeating it drops a unique **Abyssal Core** plus netherite scrap, ghast tears and blaze rods. At most
one exists per Nether dimension at a time; tunable in `config/projecthero_behemoth.json`.

### The Oathbreaker
A summoned **4-block knight boss** -- the hardest fight in the mod. Craft a **Knight's Soul** (an Abyssal Core surrounded by 8 Grave
Essence) and right-click it on a **Respawn Anchor** to call him forth. **4,000 HP** (more with more players nearby), a
hidden **poise** meter you can break to stagger him, and **three phases**: the disciplined Knight (Stance Dash, Four-Strike
Combo, Oath Guard parries, Leaping Cleave), the Forsworn at 60% (Soul Rend, Chains of the Forsworn, a fifth combo hit,
feints) and the Oathless below 25% (Judgement slam, the unblockable Execution grab, phantom echoes of his swings).
**v0.13.17:** every move hits ~30% harder, and he hits a further **10% harder in phase 2** and **25% harder in phase 3**.
**v0.13.19:** two new attacks -- the **Oathbound Whirlwind** (two full 360-degree spins that punish anyone circling behind
him) and **Grave Geysers** (he plunges his sword and soul-fire columns erupt under every player within 30 blocks). He
tracks players from **50 blocks** away, even out of sight, and his aggro follows whoever is actually hurting him -- you
can no longer beat on him while a friend kites him around.
Defeating him drops a netherite sword, Grave Essence, a guaranteed Abyssal Core and the Broken Oath.


### Apokolips Invasion -- the Darkseid Raid (v0.13.18)
An **endgame co-op raid for up to 8 heroes** that never happens on its own. Craft a **Boom Tube Beacon** (a Nether Star,
4 Supervillain Tokens, 2 Echo Shards and 3 Crying Obsidian -- or a Nether Star, 4 Omega Shards and 4 Crying Obsidian) and
use it: every survival player within 48 blocks joins the roster, the sky turns Apokolips red and a 64-block arena opens.
- **Five Parademon waves** pour through Boom Tubes: winged Parademons that take to the air after flyers, Ranged gunners
  that hover level with you and lead their shots, then Elites and Brutes.
- **DARKSEID -- LORD OF APOKOLIPS** steps out of a giant Boom Tube, shielded by **four Mother Boxes**. Right-click a box and
  stay beside it for 10 seconds to disrupt it; ignore one for 90 seconds and it **overloads** (he heals, it explodes,
  Parademons pour out, the ground burns).
- **Three phases** (3,000 HP + 600 per extra participant): **Omega Beams** that curve after you (blocks stop them), the
  **Omega Barrage**, a **Godly Ground Slam** that reaches flyers, **Darkseid's Grip**, the **Omega Teleport**, the
  **Apokoliptian Charge** and Boom Tube reinforcements; at 60% the **Omega Effect** adds a rotating **Omega Beam Sweep**;
  at 25% **Omega Rage** brings **Omega Annihilation** -- hit him hard enough during the 5-second charge to stagger him,
  or take an arena-wide blast.
- Dying isn't the end: respawn and return after 25 seconds. The raid is lost only if every hero is down at once. After
  15 minutes he soft-enrages instead of wiping you. Flight is never disabled -- he just has answers for it.
- **Rewards** for every official participant: Darkseid's **Omega Core**, **Omega Shards**, a rare **Mother Box** (a personal
  Boom Tube home) and a very rare **Omega Relic** (24 charges of homing Omega Beams). Advancements *Anti-Life* and
  *Apokolips Falls*. Everything is tunable in `config/projecthero_darkseid.json`.
- **v0.13.19:** **five** Parademon waves (bigger, and Parademons are 20% tougher and hit 15% harder), Parademons have
  **wings**, and gunners strafe and circle while they shoot. Darkseid attacks more often, calls reinforcements more
  often, and fires Omega Beams more often -- and the beams now **zig-zag** through the air before homing in. Once every
  Mother Box is down, some of them **reawaken** 70-100 seconds later (with a warning), so you have to keep shutting them off.

---

## Squads

These powers are built to level a hillside, which makes playing together awkward without a way to say
"not them". **`/squad create <name>`** starts a squad and **`/squad invite <player>`** offers a place;
squadmates simply cannot hurt each other — not with a sword, an arrow, a grenade, a repulsor beam or a
55-damage Psychic Detonation. Press **P** for the roster: every teammate's health, whichever hero
identity or mutation currently holds their ability slots, their coordinates, and how far away they are
and in which direction. Squads hold up to 12 and survive a restart.

- **Locator Bar (v0.13.4, reworked into a hairline bar in v0.13.5):** a thin Bedrock Edition-style line
  across the top of your screen with every online, same-dimension squadmate's face sitting on it,
  sliding toward the middle as you turn to face them (pinning to the edge when they're behind you) and
  growing or shrinking with distance -- closer reads bigger. Toggle it from a button on the **P** roster
  screen.

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
  `raid start|end|removetimer|advancetimer <supervillain|gravebound|darkseid>` (plus `raid darkseid status|enrage|attack|stagger`).
- Built on a shared GeckoLib armour-model pipeline and backed by 200+ automated in-game tests.
- **License:** All Rights Reserved. This is a personal project shared as-is; please don't redistribute
  or reupload the jar.

---

*Project Hero started as "just Thor and Mjolnir" and kept growing. Bug reports and feedback welcome.*
