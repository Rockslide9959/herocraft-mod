// v0.17.1 Quirk revamp, wave 3 (Overhaul, Dark Shadow, Engine): synthesises the kits' own sounds as 44.1 kHz mono WAVs and
// encodes them to OGG Vorbis with an ffmpeg that has libvorbis (Minecraft needs mono for positional sound; ffmpeg's own
// "vorbis" encoder is stereo-only -- the copy in %LOCALAPPDATA%\Medal\recorder-*\ffmpeg7.exe has libvorbis). Same pipeline as
// synth_quirk_framework_sounds.js: no samples, filtered noise and sines.
//   node scratchpad/synth_quirk_kits3_sounds.js "<path to an ffmpeg.exe with libvorbis>"
// Output: src/main/resources/assets/projecthero/sounds/quirk/{overhaul,dark_shadow,engine}_*.ogg
//   overhaul_disassemble1/2  the crackling disassembly (a fast spray of dry cracks over a gritty crumble)
//   overhaul_spike1/2        a stone spike crunching up out of the ground (thump, grinding grit, a sharp crack)
//   overhaul_reassemble      the "rewind": scattered clicks rushing inward, a rising reversed swell, a clean snap
//   dark_shadow_growl1/2     a low throaty growl (a rough sawtooth voice with a breathy formant and a tremolo)
//   dark_shadow_whoosh1/2    a heavy shadow whoosh (a low air sweep with a cloth / feather flutter)
//   dark_shadow_roar         the berserk roar (layered growl voices rising in pitch, distorted, with a rasping tail)
//   engine_rev               a calf-engine rev (a two-stroke pulse train rising 28 -> 95 Hz with a turbine whine over it)
//   engine_burst             Recipro Burst ignition (a supersonic crack, a sub thump and a rising jet roar)
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');

const SR = 44100;
const FFMPEG = process.argv[2];
const WAV_DIR = path.join(os.tmpdir(), 'quirk_kits3_wav');
const OUT_DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds', 'quirk');
fs.mkdirSync(WAV_DIR, { recursive: true });
fs.mkdirSync(OUT_DIR, { recursive: true });

let seed = 9137;
const rnd = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 4294967296; };
const noise = () => rnd() * 2 - 1;

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
function mix(dst, src, at, gain = 1) { const o = Math.round(at * SR); for (let i = 0; i < src.length && i + o < dst.length; i++) if (i + o >= 0) dst[i + o] += src[i] * gain; }
function env(x, fn) { for (let i = 0; i < x.length; i++) x[i] *= fn(i / x.length); return x; }
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
/** A band-limited-ish sawtooth (summed harmonics) gliding by f(t). */
function saw(sec, f, a, n = 14) {
	const h = [];
	for (let k = 1; k <= n; k++) h.push(1 / k);
	return tone(sec, f, a, h);
}
function softClip(x, k = 1.6) { for (let i = 0; i < x.length; i++) x[i] = Math.tanh(x[i] * k) / Math.tanh(k); return x; }
function reverse(x) { const y = new Float32Array(x.length); for (let i = 0; i < x.length; i++) y[i] = x[x.length - 1 - i]; return y; }
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

/** One dry crack: a tiny highpassed click with a short body at {@code hz}. */
function crack(hz, len = 0.018) {
	const c = env(sweep(noiseBuf(len), 'bp', () => hz, 2.5), t => Math.pow(1 - t, 3));
	return c;
}

// ================================================================ Overhaul

/** DISASSEMBLE: dozens of dry cracks spraying out over 0.5 s (dense at first), over a gritty low crumble. */
function disassemble(variant) {
	const len = 0.62;
	const out = buf(len);
	const n = 46 + variant * 8;
	for (let i = 0; i < n; i++) {
		const t = Math.pow(rnd(), 1.8) * 0.48;
		mix(out, crack(1400 + rnd() * 4200, 0.008 + rnd() * 0.02), t, (0.5 + rnd() * 0.5) * (1 - t * 1.2));
	}
	// the crumble: lowpassed grit that thins out
	const grit = env(sweep(noiseBuf(len), 'bp', t => 700 - 400 * t, 0.9), t => Math.min(1, t * 30) * Math.pow(1 - t, 1.6));
	mix(out, grit, 0, 0.7);
	// a wet tearing undertone
	mix(out, env(sweep(noiseBuf(0.3), 'lp', t => 420 - 200 * t), t => Math.pow(1 - t, 2)), 0.01, 0.6);
	mix(out, tone(0.25, t => 95 - 40 * t, t => Math.pow(1 - t, 3), [1, 0.3]), 0, 0.5);
	return normalise(softClip(out, 1.5), 0.9);
}

/** SPIKE: a ground thump, stone grinding up for a beat, then a sharp crack as the tip breaks through. */
function spike(variant) {
	const len = 0.7;
	const out = buf(len);
	mix(out, tone(0.4, t => 75 - 35 * Math.min(1, t * 2), t => Math.pow(1 - t, 2.2), [1, 0.4, 0.15]), 0, 1.1);
	const grind = env(sweep(noiseBuf(0.35), 'bp', t => 500 + 1400 * t, 1.2), t => Math.sin(Math.PI * t) * (0.7 + 0.3 * Math.sin(t * 90)));
	mix(out, grind, 0.02, 0.9);
	for (let i = 0; i < 12; i++) {
		mix(out, crack(900 + rnd() * 2500, 0.01 + rnd() * 0.015), 0.04 + rnd() * 0.28, 0.4);
	}
	// the break-through crack and its ring
	mix(out, env(sweep(noiseBuf(0.05), 'hp', () => 2200 + variant * 400), t => Math.pow(1 - t, 2)), 0.3 + variant * 0.03, 1.0);
	mix(out, env(sweep(noiseBuf(0.3), 'lp', t => 1800 - 1400 * t), t => Math.pow(1 - t, 3)), 0.31, 0.6);
	return normalise(softClip(out, 1.6), 0.92);
}

/** REASSEMBLE: scattered clicks rushing INWARD (sparse to dense), a reversed swell, and a clean snap shut. */
function reassemble() {
	const len = 0.95;
	const out = buf(len);
	for (let i = 0; i < 60; i++) {
		const t = 0.75 * (1 - Math.pow(rnd(), 2.2));
		mix(out, crack(2000 + rnd() * 3500, 0.006 + rnd() * 0.01), t, 0.25 + 0.6 * (t / 0.75));
	}
	// a reversed crumble swelling into the snap
	const swell = reverse(env(sweep(noiseBuf(0.7), 'bp', t => 600 + 2200 * t, 0.8), t => Math.pow(1 - t, 2.5)));
	mix(out, swell, 0.08, 0.8);
	mix(out, tone(0.72, t => 240 + 520 * t * t, t => Math.pow(t, 2.5) * 0.6, [1, 0.5, 0.2]), 0.06, 0.7);
	// the snap: a clean click and a short glassy ring
	mix(out, env(sweep(noiseBuf(0.02), 'hp', () => 3200), t => 1 - t), 0.78, 1.0);
	mix(out, tone(0.17, () => 1180, t => Math.pow(1 - t, 3), [1, 0.4, 0.25]), 0.78, 0.45);
	mix(out, tone(0.2, t => 90 - 30 * t, t => Math.pow(1 - t, 2.5)), 0.78, 0.7);
	return normalise(softClip(out, 1.3), 0.88);
}

// ================================================================ Dark Shadow

/** A growl voice: a rough low sawtooth with jitter, through a breathy vocal formant, with a slow tremolo. */
function growlVoice(len, f0, f1, rough) {
	const v = saw(len, t => (f0 + (f1 - f0) * t) * (1 + 0.04 * Math.sin(t * 60) + 0.03 * noise()), t => 1, 18);
	const breath = noiseBuf(len);
	for (let i = 0; i < v.length; i++) v[i] = v[i] * (1 - rough) + breath[i] * rough * 0.6 * Math.abs(v[i]);
	let x = sweep(v, 'bp', t => 520 + 180 * Math.sin(t * 9), 1.1);
	const low = sweep(v, 'lp', () => 260);
	for (let i = 0; i < x.length; i++) x[i] = x[i] * 0.9 + low[i] * 0.8;
	return x;
}

function growl(variant) {
	const len = 1.25;
	const x = growlVoice(len, 62 + variant * 6, 55 + variant * 4, 0.35);
	env(x, t => Math.min(1, t * 6) * Math.pow(1 - t, 0.8) * (0.65 + 0.35 * Math.sin(t * 2 * Math.PI * (7 + variant))));
	return normalise(softClip(x, 2.2), 0.85);
}

/** WHOOSH: a low, heavy air sweep (deeper than the framework's) with a cloth / feather flutter riding it. */
function shadowWhoosh(variant) {
	const len = 0.55 + variant * 0.08;
	const x = sweep(noiseBuf(len), 'bp', t => 180 + 900 * Math.sin(Math.PI * Math.min(1, t * 1.15)), 0.9);
	env(x, t => Math.pow(Math.sin(Math.PI * t), 1.4));
	const flutter = env(sweep(noiseBuf(len), 'bp', () => 1300 + variant * 200, 2), t => Math.sin(Math.PI * t) * (0.5 + 0.5 * Math.sin(t * len * 2 * Math.PI * 38)));
	mix(x, flutter, 0, 0.35);
	mix(x, env(sweep(noiseBuf(len), 'lp', () => 140), t => Math.sin(Math.PI * t)), 0, 1.1);
	return normalise(x, 0.85);
}

/** ROAR: two growl voices an octave apart rising in pitch, distorted, a rasping noise tail. */
function roar() {
	const len = 1.9;
	const out = buf(len);
	const a = growlVoice(len, 70, 120, 0.4);
	const b = growlVoice(len, 140, 210, 0.5);
	env(a, t => Math.min(1, t * 4) * Math.pow(1 - t, 0.7));
	env(b, t => Math.min(1, t * 3) * Math.pow(1 - t, 1.0) * 0.6);
	mix(out, a, 0, 1.0);
	mix(out, b, 0.03, 0.7);
	const rasp = env(sweep(noiseBuf(len), 'bp', t => 900 + 700 * t, 0.7), t => Math.min(1, t * 2) * Math.pow(1 - t, 1.2) * 0.6);
	mix(out, rasp, 0, 0.7);
	mix(out, tone(0.6, t => 48 - 10 * t, t => Math.pow(1 - t, 2)), 0, 0.8);
	return normalise(softClip(out, 2.6), 0.95);
}

// ================================================================ Engine

/** REV: a two-stroke pulse train (sharp pops) rising 28 -> 95 Hz, its exhaust bark, and a turbine whine climbing over it. */
function engineRev() {
	const len = 1.05;
	const out = buf(len);
	let t = 0;
	while (t < len - 0.02) {
		const k = t / len;
		const hz = 28 + 67 * Math.pow(Math.min(1, k * 1.3), 0.8) - (k > 0.85 ? 25 * (k - 0.85) / 0.15 : 0);
		const pop = env(sweep(noiseBuf(0.03), 'lp', () => 1300 + 2200 * k), x => Math.pow(1 - x, 4));
		mix(out, pop, t, 0.8);
		mix(out, tone(0.03, () => hz * 2.1, x => Math.pow(1 - x, 3)), t, 0.4);
		t += 1 / hz;
	}
	const bark = env(sweep(noiseBuf(len), 'bp', k => 220 + 380 * k, 1.4), k => Math.min(1, k * 10) * Math.pow(1 - k, 0.6));
	mix(out, bark, 0, 0.6);
	mix(out, tone(len, k => 900 + 2600 * Math.pow(k, 1.3), k => Math.min(1, k * 2) * Math.pow(1 - k, 0.5) * 0.22, [1, 0.3]), 0, 1.0);
	return normalise(softClip(out, 1.8), 0.9);
}

/** BURST: a supersonic crack, a sub thump, then a rising jet roar (lowpassed noise opening up) with a whine. */
function engineBurst() {
	const len = 1.6;
	const out = buf(len);
	mix(out, env(sweep(noiseBuf(0.03), 'hp', () => 2600), t => 1 - t), 0, 1.0);
	mix(out, env(sweep(noiseBuf(0.25), 'lp', t => 3000 * (1 - 0.8 * t)), t => Math.pow(1 - t, 2)), 0.004, 0.9);
	mix(out, tone(0.7, t => 58 - 24 * Math.min(1, t * 1.6), t => Math.pow(1 - t, 1.8), [1, 0.3]), 0, 1.4);
	const jet = env(sweep(noiseBuf(len), 'lp', t => 400 + 5200 * Math.pow(t, 0.7)), t => Math.min(1, t * 3) * Math.pow(1 - t, 0.7));
	mix(out, jet, 0.05, 0.9);
	mix(out, tone(len - 0.1, t => 1400 + 2400 * t, t => Math.min(1, t * 2.5) * Math.pow(1 - t, 0.8) * 0.2, [1, 0.25]), 0.08, 1.0);
	return normalise(softClip(out, 1.7), 0.95);
}

save('overhaul_disassemble1', disassemble(0));
save('overhaul_disassemble2', disassemble(1));
save('overhaul_spike1', spike(0));
save('overhaul_spike2', spike(1));
save('overhaul_reassemble', reassemble());
save('dark_shadow_growl1', growl(0));
save('dark_shadow_growl2', growl(1));
save('dark_shadow_whoosh1', shadowWhoosh(0));
save('dark_shadow_whoosh2', shadowWhoosh(1));
save('dark_shadow_roar', roar());
save('engine_rev', engineRev());
save('engine_burst', engineBurst());
