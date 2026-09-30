// v0.14.4 Moon Knight truncheon: lang keys (re-runnable after merge conflicts).
// Usage: node scratchpad/lang_v0144_truncheon.js   (from the repo root)
// Loads en_us.json, sets the keys below, writes it back with the same formatting (2-space JSON, same line ending,
// same trailing newline), and refuses to write if a plain parse/re-serialise of the ORIGINAL file wouldn't round-trip.
const fs = require('fs');
const path = require('path');

const file = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'lang', 'en_us.json');
const raw = fs.readFileSync(file, 'utf8');
const eol = raw.includes('\r\n') ? '\r\n' : '\n';
const trailing = /\r?\n$/.test(raw);
const serialise = (obj) => {
	let s = JSON.stringify(obj, null, 2);
	if (eol === '\r\n') s = s.replace(/\n/g, '\r\n');
	return trailing ? s + eol : s;
};
const lang = JSON.parse(raw);
if (serialise(lang) !== raw) {
	console.error('en_us.json does not round-trip through JSON.stringify(_, null, 2); refusing to rewrite it');
	process.exit(1);
}

const set = {
	'projecthero.moon_knight.move.truncheon.desc':
		'Press C and the Truncheon of Khonshu appears in your hand at once (whatever you were holding slides into your '
		+ 'inventory and goes back to that slot when you put it away). Tap C again to stow it. It hits for 7, and only '
		+ 'exists while you are suited: drop it, store it or take the suit off and it is simply gone.',
	'projecthero.moon_knight.move.truncheon_combo': 'Truncheon combo',
	'projecthero.moon_knight.move.truncheon_combo.desc':
		'Hits with the truncheon chain into a 3-hit combo: a forehand swing, a backhand (+3 damage and a shove), then a '
		+ 'two-handed overhead smash (+6 damage and heavy knockback), and round again. Land each hit within 1.5 seconds '
		+ 'of the last or it starts over; spam-clicking does not advance it. At night every hit on a mob heals you half a '
		+ 'heart. The bonuses grow with the moon.',
	'projecthero.moon_knight.move.staff_spin.desc':
		'Hold C: the truncheon extends into a staff and you spin a full circle, hitting everything within 3.5 blocks for '
		+ '15 and knocking it away. Brings the truncheon out first if it was not already.',
};
const del = [];

for (const [k, v] of Object.entries(set)) {
	lang[k] = v;
}
for (const k of del) {
	delete lang[k];
}
fs.writeFileSync(file, serialise(lang), 'utf8');
console.log('en_us.json: set ' + Object.keys(set).length + ', deleted ' + del.length);
