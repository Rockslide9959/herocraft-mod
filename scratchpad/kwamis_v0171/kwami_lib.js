// v0.17.1: shared pieces for the Kwami remodel (Tikki, Plagg, Trixx, Nooroo, Kaalki, Wayzz), on the same plan as
// Sass (scratchpad/viperion/gen_sass.js) and its library (scratchpad/viperion/lib_concept_models.js):
//   - a chibi base rig: a big rounded blob head (7-box rounded box), a tiny rounded body, small limbs; every Kwami keeps
//     the bone names it already had (root, body, head, arm_r/l, leg_r/l + its own extras) so the code and clips work;
//   - painters: big eyes with sclera, a shaded iris, pupils (round or slit), highlights, outline and lashes, lids for a
//     half-lidded look; small mouths; soft multi-tone skin shading from the library's fbm / mix helpers;
//   - the shared clip code (idle / wave / eat / tired / into_jewel / pop_out) with a per-Kwami float pose and extras;
//   - writers for the geo, texture, clips and the concept sheet.
'use strict';
const fs = require('fs');
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const { Model, mix, mul, fbm, clamp } = L;

const RES = path.join(__dirname, '..', '..', 'src', 'main', 'resources');
const A = path.join(RES, 'assets', 'projecthero');
const mk = d => (fs.mkdirSync(d, { recursive: true }), d);
const writeJson = (f, o) => { mk(path.dirname(f)); fs.writeFileSync(f, JSON.stringify(o, null, '\t') + '\n'); };
const writePng = (f, w, h, px) => { mk(path.dirname(f)); fs.writeFileSync(f, L.encodePng(w, h, px)); };

// ================================================================ geometry
// A rounded box from 7 overlapping boxes: three "face" boxes, three "edge" boxes and a corner box, so every edge and
// corner is chamfered twice (reads as a soft blob). r = rounding per axis [rx, ry, rz] (or one number).
function roundBox(M, b, ctr, size, r, mat, opts = {}) {
	const [w, h, d] = size;
	const [rx, ry, rz] = Array.isArray(r) ? r : [r, r, r];
	const a = 0.3, q = 0.42;
	const ins = [[0, ry, rz], [rx, 0, rz], [rx, ry, 0], [a * rx, a * ry, rz], [a * rx, ry, a * rz], [rx, a * ry, a * rz], [q * rx, q * ry, q * rz]];
	const out = [];
	ins.forEach(([ix, iy, iz], i) => out.push(M.cbox(b, ctr, [w - 2 * ix, h - 2 * iy, d - 2 * iz], mat, Object.assign({}, opts, { seed: (opts.seed || 0) + i }))));
	return out;
}
// Sass's cheaper 4-box blob, for the small parts
function blob(M, b, ctr, size, k, mat, opts = {}) {
	const [w, h, d] = size;
	const kk = Array.isArray(k) ? k : [k, k, k];
	M.cbox(b, ctr, [w, h - 2 * kk[1], d - 2 * kk[2]], mat, opts);
	M.cbox(b, ctr, [w - 2 * kk[0], h, d - 2 * kk[2]], mat, opts);
	M.cbox(b, ctr, [w - 2 * kk[0], h - 2 * kk[1], d], mat, opts);
	M.cbox(b, ctr, [w - kk[0] * 0.9, h - kk[1] * 0.9, d - kk[2] * 0.9], mat, opts);
}

// The base: legs stand on y = 0. cfg: { head: {c, size, r}, body: {c, size, r}, arm: {len, w}, leg: {len, w}, mats }
const BASE = {
	head: { c: [0, 10.4, -0.2], size: [10.4, 9.0, 8.6], r: [1.4, 2.4, 1.9] },
	body: { c: [0, 4.1, 0.1], size: [3.8, 4.0, 3.2], r: 0.9 },
	shoulderY: 5.6, shoulderX: 2.0, hipX: 0.95, hipY: 2.3,
};
function base(M, cfg = {}) {
	const H = Object.assign({}, BASE.head, cfg.head || {});
	const Bd = Object.assign({}, BASE.body, cfg.body || {});
	const sy = cfg.shoulderY || BASE.shoulderY, sx = cfg.shoulderX || BASE.shoulderX;
	const hx = cfg.hipX || BASE.hipX, hy = cfg.hipY || BASE.hipY;
	const mats = Object.assign({ head: 'head', body: 'body', limb: 'limb', paw: 'paw', foot: 'foot' }, cfg.mats || {});
	M.bone('root', null, [0, 0, 0]);
	const body = M.bone('body', 'root', [0, 2.4, 0]);
	(Bd.blob4 ? blob : roundBox)(M, body, Bd.c, Bd.size, Bd.r, mats.body, { seed: 10 });
	const head = M.bone('head', 'body', [0, Bd.c[1] + Bd.size[1] / 2 - 0.2, 0]);
	roundBox(M, head, H.c, H.size, H.r, mats.head, { seed: 30 });
	const armLen = cfg.armLen || 2.5, aw = cfg.armW || 1.1, legLen = cfg.legLen || hy, lw = cfg.legW || 1.2;
	for (const s of [-1, 1]) {
		const n = s < 0 ? 'r' : 'l';
		const arm = M.bone(`arm_${n}`, 'body', [s * sx, sy, 0], [0, 0, -s * 18]);
		const ax = s * (sx + aw / 2 - 0.15);
		M.box(arm, ax - aw / 2, sy - armLen + 0.35, -aw / 2, ax + aw / 2, sy + 0.35, aw / 2, mats.limb, { seed: 50 + s });
		M.box(arm, ax - aw / 2 - 0.08, sy - armLen + 0.05, -aw / 2 - 0.08, ax + aw / 2 + 0.08, sy - armLen + 0.85, aw / 2 + 0.08, mats.paw, { seed: 52 + s });
		const leg = M.bone(`leg_${n}`, 'body', [s * hx, hy + 0.1, 0]);
		M.box(leg, s * hx - lw / 2, 0.5, -lw / 2, s * hx + lw / 2, hy + 0.4, lw / 2, mats.limb, { seed: 60 + s });
		M.box(leg, s * hx - lw / 2 - 0.1, 0, -lw / 2 - 0.25, s * hx + lw / 2 + 0.1, 0.8, lw / 2 + 0.1, mats.foot, { seed: 62 + s });
	}
	M.A.H = H; M.A.Bd = Bd;
	return { head, body, H, Bd };
}

// ================================================================ paint helpers
const inEll = (x, y, cx, cy, rx, ry) => ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2;
// soft multi-tone skin: two-tone fbm patches, a top-light / bottom-dark gradient, darker undersides, soft borders
function skin(base, p, f, info, o = {}) {
	const lo = o.y0 == null ? 0 : o.y0, hi = o.y1 == null ? 16 : o.y1;
	const g = clamp((p[1] - lo) / Math.max(0.01, hi - lo), 0, 1);
	const light = o.light || mix(base, [255, 255, 255], 0.22), dark = o.dark || mul(base, 0.72);
	let col = mix(dark, base, 0.55 + 0.45 * g);
	col = mix(col, light, 0.35 * Math.max(0, g - 0.55) * 2.2);
	const n = fbm(p[0] * 0.55 + (o.seed || 0), p[1] * 0.55, p[2] * 0.55, 3);
	col = mix(col, n > 0.5 ? light : dark, Math.abs(n - 0.5) * (o.tone == null ? 0.35 : o.tone));
	col = mul(col, 0.97 + 0.06 * fbm(p[0] * 2.3, p[1] * 2.3 + 7, p[2] * 2.3, 2));
	if (f === 'down') col = mul(col, 0.8);
	if (info && info.ePx < 0.14) col = mul(col, 0.93);
	return col;
}
// 3D direction spot test (for Tikki's spots etc.): is p within ang radians of dir, seen from centre c?
function spot(p, c, dir, ang) {
	const v = L.vnorm([p[0] - c[0], p[1] - c[1], p[2] - c[2]]), d = L.vnorm(dir);
	return Math.acos(clamp(L.vdot(v, d), -1, 1)) < ang;
}

// The eye. E: { x, y, rx, ry, tilt, sclera|null, iris:[dark, mid, light], irx, iry, ix, iy, pupil:'round'|'slit', pr,
// lid (0..1 fraction of the eye covered from the top), lidCol, lidTilt, lashes: [[angle deg, len, width]], outline,
// lashW (extra outline thickness on the top), hl: [[x, y, r]] highlights in eye units (x outward) }
function eye(x, y, E) {
	for (const s of [-1, 1]) {
		const dx = s * (x - s * E.x), dy = y - E.y;
		const a = E.tilt || 0;
		const u = (dx * Math.cos(a) + dy * Math.sin(a)) / E.rx, v = (-dx * Math.sin(a) + dy * Math.cos(a)) / E.ry;
		const rr = Math.hypot(u, v);
		const OUT = E.outline || [18, 14, 24];
		// lashes: little flicks off the upper outer rim
		if (rr >= 0.97 && E.lashes) {
			const ang = Math.atan2(v, u) / L.DEG;
			for (const [la, len, lw] of E.lashes) {
				const t = (rr - 1) / len;
				if (t < -0.05 || t > 1) continue;
				const bend = la - 22 * t * t; // flicks curl outward/down a little
				if (Math.abs(ang - bend) < lw * (1 - 0.8 * Math.max(0, t)) / Math.max(0.6, rr)) return OUT;
			}
		}
		if (E.brow) { // a small dash above the eye
			const [bx, by, bw, bh, bt] = E.brow;
			const uu = s * (x - s * E.x) - bx * E.rx, vv = y - E.y - by * E.ry;
			const vr = vv + uu * (bt || 0);
			if (Math.abs(uu) < bw * E.rx && Math.abs(vr) < bh * (1 - 0.5 * (Math.abs(uu) / (bw * E.rx)) ** 2)) return E.browCol || OUT;
		}
		if (rr >= 1) continue;
		// the lid (half-lidded looks): skin over the top of the eye, a dark lash line under it
		if (E.lid) {
			const lt = 1 - 2 * E.lid + (E.lidTilt || 0) * u + (E.lidCurve || 0) * (1 - u * u);
			if (v > lt + 0.16) return E.lidCol;
			if (v > lt - 0.02) return OUT;
		}
		const topW = (E.ow || 0.1) + (E.lashW || 0.12) * Math.max(0, v);
		if (rr > 1 - topW) return OUT;
		// iris
		const iu = (u - (E.ix || 0)) / (E.irx || 0.7), iv = (v - (E.iy || 0)) / (E.iry || 0.75);
		const ir = Math.hypot(iu, iv);
		let col;
		if (!E.sclera || ir < 1) {
			const [d0, m0, l0] = E.iris;
			const k = clamp(0.5 - iv * 0.55, 0, 1);
			col = k < 0.5 ? mix(d0, m0, k * 2) : mix(m0, l0, (k - 0.5) * 2);
			if (E.sclera && ir > 0.86) col = mix(col, d0, 0.7);            // a darker iris ring
			if (iv < -0.25 && ir > 0.45 && ir < 0.82) col = mix(col, l0, 0.35); // the lower glow
			const pr = E.pr == null ? 0.42 : E.pr;
			if (E.pupil === 'slit') { // upright in the face, whatever the eye's tilt
				const sx = dx / E.rx - (E.ix || 0), sy = dy / E.ry;
				if (Math.abs(sx) < pr * (1 - 0.75 * sy * sy)) col = [12, 16, 10];
			}
			else if (Math.hypot(iu * 1.05, iv) < pr) col = E.pupilCol || [16, 14, 30];
			if (!E.sclera && v > 0.55) col = mix(col, mul(E.iris[0], 0.6), 0.5); // the lid's shadow
		} else {
			col = E.sclera;
			if (v > 0.5) col = mix(col, mul(E.sclera, 0.78), (v - 0.5) * 1.6);   // shadow under the upper lid
		}
		for (const [hx, hy, hr] of (E.hl || [[0.28, 0.38, 0.27], [-0.3, -0.36, 0.11]])) {
			if (Math.hypot(u - hx, v - hy) < hr) return [252, 252, 255];
		}
		return col;
	}
	return null;
}
// a smile: a curve |x-cx| < w at cy, dipping by `dip` in the middle; returns true on the line
function smileLine(x, y, cx, cy, w, dip, th) {
	const t = (x - cx) / w;
	if (Math.abs(t) > 1) return false;
	const yc = cy - dip * (1 - t * t);
	return Math.abs(y - yc) < th * (1 - 0.35 * t * t);
}

// ================================================================ clips
const TAU = Math.PI * 2;
const r3 = v => Math.round(v * 1000) / 1000;
const ease = t => t * t * (3 - 2 * t);
const tkey = t => { const s = String(Math.round(t * 100) / 100); return s.includes(".") ? s : s + ".0"; }; // "1.0", not "1": JS lists integer keys first
function clip(len, loop, fn, step = 0.1) {
	const bones = {};
	const n = Math.round(len / step);
	for (let i = 0; i <= n; i++) {
		const t = i === n ? len : i * step;
		const frame = fn(t);
		for (const [b, ch] of Object.entries(frame)) {
			bones[b] = bones[b] || {};
			for (const [k, v] of Object.entries(ch)) {
				const key = k === 'rot' ? 'rotation' : k === 'pos' ? 'position' : 'scale';
				bones[b][key] = bones[b][key] || {};
				bones[b][key][tkey(t)] = v.map(r3);
			}
		}
	}
	const o = { animation_length: len, bones };
	if (loop === true) o.loop = true;
	else if (loop === 'hold') o.loop = 'hold_on_last_frame';
	return o;
}
const DEFAULT_FLOAT = { body: [6, 0, 0], head: [-6, 0, 5], arm_r: [-20, 0, 40], arm_l: [-30, 0, -30], leg_r: [22, 0, 6], leg_l: [8, 0, -8] };
// extras(clipName, t, w, k) -> { bone: { rot } } for the Kwami's own bones (ears, antennae, tail, wings, ...)
function writeClips(id, FLOAT, extras) {
	const F = Object.assign({}, DEFAULT_FLOAT, FLOAT);
	const ANIM = path.join(A, 'animations', 'miraculous', 'kwami.animation.json');
	const file = JSON.parse(fs.readFileSync(ANIM, 'utf8'));
	const clips = file.animations;
	const P = `animation.kwami.${id}.`;
	const X = (name, t, extra) => extras ? extras(name, t, extra || {}) : {};
	const idleFrame = (t, len) => {
		const w = TAU * t / len, w2 = 2 * w;
		return Object.assign({
			root: { pos: [0, 0.9 * Math.sin(w2), 0] },
			body: { rot: [F.body[0] + 2 * Math.sin(w2 + 1), 0, 2.5 * Math.sin(w)] },
			head: { rot: [F.head[0] + 3 * Math.sin(w2 + 0.6), 7 * Math.sin(w), F.head[2] * 0.6 + 4 * Math.sin(w + 0.4)] },
			arm_r: { rot: [F.arm_r[0] + 6 * Math.sin(w2 + 0.5), F.arm_r[1], F.arm_r[2] + 6 * Math.sin(w2)] },
			arm_l: { rot: [F.arm_l[0] + 6 * Math.sin(w2 + 1.2), F.arm_l[1], F.arm_l[2] - 6 * Math.sin(w2 + 0.3)] },
			leg_r: { rot: [F.leg_r[0] + 8 * Math.sin(w2 + 1), 0, F.leg_r[2]] },
			leg_l: { rot: [F.leg_l[0] + 8 * Math.sin(w2 + 2), 0, F.leg_l[2]] },
		}, X('idle', t, { w, w2 }));
	};
	clips[P + 'idle'] = clip(4.8, true, t => idleFrame(t, 4.8));
	clips[P + 'wave'] = clip(1.5, false, t => {
		const k = Math.min(1, t / 0.2) * Math.min(1, (1.5 - t) / 0.25);
		const wv = Math.sin(TAU * t * 2.4);
		return Object.assign({
			root: { pos: [0, 0.7 * Math.abs(Math.sin(TAU * t * 1.2)), 0] },
			body: { rot: [4, 0, -4 * k] },
			head: { rot: [-6, 8 * k, -9 * k] },
			arm_r: { rot: [F.arm_r[0] * (1 - k) - 30 * k, F.arm_r[1] * (1 - k), F.arm_r[2] * (1 - k) + k * (62 + 16 * wv)], scale: [1, 1 + 0.3 * k, 1] },
			arm_l: { rot: [F.arm_l[0], F.arm_l[1], F.arm_l[2]] },
			leg_r: { rot: [14, 0, 0] },
			leg_l: { rot: [4, 0, 0] },
		}, X('wave', t, { k, wv }));
	});
	clips[P + 'eat'] = clip(2.0, true, t => {
		const nib = Math.sin(TAU * t * 4);
		return Object.assign({
			root: { pos: [0, 0.5 * Math.sin(TAU * t), 0] },
			body: { rot: [10, 0, 0] },
			head: { rot: [10 + 6 * nib, 0, 3 * Math.sin(TAU * t)] },
			arm_r: { rot: [-100 + 8 * nib, 0, -40], scale: [1, 1.4, 1] },
			arm_l: { rot: [-100 + 8 * Math.sin(TAU * t * 4 + 1), 0, 40], scale: [1, 1.4, 1] },
			leg_r: { rot: [30 + 12 * Math.sin(TAU * t * 2), 0, 6] },
			leg_l: { rot: [20 + 12 * Math.sin(TAU * t * 2 + Math.PI), 0, -6] },
		}, X('eat', t, { nib }));
	});
	clips[P + 'tired'] = clip(3.0, true, t => {
		const s = Math.sin(TAU * t / 3);
		return Object.assign({
			root: { pos: [0, -2 + 0.5 * s, 0] },
			body: { rot: [12 + 2 * s, 0, 3] },
			head: { rot: [12 + 3 * s, 0, 8] },
			arm_r: { rot: [6, 0, -12 + 2 * s] },
			arm_l: { rot: [6, 0, 12 - 2 * s] },
			leg_r: { rot: [10, 0, 4] },
			leg_l: { rot: [8, 0, -4] },
		}, X('tired', t, { s }));
	});
	clips[P + 'into_jewel'] = clip(0.6, 'hold', t => {
		const e = ease(t / 0.6);
		const sc = 1 - 0.97 * e;
		return Object.assign({
			root: { rot: [0, 900 * e * e, 0], scale: [sc, sc, sc], pos: [0, 3 * Math.sin(Math.PI * e), 0] },
			body: { rot: [0, 0, 0] },
			head: { rot: [-12 * e, 0, 0] },
			arm_r: { rot: [-20, 0, 20 - 30 * e] },
			arm_l: { rot: [-20, 0, -20 + 30 * e] },
			leg_r: { rot: [10, 0, -4 * e] },
			leg_l: { rot: [10, 0, 4 * e] },
		}, X('into_jewel', t, { e }));
	}, 0.05);
	clips[P + 'pop_out'] = clip(0.5, false, t => {
		const u = t / 0.5;
		const sc = u < 0.7 ? 1.15 * ease(u / 0.7) : 1.15 - 0.15 * ease((u - 0.7) / 0.3);
		const f = idleFrame(0, 4.8);
		f.root = { rot: [0, -360 * (1 - ease(u)), 0], scale: [sc, sc, sc], pos: [0, -2 * (1 - ease(u)), 0] };
		f.arm_r.rot[2] += 40 * Math.sin(Math.PI * u);
		f.arm_l.rot[2] -= 40 * Math.sin(Math.PI * u);
		return Object.assign(f, X('pop_out', t, { u }));
	}, 0.05);
	writeJson(ANIM, file);
	return F;
}

// ================================================================ output
function finish(M, id, shade, title, subtitle, FLOAT, extrasRest, opts = {}) {
	M.layout();
	M.paint(shade);
	const geo = M.geoJson('geometry.kwami_' + id);
	writeJson(path.join(A, 'geo', 'miraculous', id + '.geo.json'), geo);
	writePng(path.join(A, 'textures', 'entity', 'miraculous', id + '.png'), M.TW, M.TH, M.tex);
	const b = M.bounds(M.world({}));
	console.log(`${id}: ${M.bones.length} bones, ${M.cubes().length} cubes, tex ${M.TW}x${M.TH}, y ${b.lo[1].toFixed(2)}..${b.hi[1].toFixed(2)}`);
	M.k = 0.56 / 16; M.heightBlocks = b.hi[1] * M.k;
	const pose = Object.assign({}, DEFAULT_FLOAT, FLOAT, extrasRest || {});
	const it = { model: M, pose };
	const fl = [{ x: 0, z: 0, r: 0.42 }];
	L.sheet(path.join(__dirname, id + '_sheet.png'), title, subtitle, [
		{ label: 'FRONT', items: [it], cam: { yaw: 0, pitch: 6 }, floor: fl },
		{ label: 'THREE-QUARTER', items: [it], cam: { yaw: 35, pitch: 12 }, floor: fl },
		{ label: 'SIDE', items: [it], cam: { yaw: 90, pitch: 6 }, floor: fl },
		{ label: 'BACK', items: [it], cam: { yaw: 180, pitch: 10 }, floor: fl },
	], { panelH: opts.panelH || 800, minW: 2000, bg: opts.bg || { top: [150, 160, 170], bottom: [60, 66, 74] } });
}

module.exports = { L, Model, mix, mul, fbm, clamp, A, roundBox, blob, base, BASE, skin, spot, eye, smileLine, inEll, writeClips, finish, TAU, DEFAULT_FLOAT };
