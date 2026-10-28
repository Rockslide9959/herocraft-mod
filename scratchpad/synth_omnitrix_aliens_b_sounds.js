// v0.17.1 Omnitrix, second batch of aliens: Stinkfly, Wildvine, Ripjaws and Grey Matter's sounds, synthesised (filtered noise
// and sines, no samples) as 44.1 kHz mono WAVs, then OGG Vorbis through an ffmpeg with libvorbis (Minecraft needs mono for
// positional sound; Medal's %LOCALAPPDATA%\Medal\recorder-*\ffmpeg7.exe has it). Same pipeline as synth_omnitrix_sounds.js.
//   node scratchpad/synth_omnitrix_aliens_b_sounds.js "<ffmpeg.exe with libvorbis>"
// Output: src/main/resources/assets/projecthero/sounds/omnitrix/{stinkfly,wildvine,ripjaws,grey_matter}_*.ogg
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');

const SR = 44100;
const FFMPEG = process.argv[2];
const WAV_DIR = path.join(os.tmpdir(), 'omnitrix_b_wav');
const OUT_DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds', 'omnitrix');
fs.mkdirSync(WAV_DIR, { recursive: true });
fs.mkdirSync(OUT_DIR, { recursive: true });

let seed = 2020;
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

// ---------------------------------------------------------------- Stinkfly
function buzz(len, hz, depth) { return tone(len, t => hz + depth * Math.sin(t * len * 2 * Math.PI * 28), t => Math.min(1, t * 12) * Math.pow(1 - t, 0.7), [1, 0.7, 0.5, 0.35, 0.2], 'sq'); }
function sfBuzz() { const o = buf(0.7); mix(o, sweep(buzz(0.7, 190, 25), 'lp', () => 2600), 0, 0.8); mix(o, env(sweep(noiseBuf(0.7), 'bp', () => 1800, 2), t => Math.sin(Math.PI * t)), 0, 0.25); return normalise(o, 0.75); }
function goo(len, lo, hi) { return env(sweep(noiseBuf(len), 'bp', t => lo + (hi - lo) * Math.pow(1 - t, 2), 3), t => Math.min(1, t * 30) * Math.pow(1 - t, 2.2)); }
function sfSpit() { const o = buf(0.4); mix(o, goo(0.25, 600, 2600), 0, 1); mix(o, tone(0.18, t => 420 - 260 * t, t => Math.pow(1 - t, 2), [1, 0.5]), 0.01, 0.5); mix(o, click(0.01, 1600), 0, 0.6); return normalise(softClip(o, 1.4), 0.85); }
function sfSplat() { const o = buf(0.5); mix(o, thump(0.2, 140, 0.7), 0, 0.7); mix(o, goo(0.4, 300, 1800), 0, 1); for (let k = 0; k < 6; k++) mix(o, goo(0.06, 800 + rnd() * 1200, 2000), 0.05 + rnd() * 0.3, 0.3); return normalise(softClip(o, 1.5), 0.85); }
function sfGas() { const o = buf(1.6); mix(o, env(sweep(noiseBuf(1.6), 'hp', t => 2200 - 1200 * t), t => Math.min(1, t * 8) * Math.pow(1 - t, 1.1)), 0, 0.9); mix(o, env(sweep(noiseBuf(1.6), 'lp', () => 400), t => Math.sin(Math.PI * t)), 0, 0.5); mix(o, tone(1.2, t => 70 + 10 * Math.sin(t * 30), t => Math.sin(Math.PI * t) * 0.6, [1, 0.6, 0.4], 'sq'), 0.1, 0.25); return normalise(o, 0.8); }
function sfSting() { const o = buf(0.4); mix(o, env(sweep(noiseBuf(0.12), 'bp', t => 1500 + 4000 * t, 2), t => Math.sin(Math.PI * t)), 0, 0.7); mix(o, click(0.012, 3800), 0.1, 1); mix(o, goo(0.2, 500, 1500), 0.11, 0.5); return normalise(softClip(o, 1.5), 0.85); }
function sfDive() { const o = buf(1.1); mix(o, env(sweep(noiseBuf(0.9), 'bp', t => 600 + 2800 * t, 1.6), t => Math.pow(t, 1.5) * (t > 0.9 ? (1 - t) * 10 : 1)), 0, 0.8); mix(o, sweep(buzz(0.9, 230, 40), 'lp', () => 2000), 0, 0.4); mix(o, sfSplat(), 0.8, 0.8); return normalise(softClip(o, 1.4), 0.9); }

// ---------------------------------------------------------------- Wildvine
function creak(len, hz) { return sweep(tone(len, t => hz * (1 + 0.25 * Math.sin(t * 17)), t => Math.min(1, t * 10) * Math.pow(1 - t, 1.2), [1, 0.8, 0.6, 0.5, 0.4], 'sq'), 'bp', () => hz * 3, 1.4); }
function wvVine() { const o = buf(0.55); mix(o, env(sweep(noiseBuf(0.25), 'bp', t => 700 + 3500 * Math.sin(Math.PI * t), 1.2), t => Math.sin(Math.PI * t)), 0, 0.9); mix(o, click(0.02, 2400), 0.18, 1); mix(o, creak(0.3, 140), 0.2, 0.35); return normalise(softClip(o, 1.4), 0.85); }
function wvSeed() { const o = buf(0.35); mix(o, click(0.01, 1200), 0, 1); mix(o, tone(0.15, t => 500 + 700 * t, t => Math.pow(1 - t, 2), [1, 0.3]), 0, 0.6); mix(o, env(sweep(noiseBuf(0.2), 'bp', () => 1500, 1.5), t => Math.pow(1 - t, 3)), 0.01, 0.4); return normalise(o, 0.8); }
function wvBurst() { const o = buf(0.9); mix(o, thump(0.4, 90, 0.5), 0, 1); mix(o, env(sweep(noiseBuf(0.8), 'lp', t => 4000 * (1 - t) + 300), t => Math.pow(1 - t, 2.5)), 0, 0.9); for (let k = 0; k < 20; k++) mix(o, click(0.006, 2000 + rnd() * 3000), 0.02 + rnd() * 0.4, 0.3 * rnd()); return normalise(softClip(o, 1.7), 0.9); }
function wvRoots() { const o = buf(1.4); mix(o, env(sweep(noiseBuf(1.3), 'lp', t => 250 + 200 * Math.sin(t * 9)), t => Math.min(1, t * 6) * Math.pow(1 - t, 0.9)), 0, 1); mix(o, creak(1.0, 70), 0.05, 0.5); mix(o, creak(0.6, 110), 0.4, 0.35); for (let k = 0; k < 25; k++) mix(o, click(0.01, 400 + rnd() * 1500), 0.05 + rnd() * 1.1, 0.35 * rnd()); return normalise(softClip(o, 1.6), 0.9); }
function wvBloom() { const o = buf(1.2); for (const [hz, at] of [[523, 0], [659, 0.08], [784, 0.16], [1047, 0.26]]) mix(o, tone(0.8, () => hz, t => Math.min(1, t * 20) * Math.pow(1 - t, 2), [1, 0.25, 0.1]), at, 0.35); mix(o, env(sweep(noiseBuf(1.1), 'hp', () => 3500), t => Math.sin(Math.PI * t) * 0.6), 0, 0.4); return normalise(o, 0.75); }
function wvRustle() { const o = buf(0.8); for (let k = 0; k < 30; k++) mix(o, env(sweep(noiseBuf(0.05), 'bp', () => 2500 + rnd() * 3500, 1.2), t => Math.sin(Math.PI * t)), rnd() * 0.72, 0.5 * rnd()); return normalise(o, 0.7); }

// ---------------------------------------------------------------- Ripjaws
function rjBite() { const o = buf(0.45); mix(o, env(sweep(noiseBuf(0.1), 'bp', t => 900 + 1500 * t, 1), t => Math.sin(Math.PI * t)), 0, 0.5); mix(o, click(0.015, 2200), 0.08, 1.1); mix(o, click(0.01, 4000), 0.085, 0.7); mix(o, thump(0.2, 120), 0.08, 0.8); return normalise(softClip(o, 1.7), 0.92); }
function rjRoar() {
	const len = 1.4;
	const o = buf(len);
	const growl = tone(len, t => 110 + 40 * Math.sin(Math.PI * t) + 9 * Math.sin(t * 120), t => Math.min(1, t * 6) * Math.pow(1 - t, 0.9), [1, 0.8, 0.6, 0.5, 0.35, 0.25], 'sq');
	mix(o, sweep(growl, 'lp', () => 1800), 0, 0.8);
	mix(o, env(sweep(noiseBuf(len), 'bp', t => 900 + 400 * Math.sin(Math.PI * t), 1.5), t => Math.min(1, t * 6) * (1 - t)), 0, 0.6); // the wet gurgle
	for (let k = 0; k < 12; k++) mix(o, tone(0.05, () => 300 + rnd() * 500, t => Math.sin(Math.PI * t)), 0.2 + rnd() * 0.9, 0.15);
	return normalise(softClip(o, 2.0), 0.9);
}
function splashN(len, gain) { const b = env(sweep(noiseBuf(len), 'lp', t => 6000 * Math.pow(1 - t, 1.5) + 400), t => Math.min(1, t * 40) * Math.pow(1 - t, 1.8)); for (let i = 0; i < b.length; i++) b[i] *= gain; return b; }
function rjSplash() { const o = buf(1.0); mix(o, splashN(0.9, 1), 0, 1); mix(o, thump(0.2, 80), 0, 0.5); for (let k = 0; k < 20; k++) mix(o, tone(0.04, t => (600 + rnd() * 900) * (1 + t), t => Math.sin(Math.PI * t)), 0.1 + rnd() * 0.7, 0.2); return normalise(softClip(o, 1.4), 0.88); }
function rjSlam() { const o = buf(1.0); mix(o, thump(0.7, 55, 0.4), 0, 1.4); mix(o, click(0.03, 900), 0, 0.8); mix(o, splashN(0.8, 0.7), 0.01, 0.7); return normalise(softClip(o, 1.8), 0.93); }
function rjGasp() { const o = buf(0.7); mix(o, env(sweep(noiseBuf(0.6), 'bp', t => 700 + 1500 * t, 2.5), t => Math.sin(Math.PI * Math.min(1, t * 1.2))), 0, 0.9); mix(o, tone(0.4, t => 220 + 80 * t, t => Math.sin(Math.PI * t) * 0.6, [1, 0.6, 0.4], 'sq'), 0.1, 0.25); return normalise(o, 0.75); }
function rjWhirl() { const len = 1.6; const o = buf(len); mix(o, env(sweep(noiseBuf(len), 'bp', t => 400 + 300 * Math.sin(t * 40), 1.2), t => Math.sin(Math.PI * t)), 0, 1); for (let k = 0; k < 30; k++) mix(o, tone(0.05, t => (400 + rnd() * 600) * (1 + t), t => Math.sin(Math.PI * t)), rnd() * 1.5, 0.15); return normalise(o, 0.8); }

// ---------------------------------------------------------------- Grey Matter
function gmPinch() { const o = buf(0.35); mix(o, click(0.01, 3200), 0, 0.9); mix(o, tone(0.25, t => 1800 - 1200 * t, t => Math.pow(1 - t, 2), [1, 0.4]), 0.01, 0.5); mix(o, env(sweep(noiseBuf(0.2), 'hp', () => 5000), t => Math.pow(1 - t, 4)), 0.01, 0.4); return normalise(o, 0.8); }
function gmScan() { const o = buf(0.9); for (let k = 0; k < 5; k++) mix(o, tone(0.07, () => 1200 + k * 220, t => Math.pow(1 - t, 0.5), [1, 0.2], 'sq'), k * 0.11, 0.3); mix(o, tone(0.3, () => 2100, t => Math.pow(1 - t, 2), [1, 0.3]), 0.58, 0.4); return normalise(o, 0.7); }
function gmGadget() { const o = buf(0.7); for (let k = 0; k < 8; k++) mix(o, click(0.006, 2600 + k * 150), k * 0.04, 0.6); mix(o, tone(0.3, t => 300 + 900 * t, t => Math.sin(Math.PI * t), [1, 0.4, 0.2], 'sq'), 0.3, 0.2); mix(o, click(0.02, 1800), 0.6, 0.9); return normalise(o, 0.78); }
function gmLeap() { const o = buf(0.4); mix(o, tone(0.25, t => 700 + 1400 * t, t => Math.min(1, t * 15) * Math.pow(1 - t, 1.5), [1, 0.3]), 0, 0.6); mix(o, env(sweep(noiseBuf(0.2), 'bp', t => 1500 + 2000 * t, 1.5), t => Math.sin(Math.PI * t)), 0, 0.4); return normalise(o, 0.75); }
function gmRepair() { const o = buf(1.0); for (let k = 0; k < 10; k++) mix(o, click(0.01, 1500 + (k % 2) * 700), 0.05 + k * 0.07, 0.7); mix(o, tone(0.4, () => 1568, t => Math.pow(1 - t, 2), [1, 0.3, 0.1]), 0.75, 0.4); return normalise(o, 0.78); }
function gmThink() { const len = 1.4; const o = buf(len); for (const [hz, a] of [[330, 0.4], [495, 0.3], [660, 0.25]]) mix(o, tone(len, t => hz * (1 + 0.01 * Math.sin(t * 50)), t => Math.sin(Math.PI * t), [1, 0.2]), 0, a); mix(o, env(sweep(noiseBuf(len), 'hp', () => 5000), t => Math.sin(Math.PI * t) * 0.3), 0, 0.3); return normalise(o, 0.7); }

const ALL = {
	stinkfly_buzz: sfBuzz, stinkfly_spit: sfSpit, stinkfly_splat: sfSplat, stinkfly_gas: sfGas, stinkfly_sting: sfSting, stinkfly_dive: sfDive,
	wildvine_vine: wvVine, wildvine_seed: wvSeed, wildvine_burst: wvBurst, wildvine_roots: wvRoots, wildvine_bloom: wvBloom, wildvine_rustle: wvRustle,
	ripjaws_bite: rjBite, ripjaws_roar: rjRoar, ripjaws_splash: rjSplash, ripjaws_slam: rjSlam, ripjaws_gasp: rjGasp, ripjaws_whirl: rjWhirl,
	grey_matter_pinch: gmPinch, grey_matter_scan: gmScan, grey_matter_gadget: gmGadget, grey_matter_leap: gmLeap, grey_matter_repair: gmRepair,
	grey_matter_think: gmThink,
};
for (const [name, fn] of Object.entries(ALL)) save(name, fn());
