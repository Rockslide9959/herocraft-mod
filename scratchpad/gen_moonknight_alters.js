// Moon Knight v0.13.21: one suit per alter, from the user's three Blockbench models
// (C:/Users/ethan/OneDrive/Desktop/3d minecraft models/moon knight/moonknightmarc.bbmodel, moonknightsteven.bbmodel,
// moonknightJake.bbmodel).
//
// All three are the same rig as the original moonknight.bbmodel that gen_moonknight.js converted: a plain 64x64 player
// skin on a skin-shaped GeckoLib rig (Head/Body/arms/legs, each a base cube plus a second-layer cube, identical
// from / to / inflate / uv_offset in every file -- checked below). So they all keep sharing geo/moon_knight.geo.json
// (the Symbiote host's rig renamed) and differ only in texture: each alter's texture is its model's embedded PNG, byte
// for byte (Jake's is the very skin the v0.13.20 suit used). The four item icons are re-cut from Marc's skin (the
// default alter). The capes: Marc keeps the original off-white one; Steven gets a crisp white one and Jake a charcoal
// one, made here with the same folds / crescent pattern as gen_moonknight.js.
//
// Run from the repo root:  node scratchpad/gen_moonknight_alters.js
const fs = require('fs');
const path = require('path');
const { decode, encode } = require('./pngkit');

const DIR = 'C:/Users/ethan/OneDrive/Desktop/3d minecraft models/moon knight';
const RES = path.join(__dirname, '..', 'src/main/resources/assets/projecthero');
const ALTERS = { marc: 'moonknightmarc.bbmodel', steven: 'moonknightsteven.bbmodel', jake: 'moonknightJake.bbmodel' };

// ---- the rigs must match (one shared geometry)
const rig = m => JSON.stringify(m.elements.map(e => [e.name, e.from, e.to, e.inflate || 0, e.uv_offset || [0, 0]])
	.sort((a, b) => a[0].localeCompare(b[0])));
let reference = null;
for (const [alter, file] of Object.entries(ALTERS)) {
	const model = JSON.parse(fs.readFileSync(path.join(DIR, file), 'utf8'));
	if (model.resolution.width !== 64 || model.resolution.height !== 64 || model.textures.length !== 1) {
		throw new Error(file + ': expected one 64x64 texture');
	}
	const r = rig(model);
	if (reference && r !== reference) throw new Error(file + ': rig differs from the others -- needs its own geo');
	reference = r;
	// ---- suit texture: the embedded skin, byte for byte
	fs.writeFileSync(path.join(RES, `textures/armor/moon_knight_${alter}.png`),
		Buffer.from(model.textures[0].source.split(',')[1], 'base64'));
}
// the single v0.13.20 texture is replaced by the three above (it was Jake's skin)
const old = path.join(RES, 'textures/armor/moon_knight.png');
if (fs.existsSync(old)) fs.unlinkSync(old);

// ---- icons, cut from Marc's skin (same layout as gen_moonknight.js)
const skin = decode(path.join(RES, 'textures/armor/moon_knight_marc.png'));
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

// ---- per-alter capes: 64x32, left half the outside, right half the lining (gen_moonknight.js's layout)
function mix(a, b, t) { return a.map((v, i) => Math.round(v + (b[i] - v) * t)); }
function cape(file, pal) {
	const W = 64, H = 32;
	const px = Buffer.alloc(W * H * 4);
	const put = (x, y, c) => { const i = (y * W + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255; };
	const crescent = [];
	for (let y = 0; y < 9; y++) for (let x = 0; x < 9; x++) {
		const d1 = (x - 4) ** 2 + (y - 4) ** 2, d2 = (x - 5.9) ** 2 + (y - 2.9) ** 2;
		if (d1 <= 15.5 && d2 > 11.0) crescent.push([x, y]);
	}
	for (let half = 0; half < 2; half++) {
		for (let y = 0; y < H; y++) {
			for (let x = 0; x < 32; x++) {
				const fold = 0.5 + 0.5 * Math.sin(x * 0.9 + (y * 0.08));
				let c = mix(pal.cloth, pal.shade, 0.55 * (1 - fold) + (y < 3 ? 0.25 : 0));
				if (y >= H - 2 || x === 0 || x === 31) c = mix(c, pal.hem, 0.7);
				if (half === 1) c = mix(c, pal.lining, 0.35);
				put(half * 32 + x, y, c);
			}
		}
	}
	if (pal.crescent) {
		for (let gy = 5; gy < H - 8; gy += 11) {
			for (let gx = 3 + (Math.floor(gy / 9) % 2) * 7; gx < 26; gx += 13) {
				for (const [cx, cy] of crescent) put(gx + cx, gy + cy, pal.crescent);
			}
		}
	}
	fs.writeFileSync(path.join(RES, 'textures/entity', file), encode(W, H, px));
}
// Steven (Mr. Knight): crisp white, a pale grey hem, no crescents -- as clean as the suit
cape('moon_knight_cape_steven.png', { cloth: [248, 248, 246], shade: [222, 222, 224], hem: [196, 198, 204],
	lining: [170, 172, 180], crescent: null });
// Jake: charcoal, like his dark suit, with pale crescents
cape('moon_knight_cape_jake.png', { cloth: [58, 57, 60], shade: [34, 33, 37], hem: [24, 24, 27],
	lining: [12, 12, 14], crescent: [150, 156, 168] });
console.log('wrote 3 alter suit textures, 4 icons (Marc), Steven + Jake capes; removed moon_knight.png');
