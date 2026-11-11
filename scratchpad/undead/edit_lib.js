// Tiny CRLF-safe exact-text editor for the v0.17.3 Spine Whip scripts: edit(file, [[from, to], ...]); refuses a missing anchor.
const fs = require('fs');
module.exports.edit = function edit(f, pairs) {
	let t = fs.readFileSync(f, 'utf8');
	const crlf = t.includes('\r\n');
	t = t.replace(/\r\n/g, '\n');
	for (const [a, b] of pairs) {
		if (!t.includes(a)) {
			console.error('MISSING in ' + f + ':\n' + a);
			process.exit(1);
		}
		t = t.split(a).join(b);
	}
	if (crlf) t = t.replace(/\n/g, '\r\n');
	fs.writeFileSync(f, t);
	console.log('edited ' + f);
};
