// v0.14.17 (main session): Kryptonian always-on solar refill, Flash Suit sprint-only bonus, Speed Carry no cooldown,
// water running control, Sorter Bot facing/holding. Idempotent: rewrites the keys in place.
const fs = require('fs');
const path = require('path');
const FILE = path.join(__dirname, '../src/main/resources/assets/projecthero/lang/en_us.json');
const lang = JSON.parse(fs.readFileSync(FILE, 'utf8'));
function swap(key, from, to) {
	const v = lang[key];
	if (v === undefined) throw new Error('missing key ' + key);
	if (v.includes(to)) return v; // already applied
	if (!v.includes(from)) throw new Error(key + ' does not contain: ' + from);
	return v.replace(from, to);
}
const entries = {
	'projecthero.guide.kryptonian.solar.body': swap('projecthero.guide.kryptonian.solar.body',
		' -- but only once 5 seconds have passed since it was last drained by anything (a move, a held beam, flying, your Regeneration or kryptonite); the bar shows a duller gold while it waits.',
		', all the time -- even straight after a move (v0.14.17: no more refill delay). Flying and your Regeneration just slow the refill down while they run; only kryptonite stops it.'),
	'projecthero.guide.flash_suit.body': swap('projecthero.guide.flash_suit.body',
		'With all four pieces on, every speed you have is 50% faster, Speed Mode and Overdrive included.',
		'With all four pieces on, your Speed Mode and Overdrive SPRINT is 50% faster. It never speeds up your walking (v0.14.17), so moving around in it stays easy to control.'),
	'item.projecthero.flash_suit.tooltip2': 'Full suit: +50% mode sprint, no Overdrive wear. H: into the ring',
	'projecthero.power.power_04_super_speed.ability.speed_carry.desc': swap('projecthero.power.power_04_super_speed.ability.speed_carry.desc',
		' 2.5s cooldown.', ' No cooldown.'),
	'projecthero.power.power_04_super_speed.ability.speed_mode.desc': swap('projecthero.power.power_04_super_speed.ability.speed_mode.desc',
		'+50% attack speed, a 3-block step assist, run on water,',
		'+50% attack speed, a 3-block step assist, run on water (you steer, speed up and stop on water just like on land; keep moving or hold a direction to stay up, let go to sink),'),
	'projecthero.guide.stark_sorter.use.body': swap('projecthero.guide.stark_sorter.use.body',
		'flies to each, opens the lid and files up to 3 stacks per trip.',
		'flies to each -- always facing the way it flies, carrying each load in its hands -- opens the lid and files up to 3 stacks per trip.'),
};
require('./langset.js')([{ entries }]);
console.log('lang v0.14.17 main ok');
