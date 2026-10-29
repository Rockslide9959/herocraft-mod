// v0.17.1: a coarse spectrogram of a s16le mono 44100 PCM file: per 20 ms frame, the three strongest
// spectral peaks (Hz, dB) and the energy in a few bands. Usage: node spectro_user_omnitrix_audio.js file.pcm [from] [to]
const fs = require('fs');
const buf = fs.readFileSync(process.argv[2]);
const from = parseFloat(process.argv[3] || '0'), to = parseFloat(process.argv[4] || '99');
const sr = 44100, N = 2048, hop = 882;
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
		for (let i = 0; i < m; i += len) {
			for (let k = 0; k < len / 2; k++) {
				const wr = Math.cos(ang * k), wi = Math.sin(ang * k);
				const ar = re[i + k + len / 2], ai = im[i + k + len / 2];
				const tr = ar * wr - ai * wi, ti = ar * wi + ai * wr;
				re[i + k + len / 2] = re[i + k] - tr; im[i + k + len / 2] = im[i + k] - ti;
				re[i + k] += tr; im[i + k] += ti;
			}
		}
	}
}
const bands = [[0, 300], [300, 1000], [1000, 2500], [2500, 5000], [5000, 10000], [10000, 22050]];
console.log('t      ' + bands.map((b) => `${b[0]}-${b[1]}`.padStart(11)).join('') + '   peaks');
for (let s = 0; s + N < n; s += hop) {
	const t = s / sr;
	if (t < from || t > to) continue;
	const re = new Float64Array(N), im = new Float64Array(N);
	for (let i = 0; i < N; i++) re[i] = x[s + i] * (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (N - 1)));
	fft(re, im);
	const mag = new Float64Array(N / 2);
	for (let k = 0; k < N / 2; k++) mag[k] = Math.hypot(re[k], im[k]);
	const be = bands.map(([a, b]) => {
		let e = 0;
		for (let k = Math.floor(a * N / sr); k < Math.min(N / 2, Math.floor(b * N / sr)); k++) e += mag[k] * mag[k];
		return (10 * Math.log10(e + 1e-12)).toFixed(1).padStart(11);
	});
	const pk = [];
	for (let k = 2; k < N / 2 - 1; k++) if (mag[k] > mag[k - 1] && mag[k] >= mag[k + 1]) pk.push(k);
	pk.sort((a, b) => mag[b] - mag[a]);
	const top = pk.slice(0, 3).map((k) => `${Math.round(k * sr / N)}Hz/${(20 * Math.log10(mag[k] + 1e-12)).toFixed(0)}`).join(' ');
	console.log(t.toFixed(2).padEnd(7) + be.join('') + '   ' + top);
}
