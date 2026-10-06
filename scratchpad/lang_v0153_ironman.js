// v0.15.3 Iron Man lang: run with `node scratchpad/lang_v0153_ironman.js` from the repo root.
// langset.js pattern (rewrite in place / insert after an anchor), but an existing key is rewritten at EVERY occurrence
// (en_us.json carries a few duplicated keys, e.g. projecthero.guide.iron_man.controls, and the last one is the one that
// wins), and keys listed in REMOVE are deleted.
const fs = require('fs');
const path = require('path');
const FILE = path.join(__dirname, '../src/main/resources/assets/projecthero/lang/en_us.json');

const SET = [
	{ anchor: 'screen.projecthero.ironman_info.integrity', entries: {
		'screen.projecthero.ironman_info.integrity_wear': 'Integrity wear',
		'screen.projecthero.ironman_info.integrity_wear_value': '%s%% of the damage you take',
		'screen.projecthero.ironman_info.flight_drain_value': '%s/s, energy regen halved',
	} },
	{ anchor: 'message.projecthero.ironman.platform_deploy_cancelled', entries: {
		'message.projecthero.ironman.platform_retrieving': 'Hold still - the platform is taking your suit off. Sneak to cancel.',
		'message.projecthero.ironman.platform_retrieve_cancelled': 'Suit removal cancelled - whatever is still on you stays on.',
	} },
	{ anchor: null, entries: {
		'screen.projecthero.weapon_wheel.hint': 'Hold V, point at an ability, let go to bind it to [X]',
		'screen.projecthero.weapon_wheel.mk3_hint': 'Hold V, point at a weapon, let go to fire it with [G]',
		'projecthero.guide.iron_man.platform.body': 'Any suit docked on a Suit Platform recharges 10 energy and repairs 10 integrity every second -- the only way to repair a suit, since worn suits never repair themselves. Deploy and Retrieve both use the platform\'s two robotic arms and take 8 seconds: you are stood in front of it with your back to it and held still while the arms fit each piece onto you (boots first) or lift the faceplate and take each piece off and back onto the rack (helmet first). Sneak to cancel either one. The platform no longer holds a reserve.',
		'screen.projecthero.stark_fabricator.stat.flight_drain': 'Flight drain',
		'projecthero.guide.iron_man.suit_up.body': 'Each piece takes 3 seconds to build onto you, boots first, then legs, chest and helmet: the inner layer closes around you in two halves, then the outer plating fills in one pixel at a time. You strike one of four random suit-up poses that change with the piece being built and react as each one locks home. Nothing can hurt you while a suit is assembling onto you -- piece by piece, from the Suit Platform\'s arms, flying in when called, out of the Mark 5 suitcase or the Mark 7 pod -- until the moment it comes online. The suit only comes online -- abilities and flight unlocked -- once every piece is fully built. Called armour flies in from its platform first; the Mark 7 arrives by pod and the Mark 5 unfolds from its suitcase.',
		'projecthero.guide.iron_man.mark_1.body': '500 energy (+2/s), 750 integrity, immune to arrows and fire, diamond-grade, +6 melee. No underwater air, no auto-feed. R Strong Punch (15). G Flamethrower (8/s + burning, reaches 10 blocks, overheats at 100 heat). Z Rocket (30). X Flight Burst: 20 s of flight at 8 blocks/s that never drops below half a block off the ground -- X again ends it; Sneak + X launches you far ahead first. V Mob Highlight (only you see it). C store suit.',
		'projecthero.guide.iron_man.mark_2.body': '1250 energy (+3/s), 1000 integrity, immune to arrows and fire, Resistance I with the chestplate, +6 melee, targeting HUD. R Repulsor (10; hold 1 s for an 18 charged shot), Sneak + R Repulsor Dash (15). G Sonic Clap (15 in a 12-block cone). Z Unibeam (6 s, 20 per hit). X Flares (blind + Slowness IV); Sneak + X lights fires like flint and steel, or while flying gives 30 s of supersonic flight. V Mob Highlight. C store suit.',
		'projecthero.guide.iron_man.mark_iii.body': '2000 energy (+5/s), 1000 integrity, immune to arrows and fire, Resistance I, +7 melee, breathes underwater, auto-feeds, targeting HUD. R Repulsor (15; hold 1 s for 20), Sneak + R Repulsor Dash (20). G fires the weapon picked on the V weapon wheel: Rockets (35, area), Miniguns (hold for 5 s) or Micro-Missiles (4 homing, 25 each). Sneak + G Sonic Clap (20). Z hold the Unibeam as long as you like (80 energy/s). X homing Flares that burn and knock back (supersonic flight if used while flying); Sneak + X JARVIS Scan of the area. Sneak + V Energy Shield blocks everything. C store suit.',
		'projecthero.guide.iron_man.mark_4.body': '3000 energy (+5/s), 1750 integrity, immune to arrows and fire, diamond-grade, +7 melee, breathes underwater, auto-feeds, targeting HUD. The full Mark 3 kit with +2 damage on every move and every cooldown 2 s shorter: R Repulsor (17; hold 1 s for 22), Sneak + R Dash (22). G fires the V wheel weapon: Rockets (37), Miniguns (12 per shot) or Micro-Missiles (4 x 27); Sneak + G Sonic Clap (22). X Flares, Sneak + X JARVIS scan. Hold Z Unibeam (22 per hit). Sneak + V Energy Shield.',
		'projecthero.guide.iron_man.mark_v.body': '2500 energy (+5/s), 800 integrity, immune to arrows and fire, diamond-grade, +6 melee, breathes underwater, auto-feeds, targeting HUD. The Mark 2 kit with an instant repulsor (no spin-up) and V Gauntlet Blades. C folds it into its suitcase in 4 s -- the case lands in your hand, then your hotbar, then your inventory. Right-click the case to suit up in 6 s: hold it out, it becomes the chestplate, then the arms, legs, helmet and faceplate build on. C and the Call Armour picker never deploy it. Fresh Fabricator pieces: with all four in your inventory, right-click one to pack them into a case.',
		'projecthero.guide.iron_man.armour_rules.body': 'Iron Man armour only goes on with C or a suit deploy, and only comes off with C, a Suit Platform retrieve or a send-home -- it can\'t be dragged in or out of your armour slots like normal armour. Every suit is bulletproof (guns do nothing while the chestplate is on) and gives 70% knockback resistance. Repulsors reach 50 blocks, and from the Mark 3 up the mob highlight switches on by itself when you put the helmet on. Hits land on you in full; the suit loses 75% of the damage you take as integrity (fire and lava barely scratch it), and a worn suit never repairs itself -- dock it on a Suit Platform. An open faceplate snaps shut the moment you take off, use an ability or get hit.',
		'projecthero.guide.iron_man.controls': 'Wearing a suit the six ability keys are R / G / X / Z / V / C; every mark has its own kit (see the Mark pages below, or press I for the spec sheet). Double-tap jump in the air to fly (except the Mark 1, which flies only with its Flight Burst). Flying costs 3 energy a second and halves the suit\'s energy regen while you are in the air. Unarmoured, C puts on any armour you are carrying straight away, and Sneak + C opens the Call Armour picker -- it lists every suit on your Suit Platforms (even ones in unloaded chunks or far away) and in your inventory, shows the hovered suit\'s stats side by side with your current one, can send a suit home to its platform for repairs, and has a PILOT button for flying a suit remotely. Calling a suit in needs your Stark Glasses on (Shift + N, see Stark Glasses & Stark Gear). The Mark 5 never answers C: it lives in its suitcase. Iron Man armour makes you immune to fall damage, and flying hard into the ground slams down for 20 damage, knocking everything nearby away.',
	} },
];

// in-place sentence edits of long existing values: [key, from, to]
const PATCH = [
	['projecthero.guide.iron_man.screens.body',
		'The Mark 7 weapon wheel is a ring of wedges -- point at one to read what it does.',
		'The weapon wheel (V on the Mark 3, 4 and 7) is open only while you hold V: point at a wedge to read what it does and let go of V to equip it (let go over the centre to keep what you have).'],
];

const REMOVE = [
	'screen.projecthero.ironman_info.damage_split',
	'screen.projecthero.ironman_info.damage_split_value',
	'screen.projecthero.ironman_info.self_repair',
	'screen.projecthero.stark_fabricator.stat.armour_regen',
	'screen.projecthero.suit_call.cmp.repair',
];

let text = fs.readFileSync(FILE, 'utf8');
const eol = text.includes('\r\n') ? '\r\n' : '\n';
let lines = text.split(/\r?\n/);
const keyRe = /^\s*"((?:[^"\\]|\\.)*)"\s*:/;
const keyOf = (l) => { const m = l.match(keyRe); return m ? JSON.parse('"' + m[1] + '"') : null; };
const render = (k, v) => `  ${JSON.stringify(k)}: ${JSON.stringify(v)},`;

for (const k of REMOVE) {
	lines = lines.filter(l => keyOf(l) !== k);
}
for (const [k, from, to] of PATCH) {
	for (let i = 0; i < lines.length; i++) {
		if (keyOf(lines[i]) !== k) continue;
		const m = lines[i].match(/^(\s*"(?:[^"\\]|\\.)*"\s*:\s*)("(?:[^"\\]|\\.)*")(,?)\s*$/);
		const v = JSON.parse(m[2]);
		if (v.includes(from)) lines[i] = m[1] + JSON.stringify(v.replace(from, to)) + m[3];
		else if (!v.includes(to)) throw new Error('patch source missing in ' + k);
	}
}
for (const g of SET) {
	let at = g.anchor ? lines.findIndex(l => keyOf(l) === g.anchor) : -1;
	for (const [k, v] of Object.entries(g.entries)) {
		let found = false;
		for (let i = 0; i < lines.length; i++) {
			if (keyOf(lines[i]) === k) {
				const keepComma = lines[i].trimEnd().endsWith(',');
				lines[i] = render(k, v).replace(/,$/, keepComma ? ',' : '');
				found = true;
			}
		}
		if (found) continue;
		if (at < 0) {
			let close = lines.length - 1;
			while (close > 0 && lines[close].trim() !== '}') close--;
			let prev = close - 1;
			while (prev > 0 && lines[prev].trim() === '') prev--;
			if (!lines[prev].trimEnd().endsWith(',') && lines[prev].trim() !== '{') lines[prev] = lines[prev].trimEnd() + ',';
			lines.splice(close, 0, render(k, v).replace(/,$/, ''));
			at = close;
			continue;
		}
		if (!lines[at].trimEnd().endsWith(',')) lines[at] = lines[at].trimEnd() + ',';
		const isLast = !lines[at + 1] || lines[at + 1].trim() === '}';
		lines.splice(at + 1, 0, isLast ? render(k, v).replace(/,$/, '') : render(k, v));
		at++;
	}
}
// the last entry before the closing brace must not carry a trailing comma
let close = lines.length - 1;
while (close > 0 && lines[close].trim() !== '}') close--;
let prev = close - 1;
while (prev > 0 && lines[prev].trim() === '') prev--;
lines[prev] = lines[prev].trimEnd().replace(/,$/, '');
const out = lines.join(eol);
const parsed = JSON.parse(out); // must stay valid JSON
for (const k of REMOVE) if (k in parsed) throw new Error('still present: ' + k);
for (const g of SET) for (const [k, v] of Object.entries(g.entries)) if (parsed[k] !== v) throw new Error('not set: ' + k);
fs.writeFileSync(FILE, out);
console.log('lang updated');
