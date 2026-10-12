// v0.16.1: synthesises Wolverine's Sniff (N) -- quick nasal inhales of filtered noise, no samples -- as 44.1 kHz mono
// WAVs, then encodes them to OGG Vorbis with ffmpeg (same pipeline as synth_gun_sounds.js).
//   node scratchpad/synth_wolverine_sniff.js "<path to ffmpeg.exe>"
// Output: src/main/resources/assets/projecthero/sounds/wolverine/sniff1..3.ogg   (WAVs go to scratchpad/wolverine_wav/)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const SR = 44100;
const FFMPEG = process.argv[2];
const WAV_DIR = path.join(__dirname, 'wolverine_wav');
const OUT_DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds', 'wolverine');
fs.mkdirSync(WAV_DIR, { recursive: true });
fs.mkdirSync(OUT_DIR, { recursive: true });

let seed = 11;
function rnd() { // deterministic, so re-running gives the same files
	seed = (seed * 1664525 + 1013904223) >>> 0;
	return seed / 4294967296;
}
const noise = () => rnd() * 2 - 1;

function biquad(x, type, f, q = 0.707) {
	const w = 2 * Math.PI * f / SR, c = Math.cos(w), s = Math.sin(w), a = s / (2 * q);
	let b0, b1, b2;
	if (type === 'lp') { b0 = (1 - c) / 2; b1 = 1 - c; b2 = (1 - c) / 2; }
	else if (type === 'hp') { b0 = (1 + c) / 2; b1 = -(1 + c); b2 = (1 + c) / 2; }
	else { b0 = a; b1 = 0; b2 = -a; } // band-pass (constant peak)
	const a0 = 1 + a, a1 = -2 * c, a2 = 1 - a;
	const y = new Float32Array(x.length);
	let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
	for (let i = 0; i < x.length; i++) {
		const v = (b0 * x[i] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0;
		x2 = x1; x1 = x[i]; y2 = y1; y1 = v; y[i] = v;
	}
	return y;
}

/**
 * One inhale: air through the nose -- broadband hiss shaped by a nasal band (rising a little in pitch as the breath
 * speeds up), a fast swell and a quick choke-off. len seconds, peak at `peak` (0..1 of len).
 */
function inhale(len, peak, centre, gain) {
	const n = Math.ceil(len * SR);
	const x = new Float32Array(n);
	for (let i = 0; i < n; i++) x[i] = noise();
	// two formant bands + a little high air, blended
	const nasal = biquad(biquad(x, 'bp', centre, 2.2), 'bp', centre * 1.04, 2.2);
	const hiss = biquad(biquad(x, 'hp', 2400), 'lp', 7000);
	const low = biquad(x, 'bp', centre * 0.42, 3.0);
	const y = new Float32Array(n);
	for (let i = 0; i < n; i++) {
		const t = i / n;
		// swell to the peak, then a quicker cut (the nostrils snap shut)
		const env = t < peak ? Math.pow(t / peak, 1.6) : Math.pow(1 - (t - peak) / (1 - peak), 2.2);
		y[i] = (nasal[i] * 1.3 + hiss[i] * 0.55 + low[i] * 0.5) * env * gain;
	}
	return y;
}

function mix(dst, src, at) {
	const o = Math.round(at * SR);
	for (let i = 0; i < src.length && i + o < dst.length; i++) dst[i + o] += src[i];
}

function normalise(x, peakTarget) {
	let peak = 0;
	for (const v of x) peak = Math.max(peak, Math.abs(v));
	const k = peak > 0 ? peakTarget / peak : 1;
	for (let i = 0; i < x.length; i++) x[i] *= k;
	// 4 ms fade at both ends: no clicks
	const f = Math.round(0.004 * SR);
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

// three takes: two / three short sniffs then a longer draw, slightly different rhythm and pitch each
const takes = [
	{ name: 'sniff1', parts: [[0.0, 0.11, 0.55, 1150, 0.8], [0.16, 0.11, 0.55, 1250, 0.9], [0.33, 0.26, 0.45, 1350, 1.0]] },
	{ name: 'sniff2', parts: [[0.0, 0.09, 0.5, 1050, 0.75], [0.13, 0.09, 0.5, 1150, 0.85], [0.26, 0.10, 0.5, 1250, 0.9], [0.42, 0.22, 0.45, 1300, 1.0]] },
	{ name: 'sniff3', parts: [[0.0, 0.13, 0.55, 1200, 0.85], [0.2, 0.30, 0.4, 1400, 1.0]] },
];

for (const take of takes) {
	let end = 0;
	for (const [at, len] of take.parts) end = Math.max(end, at + len);
	const out = new Float32Array(Math.ceil((end + 0.05) * SR));
	for (const [at, len, peak, centre, gain] of take.parts) mix(out, inhale(len, peak, centre, gain), at);
	normalise(out, 0.8);
	const w = path.join(WAV_DIR, take.name + '.wav');
	wav(w, out);
	if (FFMPEG) {
		execFileSync(FFMPEG, ['-y', '-loglevel', 'error', '-i', w, '-c:a', 'libvorbis', '-q:a', '6', path.join(OUT_DIR, take.name + '.ogg')]);
	}
	console.log(take.name, (out.length / SR).toFixed(2) + ' s', FFMPEG ? '-> ogg' : '(wav only: pass ffmpeg.exe to encode)');
}
