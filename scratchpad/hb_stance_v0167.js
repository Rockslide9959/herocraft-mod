// v0.16.7 (user) Hulkbuster: re-key idle / walk / run to an upright, player-like stance + gait (a heavy weight shift),
// and add the broken-leg limps (limp_l / limp_r). Usage: node hb_stance_v0167.js <hulkbuster_body.animation.json>
const fs = require('fs');
const file = process.argv[2];
const raw = fs.readFileSync(file, 'utf8');
const crlf = raw.includes('\r\n');
const json = JSON.parse(raw);
const A = json.animations;
const P = 'animation.hulkbuster_body.';

const f4 = t => t.toFixed(4);
const r3 = v => Math.round(v * 1000) / 1000;
// keys: [[t, x, y, z], ...] -> geckolib channel (first key bare, the rest eased)
function chan(keys, easing = 'easeinoutsine') {
  const out = {};
  keys.forEach(([t, x, y, z], i) => {
    const v = [r3(x), r3(y), r3(z)];
    out[f4(t)] = i === 0 ? v : { vector: v, easing };
  });
  return out;
}
// a looping channel from evenly spaced samples over len (the last key repeats the first)
function loop(len, samples, axis = 0, base = [0, 0, 0]) {
  const n = samples.length;
  const keys = [];
  for (let i = 0; i <= n; i++) {
    const v = samples[i % n];
    const k = [...base];
    if (Array.isArray(v)) {
      k[0] = v[0]; k[1] = v[1]; k[2] = v[2];
    } else {
      k[axis] = v;
    }
    keys.push([len * i / n, k[0], k[1], k[2]]);
  }
  return keys;
}
const shift = (arr, by) => arr.map((_, i) => arr[(i + by) % arr.length]);
function bone(rot, pos) {
  const b = {};
  if (rot) b.rotation = chan(rot);
  if (pos) b.position = chan(pos);
  return b;
}

// ------------------------------------------------------------------ idle: upright, arms hanging, slow heavy breathing
{
  const L = 3.0;
  const breath = (a, b) => [[0, ...a], [1.5, ...b], [3.0, ...a]];
  A[P + 'idle'] = {
    loop: true,
    animation_length: L,
    bones: {
      root: { position: chan(breath([0, 0.1, 0], [0, 0.04, 0])), rotation: chan(breath([0, 0, 0], [0, 0, 0.5])) },
      waist: bone(breath([0, 0, 0], [0.8, 0, 0])),
      chest: bone(breath([0, 0, 0], [-1.6, 0, 0])),
      neck: bone(breath([0, 0, 0], [1.2, 0, 0])),
      backpack: bone(breath([0, 0, 0], [-0.8, 0, 0])),
      shoulder_r: bone(breath([0, 0, 0], [0, 0, -0.8])),
      shoulder_l: bone(breath([0, 0, 0], [0, 0, 0.8])),
      arm_r: bone(breath([0, 0, 2.5], [-1.2, 0, 3.4])),
      arm_l: bone(breath([0, 0, -2.5], [-1.2, 0, -3.4])),
      forearm_r: bone(breath([-4, 0, 0], [-5.5, 0, 0])),
      forearm_l: bone(breath([-4, 0, 0], [-5.5, 0, 0])),
      thigh_r: bone(breath([0, 0, 1.2], [-0.8, 0, 1.2])),
      thigh_l: bone(breath([0, 0, -1.2], [-0.8, 0, -1.2])),
      shin_r: bone(breath([1, 0, 0], [2.2, 0, 0])),
      shin_l: bone(breath([1, 0, 0], [2.2, 0, 0])),
      foot_r: bone(breath([-1, 0, 0], [-1.6, 0, 0])),
      foot_l: bone(breath([-1, 0, 0], [-1.6, 0, 0])),
    },
  };
}

// ------------------------------------------------------------------ the gait (walk / run share the shape)
// 8 samples per cycle; sample 0 = right heel strike, 4 = left heel strike.
function gait(name, L, s) {
  const thighR = s.thigh;               // right thigh x over the cycle
  const shinR = s.shin;
  const footR = s.foot;
  const armR = s.arm;                   // + = back
  const foreR = s.fore;
  const half = thighR.length / 2;
  const thighL = shift(thighR, half), shinL = shift(shinR, half), footL = shift(footR, half);
  const armL = shift(armR, half), foreL = shift(foreR, half);
  const zip = (xs, z, y = 0) => xs.map(x => [x, y, z]);
  A[P + name] = {
    loop: true,
    animation_length: L,
    bones: {
      root: {
        position: chan(loop(L, s.bob.map(v => [0, v, 0]))),
        rotation: chan(loop(L, s.sway.map(v => [0, 0, v]))),
      },
      hips: bone(loop(L, s.hipTwist.map(v => [0, v, 0]))),
      waist: bone(loop(L, s.lean.map(v => [v * 0.4, 0, 0]))),
      chest: bone(loop(L, s.lean.map((v, i) => [v, -s.hipTwist[i] * 1.2, 0]))),
      neck: bone(loop(L, s.lean.map(v => [-v * 0.6, 0, 0]))),
      thigh_r: bone(loop(L, zip(thighR, 1.2))),
      thigh_l: bone(loop(L, zip(thighL, -1.2))),
      shin_r: bone(loop(L, zip(shinR, 0))),
      shin_l: bone(loop(L, zip(shinL, 0))),
      foot_r: bone(loop(L, zip(footR, 0))),
      foot_l: bone(loop(L, zip(footL, 0))),
      arm_r: bone(loop(L, zip(armR, s.armOut))),
      arm_l: bone(loop(L, zip(armL, -s.armOut))),
      forearm_r: bone(loop(L, zip(foreR, 0))),
      forearm_l: bone(loop(L, zip(foreL, 0))),
    },
  };
}

gait('walk', 1.6, {
  //        strike  load   pass   push   toe-off swing  swing2 reach
  thigh: [-24, -16, -4, 8, 18, 2, -14, -22],
  shin: [4, 10, 6, 6, 14, 38, 42, 16],
  foot: [8, 2, -2, -6, -14, -12, -6, 4],
  arm: [15, 10, 2, -8, -15, -10, -2, 8],
  fore: [-6, -7, -9, -13, -16, -13, -9, -7],
  armOut: 3,
  // heavy: down hard on each strike, up over the passing leg; the body rolls onto the standing leg
  bob: [-0.9, -1.1, 0.5, 0.7, -0.9, -1.1, 0.5, 0.7],
  sway: [-0.6, -1.6, -1.8, -0.8, 0.6, 1.6, 1.8, 0.8],
  hipTwist: [-5, -3, 0, 3, 5, 3, 0, -3],
  lean: [2.5, 3, 2, 1.5, 2.5, 3, 2, 1.5],
});

gait('run', 1.0, {
  thigh: [-36, -20, 0, 18, 26, 4, -24, -36],
  shin: [8, 18, 12, 14, 30, 70, 74, 30],
  foot: [10, 0, -6, -16, -22, -14, -4, 6],
  arm: [30, 20, 2, -18, -30, -20, -2, 18],
  fore: [-60, -58, -55, -62, -72, -66, -58, -56],
  armOut: 4,
  bob: [-1.6, -2.0, 0.6, 1.4, -1.6, -2.0, 0.6, 1.4],
  sway: [-0.8, -1.8, -2, -1, 0.8, 1.8, 2, 1],
  hipTwist: [-7, -4, 0, 4, 7, 4, 0, -7],
  lean: [8, 9, 7, 6, 8, 9, 7, 6],
});

// ------------------------------------------------------------------ the limps (a broken leg drags, the body dips on it)
// limp_r: the RIGHT leg is broken. 2.0 s: left (good) step 0..1.0, a short quick step onto the bad right leg 1.0..1.5,
// the push off it 1.5..2.0. The right arm hangs, the body leans to the good side and dips hard on the bad plant.
function limp(name, badR) {
  const L = 2.0;
  const keysAt = (vals) => vals.map(([t, x, y, z]) => [t, x, y, z]);
  const mir = (k) => badR ? k : k.map(([t, x, y, z]) => [t, x, -y, -z]);
  const bad = badR ? 'r' : 'l';
  const good = badR ? 'l' : 'r';
  const sideZ = badR ? 1 : -1; // + z on the right thigh = out
  A[P + name] = {
    loop: true,
    animation_length: L,
    bones: {
      root: {
        position: chan(keysAt([[0, 0, -0.6, 0], [0.5, 0, 0.6, 0], [1.0, 0, -0.4, 0], [1.2, 0, -2.4, 0], [1.5, 0, -1.4, 0], [1.75, 0, 0.4, 0],
          [2.0, 0, -0.6, 0]])),
        rotation: chan(mir([[0, 0, 0, 1.5], [0.5, 0, 0, 2.5], [1.0, 0, 0, 0.5], [1.2, 0, 0, -3.5], [1.5, 0, 0, -2.0], [1.75, 0, 0, 1.0],
          [2.0, 0, 0, 1.5]])),
      },
      hips: bone(mir([[0, 0, -3, 0], [0.5, 0, 0, 0], [1.0, 0, 3, 0], [1.5, 0, 1, 0], [2.0, 0, -3, 0]])),
      waist: bone(mir([[0, 2, 0, 1], [1.2, 4, 0, -1.5], [1.6, 3, 0, 0], [2.0, 2, 0, 1]])),
      chest: bone(mir([[0, 3, 2, 2.5], [0.5, 2, 0, 3], [1.2, 6, -1, 0.5], [1.6, 4, -2, 1.5], [2.0, 3, 2, 2.5]])),
      neck: bone(mir([[0, -2, 0, -1.5], [1.2, -4, 0, 0], [2.0, -2, 0, -1.5]])),
      // the good leg: a normal, slightly long stride
      ['thigh_' + good]: bone(mir([[0, -22, 0, -1.2 * sideZ], [0.5, -4, 0, -1.2 * sideZ], [1.0, 14, 0, -1.2 * sideZ], [1.3, 20, 0, -1.2 * sideZ],
        [1.6, -2, 0, -1.2 * sideZ], [2.0, -22, 0, -1.2 * sideZ]])),
      ['shin_' + good]: bone([[0, 6, 0, 0], [0.5, 8, 0, 0], [1.0, 10, 0, 0], [1.3, 22, 0, 0], [1.6, 44, 0, 0], [2.0, 6, 0, 0]]),
      ['foot_' + good]: bone([[0, 8, 0, 0], [0.5, -2, 0, 0], [1.0, -8, 0, 0], [1.3, -16, 0, 0], [1.6, -6, 0, 0], [2.0, 8, 0, 0]]),
      // the broken leg: stiff (barely bends), swung out round from the hip and dragged; a short, careful plant
      ['thigh_' + bad]: bone(mir([[0, 8, 0, 1.5 * sideZ], [0.5, 10, 0, 4 * sideZ], [0.85, -2, 0, 6 * sideZ], [1.0, -8, 0, 3 * sideZ],
        [1.2, -6, 0, 1.5 * sideZ], [1.5, 2, 0, 1.5 * sideZ], [2.0, 8, 0, 1.5 * sideZ]])),
      ['shin_' + bad]: bone([[0, 3, 0, 0], [0.5, 5, 0, 0], [0.85, 7, 0, 0], [1.0, 3, 0, 0], [1.2, 2, 0, 0], [2.0, 3, 0, 0]]),
      ['foot_' + bad]: bone([[0, -10, 0, 0], [0.5, -14, 0, 0], [0.85, -6, 0, 0], [1.0, 2, 0, 0], [1.2, 0, 0, 0], [2.0, -10, 0, 0]]),
      // the bad side's arm hangs (a small swing), the good one swings to balance
      ['arm_' + bad]: bone(mir([[0, 2, 0, 5 * sideZ], [1.0, -3, 0, 6 * sideZ], [1.2, 1, 0, 9 * sideZ], [2.0, 2, 0, 5 * sideZ]])),
      ['forearm_' + bad]: bone([[0, -4, 0, 0], [1.2, -8, 0, 0], [2.0, -4, 0, 0]]),
      ['arm_' + good]: bone(mir([[0, 14, 0, -3 * sideZ], [0.5, 4, 0, -3 * sideZ], [1.0, -14, 0, -3 * sideZ], [1.3, -8, 0, -6 * sideZ],
        [1.6, 4, 0, -4 * sideZ], [2.0, 14, 0, -3 * sideZ]])),
      ['forearm_' + good]: bone([[0, -8, 0, 0], [1.0, -16, 0, 0], [1.3, -18, 0, 0], [2.0, -8, 0, 0]]),
    },
  };
  // channels built with bone() from [t,x,y,z] lists -- bone() expects chan-ready key lists
}
// bone() takes key lists of [t, x, y, z]
limp('limp_r', true);
limp('limp_l', false);

let out = JSON.stringify(json, null, '\t');
fs.writeFileSync(file, crlf ? out.replace(/\n/g, '\r\n') : out);
console.log('written', Object.keys(A).length, 'clips');
