// v0.17.5 Symbiote moveset v3: flattens SymbioteMovesetV3Harness output (docs/screenshots/v0175/symbiote_moveset3/screenshots) and encodes
// each film as MP4 + a frame sheet (never GIF): the Thorns aura + Spike Surge, Spike Nova (also at half speed) and the grab. Film frames
// are deleted afterwards.
//   node scratchpad/symbiote/films_moveset3_v0175.js
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = path.join(__dirname, '..', '..');
const out = path.join(root, 'docs', 'screenshots', 'v0175', 'symbiote_moveset3');
const shots = path.join(out, 'screenshots');
const sheet = path.join(root, 'scratchpad', 'contact_sheet.js');

const files = fs.readdirSync(shots).filter((f) => f.endsWith('.png')).sort();
for (const f of files.filter((f) => !f.startsWith('film_'))) fs.copyFileSync(path.join(shots, f), path.join(out, f));

function film(prefix, name, fps, sheetCount, scale) {
	const frames = files.filter((f) => f.startsWith('film_' + prefix + '_'));
	if (!frames.length) return;
	const tmp = path.join(shots, '_seq_' + name);
	fs.rmSync(tmp, { recursive: true, force: true });
	fs.mkdirSync(tmp);
	frames.forEach((f, i) => fs.copyFileSync(path.join(shots, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
	execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', String(fps), '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf',
		'-b:v', '6M', '-pix_fmt', 'yuv420p', path.join(out, name + '.mp4')]);
	const pick = [];
	for (let k = 0; k < sheetCount; k++) pick.push(path.join(shots, frames[Math.min(frames.length - 1, Math.round(k * (frames.length - 1) / (sheetCount - 1)))]));
	execFileSync('node', [sheet, path.join(out, name + '_sheet.png'), '4', String(scale), ...pick]);
	console.log(name, frames.length, 'frames');
}

film('thorns', 'symbiote_thorns_aura_spike_surge', 20, 16, 0.5);
film('nova', 'symbiote_spike_nova', 20, 12, 0.5);
film('nova', 'symbiote_spike_nova_half_speed', 10, 12, 0.5);
film('grab', 'symbiote_grab_hold_crush_setdown_throw', 20, 20, 0.45);
fs.rmSync(shots, { recursive: true, force: true });
