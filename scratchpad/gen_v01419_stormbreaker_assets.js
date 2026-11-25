// v0.14.19 Stormbreaker (rebuilt v0.16.1, rebuilt again v0.17.7): generates the item models (held + thrown) and all
// textures. Re-runnable; edit this, not the outputs. Run from the repo root: node scratchpad/gen_v01419_stormbreaker_assets.js
//
// v0.17.7: 1.5 blocks long. The model is 24 px from pommel to crown (16 px = 1 block) -- the elements simply run past the
// usual 16 px box (Minecraft allows -16..32), up local +Y from y 0 to y 24, x / z centred on 8. Third person draws it at
// scale 1.0, so it is 1.5 blocks in the hand; the thrown model's FIXED scale is 1.0, so the flying / resting / pinned axe
// is 1.5 blocks in the world too. The GUI and item-frame transforms are fitted to the slot by this script.
//  - a faceted crescent blade: every edge is a straight segment at a multiple of 22.5 degrees (an element rotation),
//    so the outline is clean and crisp instead of stepped -- a bright honed cutting edge, a lighter ground bevel behind
//    it and the dark Uru body, thickening into a throat at the head; an upswept horn and a bearded lower point with the
//    hollow of the crescent between the beard and the haft; a glowing rune strip down the flat;
//  - a chamfered head with a rune band, a stepped hammer poll (root, neck, block, polished face) on the back and a
//    branch-end crown on top;
//  - a long Groot-branch haft (wandering segments, knots, a vine winding up it), a leather-wrapped grip of alternating
//    wraps between a tie and an Uru ferrule, and an end cap.
// Element rotations: Minecraft allows one rotation per element, in 22.5 degree steps from -45 to 45. The held model also
// needs the -45 degree icon-diagonal bake on every element, so each blade facet's own rotation is composed with the bake
// into one rotation about a new origin, and anything that lands outside -45..45 is brought back by turning the box
// itself a quarter turn (an exact, axis-aligned remap) -- see place().
const fs = require('fs');
const path = require('path');
const { encode } = require('./pngkit.js');

const ROOT = path.join(__dirname, '../src/main/resources/assets/projecthero');
const TEX = path.join(ROOT, 'textures/item');
const MODELS = path.join(ROOT, 'models/item');

// ---------------- deterministic noise ----------------
let seed = 1417;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));

function image(w, h, fn) {
	const px = Buffer.alloc(w * h * 4);
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		const c = fn(x, y);
		const i = (y * w + x) * 4;
		if (!c) { px[i + 3] = 0; continue; }
		px[i] = clamp(c[0]); px[i + 1] = clamp(c[1]); px[i + 2] = clamp(c[2]); px[i + 3] = c.length > 3 ? clamp(c[3]) : 255;
	}
	return px;
}
const write = (name, w, h, px) => { fs.writeFileSync(path.join(TEX, name + '.png'), encode(w, h, px)); console.log('wrote', name); };

// ---------------- textures (64 x 64: 4 texels per model pixel, like Mjolnir's) ----------------
// Every texture tiles (vertically at least) because the long model wraps its block-projected UVs every 16 px of height.
const T = 64;
const hash = (x, y, s) => { const v = Math.sin(x * 127.1 + y * 311.7 + s * 74.7) * 43758.5453; return v - Math.floor(v); };
const add = (c, k) => [c[0] + k, c[1] + k, c[2] + k];
const TAU = Math.PI * 2;
/** Faint horizontal brushing (per-row streaks) -- tiles. */
const brush = (x, y, s) => (hash(0, y, s) - 0.5) * 5 + (hash(x >> 3, y, s + 1) - 0.5) * 2;
/** A broad soft diagonal sheen band, period 64 both ways -- tiles. */
const sheen = (x, y) => 8 * Math.max(0, Math.cos(TAU * (x + y) / 64)) - 3;

// Uru: dark blued steel (Stormbreaker's colder, darker than Mjolnir's) -- a quiet plate with faint brushing and sheen.
const URU = { deep: [30, 34, 46], dark: [56, 63, 80], mid: [80, 89, 110], light: [116, 128, 152], hi: [170, 184, 206] };
write('stormbreaker_metal', T, T, image(T, T, (x, y) => add(URU.mid, brush(x, y, 1) + sheen(x, y))));
// Bevel: the ground facet between the body and the edge -- mid steel with fine grinding marks across it.
write('stormbreaker_bevel', T, T, image(T, T, (x, y) => {
	const grind = ((x + y * 2) % 7 === 0) ? 6 : 0;
	return add([132, 145, 168], brush(x, y, 2) * 0.6 + grind + sheen(x, y) * 0.6);
}));
// Edge: the honed cutting edge -- bright silver-white with a cold cast, nearly flat so it reads as one crisp line.
// Blade: the flat of the blade -- darker than the head so the two read apart, the same quiet brushing.
write('stormbreaker_blade', T, T, image(T, T, (x, y) => add([62, 70, 90], brush(x, y, 7) + sheen(x, y) * 0.8)));
write('stormbreaker_edge', T, T, image(T, T, (x, y) => add([214, 226, 240], (hash(x, y, 3) - 0.5) * 4 + sheen(x, y) * 0.5)));
// Rune: near-black Uru with glowing cyan Norse runes. Rows 0..3: a horizontal line of glyphs (the head band, UV v 0..1);
// cols 0..3 below row 8: the same glyphs stacked upright (the blade strip, UV u 0..1). Dim cyan halo round each stroke.
(function rune() {
	const G = [
		['.#.', '###', '.#.', '.#.'], // Tiwaz
		['#..', '##.', '##.', '#..'], // Thurisaz
		['#.#', '###', '.#.', '.#.'], // Algiz
		['##.', '#.#', '##.', '#.#'], // Raido
		['#.#', '##.', '#.#', '#..'], // Fehu-ish
		['#.#', '.#.', '#.#', '#.#'], // Gebo-ish
	];
	const on = new Set();
	for (let k = 0; k < 16; k++) { // the band: glyphs 3 wide + 1 gap along rows 0..3
		const g = G[k % G.length];
		for (let r = 0; r < 4; r++) for (let c = 0; c < 3; c++) if (g[r][c] === '#') on.add((k * 4 + c) + ',' + r);
	}
	for (let k = 0; k < 11; k++) { // the blade strip: glyphs 4 tall + 1 gap down cols 0..2 from row 8
		const g = G[(k + 2) % G.length];
		for (let r = 0; r < 4; r++) for (let c = 0; c < 3; c++) if (g[r][c] === '#') on.add(c + ',' + (8 + k * 5 + r));
	}
	write('stormbreaker_rune', T, T, image(T, T, (x, y) => {
		if (on.has(x + ',' + y)) return [104, 196, 226];
		const near = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => on.has((x + dx) + ',' + (y + dy)));
		if (near) return [38, 62, 84];
		return add([30, 34, 46], (hash(x, y, 6) - 0.5) * 4);
	}));
})();
// Handle: Groot's branch -- warm bark with twisting, slanted fibre grooves and pale ridges, a hint of living green.
write('stormbreaker_handle', T, T, image(T, T, (x, y) => {
	const f = Math.sin(TAU * (x + y * 0.25) / 8 + Math.sin(TAU * y / 32) * 0.8);
	let c = [116, 82, 52];
	if (f < -0.6) c = [72, 48, 30]; // groove
	else if (f > 0.7) c = [140, 102, 66]; // ridge
	c = add(c, (hash(x, y, 4) - 0.5) * 5);
	if (Math.cos(TAU * x / 32) * Math.cos(TAU * y / 64) > 0.92) c = [c[0] - 14, c[1] + 4, c[2] - 10];
	return c;
}));
// Grip: dark leather strap wound round, each wrap shaded round, a seam between wraps and a stitched line.
write('stormbreaker_grip', T, T, image(T, T, (x, y) => {
	const b = ((y + Math.floor(x / 2)) % 8 + 8) % 8;
	const round = Math.sin((b + 0.5) / 8 * Math.PI);
	let c = [40 + round * 38, 26 + round * 26, 18 + round * 17];
	if (b === 0) c = [20, 13, 10];
	if (b === 4 && x % 3 === 0) c = add(c, 26);
	return add(c, (hash(x, y, 5) - 0.5) * 4);
}));
// Unforged Stormbreaker: a flat 16x16 tool sprite on the usual bottom-left -> top-right diagonal. Cold, dull grey
// metal head (axe blade on the upper side, hammer block on the lower), a rough dark core where the handle will be,
// and one dim orange glow line along the blade -- the heat it still needs.
(function unforged() {
	const c = { x: 7.6, y: 8.4 };
	const d = { x: Math.SQRT1_2, y: -Math.SQRT1_2 }; // along the handle, toward the top-right (head)
	const n = { x: Math.SQRT1_2, y: Math.SQRT1_2 }; // across the handle
	function shapeAt(px, py) {
		const ux = px - c.x, uy = py - c.y;
		const u = ux * d.x + uy * d.y; // along
		const v = ux * n.x + uy * n.y; // across (+v = lower-right = axe side)
		// head core
		if (u >= 1.8 && u <= 5.2 && Math.abs(v) <= 1.4) return { part: 'core', u, v };
		// hammer block (upper-left side)
		if (v <= -1.4 && v >= -4.0 && u >= 2.1 && u <= 4.9) return { part: v <= -3.3 ? 'hammer_face' : 'hammer', u, v };
		// axe blade (lower-right side), flaring toward the edge
		if (v >= 1.4 && v <= 4.9) {
			const flare = (v - 1.4) * 0.45;
			if (u >= 2.1 - flare && u <= 4.9 + flare) return { part: v >= 4.1 ? 'edge' : 'blade', u, v };
		}
		// stubby blaze-rod core / unfinished handle
		if (u >= -8.4 && u < 1.8 && Math.abs(v) <= 0.85) return { part: 'handle', u, v };
		return null;
	}
	const px = Buffer.alloc(16 * 16 * 4);
	seed = 55;
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		// 4x4 supersampling: majority coverage decides the pixel, the most common part decides its colour
		const counts = {};
		let covered = 0, sample = null;
		for (let sy = 0; sy < 4; sy++) for (let sx = 0; sx < 4; sx++) {
			const s = shapeAt(x + (sx + 0.5) / 4, y + (sy + 0.5) / 4);
			if (s) { covered++; counts[s.part] = (counts[s.part] || 0) + 1; sample = sample || s; }
		}
		const i = (y * 16 + x) * 4;
		const noise = (rnd() - 0.5) * 10;
		if (covered < 7) { px[i + 3] = 0; continue; }
		const part = Object.entries(counts).sort((a, b) => b[1] - a[1])[0][0];
		const s = shapeAt(x + 0.5, y + 0.5) || sample;
		let col;
		if (part === 'handle') col = [74 + noise, 58 + noise, 46 + noise]; // soot-dark rod
		else if (part === 'core') col = [78 + noise, 80 + noise, 86 + noise];
		else if (part === 'hammer') col = [66 + noise, 68 + noise, 74 + noise];
		else if (part === 'hammer_face') col = [104 + noise, 106 + noise, 112 + noise];
		else if (part === 'blade') col = [92 + noise, 94 + noise, 100 + noise];
		else col = [190 + noise * 0.5, 96 + noise, 34 + noise]; // the dim orange glow line along the edge
		// simple top-left light
		if (s && part !== 'edge') {
			const shade = (-(s.v) * 2.0) - (s.u - 4) * 0.5;
			col = col.map((ch) => ch + shade);
		}
		px[i] = clamp(col[0]); px[i + 1] = clamp(col[1]); px[i + 2] = clamp(col[2]); px[i + 3] = 255;
	}
	// a dark outline so it reads on any slot background
	const out = Buffer.from(px);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const i = (y * 16 + x) * 4;
		if (px[i + 3] !== 0) continue;
		let edge = false;
		for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
			const nx = x + dx, ny = y + dy;
			if (nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && px[(ny * 16 + nx) * 4 + 3] !== 0) edge = true;
		}
		if (edge) { out[i] = 24; out[i + 1] = 22; out[i + 2] = 24; out[i + 3] = 255; }
	}
	write('unforged_stormbreaker', 16, 16, out);
})();


// ---------------- 3D model ----------------
// Final coordinates: the blade on -X, the hammer poll on +X (the vanilla axe sprite's sides once baked onto the icon
// diagonal, so with the vanilla handheld rotations it is held blade-forward like any axe), handle up local +Y.
const ELEMENTS = [];
const r4 = (v) => Math.round(v * 10000) / 10000;
/** An axis-aligned box. faces: optional per-face {tex, uv} overrides. */
const box = (name, from, to, tex, faces) => ELEMENTS.push({ name, from, to, tex, faces: faces || {}, rot: null });
/** A box of half-thickness zh centred on z 8, turned `angle` degrees about Z round its own centre. */
const rbox = (name, cx, cy, w, h, zh, angle, tex) => ELEMENTS.push({
	name, from: [cx - w / 2, cy - h / 2, 8 - zh], to: [cx + w / 2, cy + h / 2, 8 + zh], tex, faces: {},
	rot: angle ? { angle, origin: [cx, cy, 8] } : null,
});
/**
 * A straight strip lying along segment p -> q of the blade outline: `w` wide, starting `off` in from the line on the
 * inward side (the left of p -> q, the outline running counter-clockwise), `zh` half thick, running `ext` past
 * both ends so neighbouring facets join cleanly -- or [past p, past q] (negative = stop short).
 */
function strip(name, p, q, w, zh, tex, ext = 0.12, off = 0) {
	const [e0, e1] = Array.isArray(ext) ? ext : [ext, ext];
	const dx = q[0] - p[0], dy = q[1] - p[1], L0 = Math.hypot(dx, dy);
	const ux = dx / L0, uy = dy / L0, shift = (e1 - e0) / 2; // an uneven extension slides the centre along the line
	const L = L0 + e0 + e1;
	ext = 0; // already in L
	const nx = -dy / L0, ny = dx / L0;
	const cx = (p[0] + q[0]) / 2 + ux * shift + nx * (off + w / 2), cy = (p[1] + q[1]) / 2 + uy * shift + ny * (off + w / 2);
	let a = Math.atan2(dy, dx) * 180 / Math.PI;
	a = ((a % 180) + 180) % 180; if (a > 90) a -= 180; // (-90, 90]
	const snapped = Math.round(a / 22.5) * 22.5;
	if (Math.abs(snapped - a) > 0.05) throw new Error(name + ': segment angle ' + a + ' is not a multiple of 22.5');
	if (Math.abs(snapped) === 90) return rbox(name, cx, cy, w, L + 2 * ext, zh, 0, tex);
	if (Math.abs(snapped) === 67.5) return rbox(name, cx, cy, w, L + 2 * ext, zh, snapped - Math.sign(snapped) * 90, tex);
	return rbox(name, cx, cy, L + 2 * ext, w, zh, snapped, tex);
}

// ---- the blade outline, counter-clockwise; every segment at a multiple of 22.5 degrees ----
const C22 = Math.cos(Math.PI / 8), S22 = Math.sin(Math.PI / 8), C45 = Math.SQRT1_2;
const at = (p, dx, dy, L) => [p[0] + dx * L, p[1] + dy * L];
const LIFT = 0.75; // the head sits this much higher than the v0.16.1 sketch: total length 24
const A = [6.5, 22.4]; // where the upper back of the blade leaves the head
const B = at(A, -C22, S22, 4.0); // the horn tip, swept up (y 23.93)
const Cc = at(B, -C45, -C45, 0.7); // the clipped point of the horn
const D = at(Cc, -S22, -C22, 2.2); // the cutting edge turning down
const E = at(D, 0, -1, 3.6); // the long straight of the edge
const F = at(E, S22, -C22, 2.2);
const G = at(F, C45, -C45, 2.8); // the beard tip, hooked down along the haft
const Hh = [6.5, G[1] + (6.5 - G[0])]; // the beard's inner edge rises at 45 degrees back to the head's socket
const EDGE = [[B, Cc], [Cc, D], [D, E], [E, F], [F, G]];

// blade thicknesses (half, in z): the body thickest, the ground bevel thinner, the honed edge thinnest -- a stepped
// profile, so from the flat the edge is a crisp bright line outside a lighter bevel band outside the dark body
const ZB = 0.32, ZV = 0.21, ZE = 0.11, WE = 0.4, WV = 0.8, BODY = WE + WV;
// v0.17.7 clipping fix (user: "stormbreaker model has some clipping on it"). Audit: scratchpad/v0177/stormbreaker_overlap_check.js
// (0 fights / 0 close pairs now); close-ups: VIEW=head [FIGHT=1] node scratchpad/preview_thor_weapons_v0177.js ...
//  - neighbouring edge / bevel facets used to overlap 0.12 past every joint on the SAME plane (z-fighting at each joint)
//    and, at the horn and beard tips, the overlap stuck the bright edge out past the horn / beard back (the light nub):
//    now every second facet is JOG thinner (the thicker one wins the overlap cleanly), a joint overlaps only by
//    w * tan(11.25 deg) -- exactly enough to close the wedge a 22.5 degree turn leaves -- and the tips do not overlap at
//    all (the horn / beard strip already covers that corner);
//  - the body pieces were 0.003 px apart in thickness (flicker past ~12 blocks): now 0.02 px steps, plates abutting;
//  - the lit rims sat on the same plane as the horn / beard back: they now wrap 0.03 proud of it, and stop short of the tips.
const JOG = 0.03, TAN11 = Math.tan(Math.PI / 16);
for (const [i, [p, q]] of EDGE.entries()) {
	const jog = i % 2 ? JOG : 0;
	const tip = (k, w) => (k ? r4(w * TAN11) : 0);
	// the beard tip turns 90 degrees: the last facet stops 0.03 short so its end is not on the beard's back face
	const last = (k, w) => (k < EDGE.length - 1 ? tip(true, w) : -0.03);
	strip('edge' + i, p, q, WE, r4(ZE - jog), 'edge', [tip(i > 0, WE), last(i, WE)]);
	strip('bevel' + i, p, q, WV, r4(ZV - jog), 'bevel', [tip(i > 0, WV), last(i, WV)], WE);
}
// the body: strips along the back edges (horn, beard) and inside the bevel, plates between them; any two that overlap
// differ in thickness by at least 0.02 px so no two flats fight
strip('horn', A, B, 1.8, ZB, 'blade', [0.05, 0]);
strip('beard', G, Hh, 1.8, ZB, 'blade', [-0.03, 0.05]); // its start face would sit on the last edge facet's outer face
strip('horn_tip_fill', B, Cc, 0.9, 0.30, 'blade', 0, BODY);
strip('horn_fill', Cc, D, 1.6, 0.28, 'blade', 0, BODY);
strip('beard_fill', E, F, 1.6, 0.28, 'blade', 0, BODY);
strip('beard_tip_fill', F, G, 1.2, 0.30, 'blade', [0, -0.03], BODY);
{ const z = 0.26; box('plate', [r4(D[0] + BODY), r4(E[1]), 8 - z], [6.6, r4(D[1]), 8 + z], 'blade'); }
{ const z = 0.26; box('plate_hi', [3.15, r4(D[1]), 8 - z], [6.6, 22.4, 8 + z], 'blade'); }
{ const z = 0.26; box('plate_lo', [3.45, 16.3, 8 - z], [6.6, r4(E[1]), 8 + z], 'blade'); }
// a lit rim along the back of the horn and the beard, wrapping just proud of the back and the flat
strip('horn_rim', A, B, 0.28, r4(ZB + 0.025), 'bevel', [0.02, -0.03], -0.03);
strip('beard_rim', G, Hh, 0.28, r4(ZB + 0.025), 'bevel', [-0.06, 0.02], -0.03);
box('throat', [5.6, 18.25, 7.45], [6.6, 21.75, 8.55], 'metal');
// the rune strip down the flat of the blade, just proud of it on both faces
box('blade_rune', [3.2, 17.9, r4(8 - ZB - 0.03)], [3.95, 21.1, r4(8 + ZB + 0.03)], 'rune', {
	north: { tex: 'rune', uv: [0, 2, 0.75, 5.2] }, south: { tex: 'rune', uv: [0, 2, 0.75, 5.2] },
});

// ---- the head: chamfered core, collar, rune band, crown ----
const HC0 = [6.5, 16.25 + LIFT, 6.6], HC1 = [9.5, 22.25 + LIFT, 9.4], HCH = 0.4;
const hin = (ix, iy, iz) => [[HC0[0] + ix, HC0[1] + iy, HC0[2] + iz], [HC1[0] - ix, HC1[1] - iy, HC1[2] - iz]];
box('head_x', ...hin(0, HCH, HCH), 'bevel');
box('head_y', ...hin(HCH, 0, HCH), 'bevel');
box('head_z', ...hin(HCH, HCH, 0), 'metal');
box('head_collar', [6.75, 15.75 + LIFT, 6.85], [9.25, 16.25 + LIFT, 9.15], 'bevel');
box('rune_band', [6.4, 18.75 + LIFT, 6.5], [9.6, 19.75 + LIFT, 9.5], 'rune', {
	north: { tex: 'rune', uv: [0, 0, 3.2, 1] }, south: { tex: 'rune', uv: [0, 0, 3.2, 1] },
	east: { tex: 'rune', uv: [4, 0, 7, 1] }, west: { tex: 'rune', uv: [4, 0, 7, 1] },
});
box('crown_cap', [6.9, 22.25 + LIFT, 7.0], [9.1, 22.75 + LIFT, 9.0], 'bevel');
box('crown_wood', [7.4, 22.75 + LIFT, 7.4], [8.6, 23.25 + LIFT, 8.6], 'handle');

// ---- the hammer poll on the back: root, a waisted neck, the block, a polished face ----
box('poll_root', [9.5, 17.25 + LIFT, 6.85], [10.2, 21.25 + LIFT, 9.15], 'metal');
box('poll_neck', [10.2, 17.75 + LIFT, 7.15], [10.7, 20.75 + LIFT, 8.85], 'bevel');
box('poll_block', [10.7, 17.15 + LIFT, 6.75], [11.95, 21.35 + LIFT, 9.25], 'metal');
box('poll_face', [11.95, 17.45 + LIFT, 7.05], [12.3, 21.05 + LIFT, 8.95], 'edge');

// v0.17.7 (user): held like a greataxe -- the fist sits well up the haft, at y 9 (GRIP_Y, a third of the way up the
// 24 px axe), on a leather grip from y 6 to y 12, with bare branch below it down to the ferrule, so the end of the
// haft sticks out under the fist. The hand display transforms put the fist at GRIP_Y (see DISPLAY below).
const GRIP_Y = 9.0, GRIP0 = 6.0, GRIP1 = 12.0;
// ---- Groot's branch: wandering segments of changing girth, knots and a vine winding up -- below and above the grip ----
const branch = (tag, y0, y1, k0) => {
	for (let i = k0, y = y0; y < y1 - 1e-6; i++, y += 1.5) {
		const dx = Math.sin(i * 1.7) * 0.1, dz = Math.cos(i * 1.3) * 0.1;
		const g = 0.7 + 0.07 * Math.sin(i * 2.1 + 0.5);
		box(tag + i, [r4(8 - g + dx), r4(y), r4(8 - g + dz)], [r4(8 + g + dx), r4(Math.min(y1, y + 1.5)), r4(8 + g + dz)], 'handle');
	}
};
branch('branch_lo', 1.3, GRIP0 + 0.1, 0);
branch('branch_hi', GRIP1 - 0.1, 16.0 + LIFT, 4);
box('knot0', [8.6, 3.2, 7.55], [9.0, 3.75, 8.2], 'handle');
box('knot1', [6.95, 14.1, 7.68], [7.35, 14.6, 8.35], 'handle');
box('vine0', [7.7, 1.6, 8.62], [8.25, 3.4, 8.92], 'handle');
box('vine1', [8.62, 3.9, 7.75], [8.92, 5.66, 8.3], 'handle');
box('vine2', [7.75, 12.4, 7.08], [8.3, 14.4, 7.38], 'handle');
box('vine3', [7.08, 14.3, 7.7], [7.38, 16.3, 8.25], 'handle');

// ---- the grip: a leather core and alternating wraps between two ties; the ferrule and end cap at the bottom ----
box('grip_core', [7.3, GRIP0, 7.3], [8.7, GRIP1, 8.7], 'grip');
for (let i = 0, y = GRIP0 + 0.15; y < GRIP1 - 0.35; i++, y += 0.6) {
	const g = i % 2 === 0 ? 0.86 : 0.78;
	box('wrap' + i, [r4(8 - g), r4(y), r4(8 - g)], [r4(8 + g), r4(y + 0.45), r4(8 + g)], 'grip');
}
box('tie_lo', [7.08, GRIP0 - 0.3, 7.08], [8.92, GRIP0 + 0.2, 8.92], 'bevel');
box('tie_hi', [7.08, GRIP1 - 0.2, 7.08], [8.92, GRIP1 + 0.3, 8.92], 'bevel');
box('ferrule', [7.05, 0.55, 7.05], [8.95, 1.3, 8.95], 'metal');
box('end_cap', [7.35, 0.0, 7.35], [8.65, 0.55, 8.65], 'bevel');

// ---------------- placement: rotations, the held bake, UVs ----------------
const rad = (d) => d * Math.PI / 180;
const rot2 = (p, a) => [p[0] * Math.cos(rad(a)) - p[1] * Math.sin(rad(a)), p[0] * Math.sin(rad(a)) + p[1] * Math.cos(rad(a))];
/** Turns a box a quarter turn (sign = +1 counter-clockwise) about Z through o: exact, still axis-aligned. */
function quarter(from, to, o, sign) {
	const cs = [[from[0], from[1]], [to[0], to[1]]].map(([x, y]) => {
		const [rx, ry] = rot2([x - o[0], y - o[1]], 90 * sign);
		return [o[0] + rx, o[1] + ry];
	});
	return [[Math.min(cs[0][0], cs[1][0]), Math.min(cs[0][1], cs[1][1]), from[2]], [Math.max(cs[0][0], cs[1][0]), Math.max(cs[0][1], cs[1][1]), to[2]]];
}
/**
 * The element's own rotation, composed (held) with the -45 degree bake about (8, 8): R_bake(c) . R_own(o) is one
 * rotation by (own - 45) about o' = (I - R)^-1 d. A total outside -45..45 is brought in by a quarter turn of the box.
 */
function place(el, baked) {
	let from = el.from.slice(), to = el.to.slice();
	let angle = el.rot ? el.rot.angle : 0;
	let o = el.rot ? el.rot.origin.slice(0, 2) : [8, 8];
	let turns = 0;
	if (baked) {
		const c = [8, 8], total = angle - 45;
		// d = c + R_bake(o - c) - R_total(o)
		const rb = rot2([o[0] - c[0], o[1] - c[1]], -45), rt = rot2(o, total);
		const d = [c[0] + rb[0] - rt[0], c[1] + rb[1] - rt[1]];
		if (Math.abs(total) < 1e-9) {
			from = [from[0] + d[0], from[1] + d[1], from[2]]; to = [to[0] + d[0], to[1] + d[1], to[2]];
			return { from, to, angle: 0, origin: [8, 8, 8], turns };
		}
		// solve (I - R) o' = d
		const cs = Math.cos(rad(total)), sn = Math.sin(rad(total));
		const a11 = 1 - cs, a12 = sn, a21 = -sn, a22 = 1 - cs, det = a11 * a22 - a12 * a21;
		o = [(d[0] * a22 - a12 * d[1]) / det, (a11 * d[1] - a21 * d[0]) / det];
		angle = total;
	}
	while (angle < -45 - 1e-9) { [from, to] = quarter(from, to, o, -1); angle += 90; turns++; }
	while (angle > 45 + 1e-9) { [from, to] = quarter(from, to, o, 1); angle -= 90; turns++; }
	return { from, to, angle: r4(angle), origin: [r4(o[0]), r4(o[1]), 8], turns };
}

/** Block-projected UVs (Blockbench auto-UV), v wrapped every 16 px of height for this 24 px model (tiling textures). */
function span(a0, a1) {
	if (a0 >= -1e-6 && a1 <= 16 + 1e-6) return [a0, a1];
	if (a0 >= -16 - 1e-6 && a1 <= 1e-6) return [a0 + 16, a1 + 16];
	if (a1 - a0 <= 16) { const s = ((a0 % 16) + 16) % 16; return s + (a1 - a0) <= 16 ? [s, s + a1 - a0] : [0, a1 - a0]; }
	return [0, 16];
}
function autoUv(face, f, t) {
	const [x0, y0, z0] = f, [x1, y1, z1] = t;
	const uvp = (a, b) => [r4(a[0]), r4(b[0]), r4(a[1]), r4(b[1])];
	switch (face) {
		case 'north': return uvp(span(16 - x1, 16 - x0), span(16 - y1, 16 - y0));
		case 'south': return uvp(span(x0, x1), span(16 - y1, 16 - y0));
		case 'east': return uvp(span(16 - z1, 16 - z0), span(16 - y1, 16 - y0));
		case 'west': return uvp(span(z0, z1), span(16 - y1, 16 - y0));
		case 'up': return uvp(span(x0, x1), span(z0, z1));
		default: return uvp(span(x0, x1), span(16 - z1, 16 - z0));
	}
}
function buildElements(baked) {
	return ELEMENTS.map((el) => {
		const pl = place(el, baked);
		const from = pl.from.map(r4), to = pl.to.map(r4);
		for (const v of [...from, ...to]) if (v < -16 || v > 32) throw new Error(el.name + ' out of the -16..32 range: ' + v);
		const faces = {};
		const quarterTurned = pl.turns > 0;
		for (const face of ['north', 'east', 'south', 'west', 'up', 'down']) {
			const o = el.faces[face];
			faces[face] = { uv: o && !quarterTurned ? o.uv : autoUv(face, from, to), texture: '#' + (o && !quarterTurned ? o.tex : el.tex) };
		}
		const e = { __comment: el.name, from, to };
		if (pl.angle !== 0) e.rotation = { origin: pl.origin, axis: 'z', angle: pl.angle, rescale: false };
		e.faces = faces;
		delete e.__comment;
		return e;
	});
}

const TEXTURES = {
	metal: 'projecthero:item/stormbreaker_metal',
	bevel: 'projecthero:item/stormbreaker_bevel',
	edge: 'projecthero:item/stormbreaker_edge',
	rune: 'projecthero:item/stormbreaker_rune',
	handle: 'projecthero:item/stormbreaker_handle',
	grip: 'projecthero:item/stormbreaker_grip',
	blade: 'projecthero:item/stormbreaker_blade',
	particle: 'projecthero:item/stormbreaker_metal',
};
console.log('elements', ELEMENTS.length);
// element names for the clipping audit (scratchpad/v0177/stormbreaker_overlap_check.js)
fs.writeFileSync(path.join(__dirname, 'v0177/stormbreaker_names.json'), JSON.stringify(ELEMENTS.map((e) => e.name)) + '\n');

// --- fit a display rotation's projection of the baked model into `size` model px, centred ---
function rotXYZ(p, r) { // MC: rotationXYZ = Rx * Ry * Rz applied to the point
	let [x, y, z] = p;
	let c = Math.cos(rad(r[2])), s = Math.sin(rad(r[2]));[x, y] = [x * c - y * s, x * s + y * c];
	c = Math.cos(rad(r[1])); s = Math.sin(rad(r[1]));[x, z] = [x * c + z * s, -x * s + z * c];
	c = Math.cos(rad(r[0])); s = Math.sin(rad(r[0]));[y, z] = [y * c - z * s, y * s + z * c];
	return [x, y, z];
}
function bakedCorners() { // every corner of every element, rotated as the baked model draws it, relative to (8, 8, 8)
	const out = [];
	for (const el of buildElements(true)) for (let i = 0; i < 8; i++) {
		const p = [i & 1 ? el.to[0] : el.from[0], i & 2 ? el.to[1] : el.from[1], i & 4 ? el.to[2] : el.from[2]];
		if (el.rotation) {
			const o = el.rotation.origin, [rx, ry] = rot2([p[0] - o[0], p[1] - o[1]], el.rotation.angle);
			p[0] = o[0] + rx; p[1] = o[1] + ry;
		}
		out.push([p[0] - 8, p[1] - 8, p[2] - 8]);
	}
	return out;
}
function fit(rotation, size, lift = 0) {
	const pts = bakedCorners().map((p) => rotXYZ(p, rotation));
	const xs = pts.map((p) => p[0]), ys = pts.map((p) => p[1]);
	const w = Math.max(...xs) - Math.min(...xs), h = Math.max(...ys) - Math.min(...ys);
	const s = Math.round(size / Math.max(w, h) * 1000) / 1000;
	const cx = (Math.max(...xs) + Math.min(...xs)) / 2, cy = (Math.max(...ys) + Math.min(...ys)) / 2;
	return { rotation, translation: [r4(-cx * s), r4(-cy * s + lift), 0], scale: [s, s, s] };
}

// Hand contexts: the vanilla minecraft:item/handheld ROTATIONS ([0,-90,55] third person, [0,-90,25] first person,
// mirrored for the left hand), so it is held like every vanilla axe; translation / scale our own. Third person at scale
// 1.0 = true size (24 px = 1.5 blocks). The translations put the fist (the item origin ItemInHandLayer renders at) on
// the haft at GRIP_Y = 9 px from the pommel, like a greataxe -- the vanilla-style [0,4,1] held it at y 4.2, the very
// end; [0,-0.69,1.83] is that slid 4.77 px down the baked handle axis. First person: the same slide, a little smaller,
// pushed away so the whole head shows, the pommel end below the screen. Checked in the client screenshot harness
// (scratchpad/ThorWeaponsHarness.v0177.java.txt). ItemInHandLayerMixin's move-pose re-grip turns the item about that
// same origin, so it stays a turn about the fist.
const DISPLAY = {
	thirdperson_righthand: { rotation: [0, -90, 55], translation: [0, -0.69, 1.83], scale: [1, 1, 1] },
	thirdperson_lefthand: { rotation: [0, 90, -55], translation: [0, -0.69, 1.83], scale: [1, 1, 1] },
	firstperson_righthand: { rotation: [0, -90, 25], translation: [0, -1.0, -4.3], scale: [0.72, 0.72, 0.72] },
	firstperson_lefthand: { rotation: [0, 90, -25], translation: [0, -1.0, -4.3], scale: [0.72, 0.72, 0.72] },
	ground: fit([0, 0, 0], 12, 4),
	head: { rotation: [0, 180, 0], translation: [0, 13, 7], scale: [1, 1, 1] },
	gui: fit([0, 0, 0], 15.75),
	fixed: fit([0, 180, 0], 14),
};
const held = {
	credit: 'Stormbreaker 3D model (v0.14.19; rebuilt v0.16.1; rebuilt v0.17.7 at 1.5 blocks: faceted crescent blade of 22.5-degree segments with honed edge + bevel + body, chamfered head with rune band, stepped hammer poll, long Groot-branch haft, leather grip), generated by scratchpad/gen_v01419_stormbreaker_assets.js -- edit the generator, not this file. 24 px long (y 0..24, x/z centred on 8; elements run past the 16 px box), blade on -X, hammer face on +X, every element baked onto the vanilla tool icon diagonal (-45 degrees about Z, composed with each blade facet\'s own rotation). Hand contexts use the vanilla handheld rotations at scale 1.0 (true size); gui / fixed / ground are fitted by the generator.',
	textures: TEXTURES,
	elements: buildElements(true),
	display: DISPLAY,
};
const thrown = {
	credit: 'Stormbreaker as rendered by StormbreakerEntityRenderer (flying, resting, pinned): the same geometry as stormbreaker.json without the icon bake (the renderer needs the raw handle-up +Y axis), 24 px long, y 0..24. FIXED scale 1.0 = 1.5 blocks in the world; StormbreakerEntityRenderer FLAT_LIFT / FLAT_CENTRE / PINNED_LIFT / SPIN_PIVOT are derived from these bounds and this scale. Render-only, never in a creative tab.',
	textures: TEXTURES,
	elements: buildElements(false),
	display: { fixed: { scale: [1, 1, 1] } },
};
fs.writeFileSync(path.join(MODELS, 'stormbreaker.json'), JSON.stringify(held, null, 2) + '\n');
fs.writeFileSync(path.join(MODELS, 'stormbreaker_thrown.json'), JSON.stringify(thrown, null, 2) + '\n');
fs.writeFileSync(path.join(MODELS, 'unforged_stormbreaker.json'), JSON.stringify({
	parent: 'minecraft:item/handheld',
	textures: { layer0: 'projecthero:item/unforged_stormbreaker' },
}, null, 2) + '\n');
console.log('wrote models; gui', JSON.stringify(DISPLAY.gui), 'fixed', JSON.stringify(DISPLAY.fixed), 'ground', JSON.stringify(DISPLAY.ground));
