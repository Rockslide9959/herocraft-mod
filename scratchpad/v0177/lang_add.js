// v0.17.7: add / replace en_us.json keys one by one (never rewrites other keys). Usage: node lang_add.js keys.json
const fs = require('fs');
const path = 'src/main/resources/assets/projecthero/lang/en_us.json';
const add = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
let text = fs.readFileSync(path, 'utf8');
const nl = text.includes('\r\n') ? '\r\n' : '\n';
const esc = s => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
for (const [k, v] of Object.entries(add)) {
  const enc = JSON.stringify(v);
  const re = new RegExp('("' + esc(k) + '":\\s*)"(?:[^"\\\\]|\\\\.)*"');
  if (re.test(text)) {
    text = text.replace(re, (m, p) => p + enc);
    console.log('replaced', k);
  } else {
    text = text.replace(/\s*\}\s*$/, ',' + nl + '  ' + JSON.stringify(k) + ': ' + enc + nl + '}' + nl);
    console.log('added', k);
  }
}
JSON.parse(text);
fs.writeFileSync(path, text);
