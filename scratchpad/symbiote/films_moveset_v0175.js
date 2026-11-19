// v0.17.5 Symbiote moveset v2: flattens SymbioteMovesetHarness output (docs/screenshots/v0175/symbiote_moveset2/screenshots) and encodes
// the film as MP4 + a frame sheet (never GIF), plus the Maw Bite / Devour close-up as its own MP4 + sheet; the film frames are deleted.
//   node scratchpad/symbiote/films_moveset_v0175.js
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = path.join(__dirname, '..', '..');
const out = path.join(root, 'docs', 'screenshots', 'v0175', 'symbiote_moveset2');
const shots = path.join(out, 'screenshots');
const sheet = path.join(root, 'scratchpad', 'contact_sheet.js');

const files = fs.readdirSync(shots).filter((f) => f.endsWith('.png')).sort();
for (const f of files.filter((f) => !f.startsWith('film_'))) fs.copyFileSync(path.join(shots, f), path.join(out, f));
const list = files.filter((f) => f.startsWith('film_moves_'));

function film(frames, name, fps, sheetCount, scale) {
	const tmp = path.join(shots, '_seq_' + name);
	fs.rmSync(tmp, { recursive: true, force: true });
	fs.mkdirSync(tmp);
	frames.forEach((f, i) => fs.copyFileSync(path.join(shots, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
	execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', String(fps), '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf',
		'-b:v', '6M', '-pix_fmt', 'yuv420p', path.join(out, name + '.mp4')]);
	const pick = [];
	for (let k = 0; k < sheetCount; k++) pick.push(path.join(shots, frames[Math.min(frames.length - 1, Math.round(k * (frames.length - 1) / (sheetCount - 1)))]));
	execFileSync('node', [sheet, path.join(out, name + '_sheet.png'), '4', String(scale), ...pick]);
}

film(list, 'symbiote_moveset_v2_all_moves', 20, 24, 0.4);
// the close-up: Maw Bite then Devour (film frames 316..405 in the harness timeline)
film(list.slice(316, 406), 'symbiote_maw_bite_devour_closeup', 10, 16, 0.6);
fs.rmSync(shots, { recursive: true, force: true });
console.log('film frames', list.length);
