// v0.17.5 Symbiote suit, made from scratch (user: "create a new model for it ... make it so that youre able to change the colour").
// Original art -- an original take on the classic living-suit look, no copied logos. Painted procedurally in BODY SPACE: every
// texel of the player-skin box-UV layout (both layers) is placed on the body, so the emblem, muscles, veins and lighting run
// seamlessly across face edges.
//
//   node scratchpad/symbiote/gen_symbiote_suit_v0175.js
//
// Writes (src/main/resources/assets/projecthero/):
//   textures/armor/symbiote_host_tint.png  128x128 TINT MASK the game colours at draw time (SymbioteSuitTint):
//        R = shade (0.5 = the chosen colour, lower darkens to black, higher lifts toward a light tint of the hue)
//        G = gloss (wet highlight laid over the shade, toward a near-white of the hue)
//        B = channel: 0 primary (body), 128 secondary (emblem), 255 eyes (lens)    A = coverage
//   textures/armor/symbiote_host_fx.png    128x128 animation map (SymbioteSuitFx): R = sheen sweep coordinate, G = sheen strength,
//        B = vein strength (the slow pulse), A = coverage
//   textures/armor/symbiote_host.png       the Classic colours baked (fallback, Black Suit takeover, anything without a wearer)
//   geo/symbiote_host.geo.json             the model: base + raised shell in the player-skin layout (declared 64 wide, so the
//        128 px texture is sampled at 2x) plus 3D extras: raised pec / thigh plates, spine nodes (v0.17.5 round 2: the shoulder caps + drips removed, user)
//   textures/item/symbiote_host_{helmet,chestplate,leggings,boots}{,_gloss,_emblem}.png  three-layer tintable icons
// and docs/screenshots/v0175/symbiote_suit/texture_preview_presets.png (front / back / side of every preset).
//
// The colour maths here (bake) is the exact twin of SymbioteSuitTint#bake in Java -- change both together.
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('../pnglib.js');

const ROOT = path.join(__dirname, '..', '..');
const ASSETS = path.join(ROOT, 'src', 'main', 'resources', 'assets', 'projecthero');
const SHOTS = path.join(ROOT, 'docs', 'screenshots', 'v0175', 'symbiote_suit');
const S = 2;          // texels per model pixel (HD 2x)
const W = 64 * S, H = 64 * S;

// ---------------------------------------------------------------- maths
const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const smooth = (a, b, v) => { const t = clamp((v - a) / (b - a)); return t * t * (3 - 2 * t); };
const len3 = (a) => Math.hypot(a[0], a[1], a[2]);
const norm = (a) => { const l = len3(a) || 1; return [a[0] / l, a[1] / l, a[2] / l]; };
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const add = (a, b, k = 1) => [a[0] + b[0] * k, a[1] + b[1] * k, a[2] + b[2] * k];
function hash3(x, y, z) { let h = (x * 374761393 + y * 668265263 + z * 2147483647) | 0; h = (h ^ (h >>> 13)) * 1274126177 | 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; }
function vnoise(x, y, z) {
	const xi = Math.floor(x), yi = Math.floor(y), zi = Math.floor(z), xf = x - xi, yf = y - yi, zf = z - zi;
	const u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf), w = zf * zf * (3 - 2 * zf);
	let r = 0;
	for (let dz = 0; dz < 2; dz++) for (let dy = 0; dy < 2; dy++) for (let dx = 0; dx < 2; dx++) {
		r += hash3(xi + dx, yi + dy, zi + dz) * (dx ? u : 1 - u) * (dy ? v : 1 - v) * (dz ? w : 1 - w);
	}
	return r * 2 - 1;
}
const fbm = (p, f) => vnoise(p[0] * f, p[1] * f, p[2] * f) * 0.65 + vnoise(p[0] * f * 2.1 + 7, p[1] * f * 2.1, p[2] * f * 2.1) * 0.35;

// ---------------------------------------------------------------- the body (y up, feet at 0; x > 0 = the wearer's left; z < 0 = front)
const PARTS = {
	head: { o: [-4, 24, -4], s: [8, 8, 8] },
	body: { o: [-4, 12, -2], s: [8, 12, 4] },
	rarm: { o: [-8, 12, -2], s: [4, 12, 4] },
	larm: { o: [4, 12, -2], s: [4, 12, 4] },
	rleg: { o: [-4, 0, -2], s: [4, 12, 4] },
	lleg: { o: [0, 0, -2], s: [4, 12, 4] },
};
// {part, u, v, layer}: the 64x64 player-skin layout, both layers
const BOXES = [
	['head', 0, 0, 0], ['head', 32, 0, 1],
	['body', 16, 16, 0], ['body', 16, 32, 1],
	['rarm', 40, 16, 0], ['rarm', 40, 32, 1],
	['larm', 32, 48, 0], ['larm', 48, 48, 1],
	['rleg', 0, 16, 0], ['rleg', 0, 32, 1],
	['lleg', 16, 48, 0], ['lleg', 0, 48, 1],
];
// vanilla ModelPart.Cube box UV, checked against its polygon / vertex order:
// top: fu 0 -> minX, fv 0 -> back; bottom: same; west (wearer's right side): fu 0 -> back; front: fu 0 -> minX;
// east (left side): fu 0 -> front; back: fu 0 -> maxX. fv 0 = the top edge of every side face.
function facesOf(u, v, sx, sy, sz) {
	return [
		{ id: 'top', u0: u + sz, v0: v, du: sx, dv: sz, n: [0, 1, 0] },
		{ id: 'bottom', u0: u + sz + sx, v0: v, du: sx, dv: sz, n: [0, -1, 0] },
		{ id: 'west', u0: u, v0: v + sz, du: sz, dv: sy, n: [-1, 0, 0] },
		{ id: 'front', u0: u + sz, v0: v + sz, du: sx, dv: sy, n: [0, 0, -1] },
		{ id: 'east', u0: u + sz + sx, v0: v + sz, du: sz, dv: sy, n: [1, 0, 0] },
		{ id: 'back', u0: u + 2 * sz + sx, v0: v + sz, du: sx, dv: sy, n: [0, 0, 1] },
	];
}
function pointOn(face, fu, fv, g) {
	const [ox, oy, oz] = g.o, [sx, sy, sz] = g.s;
	switch (face) {
		case 'top': return [ox + sx * fu, oy + sy, oz + sz * (1 - fv)];
		case 'bottom': return [ox + sx * fu, oy, oz + sz * (1 - fv)];
		case 'west': return [ox, oy + sy * (1 - fv), oz + sz * (1 - fu)];
		case 'front': return [ox + sx * fu, oy + sy * (1 - fv), oz];
		case 'east': return [ox + sx, oy + sy * (1 - fv), oz + sz * fu];
		default: return [ox + sx * (1 - fu), oy + sy * (1 - fv), oz + sz];
	}
}

// ---------------------------------------------------------------- design: the emblem (an original spider), as 3D strokes on the torso
function seg(p, a, b) { // distance from p to segment ab, and t along it
	const ab = [b[0] - a[0], b[1] - a[1], b[2] - a[2]], ap = [p[0] - a[0], p[1] - a[1], p[2] - a[2]];
	const t = clamp(dot(ap, ab) / (dot(ab, ab) || 1));
	return [len3([ap[0] - ab[0] * t, ap[1] - ab[1] * t, ap[2] - ab[2] * t]), t];
}
function chain(p, pts, r0, r1, rKnee) { // tapered stroke through pts, radius r0 at the root .. r1 at the end
	let best = 1e9; let total = 0; const lens = [];
	for (let i = 0; i < pts.length - 1; i++) { lens.push(len3(add(pts[i + 1], pts[i], -1))); total += lens[i]; }
	let acc = 0;
	for (let i = 0; i < pts.length - 1; i++) {
		const [d, t] = seg(p, pts[i], pts[i + 1]);
		const along = (acc + lens[i] * t) / total;
		let r = r0 + (r1 - r0) * Math.pow(along, 1.4);
		// a knuckle at every joint
		const jt = Math.min(t, 1 - t) * lens[i];
		if (rKnee && i > 0 && t < 0.5) r = Math.max(r, rKnee * (1 - along * 0.5) - jt * 0.25);
		best = Math.min(best, d - r);
		acc += lens[i];
	}
	return best;
}
function ellipse(p, cx, cy, z, rx, ry) { // flat ellipse on a z face (only near that face)
	if (Math.abs(p[2] - z) > 0.05) return 1e9;
	const k = Math.hypot((p[0] - cx) / rx, (p[1] - cy) / ry);
	return (k - 1) * Math.min(rx, ry);
}
const LEGS = [];
for (const sg of [-1, 1]) {
	// over the shoulder, front to back
	LEGS.push({ pts: [[sg * 0.55, 21.4, -2], [sg * 1.75, 23.0, -2], [sg * 2.7, 24, -1.0], [sg * 2.7, 24, 1.0], [sg * 1.9, 22.8, 2], [sg * 0.7, 21.3, 2]], r0: 0.62, r1: 0.6, knee: 0.7 });
	// round the side high, front to back
	LEGS.push({ pts: [[sg * 1.15, 20.6, -2], [sg * 3.05, 21.75, -2], [sg * 4, 21.0, -0.8], [sg * 4, 20.6, 0.9], [sg * 3.0, 20.4, 2], [sg * 1.1, 20.0, 2]], r0: 0.6, r1: 0.56, knee: 0.68 });
	// round the ribs low, front to back
	LEGS.push({ pts: [[sg * 1.25, 19.5, -2], [sg * 3.05, 18.4, -2], [sg * 4, 17.1, -0.7], [sg * 4, 16.5, 1.0], [sg * 3.0, 17.0, 2], [sg * 1.1, 18.6, 2]], r0: 0.6, r1: 0.54, knee: 0.68 });
	// the bottom pair, free tips, front and back
	LEGS.push({ pts: [[sg * 0.95, 18.7, -2], [sg * 2.1, 16.9, -2], [sg * 2.85, 14.7, -2], [sg * 3.2, 12.9, -2]], r0: 0.58, r1: 0.05, knee: 0.64 });
	LEGS.push({ pts: [[sg * 0.85, 18.3, 2], [sg * 2.0, 16.8, 2], [sg * 2.7, 14.9, 2], [sg * 3.0, 13.5, 2]], r0: 0.55, r1: 0.05, knee: 0.6 });
}
function emblemSdf(p) {
	let d = 1e9;
	// front body: a small head, the thorax, a long abdomen tapering to a spike
	d = Math.min(d, ellipse(p, 0, 22.0, -2, 0.78, 0.6));
	d = Math.min(d, ellipse(p, 0, 20.15, -2, 1.3, 1.45));
	d = Math.min(d, chain(p, [[0, 19.0, -2], [0, 16.4, -2], [0, 13.6, -2]], 1.02, 0.05, 0));
	// back body, a little smaller
	d = Math.min(d, ellipse(p, 0, 21.75, 2, 0.7, 0.55));
	d = Math.min(d, ellipse(p, 0, 20.0, 2, 1.18, 1.32));
	d = Math.min(d, chain(p, [[0, 19.0, 2], [0, 16.8, 2], [0, 14.6, 2]], 0.9, 0.05, 0));
	for (const l of LEGS) d = Math.min(d, chain(p, l.pts, l.r0, l.r1, l.knee));
	// living edge: slow ripples + a few ragged barbs
	d += vnoise(p[0] * 2.3, p[1] * 2.3, p[2] * 2.3) * 0.09 - Math.max(0, vnoise(p[0] * 5.1 + 3, p[1] * 5.1, p[2] * 5.1) - 0.55) * 0.5;
	return d;
}

// ---------------------------------------------------------------- design: the eyes (big, angular, sweeping up and back round the head)
// eye outline in the head's front plane; x' runs on round the side of the head (x' = 4 + depth back from the front edge)
const EYE = [[0.42, 26.85], [0.95, 28.35], [2.2, 29.55], [3.55, 30.55], [4.95, 31.05], [5.35, 30.75], [4.45, 29.3], [3.2, 27.85], [1.85, 26.95], [0.95, 26.6]];
function polySdf(x, y, poly) {
	let d = 1e9, inside = false;
	for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
		const [xi, yi] = poly[i], [xj, yj] = poly[j];
		if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside;
		const [dd] = seg([x, y, 0], [xi, yi, 0], [xj, yj, 0]);
		d = Math.min(d, dd);
	}
	return inside ? -d : d;
}
function eyeSdf(p, face) {
	// unwrap: front face as is, the side faces continue the plane outward
	let ux;
	if (face === 'front') ux = p[0];
	else if (face === 'west') ux = -(4 + (p[2] + 4));
	else if (face === 'east') ux = 4 + (p[2] + 4);
	else return 1e9;
	const sg = ux < 0 ? -1 : 1;
	return polySdf(Math.abs(ux), p[1], EYE) + (sg < 0 ? 0 : 0);
}

// ---------------------------------------------------------------- design: muscles (a height field on the body), rounding, veins
const bump = (dx, dy, rx, ry) => Math.exp(-((dx / rx) ** 2 + (dy / ry) ** 2));
function height(part, p) {
	const [x, y, z] = p;
	let h = 0;
	if (part === 'body') {
		const front = smooth(0.5, -1.6, z), back = smooth(-0.5, 1.6, z);
		for (const sg of [-1, 1]) {
			h += front * 0.95 * bump(x - sg * 2.05, y - 20.5, 2.0, 1.45);          // pecs
			for (const ay of [17.55, 15.85, 14.2]) h += front * 0.5 * bump(x - sg * 0.95, y - ay, 0.82, 0.66); // abs
			h += front * 0.35 * bump(x - sg * 3.3, y - 15.4, 0.9, 2.5);           // obliques
			h += back * 0.5 * bump(x - sg * 1.8, y - 22.7, 2.2, 1.1);             // traps
			h += back * 0.6 * bump(x - sg * 2.05, y - 19.9, 1.55, 1.85);          // shoulder blades
			h += back * 0.42 * bump(x - sg * 2.8, y - 16.4, 1.25, 2.8);           // lats
		}
		h -= (front * smooth(19.4, 18.6, y) + back) * 0.3 * Math.exp(-((x / 0.38) ** 2)); // centre line / spine
		h += 0.18 * smooth(22.6, 23.5, y) * Math.exp(-(((Math.abs(x) - 2) / 1.6) ** 2)) * front; // collarbones
		const side = smooth(3.2, 4.0, Math.abs(x));
		h += side * 0.2 * Math.max(0, Math.cos((y + 0.7 * z) * 2.3)) * smooth(13.5, 15, y) * smooth(21, 19.5, y); // ribs
	} else if (part === 'rarm' || part === 'larm') {
		const cx = part === 'rarm' ? -6 : 6, ax = x - cx;
		const front = smooth(0.4, -1.8, z), back = smooth(-0.4, 1.8, z);
		h += 0.6 * Math.exp(-(((y - 22.3) / 1.4) ** 2));                     // deltoid
		h += front * 0.6 * Math.exp(-(((y - 18.8) / 1.5) ** 2));             // biceps
		h += back * 0.5 * Math.exp(-(((y - 19.4) / 1.7) ** 2));              // triceps
		h -= 0.32 * Math.exp(-(((y - 17.1) / 0.42) ** 2));                   // elbow
		h += 0.38 * Math.exp(-(((y - 15.4) / 1.2) ** 2)) * (0.6 + 0.4 * smooth(0, -1.5, ax * (cx < 0 ? 1 : -1))); // forearm
		h -= 0.22 * smooth(13.4, 12.2, y);                                   // wrist
		h += 0.07 * Math.sin(Math.atan2(z, ax) * 6 + y * 0.45);              // sinew
	} else if (part === 'rleg' || part === 'lleg') {
		const cx = part === 'rleg' ? -2 : 2, ax = x - cx;
		const front = smooth(0.4, -1.8, z), back = smooth(-0.4, 1.8, z);
		const inner = smooth(0.3, -1.6, ax * (cx < 0 ? -1 : 1));
		h += front * 0.6 * Math.exp(-(((y - 9.0) / 1.9) ** 2));              // quads
		h += front * inner * 0.38 * Math.exp(-(((y - 7.0) / 0.9) ** 2));     // the teardrop above the knee
		h += front * 0.35 * Math.exp(-(((y - 5.5) / 0.6) ** 2)) * Math.exp(-((ax / 1.2) ** 2)); // kneecap
		h -= front * 0.22 * Math.exp(-(((y - 6.35) / 0.3) ** 2));
		h += back * 0.62 * Math.exp(-(((y - 3.8) / 1.35) ** 2));             // calves
		h += back * 0.5 * Math.exp(-(((y - 10.6) / 1.1) ** 2));              // glutes
		h -= 0.16 * smooth(1.6, 0.4, y);                                     // ankle
		h += 0.06 * Math.sin(Math.atan2(z, ax) * 5 + y * 0.35);
	} else if (part === 'head') {
		const front = smooth(-1, -3.8, z);
		for (const sg of [-1, 1]) {
			h += front * 0.32 * bump(x - sg * 2.4, y - 26.6, 1.4, 0.8);         // cheekbones
			h += front * 0.22 * bump(x - sg * 1.6, y - 31.0, 1.8, 0.6);         // brow
		}
		h -= front * 0.2 * smooth(25.3, 24.2, y);                            // under the jaw
	}
	return h;
}
function roundNormal(part, p, faceN) {
	const [x, y, z] = p;
	let r;
	if (part === 'body') r = [x / 4.4, faceN[1] * 0.9, z / 2.3];
	else if (part === 'head') r = [x / 4.4, (y - 28) / 4.6, z / 4.4];
	else {
		const cx = part === 'rarm' ? -6 : part === 'larm' ? 6 : part === 'rleg' ? -2 : 2;
		r = [(x - cx) / 2.2, faceN[1] * 0.9, z / 2.2];
	}
	return norm(add(faceN.map((v) => v * 0.5), norm(r), 0.6));
}
function veinAt(part, p) {
	if (part === 'head') return 0;
	const warp = [vnoise(p[0] * 0.4 + 5, p[1] * 0.4, p[2] * 0.4) * 1.5, 0, vnoise(p[0] * 0.4, p[1] * 0.4 + 9, p[2] * 0.4) * 1.5];
	const q = add(p, warp);
	const n1 = vnoise(q[0] * 0.3, q[1] * 0.3, q[2] * 0.3), n2 = vnoise(q[0] * 0.62 + 11, q[1] * 0.62 + 3, q[2] * 0.62 + 7);
	let v = smooth(0.94, 0.985, 1 - Math.abs(n1)) + smooth(0.965, 0.993, 1 - Math.abs(n2)) * 0.6;
	const where = part === 'body' ? smooth(2.0, 3.6, Math.abs(p[0])) * 0.8 + 0.3 * smooth(0.5, 2, p[2]) : 1;
	return clamp(v * where);
}

// ---------------------------------------------------------------- paint
const LIGHT = norm([-0.35, 0.82, -0.5]);
const FILL = norm([0.65, 0.2, -0.35]);
const RIM = norm([0.15, 0.55, 1]);
const tint = Buffer.alloc(W * H * 4);   // R shade, G gloss, B channel, A
const fx = Buffer.alloc(W * H * 4);     // R sweep, G sheen, B veins, A
const used = new Uint8Array(W * H);
function put(buf, x, y, c) { const i = (y * W + x) * 4; buf[i] = c[0]; buf[i + 1] = c[1]; buf[i + 2] = c[2]; buf[i + 3] = c[3]; }
const CH = { primary: 0, secondary: 128, eyes: 255 };

function shadeTexel(part, layer, face, p, faceN) {
	// bump-mapped normal on the rounded body
	const e = 0.18;
	const h0 = height(part, p);
	let t1, t2;
	if (Math.abs(faceN[1]) > 0.5) { t1 = [1, 0, 0]; t2 = [0, 0, 1]; } else if (Math.abs(faceN[0]) > 0.5) { t1 = [0, 0, 1]; t2 = [0, 1, 0]; } else { t1 = [1, 0, 0]; t2 = [0, 1, 0]; }
	const g1 = (height(part, add(p, t1, e)) - height(part, add(p, t1, -e))) / (2 * e);
	const g2 = (height(part, add(p, t2, e)) - height(part, add(p, t2, -e))) / (2 * e);
	let n = roundNormal(part, p, faceN);
	n = norm(add(add(n, t1, -g1 * 0.9), t2, -g2 * 0.9));

	const em = part === 'body' ? emblemSdf(p) : 1e9;
	const ey = part === 'head' ? eyeSdf(p, face) : 1e9;
	const vein = veinAt(part, p);
	const k = 0.5 / S; // half a texel, model px

	// ambient occlusion: the inner faces of limbs, under the arms, soles / palms, creases
	let ao = 0;
	if (face === 'bottom') ao -= 0.12;
	if ((part === 'rarm' && face === 'east') || (part === 'larm' && face === 'west')) ao -= 0.1;
	if (part === 'body' && (face === 'west' || face === 'east')) ao -= 0.06 * smooth(16, 22, p[1]);
	if ((part === 'rleg' && face === 'east') || (part === 'lleg' && face === 'west')) ao -= 0.08;
	ao += Math.min(0, h0) * 0.35;
	const dKey = Math.max(0, dot(n, LIGHT)), dFill = Math.max(0, dot(n, FILL)), dRim = Math.max(0, dot(n, RIM));
	const half = norm(add(LIGHT, faceN));
	const spec = (Math.pow(Math.max(0, dot(n, half)), 26) * 0.85 + Math.pow(Math.max(0, dot(n, norm(add(RIM, faceN)))), 18) * 0.35) * (part === 'head' && face === 'top' ? 0.45 : 1);
	const grain = fbm(p, 2.6) * 0.012;

	let ch, s, g, sheen, a = 255, raised = false;
	if (ey < -k * 0.2) {
		// the lens: bright, lit from the top, a darker inner corner, a glint
		ch = 'eyes';
		const up = (p[1] - 26.6) / 4.4;
		s = 0.47 + 0.1 * clamp(up) + 0.04 * dKey - 0.1 * smooth(-0.2, -0.9, ey) * 0 - 0.08 * smooth(0.25, -0.1, ey + 0.35);
		s -= 0.08 * smooth(1.6, 0.5, Math.abs(face === 'front' ? p[0] : 4)) * smooth(28.0, 26.8, p[1]);
		const glintD = Math.hypot(Math.abs(face === 'front' ? p[0] : 4.6) - 3.0, p[1] - 29.6);
		g = 0.85 * smooth(0.55, 0.15, glintD) + 0.12 * spec;
		sheen = 0.5;
		raised = true;
	} else if (ey < 0.62) {
		// the lens rim: the darkest line on the suit, so the eyes read from far away
		ch = 'primary'; s = 0.12 + 0.05 * dKey; g = 0.18 * spec; sheen = 0.25; raised = true;
	} else if (em < -k * 0.3) {
		ch = 'secondary';
		s = 0.4 + 0.16 * dKey + 0.04 * dFill + 0.05 * dRim + ao * 0.6 + grain;
		s -= 0.07 * smooth(-0.32, -0.02, em); // a softer inner rim so the edge reads round
		g = clamp(spec * 0.75 + 0.1 * smooth(-0.6, -0.2, em) * dKey);
		sheen = 0.55;
		raised = true;
	} else if (em < 0.42) {
		ch = 'primary'; s = 0.14 + 0.06 * dKey; g = 0.25 * spec; sheen = 0.3; raised = em < 0.28;
	} else {
		ch = 'primary';
		s = 0.29 + 0.22 * dKey + 0.05 * dFill + 0.09 * dRim + ao + grain + 0.035 * vein;
		g = clamp(spec * (1 - 0.25 * vein) + 0.25 * vein * dKey * 0.4);
		sheen = 0.45 + 0.4 * dKey;
		raised = vein > 0.9 && part !== 'head';
		if (raised) { s += 0.05; g = clamp(g + 0.15); }
	}
	if (layer === 1 && !raised) a = 0;
	// the shell's raised strands get a top-lit edge
	if (layer === 1 && raised && ch === 'primary') s += 0.03;
	const sweep = clamp((p[1] + 0.45 * p[0] + 0.25 * p[2] + 5) / 42);
	return {
		tint: [Math.round(clamp(s) * 255), Math.round(clamp(g) * 255), CH[ch], a],
		fx: [Math.round(sweep * 255), Math.round(clamp(sheen) * 255), Math.round((ch === 'primary' && em > 0.42 && ey > 0.62 ? vein : 0) * 255), a],
	};
}

for (const [part, u, v, layer] of BOXES) {
	const g = PARTS[part];
	for (const f of facesOf(u, v, ...g.s)) {
		for (let ty = f.v0 * S; ty < (f.v0 + f.dv) * S; ty++) {
			for (let tx = f.u0 * S; tx < (f.u0 + f.du) * S; tx++) {
				const fu = ((tx + 0.5) / S - f.u0) / f.du, fv = ((ty + 0.5) / S - f.v0) / f.dv;
				const p = pointOn(f.id, fu, fv, g);
				const r = shadeTexel(part, layer, f.id, p, f.n);
				put(tint, tx, ty, r.tint);
				put(fx, tx, ty, r.fx);
				used[ty * W + tx] = 1;
			}
		}
	}
}

// ---------------------------------------------------------------- the goo patch (unused skin texels) the 3D extras are wrapped in
// region A: skin (24,0)-(40,8) (the suit-up tendrils SymbioteEmerge draws sample it); region B: skin (56,16)-(64,48) (spine nodes). A cylinder-ish glossy goo with a vertical highlight band.
function gooPatch(x0, y0, w, h) {
	for (let ty = y0 * S; ty < (y0 + h) * S; ty++) for (let tx = x0 * S; tx < (x0 + w) * S; tx++) {
		if (used[ty * W + tx]) continue;
		const u = (tx - x0 * S + 0.5) / (w * S), vv = (ty - y0 * S + 0.5) / (h * S);
		const p = [tx * 0.5, ty * 0.5, 0];
		const roll = Math.sin(u * Math.PI);
		const s = 0.2 + 0.2 * roll + 0.06 * (1 - vv) + fbm(p, 1.4) * 0.04;
		const g = Math.pow(Math.max(0, roll), 14) * 0.45 * (0.6 + 0.4 * (1 - vv));
		put(tint, tx, ty, [Math.round(clamp(s) * 255), Math.round(clamp(g) * 255), 0, 255]);
		put(fx, tx, ty, [Math.round(clamp(0.5 + vv * 0.1) * 255), 110, 0, 255]);
	}
}
gooPatch(24, 0, 16, 8);
gooPatch(56, 16, 8, 32);

// ---------------------------------------------------------------- bake (twin of SymbioteSuitTint#bake)
function hue(c) {
	const m = Math.max(c[0], c[1], c[2]);
	if (m < 0.03) return [0.82, 0.86, 1.0];
	return [c[0] / m, c[1] / m, c[2] / m];
}
function bakePixel(r, gl, b, a, cols) {
	if (a === 0) return [0, 0, 0, 0];
	const c = (b >= 192 ? cols.eyes : b >= 64 ? cols.secondary : cols.primary).map((v) => v / 255);
	const s = r / 255, g = gl / 255;
	const hu = hue(c);
	let o;
	if (s <= 0.5) { const k = s / 0.5; o = c.map((v) => v * k); } else {
		const t = (s - 0.5) / 0.5;
		const lift = hu.map((v) => (0.5 + 0.5 * v) * 0.86);
		o = c.map((v, i) => v + (Math.max(lift[i], v) - v) * t);
	}
	const spec = hu.map((v) => 0.72 + 0.28 * v);
	o = o.map((v, i) => v + (Math.max(spec[i], v) - v) * g);
	return [Math.round(clamp(o[0]) * 255), Math.round(clamp(o[1]) * 255), Math.round(clamp(o[2]) * 255), a];
}
function bake(src, cols) {
	const out = Buffer.alloc(src.length);
	for (let i = 0; i < src.length; i += 4) {
		const px = bakePixel(src[i], src[i + 1], src[i + 2], src[i + 3], cols);
		out[i] = px[0]; out[i + 1] = px[1]; out[i + 2] = px[2]; out[i + 3] = px[3];
	}
	return out;
}
const rgb = (h) => [(h >> 16) & 255, (h >> 8) & 255, h & 255];
// keep in step with SymbioteColourPreset.java
const PRESETS = [
	['classic', 0x14161F, 0xF2F4F8, 0xF4F6FA],
	['anti_venom', 0xEEF0F2, 0x16171C, 0x101114],
	['carnage', 0xB3121B, 0x1A0A0C, 0xF6F1EA],
	['scream', 0xE6C21E, 0xC2241F, 0xF7F4E8],
	['agony', 0x5B2A86, 0x121016, 0xF2E9FF],
	['riot', 0x8E959E, 0x2F6FD6, 0xF0F4FA],
	['toxin', 0x1E4FB8, 0xC81E2A, 0xF4F6FA],
];
const colsOf = (p) => ({ primary: rgb(p[1]), secondary: rgb(p[2]), eyes: rgb(p[3]) });

// ---------------------------------------------------------------- write
function write(rel, w, h, buf) { const f = path.join(ASSETS, rel); fs.mkdirSync(path.dirname(f), { recursive: true }); PNG.write(f, w, h, buf); console.log('wrote', rel); }
write('textures/armor/symbiote_host_tint.png', W, H, tint);
write('textures/armor/symbiote_host_fx.png', W, H, fx);
write('textures/armor/symbiote_host.png', W, H, bake(tint, colsOf(PRESETS[0])));

// ---------------------------------------------------------------- geo
function cube(name, origin, size, uv, inflate) { const c = { name, origin, size, uv }; if (inflate) c.inflate = inflate; return c; }
function faceUv(region, faces) { // per-face UVs, all faces sampling sub-rectangles of a region {u, v} (skin px)
	const o = {};
	for (const [k, wh] of Object.entries(faces)) o[k] = { uv: [region.u + (wh[2] || 0), region.v + (wh[3] || 0)], uv_size: [wh[0], wh[1]] };
	return o;
}
// per-face UVs onto the body's own front region (a raised plate shows exactly the texels behind it)
function plateUv(u0, v0, w, h, depth) {
	return {
		north: { uv: [u0, v0], uv_size: [w, h] },
		south: { uv: [u0, v0], uv_size: [w, h] },
		east: { uv: [u0, v0], uv_size: [depth, h] },
		west: { uv: [u0 + w - depth, v0], uv_size: [depth, h] },
		up: { uv: [u0, v0], uv_size: [w, depth] },
		down: { uv: [u0, v0 + h - depth], uv_size: [w, depth] },
	};
}
const A = { u: 24, v: 0 }, B = { u: 56, v: 16 };
const dripFaces = (w, h, d) => faceUv(B, { north: [w, h, 0, 0], south: [w, h, 1, 0], east: [d, h, 2, 0], west: [d, h, 3, 0], up: [w, d, 4, 0], down: [w, d, 5, 0] });
const bones = [
	{ name: 'armorHead', pivot: [0, 24, 0], cubes: [cube('head_base', [-4, 24, -4], [8, 8, 8], [0, 0], 0.3), cube('head_shell', [-4, 24, -4], [8, 8, 8], [32, 0], 0.62)] },
	{ name: 'armorBody', pivot: [0, 24, 0], cubes: [cube('body_base', [-4, 12, -2], [8, 12, 4], [16, 16], 0.3), cube('body_shell', [-4, 12, -2], [8, 12, 4], [16, 32], 0.55)] },
	// raised chest: the pecs + emblem stand proud of the body, wrapped in the body-front texels behind them (skin 20..28, 20..24)
	{ name: 'sym_chest', parent: 'armorBody', pivot: [0, 24, 0], cubes: [{ name: 'chest_plate', origin: [-3.75, 18.5, -2.62], size: [7.5, 3.9, 0.6], inflate: 0.12, uv: plateUv(20.25, 21.6, 7.5, 3.9, 0.6) }] },
	// spine nodes below the back emblem
	{ name: 'sym_spine', parent: 'armorBody', pivot: [0, 15, 2.4], cubes: [
		{ name: 'spine_1', origin: [-0.55, 15.6, 2.1], size: [1.1, 0.9, 0.7], uv: dripFaces(1.1, 0.9, 0.7) },
		{ name: 'spine_2', origin: [-0.5, 14.1, 2.1], size: [1.0, 0.85, 0.65], uv: dripFaces(1.0, 0.85, 0.65) },
		{ name: 'spine_3', origin: [-0.45, 12.7, 2.1], size: [0.9, 0.8, 0.6], uv: dripFaces(0.9, 0.8, 0.6) }] },
	{ name: 'armorRightArm', pivot: [-5, 22, 0], cubes: [cube('right_arm_base', [-8, 12, -2], [4, 12, 4], [40, 16], 0.3), cube('right_arm_shell', [-8, 12, -2], [4, 12, 4], [40, 32], 0.55)] },
	{ name: 'armorLeftArm', pivot: [5, 22, 0], cubes: [cube('left_arm_base', [4, 12, -2], [4, 12, 4], [32, 48], 0.3), cube('left_arm_shell', [4, 12, -2], [4, 12, 4], [48, 48], 0.55)] },
	{ name: 'armorRightLeg', pivot: [-1.9, 12, 0], cubes: [cube('right_leg_base', [-3.9, 0, -2], [4, 12, 4], [0, 16], 0.3), cube('right_leg_shell', [-3.9, 0, -2], [4, 12, 4], [0, 32], 0.55)] },
	// raised quads: the thigh front texels again, standing proud (skin leg front 4..8, rows 20.. => y 11..7)
	{ name: 'sym_thigh_r', parent: 'armorRightLeg', pivot: [-1.9, 12, 0], cubes: [{ name: 'thigh_r', origin: [-3.6, 7.0, -2.55], size: [3.4, 3.6, 0.5], inflate: 0.1, uv: plateUv(4.3, 21.4, 3.4, 3.6, 0.5) }] },
	{ name: 'armorLeftLeg', pivot: [1.9, 12, 0], cubes: [cube('left_leg_base', [-0.1, 0, -2], [4, 12, 4], [16, 48], 0.3), cube('left_leg_shell', [-0.1, 0, -2], [4, 12, 4], [0, 48], 0.55)] },
	{ name: 'sym_thigh_l', parent: 'armorLeftLeg', pivot: [1.9, 12, 0], cubes: [{ name: 'thigh_l', origin: [0.2, 7.0, -2.55], size: [3.4, 3.6, 0.5], inflate: 0.1, uv: plateUv(20.3, 53.4, 3.4, 3.6, 0.5) }] },
	{ name: 'armorRightBoot', pivot: [-1.9, 12, 0] },
	{ name: 'armorLeftBoot', pivot: [1.9, 12, 0] },
];
const geo = {
	format_version: '1.12.0',
	'minecraft:geometry': [{
		description: { identifier: 'geometry.symbiote_host', texture_width: 64, texture_height: 64, visible_bounds_width: 3, visible_bounds_height: 3.5, visible_bounds_offset: [0, 1.25, 0] },
		bones,
	}],
};
const geoFile = path.join(ASSETS, 'geo', 'symbiote_host.geo.json');
fs.writeFileSync(geoFile, JSON.stringify(geo, null, '\t').replace(/\n/g, '\r\n') + '\r\n');
console.log('wrote geo/symbiote_host.geo.json');

// ---------------------------------------------------------------- item icons: three layers (primary tinted, untinted gloss, secondary tinted)
// 16x16 pixel art drawn from small ASCII maps: '#' body, '+' body highlight, 'e' emblem, 'o' eye, '.' empty
const ICONS = {
	helmet: [
		'................', '................', '.....######.....', '....########....', '...##########...', '...#+######+#...', '..##oo####oo##..', '..#ooo####ooo#..',
		'..#oooo##oooo#..', '..##ooo##ooo##..', '...##########...', '...##########...', '....########....', '.....######.....', '................', '................'],
	chestplate: [
		'................', '..####....####..', '.#+###eeee###+#.', '.#####eeee#####.', '.##ee#eeee#ee##.', '.##eeeeeeeeee##.', '.###e#eeee#e###.', '.##..#eeee#..##.',
		'.##..##ee##..##.', '.##..##ee##..##.', '.##..#####...##.', '.##..######..##.', '.##..######..##.', '.....######.....', '................', '................'],
	leggings: [
		'................', '...##########...', '...#+########...', '...##########...', '...#####.####...', '...#+##..##+#...', '...####..####...', '...####..####...',
		'...####..####...', '...####..####...', '...####..####...', '...####..####...', '...####..####...', '...####..####...', '................', '................'],
	boots: [
		'................', '................', '................', '................', '................', '................', '...####..####...', '...#+##..#+##...',
		'...####..####...', '...####..####...', '..#####..#####..', '..#####..#####..', '..######.######.', '..######.######.', '................', '................'],
};
for (const [name, map] of Object.entries(ICONS)) {
	const base = Buffer.alloc(16 * 16 * 4), gloss = Buffer.alloc(16 * 16 * 4), emb = Buffer.alloc(16 * 16 * 4);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const c = map[y][x], i = (y * 16 + x) * 4;
		if (c === '.') continue;
		const edge = (dx, dy) => { const yy = y + dy, xx = x + dx; return yy < 0 || yy > 15 || xx < 0 || xx > 15 || map[yy][xx] === '.'; };
		if (c === 'e' || c === 'o') {
			const v = c === 'o' ? 255 : (edge(0, -1) ? 255 : 228);
			emb[i] = emb[i + 1] = emb[i + 2] = v; emb[i + 3] = 255;
			continue;
		}
		// body: light grey ramp (multiplied by the primary colour in game), darker at the bottom / right edges
		let v = 225 - y * 4 - (edge(1, 0) || edge(0, 1) ? 50 : 0) + (edge(-1, 0) || edge(0, -1) ? 18 : 0);
		base[i] = base[i + 1] = base[i + 2] = clamp(v, 60, 255); base[i + 3] = 255;
		// gloss: a white sheen on the top-left edges and the '+' spots (untinted, translucent, so a black suit still reads)
		const ga = c === '+' ? 200 : edge(-1, 0) || edge(0, -1) ? 95 : 0;
		if (ga) { gloss[i] = gloss[i + 1] = gloss[i + 2] = 255; gloss[i + 3] = ga; }
	}
	write('textures/item/symbiote_host_' + name + '.png', 16, 16, base);
	write('textures/item/symbiote_host_' + name + '_gloss.png', 16, 16, gloss);
	write('textures/item/symbiote_host_' + name + '_emblem.png', 16, 16, emb);
	const model = { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/symbiote_host_' + name, layer1: 'projecthero:item/symbiote_host_' + name + '_gloss', layer2: 'projecthero:item/symbiote_host_' + name + '_emblem' } };
	fs.writeFileSync(path.join(ASSETS, 'models', 'item', 'symbiote_host_' + name + '.json'), JSON.stringify(model));
}

// ---------------------------------------------------------------- preview: front / back / side of every preset, flat-projected
function project(img, view) {
	// paint the faces facing the camera, base then shell, on a 16 px wide x 32 px tall figure (x S x 4 zoom)
	const Z = 4, FW = 18 * S * Z, FH = 34 * S * Z;
	const out = Buffer.alloc(FW * FH * 4);
	for (let i = 0; i < out.length; i += 4) { out[i] = 92; out[i + 1] = 98; out[i + 2] = 110; out[i + 3] = 255; }
	const want = view === 'front' ? 'front' : view === 'back' ? 'back' : 'east';
	for (const layer of [0, 1]) for (const [part, u, v, l] of BOXES) {
		if (l !== layer) continue;
		const g = PARTS[part];
		const f = facesOf(u, v, ...g.s).find((ff) => ff.id === want);
		for (let ty = 0; ty < f.dv * S; ty++) for (let tx = 0; tx < f.du * S; tx++) {
			const sx = f.u0 * S + tx, sy = f.v0 * S + ty, si = (sy * W + sx) * 4;
			if (img[si + 3] === 0) continue;
			const fu = (tx + 0.5) / (f.du * S), fv = (ty + 0.5) / (f.dv * S);
			const p = pointOn(f.id, fu, fv, g);
			let X = view === 'front' ? p[0] : view === 'back' ? -p[0] : -p[2] * (part === 'rarm' || part === 'rleg' ? 1 : 1);
			if (view === 'side' && (part === 'rarm' || part === 'rleg')) continue; // the near side only
			const px = Math.floor((X + 9) * S * Z - Z * S * 0.5), py = Math.floor((33 - p[1]) * S * Z - Z * S * 0.5);
			for (let yy = 0; yy < Z; yy++) for (let xx = 0; xx < Z; xx++) {
				const ox = px + xx + (view === 'front' ? 0 : 0), oy = py + yy;
				if (ox < 0 || oy < 0 || ox >= FW || oy >= FH) continue;
				const oi = (oy * FW + ox) * 4;
				out[oi] = img[si]; out[oi + 1] = img[si + 1]; out[oi + 2] = img[si + 2];
			}
		}
	}
	return { w: FW, h: FH, px: out };
}
const tiles = [];
for (const p of PRESETS) {
	const baked = bake(tint, colsOf(p));
	for (const view of ['front', 'back', 'side']) tiles.push(project(baked, view));
}
const cols = 6, tw = tiles[0].w, th = tiles[0].h, gap = 6;
const rows = Math.ceil(tiles.length / cols);
const SW = cols * tw + (cols - 1) * gap, SH = rows * th + (rows - 1) * gap;
const sheet = Buffer.alloc(SW * SH * 4, 255);
tiles.forEach((t, i) => {
	const ox = (i % cols) * (tw + gap), oy = Math.floor(i / cols) * (th + gap);
	for (let y = 0; y < th; y++) t.px.copy(sheet, ((oy + y) * SW + ox) * 4, y * tw * 4, (y + 1) * tw * 4);
});
fs.mkdirSync(SHOTS, { recursive: true });
PNG.write(path.join(SHOTS, 'texture_preview_presets.png'), SW, SH, sheet);
console.log('wrote preview', SW, 'x', SH);
