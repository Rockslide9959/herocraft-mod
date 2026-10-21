// Prints PNGs as ASCII luminance maps (. = transparent, 0-9 = luminance) -- for eyeballing icon shapes.
const k = require('./pngkit.js');
for (const f of process.argv.slice(2)) {
	const im = k.decode(f); console.log(f.split(/[\\/]/).pop(), im.w, im.h);
	for (let y = 0; y < im.h; y++) {
		let s = '';
		for (let x = 0; x < im.w; x++) {
			const i = (y * im.w + x) * 4;
			if (im.px[i + 3] < 128) { s += '.'; continue; }
			const l = im.px[i] * .3 + im.px[i + 1] * .59 + im.px[i + 2] * .11;
			s += Math.min(9, Math.floor(l / 25.6));
		}
		console.log(s);
	}
}
