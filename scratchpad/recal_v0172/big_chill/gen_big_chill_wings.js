// v0.17.2 (user: "some of big chills wings are in his legs also the antenna should come from the back of his wings not his head ...
// also the top and bottom wings are supposed to be connected since its 1 wing similar to stinkfly, please make it look better
// according to my notes and the reference pics i sent earlier please"): Big Chill's wing, ONE per side -- a single moth membrane
// with a big upper lobe and a smaller lower lobe joined at the root on his back (a notch on the outer edge where they meet).
// textures/entity/omnitrix/big_chill_wings.png (128x128). Every colour is a pixel of big_chill_cloaked.png:
//   0e101b  the black outline and the darkest shading at the root
//   131725 / 152b5a  the dark navy along his body and the leathery ribs
//   183666 / 1b4375  the steel-blue field
//   1a5384 / 1c4e7e  the lighter blue sheen across the middle
//   3baecb / 40bed3 / b1d6dd  the light-cyan eye-spots (teardrops, outlined black)
// Cells (64 wide x 128 tall):
//   BACK 0,0 (the cloak's outside, seen from behind)  /  FRONT 64,0 (seen from the front, the eye-spots bright)
//     cell x = u, the span out from the root (0 .. U_MAX px), cell y = down from w = W_TOP to w = W_BOT (w = height above the root,
//     px). BigChillWings.java maps the same (u, w) rectangle onto its mesh -- keep U_MAX / W_TOP / W_BOT in step with it.
//   The wing-root spines (BigChillWings) are coloured from texel 2,68 of the back cell: the dark root just inside the outline.
// Usage: node scratchpad/recal_v0172/big_chill/gen_big_chill_wings.js
'use strict';
const path = require('path');
const png = require('../../pnglib.js');

const W = 128, H = 128, CW = 64, CH = 128;
const U_MAX = 22, W_TOP = 24, W_BOT = -20;
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

// texel <-> wing coordinates (px on the model)
const toU = x => (x + 0.5) / CW * U_MAX;
const toW = y => W_TOP - (y + 0.5) / CH * (W_TOP - W_BOT);

/** A smooth curve through the points (Catmull-Rom), n samples per span. */
function curve(pts, n) {
	const out = [];
	for (let i = 0; i + 1 < pts.length; i++) {
		const p0 = pts[Math.max(0, i - 1)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(pts.length - 1, i + 2)];
		for (let k = 0; k < n; k++) {
			const t = k / n, t2 = t * t, t3 = t2 * t;
			out.push([0, 1].map(j => 0.5 * (2 * p1[j] + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t2
				+ (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t3)));
		}
	}
	return out;
}

/**
 * A ragged scalloped edge from a to b through the guide points: between each pair of cusps the edge bites in (toward the inside,
 * `inward` = +1 for the left of travel) in a round scallop. Returns the polyline and its cusps (the rib ends).
 */
function scallops(guide, count, depth, inward) {
	const base = curve(guide, 24);
	base.push(guide[guide.length - 1]);
	const len = [0];
	for (let i = 1; i < base.length; i++) len.push(len[i - 1] + Math.hypot(base[i][0] - base[i - 1][0], base[i][1] - base[i - 1][1]));
	const L = len[len.length - 1], out = [], cusps = [];
	for (let i = 0; i < base.length; i++) {
		const s = len[i] / L * count, k = s - Math.floor(s);
		const a = base[Math.max(0, i - 1)], b = base[Math.min(base.length - 1, i + 1)];
		const tx = b[0] - a[0], ty = b[1] - a[1], tl = Math.hypot(tx, ty) || 1;
		// left normal of travel in (u, w): (-ty, tx)
		const nx = -ty / tl * inward, ny = tx / tl * inward;
		// the bites are shallow near the ends so the points stay sharp, deepest mid-edge, a little uneven (ragged)
		const env = Math.sin(Math.PI * len[i] / L) * 0.75 + 0.25;
		const d = depth * env * Math.sqrt(Math.max(0, 1 - Math.pow(k * 2 - 1, 2))) * (0.8 + 0.4 * Math.abs(Math.sin(Math.floor(s) * 2.7)));
		out.push([base[i][0] + nx * d, base[i][1] + ny * d]);
	}
	for (let c = 1; c < count; c++) {
		const target = c / count * L;
		let i = 0;
		while (i < len.length - 1 && len[i] < target) i++;
		cusps.push(base[i]);
	}
	return { pts: out, cusps };
}

// ---- the outline, (u, w) in px, counter-clockwise from the root's top: up the spine-side leading edge, out to the upper tip, down
// the upper lobe's ragged trailing edge into the notch, out and down the lower lobe's leading edge to its tip, back up its ragged
// trailing edge (well outside his legs: past u = 7 at hip height) to the root.
const ROOT_TOP = [0.3, 2.5], ROOT_BOT = [0.3, -2.5];
const UP_TIP = [21.3, 19.5], NOTCH = [10.5, 0.2], LOW_TIP = [17.5, -18.5];
const upperLead = curve([ROOT_TOP, [1.6, 8], [3.6, 13.5], [7.5, 16.6], [13, 18.2], UP_TIP], 10);
const upperTrail = scallops([UP_TIP, [19.5, 13], [16.5, 7.5], [13, 3.2], NOTCH], 5, 1.5, 1);
const lowerLead = curve([NOTCH, [13.6, -2.4], [16.2, -7], [17.6, -12.5], LOW_TIP], 10);
const lowerTrail = scallops([LOW_TIP, [14.2, -14], [10.8, -10], [6.8, -6.3], [3.2, -3.6], ROOT_BOT], 4, 1.3, 1);
const outline = [...upperLead, ...upperTrail.pts, ...lowerLead, ...lowerTrail.pts];

function insidePoly(u, w) {
	let c = false;
	for (let i = 0, j = outline.length - 1; i < outline.length; j = i++) {
		const [xi, yi] = outline[i], [xj, yj] = outline[j];
		if ((yi > w) !== (yj > w) && u < (xj - xi) * (w - yi) / (yj - yi) + xi) c = !c;
	}
	return c;
}

/** Distance from (x,y) to the segment a-b. */
function seg(x, y, ax, ay, bx, by) {
	const dx = bx - ax, dy = by - ay;
	const t = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)));
	return Math.hypot(x - (ax + dx * t), y - (ay + dy * t));
}

// the leathery ribs fan from the root to the cusps of the ragged edges and out to both tips; the upper lobe's leading rib runs
// under the spine
const ROOT = [0.4, 0];
const ribs = [UP_TIP, ...upperTrail.cusps, NOTCH, LOW_TIP, ...lowerTrail.cusps].map(c => [ROOT[0], ROOT[1], c[0], c[1]]);
ribs.push([1.0, 3, 6.5, 16.2]);

// eye-spots: teardrops, round end toward the root, pointed toward the tip they face -- [u, w, radius u, radius w, tip angle]
const spots = [[11.3, 12.4, 1.9, 1.1, 0.55], [15.9, 14.3, 1.35, 0.8, 0.45], [7.4, 8.2, 1.6, 0.95, 0.85], [12.3, -8.4, 1.7, 1.0, -0.95],
	[8.8, -4.6, 1.15, 0.7, -0.7]];

function wing(ox, front) {
	const mask = [];
	for (let y = 0; y < CH; y++) {
		mask.push([]);
		for (let x = 0; x < CW; x++) mask[y].push(insidePoly(toU(x), toW(y)));
	}
	// no stray specks: a pixel needs two filled neighbours to stay
	for (let pass = 0; pass < 2; pass++) {
		for (let y = 0; y < CH; y++) {
			for (let x = 0; x < CW; x++) {
				if (!mask[y][x]) continue;
				let n = 0;
				for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
					const xx = x + dx, yy = y + dy;
					if (xx >= 0 && yy >= 0 && xx < CW && yy < CH && mask[yy][xx]) n++;
				}
				if (n < 2) mask[y][x] = false;
			}
		}
	}
	const inside = (x, y) => x >= 0 && y >= 0 && x < CW && y < CH && mask[y][x];
	for (let y = 0; y < CH; y++) {
		for (let x = 0; x < CW; x++) {
			if (!mask[y][x]) continue;
			const u = toU(x), w = toW(y);
			// the black outline, ~0.5 px on the model (a texel is ~0.35 px); the root edge (u = 0, against his back) stays open
			let edge = false;
			for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [-1, -1], [1, -1], [-1, 1]]) {
				if (x + dx < 0) continue;
				if (!inside(x + dx, y + dy)) edge = true;
			}
			if (edge) {
				set(ox + x, y, BLACK);
				continue;
			}
			// the field: dark navy at the root, steel blue out to a blue toward the tips
			const r = Math.hypot(u - ROOT[0], w - ROOT[1]);
			let c;
			if (r < 2.5) c = mix(BLACK, DARK, r / 2.5);
			else if (r < 6) c = mix(DARK, NAVY, (r - 2.5) / 3.5);
			else c = mix(NAVY, STEEL, (r - 6) / 6);
			if (r > 12) c = mix(c, BLUE, (r - 12) / 8);
			// the sheen: a band arcing across the middle of the membrane (brighter on the back, the cloak's outside)
			const sheen = Math.exp(-Math.pow((r - 11) / 3.2, 2)) * (w > 0 ? 1 : 0.7);
			c = mix(c, front ? SHEEN : SHEEN2, sheen * (front ? 0.5 : 0.7));
			// the inner fold between the lobes darker (where the membrane gathers at the root)
			const fold = Math.exp(-Math.pow(w / 1.6, 2)) * Math.max(0, 1 - u / 10);
			c = mix(c, DARK, fold * 0.6);
			// the leathery ribs
			let rd = 99;
			for (const rb of ribs) rd = Math.min(rd, seg(u, w, rb[0], rb[1], rb[2], rb[3]));
			if (rd < 0.22) c = mix(c, front ? DARK : NAVY, 0.85);
			else if (rd < 0.5) c = mix(c, front ? DARK : NAVY, 0.3);
			// grain
			c = mix(c, DARK, rnd() * 0.1);
			// the eye-spots, outlined black (both faces: the membrane is the same pattern through)
			for (const s of spots) {
				const ca = Math.cos(s[4]), sa = Math.sin(s[4]);
				const lu = (u - s[0]) * ca + (w - s[1]) * sa, lw = -(u - s[0]) * sa + (w - s[1]) * ca;
				const dx = lu / s[2], dy = lw / s[3];
				const tw = dx > 0 ? 1 + dx * 0.9 : 1; // the teardrop narrows toward its tip
				const d = Math.hypot(dx * tw, dy);
				if (d < 1.0) {
					c = d < 0.38 ? mix(ICE, CYAN_HI, d / 0.38) : d < 0.72 ? CYAN_HI : CYAN;
					if (!front) c = mix(c, NAVY, 0.25);
				} else if (d < 1.4) {
					c = BLACK;
				}
			}
			set(ox + x, y, c);
		}
	}
}
wing(0, false);
wing(64, true);

const out = path.join(__dirname, '../../../src/main/resources/assets/projecthero/textures/entity/omnitrix/big_chill_wings.png');
png.write(out, W, H, px);
console.log('wrote', out);
