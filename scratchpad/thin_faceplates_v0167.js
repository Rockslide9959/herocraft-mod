// v0.16.7 (user): the solid faceplate "sticks out" -- GeckoLib's inflate grows a cube on EVERY side, so the 0.6 px plate
// with inflate 0.32 was 1.24 px thick and stood proud of the helmet even when shut. This rewrites each solid faceplate as an
// explicit, NON-inflated cube: the same front face / outline as before (front at -4 - inflate, edges at +/- inflate), but
// only THICKNESS px deep -- flush with the helmet when closed, still a real plate (not stacked sheets) when lifted.
// Run after scratchpad/solid_faceplates_v0167.js. Usage: node scratchpad/thin_faceplates_v0167.js
'use strict';
const fs = require('fs');
const path = require('path');
const GEO = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'geo');
const THICKNESS = 0.3;
for (const id of ['mark_1', 'mark_2', 'mark_iii', 'mark_4', 'mark_v', 'mark_6', 'mark_vii', 'mark_8']) {
	const f = path.join(GEO, id + '.geo.json');
	const g = JSON.parse(fs.readFileSync(f, 'utf8'));
	const fp = g['minecraft:geometry'][0].bones.find(b => b.name === 'faceplate');
	const c = fp.cubes[0];
	const i = c.inflate || 0;
	if (c.size[2] === THICKNESS && !c.inflate) {
		console.log(id + ': already thin');
		continue;
	}
	c.origin = [-4 - i, 24 - i, -4 - i].map(v => Math.round(v * 1000) / 1000);
	c.size = [8 + 2 * i, 8 + 2 * i, THICKNESS].map(v => Math.round(v * 1000) / 1000);
	delete c.inflate;
	fs.writeFileSync(f, JSON.stringify(g, null, '\t') + '\n');
	console.log(id + ': faceplate ' + THICKNESS + ' px (was ' + (0.6 + 2 * i).toFixed(2) + ')');
}
