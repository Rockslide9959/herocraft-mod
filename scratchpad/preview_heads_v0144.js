// Offline rasteriser for the v0.14.4 head models: textured, z-buffered, orthographic. Applies element rotations and
// the item display transforms exactly as ItemRenderer / CustomHeadLayer do, so the GUI icon, the placed block and the
// head worn by a Steve-sized model can be checked without a client.
// Usage (repo root): node scratchpad/preview_heads_v0144.js <out.png>
const fs = require('fs');
const path = require('path');
const png = require('./pnglib.js');
const A = path.join(__dirname, '../src/main/resources/assets/projecthero/');
const out = process.argv[2] || 'heads_preview.png';

// ---------------------------------------------------------------- 4x4 matrices (column vectors, row-major arrays)
const I = () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
const mul = (a, b) => { const o = new Array(16).fill(0); for (let r = 0; r < 4; r++) for (let c = 0; c < 4; c++) for (let k = 0; k < 4; k++) o[r * 4 + c] += a[r * 4 + k] * b[k * 4 + c]; return o; };
const T = (x, y, z) => [1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1];
const Sc = (x, y, z) => [x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1];
const Rx = (d) => { const a = d * Math.PI / 180, c = Math.cos(a), s = Math.sin(a); return [1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1]; };
const Ry = (d) => { const a = d * Math.PI / 180, c = Math.cos(a), s = Math.sin(a); return [c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1]; };
const Rz = (d) => { const a = d * Math.PI / 180, c = Math.cos(a), s = Math.sin(a); return [c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]; };
const chain = (...ms) => ms.reduce((a, b) => mul(a, b), I());
const ap = (m, p) => [0, 1, 2].map(r => m[r * 4] * p[0] + m[r * 4 + 1] * p[1] + m[r * 4 + 2] * p[2] + m[r * 4 + 3]);
// ItemTransform.apply: translate(t), rotationXYZ(rx, ry, rz) (= Rx * Ry * Rz), scale(s)
const display = (d) => d ? chain(T(d.translation[0] / 16, d.translation[1] / 16, d.translation[2] / 16), Rx(d.rotation[0]), Ry(d.rotation[1]), Rz(d.rotation[2]), Sc(...d.scale)) : I();

function loadModel(id) {
	const m = JSON.parse(fs.readFileSync(A + `models/${id}.json`, 'utf8'));
	if (m.parent && m.parent.startsWith('projecthero:')) {
		const p = loadModel(m.parent.slice('projecthero:'.length));
		return { ...p, ...m, textures: { ...p.textures, ...(m.textures || {}) }, display: { ...(p.display || {}), ...(m.display || {}) }, elements: m.elements || p.elements };
	}
	return m;
}
const texCache = {};
const tex = (ref) => { const id = ref.split(':')[1]; return texCache[id] || (texCache[id] = png.read(A + `textures/${id}.png`)); };

// face corners (s, t) -> position, in vanilla's default-UV orientation
function faceCorner(dir, f, t, s, v) {
	const [x0, y0, z0] = f, [x1, y1, z1] = t;
	const y = y1 - v * (y1 - y0);
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

// ---------------------------------------------------------------- scene / raster
class Canvas {
	constructor(w, h, bg) { this.w = w; this.h = h; this.d = Buffer.alloc(w * h * 4); this.z = new Float32Array(w * h).fill(-1e9); for (let i = 0; i < w * h; i++) { this.d[i * 4] = bg[0]; this.d[i * 4 + 1] = bg[1]; this.d[i * 4 + 2] = bg[2]; this.d[i * 4 + 3] = 255; } }
}
// quad: 4 screen points [x, y, depth] + per-corner (u, v) in texels; sampler(u, v) -> rgba or null
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
			const z = w0 * p0[2] + w1 * p1[2] + w2 * p2[2];
			const i = y * cv.w + x;
			if (z <= cv.z[i]) continue;
			const u = w0 * UV[a][0] + w1 * UV[b][0] + w2 * UV[c][0], v = w0 * UV[a][1] + w1 * UV[b][1] + w2 * UV[c][1];
			const col = sampler(u, v); if (!col) continue;
			cv.z[i] = z;
			cv.d[i * 4] = Math.min(255, col[0] * shade); cv.d[i * 4 + 1] = Math.min(255, col[1] * shade); cv.d[i * 4 + 2] = Math.min(255, col[2] * shade);
		}
	}
}

const TINT = [95, 242, 255];
// draw a block model through matrix M (block-model px units -> screen [x, y, depth]); cull back faces
function drawModel(cv, model, M, opts = {}) {
	for (const el of model.elements) {
		let R = I();
		if (el.rotation) {
			const o = el.rotation.origin, ang = el.rotation.angle;
			R = chain(T(...o), el.rotation.axis === 'x' ? Rx(ang) : el.rotation.axis === 'y' ? Ry(ang) : Rz(ang), T(-o[0], -o[1], -o[2]));
		}
		const MR = mul(M, R);
		for (const [dir, face] of Object.entries(el.faces)) {
			const img = tex(model.textures[face.texture.slice(1)]);
			const [u0, v0, u1, v1] = face.uv.map(q => q * img.w / 16);
			const corners = [[0, 0], [1, 0], [1, 1], [0, 1]];
			const P = corners.map(([s, t]) => ap(MR, faceCorner(dir, el.from, el.to, s, t)));
			const UV = corners.map(([s, t]) => [u0 + s * (u1 - u0), v0 + t * (v1 - v0)]);
			// back-face cull by screen winding (the quad's outward normal after transform)
			const n = ap(MR, NORM[dir]).map((q, k) => q - ap(MR, [0, 0, 0])[k]);
			if (!opts.noCull && n[2] <= 1e-6) continue;
			const glow = face.tintindex === 0;
			const len = Math.hypot(...n) || 1;
			const light = glow ? 1 : 0.5 + 0.5 * Math.max(0, (n[2] * 0.75 - n[1] * 0.55 + n[0] * -0.2) / len);
			drawQuad(cv, P, UV, (u, v) => {
				const x = Math.min(img.w - 1, Math.max(0, Math.floor(u))), y = Math.min(img.h - 1, Math.max(0, Math.floor(v)));
				const i = (y * img.w + x) * 4; if (img.data[i + 3] < 128) return null;
				const c = [img.data[i], img.data[i + 1], img.data[i + 2]];
				return glow ? c.map((q, k) => q * TINT[k] / 255) : c;
			}, light);
		}
	}
}
// a plain box (Steve), in head-model space px, coloured per face; the front gets two eye pixels + a mouth
function drawBox(cv, from, to, M, col, front) {
	const el = { from, to, faces: {} };
	for (const dir of Object.keys(NORM)) {
		const corners = [[0, 0], [1, 0], [1, 1], [0, 1]];
		const P = corners.map(([s, t]) => ap(M, faceCorner(dir, from, to, s, t)));
		const n = ap(M, NORM[dir]).map((q, k) => q - ap(M, [0, 0, 0])[k]);
		if (n[2] <= 1e-6) continue;
		const len = Math.hypot(...n);
		const light = 0.5 + 0.5 * Math.max(0, (n[2] * 0.75 - n[1] * 0.55 - n[0] * 0.2) / len);
		drawQuad(cv, P, corners.map(([s, t]) => [s, t]), (s, t) => (front && dir === front && ((t > 0.45 && t < 0.6 && (s > 0.15 && s < 0.35 || s > 0.65 && s < 0.85)) || (t > 0.75 && t < 0.85 && s > 0.35 && s < 0.65))) ? [40, 30, 60] : col, light);
	}
}
void 0;

// ---------------------------------------------------------------- views
const SZ = 256;
function gui(id) {
	const cv = new Canvas(SZ, SZ, [139, 139, 139]);
	const m = loadModel(`item/${id}`);
	// screen: centre, 1 block = SZ px, y down; GUI looks down -z
	const M = chain(T(SZ / 2, SZ / 2, 0), Sc(SZ, -SZ, SZ), display(m.display.gui), T(-0.5, -0.5, -0.5), Sc(1 / 16, 1 / 16, 1 / 16));
	drawModel(cv, m, M);
	return cv;
}
// the head worn: head-model space (neck pivot, y down, -z = face) -> view (x right, y up, +z toward camera)
function worn(id, yaw, pitch, withHead) {
	const cv = new Canvas(SZ, SZ, [70, 90, 110]);
	const m = loadModel(`item/${id}`);
	const px = SZ / 1.6; // pixels per block
	const view = chain(T(SZ / 2, SZ * 0.62, 0), Sc(px, -px, px), Rx(pitch), Ry(yaw), Rx(180));
	const head = chain(T(0, -0.25, 0), Ry(180), Sc(0.625, -0.625, -0.625), display(m.display.head), T(-0.5, -0.5, -0.5), Sc(1 / 16, 1 / 16, 1 / 16));
	drawModel(cv, m, mul(view, head));
	const u = Sc(1 / 16, 1 / 16, 1 / 16);
	if (withHead) drawBox(cv, [-4, -8, -4], [4, 0, 4], mul(view, u), [196, 150, 110], 'north');
	drawBox(cv, [-4, 0, -2], [4, 12, 2], mul(view, u), [40, 150, 170]); // body
	drawBox(cv, [-8, 0, -2], [-4, 12, 2], mul(view, u), [196, 150, 110]); // right arm (model -x = wearer's right)
	drawBox(cv, [4, 0, -2], [8, 12, 2], mul(view, u), [150, 110, 80]); // left arm, darker so sides read
	return cv;
}
function placed(id, yaw) {
	const cv = new Canvas(SZ, SZ, [96, 110, 96]);
	const m = loadModel(`block/${id}`);
	const px = SZ * 0.7;
	const M = chain(T(SZ / 2, SZ * 0.5, 0), Sc(px, -px, px), Rx(25), Ry(yaw), T(-0.5, -0.5, -0.5), Sc(1 / 16, 1 / 16, 1 / 16));
	drawModel(cv, m, M);
	// outline the block cell floor + back wall so the placement reads
	return cv;
}

const views = [
	gui('final_boss_trophy'), gui('boss_trophy'), worn('final_boss_trophy', 0, 0, true), worn('boss_trophy', 0, 0, true),
	worn('final_boss_trophy', 35, 15, true), worn('boss_trophy', 35, 15, true), worn('final_boss_trophy', 90, 0, true), worn('boss_trophy', 90, 0, true),
	worn('final_boss_trophy', 200, 10, true), worn('boss_trophy', 200, 10, true),
	placed('grave_champion_head', 150), placed('empowered_zombie_head', 150), placed('grave_champion_wall_head', 200), placed('empowered_zombie_wall_head', 200),
];
const COLS = 4, W = SZ * COLS, H = SZ * Math.ceil(views.length / COLS);
const data = Buffer.alloc(W * H * 4, 30);
views.forEach((cv, k) => {
	const ox = (k % COLS) * SZ, oy = Math.floor(k / COLS) * SZ;
	for (let y = 0; y < SZ; y++) cv.d.copy(data, ((oy + y) * W + ox) * 4, y * SZ * 4, (y + 1) * SZ * 4);
});
for (let i = 3; i < data.length; i += 4) data[i] = 255;
png.write(out, { w: W, h: H, data });
// a true-size 16x16 GUI icon too (nearest downsample of the GUI view), upscaled 8x
for (const [k, id] of [[0, 'final_boss_trophy'], [1, 'boss_trophy']]) {
	const cv = views[k]; const small = Buffer.alloc(16 * 16 * 4);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const s = ((y * 16 + 8) * SZ + x * 16 + 8) * 4; cv.d.copy(small, (y * 16 + x) * 4, s, s + 4); small[(y * 16 + x) * 4 + 3] = 255; }
	png.write(out.replace('.png', `_${id}_icon.png`), png.upscale({ w: 16, h: 16, data: small }, 8));
}
console.log('wrote', out);
