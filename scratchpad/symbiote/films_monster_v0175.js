// v0.17.5 Symbiote Monster form: copies SymbioteMonsterHarness stills into docs/screenshots/v0175/symbiote_monster/ and encodes the
// films as MP4 + PNG frame sheets (never GIF).
//   node scratchpad/symbiote/films_monster_v0175.js <harness out dir>   (the dir holding screenshots/)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = path.join(__dirname, '..', '..');
const out = path.join(root, 'docs', 'screenshots', 'v0175', 'symbiote_monster');
const shots = path.join(process.argv[2], 'screenshots');
const sheet = path.join(root, 'scratchpad', 'contact_sheet.js');
fs.mkdirSync(out, { recursive: true });

const files = fs.readdirSync(shots).filter((f) => f.endsWith('.png')).sort();
for (const f of files.filter((f) => !f.startsWith('film_'))) fs.copyFileSync(path.join(shots, f), path.join(out, f));

function film(prefix, name, fps, sheetCount, scale, skip = 0) {
	const frames = files.filter((f) => new RegExp('^film_' + prefix + '_\\d+\\.png$').test(f)).slice(skip);
	if (!frames.length) { console.log('no frames for', prefix); return; }
	const tmp = path.join(process.argv[2], '_seq_' + name);
	fs.rmSync(tmp, { recursive: true, force: true });
	fs.mkdirSync(tmp);
	frames.forEach((f, i) => fs.copyFileSync(path.join(shots, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
	execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', String(fps), '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf',
		'-b:v', '6M', '-pix_fmt', 'yuv420p', path.join(out, name + '.mp4')]);
	const pick = [];
	for (let k = 0; k < sheetCount; k++) pick.push(path.join(shots, frames[Math.min(frames.length - 1, Math.round(k * (frames.length - 1) / (sheetCount - 1)))]));
	execFileSync('node', [sheet, path.join(out, name + '_sheet.png'), '4', String(scale), ...pick]);
	fs.rmSync(tmp, { recursive: true, force: true });
	console.log(name, frames.length, 'frames');
}

// every rendered frame (~30 fps; the slow-motion one at 6 tps)
film('transform', 'symbiote_monster_transformation', 30, 16, 0.4);
film('transform_slowmo', 'symbiote_monster_cocoon_slowmo', 30, 16, 0.4);
film('attacks', 'symbiote_monster_all_moves', 30, 20, 0.4);
film('melt', 'symbiote_monster_melt_back', 30, 12, 0.4);
