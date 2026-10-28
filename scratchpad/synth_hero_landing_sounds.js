// v0.17.1 Hero Landing: synthesises the superhero landing's sounds -- the heavy impact thud every landing plays and the
// element layers (hard-light hum, thunder crack, cosmic whoosh) -- as 44.1 kHz mono WAVs, then OGG Vorbis with an ffmpeg that
// has libvorbis (%LOCALAPPDATA%\Medal\recorder-*\ffmpeg7.exe). Same pipeline as synth_omnitrix_sounds.js.
//   node scratchpad/synth_hero_landing_sounds.js "<ffmpeg.exe with libvorbis>"
// Output: src/main/resources/assets/projecthero/sounds/hero_landing/*.ogg
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');

const SR = 44100;
const FFMPEG = process.argv[2];
const WAV_DIR = path.join(os.tmpdir(), 'hero_landing_wav');
const OUT_DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds', 'hero_landing');
fs.mkdirSync(WAV_DIR, { recursive: true });
fs.mkdirSync(OUT_DIR, { recursive: true });

let seed = 4242;
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
const buf = sec => new Float32Array(Math.ceil(sec * SR));
function noiseBuf(sec) { const b = buf(sec); for (let i = 0; i < b.length; i++) b[i] = noise(); return b; }
function mix(dst, src, at, gain = 1) { const o = Math.round(at * SR); for (let i = 0; i < src.length && i + o < dst.length; i++) dst[i + o] += src[i] * gain; }
function env(x, fn) { for (let i = 0; i < x.length; i++) x[i] *= fn(i / x.length); return x; }
function tone(sec, f, a, harmonics = [1], wave = 'sin') {
	const b = buf(sec);
	let ph = 0;
	for (let i = 0; i < b.length; i++) {
		const t = i / b.length;
		ph += 2 * Math.PI * f(t) / SR;
		let v = 0;
		harmonics.forEach((h, k) => {
			const p = ph * (k + 1);
			v += (wave === 'sq' ? Math.sign(Math.sin(p)) * 0.6 : Math.sin(p)) * h;
		});
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
	if (FFMPEG) execFileSync(FFMPEG, ['-y', '-loglevel', 'error', '-i', w, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '6', path.join(OUT_DIR, name + '.ogg')]);
	console.log(name, (x.length / SR).toFixed(2) + ' s', FFMPEG ? '-> ogg' : '(wav only)');
}
const click = (len, hz) => env(sweep(noiseBuf(len), 'bp', () => hz, 3), t => Math.pow(1 - t, 4));
function thump(len, hz, drop = 0.6) { return tone(len, t => hz * (1.5 - drop * Math.min(1, t * 3)), t => Math.pow(1 - t, 2.4), [1, 0.3]); }

// ---------------------------------------------------------------- the landing
/** The heavy impact thud: a sub drop, a body of crushed ground (low-passed noise) and a sharp crack on top. */
function impact() {
	const o = buf(1.3);
	mix(o, tone(1.0, t => 62 * (1.6 - 0.7 * Math.min(1, t * 4)), t => Math.pow(1 - t, 2.2), [1, 0.45, 0.15]), 0, 1.25);
	mix(o, env(sweep(noiseBuf(0.9), 'lp', t => 1800 * Math.pow(1 - t, 2) + 120), t => Math.min(1, t * 60) * Math.pow(1 - t, 2.6)), 0, 1.0);
	mix(o, click(0.025, 2400), 0, 0.9);
	mix(o, click(0.02, 900), 0.004, 0.7);
	// debris rattle settling
	for (let k = 0; k < 9; k++) mix(o, click(0.012, 1400 + rnd() * 2600), 0.08 + rnd() * 0.55, 0.12 + rnd() * 0.12);
	return normalise(softClip(o, 1.9), 0.95);
}
/** Hard light: a chord hum that swells out of the hit and shimmers away. */
function hardLight() {
	const o = buf(1.4);
	for (const [hz, g] of [[220, 0.6], [330, 0.4], [440, 0.3], [660, 0.18]]) {
		mix(o, tone(1.3, t => hz * (1 + 0.004 * Math.sin(t * 90)), t => Math.min(1, t * 14) * Math.pow(1 - t, 1.8), [1, 0.3, 0.12]), 0.01, g);
	}
	mix(o, env(sweep(noiseBuf(1.0), 'bp', t => 5200 - 2500 * t, 4), t => Math.min(1, t * 8) * Math.pow(1 - t, 2)), 0.02, 0.25);
	mix(o, tone(0.5, t => 1320 * Math.pow(2, -t), t => Math.min(1, t * 30) * Math.pow(1 - t, 3)), 0, 0.3);
	return normalise(softClip(o, 1.3), 0.85);
}
/** A thunder crack: a sharp electric snap into a rolling low rumble. */
function thunder() {
	const o = buf(2.2);
	mix(o, env(sweep(noiseBuf(0.18), 'hp', () => 2200), t => Math.pow(1 - t, 3)), 0, 1.0);
	for (let k = 0; k < 6; k++) mix(o, click(0.01, 3000 + rnd() * 3000), rnd() * 0.08, 0.6);
	mix(o, env(sweep(noiseBuf(2.1), 'lp', t => 420 * Math.pow(1 - t, 1.5) + 60), t => Math.min(1, t * 12) * Math.pow(1 - t, 1.4) * (0.7 + 0.3 * Math.sin(t * 40))), 0.04,
		1.3);
	mix(o, tone(1.6, t => 48 + 10 * (1 - t), t => Math.min(1, t * 8) * Math.pow(1 - t, 2)), 0.03, 0.8);
	return normalise(softClip(o, 1.8), 0.95);
}
/** A cosmic whoosh: a bright filtered sweep falling away with a starry shimmer. */
function cosmic() {
	const o = buf(1.4);
	mix(o, env(sweep(noiseBuf(1.3), 'bp', t => 300 + 4200 * Math.pow(1 - t, 2), 1.6), t => Math.min(1, t * 10) * Math.pow(1 - t, 1.6)), 0, 1.0);
	mix(o, tone(1.2, t => 880 * Math.pow(2, -1.3 * t), t => Math.min(1, t * 20) * Math.pow(1 - t, 2.2), [1, 0.5, 0.25]), 0.01, 0.35);
	for (let k = 0; k < 12; k++) mix(o, tone(0.12, () => 2400 + rnd() * 3000, t => Math.pow(1 - t, 3)), 0.05 + rnd() * 0.8, 0.08);
	return normalise(softClip(o, 1.3), 0.85);
}

const ALL = { impact, hardlight: hardLight, thunder, cosmic };
for (const [name, fn] of Object.entries(ALL)) save(name, fn());
