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

// --- v0.17.1 (user: "use this for the stinkfly model" -- a minecraftskins.com skin): Stinkfly is a player-model alien again on
// that skin (the custom GeckoLib body from scratchpad/stinkfly_v0171 is retired; its generator would overwrite this file)
write('stinkfly.png', 64, 64, dec(path.join(__dirname, 'stinkfly_src.png')).px);

// --- v0.17.1 (user: "for xlr8s helmet can you take the helmet from this skin" -- planetminecraft xlr8-visor-ben-10-os): his
// closed-visor skin is his own body with THAT skin's helmet (both head layers, rows 0..15); its glow sheet the visor's teal X
{
	const X = path.join(__dirname, '../xlr8_v0171');
	const body = dec(path.join(X, 'xlr8_src.png')), visor = dec(path.join(X, 'xlr8_visor_src.png'));
	const mask = Buffer.from(body.px), glow = Buffer.alloc(64 * 64 * 4);
	for (let y = 0; y < 16; y++) {
		for (let x = 0; x < 64; x++) {
			const o = (y * 64 + x) * 4;
			for (let c = 0; c < 4; c++) mask[o + c] = visor.px[o + c];
			const [r, g, b, a] = visor.px.slice(o, o + 4);
			if (a && b > 120 && g > 100 && r < 110) { glow[o] = r; glow[o + 1] = g; glow[o + 2] = b; glow[o + 3] = 255; }
		}
	}
	write('xlr8_mask.png', 64, 64, mask);
	write('xlr8_mask_eyes.png', 64, 64, glow);
}

// --- v0.17.1 (user: "remove the original pixels from the alien model that were supposed to look like the omnitrix badge on the
// skins ... position our alien badges more on top of those"): each skin's painted symbol is cleared -- the jacket-layer pixels
// made see-through, the base pixels repainted in the colour beside them -- and the client kit's badge() sits on that spot
{
	const fix = (name, jobs) => {
		const file = path.join(OUT, name + '.png');
		const im = dec(file);
		const at = (x, y) => (y * 64 + x) * 4;
		for (const j of jobs) {
			for (let y = j.y0; y <= j.y1; y++) {
				for (let x = j.x0; x <= j.x1; x++) {
					const o = at(x, y);
					if (j.clear) { im.px[o + 3] = 0; continue; }
					const s = at(j.from[0], j.from[1]);
					for (let c = 0; c < 4; c++) im.px[o + c] = im.px[s + c];
				}
			}
		}
		write(name + '.png', 64, 64, im.px);
	};
	// x/y are skin texels: the body's front is 20..27 x 20..31, its jacket layer 20..27 x 36..47
	fix('heatblast', [{ x0: 23, x1: 24, y0: 39, y1: 40, clear: true }]);
	fix('diamondhead', [{ x0: 26, x1: 27, y0: 37, y1: 38, clear: true }]);
	fix('ghostfreak', [{ x0: 24, x1: 25, y0: 22, y1: 23, from: [23, 22] }, { x0: 24, x1: 25, y0: 38, y1: 39, clear: true }]);
	fix('ripjaws', [{ x0: 25, x1: 27, y0: 20, y1: 23, from: [25, 24] }, { x0: 25, x1: 27, y0: 36, y1: 39, clear: true }]);
	fix('upgrade', [{ x0: 23, x1: 24, y0: 24, y1: 25, from: [22, 24] }, { x0: 23, x1: 24, y0: 40, y1: 41, clear: true }]);
	fix('stinkfly', [{ x0: 23, x1: 24, y0: 23, y1: 23, from: [22, 23] }]);
}

// --- v0.17.1: Stinkfly's extras for the new skin -- the tail (32..40 x 8..16) white with grey bands like the skin's suit, the
// stinger (40..44 x 8..12) black with a white stripe; the wings and mandibles as they were
{
	const sf = dec(path.join(__dirname, 'stinkfly_extras_old.png'));
	for (let y = 8; y < 16; y++) {
		for (let x = 32; x < 44; x++) {
			const o = (y * 64 + x) * 4;
			if (!sf.px[o + 3]) continue;
			let c;
			if (x < 40) c = (y % 4 === 3) ? [70, 74, 78] : [236 - (x % 2) * 10, 238 - (x % 2) * 10, 240 - (x % 2) * 10];
			else c = (y === 9 || y === 10) && x === 41 ? [230, 232, 234] : [22, 22, 26];
			sf.px[o] = c[0]; sf.px[o + 1] = c[1]; sf.px[o + 2] = c[2];
		}
	}
	write('stinkfly_extras.png', sf.w, sf.h, sf.px);
}

// --- v0.17.1 (user: "move his omnitrix badge to his back and remove the badge that was on the skin and fill in the gaps"): Grey
// Matter's painted back symbol (base 32..39 x 20..27, its ring on the jacket 32..39 x 36..43) filled with his back's own colours
// below it (each column copies row 29), the jacket ring cleared; GreyMatterClient's badge sits on the spot facing back
{
	const file = path.join(OUT, 'grey_matter.png');
	const im = dec(file);
	for (let x = 32; x < 40; x++) {
		const src = (29 * 64 + x) * 4;
		for (let y = 20; y < 28; y++) {
			const o = (y * 64 + x) * 4;
			for (let c = 0; c < 4; c++) im.px[o + c] = im.px[src + c];
		}
		for (let y = 36; y < 44; y++) im.px[(y * 64 + x) * 4 + 3] = 0;
	}
	write('grey_matter.png', 64, 64, im.px);
}

// --- v0.17.1 (user: "fill in the gaps of ghostfreaks arms"): Ghostfreak's and Grey Matter's skins are SLIM (3-px arms) but an
// alien is always drawn on the wide model, so their arm faces were read off by a column and left a see-through strip. Each arm
// and sleeve is remapped from the slim layout to the wide one: every 3-wide face stretched to 4 (its middle column doubled)
{
	const slimToWide = (px, ox, oy) => {
		const src = Buffer.from(px);
		const at = (x, y) => (y * 64 + x) * 4;
		const copy = (sx, sy, dx, dy) => { for (let c = 0; c < 4; c++) px[at(dx, dy) + c] = src[at(sx, sy) + c]; };
		const stretch = c => Math.min(2, Math.floor(c * 3 / 4)); // 0,0,1,2 -> the middle doubled
		for (let y = oy; y < oy + 16; y++) for (let x = ox; x < ox + 16; x++) px[at(x, y) + 3] = 0;
		for (let r = 0; r < 4; r++) {
			for (let c = 0; c < 4; c++) {
				copy(ox + 4 + stretch(c), oy + r, ox + 4 + c, oy + r); // top
				copy(ox + 7 + stretch(c), oy + r, ox + 8 + c, oy + r); // bottom
			}
		}
		for (let r = 4; r < 16; r++) {
			for (let c = 0; c < 4; c++) {
				copy(ox + c, oy + r, ox + c, oy + r); // the outer side (4 deep either way)
				copy(ox + 4 + stretch(c), oy + r, ox + 4 + c, oy + r); // front
				copy(ox + 7 + c, oy + r, ox + 8 + c, oy + r); // the inner side
				copy(ox + 11 + stretch(c), oy + r, ox + 12 + c, oy + r); // back
			}
		}
	};
	for (const name of ['ghostfreak', 'grey_matter']) {
		const im = dec(path.join(OUT, name + '.png'));
		for (const [ox, oy] of [[40, 16], [32, 48], [40, 32], [48, 48]]) slimToWide(im.px, ox, oy);
		write(name + '.png', 64, 64, im.px);
	}
}
