// v0.17.2 Recalibrated Omnitrix: Chromastone (Crystalsapien) -- the basic body. A 64x64 WIDE skin (faceted magenta-violet
// crystal with dark seams, a pale crystal face with dark eyes, white glints) and an extras sheet whose rectangles
// ChromastoneClient maps onto AlienParts.box cubes:
//   SPIKE 0,0 8x8 (a faceted crystal) | SPIKE_D 8,0 8x8 (the darker one) | GLOW 16,0 8x8 (the charge glow) | SEAM 24,0 4x4
// Usage: node scratchpad/recal_v0172/chromastone/gen_chromastone.js [previewDir]
'use strict';
const L = require('../lib_recal_b.js');
const { img, hex, hash, shade, mixc, rect, save, paintBox, SKIN } = L;
const PREVIEW = process.argv[2];

const VIO = ['#2a0a3e', '#4a1868', '#7a2ea0', '#a24ad0', '#c87ae8', '#ecc8ff'].map(hex);
const SEAM = hex('#16061e');

// faceted crystal: diagonal facet bands of alternating light / dark violet, dark seams between, a white glint now and then
function facet(u, v, x, y, f, seed) {
	const k = Math.floor((x + y * 0.7) / 3) + Math.floor((x - y * 0.6) / 4);
	if (((x + 2 * y) % 7 === 0) && hash(x, y, seed) < 0.55) return SEAM;
	let t = 2 + (k % 2 === 0 ? 1 : 0);
	if (f === 'top') t += 1;
	if (f === 'bottom' || f === 'back') t -= 1;
	let c = shade(VIO[Math.max(0, Math.min(5, t))], 0.92 + hash(x, y, seed + 1) * 0.16);
	if (hash(x, y, seed + 2) < 0.04) c = VIO[5];
	return c;
}

const im = img(64, 64);
paintBox(im, SKIN.head, (f, u, v, w, h, x, y) => {
	if (f === 'front') {
		if (v >= 2 && v <= 3 && (u === 2 || u === 5)) return hex('#1a0a24'); // dark eyes
		if (v === 2 && (u === 1 || u === 6)) return VIO[3];
		if (v === 6 && u >= 3 && u <= 4) return VIO[1]; // the slit mouth
		return shade(mixc(VIO[5], VIO[4], v / 8), 0.95 + hash(u, v, 3) * 0.08); // the pale crystal face
	}
	return facet(u, v, x, y, f, 5);
});
paintBox(im, SKIN.body, (f, u, v, w, h, x, y) => {
	if (f === 'front' && u >= 2 && u <= 5 && v >= 6 && v <= 10) return shade(VIO[4], 0.95 + hash(u, v, 9) * 0.1); // the chest crystal
	return facet(u, v, x, y, f, 11);
});
for (const k of ['rArm', 'lArm', 'rLeg', 'lLeg']) paintBox(im, SKIN[k], (f, u, v, w, h, x, y) => {
	if (v % 6 === 5 && f !== 'top' && f !== 'bottom') return SEAM; // the joints
	return facet(u, v, x, y, f, 21 + k.length);
});
save(im, 'entity/omnitrix/chromastone.png', PREVIEW);

const ex = img(64, 64);
rect(ex, 0, 0, 8, 8, (u, v) => (u + v) % 5 === 0 ? VIO[5] : u < 4 ? VIO[4] : VIO[3]); // SPIKE: a lit and a shadowed facet
rect(ex, 8, 0, 8, 8, (u, v) => (u + v) % 5 === 0 ? VIO[3] : u < 4 ? VIO[2] : VIO[1]); // SPIKE_D
rect(ex, 16, 0, 8, 8, (u, v) => { const d = Math.hypot(u - 3.5, v - 3.5); return mixc(hex('#ffffff'), hex('#e070ff'), d / 5); }); // GLOW
rect(ex, 24, 0, 4, 4, () => SEAM); // SEAM
save(ex, 'entity/omnitrix/chromastone_extras.png', PREVIEW);
