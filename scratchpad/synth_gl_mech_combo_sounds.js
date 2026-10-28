// v0.17.1 Hard-Light Mech construct combos: synthesises the Giga Drill and Hard-Light Katana sounds (gl_mech_*) the same
// way as synth_quirk_explosion_hellflame_sounds.js -- filtered noise, sines and FM partials, 44.1 kHz MONO WAVs encoded
// to OGG Vorbis by an ffmpeg WITH libvorbis (Medal's %LOCALAPPDATA%\Medal\recorder-*\ffmpeg7.exe; ffmpeg's own
// "vorbis" encoder is stereo-only and Minecraft needs mono for positional sound).
//   node scratchpad/synth_gl_mech_combo_sounds.js "<path to ffmpeg7.exe>"
// Output: src/main/resources/assets/projecthero/sounds/green_lantern/mech_*.ogg
//
// drill_spin (a whine over a grinding rattle -- the game replays it with the pitch rising), drill_charge (thruster roar
// + the drill screaming up), drill_grind (rock being chewed), giga_break (a rising scream into a huge boom and a long
// shimmer), katana_draw (a long bright metallic ring with a slide), katana_sheath (a slide into a click), katana_swish
// (an airy blade swish), katana_hit (a cut connecting: a crack + a ringing tick), katana_iaido (a sharp ring snapping
// into a rushing crescent).
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');

const SR = 44100;
const FFMPEG = process.argv[2];
const WAV_DIR = path.join(os.tmpdir(), 'gl_mech_combo_wav');
const OUT_DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds', 'green_lantern');
fs.mkdirSync(WAV_DIR, { recursive: true });
fs.mkdirSync(OUT_DIR, { recursive: true });

let seed = 9091;
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
// inharmonic partials (struck metal): [ratio, gain, decay seconds]
function bell(sec, f0, partials, attack = 0.002) {
	const b = buf(sec);
	for (const [r, g, d] of partials) {
		const f = f0 * r;
		for (let i = 0; i < b.length; i++) {
			const t = i / SR;
			b[i] += Math.sin(2 * Math.PI * f * t + 0.3 * Math.sin(2 * Math.PI * 5.5 * t)) * g * Math.exp(-t / d) * Math.min(1, t / attack);
		}
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
// a grinding rattle: noise chopped at a rate (the flutes biting), band-passed
function rattle(sec, rate, lo, hi, q = 1.2) {
	const n = noiseBuf(sec);
	for (let i = 0; i < n.length; i++) {
		const t = i / SR;
		const r = typeof rate === 'function' ? rate(i / n.length) : rate;
		n[i] *= 0.35 + 0.65 * Math.pow(0.5 + 0.5 * Math.sin(2 * Math.PI * r * t), 3);
	}
	return sweep(n, 'bp', t => lo + (hi - lo) * t, q);
}

// ---- drill_spin: 0.55 s, a whining motor + spiral rattle (looped by replays at rising pitch)
{
	const len = 0.55, out = buf(len);
	mix(out, env(tone(len, t => 420 + 30 * Math.sin(t * 40), () => 1, [1, 0.5, 0.35, 0.2, 0.12]), t => Math.sin(Math.PI * t) ** 0.4), 0, 0.5);
	mix(out, env(tone(len, () => 1260, () => 1, [1, 0.3]), t => Math.sin(Math.PI * t) ** 0.6), 0, 0.18);
	mix(out, env(rattle(len, 46, 900, 1400, 1.6), t => Math.sin(Math.PI * t) ** 0.5), 0, 0.9);
	mix(out, env(sweep(noiseBuf(len), 'lp', () => 260), t => Math.sin(Math.PI * t) ** 0.5), 0, 0.6);
	save('mech_drill_spin', normalise(softClip(out, 1.3), 0.85));
}
// ---- drill_charge: 1.3 s, a thruster blast + the drill screaming up in pitch
{
	const len = 1.3, out = buf(len);
	mix(out, env(sweep(noiseBuf(len), 'lp', t => 1800 - 1200 * t), t => Math.min(1, t * 30) * Math.pow(1 - t, 1.3)), 0, 1.0);
	mix(out, env(tone(len, t => 50 + 30 * (1 - t), t => Math.pow(1 - t, 2), [1, 0.4]), t => Math.min(1, t * 40)), 0, 0.9);
	mix(out, env(tone(len, t => 500 + 900 * Math.pow(t, 0.6), () => 1, [1, 0.6, 0.3, 0.15]), t => Math.min(1, t * 6) * Math.pow(1 - t, 0.8)), 0, 0.35);
	mix(out, env(rattle(len, t => 50 + 90 * t, 700, 2200, 1.4), t => Math.min(1, t * 8) * Math.pow(1 - t, 1.0)), 0, 0.7);
	save('mech_drill_charge', normalise(softClip(out, 1.6), 0.92));
}
// ---- drill_grind: 0.5 s, rock chewed: a low crunching rattle with debris ticks
{
	const len = 0.5, out = buf(len);
	mix(out, env(rattle(len, 33, 260, 520, 0.9), t => Math.min(1, t * 40) * Math.pow(1 - t, 0.7)), 0, 1.0);
	mix(out, env(sweep(noiseBuf(len), 'lp', () => 140), t => Math.pow(1 - t, 0.8)), 0, 0.8);
	for (let k = 0; k < 18; k++) {
		const at = rnd() * (len - 0.03);
		mix(out, env(sweep(noiseBuf(0.012), 'bp', () => 1800 + rnd() * 2500, 2), t => 1 - t), at, 0.5 + rnd() * 0.4);
	}
	mix(out, env(tone(len, () => 640, () => 1, [1, 0.3]), t => Math.pow(1 - t, 0.5)), 0, 0.12);
	save('mech_drill_grind', normalise(softClip(out, 1.8), 0.9));
}
// ---- giga_break: 2.8 s, a 0.5 s scream up, a huge boom (sub drop + crack + burst) and a long hard-light shimmer
{
	const len = 2.8, out = buf(len);
	mix(out, env(tone(0.55, t => 700 + 2200 * t * t, t => Math.min(1, t * 4), [1, 0.5, 0.3]), t => 1), 0, 0.32);
	mix(out, env(rattle(0.55, t => 80 + 220 * t, 1200, 3000, 1.4), t => t), 0, 0.5);
	const B = 0.52;
	mix(out, env(sweep(noiseBuf(0.02), 'hp', () => 2200), t => 1 - t), B, 1.0);
	mix(out, env(sweep(noiseBuf(1.8), 'lp', t => 3200 * Math.pow(1 - t, 2) + 120), t => Math.pow(1 - t, 2.6)), B + 0.002, 1.2);
	mix(out, env(tone(2.0, t => 70 * (1.7 - 0.8 * Math.min(1, t * 2.5)), t => Math.pow(1 - t, 2.2), [1, 0.35, 0.12]), t => Math.min(1, t * 200)), B, 1.5);
	mix(out, env(bell(2.2, 330, [[1, 0.35, 0.9], [2.76, 0.25, 0.7], [5.4, 0.15, 0.5], [8.9, 0.1, 0.35]]), t => Math.pow(1 - t, 0.6)), B + 0.05, 0.4);
	mix(out, env(sweep(noiseBuf(2.0), 'bp', t => 5200 - 2400 * t, 3), t => Math.min(1, t * 10) * Math.pow(1 - t, 1.6)), B + 0.1, 0.25);
	save('mech_giga_break', normalise(softClip(out, 2.0), 0.95));
}
// ---- katana_draw: 1.3 s, a sliding scrape into a long bright ring
{
	const len = 1.3, out = buf(len);
	mix(out, env(sweep(noiseBuf(0.28), 'bp', t => 3000 + 4500 * t, 4), t => Math.sin(Math.PI * t) ** 0.6), 0, 0.5);
	mix(out, bell(1.05, 1480, [[1, 0.5, 0.75], [1.502, 0.22, 0.55], [2.43, 0.3, 0.42], [3.91, 0.16, 0.3], [5.7, 0.08, 0.2]], 0.004), 0.24, 0.9);
	mix(out, env(tone(1.05, () => 2960, () => 1), t => Math.pow(1 - t, 3)), 0.24, 0.08);
	save('mech_katana_draw', normalise(softClip(out, 1.1), 0.82));
}
// ---- katana_sheath: 0.6 s, a slide (falling) into a solid click
{
	const len = 0.6, out = buf(len);
	mix(out, env(sweep(noiseBuf(0.38), 'bp', t => 6000 - 3600 * t, 3.5), t => Math.sin(Math.PI * Math.min(1, t * 1.1)) ** 0.7), 0, 0.55);
	mix(out, env(sweep(noiseBuf(0.03), 'bp', () => 2600, 2), t => Math.pow(1 - t, 2)), 0.4, 1.0);
	mix(out, bell(0.2, 2100, [[1, 0.3, 0.05], [2.3, 0.2, 0.04]]), 0.4, 0.7);
	mix(out, env(tone(0.12, () => 160, t => Math.pow(1 - t, 3)), () => 1), 0.4, 0.6);
	save('mech_katana_sheath', normalise(softClip(out, 1.2), 0.8));
}
// ---- katana_swish: 0.42 s, an airy swish (band-passed noise sweeping up then down) with a faint ring
{
	const len = 0.42, out = buf(len);
	const x = sweep(noiseBuf(len), 'bp', t => 600 + 2600 * Math.sin(Math.PI * Math.min(1, t * 1.15)), 1.4);
	mix(out, env(x, t => Math.pow(Math.sin(Math.PI * Math.min(1, t * 1.05)), 2.2)), 0, 1.0);
	mix(out, env(sweep(noiseBuf(len), 'lp', () => 400), t => Math.pow(Math.sin(Math.PI * t), 2)), 0, 0.4);
	mix(out, env(tone(len, t => 1900 + 300 * t, () => 1), t => Math.pow(Math.sin(Math.PI * t), 3)), 0, 0.05);
	save('mech_katana_swish', normalise(out, 0.85));
}
// ---- katana_hit: 0.45 s, a cut connecting: a sharp crack, a meaty thud and a short ringing tick
{
	const len = 0.45, out = buf(len);
	mix(out, env(sweep(noiseBuf(0.03), 'hp', () => 2800), t => 1 - t), 0, 0.9);
	mix(out, env(sweep(noiseBuf(0.2), 'lp', t => 1400 * (1 - t) + 200), t => Math.pow(1 - t, 3)), 0.003, 0.9);
	mix(out, env(tone(0.25, t => 120 * (1.4 - 0.5 * t), t => Math.pow(1 - t, 3)), () => 1), 0, 0.9);
	mix(out, bell(0.4, 1900, [[1, 0.3, 0.12], [2.6, 0.15, 0.08], [4.1, 0.08, 0.05]]), 0.004, 0.6);
	save('mech_katana_hit', normalise(softClip(out, 1.5), 0.9));
}
// ---- katana_iaido: 1.1 s, a sharp ring snapping straight into a rushing crescent (a wide whoosh that tails off)
{
	const len = 1.1, out = buf(len);
	mix(out, env(sweep(noiseBuf(0.08), 'bp', t => 4000 + 4000 * t, 3), t => Math.sin(Math.PI * t)), 0, 0.6);
	mix(out, bell(0.9, 1760, [[1, 0.45, 0.4], [2.43, 0.3, 0.3], [3.91, 0.2, 0.2], [6.2, 0.1, 0.12]], 0.002), 0.06, 0.8);
	const w = sweep(noiseBuf(0.9), 'bp', t => 2200 - 1700 * t, 0.9);
	mix(out, env(w, t => Math.min(1, t * 12) * Math.pow(1 - t, 1.4)), 0.08, 1.0);
	mix(out, env(sweep(noiseBuf(0.9), 'lp', t => 700 - 400 * t), t => Math.min(1, t * 10) * Math.pow(1 - t, 1.6)), 0.08, 0.6);
	save('mech_katana_iaido', normalise(softClip(out, 1.3), 0.9));
}
