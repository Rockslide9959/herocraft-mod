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

// --- v0.17.1: Ripjaws, Grey Matter, Diamondhead, Ghostfreak -- the user's planetminecraft skins as-is ------------------------
for (const n of ['ripjaws', 'grey_matter', 'diamondhead', 'ghostfreak']) {
	write(n + '.png', 64, 64, dec(path.join(__dirname, n + '_src.png')).px);
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
// v0.17.1 (user, second pass: "he looks orange right now, maybe remove the animated fire texture on his body completely of
// you cant make it match his colours"): the skin keeps its own colours; the overlay only breathes HIS pale yellow brighter
const skin = Buffer.from(hb.px);
write('heatblast.png', 64, 64, skin);
const nearPale = (x, y) => {
	for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
		const xx = x + dx, yy = y + dy;
		if (xx >= 0 && yy >= 0 && xx < 64 && yy < 64 && isPale(hb.px, (yy * 64 + xx) * 4)) return true;
	}
	return false;
};
const FRAMES = 32;
for (let f = 0; f < FRAMES; f++) {
	const px = Buffer.alloc(64 * 64 * 4);
	const scroll = f / FRAMES; // one full noise period per loop
	for (let y = 0; y < 64; y++) {
		for (let x = 0; x < 64; x++) {
			const o = (y * 64 + x) * 4;
			const [r, g, b, a] = hb.px.slice(o, o + 4);
			const near = nearPale(x, y);
			if (a === 0) continue;
			const pale = isPale(hb.px, o); // the cracks, the fire areas and the head
			const rock = isRock(hb.px, o); // the dark red rock
			let rgb = [0, 0, 0];
			if (pale) {
				// his own pale yellow, a little brighter in a slow wave up each limb (the head steadier), never orange: the
				// fire areas look alive without changing colour
				const wave = 0.5 + 0.5 * Math.sin((scroll * 2 + (y % 16) / 16) * Math.PI * 2);
				const n = vnoise(x * 0.6, y * 0.6 + scroll * 38.4, 38, 38, 3);
				const k = isHead(x, y) ? 0.12 + 0.05 * wave : 0.08 + 0.16 * wave + 0.06 * (n - 0.5);
				rgb = [r * k, g * k, b * k * 0.85];
			} else if (rock && near) {
				// the rock right at the cracks: the faintest warm ember in the rock's own red
				const n1 = vnoise(x * 0.5, y * 0.5 + scroll * 32, 32, 32, 11);
				const k = smooth(0.55, 0.95, n1) * 0.35;
				rgb = [r * k, g * k, b * k];
			}
			px[o] = clamp(Math.round(rgb[0]), 0, 255);
			px[o + 1] = clamp(Math.round(rgb[1]), 0, 255);
			px[o + 2] = clamp(Math.round(rgb[2]), 0, 255);
			px[o + 3] = 255;
		}
	}
	write(`heatblast_glow_${String(f).padStart(2, '0')}.png`, 64, 64, px);
}

console.log('ripjaws / grey_matter / diamondhead / ghostfreak.png, four_arms.png, heatblast.png, heatblast_glow_00..31.png written');

// --- v0.17.1: the extras sheets retinted to the new skins (shapes unchanged; the originals are *_old.png beside this) -------
{
	const lum = (r, g, b) => (0.3 * r + 0.59 * g + 0.11 * b) / 255;
	const ramp = (stops, t) => {
		t = clamp(t);
		for (let i = 0; i < stops.length - 1; i++) {
			const [a, ca] = stops[i], [b, cb] = stops[i + 1];
			if (t <= b) { const k = (t - a) / (b - a); return [0, 1, 2].map(c => Math.round(ca[c] + (cb[c] - ca[c]) * k)); }
		}
		return stops[stops.length - 1][1];
	};
	// Ripjaws: his blue-grey jaw, head plates and fins in the new skin's mint-and-pale (the red mouth, yellow and teeth kept)
	const rj = dec(path.join(__dirname, 'ripjaws_extras_old.png'));
	const MINT = [[0, [24, 52, 40]], [0.45, [104, 150, 122]], [0.75, [178, 214, 190]], [1, [236, 250, 238]]];
	for (let i = 0; i < rj.px.length; i += 4) {
		const [r, g, b, a] = rj.px.slice(i, i + 4);
		if (!a) continue;
		if (b > r + 12 && b >= g - 6) { // the blue-greys
			const [nr, ng, nb] = ramp(MINT, lum(r, g, b) * 1.32);
			rj.px[i] = nr; rj.px[i + 1] = ng; rj.px[i + 2] = nb;
		}
	}
	write('ripjaws_extras.png', rj.w, rj.h, rj.px);
	// Ghostfreak: the white skirt with black dashes in his skin's lavender with dark purple marks (the purple eye kept)
	const gf = dec(path.join(__dirname, 'ghostfreak_extras_old.png'));
	const LAV = [[0, [21, 12, 28]], [0.35, [60, 40, 72]], [0.7, [176, 166, 190]], [1, [222, 214, 232]]];
	for (let i = 0; i < gf.px.length; i += 4) {
		const [r, g, b, a] = gf.px.slice(i, i + 4);
		if (!a) continue;
		const sat = Math.max(r, g, b) - Math.min(r, g, b);
		if (sat < 28) { // the greys, whites and blacks
			const [nr, ng, nb] = ramp(LAV, lum(r, g, b));
			gf.px[i] = nr; gf.px[i + 1] = ng; gf.px[i + 2] = nb;
		}
	}
	write('ghostfreak_extras.png', gf.w, gf.h, gf.px);
}

// --- v0.17.1: Upgrade on the user's minecraftskins.com skin; his glow sheet is the skin's own green circuit lines (and eye) -----
{
	const up = dec(path.join(__dirname, 'upgrade_src.png'));
	write('upgrade.png', 64, 64, up.px);
	const glow = Buffer.alloc(64 * 64 * 4);
	for (let i = 0; i < up.px.length; i += 4) {
		const [r, g, b, a] = up.px.slice(i, i + 4);
		if (!a) continue;
		// the bright greens: the circuit lines and the eye (drawn additively, so the rest stays black)
		if (g > 150 && g > r + 40 && g > b + 40) {
			glow[i] = r; glow[i + 1] = g; glow[i + 2] = b;
		}
		glow[i + 3] = 255;
	}
	write('upgrade_glow.png', 64, 64, glow);
}

// --- v0.17.1 (user: "make his back spikes match the green colour of the rest of his diamond body"): Diamondhead's crystal sheet
// remapped by brightness onto his new skin's own greens (90,188,113 .. 176,242,204); alpha kept, so the crystals stay see-through
{
	const dh = dec(path.join(__dirname, 'diamondhead_extras_old.png'));
	const GREEN = [[0, [40, 110, 70]], [0.3, [90, 188, 113]], [0.55, [121, 211, 156]], [0.8, [150, 229, 184]], [1, [190, 248, 214]]];
	const lum = (r, g, b) => (0.3 * r + 0.59 * g + 0.11 * b) / 255;
	const ramp = (stops, t) => {
		t = clamp(t);
		for (let i = 0; i < stops.length - 1; i++) {
			const [a, ca] = stops[i], [b, cb] = stops[i + 1];
			if (t <= b) { const k = (t - a) / (b - a); return [0, 1, 2].map(c => Math.round(ca[c] + (cb[c] - ca[c]) * k)); }
		}
		return stops[stops.length - 1][1];
	};
	let lo = 1, hi = 0;
	for (let i = 0; i < dh.px.length; i += 4) if (dh.px[i + 3]) { const l = lum(dh.px[i], dh.px[i + 1], dh.px[i + 2]); lo = Math.min(lo, l); hi = Math.max(hi, l); }
	for (let i = 0; i < dh.px.length; i += 4) {
		if (!dh.px[i + 3]) continue;
		const t = (lum(dh.px[i], dh.px[i + 1], dh.px[i + 2]) - lo) / Math.max(1e-3, hi - lo);
		const [r, g, b] = ramp(GREEN, t);
		dh.px[i] = r; dh.px[i + 1] = g; dh.px[i + 2] = b;
	}
	write('diamondhead_extras.png', dh.w, dh.h, dh.px);
}
