// v0.17.1 Overhaul / Dark Shadow / Engine: a JS copy of QuirkFrameworkGameTests.heavyUse / recovery (the pacing test),
// to tune per-move costs before CI runs the real one. node scratchpad/sim_quirk_kits3_pacing.js
function heavy(spec, ticks) {
  const dir = spec.builds ? 1 : -1;
  let v = spec.builds ? 0 : 100;
  const ready = spec.moves.map(() => 0);
  let next = 0;
  const add = (d) => {
    const toward = spec.builds ? d > 0 : d < 0;
    d *= toward ? spec.build : spec.recover;
    v = Math.max(0, Math.min(100, v + d));
  };
  for (let t = 0; t < ticks; t++) {
    if (spec.sustained) add(dir * spec.sustained / 20);
    if (spec.regen) add(-dir * spec.regen / 20);
    if (spec.moves.length && t % 40 === 0) {
      for (let k = 0; k < spec.moves.length; k++) {
        const i = (next + k) % spec.moves.length;
        if (ready[i] <= t) { add(dir * spec.moves[i][0]); ready[i] = t + spec.moves[i][1]; next = i + 1; break; }
      }
    }
    if (spec.builds ? v >= spec.danger : v <= spec.danger) return t;
  }
  return -1;
}
function rec(spec) {
  let v = spec.danger;
  for (let t = 0; t < 800; t++) {
    if (t >= spec.delay) {
      const d = -(spec.builds ? 1 : -1) * spec.rec / 20;
      const toward = spec.builds ? d > 0 : d < 0;
      v = Math.max(0, Math.min(100, v + d * (toward ? spec.build : spec.recover)));
    }
    if (spec.builds ? v <= spec.safe : v >= spec.safe) return t;
  }
  return -1;
}
const suit = 0.6;
const specs = {
  overhaul: { builds: false, build: 1, recover: 1, danger: 25, safe: 80, regen: 10 * 36 / 40, rec: 10, delay: 4,
    moves: [[12, 16], [-5, 20], [20, 30], [40, 200], [16, 24], [-8, 30], [10, 20], [6, 16], [-10, 100], [20, 20], [30, 900], [45, 600]] },
  dark_shadow: { builds: true, build: 0.20, recover: 1, danger: 75, safe: 30, sustained: 0.25 * 20, rec: 10, delay: 0,
    moves: [[2, 14], [3, 40], [6, 80], [6, 80], [5, 60], [4, 100], [3, 100], [4, 200], [10, 300]] },
  engine: { builds: true, build: 0.30, recover: 1.4, danger: 80, safe: 30, sustained: 5 * suit, rec: 2.5, delay: 0,
    moves: [[3 * suit, 16], [4.5 * suit, 16], [5 * suit, 40], [6 * suit, 60], [5 * suit, 70], [6 * suit, 80], [6 * suit, 100], [8 * suit, 120],
      [10 * suit, 140], [14 * suit, 240]] },
};
for (const [k, s] of Object.entries(specs)) {
  const h = heavy(s, 45 * 20), late = s.builds ? heavy(s, 180 * 20) : 'n/a', r = rec(s);
  console.log(k, '45s:', h < 0 ? '>45s' : h / 20 + 's', ' 3min reaches at:', late === -1 ? 'NEVER' : late === 'n/a' ? late : late / 20 + 's',
    ' recovery:', r / 20 + 's');
}
