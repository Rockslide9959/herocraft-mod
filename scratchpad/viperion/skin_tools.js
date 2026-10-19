// Skin helpers (v0.16.6 Miraculous suits): PNG decode (RGBA / RGB / palette, 8-bit, non-interlaced) + encode, and
// an upscaled preview with a grid. Usage: node skin_tools.js preview <in.png> <out.png> [scale]
'use strict';
const fs = require('fs');
const zlib = require('zlib');
function decode(file) {
	const b = fs.readFileSync(file); let o = 8; const cs = [];
	while (o < b.length) { const len = b.readUInt32BE(o); const t = b.toString('ascii', o + 4, o + 8); cs.push({ t, d: b.slice(o + 8, o + 8 + len) }); o += 12 + len; }
	const ih = cs.find(c => c.t === 'IHDR').d; const w = ih.readUInt32BE(0), h = ih.readUInt32BE(4), depth = ih[8], ct = ih[9];
	if (depth !== 8) throw new Error('only 8-bit PNGs');
	const plte = cs.find(c => c.t === 'PLTE'), trns = cs.find(c => c.t === 'tRNS');
	const raw = zlib.inflateSync(Buffer.concat(cs.filter(c => c.t === 'IDAT').map(c => c.d)));
	const ch = { 6: 4, 2: 3, 3: 1, 0: 1, 4: 2 }[ct]; const st = w * ch; const px = Buffer.alloc(w * h * 4); let prev = Buffer.alloc(st);
	for (let y = 0; y < h; y++) {
		const f = raw[y * (st + 1)]; const line = raw.slice(y * (st + 1) + 1, y * (st + 1) + 1 + st); const cur = Buffer.alloc(st);
		for (let x = 0; x < st; x++) { const A = x >= ch ? cur[x - ch] : 0, B = prev[x], C = x >= ch ? prev[x - ch] : 0; let v = line[x];
			if (f === 1) v = (v + A) & 255; else if (f === 2) v = (v + B) & 255; else if (f === 3) v = (v + ((A + B) >> 1)) & 255;
			else if (f === 4) { const p = A + B - C; const pa = Math.abs(p - A), pb = Math.abs(p - B), pc = Math.abs(p - C); v = (v + (pa <= pb && pa <= pc ? A : pb <= pc ? B : C)) & 255; } cur[x] = v; }
		for (let x = 0; x < w; x++) { const d = (y * w + x) * 4, s = x * ch;
			if (ct === 3) { const i = cur[s]; px[d] = plte.d[i * 3]; px[d + 1] = plte.d[i * 3 + 1]; px[d + 2] = plte.d[i * 3 + 2]; px[d + 3] = trns && i < trns.d.length ? trns.d[i] : 255; }
			else if (ct === 0 || ct === 4) { px[d] = px[d + 1] = px[d + 2] = cur[s]; px[d + 3] = ct === 4 ? cur[s + 1] : 255; }
			else { px[d] = cur[s]; px[d + 1] = cur[s + 1]; px[d + 2] = cur[s + 2]; px[d + 3] = ct === 6 ? cur[s + 3] : 255; } }
		prev = cur;
	}
	return { w, h, px };
}
const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function encode(w, h, px) { const st = w * 4; const raw = Buffer.alloc((st + 1) * h); for (let y = 0; y < h; y++) { raw[y * (st + 1)] = 0; px.copy(raw, y * (st + 1) + 1, y * st, y * st + st); }
	const ih = Buffer.alloc(13); ih.writeUInt32BE(w, 0); ih.writeUInt32BE(h, 4); ih[8] = 8; ih[9] = 6;
	return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ih), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]); }
function preview(img, scale = 10) { // checkerboard behind transparency, grid every 8 texels
	const W = img.w * scale, H = img.h * scale, out = Buffer.alloc(W * H * 4);
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		const sx = Math.floor(x / scale), sy = Math.floor(y / scale), s = (sy * img.w + sx) * 4, d = (y * W + x) * 4;
		const chk = ((x >> 3) + (y >> 3)) & 1 ? 200 : 160, a = img.px[s + 3] / 255;
		for (let k = 0; k < 3; k++) out[d + k] = Math.round(img.px[s + k] * a + chk * (1 - a));
		if (x % (scale * 8) === 0 || y % (scale * 8) === 0) { out[d] = 255; out[d + 1] = 0; out[d + 2] = 255; }
		out[d + 3] = 255;
	}
	return { w: W, h: H, px: out };
}
module.exports = { decode, encode, preview };
if (require.main === module && process.argv[2] === 'preview') {
	const p = preview(decode(process.argv[3]), +(process.argv[5] || 10));
	fs.writeFileSync(process.argv[4], encode(p.w, p.h, p.px));
}

// ---- v0.16.6: slim (3 px) <-> wide (4 px) arm layouts, so a suit fits whichever model the wearer uses ----
// The four arm regions (origin of the 16x16 block): right arm base (40,16), right sleeve (40,32), left arm base (32,48),
// left sleeve (48,48). Columns are resampled per face: 3 -> 4 repeats the middle column, 4 -> 3 drops the third.
const ARM_REGIONS = [[40, 16], [40, 32], [32, 48], [48, 48]];
function armFaces(ox, oy, slim) {
	const a = slim ? 3 : 4;
	return {
		top: [ox + 4, oy, a, 4], bottom: [ox + 4 + a, oy, a, 4],
		right: [ox, oy + 4, 4, 12], front: [ox + 4, oy + 4, a, 12], left: [ox + 4 + a, oy + 4, 4, 12], back: [ox + 8 + a, oy + 4, a, 12],
	};
}
function convertArms(img, toSlim) {
	const out = { w: img.w, h: img.h, px: Buffer.from(img.px) };
	const map = toSlim ? [0, 1, 3] : [0, 1, 1, 2];
	for (const [ox, oy] of ARM_REGIONS) {
		const src = armFaces(ox, oy, !toSlim), dst = armFaces(ox, oy, toSlim);
		// clear the whole 16x16 block first, then lay the faces back down
		for (let y = oy; y < oy + 16; y++) for (let x = ox; x < ox + 16; x++) out.px.fill(0, (y * img.w + x) * 4, (y * img.w + x) * 4 + 4);
		for (const f of Object.keys(src)) {
			const [sx, sy, sw, sh] = src[f], [dx, dy, dw, dh] = dst[f];
			for (let y = 0; y < dh; y++) for (let x = 0; x < dw; x++) {
				const cx = dw === sw ? x : map[x];
				const si = ((sy + y) * img.w + sx + cx) * 4, di = ((dy + y) * img.w + dx + x) * 4;
				img.px.copy(out.px, di, si, si + 4);
			}
		}
	}
	return out;
}
module.exports.convertArms = convertArms;

// ---- v0.16.6: fill the gaps in a bodysuit -- every transparent texel on a BASE-layer body / arm / leg face takes the
// colour of the nearest opaque texel on the same face (the head and the overlay layer are left alone).
function boxFaces(ox, oy, w, h, d) { // the standard skin box unwrap: width w, height h, depth d at (ox, oy)
	return [[ox + d, oy, w, d], [ox + d + w, oy, w, d], [ox, oy + d, d, h], [ox + d, oy + d, w, h], [ox + d + w, oy + d, d, h], [ox + 2 * d + w, oy + d, w, h]];
}
function fillSuitGaps(img, slim) {
	const a = slim ? 3 : 4;
	const faces = [
		...boxFaces(16, 16, 8, 12, 4),            // body
		...boxFaces(40, 16, a, 12, 4),            // right arm
		...boxFaces(32, 48, a, 12, 4),            // left arm
		...boxFaces(0, 16, 4, 12, 4),             // right leg
		...boxFaces(16, 48, 4, 12, 4),            // left leg
	];
	let filled = 0;
	for (const [fx, fy, fw, fh] of faces) {
		const op = [];
		for (let y = fy; y < fy + fh; y++) for (let x = fx; x < fx + fw; x++) if (img.px[(y * img.w + x) * 4 + 3] > 0) op.push([x, y]);
		if (!op.length) continue;
		for (let y = fy; y < fy + fh; y++) for (let x = fx; x < fx + fw; x++) {
			const i = (y * img.w + x) * 4;
			if (img.px[i + 3] > 0) continue;
			let best = null, bd = Infinity;
			for (const [qx, qy] of op) { const dd = (qx - x) ** 2 + (qy - y) ** 2; if (dd < bd) { bd = dd; best = [qx, qy]; } }
			const j = (best[1] * img.w + best[0]) * 4;
			img.px.copy(img.px, i, j, j + 4);
			img.px[i + 3] = 255;
			filled++;
		}
	}
	return filled;
}
module.exports.fillSuitGaps = fillSuitGaps;
