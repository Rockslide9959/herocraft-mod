// v0.17.5 Symbiote tendril vfx + exact colours: flattens SymbioteVfxHarness output (docs/screenshots/v0175/symbiote_tendril_vfx/raw/
// screenshots) into the folder, encodes the films as MP4 + frame sheets (never GIF) and deletes the film frames.
//   node scratchpad/symbiote/films_tendril_vfx_v0175.js
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = path.join(__dirname, '..', '..');
const out = path.join(root, 'docs', 'screenshots', 'v0175', 'symbiote_tendril_vfx');
const raw = path.join(out, 'raw');
const shots = path.join(raw, 'screenshots');
const sheet = path.join(root, 'scratchpad', 'contact_sheet.js');

const files = fs.readdirSync(shots).filter((f) => f.endsWith('.png')).sort();
for (const f of files.filter((f) => !f.startsWith('film_'))) fs.copyFileSync(path.join(shots, f), path.join(out, f));
for (const f of fs.readdirSync(raw).filter((f) => f.startsWith('bake_'))) fs.copyFileSync(path.join(raw, f), path.join(out, f));

function film(prefix, name, fps, sheetCount, scale, max) {
	const frames = files.filter((f) => f.startsWith(prefix)).slice(0, max || 100000);
	if (!frames.length) { console.log('no frames for', prefix); return; }
	const tmp = path.join(raw, '_seq_' + name);
	fs.rmSync(tmp, { recursive: true, force: true });
	fs.mkdirSync(tmp);
	frames.forEach((f, i) => fs.copyFileSync(path.join(shots, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
	execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', String(fps), '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf',
		'-b:v', '6M', '-pix_fmt', 'yuv420p', path.join(out, name + '.mp4')]);
	const pick = [];
	for (let k = 0; k < sheetCount; k++) pick.push(path.join(shots, frames[Math.min(frames.length - 1, Math.round(k * (frames.length - 1) / (sheetCount - 1)))]));
	execFileSync('node', [sheet, path.join(out, name + '_sheet.png'), '4', String(scale), ...pick]);
	console.log(name, frames.length, 'frames');
}

film('film_moves_', 'symbiote_tendril_vfx_all_moves', 20, 32, 0.4);
// the Lunge close-ups play at a quarter speed (5 fps of 20 fps frames): the push-off lasts about half a second
film('film_lunge_', 'symbiote_lunge_leg_tendrils_closeup', 5, 16, 0.6, 30);
film('film_lungeback_', 'symbiote_lunge_leg_tendrils_from_behind', 5, 12, 0.6, 26);
fs.rmSync(raw, { recursive: true, force: true });
