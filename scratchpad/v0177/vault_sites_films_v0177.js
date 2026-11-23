// v0.17.7 The Vault's new floors + Tier 2 sites: copy VaultSitesHarness stills into docs/screenshots/v0177/vault_sites/,
// build the contact sheet of the 19 site overviews, and encode Warden Kross's phase-2 Breach Slam frames into an MP4 +
// a frame sheet (never a GIF).
//   node scratchpad/v0177/vault_sites_films_v0177.js [shots dir]   (from the worktree root)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = process.cwd();
const shots = process.argv[2] || path.join(root, 'run', 'vaultsites_shots', 'screenshots');
const out = path.join(root, 'docs', 'screenshots', 'v0177', 'vault_sites');
fs.mkdirSync(out, { recursive: true });
const sheet = path.join(root, 'scratchpad', 'contact_sheet.js');

const files = fs.readdirSync(shots).filter(f => f.endsWith('.png')).sort();
const film = files.filter(f => f.startsWith('film_'));
for (const f of files.filter(f => !f.startsWith('film_'))) {
  fs.copyFileSync(path.join(shots, f), path.join(out, f));
}
// the 19 site overviews on one sheet
const sites = files.filter(f => f.startsWith('site_')).map(f => path.join(shots, f));
if (sites.length) {
  execFileSync('java', [path.join(root, 'scratchpad', 'v0177', 'ContactSheet.java'), path.join(out, 'sites_contact_sheet.png'), '5', '360', ...sites], { stdio: 'inherit' });
}
const closes = files.filter(f => f.startsWith('close_')).map(f => path.join(shots, f));
if (closes.length) {
  execFileSync('java', [path.join(root, 'scratchpad', 'v0177', 'ContactSheet.java'), path.join(out, 'sites_new_rooms_sheet.png'), '3', '420', ...closes], { stdio: 'inherit' });
}
if (film.length) {
  const tmp = path.join(shots, '_seq');
  fs.rmSync(tmp, { recursive: true, force: true });
  fs.mkdirSync(tmp);
  film.forEach((f, i) => fs.copyFileSync(path.join(shots, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
  const mp4 = path.join(out, 'kross_phase2_breach_slam.mp4');
  execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf', '-b:v', '5M',
    '-pix_fmt', 'yuv420p', mp4]);
  fs.rmSync(tmp, { recursive: true, force: true });
  const step = Math.max(1, Math.floor(film.length / 12));
  const pick = [];
  for (let i = 0; i < film.length && pick.length < 12; i += step) pick.push(path.join(shots, film[i]));
  execFileSync('node', [sheet, path.join(out, 'kross_phase2_breach_slam_sheet.png'), '4', '0.4', ...pick]);
  console.log('film frames', film.length);
}
console.log('done ->', out);
