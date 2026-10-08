// v0.15.15 Symbiote: restructured I-key / guide pages (short sections, one row per key with its cooldown), diamond suit +
// Resistance I, the chest-out suit-up, Call Carnage messages. Re-runnable: existing keys are rewritten in place.
require('./langset.js')([
	{ anchor: 'projecthero.guide.symbiote.body', entries: {
		'projecthero.guide.symbiote.overview': 'An alien organism that bonds with you and wraps you in a living black suit: diamond-grade armour, Resistance I, tendrils, spikes and a blade -- all fed by its own Biomass bar. It hates fire and loud sound.',
		// how to get it
		'projecthero.guide.symbiote.s.get.find': '1. Find one: it crawls out of a Symbiote Meteorite in a crater, waits in a hidden research lab\'s containment cell, or bursts out of a rare Symbiote Host mob when it dies.',
		'projecthero.guide.symbiote.s.get.bond': '2. Right-click it (or use a filled Symbiote Vial) and win the bonding minigame: stop the marker in the green zone three times running.',
		'projecthero.guide.symbiote.s.get.settle': '3. Ride out 35 seconds of sickness while it settles (no moves yet), then press H.',
		'projecthero.guide.symbiote.s.get.purge': 'Bonding removes every other power, except Spider-Man (Black Suit) and the Punisher (Agent Venom).',
		// the suit
		'projecthero.guide.symbiote.s.suit': 'The suit (H)',
		'projecthero.guide.symbiote.s.suit.body': 'H wraps the suit on or draws it back in. It spreads over you pixel by pixel from the chest outward -- arms and legs, then the head last -- in 2 seconds, and you grow to 2.7 blocks tall. Your moves work with the suit off too.',
		'projecthero.guide.symbiote.s.suit.armour': 'Diamond-grade armour: 20 armour, 2 toughness.',
		'projecthero.guide.symbiote.s.suit.resistance': 'Resistance I for as long as the suit is on.',
		'projecthero.guide.symbiote.s.suit.stats': '+15% speed, +20% jump, +20% knockback resistance, and your bare fist hits for 6.',
		'projecthero.guide.symbiote.s.suit.bonded': 'Suit off but bonded: +7% speed and +10% knockback resistance.',
		// biomass
		'projecthero.guide.symbiote.s.biomass': 'Biomass -- the Symbiote\'s life',
		'projecthero.guide.symbiote.s.biomass.body': 'A 200-point bar under your ability boxes. You still take every hit in full, and the Symbiote loses 40% of it as well.',
		'projecthero.guide.symbiote.s.biomass.regen': 'Grows back 8 a second after 3 seconds out of combat (4 with the suit on, none while the Blade or Shield is out).',
		'projecthero.guide.symbiote.s.biomass.costs': 'Costs: Blade 0.3 a second, Shield 0.5 a second, healing you about 2 a second, catching an arrow 3.',
		'projecthero.guide.symbiote.s.biomass.empty': 'At 0 the suit melts off and every move locks until it is back to 20%.',
		'projecthero.guide.symbiote.s.biomass.death': 'Dying does not refill it.',
		// moves
		'projecthero.guide.symbiote.s.moves': 'Moves',
		'projecthero.guide.symbiote.s.combos': 'Sneak combos',
		'projecthero.guide.symbiote.s.cd': '%s s cooldown',
		'projecthero.guide.symbiote.s.toggle': 'toggle',
		'projecthero.symbiote.ability.tendril_sweep': 'Tendril Sweep',
		'projecthero.guide.symbiote.s.move.tendril_strike': 'A tendril whips 30 blocks down your aim: 15 damage and a knockback. It can miss, and blocks stop it.',
		'projecthero.guide.symbiote.s.move.spike_shot': 'A living spike, fast and almost flat: 14 damage and Wither V for 5 seconds. Spikes burst out of your back and arms as it fires.',
		'projecthero.guide.symbiote.s.move.leap': 'Launches you 20 blocks the way you look; the first enemy in your path takes 15. No fall damage on the landing.',
		'projecthero.guide.symbiote.s.move.barrage': 'A second-long flurry of tendrils from both hands, 4 damage a hit, 30 blocks. With the Blade out it is a wide 8-damage slash instead.',
		'projecthero.guide.symbiote.s.move.blade': 'A curved blade grows from your hand: +5 melee, 50% faster swings. Feeds on 0.3 Biomass a second; nothing else fits in that hand.',
		'projecthero.guide.symbiote.s.move.spikes': 'Spikes bristle out of your back and arms and stay out: anything that hits you in melee takes Thorns IV damage.',
		'projecthero.guide.symbiote.s.move.tendril_sweep': 'Seven tendrils fan across everything in front of you: 8-10 damage each and Slowness for 7 seconds.',
		'projecthero.guide.symbiote.s.move.spike_fan': 'Five spikes in a 40-degree fan from both hands, each 14 damage and Wither V.',
		'projecthero.guide.symbiote.s.move.grapple': 'A 30-block tendril reels you to whatever it hits (no fall damage), or pulls a dropped item to you.',
		'projecthero.guide.symbiote.s.move.onslaught': 'Hold 3 seconds, then let go: tendrils erupt 9 blocks around you -- 20 damage, Wither III, Blindness, Slowness IV, Weakness II, and they are thrown up. Let go early to cancel for free.',
		'projecthero.guide.symbiote.s.move.shield': 'A living shield: stops 90% of hits from the front, none from the sides or back. Slows you; 0.5 Biomass a second.',
		'projecthero.guide.symbiote.s.move.tendril_grab': 'Seize a target within 15 blocks and hold it helpless. Press C again to hurl it (it is thrown for you after 3.5 seconds).',
		'projecthero.guide.symbiote.s.move.symbiote_tendril_strike': 'A short tendril lash for 8 blocks: real damage and a solid knockback.',
		'projecthero.guide.symbiote.s.move.symbiote_crush': 'Hold on a target within 10 blocks to crush it: 8 damage a second for up to 5 seconds. Let go to release it.',
		'projecthero.guide.symbiote.s.move.symbiote_slam_enhanced': 'In the air it drives you straight down for a 15-damage shockwave on landing; on the ground it is a plain slam.',
		// always on
		'projecthero.guide.symbiote.s.passive.recovery': 'Symbiote Recovery: Regeneration II while you are hurt, paid for in Biomass.',
		'projecthero.guide.symbiote.s.passive.protect': 'Protector: a heavy, dangerous or fatal hit -- or dropping under 5 hearts -- wraps the suit on you by itself.',
		'projecthero.guide.symbiote.s.passive.resurrect': 'Resurrection: a fatal hit brings you back at half health with a tendril blast that throws everything back. Once every 10 minutes (20 for Black Suit Spider-Man and Agent Venom); a sound attack stops it.',
		'projecthero.guide.symbiote.s.passive.falls': 'No fall damage while bonded.',
		'projecthero.guide.symbiote.s.passive.arrows': 'Arrows that reach you are snatched out of the air (3 Biomass each).',
		'projecthero.guide.symbiote.s.passive.cloak': 'Camouflage: crouch for 5 seconds with the suit on to vanish; stand up to reappear.',
		'projecthero.guide.symbiote.s.passive.predator': 'Predator Vision: everything alive within 20 blocks is outlined -- hostiles red, players purple, the rest blue. Only you see it; N switches it off and on.',
		'projecthero.guide.symbiote.s.passive.claws': 'Living Claws: your empty hand mines like a wooden pickaxe, axe and shovel.',
		'projecthero.guide.symbiote.s.passive.diet': 'Iron stomach: with the suit on, raw food feeds you like cooked.',
		'projecthero.guide.symbiote.s.passive.squad': 'Squadmates: no Symbiote move ever touches them, and their hits cost no Biomass.',
		'projecthero.guide.symbiote.s.passive.pet': 'Symbiote Pet: sneak + right-click your tamed wolf or cat (empty hand, suit on) to share the Symbiote with it -- 40 Biomass for a Normal host. Again to take it back.',
		'projecthero.guide.symbiote.s.passive.vial': 'Symbiote Vial: sneak + use one to cut the Symbiote out of you and bottle it.',
		// weaknesses
		'projecthero.guide.symbiote.s.weakness.sonic': 'Sound -- a Warden\'s boom, a bell, a goat horn -- tears the suit off and locks every move for 5 seconds. Sonic damage +50% while suited.',
		'projecthero.guide.symbiote.s.weakness.fire': 'Fire and lava hurt 50% more while suited. Burn for 2 seconds and the suit retracts for 10 seconds (you stay bonded).',
		// tips
		'projecthero.guide.symbiote.s.tips': 'Tips',
		'projecthero.guide.symbiote.s.tip.carnage': 'Call Carnage: hold right-click on a free Symbiote for 5 seconds. It turns crimson and dissolves -- and a Carnage meteor comes down nearby. Let go to cancel. 10-minute cooldown.',
		'projecthero.guide.symbiote.s.tip.burn': 'Flint and steel burns a free Symbiote away for good; an empty Vial bottles it.',
		'projecthero.guide.symbiote.s.tip.biomass': 'Put the Blade and Shield away when you are not using them, so the Biomass can grow back.',
		// Black Suit Spider-Man page
		'projecthero.guide.symbiote_spider_man.suit': 'The black suit (H)',
		'projecthero.guide.symbiote_spider_man.suit.body': 'H wraps the black suit over your Spider-Man gear or draws it back in -- it spreads from the chest outward, the head last. You keep your own size.',
	} },
	// v0.15.15: the old one-liners rewritten to match the new suit (still read by the pet / book pages)
	{ anchor: null, entries: {
		'projecthero.guide.symbiote.passive.resist': 'Living Armour -- the suit is diamond-grade armour and gives you Resistance I while it is on.',
		'projecthero.guide.symbiote.passive.growth': 'Suit-up -- the suit spreads over you in 2 seconds, pixel by pixel from the chest outward with the head last, and a normal host grows to 2.7 blocks tall (an Iron Golem) as it does. Retracting it runs backwards and shrinks them back. Symbiote Spider-Man keeps his own size.',
		'projecthero.guide.symbiote.controls.body': 'A Normal Host\'s moves (Black Suit Spider-Man keeps his web abilities instead, and Agent Venom the Punisher\'s kit -- see below and the Punisher chapter).',
	} },
	// Call Carnage messages
	{ anchor: 'message.projecthero.symbiote.ability_cooldown', entries: {
		'message.projecthero.symbiote.carnage_call.progress': 'Calling Carnage %s',
		'message.projecthero.symbiote.carnage_call.cancelled': 'The call fades -- the Symbiote lets go.',
		'message.projecthero.symbiote.carnage_call.cooldown': 'The Symbiotes are still reeling from your last call -- %s',
		'message.projecthero.symbiote.carnage_call.peaceful': 'Nothing answers on Peaceful.',
		'message.projecthero.symbiote.carnage_call.already': 'Carnage is already here.',
		'message.projecthero.symbiote.carnage_call.no_ground': 'Nothing crimson can land here -- find open ground.',
		'message.projecthero.symbiote.carnage_call.called': 'The Symbiote screams into the sky and burns crimson... something answers. Carnage is coming.',
	} },
]);
