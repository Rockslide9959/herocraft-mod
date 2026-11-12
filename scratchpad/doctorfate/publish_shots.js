// node scratchpad/doctorfate/publish_shots.js <ffmpeg.exe> -- run/screenshots/df_* -> docs/screenshots/v0173/doctorfate/
// stills are copied; each clip (df_<clip>_NNNN.png) becomes <clip>.mp4 (20 fps, or 10 fps for every-2-ticks clips) plus a
// <clip>_sheet.png frame sheet of 12 evenly spaced frames.
'use strict';
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const ffmpeg = process.argv[2];
const SRC = 'run/screenshots';
const OUT = 'docs/screenshots/v0173/doctorfate';
fs.mkdirSync(OUT, { recursive: true });
const files = fs.readdirSync(SRC).filter(f => f.startsWith('df_') && f.endsWith('.png'));
const clips = {};
for (const f of files) {
	const m = f.match(/^df_(.+)_(\d{4})\.png$/);
	if (m) {
		(clips[m[1]] = clips[m[1]] || []).push(f);
	} else {
		fs.copyFileSync(path.join(SRC, f), path.join(OUT, f.slice(3)));
		console.log('still', f.slice(3));
	}
}
const fps = { helmet_on: 20, spells: 20, avatar: 10, possession: 10 };
for (const [clip, list] of Object.entries(clips)) {
	list.sort();
	execFileSync(ffmpeg, ['-y', '-loglevel', 'error', '-framerate', String(fps[clip] || 20), '-i', path.join(SRC, 'df_' + clip + '_%04d.png'),
		'-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-vf', 'scale=trunc(iw/2)*2:trunc(ih/2)*2', path.join(OUT, clip + '.mp4')]);
	const n = Math.min(12, list.length);
	const pick = [];
	for (let i = 0; i < n; i++) pick.push(path.join(SRC, list[Math.round(i * (list.length - 1) / Math.max(1, n - 1))]));
	execFileSync('node', ['scratchpad/contact_sheet.js', path.join(OUT, clip + '_sheet.png'), '4', '0.5', ...pick]);
	console.log('clip', clip, list.length, 'frames');
}
