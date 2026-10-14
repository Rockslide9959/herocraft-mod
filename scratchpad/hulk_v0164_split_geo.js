// v0.16.4: segment the Hulk's limbs at the elbows / knees (box UV kept; each segment's UV offset moved down the
// texture so its side faces show exactly its slice of the original 12 px limb texture).
const fs = require('fs');
const path = process.argv[2];
const g = JSON.parse(fs.readFileSync(path, 'utf8'));
const geo = g['minecraft:geometry'][0];
const bones = geo.bones;
const byName = Object.fromEntries(bones.map(b => [b.name, b]));
if (byName.right_forearm) { console.log('already split'); process.exit(0); }

// segments top-down: [name suffix, top y (px from the limb top), height]
const ARM = [['', 0, 6], ['forearm', 6, 3], ['hand', 9, 3]];
const LEG = [['', 0, 6], ['shin', 6, 4], ['foot', 10, 2]];

function split(limbName, segs, childNames) {
	const limb = byName[limbName];
	const cubes = limb.cubes;
	const out = []; // new bones after the limb
	const cx = cubes[0].origin[0] + cubes[0].size[0] / 2;
	const top = cubes[0].origin[1] + cubes[0].size[1];
	let parent = limbName;
	const segCubes = segs.map(([suffix, off, h]) => cubes.map(c => {
		const y0 = top - off - h;
		return Object.assign({}, c, {
			origin: [c.origin[0], y0, c.origin[2]],
			size: [c.size[0], h, c.size[2]],
			uv: [c.uv[0], c.uv[1] + off]
		});
	}));
	limb.cubes = segCubes[0];
	for (let i = 1; i < segs.length; i++) {
		const [suffix, off] = segs[i];
		const name = childNames[i - 1];
		const b = { name, parent, pivot: [cx, top - off, 0], cubes: segCubes[i] };
		out.push(b);
		parent = name;
	}
	// insert right after the limb
	const idx = bones.indexOf(limb);
	bones.splice(idx + 1, 0, ...out);
}
split('right_arm', ARM, ['right_forearm', 'right_hand']);
split('left_arm', ARM, ['left_forearm', 'left_hand']);
split('right_leg', LEG, ['right_shin', 'right_foot']);
split('left_leg', LEG, ['left_leg_shin_tmp', 'left_foot']);
// name fix (keep the helper generic)
for (const b of bones) {
	if (b.name === 'left_leg_shin_tmp') b.name = 'left_shin';
	if (b.parent === 'left_leg_shin_tmp') b.parent = 'left_shin';
}
// gear that sits on the lower arm / in the fist follows the new segments
const reparent = { gladiator_bracer_right: 'right_forearm', gladiator_bracer_left: 'left_forearm',
	gladiator_hammer: 'right_hand', gladiator_axe: 'left_hand' };
for (const b of bones) {
	if (reparent[b.name]) b.parent = reparent[b.name];
}
fs.writeFileSync(path, JSON.stringify(g, null, '\t') + '\n');
console.log(bones.map(b => b.name + '<' + (b.parent || '') + ' ' + JSON.stringify(b.pivot)).join('\n'));
