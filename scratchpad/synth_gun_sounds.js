// v0.15.16: synthesises every Punisher firearm sound from scratch (no samples) -- gunshots per gun (3 variants each),
// distant versions, dry fire, magazine out / in, charging handle, pump, bolt, shell insert, impacts,
// ricochet, flesh hit -- as 44.1 kHz mono WAVs, then encodes them to OGG Vorbis with ffmpeg.
//   node scratchpad/synth_gun_sounds.js "<path to ffmpeg.exe>"
// Output: src/main/resources/assets/projecthero/sounds/gun/<name>.ogg   (WAVs go to scratchpad/gun_wav/)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const SR = 44100;
const FFMPEG = process.argv[2];
const WAV_DIR = path.join(__dirname, 'gun_wav');
const OUT_DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds', 'gun');
fs.mkdirSync(WAV_DIR, { recursive: true });
fs.mkdirSync(OUT_DIR, { recursive: true });

// ---------------------------------------------------------------- tiny DSP kit
let seed = 1;
function rnd() { // deterministic, so re-running gives the same files
	seed = (seed * 1664525 + 1013904223) >>> 0;
	return seed / 4294967296;
}
const noise = () => rnd() * 2 - 1;
const buf = sec => new Float32Array(Math.ceil(sec * SR));

function biquad(x, type, f, q = 0.707) {
	const w = 2 * Math.PI * f / SR, c = Math.cos(w), s = Math.sin(w), a = s / (2 * q);
	let b0, b1, b2, a0, a1, a2;
	if (type === 'lp') { b0 = (1 - c) / 2; b1 = 1 - c; b2 = (1 - c) / 2; }
	else if (type === 'hp') { b0 = (1 + c) / 2; b1 = -(1 + c); b2 = (1 + c) / 2; }
	else { b0 = a; b1 = 0; b2 = -a; } // band-pass (constant peak)
	a0 = 1 + a; a1 = -2 * c; a2 = 1 - a;
	const y = new Float32Array(x.length);
	let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
	for (let i = 0; i < x.length; i++) {
		const v = (b0 * x[i] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0;
		x2 = x1; x1 = x[i]; y2 = y1; y1 = v; y[i] = v;
	}
	return y;
}
function mix(dst, src, at = 0, gain = 1) {
	const o = Math.round(at * SR);
	for (let i = 0; i < src.length && i + o < dst.length; i++) if (i + o >= 0) dst[i + o] += src[i] * gain;
	return dst;
}
/** filtered noise burst: attack a, exponential decay tau, length len */
function burst(len, tau, filt, a = 0.0005) {
	let x = buf(len);
	for (let i = 0; i < x.length; i++) {
		const t = i / SR;
		x[i] = noise() * Math.min(1, t / a) * Math.exp(-t / tau);
	}
	for (const [type, f, q] of filt) x = biquad(x, type, f, q);
	return x;
}
/** pitched thump: sine sweeping f0 -> f1, exponential decay */
function thump(len, f0, f1, tau, drive = 2) {
	const x = buf(len);
	let ph = 0;
	for (let i = 0; i < x.length; i++) {
		const t = i / SR;
		const f = f1 + (f0 - f1) * Math.exp(-t / (len * 0.25));
		ph += 2 * Math.PI * f / SR;
		x[i] = Math.tanh(Math.sin(ph) * drive) * Math.exp(-t / tau) * Math.min(1, t / 0.0008);
	}
	return x;
}
/** inharmonic metal ping */
function ping(len, freqs, tau) {
	const x = buf(len);
	for (let i = 0; i < x.length; i++) {
		const t = i / SR;
		let v = 0;
		freqs.forEach((f, k) => { v += Math.sin(2 * Math.PI * f * t) * Math.exp(-t / (tau * (1 - k * 0.2))) / (k + 1); });
		x[i] = v * Math.min(1, t / 0.0003);
	}
	return x;
}
/** a short metallic click */
function click(gain = 1, f = 3500, tau = 0.004) {
	return mix(burst(0.03, tau, [['bp', f, 1.4]]), ping(0.03, [f * 0.9, f * 1.37], tau * 1.5), 0, 0.25).map(v => v * gain);
}
/** a slide / scrape: noise through a band sweeping f0 -> f1 */
function slide(len, f0, f1, gain = 0.5) {
	const x = buf(len);
	const n = burst(len, 10, []);
	let y1 = 0, y2 = 0, x1 = 0, x2 = 0;
	for (let i = 0; i < x.length; i++) {
		const t = i / x.length;
		const f = f0 + (f1 - f0) * t;
		const w = 2 * Math.PI * f / SR, c = Math.cos(w), s = Math.sin(w), a = s / 4;
		const v = (a * n[i] - a * x2 - (-2 * c) * y1 - (1 - a) * y2) / (1 + a);
		x2 = x1; x1 = n[i]; y2 = y1; y1 = v;
		x[i] = v * Math.sin(Math.PI * t) * gain * 3;
	}
	return x;
}
/** early reflections + a diffuse tail, for an outdoor-ish space */
function space(x, tail, wet) {
	const out = buf(x.length / SR + tail);
	mix(out, x);
	[[0.019, 0.35], [0.031, 0.28], [0.047, 0.22], [0.071, 0.16], [0.103, 0.11]].forEach(([d, g]) => mix(out, biquad(x, 'lp', 3500), d, g * wet));
	const diffuse = burst(tail, tail / 4.5, [['lp', 1400], ['hp', 120]]);
	let env = 0;
	for (let i = 0; i < x.length; i++) env = Math.max(env, Math.abs(x[i]));
	mix(out, diffuse, 0.012, wet * 0.5 * env);
	return out;
}
function master(x, peak = 0.89) {
	let m = 0;
	for (let i = 0; i < x.length; i++) { x[i] = Math.tanh(x[i] * 1.2); m = Math.max(m, Math.abs(x[i])); }
	const g = peak / Math.max(1e-6, m);
	// trim trailing silence, short fade
	let end = x.length;
	while (end > SR * 0.05 && Math.abs(x[end - 1] * g) < 0.0008) end--;
	const y = x.slice(0, end);
	for (let i = 0; i < y.length; i++) y[i] *= g * Math.min(1, (y.length - i) / (SR * 0.01));
	return y;
}

// ---------------------------------------------------------------- the sounds
/** One gunshot. p: crack (gain, tau), body (thump f0, f1, tau), blast (lp, tau), tail, mech (cycle click delay) */
function gunshot(p, v) {
	seed = 1000 + p.seed * 17 + v * 101;
	const j = 1 + (rnd() - 0.5) * 0.08; // variant pitch jitter
	const len = 0.5 + p.tail;
	let x = buf(len);
	mix(x, burst(0.04, p.crackTau, [['hp', 1800], ['hp', 1200]]), 0, p.crack);               // supersonic crack
	mix(x, thump(0.35, p.f0 * j, p.f1 * j, p.bodyTau, p.drive), 0.0006, p.body);               // the pressure punch
	mix(x, burst(0.4, p.blastTau, [['lp', p.blastLp * j], ['hp', 60]]), 0, p.blast);           // muzzle blast
	mix(x, burst(0.25, 0.05, [['bp', 650 * j, 0.8]]), 0.002, p.blast * 0.35);                 // mid "bark"
	if (p.mech) {
		mix(x, click(0.12, 4200 * j, 0.003), p.mech);                                          // the action cycling
		mix(x, click(0.08, 3100 * j, 0.004), p.mech + 0.022);
	}
	return master(space(x, p.tail, p.wet));
}
function distant(p, v) {
	seed = 5000 + p.seed * 13 + v * 71;
	let x = buf(0.6);
	mix(x, burst(0.05, 0.004, [['hp', 900], ['lp', 3000]]), 0, p.crack * 0.4);
	mix(x, thump(0.4, p.f0 * 0.8, p.f1 * 0.8, p.bodyTau * 1.6, 1.4), 0.002, p.body);
	mix(x, burst(0.5, p.blastTau * 2.5, [['lp', 500], ['hp', 50]]), 0, p.blast);
	x = biquad(biquad(x, 'lp', 1100), 'lp', 1300);
	let out = space(x, 1.4 + p.tail, 1.3);
	// slap-back echoes off far terrain
	const e = biquad(x, 'lp', 700);
	[[0.28, 0.45], [0.61, 0.3], [1.05, 0.18]].forEach(([d, g]) => mix(out, e, d, g));
	return master(out, 0.8);
}

const GUNS = {
	pistol: { seed: 1, crack: 0.9, crackTau: 0.0035, f0: 260, f1: 75, bodyTau: 0.045, drive: 2.2, body: 0.9, blastLp: 2400, blastTau: 0.022, blast: 0.75, tail: 0.55, wet: 0.8, mech: 0.045 },
	rifle: { seed: 2, crack: 1.0, crackTau: 0.003, f0: 220, f1: 60, bodyTau: 0.06, drive: 2.6, body: 1.0, blastLp: 2000, blastTau: 0.03, blast: 0.9, tail: 0.75, wet: 0.9, mech: 0.05 },
	shotgun: { seed: 3, crack: 0.6, crackTau: 0.006, f0: 150, f1: 38, bodyTau: 0.11, drive: 3.2, body: 1.25, blastLp: 1300, blastTau: 0.06, blast: 1.2, tail: 1.0, wet: 1.0, mech: 0 },
	sniper: { seed: 4, crack: 1.25, crackTau: 0.0028, f0: 170, f1: 34, bodyTau: 0.13, drive: 3.4, body: 1.35, blastLp: 1700, blastTau: 0.05, blast: 1.1, tail: 1.5, wet: 1.2, mech: 0 },
};

const OUT = {};
for (const [g, p] of Object.entries(GUNS)) {
	for (let v = 1; v <= 3; v++) OUT[`${g}_fire${v}`] = gunshot(p, v);
	for (let v = 1; v <= 2; v++) OUT[`${g}_far${v}`] = distant(p, v);
}

// (v0.15.18: the brass-casing "tring" + shotgun-hull tock landing sounds were removed -- user: "remove that tring
// sound effect when shooting". Every later section reseeds, so the other sounds come out unchanged.)

seed = 42;
OUT.dry = master(mix(click(1, 3600, 0.003), click(0.5, 2600, 0.004), 0.012), 0.7);
OUT.mag_out = master(mix(mix(mix(buf(0.4), click(1, 3200), 0), slide(0.12, 1600, 2600, 0.6), 0.02), thump(0.06, 300, 180, 0.01, 1), 0.15, 0.5), 0.75);
OUT.mag_in = master(mix(mix(mix(buf(0.3), slide(0.05, 2200, 1500, 0.4), 0), click(1, 2900, 0.005), 0.05), thump(0.08, 240, 120, 0.015, 1.6), 0.05, 0.9), 0.85);
OUT.rack = master(mix(mix(mix(mix(buf(0.45), click(0.8, 3800), 0), slide(0.09, 1800, 3000, 0.7), 0.01), click(0.9, 3300), 0.11), mix(click(1.1, 2800, 0.006), thump(0.06, 260, 140, 0.012), 0, 0.6), 0.2), 0.85);
OUT.pump = master(mix(mix(mix(mix(buf(0.5), slide(0.1, 900, 1700, 0.9), 0), click(1, 2500, 0.006), 0.1), slide(0.09, 1700, 1000, 0.8), 0.17), mix(click(1.2, 2300, 0.007), thump(0.07, 220, 110, 0.015), 0, 0.7), 0.27), 0.9);
OUT.bolt = master(mix(mix(mix(mix(mix(buf(0.75), click(0.9, 3400), 0), slide(0.14, 1500, 2700, 0.7), 0.06), click(0.7, 3000), 0.22), slide(0.12, 2600, 1500, 0.7), 0.34), mix(click(1.1, 2700, 0.006), thump(0.06, 240, 130, 0.012), 0, 0.6), 0.48), 0.85);
OUT.shell_in = master(mix(mix(buf(0.25), slide(0.05, 1400, 900, 0.5), 0), mix(click(0.8, 2400, 0.005), thump(0.05, 420, 260, 0.01), 0, 0.5), 0.045), 0.7);

// impacts
for (let v = 1; v <= 3; v++) {
	seed = 7000 + v * 13;
	const x = buf(0.3);
	mix(x, burst(0.02, 0.002, [['hp', 2500]]), 0, 0.8);
	mix(x, burst(0.2, 0.03, [['lp', 1500 + rnd() * 500], ['hp', 90]]), 0.001, 1);
	mix(x, thump(0.1, 180, 80, 0.02, 1.4), 0, 0.6);
	OUT[`impact${v}`] = master(x, 0.7);
}
for (let v = 1; v <= 2; v++) {
	seed = 7500 + v * 19;
	const x = buf(0.55);
	let ph = 0;
	const f0 = 4200 + rnd() * 900, f1 = 1300 + rnd() * 300;
	for (let i = 0; i < x.length; i++) {
		const t = i / SR;
		const f = f1 + (f0 - f1) * Math.exp(-t / 0.12) + Math.sin(t * 2 * Math.PI * 23) * 60;
		ph += 2 * Math.PI * f / SR;
		x[i] = Math.sin(ph) * Math.exp(-t / 0.13) * Math.min(1, t / 0.004) * 0.6;
	}
	mix(x, burst(0.03, 0.003, [['hp', 2000]]), 0, 0.7);
	OUT[`ricochet${v}`] = master(x, 0.6);
}
for (let v = 1; v <= 2; v++) {
	seed = 7800 + v * 23;
	const x = buf(0.25);
	mix(x, thump(0.12, 140, 60, 0.03, 1.8), 0, 1);
	mix(x, burst(0.15, 0.025, [['lp', 900], ['hp', 120]]), 0.003, 0.9);
	OUT[`flesh${v}`] = master(x, 0.75);
}

// ---------------------------------------------------------------- write + encode
function wav(file, x) {
	const b = Buffer.alloc(44 + x.length * 2);
	b.write('RIFF', 0); b.writeUInt32LE(36 + x.length * 2, 4); b.write('WAVE', 8); b.write('fmt ', 12);
	b.writeUInt32LE(16, 16); b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22); b.writeUInt32LE(SR, 24); b.writeUInt32LE(SR * 2, 28);
	b.writeUInt16LE(2, 32); b.writeUInt16LE(16, 34); b.write('data', 36); b.writeUInt32LE(x.length * 2, 40);
	for (let i = 0; i < x.length; i++) b.writeInt16LE(Math.max(-32767, Math.min(32767, Math.round(x[i] * 32767))), 44 + i * 2);
	fs.writeFileSync(file, b);
}
for (const [name, x] of Object.entries(OUT)) {
	const w = path.join(WAV_DIR, name + '.wav');
	wav(w, x);
	if (FFMPEG) {
		execFileSync(FFMPEG, ['-y', '-loglevel', 'error', '-i', w, '-c:a', 'libvorbis', '-q:a', '6', path.join(OUT_DIR, name + '.ogg')]);
	}
}
console.log(Object.keys(OUT).length + ' sounds: ' + Object.keys(OUT).join(' '));
