// v0.14.8 Kryptonian: set (or add) every en_us.json key the power, the meteor and the guide chapter use.
// Re-runnable (e.g. after a merge conflict in en_us.json): existing keys are replaced in place, new ones are added
// just before the closing brace. Keeps the file's line endings. Run from the repo root:
//   node scratchpad/lang_v0148_kryptonian.js
const fs = require('fs');
const file = 'src/main/resources/assets/projecthero/lang/en_us.json';
let s = fs.readFileSync(file, 'utf8');
const nl = s.includes('\r\n') ? '\r\n' : '\n';

const ab = (id, name, desc) => ({
	['projecthero.kryptonian.ability.' + id]: name,
	['projecthero.kryptonian.ability.' + id + '.desc']: desc,
});

const set = {
	// ---- blocks, items, entity
	'block.projecthero.kryptonite_ore': 'Kryptonite Ore',
	'block.projecthero.kryptonite_block': 'Block of Kryptonite',
	'block.projecthero.meteor_core': 'Meteor Core',
	'item.projecthero.kryptonite_shard': 'Kryptonite Shard',
	'item.projecthero.kryptonite_shard.tooltip': 'Weakens any Kryptonian nearby',
	'item.projecthero.kryptonian_crystal': 'Kryptonian Crystal',
	'item.projecthero.kryptonian_crystal.tooltip': 'Hold right-click in sunlight',
	'item.projecthero.kryptonian_crystal.tooltip2': 'Makes you a Kryptonian',
	'entity.projecthero.kryptonite_meteor': 'Kryptonite Meteor',
	'projecthero.hero_tier.kryptonian': 'Kryptonian',
	'projecthero.squad.identity.kryptonian': 'Kryptonian',
	'hud.projecthero.kryptonian.title': 'Kryptonian',

	// ---- messages
	'message.projecthero.kryptonian.title': 'KRYPTONIAN',
	'message.projecthero.kryptonian.subtitle': 'The yellow sun is yours',
	'message.projecthero.kryptonian.acquired': 'The crystal pours stored sunlight into you. You are a Kryptonian.',
	'message.projecthero.kryptonian.acquired_hint': 'Double-tap jump in the air to fly. R G Z X V (and Shift+) are your powers. Stay away from kryptonite.',
	'message.projecthero.kryptonian.weakened': 'Kryptonite! Your powers are gone',
	'message.projecthero.kryptonian.depowered': 'Burnt out: %ss',
	'message.projecthero.kryptonian.repowered': 'The sun fills you again',
	'message.projecthero.kryptonian.kryptonite': 'Kryptonite nearby -- you feel weak',
	'message.projecthero.kryptonian.kryptonite_clear': 'Your strength returns',
	'message.projecthero.kryptonian.fall': 'You drop out of the sky!',
	'message.projecthero.kryptonian.cooldown': '%s: %ss',
	'message.projecthero.kryptonian.low_solar': 'Needs %s Solar Energy (%s)',
	'message.projecthero.kryptonian.flare_charging': 'SOLAR FLARE',
	'message.projecthero.kryptonian.flare_spent': 'Burnt out for 30 seconds',
	'message.projecthero.kryptonian.grab_none': 'Nothing to grab',
	'message.projecthero.kryptonian_crystal.already': 'You are already a Kryptonian',
	'message.projecthero.kryptonian_crystal.needs_sun': 'The crystal needs direct sunlight',
	'message.projecthero.meteor.streaks': 'A green-glowing meteor streaks across the sky...',
	'message.projecthero.meteor.direction': 'It came down to the %s, about %s blocks away.',
	'message.projecthero.meteor.dir.n': 'north',
	'message.projecthero.meteor.dir.ne': 'north-east',
	'message.projecthero.meteor.dir.e': 'east',
	'message.projecthero.meteor.dir.se': 'south-east',
	'message.projecthero.meteor.dir.s': 'south',
	'message.projecthero.meteor.dir.sw': 'south-west',
	'message.projecthero.meteor.dir.w': 'west',
	'message.projecthero.meteor.dir.nw': 'north-west',

	// ---- the ten moves
	...ab('punch', 'Kryptonian Punch', 'R: one punch at whatever you look at (6 blocks) for 32, launching it along the punch, with a shockwave that hits everything within 3 blocks of it for 12. Punching thin air still flattens what is right in front of you (14). 8 Solar Energy, 4 s cooldown.'),
	...ab('heat_vision', 'Heat Vision', 'Shift+R, hold: twin red beams from your eyes, 32 blocks. 4 damage every quarter-second (16 a second) to whatever they touch, which bursts into flames; burns blocks when terrain damage is on. Costs 3 Solar Energy a second, lasts up to 6 s per burst, 5 s cooldown after you let go. The beams come out of your eyes in first and third person.'),
	...ab('freeze_breath', 'Freeze Breath', 'G: a 1.5 s blast of freezing breath in a 60-degree cone, 12 blocks: 5 damage every quarter-second, frozen solid and Slowness IV for 6 s. Water in its path ices over (it melts back), fires go out. 12 Solar Energy, 8 s cooldown.'),
	...ab('thunderclap', 'Thunderclap', 'Shift+G: a clap that rolls out as a shockwave, an 80-degree cone 20 blocks long: up to 20 damage, thrown back hard and stunned (Slowness VII, Weakness II) for 2 s. 15 Solar Energy, 10 s cooldown.'),
	...ab('ground_slam', 'Ground Slam', 'Z: on the ground, slam it; in the air, dive straight down and slam where you land (a longer dive hits harder). Everything within 7 blocks takes up to 30 and is thrown up and away; a small crater where terrain damage is on. 15 Solar Energy, 8 s cooldown.'),
	...ab('solar_flare', 'SOLAR FLARE', 'Shift+Z, the ultimate: needs 50+ Solar Energy. Two seconds of the sun pouring out of you, then all of it goes at once -- everything within 12 blocks takes 60 plus 0.6 per point of Solar Energy (up to 120), is thrown away and set on fire, and the ground caves in. Then you are burnt out for 30 s: no flight, no powers, no Solar Energy. 90 s cooldown.'),
	...ab('super_dash', 'Super Dash', 'X: a blur of speed 16 blocks along your look (on foot it stays low), ramming everything in the way for 20 and knocking it aside. Works in flight too. 6 Solar Energy, 3 s cooldown.'),
	...ab('sky_launch', 'Sky Launch', 'Shift+X: rocket straight up about 40 blocks (the take-off shockwave hits everything within 4 for 8) and hang there in flight. 5 Solar Energy, 8 s cooldown.'),
	...ab('xray_vision', 'X-Ray Vision', 'V: for 10 s you see every living thing within 48 blocks outlined through walls (only you see it), with night vision. 5 Solar Energy, 20 s cooldown.'),
	...ab('super_grab', 'Super Grab', 'Shift+V: pick up the creature you look at (6 blocks; not bosses or anything huge) and hold it in front of you; V or Shift+V again hurls it -- it smashes into whatever it hits for 24 and 18 to everything around. Held too long (10 s) and you throw it anyway. Squad-mates are never grabbed. 5 Solar Energy, 10 s cooldown from the throw.'),

	// ---- guide chapter
	'projecthero.guide.kryptonian': 'Kryptonian',
	'projecthero.guide.kryptonian.tier': 'Hero Tier -- Primary power',
	'projecthero.guide.kryptonian.body': 'A Kryptonian under a yellow sun: nearly unbreakable, strong enough to launch a Warden with one punch, fast, fireproof, and able to fly. The sun is the fuel -- and kryptonite is the one thing that can bring you down.',
	'projecthero.guide.kryptonian.origin': 'Becoming a Kryptonian',
	'projecthero.guide.kryptonian.origin.body': 'Now and then a green meteor falls at night (see The Kryptonite Meteor). At the bottom of its crater sits the glowing Meteor Core, ringed with kryptonite ore. Mine the core with an iron pickaxe or better to get the Kryptonian Crystal -- there is only one per meteor. Take it out under the open daytime sky and hold right-click: after two seconds it pours its stored sunlight into you. Like every Hero-Tier power it replaces whatever power you had.',
	'projecthero.guide.kryptonian.body_stats': 'The body',
	'projecthero.guide.kryptonian.body_stats.body': 'Always on, no form to switch into: 60 max health, 75% less damage from everything that gets through, and nothing at all from falls, fire, lava, drowning, suffocation or freezing. Bare fists hit for 15 with heavy knockback (more when sprinting), you cannot be knocked back yourself, you run 40% faster, jump about 4 blocks and step up whole blocks. The sun heals you: 2 health a second in direct sunlight, 0.5 a second otherwise, and it keeps you fed. You never run out of air.',
	'projecthero.guide.kryptonian.solar': 'Solar Energy',
	'projecthero.guide.kryptonian.solar.body': 'The gold bar over your keys (0-100). It fills 4 a second in direct sunlight, 1 a second in daytime shade or rain, 0.5 a second at night and 0.25 a second underground or in the Nether and the End. Every move costs some (Heat Vision costs 3 a second while it burns); your body\'s passives never need it. The Solar Flare dumps all of it.',
	'projecthero.guide.kryptonian.flight': 'Flight',
	'projecthero.guide.kryptonian.flight.body': 'Double-tap jump in the air to take off; double-tap again, or touch the ground, to land. You fly where you look: hold W to fly (look up to climb, down to dive), S to brake to a stop, A and D to slide sideways, Space and Sneak to rise and sink. Let go of everything and you hover. Sprint for super-speed flight -- 40 blocks a second, with a sonic boom as you break into it. Flight costs nothing.',
	'projecthero.guide.kryptonian.kryptonite': 'Kryptonite',
	'projecthero.guide.kryptonian.kryptonite.body': 'Kryptonite ore or a block of kryptonite within 5 blocks, a shard on the ground or in anyone\'s hand within 6 blocks, or a shard anywhere in your own inventory, and you are weakened: every passive switches off (so no damage reduction and no bonus health), you fall out of the sky, no move works, you get Weakness II and Slowness II, you lose 1 health and 5 Solar Energy a second, and your bar turns green. It passes 2 seconds after you get clear. Anyone can mine the ore (iron pickaxe) for shards -- and use them against you.',
	'projecthero.guide.kryptonian.controls': 'Controls',
	'projecthero.guide.kryptonian.meteor': 'The Kryptonite Meteor',
	'projecthero.guide.kryptonian.meteor.body': 'Every night in the Overworld there is a 1 in 5 chance a meteor falls (it is certain after six nights without one), at some moment of the night, 80 to 200 blocks from a random player. Everyone sees the message; that player is told which way it came down and roughly how far. It streaks across the sky trailing fire and green light and hits with a boom, leaving a scorched crater about 8 blocks across (chests and other block entities are never touched), about ten kryptonite ore around the centre and one Meteor Core in the middle.',
	'projecthero.guide.kryptonian.commands': 'Commands (operators)',
	'projecthero.guide.kryptonian.commands.body': '/projecthero power grant kryptonian [player] -- give the power. /projecthero meteor -- a meteor falls near you like the nightly event; /projecthero meteor here -- one lands about 15 blocks in front of you. /projecthero kryptonian solar <0-100> -- set your Solar Energy.',
};

let added = 0;
let replaced = 0;
for (const [k, v] of Object.entries(set)) {
	const re = new RegExp('^(\\s*)"' + k.replace(/[.]/g, '\\.') + '": ".*?",?$', 'm');
	const m = s.match(re);
	if (m) {
		const comma = m[0].trimEnd().endsWith(',') ? ',' : '';
		s = s.replace(re, m[1] + JSON.stringify(k) + ': ' + JSON.stringify(v) + comma);
		replaced++;
		continue;
	}
	// new key: before the closing brace (the previous last line gains a comma)
	const end = s.lastIndexOf('}');
	let head = s.substring(0, end).replace(/\s+$/, '');
	if (!head.endsWith(',') && !head.endsWith('{')) {
		head += ',';
	}
	s = head + nl + '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + nl + '}' + nl;
	added++;
}
JSON.parse(s);
fs.writeFileSync(file, s);
console.log('lang ok: added', added, 'replaced', replaced);
