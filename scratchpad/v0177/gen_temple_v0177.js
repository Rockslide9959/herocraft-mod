// v0.17.7 Ancient Temple: every resource except the boss model (see gen_ankhatep_v0177.js) --
//   * the seven player-model skins (temple guardians x2, wrapped revenants x2, cult acolytes x2, the Tomb Sentinel),
//     painted from scratch on the 64x64 player layout with a full second (overlay) layer for depth;
//   * block textures, block models, blockstates and item models for the 15 temple blocks;
//   * item textures + models (the four weights, the Bound King's Ankh), the curse's effect icon;
//   * loot tables (the five mobs, glyph carving), the structure + structure set + biome tag, the tier 4 locator tag.
// Usage (repo root): node scratchpad/v0177/gen_temple_v0177.js
const fs = require('fs');
const path = require('path');
const { encode } = require('../pngkit');

const ROOT = path.resolve(__dirname, '..', '..');
const A = path.join(ROOT, 'src/main/resources/assets/projecthero');
const D = path.join(ROOT, 'src/main/resources/data/projecthero');
const write = (p, data) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, data); };
const json = (p, o) => write(p, JSON.stringify(o, null, 2) + '\n');

let seed = 17707;
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const mix = (a, b, t) => a.map((v, i) => clamp(v + (b[i] - v) * t));
const shade = (c, f) => c.map(v => clamp(v * f));
const jit = (c, n) => { const d = (rnd() - 0.5) * n; return c.map(v => clamp(v + d)); };

function canvas(w, h) {
	const px = Buffer.alloc(w * h * 4);
	const c = {
		w, h, px,
		set(x, y, col, a = 255) { x = Math.round(x); y = Math.round(y); if (x < 0 || y < 0 || x >= w || y >= h) return; const i = (y * w + x) * 4; px[i] = clamp(col[0]); px[i + 1] = clamp(col[1]); px[i + 2] = clamp(col[2]); px[i + 3] = a; },
		get(x, y) { const i = (y * w + x) * 4; return [px[i], px[i + 1], px[i + 2], px[i + 3]]; },
		clear(x, y) { const i = (y * w + x) * 4; px[i] = px[i + 1] = px[i + 2] = px[i + 3] = 0; },
		rect(x0, y0, x1, y1, f) { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) { const col = typeof f === 'function' ? f(x, y) : f; if (col) c.set(x, y, col, col[3] === undefined ? 255 : col[3]); } },
		png() { return encode(w, h, px); },
	};
	return c;
}

// ================================================================ player skins
// faces of each part in the 64x64 layout: [u, v, w, h] per face, base layer and overlay layer
function partFaces(u, v, w, h, d) {
	// box-uv: top (u+d, v, w, d), bottom (u+d+w, v, w, d), right (u, v+d, d, h), front (u+d, v+d, w, h), left (u+d+w, v+d, d, h), back (u+2d+w, v+d, w, h)
	return {
		top: [u + d, v, w, d], bottom: [u + d + w, v, w, d], right: [u, v + d, d, h], front: [u + d, v + d, w, h],
		left: [u + d + w, v + d, d, h], back: [u + 2 * d + w, v + d, w, h],
	};
}
const PARTS = {
	head: { base: partFaces(0, 0, 8, 8, 8), over: partFaces(32, 0, 8, 8, 8) },
	body: { base: partFaces(16, 16, 8, 12, 4), over: partFaces(16, 32, 8, 12, 4) },
	rarm: { base: partFaces(40, 16, 4, 12, 4), over: partFaces(40, 32, 4, 12, 4) },
	larm: { base: partFaces(32, 48, 4, 12, 4), over: partFaces(48, 48, 4, 12, 4) },
	rleg: { base: partFaces(0, 16, 4, 12, 4), over: partFaces(0, 32, 4, 12, 4) },
	lleg: { base: partFaces(16, 48, 4, 12, 4), over: partFaces(0, 48, 4, 12, 4) },
};
// paint(part, layer, face, x, y, w, h) -> colour [r,g,b(,a)] or null (transparent)
function skin(name, paint) {
	const c = canvas(64, 64);
	for (const [part, layers] of Object.entries(PARTS)) {
		for (const layer of ['base', 'over']) {
			for (const [face, [u, v, w, h]] of Object.entries(layers[layer])) {
				for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
					const col = paint(part, layer, face, x, y, w, h);
					if (col && (col[3] === undefined || col[3] > 0)) c.set(u + x, v + y, col, col[3] === undefined ? 255 : col[3]);
				}
			}
		}
	}
	write(path.join(A, 'textures/entity/temple', name + '.png'), c.png());
}

const GOLD = [222, 176, 56], GOLD_D = [150, 104, 28], GOLD_L = [255, 230, 140];
const LAPIS = [34, 60, 150], LAPIS_D = [18, 32, 90];
const BRONZE = [168, 110, 52], BRONZE_D = [96, 58, 28];
const LINEN = [234, 228, 210], LINEN_D = [186, 176, 150];
const SKIN = [150, 98, 62], SKIN_D = [104, 64, 40];
const CARNELIAN = [176, 52, 40], TURQ = [62, 178, 168];
const BLACK = [24, 22, 26];
const WRAP = [200, 186, 152], WRAP_D = [138, 122, 92], ROT = [58, 46, 38];
const GREEN_EYE = [130, 255, 120];

const isSide = f => f === 'front' || f === 'back' || f === 'left' || f === 'right';
function gold(x, y) { return (x + y) % 5 === 0 ? GOLD_L : jit(mix(GOLD, GOLD_D, rnd() * 0.35), 10); }
function collarRow(y) { return [GOLD, LAPIS, CARNELIAN, GOLD, TURQ, GOLD][y % 6]; }
function wrapCol(x, y, tone = 0) {
	const band = Math.floor((y + x * 0.5) / 2);
	let c = mix(WRAP, WRAP_D, (band % 2 ? 0.25 : 0.0) + rnd() * 0.25 + tone);
	if ((y + x * 0.5) % 2 < 0.5) c = shade(c, 0.85); // the edge of each turn
	if (rnd() < 0.012) c = ROT; else if (rnd() < 0.05) c = shade(c, 0.78);
	return c;
}

// ---- temple guardian A: jackal-masked, bronze scale, gold collar, white kilt
function guardian(variant) {
	return (part, layer, face, x, y, w, h) => {
		if (part === 'head') {
			if (layer === 'base') {
				if (variant === 'a') {
					// the jackal mask: black lacquer, gold trim, a long muzzle shaded on the front, gold-ringed eyes
					let c = jit(BLACK, 10);
					if (face === 'front') {
						if (y === 3 && (x === 1 || x === 2 || x === 5 || x === 6)) c = GOLD;
						if (y === 4 && (x === 2 || x === 5)) c = [240, 210, 90];
						if (y >= 5 && x >= 2 && x <= 5) c = mix(BLACK, [70, 64, 70], 0.4 + (y - 5) * 0.12); // the muzzle catching light
						if (y === 7 && (x === 3 || x === 4)) c = [10, 10, 12];
						if (y === 0 || x === 0 || x === 7) c = (x + y) % 2 ? GOLD : GOLD_D;
					} else if (face === 'top') {
						c = (x === 1 || x === 6) && y < 3 ? GOLD : c;
					} else if (isSide(face) && y === 0) c = GOLD_D;
					return c;
				}
				// B: the nemes headcloth with a bare, kohl-eyed face and a short beard
				if (face === 'front') {
					let c = jit(SKIN, 8);
					if (y === 0 || y === 1) c = y % 2 ? LAPIS : GOLD;
					if (x === 0 || x === 7) c = (y % 2) ? LAPIS : GOLD;
					if (y === 3 && (x === 1 || x === 2 || x === 5 || x === 6)) c = BLACK;
					if (y === 4 && (x === 2 || x === 5)) c = [240, 236, 220];
					if (y === 4 && (x === 1 || x === 6)) c = BLACK;
					if (y === 5 && (x === 3 || x === 4)) c = SKIN_D;
					if (y === 6 && x >= 3 && x <= 4) c = [120, 50, 40];
					if (y === 7 && x >= 3 && x <= 4) c = [40, 30, 26];
					return c;
				}
				return (y % 2) ? jit(LAPIS, 10) : jit(GOLD, 10);
			}
			// overlay: jackal ears + a gold band (A) / the nemes' wings (B)
			if (variant === 'a') {
				if (face === 'front' && y <= 1 && (x === 1 || x === 6)) return GOLD;
				if (face === 'top' && (x <= 1 || x >= 6) && y <= 2) return BLACK;
				if (isSide(face) && y === 2) return GOLD;
				return null;
			}
			if (face === 'front') return (y <= 1) ? ((y % 2) ? LAPIS : GOLD) : null;
			if (face === 'left' || face === 'right' || face === 'back') return y >= 1 ? ((y % 2) ? jit(LAPIS, 8) : jit(GOLD, 8)) : null;
			if (face === 'top') return (x + y) % 2 ? GOLD : LAPIS;
			return null;
		}
		if (part === 'body') {
			if (layer === 'base') {
				if (y >= 9) return y === 9 ? GOLD : jit(LINEN, 8); // belt + kilt top
				return jit(SKIN, 10);
			}
			// overlay: the broad collar, bronze scale cuirass, belt, apron
			if (y <= 2 && isSide(face)) return collarRow(y + (face === 'front' ? 0 : 1));
			if (face === 'top') return collarRow(y);
			if (y >= 3 && y <= 8 && isSide(face)) {
				const scale = ((x + (y % 2)) % 2 === 0);
				return scale ? jit(BRONZE, 14) : jit(BRONZE_D, 10);
			}
			if (y === 9 && isSide(face)) return gold(x, y);
			if (y >= 10 && face === 'front' && x >= 3 && x <= 4) return (y % 2) ? LAPIS : GOLD; // the apron
			return null;
		}
		if (part === 'rarm' || part === 'larm') {
			if (layer === 'base') {
				if (y >= 9 && y <= 11) return y === 11 ? SKIN_D : jit(SKIN, 8);
				return jit(SKIN, 10);
			}
			if (y <= 2) return isSide(face) || face === 'top' ? jit(BRONZE, 12) : null; // pauldron
			if (y === 4) return isSide(face) ? GOLD : null; // arm ring
			if (y >= 7 && y <= 10) return isSide(face) ? (y === 7 || y === 10 ? GOLD : jit(BRONZE_D, 12)) : null; // bracer
			return null;
		}
		// legs: kilt to the knee, bare shins, sandals
		if (layer === 'base') {
			if (y <= 5) return (face === 'front' || face === 'back') ? (x % 2 ? jit(LINEN, 6) : jit(LINEN_D, 6)) : jit(LINEN, 8);
			if (y >= 10) return y === 11 ? [90, 60, 36] : (x % 2 ? [140, 96, 52] : SKIN);
			return jit(SKIN, 10);
		}
		if (y === 0 && isSide(face)) return gold(x, y);
		if (y >= 7 && y <= 9 && isSide(face)) return y === 7 ? GOLD : jit(BRONZE, 12); // greaves
		return null;
	};
}

// ---- wrapped revenant: bandages over everything, green eyes; B: older, a scarab amulet and a torn blue collar
function revenant(variant) {
	const tone = variant === 'a' ? 0 : 0.18;
	return (part, layer, face, x, y, w, h) => {
		if (layer === 'base') {
			let c = wrapCol(x, y, tone);
			if (variant === 'b') c = mix(c, [120, 96, 64], 0.2);
			if (part === 'head' && face === 'front') {
				if (y === 3 && (x === 2 || x === 5)) return GREEN_EYE;
				if (y === 3 && (x === 1 || x === 3 || x === 4 || x === 6)) return ROT;
				if (y === 4 && (x === 2 || x === 5)) return [40, 120, 50];
				if (y === 6 && x >= 2 && x <= 5) return x % 2 ? ROT : [20, 16, 14];
			}
			if (part === 'body' && variant === 'b' && face === 'front') {
				if (y <= 1) return y === 0 ? LAPIS_D : (x % 3 === 0 ? ROT : LAPIS);
				if (y >= 4 && y <= 6 && x >= 3 && x <= 4) return y === 4 ? GOLD_D : (y === 5 ? GOLD : [60, 140, 120]);
			}
			return c;
		}
		// overlay: loose strips and torn ends hanging off the arms, legs and head
		if (part === 'head') {
			if (face === 'front' && y === 1) return x % 3 === 0 ? null : jit(WRAP_D, 10);
			if (isSide(face) && y === 7 && x % 2 === 0) return jit(WRAP, 10);
			return null;
		}
		if ((part === 'rarm' || part === 'larm' || part === 'rleg' || part === 'lleg') && isSide(face)) {
			const strip = (x * 7 + (part.length * 3)) % 4 === 0;
			if (strip && y >= 6) return y >= 10 && rnd() < 0.5 ? null : jit(mix(WRAP, WRAP_D, 0.4 + tone), 10);
			if (y === 2 && x % 2 === 0) return jit(WRAP_D, 12);
			return null;
		}
		if (part === 'body' && isSide(face)) {
			if ((x === 1 || x === 6) && y >= 8) return jit(WRAP_D, 10);
			if (y === 7) return x % 2 ? jit(WRAP, 8) : null;
		}
		return null;
	};
}

// ---- cult acolyte: hood and robe (A ochre and black, B black and gold with a white skull paint), a gold torc
function acolyte(variant) {
	const ROBE = variant === 'a' ? [176, 96, 40] : [36, 30, 38];
	const ROBE_D = variant === 'a' ? [116, 56, 22] : [20, 16, 22];
	const TRIM = variant === 'a' ? BLACK : GOLD;
	const SASH = variant === 'a' ? BLACK : CARNELIAN;
	return (part, layer, face, x, y, w, h) => {
		if (part === 'head') {
			if (layer === 'base') {
				if (face === 'front') {
					if (variant === 'b') {
						let c = [226, 222, 210];
						if (y === 3 && (x === 1 || x === 2 || x === 5 || x === 6)) c = BLACK;
						if (y === 4 && (x === 2 || x === 5)) c = [200, 40, 30];
						if (y === 5 && (x === 3 || x === 4)) c = BLACK;
						if (y === 6 && x >= 2 && x <= 5) c = x % 2 ? BLACK : [210, 206, 196];
						if (y <= 1) c = jit(ROBE, 8);
						return c;
					}
					let c = mix(SKIN_D, BLACK, 0.55); // a face in the hood's shadow
					if (y === 4 && (x === 2 || x === 5)) c = [255, 200, 90];
					if (y <= 1) c = jit(ROBE, 8);
					return c;
				}
				return jit(ROBE, 10);
			}
			// the hood
			if (face === 'front') return (y <= 1 || x === 0 || x === 7) ? (y === 1 && x > 0 && x < 7 ? TRIM : jit(ROBE_D, 8)) : null;
			if (face === 'top' || face === 'left' || face === 'right' || face === 'back') return jit(mix(ROBE, ROBE_D, 0.3), 10);
			return null;
		}
		if (layer === 'base') {
			if (part === 'body') {
				if (y <= 1 && isSide(face)) return gold(x, y); // the torc
				if (y === 7 && isSide(face)) return SASH;
				if (face === 'front' && (x === 3 || x === 4)) return TRIM;
				return (x + y) % 4 === 0 ? jit(ROBE_D, 8) : jit(ROBE, 10);
			}
			if (part === 'rarm' || part === 'larm') {
				if (y >= 10) return y === 10 ? TRIM : jit(SKIN, 8);
				return jit(ROBE, 10);
			}
			if (y === 11) return [60, 40, 24];
			return x % 2 ? jit(ROBE, 8) : jit(ROBE_D, 8);
		}
		// overlay: the robe's skirt and sleeves hang loose
		if ((part === 'rleg' || part === 'lleg') && isSide(face) && y <= 9) return y === 9 ? TRIM : (x % 2 ? jit(ROBE, 8) : jit(ROBE_D, 8));
		if ((part === 'rarm' || part === 'larm') && isSide(face) && y >= 7 && y <= 9) return y === 9 ? TRIM : jit(ROBE_D, 8);
		if (part === 'body' && isSide(face) && y >= 8) return y === 11 ? TRIM : jit(mix(ROBE, ROBE_D, 0.4), 8);
		return null;
	};
}

// ---- the Tomb Sentinel: gold pharaoh mask, striped nemes, lapis-and-gold lamellar, white kilt, gold greaves
function sentinel() {
	return (part, layer, face, x, y, w, h) => {
		if (part === 'head') {
			if (layer === 'base') {
				if (face === 'front') {
					let c = gold(x, y);
					if (y === 3 && x >= 1 && x <= 6 && x !== 3 && x !== 4) c = (x === 2 || x === 5) ? [40, 200, 255] : LAPIS_D;
					if (y === 4 && (x === 1 || x === 6)) c = LAPIS_D;
					if (y === 5 && (x === 3 || x === 4)) c = GOLD_D;
					if (y === 6 && (x === 3 || x === 4)) c = CARNELIAN;
					if (y === 7 && (x === 3 || x === 4)) c = LAPIS;
					if (x === 0 || x === 7) c = (y % 2) ? LAPIS : GOLD;
					if (y === 0) c = (x % 2) ? LAPIS : GOLD;
					return c;
				}
				return (y % 2) ? jit(LAPIS, 8) : jit(GOLD, 8);
			}
			if (face === 'front') {
				if (y === 0 && (x === 3 || x === 4)) return GOLD_L; // the uraeus
				if (y === 1 && (x === 3 || x === 4)) return CARNELIAN;
				return x === 0 || x === 7 ? ((y % 2) ? LAPIS : GOLD) : null;
			}
			if (face === 'top') return (x + y) % 2 ? GOLD : LAPIS;
			return (y % 2) ? jit(LAPIS, 8) : jit(GOLD, 8);
		}
		if (part === 'body') {
			if (layer === 'base') {
				if (y >= 9) return y === 9 ? GOLD : jit(LINEN, 6);
				return (y % 2) ? jit(LAPIS, 10) : jit(GOLD_D, 10);
			}
			if (face === 'top') return collarRow(y);
			if (y <= 3 && isSide(face)) return collarRow(y);
			if (y >= 4 && y <= 8 && isSide(face)) return ((x + y) % 2) ? jit(LAPIS, 12) : gold(x, y); // lamellar
			if (y === 6 && face === 'front' && x >= 2 && x <= 5) return x === 3 || x === 4 ? [60, 160, 120] : GOLD; // scarab
			if (y >= 9 && face === 'front' && x >= 2 && x <= 5) return (x % 2) ? GOLD : LAPIS;
			return null;
		}
		if (part === 'rarm' || part === 'larm') {
			if (layer === 'base') return y >= 10 ? GOLD_D : (y <= 3 ? jit(LAPIS, 8) : jit(SKIN, 8));
			if (y <= 3) return isSide(face) || face === 'top' ? ((y % 2) ? LAPIS : gold(x, y)) : null;
			if (y >= 6 && y <= 10) return isSide(face) ? gold(x, y) : null;
			return null;
		}
		if (layer === 'base') {
			if (y <= 5) return (x % 2) ? jit(LINEN, 6) : jit(LINEN_D, 6);
			return y >= 10 ? GOLD_D : jit(SKIN, 8);
		}
		if (y === 0 && isSide(face)) return gold(x, y);
		if (y >= 6 && y <= 11 && isSide(face)) return ((y + x) % 3 === 0) ? LAPIS : gold(x, y);
		return null;
	};
}

skin('guardian_a', guardian('a'));
skin('guardian_b', guardian('b'));
skin('revenant_a', revenant('a'));
skin('revenant_b', revenant('b'));
skin('acolyte_a', acolyte('a'));
skin('acolyte_b', acolyte('b'));
skin('tomb_sentinel', sentinel());

// ================================================================ block textures (16x16)
const BT = path.join(A, 'textures/block/temple');
const tex = (name, c) => write(path.join(BT, name + '.png'), c.png());
const SAND = [218, 196, 140], SAND_D = [176, 150, 98], SAND_L = [236, 220, 172];
const STONE = [118, 112, 104], STONE_D = [78, 74, 70];

function sandstone(c, tone = 0) {
	c.rect(0, 0, 15, 15, (x, y) => {
		let col = jit(mix(SAND, SAND_D, rnd() * 0.3 + tone), 8);
		if (y === 0 || y === 15) col = shade(col, 0.88);
		if (x === 0 || x === 15) col = shade(col, 0.92);
		return col;
	});
}
function glyphShape(g) {
	// 10x10 masks for Sun, Eye, Scarab, Ankh
	return [
		['....##....', '..#....#..', '....##....', '.#.####.#.', '..######..', '#.######.#', '..######..', '.#.####.#.', '....##....', '..#....#..'],
		['..........', '...####...', '.##....##.', '#...##...#', '#..####..#', '.##.##.##.', '...####...', '....#.....', '....#..#..', '.....##...'],
		['....##....', '...#..#...', '#.######.#', '.########.', '#.######.#', '.########.', '#.######.#', '..######..', '.#..##..#.', '#........#'],
		['...####...', '..#....#..', '..#....#..', '...#..#...', '##########', '##########', '....##....', '....##....', '....##....', '....##....'],
	][g];
}
function drawGlyph(c, g, ox, oy, col, colEdge) {
	const m = glyphShape(g);
	m.forEach((row, y) => [...row].forEach((ch, x) => { if (ch === '#') { c.set(ox + x, oy + y, col); if (colEdge && y + 1 < 10 && m[y + 1][x] !== '#') c.set(ox + x, oy + y + 1, colEdge); } }));
}
// glyph carvings (wall) and glyph plates (floor, gold inlay on dark stone; lit = glowing)
for (let g = 0; g < 4; g++) {
	const c = canvas(16, 16); sandstone(c);
	c.rect(1, 1, 14, 14, (x, y) => (x === 1 || y === 1 || x === 14 || y === 14) ? SAND_D : null);
	drawGlyph(c, g, 3, 3, [120, 84, 44], SAND_L);
	tex('glyph_carving_' + g, c);
	for (const lit of [false, true]) {
		const p = canvas(16, 16);
		p.rect(0, 0, 15, 15, () => jit(lit ? [70, 60, 44] : [52, 46, 40], 10));
		p.rect(0, 0, 15, 15, (x, y) => (x === 0 || y === 0 || x === 15 || y === 15) ? GOLD_D : null);
		drawGlyph(p, g, 3, 3, lit ? [255, 236, 130] : GOLD, lit ? [255, 255, 200] : GOLD_D);
		tex('glyph_plate_' + g + (lit ? '_lit' : ''), p);
	}
}
{ // ward plate: a sandstone tile with a red-ochre eye ring
	const c = canvas(16, 16); sandstone(c, 0.1);
	for (let a = 0; a < 64; a++) { const t = a / 64 * Math.PI * 2; c.set(8 + Math.cos(t) * 5.5, 8 + Math.sin(t) * 5.5, [150, 54, 30]); }
	drawGlyph(c, 1, 3, 3, [150, 54, 30]);
	tex('ward_plate', c);
}
{ // ward gong: bronze disc in a dark frame
	const c = canvas(16, 16);
	c.rect(0, 0, 15, 15, () => jit([60, 40, 26], 10));
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const d = Math.hypot(x - 7.5, y - 7.5);
		if (d < 7) c.set(x, y, d < 1.6 ? GOLD_L : (Math.floor(d) % 2 ? jit(BRONZE, 10) : jit([200, 140, 70], 10)));
		if (d >= 6.3 && d < 7) c.set(x, y, BRONZE_D);
	}
	tex('ward_gong_front', c);
	const s = canvas(16, 16); s.rect(0, 0, 15, 15, (x, y) => jit((x + y) % 7 === 0 ? [44, 30, 20] : [70, 48, 30], 8)); tex('ward_gong_side', s);
}
function carvedStone(c) {
	c.rect(0, 0, 15, 15, (x, y) => jit(mix(SAND, SAND_D, 0.25 + rnd() * 0.2), 10));
	c.rect(0, 0, 15, 15, (x, y) => (x === 0 || y === 0 || x === 15 || y === 15) ? SAND_D : null);
}
for (const high of [false, true]) { // dart trap: a carved face; the mouth sits at the dart line
	const c = canvas(16, 16); carvedStone(c);
	const my = high ? 1 : 10;
	c.rect(4, Math.max(1, my - 6), 5, Math.max(1, my - 5), [90, 60, 36]); c.rect(10, Math.max(1, my - 6), 11, Math.max(1, my - 5), [90, 60, 36]);
	if (high) { c.rect(4, 6, 5, 7, [90, 60, 36]); c.rect(10, 6, 11, 7, [90, 60, 36]); }
	c.rect(6, my, 9, my + 2, BLACK); c.rect(7, my + 1, 8, my + 1, [8, 6, 6]);
	c.rect(5, my - 1, 10, my - 1, SAND_D); c.rect(5, my + 3, 10, my + 3, SAND_D);
	tex(high ? 'dart_trap_front_high' : 'dart_trap_front', c);
}
for (const stage of [0, 1]) { // crumbling floor: cracked flagstone (more cracks while shaking)
	const c = canvas(16, 16); sandstone(c, 0.05);
	const cracks = stage ? 9 : 4;
	for (let k = 0; k < cracks; k++) { let x = rnd() * 16, y = rnd() * 16; for (let i = 0; i < 7; i++) { c.set(x, y, [96, 76, 50]); x += rnd() * 2 - 1; y += rnd() * 2 - 0.5; } }
	tex('crumbling_floor' + (stage ? '_shaking' : ''), c);
}
{ // mirror + bronze frame
	const m = canvas(16, 16); m.rect(0, 0, 15, 15, (x, y) => mix([200, 230, 236], [120, 160, 176], ((x + y) % 9) / 9 * 0.6)); m.rect(0, 0, 15, 15, (x, y) => (x + 15 - y) % 11 === 0 ? [250, 255, 255] : null); tex('mirror', m);
	const b = canvas(16, 16); b.rect(0, 0, 15, 15, (x, y) => jit((y % 4 === 0) ? BRONZE_D : BRONZE, 12)); tex('bronze', b);
	const bk = canvas(16, 16); bk.rect(0, 0, 15, 15, () => jit([70, 44, 26], 10)); tex('mirror_back', bk);
}
{ // sun lens: a golden sun disc round a crystal
	const c = canvas(16, 16); carvedStone(c);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const d = Math.hypot(x - 7.5, y - 7.5), a = Math.atan2(y - 7.5, x - 7.5);
		if (d < 6.5 && (d < 4 || Math.floor((a + Math.PI) / (Math.PI / 6)) % 2 === 0)) c.set(x, y, d < 2.5 ? [255, 250, 210] : gold(x, y));
	}
	tex('sun_lens_front', c);
	const s = canvas(16, 16); carvedStone(s); s.rect(0, 6, 15, 7, GOLD); tex('sun_lens_side', s);
}
// seals: a tablet (gold rim, lapis field, a white icon); broken = cracked, dull
const SEAL_ICON = {
	glyph: ['..........', '.##.##.##.', '.##.##.##.', '..........', '.##.##.##.', '.##.##.##.', '..........', '.########.', '..........', '..........'],
	light: glyphShape(0),
	weight: ['....##....', '....##....', '##########', '#...##...#', '#...##...#', '#...##...#', '##..##..##', '....##....', '..######..', '..######..'],
};
for (const kind of ['glyph', 'light', 'weight']) {
	for (const broken of [false, true]) {
		const c = canvas(16, 16);
		c.rect(0, 0, 15, 15, (x, y) => (x === 0 || y === 0 || x === 15 || y === 15 || x === 1 || x === 14) ? gold(x, y) : jit(broken ? [40, 46, 70] : LAPIS, 10));
		SEAL_ICON[kind].forEach((row, y) => [...row].forEach((ch, x) => { if (ch === '#') c.set(3 + x, 3 + y, broken ? [130, 130, 120] : [250, 246, 220]); }));
		if (broken) { let x = 4, y = 0; for (let i = 0; i < 18; i++) { c.set(x, y, BLACK); x += rnd() < 0.5 ? 1 : 0; y += 1; if (y > 15) break; } }
		tex('seal_' + kind + (broken ? '_broken' : ''), c);
	}
}
{ const c = canvas(16, 16); carvedStone(c); c.rect(0, 0, 15, 2, GOLD); c.rect(0, 13, 15, 15, GOLD_D); tex('seal_side', c); }
{ // altar
	const c = canvas(16, 16); carvedStone(c); c.rect(0, 0, 15, 1, GOLD); c.rect(0, 14, 15, 15, GOLD_D); for (let x = 2; x < 14; x += 3) c.rect(x, 5, x, 10, SAND_D); tex('altar_stone', c);
	const p = canvas(16, 16); p.rect(0, 0, 15, 15, (x, y) => { const d = Math.hypot(x - 7.5, y - 7.5); return d < 7.5 ? jit(d > 6 ? BRONZE_D : BRONZE, 10) : jit(BRONZE_D, 6); }); tex('altar_pan', p);
	const w = canvas(16, 16); w.rect(0, 0, 15, 15, (x, y) => jit(((x + y) % 4) ? [140, 136, 128] : [108, 104, 98], 10)); tex('altar_weight', w);
}
{ // temple gate: carved slab with vertical grooves and a central ankh
	const c = canvas(16, 16); carvedStone(c);
	for (let x = 2; x < 16; x += 4) c.rect(x, 1, x, 14, SAND_D);
	drawGlyph(c, 3, 3, 3, [120, 84, 44], SAND_L);
	tex('temple_gate', c);
}
{ // crusher core (a cracked trigger stone) and the crusher (spiked)
	const c = canvas(16, 16); sandstone(c, 0.1); c.rect(3, 3, 12, 12, (x, y) => (x === 3 || y === 3 || x === 12 || y === 12) ? [120, 90, 56] : null); drawGlyph(c, 1, 3, 3, [150, 54, 30]); tex('crusher_core', c);
	const k = canvas(16, 16); k.rect(0, 0, 15, 15, () => jit(STONE, 12));
	for (let sy = 0; sy < 16; sy += 5) for (let sx = 0; sx < 16; sx += 5) { k.rect(sx + 1, sy + 1, sx + 3, sy + 3, [168, 160, 150]); k.set(sx + 2, sy + 2, [230, 226, 220]); k.set(sx + 3, sy + 3, STONE_D); }
	tex('temple_crusher', k);
}
{ // canopic jar: alabaster with a hieroglyph band; four lids
	const c = canvas(16, 16); c.rect(0, 0, 15, 15, (x, y) => jit(mix([236, 228, 206], [200, 188, 160], ((x * 3 + y) % 7) / 7 * 0.5), 6));
	c.rect(0, 5, 15, 9, (x, y) => (y === 5 || y === 9) ? GOLD_D : ((x + y) % 3 === 0 ? LAPIS : [236, 228, 206]));
	tex('canopic_jar', c);
	const LIDS = [[34, 32, 36], [150, 100, 60], [196, 170, 120], [222, 176, 56]];
	LIDS.forEach((col, i) => {
		const l = canvas(16, 16); l.rect(0, 0, 15, 15, () => jit(col, 12));
		l.set(4, 6, i === 0 ? GOLD : BLACK); l.set(11, 6, i === 0 ? GOLD : BLACK); l.rect(6, 10, 9, 11, shade(col, 0.6));
		tex('canopic_lid_' + i, l);
	});
}
{ // the treasury's sealed vault: a gold door with an ankh
	const c = canvas(16, 16); c.rect(0, 0, 15, 15, (x, y) => (x <= 1 || x >= 14 || y <= 1 || y >= 14) ? gold(x, y) : jit(LAPIS_D, 8)); drawGlyph(c, 3, 3, 3, GOLD_L, GOLD_D); tex('vault_seal_front', c);
	const s = canvas(16, 16); s.rect(0, 0, 15, 15, (x, y) => gold(x, y)); tex('vault_seal_side', s);
}

// ================================================================ models + blockstates
const BM = path.join(A, 'models/block/temple');
const model = (name, o) => json(path.join(BM, name + '.json'), o);
const t = n => 'projecthero:block/temple/' + n;
const ref = n => 'projecthero:block/temple/' + n;
const bs = (name, o) => json(path.join(A, 'blockstates', name + '.json'), o);
const itemModel = (name, parent) => json(path.join(A, 'models/item', name + '.json'), { parent });
const H = ['north', 'east', 'south', 'west'];
const YROT = { north: 0, east: 90, south: 180, west: 270 };

// orientable blocks
model('ward_gong', { parent: 'minecraft:block/orientable', textures: { front: t('ward_gong_front'), side: t('ward_gong_side'), top: t('ward_gong_side') } });
bs('ward_gong', { variants: Object.fromEntries(H.flatMap(f => [false, true].map(p => [`facing=${f},powered=${p}`, { model: ref('ward_gong'), y: YROT[f] }]))) });
itemModel('ward_gong', ref('ward_gong'));
for (const high of [false, true]) model('dart_trap' + (high ? '_high' : ''), { parent: 'minecraft:block/orientable', textures: { front: t(high ? 'dart_trap_front_high' : 'dart_trap_front'), side: 'minecraft:block/cut_sandstone', top: 'minecraft:block/sandstone_top' } });
bs('dart_trap', { variants: Object.fromEntries(H.flatMap(f => [false, true].map(h => [`facing=${f},high=${h}`, { model: ref('dart_trap' + (h ? '_high' : '')), y: YROT[f] }]))) });
itemModel('dart_trap', ref('dart_trap'));
model('sun_lens', { parent: 'minecraft:block/orientable', textures: { front: t('sun_lens_front'), side: t('sun_lens_side'), top: t('sun_lens_side') } });
bs('sun_lens', { variants: Object.fromEntries(H.map(f => [`facing=${f}`, { model: ref('sun_lens'), y: YROT[f] }])) });
itemModel('sun_lens', ref('sun_lens'));
model('temple_vault_seal', { parent: 'minecraft:block/orientable', textures: { front: t('vault_seal_front'), side: t('vault_seal_side'), top: t('vault_seal_side') } });
bs('temple_vault_seal', { variants: Object.fromEntries(H.map(f => [`facing=${f}`, { model: ref('temple_vault_seal'), y: YROT[f] }])) });
itemModel('temple_vault_seal', ref('temple_vault_seal'));

// thin plates
const plate = (h, texName) => ({ parent: 'minecraft:block/block', textures: { particle: t(texName), top: t(texName), side: t(texName) },
	elements: [{ from: [h === 1 ? 1 : 0, 0, h === 1 ? 1 : 0], to: [h === 1 ? 15 : 16, h, h === 1 ? 15 : 16], faces: { up: { texture: '#top' }, down: { texture: '#top' }, north: { texture: '#side', uv: [0, 15, 16, 16] }, south: { texture: '#side', uv: [0, 15, 16, 16] }, east: { texture: '#side', uv: [0, 15, 16, 16] }, west: { texture: '#side', uv: [0, 15, 16, 16] } } }] });
model('ward_plate', plate(1, 'ward_plate'));
model('ward_plate_down', { ...plate(1, 'ward_plate'), elements: [{ ...plate(1, 'ward_plate').elements[0], to: [15, 0.5, 15] }] });
bs('ward_plate', { variants: { 'powered=false': { model: ref('ward_plate') }, 'powered=true': { model: ref('ward_plate_down') } } });
itemModel('ward_plate', ref('ward_plate'));
const gpv = {};
for (let g = 0; g < 4; g++) for (const lit of [false, true]) {
	model(`glyph_plate_${g}${lit ? '_lit' : ''}`, plate(1, `glyph_plate_${g}${lit ? '_lit' : ''}`));
	for (const pr of [false, true]) gpv[`glyph=${g},lit=${lit},pressed=${pr}`] = { model: ref(`glyph_plate_${g}${lit ? '_lit' : ''}`) };
}
bs('glyph_plate', { variants: gpv });
itemModel('glyph_plate', ref('glyph_plate_0'));
const gcv = {};
for (let g = 0; g < 4; g++) { model('glyph_carving_' + g, { parent: 'minecraft:block/cube_all', textures: { all: t('glyph_carving_' + g) } }); gcv['glyph=' + g] = { model: ref('glyph_carving_' + g) }; }
bs('glyph_carving', { variants: gcv });
itemModel('glyph_carving', ref('glyph_carving_0'));

// simple cubes
for (const [name, texName] of [['temple_gate', 'temple_gate'], ['crusher_core', 'crusher_core'], ['temple_crusher', 'temple_crusher']]) {
	model(name, { parent: 'minecraft:block/cube_all', textures: { all: t(texName) } });
	bs(name, name === 'temple_gate' ? { variants: Object.fromEntries(['trials', 'crypt', 'vault'].map(g => ['gate=' + g, { model: ref(name) }])) } : { variants: { '': { model: ref(name) } } });
	itemModel(name, ref(name));
}
model('crumbling_floor', { parent: 'minecraft:block/cube_all', textures: { all: t('crumbling_floor') } });
model('crumbling_floor_shaking', { parent: 'minecraft:block/cube_all', textures: { all: t('crumbling_floor_shaking') } });
bs('crumbling_floor', { variants: { 'stage=0': { model: ref('crumbling_floor') }, 'stage=1': { model: ref('crumbling_floor_shaking') }, 'stage=2': { model: ref('crumbling_floor') } } });
itemModel('crumbling_floor', ref('crumbling_floor'));

// the mirror: a pedestal + a frame + a mirror plane at 45 degrees (face = north-east diagonal for facing=north)
model('temple_mirror', {
	parent: 'minecraft:block/block', textures: { particle: t('bronze'), bronze: t('bronze'), mirror: t('mirror'), back: t('mirror_back'), stone: 'minecraft:block/chiseled_sandstone' },
	elements: [
		{ from: [3, 0, 3], to: [13, 2, 13], faces: { up: { texture: '#stone' }, down: { texture: '#stone' }, north: { texture: '#stone' }, south: { texture: '#stone' }, east: { texture: '#stone' }, west: { texture: '#stone' } } },
		{ from: [7, 2, 7], to: [9, 4, 9], faces: { up: { texture: '#bronze' }, north: { texture: '#bronze' }, south: { texture: '#bronze' }, east: { texture: '#bronze' }, west: { texture: '#bronze' } } },
		{ from: [0, 4, 7.25], to: [16, 15, 8.75], rotation: { origin: [8, 8, 8], axis: 'y', angle: 45, rescale: true },
			faces: { north: { texture: '#mirror' }, south: { texture: '#back' }, up: { texture: '#bronze' }, down: { texture: '#bronze' }, east: { texture: '#bronze' }, west: { texture: '#bronze' } } },
		{ from: [0, 15, 7], to: [16, 16, 9], rotation: { origin: [8, 8, 8], axis: 'y', angle: 45, rescale: true },
			faces: { north: { texture: '#bronze' }, south: { texture: '#bronze' }, up: { texture: '#bronze' }, down: { texture: '#bronze' }, east: { texture: '#bronze' }, west: { texture: '#bronze' } } },
	],
});
bs('temple_mirror', { variants: Object.fromEntries(H.map(f => [`facing=${f}`, { model: ref('temple_mirror'), y: YROT[f] }])) });
itemModel('temple_mirror', ref('temple_mirror'));

// seals: a tablet on a pedestal (12 wide, full height), the icon on all four sides
const sealv = {};
for (const kind of ['glyph', 'light', 'weight']) for (const broken of [false, true]) {
	const n = `temple_seal_${kind}${broken ? '_broken' : ''}`;
	model(n, {
		parent: 'minecraft:block/block', textures: { particle: t('seal_side'), face: t(`seal_${kind}${broken ? '_broken' : ''}`), side: t('seal_side') },
		elements: [
			{ from: [2, 0, 2], to: [14, 3, 14], faces: { up: { texture: '#side' }, down: { texture: '#side' }, north: { texture: '#side' }, south: { texture: '#side' }, east: { texture: '#side' }, west: { texture: '#side' } } },
			{ from: [3, 3, 3], to: [13, 16, 13], faces: { up: { texture: '#side' }, north: { texture: '#face' }, south: { texture: '#face' }, east: { texture: '#face' }, west: { texture: '#face' } } },
		],
	});
	sealv[`broken=${broken},kind=${kind}`] = { model: ref(n) };
}
bs('temple_seal', { variants: sealv });
itemModel('temple_seal', ref('temple_seal_light'));

// the weight altar: a stone column, a bronze beam, two pans (weights on them as the load grows)
const altarEls = load => {
	const els = [
		{ from: [3, 0, 3], to: [13, 8, 13], faces: { up: { texture: '#stone' }, down: { texture: '#stone' }, north: { texture: '#stone' }, south: { texture: '#stone' }, east: { texture: '#stone' }, west: { texture: '#stone' } } },
		{ from: [7, 8, 7], to: [9, 13, 9], faces: { north: { texture: '#bronze' }, south: { texture: '#bronze' }, east: { texture: '#bronze' }, west: { texture: '#bronze' }, up: { texture: '#bronze' } } },
		{ from: [0, 13, 7.5], to: [16, 14, 8.5], faces: { north: { texture: '#bronze' }, south: { texture: '#bronze' }, east: { texture: '#bronze' }, west: { texture: '#bronze' }, up: { texture: '#bronze' }, down: { texture: '#bronze' } } },
		{ from: [0, 9, 5], to: [5, 10, 11], faces: { up: { texture: '#pan' }, down: { texture: '#pan' }, north: { texture: '#bronze' }, south: { texture: '#bronze' }, east: { texture: '#bronze' }, west: { texture: '#bronze' } } },
		{ from: [11, 9, 5], to: [16, 10, 11], faces: { up: { texture: '#pan' }, down: { texture: '#pan' }, north: { texture: '#bronze' }, south: { texture: '#bronze' }, east: { texture: '#bronze' }, west: { texture: '#bronze' } } },
	];
	if (load >= 1) els.push({ from: [1, 10, 6], to: [4, 12, 9], faces: { up: { texture: '#weight' }, north: { texture: '#weight' }, south: { texture: '#weight' }, east: { texture: '#weight' }, west: { texture: '#weight' } } });
	if (load >= 2) els.push({ from: [12, 10, 7], to: [15, 12, 10], faces: { up: { texture: '#weight' }, north: { texture: '#weight' }, south: { texture: '#weight' }, east: { texture: '#weight' }, west: { texture: '#weight' } } });
	return els;
};
const altarv = {};
for (let load = 0; load <= 2; load++) for (const solved of [false, true]) {
	const n = `weight_altar_${load}${solved ? '_solved' : ''}`;
	model(n, { parent: 'minecraft:block/block', textures: { particle: t('altar_stone'), stone: t(solved ? 'glyph_carving_0' : 'altar_stone'), bronze: t('bronze'), pan: t('altar_pan'), weight: t('altar_weight') }, elements: altarEls(load) });
	altarv[`load=${load},solved=${solved}`] = { model: ref(n) };
}
bs('weight_altar', { variants: altarv });
itemModel('weight_altar', ref('weight_altar_0'));

// canopic jars: a jar and a lid in four heads
const jarv = {};
for (let i = 0; i < 4; i++) {
	model('canopic_jar_' + i, {
		parent: 'minecraft:block/block', textures: { particle: t('canopic_jar'), jar: t('canopic_jar'), lid: t('canopic_lid_' + i) },
		elements: [
			{ from: [5, 0, 5], to: [11, 1, 11], faces: { up: { texture: '#jar' }, down: { texture: '#jar' }, north: { texture: '#jar' }, south: { texture: '#jar' }, east: { texture: '#jar' }, west: { texture: '#jar' } } },
			{ from: [4.5, 1, 4.5], to: [11.5, 9, 11.5], faces: { down: { texture: '#jar' }, north: { texture: '#jar' }, south: { texture: '#jar' }, east: { texture: '#jar' }, west: { texture: '#jar' } } },
			{ from: [5.5, 9, 5.5], to: [10.5, 13, 10.5], faces: { up: { texture: '#lid' }, north: { texture: '#lid' }, south: { texture: '#lid' }, east: { texture: '#lid' }, west: { texture: '#lid' } } },
			{ from: [7, 11, 4.5], to: [9, 12.5, 5.5], faces: { up: { texture: '#lid' }, north: { texture: '#lid' }, east: { texture: '#lid' }, west: { texture: '#lid' }, down: { texture: '#lid' } } },
		],
	});
	jarv['head=' + i] = { model: ref('canopic_jar_' + i) };
}
bs('canopic_jar', { variants: jarv });
itemModel('canopic_jar', ref('canopic_jar_0'));

// ================================================================ items
const IT = path.join(A, 'textures/item/temple');
const itex = (name, c) => write(path.join(IT, name + '.png'), c.png());
const itemFlat = (name, texName) => json(path.join(A, 'models/item', name + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/temple/' + texName } });
['weight_feather', 'weight_ibis', 'weight_jackal', 'weight_lion'].forEach((name, i) => {
	const c = canvas(16, 16);
	const w = 6 + i * 2, h = 5 + i, x0 = 8 - w / 2, y0 = 14 - h;
	c.rect(x0, y0, x0 + w - 1, 14, (x, y) => (y === y0 ? [180, 176, 166] : jit(((x + y) % 3) ? [140, 136, 128] : [116, 112, 104], 8)));
	c.rect(x0 + 1, y0 - 2, x0 + w - 2, y0 - 1, (x, y) => [x === x0 + 1 || x === x0 + w - 2 ? 90 : 110, 104, 96]);
	c.rect(7, y0 - 3, 8, y0 - 3, GOLD);
	for (let k = 0; k <= i; k++) c.set(x0 + 1 + k * 2, y0 + Math.floor(h / 2), GOLD);
	c.rect(x0, 14, x0 + w - 1, 14, [70, 66, 60]);
	itex(name, c); itemFlat(name, name);
});
{ // the ankh: gold with a green gem, a lapis wrap round the stem
	const c = canvas(16, 16);
	const m = ['......####......', '.....##..##.....', '....##....##....', '....##....##....', '.....##..##.....', '......####......', '.##############.', '.##############.', '......####......', '......#LL#......', '......####......', '......#LL#......', '......####......', '......####......', '......####......', '......####......'];
	m.forEach((row, y) => [...row].forEach((ch, x) => { if (ch === '#') c.set(x, y, (x + y) % 4 === 0 ? GOLD_L : (x > 7 ? GOLD_D : GOLD)); if (ch === 'L') c.set(x, y, LAPIS); }));
	c.set(7, 7, [120, 255, 140]); c.set(8, 7, [70, 200, 100]); c.set(7, 6, [160, 255, 180]); c.set(8, 6, [120, 255, 140]);
	itex('bound_kings_ankh', c); itemFlat('bound_kings_ankh', 'bound_kings_ankh');
}
{ // the curse's icon (18x18)
	const c = canvas(18, 18);
	const m = ['.......####.......', '......##..##......', '......##..##......', '.......####.......', '..##############..', '..##############..', '.......####.......', '.......####.......', '.......####.......', '.......####.......', '.......####.......', '.......####.......'];
	m.forEach((row, y) => [...row].forEach((ch, x) => { if (ch === '#') c.set(x, y + 3, (x + y) % 3 ? [150, 200, 90] : [90, 150, 60]); }));
	for (let y = 4; y < 16; y++) c.set(9 + ((y % 3) - 1), y, [30, 40, 20]);
	write(path.join(A, 'textures/mob_effect/bound_curse.png'), c.png());
}

// ================================================================ data: loot, structure, tags
const lootEntity = (name, pools) => json(path.join(D, 'loot_table/entities', name + '.json'), { type: 'minecraft:entity', pools });
const item = (name, min, max, extra = {}) => ({ type: 'minecraft:item', name, ...extra, functions: [{ function: 'minecraft:set_count', count: { type: 'minecraft:uniform', min, max } }] });
const pool = (entries, cond) => ({ rolls: 1, entries, ...(cond ? { conditions: cond } : {}) });
const chance = c => [{ condition: 'minecraft:random_chance', chance: c }];
const killed = [{ condition: 'minecraft:killed_by_player' }];
lootEntity('temple_guardian', [pool([item('minecraft:gold_nugget', 1, 4)], killed), pool([item('projecthero:salvaged_tech', 1, 2)], chance(0.3))]);
lootEntity('wrapped_revenant', [pool([item('minecraft:string', 1, 3), item('minecraft:rotten_flesh', 1, 2)], killed), pool([item('minecraft:gold_nugget', 1, 3)], chance(0.4))]);
lootEntity('cult_acolyte', [pool([item('minecraft:gold_nugget', 2, 5)], killed), pool([item('minecraft:arrow', 2, 5)], chance(0.5))]);
lootEntity('tomb_sentinel', [
	pool([item('projecthero:vibranium_shard', 2, 4)]), pool([item('minecraft:experience_bottle', 3, 6)]), pool([item('minecraft:gold_ingot', 4, 9)]),
	pool([item('projecthero:hero_alloy_ingot', 2, 4)]),
]);
lootEntity('ankhatep', [
	pool([item('projecthero:celestial_core', 2, 3)]), pool([item('projecthero:vibranium_shard', 3, 5)]), pool([item('minecraft:experience_bottle', 6, 10)]),
	pool([item('minecraft:golden_apple', 1, 2)]), pool([item('minecraft:gold_block', 2, 4)]),
	pool([{ type: 'minecraft:item', name: 'minecraft:enchanted_golden_apple' }], chance(0.25)),
]);
json(path.join(D, 'loot_table/blocks/glyph_carving.json'), { type: 'minecraft:block', pools: [{ rolls: 1, entries: [{ type: 'minecraft:item', name: 'projecthero:glyph_carving' }], conditions: [{ condition: 'minecraft:survives_explosion' }] }] });

json(path.join(D, 'worldgen/structure/ancient_temple.json'), { type: 'projecthero:ancient_temple', biomes: '#projecthero:ancient_temple_biomes', step: 'top_layer_modification', terrain_adaptation: 'none', spawn_overrides: {} });
json(path.join(D, 'worldgen/structure_set/ancient_temple.json'), {
	structures: [{ structure: 'projecthero:ancient_temple', weight: 1 }],
	placement: { type: 'minecraft:random_spread', salt: 1705301, spacing: 240, separation: 90, exclusion_zone: { other_set: 'minecraft:villages', chunk_count: 4 } },
});
json(path.join(D, 'tags/worldgen/biome/ancient_temple_biomes.json'), { replace: false, values: ['minecraft:desert', 'minecraft:jungle', 'minecraft:sparse_jungle', 'minecraft:bamboo_jungle'] });
{ // the tier 4 locator tag: add the temple (keeps whatever else is listed)
	const p = path.join(D, 'tags/worldgen/structure/locator/tier4.json');
	const tag = JSON.parse(fs.readFileSync(p, 'utf8'));
	if (!tag.values.includes('projecthero:ancient_temple')) tag.values.push('projecthero:ancient_temple');
	json(p, tag);
}
console.log('temple assets written');
