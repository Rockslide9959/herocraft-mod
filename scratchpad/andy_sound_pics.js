// v0.17.3 Andy sounds: spectrogram + envelope sheets of the regrowth sounds (you can't listen in CI, so look instead).
// One sheet per group: each row is a labelled spectrogram (log frequency, 20 Hz-20 kHz) with its waveform envelope under it.
// Loops are tiled across 6 s (so the wrap shows too); accents are strung together with 0.25 s gaps.
//   node scratchpad/andy_sound_pics.js <ffmpeg> <dir with regrow_*.ogg> <out prefix>   -> <prefix>_loops.png, <prefix>_accents.png
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');

const [FF, DIR, PREFIX] = process.argv.slice(2);
const SR = 44100;
const TMP = fs.mkdtempSync(path.join(os.tmpdir(), 'andy_pics_'));

function decode(file) {
	const r = spawnSync(FF, ['-hide_banner', '-loglevel', 'error', '-i', file, '-ac', '1', '-ar', String(SR), '-f', 'f32le', '-'], { maxBuffer: 1 << 28 });
	if (r.status !== 0) throw new Error('decode ' + file + ': ' + r.stderr);
	return new Float32Array(r.stdout.buffer.slice(r.stdout.byteOffset, r.stdout.byteOffset + r.stdout.length));
}
function wav(file, x) {
	const b = Buffer.alloc(44 + x.length * 2);
	b.write('RIFF', 0); b.writeUInt32LE(36 + x.length * 2, 4); b.write('WAVE', 8); b.write('fmt ', 12); b.writeUInt32LE(16, 16);
	b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22); b.writeUInt32LE(SR, 24); b.writeUInt32LE(SR * 2, 28); b.writeUInt16LE(2, 32);
	b.writeUInt16LE(16, 34); b.write('data', 36); b.writeUInt32LE(x.length * 2, 40);
	for (let i = 0; i < x.length; i++) b.writeInt16LE(Math.max(-32768, Math.min(32767, Math.round(x[i] * 32767))), 44 + i * 2);
	fs.writeFileSync(file, b);
}
function chain(names, gap = 0.25) {
	const parts = names.map(n => decode(path.join(DIR, n + '.ogg')));
	const g = Math.round(gap * SR);
	const out = new Float32Array(parts.reduce((a, p) => a + p.length + g, 0));
	let o = 0;
	for (const p of parts) { out.set(p, o); o += p.length + g; }
	return out;
}
function run(args) {
	const r = spawnSync(FF, ['-y', '-hide_banner', '-loglevel', 'error', ...args], { cwd: TMP });
	if (r.status !== 0) throw new Error(r.stderr.toString());
}
/** One row: label, spectrogram, envelope. Fixed seconds-per-pixel within a sheet so densities compare. */
function row(i, label, x, seconds) {
	const pad = new Float32Array(Math.round(seconds * SR));
	pad.set(x.subarray(0, Math.min(x.length, pad.length)));
	wav(path.join(TMP, `r${i}.wav`), pad);
	fs.writeFileSync(path.join(TMP, `l${i}.txt`), label);
	run(['-i', `r${i}.wav`, '-filter_complex',
		`[0:a]asplit[a][b];[a]showspectrumpic=s=1400x260:legend=0:fscale=log:color=intensity:gain=1:drange=90[s];` +
		`[b]showwavespic=s=1400x90:colors=0xE04040:scale=lin[w];[s][w]vstack,pad=1400:390:0:40:black,` +
		`drawtext=fontfile=font.ttf:textfile=l${i}.txt:fontcolor=white:fontsize=24:x=8:y=8[o]`, '-map', '[o]', '-frames:v', '1', `p${i}.png`]);
	return `p${i}.png`;
}
function sheet(file, rows, seconds) {
	const pics = rows.map(([label, x], i) => row(sheet.n++, label, x, seconds));
	const ins = pics.flatMap(p => ['-i', p]);
	run([...ins, '-filter_complex', pics.map((_, i) => `[${i}:v]`).join('') + `vstack=inputs=${pics.length}[o]`, '-map', '[o]', '-frames:v', '1', path.resolve(file)]);
	console.log(file);
}
sheet.n = 0;
fs.copyFileSync('C:/Windows/Fonts/arial.ttf', path.join(TMP, 'font.ttf'));
const tag = path.basename(PREFIX);
const WINDOW = 6.0;
/** A loop played round and round for the whole window (so the wrap and the density both show). */
function loopRow(name) {
	const x = decode(path.join(DIR, name + '.ogg'));
	const y = new Float32Array(Math.round(WINDOW * SR));
	for (let i = 0; i < y.length; i++) y[i] = x[i % x.length];
	return [`${tag}: ${name} (loop ${(x.length / SR).toFixed(1)} s, repeated for ${WINDOW} s)`, y];
}
const takes = base => { const out = []; for (let k = 1; fs.existsSync(path.join(DIR, base + k + '.ogg')); k++) out.push(base + k); return out; };
sheet(PREFIX + '_loops.png', ['regrow_bone_push1', 'regrow_muscle1', 'regrow_muscle2', 'regrow_skin1', 'regrow_skin2'].map(loopRow), WINDOW);
sheet(PREFIX + '_accents.png', ['regrow_squelch', 'regrow_skin_stretch', 'regrow_finish', 'regrow_bone_crack'].map(b => {
	const t = takes(b);
	return [`${tag}: ${b}1-${t.length}`, chain(t)];
}), 4.5);
