// v0.13.19: CurseForge description updates (Symbiote, Oathbreaker, Hulk, Moon Knight teaser).
const fs = require('fs');
const path = require('path');
const FILE = path.join(__dirname, '../docs/CURSEFORGE_DESCRIPTION.md');
let s = fs.readFileSync(FILE, 'utf8');
function block(startMarker, endMarker, replacement) {
	const a = s.indexOf(startMarker);
	const b = s.indexOf(endMarker, a + 1);
	if (a < 0 || b < 0) throw new Error('markers: ' + startMarker);
	s = s.slice(0, a) + replacement + s.slice(b);
}

block('### 🖤 The Symbiote', '### 🔵 Max Steel', `### 🖤 The Symbiote
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

`);

s = s.replace(`**v0.13.17:** every move hits ~30% harder, and he hits a further **10% harder in phase 2** and **25% harder in phase 3**.`,
`**v0.13.17:** every move hits ~30% harder, and he hits a further **10% harder in phase 2** and **25% harder in phase 3**.
**v0.13.19:** two new attacks -- the **Oathbound Whirlwind** (two full 360-degree spins that punish anyone circling behind
him) and **Grave Geysers** (he plunges his sword and soul-fire columns erupt under every player within 30 blocks). He
tracks players from **50 blocks** away, even out of sight, and his aggro follows whoever is actually hurting him -- you
can no longer beat on him while a friend kites him around.`);

s = s.replace(`- **The Hulk refuses to die:** Banner **can't be killed** -- every fatal hit brings the Hulk bursting out at full
  health, no cooldown. To kill a Gamma player you have to beat the Hulk.`,
`- **The Hulk refuses to die:** Banner **can't be killed** -- every fatal hit brings the Hulk bursting out at full
  health, no cooldown (so the HUD no longer shows a death-save dot). To kill a Gamma player you have to beat the Hulk.`);

if (!s.includes('### 🌙 Moon Knight')) {
	s = s.replace(`## 27 Experimental Powers`, `### 🌙 Moon Knight (coming soon)
Khonshu's fist is being built right now. v0.13.19 lays the foundations -- the pact with Khonshu, a **lunar power** that
makes him strongest under a full moon (and weaker by day or underground), a **Vengeance** meter fed by protecting
villagers and travellers at night, the **Fracture** between his three alters and **Khonshu's Resurrection**. The suit,
the cape, the six abilities and the desert **Temple of Khonshu** arrive in the next updates.

## 27 Experimental Powers`);
}
fs.writeFileSync(FILE, s);
console.log('description updated');
