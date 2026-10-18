// v0.16.7 Dark Shadow: the costume skins (Tokoyami / Tsukuyomi, wide + slim), the shadow creature's texture, the
// Black Ankh armour overlay, and preview sheets. Usage: node scratchpad/gen_dark_shadow.js
'use strict';
const fs = require('fs');
const path = require('path');
const T = require('C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod/scratchpad/skin_tools.js');

const ROOT = path.resolve(__dirname, '..');
const TEX = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/entity/quirk');
const SHOTS = 'C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod/scratchpad/quirk_shots/dark_shadow';
fs.mkdirSync(path.join(TEX, 'dark_shadow'), { recursive: true });
fs.mkdirSync(SHOTS, { recursive: true });

// ------------------------------------------------------------------ image helpers
function img(w, h) { return { w, h, px: Buffer.alloc(w * h * 4) }; }
const hex = s => { const n = parseInt(s.replace('#', ''), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255, 255]; };
function set(im, x, y, c) { if (!c || x < 0 || y < 0 || x >= im.w || y >= im.h) return; const i = (y * im.w + x) * 4; im.px[i] = c[0]; im.px[i + 1] = c[1]; im.px[i + 2] = c[2]; im.px[i + 3] = c[3] === undefined ? 255 : c[3]; }
function get(im, x, y) { const i = (y * im.w + x) * 4; return [im.px[i], im.px[i + 1], im.px[i + 2], im.px[i + 3]]; }
// a stable per-texel hash noise 0..1
function hash(x, y, s = 0) { let h = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; h = (h ^ (h >>> 13)) * 1274126177; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; }
const mix = (a, b, t) => [0, 1, 2].map(k => Math.round(a[k] + (b[k] - a[k]) * t)).concat([255]);
const shade = (c, f) => [Math.max(0, Math.min(255, Math.round(c[0] * f))), Math.max(0, Math.min(255, Math.round(c[1] * f))), Math.max(0, Math.min(255, Math.round(c[2] * f))), 255];

// the standard box unwrap: faces of a box w x h x d whose texture starts at (u, v)
function faces(u, v, w, h, d) {
	return {
		top: [u + d, v, w, d], bottom: [u + d + w, v, w, d],
		right: [u, v + d, d, h], front: [u + d, v + d, w, h], left: [u + d + w, v + d, d, h], back: [u + d + w + d, v + d, w, h],
	};
}
// paint every face of a box: fn(face, x, y, fw, fh) -> colour | null
function paintBox(im, u, v, w, h, d, fn) {
	const f = faces(u, v, w, h, d);
	for (const name of Object.keys(f)) {
		const [fx, fy, fw, fh] = f[name];
		for (let y = 0; y < fh; y++) for (let x = 0; x < fw; x++) set(im, fx + x, fy + y, fn(name, x, y, fw, fh));
	}
}

// ------------------------------------------------------------------ palettes
const FEATHER = ['#07050b', '#0e0a15', '#16101f', '#1f1730', '#2b2045'].map(hex); // head feathers, blue-black sheen
const SUIT = ['#2a0c09', '#3f130e', '#561c15', '#6c271d', '#84372a'].map(hex); // dark red-brown body suit
const CLOAK = ['#08080d', '#101019', '#191926', '#232336', '#30304a'].map(hex); // the dark cloak
const LINING = ['#2a1036', '#3b1a4c'].map(hex); // its purple lining
const LEATHER = ['#140c08', '#21140d', '#2e1d13', '#3d2819'].map(hex);
const BRASS = ['#6b4f1c', '#a07b2c', '#d4ad4e', '#f2d888'].map(hex);
const BEAK = ['#121014', '#1d1a20', '#2b2730', '#4a4250', '#7d6a3a'].map(hex);
const EYE_RED = ['#5a0606', '#a50f0f', '#e02424', '#ff8a7a'].map(hex);
const GLOVE = ['#0b0a0d', '#141218', '#1e1b24'].map(hex);

// feather scallops: rows of small rounded feathers, light upper edge, dark lower edge
function feather(pal, x, y, seed, rowH = 3) {
	const row = Math.floor(y / rowH), off = row % 2 ? 1 : 0;
	const fx = (x + off) % 3, fy = y % rowH;
	let i = 2;
	if (fy === 0) i = 3; // the rounded top edge catches light
	if (fy === rowH - 1) i = 1; // the overlap shadow
	if (fx === 0 && fy > 0) i = Math.max(0, i - 1); // the feather's side
	const n = hash(x, y, seed);
	if (n > 0.86) i = Math.min(pal.length - 1, i + 1); else if (n < 0.12) i = Math.max(0, i - 1);
	return pal[i];
}
function cloth(pal, x, y, seed, base = 2) {
	const n = hash(x, y, seed);
	let i = base;
	if (n > 0.8) i++; else if (n < 0.18) i--;
	return pal[Math.max(0, Math.min(pal.length - 1, i))];
}

// ================================================================== the costume (wide arms; slim converted after)
function costume() {
	const im = img(64, 64);
	// ---------------- head: Tokoyami's own feathered bird head
	paintBox(im, 0, 0, 8, 8, 8, (f, x, y) => {
		let c = feather(FEATHER, x, y + (f === 'top' ? 0 : 1), 11, 3);
		if (f === 'front') {
			// a darker face area, the beak in the middle, red eyes either side
			c = feather(FEATHER, x, y, 12, 2);
			if (y === 2 && (x === 1 || x === 2 || x === 5 || x === 6)) c = FEATHER[4]; // the brow ridge
			if (y === 3 && (x === 1 || x === 2)) c = x === 1 ? EYE_RED[1] : EYE_RED[2];
			if (y === 3 && (x === 5 || x === 6)) c = x === 6 ? EYE_RED[1] : EYE_RED[2];
			if (y === 4 && (x === 1 || x === 6)) c = EYE_RED[0];
			if (y === 4 && (x === 2 || x === 5)) c = EYE_RED[3]; // the glint
			// the beak: broad at the base (rows 3-4), hooked tip low (rows 5-7)
			if (y === 3 && (x === 3 || x === 4)) c = BEAK[2];
			if (y === 4 && x >= 3 && x <= 4) c = BEAK[3];
			if (y === 5 && x >= 3 && x <= 4) c = BEAK[2];
			if (y === 6 && x >= 3 && x <= 4) c = x === 3 ? BEAK[1] : BEAK[2];
			if (y === 7 && (x === 3 || x === 4)) c = x === 3 ? BEAK[4] : BEAK[1];
			if (y === 5 && (x === 2 || x === 5)) c = BEAK[0]; // the gape line
		}
		if (f === 'bottom') c = FEATHER[1];
		if (f === 'right' || f === 'left') {
			const nearFront = f === 'right' ? x === 7 : x === 0;
			if (nearFront && y === 3) c = EYE_RED[0];
			if (y >= 6) c = feather(FEATHER, x, y, 13, 2);
		}
		return c;
	});
	// ---------------- hat layer: the feather crest, side tufts and the beak standing out
	paintBox(im, 32, 0, 8, 8, 8, (f, x, y, fw, fh) => {
		const n = hash(x, y, 21);
		if (f === 'top') {
			// spiky feathers swept back: dense toward the back
			if (y >= 3 || n > 0.45) return feather(FEATHER, x, y, 22, 2);
			return null;
		}
		if (f === 'back') {
			// long feathers hanging down the back of the head, ragged ends
			const len = 5 + Math.floor(hash(x, 0, 23) * 4);
			return y < len ? feather(FEATHER, x, y, 24, 3) : null;
		}
		if (f === 'right' || f === 'left') {
			// swept-back side tufts: the back half and the top rows
			const back = f === 'right' ? x <= 3 : x >= 4;
			if (y <= 1 || (back && y <= 4 + (n > 0.5 ? 1 : 0))) return feather(FEATHER, x, y, 25, 2);
			if (y >= 5 && y <= 6 && ((f === 'right' && x >= 5) || (f === 'left' && x <= 2))) return FEATHER[2]; // the cheek ruff
			return null;
		}
		if (f === 'front') {
			if (y === 0) return n > 0.3 ? FEATHER[3] : FEATHER[2]; // the crest's front fringe
			if (y === 1 && (x <= 1 || x >= 6)) return FEATHER[2];
			if (y === 2 && (x === 1 || x === 2 || x === 5 || x === 6)) return FEATHER[3]; // the angry brows
			if (y === 4 && (x === 3 || x === 4)) return BEAK[3];
			if (y === 5 && (x === 3 || x === 4)) return BEAK[2];
			if (y === 6 && x === 3) return BEAK[4];
			return null;
		}
		if (f === 'bottom') return null;
		return null;
	});
	// ---------------- body: the dark red-brown suit, a belt
	paintBox(im, 16, 16, 8, 12, 4, (f, x, y, fw) => {
		let c = cloth(SUIT, x, y, 31);
		if (f === 'front' || f === 'back') {
			if (x === 0 || x === fw - 1) c = SUIT[1]; // side shading
			if (f === 'front' && (x === 3 || x === 4) && y < 9) c = x === 3 ? SUIT[3] : SUIT[2]; // the centre seam / zip
			if (f === 'front' && y >= 2 && y <= 5 && (x === 1 || x === 6)) c = SUIT[3]; // chest panel highlight
		}
		if (y === 9 || y === 10) { // the belt
			c = y === 9 ? LEATHER[2] : LEATHER[1];
			if (f === 'front' && (x === 3 || x === 4)) c = y === 9 ? BRASS[3] : BRASS[1];
			if (f === 'front' && (x === 2 || x === 5)) c = BRASS[0];
			if ((f === 'right' || f === 'left') && x === 1) c = LEATHER[3]; // a pouch strap
		}
		if (y === 11) c = SUIT[1];
		if (f === 'top') c = cloth(SUIT, x, y, 32, 1);
		if (f === 'bottom') c = SUIT[0];
		return c;
	});
	// ---------------- jacket layer: the cloak -- high collar, closed over the chest, open below, ragged hem
	paintBox(im, 16, 32, 8, 12, 4, (f, x, y, fw, fh) => {
		const n = hash(x, y, 41);
		const hem = 10 + Math.floor(hash(x, 7, 42) * 2); // ragged bottom edge
		if (f === 'top') return x === 0 || x === fw - 1 ? CLOAK[3] : cloth(CLOAK, x, y, 43, 2);
		if (f === 'bottom') return CLOAK[0];
		if (y > hem) return null;
		let c = cloth(CLOAK, x, y, 44, 2);
		// vertical folds
		if ((x + (f === 'back' ? 1 : 0)) % 3 === 0) c = CLOAK[1];
		if ((x + (f === 'back' ? 1 : 0)) % 3 === 1 && n > 0.4) c = CLOAK[3];
		if (y === 0) c = CLOAK[4]; // the collar rim
		if (y === 1 && (f === 'front' || f === 'back')) c = CLOAK[3];
		if (f === 'front') {
			if (y >= 7 && x >= 2 && x <= 5) return null; // open below the chest: the suit and belt show
			if (y >= 6 && (x === 1 || x === 6)) c = LINING[n > 0.5 ? 1 : 0]; // the purple lining at the opening
			if (y === 2 && (x === 3 || x === 4)) c = BRASS[2]; // the clasp
			if (y === 3 && (x === 3 || x === 4)) c = BRASS[0];
		}
		if (y === hem) c = CLOAK[0];
		return c;
	});
	// ---------------- arms: suit sleeves, dark gloves; the cloak over the shoulders
	const arm = (u, v, seed) => paintBox(im, u, v, 4, 12, 4, (f, x, y) => {
		let c = cloth(SUIT, x, y, seed);
		if (y === 7) c = SUIT[1]; // the elbow crease
		if (y >= 9) c = cloth(GLOVE, x, y, seed + 1, 1); // the glove
		if (y === 9) c = GLOVE[2]; // the cuff
		if (f === 'top') c = cloth(SUIT, x, y, seed + 2);
		if (f === 'bottom') c = GLOVE[0];
		return c;
	});
	arm(40, 16, 51);
	arm(32, 48, 52);
	const sleeve = (u, v, seed) => paintBox(im, u, v, 4, 12, 4, (f, x, y) => {
		const n = hash(x, y, seed);
		if (f === 'top') return cloth(CLOAK, x, y, seed, 2);
		if (f === 'bottom') return null;
		const hem = 4 + (hash(x, 3, seed) > 0.5 ? 1 : 0);
		if (y > hem) return null;
		let c = cloth(CLOAK, x, y, seed + 1, 2);
		if (x % 2 === 0 && n > 0.3) c = CLOAK[1];
		if (y === 0) c = CLOAK[4];
		if (y === hem) c = CLOAK[0];
		return c;
	});
	sleeve(40, 32, 53);
	sleeve(48, 48, 54);
	// ---------------- legs: suit trousers, boots
	const leg = (u, v, seed) => paintBox(im, u, v, 4, 12, 4, (f, x, y) => {
		let c = cloth(SUIT, x, y, seed);
		if (f === 'front' && y === 5) c = SUIT[3]; // the knee
		if (f === 'front' && y === 6) c = SUIT[1];
		if (y >= 8) c = cloth(LEATHER, x, y, seed + 1, 1); // the boot
		if (y === 8) c = LEATHER[3];
		if (y === 11) c = LEATHER[0]; // the sole
		if (f === 'top') c = cloth(SUIT, x, y, seed + 2);
		if (f === 'bottom') c = LEATHER[0];
		return c;
	});
	leg(0, 16, 61);
	leg(16, 48, 62);
	const pants = (u, v, seed) => paintBox(im, u, v, 4, 12, 4, (f, x, y) => {
		if (f === 'top' || f === 'bottom') return null;
		if (y === 8) return LEATHER[3]; // the boot top flares out a little
		if (y === 9 && (f === 'front' || f === 'right' || f === 'left') && x % 2 === 0) return LEATHER[2]; // the buckles
		if (y === 9 && f === 'front' && x === 1) return BRASS[1];
		return null;
	});
	pants(0, 32, 63);
	pants(0, 48, 64);
	return im;
}

// ================================================================== the creature (64x64; the boxes in DarkShadowModel)
const BODY = ['#06030a', '#0d0616', '#160a24', '#211035', '#2f1a4e', '#4a2a80'].map(hex);
const EYE = ['#ffd23a', '#fff07a', '#fffbe0'].map(hex);
const CLAW = ['#0e0a14', '#1a1326', '#2c2140', '#5a4878'].map(hex);
function creature() {
	const im = img(64, 64);
	const body = (x, y, seed) => {
		const c = feather(BODY, x, y, seed, 3);
		return hash(x, y, seed + 7) > 0.93 ? BODY[5] : c;
	};
	paintBox(im, 0, 0, 8, 7, 8, (f, x, y) => body(x, y, 1)); // skull
	paintBox(im, 32, 0, 4, 3, 6, (f, x, y) => (f === 'top' && x === 1) ? CLAW[1] : (y === 0 ? CLAW[1] : CLAW[0])); // upper beak
	paintBox(im, 52, 0, 2, 3, 3, (f, x, y) => y === 2 ? CLAW[2] : CLAW[1]); // the hooked tip
	paintBox(im, 32, 10, 3, 2, 5, (f, x, y) => (f === 'top' ? hex('#3a0c18') : CLAW[0])); // the jaw (red mouth on top)
	paintBox(im, 0, 16, 2, 5, 6, (f, x, y) => body(x, y, 2)); // crest feather
	paintBox(im, 16, 16, 1, 6, 5, (f, x, y) => body(x, y, 3)); // cheek ruff
	paintBox(im, 52, 7, 2, 1, 1, (f, x, y) => EYE[f === 'front' ? 2 : 1]); // eye (glows)
	paintBox(im, 0, 28, 4, 4, 16, (f, x, y) => body(x, y, 4)); // the tendril segment
	paintBox(im, 40, 28, 1, 1, 4, (f, x, y) => (f === 'front' || y === 0) ? CLAW[3] : CLAW[1]); // claw finger
	paintBox(im, 40, 34, 3, 3, 3, (f, x, y) => body(x, y, 5)); // the hand
	paintBox(im, 52, 28, 1, 1, 5, (f, x, y) => (f === 'front' ? BODY[5] : BODY[2])); // spike
	paintBox(im, 0, 48, 16, 2, 2, (f, x, y) => body(x, y, 6)); // wing bone
	paintBox(im, 36, 48, 3, 12, 1, (f, x, y, fw, fh) => { // wing feather: dark with a violet rim and ragged tip
		if (y === fh - 1 && x !== 1) return null;
		if (x === 0 && (f === 'front' || f === 'back')) return BODY[4];
		return y % 4 === 0 ? BODY[3] : (hash(x, y, 8) > 0.7 ? BODY[2] : BODY[1]);
	});
	return im;
}

// ================================================================== Black Ankh (player-layout overlay, translucent)
function ankh() {
	const im = img(64, 64);
	const c = (x, y, seed, a = 248) => { const col = feather(BODY, x, y, seed, 3); return [col[0], col[1], col[2], a]; };
	const rim = (a = 235) => [BODY[5][0], BODY[5][1], BODY[5][2], a];
	// body + arms + legs + head-less (the head keeps Tokoyami's own look)
	paintBox(im, 16, 16, 8, 12, 4, (f, x, y) => (y === 0 || (f === 'front' && (x === 3 || x === 4) && y % 3 === 1)) ? rim() : c(x, y, 71));
	for (const [u, v, s] of [[40, 16, 72], [32, 48, 73]]) {
		paintBox(im, u, v, 4, 12, 4, (f, x, y) => {
			if (y >= 10 && (f === 'front' || f === 'bottom') && x % 2 === 0) return [CLAW[3][0], CLAW[3][1], CLAW[3][2], 255]; // claw tips
			if (y === 0 || y === 6) return rim();
			return c(x, y, s);
		});
	}
	for (const [u, v, s] of [[0, 16, 74], [16, 48, 75]]) {
		paintBox(im, u, v, 4, 12, 4, (f, x, y) => (y === 5 && f === 'front') ? rim() : c(x, y, s, 242));
	}
	return im;
}

// ------------------------------------------------------------------ write + previews
function write(file, im) { fs.writeFileSync(file, T.encode(im.w, im.h, im.px)); }
const wide = costume();
const slim = T.convertArms(wide, true);
write(path.join(TEX, 'dark_shadow_wide.png'), wide);
write(path.join(TEX, 'dark_shadow_slim.png'), slim);
write(path.join(TEX, 'dark_shadow/creature.png'), creature());
write(path.join(TEX, 'dark_shadow/ankh.png'), ankh());

// a front / back / sides contact sheet of the costume (base layer then the outer layer over it), 8x
function sheet(skin, slimArms, file) {
	const S = 8, W = 4 * 20 * S, H = 34 * S;
	const out = img(W, H);
	for (let i = 0; i < out.px.length; i += 4) { out.px[i] = 120; out.px[i + 1] = 130; out.px[i + 2] = 140; out.px[i + 3] = 255; }
	const a = slimArms ? 3 : 4;
	// [part, base(u,v), over(u,v), w, h, d, x, y] in view pixels
	const parts = [
		['head', 0, 0, 32, 0, 8, 8, 8, 4, 0], ['body', 16, 16, 16, 32, 8, 12, 4, 4, 8],
		['rarm', 40, 16, 40, 32, a, 12, 4, 4 - a, 8], ['larm', 32, 48, 48, 48, a, 12, 4, 12, 8],
		['rleg', 0, 16, 0, 32, 4, 12, 4, 4, 20], ['lleg', 16, 48, 0, 48, 4, 12, 4, 8, 20],
	];
	const views = ['front', 'back', 'right', 'left'];
	views.forEach((view, vi) => {
		const ox = vi * 20 + 2;
		for (const [name, bu, bv, ou, ov, w, h, d, px0, py0] of parts) {
			for (const layer of [[bu, bv], [ou, ov]]) {
				const F = faces(layer[0], layer[1], w, h, d);
				let face = F[view], X = px0, mirror = false;
				if (view === 'back') { X = 16 - px0 - w; }
				if (view === 'right' || view === 'left') {
					// side view: only the outermost limb of each pair; the face is d wide, centred
					const side = view === 'right' ? ['rarm', 'rleg', 'head', 'body'] : ['larm', 'lleg', 'head', 'body'];
					if (!side.includes(name)) continue;
					X = 8 - d / 2;
				}
				const [fx, fy, fw, fh] = face;
				for (let y = 0; y < fh; y++) for (let x = 0; x < fw; x++) {
					const c = get(skin, fx + x, fy + y);
					if (c[3] === 0) continue;
					for (let yy = 0; yy < S; yy++) for (let xx = 0; xx < S; xx++) set(out, (ox + X + x) * S + xx, (py0 + 1 + y) * S + yy, c);
				}
			}
		}
	});
	write(file, out);
}
sheet(wide, false, path.join(SHOTS, 'costume_wide_sheet.png'));
sheet(slim, true, path.join(SHOTS, 'costume_slim_sheet.png'));
for (const [n, f] of [['costume_wide', 'dark_shadow_wide.png'], ['costume_slim', 'dark_shadow_slim.png'], ['creature', 'dark_shadow/creature.png'], ['ankh', 'dark_shadow/ankh.png']]) {
	const p = T.preview(T.decode(path.join(TEX, f)), 10);
	write(path.join(SHOTS, n + '_texture.png'), p);
}
console.log('ok');
