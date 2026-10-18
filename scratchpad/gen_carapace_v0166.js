// v0.16.6 Carapace assets, painted procedurally (the suit itself is the user's chosen skin, minecraftskins.com 19407493,
// cut to suit-only by scratchpad/make_suit_skins.js): the turtle-shell Shield's texture and the tiling hexagon texture of his green energy shell (Shelter, Shell
// Barrier, Protect). Hand-rolled PNG via Node zlib (no Python on this machine).
// Run: node scratchpad/gen_carapace_v0166.js [previewDir]
'use strict';
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const ROOT = path.resolve(__dirname, '..');
const OUT = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/entity/miraculous/carapace');
const PREVIEW = process.argv[2] || null;

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
function img(w, h) { return { w, h, px: Buffer.alloc(w * h * 4) }; }
function set(im, x, y, c) { if (x < 0 || y < 0 || x >= im.w || y >= im.h) return; const i = (y * im.w + x) * 4; im.px[i] = c[0]; im.px[i + 1] = c[1]; im.px[i + 2] = c[2]; im.px[i + 3] = c.length > 3 ? c[3] : 255; }
function clear(im, x, y) { if (x < 0 || y < 0 || x >= im.w || y >= im.h) return; im.px.fill(0, (y * im.w + x) * 4, (y * im.w + x) * 4 + 4); }
function hash(x, y) { let h = (x * 374761393 + y * 668265263) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); h ^= h >>> 16; return (h & 0xffff) / 65535; }
function shade(c, k) { return [Math.max(0, Math.min(255, Math.round(c[0] * k))), Math.max(0, Math.min(255, Math.round(c[1] * k))), Math.max(0, Math.min(255, Math.round(c[2] * k)))]; }
function grain(c, x, y, amt = 0.06) { return shade(c, 1 + (hash(x, y) - 0.5) * 2 * amt); }
function save(name, im) { fs.mkdirSync(OUT, { recursive: true }); fs.writeFileSync(path.join(OUT, name), encodePng(im.w, im.h, im.px)); console.log('wrote', name); }
function preview(name, im, scale) {
	if (!PREVIEW) return;
	const W = im.w * scale, H = im.h * scale, out = img(W, H);
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		const sx = Math.floor(x / scale), sy = Math.floor(y / scale), s = (sy * im.w + sx) * 4, d = (y * W + x) * 4;
		const chk = ((x >> 3) + (y >> 3)) & 1 ? 200 : 160, a = im.px[s + 3] / 255;
		for (let k = 0; k < 3; k++) out.px[d + k] = Math.round(im.px[s + k] * a + chk * (1 - a));
		out.px[d + 3] = 255;
	}
	fs.mkdirSync(PREVIEW, { recursive: true });
	fs.writeFileSync(path.join(PREVIEW, name), encodePng(W, H, out.px));
}

// ---------------------------------------------------------------- the palette
const GREEN = [58, 158, 78];       // the shell plates
const DARK = [28, 86, 42];         // plate lines, the rim
const DARKER = [18, 58, 30];

// ---------------------------------------------------------------- the Shield (64x64)
// front: the turtle shell in the inscribed circle (r 31) -- seven hexagon plates and a ring of edge plates, dark lines,
// each plate lighter at its middle, a dark rim; (0..7, 0..7) the back, (56..63, 0..7) the rim's side, (0..7, 56..63) the strap
function paintShield() {
	const im = img(64, 64);
	const cx = 32, cy = 32;
	const centres = [[0, 0]];
	for (let k = 0; k < 6; k++) { const a = Math.PI / 6 + k * Math.PI / 3; centres.push([Math.cos(a) * 15, Math.sin(a) * 15]); }
	for (let k = 0; k < 12; k++) { const a = k * Math.PI / 6; centres.push([Math.cos(a) * 27, Math.sin(a) * 27]); }
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const dx = x + 0.5 - cx, dy = y + 0.5 - cy, r = Math.hypot(dx, dy);
		if (r > 31.5) continue;
		if (r > 28.5) { set(im, x, y, grain(r > 30.5 ? DARKER : shade(DARK, r > 29.5 ? 0.9 : 1.15), x, y, 0.04)); continue; }
		let d1 = Infinity, d2 = Infinity, near = null;
		for (const c of centres) { const d = Math.hypot(dx - c[0], dy - c[1]); if (d < d1) { d2 = d1; d1 = d; near = c; } else if (d < d2) d2 = d; }
		if (d2 - d1 < 1.5) { set(im, x, y, grain(DARKER, x, y, 0.04)); continue; }
		const outer = Math.hypot(near[0], near[1]) > 20;
		const base = outer ? shade(GREEN, 0.82) : GREEN;
		const glow = Math.max(0, 1 - d1 / (outer ? 8 : 9));
		set(im, x, y, grain(shade(base, 0.9 + 0.35 * glow), x, y, 0.05));
	}
	for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
		set(im, x, y, grain([52, 92, 50], x, y, 0.06));          // the back: a darker, duller shell
		set(im, 56 + x, y, grain(DARK, x, y, 0.06));              // the rim's side
		set(im, x, 56 + y, grain([96, 68, 40], x, y, 0.08));      // the leather strap
	}
	return im;
}

// ---------------------------------------------------------------- the energy shell (48x28, tiling)
// flat-topped hexagons (stretched a hair to tile in 48 x 28): bright lines, a faint fill, a little brighter in the middle
function paintHex() {
	const W = 48, H = 28, im = img(W, H);
	const centres = [];
	for (let i = -1; i <= 3; i++) for (let j = -1; j <= 2; j++) centres.push([i * 24, j * 28 + (i & 1 ? 14 : 0)]);
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		let d1 = Infinity, d2 = Infinity;
		for (const c of centres) { const d = Math.hypot(x + 0.5 - c[0], y + 0.5 - c[1]); if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) d2 = d; }
		const e = d2 - d1;
		if (e < 1.6) set(im, x, y, [150, 255, 160, 235]);
		else if (e < 3.2) set(im, x, y, [90, 230, 120, 140]);
		else set(im, x, y, [60, 200, 100, Math.round(40 + 40 * Math.max(0, 1 - d1 / 14))]);
	}
	return im;
}

const shield = paintShield();
save('shield.png', shield);
const hex = paintHex();
save('hex.png', hex);
preview('shield_x8.png', shield, 8);
preview('hex_x8.png', hex, 8);
