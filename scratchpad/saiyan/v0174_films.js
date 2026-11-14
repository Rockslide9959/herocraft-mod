// v0.17.4 Saiyan films: after a SaiyanV4Harness run (frames in run/screenshots/sv4_*), makes per clip an MP4 (20 fps) and a 4x4
// frame sheet, and -- where the harness logged sounds (sv4_<clip>_sounds.txt: frame, file, volume, pitch, loop/once, stop=frame)
// -- a "<clip>_sound.mp4" with the exact sound files the game played laid under the video at the frames they started (loops
// repeated until they stopped). Stills are copied. Output: docs/screenshots/v0174/saiyan/.
//   node scratchpad/saiyan/v0174_films.js      (from the worktree root)
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const FF = process.env.FFMPEG || 'C:/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe';
const ROOT = process.cwd();
const SHOTS = path.join(ROOT, 'run', 'screenshots');
const OUT = path.join(ROOT, 'docs', 'screenshots', 'v0174', 'saiyan');
const SOUNDS = path.join(ROOT, 'src', 'main', 'resources', 'assets');
fs.mkdirSync(OUT, { recursive: true });
const font = path.join(SHOTS, 'arial.ttf');
if (!fs.existsSync(font)) fs.copyFileSync('C:/Windows/Fonts/arial.ttf', font);
const ff = (args, cwd) => execFileSync(FF, ['-hide_banner', '-loglevel', 'error', '-y', ...args], { cwd: cwd || SHOTS });

const files = fs.readdirSync(SHOTS);
const clips = [...new Set(files.map(f => /^sv4_(.+)_(\d{3})\.png$/.exec(f)).filter(m => m && m[1] !== 'still').map(m => m[1]))];
for (const c of clips) {
	const count = files.filter(f => new RegExp(`^sv4_${c}_\\d{3}\\.png$`).test(f)).length;
	const mp4 = path.join(OUT, c + '.mp4');
	ff(['-framerate', '20', '-start_number', '0', '-i', `sv4_${c}_%03d.png`, '-vf', 'scale=trunc(iw/2)*2:trunc(ih/2)*2', '-c:v', 'libx264',
		'-pix_fmt', 'yuv420p', '-crf', '18', mp4]);
	const every = Math.max(1, Math.ceil(count / 16));
	ff(['-start_number', '0', '-i', `sv4_${c}_%03d.png`, '-vf',
		`select='not(mod(n\\,${every}))',scale=384:-2,drawtext=fontfile=arial.ttf:text='%{n}':x=4:y=4:fontsize=16:fontcolor=yellow:box=1:boxcolor=black@0.5,tile=4x4`,
		'-frames:v', '1', path.join(OUT, c + '_sheet.png')]);
	console.log(c, count, 'frames');
	const log = path.join(SHOTS, `sv4_${c}_sounds.txt`);
	if (!fs.existsSync(log)) continue;
	const lines = fs.readFileSync(log, 'utf8').split(/\r?\n/).filter(Boolean);
	const inputs = [], chains = [];
	let n = 0;
	for (const l of lines) {
		const [frame, file, vol0, pitch, kind, event] = l.split(' ');
		// a client loop starts near-silent and fades in: use the volume it fades up to (SaiyanLoopSounds / the beam loop)
		const LOOP_VOL = { 'projecthero:saiyan_aura_loop_gold': 0.35, 'projecthero:saiyan_aura_loop_red': 0.35, 'projecthero:saiyan_ki_charge_loop': 0.45,
			'projecthero:saiyan_kame_loop': 0.6 };
		const vol = kind === 'loop' && +vol0 < 0.05 ? (LOOP_VOL[event] || 0.4) : +vol0;
		const stop = /stop=(\d+)/.exec(l);
		const [ns, p] = file.split(':');
		const ogg = path.join(SOUNDS, ns, 'sounds', p + '.ogg');
		if (!fs.existsSync(ogg)) continue;
		const start = +frame / 20;
		const end = (stop ? +stop[1] : count) / 20;
		if (kind === 'loop') inputs.push('-stream_loop', '-1');
		inputs.push('-i', ogg);
		const pt = Math.max(0.5, Math.min(2, +pitch));
		let ch = `[${n + 1}:a]aformat=channel_layouts=mono,asetrate=${Math.round(44100 * pt)},aresample=44100,volume=${Math.min(1, +vol).toFixed(3)}`;
		if (kind === 'loop') ch += `,atrim=0:${Math.max(0.05, end - start).toFixed(3)},afade=t=out:st=${Math.max(0, end - start - 0.3).toFixed(3)}:d=0.3`;
		ch += `,adelay=${Math.round(start * 1000)}[s${n}]`;
		chains.push(ch);
		n++;
	}
	if (!n) continue;
	const fc = chains.join(';') + ';' + Array.from({ length: n }, (_, i) => `[s${i}]`).join('') + `amix=inputs=${n}:normalize=0,alimiter=limit=0.9,apad[a]`;
	ff(['-i', mp4, ...inputs, '-filter_complex', fc, '-map', '0:v', '-map', '[a]', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '160k', '-shortest',
		path.join(OUT, c + '_sound.mp4')], ROOT);
	console.log('  +sound', n, 'clips');
}
for (const f of files.filter(f => f.startsWith('sv4_still_'))) {
	fs.copyFileSync(path.join(SHOTS, f), path.join(OUT, f.replace('sv4_still_', '')));
}
console.log('->', OUT);
