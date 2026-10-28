// v0.17.1 Omnitrix sound rework (replaces the watch half of synth_omnitrix_sounds.js; every alien move is now built from
// vanilla sounds in OmnitrixSounds / the kits -- see docs/OMNITRIX_SOUNDS_0171.md). Only what vanilla cannot do is
// synthesised here, and none of it is a sustained pure tone or a bell partial: filtered noise, buzzy pulse trains with a
// jittered pitch, pitch sweeps, envelopes and gentle tanh saturation.
//   omnitrix/core_up   -- the core popping up: a soft rising electronic whirr (the clack is a vanilla layer on top)
//   omnitrix/transform -- the slam: a soft noise thump, then a fast bright whoosh-zap sweep up with electric crackle
//   omnitrix/revert    -- Shift+V: a short descending power-down whir with a soft zap
//   omnitrix/timeout   -- time's up: a longer power-down whir that sputters out, with a red-flash zap at the start
//   omnitrix/warning   -- the timeout warning: one soft, mid-pitched, rounded electronic blip (played on a cadence)
//   omnitrix/denied    -- refused (recharging / held / rejected): a dull low buzz
// 44.1 kHz mono WAV -> OGG Vorbis through an ffmpeg with libvorbis (%LOCALAPPDATA%\Medal\recorder-*\ffmpeg7.exe); every file is
// normalised to a -6 dBFS peak with raised-cosine fades so nothing clicks.
//   node scratchpad/synth_omnitrix_v2_sounds.js "<ffmpeg.exe with libvorbis>"
// Check: node scratchpad/analyse_omnitrix_sounds.js "<ffmpeg.exe>"
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');

const SR = 44100;
const FFMPEG = process.argv[2];
const WAV_DIR = path.join(os.tmpdir(), 'omnitrix_v2_wav');
const OUT_DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds', 'omnitrix');
fs.mkdirSync(WAV_DIR, { recursive: true });
fs.mkdirSync(OUT_DIR, { recursive: true });

let seed = 20171;
const rnd = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 4294967296; };
const noise = () => rnd() * 2 - 1;
const buf = sec => new Float32Array(Math.ceil(sec * SR));
function noiseBuf(sec) { const b = buf(sec); for (let i = 0; i < b.length; i++) b[i] = noise(); return b; }
/** A biquad (lp / hp / bp) whose cutoff follows f(t), t in 0..1 over the buffer. */
function filt(x, type, f, q = 0.707) {
	const y = new Float32Array(x.length);
	let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
	for (let i = 0; i < x.length; i++) {
		const fc = Math.max(30, Math.min(SR * 0.45, f(i / x.length)));
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
function mix(dst, src, at, gain = 1) { const o = Math.round(at * SR); for (let i = 0; i < src.length && i + o < dst.length; i++) dst[i + o] += src[i] * gain; }
function env(x, fn) { for (let i = 0; i < x.length; i++) x[i] *= fn(i / x.length); return x; }
function sat(x, k = 1.5) { for (let i = 0; i < x.length; i++) x[i] = Math.tanh(x[i] * k) / Math.tanh(k); return x; }
/** Slowly wandering random value in -1..1 (smoothed noise), for pitch jitter. */
function drift(len, rate) {
	const d = new Float32Array(len);
	let v = 0, target = 0;
	const step = Math.max(1, Math.round(SR / rate));
	for (let i = 0; i < len; i++) {
		if (i % step === 0) target = noise();
		v += (target - v) * (rate / SR) * 4;
		d[i] = v;
	}
	return d;
}
/**
 * A motor / servo whirr: a narrow pulse train (every harmonic, so it buzzes rather than rings) at f(t) Hz with a jittered
 * pitch, mixed with noise band-passed around its 4th harmonic, low-passed at lp(t). Never a sine.
 */
function whirr(sec, f, lp, jitter = 0.03) {
	const b = buf(sec);
	const d = drift(b.length, 30);
	let ph = 0, cycle = -1, width = 0.18, amp = 1;
	for (let i = 0; i < b.length; i++) {
		const t = i / b.length;
		ph += f(t) * (1 + jitter * d[i] + 0.04 * noise()) / SR;
		if (Math.floor(ph) !== cycle) { cycle = Math.floor(ph); width = 0.1 + 0.2 * rnd(); amp = 0.6 + 0.4 * rnd(); } // a rough, uneven motor
		const frac = ph - Math.floor(ph);
		b[i] = ((frac < width ? 1 : 0) - width) * amp;
	}
	const body = filt(filt(b, 'lp', lp), 'hp', () => 90);
	const hiss = filt(noiseBuf(sec), 'bp', t => f(t) * 4, 1.2);
	const air = filt(noiseBuf(sec), 'lp', lp);
	const o = buf(sec);
	mix(o, body, 0, 1);
	mix(o, hiss, 0, 0.9);
	mix(o, air, 0, 0.35);
	return o;
}
/** Electric crackle: sparse, very short band-passed noise ticks, density(t) per second. */
function crackle(sec, density, lo = 2500, hi = 7000) {
	const o = buf(sec);
	const tickLen = Math.round(0.004 * SR);
	for (let i = 0; i < o.length; i++) {
		const t = i / o.length;
		if (rnd() < density(t) / SR) {
			const hz = lo + rnd() * (hi - lo);
			const tick = filt(noiseBuf(tickLen / SR), 'bp', () => hz, 2.5);
			const g = 0.4 + 0.6 * rnd();
			for (let k = 0; k < tick.length && i + k < o.length; k++) o[i + k] += tick[k] * g * Math.pow(1 - k / tick.length, 3);
		}
	}
	return o;
}
/** A soft low thump: low-passed noise with a falling cutoff (no sine body), ~0.15 s. */
function thump(sec = 0.16, top = 900) {
	return env(filt(filt(noiseBuf(sec), 'lp', t => top * Math.pow(1 - t, 2) + 80), 'lp', () => 400), t => Math.min(1, t * 80) * Math.pow(1 - t, 3));
}
const PEAK = Math.pow(10, -6 / 20); // -6 dBFS
function finish(x, fadeIn = 0.004, fadeOut = 0.03) {
	let peak = 0;
	for (const v of x) peak = Math.max(peak, Math.abs(v));
	const k = peak > 0 ? PEAK / peak : 1;
	for (let i = 0; i < x.length; i++) x[i] *= k;
	const fi = Math.round(fadeIn * SR), fo = Math.round(fadeOut * SR);
	for (let i = 0; i < fi && i < x.length; i++) x[i] *= 0.5 - 0.5 * Math.cos(Math.PI * i / fi);
	for (let i = 0; i < fo && i < x.length; i++) x[x.length - 1 - i] *= 0.5 - 0.5 * Math.cos(Math.PI * i / fo);
	return x;
}

// ---------------------------------------------------------------- the sounds
function coreUp() {
	const len = 0.55;
	const o = buf(len);
	// the whirr spinning up: 70 -> 210 Hz pulse, the filter opening, swelling in then easing off
	mix(o, env(whirr(len, t => 70 + 140 * Math.pow(t, 0.8), t => 700 + 1900 * t), t => Math.min(1, t * 6) * Math.pow(1 - t, 0.9)), 0, 0.8);
	// a breath of air rising with it
	mix(o, env(filt(noiseBuf(len), 'bp', t => 900 + 2600 * t, 1.2), t => Math.sin(Math.PI * t)), 0, 0.25);
	return finish(sat(o, 1.3), 0.006, 0.08);
}
function transform() {
	const len = 0.95;
	const o = buf(len);
	mix(o, thump(0.18, 1100), 0, 1.0);
	// the whoosh-zap: noise swept up fast, bright at the top, wrapping round the body
	const sw = 0.55;
	mix(o, env(filt(noiseBuf(sw), 'bp', t => 350 + 6500 * Math.pow(t, 1.6), 1.4), t => Math.pow(Math.sin(Math.PI * Math.min(1, t * 1.15)), 1.2)), 0.05, 0.9);
	mix(o, env(filt(noiseBuf(sw), 'hp', t => 1500 + 5000 * t), t => Math.pow(t, 2) * Math.pow(1 - t, 0.5)), 0.05, 0.35);
	// crackle riding the sweep and fizzing out after it
	mix(o, crackle(0.8, t => 260 * Math.sin(Math.PI * Math.min(1, t * 1.4))), 0.08, 0.55);
	// the energy settling: a soft airy tail
	mix(o, env(filt(noiseBuf(0.4), 'bp', () => 5200, 0.9), t => Math.pow(1 - t, 2.5)), 0.52, 0.25);
	return finish(sat(o, 1.4), 0.002, 0.12);
}
function powerDown(len, zap, sputter) {
	const o = buf(len);
	// the whirr winding down: 230 -> 45 Hz, the filter closing
	mix(o, env(whirr(len, t => 230 * Math.pow(0.2, t), t => 2400 * Math.pow(1 - t, 1.5) + 250, 0.05), t => Math.min(1, t * 20) * Math.pow(1 - t, 1.1)), 0.03, 0.8);
	// the red flash zap
	mix(o, env(filt(noiseBuf(0.22), 'bp', t => 6000 - 5000 * t, 1.3), t => Math.pow(1 - t, 2)), 0, zap);
	mix(o, crackle(0.3, t => 400 * Math.pow(1 - t, 1.5)), 0, zap * 0.6);
	if (sputter) {
		// the last of the power stuttering out
		const gate = Math.round(0.035 * SR);
		const start = Math.round(len * 0.55 * SR);
		for (let i = start; i < o.length; i++) if (Math.floor((i - start) / gate) % 3 === 1) o[i] *= 0.25;
	}
	return finish(sat(o, 1.3), 0.002, 0.1);
}
const revert = () => powerDown(0.7, 0.45, false);
const timeout = () => powerDown(1.2, 0.8, true);
function warning() {
	// one rounded blip: a soft, slightly detuned pulse voice at ~760 Hz (low-passed) inside a band of breathy noise at the same
	// pitch, so it reads as an electronic "bip", not a sine ping; an 8 ms attack
	const len = 0.11;
	const o = buf(len);
	for (const hz of [760, 766]) {
		const b = buf(len);
		let ph = rnd();
		for (let i = 0; i < b.length; i++) { ph += hz * (1 + 0.01 * noise()) / SR; b[i] = (ph % 1) < 0.35 ? 0.65 : -0.35; }
		mix(o, filt(b, 'lp', () => 1400, 0.6), 0, 0.3);
	}
	mix(o, filt(noiseBuf(len), 'bp', () => 780, 5), 0, 1.4);
	mix(o, filt(noiseBuf(len), 'bp', () => 1600, 1.5), 0, 0.15);
	env(o, t => Math.min(1, t / 0.075) * Math.pow(1 - t, 1.6));
	return finish(sat(o, 1.1), 0.008, 0.03);
}
function denied() {
	// a dull low buzz: a 98 Hz pulse train muffled to 650 Hz, with a slight 24 Hz flutter
	const len = 0.3;
	const o = whirr(len, () => 98, () => 650, 0.02);
	env(o, t => Math.min(1, t * 25) * (0.8 + 0.2 * Math.sin(t * len * 2 * Math.PI * 24)) * Math.pow(1 - t, 0.7));
	return finish(sat(o, 1.6), 0.008, 0.06);
}

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
	const w = path.join(WAV_DIR, name + '.wav');
	wav(w, x);
	if (FFMPEG) execFileSync(FFMPEG, ['-y', '-loglevel', 'error', '-i', w, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5', path.join(OUT_DIR, name + '.ogg')]);
	console.log(name, (x.length / SR).toFixed(2) + ' s', FFMPEG ? '-> ogg' : '(wav only)');
}
const ALL = { core_up: coreUp, transform, revert, timeout, warning, denied };
for (const [name, fn] of Object.entries(ALL)) save(name, fn());
