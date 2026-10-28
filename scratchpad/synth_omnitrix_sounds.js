// SUPERSEDED by the v0.17.1 Omnitrix sound rework (docs/OMNITRIX_SOUNDS_0171.md): the alien / watch sounds are now
// OmnitrixSfx recipes built from vanilla samples, and the few synthesised files left come from synth_omnitrix_v2_sounds.js.
// Do NOT run this script: it would recreate deleted, unregistered files (or overwrite the new ones with the old tonal versions).
// v0.17.1 Omnitrix: synthesises the watch's sounds (slap, core up / down, dial click, select, transform swoosh, revert,
// timeout chime, warning beep, recharged ding, extraction pulse, self-destruct alarm, extraction done, pod hiss, meteor) and
// Heatblast's / Four Arms' (blast, stream, nova, jet, shield, ignite; punch, clap, slam, throw, roar) as 44.1 kHz mono WAVs,
// then OGG Vorbis with an ffmpeg that has libvorbis (Minecraft needs mono for positional sound; ffmpeg's built-in "vorbis"
// encoder is stereo-only -- %LOCALAPPDATA%\Medal\recorder-*\ffmpeg7.exe has libvorbis). Same pipeline as
// synth_quirk_framework_sounds.js: no samples, filtered noise and sines.
//   node scratchpad/synth_omnitrix_sounds.js "<ffmpeg.exe with libvorbis>"
// Output: src/main/resources/assets/projecthero/sounds/omnitrix/*.ogg
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');

const SR = 44100;
const FFMPEG = process.argv[2];
const WAV_DIR = path.join(os.tmpdir(), 'omnitrix_wav');
const OUT_DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds', 'omnitrix');
fs.mkdirSync(WAV_DIR, { recursive: true });
fs.mkdirSync(OUT_DIR, { recursive: true });

let seed = 1010;
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

// ---------------------------------------------------------------- the watch
function slap() { const o = buf(0.45); mix(o, click(0.02, 3500), 0, 1); mix(o, thump(0.25, 140), 0, 0.9); mix(o, tone(0.4, t => 880 + 900 * t, t => Math.min(1, t * 20) * Math.pow(1 - t, 2), [1, 0.4]), 0.03, 0.35); return normalise(softClip(o, 1.5), 0.9); }
function coreUp() {
	const o = buf(0.6);
	mix(o, click(0.015, 2600), 0, 0.8);
	mix(o, env(sweep(noiseBuf(0.25), 'bp', t => 1200 + 3000 * t, 2), t => Math.sin(Math.PI * t)), 0.01, 0.5); // the hiss of it rising
	mix(o, tone(0.5, t => 330 * Math.pow(2, t * 1.2), t => Math.min(1, t * 12) * Math.pow(1 - t, 1.5), [1, 0.5, 0.25]), 0.04, 0.55);
	mix(o, click(0.012, 4200), 0.2, 0.7); // the lock click at the top
	return normalise(softClip(o, 1.3), 0.85);
}
function coreDown() {
	const o = buf(0.45);
	mix(o, tone(0.35, t => 700 * Math.pow(0.5, t * 1.2), t => Math.min(1, t * 12) * Math.pow(1 - t, 1.6), [1, 0.4]), 0, 0.5);
	mix(o, click(0.015, 2200), 0.22, 0.9);
	mix(o, thump(0.15, 120), 0.22, 0.4);
	return normalise(o, 0.8);
}
function dial() { const o = buf(0.12); mix(o, click(0.012, 3000), 0, 1); mix(o, click(0.01, 5200), 0.03, 0.5); mix(o, tone(0.08, () => 1500, t => Math.pow(1 - t, 6)), 0, 0.2); return normalise(o, 0.7); }
function select() { const o = buf(0.35); mix(o, tone(0.12, () => 990, t => Math.pow(1 - t, 2), [1, 0.3]), 0, 0.6); mix(o, tone(0.2, () => 1480, t => Math.pow(1 - t, 2), [1, 0.3]), 0.08, 0.6); return normalise(o, 0.7); }
function transform() {
	const len = 1.4;
	const o = buf(len);
	// the swoosh climbing up the body, a bright chord blooming, a shimmer
	mix(o, env(sweep(noiseBuf(len), 'bp', t => 300 + 5000 * Math.pow(t, 1.3), 2.5), t => Math.sin(Math.PI * Math.min(1, t * 1.3))), 0, 0.7);
	for (const [hz, a] of [[220, 0.5], [277, 0.35], [330, 0.35], [440, 0.25], [554, 0.15]]) {
		mix(o, tone(1.0, t => hz * (1 + 0.5 * Math.min(1, t * 2)), t => Math.min(1, t * 4) * Math.pow(1 - t, 1.4), [1, 0.3]), 0.25, a);
	}
	mix(o, env(sweep(noiseBuf(0.8), 'hp', () => 6000), t => Math.pow(1 - t, 2)), 0.45, 0.35);
	return normalise(softClip(o, 1.4), 0.9);
}
function revert() {
	const len = 1.0;
	const o = buf(len);
	mix(o, env(sweep(noiseBuf(len), 'bp', t => 5000 - 4500 * t, 2.5), t => Math.sin(Math.PI * t)), 0, 0.6);
	mix(o, tone(0.8, t => 660 * Math.pow(0.45, t), t => Math.min(1, t * 6) * Math.pow(1 - t, 1.5), [1, 0.4, 0.2]), 0.1, 0.45);
	return normalise(o, 0.85);
}
function timeout() {
	// the classic beep-beep-beep then a falling "power down" and the revert swoosh
	const o = buf(1.6);
	for (let k = 0; k < 3; k++) mix(o, tone(0.1, () => 1760, t => Math.pow(1 - t, 0.6), [1, 0.2], 'sq'), k * 0.16, 0.4);
	mix(o, tone(0.7, t => 880 * Math.pow(0.25, t), t => Math.pow(1 - t, 1.2), [1, 0.3], 'sq'), 0.5, 0.35);
	mix(o, revert(), 0.55, 0.6);
	return normalise(softClip(o, 1.2), 0.85);
}
function warning() { const o = buf(0.2); mix(o, tone(0.12, () => 1320, t => Math.pow(1 - t, 0.5), [1, 0.15], 'sq'), 0, 0.5); return normalise(o, 0.6); }
function recharged() { const o = buf(0.9); for (const [hz, at] of [[784, 0], [1047, 0.1], [1568, 0.2]]) mix(o, tone(0.6, () => hz, t => Math.pow(1 - t, 2), [1, 0.3, 0.1]), at, 0.5); return normalise(o, 0.75); }
function extractPulse() {
	const o = buf(0.5);
	mix(o, env(sweep(noiseBuf(0.4), 'bp', t => 2400 - 1800 * t, 4), t => Math.pow(1 - t, 2)), 0, 0.8);
	mix(o, tone(0.35, t => 90 + 40 * Math.sin(t * 60), t => Math.pow(1 - t, 2), [1, 0.6, 0.4, 0.3], 'sq'), 0, 0.5);
	mix(o, click(0.02, 1800), 0, 0.9);
	return normalise(softClip(o, 1.8), 0.9);
}
function selfDestruct() { const o = buf(1.0); for (let k = 0; k < 2; k++) mix(o, tone(0.4, t => 700 + 500 * Math.sin(Math.PI * t), t => Math.min(1, t * 10) * (1 - t * 0.4), [1, 0.3, 0.2], 'sq'), k * 0.48, 0.45); return normalise(softClip(o, 1.3), 0.75); }
function extractDone() {
	const o = buf(1.4);
	mix(o, thump(0.5, 70, 0.4), 0, 1);
	mix(o, click(0.03, 2000), 0, 0.8);
	mix(o, env(sweep(noiseBuf(1.2), 'lp', t => 3000 * (1 - t) + 200), t => Math.pow(1 - t, 2)), 0, 0.6); // the field collapsing
	mix(o, recharged(), 0.35, 0.4);
	return normalise(softClip(o, 1.5), 0.9);
}
function podOpen() {
	const o = buf(1.6);
	mix(o, click(0.03, 1400), 0, 0.9);
	mix(o, env(sweep(noiseBuf(1.4), 'hp', t => 2500 - 1200 * t), t => Math.min(1, t * 20) * Math.pow(1 - t, 1.2)), 0.02, 0.8); // the pressure hiss
	mix(o, tone(1.0, t => 220 * (1 + 0.3 * t), t => Math.min(1, t * 3) * Math.pow(1 - t, 2), [1, 0.4]), 0.3, 0.3);
	return normalise(o, 0.85);
}
function meteor() {
	const len = 3.0;
	const o = buf(len);
	mix(o, env(sweep(noiseBuf(len), 'bp', t => 3000 - 2500 * t, 3), t => Math.min(1, t * 1.5) * (0.4 + 0.6 * t)), 0, 0.8); // the falling whistle
	mix(o, env(sweep(noiseBuf(len), 'lp', () => 300), t => t * t), 0, 0.9); // the roar growing
	return normalise(softClip(o, 1.3), 0.9);
}

// ---------------------------------------------------------------- Heatblast
function fireRoar(len, lo, hi) { return env(sweep(noiseBuf(len), 'lp', t => lo + (hi - lo) * Math.sin(Math.PI * Math.min(1, t * 1.6))), t => Math.min(1, t * 15) * Math.pow(1 - t, 1.3)); }
function hbBlast() { const o = buf(0.6); mix(o, fireRoar(0.55, 600, 3500), 0, 1); mix(o, thump(0.2, 110), 0, 0.5); return normalise(softClip(o, 1.6), 0.9); }
function hbStream() {
	const o = buf(1.2);
	const crackle = buf(1.2);
	for (let k = 0; k < 40; k++) mix(crackle, click(0.006, 2500 + rnd() * 3000), rnd() * 1.15, 0.5 * rnd());
	mix(o, env(sweep(noiseBuf(1.2), 'lp', () => 1800), t => Math.min(1, t * 8) * (t > 0.85 ? (1 - t) / 0.15 : 1)), 0, 0.9);
	mix(o, crackle, 0, 0.6);
	return normalise(o, 0.85);
}
function hbNova() { const o = buf(1.6); mix(o, thump(0.9, 55, 0.5), 0, 1.2); mix(o, fireRoar(1.5, 300, 4000), 0, 1); mix(o, click(0.03, 1500), 0, 0.8); return normalise(softClip(o, 1.8), 0.95); }
function hbJet() { const o = buf(0.9); mix(o, env(sweep(noiseBuf(0.9), 'bp', t => 900 + 600 * Math.sin(t * 20), 0.8), t => Math.min(1, t * 10) * Math.pow(1 - t, 0.8)), 0, 1); return normalise(o, 0.8); }
function hbShield() { const o = buf(1.0); mix(o, fireRoar(0.95, 400, 2500), 0, 0.9); mix(o, tone(0.9, t => 160 + 60 * t, t => Math.min(1, t * 5) * (1 - t), [1, 0.5, 0.3]), 0, 0.4); return normalise(softClip(o, 1.4), 0.85); }
function hbIgnite() { const o = buf(1.0); mix(o, click(0.02, 3000), 0, 0.6); mix(o, fireRoar(0.95, 200, 5000), 0.02, 1); mix(o, thump(0.4, 80), 0.02, 0.6); return normalise(softClip(o, 1.6), 0.9); }

// ---------------------------------------------------------------- Four Arms
function faPunch() { const o = buf(0.4); mix(o, click(0.015, 2000), 0, 0.9); mix(o, env(sweep(noiseBuf(0.2), 'lp', t => 2200 * (1 - 0.8 * t)), t => Math.pow(1 - t, 3)), 0.002, 0.9); mix(o, thump(0.3, 75), 0, 1.2); return normalise(softClip(o, 1.6), 0.92); }
function faClap() {
	const o = buf(1.3);
	mix(o, click(0.02, 1600), 0, 1.0);
	mix(o, env(sweep(noiseBuf(0.15), 'bp', () => 1200, 1), t => Math.pow(1 - t, 3)), 0, 1.0);
	mix(o, thump(0.8, 50, 0.4), 0, 1.3);
	mix(o, env(sweep(noiseBuf(1.2), 'lp', t => 500 - 300 * t), t => Math.min(1, t * 6) * Math.pow(1 - t, 1.6)), 0.05, 0.8); // the rolling boom
	return normalise(softClip(o, 1.8), 0.95);
}
function faSlam() {
	const o = buf(1.4);
	mix(o, thump(1.0, 48, 0.45), 0, 1.4);
	mix(o, click(0.03, 900), 0, 0.9);
	const rubble = buf(1.3);
	for (let k = 0; k < 50; k++) mix(rubble, click(0.01, 500 + rnd() * 2500), 0.05 + rnd() * 1.1, 0.4 * rnd());
	mix(o, rubble, 0, 0.8);
	return normalise(softClip(o, 1.8), 0.95);
}
function faThrow() { const o = buf(0.6); mix(o, env(sweep(noiseBuf(0.6), 'bp', t => 400 + 1600 * Math.sin(Math.PI * t), 1.2), t => Math.sin(Math.PI * t)), 0, 1); mix(o, tone(0.3, () => 90, t => Math.pow(1 - t, 2)), 0, 0.4); return normalise(o, 0.85); }
function faRoar() {
	const len = 1.3;
	const o = buf(len);
	const growl = tone(len, t => 85 + 25 * Math.sin(Math.PI * t) + 6 * Math.sin(t * 90), t => Math.min(1, t * 6) * Math.pow(1 - t, 0.9), [1, 0.8, 0.6, 0.45, 0.3, 0.2], 'sq');
	mix(o, sweep(growl, 'lp', () => 1400), 0, 0.8);
	mix(o, env(sweep(noiseBuf(len), 'bp', () => 700, 1.2), t => Math.min(1, t * 6) * Math.pow(1 - t, 1.0)), 0, 0.6);
	return normalise(softClip(o, 2.0), 0.9);
}

const ALL = {
	slap, core_up: coreUp, core_down: coreDown, dial, select, transform, revert, timeout, warning, recharged, extract_pulse: extractPulse,
	self_destruct: selfDestruct, extract_done: extractDone, pod_open: podOpen, meteor,
	heatblast_blast: hbBlast, heatblast_stream: hbStream, heatblast_nova: hbNova, heatblast_jet: hbJet, heatblast_shield: hbShield,
	heatblast_ignite: hbIgnite, four_arms_punch: faPunch, four_arms_clap: faClap, four_arms_slam: faSlam, four_arms_throw: faThrow,
	four_arms_roar: faRoar,
};
for (const [name, fn] of Object.entries(ALL)) save(name, fn());
