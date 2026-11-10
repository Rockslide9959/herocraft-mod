// v0.17.3 Omnitrix: Snare-oh, Eye Guy, Frankenstrike textures (user: "make aliens based on their abilities and stuff they do in the
// show please, replace the pixel omnitrix on their skin with our omnitrix badge please"). Sources: docs/reference/omnitrix_new_aliens.
//   textures/entity/omnitrix/<id>.png        the skin, its painted pixel Omnitrix painted out (the mod's badge sits there)
//   textures/entity/omnitrix/<id>_glow.png   the emissive pass (lime eyes / stripes / bolt glows; Eye Guy's irises white for tinting)
//   textures/entity/omnitrix/snare_oh_bandage.png  one bandage strip (Snare-oh's ropes and wraps)
// and the dial icon sources (320x320, the placeholder style the transplant reads) in scratchpad/omnitrix_new/dial_src/.
// Then: node scratchpad/gen_omnitrix_icon_transplant.js snare_oh=... eye_guy=... frankenstrike=...
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const { dec } = require('../ben10_v0171/png_view.js');

const ROOT = path.join(__dirname, '..', '..');
const REF = path.join(ROOT, 'docs', 'reference', 'omnitrix_new_aliens');
const OUT = path.join(ROOT, 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'entity', 'omnitrix');
const at = (x, y) => (y * 64 + x) * 4;
const put = (b, x, y, hex, a = 255) => {
	const i = at(x, y);
	b[i] = (hex >> 16) & 255;
	b[i + 1] = (hex >> 8) & 255;
	b[i + 2] = hex & 255;
	b[i + 3] = a;
};
const clear = (b, x, y) => put(b, x, y, 0, 0);
const lime = (b, i) => b[i + 3] > 10 && b[i + 1] > 150 && b[i + 1] - b[i] > 40 && b[i + 1] - b[i + 2] > 60;

function glow(px, white, keep) {
	const g = Buffer.alloc(64 * 64 * 4);
	let n = 0;
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const i = at(x, y);
		if (!lime(px, i) || (keep && !keep(x, y))) continue;
		if (white) {
			const m = Math.max(px[i], px[i + 1], px[i + 2]);
			g[i] = g[i + 1] = g[i + 2] = m;
		} else {
			g[i] = px[i];
			g[i + 1] = px[i + 1];
			g[i + 2] = px[i + 2];
		}
		g[i + 3] = 255;
		n++;
	}
	return { g, n };
}

// ---------------------------------------------------------------- Snare-oh
{
	const px = Buffer.from(dec(path.join(REF, 'snare_oh.png')).px);
	// the chest symbol: lime bars at x22/x25 and two pale diagonals, rows 22..26 -> the bandage: the two glowing bands carry on
	// across his chest (rows 23 and 25), the wraps between them take the panel's own tans
	for (let x = 22; x <= 25; x++) {
		put(px, x, 22, 0x9f9e77);
		put(px, x, 23, 0x71d32a);
		put(px, x, 24, 0x87865e);
		put(px, x, 25, 0x71d32a);
		put(px, x, 26, x === 22 || x === 25 ? 0x87865e : 0x9f9e77);
	}
	fs.writeFileSync(path.join(OUT, 'snare_oh.png'), L.encodePng(64, 64, px));
	const { g, n } = glow(px, false);
	fs.writeFileSync(path.join(OUT, 'snare_oh_glow.png'), L.encodePng(64, 64, g));
	console.log('snare_oh glow texels', n);
	// one bandage strip, 16 x 32: tan wraps laid on the diagonal with darker folds and a pale edge, like his skin's
	const S = Buffer.alloc(16 * 32 * 4);
	for (let y = 0; y < 32; y++) for (let x = 0; x < 16; x++) {
		const i = (y * 16 + x) * 4;
		const d = (x + y * 2) % 8;
		let c = d < 1 ? 0x63624b : d < 3 ? 0x87865e : d < 6 ? 0x9f9e77 : 0xb4b28a;
		if (x === 0 || x === 15) c = 0x6d6c52;
		S[i] = (c >> 16) & 255;
		S[i + 1] = (c >> 8) & 255;
		S[i + 2] = c & 255;
		S[i + 3] = 255;
	}
	fs.writeFileSync(path.join(OUT, 'snare_oh_bandage.png'), L.encodePng(16, 32, S));
}

// ---------------------------------------------------------------- Eye Guy
{
	const px = Buffer.from(dec(path.join(REF, 'eye_guy.png')).px);
	// the belt buckle's pixel Omnitrix (jacket 23..24 x 45..46) goes clear: the body layer's dark buckle shows, the badge sits on it
	for (const [x, y] of [[23, 45], [24, 45], [23, 46], [24, 46]]) clear(px, x, y);
	fs.writeFileSync(path.join(OUT, 'eye_guy.png'), L.encodePng(64, 64, px));
	// every iris (lime) as white, so the renderer tints them with the beam colour
	const { g, n } = glow(px, true);
	fs.writeFileSync(path.join(OUT, 'eye_guy_glow.png'), L.encodePng(64, 64, g));
	console.log('eye_guy glow texels', n);
}

// ---------------------------------------------------------------- Frankenstrike
{
	const px = Buffer.from(dec(path.join(REF, 'frankenstrike.png')).px);
	// the chest's pixel Omnitrix (a lime / dark checker on the jacket, 23..24 x 38..39) goes clear: his grey-green chest shows
	for (const [x, y] of [[23, 38], [24, 38], [23, 39], [24, 39]]) clear(px, x, y);
	fs.writeFileSync(path.join(OUT, 'frankenstrike.png'), L.encodePng(64, 64, px));
	const { g, n } = glow(px, false);
	fs.writeFileSync(path.join(OUT, 'frankenstrike_glow.png'), L.encodePng(64, 64, g));
	console.log('frankenstrike glow texels', n);
}

// ---------------------------------------------------------------- dial icon sources (the feedback placeholder's style)
const W = 320, H = 320, SS = 3;
const cx = 160, cy = 160, hw = 100, hh = 150;
const inDiamond = (x, y, k = 0) => Math.abs(x - cx) / (hw - k) + Math.abs(y - cy) / (hh - k * 1.5) <= 1;
const inDisc = (x, y) => (x - cx) ** 2 + (y - cy) ** 2 <= 155 * 155;
const ell = (x, y, ex, ey, rx, ry) => ((x - ex) / rx) ** 2 + ((y - ey) / ry) ** 2 <= 1;
const rect = (x, y, x0, y0, x1, y1) => x >= x0 && x <= x1 && y >= y0 && y <= y1;
function nearCurve(x, y, p0, p1, p2, w) {
	for (let i = 0; i <= 60; i++) {
		const t = i / 60, a = (1 - t) * (1 - t), b = 2 * (1 - t) * t, c = t * t;
		const qx = a * p0[0] + b * p1[0] + c * p2[0], qy = a * p0[1] + b * p1[1] + c * p2[1];
		if ((x - qx) ** 2 + (y - qy) ** 2 <= w * w) return true;
	}
	return false;
}
const torso = (x, y, top, w0, slope) => y > top && y < 262 && Math.abs(x - 160) < w0 + (y - top) * slope;

// full-body figures, standing, like the user's icon sheet (Heatblast, Four Arms...)
/** Distance from (x, y) to the segment a-b. */
function seg(x, y, a, b) {
	const dx = b[0] - a[0], dy = b[1] - a[1];
	const t = Math.max(0, Math.min(1, ((x - a[0]) * dx + (y - a[1]) * dy) / (dx * dx + dy * dy)));
	return Math.hypot(x - (a[0] + t * dx), y - (a[1] + t * dy));
}
/** A tapered limb from a (radius ra) to b (radius rb). */
function limb(x, y, a, b, ra, rb) {
	const dx = b[0] - a[0], dy = b[1] - a[1];
	const t = Math.max(0, Math.min(1, ((x - a[0]) * dx + (y - a[1]) * dy) / (dx * dx + dy * dy)));
	return Math.hypot(x - (a[0] + t * dx), y - (a[1] + t * dy)) <= ra + (rb - ra) * t;
}
/** Both sides of a limb mirrored about x = 160. */
const pair = (x, y, a, b, ra, rb) => limb(x, y, a, b, ra, rb) || limb(x, y, [320 - a[0], a[1]], [320 - b[0], b[1]], ra, rb);
const torsoT = (x, y, y0, w0, y1, w1) => y >= y0 && y <= y1 && Math.abs(x - 160) <= w0 + (w1 - w0) * (y - y0) / (y1 - y0);

const SIL = {
	// a mummy: wrapped all over (thin gaps across the limbs and body), two eye slits, a loose bandage whipping off his right hand
	snare_oh(x, y) {
		const head = ell(x, y, 160, 72, 19, 22);
		const body = torsoT(x, y, 92, 32, 170, 22) || pair(x, y, [130, 104], [112, 160], 11, 9) || pair(x, y, [112, 160], [106, 206], 9, 8)
			|| pair(x, y, [146, 168], [140, 220], 12, 10) || pair(x, y, [140, 220], [138, 268], 10, 9);
		if (!head && !body && !nearCurve(x, y, [106, 206], [88, 190], [86, 150], 4.5)) return false;
		if (head && (ell(x, y, 152, 70, 5, 2.6) || ell(x, y, 168, 70, 5, 2.6))) return false; // the eyes
		// the wraps: diagonal gaps
		if ((head || body) && Math.abs(((y + (x - 160) * 0.35) % 24 + 24) % 24 - 12) < 1.6 && !(head && y < 60)) return false;
		return true;
	},
	// slim, two swept-back ears, eyes open all over him (lime holes), the big eye on his chest
	eye_guy(x, y) {
		const head = ell(x, y, 160, 74, 15, 19);
		const ears = nearCurve(x, y, [150, 62], [140, 52], [138, 40], 4.5) || nearCurve(x, y, [170, 62], [180, 52], [182, 40], 4.5);
		const body = torsoT(x, y, 94, 27, 170, 17) || pair(x, y, [133, 104], [118, 158], 8, 7) || pair(x, y, [118, 158], [112, 204], 7, 6)
			|| pair(x, y, [148, 168], [143, 222], 9, 8) || pair(x, y, [143, 222], [141, 268], 8, 7);
		if (!head && !ears && !body) return false;
		if (head && (ell(x, y, 154, 72, 3.5, 3) || ell(x, y, 166, 72, 3.5, 3))) return false;
		if (body) {
			if (ell(x, y, 160, 128, 13, 10)) return ell(x, y, 160, 128, 4.5, 5); // the chest eye
			for (const [ex, ey] of [[121, 140], [199, 140], [114, 186], [206, 186], [144, 202], [176, 202], [142, 244], [178, 244], [146, 104], [174, 104]]) {
				if (ell(x, y, ex, ey, 3.6, 2.8)) return false;
			}
		}
		return true;
	},
	// hulking: a big flat-topped head, a heavy brow, huge shoulders and fists, the electrode bolts up off his shoulders, a spark between
	frankenstrike(x, y) {
		const head = rect(x, y, 144, 58, 176, 96);
		const body = torsoT(x, y, 92, 40, 172, 26) || pair(x, y, [124, 104], [104, 164], 14, 12) || pair(x, y, [104, 164], [100, 206], 12, 14)
			|| pair(x, y, [146, 170], [138, 222], 13, 11) || pair(x, y, [138, 222], [136, 268], 11, 12);
		const bolts = rect(x, y, 127, 80, 135, 106) || rect(x, y, 185, 80, 193, 106) || rect(x, y, 124, 74, 138, 81) || rect(x, y, 182, 74, 196, 81);
		const zx = [131, 140, 150, 160, 170, 180, 189], zy = [72, 60, 68, 56, 66, 58, 72];
		let spark = false;
		for (let i = 0; i < zx.length - 1; i++) if (seg(x, y, [zx[i], zy[i]], [zx[i + 1], zy[i + 1]]) < 2.6) spark = true;
		if (!head && !body && !bolts && !spark) return false;
		if (head && (ell(x, y, 153, 78, 4, 2.6) || ell(x, y, 167, 78, 4, 2.6))) return false;
		if (head && rect(x, y, 152, 87, 168, 89)) return false;
		return true;
	},
};

const SRC = path.join(__dirname, 'dial_src');
fs.mkdirSync(SRC, { recursive: true });
for (const id of Object.keys(SIL)) {
	const px = Buffer.alloc(W * H * 4);
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		let acc = [0, 0, 0];
		for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
			const X = x + (sx + 0.5) / SS, Y = y + (sy + 0.5) / SS;
			let c = [255, 255, 255];
			if (inDisc(X, Y)) c = [96, 96, 96];
			if (inDiamond(X, Y)) c = [20, 20, 20];
			if (inDiamond(X, Y, 12)) c = SIL[id](160 + (X - 160) / 0.8, 166 + (Y - 166) / 0.8) ? [0, 0, 0] : [170, 240, 60];
			acc[0] += c[0]; acc[1] += c[1]; acc[2] += c[2];
		}
		const i = (y * W + x) * 4, n = SS * SS;
		px[i] = acc[0] / n; px[i + 1] = acc[1] / n; px[i + 2] = acc[2] / n; px[i + 3] = 255;
	}
	fs.writeFileSync(path.join(SRC, id + '.png'), L.encodePng(W, H, px));
	console.log('dial source', id);
}
