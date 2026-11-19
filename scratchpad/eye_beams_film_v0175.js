// v0.17.5 eye beams: EyeBeamHarness output (run/eyebeam_out/{before,after}) -> docs/screenshots/v0175/eye_beams:
// before/ and after/ stills; a before|after comparison sheet per still; every after film as an MP4 + a 16-frame sheet.
//   node scratchpad/eye_beams_film_v0175.js      (from the checkout root)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = process.cwd();
const outRoot = path.join(root, 'docs', 'screenshots', 'v0175', 'eye_beams');
const CS = path.join(root, 'scratchpad', 'contact_sheet.js');
for (const phase of ['before', 'after']) {
	const shots = path.join(root, 'run', 'eyebeam_out', phase, 'screenshots');
	if (!fs.existsSync(shots)) continue;
	const out = path.join(outRoot, phase);
	fs.mkdirSync(out, { recursive: true });
	for (const f of fs.readdirSync(shots).filter(f => f.endsWith('.png'))) fs.copyFileSync(path.join(shots, f), path.join(out, f));
	if (phase !== 'after') continue;
	for (const dir of fs.readdirSync(shots).filter(d => d.startsWith('film_'))) {
		const film = path.join(shots, dir);
		const frames = fs.readdirSync(film).filter(f => /^f\d{3}\.png$/.test(f)).sort();
		if (frames.length < 4) continue;
		const name = dir.replace(/^film_/, '');
		const pick = [];
		for (let i = 0; i < 16; i++) pick.push(path.join(film, frames[Math.round(i * (frames.length - 1) / 15)]));
		execFileSync('node', [CS, path.join(out, name + '_sheet.png'), '4', '0.5', ...pick]);
		const tmp = path.join(film, '_seq');
		fs.rmSync(tmp, { recursive: true, force: true });
		fs.mkdirSync(tmp);
		frames.forEach((f, i) => fs.copyFileSync(path.join(film, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
		execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf', '-b:v', '4M',
			'-pix_fmt', 'yuv420p', path.join(out, name + '.mp4')]);
		fs.rmSync(tmp, { recursive: true, force: true });
	}
}
// before | after side by side for every still both runs took
const b = path.join(outRoot, 'before'), a = path.join(outRoot, 'after'), cmp = path.join(outRoot, 'compare');
if (fs.existsSync(b) && fs.existsSync(a)) {
	fs.mkdirSync(cmp, { recursive: true });
	for (const f of fs.readdirSync(b).filter(f => f.endsWith('.png') && fs.existsSync(path.join(a, f)))) {
		execFileSync('node', [CS, path.join(cmp, f), '2', '0.75', path.join(b, f), path.join(a, f)]);
	}
}
console.log('done', outRoot);
