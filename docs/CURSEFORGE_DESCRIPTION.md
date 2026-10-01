# Project Hero

**A huge superhero mod for Minecraft 1.21.1 (Fabric).** Become Thor, Iron Man, Spider-Man, Max Steel, the Punisher,
Green Lantern, Wolverine, a Titan Shifter, All Might, the Hulk, Moon Knight, a Super Soldier or a Kryptonian, bond with a living alien Symbiote, or mutate
experimental superpowers. Then put them to the test against world raids, the Oathbreaker and Darkseid himself.

Every hero is a full survival progression, not a creative-only toy: you earn each power, fuel it, master it, and can
lose it again.

💬 **[Join the Discord](https://discord.gg/3jzdAmmfnC)** for updates, changelogs, sneak peeks and support.

---

## Requirements

| | |
| --- | --- |
| **Minecraft** | 1.21.1 |
| **Mod loader** | Fabric (Loader 0.19.3+) |
| **Java** | 21 or newer |
| **Required dependencies** | [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api) · [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) |

Works in single-player and on dedicated servers. Powers are per-player and fully synced, so a whole server can run
different heroes side by side. Almost everything is tunable in generated config files (`config/projecthero*.json`).

---

## How powers work

- **Primary powers** are who you are: every hero below, plus your experimental mutations. You hold **one Primary
  power at a time**; your mutations (up to three, stacking) count as that one power. Gaining a new hero -- lifting
  Mjolnir included -- replaces what you had.
- **Secondary powers** ride on top. The **Symbiote** is the Secondary power: it bonds with anyone, but only shares a host
  with Spider-Man (Black Suit) or the Punisher (Agent Venom).
- **Controls:** six ability keys, **Ability 1–6** in Options › Controls (default **R, G, Z, X, C, V**), plus **H**
  (Utility 1: suit up / transform), **N** (Utility 2), **I** (your power screen) and **P** (squad menu). Many moves have a
  **Sneak +** version, and holding **Left Alt** shows every ability's name on the HUD.
- Craft the **Guidebook** (Book + Feather + Spider Eye + Emerald) for a full in-game manual of every power, structure and
  event.

---

## The Heroes

### ⚡ Thor
Find a rare **Mjolnir crater** (villagers will point the way for 25 emeralds if you sneak-right-click them holding a
Lightning Rod). The hammer only moves for a **Hero of the Village**. Lift it with the effect and Mjolnir binds to you.
- Throw Mjolnir and **recall it from anywhere**, even out of another player's hand or an unloaded chunk. Fly by
  double-tapping jump.
- Lightning Strike, God of Thunder's Wrath, a crackling Lightning Beam, Chain Lightning, **Hammer Volley** (the hammer hunts
  every enemy around you), Thunderclap, Storm Call and Mjolnir Parry.
- **The Power of Thor:** +11 melee, +10 hearts, 80% less damage, permanent Regeneration, no fall or lightning damage.
- **H** calls down lightning and forges **Thor's Armour** onto you piece by piece -- boots, greaves, then chestplate and
  cape, each arriving with its own bolt from the sky.
- Every move has its own animation, the Beam and Chain Lightning are thick forking bolts, Thunderclap sends a shockwave
  ring across the ground -- and none of it ever hurts your squad.

### 🔴 Iron Man / Tony Stark
Build an **Arc Reactor**, the **Stark Fabricator** and a **Suit Platform**, then climb the ladder from the cave-built
**Mark 1** to the **Mark 7** with Blank Blueprints.
- Seven suits with their own energy, integrity, flight, strength and self-repair. **Call your armour** and watch it fly to
  you and assemble.
- Repulsors, Unibeam, rockets, micro-missiles, flares, a wrist laser, the Repulsor Shield, a weapon wheel, night vision,
  a targeting view and a retractable faceplate (**H**).
- **Protocol Phoenix** recalls your best suit when you would otherwise die. A single Repulsor in your boots slot gives
  flight with no suit at all.

### 🕷️ Spider-Man
Grown from the **Spider Climbing / Adhesion** mutation with an **Arachnid Mutagen**.
- Web Swing, Web Zip, Web Shot, Web Yank, Web Net and wall-crawling, all fed by an organic **Web Reserve** (no ammo).
- Momentum-keeping web-swinging, full wall and ceiling crawling, a double jump and a super-leap.
- **Spider-Sense:** directional danger warnings, auto-dodges and a glow on anything hunting you. No fall damage.
- A craftable Spider-Man suit with a removable mask (**H**).

### 🖤 The Symbiote
A **living alien organism**: a pool of black goo that crawls, climbs walls, flees fire and hunts for a host. If it
reaches a mob it **takes control of it**, and crawls free again when that mob dies. Release one by breaking the
**Symbiote Meteorite** in a crashed meteor, find one trapped in a buried **containment lab**, or hunt down an infected
mob. Right-click it and survive the bonding.
- **H** grows the black suit over you **one pixel at a time**. A Normal Host swells to 150% size.
- **Every move comes out of your hands** as a real tendril or spike, each with its own animation: **Tendril Strike**
  and **Sweep**, **Symbiote Spikes** (14 damage + Wither V; Sneak+G fires a fan of five), **Lunge**, **Grapple**,
  **Tendril Barrage**, a **living Blade** that grows from your arm, **Symbiote Shield**, **Thorns** and **Tendril Grab**.
- **Symbiote Onslaught:** a pool of living black spreads under you and erupts into a crown of giant tendrils that
  impale, wither, blind and cripple everything around you.
- A **Biomass** bar that suffers alongside you, feeds the Blade and Shield, and regenerates when you're safe.
- **It protects its host:** it wraps you on its own when you're hurt and **drags you back from death** once every ten
  minutes (twenty as Black Suit Spider-Man or Agent Venom). Camouflage while crouching, and a Predator Vision that outlines living things (**N**).
- **Weaknesses:** fire and sound. Bells, goat horns and Warden booms tear the suit right off you.
- **Symbiote Pets:** a free Symbiote also takes over animals -- and it hunts for your tamed wolf or cat (or share
  yours: Sneak + right-click it while suited). Your pet becomes the host and stays loyal, and in a fight the Symbiote
  spreads over it **pixel by pixel**: bigger, white-eyed, regenerating, with a Tendril Lash, Pounce, Spike Burst,
  Latching Bite and a **Guardian Shroud** that wraps you in the Symbiote when you're in danger.
- **Black Suit Spider-Man** and **Agent Venom** (with the Punisher) get their own Symbiote extras. Bottle a Symbiote in
  a **Symbiote Vial**.

### 🔵 Max Steel
Bond with **Steel**, the alien companion floating over a crash site.
- **Go Turbo** (**N**) into the nanotech suit, powered by T.U.R.B.O. energy -- or press a mode key and go straight from
  Normal form into that Turbo mode. T.U.R.B.O. energy recharges in Normal form too.
- Six Turbo modes, each with its own suit: Blast, Strength (with Resistance I), Speed, Flight (with wings), Stealth and the **Turbo Cannon**
  that fires you as a guided projectile.
- Burn out and you overload. An emergency revive fires if you have enough energy banked.

### 💀 The Punisher
No superpowers: guns, explosives, gear and training. Complete the **Vigilante Training Manual** or find a
**Vigilante Safehouse**.
- Pistol, Rifle, Shotgun and a fully modelled, scoped Sniper, with recoil, spread, range falloff and **headshots**. Ammo regenerates, so
  there's nothing to carry.
- Tactical Satchel, cook-and-throw Frag Grenades, Tactical Roll, Suppressive Fire, Adrenaline and remote **C4**.
- Tactical armour only a Punisher can craft.
- **Agent Venom:** bond with a Symbiote and **H** wraps you in the Agent Venom suit: Tendril Swing, Tendril Snatch,
  Symbiote Unleashed and Symbiote Rounds, on top of your whole arsenal.

### 💚 Green Lantern
Find a **Fallen Lantern Site**, pass the **Will Trial** and answer the ring's question. Are you afraid?
- A 10,000-point **Ring Charge** fuels beams, **directional flight** (fly wherever you look), shields, a Protective Dome
  and everything the ring can imagine -- and every attack is a real shape of hard light: a **giant fist** that flies,
  a **war hammer** swung down from the sky, **homing missiles**, an **Emerald Gatling** on your fist and a **Giant
  Hand** (**H**) that crushes and hurls whatever you aim at.
- **19 hard-light constructs** on a new construct wheel (Attack / Defence / Mobility / Utility) -- walls, walkable
  ramps, a spinning turret, a **Buzzsaw** that ricochets between enemies, an **Anvil Drop**, a **Chain Snare**, a
  **Launch Pad** and an **Emerald Warrior** that fights at your side. **N** dismisses them all.
- Every move has its own animation, a clean ring-charge HUD shows every key, and the ring on your hand glows and flares
  as you use it. Hold **Sneak + N** for 5 seconds to take the ring off (and give it to someone else).
- Hold **X** to recite the Oath for 30 seconds of doubled power.
- The ring never recharges on its own: recite the Oath at your **Power Battery** to refill it.

### 🐺 Wolverine
The ascension of **Super Regeneration**: drink an **Adamantium Serum**.
- A **Healing Factor** pool that knits you back together, a **Death Surge** that refuses to let you die, and a body that
  shrugs off poison, knockback and falls.
- **Adamantium claws** (**H**): Claw Slash, Cross Slash, a dragging Claw Dash, **Berserker Rage**, Frenzy and the charged
  **Adamantium Execution**. Right-click to guard with crossed claws.
- Enemies glow for you alone, and your own model shows raw flesh and bone when you take too much.

### 🗿 Titan Shifter
Drink a **Titan Serum**, fill your Titan Energy and press **H** to burst into an **11-block Titan** in a flash of
lightning and steam.
- A real 500-HP creature you ride and control: punches, kicks, a Heavy Smash, Stomp, Leap, a **Titan Roar** that exposes
  everything for 50 blocks, and crystal **Hardening**.
- Grab and eat mobs, carry squadmates on your shoulders, and break into a run.
- Hold **H** at low energy for a weaker, pale **Emergency Titan**. Your abandoned Titan body steams and dissolves where it
  stood.

### 💪 All Might / One For All
Use a **Vestige of One For All**. **H** swaps between your Base Form and a towering **Power Form**.
- **Detroit, New Hampshire, Texas and Carolina Smash**, a leap, **Plus Ultra** and the charged **United States of Smash**
  that leaves a crater behind.
- One For All is a 5-minute timer that drains while you're powered up, and the craftable costume goes on automatically
  from your locker (**N**).

### 💚 The Hulk
Find a ruined **Gamma Lab**, drink the **Gamma Serum**, then right-click the **Gamma Reactor** -- it goes critical and
explodes, and the Gamma in your blood lets you walk out of the crater as the Hulk.
- Rage builds as you get hurt. Let the Hulk out at 75 (**H**), or at 100 he takes over: you drop to your knees and he
  rises roaring.
- 1.8x size, 20-damage punches, his own armour, fast regeneration, immunity to fire, arrows, falls and webs.
- Power Punch, Ground Smash, Thunderclap, **HULK SMASH**, a 70-block Super Leap, a rampaging Charge, and Grab (throw mobs,
  tear up the earth, or carry your squadmates).
- **Banner can't be killed:** a fatal hit just unleashes the Hulk. Lose control and he goes on a rampage -- hitting
  friend and foe alike, squadmates included, who can fight back -- and you'll need the breathing minigame to calm him down.

### 🌙 Moon Knight
Find a rare **Temple of Khonshu** in the desert, take the **Scarab of Khonshu** from its hidden chamber, and at night lay
it on the altar under the open sky and kneel. Khonshu speaks... you die in a flash of white, and rise again as his fist.
- **H** summons the suit: it materialises over you **one pixel at a time** in 1.5 s as bandages spiral up your body,
  with a flowing **hooded cape**. In it you **regenerate** fast, hit **+7** harder, run **30% faster**, jump over
  **two blocks**, step straight up full blocks, take **20% less** damage and **half** fall damage. Your own
  armour is kept safe and handed back when you take it off -- and out of the suit, a hard hit calls it back on its own.
- **Lunar power:** three states -- **Day** x0.7, **Night** x1.0, **Full Moon** x1.5 (the Nether and the End count as
  day). **Vengeance** builds by protecting villagers and travellers and killing monsters, regenerates when you're out of
  combat, and powers his strongest moves.
- **Three alters (V), three suits:** **Marc** the fighter in white and gold, **Steven** the scholar in the capeless Mr. Knight suit
  (Scholar's Sight finds chests and ores through walls, better trades, **Fortune III** on everything he mines), **Jake** the shadow in black (backstabs, and a Vanish no mob can see through).
  Switch and the new suit rematerialises over the old one. Run out of Vengeance and your mind **Fractures**.
- **R** Crescent Darts (homing at night, boomerang back; a **Crescent Fan** of five darts that each lock on to an enemy;
  **Moon Mark**) · **G** Grapple Kick (20 damage, with aim assist and a lock-on marker) · **X** Dash, that goes wherever
  you aim, and **Sneak+X** a 100-block Grappling Line that reels in mobs and squadmates · **Z** an area-blast **Moonbeam**,
  **Khonshu's Judgement** (a 15 s burn that heals you) and the ultimate one-minute **Eye of Khonshu** that calls moonbeams
  down on everything around you · **C** summons the **Truncheon** into your hand (a three-hit combo, a 15-damage staff
  spin, ground / dive slams) · **Sneak+G** Shadow Step.
- **The cape:** jump and hold Sneak to **glide** fast and flat out on cape wings (glide into a mob to kick it); hold right click
  to **block** with it.
- **Khonshu's Resurrection:** once per lunar cycle, a fatal blow brings you back in a flash of moonlight.

### 🛡️ Super Soldier
Brew a Potion of **Strength**, **Swiftness** and **Leaping**, and combine all three in a crafting table into the
**Unrefined Super Soldier Serum**. Drink it and roll the dice: **1 in 10** it makes you a Super Soldier -- **9 in 10**
your body rejects it and you die (creative won't save you). Or be patient: **10 minutes in a Blast Furnace** turns it
into the **Refined Serum**, which always works.
- Peak human, always on: **+50%** speed, **+5 hearts**, **+7** bare-handed damage, **30% less** damage taken, a
  **2-block** jump, knockback resistance, faster swings, healing out of combat, immune to Poison and Nausea.
- **R** Combo Strike (a three-punch combo) / **Shift+R** Uppercut Launcher · **G** Flying Kick (a lunging kick from
  up to 8 blocks away) / **Shift+G** Judo Takedown (over the shoulder and into the ground, stunned) · **Z** Leaping Slam /
  **Shift+Z** the ultimate **Super Soldier Onslaught** · **X** Tactical Roll (untouchable mid-roll) / **Shift+X** High
  Leap · **V** Battle Cry (buffs your squad, weakens the enemy) / **Shift+V** Tactical Focus (marks every enemy within
  30 blocks; your hits on them crit).
- **The shield:** craft the round, unbreakable **Adamantium Shield** (iron blocks, a netherite ingot, red, white and
  blue dye). It blocks like any shield -- and **C** throws it: it bounces between up to **four** enemies for 9 each and
  flies back to your hand. Any ordinary shield can be thrown too (three enemies, 6 each), and it always comes back.
- **The suit:** a craftable four-piece **Captain America** suit (wool, iron and leather; 18 armour) -- only a Super
  Soldier can wear it.

### ⚡ The Speedster (Super Speed)
A **Hero-Tier** power awakened by the **Speed Force**: get **Strength, Speed and Jump Boost** on you at once, then
activate **Charged Copper Plates** or get **struck by lightning** -- half the time the Speed Force takes you, the other
half it burns the effects away and you try again.
- **Speed Mode** walks at ~20 blocks a second (easy to fight in) and **sprints at ~40**; **Overdrive** hits ~100.
- **Blitz** zips you to a target with a heavy, launching hit and a shockwave that knocks back everything round it.
  Mach Punch, Speed Vortex, a 10-second **Speed Sweep**, Phase through walls, carry anyone on **N**, and a charged,
  game-wide **Time Slow**. Regeneration III, always.
- **The Flash Suit** packs into a gold **Flash Ring** on **H** -- and slowly mends itself while it is in there.

### ☀️ The Kryptonian
Somewhere out in the world lies a rare **Kryptonite Meteor crater** -- as hard to find as Mjolnir's.
At the bottom of its scorched bowl, ringed with glowing **kryptonite ore**, sits the **Meteor Core** -- mine it for the
one **Kryptonian Crystal** it holds, then hold it up to the **daytime sun**. The stored sunlight pours into you.
- **The body:** 60 health, **75% less damage**, and nothing at all from falls, fire, lava, drowning or freezing. 15-damage
  fists that send things flying, no knockback, 40% faster, a 4-block jump, and the sun heals and feeds you.
- **True flight:** double-tap jump and fly wherever you look -- **S** brakes to a hover, **Sprint** is 40 blocks a second
  with a **sonic boom** as you break into it, and he flies one fist forward.
- **Solar Energy** charges in sunlight (fast in direct sun, slowly at night, barely underground) and fuels ten moves:
  **R** Kryptonian Punch (32, launches) · **Shift+R** held **Heat Vision** from your eyes · **G** Freeze Breath (ices
  water) · **Shift+G** Thunderclap · **Z** Ground Slam (dive from the sky) · **Shift+Z** **SOLAR FLARE** -- dump every
  drop of sunlight in a 12-block blast of up to 120 damage, then you're burnt out for 30 s · **X** Super Dash ·
  **Shift+X** Sky Launch (40 blocks straight up) · **V** X-Ray Vision · **Shift+V** Super Grab & Throw.
- **Kryptonite:** ore, blocks and shards (mine the ore with an iron pickaxe) within a few blocks strip every power, drop
  you out of the sky and hurt you. Your enemies can carry it too.
- **The Superman Suit:** a craftable four-piece set (blue and red wool, gold and diamonds) that **only a Kryptonian can
  wear** -- netherite-strong and fireproof, with a real red cloth **cape** (gold shield on the back) that sways as you
  walk and streams out flat behind you at super-speed.

---

## Experimental Powers

A whole second progression system. Mutate powers with **experimental serums**, **research notes**, **exposure events**
or **lab devices**, found in rare research sites. The **Mutagenic**, **Heroic** and **Prismatic Serums** grant a random
power you don't have yet.

Own up to three at once: all their passives run permanently, and the one you select drives your ability keys. The
experimental powers are being remade one by one; **three are available right now**, the rest return as they're rebuilt:

- **Super Strength** -- six heavy-hitting keys, three passives and a superhero landing.
- **Laser Vision** -- beams straight from your eyes, driven by a 0-100 heat gauge that cools once you stop firing.
- **Super Regeneration** -- no keys at all: heals 2 HP every tick, burns off harmful effects in 2 seconds and holds
  **three revive charges**.

---

## World Events & Bosses

### 🧟 The Zombie Raid
Catch the **Gravebound Curse** from a Graveyard or a Cursed Zombie, then cure it with an Enchanted Golden Apple or survive
**twelve waves** and three **Powered Zombie Bosses**, each wielding a real superpower. Their heads are **wearable,
placeable trophies** that remember who slew them. The curse can strike again and again.

### 🏹 The Supervillain Village Raid
A **Pillager Spy** attacking you in a village sets off five raider waves and an **Empowered** supervillain with a random
power and look. Raids are repeatable: spies keep coming, even for a village that has been raided before.

### 🗼 The Titan
A zombie that isn't what it seems. At low health it becomes an **18-block giant** that hunts you with punches, sweeps,
stomps, shockwaves, grabs, boulder throws, charges, a **Leaping Slam** and a **Grave Roar** that raises the dead -- every
move animated, telegraphed and dodgeable. It turns on whoever hurts it most, so the whole group has to stay sharp.

### 🔥 The Abyssal Behemoth
A very rare **Nether boss**: a nine-block, horned, ancient Ghast with nine attacks and three phases, including fireball
barrages, magma rain, a sweeping beam and a tether that drags it down for melee. It drops the **Abyssal Core**.

### ⚔️ The Oathbreaker
The hardest duel in the mod. Summon a **4-block fallen knight** with a **Knight's Soul** on a Respawn Anchor.
- Break his hidden **poise** to stagger him through **three phases**: Stance Dash, combos, parries, a Leaping Cleave,
  Soul Rend, Chains, the Judgement slam, the unblockable Execution grab, the **Oathbound Whirlwind** and **Grave Geysers**.
- He hunts you from 50 blocks, and he turns on whoever is actually hurting him.

### 🌌 Apokolips Invasion (the Darkseid Raid)
An **endgame co-op raid for up to 8 heroes**, started with a **Boom Tube Beacon**.
- **Five waves** of winged Parademons pour through Boom Tubes, including strafing gunners, Elites and Brutes.
- Then **DARKSEID** steps out, shielded by **four Mother Boxes** you must channel to shut down, and they won't stay down.
- **Zig-zagging Omega Beams**, the Omega Barrage, a Godly Ground Slam, Darkseid's Grip, teleports, charges and
  reinforcements, all the way to **Omega Annihilation**.
- Rewards: the **Omega Core**, Omega Shards, a personal **Mother Box** and the very rare **Omega Relic**.

---

## 🤝 Squads
`/squad create` and `/squad invite` make a team that can't hurt each other, whatever powers are flying around. Press **P**
for the roster: health, heroes, coordinates and direction for every squadmate. The Locator Bar shows their faces across
the top of your screen.

## Removing powers
Every power can be given up in survival: craft a **Power Suppressor** and sneak-use it.

---

## Notes
- **Creative tabs:** Superheroes, Iron Man, Punisher.
- **Admin commands** (op only) live under `/projecthero`: `power grant|remove|stack`, `locate <structure>` and `raid ...`.
- Custom GeckoLib models and animations throughout, backed by 400+ automated in-game tests.
- **License:** All Rights Reserved. Please don't redistribute or reupload the jar.

*Project Hero started as "just Thor and Mjolnir" and kept growing. Bug reports, ideas and feedback are always welcome
on the [Discord](https://discord.gg/3jzdAmmfnC)!*
