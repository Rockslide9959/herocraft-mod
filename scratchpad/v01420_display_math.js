// third-person item frame math. world: player faces +Z, +X = player's left, +Y up.
const d2r = Math.PI / 180;
const mul = (A, B) => A.map((r, i) => [0, 1, 2].map(j => r[0] * B[0][j] + r[1] * B[1][j] + r[2] * B[2][j]));
const ap = (A, v) => A.map(r => r[0] * v[0] + r[1] * v[1] + r[2] * v[2]);
const Rx = a => { const c = Math.cos(a * d2r), s = Math.sin(a * d2r); return [[1, 0, 0], [0, c, -s], [0, s, c]]; };
const Ry = a => { const c = Math.cos(a * d2r), s = Math.sin(a * d2r); return [[c, 0, s], [0, 1, 0], [-s, 0, c]]; };
const Rz = a => { const c = Math.cos(a * d2r), s = Math.sin(a * d2r); return [[c, -s, 0], [s, c, 0], [0, 0, 1]]; };
const R = [[1, 0, 0], [0, -1, 0], [0, 0, -1]];
const fmt = v => v.map(x => x.toFixed(2)).join(',');
function tp(rot, bake) {
	let M = mul(R, mul(Rx(-90), mul(Ry(180), mul(Rx(rot[0]), mul(Ry(rot[1]), Rz(rot[2]))))));
	M = mul(M, Rz(bake));
	return M;
}
// first person (right hand): ItemInHandRenderer: no entity model; pose = camera space (x right, y up, -z forward)
function fp(rot, bake) {
	let M = mul(Rx(rot[0]), mul(Ry(rot[1]), Rz(rot[2])));
	return mul(M, Rz(bake));
}
const args = process.argv.slice(2).map(Number);
const [rx, ry, rz, bake] = args;
const T = tp([rx, ry, rz], bake);
console.log('TP handle(+Y)', fmt(ap(T, [0, 1, 0])), ' axe(+X)', fmt(ap(T, [1, 0, 0])), ' +Z', fmt(ap(T, [0, 0, 1])), '   (world: +Z fwd, +X left, +Y up)');
const F = fp([rx, ry, rz], bake);
console.log('FP handle(+Y)', fmt(ap(F, [0, 1, 0])), ' axe(+X)', fmt(ap(F, [1, 0, 0])), ' +Z', fmt(ap(F, [0, 0, 1])), '   (cam: +X right, +Y up, -Z fwd)');
