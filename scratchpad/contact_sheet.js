// contact sheet: node contact_sheet.js out.png cols scale img1 img2 ...
const p = require('./pnglib.js');
const [, , out, colsS, scaleS, ...files] = process.argv;
const cols = +colsS, sc = +scaleS;
const imgs = files.map(f => p.read(f));
const w = Math.round(imgs[0].W * sc), h = Math.round(imgs[0].H * sc);
const rows = Math.ceil(imgs.length / cols), W = cols * w + (cols - 1) * 4, H = rows * h + (rows - 1) * 4;
const o = Buffer.alloc(W * H * 4, 255);
imgs.forEach((im, i) => {
	const ox = (i % cols) * (w + 4), oy = Math.floor(i / cols) * (h + 4);
	for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
		const sx = Math.min(im.W - 1, Math.floor(x / sc)), sy = Math.min(im.H - 1, Math.floor(y / sc));
		im.px.copy(o, ((oy + y) * W + ox + x) * 4, (sy * im.W + sx) * 4, (sy * im.W + sx) * 4 + 4);
	}
});
p.write(out, W, H, o);
