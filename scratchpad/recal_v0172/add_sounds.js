// v0.17.2: adds the Recalibrated Omnitrix's sound events to sounds.json (idempotent; text insert, keeps the file's format)
const fs = require('fs');
const path = 'src/main/resources/assets/projecthero/sounds.json';
let txt = fs.readFileSync(path, 'utf8');
const json = JSON.parse(txt);
const add = ['recal_prime', 'recal_transform', 'recal_detransform', 'recal_dial_turn'].filter(k => !json['omnitrix_' + k]);
if (!add.length) { console.log('nothing to add'); process.exit(0); }
const block = add.map(k => `  "omnitrix_${k}": {\n    "subtitle": "subtitles.projecthero.omnitrix_${k}",\n    "sounds": [\n      {\n        "name": "projecthero:omnitrix/${k}"\n      }\n    ]\n  }`).join(',\n');
const end = txt.lastIndexOf('}');
let head = txt.slice(0, end).replace(/\s*$/, '');
txt = head + ',\n' + block + '\n}\n';
JSON.parse(txt);
fs.writeFileSync(path, txt);
console.log('added', add);
