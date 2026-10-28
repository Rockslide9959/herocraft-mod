// v0.17.1 Omnitrix, second batch of aliens (Stinkfly, Wildvine, Ripjaws, Grey Matter): their lang lines as ONE contiguous
// block at the END of en_us.json, and their sounds as ONE contiguous block at the end of sounds.json. Re-running replaces both
// blocks (kept apart from gen_omnitrix_lang.js's block so parallel alien batches never touch the same lines).
//   node scratchpad/gen_omnitrix_aliens_b_lang.js
'use strict';
const fs = require('fs');
const path = require('path');
const ASSETS = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero');
const LANG = path.join(ASSETS, 'lang', 'en_us.json');
const SOUNDS_JSON = path.join(ASSETS, 'sounds.json');

const MOVES = {
	stinkfly: {
		slime_shot: ['Slime Shot', 'Spits a glob of goo (3 damage). Whatever it hits is stuck fast for 1.5 s, then slowed.'],
		slime_net: ['Slime Net', 'Spits a wide net of goo: everything where it lands is stuck in place for 3 s.'],
		toxic_gas: ['Toxic Gas', 'A stinking green cloud a few blocks ahead for 5 s: poisons, sickens and stings (1.5 every half second) anything inside.'],
		stinger_stab: ['Stinger Stab', 'Your tail whips under you and stabs the creature in front (6 damage + Poison II).'],
		wing_flight: ['Wing Flight', 'Take off (a hop into the air) or land. Your wings are always ready, so double-tapping jump flies too. No fall damage, ever.'],
		wing_dash: ['Wing Dash', 'A burst of speed wherever you look -- in the air or on the ground.'],
		dive_bomb: ['Dive Bomb', 'Plunge from the air (on the ground you hop up first) and splash down in a ring of goo: up to 8 damage and a slow.'],
		wing_gust: ['Wing Gust', 'Beat your wings at the enemy: shoves everything in front back, puts out fires and blows projectiles out of the air.'],
		goo_storm: ['Goo Storm', 'Goo rains down on the spot you look at for 6 s: everything under it is slowed and stung.'],
		slime_barrage: ['Slime Barrage', 'Five globs of goo in a fan (2.5 each, each one sticks).'],
		stinger_jab: ['Stinger Jab', 'Your punches carry a little poison.'],
		goo_spit: ['Goo Spit', 'Empty hand: a quick small glob of goo (2 damage, slows).'],
	},
	wildvine: {
		seed_bomb: ['Seed Bomb', 'Lobs an explosive seed that bursts in a shower of spores: 7 damage around it, no damage to blocks.'],
		thorn_whip: ['Thorn Whip', 'A wide lash of thorny vines in front of you (6 damage, knockback).'],
		vine_bind: ['Vine Bind', 'Vines lash out to the creature you look at (18 blocks) and pin it in place for 4 s.'],
		pollen_burst: ['Pollen Burst', 'A cloud of pollen around you: nausea, slowness and weakness for everything nearby.'],
		vine_grapple: ['Vine Grapple', 'Whips a vine to the block you look at (28 blocks) and reels you in. Press again to let go.'],
		stretch_climb: ['Stretch Climb', 'Stretch straight up the wall in front of you to its top (12 blocks at most). With no wall, a tall stretch upwards.'],
		root_wall: ['Root Wall', 'A wall of roots bursts up in front of you for 8 s: creatures are pushed back from it and projectiles stick in it.'],
		regrowth: ['Regrowth', '10 s of healing for you and your squad nearby -- 2 health a second in sunlight, a trickle in the shade.'],
		overgrowth: ['Overgrowth', 'Vines overrun the ground around you (7 blocks) for 8 s and keep entangling every creature in it.'],
		root_eruption: ['Root Eruption', 'Roots burst out of the ground in a line ahead (7 damage, launches what they hit).'],
		vine_lash: ['Vine Lash', 'Your stretchy vine arms reach 1.5 blocks further.'],
		bonemeal_touch: ['Bonemeal Touch', 'Use a block with an empty hand: it grows as if bonemealed.'],
		bush_hide: ['Bush Hide', 'Sneak and hold still: you become a bush, and whatever was hunting you loses track of you.'],
	},
	ripjaws: {
		bite: ['Bite', 'A huge chomp on the creature in front of you (7 damage) that heals you a little.'],
		jaw_lunge: ['Jaw Lunge', 'Lunge jaws-first: the first creature in the way takes 10.'],
		tail_slam: ['Tail Slam', 'Your tail sweeps a ring of force around you (8 damage, knockback; wider in water).'],
		fin_slash: ['Fin Slash', 'A spin of razor fins (6 damage around you, slows).'],
		water_dash: ['Water Dash', 'A burst through the water where you look. On land: a quick hop.'],
		breach: ['Breach', 'Leap clean out of the water. On land: a back-flip away.'],
		whirlpool: ['Whirlpool / Mud Splash', 'In water: a 5 s whirlpool ahead that drags creatures in, spins them and drowns them. On land: a spray of mud that blinds and slows.'],
		tidal_roar: ['Tidal Roar', 'A roar that weakens, slows and shoves back everything around you.'],
		feeding_frenzy: ['Feeding Frenzy', '10 s of frenzy: +30% damage, faster, and every bite heals 3.'],
		drench: ['Drench', 'Splash water over yourself: half your hydration back, fire out.'],
		jaw_punch: ['Jaw Punch', 'Heavy claws -- +3 damage in water.'],
		dig: ['Dig', 'Use on sand, gravel, dirt, clay, mud or snow: you dig straight through it, one block per use.'],
	},
	grey_matter: {
		nerve_pinch: ['Nerve Pinch', 'Pinch a nerve of the creature right in front of you: stunned for 3 s (it forgets what it was doing).'],
		weak_spot_strike: ['Weak-Spot Strike', 'One precise blow to its weak spot (12 damage).'],
		analyze: ['Analyze', 'Reads the creature you look at (24 blocks): health, armour and its weakness. It glows and takes 35% more damage from everyone for 10 s.'],
		decoy: ['Decoy', 'Tosses a ticking gadget: for 5 s mobs forget you and go after it.'],
		big_leap: ['Big Leap', 'A huge leap for something so small (no fall damage from it).'],
		scurry: ['Scurry', '5 s at top speed; whatever was hunting you loses you.'],
		snare_trap: ['Snare Trap', 'Quick-craft a tripwire snare on the ground just ahead: the first creature over it is caught for 4 s. Up to three at a time; they last a minute.'],
		smoke_bomb: ['Smoke Bomb', 'A puff of smoke: everything near you is blinded and loses track of you.'],
		genius_repair: ['Genius Repair', 'Fix the block you look at (anvils, cracked bricks) or give a furnace or brewing stand 10 s of work at once; otherwise repair the gear in your hands by 30%.'],
		mind_map: ['Mind Map', 'Ores, chests, barrels and spawners within 20 blocks outlined through the walls for 8 s.'],
		precision_jab: ['Precision Jab', 'Your punches hit an analysed creature for 2 more.'],
		quick_read: ['Quick Read', 'Use on a creature: its health at a glance.'],
	},
};

const L = {
	'projecthero.guide.omnitrix.alien.stinkfly': 'A Lepidopterran: a green insect with four eyes on stalks, translucent wings and a stinger. He really flies, never takes fall damage, and fights with goo and gas -- but has only 14 health.',
	'projecthero.guide.omnitrix.alien.wildvine': 'A Florauna: a walking plant with a pink flower for a head. Vines that grapple, bind and entangle, seed bombs, healing in the sun -- and he can hide as a bush. Fire hurts him more.',
	'projecthero.guide.omnitrix.alien.ripjaws': 'A Piscciss Volann: a fish-man with a huge toothy jaw. In water he is fast, never drowns and hits half again as hard. On land he dries out: after 2 minutes he starts to choke (the meter over his keys) until he gets wet again -- water refills it in 15 s, rain stops it.',
	'projecthero.guide.omnitrix.alien.grey_matter': 'A Galvan: a tiny grey genius a third of your size -- he fits through one-block gaps and mobs keep losing sight of him, but he has only 8 health. Pressure points, gadgets, repairs and a mind that maps everything around him.',
	'message.projecthero.omnitrix.wildvine.no_target': 'No creature in reach of your vines.',
	'message.projecthero.omnitrix.wildvine.no_anchor': 'Nothing to grab onto there.',
	'message.projecthero.omnitrix.wildvine.hidden': 'You are a bush. Nothing to see here.',
	'message.projecthero.omnitrix.wildvine.shade': 'No sun: Regrowth only trickles in the shade.',
	'message.projecthero.omnitrix.ripjaws.drying': 'You are drying out -- get to water!',
	'message.projecthero.omnitrix.ripjaws.choking': 'You are choking on the air! Find water or rain.',
	'message.projecthero.omnitrix.grey_matter.no_target': 'No creature close enough.',
	'message.projecthero.omnitrix.grey_matter.analysis': 'Analysis -- %s: %s / %s health, %s armour. Weakness: %s',
	'message.projecthero.omnitrix.grey_matter.marked': 'Weak points marked: it takes 35% more damage for 10 s.',
	'message.projecthero.omnitrix.grey_matter.read': '%s: %s / %s health',
	'message.projecthero.omnitrix.grey_matter.nothing_to_fix': 'Nothing here to fix or tune up.',
	'message.projecthero.omnitrix.grey_matter.mind_map': 'Mind Map: %s things worth finding.',
	'message.projecthero.omnitrix.grey_matter.weak.water': 'water and rain.',
	'message.projecthero.omnitrix.grey_matter.weak.undead': 'Smite, healing potions and daylight.',
	'message.projecthero.omnitrix.grey_matter.weak.arthropod': 'Bane of Arthropods.',
	'message.projecthero.omnitrix.grey_matter.weak.aquatic': 'Impaling, and being out of the water.',
	'message.projecthero.omnitrix.grey_matter.weak.fireproof': 'not fire -- use water, snow or plain force.',
	'message.projecthero.omnitrix.grey_matter.weak.illager': 'it fights in a crowd: thin the group first.',
	'message.projecthero.omnitrix.grey_matter.weak.boss': 'too big to stun for long -- mark it and let the team hit it.',
	'message.projecthero.omnitrix.grey_matter.weak.general': 'nothing special: strike the weak spot.',
	'hud.projecthero.omnitrix.ripjaws.hydration': 'Hydration: %s s',
	'hud.projecthero.omnitrix.ripjaws.wet': 'Hydration: refilling',
	'hud.projecthero.omnitrix.ripjaws.choking': 'DRYING OUT!',
	// v0.17.1 polish: the right-side HUD's meter rows
	'hud.projecthero.omnitrix.ripjaws.meter': 'Hydration',
	'hud.projecthero.omnitrix.title': 'Omnitrix',
	'hud.projecthero.omnitrix.meter.time': 'Time',
	'hud.projecthero.omnitrix.meter.recharge': 'Recharge',
};
for (const [alien, moves] of Object.entries(MOVES)) {
	for (const [id, [name, desc]] of Object.entries(moves)) {
		L['projecthero.omnitrix.' + alien + '.' + id] = name;
		L['projecthero.omnitrix.' + alien + '.' + id + '.desc'] = desc;
	}
}
const SOUNDS = {
	stinkfly_buzz: 'Wings buzz', stinkfly_spit: 'Goo spat', stinkfly_splat: 'Goo splats', stinkfly_gas: 'Toxic gas hisses', stinkfly_sting: 'Stinger stabs',
	stinkfly_dive: 'Stinkfly dives', wildvine_vine: 'Vine lashes', wildvine_seed: 'Seed pops', wildvine_burst: 'Seed bomb bursts', wildvine_roots: 'Roots erupt',
	wildvine_bloom: 'Wildvine blooms', wildvine_rustle: 'Leaves rustle', ripjaws_bite: 'Jaws snap', ripjaws_roar: 'Ripjaws roars', ripjaws_splash: 'Water splashes',
	ripjaws_slam: 'Tail slams', ripjaws_gasp: 'Ripjaws gasps', ripjaws_whirl: 'Whirlpool churns', grey_matter_pinch: 'Nerve pinched', grey_matter_scan: 'Grey Matter analyses',
	grey_matter_gadget: 'Gadget clicks', grey_matter_leap: 'Grey Matter leaps', grey_matter_repair: 'Gadgets whirr', grey_matter_think: 'Grey Matter thinks',
};
for (const [k, v] of Object.entries(SOUNDS)) L['subtitles.projecthero.' + k] = v;

// ---------------------------------------------------------------- en_us.json: the block at the end
// work in LF, write back in the file's own line endings (a CRLF checkout must not duplicate the block)
let raw = fs.readFileSync(LANG, 'utf8');
const EOL = raw.includes('\r\n') ? '\r\n' : '\n';
let s = raw.replace(/\r\n/g, '\n');
const START = '  "_comment_omnitrix_aliens_b_start": "v0.17.1 Omnitrix: Stinkfly, Wildvine, Ripjaws, Grey Matter",\n';
const END_KEY = '"_comment_omnitrix_aliens_b_end": ""';
for (let i = s.indexOf(START); i >= 0; i = s.indexOf(START)) {
	const j = s.indexOf(END_KEY, i);
	const lineEnd = s.indexOf('\n', j);
	s = s.slice(0, i) + s.slice(lineEnd + 1);
	// the block was last: the line before it needs no trailing comma
	s = s.replace(/,(\s*)\}\s*$/, '$1}\n');
}
let block = START;
for (const [k, v] of Object.entries(L)) block += '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + ',\n';
block += '  ' + END_KEY + '\n';
s = s.replace(/\s*\}\s*$/, ',\n' + block + '}\n');
const parsed = JSON.parse(s);
fs.writeFileSync(LANG, s.replace(/\n/g, EOL));

// ---------------------------------------------------------------- sounds.json: the block at the end
const rawJ = fs.readFileSync(SOUNDS_JSON, 'utf8');
const EOLJ = rawJ.includes('\r\n') ? '\r\n' : '\n';
let j = rawJ.replace(/\r\n/g, '\n');
const SND_START = '  "stinkfly_buzz": {';
const k0 = j.indexOf(SND_START);
if (k0 >= 0) {
	j = j.slice(0, k0).replace(/,\s*$/, '\n') + '}\n';
}
let sb = '';
for (const name of Object.keys(SOUNDS)) {
	sb += '  ' + JSON.stringify(name) + ': {\n    "subtitle": "subtitles.projecthero.' + name + '",\n    "sounds": [\n      {\n        "name": "projecthero:omnitrix/' + name + '"\n      }\n    ]\n  },\n';
}
sb = sb.replace(/,\n$/, '\n');
j = j.replace(/\s*\}\s*$/, ',\n' + sb + '}\n');
JSON.parse(j);
fs.writeFileSync(SOUNDS_JSON, j.replace(/\n/g, EOLJ));
console.log('lang lines', Object.keys(L).length, 'total keys', Object.keys(parsed).length, '; sounds', Object.keys(SOUNDS).length);
