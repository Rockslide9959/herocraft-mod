// Minimal PNG decode/encode (8-bit, non-interlaced; gray/RGB/RGBA/palette) for asset scripts.
const fs = require('fs');
const zlib = require('zlib');
const CRC_TABLE = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = (buf) => { let c = 0xffffffff; for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (type, data) => { const len = Buffer.alloc(4); len.writeUInt32BE(data.length, 0); const td = Buffer.concat([Buffer.from(type, 'ascii'), data]); const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td), 0); return Buffer.concat([len, td, crc]); };

function encode(w, h, data) {
	const stride = w * 4;
	const raw = Buffer.alloc((stride + 1) * h);
	for (let y = 0; y < h; y++) { raw[y * (stride + 1)] = 0; data.copy(raw, y * (stride + 1) + 1, y * stride, y * stride + stride); }
	const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
	const ihdr = Buffer.alloc(13);
	ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
	return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

function decode(buf) {
	let p = 8, w, h, bitDepth, colorType, idat = [], plte = null, trns = null;
	while (p < buf.length) {
		const len = buf.readUInt32BE(p); const type = buf.toString('ascii', p + 4, p + 8); const data = buf.subarray(p + 8, p + 8 + len);
		if (type === 'IHDR') { w = data.readUInt32BE(0); h = data.readUInt32BE(4); bitDepth = data[8]; colorType = data[9]; if (data[12] !== 0) throw new Error('interlaced'); }
		else if (type === 'PLTE') plte = data; else if (type === 'tRNS') trns = data; else if (type === 'IDAT') idat.push(data);
		p += 12 + len;
	}
	if (bitDepth !== 8) throw new Error('bitDepth ' + bitDepth);
	const ch = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[colorType];
	const raw = zlib.inflateSync(Buffer.concat(idat));
	const stride = w * ch; const out = Buffer.alloc(w * h * 4);
	let prev = Buffer.alloc(stride);
	for (let y = 0; y < h; y++) {
		const f = raw[y * (stride + 1)]; const line = Buffer.from(raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1)));
		for (let i = 0; i < stride; i++) {
			const a = i >= ch ? line[i - ch] : 0, b = prev[i], c = i >= ch ? prev[i - ch] : 0;
			let v = line[i];
			if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1;
			else if (f === 4) { const pp = a + b - c, pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c); v += (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c); }
			line[i] = v & 255;
		}
		for (let x = 0; x < w; x++) {
			const o = (y * w + x) * 4;
			if (colorType === 6) { out[o] = line[x * 4]; out[o + 1] = line[x * 4 + 1]; out[o + 2] = line[x * 4 + 2]; out[o + 3] = line[x * 4 + 3]; }
			else if (colorType === 2) { out[o] = line[x * 3]; out[o + 1] = line[x * 3 + 1]; out[o + 2] = line[x * 3 + 2]; out[o + 3] = 255; }
			else if (colorType === 0) { out[o] = out[o + 1] = out[o + 2] = line[x]; out[o + 3] = 255; }
			else if (colorType === 4) { out[o] = out[o + 1] = out[o + 2] = line[x * 2]; out[o + 3] = line[x * 2 + 1]; }
			else { const i = line[x]; out[o] = plte[i * 3]; out[o + 1] = plte[i * 3 + 1]; out[o + 2] = plte[i * 3 + 2]; out[o + 3] = trns && i < trns.length ? trns[i] : 255; }
		}
		prev = line;
	}
	return { w, h, data: out };
}

function upscale(img, k, bg) {
	const w = img.w * k, h = img.h * k; const out = Buffer.alloc(w * h * 4);
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		const s = ((Math.floor(y / k)) * img.w + Math.floor(x / k)) * 4; const o = (y * w + x) * 4;
		const a = img.data[s + 3] / 255; const b = bg || [90, 90, 90];
		out[o] = img.data[s] * a + b[0] * (1 - a); out[o + 1] = img.data[s + 1] * a + b[1] * (1 - a); out[o + 2] = img.data[s + 2] * a + b[2] * (1 - a); out[o + 3] = 255;
	}
	return { w, h, data: out };
}

module.exports = { encode, decode, upscale, read: (f) => decode(fs.readFileSync(f)), write: (f, img) => fs.writeFileSync(f, encode(img.w, img.h, img.data)) };
