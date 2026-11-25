// v0.17.7 Stormbreaker clipping audit. Lists every pair of element faces that would z-fight (same facing, on the same
// plane within TOL px, overlapping) or sit closer than NEAR px (too close for the depth buffer at range), skipping
// overlaps buried inside a third element (never drawn). Run from the repo root after the generator:
//   node scratchpad/v0177/stormbreaker_overlap_check.js [model id, default stormbreaker_thrown]
// Element names come from scratchpad/v0177/stormbreaker_names.json (the generator writes it).
const fs = require('fs');
const path = require('path');
const id = process.argv[2] || 'stormbreaker_thrown';
const model = JSON.parse(fs.readFileSync(path.join(__dirname, '../../src/main/resources/assets/projecthero/models/item', id + '.json'), 'utf8'));
let names = [];
try { names = JSON.parse(fs.readFileSync(path.join(__dirname, 'stormbreaker_names.json'), 'utf8')); } catch (e) { /* indices only */ }
const nm = (i) => names[i] || ('#' + i);
const TOL = 0.002, NEAR = +(process.env.NEAR || 0.019);
const rad = (d) => d * Math.PI / 180;
function rotate(e, p, sign) {
	if (!e.rotation) return p;
	const o = e.rotation.origin, a = sign * rad(e.rotation.angle), c = Math.cos(a), s = Math.sin(a);
	const [x, y, z] = [p[0] - o[0], p[1] - o[1], p[2] - o[2]];
	const ax = e.rotation.axis;
	if (ax === 'z') return [o[0] + x * c - y * s, o[1] + x * s + y * c, p[2]];
	if (ax === 'y') return [o[0] + x * c + z * s, p[1], o[2] - x * s + z * c];
	return [p[0], o[1] + y * c - z * s, o[2] + y * s + z * c];
}
function corners(e) {
	const f = e.from, t = e.to;
	const F = {
		north: [[t[0], t[1], f[2]], [f[0], t[1], f[2]], [f[0], f[1], f[2]], [t[0], f[1], f[2]]],
		south: [[f[0], t[1], t[2]], [t[0], t[1], t[2]], [t[0], f[1], t[2]], [f[0], f[1], t[2]]],
		east: [[t[0], t[1], t[2]], [t[0], t[1], f[2]], [t[0], f[1], f[2]], [t[0], f[1], t[2]]],
		west: [[f[0], t[1], f[2]], [f[0], t[1], t[2]], [f[0], f[1], t[2]], [f[0], f[1], f[2]]],
		up: [[f[0], t[1], f[2]], [t[0], t[1], f[2]], [t[0], t[1], t[2]], [f[0], t[1], t[2]]],
		down: [[f[0], f[1], t[2]], [t[0], f[1], t[2]], [t[0], f[1], f[2]], [f[0], f[1], f[2]]],
	};
	const out = {};
	for (const k in F) out[k] = F[k].map((p) => rotate(e, p, 1));
	return out;
}
/** Whether p lies inside element e by more than m on every side (so a face there is buried). */
function inside(e, p, m) {
	const q = rotate(e, p, -1);
	for (let k = 0; k < 3; k++) if (q[k] < e.from[k] + m || q[k] > e.to[k] - m) return false;
	return true;
}
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = (a) => { const l = Math.hypot(...a); return a.map((v) => v / l); };
function clip(poly, a, b) { // keep the left of a->b
	const out = [];
	const side = (p) => (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]);
	for (let i = 0; i < poly.length; i++) {
		const p = poly[i], q = poly[(i + 1) % poly.length], sp = side(p), sq = side(q);
		if (sp >= 0) out.push(p);
		if ((sp >= 0) !== (sq >= 0)) { const k = sp / (sp - sq); out.push([p[0] + (q[0] - p[0]) * k, p[1] + (q[1] - p[1]) * k]); }
	}
	return out;
}
const area = (p) => { let s = 0; for (let i = 0; i < p.length; i++) { const a = p[i], b = p[(i + 1) % p.length]; s += a[0] * b[1] - b[0] * a[1]; } return s / 2; };
const ccw = (p) => (area(p) < 0 ? p.slice().reverse() : p);
function overlapPoly(A, B) {
	let poly = ccw(A);
	const cb = ccw(B);
	for (let i = 0; i < cb.length && poly.length; i++) poly = clip(poly, cb[i], cb[(i + 1) % cb.length]);
	return poly;
}
const faces = [];
model.elements.forEach((e, i) => {
	const c = corners(e);
	for (const k in c) {
		const q = c[k];
		const n = norm(cross(sub(q[1], q[0]), sub(q[2], q[1])));
		if (!isFinite(n[0])) continue;
		faces.push({ i, k, q, n, d: dot(n, q[0]), tex: e.faces[k] && e.faces[k].texture });
	}
});
let fights = 0, close = 0, hidden = 0;
for (let a = 0; a < faces.length; a++) for (let b = a + 1; b < faces.length; b++) {
	const A = faces[a], B = faces[b];
	if (A.i === B.i || dot(A.n, B.n) < 0.9999) continue;
	const gap = B.d - A.d;
	if (Math.abs(gap) > NEAR) continue;
	const u = norm(sub(A.q[1], A.q[0])), v = cross(A.n, u);
	const P = (q) => q.map((p) => [dot(p, u), dot(p, v)]);
	const poly = overlapPoly(P(A.q), P(B.q));
	const ar = poly.length >= 3 ? Math.abs(area(poly)) : 0;
	if (ar < 1e-4) continue;
	const cx = poly.reduce((s, p) => s + p[0], 0) / poly.length, cy = poly.reduce((s, p) => s + p[1], 0) / poly.length;
	const samples = [[cx, cy], ...poly.map((p) => [cx + (p[0] - cx) * 0.9, cy + (p[1] - cy) * 0.9])];
	const lift = (s2) => [0, 1, 2].map((k) => u[k] * s2[0] + v[k] * s2[1] + A.n[k] * A.d);
	const buried = samples.every((s2) => model.elements.some((e, j) => j !== A.i && j !== B.i && inside(e, lift(s2), 0.01)));
	if (buried) { hidden++; continue; }
	const tag = Math.abs(gap) <= TOL ? 'FIGHT' : 'CLOSE';
	if (tag === 'FIGHT') fights++; else close++;
	console.log(tag, 'gap', gap.toFixed(4), 'area', ar.toFixed(3), `${nm(A.i)}.${A.k}(${A.tex})`, `${nm(B.i)}.${B.k}(${B.tex})`);
}
console.log('fights', fights, 'close', close, 'buried (harmless)', hidden, 'elements', model.elements.length);
process.exitCode = fights + close > 0 ? 1 : 0;
