// Offline preview of the v0.15.1 Stark Furnace / Smelter / Smoker block models: textured, z-buffered rasteriser with
// vanilla face shading (up 1.0, N/S 0.8, E/W 0.6, down 0.5). Writes, per block, a sheet of front + 3/4 views unlit and
// lit (lit = dim ambient, glow faces fullbright like shade:false under block light 14) plus the inventory (GUI) view,
// and an overview of all three. Usage (repo root): node scratchpad/preview_v0151_stark_furnaces.js <outDir>
const fs = require('fs');
const path = require('path');
const png = require('./pnglib.js');
const A = path.join(__dirname, '../src/main/resources/assets/projecthero/');
const outDir = process.argv[2] || path.join(__dirname, 'shots_v0151');
fs.mkdirSync(outDir, { recursive: true });

const I = () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
const mul = (a, b) => { const o = new Array(16).fill(0); for (let r = 0; r < 4; r++) for (let c = 0; c < 4; c++) for (let k = 0; k < 4; k++) o[r * 4 + c] += a[r * 4 + k] * b[k * 4 + c]; return o; };
const T = (x, y, z) => [1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1];
const Sc = (x, y, z) => [x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1];
const Rx = (d) => { const a = d * Math.PI / 180, c = Math.cos(a), s = Math.sin(a); return [1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1]; };
const Ry = (d) => { const a = d * Math.PI / 180, c = Math.cos(a), s = Math.sin(a); return [c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1]; };
const chain = (...ms) => ms.reduce((a, b) => mul(a, b), I());
const ap = (m, p) => [0, 1, 2].map(r => m[r * 4] * p[0] + m[r * 4 + 1] * p[1] + m[r * 4 + 2] * p[2] + m[r * 4 + 3]);

const texCache = {};
const tex = (ref) => { const id = ref.split(':')[1]; return texCache[id] || (texCache[id] = png.read(A + `textures/${id}.png`)); };
function faceCorner(dir, f, t, s, v) {
	const [x0, y0, z0] = f, [x1, y1, z1] = t; const y = y1 - v * (y1 - y0);
	switch (dir) {
		case 'north': return [x1 - s * (x1 - x0), y, z0];
		case 'south': return [x0 + s * (x1 - x0), y, z1];
		case 'east': return [x1, y, z1 - s * (z1 - z0)];
		case 'west': return [x0, y, z0 + s * (z1 - z0)];
		case 'up': return [x0 + s * (x1 - x0), y1, z0 + v * (z1 - z0)];
		case 'down': return [x0 + s * (x1 - x0), y0, z1 - v * (z1 - z0)];
	}
}
const NORM = { north: [0, 0, -1], south: [0, 0, 1], east: [1, 0, 0], west: [-1, 0, 0], up: [0, 1, 0], down: [0, -1, 0] };
const SHADE = { up: 1, down: 0.5, north: 0.8, south: 0.8, east: 0.6, west: 0.6 };

class Canvas { constructor(w, h, bg) { this.w = w; this.h = h; this.d = Buffer.alloc(w * h * 4); this.z = new Float32Array(w * h).fill(-1e9); for (let i = 0; i < w * h; i++) { this.d[i * 4] = bg[0]; this.d[i * 4 + 1] = bg[1]; this.d[i * 4 + 2] = bg[2]; this.d[i * 4 + 3] = 255; } } }
function drawQuad(cv, P, UV, sampler, shade) {
	for (const [a, b, c] of [[0, 1, 2], [0, 2, 3]]) {
		const p0 = P[a], p1 = P[b], p2 = P[c];
		const minx = Math.max(0, Math.floor(Math.min(p0[0], p1[0], p2[0]))), maxx = Math.min(cv.w - 1, Math.ceil(Math.max(p0[0], p1[0], p2[0])));
		const miny = Math.max(0, Math.floor(Math.min(p0[1], p1[1], p2[1]))), maxy = Math.min(cv.h - 1, Math.ceil(Math.max(p0[1], p1[1], p2[1])));
		const den = (p1[1] - p2[1]) * (p0[0] - p2[0]) + (p2[0] - p1[0]) * (p0[1] - p2[1]);
		if (Math.abs(den) < 1e-9) continue;
		for (let y = miny; y <= maxy; y++) for (let x = minx; x <= maxx; x++) {
			const px = x + 0.5, py = y + 0.5;
			const w0 = ((p1[1] - p2[1]) * (px - p2[0]) + (p2[0] - p1[0]) * (py - p2[1])) / den;
			const w1 = ((p2[1] - p0[1]) * (px - p2[0]) + (p0[0] - p2[0]) * (py - p2[1])) / den;
			const w2 = 1 - w0 - w1;
			if (w0 < -1e-6 || w1 < -1e-6 || w2 < -1e-6) continue;
			const z = w0 * p0[2] + w1 * p1[2] + w2 * p2[2]; const i = y * cv.w + x;
			if (z <= cv.z[i]) continue;
			const u = w0 * UV[a][0] + w1 * UV[b][0] + w2 * UV[c][0], v = w0 * UV[a][1] + w1 * UV[b][1] + w2 * UV[c][1];
			const col = sampler(u, v); cv.z[i] = z;
			for (let k = 0; k < 3; k++) cv.d[i * 4 + k] = Math.min(255, col[k] * shade[k]);
		}
	}
}
function drawModel(cv, model, M, proj, lit) {
	for (const el of model.elements) {
		for (const [dir, face] of Object.entries(el.faces)) {
			const img = tex(model.textures[face.texture.slice(1)]);
			const [u0, v0, u1, v1] = face.uv.map(q => q * img.w / 16);
			const corners = [[0, 0], [1, 0], [1, 1], [0, 1]];
			const V = corners.map(([s, t]) => ap(M, faceCorner(dir, el.from, el.to, s, t)));
			const o = ap(M, [0, 0, 0]); const n = ap(M, NORM[dir]).map((q, k) => q - o[k]);
			if (n[2] <= 1e-6) continue; // ortho camera looks down -z
			const base = el.shade === false ? 1 : SHADE[dir];
			// lit: glow faces at full brightness, the rest under warm block light ~14 at night
			const shade = lit ? (el.shade === false ? [1, 1, 1] : [base * 0.62, base * 0.55, base * 0.5]) : [base, base, base];
			drawQuad(cv, V.map(proj), corners.map(([s, t]) => [u0 + s * (u1 - u0), v0 + t * (v1 - v0)]), (u, v) => {
				const x = Math.min(img.w - 1, Math.max(0, Math.floor(u))), y = Math.min(img.h - 1, Math.max(0, Math.floor(v)));
				const i = (y * img.w + x) * 4; return [img.data[i], img.data[i + 1], img.data[i + 2]];
			}, shade);
		}
	}
}
const ortho = (cx, cy, px) => (p) => [cx + p[0] * px, cy - p[1] * px, p[2]];
const SZ = 360;
function view(model, yaw, pitch, lit, scale = 0.62) {
	const cv = new Canvas(SZ, SZ, lit ? [16, 18, 24] : [150, 170, 190]);
	// block centred at the origin; yaw 180 puts the north (front) face towards the camera
	const M = chain(Rx(pitch), Ry(yaw), T(-0.5, -0.5, -0.5), Sc(1 / 16, 1 / 16, 1 / 16));
	drawModel(cv, model, M, ortho(SZ / 2, SZ / 2 + 10, SZ * scale), lit);
	return cv;
}
function gui(model) {
	const cv = new Canvas(SZ, SZ, [139, 139, 139]);
	const d = { rotation: [30, 225, 0], scale: [0.625, 0.625, 0.625] }; // minecraft:block/block gui transform
	const M = chain(Rx(d.rotation[0]), Ry(d.rotation[1]), Sc(...d.scale), T(-0.5, -0.5, -0.5), Sc(1 / 16, 1 / 16, 1 / 16));
	drawModel(cv, model, M, ortho(SZ / 2, SZ / 2, SZ), false);
	return cv;
}
function sheet(views, cols, file) {
	const W = SZ * cols, H = SZ * Math.ceil(views.length / cols); const data = Buffer.alloc(W * H * 4, 30);
	views.forEach((cv, k) => { const ox = (k % cols) * SZ, oy = Math.floor(k / cols) * SZ; for (let y = 0; y < SZ; y++) cv.d.copy(data, ((oy + y) * W + ox) * 4, y * SZ * 4, (y + 1) * SZ * 4); });
	for (let i = 3; i < data.length; i += 4) data[i] = 255;
	png.write(file, { w: W, h: H, data }); console.log('wrote', file);
}
const load = id => JSON.parse(fs.readFileSync(A + `models/block/${id}.json`, 'utf8'));
const overview = [];
for (const kind of ['furnace', 'smelter', 'smoker']) {
	const off = load('stark_' + kind), on = load('stark_' + kind + '_on');
	const v = [view(off, 180, 0, false), view(off, 215, 25, false), view(on, 180, 0, true), view(on, 215, 25, true), view(off, 30, 25, false), gui(off)];
	sheet(v, 3, path.join(outDir, `F_${kind}.png`));
	overview.push(v[1], v[3]);
}
sheet(overview, 2, path.join(outDir, 'F_overview.png'));
