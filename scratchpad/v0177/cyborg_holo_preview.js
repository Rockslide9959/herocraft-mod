// v0.17.7: offline frames of Cyborg's hologram sweep -- both layers of the plates cut at the hologram's line (the same per-texel
// order as CyborgRender.holoOrder, in base-layer coordinates; an overlay texel follows the base texel under it), over a plain
// wearer skin. Reached texels show the wearer (the hologram is the wearer's own skin).
//
//   node scratchpad/v0177/cyborg_holo_preview.js
const path = require('path');
const PNG = require('../pnglib.js');
const preview = require('./skin_preview.js');

const ROOT = path.resolve(__dirname, '..', '..');
const plates = PNG.read(path.join(ROOT, 'src/main/resources/assets/projecthero/textures/entity/cyborg/cyborg.png'));
const WEARER = path.join(__dirname, 'cyborg_preview_wearer.png');

// OverlayStrips.heights, ported
const heights = new Float32Array(64 * 64).fill(-1);
for (const [u, v, w, hh, d, top] of [[0, 0, 8, 8, 8, 0], [32, 0, 8, 8, 8, 0], [16, 16, 8, 12, 4, 8], [16, 32, 8, 12, 4, 8], [40, 16, 4, 12, 4, 8],
	[40, 32, 4, 12, 4, 8], [32, 48, 4, 12, 4, 8], [48, 48, 4, 12, 4, 8], [0, 16, 4, 12, 4, 20], [0, 32, 4, 12, 4, 20], [16, 48, 4, 12, 4, 20],
	[0, 48, 4, 12, 4, 20]]) {
	const bottom = top === 0 ? hh - 0.1 : top + hh;
	for (let y = v; y < v + d; y++) {
		for (let x = u + d; x < u + d + w; x++) heights[y * 64 + x] = top;
		for (let x = u + d + w; x < u + d + 2 * w; x++) heights[y * 64 + x] = bottom;
	}
	for (let y = v + d; y < v + d + hh; y++) for (let x = u; x < u + 2 * d + 2 * w; x++) heights[y * 64 + x] = top + (y - v - d) + 0.5;
}
const holoOrder = (u, v) => {
	const h = heights[v * 64 + u];
	if (h < 0) return 2;
	let k = Math.imul(u, 73856093) ^ Math.imul(v, 19349663);
	k = Math.imul(k ^ (k >>> 13), 1274126177);
	const jitter = ((k >>> 8) & 0xFFFF) / 65536;
	return Math.min(1, Math.max(0, (Math.min(h, 31.99) + jitter * 0.98) / 32));
};
// base texel under each overlay texel (OverlayStrips.overlayIndex, inverted)
const baseOf = (x, y) => {
	if (y < 16) return x >= 32 ? [x - 32, y] : [x, y];
	if (y >= 32 && y < 48) return [x, y - 16];
	if (y >= 48) return x >= 48 ? [x - 16, y] : x < 16 ? [x + 16, y] : [x, y];
	return [x, y];
};
const frames = [0, 0.2, 0.4, 0.6, 0.8, 1].map(line => {
	const px = Buffer.from(plates.px);
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const [bu, bv] = baseOf(x, y);
		if (line > 0 && holoOrder(bu, bv) <= line) px.fill(0, (y * 64 + x) * 4, (y * 64 + x) * 4 + 4);
	}
	return { W: 64, H: 64, px };
});
const out = path.join(ROOT, 'docs/screenshots/v0177/cyborg_skin/hologram_sweep_both_layers.png');
preview.render(frames, out, { underlay: WEARER });
console.log('wrote ' + path.relative(ROOT, out) + ' (rows: line 0, 0.2, 0.4, 0.6, 0.8, 1)');
