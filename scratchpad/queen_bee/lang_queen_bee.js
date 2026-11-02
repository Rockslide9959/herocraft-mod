// v0.17.2: inserts the Queen Bee / Pollen lang keys into en_us.json before its final '}' (never re-serialises the file;
// LF endings kept), and adds queen_bee to the operator commands line. Skips keys that are already there.
// Usage (repo root): node scratchpad/queen_bee/lang_queen_bee.js
'use strict';
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'lang', 'en_us.json');
let s = fs.readFileSync(file, 'utf8');

const A = 'projecthero.miraculous.ability.';
const K = {
	'entity.projecthero.kwami.pollen': 'Pollen',
	'projecthero.hero_tier.queen_bee': 'Queen Bee',
	'projecthero.squad.identity.queen_bee': 'Queen Bee',
	'projecthero.guide.queen_bee': 'Queen Bee',
	'item.projecthero.bee_comb': 'Bee Comb',
	'projecthero.calling.kwami.pollen': 'Pollen',
	'projecthero.calling.hero.queen_bee': 'Queen Bee',
	'message.projecthero.miraculous.fed.pollen': 'Mmm, sweet as pollen! Thank you, my Queen. I\'m buzzing again!',
	'message.projecthero.miraculous.tired.pollen': 'Oh, my Queen... that wore my wings out. A little honeycomb, please?',
	'message.projecthero.miraculous.intro.pollen.1': 'Greetings, my Queen! I am Pollen, the Kwami of Subjection. The Bee Comb chose you.',
	'message.projecthero.miraculous.intro.pollen.2': 'Say "Pollen, buzz on!" in chat and you will become Queen Bee, with a spinning top and a sting nobody forgets.',
	'message.projecthero.miraculous.intro.pollen.3': '"Buzz off!" changes you back. Afterwards I\'ll be hungry: honeycomb is my favourite!',
	'hud.projecthero.miraculous.hungry.pollen': 'Pollen is hungry: honeycomb, or 10 of any food',
	'message.projecthero.kwami_chat.pollen.countdown.1': 'My Queen, the comb is almost out of time!',
	'message.projecthero.kwami_chat.pollen.countdown.2': 'Hurry! Even a queen must bow to the clock!',
	'message.projecthero.kwami_chat.pollen.danger.1': 'Danger, my Queen! Something is coming!',
	'message.projecthero.kwami_chat.pollen.danger.2': 'Bzz! Trouble nearby. Top at the ready!',
	'message.projecthero.kwami_chat.pollen.hurt.1': 'My Queen, you are hurt! Please be careful!',
	'message.projecthero.kwami_chat.pollen.hurt.2': 'Ouch! A true queen knows when to fly away!',
	'message.projecthero.kwami_chat.pollen.victory.1': 'Magnificent, my Queen! The whole hive is proud!',
	'message.projecthero.kwami_chat.pollen.victory.2': 'Bzz bzz! Long live the Queen!',
	'message.projecthero.kwami_chat.pollen.idle.1': 'Did you know a hive works best when everyone works together?',
	'message.projecthero.kwami_chat.pollen.idle.2': 'A little honeycomb would be lovely right about now...',
	'message.projecthero.kwami_chat.pollen.idle.3': 'You are kinder than you let people see, my Queen.',
	'message.projecthero.queen_bee.no_target': 'Nothing to aim at',
	'message.projecthero.queen_bee.no_hook': 'Nothing in reach to hook the top on',
	'message.projecthero.queen_bee.venom': 'Venom! Your next hit paralyses.',
	'message.projecthero.queen_bee.venom_faded': 'Venom faded',

	'projecthero.guide.queen_bee.tier': 'Hero-Tier power (Primary): the Miraculous of the Bee',
	'projecthero.guide.queen_bee.body': 'Pollen, the Kwami of Subjection, lives in the Bee Comb. Say her phrase in chat and she turns you into Queen Bee, the gold-and-black bee heroine with a spinning top on a string that is a club, a shield, a grapple and a rotor, with six moves on R, G, X, Z, V and C (and six more with Shift). The suit is drawn over your own skin: your face and hair show under the mask, on slim and wide skins alike.',
	'projecthero.guide.queen_bee.jewel': 'The Comb',
	'projecthero.guide.queen_bee.jewel.body': 'The Bee Comb is one of the jewels in the Miraculous Box, and a spawn calling can offer it too. Right-click it: Pollen pops out, says hello and Queen Bee becomes your Primary power, replacing any other. The comb then sits in your hair at the back of your head (brighter gold while you are transformed). Another holder\'s comb is refused while you already hold Pollen.',
	'projecthero.guide.queen_bee.kwami': 'Pollen',
	'projecthero.guide.queen_bee.kwami.body': 'While you are not transformed, Pollen floats over your shoulder and goes wherever you go: caves, water, flying, riding. She looks where you look and drifts round you when you stand still, her two pairs of little wings buzzing and her antennae bobbing. Nothing can hurt her.',
	'projecthero.guide.queen_bee.transform': 'Transforming',
	'projecthero.guide.queen_bee.transform.body': 'Type "Pollen, buzz on!" (or just "Buzz on!") in chat: Pollen zips into the comb, gold ribbons spiral round you and the suit runs down from the comb over your body, texel by texel, its edge a band of honey light. "Buzz off!" (or "Pollen, buzz off!") changes you back.',
	'projecthero.guide.queen_bee.food': 'Feeding Pollen',
	'projecthero.guide.queen_bee.food.body': 'Every time you change back (by the phrase, the countdown or dying), Pollen is worn out and hungry, and won\'t transform you again until she has eaten honeycomb (shear it from a bee nest or hive). Right-click her with it, or use it while she is hungry. No honeycomb? Right-click her with 10 pieces of any food instead.',
	'projecthero.guide.queen_bee.glide': 'The buzzing glide',
	'projecthero.guide.queen_bee.glide.body': 'Transformed, hold jump in the air and you drift down slowly, like a bee, instead of dropping -- and a slow glide never builds up fall damage. It doesn\'t work while you are flying, on an elytra or in water.',
	'projecthero.guide.queen_bee.venom': 'Venom',
	'projecthero.guide.queen_bee.venom.body': 'Shift+Z: you cry "Venom!" and the top glows pale gold for 10 seconds. It starts the Miraculous countdown. The next creature you hit -- with a move or a plain punch -- takes 40 damage and is paralysed for 10 seconds: frozen where it stands, unable to move, attack or target anyone, with slow rings of honey light turning round it. Press Shift+Z again while it glows to touch whatever is right in front of you. A boss takes at most 8% of its health and is only slowed for 3 seconds. Unused, the glow fades after 10 seconds (the countdown keeps running).',

	[A + 'queen_bee_top_strike']: 'Top Strike',
	[A + 'queen_bee_top_strike.desc']: 'Press up to three times for a three-swing combo with the spinning top on its string: 7, 7 and an overhead 9 to everything in front of you within 5 blocks; the third knocks back. 1.5 s cooldown after the third.',
	[A + 'queen_bee_top_throw']: 'Top Throw',
	[A + 'queen_bee_top_throw.desc']: 'Hurl the top at what you aim at (up to 20 blocks): 14 damage, then it ricochets on to the next creature within 8 blocks, up to 3 in all, and flies back to your hand on its string. 6 s cooldown.',
	[A + 'queen_bee_spinning_shield']: 'Spinning Shield',
	[A + 'queen_bee_spinning_shield.desc']: 'Spin the top face-out before you for 4 s: arrows and other projectiles from any side are turned back at whoever shot them, and everything within 3 blocks takes 4 damage every half second. 10 s cooldown.',
	[A + 'queen_bee_top_tangle']: 'Top Tangle',
	[A + 'queen_bee_top_tangle.desc']: 'The top\'s string whips round the creature you aim at (up to 14 blocks): 6 damage and it is wrapped up, rooted and unable to attack, for 3 s. Bosses are only slowed. 10 s cooldown.',
	[A + 'queen_bee_top_swing']: 'Top Swing',
	[A + 'queen_bee_top_swing.desc']: 'Hook the top on the block you aim at (up to 30 blocks) and swing / pull yourself there, with a hop up over the edge at the end. No fall damage on the landing. 3 s cooldown.',
	[A + 'queen_bee_top_copter']: 'Top Copter',
	[A + 'queen_bee_top_copter.desc']: 'Spin the top overhead like a rotor and fly for up to 15 s (creative-style flight, a little quicker). Press again, touch down or run out of time to land, safely. 8 s cooldown after landing.',
	[A + 'queen_bee_stinger_dive']: 'Stinger Dive',
	[A + 'queen_bee_stinger_dive.desc']: 'Leap high, then dive point-first: everything within 4 blocks of where you land takes 17 damage and is knocked up. No fall damage. 9 s cooldown.',
	[A + 'queen_bee_venom']: 'VENOM',
	[A + 'queen_bee_venom.desc']: 'Your special. The top glows for 10 s: the next creature you hit (a move or a punch) takes 40 damage and is paralysed for 10 s. Press again while it glows to touch what is right in front of you. Bosses: at most 8% of their health, slowed 3 s. Starts the 3-minute countdown. 2 min cooldown.',
	[A + 'queen_bee_hive_sense']: 'Hive Sense',
	[A + 'queen_bee_hive_sense.desc']: 'Toggle: every hostile mob within 32 blocks glows gold through walls (only you see it). Off has a 1 s cooldown.',
	[A + 'queen_bee_queens_command']: 'Queen\'s Command',
	[A + 'queen_bee_queens_command.desc']: '"Bow before your queen!": hostile mobs within 10 blocks drop their target and can\'t pick a new one for 5 s (bosses ignore it), and your allies there -- your tamed pets and players you can\'t hurt -- get Strength I for 10 s. 20 s cooldown.',
	[A + 'queen_bee_sting']: 'Sting',
	[A + 'queen_bee_sting.desc']: 'A quick lunge, the top\'s point first: the first creature you reach takes 13 damage and Poison for 3 s. No fall damage after it. 4 s cooldown.',
	[A + 'queen_bee_stinger_volley']: 'Stinger Volley',
	[A + 'queen_bee_stinger_volley.desc']: 'The top hovers over your shoulder and fires 6 stingers, one every 0.2 s, at what you aim at (up to 20 blocks): 6 damage and Poison for 2 s each. 12 s cooldown.',
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
// the operator commands line: add queen_bee
s = s.replace('viperion or multimouse, and /projecthero', 'viperion, multimouse or queen_bee, and /projecthero');
fs.writeFileSync(file, s);
JSON.parse(s);
console.log('added', lines.length, 'keys');
