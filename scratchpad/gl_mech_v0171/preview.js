// Preview sheets for the Hard-Light Mech: FRONT / THREE-QUARTER / SIDE / BACK (rest stance with the pilot seated in the
// cockpit) and an action sheet (punch, ground slam, beam, rocket fist, shield, missiles) sampled from the real clips.
'use strict';
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const anims = require('./anims.js');
const { Model } = L;

// world() that also understands clip frames ({ rot, pos, scale } per bone), like GeckoLib does
function poseWorld(M, frame) {
	const W = {};
	for (const b of M.bones) {
		const ch = frame[b.name] || {};
		const r = [b.rot[0] + ((ch.rot || [0, 0, 0])[0]), b.rot[1] + ((ch.rot || [0, 0, 0])[1]), b.rot[2] + ((ch.rot || [0, 0, 0])[2])];
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

// a little seated pilot (Green Lantern suit) for the cockpit
function pilot() {
	const P = new Model('pilot', { TW: 128, D: 2 });
	P.bone('root', null, [0, 0, 0]);
	const b = P.bone('body', 'root', [0, 12, 0]);
	P.box(b, -4, 4, -2, 4, 16, 2, 'suit');
	P.box(b, -4, 16, -4, 4, 24, 4, 'head');
	P.box(b, -4.2, 19.5, -4.25, 4.2, 21.5, -4.05, 'mask');
	P.box(b, -8, 5, -2, -4, 16, 2, 'arm');
	P.box(b, 4, 5, -2, 8, 16, 2, 'arm');
	P.box(b, -4, 0, -12, 0, 4, 0, 'leg'); // legs straight forward (riding)
	P.box(b, 0, 0, -12, 4, 4, 0, 'leg');
	P.layout();
	P.paint((c, f, s, t, p) => {
		switch (c.mat) {
			case 'head': return [p[1] > 22 ? [40, 30, 24] : [255, 210, 170], 255, null];
			case 'mask': return [[30, 160, 70], 255, [80, 255, 140], 255];
			case 'arm': return [p[1] < 7 ? [60, 220, 110] : [70, 78, 74], 255, null];
			case 'leg': return [p[2] < -10 ? [70, 78, 74] : [90, 255, 150], 255, null];
			default: { // suit: green chest panel, black sides, the emblem
				const dx = p[0], dy = p[1] - 12;
				if (f === 'north' && Math.hypot(dx, dy) < 2.2 && Math.hypot(dx, dy) > 1.2) return [[230, 255, 235], 255, [180, 255, 200], 255];
				return [Math.abs(p[0]) < 2.8 || f !== 'north' && f !== 'south' ? [90, 255, 150] : [70, 78, 74], 255, null];
			}
		}
	});
	P.k = 1 / 16; P.heightBlocks = 2;
	return P;
}

module.exports = function (M, ROOT) {
	M.k = 1 / 16; M.heightBlocks = 6.4;
	const P = pilot();
	const SEAT = [0, 57 / 16, 0.5 / 16]; // pilot feet origin in blocks (bottom of the shins) -- matches the entity seat
	const shot = (frame, extra = {}) => {
		const W = poseWorld(M, frame);
		// the pilot rides the torso: follow its transform
		const items = [Object.assign({ model: M, pose: {}, root: [0, 0, 0] }, extra)];
		return { items, W };
	};
	// gather() calls M.world(pose, root); swap in our frame-aware world for this model
	const withFrame = frame => {
		const W = poseWorld(M, frame);
		const m = Object.create(M);
		m.world = () => W;
		m.cubes = () => M.cubes();
		m.k = M.k; m.heightBlocks = M.heightBlocks;
		const tw = W.torso;
		const pm = Object.create(P);
		pm.world = () => {
			const o = {};
			// pilot pixels -> mech torso space (16 px per block): seat offset then the torso transform
			const seat = L.aff([[1, 0, 0], [0, 1, 0], [0, 0, 1]], [SEAT[0] * 16, SEAT[1] * 16, SEAT[2] * 16]);
			const base = L.affMul(tw, seat);
			for (const b of P.bones) o[b.name] = L.affMul(base, L.pivotRot(b.pivot, b.rot));
			return o;
		};
		pm.cubes = () => P.cubes();
		pm.k = M.k; pm.heightBlocks = 2;
		return [{ model: m, pose: {} }, { model: pm, pose: {} }];
	};
	const rest = withFrame(anims.sample('idle', 0));
	const fl = [{ x: 0, z: 0, r: 3.2 }];
	const bg = { top: [36, 44, 52], bottom: [12, 16, 20] };
	const light = Object.assign({}, L.DEFAULT_LIGHT, { rimCol: [0.55, 1.0, 0.7], rimK: 0.45, amb: 0.42 });
	const opts = { panelH: 900, minW: 2200, bg, bloom: 0.55, edgeThr: 0.05, light };
	const out = path.join(ROOT, 'docs', 'screenshots', 'v0171');
	L.sheet(path.join(out, 'gl_mech_sheet.png'), 'HARD-LIGHT MECH', 'GREEN LANTERN ULTIMATE CONSTRUCT - 6.4 BLOCKS - PILOT IN THE CHEST COCKPIT', [
		{ label: 'FRONT', items: rest, cam: { yaw: 0, pitch: 6 }, floor: fl },
		{ label: 'THREE-QUARTER', items: rest, cam: { yaw: 35, pitch: 12 }, floor: fl },
		{ label: 'SIDE', items: rest, cam: { yaw: 90, pitch: 6 }, floor: fl },
		{ label: 'BACK', items: rest, cam: { yaw: 180, pitch: 10 }, floor: fl },
	], opts);
	const at = (name, t, yaw = 35) => ({ label: name.replace('_', ' ') + ' ' + t.toFixed(2), items: withFrame(anims.sample(name, t)), cam: { yaw, pitch: 10 }, floor: fl });
	L.sheet(path.join(out, 'gl_mech_poses.png'), 'HARD-LIGHT MECH', 'CLIPS 1', [
		at('walk', 0.15, 70), at('punch_r', 0.25, 50), at('punch_l', 0.25, -50), at('ground_slam', 0.4, 60), at('ground_slam', 0.65, 60),
	], Object.assign({}, opts, { panelH: 800, maxW: 3000 }));
	L.sheet(path.join(out, 'gl_mech_poses2.png'), 'HARD-LIGHT MECH', 'CLIPS 2', [
		at('beam', 0.1, 50), at('rocket_fist', 0.5, 60), at('shield', 0.2, 40), at('missiles', 0.3, 30), at('dash', 0.3, 80), at('assemble', 0.9, 30),
	], Object.assign({}, opts, { panelH: 800, maxW: 3200 }));
};
