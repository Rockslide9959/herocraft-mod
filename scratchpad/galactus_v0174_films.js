// v0.17.4 Galactus raid fix: encode GalactusRaidHarness.v0174 frames into MP4s + frame sheets (never GIFs).
// node scratchpad/galactus_v0174_films.js <film frames dir> <film client log> <loss frames dir>
// Frames are grabbed every 2 ticks, so 20 fps = 2x speed; the hand-hit clip is encoded at 10 fps (real time).
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const root = process.cwd();
const [filmDir, filmLog, lossDir] = process.argv.slice(2);
const out = path.join(root, 'docs', 'screenshots', 'v0174', 'galactus');
fs.mkdirSync(out, { recursive: true });
const sheet = path.join(root, 'scratchpad', 'contact_sheet.js');

function frames(dir, section) {
	const d = path.join(dir, section, 'screenshots');
	return fs.existsSync(d) ? fs.readdirSync(d).filter(f => f.endsWith('.png')).sort().map(f => path.join(d, f)) : [];
}
function encode(list, mp4, fps) {
	const tmp = path.join(root, 'run', '_seq');
	fs.rmSync(tmp, { recursive: true, force: true });
	fs.mkdirSync(tmp);
	list.forEach((f, i) => fs.copyFileSync(f, path.join(tmp, String(i).padStart(5, '0') + '.png')));
	execFileSync(FF, ['-y', '-loglevel', 'error', '-framerate', String(fps), '-i', path.join(tmp, '%05d.png'), '-vf', 'scale=854:-2', '-c:v', 'h264_mf',
		'-b:v', '5M', '-pix_fmt', 'yuv420p', mp4]);
	fs.rmSync(tmp, { recursive: true, force: true });
}
function pick(list, n) {
	const step = Math.max(1, Math.floor(list.length / n));
	const r = [];
	for (let i = 0; i < list.length && r.length < n; i += step) r.push(list[i]);
	return r;
}
function make(name, list, fps, n = 8) {
	if (!list.length) {
		console.log('skip', name);
		return;
	}
	encode(list, path.join(out, name + '.mp4'), fps);
	execFileSync('node', [sheet, path.join(out, name + '_sheet.png'), '4', '0.5', ...pick(list, n)]);
	console.log(name, list.length, 'frames');
}
const secs = t => {
	const m = /^\[(\d+):(\d+):(\d+)\]/.exec(t);
	return m ? +m[1] * 3600 + +m[2] * 60 + +m[3] : 0;
};

// the hand being hit in orbit: from the first stuck-fist hit in the log, real time
const log = fs.readFileSync(filmLog, 'latin1').split(/\r?\n/);
const s1 = log.find(l => l.includes('[HARNESS] phase STAGE1'));
const hit = log.find(l => /crosshair on ENTITY devourer_part (HAND|FORE)/.test(l));
const st1 = frames(filmDir, 'stage1');
if (s1 && hit) {
	const at = Math.max(0, (secs(hit) - secs(s1)) * 10 - 30);
	make('stage1_hitting_the_hand', st1.slice(at, at + 260), 10, 12);
}
for (const [sec, name] of [['arrival', 'c1_herald'], ['herald', 'act0_herald_duel'], ['devourer', 'c2_devourer_wakes'], ['ascent', 'c2_arrival'],
	['stage1', 'stage1_full'], ['pull', 'c3_worldship'], ['stage2', 'stage2_feast'], ['rise', 'c4_rise'], ['stage3', 'stage3_devourer'],
	['ending', 'c5_victory'], ['home', 'victory_home']]) {
	make(name, frames(filmDir, sec), 20);
}
if (lossDir) {
	const loss = [...frames(lossDir, 'stage1'), ...frames(lossDir, 'home')];
	make('loss_death_in_orbit', loss, 20);
}
console.log('done ->', out);
