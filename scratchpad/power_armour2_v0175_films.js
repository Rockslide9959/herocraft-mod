// v0.17.5 Power Armour round 2 (proportions + side-aware opening): MP4s + 8-frame sheets from PowerArmourHarness2 frames, the
// stills copied, and before / after side-by-sides (before = a run of the harness's "before" mode on the old code).
//   node scratchpad/power_armour2_v0175_films.js <after out dir> <before out dir> <dest dir>
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const [src, before, dest] = process.argv.slice(2);
const shots = path.join(src, 'screenshots');
fs.mkdirSync(dest, { recursive: true });

for (const f of fs.readdirSync(shots)) {
  if (f.endsWith('.png')) fs.copyFileSync(path.join(shots, f), path.join(dest, f));
}
// before / after, one image per view (before on the left, after on the right)
const bshots = path.join(before, 'screenshots');
for (const view of ['front', 'side', 'back', 'three_quarter']) {
  const b = path.join(bshots, `before_compare_${view}.png`), a = path.join(shots, `after_compare_${view}.png`);
  if (!fs.existsSync(b) || !fs.existsSync(a)) continue;
  fs.copyFileSync(b, path.join(dest, `before_compare_${view}.png`));
  execFileSync(FF, ['-y', '-loglevel', 'error', '-i', b, '-i', a, '-filter_complex',
    '[0]pad=iw+6:ih:0:0:color=0x1b1e24[l];[l][1]hstack',
    path.join(dest, `before_after_${view}.png`)]);
}
// [dir, mp4 name, sheet frames]
for (const [dir, name, picks] of [
  ['enter_front', 'enter_from_front', [2, 12, 22, 30, 42, 56, 70, 92]],
  ['enter_back', 'enter_from_back', [2, 12, 22, 30, 42, 56, 70, 92]],
  ['exit_front', 'exit_front', [6, 22, 28, 34, 40, 46, 54, 70]],
  ['exit_back', 'exit_back', [6, 22, 28, 34, 40, 46, 54, 70]],
]) {
  const folder = path.join(shots, dir);
  if (!fs.existsSync(folder)) continue;
  const count = fs.readdirSync(folder).filter(f => f.endsWith('.png')).length;
  if (!count) continue;
  const frames = path.join(folder, 'f%03d.png');
  execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-start_number', '0', '-i', frames,
    '-frames:v', String(count), '-vf', 'scale=854:-2', '-c:v', 'h264_mf', '-b:v', '5M', '-pix_fmt', 'yuv420p', path.join(dest, name + '.mp4')]);
  const sel = picks.filter(n => n < count).map(n => `eq(n\\,${n})`).join('+');
  execFileSync(FF, ['-y', '-loglevel', 'error', '-start_number', '0', '-i', frames,
    '-vf', `select='${sel}',scale=427:-1,tile=4x2:padding=4:color=0x1b1e24`, '-frames:v', '1', path.join(dest, name + '_sheet.png')]);
  console.log('encoded', name, count, 'frames');
}
