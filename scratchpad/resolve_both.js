// keep both sides of every conflict (HEAD first, then theirs): node resolve_both.js file
const fs = require('fs');
const f = process.argv[2];
let s = fs.readFileSync(f, 'utf8');
s = s.replace(/^<<<<<<< [^\r\n]*\r?\n([\s\S]*?)^=======\r?\n([\s\S]*?)^>>>>>>> [^\r\n]*\r?\n/gm, (m, a, b) => a + b);
fs.writeFileSync(f, s);
