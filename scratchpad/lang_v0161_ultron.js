// v0.16.1 Ultron Uprising, Part A (the harder raid): the Lancer and Hijacker, Prime's Overclock / Network Override,
// the Sentry's Fist Rocket / Reconstruct / re-shield, the first-defeat line, the jam HUD, and the guide's new numbers.
// Run from the repo root: node scratchpad/lang_v0161_ultron.js   (idempotent: existing keys are rewritten in place)
require('./langset.js')([
	{
		anchor: 'entity.projecthero.ultron_pylon',
		entries: {
			'entity.projecthero.ultron_lancer': 'Ultron Lancer',
			'entity.projecthero.ultron_hijacker': 'Ultron Hijacker',
			'entity.projecthero.ultron_fist_rocket': 'Fist Rocket',
		},
	},
	{
		anchor: 'item.projecthero.ultron_pylon_spawn_egg',
		entries: {
			'item.projecthero.ultron_lancer_spawn_egg': 'Ultron Lancer Spawn Egg',
			'item.projecthero.ultron_hijacker_spawn_egg': 'Ultron Hijacker Spawn Egg',
		},
	},
	{
		anchor: 'message.projecthero.ultron.natural',
		entries: {
			'message.projecthero.ultron.jammed': 'Ultron has this key jammed -- %s s',
			'hud.projecthero.ultron.override': 'NETWORK OVERRIDE',
			'hud.projecthero.ultron.override.sub': 'Ultron is in your systems -- abilities offline %s s',
			'hud.projecthero.ultron.jammed': '%s JAMMED -- %s s',
		},
	},
	{
		anchor: 'message.projecthero.ironman.jarvis.ultron_process',
		entries: {
			'message.projecthero.ironman.jarvis.ultron_reconstruct': "He's pulling his drones in to rebuild, sir. Shoot them down first.",
		},
	},
	{
		anchor: 'boss.projecthero.ultron.say.death',
		entries: {
			'boss.projecthero.ultron.say.death_first': "You can't kill an idea. I'll see you soon.",
			'boss.projecthero.ultron.say.overclock': 'Let me show you what I was holding back.',
			'boss.projecthero.ultron.say.override': 'Your systems are mine now.',
			'boss.projecthero.ultron.say.fist': 'Catch.',
			'boss.projecthero.ultron.say.reconstruct': 'Come home, children. I need the parts.',
			'boss.projecthero.ultron.say.reshield': 'Again. I can do this forever.',
		},
	},
	{
		anchor: 'projecthero.guide.ultron.commands.body',
		entries: {
			'projecthero.guide.ultron.body': 'Stark tech turned on its makers. Ultron is a network, not one body: cut the network first, then finish him. Nothing he has adapts: your attacks always do what they say.',
			'projecthero.guide.ultron.pylons.body': 'Four pylons rise 24 blocks out (450 health, +150 per extra fighter). While any stands, Ultron\'s units within 16 blocks repair 3 health a second, and Ultron himself won\'t come. A pylon with three or more of his units within 8 blocks is shielded (a red shell): clear the guards first. A destroyed pylon bursts in an EMP that stuns nearby robots for 3 s. Iron Man\'s targeting locks onto them.',
			'projecthero.guide.ultron.waves.body': 'Seven waves, half again bigger per extra fighter, at most 36 units at once. Drones (40 health) hover and fire red bolts. Sentinel Drones (60) sprint in to claw and explode when destroyed -- step away at the beeps. The Heavy (180 health, 10 armour, from wave 3) fires four red missiles after raising its arms, and stamps up close. Sniper Frames (from wave 4) perch on the pylons and paint you with a laser for 1.5 s before a 16-damage shot, then jump to another pylon -- break line of sight. Lancers join at wave 4, Hijackers at wave 5.',
			'projecthero.guide.ultron.lancer': 'Ultron Lancer',
			'projecthero.guide.ultron.lancer.body': 'A heavy silver-and-red frame (90 health, 8 armour) behind a red hex energy shield. The shield stops projectiles and beams that come at it from the front: go round it, hit it from above, or close in -- melee gets through. Lancers advance shoulder to shoulder and lunge with an energy lance (10 damage, 4 blocks). After every lunge the shield is down for 2 s: that is your opening.',
			'projecthero.guide.ultron.hijacker': 'Ultron Hijacker',
			'projecthero.guide.ultron.hijacker.body': 'Lean, dark and fast (50 health). It runs cloaked -- only a shimmer -- until it is 6 blocks away, then latches onto you for up to 4 s: one of your ability keys goes dead for 6 s, and a worn Iron Man suit loses 15% of its energy. Hit it to break the latch. The Mind Stone and the Iron Man highlight show it while it is cloaked.',
			'projecthero.guide.ultron.prime.body': 'Once every relay is down Ultron Prime flies in (1,600 health, +450 per extra fighter, up to 5,000; 12 armour) and rebuilds two reserve pylons (300 health) -- spare bodies, shielded while he is above half health. Repulsor barrage: five quick beams. Encephalo-beam: a red line shows where it starts, then it sweeps -- below a third it sweeps there and back. Drone swarm. Network Override: a red reticle appears over whoever is hurting him most, and a second later their ability keys go dead for 4 s -- break line of sight before it lands. Below two-thirds he Overclocks (6 s, half again as fast, an 8-beam barrage) and magnetically pulls anyone in iron, chainmail, netherite or Iron Man armour in for a punch; below one-third he dive-bombs. Destroy him while a pylon stands and he uploads to it: the pylon is consumed and he rebuilds there at half health. He turns on whoever is hurting him most: every hit builds threat that fades over about 10 seconds.',
			'projecthero.guide.ultron.sentry.body': 'With no body left to jump to, the giant Sentry rises from the uplink (6 blocks tall, 2,600 health, +600 per extra fighter, up to 7,000). Arm slam up close; a chest cannon that charges for 2 s then fires a beam that slowly turns after you (20 degrees a second) -- keep moving sideways; missile rain; and the Fist Rocket: his forearm flies at you and bursts for 18 -- shoot it down (60 health). At half health he Reconstructs: for 6 s every drone in the arena flies into him, each one healing 2% -- kill them on the way. Then he shields himself and four drones hold the shield; kill them and he staggers, taking half again as much damage. At a quarter he does it all once more. He turns on whoever is hurting him most.',
			'projecthero.guide.ultron.weakness.body': 'Every robot takes 50% more from lightning and electricity (Thor, Electrokinesis) and 25% more from Hulk or any heavy melee hit, and shrugs off poison and wither. Nothing adapts: hit them the same way all day and it does the same damage. Every Ultron hit lands 30% harder than its listed number, and wears an Iron Man suit\'s hull 10% harder.',
			'projecthero.guide.ultron.commands.body': 'Operators: /projecthero raid start ultron (where you stand), /projecthero raid end ultron, /projecthero raid advancetimer ultron (skip to the next stage). Tuning: config/projecthero_ultron.json (v0.16.1 rewrites the old easy numbers in an existing file).',
		},
	},
]);
