// v0.16.1 Wolverine: claw combo + VFX, claws as iron shovel / axe, +3 strength with any weapon, suit mends 1/s after
// 30 s out of combat, the synthesised Sniff sound. Sets the lang keys AND the sounds.json entry (idempotent -- re-run
// after a merge conflict). Run from the repo root: node scratchpad/lang_v0161_wolverine.js
const fs = require('fs');
const path = require('path');

require('./langset.js')([
	{
		anchor: 'projecthero.guide.wolverine.claws',
		entries: {
			'projecthero.guide.wolverine.claws.body': 'Three curved blades in each hand, hidden until you want them. H deploys or retracts them. While they are out they are your tools: they dig like an iron shovel and chop like an iron axe (the same speed and the same drops), cut like a sword (cobwebs give way at once and drop their string, plants and leaves 1.5x faster), and you mine 1.5x faster on top of that. Right-clicking a block with them does nothing a tool would -- no dirt paths, no stripped logs. With them out your bare-hand attack is 12 damage (1 + your +3 strength + the claws\' +8). You cannot hold items in either hand while the claws are out (they are stowed in your inventory). Left-click chains a combo: a slash with the right claws, a slash with the left, then an overhead cross with both -- the same damage every hit. Every claw strike leaves a pale slash trail behind the blades and throws adamantium sparks (in first person too). Hold right-click to block: both sets of claws cross in front of you and anything a shield could stop does 30% less damage (falls, fire, drowning and magic still get through), but -- like a raised shield -- you can\'t attack while guarding. Deploying the claws tears through your hands for 4 damage (never enough to kill you) -- but that is not a fight: it never fills your Rage bar and never tears your suit. Every claw move deploys them for you if they are away. Other players see your claws and every move.',
		},
	},
	{
		anchor: 'projecthero.guide.wolverine.suit',
		entries: {
			'projecthero.guide.wolverine.suit.body': 'A craftable yellow-and-blue suit (Cowl, Suit, Leggings, Boots) made from leather and yellow / blue dye, found in the Superheroes tab. It gets visibly torn and bloodied as your health drops (and stays torn, repairing itself over 20 seconds once you recover), with big chunks breaking away to bare your skin, and is almost torn off during the Death Surge (until your skin is 50% back). Chainmail-level protection, diamond-level durability, and it mends itself: once you have been out of combat for 30 seconds (no damage dealt or taken), every damaged piece regains 1 durability a second, worn or carried. Anyone can wear it.',
		},
	},
	{
		anchor: 'projecthero.guide.wolverine.sniff',
		entries: {
			'projecthero.guide.wolverine.sniff': 'Sniff: a few sharp sniffs of the air, and every living thing within 40 blocks is highlighted for 20 seconds. No cooldown; your own screen only.',
			'projecthero.guide.wolverine.lmb': 'Claws out: combo -- right slash, left slash, overhead cross',
		},
	},
	{
		anchor: 'projecthero.guide.wolverine.passives.body',
		entries: {
			'projecthero.guide.wolverine.passives.body': 'Healing factor: 1.5 HP every 5 ticks (6 HP/s), but every HP it heals is paid from a Healing Factor pool of 250 HP (the green bar on your HUD, with its percentage). At 0 the healing factor is OFF until the pool has some charge again. The pool only refills once you have gone 5 seconds without TAKING damage, then 5 HP per second. 35% less physical damage, 75% knockback resistance, 75% less fall damage. Strong Poison and Wither resistance. Strength: +3 melee damage whatever is in your hand -- a bare fist hits for 4, an iron sword for 9, an axe or any weapon 3 more than usual -- and 12 with the claws out (the +3 is counted once; the claws add their own +8). Jump 2 blocks high (no passive speed bonus). Any mob hunting you glows orange, for you alone (enhanced senses). Fire still hurts, but you outheal it while the pool lasts. Lethal falls: a fall that would kill you leaves you at half a heart instead. The Healing Factor pool takes the damage (at most 100 -- anything beyond is not taken), your legs turn to raw flesh and you are slowed (Slowness VI) for 20 seconds, then your skin phases back in over the flesh across another 20 seconds. It needs some pool left: with the healing factor spent, a lethal fall still kills.',
		},
	},
	{
		anchor: 'item.projecthero.wolverine_suit_chestplate.desc',
		entries: {
			'item.projecthero.wolverine_suit_chestplate.desc': 'The yellow-and-blue costume. Grants nothing, but mends 1 durability a second after 30 s out of combat.',
		},
	},
	{
		anchor: 'subtitles.projecthero.darkseid_punch',
		entries: {
			'subtitles.projecthero.wolverine_sniff': 'Wolverine sniffs the air',
		},
	},
]);

// ---- sounds.json: the Sniff (three synthesised takes, scratchpad/synth_wolverine_sniff.js)
const SOUNDS = path.join(__dirname, '../src/main/resources/assets/projecthero/sounds.json');
const text = fs.readFileSync(SOUNDS, 'utf8');
const eol = text.includes('\r\n') ? '\r\n' : '\n';
const sounds = JSON.parse(text);
sounds.wolverine_sniff = {
	subtitle: 'subtitles.projecthero.wolverine_sniff',
	sounds: [
		{ name: 'projecthero:wolverine/sniff1' },
		{ name: 'projecthero:wolverine/sniff2' },
		{ name: 'projecthero:wolverine/sniff3' },
	],
};
fs.writeFileSync(SOUNDS, JSON.stringify(sounds, null, 2).replace(/\n/g, eol) + eol);
console.log('sounds.json updated');
