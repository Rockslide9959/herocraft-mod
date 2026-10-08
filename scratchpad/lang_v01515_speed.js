// v0.15.15 speed: updates every en_us.json string that states a flight / Super Speed speed to the new numbers.
// Re-runnable (each replacement is a no-op once applied) and touches only the matched substrings, so it is safe to run
// on a merged file where other builders changed other parts of the same strings. Usage: node scratchpad/lang_v01515_speed.js
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'lang', 'en_us.json');
let s = fs.readFileSync(file, 'utf8');

let changed = 0;
function rep(from, to, required = true) {
	if (s.includes(to) && !s.includes(from)) return; // already applied
	if (!s.includes(from)) {
		if (required) throw new Error('missing: ' + from);
		return;
	}
	s = s.split(from).join(to); // all occurrences (iron_man.controls is duplicated in the file)
	changed++;
}

// Iron Man
rep('The Mark 2 flies at 15 blocks a second (30 sprinting), the Mark 3 and every later Mark at 18 (35 sprinting).',
	'The Mark 1\'s Flight Burst flies at 8 blocks a second (sprinting adds nothing); the Mark 2 flies at 15 blocks a second '
	+ '(25 sprinting), the Mark 3 and Mark 4 at 17 (32 sprinting), the Mark 5 at 17 (28 sprinting) and the Mark 6, 7 and 8 '
	+ 'at 18 (36 sprinting).');

// Kryptonian
rep('Sprint for super-speed flight -- 35 blocks a second.',
	'You cruise at 20 blocks a second; sprint for super-speed flight -- 45 blocks a second.');
rep('toggle it on and sprint flight goes from 35 to 55 blocks a second', 'toggle it on and sprint flight goes from 45 to 55 blocks a second');

// Thor
rep('"Double-tap Jump while holding Mjolnir (or Stormbreaker) to fly."',
	'"Double-tap Jump while holding Mjolnir (or Stormbreaker) to fly: 18 blocks a second, 36 sprinting."');

// Max Steel
rep('Movement keys steer, jump climbs, sneak drops, sprint boosts. Costs more the faster you go.',
	'Movement keys steer, jump climbs, sneak drops, sprint boosts: 18 blocks a second, 36 sprinting. Costs more the faster you go.');

// Green Lantern
rep('letting go of everything eases you into a steady hover. Costs a flat 1 charge/sec while flying',
	'letting go of everything eases you into a steady hover. Ring Flight cruises at 18 blocks a second, 40 sprinting. Costs a flat 1 charge/sec while flying');
rep('Sneak + Sprint to Boost for far more speed at 40 charge/sec',
	'Sneak + Sprint to Boost for far more speed (55 blocks a second) at 40 charge/sec');

// Super Speed
rep('walk at roughly 32 blocks/second and SPRINT at roughly 100 blocks/second',
	'walk at 32 blocks/second and SPRINT at 120 blocks/second');
rep('walk at roughly 20 blocks/second (easy to control in a fight) and SPRINT at roughly 40 blocks/second',
	'walk at 20 blocks/second (easy to control in a fight) and SPRINT at 40 blocks/second');

fs.writeFileSync(file, s);
JSON.parse(s.replace(/^﻿/, '')); // still valid JSON
console.log('lang_v01515_speed: ' + changed + ' replacement(s) applied');
