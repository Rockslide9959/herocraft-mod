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

// --- the ball's shell: a 64x32 wrap (longitude across, latitude down) for the client's sphere -------------------------------
// two bands of his gold plates in a brick pattern, black-edged, white seams between them and white caps at the poles (his white
// body showing between the plates, as the show's ball)
{
	const W = 64, H = 32;
	const px = Buffer.alloc(W * H * 4);
	const set = (x, y, c) => { const o = (y * W + x) * 4; px[o] = c[0]; px[o + 1] = c[1]; px[o + 2] = c[2]; px[o + 3] = 255; };
	const WHITE = [255, 255, 255], SHADE = [0xd8, 0xd8, 0xd8], BLACK = [0, 0, 0], GOLD = [0xda, 0xbe, 0x0b], DARK = [0xa8, 0x94, 0x11],
		LIGHT = [0xf0, 0xd4, 0x3b];
	const bands = [[3, 15, 0], [17, 29, 4]]; // rows y0..y1 inclusive, column offset (the brick shift)
	for (let y = 0; y < H; y++) {
		for (let x = 0; x < W; x++) {
			let c = (y < 2 || y > H - 3) ? SHADE : WHITE;
			for (const [y0, y1, off] of bands) {
				if (y < y0 || y > y1) continue;
				const cx = ((x + off) % 8 + 8) % 8; // 8 plates round the ball
				if (cx === 0) { c = WHITE; break; } // the white seam between two plates
				const edge = y === y0 || y === y1 || cx === 1 || cx === 7;
				if (edge) { c = BLACK; break; }
				c = (y === y0 + 1 || cx === 2) ? LIGHT : (y >= y1 - 2 || cx === 6) ? DARK : GOLD;
			}
			set(x, y, c);
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
