// v0.17.3 Spine Whip lang: key-by-key, in place (line edits only; CRLF kept; refuses duplicate keys; validates the JSON). Run from the repo root.
const fs = require('fs');
const L = 'src/main/resources/assets/projecthero/lang/en_us.json';
let raw = fs.readFileSync(L, 'utf8');
const crlf = raw.includes('\r\n');
let lines = raw.replace(/\r\n/g, '\n').split('\n');
const keyOf = l => { const m = /^  "([^"]+)":/.exec(l); return m ? m[1] : null; };
function keys() { return lines.map(keyOf).filter(k => k); }
function dupCheck() {
	const k = keys();
	if (new Set(k).size !== k.length) throw new Error('duplicate keys in en_us.json');
}
dupCheck();
const q = s => JSON.stringify(s);
function idx(k) { return lines.findIndex(l => keyOf(l) === k); }
function set(k, v) {
	const i = idx(k);
	if (i < 0) throw new Error('no key ' + k);
	const comma = lines[i].trimEnd().endsWith(',');
	lines[i] = '  ' + q(k) + ': ' + q(v) + (comma ? ',' : '');
}
function remove(k) {
	const i = idx(k);
	if (i < 0) { console.log('(already gone) ' + k); return; }
	lines.splice(i, 1);
}
function addAfter(after, k, v) {
	if (idx(k) >= 0) { set(k, v); return; }
	const i = idx(after);
	if (i < 0) throw new Error('no anchor ' + after);
	if (!lines[i].trimEnd().endsWith(',')) lines[i] += ',';
	lines.splice(i + 1, 0, '  ' + q(k) + ': ' + q(v) + ',');
}

remove('projecthero.undead.ability.rib_cage_guard');
remove('projecthero.undead.ability.rib_cage_guard.desc');
addAfter('projecthero.undead.ability.rib_shotgun.desc', 'projecthero.undead.ability.spine_whip', 'Spine Whip');
addAfter('projecthero.undead.ability.spine_whip', 'projecthero.undead.ability.spine_whip.desc',
	'Tap Shift+V: you reach over your shoulder and tear your own spine out through your back, then lash it as a long whip of bone in a wide sweep in front of you (about 150°, out to 7 blocks): 14 damage to everything it passes through, knocking them out and along the swing. The hooked tip snags the one creature nearest your crosshair (at least a few blocks out) and yanks it in to your feet instead. Then the spine crumbles and the split in your back grows back like any lost part. It goes off on a quick tap only: holding Shift+V is Reform. Needs your torso and an arm. 7s cooldown.');
addAfter('message.projecthero.undead.no_chest', 'message.projecthero.undead.no_spine', 'You need your torso to tear your spine out');
set('projecthero.undead.ability.reform.desc',
	'Shift + hold V for 1 second (you hug yourself and the V box fills; a quick Shift+V is the Spine Whip instead, and letting go early does nothing): force your body back together at once. Every part that is gone or still regrowing is whole on the spot, you heal 3 hearts, and fire, poison, wither and every other harmful effect on you go. Not while the katana is out (Shift+V puts it away). 20s cooldown.');
set('projecthero.undead.ability.rib_shotgun.desc',
	'Tap V: you dig both hands into your chest, tear it open and your ribs spring out of it, then burst free as a spread of seven bone spikes (a 30° cone, aimed at your crosshair): 6 damage each and a little knockback, out to 20 blocks, so up close one target takes most of them. A spike that hits a wall sticks in it for a moment and crumbles. The blast rocks you back a step, and the hole in your chest grows back like any lost part (the ribs, then the muscle, then the skin). Needs your torso. 6s cooldown.');

dupCheck();
let out = lines.join('\n');
JSON.parse(out); // throws if broken
if (crlf) out = out.replace(/\n/g, '\r\n');
fs.writeFileSync(L, out);
console.log('lang ok, keys: ' + keys().length);
