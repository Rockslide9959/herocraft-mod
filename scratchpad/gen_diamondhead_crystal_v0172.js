// v0.17.2 Diamondhead revamp: the Petrosapien Crystal block texture -- a faceted green gem (16x16), animated shimmer.
// Usage: node scratchpad/gen_diamondhead_crystal_v0172.js <out.png> [preview.png]
// Writes the 16 x (16*FRAMES) strip (+ .mcmeta beside it) and, optionally, an 8x-scaled 3x3 tiled preview of frame 0.
const fs = require('fs');
const zlib = require('zlib');

const FRAMES = 8;
const N = 16;

function crc32(buf) {
	let c, crc = 0xffffffff;
	for (let n = 0; n < buf.length; n++) {
		c = (crc ^ buf[n]) & 0xff;
		for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
		crc = (crc >>> 8) ^ c;
	}
	return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
	const len = Buffer.alloc(4);
	len.writeUInt32BE(data.length);
	const td = Buffer.concat([Buffer.from(type), data]);
	const crc = Buffer.alloc(4);
	crc.writeUInt32BE(crc32(td));
	return Buffer.concat([len, td, crc]);
}

function png(w, h, rgba) {
	const raw = Buffer.alloc((w * 4 + 1) * h);
	for (let y = 0; y < h; y++) {
		raw[y * (w * 4 + 1)] = 0;
		rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
	}
	const ihdr = Buffer.alloc(13);
	ihdr.writeUInt32BE(w, 0);
	ihdr.writeUInt32BE(h, 4);
	ihdr[8] = 8;
	ihdr[9] = 6;
	return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)),
		chunk('IEND', Buffer.alloc(0))]);
}

// the facets: seeds of a Voronoi split (wrapping, so the block tiles), each with a tilt that sets how it catches the light
const SEEDS = [
	[3.0, 3.5, 0.95], [10.5, 2.0, 0.55], [14.0, 8.5, 0.25], [7.5, 8.0, 0.75], [2.0, 11.0, 0.35],
	[11.0, 13.0, 0.6], [6.0, 14.5, 0.15], [13.5, 4.5, 0.85], [4.5, 7.0, 0.45],
];
// sparkles: [x, y, phase]
const GLINTS = [[3, 4, 0], [11, 12, 3], [8, 7, 5], [13, 2, 6]];

const lerp = (a, b, t) => a + (b - a) * t;
const mix = (c1, c2, t) => [lerp(c1[0], c2[0], t), lerp(c1[1], c2[1], t), lerp(c1[2], c2[2], t)];
const DEEP = [8, 70, 52], BASE = [34, 168, 120], LIGHT = [110, 236, 190], HI = [228, 255, 246];

function dist(x, y, sx, sy) {
	let dx = Math.abs(x - sx), dy = Math.abs(y - sy);
	dx = Math.min(dx, N - dx);
	dy = Math.min(dy, N - dy);
	return Math.sqrt(dx * dx + dy * dy);
}

function frame(f) {
	const out = Buffer.alloc(N * N * 4);
	for (let y = 0; y < N; y++) {
		for (let x = 0; x < N; x++) {
			const px = x + 0.5, py = y + 0.5;
			let best = 1e9, second = 1e9, bi = 0;
			SEEDS.forEach((s, i) => {
				const d = dist(px, py, s[0], s[1]);
				if (d < best) {
					second = best;
					best = d;
					bi = i;
				} else if (d < second) {
					second = d;
				}
			});
			const tilt = SEEDS[bi][2];
			// inside a facet: a gentle gradient toward its light side (up-left), its depth by its tilt
			const s = SEEDS[bi];
			const grad = Math.max(0, Math.min(1, 0.5 + ((s[0] - px) + (s[1] - py)) * 0.07));
			let c = tilt < 0.5 ? mix(DEEP, BASE, 0.25 + tilt * 1.5) : mix(BASE, LIGHT, (tilt - 0.5) * 1.6);
			c = mix(c, LIGHT, 0.18 * grad);
			let a = 176 + tilt * 40;
			// the edges between facets: a bright ridge on the lit facet's side, a dark seam beside it
			const edge = second - best;
			if (edge < 0.5) {
				c = mix(c, tilt > 0.5 ? HI : LIGHT, 0.85 - edge);
				a = 228;
			} else if (edge < 1.0 && tilt < 0.6) {
				c = mix(c, DEEP, 0.45);
				a = Math.max(a, 200);
			}
			// the shimmer: a band of light sliding diagonally over the block, once a cycle
			const band = ((px + py) / (2 * N) - f / FRAMES + 1) % 1;
			const shine = Math.max(0, 1 - Math.abs(band - 0.5) * 12);
			c = mix(c, HI, shine * 0.3);
			a = Math.min(255, a + shine * 30);
			// a block outline: lit top and left, shadowed bottom and right
			if (x === 0 || y === 0) {
				c = mix(c, LIGHT, 0.5);
				a = 236;
			} else if (x === N - 1 || y === N - 1) {
				c = mix(c, DEEP, 0.55);
				a = 236;
			}
			const o = (y * N + x) * 4;
			out[o] = Math.round(c[0]);
			out[o + 1] = Math.round(c[1]);
			out[o + 2] = Math.round(c[2]);
			out[o + 3] = Math.round(a);
		}
	}
	// the inner glints: little four-point stars that flare in turn
	for (const [gx, gy, ph] of GLINTS) {
		const k = Math.max(0, Math.cos(((f - ph) / FRAMES) * Math.PI * 2));
		if (k < 0.05) continue;
		const put = (x, y, w) => {
			if (x < 1 || y < 1 || x > N - 2 || y > N - 2) return;
			const o = (y * N + x) * 4;
			const c = mix([out[o], out[o + 1], out[o + 2]], HI, w);
			out[o] = c[0];
			out[o + 1] = c[1];
			out[o + 2] = c[2];
			out[o + 3] = Math.max(out[o + 3], Math.round(200 + 55 * w));
		};
		put(gx, gy, k);
		put(gx - 1, gy, k * 0.55);
		put(gx + 1, gy, k * 0.55);
		put(gx, gy - 1, k * 0.55);
		put(gx, gy + 1, k * 0.55);
	}
	return out;
}

const strip = Buffer.concat(Array.from({ length: FRAMES }, (_, f) => frame(f)));
const outPath = process.argv[2];
fs.writeFileSync(outPath, png(N, N * FRAMES, strip));
fs.writeFileSync(outPath + '.mcmeta', JSON.stringify({ animation: { frametime: 4, interpolate: true } }, null, 2) + '\n');
if (process.argv[3]) {
	// 3x3 tiles of frame 0, 8x, on a mid-grey checker (to judge the translucency)
	const S = 8, W = N * 3 * S;
	const f0 = frame(0);
	const img = Buffer.alloc(W * W * 4);
	for (let y = 0; y < W; y++) {
		for (let x = 0; x < W; x++) {
			const tx = Math.floor(x / S) % N, ty = Math.floor(y / S) % N;
			const o = (ty * N + tx) * 4;
			const bg = ((x >> 4) + (y >> 4)) % 2 ? 120 : 80;
			const al = f0[o + 3] / 255;
			const p = (y * W + x) * 4;
			for (let k = 0; k < 3; k++) img[p + k] = Math.round(f0[o + k] * al + bg * (1 - al));
			img[p + 3] = 255;
		}
	}
	fs.writeFileSync(process.argv[3], png(W, W, img));
}
console.log('wrote', outPath);
