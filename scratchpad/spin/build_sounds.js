// v0.17.3 the Spin: builds the 13 Spin sounds from the user's clip docs/reference/spin/spin_sfx_source.mp4.
//
//   node scratchpad/spin/build_sounds.js <work_dir>
//
// The clip (11.15 s) is one spin sound in two parts:
//   A  0.05-2.05 s  an intense, rising whirr, pulsing at ~23 Hz (44 ms), with hiss at 8-10 kHz        (-30 -> -23 dB RMS)
//   B  2.10-11.1 s  the same whirr settled to a steady, quieter spin (44 ms pulse, a 330 Hz tone, ticks) (-33 -> -28 dB RMS)
// There is no impact, whoosh, whistle or boom in it, so those parts are synthesised and layered with the clip's whirr
// (see the recipe for each sound below; the report lists which sound came from where).
//
// Output: src/main/resources/assets/projecthero/sounds/spin/<id>.ogg, mono 44.1 kHz Vorbis. Loudness: one-shots are gained to
// -21.7 LUFS (Four Arms' sequence, the reference quiet sound in scratchpad/omnitrix_seq/cut_clip.js) and limited to -4 dBFS;
// the two loops play continuously so they sit lower (-25 LUFS) and get only a constant gain (a limiter would break the seam).
const fs = require('fs');
const path = require('path');
const { execFileSync, spawnSync } = require('child_process');

const FF = process.env.FFMPEG || 'C:/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe';
const ROOT = path.resolve(__dirname, '..', '..');
const CLIP = path.join(ROOT, 'docs/reference/spin/spin_sfx_source.mp4');
const OUT = path.join(ROOT, 'src/main/resources/assets/projecthero/sounds/spin');
const WORK = path.resolve(process.argv[2] || path.join(__dirname, 'work'));
const SR = 44100;
const LIMIT = 0.63; // -4 dBFS
fs.mkdirSync(WORK, { recursive: true });
fs.mkdirSync(OUT, { recursive: true });

// ------------------------------------------------------------------ wav io
function readWav(file) {
	const b = fs.readFileSync(file);
	let p = 12, data;
	while (p < b.length) {
		const id = b.toString('ascii', p, p + 4), sz = b.readUInt32LE(p + 4);
		if (id === 'data') { data = b.subarray(p + 8, p + 8 + sz); break; }
		p += 8 + sz;
	}
	const n = data.length / 2, x = new Float32Array(n);
	for (let i = 0; i < n; i++) x[i] = data.readInt16LE(i * 2) / 32768;
	return x;
}
function writeWav(file, x) {
	const b = Buffer.alloc(44 + x.length * 2);
	b.write('RIFF', 0); b.writeUInt32LE(36 + x.length * 2, 4); b.write('WAVE', 8); b.write('fmt ', 12);
	b.writeUInt32LE(16, 16); b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22); b.writeUInt32LE(SR, 24); b.writeUInt32LE(SR * 2, 28);
	b.writeUInt16LE(2, 32); b.writeUInt16LE(16, 34); b.write('data', 36); b.writeUInt32LE(x.length * 2, 40);
	for (let i = 0; i < x.length; i++) b.writeInt16LE(Math.max(-32767, Math.min(32767, Math.round(x[i] * 32767))), 44 + i * 2);
	fs.writeFileSync(file, b);
}

// ------------------------------------------------------------------ dsp
const S = (t) => Math.round(t * SR);
const buf = (sec) => new Float32Array(S(sec));
let seed = 12345;
const rnd = () => { seed = (seed * 1103515245 + 12345) >>> 0; return seed / 4294967296 * 2 - 1; };
function slice(x, a, b) { return x.slice(S(a), S(b)); }
function reverse(x) { return Float32Array.from(x).reverse(); }
/** Plays x back at a varying rate (rate(t01) -> playback speed; >1 = higher and shorter). */
function resample(x, rate, maxSec = 30) {
	const out = [];
	let pos = 0;
	const total = x.length;
	while (pos < total - 2 && out.length < S(maxSec)) {
		const i = Math.floor(pos), f = pos - i;
		const a = x[Math.max(0, i - 1)], b0 = x[i], c = x[i + 1], d = x[Math.min(total - 1, i + 2)];
		// cubic hermite
		const c0 = b0, c1 = 0.5 * (c - a), c2 = a - 2.5 * b0 + 2 * c - 0.5 * d, c3 = 0.5 * (d - a) + 1.5 * (b0 - c);
		out.push(((c3 * f + c2) * f + c1) * f + c0);
		pos += typeof rate === 'function' ? rate(pos / total) : rate;
	}
	return Float32Array.from(out);
}
/** RBJ biquad: type lp | hp | bp; freq may be a function of t01. */
function biquad(x, type, freq, q = 0.707) {
	const y = new Float32Array(x.length);
	let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
	let b0, b1, b2, a1, a2, lastF = -1;
	for (let i = 0; i < x.length; i++) {
		const f = typeof freq === 'function' ? freq(i / x.length) : freq;
		if (f !== lastF) {
			lastF = f;
			const w = 2 * Math.PI * Math.min(f, SR * 0.45) / SR, cs = Math.cos(w), al = Math.sin(w) / (2 * q);
			let B0, B1, B2;
			if (type === 'lp') { B0 = (1 - cs) / 2; B1 = 1 - cs; B2 = (1 - cs) / 2; }
			else if (type === 'hp') { B0 = (1 + cs) / 2; B1 = -(1 + cs); B2 = (1 + cs) / 2; }
			else { B0 = al; B1 = 0; B2 = -al; }
			const A0 = 1 + al;
			b0 = B0 / A0; b1 = B1 / A0; b2 = B2 / A0; a1 = -2 * cs / A0; a2 = (1 - al) / A0;
		}
		const v = b0 * x[i] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
		x2 = x1; x1 = x[i]; y2 = y1; y1 = v; y[i] = v;
	}
	return y;
}
/** Multiplies by a piecewise-linear envelope [[sec, gain], ...]. */
function env(x, pts) {
	const y = new Float32Array(x.length);
	for (let i = 0; i < x.length; i++) {
		const t = i / SR;
		let g = pts[pts.length - 1][1];
		for (let k = 0; k < pts.length - 1; k++) {
			if (t >= pts[k][0] && t <= pts[k + 1][0]) {
				const u = (t - pts[k][0]) / Math.max(1e-9, pts[k + 1][0] - pts[k][0]);
				g = pts[k][1] + (pts[k + 1][1] - pts[k][1]) * u;
				break;
			}
			if (t < pts[0][0]) { g = pts[0][1]; break; }
		}
		y[i] = x[i] * g;
	}
	return y;
}
const expDecay = (x, tau, delay = 0) => x.map((v, i) => { const t = i / SR - delay; return t < 0 ? 0 : v * Math.exp(-t / tau); });
function gain(x, g) { return x.map((v) => v * g); }
/** Adds src into dst at sec offset. */
function mix(dst, src, at = 0, g = 1) {
	const o = S(at);
	for (let i = 0; i < src.length && o + i < dst.length; i++) if (o + i >= 0) dst[o + i] += src[i] * g;
	return dst;
}
function noise(sec) { const x = buf(sec); for (let i = 0; i < x.length; i++) x[i] = rnd(); return x; }
function brown(sec) { const x = buf(sec); let v = 0; for (let i = 0; i < x.length; i++) { v = (v + 0.02 * rnd()) * 0.998; x[i] = v * 8; } return x; }
/** Sine with frequency f(t sec) (or a number), amplitude a(t sec) (or a number). */
function osc(sec, f, a = 1, phase = 0) {
	const x = buf(sec);
	let ph = phase;
	for (let i = 0; i < x.length; i++) {
		const t = i / SR;
		x[i] = Math.sin(ph) * (typeof a === 'function' ? a(t) : a);
		ph += 2 * Math.PI * (typeof f === 'function' ? f(t) : f) / SR;
	}
	return x;
}
/** Evens out a crescendo: divides by a smoothed RMS envelope (window ~ 120 ms), so a loop cut from it holds steady. */
function flatten(x) {
	const w = S(0.12), y = new Float32Array(x.length);
	const pre = new Float64Array(x.length + 1);
	for (let i = 0; i < x.length; i++) pre[i + 1] = pre[i] + x[i] * x[i];
	for (let i = 0; i < x.length; i++) {
		const a = Math.max(0, i - w), b = Math.min(x.length, i + w);
		const r = Math.sqrt((pre[b] - pre[a]) / (b - a)) || 1e-4;
		y[i] = x[i] * 0.05 / r;
	}
	return y;
}
/** Seamless loop of length L sec: the X-sec tail past L is crossfaded (equal power) into the head. Needs x >= L + X. */
function loopify(x, L, X) {
	const n = S(L), m = S(X), y = x.slice(0, n);
	if (x.length < n + m) throw new Error('loop source too short: ' + x.length / SR + ' < ' + (L + X));
	for (let i = 0; i < m; i++) {
		const u = i / m;
		y[i] = x[i] * Math.sin(u * Math.PI / 2) + x[n + i] * Math.cos(u * Math.PI / 2);
	}
	return y;
}
function fades(x, fin = 0.005, fout = 0.03) {
	const y = Float32Array.from(x), a = S(fin), b = S(fout);
	for (let i = 0; i < a && i < y.length; i++) y[i] *= i / a;
	for (let i = 0; i < b && i < y.length; i++) y[y.length - 1 - i] *= i / b;
	return y;
}
/** A struck steel partial set: [freq, amp, decay sec]. */
function ring(sec, partials, at = 0) {
	const x = buf(sec);
	for (const [f, a, d] of partials) mix(x, expDecay(osc(sec - at, f, a), d), at);
	return x;
}
function thump(sec, f0, f1, tau, a = 1) {
	return expDecay(osc(sec, (t) => f1 + (f0 - f1) * Math.exp(-t / (tau * 0.6)), a), tau);
}
function whoosh(sec, f0, fPeak, f1, peakAt, q = 1.2) {
	const fn = (u) => { const t = u * sec; return t < peakAt ? f0 + (fPeak - f0) * (t / peakAt) : fPeak + (f1 - fPeak) * ((t - peakAt) / (sec - peakAt)); };
	return env(biquad(noise(sec), 'bp', fn, q), [[0, 0], [peakAt, 1], [sec, 0]]);
}

// ------------------------------------------------------------------ the clip
const wav = path.join(WORK, 'clip.wav');
execFileSync(FF, ['-hide_banner', '-loglevel', 'error', '-y', '-i', CLIP, '-vn', '-ac', '1', '-ar', String(SR), wav]);
const clip = biquad(readWav(wav), 'hp', 35); // drop sub rumble below the whirr
const A = slice(clip, 0.05, 2.05);           // intense rising whirr
const B = slice(clip, 2.1, 11.1);            // steady whirr
const at = (x, a, b) => slice(x, a, b);      // seconds within A or B

// ------------------------------------------------------------------ the sounds
const SOUNDS = {};

// Ball throw 0.55 s: clip 1.45-2.05 s (the peak of the whirr) sped up 1.0 -> 1.5 as it leaves the hand, + a noise whoosh.
SOUNDS.spin_ball_throw = (() => {
	const out = buf(0.55);
	const w = resample(at(A, 1.4, 2.0), (u) => 1.0 + 0.5 * u);
	mix(out, env(w, [[0, 0], [0.02, 1], [0.15, 0.9], [0.5, 0]]), 0, 1.0);
	mix(out, whoosh(0.5, 500, 2400, 900, 0.12), 0, 0.12);
	return out;
})();

// Spin loop 2.0 s: clip 3.1-5.4 s (part B), crossfade-looped (0.25 s seam). The plain spinning whirr.
SOUNDS.spin_ball_spin_loop = { loop: true, x: loopify(at(B, 1.0, 3.3), 2.0, 0.25) };

// Ball hit 0.6 s: synthesised steel strike (thunk + click + inharmonic ring) + clip 1.9-2.2 s (the whirr's sudden drop at 2.05 s).
SOUNDS.spin_ball_hit = (() => {
	const out = buf(0.6);
	mix(out, thump(0.4, 140, 60, 0.09), 0, 0.9);
	mix(out, expDecay(biquad(noise(0.05), 'hp', 2500), 0.006), 0, 0.5);
	mix(out, ring(0.6, [[1180, 0.11, 0.14], [2710, 0.06, 0.09], [4130, 0.03, 0.06]]), 0, 1);
	mix(out, env(slice(clip, 1.9, 2.2), [[0, 0.2], [0.13, 1], [0.3, 0]]), 0, 0.9);
	return out;
})();

// Ball catch 0.4 s: synthesised palm slap + clip 6.1-6.5 s slowed 0.9 -> 0.45 (the spin dying in the hand).
SOUNDS.spin_ball_catch = (() => {
	const out = buf(0.4);
	mix(out, expDecay(biquad(noise(0.12), 'bp', 1400, 0.7), 0.025), 0, 0.5);
	mix(out, thump(0.2, 180, 90, 0.05), 0, 0.6);
	mix(out, env(resample(at(B, 4.0, 4.4), (u) => 0.9 - 0.45 * u), [[0, 1], [0.35, 0]]), 0.01, 0.9);
	return out;
})();

// Body spin 1.4 s: clip 5.1-6.4 s at 0.75 speed with a slow +-8 % twist wobble, low-passed.
SOUNDS.spin_body_spin = (() => {
	const w = resample(at(B, 3.0, 4.3), (u) => 0.75 * (1 + 0.08 * Math.sin(u * 2 * Math.PI * 4)));
	return env(biquad(w, 'lp', 3200), [[0, 0], [0.1, 1], [1.0, 0.8], [1.4, 0]]).slice(0, S(1.4));
})();

// Golden charge loop 1.0 s: clip 0.3-2.05 s levelled, layered with itself +4 semitones and +7 (a fifth), + a quiet tremolo chord.
SOUNDS.spin_golden_charge_loop = (() => {
	const src = flatten(at(A, 0.25, 2.0));
	const L = 1.0, X = 0.2;
	const base = src.slice(0, S(L + X));
	const up4 = resample(src, 1.26).slice(0, S(L + X));
	const up7 = resample(src, 1.4983).slice(0, S(L + X));
	const out = buf(L + X);
	mix(out, base, 0, 1.0);
	mix(out, up4, 0, 0.55);
	mix(out, up7, 0, 0.35);
	// integer-Hz partials and a 6 Hz tremolo -> whole cycles in 1.0 s
	const trem = (t) => 0.006 * (0.6 + 0.4 * Math.sin(2 * Math.PI * 6 * t));
	for (const f of [660, 990, 1320]) mix(out, osc(L + X, f, trem), 0, 1);
	return { loop: true, x: loopify(out, L, X) };
})();

// Golden release 1.6 s: synthesised burst (thump, bright G-major "shing", whoosh) + clip 1.35-2.1 s at 1.6x and clip 2.6-4.6 s at 1.5x
// as the whirr tail.
SOUNDS.spin_golden_release = (() => {
	const out = buf(1.6);
	mix(out, thump(0.5, 120, 45, 0.16), 0, 0.8);
	mix(out, ring(1.6, [[1568, 0.10, 0.7], [2349, 0.07, 0.55], [3136, 0.05, 0.45], [4699, 0.025, 0.3]]), 0, 1);
	mix(out, whoosh(0.6, 2500, 1500, 400, 0.05, 0.8), 0, 0.15);
	mix(out, env(resample(at(A, 1.3, 2.05), 1.6), [[0, 1], [0.45, 0]]), 0, 0.8);
	mix(out, env(resample(at(B, 0.5, 2.5), 1.5), [[0, 0], [0.1, 0.7], [1.3, 0]]), 0.05, 0.8);
	return out;
})();

// Ball Breaker 2.0 s: clip 1.3-2.1 s reversed at 0.8x (the figure rushing in), a heavy synthesised impact at 0.62 s, then
// clip 4.1-5.1 s at 0.6x as a low rumbling tail.
SOUNDS.spin_ball_breaker = (() => {
	const out = buf(2.0);
	const rush = resample(reverse(at(A, 1.25, 2.05)), 0.8);
	mix(out, env(rush, [[0, 0], [0.3, 0.6], [rush.length / SR - 0.02, 1], [rush.length / SR, 0]]), 0.62 - rush.length / SR, 0.9);
	mix(out, thump(0.7, 90, 32, 0.25), 0.62, 1.0);
	mix(out, expDecay(biquad(noise(0.15), 'hp', 1200), 0.02), 0.62, 0.45);
	mix(out, ring(1.2, [[520, 0.11, 0.3], [1210, 0.06, 0.22], [1890, 0.04, 0.15]]), 0.62, 1);
	mix(out, env(biquad(resample(at(B, 2.0, 3.0), 0.6), 'lp', 1500), [[0, 0.8], [1.3, 0]]), 0.65, 0.9);
	return out;
})();

// Scan 1.6 s: clip 7.1-9.0 s at 1.12x, band-passed (a hum on the ground), + a soft sonar ping.
SOUNDS.spin_scan = (() => {
	const out = buf(1.6);
	const w = biquad(biquad(resample(at(B, 5.0, 6.9), 1.12), 'hp', 180), 'lp', 3000);
	mix(out, env(w, [[0, 0], [0.15, 1], [1.1, 1], [1.6, 0]]), 0, 1);
	mix(out, expDecay(osc(1.0, 1760, 0.05), 0.25), 0.02, 1);
	mix(out, expDecay(osc(1.0, 1760 * 2.01, 0.015), 0.15), 0.02, 1);
	return out;
})();

// Quake 1.5 s: synthesised boom (55 -> 28 Hz), crack and brown-noise rumble + clip 1.5-2.1 s an octave down (the ball drilling in).
SOUNDS.spin_quake = (() => {
	const out = buf(1.5);
	mix(out, thump(1.2, 75, 28, 0.35), 0, 1.0);
	mix(out, expDecay(biquad(noise(0.08), 'hp', 900), 0.015), 0, 0.35);
	mix(out, expDecay(biquad(brown(1.5), 'lp', 220), 0.45), 0, 0.5);
	mix(out, env(resample(at(A, 1.45, 2.05), 0.5), [[0, 1], [1.1, 0]]), 0, 1.0);
	return out;
})();

// Healing Spin 1.8 s: clip 3.6-6.4 s at 1.5x, softened, + a gentle C-major chord with a 5 Hz shimmer.
SOUNDS.spin_heal = (() => {
	const out = buf(1.8);
	const w = biquad(resample(at(B, 1.5, 4.3), 1.5), 'lp', 2500);
	mix(out, env(w, [[0, 0], [0.2, 0.7], [1.2, 0.6], [1.8, 0]]), 0, 1);
	const a = (t) => 0.035 * Math.min(1, t / 0.3) * Math.exp(-Math.max(0, t - 0.3) / 0.7) * (0.75 + 0.25 * Math.sin(2 * Math.PI * 5 * t));
	for (const f of [523.25, 659.25, 783.99, 1046.5]) mix(out, osc(1.8, f, a), 0, f > 1000 ? 0.5 : 1);
	return out;
})();

// Spin Leap 0.7 s: clip 8.1-8.8 s spun up 0.9 -> 1.8 (the leg muscles winding), a push-off thump, then a noise whoosh.
SOUNDS.spin_leap = (() => {
	const out = buf(0.7);
	mix(out, env(resample(at(B, 6.0, 6.7), (u) => 0.9 + 0.9 * u), [[0, 0.3], [0.2, 1], [0.45, 0]]), 0, 0.9);
	mix(out, thump(0.25, 110, 55, 0.06), 0.18, 0.6);
	mix(out, whoosh(0.5, 400, 1800, 600, 0.12), 0.18, 0.13);
	return out;
})();

// Whistle 1.0 s: synthesised two-note whistle for Valkyrie (rising "fwee", then a held "fweeo"), vibrato + breath noise.
SOUNDS.spin_whistle = (() => {
	const out = buf(1.0);
	const note = (sec, f, a) => {
		const tone = osc(sec, (t) => f(t) * (1 + 0.012 * Math.sin(2 * Math.PI * 6 * t)), a);
		const h2 = osc(sec, (t) => 2 * f(t) * (1 + 0.012 * Math.sin(2 * Math.PI * 6 * t)), (t) => a(t) * 0.08);
		const breath = biquad(noise(sec), 'bp', (u) => f(u * sec), 6).map((v, i) => v * a(i / SR) * 0.35);
		const x = buf(sec); mix(x, tone); mix(x, h2); mix(x, breath); return x;
	};
	mix(out, note(0.34, (t) => 1500 + 1100 * Math.min(1, t / 0.22), (t) => 0.25 * Math.min(1, t / 0.04) * Math.min(1, (0.34 - t) / 0.05)), 0, 1);
	mix(out, note(0.52, (t) => (t < 0.3 ? 2650 : 2650 - 500 * (t - 0.3) / 0.22), (t) => 0.25 * Math.min(1, t / 0.03) * Math.min(1, (0.52 - t) / 0.12)), 0.43, 1);
	return out;
})();

// ------------------------------------------------------------------ loudness + encode
function measureLufs(file, isLoop) {
	// measure 3 back-to-back copies so the 400 ms gating blocks see short sounds properly
	const r = spawnSync(FF, ['-hide_banner', '-stream_loop', '2', '-i', file, '-af', 'ebur128', '-f', 'null', '-'], { encoding: 'utf8', maxBuffer: 1 << 26 });
	const all = (r.stderr || '').match(/I:\s+(-?[0-9.]+) LUFS/g) || [];
	return all.length ? parseFloat(all[all.length - 1].replace(/[^0-9.-]/g, '')) : -30;
}
const report = [];
for (const [id, v] of Object.entries(SOUNDS)) {
	const isLoop = !!(v && v.loop);
	let x = isLoop ? v.x : fades(v);
	const wavFile = path.join(WORK, id + '.wav');
	writeWav(wavFile, x);
	const lufs = measureLufs(wavFile, isLoop);
	const target = isLoop ? -25 : id === 'spin_whistle' ? -24 : -21.7;
	let g = Math.pow(10, Math.max(-30, Math.min(24, target - lufs)) / 20);
	let peak = 0;
	for (const s of x) peak = Math.max(peak, Math.abs(s));
	if (isLoop && peak * g > LIMIT) g = LIMIT / peak; // loops: constant gain only, never clipped
	const ogg = path.join(OUT, id + '.ogg');
	const af = isLoop ? `volume=${g.toFixed(5)}` : `volume=${g.toFixed(5)},alimiter=limit=${LIMIT}:level=false:attack=2:release=40`;
	execFileSync(FF, ['-hide_banner', '-loglevel', 'error', '-y', '-i', wavFile, '-ac', '1', '-ar', String(SR), '-af', af, '-c:a', 'libvorbis', '-q:a', '3', ogg]);
	const chk = spawnSync(FF, ['-hide_banner', '-stream_loop', '2', '-i', ogg, '-af', 'ebur128=peak=true', '-f', 'null', '-'], { encoding: 'utf8', maxBuffer: 1 << 26 });
	const I = ((chk.stderr || '').match(/I:\s+(-?[0-9.]+) LUFS/g) || []).pop();
	const P = ((chk.stderr || '').match(/Peak:\s+(-?[0-9.]+) dBFS/g) || []).pop();
	report.push(`${id.padEnd(26)} ${(x.length / SR).toFixed(2)} s  ${isLoop ? 'loop ' : 'shot '} ${I}  true ${P}`);
}
console.log(report.join('\n'));
