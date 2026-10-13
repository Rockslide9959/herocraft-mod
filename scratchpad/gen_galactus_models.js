// GALACTUS: THE DEVOURER world event: CONCEPT models (nothing wired into the game; the user approves the looks first).
//   Galactus (60 blocks, Jack Kirby's classic armour), the Silver Surfer + his board, the Punisher (Galactus's robot
//   sentinel), the Element Converter (with its ring of consumed ground), the Worldship (sky backdrop prop) and the
//   Ultimate Nullifier (hand-held artifact). Painted procedurally (no copied textures).
//
// Usage (from the repo root):
//   node scratchpad/gen_galactus_models.js [--concept <outDir>] [--only galactus,surfer,punisher,converter,worldship,nullifier,board,lineup] [--no-assets]
//   node scratchpad/gen_galactus_models.js --game [--no-previews]   (GAME EXPORT into src/main/resources, see the section below)
// Default outDir is <main checkout>/scratchpad/concepts_galactus. Writes one concept sheet per subject (>= 1800 px wide)
// plus a lineup at true scale; also writes each model's geo / texture / glowmask / poses into <outDir>/assets
// (never into src/), so the same rigs can be exported to the game later.
//
// Conventions (Bedrock geo / GeckoLib, shared with lib_concept_models.js): +Y up, the model faces -Z, the entity's
// RIGHT side is -X. Rotation X > 0 tips a bone's top forward (a hanging limb's free end swings BACK); rotation Z > 0
// swings a hanging limb toward -X (the right). Pose rotations are ADDED to a bone's rest rotation. Model pixels.
'use strict';
const path = require('path');
const fs = require('fs');
const L = require('./lib_concept_models.js');
const { Model, mix, mul, fbm, vnoise, hash3, clamp, smooth, seeded, vnorm, vadd, vsub, vsc, vlen, vdot, mv3, affApply } = L;

const args = process.argv.slice(2);
const ci = args.indexOf('--concept');
const REPO = path.join(__dirname, '..');
// worktrees live in <main>/.claude/worktrees/<name>; the concept folder lives in the MAIN checkout's scratchpad
const MAIN = /[\\/]\.claude[\\/]worktrees[\\/]/.test(REPO) ? path.join(REPO, '..', '..', '..') : REPO;
const OUT = ci >= 0 ? args[ci + 1] : path.join(MAIN, 'scratchpad', 'concepts_galactus');
const oi = args.indexOf('--only');
const ONLY = oi >= 0 ? args[oi + 1].split(',') : null;
const GAME = args.includes('--game');
const WANT = n => !GAME && (!ONLY || ONLY.includes(n));
const ASSETS = !args.includes('--no-assets');
const DEGR = Math.PI / 180;

// ================================================================ ANIMATION TIMING TABLE (game export, --game)
// The clip lengths and hit ticks are the GAME's: com.projecthero.mod.galactus.GalactusAnims (the server's moves land
// their damage on those ticks). --game parses its clip(...) / hold(...) / loop(...) lines (pose(...) lines are the
// Surfer's code-driven poses and are skipped), authors every clip to that table and FAILS if a strike keyframe is off
// its tick (a play-once / held clip is (length - TRANSITION) ticks long and keys its strike on (hit - TRANSITION); loops
// are `length` ticks), or if a clip in the table was not authored / an authored clip is not in the table. It also
// writes scratchpad/galactus_anim_timing.json.
const GAL_TRANSITION = 4;
const GAL_ANIMS_JAVA = 'src/main/java/com/projecthero/mod/galactus/GalactusAnims.java';
const PUNISHER_WALK_SPEED = 0.12; // blocks/tick the walk stride is matched to (MOVEMENT_SPEED 0.24 walking)

// ================================================================ palettes
const PC_WHITE = [236, 246, 255];          // Power Cosmic core
const PC_GLOW = [140, 196, 255];           // Power Cosmic halo
const PC_VIOLET = [190, 150, 255];
const GAL = {
	purple: [104, 56, 162], purpleD: [70, 36, 120], magenta: [138, 58, 172],
	blue: [42, 82, 178], blueD: [26, 50, 120], blueL: [128, 166, 232],
	face: [150, 152, 164],
};
const PUN = { teal: [70, 124, 126], tealD: [42, 80, 84], grey: [138, 144, 150], greyD: [74, 80, 88], orange: [244, 132, 36], orangeGlow: [255, 150, 50] };
const ORANGE_GLOW = [255, 140, 40];

// ================================================================ painting helpers
const bevel = (col, info, k1 = 0.8, k2 = 0.93) => info.ePx < 0.3 ? mul(col, k1) : info.ePx < 0.62 ? mul(col, k2) : col;
const vgrad = (col, f, t, k = 0.2) => f === 'up' ? mul(col, 1.1) : f === 'down' ? mul(col, 0.6) : mul(col, 1.06 - k * t);
const has = (c, w) => !!(c.tag && c.tag.split(' ').includes(w));
function segDist(px, py, ax, ay, bx, by) {
	const dx = bx - ax, dy = by - ay; const l2 = dx * dx + dy * dy || 1;
	const t = clamp(((px - ax) * dx + (py - ay) * dy) / l2, 0, 1);
	return Math.hypot(px - ax - dx * t, py - ay - dy * t);
}
// face-local coordinates in model px: u across (0..fw), v down (0..fh)
const UV = (s, t, info) => [s * info.fw, t * info.fh];

// Polished comic armour plate: clean colour, bright bevelled rim, dark seam inside the rim, optional Kirby details
// selected by the cube's tag: 'panel' (inset panel line), 'ribs' (horizontal segment grooves), 'vrib' (vertical
// grooves), 'circles' (a row of Kirby discs), 'dots' (rivet row), 'slots' (glowing vertical slots), 'chev' (chevrons).
function plate(base, c, f, s, t, p, info, glowCol = PC_GLOW) {
	const n = fbm(p[0] * 0.21 + c.seed, p[1] * 0.21, p[2] * 0.21, 3);
	let col = mul(base, 0.9 + 0.2 * n);
	col = mul(col, 0.985 + 0.03 * hash3(info.i, info.j, c.seed + 7));
	col = vgrad(col, f, t, 0.22);
	let g = null;
	const [u, v] = UV(s, t, info);
	const side = f !== 'up' && f !== 'down';
	const big = info.fw > 3.2 && info.fh > 3.2;
	if (has(c, 'panel') && big) {
		const d = info.ePx;
		if (Math.abs(d - 1.25) < 0.17) col = mul(col, 0.55);
		else if (Math.abs(d - 1.5) < 0.13) col = mix(col, [255, 255, 255], 0.22);
	}
	if (has(c, 'ribs') && side) {
		const per = c.ribPer || 2.6;
		const q = ((p[1] % per) + per) % per / per;
		if (q < 0.12) col = mul(col, 0.58); else if (q < 0.24) col = mix(col, [255, 255, 255], 0.2); else if (q > 0.82) col = mul(col, 0.86);
	}
	if (has(c, 'vrib') && side && info.fw > 2) {
		const per = c.ribPer || 2.4;
		const q = ((u % per) + per) % per / per;
		if (q < 0.12) col = mul(col, 0.62); else if (q < 0.22) col = mix(col, [255, 255, 255], 0.18);
	}
	if (has(c, 'circles') && side && big) {
		const r = Math.min(info.fh * 0.22, 2.6), nC = Math.max(1, Math.floor(info.fw / (r * 2.8)));
		const cx = (Math.floor(u / (info.fw / nC)) + 0.5) * (info.fw / nC), cy = info.fh * (c.circY || 0.5);
		const d = Math.hypot(u - cx, v - cy);
		if (d < r) {
			if (d > r * 0.78) col = mul(col, 0.45);
			else if (d > r * 0.62) col = mix(col, [255, 255, 255], 0.3);
			else col = mix(mul(col, 0.7), [255, 255, 255], 0.1 * (1 - d / r));
			if (has(c, 'glowdisc') && d < r * 0.5) { col = mix(PC_WHITE, glowCol, d / (r * 0.5)); g = glowCol; }
		}
	}
	if (has(c, 'dots') && side && info.fh > 1.4) {
		const per = 1.8; const cx = (Math.floor(u / per) + 0.5) * per;
		if (Math.hypot(u - cx, v - info.fh * 0.5) < 0.32) col = mul(col, 0.5);
	}
	if (has(c, 'slots') && side && info.fw > 2.5 && info.fh > 3) {
		const per = c.slotPer || 2.4; const q = ((u + per * 0.5) % per) - per / 2;
		if (Math.abs(q) < 0.32 && v > info.fh * 0.18 && v < info.fh * 0.82) { col = mix(PC_WHITE, glowCol, 0.4); g = glowCol; }
		else if (Math.abs(q) < 0.55 && v > info.fh * 0.15 && v < info.fh * 0.85) col = mul(col, 0.45);
	}
	if (has(c, 'chev') && f === 'north') {
		const x = u - info.fw / 2;
		const per = c.chevPer || 3; const q = (((v - Math.abs(x) * 0.6) % per) + per) % per;
		if (q < 0.35) col = mul(col, 0.6); else if (q < 0.6) col = mix(col, [255, 255, 255], 0.18);
	}
	if (has(c, 'stripe') && side) { // a vertical inset stripe of the secondary colour down the middle of a face
		const x = Math.abs(u - info.fw / 2);
		if (x < info.fw * 0.18) col = mix(col, c.stripeCol || GAL.blue, 0.85);
		else if (x < info.fw * 0.18 + 0.25) col = mul(col, 0.55);
	}
	// rim: a thin bright worn edge, then a dark seam
	if (info.ePx < 0.28) col = mix(col, [255, 255, 255], 0.28);
	else if (info.ePx < 0.55) col = mul(col, 0.78);
	return [col, g];
}

// A comic chrome: mostly bright silver with dark curved "sweep" reflections (the way Buscema/Kirby ink the Surfer),
// strong white rims; up faces see the sky, down faces see the ground.
function chrome(c, f, s, t, p, info, tint = [1, 1, 1]) {
	// a smooth environment gradient per face (sky above, dark horizon band, ground below), softly warped
	const warp = (fbm(p[0] * 0.12 + 3.1 + c.seed * 0.013, p[1] * 0.07, p[2] * 0.12 + 1.7, 3) - 0.5) * 0.7;
	const side = f !== 'up' && f !== 'down';
	const hz = side ? t + warp : 0.2 + warp;               // 0 = top of the face
	let col;
	if (hz < 0.42) col = mix([246, 249, 255], [196, 206, 224], hz / 0.42);
	else if (hz < 0.58) col = mix([196, 206, 224], [96, 106, 134], (hz - 0.42) / 0.16);   // the horizon line reflected
	else col = mix([120, 128, 150], [168, 174, 188], Math.min(1, (hz - 0.58) / 0.42));
	const sw = fbm(p[0] * 0.18 + 9, p[1] * 0.1, p[2] * 0.18, 3);
	if (side && info.fh > 4 && info.fw > 2.5 && Math.abs(sw - 0.5) < 0.008) col = mix(col, [40, 46, 66], 0.55); // a thin inked sweep
	if (f === 'up') col = mix(col, [250, 252, 255], 0.55);
	if (f === 'down') col = mix(col, [86, 92, 116], 0.6);
	col = col.map((v, i) => v * tint[i]);
	if (info.ePx < 0.26) col = mix(col, [255, 255, 255], 0.4);
	else if (info.ePx < 0.5) col = mul(col, 0.93);
	return col;
}

// The bold Kirby "G" in a unit square (u right, v down); returns 1 on a stroke.
function gMask(u, v) {
	const w = 0.21;
	if (u < 0 || u > 1 || v < 0 || v > 1) return 0;
	if (v < w) return u < 0.94 ? 1 : 0;
	if (u < w) return 1;
	if (v > 1 - w) return 1;
	if (u > 1 - w && v > 0.46) return 1;
	if (v > 0.46 && v < 0.46 + w * 0.9 && u > 0.48) return 1;
	if (u > 1 - w && v < w + 0.1) return 1; // the serif at the top right
	return 0;
}

// ================================================================ rig: a generic humanoid (bones + base cubes)
// Bones: root > hips > (thigh > shin > foot) x2, hips > waist > chest > neck > head, chest > shoulder > arm >
// forearm > hand > (fingers > fingertips, thumb) x2. `mats` names the material of every base part.
const SIDES = [[-1, 'r'], [1, 'l']];
function humanoid(M, P) {
	const A = M.A;
	const ankleY = P.footH, kneeY = ankleY + P.shinLen, hipY = kneeY + P.thighLen;
	const pelvisTop = hipY + P.pelvisH * 0.55;
	const chestBot = pelvisTop + P.waistH, chestTop = chestBot + P.chestH;
	const neckTop = chestTop + P.neckH, headBot = neckTop - (P.headSink || 1), headTop = headBot + P.headH;
	const shX = P.chestW / 2 + P.armW / 2 - (P.shIn || 1), shY = chestTop - P.armW * 0.55;
	const elbowY = shY - P.upperLen, wristY = elbowY - P.foreLen, knuckY = wristY - P.handLen;
	Object.assign(A, { ankleY, kneeY, hipY, pelvisTop, chestBot, chestTop, neckTop, headBot, headTop, shX, shY, elbowY, wristY, knuckY, legX: P.legX });
	const m = k => P.mats[k] || P.mats.def;
	const root = M.bone('root', null, [0, 0, 0]);
	const hips = M.bone('hips', 'root', [0, hipY, 0]);
	M.box(hips, -P.pelvisW / 2, hipY - P.pelvisH * 0.45, -P.pelvisD / 2, P.pelvisW / 2, pelvisTop, P.pelvisD / 2, m('pelvis'), { paint: 'pelvis' });
	for (const [s, n] of SIDES) {
		const lx = s * P.legX;
		const thigh = M.bone(`thigh_${n}`, 'hips', [lx, hipY, 0], [0, 0, -s * (P.legOut || 1)]);
		M.box(thigh, lx - P.thighW / 2, kneeY - 0.5, -P.thighD / 2, lx + P.thighW / 2, hipY + 1, P.thighD / 2, m('thigh'), { paint: 'thigh' });
		const shin = M.bone(`shin_${n}`, `thigh_${n}`, [lx, kneeY, 0]);
		M.box(shin, lx - P.shinW / 2, ankleY - 0.3, -P.shinD / 2, lx + P.shinW / 2, kneeY + 0.4, P.shinD / 2, m('shin'), { paint: 'shin' });
		const foot = M.bone(`foot_${n}`, `shin_${n}`, [lx, ankleY, 0]);
		M.box(foot, lx - P.footW / 2, 0, -P.footLen * 0.66, lx + P.footW / 2, ankleY + 0.3, P.footLen * 0.34, m('foot'), { paint: 'foot', tag: 'sole' });
	}
	const waist = M.bone('waist', 'hips', [0, pelvisTop, 0]);
	M.box(waist, -P.waistW / 2, pelvisTop - 0.6, -P.waistD / 2, P.waistW / 2, chestBot + 0.6, P.waistD / 2, m('waist'), { paint: 'waist' });
	// game rig: `flinch` (code-driven hit flinch) sits between the waist and the chest; `neck` and `head` are the code-driven
	// look, so the clips nod the head through the keyable `nod` bone between them
	M.bone('flinch', 'waist', [0, chestBot, 0]);
	const chest = M.bone('chest', 'flinch', [0, chestBot, 0]);
	M.box(chest, -P.chestW / 2, chestBot, -P.chestD / 2, P.chestW / 2, chestTop, P.chestD / 2, m('chest'), { paint: 'chest' });
	const neck = M.bone('neck', 'chest', [0, chestTop, 0]);
	M.box(neck, -P.neckW / 2, chestTop - 1, -P.neckW / 2, P.neckW / 2, neckTop + 0.5, P.neckW / 2, m('neck'), { paint: 'neck' });
	M.bone('nod', 'neck', [0, neckTop, 0]);
	const head = M.bone('head', 'nod', [0, neckTop, 0]);
	M.box(head, -P.headW / 2, headBot, -P.headD / 2, P.headW / 2, headTop, P.headD / 2, m('head'), { paint: 'face' });
	for (const [s, n] of SIDES) {
		const sx = s * shX;
		M.bone(`shoulder_${n}`, 'chest', [s * shX * 0.4, chestTop - 1, 0]);
		const ua = M.bone(`arm_${n}`, `shoulder_${n}`, [sx, shY, 0], [0, 0, -s * (P.armOut || 4)]);
		M.box(ua, sx - P.armW / 2, elbowY - 0.4, -P.armD / 2, sx + P.armW / 2, shY + P.armW * 0.45, P.armD / 2, m('arm'), { paint: 'arm' });
		const fa = M.bone(`forearm_${n}`, `arm_${n}`, [sx, elbowY, 0], [-6, 0, 0]);
		M.box(fa, sx - P.foreW / 2, wristY - 0.2, -P.foreW / 2, sx + P.foreW / 2, elbowY + 0.4, P.foreW / 2, m('fore'), { paint: 'fore' });
		const hand = M.bone(`hand_${n}`, `forearm_${n}`, [sx, wristY, 0]);
		const hT = P.handT / 2, hW = P.handW / 2;
		M.box(hand, sx - hT, knuckY, -hW, sx + hT, wristY + 0.3, hW, m('hand'), { paint: 'palm' });
		const fing = M.bone(`fingers_${n}`, `hand_${n}`, [sx, knuckY + 0.2, 0]);
		const tips = M.bone(`fingertips_${n}`, `fingers_${n}`, [sx, knuckY - P.fingerLen * 0.5, 0]);
		const fz = [-0.75, -0.25, 0.25, 0.75].map(v => v * hW * 2 * 0.98), fl = [0.95, 1.05, 1.0, 0.82];
		const fwid = hW * 2 / 4 * 0.86;
		fz.forEach((z, i) => {
			const len = P.fingerLen * fl[i];
			M.box(fing, sx - hT * 0.82, knuckY - len * 0.52, z - fwid / 2, sx + hT * 0.82, knuckY + 0.3, z + fwid / 2, m('finger'), { paint: 'finger' });
			M.box(tips, sx - hT * 0.74, knuckY - P.fingerLen * 0.5 - len * 0.5, z - fwid * 0.46, sx + hT * 0.74, knuckY - P.fingerLen * 0.5 + 0.25, z + fwid * 0.46, m('finger'), { paint: 'fingertip' });
		});
		const th = M.bone(`thumb_${n}`, `hand_${n}`, [sx - s * hT * 0.3, wristY - P.handLen * 0.35, -hW + fwid * 0.3]);
		M.stick(th, [sx - s * hT * 0.3, wristY - P.handLen * 0.35, -hW + fwid * 0.3], [-s * 0.25, -0.8, -0.55], P.fingerLen * 0.85, fwid * 1.05, m('finger'), { paint: 'finger' });
	}
	return M;
}
const FIST = n => { const s = n === 'r' ? -1 : 1; return { [`fingers_${n}`]: [0, 0, s * 95], [`fingertips_${n}`]: [0, 0, s * 100], [`thumb_${n}`]: [0, 0, s * 40] }; };
const OPEN = (n, k = 1) => { const s = n === 'r' ? -1 : 1; return { [`fingers_${n}`]: [0, 0, s * 12 * k], [`fingertips_${n}`]: [0, 0, s * 16 * k] }; };
const BACKBEND = (n, k = 1) => { const s = n === 'r' ? -1 : 1; return { [`fingers_${n}`]: [0, 0, -s * 14 * k], [`fingertips_${n}`]: [0, 0, -s * 6 * k], [`thumb_${n}`]: [0, 0, -s * 30 * k] }; };

// Feet planted: root offset that puts the lowest foot cube on y = 0.
function planted(M, pose, extra = 0) {
	const W = M.world(pose);
	const lo = M.bounds(W, c => /^foot/.test(c.bone) || c.tag === 'sole').lo[1];
	return [0, -lo + extra, 0];
}
// World position (blocks) of a point given in a bone's rest coordinates, for a render item.
function wpt(item, bone, pt) {
	const M = item.model;
	const W = M.world(item.pose || {}, item.root || [0, 0, 0]);
	const q = affApply(W[bone], pt);
	const R = L.Ry((item.yaw || 0) * DEGR);
	return vadd(mv3(R, vsc(q, item.scale || M.k)), item.pos || [0, 0, 0]);
}
// a box with chamfered vertical edges (two crossed boxes): reads rounder at no texture cost
function cham(M, b, x0, y0, z0, x1, y1, z1, k, mat, opts = {}) {
	M.box(b, x0 + k, y0, z0, x1 - k, y1, z1, mat, opts);
	M.box(b, x0, y0, z0 + k, x1, y1, z1 - k, mat, opts);
	// a big chamfer would leave a plus shape: fill the corners back in with a third, inset box (reads as an octagon)
	if (k > 0.18 * Math.min(x1 - x0, z1 - z0)) M.box(b, x0 + k * 0.45, y0 + 0.01, z0 + k * 0.45, x1 - k * 0.45, y1 - 0.01, z1 - k * 0.45, mat, opts);
}
// a disc facing -Z (an octagon from two crossed boxes)
function discZ(M, b, ctr, r, d, mat, opts = {}) {
	const k = r * 0.42;
	M.box(b, ctr[0] - r, ctr[1] - r + k, ctr[2] - d / 2, ctr[0] + r, ctr[1] + r - k, ctr[2] + d / 2, mat, opts);
	M.box(b, ctr[0] - r + k, ctr[1] - r, ctr[2] - d / 2 - 0.02, ctr[0] + r - k, ctr[1] + r, ctr[2] + d / 2 - 0.02, mat, opts);
}
function discX(M, b, ctr, r, d, mat, opts = {}) {
	const k = r * 0.42;
	M.box(b, ctr[0] - d / 2, ctr[1] - r + k, ctr[2] - r, ctr[0] + d / 2, ctr[1] + r - k, ctr[2] + r, mat, opts);
	M.box(b, ctr[0] - d / 2 - 0.02, ctr[1] - r, ctr[2] - r + k, ctr[0] + d / 2 - 0.02, ctr[1] + r, ctr[2] + r - k, mat, opts);
}

// ================================================================ screen-space effects (render pre/post hooks)
// A thick glowing beam between two world points; respects the depth buffer so it passes behind geometry.
function beam(ctx, A, B, r, core = PC_WHITE, glow = PC_GLOW, opts = {}) {
	const a = ctx.proj(A), b = ctx.proj(B);
	const R = Math.max(1.5, r * ctx.ppu);
	const ext = R * 2.6;
	const x0 = Math.max(0, Math.floor(Math.min(a[0], b[0]) - ext)), x1 = Math.min(ctx.W - 1, Math.ceil(Math.max(a[0], b[0]) + ext));
	const y0 = Math.max(0, Math.floor(Math.min(a[1], b[1]) - ext)), y1 = Math.min(ctx.H - 1, Math.ceil(Math.max(a[1], b[1]) + ext));
	const dx = b[0] - a[0], dy = b[1] - a[1], l2 = dx * dx + dy * dy || 1;
	const taper = opts.taper || 0;
	for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
		const tt = clamp(((x - a[0]) * dx + (y - a[1]) * dy) / l2, 0, 1);
		const d = Math.hypot(x - a[0] - dx * tt, y - a[1] - dy * tt);
		const Rt = R * (1 - taper * tt) * (1 + 0.12 * Math.sin(tt * 40 + (opts.phase || 0)));
		if (d > Rt * 2.6) continue;
		const i = y * ctx.W + x;
		const z = a[2] + (b[2] - a[2]) * tt;
		if (ctx.zb[i] !== -Infinity && z < ctx.zb[i] - R) continue;
		const j = i * 3;
		let k, cc;
		if (d < Rt * 0.5) { k = 1; cc = core; }
		else { const q = (d - Rt * 0.5) / (Rt * 2.1); k = Math.pow(1 - q, 2.2) * 0.9; cc = mix(core, glow, Math.min(1, q * 2.5)); }
		for (let qq = 0; qq < 3; qq++) ctx.col[j + qq] = ctx.col[j + qq] * (1 - k) + cc[qq] * k;
		const ek = (opts.emi == null ? 0.75 : opts.emi) * k;
		ctx.emi[j] = Math.max(ctx.emi[j], glow[0] * ek); ctx.emi[j + 1] = Math.max(ctx.emi[j + 1], glow[1] * ek); ctx.emi[j + 2] = Math.max(ctx.emi[j + 2], glow[2] * ek);
	}
}
// A radial flare (soft disc) at a world point.
function flare(ctx, P, r, core = PC_WHITE, glow = PC_GLOW, k0 = 1, depth = false) {
	const c = ctx.proj(P); const R = Math.max(2, r * ctx.ppu);
	for (let y = Math.max(0, Math.floor(c[1] - R)); y < Math.min(ctx.H, c[1] + R); y++) for (let x = Math.max(0, Math.floor(c[0] - R)); x < Math.min(ctx.W, c[0] + R); x++) {
		const d = Math.hypot(x - c[0], y - c[1]) / R; if (d >= 1) continue;
		const i = y * ctx.W + x;
		if (depth && ctx.zb[i] !== -Infinity && c[2] < ctx.zb[i] - R * 0.5) continue;
		const j = i * 3; const k = Math.pow(1 - d, 2) * k0;
		const cc = mix(core, glow, d * 1.4);
		for (let q = 0; q < 3; q++) ctx.col[j + q] = ctx.col[j + q] * (1 - k) + cc[q] * k;
		ctx.emi[j] = Math.max(ctx.emi[j], glow[0] * k); ctx.emi[j + 1] = Math.max(ctx.emi[j + 1], glow[1] * k); ctx.emi[j + 2] = Math.max(ctx.emi[j + 2], glow[2] * k);
	}
}
// Kirby krackle: a cloud of energy blobs peppered with black dots, around a world point.
function krackle(ctx, P, R, n, seed, glow = PC_GLOW, core = PC_WHITE) {
	const c = ctx.proj(P); const Rp = R * ctx.ppu;
	const rnd = seeded(seed);
	const blobs = [];
	for (let i = 0; i < n; i++) {
		const a = rnd() * Math.PI * 2, d = Math.pow(rnd(), 0.7) * Rp;
		blobs.push([c[0] + Math.cos(a) * d, c[1] + Math.sin(a) * d, Rp * (0.07 + rnd() * 0.16), rnd()]);
	}
	for (const [bx, by, br] of blobs) {
		for (let y = Math.max(0, Math.floor(by - br)); y < Math.min(ctx.H, by + br); y++) for (let x = Math.max(0, Math.floor(bx - br)); x < Math.min(ctx.W, bx + br); x++) {
			const d = Math.hypot(x - bx, y - by) / br; if (d >= 1) continue;
			const j = (y * ctx.W + x) * 3; const k = d < 0.7 ? 1 : (1 - d) / 0.3;
			const cc = mix(core, glow, d);
			for (let q = 0; q < 3; q++) ctx.col[j + q] = ctx.col[j + q] * (1 - k) + cc[q] * k;
			ctx.emi[j] = Math.max(ctx.emi[j], glow[0] * 0.8 * k); ctx.emi[j + 1] = Math.max(ctx.emi[j + 1], glow[1] * 0.8 * k); ctx.emi[j + 2] = Math.max(ctx.emi[j + 2], glow[2] * 0.8 * k);
		}
	}
	for (const [bx, by, br, h] of blobs) { // the black Kirby dots
		if (h < 0.35) continue;
		const dr = br * (0.35 + 0.25 * h);
		for (let y = Math.max(0, Math.floor(by - dr)); y < Math.min(ctx.H, by + dr); y++) for (let x = Math.max(0, Math.floor(bx - dr)); x < Math.min(ctx.W, bx + dr); x++) {
			if (Math.hypot(x - bx, y - by) >= dr) continue;
			const j = (y * ctx.W + x) * 3;
			ctx.col[j] = 16; ctx.col[j + 1] = 14; ctx.col[j + 2] = 26; ctx.emi[j] = ctx.emi[j + 1] = ctx.emi[j + 2] = 0;
		}
	}
}
// A starfield painted into the background (pre hook: before the geometry).
function stars(ctx, n, seed, k = 1) {
	const rnd = seeded(seed);
	for (let i = 0; i < n; i++) {
		const x = Math.floor(rnd() * ctx.W), y = Math.floor(rnd() * ctx.H * 0.85);
		const b = rnd(); const r = b > 0.97 ? 2 * ctx.ss : ctx.ss * 0.6;
		const fade = (1 - y / ctx.H) * k;
		for (let yy = -r; yy <= r; yy++) for (let xx = -r; xx <= r; xx++) {
			const X = x + xx, Y = y + yy; if (X < 0 || Y < 0 || X >= ctx.W || Y >= ctx.H) continue;
			const d = Math.hypot(xx, yy) / (r + 0.5); if (d > 1) continue;
			const j = (Y * ctx.W + X) * 3; const a = (1 - d) * (0.4 + 0.6 * b) * fade;
			ctx.col[j] += (230 - ctx.col[j]) * a; ctx.col[j + 1] += (236 - ctx.col[j + 1]) * a; ctx.col[j + 2] += (255 - ctx.col[j + 2]) * a;
		}
	}
}
// A soft nebula wash (pre hook).
function nebula(ctx, cols, k = 0.35, seed = 1) {
	for (let y = 0; y < ctx.H; y++) for (let x = 0; x < ctx.W; x++) {
		const u = x / ctx.W * 3, v = y / ctx.H * 3;
		const n = fbm(u + seed, v, seed * 0.37, 4);
		const a = smooth(0.48, 0.8, n) * k * (1 - y / ctx.H * 0.6);
		if (a <= 0) continue;
		const j = (y * ctx.W + x) * 3; const cc = mix(cols[0], cols[1], fbm(u * 2, v * 2 + 5, 3, 2));
		for (let q = 0; q < 3; q++) ctx.col[j + q] += (cc[q] - ctx.col[j + q]) * a;
	}
}

// ================================================================ sheet (the lib's sheet, with every render option passed through)
function sheet(file, title, subtitle, panels, opts = {}) {
	const PH = opts.panelH || 1120, TITLE = opts.titleH == null ? 84 : opts.titleH, PAD = opts.pad || 0.07;
	const minW = opts.minW || 1800;
	const fits = panels.map(pn => {
		pn.faces = L.gather(pn.items);
		const extra = (pn.noOrigin ? [] : pn.items.map(it => it.pos || [0, 0, 0])).concat(pn.extraPts || []);
		return L.projBounds(pn.faces, pn.cam, extra);
	});
	let ppu = Infinity;
	fits.forEach((b, i) => { if (!panels[i].ownScale) ppu = Math.min(ppu, PH * (1 - 2 * PAD) / (b.hi[1] - b.lo[1])); });
	if (!isFinite(ppu)) ppu = PH * (1 - 2 * PAD) / Math.max(...fits.map(b => b.hi[1] - b.lo[1]));
	if (opts.maxPpu) ppu = Math.min(ppu, opts.maxPpu);
	const widths = fits.map((b, i) => {
		const pn = panels[i];
		let k = pn.ownScale ? Math.min(ppu * (pn.ownScaleMax || 1.0), PH * (1 - 2 * PAD) / (b.hi[1] - b.lo[1])) : ppu;
		if (pn.fitW) k = Math.min(k, (pn.fitW - PH * 2 * PAD * 0.9) / (b.hi[0] - b.lo[0]));
		pn.ppu = k;
		return pn.fitW || Math.max(opts.minPanelW || 360, Math.round((b.hi[0] - b.lo[0]) * k + PH * 2 * PAD * 0.9));
	});
	let total = widths.reduce((a, b) => a + b, 0);
	if (total < minW) { const extra = (minW - total) / widths.length; for (let i = 0; i < widths.length; i++) widths[i] = Math.round(widths[i] + extra); total = widths.reduce((a, b) => a + b, 0); }
	const maxW = opts.maxW || 3400;
	if (total > maxW) { const f = maxW / total; for (let i = 0; i < widths.length; i++) { widths[i] = Math.round(widths[i] * f); panels[i].ppu *= f; } total = widths.reduce((a, b) => a + b, 0); }
	const cv = new L.Canvas(total, PH + TITLE, [34, 36, 42]);
	let x = 0;
	panels.forEach((pn, i) => {
		const b = fits[i], k = pn.ppu, w = widths[i];
		const cxw = (b.lo[0] + b.hi[0]) / 2, cyw = (b.lo[1] + b.hi[1]) / 2;
		const origin = [w / 2 - cxw * k + (pn.dx || 0), PH / 2 + cyw * k + (pn.dy || 0) + 14];
		const floors = pn.floor || pn.items.filter(it => !it.noFloor).map(it => ({ x: (it.pos || [0, 0, 0])[0], z: (it.pos || [0, 0, 0])[2], r: (it.floorR || it.model.heightBlocks * 0.42 + 0.8) }));
		const ro = Object.assign({ ss: opts.ss || 2, bloom: opts.bloom, aoR: opts.aoR, edgeThr: opts.edgeThr, shadowSoft: opts.shadowSoft, contactH: opts.contactH, light: opts.light, bg: opts.bg, shadow: opts.shadow }, pn.render || {});
		const img = L.renderPanel(Object.assign(ro, { items: pn.items, faces: pn.faces, W: w, H: PH, cam: pn.cam, ppu: k, origin, floor: floors, bg: pn.bg || ro.bg, pre: pn.pre, post: pn.post, light: pn.light || ro.light }));
		cv.blit(img, w, PH, x, TITLE);
		if (i > 0) cv.rect(x, TITLE, 2, PH, [30, 32, 38]);
		cv.text(pn.label, x + w / 2, TITLE + 18, 3, [236, 240, 246], { align: 'center' });
		if (pn.note) pn.note.forEach((ln, q) => cv.text(ln, x + w / 2, TITLE + PH - 40 - (pn.note.length - 1 - q) * 24, 2, [200, 214, 236], { align: 'center' }));
		x += w;
	});
	if (TITLE > 0) {
		cv.rect(0, 0, total, TITLE, [26, 28, 34]);
		cv.text(title, 28, 18, 5, [240, 244, 250]);
		if (subtitle) cv.text(subtitle, 28 + (title.length * 30) + 28, 30, 3, [160, 190, 220]);
	}
	if (opts.returnCanvas) return cv;
	cv.save(file);
	console.log(`wrote ${file} (${total}x${PH + TITLE})`);
}

// Two or more sheet rows stacked into one image (rows are sheet() canvases rendered with returnCanvas).
function stackRows(file, rows) {
	const w = Math.max(...rows.map(r => r.w)), h = rows.reduce((a, r) => a + r.h + 4, -4);
	const cv = new L.Canvas(w, h, [24, 26, 32]);
	let y = 0;
	for (const r of rows) { cv.blit(r.px, r.w, r.h, Math.round((w - r.w) / 2), y); y += r.h + 4; }
	cv.save(file);
	console.log('wrote ' + file + ' (' + w + 'x' + h + ')');
}
function finish(M, id, blocks, filter) {
	M.setHeight(blocks, filter);
	M.layout();
	M.paint(shadeFor(M));
	if (ASSETS) M.writeAssets(path.join(OUT, 'assets'), id);
	console.log(`${id}: ${M.bones.length} bones, ${M.cubes().length} cubes, tex ${M.TW}x${M.TH}, scale ${M.MODEL_SCALE.toFixed(3)}`);
	return M;
}

// ================================================================ GALACTUS
// Kirby's 1966 design: towering armoured figure; a huge helmet with two tall purple tuning-fork prongs, a blue helmet
// shell and a purple W-shaped faceplate frame around a stern grey face; the big chest plate with the "G"; purple
// armour with blue segmented plating, flared gauntlets and boots, a belt with a disc buckle.
// Weak points (glow): eyes, the helmet crest, the G emblem, the gauntlet energy rings + palm emitters.
function buildGalactus() {
	const M = new Model('galactus', { TW: 2048, D: 3 });
	const P = {
		footH: 5, footLen: 20, footW: 12, shinLen: 26, shinW: 10.5, shinD: 11, thighLen: 26, thighW: 13, thighD: 14, legX: 8, legOut: 1.5,
		pelvisH: 10, pelvisW: 28, pelvisD: 16, waistH: 9, waistW: 25, waistD: 15, chestH: 26, chestW: 38, chestD: 20,
		neckH: 4, neckW: 12, headW: 15, headH: 19, headD: 16, headSink: 3,
		armW: 12, armD: 12, upperLen: 21, foreLen: 18, foreW: 13, handLen: 9.5, handT: 6.4, handW: 13.5, fingerLen: 10.5, armOut: 9, shIn: 3,
		mats: { def: 'g_purple', pelvis: 'g_blue', foot: 'g_blue', shin: 'g_blue', hand: 'g_blue', finger: 'g_blue', fore: 'g_blue', head: 'g_face', neck: 'g_blueD', waist: 'g_blue' },
	};
	humanoid(M, P);
	const A = M.A;
	// The head + helmet are authored in "head units" (a 11-wide head; y measured up from the jaw line) and scaled by hs.
	const hs = P.headW / 11, hb = A.headBot;
	const HB = (b, x0, y0, z0, x1, y1, z1, mat, o = {}) => {
		if (o.pivot) o = Object.assign({}, o, { pivot: [o.pivot[0] * hs, hb + o.pivot[1] * hs, o.pivot[2] * hs] });
		return M.box(b, x0 * hs, hb + y0 * hs, z0 * hs, x1 * hs, hb + y1 * hs, z1 * hs, mat, o);
	};
	const HC = (b, x0, y0, z0, x1, y1, z1, k, mat, o = {}) => { HB(b, x0 + k, y0, z0, x1 - k, y1, z1, mat, o); HB(b, x0, y0, z0 + k, x1, y1, z1 - k, mat, o); };
	const HDX = (b, c, r, d, mat, o = {}) => { const k = r * 0.42; HB(b, c[0] - d / 2, c[1] - r + k, c[2] - r, c[0] + d / 2, c[1] + r - k, c[2] + r, mat, o); HB(b, c[0] - d / 2 - 0.02, c[1] - r, c[2] - r + k, c[0] + d / 2 - 0.02, c[1] + r, c[2] + r - k, mat, o); };
	const ht = 14, eyeY = 8.6; // head units
	Object.assign(A, { hs, eyeYu: eyeY, eyeY: hb + eyeY * hs, eyeX: 2.5 * hs, faceZ: -P.headD / 2 });
	// ---- face: brow, nose, cheekbones, chin
	HB('head', -4.8, eyeY + 0.75, -6.9, 4.8, eyeY + 2.3, -5.4, 'g_face', { paint: 'brow' });
	HB('head', -1.05, eyeY - 4.3, -7.2, 1.05, eyeY + 0.9, -5.7, 'g_face', { paint: 'nose' });
	HB('head', -4.6, eyeY - 2.6, -6.5, 4.6, eyeY - 1.2, -5.6, 'g_face', { paint: 'cheek' });
	HB('head', -2.8, -0.8, -6.7, 2.8, 2.2, -4.0, 'g_face', { paint: 'chin' });
	// ---- helmet
	const helm = M.bone('helmet', 'head', [0, A.eyeY, 0]);
	HC(helm, -9, ht - 1, -8, 9, ht + 4, 8.5, 1.6, 'g_blue', { tag: 'panel', seed: 1 });             // dome
	HC(helm, -7.4, ht + 4, -6.6, 7.4, ht + 6.2, 7, 1.6, 'g_blue', { seed: 2 });                      // dome cap
	for (const [s] of SIDES) {
		const X = (a, b) => s > 0 ? [a, b] : [-b, -a];
		const [x0, x1] = X(5.0, 9.4);
		HB(helm, x0, -2.2, -6.4, x1, ht + 1, 8.6, 'g_blue', { tag: 'panel vrib', seed: 3 });            // side shell
		const [f0, f1] = X(4.4, 8.6);
		HB(helm, f0, 1.5, -8.2, f1, eyeY + 3.0, -6.0, 'g_frame', { paint: 'cheekframe', seed: 4 });      // purple cheek frame
		const [j0, j1] = X(3.2, 6.6);
		HB(helm, j0, -2.4, -7.6, j1, 2.4, -5.0, 'g_frame', { paint: 'jawframe', seed: 5, rot: [0, 0, s * 18], pivot: [s * 5, 1, -6] });
		const [t0, t1] = X(4.2, 4.9);
		HB(helm, t0, 0.6, -8.35, t1, eyeY + 2.2, -7.8, 'g_blueL', { seed: 6 });                          // blue inner trim
		HDX(helm, [s * 9.8, eyeY - 1.2, 1.2], 4.0, 1.4, 'g_disc', { seed: 7 });                           // ear disc
		HDX(helm, [s * 10.6, eyeY - 1.2, 1.2], 1.8, 1.0, 'g_buckle', { seed: 8, paint: 'nolight' });
		// the tuning-fork prongs: tall flat purple fins rising from the helmet sides, flaring slightly outward
		const pr = M.bone(`prong_${s > 0 ? 'l' : 'r'}`, 'helmet', [s * 9.6 * hs, hb + (ht - 1) * hs, 0.8 * hs], [0, 0, -s * 5]);
		const [p0, p1] = X(8.3, 11.3);
		HB(pr, p0, ht - 3, -3.6, p1, ht + 21, 5.2, 'g_prong', { tag: 'panel', seed: 9 });
		const [q0, q1] = X(7.9, 11.7);
		HB(pr, q0, ht + 21, -4.1, q1, ht + 23.5, 5.7, 'g_blue', { seed: 10, tag: 'dots' });              // cap
		HB(pr, q0, ht - 3.5, -4.3, q1, ht + 1.5, 5.9, 'g_blue', { seed: 11, tag: 'ribs' });              // collar at the base
	}
	// brow band with the W: dips to a point between the eyes, the forehead plate above it carries the glowing crest
	HB(helm, -8.6, eyeY + 2.2, -8.5, 8.6, eyeY + 4.4, -6.0, 'g_frame', { paint: 'browband', seed: 13 });
	HB(helm, -2.4, eyeY + 1.0, -8.6, 2.4, eyeY + 2.4, -6.2, 'g_frame', { seed: 14 });
	HB(helm, -1.2, eyeY - 0.1, -8.5, 1.2, eyeY + 1.2, -6.4, 'g_frame', { seed: 15 });
	HB(helm, -8.2, eyeY + 4.4, -8.0, 8.2, ht + 1.5, -5.8, 'g_blue', { paint: 'forehead', seed: 16 });
	// the crest: its own bone (GalactusModelFit skips 'crest*' / 'prong*' bones when it fits him to 60 blocks)
	const crestB = M.bone('crest', 'helmet', [0, hb + (eyeY + 2.6) * hs, -8.5 * hs]);
	HB(crestB, -1.15, eyeY + 2.6, -9.1, 1.15, ht + 6.9, -7.8, 'g_crest', { seed: 17 });                 // crest (front)
	HB(crestB, -1.15, ht + 6.0, -7.9, 1.15, ht + 7.1, 6.4, 'g_crest', { seed: 18 });                      // crest (over the top)
	for (const [s] of SIDES) HB(crestB, s > 0 ? 1.15 : -2.6, eyeY + 4.4, -8.9, s > 0 ? 2.6 : -1.15, ht + 5.5, -7.6, 'g_frame', { seed: 19 });
	// back of the helmet flaring down over the neck
	HB(helm, -9.2, -3, 5.2, 9.2, ht + 3.5, 10.2, 'g_blue', { tag: 'panel vrib', seed: 20 });
	HB(helm, -10.5, -7.5, 6.5, 10.5, -2.5, 12.5, 'g_blue', { tag: 'ribs', seed: 21, rot: [-18, 0, 0], pivot: [0, -3, 6] });
	// ---- torso: wide yoke tapering through the ribcage segments to the banded midriff
	const cb = A.chestBot, ct = A.chestTop;
	cham(M, 'chest', -19.4, cb + 10, -11, 19.4, ct + 0.8, 10.6, 3.0, 'g_purple', { tag: 'panel', seed: 30 });     // upper chest/yoke
	M.box('chest', -14.5, cb + 5, -12.2, 14.5, ct - 1.5, -10.4, 'g_blue', { tag: 'panel', seed: 31 });       // emblem frame
	M.box('chest', -11.6, cb + 7, -13.2, 11.6, ct - 4.0, -11.8, 'g_G', { seed: 32 });                         // the G plate
	cham(M, 'chest', -10.5, ct - 2.0, -7.5, 10.5, ct + 3.4, 9.0, 2.0, 'g_blue', { tag: 'ribs', seed: 33 });    // collar
	M.box('chest', -16.5, cb + 2, 10.2, 16.5, ct - 1, 12.2, 'g_purple', { tag: 'vrib panel', seed: 34 });     // back plate
	cham(M, 'chest', -17.2, cb + 4.5, -10.4, 17.2, cb + 10.5, 10.4, 2.6, 'g_purpleD', { tag: 'ribs', seed: 35, ribPer: 3.0 }); // ribcage segments
	cham(M, 'chest', -15.0, cb - 0.4, -9.8, 15.0, cb + 4.8, 9.8, 2.4, 'g_purpleD', { tag: 'ribs', seed: 35, ribPer: 3.0 });
	// segmented midriff + belt + disc buckle
	for (let i = 0; i < 3; i++) cham(M, 'waist', -13.6 + i * 0.4, A.pelvisTop + 0.5 + i * 2.9, -8.6 + i * 0.2, 13.6 - i * 0.4, A.pelvisTop + 3.0 + i * 2.9, 8.6 - i * 0.2, 2.0, i % 2 ? 'g_blueD' : 'g_blue', { seed: 36 + i, tag: 'dots' });
	cham(M, 'hips', -15.4, A.pelvisTop - 2.6, -9.4, 15.4, A.pelvisTop + 1.2, 9.4, 2.2, 'g_blueD', { tag: 'circles', seed: 40 });
	discZ(M, 'hips', [0, A.pelvisTop - 0.7, -10.0], 4.6, 1.8, 'g_disc', { seed: 41 });
	discZ(M, 'hips', [0, A.pelvisTop - 0.7, -11.0], 2.2, 0.8, 'g_buckle', { seed: 42 });
	cham(M, 'hips', -14.6, A.hipY - 6, -8.6, 14.6, A.pelvisTop - 2.4, 8.6, 2.2, 'g_purpleD', { tag: 'panel', seed: 43 }); // trunks
	// ---- arms: layered pauldrons, banded upper arms, flared gauntlets with energy rings + palm emitters
	for (const [s, n] of SIDES) {
		const sx = s * A.shX, X = (a, b) => s > 0 ? [a, b] : [-b, -a];
		const sh = `shoulder_${n}`;
		cham(M, sh, sx - 8.5, A.shY - 1, -8.5, sx + 8.5, A.shY + 7.5, 8.5, 2.8, 'g_purple', { tag: 'panel', seed: 50 });
		M.box(sh, ...[X(A.shX - 2, A.shX + 9.6)[0], A.shY - 4.5, -8.9, X(A.shX - 2, A.shX + 9.6)[1], A.shY + 0.5, 8.9], 'g_blue', { tag: 'circles', seed: 51, rot: [0, 0, s * 14], pivot: [sx, A.shY, 0] });
		M.box(sh, ...[X(A.shX + 0, A.shX + 10.4)[0], A.shY - 7.8, -8.4, X(A.shX + 0, A.shX + 10.4)[1], A.shY - 4.0, 8.4], 'g_purpleD', { tag: 'ribs', seed: 52, rot: [0, 0, s * 22], pivot: [sx, A.shY - 3, 0] });
		const ua = `arm_${n}`;
		cham(M, ua, sx - 6.4, A.elbowY - 0.5, -6.4, sx + 6.4, A.shY - 3, 6.4, 1.8, 'g_purple', { seed: 49 });
		for (const y of [A.elbowY + 3, A.elbowY + 9.5]) cham(M, ua, sx - 7.0, y, -7.0, sx + 7.0, y + 2.4, 7.0, 1.8, 'g_blue', { seed: 53, tag: 'dots' });
		const fa = `forearm_${n}`;
		cham(M, fa, sx - 7.4, A.wristY + 0.5, -7.4, sx + 7.4, A.elbowY - 4.5, 7.4, 1.8, 'g_blue', { tag: 'circles glowdisc panel', seed: 54 });
		cham(M, fa, sx - 10.2, A.elbowY - 7.5, -10.2, sx + 10.2, A.elbowY + 0.5, 10.2, 2.8, 'g_blue', { tag: 'circles panel', seed: 55 });   // flared cuff
		cham(M, fa, sx - 10.4, A.elbowY - 8.5, -10.4, sx + 10.4, A.elbowY - 7.3, 10.4, 2.8, 'g_purple', { seed: 56 });
		cham(M, fa, sx - 7.9, A.wristY + 1.2, -7.9, sx + 7.9, A.wristY + 3.0, 7.9, 1.9, 'g_core', { seed: 57 });                             // energy ring
		M.box(`hand_${n}`, sx - 3.6, A.knuckY + 1.5, -7.0, sx + 3.6, A.wristY + 0.6, 7.0, 'g_blue', { paint: 'palm', seed: 58 });
	}
	// ---- legs: purple thighs (tapering to the knee), knee guards, big flared boots
	for (const [s, n] of SIDES) {
		const lx = s * A.legX;
		cham(M, `thigh_${n}`, lx - 7.6, A.kneeY + 11, -7.8, lx + 7.6, A.hipY - 1, 7.6, 2.6, 'g_purple', { tag: 'panel', seed: 60 });
		cham(M, `thigh_${n}`, lx - 6.6, A.kneeY + 1, -7.0, lx + 6.6, A.kneeY + 11.5, 6.8, 2.2, 'g_purple', { tag: 'panel', seed: 59 });
		cham(M, `shin_${n}`, lx - 7.0, A.kneeY - 3.5, -8.4, lx + 7.0, A.kneeY + 3.5, 6.2, 2.0, 'g_blueD', { tag: 'panel', seed: 61 });   // knee guard
		cham(M, `shin_${n}`, lx - 8.6, A.kneeY - 9, -8.6, lx + 8.6, A.kneeY - 3.5, 8.6, 2.6, 'g_blue', { tag: 'panel', seed: 62 });     // boot cuff
		cham(M, `shin_${n}`, lx - 6.8, A.ankleY - 0.5, -7.0, lx + 6.8, A.kneeY - 9, 6.8, 2.0, 'g_blue', { tag: 'panel', seed: 63 });
		cham(M, `foot_${n}`, lx - 7.0, 0, -14.6, lx + 7.0, A.ankleY + 2, 7.6, 2.0, 'g_blue', { tag: 'sole panel', seed: 64, paint: 'boot' });
		M.box(`foot_${n}`, lx - 7.3, 0, -15.2, lx + 7.3, 1.3, 8.0, 'g_sole', { tag: 'sole', seed: 65 });
		M.box(`foot_${n}`, lx - 5.0, 2.0, -15.4, lx + 5.0, A.ankleY + 1.0, -13.6, 'g_purple', { seed: 66 });   // toe cap
	}
	// empty locator bones for the gameplay code (beam origins, weak-point hit boxes, the grab socket)
	M.bone('eye_r', 'head', [-A.eyeX, A.eyeY, A.faceZ - 0.5]);
	M.bone('eye_l', 'head', [A.eyeX, A.eyeY, A.faceZ - 0.5]);
	M.bone('weak_crest', 'helmet', [0, A.headBot + (A.eyeYu + 7) * A.hs, -8.5 * A.hs]);
	M.bone('weak_emblem', 'chest', [0, (A.chestBot + 7 + A.chestTop - 4) / 2, -13.2]);
	for (const [s, n] of SIDES) {
		M.bone(`weak_gauntlet_${n}`, `forearm_${n}`, [s * A.shX, A.wristY + 2.1, 0]);
		M.bone(`palm_${n}`, `hand_${n}`, [s * A.shX - s * 3.6, A.knuckY + 4, 0]);
	}
	M.bone('grab_socket', 'hand_r', [-A.shX + 2, A.knuckY - 2, 0]);
	return M;
}

function galactusFace(M, c, f, s, t, p, info) {
	const A = M.A;
	const hs = A.hs, x = p[0] / hs, y = (p[1] - A.headBot) / hs, ax = Math.abs(x);
	const U = { eyeY: A.eyeYu, eyeX: 2.5, eyeW: 1.65, eyeH: 0.62, mouthY: 3.0 };
	let col = mix([116, 118, 132], [182, 184, 196], 0.35 + 0.55 * fbm(p[0] * 0.45 + 3, p[1] * 0.45, p[2] * 0.45, 3));
	col = vgrad(col, f, t, 0.1);
	let g = null;
	const P = c.paint;
	if (f === 'north' && P === 'face') {
		const ex = ax - U.eyeX, ey = y - U.eyeY;
		const sock = Math.pow(ex / (U.eyeW + 1.0), 2) + Math.pow((ey - 0.2) / (U.eyeH + 0.9), 2);
		if (sock < 1) col = mul(col, 0.42 + 0.5 * sock);
		const e = Math.pow(ex / U.eyeW, 2) + Math.pow((ey + ex * 0.16) / U.eyeH, 2);
		if (e < 1) { const k = Math.sqrt(e); col = mix(PC_WHITE, [150, 210, 255], k); g = mix([240, 250, 255], [110, 180, 255], k); }
		if (y < U.eyeY - 0.9 && y > U.eyeY - 1.4 && ax > 1.0 && ax < 4.2) col = mul(col, 0.7);                             // under-eye
		if (segDist(ax, y, 1.3, U.eyeY - 3.4, 2.5, U.mouthY - 0.1) < 0.2) col = mul(col, 0.52);                           // nasolabial folds
		if (ax > 3.0 && y < U.eyeY - 2.6 && y > U.mouthY - 1) col = mul(col, 0.8);                                         // cheek hollow
		const my = U.mouthY - Math.max(0, ax - 1.0) * 0.45;                                                                 // stern down-turned mouth
		if (Math.abs(y - my) < 0.2 && ax < 2.3) col = [40, 38, 50];
		else if (y > my && y < my + 0.55 && ax < 2.1) col = mul(col, 0.8);
		else if (y < my - 0.2 && y > my - 0.6 && ax < 1.6) col = mix(col, [210, 212, 222], 0.3);
	}
	if (f === 'north' && P === 'brow') { if (t > 0.65) col = mul(col, 0.7); if (ax < 0.8 && t > 0.2) col = mul(col, 0.62); /* frown furrows */ if (Math.abs(ax - 1.1) < 0.14) col = mul(col, 0.65); }
	if (P === 'nose') { if (f === 'north' && t > 0.86) col = mul(col, 0.6); if (f === 'east' || f === 'west') col = mul(col, 0.8); }
	if (P === 'chin' && f === 'north') { if (ax < 0.18 && t > 0.4) col = mul(col, 0.6); if (t < 0.2) col = mul(col, 0.8); }
	if (P === 'cheek' && f === 'down') col = mul(col, 0.6);
	return [bevel(col, info, 0.85, 0.95), 255, g];
}

// ================================================================ material switch (every model)
function shadeFor(M) {
	return (c, f, s, t, p, info) => {
		const P = c.paint;
		switch (c.mat) {
			// ---------------- Galactus
			case 'g_purple': { const [col, g] = plate(GAL.purple, c, f, s, t, p, info); return [col, 255, g]; }
			case 'g_purpleD': { const [col, g] = plate(GAL.purpleD, c, f, s, t, p, info); return [col, 255, g]; }
			case 'g_blue': {
				let [col, g] = plate(GAL.blue, c, f, s, t, p, info);
				if (P === 'palm') { // palm emitter on the inner face (the gauntlet energy weak point)
					const inner = (p[0] < 0 && f === 'east') || (p[0] > 0 && f === 'west');
					const d = Math.hypot(s - 0.5, (t - 0.4) * info.fh / info.fw);
					if (inner && d < 0.3) { col = mix(PC_WHITE, PC_GLOW, d / 0.3); g = mix(PC_WHITE, PC_GLOW, d / 0.3); }
					else if (inner && d < 0.38) col = mul(col, 0.45);
				}
				if (P === 'forehead' && f === 'north') { // the W: purple chevrons rising from the brow band
					const [u, v] = UV(s, t, info); const xx = Math.abs(u - info.fw / 2), yy = info.fh - v;
					const w1 = Math.abs(yy - (xx - 2.6) * 0.95);
					if (xx > 2.6 && w1 < 1.1) col = mix(GAL.purple, [255, 255, 255], w1 < 0.25 ? 0.2 : 0);
					else if (xx > 2.6 && w1 < 1.35) col = mul(col, 0.5);
				}
				return [col, 255, g];
			}
			case 'g_blueD': { const [col, g] = plate(GAL.blueD, c, f, s, t, p, info); return [col, 255, g]; }
			case 'g_blueL': { const [col, g] = plate(GAL.blueL, c, f, s, t, p, info); return [col, 255, g]; }
			case 'g_frame': { // the purple faceplate frame: purple with a blue inset line following its length
				let [col] = plate(GAL.magenta, c, f, s, t, p, info);
				if (f === 'north' && info.fw > 1.6 && info.fh > 1.6) {
					if (Math.abs(info.ePx - 0.75) < 0.16) col = mix(col, GAL.blueL, 0.8);
				}
				return [col, 255, null];
			}
			case 'g_prong': {
				let [col] = plate(GAL.magenta, c, f, s, t, p, info);
				const [u, v] = UV(s, t, info);
				if ((f === 'north' || f === 'south' || f === 'east' || f === 'west') && info.fw > 2) {
					const x = Math.abs(u - info.fw / 2);
					if (x < info.fw * 0.16 && v > 2.5 && v < info.fh - 3) col = mix(col, GAL.blue, 0.9);       // a blue inlay down each face
					else if (x < info.fw * 0.16 + 0.3 && v > 2.2 && v < info.fh - 2.7) col = mul(col, 0.5);
					for (const yy of [4, 9, 14, 19]) { const d = Math.hypot(u - info.fw / 2, v - yy); if (d < 1.0 && d > 0.6) col = mul(col, 0.45); }
				}
				return [col, 255, null];
			}
			case 'g_disc': { // a Kirby disc: concentric rings
				let col = plate(GAL.blueL, c, f, s, t, p, info)[0];
				const d = Math.hypot(s - 0.5, t - 0.5) * 2;
				if (f === 'north' || f === 'east' || f === 'west') { if (Math.abs(d - 0.62) < 0.07 || Math.abs(d - 0.32) < 0.06) col = mul(col, 0.45); else if (d < 0.25) col = mix(col, [255, 255, 255], 0.3); }
				return [col, 255, null];
			}
			case 'g_buckle': { let col = mix([200, 206, 230], [255, 255, 255], 0.2); const d = Math.hypot(s - 0.5, t - 0.5) * 2; if (d > 0.6 && d < 0.75) col = mul(col, 0.5); return [bevel(col, info), 255, d < 0.5 && P !== 'nolight' ? mul(PC_GLOW, 0.5) : null]; }
			case 'g_core': return [mix(PC_WHITE, PC_GLOW, 0.25 + 0.3 * vnoise(p[0] * 2, p[1] * 2, p[2] * 2)), 255, mix(PC_WHITE, PC_GLOW, 0.5)];
			case 'g_crest': { // the helmet crest: a channel of Power Cosmic, banded, framed dark
				const band = 0.5 + 0.5 * Math.sin((p[1] + p[2]) * 1.4);
				let col = mix(PC_GLOW, PC_WHITE, 0.4 + 0.5 * band);
				if (info.ePx < 0.25) col = mix(col, GAL.blueD, 0.6);
				return [col, 255, mix(PC_GLOW, PC_WHITE, 0.3 + 0.4 * band)];
			}
			case 'g_G': { // the chest emblem: pale blue plate, the big purple G with a glowing outline
				let col = plate([150, 186, 238], c, f, s, t, p, info)[0];
				let g = null;
				if (f === 'north') {
					const m = 0.13;
					const u = (s - m) / (1 - 2 * m), v = (t - m) / (1 - 2 * m);
					const e = 0.022;
					const on = gMask(u, v);
					const edge = on && (!gMask(u + e, v) || !gMask(u - e, v) || !gMask(u, v + e * 1.2) || !gMask(u, v - e * 1.2));
					if (on) { col = mix(GAL.purple, [170, 110, 230], 0.25 * (1 - v)); if (v < 0.5 && u < 0.5) col = mul(col, 1.08); }
					if (edge) { col = PC_WHITE; g = PC_GLOW; }
					if (info.ePx < 0.5) { col = mix(PC_WHITE, PC_GLOW, 0.4); g = mul(PC_GLOW, 0.8); }
					for (const [cx, cy] of [[0.05, 0.06], [0.95, 0.06], [0.05, 0.94], [0.95, 0.94]]) if (Math.hypot((s - cx) * info.fw, (t - cy) * info.fh) < 0.55) { col = PC_WHITE; g = PC_GLOW; }
				}
				return [col, 255, g];
			}
			case 'g_sole': return [bevel(mul(GAL.blueD, 0.55), info), 255, null];
			case 'g_face': return galactusFace(M, c, f, s, t, p, info);
			// ---------------- Silver Surfer + board
			case 's_chrome': return surferChrome(M, c, f, s, t, p, info);
			case 'board': case 'board_glow': return boardShade(c, f, s, t, p, info);
			default:
				if (c.mat.startsWith('p_')) { const r = punisherShade(M, c, f, s, t, p, info); if (r) return r; }
				if (c.mat.startsWith('ec_') || c.mat.startsWith('gr_')) { const r = converterShade(M, c, f, s, t, p, info); if (r) return r; }
				if (c.mat.startsWith('ws_')) { const r = worldshipShade(M, c, f, s, t, p, info); if (r) return r; }
				if (c.mat.startsWith('nf_')) { const r = nullifierShade(M, c, f, s, t, p, info); if (r) return r; }
				if (M.extraMat) { const r = M.extraMat(c, f, s, t, p, info); if (r) return r; }
				return [[255, 0, 255], 255, null];
		}
	};
}

// ---------------------------------------------------------------- Galactus poses
function galactusPoses(M) {
	const A = M.A;
	const stand = Object.assign({
		arm_r: [-4, 0, 6], forearm_r: [-14, 0, 0], arm_l: [-4, 0, -6], forearm_l: [-14, 0, 0],
		thigh_r: [0, 0, 4], thigh_l: [0, 0, -4], head: [0, 0, 0], chest: [-2, 0, 0],
	}, FIST('r'), FIST('l'));
	// eye beams: head lowered, fists clenched and pulled back, leaning into it
	const beams = Object.assign({
		waist: [6, 0, 0], chest: [8, 0, 0], neck: [6, 0, 0], head: [14, 0, 0],
		arm_r: [18, 0, 26], forearm_r: [-40, 0, 0], arm_l: [18, 0, -26], forearm_l: [-40, 0, 0],
		thigh_r: [-10, 0, 8], shin_r: [12, 0, 0], foot_r: [-2, 0, 0], thigh_l: [12, 0, -6], shin_l: [4, 0, 0], foot_l: [-14, 0, 0],
	}, FIST('r'), FIST('l'));
	// the giant open hand reaching down toward the ground in front of him
	const reachP = Object.assign({
		waist: [24, 0, 0], chest: [24, -10, 0], head: [6, -10, 0],
		arm_l: [10, 0, -18], forearm_l: [-30, 0, 0],
		thigh_r: [-56, 0, 6], shin_r: [78, 0, 0], foot_r: [-22, 0, 0],
		thigh_l: [26, 0, -6], shin_l: [40, 0, 0], foot_l: [-64, 0, 0],
	}, OPEN('r', 1.6), FIST('l'));
	const palm = [-A.shX, A.knuckY + 3, 0];
	reachP.$root = planted(M, reachP);
	// aim for a point 92 % of the arm's length from the shoulder, down and forward (always reachable)
	const W0 = M.world(reachP, reachP.$root);
	const sh = affApply(W0.arm_r, [-A.shX, A.shY, 0]);
	const armLen = A.shY - A.knuckY - 3;
	const target = vadd(sh, vsc(vnorm([-0.22, -0.78, -0.6]), armLen * 0.97));
	L.reach(M, reachP, 'hand_r', palm, target, [['arm_r', 0], ['arm_r', 1], ['arm_r', 2], ['forearm_r', 0], ['hand_r', 0], ['hand_r', 2]],
		W => 30 * (1 - vdot(vnorm(mv3(W.hand_r.m, [1, 0, 0])), [0, -1, 0])) + 10 * (1 - vdot(vnorm(vsub(affApply(W.forearm_r, [-A.shX, A.elbowY, 0]), sh)), vnorm(vsub(target, sh)))));
	return { stand, beams, reach: reachP };
}

// ================================================================ SILVER SURFER
// Player-sized (2.1 blocks), lean, smooth chrome; bald head, solid white glowing eyes; muscle definition is inked into
// the chrome the way the comics do it rather than modelled as bulk.
function buildSurfer() {
	const M = new Model('silver_surfer', { TW: 512, D: 3 });
	const P = {
		footH: 2.2, footLen: 7.4, footW: 3.3, shinLen: 15, shinW: 3.3, shinD: 3.5, thighLen: 15, thighW: 4.5, thighD: 4.7, legX: 2.45, legOut: 1,
		pelvisH: 5, pelvisW: 9.4, pelvisD: 5.2, waistH: 5.2, waistW: 7.2, waistD: 4.7, chestH: 12, chestW: 10.8, chestD: 6.0,
		neckH: 2.6, neckW: 3.3, headW: 5.2, headH: 6.6, headD: 6.0, headSink: 0.7,
		armW: 3.2, armD: 3.4, upperLen: 11.5, foreLen: 10.5, foreW: 3.2, handLen: 3.4, handT: 1.5, handW: 3.0, fingerLen: 3.4, armOut: 4, shIn: -0.9,
		mats: { def: 's_chrome' },
	};
	humanoid(M, P);
	const A = M.A;
	const cb = A.chestBot, ct = A.chestTop, hb = A.headBot, ht = A.headTop, fz = -P.headD / 2;
	Object.assign(A, { eyeY: hb + 3.9, eyeX: 1.25, eyeW: 0.85, eyeH: 0.33, mouthY: hb + 1.45, faceZ: fz });
	// torso: broad upper chest tapering to a narrow waist, pecs, abs, lats, traps, glutes
	cham(M, 'chest', -7.2, ct - 6.8, -3.3, 7.2, ct, 3.1, 1.0, 's_chrome', { paint: 'chest', seed: 1 });
	M.box('chest', -6.4, ct - 5.6, -3.75, 6.4, ct - 1.3, -2.9, 's_chrome', { paint: 'pec', seed: 2 });
	cham(M, 'chest', -6.4, cb + 2.5, -3.0, 6.4, ct - 5.5, 2.9, 0.9, 's_chrome', { paint: 'lat', seed: 3 });
	M.box('waist', -2.7, A.pelvisTop - 0.1, -2.75, 2.7, cb + 3.6, -2.1, 's_chrome', { paint: 'abs', seed: 4 });
	for (const [s] of SIDES) M.box('chest', s > 0 ? 0.9 : -6.0, ct - 0.8, -1.6, s > 0 ? 6.0 : -0.9, ct + 1.0, 2.2, 's_chrome', { paint: 'trap', seed: 5, rot: [0, 0, s * 16], pivot: [s * 0.9, ct, 0] });
	M.box('hips', -4.2, A.hipY - 3.0, 1.9, 4.2, A.hipY + 1.8, 3.2, 's_chrome', { paint: 'glute', seed: 6 });
	// head: bald dome, brow, nose, cheekbones, chin, ears
	M.box('head', -2.3, ht - 0.05, -2.5, 2.3, ht + 0.55, 2.4, 's_chrome', { paint: 'skull', seed: 7 });
	M.box('head', -2.55, A.eyeY + 0.35, fz - 0.4, 2.55, A.eyeY + 1.0, fz + 0.4, 's_chrome', { paint: 'brow', seed: 8 });
	M.box('head', -0.42, A.eyeY - 1.9, fz - 0.55, 0.42, A.eyeY + 0.5, fz + 0.3, 's_chrome', { paint: 'nose', seed: 9 });
	M.box('head', -1.3, hb - 0.3, fz - 0.25, 1.3, hb + 1.0, fz + 1.4, 's_chrome', { paint: 'chin', seed: 10 });
	for (const [s] of SIDES) M.box('head', s > 0 ? 2.6 : -2.95, A.eyeY - 1.4, -0.6, s > 0 ? 2.95 : -2.6, A.eyeY + 0.4, 0.6, 's_chrome', { paint: 'ear', seed: 11 });
	// limbs: deltoids, biceps, triceps, forearm swell, quads, calves, knees
	for (const [s, n] of SIDES) {
		const sx = s * A.shX, lx = s * A.legX;
		cham(M, `arm_${n}`, sx - 2.15, A.shY - 3.6, -2.2, sx + 2.15, A.shY + 1.7, 2.2, 0.7, 's_chrome', { paint: 'delt', seed: 12 });
		M.box(`arm_${n}`, sx - 1.25, A.elbowY + 1.2, -2.15, sx + 1.25, A.shY - 3.2, -1.4, 's_chrome', { paint: 'bicep', seed: 13 });
		M.box(`arm_${n}`, sx - 1.3, A.elbowY + 1.0, 1.4, sx + 1.3, A.shY - 3.0, 2.0, 's_chrome', { paint: 'arm', seed: 14 });
		cham(M, `forearm_${n}`, sx - 1.95, A.elbowY - 4.8, -1.95, sx + 1.95, A.elbowY + 0.3, 1.95, 0.6, 's_chrome', { paint: 'fore', seed: 15 });
		M.box(`thigh_${n}`, lx - 1.9, A.kneeY + 2.8, -2.75, lx + 1.9, A.hipY - 2.2, -2.0, 's_chrome', { paint: 'quad', seed: 16 });
		M.box(`thigh_${n}`, lx - 1.9, A.kneeY + 2.5, 2.0, lx + 1.9, A.hipY - 3.5, 2.6, 's_chrome', { paint: 'thigh', seed: 17 });
		M.box(`shin_${n}`, lx - 1.85, A.kneeY - 7.5, -0.8, lx + 1.85, A.kneeY - 1.5, 2.4, 's_chrome', { paint: 'calf', seed: 18 });
		M.box(`shin_${n}`, lx - 1.3, A.kneeY - 1.2, -2.15, lx + 1.3, A.kneeY + 0.9, -1.5, 's_chrome', { paint: 'knee', seed: 19 });
	}
	// a bone for the Power Cosmic orb / blast at the right palm (empty; effects attach here)
	M.bone('cosmic_r', 'hand_r', [-A.shX, A.knuckY + 1, 0]);
	return M;
}

function surferChrome(M, c, f, s, t, p, info) {
	const A = M.A;
	let col = chrome(c, f, s, t, p, info);
	let g = null;
	const P = c.paint, x = p[0], y = p[1], ax = Math.abs(x);
	const ink = (k = 0.85) => { col = mix(col, [30, 34, 48], k); };
	if (f === 'north') {
		if (P === 'pec') { if (t > 0.82) ink(0.7); if (ax < 0.22) ink(0.6); }
		if (P === 'abs') { if (ax < 0.12) ink(0.7); for (const yy of [A.pelvisTop + 1.9, A.pelvisTop + 3.9, A.pelvisTop + 5.9]) if (Math.abs(y - yy) < 0.12) ink(0.7); }
		if (P === 'lat' && Math.abs(ax - 5.0 + (y - A.chestBot) * 0.15) < 0.12) ink(0.6);
		if (P === 'quad' && Math.abs(x - Math.sign(x) * A.legX + Math.sign(x) * 0.2 * (y - A.kneeY - 2.8) / 10) < 0.1) ink(0.55);
		if (P === 'knee' && t > 0.7) ink(0.5);
	}
	if (P === 'bicep' && (f === 'down' || (f === 'north' && t > 0.9))) ink(0.6);
	if (P === 'calf' && f === 'south' && t > 0.85) ink(0.5);
	if (P === 'face' && f === 'north') {
		const ex = ax - A.eyeX, ey = y - A.eyeY;
		const e = Math.pow(ex / A.eyeW, 2) + Math.pow((ey + ex * 0.22) / A.eyeH, 2);
		if (e < 1) { col = mix([255, 255, 255], [214, 232, 255], Math.sqrt(e)); g = [236, 246, 255]; }
		else if (e < 1.9) ink(0.75);
		if (Math.abs(y - A.mouthY - Math.max(0, ax - 0.5) * 0.15) < 0.09 && ax < 1.05) ink(0.8);
		if (segDist(ax, y, 0.55, A.eyeY - 1.6, 1.15, A.mouthY + 0.15) < 0.07) ink(0.5);
		if (ax > 1.4 && y < A.eyeY - 0.55 && y > A.eyeY - 0.75) ink(0.45); // cheekbone line
	}
	if (P === 'brow' && f === 'down') ink(0.7);
	if (P === 'chin' && f === 'north' && t > 0.85) ink(0.4);
	if (P === 'palm' || P === 'finger' || P === 'fingertip') { if (info.ePx < 0.2) col = mix(col, [30, 34, 48], 0.3); }
	return [col, 255, g];
}

// The board: a long, thin pointed oval of polished silver (both ends pointed), Power Cosmic glowing underneath.
// Built in the Surfer's model pixels (so both share one scale); its top surface is y = 0.
function buildBoard() {
	const M = new Model('surfboard', { TW: 512, D: 3 });
	const b = M.bone('board', null, [0, 0, 0]);
	const half = [[0, 15, 14.0, 1.7], [15, 19, 12.6, 1.6], [19, 22, 10.6, 1.45], [22, 24.6, 8.2, 1.3], [24.6, 26.6, 5.6, 1.1], [26.6, 28.0, 3.2, 0.9], [28.0, 29.0, 1.4, 0.7]];
	for (const sgn of [-1, 1]) for (const [z0, z1, w, th] of half) {
		const lift = sgn < 0 ? Math.max(0, z1 - 22) * 0.22 : 0; // the nose (front, -Z) rises a touch
		M.box(b, -w / 2, -th + lift, sgn < 0 ? -z1 : z0, w / 2, lift, sgn < 0 ? -z0 : z1, 'board', { seed: 1 + z0 });
		M.box(b, -w / 2 + 0.5, -th - 0.25 + lift, sgn < 0 ? -z1 + 0.3 : z0, w / 2 - 0.5, -th + 0.05 + lift, sgn < 0 ? -z0 : z1 - 0.3, 'board_glow', { seed: 2 + z0 });
	}
	M.box(b, -0.35, 0, -21, 0.35, 0.18, 21, 'board', { paint: 'ridge', seed: 50 }); // a faint keel line along the top
	return M;
}
function boardShade(c, f, s, t, p, info) {
	if (c.mat === 'board') {
		let col = chrome(c, f, s, t, [p[0] * 0.5, p[2] * 0.12 + 40, p[1]], info, [0.97, 0.99, 1.03]);
		if (f === 'up') { // the deck mirrors the sky: bright along the keel, a dark horizon band toward each rail, streaks nose to tail
			const w = Math.abs(p[0]) / 7 + (fbm(p[2] * 0.06, 2, 3, 2) - 0.5) * 0.25;
			col = w < 0.42 ? mix([246, 250, 255], [190, 200, 222], w / 0.42) : w < 0.62 ? mix([190, 200, 222], [84, 94, 124], (w - 0.42) / 0.2) : mix([110, 120, 148], [176, 184, 204], Math.min(1, (w - 0.62) / 0.38));
			const st = 0.5 + 0.5 * Math.sin(p[0] * 1.3 + fbm(p[2] * 0.05, 1, 2, 2) * 3);
			col = mix(col, [255, 255, 255], 0.3 * Math.pow(st, 8));
			col = mix(col, [150, 170, 230], 0.12);
			if (info.ePx < 0.26) col = mix(col, [255, 255, 255], 0.6);
		}
		if (c.paint === 'ridge') col = mix(col, [255, 255, 255], 0.3);
		return [col, 255, null];
	}
	if (c.mat === 'board_glow') { // the underside: dim chrome lit from within by Power Cosmic
		const n = fbm(p[0] * 0.3, p[2] * 0.15, 3, 3);
		// dim brushed silver with Power Cosmic veins running nose to tail; only the veins and a soft wash glow
		const vein = Math.abs(Math.sin(p[0] * 1.1 + fbm(p[2] * 0.08, 2, 1, 3) * 5));
		let col = mix([96, 110, 150], [150, 170, 210], n);
		let g = mul([50, 90, 210], 0.35 + 0.3 * n);
		if (vein < 0.12) { col = mix([200, 225, 255], [255, 255, 255], 1 - vein / 0.12); g = mix([90, 150, 255], [200, 230, 255], 1 - vein / 0.12); }
		if (info.ePx < 0.3) col = mix(col, [220, 230, 250], 0.5);
		return [col, 255, g];
	}
	return null;
}

// A plain player in a teal shirt and blue trousers for item/mount close-ups (generic, painted procedurally).
function buildPaintedPlayer() {
	const M = new Model('player_painted', { TW: 256, D: 3 });
	const r = M.bone('root', null, [0, 0, 0]);
	const legs = M.bone('legs', 'root', [0, 12, 0]);
	M.box(legs, -4, 0, -2, 0, 12, 2, 'pl_pants'); M.box(legs, 0, 0, -2, 4, 12, 2, 'pl_pants');
	M.box(legs, -4, 0, -2.05, 0, 1.6, 2.05, 'pl_shoe'); M.box(legs, 0, 0, -2.05, 4, 1.6, 2.05, 'pl_shoe');
	M.box(r, -4, 12, -2, 4, 24, 2, 'pl_shirt');
	const ar = M.bone('arm_r', 'root', [-6, 22, 0]), al = M.bone('arm_l', 'root', [6, 22, 0]);
	M.box(ar, -8, 12, -2, -4, 24, 2, 'pl_arm'); M.box(al, 4, 12, -2, 8, 24, 2, 'pl_arm');
	const hd = M.bone('head', 'root', [0, 24, 0]);
	M.box(hd, -4, 24, -4, 4, 32, 4, 'pl_head');
	M.k = 2 / 32; M.MODEL_SCALE = 1; M.heightBlocks = 2;
	M.extraMat = (c, f, s, t, p, info) => {
		const n = 0.92 + 0.12 * hash3(info.i >> 1, info.j >> 1, 3);
		switch (c.mat) {
			case 'pl_pants': return [mul([58, 60, 72], n), 255, null];
			case 'pl_shoe': return [mul([70, 70, 74], n), 255, null];
			case 'pl_shirt': return [mul([156, 54, 48], n), 255, null];
			case 'pl_arm': return [mul(p[1] > 20 ? [156, 54, 48] : [200, 150, 110], n), 255, null];
			case 'pl_head': {
				let col = mul([200, 150, 110], n);
				if (f === 'up' || p[1] > 30.5 || (f !== 'north' && p[1] > 28)) col = mul([30, 28, 32], n);
				if (f === 'north' && p[1] > 27 && p[1] < 28 && Math.abs(Math.abs(p[0]) - 2) < 1) col = Math.abs(p[0]) > 2 ? [250, 250, 250] : [50, 110, 60];
				if (f === 'north' && p[1] > 25 && p[1] < 26 && Math.abs(p[0]) < 1.5) col = [110, 70, 60];
				return [col, 255, null];
			}
		}
		return null;
	};
	return M;
}

function surferPoses(M, B) {
	const A = M.A;
	const stand = Object.assign({
		arm_r: [-2, 0, 4], forearm_r: [-10, 0, 0], arm_l: [-2, 0, -4], forearm_l: [-10, 0, 0],
		thigh_r: [0, 0, 2], thigh_l: [0, 0, -2], head: [-4, 0, 0],
	}, OPEN('r', 0.8), OPEN('l', 0.8));
	// feet onto the board: solve each leg so the sole sits on a board point, foot flat on the deck, knee bending forward
	const footOn = (pose, boardPose, n, bpt) => {
		const s = n === 'r' ? -1 : 1;
		const BW = B.world(boardPose);
		const tgt = affApply(BW.board, bpt);
		const up = vnorm(mv3(BW.board.m, [0, 1, 0]));
		const sole = [s * A.legX, 0, -P_FOOTLEN * 0.16];
		L.reach(M, pose, `foot_${n}`, sole, tgt, [[`thigh_${n}`, 0], [`thigh_${n}`, 1], [`thigh_${n}`, 2], [`shin_${n}`, 0], [`foot_${n}`, 0], [`foot_${n}`, 2]],
			W => 6 * (1 - vdot(vnorm(mv3(W[`foot_${n}`].m, [0, 1, 0])), up)) + (pose[`shin_${n}`][0] < 2 ? 4 : 0));
	};
	const P_FOOTLEN = 7.4;
	// surfing + Power Cosmic blast: crouched, left foot forward, right arm thrust out, board banking into the turn
	const boardSurf = { board: [-7, 0, -9] };
	const surf = Object.assign({
		$root: [0, -3.2, 0],
		hips: [10, -18, 0], waist: [4, 6, 0], chest: [4, 12, 0], head: [-4, 4, 0],
		arm_r: [-84, 0, 26], forearm_r: [-4, 0, 0], hand_r: [-60, 0, 0],
		arm_l: [32, 0, -38], forearm_l: [-28, 0, 0],
		thigh_l: [-30, 0, -4], shin_l: [40, 0, 0], thigh_r: [10, 0, 6], shin_r: [40, 0, 0],
	}, BACKBEND('r', 1.2), OPEN('l', 1));
	footOn(surf, boardSurf, 'l', [1.6, 0, -8.5]);
	footOn(surf, boardSurf, 'r', [-1.8, 0, 7.5]);
	// the ally: standing tall on the hovering board, one hand lifted with a small Power Cosmic orb
	const boardAlly = { board: [0, 0, 0] };
	const ally = Object.assign({
		$root: [0, 0, 0], head: [-6, 10, 0], chest: [-2, 6, 0],
		arm_r: [-14, 0, 20], forearm_r: [-38, 0, 0], hand_r: [0, 0, 0],
		arm_l: [-4, 0, -6], forearm_l: [-12, 0, 0],
	}, OPEN('r', 1.8), OPEN('l', 0.8));
	footOn(ally, boardAlly, 'l', [2.4, 0, -1.0]);
	footOn(ally, boardAlly, 'r', [-2.4, 0, 0.6]);
	return { stand, surf, boardSurf, ally, boardAlly };
}

function renderSurfer(M, B) {
	const bg = { top: [64, 72, 104], bottom: [34, 38, 54] };
	const pre = ctx => { nebula(ctx, [[60, 70, 150], [120, 80, 170]], 0.25, 3); stars(ctx, 320, 21, 0.8); };
	const light = Object.assign({}, L.DEFAULT_LIGHT, { rimK: 0.85, keyK: 0.72, amb: 0.4 });
	const A = M.A;
	const it = (pose, extra = {}) => Object.assign({ model: M, pose, root: pose.$root || planted(M, pose), floorR: 1.4 }, extra);
	const lift = 1.1; // the board hovers this many blocks up
	const surfItem = it(M.poses.surf, { pos: [0, lift + 0.4, 0], noFloor: true });
	const surfBoard = { model: B, pose: M.poses.boardSurf, root: [0, 0, 0], pos: [0, lift + 0.4, 0], floorR: 1.6 };
	const allyItem = it(M.poses.ally, { pos: [0, lift, 0], noFloor: true });
	const allyBoard = { model: B, pose: M.poses.boardAlly, root: [0, 0, 0], pos: [0, lift, 0], floorR: 1.6 };
	const palm = it2 => wpt(it2, 'hand_r', [-A.shX, A.knuckY + 1.2, 0]);
	const elbow = it2 => wpt(it2, 'forearm_r', [-A.shX, A.elbowY, 0]);
	const handP = palm(surfItem), dir = vnorm(vsub(handP, elbow(surfItem)));
	const blastEnd = vadd(handP, vsc(dir, 4.2));
	const glowUnder = (ctx, item) => { // Power Cosmic glow under the deck + a soft pool of light on the ground
		for (let z = -26; z <= 26; z += 2) for (let x = -5; x <= 5; x += 2.5) flare(ctx, wpt(item, "board", [x, -2.4, z]), 0.16, [220, 236, 255], [90, 140, 255], 0.3, true);
		const c = wpt(item, 'board', [0, 0, 0]);
		flare(ctx, [c[0], 0.02, c[2]], 1.1, [150, 180, 255], [60, 90, 200], 0.18, true);
	};
	const row1 = sheet(null, 'SILVER SURFER', '2.1 BLOCKS - HERALD OF GALACTUS: CHROME, WHITE EYES, POWER COSMIC - AND HIS BOARD', [
		{ label: 'FRONT', items: [it(M.poses.stand)], cam: { yaw: 0, pitch: 6 }, pre },
		{ label: 'THREE-QUARTER', items: [it(M.poses.stand)], cam: { yaw: 35, pitch: 6 }, pre },
		{ label: 'SIDE', items: [it(M.poses.stand)], cam: { yaw: 90, pitch: 6 }, pre },
		{
			label: 'ACTION: SURFING - POWER COSMIC BLAST', items: [surfBoard, surfItem], cam: { yaw: -58, pitch: 8 }, ownScale: true, pre,
			extraPts: [blastEnd, vadd(blastEnd, [0, -1.5, 0]), [0, 0, 0]],
			post: ctx => { glowUnder(ctx, surfBoard); beam(ctx, handP, blastEnd, 0.075, PC_WHITE, PC_GLOW, { taper: -1.2 }); krackle(ctx, handP, 0.28, 18, 5); krackle(ctx, blastEnd, 0.55, 30, 9); flare(ctx, blastEnd, 0.7); flare(ctx, handP, 0.35); },
		},
		{
			label: 'ALLY: ON THE BOARD', items: [allyBoard, allyItem], cam: { yaw: 32, pitch: 6 }, ownScale: true, pre,
			post: ctx => { glowUnder(ctx, allyBoard); const o = palm(allyItem); krackle(ctx, vadd(o, [0, -0.08, 0]), 0.14, 12, 4); flare(ctx, vadd(o, [0, -0.08, 0]), 0.22); },
			note: ['HOVERS, GLOW UNDER THE BOARD'],
		},
	], { returnCanvas: true, panelH: 1150, minW: 2600, maxW: 3300, bg, light, aoR: 0.12, edgeThr: 0.06, shadowSoft: 0.2 });
	const only = re => c => !re.test(c.bone);
	const row2 = sheet(null, '', '', [
		{ label: 'HEAD - FRONT', items: [it(M.poses.stand, { hide: only(/^(head|neck)$/), noFloor: true })], cam: { yaw: 0, pitch: 2 }, ownScale: true, ownScaleMax: 30, noOrigin: true, pre },
		{ label: 'HEAD - THREE-QUARTER', items: [it(M.poses.stand, { hide: only(/^(head|neck)$/), noFloor: true })], cam: { yaw: 38, pitch: 4 }, ownScale: true, ownScaleMax: 30, noOrigin: true, pre, note: ['SOLID WHITE GLOWING EYES'] },
		{ label: 'CHROME TORSO', items: [it(M.poses.stand, { hide: only(/^(chest|waist|hips|neck|head|arm_.|shoulder_.)$/), noFloor: true })], cam: { yaw: 25, pitch: 4 }, ownScale: true, ownScaleMax: 30, noOrigin: true, pre, note: ['MUSCLES INKED INTO THE CHROME'] },
		{ label: 'BOARD', items: [{ model: B, pose: {}, root: [0, 0, 0], noFloor: true }], cam: { yaw: 50, pitch: 28 }, ownScale: true, ownScaleMax: 30, noOrigin: true, pre },
	], { returnCanvas: true, titleH: 0, panelH: 700, minW: row1.w, maxW: row1.w, bg, light, shadow: false, aoR: 0.05, edgeThr: 0.03 });
	stackRows(path.join(OUT, '02_silver_surfer.png'), [row1, row2]);
}

// ================================================================ THE PUNISHER (Galactus's robot sentinel)
// About 9 blocks: a squat, massively armoured robot; faceless dome head sunk between huge shoulders, grey/teal armour
// with orange accents, oversized forearms that ARE the blaster gauntlets (twin barrels, glowing orange muzzles).
function buildPunisher() {
	const M = new Model('galactus_punisher', { TW: 1024, D: 3 });
	const P = {
		footH: 5, footLen: 16, footW: 10.5, shinLen: 14, shinW: 10, shinD: 10, thighLen: 13, thighW: 11, thighD: 11, legX: 7.6, legOut: 3,
		pelvisH: 8, pelvisW: 22, pelvisD: 13, waistH: 5, waistW: 16, waistD: 11, chestH: 21, chestW: 30, chestD: 18,
		neckH: 1.5, neckW: 9, headW: 14, headH: 10, headD: 14, headSink: 1.8,
		armW: 10, armD: 10, upperLen: 13, foreLen: 17, foreW: 11, handLen: 6, handT: 4.2, handW: 8, fingerLen: 6.5, armOut: 12, shIn: -1,
		mats: { def: 'p_teal', waist: 'p_joint', neck: 'p_joint', head: 'p_dome', hand: 'p_greyD', finger: 'p_greyD', foot: 'p_grey' },
	};
	humanoid(M, P);
	const A = M.A;
	const cb = A.chestBot, ct = A.chestTop, hb = A.headBot, ht = A.headTop;
	// faceless dome: an orange collar ring, then three narrowing tiers of polished grey-teal
	cham(M, 'head', -7.6, hb - 0.5, -7.6, 7.6, hb + 2.2, 7.6, 2.2, 'p_orange', { seed: 1, tag: 'dots' });
	cham(M, 'head', -7.0, hb + 2.0, -7.0, 7.0, ht - 1, 7.0, 2.4, 'p_dome', { seed: 2 });
	cham(M, 'head', -5.6, ht - 1.2, -5.6, 5.6, ht + 1.6, 5.6, 2.0, 'p_dome', { seed: 3 });
	cham(M, 'head', -3.6, ht + 1.4, -3.6, 3.6, ht + 2.8, 3.6, 1.3, 'p_dome', { seed: 4 });
	// barrel chest: teal core, grey breastplate with orange chevrons, a high collar the dome sits down into
	cham(M, 'chest', -16, cb + 3, -10, 16, ct, 9.6, 4.0, 'p_teal', { tag: 'panel', seed: 10 });
	cham(M, 'chest', -12.5, cb + 5, -11.4, 12.5, ct - 2, -9.4, 2.6, 'p_grey', { tag: 'panel chev', seed: 11, chevPer: 3.6 });
	cham(M, 'chest', -11, ct - 3, -9.6, 11, ct + 4.5, 9.6, 3.0, 'p_grey', { tag: 'ribs', seed: 12 });            // collar
	cham(M, 'chest', -13, cb - 0.5, -9, 13, cb + 4, 9, 3.0, 'p_tealD', { tag: 'ribs', seed: 13 });
	M.box('chest', -10, cb + 4, 9.4, 10, ct - 3, 11.8, 'p_greyD', { tag: 'vrib panel', seed: 14 });              // back pack
	for (const x of [-7.2, -2.4, 2.4, 7.2]) discZ(M, 'chest', [x, cb + 3.2, 11.4], 1.1, 1.0, 'p_glow', { seed: 15 });  // exhaust row (behind)
	// waist + armoured hip skirt
	cham(M, 'hips', -12.5, A.pelvisTop - 3, -8, 12.5, A.pelvisTop + 0.8, 8, 2.4, 'p_orange', { seed: 20, tag: 'hazard' });
	for (const [s] of SIDES) M.box('hips', s > 0 ? 4 : -12.8, A.hipY - 6.5, -7.6, s > 0 ? 12.8 : -4, A.pelvisTop - 2.8, 7.6, 'p_grey', { tag: 'panel', seed: 21, rot: [0, 0, s * 8], pivot: [s * 8, A.pelvisTop, 0] });
	M.box('hips', -4, A.hipY - 7.5, -8.4, 4, A.pelvisTop - 2.8, -7.4, 'p_grey', { tag: 'panel', seed: 22 });
	for (const [s, n] of SIDES) {
		const sx = s * A.shX, lx = s * A.legX, X = (a, b) => s > 0 ? [a, b] : [-b, -a];
		// huge rounded pauldrons with an orange stripe
		cham(M, `shoulder_${n}`, sx - 8.5, A.shY - 3, -8.5, sx + 8.5, A.shY + 8, 8.5, 3.4, 'p_grey', { tag: 'panel', seed: 30 });
		cham(M, `shoulder_${n}`, sx - 6.5, A.shY + 7.8, -6.5, sx + 6.5, A.shY + 10, 6.5, 2.4, 'p_grey', { seed: 31 });
		M.box(`shoulder_${n}`, ...[X(A.shX + 8.3, A.shX + 8.9)[0], A.shY - 1, -8.6, X(A.shX + 8.3, A.shX + 8.9)[1], A.shY + 6, 8.6], 'p_orange', { seed: 32 });
		cham(M, `shoulder_${n}`, sx - 9, A.shY - 6.5, -9, sx + 9, A.shY - 2.6, 9, 3.4, 'p_tealD', { tag: 'ribs', seed: 33 });
		cham(M, `arm_${n}`, sx - 5.4, A.elbowY - 1, -5.4, sx + 5.4, A.shY - 4, 5.4, 1.8, 'p_teal', { tag: 'ribs', seed: 34, ribPer: 3.0 });
		// the blaster gauntlet: a massive forearm housing, twin barrels on the outside, a muzzle ring at the wrist
		cham(M, `forearm_${n}`, sx - 7.6, A.wristY + 0.5, -7.6, sx + 7.6, A.elbowY + 1.2, 7.6, 2.6, 'p_grey', { tag: 'panel circles', seed: 35 });
		cham(M, `forearm_${n}`, sx - 6.4, A.elbowY - 4.5, -8.2, sx + 6.4, A.elbowY - 1.5, 8.2, 1.6, 'p_orange', { seed: 36, tag: 'hazard' });
		cham(M, `forearm_${n}`, sx - 6.2, A.wristY - 2.6, -6.2, sx + 6.2, A.wristY + 0.8, 6.2, 2.0, 'p_greyD', { tag: 'ribs', seed: 37, ribPer: 1.2 });
		for (const z of [-2.3, 2.3]) {
			const bx = sx + s * 8.8;
			cham(M, `forearm_${n}`, bx - 1.5, A.wristY - 7, z - 1.5, bx + 1.5, A.elbowY - 2, z + 1.5, 0.6, 'p_greyD', { tag: 'ribs', seed: 38, ribPer: 2.2 });
			M.box(`forearm_${n}`, bx - 1.0, A.wristY - 7.6, z - 1.0, bx + 1.0, A.wristY - 6.8, z + 1.0, 'p_glow', { seed: 39 });
		}
		M.box(`forearm_${n}`, ...[X(A.shX + 7.2, A.shX + 8.0)[0], A.wristY - 5, -4.4, X(A.shX + 7.2, A.shX + 8.0)[1], A.elbowY - 3, 4.4], 'p_greyD', { seed: 40 }); // barrel mount
		// legs: thick teal thighs, grey knee caps, heavy grey shins, wide feet
		cham(M, `thigh_${n}`, lx - 6.4, A.kneeY + 1, -6.4, lx + 6.4, A.hipY - 1, 6.4, 2.2, 'p_teal', { tag: 'panel', seed: 42 });
		cham(M, `shin_${n}`, lx - 4.6, A.kneeY - 2.5, -7.4, lx + 4.6, A.kneeY + 3.5, -4.4, 1.4, 'p_grey', { seed: 43, tag: 'dots' });
		cham(M, `shin_${n}`, lx - 6.6, A.ankleY + 0.5, -6.8, lx + 6.6, A.kneeY - 1, 6.6, 2.4, 'p_grey', { tag: 'panel ostripe', seed: 44 });
		cham(M, `foot_${n}`, lx - 6.4, 0, -11.8, lx + 6.4, A.ankleY + 1.5, 6.4, 2.2, 'p_greyD', { tag: 'sole panel', seed: 45 });
		for (const x of [-3.6, 0, 3.6]) M.box(`foot_${n}`, lx + x - 1.5, 0, -14.4, lx + x + 1.5, 3.0, -11, 'p_grey', { tag: 'sole', seed: 46 });
	}
	// muzzle locators (between each gauntlet's twin barrels) for the blast origin
	for (const [s, n] of SIDES) M.bone(`muzzle_${n}`, `forearm_${n}`, [s * A.shX + s * 8.8, A.wristY - 7.6, 0]);
	return M;
}
function punisherShade(M, c, f, s, t, p, info) {
	const base = { p_teal: PUN.teal, p_tealD: PUN.tealD, p_grey: PUN.grey, p_greyD: PUN.greyD, p_orange: PUN.orange, p_joint: [56, 62, 68] }[c.mat];
	if (base) {
		let [col, g] = plate(base, c, f, s, t, p, info, ORANGE_GLOW);
		const [u, v] = UV(s, t, info);
		if (has(c, 'hazard') && f !== 'up' && f !== 'down') { if (((u + v) % 2.4 + 2.4) % 2.4 < 1.0) col = mul([40, 40, 44], 1.0); }
		if (has(c, 'ostripe') && f !== 'up' && f !== 'down' && Math.abs(v - info.fh * 0.3) < 0.9) col = mix(col, PUN.orange, 0.9);
		if (has(c, 'chev') && f === 'north') { const x = u - info.fw / 2; const q = (((v - Math.abs(x) * 0.6) % 3.6) + 3.6) % 3.6; if (q > 0.35 && q < 1.1 && v < info.fh * 0.55) col = mix(col, PUN.orange, 0.85); }
		if (c.mat === 'p_joint') { const q = ((p[1] % 1.4) + 1.4) % 1.4; col = mul(col, q < 0.4 ? 0.6 : 1); }
		col = mix(col, mul(col, 0.8 + 0.4 * fbm(p[0] * 0.5, p[1] * 0.5, p[2] * 0.5 + 9, 3)), 0.5);                   // weathering
		if (fbm(p[0] * 0.3 + 40, p[1] * 0.3, p[2] * 0.3, 3) > 0.68) col = mix(col, [70, 60, 52], 0.35);             // scorch/grime
		return [col, 255, g];
	}
	if (c.mat === 'p_dome') { // a smooth faceless dome: a soft sky reflection band, a hot highlight, nothing else
		const w = f === 'up' ? 0.1 : t + (fbm(p[0] * 0.1, p[2] * 0.1, 4, 2) - 0.5) * 0.3;
		let col = w < 0.45 ? mix([196, 214, 214], [120, 150, 152], w / 0.45) : mix([60, 86, 90], [96, 120, 124], (w - 0.45) / 0.55);
		if (f !== 'up' && f !== 'down' && Math.abs(s - 0.32) < 0.06 && t < 0.5) col = mix(col, [255, 255, 255], 0.5);
		if (info.ePx < 0.25) col = mix(col, [230, 240, 240], 0.3);
		return [col, 255, null];
	}
	if (c.mat === 'p_glow') return [[255, 214, 150], 255, ORANGE_GLOW];
	return null;
}
function punisherPoses(M) {
	const stand = Object.assign({
		arm_r: [-6, 0, 2], forearm_r: [-20, 0, 0], arm_l: [-6, 0, -2], forearm_l: [-20, 0, 0],
		thigh_r: [-6, 0, 2], shin_r: [10, 0, 0], foot_r: [-4, 0, 0], thigh_l: [-6, 0, -2], shin_l: [10, 0, 0], foot_l: [-4, 0, 0],
		head: [4, 0, 0],
	}, FIST('r'), FIST('l'));
	const blast = Object.assign({
		waist: [8, 10, 0], chest: [6, 6, 0], head: [8, 8, 0],
		arm_r: [-82, 0, -8], forearm_r: [-10, 0, 0], arm_l: [-76, 0, 12], forearm_l: [-18, 0, 0],
		thigh_l: [-30, 0, -6], shin_l: [26, 0, 0], foot_l: [4, 0, 0],
		thigh_r: [18, 0, 8], shin_r: [16, 0, 0], foot_r: [-30, 0, 0],
	}, FIST('r'), FIST('l'));
	return { stand, blast };
}
function renderPunisher(M) {
	const A = M.A;
	const bg = { top: [96, 104, 112], bottom: [58, 62, 70] };
	const pre = ctx => stars(ctx, 120, 33, 0.35);
	const it = (pose, extra = {}) => Object.assign({ model: M, pose, root: pose.$root || planted(M, pose), floorR: 5.5 }, extra);
	const bl = it(M.poses.blast);
	const muzzles = [];
	for (const [s, n] of SIDES) for (const z of [-2.3, 2.3]) {
		const a = wpt(bl, `forearm_${n}`, [s * A.shX + s * 8.8, A.wristY - 7.4, z]);
		const b = wpt(bl, `forearm_${n}`, [s * A.shX + s * 8.8, A.wristY - 7.4 - 120, z]);
		muzzles.push([a, b]);
	}
	const row1 = sheet(null, 'THE PUNISHER', "9 BLOCKS - GALACTUS'S ROBOT SENTINEL: FACELESS DOME, GREY / TEAL ARMOUR, ORANGE ACCENTS, BLASTER GAUNTLETS", [
		{ label: 'FRONT', items: [it(M.poses.stand)], cam: { yaw: 0, pitch: 6 }, pre },
		{ label: 'THREE-QUARTER', items: [it(M.poses.stand)], cam: { yaw: 35, pitch: 6 }, pre },
		{ label: 'SIDE', items: [it(M.poses.stand)], cam: { yaw: 90, pitch: 6 }, pre },
		{
			label: 'ACTION: BLASTING', items: [bl], cam: { yaw: 52, pitch: 8 }, ownScale: true, pre, extraPts: muzzles.map(m => vadd(m[0], vsc(vnorm(vsub(m[1], m[0])), 7))),
			post: ctx => { for (const [a, b] of muzzles) { const e = vadd(a, vsc(vnorm(vsub(b, a)), 7)); beam(ctx, a, e, 0.16, [255, 240, 210], ORANGE_GLOW, { taper: 0.2 }); flare(ctx, a, 0.9, [255, 240, 210], ORANGE_GLOW, 0.9); flare(ctx, e, 1.4, [255, 230, 190], ORANGE_GLOW, 0.8); } },
		},
	], { returnCanvas: true, panelH: 1150, minW: 2700, maxW: 3600, bg, aoR: 0.3, edgeThr: 0.1, shadowSoft: 0.3 });
	const only = re => c => !re.test(c.bone);
	const row2 = sheet(null, '', '', [
		{ label: 'DOME HEAD', items: [it(M.poses.stand, { hide: only(/^(head|neck)$/), noFloor: true })], cam: { yaw: 30, pitch: 10 }, ownScale: true, ownScaleMax: 20, noOrigin: true, pre, note: ['FACELESS - NO EYES, NO MOUTH'] },
		{ label: 'BLASTER GAUNTLET', items: [it(M.poses.stand, { hide: only(/^(forearm_l|hand_l|fingers_l|fingertips_l|thumb_l)$/), noFloor: true })], cam: { yaw: 60, pitch: 14 }, ownScale: true, ownScaleMax: 20, noOrigin: true, pre, note: ['TWIN BARRELS, ORANGE MUZZLES'] },
		{ label: 'CHEST', items: [it(M.poses.stand, { hide: only(/^(chest|waist|hips)$/), noFloor: true })], cam: { yaw: 20, pitch: 6 }, ownScale: true, ownScaleMax: 20, noOrigin: true, pre },
		{ label: 'BACK', items: [it(M.poses.stand, { noFloor: true })], cam: { yaw: 160, pitch: 8 }, ownScale: true, ownScaleMax: 20, noOrigin: true, pre, note: ['EXHAUST PORTS'] },
	], { returnCanvas: true, titleH: 0, panelH: 640, minW: row1.w, maxW: row1.w, bg, shadow: false, aoR: 0.1, edgeThr: 0.05 });
	stackRows(path.join(OUT, '03_punisher.png'), [row1, row2]);
}

// ================================================================ ELEMENT CONVERTER
// A 25-block alien tower Galactus plants to drain a world: angular violet-steel Kirby tech, four buttress legs, glowing
// energy conduits up every face, a pulsing Power Cosmic core in an open cage with a tilted orbit ring, and a crown of
// four prongs (the tuning-fork motif from Galactus's helmet) around an emitter orb. 4 model px per block.
const CONVERTER_PRONGS = [[-1, -1], [1, -1], [-1, 1], [1, 1]];
const EC = { hull: [74, 66, 104], hullD: [46, 40, 70], trim: [124, 70, 176], blue: [52, 82, 164], glow: [176, 130, 255], core: [236, 226, 255] };
function buildConverter() {
	const M = new Model('element_converter', { TW: 1024, D: 3 });
	const root = M.bone('root', null, [0, 0, 0]);
	// base platform + glowing seam
	cham(M, root, -20, 0, -20, 20, 5, 20, 6, 'ec_hull', { tag: 'panel ribs', ribPer: 2.5, seed: 1 });
	cham(M, root, -20.4, 4.6, -20.4, 20.4, 5.4, 20.4, 6.1, 'ec_conduit', { seed: 2 });
	cham(M, root, -16, 5.4, -16, 16, 8, 16, 5, 'ec_trim', { tag: 'circles', seed: 3 });
	// buttress legs from the ground corners up into the column
	for (const [sx, sz] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) {
		M.stick(root, [sx * 23, -0.5, sz * 23], [-sx * 0.55, 1, -sz * 0.55], 32, [5.2, 5.2], 'ec_hull', { tag: 'panel', seed: 4 });
		M.cbox(root, [sx * 23, 1.5, sz * 23], [8, 3, 8], 'ec_trim', { seed: 5 });
		M.stick(root, [sx * 22.2, 3, sz * 22.2], [-sx * 0.55, 1, -sz * 0.55], 26, [1.4, 1.4], 'ec_conduit', { seed: 6 });
	}
	// lower column
	cham(M, root, -11, 8, -11, 11, 32, 11, 3.4, 'ec_hull', { tag: 'panel circles', seed: 7, circY: 0.3 });
	cham(M, root, -14, 30, -14, 14, 35, 14, 4.4, 'ec_trim', { tag: 'ribs', ribPer: 1.6, seed: 8 });
	// angular Kirby fins down each face of the lower column (stepped, so they taper toward the top)
	for (const [ax, az] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) for (const o of [-6.5, 6.5]) {
		const fin = (out, y0, y1) => ax ? M.box(root, ax > 0 ? 11 : -11 - out, y0, o - 0.8, ax > 0 ? 11 + out : -11, y1, o + 0.8, 'ec_trim', { tag: 'panel', seed: 32 })
			: M.box(root, o - 0.8, y0, az > 0 ? 11 : -11 - out, o + 0.8, y1, az > 0 ? 11 + out : -11, 'ec_trim', { tag: 'panel', seed: 32 });
		fin(5.5, 8, 18); fin(3.8, 18, 25); fin(2.0, 25, 30);
	}
	// core cage: corner pillars, the core itself and a tilted orbit ring
	for (const [sx, sz] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) {
		M.box(root, sx * 9 - 2, 35, sz * 9 - 2, sx * 9 + 2, 56, sz * 9 + 2, 'ec_hull', { tag: 'vrib', seed: 9 });
		M.box(root, sx * 9 - 0.6, 36, sz * 9 - 2.3, sx * 9 + 0.6, 55, sz * 9 + 2.3, 'ec_conduit', { seed: 10 });
	}
	const core = M.bone('core', 'root', [0, 45, 0]);
	[[36, 38, 7], [38, 40, 11], [40, 43, 14], [43, 47, 15.4], [47, 50, 14], [50, 52, 11], [52, 54, 7]].forEach(([y0, y1, w], i) => cham(M, core, -w / 2, y0, -w / 2, w / 2, y1, w / 2, w * 0.28, 'ec_core', { seed: 11 + i }));
	const ring = M.bone('core_ring', 'core', [0, 45, 0], [22, 0, 14]);
	for (const s of [-1, 1]) { M.box(ring, -12.5, 44.4, s * 11.5 - 0.7, 12.5, 45.6, s * 11.5 + 0.7, 'ec_trim', { seed: 20 }); M.box(ring, s * 11.5 - 0.7, 44.4, -12.5, s * 11.5 + 0.7, 45.6, 12.5, 'ec_trim', { seed: 21 }); }
	// upper column (the `spire` bone, so it can crack and topple), narrowing, with the hovering halo disc
	const spire = M.bone('spire', 'root', [0, 55, 0]);
	cham(M, spire, -13, 55, -13, 13, 59, 13, 4, 'ec_trim', { tag: 'ribs', ribPer: 1.6, seed: 22 });
	cham(M, spire, -8.5, 59, -8.5, 8.5, 72, 8.5, 2.6, 'ec_hull', { tag: 'panel', seed: 23 });
	cham(M, spire, -6.5, 72, -6.5, 6.5, 82, 6.5, 2.0, 'ec_blue', { tag: 'panel ribs', ribPer: 2.2, seed: 24 });
	cham(M, spire, -15, 76.5, -15, 15, 78, 15, 5, 'ec_trim', { tag: 'dots', seed: 33 });
	cham(M, spire, -15.4, 76.2, -15.4, 15.4, 76.6, 15.4, 5.1, 'ec_conduit', { seed: 34 });
	for (const [sx, sz] of [[0, -1], [0, 1], [-1, 0], [1, 0]]) { // conduits up each face
		const x = sx * 11.2, z = sz * 11.2;
		M.box(root, sx ? x - 0.5 : -0.8, 8.5, sz ? z - 0.5 : -0.8, sx ? x + 0.5 : 0.8, 31, sz ? z + 0.5 : 0.8, 'ec_conduit', { seed: 25 });
		const x2 = sx * 8.7, z2 = sz * 8.7;
		M.box(spire, sx ? x2 - 0.5 : -0.7, 59.5, sz ? z2 - 0.5 : -0.7, sx ? x2 + 0.5 : 0.7, 71.5, sz ? z2 + 0.5 : 0.7, 'ec_conduit', { seed: 26 });
	}
	// the crown: four prongs (one bone each, so they can flex open while consuming) around the emitter orb
	const crown = M.bone('crown', 'spire', [0, 82, 0]);
	cham(M, crown, -9, 81, -9, 9, 84, 9, 3, 'ec_trim', { tag: 'dots', seed: 27 });
	CONVERTER_PRONGS.forEach(([sx, sz], i) => {
		const pb = M.bone(`prong_${i}`, 'crown', [sx * 6, 83, sz * 6]);
		M.stick(pb, [sx * 6, 83, sz * 6], [sx * 0.28, 1, sz * 0.28], 16, [2.8, 2.8], 'ec_trim', { tag: 'panel', seed: 28 });
		M.stick(pb, [sx * (6 + 0.28 * 15), 98, sz * (6 + 0.28 * 15)], [sx * 0.28, 1, sz * 0.28], 3, [1.6, 1.6], 'ec_core', { seed: 29 });
	});
	cham(M, crown, -3, 84, -3, 3, 90, 3, 1.2, 'ec_blue', { seed: 30 });
	cham(M, crown, -3.6, 90, -3.6, 3.6, 96, 3.6, 1.4, 'ec_core', { seed: 31 });
	M.bone('beam_origin', 'crown', [0, 96, 0]);
	// the shield: a faceted Power Cosmic shell around the whole tower, hidden unless the `shielded` clip shows it
	const shield = M.bone('shield', 'root', [0, 50, 0]);
	cham(M, shield, -31, -1, -31, 31, 84, 31, 11, 'ec_shield', { seed: 40 });
	cham(M, shield, -25, 84, -25, 25, 95, 25, 9, 'ec_shield', { seed: 41 });
	cham(M, shield, -16, 95, -16, 16, 103, 16, 6, 'ec_shield', { seed: 42 });
	return M;
}

// The ring of consumed ground around the base: ash-grey, flattened, cracked, with glowing drain fissures running in to
// the tower; it gives way to grass and dirt at the rim. Grey petrified trees inside, live ones outside.
function buildGround(radiusBlocks = 23) {
	const M = new Model('consumed_ground', { TW: 2048, D: 2 });
	const root = M.bone('root', null, [0, 0, 0]);
	const rnd = seeded(77);
	const T = 8; // 2-block tiles
	const R = radiusBlocks * 4;
	for (let gx = -R; gx < R; gx += T) for (let gz = -R; gz < R; gz += T) {
		const cx = gx + T / 2, cz = gz + T / 2, r = Math.hypot(cx, cz) / 4;
		if (r > radiusBlocks) continue;
		if (Math.abs(cx) < 18 && Math.abs(cz) < 18) continue; // under the platform
		const edge = 14 + fbm(cx * 0.05, cz * 0.05, 3, 3) * 6;
		const ash = r < edge;
		const top = ash ? -0.6 - rnd() * 1.2 : 0.4 + rnd() * 1.6 + (r > edge + 3 ? rnd() * 1.2 : 0);
		M.box(root, gx, -8, gz, gx + T, top, gz + T, ash ? 'gr_ash' : 'gr_grass', { seed: Math.floor(rnd() * 1000) });
	}
	const tree = (x, z, dead, h) => {
		const mat = dead ? 'gr_deadwood' : 'gr_log';
		M.box(root, x - 1.2, 0, z - 1.2, x + 1.2, h, z + 1.2, mat, { seed: 5 });
		const leaf = dead ? 'gr_deadleaf' : 'gr_leaf';
		M.box(root, x - 5, h - 3, z - 5, x + 5, h + 2, z + 5, leaf, { seed: 6 });
		M.box(root, x - 3, h + 2, z - 3, x + 3, h + 5, z + 3, leaf, { seed: 7 });
	};
	tree(-34, -22, true, 14); tree(30, -30, true, 12); tree(38, 22, true, 13); tree(-26, 40, true, 11);
	tree(-78, -26, false, 16); tree(70, -44, false, 15); tree(-52, 66, false, 14); tree(80, 30, false, 17);
	for (const [x, z, s] of [[-44, 4, 4], [22, 44, 3], [46, -8, 3.5], [-12, -48, 3]]) M.box(root, x - s, 0, z - s * 0.8, x + s, s * 1.2, z + s * 0.8, 'gr_rock', { seed: 8 });
	M.k = 0.25; M.MODEL_SCALE = 4; M.heightBlocks = 1;
	return M;
}
function converterShade(M, c, f, s, t, p, info) {
	const base = { ec_hull: EC.hull, ec_hullD: EC.hullD, ec_trim: EC.trim, ec_blue: EC.blue }[c.mat];
	if (base) {
		let [col, g] = plate(base, c, f, s, t, p, info, EC.glow);
		col = mul(col, 0.92 + 0.16 * fbm(p[0] * 0.15, p[1] * 0.15, p[2] * 0.15 + 3, 3));
		return [col, 255, g];
	}
	if (c.mat === 'ec_shield') return [[200, 170, 255], 0, null]; // invisible in the concept renders; the game look makes it a glow shell
	if (c.mat === 'ec_conduit') { const k = 0.5 + 0.5 * Math.sin(p[1] * 0.9); return [mix(EC.glow, EC.core, 0.2 + 0.5 * k), 255, mul(mix(EC.glow, EC.core, 0.1 + 0.3 * k), 0.62)]; }
	if (c.mat === 'ec_core') { // swirling Power Cosmic: violet bands with white-hot streaks and Kirby dots
		const sw = fbm(p[0] * 0.18 + 4, p[1] * 0.3, p[2] * 0.18, 3);
		const band = Math.abs(Math.sin(sw * 10 + p[1] * 0.5));
		let col = mix([120, 70, 220], [250, 246, 255], Math.pow(band, 2.5));
		let gk = 0.35 + 0.35 * Math.pow(band, 2.5);
		if (hash3(Math.floor(p[0] * 1.3), Math.floor(p[1] * 1.3), Math.floor(p[2] * 1.3)) > 0.93 && info.ePx > 0.5) { col = [30, 20, 50]; gk = 0; }
		if (info.ePx < 0.3) col = mix(col, [255, 255, 255], 0.4);
		return [col, 255, mul(mix([150, 100, 255], [240, 236, 255], Math.pow(band, 2)), gk)];
	}
	if (c.mat === 'gr_ash') {
		const n = fbm(p[0] * 0.2 + 1, p[1] * 0.2, p[2] * 0.2, 4);
		let col = mix([96, 96, 102], [168, 168, 172], n);
		let g = null;
		if (f === 'up') {
			const th = Math.atan2(p[2], p[0]), r = Math.hypot(p[0], p[2]);
			const fis = Math.abs(Math.sin(th * 7 + fbm(p[0] * 0.04, p[2] * 0.04, 1, 3) * 4 + r * 0.02));
			if (fis < 0.035 && r > 20) { col = [214, 190, 255]; g = EC.glow; }
			else if (fis < 0.08 && r > 20) col = mul(col, 0.55);
			if (Math.abs(fbm(p[0] * 0.15 + 7, p[2] * 0.15, 2, 3) - 0.5) < 0.015) col = mul(col, 0.5);   // cracks
		} else col = mul(col, f === 'down' ? 0.5 : 0.8 - 0.25 * t);
		if (info.ePx < 0.3) col = mul(col, 0.85);
		return [col, 255, g];
	}
	if (c.mat === 'gr_grass') {
		const n = fbm(p[0] * 0.3, p[1] * 0.3 + 5, p[2] * 0.3, 3);
		if (f === 'up') return [mix([62, 118, 44], [104, 160, 64], n), 255, null];
		const dirt = mix([96, 66, 44], [134, 96, 64], n);
		return [t < 0.08 ? mix([62, 118, 44], [104, 160, 64], n) : mul(dirt, 1 - 0.2 * t), 255, null];
	}
	if (c.mat === 'gr_log') return [mul(mix([86, 62, 40], [120, 90, 58], fbm(p[0], p[1] * 0.3, p[2], 2)), f === 'up' ? 1.1 : 1), 255, null];
	if (c.mat === 'gr_leaf') return [mix([40, 96, 34], [80, 140, 56], fbm(p[0] * 0.5, p[1] * 0.5, p[2] * 0.5, 3)), 255, null];
	if (c.mat === 'gr_deadwood') return [mix([90, 90, 96], [140, 140, 146], fbm(p[0], p[1] * 0.3, p[2], 2)), 255, null];
	if (c.mat === 'gr_deadleaf') return [mul(mix([110, 110, 116], [170, 170, 176], fbm(p[0] * 0.5, p[1] * 0.5, p[2] * 0.5, 3)), hash3(info.i >> 2, info.j >> 2, 3) > 0.7 ? 0.7 : 1), 255, null];
	if (c.mat === 'gr_rock') return [mix([90, 90, 96], [150, 150, 156], fbm(p[0] * 0.4, p[1] * 0.4, p[2] * 0.4, 3)), 255, null];
	return null;
}
function renderConverter(M, G) {
	const bg = { top: [86, 80, 112], bottom: [52, 50, 66] };
	const pre = ctx => { nebula(ctx, [[80, 60, 140], [60, 80, 150]], 0.18, 9); stars(ctx, 200, 41, 0.6); };
	const it = (extra = {}) => Object.assign({ model: M, pose: {}, root: [0, 0, 0], floorR: 9, hide: c => c.bone === 'shield' }, extra);
	const gi = { model: G, pose: {}, root: [0, 0, 0], noFloor: true };
	const k = M.k;
	const top = [0, 93 * k, 0], coreC = [0, 45 * k, 0];
	const drains = [];
	for (let i = 0; i < 10; i++) { const a = i / 10 * Math.PI * 2 + 0.2; drains.push([Math.cos(a) * 15, 0.3, Math.sin(a) * 15]); }
	const action = ctx => {
		beam(ctx, top, vadd(top, [0, 30, 0]), 0.9, [255, 255, 255], EC.glow, { taper: -0.5 });
		flare(ctx, top, 4, [255, 255, 255], EC.glow, 0.9);
		flare(ctx, coreC, 6, [255, 255, 255], EC.glow, 0.55, true);
		krackle(ctx, coreC, 3.2, 30, 3, EC.glow, [255, 255, 255]);
		for (const d of drains) beam(ctx, d, [d[0] * 0.25, 1.2, d[2] * 0.25], 0.18, [236, 226, 255], EC.glow, { emi: 0.6 });
	};
	const row1 = sheet(null, 'ELEMENT CONVERTER', '25 BLOCKS - ALIEN TOWER THAT DRAINS THE WORLD: CONDUITS, PULSING CORE, PRONG CROWN, A RING OF CONSUMED GROUND', [
		{ label: 'FRONT', items: [it()], cam: { yaw: 0, pitch: 6 }, pre },
		{ label: 'THREE-QUARTER', items: [it()], cam: { yaw: 35, pitch: 8 }, pre },
		{ label: 'SIDE', items: [it()], cam: { yaw: 90, pitch: 6 }, pre },
		{ label: 'ACTION: CONVERTING - THE GROUND IS CONSUMED', items: [gi, it({ noFloor: true })], cam: { yaw: 30, pitch: 22 }, ownScale: true, ownScaleMax: 1.3, pre, post: action, extraPts: [vadd(top, [0, 8, 0])], fitW: 1500 },
	], { returnCanvas: true, panelH: 1150, minW: 2900, maxW: 3500, bg, aoR: 0.35, edgeThr: 0.18, shadowSoft: 0.5, contactH: 1.5 });
	const only = re => c => !re.test(c.tag || '');
	const row2 = sheet(null, '', '', [
		{ label: 'CONSUMED GROUND - FROM ABOVE', items: [gi, it({ noFloor: true })], cam: { yaw: 20, pitch: 58 }, ownScale: true, ownScaleMax: 20, noOrigin: true, pre, note: ['ASH-GREY, CRACKED, GLOWING DRAIN FISSURES, GRASS AT THE RIM'], fitW: 1300 },
		{ label: 'CORE CAGE', items: [it({ noFloor: true, hide: c => !(c.from[1] > 29 && c.to[1] < 60) || c.bone === 'crown' })], cam: { yaw: 28, pitch: 10 }, ownScale: true, ownScaleMax: 20, noOrigin: true, pre, note: ['PULSING POWER COSMIC CORE, TILTED ORBIT RING'] },
		{ label: 'CROWN EMITTER', items: [it({ noFloor: true, hide: c => !/^(crown|prong)/.test(c.bone) })], cam: { yaw: 30, pitch: 18 }, ownScale: true, ownScaleMax: 20, noOrigin: true, pre, note: ['THE TUNING-FORK MOTIF'] },
	], { returnCanvas: true, titleH: 0, panelH: 760, minW: row1.w, maxW: row1.w, bg, shadow: false, aoR: 0.2, edgeThr: 0.1 });
	stackRows(path.join(OUT, '04_element_converter.png'), [row1, row2]);
}

// ================================================================ THE WORLDSHIP (sky backdrop prop)
// Kirby's planet-eating vessel: a vast machined sphere wrapped in an equatorial ring of hangar pods, a crown of spires
// on top (the tuning-fork prongs again), and an inverted cone below ending in the Power Cosmic intake aperture.
// Render-only prop, about 240 blocks across (2 blocks per model px).
const WS = { hull: [76, 80, 128], hullD: [48, 50, 86], trim: [118, 68, 170], blue: [50, 84, 168] };
function buildWorldship() {
	const M = new Model('worldship', { TW: 2048, D: 3 });
	const root = M.bone('root', null, [0, 0, 0]);
	const R = 30;
	for (let y = -R; y < R; y += 3) { // the sphere as stacked octagonal slabs
		const yc = y + 1.5, w = 2 * Math.sqrt(Math.max(0, R * R - yc * yc)) + 1;
		cham(M, root, -w / 2, y, -w / 2, w / 2, y + 3, w / 2, w * 0.29, Math.abs(yc) < 8 ? 'ws_band' : 'ws_hull', { tag: 'panel', seed: 100 + y });
	}
	// equatorial ring: 16 rotated segments + 4 big hangar pods + struts back to the sphere
	const ring = M.bone('ring', 'root', [0, 0, 0]);
	const RR = 50, n = 16, half = RR * Math.tan(Math.PI / n) + 0.6;
	for (let i = 0; i < n; i++) {
		const a = i * 360 / n;
		M.box(ring, -half, -3.2, RR - 5, half, 3.2, RR + 5, 'ws_ring', { rot: [0, a, 0], pivot: [0, 0, 0], tag: 'circles', seed: 200 + i });
		M.box(ring, -half + 0.4, -4.4, RR - 3, half - 0.4, -3.0, RR + 3, 'ws_trim', { rot: [0, a, 0], pivot: [0, 0, 0], seed: 220 + i });
	}
	for (let i = 0; i < 4; i++) {
		const a = 45 + i * 90;
		M.box(ring, -7, -9, RR - 7, 7, 9, RR + 8, 'ws_pod', { rot: [0, a, 0], pivot: [0, 0, 0], tag: 'panel', seed: 240 + i });
		M.box(ring, -5, 9, RR - 5, 5, 13, RR + 5, 'ws_trim', { rot: [0, a, 0], pivot: [0, 0, 0], tag: 'dots', seed: 250 + i });
		M.box(ring, -1.6, -1.6, R - 4, 1.6, 1.6, RR - 6, 'ws_trim', { rot: [0, a + 45, 0], pivot: [0, 0, 0], seed: 260 + i });          // strut
		M.box(ring, -2, -12, RR - 2, 2, -9, RR + 2, 'ws_glow', { rot: [0, a, 0], pivot: [0, 0, 0], seed: 270 + i });
	}
	// crown of spires
	const crown = M.bone('crown', 'root', [0, R, 0]);
	cham(M, crown, -8, R - 2, -8, 8, R + 6, 8, 2.6, 'ws_trim', { tag: 'ribs', ribPer: 2, seed: 300 });
	cham(M, crown, -4, R + 6, -4, 4, R + 34, 4, 1.4, 'ws_hull', { tag: 'panel', seed: 301 });
	M.cbox(crown, [0, R + 36, 0], [5, 5, 5], 'ws_glow', { seed: 302 });
	for (const [sx, sz, h] of [[-1, 0, 28], [1, 0, 28], [0, -1, 18], [0, 1, 18]]) {
		const b = [sx * 10, R - 3, sz * 10];
		M.stick(crown, b, [sx * 0.22, 1, sz * 0.22], h, [3.4, 3.4], sx ? 'ws_trim' : 'ws_hull', { tag: 'panel', seed: 310 });
		M.stick(crown, vadd(b, [sx * 0.22 * h * 0.92, h * 0.92, sz * 0.22 * h * 0.92]), [sx * 0.22, 1, sz * 0.22], 3, [2, 2], 'ws_glow', { seed: 311 });
	}
	for (let i = 0; i < 8; i++) { const a = (i + 0.5) / 8 * Math.PI * 2; M.stick(crown, [Math.cos(a) * 17, R - 8, Math.sin(a) * 17], [Math.cos(a) * 0.5, 1, Math.sin(a) * 0.5], 10, [1.6, 1.6], 'ws_hull', { seed: 320 + i }); }
	// the intake cone and its aperture
	const cone = M.bone('cone', 'root', [0, -R, 0]);
	[[-R - 4, -R + 2, 30], [-R - 10, -R - 4, 22], [-R - 16, -R - 10, 15], [-R - 21, -R - 16, 9]].forEach(([y0, y1, w], i) => cham(M, cone, -w / 2, y0, -w / 2, w / 2, y1, w / 2, w * 0.29, i % 2 ? 'ws_trim' : 'ws_hull', { tag: 'ribs', ribPer: 1.8, seed: 330 + i }));
	cham(M, cone, -5, -R - 22.5, -5, 5, -R - 21, 5, 1.5, 'ws_aperture', { seed: 340 });
	M.bone('aperture', 'cone', [0, -R - 23, 0]); // locator: the intake beam origin
	return M;
}
function worldshipShade(M, c, f, s, t, p, info) {
	const base = { ws_hull: WS.hull, ws_band: WS.blue, ws_trim: WS.trim, ws_ring: WS.hullD, ws_pod: WS.hull }[c.mat];
	if (base) {
		let [col, g] = plate(base, c, f, s, t, p, info);
		col = mul(col, 0.9 + 0.2 * fbm(p[0] * 0.08, p[1] * 0.08, p[2] * 0.08, 3));
		// city lights: a sparse grid of glowing windows on the sides
		if (f !== 'up' && f !== 'down' && info.ePx > 0.8) {
			const gx = Math.floor(info.i / 3), gy = Math.floor(info.j / 3);
			if ((info.i % 3) === 1 && (info.j % 3) === 1 && hash3(gx, gy, c.seed + 5) > (c.mat === 'ws_band' ? 0.55 : 0.82)) { col = [236, 240, 255]; g = [170, 200, 255]; }
		}
		if (c.mat === 'ws_band' && f !== 'up' && f !== 'down' && Math.abs(t - 0.5) < 0.08) { col = mix(PC_WHITE, PC_GLOW, 0.3); g = mul(PC_GLOW, 0.8); }
		return [col, 255, g];
	}
	if (c.mat === 'ws_glow') return [PC_WHITE, 255, PC_GLOW];
	if (c.mat === 'ws_aperture') { const d = Math.hypot(s - 0.5, t - 0.5) * 2; return [mix(PC_WHITE, PC_VIOLET, d), 255, mix(PC_WHITE, PC_VIOLET, d)]; }
	return null;
}
function renderWorldship(M) {
	const space = { top: [16, 14, 34], bottom: [40, 32, 70] };
	const spacePre = ctx => { nebula(ctx, [[90, 50, 150], [40, 80, 160]], 0.4, 13); stars(ctx, 700, 51, 1.2); };
	const it = { model: M, pose: {}, root: [0, 0, 0], noFloor: true };
	const k = M.k;
	const ap = [0, (-30 - 22) * k, 0];
	const groundY = -190;
	// the hero shot: dusk over a Minecraft landscape, the ship filling the sky, its intake beam on the horizon
	const skyPre = ctx => {
		for (let y = 0; y < ctx.H; y++) {
			const tt = y / ctx.H;
			const col = tt < 0.55 ? mix([22, 16, 48], [96, 56, 120], tt / 0.55) : mix([96, 56, 120], [236, 140, 96], (tt - 0.55) / 0.45);
			for (let x = 0; x < ctx.W; x++) { const j = (y * ctx.W + x) * 3; ctx.col[j] = col[0]; ctx.col[j + 1] = col[1]; ctx.col[j + 2] = col[2]; }
		}
		nebula(ctx, [[150, 80, 170], [70, 90, 180]], 0.22, 4);
		stars(ctx, 500, 61, 0.9);
	};
	const skyPost = ctx => {
		const g = ctx.proj([0, groundY, 0]);
		beam(ctx, ap, [0, groundY + 20, 0], 9, [250, 246, 255], PC_VIOLET, { taper: -0.6, emi: 0.6 });
		flare(ctx, ap, 22, PC_WHITE, PC_VIOLET, 0.8);
		// a blocky Minecraft landscape silhouette in front of everything: stepped hills, trees, a few houses
		const hz = Math.min(ctx.H - 2, Math.round(g[1]));
		const B = 9 * ctx.ss, ncol = Math.ceil(ctx.W / B) + 2;
		const hgt = [];
		for (let c = 0; c < ncol; c++) hgt.push(2 + Math.round(5 * fbm(c * 0.09 + 3, 1, 2, 3) + 5 * Math.max(0, Math.sin(c * 0.045 + 0.6))));
		const sil = hgt.slice();
		for (let c = 2; c < ncol - 2; c++) {
			const h = hash3(c, 7, 9);
			if (h > 0.86) { sil[c] = Math.max(sil[c], hgt[c] + 4); for (const d of [-1, 0, 1]) sil[c + d] = Math.max(sil[c + d], hgt[c] + 4 + (d ? 0 : 1)); }
			else if (h < 0.05) { for (const d of [0, 1, 2]) sil[c + d] = Math.max(sil[c + d], hgt[c] + 2 + (d === 1 ? 1 : 0)); }
		}
		for (let x = 0; x < ctx.W; x++) {
			const top = hz - sil[Math.floor(x / B)] * B;
			const d = Math.abs(x - g[0]) / ctx.W;
			for (let y = Math.max(0, top); y < ctx.H; y++) {
				const j = (y * ctx.W + x) * 3;
				const rim = Math.max(0, 1 - d * 5) * 0.45 * Math.max(0, 1 - (y - top) / (14 * ctx.ss));
				const sky = Math.max(0, 1 - (y - top) / (5 * ctx.ss)) * 0.25;
				ctx.col[j] = 20 + 160 * rim + 90 * sky; ctx.col[j + 1] = 16 + 120 * rim + 50 * sky; ctx.col[j + 2] = 34 + 180 * rim + 50 * sky;
				ctx.emi[j] = ctx.emi[j + 1] = ctx.emi[j + 2] = 0;
			}
		}
		flare(ctx, [0, groundY + 4, 0], 30, [255, 250, 255], PC_VIOLET, 0.6);
	};
	const row1 = sheet(null, 'THE WORLDSHIP', "SKY BACKDROP PROP - GALACTUS'S PLANET-EATING VESSEL, ABOUT 240 BLOCKS ACROSS, HANGS OVER THE EVENT", [
		{ label: 'IN THE SKY: THE WORLDSHIP ARRIVES', items: [it], cam: { yaw: 18, pitch: -12 }, ownScale: true, ownScaleMax: 20, pre: skyPre, post: skyPost,
			light: Object.assign({}, L.DEFAULT_LIGHT, { keyK: 0.5, amb: 0.34, rim: vnorm([0.25, -0.85, 0.2]), rimCol: [1.0, 0.62, 0.42], rimK: 0.95 }), extraPts: [[0, groundY, 0], [-130, groundY, 0], [130, groundY, 0]], fitW: 2300 },
		{ label: 'THREE-QUARTER', items: [it], cam: { yaw: 30, pitch: 22 }, ownScale: true, ownScaleMax: 20, pre: spacePre, bg: space, fitW: 1000, post: ctx => flare(ctx, ap, 8, PC_WHITE, PC_VIOLET, 0.5, true) },
	], { returnCanvas: true, panelH: 1180, minW: 3300, maxW: 3300, shadow: false, aoR: 2.5, edgeThr: 1.0, bg: space });
	const row2 = sheet(null, '', '', [
		{ label: 'FRONT', items: [it], cam: { yaw: 0, pitch: 2 }, ownScale: true, ownScaleMax: 20, pre: spacePre, bg: space },
		{ label: 'TOP', items: [it], cam: { yaw: 10, pitch: 80 }, ownScale: true, ownScaleMax: 20, pre: spacePre, bg: space, note: ['RING OF HANGAR PODS'] },
		{ label: 'UNDERSIDE: THE INTAKE', items: [it], cam: { yaw: 20, pitch: -40 }, ownScale: true, ownScaleMax: 20, pre: spacePre, bg: space, post: ctx => flare(ctx, ap, 9, PC_WHITE, PC_VIOLET, 0.6), note: ['POWER COSMIC APERTURE'] },
	], { returnCanvas: true, titleH: 0, panelH: 760, minW: row1.w, maxW: row1.w, shadow: false, aoR: 2.5, edgeThr: 1.0, bg: space });
	stackRows(path.join(OUT, '05_worldship.png'), [row1, row2]);
}

// ================================================================ THE ULTIMATE NULLIFIER (hand-held artifact)
// A gunmetal cosmic cylinder about a block long: rear cap, a caged Power Cosmic core seen through window slots, gold
// rings, a flared emitter head with four prongs around a glowing tip, a pistol grip with a trigger and guard.
// Built in plain 1/16-block pixels; the barrel is modelled upright in the 'barrel' bone, which is turned to point -Z.
const NF = { metal: [74, 72, 96], metalD: [44, 42, 60], gold: [196, 156, 72], grip: [42, 38, 48] };
function buildNullifier() {
	const M = new Model('ultimate_nullifier', { TW: 512, D: 6 });
	const root = M.bone('root', null, [0, 0, 0]);
	const bar = M.bone('barrel', 'root', [0, 0, 0], [90, 0, 0]);
	const ring = (y0, y1, w, mat, tag) => cham(M, bar, -w / 2, y0, -w / 2, w / 2, y1, w / 2, w * 0.3, mat, { tag, seed: Math.round(y0 * 10) });
	ring(-1, 1.6, 7.4, 'nf_gold', 'ribs');
	ring(1.6, 4.8, 6.2, 'nf_metal', 'panel');
	ring(4.8, 5.8, 7.0, 'nf_gold');
	const coreB = M.bone('core', 'barrel', [0, 9, 0]);                         // the core, seen between the cage bars (pulses)
	cham(M, coreB, -2.2, 5.6, -2.2, 2.2, 12.4, 2.2, 1.32, 'nf_core', { seed: 56 });
	M.bone('muzzle', 'barrel', [0, 22.8, 0]);
	for (const [sx, sz] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) M.box(bar, sx * 2.6 - 0.75, 5.6, sz * 2.6 - 0.75, sx * 2.6 + 0.75, 12.4, sz * 2.6 + 0.75, 'nf_metal', { seed: 3 });
	for (const [ax, az] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) M.box(bar, ax ? ax * 2.9 - 0.3 : -0.35, 6.2, az ? az * 2.9 - 0.3 : -0.35, ax ? ax * 2.9 + 0.3 : 0.35, 11.8, az ? az * 2.9 + 0.3 : 0.35, 'nf_metalD', { seed: 4 });
	ring(12.2, 13.2, 7.0, 'nf_gold');
	ring(13.2, 16.2, 5.6, 'nf_metal', 'ribs');
	ring(16.2, 17.2, 4.0, 'nf_metalD');
	ring(17.2, 19.6, 7.6, 'nf_metal', 'circles');                             // emitter head
	ring(19.6, 20.4, 8.0, 'nf_gold');
	for (const [sx, sz] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) M.stick(bar, [sx * 2.6, 20.2, sz * 2.6], [sx * 0.35, 1, sz * 0.35], 4.2, [1.1, 1.1], 'nf_gold', { seed: 5 });
	ring(20.2, 22.6, 2.8, 'nf_core');                                         // tip
	// pistol grip + trigger + guard (under the barrel, which runs from z = +1 back to z = -22.6 forward)
	const grip = M.bone('grip', 'root', [0, -3, -4]);
	M.box(grip, -1.5, -11, -6.2, 1.5, -2.6, -2.4, 'nf_grip', { rot: [-16, 0, 0], pivot: [0, -3, -4], tag: 'ribs', seed: 6 });
	M.box(grip, -1.8, -12, -6.8, 1.8, -10.8, -2.0, 'nf_gold', { rot: [-16, 0, 0], pivot: [0, -3, -4], seed: 7 });
	M.box(grip, -1.9, -3.4, -8.0, 1.9, -2.2, -1.6, 'nf_metalD', { seed: 8 });
	const trig = M.bone('trigger', 'grip', [0, -3.2, -8.6]);
	M.box(trig, -0.5, -6.0, -9.2, 0.5, -3.2, -8.2, 'nf_trigger', { rot: [10, 0, 0], pivot: [0, -3.2, -8.6], seed: 9 });
	M.box(grip, -0.45, -7.4, -11.6, 0.45, -6.6, -6.4, 'nf_metalD', { seed: 10 });  // guard (bottom)
	M.box(grip, -0.45, -7.4, -11.6, 0.45, -3.2, -10.8, 'nf_metalD', { seed: 11 }); // guard (front)
	M.k = 1 / 16; M.MODEL_SCALE = 1; M.heightBlocks = 1;
	return M;
}
function nullifierShade(M, c, f, s, t, p, info) {
	const base = { nf_metal: NF.metal, nf_metalD: NF.metalD, nf_gold: NF.gold, nf_grip: NF.grip, nf_trigger: [150, 40, 60] }[c.mat];
	if (base) {
		let [col, g] = plate(base, c, f, s, t, p, info);
		if (c.mat === 'nf_gold') col = mix(col, [255, 230, 150], 0.25 * fbm(p[0] * 2, p[1] * 2, p[2] * 2, 2));
		if (c.mat === 'nf_trigger') { col = mix(col, [255, 120, 140], 0.25); g = [255, 60, 90]; }
		return [col, 255, g];
	}
	if (c.mat === 'nf_core') {
		const sw = fbm(p[0] * 0.9 + 4, p[1] * 0.6, p[2] * 0.9, 3);
		const band = Math.abs(Math.sin(sw * 9 + p[1] * 1.2));
		let col = mix([140, 80, 230], [255, 252, 255], Math.pow(band, 2));
		if (hash3(Math.floor(p[0] * 3), Math.floor(p[1] * 3), Math.floor(p[2] * 3)) > 0.94) col = [24, 16, 40];
		return [col, 255, mix([160, 110, 255], [245, 240, 255], Math.pow(band, 2))];
	}
	return null;
}
function renderNullifier(M) {
	const bg = { top: [58, 52, 84], bottom: [30, 28, 42] };
	const pre = ctx => { nebula(ctx, [[110, 60, 180], [60, 80, 170]], 0.25, 17); stars(ctx, 260, 71, 0.7); };
	const it = (extra = {}) => Object.assign({ model: M, pose: {}, root: [0, 0, 0], noFloor: true }, extra);
	const tip = wpt(it(), 'barrel', [0, 21.6, 0]);
	const coreP = wpt(it(), 'barrel', [0, 9, 0]);
	const PP = buildPaintedPlayer(); PP.layout(); PP.paint(shadeFor(PP));
	const handAt = [-6 / 16, 22 / 16, -10.5 / 16];
	const gripC = vsc([0, -7, -5], 1 / 16);
	const held = it({ pos: vsub(handAt, gripC) });
	const tipH = vadd(tip, held.pos);
	const row1 = sheet(null, 'THE ULTIMATE NULLIFIER', 'HAND-HELD COSMIC ARTIFACT - ABOUT ONE BLOCK LONG: CAGED CORE, GOLD RINGS, FOUR-PRONG EMITTER, PISTOL GRIP', [
		{ label: 'SIDE', items: [it()], cam: { yaw: 90, pitch: 4 }, ownScale: true, ownScaleMax: 40, noOrigin: true, pre },
		{ label: 'THREE-QUARTER', items: [it()], cam: { yaw: 130, pitch: 18 }, ownScale: true, ownScaleMax: 40, noOrigin: true, pre },
		{ label: 'FRONT (EMITTER)', items: [it()], cam: { yaw: 0, pitch: 6 }, ownScale: true, ownScaleMax: 40, noOrigin: true, pre },
	], { returnCanvas: true, panelH: 1000, minW: 2600, maxW: 3000, bg, shadow: false, aoR: 0.05, edgeThr: 0.03, pad: 0.12 });
	const row2 = sheet(null, '', '', [
		{ label: 'TOP', items: [it()], cam: { yaw: 90, pitch: 82 }, ownScale: true, ownScaleMax: 40, noOrigin: true, pre },
		{
			label: 'ARMED: THE CORE AWAKENS', items: [it()], cam: { yaw: 120, pitch: 10 }, ownScale: true, ownScaleMax: 40, noOrigin: true, pre, extraPts: [vadd(tip, [0, 0, -0.5])],
			post: ctx => { krackle(ctx, tip, 0.32, 30, 15, PC_VIOLET); flare(ctx, tip, 0.35, PC_WHITE, PC_VIOLET, 0.9); krackle(ctx, coreP, 0.28, 20, 19, PC_VIOLET); },
		},
		{ label: 'IN HAND (PLAYER FOR SCALE)', items: [{ model: PP, pose: { arm_r: [-90, 0, 0] }, root: [0, 0, 0], noFloor: true }, held], cam: { yaw: 120, pitch: 8 }, ownScale: true, ownScaleMax: 40, noOrigin: true, pre, post: ctx => flare(ctx, tipH, 0.12, PC_WHITE, PC_VIOLET, 0.8) },
	], { returnCanvas: true, titleH: 0, panelH: 760, minW: row1.w, maxW: row1.w, bg, shadow: false, aoR: 0.05, edgeThr: 0.03, pad: 0.1 });
	stackRows(path.join(OUT, '06_nullifier.png'), [row1, row2]);
}

// ================================================================ SURFBOARD close-up (item / mount)
function boardGlow(ctx, item) { // Power Cosmic glow under the deck + a soft pool of light on the ground
	for (let z = -26; z <= 26; z += 2) for (let x = -5; x <= 5; x += 2.5) flare(ctx, wpt(item, 'board', [x, -2.4, z]), 0.16, [220, 236, 255], [90, 140, 255], 0.3, true);
	const c = wpt(item, 'board', [0, 0, 0]);
	flare(ctx, [c[0], 0.02, c[2]], 1.1, [150, 180, 255], [60, 90, 200], 0.18, true);
}
function renderBoard(B, S) {
	const bg = { top: [64, 72, 104], bottom: [34, 38, 54] };
	const pre = ctx => { nebula(ctx, [[60, 70, 150], [120, 80, 170]], 0.25, 3); stars(ctx, 300, 21, 0.8); };
	const it = (extra = {}) => Object.assign({ model: B, pose: {}, root: [0, 0, 0], noFloor: true }, extra);
	const PP = buildPaintedPlayer(); PP.layout(); PP.paint(shadeFor(PP));
	const hover = 0.7;
	const mountB = it({ pos: [0, hover, 0] });
	const surferB = it({ pos: [0, hover, 0] });
	const row1 = sheet(null, "THE SURFER'S BOARD", 'ITEM / MOUNT - ABOUT 2 BLOCKS LONG, POINTED AT BOTH ENDS, POLISHED SILVER, POWER COSMIC GLOW UNDERNEATH', [
		{ label: 'TOP', items: [it()], cam: { yaw: 90, pitch: 86 }, ownScale: true, ownScaleMax: 60, noOrigin: true, pre, fitW: 1000 },
		{ label: 'THREE-QUARTER', items: [it()], cam: { yaw: 55, pitch: 26 }, ownScale: true, ownScaleMax: 60, noOrigin: true, pre, fitW: 1000 },
		{ label: 'SIDE', items: [it()], cam: { yaw: 90, pitch: 4 }, ownScale: true, ownScaleMax: 60, noOrigin: true, pre, note: ['THE NOSE LIFTS SLIGHTLY'], fitW: 1000 },
	], { returnCanvas: true, panelH: 820, minW: 2600, maxW: 3000, bg, shadow: false, aoR: 0.05, edgeThr: 0.03, pad: 0.12 });
	const row2 = sheet(null, '', '', [
		{ label: 'UNDERSIDE: THE GLOW', items: [it()], cam: { yaw: 50, pitch: -38 }, ownScale: true, ownScaleMax: 60, noOrigin: true, pre, fitW: 1000 },
		{ label: 'MOUNT: A PLAYER RIDES IT', items: [mountB, { model: PP, pose: { arm_r: [0, 0, 30], arm_l: [0, 0, -30] }, root: [0, 0, 0], pos: [0, hover, 0], yaw: 0, noFloor: true }], cam: { yaw: 40, pitch: 8 }, ownScale: true, ownScaleMax: 60, pre, post: ctx => boardGlow(ctx, mountB), floor: [{ x: 0, z: 0, r: 1.6 }], render: { shadow: true } },
		{ label: 'THE SURFER ON IT', items: [surferB, { model: S, pose: S.poses.ally, root: S.poses.ally.$root, pos: [0, hover, 0], noFloor: true }], cam: { yaw: 30, pitch: 6 }, ownScale: true, ownScaleMax: 60, pre, post: ctx => boardGlow(ctx, surferB), floor: [{ x: 0, z: 0, r: 1.6 }], render: { shadow: true } },
	], { returnCanvas: true, titleH: 0, panelH: 900, minW: row1.w, maxW: row1.w, bg, shadow: false, aoR: 0.08, edgeThr: 0.05 });
	stackRows(path.join(OUT, '07_surfboard.png'), [row1, row2]);
}

// ================================================================ LINEUP at true scale
function lineup() {
	const G = built.galactus, S = built.surfer, B = built.board, PN = built.punisher, C = built.converter;
	const PL = buildPlayer(); PL.layout(); PL.paint(shadeFor(PL));
	// the Surfer is a regular player model with a user-chosen skin; a plain chrome player stands in for him here
	const CP = buildPlayer();
	CP.extraMat = (c, f, s, t, p, info) => {
		let col = chrome(c, f, s, t, p, info);
		if (f === 'north' && p[1] > 27 && p[1] < 28 && Math.abs(Math.abs(p[0]) - 2) < 1) return [[255, 255, 255], 255, [236, 246, 255]];
		return [col, 255, null];
	};
	CP.layout(); CP.paint(shadeFor(CP));
	const hover = 0.6;
	const row = [
		{ name: 'PLAYER', h: '2 BLOCKS', x: 1.2, items: x => [{ model: PL, pose: {}, root: [0, 0, 0], pos: [x, 0, 0], silhouette: [46, 50, 64] }] },
		{ name: 'SURFER (STAND-IN)', h: '2 + BOARD', x: 4.4, items: x => [{ model: B, pose: {}, root: [0, 0, 0], pos: [x, hover, 0], yaw: -70 }, { model: CP, pose: {}, root: [0, 0, 0], pos: [x, hover, 0], yaw: -70 }] },
		{ name: 'PUNISHER', h: '9 BLOCKS', x: 13, items: x => [{ model: PN, pose: PN.poses.stand, root: planted(PN, PN.poses.stand), pos: [x, 0, 0], yaw: -15 }] },
		{ name: 'ELEMENT CONVERTER', h: '25 BLOCKS', x: 34, items: x => [{ model: C, pose: {}, root: [0, 0, 0], pos: [x, 0, 0], yaw: 20, hide: c => c.bone === 'shield' }] },
		{ name: 'GALACTUS', h: '60 TO THE HEAD', x: 66, items: x => [{ model: G, pose: G.poses.stand, root: planted(G, G.poses.stand), pos: [x, 0, 0], yaw: -18 }] },
	];
	const items = row.flatMap(r => r.items(r.x));
	const H = 1440, top = 90, ppu = 16.5;
	const cam = { yaw: 0, pitch: 5 };
	const total = 88;
	const Wmain = Math.round(total * ppu + 140);
	const origin = [70, H - 110];
	const bg = { top: [70, 68, 96], bottom: [40, 40, 54] };
	const pre = ctx => stars(ctx, 380, 91, 0.6);
	const main = L.renderPanel({ items, W: Wmain, H, cam, ppu, origin, ss: 2, floor: [{ x: total / 2, z: 0, r: total * 0.6, k: 0.35 }], bg, pre, shadowSoft: 0.6, aoR: 0.5, edgeThr: 0.25, contactH: 1.5,
		post: ctx => { boardGlowLite(ctx, items[1]); } });
	// inset: the small ones at 5x so the player-scale detail can be read
	const insetRow = row.slice(0, 3);
	const iItems = insetRow.flatMap(r => r.items(r.x));
	const ippu = 82, iW = Math.round(16.5 * ippu + 60), iH = H;
	const iorigin = [10, iH - 120];
	const inset = L.renderPanel({ items: iItems, W: iW, H: iH, cam, ppu: ippu, origin: iorigin, ss: 2, floor: [{ x: 7, z: 0, r: 10, k: 0.35 }], bg, pre, shadowSoft: 0.12, aoR: 0.1, edgeThr: 0.05, contactH: 0.4,
		post: ctx => { boardGlowLite(ctx, iItems[1]); } });
	const cv = new L.Canvas(Wmain + iW + 6, H + top, [26, 28, 34]);
	cv.blit(main, Wmain, H, 0, top);
	cv.blit(inset, iW, iH, Wmain + 6, top);
	cv.text('LINEUP AT TRUE SCALE', 28, 22, 5, [240, 244, 250]);
	cv.text('GALACTUS: THE DEVOURER - HEIGHTS IN BLOCKS, RULER IN BLOCKS', 28 + 21 * 30 + 24, 34, 3, [160, 190, 220]);
	for (let b = 0; b <= 70; b += 1) { const yy = top + origin[1] - Math.round(b * ppu * Math.cos(cam.pitch * DEGR)); if (b % 5 === 0) { cv.rect(0, yy, b % 10 === 0 ? 26 : 16, 2, [230, 236, 244]); if (b % 10 === 0 && b) cv.text(String(b), 30, yy - 8, 2, [230, 236, 244]); } }
	for (const r of row) {
		const sx = origin[0] + r.x * ppu;
		const two = r.x < 10;
		if (two) continue;
		cv.text(r.name, sx, top + H - 84, 2, [240, 244, 250], { align: 'center' });
		cv.text(r.h, sx, top + H - 58, 2, [160, 196, 236], { align: 'center' });
	}
	cv.text('PLAYER + SURFER: SEE INSET', 14, top + H - 114, 2, [200, 214, 236]);
	cv.rect(Wmain + 6, top, iW, 54, [26, 28, 34], 0.6);
	cv.text('INSET X5: THE SMALL ONES', Wmain + 6 + iW / 2, top + 18, 3, [236, 240, 246], { align: 'center' });
	for (const r of insetRow) {
		const sx = Wmain + 6 + iorigin[0] + r.x * ippu;
		cv.text(r.name, sx, top + iH - 84, 2, [240, 244, 250], { align: 'center' });
		cv.text(r.h, sx, top + iH - 58, 2, [160, 196, 236], { align: 'center' });
	}
	for (let b = 0; b <= 9; b++) { const yy = top + iorigin[1] - Math.round(b * ippu * Math.cos(cam.pitch * DEGR)); cv.rect(Wmain + 6 + iW - 22, yy, 22, 2, [230, 236, 244]); cv.text(String(b), Wmain + 6 + iW - 50, yy - 8, 2, [230, 236, 244]); }
	cv.save(path.join(OUT, '00_lineup.png'));
	console.log(`wrote 00_lineup.png (${Wmain + iW + 6}x${H + top})`);
}
function boardGlowLite(ctx, item) {
	for (let z = -26; z <= 26; z += 4) flare(ctx, wpt(item, 'board', [0, -2.4, z]), 0.2, [220, 236, 255], [90, 140, 255], 0.3, true);
}

// ================================================================ GAME EXPORT (--game)
// Writes the approved concept models AS the game assets + every animation clip into
// src/main/resources/assets/projecthero/{geo,textures/entity,animations}/galactus/<name>.* (plus the Nullifier's item
// display model), and scratchpad/galactus_anim_timing.json; then renders preview sheets FROM those written files into
// <concept dir>/anim/. The Silver Surfer is NOT exported (he is a regular player model with a user-chosen skin).
//
// The in-game look (lessons from the Frost Giant export):
//   - GeckoLib's AutoGlowingTexture uses the glowmask only as a MASK: every non-zero glowmask texel takes the BASE
//     texture's colour into the emissive texture (at the glowmask's alpha) and is cleared from the base. So glowing
//     texels are painted bright in the BASE texture; the glowmask is white, alpha = how opaque the glow is.
//   - HALO shells: inflated copies of the weak points / glowing parts (+ flat plates over Galactus's eyes and palms).
//     Their base texel carries a soft radial colour, their glowmask alpha is tiny: GeckoLib's own glow pass shows a
//     faint haze, and a FrostHaloLayer-style additive pass (RenderType.eyes on the generated _glowmask texture) turns
//     them into bloom.
//   - cube counts stay modest: Galactus is one entity; Punishers come in groups.
const GLOW_HALO = {
	galactus: { mats: { g_core: PC_GLOW, g_crest: PC_GLOW, g_G: PC_GLOW }, k: 0.9 },
	galactus_hand: { mats: { g_core: PC_GLOW }, k: 0.9 },
	punisher: { mats: { p_glow: ORANGE_GLOW }, k: 0.9 },
	element_converter: { mats: { ec_core: EC.glow }, k: 1.0 },
	worldship: { mats: { ws_glow: PC_GLOW, ws_aperture: PC_VIOLET }, k: 1.0 },
	surfboard_mount: { mats: { board_glow: [90, 150, 255] }, k: 0.55 },
	nullifier: { mats: { nf_core: PC_VIOLET }, k: 0.9 },
};
const satC = (c, k) => { const l = 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]; return c.map(v => clamp(l + (v - l) * k)); };
const contrastC = (c, k, mid = 118) => c.map(v => clamp(mid + (v - mid) * k));
function addHalos(M, spec, plates = []) {
	for (const c of M.cubes().slice()) {
		const col = spec.mats[c.mat];
		if (!col) continue;
		const sz = [0, 1, 2].map(i => c.to[i] - c.from[i]);
		const h = Math.max(0.5, Math.min(2.2, Math.min(...sz) * 0.7 + 0.3));
		const hc = M.box(M.B[c.bone], c.from[0] - h, c.from[1] - h, c.from[2] - h, c.to[0] + h, c.to[1] + h, c.to[2] + h, 'halo', { rot: c.rot, pivot: c.pivot });
		hc.haloCol = col; hc.haloK = spec.k;
	}
	for (const e of plates) {
		const hc = M.box(e.bone, e.c[0] - e.s[0] / 2, e.c[1] - e.s[1] / 2, e.c[2] - e.s[2] / 2, e.c[0] + e.s[0] / 2, e.c[1] + e.s[1] / 2, e.c[2] + e.s[2] / 2, 'halo');
		hc.haloCol = e.col; hc.haloK = e.k || 1;
	}
}
function gameShade(M) {
	const base = shadeFor(M);
	const W = [255, 255, 255];
	return (c, f, s, t, p, info) => {
		if (c.mat === 'halo') {
			const r = Math.max(Math.abs(s - 0.5), Math.abs(t - 0.5)) * 2;
			const fall = Math.pow(Math.max(0, 1 - r), 1.3);
			return [c.haloCol.map(v => v * fall * c.haloK), 255, W, Math.max(1, Math.round(70 * fall * c.haloK))];
		}
		if (c.mat === 'ec_shield') { // the converter's shield: a faint violet shell with brighter hex seams (emissive only)
			const [u, v] = UV(s, t, info);
			const lat = q => Math.abs(((q / 11) % 1 + 1) % 1 - 0.5); // a diagonal lattice of energy lines, 11 px apart
			const seam = Math.max(lat(u + v), lat(u - v)) > 0.47 ? 1 : 0;
			const edge = info.ePx < 1.0 ? 1 : 0;
			// interior: a dim violet haze (alpha 30); the hex seams and rims carry the light (alpha 140)
			const k = Math.max(seam, edge * 0.8);
			return [mix([46, 28, 96], [180, 160, 245], k), 255, W, k > 0 ? 140 : 30];
		}
		let [col, a, g] = base(c, f, s, t, p, info);
		if (g && a > 0) {
			col = mix(col, g, 0.3).map(v => clamp(v * 1.08));
			return [col, a, W, 255];
		}
		return [contrastC(satC(col, 1.12), 1.05), a, null];
	};
}
function gameLook(M, id, plates) {
	addHalos(M, GLOW_HALO[id], plates);
	M.layout();
	M.paint(gameShade(M));
	return M;
}
// move / scale every coordinate of a model (before layout): q -> (q - origin) * f
function transformModel(M, origin, f) {
	const T = q => [(q[0] - origin[0]) * f, (q[1] - origin[1]) * f, (q[2] - origin[2]) * f];
	for (const b of M.bones) b.pivot = T(b.pivot);
	for (const c of M.cubes()) { c.from = T(c.from); c.to = T(c.to); if (c.pivot) c.pivot = T(c.pivot); }
}
// Galactus's right gauntlet + hand as its own render-only model, rooted at the palm (root > forearm_r > hand_r > ...),
// sharing Galactus's painted texture. Drawn at Galactus's model scale.
function handFromGalactus(G) {
	const keep = ['forearm_r', 'hand_r', 'fingers_r', 'fingertips_r', 'thumb_r', 'palm_r', 'grab_socket', 'weak_gauntlet_r'];
	const palm = [-G.A.shX, G.A.knuckY + 4, 0];
	const H = new Model('galactus_hand', { TW: G.TW, D: G.D });
	H.bone('root', null, [0, 0, 0]);
	for (const n of keep) {
		const b = G.B[n];
		const nb = H.bone(n, n === 'forearm_r' ? 'root' : b.parent, b.pivot.map((v, i) => v - palm[i]), b.rot);
		for (const c of b.cubes) {
			const cc = H.box(nb, c.from[0] - palm[0], c.from[1] - palm[1], c.from[2] - palm[2], c.to[0] - palm[0], c.to[1] - palm[1], c.to[2] - palm[2], c.mat, { rot: c.rot, pivot: c.pivot ? c.pivot.map((v, i) => v - palm[i]) : null, paint: c.paint, tag: c.tag });
			cc.uv = c.uv;
		}
	}
	H.TH = G.TH; H.tex = G.tex; H.glow = G.glow; H.rects = G.rects;
	H.k = G.k; H.MODEL_SCALE = G.MODEL_SCALE; H.heightBlocks = 9; H.A = G.A;
	// origin under the fingertips (rest pose): the hand entity's position is the bottom of the hand
	const lowY = H.bounds(H.world({})).lo[1];
	transformModel(H, [0, lowY, 0], 1);
	return H;
}
// GalactusModelFit's rule: bones named crest* / prong* / antenna* / horn* (and everything under them) are not measured.
function fitSkip(M) {
	const skip = new Set();
	for (const b of M.bones) if (/^(crest|prong|antenna|horn)/i.test(b.name) || (b.parent && skip.has(b.parent))) skip.add(b.name);
	return c => !skip.has(c.bone);
}
// Parse GalactusAnims.java: the asset-name constants, then every clip(...) / hold(...) / loop(...) line.
function galTiming() {
	const src = fs.readFileSync(path.join(REPO, GAL_ANIMS_JAVA), 'utf8');
	const tr = /int TRANSITION = (\d+);/.exec(src);
	if (!tr || +tr[1] !== GAL_TRANSITION) throw new Error(`GalactusAnims.TRANSITION is not ${GAL_TRANSITION}`);
	const names = {};
	for (const m of src.matchAll(/static final String (\w+) = "(\w+)";/g)) names[m[1]] = m[2];
	const out = {};
	for (const m of src.matchAll(/= (clip|hold|loop)\((\w+), "(\w+)", (\d+)(?:, (-?\d+))?\);/g)) {
		const model = names[m[2]];
		if (!model) throw new Error(`GalactusAnims: unknown model constant ${m[2]}`);
		const kind = m[1] === 'clip' ? 'once' : m[1], length = +m[4], hit = m[5] != null ? +m[5] : -1;
		if (hit >= 0 && hit < GAL_TRANSITION) throw new Error(`${model}.${m[3]}: hit ${hit} is inside the ${GAL_TRANSITION}-tick transition`);
		if (hit >= length) throw new Error(`${model}.${m[3]}: hit ${hit} is past the clip length ${length}`);
		(out[model] = out[model] || {})[m[3]] = { kind, length, hit };
	}
	if (!out.galactus || !out.punisher) throw new Error('GalactusAnims parse found no clips');
	return out;
}
function writeGame(M, id, an) {
	const RES = path.join(REPO, 'src/main/resources/assets/projecthero');
	const geoDir = path.join(RES, 'geo/galactus'), texDir = path.join(RES, 'textures/entity/galactus'), animDir = path.join(RES, 'animations/galactus');
	for (const d of [geoDir, texDir, animDir]) fs.mkdirSync(d, { recursive: true });
	const files = { geo: path.join(geoDir, `${id}.geo.json`), tex: path.join(texDir, `${id}.png`), glow: path.join(texDir, `${id}_glowmask.png`), anim: path.join(animDir, `${id}.animation.json`) };
	fs.writeFileSync(files.geo, JSON.stringify(M.geoJson(`geometry.${id}`), null, '\t') + '\n');
	fs.writeFileSync(files.tex, L.encodePng(M.TW, M.TH, M.tex));
	fs.writeFileSync(files.glow, L.encodePng(M.TW, M.TH, M.glow));
	fs.writeFileSync(files.anim, JSON.stringify(an.build(), null, '\t') + '\n');
	return files;
}
function nullifierItemModel() {
	const d = (rotation, translation, scale) => ({ rotation, translation, scale: [scale, scale, scale] });
	return {
		parent: 'builtin/entity', gui_light: 'front', textures: { particle: 'projecthero:entity/galactus/nullifier' },
		display: {
			gui: d([20, -135, 0], [0, 0, 0], 0.62), ground: d([0, 0, 0], [0, 2, 0], 0.4), fixed: d([0, 90, 0], [0, 0, 0], 0.8), head: d([0, 180, 0], [0, 10, 0], 0.7),
			thirdperson_righthand: d([0, 0, 0], [0, 1.5, -1], 0.62), thirdperson_lefthand: d([0, 0, 0], [0, 1.5, -1], 0.62),
			firstperson_righthand: d([0, -6, 0], [1.5, 1.5, -1], 0.55), firstperson_lefthand: d([0, 6, 0], [1.5, 1.5, -1], 0.55),
		},
	};
}
function gameExport() {
	const A = require('./lib_geckogen_anim.js');
	const CL = require('./galactus_clips.js');
	if (CL.T !== GAL_TRANSITION) throw new Error('galactus_clips.js TRANSITION differs from GAL_TRANSITION');
	const timing = galTiming();
	const out = {
		transition: GAL_TRANSITION, source: 'src/main/java/com/projecthero/mod/galactus/GalactusAnims.java (parsed)',
		note: 'length/hit = server ticks; a play-once/held clip is (length - 4) ticks long and keys its strike on (hit - 4); loops are length ticks. Never-keyed bones: neck, head (code look; clips nod through `nod`), flinch (code flinch).',
		never_keyed: ['neck', 'head', 'flinch'], models: {},
	};
	const previews = [];
	const unit = (id, M, clipsFn, extra = {}) => {
		const an = new A.AnimSet(M, id, { neverKey: ['neck', 'head', 'flinch'], hidden: extra.hidden || [] });
		clipsFn(an, timing[id]);
		const files = writeGame(M, id, an);
		const locators = M.bones.filter(b => !b.cubes.length && b.name !== 'root' && !['flinch', 'nod'].includes(b.name) && !M.bones.some(o => o.parent === b.name)).map(b => b.name);
		out.models[id] = {
			files: Object.fromEntries(Object.entries(files).map(([k, v]) => [k, path.relative(REPO, v).replace(/\\/g, '/')])),
			model_scale: +M.MODEL_SCALE.toFixed(4), height_blocks: M.heightBlocks, texture: [M.TW, M.TH], bones: M.bones.length, cubes: M.cubes().length,
			locator_bones: locators, hidden_by_default: extra.hidden || [], clips: {},
		};
		if (extra.note) out.models[id].note = extra.note;
		for (const [n, t] of Object.entries(timing[id])) {
			if (!an.timing[n]) throw new Error(`${id}: clip ${n} from the timing table was not authored`);
			out.models[id].clips[n] = Object.assign({ server_length: t.length, hit: t.hit, kind: t.kind, clip_ticks: an.clips[n].len, strike_clip_tick: t.hit >= GAL_TRANSITION ? t.hit - GAL_TRANSITION : null },
				an.timing[n].stride_blocks ? { stride_blocks: an.timing[n].stride_blocks, move_speed: an.clips[n].speed } : {});
		}
		for (const n of Object.keys(an.clips)) if (!timing[id][n]) throw new Error(`${id}: authored clip ${n} is not in the timing table`);
		previews.push({ id, files, k: M.MODEL_SCALE / 16, height: M.heightBlocks, clips: an.clips, timing: timing[id], cam: extra.cam, hidden: extra.hidden || [], bg: extra.bg });
		console.log(`${id}: ${M.bones.length} bones, ${M.cubes().length} cubes, tex ${M.TW}x${M.TH}, ${Object.keys(an.clips).length} clips -> ${path.relative(REPO, files.geo)}`);
	};
	// ---- Galactus (60 blocks toes to head; the prongs rise above) + his render-only grab hand
	let G;
	{
		G = buildGalactus();
		G.setHeight(60, fitSkip(G));
		const poses = galactusPoses(G);
		const A0 = G.A, hs = A0.hs;
		const eyes = [-1, 1].map(s => ({ bone: 'head', c: [s * A0.eyeX, A0.eyeY, A0.faceZ - 0.35], s: [3.3 * 2 * hs * 0.5 + 1.6, 0.62 * 2 * hs + 1.3, 0.2], col: [200, 230, 255], k: 1 }));
		const palms = SIDES.map(([s, n]) => ({ bone: `hand_${n}`, c: [s * A0.shX - s * 3.75, A0.knuckY + 5, 0], s: [0.2, 6, 6], col: PC_GLOW, k: 0.9 }));
		gameLook(G, 'galactus', eyes.concat(palms));
		unit('galactus', G, (an, t) => CL.galactusClips(an, t, poses), { cam: { yaw: 30, pitch: 6 }, note: 'height_blocks = toes to the top of the head; the helmet prongs rise above. Locators: eye_l/eye_r (beam origins), weak_crest, weak_emblem, weak_gauntlet_l/r (weak points), palm_l/r, grab_socket (where a grabbed player sits).' });
	}
	{
		const H = handFromGalactus(G);
		unit('galactus_hand', H, CL.handClips, { cam: { yaw: 50, pitch: 10 }, note: 'Render-only, fitted to 9 blocks by GalactusRenderers.Hand: the right gauntlet + hand, hanging fingers-down, its origin under the fingertips (the server holds the victim 2 blocks above the hand entity, i.e. in the palm); grab_socket = that spot; same paint as galactus.png (own copy).' });
	}
	// ---- Punisher
	{
		const M = buildPunisher();
		M.setHeight(9);
		const poses = punisherPoses(M);
		gameLook(M, 'punisher');
		unit('punisher', M, (an, t) => CL.punisherClips(an, t, poses, PUNISHER_WALK_SPEED), { cam: { yaw: 30, pitch: 8 }, note: 'Locators: muzzle_l/muzzle_r (blast origins).' });
	}
	// ---- Element Converter (the consumed ground is real blocks, not part of the model)
	{
		const M = buildConverter();
		M.setHeight(25, c => c.bone !== 'shield');
		gameLook(M, 'element_converter');
		unit('element_converter', M, CL.converterClips, { hidden: ['shield'], cam: { yaw: 30, pitch: 8 }, note: 'shield is scaled to 0 except in `shielded`. Locators: beam_origin (crown top). The core bone pulses (scale) and spins.' });
	}
	// ---- Worldship (sky prop, 2 blocks per model px)
	{
		const M = buildWorldship();
		M.k = 2; M.MODEL_SCALE = 32; M.heightBlocks = 240;
		gameLook(M, 'worldship');
		unit('worldship', M, CL.worldshipClips, { cam: { yaw: 25, pitch: 14 }, bg: { top: [16, 14, 34], bottom: [40, 32, 70] }, note: 'Model px are 2 blocks (model_scale 32): about 240 blocks across. Locator: aperture (the intake beam origin).' });
	}
	// ---- the Surfer's board as a mount (plain 1/16 pixels, about 2 blocks long)
	{
		const B = buildBoard();
		const W = B.bones[0];
		W.name = 'board'; delete B.B.board; // re-root: root > board
		const root = { name: 'root', parent: null, pivot: [0, 0, 0], rot: [0, 0, 0], cubes: [] };
		W.parent = 'root'; B.bones.unshift(root); B.B.root = root; B.B.board = W;
		transformModel(B, [0, 0, 0], 32 / 58);
		transformModel(B, [0, -4, 0], 1); // the deck at 4 px = GalactusRenderers.BOARD_TOP (0.25 blocks): the rider stands on it
		B.k = 1 / 16; B.MODEL_SCALE = 1; B.heightBlocks = 0.2;
		gameLook(B, 'surfboard_mount');
		unit('surfboard_mount', B, CL.boardClips, { cam: { yaw: 40, pitch: 24 }, note: 'Deck at y = 4 px (BOARD_TOP 0.25 blocks, the rider stands on it), underside glow below; about 2 blocks long, nose toward -Z.' });
	}
	// ---- the Ultimate Nullifier (item; centred on the origin for GeckoLib's item renderer)
	{
		const M = buildNullifier();
		transformModel(M, [0, -4, -10.8], 1);
		M.k = 1 / 16; M.MODEL_SCALE = 1; M.heightBlocks = 1;
		gameLook(M, 'nullifier');
		unit('nullifier', M, CL.nullifierClips, { cam: { yaw: 110, pitch: 14 }, note: 'Item model for ultimate_nullifier (GeoItemRenderer; display transforms in models/item/ultimate_nullifier.json), centred on the origin. Locator: muzzle.' });
	}
	fs.writeFileSync(path.join(REPO, 'scratchpad/galactus_anim_timing.json'), JSON.stringify(out, null, '\t') + '\n');
	console.log('wrote scratchpad/galactus_anim_timing.json');
	if (!args.includes('--no-previews')) for (const p of previews) gamePreview(p, A);
}

// Preview sheets rendered from the WRITTEN game files (geo + PNG + animation JSON), as GeckoLib would mask the glow.
function gamePreview(p, A) {
	const G = A.loadGameModel(p.files.geo, p.files.tex, p.files.glow, p.k * 16, true);
	G.heightBlocks = p.height;
	const anims = A.loadAnimations(p.files.anim);
	const key = n => `animation.${p.id}.${n}`;
	const frames = [];
	const add = (label, n, t) => { const c = p.clips[n]; if (c) frames.push([label, n, Math.max(0, Math.min(c.len, Math.round(t)))]); };
	const big = p.height > 20;
	for (const [n, t] of Object.entries(p.timing)) {
		const c = p.clips[n], N = n.toUpperCase().replace(/_/g, ' ');
		if (t.kind === 'loop') { add(`${N}`, n, 0); if (c.len > 8) add(`${N} (MID)`, n, c.len * 0.5); continue; }
		if (t.hit >= GAL_TRANSITION) { add(`${N} (WIND-UP)`, n, (t.hit - GAL_TRANSITION) * 0.6); add(`${N} AT HIT ${t.hit}`, n, t.hit - GAL_TRANSITION); }
		else add(`${N} (MID)`, n, c.len * 0.4);
		add(`${N} (END)`, n, c.len);
	}
	const dir = path.join(OUT, 'anim');
	fs.mkdirSync(dir, { recursive: true });
	const per = 5;
	for (let i = 0; i * per < frames.length; i++) {
		const chunk = frames.slice(i * per, i * per + per);
		const panels = chunk.map(([label, n, t]) => {
			const pose = A.poseAt(anims, key(n), t);
			const hidden = new Set(pose.$hidden || []);
			for (const b of G.bones) if (b.parent && hidden.has(b.parent)) hidden.add(b.name);
			return { label, items: [{ model: G, pose, root: pose.$root, hide: c => hidden.has(c.bone), floorR: big ? p.height * 0.35 : p.height * 0.45 + 0.8, noFloor: p.id === 'worldship' }], cam: p.cam || { yaw: 28, pitch: 8 }, ownScale: p.id === 'worldship' || p.id === 'galactus', noOrigin: ['galactus_hand', 'worldship', 'nullifier', 'surfboard_mount'].includes(p.id) };
		});
		sheet(path.join(dir, `${p.id}_${i + 1}.png`), `${p.id.toUpperCase().replace(/_/g, ' ')} - GAME ASSETS`, `PAGE ${i + 1} - POSES READ BACK FROM THE WRITTEN GEO + ANIMATION JSON`, panels,
			{ minW: 1800, panelH: 900, maxW: 3400, ss: 1, bg: p.bg || { top: [96, 98, 116], bottom: [58, 60, 74] }, shadow: p.id !== 'worldship', aoR: big ? 0.8 : 0.2, edgeThr: big ? 0.4 : 0.08, shadowSoft: big ? 0.8 : 0.25, contactH: big ? 3 : 0.6 });
	}
}

// ================================================================ main
const built = {};
if (WANT('galactus') || WANT('lineup')) {
	const M = buildGalactus();
	M.ground(c => /^foot/.test(c.bone));
	finish(M, 'galactus', 60, fitSkip(M)); // 60 blocks from the toes to the top of the head; the prongs rise above
	M.poses = galactusPoses(M);
	built.galactus = M;
	if (WANT('galactus')) renderGalactus(M);
}
if (WANT('surfer') || WANT('board') || WANT('lineup')) {
	const M = buildSurfer();
	finish(M, 'silver_surfer', 2.1);
	const B = buildBoard();
	B.k = M.k; B.MODEL_SCALE = M.MODEL_SCALE; B.heightBlocks = 0.3;
	B.layout(); B.paint(shadeFor(B));
	if (ASSETS) B.writeAssets(path.join(OUT, 'assets'), 'silver_surfer_board');
	M.poses = surferPoses(M, B);
	built.surfer = M; built.board = B;
	if (WANT('surfer')) renderSurfer(M, B);
}
if (WANT('punisher') || WANT('lineup')) {
	const M = buildPunisher();
	finish(M, 'galactus_punisher', 9);
	M.poses = punisherPoses(M);
	built.punisher = M;
	if (WANT('punisher')) renderPunisher(M);
}
if (WANT('converter') || WANT('lineup')) {
	const M = buildConverter();
	finish(M, 'element_converter', 25);
	const G = buildGround();
	G.k = M.k; G.MODEL_SCALE = M.MODEL_SCALE;
	G.layout(); G.paint(shadeFor(G));
	if (ASSETS) G.writeAssets(path.join(OUT, 'assets'), 'consumed_ground');
	built.converter = M; built.ground = G;
	if (WANT('converter')) renderConverter(M, G);
}
if (WANT('worldship')) {
	const M = buildWorldship();
	M.k = 2; M.MODEL_SCALE = 32; M.heightBlocks = 240;
	M.layout(); M.paint(shadeFor(M));
	if (ASSETS) M.writeAssets(path.join(OUT, 'assets'), 'worldship');
	console.log(`worldship: ${M.bones.length} bones, ${M.cubes().length} cubes, tex ${M.TW}x${M.TH}`);
	renderWorldship(M);
}
if (WANT('nullifier')) {
	const M = buildNullifier();
	M.layout(); M.paint(shadeFor(M));
	if (ASSETS) M.writeAssets(path.join(OUT, 'assets'), 'ultimate_nullifier');
	console.log(`ultimate_nullifier: ${M.bones.length} bones, ${M.cubes().length} cubes, tex ${M.TW}x${M.TH}`);
	renderNullifier(M);
}
if (WANT('board')) renderBoard(built.board, built.surfer);
if (WANT('lineup')) lineup();

function galBg() { return { top: [92, 90, 116], bottom: [58, 56, 74] }; }
function renderGalactus(M) {
	const it = (pose, extra = {}) => Object.assign({ model: M, pose, root: pose.$root || planted(M, pose), floorR: 22 }, extra);
	const pre = ctx => stars(ctx, 260, 7, 0.55);
	const A = M.A;
	const beamsItem = it(M.poses.beams);
	const eyeL = wpt(beamsItem, 'head', [A.eyeX, A.eyeY, A.faceZ - 0.4]), eyeR = wpt(beamsItem, 'head', [-A.eyeX, A.eyeY, A.faceZ - 0.4]);
	const hit = [-20, 0, -46], hitL = vadd(hit, [2.2, 0, 0]), hitR = vadd(hit, [-2.2, 0, 0]);
	const reachItem = it(M.poses.reach);
	const PL = buildPlayer(); PL.layout(); PL.paint(shadeFor(PL));
	const handPt = wpt(reachItem, 'hand_r', [-A.shX, A.knuckY, 0]);
	const plItem = { model: PL, pose: {}, root: [0, 0, 0], pos: [handPt[0] + 1, 0, handPt[2] - 2], silhouette: [40, 42, 56], noFloor: true };
	const ro = { panelH: 1200, minW: 2900, maxW: 3600, bg: galBg(), shadowSoft: 0.9, aoR: 1.0, edgeThr: 0.5, contactH: 3 };
	const row1 = sheet(null, 'GALACTUS', '60 BLOCKS TOES TO HEAD (PRONGS RISE ABOVE) - KIRBY CLASSIC: TUNING-FORK HELMET, G CHEST PLATE, GLOWING WEAK POINTS', [
		{ label: 'FRONT', items: [it(M.poses.stand)], cam: { yaw: 0, pitch: 6 }, pre },
		{ label: 'THREE-QUARTER', items: [it(M.poses.stand)], cam: { yaw: 35, pitch: 6 }, pre },
		{ label: 'SIDE', items: [it(M.poses.stand)], cam: { yaw: 90, pitch: 6 }, pre },
		{
			label: 'ACTION: EYE BEAMS', items: [beamsItem], cam: { yaw: 36, pitch: 12 }, extraPts: [vadd(hit, [-8, 0, 0]), vadd(hit, [0, -5, -8]), vadd(hit, [8, -5, 8])], pre,
			post: ctx => {
				beam(ctx, eyeL, hitL, 0.42, PC_WHITE, PC_GLOW, { phase: 0 }); beam(ctx, eyeR, hitR, 0.42, PC_WHITE, PC_GLOW, { phase: 2 });
				flare(ctx, hit, 7, PC_WHITE, PC_GLOW, 0.9); krackle(ctx, hit, 7.5, 54, 11);
				flare(ctx, eyeL, 2.2); flare(ctx, eyeR, 2.2);
			},
		},
		{
			label: 'ACTION: THE HAND REACHES DOWN', items: [reachItem, plItem], cam: { yaw: -42, pitch: 8 }, ownScale: true, pre,
			post: ctx => { flare(ctx, wpt(reachItem, 'hand_r', [-A.shX + 2.6, A.knuckY + 3, 0]), 2.5, PC_WHITE, PC_GLOW, 0.6, true); },
			note: ['2-BLOCK PLAYER UNDER THE HAND'],
		},
	], Object.assign({ returnCanvas: true }, ro));
	const only = re => c => !re.test(c.bone);
	const row2 = sheet(null, '', '', [
		{ label: 'HELMET + FACE', items: [it(M.poses.stand, { hide: only(/^(head|helmet|prong|crest|neck|nod)/), noFloor: true })], cam: { yaw: 0, pitch: 2 }, ownScale: true, ownScaleMax: 20, noOrigin: true, pre },
		{ label: 'HELMET (THREE-QUARTER)', items: [it(M.poses.stand, { hide: only(/^(head|helmet|prong|crest|neck|nod)/), noFloor: true })], cam: { yaw: 40, pitch: 6 }, ownScale: true, ownScaleMax: 20, noOrigin: true, pre, note: ['CREST + EYES GLOW: WEAK POINT'] },
		{ label: 'CHEST EMBLEM + BELT', items: [it(M.poses.stand, { hide: only(/^(chest|waist|hips)$/), noFloor: true })], cam: { yaw: 14, pitch: 4 }, ownScale: true, ownScaleMax: 20, noOrigin: true, pre, note: ['THE G GLOWS: WEAK POINT'] },
		{ label: 'GAUNTLET', items: [it(M.poses.stand, { hide: only(/^(forearm_l|hand_l|fingers_l|fingertips_l|thumb_l)$/), noFloor: true })], cam: { yaw: 50, pitch: 10 }, ownScale: true, ownScaleMax: 20, noOrigin: true, pre, note: ['ENERGY RING + PALM EMITTER: WEAK POINT'] },
		{ label: 'BOOT', items: [it(M.poses.stand, { hide: only(/^(shin_l|foot_l)$/), noFloor: true })], cam: { yaw: 35, pitch: 8 }, ownScale: true, ownScaleMax: 20, noOrigin: true, pre },
	], Object.assign({}, ro, { returnCanvas: true, titleH: 0, panelH: 760, minW: row1.w, maxW: row1.w, shadow: false }));
	stackRows(path.join(OUT, '01_galactus.png'), [row1, row2]);
}

// A plain 2-block player for scale (a dark silhouette by default).
function buildPlayer() {
	const M = new Model('player', { TW: 256, D: 1 });
	const r = M.bone('root', null, [0, 0, 0]);
	M.box(r, -4, 0, -2, 0, 12, 2, 'sil'); M.box(r, 0, 0, -2, 4, 12, 2, 'sil');
	M.box(r, -4, 12, -2, 4, 24, 2, 'sil'); M.box(r, -8, 12, -2, -4, 24, 2, 'sil'); M.box(r, 4, 12, -2, 8, 24, 2, 'sil');
	M.box(r, -4, 24, -4, 4, 32, 4, 'sil');
	M.k = 2 / 32; M.MODEL_SCALE = 1; M.heightBlocks = 2;
	M.extraMat = () => [[50, 54, 64], 255, null];
	return M;
}

if (GAME) gameExport();
module.exports = { built };
