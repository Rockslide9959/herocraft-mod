// v0.17.1 Erasure + Zero Gravity revamp: synthesises the two kits' own sounds -- Eraser Head's scarf whip (2 takes), scarf
// snap (2), the stare's "glare" hum, the bind wrap and the goggles click; Uravity's touch pip (2), the float chime, the
// "Release!" clap-and-thud, the meteor rumble and the weightless push -- as 44.1 kHz mono WAVs, encoded to OGG Vorbis with an
// ffmpeg that has libvorbis (Minecraft needs mono for positional sound; the framework helper found Medal's ffmpeg7.exe). The
// same pipeline and helpers as synth_quirk_framework_sounds.js: no samples, filtered noise and sines.
//   node scratchpad/synth_quirk_erasure_zerogravity_sounds.js "%LOCALAPPDATA%/Medal/recorder-*/ffmpeg7.exe"
// Output: src/main/resources/assets/projecthero/sounds/quirk/erasure_*.ogg, zerogravity_*.ogg   (WAVs go to the OS temp folder)
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');

const SR = 44100;
const FFMPEG = process.argv[2];
const WAV_DIR = path.join(os.tmpdir(), 'quirk_erasure_zerogravity_wav');
const OUT_DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds', 'quirk');
fs.mkdirSync(WAV_DIR, { recursive: true });
fs.mkdirSync(OUT_DIR, { recursive: true });

let seed = 1337;
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


// ================================================================ Erasure (Eraser Head)

/** SCARF WHIP: the capture cloth cutting the air -- a fast rising whoosh of coarse fabric -- into a sharp crack at the tip. */
function scarfWhip(len, crackAt, pitch) {
	const out = buf(len);
	const air = sweep(noiseBuf(crackAt + 0.02), 'bp', t => (700 + 3200 * Math.pow(t, 1.6)) * pitch, 1.4);
	env(air, t => Math.pow(t, 1.5) * (1 - Math.pow(t, 8)));
	mix(out, air, 0, 0.9);
	// cloth flutter: amplitude-modulated low band
	const flutter = sweep(noiseBuf(crackAt), 'bp', () => 420 * pitch, 2.2);
	env(flutter, t => (0.5 + 0.5 * Math.sin(2 * Math.PI * (18 + 30 * t) * t * crackAt)) * Math.pow(t, 0.8) * 0.6);
	mix(out, flutter, 0, 0.7);
	// the crack: a broadband snap with a tiny ring
	const crack = env(sweep(noiseBuf(0.05), 'hp', () => 2400 * pitch), t => Math.pow(1 - t, 5));
	mix(out, crack, crackAt, 1.4);
	mix(out, tone(0.07, () => 1900 * pitch, t => Math.pow(1 - t, 4), [1, 0.4]), crackAt, 0.25);
	const tail = env(sweep(noiseBuf(len - crackAt), 'lp', t => 1600 * (1 - 0.7 * t)), t => Math.pow(1 - t, 3) * 0.5);
	mix(out, tail, crackAt + 0.01, 0.6);
	return normalise(softClip(out, 1.5), 0.9);
}

/** SCARF SNAP: the cloth pulled taut round something -- a short, dry fabric snap and a soft thump. */
function scarfSnap(pitch) {
	const out = buf(0.28);
	mix(out, env(sweep(noiseBuf(0.03), 'bp', () => 2600 * pitch, 1.1), t => Math.pow(1 - t, 3)), 0, 1.3);
	mix(out, env(sweep(noiseBuf(0.12), 'bp', t => 900 * pitch * (1 - 0.4 * t), 1.6), t => Math.pow(1 - t, 2.5)), 0.008, 0.8);
	mix(out, tone(0.16, t => 140 * (1.3 - 0.5 * t), t => Math.pow(1 - t, 3), [1, 0.2]), 0.004, 0.6);
	return normalise(softClip(out, 1.3), 0.85);
}

/** GLARE: the stare catching hold -- a low electric hum swelling under a thin rising whine, a soft sizzle on top. */
function glare() {
	const len = 0.9;
	const out = buf(len);
	mix(out, tone(len, t => 92 + 8 * t, t => Math.sin(Math.PI * Math.min(1, t * 1.6)) * (1 - 0.3 * t), [1, 0.55, 0.35, 0.2, 0.12]), 0, 0.8);
	mix(out, tone(len, t => 1250 + 900 * t, t => Math.pow(Math.sin(Math.PI * t), 2) * 0.35), 0, 0.5);
	const sizzle = env(sweep(noiseBuf(len), 'hp', () => 5200), t => Math.pow(Math.sin(Math.PI * t), 3) * 0.3);
	for (let i = 0; i < sizzle.length; i++) sizzle[i] *= 0.6 + 0.4 * Math.sin(i / SR * 2 * Math.PI * 60);
	mix(out, sizzle, 0, 0.6);
	return normalise(softClip(out, 1.6), 0.75);
}

/** BIND: the scarf winding round a body -- a rushing rustle that tightens (rising) into a cinch. */
function bindWrap() {
	const len = 0.55;
	const out = buf(len);
	const rustle = sweep(noiseBuf(0.42), 'bp', t => 500 + 1500 * t, 1.2);
	env(rustle, t => (0.55 + 0.45 * Math.sin(2 * Math.PI * (9 + 14 * t) * t * 0.42)) * Math.sin(Math.PI * Math.min(1, t * 1.05)));
	mix(out, rustle, 0, 0.9);
	mix(out, env(sweep(noiseBuf(0.06), 'bp', () => 1800, 1.3), t => Math.pow(1 - t, 3)), 0.4, 1.2);
	mix(out, tone(0.14, t => 170 - 60 * t, t => Math.pow(1 - t, 3)), 0.4, 0.5);
	return normalise(softClip(out, 1.3), 0.8);
}

/** GOGGLES: the yellow goggles snapping down -- a plastic click, a strap snap, a short high glint. */
function goggles() {
	const out = buf(0.3);
	mix(out, env(sweep(noiseBuf(0.012), 'hp', () => 3500), t => 1 - t), 0, 1.2);
	mix(out, tone(0.05, () => 2100, t => Math.pow(1 - t, 3), [1, 0.5, 0.2]), 0, 0.4);
	mix(out, env(sweep(noiseBuf(0.05), 'bp', () => 1200, 1.4), t => Math.pow(1 - t, 3)), 0.035, 0.7);
	mix(out, tone(0.18, t => 2600 + 400 * t, t => Math.pow(1 - t, 2.5) * 0.25), 0.05, 0.5);
	return normalise(out, 0.7);
}

// ================================================================ Zero Gravity (Uravity)

/** TOUCH: the fingertips meeting it -- a soft round "pip" (a sine bubble rising) with a gentle sparkle. */
function touch(base) {
	const out = buf(0.35);
	mix(out, tone(0.2, t => base * (1 + 0.45 * Math.min(1, t * 4)), t => Math.min(1, t * 60) * Math.pow(1 - t, 2.2), [1, 0.15]), 0, 0.9);
	mix(out, tone(0.3, () => base * 3.01, t => Math.min(1, t * 40) * Math.pow(1 - t, 3) * 0.3), 0.01, 0.5);
	mix(out, env(sweep(noiseBuf(0.2), 'hp', () => 7000), t => Math.pow(1 - t, 4) * 0.15), 0.01, 0.4);
	return normalise(out, 0.7);
}

/** FLOAT: something goes weightless -- a soft rising chime (a pentatonic run of bells) over an airy swell. */
function floatChime() {
	const len = 1.0;
	const out = buf(len);
	const notes = [659.3, 784, 987.8, 1318.5];
	notes.forEach((hz, i) => {
		mix(out, tone(0.7, () => hz, t => Math.min(1, t * 50) * Math.pow(1 - t, 2.4), [1, 0.35, 0.12]), i * 0.06, 0.4 - i * 0.05);
	});
	mix(out, env(sweep(noiseBuf(0.9), 'bp', t => 1200 + 2600 * t, 2.5), t => Math.sin(Math.PI * t) * 0.25), 0, 0.5);
	return normalise(out, 0.65);
}

/** RELEASE: "Release!" -- the fingertips clap (a bright tap), a beat, then a heavy low thud of everything landing. */
function releaseThud() {
	const len = 1.1;
	const out = buf(len);
	mix(out, env(sweep(noiseBuf(0.03), 'bp', () => 2800, 1.0), t => Math.pow(1 - t, 4)), 0, 1.0);
	mix(out, tone(0.12, () => 1046.5, t => Math.pow(1 - t, 3) * 0.4, [1, 0.3]), 0, 0.5);
	mix(out, tone(0.8, t => 58 - 26 * Math.min(1, t * 1.6), t => Math.pow(1 - t, 1.9), [1, 0.4, 0.15]), 0.12, 1.5);
	mix(out, env(sweep(noiseBuf(0.35), 'lp', t => 1800 * (1 - 0.8 * t)), t => Math.pow(1 - t, 2.5)), 0.12, 1.0);
	mix(out, env(sweep(noiseBuf(0.9), 'lp', t => 260 - 140 * t, 0.9), t => Math.min(1, t * 10) * Math.pow(1 - t, 2)), 0.16, 0.8);
	return normalise(softClip(out, 1.7), 0.95);
}

/** METEOR: rubble tearing up / raining down -- a deep rolling rumble with gritty crackles of stone. */
function meteor() {
	const len = 1.6;
	const out = buf(len);
	mix(out, env(sweep(noiseBuf(len), 'lp', t => 180 + 120 * Math.sin(Math.PI * t), 0.8), t => Math.min(1, t * 6) * Math.pow(1 - t, 1.4)), 0, 1.4);
	mix(out, tone(len, t => 42 + 6 * Math.sin(t * 9), t => Math.min(1, t * 5) * Math.pow(1 - t, 1.6), [1, 0.5]), 0, 0.9);
	for (let i = 0; i < 26; i++) {
		const at = rnd() * (len - 0.1);
		const g = 0.25 + 0.45 * rnd() * (1 - at / len);
		mix(out, env(sweep(noiseBuf(0.03 + rnd() * 0.04), 'bp', () => 700 + rnd() * 1800, 1.5), t => Math.pow(1 - t, 3)), at, g);
	}
	return normalise(softClip(out, 1.5), 0.9);
}

/** PUSH: an airy push off nothing -- a soft rushing whoosh with a little pink sparkle on top. */
function push() {
	const len = 0.5;
	const out = buf(len);
	const air = sweep(noiseBuf(len), 'bp', t => 500 + 1700 * Math.sin(Math.PI * Math.min(1, t * 1.2)), 1.1);
	env(air, t => Math.pow(Math.sin(Math.PI * t), 1.3));
	mix(out, air, 0, 1.0);
	mix(out, tone(0.3, t => 880 + 660 * t, t => Math.min(1, t * 30) * Math.pow(1 - t, 2.5) * 0.25), 0.02, 0.6);
	return normalise(out, 0.75);
}

save('erasure_scarf_whip1', scarfWhip(0.42, 0.24, 1.0));
save('erasure_scarf_whip2', scarfWhip(0.38, 0.21, 1.12));
save('erasure_scarf_snap1', scarfSnap(1.0));
save('erasure_scarf_snap2', scarfSnap(0.86));
save('erasure_glare', glare());
save('erasure_bind', bindWrap());
save('erasure_goggles', goggles());
save('zerogravity_touch1', touch(880));
save('zerogravity_touch2', touch(987.8));
save('zerogravity_float', floatChime());
save('zerogravity_release', releaseThud());
save('zerogravity_meteor', meteor());
save('zerogravity_push', push());
