// TEMPORARY for local tests / filming only: appends lang_arctiguana.json's keys to en_us.json (just before its closing brace).
// Undo with:  git checkout -- src/main/resources/assets/projecthero/lang/en_us.json   (the lead merges the keys for real)
const fs = require('fs');
const path = require('path');
const f = path.join(__dirname, '../../src/main/resources/assets/projecthero/lang/en_us.json');
const keys = JSON.parse(fs.readFileSync(path.join(__dirname, 'lang_arctiguana.json'), 'utf8'));
let s = fs.readFileSync(f, 'utf8');
const nl = s.includes('\r\n') ? '\r\n' : '\n';
const have = JSON.parse(s);
const add = Object.entries(keys).filter(([k]) => !(k in have));
if (!add.length) {
	console.log('nothing to add');
	process.exit(0);
}
const end = s.lastIndexOf('}');
let head = s.slice(0, end).replace(/\s+$/, '');
head += ',' + nl + add.map(([k, v]) => '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v)).join(',' + nl) + nl;
fs.writeFileSync(f, head + '}' + nl);
JSON.parse(fs.readFileSync(f, 'utf8'));
console.log('added ' + add.length + ' keys (temporary)');
