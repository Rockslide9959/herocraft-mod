// v0.17.5 beam clash rework: BeamClashHarness2 output (run/beamclash2_out/screenshots) -> docs/screenshots/v0175/beam_clash2:
// the stills, and every film_* as an MP4 + a 16-frame sheet (never a GIF).
//   node scratchpad/beam_clash2_film_v0175.js      (from the checkout root)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = process.cwd();
const shots = path.join(root, 'run', 'beamclash2_out', 'screenshots');
const out = path.join(root, 'docs', 'screenshots', 'v0175', 'beam_clash2');
const CS = path.join(root, 'scratchpad', 'contact_sheet.js');
fs.mkdirSync(out, { recursive: true });
for (const f of fs.readdirSync(shots).filter(f => f.endsWith('.png'))) fs.copyFileSync(path.join(shots, f), path.join(out, f));
for (const dir of fs.readdirSync(shots).filter(d => d.startsWith('film_'))) {
	const film = path.join(shots, dir);
	// the harness halts the client while the last screenshots may still be writing: keep only complete PNGs (ending in IEND)
	const frames = fs.readdirSync(film).filter(f => /^f\d{3}\.png$/.test(f)
		&& fs.readFileSync(path.join(film, f)).subarray(-8, -4).toString() === 'IEND').sort();
	if (frames.length < 4) continue;
	const name = dir.replace(/^film_/, 'clash_');
	const pick = [];
	for (let i = 0; i < 16; i++) pick.push(path.join(film, frames[Math.round(i * (frames.length - 1) / 15)]));
	execFileSync('node', [CS, path.join(out, name + '_sheet.png'), '4', '0.5', ...pick]);
	const tmp = path.join(film, '_seq');
	fs.rmSync(tmp, { recursive: true, force: true });
	fs.mkdirSync(tmp);
	frames.forEach((f, i) => fs.copyFileSync(path.join(film, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
	// one frame every 2 ticks = 10 fps of game time
	execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '10', '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=960:-2', '-c:v', 'h264_mf', '-b:v', '4M',
		'-pix_fmt', 'yuv420p', path.join(out, name + '.mp4')]);
	fs.rmSync(tmp, { recursive: true, force: true });
}
console.log('done', out);
