// v0.13.21 Symbiote lang: Biomass lock on the suit, blade-hand rule, faster Biomass regen, squad immunity,
// 30-block Grapple, free falls, Black Suit Spider-Man / Agent Venom tankiness trim + their new HUD panel.
// Re-runnable: existing keys are rewritten in place, new ones inserted after their anchor.
require('./langset.js')([
	{ anchor: 'message.projecthero.symbiote.recovered', entries: {
		'message.projecthero.symbiote.suit_collapsed': 'The Symbiote is spent -- the suit melts away',
		'message.projecthero.symbiote.suit_locked': 'No Biomass left to form the suit -- it needs %s%% to reform',
		'message.projecthero.symbiote.blade_no_items': 'The blade fills your hand -- sheathe it (V) to hold anything',
	} },
	{ anchor: 'hud.projecthero.symbiote.biomass', entries: {
		'hud.projecthero.symbiote.hp_broken': 'Biomass spent - suit locked',
		'hud.projecthero.symbiote.revive': 'Revive',
		'hud.projecthero.symbiote.revive_ready': 'Ready',
	} },
	{ anchor: 'hud.projecthero.symbiote.shield_guard', entries: {
		'hud.projecthero.symbiote.black_suit': 'Black Suit',
	} },
	{ anchor: 'hud.projecthero.agent_venom.title', entries: {
		'hud.projecthero.agent_venom.unleashed': 'Unleashed',
	} },
	{ anchor: null, entries: {
		'message.projecthero.symbiote.talk.falling_low_biomass': 'We are weak. Catch something -- grapple.',
		'message.projecthero.symbiote.talk.big_fall_grapple': 'Long way down. Grapple -- we have the reach.',
		'projecthero.symbiote.ability.grapple.desc': 'Sneak + X: fire a tendril up to 30 blocks and reel yourself to it -- no fall damage on the landing. It needs something to hold onto: aimed at open air the tendril reaches out the full distance and finds nothing. Aim at a dropped item and it is pulled to you instead.',
		'projecthero.symbiote.ability.blade.desc': 'V: a curved living blade grows out of your hand -- +5 melee, swings 50% faster, bites past armour. No time limit: it feeds on 0.3 Biomass a second while it is out, and dissolves when the Biomass runs out. Nothing can be held in that hand while it is out: scroll onto an item and your hand snaps back to the blade, and anything that lands in it is tucked into your pack. Sneak+V toggles Symbiote Shield.',
		'projecthero.guide.symbiote.passive.biomass': 'Biomass -- the Symbiote\'s own 200-point life bar, shown under the ability boxes as a percentage and a thin bar. When a hit lands on you (in full), the Symbiote also loses 40% of that damage from its Biomass -- falls and your squadmates never cost it anything. Out of combat for 3 seconds it repairs itself at 8 Biomass a second (4 while the suit is on, and not at all while the Blade or Shield is feeding on it). Dying does not refill it: you come back with the Biomass you had. At zero Biomass the suit melts off you and every Symbiote ability locks; neither the abilities nor the suit come back -- not even by the Symbiote wrapping you itself -- until it has recovered to 20%.',
		'projecthero.guide.symbiote.passive.protect': 'Protector -- if a single hit does more than 3 damage, if a hit would leave you under 5 hearts, or if it would be fatal, the Symbiote wraps you in the suit by itself. It also wraps you whenever you drop below 5 hearts. The suit stays on until you retract it. (Retract it by hand and it holds off for a few seconds; with its Biomass spent it cannot wrap you at all.)',
		'projecthero.guide.symbiote.passive.resurrect': 'Resurrection -- the Symbiote will not let its host die. When you take a fatal hit it brings you back at half health, erupts in massive tendrils that hurl everything within 20 blocks away from you (never your squadmates), and grants Resistance for 20 seconds. It costs no Biomass, but it can only do it once every 10 minutes -- 20 for Black Suit Spider-Man and Agent Venom -- (the wait survives relogging), and a sound attack stops it entirely.',
		'projecthero.guide.symbiote.passive.squad': 'Squadmates -- no Symbiote move touches a member of your squad: tendrils, spikes, the Onslaught and the resurrection blast pass them by, and their hits never cost the Symbiote Biomass.',
		'projecthero.guide.symbiote_spider_man.passive.armour': 'The black suit is diamond-grade armour (20 armour, 2 toughness), and while it is on, Spider-Man\'s own Resistance drops from II to I -- the suit does the protecting.',
		'projecthero.agent_venom.ability.suit.desc': 'A bonded Punisher presses H and the Symbiote wraps him in the Agent Venom suit (H again retracts it). 22 armour and 2.5 toughness, +25% melee, +15% speed and jump, +15% knockback resistance, and the whole Punisher kit still works.',
		'projecthero.agent_venom.ability.agent_venom_unleashed.desc': 'Sneak + V: let the Symbiote off the leash for 10 seconds -- +50% melee, +20% speed, and every punch heals you for 20% of the damage it deals. 45 s cooldown.',
		'projecthero.guide.agent_venom.passives.body': 'Symbiote Rounds: +20% firearm damage, and every bullet that lands lashes the target with tendrils (a moment of Slowness). Living Ammunition: the suit feeds your guns -- the reserve regenerates three times as fast and reloads are 25% quicker. The Symbiote is still a Symbiote: fire, lava and loud sounds (explosions, bells, the Warden) drive it off, it wraps you on its own when you are badly hurt, and it drags you back from death once every 20 minutes. The HUD shows your three extras above the Punisher row, with the revive timer beside them.',
	} },
]);
