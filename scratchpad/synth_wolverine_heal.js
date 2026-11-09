// v0.17.3 Wolverine healing-factor sounds, synthesised from scratch (no samples, nothing taken from any film audio): flesh
// knitting shut. Same toolkit as synth_andy_sounds.js -- filtered noise, granular crackle, stick-slip creaks, envelopes.
//
// The character (what a "regenerating wound" should sound like):
//   * a WET CRACKLE: dense, irregular clusters of tiny damped ticks (0.3-2.5 ms resonances at 0.9-6 kHz, heavy-tailed
//     loudness -- many faint, a few sharp pops), the crackle of tissue fibres re-attaching. Ticks are fixed-pitch or FALL in
//     pitch; never the rising chirp of a bubble (bubbles + steady fizz read as boiling water / lava -- the tester hated that).
//   * a SIZZLE: band-limited noise (2-7 kHz) whose loudness flickers on a grain-by-grain basis (multiplied by a crackle
//     envelope), so it hisses like burnt skin closing rather than sitting there as flat white noise.
//   * a low FLESHY layer underneath: slurps (noise through a falling resonance) and a sinew creak (stick-slip at 70-160 Hz),
//     the tension of muscle pulling closed.
//   * all of it in short dense BURSTS (0.3-1.2 s) with energy mostly 1-8 kHz, rolling off steeply above 8 kHz.
//
//   node scratchpad/synth_wolverine_heal.js "C:/Users/<you>/AppData/Local/Medal/recorder-*/ffmpeg7.exe"
// Output: src/main/resources/assets/projecthero/sounds/wolverine/heal_{crackle1-4,sizzle1-3,surge1-2}.ogg
// (WAVs in the OS temp folder). Events: projecthero:wolverine_heal_crackle / _sizzle / _surge (sounds.json); played by
// com.projecthero.mod.wolverine.WolverineHealSounds.
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');

const SR = 44100;
const FFMPEG = process.argv[2];
const WAV_DIR = path.join(os.tmpdir(), 'wolverine_heal_wav');
const OUT_DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds', 'wolverine');
fs.mkdirSync(WAV_DIR, { recursive: true });
fs.mkdirSync(OUT_DIR, { recursive: true });

let seed = 1974;
const rnd = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 4294967296; };
const noise = () => rnd() * 2 - 1;
const rr = (a, b) => a + (b - a) * rnd();
const logr = (a, b) => Math.exp(rr(Math.log(a), Math.log(b)));

// ---------------------------------------------------------------- DSP helpers (as in synth_andy_sounds.js)

function buf(sec) { return new Float32Array(Math.max(1, Math.ceil(sec * SR))); }
function noiseBuf(sec) { const b = buf(sec); for (let i = 0; i < b.length; i++) b[i] = noise(); return b; }
function mix(dst, src, at, gain = 1) { const o = Math.round(at * SR); for (let i = 0; i < src.length && i + o < dst.length; i++) if (i + o >= 0) dst[i + o] += src[i] * gain; }
function env(x, fn) { for (let i = 0; i < x.length; i++) x[i] *= fn(i / x.length, i / SR); return x; }
function filt(x, type, f, q = 0.707) {
	const y = new Float32Array(x.length);
	let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
	for (let i = 0; i < x.length; i++) {
		const fc = Math.max(20, Math.min(SR * 0.45, typeof f === 'number' ? f : f(i / x.length, i / SR)));
		const w = 2 * Math.PI * fc / SR, c = Math.cos(w), s = Math.sin(w), a = s / (2 * q);
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
function resonate(x, freqs, q) {
	const y = new Float32Array(x.length);
	freqs.forEach(f => { const b = filt(x, 'bp', f, q); for (let i = 0; i < y.length; i++) y[i] += b[i]; });
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
/** Mastering: a roll-off above ~9 kHz and a low cut (the energy belongs in 1-8 kHz, with the fleshy layer under it). */
function master(x, low = 80) { return filt(filt(filt(x, 'lp', 12500, 0.6), 'lp', 16000, 0.7), 'hp', low); }
/**
 * SNAPS: a few sharp, isolated broadband clicks (a 0.2-0.5 ms spike, high-passed) -- the odd fibre that pops loudly, the
 * full-height vertical lines of a crackle's spectrogram.
 */
function snaps(dst, len, count, amp) {
	for (let k = 0; k < count; k++) {
		const n = noiseBuf(rr(0.0002, 0.0005));
		n[0] += 1.0;
		env(n, u => 1 - u);
		const s = filt(n, 'hp', rr(900, 1600));
		mix(dst, s, rr(0.01, len * 0.95), amp * rr(0.4, 1));
	}
}
/** A faint fixed whistle or two inside a sizzle (hot fat sings at a few narrow pitches), well below the noise. */
function singing(len, freqs, gain) {
	const out = buf(len);
	const n = noiseBuf(len);
	freqs.forEach(f => { const b = filt(n, 'bp', f, 40); for (let i = 0; i < out.length; i++) out[i] += b[i]; });
	return normalise(out, gain, false);
}
function wander(sec, hz) { return normalise(filt(noiseBuf(sec), 'lp', hz), 1, false); }
/** One envelope lobe: fast rise over `a` of its width, then a curved fall. */
function bump(u, w, a = 0.15, k = 1.8) { if (u < 0 || u > w) return 0; const r = w * a; return u < r ? u / r : Math.pow(1 - (u - r) / (w - r), k); }

// ---------------------------------------------------------------- flesh building blocks

/**
 * A TICK: one fibre snapping into place. A damped sine at f (pitch falls ~15-35% as it dies: wet, never bubbly), a
 * sub-millisecond noise spike for the transient, decaying with time constant tau.
 */
function tick(f, tauMs, fall = 0.25) {
	const tau = tauMs / 1000;
	const d = Math.min(0.03, tau * 6);
	const out = buf(d);
	let ph = rnd() * Math.PI * 2;
	for (let i = 0; i < out.length; i++) {
		const s = i / SR;
		ph += 2 * Math.PI * f * (1 - fall * (1 - Math.exp(-s / (tau * 1.5)))) / SR;
		out[i] = Math.sin(ph) * Math.min(1, s / 0.00015) * Math.exp(-s / tau);
	}
	const n = Math.max(2, Math.round(0.0004 * SR));
	for (let i = 0; i < n && i < out.length; i++) out[i] += noise() * 0.5 * (1 - i / n);
	return out;
}
/**
 * WET CRACKLE over [0, len): Poisson ticks whose rate follows density(u) (u = 0..1), clustered (a fibre bundle lets go
 * in 2-6 quick ticks), with a heavy-tailed loudness. f is log-uniform in fr, weighted to 1.2-4 kHz; brightness(u) can
 * lift or darken the band through the burst.
 */
function crackle(len, density, fr = [900, 6000], amp = 1, bright = () => 1) {
	const out = buf(len + 0.04);
	let t = 0;
	while (t < len) {
		const u = t / len;
		const r = Math.max(1, density(u));
		t += -Math.log(1 - rnd() * 0.999) / r;
		if (t >= len) break;
		const n = rnd() < 0.35 ? 2 + Math.floor(rnd() * 5) : 1;
		const fb = (rnd() < 0.6 ? logr(1100, 5000) : logr(fr[0], fr[1])) * bright(u);
		const loud = Math.pow(rnd(), 3.2) * amp;
		let at = t;
		for (let k = 0; k < n; k++) {
			const f = fb * rr(0.92, 1.08);
			mix(out, tick(f, rr(0.6, 2.6) * Math.sqrt(2500 / f), rr(0.1, 0.35)), at, loud * (k === 0 ? 1 : rr(0.3, 0.8)));
			at += rr(0.0015, 0.007);
		}
	}
	return out;
}
/** A few big WET POPS in a burst: low-mid ticks (600-1400 Hz), longer and fuller, a skin flap sealing. */
function pops(dst, len, count, amp) {
	for (let k = 0; k < count; k++) {
		const f = logr(550, 1400);
		const p = tick(f, rr(2.5, 5), rr(0.25, 0.45));
		const lp = filt(p, 'lp', f * 2.2);
		mix(dst, lp, rr(0.02, len * 0.85), amp * rr(0.5, 1));
	}
}
/**
 * SIZZLE bed: noise band-passed 2-7 kHz, rolled off above 8 kHz, its loudness flickering grain by grain (multiplied by a
 * spiky crackle envelope -- exponentiated fast-wandering noise) so it hisses in fizzing shreds, never a flat white noise.
 */
function sizzle(len, centre = 3800, grit = 1) {
	const n = noiseBuf(len);
	let y = filt(n, 'bp', centre, 0.9);
	const hi = filt(n, 'bp', centre * 1.9, 1.0);
	for (let i = 0; i < y.length; i++) y[i] += 0.9 * hi[i];
	y = filt(y, 'lp', 11000, 0.8);
	y = filt(y, 'hp', 1500, 0.7);
	const w = filt(noiseBuf(len), 'lp', 260);
	const wp = peak(w) || 1;
	const slow = wander(len, 9);
	for (let i = 0; i < y.length; i++) {
		const g = Math.exp((w[i] / wp - 1) * 2.2 * grit); // mostly small, now and then a flare
		y[i] *= Math.min(1, g * 2) * (0.65 + 0.35 * slow[i]);
	}
	// level by RMS (a peak-normalised flicker would leave the bed far too quiet under the ticks), peaks capped
	let ss = 0;
	for (const v of y) ss += v * v;
	const k = 0.22 / (Math.sqrt(ss / y.length) || 1);
	for (let i = 0; i < y.length; i++) y[i] = Math.max(-1, Math.min(1, y[i] * k));
	return y;
}
/** A SLURP: meat pulling closed -- noise through a resonance falling f0 -> f1, plus a weaker formant 2.3x above. */
function slurp(dur, f0, f1, q = 6) {
	const n = noiseBuf(dur);
	const sweep = t => f0 * Math.pow(f1 / f0, Math.pow(t, 0.7));
	const a = filt(n, 'bp', sweep, q);
	const b = filt(n, 'bp', t => sweep(t) * 2.3, q * 1.4);
	for (let i = 0; i < a.length; i++) a[i] = (a[i] + 0.4 * b[i]) * bump(i / a.length, 1, 0.18, 1.5);
	const m = filt(noiseBuf(dur), 'lp', t => 240 - 110 * t, 0.9);
	env(m, u => bump(u, 1, 0.2, 2));
	const out = normalise(a, 1, false);
	mix(out, normalise(m, 1, false), 0, 0.55);
	return out;
}
/** A SINEW CREAK: stick-slip pulse train at p0 -> p1 Hz rung through leathery formants (the tension of muscle closing). */
function creak(dur, p0, p1, freqs = [320, 760, 1500], q = 4, shape = u => Math.sin(Math.PI * u)) {
	const exc = buf(dur);
	const w = wander(dur, 6);
	let t = 0;
	while (t < dur) {
		const u = t / dur;
		const f = (p0 + (p1 - p0) * u) * (1 + 0.1 * w[Math.min(w.length - 1, Math.floor(t * SR))]);
		t += (1 / f) * (1 + 0.12 * (rnd() * 2 - 1));
		if (rnd() < 0.08) continue;
		const i = Math.round(t * SR);
		if (i + 1 < exc.length) { const g = rr(0.6, 1) * shape(u); exc[i] += g; exc[i + 1] -= g * 0.6; }
	}
	const y = resonate(exc, freqs, q);
	mix(y, filt(exc, 'lp', p1 * 1.8), 0, 0.35);
	return normalise(y, 1, false);
}
/** Burst envelope: 1-3 irregular overlapping lobes, fast attack, curved fall. */
function lobes(len, n) {
	const L = [[0, len * (n > 1 ? rr(0.45, 0.7) : 1), 1]];
	for (let k = 1; k < n; k++) { const s = len * rr(0.2, 0.55); L.push([s, len - s, rr(0.55, 0.95)]); }
	return sec => L.reduce((m, [s, w, g]) => Math.max(m, g * bump(sec - s, w, rr(0.04, 0.12), 1.4)), 0);
}

// ---------------------------------------------------------------- the sounds

/** HEAL CRACKLE: one short knitting burst (0.35-0.8 s) -- dense crackle, a whisper of sizzle, a soft slurp under it. */
function healCrackle(v) {
	const len = [0.55, 0.42, 0.75, 0.62][v];
	const out = buf(len + 0.08);
	const e = lobes(len, [1, 2, 2, 3][v]);
	const body = crackle(len, u => 650 * (0.3 + e(u * len)) , [900, 6500], 1, u => 1.05 - 0.15 * u);
	mix(out, body, 0, 1.0);
	const s = sizzle(len, [4800, 5400, 5000, 4600][v], 1.1);
	env(s, (u, sec) => e(sec));
	mix(out, s, 0, 0.5);
	const cr = creak(len * rr(0.5, 0.8), rr(90, 120), rr(140, 190), [300, 750, 1600], 4);
	mix(out, cr, len * rr(0.05, 0.2), 0.16);
	pops(out, len, [2, 1, 3, 2][v], 0.35);
	snaps(out, len, [3, 2, 4, 3][v], 0.5);
	const sl = slurp(len * rr(0.6, 0.9), rr(520, 700), rr(230, 300), 5);
	mix(out, sl, rr(0, 0.05), 0.22);
	return normalise(softClip(master(out, 90), 0.8), 0.9);
}
/**
 * HEAL SIZZLE (big wounds / burns): a hot flare of sizzling skin (fast attack), crackle riding it and thinning out as the
 * wound closes, a deeper slurp + creak of the flesh pulling together, the last few pops sealing it.
 */
function healSizzle(v) {
	const len = [1.15, 0.95, 1.35][v];
	const out = buf(len + 0.1);
	const shape = (u) => Math.min(1, u / 0.04) * Math.pow(1 - u, 1.1) * (0.8 + 0.2 * Math.sin(u * 19 + v));
	const s = sizzle(len, [5200, 4600, 5600][v], 1.25);
	env(s, u => shape(u));
	mix(out, s, 0, 0.6);
	const sing = singing(len, [[6300, 10600], [5700], [6800, 9800]][v], 1);
	env(sing, u => shape(u));
	mix(out, sing, 0, 0.06);
	snaps(out, len, 5, 0.55);
	const c = crackle(len, u => 900 * Math.pow(1 - u, 0.8) + 120, [1000, 7000], 1, u => 1.1 - 0.25 * u);
	env(c, u => Math.min(1, u / 0.02) * (1 - 0.6 * u));
	mix(out, c, 0, 0.9);
	const sl = slurp(len * 0.7, rr(450, 600), rr(180, 240), 5);
	mix(out, sl, len * rr(0.15, 0.3), 0.3);
	const cr = creak(len * 0.55, rr(80, 100), rr(120, 150), [300, 720, 1400], 4);
	mix(out, cr, len * 0.25, 0.2);
	pops(out, len, 4, 0.4);
	return normalise(softClip(master(out, 80), 0.9), 0.92);
}
/**
 * HEAL SURGE (Death Surge / near-death recovery), 2-2.4 s: the whole body knitting at once. Crackle density and
 * brightness swell over ~1.5 s with overlapping bursts, sinew creaks rising in pitch under it, a sizzle flare at the peak,
 * then a last deep slurp and a few heavy pops as everything seals, and a short tail.
 */
function healSurge(v) {
	const len = [2.3, 2.0][v];
	const out = buf(len + 0.15);
	const peakAt = [0.68, 0.62][v];
	const swell = u => (u < peakAt ? Math.pow(u / peakAt, 1.6) : Math.pow(1 - (u - peakAt) / (1 - peakAt), 1.3));
	// overlapping knitting bursts, closer and louder towards the peak
	let t = 0.0;
	while (t < len * 0.9) {
		const u = t / len;
		const bl = rr(0.25, 0.6);
		const e = lobes(bl, 1 + Math.floor(rnd() * 2));
		const c = crackle(bl, x => 700 * (0.25 + e(x * bl)), [900, 6500], 1, () => 0.9 + 0.3 * swell(u));
		mix(out, c, t, 0.35 + 0.75 * swell(u));
		t += rr(0.12, 0.3) * (1.2 - 0.6 * swell(u));
	}
	// a continuous fine crackle under the bursts so the swell reads as one movement
	const fine = crackle(len, u => 200 + 900 * swell(u), [1500, 7000], 0.5);
	mix(out, fine, 0, 0.7);
	const s = sizzle(len, 5000, 1.2);
	env(s, u => 0.1 + 0.9 * Math.pow(swell(u), 2.2));
	mix(out, s, 0, 0.75);
	snaps(out, len, 9, 0.6);
	// tension: two sinew creaks rising under the swell
	mix(out, creak(len * 0.55, 70, 130, [280, 650, 1300], 4, u => Math.pow(u, 0.7) * (1 - Math.pow(u, 6))), len * 0.1, 0.18);
	mix(out, creak(len * 0.4, 110, 170, [340, 820, 1600], 4), len * 0.4, 0.14);
	// fleshy slurps through it, the deepest at the seal
	mix(out, slurp(0.45, 600, 280, 5), len * 0.2, 0.25);
	mix(out, slurp(0.5, 560, 240, 5), len * 0.45, 0.3);
	mix(out, slurp(0.55, 480, 170, 5), len * (peakAt + 0.02), 0.45);
	// the seal: heavy pops after the peak
	for (let k = 0; k < 5; k++) {
		const f = logr(500, 1100);
		mix(out, filt(tick(f, rr(3, 6), 0.4), 'lp', f * 2.2), len * (peakAt + rr(0.0, 0.18)), rr(0.35, 0.6));
	}
	// a low swell of body mass (fleshy pressure), kept faint
	const mass = filt(noiseBuf(len), 'lp', 180, 0.8);
	env(mass, u => 0.4 * swell(u));
	mix(out, normalise(mass, 1, false), 0, 0.25);
	const y = master(out, 60);
	env(y, (u, sec) => Math.min(1, sec / 0.03) * (sec > len ? Math.max(0, 1 - (sec - len) / 0.15) : 1));
	return normalise(softClip(y, 1.0), 0.95);
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
function save(name, x) {
	for (let i = 0; i < x.length; i++) if (!Number.isFinite(x[i])) x[i] = 0;
	const w = path.join(WAV_DIR, name + '.wav');
	wav(w, x);
	if (FFMPEG) {
		execFileSync(FFMPEG, ['-y', '-loglevel', 'error', '-i', w, '-ac', '1', '-ar', '44100', '-c:a', 'libvorbis', '-q:a', '5', path.join(OUT_DIR, name + '.ogg')]);
	}
	console.log(name.padEnd(16), (x.length / SR).toFixed(2) + ' s  peak ' + peak(x).toFixed(2));
}

for (let v = 0; v < 4; v++) save('heal_crackle' + (v + 1), healCrackle(v));
for (let v = 0; v < 3; v++) save('heal_sizzle' + (v + 1), healSizzle(v));
for (let v = 0; v < 2; v++) save('heal_surge' + (v + 1), healSurge(v));
console.log('WAVs in', WAV_DIR, FFMPEG ? '| OGGs in ' + OUT_DIR : '(no ffmpeg given: WAVs only)');
module.exports = { WAV_DIR };
