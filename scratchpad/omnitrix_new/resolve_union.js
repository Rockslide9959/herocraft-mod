// Resolves the new-alien merge conflicts: both sides' lines kept (union), with per-file fixes.
// Usage: node resolve_union.js <aliensMergedCount>
const fs = require('fs'), cp = require('child_process');
const N = +process.argv[2];
const files = cp.execSync('git diff --name-only --diff-filter=U', { encoding: 'utf8' }).trim().split('\n').filter(Boolean);
const FULL = '"snare_oh", "eye_guy", "frankenstrike", "arctiguana", "benwolf", "shock_rock"';
for (const f of files) {
  let s = fs.readFileSync(f, 'utf8');
  s = s.replace(/<<<<<<< [^\n]*\r?\n([\s\S]*?)=======\r?\n([\s\S]*?)>>>>>>> [^\n]*\r?\n/g, (m, a, b) => {
    const nl = m.includes('\r\n') ? '\r\n' : '\n';
    if (/Aliens\.(all|playable)\(\)\.size\(\)/.test(a)) {
      // one line, the count set to 17 + aliens merged so far
      return a.replace(/size\(\) (==|!=) \d+/, 'size() $1 ' + (17 + N)).replace(/expected all \d+/, 'expected all ' + (17 + N));
    }
    if (f.endsWith('OmnitrixInfo.java')) {
      return '\t\t\t// v0.17.3: the new aliens, U+E01B..U+E020 (Snare-oh, Eye Guy, Frankenstrike, Arctiguana, Benwolf, Shock Rock)' + nl + '\t\t\t' + FULL + ');' + nl;
    }
    if (f.endsWith('Aliens.java') && /"\); \/\/ v0\.17\.3/.test(a)) {
      const ids = (a + b).match(/"[a-z_]+"/g);
      return '\t\t\t' + [...new Set(ids)].join(', ') + '); // v0.17.3' + nl;
    }
    if (f.endsWith('omnitrix_dial.json')) {
      const lines = (a + b).split(/\r?\n/).filter(l => l.trim()).map(l => l.replace(/,\s*$/, ''));
      return lines.join(',' + nl) + nl;
    }
    // registrations, test lists: keep both sides, no duplicate lines
    const out = [];
    for (const l of (a + b).split(/\r?\n/)) if (l.length && !out.includes(l)) out.push(l);
    if (f.endsWith('fabric.mod.json')) return out.map(l => l.replace(/,\s*$/, '')).map((l, i, arr) => l + ',').join(nl) + nl;
    return out.join(nl) + nl;
  });
  fs.writeFileSync(f, s);
  console.log('resolved', f);
}
