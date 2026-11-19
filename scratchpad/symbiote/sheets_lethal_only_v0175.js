// v0.17.5 Symbiote "Lethal Protector only" round: flattens the harness output (Screenshot.grab writes into <dir>/screenshots/) and
// builds comparison sheets with ffmpeg.
//   node scratchpad/symbiote/sheets_lethal_only_v0175.js
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const FF = 'C:/Users/ethan/AppData/Local/CapCut/Apps/9.5.0.4050/ffmpeg.exe';
const DIR = path.join(__dirname, '..', '..', 'docs', 'screenshots', 'v0175', 'symbiote_lethal_only');

for (const sub of ['before', 'after']) {
	const inner = path.join(DIR, sub, 'screenshots');
	if (fs.existsSync(inner)) {
		for (const f of fs.readdirSync(inner)) fs.renameSync(path.join(inner, f), path.join(DIR, sub, f));
		fs.rmdirSync(inner);
	}
}
const P = (sub, name) => path.join(DIR, sub, name + '.png');

// a grid of images (each scaled to w x h), in reading order (no labels: this ffmpeg build has no drawtext font)
function grid(out, cells, cols, w, h) {
	const args = ['-y'];
	cells.forEach((c) => args.push('-i', c.file));
	const parts = [];
	cells.forEach((c, i) => {
		const label = c.label.replace(/'/g, '').replace(/:/g, '\\:');
		parts.push(`[${i}:v]scale=${w}:${h}[v${i}]`);
	});
	const layout = cells.map((c, i) => `${(i % cols) * w}_${Math.floor(i / cols) * h}`).join('|');
	parts.push(cells.map((c, i) => `[v${i}]`).join('') + `xstack=inputs=${cells.length}:layout=${layout}:fill=black[o]`);
	args.push('-filter_complex', parts.join(';'), '-map', '[o]', '-frames:v', '1', out);
	execFileSync(FF, args, { stdio: 'ignore' });
	console.log('wrote', path.relative(DIR, out));
}

// glint: before / after, front + three-quarter
grid(path.join(DIR, 'sheet_glint_before_after.png'), [
	{ file: P('before', 'before_glint_front_band_mid'), label: 'before: sheen band mid-body' },
	{ file: P('before', 'before_glint_three_quarter_band_mid'), label: 'before: sheen band (3/4)' },
	{ file: P('after', 'after_glint_front_band_mid'), label: 'after: same moment, no sheen' },
	{ file: P('after', 'after_glint_three_quarter_band_mid'), label: 'after: same moment (3/4)' },
], 2, 640, 360);

// faces: before / after for each colour, closed + snarl, front + three-quarter
for (const c of ['carnage', 'toxin', 'classic']) {
	const cells = [];
	for (const sub of ['before', 'after']) {
		for (const v of ['closed_front', 'closed_three_quarter', 'snarl_front', 'snarl_three_quarter']) {
			cells.push({ file: P(sub, `${sub}_face_${c}_${v}`), label: `${sub} ${c} ${v.replace(/_/g, ' ')}` });
		}
	}
	grid(path.join(DIR, `sheet_face_${c}_before_after.png`), cells, 4, 427, 240);
}

// six fresh grants side by side
const grants = fs.readdirSync(path.join(DIR, 'after')).filter((f) => f.startsWith('random_grant_')).sort();
grid(path.join(DIR, 'sheet_random_grants.png'), grants.map((f) => ({
	file: path.join(DIR, 'after', f), label: f.replace('.png', '').replace(/_/g, ' '),
})), 3, 427, 240);
