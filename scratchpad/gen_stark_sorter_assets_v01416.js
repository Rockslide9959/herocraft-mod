// v0.14.16: Stark Sorting Station + Stark Sorter Bot assets.
//
// Writes (relative to the repo root):
//   textures/block/stark_sorting_station_{base,side,front,screen,rim,top,pad,pylon}.png  (16x16)
//   textures/entity/stark_sorter_bot.png + stark_sorter_bot_glowmask.png              (64x64, box UV)
//   geo/stark_sorter_bot.geo.json, animations/stark_sorter_bot.animation.json
//   models/block/stark_sorting_station.json, models/item/..., blockstates/...
//
// Palette: Stark red, gold trim, gunmetal, and arc-reactor cyan. Same hand-rolled PNG encoder as the other
// scripts in this folder (Node zlib + CRC32) -- the project has no image library and no Python.
// Run from the repo root:  node scratchpad/gen_stark_sorter_assets_v01416.js
const fs = require('fs');
const zlib = require('zlib');
const path = require('path');

const ASSETS = path.join(__dirname, '../src/main/resources/assets/projecthero');

// ---------------------------------------------------------------- PNG encoding
const CRC_TABLE = (() => {
	const t = new Uint32Array(256);
	for (let n = 0; n < 256; n++) {
		let c = n;
		for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
		t[n] = c >>> 0;
	}
	return t;
})();
const crc32 = (buf) => {
	let c = 0xffffffff;
	for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
	return (c ^ 0xffffffff) >>> 0;
};
const chunk = (type, data) => {
	const len = Buffer.alloc(4);
	len.writeUInt32BE(data.length, 0);
	const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
	const crc = Buffer.alloc(4);
	crc.writeUInt32BE(crc32(td), 0);
	return Buffer.concat([len, td, crc]);
};
function encode(w, h, data) {
	const stride = w * 4;
	const raw = Buffer.alloc((stride + 1) * h);
	for (let y = 0; y < h; y++) {
		raw[y * (stride + 1)] = 0;
		data.copy(raw, y * (stride + 1) + 1, y * stride, y * stride + stride);
	}
	const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
	const ihdr = Buffer.alloc(13);
	ihdr.writeUInt32BE(w, 0);
	ihdr.writeUInt32BE(h, 4);
	ihdr[8] = 8;
	ihdr[9] = 6;
	return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
		chunk('IEND', Buffer.alloc(0))]);
}

// ---------------------------------------------------------------- canvas
class Img {
	constructor(w, h) { this.w = w; this.h = h; this.d = Buffer.alloc(w * h * 4); }
	set(x, y, c) {
		if (x < 0 || y < 0 || x >= this.w || y >= this.h) return;
		const i = (y * this.w + x) * 4;
		this.d[i] = (c >> 16) & 255; this.d[i + 1] = (c >> 8) & 255; this.d[i + 2] = c & 255;
		this.d[i + 3] = c === null ? 0 : 255;
	}
	rect(x, y, w, h, c) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.set(x + i, y + j, c); }
	shaded(x, y, w, h, c, amt = 10, seed = 1) {
		for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.set(x + i, y + j, jitter(c, amt, x + i, y + j, seed));
	}
	save(file) { fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, encode(this.w, this.h, this.d)); }
}
const hash = (x, y, s) => { let h = (x * 374761393 + y * 668265263 + s * 2147483647) >>> 0; h = ((h ^ (h >>> 13)) * 1274126177) >>> 0; return (h ^ (h >>> 16)) >>> 0; };
const clamp = (v) => Math.max(0, Math.min(255, v));
function shade(c, k) { return (clamp(((c >> 16) & 255) + k) << 16) | (clamp(((c >> 8) & 255) + k) << 8) | clamp((c & 255) + k); }
function jitter(c, amt, x, y, s) { return shade(c, (hash(x, y, s) % (amt * 2 + 1)) - amt); }

const RED = 0xa3191f, RED_D = 0x6e0f14, RED_L = 0xc8302f;
const GOLD = 0xd9a43a, GOLD_D = 0xa87a22, GOLD_L = 0xf2d27a;
const GUN = 0x3a3f48, GUN_D = 0x262a31, GUN_L = 0x565d68;
const CYAN = 0x5fd8ff, CYAN_D = 0x1f8fc0, CORE = 0xe8fbff;
const NAVY = 0x0d1a2a;

// ---------------------------------------------------------------- block textures
const B = (n) => path.join(ASSETS, 'textures/block', 'stark_sorting_station_' + n + '.png');

{ // base: gunmetal plinth, gold stripe + cyan running lights in the band the base element shows (rows 13-15)
	const t = new Img(16, 16);
	t.shaded(0, 0, 16, 16, GUN, 6, 3);
	for (let x = 0; x < 16; x++) { t.set(x, 13, GOLD); t.set(x, 0, GUN_L); }
	for (let x = 2; x < 16; x += 4) t.set(x, 14, CYAN);
	t.rect(0, 15, 16, 1, GUN_D);
	t.save(B('base'));
}
{ // side: red armour panels with gold edge trim and a centre seam
	const t = new Img(16, 16);
	t.shaded(0, 0, 16, 16, RED, 8, 5);
	for (let y = 0; y < 16; y++) { t.set(1, y, GOLD); t.set(14, y, GOLD); t.set(2, y, GOLD_D); t.set(13, y, GOLD_D); }
	for (let x = 3; x < 13; x++) { t.set(x, 8, RED_D); t.set(x, 4, RED_L); }
	for (const [x, y] of [[4, 6], [11, 6], [4, 11], [11, 11]]) t.set(x, y, GOLD_L);
	for (let y = 10; y < 13; y++) for (let x = 6; x < 10; x += 2) t.set(x, y, GUN_D); // vents
	t.save(B('side'));
}
{ // front: darker red behind the holo screen, gold frame
	const t = new Img(16, 16);
	t.shaded(0, 0, 16, 16, RED_D, 6, 7);
	for (let y = 0; y < 16; y++) { t.set(1, y, GOLD); t.set(14, y, GOLD); }
	for (let x = 1; x < 15; x++) { t.set(x, 4, GOLD); t.set(x, 12, GOLD_D); }
	t.save(B('front'));
}
{ // screen (top half is mapped onto the console face): navy holo display, cyan frame, sorted "bins"
	const t = new Img(16, 16);
	t.rect(0, 0, 16, 16, GUN);
	t.rect(0, 0, 16, 8, NAVY);
	for (let x = 0; x < 16; x++) { t.set(x, 0, CYAN_D); t.set(x, 7, CYAN_D); }
	for (let y = 0; y < 8; y++) { t.set(0, y, CYAN_D); t.set(15, y, CYAN_D); }
	const bins = [[2, 0x66e0a0, 3], [5, 0xffc857, 2], [8, 0xff6b6b, 4], [11, CYAN, 3]];
	for (const [x, c, hgt] of bins) { for (let y = 6 - hgt; y < 6; y++) { t.set(x, y, c); t.set(x + 1, y, shade(c, -30)); } }
	t.set(13, 2, CORE); t.set(13, 4, CYAN);
	t.save(B('screen'));
}
{ // rim: polished gold band
	const t = new Img(16, 16);
	t.shaded(0, 0, 16, 16, GOLD, 7, 9);
	for (let x = 0; x < 16; x++) { t.set(x, 2, GOLD_L); t.set(x, 3, GOLD_D); t.set(x, 15, GOLD_D); }
	t.save(B('rim'));
}
{ // top: gold border, gunmetal deck with cyan corner lights
	const t = new Img(16, 16);
	t.shaded(0, 0, 16, 16, GOLD, 6, 11);
	t.shaded(2, 2, 12, 12, GUN, 5, 12);
	for (const [x, y] of [[2, 2], [13, 2], [2, 13], [13, 13]]) t.set(x, y, CYAN);
	t.save(B('top'));
}
{ // pad: the launch pad -- an arc-reactor ring (the element shows the centre 10x10)
	const t = new Img(16, 16);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const dx = x - 7.5, dy = y - 7.5, r = Math.sqrt(dx * dx + dy * dy);
		let c = jitter(GUN_D, 4, x, y, 13);
		if (r < 1.6) c = CORE;
		else if (r < 2.6) c = CYAN;
		else if (r < 3.4) c = GUN;
		else if (r < 4.4) c = CYAN_D;
		else if (r < 5.0) c = GOLD;
		t.set(x, y, c);
	}
	for (const a of [0, 1, 2, 3, 4, 5, 6, 7]) { // the reactor's segment lines
		const ang = a * Math.PI / 4;
		t.set(Math.round(7.5 + Math.cos(ang) * 3.9), Math.round(7.5 + Math.sin(ang) * 3.9), CYAN);
	}
	t.save(B('pad'));
}
{ // pylon: gold sensor masts with a cyan emitter at the top
	const t = new Img(16, 16);
	t.shaded(0, 0, 16, 16, GOLD_D, 6, 15);
	t.rect(0, 0, 16, 1, CORE);
	t.rect(0, 1, 16, 1, CYAN);
	for (let x = 0; x < 16; x += 2) t.set(x, 3, GOLD_L);
	t.save(B('pylon'));
}

// ---------------------------------------------------------------- block model
const face = (tex, extra = {}) => Object.assign({ texture: '#' + tex }, extra);
const allFaces = (tex, overrides = {}) => {
	const f = {};
	for (const d of ['north', 'south', 'east', 'west', 'up', 'down']) f[d] = face(tex);
	return Object.assign(f, overrides);
};
const blockModel = {
	parent: 'block/block',
	ambientocclusion: false,
	textures: {
		particle: 'projecthero:block/stark_sorting_station_side',
		base: 'projecthero:block/stark_sorting_station_base',
		side: 'projecthero:block/stark_sorting_station_side',
		front: 'projecthero:block/stark_sorting_station_front',
		screen: 'projecthero:block/stark_sorting_station_screen',
		rim: 'projecthero:block/stark_sorting_station_rim',
		top: 'projecthero:block/stark_sorting_station_top',
		pad: 'projecthero:block/stark_sorting_station_pad',
		pylon: 'projecthero:block/stark_sorting_station_pylon',
	},
	elements: [
		{ from: [0, 0, 0], to: [16, 3, 16], faces: allFaces('base', { down: face('base', { cullface: 'down' }), up: face('top') }) },
		{ from: [1, 3, 1], to: [15, 12, 15], faces: allFaces('side', { north: face('front'), up: face('top'), down: face('base') }) },
		{ from: [3, 5, 0.25], to: [13, 10, 1], faces: allFaces('rim', { north: face('screen', { uv: [0, 0, 16, 8] }) }) },
		{ from: [0, 12, 0], to: [16, 14, 16], faces: allFaces('rim', { up: face('top'), down: face('rim') }) },
		{ from: [3, 14, 3], to: [13, 15, 13], faces: allFaces('rim', { up: face('pad') }) },
		{ from: [1, 14, 12], to: [3, 18, 14], faces: allFaces('pylon', {
			north: face('pylon', { uv: [0, 0, 2, 4] }), south: face('pylon', { uv: [2, 0, 4, 4] }),
			east: face('pylon', { uv: [4, 0, 6, 4] }), west: face('pylon', { uv: [6, 0, 8, 4] }),
			up: face('pylon', { uv: [0, 0, 2, 2] }), down: face('pylon', { uv: [0, 4, 2, 6] }) }) },
		{ from: [13, 14, 12], to: [15, 18, 14], faces: allFaces('pylon', {
			north: face('pylon', { uv: [0, 0, 2, 4] }), south: face('pylon', { uv: [2, 0, 4, 4] }),
			east: face('pylon', { uv: [4, 0, 6, 4] }), west: face('pylon', { uv: [6, 0, 8, 4] }),
			up: face('pylon', { uv: [0, 0, 2, 2] }), down: face('pylon', { uv: [0, 4, 2, 6] }) }) },
	],
};
const writeJson = (rel, obj) => {
	const f = path.join(ASSETS, rel);
	fs.mkdirSync(path.dirname(f), { recursive: true });
	fs.writeFileSync(f, JSON.stringify(obj, null, 2) + '\n');
};
writeJson('models/block/stark_sorting_station.json', blockModel);
writeJson('models/item/stark_sorting_station.json', { parent: 'projecthero:block/stark_sorting_station' });
writeJson('blockstates/stark_sorting_station.json', { variants: {
	'facing=north': { model: 'projecthero:block/stark_sorting_station' },
	'facing=east': { model: 'projecthero:block/stark_sorting_station', y: 90 },
	'facing=south': { model: 'projecthero:block/stark_sorting_station', y: 180 },
	'facing=west': { model: 'projecthero:block/stark_sorting_station', y: 270 },
} });

// ---------------------------------------------------------------- the bot: geo + box-UV texture + glowmask
// cube: [name, bone, origin, size, uv, colours{top,bottom,front,back,side}, glow?]
const tex = new Img(64, 64);
const glow = new Img(64, 64);
for (let i = 0; i < glow.d.length; i += 4) glow.d[i + 3] = 0;
const cubes = [];
function cube(bone, origin, size, uv, col, glowing = false) {
	cubes.push({ bone, origin, size, uv });
	const [w, h, d] = size;
	const [u, v] = uv;
	const paint = (img, x, y, ww, hh, c, s) => img.shaded(x, y, ww, hh, c, glowing ? 3 : 7, s);
	const fc = (k) => col[k] !== undefined ? col[k] : col.side;
	const regions = [
		['top', u + d, v, w, d], ['bottom', u + d + w, v, w, d],
		['side', u, v + d, d, h], ['front', u + d, v + d, w, h],
		['side', u + d + w, v + d, d, h], ['back', u + 2 * d + w, v + d, w, h],
	];
	let s = 20;
	for (const [k, x, y, ww, hh] of regions) {
		paint(tex, x, y, ww, hh, fc(k), s++);
		if (glowing) paint(glow, x, y, ww, hh, fc(k), s);
	}
	return regions;
}
const red = { side: RED, top: RED_L, bottom: RED_D };
const gold = { side: GOLD, top: GOLD_L, bottom: GOLD_D };
const gun = { side: GUN, top: GUN_L, bottom: GUN_D };
const cyan = { side: CYAN, front: CORE, top: CORE, bottom: CYAN };

const torso = cube('body', [-4, 6, -3], [8, 7, 6], [0, 0], { side: RED, top: RED_L, bottom: RED_D, front: RED, back: RED_D });
cube('body', [-3, 7, -3.5], [6, 5, 1], [0, 14], gold);
cube('body', [-1.5, 8, -4], [3, 3, 1], [16, 14], cyan, true);
cube('body', [-2.5, 3, -2.5], [5, 3, 5], [28, 0], gun);
cube('flame', [-1.5, 1, -1.5], [3, 2, 3], [48, 0], { side: CYAN, top: CORE, bottom: CORE }, true);
cube('head', [-3, 13, -3], [6, 5, 6], [0, 20], { side: RED, top: RED_L, bottom: RED_D, front: GOLD, back: RED_D });
cube('head', [-2.5, 15, -3.5], [5, 1, 1], [26, 20], cyan, true);
cube('antenna', [-0.5, 18, -0.5], [1, 2, 1], [40, 20], gold);
cube('antenna', [-0.5, 20, -0.5], [1, 1, 1], [46, 20], cyan, true);
cube('left_arm', [4, 9, -1.5], [2, 4, 3], [0, 32], red);
cube('left_arm', [4, 7, -2], [2, 2, 3], [12, 32], gold);
cube('left_arm', [3.5, 11.5, -2], [3, 2, 4], [0, 40], gold);
cube('right_arm', [-6, 9, -1.5], [2, 4, 3], [24, 32], red);
cube('right_arm', [-6, 7, -2], [2, 2, 3], [36, 32], gold);
cube('right_arm', [-6.5, 11.5, -2], [3, 2, 4], [16, 40], gold);
// face details: gold faceplate gets a darker mouth line; the torso gets a gold belt line
for (let x = 7; x < 11; x++) tex.set(x, 30, GOLD_D);
for (let x = 6; x < 14; x++) tex.set(x, 11, RED_D);
tex.save(path.join(ASSETS, 'textures/entity/stark_sorter_bot.png'));
glow.save(path.join(ASSETS, 'textures/entity/stark_sorter_bot_glowmask.png'));

const bones = [
	{ name: 'root', pivot: [0, 0, 0] },
	{ name: 'body', parent: 'root', pivot: [0, 7, 0] },
	{ name: 'flame', parent: 'body', pivot: [0, 3, 0] },
	{ name: 'head', parent: 'body', pivot: [0, 13, 0] },
	{ name: 'antenna', parent: 'head', pivot: [0, 18, 0] },
	{ name: 'left_arm', parent: 'body', pivot: [5, 12.5, 0] },
	{ name: 'right_arm', parent: 'body', pivot: [-5, 12.5, 0] },
];
for (const b of bones) {
	const cs = cubes.filter(c => c.bone === b.name).map(c => ({ origin: c.origin, size: c.size, uv: c.uv }));
	if (cs.length) b.cubes = cs;
}
writeJson('geo/stark_sorter_bot.geo.json', {
	format_version: '1.12.0',
	'minecraft:geometry': [{
		description: {
			identifier: 'geometry.stark_sorter_bot', texture_width: 64, texture_height: 64,
			visible_bounds_width: 2, visible_bounds_height: 2.5, visible_bounds_offset: [0, 0.75, 0],
		},
		bones,
	}],
});

// ---------------------------------------------------------------- animations
const A = 'animation.stark_sorter_bot.';
writeJson('animations/stark_sorter_bot.animation.json', {
	format_version: '1.8.0',
	animations: {
		[A + 'idle']: { loop: true, animation_length: 2.0, bones: {
			root: { position: { '0.0': [0, 0, 0], '1.0': [0, 0.8, 0], '2.0': [0, 0, 0] } },
			head: { rotation: { '0.0': [0, 0, 0], '0.5': [0, 10, 0], '1.5': [0, -10, 0], '2.0': [0, 0, 0] } },
			left_arm: { rotation: { '0.0': [0, 0, -5], '1.0': [0, 0, -12], '2.0': [0, 0, -5] } },
			right_arm: { rotation: { '0.0': [0, 0, 5], '1.0': [0, 0, 12], '2.0': [0, 0, 5] } },
			flame: { scale: { '0.0': [1, 1, 1], '1.0': [1, 1.35, 1], '2.0': [1, 1, 1] } },
			antenna: { rotation: { '0.0': [0, 0, -4], '1.0': [0, 0, 4], '2.0': [0, 0, -4] } },
		} },
		[A + 'fly']: { loop: true, animation_length: 0.5, bones: {
			body: { rotation: { '0.0': [14, 0, 0], '0.25': [16, 0, 0], '0.5': [14, 0, 0] } },
			root: { position: { '0.0': [0, 0, 0], '0.25': [0, 0.4, 0], '0.5': [0, 0, 0] } },
			left_arm: { rotation: { '0.0': [-32, 0, -4], '0.25': [-38, 0, -4], '0.5': [-32, 0, -4] } },
			right_arm: { rotation: { '0.0': [-32, 0, 4], '0.25': [-38, 0, 4], '0.5': [-32, 0, 4] } },
			head: { rotation: { '0.0': [-10, 0, 0], '0.5': [-10, 0, 0] } },
			flame: { scale: { '0.0': [1.2, 1.7, 1.2], '0.25': [1.25, 2.1, 1.25], '0.5': [1.2, 1.7, 1.2] } },
			antenna: { rotation: { '0.0': [18, 0, 0], '0.5': [18, 0, 0] } },
		} },
		[A + 'pickup']: { loop: true, animation_length: 0.25, bones: {
			body: { rotation: { '0.0': [0, 0, 0], '0.125': [20, 0, 0], '0.25': [0, 0, 0] } },
			left_arm: { rotation: { '0.0': [-30, 0, 0], '0.125': [-85, 0, 0], '0.25': [-40, 0, 0] } },
			right_arm: { rotation: { '0.0': [-30, 0, 0], '0.125': [-85, 0, 0], '0.25': [-40, 0, 0] } },
			head: { rotation: { '0.0': [0, 0, 0], '0.125': [15, 0, 0], '0.25': [0, 0, 0] } },
		} },
		[A + 'deposit']: { loop: true, animation_length: 0.5, bones: {
			body: { rotation: { '0.0': [5, 0, 0], '0.15': [25, 0, 0], '0.35': [25, 0, 0], '0.5': [5, 0, 0] } },
			left_arm: { rotation: { '0.0': [-35, 0, 0], '0.15': [-100, 0, -6], '0.35': [-100, 0, -6], '0.5': [-35, 0, 0] } },
			right_arm: { rotation: { '0.0': [-35, 0, 0], '0.15': [-100, 0, 6], '0.35': [-100, 0, 6], '0.5': [-35, 0, 0] } },
			head: { rotation: { '0.0': [0, 0, 0], '0.15': [18, 0, 0], '0.35': [18, 0, 0], '0.5': [0, 0, 0] } },
		} },
		[A + 'spawn']: { loop: 'hold_on_last_frame', animation_length: 0.8, bones: {
			root: {
				scale: { '0.0': [0.05, 0.05, 0.05], '0.5': [1.15, 1.15, 1.15], '0.8': [1, 1, 1] },
				rotation: { '0.0': [0, -360, 0], '0.8': [0, 0, 0] },
			},
			left_arm: { rotation: { '0.0': [0, 0, -90], '0.5': [0, 0, -90], '0.8': [0, 0, -5] } },
			right_arm: { rotation: { '0.0': [0, 0, 90], '0.5': [0, 0, 90], '0.8': [0, 0, 5] } },
			head: { scale: { '0.0': [0.2, 0.2, 0.2], '0.4': [0.2, 0.2, 0.2], '0.7': [1, 1, 1] } },
		} },
		[A + 'despawn']: { loop: 'hold_on_last_frame', animation_length: 0.7, bones: {
			root: {
				scale: { '0.0': [1, 1, 1], '0.25': [1.15, 1.15, 1.15], '0.7': [0.02, 0.02, 0.02] },
				rotation: { '0.0': [0, 0, 0], '0.7': [0, 360, 0] },
			},
			left_arm: { rotation: { '0.0': [0, 0, -5], '0.3': [0, 0, -90] } },
			right_arm: { rotation: { '0.0': [0, 0, 5], '0.3': [0, 0, 90] } },
		} },
	},
});
console.log('stark sorter assets written');
