// v0.16.4: convert a slim-arm (3 px) player skin to the wide (4 px) layout -- each 3-wide face gets its middle column doubled.
const p = require('./pnglib.js');
const [, , inp, out] = process.argv;
const r = p.read(inp), src = r.px, dst = Buffer.from(src);
const get = (x, y) => src.slice((y * 64 + x) * 4, (y * 64 + x) * 4 + 4);
const put = (x, y, c) => c.copy(dst, (y * 64 + x) * 4);
const widen = [0, 1, 1, 2];
for (const [u, v] of [[40, 16], [40, 32], [32, 48], [48, 48]]) {
	const face = (ox, oy, w, h) => { const a = []; for (let y = 0; y < h; y++) { a.push([]); for (let x = 0; x < w; x++) a[y].push(get(ox + x, oy + y)); } return a; };
	const top = face(u + 4, v, 3, 4), bottom = face(u + 7, v, 3, 4), right = face(u, v + 4, 4, 12),
		front = face(u + 4, v + 4, 3, 12), left = face(u + 7, v + 4, 4, 12), back = face(u + 11, v + 4, 3, 12);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(u + x, v + y, Buffer.alloc(4)); // clear the box area
	const wide = (f, ox, oy, h) => { for (let y = 0; y < h; y++) for (let x = 0; x < 4; x++) put(ox + x, oy + y, f[y][widen[x]]); };
	const copy = (f, ox, oy, h) => { for (let y = 0; y < h; y++) for (let x = 0; x < 4; x++) put(ox + x, oy + y, f[y][x]); };
	wide(top, u + 4, v, 4); wide(bottom, u + 8, v, 4);
	copy(right, u, v + 4, 12); wide(front, u + 4, v + 4, 12); copy(left, u + 8, v + 4, 12); wide(back, u + 12, v + 4, 12);
}
p.write(out, 64, 64, dst);
console.log('ok');
