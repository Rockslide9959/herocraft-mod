// Minimal PNG decode/encode (RGBA8) + nearest-neighbour upscale, no dependencies.
const fs = require('fs');
const zlib = require('zlib');

function decode(p) {
	const b = fs.readFileSync(p); let o = 8; const cs = [];
	while (o < b.length) { const len = b.readUInt32BE(o); const t = b.toString('ascii', o + 4, o + 8); cs.push({ t, d: b.slice(o + 8, o + 8 + len) }); o += 12 + len; }
	const ih = cs.find(c => c.t === 'IHDR').d; const w = ih.readUInt32BE(0), h = ih.readUInt32BE(4), depth = ih[8], ct = ih[9];
	const plte = cs.find(c => c.t === 'PLTE'); const trns = cs.find(c => c.t === 'tRNS');
	const raw = zlib.inflateSync(Buffer.concat(cs.filter(c => c.t === 'IDAT').map(c => c.d)));
	const ch = ct === 6 ? 4 : ct === 2 ? 3 : ct === 4 ? 2 : 1;
	const bpp = Math.max(1, (ch * depth) >> 3); const st = Math.ceil(w * ch * depth / 8);
	const px = Buffer.alloc(w * h * 4); let prev = Buffer.alloc(st);
	for (let y = 0; y < h; y++) {
		const f = raw[y * (st + 1)]; const line = raw.slice(y * (st + 1) + 1, y * (st + 1) + 1 + st); const cur = Buffer.alloc(st);
		for (let x = 0; x < st; x++) {
			const A = x >= bpp ? cur[x - bpp] : 0, B = prev[x], C = x >= bpp ? prev[x - bpp] : 0; let v = line[x];
			if (f === 1) v = (v + A) & 255; else if (f === 2) v = (v + B) & 255; else if (f === 3) v = (v + ((A + B) >> 1)) & 255;
			else if (f === 4) { const pp = A + B - C; const pa = Math.abs(pp - A), pb = Math.abs(pp - B), pc = Math.abs(pp - C); v = (v + (pa <= pb && pa <= pc ? A : pb <= pc ? B : C)) & 255; }
			cur[x] = v;
		}
		for (let x = 0; x < w; x++) {
			const i = (y * w + x) * 4;
			if (ct === 3) {
				const idx = depth === 8 ? cur[x] : (cur[(x * depth) >> 3] >> (8 - depth - ((x * depth) & 7))) & ((1 << depth) - 1);
				px[i] = plte.d[idx * 3]; px[i + 1] = plte.d[idx * 3 + 1]; px[i + 2] = plte.d[idx * 3 + 2];
				px[i + 3] = trns && idx < trns.d.length ? trns.d[idx] : 255;
			} else if (ct === 6) { cur.copy(px, i, x * 4, x * 4 + 4); }
			else if (ct === 2) { px[i] = cur[x * 3]; px[i + 1] = cur[x * 3 + 1]; px[i + 2] = cur[x * 3 + 2]; px[i + 3] = 255; }
			else if (ct === 4) { px[i] = px[i + 1] = px[i + 2] = cur[x * 2]; px[i + 3] = cur[x * 2 + 1]; }
			else { px[i] = px[i + 1] = px[i + 2] = cur[x]; px[i + 3] = 255; }
		}
		prev = cur;
	}
	return { w, h, px };
}

const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function encode(w, h, px) {
	const st = w * 4; const raw = Buffer.alloc((st + 1) * h);
	for (let y = 0; y < h; y++) { raw[y * (st + 1)] = 0; px.copy(raw, y * (st + 1) + 1, y * st, y * st + st); }
	const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
	return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

function upscale(img, k, bg) {
	const w = img.w * k, h = img.h * k; const px = Buffer.alloc(w * h * 4);
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		const s = ((Math.floor(y / k) * img.w) + Math.floor(x / k)) * 4, d = (y * w + x) * 4;
		const a = img.px[s + 3] / 255;
		const checker = bg && (((x >> 3) + (y >> 3)) & 1) ? 200 : 160;
		for (let c = 0; c < 3; c++) px[d + c] = bg ? Math.round(img.px[s + c] * a + checker * (1 - a)) : img.px[s + c];
		px[d + 3] = bg ? 255 : img.px[s + 3];
	}
	return { w, h, px };
}

module.exports = { decode, encode, upscale };

if (require.main === module) {
	const [, , src, out, k] = process.argv;
	const u = upscale(decode(src), Number(k || 8), true);
	fs.writeFileSync(out, encode(u.w, u.h, u.px));
}
