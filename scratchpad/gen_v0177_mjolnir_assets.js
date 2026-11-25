// v0.17.7 Mjolnir rebuild: generates mjolnir.json (held, -45 degree icon bake), mjolnir_thrown.json (the same geometry
// un-rotated, for MjolnirEntityRenderer) and every Mjolnir texture. Re-runnable; edit this, not the outputs.
// Run from the repo root: node scratchpad/gen_v0177_mjolnir_assets.js
//
// The film Uru hammer, kept inside the old bounding convention (x / z centred on 8, the head's top at y 14.375, the
// pommel at y 0.5) because MjolnirEntityRenderer.UPRIGHT_LIFT and the vanilla mace hand transforms are tuned to it:
//  - a rectangular head 9 x 4.5 x 4.5 with every edge chamfered in two steps (a 0.75 cut rounded by a 0.5 step), so
//    all twelve edges and the four corners catch a light bevel line;
//  - Celtic knotwork panels on both broad sides (a closed two-strand knot, over-under at each crossing, in a recessed
//    frame), framed striking faces on the ends, a plain brushed top;
//  - a stepped collar under the head, a short leather-wrapped grip between two Uru bands, a chamfered pommel with an
//    eyelet and a leather wrist strap loop hanging from it.
// Every texture is 64 x 64 and every face maps at 4 texels per model pixel, so the texel grid is the same size all
// over the hammer. One light direction everywhere (from above, a little to the left), a soft diagonal sheen, no noise
// beyond a faint brushing.
const fs = require('fs');
const path = require('path');
const { encode } = require('./pngkit.js');

const ROOT = path.join(__dirname, '../src/main/resources/assets/projecthero');
const TEX = path.join(ROOT, 'textures/item');
const MODELS = path.join(ROOT, 'models/item');
const N = 64; // texture size; 16 UV units = 64 texels, 1 model pixel = 4 texels

const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));
const write = (name, w, h, fn) => {
	const px = Buffer.alloc(w * h * 4);
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		const c = fn(x, y);
		const i = (y * w + x) * 4;
		if (!c) continue;
		px[i] = clamp(c[0]); px[i + 1] = clamp(c[1]); px[i + 2] = clamp(c[2]); px[i + 3] = c.length > 3 ? clamp(c[3]) : 255;
	}
	fs.writeFileSync(path.join(TEX, name + '.png'), encode(w, h, px));
	console.log('wrote', name, w + 'x' + h);
};
const hash = (x, y, s) => { const v = Math.sin(x * 127.1 + y * 311.7 + s * 74.7) * 43758.5453; return v - Math.floor(v); };
const add = (c, k) => [c[0] + k, c[1] + k, c[2] + k];
const mix = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];

// ---------------- palette ----------------
// Uru: a cool, slightly blue-grey steel. Five steps from deep recess to specular.
const URU = { deep: [58, 61, 70], dark: [92, 96, 106], mid: [136, 140, 150], light: [174, 178, 188], hi: [214, 218, 226] };
const LEATHER = { deep: [38, 24, 17], dark: [64, 41, 28], mid: [96, 63, 42], light: [130, 89, 60], hi: [158, 114, 80] };
/** The faint brushing every Uru surface carries (horizontal streaks, +-3) plus a broad soft sheen band (+-8). */
const brush = (x, y, s) => (hash(0, y, s) - 0.5) * 5 + (hash(Math.floor(x / 9), y, s + 1) - 0.5) * 2;
const sheen = (x, y) => 9 * Math.max(0, Math.cos((x * 0.55 - y) * 0.11)) - 4;

// ---------------- mjolnir_head: an atlas of the head's faces ----------------
// Regions (texels): side panel (0,0) 30x12, top (0,12) 30x12, bottom (0,24) 30x12, end face (32,0) 12x12.
const SIDE = { x: 0, y: 0, w: 30, h: 12 };
const TOP = { x: 0, y: 12, w: 30, h: 12 };
const BOTTOM = { x: 0, y: 24, w: 30, h: 12 };
const END = { x: 32, y: 0, w: 12, h: 12 };
const uvOf = (r) => [r.x / 4, r.y / 4, (r.x + r.w) / 4, (r.y + r.h) / 4];

// The knot, as pixel art on the 26 x 8 recessed panel (cols 2..27, rows 2..9): two 2-pixel strands zig-zagging between
// the top and bottom of the panel in opposite phase, joined by an upright bar at each end into one closed knot. They
// cross twice (a closed figure-eight interlace); A runs over at the first crossing, B at the second, and the under strand is cut either side of
// each crossing so the over-under reads. Each strand is lit along its upper pixel and shaded along its lower one, with a
// dark outline round it against the recess.
const KNOT = (() => {
	const W = 26, H = 8;
	const g = Array.from({ length: H }, () => new Array(W).fill(null)); // null = recess, else {s: 'A'|'B', lit}
	const yA = (x) => Math.round(6 - Math.abs((((x - 1) / 2) % 12 + 12) % 12 - 6));
	const crossX = [7, 19];
	const over = (x) => { let k = 0; crossX.forEach((c, i) => { if (Math.abs(c - x) < Math.abs(crossX[k] - x)) k = i; }); return k % 2 === 0 ? 'A' : 'B'; };
	const cut = new Set();
	for (let x = 1; x <= 24; x++) {
		const a = yA(x), b = 6 - a;
		const nearCross = crossX.some((c) => x >= c - 2 && x <= c + 1);
		for (const [s, y] of [['A', a], ['B', b]]) {
			if (nearCross && s !== over(x)) {
				// the under strand: drawn only where it is clear of the over strand, with a one-pixel gap round it
				for (const yy of [y, y + 1]) {
					const ov = over(x) === 'A' ? a : b;
					if (yy === ov || yy === ov + 1) continue; // the over strand's own pixels
					if (Math.abs(yy - ov) <= 1 || Math.abs(yy - (ov + 1)) <= 1) cut.add(x + ',' + yy);
					else if (!g[yy][x]) g[yy][x] = { s, lit: yy === y };
				}
				continue;
			}
			g[y][x] = { s, lit: true };
			if (!g[y + 1][x] || g[y + 1][x].s !== over(x)) g[y + 1][x] = { s, lit: false };
		}
	}
	for (let y = 0; y < H; y++) { g[y][0] = { s: 'B', lit: true, bar: true }; g[y][25] = { s: 'A', lit: false, bar: true }; }
	const at = (x, y) => (x < 0 || y < 0 || x >= W || y >= H) ? null : g[y][x];
	return { at, cut, W, H };
})();
function knotPixel(x, y) {
	// the frame: a lit rim, a groove, then the recessed panel
	if (y === 0) return add(URU.hi, -6 + sheen(x, y) * 0.5);
	if (y === 11) return URU.dark;
	if (x === 0 || x === 29) return add(URU.mid, 6);
	if (y === 1 || y === 10 || x === 1 || x === 28) { // the groove round the panel; its bottom-right lip catches light
		if (y === 10 || x === 28) return add(URU.light, -8);
		return URU.deep;
	}
	const kx = x - 2, ky = y - 2;
	const c = KNOT.at(kx, ky);
	if (c && !KNOT.cut.has(kx + ',' + ky)) {
		if (c.bar) return kx === 0 ? URU.light : add(URU.mid, -2);
		return c.lit ? URU.hi : URU.light;
	}
	// outline: next to a strand pixel
	for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
		const n = KNOT.at(kx + dx, ky + dy);
		if (n && !KNOT.cut.has((kx + dx) + ',' + (ky + dy))) return add(URU.deep, -12);
	}
	return add(mix(URU.dark, URU.deep, 0.3), brush(x, y, 9) * 0.5);
}
function plainPixel(x, y, w, h, base) { // top / bottom: brushed plate with a lit top rim and a shaded bottom rim
	if (y === 0) return add(URU.hi, -8);
	if (y === h - 1) return add(base, -26);
	if (x === 0) return add(base, 10);
	if (x === w - 1) return add(base, -14);
	return add(base, brush(x, y, 3) + sheen(x, y));
}
function endPixel(x, y) { // a striking face: lit rim, inner chamfer line, a slightly domed plate with a soft hot spot
	const e = Math.min(x, y, 11 - x, 11 - y);
	if (e === 0) return (x === 0 || y === 0) ? add(URU.hi, -6) : URU.dark;
	if (e === 1) return (x === 1 || y === 1) ? add(URU.mid, -6) : add(URU.light, -4);
	const d = Math.hypot(x - 4.5, y - 4.5);
	return add(mix(URU.light, URU.mid, Math.min(1, d / 7)), brush(x, y, 5) * 0.6 + (d < 1.6 ? 12 : 0));
}
write('mjolnir_head', N, N, (x, y) => {
	const inR = (r) => x >= r.x && x < r.x + r.w && y >= r.y && y < r.y + r.h;
	if (inR(SIDE)) return knotPixel(x - SIDE.x, y - SIDE.y);
	if (inR(TOP)) return plainPixel(x - TOP.x, y - TOP.y, TOP.w, TOP.h, URU.light);
	if (inR(BOTTOM)) return plainPixel(x - BOTTOM.x, y - BOTTOM.y, BOTTOM.w, BOTTOM.h, URU.mid);
	if (inR(END)) return endPixel(x - END.x, y - END.y);
	return URU.mid;
});

// ---------------- mjolnir_metal: plain brushed Uru (collar, bands, pommel), block-projected ----------------
write('mjolnir_metal', N, N, (x, y) => add(URU.mid, brush(x, y, 1) + sheen(x, y) * 0.8));
// ---------------- mjolnir_bevel: the chamfer steps -- bright, almost flat, so every edge reads as a highlight ----------------
write('mjolnir_bevel', 16, 16, (x, y) => add(mix(URU.light, URU.hi, 0.35), (hash(x, y, 4) - 0.5) * 3));

// ---------------- mjolnir_leather: the spiral grip wrap, block-projected (4 texels per model pixel) ----------------
// A wrap every 6 texels along the handle, slanted (the strap winds round), each wrap shaded round with a dark seam
// below it and a lit lip above.
write('mjolnir_leather', N, N, (x, y) => {
	const b = ((y + x * 0.5) % 6 + 6) % 6;
	let c;
	if (b < 1) c = LEATHER.deep; // seam
	else if (b < 2) c = LEATHER.light; // lit lip of the wrap below the seam
	else if (b < 4) c = LEATHER.mid;
	else c = LEATHER.dark;
	return add(c, (hash(x, y, 7) - 0.5) * 4);
});
// ---------------- mjolnir_strap: the wrist strap (a flat band with a stitched line down its middle) ----------------
write('mjolnir_strap', 16, 16, (x, y) => {
	if (x === 0 || x === 15) return LEATHER.deep;
	if (x === 1) return LEATHER.light;
	if (x === 14) return LEATHER.dark;
	if (x === 7 || x === 8) return (y % 4 < 2) ? add(LEATHER.hi, 8) : LEATHER.dark; // stitching
	return add(LEATHER.mid, (hash(x, y, 8) - 0.5) * 4);
});

// ---------------- geometry (head-up, local +Y) ----------------
const ELEMENTS = [];
const r3 = (v) => Math.round(v * 10000) / 10000;
/** faces: per-face {tex, uv} overrides; anything not given is block-projected with `tex`. */
const box = (from, to, tex, faces = {}) => ELEMENTS.push({ from: from.map(r3), to: to.map(r3), tex, faces });

// The head: x 3.5..12.5, y 9.875..14.375, z 5.75..10.25, chamfer 0.75 with a 0.5 rounding step.
const H0 = [3.5, 9.875, 5.75], H1 = [12.5, 14.375, 10.25], C = 0.75, R = 0.5;
const inset = (ix, iy, iz) => [[H0[0] + ix, H0[1] + iy, H0[2] + iz], [H1[0] - ix, H1[1] - iy, H1[2] - iz]];
{
	const [f, t] = inset(C, C, 0); // the broad sides: knot panels
	box(f, t, 'bevel', { north: { tex: 'head', uv: uvOf(SIDE) }, south: { tex: 'head', uv: uvOf(SIDE) } });
}
{
	const [f, t] = inset(C, 0, C); // the top and bottom
	box(f, t, 'bevel', { up: { tex: 'head', uv: uvOf(TOP) }, down: { tex: 'head', uv: uvOf(BOTTOM) } });
}
{
	const [f, t] = inset(0, C, C); // the striking ends
	box(f, t, 'bevel', { east: { tex: 'head', uv: uvOf(END) }, west: { tex: 'head', uv: uvOf(END) } });
}
{
	const [f, t] = inset(R, R, R); // the rounding step: a bright line along every edge
	box(f, t, 'bevel');
}
// the collar under the head, stepped down to the handle, and the top band
box([6.625, 9.375, 6.625], [9.375, 9.875, 9.375], 'metal');
box([6.875, 8.875, 6.875], [9.125, 9.375, 9.125], 'bevel');
box([7.0, 8.375, 7.0], [9.0, 8.875, 9.0], 'metal');
// the leather grip between the bands
box([7.0625, 2.75, 7.0625], [8.9375, 8.375, 8.9375], 'leather');
// the lower band, the chamfered pommel and its eyelet
box([6.9375, 2.125, 6.9375], [9.0625, 2.75, 9.0625], 'metal');
box([6.75, 1.25, 6.75], [9.25, 1.875, 9.25], 'metal');
box([7.0, 1.0, 7.0], [9.0, 2.125, 9.0], 'bevel');
box([7.5, 0.5, 7.625], [8.5, 1.0, 8.375], 'metal');
// the wrist strap: a loop of flat leather band hanging from the eyelet (thin in z)
const SZ = [7.875, 8.125];
const strap = (f, t) => box([f[0], f[1], SZ[0]], [t[0], t[1], SZ[1]], 'strap', {
	north: { tex: 'strap', uv: [0, 0, 16, 16] }, south: { tex: 'strap', uv: [0, 0, 16, 16] },
	east: { tex: 'strap', uv: [7, 0, 9, 16] }, west: { tex: 'strap', uv: [7, 0, 9, 16] },
	up: { tex: 'strap', uv: [0, 7, 16, 9] }, down: { tex: 'strap', uv: [0, 7, 16, 9] },
});
strap([7.25, -1.75], [7.75, 0.75]);
strap([8.25, -1.75], [8.75, 0.75]);
strap([7.5, -2.25], [8.5, -1.75]);
strap([7.375, -2.0], [7.625, -1.75]);
strap([8.375, -2.0], [8.625, -1.75]);

// ---------------- UVs + output ----------------
/** Block projection (Blockbench auto-UV): UV = model coordinate, so 1 model pixel = 4 texels of a 64 px texture. */
function projUv(a0, a1, b0, b1) {
	const c = (v) => Math.max(0, Math.min(16, r3(v)));
	return [c(a0), c(b0), c(a1), c(b1)];
}
function autoUv(face, f, t) {
	const [x0, y0, z0] = f, [x1, y1, z1] = t;
	switch (face) {
		case 'north': return projUv(16 - x1, 16 - x0, 16 - y1, 16 - y0);
		case 'south': return projUv(x0, x1, 16 - y1, 16 - y0);
		case 'east': return projUv(16 - z1, 16 - z0, 16 - y1, 16 - y0);
		case 'west': return projUv(z0, z1, 16 - y1, 16 - y0);
		case 'up': return projUv(x0, x1, z0, z1);
		default: return projUv(x0, x1, 16 - z1, 16 - z0);
	}
}
function buildElements(baked) {
	return ELEMENTS.map((el) => {
		const faces = {};
		for (const face of ['north', 'east', 'south', 'west', 'up', 'down']) {
			const o = el.faces[face];
			faces[face] = { uv: o ? o.uv : autoUv(face, el.from, el.to), texture: '#' + (o ? o.tex : el.tex) };
		}
		const e = { from: el.from, to: el.to };
		if (baked) e.rotation = { origin: [8, 8, 8], axis: 'z', angle: -45, rescale: false };
		e.faces = faces;
		return e;
	});
}

const TEXTURES = {
	head: 'projecthero:item/mjolnir_head',
	metal: 'projecthero:item/mjolnir_metal',
	bevel: 'projecthero:item/mjolnir_bevel',
	leather: 'projecthero:item/mjolnir_leather',
	strap: 'projecthero:item/mjolnir_strap',
	particle: 'projecthero:item/mjolnir_metal',
};

// Hand / ground / head contexts unchanged from the pre-0.17.7 model (vanilla minecraft:item/handheld_mace and
// minecraft:item/generated): the bounding box is the same, so they still fit. gui and fixed are re-fitted below so the
// hammer fills the slot / frame instead of floating small in the middle.
const DISPLAY = {
	thirdperson_righthand: { rotation: [0, -90, 55], translation: [0, 4, 1], scale: [1, 1, 1] },
	thirdperson_lefthand: { rotation: [0, 90, -55], translation: [0, 4, 1], scale: [1, 1, 1] },
	firstperson_righthand: { rotation: [0, -90, 25], translation: [0, 3, 0.8], scale: [0.9, 0.9, 0.9] },
	firstperson_lefthand: { rotation: [0, 90, -25], translation: [0, 3, 0.8], scale: [0.9, 0.9, 0.9] },
	ground: { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.5, 0.5, 0.5] },
	head: { rotation: [0, 180, 0], translation: [0, 13, 7], scale: [1, 1, 1] },
	gui: null,
	fixed: null,
};

// --- fit a display rotation's projection of the baked model into a square of `size` model px, centred ---
const rad = (d) => d * Math.PI / 180;
function rotXYZ(p, r) { // MC: rotationXYZ = Rx * Ry * Rz applied to the point
	let [x, y, z] = p;
	let c = Math.cos(rad(r[2])), s = Math.sin(rad(r[2]));[x, y] = [x * c - y * s, x * s + y * c];
	c = Math.cos(rad(r[1])); s = Math.sin(rad(r[1]));[x, z] = [x * c + z * s, -x * s + z * c];
	c = Math.cos(rad(r[0])); s = Math.sin(rad(r[0]));[y, z] = [y * c - z * s, y * s + z * c];
	return [x, y, z];
}
function bakedCorners() {
	const out = [];
	const c = Math.cos(rad(-45)), s = Math.sin(rad(-45));
	for (const el of ELEMENTS) for (let i = 0; i < 8; i++) {
		const p = [i & 1 ? el.to[0] : el.from[0], i & 2 ? el.to[1] : el.from[1], i & 4 ? el.to[2] : el.from[2]];
		const x = p[0] - 8, y = p[1] - 8;
		out.push([x * c - y * s, x * s + y * c, p[2] - 8]);
	}
	return out;
}
function fit(rotation, size) {
	const pts = bakedCorners().map((p) => rotXYZ(p, rotation));
	const xs = pts.map((p) => p[0]), ys = pts.map((p) => p[1]);
	const w = Math.max(...xs) - Math.min(...xs), h = Math.max(...ys) - Math.min(...ys);
	const s = Math.round(size / Math.max(w, h) * 1000) / 1000;
	const cx = (Math.max(...xs) + Math.min(...xs)) / 2, cy = (Math.max(...ys) + Math.min(...ys)) / 2;
	return { rotation, translation: [r3(-cx * s), r3(-cy * s), 0], scale: [s, s, s] };
}
DISPLAY.gui = fit([30, 225, 0], 15.5);
DISPLAY.fixed = fit([0, 180, 0], 14);

const held = {
	credit: 'Mjolnir (rebuilt v0.17.7), generated by scratchpad/gen_v0177_mjolnir_assets.js -- edit the generator, not this file. Two-step chamfered Uru head with Celtic knot side panels and framed striking faces, stepped collar, leather grip between Uru bands, chamfered pommel, eyelet and wrist strap loop; 64x64 textures at 4 texels per model pixel. Bounding convention kept from the old model (x/z centred on 8, head top at y 14.375, pommel at y 0.5; the strap hangs below to y -2.25): MjolnirEntityRenderer.UPRIGHT_LIFT and the vanilla handheld_mace hand transforms depend on it. Every element carries the -45 degree Z bake (origin 8,8,8) onto the vanilla tool icon diagonal; gui / fixed are fitted to the slot by the generator.',
	textures: TEXTURES,
	elements: buildElements(true),
	display: DISPLAY,
};
const thrown = {
	credit: 'Mjolnir as rendered by MjolnirEntityRenderer for the flying / resting hammer: the same geometry as mjolnir.json with NO per-element rotation (the renderer needs the raw head-up local +Y axis). Bounds x/z centred on 8, head top at y 14.375 -- MjolnirEntityRenderer.UPRIGHT_LIFT is computed from that top and this "fixed" scale, so neither may change alone. Generated by scratchpad/gen_v0177_mjolnir_assets.js. Render-only, never in a creative tab.',
	textures: TEXTURES,
	elements: buildElements(false),
	display: { fixed: { scale: [1.15, 1.15, 1.15] } },
};
fs.writeFileSync(path.join(MODELS, 'mjolnir.json'), JSON.stringify(held, null, 2) + '\n');
fs.writeFileSync(path.join(MODELS, 'mjolnir_thrown.json'), JSON.stringify(thrown, null, 2) + '\n');
console.log('elements', ELEMENTS.length, 'gui', JSON.stringify(DISPLAY.gui), 'fixed', JSON.stringify(DISPLAY.fixed));
