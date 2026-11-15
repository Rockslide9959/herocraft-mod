// v0.17.5 Omnitrix function mode: the textures.
//   entity/omnitrix/code_glyphs.png  256x32: 16 cells of 16x32 -- seven-segment digits 0-9 (cells 0-9), the slot cursor bar (10),
//     a solid block (11, the readout's backing band), a dash (12). White on transparent: drawn as cutout with a vertex tint (the
//     tint is the colour; first-person hands lose additive / translucent layers drawn this late).
//   item/omnitrix_data_fragment.png  16x16: a glowing green Galvan data-crystal shard with a lit circuit trace.
// Usage: node scratchpad/gen_omnitrix_function_v0175.js
'use strict';
const path = require('path');
const PNG = require('./pnglib.js');
const RES = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures');

// ---------------------------------------------------------------- the glyph atlas
{
	const CW = 16, CH = 32, N = 16, W = CW * N, H = CH;
	const px = Buffer.alloc(W * H * 4);
	// transparent background: drawn as cutout with a vertex tint (RenderType.entityCutoutNoCull), so only the strokes show
	const rect = (cell, x0, y0, x1, y1, v = 255) => {
		for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
			const i = (y * W + cell * CW + x) * 4;
			px[i] = px[i + 1] = px[i + 2] = Math.max(px[i], v);
			px[i + 3] = 255;
		}
	};
	// segments (x0, y0, x1, y1), 3 px thick, small gaps at the joints
	const SEG = {
		a: [4, 3, 12, 6], b: [11, 6, 14, 15], c: [11, 17, 14, 26], d: [4, 26, 12, 29],
		e: [2, 17, 5, 26], f: [2, 6, 5, 15], g: [4, 14, 12, 17],
	};
	const DIGITS = ['abcdef', 'bc', 'abged', 'abgcd', 'fgbc', 'afgcd', 'afgedc', 'abc', 'abcdefg', 'abcdfg'];
	DIGITS.forEach((segs, cell) => {
		for (const s of segs) {
			const [x0, y0, x1, y1] = SEG[s];
			rect(cell, x0, y0, x1, y1);
			// a dimmer 1-px rim so the strokes read softly lit when magnified
			rect(cell, x0 - 1, y0, x0, y1, 70); rect(cell, x1, y0, x1 + 1, y1, 70);
			rect(cell, x0, y0 - 1, x1, y0, 70); rect(cell, x0, y1, x1, y1 + 1, 70);
		}
	});
	rect(10, 2, 29, 14, 31); // the cursor bar
	rect(11, 0, 0, CW, CH); // solid
	rect(12, 4, 14, 12, 17); // dash
	PNG.write(path.join(RES, 'entity', 'omnitrix', 'code_glyphs.png'), W, H, px);
}

// ---------------------------------------------------------------- the Data Fragment item
{
	const W = 16, H = 16;
	const px = Buffer.alloc(W * H * 4);
	const set = (x, y, c, a = 255) => {
		if (x < 0 || y < 0 || x >= W || y >= H) return;
		const i = (y * W + x) * 4;
		px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = a;
	};
	// a tall shard leaning right: its outline polygon, filled row by row
	const poly = [[7, 1], [11, 4], [12, 9], [9, 14], [5, 14], [4, 9], [5, 4]];
	const inside = (x, y) => {
		let c = false;
		for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
			const [xi, yi] = poly[i], [xj, yj] = poly[j];
			if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c;
		}
		return c;
	};
	const DARK = [16, 70, 22], MID = [44, 168, 52], LIGHT = [128, 238, 104], HOT = [226, 255, 210], EDGE = [10, 34, 12];
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		const cx = x + 0.5, cy = y + 0.5;
		if (!inside(cx, cy)) continue;
		// facets: left face darker, right face lit, a bright ridge down the middle
		const ridge = 7.5 + (cy - 1) * 0.12;
		let c = cx < ridge - 0.5 ? DARK : cx < ridge + 0.6 ? LIGHT : MID;
		if (cy < 5 && cx >= ridge - 0.5) c = LIGHT;
		set(x, y, c);
	}
	// outline
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		const i = (y * W + x) * 4;
		if (px[i + 3]) continue;
		const n = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => {
			const xx = x + dx, yy = y + dy;
			return xx >= 0 && yy >= 0 && xx < W && yy < H && px[(yy * W + xx) * 4 + 3] && !(px[(yy * W + xx) * 4] === EDGE[0]);
		});
		if (n) set(x, y, EDGE);
	}
	// the lit circuit trace and the Omnitrix hourglass glint
	for (const [x, y] of [[6, 8], [6, 9], [7, 9], [8, 9], [8, 10], [8, 11], [9, 7], [10, 7]]) set(x, y, HOT);
	set(7, 3, HOT); set(8, 4, HOT);
	PNG.write(path.join(RES, 'item', 'omnitrix_data_fragment.png'), W, H, px);
}
console.log('wrote code_glyphs.png and omnitrix_data_fragment.png');
