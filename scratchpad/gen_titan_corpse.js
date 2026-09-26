// Builds the v0.12.36 Titan CORPSE model: the Titan's player rig cut into horizontal slices so the renderer can break the body
// apart piece by piece as it dissolves.
//   geo/titan_form_corpse.geo.json           same bones as titan_form (root > body > head/arms, root > legs) plus one cube-holding child bone per
//                                       piece: piece_<part>_<n>  (body/arms/legs: 3 slices of 4 px, head: 2 halves)
//   textures/entity/titan_form_corpse.png    a 128x128 atlas: every slice gets its own box-UV region copied from the Titan skin. The caps of
//                                       interior slices are filled with a darker "cut flesh" colour (base layer) or left clear (outer layer).
// Regenerate whenever textures/entity/titan_form.png changes:  node scratchpad/gen_titan_corpse.js
const fs = require('fs');
const path = require('path');
const png = require('./pnglib.js');
const ROOT = path.join(__dirname, '..', 'src/main/resources/assets/projecthero/');
const skin = png.read(ROOT + 'textures/entity/titan_form.png');

const AW = 128, AH = 128;
const atlas = { w: AW, h: AH, data: Buffer.alloc(AW * AH * 4) };
const getSkin = (x, y) => { const i = (y * 64 + x) * 4; return [skin.data[i], skin.data[i + 1], skin.data[i + 2], skin.data[i + 3]]; };
const put = (x, y, c) => { const i = (y * AW + x) * 4; atlas.data[i] = c[0]; atlas.data[i + 1] = c[1]; atlas.data[i + 2] = c[2]; atlas.data[i + 3] = c[3]; };

// source parts: [name, base cube, layer cube, layerInflate, pivot of the bone, cube origin, w, h, d]
const PARTS = [
	{ part: 'head', bone: 'head', origin: [-4, 24, -4], size: [8, 8, 8], uv: [0, 0], layerUv: [32, 0], layerInflate: 0.5, slice: 4 },
	{ part: 'body', bone: 'body', origin: [-4, 12, -2], size: [8, 12, 4], uv: [16, 16], layerUv: [16, 32], layerInflate: 0.25, slice: 4 },
	{ part: 'rarm', bone: 'right_arm', origin: [-8, 12, -2], size: [4, 12, 4], uv: [40, 16], layerUv: [40, 32], layerInflate: 0.25, slice: 4 },
	{ part: 'larm', bone: 'left_arm', origin: [4, 12, -2], size: [4, 12, 4], uv: [32, 48], layerUv: [48, 48], layerInflate: 0.25, slice: 4 },
	{ part: 'rleg', bone: 'right_leg', origin: [-3.9, 0, -2], size: [4, 12, 4], uv: [0, 16], layerUv: [0, 32], layerInflate: 0.25, slice: 4 },
	{ part: 'lleg', bone: 'left_leg', origin: [-0.1, 0, -2], size: [4, 12, 4], uv: [16, 48], layerUv: [0, 48], layerInflate: 0.25, slice: 4 },
];

// shelf packer for the atlas
let px = 0, py = 0, rowH = 0;
function alloc(w, h) {
	if (px + w > AW) { px = 0; py += rowH; rowH = 0; }
	if (py + h > AH) throw new Error('atlas full');
	const r = [px, py]; px += w; rowH = Math.max(rowH, h); return r;
}

/** Copy the source cube's region for the slice rows [rt, rt+hs) (rows counted from the cube top) into a fresh box-UV block. */
function cutSlice(srcUv, w, h, d, rt, hs, isTop, isBottom, isBase) {
	const bw = 2 * d + 2 * w, bh = d + hs;
	const [U, V] = alloc(bw, bh);
	const [su, sv] = srcUv;
	// sides: right (d), front (w), left (d), back (w)
	const sideCols = [[0, d], [d, w], [d + w, d], [2 * d + w, w]];
	let sum = [0, 0, 0, 0], n = 0;
	for (const [c0, cw] of sideCols) {
		for (let y = 0; y < hs; y++) for (let x = 0; x < cw; x++) {
			const c = getSkin(su + c0 + x, sv + d + rt + y);
			put(U + c0 + x, V + d + y, c);
			if (c[3] > 0) { sum[0] += c[0]; sum[1] += c[1]; sum[2] += c[2]; n++; }
		}
	}
	const cut = n ? [Math.round(sum[0] / n * 0.75), Math.round(sum[1] / n * 0.7), Math.round(sum[2] / n * 0.7), 255] : [0, 0, 0, 0];
	const cap = isBase ? cut : [0, 0, 0, 0];
	for (let y = 0; y < d; y++) for (let x = 0; x < w; x++) {
		put(U + d + x, V + y, isTop ? getSkin(su + d + x, sv + y) : cap);           // top
		put(U + d + w + x, V + y, isBottom ? getSkin(su + d + w + x, sv + y) : cap); // bottom
	}
	return [U, V];
}

const pieces = []; // {name, order key}
const bones = [
	{ name: 'root', pivot: [0, 0, 0] },
	{ name: 'body', parent: 'root', pivot: [0, 12, 0] },
	{ name: 'head', parent: 'body', pivot: [0, 24, 0] },
	{ name: 'right_arm', parent: 'body', pivot: [-5, 22, 0] },
	{ name: 'left_arm', parent: 'body', pivot: [5, 22, 0] },
	{ name: 'right_leg', parent: 'root', pivot: [-2, 12, 0] },
	{ name: 'left_leg', parent: 'root', pivot: [2, 12, 0] },
];
const pivotOf = (n) => bones.find((b) => b.name === n).pivot;
for (const P of PARTS) {
	const [ox, oy, oz] = P.origin, [w, h, d] = P.size;
	const count = Math.round(h / P.slice);
	for (let i = 0; i < count; i++) {
		const rt = i * P.slice; // rows from the top
		const y0 = oy + h - rt - P.slice;
		const isTop = i === 0, isBottom = i === count - 1;
		const baseUv = cutSlice(P.uv, w, h, d, rt, P.slice, isTop, isBottom, true);
		const layerUv = cutSlice(P.layerUv, w, h, d, rt, P.slice, isTop, isBottom, false);
		const name = `piece_${P.part}_${i}`;
		bones.push({
			name, parent: P.bone, pivot: pivotOf(P.bone),
			cubes: [
				{ origin: [ox, y0, oz], size: [w, P.slice, d], uv: baseUv },
				{ origin: [ox, y0, oz], size: [w, P.slice, d], uv: layerUv, inflate: P.layerInflate },
			],
		});
		pieces.push({ name, cx: ox + w / 2, cy: y0 + P.slice / 2 });
	}
}
const geo = {
	format_version: '1.12.0',
	'minecraft:geometry': [{
		description: { identifier: 'geometry.titan_form_corpse', texture_width: AW, texture_height: AH, visible_bounds_width: 4, visible_bounds_height: 4, visible_bounds_offset: [0, 1, 0] },
		bones,
	}],
};
fs.writeFileSync(ROOT + 'geo/titan_form_corpse.geo.json', JSON.stringify(geo, null, 1));
png.write(ROOT + 'textures/entity/titan_form_corpse.png', atlas);
console.log('pieces:', pieces.map((p) => p.name).join(' '), '\natlas used up to y=' + (py + rowH));
