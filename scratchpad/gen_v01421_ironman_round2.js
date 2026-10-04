// v0.14.21 Iron Man "round two" assets. Idempotent; re-run after repainting any mark texture.
//   node scratchpad/gen_v01421_ironman_round2.js
//
// 1. textures/armor/<mark>_glowmask.png for every mark (64x64, read by client IronManSuitGlowLayer, drawn additively
//    + fullbright, so every transparent texel is written as rgb 0). Source = the mark's own cyan texels (eye slits, the
//    arc reactor, the soles, Mark V's palms) + white texels inside a cyan cluster (Mark 2's eye cores), plus the palm
//    repulsor (centre 2x2 of the base-arm "down" rect) for marks 2..VII. Mark 1 has no cyan: only its white reactor
//    dot glows, softly (55%).
// 2. Mark V blades: a blade swatch painted into the unused top-left 8x8 of textures/armor/mark_v.png and two bones,
//    right_blade / left_blade (housing + blade + tip), added to geo/mark_v.geo.json under the arm bones. The pivot is
//    the top of the blade so SuperheroArmorRenderer can scale the bone along Y to extend / retract it.
// 3. textures/misc/repulsor_palm_glow.png -- 16x16 radial cyan glow used for the first-person palm repulsors and the
//    Repulsor Boots' lit soles.
// 4. textures/armor/repulsor_boots.png -- 32x32 material swatches for the worn Repulsor Boots (RepulsorBootsLayer).
const fs = require('fs');
const path = require('path');
const P = require('./pnglib.js');

const ROOT = path.join(__dirname, '../src/main/resources/assets/projecthero');
const ARMOR = path.join(ROOT, 'textures/armor');
const MARKS = ['mark_1', 'mark_2', 'mark_iii', 'mark_4', 'mark_v', 'mark_6', 'mark_vii'];

const px = (img, x, y) => { const o = (y * img.w + x) * 4; return [img.data[o], img.data[o + 1], img.data[o + 2], img.data[o + 3]]; };
const put = (img, x, y, c) => { const o = (y * img.w + x) * 4; img.data[o] = c[0]; img.data[o + 1] = c[1]; img.data[o + 2] = c[2]; img.data[o + 3] = c.length > 3 ? c[3] : 255; };
const blank = (w, h) => ({ w, h, data: Buffer.alloc(w * h * 4) });
const isCyan = ([r, g, b, a]) => a > 0 && b > 170 && g > 150 && b - r > 50;
const isWhite = ([r, g, b, a]) => a > 0 && r > 200 && g > 200 && b > 200;

// ---------------------------------------------------------------- 1. glowmasks
const PALM = { right: [[49, 17], [50, 17], [49, 18], [50, 18]], left: [[41, 49], [42, 49], [41, 50], [42, 50]] };
const PALM_COLOUR = [0x5f, 0xf6, 0xff];
for (const m of MARKS) {
	const src = P.read(path.join(ARMOR, m + '.png'));
	const out = blank(64, 64);
	let n = 0;
	for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
		const c = px(src, x, y);
		if (m === 'mark_1') {
			// the crude reactor: a dim white dot in the arc-reactor face (cols 22-25, rows 24-27)
			if (x >= 22 && x <= 25 && y >= 24 && y <= 27 && isWhite(c)) {
				put(out, x, y, [Math.round(c[0] * 0.55), Math.round(c[1] * 0.6), Math.round(c[2] * 0.62), 255]); n++;
			}
			continue;
		}
		let glow = isCyan(c);
		if (!glow && isWhite(c)) {
			for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
				const xx = x + dx, yy = y + dy;
				if (xx >= 0 && yy >= 0 && xx < 64 && yy < 64 && isCyan(px(src, xx, yy))) glow = true;
			}
		}
		if (glow) { put(out, x, y, [c[0], c[1], c[2], 255]); n++; }
	}
	if (m !== 'mark_1') {
		for (const [x, y] of [...PALM.right, ...PALM.left]) {
			if (px(out, x, y)[3] === 0) { put(out, x, y, [...PALM_COLOUR, 255]); n++; }
		}
	}
	P.write(path.join(ARMOR, m + '_glowmask.png'), out);
	console.log(m + '_glowmask.png', n, 'texels');
}

// ---------------------------------------------------------------- 2. Mark V blades
const BLADE = {
	// blade sides (cols 0-4, rows 0-7): symmetric across the blade's depth so either face orientation reads right
	side: [[0xf4, 0xf8, 0xfb], [0xc4, 0xcd, 0xd6], [0x9e, 0xa9, 0xb5], [0xc4, 0xcd, 0xd6], [0xf4, 0xf8, 0xfb]],
	edge: [0xff, 0xff, 0xff],          // col 5: the cutting edges / thin faces
	red: [0xb3, 0x1b, 0x1e], redDark: [0x7c, 0x10, 0x14], gold: [0xd9, 0xa4, 0x3a], // cols 6-7: housing
};
{
	const f = path.join(ARMOR, 'mark_v.png');
	const img = P.read(f);
	for (let y = 0; y < 8; y++) {
		const shade = 1 - y * 0.035; // a touch darker toward the tip
		for (let x = 0; x < 5; x++) put(img, x, y, BLADE.side[x].map(v => Math.round(v * shade)));
		put(img, 5, y, BLADE.edge);
		put(img, 6, y, y === 3 ? BLADE.gold : BLADE.red);
		put(img, 7, y, y === 3 ? BLADE.gold : BLADE.redDark);
	}
	P.write(f, img);
	console.log('mark_v.png blade swatch painted');
}
{
	const f = path.join(ROOT, 'geo/mark_v.geo.json');
	const geo = JSON.parse(fs.readFileSync(f, 'utf8'));
	const g = geo['minecraft:geometry'][0];
	g.bones = g.bones.filter(b => b.name !== 'right_blade' && b.name !== 'left_blade');
	const face = (u, v, w, h) => ({ uv: [u, v], uv_size: [w, h] });
	const bladeUv = { north: face(5, 0, 1, 8), south: face(5, 0, 1, 8), east: face(0, 0, 5, 8), west: face(0, 0, 5, 8), up: face(5, 0, 1, 1), down: face(5, 7, 1, 1) };
	const housingUv = { north: face(6, 0, 2, 8), south: face(6, 0, 2, 8), east: face(6, 0, 2, 8), west: face(6, 0, 2, 8), up: face(6, 0, 2, 1), down: face(6, 7, 2, 1) };
	// sx = -1 right arm (outer side is -x), +1 left arm. Bedrock y-up; the fist ends at y 12.
	const blade = (name, parent, sx) => {
		const outer = (a, b) => (sx < 0 ? [-b, -a] : [a, b]); // x range on the outer side of the gauntlet
		const [hx0, hx1] = outer(8.45, 9.25), [bx0, bx1] = outer(8.7, 9.05), [tx0, tx1] = outer(8.72, 9.03);
		return {
			name, parent, pivot: [sx * 8.85, 16.5, 0],
			cubes: [
				{ origin: [hx0, 13.2, -1.15], size: [hx1 - hx0, 3.3, 2.3], uv: housingUv },          // red housing on the gauntlet
				{ origin: [bx0, 5.6, -0.95], size: [bx1 - bx0, 10.4, 1.9], uv: bladeUv },            // the blade
				{ origin: [tx0, 3.9, -0.45], size: [tx1 - tx0, 1.7, 0.9], uv: bladeUv },             // tapered tip
			],
		};
	};
	const insertAfter = (boneName, bone) => { const i = g.bones.findIndex(b => b.name === boneName); g.bones.splice(i + 1, 0, bone); };
	insertAfter('right_gauntlet', blade('right_blade', 'armorRightArm', -1));
	insertAfter('left_gauntlet', blade('left_blade', 'armorLeftArm', 1));
	fs.writeFileSync(f, JSON.stringify(geo, null, 2) + '\n');
	console.log('mark_v.geo.json blade bones written');
}

// ---------------------------------------------------------------- 3. palm glow
{
	const img = blank(16, 16);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8) / 8;
		if (d >= 1) continue;
		const core = Math.max(0, 1 - d / 0.35);
		const r = Math.round(0x5f + (255 - 0x5f) * core), g = Math.round(0xe6 + (255 - 0xe6) * core);
		const a = Math.round(255 * Math.min(1, Math.pow(1 - d, 1.4) * 1.6));
		put(img, x, y, [r, g, 255, a]);
	}
	P.write(path.join(ROOT, 'textures/misc/repulsor_palm_glow.png'), img);
	console.log('repulsor_palm_glow.png');
}

// ---------------------------------------------------------------- 4. Repulsor Boots swatches (8x8 each)
{
	const img = blank(32, 32);
	const sw = (ox, oy, base, rim) => {
		for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
			const edge = x === 0 || y === 0 || x === 7 || y === 7;
			const k = 1 - y * 0.03 + ((x * 7 + y * 3) % 5 === 0 ? 0.05 : 0);
			put(img, ox + x, oy + y, (edge ? rim : base).map(v => Math.max(0, Math.min(255, Math.round(v * k)))));
		}
	};
	sw(0, 0, [0xc9, 0xd0, 0xd6], [0x8f, 0x98, 0xa2]);   // silver shell
	sw(8, 0, [0xb0, 0x1c, 0x20], [0x78, 0x10, 0x14]);   // red trim
	sw(16, 0, [0x3a, 0x3f, 0x46], [0x22, 0x26, 0x2b]);  // dark thruster / sole
	sw(24, 0, [0xd8, 0xa8, 0x3c], [0x9c, 0x72, 0x22]);  // gold
	// row 8: sole nozzle ring (dark with a cyan ring) for the sole face
	for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
		const d = Math.hypot(x + 0.5 - 4, y + 0.5 - 4);
		put(img, x, 8 + y, d > 2.2 && d < 3.4 ? [0x4f, 0xd8, 0xf0] : d <= 2.2 ? [0x1a, 0x22, 0x2a] : [0x3a, 0x3f, 0x46]);
	}
	P.write(path.join(ARMOR, 'repulsor_boots.png'), img);
	console.log('repulsor_boots.png');
}
