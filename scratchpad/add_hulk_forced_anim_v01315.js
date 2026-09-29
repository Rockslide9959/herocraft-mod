// v0.13.15: adds animation.hulk.transform_forced -- the unwilling change on the Hulk model. It lines up with
// HulkPose (Banner's vanilla-model kneel) through the cross-fade: root down 8 px, body leaning ~22 deg from the hips,
// legs folded back 70 deg, arms up clutching the head. Then he pounds the ground, rises and roars (the same roar pose
// as animation.hulk.transform), timed to HulkConfig: kneel 20 t, growth 60 t, rise 24 t (5.2 s in all).
// Sign conventions (checked against the existing clips): +X on body/head = lean/bow forward, leg +X = foot back,
// arm -X = raised forward, right-arm +Z = out to the side (so, with the arm raised, in toward the head).
const fs = require('fs');
const file = 'src/main/resources/assets/projecthero/animations/hulk.animation.json';
const json = JSON.parse(fs.readFileSync(file, 'utf8'));

const r = v => Math.round(v * 100) / 100;
const key = s => r(s).toFixed(2);
const bones = { root: { position: {} }, body: { rotation: {} }, head: { rotation: {} },
  right_arm: { rotation: {} }, left_arm: { rotation: {} }, right_leg: { rotation: {} }, left_leg: { rotation: {} } };
function pose(t, p) {
  const k = key(t);
  bones.root.position[k] = [0, r(p.rootY), 0];
  bones.body.rotation[k] = [r(p.body), 0, r(p.bodyZ || 0)];
  bones.head.rotation[k] = [r(p.head), 0, r(p.headZ || 0)];
  bones.right_arm.rotation[k] = [r(p.armX), r(p.armY || 0), r(p.armZ)];
  bones.left_arm.rotation[k] = [r(p.armXL !== undefined ? p.armXL : p.armX), r(-(p.armY || 0)), r(-p.armZ)];
  bones.right_leg.rotation[k] = [r(p.leg), 0, r(p.legZ || 0)];
  bones.left_leg.rotation[k] = [r(p.leg), 0, r(-(p.legZ || 0))];
}
const KNEEL = { rootY: -8, body: 22, head: 10, armX: -146, armZ: 26, leg: 70, legZ: 4.5 };
pose(0.0, { rootY: 0, body: 0, head: 0, armX: 0, armZ: 4, leg: 0 });
pose(0.3, KNEEL);
// 0.3 .. 3.2 s: on his knees fighting it -- a shiver that grows with the change
for (let t = 0.4; t < 3.2; t += 0.1) {
  const grow = Math.max(0, Math.min(1, (t - 1.0) / 3.0));
  const a = 1.5 + 3.5 * grow;
  const s1 = Math.sin(t * 37), s2 = Math.cos(t * 29);
  pose(t, { ...KNEEL, body: KNEEL.body + s1 * a * 0.6 + grow * 6, bodyZ: s2 * a * 0.3, head: KNEEL.head + s2 * a,
    headZ: s1 * a * 0.5, armX: KNEEL.armX + s1 * a, armXL: KNEEL.armX - s1 * a, armZ: KNEEL.armZ + s2 * a * 0.4 });
}
// 3.2 .. 3.9 s: tears his hands off his head and slams both fists into the ground
pose(3.3, { ...KNEEL, body: 38, head: -4, armX: -170, armZ: 30 });
pose(3.55, { ...KNEEL, body: 46, head: 18, armX: -48, armZ: 10 });
pose(3.9, { ...KNEEL, body: 44, head: 14, armX: -44, armZ: 12 });
// 4.0 .. 4.3 s: he surges up
pose(4.05, { rootY: -3.5, body: 20, head: -8, armX: -30, armZ: 30, leg: 30, legZ: 3 });
pose(4.3, { rootY: 0.4, body: -12, head: -38, armX: -40, armZ: 72, leg: 0, legZ: 6 });
// 4.3 .. 4.8 s: the roar
pose(4.8, { rootY: 0.2, body: -10, head: -34, armX: -44, armZ: 76, leg: 0, legZ: 6 });
pose(5.2, { rootY: 0, body: 8, head: -8, armX: -4, armZ: 12, leg: 0, legZ: 0 });

json.animations['animation.hulk.transform_forced'] = { animation_length: 5.2, bones };
fs.writeFileSync(file, JSON.stringify(json, null, 1) + '\n');
console.log('keyframes:', Object.keys(bones.body.rotation).length);
