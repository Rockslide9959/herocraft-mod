// Turns a harness capture into films (docs/OMNITRIX_SEQUENCES.md).
//
//   node scratchpad/omnitrix_seq/film.js <tag> [options]
//
// <tag> is the harness tag: frames run/screenshots/seq_<tag>_NNN.png (NNN = sequence tick).
//   (no option)          <tag>.mp4: our frames at 20 fps with the alien's sequence sound
//   --sbs                <alien>_vs_show.mp4: ours | the show (docs/reference/omnitrix_sequences/<alien>/f_NNN.jpg), tick-locked
//   --vs <tag2>          <tag2>|<tag> side by side (e.g. before | after); --labels "BEFORE,AFTER"
//   --diff               with --vs: per-frame PSNR of <tag> against <tag2> (identical renders are 'inf' / above ~45 dB)
//   --sheet              <name>_sheet.jpg: every 5th tick, labelled (send this with the MP4 -- never a GIF)
//   --alien <id>         default: the tag minus a trailing _self / _watch
//   --out <file.mp4>     default: docs/screenshots/v0173/omnitrix_sequences/<name>.mp4
//   --from N --to N      limit the ticks used
const fs = require('fs');
const path = require('path');
const { execFileSync, spawnSync } = require('child_process');

const FF = process.env.FFMPEG || 'C:/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe';
const ROOT = path.resolve(__dirname, '..', '..');
const SHOTS = path.join(ROOT, 'run/screenshots');
const FONT = "fontfile='C\\:/Windows/Fonts/arialbd.ttf'";

const args = process.argv.slice(2);
const flag = (n) => { const i = args.indexOf(n); if (i < 0) return false; args.splice(i, 1); return true; };
const opt = (n, d) => { const i = args.indexOf(n); if (i < 0) return d; const v = args[i + 1]; args.splice(i, 2); return v; };
const sbs = flag('--sbs'), diff = flag('--diff'), sheet = flag('--sheet');
const vs = opt('--vs', null), labels = (opt('--labels', '') || '').split(',').filter(Boolean);
let out = opt('--out', null);
const fromOpt = opt('--from', null), toOpt = opt('--to', null);
const tag = args[0];
if (!tag) {
  console.error('usage: node scratchpad/omnitrix_seq/film.js <tag> [--sbs] [--vs <tag2> [--diff] [--labels A,B]] [--sheet] [--alien id] [--out f.mp4]');
  process.exit(1);
}
const alien = opt('--alien', null) || tag.replace(/_(self|watch)$/, '');
const OUTDIR = path.join(ROOT, 'docs/screenshots/v0173/omnitrix_sequences');
fs.mkdirSync(OUTDIR, { recursive: true });

function ticks(t) {
  const re = new RegExp('^seq_' + t.replace(/[^a-z0-9_]/gi, '') + '_(\\d{3})\\.png$');
  const n = fs.readdirSync(SHOTS).map((f) => (f.match(re) || [])[1]).filter(Boolean).map(Number).sort((a, b) => a - b);
  if (!n.length) throw new Error('no frames run/screenshots/seq_' + t + '_NNN.png');
  return n;
}
function range(n) {
  let a = n[0], b = n[n.length - 1];
  if (fromOpt != null) a = Math.max(a, +fromOpt);
  if (toOpt != null) b = Math.min(b, +toOpt);
  // the run must be contiguous for ffmpeg's image2 reader
  for (let i = a; i <= b; i++) if (!n.includes(i)) { b = i - 1; break; }
  return [a, b];
}
function ff(a) { execFileSync(FF, ['-hide_banner', '-loglevel', 'error', '-y', ...a], { maxBuffer: 1 << 26, stdio: ['ignore', 'inherit', 'inherit'] }); }
const sound = path.join(ROOT, 'src/main/resources/assets/projecthero/sounds/omnitrix', alien + '_sequence.ogg');
const audioIn = (from) => fs.existsSync(sound) ? ['-ss', (from / 20).toFixed(3), '-i', sound] : [];
const audioMap = (idx) => fs.existsSync(sound) ? ['-map', idx + ':a', '-c:a', 'aac', '-b:a', '128k'] : [];
const enc = ['-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '20', '-preset', 'veryfast', '-r', '20'];
const label = (txt, from, withTick) => `drawtext=${FONT}:text='${txt}${withTick ? `  t=%{eif\\:n+${from}\\:d}` : ''}':x=10:y=10:fontsize=22:fontcolor=white:borderw=2:bordercolor=black`;
const seqIn = (t, from) => ['-framerate', '20', '-start_number', String(from), '-i', path.join(SHOTS, `seq_${t}_%03d.png`)];

const mine = ticks(tag);
let [a, b] = range(mine);
let name;
if (vs) {
  const other = ticks(vs);
  const [a2, b2] = range(other);
  a = Math.max(a, a2); b = Math.min(b, b2);
  name = path.basename(out || `${vs}_vs_${tag}.mp4`, '.mp4');
  out = out || path.join(OUTDIR, name + '.mp4');
  const [l1, l2] = labels.length === 2 ? labels : [vs.toUpperCase(), tag.toUpperCase()];
  ff([...seqIn(vs, a), ...seqIn(tag, a), ...audioIn(a), '-frames:v', String(b - a + 1), '-filter_complex',
    `[0:v]scale=640:360,${label(l1, a, true)}[l];[1:v]scale=640:360,${label(l2, a, false)}[r];[l][r]hstack=2[v]`,
    '-map', '[v]', ...audioMap(2), ...enc, '-shortest', out]);
  if (diff) {
    const r = spawnSync(FF, ['-hide_banner', ...seqIn(vs, a), ...seqIn(tag, a), '-frames:v', String(b - a + 1), '-lavfi', '[0:v][1:v]psnr=stats_file=-', '-f', 'null', '-'],
      { encoding: 'utf8', maxBuffer: 1 << 26 });
    const rows = (r.stdout || '').trim().split(/\r?\n/).filter((l) => l.includes('psnr_avg'));
    const vals = rows.map((l, i) => ({ t: a + i, p: /psnr_avg:inf/.test(l) ? Infinity : parseFloat(l.match(/psnr_avg:([0-9.]+)/)[1]) }));
    const finite = vals.filter((v) => isFinite(v.p));
    const low = vals.filter((v) => v.p < 35);
    console.log(`PSNR over ${vals.length} frames: ${vals.length - finite.length} identical, min ${finite.length ? Math.min(...finite.map((v) => v.p)).toFixed(1) : 'inf'} dB, mean(finite) ${finite.length ? (finite.reduce((s, v) => s + v.p, 0) / finite.length).toFixed(1) : 'inf'} dB`);
    if (low.length) console.log('frames under 35 dB:', low.map((v) => `${v.t}:${v.p.toFixed(1)}`).join(' '));
  }
} else if (sbs) {
  const ref = path.join(ROOT, 'docs/reference/omnitrix_sequences', alien);
  if (!fs.existsSync(path.join(ref, 'f_000.jpg'))) throw new Error('no show frames: run cut_clip.js ' + alien + ' first');
  const refN = fs.readdirSync(ref).filter((f) => /^f_\d+\.jpg$/.test(f)).length;
  b = Math.min(b, refN - 1);
  name = path.basename(out || `${alien}_vs_show.mp4`, '.mp4');
  out = out || path.join(OUTDIR, name + '.mp4');
  ff([...seqIn(tag, a), '-framerate', '20', '-start_number', String(a), '-i', path.join(ref, 'f_%03d.jpg'), ...audioIn(a),
    '-frames:v', String(b - a + 1), '-filter_complex',
    `[0:v]scale=640:360,${label('OURS', a, true)}[l];[1:v]scale=640:360,${label('SHOW', a, false)}[r];[l][r]hstack=2[v]`,
    '-map', '[v]', ...audioMap(2), ...enc, '-shortest', out]);
} else {
  name = path.basename(out || `${tag}.mp4`, '.mp4');
  out = out || path.join(OUTDIR, name + '.mp4');
  ff([...seqIn(tag, a), ...audioIn(a), '-frames:v', String(b - a + 1), '-vf', `scale=854:480,${label(tag.toUpperCase(), a, true)}`,
    '-map', '0:v', ...audioMap(1), ...enc, '-shortest', out]);
}
console.log('film:', path.relative(ROOT, out), `(ticks ${a}..${b})`);
if (sheet) {
  const s = path.join(path.dirname(out), name + '_sheet.jpg');
  const cols = vs || sbs ? 6 : 10, n = Math.ceil((b - a + 1) / 5);
  ff(['-i', out, '-vf', `select='not(mod(n\\,5))',scale=${vs || sbs ? 480 : 240}:-2,tile=${cols}x${Math.ceil(n / cols)}:padding=2`, '-frames:v', '1', '-q:v', '4', s]);
  console.log('sheet:', path.relative(ROOT, s));
}
