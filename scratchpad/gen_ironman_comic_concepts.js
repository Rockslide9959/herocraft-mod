// Iron Man comic-armour CONCEPTS, skin-based (planning art for the Iron Man rework; nothing here touches src/).
//
// Every armour is a 64x64 player-skin-layout texture (base layer + outer layer for depth) plus a glowmask, rendered on
// the player model with the in-game armour layering (base cubes inflated ~0.25, outer layer ~0.5 / helmet 0.75):
//   mark_1     the CURRENT in-game Mark 1 (assets/projecthero/textures/armor/mark_1.png), unchanged
//   mark_1_v2  the user's gold Mark 1 skin (NovaSkin "Classic Iron Man MK1 (Gold Version)"), cleaned up: stray pixels
//              removed, shading smoothed onto one polished-gold ramp, plus a generated OUTER layer with armour
//              detailing (faceplate frame, chest-plate rim, shoulder plates, gauntlet + boot cuffs, knee plates, rivets)
//   mark_2     "Classic Iron Man" by Zukkito (NovaSkin), the user's pick, with the faceplate made slightly smaller
//   mark_iii, mark_4 (Hydro), mark_v (Stealth), mark_6 (Silver Centurion), mark_vii (Space), mark_8 (Hulkbuster):
//              variants of that Mark 2: same layout and faceplate, recoloured per suit with suit details painted in;
//              the Hulkbuster wears its skin on a bulky ~3-block mech rig.
//
// Usage (repo root or worktree):  node scratchpad/gen_ironman_comic_concepts.js [--out <dir>] [--only mark_2,mark_8,lineup]
// Inputs: <main repo>/scratchpad/ironman_refs/{classic_iron_man_by_zukkito,mark_1_v2_user_skin}.png and the in-game
// mark_1 texture. Outputs: <out>/skins/<id>.png + <id>_glowmask.png, <out>/NN_<name>.png sheets, <out>/00_lineup.png.
// Default <out>: <main repo>/scratchpad/concepts_ironman.
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('./lib_concept_models.js');
const pk = require('./pngkit.js');
const { Model, clamp, mix, mul } = L;

const args = process.argv.slice(2);
function mainRoot() {
	const d = __dirname.split(path.sep).join('/');
	const i = d.indexOf('/.claude/worktrees/');
	return i >= 0 ? d.slice(0, i) : path.join(__dirname, '..');
}
const ROOT = mainRoot();
const oa = args.indexOf('--out');
const OUT = oa >= 0 ? args[oa + 1] : path.join(ROOT, 'scratchpad', 'concepts_ironman');
const REFS = path.join(ROOT, 'scratchpad', 'ironman_refs');
const ASSETS = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'armor');
const oo = args.indexOf('--only');
const ONLY = oo >= 0 ? args[oo + 1].split(',') : null;
const WANT = k => !ONLY || ONLY.includes(k);

// ================================================================ skin layout
const PARTS = {
	head: { U: 0, V: 0, w: 8, h: 8, d: 8 }, hat: { U: 32, V: 0, w: 8, h: 8, d: 8 },
	body: { U: 16, V: 16, w: 8, h: 12, d: 4 }, jacket: { U: 16, V: 32, w: 8, h: 12, d: 4 },
	rarm: { U: 40, V: 16, w: 4, h: 12, d: 4 }, rsleeve: { U: 40, V: 32, w: 4, h: 12, d: 4 },
	larm: { U: 32, V: 48, w: 4, h: 12, d: 4 }, lsleeve: { U: 48, V: 48, w: 4, h: 12, d: 4 },
	rleg: { U: 0, V: 16, w: 4, h: 12, d: 4 }, rpants: { U: 0, V: 32, w: 4, h: 12, d: 4 },
	lleg: { U: 16, V: 48, w: 4, h: 12, d: 4 }, lpants: { U: 0, V: 48, w: 4, h: 12, d: 4 },
};
const OUTER = { head: 'hat', body: 'jacket', rarm: 'rsleeve', larm: 'lsleeve', rleg: 'rpants', lleg: 'lpants' };
const SKFACES = ['front', 'back', 'right', 'left', 'top', 'bottom'];
function rect(P, f) {
	if (typeof P === 'string') P = PARTS[P];
	const { U, V, w, h, d } = P;
	switch (f) {
		case 'front': return [U + d, V + d, w, h];
		case 'back': return [U + 2 * d + w, V + d, w, h];
		case 'right': return [U, V + d, d, h];   // the wearer's right side of the part (json -X, 'west')
		case 'left': return [U + d + w, V + d, d, h];
		case 'top': return [U + d, V, w, d];
		default: return [U + d + w, V, w, d];      // bottom
	}
}
const LIBFACE = { north: 'front', south: 'back', west: 'right', east: 'left', up: 'top', down: 'bottom' };

class Skin {
	constructor(px) { this.px = px ? Buffer.from(px) : Buffer.alloc(64 * 64 * 4); this.glow = Buffer.alloc(64 * 64 * 4); }
	static load(file) { const im = pk.decode(file); if (im.w !== 64 || im.h !== 64) throw new Error(file + ' is not 64x64'); return new Skin(im.px); }
	clone() { const s = new Skin(this.px); s.glow = Buffer.from(this.glow); return s; }
	i(x, y) { return (y * 64 + x) * 4; }
	get(x, y) { const i = this.i(x, y); return [this.px[i], this.px[i + 1], this.px[i + 2], this.px[i + 3]]; }
	set(x, y, c, a = 255) { const i = this.i(x, y); this.px[i] = clamp(Math.round(c[0])); this.px[i + 1] = clamp(Math.round(c[1])); this.px[i + 2] = clamp(Math.round(c[2])); this.px[i + 3] = a; }
	clear(x, y) { const i = this.i(x, y); this.px[i] = this.px[i + 1] = this.px[i + 2] = this.px[i + 3] = 0; }
	opaque(x, y) { return this.px[this.i(x, y) + 3] >= 128; }
	setGlow(x, y, c) { const i = this.i(x, y); this.glow[i] = clamp(Math.round(c[0])); this.glow[i + 1] = clamp(Math.round(c[1])); this.glow[i + 2] = clamp(Math.round(c[2])); this.glow[i + 3] = 255; }
	clearGlow(x, y) { const i = this.i(x, y); this.glow[i] = this.glow[i + 1] = this.glow[i + 2] = this.glow[i + 3] = 0; }
	// face-local access: part, face, local x, y
	at(part, f, x, y) { const r = rect(part, f); return [r[0] + x, r[1] + y]; }
	fget(part, f, x, y) { const [X, Y] = this.at(part, f, x, y); return this.get(X, Y); }
	fset(part, f, x, y, c, a = 255) { const r = rect(part, f); if (x < 0 || y < 0 || x >= r[2] || y >= r[3]) return; this.set(r[0] + x, r[1] + y, c, a); }
	fglow(part, f, x, y, c) { const r = rect(part, f); if (x < 0 || y < 0 || x >= r[2] || y >= r[3]) return; this.setGlow(r[0] + x, r[1] + y, c); }
	fclear(part, f, x, y) { const r = rect(part, f); this.clear(r[0] + x, r[1] + y); this.clearGlow(r[0] + x, r[1] + y); }
	save(file) {
		fs.mkdirSync(path.dirname(file), { recursive: true });
		fs.writeFileSync(file, L.encodePng(64, 64, this.px));
		fs.writeFileSync(file.replace(/\.png$/, '_glowmask.png'), L.encodePng(64, 64, this.glow));
	}
}
// which pixels belong to any face of any part
const OWNER = (() => {
	const m = new Array(64 * 64).fill(null);
	for (const [name, P] of Object.entries(PARTS)) for (const f of SKFACES) { const [x0, y0, w, h] = rect(P, f); for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) m[y * 64 + x] = { part: name, f, x: x - x0, y: y - y0, w, h }; }
	return m;
})();
function stripOutside(sk) { for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) if (!OWNER[y * 64 + x]) { sk.clear(x, y); sk.clearGlow(x, y); } }
const lum = c => 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2];

// ================================================================ colour ramps
function ramp(stops) { // stops light -> dark; t in 0..1
	return t => { t = clamp(t, 0, 1) * (stops.length - 1); const i = Math.min(stops.length - 2, Math.floor(t)); return mix(stops[i], stops[i + 1], t - i); };
}
// Zukkito Mark 2 families (by hue) and their luminance span
function family(c) {
	const [r, g, b] = c;
	if (r > 180 && g > 180 && b > 180) return 'glow';            // white eye / emitter pixels
	if (b > 170 && g > 170) return 'glow';                        // pale cyan
	if (g > 95 && r > 120 && g > 0.5 * r && b < 0.75 * g) return 'gold';
	return 'red';
}
const SPAN = { red: [39, 125], gold: [118, 218] };
const level = (c, fam) => 1 - clamp((lum(c) - SPAN[fam][0]) / (SPAN[fam][1] - SPAN[fam][0]), 0, 1); // 0 light .. 1 dark

// recolour every opaque pixel: fn(info) -> ramp function or null (keep)
function recolour(sk, fn) {
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const o = OWNER[y * 64 + x]; if (!o || !sk.opaque(x, y)) continue;
		const c = sk.get(x, y); const fam = family(c);
		const info = Object.assign({ fam, t: fam === 'glow' ? 0 : level(c, fam), c, X: x, Y: y }, o);
		const R = fn(info);
		if (R) sk.set(x, y, typeof R === 'function' ? R(info.t) : R);
	}
}
// ================================================================ pixel-art stencils
// draw an ASCII stencil onto a face: legend maps chars to colours (or functions (x,y)->colour); '.' = skip, ' ' = skip
function stencil(sk, part, f, x0, y0, rows, legend, glowLegend = {}) {
	rows.forEach((row, j) => [...row].forEach((ch, i) => {
		if (ch === '.' || ch === ' ') return;
		if (ch === '_') { sk.fclear(part, f, x0 + i, y0 + j); return; }
		const c = legend[ch]; if (!c) return;
		sk.fset(part, f, x0 + i, y0 + j, typeof c === 'function' ? c(x0 + i, y0 + j) : c);
		if (glowLegend[ch]) sk.fglow(part, f, x0 + i, y0 + j, glowLegend[ch]);
	}));
}
function eachFace(parts, faces, fn) { for (const p of parts) for (const f of faces) { const [, , w, h] = rect(p, f); for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) fn(p, f, x, y, w, h); } }
const SIDES = ['front', 'back', 'right', 'left'];
// glow every pixel that is pale cyan / white (eyes, emitters) on the base or outer layer
// glow positions = the Mark 2's own white / pale-cyan pixels (eyes, unibeam, boot jets), fixed before any recolour so
// light silver / white ramps of the variants never count as glow
let GLOWPOS = null;
const isGlowAt = (sk, part, f, x, y) => { const [X, Y] = sk.at(part, f, x, y); return GLOWPOS.has(Y * 64 + X); };
function autoGlow(sk, k = 1, tint = null) {
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		if (!OWNER[y * 64 + x] || !sk.opaque(x, y)) continue;
		const c = sk.get(x, y);
		if (GLOWPOS ? GLOWPOS.has(y * 64 + x) : family(c) === 'glow') sk.setGlow(x, y, mul(tint ? mix(c, tint, 0.5) : c, k));
	}
}

// ================================================================ MARK 2 base (Zukkito) + faceplate trim
function loadMark2() {
	const sk = Skin.load(path.join(REFS, 'classic_iron_man_by_zukkito.png'));
	stripOutside(sk);
	// the artist's palette swatches (55,47,61) / (99,73,82) left in the face wrap -> neighbouring helmet colour
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) { const c = sk.get(x, y); if (c[3] > 0 && ((c[0] === 55 && c[1] === 47) || (c[0] === 99 && c[1] === 73))) sk.set(x, y, sk.get(x < 12 ? x - 2 : x + 2, y)); }
	// FACEPLATE SLIGHTLY SMALLER: the gold mask wrapped 2 px round each side of the head; those columns become helmet
	// red again (copied from the helmet just behind them), and the top / chin corners are trimmed to a trapezoid.
	for (let y = 8; y < 16; y++) {
		for (const x of [6, 7]) sk.set(x, y, sk.get(x - 3, y));
		for (const x of [16, 17]) sk.set(x, y, sk.get(x + 3, y));
	}
	// new faceplate outline on the 8x8 face: '#' keeps the gold plate, '.' becomes helmet red, 'o' is the dark plate edge
	const MASK = [
		'oooooooo',
		'o##oo##o',
		'o######o',
		'o######o',
		'o######o',
		'o######o',
		'.o####o.',
		'..o##o..'];
	const RED = [[209, 39, 67], [165, 39, 23], [112, 26, 51]];
	for (let j = 0; j < 8; j++) for (let i = 0; i < 8; i++) {
		const ch = MASK[j][i]; if (ch === '#') continue;
		const x = 8 + i, y = 8 + j;
		if (ch === 'o') { sk.set(x, y, j === 0 ? RED[0] : RED[2]); continue; }
		sk.set(x, y, RED[(i + j) % 2 ? 1 : 0]);
	}
	// the helmet's widow's peak: a red point coming down the forehead onto the plate
	sk.set(11, 9, RED[0]); sk.set(12, 9, RED[0]);
	// a darker frame line where the faceplate meets the helmet sides reads as the plate edge
	for (let y = 9; y < 14; y++) { sk.set(7, y, mix(sk.get(7, y), [40, 10, 14], 0.45)); sk.set(16, y, mix(sk.get(16, y), [40, 10, 14], 0.45)); }
	return sk;
}
function mark2Glow(sk, k = 1) {
	autoGlow(sk, k);
	// palm repulsors (arm undersides) and boot jets (soles)
	for (const p of ['rarm', 'larm']) for (const [x, y] of [[1, 1], [2, 1], [1, 2], [2, 2]]) { sk.fset(p, 'bottom', x, y, [214, 240, 246]); sk.fglow(p, 'bottom', x, y, mul([190, 236, 255], k)); }
	for (const p of ['rleg', 'lleg']) for (const [x, y] of [[1, 1], [2, 1], [1, 2], [2, 2]]) sk.fglow(p, 'bottom', x, y, mul([150, 210, 240], 0.7 * k));
}

// ================================================================ MARK 1 V2 (user's gold Mk1): clean + outer layer
function buildMark1v2() {
	const src = Skin.load(path.join(REFS, 'mark_1_v2_user_skin.png'));
	stripOutside(src);
	const sk = src.clone();
	const GOLD = ramp([[255, 240, 160], [242, 204, 86], [214, 168, 46], [170, 122, 26], [116, 78, 14], [66, 42, 8]]);
	// smooth each base face (3x3 median of luminance within the face), keep real features (dark slits / grille /
	// lights), quantise onto one polished-gold ramp with a gentle top light
	for (const part of ['head', 'body', 'rarm', 'larm', 'rleg', 'lleg']) for (const f of SKFACES) {
		const [x0, y0, w, h] = rect(part, f);
		const L0 = []; for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) L0.push(src.opaque(x0 + x, y0 + y) ? lum(src.get(x0 + x, y0 + y)) : null);
		for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
			const v = L0[y * w + x]; if (v == null) continue;
			const nb = []; for (let dy = -2; dy <= 2; dy++) for (let dx = -2; dx <= 2; dx++) { const xx = x + dx, yy = y + dy; if (xx >= 0 && yy >= 0 && xx < w && yy < h && L0[yy * w + xx] != null) nb.push(L0[yy * w + xx]); }
			nb.sort((a, b) => a - b); const med = nb[nb.length >> 1];
			const c = src.get(x0 + x, y0 + y);
			const isLight = c[0] > 230 && c[1] > 220 && c[2] > 150;
			if (isLight) { sk.set(x0 + x, y0 + y, [255, 246, 214]); sk.setGlow(x0 + x, y0 + y, [255, 236, 190]); continue; }
			if (v < 40) { sk.set(x0 + x, y0 + y, [22, 16, 8]); continue; } // slits / grille / sockets stay dark
			const topK = (f === 'top') ? -0.08 : (f === 'bottom') ? 0.12 : (y / Math.max(1, h - 1) - 0.5) * 0.14;
			const t = clamp(1 - (med - 40) / 150 + topK, 0, 1);
			sk.set(x0 + x, y0 + y, GOLD(Math.round(t * 8) / 8));
		}
	}
	// eye slits of the helmet: read as dark recessed slits, faint warm glow behind them
	{ const [x0, y0] = rect('head', 'front'); for (let x = 1; x < 7; x++) { const c = sk.get(x0 + x, y0 + 3); if (lum(c) < 60) sk.setGlow(x0 + x, y0 + 3, [120, 86, 40]); } }
	// ---- OUTER LAYER: armour detailing
	const g = ramp([[255, 244, 176], [246, 210, 96], [222, 176, 54], [180, 130, 30], [124, 84, 16], [70, 46, 10]]);
	const EDGE = g(0.18), MID = g(0.42), SH = g(0.8), RIV = [255, 230, 150];
	// helmet: faceplate frame (front border, open over the eyes and grille), brow bar, side rivets, crown ring
	stencil(sk, 'hat', 'front', 0, 0, [
		'EEEEEEEE',
		'M......M',
		'BBBBBBBB',
		'M......M',
		'M......M',
		'M......M',
		'S......S',
		'SSSSSSSS'], { E: EDGE, M: MID, B: g(0.25), S: SH });
	for (const f of ['right', 'left']) stencil(sk, 'hat', f, 0, 0, ['EEEEEEEE', '........', '........', '.R....R.', '........', '........', '.R....R.', 'SSSSSSSS'], { E: EDGE, R: RIV, S: SH });
	stencil(sk, 'hat', 'back', 0, 0, ['EEEEEEEE', '........', '........', '.R....R.', '........', '........', '.R....R.', 'SSSSSSSS'], { E: EDGE, R: RIV, S: SH });
	stencil(sk, 'hat', 'top', 0, 0, ['MMMMMMMM', 'M......M', 'M......M', 'M......M', 'M......M', 'M......M', 'M......M', 'MMMMMMMM'], { M: MID });
	// chest-plate rim around the chest light + belt band
	stencil(sk, 'jacket', 'front', 0, 0, [
		'EEEEEEEE',
		'MR....RM',
		'M......M',
		'M......M',
		'M......M',
		'MR....RM',
		'SSSSSSSS',
		'........',
		'........',
		'........',
		'EERRRREE',
		'SSSSSSSS'], { E: EDGE, M: MID, R: RIV, S: SH });
	stencil(sk, 'jacket', 'back', 0, 0, ['EEEEEEEE', 'M......M', 'M.R..R.M', 'M......M', 'M......M', 'M.R..R.M', 'SSSSSSSS', '........', '........', '........', 'EEEEEEEE', 'SSSSSSSS'], { E: EDGE, M: MID, R: RIV, S: SH });
	for (const f of ['right', 'left']) stencil(sk, 'jacket', f, 0, 0, ['EEEE', '....', '....', '....', '....', '....', '....', '....', '....', '....', 'EEEE', 'SSSS'], { E: EDGE, S: SH });
	// shoulder plates (top 4 rows of each sleeve + the top), gauntlet cuffs
	for (const p of ['rsleeve', 'lsleeve']) {
		for (const f of SIDES) stencil(sk, p, f, 0, 0, ['EEEE', 'MRRM', 'MMMM', 'SSSS', '....', '....', '....', 'EEEE', 'SRRS', '....', '....', '....'], { E: EDGE, M: MID, R: RIV, S: SH });
		stencil(sk, p, 'top', 0, 0, ['EEEE', 'MMMM', 'MMMM', 'SSSS'], { E: EDGE, M: MID, S: SH });
	}
	// knee plates (front), boot cuffs (all sides)
	for (const p of ['rpants', 'lpants']) {
		stencil(sk, p, 'front', 0, 0, ['....', '....', '....', 'EEEE', 'MRRM', 'SMMS', '.SS.', '....', 'EEEE', 'SRRS', '....', '....'], { E: EDGE, M: MID, R: RIV, S: SH });
		for (const f of ['back', 'right', 'left']) stencil(sk, p, f, 0, 0, ['....', '....', '....', '....', '....', '....', '....', '....', 'EEEE', 'SRRS', '....', '....'], { E: EDGE, R: RIV, S: SH });
	}
	return sk;
}

// ================================================================ MARK 2 variants
function emitterTri(sk, glow = [215, 245, 255], rim = [60, 50, 54], k = 1) {
	// body front rows 0..3 (the old round unibeam spot) -> a downward triangle, 6 wide
	const W = [235, 250, 255], C = [200, 236, 250];
	stencil(sk, 'body', 'front', 0, 0, ['.RRRRRR.', 'RWWWWWWR', '.RCWWCR.', '..RWWR..', '...RR...'], { R: rim, W, C });
	for (let y = 0; y < 5; y++) for (let x = 0; x < 8; x++) { const c = sk.fget('body', 'front', x, y); if (c[0] === W[0] || c[0] === C[0]) sk.fglow('body', 'front', x, y, mul(glow, k * (c[0] === W[0] ? 1 : 0.7))); }
	// the outer layer's round frame becomes a raised V-shaped triangle frame
	for (let y = 0; y < 6; y++) for (let x = 0; x < 8; x++) sk.fclear('jacket', 'front', x, y);
	stencil(sk, 'jacket', 'front', 0, 0, ['RRRRRRRR', 'R......R', '.R....R.', '..R..R..', '...RR...'], { R: mix(rim, [255, 255, 255], 0.25) });
}
function roundEmitterGlow(sk, glow, k = 1) {
	for (let y = 0; y < 4; y++) for (let x = 1; x < 7; x++) { if (isGlowAt(sk, 'body', 'front', x, y)) { sk.fset('body', 'front', x, y, mix(glow, [255, 255, 255], 0.5)); sk.fglow('body', 'front', x, y, mul(glow, k)); } }
}
function eyesGlow(sk, glow, k = 1) {
	for (let x = 0; x < 8; x++) for (let y = 1; y < 5; y++) { if (isGlowAt(sk, 'head', 'front', x, y)) { const cc = mul(mix(glow, [255, 255, 255], 0.45), Math.max(0.35, k)); sk.fset('head', 'front', x, y, cc); sk.fglow('head', 'front', x, y, mul(glow, k)); } }
}

const RAMPS = {
	red3: ramp([[250, 98, 74], [216, 44, 46], [170, 24, 32], [116, 14, 22], [66, 10, 12]]),
	gold3: ramp([[255, 240, 150], [246, 206, 84], [222, 164, 48], [176, 116, 30], [120, 76, 20]]),
	navy: ramp([[96, 128, 178], [52, 76, 124], [34, 50, 88], [22, 32, 60], [12, 18, 34]]),
	gunmetal: ramp([[176, 184, 194], [128, 136, 148], [92, 98, 110], [62, 66, 76], [36, 38, 46]]),
	yellow: ramp([[255, 236, 120], [246, 204, 40], [214, 164, 20], [160, 112, 10]]),
	black: ramp([[78, 82, 92], [52, 55, 63], [36, 38, 44], [24, 25, 30], [14, 15, 18]]),
	darkgrey: ramp([[130, 136, 148], [96, 102, 114], [70, 74, 84], [48, 51, 59], [30, 32, 38]]),
	silver: ramp([[250, 252, 255], [214, 220, 228], [172, 180, 192], [128, 136, 150], [86, 92, 106]]),
	white: ramp([[255, 255, 255], [236, 238, 242], [206, 210, 218], [160, 166, 178], [110, 116, 128]]),
	redSpace: ramp([[246, 90, 74], [214, 40, 44], [168, 22, 30], [112, 14, 20], [64, 8, 12]]),
	goldTrim: ramp([[255, 236, 150], [244, 196, 74], [212, 148, 40], [160, 104, 24], [104, 64, 14]]),
};

function buildMark3(m2) {
	const sk = m2.clone(); sk.glow.fill(0);
	recolour(sk, i => i.fam === 'red' ? RAMPS.red3 : i.fam === 'gold' ? RAMPS.gold3 : null);
	emitterTri(sk, [215, 245, 255], [196, 146, 40]);
	// segmented classic: gold ab plates with dark seams, shoulder-plate seams, gold forearm cuff lines
	const G = RAMPS.gold3, R = RAMPS.red3;
	stencil(sk, 'body', 'front', 1, 5, ['.DDDDDD.', '.GGDGGG.', '.ggDggg.', '.DDDDDD.', '.GGDGGG.', '.ggDggg.'].map(r => r.slice(1)), { D: R(0.95), G: G(0.2), g: G(0.5) });
	for (const p of ['rarm', 'larm']) for (const f of SIDES) { for (let x = 0; x < 4; x++) { sk.fset(p, f, x, 3, R(0.92)); sk.fset(p, f, x, 7, G(0.15)); sk.fset(p, f, x, 8, G(0.6)); } }
	for (const p of ['rleg', 'lleg']) for (const f of SIDES) for (let x = 0; x < 4; x++) sk.fset(p, f, x, 5, R(0.92));
	mark2Glow(sk); eyesGlow(sk, [200, 238, 255]);
	return sk;
}

function buildHydro(m2) {
	const sk = m2.clone(); sk.glow.fill(0);
	recolour(sk, i => i.fam === 'red' ? RAMPS.navy : i.fam === 'gold' ? RAMPS.gunmetal : null);
	const Y = RAMPS.yellow, N = RAMPS.navy, GM = RAMPS.gunmetal;
	// porthole ring on the helmet's outer layer around the faceplate (yellow, 4 bolts), glass tint over the face
	stencil(sk, 'hat', 'front', 0, 0, [
		'.yYYYYy.',
		'y......y',
		'Y......Y',
		'B......B',
		'Y......Y',
		'Y......Y',
		'y......y',
		'.yYBBYy.'], { Y: Y(0.15), y: Y(0.45), B: GM(0.85) });
	for (let y = 1; y < 7; y++) for (let x = 1; x < 7; x++) { const c = sk.fget('head', 'front', x, y); if (family(c) !== 'glow') sk.fset('head', 'front', x, y, mix(c, [40, 120, 140], 0.35)); }
	// twin headlamps on the helmet sides, near the front edge
	sk.fset('hat', 'right', 6, 2, [255, 248, 214]); sk.fglow('hat', 'right', 6, 2, [255, 240, 190]); sk.fset('hat', 'right', 6, 1, GM(0.7)); sk.fset('hat', 'right', 6, 3, GM(0.7));
	sk.fset('hat', 'left', 1, 2, [255, 248, 214]); sk.fglow('hat', 'left', 1, 2, [255, 240, 190]); sk.fset('hat', 'left', 1, 1, GM(0.7)); sk.fset('hat', 'left', 1, 3, GM(0.7));
	// belt with hazard chevrons, yellow shoulder bands and boot weights
	for (let x = 0; x < 8; x++) { sk.fset('body', 'front', x, 10, ((x) % 2) ? Y(0.2) : [24, 24, 28]); sk.fset('body', 'front', x, 11, ((x + 1) % 2) ? Y(0.45) : [24, 24, 28]); sk.fset('body', 'back', x, 10, Y(0.3)); sk.fset('body', 'back', x, 11, Y(0.55)); }
	for (const f of ['right', 'left']) for (let x = 0; x < 4; x++) { sk.fset('body', f, x, 10, Y(0.3)); sk.fset('body', f, x, 11, Y(0.55)); }
	for (const p of ['rsleeve', 'lsleeve']) { for (const f of SIDES) stencil(sk, p, f, 0, 0, ['YYYY', 'yyyy', 'SSSS'], { Y: Y(0.15), y: Y(0.5), S: GM(0.8) }); stencil(sk, p, 'top', 0, 0, ['YYYY', 'YBBY', 'YBBY', 'YYYY'], { Y: Y(0.2), B: GM(0.6) }); }
	for (const p of ['rpants', 'lpants']) for (const f of SIDES) stencil(sk, p, f, 0, 8, ['YYYY', 'yyyy', 'GGGG', 'gggg'], { Y: Y(0.2), y: Y(0.5), G: GM(0.45), g: GM(0.75) });
	// back: two yellow air tanks with gunmetal caps + a hose, on the outer layer
	stencil(sk, 'jacket', 'back', 0, 0, [
		'.CC..CC.',
		'.YY..YY.',
		'.Yy..Yy.',
		'.Yy..Yy.',
		'.Yy..Yy.',
		'.GG..GG.',
		'.Yy..Yy.',
		'.Yy..Yy.',
		'.yy..yy.',
		'.CC..CC.'], { C: GM(0.6), Y: Y(0.15), y: Y(0.5), G: GM(0.85) });
	// round amber sonar emitter instead of the white unibeam, teal eyes
	roundEmitterGlow(sk, [255, 196, 110]); eyesGlow(sk, [90, 220, 235]);
	mark2Glow(sk, 0.8);
	for (const p of ['rarm', 'larm']) for (const [x, y] of [[1, 1], [2, 1], [1, 2], [2, 2]]) { sk.fset(p, 'bottom', x, y, [150, 230, 240]); sk.fglow(p, 'bottom', x, y, [90, 210, 230]); }
	return sk;
}

function buildStealth(m2) {
	const sk = m2.clone(); sk.glow.fill(0);
	recolour(sk, i => i.fam === 'red' ? RAMPS.black : i.fam === 'gold' ? RAMPS.darkgrey : null);
	// blue-grey accent seams (shoulders, waist, knees)
	const A = [86, 104, 130];
	for (const p of ['rarm', 'larm']) for (const f of SIDES) for (let x = 0; x < 4; x++) sk.fset(p, f, x, 3, A);
	for (const p of ['rleg', 'lleg']) for (const f of SIDES) for (let x = 0; x < 4; x++) sk.fset(p, f, x, 5, mix(A, [20, 22, 26], 0.4));
	for (let x = 0; x < 8; x++) sk.fset('body', 'front', x, 10, A);
	eyesGlow(sk, [150, 190, 235], 0.55); roundEmitterGlow(sk, [110, 160, 225], 0.55);
	mark2Glow(sk, 0.5);
	for (const p of ['rarm', 'larm']) for (const [x, y] of [[1, 1], [2, 1], [1, 2], [2, 2]]) { sk.fset(p, 'bottom', x, y, [130, 160, 200]); sk.fglow(p, 'bottom', x, y, [90, 130, 190]); }
	return sk;
}

function buildCenturion(m2) {
	const sk = m2.clone(); sk.glow.fill(0);
	// silver where the classic is gold, red stays; the upper chest turns silver with a red V below it
	recolour(sk, i => {
		if (i.fam === 'gold') return RAMPS.silver;
		if (i.fam === 'red') {
			if ((i.part === 'body' || i.part === 'jacket') && i.f === 'front' && i.y < 5 + Math.abs(i.x - 3.5) * 0.9) return t => RAMPS.silver(t * 0.55);
			if ((i.part === 'body' || i.part === 'jacket') && (i.f === 'right' || i.f === 'left' || i.f === 'back' || i.f === 'top') && i.y < 8) return t => RAMPS.silver(t * 0.55);
			return RAMPS.red3;
		}
		return null;
	});
	emitterTri(sk, [220, 245, 255], [90, 96, 110]);
	// big flared shoulder blades on the sleeves (red, silver trim), angled up toward the outside
	const R = RAMPS.red3, S = RAMPS.silver;
	for (const [p, out] of [['rsleeve', 'right'], ['lsleeve', 'left']]) {
		stencil(sk, p, out, 0, 0, ['SSSS', 'RRRR', 'rRRr', 'sRRs', '.rr.'], { S: S(0.1), R: R(0.2), r: R(0.55), s: S(0.6) });
		for (const f of ['front', 'back']) stencil(sk, p, f, 0, 0, out === 'right' ? ['SSSS', 'RRRr', 'RRr.', 'rr..'] : ['SSSS', 'rRRR', '.rRR', '..rr'], { S: S(0.1), R: R(0.25), r: R(0.6) });
		stencil(sk, p, 'top', 0, 0, ['SSSS', 'SRRS', 'SRRS', 'SSSS'], { S: S(0.15), R: R(0.3) });
	}
	mark2Glow(sk); eyesGlow(sk, [215, 240, 255]);
	return sk;
}

function buildSpace(m2) {
	const sk = m2.clone(); sk.glow.fill(0);
	// white plating where the classic is red, red where it is gold (limbs), gold trim painted on
	recolour(sk, i => {
		if (i.fam === 'red') return t => RAMPS.white(t * 0.62);
		if (i.fam === 'gold') return (i.part === 'head' || i.part === 'hat') ? RAMPS.goldTrim : RAMPS.redSpace;
		return null;
	});
	const G = RAMPS.goldTrim, W = RAMPS.white, R = RAMPS.redSpace;
	// gold mirror visor on the helmet's outer layer, shaped like the faceplate (covers the eyes)
	stencil(sk, 'hat', 'front', 0, 0, [
		'________',
		'_VhHHHH_',
		'_hVHHHH_',
		'_VHHHHH_',
		'_HHHHHD_',
		'_HHHHDD_',
		'__HHDD__',
		'___DD___'], { g: G(0.6), H: [240, 184, 66], h: [255, 248, 210], V: [255, 226, 130], D: [168, 108, 26] });
	// the visor sits proud on the outer layer; under it the plate is gold too (no eyes)
	for (let y = 1; y < 8; y++) for (let x = 1; x < 7; x++) { const c = sk.fget('hat', 'front', x, y); if (c[3] > 0) sk.fset('head', 'front', x, y, mul(c, 0.8)); }
	for (let y = 1; y < 6; y++) for (let x = 1; x < 7; x++) sk.clearGlow(...sk.at('head', 'front', x, y));
	// red crest stripe over the helmet
	for (let y = 0; y < 8; y++) { sk.fset('head', 'top', 3, y, R(0.2)); sk.fset('head', 'top', 4, y, R(0.35)); }
	for (let y = 0; y < 8; y++) { sk.fset('head', 'back', 3, y, R(0.3)); sk.fset('head', 'back', 4, y, R(0.45)); }
	// gold trim rings: neck, belt, cuffs, knees, boot tops
	for (const f of SIDES) {
		const [, , w] = rect('body', f);
		for (let x = 0; x < w; x++) { sk.fset('body', f, x, 0, G(0.2)); sk.fset('body', f, x, 10, G(0.15)); sk.fset('body', f, x, 11, G(0.55)); }
		for (const p of ['rarm', 'larm']) for (let x = 0; x < 4; x++) { sk.fset(p, f, x, 4, G(0.2)); sk.fset(p, f, x, 8, G(0.25)); }
		for (const p of ['rleg', 'lleg']) for (let x = 0; x < 4; x++) { sk.fset(p, f, x, 5, G(0.2)); sk.fset(p, f, x, 8, G(0.25)); }
	}
	// filigree dots on the belt
	for (let x = 1; x < 8; x += 2) sk.fset('body', 'front', x, 10, G(0.0));
	// thruster backpack on the jacket back: white shell, red panel, vents, glowing nozzles at the bottom, oxygen tanks on the sides
	stencil(sk, 'jacket', 'back', 0, 0, [
		'GGGGGGGG',
		'WWWWWWWW',
		'WRRRRRRW',
		'WRrrrrRW',
		'WWWWWWWW',
		'WvvvvvvW',
		'WWWWWWWW',
		'WvvvvvvW',
		'WWWWWWWW',
		'GGGGGGGG',
		'.NN..NN.',
		'.nn..nn.'], { G: G(0.3), W: W(0.15), R: R(0.25), r: R(0.55), v: W(0.75), N: [90, 96, 108], n: [220, 240, 255] }, { n: [170, 220, 255] });
	for (const f of ['right', 'left']) stencil(sk, 'jacket', f, 0, 1, ['.RR.', '.RR.', '.rr.', '.RR.', '.RR.', '.rr.', '.RR.', '.GG.'], { R: R(0.25), r: R(0.6), G: G(0.3) });
	// boot thrusters (heel)
	for (const p of ['rpants', 'lpants']) stencil(sk, p, 'back', 0, 9, ['.NN.', '.nn.'], { N: [90, 96, 108], n: [220, 240, 255] }, { n: [170, 220, 255] });
	mark2Glow(sk); roundEmitterGlow(sk, [200, 238, 255]);
	return sk;
}

function buildHulkbusterSkin(m2) {
	const sk = buildMark3(m2);
	// heavier, mech-like: silver abs and hydraulics painted over the classic segmentation
	const S = RAMPS.silver, R = RAMPS.red3, G = RAMPS.gold3, D = [40, 40, 46];
	stencil(sk, 'body', 'front', 1, 5, ['SSDSSS', 'ssDsss', 'DDDDDD', 'SSDSSS', 'ssDsss'], { S: S(0.25), s: S(0.6), D });
	for (const p of ['rarm', 'larm']) for (const f of SIDES) stencil(sk, p, f, 0, 4, ['DPPD', 'DPPD', 'DppD'], { D, P: S(0.15), p: S(0.5) });
	for (const p of ['rarm', 'larm']) for (const f of ['front']) stencil(sk, p, f, 0, 9, ['KKKK', 'kkkk', 'GGGG'], { K: R(0.3), k: R(0.75), G: G(0.3) });
	for (const p of ['rleg', 'lleg']) for (const f of ['front', 'back']) stencil(sk, p, f, 1, 1, ['PP', 'PP', 'pp', 'DD'], { P: S(0.2), p: S(0.55), D });
	// back power pack: vents + glowing thrusters on the jacket
	stencil(sk, 'jacket', 'back', 0, 1, ['SSSSSSSS', 'SvvvvvvS', 'SSSSSSSS', 'SvvvvvvS', 'SSSSSSSS', '.NN..NN.', '.nn..nn.'], { S: S(0.4), v: D, N: [90, 96, 108], n: [210, 240, 255] }, { n: [160, 220, 255] });
	return sk;
}

// ================================================================ renderer: skin on a rig
const FX = [[168, 226, 255], [255, 150, 50], [150, 210, 255], [120, 160, 210], [110, 220, 235], [205, 240, 255]]; // fx colour slots
const FXI = { rep: 0, flame: 1, jet: 2, dim: 3, wash: 4, uni: 5 };
function skinModel(name, sk, spec) {
	const M = new Model(name, { TW: 64, D: 1 });
	M.TH = 66;
	M.tex = Buffer.alloc(64 * 66 * 4); sk.px.copy(M.tex, 0);
	M.glow = Buffer.alloc(64 * 66 * 4); sk.glow.copy(M.glow, 0);
	FX.forEach((c, k) => { const i = (64 * 64 + k) * 4; M.glow[i] = c[0]; M.glow[i + 1] = c[1]; M.glow[i + 2] = c[2]; M.glow[i + 3] = 255; const j = (65 * 64 + k) * 4; const w = mix(c, [255, 255, 255], 0.7); M.glow[j] = w[0]; M.glow[j + 1] = w[1]; M.glow[j + 2] = w[2]; M.glow[j + 3] = 255; });
	M.texel = function (r, s, t) {
		if (r.fx != null) return ((r.core ? 65 : 64) * 64 + r.fx) * 4;
		const [x0, y0, w, h] = r.rect;
		let fx = s, fy = t;
		if (r.f === 'top') { fx = 1 - s; fy = 1 - t; } else if (r.f === 'bottom') { fx = 1 - s; }
		const x = Math.min(w - 1, Math.floor(fx * w)), y = Math.min(h - 1, Math.floor(fy * h));
		return ((y0 + y) * 64 + x0 + x) * 4;
	};
	for (const [b, parent, pivot] of spec.bones) M.bone(b, parent, pivot);
	// cube with skin part uvs: every face samples its part's face rect, or a fixed rect for "mapped" extras
	M.part = (bone, a, part, infl, opts = {}) => {
		const i = Array.isArray(infl) ? infl : [infl, infl, infl, infl, infl, infl]; // -x,-y,-z,+x,+y,+z
		const c = M.box(bone, a[0] - i[0], a[1] - i[1], a[2] - i[2], a[3] + i[3], a[4] + i[4], a[5] + i[5], 'skin', opts);
		c.uv = {};
		for (const f of L.FACES) c.uv[f] = opts.map ? { rect: typeof opts.map === 'function' ? opts.map(f) : opts.map, f: 'front' } : { rect: rect(part, LIBFACE[f]), f: LIBFACE[f] };
		return c;
	};
	M.fx = (bone, a, k, core) => { const c = M.box(bone, a[0], a[1], a[2], a[3], a[4], a[5], 'fx'); c.uv = {}; for (const f of L.FACES) c.uv[f] = { fx: k, core }; return c; };
	spec.build(M);
	M.heightBlocks = spec.height || 2;
	return M;
}
const isFx = c => c.mat === 'fx';

// standard player rig with the in-game armour layering
const PLAYER = {
	bones: [['root', null, [0, 12, 0]], ['head', 'root', [0, 24, 0]], ['arm_r', 'root', [-5, 22, 0]], ['arm_l', 'root', [5, 22, 0]], ['leg_r', 'root', [-1.9, 12, 0]], ['leg_l', 'root', [1.9, 12, 0]]],
	build(M) {
		M.part('head', [-4, 24, -4, 4, 32, 4], 'head', 0.25); M.part('head', [-4, 24, -4, 4, 32, 4], 'hat', 0.75);
		M.part('root', [-4, 12, -2, 4, 24, 2], 'body', 0.25); M.part('root', [-4, 12, -2, 4, 24, 2], 'jacket', 0.5);
		M.part('arm_r', [-8, 12, -2, -4, 24, 2], 'rarm', 0.3); M.part('arm_r', [-8, 12, -2, -4, 24, 2], 'rsleeve', 0.56);
		M.part('arm_l', [4, 12, -2, 8, 24, 2], 'larm', 0.3); M.part('arm_l', [4, 12, -2, 8, 24, 2], 'lsleeve', 0.56);
		M.part('leg_r', [-4, 0, -2, 0, 12, 2], 'rleg', [0.22, 0.22, 0.22, -0.02, 0.22, 0.22]); M.part('leg_r', [-4, 0, -2, 0, 12, 2], 'rpants', [0.46, 0.46, 0.46, -0.04, 0.3, 0.46]);
		M.part('leg_l', [0, 0, -2, 4, 12, 2], 'lleg', [-0.02, 0.22, 0.22, 0.22, 0.22, 0.22]); M.part('leg_l', [0, 0, -2, 4, 12, 2], 'lpants', [-0.04, 0.46, 0.46, 0.46, 0.3, 0.46]);
		// effect geometry (only shown in the action panels)
		M.fx('arm_r', [-6.9, -16, -0.9, -5.1, 11.6, 0.9], FXI.rep, true); M.fx('arm_r', [-7.6, -16, -1.6, -4.4, 11.6, 1.6], FXI.rep);
		M.fx('root', [-0.8, 20.2, -30, 0.8, 21.8, -2.8], FXI.uni, true); M.fx('root', [-1.7, 19.4, -30, 1.7, 22.6, -2.8], FXI.uni);
		for (const [b, x] of [['leg_r', -2], ['leg_l', 2]]) { M.fx(b, [x - 0.7, -6, -0.7, x + 0.7, -0.2, 0.7], FXI.jet, true); M.fx(b, [x - 1.3, -6, -1.3, x + 1.3, -0.2, 1.3], FXI.jet); }
		M.fx('root', [-2.2, 4, 2.8, -0.8, 11.5, 3.9], FXI.jet, true); M.fx('root', [0.8, 4, 2.8, 2.2, 11.5, 3.9], FXI.jet, true);
	},
};
// bulky mech rig for the Hulkbuster: the same skin stretched over a ~3-block body, small head, huge arms and fists
const HULK = {
	height: 3,
	bones: [['root', null, [0, 17, 0]], ['head', 'root', [0, 35, -3]], ['arm_r', 'root', [-15.5, 33, 0]], ['arm_l', 'root', [15.5, 33, 0]], ['leg_r', 'root', [-5, 17, 0]], ['leg_l', 'root', [5, 17, 0]]],
	build(M) {
		const fr = (p, f, y0, y1) => { const r = rect(p, f); return [r[0], r[1] + y0, r[2], y1 - y0]; };
		// torso + hump
		M.part('root', [-11, 16.5, -6, 11, 36, 6], 'body', 0.3); M.part('root', [-11, 16.5, -6, 11, 36, 6], 'jacket', 0.9);
		M.part('root', [-8, 34, -4, 8, 39, 6.5], 'body', 0.3, { map: f => fr('body', f === 'north' ? 'front' : 'back', 0, 3) });
		// small head sunk into the hump, forward
		M.part('head', [-4, 34.5, -8, 4, 42.5, 0], 'head', 0.25); M.part('head', [-4, 34.5, -8, 4, 42.5, 0], 'hat', 0.75);
		for (const [b, s] of [['arm_r', -1], ['arm_l', 1]]) {
			const arm = s < 0 ? 'rarm' : 'larm', sl = s < 0 ? 'rsleeve' : 'lsleeve';
			const X = v => s * v;
			const a = [Math.min(X(11), X(20.5)), 9, -5, Math.max(X(11), X(20.5)), 34, 5];
			M.part(b, a, arm, 0.3); M.part(b, a, sl, 0.8);
			// huge pauldron, elbow ring and fist block (mapped onto the arm's upper / lower skin rows)
			M.part(b, [Math.min(X(9.5), X(22.5)), 28, -6.6, Math.max(X(9.5), X(22.5)), 37.5, 6.6], arm, 0, { map: fr(arm, 'top', 0, 4) });
			M.part(b, [Math.min(X(10.2), X(21.3)), 18.5, -5.9, Math.max(X(10.2), X(21.3)), 21, 5.9], arm, 0, { map: fr(arm, 'front', 7, 9) });
			M.part(b, [Math.min(X(10), X(21.6)), 3.5, -6.6, Math.max(X(10), X(21.6)), 11.5, 6.6], arm, 0, { map: fr(arm, 'front', 9, 12) });
		}
		for (const [b, s] of [['leg_r', -1], ['leg_l', 1]]) {
			const leg = s < 0 ? 'rleg' : 'lleg', pa = s < 0 ? 'rpants' : 'lpants';
			const X = v => s * v;
			const a = [Math.min(X(0.4), X(9.8)), 0, -5, Math.max(X(0.4), X(9.8)), 17.5, 5];
			M.part(b, a, leg, 0.3); M.part(b, a, pa, 0.75);
			M.part(b, [Math.min(X(0.2), X(10.6)), 0, -7.6, Math.max(X(0.2), X(10.6)), 3.2, 5.4], leg, 0, { map: fr(leg, 'front', 9, 12) });
			M.part(b, [Math.min(X(2.2), X(8.0)), 7.2, -6.4, Math.max(X(2.2), X(8.0)), 11, -4.6], leg, 0, { map: fr(leg, 'front', 3, 6) });
		}
		M.fx('arm_r', [-17.2, -14, -1.6, -14.4, 3.5, 1.6], FXI.rep, true); M.fx('arm_r', [-18.6, -14, -3, -13, 3.5, 3], FXI.rep);
		M.fx('root', [-1.2, 29, -30, 1.2, 31.4, -6.9], FXI.uni, true); M.fx('root', [-2.6, 27.6, -30, 2.6, 32.8, -6.9], FXI.uni);
	},
};

// ================================================================ sheets
function bloomPost(rad1, rad2, k1, k2) {
	return ctx => {
		const { W, H, col, emi, ss } = ctx;
		const e1 = Float32Array.from(emi), e2 = Float32Array.from(emi);
		for (let k = 0; k < 3; k++) L.boxBlur(e1, W, H, Math.round(rad1 * ss), 3);
		for (let k = 0; k < 3; k++) L.boxBlur(e2, W, H, Math.round(rad2 * ss), 3);
		for (let i = 0; i < W * H * 3; i++) col[i] += k1 * e1[i] + k2 * e2[i];
	};
}
const LIGHT = Object.assign({}, L.DEFAULT_LIGHT, { keyK: 0.82, rimK: 0.85, amb: 0.34 });
function planted(M, pose, lift = 0) { return [0, -M.bounds(M.world(pose), c => !isFx(c)).lo[1] + lift, 0]; }
function noShadowFx(faces) { for (const f of faces) if (isFx(f.c)) f.noShadow = true; return faces; }

function sheet(file, title, sub, panels, opts = {}) {
	const PH = opts.panelH || 1100, TITLE = 84, PAD = 0.08;
	const fits = panels.map(pn => { pn.faces = noShadowFx(L.gather(pn.items)); return L.projBounds(pn.faces.filter(f => !isFx(f.c)), pn.cam, pn.items.map(it => it.pos || [0, 0, 0])); });
	let ppu = Infinity;
	fits.forEach((b, i) => { if (!panels[i].ownScale) ppu = Math.min(ppu, PH * (1 - 2 * PAD) / (b.hi[1] - b.lo[1])); });
	const widths = fits.map((b, i) => {
		let k = ppu;
		if (panels[i].ownScale) k = Math.min(ppu, PH * (1 - 2 * PAD) / (b.hi[1] - b.lo[1]));
		panels[i].ppu = k;
		return Math.max(opts.minPanelW || 420, Math.round((b.hi[0] - b.lo[0]) * k + PH * 2 * PAD * 1.2) + (panels[i].extraW || 0));
	});
	let total = widths.reduce((a, b) => a + b, 0);
	const minW = opts.minW || 1900;
	if (total < minW) { const ex = (minW - total) / widths.length; for (let i = 0; i < widths.length; i++) widths[i] = Math.round(widths[i] + ex); total = widths.reduce((a, b) => a + b, 0); }
	const cv = new L.Canvas(total, PH + TITLE, [34, 36, 42]);
	let x = 0;
	panels.forEach((pn, i) => {
		const b = fits[i], k = pn.ppu, w = widths[i];
		const cxw = (b.lo[0] + b.hi[0]) / 2, cyw = (b.lo[1] + b.hi[1]) / 2;
		const origin = [w / 2 - cxw * k + (pn.dx || 0), PH / 2 + cyw * k + 24];
		const floors = pn.items.map(it => ({ x: (it.pos || [0, 0, 0])[0], z: (it.pos || [0, 0, 0])[2], r: it.model.heightBlocks * 0.42 + 0.8 }));
		const img = L.renderPanel({
			items: pn.items, faces: pn.faces, W: w, H: PH, cam: pn.cam, ppu: k, origin, ss: 3, floor: floors, light: LIGHT,
			bg: { top: [132, 136, 146], bottom: [80, 84, 94] }, bloom: 0, post: bloomPost(PH * 0.01, PH * 0.04, 0.85, 0.55),
			aoR: 0.12, edgeThr: 0.04, edgeK: 0.7, shadowSoft: 0.14, contactH: 0.3,
		});
		cv.blit(img, w, PH, x, TITLE);
		if (i > 0) cv.rect(x, TITLE, 2, PH, [30, 32, 38]);
		cv.text(pn.label, x + w / 2, TITLE + 18, 3, [236, 240, 246], { align: 'center' });
		x += w;
	});
	cv.rect(0, 0, total, TITLE, [26, 28, 34]);
	cv.text(title, 28, 18, 5, [240, 244, 250]);
	if (sub) cv.text(sub, 28 + title.length * 30 + 28, 30, 3, [160, 190, 220]);
	if (opts.footer) cv.text(opts.footer, total - 24, TITLE + PH - 34, 2, [214, 220, 230], { align: 'right' });
	cv.save(file);
	console.log(`wrote ${file} (${total}x${PH + TITLE})`);
}

function render(M, file, title, sub, action, footer, stand) {
	stand = stand || { arm_r: [0, 0, 4], arm_l: [0, 0, -4], leg_r: [0, 0, 1.5], leg_l: [0, 0, -1.5] };
	const it = (pose, extra = {}) => Object.assign({ model: M, pose, root: planted(M, pose), hide: isFx }, extra);
	const pitch = 5;
	const panels = [
		{ label: 'FRONT', items: [it(stand)], cam: { yaw: 0, pitch } },
		{ label: 'THREE-QUARTER', items: [it(stand)], cam: { yaw: 35, pitch } },
		{ label: 'SIDE', items: [it(stand)], cam: { yaw: 90, pitch } },
		{ label: 'BACK', items: [it(stand)], cam: { yaw: 180, pitch } },
	];
	if (action) panels.push({
		label: action.label, ownScale: true, extraW: action.extraW || 0, dx: action.dx || 0,
		items: [it(action.pose, { root: planted(M, action.pose, action.lift || 0), hide: c => isFx(c) && !(action.fx || []).some(fn => fn(c)) })],
		cam: { yaw: action.yaw == null ? 30 : action.yaw, pitch: action.pitch == null ? 1 : action.pitch },
	});
	sheet(path.join(OUT, file), title, sub, panels, { footer });
}

// fx selectors
const FXSEL = {
	palmR: c => c.bone === 'arm_r' && c.uv.north.fx === FXI.rep,
	chest: c => c.bone === 'root' && c.uv.north.fx === FXI.uni,
	boots: c => (c.bone === 'leg_r' || c.bone === 'leg_l') && c.uv.north.fx === FXI.jet,
	back: c => c.bone === 'root' && c.uv.north.fx === FXI.jet,
};

// ================================================================ main
fs.mkdirSync(path.join(OUT, 'skins'), { recursive: true });
const m2 = loadMark2();
GLOWPOS = new Set(); for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) if (OWNER[y * 64 + x] && m2.opaque(x, y) && family(m2.get(x, y)) === 'glow') GLOWPOS.add(y * 64 + x);
const mark2 = m2.clone(); mark2Glow(mark2); eyesGlow(mark2, [200, 238, 255]);
const mk1 = Skin.load(path.join(ASSETS, 'mark_1.png'));
try { const g = pk.decode(path.join(ASSETS, 'mark_1_glowmask.png')); mk1.glow = Buffer.from(g.px); } catch (e) { /* none */ }

const SUITS = [
	{ id: 'mark_1', file: '01_mark1.png', title: 'MARK 1', sub: 'THE CURRENT IN-GAME MARK 1 (UNCHANGED)', skin: () => mk1,
		footer: 'TALES OF SUSPENSE 39 - GREY IRON BOX OF SCRAPS',
		action: { label: 'ACTION: IRON FIST', yaw: -50, pose: { root: [0, 10, 0], head: [0, -8, 0], arm_r: [-90, 0, 0], arm_l: [30, 0, -8], leg_r: [-24, 0, 2], leg_l: [20, 0, -2] } } },
	{ id: 'mark_1_v2', file: '02_mark1_v2.png', title: 'MARK 1 V2', sub: "USER'S GOLD MK1 SKIN, CLEANED + NEW OUTER ARMOUR LAYER", skin: buildMark1v2,
		footer: 'TALES OF SUSPENSE 40 - POLISHED GOLD - FACEPLATE FRAME - CHEST RIM - SHOULDER + KNEE PLATES - CUFFS',
		action: { label: 'ACTION: JET-BOOT LIFT-OFF', yaw: 28, lift: 6, fx: [FXSEL.boots], pose: { head: [-10, 0, 0], arm_r: [0, 0, 28], arm_l: [0, 0, -28], leg_r: [6, 0, 3], leg_l: [-4, 0, -3] } } },
	{ id: 'mark_2', file: '03_mark2.png', title: 'MARK 2', sub: 'CLASSIC IRON MAN (ZUKKITO) - FACEPLATE SLIGHTLY SMALLER', skin: () => mark2,
		footer: 'TALES OF SUSPENSE 48 - RED + GOLD - ROUND UNIBEAM - PALM REPULSORS - BOOT JETS',
		action: { label: 'ACTION: REPULSOR BLAST', yaw: -58, extraW: 420, dx: 210, fx: [FXSEL.palmR], pose: { root: [0, 8, 0], head: [0, -8, 0], arm_r: [-90, 0, 0], arm_l: [-24, 0, -18], leg_r: [-14, 0, 3], leg_l: [16, 0, -3] } } },
	{ id: 'mark_iii', file: '04_mark3.png', title: 'MARK 3', sub: 'MARK 2 VARIANT - THE SEGMENTED 70S-80S CLASSIC', skin: () => buildMark3(m2),
		footer: 'TRIANGULAR UNIBEAM - GOLD SEGMENTED ABS - SHOULDER + KNEE SEAMS - GOLD CUFFS',
		action: { label: 'ACTION: UNIBEAM', yaw: -40, extraW: 380, dx: 190, fx: [FXSEL.chest], pose: { root: [-6, 0, 0], head: [6, 0, 0], arm_r: [36, 0, 14], arm_l: [36, 0, -14], leg_r: [-10, 0, 5], leg_l: [12, 0, -5] } } },
	{ id: 'mark_4', file: '05_mark4_hydro.png', title: 'MARK 4 - HYDRO', sub: 'MARK 2 VARIANT - DEEP-SEA ARMOUR (IRON MAN 218)', skin: () => buildHydro(m2),
		footer: 'NAVY + GUNMETAL + YELLOW - PORTHOLE RING - HEADLAMPS - AIR TANKS - HAZARD BELT - WEIGHTED BOOTS',
		action: { label: 'ACTION: SEA-FLOOR STRIDE', yaw: 55, pose: { root: [12, 0, 0], head: [-10, 0, 0], arm_r: [-60, 0, 8], arm_l: [-30, 0, -8], leg_r: [-28, 0, 2], leg_l: [24, 0, -2] } } },
	{ id: 'mark_v', file: '06_mark5_stealth.png', title: 'MARK 5 - STEALTH', sub: 'MARK 2 VARIANT - IRON MAN 152', skin: () => buildStealth(m2),
		footer: 'MATTE BLACK + DARK GREY - BLUE-GREY SEAMS - DIM EYES + LOW-GLOW EMITTER',
		action: { label: 'ACTION: SILENT HOVER', yaw: 30, lift: 8, fx: [FXSEL.boots], pose: { head: [-8, -10, 0], arm_r: [-8, 0, 20], arm_l: [-8, 0, -20], leg_r: [4, 0, 3], leg_l: [-10, 0, -3] } } },
	{ id: 'mark_6', file: '07_mark6_silver_centurion.png', title: 'MARK 6 - SILVER CENTURION', sub: 'MARK 2 VARIANT - IRON MAN 200', skin: () => buildCenturion(m2),
		footer: 'SILVER + RED - FLARED SHOULDER BLADES - TRIANGULAR CHEST BEAM - SILVER FACEPLATE',
		action: { label: 'ACTION: CHEST BEAM', yaw: -40, extraW: 380, dx: 190, fx: [FXSEL.chest], pose: { root: [-5, 0, 0], arm_r: [40, 0, 18], arm_l: [40, 0, -18], leg_r: [0, 0, 8], leg_l: [0, 0, -8] } } },
	{ id: 'mark_vii', file: '08_mark7_space.png', title: 'MARK 7 - SPACE', sub: 'MARK 2 VARIANT - IRON MAN 142', skin: () => buildSpace(m2),
		footer: 'WHITE + RED + GOLD TRIM - GOLD VISOR - THRUSTER BACKPACK - BOOT THRUSTERS',
		action: { label: 'ACTION: FLIGHT', yaw: 60, pitch: 2, lift: 4, fx: [FXSEL.boots, FXSEL.back], pose: { root: [48, 0, 0], head: [-34, 0, 0], arm_r: [-150, 0, 10], arm_l: [14, 0, -16], leg_r: [10, 0, 3], leg_l: [-6, 0, -3] } } },
	{ id: 'mark_8', file: '09_mark8_hulkbuster.png', title: 'MARK 8 - HULKBUSTER', sub: 'MARK 2 VARIANT ON A BULKY MECH BODY - ABOUT 3 BLOCKS', skin: () => buildHulkbusterSkin(m2), rig: HULK,
		footer: 'MARK 3 COLOURS + SILVER HYDRAULICS - SMALL HEAD - HUGE FISTS - BACK THRUSTERS - TRIANGULAR EMITTER',
		stand: { arm_r: [0, 0, 6], arm_l: [0, 0, -6], leg_r: [0, 0, 2], leg_l: [0, 0, -2] },
		action: { label: 'ACTION: HULKBUSTER PUNCH', yaw: -55, extraW: 300, dx: 150, fx: [FXSEL.palmR], pose: { root: [8, 12, 0], head: [-6, -8, 0], arm_r: [-92, 0, 0], arm_l: [28, 0, -10], leg_r: [-20, 0, 2], leg_l: [16, 0, -2] } } },
];

const built = {};
for (const S of SUITS) {
	if (!WANT(S.id) && !WANT('lineup')) continue;
	const sk = S.skin();
	sk.save(path.join(OUT, 'skins', S.id + '.png'));
	const M = skinModel(S.id, sk, S.rig || PLAYER);
	built[S.id] = { M, S };
	if (WANT(S.id)) render(M, S.file, S.title, S.sub, S.action, S.footer, S.stand);
}

if (WANT('lineup')) {
	const sil = new Skin(); for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) { const o = OWNER[y * 64 + x]; if (o && !OUTER[o.part] === false) sil.set(x, y, [58, 62, 72]); else if (o && Object.keys(OUTER).includes(o.part)) sil.set(x, y, [58, 62, 72]); }
	const P = skinModel('player', sil, { bones: PLAYER.bones, build(M) { M.part('head', [-4, 24, -4, 4, 32, 4], 'head', 0); M.part('root', [-4, 12, -2, 4, 24, 2], 'body', 0); M.part('arm_r', [-8, 12, -2, -4, 24, 2], 'rarm', 0); M.part('arm_l', [4, 12, -2, 8, 24, 2], 'larm', 0); M.part('leg_r', [-4, 0, -2, -0.02, 12, 2], 'rleg', 0); M.part('leg_l', [0.02, 0, -2, 4, 12, 2], 'lleg', 0); } });
	const row = [{ M: P, name: 'PLAYER', h: '2 BLOCKS', sil: true }];
	const names = { mark_1: 'MARK 1', mark_1_v2: 'MARK 1 V2', mark_2: 'MARK 2', mark_iii: 'MARK 3', mark_4: 'MARK 4 HYDRO', mark_v: 'MARK 5 STEALTH', mark_6: 'MARK 6 CENTURION', mark_vii: 'MARK 7 SPACE', mark_8: 'MARK 8 HULKBUSTER' };
	for (const S of SUITS) { const { M } = built[S.id]; const pose = S.stand || {}; const b = M.bounds(M.world(pose), c => !isFx(c)); row.push({ M, pose, name: names[S.id], h: ((b.hi[1] - b.lo[1]) / 16).toFixed(1) + ' BLOCKS', w: (b.hi[0] - b.lo[0]) / 16 }); }
	row[0].w = 1.0;
	let x = 0; const items = [];
	for (const r of row) { x += r.w / 2; r.x = x; items.push({ model: r.M, pose: r.pose || {}, root: planted(r.M, r.pose || {}), pos: [x, 0, 0], hide: isFx }); x += r.w / 2 + 0.7; }
	const total = x - 0.7;
	const H = 1100, top = 84, ppu = 280;
	const cam = { yaw: 0, pitch: 4 };
	const Wpx = Math.round(total * ppu + 160);
	const origin = [80, H - 140];
	const img = L.renderPanel({ items, faces: L.gather(items), W: Wpx, H, cam, ppu, origin, ss: 2, light: LIGHT, floor: [{ x: total / 2, z: 0, r: total * 0.62, k: 0.4 }], bg: { top: [132, 136, 146], bottom: [80, 84, 94] }, bloom: 0, post: bloomPost(H * 0.01, H * 0.04, 0.85, 0.55), aoR: 0.12, edgeThr: 0.04, edgeK: 0.7, shadowSoft: 0.14, contactH: 0.3 });
	const cv = new L.Canvas(Wpx, H + top, [26, 28, 34]);
	cv.blit(img, Wpx, H, 0, top);
	cv.text('IRON MAN - COMIC ARMOURS', 28, 18, 5, [240, 244, 250]);
	cv.text('LINEUP AT TRUE SCALE - RULER IN BLOCKS', 28 + 24 * 30 + 28, 30, 3, [160, 190, 220]);
	for (let b = 0; b <= 3; b++) { const yy = top + origin[1] - Math.round(b * ppu * Math.cos(cam.pitch * Math.PI / 180)); cv.rect(0, yy, 22, 2, [230, 236, 244]); if (b) cv.text(String(b), 28, yy - 20, 2, [230, 236, 244]); }
	for (const r of row) { const sx = origin[0] + r.x * ppu; cv.text(r.name, sx, top + H - 100, 2, [240, 244, 250], { align: 'center' }); cv.text(r.h, sx, top + H - 74, 2, [160, 196, 236], { align: 'center' }); }
	cv.save(path.join(OUT, '00_lineup.png'));
	console.log('wrote 00_lineup.png');
}
