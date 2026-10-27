// v0.17.1 Erasure + Zero Gravity revamp: appends the two kits' sound events to sounds.json in ONE block at the end (text
// append, so the rest of the file is untouched). Idempotent.   node scratchpad/add_sounds_erasure_zerogravity.js
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds.json');
const events = {
	quirk_erasure_scarf_whip: ['quirk/erasure_scarf_whip1', 'quirk/erasure_scarf_whip2'],
	quirk_erasure_scarf_snap: ['quirk/erasure_scarf_snap1', 'quirk/erasure_scarf_snap2'],
	quirk_erasure_glare: ['quirk/erasure_glare'],
	quirk_erasure_bind: ['quirk/erasure_bind'],
	quirk_erasure_goggles: ['quirk/erasure_goggles'],
	quirk_zerogravity_touch: ['quirk/zerogravity_touch1', 'quirk/zerogravity_touch2'],
	quirk_zerogravity_float: ['quirk/zerogravity_float'],
	quirk_zerogravity_release: ['quirk/zerogravity_release'],
	quirk_zerogravity_meteor: ['quirk/zerogravity_meteor'],
	quirk_zerogravity_push: ['quirk/zerogravity_push'],
};
let text = fs.readFileSync(file, 'utf8');
const crlf = text.includes('\r\n');
text = text.replace(/\r\n/g, '\n');
if (text.includes('"quirk_erasure_scarf_whip"')) {
	console.log('already there');
	process.exit(0);
}
const end = text.lastIndexOf('}');
let body = text.slice(0, end).replace(/\s+$/, '');
let block = '';
for (const [ev, files] of Object.entries(events)) {
	block += ',\n  "' + ev + '": {\n    "subtitle": "subtitles.projecthero.' + ev + '",\n    "sounds": [\n';
	block += files.map(f => '      {\n        "name": "projecthero:' + f + '"\n      }').join(',\n');
	block += '\n    ]\n  }';
}
let out = body + block + '\n}\n';
JSON.parse(out); // still valid
if (crlf) out = out.replace(/\n/g, '\r\n');
fs.writeFileSync(file, out);
console.log('added ' + Object.keys(events).length + ' events');
