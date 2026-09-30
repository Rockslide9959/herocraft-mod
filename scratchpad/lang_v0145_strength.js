// v0.14.5 Super Strength rework: lang updates for en_us.json.
// Line-based and idempotent: replaces values in place, inserts new keys after an anchor key, deletes removed keys.
// Never reformats the rest of the file; keeps the file's line endings. Run from the repo root:
//   node scratchpad/lang_v0145_strength.js
const fs = require('fs');
const f = 'src/main/resources/assets/projecthero/lang/en_us.json';
const raw = fs.readFileSync(f, 'utf8');
const eol = raw.includes('\r\n') ? '\r\n' : '\n';
let lines = raw.split(/\r?\n/);

const P = 'projecthero.power.power_01_super_strength.';

const SET = {
	[P + 'desc']: 'Raw, overwhelming physical force: fists that hit like wrecking balls, a body that can throw anything it gets its hands on — creatures, players, even the ground itself — and every leap landed like a hero.',
	[P + 'ability.ground_slam.desc']: 'G: 20 damage in a 5-block radius, slows for 2s and launches enemies up. Used airborne it becomes a dive that hits harder the farther you fell (20, up to 34) and ends in a superhero landing that shrugs off the fall. Leaves the ground intact. 6.8s cooldown. Shift + G = Thunderclap: clap a 120° cone of air 9 blocks out — 9 damage, a hard shove and a 2.5s stun (Slowness V + Weakness), arrows turned around, and every fire in the cone snuffed out (on creatures, on you and on the ground). Thunderclap has its own 8s cooldown; the G box shows whichever of the two is longer.',
	[P + 'ability.power_leap.desc']: 'X: hold to charge (a bar above your keys fills), release to launch yourself the way you are looking — 11 blocks at a tap, up to 38 fully charged — and touch down in a superhero landing: one knee down, fist driven into the ground, cracks racing out through the block you land on. 7 damage in a 2.5-block radius at a tap, up to 22 in 5.5 blocks fully charged. No fall damage. 2.5s cooldown.',
	[P + 'ability.bull_rush.desc']: 'Z: hold for 5s to charge (damage resistance and full knockback resistance while charging; a bar above your keys fills), then plough forward for 8s at double sprint speed with a 4-block step assist, hitting everything in your path for 24 and a 5-block knockback — the same bar drains as the rush runs out. Releasing early cancels. 34s cooldown.',
	[P + 'ability.maximum_effort.desc']: 'C: 30 seconds of everything turned up — every Super Strength move and your fists hit twice as hard (Bull Rush +25%), slams grow 50% wider, you are immune to knockback, take 30% less damage, move and jump faster, and ability cooldowns are halved. Red veins light up under your skin and a bar above your keys counts it down. 70s cooldown.',
	[P + 'ability.grab_throw.desc']: 'V: grab the creature or player in front of you and hoist it over your head — or, if there is none, rip the block you are looking at straight out of the world. Press again to throw: a thrown creature takes 12 when it slams into a wall, the floor or another mob (and so do they); a thrown block hits for 19 and shatters for 8 around it. Sneak + V while holding a creature sets it down gently. Anything held is dropped after 15s. 3.4s cooldown. Shift + V with empty hands = Rip & Hurl: tear a boulder out of the ground in front of you — it rises over your head in half a second — and hurl it about twice as far as before: 26 damage to whatever it strikes and 14 to everything within 3 blocks as it shatters. Rip & Hurl has its own 9.4s cooldown; the V box shows whichever is longer. Containers and obsidian-hard blocks cannot be ripped; with terrain damage off the block is copied, not removed.',
	[P + 'passive.melee']: '+10 unarmed damage (a bare-handed hit lands 11) — while you are not holding a weapon or tool',
	[P + 'passive.charged']: 'Charged Punch: hold the attack key for 1s (not while aimed at a block you could mine), then release — your melee damage +15 (26 bare-handed), massive knockback, disables shields. A bar above your keys fills as it charges, then drains over its 2s cooldown.',
};
// new keys: [key, value, insert-after key]
const ADD = [
	[P + 'passive.knockback', 'Your melee hits knock enemies back 150% as far', P + 'passive.melee'],
];
const REMOVE = [
	P + 'passive.defense', P + 'passive.jump', P + 'passive.mining', P + 'passive.fall',
	P + 'ability.thunderclap.desc', P + 'ability.rip_hurl.desc',
];

const keyOf = (line) => { const m = /^\s*"((?:[^"\\]|\\.)*)"\s*:/.exec(line); return m ? m[1] : null; };
const enc = (v) => JSON.stringify(v);
const indentOf = (line) => /^\s*/.exec(line)[0];

// replace
for (const [k, v] of Object.entries(SET)) {
	const i = lines.findIndex((l) => keyOf(l) === k);
	if (i < 0) { console.error('missing key to set: ' + k); process.exit(1); }
	const comma = lines[i].trimEnd().endsWith(',') ? ',' : '';
	lines[i] = indentOf(lines[i]) + enc(k) + ': ' + enc(v) + comma;
}
// add (or update if already present)
for (const [k, v, after] of ADD) {
	const existing = lines.findIndex((l) => keyOf(l) === k);
	if (existing >= 0) {
		const comma = lines[existing].trimEnd().endsWith(',') ? ',' : '';
		lines[existing] = indentOf(lines[existing]) + enc(k) + ': ' + enc(v) + comma;
		continue;
	}
	const a = lines.findIndex((l) => keyOf(l) === after);
	if (a < 0) { console.error('missing anchor: ' + after); process.exit(1); }
	if (!lines[a].trimEnd().endsWith(',')) { lines[a] = lines[a].trimEnd() + ','; }
	lines.splice(a + 1, 0, indentOf(lines[a]) + enc(k) + ': ' + enc(v) + ',');
}
// remove
for (const k of REMOVE) {
	const i = lines.findIndex((l) => keyOf(l) === k);
	if (i < 0) { continue; }
	const hadComma = lines[i].trimEnd().endsWith(',');
	lines.splice(i, 1);
	if (!hadComma) { // it was the last entry: drop the trailing comma of the one before
		for (let j = i - 1; j >= 0; j--) {
			if (keyOf(lines[j]) !== null) { lines[j] = lines[j].trimEnd().replace(/,$/, ''); break; }
		}
	}
}

const out = lines.join(eol);
JSON.parse(out); // must still be valid JSON
fs.writeFileSync(f, out);
console.log('lang_v0145_strength: ' + Object.keys(SET).length + ' set, ' + ADD.length + ' added, ' + REMOVE.length + ' removed');
