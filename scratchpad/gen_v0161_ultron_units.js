// v0.16.1 Ultron Uprising, Part A: the Lancer and Hijacker skins, painted procedurally (user decision: no Skindex
// downloads) by recolouring the existing Ultron skins, plus the Lancer's hex energy-shield texture.
// usage: node scratchpad/gen_v0161_ultron_units.js [repo or worktree root]   (default: this script's repo)
//   - lancer.png    <- heavy.png: a heavier SILVER-and-RED frame (silver plate ramp, red shoulder pads, red chest
//                      stripe + arc plate, red belt), wide arms;
//   - hijacker.png  <- sniper.png: lean DARK GUNMETAL with glowing RED SEAMS (centre line, limb seams, visor slit),
//                      converted to the SLIM (3-pixel) arm layout;
//   - <name>_eyes.png: every strongly red texel alone (drawn full-bright by the renderer's eyes layer, so the seams glow);
//   - lancer_shield.png: a 32x32 translucent red hex plane (the Lancer's front shield layer).
// Hand-rolled PNG via Node zlib (no dependencies), like the other generators. Writes a nearest-scaled preview sheet to
// scratchpad/shots_v0161/ultron_a/skins_preview.png when that folder can be made.
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const root = process.argv[2] || path.join(__dirname, '..');
const entDir = path.join(root, 'src/main/resources/assets/projecthero/textures/entity/ultron');

// ------------------------------------------------------------------ png
function decode(buf) {
	let p = 8, w, h, ct, bd, idat = [], plte = null, trns = null;
	while (p < buf.length) {
		const len = buf.readUInt32BE(p), type = buf.toString('ascii', p + 4, p + 8), d = buf.subarray(p + 8, p + 8 + len);
		if (type === 'IHDR') { w = d.readUInt32BE(0); h = d.readUInt32BE(4); bd = d[8]; ct = d[9]; }
		else if (type === 'IDAT') idat.push(d);
		else if (type === 'PLTE') plte = d;
		else if (type === 'tRNS') trns = d;
		p += 12 + len;
	}
	if (bd !== 8) throw new Error('bit depth ' + bd);
	const raw = zlib.inflateSync(Buffer.concat(idat));
	const bpp = { 6: 4, 2: 3, 3: 1, 0: 1, 4: 2 }[ct];
	const stride = w * bpp;
	const out = Buffer.alloc(w * h * 4);
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
		for (let x = 0; x < w; x++) {
			const o = (y * w + x) * 4;
			if (ct === 3) { const k = line[x]; out[o] = plte[k * 3]; out[o + 1] = plte[k * 3 + 1]; out[o + 2] = plte[k * 3 + 2]; out[o + 3] = trns && k < trns.length ? trns[k] : 255; }
			else if (ct === 6) line.copy(out, o, x * 4, x * 4 + 4);
			else if (ct === 2) { out[o] = line[x * 3]; out[o + 1] = line[x * 3 + 1]; out[o + 2] = line[x * 3 + 2]; out[o + 3] = 255; }
			else { out[o] = out[o + 1] = out[o + 2] = line[x * bpp]; out[o + 3] = ct === 4 ? line[x * 2 + 1] : 255; }
		}
		prev = line;
	}
	return { w, h, px: out };
}
const CRC = (() => { const t = new Int32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c; } return t; })();
function crc32(b) { let c = -1; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 255] ^ (c >>> 8); return (c ^ -1) >>> 0; }
function chunk(type, data) {
	const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
	const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
	const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
	return Buffer.concat([len, td, crc]);
}
function encode(w, h, px) {
	const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
	const raw = Buffer.alloc((w * 4 + 1) * h);
	for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; px.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
	return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

// ------------------------------------------------------------------ skin helpers
const W = 64;
const get = (px, x, y) => { const o = (y * W + x) * 4; return [px[o], px[o + 1], px[o + 2], px[o + 3]]; };
const put = (px, x, y, c) => { const o = (y * W + x) * 4; px[o] = c[0]; px[o + 1] = c[1]; px[o + 2] = c[2]; px[o + 3] = c[3] ?? 255; };
const isRed = (c) => c[3] > 0 && c[0] >= 150 && c[0] > c[1] * 2.0 && c[0] > c[2] * 2.0;
const lum = (c) => 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2];
const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));
function rect(px, x0, y0, w, h, c) { for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) put(px, x, y, c); }

/** Recolours every opaque texel: red stays (re-tinted) red, the rest goes through {@code ramp(luminance)}. */
function recolour(px, ramp, red) {
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const c = get(px, x, y);
		if (c[3] === 0) continue;
		if (isRed(c)) { put(px, x, y, [red[0], red[1], red[2], c[3]]); continue; }
		const n = ramp(lum(c) / 255);
		put(px, x, y, [n[0], n[1], n[2], c[3]]);
	}
}

/** A wide (4-pixel) arm box at (x0, y0) -> the slim (3-pixel) layout, dropping the third column of every 4-wide face. */
function toSlimArm(px, x0, y0) {
	const old = Buffer.from(px);
	const g = (x, y) => get(old, x, y);
	const keep = [0, 1, 3];
	// clear the whole 16 x 16 box first
	for (let y = y0; y < y0 + 16; y++) for (let x = x0; x < x0 + 16; x++) put(px, x, y, [0, 0, 0, 0]);
	// top (x0+4, y0, 4 wide) and bottom (x0+8, y0) -> top (x0+4, w3) and bottom (x0+7, w3)
	for (let y = 0; y < 4; y++) for (let i = 0; i < 3; i++) {
		put(px, x0 + 4 + i, y0 + y, g(x0 + 4 + keep[i], y0 + y));
		put(px, x0 + 7 + i, y0 + y, g(x0 + 8 + keep[i], y0 + y));
	}
	for (let y = 4; y < 16; y++) {
		for (let i = 0; i < 4; i++) put(px, x0 + i, y0 + y, g(x0 + i, y0 + y)); // right side (unchanged)
		for (let i = 0; i < 3; i++) put(px, x0 + 4 + i, y0 + y, g(x0 + 4 + keep[i], y0 + y)); // front
		for (let i = 0; i < 4; i++) put(px, x0 + 7 + i, y0 + y, g(x0 + 8 + i, y0 + y)); // left side
		for (let i = 0; i < 3; i++) put(px, x0 + 11 + i, y0 + y, g(x0 + 12 + keep[i], y0 + y)); // back
	}
}

function eyesOf(px) {
	const eyes = Buffer.alloc(64 * 64 * 4);
	let red = 0;
	for (let i = 0; i < 64 * 64; i++) {
		const c = [px[i * 4], px[i * 4 + 1], px[i * 4 + 2], px[i * 4 + 3]];
		if (isRed(c)) {
			eyes[i * 4] = Math.min(255, c[0] + 30); eyes[i * 4 + 1] = Math.min(255, c[1] + 10); eyes[i * 4 + 2] = Math.min(255, c[2] + 10); eyes[i * 4 + 3] = 255;
			red++;
		}
	}
	return { eyes, red };
}

function load(name) {
	const im = decode(fs.readFileSync(path.join(entDir, name + '.png')));
	if (im.w !== 64 || im.h !== 64) throw new Error(name + ' is not 64x64');
	return Buffer.from(im.px);
}

// ------------------------------------------------------------------ the Lancer: heavier, silver and red (from the Heavy)
const RED = [228, 30, 22], RED_DEEP = [176, 18, 14], RED_HOT = [255, 70, 52];
const lancer = load('heavy');
recolour(lancer, (l) => { const c = 92 + l * 150; return [clamp(c), clamp(c + 3), clamp(c + 10)]; }, RED);
// plate lines: darken every 4th row of the body / legs base faces a touch (riveted armour bands)
for (const [x0, y0, w, h] of [[20, 20, 8, 12], [32, 20, 8, 12], [4, 20, 4, 12], [20, 52, 4, 12]]) {
	for (let y = y0 + 3; y < y0 + h; y += 4) for (let x = x0; x < x0 + w; x++) {
		const c = get(lancer, x, y); if (!isRed(c)) put(lancer, x, y, [clamp(c[0] * 0.72), clamp(c[1] * 0.72), clamp(c[2] * 0.74), 255]);
	}
}
// red chest stripe + arc plate + belt (body front 20..27 x 20..31)
for (let y = 20; y < 31; y++) { put(lancer, 23, y, RED_DEEP); put(lancer, 24, y, RED_DEEP); }
for (let x = 21; x < 27; x++) put(lancer, x, 22, RED);
put(lancer, 23, 23, RED_HOT); put(lancer, 24, 23, RED_HOT);
for (let x = 20; x < 28; x++) put(lancer, x, 30, RED_DEEP);
// back plate: a red spine (body back 32..39 x 20..31)
for (let y = 21; y < 30; y++) { put(lancer, 35, y, RED_DEEP); put(lancer, 36, y, RED_DEEP); }
// shoulder pads: the arm tops and the top three rows round each arm (right arm box 40,16 / left arm box 32,48)
rect(lancer, 44, 16, 4, 4, RED);
rect(lancer, 40, 20, 16, 3, RED);
for (let x = 40; x < 56; x++) put(lancer, x, 22, RED_DEEP);
rect(lancer, 36, 48, 4, 4, RED);
rect(lancer, 32, 52, 16, 3, RED);
for (let x = 32; x < 48; x++) put(lancer, x, 54, RED_DEEP);
// knee guards (right leg front 4..7 x 20..31, left leg front 20..23 x 52..63)
rect(lancer, 4, 25, 4, 2, RED_DEEP);
rect(lancer, 20, 57, 4, 2, RED_DEEP);
// the visor: keep the source's red eyes, and add a bright T-visor across the face (head front 8..15 x 8..15)
for (let x = 9; x < 15; x++) put(lancer, x, 12, RED);
put(lancer, 11, 13, RED_DEEP); put(lancer, 12, 13, RED_DEEP);

// ------------------------------------------------------------------ the Hijacker: lean dark gunmetal, red seams (from the Sniper)
const hij = load('sniper');
recolour(hij, (l) => { const c = 26 + l * 62; return [clamp(c), clamp(c + 4), clamp(c + 11)]; }, RED);
const SEAM = [214, 26, 20];
// body: a centre seam front and back, and a collar line
for (let y = 20; y < 32; y++) { put(hij, 23, y, SEAM); put(hij, 35, y, SEAM); }
for (let x = 20; x < 28; x++) put(hij, x, 20, [40, 44, 52]);
for (let x = 21; x < 27; x++) put(hij, x, 25, SEAM);
// legs: a seam down the outer side and the front centre
for (let y = 20; y < 32; y++) { put(hij, 0, y, SEAM); put(hij, 5, y, SEAM); }
for (let y = 52; y < 64; y++) { put(hij, 27, y, SEAM); put(hij, 21, y, SEAM); }
// head: a thin red visor slit and a seam over the crown
for (let x = 8; x < 16; x++) put(hij, x, 11, [18, 20, 26]);
for (let x = 9; x < 15; x++) put(hij, x, 12, SEAM);
for (let y = 0; y < 8; y++) put(hij, 11, y, SEAM);
// arms (still wide here): a seam down the outer side and the front, then convert to slim
for (let y = 20; y < 32; y++) { put(hij, 41, y, SEAM); put(hij, 45, y, SEAM); }
for (let y = 52; y < 64; y++) { put(hij, 42, y, SEAM); put(hij, 37, y, SEAM); }
toSlimArm(hij, 40, 16); // right arm base
toSlimArm(hij, 40, 32); // right arm sleeve
toSlimArm(hij, 32, 48); // left arm base
toSlimArm(hij, 48, 48); // left arm sleeve

// ------------------------------------------------------------------ write the skins and their glow
for (const [name, px] of [['lancer', lancer], ['hijacker', hij]]) {
	const { eyes, red } = eyesOf(px);
	fs.writeFileSync(path.join(entDir, name + '.png'), encode(64, 64, px));
	fs.writeFileSync(path.join(entDir, name + '_eyes.png'), encode(64, 64, eyes));
	console.log(name, 'glowing texels', red);
}

// ------------------------------------------------------------------ the Lancer's hex shield (32 x 32, translucent red)
{
	const S = 32;
	const b = Buffer.alloc(S * S * 4);
	const r = 4.2; // hex size
	const centres = [];
	for (let row = -2; row < 12; row++) for (let col = -2; col < 10; col++) {
		centres.push([col * r * Math.sqrt(3) + (row % 2 ? r * Math.sqrt(3) / 2 : 0), row * r * 1.5]);
	}
	for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
		let d1 = 1e9, d2 = 1e9;
		for (const [cx, cy] of centres) {
			const d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
			if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) d2 = d;
		}
		const edge = d2 - d1 < 1.1;
		const border = x === 0 || y === 0 || x === S - 1 || y === S - 1;
		const o = (y * S + x) * 4;
		if (border) { b[o] = 255; b[o + 1] = 90; b[o + 2] = 70; b[o + 3] = 235; }
		else if (edge) { b[o] = 255; b[o + 1] = 60; b[o + 2] = 44; b[o + 3] = 190; }
		else { b[o] = 220; b[o + 1] = 24; b[o + 2] = 18; b[o + 3] = 64 + Math.round(30 * (1 - d1 / r)); }
	}
	fs.writeFileSync(path.join(entDir, 'lancer_shield.png'), encode(S, S, b));
	console.log('lancer_shield 32x32');
}

// ------------------------------------------------------------------ a preview sheet (8x nearest, on grey)
try {
	const outDir = path.join(root, 'scratchpad/shots_v0161/ultron_a');
	fs.mkdirSync(outDir, { recursive: true });
	const names = ['heavy', 'lancer', 'sniper', 'hijacker'];
	const Z = 8, gap = 8;
	const PW = names.length * (64 * Z + gap) + gap, PH = 64 * Z + gap * 2;
	const sheet = Buffer.alloc(PW * PH * 4);
	for (let i = 0; i < PW * PH; i++) { sheet[i * 4] = 60; sheet[i * 4 + 1] = 62; sheet[i * 4 + 2] = 66; sheet[i * 4 + 3] = 255; }
	names.forEach((n, k) => {
		const px = decode(fs.readFileSync(path.join(entDir, n + '.png'))).px;
		const ox = gap + k * (64 * Z + gap), oy = gap;
		for (let y = 0; y < 64 * Z; y++) for (let x = 0; x < 64 * Z; x++) {
			const s = (Math.floor(y / Z) * 64 + Math.floor(x / Z)) * 4;
			if (px[s + 3] === 0) continue;
			const o = ((oy + y) * PW + ox + x) * 4;
			sheet[o] = px[s]; sheet[o + 1] = px[s + 1]; sheet[o + 2] = px[s + 2];
		}
	});
	fs.writeFileSync(path.join(outDir, 'skins_preview.png'), encode(PW, PH, sheet));
	console.log('preview', path.join(outDir, 'skins_preview.png'));
} catch (e) {
	console.log('no preview:', e.message);
}
