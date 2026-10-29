// v0.17.1 Omnitrix, second batch of aliens: Stinkfly, Wildvine, Ripjaws and Grey Matter -- all procedural, "very basic models
// for now" (the user): a 64x64 WIDE player skin each plus a 64x64 extras sheet whose rectangles the client kits map onto their
// extra cubes with AlienParts.box (one UV rectangle on every face of a cube).
//   * entity/omnitrix/<alien>.png          the skin
//   * entity/omnitrix/<alien>_extras.png   wings / eye stalks / tail (Stinkfly), petals / centre / vines / leaves (Wildvine),
//                                          jaw / teeth / fins / tail (Ripjaws), the big eyes (Grey Matter)
// The extras rectangles are listed next to each painter (pixels on the 64x64 sheet) and mirrored as constants in the client kits.
// Usage: node scratchpad/gen_omnitrix_aliens_b_textures.js [previewDir]
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
		const s = 8;
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
const OGREEN = ['#0e3a12', '#1f7a22', '#39c43a', '#7cff6a', '#d8ffcf'].map(hex);
const OBLACK = ['#0a0c0b', '#16191a', '#24292a', '#383e40'].map(hex);
function hourglass(im, x0, y0, s) {
	const r = s / 2;
	for (let y = 0; y < s; y++) for (let x = 0; x < s; x++) {
		const dx = x + 0.5 - r, dy = y + 0.5 - r;
		const d = Math.sqrt(dx * dx + dy * dy);
		if (d > r) continue;
		let c = d > r - 1 ? OBLACK[2] : OGREEN[d < r * 0.45 ? 4 : 3];
		if (d < r - 1 && Math.abs(dy) < Math.abs(dx) * 0.85 + 0.2 && Math.abs(dx) > 0.6) c = OBLACK[0];
		put(im, x0 + x, y0 + y, c);
	}
}
// a generic muscled-skin tone for a face: darker at the edges, noise
function tone(ramp, u, v, w, h, seed, f) {
	const n = hash(u, v, seed);
	let t = 2;
	if (f === 'right' || f === 'left' || f === 'back') t = 1;
	if (f === 'bottom') t = 0;
	if (f === 'top') t = 3;
	if ((u === 0 || u === w - 1) && f !== 'top' && f !== 'bottom') t -= 1;
	return shade(ramp[Math.max(0, Math.min(ramp.length - 1, t))], 0.93 + n * 0.14);
}

// ================================================================ STINKFLY
// extras: wing 0,0 32x32 (translucent) | stalk 32,0 4x8 | eye 36,0 8x8 | tail 32,8 8x8 | stinger 40,8 4x4 | limb 44,0 4x16 |
//         mandible 48,0 4x4 | belly 48,4 8x8
function stinkfly() {
	const im = img(64, 64);
	const GREEN = ['#2a4a0e', '#3e6a16', '#5a8e22', '#7aae32', '#a0cc4a'].map(hex);
	const YEL = ['#8a8a1a', '#b8b82a', '#d8d848', '#eeee80'].map(hex);
	const DARK = hex('#1a2a08');
	paintBox(im, SKIN.head, (f, u, v, w, h) => {
		if (f === 'front') {
			if (v === 6 && (u === 2 || u === 5)) return DARK; // the mandible roots
			if (v === 7 && (u === 2 || u === 3 || u === 4 || u === 5)) return hex('#203008');
			if (v === 5 && u >= 3 && u <= 4) return YEL[1];
			if (v >= 2 && v <= 3 && (u === 1 || u === 6)) return hex('#e88a20'); // two small lower eyes
		}
		if (f === 'top' && (u + v) % 3 === 0) return GREEN[2];
		return tone(GREEN, u, v, w, h, 3 + f.length, f);
	});
	paintBox(im, SKIN.body, (f, u, v, w, h) => {
		if (f === 'front') {
			if (u >= 2 && u <= 5 && v >= 7) return v % 2 === 0 ? YEL[1] : YEL[2]; // the banded belly
			if (u === 0 || u === 7) return GREEN[1];
		}
		if (f === 'back' && v % 3 === 0) return GREEN[1];
		return tone(GREEN, u, v, w, h, 11 + f.length, f);
	});
	hourglass(im, SKIN.body.front[0] + 1, SKIN.body.front[1] + 1, 6);
	for (const k of ['rArm', 'lArm', 'rLeg', 'lLeg']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
		if (v % 4 === 3 && f !== 'top' && f !== 'bottom') return GREEN[0]; // segment joints
		if (v >= h - 2 && f !== 'top') return DARK; // the claws
		return tone(GREEN, u, v, w, h, 21 + k.length, f);
	});
	save(im, 'entity/omnitrix/stinkfly.png');

	const ex = img(64, 64);
	// the wing: pale green glass with dark veins, more opaque at the root (u = 0)
	rect(ex, 0, 0, 32, 32, (u, v) => {
		const vein = (v % 8 === 0) || (Math.abs(u - v * 0.6) < 0.6) || (u % 11 === 0 && v > 6);
		const edge = u === 31 || v === 0 || v === 31;
		if (vein || edge) return [...hex('#4a6a1a'), 230];
		const a = Math.round(150 - u * 2.5 + hash(u, v, 3) * 20);
		return [...mixc(hex('#e0ffd0'), hex('#a8e8ff'), u / 32), Math.max(70, a)];
	});
	rect(ex, 32, 0, 4, 8, (u, v) => tone(GREEN, u, v, 4, 8, 5, u === 0 ? 'right' : 'front'));
	rect(ex, 36, 0, 8, 8, (u, v) => {
		const d = Math.hypot(u - 3.5, v - 3.5);
		if (d < 1.5) return hex('#101008');
		if (d < 2.0 && u < 3 && v < 3) return hex('#fffff0');
		return mixc(hex('#ffb020'), hex('#e05a10'), d / 5);
	});
	rect(ex, 32, 8, 8, 8, (u, v) => (v % 3 === 0) ? GREEN[1] : (u === 0 || u === 7) ? GREEN[2] : YEL[1]);
	rect(ex, 40, 8, 4, 4, (u, v) => mixc(hex('#3a3a10'), hex('#101008'), v / 4));
	rect(ex, 44, 0, 4, 16, (u, v) => v % 5 === 4 ? GREEN[0] : tone(GREEN, u, v, 4, 16, 7, u === 0 ? 'right' : 'front'));
	rect(ex, 48, 0, 4, 4, () => hex('#203008'));
	rect(ex, 48, 4, 8, 8, (u, v) => v % 2 === 0 ? YEL[1] : YEL[2]);
	save(ex, 'entity/omnitrix/stinkfly_extras.png');
}

// ================================================================ WILDVINE
// extras: petal 0,0 16x16 | centre 16,0 16x16 (yellow, two eyes) | vine 32,0 8x8 | leaf 40,0 8x8 | bush 0,16 32x32 (leaves)
function wildvine() {
	const im = img(64, 64);
	const GREEN = ['#1e4a10', '#2e6a18', '#3e8a22', '#5aa830', '#86c850'].map(hex);
	const PINK = ['#8a2a5a', '#b83a7a', '#e05a9a', '#f088c0', '#fab8dc'].map(hex);
	const YEL = ['#c89a18', '#e8c030', '#f8e070'].map(hex);
	paintBox(im, SKIN.head, (f, u, v, w, h) => {
		// the bulb: pink petals wrapping the head, a yellow centre on the face with two eyes
		if (f === 'front') {
			const d = Math.hypot(u - 3.5, v - 4);
			if (d < 3.2) {
				if (v === 3 && (u === 2 || u === 5)) return hex('#1a3a0a');
				if (v === 4 && (u === 2 || u === 5)) return hex('#0a1a04');
				if (v === 6 && u >= 3 && u <= 4) return YEL[0];
				return YEL[d < 2 ? 2 : 1];
			}
		}
		const petal = Math.floor(u / 2 + (f === 'top' ? v / 2 : 0)) % 2;
		const t = f === 'bottom' ? 0 : f === 'top' ? 3 : 2 + petal - (v > 6 ? 1 : 0);
		return shade(PINK[Math.max(0, Math.min(4, t))], 0.94 + hash(u, v, 5 + f.length) * 0.12);
	});
	// the hat: a ring of petal tips around the bulb (cut out between petals)
	paintBox(im, SKIN.hat, (f, u, v, w, h) => {
		if (f === 'bottom' || f === 'front') return null;
		if (f === 'top') return (u + v) % 3 === 0 ? [...PINK[4], 255] : null;
		return (v < 3 && u % 3 !== 1) ? [...PINK[3], 255] : null;
	});
	paintBox(im, SKIN.body, (f, u, v, w, h) => {
		if (f === 'front' && u >= 2 && u <= 5 && v >= 5) return shade(GREEN[4], 0.95 + hash(u, v, 2) * 0.1); // the pale belly
		if ((u + v) % 5 === 0 && f !== 'top') return GREEN[1]; // vine twists
		return tone(GREEN, u, v, w, h, 13 + f.length, f);
	});
	hourglass(im, SKIN.body.front[0] + 1, SKIN.body.front[1] + 1, 6);
	for (const k of ['rArm', 'lArm', 'rLeg', 'lLeg']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
		if ((u + v + (k.length)) % 4 === 0 && f !== 'top' && f !== 'bottom') return GREEN[1]; // the spiral of a vine
		if (v >= h - 2 && f !== 'top') return GREEN[0]; // root-like hands and feet
		return tone(GREEN, u, v, w, h, 31 + k.length, f);
	});
	save(im, 'entity/omnitrix/wildvine.png');

	const ex = img(64, 64);
	rect(ex, 0, 0, 16, 16, (u, v) => {
		const t = 1 - v / 16;
		if (Math.abs(u - 7.5) < 0.7) return PINK[1]; // the midrib
		return shade(mixc(PINK[2], PINK[4], t), 0.95 + hash(u, v, 1) * 0.1);
	});
	rect(ex, 16, 0, 16, 16, (u, v) => {
		if ((v === 6 || v === 7) && (u === 4 || u === 5 || u === 10 || u === 11)) return hex('#0a1a04'); // the two eyes
		if (v === 5 && (u === 4 || u === 10)) return hex('#ffffff');
		if (v === 11 && u >= 6 && u <= 9) return YEL[0];
		const d = Math.hypot(u - 7.5, v - 7.5);
		return shade(YEL[d < 5 ? 2 : 1], 0.95 + hash(u, v, 2) * 0.1);
	});
	rect(ex, 32, 0, 8, 8, (u, v) => (u + v) % 4 === 0 ? GREEN[1] : tone(GREEN, u, v, 8, 8, 9, 'front'));
	rect(ex, 40, 0, 8, 8, (u, v) => {
		const inLeaf = Math.abs(u - 3.5) < 3.8 - Math.abs(v - 3.5) * 0.45;
		if (!inLeaf) return null;
		return Math.abs(u - 3.5) < 0.6 ? GREEN[2] : GREEN[3];
	});
	rect(ex, 0, 16, 32, 32, (u, v) => {
		const n = hash(u, v, 11);
		if (n < 0.08) return null; // gaps between the leaves
		const leaf = hash(Math.floor(u / 3), Math.floor(v / 3), 12);
		const c = leaf < 0.33 ? GREEN[2] : leaf < 0.66 ? GREEN[3] : GREEN[1];
		if (hash(u, v, 13) < 0.03) return PINK[3]; // a few blossoms
		return shade(c, 0.85 + n * 0.3);
	});
	save(ex, 'entity/omnitrix/wildvine_extras.png');
}

// ================================================================ RIPJAWS
// extras: jaw 0,0 16x8 | teeth 16,0 8x4 | fin 24,0 16x16 (part translucent) | tail 0,8 16x8 (scales) | tailfin 40,0 16x16 |
//         gill 0,16 8x8 | eye 16,4 4x4
function ripjaws() {
	const im = img(64, 64);
	const BLUE = ['#2e3e48', '#46606e', '#5e7e8c', '#7a98a4', '#9cb6c0'].map(hex);
	const PALE = ['#b8b0a0', '#d6cfc0', '#ece6da'].map(hex);
	const DARK = ['#101418', '#1c2228', '#2a3038'].map(hex);
	paintBox(im, SKIN.head, (f, u, v, w, h) => {
		if (f === 'front') {
			if (v === 1 && (u === 1 || u === 6)) return hex('#f0d040'); // the small eyes high up
			if (v === 2 && (u === 1 || u === 6)) return hex('#806010');
			if (v >= 4) return v === 4 ? DARK[0] : PALE[1]; // the jawline (the jaw itself is an extra cube)
			if (v === 3 && u >= 1 && u <= 6) return BLUE[1];
		}
		if ((f === 'right' || f === 'left') && v >= 4 && v <= 6 && u % 2 === 1) return hex('#8a2a2a'); // the gills
		if (f === 'top' && (u === 3 || u === 4)) return BLUE[1]; // the dorsal ridge
		return tone(BLUE, u, v, w, h, 3 + f.length, f);
	});
	paintBox(im, SKIN.body, (f, u, v, w, h) => {
		if (v >= 10) return DARK[1]; // the trunks
		if (f === 'front' && u >= 2 && u <= 5) return shade(PALE[v % 3 === 0 ? 0 : 1], 0.95 + hash(u, v, 2) * 0.1); // the pale belly
		if ((f === 'right' || f === 'left') && v >= 2 && v <= 6 && u === 1) return hex('#8a2a2a');
		return tone(BLUE, u, v, w, h, 11 + f.length, f);
	});
	hourglass(im, SKIN.body.front[0] + 1, SKIN.body.front[1] + 1, 6);
	for (const k of ['rArm', 'lArm']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
		if (v >= h - 3 && f !== 'top') return (u + v) % 2 === 0 ? BLUE[2] : BLUE[1]; // webbed hands
		if (f === 'front' && u >= 1 && u <= 2) return PALE[0];
		return tone(BLUE, u, v, w, h, 21 + k.length, f);
	});
	for (const k of ['rLeg', 'lLeg']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
		if (v < 3) return DARK[1];
		if (v >= h - 2) return BLUE[0];
		return tone(BLUE, u, v, w, h, 41 + k.length, f);
	});
	save(im, 'entity/omnitrix/ripjaws.png');

	const ex = img(64, 64);
	rect(ex, 0, 0, 16, 8, (u, v) => v < 3 ? tone(BLUE, u, v, 16, 8, 2, 'front') : shade(PALE[1], 0.95 + hash(u, v, 3) * 0.1));
	rect(ex, 16, 0, 8, 4, (u, v) => (u % 2 === 0 || v === 0) ? hex('#f4f2ea') : hex('#b8b4a8'));
	rect(ex, 16, 4, 4, 4, () => hex('#f0d040'));
	rect(ex, 24, 0, 16, 16, (u, v) => {
		const ray = u % 3 === 0;
		return [...(ray ? BLUE[1] : mixc(BLUE[3], hex('#c8e8f4'), v / 16)), ray ? 240 : 190];
	});
	rect(ex, 0, 8, 16, 8, (u, v) => {
		const scale = ((u + (v % 2) * 2) % 4 === 0) || v % 2 === 0 && u % 4 === 2;
		return scale ? BLUE[1] : tone(BLUE, u, v, 16, 8, 6, v > 5 ? 'right' : 'front');
	});
	rect(ex, 40, 0, 16, 16, (u, v) => {
		const ray = (u + Math.floor(v / 4)) % 3 === 0;
		return [...(ray ? BLUE[1] : mixc(BLUE[2], hex('#b8dce8'), u / 16)), 230];
	});
	rect(ex, 0, 16, 8, 8, (u, v) => u % 2 === 0 ? hex('#6a1a1a') : hex('#a83a3a'));
	save(ex, 'entity/omnitrix/ripjaws_extras.png');
}

// ================================================================ GREY MATTER
// extras: eye 0,0 8x8 (black, glossy)
function greyMatter() {
	const im = img(64, 64);
	const GREY = ['#4a4e54', '#62676e', '#7e848c', '#9aa0a8', '#b6bcc4'].map(hex);
	const BLACK = hex('#07080a');
	paintBox(im, SKIN.head, (f, u, v, w, h) => {
		if (f === 'front') {
			// two huge black eyes over most of the face, a wide frog mouth
			const eyeL = Math.hypot(u - 1.8, v - 3.2) < 1.9;
			const eyeR = Math.hypot(u - 5.2, v - 3.2) < 1.9;
			if (eyeL || eyeR) {
				if ((eyeL && u === 1 && v === 2) || (eyeR && u === 4 && v === 2)) return hex('#e8f0ff'); // the shine
				return BLACK;
			}
			if (v === 6 && u >= 1 && u <= 6) return GREY[0];
			if (v === 7) return GREY[2];
		}
		if (f === 'top' && (u === 2 || u === 5) && v > 1 && v < 6) return GREY[2]; // brain ridges
		return tone(GREY, u, v, w, h, 3 + f.length, f === 'back' || f === 'left' || f === 'right' ? 'top' : f); // the big head stays light all round

	});
	paintBox(im, SKIN.body, (f, u, v, w, h) => {
		if (f === 'front') {
			if (u >= 2 && u <= 5 && v >= 7) return GREY[4]; // the pale belly
		}
		return tone(GREY, u, v, w, h, 11 + f.length, f);
	});
	hourglass(im, SKIN.body.front[0] + 1, SKIN.body.front[1] + 1, 6);
	for (const k of ['rArm', 'lArm', 'rLeg', 'lLeg']) paintBox(im, SKIN[k], (f, u, v, w, h) => {
		if (v >= h - 2 && f !== 'top') return GREY[1]; // three-fingered hands, flat frog feet
		return tone(GREY, u, v, w, h, 21 + k.length, f);
	});
	save(im, 'entity/omnitrix/grey_matter.png');

	const ex = img(64, 64);
	rect(ex, 0, 0, 8, 8, (u, v) => (u === 1 || u === 2) && (v === 1 || v === 2) ? hex('#e8f0ff') : BLACK);
	rect(ex, 8, 0, 8, 8, (u, v) => tone(GREY, u, v, 8, 8, 9, 'front'));
	save(ex, 'entity/omnitrix/grey_matter_extras.png');
}

// v0.17.1: Stinkfly has his own GeckoLib body now (scratchpad/stinkfly_v0171/gen_stinkfly.js writes stinkfly.png) -- do not
// overwrite it with the old player-model skin
// stinkfly();
wildvine();
ripjaws();
greyMatter();
console.log('omnitrix aliens (batch b) textures written');
