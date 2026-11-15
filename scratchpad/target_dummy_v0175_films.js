// v0.17.5 Target Dummy: encode the TargetDummyHarness frames into MP4s + 8-frame sheets, and copy the stills.
//   node scratchpad/target_dummy_v0175_films.js <harness out dir> <dest dir>
// <harness out dir>/screenshots/{melee,power}/fNNN.png -> <dest>/{sword_hits,power_hits}.mp4 + *_sheet.png; stills copied.
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const [src, dest] = process.argv.slice(2);
const shots = path.join(src, 'screenshots');
fs.mkdirSync(dest, { recursive: true });

for (const f of fs.readdirSync(shots)) {
  if (f.endsWith('.png')) fs.copyFileSync(path.join(shots, f), path.join(dest, f));
}
// [dir, name, first frame, frame count, sheet frames (hits + the wobble after them)]
for (const [dir, name, count, picks] of [
  ['melee', 'sword_hits', 110, [3, 6, 8, 11, 61, 63, 65, 68]],
  ['power', 'power_hits', 100, [4, 7, 10, 34, 45, 60, 75, 90]],
]) {
  const frames = path.join(shots, dir, 'f%03d.png');
  execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-start_number', '0', '-i', frames,
    '-frames:v', String(count), '-vf', 'scale=960:-2', '-c:v', 'h264_mf', '-b:v', '5M', '-pix_fmt', 'yuv420p', path.join(dest, name + '.mp4')]);
  const sel = picks.map(n => `eq(n\\,${n})`).join('+');
  execFileSync(FF, ['-y', '-loglevel', 'error', '-start_number', '0', '-i', frames,
    '-vf', `select='${sel}',scale=480:-1,tile=4x2:padding=4:color=0x1b1e24`, '-frames:v', '1', path.join(dest, name + '_sheet.png')]);
  console.log('encoded', name);
}
