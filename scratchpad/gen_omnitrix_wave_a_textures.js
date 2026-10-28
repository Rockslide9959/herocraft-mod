// v0.17.1 Omnitrix wave A: the skins and extras sheets of XLR8, Diamondhead, Upgrade and Ghostfreak (all procedural, the same
// helpers as gen_omnitrix_textures.js -- copied so the two scripts never collide).
//   * entity/omnitrix/xlr8.png, diamondhead.png, upgrade.png, ghostfreak.png    64x64 wide player skins
//   * entity/omnitrix/xlr8_extras.png       visor, helmet band, tail stripes, ball feet, claws (swatches)
//   * entity/omnitrix/diamondhead_extras.png crystal facets (translucent) for the head, ridges and blade
//   * entity/omnitrix/upgrade_glow.png      the circuit lines and the eye only (drawn emissive over the body)
//   * entity/omnitrix/upgrade_extras.png    stretched arm, the eye disc, the slick
//   * entity/omnitrix/ghostfreak_extras.png the wispy tail, the purple eye, claws
//   * block/diamondhead_crystal.png         Diamondhead's conjured crystal block
// Usage: node scratchpad/gen_omnitrix_wave_a_textures.js [previewDir]
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('./pnglib.js');

const TEX = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures');
const PREVIEW = process.argv[2];

function img(w, h) { return { w, h, px: Buffer.alloc(w * h * 4) }; }
const hex = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
function hash(x, y, s) { let h = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; h = (h ^ (h >>> 13)) * 1274126177; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; }
function shade(c, f) { return c.map(v => Math.max(0, Math.min(255, Math.round(v * f)))); }
function mixc(a, b, t) { return a.map((v, i) => Math.round(v + (b[i] - v) * t)); }
function put(im, x, y, c, a = 255) { if (x < 0 || y < 0 || x >= im.w || y >= im.h) return; const i = (y * im.w + x) * 4; im.px[i] = c[0]; im.px[i + 1] = c[1]; im.px[i + 2] = c[2]; im.px[i + 3] = a; }
function get(im, x, y) { const i = (y * im.w + x) * 4; return [im.px[i], im.px[i + 1], im.px[i + 2], im.px[i + 3]]; }
function rect(im, x, y, w, h, fn) { for (let v = 0; v < h; v++) for (let u = 0; u < w; u++) { const c = fn(u, v, w, h, x + u, y + v); if (c) put(im, x + u, y + v, c.slice(0, 3), c[3] === undefined ? 255 : c[3]); } }
function save(im, rel) {
	const out = path.join(TEX, rel);
	fs.mkdirSync(path.dirname(out), { recursive: true });
	PNG.write(out, im.w, im.h, im.px);
	if (PREVIEW) {
		const s = im.w <= 32 ? 12 : 8;
		const p = img(im.w * s, im.h * s);
		for (let y = 0; y < p.h; y++) for (let x = 0; x < p.w; x++) {
			const c = get(im, Math.floor(x / s), Math.floor(y / s));
			const chk = ((Math.floor(x / 8) + Math.floor(y / 8)) % 2) ? 200 : 160;
			const a = c[3] / 255;
			put(p, x, y, [0, 1, 2].map(k => Math.round(c[k] * a + chk * (1 - a))));
		}
		fs.mkdirSync(PREVIEW, { recursive: true });
		PNG.write(path.join(PREVIEW, rel.replace(/[\\/]/g, '_')), p.w, p.h, p.px);
	}
}
function faces(ox, oy, w, h, d) {
	return {
		top: [ox + d, oy, w, d], bottom: [ox + d + w, oy, w, d],
		right: [ox, oy + d, d, h], front: [ox + d, oy + d, w, h], left: [ox + d + w, oy + d, d, h], back: [ox + 2 * d + w, oy + d, w, h],
	};
}
function paintBox(im, box, fn) { for (const [name, f] of Object.entries(box)) rect(im, f[0], f[1], f[2], f[3], (u, v, w, h, x, y) => fn(name, u, v, w, h, x, y)); }
const SKIN = {
	head: faces(0, 0, 8, 8, 8), hat: faces(32, 0, 8, 8, 8),
	body: faces(16, 16, 8, 12, 4), jacket: faces(16, 32, 8, 12, 4),
	rArm: faces(40, 16, 4, 12, 4), rSleeve: faces(40, 32, 4, 12, 4),
	lArm: faces(32, 48, 4, 12, 4), lSleeve: faces(48, 48, 4, 12, 4),
	rLeg: faces(0, 16, 4, 12, 4), rPants: faces(0, 32, 4, 12, 4),
	lLeg: faces(16, 48, 4, 12, 4), lPants: faces(0, 48, 4, 12, 4),
};
const disc = (im, x0, y0, s, fn) => { const r = s / 2; for (let y = 0; y < s; y++) for (let x = 0; x < s; x++) { const d = Math.hypot(x + 0.5 - r, y + 0.5 - r) / r; if (d <= 1) { const c = fn(d, x, y); if (c) put(im, x0 + x, y0 + y, c.slice(0, 3), c[3] === undefined ? 255 : c[3]); } } };

// ================================================================ XLR8
function xlr8() {
	const im = img(64, 64);
	const BLUE = ['#0c1e3e', '#16336a', '#22509a', '#2f6cc8', '#4c8be0', '#8cc0f4'].map(hex);
	const BLACK = ['#050608', '#0c0e12', '#15181e', '#20242c', '#2e333c'].map(hex);
	const LIGHT = hex('#9fd8ff'), WHITE = hex('#e8f4ff');
	const blue = (u, v, s, t = 3) => shade(BLUE[t], 0.92 + hash(u, v, s) * 0.14);
	const black = (u, v, s, t = 2) => shade(BLACK[t], 0.9 + hash(u, v, s) * 0.3);
	// head: the blue helmet, black under the visor, a dark jaw with a pale mouth line
	paintBox(im, SKIN.head, (f, u, v, w, h) => {
		if (f === 'top') return (u === 3 || u === 4) ? LIGHT : blue(u, v, 3, 4);
		if (f === 'bottom') return black(u, v, 4, 1);
		if (f === 'front') {
			if (v < 2) return blue(u, v, 5, 4);
			if (v < 5) return black(u, v, 6, 0);
			if (v === 6 && u >= 2 && u <= 5) return LIGHT;
			return black(u, v, 7, 2);
		}
		if (v >= 2 && v < 5) return black(u, v, 8, 1);
		return blue(u, v, 9 + f.length, v > 5 ? 2 : 3);
	});
	// body: a black chest with three light chevrons, blue flanks, a blue back with black bands
	paintBox(im, SKIN.body, (f, u, v, w, h) => {
		if (f === 'front') {
			const x = Math.abs(u - 3.5);
			for (const c of [2, 5, 8]) if (Math.abs(v - (c + x * 0.6)) < 0.55) return v < 9 ? WHITE : LIGHT;
			if (u === 0 || u === 7) return blue(u, v, 21, 2);
			return black(u, v, 22, 2);
		}
		if (f === 'back') return (v % 4 === 1) ? black(u, v, 23, 1) : blue(u, v, 24, 3);
		if (f === 'top') return blue(u, v, 25, 4);
		if (f === 'bottom') return black(u, v, 26, 1);
		return (v % 4 === 1) ? black(u, v, 27, 1) : blue(u, v, 28, 2);
	});
	// arms: blue upper arms, black forearms with a light stripe, black hands
	for (const k of ['rArm', 'lArm']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
		if (f === 'top') return blue(u, v, 31, 4);
		if (f === 'bottom') return black(u, v, 32, 0);
		if (v < 5) return blue(u, v, 33 + f.length, 3);
		if (v === 6) return LIGHT;
		return black(u, v, 35, v >= h - 3 ? 1 : 2);
	});
	// legs: black with a blue thigh stripe, dark grey at the bottom (the ball feet sit there)
	for (const k of ['rLeg', 'lLeg']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
		if (f === 'top') return black(u, v, 41, 2);
		if (f === 'bottom') return black(u, v, 42, 3);
		if (v >= 2 && v <= 4 && f !== 'back') return blue(u, v, 43, 3);
		if (v === 5 && f === 'front') return LIGHT;
		return black(u, v, 44 + f.length, v >= h - 2 ? 3 : 2);
	});
	save(im, 'entity/omnitrix/xlr8.png');

	const ex = img(32, 32);
	rect(ex, 0, 0, 4, 4, (u, v) => (u + v === 1) ? hex('#3a4a6a') : BLACK[0]);
	rect(ex, 4, 0, 4, 4, (u, v) => blue(u, v, 51, 3));
	rect(ex, 8, 0, 4, 4, () => LIGHT);
	rect(ex, 12, 0, 4, 4, (u, v) => v === 0 ? WHITE : hex('#c8d8e8'));
	rect(ex, 16, 0, 8, 8, (u, v) => (u % 4 === 0 || v % 4 === 0) ? BLACK[1] : (u + v) % 3 === 0 ? hex('#5a6270') : hex('#3a404a'));
	rect(ex, 24, 0, 4, 4, () => hex('#6ad0ff'));
	rect(ex, 0, 8, 8, 8, (u, v) => (v % 4 < 2) ? blue(u, v, 52, 3) : black(u, v, 53, 1));
	rect(ex, 0, 16, 16, 8, (u, v) => {
		const d = Math.abs(v - 2.5 - (u - 8) * (u - 8) * 0.04);
		if (d < 0.6) return hex('#3a6ac8');
		if (d < 1.2) return hex('#1a2a4a');
		return BLACK[0];
	});
	save(ex, 'entity/omnitrix/xlr8_extras.png');
}

// ================================================================ DIAMONDHEAD
const DG = ['#0a3a24', '#126a40', '#1e9a6a', '#3ccf98', '#7cf0c4', '#d0fff0'].map(hex);
function facet(u, v, s, base = 2) {
	// crystal facets: diagonal bands of light and dark with sparkle points
	const a = Math.floor((u + v + Math.floor(hash(Math.floor(u / 3), Math.floor(v / 3), s) * 3)) / 2) % 3;
	const b = (u - v + 64) % 5 === 0;
	let t = base + (a === 0 ? 1 : a === 1 ? 0 : -1) + (b ? 1 : 0);
	if (hash(u, v, s + 9) < 0.05) t = 5;
	return DG[Math.max(0, Math.min(5, t))];
}
function diamondhead() {
	const im = img(64, 64);
	const SUIT = ['#08100c', '#101c16', '#1a2a22'].map(hex);
	paintBox(im, SKIN.head, (f, u, v) => {
		if (f === 'front') {
			if (v === 3 && (u === 1 || u === 2 || u === 5 || u === 6)) return hex('#022014'); // the eyes
			if (v === 4 && (u === 2 || u === 5)) return hex('#0c4a30');
			if (v === 6 && u >= 3 && u <= 4) return DG[0]; // the mouth
			return facet(u, v, 61, 3);
		}
		return facet(u, v, 62 + f.length, f === 'top' ? 4 : 2);
	});
	// the body: a black suit, green crystal at the collar and the shoulders, a pale stripe down the side
	paintBox(im, SKIN.body, (f, u, v, w) => {
		if (f === 'top') return facet(u, v, 71, 3);
		if (v < 1 || (f === 'front' && v < 3 && (u < 2 || u > 5))) return facet(u, v, 72, 3);
		if (f === 'front' && (u === 0 || u === w - 1)) return facet(u, v, 73, 2);
		if ((f === 'left' || f === 'right') && u === 1) return hex('#d8e8e0');
		return shade(SUIT[1 + (hash(u, v, 74) < 0.3 ? 1 : 0)], 0.9 + hash(u, v, 75) * 0.2);
	});
	for (const k of ['rArm', 'lArm']) paintBox(im, SKIN[k], (f, u, v, w, h) => facet(u, v, 81 + f.length + (k === 'lArm' ? 7 : 0), v > h - 4 ? 3 : 2));
	for (const k of ['rLeg', 'lLeg']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
		if (v >= h - 3) return facet(u, v, 91, 2); // crystal feet
		return shade(SUIT[1], 0.9 + hash(u, v, 92 + f.length) * 0.25);
	});
	save(im, 'entity/omnitrix/diamondhead.png');

	const ex = img(32, 32);
	rect(ex, 0, 0, 16, 16, (u, v) => [...facet(u, v, 101, 3), 215]);
	rect(ex, 16, 0, 8, 8, (u, v) => [...DG[(u + v) % 3 === 0 ? 1 : 0], 230]);
	rect(ex, 24, 0, 8, 8, () => hex('#c8fff0'));
	rect(ex, 16, 8, 8, 8, (u, v) => [...facet(u, v, 102, 4), 200]);
	save(ex, 'entity/omnitrix/diamondhead_extras.png');

	const blk = img(16, 16);
	rect(blk, 0, 0, 16, 16, (u, v) => {
		const edge = u === 0 || v === 0 || u === 15 || v === 15;
		if (edge) return [...DG[4], 230];
		const c = facet(u, v, 111, 2);
		return [...c, (u + v) % 7 === 0 ? 210 : 150];
	});
	save(blk, 'block/diamondhead_crystal.png');
}

// ================================================================ UPGRADE
function upgradeLines(u, v, w, h, f, seed) {
	// right-angled circuit traces: a spine down the middle of each face, branches every few texels, node dots
	// the spine wanders: it jogs one texel sideways every few rows and breaks now and then (less of a ladder)
	if (f === 'top' || f === 'bottom') return (u === Math.floor(w / 2) && hash(u, v, seed) < 0.6) ? 1 : 0;
	const seg = Math.floor(v / 5);
	const mid = Math.max(0, Math.min(w - 1, Math.floor(w / 2) + (hash(seg, 3, seed) < 0.5 ? -1 : 0)));
	if (v % 5 === 4 && hash(seg, 5, seed) < 0.35) return 0; // a break
	if (u === mid && v > 0) return v % 5 === 2 ? 2 : 1;
	// a short branch on one segment in two, ending in a node
	if (v % 5 === 2 && hash(seg, 7, seed) < 0.5) {
		const dir = hash(seg, 1, seed) < 0.5 ? -1 : 1;
		const len = 1 + Math.floor(hash(seg, 9, seed) * 2);
		const k = (u - mid) * dir;
		if (k > 0 && k <= len) return k === len ? 2 : 1;
	}
	return 0;
}
function upgrade() {
	const im = img(64, 64);
	const glow = img(64, 64);
	const BLK = ['#040505', '#0a0c0b', '#121614', '#1c211e'].map(hex);
	const G = ['#0e5a1c', '#1fa83a', '#38e05a', '#9cff9a', '#e0ffe0'].map(hex);
	const skin = (name, box, seed) => paintBox(im, box, (f, u, v, w, h, x, y) => {
		const l = upgradeLines(u, v, w, h, f, seed + f.length);
		if (l) {
			put(glow, x, y, l === 2 ? G[4] : G[3]);
			return l === 2 ? G[3] : G[2];
		}
		return shade(BLK[1 + (hash(u, v, seed) < 0.25 ? 1 : 0)], 0.9 + hash(u, v, seed + 1) * 0.4);
	});
	skin('body', SKIN.body, 11);
	for (const k of ['rArm', 'lArm', 'rLeg', 'lLeg']) skin(k, SKIN[k], 20 + k.length);
	// head: black, the great round green eye on the front
	paintBox(im, SKIN.head, (f, u, v, w, h, x, y) => {
		if (f === 'front') {
			const d = Math.hypot(u + 0.5 - 4, v + 0.5 - 4);
			if (d < 1.2) { put(glow, x, y, G[4]); return G[4]; }
			if (d < 2.6) { put(glow, x, y, G[3]); return G[2]; }
			if (d < 3.4) return G[0];
			return shade(BLK[1], 0.9 + hash(u, v, 31) * 0.3);
		}
		const l = upgradeLines(u, v, w, h, f, 33);
		if (l && f !== 'bottom') { put(glow, x, y, G[3]); return G[2]; }
		return shade(BLK[1], 0.9 + hash(u, v, 32 + f.length) * 0.3);
	});
	save(im, 'entity/omnitrix/upgrade.png');
	save(glow, 'entity/omnitrix/upgrade_glow.png');

	const ex = img(32, 32);
	rect(ex, 0, 0, 16, 16, (u, v) => (u === 8) ? G[2] : shade(BLK[1], 0.9 + hash(u, v, 41) * 0.4));
	disc(ex, 16, 0, 16, d => d < 0.25 ? G[4] : d < 0.7 ? G[3] : d < 0.85 ? G[1] : [0, 0, 0, 0]);
	disc(ex, 0, 16, 16, d => d > 0.92 ? [...G[1], 120] : [...shade(BLK[2], 1 + (d < 0.4 ? 0.4 : 0)), 220]);
	save(ex, 'entity/omnitrix/upgrade_extras.png');
}

// ================================================================ GHOSTFREAK
function ghostfreak() {
	const im = img(64, 64);
	const PALE = ['#8a9494', '#a8b2b2', '#c8d2d2', '#dde4e4', '#f0f4f4'].map(hex);
	const INK = ['#050506', '#121214', '#242428'].map(hex);
	const pale = (u, v, s, t = 3) => shade(PALE[t], 0.95 + hash(u, v, s) * 0.08);
	// diagonal black stripes, as on his body in the show
	const stripe = (u, v, k, slope = 0.7, gap = 4) => ((Math.floor(u * slope + v + k) % gap) + gap) % gap === 0;
	paintBox(im, SKIN.head, (f, u, v) => {
		if (f === 'front') {
			const d = Math.hypot(u + 0.5 - 4, v + 0.5 - 3.6);
			if (d < 2.4) return INK[1]; // the eye socket (the glowing eye sits on it)
			if (v === 7 && u >= 1 && u <= 6) return INK[0]; // the jagged mouth
			if (v === 6 && (u === 2 || u === 5)) return INK[0];
			if ((u === 0 || u === 7) && v < 6) return INK[1]; // stripes framing the face
			return pale(u, v, 61, 4);
		}
		if (f === 'top') return (u === 2 || u === 5) ? INK[1] : pale(u, v, 62, 4);
		return stripe(u, v, 1, 0.5, 3) ? INK[1] : pale(u, v, 63 + f.length, 3);
	});
	paintBox(im, SKIN.body, (f, u, v) => {
		if (f === 'top' || f === 'bottom') return pale(u, v, 71, 3);
		if (f === 'front' && u >= 3 && u <= 4 && v > 8) return INK[1];
		return stripe(f === 'front' ? Math.abs(u - 3.5) * 2 : u, v, f.length, 0.8, 4) ? INK[1] : pale(u, v, 72 + f.length, f === 'back' ? 2 : 3);
	});
	for (const k of ['rArm', 'lArm']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
		if (v >= h - 3) return shade(INK[2], 1.2 + hash(u, v, 81) * 0.4); // dark hands
		return stripe(u, v, 3, 0.6, 4) ? INK[1] : pale(u, v, 82 + f.length, 3);
	});
	for (const k of ['rLeg', 'lLeg']) paintBox(im, SKIN[k], (f, u, v) => pale(u, v, 91, 2));
	save(im, 'entity/omnitrix/ghostfreak.png');

	const ex = img(32, 32);
	// the tail: pale wisps fading out with dark bands (rows step down the sheet per segment)
	rect(ex, 0, 0, 16, 16, (u, v) => {
		const band = (v + Math.floor(hash(u, 0, 7) * 2)) % 4 === 0;
		const a = 230 - v * 6 - Math.floor(hash(u, v, 9) * 40);
		return band ? [...INK[1], Math.max(80, a)] : [...pale(u, v, 101, 3), Math.max(60, a)];
	});
	disc(ex, 16, 0, 16, d => d < 0.18 ? hex('#ffffff') : d < 0.4 ? hex('#2a0848') : d < 0.78 ? hex('#a24cff') : d < 0.9 ? hex('#5a18a8') : [0, 0, 0, 0]);
	rect(ex, 0, 16, 8, 8, (u, v) => v < 2 ? hex('#3a3a40') : INK[0]);
	save(ex, 'entity/omnitrix/ghostfreak_extras.png');
}

xlr8();
diamondhead();
upgrade();
ghostfreak();
console.log('omnitrix wave A textures written');
