// v0.17.3 Gyro: loudness and modulation of the user's spin clip (a mono 44.1 kHz 16-bit wav from ffmpeg).
//   node scratchpad/gyro/analyse_clip.js <clip.wav>
const fs = require('fs');
const b = fs.readFileSync(process.argv[2]);
let p = 12, data, sr = 44100;
while (p < b.length) {
	const id = b.toString('ascii', p, p + 4), sz = b.readUInt32LE(p + 4);
	if (id === 'fmt ') sr = b.readUInt32LE(p + 12);
	if (id === 'data') { data = b.subarray(p + 8, p + 8 + sz); break; }
	p += 8 + sz;
}
const n = data.length / 2, x = new Float32Array(n);
for (let i = 0; i < n; i++) x[i] = data.readInt16LE(i * 2) / 32768;
let pk = 0;
for (const v of x) pk = Math.max(pk, Math.abs(v));
console.log('samples', n, 'sr', sr, 'peak', pk.toFixed(3));
const W = Math.round(sr * 0.1);
let line = '';
for (let i = 0; i + W <= n; i += W) {
	let s = 0;
	for (let j = i; j < i + W; j++) s += x[j] * x[j];
	line += (i / sr).toFixed(1) + ':' + (20 * Math.log10(Math.sqrt(s / W))).toFixed(0) + ' ';
}
console.log(line);
function rate(a, bb) {
	const w = 88, env = [];
	for (let i = Math.round(a * sr); i + w < Math.round(bb * sr); i += w) {
		let s = 0;
		for (let j = i; j < i + w; j++) s += Math.abs(x[j]);
		env.push(s / w);
	}
	const m = env.reduce((q, v) => q + v, 0) / env.length;
	const e = env.map((v) => v - m);
	const best = [];
	for (let lag = 5; lag < 250; lag++) {
		let s = 0;
		for (let i = 0; i + lag < e.length; i++) s += e[i] * e[i + lag];
		best.push([s, lag]);
	}
	best.sort((q, r) => r[0] - q[0]);
	return best.slice(0, 4).map(([, l]) => (l * w / sr * 1000).toFixed(0) + 'ms');
}
console.log('0.2-2.0 s envelope period', rate(0.2, 2.0));
console.log('2.3-6 s envelope period', rate(2.3, 6));
console.log('6-10.5 s envelope period', rate(6, 10.5));
