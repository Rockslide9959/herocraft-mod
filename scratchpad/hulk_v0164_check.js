// node check.js <geo> <anim> [<java file to scan for clip names>...]
const fs = require('fs');
const geo = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const an = JSON.parse(fs.readFileSync(process.argv[3], 'utf8'));
const bones = geo['minecraft:geometry'][0].bones;
const names = new Set(bones.map(b => b.name));
let errors = 0;
const err = m => { errors++; console.log('ERROR ' + m); };
for (const b of bones) {
	if (b.parent && !names.has(b.parent)) err('bone ' + b.name + ' parent ' + b.parent + ' missing');
	for (const c of b.cubes || []) {
		if (Array.isArray(c.uv)) {
			const [u, v] = c.uv, [w, h, d] = c.size;
			if (u + 2 * d + 2 * w > geo['minecraft:geometry'][0].description.texture_width || v + d + h > geo['minecraft:geometry'][0].description.texture_height)
				err('cube in ' + b.name + ' box uv out of texture');
		}
	}
}
if (names.size !== bones.length) err('duplicate bone names');
for (const [k, a] of Object.entries(an.animations)) {
	if (typeof a.animation_length !== 'number') err(k + ' no length');
	for (const [bn, chs] of Object.entries(a.bones || {})) {
		if (!names.has(bn)) err(k + ' references missing bone ' + bn);
		for (const [ch, kf] of Object.entries(chs)) {
			if (!['rotation', 'position', 'scale'].includes(ch)) err(k + ' ' + bn + ' bad channel ' + ch);
			let prev = -1;
			for (const [t, v] of Object.entries(kf)) {
				const n = Number(t);
				if (!(n >= 0) || n <= prev) err(k + ' ' + bn + '.' + ch + ' time order ' + t);
				if (n > a.animation_length + 1e-6) err(k + ' ' + bn + '.' + ch + ' key past length ' + t);
				prev = n;
				const vec = Array.isArray(v) ? v : v.vector;
				if (!Array.isArray(vec) || vec.length !== 3 || vec.some(x => typeof x !== 'number' || !isFinite(x))) err(k + ' ' + bn + ' bad value at ' + t);
			}
		}
	}
}
for (const f of process.argv.slice(4)) {
	const txt = fs.readFileSync(f, 'utf8');
	for (const m of txt.matchAll(/P \+ "([a-z_]+)"/g)) {
		if (!an.animations['animation.hulk.' + m[1]]) err(f + ' uses missing clip ' + m[1]);
	}
}
console.log(errors ? errors + ' errors' : 'OK: ' + names.size + ' bones, ' + Object.keys(an.animations).length + ' clips');
