// node scratchpad/doctorfate/views_skin.js <skin.png> <out.png> [slim|wide] [base|over|both]
// Flat front / right / back / left / top views of a 64x64 player skin, each part laid out like a standing player. Base layer drawn,
// then the outer layer over it; empty texels show as a plain skin tone (what the wearer's own skin would show). 12x.
'use strict';
const P = require('../pnglib.js');
const [, , inp, out, arms = 'slim', mode = 'both'] = process.argv;
const r = P.read(inp), aw = arms === 'slim' ? 3 : 4, S = 12;
const F = (u, v, w, h, d) => ({ top: [u + d, v, w, d], bottom: [u + d + w, v, w, d], right: [u, v + d, d, h], front: [u + d, v + d, w, h], left: [u + d + w, v + d, d, h], back: [u + 2 * d + w, v + d, w, h] });
// part: [base faces, over faces, w, h, d, x offset in a front view (player's right on the viewer's left), y offset]
const parts = {
	head: [F(0, 0, 8, 8, 8), F(32, 0, 8, 8, 8), 8, 8, 8, 4, 0], body: [F(16, 16, 8, 12, 4), F(16, 32, 8, 12, 4), 8, 12, 4, 4, 8],
	rarm: [F(40, 16, aw, 12, 4), F(40, 32, aw, 12, 4), aw, 12, 4, 4 - aw, 8], larm: [F(32, 48, aw, 12, 4), F(48, 48, aw, 12, 4), aw, 12, 4, 12, 8],
	rleg: [F(0, 16, 4, 12, 4), F(0, 32, 4, 12, 4), 4, 12, 4, 4, 20], lleg: [F(16, 48, 4, 12, 4), F(0, 48, 4, 12, 4), 4, 12, 4, 8, 20],
};
const VW = 22, VH = 34, views = ['front', 'right', 'back', 'left'], W = (VW * 4 + 3) * S, H = VH * S, o = Buffer.alloc(W * H * 4);
for (let i = 0; i < W * H; i++) { o[i * 4] = 40; o[i * 4 + 1] = 40; o[i * 4 + 2] = 48; o[i * 4 + 3] = 255; }
const px = (x, y) => r.px.slice((y * 64 + x) * 4, (y * 64 + x) * 4 + 4);
function plot(vx, vy, c) { for (let yy = 0; yy < S; yy++) for (let xx = 0; xx < S; xx++) { const d = ((vy * S + yy) * W + vx * S + xx) * 4; o[d] = c[0]; o[d + 1] = c[1]; o[d + 2] = c[2]; } }
views.forEach((view, vi) => {
	const X0 = vi * (VW + 1) + 3, Y0 = 1;
	const order = view === 'right' ? ['larm', 'lleg', 'body', 'head', 'rleg', 'rarm'] : view === 'left' ? ['rarm', 'rleg', 'body', 'head', 'lleg', 'larm'] : ['head', 'body', 'rarm', 'larm', 'rleg', 'lleg'];
	for (const name of order) {
		const [bf, of, w, h, d, ox, oy] = parts[name];
		const face = view === 'right' ? 'right' : view === 'left' ? 'left' : view;
		const fw = face === 'right' || face === 'left' ? d : w;
		// x placement: front: ox; back: mirrored; sides: depth centered at column 8
		let px0 = view === 'front' ? ox : view === 'back' ? (16 - ox - w) : 8 - d / 2;
		for (let y = 0; y < h; y++) for (let x = 0; x < fw; x++) {
			let c = [201, 154, 120];
			for (const [faces, use] of [[bf, mode !== 'over'], [of, mode !== 'base']]) {
				if (!use) continue;
				const [u, v] = faces[face], t = px(u + x, v + y);
				if (t[3] > 0) c = [t[0], t[1], t[2]];
			}
			plot(X0 + px0 + x, Y0 + oy + y, c);
		}
	}
});
P.write(out, W, H, o);
