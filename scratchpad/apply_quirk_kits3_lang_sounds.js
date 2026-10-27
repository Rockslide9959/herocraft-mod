// v0.17.1 Quirk revamp wave 3 (Overhaul, Dark Shadow, Engine): the lang text and sounds.json entries, applied as text edits so
// the rest of each file (its escapes, its order) is untouched. Changed keys are rewritten in place; new keys go in ONE
// contiguous block per Quirk (after that Quirk's last existing ability line); the sounds go in one block at the end of
// sounds.json. Idempotent (a key already present is updated, never duplicated).
//   node scratchpad/apply_quirk_kits3_lang_sounds.js
const fs = require('fs');
const path = require('path');
const ROOT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero');
const LANG = path.join(ROOT, 'lang', 'en_us.json');
const SOUNDS = path.join(ROOT, 'sounds.json');

// ---------------------------------------------------------------- the text
const UPDATE = {
	// ---- Overhaul
	'projecthero.quirk.meter.focus': 'Matter',
	'projecthero.quirk.ultimate.overhaul.desc': 'Both palms on the ground: for 16 blocks the ground breaks apart and rises as a sea of spikes rolling out in three waves (12 each and a toss), then reassembles perfectly behind them. Fused and unmovable while it lasts; it spends all your Matter.',
	'projecthero.guide.quirk.overhaul.bands': 'Starved (under 25): what you build comes out small. Stocked: normal. Overflowing (80 and up): spikes and walls are 1.5x the size.',
	'projecthero.guide.quirk.overhaul.body': 'Take apart and put back anything you touch: burst enemies into fragments, make the ground erupt in jagged spikes, walls and pits, reassemble the wounded and broken tools, rebuild blasted ground and fuse with the earth into a spiked stone giant.',
	'projecthero.guide.quirk.overhaul.meter.body': 'Matter is what you build with. Your feet draw it from the ground: 10 a second standing on stone or earth, 3 on wood or in the air (half while fused). Spikes, walls, Fusion and Mend spend it; Disassemble (+5), Shrapnel Burst (+8), Disassemble Terrain (+10) and every kill (+15) gain it.',
	'projecthero.guide.quirk.overhaul.overload': 'Out of Matter',
	'projecthero.guide.quirk.overhaul.overload.body': 'A move you can not afford simply does not happen. Stand on stone or earth to refill fast.',
	'projecthero.guide.quirk.overhaul.passive': 'The gloves, and survival',
	'projecthero.guide.quirk.overhaul.passive.body': 'Your gloves stay on until a move tugs them off for 8 s; bare-handed punches then disassemble too (+4). Germophobe: after a mob hits you, your next move within 3 s hits 20% harder. Sneak + V with a damaged tool or armour piece in your off hand mends it (+100 durability, 20 Matter). Sneak + a bare-hand punch on stone or earth takes a 3x3 of it apart into its drops (6 Matter, where you may build). The full costume\'s mask keeps out poison. Walls, pits and lifted ground always go back.',
	'projecthero.quirk.overhaul.intro': 'Touch to take apart, touch to put back together. Your feet draw Matter from the ground; spikes and walls spend it, disassembling gains it. The gloves come off on their own.',
	'projecthero.quirk.ability.quirk_overhaul_disassemble.desc': 'The glove comes off: a two-finger touch, 18 damage as the body cracks and bursts apart. A body it kills bursts on everything near it (6). Gains 5 Matter. 1 s cooldown.',
	'projecthero.quirk.ability.quirk_overhaul_disarm.desc': 'A quick wrist grab: what a mob holds (or, empty-handed, its armour) comes apart for good. A player\'s is knocked from their grip instead. Not bosses. Gains 4 Matter.',
	'projecthero.quirk.ability.quirk_overhaul_spike_wave.desc': 'Palm slapped to the floor: a jagged line of stone spikes erupts toward the target, 14 blocks: 10 damage and a toss. 12 Matter. Hold G instead for the Thorn Forest.',
	'projecthero.quirk.ability.quirk_overhaul_spike_field.desc': 'Both palms down: two rings of spikes erupt around you, 9 damage to all within 5 blocks. 20 Matter.',
	'projecthero.quirk.ability.quirk_overhaul_surf.desc': 'Hold X: the ground under you breaks and reforms a step ahead, and you ride the wave the way you look for as long as you hold it, stepping up 2-block rises and throwing aside what it runs over (5). Let go to stop. No fall damage. 6 Matter, then 8 a second.',
	'projecthero.quirk.ability.quirk_overhaul_pillar.desc': 'A stone pillar shoots up under you and flings you high. No fall damage for 5 s. 10 Matter, 1 s cooldown.',
	'projecthero.quirk.ability.quirk_overhaul_strike.desc': 'A low dash, bare hand out: the first enemy you touch takes 14 and is thrown. Free; a touch that lands gains 3 Matter.',
	'projecthero.quirk.ability.quirk_overhaul_pit.desc': 'The floor breaks out from under everything near where you aim: each drops into a pit that closes over it, then opens again 4 s later. Not bosses. Gains 10 Matter. 5 s cooldown.',
	'projecthero.quirk.ability.quirk_overhaul_wall.desc': 'The ground ahead bursts up into a 5x3 stone wall for 10 s (7x4 Overflowing, smaller Starved), assembling out of flying fragments and throwing back whatever stood there (5). 16 Matter.',
	'projecthero.quirk.ability.quirk_overhaul_fusion.desc': 'Absorb the ground for 20 s: bigger, stone-plated, spiked limbs and two extra arms. +8 damage, +12 armour, +2.5 reach. Again: end. Matter refills at half speed while fused. 45 Matter, 30 s cooldown after it ends. With a damaged tool in your off hand: Mend instead.',
	'projecthero.quirk.ability.quirk_overhaul_reassemble.desc': 'Put back what is broken: you (or the ally you look at) taken apart and reassembled to full health, debuffs gone. Saving an ally near death feeds Plus Ultra. 30 Matter, 45 s cooldown. Hold V instead for Restore.',
	'message.projecthero.overhaul.gloved': 'Your gloves are on.',
	// ---- Dark Shadow
	'projecthero.quirk.ultimate.dark_shadow.desc': 'Dark Shadow fully merges with you: a towering winged black-bird armour (1.6x, glowing eyes) for 12 s of fast flight; every attack key is a claw dive (14 to everything it passes). Shadow is held at Ravenous and can\'t go berserk. Afterwards it is spent and hides for 5 s.',
	'projecthero.guide.quirk.dark_shadow.bands': 'Restrained (under 30): x0.6. Hunting: x1.0. Ravenous (75 and up): x1.4, and its hits heal you. At 100 it goes berserk.',
	'projecthero.guide.quirk.dark_shadow.meter.body': 'Shadow climbs slowly in the pitch dark (light 3 or less), more slowly still in dim light (to 85 at most) and falls fast in bright light. Every move pushes it up a little. Its strength also follows the light at it: x0.55 in daylight, x1.7 in the pitch dark.',
	'projecthero.guide.quirk.dark_shadow.overload.body': 'At 100 Shadow it goes berserk: huge, spiked and red-eyed, it attacks everything near you, you included (never below 1 heart), until light falls on it. A light source in your hand, daylight or the Light Flare (Shift+V, which still works) calms it. Unleash (Shift+C) is the controlled version.',
	'projecthero.guide.quirk.dark_shadow.passive.body': 'A torch or other light source held in either hand counts as light on it. In Black Ankh it is close to you, its Shadow rises half as fast and you see in the dark (Night owl). Shadow catch: fall more than 6 blocks with Shadow above 30 and it catches you (no fall damage, once every 20 s). Shadow Scout marks creatures through walls. Tokoyami\'s full cloak hides it from the light: it feels two light levels darker.',
	'projecthero.guide.quirk.dark_shadow.hide': 'N hides Dark Shadow: it tucks back into your body and stays gone until you press N again. While hidden no move works except the Light Flare (Shift+V), Shadow drains and it can\'t go berserk. A berserk shadow won\'t hide.',
	'projecthero.quirk.dark_shadow.intro': 'A living shadow at your back: small in the light, fierce in the dark. N hides it. Full Shadow in the dark and it goes Berserk until light calms it.',
	'projecthero.quirk.ability.quirk_dark_shadow_strike.desc': 'You point; it lunges out 5 to 10 blocks (further in the dark) and rakes the target for 8 x strength. In Black Ankh: a clawed dash instead.',
	'projecthero.quirk.ability.quirk_dark_shadow_grab.desc': 'It wraps a creature 6 to 12 blocks away in shadow, hoists it and slams it down: 9 x strength, splashing those nearby. Keep G held after the grab for Shadow Toss: it carries the creature overhead and throws it along your aim when you let go (12, and the impact splashes). Bosses are only struck.',
	'projecthero.quirk.ability.quirk_dark_shadow_black_ankh.desc': 'Toggle: it coats you as armour with a bird-skull helm and claws. +4 to +10 armour, +3 toughness, +1 reach, 3 x strength attack damage, night vision. Shadow rises half as fast.',
	'projecthero.quirk.ability.quirk_dark_shadow_calm.desc': 'Needs a light source in hand or daylight: a flash that blinds mobs in 8 blocks, ends Berserk and drops Shadow to 15. Works while berserk or hidden.',
	'projecthero.quirk.ability.quirk_dark_shadow_dark_fang.desc': 'It lunges at a target 4 to 8 blocks away and bites 4 times, 4 x strength each; the last bite flings it.',
	'projecthero.quirk.ability.quirk_dark_shadow_abyssal_ankh': 'Black Fallen Angel',
	'projecthero.quirk.ability.quirk_dark_shadow_abyssal_ankh.desc': 'Hold X: Black Ankh plus great shadow wings while you hold it. Glide like an elytra; in the dark the wings drive you on. No fall damage.',
	'projecthero.quirk.ability.quirk_dark_shadow_scout.desc': 'It flies out 12 to 28 blocks along your look and marks every creature near its path for 10 s, through walls: a violet eye over each, and marked creatures take 15% more from you.',
	'projecthero.quirk.ability.quirk_dark_shadow_recall.desc': 'N: it tucks away into your body and is not seen. No moves except the Light Flare; Shadow drains and it can\'t go berserk. N again calls it back out.',
	'projecthero.quirk.ability.quirk_dark_shadow_berserk': 'Unleash',
	'projecthero.quirk.ability.quirk_dark_shadow_berserk.desc': 'Darkness only: you let it loose under control. 8 s of Berserk that never touches you or your squad, then Shadow drops to 40. Light still ends it early.',
	// ---- Engine
	'projecthero.quirk.ultimate.engine.desc': 'Hold C: a one-second rev in a sprinter\'s crouch, the nozzles white-hot. Then 6 s at double the Turbo cap: the first kick key chains you from target to target within 10 blocks (up to six kicks, 12 + 0.4 per b/s each) and ends with a mach kick (35). Then a 30 s stall.',
	'projecthero.guide.quirk.engine.bands': 'Cold (under 30): momentum builds 10% slower. Warm: normal. Redline (80 and up): +15% damage and red exhaust. At 100: Stall.',
	'projecthero.guide.quirk.engine.passive.body': 'Step assist always. The run carries through jumps. In gear: sharp turns slide, and at Gear 2+ you bowl over mobs you run into. While any gear is engaged you take 60% less fall damage. At Gear 3 with 70% momentum you run across water. Sprint into a wall in gear and you run up it (4 blocks). Sneak + use (empty hand) on an ally, a pet or a villager carries them on your back; again to set them down.',
	'projecthero.quirk.ability.quirk_engine_recipro_kick.desc': 'One engine-driven kick: 5 standing, up to 28 the faster you run. Hold R up to 1 s first for the Charged Kick: up to 1.5x and a launch.',
	'projecthero.quirk.ability.quirk_engine_recipro_dash.desc': 'One burst dash exactly where you are aiming (look up to dash up, down to dive): 7 blocks, +2 a gear. In Recipro Turbo: the Turbo Strike, a 28-block rush that hits everything in the path. Hold X while running for the Engine Brake: a drifting skid that keeps 85% of your momentum.',
};

const NEW = {
	overhaul: {
		anchor: 'projecthero.quirk.ability.quirk_overhaul_reassemble.desc',
		keys: {
			'projecthero.quirk.ability.quirk_overhaul_thorn_forest': 'Thorn Forest',
			'projecthero.quirk.ability.quirk_overhaul_thorn_forest.desc': 'Hold G: kneel with both palms pressed to the ground and aim a crack marker; let go and a 7x7 forest of 6-block spikes rises there in waves (16 and an impale), then stands 4 s as cover: what walks in is pushed out, arrows break on it. 40 Matter, 10 s cooldown.',
			'projecthero.quirk.ability.quirk_overhaul_ground_shift': 'Ground Shift',
			'projecthero.quirk.ability.quirk_overhaul_ground_shift.desc': 'The floor itself slides you 8 blocks the way you look in a rippling wave; you stand still and are carried. 6 Matter. Hold X instead for Terrain Surf (rides while held, 8 Matter a second).',
			'projecthero.quirk.ability.quirk_overhaul_shrapnel': 'Shrapnel Burst',
			'projecthero.quirk.ability.quirk_overhaul_shrapnel.desc': 'A palm swept upward: the ground ahead comes apart and flies as a cone of stone shards, 8 blocks. 3 a shard, five shards on a body point blank (15), fewer further out. Gains 8 Matter.',
			'projecthero.quirk.ability.quirk_overhaul_restore': 'Restore',
			'projecthero.quirk.ability.quirk_overhaul_restore.desc': 'Hold V for a second: every block an explosion broke within 12 blocks in the last 5 minutes rebuilds as a wave, its debris drawn back in. 10 s cooldown.',
			'projecthero.quirk.ability.quirk_overhaul_mend': 'Mend',
			'projecthero.quirk.ability.quirk_overhaul_mend.desc': 'Shift+V with a damaged tool or armour piece in your off hand: it comes apart and goes back together, +100 durability. 20 Matter.',
			'message.projecthero.overhaul.matter': 'Not enough Matter (%s needed).',
			'message.projecthero.overhaul.nothing_to_mend': 'Hold a damaged tool or armour piece in your off hand to mend it.',
			'message.projecthero.overhaul.mended': 'Mended: %s.',
			'message.projecthero.overhaul.nothing_broken': 'Nothing broken here to restore.',
			'message.projecthero.overhaul.ultimate_spent': 'Every scrap of Matter went into it.',
			'projecthero.quirk.combo.overhaul.buried_alive': 'Buried Alive',
			'projecthero.quirk.combo.overhaul.buried_alive.desc': 'Disassemble Terrain, then Ground Spikes within 2 s: the spikes hit 1.5x harder through the pits.',
			'projecthero.guide.quirk.overhaul.holds': 'Hold moves',
			'projecthero.guide.quirk.overhaul.holds.body': 'Hold G: Thorn Forest. Hold X: Terrain Surf. Hold V: Restore. Hold C with a full Plus Ultra gauge: Total Disassembly. A quick tap is still the tap move.',
			'subtitles.projecthero.quirk_overhaul_disassemble': 'Matter crackles apart',
			'subtitles.projecthero.quirk_overhaul_spike': 'Stone spikes erupt',
			'subtitles.projecthero.quirk_overhaul_reassemble': 'Matter snaps back together',
		},
	},
	dark_shadow: {
		anchor: 'projecthero.quirk.ability.quirk_dark_shadow_berserk.desc',
		keys: {
			'projecthero.quirk.ability.quirk_dark_shadow_abyssal_rampage': 'Abyssal Rampage',
			'projecthero.quirk.ability.quirk_dark_shadow_abyssal_rampage.desc': 'You brace; it rears up over your head and swings three great claw arcs in front of you: 7, 7, then an overhead 11 x strength, 5 blocks.',
			'projecthero.quirk.ability.quirk_dark_shadow_lunge': 'Shadow Lunge',
			'projecthero.quirk.ability.quirk_dark_shadow_lunge.desc': 'It seizes a point 10 blocks ahead and yanks you there. Hold X instead for Black Fallen Angel.',
			'projecthero.quirk.ability.quirk_dark_shadow_claw_dive': 'Claw Dive',
			'projecthero.quirk.ability.quirk_dark_shadow_claw_dive.desc': 'In Abyssal Black Body: a dive along your look that rakes everything it passes, 14 each.',
			'projecthero.quirk.ability.quirk_dark_shadow_catch': 'Shadow Catch',
			'message.projecthero.dark_shadow.no_room': 'No room for Dark Shadow to pull you.',
			'message.projecthero.dark_shadow.spent': 'Dark Shadow is spent and hides for a moment.',
			'message.projecthero.dark_shadow.caught': 'Dark Shadow caught you.',
			'hud.projecthero.dark_shadow.unleashed': 'UNLEASHED: under control',
			'hud.projecthero.dark_shadow.state.blackbody': 'Abyssal Black Body %ss',
			'hud.projecthero.dark_shadow.state.spent': 'Spent: hidden %ss',
			'hud.projecthero.dark_shadow.state.unleash': 'Unleashed %ss',
			'projecthero.quirk.combo.dark_shadow.ravening_feast': 'Ravening Feast',
			'projecthero.quirk.combo.dark_shadow.ravening_feast.desc': 'Dark Fang, then Abyssal Rampage within 2 s: all three swings hit 1.3x harder.',
			'projecthero.guide.quirk.dark_shadow.holds': 'Hold moves',
			'projecthero.guide.quirk.dark_shadow.holds.body': 'Keep G held after Shadow Grab: Shadow Toss. Hold X: Black Fallen Angel (the wings, while held). Hold C with a full Plus Ultra gauge: Abyssal Black Body. A quick tap is still the tap move.',
			'subtitles.projecthero.quirk_dark_shadow_growl': 'Dark Shadow growls',
			'subtitles.projecthero.quirk_dark_shadow_whoosh': 'Shadow sweeps past',
			'subtitles.projecthero.quirk_dark_shadow_roar': 'Dark Shadow roars',
		},
	},
	engine: {
		anchor: 'projecthero.quirk.ability.quirk_engine_finisher.desc',
		keys: {
			'hud.projecthero.quirk.engine.burst': 'RECIPRO BURST %ss',
			'message.projecthero.quirk.engine.burst': 'Recipro Burst: Extend!',
			'message.projecthero.quirk.engine.carry': 'Carrying %s.',
			'message.projecthero.quirk.engine.no_target': 'Nobody in reach to chain.',
			'projecthero.quirk.combo.engine.sky_splitter': 'Sky Splitter',
			'projecthero.quirk.combo.engine.sky_splitter.desc': 'Rising Kick, then the Axe Kick within 2 s: the heel brings it down 1.5x harder.',
			'projecthero.guide.quirk.engine.holds': 'Hold moves',
			'projecthero.guide.quirk.engine.holds.body': 'Hold R up to 1 s: the Charged Kick. Hold X while running: the Engine Brake. Hold C with a full Plus Ultra gauge: Recipro Burst: Extend. A quick tap is still the tap move; gear shifts have no cooldown.',
			'subtitles.projecthero.quirk_engine_rev': 'Calf engines rev',
			'subtitles.projecthero.quirk_engine_burst': 'Recipro Burst ignites',
		},
	},
};

const SOUND_EVENTS = {
	quirk_overhaul_disassemble: ['quirk/overhaul_disassemble1', 'quirk/overhaul_disassemble2'],
	quirk_overhaul_spike: ['quirk/overhaul_spike1', 'quirk/overhaul_spike2'],
	quirk_overhaul_reassemble: ['quirk/overhaul_reassemble'],
	quirk_dark_shadow_growl: ['quirk/dark_shadow_growl1', 'quirk/dark_shadow_growl2'],
	quirk_dark_shadow_whoosh: ['quirk/dark_shadow_whoosh1', 'quirk/dark_shadow_whoosh2'],
	quirk_dark_shadow_roar: ['quirk/dark_shadow_roar'],
	quirk_engine_rev: ['quirk/engine_rev'],
	quirk_engine_burst: ['quirk/engine_burst'],
};

// ---------------------------------------------------------------- the lang file (text edits)
let lang = fs.readFileSync(LANG, 'utf8');
const nl = lang.includes('\r\n') ? '\r\n' : '\n';
let lines = lang.split(nl);
const keyOf = l => {
	const m = /^\s*"([^"]+)":/.exec(l);
	return m ? m[1] : null;
};
const lineFor = (k, v, last) => '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + (last ? '' : ',');
let updated = 0, added = 0;
for (let i = 0; i < lines.length; i++) {
	const k = keyOf(lines[i]);
	if (k && Object.prototype.hasOwnProperty.call(UPDATE, k)) {
		const last = !lines[i].trimEnd().endsWith(',');
		lines[i] = lineFor(k, UPDATE[k], last);
		updated++;
	}
}
for (const [q, block] of Object.entries(NEW)) {
	const present = new Set(lines.map(keyOf).filter(Boolean));
	const fresh = Object.entries(block.keys).filter(([k]) => !present.has(k));
	// keys that exist already: update in place
	for (let i = 0; i < lines.length; i++) {
		const k = keyOf(lines[i]);
		if (k && Object.prototype.hasOwnProperty.call(block.keys, k)) {
			const last = !lines[i].trimEnd().endsWith(',');
			lines[i] = lineFor(k, block.keys[k], last);
		}
	}
	const at = lines.findIndex(l => keyOf(l) === block.anchor);
	if (at < 0) {
		throw new Error('anchor not found for ' + q + ': ' + block.anchor);
	}
	if (!lines[at].trimEnd().endsWith(',')) {
		throw new Error('anchor is the last key: ' + block.anchor);
	}
	lines.splice(at + 1, 0, ...fresh.map(([k, v]) => lineFor(k, v, false)));
	added += fresh.length;
}
fs.writeFileSync(LANG, lines.join(nl));
JSON.parse(fs.readFileSync(LANG, 'utf8'));
console.log('lang: updated', updated, 'added', added);

// ---------------------------------------------------------------- sounds.json (one block at the end)
let snd = fs.readFileSync(SOUNDS, 'utf8');
const snl = snd.includes('\r\n') ? '\r\n' : '\n';
const parsed = JSON.parse(snd);
const missing = Object.keys(SOUND_EVENTS).filter(k => !parsed[k]);
if (missing.length) {
	const body = missing.map(k => {
		const sounds = SOUND_EVENTS[k].map(n => '      {' + snl + '        "name": "projecthero:' + n + '"' + snl + '      }').join(',' + snl);
		return '  "' + k + '": {' + snl + '    "subtitle": "subtitles.projecthero.' + k + '",' + snl + '    "sounds": [' + snl + sounds + snl + '    ]' + snl + '  }';
	}).join(',' + snl);
	const end = snd.lastIndexOf('}');
	let head = snd.slice(0, end).replace(/\s+$/, '');
	snd = head + ',' + snl + body + snl + '}' + snd.slice(end + 1);
	fs.writeFileSync(SOUNDS, snd);
	JSON.parse(snd);
}
console.log('sounds: added', missing.length);
