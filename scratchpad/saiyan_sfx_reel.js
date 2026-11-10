// v0.17.3 Saiyan sounds: the listening reel. Every take in order with short gaps (loops played twice back to back so the
// seam can be heard), at the level each is meant to be played. Writes reel.mp3, spectrogram.png and reel.mp4 (spectrogram
// with a moving playhead + the audio).
//   node scratchpad/saiyan_sfx_reel.js <ffmpeg> <out dir>
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');

const FF = process.argv[2];
const OUT = path.resolve(process.argv[3]);
const SR = 44100;
const DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds', 'saiyan');
const TMP = fs.mkdtempSync(path.join(os.tmpdir(), 'saiyan_reel_'));
fs.mkdirSync(OUT, { recursive: true });
function run(args) { const r = spawnSync(FF, ['-hide_banner', '-loglevel', 'error', '-y', ...args], { maxBuffer: 1 << 28 }); if (r.status !== 0) throw new Error(String(r.stderr)); return r.stdout; }
function decode(file) { const b = run(['-i', file, '-ac', '1', '-ar', String(SR), '-f', 'f32le', '-']); return new Float32Array(b.buffer.slice(b.byteOffset, b.byteOffset + b.length)); }

// [stem, play gain (the in-game volume the power should use), loop?]
const ORDER = [
	['ki_charge_start', 0.6], ['ki_charge_loop', 0.45, true],
	['aura_loop_gold', 0.35, true], ['aura_loop_blue', 0.35, true], ['aura_loop_red', 0.35, true], ['aura_loop_silver', 0.35, true],
	['transform_burst1', 0.8], ['transform_burst2', 0.8], ['ssj2_spark1', 0.4], ['ssj2_spark2', 0.4], ['ssj2_spark3', 0.4], ['power_down', 0.6],
	['ki_dash1', 0.6], ['ki_dash2', 0.6], ['instant_step1', 0.6], ['instant_step2', 0.6],
	['punch_hit_light1', 0.6], ['punch_hit_light2', 0.6], ['punch_hit_light3', 0.6], ['punch_hit_heavy1', 0.75], ['punch_hit_heavy2', 0.75],
	['beam_charge', 0.6], ['beam_fire_loop', 0.55, true], ['beam_impact1', 0.75], ['beam_impact2', 0.75],
	['orb_charge', 0.7], ['orb_explode', 0.85], ['dragon_rush', 0.7], ['barrage_rapid', 0.6],
	['instinct_dodge1', 0.5], ['instinct_dodge2', 0.5], ['exhausted', 0.6],
];
const clips = [];
let cursor = 0.4;
for (const [stem, gain, loop] of ORDER) {
	const x = decode(path.join(DIR, stem + '.ogg'));
	const reps = loop ? 2 : 1;
	for (let r = 0; r < reps; r++) { clips.push({ x, at: cursor, gain }); cursor += x.length / SR; }
	cursor += 0.45;
}
const total = new Float32Array(Math.ceil((cursor + 0.3) * SR));
for (const c of clips) { const o = Math.round(c.at * SR); for (let i = 0; i < c.x.length && o + i < total.length; i++) total[o + i] += c.x[i] * c.gain; }
let pk = 0; for (const v of total) pk = Math.max(pk, Math.abs(v));
if (pk > 0.98) for (let i = 0; i < total.length; i++) total[i] *= 0.98 / pk;
const raw = path.join(TMP, 'reel.f32');
fs.writeFileSync(raw, Buffer.from(total.buffer));
const mp3 = path.join(OUT, 'reel.mp3');
run(['-f', 'f32le', '-ar', String(SR), '-ac', '1', '-i', raw, '-c:a', 'libmp3lame', '-q:a', '2', mp3]);
const spec = path.join(OUT, 'spectrogram.png');
run(['-i', mp3, '-lavfi', 'showspectrumpic=s=1600x420:legend=1:color=intensity:scale=log:fscale=lin', spec]);
const dur = total.length / SR;
const mp4 = path.join(OUT, 'reel.mp4');
// the spectrogram picture (with its legend margins) scrolled under a playhead line
run(['-loop', '1', '-i', spec, '-i', mp3, '-filter_complex',
	`[0:v]scale=1920:-2,format=yuv420p[bg];[bg]drawbox=x='166+(1600*1920/1938)*t/${dur.toFixed(3)}':y=0:w=3:h=ih:color=white@0.85:t=fill[v]`,
	'-map', '[v]', '-map', '1:a', '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '24', '-r', '25', '-c:a', 'aac', '-b:a', '160k', '-shortest', mp4]);
console.log('reel', dur.toFixed(1) + ' s ->', OUT);
