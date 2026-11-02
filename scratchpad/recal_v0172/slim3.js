// v0.17.2 round 4 (user: "make the wristband 2 pixels and resize the watch face and dial to fit the wristband size"): band 2 px along
// the arm, the bezel 3 px across (the same 1.5x the band as round 3), the knobs and stripe scaled with it
const fs = require('fs');
const f = 'src/client/java/com/projecthero/mod/client/omnitrix/OmnitrixModel.java';
let s = fs.readFileSync(f, 'utf8');
const crlf = s.includes('\r\n'); s = s.replace(/\r\n/g, '\n');
const R = [
	['private static final float RB = 3.0f, RT = 2.65f, RI = 2.15f, BEZEL_H = 0.3f;', 'private static final float RB = 1.5f, RT = 1.32f, RI = 1.08f, BEZEL_H = 0.24f;'],
	['RECAL_WALL = 0.18f, RECAL_HALF_LEN = 2.0f;', 'RECAL_WALL = 0.16f, RECAL_HALF_LEN = 1.0f;'],
	['private static final float RECAL_FACE_X = 0.2f;', 'private static final float RECAL_FACE_X = 0.16f;'],
	['cy - 0.55f, cy + 0.55f, WHITE, light);', 'cy - 0.3f, cy + 0.3f, WHITE, light);'],
	['pose.translate((xo + 0.15f) * S, cy * S, side * (RB - 0.1f) * S);', 'pose.translate((xo + 0.12f) * S, cy * S, side * (RB - 0.06f) * S);'],
	['cylinder(pose, vc, 0f, 0.55f, 0f, 0f, 0.28f, PLATE, light, 12);\n\t\t\tcylinder(pose, vc, 0.55f, 0.65f, 0f, 0f, 0.2f, RING, light, 12);', 'cylinder(pose, vc, 0f, 0.36f, 0f, 0f, 0.17f, PLATE, light, 12);\n\t\t\tcylinder(pose, vc, 0.36f, 0.42f, 0f, 0f, 0.12f, RING, light, 12);'],
];
for (const [a, b] of R) { if (!s.includes(a)) throw new Error('miss ' + a.slice(0, 70)); s = s.replace(a, b); }
fs.writeFileSync(f, crlf ? s.replace(/\n/g, '\r\n') : s);
console.log('ok');
