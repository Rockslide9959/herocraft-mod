// v0.17.1: inserts the Multimouse / Mullo lang keys into en_us.json before its final '}' (never re-serialises the
// file; LF endings kept), and adds multimouse to the operator commands line. Skips keys that are already there.
// Usage (repo root): node scratchpad/multimouse/lang_multimouse.js
'use strict';
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'lang', 'en_us.json');
let s = fs.readFileSync(file, 'utf8');

const A = 'projecthero.miraculous.ability.';
const K = {
	[A + 'multimouse_rope_lash']: 'Rope Lash',
	[A + 'multimouse_rope_lash.desc']: 'Press up to three times for a three-swing jump-rope whip combo, as Cat Noir\'s Baton Strike. 8 damage a swing to everything in front of you within 5 blocks; the third swing knocks back. 1.5 s cooldown after the third.',
	[A + 'multimouse_lasso_yank']: 'Lasso Yank',
	[A + 'multimouse_lasso_yank.desc']: 'Lasso the creature you aim at (up to 16 blocks) and yank it to your feet: 6 damage and a 1.5 s stun. Bosses are only slowed. 8 s cooldown.',
	[A + 'multimouse_rope_twirl']: 'Rope Twirl',
	[A + 'multimouse_rope_twirl.desc']: 'Spin the rope round you for 4 s: everything within 3.5 blocks takes 4 damage every half second, and arrows from any side bounce off. 10 s cooldown.',
	[A + 'multimouse_skipping_trip']: 'Skipping Trip',
	[A + 'multimouse_skipping_trip.desc']: 'A low sweep along the ground in a 6-block arc in front of you: 6 damage, and everything hit is tripped (knocked down, slowed hard and unable to attack for 2 s). Bosses are only slowed. 8 s cooldown.',
	[A + 'multimouse_rope_swing']: 'Rope Swing',
	[A + 'multimouse_rope_swing.desc']: 'The rope hooks the block you aim at, up to 30 blocks away, and swings you to it. No fall damage on the landing. 3 s cooldown.',
	[A + 'multimouse_scurry']: 'Scurry',
	[A + 'multimouse_scurry.desc']: 'Shrink to mouse size (a third of your height) for up to 20 s: 40% faster, you fit through 1-block gaps and mobs often miss you. Press again to grow back (only where there is room). 5 s cooldown afterwards.',
	[A + 'multimouse_mouse_swarm']: 'Mouse Swarm',
	[A + 'multimouse_mouse_swarm.desc']: 'Three tiny copies of you dart at your target for 6 s and nibble it, 2 damage a bite. 14 s cooldown.',
	[A + 'multimouse_multitude']: 'MULTITUDE',
	[A + 'multimouse_multitude.desc']: 'Split into 11 mini Multimice for 60 s (it starts the countdown): you plus 10 copies, a quarter of your size. The copies fight what you hit or look at, 3 damage a bite, 6 health each; your own moves do half damage. Shift+Z again (or the timer) recombines: the survivors run back and a shockwave knocks back everything within 6 blocks, 2 damage for each copy that made it. 2 min cooldown.',
	[A + 'multimouse_mouse_hearing']: 'Mouse Hearing',
	[A + 'multimouse_mouse_hearing.desc']: 'Toggle: every creature within 32 blocks shows through walls as rings of sound pulsing with its footsteps, only for you. Press again to stop.',
	[A + 'multimouse_gnaw']: 'Gnaw',
	[A + 'multimouse_gnaw.desc']: 'Chew straight through the blocks in front of you: a 1x2 hole up to 3 deep, through any block, stone included (look straight down or up to chew a shaft). Never bedrock, barriers, chests or anything unbreakable; not in protected areas. The blocks drop. 4 s cooldown.',
	[A + 'multimouse_rope_bind']: 'Rope Bind',
	[A + 'multimouse_rope_bind.desc']: 'Throw the rope at the creature you aim at (up to 14 blocks) and tie it up: rooted and unable to attack for 4 s. Bosses are only slowed. 12 s cooldown.',
	[A + 'multimouse_mouse_trap']: 'Mouse Trap',
	[A + 'multimouse_mouse_trap.desc']: 'Set a rope snare on the ground where you look (or at your feet), up to 3 at once. The first mob to step in is snared for 5 s and takes 8 damage. A snare frays away after 2 minutes.',
	'message.projecthero.multimouse.no_target': 'No creature in your sights.',
	'message.projecthero.multimouse.no_hook': 'Nothing for the rope to hook onto.',
	'message.projecthero.multimouse.already_small': 'You are already tiny.',
	'message.projecthero.multimouse.no_room': 'No room to grow back here.',
	'message.projecthero.multimouse.gnaw_off': 'Gnaw can\'t chew here (terrain damage or mobGriefing is off).',
	'message.projecthero.multimouse.multitude': 'Multitude!',
	'hud.projecthero.multimouse.multitude': 'Multitude  %s / %s',
	'hud.projecthero.multimouse.scurry': 'Scurry',
	'projecthero.calling.kwami.mullo': 'Mullo',
	'projecthero.calling.hero.multimouse': 'Multimouse',
	'entity.projecthero.kwami.mullo': 'Mullo',
	'entity.projecthero.multimouse_mini': 'Mini Multimouse',
	'projecthero.hero_tier.multimouse': 'Multimouse',
	'projecthero.squad.identity.multimouse': 'Multimouse',
	'projecthero.guide.multimouse': 'Multimouse',
	'item.projecthero.mouse_pendant': 'Mouse Pendant',
	'item.projecthero.cheese_wedge': 'Cheese Wedge',
	'message.projecthero.miraculous.fed.mullo': 'Mmm, cheese! Squeak! I feel ten times better. Well, eleven!',
	'message.projecthero.miraculous.tired.mullo': 'Phew... splitting up is hard work. A cheese wedge, please?',
	'message.projecthero.miraculous.intro.mullo.1': 'Hi hi! I\'m Mullo, the Kwami of Multiplication. The Mouse Pendant chose you!',
	'message.projecthero.miraculous.intro.mullo.2': 'Say "Mullo, multiply!" in chat and you\'ll become Multimouse: small, quick, and never alone.',
	'message.projecthero.miraculous.intro.mullo.3': '"Divide!" brings you back. Afterwards I\'ll be hungry: a cheese wedge, please!',
	'hud.projecthero.miraculous.hungry.mullo': 'Mullo is hungry: a cheese wedge, or 10 of any food',
	'message.projecthero.kwami_chat.mullo.countdown.1': 'The pendant\'s nearly out! Wrap it up, quick quick!',
	'message.projecthero.kwami_chat.mullo.countdown.2': 'Hurry! Even a hundred mice can\'t stop the clock!',
	'message.projecthero.kwami_chat.mullo.danger.1': 'Ears up! Something\'s coming!',
	'message.projecthero.kwami_chat.mullo.danger.2': 'I hear trouble. Stay small, stay smart!',
	'message.projecthero.kwami_chat.mullo.hurt.1': 'Ouch! Little ones should dodge, not tank!',
	'message.projecthero.kwami_chat.mullo.hurt.2': 'You\'re hurt! Scurry away and catch your breath!',
	'message.projecthero.kwami_chat.mullo.victory.1': 'We did it! Together we\'re enormous!',
	'message.projecthero.kwami_chat.mullo.victory.2': 'Squeak! The small ones win again!',
	'message.projecthero.kwami_chat.mullo.idle.1': 'Do you think cheese dreams of mice?',
	'message.projecthero.kwami_chat.mullo.idle.2': 'A cheese wedge would be lovely right about now...',
	'message.projecthero.kwami_chat.mullo.idle.3': 'Many little hands make big work light!',
	'projecthero.guide.multimouse.tier': 'Hero-Tier power (Primary): the Miraculous of the Mouse',
	'projecthero.guide.multimouse.body': 'Mullo, the Kwami of Multiplication, lives in the Mouse Pendant. Say her phrase in chat and she turns you into Multimouse, the silver-and-pink mouse hero with a jump rope that is a whip, a lasso and a grapple, with six moves on R, G, X, Z, V and C (each with a Shift version) -- and Multitude, which splits you into a swarm of tiny copies of yourself.',
	'projecthero.guide.multimouse.jewel': 'The Pendant',
	'projecthero.guide.multimouse.jewel.body': 'The Mouse Pendant is one of the jewels in the Miraculous Box, and a spawn calling can offer it too. Right-click it: Mullo pops out, says hello and Multimouse becomes your Primary power, replacing any other. The jewel is used up. From then on you wear the round silver pendant at your collar; transformed, it sits on the suit.',
	'projecthero.guide.multimouse.kwami': 'Mullo',
	'projecthero.guide.multimouse.kwami.body': 'While you are not transformed, Mullo floats over your shoulder and goes wherever you go: caves, water, flying, riding. She looks where you look and drifts round you when you stand still, her big round ears twitching and her curly tail swishing. Nothing can hurt her, and if she is ever left behind she comes straight back to you. Press N to tuck Mullo away out of sight (N again calls her back out); you can only transform while she is out. Hold Shift+N for 5 seconds to take the Miraculous off: Mullo goes back into the pendant, which lands in your inventory (not while transformed or while she is hungry).',
	'projecthero.guide.multimouse.transform': 'Transforming',
	'projecthero.guide.multimouse.transform.body': 'Type "Mullo, multiply!" -- or just "Multiply!" -- in chat to transform (capitals and punctuation don\'t matter). Mullo spins into the pendant and, with a flash of pink light, the suit spreads out from your collar: the silver hood with its round mouse ears, the pink jacket front and cuffs, the jump-rope belt and the long rope tail. Type "Divide!" (or "Mullo, divide!") to change back. H does not transform. Chat shows the phrase as the hero saying it ("<Multimouse> Multiply!"), never under your own name.',
	'projecthero.guide.multimouse.food': 'Feeding Mullo',
	'projecthero.guide.multimouse.food.body': 'Every time you change back (by the phrase, the countdown or dying), Mullo is worn out and hungry, and won\'t transform you again until she has eaten a Cheese Wedge. Craft wedges from a milk bucket and sugar (4 at a time, the bucket comes back); they are good food for you too. Right-click her with one, or eat one while she is hungry and she eats it instead. No cheese? Right-click her with 10 pieces of any other food instead. The wedge takes 10 seconds to eat, the 10 other pieces 22 seconds.',
	'projecthero.guide.multimouse.mouse': 'Light and quiet',
	'projecthero.guide.multimouse.mouse.body': 'Transformed, a fall hurts you only a quarter as much (75% less) and a fall alone never kills you. While you crouch, mobs more than 6 blocks away lose track of you. Small (Scurry or Multitude), you are hard to hit: about a third of mob hits and arrows miss you.',
	'projecthero.guide.multimouse.multitude': 'Multitude',
	'projecthero.guide.multimouse.multitude.body': 'Shift+Z: you cry "Multitude!" and split into 11 mini Multimice for 60 seconds -- you plus 10 copies, each a quarter of your size, wearing your own skin and the suit. It starts the Miraculous countdown. You stay tiny, quick and hard to hit and can run through 1-block gaps; your moves still work at half damage. The copies follow you and mob whatever you hit or look at (else the nearest threat to you): 3 damage a bite, 6 health each, and they pop in a puff of pink sparkle when they are killed. The HUD shows how many are left. Press Shift+Z again, or wait for the time to run out, to recombine: every surviving copy runs back to you and you snap back to full size in a shockwave that knocks back everything within 6 blocks and deals 2 damage for each copy that made it back. Copies hitting a boss count as your damage on its threat table.',
};

const lines = [];
for (const [k, v] of Object.entries(K)) {
	if (s.includes('"' + k + '"')) {
		continue;
	}
	lines.push('  ' + JSON.stringify(k) + ': ' + JSON.stringify(v));
}
const end = s.lastIndexOf('}');
let head = s.slice(0, end).replace(/\s*$/, '');
if (lines.length) {
	head += ',\n' + lines.join(',\n');
}
s = head + '\n}\n';
// the operator commands line: add multimouse
s = s.replace('pegasus or viperion, and /projecthero', 'pegasus, viperion or multimouse, and /projecthero');
fs.writeFileSync(file, s);
JSON.parse(s);
console.log('added', lines.length, 'keys');
