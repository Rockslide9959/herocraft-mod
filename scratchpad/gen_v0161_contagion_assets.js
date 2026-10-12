// v0.16.1 Ultron Contagion asset generator (ART ONLY: no Java, lang, recipes or tags).
// usage (from the repo/worktree root):  node scratchpad/gen_v0161_contagion_assets.js
// Writes into src/main/resources/assets/projecthero/ and is idempotent (every file is fully rewritten).
// NOTE: the four corruption blocks (corrupted_circuitry incl. its edge variants, corrupted_circuitry_stone, _log, _leaves)
// are now produced by gen_v0161_contagion_blocks2.js; this script no longer writes any of their textures/models/states.
//
//  Blocks   citadel_plating, citadel_rock,
//           rising_rock, firewall_beacon (lit/unlit element model), omega_core (facing trophy: pedestal + Omega head)
//  Items    ultron_scrap, nanite_sample, purge_charge, override_spike, firewall_chip, vibranium_ingot,
//           vibranium_plating_2, 3 spawn eggs (template model only), vibranium_shield (+_blocking, entity texture)
//  Entity   assimilated overlays (+_eyes) for zombie/skeleton/spider/creeper/pillager, ultron_golem (+_eyes),
//           legion_visor
//  GeckoLib ultron_nexus, lift_engine, uplink_antenna, vibranium_core: MOVED to gen_v0161_contagion_models2.js
//
// Palette: Ultron = dark gunmetal/black metal, silver-white vibranium, glowing red (#FF2010) veins/eyes/seams.
// The good guys' tech (Firewall Beacon, Purge Charge, Override Spike, Firewall Chip) is blue/cyan; vibranium items
// are purple-silver with a violet sheen.
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('./v0161_asset_lib.js');
const { Canvas, mix, mul, hex, hash3, vnoise, fbm, clamp, writeJson, writePng, written, Geo, clip, RES } = L;
const { decodePng } = require('./pngdecode_v0161.js');

// ================================================================ palette
const RED = hex('FF2010'), RED_M = hex('C8160C'), RED_D = hex('6E0806'), HOT = hex('FF9070'), WHOT = [255, 222, 210];
const M0 = hex('1A1C22'), M1 = hex('262930'), M2 = hex('3A3E48'), M3 = hex('565C69'), M4 = hex('8A93A3');
const SIL = hex('C4CAD4'), SIL2 = hex('ECEFF4'), BLK = hex('101114');
const TRACE = hex('46505F'), PAD = hex('7C8698');
const CYAN = hex('40E0FF'), CYAN_L = hex('C8FAFF'), BLUE = hex('1E64D2'), BLUE_D = hex('0E2A6E');
const VIO_D = hex('34264E'), VIO = hex('6A4E9C'), VIO_L = hex('A896D2'), VIO_S = hex('DCCEFF');
const NS = 'projecthero';

// ================================================================ helpers
function polyline(pts) {
	const out = [];
	for (let k = 0; k < pts.length - 1; k++) {
		let [x0, y0] = pts[k]; const [x1, y1] = pts[k + 1];
		const dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
		let err = dx + dy;
		for (;;) {
			const l = out[out.length - 1];
			if (!l || l[0] !== x0 || l[1] !== y0) out.push([x0, y0]);
			if (x0 === x1 && y0 === y1) break;
			const e2 = 2 * err;
			if (e2 >= dy) { err += dy; x0 += sx; }
			if (e2 <= dx) { err += dx; y0 += sy; }
		}
	}
	return out;
}
// brightness of a travelling pulse at path index idx (cyclic), `count` pulses evenly spaced
function pulse(idx, len, phase, count = 1) {
	const sig = Math.max(1.4, len / 9);
	let b = 0;
	for (let k = 0; k < count; k++) {
		const pos = ((phase + k / count) % 1) * len;
		let d = Math.abs(idx - pos); d = Math.min(d, len - d);
		b = Math.max(b, Math.exp(-(d * d) / (2 * sig * sig)));
	}
	return b;
}
function veinCol(b) {
	let c = mix(RED_D, RED, b);
	if (b > 0.72) c = mix(c, HOT, (b - 0.72) * 3.2);
	return c;
}
// paints red veins (array of {path, phase}) into a 16x16 frame canvas, with a soft red tint on neighbours
function paintVeins(cv, veins, frac, base = 0.22, count = 1) {
	const B = new Map();
	for (const v of veins) {
		const len = v.path.length;
		v.path.forEach(([x, y], idx) => {
			const b = base + (1 - base) * pulse(idx, len, frac + (v.phase || 0), count);
			const k = ((y & 15) << 4) | (x & 15);
			B.set(k, Math.max(B.get(k) || 0, b));
		});
	}
	const tint = new Map();
	for (const [k, b] of B) {
		const x = k & 15, y = k >> 4;
		for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
			const nk = (((y + dy) & 15) << 4) | ((x + dx) & 15);
			if (!B.has(nk)) tint.set(nk, Math.max(tint.get(nk) || 0, b));
		}
	}
	for (const [k, b] of tint) cv.blend(k & 15, k >> 4, hex('8A140C'), 0.12 + 0.3 * b);
	for (const [k, b] of B) cv.set(k & 15, k >> 4, veinCol(b));
}
function strip(frames) {
	const out = new Canvas(16, 16 * frames.length);
	frames.forEach((f, i) => out.blit(f, 0, i * 16));
	return out;
}
function mcmeta(rel, frametime, interpolate = true) {
	const p = path.join(RES, rel); fs.mkdirSync(path.dirname(p), { recursive: true });
	fs.writeFileSync(p, JSON.stringify({ animation: { frametime, interpolate } }, null, '\t') + '\n'); written.push(rel);
}
function drawTraces(cv, traces) {
	for (const t of traces) {
		const px = polyline(t);
		for (const [x, y] of px) cv.set(x, y, mix(cv.get(x, y).slice(0, 3), TRACE, 0.85));
		for (const [x, y] of [px[0], px[px.length - 1]]) cv.set(x, y, PAD);
	}
}

// ================================================================ 1. block textures
// (corrupted_circuitry / _stone / _log / _leaves textures, models and blockstates now come from gen_v0161_contagion_blocks2.js)
// rock tone helper, still used by citadel_rock below
function rockBase(x, y, seed, lo, hi) {
	const n = fbm(x * 0.28, y * 0.28, seed, 4);
	let c = mix(lo, hi, n * 1.35 - 0.2);
	const cr = fbm(x * 0.22 + 3, y * 0.22, seed + 50, 3);
	if (Math.abs(cr - 0.5) < 0.035) c = mul(c, 0.55);
	if (hash3(x, y, seed + 3) > 0.94) c = mix(c, hex('6A6A74'), 0.5);
	if (hash3(x, y, seed + 4) < 0.05) c = mul(c, 0.7);
	return c;
}
// --- citadel_plating (static)
{
	const cv = new Canvas(16, 16);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		let c = mix(M2, M3, 0.35 + 0.3 * fbm(x * 0.3, y * 0.3, 41, 3));
		if (x === 0 || y === 0) c = mix(M3, SIL, 0.3);
		if (x === 15 || y === 15) c = M0;
		if (x === 1 && y > 0 && y < 15 || y === 1 && x > 0 && x < 15) c = mix(c, M4, 0.25);
		// recessed upper panel with vents
		if (x >= 2 && x <= 13 && y >= 2 && y <= 9) {
			c = mix(M1, M2, 0.3 * hash3(x, y, 44));
			if (x === 2 || y === 2) c = M0;
			if (x === 13 || y === 9) c = mix(M3, M4, 0.3);
			if (x >= 4 && x <= 11 && (y === 4 || y === 6 || y === 8) && y !== 9) c = hex('0E0F12');
			if (x >= 4 && x <= 11 && (y === 5 || y === 7)) c = mix(c, M3, 0.25);
		}
		// red light strip
		if (y === 12 && x >= 2 && x <= 13) c = (x >= 6 && x <= 9) ? ((x === 7 || x === 8) ? HOT : RED) : RED_M;
		if ((y === 11 || y === 13) && x >= 2 && x <= 13) c = mix(c, hex('5A0C08'), 0.45);
		if ((y === 11) && (x === 1 || x === 14)) c = M0;
		if ([[1, 1], [14, 1], [1, 14], [14, 14]].some(([a, b]) => a === x && b === y)) c = SIL;
		if ([[2, 2], [15, 2], [2, 15], [15, 15]].some(([a, b]) => a === x && b === y)) c = hex('0C0C10');
		cv.set(x, y, mul(c, 0.95 + 0.1 * hash3(x, y, 46)));
	}
	writePng('textures/block/citadel_plating.png', cv);
}
// --- citadel_rock (static)
function crackRock(seed, lo, hi, cracks) {
	const cv = new Canvas(16, 16);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		let c = rockBase(x, y, seed, lo, hi);
		if (vnoise(0, y * 0.8, seed + 9) > 0.7) c = mul(c, 0.88);
		cv.set(x, y, c);
	}
	const px = [];
	cracks.forEach((cr, k) => polyline(cr).forEach(([x, y], i) => px.push([x, y, 0.55 + 0.45 * Math.abs(Math.sin(i * 0.9 + k * 2))])));
	const set = new Set(px.map(([x, y]) => x + ',' + y));
	for (const [x, y, b] of px) for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
		const nx = (x + dx) & 15, ny = (y + dy) & 15;
		if (!set.has(nx + ',' + ny)) cv.blend(nx, ny, hex('7A0E08'), 0.35 * b);
	}
	for (const [x, y, b] of px) cv.set(x, y, veinCol(b));
	return cv;
}
writePng('textures/block/citadel_rock.png', crackRock(61, hex('141317'), hex('3A353F'), [
	[[0, 4], [3, 5], [5, 8], [4, 11], [6, 15]], [[5, 8], [9, 7], [12, 9], [15, 8]], [[9, 7], [10, 3], [12, 0]]]));
// --- rising_rock (static): red rim + rune
{
	const cv = crackRock(71, hex('1A181E'), hex('45404B'), [[[3, 13], [5, 12]], [[11, 2], [12, 4]]]);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const e = Math.min(x, y, 15 - x, 15 - y);
		if (e === 0) cv.set(x, y, veinCol(0.45 + 0.35 * hash3(x, y, 72) + ((x === 0 || x === 15) && (y === 0 || y === 15) ? 0.3 : 0)));
		else if (e === 1) cv.blend(x, y, hex('8A140C'), 0.3);
	}
	const rune = ['..#..', '.###.', '#.#.#', '..#..', '.#.#.', '#...#'];
	rune.forEach((row, j) => [...row].forEach((ch, i) => {
		if (ch === '#') cv.set(5 + i, 5 + j, (i === 2 && j === 1) ? HOT : RED);
		else cv.blend(5 + i, 5 + j, hex('5A0A06'), 0.25);
	}));
	writePng('textures/block/rising_rock.png', cv);
}

// ================================================================ 2. simple block models / states
function cubeAll(id) {
	writeJson(`blockstates/${id}.json`, { variants: { '': { model: `${NS}:block/${id}` } } });
	writeJson(`models/block/${id}.json`, { parent: 'minecraft:block/cube_all', textures: { all: `${NS}:block/${id}` } });
	writeJson(`models/item/${id}.json`, { parent: `${NS}:block/${id}` });
}
['citadel_plating', 'citadel_rock', 'rising_rock'].forEach(cubeAll);

// ================================================================ 3. element (Blockbench-style) block models
// Every face is painted at 1 texel per model pixel into a TS x TS atlas; Java model UVs are in 0..16 space,
// so uv = texel * 16 / TS. Faces that share a key share one atlas region.
const DIRS = { side: ['north', 'south', 'east', 'west'] };
function faceDims(el, dir) {
	const d = [0, 1, 2].map(i => el.to[i] - el.from[i]);
	if (dir === 'north' || dir === 'south') return [d[0], d[1]];
	if (dir === 'east' || dir === 'west') return [d[2], d[1]];
	return [d[0], d[2]];
}
function elementModel(TS, elements, painters, texRef) {
	const regions = {};
	const els = elements.map(e => {
		const faces = {};
		for (const [k, key] of Object.entries(e.faces)) for (const dir of (DIRS[k] || [k])) {
			const [w, h] = faceDims(e, dir);
			if (!Number.isInteger(w) || !Number.isInteger(h)) throw new Error('non-integer face ' + key);
			if (regions[key] && (regions[key].w !== w || regions[key].h !== h)) throw new Error(`region ${key} size clash ${w}x${h} vs ${regions[key].w}x${regions[key].h}`);
			regions[key] = { w, h };
			faces[dir] = key;
		}
		return { e, faces };
	});
	// first-fit shelf packing
	const keys = Object.keys(regions).sort((a, b) => regions[b].h - regions[a].h || regions[b].w - regions[a].w);
	const shelves = [];
	let yTop = 0;
	for (const k of keys) {
		const r = regions[k];
		let sh = shelves.find(s => s.h >= r.h && s.x + r.w <= TS);
		if (!sh) { sh = { y: yTop, h: r.h, x: 0 }; yTop += r.h; shelves.push(sh); }
		r.x = sh.x; r.y = sh.y; sh.x += r.w;
	}
	if (yTop > TS) throw new Error('atlas overflow ' + yTop);
	const S = 16 / TS;
	const R = n => Math.round(n * 10000) / 10000;
	const json = els.map(({ e, faces }) => {
		const o = { from: e.from, to: e.to };
		if (e.rotation) o.rotation = e.rotation;
		o.faces = {};
		for (const [dir, key] of Object.entries(faces)) {
			const r = regions[key];
			o.faces[dir] = { uv: [R(r.x * S), R(r.y * S), R((r.x + r.w) * S), R((r.y + r.h) * S)], texture: texRef };
			if (e.cull && e.cull.includes(dir)) o.faces[dir].cullface = dir;
		}
		return o;
	});
	const paint = pset => {
		const cv = new Canvas(TS, TS);
		for (const [k, r] of Object.entries(regions)) {
			const fn = pset[k]; if (!fn) throw new Error('no painter for ' + k);
			for (let j = 0; j < r.h; j++) for (let i = 0; i < r.w; i++) { const c = fn(i, j, r.w, r.h); if (c) cv.set(r.x + i, r.y + j, c, c[3] == null ? 255 : c[3]); }
		}
		return cv;
	};
	return { elements: json, paint, regions };
}
function steel(i, j, w, h, seed, base = hex('6C7482')) {
	let c = mul(base, 0.93 + 0.12 * hash3(i, j, seed));
	if (j === 0) c = mix(c, SIL2, 0.35);
	else if (i === 0) c = mix(c, SIL, 0.18);
	if (j === h - 1 && h > 1) c = mul(c, 0.6);
	else if (i === w - 1 && w > 1) c = mul(c, 0.78);
	return c;
}

// --- firewall_beacon
{
	const posts = [[1, 1], [13, 1], [1, 13], [13, 13]].map(([x, z]) => ({ from: [x, 4, z], to: [x + 2, 12, z + 2], faces: { side: 'post_side', up: 'post_top', down: 'post_top' } }));
	const elements = [
		{ from: [0, 0, 0], to: [16, 4, 16], faces: { side: 'base_side', up: 'base_top', down: 'base_top' }, cull: ['north', 'south', 'east', 'west', 'down'] },
		...posts,
		{ from: [5, 4, 5], to: [11, 12, 11], faces: { side: 'core_side', up: 'core_top', down: 'core_top' } },
		{ from: [5, 4, 5], to: [11, 12, 11], rotation: { origin: [8, 8, 8], axis: 'y', angle: 45 }, faces: { side: 'core_side', up: 'core_top', down: 'core_top' } },
		{ from: [1, 12, 1], to: [15, 14, 15], faces: { side: 'collar_side', up: 'collar_top', down: 'collar_top' } },
		{ from: [3, 14, 3], to: [13, 15, 13], faces: { side: 'dish_side', up: 'dish_top', down: 'dish_top' } },
		{ from: [6, 14, 6], to: [10, 16, 10], faces: { side: 'emit_side', up: 'emit_top', down: 'emit_top' } },
	];
	const M = elementModel(32, elements, null, '#beacon');
	const painters = lit => {
		const glowA = lit ? CYAN : hex('1A2C44'), glowB = lit ? CYAN_L : hex('2A3C56'), line = lit ? CYAN : hex('16263E');
		return {
			base_top: (i, j, w, h) => {
				const d = Math.max(Math.abs(i - 7.5), Math.abs(j - 7.5));
				let c = steel(i, j, w, h, 101, hex('5E6674'));
				if (d >= 7.5) c = mix(c, SIL, 0.25);
				if (Math.floor(d) === 5) c = (i + j) % 2 ? line : mix(line, hex('000000'), 0.3);
				if (d < 3) c = hex('22262E');
				if ((i === 2 || i === 13) && (j === 7 || j === 8)) c = SIL;
				return c;
			},
			base_side: (i, j, w, h) => {
				let c = steel(i, j, w, h, 102, hex('5E6674'));
				if ((j === 1 || j === 2) && i % 3 === 1 && i > 1 && i < 14) c = hex('1A1D24');
				if ((j === 1 || j === 2) && (i === 7 || i === 8)) c = j === 1 ? glowB : glowA;
				return c;
			},
			post_side: (i, j, w, h) => { let c = steel(i, j, w, h, 103, hex('7A8290')); if (j % 4 === 0) c = mix(c, SIL2, 0.4); if (j % 4 === 3) c = mul(c, 0.7); return c; },
			post_top: (i, j) => mix(SIL, M4, (i + j) * 0.2),
			core_side: (i, j, w, h) => {
				const edge = i === 0 || i === w - 1 || j === 0 || j === h - 1;
				if (lit) {
					if (edge) return (j === 0 || j === h - 1) ? BLUE_D : BLUE;
					let c = mix(CYAN, hex('2AA8E8'), Math.abs(i - 2.5) / 2.5);
					if (i === 2) c = CYAN_L;
					if ((i === 2 || i === 3) && (j === 3 || j === 4)) c = [240, 255, 255];
					return c;
				}
				if (edge) return hex('1E2128');
				let c = mix(hex('3A4250'), hex('262B34'), Math.abs(i - 2.5) / 2.5);
				if (i === 2) c = hex('56606E');
				return c;
			},
			core_top: (i, j, w, h) => {
				const d = Math.hypot(i - 2.5, j - 2.5);
				if (lit) return d < 1.2 ? [240, 255, 255] : d < 2.2 ? CYAN_L : BLUE;
				return d < 1.6 ? hex('3A4250') : hex('1E2128');
			},
			collar_top: (i, j, w, h) => {
				const d = Math.max(Math.abs(i - 6.5), Math.abs(j - 6.5));
				let c = steel(i, j, w, h, 104, hex('6C7482'));
				if (d >= 6) c = mix(c, SIL2, 0.3);
				if (d >= 4 && d < 5) c = (i + j) % 3 === 0 ? glowB : line;
				if (d < 2) c = hex('1A1D24');
				return c;
			},
			collar_side: (i, j, w, h) => { let c = steel(i, j, w, h, 105); if (j === 1 && i % 4 === 2) c = glowA; return c; },
			dish_top: (i, j) => {
				const r = Math.hypot(i - 4.5, j - 4.5);
				if (r > 4.2) return mix(SIL, M4, 0.3);
				let c = mix(hex('9AA2B0'), hex('3C424E'), 1 - r / 4.2);
				if (lit && r < 2.5) c = mix(c, CYAN, 0.8 - r * 0.25);
				return c;
			},
			dish_side: (i, j, w, h) => mix(SIL, M4, (i % 2) * 0.3),
			emit_side: (i, j) => lit ? (j === 0 ? CYAN_L : CYAN) : (j === 0 ? hex('3A4250') : hex('262B34')),
			emit_top: (i, j) => { const d = Math.hypot(i - 1.5, j - 1.5); return lit ? (d < 1 ? [250, 255, 255] : CYAN_L) : (d < 1 ? hex('4A5464') : hex('2A303A')); },
		};
	};
	for (const lit of [false, true]) {
		const id = lit ? 'firewall_beacon_lit' : 'firewall_beacon';
		writePng(`textures/block/${id}.png`, M.paint(painters(lit)));
		writeJson(`models/block/${id}.json`, { parent: 'minecraft:block/block', texture_size: [32, 32], textures: { beacon: `${NS}:block/${id}`, particle: `${NS}:block/${id}` }, elements: M.elements });
	}
	writeJson('blockstates/firewall_beacon.json', { variants: { 'lit=false': { model: `${NS}:block/firewall_beacon` }, 'lit=true': { model: `${NS}:block/firewall_beacon_lit` } } });
	writeJson('models/item/firewall_beacon.json', { parent: `${NS}:block/firewall_beacon_lit` });
}

// --- omega_core trophy: pedestal + Omega head, face plane at z = 4.5 (north)
{
	const elements = [
		{ from: [2, 0, 2], to: [14, 6, 14], faces: { side: 'ped_side', up: 'ped_top', down: 'ped_top' }, cull: ['down'] },
		{ from: [6.5, 6, 6.5], to: [9.5, 7, 9.5], faces: { side: 'neck_side', up: 'neck_top', down: 'neck_top' } },
		{ from: [5.5, 7, 4.5], to: [10.5, 10, 10.5], faces: { north: 'jaw_front', south: 'jaw_back', east: 'jaw_side', west: 'jaw_side', up: 'jaw_top', down: 'jaw_top' } },
		{ from: [4.5, 10, 4.5], to: [11.5, 14, 11.5], faces: { north: 'head_front', south: 'head_back', east: 'head_side', west: 'head_side', up: 'head_top', down: 'head_top' } },
		{ from: [4, 14, 5], to: [12, 16, 12], faces: { north: 'crest_front', south: 'crest_back', east: 'crest_side', west: 'crest_side', up: 'crest_top', down: 'crest_top' } },
	];
	const M = elementModel(32, elements, null, '#core');
	const sil = (i, j, w, h, seed) => {
		let c = mix(SIL2, hex('9EA6B4'), (j + 0.5) / Math.max(h, 1) * 0.6);
		c = mul(c, 0.95 + 0.08 * hash3(i, j, seed));
		if (j === 0) c = mix(c, [255, 255, 255], 0.3);
		if (i === 0 || i === w - 1) c = mul(c, 0.86);
		return c;
	};
	const DARK = hex('0E0C10');
	const pnt = {
		ped_top: (i, j, w, h) => {
			const d = Math.max(Math.abs(i - 5.5), Math.abs(j - 5.5));
			let c = mix(M1, M2, 0.5 * hash3(i, j, 201));
			if (d >= 5.5) c = mix(M3, M4, 0.3);
			if (d >= 3.5 && d < 4.5) c = RED_D;
			if (d < 2) c = M0;
			return c;
		},
		ped_side: (i, j, w, h) => {
			let c = mix(M1, M2, 0.3 + 0.4 * hash3(i, j, 202));
			if (j === 0) c = mix(M3, M4, 0.5);
			if (j === h - 1) c = M0;
			if (i === 3 || i === 8) c = mul(c, 0.6);
			if (j === 2) c = (i >= 4 && i <= 7) ? (i === 5 || i === 6 ? HOT : RED) : RED_M;
			if (j === 3 && i > 0 && i < w - 1) c = mix(c, hex('4A0806'), 0.4);
			return c;
		},
		neck_side: () => BLK, neck_top: () => BLK,
		jaw_front: (i, j, w, h) => {
			let c = sil(i, j, w, h, 203);
			if (j === 1) c = (i === 1 || i === 3) ? hex('5A5E66') : DARK; // mouth slit
			if (j === 2 && (i === 0 || i === w - 1)) c = mul(c, 0.6);    // sharp jaw taper
			return c;
		},
		jaw_back: (i, j, w, h) => mul(sil(i, j, w, h, 204), 0.75),
		jaw_side: (i, j, w, h) => { let c = sil(i, j, w, h, 205); if (j === h - 1 && i < 2) c = mul(c, 0.6); if (i === w - 1 && j === 0) c = mul(c, 0.7); return c; },
		jaw_top: (i, j, w, h) => mul(sil(i, j, w, h, 206), 0.7),
		head_front: (i, j, w, h) => {
			let c = sil(i, j, w, h, 207);
			if (j === 0) c = mix(c, M4, 0.25);                                   // brow ridge
			if (j === 1 && (i === 1 || i === 2 || i === 4 || i === 5)) c = DARK; // eye sockets (y 12..13)
			if (j === 2 && (i === 1 || i === 5)) c = mix(c, DARK, 0.6);
			if (i === 3 && j >= 1) c = mix(c, [255, 255, 255], 0.25);            // nose ridge
			if (j === 3 && (i === 0 || i === 6)) c = mul(c, 0.65);               // cheek seams
			return c;
		},
		head_side: (i, j, w, h) => { let c = sil(i, j, w, h, 208); if (j === 2 && i > 1 && i < 6) c = mul(c, 0.7); return c; },
		head_back: (i, j, w, h) => mul(sil(i, j, w, h, 209), 0.82),
		head_top: (i, j, w, h) => sil(i, j, w, h, 210),
		crest_front: (i, j, w, h) => { let c = sil(i, j, w, h, 211); if (i === 3 || i === 4) c = mix(c, M3, 0.5); return c; },
		crest_side: (i, j, w, h) => sil(i, j, w, h, 212),
		crest_back: (i, j, w, h) => mul(sil(i, j, w, h, 213), 0.8),
		crest_top: (i, j, w, h) => { let c = sil(i, j, w, h, 214); if (i === 3 || i === 4) c = mix(c, M3, 0.55); return c; },
	};
	writePng('textures/block/ultron_omega_core.png', M.paint(pnt));
	writeJson('models/block/ultron_omega_core.json', { parent: 'minecraft:block/block', texture_size: [32, 32], textures: { core: `${NS}:block/ultron_omega_core`, particle: `${NS}:block/ultron_omega_core` }, elements: M.elements });
	writeJson('blockstates/ultron_omega_core.json', { variants: {
		'facing=north': { model: `${NS}:block/ultron_omega_core` },
		'facing=east': { model: `${NS}:block/ultron_omega_core`, y: 90 },
		'facing=south': { model: `${NS}:block/ultron_omega_core`, y: 180 },
		'facing=west': { model: `${NS}:block/ultron_omega_core`, y: 270 },
	} });
	writeJson('models/item/ultron_omega_core.json', { parent: `${NS}:block/ultron_omega_core`, display: {
		gui: { rotation: [25, 200, 0], translation: [0, 0.5, 0], scale: [0.8, 0.8, 0.8] },
		ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.4, 0.4, 0.4] },
		fixed: { rotation: [0, 180, 0], translation: [0, 0, 0], scale: [0.7, 0.7, 0.7] },
		thirdperson_righthand: { rotation: [75, 225, 0], translation: [0, 2.5, 0], scale: [0.45, 0.45, 0.45] },
		firstperson_righthand: { rotation: [0, 225, 0], translation: [0, 0, 0], scale: [0.5, 0.5, 0.5] },
		firstperson_lefthand: { rotation: [0, 45, 0], translation: [0, 0, 0], scale: [0.5, 0.5, 0.5] },
	} });
}

// ================================================================ 4. items
const PAL = {
	k: hex('101014'), d: hex('2C2E36'), m: hex('4A4E5A'), l: hex('7E8696'), w: hex('D0D6E0'),
	r: RED, R: hex('FF6A50'), h: WHOT, x: hex('7A0C08'),
	G: [235, 245, 255, 210], g: [150, 175, 195, 150], s: hex('C8CDD6'),
};
function ascii(rows, pal) {
	if (rows.length !== 16) throw new Error('rows ' + rows.length);
	const cv = new Canvas(16, 16);
	rows.forEach((row, y) => {
		if (row.length !== 16) throw new Error(`row ${y} has ${row.length}: ${row}`);
		[...row].forEach((ch, x) => { if (ch === '.') return; const c = pal[ch]; if (!c) throw new Error('pal ' + ch); cv.set(x, y, c, c[3] == null ? 255 : c[3]); });
	});
	return cv;
}
// shape(x, y) -> colour | null over pixel centres, then a dark outline around it
function shapeItem(fn, outline = PAL.k) {
	const cv = new Canvas(16, 16);
	const inside = [];
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const c = fn(x + 0.5, y + 0.5, x, y); if (c) { cv.set(x, y, c, c[3] == null ? 255 : c[3]); inside.push(x + ',' + y); } }
	const S = new Set(inside);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		if (S.has(x + ',' + y)) continue;
		if ([[1, 0], [-1, 0], [0, 1], [0, -1]].some(([a, b]) => S.has((x + a) + ',' + (y + b)))) cv.set(x, y, outline);
	}
	return cv;
}
function pip(px, py, poly) {
	let inside = false;
	for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
		const [xi, yi] = poly[i], [xj, yj] = poly[j];
		if ((yi > py) !== (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) inside = !inside;
	}
	return inside;
}
function itemModel(id) { writeJson(`models/item/${id}.json`, { parent: 'minecraft:item/generated', textures: { layer0: `${NS}:item/${id}` } }); }

writePng('textures/item/ultron_scrap.png', ascii([
	'................',
	'............k...',
	'...........kwk..',
	'..........kwlk..',
	'.........kllmk..',
	'....kkk.kldmk...',
	'...kdmmkkmdk....',
	'..kdmlwlmmdk....',
	'..kmlxrRrxmk....',
	'.kdmxrhRrxdk....',
	'.kdmlxrrxmdk....',
	'.kddmmxmmdk.....',
	'..kdddmdmk......',
	'...kkdmdk.......',
	'.....kkk........',
	'................'], PAL));
itemModel('ultron_scrap');

writePng('textures/item/nanite_sample.png', ascii([
	'................',
	'......kkkk......',
	'.....kwlllk.....',
	'.....kmddmk.....',
	'......kkkk......',
	'......kGgk......',
	'.....kGgggk.....',
	'....kGgsrggk....',
	'....kGsrsRgk....',
	'....kgrshrsk....',
	'....kGsrsrgk....',
	'....kgrsRsgk....',
	'....kgsrsrgk....',
	'....kGgrsggk....',
	'.....kkkkkk.....',
	'................'], PAL));
itemModel('nanite_sample');

// purge_charge: round blue EMP grenade, glowing cyan band, steel cap
writePng('textures/item/purge_charge.png', shapeItem((x, y, ix, iy) => {
	const cx = 8, cy = 9, r = 5.6;
	const dx = x - cx, dy = y - cy, d = Math.hypot(dx, dy);
	if (iy >= 2 && iy <= 3 && ix >= 6 && ix <= 9) return iy === 2 ? (ix === 6 ? PAL.w : PAL.l) : PAL.m;   // cap
	if (iy === 1 && (ix === 10 || ix === 11)) return PAL.l;                                               // pin ring
	if (iy === 2 && ix === 11) return PAL.l;
	if (d > r) return null;
	const nz = Math.sqrt(Math.max(0, 1 - (d / r) ** 2));
	const lum = clamp(0.35 + 0.65 * ((-dx * 0.55 - dy * 0.65) / r + nz * 0.7), 0, 1.2);
	if (iy === 9 || iy === 10) return iy === 9 ? mix(CYAN, CYAN_L, lum * 0.8) : mix(hex('1AA0D0'), CYAN, lum);
	let c = mix(BLUE_D, hex('3C86F0'), lum);
	if (lum > 1.0) c = mix(c, hex('B8D8FF'), 0.6);
	if (ix === 8 && iy > 3 && iy < 15 && iy !== 9 && iy !== 10) c = mul(c, 0.72);                      // seam
	return c;
}));
itemModel('purge_charge');

// override_spike: a steel stake, cyan circuit lines, blue glowing tip (tip bottom-left, cap top-right)
writePng('textures/item/override_spike.png', shapeItem((x, y) => {
	const T = [1.8, 14.2], E = [13.6, 2.4];
	const ax = E[0] - T[0], ay = E[1] - T[1], len = Math.hypot(ax, ay);
	const ux = ax / len, uy = ay / len;
	const t = ((x - T[0]) * ux + (y - T[1]) * uy) / len;
	const q = (x - T[0]) * -uy + (y - T[1]) * ux;
	if (t < 0 || t > 1) return null;
	let hw = t < 0.4 ? 0.35 + 1.25 * t / 0.4 : t > 0.86 ? 2.1 : t > 0.74 ? 1.25 : 1.55;
	if (Math.abs(q) > hw) return null;
	if (t < 0.18) return t < 0.08 ? CYAN_L : CYAN;                                  // glowing tip
	if (t < 0.4) return q < 0 ? hex('3C86F0') : BLUE;
	if (t > 0.86) return q < -0.4 ? PAL.w : q > 0.6 ? PAL.d : PAL.l;               // cap
	if (t > 0.74) return Math.round(t * 30) % 2 ? PAL.d : PAL.m;                    // grip wrap
	if (Math.abs(q) < 0.5 && Math.round(t * 22) % 3 !== 0) return CYAN;             // circuit line
	return q < 0 ? PAL.l : PAL.m;
}));
itemModel('override_spike');

// firewall_chip: blue chip, silver pins, cyan shield icon
{
	const shield = ['cccccc', 'cCCCCc', 'cCwCCc', 'cCwCCc', '.cCCc.', '.cCCc.', '..cc..'];
	const cv = new Canvas(16, 16);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const inBody = x >= 3 && x <= 12 && y >= 3 && y <= 12;
		if (inBody) {
			let c = (x === 3 || y === 3 || x === 12 || y === 12) ? PAL.k : (x === 4 || y === 4) ? hex('2A5AB8') : BLUE_D;
			if (!(x === 3 || y === 3 || x === 12 || y === 12) && (x + y) % 5 === 0 && hash3(x, y, 1) > 0.5) c = mix(c, BLUE, 0.6);
			cv.set(x, y, c);
		}
		const pinCols = [4, 6, 9, 11];
		if (pinCols.includes(x) && (y === 1 || y === 2 || y === 13 || y === 14)) cv.set(x, y, (y === 1 || y === 14) ? PAL.w : PAL.l);
		if (pinCols.includes(y) && (x === 1 || x === 2 || x === 13 || x === 14)) cv.set(x, y, (x === 1 || x === 14) ? PAL.w : PAL.l);
	}
	shield.forEach((row, j) => [...row].forEach((ch, i) => { if (ch !== '.') cv.set(5 + i, 5 + j, ch === 'c' ? CYAN : ch === 'C' ? hex('1E9AD8') : CYAN_L); }));
	writePng('textures/item/firewall_chip.png', cv);
	itemModel('firewall_chip');
}

// vibranium_ingot: isometric ingot built from three polygons
{
	const top = [[1, 8.5], [8.5, 4.5], [14.5, 6.5], [7, 10.5]];
	const front = [[7, 10.5], [14.5, 6.5], [14.5, 9.5], [7, 13.5]];
	const left = [[1, 8.5], [7, 10.5], [7, 13.5], [1, 11.5]];
	writePng('textures/item/vibranium_ingot.png', shapeItem((x, y, ix, iy) => {
		if (pip(x, y, top)) {
			const g = (x - y * 1.6 + 6) / 14;                       // diagonal sheen across the top
			let c = mix(VIO_L, hex('8E7CB8'), clamp(1 - g, 0, 1) * 0.7);
			if (Math.abs(x - (y * -1.9 + 20)) < 0.9) c = VIO_S;      // violet glint
			if (pip(x, y + 1.2, front) || pip(x - 1, y + 1, left)) c = mix(c, [255, 255, 255], 0.25); // bevel edge
			return c;
		}
		if (pip(x, y, front)) return mix(VIO, VIO_D, (y - 7) / 7 + (x - 7) / 30);
		if (pip(x, y, left)) return mix(hex('4E3A78'), VIO_D, (y - 8.5) / 5);
		return null;
	}, hex('1A1228')));
	itemModel('vibranium_ingot');
}

// vibranium_plating_2: curved purple-silver plate with "II" notches
writePng('textures/item/vibranium_plating_2.png', shapeItem((x, y, ix, iy) => {
	const bow = (x - 8) ** 2 * 0.055;
	const yt = 3.5 + bow, yb = 12.5 + bow * 0.6;
	if (x < 2 || x > 14 || y < yt || y > yb) return null;
	const u = (x - 2) / 12;
	let c = mix(hex('5A4888'), VIO_L, 1 - Math.abs(u - 0.38) * 2.2);  // curvature highlight band
	c = mix(c, hex('C8C4D8'), 0.25);
	if (y - yt < 1) c = mix(c, VIO_S, 0.6);
	if (yb - y < 1) c = mul(c, 0.6);
	if ((ix === 6 || ix === 9) && iy >= 6 && iy <= 10) c = hex('2A1E44');
	if ((ix === 7 || ix === 10) && iy >= 6 && iy <= 10) c = mix(c, VIO_S, 0.5);
	if ((ix === 3 || ix === 13) && (iy === Math.round(yt + 1) || iy === Math.round(yb - 1))) c = SIL;
	return c;
}, hex('1A1228')));
itemModel('vibranium_plating_2');

for (const egg of ['ultron_omega_spawn_egg', 'ultron_nexus_spawn_egg', 'hijacked_mark_spawn_egg']) writeJson(`models/item/${egg}.json`, { parent: 'minecraft:item/template_spawn_egg' });

// vibranium_shield models: copies of the adamantium shield models
{
	const src = path.join(RES, 'models/item');
	const a = JSON.parse(fs.readFileSync(path.join(src, 'adamantium_shield.json'), 'utf8'));
	const b = JSON.parse(fs.readFileSync(path.join(src, 'adamantium_shield_blocking.json'), 'utf8'));
	a.textures.particle = `${NS}:item/vibranium_ingot`; b.textures.particle = `${NS}:item/vibranium_ingot`;
	a.overrides = a.overrides.map(o => Object.assign({}, o, { model: `${NS}:item/vibranium_shield_blocking` }));
	writeJson('models/item/vibranium_shield.json', a);
	writeJson('models/item/vibranium_shield_blocking.json', b);

	// entity texture: same 64x64 layout + alpha mask as the adamantium shield
	const ad = decodePng(path.join(RES, 'textures/entity/adamantium_shield.png'));
	if (ad.w !== 64 || ad.h !== 64) throw new Error('unexpected adamantium shield size');
	const cv = new Canvas(64, 64);
	const centre = (x0, y0, x1, y1) => { let sx = 0, sy = 0, n = 0; for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) if (ad.px[(y * 64 + x) * 4 + 3]) { sx += x + 0.5; sy += y + 0.5; n++; } return [sx / n, sy / n]; };
	const [fx, fy] = centre(0, 0, 32, 32), [bx, by] = centre(32, 0, 64, 32);
	const RD = hex('8A1420'), RDD = hex('5A0C16'), SV = hex('B8B4C8'), BL = hex('1E2A6A');
	const star = (dx, dy, R) => { // five-point star test
		const a = Math.atan2(dx, -dy), r = Math.hypot(dx, dy);
		const k = Math.cos(Math.PI / 5) / Math.cos(((a % (2 * Math.PI / 5)) + 2 * Math.PI / 5) % (2 * Math.PI / 5) - Math.PI / 5);
		return r < R * (0.45 + 0.55 * Math.pow(Math.max(0, k - 0.809) / 0.191, 1.5));
	};
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const a = ad.px[(y * 64 + x) * 4 + 3];
		if (!a) continue;
		let c;
		if (y < 32 && x < 32) {            // front disc
			const dx = x + 0.5 - fx, dy = y + 0.5 - fy, d = Math.hypot(dx, dy);
			if (d > 13.2) c = RD; else if (d > 10.2) c = SV; else if (d > 7.2) c = RD; else c = BL;
			if (d <= 7.2 && star(dx, dy, 6.4)) c = SV;
			// metallic violet vibranium sheen: diagonal light band + darker lower-right
			const sheen = Math.exp(-(((dx + dy) / 6 + 1.2) ** 2));
			c = mix(c, VIO_S, 0.38 * sheen);
			c = mix(c, hex('3A1E4E'), clamp((dx + dy) / 40, 0, 0.35));
			if (Math.abs(d - 13.2) < 0.5 || Math.abs(d - 10.2) < 0.5 || Math.abs(d - 7.2) < 0.5) c = mul(c, 0.78); // ring grooves
			if (d > 15) c = mix(c, VIO_L, 0.35);                                                                    // bevelled rim
			c = mul(mix(c, VIO_D, 0.22), 0.86 * (0.94 + 0.1 * hash3(x, y, 301)));   // darker, violet-tinted vibranium
		} else if (y < 32) {               // back disc: brushed dark purple-silver
			const dx = x + 0.5 - bx, dy = y + 0.5 - by, d = Math.hypot(dx, dy);
			c = mix(hex('4A4060'), hex('7A7094'), 0.4 + 0.5 * vnoise(x * 0.25, y * 3.1, 302));
			c = mix(c, VIO_S, 0.25 * Math.exp(-(((dx - dy) / 8) ** 2)));
			if (d > 14) c = mix(c, hex('2A2238'), 0.5);
			if (Math.abs(d - 5) < 0.6 && ((Math.atan2(dy, dx) * 4 / Math.PI) % 1 + 1) % 1 < 0.25) c = SV; // rivet ring
		} else if (x < 32) {               // rim band
			const r = y - 32;
			c = r === 0 || r === 3 ? mix(SV, VIO_L, 0.4) : mix(RDD, VIO_D, 0.35);
			c = mul(c, 0.92 + 0.12 * hash3(x, y, 303));
		} else {                           // grip
			c = mix(hex('2A1E36'), hex('4A3A5E'), ((x + y) % 3) / 3);
			if (y % 4 === 0) c = mul(c, 0.7);
		}
		cv.set(x, y, c, a);
	}
	writePng('textures/entity/vibranium_shield.png', cv);
}

// ================================================================ 5. assimilated overlays + eyes
function boxRects(u, v, w, h, d) {
	return {
		up: { x: u + d, y: v, w, h: d }, down: { x: u + d + w, y: v, w, h: d },
		right: { x: u, y: v + d, w: d, h }, front: { x: u + d, y: v + d, w, h },
		left: { x: u + d + w, y: v + d, w: d, h }, back: { x: u + 2 * d + w, y: v + d, w, h },
	};
}
const PL_D = hex('3C404A'), PL_L = hex('5C616E'), PL_SIL = hex('A8B0BE'), PL_SEAM = hex('1A1B20'), PL_RIV = hex('D2D8E2');
function plateTexel(i, j, seed) {
	const pi = i & 3, pj = j & 3, ci = i >> 2, cj = j >> 2;
	let c = mix(PL_D, PL_L, hash3(ci, cj, seed));
	c = mul(c, 0.9 + 0.14 * hash3(i, j, seed + 5));
	if (pj === 0) c = mix(c, PL_SIL, 0.55); else if (pi === 0) c = mix(c, hex('8A93A3'), 0.3);
	if (pj === 3 || pi === 3) {
		c = PL_SEAM;
		if (hash3(pj === 3 ? ci : ci + 50, pj === 3 ? cj : cj + 50, seed + 13) > 0.6) c = hash3(i, j, seed + 21) > 0.45 ? hex('E8261A') : hex('9A140C');
	}
	if (pi === 1 && pj === 1 && hash3(ci, cj, seed + 9) > 0.55) c = PL_RIV;
	return c;
}
// mode: 'right_half' | 'full' | 'top_half' | 'chest' | 'bottom_half'
function plated(mode, face, s, t, j, seed, fi) {
	const jag = (hash3(j >> 1, seed, fi) - 0.5) * 0.28;
	switch (mode) {
		case 'full': return true;
		case 'right_half':
			if (face === 'right') return true; if (face === 'left') return false;
			if (face === 'back') return s > 0.5 - jag;
			return s < 0.5 + jag;
		case 'top_half':
			if (face === 'up') return true; if (face === 'down') return false;
			return t < 0.5 + jag;
		case 'bottom_half':
			if (face === 'up') return false; if (face === 'down') return true;
			return t > 0.5 + jag;
		case 'chest':
			if (face === 'up') return true; if (face === 'down' || face === 'back') return false;
			if (face === 'front') return t < 0.72 + jag;
			return t < 0.45 + jag;
	}
	return false;
}
function plateBox(cv, u, v, w, h, d, mode, seed) {
	const R = boxRects(u, v, w, h, d);
	Object.entries(R).forEach(([face, r], fi) => {
		const P = (i, j) => i >= 0 && j >= 0 && i < r.w && j < r.h && plated(mode, face, (i + 0.5) / r.w, (j + 0.5) / r.h, j, seed, fi);
		for (let j = 0; j < r.h; j++) for (let i = 0; i < r.w; i++) {
			if (!P(i, j)) continue;
			let c = plateTexel(i, j, seed + fi * 17);
			// dark edge where the plating meets the mob's own skin
			if ((i > 0 && !P(i - 1, j)) || (i < r.w - 1 && !P(i + 1, j)) || (j > 0 && !P(i, j - 1)) || (j < r.h - 1 && !P(i, j + 1))) c = hex('121216');
			cv.set(r.x + i, r.y + j, c);
		}
	});
}
function eyePix(cv, x, y, hot) { cv.set(x, y, hot ? HOT.map((v, i) => [255, 128, 112][i]) : hex('FF2A1A')); }
function eyesHumanoid(cv, rowY, x0 = 9, x1 = 13, rows = 1) {
	for (let r = 0; r < rows; r++) { eyePix(cv, x0, rowY + r, false); eyePix(cv, x0 + 1, rowY + r, r === 0); eyePix(cv, x1, rowY + r, r === 0); eyePix(cv, x1 + 1, rowY + r, false); }
}
function socket(cv, pts) { for (const [x, y] of pts) if (cv.get(x, y)[3]) cv.set(x, y, hex('0C0808')); }
const A = 'textures/entity/ultron/assimilated/';
{ // zombie (64x64 humanoid)
	const cv = new Canvas(64, 64), ey = new Canvas(64, 64);
	plateBox(cv, 0, 0, 8, 8, 8, 'right_half', 401);     // head
	plateBox(cv, 16, 16, 8, 12, 4, 'right_half', 402);  // body
	plateBox(cv, 40, 16, 4, 12, 4, 'full', 403);        // right arm
	plateBox(cv, 0, 16, 4, 12, 4, 'full', 404);         // right leg
	socket(cv, [[9, 12], [10, 12], [13, 12], [14, 12]]);
	eyesHumanoid(ey, 12);
	writePng(A + 'zombie.png', cv); writePng(A + 'zombie_eyes.png', ey);
}
{ // skeleton (64x32): limbs share UVs with the mirrored left limbs, so they get partial plating
	const cv = new Canvas(64, 32), ey = new Canvas(64, 32);
	plateBox(cv, 0, 0, 8, 8, 8, 'right_half', 411);
	plateBox(cv, 16, 16, 8, 12, 4, 'right_half', 412);
	plateBox(cv, 40, 16, 2, 12, 2, 'top_half', 413);
	plateBox(cv, 0, 16, 2, 12, 2, 'bottom_half', 414);
	socket(cv, [[9, 12], [10, 12], [13, 12], [14, 12]]);
	eyesHumanoid(ey, 12);
	writePng(A + 'skeleton.png', cv); writePng(A + 'skeleton_eyes.png', ey);
}
{ // spider (64x32)
	const cv = new Canvas(64, 32), ey = new Canvas(64, 32);
	plateBox(cv, 32, 4, 8, 8, 8, 'right_half', 421);   // head
	plateBox(cv, 0, 0, 6, 6, 6, 'right_half', 422);    // neck
	plateBox(cv, 0, 12, 10, 8, 12, 'right_half', 423); // abdomen
	plateBox(cv, 18, 0, 16, 2, 2, 'right_half', 424);  // legs (shared by all 8)
	const eyes = [[42, 14, 1], [45, 14, 1], [41, 13, 0], [46, 13, 0], [43, 16, 0], [44, 16, 0], [41, 16, 0], [46, 16, 0]];
	socket(cv, eyes);
	for (const [x, y, hot] of eyes) eyePix(ey, x, y, !!hot);
	writePng(A + 'spider.png', cv); writePng(A + 'spider_eyes.png', ey);
}
{ // creeper (64x32)
	const cv = new Canvas(64, 32), ey = new Canvas(64, 32);
	plateBox(cv, 0, 0, 8, 8, 8, 'right_half', 431);
	plateBox(cv, 16, 16, 8, 12, 4, 'right_half', 432);
	plateBox(cv, 0, 16, 4, 6, 4, 'right_half', 433);   // legs (shared by all 4)
	const eyes = [[9, 11], [10, 11], [9, 12], [10, 12], [13, 11], [14, 11], [13, 12], [14, 12]];
	socket(cv, eyes);
	eyes.forEach(([x, y]) => eyePix(ey, x, y, (x === 10 || x === 13) && y === 11));
	writePng(A + 'creeper.png', cv); writePng(A + 'creeper_eyes.png', ey);
}
{ // pillager (64x64 illager)
	const cv = new Canvas(64, 64), ey = new Canvas(64, 64);
	plateBox(cv, 0, 0, 8, 10, 8, 'right_half', 441);    // head
	plateBox(cv, 24, 0, 2, 4, 2, 'right_half', 442);    // nose
	plateBox(cv, 16, 20, 8, 12, 6, 'right_half', 443);  // body
	plateBox(cv, 44, 22, 4, 8, 4, 'right_half', 444);   // crossed arms
	plateBox(cv, 40, 38, 8, 4, 4, 'right_half', 445);   // crossed arms (middle)
	plateBox(cv, 40, 46, 4, 12, 4, 'top_half', 446);    // separate arms (shared, mirrored)
	plateBox(cv, 0, 22, 4, 12, 4, 'right_half', 447);   // legs (shared, mirrored)
	socket(cv, [[9, 12], [10, 12], [13, 12], [14, 12]]);
	eyesHumanoid(ey, 12);
	writePng(A + 'pillager.png', cv); writePng(A + 'pillager_eyes.png', ey);
}
{ // ultron golem overlay (128x128 iron golem layout)
	const cv = new Canvas(128, 128), ey = new Canvas(128, 128);
	plateBox(cv, 0, 0, 8, 10, 8, 'top_half', 451);       // head, top half
	plateBox(cv, 0, 40, 18, 12, 11, 'chest', 452);       // chest
	plateBox(cv, 60, 21, 4, 30, 6, 'top_half', 453);     // right arm, shoulder to forearm
	const eyes = [[9, 12], [10, 12], [13, 12], [14, 12], [9, 13], [10, 13], [13, 13], [14, 13]];
	socket(cv, eyes);
	eyes.forEach(([x, y]) => eyePix(ey, x, y, (x === 10 || x === 13) && y === 12));
	writePng('textures/entity/ultron/ultron_golem.png', cv); writePng('textures/entity/ultron/ultron_golem_eyes.png', ey);
}
{ // legion visor: slanted Iron-Man-style eye slits on the helmet front face
	const ey = new Canvas(64, 32);
	for (const [x, y, hot] of [[9, 11, 0], [10, 11, 1], [11, 12, 0], [12, 12, 0], [13, 11, 1], [14, 11, 0]]) eyePix(ey, x, y, !!hot);
	ey.set(11, 12, hex('C81C10')); ey.set(12, 12, hex('C81C10'));
	writePng('textures/entity/ultron/legion_visor.png', ey);
}

// ================================================================ 6. GeckoLib structures
// ultron_nexus, lift_engine, uplink_antenna and vibranium_core (geo + texture + _glowmask + animation) are now generated
// by gen_v0161_contagion_models2.js (detailed remake); this script deliberately no longer writes them.

console.log(`gen_v0161_contagion_assets: wrote ${written.length} files under ${path.relative(L.ROOT, RES)}:`);
for (const w of written) console.log('  ' + w);
