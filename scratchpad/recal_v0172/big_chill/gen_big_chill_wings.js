// v0.17.2 (user: "make big chills flying animations look better please, make them look exactly how they look like in the reference
// images I sent and also try to match the colours to the colours of his cloaked skin model version please"): Big Chill's four moth
// wings, textures/entity/omnitrix/big_chill_wings.png (128x128). Every colour is a pixel of big_chill_cloaked.png:
//   0e101b  the black outline and the darkest shading at the root
//   131725 / 152b5a  the dark navy along his body and the leathery ribs
//   183666 / 1b4375  the steel-blue field
//   1a5384 / 1c4e7e  the lighter blue sheen across the middle
//   3baecb / 40bed3 / b1d6dd  the light-cyan eye-spots (teardrops, outlined black)
// Cells (64x64 each):
//   UPPER BACK 0,0 / UPPER FRONT 64,0   the big upper wing (back face = the cloak's outside, front face = seen from the front)
//   LOWER BACK 0,64 / LOWER FRONT 64,64 the smaller lower wing
//     cell x = along the span (0 = the root at his spine, 63 = the sharp tip), y = down the chord (0 = the leading edge,
//     63 = the trailing edge). The leading edge sweeps out to a sharp point at the tip; the trailing edge is cut into bat-like
//     scallops between the ribs, all by alpha.
// Usage: node scratchpad/recal_v0172/big_chill/gen_big_chill_wings.js
'use strict';
const path = require('path');
const png = require('../../pnglib.js');

const W = 128, H = 128;
const px = Buffer.alloc(W * H * 4);
const hex = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const mix = (a, b, t) => a.map((c, i) => Math.round(c + (b[i] - c) * Math.max(0, Math.min(1, t))));
const set = (x, y, c) => {
	const i = (y * W + x) * 4;
	px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255;
};
let seed = 424242;
const rnd = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);

const BLACK = hex('#0e101b'), DARK = hex('#131725'), NAVY = hex('#152b5a');
const STEEL = hex('#183666'), BLUE = hex('#1b4375'), SHEEN = hex('#1a5384'), SHEEN2 = hex('#1c4e7e');
const CYAN = hex('#3baecb'), CYAN_HI = hex('#40bed3'), ICE = hex('#b1d6dd');

/** Distance from (x,y) to the segment a-b, and how far along it. */
function seg(x, y, ax, ay, bx, by) {
	const dx = bx - ax, dy = by - ay;
	const t = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)));
	return [Math.hypot(x - (ax + dx * t), y - (ay + dy * t)), t];
}

/**
 * The wing outline: the leading edge (top) and the trailing edge (bottom) at span fraction f, before the scallops.
 * Upper: a long crescent -- the leading edge bowed a little, the trailing edge sweeping up to meet it in a sharp point.
 * Lower: shorter and fuller, ending in a point too.
 */
function lead(f, upper) {
	return upper ? 7 * Math.sin(Math.PI * Math.min(1, f * 1.05)) * (1 - f) + 1 : 5 * Math.sin(Math.PI * f) * (1 - f) + 1;
}
function trail(f, upper) {
	return upper ? 63 - 61 * Math.pow(f, 2.3) : 63 - 61 * Math.pow(f, 2.0);
}

/** The ribs fan from the root to the cusps between the scallops; returns {cusps[], ribs[]}. */
function ribs(upper) {
	const n = upper ? 5 : 4;
	const cusps = [], r = [];
	for (let i = 0; i <= n; i++) {
		const f = upper ? 0.12 + i * 0.86 / n : 0.1 + i * 0.86 / n;
		cusps.push(f * 63);
	}
	for (let i = 1; i < cusps.length; i++) {
		const cx = cusps[i];
		r.push([0, upper ? 8 : 6, cx, trail(cx / 63, upper)]);
	}
	return { cusps, ribs: r };
}

/** How deep the trailing edge is bitten at x: a round scallop between each pair of cusps. */
function bite(x, cusps, depth) {
	for (let i = 0; i + 1 < cusps.length; i++) {
		const a = cusps[i], b = cusps[i + 1];
		if (x >= a && x <= b) {
			const k = ((x - a) / (b - a)) * 2 - 1;
			return depth * (1 - Math.abs(i / cusps.length - 0.5) * 0.6) * Math.sqrt(Math.max(0, 1 - k * k));
		}
	}
	return 0;
}

function wing(ox, oy, upper, front) {
	const { cusps, ribs: rs } = ribs(upper);
	const mask = [];
	for (let y = 0; y < 64; y++) {
		mask.push([]);
		for (let x = 0; x < 64; x++) {
			const f = x / 63;
			const t0 = lead(f, upper);
			// the bites scale with the wing's depth there, so the narrow tip stays whole
			const t1 = trail(f, upper) - bite(x, cusps, Math.min(upper ? 10 : 8, (trail(f, upper) - t0) * 0.28));
			mask[y].push(y >= t0 && y <= t1 && t1 - t0 > 1.5);
		}
	}
	// no stray specks: a pixel needs two filled neighbours to stay
	for (let pass = 0; pass < 2; pass++) {
		for (let y = 0; y < 64; y++) {
			for (let x = 0; x < 64; x++) {
				if (!mask[y][x]) continue;
				let n = 0;
				for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
					const xx = x + dx, yy = y + dy;
					if (xx >= 0 && yy >= 0 && xx < 64 && yy < 64 && mask[yy][xx]) n++;
				}
				if (n < 2) mask[y][x] = false;
			}
		}
	}
	const inside = (x, y) => x >= 0 && y >= 0 && x < 64 && y < 64 && mask[y][x];
	// eye-spots: teardrops, round end toward the body, pointed toward the tip
	const spots = upper ? [[36, 18, 3.4, 5.0], [48, 11, 2.6, 3.8], [24, 30, 3.0, 4.6]] : [[30, 18, 3.0, 4.6], [43, 12, 2.3, 3.4]];
	for (let y = 0; y < 64; y++) {
		for (let x = 0; x < 64; x++) {
			if (!mask[y][x]) continue;
			const f = x / 63;
			// the black outline: ~0.5 px on the model (a span texel is ~0.45 px, a chord texel ~0.25 px)
			const edge = !inside(x - 1, y) && x > 0 || !inside(x + 1, y) || !inside(x, y - 1) || !inside(x, y + 1) || !inside(x, y - 2) || !inside(x, y + 2);
			if (edge) {
				set(ox + x, oy + y, BLACK);
				continue;
			}
			// the field: dark navy along his body, steel blue out to a lighter blue sheen, a little darker toward the tip again
			const tf = trail(f, upper), lf = lead(f, upper);
			const v = (y - lf) / Math.max(1, tf - lf); // 0 leading .. 1 trailing
			let c;
			if (f < 0.12) c = mix(BLACK, DARK, f / 0.12 * 0.9 + v * 0.1);
			else if (f < 0.3) c = mix(DARK, NAVY, (f - 0.12) / 0.18);
			else c = mix(NAVY, STEEL, (f - 0.3) / 0.25);
			if (f > 0.55) c = mix(c, BLUE, (f - 0.55) / 0.3);
			// the sheen: a diagonal band across the middle of the membrane (brighter on the back, the cloak's outside)
			const sheen = Math.exp(-Math.pow((f * 63 - 30 - v * 14) / 9, 2)) * (0.55 + (1 - v) * 0.3);
			c = mix(c, front ? SHEEN : SHEEN2, sheen * (front ? 0.55 : 0.75) * Math.min(1, f * 3));
			// the trailing edge darkens into the scallops
			if (v > 0.8) c = mix(c, NAVY, (v - 0.8) * 2.5);
			// the leathery ribs from the root to each cusp
			let rd = 99;
			for (const r of rs) rd = Math.min(rd, seg(x, y, r[0], r[1], r[2], r[3])[0] * (1 + 0.4 * 0));
			if (rd < 0.9) c = mix(c, front ? DARK : NAVY, 0.85);
			else if (rd < 1.7) c = mix(c, front ? DARK : NAVY, 0.3);
			// grain
			c = mix(c, DARK, rnd() * 0.1);
			// the eye-spots, outlined black (both faces: the wing membrane is the same pattern through)
			for (const s of spots) {
				const dx = (x - s[0]) / s[2], dy = (y - s[1]) / s[3];
				// teardrop: narrows toward the tip (+x)
				const tw = dx > 0 ? 1 + dx * 0.9 : 1;
				const d = Math.hypot(dx * tw, dy);
				if (d < 1.0) {
					c = d < 0.38 ? mix(ICE, CYAN_HI, d / 0.38) : d < 0.72 ? CYAN_HI : CYAN;
					if (!front) c = mix(c, NAVY, 0.25);
				} else if (d < 1.38) {
					c = BLACK;
				}
			}
			set(ox + x, oy + y, c);
		}
	}
}
wing(0, 0, true, false);
wing(64, 0, true, true);
wing(0, 64, false, false);
wing(64, 64, false, true);

const out = path.join(__dirname, '../../../src/main/resources/assets/projecthero/textures/entity/omnitrix/big_chill_wings.png');
png.write(out, W, H, px);
console.log('wrote', out);
