// Key-beat comparison sheet (v0.17.4): one row per tick, the show's frame | each capture's frame at that tick, labelled.
//
//   node scratchpad/omnitrix_seq/compare.js <alien> <t1,t2,...> <tag1>[,<tag2>...] [--labels OLD,NEW] [--out file.jpg] [--no-show]
//
// The show frames are docs/reference/omnitrix_sequences/<alien>/f_NNN.jpg (cut_clip.js), the captures run/screenshots/seq_<tag>_NNN.png
// (the harness). A missing frame is drawn black. Sheets with show frames are copyrighted stills: the default output is
// docs/screenshots/v0174/omnitrix_sequences/vs_show/ (git-ignored). With --no-show the sheet is ours alone and may be committed.
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const FF = process.env.FFMPEG || 'C:/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe';
const ROOT = path.resolve(__dirname, '..', '..');
const FONT = "fontfile='C\\:/Windows/Fonts/arialbd.ttf'";
const args = process.argv.slice(2);
const flag = (n) => { const i = args.indexOf(n); if (i < 0) return false; args.splice(i, 1); return true; };
const opt = (n, d) => { const i = args.indexOf(n); if (i < 0) return d; const v = args[i + 1]; args.splice(i, 2); return v; };
const noShow = flag('--no-show');
const labels = (opt('--labels', '') || '').split(',').filter(Boolean);
let out = opt('--out', null);
const [alien, tickList, tagList] = args;
if (!alien || !tickList || !tagList) {
  console.error('usage: compare.js <alien> <t1,t2,...> <tag1>[,<tag2>...] [--labels A,B] [--out f.jpg] [--no-show]');
  process.exit(1);
}
const ticks = tickList.split(',').map(Number);
const tags = tagList.split(',');
const W = 480, H = 270;
const cols = [];
if (!noShow) cols.push({ name: 'SHOW', file: (t) => path.join(ROOT, 'docs/reference/omnitrix_sequences', alien, `f_${String(t).padStart(3, '0')}.jpg`) });
tags.forEach((tg, i) => cols.push({ name: (labels[i] || tg).toUpperCase(), file: (t) => path.join(ROOT, 'run/screenshots', `seq_${tg}_${String(t).padStart(3, '0')}.png`) }));
out = out || path.join(ROOT, 'docs/screenshots/v0174/omnitrix_sequences', noShow ? '' : 'vs_show', `${alien}_compare.jpg`);
fs.mkdirSync(path.dirname(out), { recursive: true });

const inputs = [], filters = [];
let n = 0;
const rowLabels = [];
for (const t of ticks) {
  const row = [];
  for (const c of cols) {
    const f = c.file(t);
    if (fs.existsSync(f)) inputs.push('-i', f); else inputs.push('-f', 'lavfi', '-i', `color=black:s=${W}x${H}`);
    filters.push(`[${n}:v]scale=${W}:${H},setsar=1,drawtext=${FONT}:text='${c.name}  t=${t}':x=8:y=8:fontsize=20:fontcolor=white:borderw=2:bordercolor=black[c${n}]`);
    row.push(`[c${n}]`);
    n++;
  }
  filters.push(`${row.join('')}hstack=${row.length}[r${rowLabels.length}]`);
  rowLabels.push(`[r${rowLabels.length}]`);
}
filters.push(rowLabels.length > 1 ? `${rowLabels.join('')}vstack=${rowLabels.length}[v]` : `${rowLabels[0]}null[v]`);
execFileSync(FF, ['-hide_banner', '-loglevel', 'error', '-y', ...inputs, '-filter_complex', filters.join(';'), '-map', '[v]', '-frames:v', '1', '-q:v', '4', out]);
console.log('sheet:', path.relative(ROOT, out));
