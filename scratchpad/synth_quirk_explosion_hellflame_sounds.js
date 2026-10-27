// v0.17.1 Explosion + Hellflame revamp: synthesises the two kits' own sounds (quirk_explosion_* and quirk_hellflame_*) the
// same way as synth_quirk_framework_sounds.js -- filtered noise and sines, 44.1 kHz MONO WAVs encoded to OGG Vorbis by an
// ffmpeg WITH libvorbis (Medal's %LOCALAPPDATA%\Medal\recorder-*\ffmpeg7.exe has it; ffmpeg's own "vorbis" encoder is
// stereo-only and Minecraft needs mono for positional sound).
//   node scratchpad/synth_quirk_explosion_hellflame_sounds.js "<path to ffmpeg7.exe>"
// Output: src/main/resources/assets/projecthero/sounds/quirk/{explosion,hellflame}_*.ogg
//
// Explosion: crackle (the sweat igniting on the palms), blast_small (sharp AP pop), blast (the medium boom with a sub
// thump), howitzer (corkscrew whine into the deepest boom), stun (flashbang crack + ring), pierce (charge whine snapping
// into a tearing beam). Hellflame: roar (a flame burst), jet (the jet burn), vent (whoomp + steam), beam (Hellblast's
// ignition), comet (the Prominence Burn scream into the detonation), whip (Hell Spider's strings).
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');

const SR = 44100;
const FFMPEG = process.argv[2];
const WAV_DIR = path.join(os.tmpdir(), 'quirk_exhf_wav');
const OUT_DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds', 'quirk');
fs.mkdirSync(WAV_DIR, { recursive: true });
fs.mkdirSync(OUT_DIR, { recursive: true });

let seed = 1717;
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
function mix(dst, src, at, gain = 1) { const o = Math.round(at * SR); for (let i = 0; i < src.length && i + o < dst.length; i++) dst[i + o] += src[i] * gain; }
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

function whoosh(len, lo, hi, q) {
	const x = sweep(noiseBuf(len), 'bp', t => lo + (hi - lo) * Math.sin(Math.PI * Math.min(1, t * 1.1)), q);
	env(x, t => Math.pow(Math.sin(Math.PI * Math.min(1, t)), 1.6));
	return normalise(x, 0.8);
}
function impact(len, thumpHz, burstCut, crack) {
	const out = buf(len);
	mix(out, env(sweep(noiseBuf(0.012), 'hp', () => 2500), t => 1 - t), 0, crack);
	mix(out, env(sweep(noiseBuf(len * 0.5), 'lp', t => burstCut * (1 - 0.7 * t)), t => Math.pow(1 - t, 3)), 0.002, 0.9);
	mix(out, tone(len, t => thumpHz * (1.6 - 0.75 * Math.min(1, t * 3)), t => Math.pow(1 - t, 2.5), [1, 0.25]), 0.0, 1.1);
	return normalise(softClip(out, 1.4), 0.92);
}

// ---------------------------------------------------------------- building blocks

/** A crackle: tiny randomly spaced high clicks (sparks), a fizz bed and (optionally) a soft pop at the end. */
function crackle(len, density, pop) {
	const out = buf(len);
	const n = Math.round(len * density);
	for (let i = 0; i < n; i++) {
		const at = rnd() * len * 0.9;
		const c = env(sweep(noiseBuf(0.004 + rnd() * 0.006), 'hp', () => 3000 + rnd() * 5000), t => Math.pow(1 - t, 2));
		mix(out, c, at, 0.35 + rnd() * 0.65);
	}
	mix(out, env(sweep(noiseBuf(len), 'bp', t => 5200 - 1500 * t, 1.2), t => Math.sin(Math.PI * t) * 0.35), 0, 0.5);
	if (pop) {
		mix(out, impact(0.18, 140, 3200, 1.0), len - 0.2, 0.6 * pop);
	}
	return normalise(out, 0.7);
}

/** A sharp blast: a hard crack, a bright burst and a punchy thump. */
function sharpBlast(len, thumpHz, bright) {
	const out = buf(len);
	mix(out, env(sweep(noiseBuf(0.01), 'hp', () => 3500), t => 1 - t), 0, 1.1);
	mix(out, env(sweep(noiseBuf(len * 0.6), 'lp', t => bright * (1 - 0.85 * t)), t => Math.pow(1 - t, 2.4)), 0.001, 1.1);
	mix(out, tone(len * 0.7, t => thumpHz * (1.8 - 0.9 * Math.min(1, t * 2.5)), t => Math.pow(1 - t, 2.2), [1, 0.4, 0.15]), 0, 1.0);
	mix(out, crackle(len * 0.8, 80, 0), 0.04, 0.25);
	return normalise(softClip(out, 2.0), 0.95);
}

/** A big blast: crack, fat noise burst, the sub-bass thump and a rolling rumble with crackle in the tail. */
function bigBlast(len, sub, tail) {
	const out = buf(len);
	mix(out, env(sweep(noiseBuf(0.02), 'hp', () => 2400), t => 1 - t), 0, 1.2);
	mix(out, env(sweep(noiseBuf(len * 0.45), 'lp', t => 3800 * (1 - 0.85 * t)), t => Math.pow(1 - t, 1.8)), 0.002, 1.2);
	mix(out, tone(len * 0.8, t => sub * (1.9 - 1.0 * Math.min(1, t * 1.6)), t => Math.pow(1 - t, 1.6), [1, 0.4, 0.15]), 0, 1.6);
	mix(out, env(sweep(noiseBuf(len), 'lp', t => 300 - 180 * t, 0.9), t => Math.min(1, t * 10) * Math.pow(1 - t, 1.4)), 0.04, tail);
	mix(out, crackle(len * 0.7, 60, 0), len * 0.18, 0.3);
	return normalise(softClip(out, 2.2), 0.97);
}

/** A flame roar: noise shaped like a gas burner, a low rumble and a flickering top. */
function roar(len, cut, attack, low) {
	const out = buf(len);
	const body = sweep(noiseBuf(len), 'lp', t => cut * (0.6 + 0.4 * Math.sin(Math.PI * Math.min(1, t * 1.2))), 0.8);
	let fl = 0;
	for (let i = 0; i < body.length; i++) {
		fl += 2 * Math.PI * (7 + 5 * Math.sin(i / SR * 3.1)) / SR;
		body[i] *= 0.7 + 0.3 * Math.sin(fl);
	}
	env(body, t => Math.min(1, t / attack) * Math.pow(1 - t, 1.2));
	mix(out, body, 0, 1.0);
	mix(out, env(sweep(noiseBuf(len), 'lp', () => 160, 0.9), t => Math.min(1, t / attack) * Math.pow(1 - t, 1.5)), 0, low);
	mix(out, env(sweep(noiseBuf(len), 'bp', t => 3200 + 800 * Math.sin(t * 40), 2.5), t => Math.sin(Math.PI * t) * 0.3), 0, 0.4);
	return normalise(softClip(out, 1.6), 0.9);
}

// ---------------------------------------------------------------- Explosion

function howitzer() {
	const len = 2.6;
	const out = buf(len);
	const spin = 0.55;
	const whine = env(sweep(noiseBuf(spin), 'bp', t => 400 + 2600 * t * t, 3), t => t * t);
	for (let i = 0; i < whine.length; i++) whine[i] *= 0.6 + 0.4 * Math.sin(2 * Math.PI * (10 + 30 * i / whine.length) * i / SR);
	mix(out, whine, 0, 0.9);
	for (let k = 0; k < 5; k++) mix(out, sharpBlast(0.25, 120 + 20 * k, 3000), k * 0.1, 0.25 + 0.06 * k);
	mix(out, bigBlast(2.0, 46, 1.4), spin, 1.0);
	return normalise(softClip(out, 1.6), 0.98);
}

function stunFlash() {
	const len = 1.4;
	const out = buf(len);
	mix(out, sharpBlast(0.5, 160, 7000), 0, 1.0);
	mix(out, env(sweep(noiseBuf(0.9), 'hp', t => 6500 - 2000 * t), t => Math.pow(1 - t, 2)), 0.02, 0.5);
	mix(out, tone(1.2, t => 3600 - 300 * t, t => Math.min(1, t * 30) * Math.pow(1 - t, 1.3), [1, 0.1]), 0.05, 0.18);
	return normalise(softClip(out, 1.5), 0.9);
}

function pierce() {
	const len = 1.1;
	const out = buf(len);
	mix(out, tone(0.35, t => 600 + 2200 * t, t => t * t, [1, 0.5, 0.25]), 0, 0.3);
	mix(out, sharpBlast(0.4, 180, 9000), 0.33, 0.9);
	const tear = env(sweep(noiseBuf(0.7), 'bp', t => 2600 - 1400 * t, 2.2), t => Math.min(1, t * 20) * Math.pow(1 - t, 1.4));
	for (let i = 0; i < tear.length; i++) tear[i] *= 0.7 + 0.3 * Math.sin(2 * Math.PI * 55 * i / SR);
	mix(out, tear, 0.36, 0.8);
	return normalise(softClip(out, 1.6), 0.95);
}

// ---------------------------------------------------------------- Hellflame

function jet() {
	const len = 1.0;
	const out = buf(len);
	mix(out, impact(0.3, 90, 2000, 0.7), 0, 0.8);
	const rush = env(sweep(noiseBuf(len), 'bp', t => 2600 - 1900 * t, 1.1), t => Math.min(1, t * 14) * Math.pow(1 - t, 1.1));
	mix(out, rush, 0.01, 1.0);
	mix(out, roar(len, 1800, 0.05, 0.8), 0, 0.7);
	return normalise(softClip(out, 1.5), 0.92);
}

function vent() {
	const len = 1.4;
	const out = buf(len);
	mix(out, bigBlast(0.7, 70, 0.6), 0, 0.7);
	mix(out, env(sweep(noiseBuf(1.3), 'hp', t => 3500 + 2500 * t), t => Math.min(1, t * 6) * Math.pow(1 - t, 1.2)), 0.05, 0.75);
	return normalise(softClip(out, 1.3), 0.9);
}

function beam() {
	const len = 1.5;
	const out = buf(len);
	mix(out, bigBlast(0.8, 60, 0.5), 0, 0.6);
	mix(out, roar(len, 2600, 0.04, 1.0), 0.02, 1.0);
	mix(out, tone(len, t => 700 + 500 * t, t => Math.min(1, t * 6) * (1 - t), [1, 0.3]), 0.05, 0.08);
	return normalise(softClip(out, 1.6), 0.95);
}

function comet() {
	const len = 3.0;
	const out = buf(len);
	const fly = 1.5;
	mix(out, roar(fly, 3000, 0.25, 1.2), 0, 1.0);
	const shriek = tone(fly, t => 500 + 1700 * t * t, t => t * (0.7 + 0.3 * Math.sin(2 * Math.PI * 18 * t * fly)), [1, 0.5, 0.3, 0.2]);
	mix(out, sweep(shriek, 'lp', () => 5000), 0, 0.3);
	mix(out, env(sweep(noiseBuf(fly), 'bp', t => 800 + 4000 * t, 2), t => t * t), 0, 0.6);
	mix(out, bigBlast(1.5, 42, 1.5), fly - 0.05, 1.1);
	return normalise(softClip(out, 1.7), 0.98);
}

function whip() {
	const out = buf(0.55);
	mix(out, whoosh(0.3, 900, 4200, 2.2), 0, 0.8);
	mix(out, roar(0.45, 2800, 0.02, 0.4), 0.05, 0.6);
	return normalise(out, 0.85);
}

save('explosion_crackle', crackle(0.55, 70, 1));
save('explosion_blast_small', sharpBlast(0.45, 150, 5200));
save('explosion_blast', bigBlast(1.0, 70, 0.9));
save('explosion_howitzer', howitzer());
save('explosion_stun', stunFlash());
save('explosion_pierce', pierce());
save('hellflame_roar', roar(0.9, 2200, 0.06, 0.9));
save('hellflame_jet', jet());
save('hellflame_vent', vent());
save('hellflame_beam', beam());
save('hellflame_comet', comet());
save('hellflame_whip', whip());
