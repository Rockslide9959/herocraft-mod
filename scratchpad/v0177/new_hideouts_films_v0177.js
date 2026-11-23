// v0.17.7 three new hideouts: copy NewHideoutsHarness stills into docs/screenshots/v0177/new_hideouts/, encode the
// Crypt Lord's Requiem and the Oathbound Champion's Judgement Blades frames into MP4s + frame sheets (never a GIF), and a
// contact sheet per hideout.
//   node scratchpad/v0177/new_hideouts_films_v0177.js [shots dir]   (from the worktree root)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = process.cwd();
const shots = process.argv[2] || path.join(root, 'run', 'newhideouts_shots', 'screenshots');
const out = path.join(root, 'docs', 'screenshots', 'v0177', 'new_hideouts');
fs.mkdirSync(out, { recursive: true });
const sheet = path.join(root, 'scratchpad', 'contact_sheet.js');

const files = fs.readdirSync(shots).filter(f => f.endsWith('.png')).sort();
const stills = files.filter(f => !f.startsWith('film'));
for (const f of stills) fs.copyFileSync(path.join(shots, f), path.join(out, f));
for (const fac of ['gravebound', 'carnage', 'oathbreaker']) {
  const mine = stills.filter(f => f.startsWith(fac + '_')).map(f => path.join(shots, f));
  if (mine.length) execFileSync('node', [sheet, path.join(out, `${fac}_contact_sheet.png`), '4', '0.35', ...mine]);
}
function film(prefix, name) {
  const frames = files.filter(f => f.startsWith(prefix));
  if (!frames.length) return;
  const tmp = path.join(shots, '_seq_' + name);
  fs.rmSync(tmp, { recursive: true, force: true });
  fs.mkdirSync(tmp);
  frames.forEach((f, i) => fs.copyFileSync(path.join(shots, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
  const mp4 = path.join(out, name + '.mp4');
  execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf', '-b:v', '5M',
    '-pix_fmt', 'yuv420p', mp4]);
  fs.rmSync(tmp, { recursive: true, force: true });
  const step = Math.max(1, Math.floor(frames.length / 12));
  const pick = [];
  for (let i = 0; i < frames.length && pick.length < 12; i += step) pick.push(path.join(shots, frames[i]));
  execFileSync('node', [sheet, path.join(out, name + '_sheet.png'), '4', '0.4', ...pick]);
  console.log(name, frames.length, 'frames');
}
film('film_crypt_lord_requiem_', 'crypt_lord_phase2_requiem');
film('film2_champion_blades_', 'oathbound_champion_phase2_judgement_blades');
console.log('done ->', out);
