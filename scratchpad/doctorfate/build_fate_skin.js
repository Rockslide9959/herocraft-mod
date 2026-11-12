// node scratchpad/doctorfate/build_fate_skin.js -- v0.17.3 Doctor Fate's suit from the user's chosen skin.
//   source (kept untouched): docs/reference/doctorfate/doctor_fate_dceu_jmct.png  (JMCT, PlanetMinecraft, slim / Alex arms)
//   writes  entity/doctor_fate/suit_slim.png  the skin with: helmet side openings cut out (skin-tone texels of the head made
//                                            transparent, so the wearer's own skin shows), the painted cape taken off the
//                                            back (outer layer cleared, base layer repainted as the suit's back)
//           entity/doctor_fate/suit.png       the same re-laid for wide (4 px) arms: every 3-wide arm face (front, back, top,
//                                            bottom, base + sleeve) gets a 4th column by repeating its middle column, so the
//                                            edge columns -- the ones that meet the side faces -- are the original ones and
//                                            every seam still lines up; the 4-wide side faces are copied as they are
//           entity/doctor_fate/cloak.png      the Cloak of Destiny for FlowingCapeLayer (64 x 48: outside on the left half,
//                                            lining on the right), painted in the skin's own cape colours and fold pattern
// Prints every texel it changes, so the edit can be checked.
'use strict';
const P = require('../pnglib.js');
const path = require('path');
const fs = require('fs');
const ROOT = path.join(__dirname, '../..');
const SRC = path.join(ROOT, 'docs/reference/doctorfate/doctor_fate_dceu_jmct.png');
const OUT = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/entity/doctor_fate');

const src = P.read(SRC);
if (src.W !== 64 || src.H !== 64) throw new Error('expected a 64x64 skin');
const px = Buffer.from(src.px);
const hex = (x, y) => { const i = (y * 64 + x) * 4; return px[i + 3] ? px.slice(i, i + 3).toString('hex') : null; };
const put = (b, x, y, h) => { const i = (y * 64 + x) * 4; if (h === null) { b.fill(0, i, i + 4); return; } b[i] = parseInt(h.slice(0, 2), 16); b[i + 1] = parseInt(h.slice(2, 4), 16); b[i + 2] = parseInt(h.slice(4, 6), 16); b[i + 3] = 255; };
let changes = 0;
function set(x, y, h, why) {
	const was = hex(x, y);
	if (was === h) return;
	put(px, x, y, h);
	changes++;
	if (process.argv.includes('-v')) console.log(`  ${why}: (${x},${y}) ${was || 'clear'} -> ${h || 'clear'}`);
}

// the skin's two "not suit" palettes
const SKIN_TONE = new Set(['f5a98d', 'f9b29a', 'fabda8', 'fbc5b2', 'ffcab8', 'ffd1c2']);
const CAPE = new Set(['957d35', 'a6883f', 'b89746', 'ba9c44', 'bfa15a', 'c6ad6d', 'cdb680', 'd4c193', 'e0cda0', 'e5cf9b', 'eedbaf', 'f2e4c4']);
const rect = (x0, y0, w, h, fn) => { for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) fn(x, y); };

// ---- 1. helmet side openings: the head's base layer has the face / neck painted where the DCEU helmet is open (the two side
// openings by the jaw, the gap round the back of the neck, the underside). Clear them; the hat layer's gold frame stays.
rect(0, 0, 32, 16, (x, y) => { if (SKIN_TONE.has(hex(x, y))) set(x, y, null, 'helmet opening'); });

// ---- 2. the cape off the back.
// Outer layer: clear cape-coloured texels on the backs, sides, tops and bottoms of the jacket, sleeves and trousers. The arms'
// OUTER side faces keep theirs (the bracer studs use the same tan), and no front face is touched (knee pads, buckles).
const clearCape = (x0, y0, w, h, why) => rect(x0, y0, w, h, (x, y) => { if (CAPE.has(hex(x, y))) set(x, y, null, why); });
// jacket (16,32): top 20..27/32..35, bottom 28..35/32..35, right side 16..19, left side 28..31, back 32..39 (rows 36..47)
clearCape(20, 32, 16, 4, 'jacket top/bottom');
clearCape(16, 36, 4, 12, 'jacket right side');
clearCape(28, 36, 4, 12, 'jacket left side');
clearCape(32, 36, 8, 12, 'jacket back');
// right sleeve (40,32), slim: top 44..46, bottom 47..49 (rows 32..35); outer 40..43, front 44..46, inner 47..50, back 51..53
clearCape(44, 32, 6, 4, 'right sleeve top/bottom');
clearCape(47, 36, 7, 12, 'right sleeve inner/back');
// left sleeve (48,48), slim: top 52..54, bottom 55..57; inner 48..51, front 52..54, outer 55..58, back 59..61
clearCape(52, 48, 6, 4, 'left sleeve top/bottom');
clearCape(48, 52, 4, 12, 'left sleeve inner');
clearCape(59, 52, 3, 12, 'left sleeve back');
// trousers: right (0,32), left (0,48): top 4..7, bottom 8..11; sides 0..3 and 8..11, back 12..15
for (const v of [32, 48]) {
	clearCape(4, v, 8, 4, 'trousers top/bottom');
	clearCape(0, v + 4, 4, 12, 'trousers side');
	clearCape(8, v + 4, 8, 12, 'trousers side/back');
}

// Base layer: the cape's collar on the body and its hem on the legs are painted on the base; repaint them as the suit.
// body top (20..27, 16..19): row 16 is the back edge -- gold shoulder caps like the front edge (row 19)
set(20, 16, 'ffbe3d', 'body top'); set(21, 16, 'ffc552', 'body top'); set(26, 16, 'ffc552', 'body top'); set(27, 16, 'ffbe3d', 'body top');
set(20, 17, 'ffbe3d', 'body top'); set(27, 17, 'ffbe3d', 'body top');
// body sides (right 16..19, left 28..31): the gold shoulder band (row 20) runs all the way round; row 21 navy
set(16, 20, 'ffbe3d', 'body side'); set(17, 20, 'ffbe3d', 'body side'); set(30, 20, 'ffbe3d', 'body side'); set(31, 20, 'ffbe3d', 'body side');
set(16, 21, '0a1c2d', 'body side'); set(17, 21, '061724', 'body side'); set(30, 21, '061724', 'body side'); set(31, 21, '0a1c2d', 'body side');
// body back (32..39): row 20 = the front's shoulder row mirrored (gold caps, navy between); rows 21-22 = the navy of rows 24 / 23
const backRow20 = ['ffbe3d', 'ffc552', '0d2235', '10283d', '10283d', '0d2235', 'ffc552', 'ffbe3d'];
for (let i = 0; i < 8; i++) {
	set(32 + i, 20, backRow20[i], 'body back');
	set(32 + i, 21, hex(32 + i, 24), 'body back');
	set(32 + i, 22, hex(32 + i, 23), 'body back');
}
// a thin gold seam down the spine, like the front's centre line, so the back isn't a blank panel
set(35, 23, '15324b', 'body back'); set(36, 23, '15324b', 'body back');
// legs: the hem across the back of the calves (right leg rows 25-26, left leg rows 57-58) -> the navy of the row above
const legHem = (x0, rowFrom, rows) => {
	for (const y of rows) for (let x = x0; x < x0 + 16; x++) {
		const fx = x - x0; // 0..3 outer/inner side, 4..7 front, 8..11 side, 12..15 back
		if (fx >= 4 && fx <= 7) continue;
		if (CAPE.has(hex(x, y))) set(x, y, hex(x, rowFrom), 'leg hem');
	}
};
legHem(0, 24, [25, 26]);
legHem(16, 56, [57, 58]);

// any cape texel left on a back face is a mistake: check
const leftovers = [];
const checkBack = (x0, y0, w, h, name) => rect(x0, y0, w, h, (x, y) => { if (CAPE.has(hex(x, y))) leftovers.push(`${name} (${x},${y}) ${hex(x, y)}`); });
checkBack(32, 20, 8, 12, 'body back'); checkBack(32, 36, 8, 12, 'jacket back');
checkBack(12, 20, 4, 11, 'right leg back'); checkBack(28, 52, 4, 11, 'left leg back');
checkBack(12, 36, 4, 12, 'right trousers back'); checkBack(12, 52, 4, 12, 'left trousers back');
checkBack(51, 20, 3, 12, 'right arm back'); checkBack(43, 52, 3, 12, 'left arm back');
if (leftovers.length) throw new Error('cape texels left on a back face:\n' + leftovers.join('\n'));

fs.mkdirSync(OUT, { recursive: true });
P.write(path.join(OUT, 'suit_slim.png'), 64, 64, px);
console.log(`suit_slim.png: ${changes} texels changed`);

// ---- 3. wide arms
function toWide(slim) {
	const out = Buffer.from(slim);
	const g = (x, y) => slim.slice((y * 64 + x) * 4, (y * 64 + x) * 4 + 4);
	const p = (x, y, c) => c.copy(out, (y * 64 + x) * 4);
	const take = (ox, oy, w, h) => { const f = []; for (let y = 0; y < h; y++) { f.push([]); for (let x = 0; x < w; x++) f[y].push(g(ox + x, oy + y)); } return f; };
	const widen = [0, 1, 1, 2]; // the edge columns stay on the edges, the middle one is repeated
	for (const [u, v] of [[40, 16], [40, 32], [32, 48], [48, 48]]) {
		const top = take(u + 4, v, 3, 4), bottom = take(u + 7, v, 3, 4), right = take(u, v + 4, 4, 12),
			front = take(u + 4, v + 4, 3, 12), left = take(u + 7, v + 4, 4, 12), back = take(u + 11, v + 4, 3, 12);
		rect(u, v, 16, 16, (x, y) => p(x, y, Buffer.alloc(4)));
		const wide = (f, ox, oy, h) => { for (let y = 0; y < h; y++) for (let x = 0; x < 4; x++) p(ox + x, oy + y, f[y][widen[x]]); };
		const copy = (f, ox, oy, h) => { for (let y = 0; y < h; y++) for (let x = 0; x < 4; x++) p(ox + x, oy + y, f[y][x]); };
		wide(top, u + 4, v, 4); wide(bottom, u + 8, v, 4);
		copy(right, u, v + 4, 12); wide(front, u + 4, v + 4, 12); copy(left, u + 8, v + 4, 12); wide(back, u + 12, v + 4, 12);
	}
	return out;
}
P.write(path.join(OUT, 'suit.png'), 64, 64, toWide(px));
console.log('suit.png: wide arms');

// ---- 4. the Cloak of Destiny (64 x 48): outside = left half, lining = right half. The skin's cape is pale gold, darker at the
// shoulders and lighter towards the hem, with a light fold down the middle; the cloth keeps those colours and that fold pattern
// (one skin column = four cloak columns, softened), and a gold border along the sides and hem like the suit's trim.
// the skin's cape tan (957d35 .. f2e4c4) mixed 40/60 with the helmet's gold (ffb624 .. ffd88a, plus darker shades), so the cloth
// reads as the gold / yellow Cloak of Destiny but stays in the skin's palette
const RAMP = ['987220', 'af8128', 'c6932d', 'd8a237', 'e8b241', 'ebbb58', 'eec36c', 'f3cc7d', 'f8d68f', 'fadda1'];
// brightness of each of the skin cape's 8 columns (jacket back, row 43): c6ad6d cdb680 bfa15a d4c193 e0cda0 bfa15a cdb680 c6ad6d
const FOLD = [4, 5, 3, 6, 7, 3, 5, 4];
const cloak = Buffer.alloc(64 * 48 * 4);
const cput = (x, y, h) => { const i = (y * 64 + x) * 4; cloak[i] = parseInt(h.slice(0, 2), 16); cloak[i + 1] = parseInt(h.slice(2, 4), 16); cloak[i + 2] = parseInt(h.slice(4, 6), 16); cloak[i + 3] = 255; };
const noise = (x, y) => { let h = (x * 374761393 + y * 668265263) >>> 0; h = ((h ^ (h >>> 13)) * 1274126177) >>> 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967295; };
for (let y = 0; y < 48; y++) for (let x = 0; x < 32; x++) {
	// fold: interpolate between neighbouring skin columns so the folds are soft, not 4-px blocks
	const fx = (x + 0.5) / 4 - 0.5, i0 = Math.max(0, Math.floor(fx)), i1 = Math.min(7, i0 + 1), t = Math.max(0, Math.min(1, fx - i0));
	const fold = FOLD[i0] * (1 - t) + FOLD[i1] * t;
	const down = y / 47; // darker at the shoulders, lighter at the hem (the skin goes a6883f/b89746 at the top -> e0cda0/eedbaf low)
	let lvl = fold - 2.2 + down * 3.2 + (noise(x, y) - 0.5) * 0.9;
	if (y < 2) lvl -= 1; // the shadow under the collar
	let c = RAMP[Math.max(0, Math.min(RAMP.length - 1, Math.round(lvl)))];
	if (x === 0 || x === 31 || y >= 46) c = y === 47 ? '9a6a12' : 'c6932d'; // a deeper gold border along the sides and hem
	cput(x, y, c);
	// lining: the same cloth in shadow (two steps darker), no border
	const l = RAMP[Math.max(0, Math.min(RAMP.length - 1, Math.round(lvl - 2)))];
	cput(32 + x, y, l);
}
P.write(path.join(OUT, 'cloak.png'), 64, 48, cloak);
console.log('cloak.png: Cloak of Destiny');
