// node scratchpad/omnitrix_new/apply_lang.js <fragment.json> -- merges flat keys into en_us.json (CRLF kept, dup-checked).
const fs = require('fs');
const L = 'src/main/resources/assets/projecthero/lang/en_us.json';
let raw = fs.readFileSync(L, 'utf8');
const crlf = raw.includes('\r\n');
raw = raw.replace(/\r\n/g, '\n');
const keys = [...raw.matchAll(/^  "([^"]+)":/mg)].map(m => m[1]);
if (new Set(keys).size !== keys.length) throw new Error('duplicate keys already');
const lang = JSON.parse(raw);
const add = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
let a = 0, c = 0;
for (const [k, v] of Object.entries(add)) { if (!(k in lang)) a++; else if (lang[k] !== v) c++; lang[k] = v; }
const out = JSON.stringify(lang, null, 2) + '\n';
fs.writeFileSync(L, crlf ? out.replace(/\n/g, '\r\n') : out);
console.log(process.argv[2], { added: a, changed: c });
