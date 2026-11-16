// v0.17.5 Sentry rework: finds the first trial frame showing the verdict line (violet = won, red = lost) and copies it.
//   node scratchpad/sentry/pick_verdict.js <shots dir> <prefix> <out.png>
const fs = require('fs');
const path = require('path');
const PNG = require('../pnglib.js');
const [, , dir, prefix, out] = process.argv;
const files = fs.readdirSync(dir).filter((f) => f.startsWith(prefix)).sort();
for (const f of files) {
	const im = PNG.read(path.join(dir, f));
	let hits = 0;
	for (let y = Math.floor(im.H / 2) - 14; y < Math.floor(im.H / 2) + 14; y++) for (let x = 0; x < im.W; x++) {
		const i = (y * im.W + x) * 4, r = im.px[i], g = im.px[i + 1], b = im.px[i + 2];
		if ((r > 160 && g < 150 && b > 220) || (r > 230 && g < 110 && b < 110)) hits++;
	}
	if (hits > 40) {
		fs.copyFileSync(path.join(dir, f), out);
		console.log(prefix, '->', f, hits);
		process.exit(0);
	}
}
console.log('no verdict frame for', prefix);
