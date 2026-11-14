// v0.17.4: cuts the user's Dragon Ball clips (assets/projecthero/sounds/saiyan/dbz/, GIT-IGNORED: show audio, test builds only)
// into the trimmed one-shots and crossfaded loops the Saiyan's sound events point at. Every output also lands in that ignored
// folder, prefixed "cut_". The table here is the source for docs/SAIYAN_DBZ_SOUND_MAP.md.
//
//   node scratchpad/saiyan/dbz_cut.js            (from the repo root)
//
// Each one-shot: trimmed [from, to] s, 4 ms fade in, `fade` s fade out, loudness-normalised (single-pass loudnorm), mono 44.1 kHz,
// Vorbis q5. Loops: the segment (optionally followed by itself reversed, to lengthen a short roar), then its last `xf` s
// equal-power crossfaded over its first `xf` s, so the end flows back into the start with no click.
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const FF = process.env.FFMPEG || 'C:/Users/ethan/AppData/Local/Medal/recorder-2643.3040.1/ffmpeg7.exe';
const DIR = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'sounds', 'saiyan', 'dbz');

/** [out, source clip, from, to, fade out, loudness LUFS] */
const SHOTS = [
	['cut_charge_start', 'charging_up_2', 0.0, 1.38, 0.08, -16],
	['cut_transform_1', 'transforming', 0.86, 3.0, 0.3, -15],
	['cut_transform_2', 'super_saiyan_2', 0.9, 5.0, 0.6, -15],
	['cut_power_down', 'landed_untransform', 0.36, 1.58, 0.15, -17],
	['cut_take_off', 'time_to_fly', 0.52, 1.0, 0.08, -17],
	['cut_dash_1', 'time_to_move', 0.72, 1.9, 0.4, -17],
	['cut_dash_2', 'time_to_move_2', 0.05, 1.88, 0.3, -17],
	['cut_land_1', 'perfect_landing', 0.0, 1.0, 0.1, -17],
	['cut_land_2', 'perfect_landing_2', 0.25, 2.68, 0.3, -17],
	['cut_land_heavy_1', 'crash_into_ground', 1.22, 3.0, 0.4, -15],
	['cut_land_heavy_2', 'unperfect_landing', 0.0, 1.22, 0.1, -15],
	['cut_kame_charge', 'kamehameha_charge', 0.5, 6.0, 0.3, -16],
	['cut_kame_fire', 'kamehameha_fire', 0.75, 3.2, 0.8, -15],
	['cut_blast_1', 'ki_blast_fire', 0.21, 1.0, 0.2, -18],
	['cut_blast_2', 'mini_ki_blast', 0.0, 1.0, 0.25, -18],
	['cut_blast_3', 'pew_2', 0.72, 1.52, 0.15, -18],
	['cut_rain_shot', 'pew', 0.42, 1.6, 0.4, -19],
	['cut_blast_hit', 'ki_blast_explosion', 0.42, 1.8, 0.5, -18],
	['cut_boom_1', 'boom', 0.0, 3.0, 1.0, -16],
	['cut_boom_2', 'explosion', 0.78, 3.5, 1.0, -16],
	['cut_wave', 'explosion_2', 0.0, 3.5, 1.2, -15],
	['cut_sb_rise', 'here_goes_spirit_bomb_charge', 1.95, 7.0, 0.6, -17],
	['cut_sb_charge', 'charging_spirit_bomb', 0.02, 3.44, 0.3, -17],
	['cut_sb_throw', 'throw_it', 0.29, 1.45, 0.2, -15],
	['cut_sb_explode', 'spirit_bomb_explosion', 2.78, 8.0, 1.5, -14],
	['cut_sb_boom', 'boom_2', 0.45, 6.0, 2.0, -16],
	['cut_vanish', 'teleport', 0.0, 0.18, 0.03, -16],
	['cut_vanish_away', 'teleport_away', 0.36, 2.0, 0.5, -17],
	['cut_swing', 'swing_punch', 0.0, 0.5, 0.05, -18],
	['cut_punch_1', 'combat_fight', 0.86, 1.27, 0.05, -16],
	['cut_punch_2', 'combat_fight', 1.31, 1.66, 0.05, -16],
	['cut_punch_heavy', 'punch_knockback', 0.53, 1.88, 0.3, -15],
	['cut_flurry_1', 'combat_fight', 1.7, 3.0, 0.3, -17],
	['cut_flurry_2', 'combat_fight', 0.0, 0.78, 0.1, -17],
	['cut_finisher', 'finisher', 0.38, 2.5, 0.3, -15],
	['cut_dodge_1', 'dodge_attack', 0.02, 0.55, 0.08, -17],
	['cut_dodge_2', 'dodge_attack', 0.68, 1.03, 0.06, -17],
	['cut_dodge_3', 'dodge_attack', 1.1, 1.9, 0.1, -17],
];

/** [out, source clip, from, to, crossfade s, add the reversed segment, loudness LUFS] */
const LOOPS = [
	// the user: "the super saiyan aura should be the charge sound effect"
	['cut_aura_loop', 'charging_up', 1.66, 3.0, 0.3, true, -18],
	['cut_charge_loop', 'charging_up_2', 1.48, 7.08, 0.4, false, -17],
	['cut_beam_loop', 'kamehameha_fire', 1.5, 5.5, 0.4, false, -16],
];

function run(args) {
	execFileSync(FF, ['-hide_banner', '-loglevel', 'error', '-y', ...args]);
}

const ENC = ['-ac', '1', '-ar', '44100', '-c:a', 'libvorbis', '-q:a', '5'];
const norm = lufs => `loudnorm=I=${lufs}:TP=-1.5:LRA=11,aresample=44100`;

for (const [out, src, from, to, fade, lufs] of SHOTS) {
	const len = to - from;
	const f = `atrim=${from}:${to},asetpts=PTS-STARTPTS,afade=t=in:d=0.004,afade=t=out:st=${(len - fade).toFixed(3)}:d=${fade},${norm(lufs)}`;
	run(['-i', path.join(DIR, src + '.ogg'), '-af', f, ...ENC, path.join(DIR, out + '.ogg')]);
	console.log(out, '<-', src, from, to);
}

for (const [out, src, from, to, xf, rev, lufs] of LOOPS) {
	const seg = to - from;
	const L = rev ? seg * 2 : seg;
	let g = `[0:a]atrim=${from}:${to},asetpts=PTS-STARTPTS`;
	if (rev) {
		g += `,asplit=2[f][r];[r]areverse[rr];[f][rr]concat=n=2:v=0:a=1`;
	}
	g += `,${norm(lufs)},asplit=3[a][b][c];`;
	g += `[a]atrim=0:${xf},asetpts=PTS-STARTPTS,afade=t=in:d=${xf}:curve=qsin[head];`;
	g += `[b]atrim=${(L - xf).toFixed(3)}:${L.toFixed(3)},asetpts=PTS-STARTPTS,afade=t=out:d=${xf}:curve=qsin[tail];`;
	g += `[head][tail]amix=inputs=2:normalize=0[mix];`;
	g += `[c]atrim=${xf}:${(L - xf).toFixed(3)},asetpts=PTS-STARTPTS[body];`;
	g += `[mix][body]concat=n=2:v=0:a=1[o]`;
	run(['-i', path.join(DIR, src + '.ogg'), '-filter_complex', g, '-map', '[o]', ...ENC, path.join(DIR, out + '.ogg')]);
	console.log(out, '<- loop', src, from, to, rev ? '(+reversed)' : '');
}
console.log('done:', fs.readdirSync(DIR).filter(n => n.startsWith('cut_')).length, 'cut files');
