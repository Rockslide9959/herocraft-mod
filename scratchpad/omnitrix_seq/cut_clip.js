// Cuts one alien's show transformation out of the compilation video (v0.17.3, docs/OMNITRIX_SEQUENCES.md).
//
//   node scratchpad/omnitrix_seq/cut_clip.js <alien_id> <start_s> <end_s> [--no-sound] [--no-frames] [--video <mp4>]
//
// <start_s> is the slap (sequence tick 0), <end_s> the end (a little after the cut back to the world). Both in seconds of the
// video, decimals allowed. Makes:
//   src/main/resources/assets/projecthero/sounds/omnitrix/<id>_sequence.ogg   mono 44.1 kHz Vorbis, loudness matched to Four Arms'
//   docs/reference/omnitrix_sequences/<id>/f_000.jpg ...                       640x360 frames at 20 fps: f_NNN = tick NNN (gitignored)
//   docs/reference/omnitrix_sequences/<id>/contact_sheet.jpg                   every 5th tick, labelled
//   docs/reference/omnitrix_sequences/<id>/clip.json                           the cut (start, end, ticks)
// The video tick mapping is: video second v -> tick (v - start) * 20.
// ffmpeg: $FFMPEG, else the Medal copy. Loudness: Four Arms' sequence is -21.7 LUFS integrated, true peak -4.2 dBFS; every cut is
// gained to -21.7 LUFS and limited to -4 dBFS (the user dislikes loud sounds).
const fs = require('fs');
const path = require('path');
const { execFileSync, spawnSync } = require('child_process');

const FF = process.env.FFMPEG || 'C:/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe';
const ROOT = path.resolve(__dirname, '..', '..');
const TARGET_LUFS = -21.7;
const LIMIT = 0.63; // -4 dBFS

const args = process.argv.slice(2);
const flag = (n) => { const i = args.indexOf(n); if (i < 0) return false; args.splice(i, 1); return true; };
const opt = (n, d) => { const i = args.indexOf(n); if (i < 0) return d; const v = args[i + 1]; args.splice(i, 2); return v; };
const noSound = flag('--no-sound');
const noFrames = flag('--no-frames');
const video = opt('--video', 'C:/Users/ethan/Downloads/EVERY-ALIEN-TRANSFORMATIONS-SnapYT.App.mp4');
const [id, startS, endS] = args;
if (!id || !/^[a-z0-9_]+$/.test(id) || isNaN(+startS) || isNaN(+endS) || +endS <= +startS) {
  console.error('usage: node scratchpad/omnitrix_seq/cut_clip.js <alien_id> <start_s> <end_s> [--no-sound] [--no-frames] [--video <mp4>]');
  process.exit(1);
}
const start = +startS, end = +endS, dur = end - start;
const ticks = Math.round(dur * 20);

function ff(a) {
  return execFileSync(FF, ['-hide_banner', '-loglevel', 'error', '-y', ...a], { maxBuffer: 1 << 26 });
}

// ------------------------------------------------------------------ the sound
let lufs = null;
if (!noSound) {
  const ogg = path.join(ROOT, 'src/main/resources/assets/projecthero/sounds/omnitrix', id + '_sequence.ogg');
  // pass 1: measure the cut's integrated loudness
  const m = spawnSync(FF, ['-hide_banner', '-ss', String(start), '-t', String(dur), '-i', video, '-vn', '-ac', '1', '-af', 'ebur128', '-f', 'null', '-'],
    { encoding: 'utf8', maxBuffer: 1 << 26 });
  const all = (m.stderr || '').match(/I:\s+(-?[0-9.]+) LUFS/g) || [];
  lufs = all.length ? parseFloat(all[all.length - 1].replace(/[^0-9.-]/g, '')) : -20;
  const gain = Math.max(-30, Math.min(12, TARGET_LUFS - lufs));
  // pass 2: gain, limit, tiny fades, mono Vorbis
  const fadeOut = Math.min(0.25, dur / 4);
  ff(['-ss', String(start), '-t', String(dur), '-i', video, '-vn', '-ac', '1', '-ar', '44100',
    '-af', `volume=${gain.toFixed(2)}dB,alimiter=limit=${LIMIT}:level=false:attack=2:release=40,afade=t=in:d=0.02,afade=t=out:st=${(dur - fadeOut).toFixed(3)}:d=${fadeOut.toFixed(3)}`,
    '-c:a', 'libvorbis', '-q:a', '3', ogg]);
  console.log(`sound: ${path.relative(ROOT, ogg)}  (${dur.toFixed(2)} s, was ${lufs} LUFS, gain ${gain.toFixed(1)} dB)`);
}

// ------------------------------------------------------------------ the frames and the contact sheet
const dir = path.join(ROOT, 'docs/reference/omnitrix_sequences', id);
if (!noFrames) {
  fs.mkdirSync(dir, { recursive: true });
  for (const f of fs.readdirSync(dir)) {
    if (/^f_\d+\.jpg$/.test(f)) fs.unlinkSync(path.join(dir, f));
  }
  // -ss before -i is frame-accurate when re-encoding; fps=20 puts frame n at start + n/20
  ff(['-ss', String(start), '-t', String(dur), '-i', video, '-an', '-vf', 'fps=20,scale=640:360', '-start_number', '0', '-q:v', '3',
    path.join(dir, 'f_%03d.jpg')]);
  const n = fs.readdirSync(dir).filter((f) => /^f_\d+\.jpg$/.test(f)).length;
  const rows = Math.ceil(Math.ceil(n / 5) / 10);
  ff(['-framerate', '20', '-start_number', '0', '-i', path.join(dir, 'f_%03d.jpg'), '-vf',
    "drawtext=fontfile='C\\:/Windows/Fonts/arialbd.ttf':text='%{eif\\:n\\:d}':x=8:y=8:fontsize=44:fontcolor=white:borderw=3:bordercolor=black," +
    `select='not(mod(n\\,5))',scale=192:108,tile=10x${rows}:padding=2:color=black`,
    '-frames:v', '1', '-q:v', '3', path.join(dir, 'contact_sheet.jpg')]);
  console.log(`frames: ${path.relative(ROOT, dir)}/f_000.jpg .. f_${String(n - 1).padStart(3, '0')}.jpg (${n}), contact_sheet.jpg (every 5th tick)`);
}
fs.mkdirSync(dir, { recursive: true });
fs.writeFileSync(path.join(dir, 'clip.json'), JSON.stringify({ alien: id, video: path.basename(video), start, end, ticks,
  mapping: `tick = (videoSecond - ${start}) * 20`, sound: noSound ? null : `omnitrix/${id}_sequence.ogg`, measuredLufs: lufs }, null, 2) + '\n');

console.log(`
ticks: ${ticks} (use it as the sequence's total, or a little less if the cut runs on past the fade)
The sound event is pre-registered for the 12 show aliens: OmnitrixSounds.sequenceCue("${id}") plays
projecthero:omnitrix_${id}_sequence (sounds.json + subtitle already there). For an alien outside the 12, see
docs/OMNITRIX_SEQUENCES.md -> "A new sound event".`);
