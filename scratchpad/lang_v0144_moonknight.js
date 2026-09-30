// v0.14.4 lang: Moon Knight balance pass -- three lunar states, Vengeance regen, halved falls, Marc's Resistance I,
// AoE Moonbeam, the minute-long Eye of Khonshu, the new Judgement, 5 s Moon Mark, homing Crescent Fan, longer Dash,
// the Grappling Line pulling squad-mates, Crescent Slam 18, Grapple Kick 20 with aim assist + lock-on preview.
// Idempotent. Run from the repo root: node scratchpad/lang_v0144_moonknight.js
require('./langset.js')([
	// ---------------------------------------------------------------- messages
	{ anchor: 'message.projecthero.moon_knight.protector_kill', entries: {
		'message.projecthero.moon_knight.protector_kill': 'You protect those who travel at night (+%s Vengeance)',
	} },
	{ anchor: 'message.projecthero.moon_knight.judgement_fulfilled', entries: {
		'message.projecthero.moon_knight.judgement_fulfilled': 'Judgement is done',
	} },
	// ---------------------------------------------------------------- HUD
	{ anchor: 'hud.projecthero.moon_knight.fractured', entries: {
		'hud.projecthero.moon_knight.lunar.day': 'Day',
		'hud.projecthero.moon_knight.lunar.night': 'Night',
		'hud.projecthero.moon_knight.lunar.full_moon': 'Full Moon',
		'hud.projecthero.moon_knight.kick_target': '[%s] %s  %sm',
	} },
	// ---------------------------------------------------------------- Guidebook / power info
	{ anchor: 'projecthero.guide.moon_knight.suit', entries: {
		'projecthero.guide.moon_knight.suit.body': 'Press H: for a second and a half white bandages spiral up your body and the suit materialises one pixel at a time (you can\'t be hurt while it does), then the suit of Khonshu is on, with its cape. While it is on you heal half a heart every quarter second, hit 7 harder with your fists, move 30% faster, jump clean over two blocks, walk straight up full blocks without jumping, and take 20% less damage -- and falls hurt you half as much again on top of that. Press H again and it dissolves away over another second and a half. The armour you were wearing is kept safe and handed back exactly as it was. Out of the suit, Khonshu will not let you fall: a single hit of more than 5 hearts, or being left under 4 hearts, starts the suit-up on its own (not in the 3 seconds after you take it off). The suit can\'t be taken off, dropped or stored, and none of Moon Knight\'s abilities or HUD work without it. While suited, his keys win over vanilla\'s creative hotbar save / load (C / X).',
	} },
	{ anchor: 'projecthero.guide.moon_knight.lunar', entries: {
		'projecthero.guide.moon_knight.lunar.body': 'The moon has three states. Every ability\'s damage, range and duration is multiplied by it, and every cooldown divided by it: DAY x0.7 (the weakest), NIGHT x1.0 and FULL MOON x1.5 (the strongest). The numbers written on the abilities are the night numbers. The Nether and the End have no moon, so there it is always day. Any night -- ordinary or full moon -- also makes your darts home in, lets you call the Moonbeam, makes the Truncheon heal you, carries your glides further and makes kills worth more Vengeance; only the full moon opens the Eye of Khonshu and recharges Khonshu\'s Resurrection. The HUD shows the state: the sun by day, tonight\'s moon at night, a glowing full moon under the full moon, with the multiplier beside it (hold Left Alt for its name).',
	} },
	{ anchor: 'projecthero.guide.moon_knight.vengeance', entries: {
		'projecthero.guide.moon_knight.vengeance.body': 'A 0-100 meter. Killing a hostile mob that was hunting a villager, a wandering trader, an iron golem or another player gives +6; any other hostile kill gives +3 at night and +2 by day. Out of combat -- no damage dealt or taken for 5 seconds -- it regenerates on its own, 0.5% a second. Some abilities cost Vengeance. If it runs dry while you are suited your mind Fractures: another alter takes over for 20 seconds (it can only happen once every ten minutes).',
	} },
	{ anchor: 'projecthero.guide.moon_knight.alters', entries: {
		'projecthero.guide.moon_knight.alters.body': 'Marc Spector, the fighter: Resistance I, +4 armour, +20% melee damage, harder to knock back. Steven Grant, the thinker: 15% less melee damage taken, extra loot from mobs, better prices from villagers, and every block he mines drops as if his tool had Fortune III (bare-handed too; a real Fortune tool never stacks with it -- the higher level counts). His Mr. Knight suit has no cape, so as Steven you can\'t Cape Glide or Cape Block. Jake Lockley, the shadow: faster while sneaking, mobs notice you from half as far away, +50% damage attacking from behind. Each wears his own suit -- Marc\'s white and gold armour, Steven\'s white, capeless Mr. Knight suit, Jake\'s dark suit -- and changing alter in the suit rematerialises the new one over the old, pixel by pixel.',
	} },
	{ anchor: 'projecthero.guide.moon_knight.controls', entries: {
		'projecthero.guide.moon_knight.controls.body': 'Most keys have up to three moves: tap it, hold it, or press it while sneaking. The cape needs no key: jump and hold Sneak to glide, hold right click to block. They only work in the suit. Damage, durations and cooldowns below are night values: x0.7 by day, x1.5 under a full moon (cooldowns the other way round).',
	} },
	// ---------------------------------------------------------------- the moves
	{ anchor: 'projecthero.moon_knight.move.glide', entries: {
		'projecthero.moon_knight.move.glide.desc': 'Jump, then hold Sneak in the air: you tip forward flat, the cape stretches between your arms and legs, and you glide -- fast -- (no elytra needed, no fall damage). Look down to dive even faster. Glides go further at night. Glide into a mob to kick it (12). Let go of Sneak, or land, to stop. Not as Steven -- his suit has no cape.',
	} },
	{ anchor: 'projecthero.moon_knight.move.shroud', entries: {
		'projecthero.moon_knight.move.shroud.desc': 'Hold right click with an empty hand (or the Truncheon): the cape wraps around you and you take 30% less damage for as long as you hold it, but you move slowly. Not as Steven -- his suit has no cape.',
	} },
	{ anchor: 'projecthero.moon_knight.move.dart_fan', entries: {
		'projecthero.moon_knight.move.dart_fan.desc': 'Hold: five crescent darts fly at once, each locking on to one of the five closest hostile mobs you can see (within 32 blocks) and chasing it down -- 15 each, the same as a single dart. With fewer targets the spare darts double up on them; with none, they fan out ahead of you. 3.25 s cooldown.',
	} },
	{ anchor: 'projecthero.moon_knight.move.moon_mark', entries: {
		'projecthero.moon_knight.move.moon_mark.desc': 'A dart that sticks in its target: it glows for 10 s and takes 30% more damage from you. 5 s cooldown.',
	} },
	{ anchor: 'projecthero.moon_knight.move.grapple', entries: {
		'projecthero.moon_knight.move.grapple.desc': 'Fire a line up to 100 blocks. At a block you are pulled to it; at a mob, it is reeled in to you and stunned for 1.5 s (a boss is too heavy, so you are pulled to it instead). Hook a squad-mate -- or your own pet -- and you reel them in to you too: a rescue, no harm and no stun.',
	} },
	{ anchor: 'projecthero.moon_knight.move.dive_kick', entries: {
		'projecthero.moon_knight.move.dive_kick.desc': 'Fire the line into an enemy up to 100 blocks away, get pulled in and crash into it feet first (20 + knockback). Forgiving aim: with nothing under the crosshair it locks on to the enemy nearest the crosshair (within about 9 degrees, in sight), and before you press it a ring of moonlight over that enemy\'s head -- and its name under your crosshair -- shows who you will hit. The pull chases and leads a moving target so the kick lands. Never locks on to your squad.',
	} },
	{ anchor: 'projecthero.moon_knight.move.dash', entries: {
		'projecthero.moon_knight.move.dash.desc': 'A burst of speed about 12 blocks exactly where you are aiming -- up, down or level -- with no fall damage at the end of it.',
	} },
	{ anchor: 'projecthero.moon_knight.move.slam', entries: {
		'projecthero.moon_knight.move.slam.desc': 'On the ground: a shockwave that hits everything within 4 blocks for 18 and throws it into the air. In the air: dive straight down and slam where you land -- 18 from six blocks up, more the further you fell (up to 36) -- and you take no fall damage.',
	} },
	{ anchor: 'projecthero.moon_knight.move.moonbeam', entries: {
		'projecthero.moon_knight.move.moonbeam.desc': 'Call a beam of moonlight down on the block or mob you are looking at (up to 40 blocks). It hits every hostile within 4.5 blocks of where it lands: 35 at the centre, down to 21 at the edge, double to the undead -- never you or your squad. Night only; costs 10 Vengeance (10%); 5 s cooldown.',
	} },
	{ anchor: 'projecthero.moon_knight.move.eye', entries: {
		'projecthero.moon_knight.move.eye.desc': 'The ultimate. Only under a full moon with 100 Vengeance: hold for 2 s. For one minute, every hostile mob within 30 blocks of you -- the area moves with you -- glows and is weakened (Weakness II) for as long as the Eye is open, you keep Strength II and Speed II, and every 2 seconds Khonshu brings a Moonbeam down on a random enemy near you (never your squad). Khonshu\'s skull looks down from the sky. Once per night.',
	} },
	{ anchor: 'projecthero.moon_knight.move.judgement', entries: {
		'projecthero.moon_knight.move.judgement.desc': 'Judge the mob you are looking at (up to 32 blocks) for 15 s: it burns for 10 damage every second, and every point of damage you deal it while the Judgement lasts -- the burn and your own hits -- heals you. Costs 10 Vengeance (10%); 20 s cooldown.',
	} },
]);
