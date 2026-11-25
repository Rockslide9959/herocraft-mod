// v0.17.7: quick 16-frame review sheets of every film_* folder under a harness output dir.
//   node scratchpad/v0177/thor_sheets.js <screenshots dir> <out dir>
const fs = require('fs'), path = require('path'), { execFileSync } = require('child_process');
const src = process.argv[2], out = process.argv[3];
fs.mkdirSync(out, { recursive: true });
for (const d of fs.readdirSync(src)) {
	const full = path.join(src, d);
	if (!d.startsWith('film_') || !fs.statSync(full).isDirectory()) continue;
	const fr = fs.readdirSync(full).filter(f => /^f\d{3}\.png$/.test(f)).sort();
	if (fr.length < 2) continue;
	const n = Math.min(16, fr.length), pick = [];
	for (let i = 0; i < n; i++) pick.push(path.join(full, fr[Math.round(i * (fr.length - 1) / (n - 1))]));
	execFileSync('node', ['scratchpad/contact_sheet.js', path.join(out, d + '.png'), '4', '0.4', ...pick]);
	console.log(d, fr.length);
}
