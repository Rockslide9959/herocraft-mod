// v0.17.7: before | after first-person Laser Vision wobble film -> docs/screenshots/v0177/eye_beams_fp/
const fs = require('fs'), path = require('path'), { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const out = 'docs/screenshots/v0177/eye_beams_fp';
fs.mkdirSync(out, { recursive: true });
const dir = p => `run/eyebeam_out/${p}/screenshots/film_laser_fp_walk`;
const args = ['-y', '-loglevel', 'error', '-framerate', '20', '-i', path.join(dir('v0177_before'), 'f%03d.png'), '-framerate', '20', '-i', path.join(dir('v0177_after'), 'f%03d.png'),
	'-filter_complex', "[0:v]scale=640:-2,drawtext=text='BEFORE':x=12:y=12:fontsize=26:fontcolor=white:box=1:boxcolor=black@0.5[a];[1:v]scale=640:-2,drawtext=text='AFTER':x=12:y=12:fontsize=26:fontcolor=white:box=1:boxcolor=black@0.5[b];[a][b]hstack",
	'-c:v', 'h264_mf', '-b:v', '6M', '-pix_fmt', 'yuv420p', path.join(out, 'laser_fp_walk_before_after.mp4')];
try { execFileSync(FF, args); } catch (e) {
	// no drawtext font: plain side by side
	args[args.indexOf('-filter_complex') + 1] = '[0:v]scale=640:-2[a];[1:v]scale=640:-2[b];[a][b]hstack';
	execFileSync(FF, args);
}
for (const ph of ['v0177_before', 'v0177_after']) {
	const fr = fs.readdirSync(dir(ph)).filter(f => /^f\d{3}\.png$/.test(f)).sort();
	const pick = [];
	for (let i = 0; i < 16; i++) pick.push(path.join(dir(ph), fr[Math.round(i * (fr.length - 1) / 15)]));
	execFileSync('node', ['scratchpad/contact_sheet.js', path.join(out, `laser_fp_walk_${ph.replace('v0177_', '')}_sheet.png`), '4', '0.5', ...pick]);
}
console.log('done');
