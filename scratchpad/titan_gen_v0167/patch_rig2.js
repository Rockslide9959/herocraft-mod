const fs = require('fs');
process.chdir(__dirname);
let s = fs.readFileSync('rig.js', 'utf8');
const rep = (a, b) => { if (!s.includes(a)) throw new Error('miss ' + a.slice(0, 50)); s = s.replace(a, b); };
rep("			add(`${side}_forearm`, 'knuckle', 'knuckle', [hd[0] + 0.1, hd[1] + 0.2, hd[2] - 0.42, hd[3] - 0.1, hd[1] + 1.3, hd[2] + 0.05], { layer: 2 });\n		}",
	"			add(`${side}_forearm`, 'knuckle', 'knuckle', [hd[0] + 0.1, hd[1] + 0.2, hd[2] - 0.42, hd[3] - 0.1, hd[1] + 1.3, hd[2] + 0.05], { layer: 2 });\n		}\n" +
	"		// Crystal Fists: crystal over the fists and shins (and the nape), drawn only while the move runs\n" +
	"		for (const side of ['right', 'left']) {\n" +
	"			const m = side === 'right' ? b => b : mirrorBox;\n" +
	"			const hd = m(P.hand), sh = m(P.shin);\n" +
	"			M.bone(`crystal_${side}_fist`, `${side}_forearm`, [R_cx(hd), hd[1], R_cz(hd)]);\n" +
	"			add(`crystal_${side}_fist`, 'crystal', 'crystal', [hd[0] - 0.35, hd[1] - 0.45, hd[2] - 0.75, hd[3] + 0.35, hd[1] + 2.0, hd[5] + 0.3], { layer: 2 });\n" +
	"			M.bone(`crystal_${side}_shin`, `${side}_shin`, [R_cx(sh), sh[1], R_cz(sh)]);\n" +
	"			add(`crystal_${side}_shin`, 'crystal', 'crystal', [sh[0] - 0.3, sh[1] + 0.4, sh[2] - 0.65, sh[3] + 0.3, sh[4] - 0.4, sh[2] + 0.9], { layer: 2 });\n" +
	"		}\n" +
	"		M.bone('crystal_nape', 'body', [0, P.neckY, P.chest[5]]);\n" +
	"		add('crystal_nape', 'crystal', 'crystal', [-1.9, P.neckY - 3.0, P.chest[5] + 0.1, 1.9, P.neckY + 0.5, P.chest[5] + 0.75], { layer: 2 });");
rep("		const C = P.chest, H = P.head;\n		fur('body', 'ruff',",
	"		const C = P.chest, H = P.head;\n" +
	"		const hd = P.hand;\n" +
	"		M.bone('boulder', 'right_forearm', [R_cx(hd), hd[1], R_cz(hd)]);\n" +
	"		add('boulder', 'rock', 'boulder', [R_cx(hd) - 2.6, hd[1] - 4.4, R_cz(hd) - 2.9, R_cx(hd) + 2.6, hd[1] + 0.9, R_cz(hd) + 2.3], { layer: 2 });\n" +
	"		fur('body', 'ruff',");
rep("const EXTRAS = {", "const R_cx = cx, R_cz = cz;\nconst EXTRAS = {");
fs.writeFileSync('rig.js', s);
let p = fs.readFileSync('paint.js', 'utf8');
p = p.replace("			case 'stump': return paintStump(M, c, f, s, t, p, info);", "			case 'stump': return paintStump(M, c, f, s, t, p, info);\n			case 'crystal': return paintCrystal(M, c, f, s, t, p, info);\n			case 'rock': return paintRock(M, c, f, s, t, p, info);");
p = p.replace("function shade(M) {", `function paintCrystal(M, c, f, s, t, p, info) {
	const diag = (s - 0.5) + (t - 0.5) * 0.7;
	let col = mix([206, 232, 248], [120, 170, 214], 0.2 + (diag > 0 ? 0.25 : 0) + t * 0.2);
	const n = fbm(p[0] * 0.8, p[1] * 0.8, p[2] * 0.8, 3);
	col = mix(col, [236, 248, 255], smooth(0.6, 0.8, n) * 0.6);
	if (info.ePx < 0.18) col = mix(col, [250, 254, 255], 0.6);
	const k = Math.abs(((p[0] + p[1] * 0.7) / 0.9) % 1);
	if (k < 0.05) col = mix(col, [150, 196, 230], 0.5);
	return [col, 255];
}

function paintRock(M, c, f, s, t, p, info) {
	const n = fbm(p[0] * 0.9 + 3, p[1] * 0.9, p[2] * 0.9, 4);
	let col = mix([88, 84, 80], [150, 146, 138], n);
	if (Math.abs(fbm(p[0] * 0.6 + 9, p[1] * 0.6, p[2] * 0.6, 3) - 0.5) < 0.02) col = tint(col, 0.6);
	if (vnoise(p[0] * 3, p[1] * 3, p[2] * 3) > 0.8) col = mix(col, [96, 120, 70], 0.5);
	col = tint(col, 0.85 + 0.15 * smooth(0, 0.6, info.ePx));
	return [col, 255];
}

function shade(M) {`);
fs.writeFileSync('paint.js', p);
console.log('patched');
