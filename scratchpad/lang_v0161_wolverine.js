// v0.16.1 Wolverine: claw combo + VFX, claws as iron shovel / axe, +3 strength with any weapon, suit mends 1/s after
// 30 s out of combat, the synthesised Sniff sound. Sets the lang keys AND the sounds.json entry (idempotent -- re-run
// after a merge conflict). Run from the repo root: node scratchpad/lang_v0161_wolverine.js
const fs = require('fs');
const path = require('path');

require('./langset.js')([
	{
		anchor: 'projecthero.wolverine.ability.adamantium_execution',
		entries: {
			'projecthero.wolverine.ability.berserker_plunge': 'Berserker Plunge',
			'projecthero.wolverine.ability.rising_uppercut': 'Rising Uppercut',
			'projecthero.wolverine.ability.whirlwind': 'Whirlwind',
			'projecthero.wolverine.ability.pounce': 'Pounce',
			'projecthero.wolverine.ability.bleed_flurry': 'Bleed Flurry',
			'projecthero.wolverine.ability.feral_roar': 'Feral Roar',
			'hud.projecthero.wolverine.shift_pair': '%s  /  Shift: %s',
		},
	},
	{
		anchor: 'projecthero.guide.wolverine.ability.adamantium_execution',
		entries: {
			'projecthero.guide.wolverine.ability.claw_dash': 'Launch yourself 21 blocks along your aim, leaning into the lunge with both claws thrust forward -- both claw tips leave a trail through the whole leap. You cannot dash through blocks and you are knockback-immune mid-dash. Every enemy in a 3-block-wide path takes 12 damage (once each), and the first one you hit -- mob or player -- is grabbed and dragged along in front of you until you land. Your claw moves swing left and right hands in turn. 6-second cooldown.',
			'projecthero.guide.wolverine.ability.adamantium_execution': 'Shift+Z (v0.16.1; it was plain Z): hold for 5 seconds to charge (you move slowly while charging), then release: whatever you are looking at within 10 blocks glows red for you and Wolverine launches straight at them for a 60-damage claw strike with heavy knockback. A whiff only costs 8 seconds. 30-second cooldown, its own.',
			'projecthero.guide.wolverine.ability.berserker_plunge': 'Leap high, arms raised, then slam down claws-first where you land: 16 damage to everything within 3 blocks and they are knocked into the air, with a shockwave ring. 12-second cooldown.',
			'projecthero.guide.wolverine.ability.rising_uppercut': 'A crouched, low-to-high slash: 14 damage to what is in front of you (3.5 blocks deep, 2.5 wide) and it is launched into the air, leaving a vertical trail. 4-second cooldown, separate from Claw Slash.',
			'projecthero.guide.wolverine.ability.whirlwind': 'Arms flung wide, you spin a full turn twice: each spin hits everything within 3.5 blocks for 10, all the way round, leaving a ring of claw trails. 7-second cooldown, separate from Cross Slash.',
			'projecthero.guide.wolverine.ability.pounce': 'Leap onto the enemy you are aiming at (up to 14 blocks): you land on it, pin it in place for 1.5 seconds and rake it 3 times for 6 each. Bosses are not pinned, only raked. No target, no cooldown. 10-second cooldown, separate from Claw Dash.',
			'projecthero.guide.wolverine.ability.bleed_flurry': 'Six fast slashes into a cone in front of you, 5 damage each, and everything they cut is lacerated: 1 more damage a second for 4 seconds (steel-blue flecks, no blood). No target, no cooldown. 12-second cooldown, separate from Frenzy.',
			'projecthero.guide.wolverine.ability.feral_roar': 'A roar that throws back every enemy within 6 blocks and leaves them weakened (Weakness I, 5 s), with a shockwave ring. It does not need the rage bar. 20-second cooldown.',
		},
	},
	{
		anchor: 'projecthero.guide.wolverine.claws',
		entries: {
			'projecthero.guide.wolverine.claws.body': 'Three curved blades in each hand, hidden until you want them. H deploys or retracts them: deploying, you raise both fists in front of your face, then throw your arms down to your sides as the blades snap out of the backs of your fists (claws an ability brings out just appear, with no flourish); retracting, they simply slide back in. While they are out they are your tools: they dig like an iron shovel and chop like an iron axe (the same speed and the same drops), cut like a sword (cobwebs give way at once and drop their string, plants and leaves 1.5x faster), and you mine 1.5x faster on top of that. Right-clicking a block with them does nothing a tool would -- no dirt paths, no stripped logs. With them out your bare-hand attack is 12 damage (1 + your +3 strength + the claws\' +8). You cannot hold items in either hand while the claws are out (they are stowed in your inventory). Left-click chains a three-hit combo: a rising diagonal slash with the right claws, a horizontal slash with the left, then a finisher -- both arms raised high, raked down into an X across your chest -- the same damage every hit. Every claw strike leaves a thin slash trail behind the blade tips and throws adamantium sparks (in first person too). Hold right-click to block: both sets of claws cross in front of you and anything a shield could stop does 30% less damage (falls, fire, drowning and magic still get through), but -- like a raised shield -- you can\'t attack while guarding. Deploying the claws tears through your hands for 4 damage (never enough to kill you) -- but that is not a fight: it never fills your Rage bar and never tears your suit. Every claw move deploys them for you if they are away. Other players see your claws and every move.',
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
			'projecthero.guide.wolverine.lmb': 'Claws out: combo -- rising slash, horizontal slash, X finisher',
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
