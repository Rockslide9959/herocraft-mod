// v0.14.28: Iron Man suit names use plain numbers (Mark 1..7) instead of Roman numerals, in every lang value.
const fs = require('fs');
const path = require('path');
const FILE = path.join(__dirname, '../src/main/resources/assets/projecthero/lang/en_us.json');
const MAP = { XLII: '42', VIII: '8', VII: '7', VI: '6', IV: '4', III: '3', II: '2', V: '5', I: '1', L: '50' };
const re = /\b(Mark|Mk\.?) (XLII|VIII|VII|VI|IV|III|II|V|I|L)\b/g;
const keyRe = /^(\s*"(?:[^"\\]|\\.)*"\s*:\s*)(.*)$/;
let n = 0;
const out = fs.readFileSync(FILE, 'utf8').split(/(\r?\n)/).map(line => {
	const m = line.match(keyRe);
	if (!m) return line;
	return m[1] + m[2].replace(re, (_, w, r) => { n++; return w + ' ' + MAP[r]; });
}).join('');
fs.writeFileSync(FILE, out);
console.log('renamed', n);
