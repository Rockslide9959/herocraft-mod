// Resolve en_us.json merge conflicts key by key: keep every key from both sides; on the same key, ours wins.
const fs = require('fs');
const f = process.argv[2];
let t = fs.readFileSync(f, 'utf8');
const nl = t.includes('\r\n') ? '\r\n' : '\n';
const re = /<<<<<<< [^\n]*\n([\s\S]*?)=======\r?\n([\s\S]*?)>>>>>>> [^\n]*\n/g;
let hunks = 0;
const lines = s => s.split(/\r?\n/).filter(l => l.trim());
const key = l => (l.match(/^\s*"((?:[^"\\]|\\.)*)"\s*:/) || [])[1];
t = t.replace(re, (m, ours, theirs) => {
  hunks++;
  const out = lines(ours).map(l => l.replace(/,\s*$/, ''));
  const seen = new Set(out.map(key));
  for (const l of lines(theirs)) {
    const k = key(l);
    if (!seen.has(k)) { out.push(l.replace(/,\s*$/, '')); seen.add(k); } else console.log('dup kept ours:', k);
  }
  return out.map(l => l + ',').join(nl) + nl;
});
t = t.replace(/,(\s*)\}\s*$/, '$1}' + nl);
JSON.parse(t);
fs.writeFileSync(f, t);
console.log('hunks', hunks, 'json ok');
