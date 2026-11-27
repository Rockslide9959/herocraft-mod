// v0.17.7 Black Panther Ancestral Plane: hand-made textures, nothing borrowed, no real person's likeness.
//   textures/entity/ancestral/ancestor_0..4.png   five 64x64 wide player skins: robed Wakandan royals of old (purple and gold;
//                                                  the eldest in white and gold), all overlay layers used for robe folds,
//                                                  collars, sashes, headwraps and crowns
//   textures/entity/ancestral/panther_shade.png    64x32 ocelot-layout hide: translucent near-black with violet rim light
//   textures/entity/ancestral/panther_shade_eyes.png  the glowing violet eyes (eyes render layer)
//   textures/entity/ancestral/bast_spirit.png      the spirit's hide: violet-black, gold starlight, translucent
//   textures/entity/ancestral/bast_spirit_eyes.png gold eyes + glowing gold star motes
//   textures/mob_effect/bast_blessing.png          18x18 effect icon: a gold-rimmed black panther head on a violet glow
// Previews: docs/screenshots/v0177/bp_ancestral/ancestor_skins_preview.png, bast_blessing_icon_x8.png
// Run from the repo root: node scratchpad/v0177/gen_ancestor_skins.js
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const ROOT = path.join(__dirname, '..', '..');
const TEX = path.join(ROOT, 'src', 'main', 'resources', 'assets', 'projecthero', 'textures');
const OUT = path.join(TEX, 'entity', 'ancestral');
const EFFECT = path.join(TEX, 'mob_effect');
const PREVIEW = path.join(ROOT, 'docs', 'screenshots', 'v0177', 'bp_ancestral');
for (const d of [OUT, EFFECT, PREVIEW]) fs.mkdirSync(d, { recursive: true });

// ---------------------------------------------------------------- png
const CRC_TABLE = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = (buf) => { let c = 0xffffffff; for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (type, data) => { const len = Buffer.alloc(4); len.writeUInt32BE(data.length, 0); const td = Buffer.concat([Buffer.from(type, 'ascii'), data]); const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td), 0); return Buffer.concat([len, td, crc]); };
function encode(w, h, data) {
	const stride = w * 4;
	const raw = Buffer.alloc((stride + 1) * h);
	for (let y = 0; y < h; y++) { raw[y * (stride + 1)] = 0; data.copy(raw, y * (stride + 1) + 1, y * stride, y * stride + stride); }
	const ihdr = Buffer.alloc(13);
	ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
	return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr),
		chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}
function hash(x, y, s = 0) {
	let h = (x * 374761393 + y * 668265263 + s * 2147483647) >>> 0;
	h = ((h ^ (h >>> 13)) * 1274126177) >>> 0;
	return ((h ^ (h >>> 16)) & 0xffff) / 0xffff;
}
const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));
class Img {
	constructor(w, h) { this.w = w; this.h = h; this.d = Buffer.alloc(w * h * 4); }
	set(x, y, c, a) { if (x < 0 || y < 0 || x >= this.w || y >= this.h) return; const i = (y * this.w + x) * 4; this.d[i] = clamp(c[0]); this.d[i + 1] = clamp(c[1]); this.d[i + 2] = clamp(c[2]); this.d[i + 3] = a === undefined ? (c[3] === undefined ? 255 : c[3]) : a; }
	get(x, y) { const i = (y * this.w + x) * 4; return [this.d[i], this.d[i + 1], this.d[i + 2], this.d[i + 3]]; }
	save(dir, name) { fs.writeFileSync(path.join(dir, name), encode(this.w, this.h, this.d)); }
}
const shade = (c, f) => [c[0] * f, c[1] * f, c[2] * f];
const mix = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
/** Paints a box (u, v, w, h, d) with fn(face, x, y, fw, fh). Faces: top, bottom, right, front, left, back. */
function box(img, u, v, w, h, d, fn) {
	const faces = {
		top: [u + d, v, w, d], bottom: [u + d + w, v, w, d],
		right: [u, v + d, d, h], front: [u + d, v + d, w, h], left: [u + d + w, v + d, d, h], back: [u + d + w + d, v + d, w, h]
	};
	for (const [face, [fx, fy, fw, fh]] of Object.entries(faces)) {
		for (let y = 0; y < fh; y++) for (let x = 0; x < fw; x++) {
			const c = fn(face, x, y, fw, fh);
			if (c) img.set(fx + x, fy + y, c);
		}
	}
}

// ---------------------------------------------------------------- the ancestors
const GOLD = [214, 168, 72], GOLD_HI = [246, 214, 128], GOLD_LO = [150, 104, 40];
const VARIANTS = [
	// 0: the eldest -- white and gold, a tall white headwrap with a gold band, a violet sash
	{ skin: [92, 58, 40], robe: [226, 218, 204], robe2: [196, 186, 170], trim: GOLD, sash: [118, 58, 168], wrap: [236, 230, 218], wrapBand: GOLD,
		hair: null, beard: false, crown: false, wrapTall: true, eyes: [46, 30, 22], pattern: 'diamond' },
	// 1: a king -- royal purple with a broad gold collar, a gold crown band, a short grey beard
	{ skin: [78, 50, 34], robe: [88, 40, 132], robe2: [64, 26, 100], trim: GOLD, sash: [40, 18, 64], wrap: null, hair: [26, 20, 18], beard: [150, 146, 140],
		crown: true, wrapTall: false, eyes: [40, 26, 18], pattern: 'triangle' },
	// 2: a warrior queen -- violet with white geometric bands, shaved head and a gold circlet
	{ skin: [70, 44, 30], robe: [120, 56, 170], robe2: [92, 38, 136], trim: [232, 226, 240], sash: GOLD, wrap: null, hair: [20, 16, 14], beard: false,
		crown: 'circlet', wrapTall: false, eyes: [36, 24, 18], pattern: 'zigzag' },
	// 3: an elder scholar -- deep plum with a gold shoulder mantle and long braids
	{ skin: [96, 62, 44], robe: [104, 34, 72], robe2: [78, 24, 54], trim: GOLD, sash: [182, 120, 52], wrap: null, hair: [30, 22, 20], braids: true, beard: false,
		crown: false, wrapTall: false, eyes: [44, 28, 20], pattern: 'diamond' },
	// 4: a guardian -- indigo with bronze trims and a purple headwrap
	{ skin: [84, 54, 38], robe: [52, 40, 118], robe2: [36, 28, 90], trim: [190, 128, 70], sash: [150, 90, 200], wrap: [112, 52, 160], wrapBand: [190, 128, 70],
		hair: null, beard: [40, 32, 28], crown: false, wrapTall: false, eyes: [38, 26, 18], pattern: 'triangle' },
];

/** A woven trim pattern value 0..1 at (x, y) across a band of height h. */
function trimPattern(kind, x, y, h) {
	if (kind === 'diamond') { const c = Math.abs(((x % 4) + 0.5) - 2) + Math.abs((y + 0.5) - h / 2); return c < 1.6 ? 1 : 0.35; }
	if (kind === 'triangle') { return (x % 4) >= y * 4 / Math.max(1, h) ? 1 : 0.4; }
	return ((x + y) % 4 < 2) ? 1 : 0.4; // zigzag
}

function fabric(base, x, y, salt) {
	const n = hash(x, y, salt);
	const fold = Math.sin(x * 1.9 + salt) * 0.06;
	return shade(base, 0.9 + n * 0.12 + fold);
}

function ancestor(v, idx) {
	const img = new Img(64, 64);
	const S = v.skin, SD = shade(S, 0.8), SH = shade(S, 1.15);
	// ---- head
	box(img, 0, 0, 8, 8, 8, (face, x, y) => {
		let c = fabric(S, x, y, 3 + idx);
		const hairC = v.hair || [24, 18, 16];
		if (face === 'top') return v.wrap ? fabric(v.wrap, x, y, 7) : (v.crown === 'circlet' ? shade(hairC, 1.1) : fabric(hairC, x, y, 9));
		if (face === 'bottom') return SD;
		if (face === 'front') {
			if (y <= 1) c = v.wrap ? fabric(v.wrap, x, y, 7) : fabric(hairC, x, y, 9);
			if (y === 2) c = v.wrap ? (v.wrapBand || GOLD) : (v.crown ? GOLD : shade(S, 0.95));
			if (y === 3 && (x === 1 || x === 2 || x === 5 || x === 6)) c = shade(S, 0.62); // brows
			if (y === 4) {
				if (x === 1 || x === 6) c = [238, 234, 228];
				if (x === 2 || x === 5) c = v.eyes;
			}
			if (y === 5 && (x === 3 || x === 4)) c = shade(S, 0.82); // nose shadow
			if (y === 4 && (x === 3 || x === 4)) c = SH;
			if (y === 6 && x >= 2 && x <= 5) c = x === 2 || x === 5 ? shade(S, 0.8) : [86, 44, 40]; // mouth
			if (v.beard && y >= 6) c = (y === 6 && x >= 3 && x <= 4) ? [86, 44, 40] : fabric(v.beard, x, y, 11);
			if (y === 7 && !v.beard) c = shade(S, 0.9);
			return c;
		}
		// sides and back
		if (y <= 2) return v.wrap ? fabric(v.wrap, x, y, 7) : fabric(hairC, x, y, 9);
		if (face === 'back') return v.wrap ? fabric(v.wrap, x, y, 7) : fabric(hairC, x, y, 9);
		if (y === 2 && v.crown) return GOLD;
		if (v.beard && y >= 5 && (face === 'right' ? x >= 3 : x <= 4)) return fabric(v.beard, x, y, 11);
		if (y >= 3 && y <= 5 && (face === 'right' ? x <= 2 : x >= 5)) return v.wrap ? fabric(v.wrap, x, y, 7) : fabric(hairC, x, y, 9);
		return c;
	});
	// ---- hat layer: headwraps, crowns, braids
	box(img, 32, 0, 8, 8, 8, (face, x, y) => {
		if (v.wrap) {
			if (face === 'bottom') return null;
			if (face === 'top') return fabric(shade(v.wrap, 1.05), x, y, 13);
			if (y <= 1) return fabric(shade(v.wrap, 1.05), x, y, 13);
			if (y === 2) return (x + y) % 3 === 0 ? GOLD_HI : (v.wrapBand || GOLD);
			if (face === 'back' && y <= 4) return fabric(v.wrap, x, y, 13);
			return null;
		}
		if (v.crown === true) {
			if (face === 'top' || face === 'bottom') return null;
			if (y === 1) return x % 2 === 0 ? GOLD_HI : GOLD;
			if (y === 0 && x % 2 === 0) return GOLD_HI; // crown points
			if (y === 2) return face === 'front' && (x === 3 || x === 4) ? [150, 80, 210] : GOLD_LO;
			return null;
		}
		if (v.crown === 'circlet') {
			if (face === 'top' || face === 'bottom') return null;
			if (y === 2) return face === 'front' && (x === 3 || x === 4) ? [170, 100, 230] : GOLD;
			return null;
		}
		if (v.braids) {
			if (face === 'top') return (x % 2) ? fabric(v.hair, x, y, 15) : null;
			if (face === 'back' || face === 'right' || face === 'left') return (x % 2 === 0 && y <= 7) ? (y === 7 ? GOLD : fabric(v.hair, x, y, 15)) : null;
			return null;
		}
		return null;
	});
	// ---- body (robe), base
	box(img, 16, 16, 8, 12, 4, (face, x, y, fw) => {
		let c = fabric(face === 'back' ? v.robe2 : v.robe, x, y, 21 + idx);
		if (face === 'top') return fabric(v.robe, x, y, 21);
		if (face === 'bottom') return v.robe2;
		if (face === 'front') {
			if (y <= 2) c = mix(S, SD, y / 3); // neck and upper chest under the collar
			// the robe's open front edge with trim, down the middle
			if (y > 2 && (x === 3 || x === 4)) c = mix(v.trim, shade(v.trim, 0.7), trimPattern(v.pattern, x, y, 2) < 1 ? 0.5 : 0);
			// the sash, diagonal from the right shoulder to the left hip
			const sx = Math.round(y * 0.6);
			if (y >= 3 && (x === sx || x === sx + 1)) c = fabric(v.sash, x, y, 23);
			if (y === 8) c = x === 3 || x === 4 ? GOLD_HI : fabric(shade(v.sash, 0.9), x, y, 23); // belt
		}
		if (face === 'back' && y === 8) c = fabric(shade(v.sash, 0.9), x, y, 23);
		return c;
	});
	// ---- jacket overlay: the broad collar and robe folds
	box(img, 16, 32, 8, 12, 4, (face, x, y) => {
		if (face === 'top') return fabric(v.trim, x, y, 25);
		if (face === 'bottom') return null;
		if (y <= 2) {
			// the collar: rows of gold beads with a pattern
			const p = trimPattern(v.pattern, x, y, 3);
			if (face === 'front' && y === 0 && (x >= 2 && x <= 5)) return null; // the neck opening
			return mix(shade(v.trim, 0.75), v.trim === GOLD ? GOLD_HI : shade(v.trim, 1.15), p);
		}
		if (y === 3 && face !== 'back') return (x % 2 === 0) ? GOLD_HI : null; // collar beads
		if (y === 11) return shade(v.trim, 0.85); // hem trim
		if (y === 10 && (x % 3 === 0)) return v.trim;
		if (face === 'front' && (x === 0 || x === 7) && y > 3) return fabric(shade(v.robe, 0.82), x, y, 27); // robe edges
		return null;
	});
	// ---- arms: wide sleeves, gold cuffs, hands
	const arm = (u, v0, overlayU, overlayV, salt) => {
		box(img, u, v0, 4, 12, 4, (face, x, y) => {
			if (face === 'top') return fabric(v.trim, x, y, salt);
			if (face === 'bottom') return SD;
			if (y >= 10) return y === 11 ? SD : S; // hands
			if (y === 9) return GOLD; // bracelet
			if (y <= 1) return mix(shade(v.trim, 0.8), v.trim, trimPattern(v.pattern, x, y, 2));
			return fabric(face === 'back' ? v.robe2 : v.robe, x, y, salt);
		});
		box(img, overlayU, overlayV, 4, 12, 4, (face, x, y) => {
			if (face === 'top') return fabric(v.trim, x, y, salt + 1);
			if (face === 'bottom') return null;
			if (y <= 1) return (x + y) % 2 === 0 ? GOLD_HI : null; // shoulder beads of the collar
			if (y === 7 || y === 8) return mix(v.trim, shade(v.trim, 0.7), trimPattern(v.pattern, x, y - 7, 2) < 1 ? 0.6 : 0); // the sleeve's wide cuff
			if (y > 2 && y < 7 && x === 0 && face === 'front') return fabric(shade(v.robe, 0.85), x, y, salt + 2);
			return null;
		});
	};
	arm(40, 16, 40, 32, 31);
	arm(32, 48, 48, 48, 33);
	// ---- legs: the robe's skirt to the ankles, sandals
	const leg = (u, v0, overlayU, overlayV, salt, left) => {
		box(img, u, v0, 4, 12, 4, (face, x, y) => {
			if (face === 'top') return v.robe;
			if (face === 'bottom') return [78, 52, 32];
			if (y >= 11) return (x % 2 === 0) ? [120, 80, 44] : S; // sandals
			let c = fabric(face === 'back' ? v.robe2 : v.robe, x + (left ? 4 : 0), y, salt);
			if (face === 'front' && ((left && x === 0) || (!left && x === 3))) c = mix(v.trim, shade(v.trim, 0.7), trimPattern(v.pattern, x, y, 2) < 1 ? 0.5 : 0);
			if (y === 9 || y === 10) c = mix(shade(v.trim, 0.8), v.trim, trimPattern(v.pattern, x + (left ? 4 : 0), y - 9, 2));
			return c;
		});
		box(img, overlayU, overlayV, 4, 12, 4, (face, x, y) => {
			if (face === 'top' || face === 'bottom') return null;
			if (y === 10) return shade(v.trim, 0.9); // the hem flaring out
			if (y < 10 && (x + y + (left ? 1 : 0)) % 5 === 0) return fabric(shade(v.robe, 0.84), x, y, salt + 3); // folds
			return null;
		});
	};
	leg(0, 16, 0, 32, 41, false);
	leg(16, 48, 0, 48, 43, true);
	return img;
}

/** A flat front view of a skin (head, body, arms, legs with overlays), scaled. */
function frontView(skin, scale) {
	const W = 16, H = 32;
	const img = new Img(W * scale, H * scale);
	const blit = (sx, sy, w, h, dx, dy) => {
		for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
			const c = skin.get(sx + x, sy + y);
			if (c[3] < 10) continue;
			for (let yy = 0; yy < scale; yy++) for (let xx = 0; xx < scale; xx++) img.set((dx + x) * scale + xx, (dy + y) * scale + yy, c, 255);
		}
	};
	blit(8, 8, 8, 8, 4, 0); blit(40, 8, 8, 8, 4, 0);
	blit(20, 20, 8, 12, 4, 8); blit(20, 36, 8, 12, 4, 8);
	blit(44, 20, 4, 12, 0, 8); blit(44, 36, 4, 12, 0, 8);
	blit(36, 52, 4, 12, 12, 8); blit(52, 52, 4, 12, 12, 8);
	blit(4, 20, 4, 12, 4, 20); blit(4, 36, 4, 12, 4, 20);
	blit(20, 52, 4, 12, 8, 20); blit(4, 52, 4, 12, 8, 20);
	return img;
}

const skins = VARIANTS.map((v, i) => { const s = ancestor(v, i); s.save(OUT, `ancestor_${i}.png`); return s; });
{
	const scale = 6, pad = 12;
	const sheet = new Img(skins.length * (16 * scale + pad) + pad, 32 * scale + pad * 2);
	for (let y = 0; y < sheet.h; y++) for (let x = 0; x < sheet.w; x++) sheet.set(x, y, mix([242, 150, 74], [70, 30, 90], y / sheet.h));
	skins.forEach((s, i) => {
		const f = frontView(s, scale);
		for (let y = 0; y < f.h; y++) for (let x = 0; x < f.w; x++) { const c = f.get(x, y); if (c[3] > 0) sheet.set(pad + i * (16 * scale + pad) + x, pad + y, c); }
	});
	sheet.save(PREVIEW, 'ancestor_skins_preview.png');
}

// ---------------------------------------------------------------- the shades and Bast
function hide(spirit) {
	const img = new Img(64, 32);
	for (let y = 0; y < 32; y++) for (let x = 0; x < 64; x++) {
		const n = hash(x, y, spirit ? 91 : 71);
		let c = spirit ? mix([84, 40, 140], [140, 78, 200], n * 0.6) : mix([14, 8, 22], [34, 18, 52], n * 0.7);
		// rosettes, faint
		if (hash(x >> 1, y >> 1, 5) > 0.82) c = shade(c, 0.6);
		// a violet rim on the upper edges of each face row
		if (y % 8 === 0 || y % 8 === 1) c = mix(c, spirit ? [210, 150, 80] : [120, 64, 180], 0.35);
		if (spirit && n > 0.965) c = [246, 214, 128]; // starlight in the hide
		img.set(x, y, c, spirit ? 175 : 205);
	}
	return img;
}
function eyes(spirit) {
	const img = new Img(64, 32);
	const c = spirit ? [255, 214, 110] : [196, 130, 255];
	img.set(6, 6, c, 255); img.set(8, 6, c, 255);
	if (spirit) {
		for (let y = 0; y < 32; y++) for (let x = 0; x < 64; x++) {
			if (hash(x, y, 91) > 0.965) img.set(x, y, [230, 190, 110], 200);
		}
	}
	return img;
}
hide(false).save(OUT, 'panther_shade.png');
eyes(false).save(OUT, 'panther_shade_eyes.png');
hide(true).save(OUT, 'bast_spirit.png');
eyes(true).save(OUT, 'bast_spirit_eyes.png');

// ---------------------------------------------------------------- the effect icon
{
	const img = new Img(18, 18);
	const head = [
		'..................',
		'..................',
		'...##........##...',
		'..#gg#......#gg#..',
		'..#gk##....##kg#..',
		'..#kkkk####kkkk#..',
		'..#kkkkkkkkkkkk#..',
		'.#kkkkkkkkkkkkkk#.',
		'.#kkvvkkkkkkvvkk#.',
		'.#kkkvkkkkkkvkkk#.',
		'.#kkkkkkkkkkkkkk#.',
		'..#kkkkkggkkkkk#..',
		'..#kkkkkggkkkkk#..',
		'...#kkkgkkgkkk#...',
		'....#kkkkkkkk#....',
		'.....##kkkk##.....',
		'.......####.......',
		'..................',
	];
	for (let y = 0; y < 18; y++) for (let x = 0; x < 18; x++) {
		const r = Math.hypot(x - 8.5, y - 9) / 9;
		const glow = Math.max(0, 1 - r);
		let c = null, a = 0;
		if (glow > 0) { c = [150, 80, 230]; a = Math.round(glow * glow * 170); }
		const ch = head[y][x];
		if (ch === '#') { c = GOLD; a = 255; }
		else if (ch === 'g') { c = GOLD_HI; a = 255; }
		else if (ch === 'k') { c = mix([16, 10, 24], [40, 22, 60], hash(x, y, 3) * 0.6); a = 255; }
		else if (ch === 'v') { c = [200, 140, 255]; a = 255; }
		if (c) img.set(x, y, c, a);
	}
	img.save(EFFECT, 'bast_blessing.png');
	const big = new Img(18 * 8, 18 * 8);
	for (let y = 0; y < big.h; y++) for (let x = 0; x < big.w; x++) {
		const c = img.get(x >> 3, y >> 3);
		const bg = [48, 44, 56];
		const a = c[3] / 255;
		big.set(x, y, mix(bg, c, a), 255);
	}
	big.save(PREVIEW, 'bast_blessing_icon_x8.png');
}
console.log('wrote ancestors, shades, Bast, icon');
