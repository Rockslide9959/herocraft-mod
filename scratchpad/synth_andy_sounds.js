// v0.17.3 Andy sounds: synthesises Andy's (Undead Unluck) own blood and regrowth sounds -- no samples, only filtered noise,
// stick-slip pulse trains through body resonances, Minnaert bubbles (rising-pitch decaying sines: the core of anything
// "wet") and short click clusters (bone). 44.1 kHz mono WAVs, encoded to OGG Vorbis with an ffmpeg that has libvorbis
// (Minecraft needs mono for positional sound). Loops are crossfaded end-into-start so they repeat without a seam.
// The REGROWTH sounds use no bubbles at all since v2 (bubbles + noise beds + crackle read as boiling water / lava): see the
// REGROWTH section. Look at them with scratchpad/andy_sound_pics.js (spectrogram + envelope sheets).
//   node scratchpad/synth_andy_sounds.js "C:/Users/<you>/AppData/Local/Medal/recorder-*/ffmpeg7.exe"
// Output: src/main/resources/assets/projecthero/sounds/andy/*.ogg   (WAVs go to the OS temp folder)
// The events and their vanilla extras live in sounds.json (projecthero:andy.*); scratchpad/andy_sounds_json.js writes those.
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');

const SR = 44100;
const FFMPEG = process.argv[2];
const WAV_DIR = path.join(os.tmpdir(), 'andy_sounds_wav');
const OUT_DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds', 'andy');
fs.mkdirSync(WAV_DIR, { recursive: true });
fs.mkdirSync(OUT_DIR, { recursive: true });

let seed = 917;
const rnd = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 4294967296; };
const noise = () => rnd() * 2 - 1;
const rr = (a, b) => a + (b - a) * rnd();

// ---------------------------------------------------------------- DSP helpers

function buf(sec) { return new Float32Array(Math.max(1, Math.ceil(sec * SR))); }
function noiseBuf(sec) { const b = buf(sec); for (let i = 0; i < b.length; i++) b[i] = noise(); return b; }
/** Brown-ish noise: integrated white, leaky (rumble, flesh mass). */
function brownBuf(sec) { const b = buf(sec); let v = 0; for (let i = 0; i < b.length; i++) { v = v * 0.996 + noise() * 0.06; b[i] = v * 0.25; } return filt(b, 'hp', 35); }
function mix(dst, src, at, gain = 1) { const o = Math.round(at * SR); for (let i = 0; i < src.length && i + o < dst.length; i++) if (i + o >= 0) dst[i + o] += src[i] * gain; }
function env(x, fn) { for (let i = 0; i < x.length; i++) x[i] *= fn(i / x.length, i / SR); return x; }

/** Time-varying biquad. f(t, sec) gives the cutoff / centre (t is 0..1 through the buffer). type lp / hp / bp. */
function filt(x, type, f, q = 0.707) {
	const y = new Float32Array(x.length);
	let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
	for (let i = 0; i < x.length; i++) {
		const fc = Math.max(20, Math.min(SR * 0.45, typeof f === 'number' ? f : f(i / x.length, i / SR)));
		const qq = typeof q === 'number' ? q : q(i / x.length);
		const w = 2 * Math.PI * fc / SR, c = Math.cos(w), s = Math.sin(w), a = s / (2 * qq);
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
/** Sum of resonant band-passes (a body's formants). */
function resonate(x, freqs, q, gains) {
	const y = new Float32Array(x.length);
	freqs.forEach((f, k) => { const b = filt(x, 'bp', f, q); for (let i = 0; i < y.length; i++) y[i] += b[i] * (gains ? gains[k] : 1); });
	return y;
}
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
function peak(x) { let p = 0; for (const v of x) p = Math.max(p, Math.abs(v)); return p; }
function normalise(x, target, edge = true) {
	const p = peak(x);
	const k = p > 0 ? target / p : 1;
	for (let i = 0; i < x.length; i++) x[i] *= k;
	if (edge) {
		const f = Math.round(0.003 * SR);
		for (let i = 0; i < f && i < x.length; i++) { x[i] *= i / f; x[x.length - 1 - i] *= i / f; }
	}
	return x;
}
/** RMS-normalise a loop (loops sit under one-shots; their level is set in code). */
function normRms(x, rms, ceiling = 0.95) {
	let s = 0;
	for (const v of x) s += v * v;
	const r = Math.sqrt(s / x.length);
	const k = r > 0 ? rms / r : 1;
	for (let i = 0; i < x.length; i++) x[i] *= k;
	const p = peak(x);
	if (p > ceiling) for (let i = 0; i < x.length; i++) x[i] *= ceiling / p;
	return x;
}
/** A seamless loop: generate len + fade, then crossfade the tail over the head (equal power). */
function makeLoop(gen, len, fade = 0.35) {
	const raw = gen(len + fade);
	const out = new Float32Array(Math.round(len * SR));
	const F = Math.round(fade * SR);
	for (let i = 0; i < out.length; i++) out[i] = raw[i];
	for (let i = 0; i < F; i++) {
		const t = i / F;
		out[i] = raw[i] * Math.sin(t * Math.PI / 2) + raw[out.length + i] * Math.cos(t * Math.PI / 2);
	}
	return out;
}

// ---------------------------------------------------------------- organic building blocks

/** One Minnaert bubble: a sine whose pitch rises as it surfaces, decaying fast. The basic unit of "wet". */
function bubble(f0, dur, rise = 0.6, h2 = 0.12) {
	const b = buf(dur);
	let ph = 0;
	const tau = dur / 4;
	for (let i = 0; i < b.length; i++) {
		const s = i / SR;
		ph += 2 * Math.PI * f0 * (1 + rise * s / dur) / SR;
		const a = Math.min(1, s / 0.0008) * Math.exp(-s / tau);
		b[i] = (Math.sin(ph) + h2 * Math.sin(2 * ph)) * a;
	}
	return b;
}
/** A stream of bubbles over [start, start+len): rate(t) per second, pitch range, amplitude range. */
function bubbles(dst, start, len, rate, fmin, fmax, amin, amax, durMin = 0.008, durMax = 0.035, rise = [0.3, 1.2]) {
	let t = 0;
	while (t < len) {
		const r = typeof rate === 'number' ? rate : rate(t / len);
		t += -Math.log(1 - rnd() * 0.999) / Math.max(0.5, r);
		if (t >= len) break;
		const f = Math.exp(rr(Math.log(fmin), Math.log(fmax)));
		const d = rr(durMin, durMax) * Math.sqrt(600 / f);
		mix(dst, bubble(f, Math.min(0.09, d), rr(rise[0], rise[1])), start + t, rr(amin, amax));
	}
}
/** A click: an impulse plus a few ms of bright decaying noise (a bone fibre giving, a crackle). */
function click(dst, at, amp, bright = 2500, ms = 3) {
	const n = filt(noiseBuf(ms / 1000), 'hp', bright);
	env(n, t => Math.pow(1 - t, 3));
	n[0] += 1.2;
	mix(dst, n, at, amp);
}
/** Crackle: Poisson clicks, optionally clustered (bursts of 2-5 close clicks), as fat frying / bone fibres. */
function crackle(dst, start, len, rate, amin, amax, bright = [1800, 5000], cluster = 0.3) {
	let t = 0;
	while (t < len) {
		const r = typeof rate === 'number' ? rate : rate(t / len);
		t += -Math.log(1 - rnd() * 0.999) / Math.max(0.5, r);
		if (t >= len) break;
		const n = rnd() < cluster ? 2 + Math.floor(rnd() * 4) : 1;
		for (let k = 0; k < n; k++) click(dst, start + t + k * rr(0.002, 0.009), rr(amin, amax) * (1 - k * 0.15), rr(bright[0], bright[1]), rr(1.5, 5));
	}
}
/**
 * A creak: stick-slip friction -- a pulse train whose rate wanders (rate(t) Hz) with jitter, each pulse a tiny noise grain,
 * all rung through resonances (bone: high and hard; sinew: low and soft). amp(t) shapes it.
 */
function creak(len, rate, freqs, q, amp, jitter = 0.35, grainMs = 1.2) {
	const exc = buf(len);
	let t = 0;
	while (t < len) {
		const r = Math.max(2, rate(t / len));
		t += (1 / r) * (1 + jitter * (rnd() * 2 - 1));
		if (t >= len) break;
		const g = noiseBuf(grainMs / 1000);
		env(g, u => 1 - u);
		g[0] += 1;
		mix(exc, g, t, amp(t / len) * rr(0.5, 1));
	}
	return resonate(exc, freqs, q);
}
/** A squelch: wet noise rung through a resonance that sweeps f0 -> f1 fast (the formant of flesh being squeezed), with bubbles. */
function squelch(dur, f0, f1, q = 5, bub = 1) {
	const out = buf(dur + 0.05);
	const n = filt(noiseBuf(dur), 'bp', (t) => f0 * Math.pow(f1 / f0, Math.pow(t, 0.6)), q);
	env(n, t => Math.min(1, t * 12) * Math.pow(1 - t, 1.6));
	mix(out, n, 0, 2.2);
	const body = filt(noiseBuf(dur), 'lp', (t) => 500 + 900 * (1 - t));
	env(body, t => Math.min(1, t * 20) * Math.pow(1 - t, 3));
	mix(out, body, 0, 0.5);
	if (bub > 0) bubbles(out, 0.005, dur * 0.8, 70 * bub, Math.min(f0, f1) * 0.8, Math.max(f0, f1) * 1.4, 0.15, 0.4);
	return out;
}
/** A low wet thud: a falling sine with a soft noise slap. */
function thud(dur, f0 = 110, f1 = 50, slap = 0.6) {
	const out = buf(dur);
	mix(out, tone(dur, t => f0 + (f1 - f0) * Math.sqrt(t), t => Math.min(1, t * 80) * Math.pow(1 - t, 3), [1, 0.25, 0.08]), 0, 1);
	mix(out, env(filt(noiseBuf(Math.min(dur, 0.06)), 'lp', 1400), t => Math.pow(1 - t, 4)), 0, slap);
	return out;
}
/** A tear: a dense, irregular high-mid crackle riding a fluttering band of noise (fibres parting in sequence). */
function tear(dur, centre = 1700, density = 260) {
	const out = buf(dur);
	const band = filt(noiseBuf(dur), 'bp', t => centre * (0.8 + 0.5 * t), 1.6);
	let flutter = 0;
	env(band, (t) => { flutter = flutter * 0.9 + rnd() * 0.1; return Math.min(1, t * 10) * Math.pow(1 - t, 1.2) * (0.3 + 1.4 * flutter * (rnd() < 0.5 ? 1 : 0.4)); });
	mix(out, band, 0, 1.1);
	crackle(out, 0, dur * 0.9, t => density * (1 - 0.7 * t), 0.2, 0.6, [1200, 4200], 0.5);
	return out;
}

// ---------------------------------------------------------------- output

const made = [];
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
	made.push(name);
	console.log(name.padEnd(22), (x.length / SR).toFixed(2) + ' s  peak ' + peak(x).toFixed(2));
}

// ================================================================ REGROWTH
// v2 (tester: "it sounds like water boiling or lava when the muscle and skin start regrowing"): the regrowth is FLESH
// REBUILDING, so no bubbles, no constant fizz / hiss / crackle beds and no steady-rate grains anywhere in it. The loops are
// sparse, irregular events (0.4-1.3 s apart) over near-silence: slurps (band-passed noise through a resonance that falls in
// pitch, like meat being kneaded or pulled), sinew creaks (stick-slip impulse trains at a 180-700 Hz jittering pitch, rung
// through leathery formants), tight wet ticks, tacky "peel in reverse" releases and faint seals; only a whisper of low rumble.

/** A slow random wander, -1..1 (lowpassed noise), for jittery pitch. */
function wander(sec, hz) { return normalise(filt(noiseBuf(sec), 'lp', hz), 1, false); }
/** One envelope bump: fast rise over `a` of its width, then a curved fall. */
function bump(u, w, a = 0.15, k = 1.8) { if (u < 0 || u > w) return 0; const r = w * a; return u < r ? u / r : Math.pow(1 - (u - r) / (w - r), k); }

/**
 * A SLURP: meat kneaded / pulled. Noise through a resonance falling f0 -> f1 (plus a weaker second formant 2.3x above, the
 * "vowel" of wet flesh), shaped by 1-3 irregular overlapping pulls; a low lowpassed mass under it. No bubbles.
 */
function slurp(dur, f0, f1, q = 6, body = 0.6, lumps = 1) {
	const out = buf(dur + 0.03);
	const n = noiseBuf(dur);
	const sweep = t => f0 * Math.pow(f1 / f0, Math.pow(t, 0.7));
	const a = filt(n, 'bp', sweep, q);
	const b = filt(n, 'bp', t => sweep(t) * 2.3, q * 1.4);
	const pulls = [[0, dur * (lumps > 1 ? rr(0.45, 0.65) : 1), 1]];
	for (let k = 1; k < lumps; k++) {
		const s = dur * rr(0.25, 0.5) * k / (lumps - 1) + dur * 0.1 * (k - 1);
		pulls.push([s, Math.max(0.03, dur - s), rr(0.55, 0.9)]);
	}
	const e = u => pulls.reduce((m, [s, w, g]) => Math.max(m, g * bump(u - s, w, 0.12, 1.6)), 0);
	for (let i = 0; i < a.length; i++) { const u = i / SR, ev = e(u); a[i] = (a[i] + 0.45 * b[i]) * ev; }
	mix(out, normalise(a, 1, false), 0, 1);
	if (body > 0) {
		const m = filt(noiseBuf(dur), 'lp', t => 260 - 140 * t, 0.9);
		for (let i = 0; i < m.length; i++) m[i] *= e(i / SR);
		mix(out, normalise(m, 1, false), 0, body);
	}
	return out;
}
/**
 * A FIBRE CREAK: stretched sinew / leather / rubber. Stick-slip -- an impulse train whose rate IS the pitch (p0 -> p1 Hz),
 * wandering and jittering per pulse, now and then slipping a pulse -- in 1-3 uneven pulls ("errk-errk"), each pull swelling
 * and rising in pitch as the tension builds, rung through broad formants.
 */
function fibreCreak(dur, p0, p1, freqs, q = 4, jitter = 0.1, wob = 0.12) {
	const exc = buf(dur);
	const w = wander(dur, 7);
	const n = Math.max(1, Math.min(3, Math.round(dur / rr(0.12, 0.24))));
	const cuts = [0];
	for (let k = 1; k < n; k++) cuts.push((k + rr(-0.4, 0.4)) / n);
	cuts.push(1);
	const gains = Array.from({ length: n }, () => rr(0.55, 1));
	let t = 0;
	while (t < dur) {
		const u = t / dur;
		let k = 0;
		while (k < n - 1 && u >= cuts[k + 1]) k++;
		const local = (u - cuts[k]) / (cuts[k + 1] - cuts[k]);
		const f = (p0 + (p1 - p0) * u) * (1 + wob * w[Math.min(w.length - 1, Math.floor(t * SR))]) * (0.92 + 0.16 * local);
		t += (1 / f) * (1 + jitter * (rnd() * 2 - 1));
		if (rnd() < 0.07) continue; // a slip
		const i = Math.round(t * SR);
		const g = rr(0.6, 1) * gains[k] * Math.max(0.06, bump(local, 1, 0.45, 1.3)) * Math.pow(Math.sin(Math.PI * Math.min(1, u * 1.02)), 0.4);
		if (i + 1 < exc.length) { exc[i] += g; exc[i + 1] -= g * 0.6; }
	}
	const y = resonate(exc, freqs, q);
	mix(y, filt(exc, 'lp', p1 * 1.6), 0, 0.3); // the fundamental itself
	return normalise(y, 1, false);
}
/** A WET TICK: a fibre attaching -- a tight damped tone falling in pitch (falling, never the rising chirp of a bubble) and a soft click. */
function wetTick(f = 1600) {
	const d = 0.03;
	const out = buf(d);
	let ph = 0;
	for (let i = 0; i < out.length; i++) {
		const s = i / SR;
		ph += 2 * Math.PI * f * (0.62 + 0.38 * Math.exp(-s / 0.004)) / SR;
		out[i] = Math.sin(ph) * Math.min(1, s / 0.0004) * Math.exp(-s / 0.0045);
	}
	const c = filt(noiseBuf(0.004), 'lp', f * 1.5);
	env(c, u => Math.pow(1 - u, 3));
	mix(out, c, 0, 0.5);
	return normalise(out, 1, false);
}
/** A PEEL IN REVERSE: skin tacking down -- 3-6 soft sticky ticks closing up (accelerating) and a last, fuller "tk". Short and sparse. */
function peelReverse(f = 1) {
	const n = 3 + Math.floor(rnd() * 4);
	const out = buf(0.3);
	let t = 0, gap = rr(0.03, 0.05);
	for (let k = 0; k < n; k++) {
		const g = filt(noiseBuf(0.003), 'bp', rr(1800, 3400) * f, 2.5);
		env(g, u => Math.pow(1 - u, 2));
		mix(out, normalise(g, 1, false), t, 0.35 + 0.5 * k / n);
		t += gap; gap *= rr(0.6, 0.8);
	}
	mix(out, wetTick(rr(1300, 1800) * f), t, 0.8);
	mix(out, slurp(0.04, 900 * f, 500 * f, 4, 0.3), t, 0.25);
	return normalise(out, 1, false);
}
/** A SEAL: the faintest wet closing -- a tiny low slurp. */
function seal(f = 1) { return normalise(slurp(rr(0.05, 0.08), rr(700, 950) * f, rr(320, 450) * f, 4, 0.5), 1, false); }
/** A soft fleshy slap (the start of a squelch accent): a few ms of lowpassed noise. */
function slap(cut = 1800) { const s = filt(noiseBuf(0.018), 'lp', cut); env(s, u => Math.pow(1 - u, 3)); return normalise(s, 1, false); }
/** Events for a loop, spaced irregularly gapMin..gapMax apart, keeping the spacing across the loop's wrap. */
function placeEvents(len, gapMin, gapMax, place) {
	const first = rr(0.2, 0.4);
	let t = first;
	while (t < len + first - gapMin) { place(t); t += rr(gapMin, gapMax); }
}
/** The bed under a loop: a whisper of low organic rumble, breathing slowly (it reads as silence next to the events). */
function whisper(len, cut, gain, v) {
	const r = filt(brownBuf(len), 'lp', cut);
	env(r, (u, s) => 0.55 + 0.45 * Math.sin(s * 1.3 + v * 2.1));
	return normalise(r, gain, false);
}

/** BONE PUSH (loop): bone extending in pushes -- stick-slip grinds rung high and hard, in phrases with gaps, a lone crack now and then. */
function bonePushLoop(v) {
	return makeLoop(len => {
		const out = buf(len);
		const p = [1, 1.12, 0.9][v];
		let t = rr(0.15, 0.3);
		while (t < len - 0.15) {
			const d = rr(0.35, 0.85);
			const g = creak(d, u => 24 + 30 * u + 8 * Math.sin(u * 9 + v), [480 * p, 1050 * p, 2300 * p], 9, u => Math.pow(Math.sin(Math.PI * u), 0.8), 0.45);
			mix(out, normalise(g, 1, false), t, rr(0.55, 0.85));
			if (rnd() < 0.6) mix(out, normalise(creak(d * 0.7, u => 12 + 6 * u, [260 * p, 640 * p], 7, u => Math.sin(Math.PI * u), 0.5, 2), 1, false), t + d * 0.15, 0.35);
			if (rnd() < 0.45) click(out, t + d * rr(0.6, 0.95), rr(0.25, 0.45), rr(1600, 3200), 4);
			t += d + rr(0.25, 0.7);
		}
		mix(out, whisper(len, 150, 0.04, v), 0, 1);
		return out;
	}, 4.0, 0.25);
}
/** BONE CRACK (accent): a small sharp crack with a ring, a tiny crunch after. */
function boneCrack(v) {
	const out = buf(0.22);
	const p = rr(0.85, 1.2);
	click(out, 0, 1.0, 2200 * p, 4);
	click(out, rr(0.004, 0.012), 0.6, 1500 * p, 5);
	mix(out, resonate(env(noiseBuf(0.05), t => Math.pow(1 - t, 6)), [900 * p, 1900 * p, 3100 * p], 14, [0.5, 1, 0.6]), 0, 1.4);
	crackle(out, 0.01, 0.09, 140, 0.08, 0.3, [2000, 5000], 0.3);
	return normalise(softClip(out, 1.3), 0.85);
}

/** MUSCLE (loop): flesh kneading itself back -- sparse slurps, sinew creaks and little clusters of wet ticks over near-silence. */
function muscleLoop(v) {
	return makeLoop(len => {
		const out = buf(len);
		const p = [1, 0.9, 1.1][v];
		placeEvents(len, 0.4, 1.2, t => {
			const r = rnd();
			if (r < 0.55) {
				mix(out, slurp(rr(0.14, 0.32), rr(650, 1150) * p, rr(200, 360) * p, rr(5, 8), 0.6, rnd() < 0.4 ? 2 : 1), t, rr(0.5, 0.85));
			} else if (r < 0.85) {
				mix(out, fibreCreak(rr(0.18, 0.45), rr(170, 250) * p, rr(300, 480) * p, [380 * p, 820 * p, 1500 * p], 4), t, rr(0.45, 0.7));
			} else {
				let s = t;
				for (let k = 2 + Math.floor(rnd() * 3); k > 0; k--) { mix(out, wetTick(rr(1000, 2000) * p), s, rr(0.25, 0.45)); s += rr(0.035, 0.13); }
			}
		});
		mix(out, whisper(len, 110, 0.035, v), 0, 1);
		return out;
	}, 4.0, 0.2);
}
/** SQUELCH (accent): one punchy flesh squelch -- five different takes, wet but not splashy. */
function squelchAccent(v) {
	const f = rr(0.9, 1.12);
	const out = buf(0.6);
	if (v === 0) { // a long falling slurp
		mix(out, slap(1600), 0, 0.5);
		mix(out, slurp(0.26, 1300 * f, 300 * f, 6, 0.7), 0.004, 1);
		mix(out, thud(0.12, 140 * f, 60, 0.2), 0, 0.45);
	} else if (v === 1) { // a double knead
		mix(out, slap(1400), 0, 0.45);
		mix(out, slurp(0.3, 1000 * f, 260 * f, 7, 0.7, 2), 0.004, 1);
		mix(out, thud(0.1, 130 * f, 55, 0.2), 0, 0.4);
	} else if (v === 2) { // short and tight, a fibre catching at the end
		mix(out, slap(2200), 0, 0.55);
		mix(out, slurp(0.13, 1500 * f, 450 * f, 7, 0.5), 0.003, 1);
		mix(out, wetTick(1700 * f), 0.15, 0.5);
	} else if (v === 3) { // pulled: a slurp that drags into a sinew creak
		mix(out, slap(1500), 0, 0.4);
		mix(out, slurp(0.2, 1100 * f, 320 * f, 6, 0.6), 0.004, 1);
		mix(out, fibreCreak(0.22, 210 * f, 330 * f, [400 * f, 900 * f, 1600 * f], 4), 0.12, 0.5);
	} else { // low and heavy
		mix(out, slap(1100), 0, 0.5);
		mix(out, slurp(0.28, 850 * f, 190 * f, 5, 0.9), 0.004, 1);
		mix(out, thud(0.16, 115 * f, 45, 0.25), 0, 0.6);
	}
	return normalise(softClip(out, 1.4), 0.8);
}

/** SKIN (loop): skin drawing over -- slow leathery creaks, a sticky peel-in-reverse now and then, faint seals; higher and gentler than muscle. */
function skinLoop(v) {
	return makeLoop(len => {
		const out = buf(len);
		const p = [1, 1.1, 0.93][v];
		placeEvents(len, 0.5, 1.3, t => {
			const r = rnd();
			if (r < 0.45) {
				mix(out, fibreCreak(rr(0.25, 0.6), rr(320, 430) * p, rr(450, 680) * p, [1000 * p, 2000 * p, 3200 * p], 5, 0.08, 0.1), t, rr(0.45, 0.65));
			} else if (r < 0.75) {
				mix(out, peelReverse(p), t, rr(0.3, 0.5));
			} else {
				mix(out, seal(p * 1.2), t, rr(0.25, 0.4));
			}
		});
		mix(out, whisper(len, 160, 0.02, v), 0, 1);
		return out;
	}, 4.0, 0.2);
}
/** SKIN STRETCH (accent): skin pulling tight -- a leathery creak rising in pitch, ending in a tacky release or a seal. */
function skinStretch(v) {
	const f = rr(0.9, 1.15);
	const d = rr(0.28, 0.48);
	const out = buf(d + 0.3);
	const lo = [250, 300, 220, 340, 280][v], hi = [520, 600, 430, 700, 560][v];
	mix(out, fibreCreak(d, lo * f, hi * f, [850 * f, 1700 * f, 2900 * f], 5, 0.09, 0.14), 0, 1);
	if (v % 2 === 0) mix(out, peelReverse(f), d * 0.85, 0.5);
	else mix(out, seal(f * 1.3), d * 0.9, 0.35);
	return normalise(softClip(out, 1.2), 0.7);
}

/** FINISH: the part whole -- a tight wet snap (slap, short squelch, click, low thud), a slow sucking settle and a last faint seal. */
function finish(v) {
	const out = buf(0.6);
	const f = rr(0.9, 1.15);
	mix(out, slap(2000), 0, 0.6);
	mix(out, slurp(0.08, 1600 * f, 500 * f, 6, 0.4), 0.002, 1);
	mix(out, wetTick(1800 * f), 0.003, 0.6);
	mix(out, thud(0.16, 150 * f, 65, 0.3), 0.01, 0.6);
	mix(out, slurp(0.2, 650 * f, 230 * f, 5, 0.8), 0.1, 0.45);
	mix(out, seal(f), rr(0.33, 0.4), 0.18);
	return normalise(softClip(out, 1.5), 0.85);
}

// ================================================================ BREAKS AND BLOOD

/** PART BREAK: a wet tear into a crunch -- bone cracks, the tear, a heavy low splat, blood bubbling out. */
function partBreak(v) {
	const out = buf(0.9);
	const f = rr(0.85, 1.15);
	// the tear runs first, short
	mix(out, tear(0.16, 1500 * f, 320), 0, 0.8);
	// the crunch: a fast cluster of hard cracks with ring
	const crunchAt = rr(0.06, 0.1);
	for (let k = 0; k < 7; k++) click(out, crunchAt + k * rr(0.004, 0.012), rr(0.6, 1.1), rr(1200, 2600), 6);
	mix(out, resonate(env(noiseBuf(0.08), t => Math.pow(1 - t, 5)), [700 * f, 1500 * f, 2700 * f], 8), crunchAt, 1.5);
	// the meat: a low wet slap
	mix(out, thud(0.25, 130 * f, 50, 0.9), crunchAt, 1.0);
	mix(out, squelch(0.2, 300 * f, 1100 * f, 4, 1.4), crunchAt + 0.01, 0.9);
	// blood bubbling out after
	bubbles(out, crunchAt + 0.05, 0.6, t => 90 * (1 - t), 250, 1400, 0.08, 0.35, 0.01, 0.04);
	return normalise(softClip(out, 1.8), 0.95);
}
/** One droplet landing (for scatters): a tiny wet tick plus a high bubble. */
function droplet(dst, at, amp) {
	const d = filt(noiseBuf(0.012), 'bp', rr(1500, 3500), 2);
	env(d, t => Math.pow(1 - t, 3));
	mix(dst, d, at, amp);
	mix(dst, bubble(rr(1100, 2600), rr(0.01, 0.022), rr(0.5, 1.4)), at + 0.001, amp * 0.6);
}
/** BLOOD BURST: a big splash -- a broadband wash falling in brightness, a heavy low slap, a churn of bubbles, droplets raining down. */
function bloodBurst(v) {
	const len = 1.4;
	const out = buf(len);
	const f = rr(0.85, 1.15);
	const wash = filt(noiseBuf(0.6), 'lp', t => 7000 * Math.pow(0.12, t) * f + 400, 0.9);
	env(wash, t => Math.min(1, t * 40) * Math.pow(1 - t, 2.2));
	mix(out, wash, 0, 1.3);
	mix(out, thud(0.3, 100 * f, 40, 1.0), 0, 1.1);
	mix(out, squelch(0.25, 260 * f, 900 * f, 3.5, 2), 0.005, 1.0);
	bubbles(out, 0.02, 0.8, t => 260 * Math.pow(1 - t, 1.5), 180, 1600, 0.1, 0.45, 0.008, 0.04);
	// droplets raining back down
	for (let k = 0; k < 26; k++) droplet(out, rr(0.25, 1.25), rr(0.08, 0.3) * (1 - k / 40));
	return normalise(softClip(out, 1.9), 0.95);
}
/** BLOOD SPRAY: a short pressurised spurt -- a pulsing hiss-gush with a gurgle. */
function bloodSpray(v) {
	const len = rr(0.32, 0.45);
	const out = buf(len + 0.1);
	const f = rr(0.85, 1.2);
	const g = filt(noiseBuf(len), 'bp', t => (1700 + 900 * Math.sin(t * 9)) * f, 1.3);
	const pulses = rr(2, 4);
	env(g, t => Math.min(1, t * 25) * Math.pow(1 - t, 1.3) * (0.55 + 0.45 * Math.abs(Math.sin(Math.PI * pulses * t))));
	mix(out, g, 0, 1.2);
	mix(out, env(filt(noiseBuf(len), 'hp', 4500), t => Math.pow(1 - t, 2)), 0, 0.35);
	bubbles(out, 0.01, len, 160, 300 * f, 1500 * f, 0.08, 0.3, 0.008, 0.03);
	mix(out, squelch(0.08, 600 * f, 1300 * f, 4, 0.5), 0, 0.5);
	return normalise(softClip(out, 1.5), 0.8);
}
/** BLOOD DRIP: one small thick drop -- a plink (rising bubble) with a soft wet tick, sometimes a second tiny one. */
function bloodDrip(v) {
	const out = buf(0.3);
	const f = rr(700, 1300);
	mix(out, bubble(f, rr(0.035, 0.06), rr(1.2, 2.2), 0.05), 0.002, 1);
	const t = filt(noiseBuf(0.008), 'bp', f * 2.2, 2);
	env(t, u => Math.pow(1 - u, 3));
	mix(out, t, 0, 0.5);
	if (v % 2 === 0) mix(out, bubble(f * rr(1.3, 1.8), 0.025, 1.5), rr(0.09, 0.16), 0.35);
	return normalise(out, 0.6);
}
/** BLOOD SPLAT: a droplet landing -- a short wet slap, a squelch flick and a bubble. */
function bloodSplat(v) {
	const out = buf(0.35);
	const f = rr(0.85, 1.2);
	const slap = filt(noiseBuf(0.03), 'lp', 2600 * f);
	env(slap, t => Math.pow(1 - t, 3));
	mix(out, slap, 0, 1.0);
	mix(out, squelch(0.07, 1200 * f, 450 * f, 4, 0.8), 0.002, 0.7);
	for (let k = 0; k < 4; k++) droplet(out, rr(0.03, 0.2), rr(0.1, 0.3));
	return normalise(softClip(out, 1.4), 0.7);
}

// ================================================================ FLIGHT

/** JET LOOP: pressurised blood gushing from the body -- a roaring band-limited gush, a thin high hiss, a gurgling churn, low rumble. */
function jetLoop(v) {
	return makeLoop(len => {
		const out = buf(len);
		const p = [1, 0.9, 1.12][v];
		const gush = filt(noiseBuf(len), 'bp', (t, s) => (1100 + 300 * Math.sin(s * 4.3 + v) + 180 * Math.sin(s * 11.1)) * p, 0.9);
		let fl = 0.7;
		env(gush, () => { fl += (rnd() - 0.5) * 0.08; fl = Math.max(0.55, Math.min(1, fl)); return fl; });
		mix(out, gush, 0, 1.2);
		const hiss = filt(noiseBuf(len), 'hp', 5200 * p);
		env(hiss, (t, s) => 0.28 + 0.06 * Math.sin(s * 17));
		mix(out, hiss, 0, 1);
		bubbles(out, 0, len, 220, 200 * p, 1100 * p, 0.06, 0.24, 0.01, 0.035);
		const rumble = filt(brownBuf(len), 'lp', 160);
		mix(out, rumble, 0, 1.5);
		crackle(out, 0, len, 18, 0.05, 0.15, [1200, 3000], 0.6);
		return out;
	}, 4.0, 0.5);
}
/** JET START: the valve bursting open -- a wet pop and a gush swelling up into the jet. */
function jetStart(v) {
	const out = buf(0.8);
	const f = rr(0.9, 1.15);
	mix(out, squelch(0.1, 400 * f, 1400 * f, 5, 1.5), 0, 0.9);
	mix(out, thud(0.15, 140 * f, 60, 0.5), 0, 0.6);
	const swell = filt(noiseBuf(0.75), 'bp', t => (600 + 900 * Math.sqrt(t)) * f, 1.0);
	env(swell, t => Math.min(1, t * 3) * (t > 0.7 ? (1 - t) / 0.3 : 1));
	mix(out, swell, 0.04, 1.1);
	bubbles(out, 0.05, 0.6, 200, 250, 1300, 0.06, 0.25);
	return normalise(softClip(out, 1.4), 0.85);
}
/** JET STOP: the jet sputtering out -- a choking gush that stutters, gurgles and ends in a couple of drips. */
function jetStop(v) {
	const out = buf(1.0);
	const f = rr(0.9, 1.15);
	const g = filt(noiseBuf(0.55), 'bp', t => 1200 * f * (1 - 0.5 * t), 1.1);
	env(g, t => Math.pow(1 - t, 1.5) * (rnd() < 0.5 ? 1 : 0.6) * (0.4 + 0.6 * (Math.sin(t * 60 * (1 - 0.5 * t)) > -0.2 ? 1 : 0.15)));
	mix(out, filt(g, 'lp', 4000), 0, 1.0);
	bubbles(out, 0.05, 0.55, t => 140 * (1 - t), 180, 900, 0.1, 0.35, 0.015, 0.05);
	mix(out, bubble(rr(700, 1000), 0.05, 1.6), rr(0.65, 0.72), 0.35);
	mix(out, bubble(rr(900, 1300), 0.04, 1.8), rr(0.82, 0.9), 0.25);
	return normalise(softClip(out, 1.3), 0.75);
}
/** BURN LOOP: the boost burning his body away -- dense frying crackle, searing hiss, fat-bubble sizzle and a wind of speed. */
function burnLoop(v) {
	return makeLoop(len => {
		const out = buf(len);
		const p = [1, 1.08, 0.94][v];
		crackle(out, 0, len, t => 160 + 80 * Math.sin(t * len * 3 + v), 0.08, 0.4, [1800 * p, 6000 * p], 0.55);
		const sear = filt(noiseBuf(len), 'hp', 4200 * p);
		let s = 0.5;
		env(sear, () => { s += (rnd() - 0.5) * 0.05; s = Math.max(0.3, Math.min(0.9, s)); return s; });
		mix(out, sear, 0, 0.6);
		bubbles(out, 0, len, 500, 2500 * p, 7000 * p, 0.02, 0.08, 0.002, 0.006);
		const wind = filt(noiseBuf(len), 'bp', (t, sec) => (420 + 220 * Math.sin(sec * 1.7 + v)) * p, 1.4);
		env(wind, (t, sec) => 0.6 + 0.3 * Math.sin(sec * 2.3 + v * 1.7));
		mix(out, wind, 0, 1.3);
		// a dark meat-searing roar underneath
		mix(out, filt(brownBuf(len), 'lp', 300), 0, 1.0);
		return out;
	}, 4.0, 0.5);
}

// ================================================================ MOVES

/** HEAD ROLL: soft thuds and a short roll -- 3-5 bouncing thuds, smaller each time, a soft scuffing roll between. */
function headRoll(v) {
	const out = buf(1.3);
	const f = rr(0.9, 1.1);
	let t = 0, a = 1, gap = rr(0.2, 0.3);
	const n = 3 + (v % 3);
	for (let k = 0; k < n; k++) {
		mix(out, thud(0.2, 120 * f * (1 + k * 0.08), 60, 0.5), t, a);
		mix(out, squelch(0.06, 500 * f, 300 * f, 3, 0.3), t, a * 0.35);
		t += gap; gap *= 0.65; a *= 0.6;
	}
	const roll = filt(noiseBuf(0.5), 'lp', 600);
	env(roll, u => Math.sin(Math.PI * u) * (0.6 + 0.4 * Math.sin(u * 50)));
	mix(out, roll, t - 0.05, 0.35);
	return normalise(softClip(out, 1.3), 0.75);
}
/** LAUNCH WHOOSH: a body part leaving fast -- a wet flap, then a whoosh sweeping up and away. */
function launchWhoosh(v) {
	const len = rr(0.55, 0.75);
	const out = buf(len + 0.05);
	const f = rr(0.85, 1.2);
	const w = filt(noiseBuf(len), 'bp', t => (350 + 2600 * Math.sin(Math.PI * Math.min(1, t * 1.2)) ** 2) * f, 1.6);
	env(w, t => Math.pow(Math.sin(Math.PI * Math.pow(t, 0.7)), 2));
	mix(out, w, 0, 1.2);
	mix(out, filt(noiseBuf(len), 'lp', 300), 0, 0.2);
	mix(out, squelch(0.08, 500 * f, 1200 * f, 4, 0.8), 0, 0.6);
	mix(out, thud(0.1, 150, 70, 0.4), 0, 0.4);
	return normalise(softClip(out, 1.3), 0.85);
}
/** PARTS BULLET: a flesh pellet popping off -- a tight wet pop, a click and a short flick. */
function partsBullet(v) {
	const out = buf(0.3);
	const f = rr(0.85, 1.2);
	mix(out, bubble(320 * f, 0.03, 2.5, 0.2), 0, 1.2);
	click(out, 0.001, 0.9, 1600 * f, 4);
	mix(out, squelch(0.05, 900 * f, 1800 * f, 5, 0.6), 0.002, 0.6);
	const flick = filt(noiseBuf(0.12), 'bp', t => 2200 * f * (1 + t), 2);
	env(flick, t => Math.pow(1 - t, 2.5));
	mix(out, flick, 0.01, 0.5);
	mix(out, thud(0.08, 180 * f, 90, 0.3), 0, 0.5);
	return normalise(softClip(out, 1.6), 0.85);
}
/** CRIMSON BULLET: a heavier wet shot -- a deep thump, a squelching burst, a crack and a short splash tail. */
function crimsonBullet(v) {
	const out = buf(0.9);
	const f = rr(0.85, 1.15);
	mix(out, thud(0.35, 95 * f, 35, 1.0), 0, 1.3);
	mix(out, squelch(0.18, 280 * f, 1200 * f, 3.5, 2), 0.003, 1.1);
	click(out, 0.002, 1.0, 1400, 6);
	mix(out, resonate(env(noiseBuf(0.06), t => Math.pow(1 - t, 5)), [600 * f, 1400 * f], 6), 0.002, 1.0);
	const tail = filt(noiseBuf(0.5), 'lp', t => 3000 * (1 - 0.8 * t));
	env(tail, t => Math.pow(1 - t, 2.5));
	mix(out, tail, 0.04, 0.6);
	bubbles(out, 0.05, 0.4, 140, 200, 1100, 0.08, 0.3);
	for (let k = 0; k < 8; k++) droplet(out, rr(0.2, 0.8), rr(0.06, 0.18));
	return normalise(softClip(out, 2.0), 0.95);
}
/** PUNCH STRETCH (loop): sinew stretching under load -- a low, groaning stick-slip with wet strands squeaking (the code raises the pitch as it charges). */
function punchStretchLoop(v) {
	return makeLoop(len => {
		const out = buf(len);
		const p = [1, 0.9, 1.1][v];
		mix(out, creak(len, t => 26 + 14 * Math.sin(t * len * 2.2 + v) + 8 * Math.sin(t * len * 6.1), [300 * p, 700 * p, 1250 * p], 8,
				t => 0.6 + 0.4 * Math.sin(t * len * 1.6 + v), 0.4, 1.8), 0, 1.0);
		mix(out, creak(len, t => 70 + 30 * Math.sin(t * len * 3.3 + v), [1600 * p, 2600 * p], 14, t => 0.25 + 0.25 * Math.sin(t * len * 2.7 + v * 2), 0.25, 0.6), 0, 0.45);
		bubbles(out, 0, len, 14, 250 * p, 800 * p, 0.06, 0.2, 0.015, 0.05);
		mix(out, filt(brownBuf(len), 'lp', 200), 0, 0.7);
		return out;
	}, 3.0, 0.4);
}
/** PUNCH FIRE: the stretched arm let go -- a sinew snap, a fast whoosh and a wet slap at the end. */
function punchFire(v) {
	const out = buf(0.7);
	const f = rr(0.9, 1.15);
	click(out, 0, 1.0, 1800 * f, 5);
	mix(out, resonate(env(noiseBuf(0.05), t => Math.pow(1 - t, 5)), [500 * f, 1100 * f], 7), 0, 1.2);
	const w = filt(noiseBuf(0.35), 'bp', t => (2400 - 1700 * t) * f, 1.5);
	env(w, t => Math.min(1, t * 15) * Math.pow(1 - t, 1.6));
	mix(out, w, 0.01, 1.0);
	mix(out, squelch(0.12, 1100 * f, 400 * f, 4, 1), 0.3, 0.8);
	mix(out, thud(0.2, 110 * f, 50, 0.7), 0.3, 0.9);
	return normalise(softClip(out, 1.6), 0.9);
}

// ================================================================ render all

const SPEC = [
	['regrow_bone_push', 3, v => normalise(bonePushLoop(v), 0.8, false)],
	['regrow_bone_crack', 5, boneCrack],
	['regrow_muscle', 3, v => normalise(muscleLoop(v), 0.8, false)],
	['regrow_squelch', 5, squelchAccent],
	['regrow_skin', 3, v => normalise(skinLoop(v), 0.7, false)],
	['regrow_skin_stretch', 5, skinStretch],
	['regrow_finish', 4, finish],
	['part_break', 5, partBreak],
	['blood_burst', 4, bloodBurst],
	['blood_spray', 4, bloodSpray],
	['blood_drip', 5, bloodDrip],
	['blood_splat', 5, bloodSplat],
	['blood_jet_loop', 3, v => normRms(jetLoop(v), 0.16)],
	['blood_jet_start', 3, jetStart],
	['blood_jet_stop', 3, jetStop],
	['boost_burn_loop', 3, v => normRms(burnLoop(v), 0.15)],
	['head_roll', 4, headRoll],
	['launch_whoosh', 4, launchWhoosh],
	['bullet_parts', 4, partsBullet],
	['bullet_crimson', 4, crimsonBullet],
	['punch_stretch', 3, v => normRms(punchStretchLoop(v), 0.13)],
	['punch_fire', 4, punchFire],
];
// optional 2nd argument: a regex of names to render (e.g. "^regrow_(muscle|skin)"); the rest are left alone (every file has
// its own seed, so a partial render matches a full one; re-encoding an unchanged file still changes its OGG bytes)
const ONLY = process.argv[3] ? new RegExp(process.argv[3]) : null;
for (const [name, n, gen] of SPEC) {
	if (ONLY && !ONLY.test(name)) continue;
	for (let v = 0; v < n; v++) {
		seed = (name.length * 7919 + v * 104729 + name.charCodeAt(0) * 31) >>> 0;
		save(name + (v + 1), gen(v));
	}
}
console.log(made.length, 'files');
