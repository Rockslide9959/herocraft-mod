// v0.17.3 Saiyan power sounds, synthesised from scratch (no samples, nothing taken from any show, film or game audio).
// Same toolkit as synth_andy_sounds.js / synth_wolverine_heal.js: filtered noise, oscillators, granular crackle, envelopes.
//
//   node scratchpad/synth_saiyan.js "C:/Users/<you>/AppData/Local/Medal/recorder-*/ffmpeg7.exe"
// Output: src/main/resources/assets/projecthero/sounds/saiyan/*.ogg (WAVs in the OS temp folder). Events are registered by
// com.projecthero.mod.saiyan.SaiyanSounds and listed in sounds.json. Loops (*_loop) are cross-faded so they repeat seamlessly.
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');

const SR = 44100;
const FFMPEG = process.argv[2];
const WAV_DIR = path.join(os.tmpdir(), 'saiyan_wav');
const OUT_DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds', 'saiyan');
fs.mkdirSync(WAV_DIR, { recursive: true });
fs.mkdirSync(OUT_DIR, { recursive: true });

let seed = 2013;
const rnd = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 4294967296; };
const noise = () => rnd() * 2 - 1;
const rr = (a, b) => a + (b - a) * rnd();
const logr = (a, b) => Math.exp(rr(Math.log(a), Math.log(b)));
const TAU = Math.PI * 2;

// ---------------------------------------------------------------- DSP helpers

function buf(sec) { return new Float32Array(Math.max(1, Math.ceil(sec * SR))); }
function noiseBuf(sec) { const b = buf(sec); for (let i = 0; i < b.length; i++) b[i] = noise(); return b; }
function mix(dst, src, at, gain = 1) { const o = Math.round(at * SR); for (let i = 0; i < src.length && i + o < dst.length; i++) if (i + o >= 0) dst[i + o] += src[i] * gain; }
function env(x, fn) { for (let i = 0; i < x.length; i++) x[i] *= fn(i / x.length, i / SR); return x; }
function filt(x, type, f, q = 0.707) {
	const y = new Float32Array(x.length);
	let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
	for (let i = 0; i < x.length; i++) {
		const fc = Math.max(20, Math.min(SR * 0.45, typeof f === 'number' ? f : f(i / x.length, i / SR)));
		const w = TAU * fc / SR, c = Math.cos(w), s = Math.sin(w), a = s / (2 * q);
		let b0, b1, b2;
		if (type === 'lp') { b0 = (1 - c) / 2; b1 = 1 - c; b2 = (1 - c) / 2; }
		else if (type === 'hp') { b0 = (1 + c) / 2; b1 = -(1 + c); b2 = (1 + c) / 2; }
		else { b0 = a; b1 = 0; b2 = -a; }
		const a0 = 1 + a, a1 = -2 * c, a2 = 1 - a;
		const v = (b0 * x[i] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0;
		x2 = x1; x1 = x[i]; y2 = y1; y1 = v; y[i] = v;
	}
	return y;
}
function peak(x) { let p = 0; for (const v of x) p = Math.max(p, Math.abs(v)); return p; }
function normalise(x, target, edge = true) {
	const p = peak(x);
	const k = p > 0 ? target / p : 1;
	for (let i = 0; i < x.length; i++) x[i] *= k;
	if (edge) {
		const f = Math.round(0.004 * SR);
		for (let i = 0; i < f && i < x.length; i++) { x[i] *= i / f; x[x.length - 1 - i] *= i / f; }
	}
	return x;
}
function softClip(x, k = 1.4) { for (let i = 0; i < x.length; i++) x[i] = Math.tanh(x[i] * k) / Math.tanh(k); return x; }
function master(x, low = 40, high = 14000) { return filt(filt(x, 'lp', high, 0.7), 'hp', low); }
/** An oscillator whose frequency follows fn(u, sec); shape: sine | saw | tri. */
function osc(sec, fn, shape = 'sine') {
	const x = buf(sec);
	let ph = 0;
	for (let i = 0; i < x.length; i++) {
		ph += fn(i / x.length, i / SR) / SR;
		const p = ph - Math.floor(ph);
		x[i] = shape === 'saw' ? 2 * p - 1 : shape === 'tri' ? 1 - 4 * Math.abs(p - 0.5) : Math.sin(TAU * p);
	}
	return x;
}
/** Flame crackle: sparse damped resonant ticks (fixed or falling pitch). */
function crackle(sec, rate, f0, f1, decay = 0.0015) {
	const x = buf(sec);
	const n = Math.round(sec * rate);
	for (let k = 0; k < n; k++) {
		const at = rnd() * sec, f = logr(f0, f1), a = Math.pow(rnd(), 2.5);
		const o = Math.round(at * SR), len = Math.round(decay * 6 * SR);
		for (let i = 0; i < len && o + i < x.length; i++) {
			const t = i / SR;
			x[o + i] += a * Math.sin(TAU * f * (1 - 0.15 * t / (decay * 6)) * t) * Math.exp(-t / decay);
		}
	}
	return x;
}
/** Electric arcs: short bursts of buzzing, high-passed square-ish noise. */
function arcs(sec, count, len = 0.06) {
	const x = buf(sec);
	for (let k = 0; k < count; k++) {
		const at = rnd() * (sec - len), l = rr(len * 0.4, len * 1.4), buzz = rr(90, 240);
		const b = noiseBuf(l);
		for (let i = 0; i < b.length; i++) {
			const t = i / SR;
			const gate = Math.sin(TAU * buzz * t) > rr(-0.2, 0.4) ? 1 : 0.15;
			b[i] *= gate * Math.exp(-t / (l * 0.45));
		}
		mix(x, filt(filt(b, 'hp', 1800), 'bp', rr(3000, 7000), 1.2), at, Math.pow(rnd(), 1.5) + 0.3);
	}
	return x;
}
/** Make a buffer loop seamlessly: cross-fade the last `xf` seconds into the start, then cut them off. */
function makeLoop(x, xf = 0.35) {
	const n = Math.round(xf * SR), out = new Float32Array(x.length - n);
	for (let i = 0; i < out.length; i++) out[i] = x[i];
	for (let i = 0; i < n; i++) {
		const t = i / n;
		out[i] = x[i] * Math.sqrt(t) + x[out.length + i] * Math.sqrt(1 - t);
	}
	return out;
}
const ease = u => u * u * (3 - 2 * u);

// ---------------------------------------------------------------- the sounds

/** Rising power-up: a deep rumble + a hum that climbs, gritty debris crackle growing. (one-shot, ~2 s) */
function kiChargeStart() {
	const len = 2.0;
	const out = buf(len);
	const rumble = filt(noiseBuf(len), 'lp', (u) => 90 + 140 * u, 0.9);
	env(rumble, u => 0.3 + 0.7 * u);
	mix(out, normalise(rumble, 1, false), 0, 0.7);
	const hum = osc(len, u => 55 + 75 * ease(u), 'saw');
	mix(out, filt(hum, 'lp', u => 300 + 1600 * u), 0, 0.35);
	const air = filt(noiseBuf(len), 'bp', u => 400 + 2600 * u, 0.7);
	env(air, u => 0.1 + 0.9 * u * u);
	mix(out, normalise(air, 1, false), 0, 0.3);
	mix(out, normalise(crackle(len, 60, 700, 4000, 0.002), 1, false), 0, 0.18);
	const y = master(out, 30);
	env(y, (u, s) => Math.min(1, s / 0.15));
	return normalise(softClip(y, 1.2), 0.85);
}
/** Holding charge: a steady powered rumble with a pulsing hum and debris crackle. (loop, ~3 s) */
function kiChargeLoop() {
	const len = 3.35;
	const out = buf(len);
	const rumble = filt(noiseBuf(len), 'lp', 200, 0.9);
	env(rumble, (u, s) => 0.75 + 0.25 * Math.sin(TAU * 2 * s));
	mix(out, normalise(rumble, 1, false), 0, 0.7);
	const hum = osc(len, (u, s) => 128 + 4 * Math.sin(TAU * 0.9 * s), 'saw');
	mix(out, filt(hum, 'lp', 1500), 0, 0.28);
	const air = filt(noiseBuf(len), 'bp', 2400, 0.8);
	mix(out, normalise(air, 1, false), 0, 0.22);
	mix(out, normalise(crackle(len, 70, 600, 4500, 0.002), 1, false), 0, 0.2);
	return normalise(softClip(makeLoop(master(out, 30)), 1.1), 0.7, false);
}
/** Aura loops: quiet flame-like roar + crackle; each colour has its own timbre. */
function auraLoop(kind) {
	const len = 3.35;
	const out = buf(len);
	const P = {
		gold: { roar: 900, hum: 110, crk: [900, 5000], arcs: 0, shimmer: 0 },
		blue: { roar: 1400, hum: 165, crk: [1500, 7000], arcs: 0, shimmer: 0.25 },
		red: { roar: 650, hum: 82, crk: [600, 3500], arcs: 0, shimmer: 0 },
		silver: { roar: 2200, hum: 220, crk: [2500, 9000], arcs: 0, shimmer: 0.45 },
	}[kind];
	const roar = filt(noiseBuf(len), 'bp', (u, s) => P.roar * (1 + 0.12 * Math.sin(TAU * 1.3 * s) + 0.06 * Math.sin(TAU * 3.7 * s)), 0.6);
	env(roar, (u, s) => 0.7 + 0.3 * Math.sin(TAU * 1.9 * s + 1));
	mix(out, normalise(roar, 1, false), 0, 0.55);
	const body = filt(noiseBuf(len), 'lp', 260, 0.8);
	mix(out, normalise(body, 1, false), 0, 0.35);
	const hum = osc(len, (u, s) => P.hum * (1 + 0.01 * Math.sin(TAU * 0.7 * s)), 'tri');
	mix(out, hum, 0, 0.12);
	mix(out, normalise(crackle(len, kind === 'silver' ? 40 : 90, P.crk[0], P.crk[1], 0.0016), 1, false), 0, 0.22);
	if (P.shimmer > 0) {
		const sh = osc(len, (u, s) => P.hum * 8 * (1 + 0.004 * Math.sin(TAU * 5 * s)), 'sine');
		const sh2 = osc(len, (u, s) => P.hum * 12.03, 'sine');
		for (let i = 0; i < sh.length; i++) sh[i] = (sh[i] + 0.6 * sh2[i]) * (0.6 + 0.4 * Math.sin(TAU * 0.6 * i / SR));
		mix(out, sh, 0, 0.05 * P.shimmer * 4);
	}
	return normalise(softClip(makeLoop(master(out, 35)), 1.05), 0.55, false);
}
/** Transformation: a sub drop, a bright energy blast, then a shockwave roar that tails off. */
function transformBurst(v) {
	const len = 2.4;
	const out = buf(len);
	const sub = osc(0.9, u => 70 - 38 * u, 'sine');
	env(sub, u => Math.exp(-u * 3.2));
	mix(out, sub, 0.02, 0.9);
	const blast = noiseBuf(len);
	env(blast, (u, s) => Math.exp(-s / (0.55 + 0.15 * v)) * Math.min(1, s / 0.006));
	mix(out, normalise(filt(blast, 'lp', (u, s) => 9000 * Math.exp(-s * 1.6) + 300), 1, false), 0, 0.85);
	const wave = filt(noiseBuf(len), 'bp', (u, s) => 1800 * Math.exp(-s * 1.1) + 180, 0.7);
	env(wave, (u, s) => s < 0.1 ? s / 0.1 : Math.exp(-(s - 0.1) / 0.8));
	mix(out, normalise(wave, 1, false), 0.05, 0.6);
	const whine = osc(0.5, u => 900 + 2500 * u + v * 300, 'sine');
	env(whine, u => u * u * (1 - u) * 4);
	mix(out, whine, 0, 0.12);
	mix(out, normalise(crackle(1.6, 120, 800, 6000, 0.0018), 1, false), 0.08, 0.25);
	return normalise(softClip(master(out, 25), 1.6), 0.9);
}
function ssj2Spark(v) {
	const len = 0.55 + 0.15 * v;
	const out = buf(len);
	mix(out, normalise(arcs(len, 5 + v * 2, 0.07), 1, false), 0, 0.9);
	const snap = noiseBuf(0.02);
	env(snap, u => Math.exp(-u * 6));
	mix(out, filt(snap, 'hp', 2500), 0.01, 0.8);
	return normalise(master(out, 300), 0.6);
}
/** Power down: the aura collapses (falling roar, a soft exhale of energy). */
function powerDown() {
	const len = 1.3;
	const out = buf(len);
	const roar = filt(noiseBuf(len), 'bp', u => 1600 * Math.pow(1 - u, 1.5) + 150, 0.7);
	env(roar, u => Math.pow(1 - u, 1.6));
	mix(out, normalise(roar, 1, false), 0, 0.8);
	const hum = osc(len, u => 160 - 110 * u, 'tri');
	env(hum, u => Math.pow(1 - u, 2));
	mix(out, hum, 0, 0.2);
	return normalise(master(out, 40), 0.65);
}
function kiDash(v) {
	const len = 0.5;
	const out = buf(len);
	const w = filt(noiseBuf(len), 'bp', u => 600 + 3200 * Math.sin(Math.PI * u) + v * 400, 1.1);
	env(w, u => Math.sin(Math.PI * Math.min(1, u * 1.3)) ** 2);
	mix(out, normalise(w, 1, false), 0, 0.9);
	const thump = osc(0.12, u => 90 - 40 * u, 'sine');
	env(thump, u => Math.exp(-u * 5));
	mix(out, thump, 0, 0.4);
	return normalise(master(out, 60), 0.6);
}
/** Instant step: a quick upward zip, a pop of air, a faint shimmer tail. */
function instantStep(v) {
	const len = 0.45;
	const out = buf(len);
	const zip = osc(0.12, u => 500 + 5500 * u * u + v * 300, 'saw');
	env(zip, u => Math.sin(Math.PI * u));
	mix(out, filt(zip, 'hp', 800), 0, 0.25);
	const pop = noiseBuf(0.05);
	env(pop, u => Math.exp(-u * 6));
	mix(out, filt(pop, 'bp', 1800, 0.8), 0.11, 0.7);
	const sh = osc(0.3, u => 3200 + 600 * Math.sin(u * 30), 'sine');
	env(sh, u => Math.exp(-u * 4));
	mix(out, sh, 0.12, 0.08);
	return normalise(master(out, 120), 0.55);
}
function punchHit(heavy, v) {
	const len = heavy ? 0.7 : 0.32;
	const out = buf(len);
	const thud = osc(0.18, u => (heavy ? 95 : 140) - 50 * u + v * 6, 'sine');
	env(thud, u => Math.exp(-u * 5));
	mix(out, thud, 0, heavy ? 1 : 0.7);
	const smack = noiseBuf(0.08);
	env(smack, u => Math.exp(-u * 9));
	mix(out, filt(smack, 'bp', heavy ? 1300 : 2200, 0.9), 0, heavy ? 0.9 : 0.8);
	const zap = filt(noiseBuf(len), 'bp', u => (heavy ? 3500 : 4500) * (1 - 0.5 * u), 1.4);
	env(zap, (u, s) => Math.exp(-s / (heavy ? 0.18 : 0.07)));
	mix(out, normalise(zap, 1, false), 0.005, heavy ? 0.35 : 0.25);
	if (heavy) {
		const boom = filt(noiseBuf(len), 'lp', 400, 0.8);
		env(boom, (u, s) => Math.exp(-s / 0.2));
		mix(out, normalise(boom, 1, false), 0, 0.6);
	}
	return normalise(softClip(master(out, 35), 1.5), heavy ? 0.8 : 0.65);
}
/** Beam charge: a gathering, rising tone with swirling air, held by the cooldown of the move (~1.6 s). */
function beamCharge() {
	const len = 1.6;
	const out = buf(len);
	const tone = osc(len, u => 180 + 520 * ease(u), 'saw');
	mix(out, filt(tone, 'lp', u => 500 + 3500 * u), 0, 0.3);
	const tone2 = osc(len, u => 362 + 1040 * ease(u), 'sine');
	mix(out, tone2, 0, 0.12);
	const swirl = filt(noiseBuf(len), 'bp', (u, s) => 1500 + 1200 * Math.sin(TAU * (1 + 6 * u) * s), 1.2);
	env(swirl, u => 0.2 + 0.8 * u);
	mix(out, normalise(swirl, 1, false), 0, 0.3);
	env(out, (u, s) => Math.min(1, s / 0.1) * (0.4 + 0.6 * u));
	return normalise(softClip(master(out, 50), 1.2), 0.7);
}
/** Beam firing: a roaring energy stream with a steady bright core. (loop, ~2.5 s) */
function beamFireLoop() {
	const len = 2.85;
	const out = buf(len);
	const roar = filt(noiseBuf(len), 'lp', 2400, 0.7);
	env(roar, (u, s) => 0.8 + 0.2 * Math.sin(TAU * 7 * s));
	mix(out, normalise(roar, 1, false), 0, 0.7);
	const core = osc(len, (u, s) => 240 + 6 * Math.sin(TAU * 11 * s), 'saw');
	mix(out, filt(core, 'lp', 2200), 0, 0.25);
	const hiss = filt(noiseBuf(len), 'hp', 5000);
	mix(out, normalise(hiss, 1, false), 0, 0.12);
	mix(out, normalise(crackle(len, 110, 1200, 7000, 0.0012), 1, false), 0, 0.15);
	return normalise(softClip(makeLoop(master(out, 40)), 1.2), 0.65, false);
}
function beamImpact(v) {
	const len = 1.6;
	const out = buf(len);
	const sub = osc(0.6, u => 60 - 25 * u, 'sine');
	env(sub, u => Math.exp(-u * 3));
	mix(out, sub, 0, 0.8);
	const blast = noiseBuf(len);
	env(blast, (u, s) => Math.exp(-s / (0.35 + 0.1 * v)) * Math.min(1, s / 0.004));
	mix(out, normalise(filt(blast, 'lp', (u, s) => 7000 * Math.exp(-s * 2) + 250), 1, false), 0, 0.9);
	mix(out, normalise(crackle(1.0, 140, 700, 5000, 0.002), 1, false), 0.05, 0.3);
	return normalise(softClip(master(out, 25), 1.6), 0.85);
}
/** The orb gathering: a huge slow swell, wind pulled inward, a choir-like stack of tones rising (~3.5 s). */
function orbCharge() {
	const len = 3.5;
	const out = buf(len);
	[1, 1.5, 2, 3].forEach((r, k) => {
		const t = osc(len, u => (90 + 60 * ease(u)) * r * (1 + 0.003 * k), 'tri');
		mix(out, t, 0, 0.12 / (k + 1) * 2);
	});
	const wind = filt(noiseBuf(len), 'bp', u => 300 + 1800 * u, 0.6);
	env(wind, u => 0.2 + 0.8 * u);
	mix(out, normalise(wind, 1, false), 0, 0.45);
	const rumble = filt(noiseBuf(len), 'lp', 120, 0.9);
	mix(out, normalise(rumble, 1, false), 0, 0.5);
	env(out, (u, s) => Math.min(1, s / 0.4) * (0.35 + 0.65 * ease(u)));
	return normalise(softClip(master(out, 25), 1.2), 0.8);
}
function orbExplode() {
	const len = 3.4;
	const out = buf(len);
	const sub = osc(1.4, u => 52 - 26 * u, 'sine');
	env(sub, u => Math.exp(-u * 2.5));
	mix(out, sub, 0, 1);
	const blast = noiseBuf(len);
	env(blast, (u, s) => Math.exp(-s / 0.9) * Math.min(1, s / 0.008));
	mix(out, normalise(filt(blast, 'lp', (u, s) => 8000 * Math.exp(-s * 1.2) + 200), 1, false), 0, 1);
	const tail = filt(noiseBuf(len), 'lp', 500, 0.8);
	env(tail, (u, s) => s < 0.3 ? s / 0.3 : Math.exp(-(s - 0.3) / 1.1));
	mix(out, normalise(tail, 1, false), 0, 0.7);
	mix(out, normalise(crackle(2.6, 160, 500, 5000, 0.0022), 1, false), 0.1, 0.3);
	return normalise(softClip(master(out, 20), 1.8), 0.9);
}
/** Dragon rush: a wind-up inhale of energy, then a roaring forward rush with a growl-like low layer. */
function dragonRush() {
	const len = 1.8;
	const out = buf(len);
	const wind = filt(noiseBuf(0.5), 'bp', u => 400 + 1500 * u, 0.9);
	env(wind, u => u * u);
	mix(out, normalise(wind, 1, false), 0, 0.5);
	const rush = filt(noiseBuf(1.3), 'bp', u => 700 + 900 * Math.sin(Math.PI * u), 0.7);
	env(rush, u => Math.min(1, u * 10) * Math.pow(1 - u, 1.2));
	mix(out, normalise(rush, 1, false), 0.5, 0.9);
	const growl = osc(1.3, (u, s) => 75 + 15 * Math.sin(TAU * 23 * s) + 20 * Math.sin(Math.PI * u), 'saw');
	env(growl, u => Math.min(1, u * 8) * Math.pow(1 - u, 1.5));
	mix(out, filt(growl, 'lp', 700), 0.5, 0.45);
	return normalise(softClip(master(out, 35), 1.4), 0.75);
}
/** Barrage: ~18 fast thuds and whooshes in 1.6 s. */
function barrageRapid() {
	const len = 1.7;
	const out = buf(len);
	for (let k = 0; k < 18; k++) {
		const at = 0.03 + k * 0.088 + rr(-0.012, 0.012);
		const th = osc(0.1, u => rr(110, 170) - 50 * u, 'sine');
		env(th, u => Math.exp(-u * 6));
		mix(out, th, at, rr(0.5, 0.8));
		const sm = noiseBuf(0.045);
		env(sm, u => Math.exp(-u * 8));
		mix(out, filt(sm, 'bp', rr(1500, 2800), 0.9), at, rr(0.4, 0.7));
		const wh = filt(noiseBuf(0.07), 'bp', rr(900, 1800), 1.2);
		env(wh, u => Math.sin(Math.PI * u));
		mix(out, wh, at - 0.05, 0.15);
	}
	return normalise(softClip(master(out, 50), 1.4), 0.65);
}
/** Instinct dodge: a soft shimmering swish with a glassy chime. */
function instinctDodge(v) {
	const len = 0.6;
	const out = buf(len);
	const sw = filt(noiseBuf(len), 'bp', u => 2500 + 2500 * Math.sin(Math.PI * u) + v * 400, 1.6);
	env(sw, u => Math.sin(Math.PI * u) ** 2);
	mix(out, normalise(sw, 1, false), 0, 0.5);
	[2093, 3136, 4186].forEach((f, k) => {
		const t = osc(len, u => f * (1 + v * 0.02), 'sine');
		env(t, (u, s) => Math.exp(-s / 0.25) * Math.min(1, s / 0.02));
		mix(out, t, 0.05, 0.07 / (k + 1));
	});
	return normalise(master(out, 400), 0.45);
}
/** Exhausted: the aura fizzles out (crackle dying, falling hiss) and a heavy breath. */
function exhausted() {
	const len = 1.6;
	const out = buf(len);
	const fizz = crackle(len, 80, 800, 5000, 0.0018);
	env(fizz, u => Math.pow(1 - u, 2));
	mix(out, normalise(fizz, 1, false), 0, 0.4);
	const hiss = filt(noiseBuf(len), 'bp', u => 3000 - 2200 * u, 0.8);
	env(hiss, u => Math.pow(1 - u, 1.5) * 0.6);
	mix(out, normalise(hiss, 1, false), 0, 0.35);
	// a heavy out-breath: low-mid noise through a formant-ish band, swelling then fading
	const br = filt(filt(noiseBuf(0.9), 'bp', 700, 1.2), 'lp', 2200);
	env(br, u => Math.sin(Math.PI * Math.min(1, u * 1.1)) ** 1.5);
	mix(out, normalise(br, 1, false), 0.5, 0.45);
	return normalise(master(out, 80), 0.5);
}

// ---------------------------------------------------------------- output

function wav(file, x) {
	const b = Buffer.alloc(44 + x.length * 2);
	b.write('RIFF', 0); b.writeUInt32LE(36 + x.length * 2, 4); b.write('WAVE', 8);
	b.write('fmt ', 12); b.writeUInt32LE(16, 16); b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22);
	b.writeUInt32LE(SR, 24); b.writeUInt32LE(SR * 2, 28); b.writeUInt16LE(2, 32); b.writeUInt16LE(16, 34);
	b.write('data', 36); b.writeUInt32LE(x.length * 2, 40);
	for (let i = 0; i < x.length; i++) b.writeInt16LE(Math.max(-32768, Math.min(32767, Math.round(x[i] * 32767))), 44 + i * 2);
	fs.writeFileSync(file, b);
}
const made = [];
function save(name, x) {
	for (let i = 0; i < x.length; i++) if (!Number.isFinite(x[i])) x[i] = 0;
	const w = path.join(WAV_DIR, name + '.wav');
	wav(w, x);
	if (FFMPEG) {
		execFileSync(FFMPEG, ['-y', '-loglevel', 'error', '-i', w, '-ac', '1', '-ar', '44100', '-c:a', 'libvorbis', '-q:a', '5', path.join(OUT_DIR, name + '.ogg')]);
	}
	made.push(name);
	console.log(name.padEnd(20), (x.length / SR).toFixed(2) + ' s  peak ' + peak(x).toFixed(2));
}

save('ki_charge_start', kiChargeStart());
save('ki_charge_loop', kiChargeLoop());
for (const k of ['gold', 'blue', 'red', 'silver']) save('aura_loop_' + k, auraLoop(k));
for (let v = 0; v < 2; v++) save('transform_burst' + (v + 1), transformBurst(v));
for (let v = 0; v < 3; v++) save('ssj2_spark' + (v + 1), ssj2Spark(v));
save('power_down', powerDown());
for (let v = 0; v < 2; v++) save('ki_dash' + (v + 1), kiDash(v));
for (let v = 0; v < 2; v++) save('instant_step' + (v + 1), instantStep(v));
for (let v = 0; v < 3; v++) save('punch_hit_light' + (v + 1), punchHit(false, v));
for (let v = 0; v < 2; v++) save('punch_hit_heavy' + (v + 1), punchHit(true, v));
save('beam_charge', beamCharge());
save('beam_fire_loop', beamFireLoop());
for (let v = 0; v < 2; v++) save('beam_impact' + (v + 1), beamImpact(v));
save('orb_charge', orbCharge());
save('orb_explode', orbExplode());
save('dragon_rush', dragonRush());
save('barrage_rapid', barrageRapid());
for (let v = 0; v < 2; v++) save('instinct_dodge' + (v + 1), instinctDodge(v));
save('exhausted', exhausted());
fs.writeFileSync(path.join(WAV_DIR, 'made.json'), JSON.stringify(made));
console.log('WAVs in', WAV_DIR, FFMPEG ? '| OGGs in ' + OUT_DIR : '(no ffmpeg given: WAVs only)');
