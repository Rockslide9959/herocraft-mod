// v0.14.8: CurseForge description -- Kryptonian in the intro, experimental section down to the four remade powers.
const fs = require('fs');
const f = 'docs/CURSEFORGE_DESCRIPTION.md';
let s = fs.readFileSync(f, 'utf8');
const crlf = s.includes('\r\n');
s = s.replace(/\r\n/g, '\n');
const r = (a, b) => { if (!s.includes(a)) throw new Error('missing ' + a); s = s.replace(a, b); };
r('Moon Knight or a Super Soldier, bond with a living alien Symbiote, or mutate one of 26\nexperimental superpowers.',
  'Moon Knight, a Super Soldier or a Kryptonian, bond with a living alien Symbiote, or mutate\nexperimental superpowers.');
const before = s;
s = s.replace(/## 26 Experimental Powers[\s\S]*?\n---\n/, `## Experimental Powers

A whole second progression system. Mutate powers with **experimental serums**, **research notes**, **exposure events**
or **lab devices**, found in rare research sites. The **Mutagenic**, **Heroic** and **Prismatic Serums** grant a random
power you don't have yet.

Own up to three at once: all their passives run permanently, and the one you select drives your ability keys. The
experimental powers are being remade one by one; **four are available right now**, the rest return as they're rebuilt:

- **Super Strength** -- six heavy-hitting keys, three passives and a superhero landing.
- **Laser Vision** -- beams straight from your eyes, driven by a 0-100 heat gauge that cools once you stop firing.
- **Super Speed** -- Speed Mode and Overdrive (lightning trails), Mach Punch, Blitz, Speed Vortex, Phase (vibrate
  through walls), carry anyone on **N**, and a charged, game-wide **Time Slow** that leaves you exhausted.
- **Super Regeneration** -- no keys at all: heals 2 HP every tick, burns off harmful effects in 2 seconds and holds
  **three revive charges**.

---
`);
if (s === before) throw new Error('experimental section not found');
fs.writeFileSync(f, crlf ? s.replace(/\n/g, '\r\n') : s);
console.log('ok');
