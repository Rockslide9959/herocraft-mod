// v0.15.15 Iron Man (suit-ups, menus, energy) lang: run with `node scratchpad/lang_v01515_ironman.js` from the repo root.
// Re-runnable: new keys are set (inserted after an anchor the first time), long existing values get idempotent sentence
// patches (skipped once applied). Every occurrence of a duplicated key is rewritten.
const fs = require('fs');
const path = require('path');
const FILE = path.join(__dirname, '../src/main/resources/assets/projecthero/lang/en_us.json');

const SET = [
	{ anchor: 'message.projecthero.ironman.no_suit_available', entries: {
		'message.projecthero.ironman.flight_launch': 'Launch! Flight burst in 1.5 seconds.',
		'message.projecthero.ironman.call_low_power': '%s needs at least %s%% power to be called -- charge it on its Suit Platform',
	} },
	{ anchor: 'projecthero.guide.iron_man.hand_build.body', entries: {
		'projecthero.guide.iron_man.suit_down': 'Taking the suit off (C)',
		'projecthero.guide.iron_man.suit_down.body': 'Press C in a Mark 1 to 7 and you take it off yourself, held still the whole time (your view no longer zooms in), with every piece going into your main inventory with its charge. The Mark 1 comes off by hand in about 18 seconds: you push the faceplate up, knock the helmet bolts loose with the hammer (a clank and a spray of sparks at each), wrench the helmet off and toss it aside; knock the bracer and chest rivets loose and haul the chestplate up over your head; hammer the knee bolts, loosen the hip straps and pull the greaves off; then knock the ankle clamps open, step out of the boots and toss them too. Each plate is held for a moment, dropped, and vanishes with a clank as it hits the floor. Marks 2 to 7 are sleek and take about 6 seconds: you stand relaxed with your arms a little out, the faceplate lifts, the suit powers down and every plate folds away panel by panel toward the arc reactor -- boots first, then the legs, the helmet and the arms, and the chest last of all into the reactor -- with a glowing seam at the retracting edge. Your own skin shows again on each part as soon as its plating is gone. The Mark 5 still folds into its suitcase and the Mark 8 and later still un-build. Log out or change dimension mid-way and it finishes at once; die and it stops where it is.',
	} },
];

// in-place sentence edits of long existing values: [key, from, to]
const PATCH = [
	['projecthero.guide.iron_man.mark_1.body',
		'Sneak + X launches you far ahead first.',
		'Sneak + X launches you far ahead and switches the burst on 1.5 s later.'],
	['projecthero.guide.iron_man.controls',
		'Unarmoured, C puts on any armour you are carrying straight away, and Sneak + C opens the Call Armour picker -- it lists every suit on your Suit Platforms',
		'Unarmoured, C puts on any armour you are carrying straight away; with the Stark Glasses on, Sneak + C (or C with nothing to put on) opens the Call Armour picker -- without them it does not open at all. It lists your Mark 8 and later suits on your Suit Platforms'],
	['projecthero.guide.iron_man.glasses.body',
		'-- off your Suit Platforms from the Sneak + C picker,',
		'-- off your Suit Platforms from the Sneak + C picker (it only opens with the glasses on, and only lists the Mark 8 and later),'],
	['projecthero.guide.iron_man.glasses.body',
		'Without them, C still puts on a suit you are carrying, the Mark 5 suitcase still opens and you can still send suits home.',
		'Without them, C still puts on a suit you are carrying and the Mark 5 suitcase still opens, but the call picker does not open.'],
	['projecthero.guide.iron_man.screens.body',
		'Sneak + C also lists armour carried in your pack -- pick it to send those pieces home to your platform.',
		'The picker (Stark Glasses only, Mark 8 and later) also lists armour carried in your pack -- pick it to send those pieces home to your platform; Sneak + C in a suit does nothing on Marks 1-7 (only the Mark 8 opens the picker / sends itself home that way).'],
	['projecthero.guide.iron_man.mark_vii.body',
		'Orbital drop: called off a Suit Platform,',
		'Calling it costs 10% of its power (the bracelets, the glasses or the orbital drop) -- under 10% it stays put. Orbital drop: called off a Suit Platform,'],
	['projecthero.guide.iron_man.bracelets.body',
		'The call uses the bracelets up:',
		'The call costs the Mark 7 10% of its power (with less it stays on its platform) and uses the bracelets up:'],
	['projecthero.guide.iron_man.gantry.body',
		'Remove Suit is exactly the same in reverse -- faceplate first, every built layer taken apart --',
		'Remove Suit is exactly the same in reverse -- faceplate first (the HUD and the mob highlight go dark the moment it lifts away, and come on the moment it is fitted when suiting up), every built layer taken apart --'],
	['projecthero.guide.iron_man.remote_pilot.body',
		'Unsuited, open the Call Armour picker and press PILOT on a charged full suit.',
		'Unsuited with the Stark Glasses on, open the Call Armour picker and press PILOT on a charged full suit.'],
	['projecthero.guide.iron_man.hand_build.body',
		'Log out or change dimension mid-build',
		'Your view no longer zooms in while you are held still. Log out or change dimension mid-build'],
];

let text = fs.readFileSync(FILE, 'utf8');
const eol = text.includes('\r\n') ? '\r\n' : '\n';
let lines = text.split(/\r?\n/);
const keyRe = /^\s*"((?:[^"\\]|\\.)*)"\s*:/;
const keyOf = (l) => { const m = l.match(keyRe); return m ? JSON.parse('"' + m[1] + '"') : null; };
const render = (k, v) => `  ${JSON.stringify(k)}: ${JSON.stringify(v)},`;

for (const [k, from, to] of PATCH) {
	let seen = false;
	for (let i = 0; i < lines.length; i++) {
		if (keyOf(lines[i]) !== k) continue;
		seen = true;
		const m = lines[i].match(/^(\s*"(?:[^"\\]|\\.)*"\s*:\s*)("(?:[^"\\]|\\.)*")(,?)\s*$/);
		const v = JSON.parse(m[2]);
		if (v.includes(to)) continue;
		if (v.includes(from)) lines[i] = m[1] + JSON.stringify(v.replace(from, to)) + m[3];
		else console.warn('WARN patch source missing in ' + k + ' (left as is)');
	}
	if (!seen) console.warn('WARN key missing: ' + k);
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
let close = lines.length - 1;
while (close > 0 && lines[close].trim() !== '}') close--;
let prev = close - 1;
while (prev > 0 && lines[prev].trim() === '') prev--;
lines[prev] = lines[prev].trimEnd().replace(/,$/, '');
const out = lines.join(eol);
const parsed = JSON.parse(out);
for (const g of SET) for (const [k, v] of Object.entries(g.entries)) if (parsed[k] !== v) throw new Error('not set: ' + k);
fs.writeFileSync(FILE, out);
console.log('lang updated');
