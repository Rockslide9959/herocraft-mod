// Draws a front-view sketch of the Goku SSJ hair silhouette (from the written reference description) on a Steve-sized
// head, as a PNG. Usage: node ssj_reference_sketch.js <out.png> [size]. No dependencies (own rasteriser + PNG writer).
const fs = require('fs');
const zlib = require('zlib');
const out = process.argv[2] || 'ssj_reference_sketch.png';
const N = +(process.argv[3] || 480);
const px = new Uint8Array(N * N * 3);
const U = N / 22, CX = N / 2, BASE = N * 0.93; // units -> pixels; head base line
const X = (x) => CX + x * U, Y = (y) => BASE - y * U;
function fill(c) { for (let i = 0; i < N * N; i++) { px[i * 3] = c[0]; px[i * 3 + 1] = c[1]; px[i * 3 + 2] = c[2]; } }
function inside(poly, x, y) {
	let c = false;
	for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
		const [xi, yi] = poly[i], [xj, yj] = poly[j];
		if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c;
	}
	return c;
}
function segDist(px_, py, ax, ay, bx, by) {
	const dx = bx - ax, dy = by - ay, l = dx * dx + dy * dy;
	let t = l ? ((px_ - ax) * dx + (py - ay) * dy) / l : 0;
	t = Math.max(0, Math.min(1, t));
	return Math.hypot(px_ - ax - t * dx, py - ay - t * dy);
}
function poly(points, col, outline, lw = 2) {
	const P = points.map(([x, y]) => [X(x), Y(y)]);
	let x0 = N, x1 = 0, y0 = N, y1 = 0;
	for (const [x, y] of P) { x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
	for (let y = Math.max(0, Math.floor(y0 - 3)); y < Math.min(N, y1 + 3); y++) for (let x = Math.max(0, Math.floor(x0 - 3)); x < Math.min(N, x1 + 3); x++) {
		const inn = inside(P, x + 0.5, y + 0.5);
		let c = inn ? col : null;
		if (outline) {
			let d = 1e9;
			for (let i = 0, j = P.length - 1; i < P.length; j = i++) d = Math.min(d, segDist(x + 0.5, y + 0.5, P[j][0], P[j][1], P[i][0], P[i][1]));
			if (d < lw && (inn || d < lw * 0.6)) c = outline;
		}
		if (c) { const k = (y * N + x) * 3; px[k] = c[0]; px[k + 1] = c[1]; px[k + 2] = c[2]; }
	}
}
function bez(p, t) {
	const u = 1 - t;
	return [0, 1].map((k) => u * u * u * p[0][k] + 3 * u * u * t * p[1][k] + 3 * u * t * t * p[2][k] + t * t * t * p[3][k]);
}
/** A flame lick: bezier spine, root width w, tapering to a point; returns [whole outline, shadow half]. */
function flame(p, w) {
	const L = [], R = [], C = [];
	const n = 24;
	for (let i = 0; i <= n; i++) {
		const t = i / n, a = bez(p, Math.max(0, t - 0.01)), b = bez(p, Math.min(1, t + 0.01)), c = bez(p, t);
		let tx = b[0] - a[0], ty = b[1] - a[1]; const l = Math.hypot(tx, ty) || 1; tx /= l; ty /= l;
		const hw = (w / 2) * Math.pow(1 - t, 0.8) * (1 + 0.35 * Math.sin(Math.PI * t));
		L.push([c[0] - ty * hw, c[1] + tx * hw]); R.push([c[0] + ty * hw, c[1] - tx * hw]); C.push(c);
	}
	return [L.concat(R.reverse()), C.concat(R.slice().reverse().slice(0)).slice(0)];
}
const GOLD = [255, 218, 69], AMBER = [232, 164, 42], DARK = [120, 70, 10], CREAM = [255, 247, 210], SKIN = [198, 150, 128];
fill([170, 205, 240]);
function spike(p, w, back) {
	const pts = p.map(([x, y]) => [x, y]);
	const [whole] = flame(pts, w);
	poly(whole, back ? AMBER : GOLD, DARK, 2);
	if (!back) {
		// shadow on the right half (light from the viewer's left)
		// the right edge runs tip -> root in the second half of the outline; the spine root -> tip closes it
		const n = whole.length / 2;
		const C = [];
		for (let i = 0; i <= 24; i++) C.push(bez(pts, i / 24));
		const right = C.concat(whole.slice(n + 1));
		const cx = pts[0][0];
		// on the viewer's right side of the face the shadow is the outer half, on the left the inner half: always the right
		poly(right, AMBER, null);
		// a cream ridge just inside the left edge, through the middle of the lock
		const ridge = [];
		for (let i = 4; i <= 17; i++) { const a = whole[i], c = C[i]; ridge.push([a[0] * 0.8 + c[0] * 0.2, a[1] * 0.8 + c[1] * 0.2]); }
		for (let i = 17; i >= 4; i--) { const a = whole[i], c = C[i]; ridge.push([a[0] * 0.55 + c[0] * 0.45, a[1] * 0.55 + c[1] * 0.45]); }
		if (cx !== undefined) poly(ridge, CREAM, null);
		poly(whole, null, DARK, 2);
	}
}
// back layer peeking between the crown spikes
for (const s of [-1, 1]) spike([[s * 1, 7], [s * 1.2, 11], [s * 2.5, 14.5], [s * 3, 15.6]], 2.6, true);
// temple flares, outer fan, inner fan, centre
for (const s of [-1, 1]) spike([[s * 3.8, 5.6], [s * 5.4, 7], [s * 7.2, 8.2], [s * 8.6, 10.6]], 2.3);
for (const s of [-1, 1]) spike([[s * 3.3, 6.6], [s * 4.4, 10], [s * 6.2, 12.2], [s * 7.8, 13.4]], 2.7);
for (const s of [-1, 1]) spike([[s * 2, 7], [s * 2.4, 11.2], [s * 3.6, 14.3], [s * 5.1, 16]], 2.9);
spike([[0, 7], [0, 11.5], [-0.4, 15], [0.3, 17.4]], 3.3);
// scalp cap and face
poly([[-4.3, 8.4], [4.3, 8.4], [4.3, 5.8], [-4.3, 5.8]], AMBER, DARK);
poly([[-4, 7.3], [4, 7.3], [4, 0], [-4, 0]], SKIN, [90, 60, 40]);
poly([[-3, 4], [-1, 4], [-1, 3], [-3, 3]], [255, 255, 255], null);
poly([[1, 4], [3, 4], [3, 3], [1, 3]], [255, 255, 255], null);
poly([[-2, 4], [-1, 4], [-1, 3], [-2, 3]], [60, 70, 160], null);
poly([[1, 4], [2, 4], [2, 3], [1, 3]], [60, 70, 160], null);
poly([[-2, 2], [2, 2], [2, 1.3], [-2, 1.3]], [110, 70, 50], null);
// hairline band
poly([[-4.3, 8.4], [4.3, 8.4], [4.3, 7.2], [-4.3, 7.2]], GOLD, DARK);
// the fringe: hooked locks, the longest front-left
spike([[-3.3, 8.4], [-3.5, 7.8], [-3.2, 6.8], [-2.4, 5.8]], 1.9);
spike([[3.1, 8.4], [3.4, 7.8], [3.2, 7], [2.5, 6.2]], 1.8);
spike([[1, 8.6], [1.1, 7.9], [0.9, 6.8], [0.3, 5.6]], 2.1);
spike([[-1.6, 8.6], [-1.8, 7.8], [-1.4, 6.2], [-0.4, 4.4]], 2.3);
// PNG
const raw = Buffer.alloc((N * 3 + 1) * N);
for (let y = 0; y < N; y++) { raw[y * (N * 3 + 1)] = 0; Buffer.from(px.buffer, y * N * 3, N * 3).copy(raw, y * (N * 3 + 1) + 1); }
const crcT = []; for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; crcT[n] = c >>> 0; }
const crc = (b) => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };
const ih = Buffer.alloc(13); ih.writeUInt32BE(N, 0); ih.writeUInt32BE(N, 4); ih[8] = 8; ih[9] = 2;
fs.writeFileSync(out, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ih), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]));
console.log('wrote', out);
