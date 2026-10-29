// v0.17.1: the Omnitrix capsule's textures (the show's pod: a dark gunmetal sphere banded by curved seams, a round front hub
// where the halves meet, a keyhole notch below it, layered metal bands round the inner rim, a dark interior).
// The shell is drawn procedurally by OmnitrixPodModel.java (a lat/long ellipsoid); its UVs are equirectangular:
//   u = (lon + PI) / (2 PI)  (lon 0 = the front, +z), v = (PI/2 - lat) / PI   -- into the top 512 x 256 of the atlas.
// Run: node scratchpad/omnitrix_acquire_v0171/gen_pod.js
'use strict';
const fs = require('fs');
const path = require('path');
const { encodePng, fbm, hash3, clamp, mix, smooth } = require('../viperion/lib_concept_models.js');

const OUT = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'entity', 'omnitrix');

function canvas(w, h) {
	const px = Buffer.alloc(w * h * 4);
	return {
		w, h, px,
		set(x, y, c, a = 255) {
			if (x < 0 || y < 0 || x >= w || y >= h) return;
			const i = (y * w + x) * 4;
			px[i] = clamp(Math.round(c[0])); px[i + 1] = clamp(Math.round(c[1])); px[i + 2] = clamp(Math.round(c[2])); px[i + 3] = clamp(Math.round(a));
		},
		save(name) { fs.writeFileSync(path.join(OUT, name), encodePng(w, h, px)); console.log('wrote', name, w + 'x' + h); },
	};
}

// ---------------------------------------------------------------- palette (dark gunmetal; daylight on the entity shader reads near true)
const STEEL = [66, 71, 79];
const STEEL_HI = [108, 114, 124];
const SEAM = [20, 22, 26];
const DARK = [24, 26, 30];
const INNER = [30, 33, 38];

// ---------------------------------------------------------------- the shell's seams in (lat, lon) radians
const PI = Math.PI;
/** Distance (radians on the sphere, roughly) to the nearest seam line, and which side of it we are (for the bevel). */
function seamField(lat, lon) {
	let best = 9, side = 0;
	const consider = (d, s) => { if (Math.abs(d) < best) { best = Math.abs(d); side = s; } };
	// the latitude bands: the split at the equator (a deep groove), then two seams each side
	for (const L of [0.0, 0.46, -0.46, 0.98, -0.98]) {
		consider(lat - L, Math.sign(lat - L));
	}
	// the polar caps' ring
	for (const L of [1.30, -1.30]) {
		consider(lat - L, Math.sign(lat - L));
	}
	const c = Math.cos(lat);
	// the bands are the ball's lines (they curve round it); only the second band is cut into a few long plates
	if (Math.abs(lat) > 0.46 && Math.abs(lat) < 0.98) {
		for (let k = 0; k < 6; k++) {
			const base = -PI + k * PI / 3 + PI / 6;
			let d = lon - base;
			d = Math.atan2(Math.sin(d), Math.cos(d));
			consider(d * c, Math.sign(d));
		}
	}
	return { d: best, side };
}

function shellColour(lat, lon) {
	const { d, side } = seamField(lat, lon);
	const n = fbm(Math.cos(lon) * 6 + 11, Math.sin(lon) * 6, lat * 6, 4);
	const brushed = fbm(lon * 40, lat * 2, 3, 2);
	// the panel's own shade (a few distinct plates)
	const panelKey = Math.floor(lat / 0.46 * 2) * 31 + (Math.abs(lat) > 0.46 && Math.abs(lat) < 0.98 ? Math.floor((lon + PI) / (PI / 3)) : 0);
	const plate = (hash3(panelKey, 7, 3) - 0.5) * 10;
	let c = STEEL.map(v => v + plate + (n - 0.5) * 16 + (brushed - 0.5) * 8);
	// a soft gradient across each band (curved plating catches light near its upper edge)
	const bandPos = ((lat % 0.46) + 0.46) % 0.46 / 0.46;
	c = c.map(v => v + (bandPos - 0.5) * 10);
	// seams: a dark groove, a lit bevel on the side above / to the right, a shadowed lip below
	const w = Math.abs(lat) < 0.02 ? 0.020 : 0.0105;
	if (d < w) {
		c = mix(SEAM, c, smooth(w * 0.55, w, d));
	} else if (d < w * 2.4) {
		const t = 1 - smooth(w, w * 2.4, d);
		c = side > 0 ? mix(c, STEEL_HI, t * 0.75) : mix(c, [78, 82, 90], t * 0.6);
	}
	// the keyhole notch under the front hub (lon 0): a round hole and a slot widening downward
	const kx = Math.atan2(Math.sin(lon), Math.cos(lon)) * Math.cos(lat); // arc across
	const ky = lat;
	const hole = Math.hypot(kx, ky + 0.33);
	const slotW = 0.028 + (-(ky + 0.33)) * 0.22;
	const inSlot = ky < -0.33 && ky > -0.44 && Math.abs(kx) < slotW;
	if (hole < 0.06 || inSlot) {
		c = DARK.slice();
		if (hole > 0.05 && !inSlot) c = mix(DARK, [60, 64, 70], 0.6);
	} else if (hole < 0.075 || (ky < -0.31 && ky > -0.455 && Math.abs(kx) < slotW + 0.014)) {
		c = mix(c, STEEL_HI, 0.55); // the notch's bright chamfer
	}
	// the polar cap: a slightly darker, smoother disc
	if (Math.abs(lat) > 1.30) {
		c = c.map(v => v - 8);
	}
	return c;
}

// ---------------------------------------------------------------- the atlas (512 x 512)
const A = canvas(512, 512);
for (let y = 0; y < 256; y++) {
	const lat = PI / 2 - (y + 0.5) / 256 * PI;
	for (let x = 0; x < 512; x++) {
		const lon = (x + 0.5) / 512 * 2 * PI - PI;
		A.set(x, y, shellColour(lat, lon));
	}
}
// the hub face (0,256)-(128,384): a round plate, its groove, the inner disc, the split across the middle
for (let y = 0; y < 128; y++) {
	for (let x = 0; x < 128; x++) {
		const dx = (x + 0.5 - 64) / 64, dy = (y + 0.5 - 64) / 64;
		const r = Math.hypot(dx, dy);
		const n = fbm(x * 0.08, y * 0.08, 5, 3);
		let c;
		if (r > 1.0) c = SEAM;
		else if (r > 0.9) c = mix(STEEL_HI, STEEL, (r - 0.9) / 0.1);          // the outer chamfer
		else if (r > 0.78) c = [104, 110, 118];
		else if (r > 0.72) c = SEAM;                                           // the groove
		else if (r > 0.66) c = STEEL_HI;
		else if (r > 0.30) c = [118, 124, 133].map(v => v + (n - 0.5) * 12 - dy * 10);
		else if (r > 0.24) c = SEAM;
		else c = [92, 98, 106].map(v => v - dy * 12);
		if (Math.abs(dy) < 0.035 && r < 0.98) c = SEAM;                        // where the two halves meet
		A.set(x, 256 + y, c);
	}
}
// the hub's side wall (128,256)-(256,288): brushed steel, dark at both rims
for (let y = 0; y < 32; y++) {
	for (let x = 0; x < 128; x++) {
		const t = y / 31;
		let c = [96, 102, 110].map(v => v + (fbm(x * 0.3, y * 0.05, 1, 2) - 0.5) * 12);
		if (t < 0.12 || t > 0.88) c = SEAM;
		A.set(128 + x, 256 + y, c);
	}
}
// the interior (256,256)-(384,384): dark with radial ribs and panel lines (u round, v from the rim down to the bottom)
for (let y = 0; y < 128; y++) {
	for (let x = 0; x < 128; x++) {
		const u = x / 128, v = y / 128;
		const rib = Math.abs(((u * 12) % 1) - 0.5) < 0.06;
		const ring = Math.abs(((v * 5) % 1) - 0.5) < 0.05;
		const n = fbm(x * 0.1, y * 0.1, 9, 3);
		let c = INNER.map(k => k + (n - 0.5) * 10 - v * 8);
		if (rib || ring) c = c.map(k => k - 12);
		else if (Math.abs(((u * 12) % 1) - 0.5) < 0.1) c = c.map(k => k + 8);
		A.set(256 + x, 256 + y, c);
	}
}
// the rim bands (384,256)-(512,320): four swatches 32 wide -- the outer lip, the first step, the second step, the walls
const RIMS = [[132, 138, 147], [102, 108, 117], [72, 77, 85], [46, 50, 57]];
for (let k = 0; k < 4; k++) {
	for (let y = 0; y < 64; y++) {
		for (let x = 0; x < 32; x++) {
			const n = fbm((k * 32 + x) * 0.2, y * 0.04, 3, 2);
			let c = RIMS[k].map(v => v + (n - 0.5) * 10);
			if (x < 2 || x > 29) c = c.map(v => v * 0.7);
			A.set(384 + k * 32 + x, 256 + y, c);
		}
	}
}
// the cradle (384,320)-(512,384): a dark pedestal swatch with a lit green ring texel row
for (let y = 0; y < 64; y++) {
	for (let x = 0; x < 128; x++) {
		let c = [40, 44, 50].map(v => v + (fbm(x * 0.2, y * 0.2, 4, 2) - 0.5) * 8);
		if (y > 28 && y < 36) c = [90, 230, 90];
		A.set(384 + x, 320 + y, c);
	}
}
// the rest: plain steel (spare)
for (let y = 384; y < 512; y++) for (let x = 0; x < 512; x++) A.set(x, y, STEEL);
A.save('pod.png');

// ---------------------------------------------------------------- the heat mask (additive: white where the shell glows hot)
const H = canvas(512, 256);
for (let y = 0; y < 256; y++) {
	const lat = PI / 2 - (y + 0.5) / 256 * PI;
	for (let x = 0; x < 512; x++) {
		const lon = (x + 0.5) / 512 * 2 * PI - PI;
		const { d } = seamField(lat, lon);
		const n = fbm(Math.cos(lon) * 4 + 3, Math.sin(lon) * 4, lat * 4, 4);
		// the seams glow white-hot, the plates a dull smouldering patchwork
		let k = Math.max(1 - smooth(0.006, 0.03, d), 0.0) * 1.0 + smooth(0.45, 0.75, n) * 0.35 + 0.12;
		k = clamp(k * 255);
		H.set(x, y, [k, k, k], 255);
	}
}
H.save('pod_heat.png');

// ---------------------------------------------------------------- the interior's glow (additive, green): the lit ribs and a ring
const G = canvas(128, 128);
for (let y = 0; y < 128; y++) {
	for (let x = 0; x < 128; x++) {
		const u = x / 128, v = y / 128;
		const rib = 1 - smooth(0.0, 0.05, Math.abs(((u * 12) % 1) - 0.5));
		const ring = 1 - smooth(0.0, 0.04, Math.abs(v - 0.62));
		const k = Math.max(rib * 0.35 * smooth(0.25, 0.9, v), ring * 0.9);
		G.set(x, y, [k * 90, k * 255, k * 80], 255);
	}
}
G.save('pod_inner_glow.png');

// ---------------------------------------------------------------- the glint (the pale crescent): plain white
const W = canvas(16, 16);
for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) W.set(x, y, [255, 255, 255], 255);
W.save('pod_glint.png');

// ---------------------------------------------------------------- a soft round glow sprite (additive: the meteor, the leaping watch)
const GL = canvas(64, 64);
for (let y = 0; y < 64; y++) {
	for (let x = 0; x < 64; x++) {
		const r = Math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 32;
		const k = r >= 1 ? 0 : Math.pow(1 - r, 2.2);
		GL.set(x, y, [k * 255, k * 255, k * 255], 255);
	}
}
GL.save('pod_soft_glow.png');
