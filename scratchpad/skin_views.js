// Flat front / back / right / left views of a 64x64 costume skin (base + outer layer), transparent texels shown as a
// plain skin tone (what the wearer's own skin would show), scaled up. Usage: node skin_views.js <skin.png> <out.png> [slim]
'use strict';
const fs = require('fs');
const tools = require(process.env.SKIN_TOOLS || 'C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod/scratchpad/skin_tools.js');
const img = tools.decode(process.argv[2]);
const slim = process.argv[4] === 'slim';
const S = 12;
const SKIN = [201, 154, 120];
const faces = (ox, oy, w, h, d) => ({
	top: [ox + d, oy, w, d], bottom: [ox + d + w, oy, w, d],
	right: [ox, oy + d, d, h], front: [ox + d, oy + d, w, h], left: [ox + d + w, oy + d, d, h], back: [ox + d + w + d, oy + d, w, h],
});
const aw = slim ? 3 : 4;
const parts = {
	head: [faces(0, 0, 8, 8, 8), faces(32, 0, 8, 8, 8)],
	body: [faces(16, 16, 8, 12, 4), faces(16, 32, 8, 12, 4)],
	rarm: [faces(40, 16, aw, 12, 4), faces(40, 32, aw, 12, 4)],
	larm: [faces(32, 48, aw, 12, 4), faces(48, 48, aw, 12, 4)],
	rleg: [faces(0, 16, 4, 12, 4), faces(0, 32, 4, 12, 4)],
	lleg: [faces(16, 48, 4, 12, 4), faces(0, 48, 4, 12, 4)],
};
const VW = 4 * 22, VH = 34;
const out = { w: VW * S, h: VH * S, px: Buffer.alloc(VW * S * VH * S * 4) };
for (let i = 0; i < out.px.length; i += 4) { out.px[i] = 40; out.px[i + 1] = 44; out.px[i + 2] = 52; out.px[i + 3] = 255; }
function get(x, y) { const i = (y * 64 + x) * 4; return [img.px[i], img.px[i + 1], img.px[i + 2], img.px[i + 3]]; }
function blit(part, face, dx, dy, flip) {
	const [b, o] = parts[part];
	const [bx, by, w, h] = b[face], [ox, oy] = o[face];
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		const sx = flip ? w - 1 - x : x;
		let c = SKIN.slice();
		const base = get(bx + sx, by + y);
		if (base[3] > 0) c = base.slice(0, 3);
		const top = get(ox + sx, oy + y);
		if (top[3] > 0) c = top.slice(0, 3).map((v, k) => v * 1.04);
		for (let yy = 0; yy < S; yy++) for (let xx = 0; xx < S; xx++) {
			const X = (dx + x) * S + xx, Y = (dy + y) * S + yy, d = (Y * out.w + X) * 4;
			for (let k = 0; k < 3; k++) out.px[d + k] = Math.min(255, Math.round(c[k]));
		}
	}
}
// front
let X = 1;
blit('head', 'front', X + 4, 1); blit('body', 'front', X + 4, 9); blit('rarm', 'front', X + 4 - aw, 9); blit('larm', 'front', X + 12, 9);
blit('rleg', 'front', X + 4, 21); blit('lleg', 'front', X + 8, 21);
// back
X = 23;
blit('head', 'back', X + 4, 1); blit('body', 'back', X + 4, 9); blit('larm', 'back', X + 4 - aw, 9); blit('rarm', 'back', X + 12, 9);
blit('lleg', 'back', X + 4, 21); blit('rleg', 'back', X + 8, 21);
// right side (his right)
X = 45;
blit('head', 'right', X + 4, 1); blit('rarm', 'right', X + 6, 9); blit('rleg', 'right', X + 6, 21);
// left side
X = 67;
blit('head', 'left', X + 4, 1); blit('larm', 'left', X + 6, 9); blit('lleg', 'left', X + 6, 21);
fs.writeFileSync(process.argv[3], tools.encode(out.w, out.h, out.px));
