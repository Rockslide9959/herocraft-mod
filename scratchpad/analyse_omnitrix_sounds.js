// v0.17.1 Omnitrix sound audit: decodes each .ogg through ffmpeg to raw mono float PCM and prints, per file,
//   peak dBFS, RMS dBFS (over the loud part: frames within 30 dB of the loudest), length, spectral centroid (Hz),
//   tonality = energy-weighted mean over 2048-sample frames of (largest FFT bin / mean bin) -- a sustained pure tone scores in
//   the hundreds, a bell / chime 60-150+, filtered noise and natural samples single or low double digits -- and
//   flatness = the energy-weighted spectral flatness (geometric / arithmetic mean of the power spectrum, 0 = a pure tone, 1 = white noise).
//   node scratchpad/analyse_omnitrix_sounds.js "<ffmpeg.exe>" [dir-or-files...]   (default dir: assets/projecthero/sounds/omnitrix)
//   add --md for a Markdown table.
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const args = process.argv.slice(2);
const FFMPEG = args.shift();
const md = args.includes('--md');
const targets = args.filter(a => a !== '--md');
const SR = 44100;
const N = 2048;

function decode(file) {
	const out = execFileSync(FFMPEG, ['-v', 'error', '-i', file, '-ac', '1', '-ar', String(SR), '-f', 'f32le', '-'], { maxBuffer: 1 << 28 });
	return new Float32Array(out.buffer, out.byteOffset, out.length / 4);
}
function fft(re, im) {
	const n = re.length;
	for (let i = 1, j = 0; i < n; i++) {
		let bit = n >> 1;
		for (; j & bit; bit >>= 1) j ^= bit;
		j ^= bit;
		if (i < j) { [re[i], re[j]] = [re[j], re[i]]; [im[i], im[j]] = [im[j], im[i]]; }
	}
	for (let len = 2; len <= n; len <<= 1) {
		const a = -2 * Math.PI / len, wr = Math.cos(a), wi = Math.sin(a);
		for (let i = 0; i < n; i += len) {
			let cr = 1, ci = 0;
			for (let k = 0; k < len / 2; k++) {
				const ur = re[i + k], ui = im[i + k];
				const vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci, vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr;
				re[i + k] = ur + vr; im[i + k] = ui + vi; re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi;
				const t = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = t;
			}
		}
	}
}
const db = v => v > 0 ? 20 * Math.log10(v) : -120;
function analyse(x) {
	let peak = 0;
	for (const v of x) peak = Math.max(peak, Math.abs(v));
	const hann = new Float32Array(N).map((_, i) => 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (N - 1)));
	const frames = [];
	for (let o = 0; o + N <= x.length || (o === 0 && x.length > 0); o += N / 2) {
		const re = new Float64Array(N), im = new Float64Array(N);
		let e = 0;
		for (let i = 0; i < N; i++) { const v = o + i < x.length ? x[o + i] : 0; re[i] = v * hann[i]; e += v * v; }
		fft(re, im);
		const mag = new Float64Array(N / 2);
		let max = 0, sum = 0, cen = 0, logSum = 0, pSum = 0;
		for (let k = 1; k < N / 2; k++) {
			const m = Math.hypot(re[k], im[k]);
			mag[k] = m; max = Math.max(max, m); sum += m; cen += m * k * SR / N;
			const pw = m * m + 1e-20; logSum += Math.log(pw); pSum += pw;
		}
		const bins = N / 2 - 1;
		frames.push({ e: e / N, ton: sum > 0 ? max / (sum / bins) : 0, cen: sum > 0 ? cen / sum : 0, flat: Math.exp(logSum / bins) / (pSum / bins) });
		if (o + N > x.length) break;
	}
	const loud = Math.max(...frames.map(f => f.e));
	const used = frames.filter(f => f.e >= loud * 1e-3); // within 30 dB of the loudest frame
	const w = used.reduce((a, f) => a + f.e, 0) || 1;
	const rms = Math.sqrt(used.reduce((a, f) => a + f.e, 0) / used.length);
	return {
		len: x.length / SR,
		peak: db(peak),
		rms: db(rms),
		centroid: used.reduce((a, f) => a + f.cen * f.e, 0) / w,
		tonality: used.reduce((a, f) => a + f.ton * f.e, 0) / w,
		flatness: used.reduce((a, f) => a + f.flat * f.e, 0) / w,
	};
}

let files = [];
const roots = targets.length ? targets : [path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds', 'omnitrix')];
for (const t of roots) {
	if (fs.statSync(t).isDirectory()) files.push(...fs.readdirSync(t).filter(f => f.endsWith('.ogg')).sort().map(f => path.join(t, f)));
	else files.push(t);
}
if (md) {
	console.log('| file | length s | peak dBFS | RMS dBFS | centroid Hz | tonality | flatness |');
	console.log('|---|---|---|---|---|---|---|');
}
for (const f of files) {
	const r = analyse(decode(f));
	const name = path.basename(f, '.ogg');
	if (md) console.log(`| ${name} | ${r.len.toFixed(2)} | ${r.peak.toFixed(1)} | ${r.rms.toFixed(1)} | ${Math.round(r.centroid)} | ${r.tonality.toFixed(0)} | ${r.flatness.toFixed(3)} |`);
	else console.log(name.padEnd(24), `len ${r.len.toFixed(2)}s  peak ${r.peak.toFixed(1)} dBFS  rms ${r.rms.toFixed(1)} dBFS  centroid ${Math.round(r.centroid)} Hz  tonality ${r.tonality.toFixed(0)}  flatness ${r.flatness.toFixed(3)}`);
}
