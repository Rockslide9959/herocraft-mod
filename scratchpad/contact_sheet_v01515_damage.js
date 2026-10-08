// v0.15.15 damage: compose screenshot / texture contact sheets (no deps -- hand-rolled PNG via zlib).
// usage: node contact_sheet_v01515_damage.js out.png cols cellW cellH cropFrac file1 file2 ...
//   cropFrac: 0..1 -- keep this centred fraction of each image (1 = whole image), then nearest-scale into the cell.
const fs = require('fs');
const zlib = require('zlib');

function decode(file) {
	const b = fs.readFileSync(file);
	let p = 8, w, h, ct, idat = [];
	while (p < b.length) {
		const len = b.readUInt32BE(p);
		const type = b.toString('ascii', p + 4, p + 8);
		const data = b.subarray(p + 8, p + 8 + len);
		if (type === 'IHDR') {
			w = data.readUInt32BE(0); h = data.readUInt32BE(4); ct = data[9];
			if (data[8] !== 8 || data[12] !== 0) throw new Error(file + ': unsupported png');
		} else if (type === 'IDAT') idat.push(data);
		p += 12 + len;
	}
	const bpp = ct === 6 ? 4 : ct === 2 ? 3 : ct === 4 ? 2 : 1;
	const raw = zlib.inflateSync(Buffer.concat(idat));
	const stride = w * bpp;
	const out = Buffer.alloc(w * h * 4);
	let prev = Buffer.alloc(stride);
	for (let y = 0; y < h; y++) {
		const f = raw[y * (stride + 1)];
		const line = Buffer.from(raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1)));
		for (let i = 0; i < stride; i++) {
			const a = i >= bpp ? line[i - bpp] : 0, up = prev[i], c = i >= bpp ? prev[i - bpp] : 0;
			let v = line[i];
			if (f === 1) v += a; else if (f === 2) v += up; else if (f === 3) v += (a + up) >> 1;
			else if (f === 4) { const pp = a + up - c, pa = Math.abs(pp - a), pb = Math.abs(pp - up), pc = Math.abs(pp - c);
				v += pa <= pb && pa <= pc ? a : pb <= pc ? up : c; }
			line[i] = v & 255;
		}
		for (let x = 0; x < w; x++) {
			const o = (y * w + x) * 4, s = x * bpp;
			if (bpp >= 3) { out[o] = line[s]; out[o + 1] = line[s + 1]; out[o + 2] = line[s + 2]; out[o + 3] = bpp === 4 ? line[s + 3] : 255; }
			else { out[o] = out[o + 1] = out[o + 2] = line[s]; out[o + 3] = bpp === 2 ? line[s + 1] : 255; }
		}
		prev = line;
	}
	return { w, h, px: out };
}

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
	const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
	const td = Buffer.concat([Buffer.from(type), data]);
	const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
	return Buffer.concat([len, td, crc]);
}
function encode(w, h, px) {
	const raw = Buffer.alloc((w * 4 + 1) * h);
	for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; px.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
	const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
	return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr),
		chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

const [out, colsS, cwS, chS, cropS, ...files] = process.argv.slice(2);
const cols = +colsS, cw = +cwS, ch = +chS, crop = +cropS;
const rows = Math.ceil(files.length / cols);
const W = cols * cw, H = rows * ch;
const sheet = Buffer.alloc(W * H * 4);
for (let i = 0; i < W * H; i++) { sheet[i * 4] = 40; sheet[i * 4 + 1] = 40; sheet[i * 4 + 2] = 44; sheet[i * 4 + 3] = 255; }
files.forEach((f, idx) => {
	const img = decode(f);
	const cwid = img.w * crop, chei = img.h * crop;
	const sc = Math.min(cwid / cw, chei / ch);
	const x0 = (img.w - cw * sc) / 2, y0 = (img.h - ch * sc) / 2;
	const ox = (idx % cols) * cw, oy = Math.floor(idx / cols) * ch;
	for (let y = 0; y < ch - 2; y++) for (let x = 0; x < cw - 2; x++) {
		const sx = Math.min(img.w - 1, Math.floor(x0 + x * sc)), sy = Math.min(img.h - 1, Math.floor(y0 + y * sc));
		const s = (sy * img.w + sx) * 4, d = ((oy + y) * W + ox + x) * 4;
		const a = img.px[s + 3] / 255;
		for (let k = 0; k < 3; k++) sheet[d + k] = Math.round(img.px[s + k] * a + sheet[d + k] * (1 - a));
	}
});
fs.writeFileSync(out, encode(W, H, sheet));
console.log('wrote', out, W + 'x' + H);
