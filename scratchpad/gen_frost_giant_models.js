// v0.16.1 Frost Giant Assault: CONCEPT models (not wired into the game yet; the user approves the looks first).
//   Jotun Raider, Frost Shaman, Frost Giant Warlord, Ice Brute (one shared Jotun rig), Rime Hound (own quadruped rig),
//   King Laufey (own rig), Ymir's arm (render-only) and the Casket of Ancient Winters (prop).
// Looks are based on the Marvel movies/comics (Thor 2011 Jotnar: deep blue skin, raised ridge scarification, solid red
// eyes, tall and lean, bare-chested with dark leather/fur, ice weapons), painted procedurally (no copied textures).
// Deliberately NOT the Titan: upright, proud, long-limbed, narrow waist, broad shoulders, blue, crystals instead of bone.
//
// Usage (from the repo root):
//   node scratchpad/gen_frost_giant_models.js --concept <outDir> [--only raider,shaman,...] [--no-assets]
// Writes the concept sheets (one model per image, front / three-quarter / side + an action pose, >= 1600x1000) and
// a lineup of every creature at true relative scale; also writes each model's geo / texture / glowmask into
// <outDir>/assets (never into src/).
//
// Conventions (Bedrock geo / GeckoLib, as in gen_v01420_titan_model.js): +Y up, the model faces -Z, the entity's
// RIGHT side is -X. Rotation X > 0 tips a bone's top forward (a hanging limb's free end swings BACK); rotation Z > 0
// swings a hanging limb toward -X (the right). Pose rotations are ADDED to a bone's rest rotation. Model pixels.
'use strict';
const path = require('path');
const L = require('./lib_concept_models.js');
const { Model, mix, mul, fbm, vnoise, hash3, clamp, smooth, seeded, vnorm, vadd, vsub, vsc } = L;

const args = process.argv.slice(2);
const ci = args.indexOf('--concept');
const GAME = args.includes('--game');
const REPO = path.join(__dirname, '..');
const fs = require('fs');
const PREVIEW_ROOT = path.join(REPO, '..', '..', '..', 'scratchpad', 'concepts_v0161');
const OUT = ci >= 0 ? args[ci + 1] : (GAME ? PREVIEW_ROOT : null);
const oi = args.indexOf('--only');
const ONLY = oi >= 0 ? args[oi + 1].split(',') : null;
const WANT = n => !GAME && (!ONLY || ONLY.includes(n));

// ================================================================ palettes
const RED_EYE = [255, 44, 30], RED_HOT = [255, 170, 140];
const ICE_GLOW = [120, 214, 255];
const DEGR = Math.PI / 180;
const FACE_SEED = { north: 11, south: 23, east: 37, west: 41, up: 53, down: 67 };

// Glowing rune glyphs (casket, Ymir's bracer): an angular glyph per cell, drawn only away from the face edges.
function runeGlyph(info, cell, seed) {
	const ci = Math.floor(info.i / cell), cj = Math.floor(info.j / cell);
	const u = (info.i % cell) / cell, v = (info.j % cell) / cell;
	if (info.i < cell * 0.6 || info.j < cell * 0.6 || info.w - info.i < cell * 0.6 || info.h - info.j < cell * 0.6) return false;
	const h = hash3(ci, cj, seed);
	if (h < 0.3 || cj !== Math.floor(info.h / cell / 2)) return false;
	const w = 1.5 / cell;
	const vert = Math.abs(u - 0.5) < w;
	const d1 = Math.abs(u - v) < w * 1.2 && h > 0.55, d2 = Math.abs(u - (1 - v)) < w * 1.2 && h > 0.75 && v < 0.6;
	const bar = Math.abs(v - (h > 0.5 ? 0.3 : 0.7)) < w && u > 0.25 && u < 0.75 && h < 0.6;
	return (vert && v > 0.1 && v < 0.9) || d1 || d2 || bar;
}
const PALS = {
	raider: { L: [110, 140, 188], M: [60, 86, 134], D: [28, 40, 76], ridge: [168, 200, 236], ridgeGlow: [30, 66, 104] },
	shaman: { L: [104, 128, 188], M: [60, 78, 140], D: [30, 36, 80], ridge: [170, 196, 246], ridgeGlow: [50, 90, 150] },
	warlord: { L: [82, 116, 168], M: [44, 70, 120], D: [20, 30, 64], ridge: [146, 190, 236], ridgeGlow: [40, 80, 130] },
	brute: { L: [150, 178, 206], M: [92, 120, 158], D: [42, 58, 92], ridge: [196, 224, 246], ridgeGlow: [40, 84, 130] },
	laufey: { L: [86, 112, 156], M: [46, 62, 106], D: [18, 24, 52], ridge: [140, 176, 230], ridgeGlow: [60, 110, 170] },
};

// ================================================================ material painters
const bevel = (col, info, k1 = 0.8, k2 = 0.93) => info.ePx < 0.3 ? mul(col, k1) : info.ePx < 0.62 ? mul(col, k2) : col;
const vgrad = (col, f, t, k = 0.2) => f === 'up' ? mul(col, 1.08) : f === 'down' ? mul(col, 0.62) : mul(col, 1.05 - k * t);
function lineI(F, w) { const d = Math.abs(F - Math.round(F)); return 1 - smooth(w * 0.55, w, d); }

function leather(p, f, t, info, base = [78, 54, 40]) {
	const n = fbm(p[0] * 1.6 + 11, p[1] * 1.6, p[2] * 1.6, 3);
	let col = mix(mul(base, 0.6), mul(base, 1.25), n);
	if (vnoise(p[0] * 6, p[1] * 1.2, p[2] * 6) > 0.8) col = mul(col, 0.8); // creases
	if (info.ePx > 0.3 && info.ePx < 0.55 && ((info.i + info.j) % 3 === 0)) col = mix(col, [150, 120, 90], 0.5); // stitching
	return bevel(vgrad(col, f, t, 0.15), info, 0.7, 0.88);
}
function fur(p, f, t, info, base = [70, 60, 52], tip = [150, 138, 120]) {
	const strand = fbm(p[0] * 4.5 + 3, p[1] * 0.7, p[2] * 4.5, 3);
	const clump = fbm(p[0] * 1.2, p[1] * 0.5 + 7, p[2] * 1.2, 2);
	let col = mix(mul(base, 0.55), base, strand * 1.2);
	col = mix(col, tip, Math.pow(Math.max(0, strand - 0.45) * 1.8, 1.6) * (0.5 + clump));
	if (f !== 'up' && f !== 'down') col = mul(col, 1.08 - 0.3 * t);
	if (f === 'down') col = mul(col, 0.6);
	return bevel(col, info, 0.75, 0.92);
}
function iron(p, f, t, info, base = [92, 100, 112]) {
	const n = fbm(p[0] * 2.2 + 5, p[1] * 2.2, p[2] * 2.2, 3);
	let col = mix(mul(base, 0.7), mul(base, 1.25), n);
	col = mix(col, [70, 58, 50], Math.max(0, fbm(p[0] * 0.8 + 90, p[1] * 0.8, p[2] * 0.8, 2) - 0.6) * 1.6); // rust/grime
	if (info.ePx < 0.3) col = mix(col, [200, 210, 222], 0.45); // bright worn edge
	else if (info.ePx < 0.6) col = mul(col, 0.8);
	// rivets near the corners
	const rv = info.fw > 2.2 && info.fh > 2.2;
	if (rv) {
		const sx = Math.min(info.i + 0.5, info.w - info.i - 0.5) / info.D, sy = Math.min(info.j + 0.5, info.h - info.j - 0.5) / info.D;
		if (Math.abs(sx - 0.8) < 0.3 && Math.abs(sy - 0.8) < 0.3) col = [190, 196, 204];
	}
	return vgrad(col, f, t, 0.18);
}
function boneMat(p, f, t, info) {
	const n = fbm(p[0] * 1.8, p[1] * 1.8 + 3, p[2] * 1.8, 3);
	let col = mix([150, 136, 110], [234, 224, 198], 0.25 + 0.75 * n);
	if (vnoise(p[0] * 5, p[1] * 5, p[2] * 5) > 0.84) col = mix(col, [110, 96, 78], 0.6);
	return bevel(vgrad(col, f, t, 0.15), info, 0.75, 0.92);
}
function iceMat(p, f, t, info, deep = [60, 132, 196], pale = [196, 236, 255], glowEdge = true, seedOff = 0) {
	const n = fbm(p[0] * 0.9 + seedOff, p[1] * 0.9, p[2] * 0.9, 3);
	const band = fbm(p[0] * 0.5 + p[1] * 0.8 + seedOff, p[2] * 0.6, 3, 2);
	let col = mix(deep, pale, 0.25 + 0.6 * n);
	if (Math.abs(band - 0.5) < 0.03) col = mix(col, [240, 252, 255], 0.7); // internal fracture planes
	if (vnoise(p[0] * 3 + seedOff, p[1] * 3, p[2] * 3) > 0.85) col = mix(col, [255, 255, 255], 0.5);
	if (f === 'up') col = mix(col, [230, 248, 255], 0.3);
	if (f === 'down') col = mul(col, 0.7);
	let g = null;
	if (info.ePx < 0.26) { col = mix(col, [236, 250, 255], 0.75); if (glowEdge) g = ICE_GLOW; }
	else if (info.ePx < 0.55) col = mix(col, pale, 0.25);
	return [col, g];
}
function cloth(p, f, t, info, base, trim) {
	const weave = ((info.i + info.j) & 1) ? 0.97 : 1.03;
	const n = fbm(p[0] * 0.9 + 21, p[1] * 0.5, p[2] * 0.9, 3);
	let col = mul(mix(mul(base, 0.68), mul(base, 1.2), n), weave);
	// vertical folds
	const fold = Math.sin((p[0] + p[2]) * 1.7 + fbm(p[0] * 0.4, p[1] * 0.2, p[2] * 0.4, 2) * 6);
	col = mul(col, 0.92 + 0.08 * fold);
	return bevel(vgrad(col, f, t, 0.12), info, 0.72, 0.9);
}
function hairMat(p, f, t, info, base = [26, 28, 40], hi = [80, 86, 110]) {
	const strand = fbm(p[0] * 6 + 1, p[1] * 0.6, p[2] * 6, 2);
	let col = mix(base, hi, Math.pow(strand, 2.2) * 1.4);
	return bevel(vgrad(col, f, t, 0.15), info, 0.7, 0.9);
}
function woodMat(p, f, t, info) {
	const grain = Math.sin((p[0] + p[2]) * 4 + fbm(p[0], p[1] * 0.3, p[2], 3) * 8);
	let col = mix([34, 30, 36], [70, 62, 66], 0.5 + 0.4 * grain);
	if (fbm(p[0] * 1.5, p[1] * 0.4 + 4, p[2] * 1.5, 2) > 0.66) col = mix(col, [150, 186, 214], 0.35); // hoarfrost
	return bevel(col, info, 0.7, 0.9);
}

// ---------------------------------------------------------------- Jotun skin + ridges
function ridgeAt(M, c, p) {
	// Sparse, deliberate Jotun scarification: a few raised lines that follow the anatomy (not stripes everywhere).
	const A = M.A; const P = c.paint;
	const x = p[0], y = p[1], z = p[2];
	const ax = Math.abs(x);
	const W = 0.3;
	const ln = d => 1 - smooth(W * 0.5, W, Math.abs(d));
	let I = 0;
	const mx = v => { if (v > I) I = v; };
	if (P === 'chest' || P === 'pec' || P === 'waist' || P === 'abs' || P === 'lat' || P === 'trap') {
		if (z < 0) {
			if (ax > 1.5 && ax < A.cw * 0.8 && y > A.chestTop - 2.2) mx(ln(y - (A.chestTop - 1.0 - (ax - 2) * 0.14)));     // collarbones
			const r = Math.hypot(ax - A.pecX, (y - A.pecY) * 1.15);
			if (y < A.pecY + 0.3 && y > A.pecLow - 1) mx(ln(r - A.pecR));                                                // under each pec
			if (y < A.pecY + 1.5 && y > A.pecLow - 1 && ax > A.pecX) mx(ln(r - A.pecR + 1.25));                          // inner swirl
			if (ax < A.cw * 0.7) {
				for (const y0 of A.chevrons) if (ax > 0.5) mx(ln(y - 0.75 * ax - y0) * (y < A.pecLow ? 1 : 0));             // abdomen chevrons
			}
			if (y > A.pelvisTop - 1 && y < A.pecY + 1.2) mx(ln(ax) * 0.9);                                              // linea alba
		} else {
			if (y > A.pelvisTop) mx(ln(ax));                                                                            // spine
			for (const y0 of A.backChev) if (ax > 0.6) mx(ln(y + 0.6 * ax - y0));
			const r = Math.hypot(ax - A.pecX * 0.95, (y - A.chestTop + 4.2) * 1.1);
			if (ax > 1.2) mx(ln(r - 2.4));                                                                               // shoulder blades
		}
	} else if (P === 'delt') {
		const s = Math.sign(x) || 1;
		const r = Math.hypot(y - (A.shY - 0.8), z * 1.1);
		if ((x - s * A.shX) * s > 0.8 && y < A.shY) mx(ln(r - 2.1));                                                   // arc under the shoulder cap
	} else if (P === 'arm') {
		for (const y0 of [A.elbowY + 3.4, A.elbowY + 5.4]) mx(ln(y - 0.55 * Math.abs(z) - y0));
	} else if (P === 'fore') {
		const s = Math.sign(x) || 1;
		const lx = (x - s * A.shX) * s;
		if (lx > 0.8) mx(ln(z + 0.0) * (y > A.wristY + 2 ? 1 : 0));                                                    // a line down the outer forearm
		mx(ln(y - 0.6 * Math.abs(z) - (A.elbowY - 3.0)) * (lx > -0.5 ? 1 : 0));
	} else if (P === 'thigh') {
		const s = Math.sign(x) || 1;
		const lx = (x - s * A.legX) * s;
		if (z < 0) mx(ln(y - 0.75 * Math.abs(lx) - (A.kneeY + 6.0)));
		if (lx > 1.6) mx(ln(y - 0.5 * z - (A.kneeY + 9)));
	} else if (P === 'shin') {
		const s = Math.sign(x) || 1;
		const lx = (x - s * A.legX) * s;
		if (z < -1 && y > A.ankleY + 2) mx(ln(lx));
	} else if (P === 'head' || P === 'brow') {
		const fz = A.faceZ;
		if (z < fz + 1.0) for (const k of [1.0, 2.25]) if (y > A.browY + 0.7) mx(ln(y - A.browY - k - 0.9 * ax));    // forehead chevrons
		if (y > A.browY + 1.5 && (y > A.headTop - 0.6 || z > fz + 1.0)) { mx(ln(ax - 0.55)); mx(ln(ax - 2.1) * 0.8); } // scalp ridges
		if (z < fz + 0.5 && y < A.eyeY - 0.55 && y > A.mouthY + 0.2) mx(ln(ax - 2.0 - (A.eyeY - y) * 0.3));          // cheek lines
	} else if (P === 'jaw') {
		if (z < A.faceZ + 0.6) mx(ln(ax) * (y < A.mouthY - 0.3 ? 1 : 0));
	} else if (P === 'neck') {
		if (z > 0.5) mx(ln(ax));
	}
	return I;
}

function skin(M, c, f, s, t, p, info) {
	const pal = M.pal;
	const n1 = fbm(p[0] * 0.18 + 3, p[1] * 0.18, p[2] * 0.18, 4);
	const n2 = fbm(p[0] * 1.4 + 50, p[1] * 1.4, p[2] * 1.4, 2);
	let col = mix(pal.D, pal.L, 0.22 + 0.62 * n1);
	col = mix(col, pal.M, 0.35);
	col = mul(col, 0.92 + 0.14 * n2);
	if (fbm(p[0] * 0.6 + 7, p[1] * 0.6, p[2] * 0.6, 3) > 0.66) col = mix(col, [200, 226, 246], 0.18); // frost bloom
	col = vgrad(col, f, t, 0.22);
	// raised ridges: bright core, dark lip underneath, bright top edge
	const I = ridgeAt(M, c, p);
	const up = 0.32;
	const Iu = ridgeAt(M, c, [p[0], p[1] + up, p[2]]);
	const Id = ridgeAt(M, c, [p[0], p[1] - up, p[2]]);
	let g = null;
	if (I > 0.01) { col = mix(col, pal.ridge, I * 0.85); if (Id < I * 0.6) col = mix(col, [230, 244, 255], 0.25 * I); if (pal.ridgeGlow) g = mul(pal.ridgeGlow, I); }
	else if (Iu > 0.2) col = mul(col, 1 - 0.32 * Iu);
	// anatomy shading
	const A = M.A;
	if (c.paint === 'abs' && f === 'north') {
		const ax = Math.abs(p[0]);
		if (ax < 0.25) col = mul(col, 0.8);
		for (const yy of A.absLines || []) if (Math.abs(p[1] - yy) < 0.2) col = mul(col, 0.82);
	}
	if (c.paint === 'pec' && f === 'down') col = mul(col, 0.7);
	if (M.gaunt && (c.paint === 'chest' || c.paint === 'lat') && f !== 'up' && f !== 'down' && p[1] < A.pecLow + 0.5 && p[1] > A.chestBot - 0.2 && Math.abs(p[0]) > 1.0) {
		const r = ((p[1] - A.chestBot - 0.12 * Math.abs(p[0])) / 1.45) % 1; // gaunt: ribs showing through
		if (r > 0.0 && r < 0.28) col = mul(col, 0.68); else if (r > 0.4 && r < 0.62) col = mix(col, pal.L, 0.25);
	}
	if (M.gaunt && c.paint === 'head' && f === 'north' && Math.abs(Math.abs(p[0]) - 2.2) < 0.7 && p[1] < A.eyeY - 0.6 && p[1] > A.mouthY) col = mul(col, 0.74); // hollow cheeks
	col = bevel(col, info, 0.8, 0.94);
	if (c.paint === 'head' || c.paint === 'jaw') { const r = face(M, c, f, p, col, g); col = r[0]; g = r[1]; }
	if (c.paint === 'finger' && info && p[1] < (c.from[1] + 0.45)) col = mix(col, [30, 34, 56], 0.6); // dark nails at the tips
	return [col, 255, g];
}

function face(M, c, f, p, col, g) {
	const A = M.A;
	if (f !== 'north') return [col, g];
	const x = p[0], y = p[1], ax = Math.abs(x);
	if (c.paint === 'head') {
		// eye sockets: deep shadow, then a solid glowing red eye (no pupil: Marvel Jotun)
		const ex = Math.abs(ax - A.eyeX), ey = y - A.eyeY;
		if (ex < A.eyeW + 0.35 && Math.abs(ey) < A.eyeH + 0.3) col = mul(col, 0.45);
		const e = Math.pow(ex / A.eyeW, 2) + Math.pow((ey + (ax - A.eyeX) * 0.18) / A.eyeH, 2);
		if (e < 1) { col = mix(RED_HOT, RED_EYE, Math.sqrt(e) * 1.3); g = mix([255, 120, 90], RED_EYE, Math.sqrt(e)); }
		// brow shadow line and nose shading
		if (Math.abs(y - (A.browY - 0.15)) < 0.15 && ax < A.eyeX + A.eyeW + 0.3) col = mul(col, 0.7);
	}
	if (c.paint === 'jaw') {
		if (Math.abs(y - A.mouthY) < 0.17 && ax < A.mouthW) { col = [14, 14, 30]; }
		else if (Math.abs(y - A.mouthY - 0.3) < 0.14 && ax < A.mouthW * 0.9) col = mul(col, 0.72);
		else if (y < A.mouthY && y > A.mouthY - 0.5 && ax < A.mouthW * 0.8) col = mul(col, 0.85);
	}
	return [col, g];
}

// ---------------------------------------------------------------- shared material switch
function shadeFor(M) {
	return (c, f, s, t, p, info) => {
		const P = c.paint;
		switch (c.mat) {
			case 'skin': return skin(M, c, f, s, t, p, info);
			case 'leather': return [leather(p, f, t, info, M.leatherCol || [78, 54, 40]), 255, null];
			case 'leather_d': return [leather(p, f, t, info, [46, 36, 34]), 255, null];
			case 'strap': return [leather(p, f, t, info, [58, 40, 30]), 255, null];
			case 'fur': return [fur(p, f, t, info, M.furCol || [70, 60, 52], M.furTip || [150, 138, 120]), 255, null];
			case 'fur_w': return [fur(p, f, t, info, [150, 160, 174], [236, 242, 250]), 255, null];
			case 'fur_d': return [fur(p, f, t, info, [34, 34, 44], [96, 100, 120]), 255, null];
			case 'iron': return [iron(p, f, t, info, M.ironCol || [92, 100, 112]), 255, null];
			case 'iron_d': return [iron(p, f, t, info, [56, 62, 74]), 255, null];
			case 'bone': {
				let col = boneMat(p, f, t, info);
				if (P === 'skull' && f === 'north') { // eye sockets + nose hole of a small skull
					if (t > 0.3 && t < 0.55 && (Math.abs(s - 0.3) < 0.13 || Math.abs(s - 0.7) < 0.13)) col = [30, 26, 24];
					if (t > 0.62 && t < 0.74 && Math.abs(s - 0.5) < 0.06) col = [40, 34, 30];
					if (t > 0.82 && ((info.i & 1) === 0)) col = mul(col, 0.6);
				}
				return [col, 255, null];
			}
			case 'horn': {
				let col = boneMat(p, f, t, info);
				const ring = Math.abs(Math.sin((p[0] + p[1] * 1.3 + p[2]) * 2.6));
				col = mix(col, [90, 76, 62], (1 - ring) * 0.35 + (c.tag === 'tip' ? 0 : 0.15));
				if (c.tag === 'tip') col = mix(col, [250, 244, 226], 0.3);
				return [col, 255, null];
			}
			case 'ice': { const [col, g] = iceMat(p, f, t, info, [70, 140, 200], [196, 236, 255], true, c.seed * 3.1); return [col, 255, g]; }
			case 'blade': { // flat faces stay clear ice, the thin edges glow
				const [col0] = iceMat(p, f, t, info, [70, 140, 200], [200, 238, 255], false, c.seed * 3.1);
				let col = col0, g = null;
				if (f === 'east' || f === 'west' || (c.tag === 'tip' && (f === 'north' || f === 'south'))) { col = mix(col, [230, 250, 255], 0.6); g = ICE_GLOW; }
				else if ((f === 'up' || f === 'down') && (s < 0.1 || s > 0.9)) { col = mix(col, [230, 250, 255], 0.5); g = mul(ICE_GLOW, 0.5); }
				return [col, 255, g];
			}
			case 'ice_shaft': { const [col] = iceMat(p, f, t, info, [34, 70, 120], [110, 160, 205], false, c.seed * 3.1); return [col, 255, null]; }
			case 'ice_dim': { const [col] = iceMat(p, f, t, info, [80, 150, 205], [200, 236, 255], false, c.seed * 3.1); return [col, 255, null]; }
			case 'crystal': {
				const [col0, g0] = iceMat(p, f, t, info, [40, 120, 200], [170, 236, 255], true, c.seed * 7.7);
				let col = col0, g = g0 ? mul(g0, 0.62) : null;
				if (!g && c.tag === 'tip') g = mul(ICE_GLOW, 0.55);
				if (!g && info.ePx > 0.6 && fbm(p[0] * 2, p[1] * 2, p[2] * 2 + c.seed, 2) > 0.62) { g = mul(ICE_GLOW, 0.4); }
				return [col, 255, g];
			}
			case 'crystal_hot': { // staff / crown cores: fully glowing
				const [col] = iceMat(p, f, t, info, [110, 200, 255], [230, 250, 255], true, c.seed);
				return [mix(col, [220, 246, 255], 0.4), 255, mix(ICE_GLOW, [230, 250, 255], info.ePx < 0.3 ? 0.8 : 0.2)];
			}
			case 'cloth': {
				if (P === 'tunic' && f === 'north' && Math.abs(p[0]) < (p[1] - M.A.chestBot + 0.5) * 0.42) return [[0, 0, 0], 0, null]; // V opening
				let col = cloth(p, f, t, info, M.clothCol || [36, 52, 92]);
				if (P === 'tunic' && f === 'north' && Math.abs(p[0]) < (p[1] - M.A.chestBot + 0.5) * 0.42 + 0.45) col = mix(col, M.trimCol || [150, 160, 190], 0.7);
				// embroidered frost runes along the hem of skirts and sleeves
				if ((P === 'skirt' || P === 'sleeve') && f !== 'up' && f !== 'down' && ((Math.abs(t - 0.86) < 0.012 * 4 / Math.max(1, info.fh)) || (Math.abs(t - 0.8) < 0.15 / Math.max(1, info.fh) && ((info.i >> 2) & 1)))) col = mix(col, [150, 190, 236], 0.55);
				return [col, 255, null];
			}
			case 'cloth2': return [cloth(p, f, t, info, M.cloth2Col || [76, 46, 108]), 255, null];
			case 'trim': {
				let col = cloth(p, f, t, info, M.trimCol || [150, 160, 190]);
				const zig = Math.abs(((info.i + info.j * 0.0) % 6) - 3) === Math.round(info.j % 4) ? 1 : 0;
				if (zig) col = mix(col, [220, 230, 250], 0.6);
				return [col, 255, null];
			}
			case 'hair': return [hairMat(p, f, t, info, M.hairCol || [26, 28, 40], M.hairHi || [80, 86, 110]), 255, null];
			case 'beard': return [hairMat(p, f, t, info, [120, 128, 142], [214, 222, 234]), 255, null];
			case 'wood': return [woodMat(p, f, t, info), 255, null];
			case 'gold': { const n = fbm(p[0] * 2, p[1] * 2, p[2] * 2, 2); let col = mix([120, 86, 30], [236, 196, 96], n); if (info.ePx < 0.3) col = [255, 232, 150]; return [vgrad(col, f, t, 0.2), 255, null]; }
			case 'eye_glow': return [RED_EYE, 255, RED_EYE];
			case 'rune': { // dark blue-black iron with silver edges and glowing runes (casket, Ymir's bracer)
				let col = iron(p, f, t, info, [46, 56, 80]);
				if (info.ePx < 0.3) col = mix(col, [190, 210, 236], 0.5);
				let g = null;
				const cell = Math.max(5, Math.round(info.D * (info.fw > 30 ? 4 : 1.6)));
				if (f !== 'up' && f !== 'down' && Math.min(info.w, info.h) >= cell * 2 && runeGlyph(info, cell, c.seed + FACE_SEED[f])) { col = [150, 220, 255]; g = ICE_GLOW; }
				if (P === 'lock' && f === 'north' && Math.hypot(s - 0.5, t - 0.5) < 0.22) { col = [210, 244, 255]; g = [170, 230, 255]; }
				return [col, 255, g];
			}
			case 'casket_glass': { // swirling trapped winter, fully emissive
				const sw = fbm(p[0] * 0.16 + p[1] * 0.1, p[1] * 0.2, p[2] * 0.16 + 7, 3);
				const band = Math.abs(Math.sin(sw * 12 + p[1] * 0.4));
				let col = mix([40, 110, 210], [200, 242, 255], Math.pow(band, 3) * 0.8 + sw * 0.3);
				if (hash3(Math.floor(p[0] * 3), Math.floor(p[1] * 3), Math.floor(p[2] * 3)) > 0.992) col = [255, 255, 255];
				if (info.ePx < 0.3) col = mix(col, [230, 250, 255], 0.6);
				return [col, 255, mix([50, 130, 230], [210, 245, 255], Math.pow(band, 3) * 0.7)];
			}
			case 'wolf': case 'wolf_pale': {
				const pale = c.mat === 'wolf_pale';
				let col = fur(p, f, t, info, pale ? [150, 166, 186] : (M.furCol || [64, 80, 104]), pale ? [236, 244, 252] : (M.furTip || [176, 196, 216]));
				if (!pale && (f === 'down' || (P === 'belly' && t > 0.6) || P === 'snout' && f === 'down')) col = mix(col, [190, 204, 220], 0.5);
				if (!pale && (P === 'chest' || P === 'hips' || P === 'belly') && f === 'up') col = mul(col, 0.8); // darker saddle
				if (P === 'paw' && f !== 'up') col = mul(col, 0.75);
				if (P === 'ear' && (f === 'north') && s > 0.25 && s < 0.75 && t > 0.3) col = [60, 46, 60];
				let g = null;
				if (P === 'wskull' && f === 'north') {
					const ax = Math.abs(p[0]);
					const ey = 20.3 + (ax - 1.5) * 0.25;
					if (Math.abs(ax - 1.55) < 0.75 && Math.abs(p[1] - ey) < 0.33) { col = [200, 240, 255]; g = [150, 225, 255]; }
					else if (Math.abs(ax - 1.55) < 1.0 && Math.abs(p[1] - ey) < 0.6) col = mul(col, 0.5);
				}
				if (P === 'snout' && f === 'north' && p[1] < 17.6) col = [40, 30, 40];
				return [col, 255, g];
			}
			case 'nose': return [bevel([24, 24, 32], info, 0.7, 0.9), 255, null];
			case 'tooth': return [mix([200, 210, 220], [250, 252, 255], t), 255, null];
			case 'claw': return [[44, 50, 66], 255, null];
			case 'sil': return [[50, 54, 64], 255, null];
			case 'glacier': {
				// ancient glacier skin: grey-blue strata, blue ice veins, frost, a few deep glowing fissures
				const n = fbm(p[0] * 0.06 + 2, p[1] * 0.06, p[2] * 0.06, 4);
				const strata = 0.5 + 0.5 * Math.sin(p[1] * 0.35 + fbm(p[0] * 0.05, p[1] * 0.05, p[2] * 0.05, 3) * 9);
				let col = mix([92, 104, 122], [170, 182, 196], n * 0.7 + strata * 0.3);
				col = mul(col, 0.93 + 0.14 * fbm(p[0] * 0.6, p[1] * 0.6, p[2] * 0.6, 2));
				const vein = fbm(p[0] * 0.04 + 30, p[1] * 0.04, p[2] * 0.04, 3);
				if (vein > 0.56) col = mix(col, [128, 182, 222], Math.min(0.6, (vein - 0.56) * 5)); // blue ice
				col = mix(col, [226, 236, 246], 0.35 * smooth(0.6, 0.75, fbm(p[0] * 0.5 + 5, p[1] * 0.5, p[2] * 0.5, 3))); // hoarfrost
				let g = null;
				const mask = fbm(p[0] * 0.03 + 70, p[1] * 0.03, p[2] * 0.03, 2);
				const cr = Math.abs(fbm(p[0] * 0.08 + 9, p[1] * 0.05, p[2] * 0.08, 3) - 0.5);
				if (mask > 0.64 && cr < 0.006) { col = [130, 210, 255]; g = [60, 140, 220]; }
				else if (mask > 0.56 && cr < 0.014) col = mix(col, [36, 46, 66], 0.75);
				if (f === 'up' || (f !== 'down' && t < 0.08)) col = mix(col, [236, 244, 250], 0.75); // snow caps
				if (f === 'down') col = mul(col, 0.6);
				if ((P === 'finger' || P === 'palm') && f !== 'up' && Math.abs(Math.sin(p[1] * 0.9)) < 0.08) col = mul(col, 0.7); // knuckle creases
				return [bevel(col, info, 0.8, 0.92), 255, g];
			}
			default:
				if (M.extraMat) { const r = M.extraMat(c, f, s, t, p, info); if (r) return r; }
				return [[255, 0, 255], 255, null];
		}
	};
}

// ---------------------------------------------------------------- helpers
const SIDES = [[-1, 'r'], [1, 'l']];
const sxr = (s, a, b) => s > 0 ? [a, b] : [-b, -a]; // outward x-range [a,b] on side s
function crystals(M, bone, base, dir, n, len, w, seed, mat = 'crystal', spread = 0.45) {
	const rnd = seeded(seed * 7919 + 13);
	const d0 = vnorm(dir);
	// two axes perpendicular to dir
	const ref = Math.abs(d0[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0];
	const u = vnorm([d0[1] * ref[2] - d0[2] * ref[1], d0[2] * ref[0] - d0[0] * ref[2], d0[0] * ref[1] - d0[1] * ref[0]]);
	const v = [d0[1] * u[2] - d0[2] * u[1], d0[2] * u[0] - d0[0] * u[2], d0[0] * u[1] - d0[1] * u[0]];
	for (let i = 0; i < n; i++) {
		const a = rnd() * Math.PI * 2, r = i === 0 ? 0 : (0.35 + rnd() * 0.65);
		const dir2 = vnorm(vadd(d0, vadd(vsc(u, Math.cos(a) * spread * r), vsc(v, Math.sin(a) * spread * r))));
		const L0 = len * (i === 0 ? 1 : 0.45 + rnd() * 0.5), W0 = w * (i === 0 ? 1 : 0.55 + rnd() * 0.4);
		const b = vadd(base, vadd(vsc(u, Math.cos(a) * w * 0.7 * r), vsc(v, Math.sin(a) * w * 0.7 * r)));
		const st = vsub(b, vsc(dir2, L0 * 0.15));
		M.stick(bone, st, dir2, L0 * 0.72, W0, mat, { seed: seed + i });
		M.stick(bone, vadd(st, vsc(dir2, L0 * 0.62)), dir2, L0 * 0.42, W0 * 0.58, mat, { seed: seed + i + 50, tag: 'tip' });
		M.stick(bone, vadd(st, vsc(dir2, L0 * 0.95)), dir2, L0 * 0.16, W0 * 0.26, mat, { seed: seed + i + 90, tag: 'tip' });
	}
}
// Feet planted: root offset that puts the lowest foot cube on y = 0.
function planted(M, pose, extra = 0) {
	const W = M.world(pose);
	const lo = M.bounds(W, c => /^(foot|toe|paw)/.test(c.bone) || c.tag === 'sole').lo[1];
	return [0, -lo + extra, 0];
}

// ================================================================ the shared Jotun rig
// Bones: root > hips > (thigh > shin > foot) x2, hips > waist > flinch > chest > neck > head > jaw > beard,
// chest > shoulder > upper_arm > forearm > hand > (fingers > fingertips, weapon) x2, plus gear attachment bones
// (helm, mantle, cape, crystals_back, braid, flap_f / flap_b) depending on the variant.
const BASE = {
	footH: 2.6, footLen: 8.4, footW: 4.4,
	shinLen: 15, shinW: 3.9, shinD: 4.1,
	thighLen: 15.5, thighW: 5.2, thighD: 5.4, legX: 3.0,
	pelvisH: 6, pelvisW: 11.4, pelvisD: 6.6,
	waistH: 5.4, waistW: 8.8, waistD: 5.8,
	chestH: 13.5, chestW: 17.6, ribW: 12.0, chestD: 7.8,
	neckH: 3.2, neckW: 4.6,
	headW: 6.4, headH: 7.6, headD: 7.2,
	armW: 3.9, armD: 4.0, upperLen: 13, foreLen: 12, handLen: 3.9, fingerLen: 4.4, foreW: 4.0,
	bulk: 1, armOut: 7,
};
function jotunRig(M, P) {
	const A = M.A;
	const b = P.bulk;
	const ankleY = P.footH, kneeY = ankleY + P.shinLen, hipY = kneeY + P.thighLen;
	const pelvisTop = hipY + P.pelvisH - 2.5;
	const chestBot = pelvisTop + P.waistH, chestTop = chestBot + P.chestH;
	const hb = chestTop + P.neckH - 1.2; // bottom of the jaw
	const shX = P.chestW / 2 + P.armW / 2 - 1.0, shY = chestTop - 2.4;
	const elbowY = shY - P.upperLen, wristY = elbowY - P.foreLen, knuckY = wristY - P.handLen;
	const legX = P.legX;
	Object.assign(A, { ankleY, kneeY, hipY, pelvisTop, chestBot, chestTop, shX, shY, elbowY, wristY, knuckY, legX, hb });
	const hw = P.headW / 2, hd = P.headD;
	const faceZ = -hd / 2 - 0.35;
	Object.assign(A, {
		faceZ, browY: hb + 4.6, eyeY: hb + 4.0, eyeX: 1.45 * P.headW / 6.4, eyeW: 0.78 * P.headW / 6.4, eyeH: 0.36,
		mouthY: hb + 1.15, mouthW: 1.5, headTop: hb + P.headH + 0.3,
		pecX: 3.7 * P.chestW / 16.6, pecY: chestTop - 4.6, pecR: 2.9 * P.chestW / 16.6, pecLow: chestTop - 8.4,
		absLines: [chestBot - 1.6, chestBot + 0.6, chestBot + 2.8].filter(y => y > pelvisTop),
		cw: P.chestW / 2 * P.bulk, chevrons: [chestBot + 1.2, pelvisTop + 1.0], backChev: [chestTop - 7.5, chestBot + 1.5],
	});

	const root = M.bone('root', null, [0, 0, 0]);
	const hips = M.bone('hips', 'root', [0, hipY, 0]);
	M.box(hips, -P.pelvisW / 2, hipY - 2.5, -P.pelvisD / 2, P.pelvisW / 2, pelvisTop, P.pelvisD / 2, 'skin', { paint: 'waist' });
	M.box(hips, -P.pelvisW / 2 + 0.6, hipY - 3.0, P.pelvisD / 2 - 0.6, P.pelvisW / 2 - 0.6, hipY + 1.8, P.pelvisD / 2 + 0.9, 'skin', { paint: 'waist' }); // glutes

	for (const [s, n] of SIDES) {
		const lx = s * legX;
		const thigh = M.bone(`thigh_${n}`, 'hips', [lx, hipY, 0], [0, 0, -s * 1.5]);
		const tw = P.thighW / 2 * b, td = P.thighD / 2 * b;
		M.box(thigh, lx - tw, kneeY - 0.6, -td, lx + tw, hipY + 1.4, td, 'skin', { paint: 'thigh' });
		M.box(thigh, lx - tw * 0.82, kneeY + 3.0, -td - 0.5, lx + tw * 0.82, hipY - 2.6, -td + 0.2, 'skin', { paint: 'thigh' }); // quads
		M.box(thigh, lx - tw * 0.8, kneeY + 2.5, td - 0.2, lx + tw * 0.8, hipY - 3.5, td + 0.45, 'skin', { paint: 'thigh' });   // hamstring
		const shin = M.bone(`shin_${n}`, `thigh_${n}`, [lx, kneeY, 0]);
		const sw = P.shinW / 2 * b, sd = P.shinD / 2 * b;
		M.box(shin, lx - sw, ankleY - 0.4, -sd, lx + sw, kneeY + 0.2, sd, 'skin', { paint: 'shin' });
		M.box(shin, lx - sw * 1.1, kneeY - 8.5, -sd * 0.3, lx + sw * 1.1, kneeY - 1.6, sd + 0.9 * b, 'skin', { paint: 'shin' }); // calf
		M.box(shin, lx - sw * 0.82, kneeY - 1.6, -sd - 0.6, lx + sw * 0.82, kneeY + 1.0, -sd + 0.3, 'skin', { paint: 'shin' });     // knee cap
		const foot = M.bone(`foot_${n}`, `shin_${n}`, [lx, ankleY, 0]);
		const fw = P.footW / 2 * b;
		M.box(foot, lx - fw, 0, -P.footLen * 0.66, lx + fw, ankleY, P.footLen * 0.34, 'skin', { paint: 'foot', tag: 'sole' });
		M.box(foot, lx - fw * 0.92, 0, -P.footLen * 0.66 - 1.2, lx + fw * 0.92, 1.25, -P.footLen * 0.66 + 0.2, 'skin', { paint: 'toes', tag: 'sole' });
		M.box(foot, lx - sw * 0.9, ankleY - 0.3, -sd * 0.8, lx + sw * 0.9, ankleY + 1.4, sd * 0.8, 'skin', { paint: 'shin' }); // ankle
	}

	const waist = M.bone('waist', 'hips', [0, pelvisTop, 0]);
	const ww = P.waistW / 2 * b, wd = P.waistD / 2 * b;
	M.box(waist, -ww, pelvisTop - 0.6, -wd, ww, chestBot + 0.6, wd, 'skin', { paint: 'abs' });
	M.box(waist, -ww * 0.68, pelvisTop - 0.2, -wd - 0.45, ww * 0.68, chestBot + 0.4, -wd + 0.2, 'skin', { paint: 'abs' }); // six-pack slab
	const flinch = M.bone('flinch', 'waist', [0, chestBot, 0]);
	const chest = M.bone('chest', 'flinch', [0, chestBot, 0]);
	const cw = P.chestW / 2 * b, rw = P.ribW / 2 * b, cd = P.chestD / 2 * b;
	M.box(chest, -rw, chestBot - 0.4, -cd * 0.92, rw, chestBot + 6.5, cd * 0.92, 'skin', { paint: 'chest' });           // ribcage
	M.box(chest, -cw * 0.86, chestBot + 4.2, -cd * 0.95, cw * 0.86, chestBot + 9.0, cd, 'skin', { paint: 'lat' });       // lats flare
	M.box(chest, -cw, chestBot + 8.0, -cd, cw, chestTop, cd, 'skin', { paint: 'chest' });                                 // upper chest
	M.box(chest, -cw * 0.86, A.pecLow + 0.3, -cd - 0.75, cw * 0.86, chestTop - 1.6, -cd + 0.3, 'skin', { paint: 'pec' }); // pecs
	M.box(chest, -cw * 0.72, chestBot + 7.5, cd - 0.3, cw * 0.72, chestTop - 1.2, cd + 0.7, 'skin', { paint: 'chest' });  // upper back
	for (const [s] of SIDES) // trapezius sloping to the shoulders
		M.box(chest, ...[sxr(s, 1.2, cw * 0.82)[0], chestTop - 1.2, -1.8, sxr(s, 1.2, cw * 0.82)[1], chestTop + 1.6 * b, cd * 0.7], 'skin', { paint: 'trap', rot: [0, 0, s * 14], pivot: [s * 1.2, chestTop, 0] });

	const neck = M.bone('neck', 'chest', [0, chestTop, 0]);
	M.box(neck, -P.neckW / 2 * b, chestTop - 1.2, -2.0, P.neckW / 2 * b, hb + 2.4, 2.5, 'skin', { paint: 'neck' });
	const head = M.bone('head', 'neck', [0, chestTop + 1.6, 0]);
	M.box(head, -hw, hb + 2.0, -hd / 2, hw, hb + P.headH, hd / 2 - 0.2, 'skin', { paint: 'head' });                       // cranium
	M.box(head, -hw + 0.3, hb + 2.6, -hd / 2 - 0.35, hw - 0.3, hb + 4.4, -hd / 2 + 0.2, 'skin', { paint: 'head' });       // cheek/eye plane
	M.box(head, -hw + 0.05, A.browY - 0.05, faceZ - 0.55, hw - 0.05, A.browY + 0.85, faceZ + 0.5, 'skin', { paint: 'brow' }); // brow ridge
	M.box(head, -0.62, hb + 2.4, faceZ - 0.75, 0.62, A.browY + 0.1, faceZ + 0.4, 'skin', { paint: 'nose' });
	M.box(head, -hw - 0.3, hb + 3.0, -0.8, hw + 0.3, hb + 4.9, 0.6, 'skin', { paint: 'ear' });
	M.box(head, -hw + 0.5, hb + P.headH - 0.05, -hd / 2 + 0.6, hw - 0.5, hb + P.headH + 0.5, hd / 2 - 0.9, 'skin', { paint: 'head' }); // dome
	const jaw = M.bone('jaw', 'head', [0, hb + 2.4, 1.0]);
	M.box(jaw, -hw + 0.55, hb - 0.2, faceZ + 0.1, hw - 0.55, hb + 2.6, 1.2, 'skin', { paint: 'jaw' });
	M.box(jaw, -1.5, hb - 0.55, faceZ - 0.15, 1.5, hb + 1.0, faceZ + 1.6, 'skin', { paint: 'jaw' }); // chin
	M.bone('beard', 'jaw', [0, hb, faceZ + 0.5]);

	for (const [s, n] of SIDES) {
		const sx = s * shX;
		const sh = M.bone(`shoulder_${n}`, 'chest', [s * 2.5, chestTop - 1.0, 0]);
		const ua = M.bone(`arm_${n}`, `shoulder_${n}`, [sx, shY, 0], [0, 0, -s * P.armOut]);
		const aw = P.armW / 2 * b, ad = P.armD / 2 * b;
		M.box(ua, sx - aw, elbowY - 0.6, -ad, sx + aw, shY + 1.0, ad, 'skin', { paint: 'arm' });
		M.box(ua, sx - aw * 1.15, shY - 5.0, -ad * 1.15, sx + aw * 1.15, shY + 2.2, ad * 1.1, 'skin', { paint: 'delt' });   // deltoid
		M.box(ua, sx - aw * 0.75, elbowY + 2.0, -ad - 0.55, sx + aw * 0.75, shY - 4.5, -ad + 0.2, 'skin', { paint: 'arm' }); // biceps
		M.box(ua, sx - aw * 0.8, elbowY + 1.0, ad - 0.2, sx + aw * 0.8, shY - 4.0, ad + 0.45, 'skin', { paint: 'arm' });      // triceps
		const fa = M.bone(`forearm_${n}`, `arm_${n}`, [sx, elbowY, 0], [-8, 0, 0]);
		const fw2 = P.foreW / 2 * b;
		M.box(fa, sx - fw2 * 0.85, wristY - 0.2, -ad * 0.85, sx + fw2 * 0.85, elbowY + 0.6, ad * 0.85, 'skin', { paint: 'fore' });
		M.box(fa, sx - fw2, elbowY - 5.5, -ad * 0.98, sx + fw2, elbowY + 0.4, ad * 0.98, 'skin', { paint: 'fore' }); // forearm muscle
		const hand = M.bone(`hand_${n}`, `forearm_${n}`, [sx, wristY, 0]);
		const hx = 1.0 * b, hz = 1.75 * b;
		M.box(hand, sx - hx, knuckY, -hz, sx + hx, wristY + 0.4, hz, 'skin', { paint: 'hand' });
		// thumb: from the inner front edge, angled forward and down
		M.stick(hand, [sx - s * 0.5, wristY - 0.8, -hz + 0.3], [-s * 0.35, -0.75, -0.55], P.fingerLen * 0.75, 0.85 * b, 'skin', { paint: 'finger' });
		const fing = M.bone(`fingers_${n}`, `hand_${n}`, [sx, knuckY + 0.2, 0]);
		const tips = M.bone(`fingertips_${n}`, `fingers_${n}`, [sx, knuckY - P.fingerLen * 0.52, 0]);
		const fz = [-1.25, -0.42, 0.42, 1.25].map(v => v * hz / 1.75), fl = [0.95, 1.05, 1.0, 0.82];
		fz.forEach((z, i) => {
			const len = P.fingerLen * fl[i];
			M.box(fing, sx - 0.55 * b, knuckY - len * 0.55, z - 0.36 * b, sx + 0.55 * b, knuckY + 0.3, z + 0.36 * b, 'skin', { paint: 'finger' });
			M.box(tips, sx - 0.5 * b, knuckY - P.fingerLen * 0.52 - len * 0.5, z - 0.33 * b, sx + 0.5 * b, knuckY - P.fingerLen * 0.52 + 0.25, z + 0.33 * b, 'skin', { paint: 'finger' });
		});
		M.bone(`weapon_${n}`, `hand_${n}`, [sx - s * 1.5, knuckY + 0.9, 0]);
	}
	// gear attachment bones every Jotun has (the gameplay renderers look them up by name)
	M.bone('crystals', 'chest', [0, chestTop - 4, 5]);
	M.bone('mantle', 'chest', [0, chestTop, 2]);
	M.bone('helm', 'head', [0, A.browY, 0]);
	return M;
}
// grip curl for a weapon held in the fist (adds to the pose)
const FIST = n => { const s = n === 'r' ? -1 : 1; return { [`fingers_${n}`]: [0, 0, s * 95], [`fingertips_${n}`]: [0, 0, s * 100] }; };
const OPEN = (n, k = 1) => { const s = n === 'r' ? -1 : 1; return { [`fingers_${n}`]: [0, 0, s * 14 * k], [`fingertips_${n}`]: [0, 0, s * 18 * k] }; };

// An ice spear along the weapon bone's -Z axis: shaft of dark frozen ice, leather grip, a broad leaf blade with glowing edges.
function iceSpear(M, bone, fwd, back, k = 1, opts = {}) {
	const [wx, wy] = M.B[bone].pivot;
	const sh = 0.62 * k;
	M.box(bone, wx - sh, wy - sh, -fwd, wx + sh, wy + sh, back, 'ice_shaft', { seed: 5 });
	M.box(bone, wx - sh - 0.22, wy - sh - 0.22, -2.8 * k, wx + sh + 0.22, wy + sh + 0.22, 2.8 * k, 'strap');
	M.box(bone, wx - sh - 0.2, wy - sh - 0.2, back * 0.45, wx + sh + 0.2, wy + sh + 0.2, back * 0.45 + 1.4 * k, 'strap');
	M.box(bone, wx - sh - 0.3, wy - sh - 0.3, -fwd - 0.4, wx + sh + 0.3, wy + sh + 0.3, -fwd + 1.2 * k, 'ice', { seed: 6 }); // collar
	const widths = opts.widths || [1.8, 3.0, 3.6, 3.4, 2.8, 2.0, 1.2, 0.5];
	const seg = (opts.bladeLen || 16) * k / widths.length;
	widths.forEach((w, i) => {
		const z0 = -fwd - i * seg;
		M.box(bone, wx - w * k / 2, wy - 0.42 * k, z0 - seg - 0.05, wx + w * k / 2, wy + 0.42 * k, z0 + 0.05, 'blade', { seed: 20 + i, tag: i > widths.length - 3 ? 'tip' : null });
	});
	M.box(bone, wx - 0.35 * k, wy - 0.62 * k, -fwd - seg * (widths.length - 1), wx + 0.35 * k, wy + 0.62 * k, -fwd + 0.2, 'ice_dim', { seed: 40 }); // mid rib
	for (const sx of [-1, 1]) M.stick(bone, [wx + sx * 1.4 * k, wy, -fwd + 0.6], [sx * 0.8, 0, 1], 3.2 * k, 0.55 * k, 'ice', { seed: 41 + sx }); // barbs
	M.box(bone, wx - 0.45 * k, wy - 0.45 * k, back, wx + 0.45 * k, wy + 0.45 * k, back + 2.8 * k, 'ice', { seed: 44, tag: 'tip' }); // butt spike
}

// ================================================================ Jotun Raider
function buildRaider() {
	const M = new Model('jotun_raider', { TW: 512 });
	M.pal = PALS.raider; M.furCol = [64, 54, 46]; M.furTip = [146, 132, 112]; M.hairCol = [18, 20, 32];
	const P = Object.assign({}, BASE);
	jotunRig(M, P);
	const A = M.A;
	// topknot + shaved-side crest, braid down the back
	const hb = A.hb, top = A.headTop - 0.3;
	M.box('head', -1.3, top - 0.35, -3.4, 1.3, top + 0.55, 3.0, 'hair');
	M.box('head', -1.15, top + 0.2, 0.2, 1.15, top + 2.3, 2.6, 'hair');
	M.box('head', -1.35, top + 0.9, 0.0, 1.35, top + 1.4, 2.8, 'strap');
	const braid = M.bone('braid', 'head', [0, top + 0.6, 2.6]);
	M.tube(braid, [[0, top + 0.8, 2.4], [0, top - 1.5, 4.0], [0, hb + 0.5, 4.6], [0, hb - 3.5, 4.4], [0, hb - 6.5, 4.6]], [1.25, 1.1, 0.95, 0.8], 'hair');
	M.cbox(braid, [0, hb - 3.4, 4.45], [1.4, 0.6, 1.4], 'iron');
	// iron torc around the neck
	M.box('neck', -2.9, A.chestTop - 0.2, -2.6, 2.9, A.chestTop + 0.75, 3.1, 'iron');
	// baldric across the chest (right shoulder to left hip) + back
	M.stick('chest', [-6.6, A.chestTop - 0.3, -4.75], [11.5, -(A.chestTop - A.chestBot) + 1.8, 0], 15.5, [1.5, 0.35], 'strap');
	M.stick('chest', [-6.6, A.chestTop - 0.3, 4.75], [11.5, -(A.chestTop - A.chestBot) + 1.8, 0], 15.5, [1.5, 0.35], 'strap');
	// belt + kilt
	const hipY = A.hipY;
	M.box('hips', -6.1, A.pelvisTop - 2.0, -3.75, 6.1, A.pelvisTop - 0.2, 3.75, 'leather', { paint: 'belt' });
	M.box('hips', -1.3, A.pelvisTop - 2.3, -4.05, 1.3, A.pelvisTop + 0.1, -3.7, 'iron');
	M.box('hips', -6.2, A.pelvisTop - 3.4, -3.85, 6.2, A.pelvisTop - 1.9, 3.85, 'fur');
	const flapF = M.bone('flap_f', 'hips', [0, A.pelvisTop - 2, -3.9]);
	M.box(flapF, -2.2, hipY - 9.5, -4.25, 2.2, A.pelvisTop - 2.1, -3.85, 'leather', { paint: 'flap' });
	M.box(flapF, -2.3, hipY - 10.3, -4.3, 2.3, hipY - 9.3, -3.8, 'fur');
	const flapB = M.bone('flap_b', 'hips', [0, A.pelvisTop - 2, 3.9]);
	M.box(flapB, -3.0, hipY - 10.5, 3.85, 3.0, A.pelvisTop - 2.1, 4.25, 'leather', { paint: 'flap' });
	M.box(flapB, -3.1, hipY - 11.3, 3.8, 3.1, hipY - 10.3, 4.3, 'fur');
	for (const [s, n] of SIDES) {
		const lx = s * A.legX;
		// overlapping leather tassets on each thigh, fur hem
		M.box(`thigh_${n}`, ...[sxr(s, A.legX - 2.2, A.legX + 3.0)[0], hipY - 8.2, -3.35, sxr(s, A.legX - 2.2, A.legX + 3.0)[1], hipY + 0.6, -2.95], 'leather', { paint: 'flap', rot: [-4, 0, 0], pivot: [lx, hipY, -3] });
		M.box(`thigh_${n}`, ...[sxr(s, A.legX + 2.75, A.legX + 3.2)[0], hipY - 8.6, -2.9, sxr(s, A.legX + 2.75, A.legX + 3.2)[1], hipY + 0.6, 3.0], 'leather', { paint: 'flap', rot: [0, 0, -s * 4], pivot: [s * (A.legX + 3), hipY, 0] });
		M.box(`thigh_${n}`, ...[sxr(s, A.legX - 2.3, A.legX + 3.3)[0], hipY - 9.4, -3.5, sxr(s, A.legX - 2.3, A.legX + 3.3)[1], hipY - 8.2, 3.1], 'fur', { rot: [-4, 0, -s * 4], pivot: [lx, hipY, 0] });
		// fur shin wraps + leather foot wraps
		M.box(`shin_${n}`, lx - 2.45, A.ankleY - 0.6, -2.55, lx + 2.45, A.ankleY + 5.5, 2.55, 'fur', { paint: 'wrap' });
		M.box(`shin_${n}`, lx - 2.25, A.ankleY + 5.3, -2.35, lx + 2.25, A.ankleY + 6.2, 2.35, 'strap');
		M.box(`foot_${n}`, lx - 2.3, 1.0, -4.6, lx + 2.3, 1.9, 1.5, 'strap');
		// bracers: leather with iron bands
		const sx = s * A.shX;
		M.box(`forearm_${n}`, sx - 2.35, A.wristY - 0.1, -2.35, sx + 2.35, A.wristY + 6.6, 2.35, 'leather', { paint: 'bracer' });
		M.box(`forearm_${n}`, sx - 2.5, A.wristY + 0.4, -2.5, sx + 2.5, A.wristY + 1.2, 2.5, 'iron');
		M.box(`forearm_${n}`, sx - 2.5, A.wristY + 5.2, -2.5, sx + 2.5, A.wristY + 6.0, 2.5, 'iron');
		// upper-arm band
		M.box(`arm_${n}`, sx - 2.4, A.elbowY + 5.0, -2.45, sx + 2.4, A.elbowY + 6.0, 2.45, 'iron');
	}
	// an ice pauldron grown over the left shoulder
	crystals(M, 'shoulder_l', [A.shX - 0.5, A.shY + 2.2, 0.4], [0.55, 1, 0.25], 4, 4.6, 1.6, 11);
	M.box('shoulder_l', A.shX - 3.0, A.shY + 0.6, -2.6, A.shX + 2.9, A.shY + 2.6, 2.7, 'ice_dim', { seed: 3 });
	// ice spear in the right hand (built along -Z: tip forward when the hand hangs)
	iceSpear(M, 'weapon_r', 30, 20, 1.0);
	return M;
}

// ================================================================ Frost Shaman
function buildShaman() {
	const M = new Model('frost_shaman', { TW: 512 });
	M.pal = PALS.shaman; M.clothCol = [34, 50, 92]; M.cloth2Col = [78, 44, 112]; M.trimCol = [150, 166, 206];
	const P = Object.assign({}, BASE, { bulk: 0.9, chestW: 16.4, ribW: 11.4, waistW: 8.2, armW: 3.6, foreW: 3.6, headH: 8.0, fingerLen: 5.0 });
	jotunRig(M, P);
	const A = M.A, cw = A.cw;
	// robe torso with a deep V opening down to the sash (cut out of the texture, the ridged chest shows through)
	M.box('chest', -cw - 0.3, A.chestBot - 0.5, -4.7, cw + 0.3, A.chestTop + 0.4, 4.2, 'cloth', { paint: 'tunic' });
	M.box('waist', -4.4, A.pelvisTop - 0.8, -3.35, 4.4, A.chestBot + 0.8, 3.35, 'cloth2', { paint: 'sash' });
	M.box(M.bone('sash', 'waist', [0, A.pelvisTop, -3.5]), -1.0, A.pelvisTop - 6.5, -3.75, 0.6, A.pelvisTop + 0.2, -3.3, 'cloth2', { paint: 'sash' }); // sash tail
	// short purple shoulder mantle + hood bunched behind the neck
	M.box('chest', -cw - 1.1, A.chestTop - 3.2, -4.9, cw + 1.1, A.chestTop + 1.2, 4.9, 'cloth2', { paint: 'mantle' });
	M.box('chest', -3.2, A.chestTop + 0.4, 1.4, 3.2, A.chestTop + 3.4, 5.0, 'cloth', { paint: 'hood' });
	M.box('chest', -cw * 0.8, A.chestBot + 2, 4.25, cw * 0.8, A.chestTop - 2.6, 5.0, 'cloth2', { paint: 'mantle' });
	// long A-line robe skirt on the hips (legs hidden under it)
	const hy = A.pelvisTop - 1.0, by = A.ankleY - 1.2;
	const skirt = M.bone('skirt', 'hips', [0, hy, 0]);
	M.box(skirt, -6.2, by + 7, -4.3, 6.2, hy, 4.3, 'cloth', { paint: 'skirt' });
	M.box(skirt, -6.9, by, -4.95, 6.9, by + 7.4, 4.95, 'cloth', { paint: 'skirt' });
	M.box(skirt, -7.1, by - 0.3, -5.15, 7.1, by + 1.5, 5.15, 'trim');
	M.box(skirt, -1.3, by + 1.5, -5.15, 1.3, hy - 1, -4.25, 'cloth2', { paint: 'panel' }); // purple front panel
	M.box(skirt, -1.6, by + 1.5, 4.25, 1.6, hy - 1, 5.15, 'cloth2', { paint: 'panel' });
	for (const [s, n] of SIDES) {
		const sx = s * A.shX;
		M.box(`arm_${n}`, sx - 2.25, A.elbowY - 0.4, -2.3, sx + 2.25, A.shY + 1.6, 2.3, 'cloth', { paint: 'sleeve' });
		M.box(`forearm_${n}`, sx - 2.9, A.wristY + 0.2, -2.9, sx + 2.9, A.elbowY + 0.8, 2.9, 'cloth', { paint: 'sleeve' });
		M.box(`forearm_${n}`, sx - 3.1, A.wristY - 0.2, -3.1, sx + 3.1, A.wristY + 1.3, 3.1, 'trim');
		M.box(`hand_${n}`, sx - 1.2, A.wristY - 0.9, -1.95, sx + 1.2, A.wristY - 0.2, 1.95, 'bone'); // bone bracelet
		// ice crystals growing out of the shoulders
		crystals(M, `shoulder_${n}`, [sx - s * 0.5, A.shY + 2.6, 0.6], [s * 0.6, 1, 0.3], 5, 5.2, 1.5, 30 + s * 7);
	}
	// bone necklace hanging into the V
	const nk = [[-2.6, A.chestTop - 0.4], [-2.0, A.chestTop - 1.9], [-1.1, A.chestTop - 3.0], [0, A.chestTop - 3.5], [1.1, A.chestTop - 3.0], [2.0, A.chestTop - 1.9], [2.6, A.chestTop - 0.4]];
	nk.forEach(([x, y], i) => M.cbox('chest', [x, y - 0.6, -4.55], [0.55, i === 3 ? 2.4 : 1.4, 0.5], 'bone', { seed: i }));
	M.cbox('chest', [0, A.chestTop - 5.4, -4.6], [1.6, 1.6, 0.7], 'bone', { paint: 'skull' });
	M.cbox('waist', [3.2, A.pelvisTop - 1.4, -3.6], [1.8, 1.8, 1.0], 'bone', { paint: 'skull' });
	// bone circlet with a brow crystal; long braided white beard
	M.box('head', -3.45, A.browY + 1.0, -4.0, 3.45, A.browY + 1.8, 3.4, 'bone');
	M.stick('head', [0, A.browY + 1.4, A.faceZ - 0.1], [0, 1, -0.25], 2.6, 0.9, 'crystal_hot', { seed: 3 });
	const beard = M.B.beard;
	M.tube(beard, [[0, A.hb + 0.3, A.faceZ + 0.6], [0, A.hb - 2.5, A.faceZ - 0.2], [0, A.hb - 6.5, A.faceZ + 0.0], [0, A.hb - 9.5, A.faceZ + 0.4]], [[1.8, 1.4], [1.3, 1.1], [0.9, 0.8]], 'beard');
	M.cbox(beard, [0, A.hb - 6.4, A.faceZ], [1.3, 0.6, 1.2], 'bone');
	// a frost orb forming in front of the open right palm (shown only while casting)
	{ const sp = M.bone('spell_r', 'hand_r', [-A.shX - 2.2, A.knuckY - 1, 0]); crystals(M, sp, [-A.shX - 2.6, A.knuckY - 1.5, 0], [-1, 0, 0], 9, 5.0, 1.8, 77, 'crystal_hot', 1.4); }
	// staff in the left hand: frozen wood, bone prongs cradling a big glowing crystal, charms
	const [wx, wy] = M.B.weapon_l.pivot;
	M.box('weapon_l', wx - 0.7, wy - 0.7, -30, wx + 0.7, wy + 0.7, 34, 'wood');
	M.box('weapon_l', wx - 0.9, wy - 0.9, -2.5, wx + 0.9, wy + 0.9, 2.5, 'strap');
	M.box('weapon_l', wx - 1.0, wy - 1.0, -31.5, wx + 1.0, wy + 1.0, -29.5, 'iron');
	for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) M.tube('weapon_l', [[wx + dx * 0.6, wy + dy * 0.6, -30.5], [wx + dx * 2.4, wy + dy * 2.4, -33.5], [wx + dx * 2.0, wy + dy * 2.0, -37.5], [wx + dx * 0.5, wy + dy * 0.5, -39.5]], [0.7, 0.6, 0.45], 'horn');
	M.cbox('weapon_l', [wx, wy, -35], [2.6, 2.6, 5.2], 'crystal_hot', { seed: 9 });
	M.stick('weapon_l', [wx, wy, -37.2], [0, 0, -1], 3.6, 1.5, 'crystal_hot', { seed: 10, tag: 'tip' });
	for (const dx of [-1, 1]) { M.stick('weapon_l', [wx + dx * 1.0, wy, -29], [dx * 0.25, -1, 0.1], 4.0, 0.25, 'strap'); M.cbox('weapon_l', [wx + dx * 2.0, wy - 4.2, -28.6], [0.7, 1.4, 0.7], 'bone', { seed: 7 + dx }); }
	return M;
}

// ================================================================ Frost Giant Warlord
function buildWarlord() {
	const M = new Model('frost_giant_warlord', { TW: 512 });
	M.pal = PALS.warlord; M.furCol = [52, 44, 40]; M.furTip = [140, 126, 108]; M.ironCol = [86, 94, 108];
	const P = Object.assign({}, BASE, { bulk: 1.12, chestW: 19.2, ribW: 13.0, waistW: 9.4, armW: 4.3, foreW: 4.5, thighW: 5.6, headW: 6.8, headH: 7.8, neckH: 2.8 });
	jotunRig(M, P);
	const A = M.A, cw = A.cw, hw = 3.4;
	// helm: iron cap, nasal and cheek guards, great curved horns, a crest of short spikes
	const helm = M.B.helm;
	M.box(helm, -hw - 0.4, A.browY + 0.4, -4.35, hw + 0.4, A.headTop + 0.7, 3.8, 'iron', { paint: 'helm' });
	M.box(helm, -hw - 0.55, A.browY + 0.1, -4.6, hw + 0.55, A.browY + 1.3, 3.95, 'iron_d');
	M.box(helm, -0.45, A.eyeY - 1.4, A.faceZ - 1.25, 0.45, A.browY + 1.0, A.faceZ - 0.5, 'iron');
	for (const [s] of SIDES) {
		M.box(helm, ...[sxr(s, hw - 0.1, hw + 0.55)[0], A.hb + 1.4, -3.4, sxr(s, hw - 0.1, hw + 0.55)[1], A.browY + 0.6, 1.0], 'iron');
		const h0 = [s * (hw + 0.2), A.browY + 1.8, 0.2];
		const pts = [h0, [s * (hw + 2.6), A.browY + 2.6, 0.6], [s * (hw + 5.0), A.browY + 4.4, 0.2], [s * (hw + 6.2), A.browY + 7.2, -1.0], [s * (hw + 6.0), A.browY + 10.0, -2.6], [s * (hw + 5.0), A.browY + 12.0, -3.8]];
		const cubes = M.tube(helm, pts, [[2.2, 2.2], [1.9, 1.9], [1.5, 1.5], [1.1, 1.1], [0.6, 0.6]], 'horn');
		cubes[cubes.length - 1].tag = 'tip'; cubes[cubes.length - 2].tag = 'tip';
		M.cbox(helm, [s * (hw + 0.4), A.browY + 1.8, 0.2], [1.4, 2.8, 2.8], 'iron_d');
	}
	for (let i = 0; i < 4; i++) M.stick(helm, [0, A.headTop + 0.5, -2.6 + i * 1.8], [0, 1, 0.25], 1.8 - i * 0.2, 0.8, 'iron');
	// braided beard + moustache
	const beard = M.B.beard;
	M.box(beard, -2.9, A.hb - 2.6, A.faceZ - 0.5, 2.9, A.hb + 1.2, A.faceZ + 3.2, 'beard');
	M.box('head', -2.4, A.mouthY + 0.15, A.faceZ - 0.75, 2.4, A.mouthY + 0.95, A.faceZ - 0.2, 'beard');
	for (const [s] of SIDES) {
		M.tube(beard, [[s * 1.3, A.hb - 2.2, A.faceZ + 0.3], [s * 1.5, A.hb - 6.0, A.faceZ + 0.3], [s * 1.4, A.hb - 9.5, A.faceZ + 0.8]], [[1.3, 1.2], [1.0, 1.0]], 'beard');
		M.cbox(beard, [s * 1.5, A.hb - 5.6, A.faceZ + 0.3], [1.6, 0.8, 1.6], 'iron');
		M.cbox(beard, [s * 1.4, A.hb - 9.0, A.faceZ + 0.7], [1.3, 0.7, 1.3], 'bone');
	}
	// fur mantle over the shoulders and back + fur cloak with a ragged hem
	const mantle = M.B.mantle;
	M.box(mantle, -cw - 1.6, A.chestTop - 3.0, 2.0, cw + 1.6, A.chestTop + 2.4, 6.4, 'fur', { paint: 'mantle' });
	for (const [s] of SIDES) M.box(mantle, ...[sxr(s, 2.6, cw - 0.4)[0], A.chestTop - 1.8, -5.2, sxr(s, 2.6, cw - 0.4)[1], A.chestTop + 2.0, 2.2], 'fur');
	M.cbox(mantle, [0, A.chestTop - 1.0, -5.1], [3.0, 2.6, 1.6], 'bone', { paint: 'skull' });
	const cape = M.bone('cape', 'mantle', [0, A.chestTop - 1, 5.5], [6, 0, 0]);
	M.box(cape, -cw - 0.6, A.hipY - 6, 4.6, cw + 0.6, A.chestTop - 1.0, 6.0, 'fur', { paint: 'cape' });
	for (let i = 0; i < 9; i++) { const x = -cw + (i + 0.5) * (2 * cw / 9); M.box(cape, x - 1.1, A.hipY - 8.5 + (i % 3) * 0.9, 4.7, x + 1.1, A.hipY - 5.5, 5.9, 'fur'); }
	// leather harness with an iron boss
	M.stick('chest', [-cw + 1.5, A.chestTop - 0.4, -5.55], [2 * cw - 3, -(A.chestTop - A.chestBot) + 2, 0], 21, [1.6, 0.4], 'strap');
	M.stick('chest', [cw - 1.5, A.chestTop - 0.4, -5.55], [-(2 * cw - 3), -(A.chestTop - A.chestBot) + 2, 0], 21, [1.6, 0.4], 'strap');
	M.cbox('chest', [0, A.chestTop - 6.8, -5.85], [3.2, 3.2, 0.7], 'iron', { paint: 'boss' });
	// pauldrons: three stepped iron plates with bone spikes and fur under them
	for (const [s, n] of SIDES) {
		const sx = s * A.shX;
		const sh = `shoulder_${n}`;
		M.box(sh, sx - 3.3, A.shY + 1.2, -3.4, sx + 3.6, A.shY + 3.4, 3.4, 'fur');
		M.box(sh, ...[sxr(s, A.shX - 2.6, A.shX + 3.4)[0], A.shY + 0.6, -3.1, sxr(s, A.shX - 2.6, A.shX + 3.4)[1], A.shY + 3.1, 3.1], 'iron', { rot: [0, 0, s * 14], pivot: [sx, A.shY + 2, 0] });
		M.box(sh, ...[sxr(s, A.shX - 1.2, A.shX + 3.8)[0], A.shY - 1.4, -3.3, sxr(s, A.shX - 1.2, A.shX + 3.8)[1], A.shY + 0.8, 3.3], 'iron', { rot: [0, 0, s * 22], pivot: [sx, A.shY, 0] });
		M.box(sh, ...[sxr(s, A.shX + 0.2, A.shX + 4.0)[0], A.shY - 3.2, -3.4, sxr(s, A.shX + 0.2, A.shX + 4.0)[1], A.shY - 1.2, 3.4], 'iron', { rot: [0, 0, s * 30], pivot: [sx, A.shY - 2, 0] });
		for (const z of [-1.4, 1.4]) M.stick(sh, [sx + s * 0.6, A.shY + 3.0, z], [s * 0.35, 1, 0], 3.4, 0.9, 'bone');
		// vambraces with a bone spike, iron greaves over fur wraps, iron tassets
		M.box(`forearm_${n}`, sx - 2.6, A.wristY - 0.1, -2.6, sx + 2.6, A.wristY + 6.8, 2.6, 'iron', { paint: 'vambrace' });
		M.stick(`forearm_${n}`, [sx + s * 2.5, A.wristY + 4.5, 0], [s * 1, 0.6, 0], 2.4, 0.8, 'bone');
		const lx = s * A.legX;
		M.box(`shin_${n}`, lx - 2.6, A.ankleY - 0.6, -2.65, lx + 2.6, A.ankleY + 6.0, 2.65, 'fur');
		M.box(`shin_${n}`, lx - 2.3, A.ankleY + 1.5, -3.1, lx + 2.3, A.kneeY + 1.2, -1.6, 'iron', { paint: 'greave' });
		for (let k = 0; k < 3; k++) M.box(`thigh_${n}`, ...[sxr(s, A.legX - 2.6, A.legX + 3.4)[0], A.hipY - 3.6 - k * 2.6, -3.7 + k * 0.1, sxr(s, A.legX - 2.6, A.legX + 3.4)[1], A.hipY - 1.0 - k * 2.6, 3.4], 'iron', { paint: 'tasset', rot: [-4, 0, -s * 5], pivot: [lx, A.hipY, 0] });
	}
	M.box('hips', -6.7, A.pelvisTop - 2.1, -4.0, 6.7, A.pelvisTop - 0.1, 4.0, 'leather', { paint: 'belt' });
	M.cbox('hips', [0, A.pelvisTop - 1.1, -4.1], [3.2, 2.6, 0.6], 'bone', { paint: 'skull' });
	M.box(M.bone('flap_f', 'hips', [0, A.pelvisTop - 2, -3.9]), -2.6, A.hipY - 10, -4.1, 2.6, A.pelvisTop - 2.0, -3.6, 'leather_d', { paint: 'flap' });
	M.box(M.bone('flap_b', 'hips', [0, A.pelvisTop - 2, 3.9]), -3.4, A.hipY - 11, 3.6, 3.4, A.pelvisTop - 2.0, 4.1, 'leather_d', { paint: 'flap' });
	// ice greatsword (built along -Z from the fist)
	const [wx, wy] = M.B.weapon_r.pivot;
	M.box('weapon_r', wx - 0.75, wy - 0.75, -4.5, wx + 0.75, wy + 0.75, 5.5, 'strap');
	M.box('weapon_r', wx - 1.2, wy - 1.2, 5.5, wx + 1.2, wy + 1.2, 7.5, 'iron');
	M.cbox('weapon_r', [wx, wy, 8.2], [1.6, 1.6, 1.6], 'crystal_hot', { seed: 2 });
	M.box('weapon_r', wx - 4.5, wy - 1.0, -6.5, wx + 4.5, wy + 1.0, -4.5, 'iron');
	for (const sx of [-1, 1]) M.stick('weapon_r', [wx + sx * 4.0, wy, -5.5], [sx * 1, 0, -0.6], 3.2, [1.2, 1.2], 'crystal', { seed: 3 + sx, tag: 'tip' });
	const bw = [5.6, 5.8, 5.6, 5.3, 5.0, 4.6, 4.0, 3.2, 2.2, 1.0];
	const seg = 3.7;
	bw.forEach((w, i) => M.box('weapon_r', wx - w / 2, wy - 0.6, -6.5 - (i + 1) * seg - 0.05, wx + w / 2, wy + 0.6, -6.5 - i * seg + 0.05, 'blade', { seed: 10 + i, tag: i > 7 ? 'tip' : null }));
	M.box('weapon_r', wx - 0.7, wy - 0.75, -6.5 - 8 * seg, wx + 0.7, wy + 0.75, -6.4, 'ice_shaft', { seed: 30 }); // fuller ridge
	return M;
}

// ================================================================ Ice Brute
function buildBrute() {
	const M = new Model('ice_brute', { TW: 512 });
	M.pal = PALS.brute; M.leatherCol = [70, 52, 44];
	const P = Object.assign({}, BASE, {
		bulk: 1.3, chestW: 21, ribW: 14.5, waistW: 10.5, chestD: 8.6, chestH: 13, armW: 4.6, foreW: 5.6, armD: 4.4,
		thighW: 5.8, thighD: 5.6, shinW: 4.4, shinLen: 12.5, thighLen: 13.0, legX: 4.0, pelvisW: 14, neckH: 2.6, headW: 7.4, headH: 8.0, headD: 7.6,
		upperLen: 12, foreLen: 11.5, footW: 4.6,
	});
	jotunRig(M, P);
	const A = M.A, cw = A.cw;
	// heavy brow + ice tusks from the under-bite
	M.box('head', -3.3, A.browY - 0.2, A.faceZ - 0.9, 3.3, A.browY + 1.1, A.faceZ + 0.3, 'skin', { paint: 'brow' });
	for (const [s] of SIDES) M.stick('jaw', [s * 1.4, A.mouthY - 0.3, A.faceZ - 0.05], [s * 0.15, 1, -0.2], 2.2, 0.7, 'crystal', { seed: 70 + s, tag: 'tip' });
	// white frost-fur over the shoulders and down the back; hide loincloth
	M.box('chest', -cw - 0.8, A.chestTop - 3.5, -1.5, cw + 0.8, A.chestTop + 2.4, 6.4, 'fur_w', { paint: 'mane' });
	M.box('neck', -3.6, A.chestTop - 0.5, -0.6, 3.6, A.hb + 3.5, 3.8, 'fur_w');
	for (const [s, n] of SIDES) M.box(`shoulder_${n}`, A.shX * s - 3.4, A.shY + 0.8, -3.2, A.shX * s + 3.4, A.shY + 3.6, 3.8, 'fur_w');
	M.box('hips', -7.6, A.pelvisTop - 2.4, -4.9, 7.6, A.pelvisTop - 0.2, 4.9, 'leather', { paint: 'belt' });
	M.box(M.bone('flap_f', 'hips', [0, A.pelvisTop - 2.3, -4.7]), -3.6, A.hipY - 9, -5.0, 3.6, A.pelvisTop - 2.3, -4.4, 'fur_w');
	M.box(M.bone('flap_b', 'hips', [0, A.pelvisTop - 2.3, 4.7]), -4.6, A.hipY - 9.5, 4.4, 4.6, A.pelvisTop - 2.3, 5.0, 'fur_w');
	// crystal growths: big shards down the back, on the shoulders and along the forearms
	const back = M.B.crystals;
	crystals(M, back, [-4.2, A.chestTop - 2.5, 5.2], [-0.5, 0.9, 0.7], 5, 9.5, 2.8, 101);
	crystals(M, back, [4.2, A.chestTop - 3.0, 5.2], [0.5, 0.9, 0.7], 5, 8.5, 2.6, 102);
	crystals(M, back, [0, A.chestBot + 4, 5.0], [0, 0.5, 1], 4, 6.5, 2.2, 103);
	crystals(M, back, [-2.8, A.chestBot + 0.5, 4.6], [-0.4, 0.3, 1], 3, 4.5, 1.6, 104);
	crystals(M, back, [2.8, A.chestBot + 1.0, 4.6], [0.4, 0.3, 1], 3, 4.5, 1.6, 105);
	for (const [s, n] of SIDES) {
		const sx = s * A.shX;
		crystals(M, `shoulder_${n}`, [sx, A.shY + 3.2, 1.0], [s * 0.4, 1, 0.4], 4, 6.0, 2.0, 110 + s);
		// ice gauntlets: the forearm and fist encased in faceted ice
		M.box(`forearm_${n}`, sx - 3.4, A.wristY - 0.3, -3.3, sx + 3.4, A.wristY + 6.0, 3.3, 'ice_dim', { seed: 120 + s, paint: 'gauntlet' });
		crystals(M, `forearm_${n}`, [sx + s * 2.0, A.wristY + 4.0, 0.5], [s * 1, 0.5, 0.3], 4, 5.0, 1.6, 125 + s);
		M.box(`hand_${n}`, sx - 2.6, A.knuckY - 3.4, -3.2, sx + 2.6, A.wristY + 0.3, 3.2, 'ice', { seed: 130 + s, paint: 'gauntlet' });
		for (const z of [-2.1, -0.7, 0.7, 2.1]) M.stick(`hand_${n}`, [sx, A.knuckY - 3.2, z], [0, -1, -0.15], 1.6, 0.9, 'crystal', { seed: 140 + Math.round(z * 3), tag: 'tip' });
		const lx = s * A.legX;
		M.box(`shin_${n}`, lx - 3.1, A.ankleY - 0.5, -3.1, lx + 3.1, A.ankleY + 4.0, 3.1, 'fur_w');
	}
	return M;
}

// ---------------------------------------------------------------- poses (shaman / warlord / brute)
function shamanPoses(M) {
	const stand = Object.assign({
		arm_l: [-16, 0, -4], forearm_l: [-64, 0, 0], weapon_l: [-14, 0, 0],
		arm_r: [-10, 0, 10], forearm_r: [-30, 0, 0], hand_r: [0, 0, 0],
		head: [-2, 0, 0],
	}, FIST('l'), OPEN('r', 1.4));
	L.aimBone(M, stand, 'weapon_l', [0, 0, -1], [0.04, 1, -0.05]);
	const action = Object.assign({
		arm_l: [-20, 0, -112], forearm_l: [-40, 0, 0],
		arm_r: [-60, 0, 20], forearm_r: [-16, 0, 0], hand_r: [-30, 0, 0],
		chest: [-6, 12, 0], head: [-10, -6, 0], jaw: [16, 0, 0],

	}, FIST('l'), OPEN('r', 2.2));
	L.aimBone(M, action, 'weapon_l', [0, 0, -1], [0.15, 1, 0.1]);
	L.reach(M, action, 'hand_r', [-M.A.shX, M.A.knuckY, 0], [-M.A.shX - 7, M.A.shY - 3, -11], [['arm_r', 0], ['arm_r', 1], ['arm_r', 2], ['forearm_r', 0]]);
	return { stand, action };
}
function warlordPoses(M) {
	const A = M.A;
	// sword planted point-down in front, both hands on the pommel
	const stand = Object.assign({ head: [-4, 0, 0], chest: [-3, 0, 0], thigh_r: [0, 0, 5], thigh_l: [0, 0, -5] }, FIST('r'), FIST('l'));
	const grip = [0, A.pelvisTop + 4.5, -10.5];
	L.reach(M, stand, 'hand_r', [-A.shX, A.knuckY + 0.6, 0], grip, [['arm_r', 0], ['arm_r', 2], ['forearm_r', 0], ['arm_r', 1]]);
	L.aimBone(M, stand, 'weapon_r', [0, 0, -1], [0, -1, -0.02]);
	{
		const W = M.world(stand); const pom = L.affApply(W.weapon_r, vadd(M.B.weapon_r.pivot, [0, 0, 6.5]));
		L.reach(M, stand, 'hand_l', [A.shX, A.knuckY + 0.3, 0], vadd(pom, [0.3, 1.6, 0]), [['arm_l', 0], ['arm_l', 2], ['forearm_l', 0], ['arm_l', 1]]);
	}
	const action = Object.assign({
		arm_r: [-170, 0, 8], forearm_r: [-10, 0, 0],
		arm_l: [-40, 0, -50], forearm_l: [-80, 0, 0],
		chest: [-12, 0, 0], head: [-26, 0, 0], jaw: [26, 0, 0],
		thigh_r: [-24, 0, 10], shin_r: [30, 0, 0], foot_r: [-6, 0, 0], thigh_l: [16, 0, -10], shin_l: [6, 0, 0], foot_l: [-22, 0, 0],
	}, FIST('r'), FIST('l'));
	L.aimBone(M, action, 'weapon_r', [0, 0, -1], [0.1, 1, -0.25]);
	return { stand, action };
}
function brutePoses(M) {
	const stand = Object.assign({
		arm_r: [-8, 0, 10], forearm_r: [-22, 0, 0], arm_l: [-8, 0, -10], forearm_l: [-22, 0, 0],
		chest: [-4, 0, 0], head: [-2, 0, 0], thigh_r: [0, 0, 6], thigh_l: [0, 0, -6],
	}, FIST('r'), FIST('l'));
	const action = Object.assign({
		arm_r: [-166, 0, 14], forearm_r: [-34, 0, 0], arm_l: [-166, 0, -14], forearm_l: [-34, 0, 0],
		chest: [-14, 0, 0], waist: [-4, 0, 0], head: [-20, 0, 0], jaw: [30, 0, 0],
		thigh_r: [-12, 0, 12], shin_r: [18, 0, 0], foot_r: [-6, 0, 0], thigh_l: [-12, 0, -12], shin_l: [18, 0, 0], foot_l: [-6, 0, 0],
	}, FIST('r'), FIST('l'));
	return { stand, action };
}

// ================================================================ sheets
// ================================================================ Rime Hound (own quadruped rig)
// Bones: root > body > (neck > head > jaw / ears), body > tail, body > leg_f*/leg_b* > lower_* > paw_*.
function buildHound() {
	const M = new Model('rime_hound', { TW: 512 });
	M.furCol = [64, 80, 104]; M.furTip = [176, 196, 216];
	const root = M.bone('root', null, [0, 0, 0]);
	const body = M.bone('body', 'root', [0, 13, 0]);
	const chestB = M.bone('chest', 'body', [0, 13, -4]);
	M.box(chestB, -3.5, 9.4, -8.4, 3.5, 16.6, -1.4, 'wolf', { paint: 'chest' });           // deep chest
	M.box(body, -2.9, 10.6, -2.0, 2.9, 16.0, 4.4, 'wolf', { paint: 'belly' });            // tucked waist
	M.box(body, -3.1, 10.4, 3.6, 3.1, 16.3, 8.6, 'wolf', { paint: 'hips' });              // haunches
	M.box(chestB, -3.0, 8.8, -8.0, 3.0, 10.0, -3.0, 'wolf_pale');                            // chest fur
	// spine crystals, biggest over the shoulders
	[[-5.5, 5.0, 1.5], [-2.5, 4.4, 1.4], [0.6, 3.6, 1.2], [3.4, 2.8, 1.0], [6.2, 2.0, 0.85]].forEach(([z, len, w], i) =>
		crystals(M, body, [0, 16.2 + (i === 0 ? 0.3 : 0), z], [0, 1, 0.55], 2, len, w, 200 + i, 'crystal', 0.3));
	const neck = M.bone('neck', 'chest', [0, 14.5, -7]);
	M.stick(neck, [0, 13.0, -6.5], [0, 0.75, -0.66], 6.5, [5.0, 4.6], 'wolf', { paint: 'neck' });
	M.box(M.bone('ruff', 'neck', [0, 18, -6]), -4.3, 11.2, -10.0, 4.3, 18.6, -4.2, 'wolf_pale', { paint: 'ruff' });       // icy mane
	crystals(M, neck, [0, 18.4, -6.5], [0, 1, 0.7], 3, 5.5, 1.5, 220, 'crystal', 0.35);
	for (const [s] of SIDES) {
		crystals(M, neck, [s * 3.6, 17.2, -5.6], [s * 0.45, 0.8, 0.8], 3, 4.0, 1.2, 230 + s, 'crystal', 0.3);
		crystals(M, neck, [s * 3.8, 14.0, -5.5], [s * 0.5, 0.2, 0.9], 2, 3.0, 1.0, 240 + s, 'crystal', 0.3);
	}
	const head = M.bone('head', 'neck', [0, 18.5, -10.5]);
	M.box(head, -2.7, 16.8, -14.0, 2.7, 21.6, -9.0, 'wolf', { paint: 'wskull' });
	M.box(head, -1.55, 16.8, -18.6, 1.55, 19.5, -13.8, 'wolf', { paint: 'snout' });
	M.box(head, -0.9, 18.5, -19.15, 0.9, 19.7, -18.2, 'nose');
	M.box(head, -2.75, 20.35, -14.45, 2.75, 21.15, -13.6, 'wolf', { paint: 'brow' });
	M.box(head, -3.3, 16.2, -12.6, 3.3, 19.8, -9.2, 'wolf_pale', { paint: 'cheek' });
	for (const [s] of SIDES) {
		M.stick(M.bone(`ear_${s < 0 ? 'r' : 'l'}`, 'head', [s * 1.7, 21.2, -10.6]), [s * 1.7, 21.2, -10.6], [s * 0.25, 1, 0.4], 3.4, [1.7, 0.8], 'wolf', { paint: 'ear' });
		M.box(head, s * 1.05 - 0.28, 15.9, -18.3, s * 1.05 + 0.28, 16.85, -17.8, 'tooth');
	}
	const jaw = M.bone('jaw', 'head', [0, 17.0, -11.5]);
	M.box(jaw, -1.35, 15.6, -18.1, 1.35, 16.85, -11.2, 'wolf', { paint: 'jaw' });
	for (const [s] of SIDES) M.box(jaw, s * 0.95 - 0.25, 16.8, -17.8, s * 0.95 + 0.25, 17.5, -17.3, 'tooth');
	const tail = M.bone('tail', 'body', [0, 15.4, 8.4], [-20, 0, 0]);
	M.tube(tail, [[0, 15.2, 8.0], [0, 14.4, 11.6]], [[2.6, 2.6]], 'wolf', { paint: 'tail' });
	M.tube(M.bone('tail_tip', 'tail', [0, 14.4, 11.6]), [[0, 14.6, 11.2], [0, 12.6, 14.2], [0, 10.6, 15.4]], [[2.4, 2.4], [1.9, 1.9]], 'wolf', { paint: 'tail' });
	crystals(M, tail, [0, 14.8, 11.0], [0, 1, 0.6], 2, 2.6, 0.9, 250, 'crystal', 0.3);
	for (const [s, n] of SIDES) {
		const x = s * 2.4;
		const lf = M.bone(`leg_f${n}`, 'body', [x, 12.6, -5.2]);
		M.box(lf, x - 1.3, 6.2, -6.5, x + 1.3, 13.4, -3.8, 'wolf', { paint: 'leg' });
		const lof = M.bone(`lower_f${n}`, `leg_f${n}`, [x, 6.6, -5.2]);
		M.box(lof, x - 0.95, 1.3, -5.95, x + 0.95, 7.0, -4.3, 'wolf', { paint: 'leg' });
		const pf = M.bone(`paw_f${n}`, `lower_f${n}`, [x, 1.4, -5.1]);
		M.box(pf, x - 1.25, 0, -7.4, x + 1.25, 1.65, -4.2, 'wolf', { paint: 'paw' });
		for (const dx of [-0.75, 0, 0.75]) M.box(pf, x + dx - 0.2, 0, -8.0, x + dx + 0.2, 0.6, -7.3, 'claw');
		const xb = s * 2.5;
		const lb = M.bone(`leg_b${n}`, 'body', [xb, 13.6, 6.0]);
		M.box(lb, xb - 1.55, 7.4, 3.8, xb + 1.55, 15.0, 8.8, 'wolf', { paint: 'leg' });
		const lob = M.bone(`lower_b${n}`, `leg_b${n}`, [xb, 8.0, 7.6], [-22, 0, 0]);
		M.box(lob, xb - 0.95, 1.3, 6.9, xb + 0.95, 8.4, 8.6, 'wolf', { paint: 'leg' });
		const pb = M.bone(`paw_b${n}`, `lower_b${n}`, [xb, 1.4, 7.8], [22, 0, 0]);
		M.box(pb, xb - 1.25, 0, 5.6, xb + 1.25, 1.65, 8.9, 'wolf', { paint: 'paw' });
		for (const dx of [-0.75, 0, 0.75]) M.box(pb, xb + dx - 0.2, 0, 5.0, xb + dx + 0.2, 0.6, 5.7, 'claw');
	}
	return M;
}
function houndPoses(M) {
	const stand = { head: [6, 0, 0], neck: [4, 0, 0], tail: [6, 0, 0], jaw: [6, 0, 0] };
	const action = {
		$root: [0, 6, 0], body: [-14, 0, 0], neck: [-6, 0, 0], head: [-4, 0, 0], jaw: [38, 0, 0], tail: [-34, 0, 0],
		leg_fr: [-62, 0, 0], lower_fr: [26, 0, 0], paw_fr: [30, 0, 0], leg_fl: [-74, 0, 0], lower_fl: [30, 0, 0], paw_fl: [26, 0, 0],
		leg_br: [58, 0, 0], lower_br: [34, 0, 0], paw_br: [30, 0, 0], leg_bl: [48, 0, 0], lower_bl: [30, 0, 0], paw_bl: [40, 0, 0],
	};
	return { stand, action };
}

// ================================================================ Casket of Ancient Winters (prop; also Laufey's left hand)
function addCasket(M, parent, base, k, pre = 'casket') {
	const [bx, by, bz] = base;
	const P = (x, y, z) => [bx + x * k, by + y * k, bz + z * k];
	const cb = (b, x0, y0, z0, x1, y1, z1, mat, o = {}) => M.box(b, bx + x0 * k, by + y0 * k, bz + z0 * k, bx + x1 * k, by + y1 * k, bz + z1 * k, mat, o);
	const body = M.bone(pre, parent, P(0, 0, 0));
	cb(body, -8.6, 0.9, -6.1, 8.6, 2.3, 6.1, 'rune', { paint: 'plinth' });
	for (const sx of [-1, 1]) for (const sz of [-1, 1]) cb(body, sx * 8.6 - 1.0, 0, sz * 6.1 - 1.0, sx * 8.6 + 1.0, 1.0, sz * 6.1 + 1.0, 'iron_d', { tag: 'sole' });
	cb(body, -7.3, 2.2, -5.0, 7.3, 10.3, 5.0, 'casket_glass');
	cb(body, -8.1, 2.1, -5.65, 8.1, 3.2, 5.65, 'rune', { paint: 'band' });
	cb(body, -8.3, 9.4, -5.8, 8.3, 10.8, 5.8, 'rune', { paint: 'band' });
	for (const sx of [-1, 1]) for (const sz of [-1, 1]) cb(body, sx > 0 ? 6.9 : -8.5, 1.8, sz > 0 ? 4.6 : -6.2, sx > 0 ? 8.5 : -6.9, 11.0, sz > 0 ? 6.2 : -4.6, 'rune', { paint: 'post' });
	for (const sz of [-1, 1]) cb(body, -0.65, 3.1, sz > 0 ? 4.85 : -5.5, 0.65, 9.5, sz > 0 ? 5.5 : -4.85, 'rune', { paint: 'mullion' });
	for (const sx of [-1, 1]) {
		cb(body, sx > 0 ? 7.15 : -7.75, 3.1, -0.55, sx > 0 ? 7.75 : -7.15, 9.5, 0.55, 'rune', { paint: 'mullion' });
		// side handle: a ring hanging from two lugs
		cb(body, sx > 0 ? 8.4 : -9.4, 6.4, -2.2, sx > 0 ? 9.4 : -8.4, 7.1, -1.5, 'iron');
		cb(body, sx > 0 ? 8.4 : -9.4, 6.4, 1.5, sx > 0 ? 9.4 : -8.4, 7.1, 2.2, 'iron');
		cb(body, sx > 0 ? 9.2 : -9.9, 4.2, -2.4, sx > 0 ? 9.9 : -9.2, 6.6, 2.4, 'iron');
	}
	cb(body, -1.6, 4.6, -5.9, 1.6, 8.2, -5.0, 'rune', { paint: 'lock' });
	const lid = M.bone(`${pre}_lid`, pre, P(0, 10.8, 6.0));
	cb(lid, -8.7, 10.8, -6.4, 8.7, 12.3, 6.4, 'rune', { paint: 'band' });
	cb(lid, -7.0, 12.2, -4.8, 7.0, 13.9, 4.8, 'casket_glass');
	cb(lid, -7.4, 12.2, -5.2, 7.4, 12.8, 5.2, 'iron_d');
	cb(lid, -0.7, 13.8, -4.8, 0.7, 14.6, 4.8, 'rune', { paint: 'band' });
	cb(lid, -4.8, 13.8, -0.7, 4.8, 14.6, 0.7, 'rune', { paint: 'band' });
	M.stick(lid, P(0, 14.2, 0), [0, 1, 0], 3.2 * k, 1.8 * k, 'crystal_hot', { seed: 1 });
	M.stick(lid, P(0, 17.0, 0), [0, 1, 0], 2.4 * k, 0.9 * k, 'crystal_hot', { seed: 2, tag: 'tip' });
	for (const sx of [-1, 1]) for (const sz of [-1, 1]) M.stick(lid, P(sx * 7.8, 12.0, sz * 5.6), [sx * 0.35, 1, sz * 0.35], 2.8 * k, 1.0 * k, 'crystal', { seed: 3 + sx + sz * 2, tag: 'tip' });
	const spill = M.bone(`${pre}_spill`, pre, P(0, 8, 0));
	crystals(M, spill, P(0, 7.5, 0), [0, 1, 0], 8, 15 * k, 2.6 * k, 300, 'crystal_hot', 0.65);
	return body;
}
function buildCasket() {
	const M = new Model('casket_of_ancient_winters', { TW: 512 });
	M.bone('root', null, [0, 0, 0]);
	addCasket(M, 'root', [0, 0, 0], 1);
	return M;
}

// ================================================================ King Laufey (own rig: Jotun base + robe, collar, crown, casket)
function buildLaufey() {
	const M = new Model('king_laufey', { TW: 512 });
	M.pal = PALS.laufey; M.clothCol = [24, 30, 54]; M.trimCol = [120, 140, 180]; M.gaunt = true;
	M.furCol = [40, 40, 50]; M.furTip = [150, 154, 166];
	const P = Object.assign({}, BASE, {
		bulk: 0.86, chestW: 17.5, ribW: 11.0, waistW: 7.2, waistD: 5.0, chestD: 7.0, armW: 3.2, armD: 3.3, foreW: 3.2,
		upperLen: 16.5, foreLen: 15.5, handLen: 4.8, fingerLen: 7.8, thighLen: 19, shinLen: 18, thighW: 4.6, shinW: 3.6,
		headW: 6.2, headH: 9.0, headD: 7.2, neckH: 4.4, pelvisW: 10.5, armOut: 6,
	});
	jotunRig(M, P);
	const A = M.A, cw = A.cw;
	// fur collar rising behind the head, lapels framing the bare chest
	const collar = M.bone('collar', 'chest', [0, A.chestTop, 3]);
	M.box(collar, -8.2, A.chestTop - 2.5, 2.6, 8.2, A.chestTop + 7.5, 5.4, 'fur_d', { rot: [-14, 0, 0], pivot: [0, A.chestTop - 2, 4], paint: 'collar' });
	for (const [s] of SIDES) {
		M.box(collar, ...[sxr(s, 5.0, 9.0)[0], A.chestTop - 3, -3.6, sxr(s, 5.0, 9.0)[1], A.chestTop + 5.2, 3.8], 'fur_d', { rot: [0, 0, s * 12], pivot: [s * 5, A.chestTop, 0] });
		M.box('chest', ...[sxr(s, 5.0, cw + 0.6)[0], A.chestBot - 0.5, -4.5, sxr(s, 5.0, cw + 0.6)[1], A.chestTop + 0.5, -2.6], 'fur_d', { paint: 'lapel' });
	}
	M.box('chest', -cw - 0.5, A.chestBot - 0.8, -2.8, cw + 0.5, A.chestTop - 0.5, 4.3, 'cloth', { paint: 'coat' });
	M.box('waist', -4.6, A.pelvisTop - 1, 1.0, 4.6, A.chestBot + 1, 3.6, 'cloth', { paint: 'coat' });
	// long open robe to the floor (panels on the hips), fur trimmed
	const ry = A.pelvisTop + 1.2;
	const robe = (name, x0, x1, z0, z1, rot, piv) => {
		const b = M.bone(name, 'hips', piv, rot);
		M.box(b, x0, 0.4, z0, x1, ry, z1, 'cloth', { paint: 'robe' });
		M.box(b, x0 - 0.2, 0.0, z0 - 0.2, x1 + 0.2, 2.2, z1 + 0.2, 'fur', { paint: 'hem' });
		return b;
	};
	robe('robe_b', -8.4, 8.4, 3.0, 4.4, [3, 0, 0], [0, ry, 3.6]);
	for (const [s, n] of SIDES) {
		robe(`robe_${n}`, ...sxr(s, 6.4, 7.6), -4.7, 4.5, [0, 0, -s * 6], [s * 6.8, ry, 0]);
		const b = robe(`robe_f${n}`, ...sxr(s, 3.0, 7.4), -4.6, -3.4, [-2, 0, -s * 6], [s * 5, ry, -4]);
		M.box(b, ...[sxr(s, 2.6, 3.9)[0], 0.0, -4.8, sxr(s, 2.6, 3.9)[1], ry, -3.2], 'fur', { paint: 'hem' });
	}
	M.box('hips', -5.6, A.pelvisTop - 2.0, -3.6, 5.6, A.pelvisTop - 0.4, 3.6, 'leather_d', { paint: 'belt' });
	M.cbox('hips', [0, A.pelvisTop - 1.2, -3.7], [2.2, 2.2, 0.6], 'crystal_hot', { seed: 5 });
	M.box(M.bone('flap_f', 'hips', [0, A.pelvisTop - 2, -3.5]), -2.4, A.hipY - 11, -3.75, 2.4, A.pelvisTop - 2.0, -3.3, 'leather_d', { paint: 'flap' });
	// sleeves, fur cuffs, fur on the shoulders
	for (const [s, n] of SIDES) {
		const sx = s * A.shX;
		M.box(`arm_${n}`, sx - 2.2, A.elbowY - 0.5, -2.2, sx + 2.2, A.shY + 1.4, 2.2, 'cloth', { paint: 'sleeve' });
		M.box(`forearm_${n}`, sx - 2.6, A.wristY + 1.6, -2.6, sx + 2.6, A.elbowY + 0.6, 2.6, 'cloth', { paint: 'sleeve' });
		M.box(`forearm_${n}`, sx - 2.9, A.wristY + 0.4, -2.9, sx + 2.9, A.wristY + 2.4, 2.9, 'fur');
		M.box(`shoulder_${n}`, sx - 3.2, A.shY + 0.2, -3.0, sx + 3.4, A.shY + 3.4, 3.2, 'fur_d');
	}
	// the ice crown, fused with the skull: a band and jagged spikes, the tallest at the front and sides
	const hw = 3.1, hd = 3.6;
	const crown = M.bone('crown', 'head', [0, A.browY + 1, 0]);
	M.box(crown, -hw - 0.35, A.browY + 0.7, -hd - 0.3, hw + 0.35, A.browY + 1.9, hd + 0.1, 'ice_dim', { seed: 60 });
	const spikes = [[0, 8.5], [28, 5.0], [-28, 5.0], [55, 6.0], [-55, 6.0], [88, 8.0], [-88, 8.0], [120, 5.0], [-120, 5.0], [155, 4.2], [-155, 4.2], [180, 3.6]];
	spikes.forEach(([a, len], i) => {
		const r = a * DEGR;
		const base = [Math.sin(r) * hw * 0.95, A.browY + 1.4, -Math.cos(r) * hd * 0.95];
		const out = Math.abs(a) > 70 ? 0.55 : 0.25;
		const dir = [Math.sin(r) * out, 1, -Math.cos(r) * out * 0.7];
		const w = 1.5 * (0.7 + len / 14);
		M.stick(crown, base, dir, len * 0.7, w, 'crystal', { seed: 70 + i });
		M.stick(crown, vadd(base, vsc(vnorm(dir), len * 0.62)), dir, len * 0.45, w * 0.55, 'crystal', { seed: 90 + i, tag: 'tip' });
	});
	// icicle beard
	const beard = M.B.beard;
	[[-2.1, 3.0], [-1.3, 5.2], [-0.45, 7.4], [0.45, 7.0], [1.3, 5.6], [2.1, 3.4], [-0.9, 4.0], [0.9, 4.4]].forEach(([x, len], i) => {
		const z = A.faceZ + 0.35 + (i > 5 ? 1.2 : 0);
		M.stick(beard, [x, A.hb + 0.2, z], [x * 0.04, -1, -0.08], len * 0.65, 0.95, 'ice_dim', { seed: 110 + i });
		M.stick(beard, [x + x * 0.025 * len, A.hb + 0.2 - len * 0.6, z - 0.05 * len], [x * 0.04, -1, -0.08], len * 0.45, 0.5, 'ice', { seed: 130 + i, tag: 'tip' });
	});
	// Casket of Ancient Winters on the left fist; ice spear-staff in the right
	const [cx, cy] = M.B.weapon_l.pivot;
	addCasket(M, 'weapon_l', [cx, cy - 0.4, 0], 0.78);
	return M;
}
function laufeyPoses(M) {
	const A = M.A;
	const stand = Object.assign({
		arm_r: [-14, 0, 6], forearm_r: [-66, 0, 0],
		arm_l: [-26, 0, -4], forearm_l: [-70, 0, 0],
		head: [6, 0, 0], neck: [-2, 0, 0], chest: [-2, 0, 0],
	}, FIST('r'), FIST('l'));
	L.aimBone(M, stand, 'weapon_r', [0, 0, -1], [0, 1, -0.04]);
	L.aimBone(M, stand, 'weapon_l', [0, 1, 0], [0, 1, 0]);
	const action = Object.assign({
		arm_l: [-174, 0, -6], forearm_l: [-6, 0, 0],
		arm_r: [-56, 0, 22], forearm_r: [-30, 0, 0],
		chest: [-8, 0, 0], head: [-22, 0, 0], jaw: [16, 0, 0],
		thigh_r: [-6, 0, 3], thigh_l: [6, 0, -3],
		casket_lid: [-110, 0, 0],
	}, FIST('r'), FIST('l'));
	L.aimBone(M, action, 'weapon_r', [0, 0, -1], [0.05, -0.55, -1]);
	L.aimBone(M, action, 'weapon_l', [0, 1, 0], [0, 1, 0]);
	return { stand, action };
}
// The spear-staff is sized after the stand pose so its butt rests on the ground.
function laufeyStaff(M, stand) {
	const W = M.world(stand);
	const g = L.affApply(W.weapon_r, M.B.weapon_r.pivot);
	iceSpear(M, 'weapon_r', 34, g[1] - 2.2, 1.3, { bladeLen: 18 });
}

// ================================================================ Ymir's arm (render-only)
// v2: only the forearm and hand come out of the rift. A tapered forearm (muscle belly near the elbow, slim wrist),
// a hand with a palm, pads, tendons and knuckles, four fingers with three jointed phalanges each and a three-part
// thumb; cracked glacier skin (Voronoi plates, dark fissures, a few glowing cores) with veins of blue ice.
// Bones: root (sits in the rift) > fore > hand > (f<i>_1 > f<i>_2 > f<i>_3) x4, hand > thumb_1 > thumb_2 > thumb_3.
// The palm faces +Z, the back of the hand -Z, the thumb is on +X.
const YMIR_PALM = [0, -116, 0]; // palm centre, in rest coordinates
const YMIR_FINGERS = [ // x, width, phalanx lengths (index on +X next to the thumb)
	[9.8, 5.4, [13, 9.5, 7.5]], [3.3, 5.9, [14.5, 10.5, 8]], [-3.3, 5.7, [13.5, 9.5, 7.5]], [-9.9, 4.8, [10.5, 7.5, 6]],
];
function cham(M, b, x0, y0, z0, x1, y1, z1, i, mat, opts = {}) { // a chamfered block: two crossed boxes
	M.box(b, x0, y0, z0 + i, x1, y1, z1 - i, mat, opts);
	M.box(b, x0 + i, y0 + 0.03, z0, x1 - i, y1 - 0.03, z1, mat, opts);
}
function buildYmir() {
	const M = new Model('ymir_arm', { TW: 1024, D: 2 });
	M.extraMat = ymirSkin;
	M.bone('root', null, [0, 0, 0]);
	const fore = M.bone('fore', 'root', [0, 0, 0]);
	// tapered forearm, elbow end hidden in the rift
	[[14, -16, 17, 15], [-12, -42, 16.5, 14.2], [-38, -64, 14, 11.8], [-60, -84, 11.6, 9.6], [-80, -104, 9.6, 7.6]].forEach(([y0, y1, hx, hz]) =>
		cham(M, fore, -hx, y1, -hz, hx, y0, hz, Math.min(hx, hz) * 0.3, 'ymir', { paint: 'fore' }));
	cham(M, fore, 5, -48, -15.5, 19.5, 8, 3, 3, 'ymir', { paint: 'fore' });     // brachioradialis (thumb side)
	cham(M, fore, -13, -44, -17.5, 8, 8, -7, 3, 'ymir', { paint: 'fore' });     // extensor mass (back)
	cham(M, fore, -17.5, -36, -6, -9, 6, 12, 2.5, 'ymir', { paint: 'fore' });   // flexor mass (pinky side)
	for (const x of [-5.5, -1.5, 2.5, 6.5]) M.box(fore, x - 0.9, -101, -9.4, x + 0.9, -62, -7.6, 'ymir', { paint: 'tendon' });
	cham(M, fore, -10.4, -106, -8.4, 10.4, -97, 8.4, 2.5, 'ymir', { paint: 'wrist' }); // wrist bones
	crystals(M, fore, [-2, -10, -16.5], [-0.2, 0.45, -1], 5, 22, 5.5, 410);
	crystals(M, fore, [8, -36, -15], [0.4, 0.3, -1], 4, 15, 4.2, 411);
	crystals(M, fore, [-6, -60, -11], [-0.3, 0.25, -1], 3, 10, 3.2, 412);
	crystals(M, fore, [-17, -14, 4], [-1, 0.5, 0.2], 3, 12, 3.6, 413);
	const hand = M.bone('hand', 'fore', [0, -102, 0]);
	cham(M, hand, -13.2, -131, -5.6, 13.2, -100, 5.4, 2.0, 'ymir', { paint: 'palm' });
	cham(M, hand, 3.5, -121, 2.5, 14, -100.5, 8.5, 2.2, 'ymir', { paint: 'pad' });   // thenar pad (thumb base)
	cham(M, hand, -13.5, -129, 2.5, -5, -106, 7.2, 2.0, 'ymir', { paint: 'pad' });  // hypothenar pad
	cham(M, hand, -6, -131, 3.0, 7, -124, 6.5, 1.5, 'ymir', { paint: 'pad' });      // pads under the knuckles
	YMIR_FINGERS.forEach(([x, w]) => {
		M.box(hand, x - 0.9, -127, -7.1, x + 0.9, -104, -5.4, 'ymir', { paint: 'tendon' });          // back-of-hand tendon
		cham(M, hand, x - w / 2 - 0.6, -135, -7.6, x + w / 2 + 0.6, -127, 3.8, 1.6, 'ymir', { paint: 'knuckle' });
	});
	crystals(M, hand, [-4, -112, -7], [0, 0.35, -1], 3, 7, 2.6, 430);
	YMIR_FINGERS.forEach(([x, w, lens], i) => {
		let y = -133, parent = 'hand', ww = w;
		lens.forEach((len, j) => {
			const b = M.bone(`f${i}_${j + 1}`, parent, [x, y, 0]);
			cham(M, b, x - ww / 2, y - len - 0.6, -ww * 0.55, x + ww / 2, y + 0.4, ww * 0.5, ww * 0.26, 'ymir', { paint: 'finger' });
			if (j < 2) M.box(b, x - ww / 2 - 0.35, y - len - 1.2, -ww * 0.6, x + ww / 2 + 0.35, y - len + 1.0, -ww * 0.1, 'ymir', { paint: 'knuckle' }); // joint
			else {
				M.box(b, x - ww * 0.38, y - len - 1.6, -ww * 0.68, x + ww * 0.38, y - len * 0.45, -ww * 0.36, 'ice', { seed: 420 + i, tag: 'tip' }); // ice nail
				M.box(b, x - ww * 0.3, y - len - 2.4, -ww * 0.45, x + ww * 0.3, y - len - 0.4, ww * 0.25, 'ymir', { paint: 'finger' }); // rounded tip
			}
			parent = `f${i}_${j + 1}`; y -= len; ww *= 0.9;
		});
	});
	// thumb: three parts, angled out (+X) and toward the palm (+Z)
	let ty = -106, tw = 7.6;
	[[0, 13], [1, 10], [2, 8]].forEach(([j, len]) => {
		const b = M.bone(`thumb_${j + 1}`, j ? `thumb_${j}` : 'hand', [12, ty, 3], j ? [8, 0, 4] : [18, 0, -40]);
		cham(M, b, 12 - tw / 2, ty - len - 0.6, 3 - tw * 0.55, 12 + tw / 2, ty + 0.5, 3 + tw * 0.5, tw * 0.26, 'ymir', { paint: 'finger' });
		if (j === 2) M.box(b, 12 - tw * 0.38, ty - len - 1.4, 3 - tw * 0.68, 12 + tw * 0.38, ty - len * 0.4, 3 - tw * 0.36, 'ice', { seed: 440, tag: 'tip' });
		else M.box(b, 12 - tw / 2 - 0.35, ty - len - 1.2, 3 - tw * 0.6, 12 + tw / 2 + 0.35, ty - len + 1.0, 3 - tw * 0.1, 'ymir', { paint: 'knuckle' });
		ty -= len; tw *= 0.88;
	});
	M.k = 40 / 176; M.MODEL_SCALE = 16 * M.k; M.heightBlocks = 40;
	return M;
}
// 3D cellular noise: distance to the nearest / second-nearest feature point (cell units) and the nearest cell id.
function voronoi(x, y, z) {
	const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z);
	let d1 = 9, d2 = 9, id = 0;
	for (let dz = -1; dz <= 1; dz++) for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
		const cx = xi + dx, cy = yi + dy, cz = zi + dz;
		const px = cx + hash3(cx, cy, cz), py = cy + hash3(cx + 17, cy, cz), pz = cz + hash3(cx, cy + 31, cz);
		const d = Math.hypot(px - x, py - y, pz - z);
		if (d < d1) { d2 = d1; d1 = d; id = hash3(cx, cy, cz + 7); } else if (d < d2) d2 = d;
	}
	return [d1, d2, id];
}
function ymirSkin(c, f, s, t, p, info) {
	if (c.mat !== 'ymir') return null;
	const P = c.paint;
	const S = P === 'finger' || P === 'knuckle' || P === 'tendon' ? 4.5 : 7.5;
	const [d1, d2, id] = voronoi(p[0] / S + 3.1, p[1] / S, p[2] / S);
	const e = d2 - d1;
	let col = mix([92, 104, 124], [168, 180, 196], 0.25 + 0.6 * id);
	col = mul(col, 0.9 + 0.2 * fbm(p[0] * 0.25, p[1] * 0.25, p[2] * 0.25, 3));
	col = mix(col, [70, 84, 108], smooth(0.35, 0.05, e) * 0.5); // plates darken toward their seams
	if (e > 0.1 && e < 0.17) col = mix(col, [214, 224, 236], 0.25); // raised plate rims catch the light
	let g = null;
	// veins of blue ice running through the skin
	const v = Math.abs(fbm(p[0] * 0.03 + 11, p[1] * 0.018, p[2] * 0.03, 4) - 0.5);
	if (v < 0.022) { col = mix([110, 168, 216], [196, 234, 255], 1 - v / 0.022); if (v < 0.003) g = [40, 110, 190]; }
	// fissures between the plates: dark, with a few glowing cores
	if (e < 0.05) {
		col = mix([24, 32, 52], [60, 72, 98], e / 0.05);
		if (e < 0.02 && fbm(p[0] * 0.02 + 40, p[1] * 0.02, p[2] * 0.02, 2) > 0.64) { col = [120, 200, 255]; g = [60, 140, 220]; }
	}
	if (P === 'knuckle' || P === 'pad') col = mul(col, 1.06);
	if (P === 'tendon') col = mix(col, [190, 200, 214], 0.2);
	// frost and snow on everything facing up, dark undersides
	if (f === 'up' || (f !== 'down' && t < 0.07)) col = mix(col, [236, 244, 250], 0.7);
	else col = mix(col, [224, 236, 246], 0.3 * smooth(0.62, 0.78, fbm(p[0] * 0.5 + 5, p[1] * 0.5, p[2] * 0.5, 3)));
	if (f === 'down') col = mul(col, 0.62);
	return [bevel(col, info, 0.82, 0.93), 255, g];
}
function ymirPoses(M) {
	const fingerPose = (pose, k1, k2, k3, spread) => YMIR_FINGERS.forEach(([x], i) => {
		pose[`f${i}_1`] = [k1, 0, -x / 9.8 * spread]; pose[`f${i}_2`] = [k2, 0, 0]; pose[`f${i}_3`] = [k3, 0, 0];
	});
	// the forearm comes down out of the rift (high and behind) and the palm slams flat on the ground
	const slam = { $root: [0, 118, 56], fore: [-20, 0, 0], hand: [-60, 0, 0] };
	const palmDown = (W, dir) => 40 * (1 - L.vdot(L.vnorm(L.mv3(W.hand.m, [0, 0, 1])), L.vnorm(dir)));
	L.reach(M, slam, 'hand', YMIR_PALM, [0, 8, -12], [['fore', 0], ['fore', 1], ['fore', 2], ['hand', 0], ['hand', 1], ['hand', 2]],
		W => palmDown(W, [0, -1, 0]) + 25 * (1 - L.vdot(L.vnorm(L.mv3(W.hand.m, [0, -1, 0])), L.vnorm([0, 0, -1]))));
	fingerPose(slam, 4, 7, 9, 9);
	slam.thumb_1 = [-4, 0, -10]; slam.thumb_2 = [4, 0, 0]; slam.thumb_3 = [6, 0, 0];
	// reaching down: fingers hooked like claws
	const reachP = { $root: [0, 118, 56], fore: [-20, 0, 0], hand: [-30, 0, 0] };
	L.reach(M, reachP, 'hand', YMIR_PALM, [0, 58, -30], [['fore', 0], ['fore', 1], ['fore', 2], ['hand', 0], ['hand', 1], ['hand', 2]],
		W => palmDown(W, [0, -0.75, -0.65]));
	fingerPose(reachP, 22, 34, 30, 14);
	reachP.thumb_1 = [10, 0, -24]; reachP.thumb_2 = [20, 0, 0]; reachP.thumb_3 = [24, 0, 0];
	return { slam, reach: reachP };
}
// The arm vanishes into the rift: geometry above the rift's height is painted over with the rift's interior,
// with a bright seam where the skin passes through.
function riftOver(ctx, ctrW, R) {
	const c = ctx.proj(ctrW);
	const rx = R * ctx.ppu, ry = R * 0.5 * ctx.ppu;
	const Ct = [[ctx.C[0][0], ctx.C[1][0], ctx.C[2][0]], [ctx.C[0][1], ctx.C[1][1], ctx.C[2][1]], [ctx.C[0][2], ctx.C[1][2], ctx.C[2][2]]];
	const ox = ctx.o.origin[0] * ctx.ss, oy = ctx.o.origin[1] * ctx.ss;
	for (let y = 0; y < ctx.H; y++) for (let x = 0; x < ctx.W; x++) {
		const i = y * ctx.W + x;
		if (ctx.zb[i] === -Infinity) continue;
		const v = [(x + 0.5 - ox) / ctx.ppu, (oy - y - 0.5) / ctx.ppu, ctx.zb[i] / ctx.ppu];
		const wy = Ct[1][0] * v[0] + Ct[1][1] * v[1] + Ct[1][2] * v[2];
		if (wy < ctrW[1] - 1.2) continue;
		const dx = (x - c[0]) / rx, dy = (y - c[1]) / ry;
		const d = Math.hypot(dx, dy);
		if (d > 1.05) continue;
		const j = i * 3;
		if (wy > ctrW[1]) { // inside the rift: the swirl hides the rest of the arm
			const th = Math.atan2(dy, dx);
			const sw = 0.5 + 0.5 * Math.sin(th * 3 + d * 11 + fbm(dx * 2, dy * 2, 3, 2) * 5);
			const col = mix(mix([10, 14, 34], [40, 90, 170], Math.pow(d, 1.6)), [170, 225, 255], sw * Math.pow(d, 2.2) * 0.8);
			ctx.col[j] = col[0]; ctx.col[j + 1] = col[1]; ctx.col[j + 2] = col[2];
			ctx.emi[j] = 0; ctx.emi[j + 1] = 0; ctx.emi[j + 2] = 0;
		} else { // a glowing seam of frost where the arm leaves the rift
			const k = 1 - (ctrW[1] - wy) / 1.2;
			for (let q = 0; q < 3; q++) ctx.col[j + q] = ctx.col[j + q] * (1 - k * 0.8) + [200, 240, 255][q] * k * 0.8;
			ctx.emi[j] = 90 * k; ctx.emi[j + 1] = 170 * k; ctx.emi[j + 2] = 255 * k;
		}
	}
}
function buildBurst() {
	const M = new Model('ymir_burst', { TW: 512 });
	const r = M.bone('root', null, [0, 0, 0]);
	for (let i = 0; i < 16; i++) {
		const a = i / 16 * Math.PI * 2 + (i % 3) * 0.13, d = 46 + (i % 4) * 6;
		crystals(M, r, [Math.cos(a) * d, -1, Math.sin(a) * d], [Math.cos(a) * 0.8, 1, Math.sin(a) * 0.8], 3, 10 + (i % 5) * 3, 3.2, 500 + i, 'crystal', 0.4);
	}
	M.k = 40 / 160; M.MODEL_SCALE = 4; M.heightBlocks = 3;
	return M;
}
// A plain 2-block player for scale, drawn as a dark silhouette.
function buildPlayer() {
	const M = new Model('player', { TW: 256, D: 1 });
	const r = M.bone('root', null, [0, 0, 0]);
	M.box(r, -4, 0, -2, 0, 12, 2, 'sil'); M.box(r, 0, 0, -2, 4, 12, 2, 'sil');
	M.box(r, -4, 12, -2, 4, 24, 2, 'sil'); M.box(r, -8, 12, -2, -4, 24, 2, 'sil'); M.box(r, 4, 12, -2, 8, 24, 2, 'sil');
	M.box(r, -4, 24, -4, 4, 32, 4, 'sil');
	M.k = 2 / 32; M.MODEL_SCALE = 1; M.heightBlocks = 2;
	return M;
}

function render(M, file, title, sub, stand, action, opts = {}) {
	const pitch = opts.pitch == null ? 8 : opts.pitch;
	const it = (pose, extra = {}) => Object.assign({ model: M, pose, root: pose.$root || planted(M, pose), hide: opts.standHide || null }, extra);
	const panels = [
		{ label: 'FRONT', items: [it(stand)], cam: { yaw: 0, pitch } },
		{ label: 'THREE-QUARTER', items: [it(stand)], cam: { yaw: opts.q || 35, pitch } },
		{ label: 'SIDE', items: [it(stand)], cam: { yaw: opts.side || 90, pitch } },
		{ label: action.label, items: [it(action.pose, { hide: action.hide || null })], cam: { yaw: action.yaw == null ? 25 : action.yaw, pitch: action.pitch == null ? pitch : action.pitch }, ownScale: true },
	];
	L.sheet(path.join(OUT, file), title, sub, panels, Object.assign({ minW: 1800, panelH: 1100 }, opts.sheet || {}));
}

function finish(M, id, blocks) {
	M.setHeight(blocks, c => (!['hair', 'horn', 'crystal', 'crystal_hot', 'ice', 'blade'].includes(c.mat) && !/^(weapon|casket_spill)/.test(c.bone)) || c.bone === 'root');
	M.layout();
	M.paint(shadeFor(M));
	if (!args.includes('--no-assets')) M.writeAssets(path.join(OUT, 'assets'), id);
	console.log(`${id}: ${M.bones.length} bones, ${M.cubes().length} cubes, tex ${M.TW}x${M.TH}, scale ${M.MODEL_SCALE.toFixed(3)}`);
	return M;
}

// ---------------------------------------------------------------- poses
function raiderPoses(M) {
	const stand = Object.assign({
		arm_r: [-18, 0, 4], forearm_r: [-62, 0, 0], hand_r: [0, 0, 0], weapon_r: [-20, 0, 0],
		arm_l: [4, 0, -4], forearm_l: [-10, 0, 0],
		thigh_r: [0, 0, 3], thigh_l: [0, 0, -3], head: [-3, 0, 0],
	}, FIST('r'), OPEN('l'));
	const action = Object.assign({
		waist: [0, -28, 0], chest: [-8, -14, 0], head: [-4, 34, 0], neck: [0, 6, 0],
		arm_r: [-168, 0, 22], forearm_r: [58, 0, 0], hand_r: [8, 0, 0], 
		arm_l: [-84, 26, -12], forearm_l: [-6, 0, 0],
		thigh_l: [-52, 0, -6], shin_l: [48, 0, 0], foot_l: [4, 0, 0],
		thigh_r: [30, 0, 8], shin_r: [32, 0, 0], foot_r: [-30, 0, 0],
		hips: [0, 22, 0],
	}, FIST('r'), OPEN('l', 0.6));
	L.aimBone(M, action, 'weapon_r', [0, 0, -1], [0.05, 0.28, -1]);
	return { stand, action };
}

// ================================================================ main
if (!OUT) { console.error('usage: node scratchpad/gen_frost_giant_models.js --concept <outDir> [--only raider,...]'); process.exit(1); }
const built = {};
if (WANT('raider') || WANT('lineup')) {
	const M = finish(buildRaider(), 'jotun_raider', 4.5);
	built.raider = M;
	M.poses = raiderPoses(M);
	if (WANT('raider')) render(M, '01_raider.png', 'JOTUN RAIDER', '4.5 BLOCKS - ICE SPEAR, LEATHER AND FUR KILT', M.poses.stand, { label: 'ACTION: SPEAR THROW', pose: M.poses.action, yaw: 40 });
}
if (WANT('shaman') || WANT('lineup')) {
	const M = finish(buildShaman(), 'frost_shaman', 5);
	built.shaman = M; M.poses = shamanPoses(M);
	if (WANT('shaman')) render(M, '02_shaman.png', 'FROST SHAMAN', '5 BLOCKS - ROBED CASTER, BONE JEWELLERY, CRYSTAL STAFF', M.poses.stand, { label: 'ACTION: CASTING', pose: M.poses.action, yaw: 30 }, { q: -35, standHide: c => c.bone === 'spell_r' });
}
if (WANT('warlord') || WANT('lineup')) {
	const M = finish(buildWarlord(), 'frost_giant_warlord', 11);
	built.warlord = M; M.poses = warlordPoses(M);
	if (WANT('warlord')) render(M, '03_warlord.png', 'FROST GIANT WARLORD', '11 BLOCKS - HORNED HELM, FUR MANTLE, ICE GREATSWORD', M.poses.stand, { label: 'ACTION: RALLY WAR CRY', pose: M.poses.action, yaw: 30 });
}
if (WANT('brute') || WANT('lineup')) {
	const M = finish(buildBrute(), 'ice_brute', 7);
	built.brute = M; M.poses = brutePoses(M);
	if (WANT('brute')) render(M, '04_brute.png', 'ICE BRUTE', '7 BLOCKS - HULKING BUT UPRIGHT, ICE GAUNTLETS, CRYSTAL GROWTHS', M.poses.stand, { label: 'ACTION: GLACIER SLAM', pose: M.poses.action, yaw: 30 }, { q: 145 });
}
if (WANT('hound') || WANT('lineup')) {
	const M = finish(buildHound(), 'rime_hound', 1.6);
	built.hound = M; M.poses = houndPoses(M);
	if (WANT('hound')) render(M, '05_hound.png', 'RIME HOUND', '1.6 BLOCKS - FROST WOLF, ICY MANE, CRYSTAL SPINES', M.poses.stand, { label: 'ACTION: POUNCE', pose: M.poses.action, yaw: 70 }, { q: 40, side: 90, sheet: { maxW: 2700 } });
}
let casketM = null;
if (WANT('casket') || WANT('lineup')) {
	casketM = finish(buildCasket(), 'casket_of_ancient_winters', 1.1);
	const M = casketM;
	const shut = { casket_lid: [0, 0, 0] };
	const open = { casket_lid: [-105, 0, 0] };
	const hideSpill = c => c.bone === 'casket_spill';
	const it = (pose, hide) => ({ model: M, pose, root: [0, 0, 0], hide, floorR: 1.2 });
	if (WANT('casket')) L.sheet(path.join(OUT, '09_casket.png'), 'CASKET OF ANCIENT WINTERS', 'PROP / OFF-HAND ARTIFACT - LAUFEY CARRIES IT IN HIS LEFT HAND', [
		{ label: 'FRONT', items: [it(shut, hideSpill)], cam: { yaw: 0, pitch: 14 } },
		{ label: 'THREE-QUARTER', items: [it(shut, hideSpill)], cam: { yaw: -38, pitch: 24 } },
		{ label: 'SIDE', items: [it(shut, hideSpill)], cam: { yaw: 90, pitch: 14 } },
		{ label: 'OPENED: WINTER POURS OUT', items: [it(open, null)], cam: { yaw: -30, pitch: 18 }, ownScale: true },
	], { minW: 1900, maxW: 2600, panelH: 1100, bg: { top: [96, 104, 120], bottom: [60, 64, 74] } });
}
if (WANT('laufey') || WANT('lineup')) {
	const M = buildLaufey();
	M.poses = laufeyPoses(M);
	laufeyStaff(M, M.poses.stand);
	finish(M, 'king_laufey', 14);
	built.laufey = M;
	const hideSpill = c => c.bone === 'casket_spill';
	if (WANT('laufey')) render(M, '06_laufey.png', 'KING LAUFEY', '14 BLOCKS - ICE CROWN, CASKET OF ANCIENT WINTERS (LEFT), SPEAR-STAFF (RIGHT)', M.poses.stand,
		{ label: 'ACTION: THE CASKET OPENS', pose: M.poses.action, yaw: -28 }, { q: -35, standHide: hideSpill });
}
if (WANT('ymir') || WANT('lineup')) {
	const M = buildYmir(); M.layout(); M.paint(shadeFor(M)); if (!args.includes('--no-assets')) M.writeAssets(path.join(OUT, 'assets'), 'ymir_arm');
	const B = buildBurst(); B.layout(); B.paint(shadeFor(B));
	const PL = buildPlayer(); PL.layout(); PL.paint(shadeFor(PL));
	built.ymir = M; built.burst = B; built.player = PL;
	M.poses = ymirPoses(M);
	if (WANT('ymir')) {
		const W = M.world(M.poses.slam, M.poses.slam.$root);
		const hp = vsc(L.affApply(W.hand, YMIR_PALM), M.k);
		const items = (pose, burst) => [
			{ model: M, pose, root: pose.$root, floorR: 16 },
			...(burst ? [{ model: B, pose: {}, root: [0, 0, 0], pos: [hp[0], 0, hp[2]], floorR: 0.1 }] : []),
			{ model: PL, pose: {}, root: [0, 0, 0], pos: [hp[0] + 9, 0, hp[2] - 6], silhouette: [46, 50, 60], floorR: 0.1, yaw: 0 },
		];
		const RR = 13;
		const panel = (label, pose, burst, yaw) => {
			const rc = vsc(pose.$root, M.k);
			return {
				label, items: items(pose, burst), cam: { yaw, pitch: 8 },
				pre: ctx => drawRift(ctx, rc, RR), post: ctx => riftOver(ctx, rc, RR),
				extraPts: [vadd(rc, [RR, RR * 0.3, 0]), vadd(rc, [-RR, RR * 0.3, 0]), vadd(rc, [0, RR * 0.3, RR]), vadd(rc, [0, RR * 0.3, -RR])],
			};
		};
		L.sheet(path.join(OUT, '07_ymir_arm.png'), "YMIR'S ARM", 'RENDER-ONLY - FOREARM AND HAND, ABOUT 40 BLOCKS, OUT OF THE SKY RIFT (PLAYER FOR SCALE)', [
			panel('SLAM - FRONT', M.poses.slam, true, 0),
			panel('SLAM - THREE-QUARTER', M.poses.slam, true, 38),
			panel('SLAM - SIDE', M.poses.slam, true, 90),
			panel('REACHING DOWN', M.poses.reach, false, 55),
		], { minW: 2400, maxW: 3400, panelH: 1150, bg: { top: [70, 80, 100], bottom: [104, 110, 122] }, shadowSoft: 0.5, aoR: 0.5, edgeThr: 0.25 });
	}
}
if (WANT('lineup')) lineup();

// A swirling blue-white tear in the sky, drawn behind everything (billboard, like the in-game rift).
function drawRift(ctx, ctrW, R) {
	const c = ctx.proj(ctrW);
	const rx = R * ctx.ppu, ry = R * 0.5 * ctx.ppu;
	for (let y = Math.max(0, Math.floor(c[1] - ry * 1.8)); y < Math.min(ctx.H, c[1] + ry * 1.8); y++) for (let x = Math.max(0, Math.floor(c[0] - rx * 1.8)); x < Math.min(ctx.W, c[0] + rx * 1.8); x++) {
		const dx = (x - c[0]) / rx, dy = (y - c[1]) / ry;
		const ripple = (fbm(dx * 3 + 4, dy * 3, 1.7, 3) - 0.5) * 0.35;
		const d = Math.hypot(dx, dy) + ripple;
		const th = Math.atan2(dy, dx);
		const i = (y * ctx.W + x) * 3;
		let col = null, k = 0, e = 0;
		if (d < 1) {
			const sw = 0.5 + 0.5 * Math.sin(th * 3 + d * 11 + fbm(dx * 2, dy * 2, 3, 2) * 5);
			col = mix(mix([10, 14, 34], [40, 90, 170], Math.pow(d, 1.6)), [170, 225, 255], sw * Math.pow(d, 2.2) * 0.8);
			k = 1;
			if (d > 0.86) { col = mix(col, [235, 250, 255], (d - 0.86) / 0.14); e = (d - 0.86) / 0.14; }
		} else if (d < 1.7) { col = [150, 200, 250]; k = 0.55 * Math.pow(1 - (d - 1) / 0.7, 2); }
		if (!col) continue;
		for (let q = 0; q < 3; q++) ctx.col[i + q] = ctx.col[i + q] * (1 - k) + col[q] * k;
		if (e > 0) { ctx.emi[i] = 120 * e; ctx.emi[i + 1] = 190 * e; ctx.emi[i + 2] = 255 * e; }
	}
}

// Every creature side by side at true relative scale, with a 2-block player silhouette.
function lineup() {
	let omega = null;
	try { const U = require('./gen_ultron_omega_model.js'); omega = U.buildOmegaFinished(path.join(OUT, 'assets'), args.includes('--no-assets')); } catch (e) { console.log('lineup: Ultron Omega not available: ' + e.message); }
	const PL = built.player;
	const row = [
		{ M: PL, pose: {}, name: 'PLAYER', h: '2', w: 1.4, sil: true },
		{ M: built.hound, pose: built.hound.poses.stand, name: 'RIME HOUND', h: '1.6', w: 3.0, yaw: -55 },
		{ M: built.raider, pose: built.raider.poses.stand, name: 'JOTUN RAIDER', h: '4.5', w: 3.4 },
		{ M: built.shaman, pose: built.shaman.poses.stand, name: 'FROST SHAMAN', h: '5', w: 3.6, hide: c => c.bone === 'spell_r' },
		...(omega ? [{ M: omega, pose: omega.poses.stand, name: 'ULTRON OMEGA', h: '4', w: 3.2 }] : []),
		{ M: built.brute, pose: built.brute.poses.stand, name: 'ICE BRUTE', h: '7', w: 5.4 },
		{ M: built.warlord, pose: built.warlord.poses.stand, name: 'WARLORD', h: '11', w: 6.4 },
		{ M: built.laufey, pose: built.laufey.poses.stand, name: 'KING LAUFEY', h: '14', w: 7.6, hide: c => c.bone === 'casket_spill' },
	];
	let x = 0;
	const items = [];
	for (const r of row) {
		x += r.w / 2;
		r.x = x;
		items.push({ model: r.M, pose: r.pose, root: r.sil ? [0, 0, 0] : (r.pose.$root || planted(r.M, r.pose)), pos: [x, 0, 0], yaw: r.yaw || 0, hide: r.hide, silhouette: r.sil ? [46, 50, 60] : null });
		x += r.w / 2 + 0.9;
	}
	// Ymir's hand slams down at the end of the row; his arm runs out of the top of the frame
	const Y = built.ymir, pose = Y.poses.slam;
	const W = Y.world(pose, pose.$root);
	const hp = vsc(L.affApply(W.hand, YMIR_PALM), Y.k);
	const yx = x + 6.5;
	items.push({ model: Y, pose, root: pose.$root, pos: [yx - hp[0], 0, -hp[2] + 3] });

	const total = yx + 9;
	const H = 1150, top = 90, ppu = 58;
	const cam = { yaw: 0, pitch: 6 };
	const Wpx = Math.round(total * ppu + 120);
	const origin = [60, H - 120];
	const img = L.renderPanel({ items, W: Wpx, H, cam, ppu, origin, ss: 2, floor: [{ x: total / 2, z: 0, r: total * 0.62, k: 0.4 }], bg: { top: [120, 126, 136], bottom: [80, 84, 92] }, shadowSoft: 0.25, aoR: 0.2 });
	const cv = new L.Canvas(Wpx, H + top, [26, 28, 34]);
	cv.blit(img, Wpx, H, 0, top);
	cv.text('LINEUP AT TRUE SCALE', 28, 22, 5, [240, 244, 250]);
	cv.text('FROST GIANT ASSAULT + ULTRON OMEGA - HEIGHTS IN BLOCKS, RULER IN BLOCKS', 28 + 21 * 30 + 24, 34, 3, [160, 190, 220]);
	// block ruler on the left
	for (let b = 0; b <= 16; b++) { const yy = top + origin[1] - Math.round(b * ppu * Math.cos(cam.pitch * DEGR)); cv.rect(0, yy, b % 5 === 0 ? 26 : 14, 2, [230, 236, 244]); if (b % 5 === 0 && b) cv.text(String(b), 30, yy - 8, 2, [230, 236, 244]); }
	for (const r of row) {
		const sx = origin[0] + r.x * ppu;
		cv.text(r.name, sx, top + H - 92, 2, [240, 244, 250], { align: 'center' });
		cv.text(r.h + ' BLOCKS', sx, top + H - 66, 2, [160, 196, 236], { align: 'center' });
	}
	cv.text("YMIR'S HAND", origin[0] + yx * ppu, top + H - 92, 2, [240, 244, 250], { align: 'center' });
	cv.text('ARM ABOUT 40 BLOCKS', origin[0] + yx * ppu, top + H - 66, 2, [160, 196, 236], { align: 'center' });
	cv.save(path.join(OUT, '00_lineup.png'));
	console.log(`wrote 00_lineup.png (${Wpx}x${H + top})`);
}
module.exports = { built };

// ================================================================ GAME EXPORT (--game)
// Writes the concept models AS the real game assets (no simplification) + every animation clip, into
// src/main/resources/assets/projecthero/{geo,textures/entity,animations}/frost_giant/, and
// scratchpad/frost_giant_anim_timing.json; then renders preview sheets FROM those written files into
// scratchpad/concepts_v0161/anim/.
//
// TIMING TABLE: the clip lengths and strike ticks are FrostGiantAnims.java's (the server AI is built on them); this
// generator parses its clip(...) / hold(...) / loop(...) lines, authors every clip to that table and fails if a strike
// keyframe is off its tick. Walk / run strides are matched to these assumed ground speeds (blocks/tick):
const FROST_SPEEDS = {
	rime_hound: { walk: 0.19, run: 0.26 }, jotun_raider: { walk: 0.135, run: 0.27 }, frost_shaman: { walk: 0.125, run: 0.25 },
	ice_brute: { walk: 0.12, run: 0.3, charge: 0.42 }, frost_giant_warlord: { walk: 0.11, run: 0.22 }, king_laufey: { walk: 0.09, run: 0.18 },
};
// Bones FrostModelFit ignores when it measures the rest height (crowns, horns, held weapons, props), so the BODY fills
// the hit-box like the concepts.
const FIT_IGNORE = ['helm', 'crown', 'weapon_r', 'weapon_l', 'spell_r', 'crystals'];

function parseFrostTiming() {
	const src = fs.readFileSync(path.join(REPO, 'src/main/java/com/projecthero/mod/frostgiant/FrostGiantAnims.java'), 'utf8');
	const names = {};
	for (const m of src.matchAll(/static final String (\w+) = "(\w+)";/g)) names[m[1]] = m[2];
	const t = {};
	for (const m of src.matchAll(/= (clip|hold|loop)\((\w+), "(\w+)", (\d+)(?:, (-?\d+))?\);/g)) {
		const model = names[m[2]];
		(t[model] = t[model] || {})[m[3]] = { kind: m[1] === 'clip' ? 'once' : m[1], length: +m[4], hit: m[5] != null ? +m[5] : -1 };
	}
	const tr = /TRANSITION = (\d+);/.exec(src);
	if (!tr || +tr[1] !== 4) throw new Error('FrostGiantAnims.TRANSITION is not 4');
	return t;
}
function renameBones(M, map) {
	for (const b of M.bones) {
		if (map[b.name]) { delete M.B[b.name]; b.name = map[b.name]; M.B[b.name] = b; }
		if (b.parent && map[b.parent]) b.parent = map[b.parent];
		for (const c of b.cubes) if (map[c.bone]) c.bone = map[c.bone];
	}
}
// Ymir for the game: palm centre at the origin, the forearm rising 640 px (40 blocks) to the rift, drawn at scale 1.
// The chain is re-rooted at the palm (root > hand > fore, fingers and thumb on the hand), so posing the hand swings the
// whole arm around the palm.
function ymirGame(C) {
	const S = 640 / (14 + 116); // palm centre (y -116) to the top of the forearm (y +14) = 40 blocks
	const P = q => [q[0] * S, (q[1] - YMIR_PALM[1]) * S, q[2] * S];
	const G = new Model('ymir_arm', { TW: C.TW, D: C.D });
	G.bone('root', null, [0, 0, 0]);
	const order = ['hand', 'fore', ...C.bones.map(b => b.name).filter(n => n !== 'root' && n !== 'hand' && n !== 'fore')];
	for (const n of order) {
		const b = C.B[n];
		const parent = n === 'hand' ? 'root' : n === 'fore' ? 'hand' : b.parent;
		const pivot = n === 'hand' ? [0, 0, 0] : n === 'fore' ? P(C.B.hand.pivot) : P(b.pivot); // fore now turns at the wrist
		const nb = G.bone(n, parent, pivot, b.rot);
		for (const c of b.cubes) {
			const f = P(c.from), t = P(c.to);
			const cc = G.box(nb, f[0], f[1], f[2], t[0], t[1], t[2], c.mat, { rot: c.rot, pivot: c.pivot ? P(c.pivot) : null, paint: c.paint, tag: c.tag });
			cc.uv = c.uv;
		}
	}
	G.TH = C.TH; G.tex = C.tex; G.glow = C.glow; G.rects = C.rects;
	G.k = 1 / 16; G.MODEL_SCALE = 1; G.heightBlocks = 40;
	return G;
}
// rest height (px) as FrostModelFit measures it, skipping FIT_IGNORE bones and everything under them
function fitHeightPx(M) {
	const skip = new Set();
	for (const b of M.bones) if (FIT_IGNORE.includes(b.name) || (b.parent && skip.has(b.parent))) skip.add(b.name);
	const bb = M.bounds(M.world({}), c => !skip.has(c.bone));
	return bb.hi[1] - bb.lo[1];
}
function writeGame(M, id, an, dir) {
	const RES = path.join(REPO, 'src/main/resources/assets/projecthero');
	const geoDir = path.join(RES, 'geo', dir), texDir = path.join(RES, 'textures/entity', dir), animDir = path.join(RES, 'animations', dir);
	for (const d of [geoDir, texDir, animDir]) fs.mkdirSync(d, { recursive: true });
	fs.writeFileSync(path.join(geoDir, `${id}.geo.json`), JSON.stringify(M.geoJson(`geometry.${id}`), null, '\t') + '\n');
	fs.writeFileSync(path.join(texDir, `${id}.png`), L.encodePng(M.TW, M.TH, M.tex));
	fs.writeFileSync(path.join(texDir, `${id}_glowmask.png`), L.encodePng(M.TW, M.TH, M.glow));
	fs.writeFileSync(path.join(animDir, `${id}.animation.json`), JSON.stringify(an.build(), null, '\t') + '\n');
	return { geo: path.join(geoDir, `${id}.geo.json`), tex: path.join(texDir, `${id}.png`), glow: path.join(texDir, `${id}_glowmask.png`), anim: path.join(animDir, `${id}.animation.json`) };
}


// ---------------------------------------------------------------- the in-game look (game export only)
// Minecraft's flat entity lighting and the GeckoLib glow layer flatten the concept look, so the game textures get:
//   - a little more saturation / contrast (skin most, ice a lot so it reads as cyan ice, not grey plastic),
//   - ice and crystals that glow through their whole body (edges at full strength, the interior softer),
//     stronger ridge glow, red eyes at full,
//   - HALO shells: an inflated copy of every glowing cube (and a flat plate over each eye) whose base texel is fully
//     transparent (invisible in the main pass and in GeckoLib's glow pass) and whose glowmask RGB is a soft radial
//     gradient; FrostGiantRenderer's additive glow pass (RenderType.eyes on the glowmask) turns them into a bloom halo.
const GLOW_MATS = new Set(['ice', 'crystal', 'crystal_hot', 'blade', 'casket_glass', 'ice_dim', 'ice_shaft']);
const sat = (c, k) => { const l = 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]; return c.map(v => clamp(l + (v - l) * k)); };
const contrast = (c, k, mid = 118) => c.map(v => clamp(mid + (v - mid) * k));
function addHalos(M, eyes) {
	const cubes = M.cubes().slice();
	for (const c of cubes) {
		if (!GLOW_MATS.has(c.mat)) continue;
		const sz = [0, 1, 2].map(i => c.to[i] - c.from[i]);
		const h = Math.max(0.5, Math.min(2.2, Math.min(...sz) * 0.7 + 0.3));
		const b = M.B[c.bone];
		const hc = M.box(b, c.from[0] - h, c.from[1] - h, c.from[2] - h, c.to[0] + h, c.to[1] + h, c.to[2] + h, 'halo', { rot: c.rot, pivot: c.pivot, tag: c.mat === 'crystal_hot' || c.mat === 'casket_glass' ? 'hot' : c.mat === 'ice_dim' || c.mat === 'ice_shaft' ? 'dim' : null });
		hc.haloOf = c.mat;
	}
	for (const e of eyes || []) M.box(e.bone, e.c[0] - e.s[0] / 2, e.c[1] - e.s[1] / 2, e.c[2] - e.s[2] / 2, e.c[0] + e.s[0] / 2, e.c[1] + e.s[1] / 2, e.c[2] + e.s[2] / 2, 'halo', { tag: 'eye' });
}
function jotunEyes(M) {
	const A = M.A;
	return [-1, 1].map(s => ({ bone: 'head', c: [s * A.eyeX, A.eyeY, A.faceZ - 0.2], s: [A.eyeW * 2 + 1.5, A.eyeH * 2 + 1.3, 0.2] }));
}
// GeckoLib's AutoGlowingTexture uses the glowmask only as a MASK: every non-zero glowmask texel takes the BASE texture's
// colour (at the glowmask's alpha) into the emissive texture, and that base texel is cleared. So the emissive colour lives
// in the base texture, and the glowmask carries only "where" and "how opaque".
function gameShade(M) {
	const base = shadeFor(M);
	const W = [255, 255, 255];
	return (c, f, s, t, p, info) => {
		if (c.mat === 'halo') {
			const r = Math.max(Math.abs(s - 0.5), Math.abs(t - 0.5)) * 2; // 0 centre .. 1 edge of the face
			const fall = Math.pow(Math.max(0, 1 - r), 1.3);
			const col = c.tag === 'eye' ? [255, 50, 30] : c.tag === 'hot' ? [150, 225, 255] : [90, 190, 255];
			const k = c.tag === 'eye' ? 1.0 : c.tag === 'hot' ? 1.0 : c.tag === 'dim' ? 0.5 : 0.85;
			// colour = the additive halo (FrostHaloLayer); a faint translucent haze in GeckoLib's own glow pass
			return [col.map(v => v * fall * k), 255, W, Math.max(1, Math.round(70 * fall * k))];
		}
		let [col, a, g] = base(c, f, s, t, p, info);
		let ga = null;
		if (GLOW_MATS.has(c.mat)) {
			if (c.mat !== 'casket_glass' && c.mat !== 'crystal_hot') { col = sat(col, 1.55); col = mix(col, [70, 170, 240], 0.18); } // the hot cores stay white-cyan
			g = W; ga = c.mat === 'ice_shaft' ? 205 : c.mat === 'ice_dim' ? 225 : 255; // the whole body is emissive
		} else if (c.mat === 'skin') {
			col = mul(contrast(sat(col, 1.2), 1.1), 1.16);
			if (g) {
				if (g[0] > g[2]) ga = 255; // red eyes
				else { col = mix(col, [120, 200, 255], 0.5); ga = 165; } // raised ridges glow cold blue, half-translucent
			}
		} else {
			col = contrast(sat(col, 1.15), 1.06);
			if (g) col = mix(col, g, 0.5);
		}
		return [col, a, g, ga];
	};
}
function gameLook(M, eyes) {
	addHalos(M, eyes);
	M.layout();
	M.paint(gameShade(M));
	return M;
}
function gameExport() {
	const A = require('./lib_geckogen_anim.js');
	const CL = require('./frost_giant_clips.js');
	const timing = parseFrostTiming();
	const out = { transition: 4, source: 'src/main/java/com/projecthero/mod/frostgiant/FrostGiantAnims.java', note: 'length/hit = server ticks (FrostGiantAnims); a play-once/held clip is (length - 4) ticks long and keys its strike on (hit - 4); loops are length ticks.', fit_ignore_bones: FIT_IGNORE, models: {} };
	const previews = [];
	const DANGLE = {
		jotun_raider: [{ bone: 'braid', dir: [0, -1, 0.35], period: 16, damp: 0.22, limit: 40 }, { bone: 'flap_f', period: 12, limit: 30 }, { bone: 'flap_b', period: 12, limit: 30 }],
		frost_shaman: [{ bone: 'skirt', period: 22, stiff: 0.55, limit: 12, acc: 0.8 }, { bone: 'sash', period: 12, limit: 35 }, { bone: 'beard', period: 14, damp: 0.25, limit: 30 }],
		frost_giant_warlord: [{ bone: 'cape', period: 22, damp: 0.28, limit: 35, drag: 3 }, { bone: 'beard', period: 16, limit: 25 }, { bone: 'flap_f', period: 14, limit: 30 }, { bone: 'flap_b', period: 14, limit: 30 }],
		ice_brute: [{ bone: 'flap_f', period: 14, limit: 30 }, { bone: 'flap_b', period: 14, limit: 30 }],
		king_laufey: [{ bone: 'robe_b', period: 26, stiff: 0.5, limit: 16, drag: 2 }, { bone: 'robe_l', period: 24, stiff: 0.5, limit: 12 }, { bone: 'robe_r', period: 24, stiff: 0.5, limit: 12 },
			{ bone: 'robe_fl', period: 24, stiff: 0.5, limit: 14 }, { bone: 'robe_fr', period: 24, stiff: 0.5, limit: 14 }, { bone: 'flap_f', period: 16, limit: 25 }, { bone: 'beard', period: 18, stiff: 0.4, limit: 10 }],
		rime_hound: [{ bone: 'tail', dir: [0, -0.35, 1], period: 12, damp: 0.25, limit: 35, drag: 4 }, { bone: 'tail_tip', dir: [0, -0.6, 1], period: 9, damp: 0.22, limit: 40 },
			{ bone: 'ear_l', dir: [0, 1, 0.3], period: 6, stiff: 0.25, limit: 25 }, { bone: 'ear_r', dir: [0, 1, 0.3], period: 6, stiff: 0.25, limit: 25 }, { bone: 'ruff', period: 10, stiff: 0.3, limit: 12 }],
	};
	const unit = (id, M, clipsFn, poses, opts = {}) => {
		const an = new A.AnimSet(M, id, { dangles: DANGLE[id] || [], hidden: opts.hidden || [] });
		clipsFn(an, timing[id], poses);
		const files = writeGame(M, id, an, 'frost_giant');
		const fitPx = fitHeightPx(M);
		out.models[id] = {
			texture: [M.TW, M.TH], bones: M.bones.length, cubes: M.cubes().length,
			rest_fit_height_px: +fitPx.toFixed(2), concept_model_scale: M.MODEL_SCALE ? +M.MODEL_SCALE.toFixed(4) : null,
			speeds_blocks_per_tick: FROST_SPEEDS[id] || null, clips: {},
		};
		for (const [n, t] of Object.entries(timing[id])) {
			if (!an.timing[n]) throw new Error(`${id}: clip ${n} from FrostGiantAnims was not authored`);
			out.models[id].clips[n] = Object.assign({ server_length: t.length, hit: t.hit, kind: t.kind, clip_ticks: an.timing[n].length, strike_clip_tick: t.hit >= 4 ? t.hit - 4 : null },
				an.timing[n].stride_blocks ? { stride_blocks: an.timing[n].stride_blocks } : {}, an.timing[n].move_speed ? { move_speed: an.timing[n].move_speed } : {});
		}
		for (const n of Object.keys(an.clips)) if (!timing[id][n]) throw new Error(`${id}: authored clip ${n} is not in FrostGiantAnims`);
		previews.push({ id, files, k: opts.k || (opts.height ? opts.height / fitPx : M.k), clips: an.clips, height: opts.height || 2, timing: timing[id], cam: opts.cam });
		console.log(`${id}: ${M.bones.length} bones, ${M.cubes().length} cubes, tex ${M.TW}x${M.TH}, ${Object.keys(an.clips).length} clips`);
	};
	const prep = (M, blocks) => { M.setHeight(blocks, c => (!['hair', 'horn', 'crystal', 'crystal_hot', 'ice', 'blade'].includes(c.mat) && !/^(weapon|casket_spill)/.test(c.bone)) || c.bone === 'root'); gameLook(M, M.A && M.A.eyeX ? jotunEyes(M) : M.B.wskull ? null : null); return M; };
	{ const M = buildHound(); M.setHeight(1.6); gameLook(M, [-1, 1].map(s => ({ bone: 'head', c: [s * 1.55, 20.3, -14.15], s: [2.4, 1.5, 0.2] }))); M.MODEL_SCALE = M.MODEL_SCALE; unit('rime_hound', M, CL.houndClips, houndPoses(M), { height: 1.6 }); }
	{ const M = prep(buildRaider(), 4.5); unit('jotun_raider', M, CL.raiderClips, raiderPoses(M), { height: 4.5 }); }
	{ const M = prep(buildShaman(), 5); unit('frost_shaman', M, CL.shamanClips, shamanPoses(M), { height: 5, hidden: ['spell_r'] }); }
	{ const M = prep(buildBrute(), 7); unit('ice_brute', M, CL.bruteClips, brutePoses(M), { height: 7 }); }
	{ const M = prep(buildWarlord(), 11); unit('frost_giant_warlord', M, CL.warlordClips, warlordPoses(M), { height: 11 }); }
	{
		const M = buildLaufey(); const poses = laufeyPoses(M); laufeyStaff(M, poses.stand); prep(M, 14);
		unit('king_laufey', M, CL.laufeyClips, poses, { height: 14, hidden: ['casket_spill'] });
	}
	{ const C = buildYmir(); gameLook(C); const G = ymirGame(C); unit('ymir_arm', G, CL.ymirClips, {}, { k: 1 / 16, height: 40 }); }
	{
		const M = buildCasket(); renameBones(M, { casket_lid: 'lid', casket_spill: 'spill' }); gameLook(M);
		M.k = 1.6 / 16; M.MODEL_SCALE = 1.6; M.heightBlocks = 1.8;
		unit('ice_casket', M, CL.casketClips, {}, { k: 1.6 / 16, height: 1.8, hidden: ['spill'] });
	}
	fs.writeFileSync(path.join(REPO, 'scratchpad/frost_giant_anim_timing.json'), JSON.stringify(out, null, '\t') + '\n');
	console.log('wrote scratchpad/frost_giant_anim_timing.json');
	if (!args.includes('--no-previews')) for (const p of previews) gamePreview(p, A);
}

// Preview sheets rendered from the WRITTEN game files (geo + PNG + animation JSON), at the in-game scale.
function gamePreview(p, A) {
	const G = A.loadGameModel(p.files.geo, p.files.tex, p.files.glow, p.k * 16, true);
	G.heightBlocks = p.height;
	const anims = A.loadAnimations(p.files.anim);
	const key = n => `animation.${p.id}.${n}`;
	const frames = [];
	const clipT = n => p.clips[n];
	const add = (label, n, t) => { if (clipT(n)) frames.push([label, n, Math.max(0, Math.min(clipT(n).len, Math.round(t)))]); };
	add('IDLE', 'idle', 0);
	add('IDLE (LOOKING AROUND)', 'idle', clipT('idle') ? clipT('idle').len * 0.45 : 0);
	add('WALK (CONTACT)', 'walk', 0); add('WALK (PASSING)', 'walk', clipT('walk') ? clipT('walk').len / 4 : 0);
	add('RUN', 'run', clipT('run') ? clipT('run').len / 2 : 0);
	for (const [n, t] of Object.entries(p.timing)) {
		if (['idle', 'walk', 'run'].includes(n)) continue;
		const c = clipT(n);
		if (t.hit >= 4) { add(`${n.toUpperCase().replace(/_/g, ' ')} AT HIT ${t.hit}`, n, t.hit - 4); }
		else add(`${n.toUpperCase().replace(/_/g, ' ')} (END)`, n, c.len);
	}
	const dir = path.join(PREVIEW_ROOT, 'anim');
	fs.mkdirSync(dir, { recursive: true });
	const per = 5;
	for (let i = 0; i * per < frames.length; i++) {
		const chunk = frames.slice(i * per, i * per + per);
		const panels = chunk.map(([label, n, t]) => {
			const pose = A.poseAt(anims, key(n), t);
			const hidden = new Set(pose.$hidden || []);
			for (const b of G.bones) if (b.parent && hidden.has(b.parent)) hidden.add(b.name);
			return { label, items: [{ model: G, pose, root: pose.$root, hide: c => hidden.has(c.bone), floorR: p.height * 0.45 + 0.8 }], cam: p.cam || { yaw: p.id === 'rime_hound' ? 50 : 28, pitch: 8 } };
		});
		L.sheet(path.join(dir, `${p.id}_${i + 1}.png`), `${p.id.toUpperCase().replace(/_/g, ' ')} - GAME ASSETS`, `PAGE ${i + 1} - POSES READ BACK FROM THE ANIMATION JSON`, panels,
			{ minW: 1800, panelH: 1000, maxW: 3400, ss: 1 });
	}
}
if (GAME) gameExport();
