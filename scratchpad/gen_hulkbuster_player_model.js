// v0.16.7 (user) Era II stage 1 -- the HULKBUSTER I body as a regular player model x1.5 (3 blocks tall).
// "literally make the hulkbuster armour a regular player model but make it like 3 blocks tall ... add layering"
//
// Writes:
//   src/main/resources/assets/projecthero/geo/ironman/hulkbuster_body.geo.json
//   src/main/resources/assets/projecthero/textures/armor/hulkbuster_body.png (+ _glowmask)
// from the approved concept skin scratchpad/concepts_ironman_v2/skins/e2_hb.png (+ glowmask) in the MAIN checkout.
//
// Rig = HB_RIG of ironman_era2_main.js: the plain Steve rig (classic 4-wide arms) with every coordinate x1.5. Base layer
// ~0.12 px proud, outer layer (jacket / sleeves / pants) 0.6, hat 0.55. The bone NAMES + hierarchy are the old custom
// body's, so hulkbuster_body.animation.json still plays:
//   root > hips > waist > flinch > chest > { neck > head, backpack > jet_back, beam_fx,
//          shoulder_r > { pod_r, arm_r > forearm_r > hand_r }, shoulder_l ... }
//   hips > thigh_r > shin_r > foot_r > jet_r (and _l)
// Each limb is ONE Steve limb cut at texel rows 6 and 10 (upper arm / forearm / hand, thigh / shin / foot) so the clips'
// elbow, wrist, knee and ankle keys still bend it; the cuts are capped with a dark texel so a bend never opens a hole.
// The whole torso sits in `chest` (rigid, like a player's body); hips/waist/flinch are pivot-only. pod_r/pod_l are
// pivot-only (the missile pods are not part of the player-model look). backpack = the jacket's back face as a hinged
// plate (it swings open in Sentry Mode). fx bones (jet_back, beam_fx, jet_r, jet_l) are glow cubes on unused skin texels
// (56..59, 16..19) copied from the old body texture.
//
// Run: node scratchpad/gen_hulkbuster_player_model.js   (from the repo root)
const fs = require('fs');
const path = require('path');
const P = require('./pngkit.js');

const ROOT = path.resolve(__dirname, '..');
const MAIN = process.env.PH_MAIN || 'C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod';
const SKIN = path.join(MAIN, 'scratchpad/concepts_ironman_v2/skins/e2_hb.png');
const GLOW = path.join(MAIN, 'scratchpad/concepts_ironman_v2/skins/e2_hb_glowmask.png');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/projecthero');
const TEX = path.join(ASSETS, 'textures/armor/hulkbuster_body.png');
const TEX_GLOW = path.join(ASSETS, 'textures/armor/hulkbuster_body_glowmask.png');
const GEO = path.join(ASSETS, 'geo/ironman/hulkbuster_body.geo.json');

const K = 1.5; // texel -> model px
const B = 0.12, O = 0.6, HAT = 0.55; // base, outer, hat inflation (px)
const r3 = n => Math.round(n * 1000) / 1000;

// ---------------------------------------------------------------- texture
// The concept skin as-is, plus the fx swatches on unused skin texels (56..59 x 16..19; a player skin never samples them).
// They are copied from the old body texture's fx column so the jets / beam keep their colours: 56 = blue glow,
// 57 = white-hot core, 58 = orange glow, 59 = dark joint cap (not glowing).
const FX = { glow: [56, 16], core: [57, 16], orange: [58, 16], cap: [59, 16] };
function texture() {
	const sk = P.decode(SKIN), gm = P.decode(GLOW);
	if (sk.w !== 64 || sk.h !== 64) throw new Error('skin must be 64x64');
	const SW = [ // [r,g,b] per row 16..19
		[[205, 246, 255], [180, 236, 255], [150, 222, 250], [120, 205, 240]],
		[[255, 255, 255], [240, 252, 255], [225, 248, 255], [210, 242, 255]],
		[[255, 236, 170], [255, 196, 110], [255, 150, 60], [230, 110, 40]],
		[[44, 40, 40], [32, 28, 28], [24, 20, 20], [16, 14, 14]],
	];
	for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) {
		const i = ((16 + r) * 64 + 56 + c) * 4;
		if (sk.px[i + 3] !== 0) throw new Error('fx texel ' + (56 + c) + ',' + (16 + r) + ' is not free');
		const [R, G, Bl] = SW[c][r];
		sk.px[i] = R; sk.px[i + 1] = G; sk.px[i + 2] = Bl; sk.px[i + 3] = 255;
		if (c < 3) { gm.px[i] = R; gm.px[i + 1] = G; gm.px[i + 2] = Bl; gm.px[i + 3] = 255; }
	}
	fs.writeFileSync(TEX, P.encode(64, 64, sk.px));
	fs.writeFileSync(TEX_GLOW, P.encode(64, 64, gm.px));
	return sk;
}

// ---------------------------------------------------------------- geometry helpers
// Bedrock box layout (as GeckoLib reads it): west | north | east | south across, up / down above.
function boxFaces(u, v, w, h, d, rowFrom, rows, opts = {}) {
	const sv = v + d + rowFrom;
	const f = {
		west: { uv: [u, sv], uv_size: [d, rows] },
		north: { uv: [u + d, sv], uv_size: [w, rows] },
		east: { uv: [u + d + w, sv], uv_size: [d, rows] },
		south: { uv: [u + 2 * d + w, sv], uv_size: [w, rows] },
	};
	if (opts.up) f.up = { uv: [u + d, v], uv_size: [w, d] };
	if (opts.down) f.down = { uv: [u + d + w, v], uv_size: [w, d] };
	return f;
}
const swatch = ([u, v]) => ({ uv: [u, v], uv_size: [1, 4] });
const allFaces = s => ({ north: swatch(s), south: swatch(s), east: swatch(s), west: swatch(s), up: swatch(s), down: swatch(s) });

/**
 * A cube from texel box [x0,y0,z0]-[x1,y1,z1] (Steve px, unscaled) scaled by K, grown outward per side
 * (inf = {x0,x1,y0,y1,z0,z1}, px in the scaled model), with the given faces.
 */
function cube(box, inf, uv) {
	const [x0, y0, z0, x1, y1, z1] = box.map(n => n * K);
	const a = [x0 - inf.x0, y0 - inf.y0, z0 - inf.z0], b = [x1 + inf.x1, y1 + inf.y1, z1 + inf.z1];
	return { origin: a.map(r3), size: [0, 1, 2].map(i => r3(b[i] - a[i])), uv };
}
const even = (n, over = {}) => Object.assign({ x0: n, x1: n, y0: n, y1: n, z0: n, z1: n }, over);

// ---------------------------------------------------------------- the limbs, cut at texel rows 6 and 10
// part = { box (Steve px), base: [u,v], outer: [u,v], baseInf, outerInf } -> three segments' cubes
const CUTS = [[0, 6], [6, 4], [10, 2]]; // [rowFrom, rows] top to bottom (12 texel rows)
function limb(part) {
	const [x0, y0, z0, x1, y1, z1] = part.box; // y0 = bottom, y1 = top (Steve px)
	const w = x1 - x0, h = y1 - y0, d = z1 - z0;
	return CUTS.map(([from, rows], i) => {
		const top = y1 - from, bot = top - rows;
		const box = [x0, bot, z0, x1, top, z1];
		const first = i === 0, last = i === CUTS.length - 1;
		const cubes = [];
		// base: the seams get no y growth (the segments meet flush) + a dark cap so a bend never shows a hole
		const bi = Object.assign({}, part.baseInf, { y1: first ? part.baseInf.y1 : 0, y0: last ? part.baseInf.y0 : 0 });
		const bf = boxFaces(part.base[0], part.base[1], w, h, d, from, rows, { up: first, down: last });
		if (!first) bf.up = swatch(FX.cap);
		if (!last) bf.down = swatch(FX.cap);
		cubes.push(cube(box, bi, bf));
		// outer layer: same cut, no caps (it is see-through between the plates anyway)
		const oi = Object.assign({}, part.outerInf, { y1: first ? part.outerInf.y1 : 0, y0: last ? part.outerInf.y0 : 0 });
		cubes.push(cube(box, oi, boxFaces(part.outer[0], part.outer[1], w, h, d, from, rows, { up: first, down: last })));
		return cubes;
	});
}

// ---------------------------------------------------------------- the rig
const ZA = 0.05; // arms / legs sit 0.05 px off the torso's planes so their overlaps never z-fight
function build() {
	const bones = [];
	const bone = (name, parent, pivot, cubes) => {
		const b = { name };
		if (parent) b.parent = parent;
		b.pivot = pivot.map(r3);
		if (cubes && cubes.length) b.cubes = cubes;
		bones.push(b);
	};
	bone('root', null, [0, 0, 0]);
	bone('hips', 'root', [0, 18, 0]);
	bone('waist', 'hips', [0, 21, 0]);
	bone('flinch', 'waist', [0, 27, 0]);
	// torso: base + jacket, the jacket's back face lives on the hinged backpack plate
	const T = [-4, 12, -2, 4, 24, 2];
	const jacket = boxFaces(16, 32, 8, 12, 4, 0, 12, { up: true, down: true });
	delete jacket.south;
	bone('chest', 'flinch', [0, 27, 0], [
		cube(T, even(B, { y0: 0 }), boxFaces(16, 16, 8, 12, 4, 0, 12, { up: true, down: true })),
		cube(T, even(O), jacket),
	]);
	bone('neck', 'chest', [0, 36, 0]);
	bone('head', 'neck', [0, 36, 0], [
		cube([-4, 24, -4, 4, 32, 4], even(B), boxFaces(0, 0, 8, 8, 8, 0, 8, { up: true, down: true })),
		cube([-4, 24, -4, 4, 32, 4], even(HAT), boxFaces(32, 0, 8, 8, 8, 0, 8, { up: true, down: true })),
	]);
	// the back plate: the jacket's south face, hinged at its bottom edge (sentry_idle swings it open -72 deg)
	const zBack = 2 * K + O;
	bone('backpack', 'chest', [0, 12 * K - O, zBack], [
		{ origin: [r3(-4 * K - O), r3(12 * K - O), r3(zBack)], size: [r3(8 * K + 2 * O), r3(12 * K + 2 * O), 0],
			uv: { south: { uv: [32, 36], uv_size: [8, 12] }, north: { uv: [32, 36], uv_size: [8, 12] } } },
	]);
	// back thrusters: two flames under the power pack (scale-keyed by the clips)
	const jb = [];
	for (const x of [-3, 3]) {
		jb.push({ origin: [r3(x - 1.2), 6, 3.9], size: [2.4, 12, 1.2], uv: allFaces(FX.glow) });
		jb.push({ origin: [r3(x - 0.6), 4, 4.2], size: [1.2, 14, 0.6], uv: allFaces(FX.core) });
	}
	bone('jet_back', 'backpack', [0, 18, 4.5], jb);
	// unibeam from the triangle reactor (body texel rows 1..3 -> y 30.75..33.75)
	bone('beam_fx', 'chest', [0, 32.25, -3.6], [
		{ origin: [-2.6, 29.65, -60], size: [5.2, 5.2, 56.4], uv: allFaces(FX.glow) },
		{ origin: [-1.2, 31.05, -62], size: [2.4, 2.4, 58.4], uv: allFaces(FX.core) },
	]);
	// arms: classic 4-wide, x1.5 -> 6 px; shoulder pivot = Steve's (5, 22) x1.5
	for (const side of ['r', 'l']) {
		const s = side === 'r' ? -1 : 1;
		const box = side === 'r' ? [-8, 12, -2, -4, 24, 2] : [4, 12, -2, 8, 24, 2];
		const segs = limb({
			box, base: side === 'r' ? [40, 16] : [32, 48], outer: side === 'r' ? [40, 32] : [48, 48],
			baseInf: even(B, { z0: B + ZA, z1: B + ZA }), outerInf: even(O, { z0: O + ZA, z1: O + ZA }),
		});
		const sx = 7.5 * s, ex = 9 * s;
		bone('shoulder_' + side, 'chest', [sx, 33, 0]);
		bone('pod_' + side, 'shoulder_' + side, [ex, 36.6, 0]);
		bone('arm_' + side, 'shoulder_' + side, [sx, 33, 0], segs[0]);
		bone('forearm_' + side, 'arm_' + side, [ex, 27, 0], segs[1]);
		bone('hand_' + side, 'forearm_' + side, [ex, 21, 0], segs[2]);
	}
	// legs: the inner sides pulled in a hair so the two legs never share a plane
	for (const side of ['r', 'l']) {
		const s = side === 'r' ? -1 : 1;
		const box = side === 'r' ? [-4, 0, -2, 0, 12, 2] : [0, 0, -2, 4, 12, 2];
		const inner = side === 'r' ? 'x1' : 'x0';
		const baseInf = even(B - ZA, { y1: 0, [inner]: -0.02 });
		const outerInf = even(O - ZA, { y1: 0.3, [inner]: -0.04 });
		const segs = limb({ box, base: side === 'r' ? [0, 16] : [16, 48], outer: side === 'r' ? [0, 32] : [0, 48], baseInf, outerInf });
		const x = 3 * s;
		bone('thigh_' + side, 'hips', [x, 18, 0], segs[0]);
		bone('shin_' + side, 'thigh_' + side, [x, 9, 0], segs[1]);
		bone('foot_' + side, 'shin_' + side, [x, 3, 0], segs[2]);
		bone('jet_' + side, 'foot_' + side, [x, 0, 0], [
			{ origin: [r3(x - 1.8), -9, -1.8], size: [3.6, 8.95, 3.6], uv: allFaces(FX.glow) },
			{ origin: [r3(x - 0.9), -11, -0.9], size: [1.8, 10.95, 1.8], uv: allFaces(FX.core) },
		]);
	}
	return {
		format_version: '1.12.0',
		'minecraft:geometry': [{
			description: { identifier: 'geometry.hulkbuster_body', texture_width: 64, texture_height: 64,
				visible_bounds_width: 7, visible_bounds_height: 6, visible_bounds_offset: [0, 2, 0] },
			bones,
		}],
	};
}

texture();
const geo = build();
fs.writeFileSync(GEO, JSON.stringify(geo, null, '\t') + '\n');
const n = geo['minecraft:geometry'][0].bones.reduce((a, b) => a + (b.cubes ? b.cubes.length : 0), 0);
console.log('wrote', path.relative(ROOT, GEO), geo['minecraft:geometry'][0].bones.length, 'bones', n, 'cubes; textures', path.relative(ROOT, TEX));
