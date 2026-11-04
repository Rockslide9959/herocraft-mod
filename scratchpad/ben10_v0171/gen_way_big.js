// v0.17.2 Way Big textures (user: "add waybig too please. Use this skin: https://www.planetminecraft.com/skin/way-big-ben-10-4631092/ ...
// Remove the pixel omnitrix from the skin and place our omnitrix badge over it please"):
//   textures/entity/omnitrix/way_big.png      the skin as downloaded (waybig/waybig_src.png). Every face was checked: the skin
//                                             paints NO Omnitrix symbol (the chest is his red stripe, the head stripe is his fin,
//                                             the dark bands are wrists and collar), so nothing is painted out; WayBigClient.badge()
//                                             puts the mod's badge in the middle of his chest, where the show has it.
//   gui/omnitrix/dial_way_big(_red).png        a placeholder dial icon: his silhouette (the head fin, broad shoulders) on four_arms'
//   font/omnitrix_dial/way_big.png             diamond frame -- replace with the user's art through gen_omnitrix_dial_icons.js.
// node scratchpad/ben10_v0171/gen_way_big.js   (from the repo root)
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const { dec } = require('./png_view.js');

const RES = path.join(__dirname, '../../src/main/resources/assets/projecthero/textures');
const write = (f, w, h, px) => fs.writeFileSync(path.join(RES, f), L.encodePng(w, h, px));

const src = dec(path.join(__dirname, 'waybig', 'waybig_src.png'));
write('entity/omnitrix/way_big.png', 64, 64, src.px);

function inDiamond(x, y) { return Math.abs(x + 0.5 - 32) / 21.5 + Math.abs(y + 0.5 - 32) / 28.5 < 1; }
function silhouette(x, y) {
	const X = x + 0.5, Y = y + 0.5;
	const ell = (cx, cy, rx, ry) => ((X - cx) / rx) ** 2 + ((Y - cy) / ry) ** 2 <= 1;
	const rect = (x0, y0, x1, y1) => X >= x0 && X <= x1 && Y >= y0 && Y <= y1;
	return rect(30.6, 8, 33.4, 20) || ell(32, 9, 1.4, 2.2) // the head fin, tall and thin
		|| ell(32, 21.5, 4.3, 4.8) // the head
		|| ell(23, 27.5, 5.6, 3.6) || ell(41, 27.5, 5.6, 3.6) // the shoulders
		|| rect(18.5, 24, 21, 28) || rect(43, 24, 45.5, 28) // their raised edges
		|| ell(32, 33.5, 8.2, 8.5) // the chest
		|| ell(20.5, 37, 2.8, 9) || ell(43.5, 37, 2.8, 9) // the arms
		|| ell(20.5, 46.5, 2.6, 2) || ell(43.5, 46.5, 2.6, 2) // the hands
		|| rect(27.4, 40, 31, 52.5) || rect(33, 40, 36.6, 52.5) // the legs
		|| ell(29, 53.5, 3.4, 1.6) || ell(35, 53.5, 3.4, 1.6); // the feet
}
for (const red of [false, true]) {
	const base = dec(path.join(RES, 'gui/omnitrix/dial_four_arms' + (red ? '_red' : '') + '.png'));
	const px = Buffer.from(base.px);
	const fill = red ? [0xF2, 0x2A, 0x2A] : [137, 235, 57];
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
	write('gui/omnitrix/dial_way_big' + (red ? '_red' : '') + '.png', 64, 64, px);
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
		write('font/omnitrix_dial/way_big.png', 32, 32, g);
	}
}
const BLUR = JSON.stringify({ texture: { blur: true, clamp: true } }, null, 2) + '\n';
for (const n of ['dial_way_big', 'dial_way_big_red']) fs.writeFileSync(path.join(RES, 'gui/omnitrix', n + '.png.mcmeta'), BLUR);
console.log('way big textures written');
