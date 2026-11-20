// v0.17.5 Symbiote Monster SHOWCASE (the user's "video of the transformation and monster moves"): encodes the SymbioteMonsterShowcaseHarness
// films into docs/screenshots/v0175/symbiote_monster_showcase/ as H.264 yuv420p MP4 + PNG frame sheets (never GIF). Each film plays back at
// the rate it was captured (frames / wall-clock ms from the harness's film_<name>.meta), so real-time films run in real time.
//   node scratchpad/symbiote/films_monster_showcase_v0175.js <harness out dir>   (the dir holding screenshots/)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = path.join(__dirname, '..', '..');
const out = path.join(root, 'docs', 'screenshots', 'v0175', 'symbiote_monster_showcase');
const shots = path.join(process.argv[2], 'screenshots');
const sheet = path.join(root, 'scratchpad', 'contact_sheet.js');
fs.mkdirSync(out, { recursive: true });
const files = fs.readdirSync(shots).filter((f) => f.endsWith('.png')).sort();

function film(prefix, name, sheetCount) {
	const frames = files.filter((f) => new RegExp('^film_' + prefix + '_\\d+\\.png$').test(f));
	if (!frames.length) { console.log('no frames for', prefix); return; }
	let fps = 30;
	const meta = path.join(shots, 'film_' + prefix + '.meta');
	if (fs.existsSync(meta)) {
		const [n, ms] = fs.readFileSync(meta, 'utf8').trim().split(/\s+/).map(Number);
		if (n > 0 && ms > 0) fps = Math.max(5, Math.min(60, n / (ms / 1000)));
	}
	const tmp = path.join(process.argv[2], '_seq_' + name);
	fs.rmSync(tmp, { recursive: true, force: true });
	fs.mkdirSync(tmp);
	frames.forEach((f, i) => fs.copyFileSync(path.join(shots, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
	execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', fps.toFixed(3), '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2,fps=30',
		'-c:v', 'h264_mf', '-b:v', '6M', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', path.join(out, name + '.mp4')]);
	const pick = [];
	for (let k = 0; k < sheetCount; k++) pick.push(path.join(shots, frames[Math.min(frames.length - 1, Math.round(k * (frames.length - 1) / (sheetCount - 1)))]));
	execFileSync('node', [sheet, path.join(out, name + '_sheet.png'), '4', '0.4', ...pick]);
	fs.rmSync(tmp, { recursive: true, force: true });
	console.log(name, frames.length, 'frames at', fps.toFixed(1), 'fps =', (frames.length / fps).toFixed(1), 's');
}

film('transform', 'transformation', 16);
film('cap_moves', 'monster_moves', 24);
film('melt', 'melt_back', 12);
film('transform_slowmo', 'cocoon_slowmo', 16);
