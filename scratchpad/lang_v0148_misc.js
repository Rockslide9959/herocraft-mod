// v0.14.8 misc batch (Super Speed Time Slow charge / exhaustion / carry, Laser Vision heat) -- lang edits for
// en_us.json. Re-runnable (idempotent): sets only the keys below and leaves every other line of the file (order,
// formatting, line endings) untouched. New keys go right after the last existing key of their own group.
// Usage (from the repo root): node scratchpad/lang_v0148_misc.js
const fs = require('fs');
const path = require('path');

const FILE = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'lang', 'en_us.json');
const S = 'projecthero.power.power_04_super_speed.';
const L = 'projecthero.power.power_02_laser_vision.';

const SET = {
	// ---- Super Speed ----
	[S + 'ability.time_slow.desc']: 'Z: HOLD Z for 5s to charge it — you brace, vibrate and crackle with building speed-force (the bar above your keys fills; let go early and nothing happens, no cooldown). When full it fires by itself: for 45s everything in the world except you slows to 5% of its normal speed — every mob, animal and player, projectiles, items, falling blocks, TNT, water and lava, redstone, crops, block animations, particles, the sun, moon and clouds, the weather. You live at full speed while the world crawls, but at a normal person\'s pace: no speed boost from the passive, Speed Mode or Overdrive while it runs. Slowed creatures still take your hits in full, as fast as you can land them, anything you kill drops its loot at once and you can pick it up straight away. Only one Time Slow can run at a time. Your view turns cold and grey (everyone else sees it too). Press Z again to end it early. Afterwards you are EXHAUSTED for 30s (the Exhausted bar drains): no Super Speed moves, Speed Mode and Overdrive switch off, no speed passives, and you drop whatever you carry. 300s cooldown, starting when it ends.',
	[S + 'ability.speed_carry.desc']: 'N: snatch up the creature or player you are looking at and carry it up on your shoulders as you run, for as long as you like. It is a carry, not an attack: whatever you carry takes no fall damage and can\'t suffocate in walls, and it can\'t hurt you. Press N again to set it down in front of you — it stays safe from fall damage for 3 more seconds. You also let go if you die or become exhausted after a Time Slow. 2.5s cooldown.',
	[S + 'ability.overdrive.desc']: 'V: for 30s — sprint at roughly 64 blocks/second (a faster tier that replaces Speed Mode), +150% attack speed, a 10-block step assist, fall immunity, and your melee and every Super Speed move hit twice as hard. Your run gets even more extreme, your after-image trail turns red and leaves crackling yellow-white lightning streaking behind you, and speed explosions burst behind you while you run. 42.5s cooldown.',
	'message.projecthero.speed.exhausted': 'Exhausted — your speed is spent (%ss)',
	'message.projecthero.speed.exhausted_start': 'Exhausted! Time Slow took everything you had',
	'message.projecthero.speed.exhausted_end': 'Your speed is back',
	// ---- Laser Vision: heat no longer scales damage; venting waits 5 s, then 3 a second ----
	[L + 'desc']: 'A photonic mutation that runs on heat: twin eye beams that reach 100 blocks. Every beam heats your eyes — push it too far and you overheat.',
	[L + 'passive.heat']: 'Heat (0–100): every beam adds heat — Heat Vision 1 a second, Piercing Blast 10, Sweeping Arc 10, Recoil Blast 5, Ignite 2. Heat never changes how hard a beam hits. A full gauge overheats you: 3s locked out. Heat only starts to vent once you have not used Laser Vision for 5s, and then cools by 3 a second (3%).',
};

let text = fs.readFileSync(FILE, 'utf8');
const crlf = text.includes('\r\n');
let lines = text.replace(/\r\n/g, '\n').split('\n');

const esc = (v) => JSON.stringify(v);
const keyOf = (line) => {
	const m = line.match(/^\s*"((?:[^"\\]|\\.)*)"\s*:/);
	return m ? JSON.parse('"' + m[1] + '"') : null;
};

for (const [k, v] of Object.entries(SET)) {
	const idx = lines.findIndex((l) => keyOf(l) === k);
	const indent = '  ';
	if (idx >= 0) {
		const comma = lines[idx].trimEnd().endsWith(',') ? ',' : '';
		lines[idx] = indent + esc(k) + ': ' + esc(v) + comma;
		continue;
	}
	const group = k.substring(0, k.lastIndexOf('.') + 1);
	let anchor = -1;
	lines.forEach((l, i) => { const kk = keyOf(l); if (kk && kk.startsWith(group)) anchor = i; });
	if (anchor < 0) {
		anchor = lines.map((l) => l.trim()).lastIndexOf('}') - 1;
	}
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
console.log('en_us.json: set ' + Object.keys(SET).length + ' keys');

// Release pass: overview names the two new heroes and the four available mutations.
{
	const fs = require('fs');
	const f = 'src/main/resources/assets/projecthero/lang/en_us.json';
	const j = JSON.parse(fs.readFileSync(f, 'utf8'));
	j['projecthero.guide.overview.body'] = "Project Hero turns Minecraft into a superhero sandbox. Alongside iconic heroes like Thor, Iron Man, Spider-Man, Max Steel, the Punisher, Green Lantern, Wolverine, the Titan Shifter, All Might, Moon Knight, the Hulk, the Super Soldier and the Kryptonian, you can undergo experimental mutations to permanently gain minor powers. The mutations are being remade one by one: Super Strength, Laser Vision, Super Speed and Super Regeneration are available now, the rest return as they are rebuilt.";
	fs.writeFileSync(f, JSON.stringify(j, null, 2) + '\n');
}
