// v0.14.16 Kryptonian rework: sets (or adds) every en_us.json key the new layout, costs, C / Shift+C, the X-Ray toggle,
// Pick Up / Set Down, the Solar-paid Regeneration III and the 5 s refill delay use.
// Re-runnable (e.g. after a merge conflict in en_us.json): existing keys are replaced in place, new ones are added just
// before the closing brace. Keeps the file's line endings. The flight guide paragraph is edited surgically (only its
// "costs" sentence) so another agent's flight-control wording in the same paragraph survives. Run from the repo root:
//   node scratchpad/lang_v01416_kryptonian.js
const fs = require('fs');
const file = 'src/main/resources/assets/projecthero/lang/en_us.json';
let s = fs.readFileSync(file, 'utf8');
const nl = s.includes('\r\n') ? '\r\n' : '\n';

const ab = (id, name, desc) => ({
	['projecthero.kryptonian.ability.' + id]: name,
	['projecthero.kryptonian.ability.' + id + '.desc']: desc,
});

const set = {
	// ---- messages
	'message.projecthero.kryptonian.acquired_hint': 'Double-tap jump in the air to fly. R G Z X C V (and Shift+) are your powers. Stay away from kryptonite.',
	'message.projecthero.kryptonian.depowered': 'Powerless: %ss',
	'message.projecthero.kryptonian.flare_spent': 'Powerless for 30 seconds',
	'message.projecthero.kryptonian.flare_needs_full': 'The Solar Flare needs a full bar: %s Solar Energy (%s)',
	'message.projecthero.kryptonian.flight_no_solar': 'Out of Solar Energy -- you cannot fly',
	'message.projecthero.kryptonian.xray_on': 'X-Ray Vision on',
	'message.projecthero.kryptonian.xray_off': 'X-Ray Vision off',
	'message.projecthero.kryptonian.grab_none': 'Nothing to pick up',
	'message.projecthero.kryptonian.grab_hint': 'Carrying -- Shift+V sets it down gently, V or attacking throws it',

	// ---- the twelve moves (v0.14.16 layout)
	...ab('punch', 'Kryptonian Punch', 'R: one punch at whatever you look at (6 blocks) for 32, launching it along the punch, with a shockwave that hits everything within 3 blocks of it for 12. Punching thin air still flattens what is right in front of you (14). 5 Solar Energy, 3 s cooldown.'),
	...ab('thunderclap', 'Thunderclap', 'Shift+R: a clap that rolls out as a shockwave, an 80-degree cone 20 blocks long: up to 20 damage, thrown back hard and stunned (Slowness VII, Weakness II) for 2 s. 5 Solar Energy, 8 s cooldown.'),
	...ab('heat_vision', 'Heat Vision', 'G, hold: twin red beams from your eyes, 32 blocks. 4 damage every quarter-second (16 a second) to whatever they touch, which bursts into flames; burns blocks when terrain damage is on. 1 Solar Energy a second while it burns, up to 10 s per burst, 3 s cooldown after you let go. The beams come out of your eyes in first and third person.'),
	...ab('ground_slam', 'Ground Pound', 'Shift+G: on the ground, pound it; in the air, dive straight down and pound where you land (a longer dive hits harder). Everything within 7 blocks takes up to 30 and is thrown up and away; a small crater where terrain damage is on. 10 Solar Energy, 8 s cooldown.'),
	...ab('freeze_breath', 'Freeze Breath', 'Z, hold: a stream of freezing breath in a 60-degree cone, 12 blocks: 5 damage every quarter-second, frozen solid and Slowness IV for 6 s. Water in its path ices over (it melts back), fires go out. 1 Solar Energy a second while you blow, up to 6 s per breath, 4 s cooldown after you let go.'),
	...ab('solar_flare', 'SOLAR FLARE', 'Shift+Z, the ultimate: needs a FULL bar (100 Solar Energy) and spends all of it. Two seconds of the sun pouring out of you, then it all goes at once -- everything within 12 blocks takes 120, is thrown away and set on fire, and the ground caves in. Then you are powerless for 30 s: no powers, no flight, no Regeneration, no damage reduction and no Solar Energy -- and for the first 6 s Slowness IV, Blindness and Weakness IV. 90 s cooldown.'),
	...ab('super_dash', 'Super Dash', 'X: a blur of speed 28 blocks along your look (on foot it stays low), ramming everything in the way for 20 and knocking it aside. 3 Solar Energy, 3 s cooldown. In flight, X is Flight Boost instead: toggle it on and sprint flight goes from 35 to 55 blocks a second, with a sonic boom as you break into it. Free; it switches off when you land.'),
	...ab('sky_launch', 'Sky Launch', 'Shift+X: rocket straight up about 40 blocks (the take-off shockwave hits everything within 4 for 8) and hang there in flight. 3 Solar Energy, 6 s cooldown.'),
	...ab('barrage', 'Super-Speed Barrage', 'C: a super-speed flurry -- eight blows in about a second into everything in a short cone in front of you (4.5 blocks), 3 each with them pinned in place, then a haymaker for 12 that throws them back. 5 Solar Energy, 6 s cooldown.'),
	...ab('meteor_strike', 'Meteor Strike', 'Shift+C: rocket 12 blocks up, then dive fists-first, wreathed in fire, at whatever you are looking at (up to 48 blocks off; already in the air, you dive straight away). The impact hits everything within 6 blocks for up to 28, throws it up and away and sets it alight, and leaves a small crater where terrain damage is on. 10 Solar Energy, 12 s cooldown.'),
	...ab('xray_vision', 'X-Ray Vision', 'V: X-Ray Vision on or off. While it is on you see every living thing within 48 blocks outlined through walls, with night vision. Only you see the outlines -- nobody else gets them. Free, no cooldown.'),
	...ab('super_grab', 'Pick Up', 'Shift+V: pick up the creature you look at (6 blocks; anything up to 4.5 blocks wide and players with PvP on -- never bosses or squad-mates) and carry it in front of you for as long as you like, even in flight. It cannot fight back or slip away, and the carry never hurts it. Shift+V again sets it down gently on the ground in front of you (no damage, no fall damage); V or attacking it hurls it instead -- it smashes into whatever it hits for 24, and 18 to everything around. Free; 4 s cooldown after a throw.'),

	// ---- guide chapter
	'projecthero.guide.kryptonian.body_stats.body': 'Always on, no form to switch into: 40 max health (two rows of hearts), 80% less damage from everything that gets through (the same as Thor), and nothing at all from falls, lightning, fire, lava, drowning, suffocation or freezing. Bare fists hit for 15 with heavy knockback (more when sprinting), you cannot be knocked back yourself, you run 40% faster, jump about 4 blocks and step up whole blocks. Whenever you are hurt you get Regeneration III, paid for with 1 Solar Energy a second; it switches off the moment you are back to full health (or the Solar Energy runs out). Direct sunlight heals you as well (2 health a second) and keeps you fed. You never run out of air.',
	'projecthero.guide.kryptonian.solar.body': 'The gold bar over your keys (0-100). It fills 4 a second in direct sunlight, 1 a second in daytime shade or rain, 0.5 a second at night and 0.25 a second underground or in the Nether and the End -- but only once 5 seconds have passed since it was last drained by anything (a move, a held beam, flying, your Regeneration or kryptonite); the bar shows a duller gold while it waits. The moves are cheap: 5 for the Punch, Thunderclap or Barrage, 10 for Ground Pound or Meteor Strike, 3 for the Dash or Sky Launch, 1 a second for Heat Vision and Freeze Breath. X-Ray Vision and Pick Up are free, flying costs 0.1 a second, and the Solar Flare takes the whole bar.',
};

let added = 0;
let replaced = 0;
function put(k, v) {
	const re = new RegExp('^(\\s*)"' + k.replace(/[.]/g, '\\.') + '": ".*?",?$', 'm');
	const m = s.match(re);
	if (m) {
		const comma = m[0].trimEnd().endsWith(',') ? ',' : '';
		const line = m[1] + JSON.stringify(k) + ': ' + JSON.stringify(v) + comma;
		s = s.replace(re, () => line);
		replaced++;
		return;
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
for (const [k, v] of Object.entries(set)) {
	put(k, v);
}

// the flight paragraph: only the "what it costs" sentence changes (another agent may rewrite the controls part)
const FLIGHT = 'projecthero.guide.kryptonian.flight.body';
const COST = 'Flight costs 0.1 Solar Energy a second, so the bar does not refill while you are in the air; with an empty bar you drop out of the sky (the fall never hurts you) and cannot take off until it refills.';
const cur = JSON.parse(s)[FLIGHT];
if (typeof cur === 'string' && !cur.includes(COST)) {
	let next = cur.replace(/\s*Flight costs nothing\.?/, '');
	next = next.replace(/\s+$/, '') + ' ' + COST;
	put(FLIGHT, next);
}

JSON.parse(s);
fs.writeFileSync(file, s);
console.log('lang ok: added', added, 'replaced', replaced);
