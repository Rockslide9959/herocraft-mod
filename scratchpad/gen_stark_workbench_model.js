// v0.16.7 (user): the STARK WORKBENCH block + item models, texture, blockstate and loot table -- the two-block desk from
// the approved concept (scratchpad/gen_stark_workbench_concept.js in the main checkout): a 32 x 16 px desk, 14 px tall
// top with a cyan edge light strip under it, two cabinet legs with the red + gold Stark stripe, a centre drawer, the
// projector disc on the seam, a keyboard strip on the left and a small arc reactor on a stand on the right. The holograms
// are NOT in the model (the block entity renderer draws them).
//
// Usage (from the repo root): node scratchpad/gen_stark_workbench_model.js
// Writes:
//   src/main/resources/assets/projecthero/textures/block/stark_workbench.png      (64 x 64, 16 px patches)
//   src/main/resources/assets/projecthero/models/block/stark_workbench_{main,side,single}.json
//   src/main/resources/assets/projecthero/models/item/stark_fabricator.json      (the whole desk + a small hologram)
//   src/main/resources/assets/projecthero/blockstates/stark_fabricator.json
//   src/main/resources/data/projecthero/loot_table/blocks/stark_fabricator.json  (drops from the main / single half only)
//
// Desk coordinates: d = -16 (viewer's left end) .. +16 (viewer's right end), y up, z = -8 (front) .. +8 (back). The model
// front is north; the main half is the viewer's LEFT half (the side half goes to the placer's right), so for the
// north-facing model block x = -d on the main half, 16 - d on the side half (one block to the west), 8 - d on a single.
'use strict';
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const ROOT = path.join(__dirname, '..');
const RES = path.join(ROOT, 'src', 'main', 'resources');
const ASSETS = path.join(RES, 'assets', 'projecthero');

// ------------------------------------------------------------------ PNG (RGBA8) encoder
const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0); const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const cc = Buffer.alloc(4); cc.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, cc]); };
function encode(w, h, px) {
	const st = w * 4; const raw = Buffer.alloc((st + 1) * h);
	for (let y = 0; y < h; y++) { raw[y * (st + 1)] = 0; px.copy(raw, y * (st + 1) + 1, y * st, y * st + st); }
	const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
	return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

// ------------------------------------------------------------------ texture
const TW = 64, P = 16;
const px = Buffer.alloc(TW * TW * 4);
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
function set(x, y, c, a = 255) { const i = (y * TW + x) * 4; px[i] = clamp(c[0]); px[i + 1] = clamp(c[1]); px[i + 2] = clamp(c[2]); px[i + 3] = a; }
function hash(x, y) { let h = (x * 374761393 + y * 668265263) | 0; h = (h ^ (h >>> 13)) * 1274126177; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; }
const mul = (c, k) => [c[0] * k, c[1] * k, c[2] * k];
const mix = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
const CY = [96, 220, 255], RED = [168, 28, 34], GOLD = [222, 170, 60];
const METAL = [58, 62, 72], METAL_D = [34, 36, 44], METAL_L = [92, 98, 112];

const PATCH = {}; // name -> [col, row]
function patch(name, col, row, fn) {
	PATCH[name] = [col, row];
	for (let y = 0; y < P; y++) for (let x = 0; x < P; x++) {
		const r = fn(x, y);
		set(col * P + x, row * P + y, r, r[3] === undefined ? 255 : r[3]);
	}
}
const grain = (c, x, y, amt = 0.06) => mul(c, 1 - amt + 2 * amt * hash(x * 3 + 1, y * 7 + 2));

patch('top', 0, 0, (x, y) => {
	let c = grain(METAL_L, x, y, 0.04);
	if (x === 0 || y === 0) c = mul(METAL_L, 1.12);
	if (x === P - 1 || y === P - 1) c = mul(METAL_L, 0.8);
	if ((x === 5 || x === 10) && y > 1 && y < P - 2) c = mul(c, 0.9); // panel seams
	return c;
});
patch('side', 1, 0, (x, y) => {
	let c = grain(METAL, x, y, 0.05);
	if (y === 0) c = mul(METAL, 1.25);
	if (y === P - 1) c = mul(METAL, 0.75);
	return c;
});
patch('cabinet', 2, 0, (x, y) => {
	let c = grain(METAL_D, x, y, 0.06);
	c = mul(c, 1.08 - 0.16 * (y / P));
	if (x === 0 || x === P - 1) c = mul(c, 1.25);
	return c;
});
patch('cabinet_front', 3, 0, (x, y) => {
	let c = grain(METAL_D, x, y, 0.05);
	c = mul(c, 1.06 - 0.14 * (y / P));
	if (y >= 3 && y <= 4) c = (x >= 6 && x <= 9) ? GOLD : RED; // the Stark stripe
	if (y === 5) c = mul(RED, 0.55);
	if (x === 0 || x === P - 1 || y === P - 1) c = mul(c, 0.7);
	if (y === 0) c = mul(METAL, 1.1);
	return c;
});
patch('cyan', 0, 1, (x, y) => {
	const k = 0.85 + 0.15 * Math.sin((y / (P - 1)) * Math.PI);
	return mix(mul(CY, k), [235, 252, 255], y > 5 && y < 10 ? 0.35 : 0.1);
});
patch('keys', 1, 1, (x, y) => {
	const kx = x % 4, ky = y % 5;
	if (kx === 3 || ky === 4) return mul(METAL_D, 0.8);
	return ((Math.floor(x / 4) + Math.floor(y / 5)) % 2) ? mul(CY, 0.42) : mul(METAL, 0.9);
});
patch('lens', 2, 1, (x, y) => {
	const r = Math.hypot(x - 7.5, y - 7.5) / 7.5;
	if (r > 0.95) return mul(CY, 0.55);
	return mix([235, 252, 255], CY, Math.min(1, r * 1.1));
});
patch('reactor', 3, 1, (x, y) => {
	const r = Math.hypot(x - 7.5, y - 7.5) / 7.5;
	if (r < 0.35) return [236, 250, 255];
	if (r < 0.55) return [150, 220, 255];
	if (r < 0.8) return ((Math.floor(Math.atan2(y - 7.5, x - 7.5) / (Math.PI / 5)) & 1) ? [120, 200, 240] : [200, 238, 255]);
	return [90, 110, 130];
});
patch('drawer', 0, 2, (x, y) => {
	let c = grain(METAL, x, y, 0.05);
	if (y === 0 || x === 0) c = mul(METAL, 1.2);
	if (y === P - 1 || x === P - 1) c = mul(METAL, 0.7);
	if (y >= 7 && y <= 8 && x >= 4 && x <= 11) c = METAL_L.map(v => v * 1.15); // the handle
	return c;
});
patch('projbase', 1, 2, (x, y) => {
	let c = grain(METAL_D, x, y, 0.05);
	if (x === 0 || y === 0 || x === P - 1 || y === P - 1) c = mul(CY, 0.45);
	return c;
});
patch('under', 2, 2, (x, y) => grain(mul(METAL_D, 0.8), x, y, 0.05));
patch('holo', 3, 2, (x, y) => (y % 2 === 0 ? mix(CY, [230, 250, 255], 0.3) : mul(CY, 0.7)));

const uv = name => { const [c, r] = PATCH[name]; const u = c * P / 4, v = r * P / 4; return [u, v, u + P / 4, v + P / 4]; };

// ------------------------------------------------------------------ desk elements (desk coords)
// faces: { front, back, sides, top, bottom } patch names (null = omit)
const DESK = [
	{ name: 'top', b: [-16, 12, -8, 16, 14, 8], f: { front: 'side', back: 'side', sides: 'side', top: 'top', bottom: 'under' } },
	{ name: 'edge_light', b: [-15.5, 11, -7.5, 15.5, 12, 7.5], f: { front: 'cyan', back: 'cyan', sides: 'cyan', bottom: 'cyan' } },
	{ name: 'cabinet_l', b: [-15, 0, -7, -6, 11, 7], f: { front: 'cabinet_front', back: 'cabinet', sides: 'cabinet', bottom: 'under' } },
	{ name: 'cabinet_r', b: [6, 0, -7, 15, 11, 7], f: { front: 'cabinet_front', back: 'cabinet', sides: 'cabinet', bottom: 'under' } },
	{ name: 'drawer', b: [-6, 8, -6.5, 6, 11, 6.5], f: { front: 'drawer', back: 'side', sides: 'side', bottom: 'under' } },
	{ name: 'projector', b: [-3.5, 14, -3.5, 3.5, 15, 3.5], f: { front: 'projbase', back: 'projbase', sides: 'projbase', top: 'projbase' } },
	{ name: 'lens', b: [-2.5, 15, -2.5, 2.5, 15.5, 2.5], f: { front: 'cyan', back: 'cyan', sides: 'cyan', top: 'lens' } },
	{ name: 'keyboard', b: [-12, 14, -6.5, -5, 14.4, -3.5], f: { front: 'side', back: 'side', sides: 'side', top: 'keys' } },
	{ name: 'reactor_stand', b: [9, 14, -2, 12, 15, 1], f: { front: 'projbase', back: 'projbase', sides: 'projbase', top: 'projbase' } },
	{ name: 'reactor', b: [9.6, 15, -1.4, 11.4, 16.8, 0.4], f: { front: 'reactor', back: 'reactor', sides: 'reactor', top: 'reactor' } },
];
// the one-block desk an old single Fabricator shows until it can claim its side half
const SINGLE = [
	{ name: 'top', b: [-8, 12, -8, 8, 14, 8], f: { front: 'side', back: 'side', sides: 'side', top: 'top', bottom: 'under' } },
	{ name: 'edge_light', b: [-7.5, 11, -7.5, 7.5, 12, 7.5], f: { front: 'cyan', back: 'cyan', sides: 'cyan', bottom: 'cyan' } },
	{ name: 'cabinet_l', b: [-7.5, 0, -7, -3.5, 11, 7], f: { front: 'cabinet_front', back: 'cabinet', sides: 'cabinet', bottom: 'under' } },
	{ name: 'cabinet_r', b: [3.5, 0, -7, 7.5, 11, 7], f: { front: 'cabinet_front', back: 'cabinet', sides: 'cabinet', bottom: 'under' } },
	{ name: 'drawer', b: [-3.5, 8, -6.5, 3.5, 11, 6.5], f: { front: 'drawer', back: 'side', sides: 'side', bottom: 'under' } },
	{ name: 'projector', b: [-3.5, 14, -3.5, 3.5, 15, 3.5], f: { front: 'projbase', back: 'projbase', sides: 'projbase', top: 'projbase' } },
	{ name: 'lens', b: [-2.5, 15, -2.5, 2.5, 15.5, 2.5], f: { front: 'cyan', back: 'cyan', sides: 'cyan', top: 'lens' } },
	{ name: 'keyboard', b: [-7, 14, -7, -4.2, 14.4, -5], f: { front: 'side', back: 'side', sides: 'side', top: 'keys' } },
	{ name: 'reactor_stand', b: [4.6, 14, -1.2, 6.8, 15, 1], f: { front: 'projbase', back: 'projbase', sides: 'projbase', top: 'projbase' } },
	{ name: 'reactor', b: [5, 15, -0.8, 6.4, 16.4, 0.6], f: { front: 'reactor', back: 'reactor', sides: 'reactor', top: 'reactor' } },
];
// a small static hologram for the ITEM icon only (the placed block's hologram is the animated renderer)
const ICON_HOLO = (() => {
	const S = 0.42, base = 16;
	const P2 = (n, x0, y0, z0, x1, y1, z1) => ({ name: n, b: [x0 * S, base + y0 * S, z0 * S, x1 * S, base + y1 * S, z1 * S],
		f: { front: 'holo', back: 'holo', sides: 'holo', top: 'holo', bottom: 'holo' } });
	return [P2('holo_head', -4, 24, -4, 4, 32, 4), P2('holo_body', -4, 12, -2, 4, 24, 2), P2('holo_arm_l', -8, 12, -2, -4, 24, 2),
		P2('holo_arm_r', 4, 12, -2, 8, 24, 2), P2('holo_leg_l', -4, 0, -2, -0.2, 12, 2), P2('holo_leg_r', 0.2, 0, -2, 4, 12, 2)];
})();

const r4 = v => Math.round(v * 1000) / 1000;

/** Converts desk-coordinate elements into one model's elements: x = xOf(d), clipped to [dMin, dMax], seam faces dropped. */
function toElements(list, xOf, dMin, dMax) {
	const out = [];
	for (const e of list) {
		let [d0, y0, z0, d1, y1, z1] = e.b;
		const c0 = Math.max(d0, dMin), c1 = Math.min(d1, dMax);
		if (c1 - c0 <= 1e-6) continue;
		const xa = xOf(c0), xb = xOf(c1);
		const from = [r4(Math.min(xa, xb)), r4(y0), r4(z0 + 8)];
		const to = [r4(Math.max(xa, xb)), r4(y1), r4(z1 + 8)];
		const faces = {};
		const add = (dir, p) => { if (p) faces[dir] = { uv: uv(p), texture: '#body' }; };
		add('north', e.f.front);
		add('south', e.f.back);
		add('up', e.f.top);
		add('down', e.f.bottom);
		// east = +x = towards lower d (x decreases with d); a face lying on a seam (clipped edge) is inside the desk
		const seamLow = c0 > d0 + 1e-6, seamHigh = c1 < d1 - 1e-6; // clipped at dMin / dMax
		if (!seamLow) add('east', e.f.sides);
		if (!seamHigh) add('west', e.f.sides);
		out.push({ name: e.name, from, to, faces });
	}
	return out;
}

const DISPLAY_BLOCK = {
	gui: { rotation: [30, 225, 0], translation: [0, 0, 0], scale: [0.625, 0.625, 0.625] },
	ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.25, 0.25, 0.25] },
	fixed: { rotation: [0, 180, 0], translation: [0, 0, 0], scale: [0.5, 0.5, 0.5] },
	thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.375, 0.375, 0.375] },
	thirdperson_lefthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.375, 0.375, 0.375] },
	firstperson_righthand: { rotation: [0, 135, 0], translation: [0, 0, 0], scale: [0.48, 0.48, 0.48] },
	firstperson_lefthand: { rotation: [0, 135, 0], translation: [0, 0, 0], scale: [0.48, 0.48, 0.48] },
};
// the item is the whole 2-block desk centred on the block (x -8..24), so it is shown smaller
const DISPLAY_ITEM = {
	gui: { rotation: [25, 210, 0], translation: [0, 1.5, 0], scale: [0.48, 0.48, 0.48] },
	ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.18, 0.18, 0.18] },
	fixed: { rotation: [0, 180, 0], translation: [0, 0, 0], scale: [0.35, 0.35, 0.35] },
	thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.25, 0.25, 0.25] },
	thirdperson_lefthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.25, 0.25, 0.25] },
	firstperson_righthand: { rotation: [0, 135, 0], translation: [0, 0, 0], scale: [0.28, 0.28, 0.28] },
	firstperson_lefthand: { rotation: [0, 135, 0], translation: [0, 0, 0], scale: [0.28, 0.28, 0.28] },
};

function model(elements, display) {
	return {
		parent: 'block/block',
		ambientocclusion: false,
		textures: { particle: 'projecthero:block/stark_workbench', body: 'projecthero:block/stark_workbench' },
		elements,
		display,
	};
}

const write = (rel, data) => {
	const p = path.join(RES, rel);
	fs.mkdirSync(path.dirname(p), { recursive: true });
	fs.writeFileSync(p, typeof data === 'string' || Buffer.isBuffer(data) ? data : JSON.stringify(data, null, '\t') + '\n');
	console.log('wrote', rel);
};

write('assets/projecthero/textures/block/stark_workbench.png', encode(TW, TW, px));
write('assets/projecthero/models/block/stark_workbench_main.json', model(toElements(DESK, d => -d, -16, 0), DISPLAY_BLOCK));
write('assets/projecthero/models/block/stark_workbench_side.json', model(toElements(DESK, d => 16 - d, 0, 16), DISPLAY_BLOCK));
write('assets/projecthero/models/block/stark_workbench_single.json', model(toElements(SINGLE, d => 8 - d, -8, 8), DISPLAY_BLOCK));
write('assets/projecthero/models/item/stark_fabricator.json', model(toElements(DESK.concat(ICON_HOLO), d => 8 - d, -16, 16), DISPLAY_ITEM));

const variants = {};
const ROT = { north: 0, east: 90, south: 180, west: 270 };
for (const facing of Object.keys(ROT)) {
	for (const part of ['single', 'main', 'side']) {
		const v = { model: 'projecthero:block/stark_workbench_' + part };
		if (ROT[facing]) v.y = ROT[facing];
		variants['facing=' + facing + ',part=' + part] = v;
	}
}
write('assets/projecthero/blockstates/stark_fabricator.json', { variants });

write('data/projecthero/loot_table/blocks/stark_fabricator.json', {
	type: 'minecraft:block',
	pools: [{
		rolls: 1,
		entries: [{ type: 'minecraft:item', name: 'projecthero:stark_fabricator' }],
		conditions: [
			{ condition: 'minecraft:survives_explosion' },
			// the side half never drops (breaking it drops the main half's loot by hand, so one desk = one item)
			{ condition: 'minecraft:inverted', term: { condition: 'minecraft:block_state_property', block: 'projecthero:stark_fabricator', properties: { part: 'side' } } },
		],
	}],
});
