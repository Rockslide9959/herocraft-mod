// v0.17.7 Bifrost Travel cinematic + Stormbreaker clipping fix: films (MP4 + frame sheet each, never GIF) and stills from the
// BifrostHarness run -> docs/screenshots/v0177/bifrost/, plus before/after comparison sheets of the in-game close-ups.
//   node scratchpad/v0177/bifrost_films.js [run/bifrost_out]
const fs = require('fs'), path = require('path'), { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const src = path.join(process.argv[2] || 'run/bifrost_out', 'screenshots');
const out = 'docs/screenshots/v0177/bifrost';
fs.mkdirSync(out, { recursive: true });
for (const d of fs.readdirSync(src)) {
	const full = path.join(src, d);
	if (fs.statSync(full).isDirectory()) {
		if (!d.startsWith('film_')) continue;
		const fr = fs.readdirSync(full).filter((f) => /^f\d{3}\.png$/.test(f)).sort();
		if (fr.length < 2) continue;
		const name = d.replace(/^film_/, '');
		// one frame per game tick: 20 fps is real time
		execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', path.join(full, 'f%03d.png'), '-vf', 'scale=854:-2',
			'-c:v', 'h264_mf', '-b:v', '6M', '-pix_fmt', 'yuv420p', path.join(out, name + '.mp4')]);
		const n = Math.min(20, fr.length);
		const pick = [];
		for (let i = 0; i < n; i++) pick.push(path.join(full, fr[Math.round(i * (fr.length - 1) / (n - 1))]));
		execFileSync('node', ['scratchpad/contact_sheet.js', path.join(out, name + '_frames.png'), '5', '0.4', ...pick]);
		console.log('film', name, fr.length, 'frames');
	} else if (d.endsWith('.png')) {
		fs.copyFileSync(full, path.join(out, d));
		console.log('still', d);
	}
}
// before (top row) / after (bottom row) in-game close-ups
const views = ['right_cq', 'right_chead', 'right_front', 'ground'];
const pairs = [...views.map((v) => path.join(out, 'clip_before_' + v + '.png')), ...views.map((v) => path.join(out, 'clip_after_' + v + '.png'))];
if (pairs.every((f) => fs.existsSync(f))) {
	execFileSync('node', ['scratchpad/contact_sheet.js', path.join(out, 'clip_ingame_before_after.png'), String(views.length), '0.5', ...pairs]);
	console.log('sheet clip_ingame_before_after.png');
}
