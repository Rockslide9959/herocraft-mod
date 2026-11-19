// v0.17.5 backpacks over capes and wings: BackpackCapeHarness output (run/bpcape_out/screenshots) -> docs/screenshots/v0175/backpack_capes:
// the stills, and each kept film as an MP4 + a 16-frame sheet.   node scratchpad/backpack_capes_film_v0175.js   (from the checkout root)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = process.cwd();
const shots = path.join(root, 'run', 'bpcape_out', 'screenshots');
const out = path.join(root, 'docs', 'screenshots', 'v0175', 'backpack_capes');
// Blue Beetle / Cyborg never took off in the harness (wings / jetpack not shown), and the elytra never started gliding: not kept
const skip = /^(06_blue_beetle|14_cyborg)/;
const films = ['film_thor_cape_walk_sprint_fly', 'film_thor_cape_back', 'film_moon_knight_cape', 'film_falcon_wings_flight'];
fs.mkdirSync(out, { recursive: true });
for (const f of fs.readdirSync(shots).filter(f => f.endsWith('.png') && !skip.test(f))) fs.copyFileSync(path.join(shots, f), path.join(out, f));
for (const dir of films) {
	const film = path.join(shots, dir);
	const frames = fs.readdirSync(film).filter(f => /^f\d{3}\.png$/.test(f)).sort();
	const name = dir.replace(/^film_/, '');
	const pick = [];
	for (let i = 0; i < 16; i++) pick.push(path.join(film, frames[Math.round(i * (frames.length - 1) / 15)]));
	execFileSync('node', [path.join(root, 'scratchpad', 'contact_sheet.js'), path.join(out, name + '_sheet.png'), '4', '0.5', ...pick]);
	execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', path.join(film, 'f%03d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf',
		'-b:v', '4M', '-pix_fmt', 'yuv420p', path.join(out, name + '.mp4')]);
}
console.log(fs.readdirSync(out).length, 'files in', out);
