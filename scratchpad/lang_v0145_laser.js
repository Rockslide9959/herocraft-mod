// v0.14.5 Laser Vision rework -- lang edits for en_us.json. Re-runnable (idempotent): run from the repo root with
//   node scratchpad/lang_v0145_laser.js
// Replaces a key's value in place, inserts missing keys after an anchor key, deletes removed keys. Touches no other
// line of the file (formatting, order and line endings are preserved).
const fs = require('fs');
const FILE = 'src/main/resources/assets/projecthero/lang/en_us.json';
const P = 'projecthero.power.power_02_laser_vision.';

const SET = [
	[P + 'desc', 'A photonic mutation that runs on heat: twin eye beams that reach 100 blocks, and the hotter your eyes run the deadlier they get. Push it too far and you overheat.'],
	[P + 'ability.heat_vision', 'Heat Vision'],
	[P + 'ability.heat_vision.desc', 'R: hold to fire twin heat beams from your eyes, up to 100 blocks — 4.8 damage every half-second, ramping up to 2.5x the longer you hold, setting what it touches alight. +1 heat a second. Aim it down while in mid-air and the beam holds you up, slowing your fall. Shift+R: Piercing Blast — a 100-block needle of light that runs through up to 4 creatures for 29 damage and cuts straight through glass, leaves and panes (with terrain damage on). +10 heat, 7.5s cooldown (shown on R).'],
	[P + 'ability.sweeping_arc', 'Sweeping Arc'],
	[P + 'ability.sweeping_arc.desc', 'G: swing the beam through a 150° arc across your view in half a second, 100 blocks out — 14.4 damage and fire to everything it crosses. +10 heat, 8.5s cooldown.'],
	[P + 'ability.recoil_blast', 'Recoil Blast'],
	[P + 'ability.recoil_blast.desc', 'X: fire a blast wherever you are looking, up to 100 blocks — the ground, a wall, behind you — and the recoil throws you the other way. 9.6 damage, knockback and fire in a 2.5-block splash where it lands; no fall damage for 4s after. +5 heat, 4.25s cooldown.'],
	[P + 'ability.maximum_output', 'Maximum Output'],
	[P + 'ability.maximum_output.desc', 'Z: press to charge for 1.5s (the bar above your keys), then a thick, pulsing, powered-up beam tears out for 10s, following your aim up to 100 blocks: 22 damage to what it hits plus splash, a 12-damage burst every half-second where it lands, and it burns through soft blocks. Takes you straight to max heat and holds it there — you overheat when it ends. Can\'t be used above 50% heat. 55s cooldown.'],
	[P + 'ability.ignite', 'Ignite'],
	[P + 'ability.ignite.desc', 'V: flick a pinpoint of heat up to 100 blocks, just like flint and steel — sets the block you hit on fire (with terrain damage on), lights campfires, candles and TNT, and sets a creature alight. +2 heat, 0.5s cooldown.'],
	[P + 'ability.thermal_vision', 'Thermal Vision'],
	[P + 'ability.thermal_vision.desc', 'C: toggle thermal highlighting of living things within 50 blocks, through walls and cover (only you can see it). Keeps your eyes warm: a slow trickle of heat, and heat does not vent while it is on.'],
	[P + 'passive.heat', 'Heat (0–100): every beam adds heat — Heat Vision 1 a second, Piercing Blast 10, Sweeping Arc 10, Recoil Blast 5, Ignite 2 — and every beam hits up to 60% harder the hotter your eyes run. A full gauge overheats you: 3s locked out while it vents. Heat vents on its own a second after you stop firing.'],
	['message.projecthero.laser.too_hot_for_max', 'Too hot for Maximum Output — cool below 50% heat'],
];
// where a missing key goes (after this existing key)
const ANCHOR = {
	[P + 'ability.ignite']: P + 'ability.maximum_output.desc',
	[P + 'ability.ignite.desc']: P + 'ability.ignite',
	['message.projecthero.laser.too_hot_for_max']: 'message.projecthero.laser.overheated',
};
const REMOVE = [
	P + 'ability.piercing_lance', P + 'ability.piercing_lance.desc',
	P + 'ability.ricochet_shot', P + 'ability.ricochet_shot.desc',
	P + 'ability.cauterize', P + 'ability.cauterize.desc',
	P + 'passive.blindness', P + 'passive.glow',
	'message.projecthero.laser.max_ready', 'message.projecthero.laser.not_hot_enough',
];

const raw = fs.readFileSync(FILE, 'utf8');
const eol = raw.includes('\r\n') ? '\r\n' : '\n';
let lines = raw.split(eol);
const find = key => lines.findIndex(l => l.trimStart().startsWith(JSON.stringify(key) + ':'));
const line = (key, value, comma) => '  ' + JSON.stringify(key) + ': ' + JSON.stringify(value) + (comma ? ',' : '');

for (const key of REMOVE) {
	const i = find(key);
	if (i >= 0) {
		if (!lines[i].trimEnd().endsWith(',')) {
			// last entry: move the "no comma" onto the previous entry
			lines[i - 1] = lines[i - 1].replace(/,\s*$/, '');
		}
		lines.splice(i, 1);
	}
}
for (const [key, value] of SET) {
	const i = find(key);
	if (i >= 0) {
		lines[i] = line(key, value, lines[i].trimEnd().endsWith(','));
	} else {
		const a = find(ANCHOR[key]);
		if (a < 0) {
			throw new Error('no anchor for ' + key);
		}
		lines.splice(a + 1, 0, line(key, value, true));
		if (!lines[a].trimEnd().endsWith(',')) {
			lines[a] = lines[a] + ',';
			lines[a + 1] = lines[a + 1].replace(/,$/, '');
		}
	}
}
const out = lines.join(eol);
JSON.parse(out); // still valid JSON
fs.writeFileSync(FILE, out);
console.log('lang_v0145_laser: ok');
