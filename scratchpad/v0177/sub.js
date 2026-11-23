// v0.17.7: exact substring replacements, CRLF-safe (multi-line "from"/"to" follow the file's line endings).
// Usage: node sub.js edits.json   edits.json: [{"file": "...", "from": "...", "to": "..."}, ...]
// Each "from" must occur exactly once; if any edit fails nothing is written. Literal tabs in the JSON are allowed.
const fs = require('fs');
const edits = JSON.parse(fs.readFileSync(process.argv[2], 'utf8').replace(/\t/g, '\\t'));
const cache = {};
let bad = 0;
for (const e of edits) {
  const t = cache[e.file] ?? fs.readFileSync(e.file, 'utf8');
  cache[e.file] = t;
  const nl = t.includes('\r\n') ? '\r\n' : '\n';
  const from = e.from.replace(/\r?\n/g, nl);
  const n = t.split(from).length - 1;
  if (n !== 1) { console.error(`x ${e.file}: "${e.from.slice(0, 80)}" found ${n} times`); bad++; continue; }
  cache[e.file] = t.replace(from, () => e.to.replace(/\r?\n/g, nl));
  console.log(`ok ${e.file.split('/').pop()}: ${e.from.trim().slice(0, 60)}`);
}
if (bad) { console.error(bad + ' edit(s) failed; nothing written'); process.exit(1); }
for (const [f, t] of Object.entries(cache)) fs.writeFileSync(f, t);
