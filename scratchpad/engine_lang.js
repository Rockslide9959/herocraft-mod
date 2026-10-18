// v0.16.7 Engine quirk: inserts the Engine lang block after its anchor (CRLF kept, JSON.parse checked).
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, '..', 'src/main/resources/assets/projecthero/lang/en_us.json');
const ANCHOR = '"message.projecthero.quirk.overloaded.engine"';
const A = 'projecthero.quirk.ability.quirk_engine_';
const entries = [
	[A + 'recipro_kick', 'Recipro Kick'],
	[A + 'recipro_kick.desc', 'A fast three-kick combo, 5 damage a kick; the last one knocks back.'],
	[A + 'spin_kick', 'Spin Kick'],
	[A + 'spin_kick.desc', 'A 360 kick: 7 damage to everything within 3.5 blocks, big knockback.'],
	[A + 'recipro_burst', 'Recipro Burst'],
	[A + 'recipro_burst.desc', '10 s of huge speed, then the engines stall for 3 s. G again ends it early.'],
	[A + 'gear_up', 'Gear Up'],
	[A + 'gear_up.desc', '6 s of even more speed, at double the heat.'],
	[A + 'engine_leap', 'Engine Leap'],
	[A + 'engine_leap.desc', 'A big jump from the exhausts (about 9 blocks up). No fall damage on landing.'],
	[A + 'air_dash', 'Air Dash'],
	[A + 'air_dash.desc', 'A boost the way you look, once per jump. No fall damage on landing.'],
	[A + 'recipro_extend', 'Recipro Extend'],
	[A + 'recipro_extend.desc', 'A 15-block dash-kick: 10 damage to everything in the path.'],
	[A + 'afterburner_kick', 'Afterburner Kick'],
	[A + 'afterburner_kick.desc', 'One 12-damage kick that launches the target far the way you look.'],
	[A + 'rescue_carry', 'Rescue Carry'],
	[A + 'rescue_carry.desc', 'Pick up the ally or mob you look at and run with it. V again sets it down. Not bosses.'],
	[A + 'rescue_throw', 'Rescue Throw'],
	[A + 'rescue_throw.desc', 'Throw what you carry the way you look. Allies float down safely.'],
	[A + 'recipro_turbo', 'Recipro Turbo'],
	[A + 'recipro_turbo.desc', '3 s of extreme speed: every threat you pass takes 8 damage. +20 heat.'],
	[A + 'cool_down', 'Cool Down'],
	[A + 'cool_down.desc', 'Stop dead and vent 50 heat as steam. Ends any gear, with no stall.'],
	['message.projecthero.quirk.engine.air_dash_used', 'Land before dashing again.'],
	['message.projecthero.quirk.engine.no_target', 'Nothing to carry there.'],
	['message.projecthero.quirk.engine.too_big', '%s is far too big to carry!'],
	['message.projecthero.quirk.engine.cant_carry', 'You can\'t carry %s.'],
	['message.projecthero.quirk.engine.nothing_carried', 'You aren\'t carrying anything.'],
];
let text = fs.readFileSync(file, 'utf8');
const json = JSON.parse(text);
const fresh = entries.filter(([k]) => !(k in json));
if (fresh.length === 0) {
	console.log('already present');
	process.exit(0);
}
if (fresh.length !== entries.length) {
	throw new Error('some keys already exist: ' + entries.filter(([k]) => k in json).map(([k]) => k).join(', '));
}
const lines = text.split('\r\n');
const i = lines.findIndex((l) => l.trimStart().startsWith(ANCHOR));
if (i < 0) throw new Error('anchor not found');
const block = fresh.map(([k, v]) => '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + ',');
// the anchor line must end with a comma (it is not the last key in the file)
if (!lines[i].trimEnd().endsWith(',')) {
	lines[i] = lines[i] + ',';
	block[block.length - 1] = block[block.length - 1].replace(/,$/, '');
}
lines.splice(i + 1, 0, ...block);
const out = lines.join('\r\n');
JSON.parse(out);
fs.writeFileSync(file, out, 'utf8');
console.log('inserted ' + block.length + ' keys');
