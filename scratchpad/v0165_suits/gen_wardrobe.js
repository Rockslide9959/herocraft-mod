// v0.16.5 Mannequin + Wardrobe assets: the mannequin skin, both item icons, the six wardrobe block models, the
// blockstate, the loot table, recipes and recipe advancements. Hand-rolled PNGs (no image library here).
// Run from the repo root: node scratchpad/v0165_suits/gen_wardrobe.js
const fs = require('fs');
const path = require('path');
const P = require('./png.js');

const ASSETS = 'src/main/resources/assets/projecthero';
const DATA = 'src/main/resources/data/projecthero';
const json = (file, obj) => fs.writeFileSync(file, JSON.stringify(obj, null, 2) + '\n');
const hex = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16), 255];
const shade = (c, f) => [Math.max(0, Math.min(255, Math.round(c[0] * f))), Math.max(0, Math.min(255, Math.round(c[1] * f))),
	Math.max(0, Math.min(255, Math.round(c[2] * f))), 255];

// deterministic noise
let seed = 1651;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };

// ---------------------------------------------------------------- mannequin skin (64x64, base layer only)
{
	const W = 64, px = Buffer.alloc(W * W * 4);
	const WOOD = hex('#dcc39a');
	const SEAM = hex('#9c7a52');
	const set = (x, y, c) => px.set(c, (y * W + x) * 4);
	// grain: a gentle vertical streak per column plus a little per-pixel noise
	const grain = [];
	for (let x = 0; x < W; x++) grain.push(0.96 + rnd() * 0.07);
	// face(x, y, w, h, light): fill a cube face with wood, lit by `light`, darker toward its lower edge on the sides
	function face(x0, y0, w, h, light, vertical = true) {
		for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
			const fall = vertical ? 1.0 - 0.06 * (y / Math.max(1, h - 1)) : 1.0;
			const n = grain[(x0 + x) % W] * (0.985 + rnd() * 0.03);
			set(x0 + x, y0 + y, shade(WOOD, light * fall * n));
		}
	}
	// a cube's six faces from its texture origin (u, v) and size (w, h, d), the vanilla skin layout
	function cube(u, v, w, h, d) {
		face(u + d, v, w, d, 1.10, false); // top
		face(u + d + w, v, w, d, 0.72, false); // bottom
		face(u, v + d, d, h, 0.90); // right side
		face(u + d, v + d, w, h, 1.0); // front
		face(u + d + w, v + d, d, h, 0.90); // left side
		face(u + d + w + d, v + d, w, h, 0.84); // back
	}
	// a seam: one darker row across the four side faces of a limb at row `r` (0 = top)
	function seam(u, v, w, d, r, k = 0.78) {
		const y = v + d + r;
		for (let x = u; x < u + 2 * (w + d); x++) {
			const i = (y * W + x) * 4;
			const c = [px[i], px[i + 1], px[i + 2]];
			set(x, y, [Math.round(c[0] * k), Math.round(c[1] * k), Math.round(c[2] * k), 255]);
		}
	}
	cube(0, 0, 8, 8, 8); // head
	cube(16, 16, 8, 12, 4); // body
	cube(40, 16, 4, 12, 4); // right arm
	cube(32, 48, 4, 12, 4); // left arm
	cube(0, 16, 4, 12, 4); // right leg
	cube(16, 48, 4, 12, 4); // left leg
	// head: a soft chin shadow and the neck joint
	seam(0, 0, 8, 8, 7, 0.86);
	// body: the waist joint and the hip line
	seam(16, 16, 8, 4, 7, 0.8);
	seam(16, 16, 8, 4, 11, 0.86);
	// arms: shoulder ball, elbow, wrist
	for (const [u, v] of [[40, 16], [32, 48]]) {
		seam(u, v, 4, 4, 0, 0.84);
		seam(u, v, 4, 4, 5, 0.78);
		seam(u, v, 4, 4, 9, 0.8);
	}
	// legs: hip, knee, ankle
	for (const [u, v] of [[0, 16], [16, 48]]) {
		seam(u, v, 4, 4, 0, 0.84);
		seam(u, v, 4, 4, 5, 0.78);
		seam(u, v, 4, 4, 10, 0.82);
	}
	// faint brass screw on each shoulder joint's top (where the arm pivots)
	set(45, 17, hex('#b38a3e'));
	set(37, 49, hex('#b38a3e'));
	P.write(path.join(ASSETS, 'textures/entity/mannequin.png'), W, W, px);
}

// ---------------------------------------------------------------- icons (16x16)
function icon(name, rows, pal) {
	const px = Buffer.alloc(16 * 16 * 4);
	rows.forEach((r, y) => [...r].forEach((ch, x) => { if (ch !== '.') px.set(hex(pal[ch]), (y * 16 + x) * 4); }));
	P.write(path.join(ASSETS, 'textures/item', name + '.png'), 16, 16, px);
	json(path.join(ASSETS, 'models/item', name + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/' + name } });
}
icon('mannequin', [
	'......oooo......',
	'.....oWWWWo.....',
	'.....oWWWwo.....',
	'.....owWwwo.....',
	'......oSSo......',
	'...ooWWWWWwoo...',
	'..oWWoWWWWwowo..',
	'..oWwoWWWWwowo..',
	'..oWwoSSSSsowo..',
	'..oWwoWWWWwowo..',
	'...oooWWwwooo...',
	'.....oWwowwo....',
	'.....oWwowwo....',
	'.....oWwowwo....',
	'..ooooooooooooo.',
	'..obbbbbbbbbbbo.',
], { o: '#3b2a1a', W: '#e2c9a0', w: '#c0a073', S: '#9c7a52', s: '#86683f', b: '#7a5a37' });
icon('wardrobe', [
	'.......gg.......',
	'..oooooooooooo..',
	'.oDDDDDDDDDDDDo.',
	'.oDooooDooooDDo.',
	'.oDgrrDiWiDbbDo.',
	'.oDrrrDiWiDbbbo.',
	'.oDrrrDWWWDbbbo.',
	'.oDrrrDiWiDbbbo.',
	'.oDooooiWioooDo.',
	'.oDLLLDiWiDLLDo.',
	'.oDLgLDiWiDLgDo.',
	'.oDLLLDcccDLLDo.',
	'.oDLgLDgggDLgDo.',
	'.oDDDDDDDDDDDDo.',
	'.oooooooooooooo.',
	'..o..........o..',
], { o: '#241509', D: '#4a2f17', L: '#7a5634', g: '#e8b53c', r: '#c22a33', b: '#2e4f9a', i: '#5d3f22', W: '#dcc39a', c: '#6b4a2a' });

// ---------------------------------------------------------------- wardrobe block models (authored facing north: front z=0)
const T = {
	frame: 'minecraft:block/dark_oak_planks',
	trim: 'minecraft:block/stripped_dark_oak_log',
	inside: 'minecraft:block/spruce_planks',
	drawer: 'minecraft:block/spruce_planks',
	brass: 'minecraft:block/gold_block',
};
const wrap = (a, b) => { // an auto-UV span kept inside 0..16
	let lo = ((a % 16) + 16) % 16;
	if (lo + (b - a) > 16) lo = Math.max(0, 16 - (b - a));
	return [lo, Math.min(16, lo + (b - a))];
};
function box(from, to, tex, extra = {}) {
	const [x0, y0, z0] = from, [x1, y1, z1] = to;
	const xs = wrap(x0, x1), ys = wrap(y0, y1), zs = wrap(z0, z1);
	const V = s => [16 - s[1], 16 - s[0]];
	const faces = {
		north: { uv: [16 - xs[1], V(ys)[0], 16 - xs[0], V(ys)[1]], texture: '#' + tex },
		south: { uv: [xs[0], V(ys)[0], xs[1], V(ys)[1]], texture: '#' + tex },
		west: { uv: [zs[0], V(ys)[0], zs[1], V(ys)[1]], texture: '#' + tex },
		east: { uv: [16 - zs[1], V(ys)[0], 16 - zs[0], V(ys)[1]], texture: '#' + tex },
		up: { uv: [xs[0], zs[0], xs[1], zs[1]], texture: '#' + tex },
		down: { uv: [xs[0], 16 - zs[1], xs[1], 16 - zs[0]], texture: '#' + tex },
	};
	return { from, to, faces, ...extra };
}
const mirror = e => { // left <-> right: x -> 16 - x
	const f = [16 - e.to[0], e.from[1], e.from[2]], t = [16 - e.from[0], e.to[1], e.to[2]];
	const tex = e.faces.north.texture.slice(1);
	return box(f, t, tex);
};

// LEFT column (viewer's left; for a north-facing wardrobe it lies to the east, +X): outer wall at x 14..16, the divider
// against the middle at x 0..1
const leftLower = [
	box([14, 0, 0], [16, 16, 16], 'frame'), // outer wall
	box([0, 0, 1], [1, 16, 16], 'frame'), // divider
	box([1, 0, 14], [14, 16, 16], 'inside'), // back panel
	box([1, 0, 0], [14, 1.5, 14], 'trim'), // kick plinth
	box([1, 1.5, 1], [14, 7.5, 14], 'frame'), // drawer carcass
	box([1.5, 2, 0.5], [13.5, 7, 1], 'drawer'), // the drawer
	box([5.5, 4, -0.5], [9.5, 5, 0.5], 'brass'), // its handle
	box([1, 7.5, 0], [14, 8.5, 14.5], 'trim'), // shelf over the drawer
];
const leftUpper = [
	box([14, 0, 0], [16, 16, 16], 'frame'),
	box([0, 0, 1], [1, 16, 16], 'frame'),
	box([1, 0, 14], [14, 14, 16], 'inside'),
	box([0, 14, 0], [16, 16, 16], 'frame'), // top board
	box([1, 13, 0], [14, 14, 1.5], 'trim'), // header over the opening
	box([11.3, 8.6, 13.5], [13.3, 10.6, 14], 'brass'), // rosette where the waterfall arm meets the back panel
	box([0, 16, -1], [16, 17.5, 16], 'trim'), // cornice
	box([0, 17.5, -0.5], [16, 18, 15.5], 'frame'), // cap
	box([14, 16, -1], [16, 18.5, 1], 'brass'), // corner finial
];
// MIDDLE: the mannequin niche
const centerLower = [
	box([0, 0, 14], [16, 16, 16], 'inside'),
	box([0, 0, 0], [16, 1.5, 14], 'trim'),
	box([0, 1.5, 0], [16, 2, 14], 'frame'), // floor
	box([2.5, 2, 2.5], [13.5, 2.5, 13.5], 'brass'), // brass ring under the plinth
	box([3, 2.5, 3], [13, 3, 13], 'trim'), // display plinth (top at 3/16 = FIGURE_Y)
	box([1.5, 2, 13.5], [14.5, 2.5, 14], 'brass'), // skirting strip
];
const centerUpper = [
	box([0, 0, 14], [16, 14, 16], 'inside'),
	box([0, 14, 0], [16, 16, 16], 'frame'),
	box([0, 13, 0], [16, 14, 1.5], 'trim'), // header
	box([0, 11, 0], [1.5, 13, 1.5], 'trim'), // arch corners
	box([14.5, 11, 0], [16, 13, 1.5], 'trim'),
	box([0, 16, -1], [16, 17.5, 16], 'trim'),
	box([0, 17.5, -0.5], [16, 18, 15.5], 'frame'),
	box([2.5, 18, 0], [13.5, 21, 2], 'frame'), // pediment
	box([2, 21, -0.25], [14, 21.75, 2.25], 'trim'), // pediment cap
	box([4.5, 18.5, -0.5], [11.5, 20.5, 0], 'brass'), // the number plate (WardrobeRenderer writes on it)
	box([7, 21.75, 0.5], [9, 23, 1.5], 'brass'), // finial
];
const rightLower = leftLower.map(mirror);
const rightUpper = leftUpper.map(mirror);
const models = {
	wardrobe_left_lower: leftLower, wardrobe_left_upper: leftUpper,
	wardrobe_center_lower: centerLower, wardrobe_center_upper: centerUpper,
	wardrobe_right_lower: rightLower, wardrobe_right_upper: rightUpper,
};
for (const [name, elements] of Object.entries(models)) {
	json(path.join(ASSETS, 'models/block', name + '.json'), {
		textures: { particle: T.frame, ...T }, elements });
}
const variants = {};
for (const [facing, y] of [['north', 0], ['east', 90], ['south', 180], ['west', 270]]) {
	for (const part of ['left', 'center', 'right']) for (const half of ['lower', 'upper']) {
		variants[`facing=${facing},half=${half},part=${part}`] = { model: `projecthero:block/wardrobe_${part}_${half}`, ...(y ? { y } : {}) };
	}
}
json(path.join(ASSETS, 'blockstates/wardrobe.json'), { variants });

// ---------------------------------------------------------------- loot, recipes, advancements
json(path.join(DATA, 'loot_table/blocks/wardrobe.json'), {
	type: 'minecraft:block',
	pools: [{ rolls: 1, bonus_rolls: 0, entries: [{ type: 'minecraft:item', name: 'projecthero:wardrobe' }],
		conditions: [{ condition: 'minecraft:survives_explosion' },
			{ condition: 'minecraft:block_state_property', block: 'projecthero:wardrobe', properties: { part: 'center', half: 'lower' } }] }],
	random_sequence: 'projecthero:blocks/wardrobe' });
json(path.join(DATA, 'recipe/mannequin.json'), {
	type: 'minecraft:crafting_shaped', category: 'misc', pattern: [' W ', 'WAW', ' S '],
	key: { W: { item: 'minecraft:white_wool' }, A: { item: 'minecraft:armor_stand' }, S: { item: 'minecraft:stick' } },
	result: { id: 'projecthero:mannequin', count: 1 } });
json(path.join(DATA, 'recipe/wardrobe.json'), {
	type: 'minecraft:crafting_shaped', category: 'misc', pattern: ['PGP', 'PMP', 'PCP'],
	key: { P: { tag: 'minecraft:planks' }, G: { item: 'minecraft:gold_ingot' }, M: { item: 'projecthero:mannequin' }, C: { item: 'minecraft:chest' } },
	result: { id: 'projecthero:wardrobe', count: 1 } });
for (const [id, needs] of [['mannequin', 'minecraft:armor_stand'], ['wardrobe', 'projecthero:mannequin']]) {
	json(path.join(DATA, 'advancement/recipes', id + '.json'), {
		parent: 'minecraft:recipes/root',
		criteria: {
			has_ingredient: { trigger: 'minecraft:inventory_changed', conditions: { items: [{ items: needs }] } },
			has_the_recipe: { trigger: 'minecraft:recipe_unlocked', conditions: { recipe: 'projecthero:' + id } } },
		requirements: [['has_ingredient', 'has_the_recipe']],
		rewards: { recipes: ['projecthero:' + id] } });
}
console.log('ok');
