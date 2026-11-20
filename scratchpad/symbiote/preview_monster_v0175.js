// v0.17.5 Symbiote Monster: a tiny software rasteriser for gen_symbiote_monster_v0175.js -- orthographic turntable sheets of the
// model (bedrock space, the same bone maths the generator paints with), so proportions and paint can be checked without a client.
'use strict';
const PNG = require('../pnglib.js');

function render(M, opts) {
	const { bones, B, toModel, faceCorners, FACES, AW, AH, tex } = M;
	const W = opts.w, H = opts.h;
	const px = Buffer.alloc(W * H * 4);
	const zb = new Float32Array(W * H).fill(1e9);
	for (let i = 0; i < W * H; i++) { px[i * 4] = opts.bg[0]; px[i * 4 + 1] = opts.bg[1]; px[i * 4 + 2] = opts.bg[2]; px[i * 4 + 3] = 255; }
	const yaw = opts.yaw * Math.PI / 180, pitch = (opts.pitch || 0) * Math.PI / 180;
	const cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
	const view = (p) => {
		// turn the model by yaw (about y), tip by pitch, camera at -z looking +z: screen x = -x
		let x = p[0] * cy + p[2] * sy, z = -p[0] * sy + p[2] * cy, y = p[1];
		const y2 = y * cp - z * sp, z2 = y * sp + z * cp;
		return [-x, y2, z2];
	};
	const L = [0.45, 0.75, -0.55];
	const ll = Math.hypot(...L);
	for (const b of bones) for (const c of b.cubes) for (const f of FACES) {
		const uvr = c.uv[f];
		const corners = faceCorners(c, f).map((q) => view(toModel(b, q, opts.pose)));
		const uvs = [[uvr.uv[0], uvr.uv[1]], [uvr.uv[0] + uvr.uv_size[0], uvr.uv[1]], [uvr.uv[0] + uvr.uv_size[0], uvr.uv[1] + uvr.uv_size[1]], [uvr.uv[0], uvr.uv[1] + uvr.uv_size[1]]];
		const e1 = [corners[1][0] - corners[0][0], corners[1][1] - corners[0][1], corners[1][2] - corners[0][2]];
		const e2 = [corners[3][0] - corners[0][0], corners[3][1] - corners[0][1], corners[3][2] - corners[0][2]];
		let n = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]];
		const nl = Math.hypot(...n) || 1;
		n = n.map((v) => v / nl);
		const lit = 0.5 + 0.5 * Math.abs((n[0] * -L[0] + n[1] * L[1] + n[2] * L[2]) / ll);
		const S = (q) => [W / 2 + (q[0] - opts.cx) * opts.scale, H - opts.margin - (q[1] - opts.cyw) * opts.scale, q[2]];
		const s = corners.map(S);
		for (const tri of [[0, 1, 2], [0, 2, 3]]) {
			const [a, bq, cq] = tri.map((i) => s[i]);
			const [ta, tb, tc] = tri.map((i) => uvs[i]);
			const minx = Math.max(0, Math.floor(Math.min(a[0], bq[0], cq[0]))), maxx = Math.min(W - 1, Math.ceil(Math.max(a[0], bq[0], cq[0])));
			const miny = Math.max(0, Math.floor(Math.min(a[1], bq[1], cq[1]))), maxy = Math.min(H - 1, Math.ceil(Math.max(a[1], bq[1], cq[1])));
			const den = (bq[1] - cq[1]) * (a[0] - cq[0]) + (cq[0] - bq[0]) * (a[1] - cq[1]);
			if (Math.abs(den) < 1e-9) continue;
			for (let y = miny; y <= maxy; y++) for (let x = minx; x <= maxx; x++) {
				const X = x + 0.5, Y = y + 0.5;
				const w0 = ((bq[1] - cq[1]) * (X - cq[0]) + (cq[0] - bq[0]) * (Y - cq[1])) / den;
				const w1 = ((cq[1] - a[1]) * (X - cq[0]) + (a[0] - cq[0]) * (Y - cq[1])) / den;
				const w2 = 1 - w0 - w1;
				if (w0 < -1e-4 || w1 < -1e-4 || w2 < -1e-4) continue;
				const z = w0 * a[2] + w1 * bq[2] + w2 * cq[2];
				const zi = y * W + x;
				if (z >= zb[zi]) continue;
				zb[zi] = z;
				const u = Math.min(AW - 1, Math.max(0, Math.floor(w0 * ta[0] + w1 * tb[0] + w2 * tc[0])));
				const v = Math.min(AH - 1, Math.max(0, Math.floor(w0 * ta[1] + w1 * tb[1] + w2 * tc[1])));
				const ti = (v * AW + u) * 4;
				px[zi * 4] = Math.min(255, tex[ti] * lit);
				px[zi * 4 + 1] = Math.min(255, tex[ti + 1] * lit);
				px[zi * 4 + 2] = Math.min(255, tex[ti + 2] * lit);
			}
		}
	}
	return { px, W, H };
}

function compose(images, cols, out) {
	const w = images[0].W, h = images[0].H;
	const rows = Math.ceil(images.length / cols);
	const W = w * cols, H = h * rows;
	const px = Buffer.alloc(W * H * 4);
	images.forEach((im, k) => {
		const ox = (k % cols) * w, oy = Math.floor(k / cols) * h;
		for (let y = 0; y < h; y++) im.px.copy(px, ((oy + y) * W + ox) * 4, y * w * 4, (y + 1) * w * 4);
	});
	PNG.write(out, W, H, px);
}

exports.sheet = (M) => {
	const bg = [150, 170, 190];
	const views = [0, 35, 90, 145, 180, 250];
	const ims = views.map((yaw) => render(M, { yaw, pitch: 8, w: 360, h: 420, bg, scale: 3.1, cx: 0, cyw: 0, margin: 30 }));
	// a 1.8-block player for scale beside the front view: just a bar
	compose(ims, 3, M.out);
};
exports.face = (M) => {
	const bg = [150, 170, 190];
	const ims = [0, 30, 70].map((yaw) => render(M, { yaw, pitch: 12, w: 360, h: 300, bg, scale: 8, cx: 0, cyw: 50, margin: 10 }));
	compose(ims, 3, M.out);
};
exports.poses = (M) => {
	const bg = [150, 170, 190];
	const ims = [];
	for (const [name, pose, yaw] of M.poses) ims.push(render(M, { yaw: yaw == null ? 35 : yaw, pitch: 8, w: 300, h: 360, bg, scale: 2.6, cx: 0, cyw: 0, margin: 26, pose }));
	compose(ims, 4, M.out);
};
exports.render = render;
exports.compose = compose;
