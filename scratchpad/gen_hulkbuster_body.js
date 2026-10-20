// Mark 8 HULKBUSTER: a segmented GeckoLib BODY that replaces the wearer's player model while the full suit is worn
// (like the Hulk's player-replacement model). Not wired into Java here; the Iron Man rework hooks it up.
//
// Texture source: the 64x64 "Hulkbuster" skin from Planet Minecraft (resource 4269320),
// <main repo>/scratchpad/ironman_refs/hulkbuster_pmc_4269320.png. Its regions are split across the segments (the upper
// rows of every arm / leg face go on the upper segment, the middle rows on the lower segment, the last rows on the
// fist / foot), the outer skin layer is an inflated shell per segment, and the extra armour (pauldrons, missile-pod lids,
// knuckle plates, elbow / knee / hip pistons, back power pack + thrusters) samples a small palette strip painted into
// an unused corner of the skin (x 56-63, y 16-19) in the skin's own reds, golds and steels.
//
// Rig (Bedrock json px, faces -Z, the wearer's RIGHT is -X; about 46 px = 2.9 blocks at model scale 1):
//   root > hips > waist > flinch > chest > neck > head;  chest > backpack > jet_back;  chest > beam_fx
//   chest > shoulder_r > arm_r > forearm_r > hand_r;  shoulder_r > pod_r          (and the _l mirrors)
//   hips > thigh_r > shin_r > foot_r > jet_r                                    (and the _l mirrors)
// `head` (look, from the player's yaw / pitch) and `flinch` are code-driven and NEVER keyed. jet_back / jet_r / jet_l
// / beam_fx are effect bones at scale 0 unless a clip shows them; rocket_fist scales forearm_r (+ hand) to 0 while the
// fist flies.
//
// Usage: node scratchpad/gen_hulkbuster_body.js       (writes the assets into THIS checkout's src/main/resources,
//        scratchpad/hulkbuster_anim_timing.json, and the preview sheets into <main repo>/scratchpad/concepts_ironman/)
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('./lib_concept_models.js');
const G = require('./lib_geckogen_anim.js');
const pk = require('./pngkit.js');
const { AnimSet, addPose: ap, mirrorPose: mir } = G;
const { Model, mix } = L;

// ================================================================ TIMING TABLE (server ticks)
// TRANSITION = 4: a play-once / held clip is (length - 4) ticks long and each strike keyframe sits on (hit - 4);
// loops are `length` ticks. `hit` lists every strike tick (multi-hit moves list several).
const T = 4;
const TIMING = {
	idle:             { kind: 'loop', length: 60 },
	walk:             { kind: 'loop', length: 32 },
	run:              { kind: 'loop', length: 20 },
	jet_hover:        { kind: 'loop', length: 40 },
	jump:             { kind: 'once', length: 18 },
	land:             { kind: 'once', length: 16, hit: [4] },          // heavy landing: impact shockwave
	punch_combo:      { kind: 'once', length: 40, hit: [10, 20, 30] }, // right jab, left hook, right uppercut
	rocket_fist:      { kind: 'once', length: 54, hit: [16], events: { fist_launch: 16, fist_return: 40 } }, // forearm_r hidden clip 13-34
	pile_driver:      { kind: 'once', length: 36, hit: [20] },
	earthquake_stomp: { kind: 'once', length: 32, hit: [16] },
	grab:             { kind: 'hold', length: 20, hit: [10] },
	grab_pound:       { kind: 'once', length: 34, hit: [14, 24] },
	grab_throw:       { kind: 'once', length: 28, hit: [14] },
	chest_beam:       { kind: 'once', length: 54, hit: [14], events: { beam_start: 14, beam_end: 42 } },
	missile_swarm:    { kind: 'once', length: 44, hit: [16], events: { pods_open: 10, pods_close: 30 } },
	hydraulic_brace:  { kind: 'hold', length: 16 },
	replacement_pod:  { kind: 'once', length: 44, events: { clamp: 20 } },
	sentry_idle:      { kind: 'loop', length: 80 },
	sentry_close:     { kind: 'once', length: 28, events: { back_closed: 18 } },
};

// ================================================================ paths
function mainRoot() {
	const d = __dirname.split(path.sep).join('/');
	const i = d.indexOf('/.claude/worktrees/');
	return i >= 0 ? d.slice(0, i) : path.join(__dirname, '..');
}
const ROOT = mainRoot();
const SRC = path.join(ROOT, 'scratchpad', 'ironman_refs', 'hulkbuster_pmc_4269320.png');
const RES = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero');
const GEO = path.join(RES, 'geo', 'ironman', 'hulkbuster_body.geo.json');
const TEX = path.join(RES, 'textures', 'armor', 'hulkbuster_body.png');
const GLOW = path.join(RES, 'textures', 'armor', 'hulkbuster_body_glowmask.png');
const ANIM = path.join(RES, 'animations', 'ironman', 'hulkbuster_body.animation.json');
const TIMING_OUT = path.join(__dirname, 'hulkbuster_anim_timing.json');
const PREVIEW = path.join(ROOT, 'scratchpad', 'concepts_ironman');
const ID = 'hulkbuster_body';

// ================================================================ skin
const PARTS = {
	head: { U: 0, V: 0, w: 8, h: 8, d: 8 }, hat: { U: 32, V: 0, w: 8, h: 8, d: 8 },
	body: { U: 16, V: 16, w: 8, h: 12, d: 4 }, jacket: { U: 16, V: 32, w: 8, h: 12, d: 4 },
	rarm: { U: 40, V: 16, w: 4, h: 12, d: 4 }, rsleeve: { U: 40, V: 32, w: 4, h: 12, d: 4 },
	larm: { U: 32, V: 48, w: 4, h: 12, d: 4 }, lsleeve: { U: 48, V: 48, w: 4, h: 12, d: 4 },
	rleg: { U: 0, V: 16, w: 4, h: 12, d: 4 }, rpants: { U: 0, V: 32, w: 4, h: 12, d: 4 },
	lleg: { U: 16, V: 48, w: 4, h: 12, d: 4 }, lpants: { U: 0, V: 48, w: 4, h: 12, d: 4 },
};
function rect(P, f) {
	if (typeof P === 'string') P = PARTS[P];
	const { U, V, w, h, d } = P;
	switch (f) {
		case 'front': return [U + d, V + d, w, h];
		case 'back': return [U + 2 * d + w, V + d, w, h];
		case 'right': return [U, V + d, d, h];
		case 'left': return [U + d + w, V + d, d, h];
		case 'top': return [U + d, V, w, d];
		default: return [U + d + w, V, w, d];
	}
}
// geo face -> skin face (the convention of the in-game armour geos, e.g. geo/mark_iii.geo.json)
const F2S = { north: 'front', south: 'back', east: 'right', west: 'left', up: 'top', down: 'bottom' };
const SKF = ['front', 'back', 'right', 'left', 'top', 'bottom'];

// palette strip in the unused corner (x 56..63, y 16..19): one column per swatch, 4 shades top -> bottom
const PAL = {
	cyan: { x: 56, c: [[205, 246, 255], [180, 236, 255], [150, 222, 250], [120, 205, 240]], glow: true },
	core: { x: 57, c: [[255, 255, 255], [240, 252, 255], [225, 248, 255], [210, 242, 255]], glow: true },
	flame: { x: 58, c: [[255, 236, 170], [255, 196, 110], [255, 150, 60], [230, 110, 40]], glow: true },
	chrome: { x: 59, c: [[226, 230, 236], [186, 192, 202], [146, 152, 164], [110, 116, 128]] },
	steel: { x: 60, c: [[128, 128, 132], [108, 108, 112], [88, 88, 92], [66, 66, 70]] },
	gold: { x: 61, c: [[226, 200, 122], [206, 180, 100], [190, 160, 78], [160, 128, 58]] },
	red: { x: 62, c: [[156, 30, 12], [132, 22, 6], [108, 12, 4], [82, 6, 2]] },
	dark: { x: 63, c: [[44, 40, 40], [32, 28, 28], [24, 20, 20], [16, 14, 14]] },
};
const palRect = n => [PAL[n].x, 16, 1, 4];

function buildTexture() {
	const im = pk.decode(SRC);
	if (im.w !== 64 || im.h !== 64) throw new Error('hulkbuster skin is not 64x64');
	const tex = Buffer.from(im.px), glow = Buffer.alloc(64 * 64 * 4);
	// clear everything outside the skin layout (stray pixels), then paint the palette strip
	const owned = new Uint8Array(64 * 64);
	for (const P of Object.values(PARTS)) for (const f of SKF) { const [x0, y0, w, h] = rect(P, f); for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) owned[y * 64 + x] = 1; }
	for (let i = 0; i < 64 * 64; i++) if (!owned[i]) tex.fill(0, i * 4, i * 4 + 4);
	for (const p of Object.values(PAL)) p.c.forEach((c, j) => { const i = ((16 + j) * 64 + p.x) * 4; tex[i] = c[0]; tex[i + 1] = c[1]; tex[i + 2] = c[2]; tex[i + 3] = 255; if (p.glow) { glow[i] = c[0]; glow[i + 1] = c[1]; glow[i + 2] = c[2]; glow[i + 3] = 255; } });
	// glowmask: the skin's pale-cyan eyes / emitters
	// (the cyan knee accents on the trouser layer stay plain colour: stretched over the shins they read as light bars)
	for (let i = 0; i < 64 * 64; i++) { const px = i % 64, py = i >> 6; if (px < 16 && py >= 32) continue; const r = tex[i * 4], g = tex[i * 4 + 1], b = tex[i * 4 + 2], a = tex[i * 4 + 3]; if (a > 128 && b > 200 && g > 200 && r < 215) { glow[i * 4] = r; glow[i * 4 + 1] = g; glow[i * 4 + 2] = b; glow[i * 4 + 3] = 255; } }
	return { tex, glow };
}

// ================================================================ model
function buildModel() {
	const M = new Model(ID, { TW: 64, D: 1 });
	M.TH = 64; M.k = 1 / 16; M.MODEL_SCALE = 1; M.heightBlocks = 3;
	const bone = (n, p, piv, rot) => M.bone(n, p, piv, rot || [0, 0, 0]);
	bone('root', null, [0, 0, 0]);
	bone('hips', 'root', [0, 19, 0]);
	bone('waist', 'hips', [0, 21, 0]);
	bone('flinch', 'waist', [0, 27, 0]);
	bone('chest', 'flinch', [0, 27, 0]);
	bone('neck', 'chest', [0, 37.5, -2.5]);
	bone('head', 'neck', [0, 38.5, -4]);
	bone('backpack', 'chest', [0, 26, 9.6]);
	bone('jet_back', 'backpack', [0, 26, 8]);
	bone('beam_fx', 'chest', [0, 32, -7]);
	for (const s of ['r', 'l']) {
		const X = v => (s === 'r' ? -v : v);
		bone(`shoulder_${s}`, 'chest', [X(11), 37, 0]);
		bone(`pod_${s}`, `shoulder_${s}`, [X(16), 42.4, 5.5]);
		bone(`arm_${s}`, `shoulder_${s}`, [X(15), 34, 0]);
		bone(`forearm_${s}`, `arm_${s}`, [X(15), 25.5, 0]);
		bone(`hand_${s}`, `forearm_${s}`, [X(15.2), 15, 0]);
		bone(`thigh_${s}`, 'hips', [X(5.2), 19, 0]);
		bone(`shin_${s}`, `thigh_${s}`, [X(5.4), 11, 0]);
		bone(`foot_${s}`, `shin_${s}`, [X(5.6), 3, 0.5]);
		bone(`jet_${s}`, `foot_${s}`, [X(5.6), 0, 0]);
	}
	// a cube whose faces sample the skin: part rows r0..r1 of the side faces; top / bottom from the part's top / bottom
	const skinBox = (b, a, part, r0, r1, opts = {}) => {
		const c = M.box(b, a[0], a[1], a[2], a[3], a[4], a[5], 'skin', { tag: opts.tag });
		c.uv = {};
		for (const f of L.FACES) {
			const sf = F2S[f]; const r = rect(part, sf);
			if (sf === 'top' || sf === 'bottom') c.uv[f] = { u: r[0], v: r[1], w: r[2], h: r[3] };
			else c.uv[f] = { u: r[0], v: r[1] + r0, w: r[2], h: r1 - r0 };
		}
		return c;
	};
	// a cube mapped onto one fixed rect (palette swatch or a skin patch) on every face
	const mapBox = (b, a, R, opts = {}) => {
		const c = M.box(b, a[0], a[1], a[2], a[3], a[4], a[5], 'map', { tag: opts.tag, rot: opts.rot, pivot: opts.pivot });
		c.uv = {}; for (const f of L.FACES) c.uv[f] = { u: R[0], v: R[1], w: R[2], h: R[3] };
		return c;
	};
	const inf = (a, i) => [a[0] - i, a[1] - i, a[2] - i, a[3] + i, a[4] + i, a[5] + i];
	const seg = (b, a, part, outer, r0, r1, shell, opts) => { skinBox(b, a, part, r0, r1, opts); if (outer) skinBox(b, inf(a, shell), outer, r0, r1); };

	// ---- torso: chest (skin body rows 0-6), abdomen (6-9), hips (9-12); the outer layer as inflated shells
	seg('chest', [-11.5, 27, -6.5, 11.5, 39, 6], 'body', 'jacket', 0, 6, 0.7);
	seg('waist', [-8.2, 21, -5.3, 8.2, 27.6, 5], 'body', 'jacket', 6, 9, 0.5);
	seg('hips', [-9, 17, -5, 9, 21.6, 5], 'body', 'jacket', 9, 12, 0.5);
	skinBox('chest', [-8.5, 38.5, -4.5, 8.5, 41.6, 6.2], 'body', 0, 2);                  // collar hump
	mapBox('chest', [-3.2, 39.4, -5.2, 3.2, 41.0, -4.4], palRect('dark'));                 // neck socket
	mapBox('waist', [-9.0, 23.0, -2.6, -8.1, 26.8, 2.6], palRect('chrome'));                // waist actuators
	mapBox('waist', [8.1, 23.0, -2.6, 9.0, 26.8, 2.6], palRect('chrome'));
	mapBox('chest', [-2.4, 31.2, -7.25, 2.4, 34.8, -6.85], palRect('cyan'));               // the chest emitter (glows)
	mapBox('chest', [-3.3, 30.4, -7.0, 3.3, 35.6, -6.6], palRect('gold'));                  //   + its gold ring
	// ---- neck + small head (skin head + hat shell), sunk forward into the hump
	mapBox('neck', [-2.8, 36.5, -6.5, 2.8, 39.2, -1.5], palRect('steel'));
	seg('head', [-3.6, 38.6, -9.6, 3.6, 45.8, -2.4], 'head', 'hat', 0, 8, 0.45);
	// ---- back power pack + thrusters
	skinBox('backpack', [-8.2, 26.5, 6.1, 8.2, 38.5, 9.8], 'body', 1, 9);
	mapBox('backpack', [-7.4, 36.6, 9.8, 7.4, 37.6, 10.4], palRect('gold'));
	for (const x of [-4.6, 4.6]) {
		mapBox('backpack', [x - 2.2, 24.2, 6.6, x + 2.2, 27.0, 9.6], palRect('steel'));       // nozzle
		mapBox('backpack', [x - 1.5, 24.0, 7.3, x + 1.5, 24.3, 8.9], palRect('flame'));        // ember
		mapBox('jet_back', [x - 1.2, 12.0, 7.5, x + 1.2, 24.0, 8.7], palRect('cyan'));
		mapBox('jet_back', [x - 0.6, 10.0, 7.8, x + 0.6, 24.0, 8.4], palRect('core'));
	}
	for (let i = 0; i < 4; i++) mapBox('backpack', [-6 + i * 3.2, 29, 9.8, -4.6 + i * 3.2, 35, 10.3], palRect('dark')); // vents
	// beam (hidden unless chest_beam)
	mapBox('beam_fx', [-2.6, 30.4, -60, 2.6, 35.6, -7.2], palRect('cyan'));
	mapBox('beam_fx', [-1.2, 31.8, -62, 1.2, 34.2, -7.0], palRect('core'));
	for (const s of ['r', 'l']) {
		const X = v => (s === 'r' ? -v : v);
		const bx = (x0, y0, z0, x1, y1, z1) => [Math.min(X(x0), X(x1)), y0, z0, Math.max(X(x0), X(x1)), y1, z1];
		const arm = s === 'r' ? 'rarm' : 'larm', sl = s === 'r' ? 'rsleeve' : 'lsleeve';
		const leg = s === 'r' ? 'rleg' : 'lleg', pa = s === 'r' ? 'rpants' : 'lpants';
		// pauldron (big, layered) + missile-pod lid
		skinBox(`shoulder_${s}`, bx(10, 32.8, -7.4, 22, 42.2, 7.4), arm, 0, 3);
		mapBox(`shoulder_${s}`, bx(9.6, 32.4, -7.8, 22.4, 33.8, 7.8), palRect('gold'));
		mapBox(`shoulder_${s}`, bx(21.9, 35.0, -5.0, 22.8, 40.6, 5.0), palRect('gold'));
		for (let i = 0; i < 3; i++) mapBox(`shoulder_${s}`, bx(13 + i * 2.6, 41.4, -4.2, 14.6 + i * 2.6, 42.3, 4.2), palRect('dark')); // missile tubes under the lid
		for (let i = 0; i < 3; i++) mapBox(`shoulder_${s}`, bx(13.4 + i * 2.6, 42.2, -3.4, 14.2 + i * 2.6, 42.35, -2.6), palRect('flame'));
		mapBox(`pod_${s}`, bx(11.6, 42.2, -5.6, 20.6, 43.6, 5.6), palRect('red'));
		mapBox(`pod_${s}`, bx(12.2, 43.6, -5.0, 20.0, 44.0, 5.0), palRect('gold'));
		// upper arm (skin rows 0-4), elbow pistons
		seg(`arm_${s}`, bx(11, 25.5, -4.5, 19, 34.5, 4.5), arm, sl, 0, 4, 0.5);
		mapBox(`arm_${s}`, bx(16.0, 22.5, -5.7, 17.6, 32.0, -4.5), palRect('chrome'));
		mapBox(`arm_${s}`, bx(12.4, 22.5, -5.7, 14.0, 32.0, -4.5), palRect('chrome'));
		mapBox(`arm_${s}`, bx(15.7, 28.5, -6.0, 17.9, 32.4, -4.4), palRect('steel'));
		mapBox(`arm_${s}`, bx(12.1, 28.5, -6.0, 14.3, 32.4, -4.4), palRect('steel'));
		// forearm (skin rows 4-8): huge, gold band, elbow cop
		seg(`forearm_${s}`, bx(9, 15, -6.5, 21, 26, 6.5), arm, sl, 4, 8, 0.55);
		mapBox(`forearm_${s}`, bx(8.6, 21.4, -6.9, 21.4, 23.4, 6.9), palRect('gold'));
		mapBox(`forearm_${s}`, bx(12, 22.5, 6.4, 18, 26.5, 8.2), palRect('red'));
		mapBox(`forearm_${s}`, bx(21.0, 16.5, -3.5, 22.2, 21.0, 3.5), palRect('steel'));
		// fist (skin rows 8-12 + the arm bottom): a clean, solid armoured gauntlet block -- user request: NO separate finger /
		// thumb cubes ("they look creepy"). One full-width knuckle plate with a gold band across it, and the palm repulsor.
		seg(`hand_${s}`, bx(8.6, 6.4, -7, 21.4, 15.2, 7), arm, sl, 8, 12, 0.45);
		mapBox(`hand_${s}`, bx(9.0, 7.0, -7.9, 21.0, 12.2, -6.9), palRect('red'));
		mapBox(`hand_${s}`, bx(9.4, 7.6, -8.3, 20.6, 9.2, -7.8), palRect('gold'));
		mapBox(`hand_${s}`, bx(13.6, 6.1, -1.5, 16.6, 6.45, 1.5), palRect('cyan'));
		// thigh (skin rows 0-6), hip piston
		seg(`thigh_${s}`, bx(0.6, 10.5, -5, 10, 20, 5), leg, pa, 0, 6, 0.5);
		mapBox(`thigh_${s}`, bx(10.4, 11.5, -1.6, 11.6, 19.5, 1.6), palRect('chrome'));
		mapBox(`thigh_${s}`, bx(10.2, 16.5, -2.2, 11.8, 20.0, 2.2), palRect('steel'));
		// shin (rows 6-10), gold knee cap, calf piston
		seg(`shin_${s}`, bx(0.5, 3, -5.2, 10.5, 11, 5.2), leg, pa, 6, 10, 0.5);
		mapBox(`shin_${s}`, bx(2.8, 8.4, -6.4, 8.2, 13.2, -5.4), palRect('gold'));
		mapBox(`shin_${s}`, bx(3.4, 4.0, 5.4, 7.6, 10.0, 6.6), palRect('chrome'));
		mapBox(`shin_${s}`, bx(3.0, 8.5, 5.2, 8.0, 10.8, 7.0), palRect('steel'));
		// foot (rows 10-12 + the leg bottom = sole), toe plate, heel thruster
		seg(`foot_${s}`, bx(0.3, 0, -8.6, 11, 3.2, 5.6), leg, pa, 10, 12, 0.3, { tag: 'sole' });
		mapBox(`foot_${s}`, bx(0.8, 0.0, -9.6, 10.6, 2.2, -8.6), palRect('gold'));
		mapBox(`foot_${s}`, bx(3.6, 0.6, 5.8, 7.6, 3.0, 7.2), palRect('steel'));
		mapBox(`jet_${s}`, bx(3.8, -9, -2, 7.4, -0.05, 2), palRect('cyan'));
		mapBox(`jet_${s}`, bx(4.8, -11, -1, 6.4, -0.05, 1), palRect('core'));
	}
	return M;
}

// ================================================================ animations
const strike = p => Object.assign({}, p, { $strike: true });
const STAND = { arm_r: [0, 0, 7], arm_l: [0, 0, -7], forearm_r: [-12, 0, 0], forearm_l: [-12, 0, 0], thigh_r: [0, 0, 2], thigh_l: [0, 0, -2], shin_r: [3, 0, 0], shin_l: [3, 0, 0], foot_r: [-3, 0, 0], foot_l: [-3, 0, 0] };
const S = (...p) => ap(STAND, ...p);
// crouch: knees bent by k degrees (thigh forward, shin back, foot level)
const crouch = k => ({ thigh_r: [-k, 0, 0], thigh_l: [-k, 0, 0], shin_r: [k * 1.9, 0, 0], shin_l: [k * 1.9, 0, 0], foot_r: [-k * 0.9, 0, 0], foot_l: [-k * 0.9, 0, 0], waist: [k * 0.35, 0, 0] });

function buildClips(M) {
	const an = new AnimSet(M, ID, { transition: T, neverKey: ['head', 'flinch'], hidden: ['jet_back', 'jet_r', 'jet_l', 'beam_fx'], feet: c => c.tag === 'sole', knees: c => c.tag === 'sole' || /^(shin|foot)/.test(c.bone) });
	const mk = (name, frames, opts = {}) => {
		const t = TIMING[name];
		const len = t.kind === 'loop' ? t.length : t.length - T;
		const hits = t.hit || [];
		const fr = frames(len, hits.map(h => h - T));
		an.clip(name, len, t.kind === 'loop' ? true : t.kind === 'hold' ? 'hold' : false, fr, Object.assign({ strikes: hits, events: t.events || {} }, opts));
	};
	const io = 'easeinoutsine', oq = 'easeoutquad', ob = 'easeoutback', ic = 'easeincubic';

	mk('idle', len => [
		[0, S()],
		[len / 2, S({ chest: [-2.5, 0, 0], waist: [1, 0, 0], neck: [2, 0, 0], shoulder_r: [0, 0, -2], shoulder_l: [0, 0, 2], arm_r: [0, 0, 2], arm_l: [0, 0, -2], backpack: [-1.5, 0, 0] }, crouch(2)), io],
		[len, S(), io],
	]);
	// heavy stomping walk: contact (right leg forward, knees flexed), passing (left leg lifted high)
	const contactR = { thigh_r: [-26, 0, 2], shin_r: [10, 0, 0], foot_r: [14, 0, 0], thigh_l: [20, 0, -2], shin_l: [20, 0, 0], foot_l: [-12, 0, 0],
		hips: [0, -7, 0], chest: [4, 9, 0], arm_r: [16, 0, 8], arm_l: [-18, 0, -8], forearm_r: [-14, 0, 0], forearm_l: [-26, 0, 0], neck: [-4, -6, 0] };
	const passR = { thigh_r: [-2, 0, 2], shin_r: [8, 0, 0], foot_r: [-4, 0, 0], thigh_l: [-40, 0, -2], shin_l: [62, 0, 0], foot_l: [-18, 0, 0],
		hips: [0, 0, -2], chest: [7, 0, -1], arm_r: [2, 0, 8], arm_l: [-2, 0, -8], forearm_r: [-14, 0, 0], forearm_l: [-14, 0, 0], neck: [-6, 0, 0], waist: [4, 0, 0] };
	mk('walk', len => [
		[0, contactR], [len * 0.25, passR, io], [len * 0.5, mir(contactR), io], [len * 0.75, mir(passR), io], [len, contactR, io],
	], { speed: 0.09 });
	const runR = { thigh_r: [-48, 0, 2], shin_r: [18, 0, 0], foot_r: [20, 0, 0], thigh_l: [34, 0, -2], shin_l: [70, 0, 0], foot_l: [-10, 0, 0],
		hips: [0, -9, 0], waist: [16, 0, 0], chest: [6, 12, 0], arm_r: [40, 0, 10], arm_l: [-46, 0, -10], forearm_r: [-40, 0, 0], forearm_l: [-70, 0, 0], neck: [-18, -8, 0] };
	const runP = { thigh_r: [-6, 0, 2], shin_r: [30, 0, 0], foot_r: [-6, 0, 0], thigh_l: [-58, 0, -2], shin_l: [96, 0, 0], foot_l: [-20, 0, 0], waist: [18, 0, 0], chest: [6, 0, 0], arm_r: [8, 0, 10], arm_l: [-8, 0, -10], forearm_r: [-55, 0, 0], forearm_l: [-55, 0, 0], neck: [-18, 0, 0], $y: 1.2 };
	mk('run', len => [[0, runR], [len * 0.25, runP, io], [len * 0.5, mir(runR), io], [len * 0.75, mir(runP), io], [len, runR, io]], { speed: 0.2 });
	const hov = S({ thigh_r: [-10, 0, 4], thigh_l: [-4, 0, -4], shin_r: [24, 0, 0], shin_l: [16, 0, 0], foot_r: [26, 0, 0], foot_l: [22, 0, 0], arm_r: [-6, 0, 22], arm_l: [-6, 0, -22], forearm_r: [-20, 0, 0], forearm_l: [-20, 0, 0], waist: [6, 0, 0], $ground: false, $root: [0, 9, 0] });
	mk('jet_hover', len => [[0, hov], [len / 2, ap(hov, { chest: [-2, 0, 0], arm_r: [-4, 0, 3], arm_l: [-4, 0, -3], thigh_r: [-4, 0, 0], $root: [0, 10.2, 0] }), io], [len, hov, io]],
		{ scaleKeys: { jet_back: [[0, 1]], jet_r: [[0, 1]], jet_l: [[0, 1]] } });
	mk('jump', len => [
		[0, S()],
		[5, S(crouch(30), { arm_r: [34, 0, 12], arm_l: [34, 0, -12], forearm_r: [-30, 0, 0], forearm_l: [-30, 0, 0], chest: [10, 0, 0] }), oq],
		[9, S({ arm_r: [-60, 0, 14], arm_l: [-60, 0, -14], thigh_r: [8, 0, 0], thigh_l: [8, 0, 0], foot_r: [26, 0, 0], foot_l: [26, 0, 0], $ground: false, $root: [0, 6, 0] }), ob],
		[len, S({ arm_r: [-20, 0, 20], arm_l: [-20, 0, -20], thigh_r: [-30, 0, 0], thigh_l: [-24, 0, 0], shin_r: [50, 0, 0], shin_l: [44, 0, 0], $ground: false, $root: [0, 12, 0] }), oq],
	]);
	mk('land', (len, [h]) => [
		[h, strike(S(crouch(44), { chest: [18, 0, 0], arm_r: [-34, 0, 26], arm_l: [-34, 0, -26], forearm_r: [-40, 0, 0], forearm_l: [-40, 0, 0], neck: [-14, 0, 0] })), ic],
		[6, S(crouch(30), { chest: [10, 0, 0], arm_r: [-20, 0, 18], arm_l: [-20, 0, -18] }), oq],
		[len, S(), io],
	].map((f, i) => i === 0 ? [0, f[1]] : f));
	// --- punch combo: right jab, left hook, right uppercut
	const guard = S(crouch(10), { arm_r: [-28, 0, 10], arm_l: [-28, 0, -10], forearm_r: [-70, 0, 0], forearm_l: [-70, 0, 0], chest: [6, 0, 0] });
	const jabR = S(crouch(14), { arm_r: [-86, 8, 0], forearm_r: [-6, 0, 0], arm_l: [-26, 0, -12], forearm_l: [-80, 0, 0], chest: [10, 22, 0], waist: [4, 10, 0], hips: [0, 8, 0], thigh_r: [-24, 0, 0], neck: [-4, -18, 0] });
	const hookL = S(crouch(16), { arm_l: [-80, -30, -20], forearm_l: [-28, 0, 0], arm_r: [-26, 0, 12], forearm_r: [-82, 0, 0], chest: [10, -30, 0], waist: [4, -12, 0], hips: [0, -8, 0], thigh_l: [-24, 0, 0], neck: [-4, 22, 0] });
	const upR = S(crouch(8), { arm_r: [-150, 0, 6], forearm_r: [-22, 0, 0], arm_l: [-20, 0, -14], forearm_l: [-80, 0, 0], chest: [-12, 16, 0], waist: [-6, 6, 0], thigh_r: [-16, 0, 0], neck: [10, -10, 0] });
	mk('punch_combo', (len, [h1, h2, h3]) => [
		[0, S()], [3, ap(guard, { arm_r: [30, 0, 0], forearm_r: [-30, 0, 0], chest: [0, -16, 0] }), io],
		[h1, strike(jabR), ob], [h1 + 4, ap(guard, { chest: [0, 14, 0], arm_l: [20, 0, 0] }), io],
		[h2, strike(hookL), ob], [h2 + 4, ap(guard, { arm_r: [40, 0, 0], forearm_r: [-40, 0, 0], chest: [8, -10, 0] }, crouch(10)), io],
		[h3, strike(upR), ob], [h3 + 4, ap(upR, { arm_r: [10, 0, 0] }), oq], [len, S(), io],
	]);
	// --- rocket fist: wind-up, aim, the forearm launches (forearm_r scaled out while the fist flies), reattach
	const aim = S(crouch(12), { arm_r: [-90, 4, 0], forearm_r: [-2, 0, 0], arm_l: [-18, 0, -18], forearm_l: [-50, 0, 0], chest: [6, 20, 0], hips: [0, 8, 0], thigh_r: [-20, 0, 0], neck: [0, -16, 0] });
	mk('rocket_fist', (len, [h]) => [
		[0, S()], [8, S(crouch(10), { arm_r: [30, 0, 16], forearm_r: [-90, 0, 0], chest: [4, -24, 0], arm_l: [-40, 0, -10], forearm_l: [-40, 0, 0], neck: [0, 14, 0] }), io],
		[h, strike(aim), ob], [h + 6, ap(aim, { arm_r: [16, 0, 0], chest: [-6, -8, 0], $z: 2 }), oq],
		[34, ap(aim, { arm_r: [8, 0, 0] }), io], [40, ap(aim, { arm_r: [-4, 0, 0] }), io], [44, ap(aim, { arm_r: [12, 0, 0], chest: [-4, 0, 0] }), oq], [len, S(), io],
	], { scaleKeys: { forearm_r: [[0, 1], [12, 1], [13, 0], [34, 0], [36, 1, 'easeoutback']] } });
	// --- pile driver: both fists up, slam down
	const up2 = S({ arm_r: [-168, 0, -8], arm_l: [-168, 0, 8], forearm_r: [-30, 0, 0], forearm_l: [-30, 0, 0], chest: [-14, 0, 0], waist: [-6, 0, 0], neck: [12, 0, 0], thigh_r: [0, 0, 6], thigh_l: [0, 0, -6] });
	const slam = S(crouch(34), { arm_r: [-48, 0, -10], arm_l: [-48, 0, 10], forearm_r: [-10, 0, 0], forearm_l: [-10, 0, 0], chest: [34, 0, 0], waist: [14, 0, 0], neck: [-22, 0, 0], thigh_r: [0, 0, 6], thigh_l: [0, 0, -6] });
	mk('pile_driver', (len, [h]) => [[0, S()], [10, up2, io], [13, ap(up2, { arm_r: [-6, 0, 0], arm_l: [-6, 0, 0], chest: [-4, 0, 0] }), io], [h, strike(slam), ic], [h + 6, ap(slam, { chest: [-6, 0, 0] }), oq], [len, S(), io]]);
	// --- earthquake stomp
	const lift = S({ thigh_r: [-62, 0, 6], shin_r: [70, 0, 0], foot_r: [-6, 0, 0], thigh_l: [0, 0, -4], shin_l: [10, 0, 0], arm_r: [-20, 0, 36], arm_l: [-20, 0, -36], forearm_r: [-40, 0, 0], forearm_l: [-40, 0, 0], chest: [-6, 0, 0] });
	const stomp = S(crouch(20), { thigh_r: [-34, 0, 6], shin_r: [36, 0, 0], foot_r: [-2, 0, 0], arm_r: [-10, 0, 30], arm_l: [-10, 0, -30], forearm_r: [-30, 0, 0], forearm_l: [-30, 0, 0], chest: [20, 0, 0], neck: [-10, 0, 0] });
	mk('earthquake_stomp', (len, [h]) => [[0, S()], [8, lift, io], [h, strike(stomp), ic], [h + 6, ap(stomp, { chest: [-6, 0, 0] }), oq], [len, S(), io]]);
	// --- grab (hold), pound (held target slammed twice), throw
	const reach = S(crouch(12), { arm_r: [-84, 0, -6], forearm_r: [-8, 0, 0], hand_r: [0, 0, 0], chest: [12, 10, 0], arm_l: [-20, 0, -14], forearm_l: [-40, 0, 0] });
	const hold = S(crouch(6), { arm_r: [-70, 0, -4], forearm_r: [-48, 0, 0], hand_r: [-10, 0, 0], chest: [4, 6, 0], arm_l: [-20, 0, -14], forearm_l: [-40, 0, 0] });
	mk('grab', (len, [h]) => [[0, S()], [h, strike(reach), oq], [len, hold, io]]);
	const raise = S({ arm_r: [-160, 0, -4], forearm_r: [-20, 0, 0], chest: [-12, 4, 0], arm_l: [-20, 0, -18], forearm_l: [-40, 0, 0] });
	const pound = S(crouch(26), { arm_r: [-56, 0, -6], forearm_r: [-12, 0, 0], chest: [28, 6, 0], waist: [10, 0, 0], arm_l: [-24, 0, -20], forearm_l: [-40, 0, 0], neck: [-14, 0, 0] });
	mk('grab_pound', (len, [h1, h2]) => [[0, hold], [7, raise, io], [h1, strike(pound), ic], [h1 + 4, raise, io], [h2, strike(pound), ic], [len, hold, io]]);
	const windT = S(crouch(10), { arm_r: [30, 0, 30], forearm_r: [-40, 0, 0], chest: [-4, -34, 0], hips: [0, -10, 0], arm_l: [-50, 0, -10], forearm_l: [-30, 0, 0], neck: [0, 20, 0] });
	const relT = S(crouch(16), { arm_r: [-120, 0, -14], forearm_r: [-6, 0, 0], chest: [14, 30, 0], hips: [0, 12, 0], arm_l: [10, 0, -24], forearm_l: [-30, 0, 0], thigh_r: [-22, 0, 0], neck: [-6, -18, 0] });
	mk('grab_throw', (len, [h]) => [[0, hold], [7, windT, io], [h, strike(relT), ob], [h + 5, ap(relT, { arm_r: [50, 0, 0] }), oq], [len, S(), io]]);
	// --- chest beam: brace, chest forward, beam on from the strike to beam_end
	const brace = S(crouch(22), { thigh_r: [-22, 0, 14], thigh_l: [-22, 0, -14], arm_r: [26, 0, 40], arm_l: [26, 0, -40], forearm_r: [-46, 0, 0], forearm_l: [-46, 0, 0], chest: [-10, 0, 0], waist: [4, 0, 0], neck: [12, 0, 0] });
	const beam = ap(brace, { chest: [8, 0, 0], arm_r: [10, 0, 6], arm_l: [10, 0, -6], neck: [-6, 0, 0] });
	mk('chest_beam', (len, [h]) => [[0, S()], [h - 4, brace, io], [h, strike(beam), ob], [24, ap(beam, { chest: [3, 0, 0], $z: 1 }), io], [36, ap(beam, { chest: [-1, 0, 0], $z: 1.5 }), io], [38, beam, io], [44, brace, oq], [len, S(), io]],
		{ scaleKeys: { beam_fx: [[0, 0], [9, 0], [10, 1], [38, 1], [40, 0]] } });
	// --- missile swarm: shoulder pods open, launch, close
	const podsOpen = S(crouch(10), { pod_r: [-105, 0, 0], pod_l: [-105, 0, 0], arm_r: [-10, 0, 24], arm_l: [-10, 0, -24], forearm_r: [-40, 0, 0], forearm_l: [-40, 0, 0], chest: [-8, 0, 0], neck: [10, 0, 0] });
	mk('missile_swarm', (len, [h]) => [[0, S()], [8, podsOpen, ob], [h, strike(ap(podsOpen, { chest: [-6, 0, 0], shoulder_r: [0, 0, -4], shoulder_l: [0, 0, 4] })), oq], [h + 6, podsOpen, io], [28, podsOpen, io], [34, S(), io], [len, S(), io]]);
	// --- hydraulic brace: crouched guard, forearms crossed in front of the head
	mk('hydraulic_brace', len => [[0, S()], [len, S(crouch(30), { arm_r: [-78, -36, -8], arm_l: [-78, 36, 8], forearm_r: [-84, 0, 0], forearm_l: [-84, 0, 0], chest: [18, 0, 0], neck: [-12, 0, 0], thigh_r: [-30, 0, 10], thigh_l: [-30, 0, -10] }), oq]]);
	// --- replacement pod: arms and legs out while the pod clamps new parts on
	const spread = S({ arm_r: [0, 0, 70], arm_l: [0, 0, -70], forearm_r: [-6, 0, 0], forearm_l: [-6, 0, 0], thigh_r: [0, 0, 14], thigh_l: [0, 0, -14], neck: [14, 0, 0] });
	mk('replacement_pod', len => [[0, S()], [10, spread, io], [18, ap(spread, { arm_r: [0, 0, 4], arm_l: [0, 0, -4] }), io], [22, ap(spread, { chest: [3, 0, 0], arm_r: [0, 0, -6], arm_l: [0, 0, 6] }), oq], [30, spread, io], [len, S(), io]]);
	// --- sentry: the empty suit standing, back power pack swung open; then it closes up
	const empty = S(crouch(6), { backpack: [-72, 0, 0], neck: [24, 0, 0], chest: [10, 0, 0], arm_r: [-6, 0, 4], arm_l: [-6, 0, -4], forearm_r: [-24, 0, 0], forearm_l: [-24, 0, 0] });
	mk('sentry_idle', len => [[0, empty], [len / 2, ap(empty, { backpack: [-4, 0, 0], chest: [1, 0, 0] }), io], [len, empty, io]]);
	mk('sentry_close', len => [[0, empty], [10, ap(empty, { backpack: [40, 0, 0] }), io], [14, ap(S(), { backpack: [-4, 0, 0] }), io], [len, S(), oq]]);
	return an;
}

// ================================================================ preview sheets (rendered from the written game assets)
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
function hiddenFilter(GM, hidden) {
	const H = new Set(hidden || []);
	const anc = b => { for (let x = GM.B[b]; x; x = x.parent ? GM.B[x.parent] : null) if (H.has(x.name)) return true; return false; };
	return c => anc(c.bone);
}
function sheet(file, title, sub, panels, footer) {
	const PH = 1100, TITLE = 84, PAD = 0.08;
	const fits = panels.map(pn => { pn.faces = L.gather(pn.items); return L.projBounds(pn.faces, pn.cam, [[0, 0, 0]]); });
	let ppu = Infinity;
	fits.forEach((b, i) => { if (!panels[i].ownScale) ppu = Math.min(ppu, PH * (1 - 2 * PAD) / (b.hi[1] - b.lo[1])); });
	if (!isFinite(ppu)) ppu = Math.min(...fits.map(b => PH * (1 - 2 * PAD) / (b.hi[1] - b.lo[1])));
	const widths = fits.map((b, i) => {
		const k = panels[i].ownScale ? Math.min(ppu, PH * (1 - 2 * PAD) / (b.hi[1] - b.lo[1])) : ppu;
		panels[i].ppu = k;
		return Math.max(420, Math.round((b.hi[0] - b.lo[0]) * k + PH * 2 * PAD * 1.1));
	});
	const total = widths.reduce((a, b) => a + b, 0);
	const cv = new L.Canvas(total, PH + TITLE, [34, 36, 42]);
	let x = 0;
	panels.forEach((pn, i) => {
		const b = fits[i], k = pn.ppu, w = widths[i];
		const origin = [w / 2 - (b.lo[0] + b.hi[0]) / 2 * k, PH / 2 + (b.lo[1] + b.hi[1]) / 2 * k + 24];
		const img = L.renderPanel({ items: pn.items, faces: pn.faces, W: w, H: PH, cam: pn.cam, ppu: k, origin, ss: 2, light: LIGHT, floor: [{ x: 0, z: 0, r: 2.2 }],
			bg: { top: [132, 136, 146], bottom: [80, 84, 94] }, bloom: 0, post: bloomPost(PH * 0.01, PH * 0.04, 0.85, 0.55), aoR: 0.12, edgeThr: 0.04, edgeK: 0.7, shadowSoft: 0.14, contactH: 0.3 });
		cv.blit(img, w, PH, x, TITLE);
		if (i > 0) cv.rect(x, TITLE, 2, PH, [30, 32, 38]);
		cv.text(pn.label, x + w / 2, TITLE + 18, 3, [236, 240, 246], { align: 'center' });
		x += w;
	});
	cv.rect(0, 0, total, TITLE, [26, 28, 34]);
	cv.text(title, 28, 18, 5, [240, 244, 250]);
	if (sub) cv.text(sub, 28 + title.length * 30 + 28, 30, 3, [160, 190, 220]);
	if (footer) cv.text(footer, total - 24, TITLE + PH - 34, 2, [214, 220, 230], { align: 'right' });
	cv.save(file);
	console.log(`wrote ${file} (${total}x${PH + TITLE})`);
}

// ================================================================ main
function main() {
	const { tex, glow } = buildTexture();
	const M = buildModel();
	const an = buildClips(M);
	const anim = an.build();   // throws if a strike is off its tick or a never-keyed bone is keyed
	// every authored strike must sit exactly on (hit - TRANSITION)
	for (const [name, t] of Object.entries(TIMING)) for (const h of t.hit || []) {
		const c = an.clips[name]; const f = c.frames.find(fr => fr.strike && fr.t === h - T);
		if (!f) throw new Error(`${name}: strike for hit ${h} is not on clip tick ${h - T}`);
	}
	fs.mkdirSync(path.dirname(GEO), { recursive: true }); fs.mkdirSync(path.dirname(TEX), { recursive: true }); fs.mkdirSync(path.dirname(ANIM), { recursive: true });
	fs.writeFileSync(GEO, JSON.stringify(M.geoJson(`geometry.${ID}`), null, '\t') + '\n');
	fs.writeFileSync(TEX, L.encodePng(64, 64, tex));
	fs.writeFileSync(GLOW, L.encodePng(64, 64, glow));
	fs.writeFileSync(ANIM, JSON.stringify(anim, null, '\t') + '\n');
	const timingOut = { model: ID, transition: T, height_blocks: +(M.bounds(M.world({})).hi[1] / 16).toFixed(2), never_keyed: ['head', 'flinch'], effect_bones: ['jet_back', 'jet_r', 'jet_l', 'beam_fx'], clips: {} };
	for (const [name, t] of Object.entries(TIMING)) timingOut.clips[name] = Object.assign({ kind: t.kind, length: t.length, clip_length: t.kind === 'loop' ? t.length : t.length - T }, t.hit ? { hit: t.hit, strike_keyframes: t.hit.map(h => h - T) } : {}, t.events ? { events: t.events } : {}, an.clips[name].speed ? { move_speed: an.clips[name].speed } : {});
	fs.writeFileSync(TIMING_OUT, JSON.stringify(timingOut, null, '\t') + '\n');
	fs.mkdirSync(path.join(PREVIEW, 'skins'), { recursive: true });
	fs.writeFileSync(path.join(PREVIEW, 'skins', 'mark_8.png'), L.encodePng(64, 64, tex));
	fs.writeFileSync(path.join(PREVIEW, 'skins', 'mark_8_glowmask.png'), L.encodePng(64, 64, glow));
	console.log(`${ID}: ${M.bones.length} bones, ${M.cubes().length} cubes, ${Object.keys(anim.animations).length} clips, ${timingOut.height_blocks} blocks`);

	// ---- previews from the real files
	const GM = G.loadGameModel(GEO, TEX, GLOW, 1, true);
	GM.heightBlocks = 3;
	const anims = G.loadAnimations(ANIM);
	const at = (clip, tick) => G.poseAt(anims, `animation.${ID}.${clip}`, tick);
	const item = (clip, tick) => { const p = at(clip, tick); return { model: GM, pose: p, root: p.$root, hide: hiddenFilter(GM, p.$hidden) }; };
	const idle = item('idle', 0);
	sheet(path.join(PREVIEW, '09_mark8_hulkbuster.png'), 'MARK 8 - HULKBUSTER', 'SEGMENTED GECKOLIB BODY - PMC SKIN 4269320 - ABOUT ' + timingOut.height_blocks + ' BLOCKS', [
		{ label: 'FRONT', items: [idle], cam: { yaw: 0, pitch: 5 } },
		{ label: 'THREE-QUARTER', items: [idle], cam: { yaw: 35, pitch: 5 } },
		{ label: 'SIDE', items: [idle], cam: { yaw: 90, pitch: 5 } },
		{ label: 'BACK', items: [idle], cam: { yaw: 180, pitch: 5 } },
		{ label: 'PUNCH COMBO (JAB)', items: [item('punch_combo', TIMING.punch_combo.hit[0] - T)], cam: { yaw: -50, pitch: 2 }, ownScale: true },
		{ label: 'PILE DRIVER', items: [item('pile_driver', TIMING.pile_driver.hit[0] - T)], cam: { yaw: 40, pitch: 2 }, ownScale: true },
		{ label: 'CHEST BEAM', items: [item('chest_beam', 20)], cam: { yaw: -55, pitch: 2 }, ownScale: true },
	], 'PAULDRONS + MISSILE PODS - HUGE FISTS + KNUCKLE PLATES - ELBOW / HIP / KNEE PISTONS - BACK THRUSTERS');
	sheet(path.join(PREVIEW, '09b_hulkbuster_anims.png'), 'HULKBUSTER - ANIMATION FRAMES', 'POSES READ BACK FROM THE ANIMATION JSON', [
		{ label: 'IDLE', items: [item('idle', 30)], cam: { yaw: 30, pitch: 4 }, ownScale: true },
		{ label: 'WALK MID-STRIDE', items: [item('walk', 0)], cam: { yaw: 70, pitch: 4 }, ownScale: true },
		{ label: 'PUNCH IMPACT', items: [item('punch_combo', TIMING.punch_combo.hit[2] - T)], cam: { yaw: -45, pitch: 2 }, ownScale: true },
		{ label: 'ROCKET FIST LAUNCH', items: [item('rocket_fist', 20)], cam: { yaw: -60, pitch: 3 }, ownScale: true },
		{ label: 'PILE DRIVER IMPACT', items: [item('pile_driver', TIMING.pile_driver.hit[0] - T)], cam: { yaw: 60, pitch: 3 }, ownScale: true },
		{ label: 'GRAB', items: [item('grab', TIMING.grab.hit[0] - T)], cam: { yaw: -50, pitch: 3 }, ownScale: true },
		{ label: 'CHEST BEAM', items: [item('chest_beam', 24)], cam: { yaw: -60, pitch: 2 }, ownScale: true },
		{ label: 'MISSILE SWARM', items: [item('missile_swarm', 14)], cam: { yaw: 30, pitch: 8 }, ownScale: true },
		{ label: 'JET HOVER', items: [item('jet_hover', 0)], cam: { yaw: 30, pitch: 3 }, ownScale: true },
		{ label: 'SENTRY (BACK OPEN)', items: [item('sentry_idle', 0)], cam: { yaw: 150, pitch: 6 }, ownScale: true },
	]);
	// print the timing table
	console.log('\nclip               kind  len  clip  hits (strike keyframes)');
	for (const [n, t] of Object.entries(TIMING)) console.log(`${n.padEnd(18)} ${t.kind.padEnd(5)} ${String(t.length).padStart(3)}  ${String(t.kind === 'loop' ? t.length : t.length - T).padStart(4)}  ${(t.hit || []).map(h => `${h} (${h - T})`).join(', ')}${t.events ? '  ' + JSON.stringify(t.events) : ''}`);
}
main();
