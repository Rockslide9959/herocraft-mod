// v0.17.2 (user: "make xlr8s tail better"): the sheet for Xlr8Tail's round tube -- textures/entity/omnitrix/xlr8_tail.png, 16x64.
// x runs round the tube (its middle is the top of the tail), y from the root to the tip. His skin's own navy, and the teal chevrons
// the source skin (xlr8_v0171/xlr8_src.png) paints down the tail, pointing at the tip and shrinking toward it; no pale tip (v0.17.2 user).
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');

const OUT = path.join(__dirname, '../../src/main/resources/assets/projecthero/textures/entity/omnitrix/xlr8_tail.png');
const W = 16, H = 64;
const NAVY = [14, 18, 27], NAVY_LIT = [26, 35, 48], BELLY = [9, 11, 18], TEAL = [79, 152, 160], TEAL_DARK = [52, 104, 116], PALE = [208, 226, 232];
const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));
const px = Buffer.alloc(W * H * 4);
for (let y = 0; y < H; y++) {
	for (let x = 0; x < W; x++) {
		const across = Math.abs(x + 0.5 - W / 2) / (W / 2); // 0 on top .. 1 underneath
		let c = mix(mix(NAVY_LIT, NAVY, Math.min(1, across * 2)), BELLY, Math.max(0, across * 2 - 1));
		// the chevrons: five, each a band slanting back from the top line, narrower and closer toward the tip
		for (const [at, band, wide] of [[9, 3.2, 0.62], [20, 2.8, 0.56], [30, 2.4, 0.5], [39, 2, 0.44], [47, 1.6, 0.38]]) {
			const d = y - (at - across * 16);
			if (across < wide && d >= 0 && d < band) c = d < 1 ? TEAL_DARK : TEAL;
		}
		const o = (y * W + x) * 4;
		px[o] = c[0]; px[o + 1] = c[1]; px[o + 2] = c[2]; px[o + 3] = 255;
	}
}
fs.writeFileSync(OUT, L.encodePng(W, H, px));
console.log('xlr8_tail.png written');
