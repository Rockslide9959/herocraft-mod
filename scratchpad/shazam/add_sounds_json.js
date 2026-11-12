// v0.17.3 Shazam: appends the shazam_word / shazam_crackle sound events (vanilla files) to sounds.json, CRLF-safe and idempotent.
// Run from the repo root: node scratchpad/shazam/add_sounds_json.js
const fs = require('fs');
const path = require('path');
const f = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds.json');
let t = fs.readFileSync(f, 'utf8');
if (t.includes('"shazam_word"')) {
	console.log('already there');
	process.exit(0);
}
const nl = t.includes('\r\n') ? '\r\n' : '\n';
const add = [
	',',
	'  "shazam_word": {',
	'    "subtitle": "subtitles.projecthero.shazam_word",',
	'    "sounds": [',
	'      { "name": "minecraft:ambient/weather/thunder1", "volume": 0.6 },',
	'      { "name": "minecraft:ambient/weather/thunder2", "volume": 0.6 },',
	'      { "name": "minecraft:ambient/weather/thunder3", "volume": 0.6 }',
	'    ]',
	'  },',
	'  "shazam_crackle": {',
	'    "subtitle": "subtitles.projecthero.shazam_crackle",',
	'    "sounds": [',
	'      { "name": "minecraft:random/fizz", "volume": 0.4, "pitch": 1.6 },',
	'      { "name": "minecraft:random/fizz", "volume": 0.4, "pitch": 1.9 }',
	'    ]',
	'  }',
	'}',
	''
].join(nl);
const i = t.lastIndexOf('}');
t = t.slice(0, i).replace(/\s*$/, '') + add;
JSON.parse(t);
fs.writeFileSync(f, t);
console.log('ok');
