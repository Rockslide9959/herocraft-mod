// v0.17.1: onset analysis of the user's Omnitrix sounds.
// Usage: node analyse_user_omnitrix_audio.js <file.pcm (s16le mono 44100)> [--profile] [--beeps N]
// Prints a 10 ms RMS envelope (dBFS) profile, the peak, leading silence, and onset times found by a
// rising-edge detector on the envelope (onset = first frame that rises > RISE dB above the running minimum
// of the previous 80 ms and crosses the absolute threshold). With --beeps N it also reports the first N
// onsets as beeps and the next big onset as the power-down, plus the loudest 50 ms window after it.
const fs = require('fs');
const file = process.argv[2];
const profile = process.argv.includes('--profile');
const bi = process.argv.indexOf('--beeps');
const nBeeps = bi > 0 ? parseInt(process.argv[bi + 1], 10) : 0;
const buf = fs.readFileSync(file);
const n = buf.length >> 1;
const sr = 44100;
const x = new Float32Array(n);
let peak = 0;
for (let i = 0; i < n; i++) {
	x[i] = buf.readInt16LE(i * 2) / 32768;
	peak = Math.max(peak, Math.abs(x[i]));
}
const hop = Math.round(sr * 0.01);
const frames = Math.floor(n / hop);
const env = new Float32Array(frames);
for (let f = 0; f < frames; f++) {
	let s = 0;
	for (let i = f * hop; i < (f + 1) * hop; i++) s += x[i] * x[i];
	env[f] = 10 * Math.log10(s / hop + 1e-12);
}
const db = (v) => (20 * Math.log10(v + 1e-12)).toFixed(1);
console.log(`${file}: ${(n / sr).toFixed(3)} s, peak ${db(peak)} dBFS`);
// leading silence: first sample above -45 dBFS
let lead = 0;
while (lead < n && Math.abs(x[lead]) < 0.0056) lead++;
console.log(`leading silence ${(lead / sr * 1000).toFixed(1)} ms`);
if (profile) {
	for (let f = 0; f < frames; f += 2) {
		const v = Math.max(env[f], -80);
		console.log(`${(f * 0.01).toFixed(2)}s ${v.toFixed(1).padStart(6)} ${'#'.repeat(Math.max(0, Math.round((v + 70) / 1.5)))}`);
	}
}
const RISE = 9, ABS = -38;
const onsets = [];
let last = -100;
for (let f = 8; f < frames; f++) {
	let mn = Infinity;
	for (let k = f - 8; k < f; k++) mn = Math.min(mn, env[k]);
	if (env[f] > ABS && env[f] - mn > RISE && f - last > 8) {
		// refine to the sample where |x| first exceeds half the frame's local max
		let lm = 0;
		for (let i = (f - 1) * hop; i < (f + 2) * hop && i < n; i++) lm = Math.max(lm, Math.abs(x[i]));
		let i = (f - 1) * hop;
		while (i < n && Math.abs(x[i]) < lm * 0.3) i++;
		onsets.push({ t: i / sr, db: env[f] });
		last = f;
	}
}
console.log('onsets (s): ' + onsets.map((o) => `${o.t.toFixed(3)} (${o.db.toFixed(1)} dB)`).join(', '));
// loudest 50 ms window overall
let best = -1, bestF = 0;
for (let f = 0; f + 5 <= frames; f++) {
	let s = 0;
	for (let k = 0; k < 5; k++) s += Math.pow(10, env[f + k] / 10);
	if (s > best) { best = s; bestF = f; }
}
console.log(`loudest 50 ms window starts ${(bestF * 0.01).toFixed(2)} s (${(10 * Math.log10(best / 5)).toFixed(1)} dB RMS)`);
if (nBeeps > 0) {
	const beeps = onsets.slice(0, nBeeps);
	const pd = onsets[nBeeps];
	const tick = (t) => Math.round(t * 20);
	console.log('beeps: ' + beeps.map((b) => `${b.t.toFixed(3)} s -> tick ${tick(b.t)}`).join(', '));
	if (pd) {
		let b2 = -1, b2F = 0;
		for (let f = Math.floor(pd.t / 0.01); f + 5 <= frames; f++) {
			let s = 0;
			for (let k = 0; k < 5; k++) s += Math.pow(10, env[f + k] / 10);
			if (s > b2) { b2 = s; b2F = f; }
		}
		console.log(`power-down onset ${pd.t.toFixed(3)} s -> tick ${tick(pd.t)}; its loudest 50 ms ${(b2F * 0.01).toFixed(2)} s -> tick ${tick(b2F * 0.01)}`);
	}
}
