// v0.17.5 Sentry rework: source skin vs processed suit at x8; texels the script cleared below the head show magenta.
//   node scratchpad/sentry/diff_sheet.js <out.png>
const path = require('path');
const PNG = require('../pnglib.js');
const R = path.join(__dirname, '..', '..');
const pairs = [['docs/reference/sentry/sentry_comics.png', 'src/main/resources/assets/projecthero/textures/entity/sentry/sentry_comics_wide.png'],
	['docs/reference/sentry/sentry_void.png', 'src/main/resources/assets/projecthero/textures/entity/sentry/sentry_void_wide.png']];
const S = 8, W = 64 * S * 2 + 8, H = 64 * S * 2 + 8;
const o = Buffer.alloc(W * H * 4);
pairs.forEach(([a, b], row) => {
	const A = PNG.read(path.join(R, a)), B = PNG.read(path.join(R, b));
	for (let k = 0; k < 2; k++) {
		const im = k ? B : A;
		for (let Y = 0; Y < 64 * S; Y++) for (let X = 0; X < 64 * S; X++) {
			const x = X / S | 0, y = Y / S | 0, i = (y * 64 + x) * 4;
			const chk = ((x + y) & 1) ? 110 : 80;
			let col = im.px[i + 3] ? [im.px[i], im.px[i + 1], im.px[i + 2]] : [chk, chk, chk];
			if (k === 1 && A.px[i + 3] && !B.px[i + 3] && y >= 16) col = [255, 0, 255];
			const j = ((row * (64 * S + 8) + Y) * W + k * (64 * S + 8) + X) * 4;
			o[j] = col[0]; o[j + 1] = col[1]; o[j + 2] = col[2]; o[j + 3] = 255;
		}
	}
});
PNG.write(process.argv[2], W, H, o);
