// v0.17.5 Symbiote Monster form: the animations for gen_symbiote_monster_v0175.js (called from it; not run on its own).
// Every clip is a function of time returning per-bone offsets on top of the hunched rest pose -- rotations in degrees (bedrock
// convention: -X swings a hanging limb forward, +X tips an upright part forward, +Y turns the right shoulder back, +Z swings the right
// arm out), '@bone' positions in px, '#bone' scales -- sampled every 0.05 s (one tick) into GeckoLib keyframes.
'use strict';
const fs = require('fs');
const path = require('path');

const S = Math.sin, C = Math.cos, PI = Math.PI;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const ease = (x) => { x = clamp(x, 0, 1); return x * x * (3 - 2 * x); };
const easeOut = (x) => { x = clamp(x, 0, 1); return 1 - (1 - x) * (1 - x) * (1 - x); };
const easeIn = (x) => { x = clamp(x, 0, 1); return x * x * x; };
/** 0 -> 1 over [a, b], held, 1 -> 0 over [c, d]. */
const win = (t, a, b, c, d) => (t < a ? 0 : t < b ? ease((t - a) / (b - a)) : t < c ? 1 : t < d ? 1 - ease((t - c) / (d - c)) : 0);
/** Blends keyed values: keys [[t, v], ...] with smoothstep between them. v may be a number or an array. */
function keys(t, ks) {
	if (t <= ks[0][0]) return ks[0][1];
	for (let i = 0; i < ks.length - 1; i++) {
		const [t0, v0] = ks[i], [t1, v1] = ks[i + 1];
		if (t <= t1) {
			const u = ease((t - t0) / (t1 - t0));
			return Array.isArray(v0) ? v0.map((x, k) => x + (v1[k] - x) * u) : v0 + (v1 - v0) * u;
		}
	}
	return ks[ks.length - 1][1];
}
const add = (pose, bone, v) => { const o = pose[bone] || [0, 0, 0]; pose[bone] = [o[0] + v[0], o[1] + v[1], o[2] + v[2]]; };

/** Shared living motion: breathing, the tongue and back tendrils writhing, fingers flexing -- layered into every clip. */
function alive(p, t, k) {
	k = k == null ? 1 : k;
	const br = S(t * 2 * PI / 3.2);
	add(p, 'chest', [1.8 * br * k, 0, 0]);
	add(p, 'belly', [0.8 * br * k, 0, 0]);
	add(p, 'head', [-1.4 * br * k, 2.5 * S(t * 0.9) * k, 1.2 * S(t * 0.7) * k]);
	add(p, 'jaw', [(3 + 3 * br) * k, 0, 0]);
	for (let i = 0; i < 7; i++) add(p, 'tongue_' + i, [4 * S(t * 3.1 - i * 0.7) * k, 7 * S(t * 2.3 - i * 0.6) * k, 0]);
	for (const [tn, ph] of [['a', 0], ['b', 1.9], ['c', 3.4]]) {
		for (let i = 0; i < 4; i++) add(p, 'tend_' + tn + i, [9 * S(t * 2.4 + ph - i * 0.8) * k, 0, 12 * S(t * 1.7 + ph - i * 0.9) * k]);
	}
	for (const s of ['r', 'l']) for (let i = 0; i < 4; i++) add(p, 'finger_' + s + i, [5 * S(t * 1.3 + i * 0.6 + (s === 'l' ? 2 : 0)) * k, 0, 0]);
	add(p, 'arm_r', [2 * br * k, 0, 1.5 * br * k]);
	add(p, 'arm_l', [2 * br * k, 0, -1.5 * br * k]);
}

const CLIPS = {};
function clip(name, length, loop, fn) { CLIPS[name] = { length, loop, fn }; }

// ------------------------------------------------------------------------------------------------ locomotion
clip('idle', 3.2, true, (t) => {
	const p = {};
	alive(p, t, 1);
	p['@pelvis'] = [0, 0.5 * S(t * 2 * PI / 3.2), 0];
	return p;
});
function gait(t, len, amp, lean, bob) {
	const p = {};
	const ph = (t / len) * 2 * PI;
	alive(p, t, 0.6);
	add(p, 'leg_r', [amp * S(ph), 0, 0]);
	add(p, 'leg_l', [-amp * S(ph), 0, 0]);
	add(p, 'shin_r', [Math.max(0, amp * 1.1 * S(ph + 1.4)), 0, 0]);
	add(p, 'shin_l', [Math.max(0, amp * 1.1 * S(ph + 1.4 + PI)), 0, 0]);
	add(p, 'foot_r', [-amp * 0.4 * S(ph + 0.6), 0, 0]);
	add(p, 'foot_l', [amp * 0.4 * S(ph + 0.6), 0, 0]);
	// the arms knuckle along: opposite the legs, reaching forward on the down-beat
	add(p, 'arm_r', [-amp * 0.9 * S(ph) - lean * 0.3, 0, 3]);
	add(p, 'arm_l', [amp * 0.9 * S(ph) - lean * 0.3, 0, -3]);
	add(p, 'forearm_r', [-Math.max(0, amp * 0.7 * S(ph + 1.2)), 0, 0]);
	add(p, 'forearm_l', [-Math.max(0, amp * 0.7 * S(ph + 1.2 + PI)), 0, 0]);
	add(p, 'chest', [lean + 2 * S(2 * ph), 6 * S(ph) * (amp / 22), 4 * S(ph)]);
	add(p, 'head', [-lean - 2 * S(2 * ph), -6 * S(ph) * (amp / 22), -4 * S(ph)]);
	p['@pelvis'] = [0, -bob * Math.abs(C(ph)), 0];
	add(p, 'pelvis', [0, 5 * S(ph) * (amp / 22), 0]);
	return p;
}
clip('walk', 1.6, true, (t) => gait(t, 1.6, 20, 2, 1.4));
clip('run', 0.9, true, (t) => gait(t, 0.9, 34, 12, 2.8));

// ------------------------------------------------------------------------------------------------ the moves
// R: heavy claw swipe (1.0 s; the hit lands at tick 6 = 0.3 s)
clip('swipe', 1.0, false, (t) => {
	const p = {};
	alive(p, t, 0.4);
	add(p, 'chest', [keys(t, [[0, 0], [0.2, -4], [0.32, 12], [0.6, 8], [1.0, 0]]), keys(t, [[0, 0], [0.2, 28], [0.34, -32], [0.65, -22], [1.0, 0]]), 0]);
	add(p, 'arm_r', keys(t, [[0, [0, 0, 0]], [0.2, [-95, -25, 70]], [0.34, [-80, 45, -5]], [0.6, [-60, 40, -10]], [1.0, [0, 0, 0]]]));
	add(p, 'forearm_r', keys(t, [[0, [0, 0, 0]], [0.2, [-50, 0, 0]], [0.34, [-5, 0, 0]], [1.0, [0, 0, 0]]]));
	for (let i = 0; i < 4; i++) add(p, 'finger_r' + i, keys(t, [[0, [0, 0, 0]], [0.2, [30, 0, 0]], [0.32, [-25, 0, 0]], [1.0, [0, 0, 0]]]));
	add(p, 'arm_l', keys(t, [[0, [0, 0, 0]], [0.2, [-20, 0, -10]], [0.34, [15, 0, -20]], [1.0, [0, 0, 0]]]));
	add(p, 'head', [0, keys(t, [[0, 0], [0.2, -18], [0.34, 22], [1.0, 0]]), 0]);
	add(p, 'jaw', [keys(t, [[0, 0], [0.2, 10], [0.34, 22], [0.7, 6], [1.0, 0]]), 0, 0]);
	add(p, 'pelvis', [0, keys(t, [[0, 0], [0.2, 10], [0.34, -14], [1.0, 0]]), 0]);
	return p;
});
// G: ground pound (1.2 s; impact at tick 10 = 0.5 s)
clip('pound', 1.2, false, (t) => {
	const p = {};
	alive(p, t, 0.3);
	const up = keys(t, [[0, 0], [0.38, 1], [0.5, -0.2], [0.85, -0.2], [1.2, 0]]);
	const slam = keys(t, [[0, 0], [0.38, 0], [0.5, 1], [0.85, 1], [1.2, 0]]);
	add(p, 'arm_r', [-170 * Math.max(0, up) - 55 * slam, 0, 14 * Math.max(0, up) - 6 * slam]);
	add(p, 'arm_l', [-170 * Math.max(0, up) - 55 * slam, 0, -14 * Math.max(0, up) + 6 * slam]);
	add(p, 'forearm_r', [-30 * Math.max(0, up) + 10 * slam, 0, 0]);
	add(p, 'forearm_l', [-30 * Math.max(0, up) + 10 * slam, 0, 0]);
	add(p, 'chest', [-22 * Math.max(0, up) + 26 * slam, 0, 0]);
	add(p, 'belly', [-8 * Math.max(0, up) + 10 * slam, 0, 0]);
	add(p, 'head', [12 * Math.max(0, up) - 20 * slam, 0, 0]);
	add(p, 'jaw', [20 * Math.max(0, up) + 14 * slam, 0, 0]);
	add(p, 'leg_r', [-24 * slam, 0, 6 * slam]);
	add(p, 'leg_l', [-24 * slam, 0, -6 * slam]);
	add(p, 'shin_r', [30 * slam, 0, 0]);
	add(p, 'shin_l', [30 * slam, 0, 0]);
	p['@pelvis'] = [0, 2 * Math.max(0, up) - 6 * slam, 0];
	return p;
});
// X: the pounce (0.5 s crouch + spring, then the air pose is held until the landing clip)
clip('leap', 0.5, false, (t) => {
	const p = {};
	alive(p, t, 0.3);
	const crouch = keys(t, [[0, 0], [0.15, 1], [0.22, 0], [0.5, 0]]);
	const air = keys(t, [[0, 0], [0.15, 0], [0.3, 1], [0.5, 1]]);
	add(p, 'chest', [20 * crouch - 10 * air, 0, 0]);
	add(p, 'arm_r', [30 * crouch - 120 * air, 0, 10 * crouch + 20 * air]);
	add(p, 'arm_l', [30 * crouch - 120 * air, 0, -10 * crouch - 20 * air]);
	add(p, 'forearm_r', [-20 * air, 0, 0]);
	add(p, 'forearm_l', [-20 * air, 0, 0]);
	add(p, 'leg_r', [-40 * crouch + 20 * air, 0, 0]);
	add(p, 'leg_l', [-40 * crouch - 25 * air, 0, 0]);
	add(p, 'shin_r', [50 * crouch + 40 * air, 0, 0]);
	add(p, 'shin_l', [50 * crouch + 50 * air, 0, 0]);
	add(p, 'head', [-14 * air, 0, 0]);
	add(p, 'jaw', [26 * air, 0, 0]);
	p['@pelvis'] = [0, -7 * crouch, 0];
	return p;
});
clip('land', 0.7, false, (t) => {
	const p = {};
	alive(p, t, 0.3);
	const k = keys(t, [[0, 1], [0.12, 1], [0.7, 0]]);
	add(p, 'chest', [26 * k, 0, 0]);
	add(p, 'arm_r', [-60 * k, 0, 18 * k]);
	add(p, 'arm_l', [-60 * k, 0, -18 * k]);
	add(p, 'leg_r', [-36 * k, 0, 8 * k]);
	add(p, 'leg_l', [-36 * k, 0, -8 * k]);
	add(p, 'shin_r', [44 * k, 0, 0]);
	add(p, 'shin_l', [44 * k, 0, 0]);
	add(p, 'head', [-22 * k, 0, 0]);
	add(p, 'jaw', [18 * k, 0, 0]);
	p['@pelvis'] = [0, -8 * k, 0];
	return p;
});
// Z: roar (1.5 s; the blast at tick 6 = 0.3 s)
clip('roar', 1.5, false, (t) => {
	const p = {};
	alive(p, t, 0.3);
	const r = keys(t, [[0, 0], [0.22, 1], [1.15, 1], [1.5, 0]]);
	const sh = r * (S(t * 70) * 1.6 + S(t * 53) * 1.0);
	add(p, 'chest', [-24 * r + sh, sh * 1.5, 0]);
	add(p, 'belly', [-8 * r, 0, 0]);
	add(p, 'head', [-4 * r + sh, 0, sh]);
	add(p, 'jaw', [38 * r, 0, 0]);
	add(p, 'arm_r', [-40 * r, -10 * r, 62 * r]);
	add(p, 'arm_l', [-40 * r, 10 * r, -62 * r]);
	add(p, 'forearm_r', [-30 * r, 0, 0]);
	add(p, 'forearm_l', [-30 * r, 0, 0]);
	for (const s of ['r', 'l']) for (let i = 0; i < 4; i++) add(p, 'finger_' + s + i, [-30 * r, 0, 0]);
	for (let i = 0; i < 7; i++) add(p, 'tongue_' + i, [(-20 + 12 * S(t * 14 - i)) * r, 14 * S(t * 11 - i * 0.8) * r, 0]);
	for (const tn of ['a', 'b', 'c']) for (let i = 0; i < 4; i++) add(p, 'tend_' + tn + i, [-25 * r, 0, 10 * S(t * 18 + i) * r]);
	return p;
});
// C: devour (1.5 s; seized at tick 6, eaten at tick 18 = 0.9 s)
clip('devour', 1.5, false, (t) => {
	const p = {};
	alive(p, t, 0.3);
	const reach = keys(t, [[0, 0], [0.25, 1], [0.35, 1], [0.7, 0], [1.5, 0]]);
	const toMouth = keys(t, [[0, 0], [0.3, 0], [0.7, 1], [1.05, 1], [1.5, 0]]);
	add(p, 'chest', [22 * reach + 14 * toMouth, 0, 0]);
	add(p, 'arm_r', [-85 * reach - 70 * toMouth, 20 * reach + 30 * toMouth, -14 * reach]);
	add(p, 'arm_l', [-85 * reach - 70 * toMouth, -20 * reach - 30 * toMouth, 14 * reach]);
	add(p, 'forearm_r', [-10 * reach - 70 * toMouth, 0, 0]);
	add(p, 'forearm_l', [-10 * reach - 70 * toMouth, 0, 0]);
	for (const s of ['r', 'l']) for (let i = 0; i < 4; i++) add(p, 'finger_' + s + i, [-40 * reach + 30 * Math.max(toMouth, keys(t, [[0.3, 0], [0.35, 1]])), 0, 0]);
	const jaw = keys(t, [[0, 0], [0.5, 0.4], [0.8, 1], [0.92, -0.2], [1.05, 0.3], [1.15, -0.1], [1.25, 0.25], [1.5, 0]]);
	add(p, 'jaw', [40 * jaw, 0, 0]);
	add(p, 'head', [-18 * keys(t, [[0, 0], [0.8, 1], [0.92, 0], [1.5, 0]]), 0, 0]);
	for (let i = 0; i < 7; i++) add(p, 'tongue_' + i, [-18 * keys(t, [[0.4, 0], [0.7, 1], [0.9, 0]]), 0, 0]);
	return p;
});
// V: tendril pull (1.0 s; the yank at tick 6 = 0.3 s)
clip('pull', 1.0, false, (t) => {
	const p = {};
	alive(p, t, 0.3);
	const out = keys(t, [[0, 0], [0.2, 1], [0.3, 1], [0.45, 0], [1.0, 0]]);
	const yank = keys(t, [[0, 0], [0.3, 0], [0.45, 1], [0.7, 1], [1.0, 0]]);
	add(p, 'chest', [16 * out - 22 * yank, 0, 0]);
	add(p, 'arm_r', [-88 * out - 10 * yank, 0, 14 * out + 30 * yank]);
	add(p, 'arm_l', [-88 * out - 10 * yank, 0, -14 * out - 30 * yank]);
	add(p, 'forearm_r', [0, 0, 0]);
	add(p, 'forearm_l', [0, 0, 0]);
	add(p, 'forearm_r', [-60 * yank, 0, 0]);
	add(p, 'forearm_l', [-60 * yank, 0, 0]);
	for (const s of ['r', 'l']) for (let i = 0; i < 4; i++) add(p, 'finger_' + s + i, [-35 * out + 35 * yank, 0, 0]);
	add(p, 'head', [-8 * out + 6 * yank, 0, 0]);
	add(p, 'jaw', [16 * out + 24 * yank, 0, 0]);
	add(p, 'leg_r', [10 * yank, 0, 0]);
	add(p, 'leg_l', [-10 * yank, 0, 0]);
	for (const tn of ['a', 'b', 'c']) for (let i = 0; i < 4; i++) add(p, 'tend_' + tn + i, [30 * out - 20 * yank, 0, 0]);
	return p;
});
// the reveal out of the burst cocoon (1.6 s): crouched and wrapped, then up into a forward-leaning roar with the arms flung wide.
// Polish pass: the old fling arched the chest back over the hump (the face disappeared behind it from low / front angles for half a
// second); now the chest only ever straightens a little out of the hunch and the head is thrust forward and up, so the face reads.
clip('emerge', 1.6, false, (t) => {
	const p = {};
	alive(p, t, ease(t / 1.6));
	const coil = keys(t, [[0, 1], [0.12, 1], [0.32, 0], [1.6, 0]]);
	const fling = keys(t, [[0, 0], [0.12, 0], [0.32, 1], [1.15, 1], [1.6, 0]]);
	const sh = fling * (S(t * 64) * 1.4 + S(t * 47) * 0.8);
	add(p, 'chest', [22 * coil - 6 * fling + sh, sh * 0.8, 0]);
	add(p, 'belly', [10 * coil - 3 * fling, 0, 0]);
	// the head counters the chest so the face always looks ahead -- coiled it peers up from under the hump, flung it thrusts up and out
	add(p, 'head', [-30 * coil - 16 * fling + sh * 0.6, 0, sh]);
	add(p, 'jaw', [-10 * coil + 42 * fling, 0, 0]);
	add(p, 'arm_r', [-55 * coil + 35 * fling, 35 * coil - 30 * fling, -22 * coil + 120 * fling]);
	add(p, 'arm_l', [-55 * coil + 35 * fling, -35 * coil + 30 * fling, 22 * coil - 120 * fling]);
	add(p, 'forearm_r', [-85 * coil - 30 * fling, 0, 0]);
	add(p, 'forearm_l', [-85 * coil - 30 * fling, 0, 0]);
	for (const s of ['r', 'l']) for (let i = 0; i < 4; i++) add(p, 'finger_' + s + i, [20 * coil - 38 * fling, 0, 0]);
	add(p, 'leg_r', [-36 * coil - 10 * fling, 0, 4 * coil + 9 * fling]);
	add(p, 'leg_l', [-36 * coil - 10 * fling, 0, -4 * coil - 9 * fling]);
	add(p, 'shin_r', [54 * coil + 14 * fling, 0, 0]);
	add(p, 'shin_l', [54 * coil + 14 * fling, 0, 0]);
	for (let i = 0; i < 7; i++) add(p, 'tongue_' + i, [(-10 + 10 * S(t * 12 - i)) * fling, 16 * S(t * 9 - i) * fling, 0]);
	for (const tn of ['a', 'b', 'c']) for (let i = 0; i < 4; i++) add(p, 'tend_' + tn + i, [40 * coil - 34 * fling, 0, 18 * S(t * 15 + i) * fling]);
	p['@pelvis'] = [0, -9 * coil - 2 * fling, 0];
	return p;
});
// the melt back into goo (1.5 s, held): slumps, sinks and spreads into a puddle (the renderer adds the drips)
clip('melt', 1.5, false, (t) => {
	const p = {};
	const m = easeIn(t / 1.5);
	const sl = ease(t / 0.6);
	alive(p, t, 1 - sl);
	add(p, 'chest', [22 * sl, 0, 0]);
	add(p, 'head', [26 * sl, 0, 0]);
	add(p, 'jaw', [30 * sl, 0, 0]);
	add(p, 'arm_r', [-30 * sl, 0, 30 * m]);
	add(p, 'arm_l', [-30 * sl, 0, -30 * m]);
	add(p, 'leg_r', [0, 0, 20 * m]);
	add(p, 'leg_l', [0, 0, -20 * m]);
	for (const tn of ['a', 'b', 'c']) for (let i = 0; i < 4; i++) add(p, 'tend_' + tn + i, [40 * sl, 0, 0]);
	p['#root'] = [1 + 0.35 * m, Math.max(0.05, 1 - 0.95 * m), 1 + 0.35 * m];
	return p;
});

// ------------------------------------------------------------------------------------------------ write
const r3 = (v) => Math.round(v * 1000) / 1000;
module.exports = function ({ ROOT, B, bones }) {
	const out = { format_version: '1.8.0', animations: {} };
	for (const [name, c] of Object.entries(CLIPS)) {
		const dt = 0.05;
		const n = Math.round(c.length / dt);
		const tracks = {};
		for (let i = 0; i <= n; i++) {
			const t = i * dt;
			const pose = c.fn(c.loop && i === n ? 0 : t);
			for (const [k, v] of Object.entries(pose)) {
				const kind = k[0] === '@' ? 'position' : k[0] === '#' ? 'scale' : 'rotation';
				const bn = kind === 'rotation' ? k : k.slice(1);
				if (!B[bn]) throw new Error('no bone ' + bn + ' in ' + name);
				(tracks[bn] = tracks[bn] || {})[kind] = (tracks[bn][kind] || {});
				tracks[bn][kind][r3(t).toFixed(2)] = v.map(r3);
			}
		}
		// drop tracks that never move
		for (const [bn, kinds] of Object.entries(tracks)) for (const [kind, frames] of Object.entries(kinds)) {
			const rest = kind === 'scale' ? 1 : 0;
			if (Object.values(frames).every((v) => v.every((x) => Math.abs(x - rest) < 0.01))) delete kinds[kind];
			if (!Object.keys(kinds).length) delete tracks[bn];
		}
		const a = { animation_length: c.length, bones: tracks };
		if (c.loop) a.loop = true;
		else if (name === 'melt' || name === 'leap') a.loop = 'hold_on_last_frame';
		out.animations['animation.symbiote_monster.' + name] = a;
	}
	fs.writeFileSync(path.join(ROOT, 'animations', 'symbiote_monster.animation.json'), JSON.stringify(out));
	console.log('animations:', Object.keys(CLIPS).join(', '));
};
module.exports.CLIPS = CLIPS;
module.exports.previewPoses = [
	['idle', CLIPS.idle.fn(0.8)], ['walk', CLIPS.walk.fn(0.4)], ['run', CLIPS.run.fn(0.22)], ['swipe wind', CLIPS.swipe.fn(0.2)],
	['swipe hit', CLIPS.swipe.fn(0.34)], ['pound up', CLIPS.pound.fn(0.38)], ['pound slam', CLIPS.pound.fn(0.55)], ['leap air', CLIPS.leap.fn(0.5)],
	['roar', CLIPS.roar.fn(0.6)], ['devour reach', CLIPS.devour.fn(0.3)], ['devour mouth', CLIPS.devour.fn(0.8)], ['pull out', CLIPS.pull.fn(0.25)],
	['pull yank', CLIPS.pull.fn(0.55)], ['emerge coil', CLIPS.emerge.fn(0.05)], ['emerge fling', CLIPS.emerge.fn(0.6)], ['melt', CLIPS.melt.fn(1.0)],
];
