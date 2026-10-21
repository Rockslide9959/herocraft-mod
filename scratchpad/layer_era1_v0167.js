// v0.16.7 (user): "add layering to the first 5 armours as well" -- the Era II look on the Era I suits: the outer skin layer of
// the body, arms and legs sits 0.4 px off the base (was 0.22) and the texture gets layerPlates' raised plates (chest round the
// reactor, ab plates, belt, cuffs, knees, boot cuffs, helmet crest). The hat layer is left alone (the faceplate is fitted to it).
// Usage: node scratchpad/layer_era1_v0167.js [--dry]
'use strict';
const fs = require('fs');
const path = require('path');
const RES = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero');
const DRY = process.argv.includes('--dry');
const OLD = 0.22, NEW = 0.4, D = NEW - OLD;
// outer-layer texture regions (u0, v0, u1, v1) of the 64x64 skin, hat excluded
const OUTER = [[0, 32, 16, 48], [0, 48, 16, 64], [16, 32, 40, 48], [40, 32, 56, 48], [48, 48, 64, 64]];
const inOuter = (u, v) => OUTER.some(([a, b, c, d]) => u >= a && u < c && v >= b && v < d);
const r3 = v => Math.round(v * 10000) / 10000;
for (const geo of ['mark_1', 'mark_iii', 'mark_4', 'mark_v']) { // mark_1_v2 renders on mark_1's geo
	const f = path.join(RES, 'geo', geo + '.geo.json');
	const g = JSON.parse(fs.readFileSync(f, 'utf8'));
	let n = 0;
	for (const b of g['minecraft:geometry'][0].bones) for (const c of b.cubes || []) {
		if (Array.isArray(c.uv)) {
			if (inOuter(c.uv[0], c.uv[1]) && Math.abs((c.inflate || 0) - OLD) < 1e-6) { c.inflate = NEW; n++; console.log(geo, b.name, 'inflate'); }
		} else if (c.uv && typeof c.uv === 'object' && !c.inflate) {
			const faces = Object.values(c.uv);
			if (!faces.length || !faces.every(fc => inOuter(fc.uv[0], fc.uv[1]))) continue;
			// an explicit outer shell: it must wrap a same-bone base cube by ~0.22 - 0.08 = 0.14 on x
			const base = (b.cubes || []).find(o => o !== c && !Array.isArray(o.uv) && o.size && Math.abs((c.size[0] - o.size[0]) - 2 * (OLD - 0.08)) < 0.02);
			if (!base) { console.log(geo, b.name, 'SKIP (no base twin)', JSON.stringify(c.size)); continue; }
			c.origin = [r3(c.origin[0] - D), r3(c.origin[1] - D), r3(c.origin[2] - D)];
			c.size = [r3(c.size[0] + 2 * D), r3(c.size[1] + 2 * D), r3(c.size[2] + 2 * D)];
			n++; console.log(geo, b.name, 'grown');
		}
	}
	if (!DRY) fs.writeFileSync(f, JSON.stringify(g, null, '\t') + '\n');
	console.log(geo + ': ' + n + ' outer cubes');
}
