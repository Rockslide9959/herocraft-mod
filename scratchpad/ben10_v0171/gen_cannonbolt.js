// v0.17.2 Cannonbolt (user: "build canonbolt alien for the prototype omnitrix please. Use this skin:
// https://www.planetminecraft.com/skin/cannonbolt-original-series/"):
//   textures/entity/omnitrix/cannonbolt.png        the user's skin as-is (headless: his face is painted on the torso, as in the show)
//   textures/entity/omnitrix/cannonbolt_ball.png   one shell plate (32x32) for the rolled-up ball: white rim, black seam, his gold
//   textures/gui/omnitrix/dial_cannonbolt(_red).png the watch-face icon, drawn over four_arms' diamond (same frame, his silhouette)
//   textures/font/omnitrix_dial/cannonbolt.png     the 32x32 inline glyph (U+E014)
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const { dec } = require('./png_view.js');

const RES = path.join(__dirname, '../../src/main/resources/assets/projecthero/textures');
const write = (f, w, h, px) => fs.writeFileSync(path.join(RES, f), L.encodePng(w, h, px));

// --- the skin -----------------------------------------------------------------------------------------------------------------
write('entity/omnitrix/cannonbolt.png', 64, 64, dec(path.join(__dirname, 'cannonbolt_src.png')).px);

// --- the ball's shell: a 256x128 wrap (longitude across, latitude down) for the client's UV sphere ----------------------------
// v0.17.2 (user: "this is what canonbolts ball form looks like, make it look like this please" -- cannonbolt_ball_ref.png): solid
// amber plates, no white, thick black seams like a basketball's -- a big S-wave round the middle, a round plate edge on each side
// (the circle-in-circle panel) and one seam over the top. The seams are traced on the real sphere, with the exact mapping
// CannonboltClient.sphere uses (v down = theta from the BOTTOM pole, y = -cos(theta); u round = phi), so they stay round.
{
	const W = 256, H = 128;
	const px = Buffer.alloc(W * H * 4);
	const P = (theta, phi) => [Math.sin(theta) * Math.cos(phi), -Math.cos(theta), Math.sin(theta) * Math.sin(phi)];
	const fromLatLon = (lat, phi) => P(Math.PI / 2 + lat, phi); // lat + = up
	const norm = v => { const l = Math.hypot(v[0], v[1], v[2]); return v.map(c => c / l); };
	const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
	const curves = [];
	// 1. the S-wave round the middle (a tennis-ball seam)
	{ const pts = []; for (let i = 0; i < 720; i++) { const f = i / 720 * 2 * Math.PI; pts.push(fromLatLon(0.62 * Math.sin(2 * f), f)); } curves.push(pts); }
	// 2-3. a round plate edge in each big lobe the wave leaves
	for (const [lat, lon] of [[0.32, 0.75 * Math.PI], [-0.32, 1.75 * Math.PI]]) {
		const c = fromLatLon(lat, lon);
		const a = norm(cross(c, [0, 1, 0])), b = cross(c, a), r = 0.5;
		const pts = [];
		for (let i = 0; i < 360; i++) {
			const t = i / 360 * 2 * Math.PI;
			pts.push(norm(c.map((v, k) => v * Math.cos(r) + (a[k] * Math.cos(t) + b[k] * Math.sin(t)) * Math.sin(r))));
		}
		curves.push(pts);
	}
	// 4. one seam over the top: a great circle through both poles, between the two plate circles
	{ const pts = []; for (let i = 0; i < 720; i++) { const t = i / 720 * 2 * Math.PI; pts.push([Math.cos(t) * Math.cos(0.25 * Math.PI), Math.sin(t), Math.cos(t) * Math.sin(0.25 * Math.PI)]); } curves.push(pts); }
	const AMBER = [0xF0, 0xB0, 0x30], DEEP = [0xC8, 0x86, 0x1C], BLACK = [0x12, 0x0C, 0x06];
	const SEAM = 0.075, SOFT = 0.14; // radians: the black seam, then the plate's darker lip beside it
	for (let y = 0; y < H; y++) {
		for (let x = 0; x < W; x++) {
			const q = P(Math.PI * (y + 0.5) / H, 2 * Math.PI * (x + 0.5) / W);
			let best = 9;
			for (const pts of curves) for (const s of pts) {
				const d = Math.acos(Math.max(-1, Math.min(1, q[0] * s[0] + q[1] * s[1] + q[2] * s[2])));
				if (d < best) best = d;
			}
			let c;
			if (best < SEAM) c = BLACK;
			else if (best < SOFT) { const k = (best - SEAM) / (SOFT - SEAM); c = DEEP.map((v, i) => Math.round(v + (AMBER[i] - v) * k)); }
			else c = AMBER;
			const o = (y * W + x) * 4; px[o] = c[0]; px[o + 1] = c[1]; px[o + 2] = c[2]; px[o + 3] = 255;
		}
	}
	write('entity/omnitrix/cannonbolt_ball.png', W, H, px);
}

// --- the dial icons -------------------------------------------------------------------------------------------------------------
function inDiamond(x, y) { return Math.abs(x + 0.5 - 32) / 21.5 + Math.abs(y + 0.5 - 32) / 28.5 < 1; }
function silhouette(x, y) {
	const X = x + 0.5, Y = y + 0.5;
	const ell = (cx, cy, rx, ry) => ((X - cx) / rx) ** 2 + ((Y - cy) / ry) ** 2 <= 1;
	const rect = (x0, y0, x1, y1) => X >= x0 && X <= x1 && Y >= y0 && Y <= y1;
	return ell(32, 31, 9.5, 11.5) // the round torso
		|| ell(23.5, 24, 5.6, 5.2) || ell(40.5, 24, 5.6, 5.2) // the shoulder plates
		|| ell(21, 34, 2.6, 8) || ell(43, 34, 2.6, 8) // the arms
		|| ell(21, 42, 2.4, 1.8) || ell(43, 42, 2.4, 1.8) // the hands
		|| rect(27.5, 40, 30.8, 49) || rect(33.2, 40, 36.5, 49) // the stubby legs
		|| ell(28.5, 50, 3.2, 1.6) || ell(35.5, 50, 3.2, 1.6); // the feet
}
for (const red of [false, true]) {
	const base = dec(path.join(RES, 'gui/omnitrix/dial_four_arms' + (red ? '_red' : '') + '.png'));
	const px = Buffer.from(base.px);
	const fill = red ? [0xF2, 0x2A, 0x2A] : [137, 235, 57];
	// the field colour sampled from four_arms' own icon (a clean pixel of the diamond)
	{
		const o = (32 * 64 + 12) * 4;
		if (base.px[o + 3] > 200) { fill[0] = base.px[o]; fill[1] = base.px[o + 1]; fill[2] = base.px[o + 2]; }
	}
	for (let y = 0; y < 64; y++) {
		for (let x = 0; x < 64; x++) {
			if (!inDiamond(x, y)) continue;
			const o = (y * 64 + x) * 4;
			const c = silhouette(x, y) ? [0, 0, 0] : fill;
			px[o] = c[0]; px[o + 1] = c[1]; px[o + 2] = c[2]; px[o + 3] = 255;
		}
	}
	write('gui/omnitrix/dial_cannonbolt' + (red ? '_red' : '') + '.png', 64, 64, px);
	if (!red) {
		const g = Buffer.alloc(32 * 32 * 4);
		for (let y = 0; y < 32; y++) {
			for (let x = 0; x < 32; x++) {
				for (let c = 0; c < 4; c++) {
					let s = 0;
					for (const [dx, dy] of [[0, 0], [1, 0], [0, 1], [1, 1]]) s += px[((y * 2 + dy) * 64 + x * 2 + dx) * 4 + c];
					g[(y * 32 + x) * 4 + c] = Math.round(s / 4);
				}
			}
		}
		write('font/omnitrix_dial/cannonbolt.png', 32, 32, g);
	}
}
console.log('cannonbolt textures written');
