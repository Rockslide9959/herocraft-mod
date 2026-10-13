// v0.16.1 raid undead PLAYER SKINS: the Armoured Risen, the Acid Zombie, the Juggernaut, the Cursed Zombie,
// the Sword Skeleton and the Pillager Spy are drawn on the standard player rig (head, body, arms, legs + the hat / jacket /
// sleeve / pants outer layers) with these 64x64 skins. They carry the custom-model designs over (same palettes, wounds,
// gear, glow spots), painted with the same procedural material painters (gen_raid_undead_models.js), supersampled 4x4
// per texel, lit from the top with ambient occlusion in the folds (neck, armpits, inner limbs, belt), and the outer layer
// used for depth (rags, hood, plates, ridges, acid sacs, chains, runes).
// Each skin gets a <name>_glow.png (emissive colour, black elsewhere) for the full-bright eyes-style layer.
//
// Usage (repo root): node scratchpad/gen_raid_player_skins.js [--only name,...] [--no-previews]
// Writes src/main/resources/assets/projecthero/textures/entity/raid_skin/<name>.png + <name>_glow.png and preview sheets
// (front / three-quarter / side / back on a player model) into <main>/scratchpad/shots_v0161/raid_models/skins/.
//
// Coordinates: json-style model pixels, +Y up, the player faces -Z, the player's RIGHT is -X (so the front face image runs
// right -> left, as a skin template does).
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('./lib_concept_models.js');
const G = require('./gen_raid_undead_models.js');
const { mix, mul, fbm, vnoise, hash3, clamp, smooth, vlen, vsub } = L;

const args = process.argv.slice(2);
const oi = args.indexOf('--only');
const ONLY = oi >= 0 ? args[oi + 1].split(',') : null;
const OUT = path.join(G.REPO, 'src/main/resources/assets/projecthero/textures/entity/raid_skin');
const PREV = path.join(G.PREVIEW, 'skins');

// ================================================================ the player rig: parts, UVs, faces
const PARTS = {
	head: { box: [-4, 24, -4, 4, 32, 4], uv: [0, 0], outer: [32, 0], inflate: 0.5, dims: [8, 8, 8] },
	body: { box: [-4, 12, -2, 4, 24, 2], uv: [16, 16], outer: [16, 32], inflate: 0.25, dims: [8, 12, 4] },
	arm_r: { box: [-8, 12, -2, -4, 24, 2], uv: [40, 16], outer: [40, 32], inflate: 0.25, dims: [4, 12, 4] },
	arm_l: { box: [4, 12, -2, 8, 24, 2], uv: [32, 48], outer: [48, 48], inflate: 0.25, dims: [4, 12, 4] },
	leg_r: { box: [-4, 0, -2, 0, 12, 2], uv: [0, 16], outer: [0, 32], inflate: 0.25, dims: [4, 12, 4] },
	leg_l: { box: [0, 0, -2, 4, 12, 2], uv: [16, 48], outer: [0, 48], inflate: 0.25, dims: [4, 12, 4] },
};
// face rects within a part's UV block: [du, dv, width, height] for dims w,h,d
function faceRects(w, h, d) {
	return { up: [d, 0, w, d], down: [d + w, 0, w, d], west: [0, d, d, h], north: [d, d, w, h], east: [d + w, d, d, h], south: [2 * d + w, d, w, h] };
}
// the 3D point (json px) of face coordinates s,t in 0..1 (s across the image, t down it)
function facePoint(box, f, s, t) {
	const [x0, y0, z0, x1, y1, z1] = box;
	const Y = y1 - t * (y1 - y0);
	switch (f) {
		case 'north': return [x0 + s * (x1 - x0), Y, z0];
		case 'south': return [x1 - s * (x1 - x0), Y, z1];
		case 'west': return [x0, Y, z1 - s * (z1 - z0)];
		case 'east': return [x1, Y, z0 + s * (z1 - z0)];
		case 'up': return [x0 + s * (x1 - x0), y1, z1 - t * (z1 - z0)];
		default: return [x0 + s * (x1 - x0), y0, z0 + t * (z1 - z0)];
	}
}

// ================================================================ painting
const SS = 4; // supersamples per texel axis
function paintSkin(unit) {
	const tex = Buffer.alloc(64 * 64 * 4), glow = Buffer.alloc(64 * 64 * 4);
	for (const [pn, P] of Object.entries(PARTS)) {
		for (const layer of ['base', 'outer']) {
			const [U, V] = layer === 'base' ? P.uv : P.outer;
			const [w, h, d] = P.dims;
			const k = layer === 'base' ? 0 : P.inflate;
			const box = [P.box[0] - k, P.box[1] - k, P.box[2] - k, P.box[3] + k, P.box[4] + k, P.box[5] + k];
			for (const [f, [du, dv, fw, fh]] of Object.entries(faceRects(w, h, d))) {
				for (let j = 0; j < fh; j++) for (let i = 0; i < fw; i++) {
					let r = 0, g = 0, b = 0, aSum = 0, gr = 0, gg = 0, gb = 0, gN = 0, n = 0;
					for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
						const s = (i + (sx + 0.5) / SS) / fw, t = (j + (sy + 0.5) / SS) / fh;
						const p = facePoint(box, f, s, t);
						const ePx = Math.min(Math.min(s, 1 - s) * fw, Math.min(t, 1 - t) * fh);
						const ctx = { part: pn, layer, f, s, t, p, i, j, fw, fh, fx: s * fw, fy: t * fh, ePx, front: f === 'north', back: f === 'south', side: f === 'east' || f === 'west', top: f === 'up', bottom: f === 'down' };
						const res = unit.paint(ctx);
						if (!res) continue;
						const [col, a = 255, gc] = res;
						n++;
						if (a > 0) { r += col[0]; g += col[1]; b += col[2]; aSum++; }
						if (gc) { gr += gc[0]; gg += gc[1]; gb += gc[2]; gN++; }
					}
					const ti = ((V + dv + j) * 64 + (U + du + i)) * 4;
					const cover = aSum / (SS * SS);
					if (layer === 'base' || cover >= 0.5) {
						const m = Math.max(1, aSum);
						tex[ti] = clamp(Math.round(r / m)); tex[ti + 1] = clamp(Math.round(g / m)); tex[ti + 2] = clamp(Math.round(b / m)); tex[ti + 3] = 255;
					}
					if (gN >= SS * SS * 0.3 && (layer === 'base' || cover >= 0.5)) {
						glow[ti] = clamp(Math.round(gr / gN)); glow[ti + 1] = clamp(Math.round(gg / gN)); glow[ti + 2] = clamp(Math.round(gb / gN)); glow[ti + 3] = 255;
						// the eyes layer is additive: the base texel shows the glow colour too
						tex[ti] = Math.max(tex[ti], glow[ti]); tex[ti + 1] = Math.max(tex[ti + 1], glow[ti + 1]); tex[ti + 2] = Math.max(tex[ti + 2], glow[ti + 2]);
					}
				}
			}
		}
	}
	pixelPass(tex, glow, unit);
	return { tex, glow };
}

// The pixel-art pass: per-texel jitter (crisp grain instead of smooth noise), a little contrast, and selective outlines --
// where a texel borders a clearly different, brighter material inside the same face, the darker side gets a dark rim
// (sockets round the eyes, shadows along gear edges, seams between plates).
function pixelPass(tex, glow, unit) {
	const src = Buffer.from(tex);
	const lumA = i => 0.3 * src[i] + 0.59 * src[i + 1] + 0.11 * src[i + 2];
	const faceOf = new Int32Array(64 * 64).fill(-1);
	let fid = 0;
	for (const P of Object.values(PARTS)) for (const [U, V] of [P.uv, P.outer]) for (const [du, dv, fw, fh] of Object.values(faceRects(...P.dims))) {
		for (let y = 0; y < fh; y++) for (let x = 0; x < fw; x++) faceOf[(V + dv + y) * 64 + U + du + x] = fid;
		fid++;
	}
	const k = unit.contrast || 1.12;
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const i = (y * 64 + x) * 4;
		if (!src[i + 3] || glow[i + 3]) continue;
		const L0 = lumA(i);
		let rim = false;
		for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
			const nx = x + dx, ny = y + dy;
			if (nx < 0 || ny < 0 || nx > 63 || ny > 63 || faceOf[ny * 64 + nx] !== faceOf[y * 64 + x]) continue;
			const j = (ny * 64 + nx) * 4;
			if (!src[j + 3]) continue;
			const dist = Math.abs(src[i] - src[j]) + Math.abs(src[i + 1] - src[j + 1]) + Math.abs(src[i + 2] - src[j + 2]);
			if (dist > (unit.rimThreshold || 95) && lumA(j) > L0 + 12) rim = true;
		}
		const jit = 0.93 + 0.14 * hash3(x, y, 77);
		for (let c = 0; c < 3; c++) {
			let v = (118 + (src[i + c] - 118) * k) * (unit.bright || 1.14); // brighter: the game's own entity shading darkens the sides
			v *= jit * (rim ? 0.72 : 1);
			tex[i + c] = clamp(Math.round(v));
		}
	}
}
// pixel glyph at an integer offset on a face (rows of '#'), true where the texel is set
function glyphAt(ctx, x0, y0, rows) {
	const y = Math.floor(ctx.fy) - y0, x = Math.floor(ctx.fx) - x0;
	return y >= 0 && y < rows.length && x >= 0 && x < rows[y].length && rows[y][x] === '#';
}
const RUNES = [['#.#', '###', '.#.', '.#.'], ['##.', '#..', '##.', '#..'], ['.#.', '###', '#.#', '#.#'], ['#..', '##.', '#.#', '#..'], ['###', '.#.', '#.#', '.#.']];
// an iron plate with a lit top edge, a dark lower edge, optional lames (horizontal overlaps) and corner rivets
function plate(M, ctx, base, opts = {}) {
	let col = ironAt(M, ctx, base, opts.rust == null ? 1 : opts.rust)[0];
	const y = Math.floor(ctx.fy), x = Math.floor(ctx.fx);
	const y0 = opts.y0 == null ? 0 : opts.y0, y1 = opts.y1 == null ? ctx.fh - 1 : opts.y1;
	if (y === y0) col = mix(col, [190, 186, 178], 0.45);
	else if (y === y1) col = mul(col, 0.62);
	if (opts.lames && y > y0 && (y - y0) % opts.lames === 0) col = mix(col, [180, 176, 168], 0.35);
	if (opts.lames && y > y0 && (y - y0) % opts.lames === opts.lames - 1) col = mul(col, 0.7);
	if (opts.rivets && (y === y0 + 1 || y === y1 - 1) && (x === 0 || x === ctx.fw - 1)) col = [168, 160, 146];
	if (opts.ridge != null && Math.abs(ctx.fx - opts.ridge) < 0.5) col = mix(col, [176, 172, 164], 0.4);
	return col;
}

// ---------------------------------------------------------------- shared helpers
const PAL_RISEN = { base: [126, 138, 108], dark: [62, 70, 58], bruise: [96, 78, 104], rot: [86, 74, 46], vein: [56, 44, 74] };
// lighting: light from the top, AO in the folds (under the head, armpits, inner limbs, crotch, belt line)
function light(ctx, col) {
	const { part, f, p } = ctx;
	let k = 1;
	if (f === 'up') k *= 1.1;
	else if (f === 'down') k *= 0.9;
	else k *= 1.05 - 0.1 * ctx.t; // top lighter, bottom darker on every side face
	if (part === 'body' && f !== 'up' && p[1] > 22.6) k *= 0.82 + 0.18 * (24 - p[1]) / 1.4 * 0;  // neck shadow
	if (part === 'body' && f !== 'up' && p[1] > 23.0) k *= 0.8;
	if ((part === 'arm_r' && f === 'east') || (part === 'arm_l' && f === 'west')) k *= 0.86;               // inner arm faces the body
	if ((part === 'arm_r' || part === 'arm_l') && f !== 'up' && p[1] > 22.5) k *= 0.9;                       // armpit / shoulder seam
	if ((part === 'leg_r' && f === 'east') || (part === 'leg_l' && f === 'west')) k *= 0.82;                // inner legs
	if ((part === 'leg_r' || part === 'leg_l') && f !== 'down' && p[1] > 11) k *= 0.8;                        // under the body
	if (part === 'head' && f !== 'up' && p[1] < 24.6) k *= 0.85;                                             // chin edge
	if (ctx.layer === 'base' && ctx.ePx < 0.25 && f !== 'up') k *= 0.9;                                      // box edges
	return mul(col, k);
}
// a fake cube for the shared painters (they read c.paint / c.tag / c.seed / c.from / c.to)
const C = (ctx, paint, tag = null, seed = 0) => { const P = PARTS[ctx.part]; return { paint, tag, seed, from: P.box.slice(0, 3), to: P.box.slice(3) }; };
const INFO = ctx => ({ i: ctx.i, j: ctx.j, w: ctx.fw, h: ctx.fh, fw: ctx.fw, fh: ctx.fh, ePx: ctx.ePx * 3, D: 1 });
const fleshAt = (M, ctx, paint = 'skin') => G.flesh(M, C(ctx, paint), ctx.f, ctx.s, ctx.t, ctx.p, INFO(ctx));
const clothAt = (M, ctx, base, tag = 'solid', seed = 0) => G.clothMat(M, C(ctx, 'cloth', tag, seed), ctx.f, ctx.s, ctx.t, ctx.p, INFO(ctx), base);
const ironAt = (M, ctx, base, rust = 1) => G.ironMat(M, C(ctx, 'iron'), ctx.f, ctx.s, ctx.t, ctx.p, Object.assign(INFO(ctx), { fw: 1, fh: 1 }), base, rust);
const leatherAt = (M, ctx, base) => G.leatherMat(M, C(ctx, 'leather'), ctx.f, ctx.s, ctx.t, ctx.p, INFO(ctx), base);
const boneAt = (M, ctx) => G.boneMat(M, C(ctx, 'bone'), ctx.f, ctx.s, ctx.t, ctx.p, INFO(ctx));
const hairAt = (M, ctx, base, hi) => G.hairMat(M, C(ctx, 'hair'), ctx.f, ctx.s, ctx.t, ctx.p, INFO(ctx), base, hi);
const N = (p, k, o = 0) => fbm(p[0] * k + o, p[1] * k, p[2] * k, 3);
// pixel-art map lookup on a face (rows top->bottom, cols image-left->right); returns the char or null
function mapAt(ctx, rows) {
	const y = Math.floor(ctx.fy), x = Math.floor(ctx.fx);
	const row = rows[y];
	return row ? (row[x] || null) : null;
}
const inBox = (p, x0, y0, x1, y1, z0 = -99, z1 = 99) => p[0] >= x0 && p[0] <= x1 && p[1] >= y0 && p[1] <= y1 && p[2] >= z0 && p[2] <= z1;

// ================================================================ the units
const UNITS = {};

// ---------------------------------------------------------------- the Risen (basic) + Armoured + grave-child
function risenPainter(opts = {}) {
	const M = { pal: opts.pal || PAL_RISEN, seed: 3, wounds: opts.wounds || [], holes: opts.holes || [], stitches: [] };
	const shirt = opts.shirt || [92, 104, 116], trousers = opts.trousers || [74, 60, 46];
	const EYE = opts.eye || [236, 226, 150], EYE_HOT = [255, 252, 220];
	const face = opts.face || [
		'HHhHHhhH',
		'hhhhhhhh',
		'bbbbbbbb',
		'hSEhhESh',
		'hhhNNhhw',
		'hhhhhhww',
		'TtTTtTTh',
		'jjjjjjjj',
	];
	return ctx => {
		const { part, layer, p, f } = ctx;
		if (layer === 'base') {
			let col = fleshAt(M, ctx)[0], glowC = null;
			if (part === 'head') {
				if (f === 'north') {
					const ch = mapAt(ctx, face);
					if (ch === 'H') col = hairAt(M, ctx, [34, 30, 28], [80, 74, 64])[0];
					else if (ch === 'b') col = mul(col, 0.7);
					else if (ch === 'S') col = [22, 16, 18];
					else if (ch === 'E') { col = EYE_HOT; glowC = EYE; }
					else if (ch === 'N') col = [28, 16, 16];
					else if (ch === 'w') col = mix([120, 30, 30], [70, 18, 20], N(p, 2));
					else if (ch === 'T') col = mix([196, 184, 150], [226, 214, 184], N(p, 3));
					else if (ch === 't') col = [30, 16, 16];
					else if (ch === 'j') col = mul(col, 0.82);
					else if (ch === 'X') { col = mix([150, 130, 100], [214, 200, 168], N(p, 3)); } // exposed teeth through the cheek
				} else if (f === 'up' || (p[1] > 30.5 && f !== 'down')) col = hairAt(M, ctx, [34, 30, 28], [80, 74, 64])[0];
				if (opts.headExtra) { const r = opts.headExtra(ctx, col); if (r) { col = r[0]; glowC = r[1] || glowC; } }
			} else if (part === 'body') {
				const torn = opts.bodyHole && opts.bodyHole(ctx);
				if (!torn) col = clothAt(M, ctx, shirt)[0];
				if (opts.bodyExtra) { const r = opts.bodyExtra(ctx, col); if (r) { col = r[0]; glowC = r[1] || glowC; } }
			} else if (part === 'leg_r' || part === 'leg_l') {
				if (p[1] > (opts.trouserBottom ? opts.trouserBottom(ctx) : 5)) col = clothAt(M, ctx, trousers)[0];
				else if (opts.feet && p[1] < 1.6) col = leatherAt(M, ctx, [50, 38, 30])[0];
			} else if (opts.sleeve && opts.sleeve(ctx)) col = clothAt(M, ctx, shirt)[0];
			if (opts.baseExtra) { const r = opts.baseExtra(ctx, col); if (r) { col = r[0]; glowC = r[1] || glowC; } }
			return [light(ctx, col), 255, glowC];
		}
		// ---- outer layer
		if (opts.outer) { const r = opts.outer(ctx, M); if (r) return [light(ctx, r[0]), r[1] == null ? 255 : r[1], r[2] || null]; }
		return [[0, 0, 0], 0, null];
	};
}
// the ragged shirt / trousers / hair strands of a Risen on the outer layer
function risenOuter(ctx, M, extra) {
	const { part, p, f } = ctx;
	if (extra) { const r = extra(ctx, M); if (r) return r; }
	if (part === 'head') {
		// lank hair strands hanging at the sides / back, a few on top
		if (f === 'up') return N(p, 1.4, 7) > 0.55 ? [hairAt(M, ctx, [34, 30, 28], [80, 74, 64])[0]] : null;
		if (f === 'north') return null;
		const strand = hash3(Math.floor((p[0] + p[2]) * 1.0), 7, 3);
		const len = 2 + strand * 6;
		return strand > 0.45 && p[1] > 32.5 - len ? [hairAt(M, ctx, [30, 26, 24], [74, 68, 58])[0]] : null;
	}
	if (part === 'body') {
		// a ragged shirt tail hanging over the belt, torn edges
		if (p[1] < 14.5 && p[1] > 12 - 0.3 && hash3(Math.floor(p[0] + p[2] * 2 + 9), 2, 5) > 0.35 && f !== 'up') {
			const rag = 12.0 + hash3(Math.floor(p[0] * 1.5 + p[2] * 3), 4, 1) * 2.2;
			if (p[1] > rag) return [clothAt(M, ctx, [86, 96, 108])[0]];
		}
		if (f !== 'up' && f !== 'down' && p[1] > 22.8) return [clothAt(M, ctx, [100, 110, 122])[0]]; // the collar
		return null;
	}
	if (part === 'arm_r' && p[1] > 18 && p[1] < 24 && f !== 'up') {
		const rag = 18.2 + hash3(Math.floor(p[2] * 2 + p[0]), 3, 2) * 2.4; // one ragged sleeve
		return p[1] > rag ? [clothAt(M, ctx, [92, 104, 116])[0]] : null;
	}
	if ((part === 'leg_r' || part === 'leg_l') && f !== 'up' && f !== 'down') {
		const rag = 4.6 + hash3(Math.floor(p[0] * 2 + p[2] * 3), part.length, 4) * 2.2; // trousers rotted off below the knee
		if (p[1] < rag + 0.9 && p[1] > rag) return [clothAt(M, ctx, [64, 52, 40])[0]];
		if (p[1] > 10.5 && p[1] < 12) return [leatherAt(M, ctx, [70, 50, 34])[0]]; // rope belt line
	}
	return null;
}
// the Risen's look: internal only -- the basic and baby Risen use the vanilla zombie (the user's choice); this is the base the
// Armoured Risen's skin is painted on
UNITS.raid_zombie = {
	internal: true,
	title: 'RISEN (RAID ZOMBIE)', sub: 'TORN LINEN, EXPOSED BONE, LOOSE JAW', pose: 'zombie',
	paint: risenPainter({
		wounds: [{ c: [-3.2, 17, -2], r: 2.1, bone: true }, { c: [6, 15.5, -2], r: 1.4, bone: true }, { c: [-2, 4, -2], r: 1.2, bone: true }, { c: [1, 28.5, -4], r: 0.9 }],
		bodyHole: ctx => ctx.p[0] < -1.2 && ctx.p[1] > 14.5 && ctx.p[1] < 20 && (ctx.front || ctx.f === 'west'), // the flank torn open
		bodyExtra: (ctx, col) => {
			// ribs showing through the torn flank
			const { p } = ctx;
			if (p[0] < -1.2 && p[1] > 14.5 && p[1] < 20 && (ctx.front || ctx.f === 'west')) {
				const rib = Math.abs(((p[1] - 14.5) % 1.6) - 0.8) < 0.35;
				return [rib ? mix([188, 176, 146], [226, 214, 184], N(p, 3)) : mix([110, 26, 28], [50, 12, 16], N(p, 2))];
			}
			return null;
		},
		sleeve: ctx => ctx.part === 'arm_r' && ctx.p[1] > 19.5,
		feet: true,
	}),
	outerFn: risenOuter,
};
UNITS.raid_zombie.paint = withOuter(UNITS.raid_zombie.paint, (ctx, M) => risenOuter(ctx, M));
// glue: the risenPainter's outer hook is set after construction
function withOuter(paint, outer) {
	return ctx => {
		if (ctx.layer === 'outer') { const r = outer(ctx, { pal: PAL_RISEN, seed: 3, holes: [], wounds: [] }); return r ? [light(ctx, r[0]), r[1] == null ? 255 : r[1], r[2] || null] : [[0, 0, 0], 0, null]; }
		return paint(ctx);
	};
}

// the Armoured Risen: the same body under a rusted iron kit on the outer layer (sallet with a nasal bar, breastplate,
// one big pauldron, vambraces, greaves, a mail skirt)
UNITS.raid_zombie_armoured = {
	title: 'ARMOURED RISEN', sub: 'RUSTED SALLET, BREASTPLATE, PAULDRON, GREAVES', pose: 'zombie',
	paint: withOuter(UNITS.raid_zombie.paint, (ctx, M) => {
		const { part, p, f } = ctx;
		const IRON = [104, 106, 112];
		const mail = () => { const y2 = Math.floor(ctx.fy), x2 = Math.floor(ctx.fx) + (y2 & 1); const hole = (x2 % 2 === 0); return [hole ? [84, 80, 76] : mix([96, 92, 88], [140, 134, 124], N(p, 2))]; };
		const y = Math.floor(ctx.fy);
		if (part === 'head') {
			if (f === 'down') return null;
			if (f === 'north') {
				if (y <= 1) return [plate(M, ctx, IRON, { y0: 0, y1: 1 })];                          // the brow of the sallet
				if (y === 2) return [[30, 26, 26]];                                                 // the eye slit's shadow
				if ((Math.floor(ctx.fx) === 3 || Math.floor(ctx.fx) === 4) && y <= 4) return [plate(M, ctx, [124, 124, 130], { rust: 0.3, y0: 3, y1: 4 })]; // nasal bar
				return null;
			}
			if (f === 'up') return [plate(M, ctx, IRON, { ridge: 3.5 })];
			if (y <= 4) return [plate(M, ctx, IRON, { y0: 0, y1: 4, rivets: true })];
			if (f === 'south' && y <= 6) return [plate(M, ctx, IRON, { y0: 5, y1: 6 })];            // the flared neck guard
			return null;
		}
		if (part === 'body') {
			if (f === 'up' || f === 'down') return null;
			if (y >= 10) return mail();                                                             // the mail skirt
			if ((f === 'west' || f === 'east') && y >= 2) return [leatherAt(M, ctx, [44, 34, 30])[0]]; // side straps
			if (y === 9) return [leatherAt(M, ctx, [60, 44, 32])[0]];                                // the waist strap
			return [plate(M, ctx, IRON, { y0: 0, y1: 8, ridge: f === 'north' ? 3.5 : null, rivets: true })];
		}
		if (part === 'arm_r' && y <= 4 && f !== 'down') return [plate(M, ctx, IRON, { y0: 0, y1: 4, lames: 2 })]; // the pauldron
		if ((part === 'arm_r' || part === 'arm_l') && y >= 7 && y <= 10 && f !== 'up' && f !== 'down') return [plate(M, ctx, IRON, { y0: 7, y1: 10 })]; // vambraces
		if ((part === 'leg_r' || part === 'leg_l') && f !== 'up' && f !== 'down') {
			if (y <= 1) return mail();
			if (y >= 3 && y <= 10 && (f === 'north' || f === 'east' || f === 'west')) {
				if (f === 'north' && y >= 3 && y <= 4) return [plate(M, ctx, [130, 130, 134], { rust: 0.4, y0: 3, y1: 4 })]; // knee cop
				return [plate(M, ctx, IRON, { y0: 5, y1: 10 })];
			}
		}
		return null;
	}),
};

// ---------------------------------------------------------------- the Acid Zombie
const ACID = [140, 255, 70], ACID_HOT = [214, 255, 150];
function acidSac(ctx, centres) {
	for (const c of centres) {
		const d = vlen(vsub(ctx.p, c.c)) / c.r;
		if (d < 1) {
			const n = N(ctx.p, 2.2, 5);
			const col = mix(ACID_HOT, [40, 150, 20], d * 0.8 + n * 0.3);
			if (d > 0.7) return [mix(col, [70, 110, 36], 0.65), null];
			return [col, mul(mix(ACID, ACID_HOT, 1 - d), 0.85)];
		}
	}
	return null;
}
UNITS.acid_zombie = {
	title: 'ACID ZOMBIE', sub: 'BLOATED, GLOWING ACID SACS, DRIPPING JAW', pose: 'zombie',
	paint: (() => {
		const pal = { base: [150, 156, 112], dark: [58, 70, 46], bruise: [118, 96, 112], rot: [92, 104, 40], vein: [52, 96, 30] };
		const M = { pal, seed: 5, wounds: [], holes: [], stitches: [] };
		const sacsBase = [{ c: [2.2, 16.5, -2], r: 1.8 }, { c: [-5.8, 22.5, -2], r: 1.4 }];
		const sacsOuter = [{ c: [-1.8, 20.5, 2.3], r: 2.6 }, { c: [2.2, 17.2, 2.3], r: 2.2 }, { c: [0.4, 13.6, 2.3], r: 1.6 }, { c: [-6.2, 23.9, 0], r: 1.8 },
			{ c: [2.4, 15.4, -2.3], r: 1.9 }, { c: [-3.6, 31.6, -1.0], r: 1.4 }, { c: [3.6, 24.1, -1.2], r: 1.3 }];
		const face = [
			'hhhhhhhh',
			'hhhhhhhh',
			'bbbbbbbb',
			'hSEhhESh',
			'hhhNNhhh',
			'GGGGGGGG',
			'TGTGGTGT',
			'jjGjjGjj',
		];
		return ctx => {
			const { part, layer, p, f } = ctx;
			if (layer === 'base') {
				let col = fleshAt(M, ctx)[0], glowC = null;
				// green veins spreading from the sacs
				const v = Math.abs(N(p, 0.9, 4) - 0.5);
				const nearSac = sacsOuter.concat(sacsBase).some(c => vlen(vsub(p, c.c)) < c.r + 2.2);
				if (nearSac && v < 0.014 && part !== 'leg_r' && part !== 'leg_l') { col = mix(col, ACID, 0.75); glowC = mul(ACID, 0.6); }
				else if (v < 0.025 && part !== 'leg_r' && part !== 'leg_l') col = mix(col, [70, 110, 40], 0.5);
				if (part === 'head' && f === 'north') {
					const ch = mapAt(ctx, face);
					if (ch === 'b') col = mul(col, 0.7);
					else if (ch === 'S') col = [18, 22, 14];
					else if (ch === 'E') { col = [240, 255, 200]; glowC = [190, 255, 110]; }
					else if (ch === 'N') col = [20, 26, 16];
					else if (ch === 'G') { col = mix(ACID_HOT, ACID, N(p, 3)); glowC = ACID; }     // the glowing gullet
					else if (ch === 'T') col = [214, 206, 160];
					else if (ch === 'j') col = mul(col, 0.8);
				}
				if (part === 'body') {
					col = mul(col, 1.04);
					if (f === 'north' && Math.abs(p[0]) > 2.3 && Math.abs(p[0]) < 3.3 && p[1] > 17) col = leatherAt(M, ctx, [66, 56, 40])[0];        // suspenders
					if (f === 'north' && Math.abs(p[1] - 15.2) < 0.3 && Math.abs(p[0]) < 3.6) col = mul(col, 0.7);                               // the belly's sag line
				}
				if ((part === 'leg_r' || part === 'leg_l')) col = p[1] < 5 ? leatherAt(M, ctx, [44, 34, 30])[0] : clothAt(M, ctx, [70, 74, 60])[0];
				const sac = acidSac(ctx, sacsBase);
				if (sac && part !== 'leg_r' && part !== 'leg_l') { col = sac[0]; glowC = sac[1]; }
				return [light(ctx, col), 255, glowC];
			}
			// outer: bulging acid sacs on the back, shoulder and belly, drips off the jaw and a belly sac, ragged trouser hems
			const sac = acidSac(ctx, sacsOuter);
			if (sac && f !== 'down') return [sac[0], 255, sac[1]];
			if (part === 'head' && f === 'north' && p[1] < 25.2 && (Math.abs(p[0] + 1.2) < 0.45 || Math.abs(p[0] - 1.6) < 0.45)) return [mix(ACID_HOT, ACID, ctx.t), 255, ACID]; // drips
			if (part === 'body' && f === 'north' && Math.abs(p[0] - 2.4) < 0.4 && p[1] < 14 && p[1] > 12) return [ACID, 255, ACID];
			if ((part === 'leg_r' || part === 'leg_l') && f !== 'up' && f !== 'down') { const rag = 5 + hash3(Math.floor(p[0] * 2 + p[2] * 3), 2, 8) * 2; if (p[1] > rag && p[1] < rag + 0.9) return [clothAt(M, ctx, [62, 66, 52])[0]]; }
			return [[0, 0, 0], 0, null];
		};
	})(),
};

// ---------------------------------------------------------------- the Juggernaut
UNITS.juggernaut_zombie = {
	title: 'JUGGERNAUT', sub: 'SCRAP PLATES, CHAIN, RIVETED MASK', pose: 'zombie',
	paint: (() => {
		const pal = { base: [158, 124, 118], dark: [76, 50, 56], bruise: [112, 76, 104], rot: [104, 82, 54], vein: [80, 40, 62] };
		const M = { pal, seed: 7, wounds: [{ c: [-2.5, 14, -2], r: 1.2 }, { c: [-2, 9, -2], r: 1 }], holes: [], stitches: [
			{ a: [-3.6, 13.4, -2], b: [3.4, 12.6, -2] }, { a: [-8, 14, -1], b: [-8, 22, -1] }, { a: [-3, 14, 2], b: [3, 22, 2] }] };
		const IRON = [88, 86, 84], EMBER = [255, 120, 40];
		return ctx => {
			const { part, layer, p, f } = ctx;
			if (layer === 'base') {
				let col = fleshAt(M, ctx)[0], glowC = null;
				if (part === 'head') {
					// the head under the mask: scarred scalp, the mask itself is on the hat layer
					if (f === 'north') col = mul(col, 0.6);
				}
				if (part === 'body' && p[1] < 13.6) col = leatherAt(M, ctx, [62, 44, 32])[0]; // belt
				if ((part === 'leg_r' || part === 'leg_l')) col = p[1] > 6 ? clothAt(M, ctx, [72, 62, 52])[0] : clothAt(M, ctx, [60, 52, 44])[0];
				if ((part === 'arm_r' || part === 'arm_l') && p[1] < 13.6) col = leatherAt(M, ctx, [44, 34, 30])[0]; // wrapped hands
				return [light(ctx, col), 255, glowC];
			}
			// ---- outer: the riveted face-mask with burning slits, scrap plates, chain, pauldron, gauntlets, knee plates
			if (part === 'head') {
				if (f === 'north') {
					const eyeRow = Math.floor(ctx.fy) === 3;
					const x = Math.floor(ctx.fx);
					if (eyeRow && (x === 1 || x === 2 || x === 5 || x === 6)) return [[255, 190, 120], 255, EMBER];
					if (Math.floor(ctx.fy) === 6 && (x % 2 === 1)) return [[20, 16, 14]];
					return [ironAt(M, ctx, IRON)[0]];
				}
				if (f === 'up') return N(p, 1.2, 3) > 0.45 ? [ironAt(M, ctx, [58, 58, 62])[0]] : null;
				if (p[1] > 30.2 && p[1] < 31.2) return [ironAt(M, ctx, [58, 58, 62])[0]]; // the band round the skull
				if ((f === 'east' || f === 'west') && p[2] < -1) return [ironAt(M, ctx, IRON)[0]];
				return null;
			}
			if (part === 'body') {
				if (f === 'up' || f === 'down') return null;
				// a chain across the body (right shoulder to left hip), front and back
				const chainD = Math.abs((p[1] - 24) + (p[0] + 4) * 1.2);
				if ((f === 'north' || f === 'south') && chainD < 0.9) { const link = (Math.floor(p[0] * 1.4 + p[1]) & 1); return [link ? [96, 96, 100] : [36, 36, 40]]; }
				// overlapping scrap plates with rivets
				const plate = (f === 'north' && ((p[0] > -0.5 && p[1] > 14 && p[1] < 19.5) || (p[0] < 1 && p[1] > 19 && p[1] < 23.2))) || (f === 'south' && p[1] > 15 && p[1] < 23);
				if (plate) { const riv = (Math.abs(ctx.fx % 3 - 1.5) < 0.4 && Math.abs(ctx.fy % 4 - 0.8) < 0.4); return [riv ? [150, 142, 130] : ironAt(M, ctx, IRON)[0]]; }
				if (p[1] < 13.8) return [leatherAt(M, ctx, [62, 44, 32])[0]]; // loin flap over the belt
				return null;
			}
			if (part === 'arm_l' && p[1] > 19.5 && f !== 'down') return [ironAt(M, ctx, IRON)[0]]; // the pauldron
			if (part === 'arm_l' && p[1] > 21 && f === 'up') return [[60, 58, 60]];
			if ((part === 'arm_r' || part === 'arm_l') && p[1] > 13 && p[1] < 18.6 && f !== 'up' && f !== 'down') {
				if (Math.abs(p[1] - 15) < 0.35) return [ironAt(M, ctx, [58, 58, 62])[0]];
				return [ironAt(M, ctx, IRON)[0]]; // gauntlet rams
			}
			if (part === 'arm_l' && p[1] < 13 && p[1] > 12 && f !== 'up') return [ironAt(M, ctx, [58, 58, 62])[0]]; // shackle
			if ((part === 'leg_r' || part === 'leg_l') && f === 'north' && p[1] > 4.5 && p[1] < 8) return [ironAt(M, ctx, IRON)[0]]; // knee plates
			return [[0, 0, 0], 0, null];
		};
	})(),
};

// ---------------------------------------------------------------- the Cursed Zombie
const CURSE = [184, 84, 255], CURSE_HOT = [238, 200, 255];
UNITS.cursed_zombie = {
	title: 'CURSED ZOMBIE', sub: 'GRAVE SHROUD, GRAVEBOUND RUNES', pose: 'zombie',
	paint: (() => {
		const pal = { base: [150, 140, 158], dark: [64, 56, 76], bruise: [110, 80, 128], rot: [90, 76, 70], vein: [90, 50, 130] };
		const M = { pal, seed: 13, wounds: [{ c: [-2, 15, -2], r: 1.0, bone: true }], holes: [], stitches: [] };
		const face = [
			'HhhhhhhH',
			'BBBBBBBB',
			'bbbbbbbb',
			'hSEhhESh',
			'hRhNNhRh',
			'hRhhhhRh',
			'TGtTGtTh',
			'jjjjjjjj',
		];
		const rune = ctx => {
			const { part, p, f } = ctx;
			// the ringed sigil over the heart (the player's left breast = the right half of the front image)
			if (part === 'body' && f === 'north') return glyphAt(ctx, 4, 2, ['.###.', '#.#.#', '##.##', '#.#.#', '.###.']);
			// a column of runes cut into the arms and shins (outer and front faces)
			const outerFace = (part === 'arm_r' && f === 'west') || (part === 'arm_l' && f === 'east') || f === 'north';
			if ((part === 'arm_r' || part === 'arm_l') && outerFace) {
				const a = RUNES[(part.length + f.length) % RUNES.length], b = RUNES[(part.length * 3 + f.length + 2) % RUNES.length];
				return glyphAt(ctx, 0, 2, a) || glyphAt(ctx, 1, 7, b);
			}
			if ((part === 'leg_r' || part === 'leg_l') && f === 'north') return glyphAt(ctx, 1, 5, RUNES[part.length % RUNES.length]);
			return false;
		};
		return ctx => {
			const { part, layer, p, f } = ctx;
			if (layer === 'base') {
				let col = fleshAt(M, ctx)[0], glowC = null;
				if (part === 'head' && f === 'north') {
					const ch = mapAt(ctx, face);
					if (ch === 'B') col = clothAt(M, ctx, [150, 144, 128])[0];        // the bandage band
					else if (ch === 'b') col = mul(col, 0.7);
					else if (ch === 'S') col = [20, 14, 26];
					else if (ch === 'E' || ch === 'R' || ch === 'G') { col = CURSE_HOT; glowC = CURSE; }
					else if (ch === 'N') col = [24, 16, 28];
					else if (ch === 'T') col = [204, 190, 176];
					else if (ch === 't') col = [26, 14, 30];
					else if (ch === 'j') col = mul(col, 0.8);
				}
				if (part === 'head' && f !== 'north' && Math.abs(p[1] - 29.5) < 0.5) col = clothAt(M, ctx, [150, 144, 128])[0];
				if (part === 'body' && p[1] < 14.4) col = clothAt(M, ctx, [150, 144, 128])[0]; // burial wrap
				if (rune(ctx)) { col = [214, 150, 255]; glowC = [150, 64, 230]; }
				return [light(ctx, col), 255, glowC];
			}
			// outer: the shroud (a cowl thrown back over the shoulders, a drape down the back), loose bandages, shackles
			if (part === 'head' && (f === 'south' || ((f === 'east' || f === 'west') && p[2] > 1) || (f === 'up' && p[2] > 0.5))) return [clothAt(M, ctx, [52, 42, 66], 'hem', 4)[0]];
			if (part === 'body' && (f === 'south' || ((f === 'east' || f === 'west') && p[2] > 0))) {
				const rag = 12 + hash3(Math.floor(p[0] * 1.5 + p[2] * 2), 6, 2) * 2.4;
				return p[1] > rag ? [clothAt(M, ctx, [52, 42, 66])[0]] : null;
			}
			if (part === 'body' && f === 'north' && p[1] > 22.4) return [clothAt(M, ctx, [58, 46, 74])[0]];           // the cowl's fold at the collar
			if ((part === 'arm_r' || part === 'arm_l') && f !== 'up' && f !== 'down') {
				if (p[1] < 13.3 && p[1] > 12.2) return [ironAt(M, ctx, [58, 58, 62])[0]];                              // broken shackles
				if (p[1] > 22.2) return [clothAt(M, ctx, [52, 42, 66])[0]];                                            // the shroud over the shoulders
				const band = (p[1] - 14) % 2.2; if (p[1] > 13.6 && p[1] < 18 && band < 0.9) return [clothAt(M, ctx, [150, 144, 128])[0]]; // bandages
			}
			if ((part === 'leg_r' || part === 'leg_l') && f !== 'up' && f !== 'down' && p[1] < 3.6 && p[1] > 1) return [clothAt(M, ctx, [150, 144, 128])[0]];
			return [[0, 0, 0], 0, null];
		};
	})(),
};

// ---------------------------------------------------------------- the Sword Skeleton (player-proportioned)
UNITS.sword_skeleton = {
	title: 'SWORD SKELETON', sub: 'CRACKED BONE, NASAL HELM, TATTERED TABARD', pose: 'skeleton',
	paint: (() => {
		const M = { seed: 11, boneTint: [140, 130, 120] };
		const VOID = [16, 13, 14], EYE = [150, 200, 255];
		const face = [
			'KKKKKKKK',
			'KKKKKKKK',
			'KVVKKVVK',
			'KVEKKEVK',
			'KKKNNKKK',
			'KKKKKKKK',
			'TvTvTvTK',
			'KTvTvTvK',
		];
		const bone = ctx => boneAt(M, ctx)[0];
		return ctx => {
			const { part, layer, p, f } = ctx;
			if (layer === 'base') {
				let col = bone(ctx), glowC = null;
				if (part === 'head' && f === 'north') {
					const ch = mapAt(ctx, face);
					if (ch === 'V' || ch === 'N' || ch === 'v') col = VOID;
					else if (ch === 'E') { col = [235, 245, 255]; glowC = EYE; }
				}
				if (part === 'head' && f !== 'north' && f !== 'up' && p[1] < 26) col = mul(col, 0.8);
				if (part === 'body') {
					// the ribcage: dark gaps between the ribs, the sternum and spine
					const front = f === 'north', back = f === 'south';
					if (front || back || ctx.side) {
						if (p[1] < 16.5) col = (front || back) && Math.abs(p[0]) > 0.9 ? VOID : col;           // the open belly, just the spine
						else { const gap = ((p[1] - 16.5) % 1.75) < 0.75; if (gap && !(front && Math.abs(p[0]) < 0.9) && !(back && Math.abs(p[0]) < 1.1)) col = mix(VOID, [40, 30, 28], 0.3); }
						if (p[1] < 14) col = (front || back) ? (Math.abs(p[0]) < 3 ? bone(ctx) : VOID) : col; // the pelvis
					}
				}
				if ((part === 'arm_r' || part === 'arm_l' || part === 'leg_r' || part === 'leg_l')) {
					// long bones: a dark shadow either side of the bone, knobbly joints
					const u = Math.abs(ctx.fx - ctx.fw / 2);
					const joint = (part.startsWith('arm') ? Math.abs(p[1] - 18) < 0.8 : Math.abs(p[1] - 6) < 0.8) || p[1] > 22.6 || (part.startsWith('leg') && p[1] < 1.2);
					if (!joint && u > 1.1 && !ctx.top && !ctx.bottom) col = mix(VOID, col, 0.15);
				}
				return [light(ctx, col), 255, glowC];
			}
			// outer: the nasal helm, a leather pauldron, the tabard (front and back), a belt, leather bracers and boots
			const IRON = [94, 94, 96];
			if (part === 'head') {
				if (f === 'down') return null;
				if (f === 'north') { if (p[1] > 29.6) return [ironAt(M, ctx, IRON)[0]]; if (Math.abs(p[0]) < 0.6 && p[1] > 26.6) return [ironAt(M, ctx, [120, 120, 124], 0.4)[0]]; return null; }
				if (f === 'up' || p[1] > 28.4) return [ironAt(M, ctx, IRON)[0]];
				if (p[1] > 27.5) return [ironAt(M, ctx, [58, 58, 62])[0]];
				return null;
			}
			if (part === 'body' && (f === 'north' || f === 'south')) {
				if (p[1] > 13.2 && p[1] < 14.2) return [leatherAt(M, ctx, [72, 50, 34])[0]];
				if (Math.abs(p[0]) < 1.7 && p[1] < 23) {
					const rag = 12 + hash3(Math.floor(p[0] * 2), f.length, 3) * 1.6;
					if (p[1] < rag) return null;
					let col = clothAt(M, ctx, [110, 34, 34], 'solid')[0];
					const u = p[0], v = p[1] - 19;
					if (Math.hypot(u * 1.2, v) < 1.0 || (Math.abs(Math.abs(u) - Math.abs(v + 0.2)) < 0.35 && Math.abs(u) < 1.8 && Math.abs(v) < 1.8)) col = mix(col, [176, 140, 70], 0.75); // emblem
					return [col];
				}
				return null;
			}
			if (part === 'arm_r' && p[1] > 20 && f !== 'down') return [leatherAt(M, ctx, [72, 50, 34])[0]];
			if ((part === 'arm_r' || part === 'arm_l') && p[1] > 12.5 && p[1] < 16 && f !== 'up' && f !== 'down') return [leatherAt(M, ctx, [62, 44, 30])[0]];
			if ((part === 'leg_r' || part === 'leg_l') && p[1] < 3 && f !== 'up') return [leatherAt(M, ctx, [56, 40, 30])[0]];
			return [[0, 0, 0], 0, null];
		};
	})(),
};

// ---------------------------------------------------------------- the Pillager Spy
UNITS.pillager_spy = {
	title: 'PILLAGER SPY', sub: 'HOODED SCOUT, DARK CLOAK, BANDOLIER, SPYGLASS', pose: 'crossbow',
	paint: (() => {
		const pal = { base: [150, 156, 158], dark: [86, 90, 94], bruise: [120, 120, 130], rot: [120, 124, 120], vein: [110, 114, 120] };
		const M = { pal, seed: 17, wounds: [], holes: [], stitches: [], noBlood: true };
		const HOOD = [64, 56, 82], EYE = [196, 120, 255];
		const face = [
			'ssssssss',
			'ssssssss',
			'hUUUUUUh',
			'hSEhhESh',
			'hhhNNhhh',
			'CCCNNCCC',
			'CCCCCCCC',
			'CCCCCCCC',
		];
		return ctx => {
			const { part, layer, p, f } = ctx;
			if (layer === 'base') {
				let col = fleshAt(M, ctx)[0], glowC = null;
				if (part === 'head') {
					if (f === 'north') {
						const ch = mapAt(ctx, face);
						if (ch === 's') col = mul(col, 0.58);                       // in the hood's shadow
						else if (ch === 'U') col = [34, 32, 36];                    // the one heavy brow
						else if (ch === 'S') col = mul(col, 0.4);
						else if (ch === 'E') { col = [240, 220, 255]; glowC = EYE; }
						else if (ch === 'N') col = mul(col, 0.88);                  // the long nose
						else if (ch === 'C') col = clothAt(M, ctx, [52, 50, 56])[0]; // the scarf over the mouth
						else col = mul(col, 0.62);
					} else col = clothAt(M, ctx, HOOD)[0];
				}
				if (part === 'body') {
					col = leatherAt(M, ctx, [44, 34, 30])[0];                              // the jerkin
					if (f === 'north' || f === 'south') {
						const bd = Math.abs((p[1] - 23) + (p[0] + 4) * 1.15);              // the bandolier, right shoulder to left hip
						if (bd < 0.8) col = leatherAt(M, ctx, [76, 54, 38])[0];
						if (f === 'north' && bd < 0.5 && Math.floor(p[0] * 1.2) % 2 === 0) col = [150, 140, 130];  // bolt fletchings
					}
					if (p[1] < 13.4) col = leatherAt(M, ctx, [76, 54, 38])[0];         // belt
				}
				if (part === 'arm_r' || part === 'arm_l') col = p[1] > 17 ? clothAt(M, ctx, [70, 64, 82])[0] : p[1] > 13.2 ? leatherAt(M, ctx, [76, 54, 38])[0] : leatherAt(M, ctx, [44, 34, 30])[0];
				if (part === 'leg_r' || part === 'leg_l') col = p[1] > 6 ? clothAt(M, ctx, [52, 50, 56])[0] : leatherAt(M, ctx, [76, 54, 38])[0];
				return [light(ctx, col), 255, glowC];
			}
			// outer: the peaked hood (face left open), the long cloak and mantle, pouches, the spyglass at the hip, boot cuffs
			if (part === 'head') {
				if (f === 'north') { if (p[1] > 30.2) return [clothAt(M, ctx, HOOD)[0]]; if (Math.abs(p[0]) > 3.4 && p[1] > 25) return [clothAt(M, ctx, HOOD)[0]]; return null; }
				if (f === 'down') return null;
				return [clothAt(M, ctx, HOOD)[0]];
			}
			if (part === 'body') {
				if (f === 'south' || ((f === 'east' || f === 'west'))) { const rag = 12 + hash3(Math.floor(p[0] * 1.5 + p[2] * 2), 8, 1) * 1.2; return p[1] > rag ? [clothAt(M, ctx, HOOD)[0]] : null; }
				if (f === 'north' && Math.abs(p[0]) > 2.6 && Math.abs(p[0]) < 3.3 && p[1] < 22.4) return [clothAt(M, ctx, [104, 92, 120])[0]]; // the cloak's trimmed edge
				if (f === 'north' && (Math.abs(p[0]) > 2.6 || p[1] > 22.4)) return [clothAt(M, ctx, HOOD)[0]]; // cloak fronts, open over the jerkin
				if (f === 'north' && Math.abs(p[0] + 0.4) < 0.6 && p[1] < 13.6 && p[1] > 12.4) return [[170, 150, 90]];                    // belt buckle
				if (f === 'north' && p[0] > 1.2 && p[0] < 2.6 && p[1] < 14.4 && p[1] > 12.2) return [leatherAt(M, ctx, [76, 54, 38])[0]]; // pouch
				return null;
			}
			if ((part === 'arm_r' || part === 'arm_l') && p[1] > 20.4 && f !== 'down') return [clothAt(M, ctx, HOOD)[0]]; // the mantle over the shoulders
			if (part === 'leg_l' && f === 'east' && p[1] > 7 && p[1] < 11.6 && Math.abs(p[2]) < 0.8) { const n = N(p, 2); return [mix([130, 96, 36], [226, 186, 96], n)]; } // the spyglass at the hip
			if ((part === 'leg_r' || part === 'leg_l') && p[1] > 5.4 && p[1] < 6.6 && f !== 'up' && f !== 'down') return [leatherAt(M, ctx, [56, 40, 30])[0]];
			if ((part === 'leg_r' || part === 'leg_l') && (f === 'south' || f === 'east' || f === 'west') && p[1] > 7) { const rag = 7.5 + hash3(Math.floor(p[0] * 2 + p[2] * 2), 4, 6) * 2.5; return p[1] > rag ? [clothAt(M, ctx, HOOD)[0]] : null; } // the cloak's hem
			return [[0, 0, 0], 0, null];
		};
	})(),
};

// ================================================================ previews on a player model
function previewModel(skin, name) {
	const M = new L.Model(name, { TW: 64, D: 1 });
	M.bone('root', null, [0, 0, 0]);
	const piv = { head: [0, 24, 0], body: [0, 24, 0], arm_r: [-5, 22, 0], arm_l: [5, 22, 0], leg_r: [-2, 12, 0], leg_l: [2, 12, 0] };
	for (const [pn, P] of Object.entries(PARTS)) {
		const b = M.bone(pn, 'root', piv[pn]);
		for (const layer of ['base', 'outer']) {
			const k = layer === 'base' ? 0 : P.inflate;
			const c = M.box(b, P.box[0] - k, P.box[1] - k, P.box[2] - k, P.box[3] + k, P.box[4] + k, P.box[5] + k, 'x');
			const [U, V] = layer === 'base' ? P.uv : P.outer;
			c.uv = {};
			for (const [f, [du, dv, fw, fh]] of Object.entries(faceRects(...P.dims))) c.uv[f] = { u: U + du, v: V + dv, w: fw, h: fh, fw, fh };
		}
	}
	M.tex = Buffer.from(skin.tex); M.glow = Buffer.alloc(64 * 64 * 4);
	for (let i = 0; i < 64 * 64; i++) if (skin.glow[i * 4 + 3]) { M.glow[i * 4] = skin.glow[i * 4]; M.glow[i * 4 + 1] = skin.glow[i * 4 + 1]; M.glow[i * 4 + 2] = skin.glow[i * 4 + 2]; M.glow[i * 4 + 3] = 255; }
	M.TW = 64; M.TH = 64; M.k = 1 / 16; M.heightBlocks = 2;
	return M;
}
function poseFor(kind) {
	if (kind === 'zombie') return { arm_r: [-80, 0, 4], arm_l: [-80, 0, -4], head: [-4, 0, 0] };
	if (kind === 'crossbow') return { arm_r: [-80, -18, 0], arm_l: [-80, 34, 0], head: [-2, -10, 0] };
	return { arm_r: [-6, 0, 4], arm_l: [-6, 0, -4] };
}
function preview(name, unit, skin) {
	const M = previewModel(skin, name);
	const it = (pose, yaw, label) => ({ label, items: [{ model: M, pose, root: [0, 0, 0], floorR: 1.2 }], cam: { yaw, pitch: 6 } });
	const rest = { arm_r: [0, 0, 3], arm_l: [0, 0, -3] };
	const act = poseFor(unit.pose);
	fs.mkdirSync(PREV, { recursive: true });
	L.sheet(path.join(PREV, `${name}.png`), unit.title, unit.sub + ' - 64X64 PLAYER SKIN', [it(rest, 0, 'FRONT'), it(act, 35, 'THREE-QUARTER (POSE)'), it(rest, 90, 'SIDE'), it(rest, 180, 'BACK'), it(rest, 270, 'OTHER SIDE')],
		{ minW: 2200, panelH: 900, maxW: 3600, ss: 2 });
	// the raw skin, scaled up 8x, next to its glow map
	const cv = new L.Canvas(64 * 8 * 2 + 30, 64 * 8 + 60, [40, 42, 48]);
	for (const [k, buf] of [[0, skin.tex], [1, skin.glow]]) for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const i = (y * 64 + x) * 4; if (!buf[i + 3]) continue;
		cv.rect(k * (64 * 8 + 30) + x * 8, 50 + y * 8, 8, 8, [buf[i], buf[i + 1], buf[i + 2]]);
	}
	cv.text(name.toUpperCase().replace(/_/g, ' ') + ' - SKIN / GLOW', 10, 14, 3, [230, 230, 236]);
	cv.save(path.join(PREV, `${name}_texture.png`));
}

function main() {
	fs.mkdirSync(OUT, { recursive: true });
	for (const [name, unit] of Object.entries(UNITS)) {
		if ((ONLY && !ONLY.includes(name)) || unit.internal) continue;
		const skin = paintSkin(unit);
		fs.writeFileSync(path.join(OUT, `${name}.png`), L.encodePng(64, 64, skin.tex));
		fs.writeFileSync(path.join(OUT, `${name}_glow.png`), L.encodePng(64, 64, skin.glow));
		console.log(`wrote raid_skin/${name}.png + _glow.png`);
		if (!args.includes('--no-previews')) preview(name, unit, skin);
	}
}
main();
