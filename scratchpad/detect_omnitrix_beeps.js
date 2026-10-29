// v0.17.1: finds the 5 beeps and the power-down in the user's omnitrix-power-down sound (run on the decoded,
// trimmed OGG that actually ships, so the times are what the player hears from the sound's start).
// Usage: node detect_omnitrix_beeps.js <s16le mono 44100 pcm> [beeps=5]
//
// Method: a 2048-point Hann STFT every 5 ms.
//  * Beeps: the beeps are short tones (1.6 kHz falling to ~0.9 kHz) over a continuous low drone (215 -> 108 Hz)
//    and a steady 5.3 kHz whine, so broadband RMS can't see them. The beep envelope is the energy in 600-2000 Hz;
//    an onset is the first frame that rises > 10 dB above the minimum of the previous 60 ms (and > 8 dB above the
//    band's median), at least 250 ms after the last one; it is refined to the 5 ms frame where the band first
//    passes the halfway point (in dB) between the pre-onset floor and the beep's peak.
//  * Power-down: after the last beep the tone sweeps down (~970 -> 430 Hz) and then the low end (0-150 Hz) jumps
//    while 5-22 kHz collapses -- the "clunk" as the watch dies. Its onset is the first frame after the last beep
//    where the 0-150 Hz band rises > 6 dB above its previous-100 ms mean; its strongest part is the loudest 50 ms
//    (broadband RMS) after that onset.
const fs = require('fs');
const buf = fs.readFileSync(process.argv[2]);
const nBeeps = parseInt(process.argv[3] || '5', 10);
const sr = 44100, N = 2048, hop = 220;
const n = buf.length >> 1;
const x = new Float32Array(n);
for (let i = 0; i < n; i++) x[i] = buf.readInt16LE(i * 2) / 32768;
function fft(re, im) {
	const m = re.length;
	for (let i = 1, j = 0; i < m; i++) {
		let bit = m >> 1;
		for (; j & bit; bit >>= 1) j ^= bit;
		j ^= bit;
		if (i < j) { [re[i], re[j]] = [re[j], re[i]]; [im[i], im[j]] = [im[j], im[i]]; }
	}
	for (let len = 2; len <= m; len <<= 1) {
		const ang = -2 * Math.PI / len;
		for (let i = 0; i < m; i += len) for (let k = 0; k < len / 2; k++) {
			const wr = Math.cos(ang * k), wi = Math.sin(ang * k);
			const ar = re[i + k + len / 2], ai = im[i + k + len / 2];
			const tr = ar * wr - ai * wi, ti = ar * wi + ai * wr;
			re[i + k + len / 2] = re[i + k] - tr; im[i + k + len / 2] = im[i + k] - ti;
			re[i + k] += tr; im[i + k] += ti;
		}
	}
}
const band = (mag, a, b) => {
	let e = 0;
	for (let k = Math.floor(a * N / sr); k < Math.floor(b * N / sr); k++) e += mag[k] * mag[k];
	return 10 * Math.log10(e + 1e-12);
};
const T = [], beepE = [], lowE = [], rms = [];
for (let s = -N / 2; s + N / 2 < n; s += hop) {
	const re = new Float64Array(N), im = new Float64Array(N);
	let e = 0;
	for (let i = 0; i < N; i++) {
		const v = s + i >= 0 && s + i < n ? x[s + i] : 0;
		re[i] = v * (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (N - 1)));
	}
	for (let i = Math.max(0, s + N / 2 - hop / 2); i < Math.min(n, s + N / 2 + hop / 2); i++) e += x[i] * x[i];
	fft(re, im);
	const mag = new Float64Array(N / 2);
	for (let k = 0; k < N / 2; k++) mag[k] = Math.hypot(re[k], im[k]);
	T.push((s + N / 2) / sr);
	beepE.push(band(mag, 600, 2000));
	lowE.push(band(mag, 0, 150));
	rms.push(10 * Math.log10(e / hop + 1e-12));
}
const sorted = [...beepE].sort((a, b) => a - b);
const median = sorted[sorted.length >> 1];
const beeps = [];
let last = -1e9;
for (let f = 12; f < T.length && beeps.length < nBeeps; f++) {
	let mn = Infinity;
	for (let k = f - 12; k < f; k++) mn = Math.min(mn, beepE[k]);
	if (beepE[f] - mn > 10 && beepE[f] > median + 8 && T[f] - last > 0.25) {
		let pk = beepE[f];
		for (let k = f; k < Math.min(T.length, f + 16); k++) pk = Math.max(pk, beepE[k]);
		const half = (mn + pk) / 2;
		let g = f - 12;
		while (beepE[g] < half) g++;
		beeps.push({ t: T[g], peak: pk, floor: mn });
		last = T[g];
		f += 40;
	}
}
const after = beeps.length ? beeps[beeps.length - 1].t + 0.25 : 0;
let pd = null;
for (let f = 20; f < T.length; f++) {
	if (T[f] < after) continue;
	let m = 0;
	for (let k = f - 20; k < f; k++) m += lowE[k];
	m /= 20;
	if (lowE[f] - m > 6) { pd = f; break; }
}
const tick = (t) => Math.round(t * 20);
console.log(`file ${(n / sr).toFixed(3)} s; beep band median ${median.toFixed(1)} dB`);
beeps.forEach((b, i) => console.log(`beep ${i + 1}: ${b.t.toFixed(3)} s  (band floor ${b.floor.toFixed(1)} -> peak ${b.peak.toFixed(1)} dB)  -> tick ${tick(b.t)}`));
if (pd != null) {
	let best = -Infinity, bf = pd;
	for (let f = pd; f + 10 < T.length && T[f] < T[pd] + 0.6; f++) {
		let s = 0;
		for (let k = 0; k < 10; k++) s += Math.pow(10, rms[f + k] / 10);
		if (s > best) { best = s; bf = f; }
	}
	console.log(`power-down onset: ${T[pd].toFixed(3)} s -> tick ${tick(T[pd])}`);
	console.log(`power-down strongest 50 ms: ${T[bf].toFixed(3)}-${(T[bf] + 0.05).toFixed(3)} s (${(10 * Math.log10(best / 10)).toFixed(1)} dB RMS) -> tick ${tick(T[bf] + 0.025)}`);
}
