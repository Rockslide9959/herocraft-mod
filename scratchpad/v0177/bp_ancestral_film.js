// v0.17.7 Black Panther Ancestral Plane: BpAncestralHarness frames -> docs/screenshots/v0177/bp_ancestral/
//   bp_ancestral_full.mp4 (every frame, 20 fps), bp_ancestral_full_sheet.png (16 frames across the run), per-part frame sheets
//   (trial, parkour), highlight stills NN_<name>.png picked from the harness marks, and stills_contact_sheet.png. Never a GIF.
//   node scratchpad/v0177/bp_ancestral_film.js <frames dir>   (from the worktree root)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = process.cwd();
const shots = process.argv[2];
const sheet = path.join(root, 'scratchpad', 'contact_sheet.js');
const out = path.join(root, 'docs', 'screenshots', 'v0177', 'bp_ancestral');
fs.mkdirSync(out, { recursive: true });

const frames = fs.readdirSync(shots).filter(f => /^film_\d+\.png$/.test(f)).sort();
if (!frames.length) throw new Error('no frames in ' + shots);
const marks = {};
const mf = path.join(shots, 'marks.txt');
if (fs.existsSync(mf)) for (const line of fs.readFileSync(mf, 'utf8').split(/\r?\n/)) { const [k, v] = line.trim().split(/\s+/); if (k && marks[k] === undefined) marks[k] = +v; }

const tmp = path.join(shots, '_seq');
fs.rmSync(tmp, { recursive: true, force: true });
fs.mkdirSync(tmp);
frames.forEach((f, i) => fs.copyFileSync(path.join(shots, f), path.join(tmp, String(i).padStart(5, '0') + '.png')));
const mp4 = path.join(out, 'bp_ancestral_full.mp4');
execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', path.join(tmp, '%05d.png'), '-vf', 'scale=960:-2', '-c:v', 'h264_mf', '-b:v', '1100k',
	'-pix_fmt', 'yuv420p', mp4]);
fs.rmSync(tmp, { recursive: true, force: true });
console.log('film', frames.length, 'frames,', (frames.length / 20).toFixed(1), 's ->', mp4);

const at = (i) => path.join(shots, frames[Math.max(0, Math.min(frames.length - 1, i))]);
const pick = [];
for (let i = 0; i < 16; i++) pick.push(at(Math.round(i * (frames.length - 1) / 15)));
execFileSync('node', [sheet, path.join(out, 'bp_ancestral_full_sheet.png'), '4', '0.35', ...pick]);
const run = (from, n, step) => Array.from({ length: n }, (_, i) => at(from + i * step));
if (marks.trial !== undefined && marks.path !== undefined) {
	const len = marks.path - marks.trial;
	execFileSync('node', [sheet, path.join(out, 'trial_sheet.png'), '4', '0.35', ...run(marks.trial + 10, 16, Math.max(1, Math.floor(len / 17)))]);
}
if (marks.parkour !== undefined && marks.finale !== undefined) {
	const len = marks.finale - marks.parkour;
	execFileSync('node', [sheet, path.join(out, 'parkour_sheet.png'), '4', '0.35', ...run(marks.parkour + 2, 16, Math.max(1, Math.floor(len / 17)))]);
}

const STILLS = [
	['01_falling_asleep', 'bed', 60], ['02_arrival_high_over_the_savanna', 'arrival', 40], ['03_arrival_gliding_down', 'arrival', 118],
	['04_walking_the_savanna_motes', 'walk', 30], ['05_ancestors_under_the_great_acacia', 'walk', 95], ['06_greeting_wide', 'greeting', 60],
	['07_greeting_the_eldest', 'greeting', 140], ['08_greeting_reverse', 'greeting', 215], ['09_greeting_crane', 'greeting', 300],
	['10_procession', 'procession', 80], ['11_shrine_from_above', 'procession', 165], ['12_shrine_entrance', 'procession', 230],
	['13_inside_the_shrine', 'shrine', 40], ['14_statue_hero_shot', 'intro', 30], ['15_statue_face', 'intro', 90], ['16_over_the_statue', 'intro', 140],
	['17_shades_gather', 'intro', 190], ['18_trial_shade_stalks', 'trial', 70], ['19_trial_fight', 'trial', 220], ['20_trial_late', 'trial', 600],
	['21_path_forming', 'path', 70], ['22_path_forming_far', 'path', 130], ['23_parkour_start', 'parkour', 6], ['24_parkour_mid', 'parkour', 140],
	['25_parkour_fall', 'fall', 12], ['26_parkour_caught', 'caught', 6], ['27_parkour_high', 'caught', 200], ['28_finale_sun', 'finale', 30],
	['29_finale_bast_spirit', 'finale', 100], ['30_finale_spirit_close', 'finale', 170], ['31_finale_farewell', 'finale', 225], ['32_finale_gold', 'finale', 262],
	['33_waking_in_bed', 'wake', 40], ['34_black_panther_title', 'wake', 90],
];
const stills = [];
for (const [name, mark, off] of STILLS) {
	if (marks[mark] === undefined) continue;
	const dst = path.join(out, name + '.png');
	fs.copyFileSync(at(marks[mark] + off), dst);
	stills.push(dst);
}
if (stills.length) execFileSync('node', [sheet, path.join(out, 'stills_contact_sheet.png'), '4', '0.3', ...stills]);
console.log('stills', stills.length, '->', out);
