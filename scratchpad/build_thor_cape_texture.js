// v0.14.16: Thor's crimson cape texture (64x48, same layout as superman_cape.png / MoonKnightCapeLayer.drawCape):
// left half = the outside of the cape, right half = the darker inside lining. Rows 0-1 are also used by the collar.
// Run: node scratchpad/build_thor_cape_texture.js
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const W = 64, H = 48;
const out = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'entity', 'thor_cape.png');

const CRC_TABLE = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = (buf) => { let c = 0xffffffff; for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (type, data) => { const len = Buffer.alloc(4); len.writeUInt32BE(data.length, 0); const td = Buffer.concat([Buffer.from(type, 'ascii'), data]); const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td), 0); return Buffer.concat([len, td, crc]); };
function encode(w, h, data) {
	const stride = w * 4;
	const raw = Buffer.alloc((stride + 1) * h);
	for (let y = 0; y < h; y++) { raw[y * (stride + 1)] = 0; data.copy(raw, y * (stride + 1) + 1, y * stride, y * stride + stride); }
	const ihdr = Buffer.alloc(13);
	ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
	return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr),
		chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

// deterministic texel noise
function hash(x, y) {
	let h = (x * 374761393 + y * 668265263) >>> 0;
	h = ((h ^ (h >>> 13)) * 1274126177) >>> 0;
	return ((h ^ (h >>> 16)) & 0xffff) / 0xffff;
}

const OUTSIDE = [158, 16, 24];  // Thor's classic crimson
const LINING = [92, 8, 14];     // the darker inside
const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));

const data = Buffer.alloc(W * H * 4);
for (let y = 0; y < H; y++) {
	for (let x = 0; x < W; x++) {
		const inside = x >= 32;
		const lx = inside ? x - 32 : x;
		const base = inside ? LINING : OUTSIDE;
		// soft vertical pleats that widen toward the hem
		const t = y / (H - 1);
		const pleat = Math.sin((lx + 0.5) / 32 * Math.PI * (5 + 1.5 * t)) * (0.07 + 0.06 * t);
		// darker at the shoulders (in the shadow of the collar) and a touch darker at the bottom
		let shade = 1.0 + pleat - 0.10 * Math.max(0, 1 - y / 4) - 0.08 * t;
		// a stitched hem along the bottom and the two outer edges
		if (y >= H - 2) shade -= 0.18;
		if (lx === 0 || lx === 31) shade -= 0.12;
		// cloth grain
		shade += (hash(x, y) - 0.5) * 0.06;
		const i = (y * W + x) * 4;
		data[i] = clamp(base[0] * shade);
		data[i + 1] = clamp(base[1] * shade);
		data[i + 2] = clamp(base[2] * shade);
		data[i + 3] = 255;
	}
}
fs.writeFileSync(out, encode(W, H, data));
console.log('wrote ' + out);
