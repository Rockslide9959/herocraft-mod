// v0.17.1 (user: "make all the omnitrix badges white ... thats how it is in the show"): the alien skins paint a small green
// hourglass under the chest badge quad (OmnitrixLayer), which peeked out round the new white badge. This turns those painted
// hourglass pixels white / light grey on the skins that have one (the generators still paint them green: re-run this after).
// Usage: node scratchpad/whiten_omnitrix_skin_badges.js
'use strict';
const path = require('path');
const PNG = require('./pnglib.js');
const DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'entity', 'omnitrix');
// skin -> the hourglass's top row (two columns 23-24: the top pair, and the bottom pair three rows lower)
const SKINS = { four_arms: 23, grey_matter: 22, heatblast: 22, ripjaws: 22, stinkfly: 22 };
const WHITE = [236, 239, 241], GREY = [190, 195, 199];
for (const [id, top] of Object.entries(SKINS)) {
	const f = path.join(DIR, id + '.png');
	const im = PNG.read(f);
	let n = 0;
	for (const y of [top, top + 3]) for (const x of [23, 24]) {
		const i = (y * im.W + x) * 4;
		const r = im.px[i], g = im.px[i + 1], b = im.px[i + 2];
		if (g > 140 && g > r * 1.4 && g > b * 1.4) {
			const c = y === top ? WHITE : GREY;
			im.px[i] = c[0]; im.px[i + 1] = c[1]; im.px[i + 2] = c[2];
			n++;
		}
	}
	PNG.write(f, im.W, im.H, im.px);
	console.log(id, n, 'px');
}
