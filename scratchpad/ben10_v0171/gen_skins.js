// v0.17.1 Four Arms + Heatblast textures from the user's two planetminecraft skins (four_arms_src.png, heatblast_src.png).
//   four_arms.png              the skin as-is (RGBA)
//   heatblast.png              the skin as-is
//   heatblast_glow_00..31.png  the burning overlay, drawn additively (RenderType.eyes) over the whole model: lava pulsing
//                              through the skin's pale cracks, flames licking up the dark rock, the pale head lit from within.
//                              Black = no glow. Loops every 32 frames (the noise is periodic in y and the scroll is one period).
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const { dec } = require('./png_view.js');

const OUT = path.join(__dirname, '../../src/main/resources/assets/projecthero/textures/entity/omnitrix');
const write = (f, w, h, px) => fs.writeFileSync(path.join(OUT, f), L.encodePng(w, h, px));
const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const smooth = (a, b, v) => { const t = clamp((v - a) / (b - a)); return t * t * (3 - 2 * t); };

// periodic value noise: lattice (px, py) cells, wrapping after (px, py) cells
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
// fire colour ramp, 0..1 -> rgb (black at 0, for additive blending)
function fire(v) {
	const stops = [[0, 0, 0, 0], [0.25, 90, 8, 0], [0.5, 210, 50, 5], [0.72, 255, 130, 20], [0.88, 255, 210, 70], [1, 255, 250, 200]];
	v = clamp(v);
	for (let i = 0; i < stops.length - 1; i++) {
		const a = stops[i], b = stops[i + 1];
		if (v <= b[0]) { const k = (v - a[0]) / (b[0] - a[0]); return [0, 1, 2].map(c => Math.round(a[c + 1] + (b[c + 1] - a[c + 1]) * k)); }
	}
	return [255, 250, 200];
}

// --- Four Arms -------------------------------------------------------------------------------------------------------
{
	const s = dec(path.join(__dirname, 'four_arms_src.png'));
	write('four_arms.png', 64, 64, s.px);
}

// --- Heatblast -------------------------------------------------------------------------------------------------------
const hb = dec(path.join(__dirname, 'heatblast_src.png'));
// which skin rectangles are the head (base + hat): lit from within instead of burning
const isHead = (x, y) => y < 16;
const isPale = (px, o) => px[o + 3] && px[o] > 230 && px[o + 1] > 190 && px[o + 2] < 180;
const isRock = (px, o) => px[o + 3] && px[o] > 90 && px[o] < 170 && px[o + 1] < 60;
const nearRock = (x, y) => {
	for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
		const xx = x + dx, yy = y + dy;
		if (xx >= 0 && yy >= 0 && xx < 64 && yy < 64 && isRock(hb.px, (yy * 64 + xx) * 4)) return true;
	}
	return false;
};
// v0.17.1 (user: "add like a burning fire texture to the rest of his body so he doesnt just look kinda plain"): the skin's
// flat pale body areas painted as fire -- a rising flame pattern, yellow at the hottest, deep orange where it meets the rock
const skin = Buffer.from(hb.px);
for (let y = 16; y < 64; y++) {
	for (let x = 0; x < 64; x++) {
		const o = (y * 64 + x) * 4;
		if (!isPale(hb.px, o)) continue;
		const n = vnoise(x * 0.45, y * 0.32, 29, 21, 31) * 0.65 + vnoise(x * 0.9, y * 0.7, 58, 45, 32) * 0.35;
		let v = 0.5 + (n - 0.5) * 0.8 + 0.12;
		if (nearRock(x, y)) v -= 0.18;
		const [r, g, b] = fire(clamp(v, 0.42, 0.97));
		skin[o] = r; skin[o + 1] = g; skin[o + 2] = b;
	}
}
write('heatblast.png', 64, 64, skin);
const FRAMES = 32;
for (let f = 0; f < FRAMES; f++) {
	const px = Buffer.alloc(64 * 64 * 4);
	const scroll = f / FRAMES; // one full noise period per loop
	for (let y = 0; y < 64; y++) {
		for (let x = 0; x < 64; x++) {
			const o = (y * 64 + x) * 4;
			const [r, g, b, a] = hb.px.slice(o, o + 4);
			const near = nearRock(x, y);
			if (a === 0) continue;
			const pale = isPale(hb.px, o); // the cracks, the fire areas and the head
			const rock = isRock(hb.px, o); // the dark red rock
			let rgb = [0, 0, 0];
			if (isHead(x, y)) {
				if (pale) {
					// the flame head glows from inside, a slow breathing flicker
					const k = 0.2 + 0.07 * Math.sin((f / FRAMES) * Math.PI * 2 * 2 + x * 0.4) + 0.08 * (vnoise(x * 0.5, y * 0.5 + scroll * 16, 32, 16, 7) - 0.5);
					rgb = [r * k, g * k * 0.92, b * k * 0.6];
				}
			} else if (pale) {
				// lava pulsing through the cracks: a wave travelling up each limb, plus a flicker
				const wave = 0.5 + 0.5 * Math.sin((scroll * 2 + (y % 16) / 16) * Math.PI * 2);
				const n = vnoise(x * 0.6, y * 0.6 + scroll * 38.4, 38, 38, 3);
				// (kept dim: it only moves the fire painted on the skin, never washes it out)
				rgb = fire(0.55 + 0.3 * wave + 0.2 * (n - 0.5)).map(c => c * 0.38);
			} else if (rock) {
				// flames licking up the rock: two octaves of noise rising (v grows downward on every side face, so the
				// pattern moves to smaller y), the brightest tongues hugging the cracks
				const n1 = vnoise(x * 0.5, y * 0.5 + scroll * 32, 32, 32, 11);
				const n2 = vnoise(x * 1.0, y * 1.0 + scroll * 64, 64, 64, 12);
				const v = smooth(0.6, 0.98, n1 * 0.7 + n2 * 0.3);
				rgb = fire(v * 0.66).map(c => c * 0.8);
			}
			px[o] = clamp(Math.round(rgb[0]), 0, 255);
			px[o + 1] = clamp(Math.round(rgb[1]), 0, 255);
			px[o + 2] = clamp(Math.round(rgb[2]), 0, 255);
			px[o + 3] = 255;
		}
	}
	write(`heatblast_glow_${String(f).padStart(2, '0')}.png`, 64, 64, px);
}

console.log('four_arms.png, heatblast.png, heatblast_glow_00..31.png written');
