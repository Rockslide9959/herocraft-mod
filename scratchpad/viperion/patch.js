// Applies literal "insert after / replace" edits to source files. Usage: node patch.js <edits.json>
// edits.json: [{ "file": "...", "find": "...", "after": "..."} | { "file", "find", "replace" }]
// Each find must occur exactly once. Line endings of the file are kept (\n in the edit becomes the file's EOL).
'use strict';
const fs = require('fs');
const edits = JSON.parse(fs.readFileSync(process.argv[2], 'utf8').replace(/\t/g, '\\t'));
let bad = 0;
for (const e of edits) {
	let s = fs.readFileSync(e.file, 'utf8');
	const eol = s.includes('\r\n') ? '\r\n' : '\n';
	const find = e.find.replace(/\n/g, eol);
	const n = s.split(find).length - 1;
	if (n !== 1) { console.error(`FAIL (${n} matches) ${e.file}: ${e.find.slice(0, 80)}`); bad++; continue; }
	const repl = e.replace != null ? e.replace.replace(/\n/g, eol) : find + eol + e.after.replace(/\n/g, eol);
	s = s.replace(find, () => repl);
	fs.writeFileSync(e.file, s);
}
console.log(bad ? `${bad} failed` : `ok ${edits.length}`);
process.exit(bad ? 1 : 0);
