// v0.16.7 Titan Shifting revamp: the procedural painter. shade(c, f, s, t, p, info) -> [rgb, alpha, glow].
// Every texel is painted from its 3D rest-pose position, so features line up across cube faces and layers.
'use strict';
const L = require('./lib_concept_models.js');
const { fbm, vnoise, hash3, clamp, mix, smooth } = L;

const PAL = {
	titan_form: { skin: [196, 140, 112], skinDark: [128, 80, 64], eye: [74, 190, 98], hair: [44, 33, 30], hairHi: [92, 70, 58], lip: [150, 82, 74] },
	titan_female: { skin: [232, 198, 178], skinDark: [176, 130, 116], eye: [92, 150, 214], hair: [232, 198, 116], hairHi: [252, 236, 170], lip: [196, 120, 112] },
	titan_armored: { skin: [148, 70, 58], skinDark: [92, 38, 34], eye: [255, 255, 255], plate: [232, 226, 210], plateDark: [168, 160, 150], plateEdge: [196, 214, 232] },
	titan_beast: { skin: [196, 152, 124], skinDark: [126, 88, 68], eye: [58, 48, 38], fur: [156, 118, 78], furDark: [96, 68, 44], furHi: [214, 182, 132], lip: [140, 86, 74] },
};

const BONE = [228, 218, 192], BONE_DARK = [168, 150, 120];
const MUS = [170, 38, 40], MUS_DARK = [104, 16, 24], MUS_HI = [214, 84, 76], TENDON = [232, 212, 200];
const N_ = { north: [0, 0, -1], south: [0, 0, 1], east: [1, 0, 0], west: [-1, 0, 0], up: [0, 1, 0], down: [0, -1, 0] };

const rel = (b, p) => [(p[0] - b[0]) / (b[3] - b[0]), (p[1] - b[1]) / (b[4] - b[1]), (p[2] - b[2]) / (b[5] - b[2])];
const lineAt = (d, w) => 1 - smooth(w * 0.4, w, Math.abs(d));
const tint = (c, k) => c.map(v => v * k);

// ---------------------------------------------------------------- muscle anatomy (shared by muscle + skin definition)
// returns { across, line, tendon }: the coordinate across the fibres, dark separation strength, tendon strength
function anatomy(M, c, f, p) {
	const P = M.P, b = c.seg, tag = c.tag;
	const r = rel(b, p);
	const hw = (b[3] - b[0]) / 2, mx = (b[0] + b[3]) / 2;
	const ax = p[0] - mx; // signed x from the segment centre
	const h = b[4] - b[1];
	let across = (f === 'east' || f === 'west') ? p[2] : p[0];
	let line = 0, tendon = 0;
	if (f === 'up' || f === 'down') across = p[0] + p[2] * 0.7;
	switch (tag) {
		case 'chest': {
			if (f === 'north') {
				const pecY = b[1] + h * 0.36 + Math.pow(Math.abs(p[0]) / hw, 1.6) * h * 0.18;
				line = Math.max(lineAt(p[0], 0.16), p[1] < pecY + 0.5 ? lineAt(p[1] - pecY, 0.2) : 0);
				across = p[1] < pecY ? p[0] * 1.2 + p[1] * 0.3 : p[1] * 0.9 + Math.abs(p[0]) * 0.8;
				if (p[1] < pecY) { // serratus / upper abs below the pecs
					const k = (p[1] - b[1]) / 0.95;
					line = Math.max(line, Math.abs(p[0]) < hw * 0.55 ? lineAt(k - Math.round(k), 0.12) * 0.7 : 0);
				}
			} else if (f === 'south') {
				const ex = (Math.abs(p[0]) - hw * 0.5) / (hw * 0.4), ey = (p[1] - (b[1] + h * 0.6)) / (h * 0.32);
				line = Math.max(lineAt(p[0], 0.18), lineAt(Math.hypot(ex, ey) - 1, 0.12) * 0.8);
				across = p[0] * 0.7 + p[1] * 0.7 * Math.sign(p[0]);
			} else if (f === 'east' || f === 'west') {
				const k = (p[1] + p[2] * 0.8) / 0.9;
				line = p[1] < b[1] + h * 0.55 ? lineAt(k - Math.round(k), 0.1) * 0.7 : 0;
				across = p[1] + p[2] * 0.8;
			}
			break;
		}
		case 'abdomen': case 'hips': {
			if (f === 'north') {
				const k = (p[1] - b[1]) / (h / 3);
				line = Math.max(lineAt(p[0], 0.14), Math.abs(p[0]) < hw * 0.6 ? lineAt(k - Math.round(k), 0.13) : 0, lineAt(Math.abs(p[0]) - hw * 0.62, 0.14) * 0.8);
				if (tag === 'hips') line = lineAt(p[1] - (b[1] + h * 0.5 - Math.abs(p[0]) * 0.5), 0.15) * 0.6;
			} else if (f === 'south') {
				line = lineAt(p[0], 0.16);
			} else if (f === 'east' || f === 'west') {
				across = p[1] * 0.8 + p[2] * 0.6;
			}
			break;
		}
		case 'neck': {
			if (f !== 'up' && f !== 'down') {
				const d = f === 'north' ? Math.abs(p[0]) - (0.25 + (p[1] - b[1]) * 0.4) : p[2] - b[2] - (b[5] - b[2]) * (0.3 + 0.3 * r[1]);
				line = lineAt(d, 0.14) * 0.8;
				across = p[0] + p[1] * 0.5;
			}
			break;
		}
		case 'uarm': {
			const delt = b[4] - 2.3 - (f === 'north' ? 0.4 : 0);
			line = lineAt(p[1] - delt, 0.16);
			if (f === 'east' || f === 'west') line = Math.max(line, p[1] < delt ? lineAt(p[2] - (b[2] + b[5]) / 2, 0.12) * 0.7 : 0);
			if (f === 'south' && p[1] < delt) line = Math.max(line, lineAt(Math.abs(ax) - hw * 0.35 - (delt - p[1]) * 0.08, 0.12) * 0.6);
			tendon = p[1] < b[1] + 0.55 && f === 'south' ? 0.7 : 0;
			break;
		}
		case 'farm': {
			if (f !== 'up' && f !== 'down') {
				const d = (f === 'north' || f === 'south' ? ax : p[2] - (b[2] + b[5]) / 2) - (r[1] - 0.5) * hw * 1.2;
				line = lineAt(d, 0.13) * 0.7;
				across = (f === 'north' || f === 'south' ? p[0] : p[2]) + p[1] * 0.12;
			}
			tendon = smooth(b[1] + 1.3, b[1] + 0.2, p[1]);
			break;
		}
		case 'hand': {
			tendon = (f === 'north' || f === 'south') ? lineAt(((p[0] - b[0]) / 0.55) % 1 - 0.5, 0.25) * 0.8 : 0.2;
			break;
		}
		case 'thigh': {
			if (f === 'north') {
				const d1 = ax - (r[1] - 0.6) * hw * 1.1, d2 = ax + hw * 0.55 - (1 - r[1]) * hw * 0.2;
				line = Math.max(lineAt(d1, 0.13) * 0.8, lineAt(d2, 0.12) * 0.6);
				tendon = smooth(b[1] + 1.1, b[1] + 0.2, p[1]) * (Math.abs(ax) < hw * 0.5 ? 1 : 0.3);
			} else if (f === 'south') {
				line = lineAt(ax, 0.15) * 0.8;
				line = Math.max(line, lineAt(p[1] - (b[4] - 1.2), 0.15) * 0.7);
			} else if (f === 'east' || f === 'west') {
				line = lineAt(p[2] - ((b[2] + b[5]) / 2 + 0.2), 0.14) * 0.6;
			}
			break;
		}
		case 'shin': {
			if (f === 'north') {
				tendon = lineAt(ax + hw * 0.15, 0.3) * 0.75;
				line = lineAt(ax - hw * 0.35, 0.12) * 0.6;
			} else if (f === 'south') {
				const cy = b[1] + h * 0.62;
				const dia = Math.abs(ax) * 1.4 + Math.abs(p[1] - cy) * 0.55 - hw * 0.9;
				line = Math.max(lineAt(dia, 0.14) * 0.8, p[1] > cy - h * 0.3 ? lineAt(ax, 0.12) * 0.7 : 0);
				tendon = smooth(b[1] + h * 0.35, b[1] + h * 0.1, p[1]) * (Math.abs(ax) < hw * 0.4 ? 1 : 0);
			}
			if (p[1] < b[1] + 0.5) tendon = Math.max(tendon, 0.6);
			break;
		}
		case 'foot': {
			if (f === 'up') tendon = lineAt(((p[0] - b[0]) / 0.6) % 1 - 0.5, 0.22) * 0.7;
			break;
		}
		default: break;
	}
	return { across, line, tendon, r };
}

// ---------------------------------------------------------------- skeleton
function paintSkel(M, c, f, s, t, p, info) {
	const b = c.seg, r = rel(b, p);
	const n = fbm(p[0] * 1.7 + 11, p[1] * 1.7, p[2] * 1.7, 3);
	let col = mix(BONE, BONE_DARK, 0.15 + n * 0.5);
	col = tint(col, 0.9 + 0.12 * smooth(0, 0.35, info.ePx)); // darker at the cube edges: reads as round bone
	let a = 255;
	const tag = c.tag;
	if (tag === 'ribs') {
		const h = b[4] - b[1];
		const k = (p[1] - b[1]) / (h / 6.5);
		const fr = k - Math.floor(k);
		const sternum = f === 'north' && Math.abs(p[0]) < 0.42;
		if (f === 'up' || f === 'down') {
			a = info.ePx < 0.28 ? 255 : 0; // only the rim of the cage
		} else if (!sternum && f !== 'south') {
			// ribs sweep down toward the front
			const slope = f === 'north' ? Math.abs(p[0]) * 0.18 : (p[2] - b[2]) * -0.16;
			const k2 = (p[1] - b[1] + slope) / (h / 6.5);
			const fr2 = k2 - Math.floor(k2);
			a = fr2 < 0.5 ? 255 : 0;
			col = tint(col, 0.85 + 0.2 * Math.sin(fr2 / 0.5 * Math.PI));
		} else if (f === 'south') {
			a = fr < 0.55 || Math.abs(p[0]) < 0.3 ? 255 : 0;
		}
		if (sternum) col = tint(col, 1.04);
	} else if (tag === 'pelvis') {
		if (f === 'north' || f === 'south') {
			const ex = r[0] - 0.5, ey = r[1] - 0.25;
			if (Math.hypot(ex * 1.4, ey * 1.2) < 0.28) a = 0; // the pelvic opening
		}
		if (f === 'up') a = info.ePx < 0.35 ? 255 : 0;
	} else if (tag === 'skull') {
		if (f === 'north') {
			const P = M.P, H = P.head, hw = (H[3] - H[0]) / 2;
			const X = p[0] / hw, Y = (p[1] - H[1]) / (H[4] - H[1]);
			const eyeD = Math.hypot((Math.abs(X) - 0.42) / 0.26, (Y - 0.47) / 0.12);
			if (eyeD < 1) { col = mix([30, 22, 20], [70, 56, 46], eyeD); }
			const nose = Math.abs(X) < 0.16 - (Y - 0.3) * 0.4 && Y > 0.3 && Y < 0.4;
			if (nose) col = [40, 30, 26];
			if (Y < 0.3) { // upper teeth
				const tk = (p[0] + 5) / 0.45;
				col = Math.abs(X) < 0.66 ? (tk - Math.floor(tk) < 0.18 ? [120, 104, 86] : [238, 232, 214]) : col;
			}
			col = tint(col, 1 - 0.25 * smooth(0.55, 1, Math.abs(X)));
		}
		if (f === 'east' || f === 'west') {
			const Y = (p[1] - M.P.head[1]) / (M.P.head[4] - M.P.head[1]);
			const hole = Math.hypot((r[2] - 0.55) / 0.12, (Y - 0.52) / 0.07);
			if (hole < 1) col = [52, 40, 34];
		}
	} else if (tag === 'mandible') {
		if (f === 'north' || (f === 'up' && r[2] < 0.3)) {
			const tk = (p[0] + 5) / 0.45;
			if (p[1] > b[4] - 0.55 || f === 'up') col = tk - Math.floor(tk) < 0.18 ? [120, 104, 86] : [238, 232, 214];
		}
		if (f === 'up' && r[2] > 0.3) a = info.ePx < 0.3 ? 255 : 0;
	} else if (tag === 'handbones' || tag === 'footbones') {
		const fingers = tag === 'handbones' ? r[1] < 0.55 : r[2] < 0.45;
		if (fingers && f !== 'up') {
			const k = (tag === 'handbones' ? (f === 'east' || f === 'west' ? r[2] : r[0]) : r[0]) * 4;
			if (k - Math.floor(k) > 0.62) a = 0;
			const j = tag === 'handbones' ? r[1] * 6 : r[2] * 5;
			if (j - Math.floor(j) < 0.14) col = tint(col, 0.75); // knuckle joints
		}
	} else if (tag === 'spine') {
		const k = (p[1] - b[1]) / 0.9;
		const fr = k - Math.floor(k);
		col = tint(col, fr < 0.22 ? 0.66 : 1.0); // vertebra discs
		if (f === 'south') col = tint(col, 1.05);
	} else if (tag === 'cervical') {
		const k = (p[1] - b[1]) / 0.6;
		col = tint(col, k - Math.floor(k) < 0.25 ? 0.68 : 1.0);
	} else if (tag === 'humerus' || tag === 'femur' || tag === 'radius' || tag === 'ulna' || tag === 'tibia' || tag === 'fibula') {
		const knob = smooth(0.18, 0.0, Math.min(r[1], 1 - r[1]));
		col = tint(col, 0.92 + 0.12 * knob);
	}
	return [col, a];
}

// ---------------------------------------------------------------- muscle
function paintMuscle(M, c, f, s, t, p, info) {
	const an = anatomy(M, c, f, p);
	const n = fbm(p[0] * 0.9, p[1] * 0.9, p[2] * 0.9, 3);
	const stripe = 0.5 + 0.5 * Math.sin((an.across / 0.42) * Math.PI * 2 + n * 5);
	let col = mix(MUS_DARK, MUS, 0.55 + 0.45 * stripe);
	col = mix(col, MUS_HI, smooth(0.55, 0.85, n) * 0.45 * stripe);
	col = mix(col, MUS_DARK, an.line * 0.85);
	col = mix(col, TENDON, an.tendon * (0.65 + 0.35 * stripe));
	col = tint(col, 0.88 + 0.12 * smooth(0, 0.4, info.ePx));
	const tag = c.tag;
	if (tag === 'head' || tag === 'jaw') col = paintFaceMuscle(M, c, f, p, col);
	return [col, 255];
}

function paintFaceMuscle(M, c, f, p, col) {
	const P = M.P, H = P.head, hw = (H[3] - H[0]) / 2;
	const X = p[0] / hw, Y = (p[1] - H[1]) / (H[4] - H[1]), jy = M.jawTop;
	if (f === 'north') {
		// lipless mouth: a full row of teeth either side of the jaw line
		if (Math.abs(p[1] - jy) < 0.62 && Math.abs(X) < 0.8) {
			const tk = (p[0] + 7) / 0.42;
			const gap = tk - Math.floor(tk) < 0.16 || Math.abs(p[1] - jy) < 0.05;
			const gum = Math.abs(p[1] - jy) > 0.5;
			return gap ? [70, 22, 24] : gum ? [196, 92, 96] : [240, 234, 216];
		}
		const eyeD = Math.hypot((Math.abs(X) - 0.42) / 0.22, (Y - 0.47) / 0.1);
		if (eyeD < 1) {
			const iris = Math.hypot((Math.abs(X) - 0.42) / 0.09, (Y - 0.47) / 0.075);
			const pal = PAL[M.id];
			return iris < 0.55 ? [20, 18, 16] : iris < 1 ? pal.eye : [236, 228, 220];
		}
	}
	if ((f === 'east' || f === 'west') && Math.abs(p[1] - jy) < 0.55) {
		const rz = (p[2] - H[2]) / (H[5] - H[2]);
		if (rz < 0.45) { // the teeth go round the corner of the mouth (seen through the Attack Titan's cheek slits)
			const tk = (p[2] + 7) / 0.42;
			return tk - Math.floor(tk) < 0.16 ? [70, 22, 24] : [236, 230, 212];
		}
	}
	if ((c.tag === 'head' && f === 'down') || (c.tag === 'jaw' && f === 'up')) {
		const rz = (p[2] - H[2]) / (H[5] - H[2]);
		if (rz < 0.12) return [236, 230, 212];
		return c.tag === 'jaw' ? mix([150, 52, 60], [110, 30, 40], rz) : [84, 22, 28];
	}
	return col;
}

// ---------------------------------------------------------------- skin (the user's player skin)
/** The skin pixel [r, g, b, a] showing at rest point p on face f of a part: full = the whole part's box (inflated for
 * the second layer), region = its [u, v] on the 64x64 skin. Standard player mapping: the right side (-X) first, then
 * front, left, back; top above the front, bottom beside it; the front's left column is the part's right (-X) edge. */
function skinPx(M, region, full, f, p, dims) {
	const [x0, y0, z0, x1, y1, z1] = full;
	const [pw, ph, pd] = dims;
	const fx = (p[0] - x0) / (x1 - x0), fy = (y1 - p[1]) / (y1 - y0), fz = (p[2] - z0) / (z1 - z0);
	const cl = (v, n) => Math.max(0, Math.min(n - 1, Math.floor(v * n)));
	const [u, v] = region;
	let X, Y;
	switch (f) {
		case 'north': X = u + pd + cl(fx, pw); Y = v + pd + cl(fy, ph); break;
		case 'south': X = u + 2 * pd + pw + cl(1 - fx, pw); Y = v + pd + cl(fy, ph); break;
		case 'west': X = u + cl(1 - fz, pd); Y = v + pd + cl(fy, ph); break;
		case 'east': X = u + pd + pw + cl(fz, pd); Y = v + pd + cl(fy, ph); break;
		case 'up': X = u + pd + cl(fx, pw); Y = v + cl(1 - fz, pd); break;
		default: X = u + pd + pw + cl(fx, pw); Y = v + cl(fz, pd); break;
	}
	const o = (Y * 64 + X) * 4, px = M.skin.px;
	return [px[o], px[o + 1], px[o + 2], px[o + 3]];
}
const dimsOf = b => [Math.round(b[3] - b[0]), Math.round(b[4] - b[1]), Math.round(b[5] - b[2])];

/** A cut face (a joint the skin is sliced across) shows the skin of the nearest side, a little shaded. */
function cutPx(M, c, p) {
	const F = c.full;
	const d = [['west', p[0] - F[0]], ['east', F[3] - p[0]], ['north', p[2] - F[2]], ['south', F[5] - p[2]]].sort((a, b) => a[1] - b[1]);
	const q = p.slice();
	if (d[0][0] === 'west') q[0] = F[0]; else if (d[0][0] === 'east') q[0] = F[3]; else if (d[0][0] === 'north') q[2] = F[2]; else q[2] = F[5];
	return skinPx(M, c.region, F, d[0][0], q, c.dims);
}

function mouthPx(M, c, f, p) {
	const H = M.P.head, rz = (p[2] - H[2]) / (H[5] - H[2]);
	const n = fbm(p[0] * 2, p[1] * 2, p[2] * 2, 2);
	if (rz < 0.1) { const tk = (p[0] + 7) / 0.9; return tk - Math.floor(tk) < 0.16 ? [120, 104, 86] : [236, 230, 212]; }
	return c.tag === 'jaw' ? mix([150, 52, 60], [110, 30, 40], rz * 0.7 + n * 0.3) : mix([92, 24, 30], [64, 14, 20], n);
}

function paintSkinLayer(M, c, f, s, t, p, info) {
	const S = c.s, outer = !!c.outer;
	const cut = (f === 'down' && S.cutLo) || (f === 'up' && S.cutHi);
	if (cut) {
		const same = f === 'down' ? S.sameLo : S.sameHi;
		if (outer || same) return [[0, 0, 0], 0];
		if ((f === 'down' && S.mouthLo) || (f === 'up' && S.mouthHi)) return [mouthPx(M, c, f, p), 255];
		const px = cutPx(M, c, p);
		return [tint(px, 0.78), 255];
	}
	if (outer && f === 'down' && c.tag === 'foot') return [[0, 0, 0], 0]; // flush with the sole: no second-layer sole
	const px = skinPx(M, c.region, c.full, f, p, c.dims);
	if (outer) return px[3] < 128 ? [[0, 0, 0], 0] : [px, 255, null];
	let glow = null;
	if (M.P.glow && c.part === 'head' && f === 'north' && M.P.glow(px[0], px[1], px[2])) glow = [px[0], px[1], px[2]];
	return [px, 255, glow]; // the base layer is opaque, as in vanilla
}

/** Representative skin colours of the type (body front, base layer): for stumps, flaps and dust. */
function skinPal(M) {
	if (M._pal) return M._pal;
	const px = M.skin.px, cols = [];
	for (let y = 20; y < 32; y++) for (let x = 20; x < 28; x++) { const o = (y * 64 + x) * 4; cols.push([px[o], px[o + 1], px[o + 2]]); }
	cols.sort((a, b) => (a[0] + a[1] + a[2]) - (b[0] + b[1] + b[2]));
	const med = cols[cols.length >> 1];
	M._pal = { skin: med, skinDark: tint(med, 0.62) };
	return M._pal;
}

// ---------------------------------------------------------------- skin (the old procedural painter, unused)
function skinBase(M, c, f, p, info) {
	const pal = PAL[M.id];
	const n = fbm(p[0] * 0.6 + 3, p[1] * 0.6, p[2] * 0.6, 4);
	let col = mix(pal.skin, pal.skinDark, 0.08 + n * 0.32);
	const b = c.seg, r = rel(b, p);
	// soft occlusion toward the joints and undersides
	const occ = (c.tag === 'uarm' || c.tag === 'thigh') ? smooth(0.95, 0.75, r[1]) * 0.1 : 0;
	col = tint(col, 1 - occ - (f === 'down' ? 0.08 : 0));
	const an = anatomy(M, c, f, p);
	col = tint(col, (0.84 + 0.16 * smooth(0, 0.7, info.ePx)) * (0.93 + 0.1 * r[1]));
	col = mix(col, pal.skinDark, an.line * 0.6);
	col = mix(col, tint(pal.skin, 1.08), an.tendon * 0.15);
	return { col, an, n, r };
}

function face(M, c, f, p, col, a) {
	const P = M.P, pal = PAL[M.id], H = P.head, hw = (H[3] - H[0]) / 2;
	const X = p[0] / hw, Y = (p[1] - H[1]) / (H[4] - H[1]);
	let glow = null;
	if (f === 'north' && (c.tag === 'head' || c.tag === 'jaw')) {
		const ax = Math.abs(X);
		// brow ridge + socket shadow
		col = tint(col, 1 - 0.28 * lineAt(Y - 0.69, 0.07) * smooth(0.95, 0.6, ax));
		col = tint(col, 1 - 0.22 * smooth(0.17, 0.05, Math.hypot((ax - 0.42) / 2.4, Y - 0.6)));
		// eyes
		const eyeD = Math.hypot((ax - 0.42) / 0.2, (Y - 0.6) / 0.075);
		if (eyeD < 1) {
			const iris = Math.hypot((ax - 0.42) / 0.085, (Y - 0.6) / 0.07);
			col = iris < 0.5 ? [16, 14, 12] : iris < 1 ? pal.eye : [234, 226, 216];
			if (iris >= 0.5 && iris < 1 && Y > 0.61) col = tint(col, 1.18);
			if (M.id === 'titan_armored') { col = [250, 250, 255]; glow = [255, 255, 255]; }
			if (M.id === 'titan_beast') col = iris < 1 ? (iris < 0.5 ? [10, 8, 6] : pal.eye) : [210, 196, 170];
		} else if (eyeD < 1.35) col = tint(col, 0.72); // lids
		// nose
		if (ax < 0.17 && Y > 0.36 && Y < 0.6) {
			col = tint(col, 1.06 - (ax < 0.05 ? 0 : 0.0) - (Y < 0.4 && ax > 0.06 && ax < 0.14 ? 0.45 : 0));
			if (ax > 0.13) col = tint(col, 0.86);
		}
		// mouth
		const mouthY = (M.jawTop - H[1]) / (H[4] - H[1]);
		if (ax < 0.45 && Math.abs(Y - mouthY) < 0.045) col = mix(col, pal.lip || [120, 60, 56], 0.75);
		if (ax < 0.45 && Math.abs(Y - mouthY) < 0.012) col = [60, 26, 24];
	}
	// ears
	if ((f === 'east' || f === 'west') && c.tag === 'head') {
		const rz = (p[2] - H[2]) / (H[5] - H[2]);
		const ed = Math.hypot((rz - 0.56) / 0.12, (Y - 0.55) / 0.13);
		if (ed < 1) col = tint(col, ed > 0.6 ? 0.82 : 0.95);
		if (ed < 0.45) col = tint(col, 0.7);
	}
	return [col, a, glow];
}

function paintSkin(M, c, f, s, t, p, info) {
	const id = M.id, pal = PAL[id];
	let { col, an, n, r } = skinBase(M, c, f, p, info);
	let a = 255;
	let glow = null;
	if (id === 'titan_armored') {
		// unarmoured flesh: dark, fibrous, red-brown
		const stripe = 0.5 + 0.5 * Math.sin((an.across / 0.5) * Math.PI * 2 + n * 4);
		col = mix(pal.skinDark, pal.skin, 0.45 + 0.55 * stripe);
		col = mix(col, [70, 26, 24], an.line * 0.6);
	}
	if (id === 'titan_beast') {
		const isFace = c.tag === 'head' && f === 'north' && Math.abs(p[0]) < (M.P.head[3]) * 0.78 && (p[1] - M.P.head[1]) / (M.P.head[4] - M.P.head[1]) < 0.8;
		const isMuzzle = c.tag === 'jaw' && f === 'north';
		const palm = (c.tag === 'hand' && (f === 'down' || (c.side === 'right' ? f === 'east' : f === 'west'))) || (c.tag === 'foot' && f === 'down');
		if (!isFace && !isMuzzle && !palm) {
			col = furColour(M, c, f, p, pal, c.tag === 'chest' && f === 'north' ? 0.35 : c.tag === 'abdomen' && f === 'north' ? 0.25 : 0);
		} else if (palm) col = mix([96, 74, 62], [70, 52, 44], n);
	}
	if (c.tag === 'head' || c.tag === 'jaw' || c.tag === 'neck') {
		[col, a, glow] = face(M, c, f, p, col, a);
	}
	if (id === 'titan_form' && (c.tag === 'head' || c.tag === 'jaw')) {
		// the Attack Titan's lipless cheeks: open slits from the mouth corners back toward the jaw hinge
		const H = M.P.head, hw = (H[3] - H[0]) / 2, jy = M.jawTop;
		let d = 99;
		if (f === 'north') { const ax = Math.abs(p[0]) / hw; if (ax > 0.4) d = Math.abs(p[1] - (jy + (ax - 0.4) * 0.5)) / (0.22 + (ax - 0.4) * 0.5); }
		if (f === 'east' || f === 'west') { const rz = (p[2] - H[2]) / (H[5] - H[2]); if (rz < 0.55) d = Math.abs(p[1] - (jy + 0.3 + rz * 0.5)) / (0.5 - rz * 0.6); }
		const rag = (vnoise(p[0] * 5, p[1] * 5, p[2] * 5) - 0.5) * 0.5;
		if (d + rag < 0.8) a = 0;
		else if (d + rag < 1.1) col = mix(col, [120, 50, 46], 0.6);
	}
	if (id === 'titan_female' && ['uarm', 'farm', 'thigh', 'shin'].includes(c.tag) && f !== 'up' && f !== 'down') {
		// thin skin: the muscle shows through in organic patches, most at the joints and down the outside of the limbs
		const b = c.seg;
		const outer = c.side === 'right' ? (f === 'west' ? 0.18 : 0) : (f === 'east' ? 0.18 : 0);
		const joint = c.tag === 'farm' || c.tag === 'shin' ? smooth(0.55, 0.95, r[1]) * 0.2 : smooth(0.45, 0.05, r[1]) * 0.2;
		const v = fbm(p[0] * 1.1 + 7, p[1] * 0.16, p[2] * 1.1, 4) + outer + joint;
		if (v > 0.66) a = 0;
		else if (v > 0.62) col = mix(col, [180, 90, 84], 0.65); // raw rim
		void b;
	}
	if (id === 'titan_female' && c.tag === 'chest' && f === 'north') {
		// subtle chest shape
		const C = c.seg; const by = C[1] + (C[4] - C[1]) * 0.42;
		const d = Math.hypot((Math.abs(p[0]) - 1.6) / 1.5, (p[1] - by) / 1.2);
		if (d < 1) col = tint(col, 0.98 + 0.06 * (1 - d));
		if (Math.abs(d - 1) < 0.08 && p[1] < by) col = tint(col, 0.88);
	}
	return [col, a, glow];
}

function furColour(M, c, f, p, pal, light) {
	const across = (f === 'east' || f === 'west') ? p[2] : p[0];
	const streak = fbm(across * 1.7 + 5, p[1] * 0.4, (f === 'east' || f === 'west' ? p[0] : p[2]) * 1.7, 4);
	const clump = fbm(across * 0.9, p[1] * 0.35, 1.7, 2);
	let col = mix(pal.furDark, pal.fur, smooth(0.3, 0.7, streak));
	col = mix(col, pal.furHi, smooth(0.62, 0.85, streak) * 0.6 + light);
	col = mix(col, pal.furDark, smooth(0.6, 0.85, clump) * 0.25);
	return col;
}

// ragged bottom edge for hair and fur: strands of different lengths
function ragged(M, c, f, p, depth, freq) {
	const yb = c.from[1];
	const across = (f === 'east' || f === 'west') ? p[2] : p[0];
	const len = depth * (0.35 + 0.65 * hash3(Math.floor(across * freq + 100), 7, c.tag.length));
	return p[1] - yb < len;
}

function paintHair(M, c, f, s, t, p, info) {
	const pal = PAL[M.id];
	if (f === 'down') return [[0, 0, 0], 0];
	const across = (f === 'east' || f === 'west') ? p[2] : p[0];
	const streak = fbm(across * 3.6 + 9, p[1] * 0.4, (f === 'up' ? p[2] * 3.6 : 3), 4);
	let col = mix(tint(pal.hair, 0.72), pal.hair, smooth(0.25, 0.65, streak));
	col = mix(col, pal.hairHi, smooth(0.66, 0.9, streak) * 0.75);
	// a soft sheen band on the crown
	if (c.tag === 'cap') col = mix(col, pal.hairHi, 0.25 * lineAt(p[1] - (c.to[1] - 0.6), 0.5));
	if (c.tag === 'bun') col = mix(col, pal.hairHi, 0.2 * (0.5 + 0.5 * Math.sin((p[0] * 2 + p[1] * 3) * 2)));
	let a = 255;
	if (f !== 'up' && c.tag !== 'bun') {
		const depth = c.tag === 'cap' ? (f === 'north' ? 1.0 : 0.6) : c.tag === 'back' ? 1.8 : 1.2;
		if (ragged(M, c, f, p, depth, 2.2)) a = 0;
	}
	// the cap's inner (face-side) bottom must not hide the forehead
	return [col, a];
}

function paintFur(M, c, f, s, t, p, info) {
	const pal = PAL[M.id];
	if (f === 'down' && c.tag !== 'beard') return [[0, 0, 0], 0];
	let col = furColour(M, c, f, p, pal, c.tag === 'ruff' || c.tag === 'mane' ? 0.12 : 0.05);
	let a = 255;
	if (f !== 'up' && ragged(M, c, f, p, c.tag === 'beard' ? 1.0 : 1.6, 2.6)) a = 0;
	// shaggy holes near the side edges
	if (info.ePx < 0.35 && hash3(Math.floor(p[0] * 6), Math.floor(p[1] * 3), Math.floor(p[2] * 6)) > 0.55) a = 0;
	return [col, a];
}

function paintPlate(M, c, f, s, t, p, info) {
	const pal = PAL[M.id];
	const seed = (c.from[0] * 31 + c.from[1] * 17 + c.from[2] * 7) | 0;
	// faceted: each face split along a diagonal into two tones
	const diag = (s - 0.5) * (hash3(seed, 1, 2) > 0.5 ? 1 : -1) + (t - 0.5) * 0.6;
	let col = mix(pal.plate, pal.plateDark, 0.12 + (diag > 0 ? 0.12 : 0) + t * 0.15);
	const n = fbm(p[0] * 0.3, p[1] * 0.3, p[2] * 0.3, 2);
	col = mix(col, pal.plateDark, n * 0.2);
	// bevel: bright upper-left rim, dark lower-right rim, icy edge tint
	const e = info.ePx;
	if (e < 0.22) col = (t < 0.5 || s < 0.5) ? mix(col, [252, 252, 255], 0.55) : mix(col, pal.plateDark, 0.5);
	else if (e < 0.45) col = mix(col, pal.plateEdge, 0.3);
	// fine cracks
	const cr = Math.abs(vnoise(p[0] * 0.5 + 40, p[1] * 0.5, p[2] * 0.5) - 0.5);
	if (cr < 0.01 && e > 0.45) col = tint(col, 0.8);
	if (f === 'down') col = tint(col, 0.75);
	return [col, 255];
}

function paintKnuckle(M, c, f, s, t, p, info) {
	const pal = PAL[M.id];
	let col = mix(pal.skin, [208, 226, 240], 0.35);
	const k = ((p[0] - c.from[0]) / (c.to[0] - c.from[0])) * 4;
	const fr = k - Math.floor(k);
	if (fr < 0.12 || fr > 0.88) col = tint(col, 0.8);
	if (f === 'north') col = mix(col, [228, 242, 252], 0.3 * Math.sin(fr * Math.PI));
	return [col, 255];
}

function paintFlap(M, c, f, s, t, p, info) {
	const pal = PAL[M.id];
	const inner = f === 'north';
	const n = fbm(p[0] * 2, p[1] * 2, p[2] * 2, 3);
	let col;
	if (f === 'south') {
		const B = M.P.parts.body;
		col = skinPx(M, B.base, B.box, 'south', [p[0], p[1], B.box[5]], dimsOf(B.box)).slice(0, 3);
		const O = M.P.parts.body, e = O.infl, ob = [O.box[0] - e, O.box[1] - e, O.box[2] - e, O.box[3] + e, O.box[4] + e, O.box[5] + e];
		const op = skinPx(M, O.outer, ob, 'south', [p[0], p[1], ob[5]], dimsOf(O.box));
		if (op[3] >= 128) col = op.slice(0, 3);
		void pal;
	} else col = mix([226, 116, 120], [150, 34, 44], n * 0.8 + (inner ? 0.2 : 0));
	// torn inner edge (toward the hole)
	const dIn = Math.abs(p[0]); // the hole is at x = 0
	if (dIn < 0.25 + vnoise(p[1] * 4, 3, 1) * 0.35) return [col, 0];
	if (f === 'south' && dIn < 0.55 + vnoise(p[1] * 4, 3, 1) * 0.35) col = mix(col, [200, 90, 96], 0.7);
	return [col, 255];
}

function paintStump(M, c, f, s, t, p, info) {
	const pal = PAL[M.id];
	const b = c.seg;
	const cxm = (b[0] + b[3]) / 2, czm = (b[2] + b[5]) / 2;
	if (f === 'down') {
		const d = Math.hypot(p[0] - cxm, p[2] - czm);
		const n = fbm(p[0] * 3, 0, p[2] * 3, 2);
		if (d < 0.25) return [[150, 60, 50], 255];        // marrow
		if (d < 0.62) return [mix(BONE, BONE_DARK, n * 0.6), 255];
		if (d < 0.8) return [[70, 12, 18], 255];
		return [mix(MUS, [226, 110, 112], n * 0.6), 255];
	}
	const fromCut = p[1] - c.from[1];
	const n = fbm(p[0] * 2, p[1] * 2, p[2] * 2, 3);
	let col = skinPx(M, c.region, c.full, f, f === 'east' ? [c.full[3], p[1], p[2]] : f === 'west' ? [c.full[0], p[1], p[2]] : f === 'north' ? [p[0], p[1], c.full[2]] : f === 'south' ? [p[0], p[1], c.full[5]] : p, dimsOf(c.full)).slice(0, 3);
	void pal;
	const rag = vnoise(p[0] * 4 + p[2] * 4, 2, 5) * 0.7;
	if (fromCut < 0.35 + rag) col = mix(MUS, [226, 110, 112], n * 0.5);
	else if (fromCut < 0.55 + rag) col = mix(col, [170, 60, 60], 0.6);
	return [col, 255];
}

function paintCrystal(M, c, f, s, t, p, info) {
	const diag = (s - 0.5) + (t - 0.5) * 0.7;
	let col = mix([206, 232, 248], [120, 170, 214], 0.2 + (diag > 0 ? 0.25 : 0) + t * 0.2);
	const n = fbm(p[0] * 0.8, p[1] * 0.8, p[2] * 0.8, 3);
	col = mix(col, [236, 248, 255], smooth(0.6, 0.8, n) * 0.6);
	if (info.ePx < 0.18) col = mix(col, [250, 254, 255], 0.6);
	const k = Math.abs(((p[0] + p[1] * 0.7) / 0.9) % 1);
	if (k < 0.05) col = mix(col, [150, 196, 230], 0.5);
	return [col, 255];
}

function paintRock(M, c, f, s, t, p, info) {
	const n = fbm(p[0] * 0.9 + 3, p[1] * 0.9, p[2] * 0.9, 4);
	let col = mix([88, 84, 80], [150, 146, 138], n);
	if (Math.abs(fbm(p[0] * 0.6 + 9, p[1] * 0.6, p[2] * 0.6, 3) - 0.5) < 0.02) col = tint(col, 0.6);
	if (vnoise(p[0] * 3, p[1] * 3, p[2] * 3) > 0.8) col = mix(col, [96, 120, 70], 0.5);
	col = tint(col, 0.85 + 0.15 * smooth(0, 0.6, info.ePx));
	return [col, 255];
}

function shade(M) {
	return (c, f, s, t, p, info) => {
		switch (c.mat) {
			case 'skel': return paintSkel(M, c, f, s, t, p, info);
			case 'mus': return paintMuscle(M, c, f, s, t, p, info);
			case 'skin': case 'outer': return paintSkinLayer(M, c, f, s, t, p, info);
			case 'hair': return paintHair(M, c, f, s, t, p, info);
			case 'fur': return paintFur(M, c, f, s, t, p, info);
			case 'plate': return paintPlate(M, c, f, s, t, p, info);
			case 'knuckle': return paintKnuckle(M, c, f, s, t, p, info);
			case 'flap': return paintFlap(M, c, f, s, t, p, info);
			case 'stump': return paintStump(M, c, f, s, t, p, info);
			case 'crystal': return paintCrystal(M, c, f, s, t, p, info);
			case 'rock': return paintRock(M, c, f, s, t, p, info);
			default: return [[255, 0, 255], 255];
		}
	};
}

// ---------------------------------------------------------------- variants
function lum(px, i) { return (0.3 * px[i] + 0.59 * px[i + 1] + 0.11 * px[i + 2]) / 255; }

/** Hardening: every skin-layer texel turned to faceted blue-white crystal. */
function hardened(M, layerOf) {
	const out = Buffer.from(M.tex);
	for (let i = 0; i < M.TW * M.TH; i++) {
		const j = i * 4;
		if (out[j + 3] === 0 || layerOf[i] !== 2) continue;
		const x = i % M.TW, y = (i / M.TW) | 0;
		const l = lum(out, j);
		const tt = clamp((l - 0.12) / 0.75, 0, 1);
		let r = 52 + tt * 168, g = 96 + tt * 150, b = 150 + tt * 105;
		if ((x + y * 2) % 9 === 0) { r += 34; g += 26; b += 18; }
		if ((x * 2 - y + 1024) % 13 === 0) { r -= 30; g -= 24; b -= 12; }
		const nn = (hash3(x, y, 3) - 0.5) * 12;
		const k = 0.62; // how far toward the crystal
		out[j] = clamp(out[j] * (1 - k) + (r + nn) * k); out[j + 1] = clamp(out[j + 1] * (1 - k) + (g + nn) * k); out[j + 2] = clamp(out[j + 2] * (1 - k) + (b + nn) * k);
	}
	return out;
}

/** The corpse: the nape torn open where the shifter came out. */
function corpse(M, info) {
	const out = Buffer.from(M.tex);
	const P = M.P;
	const ny = P.neckY - 1.35, rx = 1.6, ry = 1.75;
	for (let i = 0; i < M.TW * M.TH; i++) {
		const j = i * 4;
		const ti = info[i];
		if (!ti || out[j + 3] === 0) continue;
		const c = ti.c;
		if (ti.f !== 'south' && !(ti.f === 'up' && c.tag === 'chest')) continue;
		if (c.tag !== 'chest') continue;
		const p = ti.p;
		const d = Math.hypot(p[0] / rx, (p[1] - ny) / ry) + (vnoise(p[0] * 3, p[1] * 3, 9) - 0.5) * 0.25;
		if (d > 1.25) continue;
		if (c.mat === 'skin' || c.mat === 'outer') {
			if (c.mat === 'outer') { if (d < 1.12) out[j + 3] = 0; continue; }
			if (d < 1.0) out[j + 3] = 0;
			else { out[j] = 176; out[j + 1] = 74; out[j + 2] = 76; } // a raw rim round the hole
		} else if (c.mat === 'mus') {
			let col;
			if (d < 0.42) col = [34, 6, 10];
			else if (d < 0.7) col = mix([92, 14, 22], [150, 30, 40], (d - 0.42) / 0.28);
			else col = mix([210, 100, 106], [236, 150, 150], vnoise(p[0] * 5, p[1] * 5, 2));
			out[j] = col[0]; out[j + 1] = col[1]; out[j + 2] = col[2];
		}
	}
	return out;
}

module.exports = { shade, hardened, corpse, PAL, skinPal, skinPx };
