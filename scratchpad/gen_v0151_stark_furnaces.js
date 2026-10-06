// v0.15.1: high-detail Stark Furnace / Smelter / Smoker -- shared Stark-machine textures, per-kind glow textures, and
// multi-element block models (unlit + lit variants). Blockstates are untouched (same facing/lit variants and model ids).
// Usage (repo root): node scratchpad/gen_v0151_stark_furnaces.js
const fs = require('fs');
const path = require('path');
const png = require('./pnglib.js');
const A = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero');
const TEX = path.join(A, 'textures', 'block');
const MOD = path.join(A, 'models', 'block');

// ---------------------------------------------------------------- textures
const GM = 0x3B4048, GM_D = 0x2A2E34, GM_DD = 0x1F2227, GM_L = 0x555B64, GM_HL = 0x737A85;
const STEEL = 0x8C939C, STEEL_D = 0x666C74, STEEL_L = 0xAEB5BE;
const RED = 0x9E1B1B, RED_D = 0x6E1010, RED_L = 0xC22A2A;
const GOLD = 0xD4A537, GOLD_D = 0x9C7420, GOLD_L = 0xF0C860;
const BLACK = 0x15171B;

const hash = (x, y, s) => { let h = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; h = (h ^ (h >>> 13)) * 1274126177 | 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967296; };
const shadeC = (c, k) => { const f = v => Math.max(0, Math.min(255, Math.round(v + k))); return (f(c >> 16 & 255) << 16) | (f(c >> 8 & 255) << 8) | f(c & 255); };
const mix = (a, b, t) => { const f = s => Math.round((a >> s & 255) * (1 - t) + (b >> s & 255) * t); return (f(16) << 16) | (f(8) << 8) | f(0); };

function img() { const d = Buffer.alloc(16 * 16 * 4); return { set(x, y, c) { if (x < 0 || y < 0 || x > 15 || y > 15) return; const o = (y * 16 + x) * 4; d[o] = c >> 16 & 255; d[o + 1] = c >> 8 & 255; d[o + 2] = c & 255; d[o + 3] = 255; }, get(x, y) { const o = (y * 16 + x) * 4; return (d[o] << 16) | (d[o + 1] << 8) | d[o + 2]; }, d }; }
function fill(im, fn) { for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) im.set(x, y, fn(x, y)); }
function rivet(im, x, y) { im.set(x, y, GM_HL); im.set(x + 1, y + 1, GM_DD); im.set(x + 1, y, GM_L); im.set(x, y + 1, GM_D); }
function save(im, name) { png.write(path.join(TEX, name + '.png'), { w: 16, h: 16, data: im.d }); console.log('tex', name); }

const T = {};
// gunmetal panel: two-by-two plates with bevels, seams and corner rivets
T.panel = () => { const im = img(); fill(im, (x, y) => {
	const lx = x % 8, ly = y % 8; let c = shadeC(GM, (hash(x, y, 1) - 0.5) * 8);
	if (ly === 0 || lx === 0) c = GM_L; if (ly === 7 || lx === 7) c = GM_D;
	return c; }); for (const [x, y] of [[2, 2], [12, 2], [2, 12], [12, 12]]) rivet(im, x, y); return im; };
// brushed steel: horizontal streaks
T.steel = () => { const im = img(); fill(im, (x, y) => { let c = shadeC(STEEL, (hash(0, y, 2) - 0.5) * 18 + (hash(x, y, 3) - 0.5) * 8);
	if (y === 0) c = STEEL_L; if (y === 15) c = STEEL_D; return c; }); return im; };
// dark base/plinth: grooves and bolts
T.base = () => { const im = img(); fill(im, (x, y) => { let c = shadeC(GM_D, (hash(x, y, 4) - 0.5) * 6);
	if (y % 8 === 0) c = GM_L; if (y % 8 === 7) c = GM_DD; return c; }); for (let x = 1; x < 16; x += 7) { rivet(im, x, 3); rivet(im, x, 11); } return im; };
// red armour trim with a gold pinstripe on row 7 (bands sample rows 6..8)
T.trim = () => { const im = img(); fill(im, (x, y) => { let c = shadeC(RED, (hash(x, y, 5) - 0.5) * 10);
	if (y === 0 || y === 8) c = RED_L; if (y === 15) c = RED_D; if (y === 7) c = (x % 4 === 3) ? GOLD_L : GOLD; return c; }); return im; };
// polished gold (hinges, latches, collars)
T.gold = () => { const im = img(); fill(im, (x, y) => { let c = shadeC(GOLD, (hash(x, y, 6) - 0.5) * 12); if ((x + y) % 7 === 0) c = GOLD_L; if (y === 15 || x === 15) c = GOLD_D; return c; }); return im; };
// louvred vent: horizontal slats
T.vent = () => { const im = img(); fill(im, (x, y) => [GM_HL, BLACK, GM_D][y % 3]); return im; };
// square grate (smoker grill floor, top vents)
T.grate = () => { const im = img(); fill(im, (x, y) => (x % 2 === 0 || y % 2 === 0) ? ((x + y) % 4 === 0 ? GM_HL : GM_L) : BLACK); return im; };
// arc-reactor-blue light strip: dim when idle, bright when lit
T.light = () => { const im = img(); fill(im, (x, y) => ((x + y) % 4 === 0) ? 0x2F6C7A : 0x22505B); return im; };
T.light_on = () => { const im = img(); fill(im, (x, y) => ((x + y) % 4 === 0) ? 0xE6FFFF : (hash(x, y, 7) < 0.3 ? 0x9EEFFF : 0x7FE3FA)); return im; };
// arc-reactor badge: a 4x4 ring cell tiled across the texture (faces map uv [0,0,4,4] or [0,0,2,2])
function reactor(on) { const im = img(); fill(im, (x, y) => { const lx = x % 4, ly = y % 4; const corner = (lx === 0 || lx === 3) && (ly === 0 || ly === 3);
	const centre = (lx === 1 || lx === 2) && (ly === 1 || ly === 2);
	if (corner) return GM_D; if (centre) return on ? 0xFFFFFF : 0x2C4A55; return on ? 0x7FE8FF : 0x4A6A74; }); return im; }
T.reactor = () => reactor(false); T.reactor_on = () => reactor(true);
// Stark plaque: red field, gold border, a gold chevron (faces map uv [0,0,6,3])
T.logo = () => { const im = img(); fill(im, (x, y) => { const lx = x % 6, ly = y % 3; if (ly === 0) return GOLD; if (ly === 2) return GOLD_D;
	return (lx === 1 || lx === 4) ? RED_D : (lx === 2 || lx === 3) ? GOLD_L : RED; }); return im; };

// per-kind firebox glass: off = smoked glass with a glint, on = the heat colour (v rows map to model height)
function glass(off, glint) { const im = img(); fill(im, (x, y) => { let c = shadeC(off, (hash(x, y, 8) - 0.5) * 6); if (x - y === 3 || x - y === 4) c = glint; return c; }); return im; }
T.stark_furnace_glow = () => { const im = glass(0x1E2024, 0x3A4048); for (let x = 0; x < 16; x++) if (hash(x, 0, 9) < 0.5) im.set(x, 12, 0x4A2618); return im; };
T.stark_furnace_glow_on = () => { const im = img(); fill(im, (x, y) => { const f = hash(x, y, 10) * 2 - 1; const h = (y - 6) / 7 + f * 0.15; // hotter towards the bottom
	return h > 0.8 ? 0xFFF2B0 : h > 0.55 ? 0xFFC040 : h > 0.25 ? 0xFF8A1E : h > 0 ? 0xE85A18 : 0xC8401A; }); return im; };
T.stark_smelter_glow = () => glass(0x1A2228, 0x34424C);
T.stark_smelter_glow_on = () => { const im = img(); fill(im, (x, y) => { const dx = Math.abs(x - 7.5) / 4, f = hash(x, y, 11) * 0.25; const t = Math.min(1, dx + f);
	return t < 0.35 ? 0xFFFFFF : t < 0.6 ? 0xE6FFFF : t < 0.85 ? 0xA8F2FF : 0x6FDCF5; }); return im; };
T.stark_smoker_glow = () => glass(0x221E1A, 0x45403A);
T.stark_smoker_glow_on = () => { const im = img(); fill(im, (x, y) => { const f = hash(x, y, 12) * 2 - 1; const h = (y - 5) / 8 + f * 0.18;
	return h > 0.75 ? 0xFFE3A0 : h > 0.45 ? 0xFFC060 : h > 0.15 ? 0xF2A03A : 0xD07A24; }); return im; };

for (const [name, fn] of Object.entries(T)) save(fn(), name.startsWith('stark_') ? name : 'stark_machine_' + name);

// ---------------------------------------------------------------- models
// Element helper: from/to in px; tex = texture var for every face, or {all, north, south, east, west, up, down};
// o.band = [v0, v1] fixes the v range of the side faces (trim stripes); o.uv = {face: [u0,v0,u1,v1]};
// o.skip = faces to leave out. UVs default to the vanilla position-derived ones, so pixel density stays 1:1.
const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];
function autoUv(dir, f, t) {
	switch (dir) {
		case 'north': return [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]];
		case 'south': return [f[0], 16 - t[1], t[0], 16 - f[1]];
		case 'east': return [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]];
		case 'west': return [f[2], 16 - t[1], t[2], 16 - f[1]];
		case 'up': return [f[0], f[2], t[0], t[2]];
		case 'down': return [f[0], 16 - t[2], t[0], 16 - f[2]];
	}
}
const r = v => Math.round(v * 1000) / 1000;
function E(from, to, tex, o = {}) {
	const faces = {};
	for (const dir of DIRS) {
		if (o.skip && o.skip.includes(dir)) continue;
		const tx = typeof tex === 'string' ? tex : (tex[dir] || (dir === 'up' || dir === 'down' ? tex.cap : tex.side) || tex.all);
		if (!tx) continue;
		let uv = (o.uv && o.uv[dir]) || autoUv(dir, from, to);
		if (o.band && dir !== 'up' && dir !== 'down') uv = [uv[0], o.band[0], uv[2], o.band[1]];
		const face = { uv: uv.map(r), texture: '#' + tx };
		const cull = { north: from[2] === 0, south: to[2] === 16, west: from[0] === 0, east: to[0] === 16, down: from[1] === 0, up: to[1] === 16 }[dir];
		if (cull) face.cullface = dir;
		faces[dir] = face;
	}
	const el = { from: from.map(r), to: to.map(r), faces };
	if (o.name) el.name = o.name;
	return el;
}
// mirror an element across the model's x centre (east <-> west details)
function mirrorX(el) {
	const f = [16 - el.to[0], el.from[1], el.from[2]], t = [16 - el.from[0], el.to[1], el.to[2]];
	const faces = {};
	for (const [dir, face] of Object.entries(el.faces)) {
		const nd = dir === 'east' ? 'west' : dir === 'west' ? 'east' : dir;
		const nf = { ...face, uv: face.uv.slice() };
		const auto = autoUv(nd, f, t); nf.uv[0] = r(auto[0]); nf.uv[2] = r(auto[2]);
		if (nd === 'up' || nd === 'down') { nf.uv[1] = r(auto[1]); nf.uv[3] = r(auto[3]); }
		if (face.cullface) nf.cullface = nd;
		faces[nd] = nf;
	}
	return { ...el, from: f, to: t, faces };
}
const both = el => [el, mirrorX(el)];
const GLOWING = new Set(['glow', 'light', 'reactor']);
const R4 = { north: [0, 0, 4, 4], south: [0, 0, 4, 4], east: [0, 0, 4, 4], west: [0, 0, 4, 4], up: [0, 0, 4, 4], down: [0, 0, 4, 4] };
const R2 = { north: [0, 0, 2, 2], south: [0, 0, 2, 2], east: [0, 0, 2, 2], west: [0, 0, 2, 2], up: [0, 0, 2, 2], down: [0, 0, 2, 2] };
const LOGO = { north: [0, 0, 6, 3], south: [0, 0, 6, 3], east: [0, 0, 6, 3], west: [0, 0, 6, 3] };

// octagonal ring of 1px bars, outer box x0..x0+8, y0..y0+8, z from zf to zt (furnace hatch)
function ring8(x0, y0, zf, zt, tex) {
	const b = (a, c, d, e) => E([x0 + a, y0 + c, zf], [x0 + d, y0 + e, zt], tex);
	return [b(2, 0, 6, 1), b(2, 7, 6, 8), b(0, 2, 1, 6), b(7, 2, 8, 6), b(1, 1, 2, 2), b(6, 1, 7, 2), b(1, 6, 2, 7), b(6, 6, 7, 7)];
}

const MODELS = {};
// ---- Stark Furnace: squat forge, big round arc-heated hatch (14 px tall)
MODELS.stark_furnace = [
	E([0, 0, 0], [16, 2, 16], { side: 'base', up: 'panel', down: 'base' }, { name: 'plinth' }),
	E([1, 2, 1], [15, 11, 15], { side: 'panel', up: 'panel' }, { name: 'body', skip: ['down'] }),
	...[[0, 0], [14, 0], [0, 14], [14, 14]].map(([x, z]) => E([x, 2, z], [x + 2, 11, z + 2], 'steel', { name: 'post', skip: ['down'] })),
	E([0.5, 11, 0.5], [15.5, 13, 15.5], { side: 'trim', up: 'trim', down: 'panel' }, { name: 'cap', band: [6, 8] }),
	E([4, 13, 4], [12, 14, 12], { side: 'vent', up: 'grate' }, { name: 'heat_vent', skip: ['down'] }),
	E([11, 13, 11], [13, 14.5, 13], { side: 'steel', up: 'light' }, { name: 'vent_valve', skip: ['down'] }),
	// the hatch: octagonal steel ring, glass behind it, an arc emitter in the middle, gold hinge + latch
	...ring8(4, 2.5, 0.25, 1, 'steel'),
	E([5, 3.5, 0.6], [11, 9.5, 1], 'glow', { name: 'hatch_glass', skip: ['south'] }),
	E([7.5, 6, 0.4], [8.5, 7, 0.6], 'light', { name: 'arc_emitter', skip: ['south'] }),
	E([3, 5, 0.5], [4, 8, 1], 'gold', { name: 'hinge', skip: ['south'] }),
	E([12, 5.5, 0.1], [13, 7.5, 1], 'gold', { name: 'latch', skip: ['south'] }),
	// power columns flanking the hatch and the strip along the cap
	...both(E([2.25, 3, 0.75], [3, 10, 1], 'light', { name: 'power_column', skip: ['south'] })),
	E([3, 11.5, 0.2], [13, 12.25, 0.5], 'light', { name: 'cap_strip', skip: ['south'] }),
	E([7, 12.25, 0.25], [9, 12.75, 0.5], 'gold', { name: 'cap_badge', skip: ['south'] }),
	// sides: Stark plaque, louvre, reactor badge
	...both(E([15, 7.5, 5], [15.4, 9, 11], 'logo', { name: 'plaque', uv: LOGO, skip: ['west'] })),
	...both(E([15, 3, 4], [15.25, 6.5, 12], 'vent', { name: 'side_vent', skip: ['west'] })),
	...both(E([15, 9.25, 7], [15.5, 10.75, 9], 'reactor', { name: 'side_reactor', uv: R2, skip: ['west'] })),
	// back: exhaust louvre and two coolant pipes
	E([4, 3, 15], [12, 10, 15.25], 'vent', { name: 'rear_vent', skip: ['north'] }),
	...both(E([2.5, 2, 15], [3.75, 11, 16], 'steel', { name: 'coolant_pipe', skip: ['north', 'down'] })),
];

// ---- Stark Smelter: tall industrial crucible, heavy frame, a full-height chimney stack in the back-right corner (16 px tall)
MODELS.stark_smelter = [
	E([0, 0, 0], [16, 3, 16], { side: 'base', up: 'panel', down: 'base' }, { name: 'base' }),
	E([1.5, 3, 1.5], [14.5, 12.5, 14.5], { side: 'panel', up: 'panel' }, { name: 'crucible', skip: ['down'] }),
	...[[0, 0], [13, 0], [0, 13]].map(([x, z]) => E([x, 3, z], [x + 3, 14, z + 3], { side: 'steel', up: 'gold' }, { name: 'frame_post', skip: ['down'] })),
	// heavy cross-beams on the sides and back
	E([14.5, 7, 3], [16, 8.5, 12], 'steel', { name: 'side_beam_e' }),
	E([0, 7, 3], [1.5, 8.5, 13], 'steel', { name: 'side_beam_w' }),
	E([3, 7, 14.5], [12, 8.5, 16], 'steel', { name: 'rear_beam' }),
	E([0.5, 12.5, 0.5], [15.5, 14, 15.5], { side: 'trim', up: 'panel', down: 'panel' }, { name: 'crown', band: [6, 7.5] }),
	// the chimney: a steel stack from the base to the very top, gold collars, glowing throat
	E([12, 3, 12], [15.75, 16, 15.75], { side: 'steel', up: 'glow' }, { name: 'chimney', skip: ['down'] }),
	E([11.5, 8, 11.5], [16, 8.75, 16], 'gold', { name: 'chimney_collar_low' }),
	E([11.5, 15, 11.5], [16, 15.75, 16], 'gold', { name: 'chimney_collar_top' }),
	E([15.75, 9.5, 12.75], [16, 13, 14.75], 'light', { name: 'chimney_gauge', skip: ['west'] }),
	E([12.75, 9.5, 15.75], [14.75, 13, 16], 'light', { name: 'chimney_gauge_rear', skip: ['north'] }),
	// top: lid, ore chute and a little arc vent
	E([2.5, 14, 2.5], [11, 14.5, 11], { side: 'steel', up: 'panel' }, { name: 'lid', skip: ['down'] }),
	E([3, 14.5, 3], [7, 15.5, 7], { side: 'vent', up: 'grate' }, { name: 'ore_chute', skip: ['down'] }),
	E([8, 14.5, 3], [10, 15.5, 5], { side: 'steel', up: 'light' }, { name: 'arc_vent', skip: ['down'] }),
	// front: heavy-framed tall crucible window with two bars, gauge strips beside it, strip on the crown
	E([4, 3, 0.5], [12, 4, 1.5], 'steel', { name: 'window_sill', skip: ['south'] }),
	E([4, 11.5, 0.5], [12, 12.5, 1.5], 'steel', { name: 'window_head', skip: ['south'] }),
	...both(E([4, 4, 0.5], [5, 11.5, 1.5], 'steel', { name: 'window_jamb', skip: ['south'] })),
	E([5, 4, 1.25], [11, 11.5, 1.5], 'glow', { name: 'window_glass', skip: ['south'] }),
	...both(E([6.75, 4, 0.9], [7.25, 11.5, 1.25], 'gold', { name: 'window_bar', skip: ['south'] })),
	...both(E([3.25, 4, 1.25], [3.75, 11.5, 1.5], 'light', { name: 'gauge_strip', skip: ['south'] })),
	E([3, 13, 0.25], [13, 13.5, 0.5], 'light', { name: 'crown_strip', skip: ['south'] }),
	// sides: plaque, reactor badge, louvre (the east ones sit forward of the chimney)
	E([14.5, 10, 3.5], [15, 11.5, 9.5], 'logo', { name: 'side_plaque_e', uv: LOGO, skip: ['west'] }),
	mirrorX(E([14.5, 10, 5], [15, 11.5, 11], 'logo', { name: 'side_plaque_w', uv: LOGO, skip: ['west'] })),
	...both(E([14.5, 4, 3.5], [14.9, 6, 5.5], 'reactor', { name: 'side_reactor', uv: R2, skip: ['west'] })),
	...both(E([14.5, 3.75, 6.5], [14.75, 6.5, 10.5], 'vent', { name: 'side_vent', skip: ['west'] })),
	E([3, 4, 14.5], [10.5, 12, 14.75], 'vent', { name: 'rear_vent', skip: ['north'] }),
];

// ---- Stark Smoker: rounded chamber, vented grill, smoke stacks (16 px tall)
MODELS.stark_smoker = [
	E([0, 0, 0], [16, 2, 16], { side: 'base', up: 'panel', down: 'base' }, { name: 'plinth' }),
	E([1, 2, 3], [15, 11, 13], { side: 'panel', up: 'panel' }, { name: 'chamber_x', skip: ['down'] }),
	E([3, 2, 1], [13, 11, 15], { side: 'panel', up: 'panel' }, { name: 'chamber_z', skip: ['down'] }),
	E([2, 2, 2], [14, 11, 14], { side: 'panel', up: 'panel' }, { name: 'chamber_mid', skip: ['down'] }),
	// red shoulder band + domed cap
	E([2, 11, 4], [14, 12.5, 12], { side: 'trim', up: 'panel' }, { name: 'dome_x', band: [6, 7.5], skip: ['down'] }),
	E([4, 11, 2], [12, 12.5, 14], { side: 'trim', up: 'panel' }, { name: 'dome_z', band: [6, 7.5], skip: ['down'] }),
	E([3, 11, 3], [13, 12.5, 13], { side: 'trim', up: 'panel' }, { name: 'dome_mid', band: [6, 7.5], skip: ['down'] }),
	E([4, 12.5, 4], [12, 13, 12], { side: 'steel', up: 'grate' }, { name: 'dome_top', skip: ['down'] }),
	// smoke stacks: a tall one with a gold collar and a glowing throat, a thin one with an arc-blue tip
	E([6, 13, 8], [10, 16, 12], { side: 'steel', up: 'glow' }, { name: 'smoke_stack', skip: ['down'] }),
	E([5.5, 14.5, 7.5], [10.5, 15.25, 12.5], 'gold', { name: 'stack_collar' }),
	E([10.5, 13, 4.5], [12, 14.75, 6], { side: 'steel', up: 'light' }, { name: 'vent_stack', skip: ['down'] }),
	// front: grill frame, amber window, gold slats, indicator strip + reactor badge
	E([4, 2.5, 0.5], [12, 3.5, 1], 'steel', { name: 'grill_bottom', skip: ['south'] }),
	E([4, 9, 0.5], [12, 10, 1], 'steel', { name: 'grill_top', skip: ['south'] }),
	...both(E([3.25, 3, 0.5], [4, 9.5, 1], 'steel', { name: 'grill_side', skip: ['south'] })),
	E([4, 3.5, 0.75], [12, 9, 1], 'glow', { name: 'grill_window', skip: ['south'] }),
	...[4, 5.25, 6.5, 7.75].map(y => E([4, y, 0.55], [12, y + 0.5, 0.75], 'gold', { name: 'grill_slat', skip: ['south'] })),
	...both(E([4, 10.25, 0.75], [6.75, 10.75, 1], 'light', { name: 'front_strip', skip: ['south'] })),
	E([7, 10, 0.5], [9, 11, 1], 'reactor', { name: 'front_reactor', uv: R2, skip: ['south'] }),
	// sides: hooded smoke louvres with an arc strip over them, and a plaque low down
	...both(E([15, 4, 5], [15.25, 8.5, 11], 'vent', { name: 'side_louvre', skip: ['west'] })),
	...both(E([15, 8.5, 4.5], [15.75, 9.25, 11.5], 'steel', { name: 'louvre_hood' })),
	...both(E([15, 9.75, 5], [15.3, 10.25, 11], 'light', { name: 'side_strip', skip: ['west'] })),
	...both(E([15, 2.5, 6.5], [15.25, 3.5, 9.5], 'logo', { name: 'side_plaque', uv: { east: [0, 0, 6, 2], west: [0, 0, 6, 2], north: [0, 0, 1, 1], south: [0, 0, 1, 1] }, skip: ['west'] })),
	E([4, 3, 15], [12, 10, 15.25], 'vent', { name: 'rear_vent', skip: ['north'] }),
];

// The "skip" lists above name the face that sits against the parent body; mirrorX swaps east/west so the mirrored copy skips
// its own inner face. Write unlit + lit models.
for (const [id, elements] of Object.entries(MODELS)) {
	for (const on of [false, true]) {
		const textures = {
			particle: 'projecthero:block/stark_machine_panel',
			panel: 'projecthero:block/stark_machine_panel', steel: 'projecthero:block/stark_machine_steel',
			base: 'projecthero:block/stark_machine_base', trim: 'projecthero:block/stark_machine_trim',
			gold: 'projecthero:block/stark_machine_gold', vent: 'projecthero:block/stark_machine_vent',
			grate: 'projecthero:block/stark_machine_grate', logo: 'projecthero:block/stark_machine_logo',
			light: 'projecthero:block/stark_machine_light' + (on ? '_on' : ''),
			reactor: 'projecthero:block/stark_machine_reactor' + (on ? '_on' : ''),
			glow: `projecthero:block/${id}_glow` + (on ? '_on' : ''),
		};
		const els = elements.map(el => {
			const e = JSON.parse(JSON.stringify(el));
			// lit: glowing faces skip directional shading so they read as light sources (block light is 14 when lit)
			if (on && Object.values(e.faces).every(f => GLOWING.has(f.texture.slice(1)))) e.shade = false;
			return e;
		});
		const model = { parent: 'minecraft:block/block', textures, elements: els };
		fs.writeFileSync(path.join(MOD, id + (on ? '_on' : '') + '.json'), JSON.stringify(model, null, 2) + '\n');
		console.log('model', id + (on ? '_on' : ''), els.length, 'elements');
	}
}
