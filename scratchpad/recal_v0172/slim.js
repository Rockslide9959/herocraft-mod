// v0.17.2 round 2 (user: "Make the wrist band thinner too and make the watch face and dial a bit smaller please"): the recal model's sizes
const fs = require('fs');
const f = 'src/client/java/com/projecthero/mod/client/omnitrix/OmnitrixModel.java';
let s = fs.readFileSync(f, 'utf8');
const crlf = s.includes('\r\n'); s = s.replace(/\r\n/g, '\n');
const R = [
	['private static final float RB = 2.0f, RT = 1.72f, RI = 1.4f, BEZEL_H = 0.5f;', 'private static final float RB = 1.6f, RT = 1.38f, RI = 1.12f, BEZEL_H = 0.4f;\n\t/** v0.17.2 round 2 (user: "make the wrist band thinner"): the recalibrated band\'s wall and its half-width along the arm. */\n\tprivate static final float RECAL_WALL = 0.18f, RECAL_HALF_LEN = 1.45f;'],
	['private static final float RECAL_FACE_X = 0.36f;', 'private static final float RECAL_FACE_X = 0.28f;'],
	['\t\treturn cuffTop(L) + RECAL_FACE_X + lift;', '\t\treturn L + GAP + RECAL_WALL + RECAL_FACE_X + lift;'],
	['\t\tfloat xo = cuffTop(L);\n\t\tSection outer = cuff.grow(WALL);\n\t\t// ---- the band and its black stripe\n\t\tribbon(pose, vc, cuff, 0f, cuff.perimeter(), true, 0f, 0f, WALL, cy - HALF_LEN, cy + HALF_LEN, CUFF, light);\n\t\tribbon(pose, vc, outer, 0f, outer.perimeter(), true, 0f, 0f, 0.06f, cy - 0.6f, cy + 0.6f, WHITE, light);',
		'\t\tfloat xo = L + GAP + RECAL_WALL;\n\t\tSection outer = cuff.grow(RECAL_WALL);\n\t\t// ---- the band and its black stripe\n\t\tribbon(pose, vc, cuff, 0f, cuff.perimeter(), true, 0f, 0f, RECAL_WALL, cy - RECAL_HALF_LEN, cy + RECAL_HALF_LEN, CUFF, light);\n\t\tribbon(pose, vc, outer, 0f, outer.perimeter(), true, 0f, 0f, 0.05f, cy - 0.42f, cy + 0.42f, WHITE, light);'],
	['float a = cy + end * 1.2f, b = cy + end * 2.45f, c = cy + end * 3.05f, d = cy + end * 3.4f;', 'float a = cy + end * 1.0f, b = cy + end * 1.85f, c = cy + end * 2.3f, d = cy + end * 2.6f;'],
	['box(pose, vc, xo - 0.05f, Math.min(a, b), -1.55f, xo + 0.24f, Math.max(a, b), 1.55f, CUFF, light);', 'box(pose, vc, xo - 0.05f, Math.min(a, b), -1.2f, xo + 0.2f, Math.max(a, b), 1.2f, CUFF, light);'],
	['box(pose, vc, xo - 0.05f, Math.min(b, c), -1.05f, xo + 0.2f, Math.max(b, c), 1.05f, CUFF, light);', 'box(pose, vc, xo - 0.05f, Math.min(b, c), -0.82f, xo + 0.17f, Math.max(b, c), 0.82f, CUFF, light);'],
	['box(pose, vc, xo - 0.05f, Math.min(c, d), -0.8f, xo + 0.28f, Math.max(c, d), 0.8f, PLATE, light);', 'box(pose, vc, xo - 0.05f, Math.min(c, d), -0.62f, xo + 0.23f, Math.max(c, d), 0.62f, PLATE, light);'],
	['box(pose, vc, xo + 0.2f, Math.min(b, c), -0.4f, xo + 0.24f, Math.max(b, c), 0.4f, WHITE, light, false);', 'box(pose, vc, xo + 0.17f, Math.min(b, c), -0.3f, xo + 0.2f, Math.max(b, c), 0.3f, WHITE, light, false);'],
	['pose.translate((xo + 0.27f) * S, cy * S, side * (RB - 0.15f) * S);', 'pose.translate((xo + 0.21f) * S, cy * S, side * (RB - 0.12f) * S);'],
	['cylinder(pose, vc, 0f, 0.62f, 0f, 0f, 0.3f, PLATE, light, 12);\n\t\t\tcylinder(pose, vc, 0.62f, 0.72f, 0f, 0f, 0.22f, RING, light, 12);', 'cylinder(pose, vc, 0f, 0.5f, 0f, 0f, 0.24f, PLATE, light, 12);\n\t\t\tcylinder(pose, vc, 0.5f, 0.58f, 0f, 0f, 0.17f, RING, light, 12);'],
];
for (const [a, b] of R) { if (!s.includes(a)) throw new Error('miss ' + a.slice(0, 70)); s = s.replace(a, b); }
fs.writeFileSync(f, crlf ? s.replace(/\n/g, '\r\n') : s);
console.log('ok');
