// v0.13.22 mutation revamp, batch E lang: powers 16 Spider Adhesion, 17 Elasticity, 18 Density, 22 Plant, 27 Size --
// the new H / N abilities, every ability description rewritten for the revamped numbers, new passives, messages,
// effects and entities. Run from the repo root: node scratchpad/lang_v01322_e.js
const P16 = 'projecthero.power.power_16_spider_climbing_adhesion';
const P17 = 'projecthero.power.power_17_elasticity';
const P18 = 'projecthero.power.power_18_density_manipulation';
const P22 = 'projecthero.power.power_22_plant_manipulation_chlorokinesis';
const P27 = 'projecthero.power.power_27_size_manipulation';
require('./langset.js')([
	// ------------------------------------------------------------------ 16 Spider Climbing / Adhesion
	{ anchor: P16 + '.passive.jump', entries: {
		[P16 + '.desc']: 'Cling to any surface and move across it. Turn the grip on, then double-tap jump against a wall or ceiling to grab it -- climb up, crawl sideways, hang still, and double-tap sneak (or jump) to let go. A predator\'s kit on top: sticky strikes, pounces, a spider-sense that side-steps the next attack and a venomous bite. Slower and less sure-footed than the arachnid who evolves out of it.',
		[P16 + '.ability.adhesive_strike.desc']: 'A sticky palm strike (4.5-block reach): 8.5 damage and the target is glued in place -- rooted and weakened for 7 seconds. 3.4s cooldown.',
		[P16 + '.ability.pounce.desc']: 'A predatory leap along your aim. The first enemy you land on within 1.5 seconds takes 7 damage and is slowed. No fall damage from the leap. 4.25s cooldown.',
		[P16 + '.ability.wall_leap.desc']: 'Powerfully launch away from the surface you are gripping (or kick off backwards in mid-air). 2.55s cooldown.',
		[P16 + '.ability.predator_rush.desc']: '35 seconds of the hunt: Speed II, Haste II, Jump Boost III and Strength, with your eyes glowing red. 30s cooldown.',
		[P16 + '.ability.spider_sense']: 'Spider-Sense Dodge',
		[P16 + '.ability.spider_sense.desc']: 'H: your spider-sense tingles for about a second -- the next attack that would hit you (melee or projectile) is dodged outright with a sidestep and a burst of speed. A red halo pulses over your head while it is primed. 6s cooldown.',
		[P16 + '.ability.venom_bite']: 'Venom Bite',
		[P16 + '.ability.venom_bite.desc']: 'N: a lunging bite on whatever is in front of you (3.8 blocks): 7 damage, Poison II for 6 seconds and Slowness II for 4. A miss costs nothing. 7s cooldown.',
		[P16 + '.passive.sense']: 'Spider-sense: you see a faint red shimmer over every hostile creature nearby (brighter and wider while Spider-Sense Dodge is primed)',
	} },
	// ------------------------------------------------------------------ 17 Elasticity
	{ anchor: P17 + '.ability.elastic_form.desc', entries: {
		[P17 + '.desc']: 'A rubber body: your arms visibly stretch across the battlefield to punch, grab and slingshot, you inflate into a projectile-bouncing balloon, flatten into a parachute, and shrug off falls entirely.',
		[P17 + '.ability.stretch_punch.desc']: 'Your arm shoots out (visibly stretching to the target, up to 15 blocks) for 14.5 damage. Hold to charge (a bar shows it): +6 damage per second, up to +12 at 2s, and 2 blocks more reach per second. Cooldown 1.7s, +0.85s for every second you hold it.',
		[P17 + '.ability.double_fist_slam.desc']: 'On the ground: both arms stretch 7 blocks out for a heavy forward smash, 20.5 damage in a 4-block ball. More than 5 blocks up: plunge straight down for a 24-damage ground pound. 6s cooldown.',
		[P17 + '.ability.slingshot.desc']: 'Anchor and launch. Aimed at terrain your arm snaps to it and reels you in; aimed at a creature (45 blocks) your arm latches onto THEM, you are hauled in at speed, and slam into them for 17 damage. 2.55s cooldown.',
		[P17 + '.ability.giant_hammer_fist.desc']: 'Both arms stretch out and swell into giant fists that slam the aim point: 41 damage in a 4.5-block area, plus Slowness. 30s cooldown.',
		[P17 + '.ability.elastic_grab.desc']: 'Your arm stretches out (15 blocks), wraps a mob or player and reels them in, holding them at arm\'s length for up to 6 seconds; press again to hurl them for 8.5 damage. 5.1s cooldown after a throw.',
		[P17 + '.ability.elastic_form.desc']: 'Toggle a body shape. All three shapes deflect projectiles outright. Plain C is Elastic: +reach, +speed, Jump Boost, +9.5 ability damage and +5 melee damage, a rubbery sheen, melee attackers bounce off, and while you hold the jump key your landings rebound like a slime block. Shift + C opens a wheel to switch to Inflated (wider, slower, Slowness II, -50% damage taken, 100% knockback resistance, attackers flung 10 blocks) or Compression (one block tall, +25% move speed).',
		[P17 + '.ability.rubber_shield']: 'Rubber Shield',
		[P17 + '.ability.rubber_shield.desc']: 'H (hold): inflate into a rubber balloon. Every projectile that reaches you bounces straight back at whoever fired it, melee attackers are flung away, you take 65% less damage and cannot be knocked back -- but you move at half speed. Drains the Rubber bar (5 seconds from full, refills in ~9s). 8s cooldown once you let go.',
		[P17 + '.ability.parachute_glide']: 'Parachute Glide',
		[P17 + '.ability.parachute_glide.desc']: 'N: flatten out into a canopy with your arms spread -- you fall slowly and glide forward wherever you look (look steeply down to dive faster). Used on the ground it hops you up first. Lasts up to 8 seconds, ends when you land, or press N again to fold up. 5s cooldown.',
	} },
	// ------------------------------------------------------------------ 18 Density Manipulation
	{ anchor: P18 + '.passive.mode', entries: {
		[P18 + '.desc']: 'Dial your density anywhere from 25% to 300% -- lighter is faster, heavier hits harder and shrugs off blows -- phase through walls, flicker intangible, and crush your enemies under their own weight. A thin shell over your body glows blue when light and orange when heavy.',
		[P18 + '.ability.density_anchor.desc']: 'Slam to maximum density and root in place for 10s: cannot be moved by mobs, immune to knockback, 76% damage reduction, but cannot sprint or jump. A 25.5s cooldown starts when it ends.',
		[P18 + '.ability.heavy_impact.desc']: 'More than 6 blocks up: slam straight down for up to 54 damage in a 20-block radius (falls off with distance) and shatter some ground. On the ground: launch upward, then slam -- the rise only ever lasts 3 seconds before the slam forces itself, so you are never left stranded in the sky. 8.5s cooldown.',
		[P18 + '.ability.zero_density.desc']: 'Drop straight to 25% density -- maximum speed, minimum weight. 1.7s cooldown.',
		[P18 + '.ability.phase.desc']: 'Toggle: vibrate and walk straight through solid blocks while the Phase meter lasts (115, 15% bigger than before). You are immune to all damage while phasing, and you render see-through so everyone can tell. This is intangibility, not flight -- you cannot rise more than 2 blocks above the ground beneath you. Jump to rise, sneak to sink; your head buried in a block costs air.',
		[P18 + '.ability.intangible_dodge']: 'Intangible Dodge',
		[P18 + '.ability.intangible_dodge.desc']: 'H: flicker intangible for half a second with a short dash -- every attack, projectile and blast passes straight through you. A cyan shimmer shows it. 4s cooldown.',
		[P18 + '.ability.crushing_touch']: 'Crushing Touch',
		[P18 + '.ability.crushing_touch.desc']: 'N: charge your fist (it glows orange) for up to 8 seconds. The next creature you hit becomes super-dense for 5 seconds: 70% slower, unable to jump, triple gravity, and anything flying is dragged out of the sky. 12s cooldown.',
		[P18 + '.passive.shell']: 'A thin shell over your body shows your density: blue when you are light, orange when you are heavy',
	} },
	// ------------------------------------------------------------------ 22 Plant Manipulation
	{ anchor: P22 + '.passive.swing_through_grass', entries: {
		[P22 + '.desc']: 'Command growth itself: thorns, snaring vines, living walls, explosive overgrowth, a planted thorn turret that fights for you and healing spore clouds -- all stronger on living ground.',
		[P22 + '.ability.thorn_shot.desc']: 'Fired from your hand for 9.5 damage and Poison III for 8 seconds (1.7s cooldown). Shift, while on natural ground or holding bone meal/a sapling, for Branch Thrust: a 15-block lunge dealing 18 damage with a trail of logs that grows into a tree (8.5s cooldown).',
		[P22 + '.ability.vine_grab.desc']: 'Root a target for 8 seconds with a lashing vine, 3.6 damage. Shift for a 10-block cone that snares every enemy in front of you. 6s cooldown.',
		[P22 + '.ability.vine_swing.desc']: 'Attach a vine line to a block up to 100 blocks away and swing toward it (no fall damage for 10s) -- or, aimed at a target instead, pull them to you. 0.85s cooldown.',
		[P22 + '.ability.overgrowth.desc']: 'Hold for 5 seconds to charge (roots gather, your eyes glow green), then explosive growth in a 20-block radius: 54 damage, rooted and Poison X for 10 seconds, and a dense thicket that recedes after a few seconds. 51s cooldown.',
		[P22 + '.ability.living_wall.desc']: 'Conjure a wall of leaves -- look up for a dome, look down for a bridge. Sneak instead to charge your touch with bone meal for 30 seconds. 6.8s cooldown.',
		[P22 + '.ability.natures_blessing.desc']: 'Toggle: your skin turns to bark and leaves. While standing on grass/moss or within 5 blocks of any plant or leaf, gain Regeneration II and +12 to all ability and melee damage. Enemies within 3 blocks of you are poisoned.',
		[P22 + '.ability.thorn_sentry']: 'Thorn Sentry',
		[P22 + '.ability.thorn_sentry.desc']: 'H: plant a living turret of cactus and flowering azalea where you look (up to 6 blocks). For 15 seconds it turns to the nearest hostile it can see within 14 blocks and spits thorns at it twice a second -- 5 damage and a short Poison each. It never targets you, your squad or any player. One at a time: a new one replaces the old. 25s cooldown.',
		[P22 + '.ability.spore_cloud']: 'Spore Cloud',
		[P22 + '.ability.spore_cloud.desc']: 'N: breathe out a 4.5-block cloud of spores just ahead of you for 8 seconds. Every half second, enemies inside are poisoned and blinded while you and your squad-mates inside are healed and given Regeneration. 16s cooldown.',
	} },
	// ------------------------------------------------------------------ 27 Size Manipulation
	{ anchor: P27 + '.passive.fall', entries: {
		[P27 + '.desc']: 'Change body scale -- every change eases over a second -- for stealth, mobility or giant strength: shrink foes with a punch, ride mobs when tiny, and pick them up when huge. Neither form is universally superior.',
		[P27 + '.ability.giant_punch.desc']: 'Enlarge the striking arm for a wide sweeping hit: 12 damage (+6 in Large, +18 in Giant, a third in Tiny) -- its reach scales with how big you are (roughly 4x in Giant form). 2.55s cooldown.',
		[P27 + '.ability.stomp.desc']: 'Stomp for a ground shockwave: 9.6 damage (+6 Large, +18 Giant) and a slow, with a radius that scales with your size (much wider as a giant). 6.8s cooldown.',
		[P27 + '.ability.giant_form.desc']: 'Toggle (Z): grow to ~15 blocks tall over one second, in a burst of particles -- ~16-block reach, +18 damage on every move, 3-block leaps, and creatures you walk over are trampled. Drains the Size Strain bar (575, ~26s); once you shrink back it slowly recharges on its own.',
		[P27 + '.ability.tiny_dash.desc']: 'Rapidly dash forward with a burst of speed and brief damage resistance -- duck under attacks or slip through a gap. 4.25s cooldown.',
		[P27 + '.ability.large_form.desc']: 'Toggle (C): grow to about 6 blocks tall over one second, in a burst of particles -- ~7-block reach, +6 damage, a wide pickup radius, and 3-block leaps. Press again for normal size.',
		[P27 + '.ability.shrink_punch']: 'Shrink Punch',
		[P27 + '.ability.shrink_punch.desc']: 'H: a punch that shrinks its target to half size for 8 seconds -- a smaller hitbox, shorter reach and 40% weaker hits -- plus 8 damage. Bosses take the hit but keep their size. A miss costs nothing. 10s cooldown.',
		[P27 + '.ability.mount']: 'Mount',
		[P27 + '.ability.mount.desc']: 'N: in Tiny form, climb onto the mob you are looking at and ride it (up to 60 seconds). In Large or Giant form, pick up the mob you are looking at (anything under 60% of your height) and carry it in your hand for up to 20 seconds. Press N again to hop off or throw it. It is always let go safely if you change form, die or lose the power. 2s cooldown after.',
	} },
	// ------------------------------------------------------------------ messages / effects / entities
	{ anchor: 'message.projecthero.elasticity.form_elastic', entries: {
		'message.projecthero.elasticity.rubber_low': 'Not enough Rubber to inflate',
		'message.projecthero.density.crush_armed': 'Crushing Touch ready -- hit something',
		'message.projecthero.plant.no_room': 'No room to plant a sentry there',
		'message.projecthero.size.mount_needs_form': 'Mount needs Tiny form (ride) or Large / Giant form (carry)',
		'message.projecthero.size.mount_none': 'Nothing there you can mount',
		'message.projecthero.size.mount_too_big': 'Too big to pick up',
		'effect.projecthero.crushing_density': 'Crushing Density',
		'effect.projecthero.shrunken': 'Shrunken',
		'entity.projecthero.thorn_sentry': 'Thorn Sentry',
		'entity.projecthero.thorn': 'Thorn',
	} },
]);
