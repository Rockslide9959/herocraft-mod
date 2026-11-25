// v0.17.7 offline previewer for the Thor weapon item models (or any block-element item model): a small textured
// software rasteriser -- element rotation, per-face UVs, MC-style two-light diffuse shading, z-buffer -- so geometry,
// UVs and the gui / fixed display transforms can be checked without a client.
// Usage (repo root): node scratchpad/preview_thor_weapons_v0177.js <out.png> <model id> [<model id> ...]
// Each model gets one row: side (south face on), back (north), 3/4 front, 3/4 from above, the GUI icon at 8x and the
// GUI icon at 2x (what a GUI-scale-2 slot shows), the item frame (fixed) view.
// v0.17.7 clipping fix: VIEW=head renders head / blade close-ups instead (side, back, 3/4 front, 3/4 back, above, below,
// edge-on); FIGHT=1 paints magenta every pixel where two different elements' faces meet within 0.01 px of depth (the
// spots that z-fight / flicker in game).
const fs = require('fs');
const path = require('path');
const { decode, encode } = require('./pngkit.js');

const ROOT = path.join(__dirname, '../src/main/resources/assets');
const loadModel = (id) => {
	const [ns, p] = id.includes(':') ? id.split(':') : ['projecthero', 'item/' + id];
	return JSON.parse(fs.readFileSync(path.join(ROOT, ns, 'models', p + '.json'), 'utf8'));
};
const texCache = {};
const loadTex = (ref) => {
	if (texCache[ref]) return texCache[ref];
	const [ns, p] = ref.split(':');
	const t = decode(path.join(ROOT, ns, 'textures', p + '.png'));
	return (texCache[ref] = { w: t.width || t.w, h: t.height || t.h, px: t.px || t.data });
};

// ---- small vector / matrix helpers (row-major 4x4) ----
const mul = (a, b) => {
	const r = new Array(16).fill(0);
	for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) for (let k = 0; k < 4; k++) r[i * 4 + j] += a[i * 4 + k] * b[k * 4 + j];
	return r;
};
const I = () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];
const T = (x, y, z) => [1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1];
const S = (x, y, z) => [x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1];
const RX = (d) => { const c = Math.cos(d * Math.PI / 180), s = Math.sin(d * Math.PI / 180); return [1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1]; };
const RY = (d) => { const c = Math.cos(d * Math.PI / 180), s = Math.sin(d * Math.PI / 180); return [c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1]; };
const RZ = (d) => { const c = Math.cos(d * Math.PI / 180), s = Math.sin(d * Math.PI / 180); return [c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]; };
const ap = (m, v) => [m[0] * v[0] + m[1] * v[1] + m[2] * v[2] + m[3], m[4] * v[0] + m[5] * v[1] + m[6] * v[2] + m[7], m[8] * v[0] + m[9] * v[1] + m[10] * v[2] + m[11]];
const apN = (m, v) => { const r = [m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[4] * v[0] + m[5] * v[1] + m[6] * v[2], m[8] * v[0] + m[9] * v[1] + m[10] * v[2]]; const l = Math.hypot(...r) || 1; return r.map((x) => x / l); };

/** MC ItemTransform.apply: translate, rotationXYZ, scale (translation in 1/16 block, as in the JSON). */
function displayMatrix(d) {
	if (!d) return I();
	const t = d.translation || [0, 0, 0], r = d.rotation || [0, 0, 0], s = d.scale || [1, 1, 1];
	return mul(mul(mul(mul(mul(T(t[0], t[1], t[2]), RX(r[0])), RY(r[1])), RZ(r[2])), S(s[0], s[1], s[2])), I());
}

function elementMatrix(e) {
	if (!e.rotation) return I();
	const o = e.rotation.origin, a = e.rotation.angle;
	const R = e.rotation.axis === 'x' ? RX(a) : e.rotation.axis === 'y' ? RY(a) : RZ(a);
	return mul(mul(T(o[0], o[1], o[2]), R), T(-o[0], -o[1], -o[2]));
}

const FACES = {
	north: { n: [0, 0, -1], c: (f, t) => [[t[0], t[1], f[2]], [f[0], t[1], f[2]], [f[0], f[1], f[2]], [t[0], f[1], f[2]]] },
	south: { n: [0, 0, 1], c: (f, t) => [[f[0], t[1], t[2]], [t[0], t[1], t[2]], [t[0], f[1], t[2]], [f[0], f[1], t[2]]] },
	east: { n: [1, 0, 0], c: (f, t) => [[t[0], t[1], t[2]], [t[0], t[1], f[2]], [t[0], f[1], f[2]], [t[0], f[1], t[2]]] },
	west: { n: [-1, 0, 0], c: (f, t) => [[f[0], t[1], f[2]], [f[0], t[1], t[2]], [f[0], f[1], t[2]], [f[0], f[1], f[2]]] },
	up: { n: [0, 1, 0], c: (f, t) => [[f[0], t[1], f[2]], [t[0], t[1], f[2]], [t[0], t[1], t[2]], [f[0], t[1], t[2]]] },
	down: { n: [0, -1, 0], c: (f, t) => [[f[0], f[1], t[2]], [t[0], f[1], t[2]], [t[0], f[1], f[2]], [f[0], f[1], f[2]]] },
};

const L0 = (() => { const v = [0.2, 1, -0.7]; const l = Math.hypot(...v); return v.map((x) => x / l); })();
const L1 = (() => { const v = [-0.2, 1, 0.7]; const l = Math.hypot(...v); return v.map((x) => x / l); })();

/**
 * Renders a model into an RGBA canvas. view = matrix from model space (0..16) to screen space where x right, y up,
 * z toward the viewer; the screen maps [cx - span/2, cx + span/2] to the canvas width.
 */
function render(model, view, W, H, span, bg) {
	const px = Buffer.alloc(W * H * 4);
	for (let i = 0; i < W * H; i++) { px[i * 4] = bg[0]; px[i * 4 + 1] = bg[1]; px[i * 4 + 2] = bg[2]; px[i * 4 + 3] = 255; }
	const zb = new Float32Array(W * H).fill(-1e9);
	const owner = new Int32Array(W * H).fill(-1);
	const fight = new Uint8Array(W * H);
	const FIGHT = !!process.env.FIGHT;
	const sc = W / span;
	const toPx = (p) => [W / 2 + p[0] * sc, H / 2 - p[1] * sc, p[2]];
	const resolve = (t) => { let r = t; for (let k = 0; k < 5 && r && r.startsWith('#'); k++) r = model.textures[r.slice(1)]; return r; };
	for (const [ei, e] of model.elements.entries()) {
		const em = mul(view, elementMatrix(e));
		for (const [fname, face] of Object.entries(e.faces || {})) {
			const F = FACES[fname];
			const corners = F.c(e.from, e.to);
			const sp = corners.map((c) => toPx(ap(em, c)));
			const n = apN(em, F.n);
			if (n[2] <= 1e-4) continue; // back-facing
			// shading in view space as MC does after the pose (approximate)
			const shade = Math.min(1, 0.4 + 0.6 * (Math.max(0, n[0] * L0[0] + n[1] * L0[1] + n[2] * L0[2]) + Math.max(0, n[0] * L1[0] + n[1] * L1[1] + n[2] * L1[2])));
			const tex = loadTex(resolve(face.texture));
			const uv = face.uv || [0, 0, 16, 16];
			// corner order: 0 = (u0,v0), 1 = (u1,v0), 2 = (u1,v1), 3 = (u0,v1) for the auto-UV convention above
			const minX = Math.max(0, Math.floor(Math.min(...sp.map((p) => p[0])))), maxX = Math.min(W - 1, Math.ceil(Math.max(...sp.map((p) => p[0]))));
			const minY = Math.max(0, Math.floor(Math.min(...sp.map((p) => p[1])))), maxY = Math.min(H - 1, Math.ceil(Math.max(...sp.map((p) => p[1]))));
			const [p0, p1, , p3] = sp;
			const ex = [p1[0] - p0[0], p1[1] - p0[1]], ey = [p3[0] - p0[0], p3[1] - p0[1]];
			const det = ex[0] * ey[1] - ex[1] * ey[0];
			if (Math.abs(det) < 1e-9) continue;
			for (let y = minY; y <= maxY; y++) for (let x = minX; x <= maxX; x++) {
				const dx = x + 0.5 - p0[0], dy = y + 0.5 - p0[1];
				const s = (dx * ey[1] - dy * ey[0]) / det, t = (ex[0] * dy - ex[1] * dx) / det;
				if (s < 0 || s > 1 || t < 0 || t > 1) continue;
				const z = p0[2] + s * (p1[2] - p0[2]) + t * (p3[2] - p0[2]);
				const k = y * W + x;
				if (FIGHT && owner[k] >= 0 && owner[k] !== ei && Math.abs(z - zb[k]) < 0.01) fight[k] = 1;
				if (z < zb[k]) continue;
				const u = uv[0] + s * (uv[2] - uv[0]), v = uv[1] + t * (uv[3] - uv[1]);
				const tx = Math.min(tex.w - 1, Math.max(0, Math.floor(u / 16 * tex.w - (uv[2] < uv[0] ? 1e-6 : -1e-6))));
				const ty = Math.min(tex.h - 1, Math.max(0, Math.floor(v / 16 * tex.h - (uv[3] < uv[1] ? 1e-6 : -1e-6))));
				const ti = (ty * tex.w + tx) * 4;
				if (tex.px[ti + 3] < 128) continue;
				if (z > zb[k] + 0.01) fight[k] = 0;
				zb[k] = z;
				owner[k] = ei;
				px[k * 4] = Math.round(tex.px[ti] * shade);
				px[k * 4 + 1] = Math.round(tex.px[ti + 1] * shade);
				px[k * 4 + 2] = Math.round(tex.px[ti + 2] * shade);
			}
		}
	}
	if (FIGHT) for (let k = 0; k < W * H; k++) if (fight[k]) { px[k * 4] = 255; px[k * 4 + 1] = 0; px[k * 4 + 2] = 255; }
	return { W, H, px };
}

function downsample(img, f) { // box filter, for the "real size" icon
	const W = img.W / f, H = img.H / f, px = Buffer.alloc(W * H * 4);
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
		const acc = [0, 0, 0];
		for (let j = 0; j < f; j++) for (let i = 0; i < f; i++) { const k = ((y * f + j) * img.W + x * f + i) * 4; acc[0] += img.px[k]; acc[1] += img.px[k + 1]; acc[2] += img.px[k + 2]; }
		const k = (y * W + x) * 4; px[k] = acc[0] / f / f; px[k + 1] = acc[1] / f / f; px[k + 2] = acc[2] / f / f; px[k + 3] = 255;
	}
	return { W, H, px };
}
function upscale(img, f) {
	const W = img.W * f, H = img.H * f, px = Buffer.alloc(W * H * 4);
	for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) img.px.copy(px, (y * W + x) * 4, ((Math.floor(y / f)) * img.W + Math.floor(x / f)) * 4, ((Math.floor(y / f)) * img.W + Math.floor(x / f)) * 4 + 4);
	return { W, H, px };
}

const CELL = +(process.env.CELL || 256);
const BG = [198, 198, 198];
function row(id) {
	const m = loadModel(id);
	const centre = T(-8, -8, -8);
	const views = [];
	// the raw model, un-baked for readability when the elements carry the -45 degree icon bake: undo it
	// held item models carry the -45 degree icon bake (possibly composed with an element's own rotation); *_thrown don't
	const baked = !id.endsWith('_thrown') && m.elements.some((e) => e.rotation && e.rotation.axis === 'z');
	const unbake = baked ? RZ(45) : I();
	const ys = m.elements.flatMap((e) => [e.from[1], e.to[1]]);
	const yc = (Math.min(...ys) + Math.max(...ys)) / 2, span = Math.max(18, Math.max(...ys) - Math.min(...ys) + 3);
	const raw = (r) => mul(mul(mul(r, T(0, -(yc - 8), 0)), unbake), centre);
	if (process.env.VIEW === 'head') { // close-ups of the head and blade (x 2..12.5, y 15..24.5 of the unbaked model)
		const [hx, hy] = (process.env.HC || "7.2,20").split(",").map(Number); // HC=x,y / HS=span zoom elsewhere
		const hv = (r) => mul(mul(mul(r, T(-(hx - 8), -(hy - 8), 0)), unbake), centre);
		const hs = +(process.env.HS || 11.5);
		return [I(), RY(180), mul(RX(12), RY(-35)), mul(RX(12), RY(145)), mul(RX(60), RY(-20)), mul(RX(-40), RY(30)), mul(RX(5), RY(-80))]
			.map((r) => render(m, hv(r), CELL, CELL, hs, BG));
	}
	views.push(render(m, raw(I()), CELL, CELL, span, BG));
	views.push(render(m, raw(RY(180)), CELL, CELL, span, BG));
	views.push(render(m, raw(mul(RX(12), RY(-35))), CELL, CELL, span, BG));
	views.push(render(m, raw(mul(RX(35), RY(-55))), CELL, CELL, span, BG));
	// GUI: the display transform, then the -0.5 offset, viewed over a 16-unit slot
	const gui = mul(displayMatrix(m.display && m.display.gui), centre);
	const big = render(m, gui, CELL, CELL, 16, [139, 139, 139]);
	views.push(big);
	views.push(upscale(downsample(big, 8), 8)); // 32 px rendered as GUI scale 2 would (16 px * 2), shown at 8x
	const fixed = mul(displayMatrix(m.display && m.display.fixed), centre);
	views.push(render(m, fixed, CELL, CELL, 16, [120, 90, 60]));
	return views;
}

const [, , out, ...ids] = process.argv;
const rows = ids.map(row);
const cols = rows[0].length;
const W = cols * CELL + (cols - 1) * 4, H = rows.length * CELL + (rows.length - 1) * 4;
const o = Buffer.alloc(W * H * 4, 255);
rows.forEach((r, j) => r.forEach((img, i) => {
	for (let y = 0; y < CELL; y++) img.px.copy(o, ((j * (CELL + 4) + y) * W + i * (CELL + 4)) * 4, y * CELL * 4, (y + 1) * CELL * 4);
}));
fs.writeFileSync(out, encode(W, H, o));
console.log('wrote', out);
