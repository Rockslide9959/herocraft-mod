// v0.17.7: the Trial of Asgard's guardian became the Destroyer. Renames the old Stormwarden keys IN PLACE in en_us.json (same
// line, new key and text), drops the Storm Spirit key, then re-runs lang_thor_acquire.js (which now holds the Destroyer text)
// so every trial line is current. Key by key, never rewrites the file. Re-runnable. From the repo root:
//   node scratchpad/v0177/lang_destroyer.js
const fs = require('fs');
const path = require('path');
const FILE = path.join(__dirname, '../../src/main/resources/assets/projecthero/lang/en_us.json');

const RENAME = {
	'entity.projecthero.stormwarden': 'entity.projecthero.destroyer',
	'item.projecthero.stormwarden_spawn_egg': 'item.projecthero.destroyer_spawn_egg',
	'projecthero.trial.stormwarden.unbound': 'projecthero.trial.destroyer.unleashed',
	'projecthero.trial.stormwarden.phase2': 'projecthero.trial.destroyer.phase2',
	'projecthero.jei.stormwarden': 'projecthero.jei.destroyer',
};
const DROP = ['entity.projecthero.storm_spirit'];

let text = fs.readFileSync(FILE, 'utf8');
const eol = text.includes('\r\n') ? '\r\n' : '\n';
let lines = text.split(/\r?\n/);
const keyRe = /^(\s*)"((?:[^"\\]|\\.)*)"(\s*:)/;
lines = lines.filter(l => { const m = l.match(keyRe); return !(m && DROP.includes(m[2])); });
lines = lines.map(l => {
	const m = l.match(keyRe);
	if (m && RENAME[m[2]]) return l.replace(keyRe, `${m[1]}"${RENAME[m[2]]}"${m[3]}`);
	return l;
});
// the last entry before the closing brace must not end with a comma
let close = lines.length - 1;
while (close > 0 && lines[close].trim() !== '}') close--;
let prev = close - 1;
while (prev > 0 && lines[prev].trim() === '') prev--;
lines[prev] = lines[prev].replace(/,\s*$/, '');
const out = lines.join(eol);
JSON.parse(out);
fs.writeFileSync(FILE, out);
console.log('renamed / dropped the Stormwarden keys');
require('./lang_thor_acquire.js');
