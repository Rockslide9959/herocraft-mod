// v0.17.5 Sentry & the Void: copy SentryHarness stills into docs/screenshots/v0175/sentry/ and encode the three films
// (A Million Suns, Erase, the takeover) into MP4s + frame sheets (never a GIF).
//   node scratchpad/sentry_v0175_films.js [shots dir]   (from the checkout root)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = process.cwd();
const shots = process.argv[2] || path.join(root, 'run', 'sentry_shots', 'screenshots');
const out = path.join(root, 'docs', 'screenshots', 'v0175', 'sentry');
fs.mkdirSync(out, { recursive: true });
const sheet = path.join(root, 'scratchpad', 'contact_sheet.js');

const files = fs.readdirSync(shots).filter(f => f.endsWith('.png')).sort();
for (const f of files.filter(f => !f.startsWith('film_'))) {
  fs.copyFileSync(path.join(shots, f), path.join(out, f));
}
for (const [prefix, name] of [['film_suns_', 'million_suns'], ['film_erase_', 'erase'], ['film_takeover_', 'takeover']]) {
  const film = files.filter(f => f.startsWith(prefix));
  if (!film.length) {
    console.log('no frames for', name);
    continue;
  }
  const tmp = path.join(shots, '_seq');
  fs.rmSync(tmp, { recursive: true, force: true });
  fs.mkdirSync(tmp);
  film.forEach((f, i) => fs.copyFileSync(path.join(shots, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
  const mp4 = path.join(out, name + '.mp4');
  execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf', '-b:v', '5M',
    '-pix_fmt', 'yuv420p', mp4]);
  fs.rmSync(tmp, { recursive: true, force: true });
  const step = Math.max(1, Math.floor(film.length / 16));
  const pick = [];
  for (let i = 0; i < film.length && pick.length < 16; i += step) pick.push(path.join(shots, film[i]));
  execFileSync('node', [sheet, path.join(out, name + '_sheet.png'), '4', '0.4', ...pick]);
  console.log(name, 'frames', film.length);
}
console.log('done ->', out);
