// Preview sheets for Wildmutt (software renderer from lib_concept_models): the rest views and the clips' key frames.
'use strict';
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const anims = require('./anims.js');

// world() that understands clip frames ({ rot, pos, scale } per bone), like GeckoLib does
function poseWorld(M, frame) {
	const W = {};
	for (const b of M.bones) {
		const ch = frame[b.name] || {};
		const rr = ch.rot || [0, 0, 0];
		const r = [b.rot[0] + rr[0], b.rot[1] + rr[1], b.rot[2] + rr[2]];
		let local = L.pivotRot(b.pivot, r);
		const sc = ch.scale || [1, 1, 1];
		if (sc[0] !== 1 || sc[1] !== 1 || sc[2] !== 1) {
			const S = [[sc[0], 0, 0], [0, sc[1], 0], [0, 0, sc[2]]];
			const ms = L.mv3(S, b.pivot);
			local = L.affMul(local, L.aff(S, [b.pivot[0] - ms[0], b.pivot[1] - ms[1], b.pivot[2] - ms[2]]));
		}
		if (ch.pos) local = L.affMul(L.aff([[1, 0, 0], [0, 1, 0], [0, 0, 1]], ch.pos), local);
		W[b.name] = b.parent ? L.affMul(W[b.parent], local) : local;
	}
	return W;
}

module.exports = function (M, dir) {
	M.k = 1 / 16; M.heightBlocks = 1.6;
	const withFrame = frame => {
		const W = poseWorld(M, frame);
		const m = Object.create(M);
		m.world = () => W;
		m.cubes = () => M.cubes();
		m.k = M.k; m.heightBlocks = M.heightBlocks;
		return [{ model: m, pose: {} }];
	};
	const rest = withFrame(anims.sample('idle', 0));
	const fl = [{ x: 0, z: 0, r: 2.0 }];
	const bg = { top: [70, 92, 110], bottom: [30, 40, 48] };
	const opts = { panelH: 760, minW: 2000, bg, bloom: 0.2, edgeThr: 0.05 };
	L.sheet(path.join(dir, 'wildmutt_sheet.png'), 'WILDMUTT', 'VIEWS', [
		{ label: 'THREE-QUARTER', items: rest, cam: { yaw: 35, pitch: 14 }, floor: fl },
		{ label: 'FRONT', items: rest, cam: { yaw: 0, pitch: 8 }, floor: fl },
		{ label: 'SIDE', items: rest, cam: { yaw: 90, pitch: 6 }, floor: fl },
		{ label: 'BACK', items: rest, cam: { yaw: 200, pitch: 16 }, floor: fl },
	], opts);
	const at = (name, t, yaw = 60, label) => ({ label: label || (name + ' ' + t.toFixed(2)), items: withFrame(anims.sample(name, t)), cam: { yaw, pitch: 10 }, floor: fl });
	L.sheet(path.join(dir, 'wildmutt_poses1.png'), 'WILDMUTT', 'LOCOMOTION', [
		at('walk', 0.0, 90), at('walk', 0.14, 90), at('walk', 0.28, 90), at('gallop', 0.0, 90), at('gallop', 0.1, 90), at('gallop', 0.21, 90), at('gallop', 0.31, 90),
	], Object.assign({}, opts, { maxW: 3400 }));
	L.sheet(path.join(dir, 'wildmutt_poses2.png'), 'WILDMUTT', 'MOVES', [
		at('pounce', 0.12, 70), at('pounce', 0.27, 70), at('pounce', 0.42, 70), at('roar', 0.6, 40), at('maul', 0.1, 50), at('maul', 0.64, 60),
	], Object.assign({}, opts, { maxW: 3400 }));
	L.sheet(path.join(dir, 'wildmutt_poses3.png'), 'WILDMUTT', 'MOVES 2', [
		at('slam', 0.34, 70), at('slam', 0.46, 70), at('quills', 0.3, 40), at('roll', 0.1, 90), at('climb', 0.2, 120), at('air', 0.5, 80), at('leap_charge', 0.1, 70), at('idle_sniff', 1.0, 40),
	], Object.assign({}, opts, { maxW: 3400 }));
};
