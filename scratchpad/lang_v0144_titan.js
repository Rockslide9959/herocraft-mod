// Lang updates for v0.14.4 (Titan boss: animations, Leaping Slam + Grave Roar, threat-based aggro).
// Run from the repo root: node scratchpad/lang_v0144_titan.js
const fs = require('fs');
const f = 'src/main/resources/assets/projecthero/lang/en_us.json';
const raw = fs.readFileSync(f, 'utf8');
const crlf = raw.includes('\r\n');
const j = JSON.parse(raw);
function set(k, v) { j[k] = v; }

set('projecthero.guide.titan.fight.body',
	'Once it transforms there is no mistaking it: a boss bar, terrain-shaking footsteps and a fully animated set of '
	+ 'telegraphed attacks, each on its own cooldown so it reads one move at a time. Every move winds up for two '
	+ 'seconds and you can see which one is coming from its body. It notices from very far away and will run down a '
	+ 'sprinting player, so come prepared before you kill the wanderer.');

set('projecthero.guide.titan.moves', 'Its Moves');
set('projecthero.guide.titan.moves.body',
	'Punch: cocks its right fist back, then a turning haymaker. '
	+ 'Backhand Sweep: winds one arm far out to the side, then sweeps it low across its front, hitting everyone in the arc. '
	+ 'Stomp: lifts a knee high before bringing the foot down; get out from under it. '
	+ 'Ground Slam: both fists raised overhead, then hammered into the ground. '
	+ 'Shockwave: arms spread wide and raised, then a crouching blast that reaches 14 blocks. '
	+ 'Grab: draws back an open hand, then snatches; it lifts you to its shoulder and hurls you. '
	+ 'Boulder: scoops a rock out of the ground and heaves it overhead before throwing. '
	+ 'Charge: lowers its head and paws the ground, then sprints. '
	+ 'Leaping Slam (new): a deep crouch and a red ring on your spot; it jumps onto you, crushes whoever is under it, and a '
	+ 'shockwave ring rolls outward from the landing. Jump as the ring reaches you to avoid it. '
	+ 'Grave Roar (new): rears back drawing in souls, then roars. Everyone within 24 blocks is hurt, slowed and dragged '
	+ 'toward it, and Husks claw their way up next to the group. '
	+ 'It also swats anyone standing at its feet with a quick raised-arm chop.');

set('projecthero.guide.titan.threat', 'Who It Hunts');
set('projecthero.guide.titan.threat.body',
	'The Titan does not lock onto one player any more. Every hit you land builds threat, standing close to it builds a '
	+ 'little more, and threat fades over time. Every couple of seconds it turns on whoever is the biggest threat '
	+ '(sometimes someone close to that, to keep you guessing). A low growl and angry sparks over a player\'s head mark '
	+ 'the switch. Its area attacks hit everyone in range, whoever it is chasing. Players in creative or spectator are ignored.');

let out = JSON.stringify(j, null, 2) + '\n';
if (crlf) {
	out = out.replace(/\n/g, '\r\n');
}
fs.writeFileSync(f, out);
console.log('lang updated (v0.14.4 Titan)');
