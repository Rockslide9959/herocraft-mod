// node scratchpad/contact_sheet.js <outPrefix> <files...> : 3 columns x N rows of half-size thumbnails, 18 per sheet
const png = require('./pnglib.js');
const [, , out, ...files] = process.argv;
const K = 2, COLS = 3, PER = 18;
for (let s = 0; s * PER < files.length; s++) {
	const group = files.slice(s * PER, s * PER + PER);
	const first = png.read(group[0]);
	const tw = Math.floor(first.w / K), th = Math.floor(first.h / K);
	const rows = Math.ceil(group.length / COLS);
	const W = tw * COLS, H = th * rows;
	const data = Buffer.alloc(W * H * 4, 255);
	group.forEach((f, i) => {
		const img = png.read(f);
		const ox = (i % COLS) * tw, oy = Math.floor(i / COLS) * th;
		for (let y = 0; y < th; y++) for (let x = 0; x < tw; x++) {
			const si = ((y * K) * img.w + x * K) * 4, di = ((oy + y) * W + ox + x) * 4;
			img.data.copy(data, di, si, si + 4); data[di + 3] = 255;
		}
	});
	png.write(`${out}_${s + 1}.png`, { w: W, h: H, data });
	console.log(`${out}_${s + 1}.png`, group.map(f => f.split('/').pop()).join(' | '));
}
