// v0.16.5 (user): the raw-flesh body Cyborg wears for 20 s between the Mother Box blast and the plates.
// node scratchpad/v0165_suits/cyborg_flesh.js  ->  textures/entity/cyborg/cyborg_flesh.png (64x64 player-skin layout, base
// layer only; the overlay areas and the human part of the face -- the same half his finished head keeps -- stay clear)
const P = require('./png.js');
const path = require('path');
const OUT = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero/textures/entity/cyborg/cyborg_flesh.png');
const px = Buffer.alloc(64 * 64 * 4);
let seed = 1337;
const rnd = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
const set = (x, y, c, a = 255) => { const i = (y * 64 + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = a; };
const mix = (a, b, t) => a.map((v, k) => Math.round(v + (b[k] - v) * t));
const MUSCLE = [150, 32, 34], FIBRE = [186, 62, 58], SEAM = [92, 16, 22], TENDON = [214, 176, 158], SCORCH = [46, 26, 22], BLOOD = [110, 8, 14];
// base-layer boxes: [u, v, w, h] of every face region (head, body, both arms, both legs)
const regions = [[0, 0, 32, 16], [16, 16, 24, 16], [40, 16, 16, 16], [0, 16, 16, 16], [16, 48, 16, 16], [32, 48, 16, 16]];
for (const [u0, v0, w, h] of regions) {
	for (let y = v0; y < v0 + h; y++) for (let x = u0; x < u0 + w; x++) {
		if (y < v0 + 4 && ((x - u0) < 8 || (x - u0) >= w - 8) && h === 16 && u0 !== 0) { /* limb / body top-bottom faces */ }
		// vertical muscle fibres, a lighter strand every other column, wobbling down the limb
		const strand = ((x + Math.floor((y - v0) / 5)) % 3 === 0);
		let c = strand ? mix(MUSCLE, FIBRE, 0.5 + rnd() * 0.5) : mix(MUSCLE, SEAM, rnd() * 0.35);
		if (rnd() < 0.05) c = BLOOD;
		set(x, y, c);
	}
}
// pale tendons across the elbows / knees (the middle of each limb's side faces) and the ribs on the chest
for (const [u0, v0] of [[40, 16], [0, 16], [16, 48], [32, 48]]) for (let x = u0; x < u0 + 16; x++) { set(x, v0 + 10, mix(TENDON, MUSCLE, rnd() * 0.3)); }
for (let ry = 22; ry <= 28; ry += 2) for (let x = 21; x <= 26; x++) set(x, ry, mix(TENDON, MUSCLE, 0.25 + rnd() * 0.3));
for (let y = 20; y < 32; y++) set(23 + (y % 2), y, SEAM); // the midline down the chest
// scorch from the blast
for (let n = 0; n < 70; n++) { const r = regions[Math.floor(rnd() * regions.length)]; const x = r[0] + Math.floor(rnd() * r[2]), y = r[1] + Math.floor(rnd() * r[3]); set(x, y, mix(SCORCH, BLOOD, rnd() * 0.4)); }
// the face: the flesh side has a bare eye and teeth; the other side (u 8..11, rows 9..14) is his own face, left clear
for (let y = 9; y <= 14; y++) for (let x = 8; x <= 11; x++) set(x, y, [0, 0, 0], 0);
set(13, 11, [236, 230, 222]); set(14, 11, [40, 20, 20]); set(12, 11, mix(MUSCLE, SEAM, 0.5));
for (let x = 12; x <= 15; x++) set(x, 14, x % 2 ? [228, 218, 196] : [200, 186, 160]);
P.write(OUT, 64, 64, px);
console.log('wrote', OUT);
