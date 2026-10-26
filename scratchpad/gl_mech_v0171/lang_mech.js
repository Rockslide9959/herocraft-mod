// v0.17.1: lang keys for the Hard-Light Mech. Inserts new keys before the final } of en_us.json (LF kept, the file is
// never re-serialised); keys already present are skipped.
'use strict';
const fs = require('fs');
const path = require('path');
const f = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'lang', 'en_us.json');
const K = {
	'entity.projecthero.hard_light_mech': 'Hard-Light Mech',
	'projecthero.green_lantern.construct.hard_light_mech': 'Hard-Light Mech',
	'projecthero.green_lantern.construct.hard_light_mech.desc': 'The ultimate construct: a 6-block mech of hard light forms round you and you pilot it from the chest cockpit. R punches, Shift+R Rocket Fist, G Ground Slam, X Thruster Dash, Z (hold) Chest Beam, V (hold) Shield Wall, C Missile Pods. N to climb out. 1500 charge, then 15 a second.',
	'message.projecthero.green_lantern.mech.formed': 'Hard-Light Mech online. N to climb out.',
	'message.projecthero.green_lantern.mech.cooldown': 'The ring needs %s s before it can build another mech.',
	'message.projecthero.green_lantern.mech.no_room': 'Not enough room to build the mech here.',
	'message.projecthero.green_lantern.mech.riding': 'Get off what you are riding first.',
	'message.projecthero.green_lantern.mech.shattered': 'The mech shatters!',
	'message.projecthero.green_lantern.mech.no_charge': 'Your ring can no longer hold the mech together.',
	'hud.projecthero.green_lantern.mech.title': 'Hard-Light Mech',
	'hud.projecthero.green_lantern.mech.assembling': 'FORMING',
	'hud.projecthero.green_lantern.mech.dissolving': 'DISSOLVING',
	'hud.projecthero.green_lantern.mech.beam': 'BEAM',
	'hud.projecthero.green_lantern.mech.shield': 'SHIELD',
	'hud.projecthero.green_lantern.mech.integrity': 'INTEGRITY %s / %s',
	'hud.projecthero.green_lantern.mech.charge': 'Ring %s%%',
	'hud.projecthero.green_lantern.mech.move.punch': 'Punch combo',
	'hud.projecthero.green_lantern.mech.move.rocket_fist': 'Rocket Fist',
	'hud.projecthero.green_lantern.mech.move.ground_slam': 'Ground Slam',
	'hud.projecthero.green_lantern.mech.move.dash': 'Thruster Dash',
	'hud.projecthero.green_lantern.mech.move.beam': 'Chest Beam (hold)',
	'hud.projecthero.green_lantern.mech.move.shield': 'Shield Wall (hold)',
	'hud.projecthero.green_lantern.mech.move.missiles': 'Missile Pods',
	'projecthero.guide.green_lantern.mech': 'The Hard-Light Mech',
	'projecthero.guide.green_lantern.mech.body': 'Green Lantern\'s ultimate construct, found in the Construct Menu under Attacks (bind it to any key, or hold left click on it). It costs 1500 Ring Charge and 15 a second to keep. Over two seconds a 6-block mech of hard light snaps together round you and you sit in the glass cockpit in its chest. WASD walks it (heavy, about 10 blocks a second, it steps up 2 blocks), Space fires its thrusters to jump. While you pilot it your ring keys drive the mech: R punches with alternating fists (hold to keep swinging), Shift+R fires the Rocket Fist (the forearm flies out 24 blocks and comes back), G is a Ground Slam whose shockwave ring reaches 9 blocks, X is a Thruster Dash, hold Z for the Chest Lantern Beam (it drains charge while it fires), hold V for a Hard-Light Shield wall that bounces projectiles back and takes most of a hit from the front, and C launches 12 homing missiles from the shoulder pods. Every hit scales with your Oath, Lantern Rank and Willpower Surge. You cannot be hurt inside: the mech takes the blows on its own Integrity (300, +10% a rank). At 0 it shatters and sets you down safely, and the ring needs a minute before it can build another. N climbs out (the mech dissolves). Third person pulls the camera back to show the whole mech.',
};
let s = fs.readFileSync(f, 'utf8');
const end = s.lastIndexOf('}');
let body = s.slice(0, end).replace(/\s+$/, '');
let added = 0;
for (const [k, v] of Object.entries(K)) {
	if (s.includes('"' + k + '"')) continue;
	body += ',\n  ' + JSON.stringify(k) + ': ' + JSON.stringify(v);
	added++;
}
fs.writeFileSync(f, body + '\n}\n');
JSON.parse(fs.readFileSync(f, 'utf8'));
console.log('added', added);
