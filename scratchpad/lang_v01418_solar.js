// v0.14.18: Kryptonian Solar Energy refills 50% faster everywhere (6 / 1.5 / 0.75 / 0.375 a second).
const fs = require('fs');
const path = require('path');
const lang = JSON.parse(fs.readFileSync(path.join(__dirname, '../src/main/resources/assets/projecthero/lang/en_us.json'), 'utf8'));
const k = 'projecthero.guide.kryptonian.solar.body';
const from = 'It fills 4 a second in direct sunlight, 1 a second in daytime shade or rain, 0.5 a second at night and 0.25 a second underground';
const to = 'It fills 6 a second in direct sunlight, 1.5 a second in daytime shade or rain, 0.75 a second at night and 0.375 a second underground';
let v = lang[k];
if (!v.includes(to)) { if (!v.includes(from)) throw new Error('solar text changed'); v = v.replace(from, to); }
require('./langset.js')([{ entries: { [k]: v } }]);
const d = path.join(__dirname, '../docs/KRYPTONIAN_REFERENCE.md');
fs.writeFileSync(d, fs.readFileSync(d, 'utf8').replace('Solar Energy per second: DIRECT 4, SHADE (day, no direct sun) 1, NIGHT 0.5, DARK (underground, Nether, End) 0.25.',
	'Solar Energy per second (v0.14.18, +50%): DIRECT 6, SHADE (day, no direct sun) 1.5, NIGHT 0.75, DARK (underground, Nether, End) 0.375.'));
console.log('solar text ok');
