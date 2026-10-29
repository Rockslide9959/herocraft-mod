// Applies exact text replacements, CRLF-aware: node apply.js <edits.js>; edits.js exports [{ file, pairs: [[from, to], ...] }]
// (paths relative to the repo root). Every "from" must occur exactly once; nothing is written if any edit misses.
'use strict';
const fs = require('fs'), path = require('path');
const root = path.join(__dirname, '..', '..');
const edits = require(path.resolve(process.argv[2]));
const out = [];
for (const e of edits) {
	const f = path.join(root, e.file);
	let s = fs.readFileSync(f, 'utf8');
	const crlf = s.includes('\r\n');
	s = s.replace(/\r\n/g, '\n');
	for (const [a, b] of e.pairs) {
		if (a === '__END__') { // insert before the class's final closing brace
			const i = s.lastIndexOf('}');
			s = s.slice(0, i) + b + s.slice(i);
			continue;
		}
		const n = s.split(a).length - 1;
		if (n !== 1) {
			console.error(`${e.file}: ${n} matches for: ${a.slice(0, 90)}`);
			process.exit(1);
		}
		s = s.replace(a, () => b);
	}
	out.push([f, crlf ? s.replace(/\n/g, '\r\n') : s]);
}
for (const [f, s] of out) fs.writeFileSync(f, s);
console.log('applied', edits.length, 'file(s)');
