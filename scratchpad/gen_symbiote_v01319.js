// v0.13.19 Symbiote textures: the living tendril, the spike projectile and the arm blade.
// Original art, generated -- run with `node scratchpad/gen_symbiote_v01319.js`.
const path = require('path');
const png = require('./pnglib.js');

const ROOT = path.join(__dirname, '..');
const OUT = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/entity');

function img(w, h) {
	return { w, h, data: Buffer.alloc(w * h * 4) };
}
function set(im, x, y, [r, g, b], a = 255) {
	const i = (y * im.w + x) * 4;
	im.data[i] = r; im.data[i + 1] = g; im.data[i + 2] = b; im.data[i + 3] = a;
}
function mix(a, b, t) {
	return a.map((v, i) => Math.round(v + (b[i] - v) * t));
}
function rng(seed) {
	return () => {
		seed = (seed + 0x6D2B79F5) | 0;
		let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
		t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
		return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
	};
}

const BLACK = [7, 6, 10];
const DEEP = [16, 11, 24];
const SHEEN = [58, 32, 92];
const SPEC = [150, 130, 190];

// ---- tendril: u runs around the tube, v along it. A dark core with one wet specular stripe and a few
// darker veins, so the tube reads as glossy and alive as it twists.
{
	const im = img(16, 16);
	const r = rng(1319);
	for (let y = 0; y < 16; y++) {
		for (let x = 0; x < 16; x++) {
			const band = Math.abs(x - 5) / 5; // stripe centred on u=5
			let c = mix(DEEP, BLACK, Math.min(1, band));
			if (x === 4 || x === 5) c = mix(c, SHEEN, 0.75);
			if (x === 5 && (y % 4 !== 3)) c = mix(c, SPEC, 0.45);
			if (r() < 0.08) c = mix(c, BLACK, 0.8); // veins / pores
			if ((x + y * 3) % 11 === 0) c = mix(c, SHEEN, 0.25);
			set(im, x, y, c);
		}
	}
	png.write(path.join(OUT, 'symbiote_tendril.png'), im);
}

// ---- spike: black shaft, the forward half catching a purple sheen and a pale edge highlight.
{
	const im = img(16, 16);
	const r = rng(7);
	for (let y = 0; y < 16; y++) {
		for (let x = 0; x < 16; x++) {
			let c = mix(BLACK, DEEP, y / 15);
			if (x % 4 === 1) c = mix(c, SHEEN, 0.35 + 0.4 * (y / 15));
			if (x % 4 === 1 && y > 10) c = mix(c, SPEC, 0.35);
			if (r() < 0.06) c = mix(c, BLACK, 0.9);
			set(im, x, y, c);
		}
	}
	png.write(path.join(OUT, 'symbiote_spike.png'), im);
}

// ---- blade: flat living black with a purple-lit cutting edge down both long sides.
{
	const im = img(16, 16);
	const r = rng(42);
	for (let y = 0; y < 16; y++) {
		for (let x = 0; x < 16; x++) {
			let c = mix(BLACK, DEEP, 0.3 + 0.3 * Math.sin((x + y) * 0.7));
			if (x === 0 || x === 15 || y === 0) c = mix(c, SHEEN, 0.8);
			if ((x === 1 || x === 14) && y % 3 === 0) c = mix(c, SPEC, 0.5);
			if (r() < 0.05) c = mix(c, SHEEN, 0.4);
			set(im, x, y, c);
		}
	}
	png.write(path.join(OUT, 'symbiote_blade.png'), im);
}
console.log('wrote symbiote_tendril.png, symbiote_spike.png, symbiote_blade.png');
