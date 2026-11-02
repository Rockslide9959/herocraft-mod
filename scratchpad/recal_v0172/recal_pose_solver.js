// v0.17.2: the Recalibrated Omnitrix's raised-wrist pose (user: "change the position in which he holds his wrist out so that he looks
// at the hologram directly like front facing"): the left forearm straight out in front at chest height, a little inward, the face up so
// the hologram stands upright in front of the head; then the right hand's three slap stops (resting by the face, wound up over it,
// pressing it) solved by search. Model space: px, y DOWN, -z forward; ModelPart rotation = Rz * Ry * Rx (applied to the vector).
// Usage: node scratchpad/recal_v0172/recal_pose_solver.js
'use strict';
const D = Math.PI / 180;
const mul = (A, B) => A.map((r, i) => [0, 1, 2].map(j => r[0] * B[0][j] + r[1] * B[1][j] + r[2] * B[2][j]));
const ap = (M, v) => M.map(r => r[0] * v[0] + r[1] * v[1] + r[2] * v[2]);
const Rx = a => [[1, 0, 0], [0, Math.cos(a), -Math.sin(a)], [0, Math.sin(a), Math.cos(a)]];
const Ry = a => [[Math.cos(a), 0, Math.sin(a)], [0, 1, 0], [-Math.sin(a), 0, Math.cos(a)]];
const Rz = a => [[Math.cos(a), -Math.sin(a), 0], [Math.sin(a), Math.cos(a), 0], [0, 0, 1]];
const zyx = (x, y, z) => mul(Rz(z), mul(Ry(y), Rx(x)));
// decompose R = Rz(c) Ry(b) Rx(a)
function euler(R) {
	const b = Math.asin(-R[2][0]);
	const a = Math.atan2(R[2][1], R[2][2]);
	const c = Math.atan2(R[1][0], R[0][0]);
	return [a, b, c];
}

// ---- the left arm: forward (Rx -90 + pitch), face (+x) rolled up (Rz -90 about the arm), swung in (world yaw)
const PITCH = -6 * D, INWARD = 30 * D;
const M = mul(Ry(INWARD), mul(Rz(-90 * D), Rx(-90 * D - PITCH)));
const left = euler(M);
const LP = [5, 2 + 2, 0]; // the left shoulder pivot (+2 px drop)
const L = 3, CY = 7.1, FACE_X = L + 0.03 + 0.2 + 0.3; // recalibrated face top at rest
const face = ap(M, [FACE_X, CY, 0]).map((v, i) => v + LP[i]);
const normal = ap(M, [1, 0, 0]);
console.log('LEFT', left.map(v => v.toFixed(3)).join(', '), ' face at', face.map(v => v.toFixed(2)).join(','), ' normal', normal.map(v => v.toFixed(2)).join(','));

// ---- the right hand: fingertip (local (-1, 10, 0) from the pivot (-5, 2, 0)) at a target, palm facing the face
const RP = [-5, 2, 0];
function tip(r) { return ap(zyx(r[0], r[1], r[2]), [-1, 10, 0]).map((v, i) => v + RP[i]); }
function palm(r) { return ap(zyx(r[0], r[1], r[2]), [1, 0, 0]); }
function solve(target, palmDir) {
	let best = null, bs = 1e9;
	for (let it = 0; it < 400000; it++) {
		const r = best && it > 50000 ? best.map(v => v + (Math.random() - 0.5) * 0.08) : [(-Math.random() * 3.2), (Math.random() - 0.5) * 3, (Math.random() - 0.5) * 3];
		const t = tip(r), d = Math.hypot(t[0] - target[0], t[1] - target[1], t[2] - target[2]);
		const pd = palm(r);
		const s = d + 2.5 * (1 - (pd[0] * palmDir[0] + pd[1] * palmDir[1] + pd[2] * palmDir[2]));
		if (s < bs) { bs = s; best = r; }
	}
	return { r: best, err: bs };
}
// round 2 (the resting hand covered his face): it rests BESIDE the watch, by the knob on his right and a little below the face, the
// palm toward the watch; it winds up above and in front of the face (clear of his head) and comes straight down on it
const at = d => face.map((v, i) => v + d[i]);
for (const [name, d, palmDir] of [['HAND_ON', [-2.0, 1.0, -2.5], [1, 0, 0]], ['HAND_UP', [-0.3, -4.0, -1.6], [0, 1, 0]], ['HAND_PRESS', [0, -0.9, 0], [0, 1, 0]]]) {
	const s = solve(at(d), palmDir);
	console.log(name, s.r.map(v => v.toFixed(3)).join(', '), ' err', s.err.toFixed(3));
}
