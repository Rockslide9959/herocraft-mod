// v0.16.7: SASS, the Kwami of Intuition (Viperion, the Snake Bracelet, loves eggs) -- a calm, wise little green snake
// Kwami with big eyes, on the same chibi plan as the other Kwamis (gen_kwami_models.js / gen_wayzz_kaalki_concept.js in
// the main checkout's scratchpad; the library lib_concept_models.js is copied here). His body tapers into a curling
// tail where the others have legs (the leg bones stay, empty, so every shared clip still drives the same bones).
//
// Writes into the game (Usage: node scratchpad/viperion/gen_sass.js):
//   assets/projecthero/geo/miraculous/sass.geo.json, textures/entity/miraculous/sass.png
//   assets/projecthero/animations/miraculous/kwami.animation.json   (+ animation.kwami.sass.{idle, wave, eat, tired,
//                                                                     into_jewel, pop_out}; the other clips untouched)
//   assets/projecthero/models/item/snake_bracelet.json + textures/item/snake_bracelet.png (hand-painted 16 x 16)
// and a concept sheet scratchpad/viperion/sass_sheet.png.
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('./lib_concept_models.js');
const { Model, mix, mul, fbm, hash3, clamp } = L;

const RES = path.join(__dirname, '..', '..', 'src', 'main', 'resources');
const A = path.join(RES, 'assets', 'projecthero');
const mk = d => (fs.mkdirSync(d, { recursive: true }), d);
const writeJson = (f, o) => { mk(path.dirname(f)); fs.writeFileSync(f, JSON.stringify(o, null, '\t') + '\n'); };
const writePng = (f, w, h, px) => { mk(path.dirname(f)); fs.writeFileSync(f, L.encodePng(w, h, px)); };

// ================================================================ the rig
let DY = 0; // the whole rig is lifted so its lowest point sits on y = 0 (painters subtract it)
const HC = () => [0, 9.6 + DY, 0];
const HS = [9.4, 7.8, 8.2];
const BC = () => [0, 4.4 + DY, 0];
const EYE = () => ({ x: 1.9, y: HC()[1] - 0.2, rx: 1.5, ry: 1.9 });

function blob(M, b, ctr, size, k, mat, opts = {}) {
	const [w, h, d] = size;
	M.cbox(b, ctr, [w, h - 2 * k, d - 2 * k], mat, opts);
	M.cbox(b, ctr, [w - 2 * k, h, d - 2 * k], mat, opts);
	M.cbox(b, ctr, [w - 2 * k, h - 2 * k, d], mat, opts);
	M.cbox(b, ctr, [w - k * 0.9, h - k * 0.9, d - k * 0.9], mat, opts);
}
const up = (v) => [v[0], v[1] + DY, v[2]];

function build() {
	const M = new Model('sass_kwami', { TW: 512, D: 8 });
	M.bone('root', null, [0, 0, 0]);
	const body = M.bone('body', 'root', up([0, 2.4, 0]));
	blob(M, body, BC(), [4.6, 4.6, 4.2], 1.0, 'body');
	const head = M.bone('head', 'body', up([0, 5.8, 0]));
	blob(M, head, HC(), HS, 1.5, 'head');
	// a soft snout on the lower face, and the crest of darker scales down the back of the head
	M.cbox(head, up([0, 7.35, -4.25]), [4.6, 1.5, 1.1], 'snout', { seed: 70 });
	M.cbox(head, up([0, 13.55, 0.6]), [2.6, 0.7, 6.0], 'crest', { seed: 80 });
	M.cbox(head, up([0, 12.2, 4.2]), [2.2, 2.8, 0.9], 'crest', { seed: 81 });
	for (const s of [-1, 1]) {
		const n = s < 0 ? 'r' : 'l';
		const arm = M.bone(`arm_${n}`, 'body', up([s * 2.3, 5.6, 0]), [0, 0, -s * 18]);
		M.box(arm, s * 2.3 - 0.7, 3.2 + DY, -0.7, s * 2.3 + 0.7, 6.0 + DY, 0.7, 'limb');
		M.box(arm, s * 2.3 - 0.78, 2.9 + DY, -0.78, s * 2.3 + 0.78, 3.8 + DY, 0.78, 'paw');
		M.bone(`leg_${n}`, 'body', up([s * 1.25, 2.2, 0])); // empty: the tail is his legs
	}
	// the tail: from under the body, down and back, curling round to the side with the tip turned up
	const tail = M.bone('tail', 'body', up([0, 2.6, 0.4]));
	M.tube(tail, [up([0, 2.9, 0.2]), up([0, 1.3, 1.2]), up([0.9, 0.7, 2.8]), up([2.4, 1.0, 3.6]), up([3.4, 2.0, 3.1]), up([3.6, 3.2, 2.0])],
		[[3.0, 3.0], [2.5, 2.5], [2.0, 2.0], [1.5, 1.5], [1.0, 1.0]], 'tail', { seed: 100 });
	return M;
}

// ================================================================ the paint
const SS = { green: [126, 204, 104], greenD: [70, 150, 74], greenL: [168, 228, 128], belly: [222, 236, 168], mark: [44, 112, 60],
	iris: [86, 150, 230], paw: [100, 182, 92] };
const inEll = (x, y, cx, cy, rx, ry) => ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2;
function soft(base, c, f, p, info) {
	let col = mul(base, 0.95 + 0.08 * fbm(p[0] * 0.8 + c.seed, p[1] * 0.8, p[2] * 0.8, 3));
	if (f === 'down') col = mul(col, 0.8);
	if (info.ePx < 0.18) col = mul(col, 0.92);
	return col;
}
function eyes(p) {
	const E = EYE();
	for (const s of [-1, 1]) {
		const ex = s * E.x;
		const q = inEll(p[0], p[1], ex, E.y, E.rx, E.ry);
		if (q >= 1) continue;
		if (q > 0.8) return [18, 22, 20];
		const lower = clamp((E.y - p[1]) / E.ry * 0.5 + 0.5, 0, 1);
		let col = mix(mul(SS.iris, 0.7), mix(SS.iris, [255, 255, 255], 0.25), lower);
		if (inEll(p[0], p[1], ex, E.y + 0.1, 0.55, 1.2) < 1) col = [14, 20, 30]; // a gentle slit of a pupil
		if (inEll(p[0], p[1], ex + 0.5, E.y + 0.8, 0.36, 0.4) < 1) col = [255, 255, 255];
		if (inEll(p[0], p[1], ex - 0.45, E.y - 0.65, 0.2, 0.22) < 1) col = [236, 244, 255];
		return col;
	}
	return null;
}
// diamond scale markings: a lattice of darker diamonds along the back / top
function diamond(a, b, size) {
	const u = (a + b) / size, v = (a - b) / size;
	const fu = u - Math.floor(u), fv = v - Math.floor(v);
	return Math.min(fu, 1 - fu, fv, 1 - fv);
}
function shade(c, f, s, t, p, info) {
	let col;
	const y = p[1] - DY;
	switch (c.mat) {
		case 'paw': col = soft(SS.paw, c, f, p, info); break;
		case 'limb': col = soft(SS.green, c, f, p, info); break;
		case 'crest': col = soft(hash3(Math.floor(p[2] * 1.5), Math.floor(y * 1.5), 3) > 0.5 ? SS.mark : mul(SS.mark, 1.2), c, f, p, info); break;
		case 'snout': {
			col = soft(SS.greenL, c, f, p, info);
			if (f === 'north') {
				for (const sx of [-1, 1]) if (inEll(p[0], y, sx * 0.8, 7.55, 0.22, 0.18) < 1) col = [44, 70, 40]; // nostrils
				// a calm little smile
				if (Math.abs(p[0]) < 1.3 && Math.abs(y - (7.0 + 0.25 * (p[0] / 1.3) ** 2)) < 0.13) col = [40, 70, 36];
			}
			break;
		}
		case 'tail': {
			// pale belly scales underneath, the diamond pattern along the top
			const under = f === 'down' || (info.j / info.h > 0.6 && (f === 'east' || f === 'west'));
			col = soft(under ? SS.belly : SS.green, c, f, p, info);
			if (!under && diamond(p[0] * 1.3, p[2] * 1.3, 2.4) < 0.12) col = soft(SS.mark, c, f, p, info);
			break;
		}
		case 'body': {
			const belly = p[2] < -0.6 && Math.abs(p[0]) < 1.8 - 0.15 * Math.abs(y - 4.4);
			col = soft(belly ? SS.belly : SS.green, c, f, p, info);
			if (belly && f === 'north' && Math.abs(((y - 2.6) % 1.1) - 0.55) > 0.47) col = mul(col, 0.88); // belly scale rows
			if (!belly && (f === 'south' || f === 'up') && diamond(p[0], y, 2.2) < 0.13) col = soft(SS.mark, c, f, p, info);
			break;
		}
		default: col = soft(SS.green, c, f, p, info);
	}
	if (c.mat === 'head') {
		// darker diamond markings over the top and back of the head; a paler chin
		const cap = y > 12.0 || f === 'south';
		if (cap && diamond(p[0] * 1.1, f === 'up' ? p[2] * 1.1 : y * 1.1, 2.6) < 0.14) col = soft(SS.mark, c, f, p, info);
		if (p[2] < -0.4 && y < 6.9) col = soft(mix(SS.green, SS.belly, 0.55), c, f, p, info);
		if (f === 'north' && p[2] < -3.0) {
			const e = eyes([p[0], p[1], p[2]]);
			if (e) col = e;
			// soft brow ridges: a calm, half-lidded look
			const E = EYE();
			for (const s of [-1, 1]) if (inEll(p[0], p[1], s * E.x, E.y + 1.75, 1.6, 0.32) < 1 && !e) col = soft(SS.greenD, c, f, p, info);
		}
	}
	return [col, 255, null];
}

// ================================================================ build it (twice: once to find the floor)
let M = build();
DY = -M.bounds(M.world({})).lo[1];
M = build();
if (Math.abs(M.bounds(M.world({})).lo[1]) > 1e-6) throw new Error('not grounded');
M.setHeight(0.62);
M.layout();
M.paint(shade);
const geo = M.geoJson('geometry.kwami_sass');
writeJson(path.join(A, 'geo', 'miraculous', 'sass.geo.json'), geo);
writePng(path.join(A, 'textures', 'entity', 'miraculous', 'sass.png'), M.TW, M.TH, M.tex);
console.log(`sass: ${M.bones.length} bones, ${M.cubes().length} cubes, tex ${M.TW}x${M.TH}, lift ${DY.toFixed(3)}`);

// ================================================================ the clips (the shared Kwami clip code, + the tail)
const TAU = Math.PI * 2;
const r3 = v => Math.round(v * 1000) / 1000;
const ease = t => t * t * (3 - 2 * t);
const tkey = t => String(Math.round(t * 100) / 100);
function clip(len, loop, fn, step = 0.1) {
	const bones = {};
	const n = Math.round(len / step);
	for (let i = 0; i <= n; i++) {
		const t = i === n ? len : i * step;
		const frame = fn(t);
		for (const [b, ch] of Object.entries(frame)) {
			bones[b] = bones[b] || {};
			for (const [k, v] of Object.entries(ch)) {
				const key = k === 'rot' ? 'rotation' : k === 'pos' ? 'position' : 'scale';
				bones[b][key] = bones[b][key] || {};
				bones[b][key][tkey(t)] = v.map(r3);
			}
		}
	}
	const o = { animation_length: len, bones };
	if (loop === true) o.loop = true;
	else if (loop === 'hold') o.loop = 'hold_on_last_frame';
	return o;
}
const FLOAT = { body: [6, 0, 0], head: [-8, 0, 5], arm_r: [-20, 0, 52], arm_l: [-30, 0, -36], tail: [-6, 0, 0] };
function idleFrame(t, len) {
	const w = TAU * t / len, w2 = 2 * w;
	return {
		root: { pos: [0, 0.9 * Math.sin(w2), 0] },
		body: { rot: [FLOAT.body[0] + 2 * Math.sin(w2 + 1), 0, 2.5 * Math.sin(w)] },
		head: { rot: [FLOAT.head[0] + 3 * Math.sin(w2 + 0.6), 7 * Math.sin(w), FLOAT.head[2] * 0.6 + 4 * Math.sin(w + 0.4)] },
		arm_r: { rot: [FLOAT.arm_r[0] + 6 * Math.sin(w2 + 0.5), 0, FLOAT.arm_r[2] + 8 * Math.sin(w2)] },
		arm_l: { rot: [FLOAT.arm_l[0] + 6 * Math.sin(w2 + 1.2), 0, FLOAT.arm_l[2] - 8 * Math.sin(w2 + 0.3)] },
		leg_r: { rot: [0, 0, 0] },
		leg_l: { rot: [0, 0, 0] },
		// the tail sways slowly from side to side, like a calm snake
		tail: { rot: [FLOAT.tail[0] + 5 * Math.sin(w2), 22 * Math.sin(w), 6 * Math.sin(w + 1)] },
	};
}
const ANIM = path.join(A, 'animations', 'miraculous', 'kwami.animation.json');
const animFile = JSON.parse(fs.readFileSync(ANIM, 'utf8'));
const clips = animFile.animations;
const P = 'animation.kwami.sass.';
clips[P + 'idle'] = clip(4.8, true, t => idleFrame(t, 4.8));
clips[P + 'wave'] = clip(1.5, false, t => {
	const k = Math.min(1, t / 0.2) * Math.min(1, (1.5 - t) / 0.25);
	const wv = Math.sin(TAU * t * 2.4);
	return {
		root: { pos: [0, 0.7 * Math.abs(Math.sin(TAU * t * 1.2)), 0] },
		body: { rot: [4, 0, -4 * k] },
		head: { rot: [-6, 8 * k, -9 * k] },
		arm_r: { rot: [-30 * k, 0, FLOAT.arm_r[2] + k * (14 + 16 * wv)], scale: [1, 1 + 0.3 * k, 1] },
		arm_l: { rot: [-10, 0, -10] },
		tail: { rot: [-10, 30 * Math.sin(TAU * t * 2), 0] },
	};
});
clips[P + 'eat'] = clip(2.0, true, t => {
	const nib = Math.sin(TAU * t * 4);
	return {
		root: { pos: [0, 0.5 * Math.sin(TAU * t), 0] },
		body: { rot: [10, 0, 0] },
		head: { rot: [10 + 6 * nib, 0, 3 * Math.sin(TAU * t)] },
		arm_r: { rot: [-100 + 8 * nib, 0, -40], scale: [1, 1.4, 1] },
		arm_l: { rot: [-100 + 8 * Math.sin(TAU * t * 4 + 1), 0, 40], scale: [1, 1.4, 1] },
		tail: { rot: [-8, 26 * Math.sin(TAU * t * 2), 0] },
	};
});
clips[P + 'tired'] = clip(3.0, true, t => {
	const s = Math.sin(TAU * t / 3);
	return {
		root: { pos: [0, -2 + 0.5 * s, 0] },
		body: { rot: [12 + 2 * s, 0, 3] },
		head: { rot: [12 + 3 * s, 0, 8] },
		arm_r: { rot: [6, 0, -12 + 2 * s] },
		arm_l: { rot: [6, 0, 12 - 2 * s] },
		tail: { rot: [24, 4 * s, 0] },
	};
});
clips[P + 'into_jewel'] = clip(0.6, 'hold', t => {
	const e = ease(t / 0.6);
	const sc = 1 - 0.97 * e;
	return {
		root: { rot: [0, 900 * e * e, 0], scale: [sc, sc, sc], pos: [0, 3 * Math.sin(Math.PI * e), 0] },
		body: { rot: [0, 0, 0] },
		head: { rot: [-12 * e, 0, 0] },
		arm_r: { rot: [-20, 0, 20 - 30 * e] },
		arm_l: { rot: [-20, 0, -20 + 30 * e] },
		tail: { rot: [0, 60 * e, 0] },
	};
}, 0.05);
clips[P + 'pop_out'] = clip(0.5, false, t => {
	const u = t / 0.5;
	const sc = u < 0.7 ? 1.15 * ease(u / 0.7) : 1.15 - 0.15 * ease((u - 0.7) / 0.3);
	const f = idleFrame(0, 4.8);
	f.root = { rot: [0, -360 * (1 - ease(u)), 0], scale: [sc, sc, sc], pos: [0, -2 * (1 - ease(u)), 0] };
	f.arm_r.rot[2] += 40 * Math.sin(Math.PI * u);
	f.arm_l.rot[2] -= 40 * Math.sin(Math.PI * u);
	return f;
}, 0.05);
writeJson(ANIM, animFile);
console.log('clips: ' + Object.keys(clips).length);

// ================================================================ the Snake Bracelet item (JSON elements, 16 x 16 swatches)
{
	const px = Buffer.alloc(16 * 16 * 4);
	const put = (x, y, c) => { const i = (y * 16 + x) * 4; for (let k = 0; k < 3; k++) px[i + k] = clamp(Math.round(c[k])); px[i + 3] = 255; };
	const sw = (k, fn) => { for (let y = 0; y < 4; y++) for (let x = 0; x < 4; x++) put((k % 4) * 4 + x, Math.floor(k / 4) * 4 + y, fn(x, y)); };
	const G = [46, 176, 120], B = [24, 26, 30];
	const scale = (base, x, y, k) => mul(base, 1.1 - 0.07 * y + (L.hash3(x, y, k) - 0.5) * 0.08 + ((x + y) % 2 ? 0.05 : -0.03));
	sw(0, (x, y) => scale(G, x, y, 1));                        // green band, lit
	sw(1, (x, y) => scale(mul(G, 0.72), x, y, 2));             // green band, shade
	sw(2, (x, y) => scale(B, x, y, 3).map(v => v + 8));        // black band, lit
	sw(3, (x, y) => scale(B, x, y, 4));                        // black band, shade
	sw(4, (x, y) => (x === 1 || x === 2) && y === 1 ? [250, 220, 70] : scale(G, x, y, 5)); // the head: two gold eyes
	sw(5, (x, y) => (y === 3 && (x === 1 || x === 2)) ? [200, 40, 50] : scale(mul(G, 0.9), x, y, 6)); // the snout, a red tongue tip
	writePng(path.join(A, 'textures', 'item', 'snake_bracelet.png'), 16, 16, px);
	const swUv = k => { const u = (k % 4) * 4, v = Math.floor(k / 4) * 4; return [u, v, u + 4, v + 4]; };
	function el(from, to, k, rot, per = {}) {
		const faces = {};
		for (const f of ['north', 'south', 'east', 'west', 'up', 'down']) faces[f] = { uv: swUv(per[f] != null ? per[f] : k), texture: '#t' };
		const e = { from: from.map(r3), to: to.map(r3), faces };
		if (rot) e.rotation = rot;
		return e;
	}
	const els = [];
	// an octagon of eight bars round the wrist, green and black in turn (item rotations: 0 / 45 only); the head on top
	const C = [8, 7.2], R = 4.9, T = 1.2;
	const half = R * Math.tan(Math.PI / 8) + 0.12, mid = R - T / 2;
	const k8 = [0, 2, 0, 2, 0, 2, 0, 2];
	els.push(el([C[0] - half, C[1] - R, 7.3], [C[0] + half, C[1] - R + T, 8.7], 2, null, { down: 3 }));          // bottom (black)
	els.push(el([C[0] - R, C[1] - half, 7.3], [C[0] - R + T, C[1] + half, 8.7], 2, null, { down: 3 }));          // sides (black)
	els.push(el([C[0] + R - T, C[1] - half, 7.3], [C[0] + R, C[1] + half, 8.7], 2, null, { down: 3 }));
	for (const [sx, sy] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) {                                            // corners (green)
		const cx = C[0] + sx * mid * Math.SQRT1_2, cy = C[1] + sy * mid * Math.SQRT1_2;
		els.push(el([cx - half, cy - T / 2, 7.32], [cx + half, cy + T / 2, 8.68], 0, { axis: 'z', angle: sx === sy ? -45 : 45, origin: [r3(cx), r3(cy), 8] }, { down: 1 }));
	}
	void k8;
	const top = C[1] + R - T / 2;
	els.push(el([C[0] - 1.6, top - 0.9, 6.9], [C[0] + 1.6, top + 1.1, 9.4], 4, null, { north: 5, down: 1 }));   // the head
	els.push(el([C[0] - 1.0, top - 0.7, 6.2], [C[0] + 1.0, top + 0.5, 7.0], 5));                              // the snout
	els.push(el([C[0] + 1.5, top - 0.6, 7.4], [C[0] + 3.4, top + 0.4, 8.6], 2, null, { down: 3 }));            // the tail tip over the head
	writeJson(path.join(A, 'models', 'item', 'snake_bracelet.json'), {
		textures: { t: 'projecthero:item/snake_bracelet', particle: 'projecthero:item/snake_bracelet' }, gui_light: 'front', elements: els,
		display: {
			gui: { rotation: [24, 160, 0], translation: [0, -0.6, 0], scale: [1.1, 1.1, 1.1] },
			ground: { translation: [0, 2, 0], scale: [0.45, 0.45, 0.45] },
			fixed: { rotation: [0, 180, 0], scale: [1, 1, 1] },
			thirdperson_righthand: { rotation: [30, 140, 0], translation: [0, 2, 0.5], scale: [0.4, 0.4, 0.4] },
			thirdperson_lefthand: { rotation: [30, 140, 0], translation: [0, 2, 0.5], scale: [0.4, 0.4, 0.4] },
			firstperson_righthand: { rotation: [10, 150, 0], translation: [1, 3.5, 0], scale: [0.34, 0.34, 0.34] },
			firstperson_lefthand: { rotation: [10, 150, 0], translation: [1, 3.5, 0], scale: [0.34, 0.34, 0.34] },
		},
	});
	console.log('snake bracelet item: ' + els.length + ' elements');
}

// ================================================================ the concept sheet (for the report)
const POSES = { rest: {}, float: { body: FLOAT.body, head: FLOAT.head, arm_r: FLOAT.arm_r, arm_l: FLOAT.arm_l, tail: FLOAT.tail } };
const it = pose => ({ model: M, pose });
const fl = [{ x: 0, z: 0, r: 0.42 }];
L.sheet(path.join(__dirname, 'sass_sheet.png'), 'SASS', 'KWAMI OF INTUITION - VIPERION - SNAKE BRACELET - LOVES EGGS', [
	{ label: 'FRONT', items: [it(POSES.float)], cam: { yaw: 0, pitch: 6 }, floor: fl },
	{ label: 'THREE-QUARTER', items: [it(POSES.float)], cam: { yaw: 35, pitch: 12 }, floor: fl },
	{ label: 'SIDE', items: [it(POSES.float)], cam: { yaw: 90, pitch: 6 }, floor: fl },
	{ label: 'BACK', items: [it(POSES.float)], cam: { yaw: 180, pitch: 10 }, floor: fl },
], { panelH: 800, minW: 2000, bg: { top: [96, 150, 120], bottom: [30, 60, 46] } });
