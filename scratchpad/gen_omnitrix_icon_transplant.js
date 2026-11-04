// v0.17.2 (user: "all the alien icons must be the same size and look the exact same except for the alien silhuotte itself"):
// the user's newer icons arrived as differently framed crops (an oval Ditto, white on Way Big, smaller Buzzshock / Upchuck /
// Wildvine). Instead of fitting each crop's own disc, this keeps ONE frame for every icon -- the original ten's
// (dial_<TEMPLATE>.png: the disc, the rim, the diamond, pixel for pixel) -- and transplants only the black silhouette:
//   1. the source's lime diamond is located: its left / right corners from the lime bounding box, its centre row from the
//      widest lime row, its height from the template's own diamond aspect (all the user's icons come from one sheet style)
//   2. every template pixel wholly inside the template's diamond is redrawn: lime, darkened by how much of the matching patch
//      of the source (4x4 supersampled) is silhouette (dark pixels inside the source diamond, its black border excluded)
//   3. the red copy uses the template's red dial with the red fill; the 32x32 font glyph is the green dial halved
// Usage: node scratchpad/gen_omnitrix_icon_transplant.js <id>=<source.png> [...]  [--preview out.png]
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('./pnglib.js');
const RES = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures');
const GUI = path.join(RES, 'gui', 'omnitrix');
const FONT = path.join(RES, 'font', 'omnitrix_dial');
const TEMPLATE = 'heatblast';

const isLime = (r, g, b) => g > 150 && g - r > 40 && g - b > 90;
const isRedFill = (r, g, b) => r > 170 && r - g > 90 && r - b > 90;

/** The diamond in an image: centre, half width, half height (pixels), from its fill colour. */
function findDiamond(img, fill, aspect) {
	const { W, H, px } = img;
	let x0 = W, x1 = -1;
	const rows = [];
	for (let y = 0; y < H; y++) {
		let l = -1, r = -1;
		for (let x = 0; x < W; x++) {
			const i = (y * W + x) * 4;
			if (px[i + 3] > 128 && fill(px[i], px[i + 1], px[i + 2])) {
				if (l < 0) l = x;
				r = x;
			}
		}
		if (l >= 0) {
			x0 = Math.min(x0, l);
			x1 = Math.max(x1, r);
			rows.push([y, r - l + 1]);
		}
	}
	const maxW = Math.max(...rows.map(r => r[1]));
	const wide = rows.filter(r => r[1] >= maxW - 1).map(r => r[0]);
	const cy = wide.reduce((a, b) => a + b, 0) / wide.length + 0.5;
	const cx = (x0 + x1 + 1) / 2;
	const hw = (x1 + 1 - x0) / 2;
	let hh;
	if (aspect) {
		hh = hw * aspect;
	} else {
		let y0 = H, y1 = -1;
		for (const [y] of rows) {
			y0 = Math.min(y0, y);
			y1 = Math.max(y1, y);
		}
		hh = (y1 + 1 - y0) / 2;
	}
	return { cx, cy, hw, hh };
}

const inD = (d, x, y, k) => Math.abs(x - d.cx) / (d.hw * k) + Math.abs(y - d.cy) / (d.hh * k) < 1;

/** How much of the source patch under template pixel (tx, ty) is silhouette (0..1). */
function coverage(src, sd, td, tx, ty) {
	let n = 0, dark = 0;
	for (let sy = 0; sy < 4; sy++) {
		for (let sx = 0; sx < 4; sx++) {
			const u = (tx + (sx + 0.5) / 4 - td.cx) / td.hw, v = (ty + (sy + 0.5) / 4 - td.cy) / td.hh;
			const X = sd.cx + u * sd.hw, Y = sd.cy + v * sd.hh;
			n++;
			if (!inD(sd, X, Y, 0.93)) continue; // the source's black diamond border is not silhouette
			const xi = Math.floor(X), yi = Math.floor(Y);
			if (xi < 0 || yi < 0 || xi >= src.W || yi >= src.H) continue;
			const i = (yi * src.W + xi) * 4;
			const m = Math.max(src.px[i], src.px[i + 1], src.px[i + 2]);
			if (src.px[i + 3] > 128 && m < 110) dark++;
		}
	}
	return dark / n;
}

function build(src, template, fillTest, fill) {
	const t = PNG.read(path.join(GUI, `dial_${template}.png`));
	const tdLime = findDiamond(t, fillTest, null);
	const out = Buffer.from(t.px);
	return { t, td: tdLime, out, fill };
}

function transplant(id, srcPath) {
	const src = PNG.read(srcPath);
	const tg = PNG.read(path.join(GUI, `dial_${TEMPLATE}.png`));
	const tr = PNG.read(path.join(GUI, `dial_${TEMPLATE}_red.png`));
	const td = findDiamond(tg, isLime, null);
	const aspect = td.hh / td.hw;
	const sd = findDiamond(src, isLime, aspect);
	// the fill colours, sampled from the template's own clean diamond pixels (left of centre, where no silhouette sits)
	const sample = (img, test) => {
		for (let x = Math.floor(td.cx - td.hw + 2); x < td.cx; x++) {
			const i = (Math.floor(td.cy) * 64 + x) * 4;
			if (test(img.px[i], img.px[i + 1], img.px[i + 2])) return [img.px[i], img.px[i + 1], img.px[i + 2]];
		}
		throw new Error('no fill pixel in template');
	};
	const LIME = sample(tg, isLime), RED = sample(tr, isRedFill);
	const outG = Buffer.from(tg.px), outR = Buffer.from(tr.px);
	for (let y = 0; y < 64; y++) {
		for (let x = 0; x < 64; x++) {
			// only pixels wholly inside the template's diamond (its rim's anti-aliasing stays the template's)
			let all = true;
			for (const [ox, oy] of [[0, 0], [1, 0], [0, 1], [1, 1]]) if (!inD(td, x + ox, y + oy, 1)) all = false;
			if (!all) continue;
			const c = coverage(src, sd, td, x, y);
			const i = (y * 64 + x) * 4;
			for (let k = 0; k < 3; k++) {
				outG[i + k] = Math.round(LIME[k] * (1 - c));
				outR[i + k] = Math.round(RED[k] * (1 - c));
			}
			outG[i + 3] = outR[i + 3] = 255;
		}
	}
	PNG.write(path.join(GUI, `dial_${id}.png`), 64, 64, outG);
	PNG.write(path.join(GUI, `dial_${id}_red.png`), 64, 64, outR);
	const BLUR = JSON.stringify({ texture: { blur: true, clamp: true } }, null, 2) + '\n';
	fs.writeFileSync(path.join(GUI, `dial_${id}.png.mcmeta`), BLUR);
	fs.writeFileSync(path.join(GUI, `dial_${id}_red.png.mcmeta`), BLUR);
	const g = Buffer.alloc(32 * 32 * 4);
	for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) for (let c = 0; c < 4; c++) {
		let s = 0;
		for (const [dx, dy] of [[0, 0], [1, 0], [0, 1], [1, 1]]) s += outG[((y * 2 + dy) * 64 + x * 2 + dx) * 4 + c];
		g[(y * 32 + x) * 4 + c] = Math.round(s / 4);
	}
	PNG.write(path.join(FONT, `${id}.png`), 32, 32, g);
	console.log(id, 'source diamond', JSON.stringify(Object.fromEntries(Object.entries(sd).map(([k, v]) => [k, +v.toFixed(1)]))));
}

const args = process.argv.slice(2);
let preview = null;
const jobs = [];
for (let i = 0; i < args.length; i++) {
	if (args[i] === '--preview') preview = args[++i];
	else jobs.push(args[i].split('='));
}
for (const [id, p] of jobs) transplant(id, p);
if (preview) {
	const ids = [TEMPLATE, 'four_arms', ...jobs.map(j => j[0])];
	const S = 3, cell = 64 * S + 6, W = cell * ids.length, H = 64 * S * 2 + 6;
	const px = Buffer.alloc(W * H * 4);
	for (let i = 0; i < px.length; i += 4) { px[i] = 70; px[i + 1] = 90; px[i + 2] = 70; px[i + 3] = 255; }
	ids.forEach((id, k) => ['', '_red'].forEach((sfx, row) => {
		const im = PNG.read(path.join(GUI, `dial_${id}${sfx}.png`));
		for (let y = 0; y < 64 * S; y++) for (let x = 0; x < 64 * S; x++) {
			const s = ((y / S | 0) * 64 + (x / S | 0)) * 4, a = im.px[s + 3] / 255;
			const o = ((y + row * (64 * S + 6)) * W + x + k * cell) * 4;
			for (let c = 0; c < 3; c++) px[o + c] = px[o + c] * (1 - a) + im.px[s + c] * a;
		}
	}));
	PNG.write(preview, W, H, px);
}
