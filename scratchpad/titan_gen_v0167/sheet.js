// node sheet.js <out.png> <cols> <maxW per tile> <title> files...
'use strict';
const path = require('path');
const pk = require('./pngkit.js');
const L = require('./lib_concept_models.js');
const [, , out, colsS, tileWS, title, ...files] = process.argv;
const cols = +colsS, tw = +tileWS;
const imgs = files.map(f => pk.decode(f));
const scale = tw / imgs[0].w;
const th = Math.round(imgs[0].h * scale);
const rows = Math.ceil(imgs.length / cols);
const LAB = 22, TOP = 50;
const cv = new L.Canvas(cols * tw, TOP + rows * (th + LAB), [30, 32, 38]);
cv.text(title, 12, 12, 4, [240, 244, 250]);
imgs.forEach((im, i) => {
	const buf = Buffer.alloc(tw * th * 4);
	for (let y = 0; y < th; y++) for (let x = 0; x < tw; x++) {
		// box filter
		const sx0 = Math.floor(x / scale), sy0 = Math.floor(y / scale), sx1 = Math.max(sx0 + 1, Math.floor((x + 1) / scale)), sy1 = Math.max(sy0 + 1, Math.floor((y + 1) / scale));
		let r = 0, g = 0, b = 0, n = 0;
		for (let yy = sy0; yy < sy1 && yy < im.h; yy++) for (let xx = sx0; xx < sx1 && xx < im.w; xx++) { const o = (yy * im.w + xx) * 4; r += im.px[o]; g += im.px[o + 1]; b += im.px[o + 2]; n++; }
		const o = (y * tw + x) * 4; buf[o] = r / n; buf[o + 1] = g / n; buf[o + 2] = b / n; buf[o + 3] = 255;
	}
	const cx = (i % cols) * tw, cy = TOP + Math.floor(i / cols) * (th + LAB);
	cv.blit(buf, tw, th, cx, cy + LAB);
	cv.text(path.basename(files[i], '.png').replace(/^tv_/, ''), cx + 6, cy + 4, 2, [220, 226, 236]);
});
cv.save(out);
console.log('wrote', out);
