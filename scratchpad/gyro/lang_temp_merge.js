// TEMP (films only): adds scratchpad/gyro/lang_power.json's keys to en_us.json so the harness client shows real text.
// Undo with: git checkout -- src/main/resources/assets/projecthero/lang/en_us.json   (en_us.json is merged key by key by the lead)
const fs = require('fs');
const W = process.argv[2];
const f = W + '/src/main/resources/assets/projecthero/lang/en_us.json';
let s = fs.readFileSync(f, 'utf8');
const nl = s.includes('\r\n') ? '\r\n' : '\n';
const add = JSON.parse(fs.readFileSync(W + '/scratchpad/gyro/lang_power.json', 'utf8'));
const have = JSON.parse(s);
const lines = Object.entries(add).filter(([k]) => !(k in have)).map(([k, v]) => '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v));
const end = s.lastIndexOf('}');
let head = s.slice(0, end).replace(/\s+$/, '');
s = head + ',' + nl + lines.join(',' + nl) + nl + '}' + nl;
JSON.parse(s);
fs.writeFileSync(f, s);
console.log('added', lines.length, 'keys (temp)');
