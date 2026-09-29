// Moon Knight (Phase 2) assets from the user's Blockbench model
// (C:/Users/ethan/OneDrive/Desktop/3d minecraft models/moon knight/moonknight.bbmodel).
//
// The model is a plain 64x64 player skin on a skin-shaped rig (Head/Body/arms/legs, each a base cube plus a
// second-layer cube) -- the same layout as the Normal Symbiote Host's converted rig, so the geometry is
// symbiote_host.geo.json under a new identifier (GeckoLib armour bone names, same UVs, same z-fight inflates;
// Blockbench's baked display-pose limb rotations are dropped). The texture is the model's embedded PNG, byte for
// byte. The four item icons are cut from the skin itself. The cape texture is original art made here.
//
// Run from the repo root:  node scratchpad/gen_moonknight.js
const fs = require('fs');
const path = require('path');
const { decode, encode } = require('./pngkit');

const SRC = 'C:/Users/ethan/OneDrive/Desktop/3d minecraft models/moon knight/moonknight.bbmodel';
const RES = path.join(__dirname, '..', 'src/main/resources/assets/projecthero');
const model = JSON.parse(fs.readFileSync(SRC, 'utf8'));

// ---- geometry ----
const geo = JSON.parse(fs.readFileSync(path.join(RES, 'geo/symbiote_host.geo.json'), 'utf8'));
geo['minecraft:geometry'][0].description.identifier = 'geometry.moon_knight';
fs.writeFileSync(path.join(RES, 'geo/moon_knight.geo.json'), JSON.stringify(geo, null, '\t'));

// ---- suit texture: the embedded skin ----
const texPath = path.join(RES, 'textures/armor/moon_knight.png');
fs.writeFileSync(texPath, Buffer.from(model.textures[0].source.split(',')[1], 'base64'));
const skin = decode(texPath);

// ---- icons ----
function icon(parts) {
	const px = Buffer.alloc(16 * 16 * 4);
	for (const p of parts) {
		for (let y = 0; y < p.h * p.k; y++) {
			for (let x = 0; x < p.w * p.k; x++) {
				const sx = p.u + Math.floor(x / p.k), sy = p.v + Math.floor(y / p.k);
				const s = (sy * skin.w + sx) * 4, dx = p.x + x, dy = p.y + y;
				if (dx < 0 || dy < 0 || dx >= 16 || dy >= 16 || skin.px[s + 3] === 0) continue;
				skin.px.copy(px, (dy * 16 + dx) * 4, s, s + 4);
			}
		}
	}
	return encode(16, 16, px);
}
const ITEM = path.join(RES, 'textures/item');
fs.writeFileSync(path.join(ITEM, 'moon_knight_helmet.png'), icon([
	{ u: 8, v: 8, w: 8, h: 8, k: 2, x: 0, y: 0 }, { u: 40, v: 8, w: 8, h: 8, k: 2, x: 0, y: 0 }]));
fs.writeFileSync(path.join(ITEM, 'moon_knight_chestplate.png'), icon([
	{ u: 20, v: 20, w: 8, h: 12, k: 1, x: 4, y: 2 }, { u: 20, v: 36, w: 8, h: 12, k: 1, x: 4, y: 2 },
	{ u: 44, v: 20, w: 4, h: 12, k: 1, x: 0, y: 2 }, { u: 44, v: 36, w: 4, h: 12, k: 1, x: 0, y: 2 },
	{ u: 36, v: 52, w: 4, h: 12, k: 1, x: 12, y: 2 }, { u: 52, v: 52, w: 4, h: 12, k: 1, x: 12, y: 2 }]));
fs.writeFileSync(path.join(ITEM, 'moon_knight_leggings.png'), icon([
	{ u: 4, v: 20, w: 4, h: 12, k: 1, x: 4, y: 2 }, { u: 4, v: 36, w: 4, h: 12, k: 1, x: 4, y: 2 },
	{ u: 20, v: 52, w: 4, h: 12, k: 1, x: 8, y: 2 }, { u: 4, v: 52, w: 4, h: 12, k: 1, x: 8, y: 2 }]));
fs.writeFileSync(path.join(ITEM, 'moon_knight_boots.png'), icon([
	{ u: 4, v: 28, w: 4, h: 4, k: 2, x: 0, y: 4 }, { u: 4, v: 44, w: 4, h: 4, k: 2, x: 0, y: 4 },
	{ u: 20, v: 60, w: 4, h: 4, k: 2, x: 8, y: 4 }, { u: 4, v: 60, w: 4, h: 4, k: 2, x: 8, y: 4 }]));
for (const piece of ['helmet', 'chestplate', 'leggings', 'boots']) {
	fs.writeFileSync(path.join(RES, `models/item/moon_knight_${piece}.json`),
		JSON.stringify({ parent: 'minecraft:item/generated', textures: { layer0: `projecthero:item/moon_knight_${piece}` } }));
}

// ---- the cape (original): 64x32. Left half = the outside (off-white cloth with soft vertical folds, a faint
// repeating crescent moon pattern and a darker hem); right half = the inside lining (a shade darker).
function mix(a, b, t) { return a.map((v, i) => Math.round(v + (b[i] - v) * t)); }
const W = 64, H = 32;
const cape = Buffer.alloc(W * H * 4);
function put(x, y, c, a = 255) { const i = (y * W + x) * 4; cape[i] = c[0]; cape[i + 1] = c[1]; cape[i + 2] = c[2]; cape[i + 3] = a; }
const CLOTH = [238, 234, 222];
const SHADE = [206, 201, 188];
const CRESCENT = [176, 184, 198];
const HEM = [178, 172, 160];
// a 9x9 crescent stamp: a disc with a smaller disc bitten out of its upper right
const crescent = [];
for (let y = 0; y < 9; y++) for (let x = 0; x < 9; x++) {
	const d1 = (x - 4) ** 2 + (y - 4) ** 2, d2 = (x - 5.9) ** 2 + (y - 2.9) ** 2;
	if (d1 <= 15.5 && d2 > 11.0) crescent.push([x, y]);
}
for (let half = 0; half < 2; half++) {
	for (let y = 0; y < H; y++) {
		for (let x = 0; x < 32; x++) {
			const fold = 0.5 + 0.5 * Math.sin(x * 0.9 + (y * 0.08));
			let c = mix(CLOTH, SHADE, 0.55 * (1 - fold) + (y < 3 ? 0.25 : 0));
			if (y >= H - 2 || x === 0 || x === 31) c = mix(c, HEM, 0.7);
			if (half === 1) c = mix(c, [150, 146, 136], 0.35);
			put(half * 32 + x, y, c);
		}
	}
}
// crescents only on the outside, in a staggered grid below the hood line
for (let gy = 5; gy < H - 8; gy += 11) {
	for (let gx = 3 + (Math.floor(gy / 9) % 2) * 7; gx < 26; gx += 13) {
		for (const [cx, cy] of crescent) put(gx + cx, gy + cy, CRESCENT);
	}
}
fs.writeFileSync(path.join(RES, 'textures/entity/moon_knight_cape.png'), encode(W, H, cape));
console.log('wrote moon_knight geo, suit texture, 4 icons, 4 item models, cape texture');
