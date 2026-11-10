// usage: node scratchpad/undead/splice.js <file> <startMarker> <endMarker> <replacementFile>
// Replaces [startMarker, endMarker) in <file> with the replacement file's text (LF line endings).
const fs = require('fs');
const [file, start, end, repl] = process.argv.slice(2);
let t = fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n');
const a = t.indexOf(start);
const b = t.indexOf(end, a + 1);
if (a < 0 || b < 0) {
	console.error('marker not found', a, b);
	process.exit(1);
}
t = t.slice(0, a) + fs.readFileSync(repl, 'utf8').replace(/\r\n/g, '\n') + t.slice(b);
fs.writeFileSync(file, t);
console.log('spliced', file);
