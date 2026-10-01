// v0.14.9 Superman Suit: every asset of the craftable, Kryptonian-only armour set, from the user's Blockbench model
// (C:/Users/ethan/OneDrive/Desktop/3d minecraft models/kryptonian/superman.bbmodel).
//
// The model is a plain 64x64 player skin on a skin-shaped rig (Head/Hat Layer, Body/Body Layer, arms and legs each a base
// cube plus an inflated second-layer cube) -- the same rig as Moon Knight's suit, so the geometry is moon_knight.geo.json
// under a new identifier (GeckoLib armour bone names, same UVs, base +0.3 / layers +0.3 on top of the model's own 0.5 /
// 0.25 layer inflation; Blockbench's baked display-pose rotations are dropped), plus real boot cubes (the bottom 4 px of
// each leg, base and layer) so the boots piece shows on its own. The skin has no head art at all, so the helmet piece is
// invisible -- your own face shows, as it should for Superman.
//
// The cape: the skin PAINTS a flat red cape (with a yellow shield) onto the second layer -- the whole Body Layer (back
// face, side-face edges, the top face's back edge and shoulder straps, the bottom edge) and the back of both leg layers
// (back face, the column either side of it, the top face's back row). Every one of those pixels is cleared here, so the
// blue suit of the base layer shows through; the cape is now a real cloth cape (SupermanCapeLayer, built on Moon Knight's
// cape mesh) with its own texture, made below in the same red with the shield on the back.
//
// Also: the four item icons (cut from the cleaned skin; the helmet icon is drawn), the item models and the recipes.
// Run from the repo root:  node scratchpad/gen_superman_suit.js   (re-runnable)
//   PREVIEW=<dir> node scratchpad/gen_superman_suit.js   also writes upscaled before/after/cape previews into <dir>
const fs = require('fs');
const path = require('path');
const { decode, encode, upscale } = require('./pngkit');

const SRC = 'C:/Users/ethan/OneDrive/Desktop/3d minecraft models/kryptonian/superman.bbmodel';
const RES = path.join(__dirname, '..', 'src/main/resources');
const A = path.join(RES, 'assets/projecthero');
const D = path.join(RES, 'data/projecthero');
const PREVIEW = process.env.PREVIEW;
const model = JSON.parse(fs.readFileSync(SRC, 'utf8'));
const json = (p, obj) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, JSON.stringify(obj, null, 2) + '\n'); };

// ---- geometry: Moon Knight's skin rig + boot cubes
const geo = JSON.parse(fs.readFileSync(path.join(A, 'geo/moon_knight.geo.json'), 'utf8'));
const g = geo['minecraft:geometry'][0];
g.description.identifier = 'geometry.superman';
for (const bone of g.bones) {
	if (bone.name === 'armorRightBoot') {
		bone.cubes = [
			{ name: 'right_boot_base', origin: [-3.9, 0, -2], size: [4, 4, 4], uv: [0, 24], inflate: 0.35 },
			{ name: 'right_boot_shell', origin: [-3.9, 0, -2], size: [4, 4, 4], uv: [0, 40], inflate: 0.6 },
		];
	} else if (bone.name === 'armorLeftBoot') {
		bone.cubes = [
			{ name: 'left_boot_base', origin: [-0.1, 0, -2], size: [4, 4, 4], uv: [16, 56], inflate: 0.35 },
			{ name: 'left_boot_shell', origin: [-0.1, 0, -2], size: [4, 4, 4], uv: [0, 56], inflate: 0.6 },
		];
	}
}
fs.writeFileSync(path.join(A, 'geo/superman.geo.json'), JSON.stringify(geo, null, '\t'));

// ---- suit texture: the embedded skin with the painted cape cleared
const srcPng = Buffer.from(model.textures[0].source.split(',')[1], 'base64');
const tmp = path.join(require('os').tmpdir(), 'superman_src_' + process.pid + '.png');
fs.writeFileSync(tmp, srcPng);
const skin = decode(tmp);
fs.unlinkSync(tmp);
const before = { w: skin.w, h: skin.h, px: Buffer.from(skin.px) };
const at = (x, y) => { const i = (y * 64 + x) * 4; return [skin.px[i], skin.px[i + 1], skin.px[i + 2], skin.px[i + 3]]; };
// the cape's colours, read off the painted cape before it goes
const CAPE_RED = at(35, 46).slice(0, 3);
const SHIELD_GOLD = at(35, 39).slice(0, 3);
const clear = (x0, y0, x1, y1) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) skin.px.fill(0, (y * 64 + x) * 4, (y * 64 + x) * 4 + 4); };
// Body Layer (uv 16,32): every pixel of it is cape -- top face back edge + shoulder straps, bottom edge, side-face
// edges, the strap ends on the front face, the whole back face with the shield
clear(16, 32, 39, 47);
// both leg layers (uv 0,32 and 0,48): the top face's back row, the back face, and the column either side of it
for (const v of [32, 48]) {
	clear(4, v, 7, v);              // up face, back edge
	clear(0, v + 4, 0, v + 13);     // east face, the column next to the back face
	clear(11, v + 4, 15, v + 15);   // west face's last column + the whole back face
}
let left = 0;
for (let y = 32; y < 64; y++) for (let x = 0; x < 16; x++) if (skin.px[(y * 64 + x) * 4 + 3]) left++;
fs.writeFileSync(path.join(A, 'textures/armor/superman.png'), encode(64, 64, skin.px));

// ---- the cape texture (original, in the cape's own colours): 64x48 -- square texels on the 0.86 x 1.25 block cape.
// Left half = the outside (red cloth with soft vertical folds, a darker hem, the shield high on the back); right half
// = the inside lining (a shade darker). The cape's u runs from the wearer's RIGHT edge to his left, so seen from behind
// the texture is mirrored -- the shield is stamped mirrored so its S reads the right way round.
const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));
const W = 64, H = 48;
const cape = Buffer.alloc(W * H * 4);
const put = (x, y, c) => { const i = (y * W + x) * 4; cape[i] = c[0]; cape[i + 1] = c[1]; cape[i + 2] = c[2]; cape[i + 3] = 255; };
const SHADE = mix(CAPE_RED, [60, 0, 10], 0.35);
const HEM = mix(CAPE_RED, [40, 0, 5], 0.5);
for (let half = 0; half < 2; half++) {
	for (let y = 0; y < H; y++) {
		for (let x = 0; x < 32; x++) {
			const fold = 0.5 + 0.5 * Math.sin(x * 0.8 + y * 0.06);
			let c = mix(CAPE_RED, SHADE, 0.5 * (1 - fold) + (y < 2 ? 0.2 : 0));
			if (y >= H - 2 || x === 0 || x === 31) c = mix(c, HEM, 0.7);
			if (half === 1) c = mix(c, [70, 0, 12], 0.3);
			put(half * 32 + x, y, c);
		}
	}
}
// the shield, 14 x 12: K = dark outline, Y = gold, R = the red S
const SHIELD = [
	'..KKKKKKKKKK..',
	'.KYYYYYYYYYYK.',
	'KYYRRRRRRRRYYK',
	'KYRRYYYYYYYYYK',
	'.KRRRRRRRRRYK.',
	'.KYYYYYYYRRYK.',
	'..KYYYYYYRRK..',
	'..KRRRRRRRRK..',
	'...KYYYYYYK...',
	'....KYYYYK....',
	'.....KYYK.....',
	'......KK......',
];
const OUTLINE = mix(CAPE_RED, [30, 0, 0], 0.6);
const S_RED = mix(CAPE_RED, [255, 40, 40], 0.15);
for (let r = 0; r < SHIELD.length; r++) {
	for (let c = 0; c < 14; c++) {
		const ch = SHIELD[r][13 - c]; // mirrored, see above
		if (ch === '.') continue;
		put(9 + c, 4 + r, ch === 'K' ? OUTLINE : ch === 'Y' ? SHIELD_GOLD : S_RED);
	}
}
fs.mkdirSync(path.join(A, 'textures/entity'), { recursive: true });
fs.writeFileSync(path.join(A, 'textures/entity/superman_cape.png'), encode(W, H, cape));

// ---- icons: cut from the cleaned skin (the helmet, which has no art on the skin, is drawn)
function icon(parts) {
	const px = Buffer.alloc(16 * 16 * 4);
	for (const p of parts) {
		for (let y = 0; y < p.h * p.k; y++) {
			for (let x = 0; x < p.w * p.k; x++) {
				const sx = p.u + Math.floor(x / p.k), sy = p.v + Math.floor(y / p.k);
				const s = (sy * 64 + sx) * 4, dx = p.x + x, dy = p.y + y;
				if (dx < 0 || dy < 0 || dx >= 16 || dy >= 16 || skin.px[s + 3] === 0) continue;
				skin.px.copy(px, (dy * 16 + dx) * 4, s, s + 4);
			}
		}
	}
	return px;
}
const ITEM = path.join(A, 'textures/item');
const icons = {};
{
	// a blue cowl with the gold-and-red shield on the brow
	const BLUE = at(36, 24).slice(0, 3);
	const DARK = mix(BLUE, [0, 0, 30], 0.45);
	const px = Buffer.alloc(16 * 16 * 4);
	const p = (x, y, c) => { const i = (y * 16 + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255; };
	const rows = ['....KKKKKKKK....', '...KBBBBBBBBK...', '..KBBBBBBBBBBK..', '..KBBBYYYYBBBK..', '..KBBYRRRRYBBK..',
		'..KBBBYRRYBBBK..', '..KBBBBYYBBBBK..', '..KBBK....KBBK..', '..KBBK....KBBK..', '..KBK......KBK..', '...K........K...'];
	rows.forEach((row, y) => [...row].forEach((ch, x) => {
		if (ch === 'K') p(x, y + 3, DARK); else if (ch === 'B') p(x, y + 3, mix(BLUE, [255, 255, 255], x < 7 ? 0.12 : 0));
		else if (ch === 'Y') p(x, y + 3, SHIELD_GOLD); else if (ch === 'R') p(x, y + 3, CAPE_RED);
	}));
	icons.helmet = px;
}
icons.chestplate = icon([
	{ u: 20, v: 20, w: 8, h: 12, k: 1, x: 4, y: 2 },
	{ u: 44, v: 20, w: 4, h: 12, k: 1, x: 0, y: 2 }, { u: 44, v: 36, w: 4, h: 12, k: 1, x: 0, y: 2 },
	{ u: 36, v: 52, w: 4, h: 12, k: 1, x: 12, y: 2 }, { u: 52, v: 52, w: 4, h: 12, k: 1, x: 12, y: 2 }]);
icons.leggings = icon([
	{ u: 4, v: 20, w: 4, h: 12, k: 1, x: 4, y: 2 }, { u: 4, v: 36, w: 4, h: 12, k: 1, x: 4, y: 2 },
	{ u: 20, v: 52, w: 4, h: 12, k: 1, x: 8, y: 2 }, { u: 4, v: 52, w: 4, h: 12, k: 1, x: 8, y: 2 }]);
{
	// a pair of red boots (the skin's boots are flat red, so they are drawn)
	const RED = at(4, 29).slice(0, 3);
	const px = Buffer.alloc(16 * 16 * 4);
	const p = (x, y, c) => { const i = (y * 16 + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255; };
	const rows = ['.KKKK....KKKK...', '.KRRK....KRRK...', '.KRRK....KRRK...', '.KRRK....KRRK...', '.KRRK....KRRK...',
		'.KRRRK...KRRRK..', 'KRRRRRK.KRRRRRK.', 'KSSSSSK.KSSSSSK.'];
	rows.forEach((row, y) => [...row].forEach((ch, x) => {
		if (ch === 'K') p(x, y + 4, mix(RED, [20, 0, 0], 0.6)); else if (ch === 'R') p(x, y + 4, mix(RED, [255, 255, 255], x % 9 < 3 ? 0.15 : 0));
		else if (ch === 'S') p(x, y + 4, mix(RED, [20, 0, 0], 0.35));
	}));
	icons.boots = px;
}
for (const piece of ['helmet', 'chestplate', 'leggings', 'boots']) {
	fs.writeFileSync(path.join(ITEM, `superman_suit_${piece}.png`), encode(16, 16, icons[piece]));
	json(path.join(A, `models/item/superman_suit_${piece}.json`),
		{ parent: 'minecraft:item/generated', textures: { layer0: `projecthero:item/superman_suit_${piece}` } });
}

// ---- recipes: blue and red wool, gold and diamonds (never kryptonite -- it would hurt the wearer)
const recipe = (piece, pattern, key) => json(path.join(D, `recipe/superman_suit_${piece}.json`), {
	type: 'minecraft:crafting_shaped', category: 'equipment', pattern,
	key: Object.fromEntries(Object.entries(key).map(([k, id]) => [k, { item: id }])),
	result: { id: `projecthero:superman_suit_${piece}`, count: 1 },
});
const B = 'minecraft:blue_wool', R = 'minecraft:red_wool', Gi = 'minecraft:gold_ingot', Gb = 'minecraft:gold_block', Dm = 'minecraft:diamond';
recipe('helmet', ['BDB', 'G G'], { B, D: Dm, G: Gi });
recipe('chestplate', ['R R', 'DSD', 'BBB'], { R, D: Dm, S: Gb, B });
recipe('leggings', ['GRG', 'B B', 'D D'], { G: Gi, R, B, D: Dm });
recipe('boots', ['R R', 'D D'], { R, D: Dm });

if (PREVIEW) {
	const grid = (img, k) => {
		const u = upscale(img, k, true);
		for (let y = 0; y < u.h; y++) for (let x = 0; x < u.w; x++) {
			if (x % (4 * k) === 0 || y % (4 * k) === 0) { const d = (y * u.w + x) * 4; u.px[d] = 0; u.px[d + 1] = 200; u.px[d + 2] = 0; }
		}
		return u;
	};
	for (const [n, img] of [['superman_before', before], ['superman_after', skin], ['superman_cape', { w: W, h: H, px: cape }]]) {
		const u = grid(img, 10);
		fs.writeFileSync(path.join(PREVIEW, n + '.png'), encode(u.w, u.h, u.px));
	}
	const sheet = Buffer.alloc(4 * 16 * 16 * 4 * 64);
	['helmet', 'chestplate', 'leggings', 'boots'].forEach((n, i) => {
		const u = upscale({ w: 16, h: 16, px: icons[n] }, 8, true);
		for (let y = 0; y < 128; y++) u.px.copy(sheet, (y * 512 + i * 128) * 4, y * 128 * 4, (y + 1) * 128 * 4);
	});
	fs.writeFileSync(path.join(PREVIEW, 'superman_icons.png'), encode(512, 128, sheet));
}
console.log('superman suit: geo, texture (layer-2 pixels left in the leg layers: ' + left + '), cape ' + CAPE_RED + ' / shield '
	+ SHIELD_GOLD + ', 4 icons, 4 item models, 4 recipes');
