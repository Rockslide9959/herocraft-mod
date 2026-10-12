// v0.16.1 raid undead: GAME EXPORT (node scratchpad/gen_raid_undead_models.js --game [--only ...] [--no-previews]).
// Writes every model AS the game asset (geo + texture + glowmask + animation) into
// src/main/resources/assets/projecthero/{geo,textures/entity,animations}/raid/, the timing table
// scratchpad/raid_undead_anim_timing.json, and preview sheets rendered FROM the written files into
// <main>/scratchpad/shots_v0161/raid_models/anim/.
//
// Glow (the AutoGlowingTexture lesson): GeckoLib uses the glowmask only as a MASK -- an emissive texel takes the BASE
// texture's colour at the glowmask's alpha. So every glowing texel is painted at its glow colour in the base texture and
// the glowmask carries white + alpha. Halo shells (inflated copies of glowing cubes with a soft radial falloff and a low
// glowmask alpha) become a bloom through the additive FrostHaloLayer pass (used for the acid, cursed and boss units).
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('./lib_concept_models.js');
const A = require('./lib_geckogen_anim.js');
const G = require('./gen_raid_undead_models.js');
const U = require('./raid_undead_units.js');
const U2 = require('./raid_undead_units2.js');
const U3 = require('./raid_undead_units3.js');
const CL = require('./raid_undead_clips.js');
const { mix, mul, clamp } = L;

const REPO = G.REPO;
const RES = path.join(REPO, 'src/main/resources/assets/projecthero');
const T = 3;
// bones the fit leaves out (the BODY fills the hit-box): must match RaidModelFit.FIT_IGNORE
const FIT_IGNORE = ['crown', 'weapon_r', 'crossbow', 'shield', 'hair', 'hood_tip', 'spyglass_hand', 'armour_head', 'acc_shards', 'acc_horns', 'acc_coils', 'acc_vines', 'acc_orbit', 'acc_wings', 'acc_spines', 'acc_horns_back', 'acc_spines_head', 'wisp_0', 'wisp_1', 'wisp_2', 'drip_0', 'drip_1', 'cowl', 'doll'];
// adult hit-box heights (blocks)
const HEIGHTS = { raid_zombie: 1.95, raid_zombie_baby: 1.95, acid_zombie: 1.95, juggernaut_zombie: 1.95 * 1.55, sword_skeleton: 1.99, cursed_zombie: 1.95, pillager_spy: 1.95, powered_zombie: 1.95 * 1.45 };

function parseTiming() {
	const src = fs.readFileSync(path.join(REPO, 'src/main/java/com/projecthero/mod/event/entity/RaidAnims.java'), 'utf8');
	const names = {};
	for (const m of src.matchAll(/static final String (\w+) = "(\w+)";/g)) names[m[1]] = m[2];
	const t = {};
	for (const m of src.matchAll(/= (clip|hold|loop)\((\w+), "(\w+)", ([^)]*)\);/g)) {
		const a = m[4].split(',').map(s => s.trim());
		const model = names[m[2]];
		let e;
		if (m[1] === 'loop') e = { kind: 'loop', length: +a[0], hit: -1, layer: 'FULL' };
		else if (m[1] === 'hold') e = { kind: 'hold', length: +a[0], hit: -1, layer: a[1] };
		else e = { kind: 'once', length: +a[0], hit: +a[1], layer: a[2] };
		(t[model] = t[model] || {})[m[3]] = e;
	}
	const tr = /TRANSITION = (\d+);/.exec(src);
	if (!tr || +tr[1] !== T) throw new Error('RaidAnims.TRANSITION is not ' + T);
	return t;
}
function fitHeightPx(M) {
	const skip = new Set();
	for (const b of M.bones) if (FIT_IGNORE.includes(b.name) || /^(acc_|wing_)/.test(b.name) || (b.parent && skip.has(b.parent))) skip.add(b.name);
	const bb = M.bounds(M.world({}), c => !skip.has(c.bone));
	return bb.hi[1] - bb.lo[1];
}

// ---------------------------------------------------------------- the in-game look
function addHalos(M, mats, colFn, eyes) {
	for (const c of M.cubes().slice()) {
		if (!mats.includes(c.mat)) continue;
		const sz = [0, 1, 2].map(i => c.to[i] - c.from[i]);
		const h = Math.max(0.35, Math.min(1.4, Math.min(...sz) * 0.6 + 0.25));
		const hc = M.box(M.B[c.bone], c.from[0] - h, c.from[1] - h, c.from[2] - h, c.to[0] + h, c.to[1] + h, c.to[2] + h, 'halo', { rot: c.rot, pivot: c.pivot });
		hc.haloCol = colFn(c);
	}
	for (const e of eyes || []) { const hc = M.cbox(e.bone, e.c, e.s, 'halo', { tag: 'eye' }); hc.haloCol = e.col; }
}
function gameShade(M, opts = {}) {
	const base = G.shadeFor(M);
	const W = [255, 255, 255];
	return (c, f, s, t, p, info) => {
		if (c.mat === 'halo') {
			const r = Math.max(Math.abs(s - 0.5), Math.abs(t - 0.5)) * 2;
			const fall = Math.pow(Math.max(0, 1 - r), 1.4);
			const k = c.tag === 'eye' ? 1.0 : 0.8;
			return [c.haloCol.map(v => v * fall * k), 255, W, Math.max(1, Math.round(60 * fall * k))];
		}
		let [col, a, g, ga] = base(c, f, s, t, p, info);
		if (g) return [g, a == null ? 255 : a, W, ga == null ? 255 : ga]; // emissive: the colour lives in the base texture
		col = col.map(v => clamp(v));
		if (opts.contrast) col = col.map(v => clamp(118 + (v - 118) * opts.contrast));
		return [col, a, null, null];
	};
}
function gameLook(M, opts) {
	if (opts.halos) addHalos(M, opts.halos.mats, opts.halos.col, opts.halos.eyes);
	M.layout();
	M.paint(gameShade(M, opts));
	return M;
}
function eyeHalos(M, col, s = 1) {
	const a = M.A;
	return [-1, 1].map(k => ({ bone: 'head', c: [k * a.eyeX, a.eyeY, a.faceZ - 0.15], s: [a.eyeW * 2.6 * s + 0.6, a.eyeH * 2.6 * s + 0.5, 0.15], col }));
}

function writeGame(M, id, an) {
	const geoDir = path.join(RES, 'geo/raid'), texDir = path.join(RES, 'textures/entity/raid'), animDir = path.join(RES, 'animations/raid');
	for (const d of [geoDir, texDir, animDir]) fs.mkdirSync(d, { recursive: true });
	const files = { geo: path.join(geoDir, `${id}.geo.json`), tex: path.join(texDir, `${id}.png`), glow: path.join(texDir, `${id}_glowmask.png`), anim: path.join(animDir, `${id}.animation.json`) };
	fs.writeFileSync(files.geo, JSON.stringify(M.geoJson(`geometry.${id}`), null, '\t') + '\n');
	fs.writeFileSync(files.tex, L.encodePng(M.TW, M.TH, M.tex));
	fs.writeFileSync(files.glow, L.encodePng(M.TW, M.TH, M.glow));
	if (an) fs.writeFileSync(files.anim, JSON.stringify(an.build(), null, '\t') + '\n');
	return files;
}

const DANGLE = {
	raid_zombie: [{ bone: 'hair', period: 12, damp: 0.25, limit: 30 }, { bone: 'tatter_b', period: 12, limit: 30 }, { bone: 'tatter_f', period: 10, limit: 30 }],
	raid_zombie_baby: [{ bone: 'hair', period: 10, limit: 30 }, { bone: 'skirt', period: 14, stiff: 0.6, limit: 14 }, { bone: 'doll', period: 10, damp: 0.2, limit: 40 }],
	acid_zombie: [{ bone: 'drip_0', period: 9, damp: 0.2, limit: 40 }, { bone: 'drip_1', period: 10, damp: 0.2, limit: 40 }, { bone: 'drip_2', period: 9, damp: 0.2, limit: 40 }],
	juggernaut_zombie: [{ bone: 'chain', period: 14, damp: 0.2, limit: 45, acc: 2 }, { bone: 'flap_f', period: 12, limit: 30 }, { bone: 'flap_b', period: 12, limit: 30 }],
	sword_skeleton: [{ bone: 'tabard_f', period: 11, limit: 35 }, { bone: 'tabard_b', period: 12, limit: 35 }],
	cursed_zombie: [{ bone: 'shroud', period: 18, damp: 0.25, limit: 30, drag: 3 }, { bone: 'wrap_f', period: 12, limit: 30 }, { bone: 'hair', period: 12, limit: 30 },
		{ bone: 'wisp_0', period: 20, damp: 0.15, limit: 40, drag: 4 }, { bone: 'wisp_1', period: 22, damp: 0.15, limit: 40, drag: 4 }, { bone: 'wisp_2', period: 18, damp: 0.15, limit: 40, drag: 4 }],
	pillager_spy: [{ bone: 'cape', period: 18, damp: 0.28, limit: 35, drag: 3 }, { bone: 'cloak_r', period: 14, limit: 25 }, { bone: 'cloak_l', period: 14, limit: 25 }, { bone: 'hood_tip', period: 10, limit: 30 }],
	powered_zombie: [{ bone: 'loin_f', period: 14, limit: 30 }, { bone: 'loin_b', period: 16, limit: 30, drag: 2 }],
};

function run() {
	const timing = parseTiming();
	const out = { transition: T, source: 'src/main/java/com/projecthero/mod/event/entity/RaidAnims.java', note: 'length/hit = server ticks (RaidAnims); a play-once/held clip is (length - 3) ticks long and keys its strike on (hit - 3); loops are length ticks.', fit_ignore_bones: FIT_IGNORE, models: {} };
	const previews = [];
	const unit = (id, M, clipsFn, poses, opts = {}) => {
		if (!G.WANT(id)) return;
		gameLook(M, opts);
		const an = new A.AnimSet(M, id, { transition: T, neverKey: ['neck'], dangles: DANGLE[id] || [], hidden: opts.hidden || [], feet: c => c.tag === 'sole' });
		clipsFn(an, timing[id], poses);
		const files = writeGame(M, id, an);
		const fitPx = fitHeightPx(M);
		out.models[id] = { texture: [M.TW, M.TH], bones: M.bones.length, cubes: M.cubes().length, rest_fit_height_px: +fitPx.toFixed(2), height_blocks: HEIGHTS[id], clips: {} };
		for (const [n, t] of Object.entries(timing[id])) {
			if (!an.timing[n]) throw new Error(`${id}: clip ${n} from RaidAnims was not authored`);
			out.models[id].clips[n] = Object.assign({ server_length: t.length, hit: t.hit, kind: t.kind, layer: t.layer, clip_ticks: an.timing[n].length, strike_clip_tick: t.hit >= T ? t.hit - T : null },
				an.timing[n].move_speed ? { move_speed: an.timing[n].move_speed, stride_blocks: an.timing[n].stride_blocks } : {});
		}
		for (const n of Object.keys(an.clips)) if (!timing[id][n]) throw new Error(`${id}: authored clip ${n} is not in RaidAnims`);
		previews.push({ id, files, k: HEIGHTS[id] / fitPx, clips: an.clips, height: HEIGHTS[id], timing: timing[id], hide: opts.previewHide });
		console.log(`${id}: ${M.bones.length} bones, ${M.cubes().length} cubes, tex ${M.TW}x${M.TH}, ${Object.keys(an.clips).length} clips`);
	};
	{ const M = U.buildRaidZombie(); unit('raid_zombie', M, CL.raidZombieClips, U.raidZombiePoses(M), { previewHide: U.ARMOUR }); }
	{ const M = U.buildBabyZombie(); unit('raid_zombie_baby', M, CL.babyClips, U.babyPoses(M)); }
	{
		const M = U.buildAcidZombie();
		unit('acid_zombie', M, CL.acidClips, U.acidPoses(M), { halos: { mats: ['acid'], col: () => [120, 255, 60], eyes: eyeHalos(M, [150, 255, 90], 0.8) } });
	}
	{ const M = U2.buildJuggernaut(); unit('juggernaut_zombie', M, CL.juggernautClips, U2.juggernautPoses(M), { halos: { mats: [], col: null, eyes: [-1, 1].map(k => ({ bone: 'mask', c: [k * 1.45, M.A.eyeY - 0.1, M.A.faceZ - 0.95], s: [2.6, 1.3, 0.15], col: [255, 110, 30] })) } }); }
	{ const M = U2.buildSkeleton(); unit('sword_skeleton', M, CL.skeletonClips, U2.skeletonPoses(M), { halos: { mats: [], col: null, eyes: [-1, 1].map(k => ({ bone: 'head', c: [k * 1.3, M.A.eyeY, M.A.faceZ - 0.1], s: [1.6, 1.6, 0.15], col: [140, 190, 255] })) } }); }
	{ const M = U3.buildCursedZombie(); unit('cursed_zombie', M, CL.cursedClips, U3.cursedPoses(M), { halos: { mats: ['wisp'], col: () => [170, 70, 255], eyes: eyeHalos(M, [190, 90, 255], 1.1) } }); }
	{ const M = U3.buildSpy(); unit('pillager_spy', M, CL.spyClips, U3.spyPoses(M), { hidden: ['spyglass_hand'], previewHide: c => c.bone === 'spyglass_hand' }); }
	{
		const M = U3.buildBoss();
		unit('powered_zombie', M, CL.bossClips, U3.bossPoses(M), { halos: { mats: ['pglow'], col: () => [235, 235, 235], eyes: eyeHalos(M, [255, 255, 255], 1.0) }, previewHide: c => U3.accentOf(c) && U3.accentOf(c) !== 'acc_shards' });
	}
	// the acid glob projectile: a wobbling glowing blob with drips (no animation file; the renderer spins/squashes it)
	if (G.WANT('acid_glob')) {
		const M = new L.Model('acid_glob', { TW: 64 });
		M.bone('root', null, [0, 0, 0]);
		const gb = M.bone('glob', 'root', [0, 2.5, 0]);
		G.blob(M, gb, [0, 2.5, 0], [4.4, 4.0, 4.4], 'acid', { seed: 1 });
		G.blob(M, gb, [1.2, 3.6, -0.8], [1.8, 1.6, 1.8], 'acid', { seed: 2 });
		M.cbox(gb, [-0.6, 0.6, 0.4], [0.7, 1.4, 0.7], 'acid', { seed: 3 });
		M.customMat = (c, f, s, t, p, info) => { const col = U.glowBody(p, f, info, [30, 120, 16], [200, 255, 120], c.seed * 3, true); return [col, 255, col, 255]; };
		M.k = 1 / 16; M.MODEL_SCALE = 1;
		addHalos(M, ['acid'], () => [110, 255, 50]);
		M.layout(); M.paint(gameShade(M));
		const gf = writeGame(M, 'acid_glob', null);
		fs.writeFileSync(gf.anim, JSON.stringify({ format_version: '1.8.0', animations: {} }, null, '\t') + '\n');
		console.log(`acid_glob: ${M.cubes().length} cubes`);
	}
	if (!G.ONLY) {
		fs.writeFileSync(path.join(REPO, 'scratchpad/raid_undead_anim_timing.json'), JSON.stringify(out, null, '\t') + '\n');
		console.log('wrote scratchpad/raid_undead_anim_timing.json');
	}
	if (!G.args.includes('--no-previews')) for (const p of previews) preview(p);
}

function preview(p) {
	const M = A.loadGameModel(p.files.geo, p.files.tex, p.files.glow, p.k * 16, true);
	M.heightBlocks = p.height;
	const anims = A.loadAnimations(p.files.anim);
	const key = n => `animation.${p.id}.${n}`;
	const frames = [];
	const add = (label, n, t) => { const c = p.clips[n]; if (c) frames.push([label, n, Math.max(0, Math.min(c.len, Math.round(t)))]); };
	add('IDLE', 'idle', 0); add('IDLE (LATER)', 'idle', p.clips.idle.len * 0.42);
	add('WALK (CONTACT)', 'walk', 0); add('WALK (PASSING)', 'walk', p.clips.walk.len / 4);
	for (const n of ['chase', 'run', 'hover']) if (p.clips[n]) add(n.toUpperCase(), n, p.clips[n].len / 2);
	for (const [n, t] of Object.entries(p.timing)) {
		if (['idle', 'walk', 'chase', 'run', 'hover'].includes(n)) continue;
		const c = p.clips[n];
		if (t.hit >= T) { if (t.hit - T > 2) add(`${n.toUpperCase().replace(/_/g, ' ')} WIND-UP`, n, (t.hit - T) * 0.75); add(`${n.toUpperCase().replace(/_/g, ' ')} AT HIT ${t.hit}`, n, t.hit - T); }
		else add(`${n.toUpperCase().replace(/_/g, ' ')} (END)`, n, c.len);
	}
	const dir = path.join(G.PREVIEW, 'anim');
	fs.mkdirSync(dir, { recursive: true });
	const per = 6;
	for (let i = 0; i * per < frames.length; i++) {
		const panels = frames.slice(i * per, i * per + per).map(([label, n, t]) => {
			const pose = A.poseAt(anims, key(n), t);
			const hidden = new Set(pose.$hidden || []);
			for (const b of M.bones) if (b.parent && hidden.has(b.parent)) hidden.add(b.name);
			return { label, items: [{ model: M, pose, root: pose.$root, hide: c => hidden.has(c.bone) || (p.hide && p.hide(c)), floorR: p.height * 0.45 + 0.6 }], cam: { yaw: 32, pitch: 6 } };
		});
		L.sheet(path.join(dir, `${p.id}_${i + 1}.png`), `${p.id.toUpperCase().replace(/_/g, ' ')} - GAME ASSETS`, `PAGE ${i + 1} - POSES READ BACK FROM THE ANIMATION JSON`, panels, { minW: 2000, panelH: 900, maxW: 4200, ss: 1 });
	}
}

module.exports = { run, parseTiming, FIT_IGNORE, HEIGHTS };
