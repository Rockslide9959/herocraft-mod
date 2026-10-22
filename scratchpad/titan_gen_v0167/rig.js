// v0.16.7 (user) -- Titan Shifting revamp, VISUALS: the four Titan Shifter rigs.
// One rig per type (same core bones as the old titan_form: root > body > head/arms, root > legs) plus jaw / forearm /
// shin children, corpse-only nape flaps and the severed-limb stumps. Every body part is THREE layers of cubes:
// skeleton (thin bones, ribs, skull) inside muscle cubes inside slightly larger skin cubes.
// Conventions (Bedrock/GeckoLib): +Y up, the model faces -Z, the entity's RIGHT side is -X; rotation X > 0 tips a
// bone's top forward (a hanging limb's free end swings back). Boxes are [x0, y0, z0, x1, y1, z1] in model px.
'use strict';
const L = require('./lib_concept_models.js');
const { Model } = L;

const D = 6;          // texels per model pixel
const SKIN_E = 0.14;  // skin shell sits this far outside the muscle (px)

// Every limb box is the RIGHT-side (-x) one; left is mirrored.
const TYPES = {
	titan_form: { // the Attack Titan: lean, long-limbed, long dark hair, exposed cheek muscle, green eyes
		name: 'Attack', heightBlocks: 15,
		hipY: 13.5, legX: 1.95, kneeY: 7.0,
		thigh: [-3.7, 6.9, -1.8, -0.2, 13.75, 1.8], shin: [-3.55, 1.25, -1.6, -0.35, 7.25, 1.6], foot: [-3.6, 0, -3.4, -0.3, 1.45, 1.7],
		abdomen: [-3.3, 13.25, -1.8, 3.3, 18.25, 1.9], chest: [-4.25, 18.0, -2.3, 4.25, 24.25, 2.35],
		neckY: 24.25, neck: [-1.6, 24.0, -1.3, 1.6, 26.0, 1.9], head: [-3.2, 25.5, -3.5, 3.2, 32.0, 3.1], jawFrac: 0.27,
		shoulder: [-5.4, 23.4], uarm: [-7.0, 17.25, -1.5, -4.0, 24.25, 1.5], elbowY: 17.5,
		farm: [-6.9, 11.25, -1.4, -4.1, 17.75, 1.4], hand: [-6.8, 8.25, -1.5, -4.2, 11.5, 1.2],
		bone: 1.0,
	},
	titan_female: { // the Female Titan: slim, athletic, blonde, thin skin on the limbs, crystal-ready knuckles
		name: 'Female', heightBlocks: 12,
		hipY: 14.5, legX: 1.8, kneeY: 7.75,
		thigh: [-3.4, 7.75, -1.7, -0.25, 14.75, 1.75], shin: [-3.2, 1.25, -1.4, -0.4, 8.0, 1.4], foot: [-3.25, 0, -3.0, -0.35, 1.4, 1.5],
		abdomen: [-2.75, 15.5, -1.6, 2.75, 19.25, 1.7], hips: [-3.35, 13.75, -1.85, 3.35, 15.75, 1.95], chest: [-3.6, 19.0, -2.0, 3.6, 24.5, 2.05],
		neckY: 24.5, neck: [-1.3, 24.25, -1.1, 1.3, 26.25, 1.6], head: [-2.9, 25.75, -3.1, 2.9, 32.0, 2.8], jawFrac: 0.26,
		shoulder: [-4.85, 23.8], uarm: [-6.2, 18.0, -1.3, -3.5, 24.5, 1.3], elbowY: 18.25,
		farm: [-6.1, 12.25, -1.2, -3.6, 18.5, 1.2], hand: [-6.0, 9.5, -1.3, -3.7, 12.5, 1.0],
		bone: 0.85,
	},
	titan_armored: { // the Armoured Titan: bulky, pale crystal plates in layers, glowing white eyes in a plated face
		name: 'Armoured', heightBlocks: 15,
		hipY: 12.5, legX: 2.4, kneeY: 6.75,
		thigh: [-4.7, 6.6, -2.3, -0.3, 12.75, 2.3], shin: [-4.5, 1.5, -2.1, -0.5, 7.0, 2.1], foot: [-4.6, 0, -3.9, -0.4, 1.75, 2.2],
		abdomen: [-4.2, 12.25, -2.3, 4.2, 17.5, 2.4], chest: [-5.2, 17.25, -2.9, 5.2, 24.0, 2.8],
		neckY: 24.0, neck: [-2.2, 23.75, -1.9, 2.2, 25.75, 2.2], head: [-3.3, 25.25, -3.4, 3.3, 31.5, 3.2], jawFrac: 0.27,
		shoulder: [-6.6, 22.8], uarm: [-8.6, 16.5, -1.9, -5.0, 24.0, 1.9], elbowY: 16.75,
		farm: [-8.6, 10.25, -1.9, -5.0, 17.0, 1.9], hand: [-8.4, 7.25, -1.9, -5.2, 10.5, 1.5],
		bone: 1.25,
	},
	titan_beast: { // the Beast Titan: ape-like, hunched, arms past the knees, fur
		name: 'Beast', heightBlocks: 14,
		hipY: 11.0, legX: 2.3, kneeY: 6.0,
		thigh: [-4.4, 5.75, -2.2, -0.2, 11.25, 2.2], shin: [-4.2, 1.25, -1.9, -0.4, 6.0, 1.9], foot: [-4.4, 0, -3.8, -0.2, 1.4, 2.0],
		abdomen: [-4.0, 10.75, -2.4, 4.0, 16.5, 2.6], chest: [-5.0, 16.25, -3.0, 5.0, 25.0, 3.2],
		neckY: 25.0, neck: [-2.0, 24.75, -2.0, 2.0, 26.5, 1.8], head: [-3.4, 26.0, -4.1, 3.4, 32.4, 2.5], jawFrac: 0.28,
		shoulder: [-6.6, 24.2], uarm: [-8.4, 15.5, -1.9, -4.8, 24.75, 1.9], elbowY: 15.75,
		farm: [-8.3, 6.9, -1.8, -4.9, 16.0, 1.8], hand: [-8.5, 3.0, -2.1, -4.7, 7.1, 1.8],
		hunch: 20, bone: 1.2,
	},
};

const mirrorBox = b => [-b[3], b[1], b[2], -b[0], b[4], b[5]];
const cx = b => (b[0] + b[3]) / 2, cz = b => (b[2] + b[5]) / 2;

function build(id) {
	const P = TYPES[id];
	const M = new Model(id, { D, TW: 1024 });
	M.P = P; M.id = id;
	const hunch = P.hunch || 0;
	const H = P.head;
	const jawTop = H[1] + (H[4] - H[1]) * P.jawFrac;
	M.jawTop = jawTop;
	// ---------------- bones
	M.bone('root', null, [0, 0, 0]);
	M.bone('body', 'root', [0, P.hipY, 0], [hunch, 0, 0]);
	M.bone('head', 'body', [0, P.neckY, 0], [-hunch * 0.85, 0, 0]);
	M.bone('jaw', 'head', [0, jawTop, H[5] - 1.6]);
	for (const side of ['right', 'left']) {
		const sx = side === 'right' ? 1 : -1;
		M.bone(`${side}_arm`, 'body', [P.shoulder[0] * sx, P.shoulder[1], 0], [-hunch, 0, 0]);
		M.bone(`${side}_forearm`, `${side}_arm`, [cx(P.uarm) * sx, P.elbowY, 0]);
	}
	for (const side of ['right', 'left']) {
		const sx = side === 'right' ? 1 : -1;
		M.bone(`${side}_leg`, 'root', [P.legX * sx, P.hipY, 0]);
		M.bone(`${side}_shin`, `${side}_leg`, [cx(P.thigh) * sx, P.kneeY, 0]);
		M.bone(`${side}_foot`, `${side}_shin`, [cx(P.shin) * sx, P.foot[4] - 0.5, cz(P.shin)]);
	}
	const backZ = P.chest[5] + SKIN_E;
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

	// ---------------- flesh: a muscle cube + a skin shell per segment
	const segs = [];
	const seg = (bone, tag, box, extra = {}) => segs.push(Object.assign({ bone, tag, box }, extra));
	seg('body', 'abdomen', P.abdomen);
	if (P.hips) seg('body', 'hips', P.hips);
	seg('body', 'chest', P.chest);
	if (id !== 'titan_armored') {
		const C = P.chest, ch = C[4] - C[1];
		seg('body', 'traps', [-P.neck[3] - 1.3, C[4] - 0.8, C[2] + 1.0, P.neck[3] + 1.3, C[4] + 0.55, C[5] - 0.25]);
		if (id === 'titan_beast') seg('body', 'pecs', [C[0] + 0.45, C[1] + ch * 0.42, C[2] - 0.3, C[3] - 0.45, C[4] - 0.7, C[2] + 0.3]);
	}
	seg('head', 'neck', P.neck);
	seg('head', 'head', [H[0], jawTop, H[2], H[3], H[4], H[5]]);
	seg('jaw', 'jaw', [H[0] + 0.3, H[1], H[2] + 0.12, H[3] - 0.3, jawTop, H[5] - 0.7]);
	for (const side of ['right', 'left']) {
		const m = side === 'right' ? b => b : mirrorBox;
		seg(`${side}_arm`, 'uarm', m(P.uarm), { side });
		seg(`${side}_forearm`, 'farm', m(P.farm), { side });
		seg(`${side}_forearm`, 'hand', m(P.hand), { side });
		seg(`${side}_leg`, 'thigh', m(P.thigh), { side });
		seg(`${side}_shin`, 'shin', m(P.shin), { side });
		seg(`${side}_foot`, 'foot', m(P.foot), { side });
	}
	M.segs = segs;
	const add = (bone, mat, tag, b, extra = {}) => {
		const c = M.box(bone, b[0], b[1], b[2], b[3], b[4], b[5], mat, { tag });
		Object.assign(c, extra);
		return c;
	};
	M.add = add;
	for (const s of segs) {
		add(s.bone, 'mus', s.tag, s.box, { seg: s.box, side: s.side, layer: 1 });
		const e = SKIN_E, b = s.box;
		add(s.bone, 'skin', s.tag, [b[0] - e, b[1] - e, b[2] - e, b[3] + e, b[4] + e, b[5] + e], { seg: b, side: s.side, layer: 2 });
	}

	// ---------------- skeleton
	const k = P.bone;
	const sk = (bone, tag, b) => add(bone, 'skel', tag, b, { layer: 0, seg: b });
	const C = P.chest, A = P.abdomen, N = P.neck;
	const bz = Math.min(C[5], A[5]);
	sk('body', 'spine', [-0.55 * k, A[1] + 0.4, bz - 1.55 * k, 0.55 * k, C[4] - 0.15, bz - 0.45]);
	sk('body', 'ribs', [C[0] + 0.55, C[1] + 0.9, C[2] + 0.4, C[3] - 0.55, C[4] - 0.75, C[5] - 0.55]);
	sk('body', 'clavicle', [C[0] + 0.35, C[4] - 1.05, C[2] + 0.3, C[3] - 0.35, C[4] - 0.45, C[2] + 0.95]);
	const pel = P.hips || A;
	sk('body', 'pelvis', [pel[0] + 0.5, A[1] + 0.2, pel[2] + 0.45, pel[3] - 0.5, A[1] + 2.3, pel[5] - 0.45]);
	sk('head', 'cervical', [-0.5 * k, N[1] - 0.1, cz(N) + 0.05, 0.5 * k, N[4] - 0.05, cz(N) + 1.0 * k]);
	sk('head', 'skull', [H[0] + 0.4, jawTop + 0.05, H[2] + 0.4, H[3] - 0.4, H[4] - 0.3, H[5] - 0.4]);
	sk('jaw', 'mandible', [H[0] + 0.7, H[1] + 0.3, H[2] + 0.45, H[3] - 0.7, jawTop + 0.05, H[5] - 1.7]);
	for (const side of ['right', 'left']) {
		const m = side === 'right' ? b => b : mirrorBox;
		const ua = m(P.uarm), fa = m(P.farm), hd = m(P.hand), th = m(P.thigh), sh = m(P.shin), ft = m(P.foot);
		const t = 0.5 * k;
		sk(`${side}_arm`, 'humerus', [cx(ua) - t, ua[1] + 0.3, cz(ua) - t, cx(ua) + t, ua[4] - 1.0, cz(ua) + t]);
		sk(`${side}_arm`, 'shoulderball', [cx(ua) - 0.85 * k, ua[4] - 1.75, cz(ua) - 0.85 * k, cx(ua) + 0.85 * k, ua[4] - 0.35, cz(ua) + 0.85 * k]);
		sk(`${side}_forearm`, 'radius', [cx(fa) - 0.85 * k, fa[1] + 0.1, cz(fa) - 0.33 * k, cx(fa) - 0.2 * k, fa[4] - 0.2, cz(fa) + 0.33 * k]);
		sk(`${side}_forearm`, 'ulna', [cx(fa) + 0.2 * k, fa[1] + 0.1, cz(fa) - 0.33 * k, cx(fa) + 0.8 * k, fa[4] - 0.2, cz(fa) + 0.33 * k]);
		sk(`${side}_forearm`, 'handbones', [hd[0] + 0.35, hd[1] + 0.15, hd[2] + 0.4, hd[3] - 0.35, hd[4] - 0.1, hd[5] - 0.35]);
		sk(`${side}_leg`, 'femur', [cx(th) - 0.62 * k, th[1] + 0.25, cz(th) - 0.62 * k, cx(th) + 0.62 * k, th[4] - 1.1, cz(th) + 0.62 * k]);
		sk(`${side}_leg`, 'femurhead', [cx(th) - 0.95 * k, th[4] - 1.8, cz(th) - 0.9 * k, cx(th) + 0.95 * k, th[4] - 0.45, cz(th) + 0.9 * k]);
		sk(`${side}_shin`, 'tibia', [cx(sh) - 0.75 * k, sh[1] + 0.2, cz(sh) - 0.55 * k, cx(sh) + 0.2 * k, sh[4] - 0.1, cz(sh) + 0.45 * k]);
		sk(`${side}_shin`, 'fibula', [cx(sh) + 0.4 * k, sh[1] + 0.3, cz(sh) - 0.25 * k, cx(sh) + 0.85 * k, sh[4] - 0.4, cz(sh) + 0.25 * k]);
		sk(`${side}_shin`, 'patella', [cx(sh) - 0.55 * k, sh[4] - 0.8, sh[2] + 0.12, cx(sh) + 0.45 * k, sh[4] + 0.25, sh[2] + 0.62]);
		sk(`${side}_foot`, 'footbones', [ft[0] + 0.35, ft[1] + 0.15, ft[2] + 0.35, ft[3] - 0.35, ft[4] - 0.25, ft[5] - 0.4]);
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
		add(`${side}_arm_stump`, 'stump', 'stump', [ua[0] + 0.05, ua[4] - 2.6, ua[2] + 0.05, ua[3] - 0.05, ua[4] - 0.1, ua[5] - 0.05], { layer: 1, stump: true, seg: ua });
		add(`${side}_leg_stump`, 'stump', 'stump', [th[0] + 0.05, th[4] - 2.8, th[2] + 0.05, th[3] - 0.05, th[4] - 0.3, th[5] - 0.05], { layer: 1, stump: true, seg: th });
	}

	EXTRAS[id](M, P, add);
	return M;
}

const R_cx = cx, R_cz = cz;
const EXTRAS = {
	titan_form(M, P, add) {
		const H = P.head;
		// long dark hair: a cap with a ragged fringe, curtains down the sides and a fall down the back to the shoulders
		add('head', 'hair', 'cap', [H[0] - 0.32, H[4] - 1.7, H[2] - 0.3, H[3] + 0.32, H[4] + 0.42, H[5] + 0.38], { layer: 2 });
		add('head', 'hair', 'side', [H[0] - 0.42, H[1] - 0.8, H[2] + 1.3, H[0] - 0.02, H[4] - 1.2, H[5] + 0.3], { layer: 2 });
		add('head', 'hair', 'side', [H[3] + 0.02, H[1] - 0.8, H[2] + 1.3, H[3] + 0.42, H[4] - 1.2, H[5] + 0.3], { layer: 2 });
		add('head', 'hair', 'back', [H[0] - 0.3, H[1] - 2.4, H[5] + 0.02, H[3] + 0.3, H[4] - 1.0, H[5] + 0.5], { layer: 2 });
	},
	titan_female(M, P, add) {
		const H = P.head;
		add('head', 'hair', 'cap', [H[0] - 0.3, H[4] - 1.9, H[2] - 0.25, H[3] + 0.3, H[4] + 0.35, H[5] + 0.32], { layer: 2 });
		add('head', 'hair', 'side', [H[0] - 0.36, H[1] + 1.2, H[2] + 0.5, H[0] - 0.02, H[4] - 1.4, H[5] + 0.25], { layer: 2 });
		add('head', 'hair', 'side', [H[3] + 0.02, H[1] + 1.2, H[2] + 0.5, H[3] + 0.36, H[4] - 1.4, H[5] + 0.25], { layer: 2 });
		add('head', 'hair', 'back', [H[0] - 0.25, H[1] + 0.2, H[5] + 0.02, H[3] + 0.25, H[4] - 1.2, H[5] + 0.4], { layer: 2 });
		add('head', 'hair', 'bun', [-1.25, H[4] - 3.4, H[5] + 0.35, 1.25, H[4] - 1.0, H[5] + 1.9], { layer: 2 });
		for (const side of ['right', 'left']) {
			const hd = side === 'right' ? P.hand : mirrorBox(P.hand);
			add(`${side}_forearm`, 'knuckle', 'knuckle', [hd[0] + 0.1, hd[1] + 0.2, hd[2] - 0.42, hd[3] - 0.1, hd[1] + 1.3, hd[2] + 0.05], { layer: 2 });
		}
		// Crystal Fists: crystal over the fists and shins (and the nape), drawn only while the move runs
		for (const side of ['right', 'left']) {
			const m = side === 'right' ? b => b : mirrorBox;
			const hd = m(P.hand), sh = m(P.shin);
			M.bone(`crystal_${side}_fist`, `${side}_forearm`, [R_cx(hd), hd[1], R_cz(hd)]);
			add(`crystal_${side}_fist`, 'crystal', 'crystal', [hd[0] - 0.35, hd[1] - 0.45, hd[2] - 0.75, hd[3] + 0.35, hd[1] + 2.0, hd[5] + 0.3], { layer: 2 });
			M.bone(`crystal_${side}_shin`, `${side}_shin`, [R_cx(sh), sh[1], R_cz(sh)]);
			add(`crystal_${side}_shin`, 'crystal', 'crystal', [sh[0] - 0.3, sh[1] + 0.4, sh[2] - 0.65, sh[3] + 0.3, sh[4] - 0.4, sh[2] + 0.9], { layer: 2 });
		}
		M.bone('crystal_nape', 'body', [0, P.neckY, P.chest[5]]);
		add('crystal_nape', 'crystal', 'crystal', [-1.9, P.neckY - 3.0, P.chest[5] + 0.1, 1.9, P.neckY + 0.5, P.chest[5] + 0.75], { layer: 2 });
	},
	titan_armored(M, P, add) {
		const plate = (bone, tag, b) => add(bone, 'plate', tag, b, { layer: 2 });
		const C = P.chest, A = P.abdomen, H = P.head;
		const fz = C[2] - SKIN_E;
		plate('body', 'pec', [C[0] + 0.25, C[1] + 2.0, fz - 0.6, -0.2, C[4] - 0.35, fz + 0.05]);
		plate('body', 'pec', [0.2, C[1] + 2.0, fz - 0.6, C[3] - 0.25, C[4] - 0.35, fz + 0.05]);
		plate('body', 'pec2', [C[0] + 0.9, C[1] + 2.8, fz - 1.0, -0.75, C[4] - 0.9, fz - 0.55]);
		plate('body', 'pec2', [0.75, C[1] + 2.8, fz - 1.0, C[3] - 0.9, C[4] - 0.9, fz - 0.55]);
		const az = A[2] - SKIN_E;
		for (let r = 0; r < 3; r++) {
			const y0 = A[1] + 0.35 + r * 1.65, y1 = y0 + 1.45;
			plate('body', 'ab', [A[0] + 0.5, y0, az - 0.45, -0.15, y1, az + 0.05]);
			plate('body', 'ab', [0.15, y0, az - 0.45, A[3] - 0.5, y1, az + 0.05]);
		}
		const bz = C[5] + SKIN_E;
		// the back plates leave the nape free (the shifter's way out)
		plate('body', 'back', [C[0] + 0.4, C[1] + 1.2, bz - 0.05, -1.9, C[4] - 0.5, bz + 0.55]);
		plate('body', 'back', [1.9, C[1] + 1.2, bz - 0.05, C[3] - 0.4, C[4] - 0.5, bz + 0.55]);
		plate('body', 'ridge', [-0.3, A[1] + 0.5, bz + 0.05, 0.3, C[1] + 3.0, bz + 0.6]);
		plate('head', 'cap', [H[0] - 0.4, H[4] - 1.8, H[2] - 0.35, H[3] + 0.4, H[4] + 0.45, H[5] + 0.4]);
		plate('head', 'brow', [H[0] - 0.2, H[1] + (H[4] - H[1]) * 0.64, H[2] - 0.6, H[3] + 0.2, H[1] + (H[4] - H[1]) * 0.76, H[2] - 0.05]);
		plate('head', 'cheek', [H[0] - 0.35, M.jawTop + 0.25, H[2] - 0.45, H[0] + 2.1, M.jawTop + 1.95, H[2] + 2.6]);
		plate('head', 'cheek', [H[3] - 2.1, M.jawTop + 0.25, H[2] - 0.45, H[3] + 0.35, M.jawTop + 1.95, H[2] + 2.6]);
		plate('head', 'crest', [-0.35, H[4] - 0.6, H[2] - 0.25, 0.35, H[4] + 0.95, H[5] - 0.5]);
		plate('jaw', 'jawguard', [H[0] + 0.1, H[1] - 0.25, H[2] - 0.3, H[3] - 0.1, H[1] + 0.8, H[2] + 2.4]);
		for (const side of ['right', 'left']) {
			const m = side === 'right' ? b => b : mirrorBox;
			const s = side === 'right' ? 1 : -1;
			const ua = m(P.uarm), fa = m(P.farm), th = m(P.thigh), sh = m(P.shin), hd = m(P.hand);
			const ox = s > 0 ? ua[0] : ua[3];
			plate(`${side}_arm`, 'pauldron', s > 0 ? [ua[0] - 0.55, ua[4] - 3.4, ua[2] - 0.5, ua[3] + 0.25, ua[4] + 0.4, ua[5] + 0.5] : [ua[0] - 0.25, ua[4] - 3.4, ua[2] - 0.5, ua[3] + 0.55, ua[4] + 0.4, ua[5] + 0.5]);
			plate(`${side}_arm`, 'pauldron2', s > 0 ? [ox - 0.95, ua[4] - 2.0, ua[2] - 0.15, ox + 2.4, ua[4] + 0.75, ua[5] + 0.15] : [ox - 2.4, ua[4] - 2.0, ua[2] - 0.15, ox + 0.95, ua[4] + 0.75, ua[5] + 0.15]);
			plate(`${side}_forearm`, 'bracer', [fa[0] - 0.4, fa[1] + 0.5, fa[2] - 0.4, fa[3] + 0.4, fa[4] - 1.2, fa[5] + 0.4]);
			plate(`${side}_forearm`, 'fin', s > 0 ? [fa[0] - 0.95, fa[1] + 1.0, -1.2, fa[0] - 0.35, fa[4] - 1.7, 1.2] : [fa[3] + 0.35, fa[1] + 1.0, -1.2, fa[3] + 0.95, fa[4] - 1.7, 1.2]);
			plate(`${side}_forearm`, 'knuckle', [hd[0] + 0.05, hd[1] + 0.3, hd[2] - 0.45, hd[3] - 0.05, hd[1] + 1.6, hd[2] + 0.1]);
			plate(`${side}_leg`, 'thighplate', [th[0] + 0.15, th[1] + 1.4, th[2] - 0.55, th[3] - 0.15, th[4] - 0.9, th[2] + 0.05]);
			plate(`${side}_leg`, 'thighside', s > 0 ? [th[0] - 0.5, th[1] + 1.0, th[2] + 0.4, th[0] + 0.05, th[4] - 1.5, th[5] - 0.4] : [th[3] - 0.05, th[1] + 1.0, th[2] + 0.4, th[3] + 0.5, th[4] - 1.5, th[5] - 0.4]);
			plate(`${side}_shin`, 'kneecap', [sh[0] + 0.45, sh[4] - 1.3, sh[2] - 0.75, sh[3] - 0.45, sh[4] + 0.85, sh[2] + 0.2]);
			plate(`${side}_shin`, 'greave', [sh[0] - 0.1, sh[1] + 0.6, sh[2] - 0.5, sh[3] + 0.1, sh[4] - 1.6, sh[2] + 0.4]);
		}
	},
	titan_beast(M, P, add) {
		const fur = (bone, tag, b) => add(bone, 'fur', tag, b, { layer: 2 });
		const C = P.chest, H = P.head;
		const hd = P.hand;
		M.bone('boulder', 'right_forearm', [R_cx(hd), hd[1], R_cz(hd)]);
		add('boulder', 'rock', 'boulder', [R_cx(hd) - 2.6, hd[1] - 4.4, R_cz(hd) - 2.9, R_cx(hd) + 2.6, hd[1] + 0.9, R_cz(hd) + 2.3], { layer: 2 });
		fur('body', 'ruff', [C[0] + 0.6, C[4] - 2.4, C[2] - 0.5, C[3] - 0.6, C[4] + 1.3, C[5] - 0.9]);
		fur('head', 'mane', [H[0] - 0.45, H[1] - 0.3, H[2] + 2.2, H[3] + 0.45, H[4] + 0.35, H[5] + 0.55]);
		fur('head', 'cheekfur', [H[0] - 0.55, H[1] + 0.2, H[2] + 0.7, H[0] + 0.1, H[4] - 1.6, H[2] + 3.4]);
		fur('head', 'cheekfur', [H[3] - 0.1, H[1] + 0.2, H[2] + 0.7, H[3] + 0.55, H[4] - 1.6, H[2] + 3.4]);
		fur('jaw', 'beard', [H[0] + 0.6, H[1] - 0.9, H[2] - 0.05, H[3] - 0.6, H[1] + 0.8, H[2] + 2.8]);
		for (const side of ['right', 'left']) {
			const m = side === 'right' ? b => b : mirrorBox;
			const s = side === 'right' ? 1 : -1;
			const ua = m(P.uarm), fa = m(P.farm);
			fur(`${side}_arm`, 'shoulderfur', s > 0 ? [ua[0] - 0.55, ua[4] - 4.5, ua[2] - 0.45, ua[3] + 0.2, ua[4] + 0.5, ua[5] + 0.45] : [ua[0] - 0.2, ua[4] - 4.5, ua[2] - 0.45, ua[3] + 0.55, ua[4] + 0.5, ua[5] + 0.45]);
			fur(`${side}_forearm`, 'armfur', s > 0 ? [fa[0] - 0.6, fa[1] + 1.2, fa[2] + 0.2, fa[0] + 1.3, fa[4] - 0.5, fa[5] + 0.55] : [fa[3] - 1.3, fa[1] + 1.2, fa[2] + 0.2, fa[3] + 0.6, fa[4] - 0.5, fa[5] + 0.55]);
		}
	},
};

module.exports = { TYPES, build, mirrorBox, SKIN_E, D, cx, cz };
