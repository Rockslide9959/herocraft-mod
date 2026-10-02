// node montage.js out.png cols scale crop(x,y,w,h|full) in1.png in2.png ...
const fs = require('fs');
const zlib = require('zlib');
function decode(buf) {
	let p = 8, w, h, ct, idat = [];
	while (p < buf.length) {
		const len = buf.readUInt32BE(p); const type = buf.toString('ascii', p + 4, p + 8);
		const data = buf.subarray(p + 8, p + 8 + len);
		if (type === 'IHDR') { w = data.readUInt32BE(0); h = data.readUInt32BE(4); ct = data[9]; }
		else if (type === 'IDAT') idat.push(data);
		p += 12 + len;
	}
	const raw = zlib.inflateSync(Buffer.concat(idat));
	const bpp = ct === 6 ? 4 : 3;
	const out = Buffer.alloc(w * h * 4);
	const stride = w * bpp;
	let prev = Buffer.alloc(stride);
	for (let y = 0; y < h; y++) {
		const f = raw[y * (stride + 1)];
		const line = Buffer.from(raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1)));
		for (let i = 0; i < stride; i++) {
			const a = i >= bpp ? line[i - bpp] : 0, b = prev[i], c = i >= bpp ? prev[i - bpp] : 0;
			let v = line[i];
			if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1;
			else if (f === 4) { const pp = a + b - c, pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c); v += (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c); }
			line[i] = v & 255;
		}
		for (let x = 0; x < w; x++) { out[(y * w + x) * 4] = line[x * bpp]; out[(y * w + x) * 4 + 1] = line[x * bpp + 1]; out[(y * w + x) * 4 + 2] = line[x * bpp + 2]; out[(y * w + x) * 4 + 3] = 255; }
		prev = line;
	}
	return { w, h, px: out };
}
function crc32(b) { let c, crc = 0xffffffff; for (let n = 0; n < b.length; n++) { c = (crc ^ b[n]) & 0xff; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; crc = (crc >>> 8) ^ c; } return (crc ^ 0xffffffff) >>> 0; }
function chunk(t, d) { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc32(td)); return Buffer.concat([l, td, c]); }
function encode(w, h, px) {
	const raw = Buffer.alloc((w * 4 + 1) * h);
	for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; px.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
	const ih = Buffer.alloc(13); ih.writeUInt32BE(w); ih.writeUInt32BE(h, 4); ih[8] = 8; ih[9] = 6;
	return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ih), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
const [out, colsS, scaleS, cropS, ...ins] = process.argv.slice(2);
const cols = +colsS, sc = +scaleS;
const imgs = ins.map(f => decode(fs.readFileSync(f)));
const crop = cropS === 'full' ? [0, 0, imgs[0].w, imgs[0].h] : cropS.split(',').map(Number);
const tw = Math.floor(crop[2] / sc), th = Math.floor(crop[3] / sc);
const rows = Math.ceil(imgs.length / cols);
const W = tw * cols + (cols - 1) * 4, H = th * rows + (rows - 1) * 4;
const px = Buffer.alloc(W * H * 4, 255);
imgs.forEach((im, i) => {
	const ox = (i % cols) * (tw + 4), oy = Math.floor(i / cols) * (th + 4);
	for (let y = 0; y < th; y++) for (let x = 0; x < tw; x++) {
		const sx = crop[0] + Math.floor(x * sc), sy = crop[1] + Math.floor(y * sc);
		const s = (sy * im.w + sx) * 4, d = ((oy + y) * W + ox + x) * 4;
		im.px.copy(px, d, s, s + 4);
	}
});
fs.writeFileSync(out, encode(W, H, px));
console.log('wrote', out, W, H);
