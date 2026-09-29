// v0.14.1 Green Lantern: Carry Platform has no time limit / cooldown; the ring is a tiny cube. Run from repo root.
const fs = require('fs');
const lang = JSON.parse(fs.readFileSync(__dirname + '/../src/main/resources/assets/projecthero/lang/en_us.json', 'utf8'));
const key = 'projecthero.guide.green_lantern.constructs.body';
const old = lang[key];
const from = 'Carry Platform is a bright 3x3 platform spawned level with you;';
if (!old.includes(from)) throw new Error('anchor text moved');
require('./langset.js')([{ entries: {
	[key]: old.replace(from, 'Carry Platform is a bright 3x3 platform spawned level with you that stays for as long as you like — no time limit and no cooldown (it only costs its small upkeep; Shift the construct key to dismiss it);'),
} }]);
