// v0.14.5 Super Speed rework -- lang edits for en_us.json. Re-runnable (idempotent): sets / removes only the keys
// below and leaves every other line of the file (order, formatting, line endings) untouched.
// Usage (from the repo root): node scratchpad/lang_v0145_speed.js
const fs = require('fs');
const path = require('path');

const FILE = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'lang', 'en_us.json');
const P = 'projecthero.power.power_04_super_speed.';

const SET = {
	[P + 'desc']: 'A speedster mutation: you are always 30% faster and eat and drink 50% faster; Speed Mode and Overdrive leave a trail of after-images behind you, Shift+C phases you through walls, and Time Slow drops everything else to 5% speed.',
	[P + 'ability.rapid_assault']: 'Rapid Assault',
	[P + 'ability.rapid_assault.desc']: 'R: a blur of blows on everything in front of you — 4 punches of 8 damage each (twice as hard in Overdrive). 1.5s cooldown.',
	[P + 'ability.speed_carry']: 'Speed Carry',
	[P + 'ability.speed_carry.desc']: 'G: snatch up the creature or player you are looking at and carry it up on your shoulders as you run (up to 30s). It is a carry, not an attack: whatever you carry takes no fall damage and can\'t suffocate in walls, and it can\'t hurt you. Press G again to set it down in front of you — it stays safe from fall damage for 3 more seconds. 2.5s cooldown.',
	[P + 'ability.momentum_dash']: 'Momentum Dash',
	[P + 'ability.momentum_dash.desc']: 'X: an instant dash exactly where you are looking — look up to dash upward, down to dash downward. Further in Overdrive. In speed modes, jumps also keep their momentum for long leaps. 1.7s cooldown.',
	[P + 'ability.time_slow']: 'Time Slow',
	[P + 'ability.time_slow.desc']: 'Z: for 30s everything within 96 blocks except you moves at 5% speed — mobs, animals, arrows and other projectiles, dropped items, falling blocks, TNT — and other players move, jump, fall, swing and mine at about 5%. Press Z again to end it early. 150s cooldown, starting when it ends.',
	[P + 'ability.overdrive']: 'Overdrive',
	[P + 'ability.overdrive.desc']: 'V: for 30s — sprint at roughly 64 blocks/second (a faster tier that replaces Speed Mode), +150% attack speed, a 10-block step assist, fall immunity, and your melee and every Super Speed move hit twice as hard. Your after-image trail turns red, and speed explosions burst behind you while you run. 42.5s cooldown.',
	[P + 'ability.speed_mode']: 'Speed Mode',
	[P + 'ability.speed_mode.desc']: 'C: toggle: sprint at roughly 32 blocks/second, +50% attack speed, run on water, 2-block step, +100% swim speed, -80% fall damage. A trail of yellow after-images follows you while you run (a new one every tick, each fading over 1s). Shift+C: Phase — hold C to vibrate through walls on your own level (you can\'t sink through floors or rise through ceilings); while phasing you take no damage but can\'t deal damage or use any other ability. Release C to stop; if you end inside a wall you step out to the nearest open spot.',
	[P + 'passive.speed']: '+30% movement speed: walking, sprinting and swimming',
	[P + 'passive.metabolism']: 'Eat and drink 50% faster (food, potions, milk, honey)',
	'message.projecthero.speed.phase_hint': 'Phasing — release C to stop',
};

const REMOVE = [
	P + 'ability.vortex', P + 'ability.vortex.desc',
	P + 'ability.phase_vibrate', P + 'ability.phase_vibrate.desc',
	P + 'ability.lightning_throw', P + 'ability.lightning_throw.desc',
	P + 'passive.sprint', P + 'passive.step', P + 'passive.collision',
	'message.projecthero.speed.no_momentum', 'message.projecthero.speed.cannot_phase',
];

let text = fs.readFileSync(FILE, 'utf8');
const crlf = text.includes('\r\n');
let lines = text.replace(/\r\n/g, '\n').split('\n');

const esc = (v) => JSON.stringify(v);
const keyOf = (line) => {
	const m = line.match(/^\s*"((?:[^"\\]|\\.)*)"\s*:/);
	return m ? JSON.parse('"' + m[1] + '"') : null;
};

// removals
lines = lines.filter((l) => !REMOVE.includes(keyOf(l)));

// updates in place / inserts after the last power_04 line (or before the closing brace)
for (const [k, v] of Object.entries(SET)) {
	const idx = lines.findIndex((l) => keyOf(l) === k);
	const indent = '  ';
	if (idx >= 0) {
		const comma = lines[idx].trimEnd().endsWith(',') ? ',' : '';
		lines[idx] = indent + esc(k) + ': ' + esc(v) + comma;
		continue;
	}
	let anchor = -1;
	const prefix = P; // everything goes with the power's own block (never appended at the end of the file)
	lines.forEach((l, i) => { const kk = keyOf(l); if (kk && kk.startsWith(prefix)) anchor = i; });
	if (anchor < 0) {
		anchor = lines.map((l) => l.trim()).lastIndexOf('}') - 1;
	}
	// the inserted line always gets a comma; fix the anchor if it was the last entry
	if (!lines[anchor].trimEnd().endsWith(',') && keyOf(lines[anchor])) {
		lines[anchor] = lines[anchor].trimEnd() + ',';
		lines.splice(anchor + 1, 0, indent + esc(k) + ': ' + esc(v));
	} else {
		lines.splice(anchor + 1, 0, indent + esc(k) + ': ' + esc(v) + ',');
	}
}

// the last entry before the closing brace must not end in a comma
const close = lines.map((l) => l.trim()).lastIndexOf('}');
for (let i = close - 1; i >= 0; i--) {
	if (lines[i].trim() === '') continue;
	lines[i] = lines[i].replace(/,\s*$/, '');
	break;
}

let out = lines.join('\n');
JSON.parse(out); // validate
if (crlf) out = out.replace(/\n/g, '\r\n');
fs.writeFileSync(FILE, out);
console.log('en_us.json: set ' + Object.keys(SET).length + ' keys, removed up to ' + REMOVE.length);
