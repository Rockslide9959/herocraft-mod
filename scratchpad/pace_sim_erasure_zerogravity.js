// v0.17.1 Erasure + Zero Gravity revamp: offline copy of QuirkFrameworkGameTests.heavyUse (the meter-pacing test's script)
// used to tune the per-move costs.   node scratchpad/pace_sim_erasure_zerogravity.js
function run(o, ticks) {
	let m = o.start || 0;
	const ready = o.moves.map(() => 0);
	let next = 0;
	for (let t = 0; t < ticks; t++) {
		const ramp = 1 + (o.rampMax - 1) * Math.min(1, t / o.rampTicks);
		m += o.build * o.sus * ramp / 20;
		if (o.moves.length && t % 40 == 0) {
			for (let k = 0; k < o.moves.length; k++) {
				const i = (next + k) % o.moves.length;
				if (ready[i] <= t) { m += o.build * o.moves[i][0]; ready[i] = t + o.moves[i][1]; next = i + 1; break; }
			}
		}
		if (m >= o.danger) return t / 20;
	}
	return -1;
}
const zg = (b, mv) => ({ build: b, sus: 0.5 * 6.5, rampMax: 2.5, rampTicks: 600, danger: 85, moves: mv });
const base = [[3, 10], [3, 160], [3, 200], [3, 400], [3, 120]];
console.log('zg framework', run(zg(0.2, base), 3600));
const args = process.argv.slice(2).map(Number);
const nw = [[3, 10], [3, 160], [3, 200], [3, 400], [3, 120], [6, 400], [1.5, 16], [3, 80]];
for (const b of [0.2, 0.18, 0.17, 0.16]) console.log('zg revamp', b, run(zg(b, nw), 3600));
const er = (b, mv) => ({ build: b, sus: 10, rampMax: 1, rampTicks: 1, danger: 90, moves: mv });
console.log('er framework', run(er(0.18, []), 3600));
for (const b of [0.18, 0.16, 0.15]) console.log('er revamp', b, run(er(b, [[15, 160], [4, 300]]), 3600));
