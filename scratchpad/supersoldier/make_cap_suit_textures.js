// v0.17.5 Captain America Battle Suit: builds the suit textures from the user's NameMC skin, an ORIGINAL fallback suit, the four
// item icons and a preview sheet.
//
//   node scratchpad/supersoldier/make_cap_suit_textures.js
//
// Source (64x64 wide Minecraft skin), NEVER modified:
//   docs/reference/supersoldier/cap_suit.png   https://namemc.com/skin/63b230543038f21b  (author unknown, no licence stated --
//   CHECK BEFORE ANY PUBLIC RELEASE; see docs/reference/supersoldier/SOURCE.txt)
//
// Writes into src/main/resources/assets/projecthero/textures/entity/supersoldier/:
//   cap_battle_suit_wide.png / _slim.png                    <- held back (.gitignore): made from the borrowed skin
//   cap_battle_suit_original_wide.png / _slim.png           <- original art, committed: what a public build draws
// textures/item/cap_battle_cowl.png, cap_battle_suit_top.png, cap_battle_suit_trousers.png, cap_battle_suit_boots.png (original icons)
// and docs/screenshots/v0175/cap_suit/texture_preview.png (front / back over a plain test skin, so the face shows through the cowl).
//
// What it does to the borrowed skin:
//  1. The cowl stays (navy, white "A", wings). Inside the face opening the skin's own face goes: every skin-tone texel of the head
//     and hat, plus the eye whites / irises on the front of the head -- the wearer's own eyes, nose and mouth show through.
//  2. Bare skin on the body and legs goes (none expected: the suit covers them).
//  3. Skin-tone texels on the arms (a wrist band and the finger tips) are repainted in the gloves' browns instead of cleared, so the
//     gauntlet reads as one piece and no stripe of the wearer's own sleeve shows through it.
//  4. Both arm widths: the wide source gets a slim copy (each 4-wide arm face drops its most-alike middle column).
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('../pnglib.js');

const ROOT = path.join(__dirname, '..', '..');
const SRC = path.join(ROOT, 'docs', 'reference', 'supersoldier', 'cap_suit.png');
const OUT = path.join(ROOT, 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'entity', 'supersoldier');
const ITEM = path.join(ROOT, 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'item');
const SHOTS = path.join(ROOT, 'docs', 'screenshots', 'v0175', 'cap_suit');

class Img {
	constructor(w, h, px) { this.w = w; this.h = h; this.d = px ? Buffer.from(px) : Buffer.alloc(w * h * 4); }
	get(x, y) { if (x < 0 || y < 0 || x >= this.w || y >= this.h) return [0, 0, 0, 0]; const i = (y * this.w + x) * 4; return [this.d[i], this.d[i + 1], this.d[i + 2], this.d[i + 3]]; }
	set(x, y, c) { if (x < 0 || y < 0 || x >= this.w || y >= this.h) return; const i = (y * this.w + x) * 4; this.d[i] = c[0]; this.d[i + 1] = c[1]; this.d[i + 2] = c[2]; this.d[i + 3] = c[3] === undefined ? 255 : c[3]; }
	clear(x, y) { this.set(x, y, [0, 0, 0, 0]); }
	copy() { return new Img(this.w, this.h, this.d); }
	save(file) { fs.mkdirSync(path.dirname(file), { recursive: true }); PNG.write(file, this.w, this.h, this.d); console.log('wrote', path.relative(ROOT, file)); }
}
const load = (f) => { const s = PNG.read(f); return new Img(s.W, s.H, s.px); };

// ------------------------------------------------------------------ skin layout
function faces(u, v, w, h, d) {
	return { top: [u + d, v, w, d], bottom: [u + d + w, v, w, d], right: [u, v + d, d, h], front: [u + d, v + d, w, h], left: [u + d + w, v + d, d, h], back: [u + d + w + d, v + d, w, h] };
}
function parts(slim) {
	const aw = slim ? 3 : 4;
	return {
		head: faces(0, 0, 8, 8, 8), hat: faces(32, 0, 8, 8, 8),
		body: faces(16, 16, 8, 12, 4), jacket: faces(16, 32, 8, 12, 4),
		rightArm: faces(40, 16, aw, 12, 4), rightSleeve: faces(40, 32, aw, 12, 4),
		leftArm: faces(32, 48, aw, 12, 4), leftSleeve: faces(48, 48, aw, 12, 4),
		rightLeg: faces(0, 16, 4, 12, 4), rightPants: faces(0, 32, 4, 12, 4),
		leftLeg: faces(16, 48, 4, 12, 4), leftPants: faces(0, 48, 4, 12, 4),
	};
}
const ARM_PARTS = ['rightArm', 'rightSleeve', 'leftArm', 'leftSleeve'];

function hsv([r, g, b]) {
	const mx = Math.max(r, g, b) / 255, mn = Math.min(r, g, b) / 255, d = mx - mn;
	let h = 0;
	if (d > 0) {
		if (mx === r / 255) h = 60 * (((g - b) / 255 / d) % 6);
		else if (mx === g / 255) h = 60 * ((b - r) / 255 / d + 2);
		else h = 60 * ((r - g) / 255 / d + 4);
	}
	if (h < 0) h += 360;
	return [h, mx === 0 ? 0 : d / mx, mx];
}
function isSkinTone(c) {
	if (!c[3]) return false;
	const [h, s, v] = hsv(c);
	return h >= 5 && h <= 35 && s >= 0.12 && s <= 0.62 && v >= 0.45;
}

// ------------------------------------------------------------------ 1-3. clean the borrowed skin
function cleanSkin(src) {
	const img = src.copy();
	const P = parts(false);
	let face = 0, eyes = 0, body = 0, glove = 0;
	// 1. head + hat: skin tones out; on the head front's lower half (the face opening) the eyes too
	for (const name of ['head', 'hat']) {
		for (const [fname, [u, v, w, h]] of Object.entries(P[name])) {
			for (let y = v; y < v + h; y++) for (let x = u; x < u + w; x++) {
				const c = img.get(x, y);
				if (isSkinTone(c)) { img.clear(x, y); face++; continue; }
				if (name === 'head' && fname === 'front' && y >= v + 4 && c[3]) {
					const [, s, val] = hsv(c);
					const white = c[0] > 225 && c[1] > 225 && c[2] > 225;
					const iris = c[2] > c[0] + 40 && val > 0.55 && s > 0.3; // the light blue irises (the cowl is a dark navy)
					if (white || iris) { img.clear(x, y); eyes++; }
				}
			}
		}
	}
	// 2-3. body / legs: skin out; arms: skin repainted as glove leather
	const leather = [[0x5c, 0x47, 0x3d], [0x41, 0x32, 0x2b], [0x37, 0x2b, 0x25]];
	for (const [name, part] of Object.entries(P)) {
		if (name === 'head' || name === 'hat') continue;
		const arm = ARM_PARTS.includes(name);
		for (const [, [u, v, w, h]] of Object.entries(part)) {
			for (let y = v; y < v + h; y++) for (let x = u; x < u + w; x++) {
				const c = img.get(x, y);
				if (!isSkinTone(c)) continue;
				if (arm) {
					const v3 = hsv(c)[2];
					img.set(x, y, [...leather[v3 > 0.95 ? 0 : v3 > 0.9 ? 1 : 2], 255]);
					glove++;
				} else { img.clear(x, y); body++; }
			}
		}
	}
	console.log(`borrowed skin: ${face} face/skin texels and ${eyes} eye texels cleared from the cowl, ${body} bare-skin texels cleared from the body/legs, ${glove} wrist/finger texels repainted as glove leather`);
	return img;
}

// ------------------------------------------------------------------ 4. wide -> slim
const rgbDiff = (a, b) => { if (!a[3] && !b[3]) return 0; if (!a[3] || !b[3]) return 400; return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]); };
const ARMS = [[40, 16], [40, 32], [32, 48], [48, 48]];
function armFaces(u, v, w) { const d = 4, h = 12; return { top: [u + d, v, w, d], bottom: [u + d + w, v, w, d], right: [u, v + d, d, h], front: [u + d, v + d, w, h], left: [u + d + w, v + d, d, h], back: [u + d + w + d, v + d, w, h] }; }
function readCols(img, [fx, fy, fw, fh]) { const cols = []; for (let x = 0; x < fw; x++) { const col = []; for (let y = 0; y < fh; y++) col.push(img.get(fx + x, fy + y)); cols.push(col); } return cols; }
function wideToSlim(wide) {
	const slim = wide.copy();
	for (const [u, v] of ARMS) {
		const W = armFaces(u, v, 4), S = armFaces(u, v, 3);
		const cols = {};
		for (const f of Object.keys(W)) cols[f] = readCols(wide, W[f]);
		let drop = 1, best = Infinity;
		for (const c of [1, 2]) {
			let cost = 0;
			for (let y = 0; y < 12; y++) cost += Math.min(rgbDiff(cols.front[c][y], cols.front[c - 1][y]), rgbDiff(cols.front[c][y], cols.front[c + 1][y]));
			if (cost < best) { best = cost; drop = c; }
		}
		for (let y = v; y < v + 16; y++) for (let x = u; x < u + 16; x++) slim.clear(x, y);
		for (const f of Object.keys(S)) {
			const [sx, sy] = S[f];
			const keep = (f === 'right' || f === 'left') ? cols[f] : cols[f].filter((_, i) => i !== drop);
			keep.forEach((col, x) => col.forEach((c, y) => slim.set(sx + x, sy + y, c)));
		}
	}
	return slim;
}

// ------------------------------------------------------------------ the original fallback suit (no borrowed pixels)
let seed = 1759;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const jit = (c, a = 7) => { const k = Math.round((rnd() - 0.5) * 2 * a); return [clamp(c[0] + k), clamp(c[1] + k), clamp(c[2] + k), 255]; };
const clamp = (v) => Math.max(0, Math.min(255, v));
const NAVY = [0x26, 0x33, 0x52], NAVY_D = [0x1b, 0x24, 0x3b], NAVY_L = [0x34, 0x45, 0x6c];
const RED = [0x9c, 0x22, 0x26], WHITE = [0xd8, 0xd8, 0xdc], SILVER = [0xa8, 0xae, 0xb8];
const LEATHER = [0x4e, 0x38, 0x2b], LEATHER_D = [0x35, 0x26, 0x1e], LEATHER_L = [0x6a, 0x4d, 0x3b];

function fillFace(img, [u, v, w, h], fn) { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const c = fn(x, y, w, h); if (c) img.set(u + x, v + y, c); } }

function originalWide() {
	const img = new Img(64, 64);
	const P = parts(false);
	// cowl: navy all round, a white "A" on the forehead, white wing marks on the sides, the face open below the brow
	for (const [f, r] of Object.entries(P.head)) {
		fillFace(img, r, (x, y, w, h) => {
			if (f === 'front') {
				if (y >= 4 && x >= 1 && x <= 6) return null; // the face opening
				if (y === 7 && (x === 0 || x === 7)) return jit(LEATHER_D, 3); // chin strap ends
				const A = [[3, 1], [4, 1], [2, 2], [5, 2], [2, 3], [3, 3], [4, 3], [5, 3]];
				if (A.some(([ax, ay]) => ax === x && ay === y)) return jit(WHITE, 4);
				if (y === 3 && (x === 1 || x === 6)) return jit(NAVY_D, 3); // the brow
				return jit(NAVY);
			}
			if (f === 'right' || f === 'left') {
				if ((y === 3 && x >= 2 && x <= 5) || (y === 2 && x >= 3 && x <= 4)) return jit(WHITE, 4); // the wings
				if (y === 7) return jit(LEATHER_D, 3);
				return jit(NAVY);
			}
			if (f === 'bottom') return null; // open under the chin
			return jit(y % 4 === 0 && f === 'back' ? NAVY_D : NAVY);
		});
	}
	// body: navy, a white star on the chest, red/white stripes on the stomach, a brown belt; a leather harness on the back
	for (const [f, r] of Object.entries(P.body)) {
		fillFace(img, r, (x, y, w, h) => {
			if (f === 'front') {
				const star = [[3, 1], [4, 1], [1, 2], [2, 2], [3, 2], [4, 2], [5, 2], [6, 2], [2, 3], [3, 3], [4, 3], [5, 3], [2, 4], [5, 4]];
				if (star.some(([sx, sy]) => sx === x && sy === y)) return jit(WHITE, 4);
				if (y >= 6 && y <= 8 && x >= 1 && x <= 6) return jit(x % 2 ? RED : WHITE, 4);
				if (y === 9) return x === 3 || x === 4 ? jit(SILVER, 3) : jit(LEATHER);
				if (y === 0 && (x === 0 || x === 7)) return jit(NAVY_L);
				return jit(NAVY);
			}
			if (f === 'back') {
				if ((x === 1 || x === 6) && y <= 9) return jit(LEATHER_D, 4); // harness straps
				if (y === 4 && x >= 1 && x <= 6) return jit(LEATHER_D, 4); // the shield clamp bar
				if (y === 9) return jit(LEATHER);
				return jit(NAVY);
			}
			if (f === 'right' || f === 'left') return y === 9 ? jit(LEATHER) : jit(y < 6 && x === 1 ? NAVY_L : NAVY);
			return jit(NAVY);
		});
	}
	// arms: navy sleeves with a red stripe down the outside, leather gauntlets from the forearm down
	for (const name of ['rightArm', 'leftArm']) {
		const outer = name === 'rightArm' ? 'right' : 'left';
		for (const [f, r] of Object.entries(P[name])) {
			fillFace(img, r, (x, y, w, h) => {
				if (f === 'top') return jit(NAVY_L);
				if (f === 'bottom') return jit(LEATHER_D, 4);
				if (y === 7) return jit(LEATHER_L, 4);
				if (y >= 8) return jit(y === 11 ? LEATHER_D : LEATHER, 5);
				if (f === outer && (x === 1 || x === 2)) return jit(RED, 5);
				return jit(NAVY);
			});
		}
	}
	// legs: navy with darker knee pads, leather boots from BOOT_ROW (7) down
	for (const name of ['rightLeg', 'leftLeg']) {
		for (const [f, r] of Object.entries(P[name])) {
			fillFace(img, r, (x, y, w, h) => {
				if (f === 'top') return jit(NAVY);
				if (f === 'bottom') return jit(LEATHER_D, 3);
				if (y === 7) return jit(LEATHER_L, 4);
				if (y >= 8) return jit(y === 11 ? LEATHER_D : LEATHER, 5);
				if (f === 'front' && (y === 4 || y === 5)) return jit(NAVY_D, 4);
				if (f === 'front' && y === 0) return jit(LEATHER); // belt line
				return jit(NAVY);
			});
		}
	}
	// outer layer: small raised belt pouches on the jacket front, nothing else
	fillFace(img, P.jacket.front, (x, y) => (y === 9 && (x === 0 || x === 1 || x === 6 || x === 7) ? jit(LEATHER_L, 4) : null));
	return img;
}

// ------------------------------------------------------------------ item icons (original, 16x16)
function icon(rows, pal) {
	const img = new Img(16, 16);
	rows.forEach((row, y) => [...row].forEach((ch, x) => { if (pal[ch]) img.set(x, y, pal[ch]); }));
	return img;
}
const PAL = { n: NAVY, d: NAVY_D, l: NAVY_L, w: WHITE, r: RED, b: LEATHER, k: LEATHER_D, h: LEATHER_L, s: SILVER, o: [0x10, 0x14, 0x22] };
const ICONS = {
	cap_battle_cowl: [
		'................', '................', '.....oooooo.....', '....onnnnnno....', '...onnnwwnnno...', '...onnwnnwnno...',
		'..owwnwwwwnwwo..', '..onnnwnnwnnno..', '..onnndddddnno..', '..onno....onno..', '..onno....onno..', '..onno....onno..',
		'...okko..okko...', '....oo....oo....', '................', '................'],
	cap_battle_suit_top: [
		'................', '..ooooo..ooooo..', '.olnnnnooonnnlo.', '.onnnnnwwnnnnno.', '.onnnwwwwwwnnno.', '.onnnnwwwwnnnno.',
		'.onno.wnnw.onno.', '.onno.nnnn.onno.', '.okko.rwrw.okko.', '.okko.rwrw.okko.', '.ohho.rwrw.ohho.', '.oooo.bsbb.oooo.',
		'......nnnn......', '......oooo......', '................', '................'],
	cap_battle_suit_trousers: [
		'................', '...bbbbsbbbbb...', '...onnnnnnnnno...', '...onnnnnnnnno..', '...onnnnonnnno..', '...onnnoonnnno..',
		'...onnno.onnno..', '...oddno.onddo..', '...oddno.onddo..', '...onnno.onnno..', '...onnno.onnno..', '...onnno.onnno..',
		'...onnno.onnno..', '...ooooo.ooooo..', '................', '................'].map((r) => r.slice(0, 16)),
	cap_battle_suit_boots: [
		'................', '................', '................', '................', '..ohhho..ohhho..', '..obbbo..obbbo..',
		'..obbbo..obbbo..', '..obbbo..obbbo..', '..obbbo..obbbo..', '..obbbo..obbbo..', '.obbbbo..obbbbo.', 'obbbbbo..obbbbbo',
		'okkkkko..okkkkko', 'ooooooo..ooooooo', '................', '................'],
};

// ------------------------------------------------------------------ preview sheet (flat front / back over a plain test skin)
function testSkin() {
	const img = new Img(64, 64);
	const P = parts(false);
	const SKIN = [0xd6, 0xa6, 0x82], SHIRT = [0x7a, 0x7a, 0x7a], HAIR = [0x5a, 0x3c, 0x22];
	for (const [name, part] of Object.entries(P)) {
		if (['hat', 'jacket', 'rightSleeve', 'leftSleeve', 'rightPants', 'leftPants'].includes(name)) continue;
		for (const [f, r] of Object.entries(part)) fillFace(img, r, (x, y) => (name === 'head' ? (y < 2 || f === 'back' || f === 'top' ? HAIR : SKIN) : name.includes('Leg') ? [0x3a, 0x4a, 0x6a] : name.includes('Arm') && y >= 8 ? SKIN : SHIRT).concat(255));
	}
	const [fu, fv] = P.head.front;
	img.set(fu + 2, fv + 4, [255, 255, 255]); img.set(fu + 3, fv + 4, [0x30, 0x60, 0xc0]); img.set(fu + 4, fv + 4, [0x30, 0x60, 0xc0]); img.set(fu + 5, fv + 4, [255, 255, 255]);
	img.set(fu + 3, fv + 6, [0x90, 0x50, 0x40]); img.set(fu + 4, fv + 6, [0x90, 0x50, 0x40]);
	return img;
}
// doll layout (16 x 32): [part, face, x, y]
const FRONT = [['head', 'front', 4, 0], ['body', 'front', 4, 8], ['rightArm', 'front', 0, 8], ['leftArm', 'front', 12, 8], ['rightLeg', 'front', 4, 20], ['leftLeg', 'front', 8, 20]];
const BACK = [['head', 'back', 4, 0], ['body', 'back', 4, 8], ['leftArm', 'back', 0, 8], ['rightArm', 'back', 12, 8], ['leftLeg', 'back', 4, 20], ['rightLeg', 'back', 8, 20]];
const OVER = { head: 'hat', body: 'jacket', rightArm: 'rightSleeve', leftArm: 'leftSleeve', rightLeg: 'rightPants', leftLeg: 'leftPants' };
function doll(skin, suit, layout) {
	const out = new Img(16, 32);
	const P = parts(false);
	const blit = (src, part, face, dx, dy) => { const [u, v, w, h] = P[part][face]; for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const c = src.get(u + x, v + y); if (c[3]) out.set(dx + x, dy + y, c); } };
	for (const [part, face, dx, dy] of layout) { blit(skin, part, face, dx, dy); blit(suit, part, face, dx, dy); blit(suit, OVER[part], face, dx, dy); }
	return out;
}
function sheet(panels, scale) {
	const W = panels.length * (16 + 4) + 4, H = 32 + 8;
	const out = new Img(W * scale, H * scale);
	for (let y = 0; y < out.h; y++) for (let x = 0; x < out.w; x++) out.set(x, y, ((x >> 4) + (y >> 4)) & 1 ? [0x3c, 0x3c, 0x44] : [0x34, 0x34, 0x3c]);
	panels.forEach((p, i) => { const ox = (4 + i * 20) * scale, oy = 4 * scale; for (let y = 0; y < 32 * scale; y++) for (let x = 0; x < 16 * scale; x++) { const c = p.get(Math.floor(x / scale), Math.floor(y / scale)); if (c[3]) out.set(ox + x, oy + y, c); } });
	return out;
}

// ------------------------------------------------------------------ run
const wideOrig = originalWide();
wideOrig.save(path.join(OUT, 'cap_battle_suit_original_wide.png'));
const slimOrig = wideToSlim(wideOrig);
slimOrig.save(path.join(OUT, 'cap_battle_suit_original_slim.png'));
const panels = [];
const skin = testSkin();
if (fs.existsSync(SRC)) {
	const src = load(SRC);
	if (src.w !== 64 || src.h !== 64) throw new Error('expected a 64x64 skin');
	const wide = cleanSkin(src);
	wide.save(path.join(OUT, 'cap_battle_suit_wide.png'));
	wideToSlim(wide).save(path.join(OUT, 'cap_battle_suit_slim.png'));
	panels.push(doll(new Img(64, 64), src, FRONT), doll(skin, wide, FRONT), doll(skin, wide, BACK));
} else {
	console.log('!! docs/reference/supersoldier/cap_suit.png missing: only the original suit is made (the game falls back to it)');
}
panels.push(doll(skin, wideOrig, FRONT), doll(skin, wideOrig, BACK));
sheet(panels, 10).save(path.join(SHOTS, 'texture_preview.png'));
for (const [name, rows] of Object.entries(ICONS)) icon(rows, PAL).save(path.join(ITEM, name + '.png'));
