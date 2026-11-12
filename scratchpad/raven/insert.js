// node scratchpad/raven/insert.js <ops.json> -- CRLF-safe line insertions: [{file, after|before, lines:[...]}, {file, replace, with}]
const fs = require('fs');
const ops = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
for (const op of ops) {
  let s = fs.readFileSync(op.file, 'utf8');
  const nl = s.includes('\r\n') ? '\r\n' : '\n';
  if (op.replace !== undefined) {
    const n = s.split(op.replace).length - 1;
    if (n !== 1) throw new Error(op.file + ': replace anchor found ' + n + 'x: ' + op.replace);
    s = s.replace(op.replace, op.with);
  } else {
    const lines = s.split(nl);
    const anchor = op.after ?? op.before;
    const idx = lines.map((l, i) => l.includes(anchor) ? i : -1).filter(i => i >= 0);
    if (idx.length !== 1) throw new Error(op.file + ': anchor found ' + idx.length + 'x: ' + anchor);
    lines.splice(op.after !== undefined ? idx[0] + 1 : idx[0], 0, ...op.lines);
    s = lines.join(nl);
  }
  fs.writeFileSync(op.file, s);
  console.log('ok', op.file);
}
