// v0.17.7: Thor polish films (MP4 + frame sheet each) and stills -> docs/screenshots/v0177/thor_polish/
//   node scratchpad/v0177/thor_polish_film.js [run/thorpolish_out]
const fs = require('fs'), path = require('path'), { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const src = path.join(process.argv[2] || 'run/thorpolish_out', 'screenshots');
const out = 'docs/screenshots/v0177/thor_polish';
fs.mkdirSync(out, { recursive: true });
for (const d of fs.readdirSync(src)) {
	const full = path.join(src, d);
	if (fs.statSync(full).isDirectory()) {
		if (!d.startsWith('film_')) continue;
		const fr = fs.readdirSync(full).filter(f => /^f\d{3}\.png$/.test(f)).sort();
		if (fr.length < 2) continue;
		const name = d.replace(/^film_/, '');
		execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', path.join(full, 'f%03d.png'), '-vf', 'scale=854:-2',
			'-c:v', 'h264_mf', '-b:v', '6M', '-pix_fmt', 'yuv420p', path.join(out, name + '.mp4')]);
		const n = Math.min(16, fr.length);
		const pick = [];
		for (let i = 0; i < n; i++) pick.push(path.join(full, fr[Math.round(i * (fr.length - 1) / (n - 1))]));
		execFileSync('node', ['scratchpad/contact_sheet.js', path.join(out, name + '_sheet.png'), '4', '0.5', ...pick]);
		console.log('film', name, fr.length, 'frames');
	} else if (d.endsWith('.png')) {
		fs.copyFileSync(full, path.join(out, d));
		console.log('still', d);
	}
}
