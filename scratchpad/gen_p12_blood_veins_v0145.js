// v0.14.5 Super Regeneration: 8 frames of red pixel veins over the whole body (64x64 player-skin layout).
// Each vein pixel knows how far down its vein it sits (d); frame f lights the pixels where (d - f) mod 8 is
// small, so a bright pulse trickles DOWN every vein as the frames advance, over a dim always-on vein bed.
// Transparent pixels are pure black because the overlay renders additively (RenderType.eyes).
// Run from the repo root:  node scratchpad/gen_p12_blood_veins_v0145.js
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const OUT = path.join(__dirname, '../src/main/resources/assets/projecthero/textures/entity/mutation');
const FRAMES = 8;
const PERIOD = 8;

// ---- minimal PNG writer (RGBA8) ----
const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
function crc32(b) { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }
function chunk(type, data) {
	const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
	const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
	const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
	return Buffer.concat([len, td, crc]);
}
function png(w, h, rgba) {
	const raw = Buffer.alloc((w * 4 + 1) * h);
	for (let y = 0; y < h; y++) rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
	const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
	return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr),
		chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

function rng(seed) { let s = seed >>> 0; return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; }; }
const r = rng(0x12B100D);

// d per pixel (-1 = no vein)
const D = new Int16Array(64 * 64).fill(-1);
function mark(x, y, d) { const i = y * 64 + x; if (D[i] < 0 || d < D[i]) D[i] = d; }

// Side strips wrap each part all the way round, so veins cross face seams; plus the tops of head and shoulders.
// [u0, v0, u1, v1, veins per strip, start offset for d]
const STRIPS = [
	[0, 8, 32, 16, 5, 0],    // head sides (face included)
	[8, 0, 16, 8, 2, 0],     // head top
	[16, 20, 40, 32, 6, 2],  // body sides
	[40, 20, 56, 32, 4, 4],  // right arm sides (wide and slim layouts both fall inside)
	[44, 16, 48, 20, 1, 3],  // right shoulder top
	[32, 52, 48, 64, 4, 4],  // left arm sides
	[36, 48, 40, 52, 1, 3],  // left shoulder top
	[0, 20, 16, 32, 4, 6],   // right leg sides
	[16, 52, 32, 64, 4, 6],  // left leg sides
];

function walk(x, y, d, strip, len, depth) {
	const [u0, v0, u1, v1] = strip;
	for (let s = 0; s < len; s++) {
		if (x < u0) x = u1 - 1; if (x >= u1) x = u0; // wrap round the limb
		if (y >= v1) return;
		mark(x, y, d);
		if (depth < 2 && r() < 0.08) walk(x + (r() < 0.5 ? -1 : 1), y + 1, d + 1, strip, Math.floor(len * 0.5), depth + 1);
		const q = r();
		if (q < 0.72) y += 1; else if (q < 0.86) x -= 1; else x += 1;
		d++;
	}
}

for (const strip of STRIPS) {
	const [u0, v0, u1, v1, count, off] = strip;
	for (let n = 0; n < count; n++) {
		const x = u0 + Math.floor((n + r() * 0.8) * (u1 - u0) / count);
		const y = v0 + (r() < 0.75 ? 0 : Math.floor(r() * (v1 - v0) / 2));
		walk(x, y, off + (y - v0), strip, (v1 - v0) * 2, 0);
	}
}

fs.mkdirSync(OUT, { recursive: true });
for (let f = 0; f < FRAMES; f++) {
	const img = Buffer.alloc(64 * 64 * 4);
	for (let i = 0; i < 64 * 64; i++) {
		const d = D[i];
		if (d < 0) continue;
		const k = ((d - f) % PERIOD + PERIOD) % PERIOD; // 0 = pulse head, trailing off behind it
		let c;
		if (k === 0) c = [255, 70, 50];        // the bright head of the trickle
		else if (k === 1) c = [215, 20, 20];
		else if (k === 2) c = [160, 8, 12];
		else c = [95, 0, 6];                    // the always-on vein bed
		img[i * 4] = c[0]; img[i * 4 + 1] = c[1]; img[i * 4 + 2] = c[2]; img[i * 4 + 3] = 255;
	}
	const file = path.join(OUT, 'p12_blood_veins_' + f + '.png');
	fs.writeFileSync(file, png(64, 64, img));
	console.log('wrote', file);
}
