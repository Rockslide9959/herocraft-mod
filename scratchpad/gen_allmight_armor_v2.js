// Builds the v0.12.35 All Might armour model from the supplied Blockbench file (all might/AllMight.bbmodel):
//   textures/armor/all_might.png   the bbmodel's embedded skin, byte-for-byte
//   geo/all_might.geo.json         a player rig (armorHead/Body/RightArm/LeftArm/RightLeg/LeftLeg) with the model's base + outer layer cubes,
//                                  pushed OUT by 0.3 so the armour sits over the wearer's own skin instead of z-fighting with it
//                                  (the hat layer stays 0.6, not 0.8, so the head is not a balloon). Boots bones exist but are cubeless:
//                                  the model has full-length legs and no separate boots.
// Run from the repo root:  node scratchpad/gen_allmight_armor_v2.js [path/to/AllMight.bbmodel]
const fs = require('fs');
const path = require('path');

const ASSETS = path.join(__dirname, '..', 'src/main/resources/assets/projecthero/');
const BBMODEL = process.argv[2] || 'C:/Users/ethan/OneDrive/Desktop/3d minecraft models/all might/AllMight.bbmodel';
const bb = JSON.parse(fs.readFileSync(BBMODEL, 'utf8'));
fs.writeFileSync(ASSETS + 'textures/armor/all_might.png', Buffer.from(bb.textures[0].source.split(',')[1], 'base64'));

const OUT = 0.3;
const cube = (origin, size, uv, inflate) => ({ origin, size, uv, inflate });
// Blockbench's Bedrock export flips X: the model's RIGHT limbs sit at +x in the bbmodel, at -x in the geo file.
const bones = [
	{ name: 'armorBody', pivot: [0, 24, 0], cubes: [cube([-4, 12, -2], [8, 12, 4], [16, 16], OUT), cube([-4, 12, -2], [8, 12, 4], [16, 32], 0.25 + OUT)] },
	{ name: 'armorHead', pivot: [0, 24, 0] }, // no helmet: the model's head is empty, so the wearer's own head and hat layer show
	{ name: 'armorRightArm', pivot: [-5, 22, 0], cubes: [cube([-8, 12, -2], [4, 12, 4], [40, 16], OUT), cube([-8, 12, -2], [4, 12, 4], [40, 32], 0.25 + OUT)] },
	{ name: 'armorLeftArm', pivot: [5, 22, 0], cubes: [cube([4, 12, -2], [4, 12, 4], [32, 48], OUT), cube([4, 12, -2], [4, 12, 4], [48, 48], 0.25 + OUT)] },
	{ name: 'armorRightLeg', pivot: [-2, 12, 0], cubes: [cube([-4, 0, -2], [4, 12, 4], [0, 16], OUT), cube([-4, 0, -2], [4, 12, 4], [0, 32], 0.25 + OUT)] },
	{ name: 'armorLeftLeg', pivot: [2, 12, 0], cubes: [cube([0, 0, -2], [4, 12, 4], [16, 48], OUT), cube([0, 0, -2], [4, 12, 4], [0, 48], 0.25 + OUT)] },
	{ name: 'armorRightBoot', pivot: [-2, 12, 0] },
	{ name: 'armorLeftBoot', pivot: [2, 12, 0] },
];
const geo = {
	format_version: '1.12.0',
	'minecraft:geometry': [{
		description: { identifier: 'geometry.all_might', texture_width: 64, texture_height: 64, visible_bounds_width: 4, visible_bounds_height: 4, visible_bounds_offset: [0, 1, 0] },
		bones,
	}],
};
fs.writeFileSync(ASSETS + 'geo/all_might.geo.json', JSON.stringify(geo, null, 1));
console.log('done');
