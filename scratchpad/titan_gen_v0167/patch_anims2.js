const fs = require('fs');
process.chdir(__dirname);
let s = fs.readFileSync('anims.js', 'utf8');
const a = s.indexOf("	// ---------------- new clips for the revamp's moves");
const b = s.indexOf('	// ---------------- the shift itself');
if (a < 0 || b < 0) throw new Error('markers');
s = s.slice(0, a) + fs.readFileSync('newclips.txt', 'utf8') + s.slice(b);
const rep = (x, y) => { if (!s.includes(x)) throw new Error('miss ' + x.slice(0, 60)); s = s.replace(x, y); };
// REVERTING (60 ticks): the body stays standing and steams while the shifter climbs out -- it sags a little, no shrinking
rep(`	clip('reversion', 2.0, 'hold', [
		[0, R({ rootScale: 1 })],
		[1.0, R({ b: [18, 0, 0], h: [14, 0, 0], rl: [-30, 0, 0], ll: [-30, 0, 0], rs: 60, ls: 60, rootScale: 0.8 }), { ground: 'all' }],
		[2.0, Object.assign({}, kneel, { rootScale: 0.25 }), { ground: 'all' }],
	], { allBones: true });`,
`	const sag = R({ b: [6, 0, 0], h: [12, 0, 0], j: [6, 0, 0], ra: [6, 0, ARM + 2], la: [6, 0, -ARM - 2], rf: -12, lf: -12, rl: [-4, 0, 0], ll: [-3, 0, 0], rs: 8, ls: 6 });
	clip('reversion', 3.0, 'hold', [
		[0, R({})],
		[0.6, R({ b: [-4, 0, 0], h: [-14, 0, 0], j: [12, 0, 0] })],
		[1.6, R({ b: [3, 0, 0], h: [6, 0, 0], j: [8, 0, 0], ra: [4, 0, ARM + 1], la: [4, 0, -ARM - 1], rf: -10, lf: -10 })],
		[3.0, sag],
	], { allBones: true });`);
rep(`		[0, R({ b: [2, 0, 0], fl: [0, 0, 0], fr: [0, 0, 0] })],`, `		[0, Object.assign({}, sag, { fl: [0, 0, 0], fr: [0, 0, 0] })],`);
// a defeated Titan's corpse starts where the death clip ended (down on its hands)
rep(`	// ---------------- the corpse: falls to its knees`, `	A['animation.titan.corpse_defeat'] = write(M, 0.5, 'hold', [[0, A['animation.titan.death'] ? lastPose(A['animation.titan.death']) : {}], [0.5, A['animation.titan.death'] ? lastPose(A['animation.titan.death']) : {}]], { ground: false, raw: true });

	// ---------------- the corpse: falls to its knees`);
rep(`function animations(M) {`, `/** The final keyed pose of a written clip, as a frame pose (rotations + root position). */
function lastPose(clip) {
	const pose = {};
	for (const [bn, ch] of Object.entries(clip.bones)) {
		const pick = o => { const ks = Object.keys(o).sort((x, y) => +x - +y); const v = o[ks[ks.length - 1]]; return Array.isArray(v) ? v : v.vector; };
		if (ch.rotation) pose[bn] = pick(ch.rotation);
		if (bn === 'root' && ch.position) pose.root = pick(ch.position);
	}
	return pose;
}

function animations(M) {`);
fs.writeFileSync('anims.js', s);
console.log('ok');
