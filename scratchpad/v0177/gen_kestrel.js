// v0.17.7 Sky Fortress boss: Admiral Kestrel's GeckoLib assets.
//   - geo/admiral_kestrel.geo.json         (a heavy flight suit: crested visor helmet, layered chest armour with a
//                                            glowing core, pauldrons, forearm missile pods, a twin-nozzle jet pack,
//                                            two-segment folding wing-blades with glowing edges, armoured legs, jet boots)
//   - animations/admiral_kestrel.animation.json (idle / walk / hover, each folded and wings-open, dash, missiles,
//                                            strafe, radio, dive, recover, transform, death)
//   - textures/entity/admiral_kestrel.png + _glowmask.png (box-UV atlas, every material painted with a face-light
//                                            gradient, bevels, panel lines, rivets, wear and decals)
// Usage (repo root): node scratchpad/v0177/gen_kestrel.js
//
// Model space is Blockbench/Bedrock: y up, 16 units = 1 block, she faces -Z, +X is her left (screen right from the front).
// Rotation conventions (as gen_brood_queen_v01416.js): +rx pitches the front (-Z) down, +rz lowers the +X end, +ry swings
// the +X end forward. So an arm hanging down is raised forward with -rx, and a wing hanging down at -X opens outward with +rz.
const fs = require('fs');
const path = require('path');
const { encode } = require('../pngkit');

const A = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero');
const json = (p, o) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, JSON.stringify(o, null, 1) + '\n'); };

// ------------------------------------------------------------------ geometry
const bones = [];
const byName = {};
function bone(name, parent, pivot, rotation) {
	const b = { name, pivot, cubes: [] };
	if (parent) b.parent = parent;
	if (rotation) b.rotation = rotation;
	bones.push(b);
	byName[name] = b;
	return b;
}
/** cube from (x0,y0,z0) with integer size, painted as material {@code mat}; {@code deco} = per-cube decal flags. */
function cube(b, origin, size, mat, deco = {}) {
	b.cubes.push({ origin, size, mat, deco });
}

bone('root', null, [0, 0, 0]);
bone('hips', 'root', [0, 19, 0]);
cube(byName.hips, [-4, 18, -2.5], [8, 3, 5], 'suit');
cube(byName.hips, [-4.5, 19, -3], [9, 2, 6], 'belt', { buckle: true });
cube(byName.hips, [-3, 15, -3.2], [6, 4, 1], 'armor_d', { tasset: true }); // front tasset
cube(byName.hips, [-3, 15, 2.2], [6, 4, 1], 'armor_d');

for (const [side, s] of [['right', -1], ['left', 1]]) {
	const x = s * 2.4;
	const thigh = bone(`${side}_leg`, 'hips', [x, 18, 0]);
	cube(thigh, [x - 2.5, 10, -2.5], [5, 9, 5], 'suit');
	cube(thigh, [x - 2.8, 11, -3.1], [5, 6, 1], 'armor', { stripe: true }); // thigh plate
	cube(thigh, [s > 0 ? x + 2.2 : x - 3.2, 11, -2], [1, 6, 4], 'armor_d'); // outer thigh plate
	const shin = bone(`${side}_shin`, `${side}_leg`, [x, 10, 0]);
	cube(shin, [x - 2.5, 3, -2.5], [5, 7, 5], 'armor');
	cube(shin, [x - 1.5, 8, -3.4], [3, 3, 1], 'armor_hi'); // knee cap
	cube(shin, [x - 3, 0, -4], [6, 3, 7], 'boot');
	cube(shin, [x - 1, 0.5, 3], [2, 2, 2], 'nozzle', { glowEnd: 'south' }); // heel jet
}

bone('torso', 'hips', [0, 21, 0]);
cube(byName.torso, [-4, 21, -2.5], [8, 4, 5], 'suit');
cube(byName.torso, [-5, 25, -3], [10, 8, 6], 'armor');
cube(byName.torso, [-4, 26, -3.8], [8, 6, 1], 'armor_hi', { chevron: true }); // chest plate
cube(byName.torso, [-1, 28, -4.4], [2, 2, 1], 'glow'); // the core
cube(byName.torso, [-4, 33, -3], [8, 1, 6], 'armor_d'); // collar
cube(byName.torso, [-3.5, 22, -3.2], [7, 3, 1], 'armor_d', { ribs: true }); // ab plate

bone('jetpack', 'torso', [0, 28, 3]);
cube(byName.jetpack, [-4, 23, 3], [8, 9, 4], 'armor_d', { vents: true });
cube(byName.jetpack, [-3, 31, 3.5], [6, 2, 3], 'armor');
for (const s of [-1, 1]) {
	cube(byName.jetpack, [s * 2.6 - 1.5, 19, 3.5], [3, 4, 3], 'nozzle', { glowEnd: 'down' });
	cube(byName.jetpack, [s * 2.6 - 1, 18, 4], [2, 1, 2], 'thrust');
	cube(byName.jetpack, [s > 0 ? 4 : -5, 25, 4], [1, 5, 2], 'accent'); // side fins
}

for (const [side, s] of [['right', -1], ['left', 1]]) {
	const wing = bone(`${side}_wing`, 'jetpack', [s * 3, 31, 6], [12, 0, s * 10]);
	cube(wing, [s > 0 ? 2.5 : -3.5, 18, 5.5], [1, 13, 5], 'wing', { stripe: true });
	cube(wing, [s > 0 ? 2.5 : -3.5, 18, 10.5], [1, 13, 1], 'glow_edge');
	const outer = bone(`${side}_wing_tip`, `${side}_wing`, [s * 3, 18, 8]);
	cube(outer, [s > 0 ? 2.5 : -3.5, 8, 6], [1, 10, 4], 'wing');
	cube(outer, [s > 0 ? 2.5 : -3.5, 8, 10], [1, 10, 1], 'glow_edge');
}

bone('head', 'torso', [0, 34, 0]);
cube(byName.head, [-4, 34, -4], [8, 8, 8], 'helmet', { helmet: true });
cube(byName.head, [-3.5, 37, -4.6], [7, 3, 1], 'visor');
cube(byName.head, [-3, 34, -4.5], [6, 2, 1], 'armor_d', { grille: true }); // chin / breather
cube(byName.head, [-1, 42, -3], [2, 2, 8], 'accent'); // crest
cube(byName.head, [-1, 40, 4], [2, 3, 2], 'accent');
for (const s of [-1, 1]) {
	cube(byName.head, [s > 0 ? 4 : -5, 35, -2], [1, 5, 5], 'armor_d', { ear: true }); // cheek / comm guards
	cube(byName.head, [s > 0 ? 4.5 : -5.5, 39, 1], [1, 4, 1], 'armor'); // antenna stubs
}

for (const [side, s] of [['right', -1], ['left', 1]]) {
	const x = s * 6.5;
	const sh = bone(`${side}_shoulder`, 'torso', [x, 31, 0]);
	cube(sh, [x - 3, 30, -3.5], [6, 4, 7], 'armor', { trim: true });
	cube(sh, [x - 2.5, 34, -2.5], [5, 1, 5], 'armor_hi');
	const arm = bone(`${side}_arm`, 'torso', [x, 30, 0]);
	cube(arm, [x - 2, 24, -2], [4, 6, 4], 'suit');
	const fore = bone(`${side}_forearm`, `${side}_arm`, [x, 24, 0]);
	cube(fore, [x - 2.5, 17, -2.5], [5, 7, 5], 'armor', { bands: true });
	cube(fore, [s > 0 ? x + 2.5 : x - 5.5, 18, -3], [3, 5, 6], 'pod', { tubes: true }); // missile pod, outer side
	cube(fore, [x - 2, 13, -2], [4, 4, 4], 'glove');
}

// ---- box-UV packing (shelf packer, identical cube+material+deco share a region)
const TEX_W = 128;
const all = [];
for (const b of bones) for (const c of b.cubes) all.push(c);
const regions = new Map();
const order = [...all].sort((a, b) => (b.size[2] + b.size[1]) - (a.size[2] + a.size[1]) || (b.size[0] - a.size[0]));
let ux = 0, uy = 0, shelf = 0;
for (const c of order) {
	const key = c.size.join('x') + c.mat + JSON.stringify(c.deco);
	if (regions.has(key)) { c.uv = regions.get(key).uv; continue; }
	const [w, h, d] = c.size;
	const rw = 2 * (w + d), rh = d + h;
	if (ux + rw > TEX_W) { ux = 0; uy += shelf; shelf = 0; }
	const r = { uv: [ux, uy], w, h, d, mat: c.mat, deco: c.deco };
	regions.set(key, r);
	c.uv = r.uv;
	ux += rw;
	shelf = Math.max(shelf, rh);
}
const TEX_H = Math.max(64, Math.ceil((uy + shelf) / 16) * 16);
console.log(`kestrel texture ${TEX_W}x${TEX_H}, ${regions.size} regions for ${all.length} cubes`);

json(path.join(A, 'geo/admiral_kestrel.geo.json'), {
	format_version: '1.12.0',
	'minecraft:geometry': [{
		description: { identifier: 'geometry.admiral_kestrel', texture_width: TEX_W, texture_height: TEX_H,
			visible_bounds_width: 5, visible_bounds_height: 4, visible_bounds_offset: [0, 1.5, 0] },
		bones: bones.map(b => {
			const o = { name: b.name };
			if (b.parent) o.parent = b.parent;
			o.pivot = b.pivot;
			if (b.rotation) o.rotation = b.rotation;
			if (b.cubes.length) o.cubes = b.cubes.map(c => ({ origin: c.origin, size: c.size, uv: c.uv }));
			return o;
		}),
	}],
});

// ------------------------------------------------------------------ texture
let seed = 717;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const shade = (c, f) => c.map(v => clamp(v * f));
const mix = (a, b, t) => a.map((v, i) => clamp(v + (b[i] - v) * t));
const tex = Buffer.alloc(TEX_W * TEX_H * 4);
const glow = Buffer.alloc(TEX_W * TEX_H * 4);
const put = (buf, x, y, c, a = 255) => { if (x < 0 || y < 0 || x >= TEX_W || y >= TEX_H) return; const i = (y * TEX_W + x) * 4; buf[i] = clamp(c[0]); buf[i + 1] = clamp(c[1]); buf[i + 2] = clamp(c[2]); buf[i + 3] = a; };

const GUN = [100, 106, 120], GUN_D = [64, 68, 80], NAVY = [36, 50, 98], NAVY_HI = [70, 96, 170], ORANGE = [240, 130, 34],
	SUIT = [34, 36, 44], BLACK = [20, 20, 24], SKY = [140, 210, 255], HOT = [255, 196, 90], SILVER = [190, 196, 206];
const LIGHT = { up: 1.18, down: 0.62, north: 1.0, south: 0.78, east: 0.88, west: 0.88 };

/** The six faces of a box-UV region: [name, u, v, width, height]. */
function faces(r) {
	const { w, h, d } = r;
	const [u, v] = r.uv;
	return [['up', u + d, v, w, d], ['down', u + d + w, v, w, d], ['east', u, v + d, d, h], ['north', u + d, v + d, w, h],
		['west', u + d + w, v + d, d, h], ['south', u + 2 * d + w, v + d, w, h]];
}

/** Base painter: a metal-ish surface with a vertical gradient, bevelled rim, grain and specks of wear. */
function metal(base, fu, fv, fw, fh, face, opts = {}) {
	const grad = face === 'up' || face === 'down' ? 1 : 1.08 - 0.16 * (fv / Math.max(1, fh - 1));
	let c = shade(base, LIGHT[face] * grad);
	c = c.map(x => x + (rnd() - 0.5) * (opts.grain ?? 7));
	if (fw > 2 && fh > 2) {
		if (fv === 0 || fu === 0) c = mix(c, [255, 255, 255], 0.16);
		if (fv === fh - 1 || fu === fw - 1) c = shade(c, 0.72);
	}
	if (rnd() < (opts.wear ?? 0.025)) c = mix(c, SILVER, 0.55);
	return c;
}

for (const r of regions.values()) {
	for (const [face, u0, v0, fw, fh] of faces(r)) {
		for (let fv = 0; fv < fh; fv++) for (let fu = 0; fu < fw; fu++) {
			const x = u0 + fu, y = v0 + fv;
			let c;
			let g = null;
			const d = r.deco;
			switch (r.mat) {
				case 'armor':
					c = metal(GUN, fu, fv, fw, fh, face);
					if (fw >= 5 && fh >= 5 && (fu === Math.floor(fw / 2)) && face !== 'up' && face !== 'down') c = shade(c, 0.82); // centre seam
					if (fw >= 4 && fh >= 4 && ((fu === 1 && fv === 1) || (fu === fw - 2 && fv === 1))) c = mix(c, [235, 238, 245], 0.6); // rivets
					if (d.stripe && fu >= Math.floor(fw / 2) - 1 && fu <= Math.floor(fw / 2) && face === 'north') c = shade(NAVY, LIGHT[face]);
					if (d.bands && (fv === 1 || fv === fh - 2) && face !== 'up' && face !== 'down') c = shade(NAVY, LIGHT[face] * 1.1);
					if (d.trim && fv === fh - 1 && face !== 'up' && face !== 'down') c = shade(ORANGE, LIGHT[face]);
					if (d.trim && face === 'up' && (fu === 0 || fu === fw - 1)) c = shade(ORANGE, 1.1);
					break;
				case 'armor_d':
					c = metal(GUN_D, fu, fv, fw, fh, face, { wear: 0.04 });
					if (d.ribs && face === 'north' && fv % 1 === 0 && fu % 2 === 1) c = shade(c, 0.7);
					if (d.vents && face === 'south' && fv >= 2 && fv <= fh - 3 && fv % 2 === 0 && fu >= 1 && fu <= fw - 2) c = BLACK;
					if (d.vents && face === 'south' && fv === 1 && fu >= 2 && fu <= fw - 3) { c = shade(ORANGE, 0.9); }
					if (d.grille && face === 'north' && fu % 2 === 1) c = BLACK;
					if (d.ear && (face === 'east' || face === 'west') && Math.abs(fu - (fw - 1) / 2) < 1.2 && Math.abs(fv - (fh - 1) / 2) < 1.2) { c = SKY; g = SKY; }
					if (d.tasset && face === 'north' && fv === 0) c = shade(ORANGE, 0.95);
					break;
				case 'armor_hi': {
					// navy enamel with a diagonal gloss streak
					const gloss = Math.abs((fu - fv) - Math.floor(fw / 3)) < 1 ? 0.35 : 0;
					c = mix(shade(NAVY, LIGHT[face] * (1.1 - 0.15 * fv / Math.max(1, fh - 1))), NAVY_HI, gloss);
					if (fv === 0 || fu === 0) c = mix(c, NAVY_HI, 0.5);
					if (fv === fh - 1 || fu === fw - 1) c = shade(c, 0.7);
					if (d.chevron && face === 'north') {
						const cx = (fw - 1) / 2;
						const dx = Math.abs(fu - cx);
						if (Math.abs(fv - (fh - 2 - dx)) < 0.75 && dx < fw / 2 - 0.5) c = shade(ORANGE, 1.05);
					}
					break;
				}
				case 'accent':
					c = metal(ORANGE, fu, fv, fw, fh, face, { grain: 10, wear: 0.0 });
					if (fv === 0 && face !== 'up') c = mix(c, [255, 230, 180], 0.4);
					break;
				case 'suit': {
					// quilted flight-suit fabric: diamond stitching
					c = shade(SUIT, LIGHT[face]);
					c = c.map(v => v + (rnd() - 0.5) * 6);
					if ((fu + fv) % 3 === 0 || (fu - fv + 30) % 3 === 0) c = shade(c, 0.78);
					if ((fu + fv) % 3 === 1 && (fu - fv + 30) % 3 === 1) c = mix(c, [80, 84, 100], 0.3);
					break;
				}
				case 'belt':
					c = shade([30, 26, 24], LIGHT[face]).map(v => v + (rnd() - 0.5) * 6);
					if (fv === 0) c = shade(c, 1.3);
					if (d.buckle && face === 'north' && Math.abs(fu - (fw - 1) / 2) < 1.2) c = mix(SILVER, [255, 255, 255], fv === 0 ? 0.4 : 0);
					if (face === 'north' && (fu === 1 || fu === fw - 2)) c = [58, 52, 44]; // pouches
					break;
				case 'helmet': {
					const top = face === 'up';
					const grad = top ? 1.1 : 1.1 - 0.25 * fv / Math.max(1, fh - 1);
					c = shade(NAVY, LIGHT[face] * grad).map(v => v + (rnd() - 0.5) * 5);
					// gunmetal lower shell, navy dome; a silver racing stripe over the top
					if (!top && fv >= fh - 3) c = shade(GUN, LIGHT[face] * (1.0 - 0.1 * (fv - fh + 3)));
					if (!top && fv === fh - 4) c = shade(ORANGE, LIGHT[face]);
					if (top && Math.abs(fu - (fw - 1) / 2) < 1.6) c = mix(SILVER, [255, 255, 255], fu === Math.floor(fw / 2) ? 0.2 : 0);
					if (face === 'south' && Math.abs(fu - (fw - 1) / 2) < 1.6 && fv < fh - 3) c = shade(SILVER, 0.85);
					if (!top && face !== 'down' && fv === 0) c = mix(c, NAVY_HI, 0.5);
					// gloss
					if (face === 'north' && fu === 1 && fv <= 2) c = mix(c, [255, 255, 255], 0.35);
					break;
				}
				case 'visor': {
					// orange glass: dark at the bottom, bright reflection streak, a glowing HUD line
					const t = fv / Math.max(1, fh - 1);
					c = mix([255, 200, 110], [120, 46, 10], t);
					if (face !== 'north') c = shade(c, 0.6);
					if (face === 'north' && fv === 0 && fu >= 1 && fu <= 3) c = [255, 248, 230];
					if (face === 'north' && fv === 1) { g = [255, 150, 60]; c = mix(c, [255, 170, 80], 0.5); }
					break;
				}
				case 'glow':
					c = (fu + fv) % 2 === 0 ? [235, 250, 255] : SKY;
					g = c;
					break;
				case 'glow_edge':
					c = mix(HOT, ORANGE, (fv % 3) / 3);
					g = c;
					break;
				case 'thrust':
					c = face === 'down' ? [255, 250, 230] : mix(HOT, [255, 110, 30], fv / Math.max(1, fh));
					g = c;
					break;
				case 'nozzle': {
					const t = fv / Math.max(1, fh - 1);
					c = mix(shade([52, 54, 62], LIGHT[face]), [92, 64, 120], t * 0.6); // heat tint toward the mouth
					if (t > 0.75 && face !== 'up' && face !== 'down') c = mix(c, [210, 120, 50], 0.45);
					if (fv === 0 && face !== 'up' && face !== 'down') c = shade(c, 1.25);
					const mouth = (d.glowEnd === 'down' && face === 'down') || (d.glowEnd === 'south' && face === 'south');
					if (mouth) {
						const dc = Math.hypot(fu - (fw - 1) / 2, fv - (fh - 1) / 2);
						c = dc < Math.min(fw, fh) / 2 - 0.4 ? HOT : [30, 30, 34];
						if (dc < Math.min(fw, fh) / 2 - 0.4) g = HOT;
					}
					break;
				}
				case 'wing': {
					c = metal(GUN, fu, fv, fw, fh, face, { wear: 0.02 });
					if ((face === 'east' || face === 'west')) {
						if (fu >= fw - 2) c = shade(NAVY, LIGHT[face] * 1.05); // navy leading stripe
						if (fv % 4 === 0) c = shade(c, 0.78); // panel lines
						if (d.stripe && fv === 2 && fu < fw - 2) c = shade(ORANGE, LIGHT[face]);
					}
					break;
				}
				case 'pod': {
					c = metal([58, 60, 68], fu, fv, fw, fh, face);
					if (face === 'north') {
						// two rows of missile tubes with hot tips
						const tube = fv >= 1 && fv <= fh - 2 && fu >= 0 && fu <= fw - 1 && (fv % 2 === 1);
						if (tube) { c = (fu % 2 === 0) ? [16, 16, 18] : [255, 140, 50]; if (fu % 2 === 1) g = [255, 140, 50]; }
					}
					if ((face === 'east' || face === 'west') && fv === 1) c = shade(ORANGE, LIGHT[face]);
					if ((face === 'east' || face === 'west') && fv === fh - 2 && fu % 2 === 0) c = [255, 210, 60]; // hazard
					break;
				}
				case 'glove':
					c = shade(BLACK, LIGHT[face]).map(v => v + (rnd() - 0.5) * 6);
					if (face === 'north' && fv <= 1) c = shade(GUN, LIGHT[face]); // knuckle plate
					if (face === 'up') c = shade(GUN_D, 1.1);
					break;
				case 'boot':
					c = metal([54, 56, 64], fu, fv, fw, fh, face);
					if (face === 'north' && fv === 0) c = shade(ORANGE, 1.0);
					if (fv === fh - 1 && face !== 'up' && face !== 'down') c = [26, 26, 28]; // sole
					if (face === 'down') c = [24, 24, 26];
					break;
				default:
					c = [255, 0, 255];
			}
			put(tex, x, y, c);
			if (g) put(glow, x, y, g);
		}
	}
}
fs.mkdirSync(path.join(A, 'textures/entity'), { recursive: true });
fs.writeFileSync(path.join(A, 'textures/entity/admiral_kestrel.png'), encode(TEX_W, TEX_H, tex));
fs.writeFileSync(path.join(A, 'textures/entity/admiral_kestrel_glowmask.png'), encode(TEX_W, TEX_H, glow));

// ------------------------------------------------------------------ animations
const r3 = v => Math.round(v * 1000) / 1000;
function clip(length, loop, tracks) {
	const out = { animation_length: r3(length) };
	if (loop) out.loop = true;
	out.bones = {};
	for (const [b, chans] of Object.entries(tracks)) {
		if (!byName[b]) throw new Error('animation names a missing bone: ' + b);
		out.bones[b] = {};
		for (const [ch, frames] of Object.entries(chans)) {
			const o = {};
			for (const [t, v] of frames) o[r3(t).toFixed(2)] = v.map(r3);
			out.bones[b][ch] = o;
		}
	}
	return out;
}
const Z = [0, 0, 0];
const add = (t, b, ch, frames) => { t[b] = t[b] || {}; t[b][ch] = frames; };
const hold = v => [[0, v]];
// wing poses: folded (rest) and open (spread out and back, tips straight)
const WING_OPEN_L = [-20, 10, 95], WING_OPEN_R = [-20, -10, -95];
const TIP_OPEN_L = [0, 0, -12], TIP_OPEN_R = [0, 0, 12];
function wings(t, open, flap = 0, len = 1) {
	if (!open) {
		add(t, 'left_wing', 'rotation', [[0, Z], [len / 2, [2, 0, 2]], [len, Z]]);
		add(t, 'right_wing', 'rotation', [[0, Z], [len / 2, [2, 0, -2]], [len, Z]]);
		return;
	}
	add(t, 'left_wing', 'rotation', [[0, WING_OPEN_L], [len / 2, [WING_OPEN_L[0] + flap, WING_OPEN_L[1], WING_OPEN_L[2] + flap]], [len, WING_OPEN_L]]);
	add(t, 'right_wing', 'rotation', [[0, WING_OPEN_R], [len / 2, [WING_OPEN_R[0] + flap, WING_OPEN_R[1], WING_OPEN_R[2] - flap]], [len, WING_OPEN_R]]);
	add(t, 'left_wing_tip', 'rotation', hold(TIP_OPEN_L));
	add(t, 'right_wing_tip', 'rotation', hold(TIP_OPEN_R));
}
const anims = {};
function idle(open) {
	const t = {};
	add(t, 'torso', 'rotation', [[0, Z], [1.5, [-2, 0, 0]], [3, Z]]);
	add(t, 'head', 'rotation', [[0, Z], [1, [2, -8, 0]], [2, [0, 6, 0]], [3, Z]]);
	add(t, 'right_arm', 'rotation', [[0, [0, 0, 6]], [1.5, [-3, 0, 8]], [3, [0, 0, 6]]]);
	add(t, 'left_arm', 'rotation', [[0, [0, 0, -6]], [1.5, [-3, 0, -8]], [3, [0, 0, -6]]]);
	add(t, 'right_forearm', 'rotation', hold([-14, 0, 0]));
	add(t, 'left_forearm', 'rotation', hold([-14, 0, 0]));
	add(t, 'hips', 'position', [[0, Z], [1.5, [0, -0.3, 0]], [3, Z]]);
	wings(t, open, 4, 3);
	return clip(3, true, t);
}
function walk(open) {
	const t = {};
	const L = 1.0;
	add(t, 'right_leg', 'rotation', [[0, [-28, 0, 0]], [L / 2, [28, 0, 0]], [L, [-28, 0, 0]]]);
	add(t, 'left_leg', 'rotation', [[0, [28, 0, 0]], [L / 2, [-28, 0, 0]], [L, [28, 0, 0]]]);
	add(t, 'right_shin', 'rotation', [[0, [10, 0, 0]], [L / 4, [40, 0, 0]], [L / 2, [5, 0, 0]], [L, [10, 0, 0]]]);
	add(t, 'left_shin', 'rotation', [[0, [5, 0, 0]], [L / 2, [10, 0, 0]], [3 * L / 4, [40, 0, 0]], [L, [5, 0, 0]]]);
	add(t, 'right_arm', 'rotation', [[0, [22, 0, 6]], [L / 2, [-22, 0, 6]], [L, [22, 0, 6]]]);
	add(t, 'left_arm', 'rotation', [[0, [-22, 0, -6]], [L / 2, [22, 0, -6]], [L, [-22, 0, -6]]]);
	add(t, 'right_forearm', 'rotation', hold([-20, 0, 0]));
	add(t, 'left_forearm', 'rotation', hold([-20, 0, 0]));
	add(t, 'torso', 'rotation', [[0, [4, 4, 0]], [L / 2, [4, -4, 0]], [L, [4, 4, 0]]]);
	add(t, 'hips', 'position', [[0, Z], [L / 4, [0, 0.6, 0]], [L / 2, Z], [3 * L / 4, [0, 0.6, 0]], [L, Z]]);
	wings(t, open, 3, L);
	return clip(L, true, t);
}
function hover(open) {
	const t = {};
	add(t, 'root', 'position', [[0, Z], [1, [0, 1.2, 0]], [2, Z]]);
	add(t, 'torso', 'rotation', hold([12, 0, 0]));
	add(t, 'hips', 'rotation', hold([8, 0, 0]));
	add(t, 'right_leg', 'rotation', [[0, [14, 0, 4]], [1, [20, 0, 6]], [2, [14, 0, 4]]]);
	add(t, 'left_leg', 'rotation', [[0, [22, 0, -4]], [1, [16, 0, -6]], [2, [22, 0, -4]]]);
	add(t, 'right_shin', 'rotation', hold([25, 0, 0]));
	add(t, 'left_shin', 'rotation', hold([35, 0, 0]));
	add(t, 'right_arm', 'rotation', [[0, [-10, 0, 18]], [1, [-14, 0, 22]], [2, [-10, 0, 18]]]);
	add(t, 'left_arm', 'rotation', [[0, [-10, 0, -18]], [1, [-14, 0, -22]], [2, [-10, 0, -18]]]);
	add(t, 'right_forearm', 'rotation', hold([-30, 0, 0]));
	add(t, 'left_forearm', 'rotation', hold([-30, 0, 0]));
	add(t, 'head', 'rotation', hold([-10, 0, 0]));
	wings(t, open, 6, 2);
	return clip(2, true, t);
}
for (const open of [false, true]) {
	const sfx = open ? '_open' : '';
	anims['idle' + sfx] = idle(open);
	anims['walk' + sfx] = walk(open);
	anims['hover' + sfx] = hover(open);
}
// jet dash: crouch with arms back (0-0.6), then a low forward lean held through the dash
{
	const t = {};
	add(t, 'torso', 'rotation', [[0, Z], [0.6, [25, 0, 0]], [0.7, [45, 0, 0]], [1.2, [45, 0, 0]]]);
	add(t, 'hips', 'position', [[0, Z], [0.6, [0, -3, 0]], [0.7, [0, -1, 0]], [1.2, [0, -1, 0]]]);
	add(t, 'right_leg', 'rotation', [[0, Z], [0.6, [-50, 0, 0]], [0.7, [30, 0, 0]], [1.2, [30, 0, 0]]]);
	add(t, 'left_leg', 'rotation', [[0, Z], [0.6, [-30, 0, 0]], [0.7, [50, 0, 0]], [1.2, [50, 0, 0]]]);
	add(t, 'right_shin', 'rotation', [[0, Z], [0.6, [80, 0, 0]], [0.7, [20, 0, 0]], [1.2, [20, 0, 0]]]);
	add(t, 'left_shin', 'rotation', [[0, Z], [0.6, [60, 0, 0]], [0.7, [40, 0, 0]], [1.2, [40, 0, 0]]]);
	add(t, 'right_arm', 'rotation', [[0, Z], [0.6, [50, 0, 20]], [0.7, [60, 0, 25]], [1.2, [60, 0, 25]]]);
	add(t, 'left_arm', 'rotation', [[0, Z], [0.6, [50, 0, -20]], [0.7, [60, 0, -25]], [1.2, [60, 0, -25]]]);
	add(t, 'head', 'rotation', [[0, Z], [0.7, [-35, 0, 0]], [1.2, [-35, 0, 0]]]);
	add(t, 'left_wing', 'rotation', [[0, Z], [0.7, [-30, 0, 40]], [1.2, [-30, 0, 40]]]);
	add(t, 'right_wing', 'rotation', [[0, Z], [0.7, [-30, 0, -40]], [1.2, [-30, 0, -40]]]);
	anims.dash = clip(1.2, false, t);
}
// missile barrage: both forearms up and forward, pods aimed (0-0.8), a recoil kick per salvo
{
	const t = {};
	const kicks = [[0, [0, 0, 0]], [0.8, [0, 0, 0]]];
	for (let k = 0; k < 6; k++) { kicks.push([0.8 + k * 0.15, [0, 0, 0.8]]); kicks.push([0.875 + k * 0.15, [0, 0, 0]]); }
	add(t, 'right_arm', 'rotation', [[0, Z], [0.5, [-95, 0, -10]], [1.8, [-95, 0, -10]], [2.2, Z]]);
	add(t, 'left_arm', 'rotation', [[0, Z], [0.5, [-95, 0, 10]], [1.8, [-95, 0, 10]], [2.2, Z]]);
	add(t, 'right_forearm', 'rotation', [[0, Z], [0.5, [-10, 0, 0]], [2.2, Z]]);
	add(t, 'left_forearm', 'rotation', [[0, Z], [0.5, [-10, 0, 0]], [2.2, Z]]);
	add(t, 'right_forearm', 'position', kicks);
	add(t, 'left_forearm', 'position', kicks.map(([tt, v]) => [tt + 0.07, v]));
	add(t, 'torso', 'rotation', [[0, Z], [0.5, [-6, 0, 0]], [2.2, Z]]);
	add(t, 'right_leg', 'rotation', hold([-10, 0, 0]));
	add(t, 'left_leg', 'rotation', hold([12, 0, 0]));
	anims.missiles = clip(2.2, false, t);
}
// strafing run: flat out, arms forward, wings swept, a slight roll
{
	const t = {};
	add(t, 'root', 'rotation', [[0, [0, 0, -8]], [0.5, [0, 0, 8]], [1, [0, 0, -8]]]);
	add(t, 'torso', 'rotation', hold([60, 0, 0]));
	add(t, 'hips', 'rotation', hold([20, 0, 0]));
	add(t, 'right_leg', 'rotation', hold([10, 0, 3]));
	add(t, 'left_leg', 'rotation', hold([14, 0, -3]));
	add(t, 'right_shin', 'rotation', hold([12, 0, 0]));
	add(t, 'left_shin', 'rotation', hold([12, 0, 0]));
	add(t, 'right_arm', 'rotation', hold([-160, 0, -12]));
	add(t, 'left_arm', 'rotation', hold([-160, 0, 12]));
	add(t, 'head', 'rotation', hold([-55, 0, 0]));
	add(t, 'left_wing', 'rotation', hold([-60, 10, 70]));
	add(t, 'right_wing', 'rotation', hold([-60, -10, -70]));
	add(t, 'left_wing_tip', 'rotation', hold([0, 0, -20]));
	add(t, 'right_wing_tip', 'rotation', hold([0, 0, 20]));
	anims.strafe = clip(1, true, t);
}
// radio call: a hand to the side of the helmet, the other arm sweeping out
{
	const t = {};
	add(t, 'right_arm', 'rotation', [[0, Z], [0.3, [-150, 0, -40]], [0.9, [-150, 0, -40]], [1.0, Z]]);
	add(t, 'right_forearm', 'rotation', [[0, Z], [0.3, [-80, 0, 0]], [0.9, [-80, 0, 0]], [1.0, Z]]);
	add(t, 'left_arm', 'rotation', [[0, Z], [0.4, [-60, -30, -50]], [0.7, [-80, -40, -60]], [1.0, Z]]);
	add(t, 'head', 'rotation', [[0, Z], [0.3, [5, -20, 10]], [0.9, [5, -20, 10]], [1.0, Z]]);
	anims.radio = clip(1.0, false, t);
}
// afterburner dive: mark (0-1.0, poised and looking down), then head-first, arms back
{
	const t = {};
	add(t, 'torso', 'rotation', [[0, Z], [1.0, [30, 0, 0]], [1.1, [80, 0, 0]], [3.0, [80, 0, 0]]]);
	add(t, 'head', 'rotation', [[0, Z], [1.0, [30, 0, 0]], [1.1, [-50, 0, 0]], [3.0, [-50, 0, 0]]]);
	add(t, 'right_arm', 'rotation', [[0, Z], [1.0, [-40, 0, 30]], [1.1, [40, 0, 20]], [3.0, [40, 0, 20]]]);
	add(t, 'left_arm', 'rotation', [[0, Z], [1.0, [-40, 0, -30]], [1.1, [40, 0, -20]], [3.0, [40, 0, -20]]]);
	add(t, 'left_wing', 'rotation', [[0, WING_OPEN_L], [1.0, [-30, 10, 110]], [1.1, [-70, 0, 25]], [3.0, [-70, 0, 25]]]);
	add(t, 'right_wing', 'rotation', [[0, WING_OPEN_R], [1.0, [-30, -10, -110]], [1.1, [-70, 0, -25]], [3.0, [-70, 0, -25]]]);
	add(t, 'right_leg', 'rotation', hold([15, 0, 0]));
	add(t, 'left_leg', 'rotation', hold([15, 0, 0]));
	anims.dive = clip(3.0, false, t);
}
// recover (grounded after a dive): one knee down, a hand on the deck, panting
{
	const t = {};
	add(t, 'hips', 'position', [[0, [0, -6, 0]], [1, [0, -6.4, 0]], [2, [0, -6, 0]]]);
	add(t, 'torso', 'rotation', [[0, [30, 0, 0]], [1, [36, 0, 0]], [2, [30, 0, 0]]]);
	add(t, 'right_leg', 'rotation', hold([-80, 0, 0]));
	add(t, 'right_shin', 'rotation', hold([80, 0, 0]));
	add(t, 'left_leg', 'rotation', hold([10, 0, 0]));
	add(t, 'left_shin', 'rotation', hold([85, 0, 0]));
	add(t, 'right_arm', 'rotation', hold([-40, 0, 10]));
	add(t, 'right_forearm', 'rotation', hold([-30, 0, 0]));
	add(t, 'left_arm', 'rotation', hold([20, 0, -20]));
	add(t, 'head', 'rotation', [[0, [-20, 0, 0]], [1, [-14, 0, 0]], [2, [-20, 0, 0]]]);
	add(t, 'left_wing', 'rotation', hold([-10, 5, 50]));
	add(t, 'right_wing', 'rotation', hold([-10, -5, -50]));
	anims.recover = clip(2, true, t);
}
// transform (phase change): arms flung out, the wings snap open in two beats, a lift
{
	const t = {};
	add(t, 'root', 'position', [[0, Z], [2, [0, 6, 0]]]);
	add(t, 'torso', 'rotation', [[0, Z], [0.5, [20, 0, 0]], [1.2, [-15, 0, 0]], [2, [5, 0, 0]]]);
	add(t, 'head', 'rotation', [[0, Z], [0.5, [25, 0, 0]], [1.2, [-30, 0, 0]], [2, Z]]);
	add(t, 'right_arm', 'rotation', [[0, Z], [0.5, [20, 0, 10]], [1.2, [-20, 0, 80]], [2, [-10, 0, 18]]]);
	add(t, 'left_arm', 'rotation', [[0, Z], [0.5, [20, 0, -10]], [1.2, [-20, 0, -80]], [2, [-10, 0, -18]]]);
	add(t, 'left_wing', 'rotation', [[0, Z], [0.8, [-10, 0, 40]], [1.0, [-10, 0, 40]], [1.3, [-25, 10, 110]], [2, WING_OPEN_L]]);
	add(t, 'right_wing', 'rotation', [[0, Z], [0.8, [-10, 0, -40]], [1.0, [-10, 0, -40]], [1.3, [-25, -10, -110]], [2, WING_OPEN_R]]);
	add(t, 'left_wing_tip', 'rotation', [[0, Z], [1.3, [0, 0, -40]], [2, TIP_OPEN_L]]);
	add(t, 'right_wing_tip', 'rotation', [[0, Z], [1.3, [0, 0, 40]], [2, TIP_OPEN_R]]);
	anims.transform = clip(2, false, t);
}
// death: thrusters cut, she staggers, sinks to her knees and topples forward
{
	const t = {};
	add(t, 'hips', 'position', [[0, Z], [0.6, [0, -1, 0]], [1.2, [0, -7, 0]], [2.0, [0, -14, -6]]]);
	add(t, 'root', 'rotation', [[0, Z], [1.2, Z], [2.0, [80, 0, 0]]]);
	add(t, 'torso', 'rotation', [[0, Z], [0.6, [-20, 10, 0]], [1.2, [25, 0, 0]], [2.0, [10, 0, 0]]]);
	add(t, 'right_leg', 'rotation', [[0, Z], [1.2, [-85, 0, 0]], [2.0, [-85, 0, 0]]]);
	add(t, 'left_leg', 'rotation', [[0, Z], [1.2, [-80, 0, 0]], [2.0, [-80, 0, 0]]]);
	add(t, 'right_shin', 'rotation', [[0, Z], [1.2, [90, 0, 0]], [2.0, [90, 0, 0]]]);
	add(t, 'left_shin', 'rotation', [[0, Z], [1.2, [90, 0, 0]], [2.0, [90, 0, 0]]]);
	add(t, 'right_arm', 'rotation', [[0, Z], [0.6, [-30, 0, 40]], [2.0, [10, 0, 15]]]);
	add(t, 'left_arm', 'rotation', [[0, Z], [0.6, [-30, 0, -40]], [2.0, [10, 0, -15]]]);
	add(t, 'head', 'rotation', [[0, Z], [0.6, [-30, 0, 0]], [2.0, [30, 0, 0]]]);
	add(t, 'left_wing', 'rotation', [[0, Z], [2.0, [10, 0, 30]]]);
	add(t, 'right_wing', 'rotation', [[0, Z], [2.0, [10, 0, -30]]]);
	anims.death = clip(2.0, false, t);
}
const out = { format_version: '1.8.0', animations: {} };
for (const [k, v] of Object.entries(anims)) out.animations['animation.admiral_kestrel.' + k] = v;
json(path.join(A, 'animations/admiral_kestrel.animation.json'), out);
console.log('kestrel: ' + bones.length + ' bones, ' + Object.keys(anims).length + ' clips');
