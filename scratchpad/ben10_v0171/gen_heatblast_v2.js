// v0.17.2 Heatblast's second skin (user: "replace our heatblast skin with this one please:
// https://www.planetminecraft.com/skin/heatblast-ben-10-5205382/") -- heatblast_v2/heatblast2_src.png.
//   heatblast.png              the skin, its painted Omnitrix (jacket 23..24 x 37..38: a grey/white 2x2) cleared so the rock
//                              under it shows; HeatblastClient.badge() sits on that spot
//   heatblast_doused.png       put out: the flame-yellow cracks dark ember red, the rock charcoal maroon (the mapping the first
//                              skin's doused sheet used, read back off it)
//   heatblast_glow_00..31.png  the burning overlay (RenderType.eyes): his pale yellow breathing brighter up each limb, a faint
//                              ember in the rock beside the cracks -- as gen_skins.js did for the first skin, the rock test
//                              widened for this skin's darker reds
// Run AFTER gen_skins.js (whose Heatblast block still writes the first skin).
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const { dec } = require('./png_view.js');

const OUT = path.join(__dirname, '../../src/main/resources/assets/projecthero/textures/entity/omnitrix');
const write = (f, px) => fs.writeFileSync(path.join(OUT, f), L.encodePng(64, 64, px));
const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const smooth = (a, b, v) => { const t = clamp((v - a) / (b - a)); return t * t * (3 - 2 * t); };
function hash(x, y, s) { let h = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; h = (h ^ (h >>> 13)) * 1274126177 | 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; }
function vnoise(x, y, px, py, seed) {
	const xi = Math.floor(x), yi = Math.floor(y), fx = x - xi, fy = y - yi;
	const w = (a, n) => ((a % n) + n) % n;
	const g = (i, j) => hash(w(i, px), w(j, py), seed);
	const sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy);
	const a = g(xi, yi) + (g(xi + 1, yi) - g(xi, yi)) * sx;
	const b = g(xi, yi + 1) + (g(xi + 1, yi + 1) - g(xi, yi + 1)) * sx;
	return a + (b - a) * sy;
}

const src = dec(path.join(__dirname, 'heatblast_v2', 'heatblast2_src.png'));
const skin = Buffer.from(src.px);
const at = (x, y) => (y * 64 + x) * 4;
for (let y = 37; y <= 38; y++) for (let x = 23; x <= 24; x++) skin[at(x, y) + 3] = 0;
write('heatblast.png', skin);

const isPale = o => skin[o + 3] && skin[o] > 230 && skin[o + 1] > 190 && skin[o + 2] < 200;
const isRock = o => skin[o + 3] && skin[o] > 55 && skin[o] < 170 && skin[o + 1] < 60;

// doused
const doused = Buffer.from(skin);
for (let o = 0; o < doused.length; o += 4) {
	if (!doused[o + 3]) continue;
	const [r, g] = skin.slice(o, o + 3);
	let c;
	if (isPale(o)) c = [0.53 * g, 0.142 * g, 0.105 * g];
	else if (isRock(o)) c = [0.44 * r + 8, 0.105 * r + 18, 0.12 * r + 20];
	else continue;
	for (let k = 0; k < 3; k++) doused[o + k] = clamp(Math.round(c[k]), 0, 255);
}
write('heatblast_doused.png', doused);

// the burning overlay
const near = (x, y, test) => {
	for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
		const xx = x + dx, yy = y + dy;
		if (xx >= 0 && yy >= 0 && xx < 64 && yy < 64 && test(at(xx, yy))) return true;
	}
	return false;
};
const FRAMES = 32;
for (let f = 0; f < FRAMES; f++) {
	const px = Buffer.alloc(64 * 64 * 4);
	const scroll = f / FRAMES;
	for (let y = 0; y < 64; y++) {
		for (let x = 0; x < 64; x++) {
			const o = at(x, y);
			if (!skin[o + 3]) continue;
			const [r, g, b] = skin.slice(o, o + 3);
			let rgb = [0, 0, 0];
			if (isPale(o)) {
				const wave = 0.5 + 0.5 * Math.sin((scroll * 2 + (y % 16) / 16) * Math.PI * 2);
				const n = vnoise(x * 0.6, y * 0.6 + scroll * 38.4, 38, 38, 3);
				const k = y < 16 ? 0.12 + 0.05 * wave : 0.08 + 0.16 * wave + 0.06 * (n - 0.5);
				rgb = [r * k, g * k, b * k * 0.85];
			} else if (isRock(o) && near(x, y, isPale)) {
				const n1 = vnoise(x * 0.5, y * 0.5 + scroll * 32, 32, 32, 11);
				// this skin's rock is darker than the first one's: a little more ember to read at all
				const k = smooth(0.55, 0.95, n1) * 0.5;
				rgb = [r * k, g * k, b * k];
			}
			px[o] = clamp(Math.round(rgb[0]), 0, 255);
			px[o + 1] = clamp(Math.round(rgb[1]), 0, 255);
			px[o + 2] = clamp(Math.round(rgb[2]), 0, 255);
			px[o + 3] = 255;
		}
	}
	write(`heatblast_glow_${String(f).padStart(2, '0')}.png`, px);
}
console.log('heatblast.png, heatblast_doused.png, heatblast_glow_00..31.png written');
