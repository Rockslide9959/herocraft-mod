// v0.17.5 Symbiote weapons + wings: paints the three textures the procedural weapon / wing meshes use (all original, re-runnable).
//   node scratchpad/symbiote/gen_symbiote_weapons_v0175.js
// - textures/entity/symbiote_weapon.png        64x64  wet goo with a vein network (tinted to the host's body colour by SymbioteSuitTint.goo)
// - textures/entity/symbiote_weapon_edge.png   16x16  pale streaked edge (vertex-coloured: the emblem colour mixed into the body colour)
// - textures/entity/symbiote_wing_membrane.png 64x64  membrane panel: pale fill (vertex-coloured), dark web veins, a ragged scalloped
//                                                      trailing edge and a few tears cut out with alpha (u across the panel, v root->edge)
const path = require('path');
const PNG = require('../pnglib.js');
const out = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'entity');

let seed = 1705;
const rnd = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
function hash(x, y) { let h = (x * 374761393 + y * 668265263) ^ 0x5bd1e995; h = (h ^ (h >>> 13)) * 1274126177; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; }
function noise(x, y) { // smooth value noise, wrapping at 64
	const xi = Math.floor(x), yi = Math.floor(y), fx = x - xi, fy = y - yi;
	const s = (t) => t * t * (3 - 2 * t);
	const w = (a) => ((a % 64) + 64) % 64;
	const a = hash(w(xi), w(yi)), b = hash(w(xi + 1), w(yi)), c = hash(w(xi), w(yi + 1)), d = hash(w(xi + 1), w(yi + 1));
	return a + (b - a) * s(fx) + (c - a) * s(fy) + (a - b - c + d) * s(fx) * s(fy);
}
function fbm(x, y) { return noise(x, y) * 0.55 + noise(x * 2, y * 2) * 0.28 + noise(x * 4, y * 4) * 0.17; }

function img(W, H, fn) {
	const px = Buffer.alloc(W * H * 4);
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		const [r, g, b, a] = fn(x, y);
		px.set([Math.max(0, Math.min(255, Math.round(r))), Math.max(0, Math.min(255, Math.round(g))), Math.max(0, Math.min(255, Math.round(b))), a], (y * W + x) * 4);
	}
	return px;
}

// --- the weapon goo: veins as the zero lines of warped noise, wet highlights streaked along v (the weapon's length)
const weapon = img(64, 64, (x, y) => {
	const wx = x / 8 + fbm(x / 9, y / 9) * 1.6, wy = y / 8 + fbm(x / 9 + 7, y / 9 + 3) * 1.6;
	const vein = Math.abs(fbm(wx, wy) - 0.5);
	const fine = Math.abs(fbm(wx * 2.3 + 11, wy * 2.3) - 0.5);
	const streak = Math.pow(noise(x / 3, y / 14), 3);
	let lum = 22 + streak * 70 + fbm(x / 5, y / 5) * 16;
	if (vein < 0.035) lum = 6 + vein * 120;          // thick veins: near black
	else if (fine < 0.012) lum *= 0.6;               // fine veins
	else if (vein < 0.06) lum += 26 * (1 - (vein - 0.035) / 0.025); // a raised rim beside a thick vein catches the light
	return [lum * 0.82, lum * 0.7, lum * 1.12, 255];
});
PNG.write(path.join(out, 'symbiote_weapon.png'), 64, 64, weapon);

// --- the edge band: pale, streaked along its length
const edge = img(16, 16, (x, y) => {
	const l = 205 + noise(x * 0.6, y * 3) * 50 - (x === 0 || x === 15 ? 25 : 0);
	return [l, l, l, 255];
});
PNG.write(path.join(out, 'symbiote_weapon_edge.png'), 16, 16, edge);

// --- the wing membrane panel
const holes = [];
for (let i = 0; i < 3; i++) holes.push([0.2 + rnd() * 0.6, 0.55 + rnd() * 0.3, 0.025 + rnd() * 0.03]);
const membrane = img(64, 64, (x, y) => {
	const u = (x + 0.5) / 64, v = (y + 0.5) / 64;
	// scalloped trailing edge between the two finger bones, ragged
	const scallop = 1 - 0.2 * Math.sin(Math.PI * u) - 0.06 * fbm(x / 3, 5) - (hash(x, 9) < 0.18 ? 0.04 : 0);
	if (v > scallop) return [0, 0, 0, 0];
	for (const [hu, hv, hr] of holes) {
		const d = Math.hypot((u - hu) * 1.4, v - hv) + fbm(x / 4, y / 4) * 0.015;
		if (d < hr) return [0, 0, 0, 0];
	}
	// the web: radial strands (u = const, wobbling) and concentric arcs (v = const, sagging between the radials)
	const radials = 4;
	const ru = u * radials + Math.sin(v * 9 + u * 3) * 0.06;
	const rd = Math.abs(ru - Math.round(ru)) / radials;
	const arcs = 5;
	const sag = 0.05 * Math.sin(Math.PI * ((u * radials) % 1));
	const av = (v + sag) * arcs;
	const ad = Math.abs(av - Math.round(av)) / arcs;
	const web = Math.min(rd * 64 / 1.3, ad * 64 / 1.1);
	let l = 196 + fbm(x / 6, y / 6) * 50 - v * 30;
	if (web < 1) l = 34 + web * 40;
	else if (web < 2) l -= 40 * (2 - web);
	// darker near the edge and along the bones
	if (scallop - v < 0.035) l *= 0.55;
	if (u < 0.04 || u > 0.96) l *= 0.6;
	return [l, l * 0.96, l, 255];
});
PNG.write(path.join(out, 'symbiote_wing_membrane.png'), 64, 64, membrane);
console.log('wrote 3 textures');
