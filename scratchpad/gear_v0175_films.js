// v0.17.5 gear tiers: encode the GearHarness frames into MP4s + 8-frame sheets, and copy the stills.
//   node scratchpad/gear_v0175_films.js <harness out dir> <dest dir>
// <harness out dir>/screenshots/{grapple,slam}/fNNN.png -> <dest>/{grapple,celestial_slam}.mp4 + *_sheet.png; stills copied as-is.
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
for (const [dir, name, from, count] of [['grapple', 'grapple', 0, 70], ['slam', 'celestial_slam', 0, 80]]) {
  const frames = path.join(shots, dir, 'f%03d.png');
  execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-start_number', String(from), '-i', frames,
    '-vf', 'scale=960:-2', '-c:v', 'h264_mf', '-b:v', '5M', '-pix_fmt', 'yuv420p', path.join(dest, name + '.mp4')]);
  const step = Math.max(1, Math.floor(count / 8));
  execFileSync(FF, ['-y', '-loglevel', 'error', '-start_number', String(from), '-i', frames,
    '-vf', `select=not(mod(n\\,${step})),scale=480:-1,tile=4x2:padding=4:color=0x1b1e24`, '-frames:v', '1', path.join(dest, name + '_sheet.png')]);
  console.log('encoded', name);
}
