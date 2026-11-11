// Redraws the user's aura sketch (from the written description, v0.17.3): a stick figure inside a tall gold jagged-flame
// outline -- low and a bit wider than the body at the feet, 3-4 sawtooth lobes up each side with tips pointing up and out,
// curving in to a jagged crown ~1.5 head-heights above the head. Usage: node aura_sketch_recreation.js <out.png> [w] [h].
// No dependencies (own rasteriser + PNG writer).
const fs = require('fs');
const zlib = require('zlib');
const out = process.argv[2] || 'aura_sketch.png';
const W = +(process.argv[3] || 480), H = +(process.argv[4] || 640);
const px = new Uint8Array(W * H * 3).fill(250);
const U = H / 13, CX = W / 2, BASE = H * 0.93; // units -> pixels; ground line
const X = (x) => CX + x * U, Y = (y) => BASE - y * U;
function segDist(x, y, ax, ay, bx, by) {
	const dx = bx - ax, dy = by - ay, l = dx * dx + dy * dy;
	let t = l ? ((x - ax) * dx + (y - ay) * dy) / l : 0;
	t = Math.max(0, Math.min(1, t));
	return Math.hypot(x - ax - t * dx, y - ay - t * dy);
}
function blend(x, y, c, a) {
	if (x < 0 || y < 0 || x >= W || y >= H || a <= 0) return;
	const i = (y * W + x) * 3;
	for (let k = 0; k < 3; k++) px[i + k] = Math.round(px[i + k] * (1 - a) + c[k] * a);
}
function line(pts, col, lw) {
	const P = pts.map(([x, y]) => [X(x), Y(y)]);
	for (let s = 0; s < P.length - 1; s++) {
		const [ax, ay] = P[s], [bx, by] = P[s + 1];
		for (let y = Math.floor(Math.min(ay, by) - lw - 2); y <= Math.max(ay, by) + lw + 2; y++)
			for (let x = Math.floor(Math.min(ax, bx) - lw - 2); x <= Math.max(ax, bx) + lw + 2; x++) {
				const d = segDist(x + 0.5, y + 0.5, ax, ay, bx, by);
				blend(x, y, col, Math.max(0, Math.min(1, lw / 2 + 0.5 - d)));
			}
	}
}
function circle(cx, cy, r, col, lw) {
	const pts = [];
	for (let i = 0; i <= 48; i++) pts.push([cx + r * Math.cos(i / 48 * 2 * Math.PI), cy + r * Math.sin(i / 48 * 2 * Math.PI)]);
	line(pts, col, lw);
}
const INK = [40, 40, 40], GOLD = [222, 168, 20];
// stick figure: feet at 0, head top at 8 (head ~1.7 tall)
circle(0, 7.15, 0.85, INK, 3);
line([[0, 6.3], [0, 3.2]], INK, 3);
line([[-1.6, 4.4], [0, 5.8], [1.6, 4.4]], INK, 3);
line([[-1.1, 0], [0, 3.2], [1.1, 0]], INK, 3);
// the aura: up the left side (notch, tip, notch, ...), the crown, down the right side
const left = [[-2.1, 0], [-3.0, 2.0], [-2.4, 1.6], [-3.7, 4.2], [-2.9, 3.7], [-4.0, 6.6], [-3.0, 6.0], [-3.3, 8.8], [-2.3, 8.1]];
const crown = [[-1.6, 10.6], [-0.9, 9.6], [0.1, 11.2], [0.9, 9.7], [1.7, 10.5]];
const right = [[2.2, 0], [3.1, 2.2], [2.5, 1.8], [3.8, 4.5], [3.0, 3.9], [3.9, 6.8], [2.9, 6.2], [3.2, 8.9], [2.3, 8.2]];
line([...left, ...crown, ...right.slice().reverse()], GOLD, 4);
const raw = Buffer.alloc((W * 3 + 1) * H);
for (let y = 0; y < H; y++) { raw[y * (W * 3 + 1)] = 0; Buffer.from(px.buffer, y * W * 3, W * 3).copy(raw, y * (W * 3 + 1) + 1); }
const crcT = []; for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; crcT[n] = c >>> 0; }
const crc = (b) => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };
const ih = Buffer.alloc(13); ih.writeUInt32BE(W, 0); ih.writeUInt32BE(H, 4); ih[8] = 8; ih[9] = 2;
fs.writeFileSync(out, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ih), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]));
console.log('wrote', out);
