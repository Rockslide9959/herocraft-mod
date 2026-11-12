// v0.17.3 Shazam: Wizard Shazam's look, after the user's two references (comic: old, dark-skinned, wild grey-black hair, red
// headband, maroon robe over one shoulder, bare arms, gold cuffs, bare feet; film: long grey hair and beard, layered rust robes over
// a deep red inner robe, a glowing gold bolt on the chest, a dark carved staff topped with a white-blue crystal).
// Hand-made, nothing borrowed. Writes three textures into src/main/resources/assets/projecthero/textures/entity/shazam/:
//   wizard_shazam.png        the 64x64 wide player skin (all overlay layers used)
//   wizard_shazam_glow.png   the skin's emissive mask (the chest bolt)
//   wizard_shazam_parts.png  128x128 atlas for WizardShazamParts (hair, beard, headband, mantle, drape, skirt, toes, staff)
// and a preview into scratchpad/shazam/wizard_preview_x6.png. The UVs below must match WizardShazamParts.java.
// Run from the repo root: node scratchpad/shazam/gen_wizard_textures.js
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
	save(name) { fs.writeFileSync(path.join(OUT, name), encode(this.w, this.h, this.d)); }
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
			if (c) img.set(fx + x, fy + y, c, c[3] === undefined ? 255 : c[3]);
		}
	}
}

// ---------------------------------------------------------------- palette
const SKIN = [98, 64, 46];
const SKIN_DARK = [70, 45, 33];
const SKIN_DEEP = [46, 29, 22];
const SKIN_LIGHT = [128, 88, 63];
const SKIN_HI = [150, 106, 76];
const HAIR_LIGHT = [196, 194, 188];
const HAIR = [150, 148, 144];
const HAIR_MID = [112, 110, 108];
const HAIR_DARK = [86, 84, 84];
const BAND = [178, 32, 30];
const BAND_DARK = [112, 18, 18];
const BAND_LIGHT = [214, 66, 52];
const INNER = [122, 18, 28]; // the deep red inner robe
const INNER_DARK = [76, 10, 18];
const RUST = [138, 54, 40]; // the rust/maroon outer robe
const RUST_DARK = [84, 28, 24];
const RUST_LIGHT = [172, 80, 58];
const GOLD = [226, 172, 56];
const GOLD_DARK = [150, 102, 28];
const GOLD_LIGHT = [255, 226, 128];
const WOOD = [62, 42, 30];
const WOOD_DARK = [36, 24, 18];
const WOOD_LIGHT = [96, 68, 48];
const LEATHER = [92, 60, 34];
const CRYSTAL = [214, 240, 255];
const CRYSTAL_MID = [140, 200, 255];
const CRYSTAL_DEEP = [70, 130, 230];

/** Grey hair in strands: a tone per strand (column), curls (darker pixels), a few light glints. */
function hair(x, y, s, dark = 0) {
	const strand = hash(x, Math.floor((y + hash(x, 0, s) * 3) / 3), s);
	let c = strand < 0.22 ? HAIR_DARK : strand < 0.5 ? HAIR_MID : strand < 0.85 ? HAIR : HAIR_LIGHT;
	const n = hash(x, y, s + 7);
	if (n < 0.08) c = mix(c, HAIR_DARK, 0.5); // a curl's shadow
	else if (n > 0.93) c = mix(c, HAIR_LIGHT, 0.7);
	return shade(c, 1 - dark);
}
/** Robe cloth: vertical folds (dark troughs, lit ridges) plus a velvety speckle. */
function cloth(base, dark, light, x, y, s, fold = 1.3) {
	const f = Math.sin(x * fold + s * 1.7 + Math.sin(y * 0.35 + s) * 0.6);
	let c = f > 0.55 ? mix(base, light, (f - 0.55) * 1.4) : f < -0.45 ? mix(base, dark, (-f - 0.45) * 1.6) : base;
	return shade(c, 0.92 + hash(x, y, s) * 0.14);
}
const skinPx = (x, y, s) => shade(SKIN, 0.93 + hash(x, y, s) * 0.12);

// ---------------------------------------------------------------- the skin (64x64 wide)
/** The jagged Shazam bolt on the chest, rows 5..11 of the 8x12 front. */
function boltAt(x, y) {
	const rows = { 6: [5, 6], 7: [4, 5], 8: [3, 4, 5, 6], 9: [4, 5], 10: [3, 4], 11: [2, 3] };
	return (rows[y] || []).includes(x);
}
function skin() {
	const img = new Img(64, 64);
	// --- head: dark weathered face, deep-set eyes under heavy grey brows, a broad nose, cheek lines; hair everywhere else
	box(img, 0, 0, 8, 8, 8, (face, x, y) => {
		if (face === 'top' || face === 'back') return hair(x, y, 3);
		if (face === 'bottom') return hair(x, y, 4, 0.2);
		if (face === 'front') {
			if (y === 0) return hair(x, y, 5);
			if (y === 1) return BAND;
			if (y === 2) { // the forehead, two deep lines
				if (x === 0 || x === 7) return SKIN_DARK;
				return (x >= 2 && x <= 5) && hash(x, 2, 9) < 0.6 ? SKIN_DARK : skinPx(x, y, 6);
			}
			if (y === 3) { // heavy grey brows over a dark brow ridge
				if (x === 1 || x === 2 || x === 5 || x === 6) return x === 1 || x === 6 ? HAIR : HAIR_LIGHT;
				if (x === 3 || x === 4) return SKIN_DARK;
				return SKIN_DARK;
			}
			if (y === 4) { // deep-set eyes in shadow
				if (x === 1 || x === 6) return SKIN_DEEP;
				if (x === 2 || x === 5) return [208, 196, 170];
				if (x === 3 || x === 4) return SKIN_LIGHT; // the bridge
				return SKIN_DARK;
			}
			if (y === 5) { // a broad nose, lined cheeks
				if (x === 3 || x === 4) return SKIN_HI;
				if (x === 2 || x === 5) return SKIN_DARK;
				return skinPx(x, y, 7);
			}
			if (y === 6) return (x === 3 || x === 4) ? SKIN_DEEP : hair(x, y, 8); // nostrils, moustache
			return hair(x, y, 9);
		}
		// sides: hair with an ear (mostly behind the mane anyway)
		if (y >= 3 && y <= 5 && x >= 5 && x <= 6) return y === 4 ? SKIN_DARK : SKIN;
		return hair(x, y, face === 'left' ? 10 : 11);
	});
	// hat layer: a curly fringe and side locks framing the face; the band itself is geometry
	box(img, 32, 0, 8, 8, 8, (face, x, y) => {
		if (face === 'front') {
			if (y === 0) return hash(x, 0, 12) < 0.8 ? hair(x, y, 12) : null;
			if (x === 0 || x === 7) return y >= 6 ? hair(x, y, 13) : (y >= 2 ? hair(x, y, 13, 0.1) : null);
			return null;
		}
		if (face === 'bottom') return null;
		return hair(x, y, face.length + 14);
	});
	// --- body: the inner deep red robe in a V, framed by the rust outer robe with a gold-thread edge; the glowing bolt
	const robeFront = (x, y, s) => {
		const half = 1 + Math.floor(y / 4); // the V opens downwards
		const inV = x >= 4 - half && x <= 3 + half;
		const edge = x === 3 - half || x === 4 + half;
		if (y <= 1 && x >= 2 && x <= 5) return skinPx(x, y, s); // the collarbones (under the beard)
		if (boltAt(x, y)) return GOLD;
		if (inV) return cloth(INNER, INNER_DARK, [150, 30, 38], x, y, s, 2.2);
		if (edge) return y % 3 === 0 ? GOLD_LIGHT : GOLD_DARK;
		return cloth(RUST, RUST_DARK, RUST_LIGHT, x, y, s);
	};
	box(img, 16, 16, 8, 12, 4, (face, x, y) => {
		if (face === 'front') return robeFront(x, y, 31);
		if (face === 'top') return cloth(RUST, RUST_DARK, RUST_LIGHT, x, y, 32);
		if (face === 'bottom') return INNER_DARK;
		return cloth(RUST, RUST_DARK, RUST_LIGHT, x, y, face === 'back' ? 33 : 34);
	});
	// jacket: the outer robe's heavy lapels and sides stand out over the inner robe (V and bolt stay recessed)
	box(img, 16, 32, 8, 12, 4, (face, x, y) => {
		if (face === 'front') {
			const half = 1 + Math.floor(y / 4);
			if (x >= 3 - half && x <= 4 + half) return null;
			if (y <= 1) return null;
			let c = cloth(RUST, RUST_DARK, RUST_LIGHT, x, y, 35);
			if (x === 2 - half || x === 5 + half) c = mix(c, RUST_LIGHT, 0.3);
			return c;
		}
		if (face === 'left' || face === 'right') return cloth(RUST, RUST_DARK, RUST_LIGHT, x, y, 36, 1.8);
		if (face === 'back') return cloth(RUST, RUST_DARK, RUST_LIGHT, x, y, 37);
		return null;
	});
	// --- arms: bare, dark and sinewy (old but strong), gold armband and wrist cuff; the left shoulder is under the drape
	const arm = (face, x, y, fw, s) => {
		if (face === 'top') return skinPx(x, y, s);
		if (face === 'bottom') return SKIN_DARK;
		if (y === 2 || y === 3) return y === 2 ? GOLD : GOLD_DARK; // the armband
		if (y === 8 || y === 9) return (x + y) % 3 === 0 ? GOLD_LIGHT : GOLD; // the cuff
		if (y >= 10) { // the hand: knuckles, fingers
			if (face === 'front' || face === 'back') return y === 11 ? (x % 2 ? SKIN_DARK : SKIN) : SKIN_LIGHT;
			return y === 11 ? SKIN_DARK : SKIN;
		}
		let c = skinPx(x, y, s);
		if (y >= 4 && y <= 6 && (face === 'front' || face === 'right' || face === 'left') && (x === 1 || x === 2)) c = mix(c, SKIN_LIGHT, 0.5); // the muscle
		if (y === 7) c = mix(c, SKIN_DARK, 0.4); // the elbow crease
		if (x === 0 || x === fw - 1) c = mix(c, SKIN_DARK, 0.45);
		if (y <= 1) c = mix(c, SKIN_DARK, 0.25);
		return c;
	};
	box(img, 40, 16, 4, 12, 4, (f, x, y, fw) => arm(f, x, y, fw, 41));
	box(img, 32, 48, 4, 12, 4, (f, x, y, fw) => arm(f, x, y, fw, 42));
	// sleeves: only the gold rings stand out
	const ring = (face, x, y) => {
		if (face === 'top' || face === 'bottom') return null;
		if (y === 2) return GOLD_LIGHT;
		if (y === 3) return GOLD_DARK;
		if (y === 8) return (x % 2) ? GOLD : GOLD_LIGHT;
		if (y === 9) return GOLD_DARK;
		return null;
	};
	box(img, 40, 32, 4, 12, 4, ring);
	box(img, 48, 48, 4, 12, 4, ring);
	// --- legs: the inner robe down to the ankles (the skirt geometry covers most of it), bare feet
	const leg = (face, x, y, fw, s) => {
		if (face === 'top') return cloth(INNER, INNER_DARK, INNER, x, y, s);
		if (face === 'bottom') return SKIN_DARK;
		if (y === 11) return face === 'front' ? (x % 2 ? SKIN_DARK : SKIN_LIGHT) : SKIN;
		if (y === 10) return face === 'front' ? SKIN : SKIN_DARK;
		return cloth(INNER, INNER_DARK, [150, 30, 38], x, y, s, 2);
	};
	box(img, 0, 16, 4, 12, 4, (f, x, y, fw) => leg(f, x, y, fw, 51));
	box(img, 16, 48, 4, 12, 4, (f, x, y, fw) => leg(f, x, y, fw, 52));
	return img;
}

/** The emissive mask: the bolt only, a brighter core along its middle. */
function glow() {
	const img = new Img(64, 64);
	for (let y = 0; y < 12; y++) for (let x = 0; x < 8; x++) {
		if (!boltAt(x, y)) continue;
		const core = y === 8 ? (x >= 3 && x <= 5) : (x === Math.max(...[0, 1, 2, 3, 4, 5, 6, 7].filter((i) => boltAt(i, y))) - 0);
		img.set(20 + x, 20 + y, core ? [255, 246, 176] : [255, 200, 64]);
	}
	return img;
}

// ---------------------------------------------------------------- the parts atlas (128x128; UVs match WizardShazamParts.java)
function parts() {
	const img = new Img(128, 128);
	// beard: moustache on the top front row, the mouth's shadow, strands down
	const beard = (s, mouth) => (face, x, y, fw, fh) => {
		if (face === 'top') return mouth ? null : hair(x, y, s);
		if (face === 'bottom') return hair(x, y, s + 1, 0.15);
		let c = hair(x, y, s);
		if (face === 'front' && mouth) {
			if (y === 0) return (x === 0 || x === fw - 1) ? hair(x, y, s, 0.1) : (x === 3 || x === 4 ? HAIR_LIGHT : HAIR);
			if (y === 1 && (x === 3 || x === 4)) return [56, 38, 32]; // the mouth
		}
		if (face === 'front' && !mouth && (x === 0 || x === fw - 1) && y === fh - 1) return null; // a ragged end
		if (face === 'back') c = shade(c, 0.8);
		return c;
	};
	box(img, 0, 0, 8, 4, 2, beard(101, true)); // beardTop (8x4x2)
	box(img, 0, 8, 6, 2, 2, beard(103, false)); // beardMid (6x2x2)
	box(img, 0, 14, 4, 1, 2, beard(105, false)); // beardTip (4x1x2)
	// mane: a curly mass bushing out under the band round the sides and back of the head
	box(img, 24, 0, 10, 7, 7, (face, x, y, fw, fh) => {
		if (face === 'front') return hair(x, y, 107);
		if (face === 'bottom') return hair(x, y, 108, 0.35);
		if ((face === 'left' || face === 'right' || face === 'back') && y === fh - 1 && hash(x, y, 109) < 0.3) return null; // ragged ends
		return hair(x, y, face.length + 110);
	});
	box(img, 60, 20, 8, 7, 1, (face, x, y, fw, fh) => (y === fh - 1 && hash(x, y, 115) < 0.4 ? null : hair(x, y, 116))); // curls at the back
	// wild curls on top: a ragged crown, holes let the head show through
	box(img, 60, 30, 8, 1, 8, (face, x, y, fw, fh) => {
		if (face === 'bottom') return hair(x, y, 140, 0.4);
		if (face === 'top') { const e = Math.min(x, y, 7 - x, 7 - y); return hash(x, y, 141) < (e === 0 ? 0.3 : 0.05) ? null : hair(x, y, 142); }
		return hash(x, y, face.length + 143) < 0.2 ? null : hair(x, y, 144);
	});
	// headband: red cloth with a darker knot at the back
	box(img, 60, 8, 9, 1, 9, (face, x, y, fw) => {
		if (face === 'top' || face === 'bottom') return null;
		if (face === 'back' && x === 4) return BAND_DARK;
		const n = hash(x, y, 117);
		return n < 0.2 ? BAND_DARK : n > 0.85 ? BAND_LIGHT : BAND;
	});
	// long hair down the back and a ragged tail
	box(img, 0, 24, 9, 8, 2, (face, x, y, fw, fh) => {
		if (face === 'front') return hair(x, y, 118, 0.35);
		if (face === 'top') return hair(x, y, 119);
		if (face === 'bottom') return hair(x, y, 120, 0.3);
		return hair(x, y, face === 'back' ? 121 : 122);
	});
	box(img, 24, 30, 7, 7, 2, (face, x, y, fw, fh) => (face === 'front' ? hair(x, y, 125, 0.35) : hair(x + 3, y + 8, face === 'back' ? 121 : 122)));
	box(img, 24, 24, 5, 3, 1, (face, x, y, fw, fh) => {
		if (y === fh - 1 && hash(x, 0, 123) < 0.45) return null;
		return hair(x, y, 124, face === 'front' ? 0.3 : 0);
	});
	// the rust mantle down the back, heavy folds, a worn gold-thread hem
	box(img, 0, 44, 10, 21, 1, (face, x, y, fw, fh) => {
		if (face === 'top') return cloth(RUST, RUST_DARK, RUST_LIGHT, x, y, 125);
		if (face === 'bottom') return RUST_DARK;
		if (y === fh - 1) return (x % 3 === 0) ? GOLD_DARK : RUST_DARK;
		if (y === fh - 2) return GOLD_DARK;
		const c = cloth(RUST, RUST_DARK, RUST_LIGHT, x, y, face === 'front' ? 126 : 127, 1.1);
		return face === 'front' ? shade(c, 0.75) : c;
	});
	// side locks over the shoulders
	const lock = (s) => (face, x, y, fw, fh) => (y === fh - 1 && x === 0 ? null : hair(x, y, s, face === 'back' ? 0.3 : 0));
	box(img, 36, 44, 2, 9, 1, lock(130));
	box(img, 44, 44, 2, 9, 1, lock(131));
	// the drape over the left shoulder
	box(img, 52, 44, 5, 9, 5, (face, x, y, fw, fh) => {
		if (face === 'bottom') return RUST_DARK;
		if (face === 'top') return cloth(RUST, RUST_DARK, RUST_LIGHT, x, y, 132, 1.6);
		if (y === fh - 1) return (x + y) % 2 ? GOLD_DARK : RUST_DARK; // the hem
		return cloth(RUST, RUST_DARK, RUST_LIGHT, x + (face === 'front' ? 0 : 5), y, 133, 1.7);
	});
	// the robe's skirt: inner deep red at the front, rust round the sides and back, a darker hem
	const skirt = (s, low) => (face, x, y, fw, fh) => {
		if (face === 'top') return INNER_DARK;
		if (face === 'bottom') return INNER_DARK;
		if (low && y === fh - 1) return face === 'front' ? INNER_DARK : RUST_DARK;
		if (face === 'front') {
			const edge = low ? 1 : 2;
			if (x < edge || x >= fw - edge) return cloth(RUST, RUST_DARK, RUST_LIGHT, x, y, s, 1.6);
			if (x === edge || x === fw - edge - 1) return y % 3 === 0 ? GOLD_LIGHT : GOLD_DARK;
			return cloth(INNER, INNER_DARK, [150, 30, 38], x, y, s + 1, 1.9);
		}
		return cloth(RUST, RUST_DARK, RUST_LIGHT, x, y, s + (face === 'back' ? 2 : 3), 1.4);
	};
	box(img, 0, 70, 9, 6, 5, skirt(140, false));
	box(img, 30, 70, 10, 5, 6, skirt(150, true));
	// bare toes
	box(img, 64, 70, 3, 1, 1, (face, x) => (face === 'front' ? (x % 2 ? SKIN_DARK : SKIN_LIGHT) : SKIN));
	// --- the staff: carved dark wood, a leather grip, a carved knob, prongs cradling the crystal
	box(img, 110, 0, 2, 34, 2, (face, x, y) => {
		if (face === 'top' || face === 'bottom') return WOOD_DARK;
		// a spiral carving round the shaft
		const t = (y + x * 2 + (face === 'front' ? 0 : face === 'right' ? 1 : face === 'back' ? 2 : 3)) % 5;
		let c = t === 0 ? WOOD_DARK : t === 2 ? WOOD_LIGHT : WOOD;
		return shade(c, 0.9 + hash(x, y, 160) * 0.2);
	});
	box(img, 100, 24, 2, 4, 2, (face, x, y) => (y % 2 ? shade(LEATHER, 0.75) : LEATHER));
	box(img, 100, 32, 3, 2, 3, (face, x, y) => (face === 'top' ? WOOD_DARK : y === 0 ? WOOD_LIGHT : WOOD));
	box(img, 100, 40, 3, 1, 3, (face, x, y) => ((x % 2) ? WOOD_DARK : WOOD_LIGHT));
	box(img, 100, 46, 1, 6, 1, (face, x, y) => (y <= 1 ? WOOD_LIGHT : y % 2 ? WOOD : WOOD_DARK));
	// the crystal: white core, blue edges (also drawn by the glow pass)
	box(img, 80, 70, 3, 5, 3, (face, x, y, fw, fh) => {
		if (face === 'top' || face === 'bottom') return CRYSTAL;
		if (x === 1 && y > 0 && y < fh - 1) return [255, 255, 255];
		return (y === 0 || y === fh - 1) ? CRYSTAL_MID : (x === 0 ? CRYSTAL : CRYSTAL_MID);
	});
	box(img, 96, 70, 2, 2, 2, (face, x, y) => (y === 0 ? [255, 255, 255] : CRYSTAL));
	// the halo: a soft additive glow (dim blue, brighter middle; black = nothing in the eyes pass)
	box(img, 80, 80, 5, 7, 5, (face, x, y, fw, fh) => {
		const dx = (x - (fw - 1) / 2) / (fw / 2), dy = (y - (fh - 1) / 2) / (fh / 2);
		const r = Math.sqrt(dx * dx + dy * dy);
		const k = Math.max(0, 1 - r) * 0.55;
		return [CRYSTAL_DEEP[0] * k, CRYSTAL_DEEP[1] * k, CRYSTAL_DEEP[2] * k + 20 * k];
	});
	box(img, 106, 70, 2, 2, 2, (face, x, y) => ((x + y) % 2 ? [90, 90, 96] : [130, 130, 136])); // the iron foot
	return img;
}

const s = skin(), g = glow(), p = parts();
s.save('wizard_shazam.png');
g.save('wizard_shazam_glow.png');
p.save('wizard_shazam_parts.png');

// preview: skin, glow and parts at 6x on a checker
(function preview() {
	const S = 6, W = 64 + 8 + 64 + 8 + 128, H = 128;
	const img = new Img(W * S, H * S);
	const blit = (src, ox) => { for (let y = 0; y < src.h; y++) for (let x = 0; x < src.w; x++) { const q = src.get(x, y); const col = q[3] ? q : (((x >> 2) + (y >> 2)) % 2 ? [60, 60, 60] : [90, 90, 90]); for (let dy = 0; dy < S; dy++) for (let dx = 0; dx < S; dx++) img.set((ox + x) * S + dx, y * S + dy, col); } };
	blit(s, 0); blit(g, 72); blit(p, 144);
	fs.writeFileSync(path.join(__dirname, 'wizard_preview_x6.png'), encode(img.w, img.h, img.d));
})();
console.log('written to', OUT);
