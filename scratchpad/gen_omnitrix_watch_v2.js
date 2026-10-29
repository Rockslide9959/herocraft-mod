// v0.17.1 Omnitrix model + dial icons remake (replaces watch() / silhouettes() / the item icon in gen_omnitrix_textures.js).
//   * entity/omnitrix/watch.png, watch_red.png   128x128: the model's nine-slice swatches (cuff, plate, bezel, core, prong,
//     light, inside, ring), the face (hourglass, 64x64 at 64,64 -- the alien badge and pod still read that quadrant), the
//     dark selection screen (64x64 at 64,0) and the round button lens (16x16 at 0,48). See OmnitrixModel.
//   * item/omnitrix.png                          16x16 particle / fallback icon
//   (v0.17.1 later: the alien_<id> icons are replaced by the user's dial art, gen_omnitrix_dial_icons.js; skip them)
//   * gui/omnitrix/alien_<id>.png                32x32 dial icons: a white figure (tinted lime / red where drawn) with a
//     1-px near-black outline, mid-grey and dark details; drawn 4x supersampled and thresholded, so edges are pixel-crisp.
// Usage: node scratchpad/gen_omnitrix_watch_v2.js [previewDir]   (previewDir also gets the contact sheet + 8x previews)
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('./pnglib.js');

const TEX = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures');
const PREVIEW = process.argv[2];

function img(w, h) { return { w, h, px: Buffer.alloc(w * h * 4) }; }
const hex = s => [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
function hash(x, y, s) { let h = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; h = (h ^ (h >>> 13)) * 1274126177; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; }
function shade(c, f) { return c.map(v => Math.max(0, Math.min(255, Math.round(v * f)))); }
function mixc(a, b, t) { return a.map((v, i) => Math.round(v + (b[i] - v) * t)); }
function put(im, x, y, c, a = 255) { if (x < 0 || y < 0 || x >= im.w || y >= im.h) return; const i = (y * im.w + x) * 4; im.px[i] = c[0]; im.px[i + 1] = c[1]; im.px[i + 2] = c[2]; im.px[i + 3] = a; }
function get(im, x, y) { const i = (y * im.w + x) * 4; return [im.px[i], im.px[i + 1], im.px[i + 2], im.px[i + 3]]; }
function save(im, rel) {
	const out = path.join(TEX, rel);
	fs.mkdirSync(path.dirname(out), { recursive: true });
	PNG.write(out, im.w, im.h, im.px);
	if (PREVIEW) preview(im, rel.replace(/[\\/]/g, '_'), im.w <= 32 ? 8 : 4);
}
function preview(im, name, s) {
	const p = img(im.w * s, im.h * s);
	for (let y = 0; y < p.h; y++) for (let x = 0; x < p.w; x++) {
		const c = get(im, Math.floor(x / s), Math.floor(y / s));
		const chk = ((Math.floor(x / 8) + Math.floor(y / 8)) % 2) ? 200 : 160;
		const a = c[3] / 255;
		put(p, x, y, [0, 1, 2].map(k => Math.round(c[k] * a + chk * (1 - a))));
	}
	fs.mkdirSync(PREVIEW, { recursive: true });
	PNG.write(path.join(PREVIEW, name), p.w, p.h, p.px);
}

// ================================================================ the watch texture
const SW = {
	cuff: { at: [0, 0], base: '#3d4147', line: '#121316', bevel: '#5c6168', noise: 0.05 },
	plate: { at: [16, 0], base: '#53585f', line: '#15161a', bevel: '#757b83', noise: 0.04 },
	black: { at: [32, 0], base: '#1c1d20', line: '#08090a', bevel: '#34373c', noise: 0.03 },
	grey: { at: [48, 0], base: '#7d8389', line: '#26282b', bevel: '#a8aeb3', noise: 0.03 },
	white: { at: [0, 16], base: '#e2e5e8', line: '#7f868c', bevel: '#ffffff', noise: 0.015 },
	green: { at: [16, 16], base: '#8cf03a', line: '#2f7d14', bevel: '#d2ff8a', noise: 0.0, lit: true },
	inner: { at: [32, 16], base: '#1f2125', line: '#0d0e10', bevel: '#26292d', noise: 0.04 },
	ring: { at: [48, 16], base: '#a3a9ae', line: '#2c2f33', bevel: '#d0d5d9', noise: 0.02 },
	dimgreen: { at: [0, 32], base: '#3c7d22', line: '#16300c', bevel: '#5aa834', noise: 0.0, lit: true },
};
const GREEN = { base: hex('#8cf03a'), hi: hex('#ccff80'), lo: hex('#62c424') };
const RED = { base: hex('#ff3d2e'), hi: hex('#ffb0a0'), lo: hex('#c41e14') };

function swatch(im, s, red) {
	let base = hex(s.base), line = hex(s.line), bevel = hex(s.bevel);
	if (s.lit && red) {
		const k = s === SW.green ? 1 : 0.42;
		base = shade(RED.base, k); line = shade(RED.lo, k * 0.5); bevel = shade(RED.hi, k);
	}
	const [ox, oy] = s.at;
	for (let v = 0; v < 16; v++) for (let u = 0; u < 16; u++) {
		const e = Math.min(u, v, 15 - u, 15 - v);
		let c;
		if (e === 0) c = line;
		else if (e === 1) c = (u <= 1 || v <= 1) ? bevel : mixc(base, line, 0.35); // lit top-left bevel, shaded bottom-right
		else c = shade(base, 1 - s.noise + hash(u, v, ox + oy) * s.noise * 2);
		put(im, ox + u, oy + v, c);
	}
}

/** The face: 64x64 disc at (ox, oy). Green hourglass top / bottom, black X dividers, grey side wedges. */
function face(im, ox, oy, red) {
	const G = red ? RED : GREEN;
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const dx = x + 0.5 - 32, dy = y + 0.5 - 32, r = Math.hypot(dx, dy);
		if (r > 31.6) continue;
		let c;
		if (r > 29.6) c = hex('#141517');
		else {
			const ax = Math.abs(dx), ay = Math.abs(dy);
			const w = 3.2, k = 1.12, bw = 4.6; // the waist half-width, the edge slope, the X line's width
			const edge = w + ay / k;
			if (ax <= edge) {
				// green: a soft top-left gloss, darker toward the rim
				const t = r / 30;
				c = mixc(G.base, G.lo, Math.max(0, t - 0.55) * 1.2);
				if (dx + dy * 0.6 < -10 && ay > 8 && ax < edge - 2.2 && r < 27) c = mixc(c, G.hi, 0.55);
			} else if (ax <= edge + bw) c = hex('#16181a');
			else {
				const t = r / 30;
				c = mixc(hex('#a3a9ae'), hex('#6c7277'), Math.max(0, t - 0.35) * 1.3);
				if (dy < -4 && ax > edge + bw + 1.5 && r < 26 && r > 16) c = mixc(c, hex('#c6cbcf'), 0.5);
			}
		}
		put(im, ox + x, oy + y, c);
	}
}

/** The selection screen: a dark green (or red) glass disc the alien icon glows on. */
function screen(im, ox, oy, red) {
	const ctr = red ? hex('#3a0d0a') : hex('#0f3d14'), edge = red ? hex('#160403') : hex('#041407'), ringc = red ? hex('#6a1a12') : hex('#1f6a26');
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const dx = x + 0.5 - 32, dy = y + 0.5 - 32, r = Math.hypot(dx, dy);
		if (r > 31.6) continue;
		let c;
		if (r > 29.6) c = hex('#141517');
		else {
			c = mixc(ctr, edge, Math.min(1, r / 29));
			if (Math.abs(r - 26.5) < 0.8) c = ringc;
			if (y % 4 === 0) c = shade(c, 1.18); // faint scanlines
		}
		put(im, ox + x, oy + y, c);
	}
}

/** A round lens (the cuff's button, the stud's light): 16x16 at (ox, oy). */
function lens(im, ox, oy, red) {
	const G = red ? RED : GREEN;
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const dx = x + 0.5 - 8, dy = y + 0.5 - 8, r = Math.hypot(dx, dy);
		if (r > 7.9) continue;
		let c = r > 6.4 ? hex('#2a2d31') : mixc(G.base, G.lo, Math.max(0, r / 6.4 - 0.4));
		if (Math.hypot(dx + 2.3, dy + 2.3) < 1.9) c = G.hi;
		put(im, ox + x, oy + y, c);
	}
}

function watch(red) {
	const im = img(128, 128);
	for (const s of Object.values(SW)) swatch(im, s, red);
	lens(im, 0, 48, red);
	screen(im, 64, 0, red);
	face(im, 64, 64, red);
	return im;
}

// ================================================================ the item icon (particle / fallback): the watch from the front
function itemIcon() {
	const im = img(16, 16);
	const C = hex('#3d4147'), D = hex('#1c1d20'), O = hex('#0e0f11'), W = hex('#e2e5e8'), Wd = hex('#8d9398');
	for (let y = 2; y < 14; y++) for (let x = 0; x < 16; x++) put(im, x, y, (y === 2 || y === 13 || x === 0 || x === 15) ? O : C);
	const f = img(64, 64); face(f, 0, 0, false);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const dx = x + 0.5 - 8, dy = y + 0.5 - 8, r = Math.hypot(dx, dy);
		if (r <= 6.6) put(im, x, y, r > 5.2 ? D : get(f, Math.floor((dx / 5.2) * 30 + 32), Math.floor((dy / 5.2) * 30 + 32)).slice(0, 3));
		else if (r <= 7.4) put(im, x, y, O);
	}
	for (const [x, y] of [[1, 3], [14, 3], [1, 12], [14, 12]]) put(im, x, y, W);
	for (const [x, y] of [[0, 4], [15, 4], [0, 11], [15, 11]]) put(im, x, y, Wd);
	for (const [x, y] of [[8, 1], [2, 8], [13, 8], [8, 14]]) put(im, x, y, GREEN.base);
	save(im, 'item/omnitrix.png');
}

// ================================================================ the dial icons
const S = 4; // supersampling
const OUTLINE = [10, 20, 11];
const MID = [168, 168, 168];
function icon(id, draw) {
	const N = 32 * S;
	const buf = new Uint8Array(N * N); // 0 empty, 1 fill, 2 mid, 3 dark
	const paint = (mode, test, bb) => {
		const [x0, y0, x1, y1] = bb.map(v => Math.round(v * S));
		for (let y = Math.max(0, y0 - 2); y < Math.min(N, y1 + 2); y++) for (let x = Math.max(0, x0 - 2); x < Math.min(N, x1 + 2); x++) {
			const px = (x + 0.5) / S, py = (y + 0.5) / S;
			if (!test(px, py)) continue;
			const i = y * N + x;
			if (mode === 'fill') buf[i] = 1;
			else if (mode === 'mid') buf[i] = 2;
			else if (mode === 'dark') { if (buf[i]) buf[i] = 3; }
			else if (mode === 'lit') { if (buf[i]) buf[i] = 1; }
			else if (mode === 'cut') buf[i] = 0;
		}
	};
	const g = {
		mode: 'fill',
		ell(cx, cy, rx, ry, rot = 0) {
			const c = Math.cos(rot * Math.PI / 180), s = Math.sin(rot * Math.PI / 180), m = Math.max(rx, ry);
			paint(this.mode, (x, y) => { const u = (x - cx) * c + (y - cy) * s, v = -(x - cx) * s + (y - cy) * c; return (u * u) / (rx * rx) + (v * v) / (ry * ry) <= 1; }, [cx - m, cy - m, cx + m, cy + m]);
			return this;
		},
		poly(pts) {
			let a = 99, b = 99, c = -99, d = -99;
			for (const p of pts) { a = Math.min(a, p[0]); b = Math.min(b, p[1]); c = Math.max(c, p[0]); d = Math.max(d, p[1]); }
			paint(this.mode, (x, y) => { let inside = false; for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) { const [xi, yi] = pts[i], [xj, yj] = pts[j]; if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside; } return inside; }, [a, b, c, d]);
			return this;
		},
		// a tapered capsule from (x0,y0,r0) to (x1,y1,r1)
		cap(x0, y0, x1, y1, r0, r1 = r0) {
			const dx = x1 - x0, dy = y1 - y0, L2 = dx * dx + dy * dy || 1e-6, m = Math.max(r0, r1);
			paint(this.mode, (x, y) => { let t = ((x - x0) * dx + (y - y0) * dy) / L2; t = Math.max(0, Math.min(1, t)); const qx = x0 + dx * t, qy = y0 + dy * t, r = r0 + (r1 - r0) * t; return (x - qx) ** 2 + (y - qy) ** 2 <= r * r; },
				[Math.min(x0, x1) - m, Math.min(y0, y1) - m, Math.max(x0, x1) + m, Math.max(y0, y1) + m]);
			return this;
		},
		// a chain of tapered capsules through [x, y, r] points
		chain(pts) { for (let i = 0; i + 1 < pts.length; i++) this.cap(pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], pts[i][2], pts[i + 1][2]); return this; },
		as(m) { this.mode = m; return this; },
	};
	draw(g);
	// downsample: a pixel is solid when half its samples are; it takes the commonest class (dark wins at 6/16 so small eyes survive)
	const cls = new Uint8Array(32 * 32);
	for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
		const n = [0, 0, 0, 0];
		for (let v = 0; v < S; v++) for (let u = 0; u < S; u++) n[buf[(y * S + v) * N + x * S + u]]++;
		const solid = n[1] + n[2] + n[3];
		if (solid * 2 < S * S) continue;
		cls[y * 32 + x] = n[3] >= 6 ? 3 : (n[2] > n[1] ? 2 : 1);
	}
	const im = img(32, 32);
	for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
		const k = cls[y * 32 + x];
		if (k) { put(im, x, y, k === 1 ? [255, 255, 255] : k === 2 ? MID : OUTLINE); continue; }
		let near = false;
		for (let v = -1; v <= 1 && !near; v++) for (let u = -1; u <= 1; u++) { const xx = x + u, yy = y + v; if (xx >= 0 && yy >= 0 && xx < 32 && yy < 32 && cls[yy * 32 + xx]) { near = true; break; } }
		if (near) put(im, x, y, OUTLINE);
	}
	save(im, 'gui/omnitrix/alien_' + id + '.png');
	ICONS.push([id, im]);
}
const ICONS = [];

function icons() {
	// Heatblast: a crown of flame for a head, a lean rock body, arms out from the sides
	icon('heatblast', g => {
		g.poly([[16, 0.6], [18.6, 5], [20.2, 1.6], [21.4, 6.4], [24.2, 4.4], [22.4, 9.6], [20.2, 13], [11.8, 13], [9.6, 9.6], [7.8, 4.4], [10.6, 6.4], [11.8, 1.6], [13.4, 5]]);
		g.as('mid').ell(16, 10.4, 3.1, 2.9);
		g.as('dark').ell(14.6, 10.2, 0.85, 0.7, 20).ell(17.4, 10.2, 0.85, 0.7, -20);
		g.as('fill').poly([[11.2, 13.8], [20.8, 13.8], [19.4, 18.5], [18, 22.4], [14, 22.4], [12.6, 18.5]]);
		g.chain([[12, 14.8, 1.7], [8.6, 18.6, 1.4], [6.2, 22.2, 1.35]]).ell(5.8, 23.2, 1.9, 1.9);
		g.chain([[20, 14.8, 1.7], [23.4, 18.6, 1.4], [25.8, 22.2, 1.35]]).ell(26.2, 23.2, 1.9, 1.9);
		g.cap(14.6, 22, 12.6, 30.4, 1.9, 1.6).cap(17.4, 22, 19.4, 30.4, 1.9, 1.6);
		g.as('mid').poly([[14.5, 16], [17.5, 16], [16, 20]]); // the chest's magma crack
	});
	// Four Arms: a small head on a huge torso, the upper pair flexed up, the lower pair out
	icon('four_arms', g => {
		g.ell(16, 5.6, 3.1, 3.3);
		g.as('dark').ell(14.4, 5.0, 0.7, 0.5).ell(17.6, 5.0, 0.7, 0.5).ell(14.6, 6.7, 0.6, 0.45).ell(17.4, 6.7, 0.6, 0.45);
		g.as('fill').poly([[8, 9.2], [24, 9.2], [21.2, 20.6], [10.8, 20.6]]);
		g.chain([[8.8, 10.4, 2.4], [4.6, 9.4, 2.0], [3.4, 4.6, 1.8]]).ell(3.4, 2.9, 2.3, 2.1);
		g.chain([[23.2, 10.4, 2.4], [27.4, 9.4, 2.0], [28.6, 4.6, 1.8]]).ell(28.6, 2.9, 2.3, 2.1);
		g.chain([[9.6, 15, 2.1], [5.4, 18.4, 1.8], [3.6, 22.4, 1.7]]).ell(3.4, 24.2, 2.3, 2.2);
		g.chain([[22.4, 15, 2.1], [26.6, 18.4, 1.8], [28.4, 22.4, 1.7]]).ell(28.6, 24.2, 2.3, 2.2);
		g.cap(13.2, 20, 11.8, 29.8, 2.6, 2.2).cap(18.8, 20, 20.2, 29.8, 2.6, 2.2);
		g.as('mid').poly([[13, 12], [19, 12], [16, 15.6]]);
	});
	// XLR8: a sprinting raptor in profile -- the crested helmet and visor, leaning forward, the long tail behind
	icon('xlr8', g => {
		g.ell(21.2, 8.2, 4.4, 3.3, -12).poly([[18.4, 6.2], [12.2, 3.0], [17.6, 10.2]]); // the helmet, swept back to a point
		g.as('dark').ell(23.4, 9.0, 2.3, 1.15, 10); // the visor
		g.as('fill').cap(19.6, 11, 14.6, 17, 2.0, 2.8);
		g.chain([[19, 13.2, 1.0], [22.8, 15.6, 0.8], [24.8, 14.6, 0.5]]);
		g.chain([[16.4, 18, 1.9], [20, 21.6, 1.4], [18.8, 26.8, 1.0], [22, 29.6, 0.9]]);
		g.chain([[13.8, 18.2, 1.8], [11, 22.4, 1.3], [11.8, 27, 1.0], [8.4, 29.4, 0.9]]);
		g.chain([[13.4, 16.4, 2.4], [8, 18, 1.6], [3.6, 18.6, 1.0], [0.8, 17.6, 0.5]]);
		g.ell(22.4, 29.9, 1.4, 1.1).ell(8, 29.7, 1.4, 1.1); // the balls on its feet
		g.as('mid').cap(9.6, 16, 9.6, 20, 0.5).cap(5.6, 17, 5.6, 20.2, 0.45); // the tail's stripes
	});
	// Diamondhead: a pointed crystal head, crystal shards jutting from the shoulders, blade hands
	icon('diamondhead', g => {
		g.poly([[16, 0.4], [20.4, 5.6], [19.6, 11], [16, 12.6], [12.4, 11], [11.6, 5.6]]);
		g.poly([[11.4, 12.2], [5.2, 2.6], [7.6, 13.4]]).poly([[20.6, 12.2], [26.8, 2.6], [24.4, 13.4]]);
		g.poly([[9, 12.6], [23, 12.6], [19.8, 22], [12.2, 22]]);
		g.chain([[10, 13.6, 1.9], [7.4, 19.6, 1.6]]).poly([[5.4, 19.4], [9.2, 19.6], [6.6, 26.6]]);
		g.chain([[22, 13.6, 1.9], [24.6, 19.6, 1.6]]).poly([[26.6, 19.4], [22.8, 19.6], [25.4, 26.6]]);
		g.cap(14.2, 21.6, 12.8, 30.4, 1.9, 1.7).cap(17.8, 21.6, 19.2, 30.4, 1.9, 1.7);
		// the facets: the right half of each crystal a shade darker
		g.as('mid').poly([[16, 0.4], [20.4, 5.6], [19.6, 11], [16, 12.6]]).poly([[20.6, 12.2], [26.8, 2.6], [24.4, 13.4]])
			.poly([[16, 12.6], [23, 12.6], [19.8, 22], [16, 22]]);
		g.as('dark').poly([[12.6, 7.2], [15.2, 8.0], [15, 8.8], [12.8, 8.2]]).poly([[19.4, 7.2], [16.8, 8.0], [17, 8.8], [19.2, 8.2]]);
	});
	// Upgrade: a smooth blobby body and head with one big round eye
	icon('upgrade', g => {
		g.ell(16, 7.4, 5.8, 5.4);
		g.as('dark').ell(16, 7.6, 3.5, 3.5);
		g.as('lit').ell(16, 7.6, 2.1, 2.1);
		g.as('dark').ell(16, 7.6, 0.9, 0.9);
		g.as('fill').ell(16, 17.4, 4.6, 5.4);
		g.chain([[12.6, 14.4, 1.9], [8.4, 18.8, 1.6], [6.6, 22.6, 1.5]]).ell(6.2, 23.8, 2.1, 1.9);
		g.chain([[19.4, 14.4, 1.9], [23.6, 18.8, 1.6], [25.4, 22.6, 1.5]]).ell(25.8, 23.8, 2.1, 1.9);
		g.cap(14.2, 21, 13.2, 29.6, 2.2, 1.9).cap(17.8, 21, 18.8, 29.6, 2.2, 1.9);
		g.ell(12.6, 30, 2.4, 1.3).ell(19.4, 30, 2.4, 1.3);
		g.as('mid').cap(16, 13.4, 16, 21, 0.45).cap(12.6, 17.2, 19.4, 17.2, 0.45); // the circuit lines
	});
	// Ghostfreak: hunched and floating, one eye, long clawed arms, a wisp of a tail for legs
	icon('ghostfreak', g => {
		g.ell(14.6, 6.6, 4.6, 4.7, -10);
		g.as('dark').ell(15.4, 6.4, 1.9, 2.0);
		g.as('lit').ell(15.8, 6.0, 0.7, 0.7);
		g.as('fill').ell(16, 13.6, 6.4, 4.8, -8);
		g.chain([[16, 16, 4.4], [17.6, 22, 2.8], [21.4, 26.6, 1.6], [26.6, 29.4, 0.5]]);
		g.chain([[10.4, 11.6, 1.6], [5.4, 15.6, 1.3], [4.2, 20.6, 1.1]]);
		g.cap(4.2, 20.6, 1.8, 25, 0.6, 0.35).cap(4.2, 20.6, 4.2, 25.8, 0.6, 0.35).cap(4.2, 20.6, 6.6, 24.8, 0.6, 0.35);
		g.chain([[21.6, 11.4, 1.6], [26.4, 14.6, 1.3], [28.2, 19.4, 1.1]]);
		g.cap(28.2, 19.4, 26.2, 24, 0.6, 0.35).cap(28.2, 19.4, 28.8, 24.6, 0.6, 0.35).cap(28.2, 19.4, 31, 23.4, 0.6, 0.35);
		g.as('dark').cap(14, 13, 18, 12.4, 0.45).cap(13.6, 15.2, 17.8, 14.8, 0.45); // the chest's black bands
	});
	// Stinkfly: wings spread wide, four eyes on stalks, a long stinger tail
	icon('stinkfly', g => {
		g.as('mid').ell(7.6, 12.4, 7.2, 2.8, -24).ell(24.4, 12.4, 7.2, 2.8, 24).ell(8.4, 18.2, 6.2, 2.2, 22).ell(23.6, 18.2, 6.2, 2.2, -22);
		g.as('fill').ell(16, 10.8, 3.0, 2.6).ell(16, 15.4, 3.2, 3.4);
		g.chain([[16, 17.6, 2.8], [16, 25, 1.7], [16, 30.6, 0.5]]);
		g.cap(14.6, 9, 11.4, 3.4, 0.6).cap(17.4, 9, 20.6, 3.4, 0.6).ell(11, 3, 1.7, 1.7).ell(21, 3, 1.7, 1.7);
		g.cap(14, 9.8, 8.6, 6.8, 0.55).cap(18, 9.8, 23.4, 6.8, 0.55).ell(8, 6.4, 1.5, 1.5).ell(24, 6.4, 1.5, 1.5);
		g.as('dark').ell(11, 3, 0.8, 0.8).ell(21, 3, 0.8, 0.8).ell(8, 6.4, 0.7, 0.7).ell(24, 6.4, 0.7, 0.7);
		g.as('fill').cap(14.4, 17, 11.6, 22.6, 0.5).cap(17.6, 17, 20.4, 22.6, 0.5);
		g.as('dark').cap(9, 11.6, 3, 9.8, 0.35).cap(23, 11.6, 29, 9.8, 0.35); // wing veins
	});
	// Wildvine: a flower bulb for a head with open petals, a thin body, long trailing vine arms
	icon('wildvine', g => {
		g.ell(16, 7.4, 3.6, 4.4);
		g.poly([[13, 5.6], [8.2, 0.8], [14.8, 3.2]]).poly([[19, 5.6], [23.8, 0.8], [17.2, 3.2]]).poly([[14.6, 3.4], [16, 0.2], [17.4, 3.4]]);
		g.poly([[12.6, 8], [7.4, 6.4], [12.8, 10.6]]).poly([[19.4, 8], [24.6, 6.4], [19.2, 10.6]]);
		g.as('dark').ell(16, 8.0, 1.25, 1.25);
		g.as('fill').cap(16, 11, 16, 13.6, 1.1).ell(16, 16.6, 2.6, 4.0);
		g.chain([[14, 14, 1.1], [8.8, 14.4, 0.95], [4.4, 18.4, 0.9], [3.2, 24.4, 0.85], [5.6, 28.4, 0.8], [8, 27.2, 0.6]]);
		g.chain([[18, 14, 1.1], [23.2, 13.2, 0.95], [27.6, 16.8, 0.9], [28.8, 22.8, 0.85], [26.6, 27, 0.8], [24.2, 26.2, 0.6]]);
		g.chain([[15, 20, 1.2], [13.6, 25, 1.0], [11, 30.6, 0.9]]).chain([[17, 20, 1.2], [18.4, 25, 1.0], [21, 30.6, 0.9]]);
		g.as('mid').ell(5.8, 16.2, 1.6, 0.8, -40).ell(26.4, 15, 1.6, 0.8, 40); // leaves on the vines
	});
	// Ripjaws: a huge-jawed fish-man in profile, jaws wide, a fin on the head, a forked tail
	icon('ripjaws', g => {
		g.ell(12, 10.6, 9.6, 6.6, -6);
		g.poly([[13, 4.8], [19.6, 1.4], [20.2, 8]]); // the head fin
		g.as('dark').poly([[2.0, 9.6], [15.6, 11.2], [2.4, 15.6]]); // the open mouth
		g.as('lit').poly([[3.4, 10], [4.6, 12], [5.8, 10.2]]).poly([[7.2, 10.4], [8.4, 12.2], [9.6, 10.6]]).poly([[11, 10.8], [12, 12.3], [13, 11]])
			.poly([[3.6, 15.2], [4.8, 13.2], [6, 15]]).poly([[7.4, 14.6], [8.6, 12.8], [9.8, 14.2]]).poly([[11.2, 13.8], [12.2, 12.4], [13.2, 13.4]]);
		g.as('dark').ell(14, 7, 1.1, 1.1);
		g.as('fill').chain([[16.4, 14.8, 3.8], [19.6, 21, 2.8], [22.2, 25.6, 1.8]]);
		g.poly([[21, 25], [30.8, 20.6], [27.2, 26.6], [31, 31], [21.4, 29]]); // the forked tail fin
		g.chain([[15, 16.6, 1.2], [10.8, 20.4, 1.0], [8.6, 22.6, 0.8]]).poly([[8.8, 21], [5.4, 23.4], [9.4, 24.6]]);
		g.as('mid').poly([[18.4, 13], [24.4, 15.4], [19, 17]]); // the back fin's shading
	});
	// Grey Matter: tiny, all head -- a huge round cranium with two great dark eyes
	icon('grey_matter', g => {
		g.ell(16, 11.2, 8.6, 8.0);
		g.as('dark').ell(12.2, 12.8, 2.9, 3.5, 18).ell(19.8, 12.8, 2.9, 3.5, -18);
		g.as('lit').ell(11.4, 11.6, 0.9, 0.9).ell(19, 11.6, 0.9, 0.9);
		g.as('mid').cap(14.4, 17.4, 17.6, 17.4, 0.45);
		g.as('fill').ell(16, 22.6, 2.6, 3.1);
		g.cap(14.4, 21.4, 10.6, 25, 0.75).cap(17.6, 21.4, 21.4, 25, 0.75);
		g.cap(15, 25, 13.6, 30.4, 0.9).cap(17, 25, 18.4, 30.4, 0.9).ell(13, 30.6, 1.5, 0.9).ell(19, 30.6, 1.5, 0.9);
	});
}

// ================================================================ the contact sheet (each icon tinted lime on the dial screen, big and at size)
function contactSheet() {
	if (!PREVIEW) return;
	const cell = 220, cellH = 290, pad = 16, cols = 5, rows = 2;
	const sheet = img(cols * cell + pad, rows * cellH + pad);
	for (let y = 0; y < sheet.h; y++) for (let x = 0; x < sheet.w; x++) put(sheet, x, y, [34, 36, 40]);
	const lime = [140, 255, 60];
	ICONS.forEach(([id, im], i) => {
		const ox = pad + (i % cols) * cell, oy = pad + Math.floor(i / cols) * cellH;
		const R = 100, cx = ox + 104, cy = oy + 104;
		for (let y = -R; y < R; y++) for (let x = -R; x < R; x++) {
			const r = Math.hypot(x + 0.5, y + 0.5);
			if (r > R) continue;
			let c = r > R - 8 ? [20, 21, 23] : mixc([15, 61, 20], [4, 20, 7], r / R);
			// the icon at 6x
			const ix = Math.floor((x + 96) / 6), iy = Math.floor((y + 96) / 6);
			if (ix >= 0 && iy >= 0 && ix < 32 && iy < 32) {
				const p = get(im, ix, iy);
				if (p[3]) c = [0, 1, 2].map(k => Math.round(p[k] * lime[k] / 255));
			}
			put(sheet, cx + x, cy + y, c);
		}
		// at 1x and 2x below-right
		for (const [sc, bx, by] of [[2, ox + 24, oy + 212], [1, ox + 120, oy + 228]]) {
			for (let y = 0; y < 32 * sc; y++) for (let x = 0; x < 32 * sc; x++) {
				const p = get(im, Math.floor(x / sc), Math.floor(y / sc));
				put(sheet, bx + x, by + y, p[3] ? [0, 1, 2].map(k => Math.round(p[k] * lime[k] / 255)) : [8, 30, 11]);
			}
		}
	});
	PNG.write(path.join(PREVIEW, 'omnitrix_model_icons_sheet.png'), sheet.w, sheet.h, sheet.px);
}

save(watch(false), 'entity/omnitrix/watch.png');
save(watch(true), 'entity/omnitrix/watch_red.png');
itemIcon();
icons();
contactSheet();
console.log('omnitrix watch + icons written');
