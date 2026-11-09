// v0.17.3 Wolverine heal sounds: the listening reel. Every take of wolverine_heal_crackle / _sizzle / _surge in order with
// short gaps, then a short "fight" simulation laid out the way WolverineHealSounds plays them (crackles at the throttled
// rate and in-game volume, a sizzle after a burn, a surge). Writes reel.mp3, spectrogram.png, compare.png (ours above an
// optional reference spectrogram PICTURE -- only the image is used, never its audio) and reel.mp4 (spectrogram + audio).
//   node scratchpad/wolverine_heal_reel.js <ffmpeg with libmp3lame + libx264 + drawtext> <out dir> [reference.png]
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');

const FF = process.argv[2];
const OUT = path.resolve(process.argv[3]);
const REF = process.argv[4];
const SR = 44100;
const DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds', 'wolverine');
const TMP = fs.mkdtempSync(path.join(os.tmpdir(), 'wolv_heal_reel_'));
fs.mkdirSync(OUT, { recursive: true });

function run(args) { const r = spawnSync(FF, ['-hide_banner', '-loglevel', 'error', '-y', ...args], { maxBuffer: 1 << 28 }); if (r.status !== 0) throw new Error(String(r.stderr)); return r.stdout; }
function decode(file) { const b = run(['-i', file, '-ac', '1', '-ar', String(SR), '-f', 'f32le', '-']); return new Float32Array(b.buffer.slice(b.byteOffset, b.byteOffset + b.length)); }
function pitched(x, p) { const n = Math.floor(x.length / p), y = new Float32Array(n); for (let i = 0; i < n; i++) { const t = i * p, k = Math.floor(t), f = t - k; y[i] = (x[k] || 0) * (1 - f) + (x[k + 1] || 0) * f; } return y; }

const clips = [];
let cursor = 0.5;
function place(x, at, gain = 1) { clips.push({ x, at, gain }); cursor = Math.max(cursor, at + x.length / SR); }
const take = n => decode(path.join(DIR, n + '.ogg'));

const takes = ['heal_crackle1', 'heal_crackle2', 'heal_crackle3', 'heal_crackle4', 'heal_sizzle1', 'heal_sizzle2', 'heal_sizzle3', 'heal_surge1', 'heal_surge2'];
let last = '';
for (const n of takes) {
	const group = n.replace(/\d+$/, '');
	if (last && group !== last) cursor += 0.5; // a longer gap between the three events
	last = group;
	place(take(n), cursor, 0.9);
	cursor += 0.4;
}
// simulation: regenerating after a fight -- crackles every 1.2-2 s (CRACKLE_GAP 24 + up to 16 ticks) at the in-game
// level relative to the sizzle / surge, one burn sizzle, then the Death Surge
cursor += 0.8;
let seed = 7;
const rnd = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 4294967296; };
const cr = [1, 2, 3, 4].map(k => take('heal_crackle' + k));
const t0 = cursor;
place(take('heal_sizzle1'), t0, 0.45 / 0.45);
let t = t0 + 1.5;
for (let k = 0; k < 4; k++) { place(pitched(cr[Math.floor(rnd() * 4)], 0.9 + rnd() * 0.25), t, 0.3 / 0.45); t += 1.2 + rnd() * 0.8; }
place(take('heal_surge2'), t + 0.2, 0.7 / 0.45);
cursor += 0.6;

const total = new Float32Array(Math.ceil(cursor * SR));
for (const c of clips) { const o = Math.round(c.at * SR); for (let i = 0; i < c.x.length && o + i < total.length; i++) total[o + i] += c.x[i] * c.gain; }
let pk = 0; for (const v of total) pk = Math.max(pk, Math.abs(v));
if (pk > 0.98) for (let i = 0; i < total.length; i++) total[i] *= 0.98 / pk;
const raw = path.join(TMP, 'reel.f32');
fs.writeFileSync(raw, Buffer.from(total.buffer));

const mp3 = path.join(OUT, 'reel.mp3');
run(['-f', 'f32le', '-ar', String(SR), '-ac', '1', '-i', raw, '-c:a', 'libmp3lame', '-q:a', '2', mp3]);
const spec = path.join(OUT, 'spectrogram.png');
const SPEC = 's=1200x360:legend=0:color=intensity:scale=log:fscale=lin';
run(['-i', mp3, '-lavfi', 'showspectrumpic=' + SPEC, spec]);
const font = 'C\\:/Windows/Fonts/arial.ttf';
const label = (txt) => `drawtext=fontfile='${font}':text='${txt}':x=10:y=8:fontsize=20:fontcolor=white:box=1:boxcolor=black@0.6`;
if (REF) {
	run(['-i', spec, '-i', REF, '-filter_complex',
		`[0]scale=1200:360,${label('Ours - synthesised (crackle x4, sizzle x3, surge x2, then an in-game mix)')}[a];` +
		`[1]scale=1200:360,${label('Reference picture (film scene, gated) - look only')}[b];[a][b]vstack`,
		path.join(OUT, 'compare.png')]);
}
run(['-loop', '1', '-framerate', '10', '-i', spec, '-i', mp3, '-filter_complex',
	`[0]scale=1200:360,format=rgb24[s];color=c=white:s=2x360:r=10[l];[s][l]overlay=x='t/${cursor.toFixed(3)}*1200':y=0:shortest=0[v]`,
	'-map', '[v]', '-map', '1:a', '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '160k', '-t', cursor.toFixed(3), path.join(OUT, 'reel.mp4')]);
console.log('reel', cursor.toFixed(2) + ' s ->', OUT);
