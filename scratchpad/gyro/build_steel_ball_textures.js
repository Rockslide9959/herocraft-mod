// v0.17.3 Gyro Zeppeli: the Zeppeli Steel Ball textures (references: docs/reference/gyro/steel_ball_ref_1..5.png).
//
//   node scratchpad/gyro/build_steel_ball_textures.js [--preview <out.png>]
//
// Writes (assets/projecthero/textures/...):
//   entity/gyro/steel_ball.png        512x256 albedo, equirectangular, used for big balls (effects)
//   entity/gyro/steel_ball_small.png  256x128 the same, box-filtered from the 4x master (hand / belt size; less shimmer)
//   entity/gyro/steel_ball_env.png    128x128 environment highlights (additive), looked up by the view-space normal
//   entity/gyro/steel_ball_halo.png   128x128 Golden Spin halo (additive)
//   entity/gyro/steel_ball_glow.png   16x16 white (the Golden Spin rim; the vertex colour carries the fresnel)
//   item/zeppeli_steel_balls.png      32x32 icon: two balls, software-rendered with the same albedo + env
//
// The UV mapping MUST match SteelBallRender: direction d -> lon = atan2(d.x, d.z), u = (lon + pi/2) / 2pi (wrapped),
// v = 0.5 - asin(d.y) / pi. So +Z (the hexagon) is u = 0.25, the back plate (-Z) u = 0.75, and the seam is at -X.
// Feature layout (angles from the +Z face centre):
//   front hexagon plate   circumradius 34 deg, vertices at 0/60/../300 deg round Z (flat sides top and bottom), chamfer 14 %
//   back round plate      radius 20 deg round -Z
//   6 edge slots          one outside each hex side, parallel to it, ends hooked outward (ref 3's slots, ref 5's "U" lines)
//   6 spokes              from each hex vertex, spiralling 35 deg as they run back to a ring round the back plate (the tennis-
//                         ball lines of refs 1, 2)
const fs = require('fs');
const path = require('path');
const png = require('../pnglib.js');

const ROOT = path.resolve(__dirname, '..', '..');
const TEX = path.join(ROOT, 'src/main/resources/assets/projecthero/textures');
const DEG = Math.PI / 180;

// ------------------------------------------------------------------ geometry (shared numbers with SteelBallRender)
const HEX_R = 34 * DEG;              // angular circumradius of the front hexagon
const HEX_RV = Math.tan(HEX_R);      // ... in the gnomonic plane z = 1
const HEX_CHAMFER = 0.86;            // the top face is the hex scaled by this; the band out to 1.0 is the bevel
const BACK_R = 20 * DEG;             // back plate radius
const BACK_CHAMFER = 0.86;
const GROOVE_W = 1.45 * DEG;         // groove half width
const LIP_W = 0.9 * DEG;             // lip band beside a groove

const norm = (v) => { const l = Math.hypot(v[0], v[1], v[2]); return [v[0] / l, v[1] / l, v[2] / l]; };
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const fromPlane = (x, y) => norm([x, y, 1]);                      // gnomonic plane (front) -> direction
const polar = (alpha, theta) => [Math.sin(alpha) * Math.cos(theta), Math.sin(alpha) * Math.sin(theta), Math.cos(alpha)];
function dirFromUV(u, v) {
	const lon = u * 2 * Math.PI - Math.PI / 2, lat = (0.5 - v) * Math.PI;
	return [Math.cos(lat) * Math.sin(lon), Math.sin(lat), Math.cos(lat) * Math.cos(lon)];
}
/** Hexagon "radius" in the gnomonic plane for vertices at k*60 deg: 1 on the boundary of a hex of circumradius 1. */
function hexMetric(x, y) {
	// flat-top hex with vertices on the x axis: apothem directions at 30, 90, 150 deg
	let m = 0;
	for (const a of [30, 90, 150]) m = Math.max(m, Math.abs(x * Math.cos(a * DEG) + y * Math.sin(a * DEG)));
	return m / Math.cos(30 * DEG);
}

// grooves as polylines of unit vectors
const GROOVES = [];
// edge slots
for (let k = 0; k < 6; k++) {
	const tm = (k * 60 + 30) * DEG, n = [Math.cos(tm), Math.sin(tm)], t = [-Math.sin(tm), Math.cos(tm)];
	const s = HEX_RV * Math.cos(30 * DEG) * 1.55, L = HEX_RV * 0.52, L0 = HEX_RV * 0.30;
	const pts = [];
	for (let i = 0; i <= 28; i++) {
		const sp = -L + 2 * L * i / 28;
		const hook = Math.max(0, Math.abs(sp) - L0) / (L - L0);
		const off = s + hook * hook * HEX_RV * 0.38;
		pts.push(fromPlane(n[0] * off + t[0] * sp, n[1] * off + t[1] * sp));
	}
	GROOVES.push(pts);
}
// spokes + back ring
const SPOKE_A0 = HEX_R + 4 * DEG, SPOKE_A1 = Math.PI - BACK_R - 7 * DEG, TWIST = 35 * DEG;
for (let k = 0; k < 6; k++) {
	const pts = [];
	for (let i = 0; i <= 48; i++) {
		const f = i / 48;
		pts.push(polar(SPOKE_A0 + (SPOKE_A1 - SPOKE_A0) * f, k * 60 * DEG + TWIST * Math.pow(f, 1.4)));
	}
	GROOVES.push(pts);
}
{
	const pts = [];
	for (let i = 0; i <= 96; i++) pts.push(polar(SPOKE_A1, i / 96 * 2 * Math.PI));
	GROOVES.push(pts);
}
// bounding caps for speed
const GB = GROOVES.map((pts) => {
	let c = [0, 0, 0];
	for (const p of pts) c = [c[0] + p[0], c[1] + p[1], c[2] + p[2]];
	c = Math.hypot(...c) < 1e-6 ? [0, 0, -1] : norm(c);
	let r = 0;
	for (const p of pts) r = Math.max(r, Math.acos(Math.max(-1, Math.min(1, dot(c, p)))));
	return { c, r };
});

/** Distance (radians) to the nearest groove centre line and the side (+1 / -1) of it. */
function grooveDist(d) {
	let best = 9, side = 1;
	for (let g = 0; g < GROOVES.length; g++) {
		const b = GB[g];
		if (Math.acos(Math.max(-1, Math.min(1, dot(d, b.c)))) > b.r + GROOVE_W + LIP_W + 0.02) continue;
		const pts = GROOVES[g];
		for (let i = 0; i < pts.length - 1; i++) {
			const a = pts[i], c = pts[i + 1];
			const ab = [c[0] - a[0], c[1] - a[1], c[2] - a[2]];
			const ad = [d[0] - a[0], d[1] - a[1], d[2] - a[2]];
			const t = Math.max(0, Math.min(1, dot(ad, ab) / dot(ab, ab)));
			const q = [a[0] + ab[0] * t - d[0], a[1] + ab[1] * t - d[1], a[2] + ab[2] * t - d[2]];
			const dist = Math.hypot(q[0], q[1], q[2]); // chord ~ angle for small values
			if (dist < best) { best = dist; side = Math.sign(dot(cross(a, c), d)) || 1; }
		}
	}
	return [best, side];
}

// ------------------------------------------------------------------ colours
const C = {
	metal: [50, 138, 30],
	metalHi: [88, 172, 40],
	metalLo: [26, 90, 18],
	plate: [76, 162, 36],
	plateEdge: [136, 206, 70],
	crease: [16, 44, 12],
	groove: [12, 34, 9],
	lipHi: [150, 210, 80],
	lipLo: [30, 80, 18],
};
const lerp = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
const smooth = (e0, e1, x) => { const t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0))); return t * t * (3 - 2 * t); };
const hash = (x, y) => { let h = (x * 374761393 + y * 668265263) >>> 0; h = ((h ^ (h >>> 13)) * 1274126177) >>> 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967296; };
function vnoise(d, f) { // cheap smooth 3D value noise from a few sines
	return (Math.sin(d[0] * f + 1.3) * Math.sin(d[1] * f * 1.17 + 0.4) * Math.sin(d[2] * f * 0.93 + 2.1) + 1) / 2;
}

/** Albedo at direction d (+ the texel for flake noise). */
function albedo(d, ix, iy) {
	let col = lerp(C.metalLo, C.metalHi, 0.35 + 0.4 * vnoise(d, 3.0));
	// front hexagon plate (and the dark crease round it)
	if (d[2] > 0.2) {
		const x = d[0] / d[2], y = d[1] / d[2];
		const m = hexMetric(x, y) / HEX_RV;
		if (m < HEX_CHAMFER) {
			col = lerp(C.plate, C.metalHi, 0.25 * (1 - m));
		} else if (m < 1.0) {
			col = lerp(C.plateEdge, C.plate, (m - HEX_CHAMFER) / (1 - HEX_CHAMFER));
		}
		const crease = 1 - smooth(1.0, 1.075, m);
		if (m >= 0.985) col = lerp(col, C.crease, crease);
	}
	// back round plate
	if (d[2] < -0.5) {
		const a = Math.acos(-d[2]) / BACK_R;
		if (a < BACK_CHAMFER) col = lerp(C.plate, C.metalHi, 0.2 * (1 - a));
		else if (a < 1) col = lerp(C.plateEdge, C.plate, (a - BACK_CHAMFER) / (1 - BACK_CHAMFER));
		if (a >= 0.98) col = lerp(col, C.crease, 1 - smooth(1.0, 1.12, a));
	}
	// grooves (not on the plates)
	const onFront = d[2] > 0.2 && hexMetric(d[0] / d[2], d[1] / d[2]) / HEX_RV < 1.0;
	const onBack = d[2] < -0.5 && Math.acos(-d[2]) < BACK_R;
	if (!onFront && !onBack) {
		const [g, side] = grooveDist(d);
		if (g < GROOVE_W + LIP_W) {
			if (g < GROOVE_W) {
				const t = g / GROOVE_W; // 0 centre .. 1 wall
				col = lerp(C.groove, lerp(C.groove, side > 0 ? C.lipLo : C.metalLo, 0.6), smooth(0.55, 1, t));
			} else {
				const t = (g - GROOVE_W) / LIP_W; // 0 at the groove wall .. 1 out on the metal
				col = lerp(side > 0 ? C.lipHi : C.lipLo, col, smooth(0, 1, t));
			}
		}
	}
	// metallic flake (ref 5's pearl paint)
	const fl = (hash(ix, iy) - 0.5) * 10;
	return col.map((v) => Math.max(0, Math.min(255, v + fl)));
}

// ------------------------------------------------------------------ write the albedo (4x master, box filtered)
function renderAlbedo(W, H, ss) {
	const px = Buffer.alloc(W * H * 4);
	for (let y = 0; y < H; y++) {
		for (let x = 0; x < W; x++) {
			let acc = [0, 0, 0];
			for (let sy = 0; sy < ss; sy++) for (let sx = 0; sx < ss; sx++) {
				const u = (x + (sx + 0.5) / ss) / W, v = (y + (sy + 0.5) / ss) / H;
				const c = albedo(dirFromUV(u, v), x * ss + sx, y * ss + sy);
				acc = [acc[0] + c[0], acc[1] + c[1], acc[2] + c[2]];
			}
			px.set([Math.round(acc[0] / ss / ss), Math.round(acc[1] / ss / ss), Math.round(acc[2] / ss / ss), 255], (y * W + x) * 4);
		}
	}
	return px;
}

// ------------------------------------------------------------------ env highlights (additive; black = nothing)
// Indexed like SteelBallRender: u = 0.5 + 0.49 * n.right, v = 0.5 - 0.49 * n.up, n = normal in the ball's view frame.
function envAt(nx, ny) {
	const r2 = nx * nx + ny * ny;
	if (r2 > 1) return [0, 0, 0];
	const nz = Math.sqrt(1 - r2);
	const n = [nx, ny, nz];
	let c = [0, 0, 0];
	const add = (col, k) => { c = [c[0] + col[0] * k, c[1] + col[1] * k, c[2] + col[2] * k]; };
	// key light, upper left: broad lime sheen + a tight white core + a window shape (two panes) like ref 3's reflections
	const key = norm([-0.5, 0.62, 0.6]);
	const h = Math.max(0, dot(n, key));
	add([120, 170, 40], Math.pow(h, 6) * 0.75);
	add([255, 255, 220], Math.pow(h, 60) * 1.0);
	// window: reflect view (0,0,-1) about n -> r; panes in r space
	const r = [2 * n[0] * nz, 2 * n[1] * nz, 2 * nz * nz - 1];
	const wx = (r[0] + 0.55) / 0.22, wy = (r[1] - 0.6) / 0.16;
	if (Math.abs(wx) < 1 && Math.abs(wy) < 1 && Math.abs(wx) > 0.08 && Math.abs(wy) > 0.1) {
		const e = smooth(1, 0.8, Math.abs(wx)) * smooth(1, 0.75, Math.abs(wy));
		add([235, 250, 200], 0.38 * e);
	}
	// sky band across the top, horizon line (ref 3: sky reflections), dark ground below
	add([60, 90, 40], smooth(0.1, 0.75, r[1]) * 0.35);
	// rim light lower right (a cool bounce, like the refs' lighter lower edge)
	const rim = norm([0.55, -0.55, 0.35]);
	add([90, 150, 60], Math.pow(Math.max(0, dot(n, rim)), 10) * 0.6);
	// fresnel sparkle at the very edge, upper side
	add([70, 110, 40], Math.pow(1 - nz, 4) * smooth(-0.3, 0.6, ny) * 0.5);
	return c.map((v) => Math.max(0, Math.min(255, v)));
}
function renderEnv(S) {
	const px = Buffer.alloc(S * S * 4);
	const ss = 3;
	for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
		let acc = [0, 0, 0];
		for (let sy = 0; sy < ss; sy++) for (let sx = 0; sx < ss; sx++) {
			const u = (x + (sx + 0.5) / ss) / S, v = (y + (sy + 0.5) / ss) / S;
			const nx = (u - 0.5) / 0.49, ny = -(v - 0.5) / 0.49;
			const r2 = nx * nx + ny * ny;
			const k = r2 > 1 ? 1 / Math.sqrt(r2) : 1; // past the edge: clamp to the rim value (bilinear safety)
			const c = envAt(nx * k * 0.999, ny * k * 0.999);
			acc = [acc[0] + c[0], acc[1] + c[1], acc[2] + c[2]];
		}
		px.set([acc[0] / 9, acc[1] / 9, acc[2] / 9, 255].map(Math.round), (y * S + x) * 4);
	}
	return px;
}

// ------------------------------------------------------------------ the Golden Spin halo
function renderHalo(S) {
	const px = Buffer.alloc(S * S * 4);
	for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
		const dx = (x + 0.5) / S * 2 - 1, dy = (y + 0.5) / S * 2 - 1;
		const r = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
		// the ball covers r < 0.45 (quad = 2.2 x radius); glow ring just outside it, soft falloff, 10 faint rays
		const ring = Math.exp(-Math.pow((r - 0.47) / 0.07, 2));
		const fall = Math.pow(Math.max(0, 1 - r), 2.2);
		const rays = Math.pow(Math.max(0, Math.cos(a * 5)), 12) * Math.max(0, 1 - r) * smooth(0.4, 0.55, r) * 0.5;
		const k = Math.min(1, 0.65 * ring + 0.5 * fall + rays);
		const edge = smooth(1.0, 0.92, r);
		px.set([255 * k, 196 * k, 70 * k].map((v) => Math.round(v * edge)).concat([255]), (y * S + x) * 4);
	}
	return px;
}

// ------------------------------------------------------------------ software preview / icon renderer (mimics SteelBallRender)
const albedoSmall = { W: 256, H: 128 };
function sampleTex(tex, u, v) {
	u = ((u % 1) + 1) % 1;
	const x = Math.min(tex.W - 1, Math.max(0, Math.floor(u * tex.W))), y = Math.min(tex.H - 1, Math.max(0, Math.floor(v * tex.H)));
	const i = (y * tex.W + x) * 4;
	return [tex.px[i], tex.px[i + 1], tex.px[i + 2]];
}
/** Draws a ball into an RGBA float canvas. rot: [yawDeg about Y (spin), pitchDeg about X]. */
function drawBall(canvas, CW, CH, cx, cy, R, alb, env, yaw, pitch, golden) {
	const cyw = Math.cos(yaw * DEG), syw = Math.sin(yaw * DEG), cp = Math.cos(pitch * DEG), sp = Math.sin(pitch * DEG);
	const ss = 4;
	for (let y = Math.floor(cy - R - 2); y <= cy + R + 2; y++) for (let x = Math.floor(cx - R - 2); x <= cx + R + 2; x++) {
		if (x < 0 || y < 0 || x >= CW || y >= CH) continue;
		let acc = [0, 0, 0], cov = 0;
		for (let sy = 0; sy < ss; sy++) for (let sx = 0; sx < ss; sx++) {
			const nx = (x + (sx + 0.5) / ss - cx) / R, ny = -(y + (sy + 0.5) / ss - cy) / R;
			const r2 = nx * nx + ny * ny;
			if (r2 > 1) continue;
			const nz = Math.sqrt(1 - r2);
			// view normal -> object: undo pitch (about X) then yaw (about Y)
			let o = [nx, ny * cp + nz * sp, -ny * sp + nz * cp];
			o = [o[0] * cyw - o[2] * syw, o[1], o[0] * syw + o[2] * cyw];
			let lon = Math.atan2(o[0], o[2]), u = (lon + Math.PI / 2) / (2 * Math.PI);
			if (u < 0) u += 1;
			const v = 0.5 - Math.asin(Math.max(-1, Math.min(1, o[1]))) / Math.PI;
			const a = sampleTex(alb, u, v);
			const ndv = nz;
			let shade = (0.30 + 0.70 * Math.pow(ndv, 0.55)) * (0.78 + 0.32 * Math.max(0, dot([nx, ny, nz], norm([-0.5, 0.62, 0.6]))));
			let base = a.map((c) => c * shade);
			if (golden) base = [base[0] * 1.15, base[1], base[2] * 0.55];
			const e = sampleTex(env, 0.5 + 0.49 * nx, 0.5 - 0.49 * ny);
			const ek = golden ? 1.2 : 1;
			let col = base.map((c, i) => c + e[i] * ek * (golden ? [1.0, 0.85, 0.35][i] : 1));
			if (golden) { const f = Math.pow(1 - ndv, 2.2); col = col.map((c, i) => c + [255, 200, 70][i] * f * 0.9); }
			acc = acc.map((c, i) => c + col[i]);
			cov++;
		}
		if (!cov) continue;
		const k = cov / (ss * ss), i = (y * CW + x) * 4;
		for (let c = 0; c < 3; c++) canvas[i + c] = canvas[i + c] * (1 - k) + Math.min(255, acc[c] / cov) * k;
		canvas[i + 3] = canvas[i + 3] * (1 - k) + 255 * k;
	}
}
function toBuf(canvas) { return Buffer.from(Array.from(canvas, (v) => Math.max(0, Math.min(255, Math.round(v))))); }

// ------------------------------------------------------------------ main
const t0 = Date.now();
const big = renderAlbedo(512, 256, 2);
const small = renderAlbedo(256, 128, 4);
const env = renderEnv(128);
fs.mkdirSync(path.join(TEX, 'entity/gyro'), { recursive: true });
png.write(path.join(TEX, 'entity/gyro/steel_ball.png'), 512, 256, big);
png.write(path.join(TEX, 'entity/gyro/steel_ball_small.png'), 256, 128, small);
png.write(path.join(TEX, 'entity/gyro/steel_ball_env.png'), 128, 128, env);
png.write(path.join(TEX, 'entity/gyro/steel_ball_halo.png'), 128, 128, renderHalo(128));
png.write(path.join(TEX, 'entity/gyro/steel_ball_glow.png'), 16, 16, Buffer.alloc(16 * 16 * 4, 255));
const BIG = { W: 512, H: 256, px: big }, SMALL = { W: 256, H: 128, px: small }, ENV = { W: 128, H: 128, px: env };

// item icon: 32x32, two balls (the back one smaller and turned), with a dark 1 px outline for readability
{
	const S = 32, cv = new Float32Array(S * S * 4);
	drawBall(cv, S, S, 20.5, 11.5, 9.6, SMALL, ENV, 150, -15, false);
	drawBall(cv, S, S, 12.0, 19.5, 11.4, SMALL, ENV, 20, 10, false);
	const out = toBuf(cv);
	// outline: transparent pixels next to opaque ones get a dark green edge
	const o2 = Buffer.from(out);
	for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
		const i = (y * S + x) * 4;
		if (out[i + 3] > 100) { o2[i + 3] = 255; continue; }
		let near = false;
		for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
			const xx = x + dx, yy = y + dy;
			if (xx >= 0 && yy >= 0 && xx < S && yy < S && out[(yy * S + xx) * 4 + 3] > 100) near = true;
		}
		if (near) o2.set([10, 32, 8, 255], i);
		else if (out[i + 3] > 0) o2.set([10, 32, 8, 255], i);
		else o2.set([0, 0, 0, 0], i);
	}
	png.write(path.join(TEX, 'item/zeppeli_steel_balls.png'), S, S, o2);
}

// optional preview sheet (software): several angles, normal + golden
const pi = process.argv.indexOf('--preview');
if (pi > 0) {
	const S = 1200, H = 520, cv = new Float32Array(S * H * 4);
	for (let i = 0; i < cv.length; i += 4) cv.set([40, 44, 48, 255], i);
	const views = [[0, 0], [35, -20], [90, 0], [180, 15], [-60, 50]];
	views.forEach(([yw, pt], k) => {
		drawBall(cv, S, H, 120 + k * 240, 130, 100, BIG, ENV, yw, pt, false);
		drawBall(cv, S, H, 120 + k * 240, 390, 100, BIG, ENV, yw, pt, true);
	});
	png.write(process.argv[pi + 1], S, H, toBuf(cv));
}
console.log('textures written in', ((Date.now() - t0) / 1000).toFixed(1), 's');
