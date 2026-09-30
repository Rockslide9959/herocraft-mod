// v0.14.4: the Grave Champion Head and the Empowered Zombie Head (boss trophy) as real, wearable, placeable heads.
// Generates, from one element list per head: the 64x64 texture, the floor + wall block models, the item models (with
// display transforms derived from CustomHeadLayer -- see the HEAD comment below), the blockstates and the loot tables.
// Run from the repo root: node scratchpad/gen_grave_heads_v0144.js
//
// Conventions
// - 1 texel per model unit (vanilla skull density); glow decals are denser. The texture is 64x64, so 4 texels = 1 UV unit.
// - Faces are painted "as the viewer sees them" (left of the texture rect = viewer's left), which is what vanilla's
//   default face UV orientation gives for an unrotated face.
// - Any face with tintindex 0 is a glow decal: tinted by the power colour (TrophyHeadColors) and drawn full-bright
//   by TrophyHeadModels (FRAPI). Glow decals may use alpha 0, so the blocks render in the cutout layer.
const fs = require('fs');
const path = require('path');
const png = require('./pnglib.js');

const ROOT = path.join(__dirname, '../src/main/resources/');
const ASSETS = ROOT + 'assets/projecthero/';
const DATA = ROOT + 'data/projecthero/';
const S = 64; // texture size
const TPU = S / 16; // texels per UV unit

// ---------------------------------------------------------------- tiny helpers
function rng(seed) { return () => { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed / 4294967296; }; }
const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));
const mix = (a, b, t) => [0, 1, 2].map(i => a[i] + (b[i] - a[i]) * t);
function jit(c, r, amt) { const d = (r() - 0.5) * 2 * amt; return [clamp(c[0] + d), clamp(c[1] + d), clamp(c[2] + d * 0.9), 255]; }

class Atlas {
	constructor() { this.data = Buffer.alloc(S * S * 4); this.cx = 0; this.cy = 0; this.rowH = 0; this.used = new Uint8Array(S * S); }
	alloc(w, h) {
		if (this.cx + w > S) { this.cx = 0; this.cy += this.rowH + 1; this.rowH = 0; }
		if (this.cy + h > S) throw new Error('texture atlas overflow');
		const r = { x: this.cx, y: this.cy, w, h };
		this.cx += w + 1; this.rowH = Math.max(this.rowH, h);
		return r;
	}
	put(x, y, c) { const i = (y * S + x) * 4; this.data[i] = c[0]; this.data[i + 1] = c[1]; this.data[i + 2] = c[2]; this.data[i + 3] = c.length > 3 ? c[3] : 255; this.used[y * S + x] = 1; }
	// copy each opaque edge texel into the empty 1-texel gutter around it so mipmaps don't bleed dark seams
	dilate() {
		const src = Buffer.from(this.data); const used = Uint8Array.from(this.used);
		for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
			if (used[y * S + x]) continue;
			for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
				const nx = x + dx, ny = y + dy;
				if (nx < 0 || ny < 0 || nx >= S || ny >= S || !used[ny * S + nx]) continue;
				const j = (ny * S + nx) * 4, i = (y * S + x) * 4;
				if (src[j + 3] === 0) continue;
				src.copy(this.data, i, j, j + 4); break;
			}
		}
	}
}

// face texel size for a direction, given element dims (units) and density
function faceDims(dir, d, dens) {
	const [dx, dy, dz] = d; const q = (v) => Math.max(1, Math.round(v * dens));
	if (dir === 'north' || dir === 'south') return [q(dx), q(dy)];
	if (dir === 'east' || dir === 'west') return [q(dz), q(dy)];
	return [q(dx), q(dz)];
}

// paint from a char map; legend maps char -> (x, y, r) => rgba
function fromMap(rows, legend) {
	return (x, y, w, h, r) => {
		const row = rows[Math.min(rows.length - 1, Math.floor(y * rows.length / h))];
		const ch = row[Math.min(row.length - 1, Math.floor(x * row.length / w))];
		const f = legend[ch]; if (!f) throw new Error('no legend for ' + ch);
		return f(x, y, r);
	};
}

// ---------------------------------------------------------------- build one head
function buildHead(spec) {
	const atlas = new Atlas();
	const r = rng(spec.seed);
	const elements = [];
	for (const el of spec.elements) {
		const d = [0, 1, 2].map(i => el.to[i] - el.from[i]);
		const out = { name: el.name, from: el.from, to: el.to };
		if (el.rotation) out.rotation = el.rotation;
		if (el.glow) out.shade = false;
		out.faces = {};
		for (const dir of ['north', 'east', 'south', 'west', 'up', 'down']) {
			const paint = el.faces[dir] || (el.faces.all && !(el.skip || []).includes(dir) ? el.faces.all : null);
			if (!paint) continue;
			const dens = el.density || 1;
			const [w, h] = faceDims(dir, d, dens);
			const rect = atlas.alloc(w, h);
			for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
				const c = paint.length === 3 ? paint(x, y, r) : paint(x, y, w, h, r, dir);
				if (c) atlas.put(rect.x + x, rect.y + y, c);
			}
			const face = { uv: [rect.x / TPU, rect.y / TPU, (rect.x + w) / TPU, (rect.y + h) / TPU], texture: '#skin' };
			if (el.glow) face.tintindex = 0;
			out.faces[dir] = face;
		}
		elements.push(out);
	}
	atlas.dilate();
	png.write(ASSETS + `textures/block/${spec.id}.png`, { w: S, h: S, data: atlas.data });
	return elements;
}

const shift = (els, dy, dz) => els.map(e => {
	const o = JSON.parse(JSON.stringify(e));
	o.from = [o.from[0], o.from[1] + dy, o.from[2] + dz]; o.to = [o.to[0], o.to[1] + dy, o.to[2] + dz];
	if (o.rotation) o.rotation.origin = [o.rotation.origin[0], o.rotation.origin[1] + dy, o.rotation.origin[2] + dz];
	return o;
});
const r4 = (v) => Math.round(v * 10000) / 10000;
const tidy = (els) => els.map(e => ({ ...e, faces: Object.fromEntries(Object.entries(e.faces).map(([k, f]) => [k, { ...f, uv: f.uv.map(r4) }])) }));

function writeJson(file, obj) {
	fs.mkdirSync(path.dirname(file), { recursive: true });
	// 2-space JSON with flat arrays kept on one line ("from": [4, 0, 4]) so the models stay readable
	const txt = JSON.stringify(obj, null, 2).replace(/\[\s+([^[\]{}]*?)\s+\]/g, (m, inner) => '[' + inner.split(/,\s+/).join(', ') + ']');
	fs.writeFileSync(file, txt + '\n');
}

// ---------------------------------------------------------------- display transforms
// HEAD (worn) -- derived from CustomHeadLayer (1.21.1 bytecode), not guessed. For a BlockItem whose block is not an
// AbstractSkullBlock the layer does, in head-model space (origin at the neck pivot, y down, 1 unit = 1 block):
//   translate(0, -0.25, 0); rotateY(180); scale(0.625, -0.625, -0.625)      (translateToHead)
// then ItemRenderer applies this model's "head" transform (translate t/16, rotate, scale s) and translate(-0.5 x3).
// So one model unit = 0.625 px of the wearer, and model (8, 8, 8) lands at the head's centre, 4 px above the neck.
// Vanilla wears an 8-unit skull at 1.1875x = 9.5 px, sitting on the neck. For our 8-unit skull (y 0..8, centred on
// x/z 8) that needs s * 8 * 0.625 = 9.5  ->  s = 1.9, and its bottom (model y 0) on the neck:
//   4 px + (t.y + s * (0 - 8)) * 0.625 = 0 px  ->  t.y = 8 * 1.9 - 4 / 0.625 = 8.8.
// The rotateY(180) + negative z-scale in translateToHead cancel out for the face: the model's NORTH face ends up on
// the wearer's face, un-mirrored (the same reason a carved pumpkin needs no head rotation) -- so no rotation here.
const HEAD = { rotation: [0, 0, 0], translation: [0, 8.8, 0], scale: [1.9, 1.9, 1.9] };

function itemModel(blockId) {
	return {
		parent: `projecthero:block/${blockId}`,
		display: {
			head: HEAD,
			// The rest copy vanilla's item/template_skull, whose skull faces SOUTH in item space; ours faces NORTH, so
			// every y rotation is vanilla's + 180 (gui 45 -> 225, fixed 180 -> 0, third person 45 -> 225, first person
			// none -> 180). The gui is dropped half a unit because the crown and horns add height above the skull.
			gui: { rotation: [30, 225, 0], translation: [0, 2.5, 0], scale: [1, 1, 1] },
			ground: { rotation: [0, 180, 0], translation: [0, 3, 0], scale: [0.5, 0.5, 0.5] },
			fixed: { rotation: [0, 0, 0], translation: [0, 4, 0], scale: [1, 1, 1] },
			thirdperson_righthand: { rotation: [45, 225, 0], translation: [0, 3, 0], scale: [0.5, 0.5, 0.5] },
			firstperson_righthand: { rotation: [0, 180, 0], translation: [0, 0, 0], scale: [1, 1, 1] },
		},
	};
}

function blockstate(model) {
	return { variants: {
		'facing=north': { model },
		'facing=east': { model, y: 90 },
		'facing=south': { model, y: 180 },
		'facing=west': { model, y: 270 },
	} };
}

function lootTable(blockId, itemId) {
	return {
		type: 'minecraft:block',
		pools: [{
			bonus_rolls: 0.0,
			rolls: 1.0,
			conditions: [{ condition: 'minecraft:survives_explosion' }],
			entries: [{
				type: 'minecraft:item',
				name: `projecthero:${itemId}`,
				functions: [{
					function: 'minecraft:copy_components',
					source: 'block_entity',
					include: ['minecraft:custom_name', 'projecthero:power_key', 'projecthero:trophy_record'],
				}],
			}],
		}],
		random_sequence: `projecthero:blocks/${blockId}`,
	};
}

// ---------------------------------------------------------------- palettes
const BONE = [212, 203, 172], BONE_D = [160, 150, 120], BONE_S = [112, 103, 82], CRACK = [66, 58, 48], SOCKET = [16, 12, 14];
const IRON = [54, 56, 64], IRON_L = [92, 96, 108], IRON_D = [30, 31, 37], RUST = [118, 66, 38], GOLD = [184, 146, 64], GOLD_D = [128, 96, 40];
const HORN0 = [44, 37, 35], HORN1 = [96, 80, 62], HORN2 = [206, 196, 166];

const bone = (x, y, r) => jit(BONE, r, 6);
const boneD = (x, y, r) => jit(BONE_D, r, 7);
const shadow = (x, y, r) => jit(BONE_S, r, 6);
const crack = (x, y, r) => jit(CRACK, r, 5);
const socket = (x, y, r) => jit(SOCKET, r, 3);
const iron = (x, y, r) => r() < 0.12 ? jit(RUST, r, 10) : jit(IRON, r, 6);
const ironL = (x, y, r) => jit(IRON_L, r, 6);
const ironD = (x, y, r) => r() < 0.15 ? jit(RUST, r, 8) : jit(IRON_D, r, 4);
const gold = (x, y, r) => r() < 0.25 ? jit(GOLD_D, r, 6) : jit(GOLD, r, 8);

// glow decals: grayscale (the power colour multiplies it), alpha 0 = cut out
const g = (v) => [v, v, v, 255];
function glowMap(rows) {
	const lv = { '.': null, '1': g(70), '2': g(130), '3': g(190), '4': g(235), '5': g(255) };
	return (x, y, w, h) => lv[rows[y][x]];
}
const EYE = glowMap(['.22.', '2453', '2543', '.33.']);
const GEM = glowMap(['.13.', '1453', '3542', '.32.']);

// a vertical gradient material (spikes, horns): t = 0 at the top of the face
const grad = (top, bottom, amt) => (x, y, w, h, r, dir) => {
	if (dir === 'up') return jit(top, r, amt);
	if (dir === 'down') return jit(bottom, r, amt);
	const t = h <= 1 ? 0.5 : y / (h - 1);
	const c = jit(mix(top, bottom, t), r, amt);
	return (y % 2 === 1 && r() < 0.5) ? [clamp(c[0] * 0.82), clamp(c[1] * 0.82), clamp(c[2] * 0.82), 255] : c; // ridges
};

// ================================================================ GRAVE CHAMPION HEAD
// A cracked champion's skull under a rusted iron crown with a glowing gem, bone crown spikes and curling horns; the
// eye sockets burn with the power's colour.
function champion() {
	const skullFront = fromMap([
		'BBBBBBBB',
		'BBBBBBBB',
		'dbbbbbbd',
		'bSSbbSSb',
		'.SS..SS.',
		'...nn...',
		'.d.dd.d.',
		'dddddddd',
	], { B: bone, b: boneD, d: shadow, S: socket, n: socket, c: crack, '.': bone });
	const skullSide = (flip) => fromMap(flip ? [
		'BBBBBBBB', 'BBBBBBBB', '..c.....', '...c..d.', '....dSd.', '..c.d.d.', '.c......', 'dddddddd',
	] : [
		'BBBBBBBB', 'BBBBBBBB', '.....c..', '.d..c...', '.dSd....', '.d.d.c..', '......c.', 'dddddddd',
	], { B: bone, d: shadow, S: socket, c: crack, '.': bone });
	const skullBack = fromMap([
		'BBBBBBBB', 'BBBBBBBB', '...c....', '...cc...', '....c...', '..cc.c..', '.c....c.', 'dddddddd',
	], { B: bone, c: crack, d: shadow, '.': bone });
	const jawFront = fromMap(['tdtdtdt', 'bbbbbbb'], { t: bone, d: socket, b: boneD });
	const bandSide = fromMap(['GGGGGGGGG', 'iLiiLiiLi', 'DDDDDDDDD'], { G: gold, i: iron, L: ironL, D: ironD });
	const bandTop = (x, y, w, h, r) => (x === 0 || y === 0 || x === w - 1 || y === h - 1) ? gold(x, y, r)
		: (x === y + 1 && x > 2 && x < 7 ? crack(x, y, r) : (x === 1 || y === 1 || x === w - 2 || y === h - 2) ? boneD(x, y, r) : bone(x, y, r));
	const spike = grad(BONE, IRON_D, 8);
	const horn = (a, b) => grad(a, b, 7);

	const els = [
		{ name: 'skull', from: [4, 0, 4], to: [12, 8, 12], faces: { north: skullFront, west: skullSide(false), east: skullSide(true), south: skullBack, down: shadow } },
		{ name: 'jaw', from: [4.5, 0, 3.5], to: [11.5, 2, 4.5], faces: { north: jawFront, all: boneD }, skip: ['south'] },
		{ name: 'crown', from: [3.5, 5.5, 3.5], to: [12.5, 8.5, 12.5], faces: { north: bandSide, east: bandSide, south: bandSide, west: bandSide, up: bandTop, down: ironD } },
		{ name: 'gem', from: [7, 6, 3.25], to: [9, 8, 3.5], faces: { east: gold, west: gold, up: gold, down: gold }, skip: [] },
		{ name: 'gem_glow', from: [7, 6, 3.24], to: [9, 8, 3.24], density: 2, glow: true, faces: { north: GEM } },
		{ name: 'eye_left', from: [9, 3, 3.95], to: [11, 5, 3.95], density: 2, glow: true, faces: { north: EYE } },
		{ name: 'eye_right', from: [5, 3, 3.95], to: [7, 5, 3.95], density: 2, glow: true, faces: { north: EYE } },
		// crown spikes: tall centre, two leaning front corners, two leaning back corners
		{ name: 'spike_c', from: [7.5, 8.5, 3.5], to: [8.5, 11.5, 4.5], faces: { all: spike } },
		{ name: 'spike_fl', from: [3.5, 8.5, 3.5], to: [4.5, 10.5, 4.5], rotation: { angle: 22.5, axis: 'z', origin: [4, 8.5, 4] }, faces: { all: spike } },
		{ name: 'spike_fr', from: [11.5, 8.5, 3.5], to: [12.5, 10.5, 4.5], rotation: { angle: -22.5, axis: 'z', origin: [12, 8.5, 4] }, faces: { all: spike } },
		{ name: 'spike_bl', from: [3.5, 8.5, 11.5], to: [4.5, 10, 12.5], rotation: { angle: 22.5, axis: 'z', origin: [4, 8.5, 12] }, faces: { all: spike } },
		{ name: 'spike_br', from: [11.5, 8.5, 11.5], to: [12.5, 10, 12.5], rotation: { angle: -22.5, axis: 'z', origin: [12, 8.5, 12] }, faces: { all: spike } },
		// horns: out of the temples, then curling up and out (x small = the wearer's left)
		{ name: 'horn_l1', from: [2, 4.5, 6.5], to: [4, 6.5, 8.5], faces: { all: horn(HORN0, HORN0) }, skip: ['east'] },
		{ name: 'horn_l2', from: [2, 6, 6.75], to: [3.5, 9, 8.25], rotation: { angle: 22.5, axis: 'z', origin: [2.75, 6, 7.5] }, faces: { all: horn(HORN1, HORN0) } },
		{ name: 'horn_l3', from: [1.1, 8.5, 7], to: [2.1, 10.5, 8], rotation: { angle: 22.5, axis: 'z', origin: [1.6, 8.5, 7.5] }, faces: { all: horn(HORN2, HORN1) } },
		{ name: 'horn_r1', from: [12, 4.5, 6.5], to: [14, 6.5, 8.5], faces: { all: horn(HORN0, HORN0) }, skip: ['west'] },
		{ name: 'horn_r2', from: [12.5, 6, 6.75], to: [14, 9, 8.25], rotation: { angle: -22.5, axis: 'z', origin: [13.25, 6, 7.5] }, faces: { all: horn(HORN1, HORN0) } },
		{ name: 'horn_r3', from: [13.9, 8.5, 7], to: [14.9, 10.5, 8], rotation: { angle: -22.5, axis: 'z', origin: [14.4, 8.5, 7.5] }, faces: { all: horn(HORN2, HORN1) } },
	];
	return buildHead({ id: 'grave_champion_head', seed: 1404, elements: els });
}

// ================================================================ EMPOWERED ZOMBIE HEAD (boss trophy)
// A rotting zombie's head, split open by the power it carried: glowing eyes, a burning sigil carved into the brow,
// and the power leaking out of a crack in the skull.
const FLESH = [84, 126, 62], FLESH_D = [58, 92, 44], FLESH_L = [108, 148, 78], ROT = [102, 90, 58], HAIR = [42, 50, 30], STITCH = [38, 30, 24], GUM = [70, 26, 28];
const flesh = (x, y, r) => { const t = r(); return t < 0.08 ? jit(ROT, r, 6) : t < 0.22 ? jit(FLESH_D, r, 5) : jit(FLESH, r, 7); };
const fleshD = (x, y, r) => jit(FLESH_D, r, 5);
const fleshL = (x, y, r) => jit(FLESH_L, r, 6);
const hair = (x, y, r) => r() < 0.2 ? jit(FLESH_D, r, 5) : jit(HAIR, r, 5);
const stitch = (x, y, r) => jit(STITCH, r, 3);
const gum = (x, y, r) => jit(GUM, r, 5);
const RUNE = glowMap(['..55..', '..44..', '.4..4.', '.3..3.', '4.55.4', '444444']);
const CRACK_GLOW = glowMap([
	'........',
	'.4......',
	'..45....',
	'...5....',
	'...45...',
	'....553.',
	'.......4',
	'........',
]);
function zombie() {
	const front = fromMap([
		'HHHHHHHH',
		'fLfffLff',
		'dd.ff.dd',
		'fSSffSSf',
		'dSSddSSd',
		'fffnnfff',
		'fgtgtgtf',
		'ffLddLff',
	], { H: hair, f: flesh, L: fleshL, d: fleshD, S: socket, n: socket, g: gum, t: bone, '.': flesh });
	const sideW = fromMap([
		'HHHHHHHH', 'fHffffff', 'ffBBBfff', 'fBBcBfff', 'ffBBffdf', 'ffffffff', 'fdffffLf', 'ffffffff',
	], { H: hair, f: flesh, d: fleshD, L: fleshL, B: bone, c: crack });
	const sideE = fromMap([
		'HHHHHHHH', 'ffffffHf', 'fx.x.xff', 'f-----ff', 'fx.x.xff', 'ffffdfff', 'fLffffdf', 'ffffffff',
	], { H: hair, f: flesh, d: fleshD, L: fleshL, x: stitch, '-': fleshD, '.': flesh });
	const back = fromMap([
		'HHHHHHHH', 'HHHfHHHH', 'HfHffHfH', 'ffffffff', 'ffdfffdf', 'ffffffff', 'fdffffff', 'ffffffff',
	], { H: hair, f: flesh, d: fleshD });
	const top = fromMap([
		'HHHHHHHH', 'HHfHHHHH', 'HHHdHHfH', 'HHHdHHHH', 'HfHHddHH', 'HHHHHdHH', 'HHfHHHHH', 'HHHHHHHH',
	], { H: hair, f: flesh, d: crack });
	const neck = fromMap(['dddddddd', 'dgggggdd', 'dgBBggdd', 'dggBBgd.', 'dgggggdd', 'dddddddd', 'dddddddd', 'dddddddd'],
		{ d: fleshD, g: gum, B: bone, '.': fleshD });

	const els = [
		{ name: 'skull', from: [4, 0, 4], to: [12, 8, 12], faces: { north: front, west: sideW, east: sideE, south: back, up: top, down: neck } },
		{ name: 'ear_l', from: [3.5, 3, 6.5], to: [4, 5, 8], faces: { all: fleshD }, skip: ['east'] },
		{ name: 'ear_r', from: [12, 3, 6.5], to: [12.5, 4.5, 8], faces: { all: fleshD }, skip: ['west'] },
		{ name: 'brow', from: [4.5, 5, 3.6], to: [11.5, 5.6, 4], faces: { all: fleshD }, skip: ['south'] },
		{ name: 'eye_left', from: [9, 3, 3.95], to: [11, 5, 3.95], density: 2, glow: true, faces: { north: EYE } },
		{ name: 'eye_right', from: [5, 3, 3.95], to: [7, 5, 3.95], density: 2, glow: true, faces: { north: EYE } },
		{ name: 'rune', from: [7, 5.8, 3.97], to: [9, 7.8, 3.97], density: 3, glow: true, faces: { north: RUNE } },
		{ name: 'crack_glow', from: [5, 8.02, 5], to: [9, 8.02, 9], density: 2, glow: true, faces: { up: CRACK_GLOW } },
	];
	return buildHead({ id: 'empowered_zombie_head', seed: 814, elements: els });
}

// ---------------------------------------------------------------- write everything
for (const [id, wallId, itemId, els] of [
	['grave_champion_head', 'grave_champion_wall_head', 'final_boss_trophy', champion()],
	['empowered_zombie_head', 'empowered_zombie_wall_head', 'boss_trophy', zombie()],
]) {
	const textures = { skin: `projecthero:block/${id}`, particle: `projecthero:block/${id}` };
	// floor: the skull stands on the block floor, centred; wall: 4 up and against the south side (vanilla wall skull).
	writeJson(ASSETS + `models/block/${id}.json`, { textures, elements: tidy(els) });
	writeJson(ASSETS + `models/block/${wallId}.json`, { textures, elements: tidy(shift(els, 4, 4)) });
	writeJson(ASSETS + `models/item/${itemId}.json`, itemModel(id));
	writeJson(ASSETS + `blockstates/${id}.json`, blockstate(`projecthero:block/${id}`));
	writeJson(ASSETS + `blockstates/${wallId}.json`, blockstate(`projecthero:block/${wallId}`));
	writeJson(DATA + `loot_table/blocks/${id}.json`, lootTable(id, itemId));
	writeJson(DATA + `loot_table/blocks/${wallId}.json`, lootTable(wallId, itemId));
	console.log('wrote', id, wallId, itemId, els.length, 'elements');
}
