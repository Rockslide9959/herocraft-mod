// v0.17.2 (user: "give him his wings please, make the cloak unfurl from around his body to make his wings since his wings are his
// cloak"): Big Chill's four moth wings, textures/entity/omnitrix/big_chill_wings.png (128x128), colours sampled from his skins.
//   UPPER OUTER 0,0   64x64  the big upper wing's outside -- the cloak's blue (big_chill_cloaked.png 1b4375 / 1a5384 / 152b5a),
//                            lighter veins, a cyan rim (3baecb)
//   UPPER INNER 64,0  64x64  its inside -- the open skin's navy (131725 / 0e101b) with blue veins (2c3ea4 / 314baf), cyan rim
//   LOWER OUTER 0,64 / LOWER INNER 64,64  the smaller lower wing, the same two faces
//     cell x = along the span (0 = the root at his spine, 63 = the tip), y = down the chord (0 = the leading edge, 63 = the
//     trailing edge): the trailing edge is scalloped (ragged, like the cloak's hem), the tip rounded, all by alpha
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
let seed = 98765;
const rnd = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);

const OUT = [hex('#152b5a'), hex('#183666'), hex('#1b4375'), hex('#1a5384'), hex('#1c4e7e')];
const OUT_VEIN = hex('#2a64a4');
const IN = [hex('#0e101b'), hex('#131725'), hex('#1e2637'), hex('#1c254c')];
const IN_VEIN = hex('#2c3ea4'), IN_VEIN2 = hex('#314baf');
const CYAN = hex('#3baecb'), CYAN_HI = hex('#40bed3');

/** Distance from (x,y) to the segment a-b. */
function segDist(x, y, ax, ay, bx, by) {
	const dx = bx - ax, dy = by - ay;
	const t = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)));
	return Math.hypot(x - (ax + dx * t), y - (ay + dy * t));
}

/** Scalloped bites along the trailing edge: 0 at the cusps, `depth` mid-bite. */
function scallop(x, period, depth) {
	const k = (x % period) / period * 2 - 1;
	return depth * Math.sqrt(Math.max(0, 1 - k * k));
}

function wing(ox, oy, upper, outer) {
	const veins = upper
		? [[0, 4, 63, 8], [0, 6, 60, 30], [0, 8, 50, 48], [0, 10, 36, 56], [0, 12, 20, 60], [30, 6, 63, 22]]
		: [[0, 4, 60, 18], [0, 6, 50, 36], [0, 8, 34, 48], [0, 10, 16, 54]];
	for (let y = 0; y < 64; y++) {
		for (let x = 0; x < 64; x++) {
			const f = x / 63;
			// the outline
			let yMax = upper ? 63 - 20 * Math.pow(f, 1.6) - scallop(x, 10.5, 4.5) : 63 - 14 * Math.pow(f, 1.3) - scallop(x, 9, 4);
			let yMin = 0;
			if (upper) {
				// the broad rounded tip
				if (x > 50 && y < 12 && Math.hypot(x - 50, 12 - y) > 13.5) continue;
				if (x > 48 && y > yMax - 12 && Math.hypot(x - 48, y - (yMax - 12)) > 15.5) continue;
			} else {
				if (x > 40 && Math.pow((x - 40) / 23.5, 2) + Math.pow((y - 26) / 30, 2) > 1) continue;
			}
			if (y > yMax || y < yMin) continue;
			const edge = Math.min(yMax - y, y - yMin + 99, 63 - x + 99);
			// base: darker at the root, a soft gradient out along the span and down the chord
			let c;
			if (outer) {
				const t = 0.25 + f * 0.55 + Math.sin(y / 63 * Math.PI) * 0.15;
				c = t < 0.33 ? mix(OUT[0], OUT[1], t / 0.33) : t < 0.66 ? mix(OUT[1], OUT[2], (t - 0.33) / 0.33) : mix(OUT[2], OUT[3], (t - 0.66) / 0.34);
			} else {
				const t = f * 0.7 + (y / 63) * 0.2;
				c = t < 0.5 ? mix(IN[0], IN[1], t / 0.5) : mix(IN[1], IN[2], (t - 0.5) / 0.5);
			}
			// the veins fanning from the root
			let vd = 99;
			for (const v of veins) vd = Math.min(vd, segDist(x, y, v[0], v[1], v[2], v[3]));
			if (vd < 1.1) c = outer ? mix(c, OUT_VEIN, 0.75) : mix(c, vd < 0.6 ? IN_VEIN2 : IN_VEIN, 0.85);
			else if (vd < 1.9) c = outer ? mix(c, OUT_VEIN, 0.25) : mix(c, IN_VEIN, 0.3);
			c = mix(c, outer ? OUT[0] : IN[0], rnd() * 0.12);
			// the rims: the leading edge and the ragged hem picked out in cyan (the inside brighter, like his open skin)
			if (y <= 1 && x > 3) c = mix(c, CYAN, outer ? 0.55 : 0.85);
			if (edge < 1.6) c = mix(c, outer ? CYAN : CYAN_HI, outer ? 0.6 : 0.9);
			else if (edge < 3) c = mix(c, IN[0], outer ? 0.25 : 0.4);
			set(ox + x, oy + y, c);
		}
	}
}
wing(0, 0, true, true);
wing(64, 0, true, false);
wing(0, 64, false, true);
wing(64, 64, false, false);

const out = path.join(__dirname, '../../../src/main/resources/assets/projecthero/textures/entity/omnitrix/big_chill_wings.png');
png.write(out, W, H, px);
console.log('wrote', out);
