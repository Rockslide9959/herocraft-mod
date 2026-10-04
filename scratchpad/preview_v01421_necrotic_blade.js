// Offline preview of the v0.14.21 Necrotic Blade: textured, z-buffered rasteriser that applies element rotations and the
// item display transforms the way ItemRenderer / ItemInHandLayer / ItemInHandRenderer do.
// Usage (repo root): node scratchpad/preview_v01421_necrotic_blade.js <out.png>
const fs = require('fs');
const path = require('path');
const png = require('./pnglib.js');
const A = path.join(__dirname, '../src/main/resources/assets/projecthero/');
const out = process.argv[2] || 'necrotic_preview.png';

const I = () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
const mul = (a, b) => { const o = new Array(16).fill(0); for (let r = 0; r < 4; r++) for (let c = 0; c < 4; c++) for (let k = 0; k < 4; k++) o[r * 4 + c] += a[r * 4 + k] * b[k * 4 + c]; return o; };
const T = (x, y, z) => [1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1];
const Sc = (x, y, z) => [x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1];
const Rx = (d) => { const a = d * Math.PI / 180, c = Math.cos(a), s = Math.sin(a); return [1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1]; };
const Ry = (d) => { const a = d * Math.PI / 180, c = Math.cos(a), s = Math.sin(a); return [c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1]; };
const Rz = (d) => { const a = d * Math.PI / 180, c = Math.cos(a), s = Math.sin(a); return [c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]; };
const chain = (...ms) => ms.reduce((a, b) => mul(a, b), I());
const ap = (m, p) => [0, 1, 2].map(r => m[r * 4] * p[0] + m[r * 4 + 1] * p[1] + m[r * 4 + 2] * p[2] + m[r * 4 + 3]);
const display = (d, left) => {
	if (!d) return I();
	const t = d.translation || [0, 0, 0], r = d.rotation || [0, 0, 0], s = d.scale || [1, 1, 1];
	const f = left ? -1 : 1; // ItemTransform.apply(leftHand): x translation, y/z rotation mirrored
	return chain(T(f * t[0] / 16, t[1] / 16, t[2] / 16), Rx(r[0]), Ry(f * r[1]), Rz(f * r[2]), Sc(...s));
};

const model = JSON.parse(fs.readFileSync(A + 'models/item/necrotic_blade.json', 'utf8'));
const texCache = {};
const tex = (ref) => { const id = ref.split(':')[1]; return texCache[id] || (texCache[id] = png.read(A + `textures/${id}.png`)); };

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

class Canvas {
	constructor(w, h, bg) { this.w = w; this.h = h; this.d = Buffer.alloc(w * h * 4); this.z = new Float32Array(w * h).fill(-1e9); for (let i = 0; i < w * h; i++) { this.d[i * 4] = bg[0]; this.d[i * 4 + 1] = bg[1]; this.d[i * 4 + 2] = bg[2]; this.d[i * 4 + 3] = 255; } }
}
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
// M: model px -> view space (x right, y up, z toward camera, in blocks). proj: view -> screen [x, y, depth].
function drawModel(cv, M, proj, dark) {
	for (const el of model.elements) {
		let R = I();
		if (el.rotation) {
			const o = el.rotation.origin, ang = el.rotation.angle;
			R = chain(T(...o), el.rotation.axis === 'x' ? Rx(ang) : el.rotation.axis === 'y' ? Ry(ang) : Rz(ang), T(-o[0], -o[1], -o[2]));
		}
		const MR = mul(M, R);
		for (const [dir, face] of Object.entries(el.faces)) {
			const texRef = model.textures[face.texture.slice(1)];
			const img = tex(texRef);
			const glow = texRef.endsWith('_glow');
			const [u0, v0, u1, v1] = face.uv.map(q => q * img.w / 16);
			const corners = [[0, 0], [1, 0], [1, 1], [0, 1]];
			const V = corners.map(([s, t]) => ap(MR, faceCorner(dir, el.from, el.to, s, t)));
			const n = ap(MR, NORM[dir]).map((q, k) => q - ap(MR, [0, 0, 0])[k]);
			const len = Math.hypot(...n) || 1;
			// cull faces pointing away from the eye (view-space position towards the camera)
			const c = V[0];
			const toEye = proj.persp ? [-c[0], -c[1], -c[2]] : [0, 0, 1];
			if (n[0] * toEye[0] + n[1] * toEye[1] + n[2] * toEye[2] <= 1e-6) continue;
			const light = glow ? 1 : (0.55 + 0.45 * Math.max(0, (n[2] * 0.6 + n[1] * 0.7 - n[0] * 0.2) / len)) * (dark ? 0.45 : 1);
			const P = V.map(proj);
			const UV = corners.map(([s, t]) => [u0 + s * (u1 - u0), v0 + t * (v1 - v0)]);
			drawQuad(cv, P, UV, (u, v) => {
				const x = Math.min(img.w - 1, Math.max(0, Math.floor(u))), y = Math.min(img.h - 1, Math.max(0, Math.floor(v)));
				const i = (y * img.w + x) * 4; if (img.data[i + 3] < 128) return null;
				return [img.data[i], img.data[i + 1], img.data[i + 2]];
			}, light);
		}
	}
}
function drawBox(cv, from, to, M, proj, col) {
	for (const dir of Object.keys(NORM)) {
		const corners = [[0, 0], [1, 0], [1, 1], [0, 1]];
		const V = corners.map(([s, t]) => ap(M, faceCorner(dir, from, to, s, t)));
		const n = ap(M, NORM[dir]).map((q, k) => q - ap(M, [0, 0, 0])[k]);
		const c = V[0]; const toEye = proj.persp ? [-c[0], -c[1], -c[2]] : [0, 0, 1];
		if (n[0] * toEye[0] + n[1] * toEye[1] + n[2] * toEye[2] <= 1e-6) continue;
		const len = Math.hypot(...n);
		const light = 0.55 + 0.45 * Math.max(0, (n[2] * 0.6 + n[1] * 0.7 - n[0] * 0.2) / len);
		drawQuad(cv, V.map(proj), corners, () => col, light);
	}
}
const ortho = (cx, cy, px) => { const f = (p) => [cx + p[0] * px, cy - p[1] * px, p[2]]; return f; };
const persp = (w, h, fovDeg) => { const f = (h / 2) / Math.tan(fovDeg / 2 * Math.PI / 180); const p = (q) => [w / 2 + f * q[0] / -q[2], h / 2 - f * q[1] / -q[2], 1 / q[2]]; p.persp = true; return p; };

const SZ = 320;
const views = [];
// 1. the GUI sprite, 16x upscaled on a slot-grey background
(function () {
	const img = png.read(A + 'textures/item/necrotic_blade.png');
	const cv = new Canvas(SZ, SZ, [139, 139, 139]);
	const k = SZ / 16;
	for (let y = 0; y < SZ; y++) for (let x = 0; x < SZ; x++) {
		const i = (Math.floor(y / k) * 16 + Math.floor(x / k)) * 4;
		if (img.data[i + 3] < 128) continue;
		const o = (y * SZ + x) * 4; cv.d[o] = img.data[i]; cv.d[o + 1] = img.data[i + 1]; cv.d[o + 2] = img.data[i + 2];
	}
	views.push(cv);
})();
// 2. the 3D model flat-on (gui transform = identity) and 3. / 4. turned to show depth; 5. dark (glow check)
for (const [yaw, pitch, dark] of [[0, 0, false], [35, 20, false], [-60, -15, false], [25, 10, true]]) {
	const cv = new Canvas(SZ, SZ, dark ? [18, 20, 26] : [139, 139, 139]);
	const M = chain(Rx(pitch), Ry(yaw), display(model.display.gui), T(-0.5, -0.5, -0.5), Sc(1 / 16, 1 / 16, 1 / 16));
	drawModel(cv, M, ortho(SZ / 2, SZ / 2, SZ * 0.8), dark);
	views.push(cv);
}
// 6. / 7. third person, right hand, viewed from the front-right and the side
function thirdPerson(camYaw, left) {
	const cv = new Canvas(SZ, SZ, [96, 120, 150]);
	// entity model space (px, y down, face toward -z) -> world (blocks, y up, facing +z)
	const world = chain(T(0, 1.5, 0), Ry(180), Sc(-1 / 16, -1 / 16, 1 / 16));
	const view = chain(Rx(10), Ry(camYaw), T(0, -1.0, 0));
	const VW = mul(view, world);
	const proj = ortho(SZ / 2, SZ * 0.5, SZ / 2.4);
	const f = left ? -1 : 1;
	const armX = -18; // HumanoidModel ArmPose.ITEM: xRot * 0.5 - PI/10
	const arm = chain(T(f * -5, 2, 0), Rx(armX));
	drawBox(cv, [-4, 0, -2], [4, 12, 2], VW, proj, [40, 150, 170]); // body
	drawBox(cv, [-4, -8, -4], [4, 0, 4], VW, proj, [196, 150, 110]); // head
	drawBox(cv, [-2 * f - 1 - (f < 0 ? 0 : 0), -2, -2].map((v, k) => k === 0 ? (f > 0 ? -3 : -1) : v), [f > 0 ? 1 : 3, 10, 2], mul(VW, arm), proj, [196, 150, 110]);
	drawBox(cv, [f > 0 ? -1 : -3, -2, -2], [f > 0 ? 3 : 1, 10, 2], mul(VW, chain(T(f * 5, 2, 0), Rx(0))), proj, [170, 130, 95]);
	drawBox(cv, [-4, 12, -2], [0, 24, 2], VW, proj, [60, 60, 140]);
	drawBox(cv, [0, 12, -2], [4, 24, 2], VW, proj, [60, 60, 140]);
	// ItemInHandLayer: translateToHand, Rx(-90), Ry(180), translate(+-1/16, 0.125, -0.625), then the display transform
	const hand = chain(VW, Sc(16, 16, 16), T(f * -5 / 16, 2 / 16, 0), Rx(armX), Rx(-90), Ry(180), T(f / 16, 0.125, -0.625),
		display(model.display[left ? 'thirdperson_lefthand' : 'thirdperson_righthand'], left), T(-0.5, -0.5, -0.5), Sc(1 / 16, 1 / 16, 1 / 16));
	drawModel(cv, hand, proj);
	return cv;
}
views.push(thirdPerson(150, false), thirdPerson(90, false));
// 8. first person, right hand (ItemInHandRenderer.applyItemArmTransform, no swing / equip), 70 degree FOV
(function () {
	const W = Math.round(SZ * 16 / 9), H = SZ; // 16:9 screen, 70 degree vertical FOV; keep the right SZ columns
	const full = new Canvas(W, H, [96, 120, 150]);
	const M = chain(T(0.56, -0.52, -0.72), display(model.display.firstperson_righthand, false), T(-0.5, -0.5, -0.5), Sc(1 / 16, 1 / 16, 1 / 16));
	drawModel(full, M, persp(W, H, 70));
	const cv = new Canvas(SZ, SZ, [0, 0, 0]);
	for (let y = 0; y < SZ; y++) full.d.copy(cv.d, y * SZ * 4, (y * W + W - SZ) * 4, (y * W + W) * 4);
	views.push(cv);
})();

const COLS = 4, W = SZ * COLS, H = SZ * Math.ceil(views.length / COLS);
const data = Buffer.alloc(W * H * 4, 30);
views.forEach((cv, k) => {
	const ox = (k % COLS) * SZ, oy = Math.floor(k / COLS) * SZ;
	for (let y = 0; y < SZ; y++) cv.d.copy(data, ((oy + y) * W + ox) * 4, y * SZ * 4, (y + 1) * SZ * 4);
});
for (let i = 3; i < data.length; i += 4) data[i] = 255;
png.write(out, { w: W, h: H, data });
console.log('wrote', out);
