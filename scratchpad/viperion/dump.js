// dump a region of a skin as hex (for reading the source skin)
'use strict';
const T = require('./skin_tools.js');
const [file, x0, y0, w, h] = process.argv.slice(2);
const img = T.decode(file);
for (let y = +y0; y < +y0 + +h; y++) {
	let line = String(y).padStart(2) + ': ';
	for (let x = +x0; x < +x0 + +w; x++) {
		const i = (y * img.w + x) * 4;
		line += img.px[i + 3] === 0 ? '  ....  ' : ' ' + [0, 1, 2].map(k => img.px[i + k].toString(16).padStart(2, '0')).join('') + ' ';
	}
	console.log(line);
}
