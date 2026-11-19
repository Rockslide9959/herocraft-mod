// v0.17.5 Symbiote weapons + wings + slim arms: copies SymWWHarness stills into docs/screenshots/v0175/symbiote_weapons_wings/, builds the
// wide-vs-slim side-by-side sheets, encodes the films as MP4 + frame sheets (never GIF).
//   node scratchpad/symbiote/films_weapons_wings_v0175.js <harness out dir>   (the dir holding screenshots/)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = path.join(__dirname, '..', '..');
const out = path.join(root, 'docs', 'screenshots', 'v0175', 'symbiote_weapons_wings');
const shots = path.join(process.argv[2], 'screenshots');
const sheet = path.join(root, 'scratchpad', 'contact_sheet.js');
fs.mkdirSync(out, { recursive: true });

const files = fs.readdirSync(shots).filter((f) => f.endsWith('.png')).sort();
for (const f of files.filter((f) => !f.startsWith('film_'))) fs.copyFileSync(path.join(shots, f), path.join(out, f));

// wide vs slim, side by side (wide left, slim right)
for (const colour of ['classic', 'carnage']) {
	for (const view of ['1_front', '2_back', '3_front_right_closeup', '4_first_person']) {
		const a = path.join(shots, `arms_wide_${colour}_${view}.png`), b = path.join(shots, `arms_slim_${colour}_${view}.png`);
		if (fs.existsSync(a) && fs.existsSync(b)) execFileSync('node', [sheet, path.join(out, `wide_vs_slim_${colour}_${view}.png`), '2', '0.6', a, b]);
	}
}

function film(prefix, name, fps, sheetCount, scale) {
	const frames = files.filter((f) => f.startsWith('film_' + prefix + '_'));
	if (!frames.length) { console.log('no frames for', prefix); return; }
	const tmp = path.join(process.argv[2], '_seq_' + name);
	fs.rmSync(tmp, { recursive: true, force: true });
	fs.mkdirSync(tmp);
	frames.forEach((f, i) => fs.copyFileSync(path.join(shots, f), path.join(tmp, String(i).padStart(4, '0') + '.png')));
	execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', String(fps), '-i', path.join(tmp, '%04d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf',
		'-b:v', '6M', '-pix_fmt', 'yuv420p', path.join(out, name + '.mp4')]);
	const pick = [];
	for (let k = 0; k < sheetCount; k++) pick.push(path.join(shots, frames[Math.min(frames.length - 1, Math.round(k * (frames.length - 1) / (sheetCount - 1)))]));
	execFileSync('node', [sheet, path.join(out, name + '_sheet.png'), '4', String(scale), ...pick]);
	fs.rmSync(tmp, { recursive: true, force: true });
	console.log(name, frames.length, 'frames');
}

// the V cycle and the Z moves at half speed (10 fps of 20 fps frames) so the 6-tick growth and the swings read
film('cycle', 'symbiote_weapon_cycle_growth_melt', 10, 24, 0.4);
film('z1', 'symbiote_weapon_z_blade_slash', 10, 12, 0.5);
film('z2', 'symbiote_weapon_z_cleave', 10, 12, 0.5);
film('z3', 'symbiote_weapon_z_ground_smash', 10, 12, 0.5);
film('z4', 'symbiote_weapon_z_lash', 10, 12, 0.5);
film('wings_behind', 'symbiote_wings_flight_behind', 20, 24, 0.4);
film('wings_side', 'symbiote_wings_flight_side', 20, 24, 0.4);
