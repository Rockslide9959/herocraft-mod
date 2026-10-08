// v0.14.11 Flash Suit: every asset of the craftable, speedster-only armour set and its ring, from the user's Blockbench
// model (C:/Users/ethan/OneDrive/Desktop/3d minecraft models/flash/flash.bbmodel).
//
// The model is a plain 64x64 player skin on the same skin rig as the Superman Suit (Head/Hat Layer, Body/Body Layer,
// arms and legs each a base cube plus an inflated second-layer cube), so the geometry is superman.geo.json (Moon
// Knight's rig + boot cubes) under a new identifier, and the skin is used as it is (the cowl leaves the jaw open, so the
// wearer's own mouth and chin show through).
//
// Also: the four suit icons (cut from the skin), the Flash Ring's icon and its on-the-hand texture (drawn), the item
// models and the four recipes. Run from the repo root:  node scratchpad/gen_flash_suit.js   (re-runnable)
//   PREVIEW=<dir> node scratchpad/gen_flash_suit.js   also writes an upscaled icon sheet into <dir>
const fs = require('fs');
const path = require('path');
const { decode, encode, upscale } = require('./pngkit');

const SRC = 'C:/Users/ethan/OneDrive/Desktop/3d minecraft models/flash/flash.bbmodel';
const RES = path.join(__dirname, '..', 'src/main/resources');
const A = path.join(RES, 'assets/projecthero');
const D = path.join(RES, 'data/projecthero');
const PREVIEW = process.env.PREVIEW;
const model = JSON.parse(fs.readFileSync(SRC, 'utf8'));
const json = (p, obj) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, JSON.stringify(obj, null, 2) + '\n'); };

// ---- geometry: the Superman Suit's skin rig
const geo = JSON.parse(fs.readFileSync(path.join(A, 'geo/superman.geo.json'), 'utf8'));
geo['minecraft:geometry'][0].description.identifier = 'geometry.flash';
fs.writeFileSync(path.join(A, 'geo/flash.geo.json'), JSON.stringify(geo, null, '\t'));

// ---- suit texture: the embedded skin, as it is
const srcPng = Buffer.from(model.textures[0].source.split(',')[1], 'base64');
const tmp = path.join(require('os').tmpdir(), 'flash_src_' + process.pid + '.png');
fs.writeFileSync(tmp, srcPng);
const skin = decode(tmp);
fs.unlinkSync(tmp);
if (skin.w !== 64 || skin.h !== 64) throw new Error('expected a 64x64 skin, got ' + skin.w + 'x' + skin.h);
fs.writeFileSync(path.join(A, 'textures/armor/flash.png'), encode(64, 64, skin.px));
const at = (x, y) => { const i = (y * 64 + x) * 4; return [skin.px[i], skin.px[i + 1], skin.px[i + 2], skin.px[i + 3]]; };

// ---- icons, cut from the skin (k = scale; later parts draw over earlier ones, transparent texels skipped)
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
const icons = {
	// the cowl's front face and its second layer, doubled
	helmet: icon([{ u: 8, v: 8, w: 8, h: 8, k: 2, x: 0, y: 0 }, { u: 40, v: 8, w: 8, h: 8, k: 2, x: 0, y: 0 }]),
	chestplate: icon([
		{ u: 20, v: 20, w: 8, h: 12, k: 1, x: 4, y: 2 }, { u: 20, v: 36, w: 8, h: 12, k: 1, x: 4, y: 2 },
		{ u: 44, v: 20, w: 4, h: 12, k: 1, x: 0, y: 2 }, { u: 44, v: 36, w: 4, h: 12, k: 1, x: 0, y: 2 },
		{ u: 36, v: 52, w: 4, h: 12, k: 1, x: 12, y: 2 }, { u: 52, v: 52, w: 4, h: 12, k: 1, x: 12, y: 2 }]),
	leggings: icon([
		{ u: 4, v: 20, w: 4, h: 12, k: 1, x: 4, y: 2 }, { u: 4, v: 36, w: 4, h: 12, k: 1, x: 4, y: 2 },
		{ u: 20, v: 52, w: 4, h: 12, k: 1, x: 8, y: 2 }, { u: 4, v: 52, w: 4, h: 12, k: 1, x: 8, y: 2 }]),
	// the bottom four rows of each leg's front (base + layer), doubled
	boots: icon([
		{ u: 4, v: 28, w: 4, h: 4, k: 2, x: -1, y: 6 }, { u: 4, v: 44, w: 4, h: 4, k: 2, x: -1, y: 6 },
		{ u: 20, v: 60, w: 4, h: 4, k: 2, x: 9, y: 6 }, { u: 4, v: 60, w: 4, h: 4, k: 2, x: 9, y: 6 }]),
};

// ---- the Flash Ring: a gold band with the red lightning emblem (icon), and the 16x16 the hand-band samples
const GOLD = [236, 178, 52], GOLD_HI = [255, 226, 120], GOLD_DK = [150, 96, 20], RED = [205, 22, 24], WHITE = [255, 244, 214];
const ringIcon = Buffer.alloc(16 * 16 * 4);
{
	const p = (x, y, c) => { const i = (y * 16 + x) * 4; ringIcon[i] = c[0]; ringIcon[i + 1] = c[1]; ringIcon[i + 2] = c[2]; ringIcon[i + 3] = 255; };
	// the band: an ellipse seen slightly from above
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const dx = (x + 0.5 - 8) / 6.2, dy = (y + 0.5 - 9.5) / 4.6, r = Math.sqrt(dx * dx + dy * dy);
		if (r <= 1.0 && r >= 0.62) p(x, y, dy < -0.2 ? GOLD_HI : dy > 0.45 ? GOLD_DK : GOLD);
	}
	// the emblem: a red disc on top with a gold-white bolt
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const dx = x + 0.5 - 8, dy = y + 0.5 - 4.5, r = Math.sqrt(dx * dx + dy * dy);
		if (r <= 4.2) p(x, y, r > 3.3 ? GOLD_DK : RED);
	}
	for (const [x, y] of [[9, 2], [8, 3], [7, 4], [8, 4], [9, 4], [8, 5], [7, 6]]) p(x, y, WHITE);
}
const ringTex = Buffer.alloc(16 * 16 * 4);
{
	const p = (x, y, c) => { const i = (y * 16 + x) * 4; ringTex[i] = c[0]; ringTex[i + 1] = c[1]; ringTex[i + 2] = c[2]; ringTex[i + 3] = 255; };
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) p(x, y, (x + y) % 5 === 0 ? GOLD_HI : y % 4 === 3 ? GOLD_DK : GOLD);
	// box UV for the 1.6 x 1.4 x 0.8 band from (0,0): the front (north) face starts at u = 0.8, v = 0.8 -- one red texel
	// with a white spark there reads as the emblem
	p(1, 1, RED); p(2, 1, RED); p(1, 2, RED); p(2, 2, WHITE);
}

const ITEM = path.join(A, 'textures/item');
for (const piece of ['helmet', 'chestplate', 'leggings', 'boots']) {
	fs.writeFileSync(path.join(ITEM, `flash_suit_${piece}.png`), encode(16, 16, icons[piece]));
	json(path.join(A, `models/item/flash_suit_${piece}.json`),
		{ parent: 'minecraft:item/generated', textures: { layer0: `projecthero:item/flash_suit_${piece}` } });
}
fs.writeFileSync(path.join(ITEM, 'flash_ring.png'), encode(16, 16, ringIcon));
json(path.join(A, 'models/item/flash_ring.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/flash_ring' } });
fs.mkdirSync(path.join(A, 'textures/entity'), { recursive: true });
fs.writeFileSync(path.join(A, 'textures/entity/flash_ring.png'), encode(16, 16, ringTex));

// ---- recipes: red wool, gold, diamonds, sugar (speed) and a lightning rod for the emblem
const recipe = (piece, pattern, key) => json(path.join(D, `recipe/flash_suit_${piece}.json`), {
	type: 'minecraft:crafting_shaped', category: 'equipment', pattern,
	key: Object.fromEntries(Object.entries(key).map(([k, id]) => [k, { item: id }])),
	result: { id: `projecthero:flash_suit_${piece}`, count: 1 },
});
const R = 'minecraft:red_wool', G = 'minecraft:gold_ingot', Dm = 'minecraft:diamond', S = 'minecraft:sugar', L = 'minecraft:lightning_rod';
recipe('helmet', ['RDR', 'G G'], { R, D: Dm, G });
recipe('chestplate', ['R R', 'DLD', 'RSR'], { R, D: Dm, L, S });
recipe('leggings', ['RDR', 'S S', 'R R'], { R, D: Dm, S });
recipe('boots', ['D D', 'G G'], { D: Dm, G });

if (PREVIEW) {
	const sheet = Buffer.alloc(5 * 128 * 128 * 4);
	[icons.helmet, icons.chestplate, icons.leggings, icons.boots, ringIcon].forEach((img, i) => {
		const u = upscale({ w: 16, h: 16, px: img }, 8, true);
		for (let y = 0; y < 128; y++) u.px.copy(sheet, (y * 640 + i * 128) * 4, y * 128 * 4, (y + 1) * 128 * 4);
	});
	fs.writeFileSync(path.join(PREVIEW, 'flash_icons.png'), encode(640, 128, sheet));
}
console.log('flash suit: geo, texture (red ' + at(20, 24).slice(0, 3) + '), 4 suit icons + ring icon + ring texture, 5 item models, 4 recipes');
