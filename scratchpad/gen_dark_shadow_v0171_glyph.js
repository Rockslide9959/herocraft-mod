// v0.17.1 Dark Shadow revamp: the violet eye glyph Shadow Scout hangs over a marked creature
// (textures/entity/quirk/dark_shadow/eye_glyph.png, 32x32, drawn in the additive buffer: black = clear).
// Run: node scratchpad/gen_dark_shadow_v0171_glyph.js
const fs = require('fs');
const path = require('path');
const { encode } = require('./suits/skin_tools.js');

const W = 32, H = 32;
const px = Buffer.alloc(W * H * 4);
function add(x, y, r, g, b, a) {
	if (x < 0 || y < 0 || x >= W || y >= H) return;
	const i = (y * W + x) * 4;
	px[i] = Math.min(255, px[i] + r * a);
	px[i + 1] = Math.min(255, px[i + 1] + g * a);
	px[i + 2] = Math.min(255, px[i + 2] + b * a);
	px[i + 3] = Math.min(255, px[i + 3] + 255 * a);
}
for (let y = 0; y < H; y++) {
	for (let x = 0; x < W; x++) {
		const u = (x + 0.5 - W / 2) / (W / 2), v = (y + 0.5 - H / 2) / (H / 2);
		// the almond outline: two arcs meeting at sharp corners (a bird's eye)
		const top = 0.62 * (1 - u * u), bot = -0.62 * (1 - u * u);
		const inside = v < top && v > bot && Math.abs(u) < 1;
		const edge = Math.min(Math.abs(v - top), Math.abs(v - bot));
		const rim = Math.abs(u) < 1 ? Math.max(0, 1 - edge / 0.12) : 0;
		// the slit pupil and the iris ring
		const d = Math.hypot(u * 1.15, v);
		const iris = inside ? Math.max(0, 1 - Math.abs(d - 0.36) / 0.1) : 0;
		const pupil = inside && Math.abs(u) < 0.09 && Math.abs(v) < 0.42 ? 1 : 0;
		const fill = inside ? 0.28 * (1 - d) : 0;
		// a soft outer halo
		const halo = Math.max(0, 1 - Math.hypot(u * 0.8, v * 1.4) / 1.05) * 0.35;
		const a = Math.min(1, rim * 0.95 + iris * 0.9 + fill + halo);
		if (a <= 0) continue;
		const hot = Math.min(1, rim + iris);
		const r = 120 + 135 * hot, g = 60 + 150 * hot * hot, b = 255;
		add(x, y, pupil ? 40 : r, pupil ? 10 : g, pupil ? 60 : b, pupil ? 0.9 : a);
	}
}
// premultiply for the additive buffer
for (let i = 0; i < px.length; i += 4) {
	const a = px[i + 3] / 255;
	px[i] = Math.round(px[i] * a);
	px[i + 1] = Math.round(px[i + 1] * a);
	px[i + 2] = Math.round(px[i + 2] * a);
}
const out = path.join(__dirname, '..', 'src/main/resources/assets/projecthero/textures/entity/quirk/dark_shadow/eye_glyph.png');
fs.writeFileSync(out, encode(W, H, px));
console.log('wrote', out);
