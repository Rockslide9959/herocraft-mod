// v0.17.3 Shazam: generates the suit layer (wide + slim, champion + family), the short white cape and Wizard Shazam's skin.
// Run from the repo root: node scratchpad/shazam/gen_shazam_textures.js
// Writes into src/main/resources/assets/projecthero/textures/entity/shazam/ and a preview sheet into scratchpad/shazam/.
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const OUT = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'entity', 'shazam');
fs.mkdirSync(OUT, { recursive: true });

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
	set(x, y, c, a = 255) { if (x < 0 || y < 0 || x >= this.w || y >= this.h) return; const i = (y * this.w + x) * 4; this.d[i] = clamp(c[0]); this.d[i + 1] = clamp(c[1]); this.d[i + 2] = clamp(c[2]); this.d[i + 3] = a; }
	get(x, y) { const i = (y * this.w + x) * 4; return [this.d[i], this.d[i + 1], this.d[i + 2], this.d[i + 3]]; }
	// the suit and cape now come from the user's skin (make_shazam_suit.js): this script only writes the Wizard
	save(name) { if (/^shazam_(suit|cape)/.test(name)) return; fs.writeFileSync(path.join(OUT, name), encode(this.w, this.h, this.d)); }
}
const shade = (c, f) => [c[0] * f, c[1] * f, c[2] * f];
const mix = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];

/**
 * Paints a skin box (u, v, w, h, d) with fn(face, x, y, fw, fh) -> colour or null (transparent).
 * Faces: top, bottom, right, front, left, back. (x, y) are face-local, y = 0 at the top of the face (for top/bottom: front edge at y = d-1).
 */
function box(img, u, v, w, h, d, fn) {
	const faces = {
		top: [u + d, v, w, d], bottom: [u + d + w, v, w, d],
		right: [u, v + d, d, h], front: [u + d, v + d, w, h], left: [u + d + w, v + d, d, h], back: [u + d + w + d, v + d, w, h]
	};
	for (const [face, [fx, fy, fw, fh]] of Object.entries(faces)) {
		for (let y = 0; y < fh; y++) for (let x = 0; x < fw; x++) {
			const c = fn(face, x, y, fw, fh);
			if (c) img.set(fx + x, fy + y, c, c[3] === undefined ? 255 : c[3]);
		}
	}
}

// ---------------------------------------------------------------- the suit
const RED = [196, 24, 32];
const RED_DARK = [128, 12, 20];
const GOLD = [255, 196, 48];
const GOLD_DARK = [196, 128, 20];
const GOLD_LIGHT = [255, 236, 140];

function cloth(base, x, y, s) {
	const n = hash(x, y, s);
	return shade(base, 0.9 + n * 0.16);
}

/** The jagged lightning bolt on the 8x12 chest: true where gold. */
function boltAt(x, y) {
	// a classic Shazam bolt, upper-right to lower-left, 8 wide x 7 tall from row 1
	const rows = [
		[5, 6], // y=1
		[4, 5], // y=2
		[3, 4], // y=3
		[2, 3, 4, 5, 6], // y=4
		[4, 5], // y=5
		[3, 4], // y=6
		[2, 3], // y=7
	];
	const r = rows[y - 1];
	return r !== undefined && r.includes(x);
}

function suit(slim, family) {
	const img = new Img(64, 64);
	const aw = slim ? 3 : 4;
	const body = (face, x, y, fw, fh, seed) => {
		let c = cloth(RED, x, y, seed);
		if (face === 'top') return cloth(RED_DARK, x, y, seed);
		if (face === 'bottom') return cloth(RED_DARK, x, y, seed);
		// the gold sash / belt (rows 8-9)
		if (y === 8 || y === 9) {
			c = cloth(y === 8 ? GOLD : GOLD_DARK, x, y, seed + 1);
			if (face === 'front' && (x === 3 || x === 4)) c = GOLD_LIGHT; // the buckle
			return c;
		}
		if (face === 'front' && boltAt(x, y)) {
			const edge = !boltAt(x + 1, y) || !boltAt(x, y - 1);
			return edge ? GOLD_LIGHT : GOLD;
		}
		if (face === 'front' && y === 0 && (x === 3 || x === 4)) return RED_DARK; // collar dip
		// soft fold shading down the sides
		if (x === 0 || x === fw - 1) c = shade(c, 0.85);
		if (family && (y === 0 || ((face === 'right' || face === 'left') && x === 1))) c = GOLD; // family gold trim
		return c;
	};
	const arm = (face, x, y, fw, fh, seed) => {
		if (face === 'top') return cloth(family ? GOLD : RED_DARK, x, y, seed);
		if (face === 'bottom') return cloth(GOLD_DARK, x, y, seed);
		if (y >= 8) { // the gold cuffs
			const c = cloth(y === 8 ? GOLD_LIGHT : GOLD, x, y, seed);
			return y === 11 ? shade(c, 0.85) : c;
		}
		let c = cloth(RED, x, y, seed);
		if (x === 0 || x === fw - 1) c = shade(c, 0.86);
		if (family && y === 0) c = GOLD;
		return c;
	};
	const leg = (face, x, y, fw, fh, seed) => {
		if (face === 'top') return cloth(RED_DARK, x, y, seed);
		if (face === 'bottom') return cloth(GOLD_DARK, x, y, seed);
		if (y >= 8) { // gold boots
			let c = cloth(y === 8 ? GOLD_LIGHT : GOLD, x, y, seed);
			if (y === 11) c = GOLD_DARK;
			return c;
		}
		let c = cloth(RED, x, y, seed);
		if (x === 0 || x === fw - 1) c = shade(c, 0.86);
		if (family && (face === 'right' || face === 'left') && x === 1) c = GOLD;
		return c;
	};
	// base layer + the overlay layer (a slightly inflated copy that covers the wearer's own jacket / sleeves / trousers)
	for (const [u, v, s] of [[16, 16, 1], [16, 32, 2]]) box(img, u, v, 8, 12, 4, (f, x, y, fw, fh) => body(f, x, y, fw, fh, s));
	for (const [u, v, s] of [[40, 16, 3], [40, 32, 4], [32, 48, 5], [48, 48, 6]]) box(img, u, v, aw, 12, 4, (f, x, y, fw, fh) => arm(f, x, y, fw, fh, s));
	for (const [u, v, s] of [[0, 16, 7], [0, 32, 8], [16, 48, 9], [0, 48, 10]]) box(img, u, v, 4, 12, 4, (f, x, y, fw, fh) => leg(f, x, y, fw, fh, s));
	// the overlay copy is a touch lighter so the edges read
	return img;
}

// ---------------------------------------------------------------- the cape (64x48, superman_cape layout: left = outside, right = lining)
function cape() {
	const img = new Img(64, 48);
	const WHITE = [242, 242, 236];
	const LINING = [214, 210, 200];
	for (let y = 0; y < 48; y++) for (let x = 0; x < 64; x++) {
		const inside = x >= 32;
		const lx = inside ? x - 32 : x;
		let c = cloth(inside ? LINING : WHITE, x, y, 11);
		// vertical folds
		c = shade(c, 0.94 + 0.06 * Math.cos(lx * 0.9));
		const trim = lx <= 1 || lx >= 30 || y >= 45 || y <= 1;
		if (trim) c = cloth((y >= 46 || lx === 0 || lx === 31) ? GOLD_DARK : GOLD, x, y, 12);
		img.set(x, y, c);
	}
	return img;
}

// ---------------------------------------------------------------- Wizard Shazam (64x64 wide skin)
function wizard() {
	const img = new Img(64, 64);
	const SKIN = [214, 170, 140];
	const SKIN_DARK = [176, 130, 104];
	const HAIR = [236, 236, 232];
	const HAIR_SHADE = [196, 196, 192];
	const ROBE = [238, 234, 224];
	const ROBE_SHADE = [200, 194, 182];
	const MANTLE = [176, 24, 30];
	const MANTLE_DARK = [118, 14, 20];
	const EYE = [70, 110, 160];
	const SANDAL = [110, 70, 40];
	const hair = (x, y) => mix(HAIR, HAIR_SHADE, hash(x, y, 21) * 0.6);
	// head base
	box(img, 0, 0, 8, 8, 8, (face, x, y) => {
		if (face === 'top') return hair(x, y);
		if (face === 'bottom') return hair(x, y);
		if (face === 'front') {
			if (y <= 1) return hair(x, y); // the hairline
			if (y === 2) return (x === 1 || x === 2 || x === 5 || x === 6) ? HAIR : SKIN; // bushy brows
			if (y === 3) {
				if (x === 2 || x === 5) return EYE;
				if (x === 1 || x === 6) return [250, 250, 250];
				return SKIN;
			}
			if (y === 4) return (x === 3 || x === 4) ? SKIN_DARK : (x === 0 || x === 7 ? hair(x, y) : SKIN); // the nose
			if (y === 5) return (x >= 2 && x <= 5) ? HAIR : hair(x, y); // moustache
			return hair(x, y); // the beard
		}
		if (face === 'back') return hair(x, y);
		// the sides: hair, an ear peeking out
		if (y >= 3 && y <= 4 && x === 3) return SKIN_DARK;
		return hair(x, y);
	});
	// hat layer: a fuller beard and hair (transparent elsewhere)
	box(img, 32, 0, 8, 8, 8, (face, x, y) => {
		if (face === 'front') return y >= 6 && x >= 1 && x <= 6 ? hair(x + 3, y) : (y <= 0 ? hair(x, y) : null);
		if (face === 'top') return hair(x + 9, y);
		if (face === 'back') return hair(x + 5, y + 2);
		if (face === 'right' || face === 'left') return y >= 5 || y <= 1 ? hair(x, y + 7) : null;
		return null;
	});
	// body: the robe, the red mantle over the shoulders, the beard running down the chest, a gold bolt clasp
	const robe = (face, x, y, fw, seed) => {
		if (face === 'top' || face === 'bottom') return cloth(MANTLE, x, y, seed);
		if (y <= 2) return cloth(y === 2 ? MANTLE_DARK : MANTLE, x, y, seed); // the mantle
		let c = cloth(ROBE, x, y, seed);
		if (face === 'front') {
			if (y <= 7 && x >= 2 && x <= 5 && y < 3 + (x === 2 || x === 5 ? 3 : 5)) return hair(x, y); // the long beard
			if (y === 8) return cloth(MANTLE, x, y, seed); // the red sash
			if (y === 9 && x >= 3 && x <= 4) return GOLD;
			if (x === 3 || x === 4) c = shade(c, 0.92); // the robe's front seam
		}
		if (face === 'back' && y <= 9) return cloth(MANTLE, x, y, seed + 3); // the mantle falls down the back
		if (y === 8) return cloth(MANTLE, x, y, seed);
		if (x === 0 || x === fw - 1) c = mix(c, ROBE_SHADE, 0.5);
		return c;
	};
	box(img, 16, 16, 8, 12, 4, (f, x, y, fw) => robe(f, x, y, fw, 31));
	// arms: wide white sleeves with red cuffs, old hands
	const sleeve = (face, x, y, fw, seed) => {
		if (face === 'top') return cloth(MANTLE, x, y, seed);
		if (face === 'bottom') return SKIN;
		if (y <= 2) return cloth(MANTLE, x, y, seed);
		if (y >= 10) return y === 11 ? SKIN_DARK : SKIN;
		if (y === 9) return cloth(MANTLE_DARK, x, y, seed);
		let c = cloth(ROBE, x, y, seed);
		if (x === 0 || x === fw - 1) c = mix(c, ROBE_SHADE, 0.5);
		return c;
	};
	box(img, 40, 16, 4, 12, 4, (f, x, y, fw) => sleeve(f, x, y, fw, 41));
	box(img, 32, 48, 4, 12, 4, (f, x, y, fw) => sleeve(f, x, y, fw, 42));
	// legs: the long robe down to red hems, sandals
	const legs = (face, x, y, fw, seed) => {
		if (face === 'top') return cloth(ROBE, x, y, seed);
		if (face === 'bottom') return SANDAL;
		if (y === 11) return (x % 2 === 0) ? SANDAL : SKIN;
		if (y === 10) return cloth(MANTLE, x, y, seed);
		let c = cloth(ROBE, x, y, seed);
		if (x === 0 || x === fw - 1) c = mix(c, ROBE_SHADE, 0.5);
		return c;
	};
	box(img, 0, 16, 4, 12, 4, (f, x, y, fw) => legs(f, x, y, fw, 51));
	box(img, 16, 48, 4, 12, 4, (f, x, y, fw) => legs(f, x, y, fw, 52));
	// jacket overlay: the mantle's collar standing up a little and the beard tip
	box(img, 16, 32, 8, 12, 4, (face, x, y) => {
		if (face === 'front' && y >= 3 && y <= 6 && x >= 3 && x <= 4) return hair(x, y + 9);
		if ((face === 'front' || face === 'back' || face === 'left' || face === 'right') && y === 0) return cloth(MANTLE, x, y, 61);
		return null;
	});
	return img;
}

suit(false, false).save('shazam_suit.png');
suit(true, false).save('shazam_suit_slim.png');
suit(false, true).save('shazam_suit_family.png');
suit(true, true).save('shazam_suit_family_slim.png');
cape().save('shazam_cape.png');
// wizard_shazam.png now comes from gen_wizard_textures.js (the reworked Wizard)

// a preview sheet (8x) of the champion suit and the Wizard side by side
function preview() {
	const a = suit(false, false), b = wizard(), c = cape();
	const S = 6, W = 64 * 3 + 16, H = 64;
	const img = new Img(W * S, H * S);
	const blit = (src, ox) => { for (let y = 0; y < src.h; y++) for (let x = 0; x < src.w; x++) { const p = src.get(x, y); const col = p[3] ? p : (((x >> 2) + (y >> 2)) % 2 ? [60, 60, 60] : [90, 90, 90]); for (let dy = 0; dy < S; dy++) for (let dx = 0; dx < S; dx++) img.set((ox + x) * S + dx, y * S + dy, col); } };
	blit(a, 0); blit(b, 72); blit(c, 144);
	fs.writeFileSync(path.join(__dirname, 'preview_textures_x6.png'), encode(img.w, img.h, img.d));
}
preview();
console.log('written to', OUT);
