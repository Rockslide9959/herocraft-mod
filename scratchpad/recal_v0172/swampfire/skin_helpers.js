// v0.17.2 Recalibrated Omnitrix, group A (Swampfire / Echo Echo / Humungousaur): the small paint helpers the three skin generators
// share (a copy of gen_omnitrix_aliens_b_textures.js's, kept here so those generators stand alone).
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('../../pnglib.js');

const TEX = path.join(__dirname, '..', '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures');
function img(w, h) { return { w, h, px: Buffer.alloc(w * h * 4) }; }
const hex = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
function hash(x, y, s) { let h = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; h = (h ^ (h >>> 13)) * 1274126177; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; }
function shade(c, f) { return c.map(v => Math.max(0, Math.min(255, Math.round(v * f)))); }
function mixc(a, b, t) { return a.map((v, i) => Math.round(v + (b[i] - v) * t)); }
function put(im, x, y, c, a = 255) { if (x < 0 || y < 0 || x >= im.w || y >= im.h) return; const i = (y * im.w + x) * 4; im.px[i] = c[0]; im.px[i + 1] = c[1]; im.px[i + 2] = c[2]; im.px[i + 3] = a; }
function get(im, x, y) { const i = (y * im.w + x) * 4; return [im.px[i], im.px[i + 1], im.px[i + 2], im.px[i + 3]]; }
function rect(im, x, y, w, h, fn) { for (let v = 0; v < h; v++) for (let u = 0; u < w; u++) { const c = fn(u, v, w, h, x + u, y + v); if (c) put(im, x + u, y + v, c.slice(0, 3), c[3] === undefined ? 255 : c[3]); } }
function save(im, rel, preview) {
	const out = path.join(TEX, rel);
	fs.mkdirSync(path.dirname(out), { recursive: true });
	PNG.write(out, im.w, im.h, im.px);
	if (preview) {
		const s = 8;
		const p = img(im.w * s, im.h * s);
		for (let y = 0; y < p.h; y++) for (let x = 0; x < p.w; x++) {
			const c = get(im, Math.floor(x / s), Math.floor(y / s));
			const chk = ((Math.floor(x / 8) + Math.floor(y / 8)) % 2) ? 200 : 160;
			const a = c[3] / 255;
			put(p, x, y, [0, 1, 2].map(k => Math.round(c[k] * a + chk * (1 - a))));
		}
		fs.mkdirSync(preview, { recursive: true });
		PNG.write(path.join(preview, rel.replace(/[\/]/g, '_')), p.w, p.h, p.px);
	}
}
function faces(ox, oy, w, h, d) {
	return {
		top: [ox + d, oy, w, d], bottom: [ox + d + w, oy, w, d],
		right: [ox, oy + d, d, h], front: [ox + d, oy + d, w, h], left: [ox + d + w, oy + d, d, h], back: [ox + 2 * d + w, oy + d, w, h],
	};
}
function paintBox(im, box, fn) { for (const [name, f] of Object.entries(box)) rect(im, f[0], f[1], f[2], f[3], (u, v, w, h, x, y) => fn(name, u, v, w, h, x, y)); }
const SKIN = {
	head: faces(0, 0, 8, 8, 8), hat: faces(32, 0, 8, 8, 8),
	body: faces(16, 16, 8, 12, 4), jacket: faces(16, 32, 8, 12, 4),
	rArm: faces(40, 16, 4, 12, 4), rSleeve: faces(40, 32, 4, 12, 4),
	lArm: faces(32, 48, 4, 12, 4), lSleeve: faces(48, 48, 4, 12, 4),
	rLeg: faces(0, 16, 4, 12, 4), rPants: faces(0, 32, 4, 12, 4),
	lLeg: faces(16, 48, 4, 12, 4), lPants: faces(0, 48, 4, 12, 4),
};
// a muscled-skin tone for a face: darker on the sides and underneath, lighter on top, a little noise
function tone(ramp, u, v, w, h, seed, f) {
	const n = hash(u, v, seed);
	let t = 2;
	if (f === 'right' || f === 'left' || f === 'back') t = 1;
	if (f === 'bottom') t = 0;
	if (f === 'top') t = 3;
	if ((u === 0 || u === w - 1) && f !== 'top' && f !== 'bottom') t -= 1;
	return shade(ramp[Math.max(0, Math.min(ramp.length - 1, t))], 0.93 + n * 0.14);
}
module.exports = { img, hex, hash, shade, mixc, put, get, rect, save, faces, paintBox, SKIN, tone };
