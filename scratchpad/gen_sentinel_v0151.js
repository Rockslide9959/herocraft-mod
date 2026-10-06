// v0.15.1 Sentinel Purge: every asset of the three Sentinel Program robots, authored from scratch (no user models).
//   - geo/sentinel_drone.geo.json   + animations + textures/entity/sentinel_drone(.png|_glowmask.png)
//       a small purple scout pod with a silver Sentinel face, one burning eye-lens, side thruster pods and an antenna
//   - geo/sentinel.geo.json         + animations + textures/entity/sentinel(.png|_glowmask.png)
//       the main unit: authored 2 blocks tall, the entity's SCALE attribute (1.75) makes it ~3.5 -- purple-and-magenta
//       armour, the flared collar, a silver face with glowing eyes, a chest emitter, palm emitters and boot thrusters
//       (two flame bones the renderer only shows while it flies)
//   - geo/master_mold.geo.json      + animations + textures/entity/master_mold(.png|_glowmask.png)
//       the Sentinel factory: authored 2 blocks tall, SCALE 5.5 makes it ~11 -- a hulking Sentinel with a domed crest,
//       pylons, a back stack with glowing spires and a chest hangar hatch over a magenta fabrication core
//   - item textures + models: trask_signal, sentinel_circuitry, master_mold_core, three spawn eggs
// Usage (from the repo root): node scratchpad/gen_sentinel_v0151.js
// Every attack clip's length and key time is printed; the Java side mirrors them in SentinelAnims.
const fs = require('fs');
const path = require('path');
const { encode } = require('./pngkit');

const RES = path.join(__dirname, '..', 'src/main/resources');
const A = path.join(RES, 'assets/projecthero');
const json = (p, obj, indent = 1) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, JSON.stringify(obj, null, indent) + '\n'); };

let seed = 151151;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const mix = (a, b, t) => a.map((v, i) => clamp(v + (b[i] - v) * t));

// ---------------------------------------------------------------- palette
const PURPLE = [100, 46, 140], PURPLE_DARK = [58, 24, 84], PURPLE_LIGHT = [138, 72, 178];
const MAGENTA = [204, 52, 156], MAGENTA_DARK = [138, 26, 104], MAGENTA_LIGHT = [240, 110, 200];
const SILVER = [200, 202, 214], SILVER_DARK = [128, 130, 148];
const GUN = [60, 58, 70], GUN_DARK = [32, 30, 40];
const EYE = [255, 214, 90], EYE_HOT = [255, 252, 210];
const EMIT = [255, 96, 220], EMIT_HOT = [255, 228, 255];
const THRUST = [110, 196, 255], THRUST_HOT = [232, 250, 255];
const LENS = [255, 64, 48], LENS_HOT = [255, 220, 170];

// ================================================================ the model builder (shared by all three robots)
const FACES = ['north', 'south', 'east', 'west', 'up', 'down'];
const faceDims = (s, f) => (f === 'north' || f === 'south') ? [s[0], s[1]] : (f === 'east' || f === 'west') ? [s[2], s[1]] : [s[0], s[2]];

function buildModel(spec) {
	const { name, S, TW } = spec;
	const bones = [];
	const bone = (bn, parent, pivot) => { const b = { name: bn, parent, pivot, cubes: [] }; bones.push(b); return b; };
	const cube = (b, from, size, mat) => { b.cubes.push({ from, size, mat }); };
	spec.build(bone, cube);

	const rects = [];
	for (const b of bones) for (const c of b.cubes) {
		c.uv = {};
		for (const f of FACES) {
			const [w, h] = faceDims(c.size, f).map(v => Math.max(1, Math.ceil(v - 1e-6)));
			const r = { c, f, w, h };
			c.uv[f] = r;
			rects.push(r);
		}
	}
	let TH;
	{
		const order = [...rects].sort((a, b) => b.h - a.h || b.w - a.w);
		let x = 0, y = 0, rowH = 0;
		for (const r of order) {
			if (r.w > TW) throw new Error(`${name}: face wider than the texture`);
			if (x + r.w > TW) { x = 0; y += rowH; rowH = 0; }
			r.u = x; r.v = y; x += r.w; rowH = Math.max(rowH, r.h);
		}
		TH = Math.ceil((y + rowH) / 8) * 8;
	}
	const tex = Buffer.alloc(TW * TH * 4);
	const glow = Buffer.alloc(TW * TH * 4);
	const put = (buf, x, y, c, a = 255) => { const i = (y * TW + x) * 4; buf[i] = c[0]; buf[i + 1] = c[1]; buf[i + 2] = c[2]; buf[i + 3] = a; };
	for (const r of rects) paintFace(r, put, tex, glow);

	// sanity: parents first, rects inside the texture and never overlapping
	const seen = new Set();
	for (const b of bones) {
		if (b.parent && !seen.has(b.parent)) throw new Error(`${name}: bone ${b.name}: parent ${b.parent} not declared first`);
		if (seen.has(b.name)) throw new Error(`${name}: duplicate bone ${b.name}`);
		seen.add(b.name);
	}
	const owner = new Int32Array(TW * TH).fill(-1);
	rects.forEach((r, i) => {
		if (r.u < 0 || r.v < 0 || r.u + r.w > TW || r.v + r.h > TH) throw new Error('uv rect outside the texture');
		for (let y = r.v; y < r.v + r.h; y++) for (let x = r.u; x < r.u + r.w; x++) {
			if (owner[y * TW + x] >= 0) throw new Error('uv rects overlap');
			owner[y * TW + x] = i;
		}
	});

	const round = n => Math.round(n * 1000) / 1000;
	const geoBones = bones.map(b => {
		const out = { name: b.name };
		if (b.parent) out.parent = b.parent;
		out.pivot = b.pivot.map(v => round(v * S));
		if (b.cubes.length) {
			out.cubes = b.cubes.map(c => {
				const uv = {};
				for (const f of FACES) {
					const r = c.uv[f];
					uv[f] = { uv: [r.u, r.v], uv_size: [r.w, r.h] };
				}
				return { origin: c.from.map(v => round(v * S)), size: c.size.map(v => round(v * S)), uv };
			});
		}
		return out;
	});
	json(path.join(A, `geo/${name}.geo.json`), {
		format_version: '1.12.0',
		'minecraft:geometry': [{
			description: {
				identifier: `geometry.${name}`, texture_width: TW, texture_height: TH,
				visible_bounds_width: spec.bounds[0], visible_bounds_height: spec.bounds[1], visible_bounds_offset: [0, spec.bounds[1] / 2, 0],
			},
			bones: geoBones,
		}],
	});
	fs.mkdirSync(path.join(A, 'textures/entity'), { recursive: true });
	fs.writeFileSync(path.join(A, `textures/entity/${name}.png`), encode(TW, TH, tex));
	fs.writeFileSync(path.join(A, `textures/entity/${name}_glowmask.png`), encode(TW, TH, glow));
	console.log(`${name}: ${bones.length} bones, ${bones.reduce((n, b) => n + b.cubes.length, 0)} cubes, texture ${TW}x${TH}`);
	return { bones, S };
}

// ---------------------------------------------------------------- face painter
function paintFace(r, put, tex, glow) {
	const { c, f, w, h, u, v } = r;
	const at = (x, y) => [u + x, v + y];
	const side = f === 'north' || f === 'south' || f === 'east' || f === 'west';
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		const [px, py] = at(x, y);
		const edge = x === 0 || y === 0 || x === w - 1 || y === h - 1;
		let col = null, alpha = 255, g = null;
		switch (c.mat) {
			case 'armour': case 'helm': {
				// purple plate: a darker panel line round every face, a lighter top lip, panel seams on big faces, rivets
				col = mix(PURPLE, PURPLE_DARK, rnd() * 0.18);
				if (y === 1 && side && h > 4) col = mix(PURPLE_LIGHT, PURPLE, 0.3);
				if (w >= 10 && x % 8 === 0 && x > 0 && x < w - 1) col = PURPLE_DARK;
				if (h >= 10 && y % 7 === 0 && y > 0 && y < h - 1) col = PURPLE_DARK;
				if (edge) col = mix(PURPLE_DARK, GUN_DARK, 0.3);
				if (!edge && (x === 1 || x === w - 2) && (y === 1 || y === h - 2) && w > 5 && h > 5) col = SILVER_DARK; // rivets
				break;
			}
			case 'trim': {
				col = mix(MAGENTA, MAGENTA_DARK, rnd() * 0.25);
				if (y === 0 && side) col = MAGENTA_LIGHT;
				if (edge && y !== 0) col = mix(MAGENTA_DARK, PURPLE_DARK, 0.4);
				break;
			}
			case 'metal': {
				col = mix(GUN, GUN_DARK, rnd() * 0.4);
				if (side && y % 2 === 0 && w > 3) col = mix(col, GUN_DARK, 0.4); // ribbed joints
				if (edge) col = GUN_DARK;
				break;
			}
			case 'face': {
				col = mix(SILVER, SILVER_DARK, rnd() * 0.18 + (edge ? 0.3 : 0));
				break;
			}
			case 'boot': {
				col = mix(PURPLE, PURPLE_DARK, rnd() * 0.2);
				if (edge) col = mix(PURPLE_DARK, GUN_DARK, 0.3);
				if (f === 'down') {
					// the thruster nozzle: a dark ring round a white-hot core
					const cx = (w - 1) / 2, cy = (h - 1) / 2;
					const d = Math.hypot((x - cx) / Math.max(1, cx), (y - cy) / Math.max(1, cy));
					if (d < 0.45) { col = mix(THRUST_HOT, THRUST, d / 0.45); g = col; }
					else if (d < 0.75) col = GUN_DARK;
				}
				if (f === 'north' && y === h - 2 && x > 0 && x < w - 1) col = MAGENTA; // toe stripe
				break;
			}
			case 'emitter': {
				const cx = (w - 1) / 2, cy = (h - 1) / 2;
				const d = Math.hypot(x - cx, y - cy) / Math.max(1, Math.max(cx, cy));
				col = mix(EMIT_HOT, EMIT, Math.min(1, d));
				g = col;
				break;
			}
			case 'core': {
				const cx = (w - 1) / 2, cy = (h - 1) / 2;
				const d = Math.hypot(x - cx, y - cy) / Math.max(1, Math.max(cx, cy));
				col = mix(EMIT_HOT, MAGENTA, Math.min(1, d));
				if ((x + y) % 4 === 0 && d > 0.4) col = mix(col, EMIT, 0.5); // fabrication lattice
				g = col;
				break;
			}
			case 'eye': col = mix(EYE_HOT, EYE, rnd() * 0.5); g = col; break;
			case 'lens': {
				const cx = (w - 1) / 2, cy = (h - 1) / 2;
				const d = Math.hypot(x - cx, y - cy) / Math.max(1, Math.max(cx, cy));
				col = mix(LENS_HOT, LENS, Math.min(1, d * 1.2));
				g = col;
				break;
			}
			case 'flame': {
				col = mix(THRUST_HOT, THRUST, Math.min(1, y / Math.max(1, h - 1) + rnd() * 0.2));
				g = col;
				break;
			}
			case 'vent': {
				col = GUN_DARK;
				if (side && y % 2 === 1 && !edge) { col = mix(EMIT, MAGENTA_DARK, 0.5); g = mix(col, [0, 0, 0], 0.3); }
				if (edge) col = mix(PURPLE_DARK, GUN_DARK, 0.5);
				break;
			}
			default: throw new Error('unknown material ' + c.mat);
		}
		put(tex, px, py, col, alpha);
		if (g && alpha) put(glow, px, py, g);
	}
	// the faces: silver masks with burning, angular eyes and a grille mouth (north = the front)
	if (c.mat === 'face' && f === 'north') {
		const dark = [40, 38, 48];
		const ex = Math.max(1, Math.round(w * 0.12)), ew = Math.max(2, Math.round(w * 0.28));
		const ey = Math.max(1, Math.round(h * 0.22));
		for (const left of [true, false]) {
			for (let i = 0; i < ew; i++) {
				const x = left ? ex + i : w - 1 - ex - i;
				// brow-slanted: the inner end sits a texel lower
				const y0 = ey + (i >= ew - Math.ceil(ew / 2) ? 1 : 0);
				for (let y = y0; y <= ey + 1; y++) { put(tex, ...at(x, y), EYE_HOT); put(glow, ...at(x, y), EYE_HOT); }
				put(tex, ...at(x, ey - 1), dark); // brow shadow
			}
		}
		const my = Math.min(h - 2, Math.round(h * 0.68));
		for (let x = Math.round(w * 0.3); x <= w - 1 - Math.round(w * 0.3); x++) {
			put(tex, ...at(x, my), x % 2 ? dark : SILVER_DARK);
			if (my + 1 < h - 1) put(tex, ...at(x, my + 1), x % 2 ? dark : SILVER_DARK);
		}
		// cheek seams
		for (let y = ey + 2; y < h - 1; y++) { put(tex, ...at(1, y), SILVER_DARK); put(tex, ...at(w - 2, y), SILVER_DARK); }
	}
	if (c.mat === 'helm' && f === 'north') {
		// a magenta forehead chevron above the mask
		const mid = (w - 1) / 2;
		for (let x = 0; x < w; x++) {
			const y = Math.round(Math.abs(x - mid) * 0.4);
			if (y < 3) put(tex, ...at(x, y + 1), MAGENTA);
		}
	}
	if (c.mat === 'armour' && f === 'north' && c.chest) {
		// the Sentinel "X" chest bands meeting at the emitter
		for (let x = 0; x < w; x++) {
			const y1 = Math.round(x * (h - 1) / (w - 1));
			const y2 = h - 1 - y1;
			for (const y of [y1, y2]) if (y > 0 && y < h - 1) put(tex, ...at(x, y), MAGENTA_DARK);
		}
	}
}

// ================================================================ animation builder
function animator(model, REST, file, animName, scaleTracks = {}) {
	const BONE_TRACKS = Object.keys(REST);
	const animations = {};
	const TIMING = {};
	const pose = (o = {}) => {
		const p = {};
		for (const k of BONE_TRACKS) {
			const val = o[k];
			if (val === undefined) p[k] = REST[k];
			else if (typeof val === 'number') p[k] = k.endsWith('_s') ? [val, val, val] : [val, 0, 0];
			else p[k] = val;
		}
		return p;
	};
	function kf(entries) {
		const out = {};
		for (const [t, v, easing] of entries) {
			const r = v.map(n => Math.round(n * 1000) / 1000);
			out[t.toFixed(2)] = easing ? { vector: r, easing } : r;
		}
		return out;
	}
	function clip(name, length, loop, frames) {
		const bonesOut = {};
		for (const track of BONE_TRACKS) {
			const entries = frames.map(([t, p, e]) => [t, track === 'root' ? p.root.map(v => v * model.S) : p[track], e]);
			const neutral = track.endsWith('_s') ? 1 : 0;
			if (entries.every(([, v]) => v.every((n, i) => n === entries[0][1][i])) && entries[0][1].every(n => n === neutral)) continue;
			const boneName = track.endsWith('_s') ? scaleTracks[track] : track;
			const channel = track === 'root' ? 'position' : track.endsWith('_s') ? 'scale' : 'rotation';
			bonesOut[boneName] = bonesOut[boneName] || {};
			bonesOut[boneName][channel] = kf(entries);
		}
		for (const [t] of frames) if (t > length + 1e-6) throw new Error(`${name}: key at ${t} past the clip length ${length}`);
		const a = { animation_length: length, bones: bonesOut };
		if (loop === true) a.loop = true;
		else if (loop === 'hold') a.loop = 'hold_on_last_frame';
		animations[`animation.${animName}.${name}`] = a;
	}
	const T = (name, lenTicks, keyTicks) => { TIMING[name] = [lenTicks, keyTicks]; return [lenTicks / 20, keyTicks / 20]; };
	const write = () => {
		json(path.join(A, `animations/${file}.animation.json`), { format_version: '1.8.0', animations });
		for (const [n, [len, at]] of Object.entries(TIMING)) console.log(`  ${file} clip ${n.padEnd(14)} ${String(len).padStart(3)} ticks, key at ${at}`);
	};
	return { pose, clip, T, write, R: pose() };
}

// mirror helpers: side +1 = right (+x), -1 = left
const mirror = side => ({ x: v => side > 0 ? v : -v, lo: (a, w) => side > 0 ? a : -a - w, n: side > 0 ? 'right' : 'left' });

// ================================================================ 1. the Sentinel (64 du = 2 blocks; SCALE 1.75 in game)
const sentinel = buildModel({
	name: 'sentinel', S: 0.5, TW: 128, bounds: [3, 3],
	build(bone, cube) {
		bone('root', null, [0, 0, 0]);
		for (const s of [1, -1]) {
			const { x, lo, n } = mirror(s);
			const leg = bone(`${n}_leg`, 'root', [x(6), 28, 0]);
			cube(leg, [lo(2, 9), 16, -4], [9, 13, 8], 'armour');      // thigh
			cube(leg, [lo(1.5, 10), 14, -5], [10, 4, 9], 'metal');    // knee
			cube(leg, [lo(2, 9), 4, -4], [9, 11, 8], 'armour');       // shin
			cube(leg, [lo(3, 7), 6, -4.6], [7, 7, 0.8], 'trim');      // shin stripe
			cube(leg, [lo(1, 11), 0, -7], [11, 5, 13], 'boot');       // boot (thruster nozzle underneath)
			const flame = bone(`${n}_flame`, `${n}_leg`, [x(6.5), 0, -0.5]);
			cube(flame, [lo(3.5, 6), -9, -3.5], [6, 9, 6], 'flame');  // boot thruster flame (renderer shows it only in flight)
		}
		const hips = bone('hips', 'root', [0, 28, 0]);
		cube(hips, [-9, 25, -5], [18, 6, 10], 'metal');               // pelvis
		cube(hips, [-9.5, 29, -5.5], [19, 2, 11], 'trim');            // belt
		const torso = bone('torso', 'hips', [0, 30, 0]);
		cube(torso, [-7, 30, -4.5], [14, 6, 9], 'metal');             // abdomen
		const chest = { from: [-12, 36, -7], size: [24, 15, 14], mat: 'armour', chest: true };
		torso.cubes.push(chest);                                      // chest
		cube(torso, [-3, 41, -7.8], [6, 6, 1], 'emitter');            // chest emitter
		cube(torso, [-11, 45, -7.6], [7, 5, 0.8], 'trim');            // pectoral plates
		cube(torso, [4, 45, -7.6], [7, 5, 0.8], 'trim');
		cube(torso, [-8, 37, 7], [16, 12, 4], 'vent');                // back thruster pack
		cube(torso, [-10, 50, -2], [20, 7, 10], 'trim');              // the flared collar
		cube(torso, [-11, 49, -4], [2, 9, 8], 'trim');                // collar wings
		cube(torso, [9, 49, -4], [2, 9, 8], 'trim');
		const head = bone('head', 'torso', [0, 51, 0]);
		cube(head, [-5, 51, -5.5], [10, 11, 10], 'helm');
		cube(head, [-4.5, 51.5, -6.3], [9, 8, 0.8], 'face');          // the silver mask
		cube(head, [-1, 62, -5], [2, 1.5, 8], 'trim');                // crest
		for (const s of [1, -1]) {
			const { x, lo, n } = mirror(s);
			const arm = bone(`${n}_arm`, 'torso', [x(14), 48, 0]);
			cube(arm, [lo(11, 10), 44, -6], [10, 8, 12], 'armour');   // pauldron
			cube(arm, [lo(11.5, 11), 43, -6.5], [11, 2, 13], 'trim'); // pauldron rim
			cube(arm, [lo(12, 7), 31, -3.5], [7, 13, 7], 'metal');    // upper arm
			const fore = bone(`${n}_forearm`, `${n}_arm`, [x(15.5), 32, 0]);
			cube(fore, [lo(11.5, 8), 18, -4], [8, 14, 8], 'armour');  // forearm
			cube(fore, [lo(11, 9), 27, -4.5], [9, 3, 9], 'trim');     // cuff
			cube(fore, [lo(12, 7), 13, -3.5], [7, 5, 7], 'metal');    // hand
			cube(fore, [lo(12.5, 6), 12.4, -3], [6, 0.6, 6], 'emitter'); // palm emitter
		}
	},
});

{
	const REST = {
		root: [0, 0, 0], hips: [0, 0, 0], torso: [3, 0, 0], head: [-3, 0, 0],
		right_arm: [-4, 0, 0], right_forearm: [-10, 0, 0], left_arm: [-4, 0, 0], left_forearm: [-10, 0, 0],
		right_leg: [0, 0, 0], left_leg: [0, 0, 0],
	};
	const { pose, clip, T, write, R } = animator(sentinel, REST, 'sentinel', 'sentinel');
	clip('idle', 3.0, true, [
		[0, R],
		[0.8, pose({ head: [-3, 14, 0], torso: 4, root: [0, -0.3, 0] }), 'easeinoutsine'],
		[1.6, pose({ head: [-3, -14, 0], torso: 4, root: [0, -0.3, 0] }), 'easeinoutsine'],
		[3.0, R, 'easeinoutsine'],
	]);
	const st = 24;
	clip('walk', 1.4, true, [
		[0, pose({ right_leg: -st, left_leg: st, right_arm: 14, left_arm: -20, torso: 6, root: [0, -1, 0] })],
		[0.35, pose({ torso: 5, root: [0, 0.6, 0] })],
		[0.7, pose({ right_leg: st, left_leg: -st, right_arm: -20, left_arm: 14, torso: 6, root: [0, -1, 0] })],
		[1.05, pose({ torso: 5, root: [0, 0.6, 0] })],
		[1.4, pose({ right_leg: -st, left_leg: st, right_arm: 14, left_arm: -20, torso: 6, root: [0, -1, 0] })],
	]);
	const flyA = pose({ torso: 16, head: -14, right_leg: 14, left_leg: 8, right_arm: 22, left_arm: 22, right_forearm: -6, left_forearm: -6 });
	clip('fly', 1.2, true, [
		[0, flyA],
		[0.6, pose({ ...flyA, right_leg: 8, left_leg: 14, root: [0, 1.2, 0] }), 'easeinoutsine'],
		[1.2, flyA, 'easeinoutsine'],
	]);
	{ // chest beam: rears back, then the chest emitter fires
		const [len, fire] = T('beam_chest', 24, 12);
		const rear = pose({ torso: -14, head: -10, right_arm: 30, left_arm: 30, right_forearm: -30, left_forearm: -30 });
		clip('beam_chest', len, false, [
			[0, R],
			[fire - 0.1, rear, 'easeoutquad'],
			[fire, pose({ ...rear, torso: -4 })],
			[0.9, pose({ ...rear, torso: -6 })],
			[len, R, 'easeinoutsine'],
		]);
	}
	{ // palm blast: the right arm swings straight at the target
		const [len, fire] = T('beam_hand', 20, 10);
		const aim = pose({ right_arm: -88, right_forearm: 0, torso: [3, -12, 0], head: [-3, 10, 0] });
		clip('beam_hand', len, false, [
			[0, R],
			[fire - 0.15, aim, 'easeoutquad'],
			[fire, pose({ ...aim, right_arm: -84 })],
			[0.75, aim],
			[len, R, 'easeinoutsine'],
		]);
	}
	{ // grab and slam: both hands reach, lift the victim overhead, hold, then drive it into the ground
		const [len, grab] = T('grab', 36, 8);
		T("grab_slam", 36, 28);
		const reach = pose({ right_arm: -82, left_arm: -82, right_forearm: -8, left_forearm: -8, torso: 12, root: [0, -1, 0] });
		const lift = pose({ right_arm: -160, left_arm: -160, right_forearm: -10, left_forearm: -10, torso: -8, head: -18 });
		const slam = pose({ right_arm: -40, left_arm: -40, right_forearm: -5, left_forearm: -5, torso: 32, head: 6, root: [0, -3, 0], right_leg: -18, left_leg: 12 });
		clip('grab', len, false, [
			[0, R],
			[grab, reach, 'easeoutquad'],
			[0.8, lift, 'easeinoutsine'],
			[1.3, pose({ ...lift, torso: -10 })],
			[1.4, slam, 'easeinquart'],
			[1.55, slam],
			[len, R, 'easeinoutsine'],
		]);
	}
	{ // drone deploy: hunches forward and the back pack spits out scouts
		const [len, release] = T('deploy', 30, 16);
		const hunch = pose({ torso: 26, head: -20, right_arm: 30, left_arm: 30, right_forearm: -40, left_forearm: -40, root: [0, -1.5, 0] });
		clip('deploy', len, false, [
			[0, R],
			[0.6, hunch, 'easeoutquad'],
			[release, pose({ ...hunch, torso: 32 })],
			[1.1, hunch],
			[len, R, 'easeinoutsine'],
		]);
	}
	{ // death: sparks, buckles to its knees, then crashes forward
		const [len] = T('death', 60, 0);
		const knees = pose({ root: [0, -12, 0], right_leg: -80, left_leg: -76, torso: 24, head: 22, right_arm: 14, left_arm: 18, right_forearm: -20, left_forearm: -20 });
		clip('death', len, 'hold', [
			[0, R],
			[0.4, pose({ torso: -12, head: -24, right_arm: -30, left_arm: 20 }), 'easeoutquad'],
			[1.1, knees, 'easeinquad'],
			[1.9, pose({ ...knees, torso: 34, head: 30 })],
			[2.4, pose({ ...knees, root: [0, -14, -8], torso: 78, head: 20, right_arm: -60, left_arm: -60 }), 'easeinquad'],
			[len, pose({ ...knees, root: [0, -14, -8], torso: 80, head: 20, right_arm: -62, left_arm: -62 })],
		]);
	}
	write();
}

// ================================================================ 2. the Sentinel Drone (real size, ~0.9 blocks)
const drone = buildModel({
	name: 'sentinel_drone', S: 0.5, TW: 64, bounds: [1.5, 1.5],
	build(bone, cube) {
		bone('root', null, [0, 0, 0]);
		const body = bone('body', 'root', [0, 12, 0]);
		cube(body, [-8, 5, -8], [16, 12, 16], 'armour');             // the pod
		cube(body, [-9, 9, -9], [18, 3, 18], 'trim');                 // magenta band
		cube(body, [-6, 6.5, -8.8], [12, 9, 0.8], 'face');            // the Sentinel mask
		cube(body, [-2, 9, -9.6], [4, 3, 0.8], 'lens');               // the burning eye-lens
		cube(body, [-3, 4, -3], [6, 1, 6], 'emitter');                // the laser under its chin
		cube(body, [-5, 17, -5], [10, 2, 10], 'metal');               // top cap
		const antenna = bone('antenna', 'body', [0, 19, 2]);
		cube(antenna, [-0.75, 19, 1.25], [1.5, 6, 1.5], 'metal');
		cube(antenna, [-1.25, 25, 0.75], [2.5, 2, 2.5], 'eye');       // beacon tip
		for (const s of [1, -1]) {
			const { x, lo, n } = mirror(s);
			const pod = bone(`${n}_pod`, 'body', [x(10), 11, 0]);
			cube(pod, [lo(9, 4), 8, -4], [4, 6, 8], 'metal');         // side thruster pods
			cube(pod, [lo(9.5, 3), 8.5, 4], [3, 5, 1], 'emitter');    // their glowing exhausts (back)
		}
	},
});
{
	const REST = { root: [0, 0, 0], body: [0, 0, 0], antenna: [0, 0, 0], right_pod: [0, 0, 0], left_pod: [0, 0, 0] };
	const { pose, clip, T, write, R } = animator(drone, REST, 'sentinel_drone', 'sentinel_drone');
	clip('idle', 1.6, true, [
		[0, R],
		[0.8, pose({ root: [0, 1.5, 0], body: [4, 0, 3], antenna: -10, right_pod: 12, left_pod: -12 }), 'easeinoutsine'],
		[1.6, R, 'easeinoutsine'],
	]);
	clip('fly', 0.8, true, [
		[0, pose({ body: 14, right_pod: 30, left_pod: 30, antenna: 20 })],
		[0.4, pose({ body: 12, right_pod: 34, left_pod: 34, antenna: 25, root: [0, 0.8, 0] }), 'easeinoutsine'],
		[0.8, pose({ body: 14, right_pod: 30, left_pod: 30, antenna: 20 }), 'easeinoutsine'],
	]);
	{
		const [len, fire] = T('shoot', 12, 4);
		clip('shoot', len, false, [
			[0, R],
			[fire, pose({ body: -12, antenna: 14 }), 'easeoutquad'],
			[len, R, 'easeinoutsine'],
		]);
	}
	{
		const [len] = T('death', 20, 0);
		clip('death', len, 'hold', [
			[0, R],
			[0.5, pose({ body: [40, 120, 30], antenna: 40, root: [0, -4, 0] }), 'easeinquad'],
			[len, pose({ body: [80, 260, 60], antenna: 60, right_pod: 60, left_pod: -60, root: [0, -12, 0] }), 'easeinquad'],
		]);
	}
	write();
}

// ================================================================ 3. Master Mold (64 du = 2 blocks; SCALE 5.5 in game)
const mold = buildModel({
	name: 'master_mold', S: 0.5, TW: 256, bounds: [3, 3],
	build(bone, cube) {
		bone('root', null, [0, 0, 0]);
		for (const s of [1, -1]) {
			const { x, lo, n } = mirror(s);
			const leg = bone(`${n}_leg`, 'root', [x(6.5), 22, 0]);
			cube(leg, [lo(2, 9), 13, -4], [9, 10, 8], 'armour');      // thigh
			cube(leg, [lo(1.5, 10), 11, -5], [10, 3, 9], 'metal');    // knee
			cube(leg, [lo(2, 9), 3, -4], [9, 9, 8], 'armour');        // shin
			cube(leg, [lo(3, 7), 4.5, -4.6], [7, 6, 0.8], 'trim');    // shin stripe
			cube(leg, [lo(1, 11), 0, -7], [11, 4, 12], 'boot');
		}
		const hips = bone('hips', 'root', [0, 22, 0]);
		cube(hips, [-11, 20, -5], [22, 5, 10], 'metal');              // pelvis
		cube(hips, [-9, 15, -5.8], [7, 6, 1], 'trim');                // skirt plates
		cube(hips, [2, 15, -5.8], [7, 6, 1], 'trim');
		const torso = bone('torso', 'hips', [0, 25, 0]);
		cube(torso, [-8, 25, -4.5], [16, 6, 9], 'metal');             // abdomen
		torso.cubes.push({ from: [-15, 31, -7], size: [30, 16, 14], mat: 'armour', chest: true }); // the great chest
		cube(torso, [-10, 32, 7], [20, 13, 5], 'vent');               // the factory stack on its back
		cube(torso, [-10, 46, -3], [20, 4, 9], 'trim');               // collar
		cube(torso, [-12, 45, -4], [2, 7, 9], 'trim');                // collar wings
		cube(torso, [10, 45, -4], [2, 7, 9], 'trim');
		cube(torso, [-9, 45, 7], [3, 10, 3], 'metal');                // back spires
		cube(torso, [6, 45, 7], [3, 10, 3], 'metal');
		cube(torso, [-9.5, 55, 6.5], [4, 2, 4], 'eye');               // their beacons
		cube(torso, [5.5, 55, 6.5], [4, 2, 4], 'eye');
		const core = bone('core', 'torso', [0, 38, -7]);
		cube(core, [-6, 33, -7.6], [12, 10, 0.6], 'core');            // the fabrication core behind the hatch
		const hatch = bone('hatch', 'torso', [0, 43.5, -8]);
		cube(hatch, [-7, 32.5, -8.8], [14, 11, 1.2], 'trim');         // the hangar hatch (hinged at the top)
		cube(hatch, [-2, 36, -9.2], [4, 4, 0.4], 'emitter');          // its sensor eye
		const head = bone('head', 'torso', [0, 47, 0]);
		cube(head, [-5, 47, -5.5], [10, 11, 10], 'helm');
		cube(head, [-4, 47.5, -6.3], [8, 8, 0.8], 'face');
		cube(head, [-6, 56, -6], [12, 3, 11], 'trim');                // domed crest
		cube(head, [-1, 59, -5], [2, 4, 10], 'trim');                 // crest fin
		for (const s of [1, -1]) {
			const { x, lo, n } = mirror(s);
			const arm = bone(`${n}_arm`, 'torso', [x(17), 44, 0]);
			cube(arm, [lo(13, 10), 40, -6], [10, 8, 12], 'armour');   // pauldron
			cube(arm, [lo(13.5, 11), 39, -6.5], [11, 2, 13], 'trim');
			cube(arm, [lo(15, 4), 48, -2], [4, 5, 4], 'metal');       // shoulder pylon
			cube(arm, [lo(15.5, 5), 53, -2.5], [5, 1.5, 5], 'emitter');
			cube(arm, [lo(14, 7), 28, -3.5], [7, 12, 7], 'metal');    // upper arm
			const fore = bone(`${n}_forearm`, `${n}_arm`, [x(17.5), 29, 0]);
			cube(fore, [lo(13.5, 8), 16, -4], [8, 13, 8], 'armour');
			cube(fore, [lo(13, 9), 25, -4.5], [9, 3, 9], 'trim');
			cube(fore, [lo(14, 7), 10, -3.5], [7, 6, 7], 'metal');    // hand
			cube(fore, [lo(14.5, 6), 9.4, -3], [6, 0.6, 6], 'emitter'); // palm emitter
		}
	},
});
{
	const REST = {
		root: [0, 0, 0], hips: [0, 0, 0], torso: [4, 0, 0], head: [-4, 0, 0], hatch: [0, 0, 0],
		right_arm: [-6, 0, 0], right_forearm: [-14, 0, 0], left_arm: [-6, 0, 0], left_forearm: [-14, 0, 0],
		right_leg: [0, 0, 0], left_leg: [0, 0, 0], core_s: [1, 1, 1],
	};
	const { pose, clip, T, write, R } = animator(mold, REST, 'master_mold', 'master_mold', { core_s: 'core' });
	clip('idle', 4.0, true, [
		[0, R],
		[2.0, pose({ torso: 6, head: [-6, 8, 0], right_arm: -4, left_arm: -4, core_s: 1.06, root: [0, -0.4, 0] }), 'easeinoutsine'],
		[4.0, R, 'easeinoutsine'],
	]);
	const st = 18;
	clip('walk', 2.4, true, [
		[0, pose({ right_leg: -st, left_leg: st, right_arm: 10, left_arm: -16, torso: 7, root: [0, -1, 0] })],
		[0.6, pose({ torso: 6, root: [0, 0.5, 0] })],
		[1.2, pose({ right_leg: st, left_leg: -st, right_arm: -16, left_arm: 10, torso: 7, root: [0, -1, 0] })],
		[1.8, pose({ torso: 6, root: [0, 0.5, 0] })],
		[2.4, pose({ right_leg: -st, left_leg: st, right_arm: 10, left_arm: -16, torso: 7, root: [0, -1, 0] })],
	]);
	{ // Stomp: the right foot rises high, then crashes down in a shockwave
		const [len, hit] = T('stomp', 34, 20);
		const up = pose({ right_leg: -62, left_leg: 6, torso: -6, head: -8, right_arm: 20, left_arm: -30, root: [0, 2, 0] });
		const down = pose({ right_leg: -4, left_leg: 4, torso: 16, head: 6, right_arm: 10, left_arm: 10, root: [0, -2.5, 0] });
		clip('stomp', len, false, [[0, R], [0.75, up, 'easeoutquad'], [hit, down, 'easeinquart'], [1.3, down], [len, R, 'easeinoutsine']]);
	}
	{ // Sweep: the right arm swings flat across everything in front
		const [len, hit] = T('sweep', 30, 16);
		const wind = pose({ torso: [2, 50, 0], head: [-4, -25, 0], right_arm: -85, right_forearm: -5, left_arm: -20, root: [0, -1, 0] });
		const through = pose({ torso: [8, -45, 0], head: [-4, 25, 0], right_arm: -88, right_forearm: 0, left_arm: 15, root: [0, -1.5, 0] });
		clip('sweep', len, false, [[0, R], [0.55, wind, 'easeoutquad'], [hit, through, 'easeinquart'], [1.05, through], [len, R, 'easeinoutsine']]);
	}
	{ // the Purge Beam: braces, the head lowers to the target, the core flares; fires from 30 to 70 ticks
		const [len, fire] = T('beam', 80, 30);
		T("beam_end", 80, 70);
		const brace = pose({ torso: -6, head: 6, right_arm: 24, left_arm: 24, right_forearm: -40, left_forearm: -40, right_leg: -10, left_leg: 10, core_s: 1.25, root: [0, -1.5, 0] });
		clip('beam', len, false, [
			[0, R],
			[fire - 0.2, brace, 'easeoutquad'],
			[fire, pose({ ...brace, torso: -9, core_s: 1.5 })],
			[2.4, pose({ ...brace, torso: [-9, 6, 0], core_s: 1.4 })],
			[3.0, pose({ ...brace, torso: [-9, -6, 0], core_s: 1.5 })],
			[3.5, pose({ ...brace, torso: -9, core_s: 1.4 })],
			[len, R, 'easeinoutsine'],
		]);
	}
	{ // Fabricate: the chest hatch swings up and a freshly built Sentinel launches out
		const [len, launch] = T('deploy', 44, 24);
		const open = pose({ hatch: -115, torso: -4, head: -10, right_arm: 15, left_arm: 15, right_forearm: -30, left_forearm: -30, core_s: 1.4 });
		clip('deploy', len, false, [
			[0, R],
			[0.6, open, 'easeoutquad'],
			[launch, pose({ ...open, torso: -8, core_s: 1.7 })],
			[1.6, open],
			[len, R, 'easeinoutsine'],
		]);
	}
	{ // the phase roar
		const [len, peak] = T('roar', 40, 16);
		const rear = pose({ torso: -22, head: -26, right_arm: -110, left_arm: -110, right_forearm: -10, left_forearm: -10, core_s: 1.5, hatch: -30, root: [0, 1, 0] });
		clip('roar', len, false, [
			[0, R], [0.6, rear, 'easeoutquad'], [peak, pose({ ...rear, torso: -26, head: -30, core_s: 1.8 })],
			[1.3, pose({ ...rear, head: [-30, 8, 0] })], [1.5, pose({ ...rear, head: [-30, -8, 0] })], [len, R, 'easeinoutsine'],
		]);
	}
	{ // death: shudders, the hatch blows open, sinks to its knees and slumps forward
		const [len] = T('death', 100, 0);
		const knees = pose({ root: [0, -9, 0], right_leg: -80, left_leg: -78, torso: 24, head: 18, hatch: -80, right_arm: 10, left_arm: 14, core_s: 0.6 });
		clip('death', len, 'hold', [
			[0, R],
			[0.4, pose({ torso: [-10, 8, 0], head: -20, hatch: -60, core_s: 1.8 }), 'easeoutquad'],
			[0.8, pose({ torso: [-6, -8, 0], head: -14, hatch: -90, core_s: 1.6 })],
			[1.8, knees, 'easeinquad'],
			[3.2, pose({ ...knees, torso: 40, head: 30, core_s: 0.3 })],
			[4.0, pose({ ...knees, torso: 58, head: 30, right_arm: -30, left_arm: -30, core_s: 0.05 }), 'easeinquad'],
			[len, pose({ ...knees, torso: 58, head: 30, right_arm: -30, left_arm: -30, core_s: 0.01 })],
		]);
	}
	write();
}

// ================================================================ item textures (16x16)
function item(name, painter) {
	const px = Buffer.alloc(16 * 16 * 4);
	const put = (x, y, c, a = 255) => { if (x < 0 || y < 0 || x > 15 || y > 15) return; const i = (y * 16 + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = a; };
	painter(put);
	fs.mkdirSync(path.join(A, 'textures/item'), { recursive: true });
	fs.writeFileSync(path.join(A, `textures/item/${name}.png`), encode(16, 16, px));
	json(path.join(A, `models/item/${name}.json`), { parent: 'minecraft:item/generated', textures: { layer0: `projecthero:item/${name}` } }, 2);
}
// The Trask Signal: a handheld Trask Industries mutant tracker -- grey casing, purple band, a red scan light, an antenna
item('trask_signal', put => {
	for (let y = 4; y <= 14; y++) for (let x = 4; x <= 11; x++) {
		const edge = x === 4 || x === 11 || y === 4 || y === 14;
		put(x, y, edge ? GUN_DARK : mix(SILVER_DARK, GUN, (y - 4) / 14));
	}
	for (let x = 5; x <= 10; x++) { put(x, 11, PURPLE); put(x, 12, PURPLE_DARK); }
	for (let y = 6; y <= 9; y++) for (let x = 6; x <= 9; x++) put(x, y, (x + y) % 2 ? [40, 60, 40] : [60, 110, 60]); // scope screen
	put(7, 7, LENS); put(8, 8, LENS_HOT);
	for (let y = 0; y <= 3; y++) put(10, y, GUN);
	put(10, 0, LENS); put(11, 0, LENS_HOT);
	put(6, 13, MAGENTA); put(9, 13, MAGENTA);
});
// Sentinel Circuitry: a salvaged board -- dark green with copper traces and a purple processor
item('sentinel_circuitry', put => {
	for (let y = 3; y <= 12; y++) for (let x = 2; x <= 13; x++) {
		const edge = x === 2 || x === 13 || y === 3 || y === 12;
		put(x, y, edge ? [24, 52, 34] : [36, 86, 52]);
	}
	for (let x = 3; x <= 12; x += 3) for (let y = 4; y <= 11; y++) if ((y + x) % 3 !== 0) put(x, y, [200, 130, 60]);
	for (let y = 6; y <= 9; y++) for (let x = 6; x <= 9; x++) put(x, y, (y === 6 || y === 9 || x === 6 || x === 9) ? PURPLE_DARK : PURPLE);
	put(7, 7, EMIT); put(8, 8, EMIT_HOT);
	for (let x = 4; x <= 11; x += 2) { put(x, 2, SILVER_DARK); put(x, 13, SILVER_DARK); }
});
// The Master Mold Core: a glowing magenta fabrication heart in a purple housing
item('master_mold_core', put => {
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const d = Math.hypot(x - 7.5, y - 7.5);
		if (d < 3.2) put(x, y, mix(EMIT_HOT, EMIT, d / 3.2));
		else if (d < 4.6) put(x, y, MAGENTA);
		else if (d < 6.6) put(x, y, (Math.round(Math.atan2(y - 7.5, x - 7.5) * 4) % 2) ? PURPLE : PURPLE_DARK);
		else if (d < 7.4) put(x, y, GUN_DARK);
	}
	put(6, 6, [255, 255, 255]);
});
for (const v of ['sentinel', 'sentinel_drone', 'master_mold']) json(path.join(A, `models/item/${v}_spawn_egg.json`), { parent: 'minecraft:item/template_spawn_egg' }, 2);
console.log('items: trask_signal, sentinel_circuitry, master_mold_core + 3 spawn eggs');
