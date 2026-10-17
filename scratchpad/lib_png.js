// v0.16.6 Ladybug: a tiny PNG decode / encode (no Python on this machine): 8-bit RGBA / RGB / grey / palette (+tRNS).
'use strict';
const zlib = require('zlib');

const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };

function encodePng(w, h, px) {
	const st = w * 4; const raw = Buffer.alloc((st + 1) * h);
	for (let y = 0; y < h; y++) { raw[y * (st + 1)] = 0; px.copy(raw, y * (st + 1) + 1, y * st, y * st + st); }
	const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
	return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

/** Returns { w, h, px } with px an RGBA Buffer. */
function decodePng(buf) {
	let p = 8, w = 0, h = 0, depth = 0, type = 0, plte = null, trns = null; const idat = [];
	while (p < buf.length) {
		const len = buf.readUInt32BE(p); const ty = buf.toString('ascii', p + 4, p + 8); const d = buf.subarray(p + 8, p + 8 + len);
		if (ty === 'IHDR') { w = d.readUInt32BE(0); h = d.readUInt32BE(4); depth = d[8]; type = d[9]; if (d[12]) throw new Error('interlaced'); }
		else if (ty === 'PLTE') plte = d; else if (ty === 'tRNS') trns = d; else if (ty === 'IDAT') idat.push(d);
		p += 12 + len;
	}
	const raw = zlib.inflateSync(Buffer.concat(idat));
	const ch = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[type];
	const bitsPP = ch * depth; const bpp = Math.max(1, bitsPP >> 3); const stride = Math.ceil(w * bitsPP / 8);
	const out = Buffer.alloc(stride * h); let prev = Buffer.alloc(stride);
	for (let y = 0; y < h; y++) {
		const f = raw[y * (stride + 1)]; const line = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1)); const cur = Buffer.alloc(stride);
		for (let i = 0; i < stride; i++) {
			const a = i >= bpp ? cur[i - bpp] : 0, b = prev[i], c = i >= bpp ? prev[i - bpp] : 0; let v = line[i];
			if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1;
			else if (f === 4) { const pp = a + b - c, pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
			cur[i] = v & 255;
		}
		cur.copy(out, y * stride); prev = cur;
	}
	const px = Buffer.alloc(w * h * 4);
	const sample = (y, x, k) => { // k-th channel of pixel x, row y
		if (depth === 8) return out[y * stride + x * ch + k];
		const bit = (x * ch + k) * depth; const byte = out[y * stride + (bit >> 3)]; return (byte >> (8 - depth - (bit & 7))) & ((1 << depth) - 1);
	};
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		const o = (y * w + x) * 4;
		if (type === 3) { const i = sample(y, x, 0); px[o] = plte[i * 3]; px[o + 1] = plte[i * 3 + 1]; px[o + 2] = plte[i * 3 + 2]; px[o + 3] = trns && i < trns.length ? trns[i] : 255; }
		else if (type === 6) { for (let k = 0; k < 4; k++) px[o + k] = sample(y, x, k); }
		else if (type === 2) { for (let k = 0; k < 3; k++) px[o + k] = sample(y, x, k); px[o + 3] = 255; }
		else if (type === 0) { const g = sample(y, x, 0) * (255 / ((1 << depth) - 1)); px[o] = px[o + 1] = px[o + 2] = g; px[o + 3] = 255; }
		else if (type === 4) { const g = sample(y, x, 0); px[o] = px[o + 1] = px[o + 2] = g; px[o + 3] = sample(y, x, 1); }
	}
	return { w, h, px };
}

module.exports = { encodePng, decodePng };
