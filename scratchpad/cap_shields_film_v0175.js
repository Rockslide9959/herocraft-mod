// v0.17.5 Captain America shields: ShieldHarness output (run/capshield_out/{before,after}) -> docs/screenshots/v0175/cap_shields:
// before/ and after/ stills as they are; every after film as an MP4 + a frame sheet; the before films as frame sheets only.
//   node scratchpad/cap_shields_film_v0175.js      (from the checkout root; CAP_SRC=reshoot copies run/capshield_out/reshoot over after/)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = process.cwd();
const outRoot = path.join(root, 'docs', 'screenshots', 'v0175', 'cap_shields');
const sheet = (film, frames, file, n) => {
	const pick = [];
	for (let i = 0; i < n; i++) pick.push(path.join(film, frames[Math.round(i * (frames.length - 1) / (n - 1))]));
	execFileSync('node', [path.join(root, 'scratchpad', 'contact_sheet.js'), file, '4', '0.5', ...pick]);
};
for (const phase of process.env.CAP_SRC ? ['after'] : ['before', 'after']) {
	const shots = path.join(root, 'run', 'capshield_out', phase === 'after' && process.env.CAP_SRC ? process.env.CAP_SRC : phase, 'screenshots');
	if (!fs.existsSync(shots)) continue;
	const out = path.join(outRoot, phase);
	fs.mkdirSync(out, { recursive: true });
	for (const f of fs.readdirSync(shots).filter(f => f.endsWith('.png'))) fs.copyFileSync(path.join(shots, f), path.join(out, f));
	for (const dir of fs.readdirSync(shots).filter(d => d.startsWith('film_'))) {
		const film = path.join(shots, dir);
		const frames = fs.readdirSync(film).filter(f => /^f\d{3}\.png$/.test(f)).sort();
		if (frames.length < 4) continue;
		const name = dir.replace(/^film_/, '');
		sheet(film, frames, path.join(out, name + '_sheet.png'), 16);
		if (phase !== 'after') continue;
		const tmp = path.join(film, '_seq');
		fs.rmSync(tmp, { recursive: true, force: true });
		fs.mkdirSync(tmp);
		frames.forEach((f, i) => fs.copyFileSync(path.join(film, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
		execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf', '-b:v', '4M',
			'-pix_fmt', 'yuv420p', path.join(out, name + '.mp4')]);
		fs.rmSync(tmp, { recursive: true, force: true });
	}
	console.log(phase, fs.readdirSync(out).length, 'files');
}
