// Preview sheets for Stinkfly (software renderer from lib_concept_models): the rest views and the clips' key frames. The
// renderer cuts texels under alpha 128, so the see-through wings are drawn solid here (in game they are translucent).
'use strict';
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const anims = require('./anims.js');

function poseWorld(M, frame) {
	const W = {};
	for (const b of M.bones) {
		const ch = frame[b.name] || {};
		const rr = ch.rot || [0, 0, 0];
		const r = [b.rot[0] + rr[0], b.rot[1] + rr[1], b.rot[2] + rr[2]];
		let local = L.pivotRot(b.pivot, r);
		if (ch.pos) local = L.affMul(L.aff([[1, 0, 0], [0, 1, 0], [0, 0, 1]], ch.pos), local);
		W[b.name] = b.parent ? L.affMul(W[b.parent], local) : local;
	}
	return W;
}
// several clips layered the way the controllers do (later wins per bone)
const layered = (...frames) => Object.assign({}, ...frames);

module.exports = function (M, dir) {
	M.k = 1 / 16; M.heightBlocks = 1.9;
	// the preview's copy of the texture: wing texels with any alpha drawn solid
	const tex = Buffer.from(M.tex);
	for (let i = 3; i < tex.length; i += 4) if (tex[i] > 20) tex[i] = 255;
	const withFrame = frame => {
		const W = poseWorld(M, frame);
		const m = Object.create(M);
		m.world = () => W;
		m.cubes = () => M.cubes();
		m.tex = tex;
		m.k = M.k; m.heightBlocks = M.heightBlocks;
		return [{ model: m, pose: {} }];
	};
	const S = anims.sample;
	const ground = (t = 0) => layered(S('idle', t), S('wings_fold', 0));
	const rest = withFrame(ground());
	const fl = [{ x: 0, z: 0, r: 2.0 }];
	const bg = { top: [70, 92, 110], bottom: [30, 40, 48] };
	const opts = { panelH: 760, minW: 2000, bg, bloom: 0.2, edgeThr: 0.05 };
	const spread = withFrame(layered(S('hover', 0.2), S('wings_hover', 0.0)));
	L.sheet(path.join(dir, 'stinkfly_sheet.png'), 'STINKFLY', 'VIEWS', [
		{ label: 'THREE-QUARTER', items: rest, cam: { yaw: 35, pitch: 10 }, floor: fl },
		{ label: 'FRONT', items: rest, cam: { yaw: 0, pitch: 6 }, floor: fl },
		{ label: 'SIDE', items: rest, cam: { yaw: 90, pitch: 6 }, floor: fl },
		{ label: 'BACK', items: rest, cam: { yaw: 200, pitch: 14 }, floor: fl },
		{ label: 'HOVER FRONT', items: spread, cam: { yaw: 20, pitch: 8 }, floor: fl },
		{ label: 'HOVER BACK', items: spread, cam: { yaw: 160, pitch: 18 }, floor: fl },
	], Object.assign({}, opts, { maxW: 3400 }));
	const at = (main, t, wings, wt, yaw = 60, move) => ({ label: (move || main) + ' ' + t.toFixed(2), items: withFrame(layered(S(main, move ? 0.3 : t), move ? S(move, t) : {}, S(wings, wt == null ? t : wt))), cam: { yaw, pitch: 10 }, floor: fl });
	L.sheet(path.join(dir, 'stinkfly_poses1.png'), 'STINKFLY', 'LOCOMOTION', [
		at('walk', 0.0, 'wings_walk', null, 90), at('walk', 0.12, 'wings_walk', null, 90), at('walk', 0.25, 'wings_walk', null, 90),
		at('hover', 0.4, 'wings_hover', 0.04, 50), at('hover', 0.4, 'wings_hover', 0.11, 50),
		at('fly', 0.2, 'wings_fly', 0.05, 90), at('fly', 0.2, 'wings_fly', 0.15, 40), at('dive', 0.5, 'wings_dive', 0, 90),
	], Object.assign({}, opts, { maxW: 3400 }));
	const mv = (move, t, yaw = 50, body = 'idle', wings = 'wings_fold') => at(body, t, wings, 0.3, yaw, move);
	L.sheet(path.join(dir, 'stinkfly_poses2.png'), 'STINKFLY', 'MOVES', [
		mv('shot', 0.16, 60), mv('barrage', 0.2, 30), mv('net', 0.18, 60), mv('net', 0.3, 60), mv('gas', 0.6, 80), mv('stab', 0.12, 90), mv('stab', 0.22, 90),
		mv('jab', 0.08, 90),
	], Object.assign({}, opts, { maxW: 3400 }));
	L.sheet(path.join(dir, 'stinkfly_poses3.png'), 'STINKFLY', 'MOVES 2', [
		Object.assign(mv('gust', 0.16, 40, 'hover', 'wings_gust'), { items: withFrame(layered(S('hover', 0.3), S('gust', 0.16), S('wings_gust', 0.16))) }),
		Object.assign(mv('gust', 0.3, 40, 'hover', 'wings_gust'), { items: withFrame(layered(S('hover', 0.3), S('gust', 0.3), S('wings_gust', 0.3))) }),
		mv('storm', 0.6, 40), at('takeoff', 0.16, 'wings_fold', 0.1, 70), at('takeoff', 0.3, 'wings_fly', 0.05, 70), at('splash', 0.1, 'wings_fold', 0.1, 60),
		at('lift_hold', 0.3, 'wings_hover', 0.05, 70), at('dash', 0.3, 'wings_dive', 0.05, 90),
	], Object.assign({}, opts, { maxW: 3400 }));
};
