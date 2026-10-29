// v0.17.1 XLR8 revamp: the user's planetminecraft skin (xlr8_src.png) as textures/entity/omnitrix/xlr8.png, and a closed-visor
// copy (xlr8_mask.png): the hat layer's front painted as his blue-black visor, slid down over the face to the jaw, with a
// glossy streak and the green eyes glowing faintly through. Uses scratchpad/xlr8_mask.png instead when the user supplies one.
// Usage: node scratchpad/xlr8_v0171/gen_xlr8_skins.js
'use strict';
const fs = require('fs'), path = require('path');
const PNG = require('../pnglib.js');
const TEX = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'entity', 'omnitrix');
const src = PNG.read(path.join(__dirname, 'xlr8_src.png'));
PNG.write(path.join(TEX, 'xlr8.png'), 64, 64, src.px);

// the extras sheet (32x32) in the skin's palette: swatches at the rectangles Xlr8Client maps (u0, v0, w, h)
{
	const W = 32, ex = Buffer.alloc(W * W * 4);
	const hx = s => [parseInt(s.slice(0, 2), 16), parseInt(s.slice(2, 4), 16), parseInt(s.slice(4, 6), 16)];
	const put = (x, y, c) => { const i = (y * W + x) * 4; const v = hx(c); ex[i] = v[0]; ex[i + 1] = v[1]; ex[i + 2] = v[2]; ex[i + 3] = 255; };
	const fill = (x0, y0, w, h, fn) => { for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) put(x0 + x, y0 + y, fn(x, y, w, h)); };
	fill(0, 0, 4, 4, (x, y) => (x + y) % 3 ? '11131c' : '1a2330'); // BLACK
	fill(4, 0, 4, 4, (x, y) => y === 0 ? '55afb1' : '4a8694'); // BLUE (teal)
	fill(8, 0, 4, 4, (x, y) => y === 0 ? 'c2dde2' : '55afb1'); // BAND (light teal)
	fill(12, 0, 4, 4, (x, y) => y < 2 ? 'd4e9e9' : 'b0ccd6'); // WHITE (claws)
	fill(24, 0, 4, 4, () => '6cf04e'); // GLINT (eyes)
	// WHEEL: a dark ball with a teal tread band across the middle and a pale hub
	fill(16, 0, 8, 8, (x, y) => (y === 3 || y === 4) ? ((x % 2) ? '4a8694' : '55afb1') : (x === 0 || x === 7 || y === 0 || y === 7) ? '080910'
		: (x >= 3 && x <= 4 && (y === 2 || y === 5)) ? '3c6b76' : '1a2330');
	// TAIL: blue-black with a teal stripe down the top and dark bands
	fill(0, 8, 8, 8, (x, y) => (x === 3 || x === 4) ? (y % 4 === 0 ? '3c6b76' : '4a8694') : (y % 4 === 0 ? '080910' : (x === 0 || x === 7) ? '11131c' : '1a2330'));
	// VISOR (unused since the revamp; kept so the old UVs stay valid)
	fill(0, 16, 16, 8, (x, y) => y === 1 ? '4a8694' : '11131c');
	PNG.write(path.join(TEX, 'xlr8_extras.png'), W, W, ex);
}
// the eye glow textures: the skin's own green eye pixels only (face for the open visor, the hat front for the closed one)
function eyes(name, pts) {
	const e = Buffer.alloc(64 * 64 * 4);
	for (const [x, y, c] of pts) { const i = (y * 64 + x) * 4; const v = [parseInt(c.slice(0, 2), 16), parseInt(c.slice(2, 4), 16), parseInt(c.slice(4, 6), 16)]; e[i] = v[0]; e[i + 1] = v[1]; e[i + 2] = v[2]; e[i + 3] = 255; }
	PNG.write(path.join(TEX, name), 64, 64, e);
}
eyes('xlr8_eyes.png', [[9, 11, '6cf04e'], [14, 11, '6cf04e'], [9, 12, '6cf04e'], [10, 12, '6cf04e'], [13, 12, '6cf04e'], [14, 12, '6cf04e'],
	[41, 11, '6cf04e'], [46, 11, '6cf04e'], [42, 12, '6cf04e'], [45, 12, '6cf04e']]);
eyes('xlr8_mask_eyes.png', [[42, 11, '6cf04e'], [45, 11, '6cf04e'], [41, 11, '2f7a34'], [46, 11, '2f7a34'], [42, 12, '2f7a34'], [45, 12, '2f7a34'],
	[10, 11, '6cf04e'], [13, 11, '6cf04e'], [9, 11, '2f7a34'], [14, 11, '2f7a34'], [10, 12, '2f7a34'], [13, 12, '2f7a34']]);
const user = path.join(__dirname, '..', 'xlr8_mask.png');
if (fs.existsSync(user)) {
	const u = PNG.read(user);
	PNG.write(path.join(TEX, 'xlr8_mask.png'), 64, 64, u.px);
	console.log('used the user mask');
	process.exit(0);
}
const px = Buffer.from(src.px);
const hex = s => [parseInt(s.slice(0, 2), 16), parseInt(s.slice(2, 4), 16), parseInt(s.slice(4, 6), 16)];
const put = (x, y, c, a = 255) => { const i = (y * 64 + x) * 4; const v = hex(c); px[i] = v[0]; px[i + 1] = v[1]; px[i + 2] = v[2]; px[i + 3] = a; };
// the visor: rows 8..14 of the hat front (x 40..47); row 15 stays clear so the jaw shows under it
const rows = [
	'11131c 1a2330 1e2d3c 1e2d3c 1e2d3c 1e2d3c 1a2330 11131c',
	'11131c 1e2d3c 223544 223544 2b4556 4a8694 1e2d3c 11131c',
	'11131c 1e2d3c 223544 2b4556 4a8694 2b4556 1e2d3c 11131c',
	'11131c 2f7a34 6cf04e 223544 223544 6cf04e 2f7a34 11131c',
	'11131c 1e2d3c 2f7a34 4a8694 223544 2f7a34 1e2d3c 11131c',
	'11131c 1e2d3c 2b4556 223544 223544 223544 1e2d3c 11131c',
	'080910 11131c 1a2330 1a2330 1a2330 1a2330 11131c 080910',
];
rows.forEach((r, j) => r.split(' ').forEach((c, i) => put(40 + i, 8 + j, c)));
// and on the face itself (rows 8..14), so the visor shows even with the hat layer turned off in the skin settings
rows.forEach((r, j) => r.split(' ').forEach((c, i) => put(8 + i, 8 + j, c)));
for (let i = 0; i < 8; i++) { const k = ((15 * 64) + 40 + i) * 4; px[k + 3] = 0; }
// the visor's rim on the sides of the hat layer, the column next to the face (it wraps a little round the cheeks)
for (let y = 9; y <= 14; y++) { put(39, y, '11131c'); put(48, y, '11131c'); }
PNG.write(path.join(TEX, 'xlr8_mask.png'), 64, 64, px);
fs.copyFileSync(path.join(TEX, 'xlr8_mask.png'), path.join(__dirname, 'xlr8_mask_painted.png'));
console.log('painted the mask');
