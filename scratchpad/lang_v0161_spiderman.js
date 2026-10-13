// v0.16.1 Spider-Man revamp: the 18-move kit, right-click swinging, the web structures, the HUD, the guide, and the
// synthesised sounds. Sets the lang keys AND the sounds.json entries (idempotent -- re-run after a merge conflict).
// Run from the repo root: node scratchpad/lang_v0161_spiderman.js
const fs = require('fs');
const path = require('path');

const A = 'projecthero.spider_man.ability.';
require('./langset.js')([
	{
		anchor: 'projecthero.spider_man.ability.web_blossom.desc',
		entries: {
			// ---- names ----
			[A + 'web_shot']: 'Web Shot',
			[A + 'web_bomb']: 'Web Bomb',
			[A + 'impact_web']: 'Impact Web',
			[A + 'point_zip']: 'Point Zip',
			[A + 'web_yank']: 'Web Yank',
			[A + 'zip_kick']: 'Zip Kick',
			[A + 'web_strike']: 'Web Strike',
			[A + 'uppercut']: 'Uppercut Launch',
			[A + 'spider_counter']: 'Spider-Sense Counter',
			[A + 'web_whip']: 'Web Whip',
			[A + 'grab_hurl']: 'Grab & Hurl',
			[A + 'swing_throw']: 'Web Swing-Throw',
			[A + 'tightrope']: 'Tightrope',
			[A + 'web_wall']: 'Web Wall',
			[A + 'weave']: 'Weave Mode',
			[A + 'spider_pulse']: 'Spider-Sense Pulse',
			[A + 'web_cocoon']: 'Web Cocoon',
			[A + 'web_blossom']: 'Web Blossom',
			[A + 'zip_boost']: 'Zip Boost',
			[A + 'web_swing']: 'Web Swing',
			// ---- descriptions (guide) ----
			[A + 'web_shot.desc']: 'A thwip: a spinning glob of webbing flies where you aim and splats on what it hits -- 2 damage, 2 webbing, 0.3 s. Every hit is a stickiness stack (max 3): slowed, weakened and its jump weighed down for 18 seconds; three inside 3 s wrap an ordinary mob in a cocoon for 18 s. Puts fires out.',
			[A + 'web_bomb.desc']: 'A ball of webbing that bursts where you aim (up to 30 blocks): everything within 4 blocks takes 3 damage and is rooted for 18 seconds, and real cobwebs fill the air round the splash for 18 s (they drop nothing and you and your squad walk through them). Squadmates are never caught. 15 webbing, 6-second cooldown.',
			[A + 'impact_web.desc']: 'Hold R to charge (full after another second), release to fire one heavy ball: 10 damage (up to 15 fully charged), a hard knockback, and whatever it hits is cocooned for 18 s with a real cobweb where it is pinned -- knocked into a wall, it is pinned there. Fires by itself after 3 s. 12 webbing, 1.5-second cooldown.',
			[A + 'point_zip.desc']: 'Fire at the block you aim at (up to 100 blocks) and get hauled all the way to it. Aimed at a wall, you land on it already crawling (if your wall crawl is on); aimed at one of your tightropes or web strands, you land standing on top of it, balanced and facing along it -- even zipping up from below. 5 webbing, 0.5-second cooldown.',
			[A + 'web_yank.desc']: 'Line something and haul (50 blocks). An armed mob or player has its held items ripped out and flung to you; otherwise light things come to you, heavy things pull you to them, and dropped items come flying. 6 webbing, 1.5-second cooldown.',
			[A + 'zip_kick.desc']: 'Hold G: zip at the enemy you aim at (30 blocks) and land a flying kick -- 14 damage and a huge knockback -- then bounce off it. 10 webbing, 5-second cooldown.',
			[A + 'web_strike.desc']: 'Web an enemy (30 blocks), get pulled in and land a three-punch combo: 5, 5, then a 9-damage finisher that knocks it back. 8 webbing, 2.5-second cooldown.',
			[A + 'uppercut.desc']: 'A rising uppercut on the enemy in front of you (5 blocks): 8 damage and it is launched skyward -- you leap after it. For 3 seconds every hit you land keeps it (and you) in the air. 6 webbing, 6-second cooldown.',
			[A + 'spider_counter.desc']: 'Hold X: a one-second guard. The next attack -- a blow or a projectile -- is dodged with a back-flip and answered: 12 damage and a knockback to the attacker, and a projectile is sent straight back. 4 webbing; 8-second cooldown (3 if nothing came).',
			[A + 'web_whip.desc']: 'Spin a strand once round you: everything within 4 blocks takes 7 damage, is knocked away and stuck with webbing. 10 webbing, 4-second cooldown.',
			[A + 'grab_hurl.desc']: 'Web the mob you aim at (16 blocks), haul it to your hands and throw it: 10 damage when it hits something. With no mob, rip out the loose block you aim at -- not a chest or other block entity, nothing unbreakable or obsidian-hard -- and throw that: it shatters into its drops and hurts what it hits. Block grabbing obeys the server\'s config, terrain-damage setting, mobGriefing and land claims. 10 webbing, 5-second cooldown.',
			[A + 'swing_throw.desc']: 'Hold Z: web a mob (30 blocks) and whirl it round your head for as long as you hold; let go to hurl it the way you look -- 6 damage when it lands in something. Bosses are too heavy. 10 webbing, 3-second cooldown.',
			[A + 'tightrope.desc']: 'Press C on a surface for anchor A, then on a second surface within 32 blocks of it: a tightrope between the two. Either end can be fixed ON another of your tightropes or woven-web strands, so ropes join into networks -- walk across a junction and you carry straight on along the next rope; cut or fade a rope and the ropes hanging from it go too. Walk it perfectly balanced, Sneak to drop and hang under it (Jump flips you back on top, Sneak lets go), or swing from it. Aim at nothing to forget anchor A. 10 webbing.',
			[A + 'web_wall.desc']: 'A sticky 3 x 3 barrier 2.5 blocks in front of you. It stops every projectile not fired by you or your squad and traps mobs that walk into it; you and your squad can climb it. 25 webbing, 10-second cooldown.',
			[A + 'weave.desc']: 'Hold C: the point you aim at (up to 24 blocks) becomes the centre. Every left-click shoots a radial strand from the centre to where you aim (up to 10 strands, 24 blocks each, 8 webbing a strand); let go of C and spiral threads fill in between them (30 webbing) -- a huge spider-web across a ravine, a street or a room. You and your squad can walk on it, climb it and swing from it; anyone else sticks fast. 15-second cooldown; closes by itself after 30 s.',
			[A + 'spider_pulse.desc']: 'Your Spider-Sense flares: every hostile within 32 blocks glows for you alone for 6 seconds, you get a burst of speed, and for 2 seconds every attack aimed at you is dodged. 10 webbing, 20-second cooldown.',
			[A + 'web_cocoon.desc']: 'Wrap an enemy that is already weakened (40% health or less) or webbed / slowed: it hangs helpless for 30 seconds and takes 60% more damage from you and your squad. Not bosses. 25 webbing, 10-second cooldown.',
			[A + 'web_blossom.desc']: 'Hold V for 2 seconds, then leap and fire webbing in every direction: everything within 20 blocks takes 40 damage and is trapped for 18 seconds, and a ring of real cobwebs is left round where you stood. Let go early to cancel (nothing spent). 75 webbing, 20-second cooldown.',
			[A + 'zip_boost.desc']: 'Sprint + Jump in the air: a quick web-zip forward. 3 webbing.',
			[A + 'web_swing.desc']: 'Hold right-click with an empty hand to fire a web and swing from it; let go to release with all your momentum. Finds real anchors -- rooftops, cliffs, canopy, cave ceilings, your own webs -- and fires one high ahead of you when there is nothing to catch.',
		},
	},
	{
		anchor: 'hud.projecthero.spider_man.web_blossom',
		entries: {
			'hud.projecthero.spider_man.move_triple': '%s  |  Shift: %s  |  Hold: %s',
			'hud.projecthero.spider_man.traversal_hint': 'Right-click (empty hand): swing  |  Sprint+Jump: zip',
			'hud.projecthero.spider_man.web_alarm': 'Web touched  %sm',
			'hud.projecthero.spider_man.status.weave': 'Weave %s/%s -- click; let go of C',
			'hud.projecthero.spider_man.status.anchor': 'Anchor A set -- C on a 2nd surface',
			'hud.projecthero.spider_man.status.impact': 'Impact Web %s%%',
			'hud.projecthero.spider_man.status.counter': 'Counter stance',
			'hud.projecthero.spider_man.status.air_combo': 'Air combo!',
			'hud.projecthero.spider_man.status.hanging': 'Hanging -- Jump up, Sneak drop',
			'hud.projecthero.spider_man.wall_crawl_on': 'Wall Crawl: ON',
			'hud.projecthero.spider_man.wall_crawl_off': 'Wall Crawl: OFF',
			'hud.projecthero.spider_man.sounds_off': 'Sounds: OFF',
		},
	},
	{
		anchor: 'message.projecthero.spider_man.web_blossom',
		entries: {
			'message.projecthero.spider_man.web_bomb': 'Web Bomb -- %s stuck',
			'message.projecthero.spider_man.wall_crawl_on': 'Wall Crawl ON -- run or fly into a wall to stick to it (N to switch off).',
			'message.projecthero.spider_man.sounds_on': 'Spider-Man sounds ON (Shift+N to mute).',
			'message.projecthero.spider_man.sounds_off': 'Spider-Man sounds muted -- Spider-Sense, web alarm and web sounds (Shift+N to unmute).',
			'message.projecthero.spider_man.wall_crawl_off': 'Wall Crawl OFF -- walls will not catch you (N to switch on).',
			'message.projecthero.spider_man.cannot_grab_block': 'That block will not come loose.',
			'message.projecthero.spider_man.block_grab_off': 'Block grabbing is switched off on this server.',
			'message.projecthero.spider_man.anchor_set': 'Anchor A set -- press C on a second surface.',
			'message.projecthero.spider_man.anchor_cleared': 'Anchor A cleared.',
			'message.projecthero.spider_man.rope_too_long': 'Too far for a tightrope (%s blocks at most).',
			'message.projecthero.spider_man.weave_open': 'Weave Mode -- left-click to spin strands, let go of C to finish.',
			'message.projecthero.spider_man.weave_full': 'The web has all its strands.',
			'message.projecthero.spider_man.weave_too_far': 'Too far from the centre (%s blocks at most).',
			'message.projecthero.spider_man.cocoon_needs_weak': 'Too strong to wrap -- weaken it or web it first.',
		},
	},
	{
		anchor: 'projecthero.guide.spider_man.controls',
		entries: {
			'projecthero.guide.spider_man.controls': 'Controls -- 18 moves',
			'projecthero.guide.spider_man.controls.body': 'Each of R, G, X, Z, V and C has three moves: tap the key, Shift + the key, or hold it (about half a second -- a tap fires when you let go). Every move has its own cooldown; the HUD shows each key\'s tap cooldown in its box, its Shift and hold cooldowns as thin blue and red bars under it, and holding Left Alt lists all 18 names.',
			'projecthero.guide.spider_man.how.tap': 'tap',
			'projecthero.guide.spider_man.how.shift': 'Shift+',
			'projecthero.guide.spider_man.how.hold': 'hold',
			'projecthero.guide.spider_man.traversal': 'Traversal (no ability key)',
			'projecthero.guide.spider_man.traversal.swing': 'Web Swing -- hold right-click with an empty hand and let go to release with your momentum (a fast release somersaults; a long drop ends in a superhero landing). Right-click still opens doors, chests and villagers when you are looking at one.',
			'projecthero.guide.spider_man.traversal.zip_boost': 'Zip Boost -- Sprint + Jump in the air for a quick web-zip forward (3 webbing). Plain Jump in the air is still the double jump.',
			'projecthero.guide.spider_man.traversal.crawl': 'Wall crawling engages by itself when you run or fly into a wall in the air, or sprint into one -- swinging into a wall drops the line and takes the wall. Sneak lets go. N switches the automatic wall crawl on or off (shown next to your Web bar; it stays as you left it); while it is off nothing sticks you to a wall.',
			'projecthero.guide.spider_man.traversal.rope': 'On your tightrope you stay balanced, and at a junction you carry on along whichever rope runs your way; Sneak drops you under it to hang, Jump flips you back on top. Tap G at a rope to zip onto it and land standing.',
			'projecthero.guide.spider_man.traversal.sounds': 'Shift+N mutes (or unmutes) your notification sounds -- the Spider-Sense ping and Pulse, the web alarm and the web-building sounds. "Sounds: OFF" shows on your HUD while they are muted; it stays as you left it.',
			'projecthero.guide.spider_man.traversal.cut': 'Attack one of your own webs to cut it down.',
			'projecthero.guide.spider_man.traversal.perch': 'Ledge perch -- Sneak standing still at the edge of a drop (3+ blocks), or up on a fence, wall or post, and you sink into the perch: low on the ledge, leaning slightly forward, legs fanned wide out to the sides and both hands meeting on the ledge between your feet. Sneak still keeps you from falling off.',
			'projecthero.guide.spider_man.webs': 'Webs you build',
			'projecthero.guide.spider_man.webs.body': 'Tightropes, Web Walls and woven webs are spun from silk, not blocks -- nothing in the world is placed or broken, and they fade after 5 minutes (or when their area unloads). You and your squad stand, walk, climb and swing on them; anyone else who touches one sticks fast, and your Spider-Sense pings -- a chime and an arrow on your HUD pointing at the spot, wherever you are within 160 blocks. You can have 12 at once; a new one replaces your oldest.',
			'projecthero.guide.spider_man.passive.crawl': 'Wall and ceiling crawling -- run or fly into a wall to stick, look where you want to go and move; Sneak or reach the ground to let go.',
			'projecthero.guide.spider_man.passive.double_jump': 'A second jump in mid-air, once every second (with Sprint held it is the Zip Boost instead).',
			'projecthero.guide.spider_man.passive.sense': 'Spider-Sense -- a ring round your crosshair lights up on the side trouble is coming from: red for a blow or shot about to land, orange for a hostile closing in (even from behind) or a bow, crossbow or gun aimed at you, yellow for a hazard (primed TNT, a swelling creeper, lava ahead, a falling block, a TNT-wired pressure plate). Icons and up/down arrows tell you what and where; the screen edge ripples on that side. Anyone winding up or aiming at you is outlined for you alone, incoming arrows show a faint path line, and invisible threats shimmer. Others see the tingle lines round your head. Move, jump or crouch during a warning for a guaranteed dodge.',
			'projecthero.guide.spider_man.passive.dodge': 'Half of all melee attacks and 60% of projectiles are simply dodged. A dodged projectile never passes through: about 40% of the time it is sent straight back at whoever fired it, otherwise an arrow is caught and anything else is swatted aside.',
			'projecthero.guide.spider_man.passive.arrows': 'Arrows you dodge and do not send back are caught out of the air and kept.',
			'projecthero.guide.symbiote_spider_man.body': 'A Symbiote that bonded with an existing Spider-Man: the living black suit over the web-slinger\'s own gear. Every web move stays where it was -- except that with the suit on, Shift+X, Shift+Z and (in the air) Shift+C are the Symbiote\'s instead of Uppercut Launch, Grab & Hurl and Web Wall.',
			'projecthero.guide.symbiote.host.spider_man': 'A player who already holds Spider-Man becomes Black Suit Spider-Man instead -- the GeckoLib black suit, keeping the whole web kit, plus three Symbiote moves on Shift+X, Shift+Z and an airborne Shift+C.',
		},
	},
	{
		anchor: 'subtitles.projecthero.wolverine_sniff',
		entries: {
			'subtitles.projecthero.spider_thwip': 'Web shooter thwips',
			'subtitles.projecthero.spider_zip': 'Web line zips',
			'subtitles.projecthero.spider_splat': 'Webbing splats',
			'subtitles.projecthero.spider_sense': 'Spider-Sense tingles',
			'entity.projecthero.web_structure': 'Spider Web',
			'entity.projecthero.thrown_block': 'Thrown Block',
			'entity.projecthero.web_shot': 'Web Shot',
		},
	},
]);

// ---- sounds.json: the thwip (three takes), zip, splat and Spider-Sense (scratchpad/synth_spiderman_sounds.js)
const SOUNDS = path.join(__dirname, '../src/main/resources/assets/projecthero/sounds.json');
const text = fs.readFileSync(SOUNDS, 'utf8');
const eol = text.includes('\r\n') ? '\r\n' : '\n';
const sounds = JSON.parse(text);
sounds.spider_thwip = {
	subtitle: 'subtitles.projecthero.spider_thwip',
	sounds: [{ name: 'projecthero:spiderman/thwip1' }, { name: 'projecthero:spiderman/thwip2' }, { name: 'projecthero:spiderman/thwip3' }],
};
sounds.spider_zip = { subtitle: 'subtitles.projecthero.spider_zip', sounds: [{ name: 'projecthero:spiderman/zip' }] };
sounds.spider_splat = { subtitle: 'subtitles.projecthero.spider_splat', sounds: [{ name: 'projecthero:spiderman/splat' }] };
sounds.spider_sense = { subtitle: 'subtitles.projecthero.spider_sense', sounds: [{ name: 'projecthero:spiderman/sense' }] };
fs.writeFileSync(SOUNDS, JSON.stringify(sounds, null, 2).replace(/\n/g, eol) + eol);
console.log('sounds.json updated');
