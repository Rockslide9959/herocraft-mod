// PREVIEW ONLY: renders the Multimouse suit worn on a plain player body (neutral placeholder skin under the suit), with
// the 3D extras the game would add as model parts: the two round mouse ears on the hood, the rope belt with its
// jump-rope handle at the front hip, and the long rope tail from the back of the belt ending in the second handle.
// Uses the concept renderer in scratchpad/viperion/lib_concept_models.js. The player model is a standard Minecraft
// biped (head 8x8x8, body 8x12x4, arms 4 or 3 x12x4, legs 4x12x4, plus the 0.5 / 0.25-px outer layer) UV-mapped to the
// 64x64 skin; every model face samples the skin with nearest-neighbour, so the texels stay crisp.
// Usage: node scratchpad/multimouse/gen_multimouse_sheet.js   (run gen_multimouse_suit.js first)
// Writes multimouse_suit_sheet_wide.png and multimouse_suit_sheet_slim.png next to this script.
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const T = require('../viperion/skin_tools.js');
const { Model, mix, mul, fbm, clamp } = L;
const OUT = __dirname;

// ---------------------------------------------------------------- the neutral placeholder skin (the "wearer")
function placeholderSkin() {
	const px = Buffer.alloc(64 * 64 * 4);
	const set = (x, y, c) => { const i = (y * 64 + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255; };
	const SK = [[200, 150, 120], [222, 176, 146], [236, 194, 166]], HAIR = [[74, 50, 36], [98, 68, 48], [120, 86, 60]];
	const SHIRT = [[90, 100, 112]], PANTS = [[60, 64, 80]];
	const fill = (x0, y0, w, h, fn) => { for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) set(x, y, fn(x - x0, y - y0)); };
	// head: skin all round, hair on top / back / upper sides and a fringe
	fill(0, 0, 32, 16, (x, y) => SK[1]);
	fill(8, 0, 8, 8, (x, y) => HAIR[(x + y) % 3]);
	fill(24, 8, 8, 8, (x, y) => y < 6 ? HAIR[(x * 3 + y) % 3] : SK[0]);
	fill(0, 8, 8, 4, (x, y) => HAIR[(x + y) % 3]); fill(16, 8, 8, 4, (x, y) => HAIR[(x + y + 1) % 3]);
	fill(8, 8, 8, 2, (x, y) => HAIR[(x + y) % 3]);
	// face: eyes on rows 12-13, a mouth on row 14
	for (const [ew, ei] of [[9, 10], [14, 13]]) { set(ew, 12, [246, 246, 246]); set(ei, 12, [70, 100, 150]); set(ew, 13, [226, 226, 226]); set(ei, 13, [44, 64, 104]); }
	set(11, 14, SK[0]); set(12, 14, SK[0]); set(10, 15, [170, 110, 96]); set(11, 15, [150, 90, 80]); set(12, 15, [150, 90, 80]); set(13, 15, [170, 110, 96]);
	// body / arms / legs: plain clothes and skin (hidden by the suit anyway)
	fill(16, 16, 24, 16, () => SHIRT[0]);
	fill(40, 16, 16, 16, () => SK[1]); fill(32, 48, 16, 16, () => SK[1]);
	fill(0, 16, 16, 16, () => PANTS[0]); fill(16, 48, 16, 16, () => PANTS[0]);
	return { w: 64, h: 64, px };
}

// ---------------------------------------------------------------- skin face lookup
// MC box unwrap; model faces -> skin faces: north = front, south = back, west = the entity's right side ("right"),
// east = "left", up = top (mirrored), down = bottom (mirrored)
function skinFaces(ox, oy, w, h, d) {
	return {
		up: [ox + d, oy, w, d], down: [ox + d + w, oy, w, d],
		west: [ox, oy + d, d, h], north: [ox + d, oy + d, w, h], east: [ox + d + w, oy + d, d, h], south: [ox + 2 * d + w, oy + d, w, h],
	};
}

const PALETTE = {
	greyD: [70, 73, 88], grey: [138, 142, 158], greyL: [196, 200, 212], greyH: [236, 238, 246],
	pinkD: [150, 40, 102], pink: [214, 78, 152], pinkL: [244, 140, 196], pinkH: [252, 196, 226],
	inner: [240, 150, 196], innerL: [252, 196, 222],
};

function build(slim, suit, base) {
	const a = slim ? 3 : 4;
	const M = new Model('multimouse_' + (slim ? 'slim' : 'wide'), { TW: 1024, D: 8 });
	M.suit = suit; M.base = base;
	const root = M.bone('root', null, [0, 0, 0]);
	const body = M.bone('body', 'root', [0, 24, 0]);
	const head = M.bone('head', 'body', [0, 24, 0]);
	const armR = M.bone('arm_r', 'body', [-5, 22, 0], [0, 0, -15]);
	const armL = M.bone('arm_l', 'body', [5, 22, 0], [0, 0, 15]);
	const legR = M.bone('leg_r', 'root', [-2, 12, 0], [-2, 0, -1.5]);
	const legL = M.bone('leg_l', 'root', [2, 12, 0], [2, 0, 1.5]);
	// part: [bone, box from/to, skin origin base, skin origin overlay, w,h,d, inflate]
	const part = (b, x0, y0, z0, w, h, d, uvB, uvO, infl) => {
		M.box(b, x0, y0, z0, x0 + w, y0 + h, z0 + d, 'skin', { tag: { layer: 'base', F: skinFaces(uvB[0], uvB[1], w, h, d) } });
		M.box(b, x0 - infl, y0 - infl, z0 - infl, x0 + w + infl, y0 + h + infl, z0 + d + infl, 'skin', { tag: { layer: 'over', F: skinFaces(uvO[0], uvO[1], w, h, d) } });
	};
	part(head, -4, 24, -4, 8, 8, 8, [0, 0], [32, 0], 0.5);
	part(body, -4, 12, -2, 8, 12, 4, [16, 16], [16, 32], 0.25);
	part(armR, -4 - a, 12, -2, a, 12, 4, [40, 16], [40, 32], 0.25);
	part(armL, 4, 12, -2, a, 12, 4, [32, 48], [48, 48], 0.25);
	part(legR, -4, 0, -2, 4, 12, 4, [0, 16], [0, 32], 0.25);
	part(legL, 0, 0, -2, 4, 12, 4, [16, 48], [0, 48], 0.25);

	// ---- 3D extra 1: the two big round mouse ears on the hood (discs of stacked slabs, grey back, pink inner front)
	for (const s of [-1, 1]) {
		const n = s < 0 ? 'r' : 'l';
		const ear = M.bone(`ear_${n}`, 'head', [s * 3.0, 32.2, 0.4], [-8, 0, s * 20]); // tilted out and slightly back
		const C = [s * 3.0, 35.4, 0.4], R = 3.4, R2 = 2.3, TH = 0.9;
		// a round disc as four overlapping boxes (a rounded octagon, the usual Minecraft way to fake a circle)
		const DISC = [[1, 0.44], [0.93, 0.72], [0.72, 0.93], [0.44, 1]];
		M.DISC = DISC;
		for (const [kw, kh] of DISC) M.cbox(ear, C, [2 * R * kw, 2 * R * kh, TH], 'ear_out', { seed: 10 });
		const Ci = [C[0], C[1] - 0.15, C[2] - TH / 2 - 0.08];
		for (const [kw, kh] of DISC) M.cbox(ear, Ci, [2 * R2 * kw, 2 * R2 * kh, 0.2], 'ear_in', { seed: 20 });
		M.A['ear' + n] = C;
	}

	// ---- 3D extra 2: the jump-rope belt round the waist (body row 8 = y 15..16), a knot at the back
	M.box(body, -4.45, 14.85, -2.45, 4.45, 16.15, 2.45, 'rope', { seed: 30 });
	M.cbox(body, [0, 15.5, 2.65], [1.6, 1.6, 0.9], 'knot', { seed: 31 });
	// the handle hanging at the front of the right hip: a short loop of rope, then the pink grip with a bead at each end
	const hip = [-2.9, 15.0, -2.6];
	M.tube(body, [hip, [-3.1, 13.9, -2.75], [-3.15, 12.9, -2.7]], 0.42, 'rope', { seed: 32 });
	M.cbox(body, [-3.15, 12.7, -2.7], [1.0, 0.5, 1.0], 'bead', { seed: 33 });
	M.cbox(body, [-3.15, 11.2, -2.7], [0.8, 2.6, 0.8], 'handle', { seed: 34 });
	M.cbox(body, [-3.15, 9.75, -2.7], [1.0, 0.5, 1.0], 'bead', { seed: 35 });

	// ---- 3D extra 3: the rope tail, from the knot at the back of the belt, curving out and down to near the ground,
	// ending in the jump-rope's other handle
	const tail = M.bone('tail', 'body', [0, 15.5, 2.9]);
	const pts = [[0, 15.5, 2.9], [0.1, 13.6, 3.9], [0.4, 10.8, 4.7], [0.9, 7.6, 5.1], [1.4, 4.9, 4.9], [1.7, 3.3, 4.4]];
	M.tube(tail, pts, 0.62, 'rope', { seed: 40 });
	const end = pts[pts.length - 1];
	M.cbox(tail, [end[0], end[1] - 0.35, end[2]], [1.1, 0.55, 1.1], 'bead', { seed: 41 });
	M.cbox(tail, [end[0], end[1] - 1.95, end[2]], [0.85, 2.7, 0.85], 'handle', { seed: 42 });
	M.cbox(tail, [end[0], end[1] - 3.5, end[2]], [1.15, 0.6, 1.15], 'bead', { seed: 43 });
	return M;
}

function sampleSkin(im, F, f, s, t) {
	const [fx, fy, fw, fh] = F[f];
	let u = s, v = t;
	if (f === 'up' || f === 'down') { u = 1 - s; v = 1 - t; }
	const x = fx + Math.min(fw - 1, Math.floor(u * fw)), y = fy + Math.min(fh - 1, Math.floor(v * fh));
	const i = (y * 64 + x) * 4;
	return [im.px[i], im.px[i + 1], im.px[i + 2], im.px[i + 3]];
}

// how deep a point sits inside the rounded-octagon disc (0 at its outline)
function discDepth(D, dx, dy, R) { let best = -1; for (const [kw, kh] of D) best = Math.max(best, Math.min(R * kw - Math.abs(dx), R * kh - Math.abs(dy))); return best; }
function shader(M) {
	return (c, f, s, t, p, info) => {
		if (c.mat === 'skin') {
			const { layer, F } = c.tag;
			const sp = sampleSkin(M.suit, F, f, s, t);
			if (sp[3] > 0) return [[sp[0], sp[1], sp[2]], 255, null];
			if (layer === 'over') return [[0, 0, 0], 0, null];
			const bp = sampleSkin(M.base, F, f, s, t);
			return [[bp[0], bp[1], bp[2]], bp[3], null];
		}
		const P = PALETTE;
		const n = fbm(p[0] * 1.3 + c.seed, p[1] * 1.3, p[2] * 1.3, 3);
		const pix = (q) => Math.floor(q * info.D) / info.D; // snap to the 1/8-px grid: keep a pixel-art look
		switch (c.mat) {
			case 'ear_out': {
				// grey satin; lighter near the top-left, a bright rim, darker toward the base
				const C = M.A[p[0] < 0 ? 'earr' : 'earl'];
				const dx = pix(p[0] - C[0]), dy = pix(p[1] - C[1]);
				const r = Math.hypot(dx, dy) / 3.4;
				let col = mix(P.grey, P.greyL, clamp(0.55 + dy * 0.12 - dx * 0.05 * Math.sign(C[0]) + (n - 0.5) * 0.3, 0, 1));
				if (discDepth(M.DISC, dx, dy, 3.4) < 0.4) col = mix(col, P.greyH, 0.4); // the rolled rim
				if (f === 'south') col = mul(col, 0.92);
				if (f === 'down') col = mul(col, 0.7);
				return [col, 255, null];
			}
			case 'ear_in': {
				const C = M.A[p[0] < 0 ? 'earr' : 'earl'];
				const dx = pix(p[0] - C[0]), dy = pix(p[1] - C[1] + 0.15);
				const r = Math.hypot(dx, dy) / 2.3;
				let col = mix(P.innerL, P.inner, r > 0.6 ? 0.75 : 0.35);
				if (discDepth(M.DISC, dx, dy, 2.3) < 0.3) col = mix(col, P.pink, 0.55);
				if (Math.hypot(dx + 0.8, dy - 0.8) < 0.4) col = mix(col, [255, 232, 244], 0.45); // a soft sheen
				return [col, 255, null];
			}
			case 'rope': {
				// a twisted rope: diagonal strands in three pinks
				const q = (pix(p[0]) + pix(p[1]) * 1.6 + pix(p[2])) * 1.4;
				const k = ((Math.floor(q) % 3) + 3) % 3;
				let col = [P.pinkD, P.pink, P.pinkL][k];
				if (q - Math.floor(q) < 0.18) col = mix(col, P.pinkD, 0.6);
				if (f === 'down') col = mul(col, 0.8);
				return [col, 255, null];
			}
			case 'knot': {
				const col = mix(P.pink, P.pinkL, clamp(n * 1.4 - 0.2, 0, 1));
				return [info.ePx < 0.15 ? mul(col, 0.8) : col, 255, null];
			}
			case 'handle': {
				// the pink grip: ridged rings, a long highlight down one side
				let col = mix(P.pink, P.pinkL, 0.25);
				if (Math.floor(pix(p[1]) * 2.5) % 2) col = mul(col, 0.9);
				if (info.ePx < 0.12) col = mul(col, 0.82);
				if ((f === 'north' || f === 'west') && Math.abs(info.i / info.w - 0.3) < 0.14) col = mix(col, P.pinkH, 0.7);
				return [col, 255, null];
			}
			case 'bead': {
				let col = mix(P.pinkL, P.pinkH, n);
				if (info.ePx < 0.1) col = mix(col, P.pink, 0.5);
				return [col, 255, null];
			}
		}
		return [[255, 0, 255], 255, null];
	};
}

const base = placeholderSkin();
fs.writeFileSync(path.join(OUT, 'placeholder_wearer.png'), T.encode(64, 64, base.px));
for (const slim of [false, true]) {
	const name = slim ? 'slim' : 'wide';
	const suit = T.decode(path.join(OUT, `multimouse_suit_${name}.png`));
	let M = build(slim, suit, base);
	M.ground();
	M.setHeight(2.1);
	M.layout();
	M.paint(shader(M));
	const it = { model: M, pose: {} };
	const fl = [{ x: 0, z: 0, r: 0.9 }];
	L.sheet(path.join(OUT, `multimouse_suit_sheet_${name}.png`), 'MULTIMOUSE',
		`MOUSE MIRACULOUS SUIT - ${slim ? 'SLIM (3 PX ARMS)' : 'WIDE (4 PX ARMS)'} - EARS, ROPE BELT AND ROPE TAIL ARE 3D PARTS`, [
		{ label: 'FRONT', items: [it], cam: { yaw: 0, pitch: 6 }, floor: fl },
		{ label: 'THREE-QUARTER', items: [it], cam: { yaw: 35, pitch: 12 }, floor: fl },
		{ label: 'SIDE', items: [it], cam: { yaw: 90, pitch: 6 }, floor: fl },
		{ label: 'BACK', items: [it], cam: { yaw: 180, pitch: 10 }, floor: fl },
	], { panelH: 900, minW: 2200, bg: { top: [150, 160, 170], bottom: [60, 66, 74] } });
}
