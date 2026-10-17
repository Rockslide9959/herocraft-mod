// v0.16.6 Rena Rouge assets: copies the user's suit skin (minecraftskins.com 17505838 "rena rogue edit") into the
// mod, paints the Flute and the fox tail / ears textures procedurally, and writes an x8 preview of the suit skin.
// Hand-rolled PNG via Node zlib (no Python on this machine). Run: node scratchpad/gen_rena_rouge_v0166.js
'use strict';
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const ROOT = path.resolve(__dirname, '..');
const SHOTS = 'C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod/scratchpad';
const SKIN = path.join(SHOTS, 'kwami_skins/rena_rouge_suit_slim.png');
const OUT = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/entity/miraculous/rena_rouge');

// ---------------------------------------------------------------- PNG
const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function encodePng(w, h, px) {
	const st = w * 4; const raw = Buffer.alloc((st + 1) * h);
	for (let y = 0; y < h; y++) { raw[y * (st + 1)] = 0; px.copy(raw, y * (st + 1) + 1, y * st, y * st + st); }
	const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
	return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}
function decodePng(buf) {
	let p = 8, w = 0, h = 0, depth = 0, type = 0, plte = null, trns = null; const idat = [];
	while (p < buf.length) {
		const len = buf.readUInt32BE(p); const ty = buf.toString('ascii', p + 4, p + 8); const d = buf.subarray(p + 8, p + 8 + len);
		if (ty === 'IHDR') { w = d.readUInt32BE(0); h = d.readUInt32BE(4); depth = d[8]; type = d[9]; if (d[12]) throw new Error('interlaced'); }
		else if (ty === 'PLTE') plte = d; else if (ty === 'tRNS') trns = d; else if (ty === 'IDAT') idat.push(d);
		p += 12 + len;
	}
	if (depth !== 8) throw new Error('depth ' + depth);
	const ch = { 6: 4, 2: 3, 3: 1, 0: 1, 4: 2 }[type];
	const raw = zlib.inflateSync(Buffer.concat(idat)); const st = w * ch; const out = Buffer.alloc(st * h);
	for (let y = 0; y < h; y++) {
		const f = raw[y * (st + 1)]; const line = raw.subarray(y * (st + 1) + 1, y * (st + 1) + 1 + st);
		for (let x = 0; x < st; x++) {
			const a = x >= ch ? out[y * st + x - ch] : 0, b = y > 0 ? out[(y - 1) * st + x] : 0, c = x >= ch && y > 0 ? out[(y - 1) * st + x - ch] : 0;
			let v = line[x];
			if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1;
			else if (f === 4) { const pp = a + b - c, pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
			out[y * st + x] = v & 255;
		}
	}
	const px = Buffer.alloc(w * h * 4);
	for (let i = 0; i < w * h; i++) {
		if (ch === 4) out.copy(px, i * 4, i * 4, i * 4 + 4);
		else if (ch === 3) { px[i * 4] = out[i * 3]; px[i * 4 + 1] = out[i * 3 + 1]; px[i * 4 + 2] = out[i * 3 + 2]; px[i * 4 + 3] = 255; }
		else if (type === 3) { const k = out[i]; px[i * 4] = plte[k * 3]; px[i * 4 + 1] = plte[k * 3 + 1]; px[i * 4 + 2] = plte[k * 3 + 2]; px[i * 4 + 3] = trns && k < trns.length ? trns[k] : 255; }
		else if (ch === 2) { px[i * 4] = px[i * 4 + 1] = px[i * 4 + 2] = out[i * 2]; px[i * 4 + 3] = out[i * 2 + 1]; }
		else { px[i * 4] = px[i * 4 + 1] = px[i * 4 + 2] = out[i]; px[i * 4 + 3] = 255; }
	}
	return { w, h, px };
}

// ---------------------------------------------------------------- canvas
function canvas(w, h) {
	const px = Buffer.alloc(w * h * 4);
	return {
		w, h, px,
		set(x, y, c, a = 255) { if (x < 0 || y < 0 || x >= w || y >= h) return; const i = (y * w + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = a; },
		rect(x, y, rw, rh, fn) { for (let j = 0; j < rh; j++) for (let i = 0; i < rw; i++) { const c = fn(i, j); if (c) this.set(x + i, y + j, c); } },
		save(file) { fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, encodePng(w, h, px)); },
	};
}
const hash = (x, y) => { let h = (x * 374761393 + y * 668265263) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); h ^= h >>> 16; return (h & 0xffff) / 65535; };
const shade = (c, k) => c.map(v => Math.max(0, Math.min(255, Math.round(v * k))));

// ---------------------------------------------------------------- 1. the suit skins
// The user's cut-outs (scratchpad/make_suit_skins.js, from minecraftskins.com 17505838): only the bodysuit, gloves,
// boots, mask and hat-layer ears are left (face, skin and hair transparent), one per arm width.
const SKIN_WIDE = path.join(SHOTS, 'kwami_skins/rena_rouge_suit_wide.png');
const skin = decodePng(fs.readFileSync(SKIN));
const wideSkin = decodePng(fs.readFileSync(SKIN_WIDE));
for (const k of [skin, wideSkin]) if (k.w !== 64 || k.h !== 64) throw new Error('suit skins must be 64x64');
fs.mkdirSync(OUT, { recursive: true });
fs.writeFileSync(path.join(OUT, 'suit.png'), encodePng(64, 64, skin.px));
fs.writeFileSync(path.join(OUT, 'suit_wide.png'), encodePng(64, 64, wideSkin.px));
console.log('suit skins copied (slim -> suit.png, wide -> suit_wide.png)');
// x8 preview
const S = 8, prev = canvas(64 * S, 64 * S);
for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
	const i = (y * 64 + x) * 4; const a = skin.px[i + 3];
	const bg = ((x + y) & 1) ? [60, 60, 70] : [90, 90, 100];
	const c = a ? [skin.px[i], skin.px[i + 1], skin.px[i + 2]] : bg;
	prev.rect(x * S, y * S, S, S, () => c);
}
prev.save(path.join(SHOTS, 'kwami_shots/rena_rouge/suit_skin_x8.png'));

// ---------------------------------------------------------------- 2. the Flute (32x32)
// Cube UV layout (RenaRougeRender.fluteModel): the shaft 2x2x20 px at uv (0,0): its long faces run down v 2..22
// across u 0..8; the white tip 3x3x3 at uv (10,0); the mouthpiece ring 3x1x3 at uv (10,8). The shaft runs along the cube Y (row 0 = the mouth end).
const ORANGE = [236, 118, 30], ORANGE_D = [196, 84, 18], WHITE = [246, 242, 232], HOLE = [40, 22, 14], GOLD = [250, 196, 70];
const flute = canvas(32, 32);
// shaft: long faces (u 0..8, v 2..22), end caps (u 2..6, v 0..2)
flute.rect(0, 0, 8, 22, (i, j) => {
	if (j < 2) return i >= 2 && i < 6 ? ORANGE_D : null;
	const row = j - 2; // 0 = the mouth end (top of the cube, model -Y), 19 = the far end
	let c = shade(ORANGE, 0.92 + 0.12 * hash(i, j));
	if (row === 0 || row === 1) c = WHITE; // the white band at the mouth end
	if (row === 7 || row === 14) c = GOLD; // two thin gold rings
	const face = Math.floor(i / 2); // 0 west, 1 north (top: the finger holes), 2 east, 3 south
	if (face === 1 && (row === 4 || row === 9 || row === 11 || row === 16 || row === 18) && (i % 2 === 0)) c = HOLE;
	if (face === 1 && (row === 4 || row === 9 || row === 11 || row === 16 || row === 18) && (i % 2 === 1)) c = shade(HOLE, 1.6);
	if (face === 3) c = shade(c, 0.85);
	return c;
});
// the white tip 3x3x3 at (10,0): u 10..22, v 0..6
flute.rect(10, 0, 12, 6, (i, j) => (j < 3 && (i < 3 || i >= 9)) ? null : shade(WHITE, 0.94 + 0.08 * hash(i + 40, j)));
// the mouthpiece ring 3x1x3 at (10,8): u 10..22, v 8..12
flute.rect(10, 8, 12, 4, (i, j) => (j < 3 && (i < 3 || i >= 9)) ? null : shade(ORANGE_D, 0.9 + 0.1 * hash(i, j + 60)));
flute.save(path.join(OUT, 'flute.png'));

// ---------------------------------------------------------------- 3. the fox tail + ears (64x32)
// Tail segments (RenaRougeRender.foxParts): five cubes at SEG_UV (keep in step with the Java boxes).
// Each segment's whole cube is painted from its own row band: bushy orange fur, the last one white.
const FUR = [232, 112, 34], FUR_D = [180, 74, 20], CREAM = [250, 236, 214], DARK = [46, 28, 24];
const parts = canvas(64, 32);
const SEGS = [[3, 3, 4], [4, 4, 4], [5, 5, 6], [5, 5, 5], [4, 4, 4]]; // w, h(depth), length for each tail segment
const SEG_UV = [[0, 0], [13, 0], [30, 0], [0, 12], [21, 12]];
SEGS.forEach((s, k) => {
	const [w, d, l] = s; const uw = 2 * (w + d), vh = d + l; const [u, v0] = SEG_UV[k];
	parts.rect(u, v0, uw, vh, (i, j) => {
		if (j < d && (i < d || i >= d + 2 * w)) return null;
		const n = hash(i + u * 3, j + k * 11);
		let c = k === 4 || (k === 3 && j >= d + l - 1) ? shade(CREAM, 0.94 + 0.08 * n) : shade(n > 0.82 ? FUR_D : FUR, 0.9 + 0.15 * n);
		// fur streaks along the length
		if (k < 4 && ((i + Math.floor(j / 2)) % 3 === 0)) c = shade(c, 0.9);
		return c;
	});
});
// ears: the base cube 4x3x1 at (0,24) -- orange, the front face cream inside an orange rim; the tip cube 2x2x1 at
// (12,24) -- orange at the bottom, the dark tip on top
parts.rect(0, 24, 10, 4, (i, j) => {
	if (j < 1 && (i < 1 || i >= 9)) return null;
	if (j >= 1 && i >= 2 && i <= 3) return shade(CREAM, 0.95 + 0.05 * hash(i, j));
	return shade(FUR, 0.92 + 0.1 * hash(i, j + 90));
});
parts.rect(12, 24, 6, 3, (i, j) => (j < 1 && (i < 1 || i >= 5)) ? null : j === 2 ? FUR : DARK);
parts.save(path.join(OUT, 'fox_parts.png'));
// ---------------------------------------------------------------- 4. the Mirage bubble (32x32, a billboard)
const bubble = canvas(32, 32);
for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
	const dx = (x + 0.5 - 16) / 15, dy = (y + 0.5 - 16) / 15, r = Math.sqrt(dx * dx + dy * dy);
	if (r > 1) continue;
	const rim = Math.pow(r, 6);
	const hl = Math.max(0, 1 - Math.hypot(dx + 0.38, dy + 0.42) / 0.22);
	const c = [255, Math.round(150 + 70 * rim + 100 * hl), Math.round(50 + 60 * rim + 200 * hl)].map(v => Math.min(255, v));
	bubble.set(x, y, c, Math.round(Math.min(255, 50 + 190 * rim + 230 * hl)));
}
bubble.save(path.join(OUT, 'bubble.png'));
console.log('wrote', OUT);
