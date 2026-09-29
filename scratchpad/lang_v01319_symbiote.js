// v0.13.19 Symbiote lang changes.
require('./langset.js')([
	{ anchor: 'entity.projecthero.symbiote_host', entries: {
		'entity.projecthero.symbiote_spike': 'Symbiote Spike',
		'entity.projecthero.symbiote_tendril': 'Symbiote Tendril',
	} },
	{ anchor: 'hud.projecthero.symbiote.hp_broken', entries: {
		'hud.projecthero.symbiote.biomass': 'Biomass %s%%',
	} },
	{ anchor: 'projecthero.symbiote.ability.spikes.desc', entries: {
		'projecthero.symbiote.ability.tendril_grab': 'Tendril Grab',
		'projecthero.symbiote.ability.tendril_grab.desc': 'Sneak+C: lash a tendril out of your hand and suspend a target helpless in front of you. Press C again to hurl it away (it is thrown for you after 3.5 seconds). 5-second cooldown.',
		'projecthero.symbiote.ability.tendril_strike.desc': 'R: a living tendril whips out of your hand along your aim, up to 30 blocks, and hits whatever it meets first for 15 damage and a solid knockback. It fires whether or not something is in your sights -- aim well, it can miss. Blocks stop it. Shift+R: Tendril Sweep -- seven tendrils fan across everything in front of you for 8-10 damage each and slow it for 7 seconds.',
		'projecthero.symbiote.ability.leap.desc': 'X: the Symbiote launches you in the direction you are looking with enough force to carry you 20 blocks -- a single real launch, so you fly and fall in an arc. You ram the first enemy in the way for 15 damage. 2-second cooldown. You take no fall damage until you next land.',
		'projecthero.symbiote.ability.spike_shot': 'Symbiote Spike',
		'projecthero.symbiote.ability.spike_shot.desc': 'G: fire a living spike from your hand -- a real projectile that flies fast and almost flat, so it can miss. It hits for 14 damage and poisons the target with Wither V for 5 seconds. 4-second cooldown. Sneak+G: Spike Fan -- five spikes from both hands in a cone in front of you, 10-second cooldown.',
		'projecthero.symbiote.ability.spike_fan': 'Spike Fan',
		'projecthero.symbiote.ability.spike_fan.desc': 'Sneak+G: five Symbiote Spikes at once, fanned across 40 degrees in front of you -- each one 14 damage and Wither V for 5 seconds. Its own 10-second cooldown, shown as a thin strip along the bottom of the G box.',
		'projecthero.symbiote.ability.onslaught.desc': 'The ultimate. Sneak and hold Z for 3 seconds: a pool of living black spreads under you and tendrils claw up out of it, slowing everything inside. Release and it erupts -- a crown of huge tendrils bursts outward to 9 blocks and a tendril spears up out of the ground into every enemy in range: 20 damage, Wither III for 8 seconds, Blindness, Slowness IV and Weakness II, and they are thrown into the air. 90-second cooldown; releasing early cancels it for free.',
		'projecthero.symbiote.ability.shield.desc': 'Sneak+V: raise a living shield in front of you. It works like a real shield -- it stops almost all damage from your front, nothing from the side or back -- and you are slowed while it holds. No time limit: it feeds on 0.5 Biomass a second while it is up, and drops when the Biomass runs out.',
		'projecthero.symbiote.ability.barrage.desc': 'Z: a flurry of tendrils from alternating hands for over a second, each one flying down your aim up to 30 blocks and hitting whatever it meets -- it fires whether you are aiming at something or not, so it can miss. Sneak + hold Z charges Symbiote Onslaught, the ultimate. With Symbiote Blade out, Z is a wide slashing arc instead.',
		'projecthero.symbiote.ability.blade.desc': 'V: a curved living blade grows out of your hand -- +5 melee, swings 50% faster, bites past armour. No time limit: it feeds on 0.3 Biomass a second while it is out, and dissolves when the Biomass runs out. You cannot hold an item while it is out. Sneak+V toggles Symbiote Shield.',
		'projecthero.symbiote.ability.spikes.desc': 'C: raise living spikes -- attackers in melee take reflected damage, like Thorns IV. Sneak+C is Tendril Grab.',
	} },
	{ anchor: 'projecthero.guide.symbiote.passive.resurrect', entries: {
		'projecthero.guide.symbiote.passive.resurrect': 'Resurrection -- the Symbiote will not let its host die. When you take a fatal hit it brings you back at half health, erupts in massive tendrils that hurl everything within 20 blocks away from you, and grants Resistance for 20 seconds. It costs no Biomass, but it can only do it once every 10 minutes (the wait survives relogging), and a sound attack stops it entirely.',
		'projecthero.guide.symbiote.passive.biomass': 'Biomass -- the Symbiote\'s own 200-point life bar, shown under the ability boxes as a percentage and a thin bar. Every hit still lands on you in full, but the Symbiote also loses 40% of that damage from its Biomass. Out of combat for 5 seconds it repairs itself (more slowly while the suit is on, and not at all while the Blade or Shield is feeding on it). Dying no longer refills it: you come back with the Biomass you had. At zero Biomass every Symbiote ability locks until it has recovered.',
	} },
]);
