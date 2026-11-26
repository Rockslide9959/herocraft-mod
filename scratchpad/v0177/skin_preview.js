// v0.17.7: flat front / right / back / left preview of a 64x64 player skin (base layer with the second layer composited over it),
// for checking skin edits offline without a game client.
//
//   node scratchpad/v0177/skin_preview.js <skin.png> <out.png> [slim] [underlay.png]
//   or require('./skin_preview.js').render(skins, out, opts)
//
// Transparent texels show the underlay: another skin (e.g. a plain "wearer" skin) or, without one, a pink checker so every
// cleared texel stands out. render() takes several skins and draws them side by side, one row each.
const PNG = require('../pnglib.js');

const S = 8; // screen pixels per texel
const GAP = 3; // texels between views

// [base u, v, overlay u, v, w, h] per body part and face; x/y place it on a 16x32 figure
function parts(view, slim) {
	const aw = slim ? 3 : 4;
	// faces of a cube at (u,v) size w,h,d: right(-x side of model = player's right) u..u+d, front u+d.., left u+d+w.., back u+2d+w..
	const face = (u, v, w, h, d, f) => {
		const y = v + d;
		if (f === 'front') return [u + d, y, w, h];
		if (f === 'right') return [u, y, d, h];
		if (f === 'left') return [u + d + w, y, d, h];
		return [u + 2 * d + w, y, w, h]; // back
	};
	// cubes: base(u,v), overlay(u,v), w,h,d, figure x (front view, viewer's left = player's right), figure y
	const C = {
		head: { b: [0, 0], o: [32, 0], w: 8, h: 8, d: 8, x: 4, y: 0 },
		body: { b: [16, 16], o: [16, 32], w: 8, h: 12, d: 4, x: 4, y: 8 },
		rarm: { b: [40, 16], o: [40, 32], w: aw, h: 12, d: 4, x: 4 - aw, y: 8 },
		larm: { b: [32, 48], o: [48, 48], w: aw, h: 12, d: 4, x: 12, y: 8 },
		rleg: { b: [0, 16], o: [0, 32], w: 4, h: 12, d: 4, x: 4, y: 20 },
		lleg: { b: [16, 48], o: [0, 48], w: 4, h: 12, d: 4, x: 8, y: 20 },
	};
	const out = [];
	const add = (c, f, x, y, flip) => {
		const [bu, bv, w, h] = face(c.b[0], c.b[1], c.w, c.h, c.d, f);
		const [ou, ov] = face(c.o[0], c.o[1], c.w, c.h, c.d, f);
		out.push({ bu, bv, ou, ov, w, h, x, y, flip });
	};
	if (view === 'front') {
		for (const k of ['rarm', 'larm', 'rleg', 'lleg', 'body', 'head']) add(C[k], 'front', C[k].x, C[k].y);
	} else if (view === 'back') {
		// mirrored: the player's right is now on the viewer's right
		const mx = c => 16 - c.x - c.w;
		for (const k of ['rarm', 'larm', 'rleg', 'lleg', 'body', 'head']) add(C[k], 'back', mx(C[k]), C[k].y);
	} else if (view === 'right') {
		// the player's right side: head, arm (covers body), leg
		add(C.head, 'right', 4, 0);
		add(C.rleg, 'right', 6, 20);
		add(C.rarm, 'right', 6, 8);
	} else {
		add(C.head, 'left', 4, 0);
		add(C.lleg, 'left', 6, 20);
		add(C.larm, 'left', 6, 8);
	}
	return out;
}

function render(skins, outFile, opts = {}) {
	const slim = !!opts.slim;
	const under = opts.underlay ? PNG.read(opts.underlay) : null;
	const views = ['front', 'right', 'back', 'left'];
	const FW = 16, FH = 32;
	const W = (views.length * (FW + GAP) + GAP) * S, rowH = (FH + GAP * 2) * S;
	const H = rowH * skins.length;
	const o = Buffer.alloc(W * H * 4);
	for (let i = 0; i < W * H; i++) o.set([46, 48, 56, 255], i * 4);
	skins.forEach((file, row) => {
		const img = typeof file === 'string' ? PNG.read(file) : file;
		const get = (src, u, v) => { const i = (v * 64 + u) * 4; return [src.px[i], src.px[i + 1], src.px[i + 2], src.px[i + 3]]; };
		views.forEach((view, vi) => {
			const ox = (GAP + vi * (FW + GAP)) * S, oy = row * rowH + GAP * S;
			for (const p of parts(view, slim)) {
				for (let ty = 0; ty < p.h; ty++) for (let tx = 0; tx < p.w; tx++) {
					const sx = p.flip ? p.w - 1 - tx : tx;
					let col;
					// underlay first (the wearer), then base, then overlay
					const layers = [];
					if (under) { layers.push(get(under, p.bu + sx, p.bv + ty)); layers.push(get(under, p.ou + sx, p.ov + ty)); }
					layers.push(get(img, p.bu + sx, p.bv + ty));
					layers.push(get(img, p.ou + sx, p.ov + ty));
					for (let py = 0; py < S; py++) for (let px = 0; px < S; px++) {
						const X = ox + (p.x + tx) * S + px, Y = oy + (p.y + ty) * S + py;
						col = under ? [0, 0, 0] : (((px >> 2) + (py >> 2)) & 1 ? [255, 120, 200] : [200, 70, 150]);
						for (const l of layers) {
							const a = l[3] / 255;
							col = [0, 1, 2].map(c => l[c] * a + col[c] * (1 - a));
						}
						const grid = (px === 0 || py === 0) ? 0.88 : 1;
						o.set([...col.map(c => Math.round(c * grid)), 255], (Y * W + X) * 4);
					}
				}
			}
		});
	});
	PNG.write(outFile, W, H, o);
}

module.exports = { render };

if (require.main === module) {
	const [, , skin, out, slim, underlay] = process.argv;
	render([skin], out, { slim: slim === 'slim', underlay });
	console.log('wrote ' + out);
}
