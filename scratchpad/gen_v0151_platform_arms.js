// v0.15.1 platform arms: paints textures/block/suit_platform_arm.png (64x64), the texture of the Suit Platform's two
// robotic arms. The arm geometry itself is built in code (IronManSuitPlatformRenderer#ARM_* ModelParts, baked with
// LayerDefinition.bakeRoot()) -- the box list below MUST match the texOffs / sizes there.
// Run: node scratchpad/gen_v0151_platform_arms.js
const fs = require('fs');
const path = require('path');
const { encode, upscale } = require('./pngkit');

const W = 64, H = 64;
const px = Buffer.alloc(W * H * 4);

function put(x, y, c) {
	if (x < 0 || y < 0 || x >= W || y >= H) return;
	const i = (y * W + x) * 4;
	px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = c.length > 3 ? c[3] : 255;
}
function shade(c, k) { return [Math.max(0, Math.min(255, Math.round(c[0] * k))), Math.max(0, Math.min(255, Math.round(c[1] * k))), Math.max(0, Math.min(255, Math.round(c[2] * k)))]; }
// deterministic per-texel grain so the metal is not flat
function grain(x, y) { const s = Math.sin(x * 12.9898 + y * 78.233) * 43758.5453; return 0.94 + 0.12 * (s - Math.floor(s)); }

const GUNMETAL = [88, 94, 104];
const DARK = [46, 50, 58];
const STEEL = [150, 156, 166];
const RED = [168, 32, 28];
const GOLD = [196, 150, 60];
const CYAN = [90, 230, 255];

/** Fill a face rectangle: grain + 1px darker rim. */
function face(x0, y0, w, h, base, rim = 0.65) {
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		const edge = x === 0 || y === 0 || x === w - 1 || y === h - 1;
		put(x0 + x, y0 + y, shade(base, (edge ? rim : 1) * grain(x0 + x, y0 + y)));
	}
}

/**
 * Standard ModelPart cube layout for texOffs(u, v), size (w, h, d):
 *   top    (u+d,     v)     w x d      bottom (u+d+w, v)     w x d
 *   east   (u,       v+d)   d x h      north  (u+d,   v+d)   w x h
 *   west   (u+d+w,   v+d)   d x h      south  (u+2d+w, v+d)  w x h
 * Every arm box is modelled along +Z, so the long faces are top / bottom / east / west.
 */
function box(u, v, w, h, d, f) {
	const faces = {
		top: [u + d, v, w, d], bottom: [u + d + w, v, w, d],
		east: [u, v + d, d, h], north: [u + d, v + d, w, h],
		west: [u + d + w, v + d, d, h], south: [u + 2 * d + w, v + d, w, h],
	};
	for (const [name, r] of Object.entries(faces)) f(name, r[0], r[1], r[2], r[3]);
}

// MOUNT (0,0) 4x8x2 -- the bracket bolted to the gantry post: dark plate, gold bolts
box(0, 0, 4, 8, 2, (n, x, y, w, h) => {
	face(x, y, w, h, DARK);
	if (n === 'north' || n === 'south') {
		put(x + 1, y + 1, GOLD); put(x + w - 2, y + 1, GOLD); put(x + 1, y + h - 2, GOLD); put(x + w - 2, y + h - 2, GOLD);
		for (let yy = 2; yy < h - 2; yy++) put(x + (w >> 1), y + yy, shade(RED, 0.9));
	}
});
// HUB (16,0) 4x4x4 -- joint housings: gunmetal with a cyan servo eye on the two sides
box(16, 0, 4, 4, 4, (n, x, y, w, h) => {
	face(x, y, w, h, GUNMETAL, 0.55);
	if (n === 'east' || n === 'west') { put(x + 1, y + 1, CYAN); put(x + 2, y + 1, CYAN); put(x + 1, y + 2, CYAN); put(x + 2, y + 2, CYAN); }
});
// PALM (32,0) 5x5x2 -- the gripper palm: red Stark plate with a steel rim
box(32, 0, 5, 5, 2, (n, x, y, w, h) => {
	face(x, y, w, h, n === 'north' || n === 'south' ? RED : STEEL, 0.6);
	if (n === 'north') put(x + 2, y + 2, CYAN);
});
// FINGER (46,0) 1x2x5 -- clamp jaws: polished steel, dark tips
box(46, 0, 1, 2, 5, (n, x, y, w, h) => {
	face(x, y, w, h, STEEL, 0.8);
	if (n === 'east' || n === 'west' || n === 'top' || n === 'bottom') {
		// the far end (high z) of a long face is the jaw tip
		const tipAtStart = n === 'top' || n === 'bottom';
		for (let k = 0; k < (n === 'top' || n === 'bottom' ? w : h); k++) {
			if (tipAtStart) put(x + k, y, DARK); else put(x, y + k, DARK);
		}
	}
});
// LINK (0,10) 3x3x15 -- upper arm / forearm: gunmetal, a red stripe down both sides, dark panel seams
box(0, 10, 3, 3, 15, (n, x, y, w, h) => {
	face(x, y, w, h, GUNMETAL, 0.7);
	const long = w > h ? 'x' : 'y';
	const len = Math.max(w, h);
	for (let k = 3; k < len - 3; k += 5) {
		// panel seams across the link
		if (long === 'x') for (let j = 0; j < h; j++) put(x + k, y + j, shade(DARK, 1.1));
		else for (let j = 0; j < w; j++) put(x + j, y + k, shade(DARK, 1.1));
	}
	if (n === 'east' || n === 'west') {
		// red stripe along the middle row of the long side
		if (long === 'x') for (let k = 1; k < w - 1; k++) put(x + k, y + 1, RED);
		else for (let k = 1; k < h - 1; k++) put(x + 1, y + k, RED);
	}
});
// PISTON (36,10) 1x1x10 -- hydraulic ram: chrome with a dark sleeve half
box(36, 10, 1, 1, 10, (n, x, y, w, h) => {
	for (let yy = 0; yy < h; yy++) for (let xx = 0; xx < w; xx++) {
		const along = w > h ? xx / w : yy / h;
		put(x + xx, y + yy, shade(along < 0.45 ? DARK : [200, 205, 212], grain(x + xx, y + yy)));
	}
});
// LAMP (36,22) 1x1x6 -- the cyan status strip (drawn full-bright)
box(36, 22, 1, 1, 6, (n, x, y, w, h) => {
	for (let yy = 0; yy < h; yy++) for (let xx = 0; xx < w; xx++) put(x + xx, y + yy, shade(CYAN, 0.9 + 0.1 * ((xx + yy) & 1)));
});

const out = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'block', 'suit_platform_arm.png');
fs.writeFileSync(out, encode(W, H, px));
const prev = upscale({ w: W, h: H, px }, 8, true);
fs.mkdirSync(path.join(__dirname, 'shots_v0151'), { recursive: true });
fs.writeFileSync(path.join(__dirname, 'shots_v0151', 'P_arm_texture_preview.png'), encode(prev.w, prev.h, prev.px));
console.log('wrote', out);
