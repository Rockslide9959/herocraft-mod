// v0.17.7 the Destroyer: copy DestroyerHarness stills into docs/screenshots/v0177/destroyer/ and encode each film
// (film_<name>_NNN.png) into an MP4 + a frame sheet (never a GIF). Also a contact sheet of the stills.
//   node scratchpad/v0177/destroyer_films_v0177.js <shots dir>   (from the worktree root)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = process.cwd();
const shots = process.argv[2];
const out = path.join(root, 'docs', 'screenshots', 'v0177', 'destroyer');
fs.mkdirSync(out, { recursive: true });
const sheet = path.join(root, 'scratchpad', 'contact_sheet.js');
// name in the frames -> [output name, fps]
const FILMS = {
	arrival: ['destroyer_arrival', 20],
	faceplate: ['destroyer_faceplate_opening', 12],
	beam: ['destroyer_disintegration_beam', 10],
	slam_charge: ['destroyer_slam_and_charge', 10],
	walk: ['destroyer_walk', 20],
	blast_backhand: ['destroyer_fire_blast_and_backhand', 16],
	phase2: ['destroyer_phase2_unleashed', 12],
	death: ['destroyer_death', 20],
};

const files = fs.readdirSync(shots).filter(f => f.endsWith('.png')).sort();
const stills = files.filter(f => !f.startsWith('film_'));
for (const f of stills) fs.copyFileSync(path.join(shots, f), path.join(out, f));
if (stills.length) execFileSync('node', [sheet, path.join(out, 'stills_contact_sheet.png'), '3', '0.4', ...stills.map(f => path.join(shots, f))]);
for (const [key, [name, fps]] of Object.entries(FILMS)) {
	const film = files.filter(f => f.startsWith('film_' + key + '_'));
	if (!film.length) continue;
	const tmp = path.join(shots, '_seq_' + key);
	fs.rmSync(tmp, { recursive: true, force: true });
	fs.mkdirSync(tmp);
	film.forEach((f, i) => fs.copyFileSync(path.join(shots, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
	const mp4 = path.join(out, name + '.mp4');
	execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', String(fps), '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf', '-b:v', '5M',
		'-pix_fmt', 'yuv420p', mp4]);
	fs.rmSync(tmp, { recursive: true, force: true });
	const step = Math.max(1, Math.floor(film.length / 12));
	const pick = [];
	for (let i = 0; i < film.length && pick.length < 12; i += step) pick.push(path.join(shots, film[i]));
	execFileSync('node', [sheet, path.join(out, name + '_sheet.png'), '4', '0.4', ...pick]);
	console.log(name, film.length, 'frames');
}
console.log('done ->', out);
