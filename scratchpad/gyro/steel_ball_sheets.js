// v0.17.3 Gyro: preview sheets for the Zeppeli Steel Ball from the harness screenshots (SteelBallHarness, run/screenshots).
//   node scratchpad/gyro/steel_ball_sheets.js <screenshots_dir>
// Writes into docs/screenshots/v0173/gyro/:
//   steel_ball_turntable.mp4   normal turn, golden turn, fast spin (20 fps)
//   steel_ball_sheet.png       every 6th turntable frame (normal + golden), cropped round the ball, + the angle sheet + hand size
//   steel_ball_vs_refs.png     the five references next to the in-game render
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = process.env.FFMPEG || 'C:/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe';
const ROOT = path.resolve(__dirname, '..', '..');
const SHOTS = path.resolve(process.argv[2] || path.join(ROOT, 'run/screenshots'));
const OUT = path.join(ROOT, 'docs/screenshots/v0173/gyro');
fs.mkdirSync(OUT, { recursive: true });
const ff = (a) => execFileSync(FF, ['-hide_banner', '-loglevel', 'error', '-y', ...a], { maxBuffer: 1 << 26 });
const P = (n) => path.join(SHOTS, 'v0173_steelball_' + n + '.png');

// ---- turntable mp4: the three runs back to back (crop to a square round the ball)
const crop = 'crop=480:480:187:0,scale=480:480';
const list = path.join(SHOTS, 'tt_list.txt');
const files = [];
for (const pre of ['tt_normal_', 'tt_golden_', 'tt_fast_']) {
	for (let i = 0; ; i++) {
		const f = P(pre + String(i).padStart(3, '0'));
		if (!fs.existsSync(f)) break;
		files.push(f);
	}
}
fs.writeFileSync(list, files.map((f) => `file '${f.replace(/\\/g, '/')}'\nduration 0.05`).join('\n') + '\n');
ff(['-f', 'concat', '-safe', '0', '-i', list, '-vf', crop + ',fps=20,format=yuv420p', '-c:v', 'libx264', '-crf', '18', '-movflags', '+faststart',
	path.join(OUT, 'steel_ball_turntable.mp4')]);

// ---- frame sheet: every 6th frame of the normal and golden turns (10 + 10), 160 px tiles, then the angle sheet and hand size
const pick = [];
for (const pre of ['tt_normal_', 'tt_golden_']) for (let i = 0; i < 60; i += 6) pick.push(P(pre + String(i).padStart(3, '0')));
const tiles = path.join(SHOTS, 'tiles.png');
const inputs = pick.flatMap((f) => ['-i', f]);
const fc = pick.map((_, i) => `[${i}:v]${crop},scale=192:192[t${i}]`).join(';') + ';' + pick.map((_, i) => `[t${i}]`).join('') + `xstack=inputs=${pick.length}:layout=` +
	pick.map((_, i) => `${(i % 10) * 192}_${Math.floor(i / 10) * 192}`).join('|') + '[o]';
ff([...inputs, '-filter_complex', fc, '-map', '[o]', tiles]);
ff(['-i', tiles, '-i', P('sheet_all'), '-i', P('hand_size'), '-filter_complex',
	'[1:v]scale=960:-2[a];[2:v]scale=960:-2[b];[a][b]hstack=inputs=2[ab];[0:v][ab]vstack=inputs=2', path.join(OUT, 'steel_ball_sheet.png')]);

// ---- references vs render: the five refs at 300 px high, then the in-game view (normal + golden turntable frames)
const refs = [1, 2, 3, 4, 5].map((i) => path.join(ROOT, `docs/reference/gyro/steel_ball_ref_${i}.png`));
const ri = refs.flatMap((f) => ['-i', f]);
const ours = [P('refs_view'), P('tt_normal_009'), P('tt_golden_004')];
const oi = ours.flatMap((f) => ['-i', f]);
const fc2 = refs.map((_, i) => `[${i}:v]scale=-2:300,pad=iw+8:300:4:0:color=0x202428[r${i}]`).join(';') + ';' +
	refs.map((_, i) => `[r${i}]`).join('') + 'hstack=inputs=5,scale=1500:-2,pad=1500:ih+30:0:30:color=0x202428[top];' +
	ours.map((_, i) => `[${5 + i}:v]${crop},scale=480:480,pad=500:480:10:0:color=0x202428[o${i}]`).join(';') + ';' +
	ours.map((_, i) => `[o${i}]`).join('') + 'hstack=inputs=3,pad=1500:ih+30:0:30:color=0x202428[bot];[top][bot]vstack=inputs=2,' +
	"drawtext=text='References':x=10:y=6:fontsize=20:fontcolor=white:fontfile='C\\:/Windows/Fonts/arial.ttf'";
ff([...ri, ...oi, '-filter_complex', fc2, path.join(OUT, 'steel_ball_vs_refs.png')]);
console.log('wrote', fs.readdirSync(OUT).join(', '));
