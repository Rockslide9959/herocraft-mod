// v0.16.7 (user) Hulkbuster: "give the hulkbuster suit a landing an animation when landing after flying".
// Adds two clips to hulkbuster_body.animation.json:
//   superhero_land  (1.15 s) -- the heavy three-point landing: slam into a deep lunge, right knee down, left foot planted
//                    forward, right fist driven into the ground, head down; then the look-up, the push off the fist and
//                    the rise back to the idle stance (its last frame is idle's first, so the hand-off is seamless).
//   touchdown_land  (0.6 s)  -- the gentle hover touchdown: the knees soak it up, arms out for balance, back to idle.
// The poses are SOLVED, not eyeballed: a sagittal-plane forward-kinematics model of the geo's real pivots (hip 18, knee 9,
// ankle 3; waist 21, chest 27, shoulder 33, elbow 27, wrist 21, fist bottom 17.4) puts the left foot flat on the ground,
// the right knee on the ground and the right fist's knuckles on the ground at the impact frame.
// Usage: node hb_landing_v0167.js <hulkbuster_body.animation.json>
const fs = require('fs');
const file = process.argv[2];
const raw = fs.readFileSync(file, 'utf8');
const crlf = raw.includes('\r\n');
const json = JSON.parse(raw);
const A = json.animations;
const P = 'animation.hulkbuster_body.';

const D = Math.PI / 180;
const r3 = v => Math.round(v * 1000) / 1000;
const f4 = t => t.toFixed(4);

// ------------------------------------------------------------------ kinematics (y up, z forward; angles in degrees)
// A bone's cumulative x rotation TH turns its rest vector (y, z) by y' = y cos - z sin, z' = y sin + z cos.
// For a bone hanging down (rest (-1, 0)) its world direction is (-cos TH, -sin TH): it points FORWARD for negative TH.
function rot(y, z, th) {
  const c = Math.cos(th * D), s = Math.sin(th * D);
  return [y * c - z * s, y * s + z * c];
}
// 2-link IK in the plane: from joint J to target T, links l1 / l2, bend 'fwd' (knee: middle joint ahead) or 'back'
// (elbow: middle joint behind). Returns the two links' world forward-from-down angles (deg).
function ik2(J, T, l1, l2, bend) {
  const dy = T[0] - J[0], dz = T[1] - J[1];
  let d = Math.hypot(dy, dz);
  d = Math.min(d, (l1 + l2) * 0.999);
  const phi = Math.atan2(dz, -dy) / D; // target direction, forward from straight down
  const a1 = Math.acos((l1 * l1 + d * d - l2 * l2) / (2 * l1 * d)) / D;
  const p1 = bend === 'fwd' ? phi + a1 : phi - a1;
  const K = [J[0] - l1 * Math.cos(p1 * D), J[1] + l1 * Math.sin(p1 * D)];
  const p2 = Math.atan2(T[1] - K[1], -(T[0] - K[0])) / D;
  return [p1, p2, K];
}

// Solve one full-body pose. Inputs (all deg / px):
//   hipY  hip joint height above the ground (rest 18); rz root forward offset
//   w, c  waist / chest forward lean; neck
//   lAnkle [y, z] left ankle target (foot flat); rKnee: {kneeOnGround: true, shin: back angle} or rAnkle [y, z]
//   fist  [y, z] right fist-bottom target (or null -> rArm given directly); shDrop shoulder_r drop
function solve(o) {
  const ry = o.hipY - 18;
  const hip = [o.hipY, o.rz || 0];
  const out = {};
  out.root = { pos: [0, ry, o.rz || 0], rot: o.rootRot || [0, 0, 0] };
  out.waist = [o.w, 0, o.waistZ || 0];
  out.chest = [o.c, o.chestY || 0, o.chestZ || 0]; // waistZ / chestZ < 0 roll the right shoulder down (toward the fist)
  out.neck = [o.neck || 0, 0, 0];
  // left leg: foot flat at the ankle target, knee forward
  {
    const [a, b] = ik2(hip, o.lAnkle, 9, 6, 'fwd');
    out.thigh_l = [-a, o.lYaw || 0, o.lSplay != null ? -o.lSplay : -6]; // lYaw swings a raised knee out (Y applies after X)
    out.shin_l = [a - b, 0, 0];
    out.foot_l = [b + (o.lToe || 0), 0, 0];
    out._lAnkle = o.lAnkle;
  }
  // right leg
  if (o.rKnee) {
    // knee on the ground (its joint 3 above it: the shin plate's half-thickness), thigh slanting back, shin lying back
    const ca = Math.max(-1, Math.min(1, (o.hipY - o.rKnee.y) / 9));
    const a = -Math.acos(ca) / D * (o.rKnee.back ? 1 : -1);
    const b = -o.rKnee.shin; // shin forward-from-down angle (negative = back)
    out.thigh_r = [-a, 0, o.rSplay != null ? o.rSplay : 6];
    out.shin_r = [a - b, 0, 0];
    out.foot_r = [b - (o.rKnee.footAbs || 0), 0, 0]; // cumulative -b + f = -footAbs: the foot hangs at footAbs (fwd)
    out._rKnee = [o.hipY - 9 * Math.cos(a * D), (o.rz || 0) + 9 * Math.sin(a * D)];
  } else {
    const [a, b] = ik2(hip, o.rAnkle, 9, 6, 'fwd');
    out.thigh_r = [-a, 0, o.rSplay != null ? o.rSplay : 6];
    out.shin_r = [a - b, 0, 0];
    out.foot_r = [b + (o.rToe || 0), 0, 0];
  }
  // torso chain -> right shoulder (world)
  const waistP = [21 + ry, o.rz || 0];
  const cOff = rot(6, 0, o.w);
  const chestP = [waistP[0] + cOff[0], waistP[1] + cOff[1]];
  const T = o.w + o.c;
  const sOff = rot(6 - (o.shDrop || 0), 0, T);
  const sh = [chestP[0] + sOff[0], chestP[1] + sOff[1]];
  out._shoulder = sh;
  out.shoulder_r = { rot: [0, 0, 0], pos: [0, -(o.shDrop || 0), 0] };
  out.shoulder_l = { rot: [0, 0, 0], pos: [0, -(o.shDropL || 0), 0] };
  if (o.fist) {
    const [p1, p2] = ik2(sh, o.fist, 6, 9.6, 'back');
    // cumulative: arm TH = T + a  with world fwd angle p1 = -TH  ->  a = -p1 - T ; forearm f = p1 - p2
    out.arm_r = [-p1 - T, 0, o.armRZ != null ? o.armRZ : 8];
    out.forearm_r = [p1 - p2, 0, 0];
    out._fistReach = Math.hypot(o.fist[0] - sh[0], o.fist[1] - sh[1]);
  } else {
    out.arm_r = o.armR;
    out.forearm_r = [o.foreR, 0, 0];
  }
  out.arm_l = o.armL;
  out.forearm_l = [o.foreL, 0, 0];
  out.hips = [0, o.hipsY || 0, 0];
  out.backpack = [o.backpack || 0, 0, 0];
  return out;
}

// the idle clip's first frame (exactly), so the clips hand back to idle with no pop
const IDLE = {
  root: { pos: [0, 0.1, 0], rot: [0, 0, 0] },
  hips: [0, 0, 0], waist: [0, 0, 0], chest: [0, 0, 0], neck: [0, 0, 0], backpack: [0, 0, 0],
  shoulder_r: { rot: [0, 0, 0], pos: [0, 0, 0] }, shoulder_l: { rot: [0, 0, 0], pos: [0, 0, 0] },
  arm_r: [0, 0, 2.5], arm_l: [0, 0, -2.5], forearm_r: [-4, 0, 0], forearm_l: [-4, 0, 0],
  thigh_r: [0, 0, 1.2], thigh_l: [0, 0, -1.2], shin_r: [1, 0, 0], shin_l: [1, 0, 0], foot_r: [-1, 0, 0], foot_l: [-1, 0, 0],
};

const ROT_BONES = ['hips', 'waist', 'chest', 'neck', 'backpack', 'arm_r', 'arm_l', 'forearm_r', 'forearm_l', 'thigh_r', 'thigh_l',
  'shin_r', 'shin_l', 'foot_r', 'foot_l'];

// keys: [{t, pose, ease}] -> geckolib bones
function clip(name, len, keys) {
  const bones = {};
  const put = (bone, chan, t, v, ease, first) => {
    bones[bone] = bones[bone] || {};
    bones[bone][chan] = bones[bone][chan] || {};
    const vec = v.map(r3);
    bones[bone][chan][f4(t)] = first ? vec : { vector: vec, easing: ease };
  };
  keys.forEach((k, i) => {
    const first = i === 0;
    const e = k.ease || 'easeinoutsine';
    const p = k.pose;
    put('root', 'position', k.t, p.root.pos, e, first);
    put('root', 'rotation', k.t, p.root.rot, e, first);
    for (const b of ROT_BONES) {
      put(b, 'rotation', k.t, p[b], e, first);
    }
    for (const b of ['shoulder_r', 'shoulder_l']) {
      put(b, 'rotation', k.t, p[b].rot, e, first);
      put(b, 'position', k.t, p[b].pos, e, first);
    }
  });
  A[P + name] = { animation_length: len, bones };
}

// a blend of two poses (for in-betweens), with optional per-bone additions
function mix(a, b, t, add = {}) {
  const lerp = (x, y) => x.map((v, i) => v + (y[i] - v) * t);
  const o = {};
  o.root = { pos: lerp(a.root.pos, b.root.pos), rot: lerp(a.root.rot, b.root.rot) };
  for (const k of ROT_BONES) o[k] = lerp(a[k], b[k]);
  for (const k of ['shoulder_r', 'shoulder_l']) o[k] = { rot: lerp(a[k].rot, b[k].rot), pos: lerp(a[k].pos, b[k].pos) };
  for (const [k, v] of Object.entries(add)) {
    if (k === 'rootPos') o.root.pos = o.root.pos.map((x, i) => x + v[i]);
    else o[k] = o[k].map((x, i) => x + v[i]);
  }
  return o;
}

// ================================================================== the heavy three-point landing
// impact: hip at 7.2, right knee on the ground well back, left foot planted ahead, torso leaning hard over (62 deg) and rolled down to the right, right fist
// planted just ahead of the right shoulder line, left arm flung out and back for balance, head down
const IMPACT = solve({
  hipY: 7.2, rz: 0,
  w: 30, c: 32, waistZ: -8, chestZ: -6, neck: 2, backpack: -4,
  lAnkle: [3, 8.5], lYaw: 24, lSplay: 4, lToe: 0,
  rKnee: { y: 3.1, back: true, shin: 84, footAbs: -10 }, rSplay: 10,
  fist: [1.9, 9.0], shDrop: 2.6, armRZ: 13, // y +1.8: the roll lowers the shoulder that much (not in the sagittal solve)
  armL: [32, 0, -52], foreL: -26, shDropL: 0.6,
});
// the overshoot two ticks later: everything settles a hair deeper (the weight arriving)
const SETTLE = solve({
  hipY: 6.8, rz: 0,
  w: 32, c: 33, waistZ: -9, chestZ: -6, neck: 6, backpack: -5,
  lAnkle: [3, 8.5], lYaw: 26, lSplay: 4, lToe: 0,
  rKnee: { y: 3.0, back: true, shin: 86, footAbs: -12 }, rSplay: 11,
  fist: [1.8, 8.8], shDrop: 3.0, armRZ: 14,
  armL: [36, 0, -56], foreL: -30, shDropL: 0.8,
});
// the hold: the iconic look-up (the chest lifts a little, the neck pitches the head up to look ahead)
const LOOKUP = solve({
  hipY: 7.1, rz: 0,
  w: 29, c: 29, waistZ: -7, chestZ: -5, neck: -40, backpack: -3,
  lAnkle: [3, 8.5], lYaw: 24, lSplay: 4, lToe: 0,
  rKnee: { y: 3.1, back: true, shin: 84, footAbs: -10 }, rSplay: 10,
  fist: [1.9, 8.6], shDrop: 2.4, armRZ: 12,
  armL: [26, 0, -46], foreL: -22, shDropL: 0.5,
});
// the push: the right arm drives the body up off the fist, the back knee leaves the ground, the torso comes up
const PUSH = solve({
  hipY: 11.0, rz: 0,
  w: 22, c: 18, neck: -14, backpack: -2,
  lAnkle: [3, 5.0], lYaw: 14, lSplay: 4, lToe: 0,
  rAnkle: [5.2, -6.5], rSplay: 6, rToe: -14,
  armR: [-26, 0, 9], foreR: -30,
  armL: [12, 0, -26], foreL: -18,
});
// almost up: the back foot has come through under the body, the knees still soft
const RISE = solve({
  hipY: 15.4, rz: 0,
  w: 7, c: 4, neck: -3, backpack: -1,
  lAnkle: [3, 1.6], lSplay: 2.5, lToe: 0,
  rAnkle: [3, -0.8], rSplay: 2.5, rToe: 0,
  armR: [-6, 0, 5], foreR: -12,
  armL: [2, 0, -6], foreL: -9,
});
// the first contact: legs starting to fold, arms still out from the flight, the fist already swinging down
const CONTACT = solve({
  hipY: 14.2, rz: 0,
  w: 14, c: 14, neck: 4, backpack: -2,
  lAnkle: [3, 4.0], lYaw: 8, lSplay: 6, lToe: 0,
  rAnkle: [4.2, -3.5], rSplay: 8, rToe: -10,
  armR: [-48, 0, 26], foreR: -34,
  armL: [-10, 0, -40], foreL: -30,
});

clip('superhero_land', 1.15, [
  { t: 0.0, pose: CONTACT },
  { t: 0.15, pose: IMPACT, ease: 'easeincubic' }, // driven into the ground: accelerate into the impact
  { t: 0.25, pose: SETTLE, ease: 'easeoutquad' },
  { t: 0.4, pose: mix(SETTLE, LOOKUP, 0.35), ease: 'easeinoutsine' },
  { t: 0.62, pose: LOOKUP, ease: 'easeinoutsine' },
  { t: 0.72, pose: mix(LOOKUP, PUSH, 0.25, { neck: [4, 0, 0] }), ease: 'easeinoutsine' }, // a breath before the push
  { t: 0.88, pose: PUSH, ease: 'easeinoutsine' },
  { t: 1.02, pose: RISE, ease: 'easeoutquad' },
  { t: 1.15, pose: IDLE, ease: 'easeinoutsine' },
]);

// ================================================================== the light touchdown
const SOAK = solve({
  hipY: 14.6, rz: 0,
  w: 10, c: 8, neck: -4, backpack: -2,
  lAnkle: [3, 1.8], lSplay: 5, lToe: 0,
  rAnkle: [3, -1.2], rSplay: 5, rToe: 0,
  armR: [-14, 0, 18], foreR: -22,
  armL: [-10, 0, -18], foreL: -20,
});
const SOAK_IN = solve({
  hipY: 16.6, rz: 0,
  w: 4, c: 4, neck: 0, backpack: -1,
  lAnkle: [3, 1.0], lSplay: 4, lToe: 0,
  rAnkle: [3, -0.6], rSplay: 4, rToe: 0,
  armR: [-10, 0, 24], foreR: -26,
  armL: [-8, 0, -24], foreL: -24,
});
clip('touchdown_land', 0.6, [
  { t: 0.0, pose: SOAK_IN },
  { t: 0.12, pose: SOAK, ease: 'easeoutquad' },
  { t: 0.3, pose: mix(SOAK, IDLE, 0.45), ease: 'easeinoutsine' },
  { t: 0.6, pose: IDLE, ease: 'easeinoutsine' },
]);

// report the contact points so the poses can be checked
const dbg = (n, p) => console.log(n, 'shoulder', p._shoulder.map(r3), 'fistReach', p._fistReach && r3(p._fistReach), 'rKnee',
  p._rKnee && p._rKnee.map(r3), 'thigh_r', p.thigh_r.map(r3), 'shin_r', p.shin_r.map(r3), 'foot_r', p.foot_r.map(r3), 'thigh_l',
  p.thigh_l.map(r3), 'shin_l', p.shin_l.map(r3), 'foot_l', p.foot_l.map(r3), 'arm_r', p.arm_r.map(r3), 'forearm_r', p.forearm_r.map(r3));
dbg('IMPACT', IMPACT);
dbg('SETTLE', SETTLE);
dbg('LOOKUP', LOOKUP);
dbg('PUSH', PUSH);

let out = JSON.stringify(json, null, '\t');
fs.writeFileSync(file, crlf ? out.replace(/\n/g, '\r\n') : out);
console.log('written', Object.keys(A).length, 'clips');
