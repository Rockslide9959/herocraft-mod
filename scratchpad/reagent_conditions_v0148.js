// v0.14.8: stamps every experimental power's reagent recipe with a fabric:load_conditions entry
// {"condition": "projecthero:power_enabled", "power": "<power key>"} so the recipe only loads while that power is in
// Powers.ENABLED. Re-runnable (replaces the condition if present). Run from the repo root: node scratchpad/reagent_conditions_v0148.js
const fs = require('fs');
const path = require('path');

const catalog = fs.readFileSync('src/main/java/com/projecthero/mod/hero/PowerCatalog.java', 'utf8');
const keys = [...catalog.matchAll(/String k = "(power_[a-z0-9_]+)"/g)].map(m => m[1]);
// mirrors ModSerums.shortName: power_01_super_strength -> super_strength
const shortName = k => { const us = k.indexOf('_', k.indexOf('_') + 1); return us > 0 ? k.substring(us + 1) : k; };

const dir = 'src/main/resources/data/projecthero/recipe';
let n = 0;
for (const key of keys) {
	const file = path.join(dir, shortName(key) + '_reagent.json');
	if (!fs.existsSync(file)) {
		throw new Error('no reagent recipe for ' + key + ' (' + file + ')');
	}
	const raw = fs.readFileSync(file, 'utf8');
	const eol = raw.includes('\r\n') ? '\r\n' : '\n';
	const json = JSON.parse(raw);
	const out = { 'fabric:load_conditions': [{ condition: 'projecthero:power_enabled', power: key }] };
	for (const [k, v] of Object.entries(json)) {
		if (k !== 'fabric:load_conditions') out[k] = v;
	}
	fs.writeFileSync(file, JSON.stringify(out, null, 2).replace(/\n/g, eol) + eol);
	n++;
}
console.log('stamped ' + n + ' reagent recipes');
