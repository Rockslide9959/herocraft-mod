// SUPERSEDED by the v0.17.1 Omnitrix sound rework (docs/OMNITRIX_SOUNDS_0171.md): the alien / watch sounds are now
// OmnitrixSfx recipes built from vanilla samples, and the few synthesised files left come from synth_omnitrix_v2_sounds.js.
// Do NOT run this script: it would recreate deleted, unregistered files (or overwrite the new ones with the old tonal versions).
// v0.17.1 Omnitrix wave A: XLR8's, Diamondhead's, Upgrade's and Ghostfreak's sounds, synthesised the same way as
// synth_omnitrix_sounds.js (filtered noise and sines, 44.1 kHz mono WAV -> OGG Vorbis through an ffmpeg with libvorbis:
// %LOCALAPPDATA%\Medal\recorder-*\ffmpeg7.exe). The helpers are copied so the two scripts never collide.
//   node scratchpad/synth_omnitrix_wave_a_sounds.js "<ffmpeg.exe with libvorbis>"
// Output: src/main/resources/assets/projecthero/sounds/omnitrix/{xlr8,diamondhead,upgrade,ghostfreak}_*.ogg
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');

const SR = 44100;
const FFMPEG = process.argv[2];
const WAV_DIR = path.join(os.tmpdir(), 'omnitrix_wave_a_wav');
const OUT_DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds', 'omnitrix');
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
			v += (wave === 'sq' ? Math.sign(Math.sin(p)) * 0.6 : wave === 'saw' ? (((p / (2 * Math.PI)) % 1) * 2 - 1) : Math.sin(p)) * h;
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
const click = (len, hz, q = 3) => env(sweep(noiseBuf(len), 'bp', () => hz, q), t => Math.pow(1 - t, 4));
function thump(len, hz, drop = 0.6) { return tone(len, t => hz * (1.5 - drop * Math.min(1, t * 3)), t => Math.pow(1 - t, 2.4), [1, 0.3]); }
const whoosh = (len, lo, hi, q = 1.2) => env(sweep(noiseBuf(len), 'bp', t => lo + (hi - lo) * Math.sin(Math.PI * t), q), t => Math.sin(Math.PI * t));

// ---------------------------------------------------------------- XLR8
function xDash() { const o = buf(0.55); mix(o, whoosh(0.5, 500, 4500, 1.4), 0, 1); mix(o, env(sweep(noiseBuf(0.4), 'hp', () => 5000), t => Math.pow(1 - t, 3)), 0.05, 0.4); return normalise(softClip(o, 1.3), 0.85); }
function xHit() { const o = buf(0.18); mix(o, click(0.012, 2400), 0, 1); mix(o, thump(0.12, 160, 0.5), 0, 0.7); mix(o, env(sweep(noiseBuf(0.1), 'lp', () => 2000), t => Math.pow(1 - t, 4)), 0, 0.5); return normalise(softClip(o, 1.6), 0.85); }
function xTornado() {
	const o = buf(1.6);
	mix(o, env(sweep(noiseBuf(1.6), 'bp', t => 400 + 300 * Math.sin(t * 30) + 800 * t, 1.6), t => Math.min(1, t * 4) * Math.pow(1 - t, 0.7)), 0, 1);
	mix(o, env(sweep(noiseBuf(1.6), 'lp', () => 260), t => Math.min(1, t * 3) * (1 - t)), 0, 0.8);
	return normalise(softClip(o, 1.4), 0.85);
}
function xBurst() {
	// a reversed swell then a slowed-down "time stretch" fall
	const o = buf(1.4);
	mix(o, env(sweep(noiseBuf(0.5), 'bp', t => 600 + 5000 * t, 1.5), t => t * t), 0, 0.9);
	mix(o, tone(0.9, t => 900 * Math.pow(0.2, t), t => Math.pow(1 - t, 1.3), [1, 0.5, 0.25]), 0.48, 0.6);
	mix(o, thump(0.6, 70, 0.4), 0.48, 0.9);
	return normalise(softClip(o, 1.5), 0.9);
}
function xVisor() { const o = buf(0.35); mix(o, click(0.01, 3600), 0, 1); mix(o, click(0.01, 2200), 0.06, 0.7); mix(o, tone(0.25, t => 1200 + 1600 * t, t => Math.pow(1 - t, 2), [1, 0.3]), 0.05, 0.35); return normalise(o, 0.75); }

// ---------------------------------------------------------------- Diamondhead
function chime(len, hz) { return tone(len, () => hz, t => Math.min(1, t * 60) * Math.pow(1 - t, 2.2), [1, 0.5, 0.33, 0.2, 0.12]); }
function dShard() { const o = buf(0.5); mix(o, click(0.01, 5200, 4), 0, 0.8); for (const [hz, at] of [[2637, 0], [3520, 0.02], [4186, 0.035]]) mix(o, chime(0.4, hz), at, 0.35); mix(o, whoosh(0.2, 1500, 5000), 0, 0.4); return normalise(o, 0.8); }
function dGrow() {
	const o = buf(1.0);
	for (let k = 0; k < 30; k++) mix(o, chime(0.15, 1800 + rnd() * 3500), rnd() * 0.75, 0.25 * rnd() + 0.05);
	mix(o, env(sweep(noiseBuf(0.9), 'bp', t => 800 + 2400 * t, 2), t => Math.min(1, t * 5) * Math.pow(1 - t, 1.2)), 0, 0.5);
	mix(o, thump(0.4, 90, 0.3), 0, 0.5);
	return normalise(softClip(o, 1.3), 0.85);
}
function dShatter() { const o = buf(0.9); mix(o, click(0.02, 3000), 0, 1); for (let k = 0; k < 40; k++) mix(o, chime(0.12, 2000 + rnd() * 5000), rnd() * 0.5, 0.3 * rnd()); mix(o, env(sweep(noiseBuf(0.6), 'hp', () => 3500), t => Math.pow(1 - t, 2.5)), 0, 0.6); return normalise(softClip(o, 1.4), 0.85); }
function dBlade() { const o = buf(0.8); mix(o, env(sweep(noiseBuf(0.7), 'bp', t => 2000 + 4000 * t, 5), t => Math.min(1, t * 8) * Math.pow(1 - t, 1.5)), 0, 0.7); mix(o, chime(0.7, 1568), 0.05, 0.5); mix(o, chime(0.6, 2349), 0.08, 0.35); return normalise(o, 0.8); }

// ---------------------------------------------------------------- Upgrade
function uBeam() {
	const o = buf(0.6);
	mix(o, tone(0.55, t => 1400 - 600 * t + 80 * Math.sin(t * 200), t => Math.min(1, t * 30) * Math.pow(1 - t, 1.2), [1, 0.5, 0.35, 0.2], 'saw'), 0, 0.5);
	mix(o, env(sweep(noiseBuf(0.55), 'bp', () => 3200, 3), t => Math.pow(1 - t, 1.5)), 0, 0.4);
	return normalise(softClip(o, 1.6), 0.8);
}
function uCharge() { const o = buf(1.1); mix(o, tone(1.05, t => 200 * Math.pow(6, t), t => Math.min(1, t * 3) * (0.5 + 0.5 * t), [1, 0.4, 0.2], 'saw'), 0, 0.45); mix(o, env(sweep(noiseBuf(1.05), 'bp', t => 500 + 4000 * t, 4), t => t), 0, 0.3); return normalise(softClip(o, 1.3), 0.75); }
function uMerge() {
	const o = buf(0.9);
	// a liquid glorp and a rising digital chirp
	mix(o, env(sweep(noiseBuf(0.5), 'bp', t => 300 + 500 * Math.sin(Math.PI * t), 6), t => Math.sin(Math.PI * t)), 0, 0.9);
	mix(o, tone(0.4, t => 160 + 140 * Math.sin(t * 18), t => Math.sin(Math.PI * t), [1, 0.5]), 0, 0.6);
	for (let k = 0; k < 6; k++) mix(o, tone(0.05, () => 1200 + k * 260, t => Math.pow(1 - t, 2), [1], 'sq'), 0.35 + k * 0.06, 0.25);
	return normalise(softClip(o, 1.3), 0.8);
}
function uEmp() {
	const o = buf(1.2);
	mix(o, thump(0.7, 60, 0.4), 0, 1.1);
	const crackle = buf(1.1);
	for (let k = 0; k < 70; k++) mix(crackle, click(0.004, 1500 + rnd() * 6000, 2), rnd() * 1.05, rnd());
	mix(o, crackle, 0, 0.7);
	mix(o, tone(0.9, t => 120 * (1 - 0.6 * t), t => Math.pow(1 - t, 1.5), [1, 0.6, 0.4, 0.3], 'sq'), 0, 0.35);
	return normalise(softClip(o, 1.8), 0.9);
}
function uSlide() { const o = buf(0.7); mix(o, env(sweep(noiseBuf(0.65), 'bp', t => 400 + 900 * Math.sin(t * 14) * 0.5 + 600, 5), t => Math.sin(Math.PI * t)), 0, 1); mix(o, tone(0.6, t => 110 + 60 * Math.sin(t * 25), t => Math.sin(Math.PI * t), [1, 0.4]), 0, 0.4); return normalise(o, 0.75); }

// ---------------------------------------------------------------- Ghostfreak
function gClaw() { const o = buf(0.45); mix(o, whoosh(0.35, 800, 3500, 2), 0, 0.9); mix(o, env(sweep(noiseBuf(0.3), 'hp', () => 4500), t => Math.pow(1 - t, 3)), 0.12, 0.5); mix(o, tone(0.3, t => 300 * (1 - 0.4 * t), t => Math.pow(1 - t, 2), [1, 0.6, 0.3]), 0.1, 0.3); return normalise(softClip(o, 1.3), 0.85); }
function gScream() {
	const len = 1.4;
	const o = buf(len);
	const voice = tone(len, t => 700 + 500 * Math.sin(Math.PI * t) + 40 * Math.sin(t * 120), t => Math.min(1, t * 6) * Math.pow(1 - t, 0.8), [1, 0.7, 0.5, 0.35, 0.25], 'saw');
	mix(o, sweep(voice, 'bp', t => 1500 + 800 * Math.sin(t * 9), 1.5), 0, 0.8);
	mix(o, env(sweep(noiseBuf(len), 'bp', () => 2500, 1), t => Math.min(1, t * 6) * Math.pow(1 - t, 1)), 0, 0.5);
	return normalise(softClip(o, 1.8), 0.85);
}
function gPhase() { const o = buf(0.9); mix(o, env(sweep(noiseBuf(0.85), 'bp', t => 1800 - 1300 * t, 3), t => Math.sin(Math.PI * t)), 0, 0.8); mix(o, tone(0.8, t => 440 * (1 + 0.05 * Math.sin(t * 40)), t => Math.sin(Math.PI * t) * 0.8, [1, 0, 0.3]), 0, 0.35); mix(o, tone(0.8, t => 466 * (1 + 0.05 * Math.sin(t * 37)), t => Math.sin(Math.PI * t) * 0.8, [1, 0, 0.3]), 0, 0.3); return normalise(o, 0.8); }
function gLaugh() {
	const o = buf(1.5);
	for (let k = 0; k < 5; k++) {
		const hz = 180 - k * 12;
		const ha = tone(0.2, t => hz * (1 + 0.15 * Math.sin(Math.PI * t)), t => Math.sin(Math.PI * t), [1, 0.8, 0.6, 0.4, 0.3, 0.2], 'saw');
		mix(o, sweep(ha, 'bp', () => 900, 1.2), k * 0.24, 0.8);
		mix(o, env(sweep(noiseBuf(0.2), 'bp', () => 1800, 1.5), t => Math.sin(Math.PI * t)), k * 0.24, 0.25);
	}
	// a ghostly reverb tail
	mix(o, env(sweep(noiseBuf(1.5), 'lp', () => 700), t => Math.min(1, t * 2) * Math.pow(1 - t, 2)), 0, 0.25);
	return normalise(softClip(o, 1.4), 0.8);
}
function gTendril() { const o = buf(0.9); mix(o, env(sweep(noiseBuf(0.85), 'lp', t => 300 + 500 * Math.sin(Math.PI * t)), t => Math.sin(Math.PI * t)), 0, 1); mix(o, tone(0.8, t => 70 + 30 * Math.sin(t * 20), t => Math.sin(Math.PI * t), [1, 0.5, 0.3]), 0, 0.6); return normalise(softClip(o, 1.5), 0.8); }

const ALL = {
	xlr8_dash: xDash, xlr8_hit: xHit, xlr8_tornado: xTornado, xlr8_burst: xBurst, xlr8_visor: xVisor,
	diamondhead_shard: dShard, diamondhead_grow: dGrow, diamondhead_shatter: dShatter, diamondhead_blade: dBlade,
	upgrade_beam: uBeam, upgrade_charge: uCharge, upgrade_merge: uMerge, upgrade_emp: uEmp, upgrade_slide: uSlide,
	ghostfreak_claw: gClaw, ghostfreak_scream: gScream, ghostfreak_phase: gPhase, ghostfreak_laugh: gLaugh, ghostfreak_tendril: gTendril,
};
for (const [name, fn] of Object.entries(ALL)) save(name, fn());
