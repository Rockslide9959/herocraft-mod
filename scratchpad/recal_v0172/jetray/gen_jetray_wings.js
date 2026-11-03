// v0.17.2 (user: "when he flies he needs his wings so create the wing model and have it be in between his arms and his tail ...
// give him his tail aswell"): Jetray's wing membranes and tail, textures/entity/omnitrix/jetray_wings.png (128x128).
//   WING TOP    0,0   64x64  the membrane's back face: gold / yellow (the skin's fecc08 / feb808 / feda08)
//   WING UNDER  64,0  64x64  the underside: a darker orange-gold
//     cell x = across the membrane (0 = his side / leg, 63 = the arm), y = down it (0 = armpit..shoulder tip, 63 = the free
//     trailing edge from the knee to the wrist, cut concave by alpha); the shoulder end tapers to a point
//   TAIL        0,64  16x64  the whip's skin: red (the skin's 9d0d22 / a30d23), a darker belly, along y
//   FIN         16,64 32x32  the little arrowhead fin on the tip (red, gold rim), alpha cut
// Usage: node scratchpad/recal_v0172/jetray/gen_jetray_wings.js
'use strict';
const path = require('path');
const png = require('../../pnglib.js');

const W = 128, H = 128;
const px = Buffer.alloc(W * H * 4);
const hex = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const mix = (a, b, t) => a.map((c, i) => Math.round(c + (b[i] - c) * t));
const set = (x, y, c, a = 255) => {
	const i = (y * W + x) * 4;
	px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = a;
};
let seed = 1234567;
const rnd = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);

const GOLD = hex('#fecc08'), GOLD_HI = hex('#feda08'), GOLD_D = hex('#feb808'), GOLD_DD = hex('#e09806');
const UNDER = hex('#c8800a'), UNDER_D = hex('#a2620a'), UNDER_DD = hex('#7e4808');
const RED = hex('#9d0d22'), RED_HI = hex('#aa2035'), RED_D = hex('#7a0a1a'), RED_DD = hex('#55060f');

// ---- the membrane cells
function membrane(ox, top) {
	for (let y = 0; y < 64; y++) {
		for (let x = 0; x < 64; x++) {
			const u = x / 63, v = y / 63;
			// the free trailing edge: concave between the knee (x 0) and the wrist (x 63); the top tapers to the shoulder point
			const trail = 63 - 14 * Math.pow(Math.sin(Math.PI * u), 0.85);
			const topCut = 7 * Math.pow(Math.sin(Math.PI * u * 0.92), 1.2) * (1 - u * 0.4);
			if (y > trail || y < topCut) continue;
			const edge = Math.min(trail - y, y - topCut);
			// base: brighter toward the arm and the middle, darker toward his side
			let c;
			if (top) {
				c = mix(GOLD_D, GOLD, Math.min(1, u * 1.4));
				c = mix(c, GOLD_HI, Math.max(0, Math.sin(Math.PI * v) * 0.35 * u));
				// the soft ribs fanning from the arm (manta wing rays): slightly darker bands
				const ray = Math.sin((u * 5.5 + v * 2.2) * Math.PI);
				if (ray > 0.86) c = mix(c, GOLD_DD, 0.45);
			} else {
				c = mix(UNDER_D, UNDER, Math.min(1, u * 1.3));
				const ray = Math.sin((u * 5.5 + v * 2.2) * Math.PI);
				if (ray > 0.86) c = mix(c, UNDER_DD, 0.45);
			}
			// grain
			c = mix(c, top ? GOLD_DD : UNDER_DD, rnd() * 0.12);
			// the rims: a darker line along the free edge, the arm's edge a touch of red where it meets his skin
			if (edge < 1.5) c = mix(c, top ? GOLD_DD : UNDER_DD, 0.7);
			if (x >= 61) c = mix(c, RED, top ? 0.55 : 0.4);
			if (x <= 1) c = mix(c, RED_D, 0.5);
			set(ox + x, y, c);
		}
	}
}
membrane(0, true);
membrane(64, false);

// ---- the tail skin: 16 across (four faces of 4), 64 along
for (let y = 0; y < 64; y++) {
	for (let x = 0; x < 16; x++) {
		const face = x >> 2; // 0 top, 1 right, 2 belly, 3 left
		let c = face === 0 ? mix(RED, RED_HI, 0.3) : face === 2 ? RED_D : RED;
		if ((x & 3) === 0 || (x & 3) === 3) c = mix(c, RED_DD, 0.25);
		// faint rings along the whip
		if (y % 8 === 0) c = mix(c, RED_DD, 0.35);
		// the last stretch darkens toward the tip
		c = mix(c, RED_DD, Math.max(0, (y - 44) / 20) * 0.5);
		c = mix(c, RED_DD, rnd() * 0.12);
		set(x, 64 + y, c);
	}
}

// ---- the tip fin: an arrowhead, red with a gold rim (tip at x 31, the shaft end at x 0)
for (let y = 0; y < 32; y++) {
	for (let x = 0; x < 32; x++) {
		const cx = x / 31, cy = (y - 15.5) / 15.5;
		// half-width: swelling out from the shaft, curving back to the point
		const hw = Math.sin(Math.PI * Math.pow(cx, 0.75)) * 0.95 * (1 - Math.pow(cx, 3) * 0.3);
		// the swept-back barbs: a notch cut from the shaft end
		const notch = cx < 0.35 && Math.abs(cy) < 0.55 * (1 - cx / 0.35) && Math.abs(cy) > 0.2 ? 1 : 0;
		if (Math.abs(cy) > hw || notch) continue;
		const d = hw - Math.abs(cy);
		let c = mix(RED, RED_HI, 0.3 * (1 - Math.abs(cy)));
		if (Math.abs(cy) < 0.12) c = RED_D; // the spine
		if (d < 0.14) c = GOLD_D;
		c = mix(c, RED_DD, rnd() * 0.1);
		set(16 + x, 64 + y, c);
	}
}

const out = path.join(__dirname, '../../../src/main/resources/assets/projecthero/textures/entity/omnitrix/jetray_wings.png');
png.write(out, W, H, px);
console.log('wrote', out);
