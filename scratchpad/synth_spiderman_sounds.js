// v0.16.1: synthesises Spider-Man's sounds -- the web-shooter THWIP (3 takes), the zip-line whoosh, the wet splat of a
// web landing, and the Spider-Sense tingle -- as 44.1 kHz mono WAVs, then encodes them to OGG Vorbis with ffmpeg (same
// pipeline as synth_wolverine_sniff.js). No samples: filtered noise and a few sine partials.
//   node scratchpad/synth_spiderman_sounds.js "<path to ffmpeg.exe>"
// Output: src/main/resources/assets/projecthero/sounds/spiderman/*.ogg   (WAVs go to scratchpad/spiderman_wav/)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const SR = 44100;
const FFMPEG = process.argv[2];
const WAV_DIR = path.join(__dirname, 'spiderman_wav');
const OUT_DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds', 'spiderman');
fs.mkdirSync(WAV_DIR, { recursive: true });
fs.mkdirSync(OUT_DIR, { recursive: true });

let seed = 23;
const rnd = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 4294967296; };
const noise = () => rnd() * 2 - 1;

/** A time-varying biquad: f(t) gives the centre / cutoff frequency at sample i. */
function sweep(x, type, f, q = 0.707) {
	const y = new Float32Array(x.length);
	let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
	for (let i = 0; i < x.length; i++) {
		const fc = Math.max(40, Math.min(SR * 0.45, f(i / x.length)));
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
		execFileSync(FFMPEG, ['-y', '-loglevel', 'error', '-i', w, '-c:a', 'libvorbis', '-q:a', '6', path.join(OUT_DIR, name + '.ogg')]);
	}
	console.log(name, (x.length / SR).toFixed(2) + ' s', FFMPEG ? '-> ogg' : '(wav only)');
}

/**
 * THWIP: a pressurised squirt -- a hard transient, then a band of hiss sweeping DOWN fast (the "thw"), a short
 * pitched chirp riding it (the "ip"), and a tiny wet tail.
 */
function thwip(len, top, bottom, chirp, wet) {
	const out = buf(len + 0.08);
	const hiss = env(sweep(sweep(noiseBuf(len), 'bp', t => top * Math.pow(bottom / top, Math.pow(t, 0.6)), 3.0), 'hp', () => 700),
		t => (t < 0.06 ? t / 0.06 : Math.pow(1 - (t - 0.06) / 0.94, 2.4)));
	mix(out, hiss, 0, 1.0);
	// the "ip": a falling sine chirp
	const c = buf(len * 0.55);
	let ph = 0;
	for (let i = 0; i < c.length; i++) {
		const t = i / c.length;
		const f = chirp * (1.0 - 0.45 * t);
		ph += 2 * Math.PI * f / SR;
		c[i] = Math.sin(ph) * Math.pow(1 - t, 3) * (t < 0.05 ? t / 0.05 : 1);
	}
	mix(out, c, 0.012, 0.35);
	// the click of the shooter
	const click = env(sweep(noiseBuf(0.006), 'hp', () => 3000), t => 1 - t);
	mix(out, click, 0, 0.7);
	// a wet little tail
	const tail = env(sweep(noiseBuf(0.07), 'bp', t => 900 - 300 * t, 2.0), t => Math.sin(Math.PI * t) * 0.6);
	mix(out, tail, len * 0.7, wet);
	return normalise(out, 0.85);
}

save('thwip1', thwip(0.11, 5200, 1300, 2400, 0.4));
save('thwip2', thwip(0.13, 4600, 1100, 2100, 0.5));
save('thwip3', thwip(0.10, 5800, 1500, 2700, 0.35));

// ZIP: a long line being shot and hauled -- a whoosh whose band climbs and falls like a fly-by, under a stretched thwip
{
	const len = 0.42;
	const out = buf(len + 0.05);
	const w = env(sweep(noiseBuf(len), 'bp', t => 700 + 2600 * Math.sin(Math.PI * Math.min(1, t * 1.1)), 1.4),
		t => Math.pow(Math.sin(Math.PI * t), 0.8));
	mix(out, w, 0, 0.9);
	mix(out, thwip(0.12, 5000, 1400, 2300, 0.2), 0, 0.6);
	save('zip', normalise(out, 0.8));
}

// SPLAT: web landing -- a soft low thud plus a short wet crackle
{
	const out = buf(0.26);
	const thud = buf(0.18);
	let ph = 0;
	for (let i = 0; i < thud.length; i++) {
		const t = i / thud.length;
		ph += 2 * Math.PI * (140 - 60 * t) / SR;
		thud[i] = Math.sin(ph) * Math.pow(1 - t, 3);
	}
	mix(out, thud, 0, 0.8);
	const wet = env(sweep(noiseBuf(0.16), 'lp', t => 2600 - 1800 * t), t => (t < 0.03 ? t / 0.03 : Math.pow(1 - t, 2)));
	mix(out, wet, 0.005, 0.9);
	for (let k = 0; k < 6; k++) {
		const pop = env(sweep(noiseBuf(0.012), 'bp', () => 1500 + rnd() * 1500, 4), t => 1 - t);
		mix(out, pop, 0.02 + rnd() * 0.15, 0.35);
	}
	save('splat', normalise(out, 0.8));
}

// SENSE: the Spider-Sense tingle -- shimmering high partials with a fast tremolo over a faint low pulse
{
	const len = 0.75;
	const out = buf(len);
	const partials = [[2637, 0.5], [3520, 0.35], [4186, 0.28], [5274, 0.2], [3136, 0.3]];
	for (let i = 0; i < out.length; i++) {
		const t = i / out.length;
		const sec = i / SR;
		let v = 0;
		for (const [f, a] of partials) v += Math.sin(2 * Math.PI * f * sec + f) * a * (0.6 + 0.4 * Math.sin(2 * Math.PI * (17 + f / 900) * sec));
		const e = t < 0.08 ? t / 0.08 : Math.pow(1 - (t - 0.08) / 0.92, 1.6);
		v *= e * 0.5;
		v += Math.sin(2 * Math.PI * 110 * sec) * 0.25 * e * (0.5 + 0.5 * Math.sin(2 * Math.PI * 9 * sec));
		out[i] = v;
	}
	save('sense', normalise(out, 0.7));
}
