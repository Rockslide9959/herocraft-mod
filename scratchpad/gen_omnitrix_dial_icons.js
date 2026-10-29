// v0.17.1: the Omnitrix dial icons from the user's chosen 222x222 reference images (a grey disc, a black rim, a lime
// diamond with the alien's black silhouette; one per alien, named by alien id). The source sheets are NOT committed.
//   * the near-white background outside the disc becomes transparent: the disc is fitted from the image (the bounding
//     box of the non-white pixels) and its alpha is the analytic coverage of that circle (16x supersampled), so the
//     edge is clean at any size; the colour under the edge is pulled to the rim's dark grey (no white fringe)
//   * a red variant per icon: the lime is swapped for a matching red (the green channel's excess moves to red), the
//     grey disc, the black rim and the silhouette untouched
//   * downscaled with a separable Lanczos-3 filter on premultiplied RGBA to the sizes drawn:
//       textures/gui/omnitrix/dial_<id>.png / dial_<id>_red.png      64x64 (watch face, HUD; .mcmeta blur = linear)
//       textures/font/omnitrix_dial/<id>.png                           32x32 (the inline text icon, font omnitrix_dial)
// Usage: node scratchpad/gen_omnitrix_dial_icons.js <folder with the 10 source PNGs>
'use strict';
const fs = require('fs');
const path = require('path');
const PNG = require('./pnglib.js');
const SRC = process.argv[2];
const RES = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero');
const GUI = path.join(RES, 'textures', 'gui', 'omnitrix');
const FONT = path.join(RES, 'textures', 'font', 'omnitrix_dial');
fs.mkdirSync(FONT, { recursive: true });
const IDS = ['heatblast', 'four_arms', 'xlr8', 'diamondhead', 'upgrade', 'ghostfreak', 'stinkfly', 'wildmutt', 'ripjaws', 'grey_matter'];

function clean(img) {
	const { W, H, px } = img;
	let x0 = W, y0 = H, x1 = -1, y1 = -1;
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		const i = (y * W + x) * 4;
		if (Math.min(px[i], px[i + 1], px[i + 2]) < 200) {
			x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
		}
	}
	const cx = (x0 + x1 + 1) / 2, cy = (y0 + y1 + 1) / 2;
	// a hair inside the outermost dark pixels so no half-white edge pixel survives
	const R = Math.min(x1 + 1 - x0, y1 + 1 - y0) / 2 - 0.6;
	const out = new Float32Array(W * H * 4);
	const RIM = [40, 40, 40];
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		let cov = 0;
		for (let sy = 0; sy < 4; sy++) for (let sx = 0; sx < 4; sx++) {
			const dx = x + (sx + 0.5) / 4 - cx, dy = y + (sy + 0.5) / 4 - cy;
			if (dx * dx + dy * dy <= R * R) cov++;
		}
		cov /= 16;
		const i = (y * W + x) * 4;
		const d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
		let r = px[i], g = px[i + 1], b = px[i + 2];
		// the outer ring of pixels: the source's anti-aliasing blends the rim into white -- use the rim's colour there
		if (d > R - 2.5) {
			r = RIM[0]; g = RIM[1]; b = RIM[2];
		}
		out[i] = r; out[i + 1] = g; out[i + 2] = b; out[i + 3] = cov * 255;
	}
	return { W, H, f: out, cx, cy, R };
}

function redden(img) {
	const f = Float32Array.from(img.f);
	for (let i = 0; i < f.length; i += 4) {
		const r = f[i], g = f[i + 1], b = f[i + 2];
		const ex = g - Math.max(r, b); // how lime the pixel is
		if (ex > 4) {
			const k = Math.min(1, ex / 60);
			// lime (160,230,40) -> red (235,40,40): green's level goes to red, green drops to blue's
			const nr = Math.min(255, g * 1.02), ng = b, nb = b;
			f[i] = r + (nr - r) * k;
			f[i + 1] = g + (ng - g) * k;
			f[i + 2] = b + (nb - b) * k;
		}
	}
	return { ...img, f };
}

function lanczos(x, a) {
	if (x === 0) return 1;
	if (Math.abs(x) >= a) return 0;
	const p = Math.PI * x;
	return (a * Math.sin(p) * Math.sin(p / a)) / (p * p);
}

// separable Lanczos-3 resample of premultiplied RGBA (W x H float) to n x n
function resize(img, n) {
	const { W, H } = img;
	const pre = new Float32Array(W * H * 4);
	for (let i = 0; i < pre.length; i += 4) {
		const a = img.f[i + 3] / 255;
		pre[i] = img.f[i] * a; pre[i + 1] = img.f[i + 1] * a; pre[i + 2] = img.f[i + 2] * a; pre[i + 3] = img.f[i + 3];
	}
	const pass = (src, sw, sh, dw, horizontal) => {
		const dh = horizontal ? sh : dw;
		const outW = horizontal ? dw : sw;
		const len = horizontal ? sw : sh;
		const scale = len / dw;
		const support = 3 * scale;
		const dst = new Float32Array(outW * dh * 4);
		for (let o = 0; o < dw; o++) {
			const c = (o + 0.5) * scale;
			const lo = Math.max(0, Math.floor(c - support)), hi = Math.min(len - 1, Math.ceil(c + support));
			const w = [];
			let ws = 0;
			for (let s = lo; s <= hi; s++) {
				const v = lanczos((s + 0.5 - c) / scale, 3);
				w.push(v); ws += v;
			}
			const lines = horizontal ? sh : sw;
			for (let l = 0; l < lines; l++) {
				const acc = [0, 0, 0, 0];
				for (let s = lo; s <= hi; s++) {
					const si = horizontal ? (l * sw + s) * 4 : (s * sw + l) * 4;
					const k = w[s - lo] / ws;
					for (let ch = 0; ch < 4; ch++) acc[ch] += src[si + ch] * k;
				}
				const di = horizontal ? (l * outW + o) * 4 : (o * outW + l) * 4;
				for (let ch = 0; ch < 4; ch++) dst[di + ch] = acc[ch];
			}
		}
		return dst;
	};
	const h = pass(pre, W, H, n, true);
	const v = pass(h, n, H, n, false);
	const out = Buffer.alloc(n * n * 4);
	for (let i = 0; i < n * n * 4; i += 4) {
		const a = Math.max(0, Math.min(255, v[i + 3]));
		const k = a > 0.5 ? 255 / a : 0;
		for (let ch = 0; ch < 3; ch++) out[i + ch] = Math.max(0, Math.min(255, Math.round(v[i + ch] * k)));
		out[i + 3] = Math.round(a);
	}
	return out;
}

// v0.17.1 (user: "make the greens match"): the source lime (#a9f227) becomes the watch's own green (#8cf03a, the green swatch
// in gen_omnitrix_watch_v2.js) -- every lime-ish pixel keeps its brightness against black (k = g / 242) so the edges stay soft
function matchGreen(img) {
	const px = img.f;
	for (let i = 0; i < px.length; i += 4) {
		const r = px[i], g = px[i + 1], b = px[i + 2];
		if (g > 30 && Math.abs(r - 0.7 * g) < 0.18 * g + 8 && b < 0.4 * g) {
			const k = Math.min(1, g / 242);
			px[i] = Math.round(0x8c * k); px[i + 1] = Math.round(0xf0 * k); px[i + 2] = Math.round(0x3a * k);
		}
	}
	return img;
}

const BLUR = JSON.stringify({ texture: { blur: true, clamp: true } }, null, 2) + '\n';
for (const id of IDS) {
	const src = path.join(SRC, id + '.png');
	const img = clean(PNG.read(src));
	const red = redden(img);
	matchGreen(img); // after the red copy is made from the original lime
	PNG.write(path.join(GUI, `dial_${id}.png`), 64, 64, resize(img, 64));
	PNG.write(path.join(GUI, `dial_${id}_red.png`), 64, 64, resize(red, 64));
	fs.writeFileSync(path.join(GUI, `dial_${id}.png.mcmeta`), BLUR);
	fs.writeFileSync(path.join(GUI, `dial_${id}_red.png.mcmeta`), BLUR);
	PNG.write(path.join(FONT, `${id}.png`), 32, 32, resize(img, 32));
	console.log(id, `disc centre (${img.cx.toFixed(1)}, ${img.cy.toFixed(1)}) r ${img.R.toFixed(1)}`);
}
// the 222-px cleaned previews (not shipped) for checking the edge and the red swap
if (process.argv[3]) {
	for (const id of ['heatblast', 'ghostfreak']) {
		const img = clean(PNG.read(path.join(SRC, id + '.png')));
		PNG.write(path.join(process.argv[3], `prev_${id}_128.png`), 128, 128, resize(img, 128));
		PNG.write(path.join(process.argv[3], `prev_${id}_red_128.png`), 128, 128, resize(redden(img), 128));
	}
}
