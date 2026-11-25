// v0.17.7 Mjolnir / Stormbreaker model rebuild: before | after comparison sheets from the harness stills.
// node scratchpad/thor_weapons_sheets_v0177.js  (reads docs/screenshots/v0177/thor_weapons/{before,after})
const fs = require('fs');
const path = require('path');
const { decode, encode } = require('./pngkit.js');
const DIR = 'docs/screenshots/v0177/thor_weapons';
// [shot, crop x, y, w, h] -- crops zoom on the weapon (854 x 480 stills), each scaled to 640 x 360
const SHOTS = [
	['gui_inventory', 250, 150, 200, 112.5], ['s_right_fp', 214, 0, 640, 360], ['m_right_fp', 214, 0, 640, 360],
	['s_right_front', 247, 110, 360, 202.5], ['s_right_side', 247, 110, 360, 202.5], ['s_right_cq', 107, 60, 640, 360],
	['m_right_front', 247, 110, 360, 202.5], ['m_right_side', 247, 110, 360, 202.5], ['s_left_fp', 0, 0, 640, 360],
	['dual_ms_front', 247, 110, 360, 202.5], ['dual_ms_fp', 107, 0, 640, 360], ['ground', 200, 60, 454, 255],
	['item_frames', 200, 40, 454, 255], ['thrown_s_b', 0, 60, 640, 360], ['thrown_m_b', 0, 60, 640, 360],
	['pinned_side', 247, 60, 360, 202.5], ['combo_s3_b', 200, 40, 454, 255], ['combo_dual2_b', 200, 80, 454, 255],
];
const W = 640, H = 360, GAP = 6;
function crop(file, [x, y, w, h]) {
	const t = decode(file);
	const px = Buffer.alloc(W * H * 4);
	for (let j = 0; j < H; j++) for (let i = 0; i < W; i++) {
		const sx = Math.min(t.w - 1, Math.floor(x + i * w / W)), sy = Math.min(t.h - 1, Math.floor(y + j * h / H));
		t.px.copy(px, (j * W + i) * 4, (sy * t.w + sx) * 4, (sy * t.w + sx) * 4 + 4);
	}
	return px;
}
function sheet(out, shots) {
	const SW = 2 * W + GAP, SH = shots.length * H + (shots.length - 1) * GAP;
	const o = Buffer.alloc(SW * SH * 4, 255);
	shots.forEach(([name, ...c], r) => ['before', 'after'].forEach((side, k) => {
		const f = path.join(DIR, side, name + '.png');
		if (!fs.existsSync(f)) return;
		const px = crop(f, c);
		for (let j = 0; j < H; j++) px.copy(o, ((r * (H + GAP) + j) * SW + k * (W + GAP)) * 4, j * W * 4, (j + 1) * W * 4);
	}));
	fs.writeFileSync(out, encode(SW, SH, o));
	console.log('wrote', out);
}
sheet(path.join(DIR, 'compare_held.png'), SHOTS.slice(0, 11));
sheet(path.join(DIR, 'compare_world.png'), SHOTS.slice(11));
