// v0.17.2 (user: "Make stinkfly's wings look better and more like how his wings actually look like"): Stinkfly's wing sheet and goo
// texture, painted from docs/reference/stinkfly/stinkfly_wings.png.
//   stinkfly_wings.png  256x128: the UPPER wing in the left 128x128 cell, the LOWER wing in the right one. Each is drawn as the
//                       RIGHT-hand wing seen from behind, its root (where it joins the mount on his back) at the cell's left
//                       edge: a long blade sweeping up and out to a sharp point, the trailing edge scalloped into a notch with a
//                       barb and a hooked claw-like lobe; pale yellow-green, slightly translucent, thin darker veins dividing it
//                       into cells, a darker rim. Alpha 0 outside the silhouette (drawn with entityTranslucent, both faces).
//   stinkfly_goo.png    32x32: a pale mottled sheet the goo blobs / net strands are tinted from (vertex colour).
// Run: node scratchpad/ben10_v0171/gen_stinkfly_wings.js
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');

const OUT = path.join(__dirname, '../../src/main/resources/assets/projecthero/textures/entity/omnitrix');
const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));

// polygons in cell pixels (x out from the root, y down); smooth curves = many points
const UPPER = [
	[2, 98], [8, 92], [11, 78], [15, 88], // root top + the little upward spike by the mount
	[24, 80], [36, 68], [50, 52], [66, 36], [84, 21], [104, 9], [127, 1], // the leading edge up to the tip
	[114, 14], [100, 28], [88, 40], [78, 50], [70, 58], // the trailing edge coming back down
	[84, 62], [96, 64], [80, 68], [68, 68], // a barb sticking out
	[62, 72], [64, 80], [74, 86], [86, 94], [98, 108], // the hooked lower lobe out to its claw
	[84, 104], [70, 100], [56, 100], [44, 104], [34, 110], // under the claw, back in
	[26, 120], [22, 110], [12, 112], [2, 112], // a short spike pointing down, then the root
];
/** Holes punched through the membrane (ellipses: cx, cy, rx, ry) -- the round cut-out under the blade. */
const UPPER_HOLES = [[46, 84, 10, 7]];
const UPPER_VEINS = [
	[[4, 104], [28, 88], [52, 66], [74, 44], [100, 20], [124, 4]], // the spine, root to tip
	[[28, 88], [20, 82]], [[52, 66], [42, 60]], [[74, 44], [64, 38]], [[100, 20], [92, 16]], // cross veins to the leading edge
	[[52, 66], [66, 62]], [[74, 44], [80, 48]], [[100, 20], [104, 24]], // ...and to the trailing edge
	[[28, 88], [34, 98], [52, 96], [70, 92], [90, 102]], [[60, 74], [66, 84]], // the lobe's veins
];

const LOWER = [
	[2, 30], [14, 26], [30, 26], [48, 30], [66, 37], [82, 46], [96, 55], [126, 64], // upper edge out to the spike tip
	[100, 66], [88, 66], // back under the spike
	[76, 66], [74, 76], // the root of the claw
	[84, 92], [94, 104], [100, 124], // the hooked claw down and out
	[86, 110], [72, 98], [58, 88], [48, 82], // back in under the claw
	[44, 98], [38, 82], // a short barb pointing down
	[26, 70], [14, 56], [2, 46],
];
const LOWER_HOLES = [[58, 75, 6.5, 7.5]];
const LOWER_VEINS = [
	[[4, 38], [30, 44], [56, 52], [80, 56], [110, 62]],
	[[30, 44], [32, 30]], [[56, 52], [58, 36]], [[80, 56], [82, 48]],
	[[30, 44], [36, 62], [52, 78]], [[56, 52], [64, 68]], [[52, 78], [70, 90], [88, 104]],
];

function inside(poly, x, y) {
	let c = false;
	for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
		const [xi, yi] = poly[i], [xj, yj] = poly[j];
		if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c;
	}
	return c;
}
function segDist(px, py, ax, ay, bx, by) {
	const dx = bx - ax, dy = by - ay, l = dx * dx + dy * dy;
	const t = l ? clamp(((px - ax) * dx + (py - ay) * dy) / l) : 0;
	const qx = ax + dx * t - px, qy = ay + dy * t - py;
	return Math.sqrt(qx * qx + qy * qy);
}
function edgeDist(poly, x, y) {
	let d = 1e9;
	for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) d = Math.min(d, segDist(x, y, poly[j][0], poly[j][1], poly[i][0], poly[i][1]));
	return d;
}
function veinDist(veins, x, y) {
	let d = 1e9;
	for (const v of veins) for (let i = 1; i < v.length; i++) d = Math.min(d, segDist(x, y, v[i - 1][0], v[i - 1][1], v[i][0], v[i][1]));
	return d;
}

function paintWing(px, W, ox, poly, veins, holes) {
	const hit = (x, y) => inside(poly, x, y) && !holes.some(h => Math.pow((x - h[0]) / h[2], 2) + Math.pow((y - h[1]) / h[3], 2) < 1);
	const holeDist = (x, y) => Math.min(1e9, ...holes.map(h => Math.abs(Math.sqrt(Math.pow((x - h[0]) / h[2], 2) + Math.pow((y - h[1]) / h[3], 2)) - 1) * Math.min(h[2], h[3])));
	const N = 4; // 4x4 supersampling for the coverage
	for (let y = 0; y < 128; y++) for (let x = 0; x < 128; x++) {
		let cov = 0;
		for (let sy = 0; sy < N; sy++) for (let sx = 0; sx < N; sx++) if (hit(x + (sx + 0.5) / N, y + (sy + 0.5) / N)) cov++;
		cov /= N * N;
		if (cov === 0) continue;
		const cx = x + 0.5, cy = y + 0.5;
		const e = Math.min(edgeDist(poly, cx, cy), holeDist(cx, cy)), v = veinDist(veins, cx, cy);
		// the membrane: paler and clearer toward the outer half, a little deeper green at the root
		const out = clamp(cx / 128);
		let r = 236 - 14 * (1 - out), g = 240 - 8 * (1 - out), b = 168 - 36 * (1 - out), a = 228 - 22 * out;
		// a faint sheen band along the blade
		const sheen = Math.exp(-Math.pow((cx * 0.7 + (128 - cy) * 0.7 - 95) / 22, 2)) * 0.35;
		r += 18 * sheen; g += 14 * sheen; b += 30 * sheen;
		if (v < 1.1) { // the veins
			const k = 1 - v / 1.1;
			r = r + (150 - r) * k; g = g + (162 - g) * k; b = b + (78 - b) * k; a = Math.max(a, 235);
		}
		if (e < 1.6) { // the rim
			const k = 1 - e / 1.6;
			r = r + (140 - r) * k; g = g + (152 - g) * k; b = b + (70 - b) * k; a = Math.max(a, 245);
		}
		const o = (y * W + ox + x) * 4;
		px[o] = Math.round(clamp(r, 0, 255));
		px[o + 1] = Math.round(clamp(g, 0, 255));
		px[o + 2] = Math.round(clamp(b, 0, 255));
		px[o + 3] = Math.round(clamp(a * cov, 0, 255));
	}
}

{
	const W = 256, H = 128;
	const px = Buffer.alloc(W * H * 4);
	paintWing(px, W, 0, UPPER, UPPER_VEINS, UPPER_HOLES);
	paintWing(px, W, 128, LOWER, LOWER_VEINS, LOWER_HOLES);
	fs.writeFileSync(path.join(OUT, 'stinkfly_wings.png'), L.encodePng(W, H, px));
}
{
	// the goo: pale, softly mottled (tinted per blob), opaque -- the vertex alpha makes it translucent
	const S = 32;
	const px = Buffer.alloc(S * S * 4);
	for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
		const n = 0.5 + 0.25 * Math.sin(x * 0.9 + Math.sin(y * 0.7) * 2) + 0.25 * Math.sin(y * 1.3 + Math.cos(x * 0.5) * 2);
		const v = Math.round(215 + 40 * clamp(n));
		const o = (y * S + x) * 4;
		px[o] = v; px[o + 1] = v; px[o + 2] = v; px[o + 3] = 255;
	}
	fs.writeFileSync(path.join(OUT, 'stinkfly_goo.png'), L.encodePng(S, S, px));
}
console.log('stinkfly_wings.png + stinkfly_goo.png written');
