// node scratchpad/doctorfate/fix_eol.js <file>... -- a file that is mostly CRLF but has some bare LF lines (an edit tool wrote
// them) is made all CRLF; a mostly-LF file is left alone. Prints what it did.
const fs = require('fs');
for (const f of process.argv.slice(2)) {
	if (!fs.existsSync(f)) continue;
	const s = fs.readFileSync(f, 'utf8');
	const crlf = (s.match(/\r\n/g) || []).length;
	const lf = (s.match(/\n/g) || []).length - crlf;
	if (crlf > 0 && lf > 0 && crlf >= lf) {
		fs.writeFileSync(f, s.replace(/\r?\n/g, '\r\n'));
		console.log('fixed', f, 'crlf', crlf, 'bare lf', lf);
	} else {
		console.log('ok   ', f, 'crlf', crlf, 'lf', lf);
	}
}
