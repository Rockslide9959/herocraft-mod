// v0.17.2 round 3 (user: "make the wrist band 4 pixels wide please, and make the dial and watch face a bit thinner aswell to match it
// please like 6 pixels wide"): band 4 px along the arm (no fins past it), the bezel 6 px across and lower
const fs = require('fs');
const f = 'src/client/java/com/projecthero/mod/client/omnitrix/OmnitrixModel.java';
let s = fs.readFileSync(f, 'utf8');
const crlf = s.includes('\r\n'); s = s.replace(/\r\n/g, '\n');
const i0 = s.indexOf('\t\t// ---- the fins along the arm'), i1 = s.indexOf('\t\t// ---- the knobs: out of the bezel');
if (i0 < 0 || i1 < 0) throw new Error('fins block');
s = s.slice(0, i0) + s.slice(i1);
const R = [
	['private static final float RB = 1.6f, RT = 1.38f, RI = 1.12f, BEZEL_H = 0.4f;', 'private static final float RB = 3.0f, RT = 2.65f, RI = 2.15f, BEZEL_H = 0.3f;'],
	['RECAL_WALL = 0.18f, RECAL_HALF_LEN = 1.45f;', 'RECAL_WALL = 0.18f, RECAL_HALF_LEN = 2.0f;'],
	['private static final float RECAL_FACE_X = 0.28f;', 'private static final float RECAL_FACE_X = 0.2f;'],
	['ribbon(pose, vc, outer, 0f, outer.perimeter(), true, 0f, 0f, 0.05f, cy - 0.42f, cy + 0.42f, WHITE, light);', 'ribbon(pose, vc, outer, 0f, outer.perimeter(), true, 0f, 0f, 0.05f, cy - 0.55f, cy + 0.55f, WHITE, light);'],
	['pose.translate((xo + 0.21f) * S, cy * S, side * (RB - 0.12f) * S);', 'pose.translate((xo + 0.15f) * S, cy * S, side * (RB - 0.1f) * S);'],
	['cylinder(pose, vc, 0f, 0.5f, 0f, 0f, 0.24f, PLATE, light, 12);\n\t\t\tcylinder(pose, vc, 0.5f, 0.58f, 0f, 0f, 0.17f, RING, light, 12);', 'cylinder(pose, vc, 0f, 0.55f, 0f, 0f, 0.28f, PLATE, light, 12);\n\t\t\tcylinder(pose, vc, 0.55f, 0.65f, 0f, 0f, 0.2f, RING, light, 12);'],
	['\t\t\t// the flat top ring\n', '\t\t\t// the underside (the ring overhangs the band), then the flat top ring\n\t\t\tsegQuad(vc, p, light, g, x0, cy + c0 * RI, s0 * RI, x0, cy + c1 * RI, s1 * RI, x0, cy + c1 * RB, s1 * RB, x0, cy + c0 * RB, s0 * RB, -1, 0, 0);\n'],
	[' *   <li><b>fins</b>: green plates hugging the face along the arm, each ending in a grey tab;</li>\n', ''],
];
for (const [a, b] of R) { if (!s.includes(a)) throw new Error('miss ' + a.slice(0, 70)); s = s.replace(a, b); }
fs.writeFileSync(f, crlf ? s.replace(/\n/g, '\r\n') : s);
console.log('ok');
