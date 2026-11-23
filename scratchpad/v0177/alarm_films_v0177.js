// v0.17.7 alarm framework + rebuilt hideouts: copy AlarmHideoutsHarness stills into docs/screenshots/v0177/alarm_hideouts/,
// crop the HUD eye out of the alarm shots (x3 nearest-neighbour), and encode the Frost Giant Jarl's phase-2 Ice Spike
// frames into an MP4 + a frame sheet (never a GIF).
//   node scratchpad/v0177/alarm_films_v0177.js [shots dir]   (from the worktree root)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = process.cwd();
const shots = process.argv[2] || path.join(root, 'run', 'alarm_shots', 'screenshots');
const out = path.join(root, 'docs', 'screenshots', 'v0177', 'alarm_hideouts');
fs.mkdirSync(out, { recursive: true });
const sheet = path.join(root, 'scratchpad', 'contact_sheet.js');

const files = fs.readdirSync(shots).filter(f => f.endsWith('.png')).sort();
const film = files.filter(f => f.startsWith('film_'));
for (const f of files.filter(f => !f.startsWith('film_'))) {
  fs.copyFileSync(path.join(shots, f), path.join(out, f));
}
// the HUD eye: the screenshot is 854x480 at GUI scale 2 -> the eye sits at x = 427 + 200 = 627 (+/-), y 4..40
for (const f of files.filter(f => f.startsWith('alarm_') || f.startsWith('hud_'))) {
  const dst = path.join(out, 'eye_' + f);
  try {
    execFileSync(FF, ['-y', '-loglevel', 'error', '-i', path.join(shots, f), '-vf', 'crop=110:56:590:0,scale=330:168:flags=neighbor', dst]);
  } catch (e) {
    console.log('crop failed', f, e.message);
  }
}
if (film.length) {
  const tmp = path.join(shots, '_seq');
  fs.rmSync(tmp, { recursive: true, force: true });
  fs.mkdirSync(tmp);
  film.forEach((f, i) => fs.copyFileSync(path.join(shots, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
  const mp4 = path.join(out, 'frost_jarl_phase2_ice_spikes.mp4');
  execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf', '-b:v', '5M',
    '-pix_fmt', 'yuv420p', mp4]);
  fs.rmSync(tmp, { recursive: true, force: true });
  const step = Math.max(1, Math.floor(film.length / 12));
  const pick = [];
  for (let i = 0; i < film.length && pick.length < 12; i += step) pick.push(path.join(shots, film[i]));
  execFileSync('node', [sheet, path.join(out, 'frost_jarl_phase2_ice_spikes_sheet.png'), '4', '0.4', ...pick]);
  console.log('film frames', film.length);
}
console.log('done ->', out);
