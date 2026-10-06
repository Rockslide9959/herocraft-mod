// v0.15.1: preview sheets for all seven Iron Man marks. Usage: node scratchpad/preview_v0151_all.js [suffix]
// (suffix "_before" to snapshot the pre-change assets). Writes <main repo>/scratchpad/shots_v0151/M_<id><suffix>.png
// (front | back | right | left; Mark V blades retracted, plus M_mark_v_blades_out.png with them extended)
const path = require('path');
const { render } = require('./preview_v0151_ironman.js');
const A = path.join(__dirname, '../src/main/resources/assets/projecthero');
const OUT = 'C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod/scratchpad/shots_v0151/';
const suffix = process.argv[2] || '';
const files = id => [path.join(A, 'geo', id + '.geo.json'), path.join(A, 'textures/armor', id + '.png')];
const glow = id => path.join(A, 'textures/armor', id + '_glowmask.png');
for (const id of ['mark_1', 'mark_2', 'mark_iii', 'mark_4', 'mark_v', 'mark_6', 'mark_vii']) {
	const n = render(...files(id), OUT + 'M_' + id + suffix + '.png', 10, glow(id), b => b.endsWith('_blade'));
	console.log(id, n, 'quads');
}
render(...files('mark_v'), OUT + 'M_mark_v_blades_out' + suffix + '.png', 10, glow('mark_v'));
