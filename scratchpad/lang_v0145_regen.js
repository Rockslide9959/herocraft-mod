// v0.14.5 Super Regeneration rework: en_us.json edits. Line-based, so the rest of the file keeps its exact
// formatting (and its CRLF line endings). Idempotent -- safe to re-run after a merge.
//   node scratchpad/lang_v0145_regen.js
const fs = require('fs');
const path = require('path');

const FILE = path.join(__dirname, '../src/main/resources/assets/projecthero/lang/en_us.json');
const P = 'projecthero.power.power_12_super_regeneration.';

const REMOVE = [
	...['rapid_heal', 'purge', 'resurrection', 'cellular_surge', 'regeneration_mode', 'adrenal_rush', 'blood_rage', 'mend']
		.flatMap(a => [P + 'ability.' + a, P + 'ability.' + a + '.desc']),
	P + 'passive.adrenaline',
	P + 'passive.debuff',
	'message.projecthero.heal.resurrection_ready',
	'message.projecthero.heal.resurrection_cooldown',
	'message.projecthero.heal.too_hungry',
	'message.projecthero.regen.no_adrenaline',
	'message.projecthero.regen.no_allies',
];

// [key, value, key to insert after when missing]
const SET = [
	[P + 'desc', 'Pure cellular regeneration, always on: no keys to press. You heal 10 health every quarter second, burn off any harmful effect within 2 seconds, and carry 3 revive charges that each recharge on their own. Ascends into Wolverine with an Adamantium Serum.', P + 'name'],
	[P + 'passive.regen', 'Healing: 2 health every tick (40 health a second) whenever you are hurt, with no hunger cost. Red veins pulse and trickle over your whole body while it works', P + 'trigger'],
	[P + 'passive.cleanse', 'Cleansing: any harmful effect (poison, wither, slowness, weakness...) dissolves out of you after 2 seconds. The unstable mutation is not a poison and is never touched', P + 'passive.regen'],
	[P + 'passive.revive', 'Revive charges: 3 charges, shown as green dots beside the power name (grey while recharging). A killing blow spends one before any Totem of Undying: you get back up at half health, cleansed, with 1 second of damage immunity. Each spent charge recharges on its own 60-second timer. /kill and the void still kill you', P + 'passive.cleanse'],
	['projecthero.guide.power.no_abilities', 'No ability keys: %s is always on.', 'projecthero.guide.power.abilities'],
];

let text = fs.readFileSync(FILE, 'utf8');
const eol = text.includes('\r\n') ? '\r\n' : '\n';
let lines = text.split(eol);

const keyOf = line => { const m = line.match(/^\s*"((?:[^"\\]|\\.)*)"\s*:/); return m ? m[1] : null; };
const removeSet = new Set(REMOVE);
const before = lines.length;
lines = lines.filter(l => !removeSet.has(keyOf(l)));
console.log('removed', before - lines.length, 'lines');

for (const [key, value, after] of SET) {
	const entry = '  ' + JSON.stringify(key) + ': ' + JSON.stringify(value) + ',';
	const i = lines.findIndex(l => keyOf(l) === key);
	if (i >= 0) {
		const trailingComma = /,\s*$/.test(lines[i]);
		lines[i] = trailingComma ? entry : entry.slice(0, -1);
		console.log('set', key);
		continue;
	}
	const a = lines.findIndex(l => keyOf(l) === after);
	if (a < 0) throw new Error('anchor not found: ' + after);
	if (!/,\s*$/.test(lines[a])) lines[a] = lines[a] + ',';
	const last = !lines.slice(a + 1).some(l => keyOf(l) !== null);
	lines.splice(a + 1, 0, last ? entry.slice(0, -1) : entry);
	console.log('added', key);
}

text = lines.join(eol);
JSON.parse(text); // must still be valid JSON
fs.writeFileSync(FILE, text);
console.log('ok');
