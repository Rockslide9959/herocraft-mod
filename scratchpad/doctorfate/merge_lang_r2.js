// node scratchpad/doctorfate/merge_lang_r2.js -- v0.17.4 round 2: merges lang_fate_r2.json into en_us.json key by key (an existing key's
// line is replaced in place, new keys go in after the Doctor Fate guide's landing line), keeping the file's own line endings.
'use strict';
const fs = require('fs');
const path = require('path');
const LANG = path.join(__dirname, '../../src/main/resources/assets/projecthero/lang/en_us.json');
const add = JSON.parse(fs.readFileSync(path.join(__dirname, 'lang_fate_r2.json'), 'utf8'));
let s = fs.readFileSync(LANG, 'utf8');
const crlf = (s.match(/\r\n/g) || []).length;
const eol = crlf * 2 > (s.match(/\n/g) || []).length ? '\r\n' : '\n'; // the file's main line ending (it has a stray CRLF or two)
const lines = s.split(eol);
const ANCHOR = '  "projecthero.guide.doctor_fate.landing.body": ';
let anchor = lines.findIndex(l => l.startsWith(ANCHOR));
if (anchor < 0) throw new Error('anchor not found');
let replaced = 0, added = 0;
const fresh = [];
for (const [k, v] of Object.entries(add)) {
	const head = '  ' + JSON.stringify(k) + ': ';
	const i = lines.findIndex(l => l.startsWith(head));
	const body = head + JSON.stringify(v);
	if (i >= 0) {
		lines[i] = body + (lines[i].trimEnd().endsWith(',') ? ',' : '');
		replaced++;
	} else {
		fresh.push(body + ',');
		added++;
	}
}
if (!lines[anchor].trimEnd().endsWith(',')) throw new Error('anchor is the last key');
lines.splice(anchor + 1, 0, ...fresh);
const out = lines.join(eol);
JSON.parse(out); // still valid
fs.writeFileSync(LANG, out);
console.log('replaced', replaced, 'added', added);
