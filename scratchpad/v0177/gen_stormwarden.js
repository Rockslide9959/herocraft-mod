// v0.17.7 Trial of Asgard boss: The Stormwarden's GeckoLib assets.
//   - geo/stormwarden.geo.json         (a towering Asgardian construct: horned crested helm with a lightning visor slit, a
//                                        storm core in a layered chestplate, heavy gold-trimmed pauldrons, a rune tabard, armoured
//                                        legs and sabatons, gauntlets, and a great rune-carved maul in the right hand)
//   - animations/stormwarden.animation.json (idle, walk, skyfall, charge, slam, slam_double, spirits, surge, form, swing, death)
//   - textures/entity/stormwarden.png + _glowmask.png (box-UV atlas; dark Uru plate with bevels/rivets/wear, gold trim, glowing
//                                        runes, visor and core in the glowmask)
// Usage (repo root): node scratchpad/v0177/gen_stormwarden.js
// Clip timings match StormwardenBoss: skyfall 25+12 ticks, charge 16+16+18, slam 24+18 (double +14), spirits 20+12, surge 40, form 40.
//
// Model space: y up, 16 units = 1 block, faces -Z, +X is its left. +rx pitches the front down; an arm hanging down is raised
// forward with -rx; +rz lowers the +X end.
const fs = require('fs');
const path = require('path');
const { encode } = require('../pngkit');

const A = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero');
const json = (p, o) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, JSON.stringify(o, null, 1) + '\n'); };

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
function cube(b, origin, size, mat, deco = {}) {
	b.cubes.push({ origin, size, mat, deco });
}

// ---------------------------------------------------------------- body (about 58 units = 3.6 blocks)
bone('root', null, [0, 0, 0]);
bone('hips', 'root', [0, 24, 0]);
cube(byName.hips, [-7, 22, -4], [14, 5, 8], 'uru_d');
cube(byName.hips, [-7.5, 25, -4.5], [15, 2, 9], 'gold', { band: true });           // belt
cube(byName.hips, [-3, 11, -5], [6, 13, 1], 'cloth', { rune: true });            // tabard, front
cube(byName.hips, [-3, 12, 4], [6, 12, 1], 'cloth');                              // tabard, back
for (const s of [-1, 1]) cube(byName.hips, [s > 0 ? 5 : -8, 17, -4], [3, 7, 8], 'uru', { trim: true }); // tassets

for (const [side, s] of [['right', -1], ['left', 1]]) {
	const x = s * 4;
	const thigh = bone(`${side}_leg`, 'hips', [x, 23, 0]);
	cube(thigh, [x - 3.5, 12, -3.5], [7, 11, 7], 'uru_d');
	cube(thigh, [x - 3.8, 13, -4.2], [7, 8, 1], 'uru', { plate: true });
	const shin = bone(`${side}_shin`, `${side}_leg`, [x, 12, 0]);
	cube(shin, [x - 3.5, 3, -3.5], [7, 9, 7], 'uru');
	cube(shin, [x - 2.5, 9, -4.6], [5, 4, 2], 'gold', { knee: true });                // knee cop
	cube(shin, [x - 4, 0, -5.5], [8, 3, 9], 'uru_d', { sabaton: true });            // sabaton
	cube(shin, [x - 1, 4, -4.2], [2, 4, 1], 'rune');                                   // shin rune
}

bone('torso', 'hips', [0, 27, 0]);
cube(byName.torso, [-6.5, 27, -4], [13, 6, 8], 'uru_d');                            // waist
cube(byName.torso, [-9, 33, -5], [18, 12, 10], 'uru');                              // chest
cube(byName.torso, [-7, 34, -6], [14, 10, 1], 'uru_hi', { chest: true });          // breastplate
cube(byName.torso, [-2, 37, -6.8], [4, 4, 1], 'core');                              // the storm core
cube(byName.torso, [-3, 36, -6.4], [6, 6, 1], 'gold', { ring: true });             // its gold mount
cube(byName.torso, [-7, 28, -4.8], [14, 5, 1], 'uru_d', { ribs: true });           // ab plates
cube(byName.torso, [-7, 34, 5], [14, 10, 1], 'uru_d', { back: true });             // backplate
cube(byName.torso, [-6, 45, -4], [12, 2, 8], 'gold', { band: true });              // gorget

bone('head', 'torso', [0, 47, 0]);
cube(byName.head, [-4.5, 47, -5], [9, 9, 9], 'helm', { helm: true });
cube(byName.head, [-4, 50, -5.6], [8, 1.5, 1], 'visor');                            // lightning visor slit
cube(byName.head, [-1, 47, -5.8], [2, 3, 1], 'uru_d');                              // nasal / chin guard
cube(byName.head, [-1, 56, -5], [2, 3, 10], 'gold', { crest: true });              // crest
for (const s of [-1, 1]) {
	const horn = bone(`${s > 0 ? 'left' : 'right'}_horn`, 'head', [s * 4.5, 53, -1], [0, 0, s * -35]);
	cube(horn, [s > 0 ? 4.5 : -6.5, 52, -2], [2, 2, 2], 'gold');
	cube(horn, [s > 0 ? 6.5 : -8.5, 53, -1.5], [2, 5, 1.5], 'horn');
	cube(horn, [s > 0 ? 7 : -8, 58, -1.2], [1, 3, 1], 'horn');
}

for (const [side, s] of [['right', -1], ['left', 1]]) {
	const x = s * 11.5;
	const sh = bone(`${side}_shoulder`, 'torso', [x, 43, 0]);
	cube(sh, [x - 4.5, 41, -5], [9, 6, 10], 'uru', { trim: true });
	cube(sh, [x - 4, 47, -4.5], [8, 1, 9], 'gold');
	cube(sh, [s > 0 ? x + 3.5 : x - 5.5, 38, -4.5], [2, 4, 9], 'uru_d');             // lower lame
	const arm = bone(`${side}_arm`, 'torso', [x, 42, 0]);
	cube(arm, [x - 3, 33, -3], [6, 9, 6], 'uru_d');
	const fore = bone(`${side}_forearm`, `${side}_arm`, [x, 33, 0]);
	cube(fore, [x - 3.5, 23, -3.5], [7, 10, 7], 'uru', { bands: true });
	cube(fore, [s > 0 ? x + 3.5 : x - 4.5, 25, -2], [1, 6, 4], 'rune');               // vambrace rune
	cube(fore, [x - 3, 18, -3], [6, 5, 6], 'gauntlet');
}

// the great maul, held upright in the right hand (grip at the hand, head above the shoulder)
bone('maul', 'right_forearm', [-11.5, 20, 0]);
cube(byName.maul, [-12.5, 8, -1], [2, 36, 2], 'haft', { wraps: true });
cube(byName.maul, [-13, 6, -1.5], [3, 2, 3], 'gold');                                 // pommel
cube(byName.maul, [-17.5, 42, -4.5], [12, 9, 9], 'uru', { maulhead: true });         // head
cube(byName.maul, [-18, 43, -5], [13, 2, 10], 'gold', { band: true });              // head bands
cube(byName.maul, [-18, 49, -5], [13, 1, 10], 'gold');
cube(byName.maul, [-14, 44.5, -5.4], [5, 4, 1], 'rune');                             // face rune
cube(byName.maul, [-14, 44.5, 4.4], [5, 4, 1], 'rune');

// ---------------------------------------------------------------- box-UV packing
const TEX_W = 256;
const all = [];
for (const b of bones) for (const c of b.cubes) all.push(c);
const regions = new Map();
const order = [...all].sort((a, b) => (b.size[2] + b.size[1]) - (a.size[2] + a.size[1]) || (b.size[0] - a.size[0]));
let ux = 0, uy = 0, shelf = 0;
const ceil = v => Math.ceil(v);
for (const c of order) {
	const key = c.size.join('x') + c.mat + JSON.stringify(c.deco);
	if (regions.has(key)) { c.uv = regions.get(key).uv; continue; }
	const [w, h, d] = c.size.map(ceil);
	const rw = 2 * (w + d), rh = d + h;
	if (ux + rw > TEX_W) { ux = 0; uy += shelf; shelf = 0; }
	const r = { uv: [ux, uy], w, h, d, mat: c.mat, deco: c.deco };
	regions.set(key, r);
	c.uv = r.uv;
	ux += rw;
	shelf = Math.max(shelf, rh);
}
const TEX_H = Math.max(64, Math.ceil((uy + shelf) / 16) * 16);
console.log(`stormwarden texture ${TEX_W}x${TEX_H}, ${regions.size} regions for ${all.length} cubes`);

json(path.join(A, 'geo/stormwarden.geo.json'), {
	format_version: '1.12.0',
	'minecraft:geometry': [{
		description: { identifier: 'geometry.stormwarden', texture_width: TEX_W, texture_height: TEX_H,
			visible_bounds_width: 6, visible_bounds_height: 6, visible_bounds_offset: [0, 2.5, 0] },
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

// ---------------------------------------------------------------- texture
let seed = 4111;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const shade = (c, f) => c.map(v => clamp(v * f));
const mix = (a, b, t) => a.map((v, i) => clamp(v + (b[i] - v) * t));
const tex = Buffer.alloc(TEX_W * TEX_H * 4);
const glow = Buffer.alloc(TEX_W * TEX_H * 4);
const put = (buf, x, y, c, a = 255) => { if (x < 0 || y < 0 || x >= TEX_W || y >= TEX_H) return; const i = (y * TEX_W + x) * 4; buf[i] = clamp(c[0]); buf[i + 1] = clamp(c[1]); buf[i + 2] = clamp(c[2]); buf[i + 3] = a; };

const URU = [70, 76, 90], URU_D = [44, 48, 58], URU_HI = [96, 104, 122], GOLD = [214, 168, 64], GOLD_D = [150, 104, 34],
	CLOTH = [34, 46, 86], BOLT = [170, 225, 255], HOT = [235, 248, 255], HORN = [222, 214, 190], LEATHER = [78, 52, 34], SILVER = [180, 188, 204];
const LIGHT = { up: 1.18, down: 0.6, north: 1.0, south: 0.8, east: 0.88, west: 0.88 };

function faces(r) {
	const { w, h, d } = r;
	const [u, v] = r.uv;
	return [['up', u + d, v, w, d], ['down', u + d + w, v, w, d], ['east', u, v + d, d, h], ['north', u + d, v + d, w, h],
		['west', u + d + w, v + d, d, h], ['south', u + 2 * d + w, v + d, w, h]];
}
function metal(base, fu, fv, fw, fh, face, opts = {}) {
	const grad = face === 'up' || face === 'down' ? 1 : 1.1 - 0.2 * (fv / Math.max(1, fh - 1));
	let c = shade(base, LIGHT[face] * grad);
	c = c.map(x => x + (rnd() - 0.5) * (opts.grain ?? 8));
	if (fw > 2 && fh > 2) {
		if (fv === 0 || fu === 0) c = mix(c, [255, 255, 255], 0.15);
		if (fv === fh - 1 || fu === fw - 1) c = shade(c, 0.7);
	}
	if (rnd() < (opts.wear ?? 0.03)) c = mix(c, SILVER, 0.5);
	return c;
}
// a carved rune: a few strokes per 3x4 cell
const RUNES = [[[1, 0], [1, 1], [1, 2], [1, 3], [0, 1], [2, 2]], [[0, 0], [1, 1], [2, 2], [1, 3], [0, 3]], [[0, 0], [0, 1], [0, 2], [0, 3], [1, 1], [2, 0]],
	[[2, 0], [1, 1], [0, 2], [1, 2], [2, 3]], [[1, 0], [0, 1], [2, 1], [1, 2], [1, 3]]];
function runeAt(fu, fv, salt) {
	const cell = Math.floor(fu / 3) + salt;
	const pat = RUNES[Math.abs(cell) % RUNES.length];
	return pat.some(([x, y]) => x === fu % 3 && y === fv % 4);
}

for (const r of regions.values()) {
	for (const [face, u0, v0, fw, fh] of faces(r)) {
		for (let fv = 0; fv < fh; fv++) for (let fu = 0; fu < fw; fu++) {
			const x = u0 + fu, y = v0 + fv;
			let c;
			let g = null;
			const d = r.deco;
			const side = face !== 'up' && face !== 'down';
			switch (r.mat) {
				case 'uru':
					c = metal(URU, fu, fv, fw, fh, face);
					if (fw >= 4 && fh >= 4 && ((fu === 1 && fv === 1) || (fu === fw - 2 && fv === 1))) c = mix(c, GOLD, 0.7); // gold rivets
					if (d.trim && side && fv === fh - 1) c = shade(GOLD, LIGHT[face]);
					if (d.trim && face === 'up' && (fu === 0 || fu === fw - 1)) c = shade(GOLD, 1.1);
					if (d.bands && side && (fv === 1 || fv === fh - 2)) c = shade(GOLD_D, LIGHT[face] * 1.1);
					if (d.plate && face === 'north' && fu === Math.floor(fw / 2)) c = shade(c, 0.8);
					if (d.maulhead && side) {
						if (fv === 0 || fv === fh - 1) c = shade(GOLD, LIGHT[face]);
						if ((face === 'east' || face === 'west') && fv >= 2 && fv <= fh - 3 && runeAt(fu, fv - 2, 3)) { c = BOLT; g = BOLT; }
					}
					if (d.maulhead && face === 'up' && (fu + fv) % 4 === 0) c = shade(c, 0.85);
					break;
				case 'uru_d':
					c = metal(URU_D, fu, fv, fw, fh, face, { wear: 0.05 });
					if (d.ribs && face === 'north' && fv % 2 === 1) c = shade(c, 0.7);
					if (d.sabaton && face === 'north' && fv === 0) c = shade(GOLD, 0.95);
					if (d.sabaton && fv === fh - 1 && side) c = [24, 24, 28];
					if (d.back && face === 'south' && Math.abs(fu - (fw - 1) / 2) < 1 && fv > 1 && fv < fh - 2) { c = shade(BOLT, 0.8); g = BOLT; } // spine of light
					break;
				case 'uru_hi': {
					const gloss = Math.abs((fu - fv) - Math.floor(fw / 3)) < 1 ? 0.3 : 0;
					c = mix(shade(URU_HI, LIGHT[face] * (1.1 - 0.15 * fv / Math.max(1, fh - 1))), [200, 210, 230], gloss);
					if (fv === 0 || fu === 0) c = mix(c, [220, 228, 240], 0.35);
					if (fv === fh - 1 || fu === fw - 1) c = shade(c, 0.7);
					if (d.chest && face === 'north') {
						// a gold winged chevron round the core and two lightning grooves running down
						const cx = (fw - 1) / 2;
						const dx = Math.abs(fu - cx);
						if (Math.abs(fv - (1 + dx * 0.55)) < 0.6 && dx < fw / 2 - 0.5) c = shade(GOLD, 1.05);
						if ((Math.abs(fu - 2) < 0.6 || Math.abs(fu - (fw - 3)) < 0.6) && fv > 3) { c = shade(BOLT, 0.9); g = BOLT; }
					}
					break;
				}
				case 'gold':
					c = metal(GOLD, fu, fv, fw, fh, face, { grain: 14, wear: 0.0 });
					if (fv === 0 && side) c = mix(c, [255, 240, 190], 0.4);
					if (d.band && side && fu % 4 === 2) c = shade(GOLD_D, LIGHT[face]);
					if (d.knee && face === 'north' && fu === Math.floor(fw / 2)) { c = BOLT; g = BOLT; }
					if (d.crest && (face === 'east' || face === 'west') && fv === 1) c = shade(GOLD_D, 0.9);
					break;
				case 'cloth': {
					c = shade(CLOTH, LIGHT[face]).map(v => v + (rnd() - 0.5) * 7);
					if (fv % 3 === 0) c = shade(c, 0.85);
					if (side && (fu === 0 || fu === fw - 1)) c = shade(GOLD_D, LIGHT[face]);
					if (side && fv === fh - 1 && fu % 2 === 0) c = shade(c, 0.6); // ragged hem
					if (d.rune && face === 'north' && fu >= 1 && fu <= fw - 2 && fv >= 2 && fv <= fh - 3 && runeAt(fu - 1, fv - 2, 1)) { c = BOLT; g = BOLT; }
					break;
				}
				case 'helm': {
					const top = face === 'up';
					c = metal(URU, fu, fv, fw, fh, face);
					if (top && Math.abs(fu - (fw - 1) / 2) < 1.6) c = shade(GOLD, 1.1);
					if (side && fv === fh - 1) c = shade(GOLD, LIGHT[face]);
					if (face === 'north' && fv >= 5) c = shade(URU_D, LIGHT[face]); // face plate, darker below the visor
					if (face === 'north' && fv >= 5 && fu % 2 === 0) c = shade(c, 0.75); // breathing slots
					break;
				}
				case 'visor': {
					c = face === 'north' ? (fu % 3 === 1 ? HOT : BOLT) : shade(BOLT, 0.7);
					g = c;
					break;
				}
				case 'core': {
					const dc = Math.hypot(fu - (fw - 1) / 2, fv - (fh - 1) / 2);
					c = dc < 1 ? [255, 255, 255] : mix(HOT, BOLT, Math.min(1, dc / 2));
					g = c;
					break;
				}
				case 'rune':
					c = (fu + fv) % 3 === 0 ? HOT : BOLT;
					if (!side) c = shade(BOLT, 0.8);
					g = c;
					break;
				case 'horn':
					c = shade(HORN, LIGHT[face]).map(v => v + (rnd() - 0.5) * 10);
					if (fv % 2 === 0) c = shade(c, 0.88);
					break;
				case 'gauntlet':
					c = metal(URU_D, fu, fv, fw, fh, face);
					if (face === 'north' && fv <= 1) c = shade(GOLD, LIGHT[face]);
					if (face === 'north' && fv === 2 && fu % 2 === 0) c = shade(URU, 1.1); // knuckles
					break;
				case 'haft':
					c = shade(LEATHER, LIGHT[face]).map(v => v + (rnd() - 0.5) * 8);
					if (d.wraps && fv % 4 === 0) c = shade(c, 0.65);
					if (d.wraps && fv % 9 === 4) c = shade(GOLD_D, LIGHT[face]);
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
fs.writeFileSync(path.join(A, 'textures/entity/stormwarden.png'), encode(TEX_W, TEX_H, tex));
fs.writeFileSync(path.join(A, 'textures/entity/stormwarden_glowmask.png'), encode(TEX_W, TEX_H, glow));

// ---------------------------------------------------------------- animations
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
const anims = {};

// idle: a slow heavy breath, the maul upright at its side, the free hand loose; the head turns as if scanning the storm
{
	const t = {};
	add(t, 'torso', 'rotation', [[0, Z], [2, [-2, 0, 0]], [4, Z]]);
	add(t, 'hips', 'position', [[0, Z], [2, [0, -0.4, 0]], [4, Z]]);
	add(t, 'head', 'rotation', [[0, Z], [1.3, [3, -12, 0]], [2.6, [0, 10, 0]], [4, Z]]);
	add(t, 'right_arm', 'rotation', hold([-10, 0, 6]));
	add(t, 'right_forearm', 'rotation', hold([-28, 0, 0]));
	add(t, 'maul', 'rotation', hold([22, 0, 0]));
	add(t, 'left_arm', 'rotation', [[0, [0, 0, -8]], [2, [-4, 0, -10]], [4, [0, 0, -8]]]);
	add(t, 'left_forearm', 'rotation', hold([-12, 0, 0]));
	anims.idle = clip(4, true, t);
}
// walk: a heavy stride
{
	const t = {};
	const L = 1.3;
	add(t, 'right_leg', 'rotation', [[0, [-24, 0, 0]], [L / 2, [24, 0, 0]], [L, [-24, 0, 0]]]);
	add(t, 'left_leg', 'rotation', [[0, [24, 0, 0]], [L / 2, [-24, 0, 0]], [L, [24, 0, 0]]]);
	add(t, 'right_shin', 'rotation', [[0, [8, 0, 0]], [L / 4, [36, 0, 0]], [L / 2, [4, 0, 0]], [L, [8, 0, 0]]]);
	add(t, 'left_shin', 'rotation', [[0, [4, 0, 0]], [L / 2, [8, 0, 0]], [3 * L / 4, [36, 0, 0]], [L, [4, 0, 0]]]);
	add(t, 'right_arm', 'rotation', [[0, [-4, 0, 6]], [L / 2, [-16, 0, 6]], [L, [-4, 0, 6]]]);
	add(t, 'right_forearm', 'rotation', hold([-28, 0, 0]));
	add(t, 'maul', 'rotation', hold([22, 0, 0]));
	add(t, 'left_arm', 'rotation', [[0, [-18, 0, -8]], [L / 2, [18, 0, -8]], [L, [-18, 0, -8]]]);
	add(t, 'torso', 'rotation', [[0, [3, 4, 0]], [L / 2, [3, -4, 0]], [L, [3, 4, 0]]]);
	add(t, 'hips', 'position', [[0, Z], [L / 4, [0, 0.8, 0]], [L / 2, Z], [3 * L / 4, [0, 0.8, 0]], [L, Z]]);
	anims.walk = clip(L, true, t);
}
// skyfall: the maul thrust to the sky (0-0.5), held crackling (to 1.25), brought down as the bolts land, recover
{
	const t = {};
	add(t, 'right_arm', 'rotation', [[0, [-10, 0, 6]], [0.5, [-170, 0, -6]], [1.2, [-172, 0, -6]], [1.32, [-60, 0, 8]], [1.85, [-10, 0, 6]]]);
	add(t, 'right_forearm', 'rotation', [[0, [-28, 0, 0]], [0.5, [0, 0, 0]], [1.32, [-20, 0, 0]], [1.85, [-28, 0, 0]]]);
	add(t, 'maul', 'rotation', [[0, [22, 0, 0]], [0.5, [0, 0, 0]], [1.85, [22, 0, 0]]]);
	add(t, 'left_arm', 'rotation', [[0, [0, 0, -8]], [0.5, [-30, 0, -60]], [1.25, [-30, 0, -62]], [1.85, [0, 0, -8]]]);
	add(t, 'head', 'rotation', [[0, Z], [0.5, [-30, 0, 0]], [1.25, [-30, 0, 0]], [1.4, [12, 0, 0]], [1.85, Z]]);
	add(t, 'torso', 'rotation', [[0, Z], [0.5, [-8, 0, 0]], [1.25, [-8, 0, 0]], [1.35, [10, 0, 0]], [1.85, Z]]);
	add(t, 'hips', 'position', [[0, Z], [1.25, Z], [1.35, [0, -1.5, 0]], [1.85, Z]]);
	anims.skyfall = clip(1.85, false, t);
}
// charge: low crouch, shoulder forward (0-0.8), the run (0.8-1.6), the stagger (1.6-2.5) -- bent over, breathing hard
{
	const t = {};
	add(t, 'torso', 'rotation', [[0, Z], [0.7, [30, -20, 0]], [0.8, [38, -24, 0]], [1.6, [38, -24, 0]], [1.75, [45, 0, 0]], [2.3, [40, 0, 0]], [2.5, Z]]);
	add(t, 'hips', 'position', [[0, Z], [0.7, [0, -3, 0]], [1.6, [0, -2, 0]], [1.75, [0, -4, 0]], [2.3, [0, -4, 0]], [2.5, Z]]);
	add(t, 'right_leg', 'rotation', [[0, Z], [0.7, [-40, 0, 0]], [0.8, [-30, 0, 0]], [1.0, [30, 0, 0]], [1.2, [-30, 0, 0]], [1.4, [30, 0, 0]], [1.6, [-20, 0, 0]], [1.75, [-50, 0, 0]], [2.5, Z]]);
	add(t, 'left_leg', 'rotation', [[0, Z], [0.7, [20, 0, 0]], [0.8, [30, 0, 0]], [1.0, [-30, 0, 0]], [1.2, [30, 0, 0]], [1.4, [-30, 0, 0]], [1.6, [20, 0, 0]], [1.75, [10, 0, 0]], [2.5, Z]]);
	add(t, 'right_shin', 'rotation', [[0, Z], [0.7, [60, 0, 0]], [0.8, [20, 0, 0]], [1.75, [70, 0, 0]], [2.5, Z]]);
	add(t, 'left_shin', 'rotation', [[0, Z], [0.7, [30, 0, 0]], [0.8, [30, 0, 0]], [1.75, [40, 0, 0]], [2.5, Z]]);
	add(t, 'left_arm', 'rotation', [[0, Z], [0.7, [-60, 30, -20]], [1.6, [-70, 30, -20]], [1.75, [-20, 0, -20]], [2.5, [0, 0, -8]]]);
	add(t, 'right_arm', 'rotation', [[0, [-10, 0, 6]], [0.7, [30, 0, 20]], [1.6, [30, 0, 20]], [1.75, [-20, 0, 10]], [2.5, [-10, 0, 6]]]);
	add(t, 'maul', 'rotation', [[0, [22, 0, 0]], [0.7, [60, 0, 0]], [1.6, [60, 0, 0]], [2.5, [22, 0, 0]]]);
	add(t, 'head', 'rotation', [[0, Z], [0.7, [-30, 15, 0]], [1.6, [-30, 15, 0]], [1.75, [10, 0, 0]], [2.5, Z]]);
	anims.charge = clip(2.5, false, t);
}
function slam(len, second) {
	const t = {};
	// both hands on the haft overhead (0-1.2), the slam (1.2-1.3), recover; a double raises it again and slams once more
	const R = [[0, [-10, 0, 6]], [1.1, [-175, 0, 20]], [1.2, [-178, 0, 20]], [1.3, [-55, 0, 18]]];
	const L = [[0, [0, 0, -8]], [1.1, [-175, 0, -20]], [1.2, [-178, 0, -20]], [1.3, [-60, 0, -14]]];
	const TOR = [[0, Z], [1.1, [-14, 0, 0]], [1.2, [-16, 0, 0]], [1.3, [34, 0, 0]]];
	const HIP = [[0, Z], [1.1, [0, 1, 0]], [1.3, [0, -5, 0]]];
	if (second) {
		R.push([1.6, [-170, 0, 20]], [1.9, [-178, 0, 20]], [2.0, [-55, 0, 18]], [len, [-10, 0, 6]]);
		L.push([1.6, [-170, 0, -20]], [1.9, [-178, 0, -20]], [2.0, [-60, 0, -14]], [len, [0, 0, -8]]);
		TOR.push([1.6, [-10, 0, 0]], [1.9, [-16, 0, 0]], [2.0, [36, 0, 0]], [len, Z]);
		HIP.push([1.6, [0, 0, 0]], [2.0, [0, -5, 0]], [len - 0.3, [0, -5, 0]], [len, Z]);
	} else {
		R.push([len, [-10, 0, 6]]);
		L.push([len, [0, 0, -8]]);
		TOR.push([len - 0.4, [30, 0, 0]], [len, Z]);
		HIP.push([len - 0.4, [0, -5, 0]], [len, Z]);
	}
	add(t, 'right_arm', 'rotation', R);
	add(t, 'left_arm', 'rotation', L);
	add(t, 'torso', 'rotation', TOR);
	add(t, 'hips', 'position', HIP);
	add(t, 'right_forearm', 'rotation', hold([-8, 0, 0]));
	add(t, 'left_forearm', 'rotation', hold([-8, 0, 0]));
	add(t, 'maul', 'rotation', hold([0, 0, 0]));
	add(t, 'right_leg', 'rotation', [[0, Z], [1.2, [-20, 0, 6]], [1.3, [-34, 0, 8]], [len, Z]]);
	add(t, 'left_leg', 'rotation', [[0, Z], [1.2, [10, 0, -6]], [1.3, [18, 0, -8]], [len, Z]]);
	add(t, 'right_shin', 'rotation', [[0, Z], [1.3, [40, 0, 0]], [len, Z]]);
	add(t, 'head', 'rotation', [[0, Z], [1.1, [-25, 0, 0]], [1.3, [20, 0, 0]], [len, Z]]);
	return clip(len, false, t);
}
anims.slam = slam(2.1, false);
anims.slam_double = slam(2.8, true);
// spirits: arms flung wide, palms up, rising on its toes as lightning gathers (0-1.0), a release, recover
{
	const t = {};
	add(t, 'left_arm', 'rotation', [[0, [0, 0, -8]], [0.8, [-40, 0, -95]], [1.0, [-50, 0, -105]], [1.1, [-20, 0, -70]], [1.6, [0, 0, -8]]]);
	add(t, 'right_arm', 'rotation', [[0, [-10, 0, 6]], [0.8, [-40, 0, 70]], [1.0, [-50, 0, 80]], [1.1, [-20, 0, 50]], [1.6, [-10, 0, 6]]]);
	add(t, 'torso', 'rotation', [[0, Z], [0.8, [-12, 0, 0]], [1.0, [-15, 0, 0]], [1.1, [5, 0, 0]], [1.6, Z]]);
	add(t, 'head', 'rotation', [[0, Z], [0.8, [-35, 0, 0]], [1.1, [5, 0, 0]], [1.6, Z]]);
	add(t, 'root', 'position', [[0, Z], [0.8, [0, 2, 0]], [1.0, [0, 2.4, 0]], [1.1, Z]]);
	anims.spirits = clip(1.6, false, t);
}
// surge (phase 2): lightning pours in -- it buckles to one knee, then stands, arms thrown out, head back, roaring
{
	const t = {};
	add(t, 'hips', 'position', [[0, Z], [0.4, [0, -7, 0]], [1.2, [0, -7, 0]], [1.6, [0, 1, 0]], [2.0, Z]]);
	add(t, 'right_leg', 'rotation', [[0, Z], [0.4, [-80, 0, 0]], [1.2, [-80, 0, 0]], [1.6, Z]]);
	add(t, 'right_shin', 'rotation', [[0, Z], [0.4, [85, 0, 0]], [1.2, [85, 0, 0]], [1.6, Z]]);
	add(t, 'left_leg', 'rotation', [[0, Z], [0.4, [10, 0, 0]], [1.2, [10, 0, 0]], [1.6, Z]]);
	add(t, 'left_shin', 'rotation', [[0, Z], [0.4, [80, 0, 0]], [1.2, [80, 0, 0]], [1.6, Z]]);
	add(t, 'torso', 'rotation', [[0, Z], [0.4, [35, 0, 0]], [1.2, [30, 0, 0]], [1.6, [-25, 0, 0]], [2.0, [-10, 0, 0]]]);
	add(t, 'head', 'rotation', [[0, Z], [0.4, [30, 0, 0]], [1.2, [25, 0, 0]], [1.6, [-45, 0, 0]], [2.0, [-20, 0, 0]]]);
	add(t, 'left_arm', 'rotation', [[0, [0, 0, -8]], [0.4, [20, 0, -20]], [1.2, [20, 0, -20]], [1.6, [-40, 0, -110]], [2.0, [-20, 0, -60]]]);
	add(t, 'right_arm', 'rotation', [[0, [-10, 0, 6]], [0.4, [-20, 0, 10]], [1.2, [-20, 0, 10]], [1.6, [-160, 0, 10]], [2.0, [-60, 0, 10]]]);
	add(t, 'maul', 'rotation', hold([0, 0, 0]));
	anims.surge = clip(2.0, false, t);
}
// form: the armour assembles out of the storm -- risen from a crouch, pieces settling, a final flex
{
	const t = {};
	add(t, 'root', 'position', [[0, [0, -10, 0]], [0.8, [0, -4, 0]], [1.4, [0, 0.5, 0]], [2.0, Z]]);
	add(t, 'hips', 'position', [[0, [0, -6, 0]], [1.0, [0, -4, 0]], [1.4, Z]]);
	add(t, 'torso', 'rotation', [[0, [50, 0, 0]], [1.0, [30, 0, 0]], [1.5, [-12, 0, 0]], [2.0, Z]]);
	add(t, 'head', 'rotation', [[0, [40, 0, 0]], [1.2, [20, 0, 0]], [1.6, [-25, 0, 0]], [2.0, Z]]);
	add(t, 'right_leg', 'rotation', [[0, [-70, 0, 0]], [1.0, [-50, 0, 0]], [1.5, Z]]);
	add(t, 'right_shin', 'rotation', [[0, [90, 0, 0]], [1.0, [70, 0, 0]], [1.5, Z]]);
	add(t, 'left_leg', 'rotation', [[0, [-40, 0, 0]], [1.0, [-30, 0, 0]], [1.5, Z]]);
	add(t, 'left_shin', 'rotation', [[0, [80, 0, 0]], [1.0, [60, 0, 0]], [1.5, Z]]);
	add(t, 'left_arm', 'rotation', [[0, [30, 0, -10]], [1.2, [10, 0, -20]], [1.6, [-30, 0, -70]], [2.0, [0, 0, -8]]]);
	add(t, 'right_arm', 'rotation', [[0, [30, 0, 10]], [1.2, [10, 0, 10]], [1.6, [-120, 0, 10]], [2.0, [-10, 0, 6]]]);
	add(t, 'right_forearm', 'rotation', [[0, [-40, 0, 0]], [2.0, [-28, 0, 0]]]);
	add(t, 'maul', 'rotation', [[0, [40, 0, 0]], [1.6, [0, 0, 0]], [2.0, [22, 0, 0]]]);
	add(t, 'left_horn', 'position', [[0, [2, 3, 0]], [1.3, [0.5, 1, 0]], [1.6, Z]]);
	add(t, 'right_horn', 'position', [[0, [-2, 3, 0]], [1.3, [-0.5, 1, 0]], [1.6, Z]]);
	add(t, 'left_shoulder', 'position', [[0, [3, 2, 0]], [1.1, [1, 0.5, 0]], [1.4, Z]]);
	add(t, 'right_shoulder', 'position', [[0, [-3, 2, 0]], [1.1, [-1, 0.5, 0]], [1.4, Z]]);
	anims.form = clip(2.0, false, t);
}
// swing: a short backhand with the maul
{
	const t = {};
	add(t, 'right_arm', 'rotation', [[0, [-10, 0, 6]], [0.15, [-120, -30, 40]], [0.3, [-60, 40, -10]], [0.5, [-10, 0, 6]]]);
	add(t, 'torso', 'rotation', [[0, Z], [0.15, [0, 20, 0]], [0.3, [10, -25, 0]], [0.5, Z]]);
	add(t, 'maul', 'rotation', [[0, [22, 0, 0]], [0.15, [0, 0, 0]], [0.5, [22, 0, 0]]]);
	anims.swing = clip(0.5, false, t);
}
// death: the lightning goes out -- it staggers, drops the maul, falls to its knees and slumps, the head bowed
{
	const t = {};
	add(t, 'hips', 'position', [[0, Z], [0.5, [0, -1, 1]], [1.2, [0, -9, 0]], [2.0, [0, -10, 0]]]);
	add(t, 'torso', 'rotation', [[0, Z], [0.5, [-20, 10, 0]], [1.2, [30, 0, 0]], [2.0, [40, 0, 0]]]);
	add(t, 'head', 'rotation', [[0, Z], [0.5, [-30, 0, 0]], [2.0, [45, 0, 0]]]);
	add(t, 'right_leg', 'rotation', [[0, Z], [1.2, [-85, 0, 0]], [2.0, [-88, 0, 0]]]);
	add(t, 'left_leg', 'rotation', [[0, Z], [1.2, [-80, 0, 0]], [2.0, [-82, 0, 0]]]);
	add(t, 'right_shin', 'rotation', [[0, Z], [1.2, [90, 0, 0]], [2.0, [90, 0, 0]]]);
	add(t, 'left_shin', 'rotation', [[0, Z], [1.2, [90, 0, 0]], [2.0, [90, 0, 0]]]);
	add(t, 'right_arm', 'rotation', [[0, [-10, 0, 6]], [0.5, [-40, 0, 40]], [2.0, [15, 0, 10]]]);
	add(t, 'left_arm', 'rotation', [[0, [0, 0, -8]], [0.5, [-30, 0, -40]], [2.0, [15, 0, -10]]]);
	add(t, 'maul', 'rotation', [[0, [22, 0, 0]], [0.6, [60, 0, 0]], [1.2, [100, 0, 0]], [2.0, [100, 0, 0]]]);
	anims.death = clip(2.0, false, t);
}
const out = { format_version: '1.8.0', animations: {} };
for (const [k, v] of Object.entries(anims)) out.animations['animation.stormwarden.' + k] = v;
json(path.join(A, 'animations/stormwarden.animation.json'), out);
console.log('stormwarden: ' + bones.length + ' bones, ' + Object.keys(anims).length + ' clips');
