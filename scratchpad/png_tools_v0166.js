// v0.16.6 ring work: tiny PNG decode / encode / upscale / crop helpers (Node zlib only -- no Python on this machine).
// CLI: node png_tools_v0166.js up <in.png> <out.png> <factor>
//      node png_tools_v0166.js crop <in.png> <out.png> x y w h [factor]
const fs = require('fs');
const zlib = require('zlib');

const CRC = (() => {
	const t = new Uint32Array(256);
	for (let n = 0; n < 256; n++) {
		let c = n;
		for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
		t[n] = c >>> 0;
	}
	return t;
})();
function crc32(buf) {
	let c = 0xffffffff;
	for (const b of buf) c = CRC[(c ^ b) & 0xff] ^ (c >>> 8);
	return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
	const len = Buffer.alloc(4);
	len.writeUInt32BE(data.length);
	const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
	const crc = Buffer.alloc(4);
	crc.writeUInt32BE(crc32(td));
	return Buffer.concat([len, td, crc]);
}
function encodePng(w, h, px) {
	const st = w * 4;
	const raw = Buffer.alloc((st + 1) * h);
	for (let y = 0; y < h; y++) {
		raw[y * (st + 1)] = 0;
		px.copy(raw, y * (st + 1) + 1, y * st, y * st + st);
	}
	const ihdr = Buffer.alloc(13);
	ihdr.writeUInt32BE(w, 0);
	ihdr.writeUInt32BE(h, 4);
	ihdr[8] = 8;
	ihdr[9] = 6;
	return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr),
		chunk('IDAT', zlib.deflateSync(raw, { level: 6 })), chunk('IEND', Buffer.alloc(0))]);
}
function decodePng(file) {
	const b = fs.readFileSync(file);
	let p = 8, w, h, bd, ct, plte = null, trns = null;
	const idat = [];
	while (p < b.length) {
		const len = b.readUInt32BE(p), type = b.toString('ascii', p + 4, p + 8), d = b.subarray(p + 8, p + 8 + len);
		if (type === 'IHDR') { w = d.readUInt32BE(0); h = d.readUInt32BE(4); bd = d[8]; ct = d[9]; }
		else if (type === 'PLTE') plte = d;
		else if (type === 'tRNS') trns = d;
		else if (type === 'IDAT') idat.push(d);
		p += 12 + len;
	}
	if (bd !== 8) throw new Error('bit depth ' + bd);
	const ch = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[ct];
	const raw = zlib.inflateSync(Buffer.concat(idat));
	const st = w * ch, out = Buffer.alloc(w * h * 4), prev = Buffer.alloc(st), cur = Buffer.alloc(st);
	for (let y = 0; y < h; y++) {
		const f = raw[y * (st + 1)];
		for (let x = 0; x < st; x++) {
			const v = raw[y * (st + 1) + 1 + x], a = x >= ch ? cur[x - ch] : 0, up = prev[x], c = x >= ch ? prev[x - ch] : 0;
			let r;
			if (f === 0) r = v;
			else if (f === 1) r = v + a;
			else if (f === 2) r = v + up;
			else if (f === 3) r = v + ((a + up) >> 1);
			else {
				const pp = a + up - c, pa = Math.abs(pp - a), pb = Math.abs(pp - up), pc = Math.abs(pp - c);
				r = v + (pa <= pb && pa <= pc ? a : pb <= pc ? up : c);
			}
			cur[x] = r & 0xff;
		}
		for (let x = 0; x < w; x++) {
			const o = (y * w + x) * 4;
			if (ct === 6) { cur.copy(out, o, x * 4, x * 4 + 4); }
			else if (ct === 2) { out[o] = cur[x * 3]; out[o + 1] = cur[x * 3 + 1]; out[o + 2] = cur[x * 3 + 2]; out[o + 3] = 255; }
			else if (ct === 0) { out[o] = out[o + 1] = out[o + 2] = cur[x]; out[o + 3] = 255; }
			else if (ct === 4) { out[o] = out[o + 1] = out[o + 2] = cur[x * 2]; out[o + 3] = cur[x * 2 + 1]; }
			else if (ct === 3) { const i = cur[x]; out[o] = plte[i * 3]; out[o + 1] = plte[i * 3 + 1]; out[o + 2] = plte[i * 3 + 2]; out[o + 3] = trns && i < trns.length ? trns[i] : 255; }
		}
		cur.copy(prev);
	}
	return { w, h, px: out };
}
function crop(img, x0, y0, cw, ch, f = 1) {
	const out = Buffer.alloc(cw * f * ch * f * 4);
	for (let y = 0; y < ch * f; y++) for (let x = 0; x < cw * f; x++) {
		const sx = x0 + Math.floor(x / f), sy = y0 + Math.floor(y / f);
		const o = (y * cw * f + x) * 4;
		if (sx < 0 || sy < 0 || sx >= img.w || sy >= img.h) { out[o + 3] = 255; continue; }
		img.px.copy(out, o, (sy * img.w + sx) * 4, (sy * img.w + sx) * 4 + 4);
		// checkerboard behind transparency so a texture's alpha shows
		const a = out[o + 3] / 255, bg = ((Math.floor(x / 8) + Math.floor(y / 8)) & 1) ? 90 : 60;
		for (let k = 0; k < 3; k++) out[o + k] = Math.round(out[o + k] * a + bg * (1 - a));
		out[o + 3] = 255;
	}
	return { w: cw * f, h: ch * f, px: out };
}
module.exports = { encodePng, decodePng, crop };
if (require.main === module) {
	const [cmd, inp, outp, ...a] = process.argv.slice(2);
	const img = decodePng(inp);
	let r;
	if (cmd === 'montage') {
		// node png_tools_v0166.js montage <first.png> <out.png> cols div x y w h <more.png...>: crop (x,y,w,h) of each, shrink by div, tile
		const [cols, div, cx, cy, cw, chh] = a.slice(0, 6).map(Number);
		const files = [inp, ...a.slice(6)];
		const tw = Math.floor(cw / div), th = Math.floor(chh / div), rows = Math.ceil(files.length / cols);
		const out = Buffer.alloc(tw * cols * th * rows * 4);
		files.forEach((f, i) => {
			const im = decodePng(f), ox = (i % cols) * tw, oy = Math.floor(i / cols) * th;
			for (let y = 0; y < th; y++) for (let x = 0; x < tw; x++) {
				const s = [0, 0, 0];
				for (let dy = 0; dy < div; dy++) for (let dx = 0; dx < div; dx++) {
					const sx = Math.min(im.w - 1, cx + x * div + dx), sy = Math.min(im.h - 1, cy + y * div + dy), o = (sy * im.w + sx) * 4;
					s[0] += im.px[o]; s[1] += im.px[o + 1]; s[2] += im.px[o + 2];
				}
				const o = ((oy + y) * tw * cols + ox + x) * 4;
				for (let k = 0; k < 3; k++) out[o + k] = Math.round(s[k] / (div * div));
				out[o + 3] = 255;
				if (x === 0 || y === 0) { out[o] = 255; out[o + 1] = 0; out[o + 2] = 255; }
			}
		});
		fs.writeFileSync(outp, encodePng(tw * cols, th * rows, out));
		console.log(outp);
		return;
	}
	if (cmd === 'up') r = crop(img, 0, 0, img.w, img.h, +a[0]);
	else if (cmd === 'crop') r = crop(img, +a[0], +a[1], +a[2], +a[3], +(a[4] || 1));
	fs.writeFileSync(outp, encodePng(r.w, r.h, r.px));
	console.log(outp, r.w + 'x' + r.h);
}
