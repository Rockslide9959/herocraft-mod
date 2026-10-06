// v0.15.3: Gladiator Hulk gear -- the seven 16x16 item icons, the 3D hammer / axe item models + their texture, the item
// model JSONs and the seven crafting recipes. Run from the repo root: node scratchpad/gen_gladiator_items_v0153.js
const fs = require('fs');
const path = require('path');
const L = require('./pnglib.js');

const ROOT = path.join(__dirname, '..');
const A = path.join(ROOT, 'src/main/resources/assets/projecthero');
const DATA = path.join(ROOT, 'src/main/resources/data/projecthero');
const hex = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16), 255];

const PAL = {
	'.': [0, 0, 0, 0],
	k: hex('#141416'), // outline
	d: hex('#2a2c31'), m: hex('#3f424a'), l: hex('#5f646e'), h: hex('#8a909b'), // iron
	b: hex('#7a5a1c'), g: hex('#b88f2e'), G: hex('#e2b852'), Y: hex('#f6dc8a'), // gold
	n: hex('#2e1d12'), L: hex('#4f3220'), e: hex('#6e4a30'), t: hex('#a07a52'), // leather
	r: hex('#7a1614'), R: hex('#b3261f'), o: hex('#d9503a'), // red
	s: hex('#6d737c'), S: hex('#aab1ba'), W: hex('#e6ebef'), // steel
	w: hex('#3b2618'), // wood
};

function icon(name, rows) {
	if (rows.length !== 16 || rows.some(r => r.length !== 16)) throw new Error('bad icon ' + name + ' ' + rows.map(r => r.length));
	const img = { w: 16, h: 16, data: Buffer.alloc(16 * 16 * 4) };
	rows.forEach((row, y) => [...row].forEach((ch, x) => {
		const c = PAL[ch];
		if (!c) throw new Error('bad char ' + ch + ' in ' + name);
		const o = (y * 16 + x) * 4;
		img.data[o] = c[0]; img.data[o + 1] = c[1]; img.data[o + 2] = c[2]; img.data[o + 3] = c[3];
	}));
	L.write(path.join(A, 'textures/item', name + '.png'), img);
}

// ------------------------------------------------------------------ icons (hand-placed pixels)
icon('gladiator_helmet', [
	'.......kk.......',
	'......kRok......',
	'......kRRk......',
	'.....kkRRkk.....',
	'....kGGgggGk....',
	'...kmlhggglmk...',
	'..kmlhmggmmlmk..',
	'..kdmmmggmmmdk..',
	'..kbGGGgGGGGbk..',
	'..kdmk.gg.kmdk..',
	'..kdlk.gg.kldk..',
	'..kdmk.gg.kmdk..',
	'..kdmGk..kGmdk..',
	'...kdmk..kmdk...',
	'....kkk..kkk....',
	'................',
]);
icon('gladiator_pauldron', [
	'................',
	'.....kkkkk......',
	'....kGGGGGkk....',
	'...kghlllmmGk...',
	'..kghlllmmmmGk..',
	'..kGGGGGGGGGGk..',
	'..kmhlllmmmmdk..',
	'...kGGGGGGGGGk..',
	'...kmhllmmmmdk..',
	'....kGGGGGGGGk..',
	'....kmhlmmmdk...',
	'.....kGGGGGk....',
	'......kmmdk.....',
	'.......kkk......',
	'................',
	'................',
]);
icon('gladiator_harness', [
	'................',
	'..kk........kk..',
	'..kLek....keLk..',
	'...kLek..keLk...',
	'....kLmkkmLk....',
	'.....kGYGGk.....',
	'....kGGRRGGk....',
	'....kGRooRGk....',
	'....kGGRRGbk....',
	'.....kGgggk.....',
	'....kLmkkmLk....',
	'...kLek..keLk...',
	'..kLek....keLk..',
	'..kLk......kLk..',
	'..kk........kk..',
	'................',
]);
icon('gladiator_bracers', [
	'................',
	'..kkkkk.........',
	'.kGGGGGk........',
	'.kLeeLLk........',
	'.kmhlmdk...kkkkk',
	'.kLetLLk..kGGGGG',
	'.kmhlmdk..kLeeLL',
	'.kLeeLLk..kmhlmd',
	'.kGGGGbk..kLetLL',
	'..kkkkk...kmhlmd',
	'..........kLeeLL',
	'..........kGGGGb',
	'...........kkkkk',
	'................',
	'................',
	'................',
]);
icon('gladiator_kilt', [
	'................',
	'.kkkkkkkkkkkkkk.',
	'.kLLLLLGGLLLLLk.',
	'.knnnnkYGknnnnk.',
	'.kLkLkkRRkkLkLk.',
	'.kLkLkkRRkkLkLk.',
	'.keke.kRRk.ekek.',
	'.kLkL.kRRk.LkLk.',
	'.kLkL.kRok.LkLk.',
	'.kekh.kRRk.hkek.',
	'.kLkL.kRRk.LkLk.',
	'.kk.k.kGGk.k.kk.',
	'......kgGk......',
	'.......kk.......',
	'................',
	'................',
]);
// the hammer: a two-headed sledge, its block-shaped head across the end of a diagonal haft (drawn procedurally)
{
	const g = [];
	for (let y = 0; y < 16; y++) g.push(Array(16).fill('.'));
	// haft from the bottom-left corner up to the head
	for (let i = 0; i < 9; i++) {
		const x = 1 + i, y = 14 - i;
		g[y][x] = i % 3 === 0 ? 'e' : 'L';
		g[y][x + 1] = 'w';
	}
	g[14][1] = 'G'; g[15][0] = 'g'; g[14][0] = 'g'; g[15][1] = 'b';
	const cx = 10.5, cy = 4.5;
	const inHead = (x, y) => { const a = ((x - cx) + (y - cy)) / Math.SQRT2, b = ((x - cx) - (y - cy)) / Math.SQRT2; return Math.abs(a) <= 5.6 && Math.abs(b) <= 2.6 ? [a, b] : null; };
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const h = inHead(x, y);
		if (!h) continue;
		const [a, b] = h;
		if (Math.abs(a) > 4.3) g[y][x] = b < 0 ? 'G' : 'g';
		else if (Math.abs(a) < 1.0) g[y][x] = b < -0.5 ? 'G' : 'g';
		else g[y][x] = b < -1.2 ? 'h' : b < 0.5 ? 'l' : 'm';
	}
	// outline
	const out = g.map(r => r.slice());
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		if (g[y][x] !== '.') continue;
		const n = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => g[y + dy] && g[y + dy][x + dx] && g[y + dy][x + dx] !== '.');
		if (n) out[y][x] = 'k';
	}
	icon('gladiator_hammer', out.map(r => r.join('')));
}
icon('gladiator_axe', [
	'.........kkk....',
	'........kSWWk...',
	'.......kSSWWk...',
	'.....kkgkSSWk...',
	'....kmGgkkSWk...',
	'....kmmgGksWk...',
	'...kwkkmgkkSk...',
	'..kLwk.kkk.kk...',
	'..kwLk..........',
	'.kLwk...........',
	'.kwLk...........',
	'kLwk............',
	'kwLk............',
	'kgGk............',
	'kbk.............',
	'.k..............',
]);

// ------------------------------------------------------------------ 3D hammer / axe: a 32x32 texture of material swatches
// swatch layout (pixels): iron 0..7 x 0..7, iron-with-gold-rim 8..15, gold 16..23, haft 24..31 (vertical wrap),
// steel 0..7 x 8..15, bright edge 8..15 x 8..15, red 16..23 x 8..15, leather 24..31 x 8..15
const T = { w: 32, h: 32, data: Buffer.alloc(32 * 32 * 4) };
const put = (x, y, c) => { const o = (y * 32 + x) * 4; T.data[o] = c[0]; T.data[o + 1] = c[1]; T.data[o + 2] = c[2]; T.data[o + 3] = 255; };
const nz = (x, y) => (((x * 73856093) ^ (y * 19349663)) >>> 0) % 7 - 3;
const sh = (c, d) => [c[0] + d * 3, c[1] + d * 3, c[2] + d * 3].map(v => Math.max(0, Math.min(255, v)));
for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
	put(x, y, sh(y === 0 ? PAL.l : PAL.m, nz(x, y)));
	const rim = x === 0 || y === 0 || x === 7 || y === 7;
	put(8 + x, y, rim ? (y === 0 ? PAL.G : PAL.g) : sh(PAL.m, nz(x + 8, y)));
	put(16 + x, y, sh(y < 2 ? PAL.G : y > 5 ? PAL.b : PAL.g, nz(x, y + 3)));
	put(24 + x, y, sh(y % 3 === 0 ? PAL.e : (y % 3 === 1 ? PAL.L : PAL.w), nz(x, y)));
	put(x, 8 + y, sh(y < 3 ? PAL.s : PAL.S, nz(x, y)));
	put(8 + x, 8 + y, sh(y < 2 ? PAL.S : PAL.W, nz(x, y) >> 1));
	put(16 + x, 8 + y, sh(x % 3 === 0 ? PAL.o : PAL.R, nz(x, y)));
	put(24 + x, 8 + y, sh(PAL.L, nz(x, y)));
}
// rows 16..31: a mirror of the haft swatch, so long faces can stretch over it
for (let y = 16; y < 32; y++) for (let x = 0; x < 32; x++) put(x, y, sh(PAL.m, nz(x, y)));
L.write(path.join(A, 'textures/item/gladiator_weapons.png'), T);

// UVs in the 0..16 space of a 32x32 texture: one swatch = 4x4 units
const SW = { iron: [0, 0], trim: [4, 0], gold: [8, 0], haft: [12, 0], steel: [0, 4], edge: [4, 4], red: [8, 4], leather: [12, 4] };
function el(from, to, mat, opts = {}) {
	const [u, v] = SW[mat];
	const face = { uv: [u, v, u + 4, v + 4], texture: '#tex' };
	const e = { from, to, faces: { north: face, south: face, east: face, west: face, up: face, down: face } };
	if (opts.rot) e.rotation = opts.rot;
	return e;
}
function model(name, elements, display) {
	const m = { credit: 'Project Hero v0.15.3 -- scratchpad/gen_gladiator_items_v0153.js', texture_size: [32, 32],
		textures: { tex: 'projecthero:item/gladiator_weapons', particle: 'projecthero:item/' + name }, elements, display };
	fs.writeFileSync(path.join(A, 'models/item', name + '.json'), JSON.stringify(m, null, 2).replace(/\n/g, '\r\n') + '\r\n');
}
// The model stands upright: haft along +Y, grip at the bottom (y ~0), head at the top. Units are 1/16 block; the
// item scale in the display transforms makes it big in the hand.
const DISPLAY = {
	// tuned in-client (harness screenshots): head up and forward in third person like a held tool, smaller first person
	thirdperson_righthand: { rotation: [0, -90, -35], translation: [0, 4, 0.5], scale: [0.7, 0.7, 0.7] },
	thirdperson_lefthand: { rotation: [0, 90, 35], translation: [0, 4, 0.5], scale: [0.7, 0.7, 0.7] },
	firstperson_righthand: { rotation: [0, -90, 25], translation: [1.13, 3.2, 1.13], scale: [0.35, 0.35, 0.35] },
	firstperson_lefthand: { rotation: [0, 90, -25], translation: [1.13, 3.2, 1.13], scale: [0.35, 0.35, 0.35] },
	gui: { rotation: [0, 0, -45], translation: [-1, -1, 0], scale: [0.4, 0.4, 0.4] },
	ground: { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.4, 0.4, 0.4] },
	fixed: { rotation: [0, 0, -45], translation: [-1, -1, 0], scale: [0.35, 0.35, 0.35] },
	head: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [0.5, 0.5, 0.5] },
};
model('gladiator_hammer', [
	el([7, -8, 7], [9, 22, 9], 'haft'),
	el([6.5, -10, 6.5], [9.5, -8, 9.5], 'gold'), // pommel
	el([6.25, 21, 6.25], [9.75, 23, 9.75], 'gold'), // collar
	el([2, 22.5, 4.5], [14, 30.5, 11.5], 'trim'), // head core (two striking faces left / right)
	el([1, 22, 4], [2.5, 31, 12], 'gold'), // left face cap
	el([13.5, 22, 4], [15, 31, 12], 'gold'), // right face cap
	el([5.5, 24.5, 4.25], [10.5, 28.5, 11.75], 'iron'), // centre band
	el([7, 30.5, 7], [9, 32, 9], 'gold'), // top spike
], DISPLAY);
model('gladiator_axe', [
	el([7, -9, 7], [9, 24, 9], 'haft'),
	el([6.5, -11, 6.5], [9.5, -9, 9.5], 'gold'), // pommel
	el([7.25, 24, 7.25], [8.75, 27, 8.75], 'gold'), // top spike
	el([6.25, 17, 6.25], [9.75, 23, 9.75], 'trim'), // socket
	el([9.5, 18, 7.5], [12, 22, 8.5], 'iron'), // neck
	el([12, 16.5, 7.6], [16, 23.5, 8.4], 'steel'), // blade body
	el([16, 14, 7.65], [19.5, 26, 8.35], 'edge'), // edge (wider, crescent)
	el([3.5, 18.5, 7.5], [6.25, 21.5, 8.5], 'iron'), // back spike
	el([2, 19.25, 7.6], [3.5, 20.75, 8.4], 'gold'), // spike tip
], DISPLAY);
for (const n of ['gladiator_helmet', 'gladiator_pauldron', 'gladiator_harness', 'gladiator_bracers', 'gladiator_kilt']) {
	fs.writeFileSync(path.join(A, 'models/item', n + '.json'), JSON.stringify({ parent: 'minecraft:item/generated',
		textures: { layer0: 'projecthero:item/' + n } }, null, 2).replace(/\n/g, '\r\n') + '\r\n');
}

// ------------------------------------------------------------------ recipes (plain crafting table, shaped)
function recipe(name, pattern, key) {
	const k = {};
	for (const [ch, item] of Object.entries(key)) k[ch] = { item };
	const r = { type: 'minecraft:crafting_shaped', category: 'equipment', pattern, key: k, result: { id: 'projecthero:' + name, count: 1 } };
	fs.writeFileSync(path.join(DATA, 'recipe', name + '.json'), JSON.stringify(r, null, 2).replace(/\n/g, '\r\n') + '\r\n');
}
const I = 'minecraft:iron_ingot', G = 'minecraft:gold_ingot', LE = 'minecraft:leather', W = 'minecraft:red_wool';
recipe('gladiator_helmet', [' W ', 'IGI', 'I I'], { W, I, G });
recipe('gladiator_pauldron', ['GII', 'III', 'L  '], { G, I, L: LE });
recipe('gladiator_harness', ['L L', 'LGL', 'L L'], { L: LE, G });
recipe('gladiator_bracers', ['IGI', 'L L', 'IGI'], { I, G, L: LE });
recipe('gladiator_kilt', ['LGL', 'LWL', 'L L'], { L: LE, G, W });
recipe('gladiator_hammer', ['BNB', 'BSB', ' S '], { B: 'minecraft:iron_block', N: 'minecraft:netherite_ingot', S: 'minecraft:stick' });
recipe('gladiator_axe', ['BN ', 'BS ', ' S '], { B: 'minecraft:iron_block', N: 'minecraft:netherite_ingot', S: 'minecraft:stick' });
console.log('gladiator items written');
