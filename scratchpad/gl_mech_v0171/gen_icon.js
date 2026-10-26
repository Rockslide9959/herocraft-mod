// v0.17.1: the Hard-Light Mech's 16x16 Construct Menu icon (textures/gui/green_lantern/mech_icon.png):
// a front-on mech silhouette -- pauldrons, chest ring, fists, legs -- in the menu's green style.
'use strict';
const path = require('path');
const fs = require('fs');
const L = require('../viperion/lib_concept_models.js');
const W = 16, H = 16;
const px = Buffer.alloc(W * H * 4);
const set = (x, y, c, a = 255) => { if (x < 0 || y < 0 || x >= W || y >= H) return; const i = (y * W + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = a; };
const rect = (x0, y0, x1, y1, c) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) set(x, y, c); };
const D = [14, 96, 46], M = [40, 200, 96], B = [130, 255, 170], Wt = [235, 255, 240];
// silhouette (dark outline first, then fill)
const parts = [
	[6, 1, 9, 3], // head
	[4, 4, 11, 9], // torso
	[1, 3, 4, 6], [11, 3, 14, 6], // pauldrons
	[1, 7, 3, 11], [12, 7, 14, 11], // arms / fists
	[5, 10, 10, 11], // pelvis
	[4, 12, 6, 15], [9, 12, 11, 15], // legs
];
for (const [a, b, c, d] of parts) rect(a - 0, b - 0, c, d, D);
for (const [a, b, c, d] of parts) rect(a + 1 > c ? a : a, b + 1 > d ? b : b + 0, c, d, M);
// edges brighter
for (const [a, b, c, d] of parts) { for (let x = a; x <= c; x++) set(x, b, B); }
// visor + chest ring + knuckles
rect(6, 2, 9, 2, Wt);
set(7, 5, Wt); set(8, 5, Wt); set(6, 6, Wt); set(9, 6, Wt); set(6, 7, Wt); set(9, 7, Wt); set(7, 8, Wt); set(8, 8, Wt);
rect(5, 4, 10, 4, B); rect(5, 9, 10, 9, B);
rect(1, 11, 3, 11, Wt); rect(12, 11, 14, 11, Wt);
// outline pass: transparent neighbours of filled pixels get a dark rim
const out = Buffer.from(px);
for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
	const i = (y * W + x) * 4;
	if (px[i + 3]) continue;
	let n = false;
	for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) { const xx = x + dx, yy = y + dy; if (xx >= 0 && yy >= 0 && xx < W && yy < H && px[(yy * W + xx) * 4 + 3]) n = true; }
	if (n) { out[i] = 6; out[i + 1] = 40; out[i + 2] = 18; out[i + 3] = 200; }
}
const f = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'gui', 'green_lantern', 'mech_icon.png');
fs.writeFileSync(f, L.encodePng(W, H, out));
console.log('wrote', f);
