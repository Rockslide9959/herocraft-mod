// v0.17.5 Symbiote Monster polish: the first-person arm pose as a function of the move and its age (ticks) -- the JS twin of
// SymbioteMonsterFirstPerson.armPose() (keep them in step). Canonical values are for the RIGHT arm; the left mirrors x, yaw and roll
// (and the bone extras' y / z). Units: camera-space blocks, degrees; wrist / curl / clawCurl / spread are bedrock bone degrees.
//   FP='{"x":1.0,...}' overrides the rest pose (tuning).
'use strict';
const S = Math.sin;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const ease = (x) => { x = clamp(x, 0, 1); return x * x * (3 - 2 * x); };
/** Smoothstepped keys: k(t, t0, v0, t1, v1, ...). */
function k(t, ...kv) {
	if (t <= kv[0]) return kv[1];
	for (let i = 0; i + 3 < kv.length; i += 2) {
		if (t <= kv[i + 2]) return kv[i + 1] + (kv[i + 3] - kv[i + 1]) * ease((t - kv[i]) / (kv[i + 2] - kv[i]));
	}
	return kv[kv.length - 1];
}
const FIELDS = ['x', 'y', 'z', 'yaw', 'pitch', 'roll', 'wrist', 'curl', 'clawCurl', 'spread'];
function rest() {
	return Object.assign({ x: 1.0, y: -0.8, z: -1.25, yaw: 24, pitch: 30, roll: -75, wrist: -10, curl: 6, clawCurl: -12, spread: 15, scale: 0.4, claw: 1.45 },
		process.env.FP ? JSON.parse(process.env.FP) : {});
}
const zero = () => ({ x: 0, y: 0, z: 0, yaw: 0, pitch: 0, roll: 0, wrist: 0, curl: 0, clawCurl: 0, spread: 0 });

/** The move's offsets for one arm ({@code right} = the canonical right arm), age {@code t} in ticks. */
function moveOffsets(move, t, right, swing) {
	const d = zero();
	switch (move) {
		case 'swing': { // left click: a quick forward rake with the right claws
			if (!right) break;
			const p = swing;
			d.x = -0.45 * p; d.y = 0.15 * p; d.z = -0.5 * p; d.yaw = 40 * p; d.pitch = 4 * p; d.roll = -40 * p; d.curl = -30 * p; d.clawCurl = -15 * p;
			break;
		}
		case 'swipe': // R: wind out to the right, slash across the screen to the left (the hit lands on tick 6)
			if (right) {
				d.x = k(t, 0, 0, 4, 0.45, 7, -0.75, 11, -1.25, 18, 0);
				d.y = k(t, 0, 0, 4, 0.3, 7, 0.3, 11, 0.15, 18, 0);
				d.z = k(t, 0, 0, 4, 0.05, 7, -0.45, 11, -0.4, 18, 0);
				d.yaw = k(t, 0, 0, 4, -35, 7, 46, 11, 56, 18, 0);
				d.pitch = k(t, 0, 0, 4, 10, 7, -10, 11, -25, 18, 0);
				d.roll = k(t, 0, 0, 4, -20, 7, -45, 11, -55, 18, 0);
				d.curl = k(t, 0, 0, 4, 24, 7, -12, 18, 0);
				d.spread = k(t, 0, 0, 4, 12, 9, 12, 18, 0);
			} else {
				d.x = k(t, 0, 0, 5, 0.12, 9, 0.18, 18, 0);
				d.y = k(t, 0, 0, 5, -0.08, 9, -0.12, 18, 0);
				d.yaw = k(t, 0, 0, 7, -10, 18, 0);
			}
			break;
		case 'pound': { // G: both fists up overhead, then slammed down (impact on tick 10)
			d.x = k(t, 0, 0, 8, -0.32, 10, -0.25, 22, 0);
			d.y = k(t, 0, 0, 8, 0.8, 10, 0.12, 16, 0.1, 22, 0);
			d.z = k(t, 0, 0, 8, -0.15, 10, -0.3, 22, 0);
			d.pitch = k(t, 0, 0, 8, 58, 10, 0, 16, 2, 22, 0);
			d.roll = k(t, 0, 0, 8, 35, 16, 35, 22, 0);
			d.curl = k(t, 0, 0, 6, -60, 16, -60, 22, 0);
			d.clawCurl = k(t, 0, 0, 6, -35, 16, -35, 22, 0);
			d.spread = k(t, 0, 0, 6, -15, 16, -15, 22, 0);
			const quake = t > 10 && t < 15 ? (15 - t) / 5 : 0;
			d.y += 0.05 * quake * S(t * 3.1 + (right ? 0 : 1.3));
			break;
		}
		case 'roar': { // Z: both arms flung out, claws splayed, shaking with the roar (the blast on tick 6)
			const tr = t > 5 && t < 24 ? 1 : 0;
			d.x = k(t, 0, 0, 5, 0.55, 22, 0.55, 30, 0) + 0.018 * tr * S(t * 2.7 + (right ? 0 : 2));
			d.y = k(t, 0, 0, 5, 0.3, 22, 0.3, 30, 0) + 0.018 * tr * S(t * 3.3 + (right ? 1 : 0));
			d.z = k(t, 0, 0, 5, -0.25, 22, -0.25, 30, 0);
			d.yaw = k(t, 0, 0, 5, -9, 22, -9, 30, 0);
			d.pitch = k(t, 0, 0, 5, 10, 22, 10, 30, 0);
			d.roll = k(t, 0, 0, 5, -65, 22, -65, 30, 0);
			d.curl = k(t, 0, 0, 5, 30, 22, 30, 30, 0);
			d.clawCurl = k(t, 0, 0, 5, 18, 22, 18, 30, 0);
			d.spread = k(t, 0, 0, 5, 25, 22, 25, 30, 0);
			break;
		}
		case 'devour': // C: both claws lunge in together, seize (tick 6), drag the prey down to the jaws (eaten on tick 18)
			d.x = k(t, 0, 0, 5, -0.48, 8, -0.52, 16, -0.55, 22, -0.5, 30, 0);
			d.y = k(t, 0, 0, 5, 0.14, 8, 0.1, 16, -0.2, 22, -0.22, 30, 0);
			d.z = k(t, 0, 0, 5, -0.55, 8, -0.5, 16, 0.05, 22, 0.05, 30, 0);
			d.yaw = k(t, 0, 0, 5, 15, 16, 25, 30, 0);
			d.pitch = k(t, 0, 0, 5, 10, 16, 0, 30, 0);
			d.curl = k(t, 0, 0, 4, 32, 7, -55, 22, -55, 30, 0);
			d.clawCurl = k(t, 0, 0, 4, 15, 7, -30, 22, -30, 30, 0);
			d.spread = k(t, 0, 0, 4, 25, 7, 0, 30, 0);
			break;
		case 'pull': { // V: claws thrust out, then yanked back to the chest (the yank on tick 6)
			const u = right ? t : t - 1;
			d.x = k(u, 0, 0, 4, -0.2, 10, 0.05, 20, 0);
			d.y = k(u, 0, 0, 4, 0.12, 10, -0.1, 20, 0);
			d.z = k(u, 0, 0, 4, -0.6, 6, -0.6, 10, 0.12, 14, 0.12, 20, 0);
			d.pitch = k(u, 0, 0, 4, 15, 10, 0, 20, 0);
			d.curl = k(u, 0, 0, 3, 30, 6, -55, 14, -55, 20, 0);
			d.clawCurl = k(u, 0, 0, 3, 15, 6, -30, 14, -30, 20, 0);
			d.spread = k(u, 0, 0, 3, 25, 6, 0, 20, 0);
			break;
		}
		case 'leap': // X: crouch (launched on tick 3), then claws up and forward, held in the air
			d.y = k(t, 0, 0, 3, -0.28, 7, 0.32);
			d.pitch = k(t, 0, 0, 3, -15, 7, 42);
			d.x = k(t, 0, 0, 7, -0.12);
			d.z = k(t, 0, 0, 7, -0.2);
			d.curl = k(t, 0, 0, 7, 28);
			d.spread = k(t, 0, 0, 7, 22);
			break;
		case 'land': // the landing smash
			d.y = k(t, 0, -0.2, 4, -0.2, 14, 0);
			d.pitch = k(t, 0, -12, 4, -12, 14, 0);
			d.x = k(t, 0, 0.12, 14, 0);
			d.curl = k(t, 0, -50, 4, -50, 14, 0);
			d.clawCurl = k(t, 0, -25, 14, 0);
			break;
		default:
	}
	return d;
}

/** Rest + idle breath + the move, both arms; {@code emerge} 0..1 raises them into view after the burst. */
exports.pose = function (move, t, time, emerge, swing) {
	const out = {};
	for (const right of [true, false]) {
		const a = rest();
		const ph = right ? 0 : 1.7;
		a.y += 0.025 * S(time * 0.1 + ph) - (1 - (emerge == null ? 1 : emerge)) * 1.2;
		a.pitch += 2 * S(time * 0.1 + ph);
		a.roll += 3 * S(time * 0.07 + ph);
		a.curl += 5 * S(time * 0.13 + ph);
		const d = moveOffsets(move, t, right, swing == null ? Math.sin(Math.sqrt(clamp(t / 6, 0, 1)) * Math.PI) : swing);
		for (const f of FIELDS) a[f] += d[f];
		a.wrist = [a.wrist, 0, 0];
		out[right ? 'r' : 'l'] = a;
	}
	return out;
};
