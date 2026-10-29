// v0.17.1 (user's reference image: the raised dial on a black riser with the Omnitrix's lime circuit pattern round its side):
// the riser's strip, wrapped four times round OmnitrixModel.riser (u round the side, v from the bezel down to the cuff).
//   entity/omnitrix/circuit.png / circuit_red.png   96x40: black, glowing lime traces -- tall and short vertical bars standing
//   on the bottom edge, L-shaped traces dropping from the top and turning sideways, a thin rail under the bezel -- with a dark
//   green halo so they read as lit; the red variant for the watch's red states. Ends match so the four repeats are seamless.
// Usage: node scratchpad/gen_omnitrix_circuit.js [previewDir]
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('./pnglib.js');
const OUT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'entity', 'omnitrix');
const W = 96, H = 40;

function make(lime, halo, hi) {
	const m = new Uint8Array(W * H); // 1 = trace
	const rect = (x0, y0, x1, y1) => {
		for (let y = Math.max(0, y0); y < Math.min(H, y1); y++) for (let x = x0; x < x1; x++) m[y * W + ((x % W) + W) % W] = 1;
	};
	// the rail just under the bezel, broken in two places
	rect(0, 2, 30, 4); rect(36, 2, 70, 4); rect(76, 2, 96, 4);
	// vertical bars from the bottom: a pair, a single tall, a pair (the reference's "| |" clusters)
	rect(6, 20, 10, 38); rect(13, 24, 17, 38);
	rect(44, 14, 48, 38);
	rect(60, 22, 64, 38); rect(67, 18, 71, 38);
	// L traces: down from the rail, then sideways
	rect(24, 4, 28, 26); rect(24, 22, 38, 26);
	rect(52, 4, 56, 12); rect(52, 8, 60, 12);
	rect(80, 4, 84, 30); rect(80, 26, 92, 30);
	rect(88, 30, 92, 38);
	// a short stub and a dot
	rect(33, 30, 37, 38); rect(2, 8, 6, 12);
	const px = Buffer.alloc(W * H * 4);
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		const i = (y * W + x) * 4;
		let c = [12, 13, 15];
		if (m[y * W + x]) {
			// lit trace with a lighter core line
			c = lime;
			const left = !m[y * W + ((x + W - 1) % W)], top = y === 0 || !m[(y - 1) * W + x];
			if (left || top) c = hi;
		} else {
			// the halo: a trace within 2 texels
			let near = 0;
			for (let dy = -2; dy <= 2; dy++) for (let dx = -2; dx <= 2; dx++) {
				const yy = y + dy, xx = ((x + dx) % W + W) % W;
				if (yy >= 0 && yy < H && m[yy * W + xx]) near = Math.max(near, 3 - Math.max(Math.abs(dx), Math.abs(dy)));
			}
			if (near > 0) c = c.map((v, k) => Math.round(v + (halo[k] - v) * near / 3 * 0.55));
		}
		px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255;
	}
	return px;
}

const green = make([140, 240, 58], [40, 120, 20], [205, 255, 140]);
const red = make([255, 61, 46], [140, 24, 16], [255, 176, 160]);
PNG.write(path.join(OUT, 'circuit.png'), W, H, green);
PNG.write(path.join(OUT, 'circuit_red.png'), W, H, red);
if (process.argv[2]) {
	const s = 6, p = Buffer.alloc(W * s * H * s * 4);
	for (let y = 0; y < H * s; y++) for (let x = 0; x < W * s; x++) {
		const i = (Math.floor(y / s) * W + Math.floor(x / s)) * 4, o = (y * W * s + x) * 4;
		green.copy(p, o, i, i + 4);
	}
	PNG.write(path.join(process.argv[2], 'circuit_preview.png'), W * s, H * s, p);
}
console.log('wrote circuit(.png, _red.png)');
