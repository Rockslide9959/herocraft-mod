// Merges the Undead rework's lang fragments (scratchpad/undead/lang_<x>.json + lang_<x>_remove.json) into en_us.json.
const fs = require('fs');
const L = 'src/main/resources/assets/projecthero/lang/en_us.json';
let raw = fs.readFileSync(L, 'utf8');
const crlf = raw.includes('\r\n');
raw = raw.replace(/\r\n/g, '\n');
const keys = [...raw.matchAll(/^  "([^"]+)":/mg)].map(m => m[1]);
if (new Set(keys).size !== keys.length) throw new Error('en_us already has duplicate keys');
const lang = JSON.parse(raw);
const parts = ['core', 'eject', 'bullets', 'kit'];
const seen = {};
let removed = 0, added = 0, changed = 0;
for (const p of parts) {
  const rm = `scratchpad/undead/lang_${p}_remove.json`;
  if (fs.existsSync(rm)) for (const k of JSON.parse(fs.readFileSync(rm, 'utf8'))) if (k in lang) { delete lang[k]; removed++; }
}
for (const p of parts) {
  const add = JSON.parse(fs.readFileSync(`scratchpad/undead/lang_${p}.json`, 'utf8'));
  for (const [k, v] of Object.entries(add)) {
    if (seen[k] && seen[k] !== p) console.log(`overlap ${k}: ${seen[k]} then ${p}`);
    seen[k] = p;
    if (!(k in lang)) added++; else if (lang[k] !== v) changed++;
    lang[k] = v;
  }
}
let out = JSON.stringify(lang, null, 2) + '\n';
fs.writeFileSync(L, crlf ? out.replace(/\n/g, '\r\n') : out);
console.log({ removed, added, changed, total: Object.keys(lang).length });
