// v0.17.5 Sentry & the Void: every texture, model and blockstate the power ships, drawn from scratch here (no borrowed art).
//   node scratchpad/gen_sentry_v0175.js [root]
// Sources: all original pixel work by this script. The Sentry's chest emblem is an ORIGINAL sun-burst (a ringed sun with
// alternating long/short rays), not the comics' "S" logo. Colours: gold suit, royal-blue gloves / boots / side stripes / belt.
// Void Form: black body with gold veins (dim veins painted in, bright ones in the emissive *_glow texture), black eyes, smoky hat layer.
'use strict';
const fs = require('fs');
const path = require('path');
const { encodePng } = require('./lib_png.js');

const root = process.argv[2] || process.cwd();
const A = p => path.join(root, 'src/main/resources/assets/projecthero', p);
const mk = f => fs.mkdirSync(path.dirname(f), { recursive: true });

function rng(seed) { let s = seed >>> 0; return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; }; }
const hex = (h, a = 255) => [(h >> 16) & 255, (h >> 8) & 255, h & 255, a];
const mix = (c, d, t) => [0, 1, 2].map(i => Math.round(c[i] + (d[i] - c[i]) * t)).concat([Math.round(c[3] + (d[3] - c[3]) * t)]);
const shade = (c, f) => [Math.max(0, Math.min(255, Math.round(c[0] * f))), Math.max(0, Math.min(255, Math.round(c[1] * f))), Math.max(0, Math.min(255, Math.round(c[2] * f))), c[3]];

function img(w, h) { return { w, h, px: Buffer.alloc(w * h * 4) }; }
function set(im, x, y, c) { if (!c || x < 0 || y < 0 || x >= im.w || y >= im.h) return; const i = (y * im.w + x) * 4; im.px[i] = c[0]; im.px[i + 1] = c[1]; im.px[i + 2] = c[2]; im.px[i + 3] = c[3]; }
function get(im, x, y) { const i = (y * im.w + x) * 4; return [im.px[i], im.px[i + 1], im.px[i + 2], im.px[i + 3]]; }
function save(im, file) { mk(file); fs.writeFileSync(file, encodePng(im.w, im.h, im.px)); console.log('wrote', path.relative(root, file)); }

// ---------------------------------------------------------------- the player-skin box layout
// a cuboid of (w, h, d) whose texture starts at (u, v)
function faces(u, v, w, h, d) {
	return {
		top: [u + d, v, w, d], bottom: [u + d + w, v, w, d],
		right: [u, v + d, d, h], front: [u + d, v + d, w, h], left: [u + d + w, v + d, d, h], back: [u + d + w + d, v + d, w, h],
	};
}
function parts(slim) {
	const aw = slim ? 3 : 4;
	return {
		head: faces(0, 0, 8, 8, 8), hat: faces(32, 0, 8, 8, 8),
		body: faces(16, 16, 8, 12, 4),
		rightArm: faces(40, 16, aw, 12, 4), leftArm: faces(32, 48, aw, 12, 4),
		rightLeg: faces(0, 16, 4, 12, 4), leftLeg: faces(16, 48, 4, 12, 4),
	};
}
// paints every face of a part: fn(face, x, y, fw, fh) -> colour or null
function paint(im, part, fn) {
	for (const [face, [u, v, fw, fh]] of Object.entries(part)) {
		for (let y = 0; y < fh; y++) for (let x = 0; x < fw; x++) set(im, u + x, v + y, fn(face, x, y, fw, fh));
	}
}

// ---------------------------------------------------------------- the Sentry suit
const GOLD = hex(0xE8B52A), GOLD_HI = hex(0xFFD860), GOLD_LO = hex(0xB0801A);
const BLUE = hex(0x2652D8), BLUE_LO = hex(0x17349A), BLUE_HI = hex(0x4F7CFF);
const SUN = hex(0xFFF4C0), WHITE_GOLD = hex(0xFFE890);

// the original sun-burst emblem on the 8x12 chest (rows 0..7): a white-gold sun in a blue ring, eight rays (not the comics' S)
const EMBLEM = [
	'...rr...',
	'.r....r.',
	'..rbbr..',
	'r.bccb.r',
	'rrbccbrr',
	'r.bccb.r',
	'..rbbr..',
	'.r....r.',
];
function emblem(x, y) {
	const c = y < EMBLEM.length ? EMBLEM[y][x] : '.';
	return c === 'c' ? 'core' : c === 'b' ? 'ring' : c === 'r' ? 'ray' : null;
}
function suitPixel(partName, face, x, y, fw, fh, glow) {
	const edge = (x === 0 || x === fw - 1) ? 0.9 : 1.0;
	if (partName === 'head' || partName === 'hat') return null; // his own head and hair
	if (partName === 'body') {
		if (face === 'front') {
			const e = emblem(x, y);
			if (e === 'core') return glow ? SUN : SUN;
			if (e === 'ring') return glow ? hex(0x6E9BFF) : BLUE_HI;
			if (e === 'ray') return glow ? WHITE_GOLD : WHITE_GOLD;
			if (y === 8 || y === 9) return glow ? (x >= 3 && x <= 4 ? GOLD_HI : null) : (x >= 3 && x <= 4 ? GOLD_HI : BLUE);
			if (glow) return null;
			return shade(y < 8 ? GOLD : GOLD_LO, edge * (1.0 - y * 0.012));
		}
		if (glow) return (face === 'back' && (y === 8 || y === 9) && false) ? GOLD_HI : null;
		if (y === 8 || y === 9) return BLUE;
		if (face === 'right' || face === 'left') return (x === 1 || x === 2) ? BLUE_LO : shade(GOLD, 0.92);
		if (face === 'back') return shade(GOLD, 0.9 * edge);
		if (face === 'top') return GOLD_HI;
		return GOLD_LO;
	}
	if (partName === 'rightArm' || partName === 'leftArm') {
		if (glow) return (y === 7 && face !== 'top' && face !== 'bottom') ? GOLD_HI : null;
		if (face === 'top') return GOLD_HI;
		if (face === 'bottom') return BLUE_LO;
		if (y >= 8) return shade(BLUE, y === 8 ? 1.15 : edge);
		if (y === 7) return GOLD_HI;
		if (face === 'right' || face === 'left') return (x === 1 || x === 2) && face === (partName === 'rightArm' ? 'right' : 'left') ? BLUE_LO : shade(GOLD, 0.92);
		return shade(GOLD, edge * (1.0 - y * 0.01));
	}
	if (partName === 'rightLeg' || partName === 'leftLeg') {
		if (glow) return null;
		if (face === 'top') return GOLD_LO;
		if (face === 'bottom') return BLUE_LO;
		if (y >= 7) return shade(BLUE, y === 7 ? 1.15 : edge * (y === 11 ? 0.8 : 1.0));
		if (face === 'right' || face === 'left') return (x === 1 || x === 2) ? BLUE_LO : shade(GOLD, 0.9);
		return shade(GOLD, edge * 0.97);
	}
	return null;
}
function sentry(slim, glow) {
	const im = img(64, 64);
	for (const [name, part] of Object.entries(parts(slim))) {
		paint(im, part, (face, x, y, fw, fh) => suitPixel(name, face, x, y, fw, fh, glow));
	}
	return im;
}

// ---------------------------------------------------------------- the Void Form
const VOIDC = hex(0x0B0910), VOID_HI = hex(0x1A1522), VEIN_DIM = hex(0x6A4512), VEIN = hex(0xFFB52A), VEIN_HOT = hex(0xFFE08A);
// a fixed set of vein pixels per part face (random walks), shared by the base and the glow
function veinMask(seed, fw, fh, walks) {
	const r = rng(seed), m = new Set();
	for (let k = 0; k < walks; k++) {
		let x = Math.floor(r() * fw), y = Math.floor(r() * fh);
		const len = 3 + Math.floor(r() * (fh * 0.9));
		for (let i = 0; i < len; i++) {
			m.add(x + ',' + y);
			const t = r();
			if (t < 0.55) y += 1; else if (t < 0.75) x += 1; else if (t < 0.95) x -= 1; else y -= 1;
			x = Math.max(0, Math.min(fw - 1, x)); y = Math.max(0, Math.min(fh - 1, y));
		}
	}
	return m;
}
function voidForm(slim, glow) {
	const im = img(64, 64);
	let seed = 7;
	for (const [name, part] of Object.entries(parts(slim))) {
		const masks = {};
		for (const [face, [, , fw, fh]] of Object.entries(part)) {
			seed += 101;
			const walks = name === 'head' ? (face === 'front' ? 3 : 1) : name === 'hat' ? 0 : face === 'front' || face === 'back' ? 3 : 2;
			masks[face] = veinMask(seed, fw, fh, walks);
		}
		const noise = rng(seed * 3);
		paint(im, part, (face, x, y, fw, fh) => {
			const vein = masks[face].has(x + ',' + y);
			if (name === 'hat') {
				// smoke rising off the head: sparse dark wisps on the hat layer
				if (glow) return null;
				const n = noise();
				if (face === 'top') return n < 0.35 ? hex(0x14101C, 150) : null;
				return (y < 3 && n < 0.30) ? hex(0x14101C, 120) : null;
			}
			if (name === 'head' && face === 'front') {
				// black eyes (glossy, a cold glint), a dark mouth; veins crack out from the eyes
				const eye = y === 4 && (x === 1 || x === 2 || x === 5 || x === 6);
				if (eye) return glow ? null : (x === 2 || x === 5 ? hex(0x2A1A44) : hex(0x000000));
				if (y === 6 && x >= 3 && x <= 4) return glow ? null : hex(0x050307);
				if (y === 5 && (x === 1 || x === 6)) return glow ? VEIN : VEIN_DIM;
			}
			if (name === 'head' && (face === 'top' || (y < 2 && face !== 'bottom'))) {
				return glow ? null : shade(hex(0x07060A), 0.8 + noise() * 0.4); // black hair
			}
			if (glow) return vein ? (noise() < 0.3 ? VEIN_HOT : VEIN) : null;
			if (vein) return VEIN_DIM;
			const base = (x + y) % 5 === 0 ? VOID_HI : VOIDC;
			return shade(base, 0.85 + noise() * 0.3);
		});
	}
	return im;
}

for (const slim of [false, true]) {
	const s = slim ? '_slim' : '';
	save(sentry(slim, false), A(`textures/entity/sentry/sentry_suit${s}.png`));
	save(sentry(slim, true), A(`textures/entity/sentry/sentry_glow${s}.png`));
	save(voidForm(slim, false), A(`textures/entity/sentry/void_form${s}.png`));
	save(voidForm(slim, true), A(`textures/entity/sentry/void_glow${s}.png`));
}

// ---------------------------------------------------------------- the Sentry Serum (item, 16x16): a golden vial
{
	const im = img(16, 16);
	const glass = hex(0xCFE8F0, 170), glassEdge = hex(0x8FB0BC, 230), cork = hex(0x8A5A2C), corkHi = hex(0xB07A44);
	const liq = hex(0xFFC83A), liqHi = hex(0xFFF0A0), liqLo = hex(0xC88A10);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const neck = x >= 6 && x <= 9 && y >= 2 && y <= 5;
		const body = x >= 4 && x <= 11 && y >= 5 && y <= 14 && !((x === 4 || x === 11) && (y === 5 || y === 14));
		if (y <= 1 && x >= 6 && x <= 9) set(im, x, y, x === 6 ? corkHi : cork);
		else if (neck) set(im, x, y, (x === 6 || x === 9) ? glassEdge : glass);
		else if (body) {
			const edge = x === 4 || x === 11 || y === 14 || y === 5;
			if (edge) set(im, x, y, glassEdge);
			else if (y >= 7) set(im, x, y, x === 5 ? liqHi : x === 10 ? liqLo : liq);
			else set(im, x, y, glass);
		}
	}
	// the sun-burst label
	for (const [x, y, c] of [[7, 10, liqHi], [8, 10, liqHi], [7, 11, liqHi], [8, 11, liqHi], [7, 9, hex(0x2652D8)], [8, 12, hex(0x2652D8)],
		[6, 10, hex(0x2652D8)], [9, 11, hex(0x2652D8)], [6, 12, hex(0xFFFFFF)], [9, 9, hex(0xFFFFFF)]]) set(im, x, y, c);
	save(im, A('textures/item/sentry_serum.png'));
}

// ---------------------------------------------------------------- blocks
{
	// void matter: black with drifting gold flecks and a purple sheen
	const im = img(16, 16), r = rng(51);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const n = r();
		set(im, x, y, n < 0.05 ? hex(0xFFB52A) : n < 0.09 ? hex(0x3A2060) : shade(hex(0x0A0810), 0.7 + r() * 0.6));
	}
	save(im, A('textures/block/void_matter.png'));
}
{
	// burnt shadow: a dark, soft-edged figure lying flat (head up the texture), arms flung out
	const im = img(16, 16);
	const fig = (x, y) => {
		const head = Math.hypot(x - 7.5, y - 2.5) < 2.0;
		const torso = x >= 5 && x <= 10 && y >= 4 && y <= 9;
		const armL = y >= 4 && y <= 5 && x >= 1 && x <= 5 && Math.abs((x - 1) * 0.25 - (y - 4)) < 1.2;
		const armR = y >= 4 && y <= 6 && x >= 10 && x <= 14;
		const legs = y >= 10 && y <= 15 && ((x >= 5 && x <= 7) || (x >= 8 && x <= 10)) && !(x === 7 && y > 11) && !(x === 8 && y > 11);
		return head || torso || armL || armR || legs;
	};
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		if (fig(x, y)) {
			let near = 0;
			for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) near += fig(x + dx, y + dy) ? 1 : 0;
			set(im, x, y, hex(0x0A0806, near === 4 ? 230 : 170));
		} else {
			let near = 0;
			for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) near += fig(x + dx, y + dy) ? 1 : 0;
			if (near) set(im, x, y, hex(0x1A1410, 70));
		}
	}
	save(im, A('textures/block/burnt_shadow.png'));
}
{
	// the O.X.E. serum vat: a steel-framed glass tank of glowing gold serum; red (sealed) or green (open) status light on the front
	const steel = hex(0x5A6470), steelHi = hex(0x8A96A4), steelLo = hex(0x343C46), glassC = hex(0xB8D8E0), gold = hex(0xFFC83A), goldHi = hex(0xFFEFA0);
	const tank = (front, light) => {
		const im = img(16, 16);
		for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
			const frame = x <= 1 || x >= 14 || y <= 1 || y >= 14;
			if (frame) { set(im, x, y, (x === 0 || y === 0) ? steelHi : (x === 15 || y === 15) ? steelLo : steel); continue; }
			const liquid = y >= 5;
			const bubble = liquid && ((x * 7 + y * 3) % 11 === 0);
			set(im, x, y, liquid ? (bubble ? goldHi : (x === 2 ? goldHi : gold)) : glassC);
		}
		if (front) {
			// a steel band with the status light and three vents across the middle
			for (let x = 2; x <= 13; x++) set(im, x, 3, steelLo);
			for (const x of [4, 6, 8]) set(im, x, 3, steelHi);
			set(im, 11, 3, light); set(im, 12, 3, light);
			// the sun-burst stencil on the glass
			for (const [x, y] of [[7, 9], [8, 9], [7, 10], [8, 10], [7, 7], [8, 12], [5, 9], [10, 10], [6, 8], [9, 11], [9, 8], [6, 11]]) set(im, x, y, goldHi);
		}
		return im;
	};
	save(tank(true, hex(0xFF2A20)), A('textures/block/oxe_serum_vat_front.png'));
	save(tank(true, hex(0x40FF60)), A('textures/block/oxe_serum_vat_front_open.png'));
	save(tank(false, null), A('textures/block/oxe_serum_vat_side.png'));
	const top = img(16, 16);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const hatch = Math.hypot(x - 7.5, y - 7.5) < 4.5;
		const rim = Math.abs(Math.hypot(x - 7.5, y - 7.5) - 4.5) < 0.7;
		set(top, x, y, rim ? steelHi : hatch ? steelLo : ((x + y) % 4 === 0 ? steelHi : steel));
	}
	save(top, A('textures/block/oxe_serum_vat_top.png'));
}

// ---------------------------------------------------------------- models / blockstates
function json(file, obj) { mk(file); fs.writeFileSync(file, JSON.stringify(obj, null, 2) + '\n'); console.log('wrote', path.relative(root, file)); }
json(A('models/item/sentry_serum.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/sentry_serum' } });
json(A('models/block/void_matter.json'), { parent: 'minecraft:block/cube_all', textures: { all: 'projecthero:block/void_matter' } });
json(A('blockstates/void_matter.json'), { variants: { '': { model: 'projecthero:block/void_matter' } } });
json(A('models/block/burnt_shadow.json'), {
	parent: 'minecraft:block/block', ambientocclusion: false, render_type: 'minecraft:translucent',
	textures: { particle: 'projecthero:block/burnt_shadow', shadow: 'projecthero:block/burnt_shadow' },
	elements: [{ from: [0, 0, 0], to: [16, 0.25, 16], faces: { up: { uv: [0, 0, 16, 16], texture: '#shadow' }, down: { uv: [0, 0, 16, 16], texture: '#shadow', cullface: 'down' } } }],
});
const facings = { north: 0, east: 90, south: 180, west: 270 };
json(A('blockstates/burnt_shadow.json'), { variants: Object.fromEntries(Object.entries(facings).map(([f, y]) => [`facing=${f}`, y ? { model: 'projecthero:block/burnt_shadow', y } : { model: 'projecthero:block/burnt_shadow' }])) });
json(A('models/block/oxe_serum_vat.json'), { parent: 'minecraft:block/orientable', textures: { front: 'projecthero:block/oxe_serum_vat_front', side: 'projecthero:block/oxe_serum_vat_side', top: 'projecthero:block/oxe_serum_vat_top' } });
json(A('models/block/oxe_serum_vat_open.json'), { parent: 'minecraft:block/orientable', textures: { front: 'projecthero:block/oxe_serum_vat_front_open', side: 'projecthero:block/oxe_serum_vat_side', top: 'projecthero:block/oxe_serum_vat_top' } });
const vatVariants = {};
for (const [f, y] of Object.entries(facings)) {
	for (const open of [false, true]) {
		const m = { model: 'projecthero:block/oxe_serum_vat' + (open ? '_open' : '') };
		if (y) m.y = y;
		vatVariants[`facing=${f},open=${open}`] = m;
	}
}
json(A('blockstates/oxe_serum_vat.json'), { variants: vatVariants });
json(A('models/item/oxe_serum_vat.json'), { parent: 'projecthero:block/oxe_serum_vat' });
