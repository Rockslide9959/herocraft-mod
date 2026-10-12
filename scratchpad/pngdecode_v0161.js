// minimal PNG decoder (8-bit / low-bit RGBA / RGB / palette / gray, non-interlaced) for the v0.16.1 asset scripts
const fs = require('fs'), zlib = require('zlib');
function decodePng(file) {
	const b = fs.readFileSync(file); let p = 8, w, h, depth, ct, pal = null, trns = null; const idat = [];
	while (p < b.length) {
		const len = b.readUInt32BE(p), ty = b.toString('ascii', p + 4, p + 8), d = b.slice(p + 8, p + 8 + len); p += 12 + len;
		if (ty === 'IHDR') { w = d.readUInt32BE(0); h = d.readUInt32BE(4); depth = d[8]; ct = d[9]; }
		else if (ty === 'PLTE') pal = d; else if (ty === 'tRNS') trns = d; else if (ty === 'IDAT') idat.push(d);
	}
	const raw = zlib.inflateSync(Buffer.concat(idat));
	const ch = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[ct]; const bpp = Math.max(1, ch * depth / 8); const st = Math.ceil(w * ch * depth / 8);
	const out = Buffer.alloc(w * h * 4); let prev = Buffer.alloc(st);
	for (let y = 0; y < h; y++) {
		const f = raw[y * (st + 1)]; const line = Buffer.from(raw.slice(y * (st + 1) + 1, y * (st + 1) + 1 + st));
		for (let i = 0; i < st; i++) {
			const a = i >= bpp ? line[i - bpp] : 0, up = prev[i], c = i >= bpp ? prev[i - bpp] : 0;
			let v = line[i];
			if (f === 1) v += a; else if (f === 2) v += up; else if (f === 3) v += (a + up) >> 1;
			else if (f === 4) { const pp = a + up - c, pa = Math.abs(pp - a), pb = Math.abs(pp - up), pc = Math.abs(pp - c); v += (pa <= pb && pa <= pc) ? a : pb <= pc ? up : c; }
			line[i] = v & 255;
		}
		for (let x = 0; x < w; x++) {
			let r, g, bl, al = 255;
			const sample = k => { if (depth === 8) return line[x * ch + k]; const bitpos = (x * ch + k) * depth; return (line[bitpos >> 3] >> (8 - depth - (bitpos & 7))) & ((1 << depth) - 1); };
			if (ct === 6) { r = sample(0); g = sample(1); bl = sample(2); al = sample(3); }
			else if (ct === 2) { r = sample(0); g = sample(1); bl = sample(2); }
			else if (ct === 3) { const i = sample(0); r = pal[i * 3]; g = pal[i * 3 + 1]; bl = pal[i * 3 + 2]; al = trns && i < trns.length ? trns[i] : 255; }
			else if (ct === 0) { r = g = bl = sample(0) * (depth === 8 ? 1 : 255 / ((1 << depth) - 1)); }
			else if (ct === 4) { r = g = bl = sample(0); al = sample(1); }
			out.set([r, g, bl, al], (y * w + x) * 4);
		}
		prev = line;
	}
	return { w, h, px: out };
}
module.exports = { decodePng };
