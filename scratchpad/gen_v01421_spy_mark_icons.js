// v0.14.21: 18x18 status-effect icons for the Supervillain's Mark (purple watching eye inside crosshair ticks) and the
// Supervillain Omen (the same eye gone crimson, with rays). Run from the repo root: node scratchpad/gen_v01421_spy_mark_icons.js
const fs = require('fs');
const path = require('path');
const { encode } = require(path.join(__dirname, 'pnglib.js'));
const OUT = 'src/main/resources/assets/projecthero/textures/mob_effect';
const W = 18, H = 18;

function canvas() {
	const data = Buffer.alloc(W * H * 4);
	return {
		data,
		set(x, y, c) {
			if (x < 0 || y < 0 || x >= W || y >= H || !c) return;
			const i = (y * W + x) * 4;
			data[i] = c[0]; data[i + 1] = c[1]; data[i + 2] = c[2]; data[i + 3] = c[3] === undefined ? 255 : c[3];
		},
		get(x, y) { const i = (y * W + x) * 4; return data[i + 3]; },
	};
}

// Eye: almond |dy| <= 4.2 * (1 - (dx/7.5)^2), centred on (8.5, 8.5).
function inEye(x, y) {
	const dx = (x + 0.5 - 9) / 7.6, dy = y + 0.5 - 9;
	return Math.abs(dx) < 1 && Math.abs(dy) <= 4.4 * (1 - dx * dx);
}

function draw(pal, rays) {
	const c = canvas();
	// crosshair ticks / rays first, so the eye sits on top
	if (rays) {
		for (const [x, y] of [[9, 0], [9, 1], [8, 1], [9, 16], [9, 17], [8, 16], [0, 8], [1, 8], [0, 9], [16, 9], [17, 9], [17, 8],
			[3, 3], [2, 2], [14, 3], [15, 2], [3, 14], [2, 15], [14, 14], [15, 15]]) c.set(x, y, pal.ray);
	} else {
		for (const [x, y] of [[8, 0], [9, 0], [8, 1], [9, 1], [8, 16], [9, 16], [8, 17], [9, 17], [0, 8], [0, 9], [1, 8], [1, 9],
			[16, 8], [16, 9], [17, 8], [17, 9]]) c.set(x, y, pal.ray);
	}
	// sclera + outline
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		if (!inEye(x, y)) continue;
		const edge = !inEye(x - 1, y) || !inEye(x + 1, y) || !inEye(x, y - 1) || !inEye(x, y + 1);
		c.set(x, y, edge ? pal.outline : (y < 9 ? pal.white : pal.whiteShade));
	}
	// iris (radius ~3.2) with a lighter upper-left glint, slit pupil
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		const d = Math.hypot(x + 0.5 - 9, y + 0.5 - 9);
		if (d <= 3.3 && inEye(x, y)) {
			c.set(x, y, d > 2.4 ? pal.irisDark : pal.iris);
		}
	}
	for (let y = 6; y <= 11; y++) c.set(8, y, pal.pupil), c.set(9, y, pal.pupil);
	c.set(7, 7, pal.glint);
	return c;
}

const MARK = {
	ray: [176, 92, 230], outline: [44, 10, 66], white: [214, 196, 232], whiteShade: [168, 146, 196],
	iris: [150, 46, 214], irisDark: [86, 20, 128], pupil: [16, 4, 24], glint: [248, 236, 255],
};
const OMEN = {
	ray: [230, 60, 70], outline: [60, 6, 18], white: [236, 200, 204], whiteShade: [196, 150, 158],
	iris: [204, 24, 54], irisDark: [120, 10, 34], pupil: [20, 2, 6], glint: [255, 236, 236],
};

fs.mkdirSync(OUT, { recursive: true });
fs.writeFileSync(`${OUT}/supervillain_mark.png`, encode(W, H, draw(MARK, false).data));
fs.writeFileSync(`${OUT}/supervillain_omen.png`, encode(W, H, draw(OMEN, true).data));
console.log('wrote supervillain_mark.png + supervillain_omen.png');
