// v0.17.7 Speed Force charge stance: SpeedForceChargeHarness output (run/sfcharge_out/screenshots) ->
// docs/screenshots/v0177/speedforce_charge: the stills, zoomed crops of the pose (+ a 2x2 sheet), the film as an MP4 and a
// 16-frame sheet.   node scratchpad/speedforce_charge_film_v0177.js   (from the checkout root)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const png = require('./pnglib.js');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = process.cwd();
const shots = path.join(root, 'run', 'sfcharge_out', 'screenshots');
const out = path.join(root, 'docs', 'screenshots', 'v0177', 'speedforce_charge');
fs.mkdirSync(out, { recursive: true });

function crop(inp, dst, x, y, w, h, s) {
	const im = png.read(inp), W = w * s, H = h * s, o = Buffer.alloc(W * H * 4);
	for (let j = 0; j < H; j++) for (let i = 0; i < W; i++) {
		const sx = x + Math.floor(i / s), sy = y + Math.floor(j / s);
		im.px.copy(o, (j * W + i) * 4, (sy * im.W + sx) * 4, (sy * im.W + sx) * 4 + 4);
	}
	png.write(dst, W, H, o);
}

const stills = fs.readdirSync(shots).filter(f => f.endsWith('.png'));
for (const f of stills) fs.copyFileSync(path.join(shots, f), path.join(out, f));
// the pose is small in the 854x480 frame: 3x crops round the model
const zoomed = [];
for (const f of ['02_charge_side.png', '03_charge_front.png', '04_charge_three_quarter.png', '05_charge_other_side.png']) {
	const dst = path.join(out, f.replace('.png', '_zoom.png'));
	crop(path.join(shots, f), dst, 307, 150, 240, 150, 3);
	zoomed.push(dst);
}
execFileSync('node', [path.join(root, 'scratchpad', 'contact_sheet.js'), path.join(out, 'charge_pose_sheet.png'), '2', '0.5', ...zoomed]);

const film = path.join(shots, 'film');
const frames = fs.readdirSync(film).filter(f => /^f\d{3}\.png$/.test(f)).sort();
const pick = [];
for (let i = 0; i < 16; i++) pick.push(path.join(film, frames[Math.round(i * (frames.length - 1) / 15)]));
execFileSync('node', [path.join(root, 'scratchpad', 'contact_sheet.js'), path.join(out, 'charge_film_sheet.png'), '4', '0.5', ...pick]);
execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', path.join(film, 'f%03d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf',
	'-b:v', '4M', '-pix_fmt', 'yuv420p', path.join(out, 'charge_film.mp4')]);
console.log(frames.length, 'film frames;', fs.readdirSync(out).length, 'files in', out);
