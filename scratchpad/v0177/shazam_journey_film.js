// v0.17.7 Shazam acquisition: ShazamJourneyHarness frames -> docs/screenshots/v0177/shazam_journey/
//   shazam_journey_full.mp4 (every frame, 20 fps), shazam_journey_full_sheet.png (16 frames across the run),
//   highlight stills NN_<name>.png picked from the harness marks, and stills_contact_sheet.png. Never a GIF.
//   node scratchpad/v0177/shazam_journey_film.js <frames dir>   (from the worktree root)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = process.cwd();
const shots = process.argv[2];
const out = path.join(root, 'docs', 'screenshots', 'v0177', 'shazam_journey');
fs.mkdirSync(out, { recursive: true });
const sheet = path.join(root, 'scratchpad', 'contact_sheet.js');

const frames = fs.readdirSync(shots).filter(f => /^film_journey_\d+\.png$/.test(f)).sort();
if (!frames.length) throw new Error('no frames in ' + shots);
const marks = {};
const mf = path.join(shots, 'marks.txt');
if (fs.existsSync(mf)) for (const line of fs.readFileSync(mf, 'utf8').split(/\r?\n/)) { const [k, v] = line.trim().split(/\s+/); if (k) marks[k] = +v; }

// the film: every frame in order
const tmp = path.join(shots, '_seq');
fs.rmSync(tmp, { recursive: true, force: true });
fs.mkdirSync(tmp);
frames.forEach((f, i) => fs.copyFileSync(path.join(shots, f), path.join(tmp, String(i).padStart(5, '0') + '.png')));
const mp4 = path.join(out, 'shazam_journey_full.mp4');
execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', '20', '-i', path.join(tmp, '%05d.png'), '-vf', 'scale=960:-2', '-c:v', 'h264_mf', '-b:v', '2500k',
	'-pix_fmt', 'yuv420p', mp4]);
fs.rmSync(tmp, { recursive: true, force: true });
console.log('film', frames.length, 'frames,', (frames.length / 20).toFixed(1), 's ->', mp4);

// the frame sheet: 16 frames spread over the whole run
const pick = [];
for (let i = 0; i < 16; i++) pick.push(path.join(shots, frames[Math.min(frames.length - 1, Math.round(i * (frames.length - 1) / 15))]));
execFileSync('node', [sheet, path.join(out, 'shazam_journey_full_sheet.png'), '4', '0.35', ...pick]);

// highlight stills from the marks
const STILLS = [
	['01_illusion_appears', 'illusion', 30], ['02_walking_to_it', 'walk_to_it', 25], ['03_descent_cutscene', 'descent', 36],
	['04_platform_train_lights', 'train_arrives', 70], ['05_train_arrives', 'train_arrives', 132], ['06_boarding', 'board', 14],
	['07_inside_the_car', 'ride', 45], ['08_lamps_streak_past', 'ride', 96], ['09_glyphs', 'ride', 136], ['10_rock_tunnel', 'ride', 178],
	['11_into_the_cave', 'ride', 236], ['12_the_cave_and_throne', 'cave', 40], ['13_wizard_rises', 'meeting', 40], ['14_dialogue', 'meeting', 125],
	['15_say_shazam_prompt', 'prompt', 30], ['16_shazam_shout', 'transform', 7], ['17_the_bolt', 'transform', 17], ['18_first_transformation', 'transform', 50],
	['19_wizard_last_words', 'transform', 100], ['20_home', 'home', 60],
];
const stills = [];
for (const [name, mark, off] of STILLS) {
	if (marks[mark] === undefined) continue;
	const i = Math.min(frames.length - 1, marks[mark] + off);
	const dst = path.join(out, name + '.png');
	fs.copyFileSync(path.join(shots, frames[i]), dst);
	stills.push(dst);
}
if (stills.length) execFileSync('node', [sheet, path.join(out, 'stills_contact_sheet.png'), '4', '0.3', ...stills]);
console.log('stills', stills.length, '->', out);
