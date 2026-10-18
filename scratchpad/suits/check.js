// v0.16.7 suits audit: every transparent / partly transparent texel inside each face rectangle of the 64x64 layout.
// Usage: node check.js            -> audits all six Miraculous suits (both arm variants)
//        node check.js <png> slim|wide [v]
'use strict';
const path = require('path');
const T = require('./skin_tools.js');
const box = (ox, oy, w, h, d) => ({ top: [ox + d, oy, w, d], bottom: [ox + d + w, oy, w, d], right: [ox, oy + d, d, h], front: [ox + d, oy + d, w, h], left: [ox + d + w, oy + d, d, h], back: [ox + 2 * d + w, oy + d, w, h] });
function parts(slim) {
	const a = slim ? 3 : 4;
	return {
		head: box(0, 0, 8, 8, 8), hat: box(32, 0, 8, 8, 8),
		body: box(16, 16, 8, 12, 4), jacket: box(16, 32, 8, 12, 4),
		rarm: box(40, 16, a, 12, 4), rsleeve: box(40, 32, a, 12, 4),
		larm: box(32, 48, a, 12, 4), lsleeve: box(48, 48, a, 12, 4),
		rleg: box(0, 16, 4, 12, 4), rpants: box(0, 32, 4, 12, 4),
		lleg: box(16, 48, 4, 12, 4), lpants: box(0, 48, 4, 12, 4),
	};
}
function audit(img, slim, verbose) {
	if (typeof img === 'string') img = T.decode(img);
	const P = parts(slim); const out = [];
	for (const [pn, faces] of Object.entries(P)) for (const [fn, [fx, fy, fw, fh]] of Object.entries(faces)) {
		let clear = 0, partial = 0; const list = [];
		for (let y = fy; y < fy + fh; y++) for (let x = fx; x < fx + fw; x++) { const a = img.px[(y * img.w + x) * 4 + 3]; if (a === 0) { clear++; list.push(`${x},${y}`); } else if (a < 255) { partial++; list.push(`${x},${y}~${a}`); } }
		if (clear || partial) out.push(`  ${pn}.${fn} [${fx},${fy} ${fw}x${fh}] clear=${clear}/${fw * fh}${partial ? ' PARTIAL=' + partial : ''}` + (verbose && clear + partial < fw * fh ? ' :: ' + list.join(' ') : ''));
	}
	return out;
}
const A = path.join(__dirname, '../../src/main/resources/assets/projecthero/textures/entity/miraculous');
const SUITS = [['ladybug/suit_slim', 1], ['ladybug/suit_wide', 0], ['cat_noir/suit_slim', 1], ['cat_noir/suit_wide', 0], ['hawk_moth/suit_slim', 1], ['hawk_moth/suit_wide', 0],
	['rena_rouge/suit', 1], ['rena_rouge/suit_wide', 0], ['carapace/suit', 1], ['carapace/suit_wide', 0], ['pegasus/suit', 1], ['pegasus/suit_wide', 0]];
module.exports = { audit, parts, box, SUITS, A };
if (require.main === module) {
	const [file, slim, v] = process.argv.slice(2);
	if (file) console.log(audit(file, slim === 'slim', v === 'v').join('\n'));
	else for (const [n, s] of SUITS) { console.log('=== ' + n + (s ? ' (slim)' : ' (wide)')); console.log(audit(path.join(A, n + '.png'), !!s, process.argv.includes('v')).join('\n')); }
}
