// GeckoLib animation authoring for the v0.16.x generators (Frost Giants, Ultron Omega).
//   - AnimSet.clip(name, lengthTicks, loop, frames, opts) authors key poses (the Titan generator's format: pose
//     values are bone rotation offsets in degrees, added to the rest rotation; the root also gets a position key so
//     the planted feet stay on the ground),
//   - secondary motion: "dangle" bones (beards, braids, capes, robes, flaps, tails, ears, crystals) are simulated as
//     damped springs that try to keep their rest world direction (gravity) and lag behind the body's acceleration;
//     the result is written as dense keys on those bones, added to anything authored on them,
//   - strike checks: every strike listed in the timing table must have an authored keyframe on (strike - TRANSITION),
//     like the Titan's check; build() throws otherwise,
//   - loadGameModel / loadAnimations / poseAt: read the written geo + PNG + animation JSON back, so the preview
//     sheets are rendered from the real game assets.
'use strict';
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const L = require('./lib_concept_models.js');
const { Model, mv3, vnorm, vdot, vsub, affApply } = L;

const DEG = Math.PI / 180;
const EASE = {
	linear: t => t,
	easeinoutsine: t => -(Math.cos(Math.PI * t) - 1) / 2,
	easeinsine: t => 1 - Math.cos(t * Math.PI / 2),
	easeoutsine: t => Math.sin(t * Math.PI / 2),
	easeinquad: t => t * t,
	easeoutquad: t => 1 - (1 - t) * (1 - t),
	easeincubic: t => t * t * t,
	easeoutcubic: t => 1 - Math.pow(1 - t, 3),
	easeoutback: t => { const c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2); },
	easeinback: t => { const c1 = 1.70158, c3 = c1 + 1; return c3 * t * t * t - c1 * t * t; },
};
const ease = (name, t) => (EASE[name || 'linear'] || EASE.linear)(t);

function normPose(p) {
	const o = {};
	for (const k of Object.keys(p || {})) {
		if (k.startsWith('$')) continue;
		const v = p[k];
		o[k] = typeof v === 'number' ? [v, 0, 0] : [v[0] || 0, v[1] || 0, v[2] || 0];
	}
	return o;
}
// sum of poses (bone-wise vector add); $-keys of the later poses win
function addPose(...ps) {
	const o = {};
	for (const p of ps) {
		if (!p) continue;
		for (const k of Object.keys(p)) {
			if (k.startsWith('$')) { o[k] = p[k]; continue; }
			const v = typeof p[k] === 'number' ? [p[k], 0, 0] : p[k];
			const a = o[k] || [0, 0, 0];
			o[k] = [a[0] + (v[0] || 0), a[1] + (v[1] || 0), a[2] + (v[2] || 0)];
		}
	}
	return o;
}
function mirrorPose(p) {
	const o = {};
	for (const k of Object.keys(p)) {
		if (k.startsWith('$')) { o[k] = p[k]; continue; }
		const v = typeof p[k] === 'number' ? [p[k], 0, 0] : p[k];
		const nk = k.replace(/_([fb]?)([rl])$/, (m, fb, s) => `_${fb}${s === 'r' ? 'l' : 'r'}`);
		o[nk] = [v[0], -(v[1] || 0), -(v[2] || 0)];
	}
	return o;
}
const scalePose = (p, k) => { const o = {}; for (const key of Object.keys(p)) { if (key.startsWith('$')) { o[key] = p[key]; continue; } const v = typeof p[key] === 'number' ? [p[key], 0, 0] : p[key]; o[key] = v.map(x => x * k); } return o; };

class AnimSet {
	// opts: { transition, neverKey: [...], feet: cube filter, knees: cube filter, dangles: [...] }
	constructor(M, id, opts = {}) {
		this.M = M; this.id = id;
		this.T = opts.transition == null ? 4 : opts.transition;
		this.never = new Set(opts.neverKey || ['neck', 'flinch']);
		this.feet = opts.feet || (c => c.tag === 'sole' || /^paw_/.test(c.bone));
		this.knees = opts.knees || (c => /^(shin|foot|lower|paw)/.test(c.bone) || c.tag === 'sole');
		this.dangles = opts.dangles || [];
		this.hidden = opts.hidden || []; // bones scaled to 0 unless a clip's scaleKeys show them (props, spell effects)
		this.clips = {}; this.timing = {};
		this.restW = M.world({});
	}
	ground(pose, mode) {
		const W = this.M.world(normPose(pose), [0, 0, 0]);
		const filt = mode === 'knees' ? this.knees : mode === 'all' ? null : mode === 'body' ? (c => !/^(weapon|casket|spell|crown|helm)/.test(c.bone)) : this.feet;
		return -this.M.bounds(W, filt).lo[1];
	}
	// frames: [tick, pose, easing?]. pose keys: bones; $root:[x,y,z] explicit root position; $ground:'feet'|'knees'|'all'|false
	// (default opts.ground or 'feet'); $y extra root height; $z root z; $strike: true marks the strike keyframe.
	clip(name, len, loop, frames, opts = {}) {
		const gmode = opts.ground === undefined ? 'feet' : opts.ground;
		const fr = frames.map(([t, p, e]) => {
			const g = p.$ground === undefined ? gmode : p.$ground;
			let root = p.$root ? p.$root.slice() : [0, 0, 0];
			if (!p.$root && g) root[1] = this.ground(p, g);
			root[1] += p.$y || 0; root[2] += p.$z || 0;
			return { t, pose: normPose(p), root, e: e || null, strike: !!p.$strike };
		});
		if (fr[0].t !== 0) throw new Error(`${this.id}.${name}: first keyframe must be at tick 0`);
		if (fr[fr.length - 1].t > len) throw new Error(`${this.id}.${name}: keyframe past the clip length`);
		const c = { name, len, loop, frames: fr, speed: opts.speed || 0, strikes: opts.strikes || [], events: opts.events || {}, noSecondary: !!opts.noSecondary, secK: opts.secK || 1, scaleKeys: opts.scaleKeys || {} };
		this.clips[name] = c;
		return c;
	}
	evalAt(c, t) {
		const F = c.frames;
		if (c.loop === true) t = ((t % c.len) + c.len) % c.len;
		if (t <= F[0].t) return { pose: F[0].pose, root: F[0].root };
		for (let i = 0; i < F.length - 1; i++) {
			const a = F[i], b = F[i + 1];
			if (t >= a.t && t <= b.t) {
				const k = b.t === a.t ? 1 : ease(b.e, (t - a.t) / (b.t - a.t));
				const pose = {};
				for (const bn of new Set([...Object.keys(a.pose), ...Object.keys(b.pose)])) {
					const va = a.pose[bn] || [0, 0, 0], vb = b.pose[bn] || [0, 0, 0];
					pose[bn] = [0, 1, 2].map(j => va[j] + (vb[j] - va[j]) * k);
				}
				return { pose, root: [0, 1, 2].map(j => a.root[j] + (b.root[j] - a.root[j]) * k) };
			}
		}
		// past the last key: loop back to the first (loops) or hold the last
		const a = F[F.length - 1];
		if (c.loop === true) {
			const b = F[0], span = c.len - a.t;
			const k = span <= 0 ? 1 : ease(b.e, (t - a.t) / span);
			const pose = {};
			for (const bn of new Set([...Object.keys(a.pose), ...Object.keys(b.pose)])) {
				const va = a.pose[bn] || [0, 0, 0], vb = b.pose[bn] || [0, 0, 0];
				pose[bn] = [0, 1, 2].map(j => va[j] + (vb[j] - va[j]) * k);
			}
			return { pose, root: [0, 1, 2].map(j => a.root[j] + (b.root[j] - a.root[j]) * k) };
		}
		return { pose: a.pose, root: a.root };
	}
	// Damped-spring secondary motion for every dangle bone; returns { bone: [[tick, [x,y,z]], ...] }.
	secondary(c) {
		const M = this.M, out = {};
		if (c.noSecondary || !this.dangles.length) return out;
		const N = c.len;
		const passes = c.loop === true ? 3 : 1;
		const samples = [];
		for (let t = 0; t <= N; t++) {
			const { pose, root } = this.evalAt(c, t);
			samples.push({ W: M.world(pose, root), pose });
		}
		for (const d of this.dangles) {
			const b = M.B[d.bone];
			if (!b) continue;
			const par = b.parent;
			const h = vnorm(d.dir || [0, -1, 0]);
			const R0 = this.restW[par].m;
			const g = mv3(R0, h); // rest world direction the bone wants to keep
			const stiff = d.stiff == null ? 0.85 : d.stiff, acc = (d.acc == null ? 1.4 : d.acc) * c.secK, drag = d.drag == null ? 2.5 : d.drag;
			const w = 2 * Math.PI / (d.period || 14), z = d.damp == null ? 0.32 : d.damp;
			const target = samples.map((s, i) => {
				const R = s.W[par].m;
				const dl = mv3([[R[0][0], R[1][0], R[2][0]], [R[0][1], R[1][1], R[2][1]], [R[0][2], R[1][2], R[2][2]]], g); // R^T g
				// signed math angles from h to dl about X and about Z
				const ax = Math.atan2(h[1] * dl[2] - h[2] * dl[1], h[1] * dl[1] + h[2] * dl[2]) / DEG;
				const az = Math.atan2(h[0] * dl[1] - h[1] * dl[0], h[0] * dl[0] + h[1] * dl[1]) / DEG;
				// inertia: the pivot's acceleration (world px/tick^2) swings the free end the other way
				const p = j => affApply(samples[Math.max(0, Math.min(N, j))].W[par], b.pivot);
				const a = [0, 1, 2].map(k => p(i + 1)[k] - 2 * p(i)[k] + p(i - 1)[k]);
				let tx = -stiff * ax + acc * (-a[2]) * 6 + drag * c.speed * 10 + (d.bias || 0);
				let tz = -stiff * az + acc * (a[0]) * 6 * (d.roll == null ? 1 : d.roll);
				const lim = d.limit || 45;
				return [Math.max(-lim, Math.min(lim, tx)), Math.max(-lim, Math.min(lim, tz))];
			});
			let x = target[0].slice(), v = [0, 0];
			const res = [];
			for (let pass = 0; pass < passes; pass++) {
				for (let i = 0; i <= N; i++) {
					const T = target[i];
					for (let k = 0; k < 2; k++) {
						const a = w * w * (T[k] - x[k]) - 2 * z * w * v[k];
						v[k] += a; x[k] += v[k];
					}
					if (pass === passes - 1) res.push([i, [x[0] * (d.kx == null ? 1 : d.kx), 0, x[1] * (d.kz == null ? 1 : d.kz)]]);
				}
			}
			out[d.bone] = res;
		}
		return out;
	}
	build() {
		const anims = {}, errors = [];
		for (const c of Object.values(this.clips)) {
			const bones = {};
			const keyed = new Set();
			c.frames.forEach(f => Object.keys(f.pose).forEach(b => keyed.add(b)));
			const sec = this.secondary(c);
			const kf = (v, e) => { const r = v.map(n => Math.round(n * 1000) / 1000); if (r.some(n => !Number.isFinite(n))) errors.push(`${c.name}: non-finite key value`); return e ? { vector: r, easing: e } : r; };
			for (const bn of keyed) {
				if (this.never.has(bn)) { errors.push(`${c.name}: never-keyed bone ${bn} is keyed`); continue; }
				if (!this.M.B[bn]) { errors.push(`${c.name}: unknown bone ${bn}`); continue; }
				if (sec[bn]) continue; // merged below
				const vals = c.frames.map(f => f.pose[bn] || [0, 0, 0]);
				if (!vals.some(v => v.some(x => Math.abs(x) > 1e-6))) continue;
				const k = {};
				c.frames.forEach((f, i) => { k[(f.t / 20).toFixed(4)] = kf(vals[i], f.e); });
				bones[bn] = { rotation: k };
			}
			for (const bn of Object.keys(sec)) {
				const k = {};
				for (const [t, v] of sec[bn]) {
					if (t % 2 && t !== c.len) continue;
					const base = this.evalAt(c, t).pose[bn] || [0, 0, 0];
					k[(t / 20).toFixed(4)] = kf([base[0] + v[0], base[1] + v[1], base[2] + v[2]]);
				}
				bones[bn] = { rotation: k };
			}
			const rv = c.frames.map(f => f.root);
			if (rv.some(v => v.some(x => Math.abs(x) > 1e-4))) {
				const k = {};
				c.frames.forEach((f, i) => { k[(f.t / 20).toFixed(4)] = kf(rv[i], f.e); });
				bones.root = Object.assign(bones.root || {}, { position: k });
			}
			for (const bn of new Set([...this.hidden, ...Object.keys(c.scaleKeys)])) {
				if (!this.M.B[bn]) { errors.push(c.name + ': unknown scaled bone ' + bn); continue; }
				const ks = c.scaleKeys[bn] || [[0, 0]];
				const k = {};
				for (const [t, v, e] of ks) k[(t / 20).toFixed(4)] = kf([v, v, v], e);
				bones[bn] = Object.assign(bones[bn] || {}, { scale: k });
			}
			const a = { animation_length: c.len / 20, bones };
			if (c.loop === true) a.loop = true; else if (c.loop === 'hold') a.loop = 'hold_on_last_frame';
			anims[`animation.${this.id}.${c.name}`] = a;
			// strike checks: a keyframe on (strike - TRANSITION), flagged as the strike
			for (const s of c.strikes) {
				const kt = s - this.T;
				const f = c.frames.find(fr => fr.t === kt);
				if (!f) errors.push(`${c.name}: no keyframe on clip tick ${kt} for the strike at server tick ${s}`);
				else if (!f.strike) errors.push(`${c.name}: keyframe at ${kt} is not marked $strike`);
			}
			for (const f of c.frames) if (f.strike && !c.strikes.includes(f.t + this.T)) errors.push(`${c.name}: $strike keyframe at ${f.t} is not in the timing table`);
			this.timing[c.name] = Object.assign({}, this.timing[c.name] || {}, { length: c.len, loop: c.loop === true ? 'loop' : c.loop === 'hold' ? 'hold' : 'once' },
				c.strikes.length ? { strike_ticks: c.strikes } : {}, Object.keys(c.events).length ? { events: c.events } : {}, c.speed ? { move_speed: c.speed } : {});
		}
		if (errors.length) throw new Error(`${this.id} animation checks failed:\n  ` + errors.join('\n  '));
		return { format_version: '1.8.0', animations: anims };
	}
}

// Stride-matched gait (the Titan's approach): contact pose = right leg forward; the cycle length makes the planted
// foot travel backward at `speed` blocks/tick so the feet don't skate.
function gaitCycle(anim, contact, footR, footL, speed) {
	const M = anim.M;
	const W = M.world(normPose(contact));
	const a = affApply(W[footR.bone], footR.p), b = affApply(W[footL.bone], footL.p);
	const step = Math.abs(a[2] - b[2]) * M.k;
	return { cycle: Math.max(8, Math.round(2 * step / speed / 2) * 2), step };
}

// ================================================================ reading the game assets back
function decodePng(buf) {
	let p = 8, w = 0, h = 0; const idat = [];
	while (p < buf.length) {
		const len = buf.readUInt32BE(p), type = buf.toString('ascii', p + 4, p + 8);
		if (type === 'IHDR') { w = buf.readUInt32BE(p + 8); h = buf.readUInt32BE(p + 12); }
		else if (type === 'IDAT') idat.push(buf.slice(p + 8, p + 8 + len));
		p += 12 + len;
	}
	const raw = zlib.inflateSync(Buffer.concat(idat));
	const out = Buffer.alloc(w * h * 4), st = w * 4;
	for (let y = 0; y < h; y++) {
		const f = raw[y * (st + 1)];
		for (let x = 0; x < st; x++) {
			const v = raw[y * (st + 1) + 1 + x];
			const left = x >= 4 ? out[y * st + x - 4] : 0, up = y ? out[(y - 1) * st + x] : 0, ul = x >= 4 && y ? out[(y - 1) * st + x - 4] : 0;
			let r = v;
			if (f === 1) r = v + left; else if (f === 2) r = v + up; else if (f === 3) r = v + ((left + up) >> 1);
			else if (f === 4) { const pp = left + up - ul, pa = Math.abs(pp - left), pb = Math.abs(pp - up), pc = Math.abs(pp - ul); r = v + (pa <= pb && pa <= pc ? left : pb <= pc ? up : ul); }
			out[y * st + x] = r & 255;
		}
	}
	return { w, h, px: out };
}
function loadGameModel(geoFile, texFile, glowFile, modelScale, geckoMask = false) {
	const geo = JSON.parse(fs.readFileSync(geoFile, 'utf8'))['minecraft:geometry'][0];
	const M = new Model(path.basename(geoFile), { TW: geo.description.texture_width });
	for (const b of geo.bones) {
		const bb = M.bone(b.name, b.parent || null, b.pivot, b.rotation || [0, 0, 0]);
		for (const c of b.cubes || []) {
			const cc = M.box(bb, c.origin[0], c.origin[1], c.origin[2], c.origin[0] + c.size[0], c.origin[1] + c.size[1], c.origin[2] + c.size[2], 'x', c.rotation ? { rot: c.rotation, pivot: c.pivot } : {});
			cc.uv = {};
			for (const f of L.FACES) { const u = c.uv[f]; cc.uv[f] = { u: u.uv[0], v: u.uv[1], w: u.uv_size[0], h: u.uv_size[1] }; }
		}
	}
	const t = decodePng(fs.readFileSync(texFile)), g = decodePng(fs.readFileSync(glowFile));
	M.TW = t.w; M.TH = t.h; M.tex = t.px; M.glow = g.px;
	if (geckoMask) for (let i = 0; i < t.px.length; i += 4) if (g.px[i] || g.px[i + 1] || g.px[i + 2] || g.px[i + 3]) { // as GeckoLib's AutoGlowingTexture
		for (let q = 0; q < 3; q++) M.glow[i + q] = t.px[i + q];
		if (!g.px[i + 3]) M.glow[i + 3] = t.px[i + 3];
		M.tex[i + 3] = 0;
	}
	M.MODEL_SCALE = modelScale; M.k = modelScale / 16;
	return M;
}
function loadAnimations(file) { return JSON.parse(fs.readFileSync(file, 'utf8')).animations; }
// Evaluate a clip from the animation JSON at a tick (GeckoLib semantics: a keyframe's easing shapes the segment into it).
function poseAt(anims, key, tick) {
	const a = anims[key];
	if (!a) throw new Error(`no clip ${key}`);
	const tsec = tick / 20;
	const pose = {}; let root = [0, 0, 0];
	const evalTrack = tr => {
		const ks = Object.keys(tr).map(k => ({ t: parseFloat(k), v: Array.isArray(tr[k]) ? tr[k] : tr[k].vector, e: Array.isArray(tr[k]) ? null : tr[k].easing })).sort((x, y) => x.t - y.t);
		if (tsec <= ks[0].t) return ks[0].v;
		for (let i = 0; i < ks.length - 1; i++) if (tsec >= ks[i].t && tsec <= ks[i + 1].t) {
			const k = ease(ks[i + 1].e, (tsec - ks[i].t) / Math.max(1e-9, ks[i + 1].t - ks[i].t));
			return ks[i].v.map((v, j) => v + (ks[i + 1].v[j] - v) * k);
		}
		return ks[ks.length - 1].v;
	};
	for (const bn of Object.keys(a.bones)) {
		if (a.bones[bn].rotation) pose[bn] = evalTrack(a.bones[bn].rotation);
		if (a.bones[bn].scale) { const sc = evalTrack(a.bones[bn].scale); if (sc[0] < 0.05) (pose.$hidden = pose.$hidden || []).push(bn); }
		if (bn === 'root' && a.bones[bn].position) root = evalTrack(a.bones[bn].position);
	}
	pose.$root = root;
	return pose;
}

module.exports = { AnimSet, addPose, mirrorPose, scalePose, normPose, gaitCycle, ease, decodePng, loadGameModel, loadAnimations, poseAt };
