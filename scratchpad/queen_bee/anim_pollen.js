// v0.17.2: Pollen's GeckoLib clips -- Nooroo's six clips copied as animation.kwami.pollen.* (same bone names: the two
// pairs of wings flutter in every clip, the antennae bob). ONLY appends the new pollen keys to kwami.animation.json (text
// insertion before the closing braces, the file's line-ending / tab style kept); nothing else in the file is touched.
// Usage (repo root): node scratchpad/queen_bee/anim_pollen.js
'use strict';
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'animations', 'miraculous', 'kwami.animation.json');
const text = fs.readFileSync(file, 'utf8');
const j = JSON.parse(text);
if (j.animations['animation.kwami.pollen.idle']) {
	console.log('pollen clips already there');
	process.exit(0);
}
const add = {};
for (const c of ['idle', 'wave', 'eat', 'tired', 'into_jewel', 'pop_out']) {
	const src = j.animations['animation.kwami.nooroo.' + c];
	if (!src) throw new Error('no nooroo clip ' + c);
	add['animation.kwami.pollen.' + c] = JSON.parse(JSON.stringify(src));
}
const eol = text.includes('\r\n') ? '\r\n' : '\n';
let block = '';
for (const [k, v] of Object.entries(add)) {
	const body = JSON.stringify(v, null, '\t').split('\n').map((l, i) => (i === 0 ? l : '\t\t' + l)).join(eol);
	block += ',' + eol + '\t\t' + JSON.stringify(k) + ': ' + body;
}
const closeAnims = text.lastIndexOf(eol + '\t}');
if (closeAnims < 0) throw new Error('could not find the end of "animations"');
const out = text.slice(0, closeAnims) + block + text.slice(closeAnims);
JSON.parse(out);
fs.writeFileSync(file, out);
console.log('added', Object.keys(add).length, 'pollen clips');
