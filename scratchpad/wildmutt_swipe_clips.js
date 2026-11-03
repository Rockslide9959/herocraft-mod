// v0.17.2: Wildmutt's Paw Swipe combo clips -- paw_swipe_r, paw_swipe_l, double_rake -- smoothstepped key poses baked at 0.02 s
// (the same density as the other baked clips). Run from the repo root: node <this> (rewrites the animation json in place).
const fs = require('fs');
const F = 'src/main/resources/assets/projecthero/animations/omnitrix/wildmutt.animation.json';
let raw = fs.readFileSync(F, 'utf8');
const CR = raw.includes('\r\n');
const d = JSON.parse(raw);
const BONES = Object.keys(d.animations['animation.projecthero.wildmutt.hunt'].bones);
const P = 'animation.projecthero.wildmutt.';

// keys: { bone: { rotation: [[t,[x,y,z]],...], position: [...] } }
function bake(len, keys) {
	const bones = {};
	for (const b of BONES) {
		const chans = keys[b] || {};
		const out = {};
		for (const ch of ['rotation', 'position']) {
			let k = chans[ch];
			if (!k && ch === 'position') continue;
			if (!k) k = [];
			k = [[0, [0, 0, 0]], ...k, [len, [0, 0, 0]]];
			const frames = {};
			for (let i = 0; i <= Math.round(len / 0.02); i++) {
				const t = +(i * 0.02).toFixed(2);
				let a = k[0], z = k[k.length - 1];
				for (let j = 0; j + 1 < k.length; j++) {
					if (t >= k[j][0] && t <= k[j + 1][0]) { a = k[j]; z = k[j + 1]; break; }
				}
				const span = Math.max(1e-6, z[0] - a[0]);
				let u = Math.min(1, Math.max(0, (t - a[0]) / span));
				u = u * u * (3 - 2 * u);
				frames[t === 0 ? '0.0' : String(t)] =a[1].map((v, n) => +(v + (z[1][n] - v) * u).toFixed(3));
			}
			out[ch] = frames;
		}
		bones[b] = out;
	}
	return { animation_length: len, bones };
}

// the right paw swipe: a quick lift and cock of the right arm, the chest twisting into it, then a sweep across and down
const R = {
	root: { rotation: [[0.08, [-8, 0, 0]], [0.16, [4, 0, 0]], [0.26, [2, 0, 0]]], position: [[0.08, [0, 1.0, 0]], [0.16, [0, -0.4, -0.9]], [0.26, [0, -0.3, -0.6]]] },
	chest: { rotation: [[0.08, [-6, 16, 4]], [0.16, [6, -20, -4]], [0.26, [4, -14, -2]]] },
	neck: { rotation: [[0.08, [-4, -6, 0]], [0.16, [8, 10, 0]], [0.26, [6, 6, 0]]] },
	head: { rotation: [[0.08, [-4, -4, 0]], [0.16, [4, 6, 0]]] },
	jaw: { rotation: [[0.08, [6, 0, 0]], [0.16, [18, 0, 0]], [0.26, [10, 0, 0]]] },
	quills_f: { rotation: [[0.16, [-10, 0, 0]]] },
	arm_r: { rotation: [[0.08, [-118, 0, 32]], [0.16, [-28, 0, -34]], [0.26, [-18, 0, -24]]] },
	forearm_r: { rotation: [[0.08, [-28, 0, 0]], [0.16, [26, 0, 0]], [0.26, [16, 0, 0]]] },
	paw_r: { rotation: [[0.08, [18, 0, 0]], [0.16, [46, 0, 0]], [0.26, [30, 0, 0]]] },
	arm_l: { rotation: [[0.08, [6, 0, 4]], [0.16, [12, 0, 6]], [0.26, [8, 0, 4]]] },
	thigh_r: { rotation: [[0.08, [-6, 0, 0]], [0.16, [6, 0, 0]]] },
	thigh_l: { rotation: [[0.08, [-4, 0, 0]], [0.16, [8, 0, 0]]] },
};

// mirror: swap _r / _l, negate y and z rotation and x position
function mirror(k) {
	const out = {};
	for (const [b, chans] of Object.entries(k)) {
		const nb = b.endsWith('_r') ? b.slice(0, -2) + '_l' : b.endsWith('_l') ? b.slice(0, -2) + '_r' : b;
		out[nb] = {};
		for (const [ch, ks] of Object.entries(chans)) {
			out[nb][ch] = ks.map(([t, v]) => [t, ch === 'rotation' ? [v[0], -v[1], -v[2]] : [-v[0], v[1], v[2]]]);
		}
	}
	return out;
}

// the two-paw rake: rear up with both arms high, then drive both paws down and through (the strike at ~0.26 s = tick 5)
const RAKE = {
	root: { rotation: [[0.18, [-26, 0, 0]], [0.27, [10, 0, 0]], [0.4, [8, 0, 0]]], position: [[0.18, [0, 3.9, 0.9]], [0.27, [0, -1.6, -3.2]], [0.4, [0, -1.4, -2.8]]] },
	chest: { rotation: [[0.18, [-6, 0, 0]], [0.27, [10, 0, 0]], [0.4, [8, 0, 0]]] },
	neck: { rotation: [[0.18, [-10, 0, 0]], [0.27, [16, 0, 0]], [0.4, [12, 0, 0]]] },
	head: { rotation: [[0.18, [-6, 0, 0]], [0.27, [8, 0, 0]]] },
	jaw: { rotation: [[0.18, [24, 0, 0]], [0.27, [14, 0, 0]], [0.4, [8, 0, 0]]] },
	quills_f: { rotation: [[0.18, [-14, 0, 0]], [0.27, [-18, 0, 0]]], },
	quills_b: { rotation: [[0.18, [-10, 0, 0]], [0.27, [-16, 0, 0]]] },
	arm_r: { rotation: [[0.18, [-135, 0, 22]], [0.27, [-30, 0, -16]], [0.4, [-34, 0, -10]]] },
	forearm_r: { rotation: [[0.18, [-30, 0, 0]], [0.27, [24, 0, 0]], [0.4, [14, 0, 0]]] },
	paw_r: { rotation: [[0.18, [20, 0, 0]], [0.27, [46, 0, 0]], [0.4, [36, 0, 0]]] },
	arm_l: { rotation: [[0.18, [-135, 0, -22]], [0.27, [-30, 0, 16]], [0.4, [-34, 0, 10]]] },
	forearm_l: { rotation: [[0.18, [-30, 0, 0]], [0.27, [24, 0, 0]], [0.4, [14, 0, 0]]] },
	paw_l: { rotation: [[0.18, [20, 0, 0]], [0.27, [46, 0, 0]], [0.4, [36, 0, 0]]] },
	thigh_r: { rotation: [[0.18, [-18, 0, 0]], [0.27, [8, 0, 0]], [0.4, [6, 0, 0]]] },
	thigh_l: { rotation: [[0.18, [-18, 0, 0]], [0.27, [8, 0, 0]], [0.4, [6, 0, 0]]] },
	shin_r: { rotation: [[0.18, [10, 0, 0]], [0.27, [-6, 0, 0]]] },
	shin_l: { rotation: [[0.18, [10, 0, 0]], [0.27, [-6, 0, 0]]] },
};

d.animations[P + 'paw_swipe_r'] = bake(0.45, R);
d.animations[P + 'paw_swipe_l'] = bake(0.45, mirror(R));
d.animations[P + 'double_rake'] = bake(0.75, RAKE);
let out = JSON.stringify(d, null, '\t');
if (!raw.includes('\n\t')) out = JSON.stringify(d); // keep the file's own style
fs.writeFileSync(F, CR ? out.replace(/\n/g, '\r\n') : out);
console.log('ok', Object.keys(d.animations).length);
