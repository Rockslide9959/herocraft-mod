// v0.16.7 (user) -- Titan Shifting, VISUALS: the four Titan Shifter rigs.
// Every Titan wears a 64x64 PLAYER SKIN (scratchpad/titan_skins/<model>.png) on standard player proportions -- head 8,
// body 8x12x4, arms 4x12x4, legs 4x12x4 in a 32 px tall model -- so the skin maps 1:1 and the Titan reads as "that skin,
// Titan-sized" (the renderer scales 32 px up to the type's height: Attack 15, Armoured 15, Female 12, Beast 14 blocks).
// Extra joint bones over a player's: jaw, forearm, shin, foot. The skin is cut across them like a player limb would be
// (every texel samples the skin pixel of its own 3D point on the WHOLE part), so a bent elbow or open jaw just parts it.
// Each part is THREE layers: skeleton (thin bones, ribs, skull) inside muscle (the skin box inset) inside the skin
// shell (the base layer, exact player box) with the skin's second layer (hat / jacket / sleeves / pants) raised over it.
// Conventions (Bedrock/GeckoLib): +Y up, the model faces -Z, the entity's RIGHT side is -X; rotation X > 0 tips a
// bone's top forward (a hanging limb's free end swings back). Boxes are [x0, y0, z0, x1, y1, z1] in model px.
'use strict';
const path = require('path');
const L = require('./lib_concept_models.js');
const PK = require('./pngkit.js');
const { Model } = L;

const D = 6;          // texels per model pixel (one skin pixel = 6x6 texels)
const SKIN_E = 0;     // the skin shell IS the outer surface (kept for the animation ground solver)
const MUS_IN = 0.3;   // the muscle sits this far inside the skin (px)
const HAT = 0.5, OUTER = 0.25; // vanilla's second-layer inflation (head / everything else)

const TYPES = {
	titan_form: { name: 'Attack', heightBlocks: 15, bone: 1.0 },
	titan_female: { name: 'Female', heightBlocks: 12, bone: 0.85 },
	// the skin's eyes are yellow: they glow
	titan_armored: { name: 'Armoured', heightBlocks: 15, bone: 1.2, glow: (r, g, b) => r > 220 && g > 210 && b < 80 },
	titan_beast: { name: 'Beast', heightBlocks: 14, bone: 1.15, hunch: 8 },
};

// standard player geometry (the old v0.12 titan_form layout); RIGHT-side (-x) limb boxes, the left is mirrored
const PLAYER = {
	hipY: 12, legX: 2, kneeY: 6, ankleY: 1.5, neckY: 24, elbowY: 18, wristY: 14, jawTop: 26,
	head: [-4, 24, -4, 4, 32, 4], chest: [-4, 18, -2, 4, 24, 2], abdomen: [-4, 12, -2, 4, 18, 2],
	uarm: [-8, 18, -2, -4, 24, 2], farm: [-8, 14, -2, -4, 18, 2], hand: [-8, 12, -2, -4, 14, 2],
	thigh: [-3.9, 6, -2, 0.1, 12, 2], shin: [-3.9, 1.5, -2, 0.1, 6, 2], foot: [-3.9, 0, -2, 0.1, 1.5, 2],
	neck: [-1.3, 23.0, -1.2, 1.3, 25.2, 1.2], shoulder: [-5, 22],
	// whole parts (what one skin region covers) and their skin regions [u, v] (base, outer)
	parts: {
		head: { box: [-4, 24, -4, 4, 32, 4], base: [0, 0], outer: [32, 0], infl: HAT },
		body: { box: [-4, 12, -2, 4, 24, 2], base: [16, 16], outer: [16, 32], infl: OUTER },
		rarm: { box: [-8, 12, -2, -4, 24, 2], base: [40, 16], outer: [40, 32], infl: OUTER },
		larm: { box: [4, 12, -2, 8, 24, 2], base: [32, 48], outer: [48, 48], infl: OUTER },
		rleg: { box: [-3.9, 0, -2, 0.1, 12, 2], base: [0, 16], outer: [0, 32], infl: OUTER },
		lleg: { box: [-0.1, 0, -2, 3.9, 12, 2], base: [16, 48], outer: [0, 48], infl: OUTER },
	},
};

const mirrorBox = b => [-b[3], b[1], b[2], -b[0], b[4], b[5]];
const cx = b => (b[0] + b[3]) / 2, cz = b => (b[2] + b[5]) / 2;

const SKIN_DIR = path.join(__dirname, '..', 'titan_skins');
function loadSkin(id) {
	const im = PK.decode(path.join(SKIN_DIR, `${id}.png`));
	if (im.w !== 64 || (im.h !== 64 && im.h !== 32)) throw new Error(`${id}: not a 64x64 / 64x32 skin`);
	if (im.h === 32) { // legacy: no second layer except the hat, the left limbs mirror the right ones
		const px = Buffer.alloc(64 * 64 * 4);
		im.px.copy(px, 0, 0, 64 * 32 * 4);
		const cp = (sx, sy, dx, dy, w, h, flip) => { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const s = ((sy + y) * 64 + sx + (flip ? w - 1 - x : x)) * 4; px.copy(px, ((dy + y) * 64 + dx + x) * 4, s, s + 4); } };
		for (const [sx, dx] of [[0, 16], [40, 32]]) { // leg -> left leg, arm -> left arm (faces mirrored and swapped)
			cp(sx + 4, 16, dx + 4, 48, 4, 4, true); cp(sx + 8, 16, dx + 8, 48, 4, 4, true);
			cp(sx + 8, 20, dx, 52, 4, 12, true); cp(sx + 4, 20, dx + 4, 52, 4, 12, true); cp(sx, 20, dx + 8, 52, 4, 12, true); cp(sx + 12, 20, dx + 12, 52, 4, 12, true);
		}
		im.px = px; im.h = 64;
	}
	return im;
}

function build(id) {
	const P = Object.assign({}, PLAYER, TYPES[id]);
	const M = new Model(id, { D, TW: 1024 });
	M.P = P; M.id = id;
	M.skin = loadSkin(id);
	const hunch = P.hunch || 0;
	const H = P.head;
	M.jawTop = P.jawTop;
	// ---------------- bones (pivots = the player model's)
	M.bone('root', null, [0, 0, 0]);
	M.bone('body', 'root', [0, P.hipY, 0], [hunch, 0, 0]);
	M.bone('head', 'body', [0, P.neckY, 0], [-hunch * 0.85, 0, 0]);
	M.bone('jaw', 'head', [0, P.jawTop, H[5] - 1.6]);
	for (const side of ['right', 'left']) {
		const sx = side === 'right' ? 1 : -1;
		M.bone(`${side}_arm`, 'body', [P.shoulder[0] * sx, P.shoulder[1], 0], [-hunch, 0, 0]);
		M.bone(`${side}_forearm`, `${side}_arm`, [cx(P.uarm) * sx, P.elbowY, 0]);
	}
	for (const side of ['right', 'left']) {
		const sx = side === 'right' ? 1 : -1;
		M.bone(`${side}_leg`, 'root', [P.legX * sx, P.hipY, 0]);
		M.bone(`${side}_shin`, `${side}_leg`, [P.legX * sx, P.kneeY, 0]);
		M.bone(`${side}_foot`, `${side}_shin`, [P.legX * sx, P.ankleY, 0]);
	}
	const backZ = P.chest[5] + OUTER;
	M.bone('nape_flap_l', 'body', [1.75, P.neckY - 1.3, backZ]);
	M.bone('nape_flap_r', 'body', [-1.75, P.neckY - 1.3, backZ]);
	for (const side of ['right', 'left']) {
		const sx = side === 'right' ? 1 : -1;
		M.bone(`${side}_arm_stump`, 'body', [P.shoulder[0] * sx, P.shoulder[1], 0], [-hunch, 0, 0]);
	}
	for (const side of ['right', 'left']) {
		const sx = side === 'right' ? 1 : -1;
		M.bone(`${side}_leg_stump`, 'root', [P.legX * sx, P.hipY, 0]);
	}

	// ---------------- segments: one per (bone, slice of a skin part)
	// cutLo / cutHi: the segment's bottom / top face is a cut through its part (hidden unless a joint opens);
	// sameLo / sameHi: that cut is against a segment on the SAME bone (never visible).
	const segs = [];
	const seg = (bone, tag, part, box, o = {}) => segs.push(Object.assign({ bone, tag, part, box }, o));
	seg('body', 'abdomen', 'body', P.abdomen, { cutHi: true, sameHi: true });
	seg('body', 'chest', 'body', P.chest, { cutLo: true, sameLo: true, top: true });
	seg('head', 'head', 'head', [H[0], P.jawTop, H[2], H[3], H[4], H[5]], { cutLo: true, top: true, mouthLo: true });
	seg('jaw', 'jaw', 'head', [H[0], H[1], H[2], H[3], P.jawTop, H[5]], { cutHi: true, mouthHi: true });
	for (const side of ['right', 'left']) {
		const m = side === 'right' ? b => b : mirrorBox;
		const a = side === 'right' ? 'rarm' : 'larm', l = side === 'right' ? 'rleg' : 'lleg';
		seg(`${side}_arm`, 'uarm', a, m(P.uarm), { side, cutLo: true, top: true });
		seg(`${side}_forearm`, 'farm', a, m(P.farm), { side, cutHi: true, cutLo: true, sameLo: true });
		seg(`${side}_forearm`, 'hand', a, m(P.hand), { side, cutHi: true, sameHi: true, bottom: true });
		seg(`${side}_leg`, 'thigh', l, m(P.thigh), { side, cutLo: true });
		seg(`${side}_shin`, 'shin', l, m(P.shin), { side, cutHi: true, cutLo: true });
		seg(`${side}_foot`, 'foot', l, m(P.foot), { side, cutHi: true });
	}
	M.segs = segs;
	const add = (bone, mat, tag, b, extra = {}) => {
		const c = M.box(bone, b[0], b[1], b[2], b[3], b[4], b[5], mat, { tag });
		Object.assign(c, extra);
		return c;
	};
	M.add = add;
	for (const s of segs) {
		const b = s.box, part = P.parts[s.part], i = MUS_IN;
		// muscle: inset sideways, and at the ends of a chain (the crown, the shoulder, the fingertips)
		// (a hair inside at the joint cuts and the soles too, so muscle and skin never share a plane and z-fight)
		const mb = [b[0] + i, b[1] + (s.bottom ? i : 0.06), b[2] + i, b[3] - i, b[4] - (s.top && s.tag !== 'chest' ? i : 0.06), b[5] - i];
		add(s.bone, 'mus', s.tag, mb, { seg: mb, side: s.side, layer: 1 });
		const sk = { seg: b, side: s.side, layer: 2, part: s.part, full: part.box, region: part.base, s, dims: [0, 1, 2].map(q => Math.round(part.box[q + 3] - part.box[q])) };
		add(s.bone, 'skin', s.tag, b, sk);
		// the second layer: inflated outward, but flush at the cuts (so neighbours never overlap)
		const e = part.infl, F = part.box;
		const ob = [b[0] - e, s.cutLo ? b[1] : b[1] - (s.tag === 'foot' ? 0 : e), b[2] - e, b[3] + e, s.cutHi ? b[4] : b[4] + e, b[5] + e];
		add(s.bone, 'outer', s.tag, ob, Object.assign({}, sk, { outer: true, region: part.outer, full: [F[0] - e, F[1] - e, F[2] - e, F[3] + e, F[4] + e, F[5] + e] }));
	}

	// ---------------- skeleton
	const k = P.bone;
	const sk = (bone, tag, b) => add(bone, 'skel', tag, b, { layer: 0, seg: b });
	const C = P.chest, A = P.abdomen, N = P.neck;
	const bz = Math.min(C[5], A[5]) - MUS_IN;
	sk('body', 'spine', [-0.55 * k, A[1] + 0.4, bz - 1.45 * k, 0.55 * k, C[4] - 0.15, bz - 0.35]);
	sk('body', 'ribs', [C[0] + 0.6, C[1] + 0.4, C[2] + 0.55, C[3] - 0.6, C[4] - 0.75, C[5] - 0.6]);
	sk('body', 'clavicle', [C[0] + 0.45, C[4] - 1.05, C[2] + 0.45, C[3] - 0.45, C[4] - 0.45, C[2] + 1.0]);
	sk('body', 'pelvis', [A[0] + 0.6, A[1] + 0.2, A[2] + 0.55, A[3] - 0.6, A[1] + 2.3, A[5] - 0.55]);
	sk('head', 'cervical', [-0.5 * k, N[1] - 0.1, cz(N) + 0.1, 0.5 * k, N[4] - 0.05, cz(N) + 1.0 * k]);
	sk('head', 'skull', [H[0] + 0.55, P.jawTop + 0.05, H[2] + 0.55, H[3] - 0.55, H[4] - 0.55, H[5] - 0.55]);
	sk('jaw', 'mandible', [H[0] + 0.8, H[1] + 0.35, H[2] + 0.55, H[3] - 0.8, P.jawTop + 0.05, H[5] - 1.7]);
	for (const side of ['right', 'left']) {
		const m = side === 'right' ? b => b : mirrorBox;
		const ua = m(P.uarm), fa = m(P.farm), hd = m(P.hand), th = m(P.thigh), sh = m(P.shin), ft = m(P.foot);
		const t = 0.45 * k;
		sk(`${side}_arm`, 'humerus', [cx(ua) - t, ua[1] + 0.2, cz(ua) - t, cx(ua) + t, ua[4] - 1.0, cz(ua) + t]);
		sk(`${side}_arm`, 'shoulderball', [cx(ua) - 0.8 * k, ua[4] - 1.75, cz(ua) - 0.8 * k, cx(ua) + 0.8 * k, ua[4] - 0.45, cz(ua) + 0.8 * k]);
		sk(`${side}_forearm`, 'radius', [cx(fa) - 0.8 * k, fa[1] + 0.1, cz(fa) - 0.3 * k, cx(fa) - 0.2 * k, fa[4] - 0.2, cz(fa) + 0.3 * k]);
		sk(`${side}_forearm`, 'ulna', [cx(fa) + 0.2 * k, fa[1] + 0.1, cz(fa) - 0.3 * k, cx(fa) + 0.75 * k, fa[4] - 0.2, cz(fa) + 0.3 * k]);
		sk(`${side}_forearm`, 'handbones', [hd[0] + 0.55, hd[1] + 0.45, hd[2] + 0.55, hd[3] - 0.55, hd[4] - 0.05, hd[5] - 0.55]);
		sk(`${side}_leg`, 'femur', [cx(th) - 0.58 * k, th[1] + 0.25, cz(th) - 0.58 * k, cx(th) + 0.58 * k, th[4] - 1.1, cz(th) + 0.58 * k]);
		sk(`${side}_leg`, 'femurhead', [cx(th) - 0.9 * k, th[4] - 1.8, cz(th) - 0.85 * k, cx(th) + 0.9 * k, th[4] - 0.45, cz(th) + 0.85 * k]);
		sk(`${side}_shin`, 'tibia', [cx(sh) - 0.7 * k, sh[1] + 0.1, cz(sh) - 0.55 * k, cx(sh) + 0.2 * k, sh[4] - 0.1, cz(sh) + 0.45 * k]);
		sk(`${side}_shin`, 'fibula', [cx(sh) + 0.4 * k, sh[1] + 0.2, cz(sh) - 0.25 * k, cx(sh) + 0.85 * k, sh[4] - 0.4, cz(sh) + 0.25 * k]);
		sk(`${side}_shin`, 'patella', [cx(sh) - 0.55 * k, sh[4] - 0.8, sh[2] + 0.4, cx(sh) + 0.45 * k, sh[4] + 0.25, sh[2] + 0.85]);
		sk(`${side}_foot`, 'footbones', [ft[0] + 0.5, ft[1] + 0.1, ft[2] + 0.5, ft[3] - 0.5, ft[4] - 0.05, ft[5] - 0.5]);
	}

	// ---------------- corpse-only nape flaps: skin + muscle peeled back from the opening, hinged at their outer edge
	const ny0 = P.neckY - 2.7, ny1 = P.neckY + 0.15;
	add('nape_flap_l', 'flap', 'flap', [0.02, ny0, backZ - 0.05, 1.75, ny1, backZ + 0.3], { layer: 1, corpseOnly: true });
	add('nape_flap_r', 'flap', 'flap', [-1.75, ny0, backZ - 0.05, -0.02, ny1, backZ + 0.3], { layer: 1, corpseOnly: true });
	M.nape = [0, P.neckY - 1.2, backZ];

	// ---------------- severed stumps (drawn only while the limb is gone)
	for (const side of ['right', 'left']) {
		const m = side === 'right' ? b => b : mirrorBox;
		const ua = m(P.uarm), th = m(P.thigh);
		const ap = side === 'right' ? 'rarm' : 'larm', lp = side === 'right' ? 'rleg' : 'lleg';
		add(`${side}_arm_stump`, 'stump', 'stump', [ua[0] + 0.05, ua[4] - 2.6, ua[2] + 0.05, ua[3] - 0.05, ua[4] - 0.1, ua[5] - 0.05], { layer: 1, stump: true, seg: ua, part: ap, full: P.parts[ap].box, region: P.parts[ap].base });
		add(`${side}_leg_stump`, 'stump', 'stump', [th[0] + 0.05, th[4] - 2.8, th[2] + 0.05, th[3] - 0.05, th[4] - 0.3, th[5] - 0.05], { layer: 1, stump: true, seg: th, part: lp, full: P.parts[lp].box, region: P.parts[lp].base });
	}

	EXTRAS[id](M, P, add);
	return M;
}

const EXTRAS = {
	titan_form() {},
	titan_armored() {},
	titan_female(M, P, add) {
		// Crystal Fists: crystal over the fists and shins (and the nape), drawn only while the move runs
		for (const side of ['right', 'left']) {
			const m = side === 'right' ? b => b : mirrorBox;
			const hd = m(P.hand), sh = m(P.shin);
			M.bone(`crystal_${side}_fist`, `${side}_forearm`, [cx(hd), hd[1], cz(hd)]);
			add(`crystal_${side}_fist`, 'crystal', 'crystal', [hd[0] - 0.45, hd[1] - 0.45, hd[2] - 0.75, hd[3] + 0.45, hd[1] + 2.3, hd[5] + 0.45], { layer: 2 });
			M.bone(`crystal_${side}_shin`, `${side}_shin`, [cx(sh), sh[1], cz(sh)]);
			add(`crystal_${side}_shin`, 'crystal', 'crystal', [sh[0] - 0.4, sh[1] + 0.3, sh[2] - 0.75, sh[3] + 0.4, sh[4] - 0.3, sh[2] + 0.9], { layer: 2 });
		}
		M.bone('crystal_nape', 'body', [0, P.neckY, P.chest[5]]);
		add('crystal_nape', 'crystal', 'crystal', [-1.9, P.neckY - 3.0, P.chest[5] + 0.3, 1.9, P.neckY + 0.3, P.chest[5] + 0.9], { layer: 2 });
	},
	titan_beast(M, P, add) {
		const hd = P.hand;
		M.bone('boulder', 'right_forearm', [cx(hd), hd[1], cz(hd)]);
		add('boulder', 'rock', 'boulder', [cx(hd) - 2.6, hd[1] - 4.4, cz(hd) - 2.9, cx(hd) + 2.6, hd[1] + 0.9, cz(hd) + 2.3], { layer: 2 });
	},
};

module.exports = { TYPES, build, mirrorBox, SKIN_E, D, cx, cz, PLAYER };
