// v0.17.7 Trial of Asgard boss: THE DESTROYER's GeckoLib assets, built from a player skin.
// User: "use this skin for the model of the destroyer please: https://www.planetminecraft.com/skin/destroyer-6483954/"
// Skin source: PlanetMinecraft, "Destroyer" (https://www.planetminecraft.com/skin/destroyer-6483954/), kept as
// scratchpad/v0177/destroyer_skin_original.png (64x64; its overlay areas use near-black for "transparent", keyed out here).
// Borrowed skins ship publicly with the mod (CLAUDE.md), credited here and in DestroyerRenderer.
//
// The body is a player-shaped model at 1 unit per skin texel (32 units tall; DestroyerRenderer scales it 1.8x to about 3.6
// blocks), so it looks exactly like the skin, just big:
//   - head + hat layer, body + jacket layer: the skin's own 64x64 box-UV layout, copied 1:1 into the atlas;
//   - arms and legs split at the elbow / knee (so the clips can bend them): each half gets its own box-UV region in the atlas,
//     painted from the matching six rows of the skin's limb faces (sleeves / trousers layers the same, inflated 0.25);
//   - the face: the head's and hat's front faces are cut out of the atlas and moved onto two faceplate bones
//     (faceplate_left / faceplate_right, the face's halves, hinged at the outer edges) that swing apart to show
//   - the molten face: a white-hot to orange cube behind them (glowmask).
// Outputs: geo/destroyer.geo.json, animations/destroyer.animation.json, textures/entity/destroyer(_unleashed)(_glowmask).png.
// Unleashed (phase 2): the skin's darkest base-layer texels (the grooves between its plates) turn to glowing orange seams.
// Usage (repo root): node scratchpad/v0177/gen_destroyer.js
// Clip timings match DestroyerBoss: form 50; beam 30 + 60 (wide 100) + 15; blast 14 + 10 + 8 (double 14+10+8+10+8);
// slam 24 + 18 (double +14); charge 16 + 16 + 18; backhand 10 + 2 + 10; surge 40.
//
// Model space: y up, faces -Z, +X is its left. +rx pitches the front down; an arm hanging down is raised forward with -rx;
// +rz lowers the +X end (so a hanging LEFT arm swings out with -rz, a right arm with +rz).
const fs = require('fs');
const path = require('path');
const { encode, decode } = require('../pngkit');

const A = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero');
const json = (p, o) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, JSON.stringify(o, null, 1) + '\n'); };
const SKIN = decode(path.join(__dirname, 'destroyer_skin_original.png'));
if (SKIN.w !== 64 || SKIN.h !== 64) throw new Error('expected a 64x64 skin');

// ---------------------------------------------------------------- atlas (128x128: the skin at 0,0 + extra regions)
const TW = 128, TH = 128;
const tex = Buffer.alloc(TW * TH * 4);
const glow = Buffer.alloc(TW * TH * 4);
const skinPx = (x, y) => { const i = (y * 64 + x) * 4; return [SKIN.px[i], SKIN.px[i + 1], SKIN.px[i + 2], SKIN.px[i + 3]]; };
const put = (buf, x, y, c) => { const i = (y * TW + x) * 4; buf[i] = c[0]; buf[i + 1] = c[1]; buf[i + 2] = c[2]; buf[i + 3] = c[3] ?? 255; };
const get = (buf, x, y) => { const i = (y * TW + x) * 4; return [buf[i], buf[i + 1], buf[i + 2], buf[i + 3]]; };
const keyed = c => c[0] <= 12 && c[1] <= 12 && c[2] <= 12; // the skin's "transparent" black
// overlay (second layer) areas of a 64x64 skin
const OVERLAY = [[32, 0, 32, 16], [0, 32, 56, 16], [0, 48, 16, 16], [48, 48, 16, 16]];
const inOverlay = (x, y) => OVERLAY.some(([ox, oy, w, h]) => x >= ox && x < ox + w && y >= oy && y < oy + h);
for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
	const c = skinPx(x, y);
	put(tex, x, y, inOverlay(x, y) && keyed(c) ? [0, 0, 0, 0] : [c[0], c[1], c[2], 255]);
}
// the face moves onto the faceplates: cut the head's and the hat's front faces out
for (let y = 8; y < 16; y++) for (let x = 8; x < 16; x++) put(tex, x, y, [0, 0, 0, 0]);
for (let y = 8; y < 16; y++) for (let x = 40; x < 48; x++) put(tex, x, y, [0, 0, 0, 0]);

/** The six faces of a box-UV region at (u, v) for a w x h x d cube: [name, x, y, fw, fh]. */
function boxFaces(u, v, w, h, d) {
	return { up: [u + d, v, w, d], down: [u + d + w, v, w, d], east: [u, v + d, d, h], north: [u + d, v + d, w, h],
		west: [u + d + w, v + d, d, h], south: [u + 2 * d + w, v + d, w, h] };
}
/** Copies a rect of the skin into the atlas (keying overlay black). */
function blit(sx, sy, w, h, dx, dy, overlay) {
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		const c = skinPx(sx + x, sy + y);
		put(tex, dx + x, dy + y, overlay && keyed(c) ? [0, 0, 0, 0] : [c[0], c[1], c[2], 255]);
	}
}
// limb halves: skin limb box at (u, v) is 4x12x4; each half is a 4x6x4 box in its own region
let ex = 64, ey = 0;
function region(w, h, d) {
	const rw = 2 * (w + d), rh = d + h;
	if (ex + rw > TW) { ex = 64; ey += rh; }
	const r = [ex, ey];
	ex += rw;
	return r;
}
function limbHalf(u, v, lower, overlay) {
	const src = boxFaces(u, v, 4, 12, 4);
	const [ru, rv] = region(4, 6, 4);
	const dst = boxFaces(ru, rv, 4, 6, 4);
	for (const f of ['east', 'north', 'west', 'south']) {
		const [sx, sy] = src[f];
		const [dx, dy] = dst[f];
		blit(sx, sy + (lower ? 6 : 0), 4, 6, dx, dy, overlay);
	}
	blit(src.up[0], src.up[1], 4, 4, dst.up[0], dst.up[1], overlay);
	blit(src.down[0], src.down[1], 4, 4, dst.down[0], dst.down[1], overlay);
	return [ru, rv];
}
const LIMBS = {
	right_arm: { base: [40, 16], over: [40, 32] },
	left_arm: { base: [32, 48], over: [48, 48] },
	right_leg: { base: [0, 16], over: [0, 32] },
	left_leg: { base: [16, 48], over: [0, 48] },
};
const LUV = {};
for (const [k, s] of Object.entries(LIMBS)) {
	LUV[k] = {
		upper: limbHalf(s.base[0], s.base[1], false, false), upperO: limbHalf(s.over[0], s.over[1], false, true),
		lower: limbHalf(s.base[0], s.base[1], true, false), lowerO: limbHalf(s.over[0], s.over[1], true, true),
	};
}
// faceplate halves: 4x8x1 boxes; north = that half of the face (model's left, +X, is the face's right-hand columns 12-16)
const sideMetal = (() => { // the helmet's side colour, darkened, for the plates' edges and backs
	let r = 0, g = 0, b = 0, n = 0;
	for (let y = 8; y < 16; y++) for (let x = 0; x < 8; x++) { const c = skinPx(x, y); r += c[0]; g += c[1]; b += c[2]; n++; }
	return [Math.round(r / n * 0.7), Math.round(g / n * 0.7), Math.round(b / n * 0.7), 255];
})();
function faceplate(su, overlay) {
	const [ru, rv] = region(4, 8, 1);
	const f = boxFaces(ru, rv, 4, 8, 1);
	for (const [name, [x, y, w, h]] of Object.entries(f)) {
		for (let yy = 0; yy < h; yy++) for (let xx = 0; xx < w; xx++) put(tex, x + xx, y + yy, overlay ? [0, 0, 0, 0] : sideMetal);
	}
	blit(su, 8, 4, 8, f.north[0], f.north[1], overlay);
	return [ru, rv];
}
const FP = { left: faceplate(12, false), right: faceplate(8, false), leftO: faceplate(44, true), rightO: faceplate(40, true) };
// the molten face: 6x6x1, white-hot in the middle, burning orange at the rim
const HOT_CORE = [255, 252, 232], HOT = [255, 196, 96], HOT_EDGE = [255, 112, 26];
const mixc = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));
const MOLTEN = region(6, 6, 1);
{
	const f = boxFaces(MOLTEN[0], MOLTEN[1], 6, 6, 1);
	for (const [name, [x, y, w, h]] of Object.entries(f)) {
		for (let yy = 0; yy < h; yy++) for (let xx = 0; xx < w; xx++) {
			const dc = Math.hypot((xx - (w - 1) / 2) / (w / 2), (yy - (h - 1) / 2) / (h / 2));
			const c = name !== 'north' ? HOT_EDGE : dc < 0.35 ? HOT_CORE : dc < 0.75 ? mixc(HOT_CORE, HOT, (dc - 0.35) / 0.4) : mixc(HOT, HOT_EDGE, Math.min(1, (dc - 0.75) / 0.3));
			put(tex, x + xx, y + yy, [...c, 255]);
			put(glow, x + xx, y + yy, [...c, 255]);
		}
	}
}
console.log('atlas regions used down to y=' + (ey + 10));

// Seams: the skin's opaque black base-layer texels (the gaps between its plates and the face's eye slits) are where the fire
// shows -- an orange glow in phase 1 (texture tinted to match), blazing when Unleashed, when its darker plates also warm.
const texU = Buffer.from(tex);
const glowU = Buffer.from(glow);
const lum = c => 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2];
let seams = 0;
for (let y = 0; y < TH; y++) for (let x = 0; x < TW; x++) {
	const c = get(tex, x, y);
	const isMolten = x >= MOLTEN[0] && x < MOLTEN[0] + 14 && y >= MOLTEN[1] && y < MOLTEN[1] + 7;
	if (c[3] === 0 || isMolten) continue;
	if (keyed(c)) {
		put(tex, x, y, [150, 58, 14, 255]);
		put(glow, x, y, [255, 118, 28, 255]);
		put(texU, x, y, [235, 120, 36, 255]);
		put(glowU, x, y, [255, 196, 96, 255]);
		seams++;
	} else if (lum(c) < 100) {
		put(glowU, x, y, [110, 40, 8, 255]); // a faint ember sheen on the darker plates
	}
}
console.log('seam texels: ' + seams);
const TD = path.join(A, 'textures/entity');
fs.mkdirSync(TD, { recursive: true });
fs.writeFileSync(path.join(TD, 'destroyer.png'), encode(TW, TH, tex));
fs.writeFileSync(path.join(TD, 'destroyer_glowmask.png'), encode(TW, TH, glow));
fs.writeFileSync(path.join(TD, 'destroyer_unleashed.png'), encode(TW, TH, texU));
fs.writeFileSync(path.join(TD, 'destroyer_unleashed_glowmask.png'), encode(TW, TH, glowU));

// ---------------------------------------------------------------- geometry (player proportions, 32 units)
const bones = [];
const byName = {};
function bone(name, parent, pivot) {
	const b = { name, pivot, cubes: [] };
	if (parent) b.parent = parent;
	bones.push(b);
	byName[name] = b;
	return b;
}
function cube(b, origin, size, uv, inflate) {
	const c = { origin, size, uv };
	if (inflate) c.inflate = inflate;
	b.cubes.push(c);
}
bone('root', null, [0, 0, 0]);
bone('hips', 'root', [0, 12, 0]);
bone('torso', 'hips', [0, 12, 0]);
cube(byName.torso, [-4, 12, -2], [8, 12, 4], [16, 16]);
cube(byName.torso, [-4, 12, -2], [8, 12, 4], [16, 32], 0.25);
bone('head', 'torso', [0, 24, 0]);
cube(byName.head, [-4, 24, -4], [8, 8, 8], [0, 0]);
cube(byName.head, [-4, 24, -4], [8, 8, 8], [32, 0], 0.5);
cube(byName.head, [-3, 25, -2.9], [6, 6, 1], MOLTEN);
bone('faceplate_left', 'head', [4, 28, -4]);
cube(byName.faceplate_left, [0, 24, -4], [4, 8, 1], FP.left);
cube(byName.faceplate_left, [0, 24, -4.5], [4, 8, 1], FP.leftO);
bone('faceplate_right', 'head', [-4, 28, -4]);
cube(byName.faceplate_right, [-4, 24, -4], [4, 8, 1], FP.right);
cube(byName.faceplate_right, [-4, 24, -4.5], [4, 8, 1], FP.rightO);
for (const [side, s] of [['right', -1], ['left', 1]]) {
	const L = LUV[`${side}_arm`];
	const ax = s > 0 ? 4 : -8; // the arm's -X edge
	bone(`${side}_arm`, 'torso', [s * 5, 22, 0]);
	cube(byName[`${side}_arm`], [ax, 18, -2], [4, 6, 4], L.upper);
	cube(byName[`${side}_arm`], [ax, 18, -2], [4, 6, 4], L.upperO, 0.25);
	bone(`${side}_forearm`, `${side}_arm`, [s * 6, 18, 0]);
	cube(byName[`${side}_forearm`], [ax, 12, -2], [4, 6, 4], L.lower);
	cube(byName[`${side}_forearm`], [ax, 12, -2], [4, 6, 4], L.lowerO, 0.25);
	const G = LUV[`${side}_leg`];
	const lx = s > 0 ? 0 : -4;
	bone(`${side}_leg`, 'hips', [s * 2, 12, 0]);
	cube(byName[`${side}_leg`], [lx, 6, -2], [4, 6, 4], G.upper);
	cube(byName[`${side}_leg`], [lx, 6, -2], [4, 6, 4], G.upperO, 0.25);
	bone(`${side}_shin`, `${side}_leg`, [s * 2, 6, 0]);
	cube(byName[`${side}_shin`], [lx, 0, -2], [4, 6, 4], G.lower);
	cube(byName[`${side}_shin`], [lx, 0, -2], [4, 6, 4], G.lowerO, 0.25);
}
json(path.join(A, 'geo/destroyer.geo.json'), {
	format_version: '1.12.0',
	'minecraft:geometry': [{
		description: { identifier: 'geometry.destroyer', texture_width: TW, texture_height: TH,
			visible_bounds_width: 3, visible_bounds_height: 3, visible_bounds_offset: [0, 1.5, 0] },
		bones: bones.map(b => {
			const o = { name: b.name };
			if (b.parent) o.parent = b.parent;
			o.pivot = b.pivot;
			if (b.cubes.length) o.cubes = b.cubes;
			return o;
		}),
	}],
});

/** The clips were authored for a 57-unit body; this one is 32 units: model-space translations scale by this. */
const POS_SCALE = 32 / 57;

// ---------------------------------------------------------------- animations
const r3 = v => Math.round(v * 1000) / 1000;
function clip(length, loop, tracks) {
	const out = { animation_length: r3(length) };
	if (loop) out.loop = true;
	out.bones = {};
	for (const [b, chans] of Object.entries(tracks)) {
		if (!byName[b]) throw new Error('animation names a missing bone: ' + b);
		out.bones[b] = {};
		for (const [ch, frames] of Object.entries(chans)) {
			const o = {};
			for (const [t, v] of frames) {
				if (t > length + 1e-6) throw new Error(`keyframe past the end of the clip: ${b}.${ch} @ ${t} > ${length}`);
				o[r3(t).toFixed(2)] = (ch === 'position' ? v.map(x => x * POS_SCALE) : v).map(r3);
			}
			out.bones[b][ch] = o;
		}
	}
	return out;
}
const Z = [0, 0, 0];
const add = (t, b, ch, frames) => { t[b] = t[b] || {}; t[b][ch] = frames; };
const hold = v => [[0, v]];
const anims = {};
// rest pose: arms hanging slightly out, a little bend at the elbow
const RA = [-4, 0, 7], LA = [-4, 0, -7], RF = [-10, 0, 0], LF = [-10, 0, 0];
/** Faceplates: angle 0 = shut; the halves swing apart about their outer hinges, {@code open} degrees. */
function faceplates(t, frames) {
	add(t, 'faceplate_left', 'rotation', frames.map(([tt, a]) => [tt, [0, -a, 0]]));
	add(t, 'faceplate_right', 'rotation', frames.map(([tt, a]) => [tt, [0, a, 0]]));
	add(t, 'faceplate_left', 'position', frames.map(([tt, a]) => [tt, [a / 75 * 0.8, 0, -a / 75 * 0.3]]));
	add(t, 'faceplate_right', 'position', frames.map(([tt, a]) => [tt, [-a / 75 * 0.8, 0, -a / 75 * 0.3]]));
}

// idle: still and watchful -- a slow breath, the head turning to scan, hands loose
{
	const t = {};
	add(t, 'torso', 'rotation', [[0, Z], [2, [-1.5, 0, 0]], [4, Z]]);
	add(t, 'hips', 'position', [[0, Z], [2, [0, -0.3, 0]], [4, Z]]);
	add(t, 'head', 'rotation', [[0, Z], [1.4, [2, -14, 0]], [2.8, [0, 12, 0]], [4, Z]]);
	add(t, 'right_arm', 'rotation', [[0, RA], [2, [-6, 0, 8]], [4, RA]]);
	add(t, 'left_arm', 'rotation', [[0, LA], [2, [-6, 0, -8]], [4, LA]]);
	add(t, 'right_forearm', 'rotation', hold(RF));
	add(t, 'left_forearm', 'rotation', hold(LF));
	anims.idle = clip(4, true, t);
}
// walk: a heavy, deliberate stride -- each foot planted with a dip of the hips, the arms barely swinging
{
	const t = {};
	const L = 1.6;
	add(t, 'right_leg', 'rotation', [[0, [-22, 0, 0]], [L / 2, [20, 0, 0]], [L, [-22, 0, 0]]]);
	add(t, 'left_leg', 'rotation', [[0, [20, 0, 0]], [L / 2, [-22, 0, 0]], [L, [20, 0, 0]]]);
	add(t, 'right_shin', 'rotation', [[0, [6, 0, 0]], [L * 0.62, [38, 0, 0]], [L * 0.85, [10, 0, 0]], [L, [6, 0, 0]]]);
	add(t, 'left_shin', 'rotation', [[0, [10, 0, 0]], [L * 0.12, [38, 0, 0]], [L * 0.35, [6, 0, 0]], [L, [10, 0, 0]]]);
	add(t, 'hips', 'position', [[0, [0, -1, 0]], [L / 4, [0, 0.4, 0]], [L / 2, [0, -1, 0]], [3 * L / 4, [0, 0.4, 0]], [L, [0, -1, 0]]]);
	add(t, 'torso', 'rotation', [[0, [4, 5, 0]], [L / 2, [4, -5, 0]], [L, [4, 5, 0]]]);
	add(t, 'head', 'rotation', [[0, [-3, -4, 0]], [L / 2, [-3, 4, 0]], [L, [-3, -4, 0]]]);
	add(t, 'right_arm', 'rotation', [[0, [10, 0, 7]], [L / 2, [-14, 0, 7]], [L, [10, 0, 7]]]);
	add(t, 'left_arm', 'rotation', [[0, [-14, 0, -7]], [L / 2, [10, 0, -7]], [L, [-14, 0, -7]]]);
	add(t, 'right_forearm', 'rotation', hold([-14, 0, 0]));
	add(t, 'left_forearm', 'rotation', hold([-14, 0, 0]));
	anims.walk = clip(L, true, t);
}
// form (50 ticks): it lands in the lightning -- down on one knee, a fist on the ground, head bowed (0-0.5); stands up, the plates
// settling (0.5-1.6); the head lifts (1.6-1.9); the faceplate cracks open on the fire inside (1.9-2.2) and shuts again
{
	const t = {};
	add(t, 'hips', 'position', [[0, [0, -9, 0]], [0.5, [0, -9, 0]], [1.0, [0, -5, 0]], [1.5, [0, 0.4, 0]], [1.7, Z]]);
	add(t, 'right_leg', 'rotation', [[0, [-82, 0, 0]], [0.5, [-82, 0, 0]], [1.0, [-50, 0, 0]], [1.5, Z]]);
	add(t, 'right_shin', 'rotation', [[0, [86, 0, 0]], [0.5, [86, 0, 0]], [1.0, [60, 0, 0]], [1.5, Z]]);
	add(t, 'left_leg', 'rotation', [[0, [12, 0, 0]], [0.5, [12, 0, 0]], [1.0, [-10, 0, 0]], [1.5, Z]]);
	add(t, 'left_shin', 'rotation', [[0, [84, 0, 0]], [0.5, [84, 0, 0]], [1.0, [40, 0, 0]], [1.5, Z]]);
	add(t, 'torso', 'rotation', [[0, [38, 0, 0]], [0.5, [36, 0, 0]], [1.1, [18, 0, 0]], [1.6, [-4, 0, 0]], [1.9, Z]]);
	add(t, 'head', 'rotation', [[0, [35, 0, 0]], [1.4, [30, 0, 0]], [1.85, [-8, 0, 0]], [2.2, [-4, 0, 0]], [2.5, Z]]);
	add(t, 'right_arm', 'rotation', [[0, [-22, 0, 10]], [0.5, [-22, 0, 10]], [1.2, [-10, 0, 12]], [1.7, RA]]);
	add(t, 'right_forearm', 'rotation', [[0, [-8, 0, 0]], [1.7, RF]]);
	add(t, 'left_arm', 'rotation', [[0, [20, 0, -14]], [0.5, [20, 0, -14]], [1.2, [0, 0, -14]], [1.7, LA]]);
	add(t, 'left_forearm', 'rotation', [[0, [-30, 0, 0]], [1.7, LF]]);
	add(t, 'right_arm', 'position', [[0, [-0.6, 0.8, 0]], [1.3, [-0.2, 0.2, 0]], [1.6, Z]]); // the pauldrons settle
	add(t, 'left_arm', 'position', [[0, [0.6, 0.8, 0]], [1.3, [0.2, 0.2, 0]], [1.6, Z]]);
	faceplates(t, [[0, 0], [1.9, 0], [2.05, 30], [2.25, 34], [2.45, 0]]);
	anims.form = clip(2.5, false, t);
}
// the beam: it plants its feet and the faceplate grinds open in steps while the fire builds (0-1.5), then the beam: braced, arms
// spread low and back, the head shuddering with the recoil, for the sweep; the faceplate shuts at the end
function beam(sweep) {
	const t = {};
	const W = 1.5, F = W + sweep, len = F + 0.75;
	faceplates(t, [[0, 0], [0.15, 4], [0.3, 0], [0.5, 22], [0.65, 22], [0.85, 42], [1.0, 42], [1.2, 64], [1.35, 64], [1.5, 78], [F, 78], [F + 0.35, 0]]);
	add(t, 'hips', 'position', [[0, Z], [0.6, [0, -1.5, 0]], [F, [0, -1.5, 0]], [len, Z]]);
	add(t, 'right_leg', 'rotation', [[0, Z], [0.6, [16, 0, 6]], [F, [16, 0, 6]], [len, Z]]);
	add(t, 'left_leg', 'rotation', [[0, Z], [0.6, [-20, 0, -6]], [F, [-20, 0, -6]], [len, Z]]);
	add(t, 'right_shin', 'rotation', [[0, Z], [0.6, [10, 0, 0]], [F, [10, 0, 0]], [len, Z]]);
	add(t, 'left_shin', 'rotation', [[0, Z], [0.6, [24, 0, 0]], [F, [24, 0, 0]], [len, Z]]);
	const tor = [[0, Z], [W - 0.1, [6, 0, 0]], [W + 0.1, [-6, 0, 0]], [W + 0.3, [-3, 0, 0]], [F, [-3, 0, 0]], [len, Z]];
	add(t, 'torso', 'rotation', tor);
	const head = [[0, Z], [W - 0.1, [10, 0, 0]], [W + 0.08, [-4, 0, 0]]];
	for (let k = W + 0.25; k < F - 0.1; k += 0.25) head.push([k, [(Math.round(k * 8) % 2 ? 7 : 5), 0, 0]]);
	head.push([F, [6, 0, 0]], [len, Z]);
	add(t, 'head', 'rotation', head);
	add(t, 'right_arm', 'rotation', [[0, RA], [0.6, [-10, 0, 20]], [W, [14, 0, 30]], [F, [14, 0, 30]], [len, RA]]);
	add(t, 'left_arm', 'rotation', [[0, LA], [0.6, [-10, 0, -20]], [W, [14, 0, -30]], [F, [14, 0, -30]], [len, LA]]);
	add(t, 'right_forearm', 'rotation', [[0, RF], [W, [-24, 0, 0]], [F, [-24, 0, 0]], [len, RF]]);
	add(t, 'left_forearm', 'rotation', [[0, LF], [W, [-24, 0, 0]], [F, [-24, 0, 0]], [len, LF]]);
	return clip(len, false, t);
}
anims.beam = beam(3.0);
anims.beam_wide = beam(5.0);
// fire blast: a short faceplate flash (0-0.7) and a jolt of fire from the face; the double re-aims and fires again
function blast(second) {
	const t = {};
	const len = second ? 2.5 : 1.6;
	const fp = [[0, 0], [0.2, 0], [0.35, 18], [0.45, 10], [0.65, 62]];
	const head = [[0, Z], [0.6, [8, 0, 0]], [0.72, [-14, 0, 0]], [0.95, [-4, 0, 0]]];
	const tor = [[0, Z], [0.6, [8, 0, 0]], [0.72, [-8, 0, 0]], [1.0, [-2, 0, 0]]];
	if (second) {
		fp.push([2.1, 62], [2.35, 0]);
		head.push([1.5, [8, 0, 0]], [1.62, [-14, 0, 0]], [1.9, [-4, 0, 0]], [len, Z]);
		tor.push([1.5, [8, 0, 0]], [1.62, [-8, 0, 0]], [len, Z]);
	} else {
		fp.push([1.2, 62], [1.45, 0]);
		head.push([len, Z]);
		tor.push([len, Z]);
	}
	faceplates(t, fp);
	add(t, 'head', 'rotation', head);
	add(t, 'torso', 'rotation', tor);
	add(t, 'right_arm', 'rotation', [[0, RA], [0.6, [-6, 0, 16]], [len - 0.3, [-6, 0, 16]], [len, RA]]);
	add(t, 'left_arm', 'rotation', [[0, LA], [0.6, [-6, 0, -16]], [len - 0.3, [-6, 0, -16]], [len, LA]]);
	add(t, 'right_leg', 'rotation', [[0, Z], [0.5, [10, 0, 0]], [len - 0.3, [10, 0, 0]], [len, Z]]);
	add(t, 'left_leg', 'rotation', [[0, Z], [0.5, [-12, 0, 0]], [len - 0.3, [-12, 0, 0]], [len, Z]]);
	add(t, 'left_shin', 'rotation', [[0, Z], [0.5, [14, 0, 0]], [len - 0.3, [14, 0, 0]], [len, Z]]);
	return clip(len, false, t);
}
anims.blast = blast(false);
anims.blast_double = blast(true);
// armoured slam: both fists raised high (0-1.2), brought down into the ground (1.2-1.3), then up again; the double slams twice
function slam(len, second) {
	const t = {};
	const R = [[0, RA], [1.1, [-172, 0, 14]], [1.2, [-176, 0, 14]], [1.3, [-38, 0, 12]]];
	const L = [[0, LA], [1.1, [-172, 0, -14]], [1.2, [-176, 0, -14]], [1.3, [-38, 0, -12]]];
	const TOR = [[0, Z], [1.1, [-12, 0, 0]], [1.2, [-14, 0, 0]], [1.3, [36, 0, 0]]];
	const HIP = [[0, Z], [1.1, [0, 1, 0]], [1.3, [0, -6, 0]]];
	if (second) {
		R.push([1.65, [-168, 0, 14]], [1.9, [-176, 0, 14]], [2.0, [-38, 0, 12]], [len, RA]);
		L.push([1.65, [-168, 0, -14]], [1.9, [-176, 0, -14]], [2.0, [-38, 0, -12]], [len, LA]);
		TOR.push([1.65, [-10, 0, 0]], [1.9, [-14, 0, 0]], [2.0, [38, 0, 0]], [len - 0.3, [30, 0, 0]], [len, Z]);
		HIP.push([1.65, [0, 0, 0]], [2.0, [0, -6, 0]], [len - 0.3, [0, -6, 0]], [len, Z]);
	} else {
		R.push([len, RA]);
		L.push([len, LA]);
		TOR.push([len - 0.4, [30, 0, 0]], [len, Z]);
		HIP.push([len - 0.4, [0, -6, 0]], [len, Z]);
	}
	add(t, 'right_arm', 'rotation', R);
	add(t, 'left_arm', 'rotation', L);
	add(t, 'torso', 'rotation', TOR);
	add(t, 'hips', 'position', HIP);
	add(t, 'right_forearm', 'rotation', hold([-6, 0, 0]));
	add(t, 'left_forearm', 'rotation', hold([-6, 0, 0]));
	add(t, 'right_leg', 'rotation', [[0, Z], [1.2, [-22, 0, 8]], [1.3, [-36, 0, 10]], [len, Z]]);
	add(t, 'left_leg', 'rotation', [[0, Z], [1.2, [12, 0, -8]], [1.3, [20, 0, -10]], [len, Z]]);
	add(t, 'right_shin', 'rotation', [[0, Z], [1.3, [42, 0, 0]], [len, Z]]);
	add(t, 'left_shin', 'rotation', [[0, Z], [1.3, [24, 0, 0]], [len, Z]]);
	add(t, 'head', 'rotation', [[0, Z], [1.1, [-22, 0, 0]], [1.3, [18, 0, 0]], [len, Z]]);
	return clip(len, false, t);
}
anims.slam = slam(2.1, false);
anims.slam_double = slam(2.8, true);
// charge: low crouch, shoulder forward (0-0.8), the run (0.8-1.6), the stagger -- bent over, hands on its knees (1.6-2.5)
{
	const t = {};
	add(t, 'torso', 'rotation', [[0, Z], [0.7, [30, -22, 0]], [0.8, [38, -26, 0]], [1.6, [38, -26, 0]], [1.75, [44, 0, 0]], [2.3, [40, 0, 0]], [2.5, Z]]);
	add(t, 'hips', 'position', [[0, Z], [0.7, [0, -3, 0]], [1.6, [0, -2, 0]], [1.75, [0, -4, 0]], [2.3, [0, -4, 0]], [2.5, Z]]);
	add(t, 'right_leg', 'rotation', [[0, Z], [0.7, [-40, 0, 0]], [0.8, [-30, 0, 0]], [1.0, [30, 0, 0]], [1.2, [-30, 0, 0]], [1.4, [30, 0, 0]], [1.6, [-20, 0, 0]], [1.75, [-50, 0, 0]], [2.5, Z]]);
	add(t, 'left_leg', 'rotation', [[0, Z], [0.7, [20, 0, 0]], [0.8, [30, 0, 0]], [1.0, [-30, 0, 0]], [1.2, [30, 0, 0]], [1.4, [-30, 0, 0]], [1.6, [20, 0, 0]], [1.75, [10, 0, 0]], [2.5, Z]]);
	add(t, 'right_shin', 'rotation', [[0, Z], [0.7, [60, 0, 0]], [0.8, [20, 0, 0]], [1.75, [70, 0, 0]], [2.5, Z]]);
	add(t, 'left_shin', 'rotation', [[0, Z], [0.7, [30, 0, 0]], [0.8, [30, 0, 0]], [1.75, [40, 0, 0]], [2.5, Z]]);
	add(t, 'left_arm', 'rotation', [[0, LA], [0.7, [-62, 30, -20]], [1.6, [-70, 30, -20]], [1.75, [-30, 0, -10]], [2.3, [-34, 0, -8]], [2.5, LA]]);
	add(t, 'right_arm', 'rotation', [[0, RA], [0.7, [30, 0, 22]], [1.6, [30, 0, 22]], [1.75, [-30, 0, 10]], [2.3, [-34, 0, 8]], [2.5, RA]]);
	add(t, 'right_forearm', 'rotation', [[0, RF], [0.7, [-30, 0, 0]], [2.5, RF]]);
	add(t, 'left_forearm', 'rotation', [[0, LF], [0.7, [-40, 0, 0]], [2.5, LF]]);
	add(t, 'head', 'rotation', [[0, Z], [0.7, [-30, 15, 0]], [1.6, [-30, 15, 0]], [1.75, [12, 0, 0]], [2.5, Z]]);
	anims.charge = clip(2.5, false, t);
}
// backhand (22 ticks): the right arm drawn across the body with a twist (0-0.5), a flat swat out to the side (0.5-0.6), recover
{
	const t = {};
	add(t, 'torso', 'rotation', [[0, Z], [0.45, [4, 32, 0]], [0.6, [4, -34, 0]], [0.8, [2, -30, 0]], [1.1, Z]]);
	add(t, 'right_arm', 'rotation', [[0, RA], [0.45, [-78, 0, -48]], [0.6, [-84, 0, 70]], [0.8, [-70, 0, 64]], [1.1, RA]]);
	add(t, 'right_forearm', 'rotation', [[0, RF], [0.45, [-50, 0, 0]], [0.6, [-6, 0, 0]], [1.1, RF]]);
	add(t, 'left_arm', 'rotation', [[0, LA], [0.45, [-20, 0, -18]], [0.6, [10, 0, -10]], [1.1, LA]]);
	add(t, 'right_leg', 'rotation', [[0, Z], [0.45, [12, 0, 0]], [0.6, [-6, 0, 0]], [1.1, Z]]);
	add(t, 'left_leg', 'rotation', [[0, Z], [0.45, [-12, 0, 0]], [0.6, [8, 0, 0]], [1.1, Z]]);
	add(t, 'head', 'rotation', [[0, Z], [0.45, [0, -20, 0]], [0.6, [0, 14, 0]], [1.1, Z]]);
	anims.backhand = clip(1.1, false, t);
}
// surge (phase 2, "Unleashed"): it buckles to a knee as the seams flare, then stands with the faceplate wide open, arms flung
// out and head thrown back
{
	const t = {};
	add(t, 'hips', 'position', [[0, Z], [0.4, [0, -7, 0]], [1.2, [0, -7, 0]], [1.6, [0, 1, 0]], [2.0, Z]]);
	add(t, 'right_leg', 'rotation', [[0, Z], [0.4, [-80, 0, 0]], [1.2, [-80, 0, 0]], [1.6, Z]]);
	add(t, 'right_shin', 'rotation', [[0, Z], [0.4, [85, 0, 0]], [1.2, [85, 0, 0]], [1.6, Z]]);
	add(t, 'left_leg', 'rotation', [[0, Z], [0.4, [10, 0, 0]], [1.2, [10, 0, 0]], [1.6, Z]]);
	add(t, 'left_shin', 'rotation', [[0, Z], [0.4, [80, 0, 0]], [1.2, [80, 0, 0]], [1.6, Z]]);
	add(t, 'torso', 'rotation', [[0, Z], [0.4, [35, 0, 0]], [1.2, [30, 0, 0]], [1.6, [-22, 0, 0]], [2.0, [-8, 0, 0]]]);
	add(t, 'head', 'rotation', [[0, Z], [0.4, [30, 0, 0]], [1.2, [25, 0, 0]], [1.6, [-40, 0, 0]], [2.0, [-14, 0, 0]]]);
	add(t, 'left_arm', 'rotation', [[0, LA], [0.4, [20, 0, -20]], [1.2, [20, 0, -20]], [1.6, [-30, 0, -105]], [2.0, [-16, 0, -50]]]);
	add(t, 'right_arm', 'rotation', [[0, RA], [0.4, [20, 0, 20]], [1.2, [20, 0, 20]], [1.6, [-30, 0, 105]], [2.0, [-16, 0, 50]]]);
	faceplates(t, [[0, 0], [1.25, 0], [1.5, 85], [1.85, 85], [2.0, 50]]);
	anims.surge = clip(2.0, false, t);
}
// death (the boss lingers 50 ticks): the fire goes out -- it staggers back, the faceplate snaps shut, the knees buckle and the
// armour slumps forward, hollow, head bowed and arms hanging
{
	const t = {};
	add(t, 'hips', 'position', [[0, Z], [0.4, [0, -0.5, 1]], [1.1, [0, -10, 0]], [1.4, [0, -11, 0]], [2.5, [0, -11, 0]]]);
	add(t, 'torso', 'rotation', [[0, Z], [0.4, [-16, 8, 0]], [1.1, [26, 4, 0]], [1.5, [48, 4, 0]], [1.65, [44, 4, 0]], [2.5, [46, 4, 0]]]);
	add(t, 'head', 'rotation', [[0, Z], [0.4, [-24, 0, 0]], [1.4, [40, 0, 6]], [2.5, [48, 0, 8]]]);
	add(t, 'right_leg', 'rotation', [[0, Z], [1.1, [-86, 0, 4]], [2.5, [-88, 0, 4]]]);
	add(t, 'left_leg', 'rotation', [[0, Z], [1.1, [-80, 0, -4]], [2.5, [-82, 0, -4]]]);
	add(t, 'right_shin', 'rotation', [[0, Z], [1.1, [90, 0, 0]], [2.5, [90, 0, 0]]]);
	add(t, 'left_shin', 'rotation', [[0, Z], [1.1, [90, 0, 0]], [2.5, [90, 0, 0]]]);
	add(t, 'right_arm', 'rotation', [[0, RA], [0.4, [-34, 0, 34]], [1.5, [-30, 0, 8]], [2.5, [-36, 0, 6]]]);
	add(t, 'left_arm', 'rotation', [[0, LA], [0.4, [-26, 0, -34]], [1.5, [-26, 0, -8]], [2.5, [-32, 0, -6]]]);
	add(t, 'right_forearm', 'rotation', [[0, RF], [1.5, [-4, 0, 0]]]);
	add(t, 'left_forearm', 'rotation', [[0, LF], [1.5, [-4, 0, 0]]]);
	faceplates(t, [[0, 0], [0.3, 0]]);
	anims.death = clip(2.5, false, t);
}
const out = { format_version: '1.8.0', animations: {} };
for (const [k, v] of Object.entries(anims)) out.animations['animation.destroyer.' + k] = v;
json(path.join(A, 'animations/destroyer.animation.json'), out);
json(path.join(A, 'models/item/destroyer_spawn_egg.json'), { parent: 'minecraft:item/template_spawn_egg' });
console.log('destroyer: ' + bones.length + ' bones, ' + Object.keys(anims).length + ' clips');
