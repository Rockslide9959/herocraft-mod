// v0.14.7 Super Speed batch -- lang edits for en_us.json. Re-runnable (idempotent): sets / removes only the keys
// below and leaves every other line of the file (order, formatting, line endings) untouched.
// Usage (from the repo root): node scratchpad/lang_v0147_speed.js
const fs = require('fs');
const path = require('path');

const FILE = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'lang', 'en_us.json');
const P = 'projecthero.power.power_04_super_speed.';

const SET = {
	[P + 'desc']: 'A speedster mutation: you are always 30% faster, regenerate (Regeneration II) and eat and drink 50% faster. Blitz enemies from across the field, sweep through every enemy around you and back, spin up a cyclone, throw a punch with all your momentum behind it, run on water and straight up walls, phase through walls and slow time itself.',
	[P + 'ability.rapid_assault']: 'Rapid Assault',
	[P + 'ability.rapid_assault.desc']: 'R: a blur of blows on everything in front of you — 4 punches of 8 damage each (32 in all; twice as hard in Overdrive). Every punch lands in full, even on players and mobs that were just hit. 1.5s cooldown. Shift+R: Mach Punch — one punch with all your momentum behind it: 12 damage standing still, rising with your running speed to 30 at full Overdrive speed, a huge knockback, and a shockwave that hits everything within 4.5 blocks of the impact for 6 and throws it back. 10s cooldown (shown on the R box).',
	[P + 'ability.mach_punch']: 'Mach Punch',
	[P + 'ability.blitz']: 'Blitz',
	[P + 'ability.blitz.desc']: 'G: zip to the enemy under your crosshair (up to 24 blocks away, even past a narrow miss) in a streak of after-images and hit it for 20 damage (40 in Overdrive), landing right in front of it and facing it. No target, no cooldown. 4s cooldown. Shift+G: Speed Vortex — for 3s you run circles around yourself so fast you become a cyclone: everything within 8 blocks is dragged round and lifted, taking 3 damage every half second, and when it ends the vortex bursts for 8 more and flings them all away. It moves with you. 14s cooldown (shown on the G box).',
	[P + 'ability.speed_vortex']: 'Speed Vortex',
	[P + 'ability.momentum_dash']: 'Momentum Dash',
	[P + 'ability.momentum_dash.desc']: 'X: an instant dash exactly where you are looking — look up to dash upward, down to dash downward. Further in Overdrive. In speed modes, jumps also keep their momentum for long leaps. 1.7s cooldown. Shift+X: Speed Sweep — blink from enemy to enemy through every hostile mob (and anything hunting you, and players when PvP is on — never your squad or pets) within 30 blocks, up to 16 of them, one every 2 ticks, hitting each for 12 (24 in Overdrive), then snap back to exactly where you started, facing the same way. Nothing can hurt you while it runs. 20s cooldown (shown on the X box).',
	[P + 'ability.speed_sweep']: 'Speed Sweep',
	[P + 'ability.time_slow']: 'Time Slow',
	[P + 'ability.time_slow.desc']: 'Z: for 45s everything in the world except you slows to 5% of its normal speed — every mob, animal and player, projectiles, items, falling blocks, TNT, water and lava, redstone, crops, block animations, particles, the sun, moon and clouds, the weather. You keep full speed: run, hit and use every move as normal while the world crawls. Slowed creatures still take your hits in full, as fast as you can land them, and anything you kill drops its loot at once. Only one Time Slow can run at a time. Your view turns cold and grey (everyone else sees it too). Press Z again to end it early. 150s cooldown, starting when it ends.',
	[P + 'ability.overdrive']: 'Overdrive',
	[P + 'ability.overdrive.desc']: 'V: for 30s — sprint at roughly 64 blocks/second (a faster tier that replaces Speed Mode), +150% attack speed, a 10-block step assist, fall immunity, and your melee and every Super Speed move hit twice as hard. Your run gets even more extreme, your after-image trail turns red and crackles with lightning, and speed explosions burst behind you while you run. 42.5s cooldown.',
	[P + 'ability.speed_mode']: 'Speed Mode',
	[P + 'ability.speed_mode.desc']: 'C: toggle: sprint at roughly 32 blocks/second in a speedster run, +50% attack speed, run on water, +100% swim speed, -80% fall damage. Run into a wall while looking straight up to run up it (faster in Overdrive), with a hop onto the top. A trail of yellow after-images follows you while you run (a new one every tick, each fading over 1s). Shift+C: Phase — hold C to vibrate through walls on your own level (you can\'t sink through floors or rise through ceilings); while phasing you take no damage but can\'t deal damage or use any other ability. Release C to stop; if you end inside a wall you step out to the nearest open spot.',
	[P + 'ability.speed_carry']: 'Speed Carry',
	[P + 'ability.speed_carry.desc']: 'N: snatch up the creature or player you are looking at and carry it up on your shoulders as you run (up to 30s). It is a carry, not an attack: whatever you carry takes no fall damage and can\'t suffocate in walls, and it can\'t hurt you. Press N again to set it down in front of you — it stays safe from fall damage for 3 more seconds. 2.5s cooldown.',
	[P + 'passive.speed']: '+30% movement speed: walking, sprinting and swimming, and a 3-block step assist',
	[P + 'passive.metabolism']: 'Eat and drink 50% faster (food, potions, milk, honey)',
	[P + 'passive.regen']: 'Regeneration II, always (a speedster\'s metabolism heals you fast)',
	'message.projecthero.speed.no_target': 'No target',
	'message.projecthero.speed.time_taken': 'Time is already slowed',
};

const REMOVE = [P + 'ability.afterimage_decoy', P + 'ability.afterimage_decoy.desc'];

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
