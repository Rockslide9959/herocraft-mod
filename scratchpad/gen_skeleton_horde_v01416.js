// v0.14.16 Skeleton Horde: every asset of the rebuilt Bone Tyrant and the six new horde skeletons.
//   - geo/bone_tyrant.geo.json            (a 7-block skeletal lich-king: crown, see-through ribcage round a soul core,
//                                          spiked pauldrons, tattered cape, a bone greatsword; authored 2 blocks tall
//                                          -- the entity's SCALE attribute (3.5) makes him ~7)
//   - animations/bone_tyrant.animation.json (idle, walk, every attack's clip, the phase roar, death)
//   - textures/entity/bone_tyrant.png + bone_tyrant_glowmask.png
//   - textures/entity/horde_skeleton/<variant>.png (recoloured vanilla skeleton)
//   - spawn-egg item models, the undead / smite entity tags
// Usage (from the repo root): node scratchpad/gen_skeleton_horde_v01416.js <dir holding assets/minecraft/textures/entity/...>
// Every clip's length and contact time is printed and checked against BoneTyrantCombat's tick constants (TIMING below).
const fs = require('fs');
const path = require('path');
const { decode, encode } = require('./pngkit');

const VANILLA = process.argv[2];
if (!VANILLA) throw new Error('usage: node scratchpad/gen_skeleton_horde_v01416.js <extracted client jar dir>');
const RES = path.join(__dirname, '..', 'src/main/resources');
const A = path.join(RES, 'assets/projecthero');
const D = path.join(RES, 'data');
const json = (p, obj, indent = 1) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, JSON.stringify(obj, null, indent) + '\n'); };

let seed = 424242;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const mix = (a, b, t) => a.map((v, i) => clamp(v + (b[i] - v) * t));

// ================================================================ the model (design units: 64 du ~ the full height)
// Emitted at S model pixels per du: 64 du -> 32 px -> 2 blocks, times SCALE 3.5 = ~7 blocks in game.
const S = 0.5;
const bones = [];
const bone = (name, parent, pivot) => { const b = { name, parent, pivot, cubes: [] }; bones.push(b); return b; };
const cube = (b, from, size, mat) => { b.cubes.push({ from, size, mat }); };

const root = bone('root', null, [0, 0, 0]);
// legs hang off the root, the hips/torso stack is separate so the torso can lean without dragging the legs
for (const side of [1, -1]) {
	const n = side > 0 ? 'right' : 'left';
	const x = v => side > 0 ? v : -v; // mirror an x coordinate
	const leg = bone(`${n}_leg`, 'root', [x(4.5), 25, 0]);
	const lo = (a, w) => side > 0 ? a : -a - w; // mirror a cube's min x for width w
	cube(leg, [lo(3, 3), 13, -1.5], [3, 12, 3], 'bone');          // femur
	cube(leg, [lo(2.5, 4), 11, -2.5], [4, 3, 4], 'armour');       // knee cop
	cube(leg, [lo(3, 3), 2, -1.5], [3, 9, 3], 'bone');            // shin
	cube(leg, [lo(2.3, 4.4), 3, -2.6], [4.4, 7, 1], 'armour');    // greave
	cube(leg, [lo(2, 5), 0, -5], [5, 2, 7], 'bone');              // foot
}
const hips = bone('hips', 'root', [0, 26, 0]);
cube(hips, [-7, 23, -3.5], [14, 4, 7], 'bone');                  // pelvis
cube(hips, [-7.5, 26, -4], [15, 2, 8], 'gold');                  // belt
cube(hips, [-4.5, 9, -4.3], [9, 15, 0.6], 'cloth');              // tabard, front
cube(hips, [-5, 11, 3.7], [10, 13, 0.6], 'cloth');               // tabard, back
const torso = bone('torso', 'hips', [0, 28, 0]);
cube(torso, [-1.5, 28, -1], [3, 8, 3], 'bone');                  // spine
cube(torso, [-7, 35, -4.5], [14, 13, 9], 'ribs');                // the see-through ribcage
cube(torso, [-1, 36, -4.9], [2, 11, 0.8], 'bone');               // sternum
cube(torso, [-6.5, 47, -4], [13, 3, 8], 'armour');               // gorget
cube(torso, [-1.5, 49, -1.5], [3, 2, 3], 'bone');                // neck
const core = bone('core', 'torso', [0, 40.5, 0]);
cube(core, [-2.5, 38, -2.5], [5, 5, 5], 'core');                 // the soul core inside the ribs
const head = bone('head', 'torso', [0, 50, 0]);
cube(head, [-4.5, 51, -5], [9, 9, 9], 'skull');
cube(head, [-5, 59, -5.5], [10, 2, 10], 'gold');                 // crown band
cube(head, [-0.75, 61, -5.6], [1.5, 5, 1], 'gold');              // crown points: front
cube(head, [-4.4, 61, -5.6], [1.5, 3.5, 1], 'gold');
cube(head, [2.9, 61, -5.6], [1.5, 3.5, 1], 'gold');
cube(head, [-5.1, 61, -1], [1, 4, 2], 'gold');                   // sides
cube(head, [4.1, 61, -1], [1, 4, 2], 'gold');
cube(head, [-0.75, 61, 4.6], [1.5, 4, 1], 'gold');               // back
cube(head, [-1, 59.3, -5.9], [2, 1.4, 0.4], 'gem');              // the crown's soul gem
const jaw = bone('jaw', 'head', [0, 51, 2]);
cube(jaw, [-3.5, 48.5, -5.4], [7, 3, 7], 'jaw');
for (const side of [1, -1]) {
	const n = side > 0 ? 'right' : 'left';
	const lo = (a, w) => side > 0 ? a : -a - w;
	const x = v => side > 0 ? v : -v;
	const arm = bone(`${n}_arm`, 'torso', [x(9), 46, 0]);
	cube(arm, [lo(6.5, 9), 44, -5], [9, 6, 10], 'armour');      // pauldron
	cube(arm, [lo(9, 3), 50, -2], [3, 4, 2], 'bone');            // pauldron spike
	cube(arm, [lo(12, 2), 49, -1], [2, 3, 2], 'bone');           // second spike
	cube(arm, [lo(8, 3), 34, -1.5], [3, 11, 3], 'bone');         // upper arm
	const fore = bone(`${n}_forearm`, `${n}_arm`, [x(9.5), 34, 0]);
	cube(fore, [lo(8, 3), 23, -1.5], [3, 11, 3], 'bone');        // forearm
	cube(fore, [lo(7.3, 4.4), 20, -2.3], [4.4, 6, 4.6], 'armour'); // gauntlet
	cube(fore, [lo(7.5, 4), 17, -2], [4, 3, 4], 'bone');         // hand
	if (side < 0) {
		for (let i = 0; i < 3; i++) cube(fore, [-11.4 + i * 1.4, 14.5, -2.2], [1, 2.5, 1], 'bone'); // claws
		const flame = bone('soul_flame', 'left_forearm', [-9.5, 15.5, 0]);
		cube(flame, [-11, 12.5, -1.5], [3, 3, 3], 'flame');      // a soul flame cupped in the claw
	}
}
const sword = bone('sword', 'right_forearm', [9.5, 18.5, 0]);   // held in the right fist, blade out along -Z
cube(sword, [8.75, 17.5, -5], [1.5, 2, 8], 'grip');
cube(sword, [8.25, 17, 3], [2.5, 3, 2], 'gold');                 // pommel
cube(sword, [6, 16, -7], [7, 4, 2], 'gold');                     // crossguard
cube(sword, [9, 15, -37], [1, 5, 30], 'blade');
cube(sword, [9, 16, -40], [1, 3, 3], 'blade');                   // tip
const cape = bone('cape', 'torso', [0, 48, 4.5]);
cube(cape, [-7, 14, 4.6], [14, 34, 0.6], 'capecloth');

// ---------------------------------------------------------------- texture atlas: one rect per face, 1 texel per du
const FACES = ['north', 'south', 'east', 'west', 'up', 'down'];
const faceDims = (s, f) => (f === 'north' || f === 'south') ? [s[0], s[1]] : (f === 'east' || f === 'west') ? [s[2], s[1]] : [s[0], s[2]];
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
const TW = 128;
{
	const order = [...rects].sort((a, b) => b.h - a.h || b.w - a.w);
	let x = 0, y = 0, rowH = 0;
	for (const r of order) {
		if (x + r.w > TW) { x = 0; y += rowH; rowH = 0; }
		r.u = x; r.v = y; x += r.w; rowH = Math.max(rowH, r.h);
	}
	var TH = Math.ceil((y + rowH) / 8) * 8;
}
const tex = Buffer.alloc(TW * TH * 4);
const glow = Buffer.alloc(TW * TH * 4);
const put = (buf, x, y, c, a = 255) => { const i = (y * TW + x) * 4; buf[i] = c[0]; buf[i + 1] = c[1]; buf[i + 2] = c[2]; buf[i + 3] = a; };

const BONE = [226, 216, 190], BONE_DARK = [150, 138, 112];
const IRON = [54, 52, 62], IRON_DARK = [28, 27, 34], GOLD = [214, 172, 58], GOLD_DARK = [140, 98, 26];
const CLOTH = [64, 24, 78], CLOTH_DARK = [30, 10, 40];
const SOUL = [96, 228, 255], SOUL_HOT = [220, 255, 255];
function paintFace(r) {
	const { c, f, w, h, u, v } = r;
	const at = (x, y) => [u + x, v + y];
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		const [px, py] = at(x, y);
		const edge = x === 0 || y === 0 || x === w - 1 || y === h - 1;
		let col = null, alpha = 255, g = null;
		switch (c.mat) {
			case 'bone': case 'jaw': case 'skull':
				col = mix(BONE, BONE_DARK, rnd() * 0.35 + (edge ? 0.25 : 0));
				if (rnd() < 0.05) col = mix(col, [90, 80, 64], 0.6); // cracks
				break;
			case 'armour':
				col = mix(IRON, IRON_DARK, rnd() * 0.5);
				if (edge) col = mix(GOLD, GOLD_DARK, rnd() * 0.4);   // gold trim
				else if (rnd() < 0.04) col = [110, 104, 120];        // scratches
				break;
			case 'gold':
				col = mix(GOLD, GOLD_DARK, rnd() * 0.45 + (edge ? 0.2 : 0));
				if (rnd() < 0.06) col = [255, 236, 150];
				break;
			case 'grip':
				col = (y + x) % 3 === 0 ? [40, 26, 18] : [86, 54, 30];
				break;
			case 'cloth': case 'capecloth': {
				col = mix(CLOTH, CLOTH_DARK, rnd() * 0.5);
				if ((f === 'north' || f === 'south' || f === 'east' || f === 'west')) {
					// a frayed, torn hem: the last rows are ragged holes
					const ragged = c.mat === 'capecloth' ? 6 : 3;
					if (y >= h - ragged && rnd() < (y - (h - ragged) + 1) / (ragged + 1)) alpha = 0;
					if (c.mat === 'capecloth' && (f === 'north' || f === 'south') && y < h - 8 && rnd() < 0.015) alpha = 0; // moth holes
					if (y === 1 && c.mat === 'capecloth') col = mix(GOLD, GOLD_DARK, 0.3);
				}
				break;
			}
			case 'ribs': {
				// horizontal ribs with gaps you can see the core through; the top/bottom are open rings
				if (f === 'up' || f === 'down') {
					if (!edge) alpha = 0;
					col = mix(BONE, BONE_DARK, rnd() * 0.4);
				} else {
					const band = y % 3;
					const spineCol = (f === 'south' && (x === Math.floor(w / 2) || x === Math.floor(w / 2) - 1));
					if (band === 2 && !spineCol && y < h - 1) alpha = 0;
					col = mix(BONE, BONE_DARK, rnd() * 0.3 + (band === 1 ? 0.25 : 0));
				}
				break;
			}
			case 'core': {
				const cx = (w - 1) / 2, cy = (h - 1) / 2;
				const d = Math.hypot(x - cx, y - cy) / Math.max(1, cx);
				col = mix(SOUL_HOT, SOUL, Math.min(1, d));
				g = col;
				break;
			}
			case 'gem': col = SOUL_HOT; g = SOUL_HOT; break;
			case 'flame': {
				col = mix(SOUL_HOT, SOUL, rnd() * 0.7);
				g = col;
				break;
			}
			case 'blade': {
				col = mix([212, 210, 198], [150, 146, 134], rnd() * 0.35);
				if ((f === 'east' || f === 'west') && h >= 5) {
					if (y === 0 || y === h - 1) col = [244, 242, 232]; // honed edges
					if (y === Math.floor(h / 2)) {                     // the fuller, inlaid with soul runes
						col = [70, 66, 70];
						if (x % 4 === 1 || x % 4 === 2) { col = SOUL; g = SOUL; }
					}
				}
				break;
			}
		}
		put(tex, px, py, col, alpha);
		if (g && alpha) put(glow, px, py, g);
	}
	// the skull's face and the jaw's teeth (north = the front face)
	if (c.mat === 'skull' && f === 'north') {
		const dark = [24, 20, 22];
		for (const [x0, x1] of [[1, 3], [5, 7]]) for (let y = 3; y <= 5; y++) for (let x = x0; x <= x1; x++) put(tex, ...at(x, y), dark);
		for (const ex of [2, 6]) { put(tex, ...at(ex, 4), SOUL_HOT); put(glow, ...at(ex, 4), SOUL_HOT); put(tex, ...at(ex, 5), SOUL); put(glow, ...at(ex, 5), SOUL); }
		put(tex, ...at(4, 6), dark); put(tex, ...at(4, 7), dark);                 // nose
		for (let x = 1; x <= 7; x++) put(tex, ...at(x, 8), x % 2 ? [240, 234, 214] : dark); // upper teeth
		for (let y = 0; y < 2; y++) for (let x = 0; x < 9; x++) if (rnd() < 0.3) put(tex, ...at(x, y), BONE_DARK);
	}
	if (c.mat === 'jaw' && f === 'north') {
		for (let x = 1; x <= 5; x++) put(tex, ...at(x, 0), x % 2 ? [240, 234, 214] : [24, 20, 22]);
	}
}
for (const r of rects) paintFace(r);

// ---------------------------------------------------------------- geo json
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
// sanity: every uv rect inside the texture, no overlaps, every parent declared earlier
{
	const seen = new Set();
	for (const b of bones) {
		if (b.parent && !seen.has(b.parent)) throw new Error(`bone ${b.name}: parent ${b.parent} not declared first`);
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
}
json(path.join(A, 'geo/bone_tyrant.geo.json'), {
	format_version: '1.12.0',
	'minecraft:geometry': [{
		description: {
			identifier: 'geometry.bone_tyrant', texture_width: TW, texture_height: TH,
			visible_bounds_width: 4, visible_bounds_height: 4, visible_bounds_offset: [0, 1.5, 0],
		},
		bones: geoBones,
	}],
});
fs.mkdirSync(path.join(A, 'textures/entity/horde_skeleton'), { recursive: true });
fs.writeFileSync(path.join(A, 'textures/entity/bone_tyrant.png'), encode(TW, TH, tex));
fs.writeFileSync(path.join(A, 'textures/entity/bone_tyrant_glowmask.png'), encode(TW, TH, glow));
console.log(`geo: ${bones.length} bones, ${bones.reduce((n, b) => n + b.cubes.length, 0)} cubes, texture ${TW}x${TH}`);

// ================================================================ animations
// Convention (as every clip in this mod): positive X tips a bone's top forward, so for anything hanging from its
// pivot (legs, arms) a NEGATIVE angle swings the free end forward; the sword points along -Z, so a positive angle
// dips its point. Root positions are in du here (converted to model px on output).
const REST = {
	root: [0, 0, 0], hips: [0, 0, 0], torso: [6, 0, 0], head: [-6, 0, 0], jaw: [0, 0, 0],
	right_arm: [8, 0, 0], right_forearm: [-12, 0, 0], sword: [152, 0, 0],
	left_arm: [-4, 0, 0], left_forearm: [-35, 0, 0], cape: [4, 0, 0],
	right_leg: [0, 0, 0], left_leg: [0, 0, 0],
	core_s: [1, 1, 1], flame_s: [1, 1, 1],
};
const BONE_TRACKS = Object.keys(REST);
const pose = (o = {}) => {
	const p = {};
	for (const k of BONE_TRACKS) p[k] = o[k] !== undefined ? (typeof o[k] === 'number' ? [o[k], 0, 0] : o[k]) : REST[k];
	if (typeof o.core_s === 'number') p.core_s = [o.core_s, o.core_s, o.core_s];
	if (typeof o.flame_s === 'number') p.flame_s = [o.flame_s, o.flame_s, o.flame_s];
	return p;
};
const R = pose();

// --- 2D forward kinematics (y/z plane, X rotations only) to keep the blade and feet out of the floor
const boneByName = Object.fromEntries(bones.map(b => [b.name, b]));
function worldPoint(p, boneName, pt) {
	let y = pt[1], z = pt[2];
	let b = boneByName[boneName];
	while (b) {
		const key = b.name === 'root' ? null : b.name;
		const ang = key && p[key] ? p[key][0] * Math.PI / 180 : 0;
		const dy = y - b.pivot[1], dz = z - b.pivot[2];
		y = b.pivot[1] + dy * Math.cos(ang) + dz * Math.sin(ang);
		z = b.pivot[2] + dz * Math.cos(ang) - dy * Math.sin(ang);
		b = b.parent ? boneByName[b.parent] : null;
	}
	return [y + p.root[1], z + p.root[2]];
}
const swordTip = p => worldPoint(p, 'sword', [9.5, 17.5, -40]);
const pommel = p => worldPoint(p, 'sword', [9.5, 18.5, 5]);

function kf(entries) {
	const out = {};
	for (const [t, v, easing] of entries) {
		const r = v.map(n => Math.round(n * 1000) / 1000);
		out[t.toFixed(2)] = easing ? { vector: r, easing } : r;
	}
	return out;
}
const problems = [];
function clip(name, length, loop, frames) {
	const bonesOut = {};
	for (const track of BONE_TRACKS) {
		const entries = frames.map(([t, p, e]) => [t, track === 'root' ? p.root.map(v => v * S) : p[track], e]);
		if (entries.every(([, v]) => v.every((n, i) => n === entries[0][1][i])) && entries[0][1].every((n, i) => n === (track.endsWith('_s') ? 1 : 0))) continue;
		const boneName = track === 'core_s' ? 'core' : track === 'flame_s' ? 'soul_flame' : track;
		const channel = track === 'root' ? 'position' : track.endsWith('_s') ? 'scale' : 'rotation';
		bonesOut[boneName] = bonesOut[boneName] || {};
		bonesOut[boneName][channel] = kf(entries);
	}
	for (const [t, p] of frames) {
		const tip = swordTip(p), pm = pommel(p);
		if (tip[0] < -5 && !name.startsWith('death') && name !== 'grave_step') problems.push(`${name}@${t}: sword tip ${tip[0].toFixed(1)} du below ground`);
		if (pm[0] < -2 && !name.startsWith('death') && name !== 'grave_step') problems.push(`${name}@${t}: pommel ${pm[0].toFixed(1)} below ground`);
	}
	const a = { animation_length: length, bones: bonesOut };
	if (loop === true) a.loop = true;
	else if (loop === 'hold') a.loop = 'hold_on_last_frame';
	animations[`animation.bone_tyrant.${name}`] = a;
	return a;
}
const animations = {};

// ---- locomotion
clip('idle', 3.0, true, [
	[0, R],
	[1.5, pose({ torso: 3, head: -9, right_arm: 7, left_forearm: -42, cape: 8, core_s: 1.18, flame_s: 1.35, root: [0, 0.4, 0] }), 'easeinoutsine'],
	[3.0, R, 'easeinoutsine'],
]);
const stride = 22, drop = -25 * (1 - Math.cos(stride * Math.PI / 180));
clip('walk', 1.6, true, [
	[0, pose({ right_leg: -stride, left_leg: stride, left_arm: 16, right_arm: 4, torso: 8, cape: 10, root: [0, drop, 0] })],
	[0.4, pose({ torso: 7, cape: 14, root: [0, 0.6, 0] })],
	[0.8, pose({ right_leg: stride, left_leg: -stride, left_arm: -22, right_arm: 14, torso: 8, cape: 10, root: [0, drop, 0] })],
	[1.2, pose({ torso: 7, cape: 14, root: [0, 0.6, 0] })],
	[1.6, pose({ right_leg: -stride, left_leg: stride, left_arm: 16, right_arm: 4, torso: 8, cape: 10, root: [0, drop, 0] })],
]);

// ---- attacks. TIMING mirrors BoneTyrantCombat (ticks): [clip length, contact]
const TIMING = {};
const T = (name, lenTicks, contactTicks) => { TIMING[name] = [lenTicks, contactTicks]; return [lenTicks / 20, contactTicks / 20]; };

{ // Greatsword Sweep: wound right, a flat cut across the front
	const [len, hit] = T('sweep', 34, 16);
	const wind = pose({ torso: [0, 55, 0], head: [-6, -30, 0], right_arm: -80, right_forearm: -10, sword: 90, left_arm: -30, right_leg: -10, left_leg: 8, root: [0, -1, 0] });
	clip('sweep', len, false, [
		[0, R],
		[0.55, wind, 'easeoutquad'],
		[0.7, pose({ ...wind, torso: [0, 62, 0] })],
		[hit, pose({ torso: [8, -45, 0], head: [-6, 30, 0], right_arm: -85, right_forearm: 0, sword: 85, left_arm: 20, right_leg: -14, left_leg: 10, root: [0, -1.5, 0] }), 'easeinquart'],
		[0.95, pose({ torso: [12, -60, 0], head: [-4, 30, 0], right_arm: -60, right_forearm: 0, sword: 80, left_arm: 25, right_leg: -14, left_leg: 10, root: [0, -1.5, 0] }), 'easeoutquad'],
		[len, R, 'easeinoutsine'],
	]);
}
{ // Bone Cleave: overhead, both hands, the blade driven into the ground -- the spikes run out from where it lands
	const [len, hit] = T('cleave', 40, 24);
	const up = pose({ torso: -12, head: -4, right_arm: -165, right_forearm: -10, sword: 30, left_arm: -150, left_forearm: -20, right_leg: -6, left_leg: 10 });
	const down = pose({ torso: 28, head: 0, right_arm: -80, right_forearm: -10, sword: 108, left_arm: -70, left_forearm: -20, right_leg: -25, left_leg: 15, root: [0, -4, 0] });
	clip('cleave', len, false, [
		[0, R],
		[0.9, up, 'easeoutquad'],
		[1.0, pose({ ...up, torso: -15, right_arm: -170 })],
		[hit, down, 'easeinquart'],
		[1.5, down],
		[len, R, 'easeinoutsine'],
	]);
}
{ // Bone Quake: a knee raised high, then a stamp that splits the ground in rings
	const [len, hit] = T('quake', 34, 18);
	clip('quake', len, false, [
		[0, R],
		[0.7, pose({ right_leg: -70, left_leg: 5, torso: -10, head: -10, left_arm: -60, right_arm: 15, sword: 170, root: [0, 4, 0] }), 'easeoutquad'],
		[hit, pose({ right_leg: 0, torso: 18, head: 5, left_arm: 10, right_arm: 10, root: [0, -3, 0] }), 'easeinquart'],
		[1.1, pose({ torso: 14, left_arm: 8, right_arm: 10, root: [0, -2, 0] })],
		[len, R, 'easeinoutsine'],
	]);
}
{ // Bone Volley: the claw drawn back, then flung -- a fan of bone shards
	const [len, hit] = T('volley', 26, 14);
	clip('volley', len, false, [
		[0, R],
		[0.55, pose({ left_arm: -40, left_forearm: -80, torso: [4, -20, 0], flame_s: 2.2 }), 'easeoutquad'],
		[hit, pose({ left_arm: -95, left_forearm: 0, torso: [8, 15, 0], flame_s: 0.6 }), 'easeinquart'],
		[0.85, pose({ left_arm: -92, left_forearm: 0, torso: [8, 15, 0], flame_s: 0.8 })],
		[len, R, 'easeinoutsine'],
	]);
}
{ // Arrow Storm: the claw raised to the sky
	const [len, hit] = T('storm', 30, 12);
	const raised = pose({ left_arm: -175, left_forearm: -5, head: -35, torso: -8, sword: 177, flame_s: 2.8, jaw: 25 });
	clip('storm', len, false, [
		[0, R],
		[0.5, raised, 'easeoutquad'],
		[hit, pose({ ...raised, left_arm: -178, flame_s: 3.5 })],
		[1.0, pose({ ...raised, flame_s: 2.5 })],
		[len, R, 'easeinoutsine'],
	]);
}
{ // Raise the Dead: both arms up, then slammed down -- the dead claw out of the ground
	const [len, hit] = T('summon', 40, 26);
	const up = pose({ left_arm: -165, left_forearm: -10, right_arm: -150, right_forearm: 0, sword: 60, torso: -12, head: -25, jaw: 20, flame_s: 2.5 });
	const down = pose({ torso: 30, left_arm: -60, left_forearm: -20, right_arm: -50, right_forearm: -10, sword: 40, root: [0, -5, 0], right_leg: -20, left_leg: 15, head: 10, flame_s: 1.4 });
	clip('summon', len, false, [
		[0, R],
		[0.9, up, 'easeoutquad'],
		[1.1, pose({ ...up, torso: -14, jaw: 28 })],
		[hit, down, 'easeinquart'],
		[1.6, down],
		[len, R, 'easeinoutsine'],
	]);
}
{ // Bone Cage: the claw reaches for its prey, then clenches
	const [len, hit] = T('cage', 24, 12);
	clip('cage', len, false, [
		[0, R],
		[0.4, pose({ left_arm: -90, left_forearm: -10, flame_s: 2.0, torso: [0, -15, 0], head: [-6, 10, 0] }), 'easeoutquad'],
		[hit, pose({ left_arm: -95, left_forearm: -60, flame_s: 0.3, torso: [4, -5, 0], head: [-6, 5, 0] }), 'easeinquart'],
		[0.8, pose({ left_arm: -92, left_forearm: -55, flame_s: 0.5, torso: [4, -5, 0] })],
		[len, R, 'easeinoutsine'],
	]);
}
{ // Tyrant's Charge: crouch, then a shoulder-first rush
	const [len] = T('charge_windup', 20, 20);
	const crouch = pose({ torso: 35, head: -30, root: [0, -5, 0], right_leg: -30, left_leg: 25, right_arm: -30, right_forearm: -20, sword: 0, left_arm: 20, cape: 30 });
	clip('charge_windup', len, 'hold', [[0, R], [len, crouch, 'easeoutquad']]);
	const runA = pose({ torso: 40, head: -35, root: [0, -4, 0], right_leg: -35, left_leg: 30, right_arm: -60, right_forearm: -10, sword: 20, left_arm: 30, cape: 45 });
	const runB = pose({ ...runA, right_leg: 30, left_leg: -35, root: [0, -2, 0], cape: 55, left_arm: -10 });
	T('charge_run', 12, 0);
	clip('charge_run', 0.6, true, [[0, runA], [0.3, runB], [0.6, runA]]);
	const [endLen] = T('charge_end', 16, 0);
	clip('charge_end', endLen, false, [[0, runA], [0.25, pose({ torso: -10, right_leg: -10, left_leg: 10, cape: 20, sword: 179 }), 'easeoutquad'], [endLen, R, 'easeinoutsine']]);
}
{ // Soul Beam: the ribs thrown open to the sky, the core pours out
	const [len, start] = T('soul_beam', 70, 24);
	TIMING.soul_beam_end = [70, 60];
	const open = pose({ torso: -20, head: -12, left_arm: -120, left_forearm: -10, right_arm: -110, right_forearm: 0, sword: 60, jaw: 30, core_s: 1.8, flame_s: 1.8 });
	clip('soul_beam', len, false, [
		[0, R],
		[1.0, open, 'easeoutquad'],
		[start, pose({ ...open, torso: -22, core_s: 2.3 })],
		[2.0, pose({ ...open, torso: [-20, 4, 0], core_s: 2.1 })],
		[2.6, pose({ ...open, torso: [-20, -4, 0], core_s: 2.3 })],
		[3.0, pose({ ...open, torso: -22, core_s: 2.0 })],
		[len, R, 'easeinoutsine'],
	]);
}
{ // Grave Step: sinks into the ground, rises somewhere else
	const [len, at] = T('grave_step', 30, 14);
	const sunk = pose({ torso: 20, right_arm: -40, left_arm: -40, root: [0, -68, 0] });
	clip('grave_step', len, false, [
		[0, R],
		[at - 0.05, sunk, 'easeinquad'],
		[at + 0.05, sunk],
		[len, R, 'easeoutback'],
	]);
}
{ // the phase roar
	const [len, hit] = T('roar', 50, 20);
	const rear = pose({ torso: -25, head: -30, jaw: 35, left_arm: -120, right_arm: -110, sword: 60, root: [0, 1, 0], core_s: 1.6, flame_s: 2.5 });
	const peak = pose({ ...rear, torso: -30, head: -35, jaw: 40, core_s: 2.0 });
	clip('roar', len, false, [
		[0, R],
		[0.8, rear, 'easeoutquad'],
		[hit, peak, 'easeinquad'],
		[1.2, pose({ ...peak, head: [-35, 6, 0] })],
		[1.4, pose({ ...peak, head: [-35, -6, 0] })],
		[1.6, pose({ ...peak, head: [-35, 6, 0] })],
		[1.8, peak],
		[len, R, 'easeinoutsine'],
	]);
}
{ // death: staggers, sits back onto the ground, slumps, falls forward; the core gutters out
	const [len] = T('death', 80, 0);
	clip('death', len, 'hold', [
		[0, R],
		[0.5, pose({ torso: [-15, 10, 0], head: -20, right_arm: 20, jaw: 15 }), 'easeoutquad'],
		[1.2, pose({ root: [0, -22, 0], right_leg: -85, left_leg: -80, torso: 20, head: 25, right_arm: 10, sword: 120, jaw: 25, core_s: 0.8 }), 'easeinquad'],
		[2.2, pose({ root: [0, -22, 0], right_leg: -85, left_leg: -80, torso: 45, head: 35, right_arm: 30, left_arm: 30, left_forearm: 0, sword: 120, jaw: 30, core_s: 0.5, flame_s: 0.4 })],
		[3.0, pose({ root: [0, -24, -6], right_leg: -85, left_leg: -80, torso: 80, head: 20, right_arm: 40, left_arm: 40, left_forearm: 0, sword: 120, jaw: 30, core_s: 0.2, flame_s: 0.01 }), 'easeinquad'],
		[len, pose({ root: [0, -24, -6], right_leg: -85, left_leg: -80, torso: 80, head: 20, right_arm: 40, left_arm: 40, left_forearm: 0, sword: 120, jaw: 30, core_s: 0.01, flame_s: 0.01 })],
	]);
}
json(path.join(A, 'animations/bone_tyrant.animation.json'), { format_version: '1.8.0', animations });
for (const [n, [len, at]] of Object.entries(TIMING)) console.log(`  clip ${n.padEnd(14)} ${String(len).padStart(3)} ticks, key at ${at}`);
if (problems.length) { console.log('POSE PROBLEMS:\n  ' + problems.join('\n  ')); process.exitCode = 1; }
console.log(`rest sword tip (y,z du): ${swordTip(R).map(v => v.toFixed(1))}`);

// ================================================================ the six horde skeletons (recoloured vanilla)
const SKELETON = decode(path.join(VANILLA, 'assets/minecraft/textures/entity/skeleton/skeleton.png'));
function variant(name, fn) {
	const s = { w: SKELETON.w, h: SKELETON.h, px: Buffer.from(SKELETON.px) };
	seed = name.length * 104729;
	for (let i = 0; i < s.w * s.h; i++) {
		const o = i * 4;
		if (!s.px[o + 3]) continue;
		const x = i % s.w, y = Math.floor(i / s.w);
		const c = [s.px[o], s.px[o + 1], s.px[o + 2]];
		const lum = (c[0] + c[1] + c[2]) / 3;
		const face = x >= 8 && x < 16 && y >= 8 && y < 16;
		const out = fn(c, lum, x, y, face);
		s.px[o] = clamp(out[0]); s.px[o + 1] = clamp(out[1]); s.px[o + 2] = clamp(out[2]);
	}
	fs.writeFileSync(path.join(A, `textures/entity/horde_skeleton/${name}.png`), encode(s.w, s.h, s.px));
}
const socket = (lum, face) => face && lum < 110;
// Bone Runner: bleached ash-white, red pinprick eyes
variant('bone_runner', (c, lum, x, y, face) => socket(lum, face) ? [200, 30, 30] : [lum * 1.08 + 8, lum * 1.06 + 6, lum * 1.04 + 4]);
// Bone Knight: tarnished iron-grey bone, cold blue eyes
variant('bone_knight', (c, lum, x, y, face) => socket(lum, face) ? [70, 140, 255] : [lum * 0.62, lum * 0.64, lum * 0.72]);
// Blight Archer: rotted green-black bone, sickly green eyes
variant('blight_archer', (c, lum, x, y, face) => socket(lum, face) ? [120, 255, 80] : [lum * 0.42 + (rnd() < 0.1 ? 20 : 0), lum * 0.55 + 6, lum * 0.36]);
// Bone Bomber: charred black, glowing orange cracks
variant('bone_bomber', (c, lum, x, y, face) => socket(lum, face) ? [255, 160, 30]
	: rnd() < 0.09 ? [255, 120 + rnd() * 60, 20] : [lum * 0.28 + 10, lum * 0.24 + 6, lum * 0.22 + 4]);
// Bone Brute: thick yellowed bone, blood-red eyes
variant('bone_brute', (c, lum, x, y, face) => socket(lum, face) ? [255, 40, 20] : [lum * 1.0, lum * 0.88, lum * 0.6]);
// Necromancer: soul-blue bone, cyan eyes
variant('necromancer', (c, lum, x, y, face) => socket(lum, face) ? [130, 255, 255] : [lum * 0.62, lum * 0.82, lum * 1.0]);

// ---- spawn eggs + entity tags
const VARIANTS = ['bone_runner', 'bone_knight', 'blight_archer', 'bone_bomber', 'bone_brute', 'necromancer'];
for (const v of VARIANTS) json(path.join(A, `models/item/${v}_spawn_egg.json`), { parent: 'minecraft:item/template_spawn_egg' }, 2);
const ids = ['projecthero:bone_tyrant', ...VARIANTS.map(v => `projecthero:${v}`)];
json(path.join(D, 'minecraft/tags/entity_type/undead.json'), { replace: false, values: ids }, 2);
json(path.join(D, 'minecraft/tags/entity_type/skeletons.json'), { replace: false, values: VARIANTS.map(v => `projecthero:${v}`) }, 2);
json(path.join(D, 'minecraft/tags/entity_type/sensitive_to_smite.json'), { replace: false, values: ids }, 2);
console.log('variants: 6 textures, 6 spawn-egg models, undead/skeletons/smite tags');
