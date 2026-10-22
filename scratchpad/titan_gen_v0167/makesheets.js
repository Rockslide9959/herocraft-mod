// node makesheets.js <screenshotDir> <outDir> [tileW]
'use strict';
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const [, , dir, out, tw = '600'] = process.argv;
fs.mkdirSync(out, { recursive: true });
const all = fs.readdirSync(dir).filter(f => f.startsWith('tv_') && f.endsWith('.png')).sort();
const groups = {
	types: f => f.startsWith('tv_type'),
	transform_0_attack: f => f.startsWith('tv_transform_0'),
	transform_1_female: f => f.startsWith('tv_transform_1'),
	transform_2_armoured: f => f.startsWith('tv_transform_2'),
	transform_3_beast: f => f.startsWith('tv_transform_3'),
	corpse: f => f.startsWith('tv_corpse'),
	sever: f => f.startsWith('tv_sever'),
	moves_0_attack: f => f.startsWith('tv_move_0'),
	moves_1_female: f => f.startsWith('tv_move_1'),
	moves_2_armoured: f => f.startsWith('tv_move_2'),
	moves_3_beast: f => f.startsWith('tv_move_3'),
};
for (const [g, fn] of Object.entries(groups)) {
	const files = all.filter(fn).map(f => path.join(dir, f));
	if (!files.length) continue;
	// moves: keep each move's frames in time order on one row
	if (g.startsWith('moves')) {
		files.sort((a, b) => {
			const ka = path.basename(a), kb = path.basename(b);
			const ma = all.indexOf(ka), mb = all.indexOf(kb);
			return ma - mb;
		});
	}
	execFileSync('node', [path.join(__dirname, 'sheet.js'), path.join(out, g + '.png'), '4', tw, g, ...files], { stdio: 'inherit' });
}
