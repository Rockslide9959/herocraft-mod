// v0.17.5 Sentry rework: builds the Sentry Suit / Void suit textures from the two PlanetMinecraft skins the user picked, plus the capes,
// the takeover's all-black body, the suit item icons and a preview sheet.
//
//   node scratchpad/sentry/make_suit_textures.js [--placeholder]
//
// Sources (64x64 Minecraft skins, wide or slim -- detected), NEVER modified:
//   docs/reference/sentry/sentry_comics.png  https://www.planetminecraft.com/skin/the-sentry-comics/
//        (credit on the page: Gillygogs for the shading, ATROX for the original Sentry skin; the page says
//         "Ask me before using it in a heropack or addon" -- ASK before a public release)
//   docs/reference/sentry/sentry_void.png    https://www.planetminecraft.com/skin/the-sentry-merged-with-the-void/
// --placeholder (or a missing source): uses the mod's own original textures instead (textures/entity/sentry/sentry_suit.png and
//   void_form.png), so the whole pipeline runs before the real skins are dropped in. Re-run without it once they are.
//
// Writes into src/main/resources/assets/projecthero/textures/entity/sentry/:
//   sentry_comics_wide.png / _slim.png, sentry_comics_glow_wide.png / _slim.png   <- held back (.gitignore): borrowed art
//   sentry_void_wide.png / _slim.png,   sentry_void_glow_wide.png / _slim.png      <- held back (.gitignore): borrowed art
//   sentry_cape_blue.png, sentry_cape_black.png    the capes (original: the FlowingCapeLayer 64x48 layout)
//   void_body.png, void_body_glow.png              the takeover: all black, two white pixel eyes (original)
// and textures/item/sentry_suit_top.png, sentry_suit_trousers.png (original icons), and the preview sheet
// docs/screenshots/v0175/sentry2/suit_texture_preview.png (front / back over a plain test skin, so the face and hands show through).
//
// What it does to each skin:
//  1. The head goes: the whole head and hat region (rows 0-15) is cleared -- the Sentry wears no mask, so his face and hair are the
//     skin's, not the costume's. The wearer's own face and hair show instead.
//  2. Bare skin goes: any texel on the body / arms / legs (both layers) that reads as a skin tone (hue 5-35 deg, saturation 0.12-0.62,
//     value >= 0.30, and not the suit's saturated gold) is cleared, so the wearer's own neck / hands show through. Counted and printed.
//  3. Both arm widths: a wide source gets a slim copy (each 4-wide arm face drops its most-alike middle column, the Vision / Shazam
//     method); a slim source gets a wide copy (the middle column doubled).
//  4. Glow: the bright gold / white texels (the emblem, belt, trims; the Void's white stars and highlights) go into *_glow, drawn full
//     bright in game.
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('../pnglib.js');

const ROOT = path.join(__dirname, '..', '..');
const REF = path.join(ROOT, 'docs', 'reference', 'sentry');
const OUT = path.join(ROOT, 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'entity', 'sentry');
const ITEM = path.join(ROOT, 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'item');
const SHOTS = path.join(ROOT, 'docs', 'screenshots', 'v0175', 'sentry2');
const FORCE_PLACEHOLDER = process.argv.includes('--placeholder');

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
function isSlim(img) {
	// a slim skin leaves the right arm's last two columns (54-55, rows 20-31) empty
	for (let y = 20; y < 32; y++) for (const x of [54, 55]) if (img.get(x, y)[3]) return false;
	return true;
}

// ------------------------------------------------------------------ 1-2. head and bare skin out
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
	if (h < 5 || h > 35 || s < 0.12 || s > 0.62 || v < 0.30) return false;
	return !(c[0] > 200 && c[1] > 150 && s > 0.55); // not the suit's saturated gold
}
function cleanSkin(src, slim, label) {
	const img = src.copy();
	let head = 0, skin = 0;
	for (let y = 0; y < 16; y++) for (let x = 0; x < 64; x++) if (img.get(x, y)[3]) { img.clear(x, y); head++; }
	const P = parts(slim);
	for (const [name, part] of Object.entries(P)) {
		if (name === 'head' || name === 'hat') continue;
		for (const [, [u, v, w, h]] of Object.entries(part)) {
			for (let y = v; y < v + h; y++) for (let x = u; x < u + w; x++) {
				if (isSkinTone(img.get(x, y))) { img.clear(x, y); skin++; }
			}
		}
	}
	// the hands: a row of an arm's side strip that is mostly bare skin is the hand -- its last highlight texels go too
	let hand = 0;
	for (const [u, v] of ARMS) {
		for (let y = v + 4; y < v + 16; y++) {
			let had = 0, gone = 0;
			for (let x = u; x < u + 16; x++) { if (src.get(x, y)[3]) had++; if (src.get(x, y)[3] && !img.get(x, y)[3]) gone++; }
			if (had > 0 && gone * 2 >= had) for (let x = u; x < u + 16; x++) if (img.get(x, y)[3]) { img.clear(x, y); hand++; }
		}
	}
	console.log(`${label}: ${head} head/hair texels, ${skin} bare-skin texels and ${hand} hand highlights cleared (${slim ? 'slim' : 'wide'} source)`);
	return img;
}

// ------------------------------------------------------------------ 3. arm widths
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
function slimToWide(slim) {
	const wide = slim.copy();
	for (const [u, v] of ARMS) {
		const S = armFaces(u, v, 3), W = armFaces(u, v, 4);
		const cols = {};
		for (const f of Object.keys(S)) cols[f] = readCols(slim, S[f]);
		for (let y = v; y < v + 16; y++) for (let x = u; x < u + 16; x++) wide.clear(x, y);
		for (const f of Object.keys(W)) {
			const [wx, wy] = W[f];
			const out = (f === 'right' || f === 'left') ? cols[f] : [cols[f][0], cols[f][1], cols[f][1], cols[f][2]];
			out.forEach((col, x) => col.forEach((c, y) => wide.set(wx + x, wy + y, c)));
		}
	}
	return wide;
}

// ------------------------------------------------------------------ 4. glow
function glowOf(img, kind) {
	const out = new Img(64, 64);
	let n = 0;
	for (let y = 16; y < 64; y++) for (let x = 0; x < 64; x++) {
		const c = img.get(x, y);
		if (!c[3]) continue;
		const [h, s, v] = hsv(c);
		const gold = h >= 35 && h <= 60 && s < 0.45 && v > 0.96; // only the palest gold highlights (the whole suit would glow otherwise)
		const white = c[0] > 225 && c[1] > 225 && c[2] > 225;
		const voidBright = kind === 'void' && v > 0.75 && (s < 0.25 || (h >= 35 && h <= 60));
		if (gold || white || voidBright) { out.set(x, y, c); n++; }
	}
	return { img: out, n };
}

// ------------------------------------------------------------------ the sources
function source(name, fallback) {
	const real = path.join(REF, name);
	if (!FORCE_PLACEHOLDER && fs.existsSync(real)) {
		const img = load(real);
		if (img.w !== 64 || img.h !== 64) throw new Error(name + ': expected a 64x64 skin, got ' + img.w + 'x' + img.h);
		return { img, placeholder: false, file: real };
	}
	const fb = path.join(OUT, fallback);
	console.log(`!! ${name} not found in docs/reference/sentry -- PLACEHOLDER from ${fallback}. Drop the real skin in and re-run.`);
	return { img: load(fb), placeholder: true, file: fb };
}

function build(name, fallback, kind) {
	const s = source(name, fallback);
	const slimSrc = isSlim(s.img);
	const clean = cleanSkin(s.img, slimSrc, name);
	const wide = slimSrc ? slimToWide(clean) : clean;
	const slim = slimSrc ? clean : wideToSlim(clean);
	const base = kind === 'comics' ? 'sentry_comics' : 'sentry_void';
	wide.save(path.join(OUT, base + '_wide.png'));
	slim.save(path.join(OUT, base + '_slim.png'));
	const gw = glowOf(wide, kind), gs = glowOf(slim, kind);
	gw.img.save(path.join(OUT, base + '_glow_wide.png'));
	gs.img.save(path.join(OUT, base + '_glow_slim.png'));
	console.log(`${name}: ${gw.n} glowing texels`);
	return { src: s.img, wide, slim, placeholder: s.placeholder };
}

const comics = build('sentry_comics.png', 'sentry_suit.png', 'comics');
const voids = build('sentry_void.png', 'void_form.png', 'void');

// ------------------------------------------------------------------ capes (original, 64x48: outside on the left half, lining right)
function cape(ramp, edge, lining) {
	const img = new Img(64, 48);
	const hash = (x, y) => { let h = (x * 374761393 + y * 668265263) >>> 0; h = ((h ^ (h >>> 13)) * 1274126177) >>> 0; return ((h ^ (h >>> 16)) & 0xffff) / 0xffff; };
	for (let y = 0; y < 48; y++) for (let x = 0; x < 64; x++) {
		const isLining = x >= 32;
		const lx = isLining ? x - 32 : x;
		const ripple = Math.sin(lx * 1.3) * 0.5 + Math.sin(lx * 0.45 + 1.0) * 0.35;
		let lvl = 1.6 + (y / 47) * 3.0 + ripple + (hash(x, y) - 0.5) * 0.6;
		if (y <= 1) lvl -= 1.2;
		let c = ramp[Math.max(0, Math.min(ramp.length - 1, Math.round(lvl)))];
		if (lx === 0 || lx === 31 || y >= 46) c = edge;
		if (isLining) c = lining(c);
		img.set(x, y, c);
	}
	return img;
}
const BLUE_RAMP = [[0x10, 0x26, 0x7a], [0x17, 0x34, 0x9a], [0x1e, 0x42, 0xb8], [0x26, 0x52, 0xd8], [0x33, 0x63, 0xe8], [0x4f, 0x7c, 0xff]];
const BLACK_RAMP = [[0x05, 0x04, 0x08], [0x09, 0x07, 0x0e], [0x0e, 0x0a, 0x16], [0x13, 0x0e, 0x1e], [0x18, 0x12, 0x26], [0x1e, 0x17, 0x2e]];
cape(BLUE_RAMP, [0xc8, 0x9a, 0x2a], (c) => c.map((v) => Math.round(v * 0.75))).save(path.join(OUT, 'sentry_cape_blue.png'));
cape(BLACK_RAMP, [0x3a, 0x2a, 0x10], (c) => c.map((v) => Math.round(v * 0.8))).save(path.join(OUT, 'sentry_cape_black.png'));

// ------------------------------------------------------------------ the takeover body: all black, two white pixel eyes
{
	const body = new Img(64, 64), glow = new Img(64, 64);
	const P = parts(false);
	for (const name of ['head', 'body', 'rightArm', 'leftArm', 'rightLeg', 'leftLeg']) {
		for (const [, [u, v, w, h]] of Object.entries(P[name])) for (let y = v; y < v + h; y++) for (let x = u; x < u + w; x++) {
			const n = ((x * 7 + y * 13) % 5) === 0 ? 3 : 0; // a faint grain so it reads as a body, not a hole
			body.set(x, y, [4 + n, 3 + n, 6 + n, 255]);
		}
	}
	for (const [x, y] of [[10, 12], [13, 12]]) { body.set(x, y, [255, 255, 255, 255]); glow.set(x, y, [255, 255, 255, 255]); }
	body.save(path.join(OUT, 'void_body.png'));
	glow.save(path.join(OUT, 'void_body_glow.png'));
}

// ------------------------------------------------------------------ item icons (original, 16x16)
function icon(rows, pal, file) {
	const img = new Img(16, 16);
	rows.forEach((r, y) => [...r].forEach((ch, x) => { if (pal[ch]) img.set(x, y, pal[ch]); }));
	img.save(file);
}
const PAL = { k: [0x1a, 0x1a, 0x2a], g: [0xe8, 0xb5, 0x2a], G: [0xff, 0xd8, 0x60], o: [0xb0, 0x80, 0x1a], b: [0x26, 0x52, 0xd8], B: [0x4f, 0x7c, 0xff], d: [0x17, 0x34, 0x9a], w: [0xff, 0xf4, 0xc0] };
icon([
	'................',
	'..kkkk....kkkk..',
	'.kbbbbkkkkbbbbk.',
	'.kbBgggggggggBbk',
	'.kbggggwwggggbk.',
	'.kbgggwGGwgggbk.',
	'.kbggwGGGGwggbk.',
	'..kgggwGGwgggk..',
	'..kggggwwggggk..',
	'..kbgggggggggbk.',
	'..kbbgggggggbbk.',
	'..kdbbbbbbbbbdk.',
	'..kbbbbbbbbbbbk.',
	'..kdddddddddddk.',
	'...kkkkkkkkkkk..',
	'................',
], PAL, path.join(ITEM, 'sentry_suit_top.png'));
icon([
	'................',
	'...kkkkkkkkkk...',
	'...kddGGGGddk...',
	'...kggggggggk...',
	'...kgggkkgggk...',
	'...kgggkkgggk...',
	'...kgggkkgggk...',
	'...kgggkkgggk...',
	'...kgggkkgggk...',
	'...kbbbkkbbbk...',
	'...kbbbkkbbbk...',
	'...kbbbkkbbbk...',
	'..kbbBbkkbBbbk..',
	'..kddddkkddddk..',
	'..kkkkkkkkkkkk..',
	'................',
], PAL, path.join(ITEM, 'sentry_suit_trousers.png'));

// ------------------------------------------------------------------ preview sheet: front / back over a plain test skin
function testSkin() {
	// a plain original test player: brown hair, a face with eyes, cyan shirt, blue trousers, skin-tone hands (to show they show through)
	const img = new Img(64, 64);
	const P = parts(false);
	const SKIN = [0xc6, 0x8e, 0x6a], HAIR = [0x4a, 0x2e, 0x1a], SHIRT = [0x2a, 0xb0, 0xb0], PANTS = [0x35, 0x3a, 0x90], SHOE = [0x50, 0x50, 0x50];
	const fill = (part, fn) => { for (const [f, [u, v, w, h]] of Object.entries(part)) for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) img.set(u + x, v + y, fn(f, x, y, w, h)); };
	fill(P.head, (f, x, y) => (f === 'top' || y < 2 || (f === 'back') || ((f === 'right' || f === 'left') && y < 4)) ? HAIR : (f === 'front' && y === 4 && (x === 1 || x === 6)) ? [255, 255, 255] : (f === 'front' && y === 4 && (x === 2 || x === 5)) ? [0x30, 0x40, 0xa0] : (f === 'front' && y === 6 && x >= 3 && x <= 4) ? [0x8a, 0x50, 0x3a] : SKIN);
	fill(P.body, () => SHIRT);
	for (const a of [P.rightArm, P.leftArm]) fill(a, (f, x, y, w, h) => y >= h - 3 || f === 'bottom' ? SKIN : SHIRT);
	for (const l of [P.rightLeg, P.leftLeg]) fill(l, (f, x, y, w, h) => y >= h - 2 || f === 'bottom' ? SHOE : PANTS);
	return img;
}
function figure(sheet, ox, oy, sc, layers, slim, side) {
	const P = parts(slim);
	const aw = slim ? 3 : 4;
	// part -> screen origin (in skin px) and the faces; mirror for the back
	const back = side === 'back';
	const place = [
		['head', 'hat', 4, 0, 8], ['body', 'jacket', 4, 8, 8],
		[back ? 'leftArm' : 'rightArm', back ? 'leftSleeve' : 'rightSleeve', 4 - aw, 8, aw],
		[back ? 'rightArm' : 'leftArm', back ? 'rightSleeve' : 'leftSleeve', 12, 8, aw],
		[back ? 'leftLeg' : 'rightLeg', back ? 'leftPants' : 'rightPants', 4, 20, 4],
		[back ? 'rightLeg' : 'leftLeg', back ? 'rightPants' : 'leftPants', 8, 20, 4],
	];
	for (const [baseName, overName, px, py] of place) {
		for (const layer of layers) {
			for (const nm of [baseName, overName]) {
				if (layer.skip && layer.skip(nm)) continue;
				const [u, v, w, h] = P[nm][side];
				for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
					const c = layer.img.get(u + x, v + y);
					if (!c[3]) continue;
					for (let yy = 0; yy < sc; yy++) for (let xx = 0; xx < sc; xx++) sheet.set(ox + (px + x) * sc + xx, oy + (py + y) * sc + yy, [c[0], c[1], c[2], 255]);
				}
			}
		}
	}
}
function capeBehind(sheet, ox, oy, sc, capeImg) {
	// the cape seen behind the figure (back view): a 10 x 20 px strip from the cape texture
	for (let y = 0; y < 20; y++) for (let x = 0; x < 10; x++) {
		const c = capeImg.get(Math.floor(x * 3.1), Math.floor(y * 2.3));
		for (let yy = 0; yy < sc; yy++) for (let xx = 0; xx < sc; xx++) sheet.set(ox + (3 + x) * sc + xx, oy + (8 + y) * sc + yy, [c[0], c[1], c[2], 255]);
	}
}
{
	const sc = 10, cellW = 18 * sc, cellH = 34 * sc;
	const cols = 6;
	const sheet = new Img(cols * cellW, cellH + 40);
	for (let y = 0; y < sheet.h; y++) for (let x = 0; x < sheet.w; x++) sheet.set(x, y, (((x >> 4) + (y >> 4)) & 1) ? [70, 70, 78] : [58, 58, 66]);
	const skin = testSkin();
	const capeBlue = load(path.join(OUT, 'sentry_cape_blue.png')), capeBlack = load(path.join(OUT, 'sentry_cape_black.png'));
	const body = load(path.join(OUT, 'void_body.png'));
	const skinOnly = { img: skin, skip: (n) => /jacket|Sleeve|Pants/.test(n) };
	const cells = [
		['comics front', [skinOnly, { img: comics.wide }], 'front', null],
		['comics back', [skinOnly, { img: comics.wide }], 'back', capeBlue],
		['void front', [skinOnly, { img: voids.wide }], 'front', null],
		['void back', [skinOnly, { img: voids.wide }], 'back', capeBlack],
		['takeover front', [{ img: body }], 'front', null],
		['comics slim front', [skinOnly, { img: comics.slim }], 'front', null],
	];
	cells.forEach(([, layers, side, capeImg], i) => {
		const ox = i * cellW + sc, oy = 20;
		if (capeImg) capeBehind(sheet, ox, oy, sc, capeImg);
		figure(sheet, ox, oy, sc, layers, i === 5, side);
	});
	sheet.save(path.join(SHOTS, 'suit_texture_preview.png'));
	console.log('preview cells (left to right): ' + cells.map((c) => c[0]).join(' | '));
}
if (comics.placeholder || voids.placeholder) {
	console.log('\n!! PLACEHOLDER RUN: the real skins are not in docs/reference/sentry yet. Drop sentry_comics.png and sentry_void.png there and re-run.');
}
