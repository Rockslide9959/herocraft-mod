// v0.17.2 Feedback: a PLACEHOLDER dial icon source (until the user sends his icon), in the style of the user's icon sheet -- a grey
// disc, a black-edged lime diamond, a black silhouette: Feedback's head with its one eye and grin, shoulders, and his two antenna
// cables arcing out to plugs. Feed it to the transplant (which keeps the original ten's exact frame):
//   node scratchpad/ben10_v0171/gen_feedback_dial_placeholder.js
//   node scratchpad/gen_omnitrix_icon_transplant.js feedback=scratchpad/ben10_v0171/feedback_dl/dial_src/feedback_placeholder.png
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');

const W = 320, H = 320, SS = 3;
const cx = 160, cy = 160, hw = 100, hh = 150;
const inDiamond = (x, y, k = 0) => Math.abs(x - cx) / (hw - k) + Math.abs(y - cy) / (hh - k * 1.5) <= 1;
const inDisc = (x, y) => (x - cx) ** 2 + (y - cy) ** 2 <= 155 * 155;
const ell = (x, y, ex, ey, rx, ry) => ((x - ex) / rx) ** 2 + ((y - ey) / ry) ** 2 <= 1;
// a thick quadratic Bezier
function nearCurve(x, y, p0, p1, p2, w) {
	for (let i = 0; i <= 60; i++) {
		const t = i / 60, a = (1 - t) * (1 - t), b = 2 * (1 - t) * t, c = t * t;
		const qx = a * p0[0] + b * p1[0] + c * p2[0], qy = a * p0[1] + b * p1[1] + c * p2[1];
		if ((x - qx) ** 2 + (y - qy) ** 2 <= w * w) return true;
	}
	return false;
}
const rect = (x, y, x0, y0, x1, y1) => x >= x0 && x <= x1 && y >= y0 && y <= y1;
function silhouette(x, y) {
	if (ell(x, y, 160, 148, 30, 34)) {
		if (ell(x, y, 170, 136, 9, 6)) return false; // the eye
		if (y > 158 && y < 165 && Math.abs(x - 158) < 18 - (y - 158)) return false; // the grin
		return true;
	}
	if (y > 170 && y < 262 && Math.abs(x - 160) < 18 + (y - 170) * 0.55) return true; // neck and shoulders
	// the cables: up off the back of the head and out in arcs
	if (nearCurve(x, y, [172, 118], [214, 70], [218, 168], 4.2)) return true;
	if (nearCurve(x, y, [148, 120], [104, 82], [104, 176], 4.2)) return true;
	// the plugs: a barrel and two prongs at each cable's end, pointing down
	for (const [px, py] of [[218, 168], [104, 176]]) {
		if (rect(x, y, px - 7, py, px + 7, py + 22)) return true;
		if (rect(x, y, px - 5, py + 22, px - 2, py + 32) || rect(x, y, px + 2, py + 22, px + 5, py + 32)) return true;
	}
	return false;
}
const px = Buffer.alloc(W * H * 4);
for (let y = 0; y < H; y++) {
	for (let x = 0; x < W; x++) {
		let acc = [0, 0, 0], n = 0;
		for (let sy = 0; sy < SS; sy++) {
			for (let sx = 0; sx < SS; sx++) {
				const X = x + (sx + 0.5) / SS, Y = y + (sy + 0.5) / SS;
				let c = [255, 255, 255];
				if (inDisc(X, Y)) c = [96, 96, 96];
				if (inDiamond(X, Y)) c = [20, 20, 20];
				if (inDiamond(X, Y, 12)) c = silhouette(X, Y) ? [0, 0, 0] : [170, 240, 60];
				acc[0] += c[0]; acc[1] += c[1]; acc[2] += c[2]; n++;
			}
		}
		const i = (y * W + x) * 4;
		px[i] = acc[0] / n; px[i + 1] = acc[1] / n; px[i + 2] = acc[2] / n; px[i + 3] = 255;
	}
}
const out = path.join(__dirname, 'feedback_dl', 'dial_src', 'feedback_placeholder.png');
fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, L.encodePng(W, H, px));
console.log('wrote ' + out);
