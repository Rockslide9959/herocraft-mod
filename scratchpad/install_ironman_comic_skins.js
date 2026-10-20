// v0.16.1 Iron Man comic rework: install the user-approved 64x64 suit skins (+ glowmasks) as the armour textures.
// Repeatable. Run from the repo root (or a worktree): node scratchpad/install_ironman_comic_skins.js
//
// Source: <main checkout>/scratchpad/concepts_ironman/skins/<id>.png + <id>_glowmask.png (standard player-skin layout:
// base + outer layer; rendered with the base at +0.25 px, the outer layer at +0.5 px and the helmet's outer layer at
// +0.75 px over the player). Every Iron Man armour geo in assets/projecthero/geo/ is already that exact skin-layout rig
// (mark_vii.geo.json: helmet / faceplate / chest / upper arms / thigh + boot split, inflates 0.25 / 0.5 / 0.75, the
// faceplate as its own bone on the helmet front), so the skins drop straight in. The Mark 5's geo still carried the old
// gauntlet-blade bones (the Stealth armour has no blades and its new skin paints nothing in their old swatch), so it is
// rebuilt from the shared rig here. The Mark 1 keeps its current model and texture (not installed).
'use strict';
const fs = require('fs');
const path = require('path');

const here = __dirname.split(path.sep).join('/');
const wt = here.indexOf('/.claude/worktrees/');
const MAIN = wt >= 0 ? here.slice(0, wt) : path.join(__dirname, '..');
const SRC = path.join(MAIN, 'scratchpad/concepts_ironman/skins');
const RES = path.join(__dirname, '../src/main/resources/assets/projecthero');
const IDS = ['mark_1_v2', 'mark_2', 'mark_iii', 'mark_4', 'mark_v', 'mark_6', 'mark_vii'];

for (const id of IDS) {
	for (const suffix of ['', '_glowmask']) {
		const from = path.join(SRC, id + suffix + '.png');
		const to = path.join(RES, 'textures/armor', id + suffix + '.png');
		if (!fs.existsSync(from)) {
			throw new Error('missing skin ' + from);
		}
		fs.copyFileSync(from, to);
		console.log('installed', path.relative(RES, to));
	}
}

// the Mark 5: the shared skin rig (drop the blade bones), own identifier
const rigFile = path.join(RES, 'geo/mark_vii.geo.json');
const rig = JSON.parse(fs.readFileSync(rigFile, 'utf8'));
const geo = rig['minecraft:geometry'][0];
const mk5File = path.join(RES, 'geo/mark_v.geo.json');
const mk5 = JSON.parse(fs.readFileSync(mk5File, 'utf8'));
const out = JSON.parse(JSON.stringify(rig));
out['minecraft:geometry'][0].description = Object.assign({}, geo.description,
	{ identifier: mk5['minecraft:geometry'][0].description.identifier });
const eol = fs.readFileSync(mk5File, 'utf8').includes('\r\n') ? '\r\n' : '\n';
fs.writeFileSync(mk5File, JSON.stringify(out, null, '\t').replace(/\n/g, eol) + eol);
console.log('rebuilt geo/mark_v.geo.json on the shared skin rig');
