// v0.17.1 Quirk framework: synthesises the shared quirk_* sounds -- whoosh (2 takes), impact (2), heavy impact with a
// sub-bass thump, the charge-up (a one-shot rise) and its seamless charge loop, the Plus Ultra ultimate sting, the
// red-zone meter warning, the combo chime and the "gauge full" shimmer -- as 44.1 kHz mono WAVs, then encodes them to
// OGG Vorbis with ffmpeg (needs an ffmpeg WITH libvorbis: Minecraft needs mono files for positional sound, and ffmpeg's
// own "vorbis" encoder is stereo-only). Same pipeline as synth_spiderman_sounds.js: no samples, filtered noise and sines.
//   node scratchpad/synth_quirk_framework_sounds.js "<path to an ffmpeg.exe with libvorbis>"
// Output: src/main/resources/assets/projecthero/sounds/quirk/*.ogg   (WAVs go to the OS temp folder)
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');

const SR = 44100;
const FFMPEG = process.argv[2];
const WAV_DIR = path.join(os.tmpdir(), 'quirk_framework_wav');
const OUT_DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds', 'quirk');
fs.mkdirSync(WAV_DIR, { recursive: true });
fs.mkdirSync(OUT_DIR, { recursive: true });

let seed = 71;
const rnd = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 4294967296; };
const noise = () => rnd() * 2 - 1;

/** A time-varying biquad: f(t) gives the centre / cutoff frequency at t (0..1 through the buffer). */
function sweep(x, type, f, q = 0.707) {
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
function buf(sec) { return new Float32Array(Math.ceil(sec * SR)); }
function noiseBuf(sec) { const b = buf(sec); for (let i = 0; i < b.length; i++) b[i] = noise(); return b; }
function mix(dst, src, at, gain = 1) { const o = Math.round(at * SR); for (let i = 0; i < src.length && i + o < dst.length; i++) dst[i + o] += src[i] * gain; }
function env(x, fn) { for (let i = 0; i < x.length; i++) x[i] *= fn(i / x.length); return x; }
/** A sine whose frequency glides by f(t) (Hz), with amplitude envelope a(t). */
function tone(sec, f, a, harmonics = [1]) {
	const b = buf(sec);
	let ph = 0;
	for (let i = 0; i < b.length; i++) {
		const t = i / b.length;
		ph += 2 * Math.PI * f(t) / SR;
		let v = 0;
		harmonics.forEach((h, k) => { v += Math.sin(ph * (k + 1)) * h; });
		b[i] = v * a(t);
	}
	return b;
}
function softClip(x, k = 1.6) { for (let i = 0; i < x.length; i++) x[i] = Math.tanh(x[i] * k) / Math.tanh(k); return x; }
function normalise(x, peakTarget) {
	let peak = 0;
	for (const v of x) peak = Math.max(peak, Math.abs(v));
	const k = peak > 0 ? peakTarget / peak : 1;
	for (let i = 0; i < x.length; i++) x[i] *= k;
	const f = Math.round(0.003 * SR);
	for (let i = 0; i < f; i++) { x[i] *= i / f; x[x.length - 1 - i] *= i / f; }
	return x;
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
	if (FFMPEG) {
		execFileSync(FFMPEG, ['-y', '-loglevel', 'error', '-i', w, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '6', path.join(OUT_DIR, name + '.ogg')]);
	}
	console.log(name, (x.length / SR).toFixed(2) + ' s', FFMPEG ? '-> ogg' : '(wav only)');
}

/** WHOOSH: a band of air sweeping up then down past the ear, with a soft low body. */
function whoosh(len, lo, hi, q) {
	const x = sweep(noiseBuf(len), 'bp', t => lo + (hi - lo) * Math.sin(Math.PI * Math.min(1, t * 1.1)), q);
	env(x, t => Math.pow(Math.sin(Math.PI * Math.min(1, t)), 1.6));
	const body = env(sweep(noiseBuf(len), 'lp', t => 300 + 500 * Math.sin(Math.PI * t)), t => Math.pow(Math.sin(Math.PI * t), 2) * 0.5);
	mix(x, body, 0);
	return normalise(x, 0.8);
}

/** IMPACT: a hard click, a short lowpassed burst and a low sine thump that drops in pitch. */
function impact(len, thumpHz, burstCut, crack) {
	const out = buf(len);
	const click = env(sweep(noiseBuf(0.012), 'hp', () => 2500), t => 1 - t);
	mix(out, click, 0, crack);
	const burst = env(sweep(noiseBuf(len * 0.5), 'lp', t => burstCut * (1 - 0.7 * t)), t => Math.pow(1 - t, 3));
	mix(out, burst, 0.002, 0.9);
	const thump = tone(len, t => thumpHz * (1.6 - 0.75 * Math.min(1, t * 3)), t => Math.pow(1 - t, 2.5), [1, 0.25]);
	mix(out, thump, 0.0, 1.1);
	return normalise(softClip(out, 1.4), 0.92);
}

/** HEAVY IMPACT: a crack, a big sub-bass thump (60 to 32 Hz) and a rolling rumble tail. */
function heavyImpact() {
	const len = 1.1;
	const out = buf(len);
	mix(out, env(sweep(noiseBuf(0.02), 'hp', () => 1800), t => 1 - t), 0, 1.0);
	mix(out, env(sweep(noiseBuf(0.25), 'lp', t => 2600 * (1 - 0.8 * t)), t => Math.pow(1 - t, 2.2)), 0.003, 1.0);
	mix(out, tone(0.9, t => 60 - 28 * Math.min(1, t * 1.5), t => Math.pow(1 - t, 1.8), [1, 0.35, 0.12]), 0, 1.5);
	mix(out, env(sweep(noiseBuf(1.0), 'lp', t => 220 - 120 * t, 0.9), t => Math.min(1, t * 8) * Math.pow(1 - t, 2)), 0.05, 0.9);
	return normalise(softClip(out, 1.8), 0.95);
}

/** CHARGE UP: a rising hum (two detuned partials and a fifth) with rising filtered air and a quickening flutter. */
function chargeUp() {
	const len = 1.15;
	const out = buf(len);
	const f = t => 95 * Math.pow(2.6, t);
	const tr = t => 0.75 + 0.25 * Math.sin(2 * Math.PI * (4 + 14 * t) * t * len);
	mix(out, tone(len, f, t => Math.min(1, t * 4) * tr(t), [1, 0.5, 0.3, 0.15]), 0, 0.55);
	mix(out, tone(len, t => f(t) * 1.5 * 1.003, t => Math.min(1, t * 3) * 0.6, [1, 0.3]), 0, 0.35);
	mix(out, env(sweep(noiseBuf(len), 'bp', t => 500 + 3500 * t * t, 2.5), t => t * t), 0, 0.5);
	return normalise(softClip(out, 1.2), 0.75);
}

/** CHARGE LOOP: a steady charged hum with a slow pulse -- exactly 2 s, both ends matched so it loops seamlessly. */
function chargeLoop() {
	const len = 2.0;
	const n = Math.round(len * SR);
	const out = new Float32Array(n);
	// every frequency is a whole number of cycles in 2 s, so the loop point is seamless
	const parts = [[110, 0.5], [165, 0.3], [220, 0.22], [331, 0.1], [440, 0.06]];
	for (let i = 0; i < n; i++) {
		const t = i / SR;
		const pulse = 0.8 + 0.2 * Math.sin(2 * Math.PI * 2 * t); // 2 Hz: 4 whole pulses
		let v = 0;
		for (const [hz, a] of parts) v += Math.sin(2 * Math.PI * hz * t) * a;
		out[i] = v * pulse;
	}
	// a bed of air, faded in / out at both ends with a crossfade copy so its seam is hidden
	const air = sweep(noiseBuf(len), 'bp', () => 1400, 1.5);
	const xf = Math.round(0.25 * SR);
	for (let i = 0; i < n; i++) {
		let a = air[i];
		if (i < xf) a = a * (i / xf) + air[n - xf + i] * (1 - i / xf);
		out[i] += a * 0.25;
	}
	let peak = 0;
	for (const v of out) peak = Math.max(peak, Math.abs(v));
	for (let i = 0; i < n; i++) out[i] *= 0.6 / peak;
	return out;
}

/** ULTIMATE STING: a swell (rising air + a chord climbing in), then a big hit with a bright shimmering chord over it. */
function ultimateSting() {
	const len = 2.2;
	const out = buf(len);
	const swell = 0.7;
	mix(out, env(sweep(noiseBuf(swell), 'bp', t => 300 + 4000 * t * t, 1.8), t => t * t * t), 0, 0.8);
	for (const [hz, a] of [[220, 0.4], [277.2, 0.3], [329.6, 0.3]]) {
		mix(out, tone(swell, t => hz * (0.5 + 0.5 * t), t => t * t, [1, 0.4]), 0, a);
	}
	mix(out, heavyImpact(), swell, 0.95);
	// the shimmer: a bright major chord with tremolo, ringing out
	const ring = 1.4;
	for (const [hz, a] of [[440, 0.3], [554.4, 0.22], [659.3, 0.22], [880, 0.12], [1318.5, 0.07]]) {
		mix(out, tone(ring, () => hz, t => Math.pow(1 - t, 1.6) * (0.85 + 0.15 * Math.sin(2 * Math.PI * 7 * t * ring)), [1, 0.2]), swell, a);
	}
	return normalise(softClip(out, 1.3), 0.95);
}

/** METER WARNING: two soft, rounded beeps (a falling pair), gentle enough to hear in a fight without nagging. */
function meterWarning() {
	const out = buf(0.42);
	const beep = hz => tone(0.13, () => hz, t => Math.min(1, t * 12) * Math.pow(1 - t, 1.5), [1, 0.2, 0.05]);
	mix(out, beep(988), 0, 0.8);
	mix(out, beep(740), 0.17, 0.8);
	return normalise(out, 0.6);
}

/** COMBO: a bright two-note chime (E6 then B6) with a short sparkle. */
function combo() {
	const out = buf(0.75);
	const bell = (hz, len) => tone(len, () => hz, t => Math.min(1, t * 40) * Math.pow(1 - t, 2.2), [1, 0.4, 0.18, 0.08]);
	mix(out, bell(1318.5, 0.5), 0, 0.7);
	mix(out, bell(1975.5, 0.6), 0.09, 0.6);
	mix(out, env(sweep(noiseBuf(0.3), 'hp', () => 6000), t => Math.pow(1 - t, 3)), 0.09, 0.12);
	return normalise(out, 0.7);
}

/** ULTRA READY: a rising shimmer arpeggio (the gauge is full). */
function ultraReady() {
	const out = buf(1.0);
	const notes = [523.3, 659.3, 784, 1046.5, 1318.5];
	notes.forEach((hz, i) => {
		mix(out, tone(0.6, () => hz, t => Math.min(1, t * 30) * Math.pow(1 - t, 1.8) * (0.8 + 0.2 * Math.sin(2 * Math.PI * 9 * t * 0.6)), [1, 0.3, 0.1]),
			i * 0.07, 0.45);
	});
	mix(out, env(sweep(noiseBuf(0.9), 'bp', t => 3000 + 5000 * t, 3), t => Math.sin(Math.PI * t) * 0.6), 0.05, 0.25);
	return normalise(out, 0.7);
}

save('whoosh1', whoosh(0.34, 500, 2600, 1.6));
save('whoosh2', whoosh(0.42, 380, 2000, 1.3));
save('impact1', impact(0.32, 95, 2200, 0.8));
save('impact2', impact(0.36, 80, 1700, 0.6));
save('impact_heavy', heavyImpact());
save('charge_up', chargeUp());
save('charge_loop', chargeLoop());
save('ultimate_sting', ultimateSting());
save('meter_warning', meterWarning());
save('combo', combo());
save('ultra_ready', ultraReady());
