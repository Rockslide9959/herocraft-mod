// Docs updates for v0.12.33 (All Might).
const fs = require('fs');
function edit(f, pairs) {
	let s = fs.readFileSync(f, 'utf8');
	const crlf = s.includes('\r\n');
	if (crlf) s = s.replace(/\r\n/g, '\n');
	for (const [a, b] of pairs) {
		if (!s.includes(a)) { console.error('MISSING in', f, ':', a.slice(0, 90)); process.exit(1); }
		s = s.replace(a, () => b);
	}
	if (crlf) s = s.replace(/\n/g, '\r\n');
	fs.writeFileSync(f, s);
}
edit('docs/CURSEFORGE_DESCRIPTION.md', [
[`Iron Man, Spider-Man, Max Steel, the Punisher, Wolverine or a giant Titan Shifter, bond`, `Iron Man, Spider-Man, Max Steel, the Punisher, Wolverine, All Might or a giant Titan Shifter, bond`],
[`Spider-Man, Max Steel, the Punisher, Green Lantern, Wolverine, the Titan Shifter and the mutations`, `Spider-Man, Max Steel, the Punisher, Green Lantern, Wolverine, the Titan Shifter, All Might and the mutations`],
[`  \`config/projecthero_titan_shifter.json\`.

---`, `  \`config/projecthero_titan_shifter.json\`.

### 💪 All Might / One For All
A permanent **Hero-Tier Primary** power (v0.12.33) — the mod's strongest pure-strength hero. Craft a **Vestige of One For All** (4 titanium-gold
plates, 2 enchanted golden apples, 2 diamond blocks, a nether star) and use it. You get a custom **All Might costume** (blond hair and smile, blue suit with
a white V collar, white gloves, red boots and a cape) that swells into a huge muscular silhouette in Full Power.

- **OFA Power:** a 100-point reserve on the HUD (\`OFA: 100 / 100\`) that every Smash spends; it refills 1 point per 0.75 s, 2.5× faster out of combat.
- **H — Transform:** toggles the **Contained** form (+40 HP, +25 melee, +25% speed, 80% knockback resistance, 2× jump, −75% fall damage, −35% damage) and
  **Full Power** (+35 melee, +40% speed, knockback immune, 2.5× jump, −90% fall damage, −50% damage, Smashes +15%). A 1.5 s damage-proof transformation with a
  wind burst; no cooldown.
- **The Smashes:** **R Detroit Smash** (50 dmg, 5 blocks, 10 OFA, 3 s) · **G Texas Smash** (70 dmg, a 10-block travelling air wave, 15 OFA, 6 s) ·
  **Z Carolina Smash** (a 10-block dash, 60 dmg, 20 OFA, 5 s) · **X New Hampshire Smash** (launch ~15 blocks up, 80 dmg on the way through and on landing,
  25 OFA, 8 s) · **C Full Cowl** (10 s of +75% speed, +50% melee, +jump, +20% more damage reduction, 20 OFA, 20 s) · **V United States of Smash** (a 1.5 s
  charge, then 250 damage in a 15-block cone, a 25-block outer shockwave and a crater — 100 OFA, 60 s) · **N All Might Leap** (~17 blocks up, 5 OFA, 5 s).
- **Passives:** punches that knock enemies back with an air burst, hard-landing shockwaves (small / medium / heavy by fall height), his own launches can never
  hurt him, bosses lose at most 10% of their health to a single Smash and are not knocked back. Block damage is configurable and never touches bedrock.
- Server-authoritative; every number is in \`AllMightConfig\`. Full details in \`docs/ALLMIGHT_REFERENCE.md\`.

---`],
]);
console.log('docs updated');
