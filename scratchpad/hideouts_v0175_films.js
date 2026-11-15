// v0.17.5 villain hideouts: copy HideoutHarness stills into docs/screenshots/v0175/hideouts/ and encode the Parademon
// Commander's Dive Strike frames into an MP4 + a frame sheet (never a GIF).
//   node scratchpad/hideouts_v0175_films.js [shots dir]   (from the checkout root)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = process.cwd();
const shots = process.argv[2] || path.join(root, 'run', 'hideout_shots', 'screenshots');
const out = path.join(root, 'docs', 'screenshots', 'v0175', 'hideouts');
fs.mkdirSync(out, { recursive: true });
const sheet = path.join(root, 'scratchpad', 'contact_sheet.js');

const files = fs.readdirSync(shots).filter(f => f.endsWith('.png')).sort();
const film = files.filter(f => f.startsWith('film_commander_dive_'));
for (const f of files.filter(f => !f.startsWith('film_'))) {
	fs.copyFileSync(path.join(shots, f), path.join(out, f));
}
if (film.length) {
	const tmp = path.join(shots, '_seq');
	fs.rmSync(tmp, { recursive: true, force: true });
	fs.mkdirSync(tmp);
	film.forEach((f, i) => fs.copyFileSync(path.join(shots, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
	const mp4 = path.join(out, 'parademon_commander_dive_strike.mp4');
	execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf', '-b:v', '5M',
		'-pix_fmt', 'yuv420p', mp4]);
	fs.rmSync(tmp, { recursive: true, force: true });
	const step = Math.max(1, Math.floor(film.length / 12));
	const pick = [];
	for (let i = 0; i < film.length && pick.length < 12; i += step) pick.push(path.join(shots, film[i]));
	execFileSync('node', [sheet, path.join(out, 'parademon_commander_dive_strike_sheet.png'), '4', '0.4', ...pick]);
	console.log('film frames', film.length);
}
console.log('done ->', out);
