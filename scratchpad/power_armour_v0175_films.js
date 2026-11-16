// v0.17.5 Power Armour: encode the PowerArmourHarness frames into MP4s + 8-frame sheets, and copy the stills.
//   node scratchpad/power_armour_v0175_films.js <harness out dir> <dest dir>
// <harness out dir>/screenshots/<film>/fNNN.png -> <dest>/<film>.mp4 + <film>_sheet.png; stills (*.png) copied as they are.
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
// [dir, mp4 name, sheet frames]
for (const [dir, name, picks] of [
  ['enter', 'step_in_and_seal', [4, 14, 24, 34, 46, 58, 70, 90]],
  ['exit', 'step_out', [8, 20, 30, 38, 46, 54, 62, 76]],
  ['punch', 'servo_punch_charged', [3, 10, 18, 24, 27, 29, 33, 42]],
  ['stomp', 'ground_stomp', [4, 7, 9, 11, 13, 16, 22, 32]],
  ['gatling', 'gatling', [6, 10, 14, 20, 26, 32, 40, 50]],
  ['rail', 'rail_cannon', [6, 16, 26, 36, 38, 40, 43, 50]],
  ['jets', 'jump_jets', [6, 9, 12, 16, 20, 26, 32, 40]],
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
