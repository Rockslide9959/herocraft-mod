// Generates the All Might assets (v0.12.33): the costume texture, the two geometry files (contained / full power), the item
// icons + item models and the Vestige recipe. Original hand-drawn art (no external skin), hand-rolled PNG writer.
//   textures/armor/all_might.png                  64x64 skin-layout costume texture (face, blond hair, blue suit, white/red gloves, red boots, cape)
//   geo/all_might_base.geo.json / _full.geo.json  the same rig, lean vs. heavily muscular (bigger inflate + shoulder cubes), both with a cape + hair antennae
//   textures/item/all_might_*.png, one_for_all_vestige.png, models/item/*.json, data/recipe/one_for_all_vestige.json
// Run from the repo root: node scratchpad/gen_allmight.js
const fs = require('fs');
const path = require('path');
const { encode } = require('./pnglib.js');
const ASSETS = path.join(__dirname, '..', 'src/main/resources/assets/projecthero/');
const DATA = path.join(__dirname, '..', 'src/main/resources/data/projecthero/');

// ---------------------------------------------------------------- palette
const SKIN = [238, 192, 160], SKIN_D = [205, 152, 122];
const HAIR = [248, 214, 82], HAIR_D = [214, 172, 44], HAIR_L = [255, 236, 140];
const BLUE = [38, 78, 200], BLUE_D = [24, 50, 142], BLUE_L = [70, 116, 234];
const WHITE = [242, 246, 252], WHITE_D = [206, 214, 228];
const RED = [206, 30, 38], RED_D = [150, 18, 26], RED_L = [236, 70, 70];
const YELLOW = [252, 206, 44], YELLOW_D = [206, 158, 20];
const EYE = [70, 130, 230], BLACK = [30, 26, 34];

// ---------------------------------------------------------------- texture painting
const W = 64, H = 64;
const px = Buffer.alloc(W * H * 4);
function put(x, y, c) { if (x < 0 || y < 0 || x >= W || y >= H || !c) return; const i = (y * W + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255; }
function hash(x, y, s) { let h = (x * 374761393 + y * 668265263 + s * 2246822519) | 0; h = (h ^ (h >>> 13)) * 1274126177 | 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967296; }
const shade = (c, k) => c.map((v) => Math.max(0, Math.min(255, Math.round(v * k))));
/** Paint a w x h rectangle at (u, v) by asking fn(x, y) for each pixel (x, y local). */
function rect(u, v, w, h, fn) { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) put(u + x, v + y, fn(x, y, w, h)); }
/** The six faces of a box UV cube at (u, v) with size (w, h, d): fn(faceName, x, y, fw, fh). */
function box(u, v, w, h, d, fn) {
	const faces = { top: [u + d, v, w, d], bottom: [u + d + w, v, w, d], right: [u, v + d, d, h], front: [u + d, v + d, w, h], left: [u + d + w, v + d, d, h], back: [u + 2 * d + w, v + d, w, h] };
	for (const [name, [fu, fv, fw, fh]] of Object.entries(faces)) rect(fu, fv, fw, fh, (x, y, ww, hh) => fn(name, x, y, ww, hh));
}
const noise = (c, x, y, s, amt = 0.06) => shade(c, 1 + (hash(x, y, s) - 0.5) * amt * 2);

// head base (0,0): skin face, blond hair around it
box(0, 0, 8, 8, 8, (f, x, y, fw, fh) => {
	if (f === 'top' || f === 'back') return noise(hash(x, y, 4) < 0.25 ? HAIR_D : HAIR, x, y, 1);
	if (f === 'right' || f === 'left') return y < 3 || (f === 'right' ? x > 4 : x < 3) ? noise(HAIR, x, y, 2) : noise(SKIN, x, y, 3);
	if (f === 'bottom') return noise(SKIN, x, y, 5);
	// front: fringe, brows, eyes, big grin
	if (y < 2) return (x === 3 || x === 4) && y === 1 ? noise(SKIN, x, y, 6) : noise(HAIR, x, y, 6);
	if (y === 2 && (x < 2 || x > 5)) return HAIR;
	if (y === 3 && ((x >= 1 && x <= 3) || (x >= 4 && x <= 6))) return HAIR_D; // thick brows
	if (y === 4 && ((x >= 1 && x <= 3) || (x >= 4 && x <= 6))) return x === 2 || x === 5 ? EYE : WHITE;
	if (y === 5 && (x === 2 || x === 5)) return SKIN_D;
	if (y === 6 && x >= 2 && x <= 5) return BLACK;
	if (y === 7 && x >= 2 && x <= 5) return WHITE; // the smile
	return noise(SKIN, x, y, 7);
});
// hat layer (32,0): the swept-back hair, front fringe only on the top rows
box(32, 0, 8, 8, 8, (f, x, y, fw, fh) => {
	if (f === 'bottom') return null;
	if (f === 'top') return noise(HAIR_L, x, y, 8, 0.05);
	if (f === 'front') return y < 1 ? noise(HAIR, x, y, 9) : null;
	if (f === 'back') return y < 7 ? noise(hash(x, y, 4) < 0.2 ? HAIR_D : HAIR, x, y, 10) : null;
	return y < 4 ? noise(HAIR, x, y, 11) : null; // sides: down over the temples
});
// body (16,16): blue suit, white collar V, yellow belt with a red buckle
box(16, 16, 8, 12, 4, (f, x, y, fw, fh) => {
	if (f === 'top' || f === 'bottom') return BLUE_D;
	let c = noise(BLUE, x, y, 12, 0.05);
	if (f === 'front') {
		const mid = 3.5;
		if (y < 5 && Math.abs(x - mid) < 3.5 - y * 0.7) c = WHITE; // the V collar
		if (y >= 6 && y <= 8 && (x === 3 || x === 4)) c = shade(BLUE_L, 1.0); // sternum highlight
		if (y === 4 || y === 5) c = c === WHITE ? WHITE : shade(BLUE_L, 0.95);
		if (y >= 9 && y <= 10) c = (x === 3 || x === 4) ? RED : YELLOW;
		if (y === 11) c = BLUE_D;
	} else if (f === 'back') {
		if (y >= 9 && y <= 10) c = YELLOW_D;
		if (y === 11) c = BLUE_D;
	} else if (y >= 9 && y <= 10) c = YELLOW_D;
	return c;
});
// arms (40,16) right / (32,48) left: blue sleeves, red cuff, white gloves
for (const [u, v] of [[40, 16], [32, 48]]) {
	box(u, v, 4, 12, 4, (f, x, y, fw, fh) => {
		if (f === 'top') return BLUE_L;
		if (f === 'bottom') return WHITE_D;
		if (y < 8) return noise(BLUE, x, y, 13, 0.05);
		if (y === 8) return RED;
		return noise(WHITE, x, y, 14, 0.03);
	});
}
// legs (0,16) right / (16,48) left: blue trousers, red boots with a white top line
for (const [u, v] of [[0, 16], [16, 48]]) {
	box(u, v, 4, 12, 4, (f, x, y, fw, fh) => {
		if (f === 'top') return BLUE_D;
		if (f === 'bottom') return RED_D;
		if (y < 8) return noise(BLUE, x, y, 15, 0.05);
		if (y === 8) return WHITE;
		return noise(y === 11 ? RED_D : RED, x, y, 16, 0.05);
	});
}
// boots (0,24) right / (16,56) left: only the top/bottom faces matter (the sides re-sample the leg's own bottom rows)
for (const [u, v] of [[0, 24], [16, 56]]) {
	box(u, v, 4, 4, 4, (f, x, y) => f === 'bottom' ? RED_D : f === 'top' ? RED : null);
}
// cape (free region 56,16 -> 64,32): deep red with a lighter fold and a gold hem
rect(56, 16, 8, 16, (x, y) => y > 13 ? YELLOW_D : (x === 3 || x === 4) ? RED_L : noise(y < 3 ? RED_L : RED, x, y, 17, 0.07));
// hair antennae (samples the hat's blond top): nothing extra to paint

fs.writeFileSync(ASSETS + 'textures/armor/all_might.png', encode(W, H, px));

// ---------------------------------------------------------------- geometry
const cube = (origin, size, uv, inflate) => { const c = { origin, size, uv }; if (inflate) c.inflate = inflate; return c; };
const blond = (u, v) => { const o = {}; for (const f of ['north', 'east', 'south', 'west', 'up', 'down']) o[f] = { uv: [u, v], uv_size: [2, 2] }; return o; };
const red = () => { const o = {}; for (const f of ['north', 'east', 'south', 'west', 'up', 'down']) o[f] = { uv: [56, 16], uv_size: [8, 16] }; return o; };

function geo(form) {
	const full = form === 'full';
	const body = full ? 2.0 : 0.3, arm = full ? 1.8 : 0.25, leg = full ? 1.2 : 0.25, head = full ? 0.7 : 0.3, hat = full ? 0.95 : 0.55;
	const boot = full ? 1.3 : 0.3;
	const capeZ = 2 + body + 0.35;
	const bones = [
		{ name: 'armorBody', pivot: [0, 24, 0], cubes: [cube([-4, 12, -2], [8, 12, 4], [16, 16], body)] },
		{ name: 'armorHead', pivot: [0, 24, 0], cubes: [cube([-4, 24, -4], [8, 8, 8], [0, 0], head), cube([-4, 24, -4], [8, 8, 8], [32, 0], hat)] },
		{ name: 'armorRightArm', pivot: [-5, 22, 0], cubes: [cube([-8, 12, -2], [4, 12, 4], [40, 16], arm)] },
		{ name: 'armorLeftArm', pivot: [5, 22, 0], cubes: [cube([4, 12, -2], [4, 12, 4], [32, 48], arm)] },
		{ name: 'armorRightLeg', pivot: [-2, 12, 0], cubes: [cube([-4, 0, -2], [4, 12, 4], [0, 16], leg)] },
		{ name: 'armorLeftLeg', pivot: [2, 12, 0], cubes: [cube([0, 0, -2], [4, 12, 4], [16, 48], leg)] },
		{ name: 'armorRightBoot', pivot: [-2, 12, 0], cubes: [cube([-4, 0, -2], [4, 4, 4], [0, 24], boot)] },
		{ name: 'armorLeftBoot', pivot: [2, 12, 0], cubes: [cube([0, 0, -2], [4, 4, 4], [16, 56], boot)] },
		// the cape hangs from the shoulders, tilted a little away from the back
		{ name: 'cape', parent: 'armorBody', pivot: [0, 24, capeZ], rotation: [7, 0, 0], cubes: [{ origin: [-5, 3, capeZ], size: [10, 21, 1], uv: red() }] },
		// the two swept-up hair antennae
		{ name: 'hair_left', parent: 'armorHead', pivot: [1.2, 32.2, -4.6], rotation: [-8, 0, -16], cubes: [{ origin: [0.6, 32, -5.2], size: [1.2, 5, 1.2], uv: blond(40, 0) }] },
		{ name: 'hair_right', parent: 'armorHead', pivot: [-1.2, 32.2, -4.6], rotation: [-8, 0, 16], cubes: [{ origin: [-1.8, 32, -5.2], size: [1.2, 5, 1.2], uv: blond(40, 0) }] },
	];
	if (full) {
		// trapezius / deltoid mass: this is what makes the full-power silhouette obviously bigger
		bones.push({ name: 'shoulders', parent: 'armorBody', pivot: [0, 24, 0], cubes: [cube([-7.5, 21.0, -3.0], [15, 4.0, 6.0], [16, 16], 0.3)] });
	}
	return {
		format_version: '1.12.0',
		'minecraft:geometry': [{ description: { identifier: 'geometry.all_might_' + form, texture_width: 64, texture_height: 64, visible_bounds_width: 4, visible_bounds_height: 4, visible_bounds_offset: [0, 1, 0] }, bones }],
	};
}
fs.writeFileSync(ASSETS + 'geo/all_might_base.geo.json', JSON.stringify(geo('base'), null, 1));
fs.writeFileSync(ASSETS + 'geo/all_might_full.geo.json', JSON.stringify(geo('full'), null, 1));

// ---------------------------------------------------------------- icons
class C {
	constructor() { this.data = Buffer.alloc(16 * 16 * 4); }
	set(x, y, c) { x = Math.round(x); y = Math.round(y); if (!c || x < 0 || y < 0 || x > 15 || y > 15) return; const i = (y * 16 + x) * 4; this.data[i] = c[0]; this.data[i + 1] = c[1]; this.data[i + 2] = c[2]; this.data[i + 3] = 255; }
	rect(x, y, w, h, c) { for (let dy = 0; dy < h; dy++) for (let dx = 0; dx < w; dx++) this.set(x + dx, y + dy, c); }
}
const ICON = ASSETS + 'textures/item/';
{ // helmet: blond hair, face, antennae, smile
	const c = new C();
	c.rect(4, 3, 8, 9, SKIN); c.rect(3, 4, 1, 5, HAIR); c.rect(12, 4, 1, 5, HAIR); c.rect(4, 2, 8, 3, HAIR); c.rect(3, 3, 10, 2, HAIR);
	c.rect(7, 4, 2, 1, SKIN); c.set(5, 0, HAIR); c.set(5, 1, HAIR); c.set(10, 0, HAIR); c.set(10, 1, HAIR); c.set(6, 1, HAIR); c.set(9, 1, HAIR);
	c.rect(5, 6, 2, 1, HAIR_D); c.rect(9, 6, 2, 1, HAIR_D); c.set(6, 7, EYE); c.set(10, 7, EYE); c.set(5, 7, WHITE); c.set(9, 7, WHITE);
	c.rect(6, 10, 4, 1, BLACK); c.rect(6, 11, 4, 1, WHITE);
	fs.writeFileSync(ICON + 'all_might_helmet.png', encode(16, 16, c.data));
}
{ // chestplate: blue torso, white V collar, yellow belt, red buckle, white gloves
	const c = new C();
	c.rect(4, 2, 8, 11, BLUE); c.rect(1, 2, 3, 8, BLUE); c.rect(12, 2, 3, 8, BLUE);
	c.rect(1, 9, 3, 1, RED); c.rect(12, 9, 3, 1, RED); c.rect(1, 10, 3, 3, WHITE); c.rect(12, 10, 3, 3, WHITE);
	for (let i = 0; i < 4; i++) c.rect(6 - i + 1, 2 + i, 4 + i * 2 - 2, 1, WHITE);
	c.rect(4, 10, 8, 2, YELLOW); c.rect(7, 10, 2, 2, RED); c.rect(4, 12, 8, 1, BLUE_D);
	c.rect(4, 2, 8, 1, BLUE_L);
	fs.writeFileSync(ICON + 'all_might_chestplate.png', encode(16, 16, c.data));
}
{ // leggings: blue trousers, yellow belt, red boot tops
	const c = new C();
	c.rect(3, 1, 10, 4, BLUE); c.rect(3, 5, 4, 8, BLUE); c.rect(9, 5, 4, 8, BLUE);
	c.rect(3, 1, 10, 1, YELLOW); c.rect(7, 1, 2, 2, RED); c.rect(3, 12, 4, 1, WHITE); c.rect(9, 12, 4, 1, WHITE); c.rect(3, 13, 4, 2, RED); c.rect(9, 13, 4, 2, RED);
	fs.writeFileSync(ICON + 'all_might_leggings.png', encode(16, 16, c.data));
}
{ // boots: red with white cuffs and dark soles
	const c = new C();
	c.rect(3, 3, 4, 10, RED); c.rect(9, 3, 4, 10, RED); c.rect(2, 11, 5, 3, RED); c.rect(9, 11, 5, 3, RED);
	c.rect(3, 3, 4, 2, WHITE); c.rect(9, 3, 4, 2, WHITE); c.rect(2, 14, 5, 1, RED_D); c.rect(9, 14, 5, 1, RED_D);
	c.rect(3, 9, 4, 1, YELLOW); c.rect(9, 9, 4, 1, YELLOW);
	fs.writeFileSync(ICON + 'all_might_boots.png', encode(16, 16, c.data));
}
{ // vestige: a glowing green-and-white orb of stored power in a gold clasp
	const c = new C();
	const G = [64, 232, 110], GL = [190, 255, 210], GD = [24, 150, 70];
	for (let y = 2; y < 14; y++) for (let x = 2; x < 14; x++) {
		const d = Math.hypot(x - 7.5, y - 7.5);
		if (d < 5.6) c.set(x, y, d < 2.2 ? WHITE : d < 3.8 ? GL : d < 5.0 ? G : GD);
	}
	c.set(7, 1, YELLOW); c.set(8, 1, YELLOW); c.set(7, 14, YELLOW); c.set(8, 14, YELLOW); c.set(1, 7, YELLOW); c.set(1, 8, YELLOW); c.set(14, 7, YELLOW); c.set(14, 8, YELLOW);
	for (const [x, y] of [[3, 3], [12, 4], [4, 12], [12, 12]]) c.set(x, y, GL);
	fs.writeFileSync(ICON + 'one_for_all_vestige.png', encode(16, 16, c.data));
}
for (const n of ['all_might_helmet', 'all_might_chestplate', 'all_might_leggings', 'all_might_boots', 'one_for_all_vestige']) {
	fs.writeFileSync(ASSETS + 'models/item/' + n + '.json', JSON.stringify({ parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/' + n } }, null, 2) + '\n');
}
fs.writeFileSync(DATA + 'recipe/one_for_all_vestige.json', JSON.stringify({
	type: 'minecraft:crafting_shaped', category: 'misc', pattern: ['PEP', 'DND', 'PEP'],
	key: { P: { item: 'projecthero:titanium_gold_plate' }, E: { item: 'minecraft:enchanted_golden_apple' }, D: { item: 'minecraft:diamond_block' }, N: { item: 'minecraft:nether_star' } },
	result: { id: 'projecthero:one_for_all_vestige', count: 1 },
}, null, 2) + '\n');
console.log('All Might assets written');
