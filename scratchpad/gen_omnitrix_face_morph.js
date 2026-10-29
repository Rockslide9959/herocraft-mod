// v0.17.1 (user: "the omnitrix transitions to the alien selection screen ... like how it is in the show"): the face's morph from
// the hourglass to the dial's diamond, as 16 frames of 64x64 (a 4x4 sheet, row-major), for OmnitrixWatch.
//   * frame 0  = the resting face (the same hourglass, X dividers and grey wedges as watch.png's face)
//   * m 0..0.6   the black dividers swing: the hourglass edge half-width f0(ay) = w + ay / k blends toward the diamond's
//                f1(ay) = D (1 - ay / H), so the X lines go near-parallel (m ~0.3) and then meet as a diamond outline;
//                the green retreats from the waist out to the tips as they swing (the triangles pinch away)
//   * m 0.55..1  a small green diamond appears in the middle of the outline and grows to fill it, brightening
//   * frame 15 = the user's dial icon without the silhouette (grey disc, black rim, the lit lime diamond), so the alien's
//                icon fades in on top of it without a jump
//   entity/omnitrix/face_morph.png, face_morph_red.png (the lime swapped for the watch's red)
// Usage: node scratchpad/gen_omnitrix_face_morph.js [previewDir]
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('./pnglib.js');
const OUT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'entity', 'omnitrix');
const PREVIEW = process.argv[2];
const hex = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
const mixc = (a, b, t) => a.map((v, i) => v + (b[i] - v) * Math.max(0, Math.min(1, t)));
const clamp = (x) => Math.max(0, Math.min(1, x));
const smooth = (x) => { x = clamp(x); return x * x * (3 - 2 * x); };
const lerp = (a, b, t) => a + (b - a) * t;

const GREEN = { base: hex('#8cf03a'), hi: hex('#ccff80'), lo: hex('#62c424') };
const LIME = hex('#a9f027'); // the dial icon's lit lime
const RED = { base: hex('#ff3d2e'), hi: hex('#ffb0a0'), lo: hex('#c41e14') };
const RED_LIT = hex('#f02a27');
const RIM = hex('#141517'), LINE = hex('#16181a');
const N = 16, S = 64, SS = 4;

function sample(dx, dy, m, red) {
	const G = red ? RED : GREEN;
	const lit = red ? RED_LIT : LIME;
	const r = Math.hypot(dx, dy);
	if (r > 31.6) return null;
	if (r > 29.6) return RIM;
	const ax = Math.abs(dx), ay = Math.abs(dy);
	const shape = smooth(m / 0.6);
	const w = 3.2, k = 1.12, D = 20.5, H = 29.5;
	const f0 = w + ay / k, f1 = D * (1 - ay / H);
	const f = lerp(f0, f1, shape);
	const bw = lerp(4.6, 3.4, shape);
	// the grey: the face's light wedge grey darkening to the dial icon's flat grey
	const t = r / 30;
	let grey = mixc(hex('#a3a9ae'), hex('#6c7277'), Math.max(0, t - 0.35) * 1.3);
	if (dy < -4 && r < 26 && r > 16) grey = mixc(grey, hex('#c6cbcf'), 0.5 * (1 - shape));
	grey = mixc(grey, hex('#545454'), smooth((m - 0.2) / 0.6));
	if (ax <= f) {
		// inside the dividers. the hourglass green retreats from the waist to the tips while they swing
		const rin = lerp(-2, 31, smooth((m - 0.06) / 0.4));
		let c = grey;
		if (ay > rin && shape < 0.999) {
			c = mixc(G.base, G.lo, Math.max(0, t - 0.55) * 1.2);
			if (dx + dy * 0.6 < -10 && ay > 8 && ax < f - 2.2 && r < 27) c = mixc(c, G.hi, 0.55 * (1 - m));
		}
		// the new diamond: grows from the middle, lit
		const s = smooth((m - 0.55) / 0.42);
		if (s > 0.001) {
			const sf = (D - 0.4) * s * (1 - ay / ((H - 0.4) * s));
			if (ax <= sf) {
				const edge = sf - ax;
				const bright = lerp(0.62, 1, smooth((m - 0.7) / 0.3));
				const core = mixc(G.base, lit, smooth((m - 0.75) / 0.25)).map(v => v * bright);
				c = edge < 1.4 * (1 - s) + 0.01 && s < 0.98 ? LINE : core;
			}
		}
		return c;
	}
	if (ax <= f + bw) return LINE;
	return grey;
}

function frame(m, red) {
	const px = new Float32Array(S * S * 4);
	for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
		const acc = [0, 0, 0, 0];
		for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
			const c = sample(x + (sx + 0.5) / SS - 32, y + (sy + 0.5) / SS - 32, m, red);
			if (c) { acc[0] += c[0]; acc[1] += c[1]; acc[2] += c[2]; acc[3] += 1; }
		}
		const i = (y * S + x) * 4;
		if (acc[3] > 0) {
			px[i] = acc[0] / acc[3]; px[i + 1] = acc[1] / acc[3]; px[i + 2] = acc[2] / acc[3];
			px[i + 3] = acc[3] >= SS * SS / 2 ? 255 : 0; // cutout: a hard disc edge
		}
	}
	return px;
}

function sheet(red) {
	const W = S * 4;
	const out = Buffer.alloc(W * W * 4);
	for (let f = 0; f < N; f++) {
		const px = frame(f / (N - 1), red);
		const ox = (f % 4) * S, oy = Math.floor(f / 4) * S;
		for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
			const si = (y * S + x) * 4, di = ((oy + y) * W + ox + x) * 4;
			for (let c = 0; c < 4; c++) out[di + c] = Math.round(px[si + c]);
		}
	}
	return out;
}

const green = sheet(false), red = sheet(true);
PNG.write(path.join(OUT, 'face_morph.png'), 256, 256, green);
PNG.write(path.join(OUT, 'face_morph_red.png'), 256, 256, red);
if (PREVIEW) {
	// 3x, on a mid grey
	const s = 3, W = 256 * s;
	const p = Buffer.alloc(W * W * 4);
	for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
		const i = (Math.floor(y / s) * 256 + Math.floor(x / s)) * 4, o = (y * W + x) * 4;
		const a = green[i + 3] / 255;
		for (let c = 0; c < 3; c++) p[o + c] = Math.round(green[i + c] * a + 120 * (1 - a));
		p[o + 3] = 255;
	}
	PNG.write(path.join(PREVIEW, 'face_morph_preview.png'), W, W, p);
}
console.log('wrote face_morph(.png, _red.png)');
