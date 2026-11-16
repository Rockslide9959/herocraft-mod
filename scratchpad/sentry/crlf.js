// Normalises the given files to CRLF (the repo's Java / docs are CRLF; some editors write LF).
//   node scratchpad/sentry/crlf.js <file>...
const fs = require('fs');
for (const f of process.argv.slice(2)) {
	const s = fs.readFileSync(f, 'utf8');
	const out = s.replace(/\r?\n/g, '\r\n');
	if (out !== s) { fs.writeFileSync(f, out); console.log('crlf', f); }
}
