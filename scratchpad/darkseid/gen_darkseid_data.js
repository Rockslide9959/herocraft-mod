// v0.13.18: Darkseid Raid + random-power serum data, lang and serum textures.
// Run from the repo root:  node scratchpad/darkseid/gen_darkseid_data.js
// Fails if any translation key used by the new Java code is missing from en_us.json.
const fs = require('fs');
const path = require('path');
const { encode } = require('../pngkit');

const REPO = path.join(__dirname, '..', '..');
const RES = path.join(REPO, 'src/main/resources/');
const DATA = RES + 'data/projecthero/';
const ASSETS = RES + 'assets/projecthero/';
const w = (rel, obj) => { fs.mkdirSync(path.dirname(rel), { recursive: true }); fs.writeFileSync(rel, JSON.stringify(obj, null, 2) + '\n'); };

// ---------------------------------------------------------------- damage type
w(DATA + 'damage_type/omega_beam.json', { message_id: 'projecthero.omega_beam', scaling: 'when_caused_by_living_non_player', exhaustion: 0.1 });

// ---------------------------------------------------------------- advancements
const adv = (id, icon, frame, parent, extra) => Object.assign({
	display: {
		icon: { id: icon },
		title: { translate: 'advancement.projecthero.darkseid.' + id + '.title' },
		description: { translate: 'advancement.projecthero.darkseid.' + id + '.description' },
		frame, show_toast: true, announce_to_chat: true, hidden: false,
	},
	criteria: { code_trigger: { trigger: 'minecraft:impossible' } },
	requirements: [['code_trigger']],
}, parent ? { parent } : {}, extra || {});
const antiLife = adv('anti_life', 'projecthero:omega_core', 'challenge', null);
antiLife.display.background = 'minecraft:textures/block/blackstone.png';
w(DATA + 'advancement/darkseid/anti_life.json', antiLife);
w(DATA + 'advancement/darkseid/apokolips_falls.json', adv('apokolips_falls', 'projecthero:mother_box', 'challenge', 'projecthero:darkseid/anti_life'));

// ---------------------------------------------------------------- recipes
const shaped = (id, pattern, key, count = 1) => w(DATA + 'recipe/' + id + '.json', {
	type: 'minecraft:crafting_shaped', category: 'misc', pattern,
	key: Object.fromEntries(Object.entries(key).map(([k, v]) => [k, { item: v }])),
	result: { id: 'projecthero:' + (id.replace(/_from_shards$/, '')), count },
});
const shapeless = (id, items) => w(DATA + 'recipe/' + id + '.json', {
	type: 'minecraft:crafting_shapeless', category: 'misc', ingredients: items.map(i => ({ item: i })), result: { id: 'projecthero:' + id, count: 1 },
});
shaped('boom_tube_beacon', ['ETE', 'TNT', 'CCC'], { E: 'minecraft:echo_shard', T: 'projecthero:supervillain_token', N: 'minecraft:nether_star', C: 'minecraft:crying_obsidian' });
shaped('boom_tube_beacon_from_shards', ['CSC', 'SNS', 'CSC'], { C: 'minecraft:crying_obsidian', S: 'projecthero:omega_shard', N: 'minecraft:nether_star' });
shaped('mother_box', ['SXS', 'XCX', 'SXS'], { S: 'projecthero:omega_shard', X: 'minecraft:netherite_scrap', C: 'projecthero:omega_core' });
shapeless('mutagenic_serum', ['minecraft:glass_bottle', 'projecthero:grave_essence', 'projecthero:grave_essence', 'minecraft:diamond', 'minecraft:diamond', 'minecraft:ghast_tear', 'minecraft:fermented_spider_eye']);
shapeless('heroic_serum', ['minecraft:glass_bottle', 'minecraft:nether_star', 'projecthero:supervillain_token', 'minecraft:diamond_block', 'minecraft:golden_apple']);
shapeless('prismatic_serum', ['minecraft:glass_bottle', 'projecthero:grave_essence', 'projecthero:supervillain_token', 'minecraft:diamond', 'minecraft:diamond', 'minecraft:ghast_tear', 'minecraft:echo_shard']);

// ---------------------------------------------------------------- loot tables
w(DATA + 'loot_table/entities/parademon.json', {
	type: 'minecraft:entity',
	pools: [
		{ rolls: 1.0, entries: [{ type: 'minecraft:item', name: 'minecraft:iron_nugget', functions: [{ function: 'minecraft:set_count', count: { type: 'minecraft:uniform', min: 0, max: 3 } }] }] },
		{ rolls: 1.0, entries: [{ type: 'minecraft:item', name: 'minecraft:gunpowder', functions: [{ function: 'minecraft:set_count', count: { type: 'minecraft:uniform', min: 0, max: 1 } }] }] },
		{ rolls: 1.0, conditions: [{ condition: 'minecraft:killed_by_player' }, { condition: 'minecraft:random_chance', chance: 0.02 }],
			entries: [{ type: 'minecraft:item', name: 'projecthero:omega_shard' }] },
	],
});
w(DATA + 'loot_table/entities/darkseid.json', { type: 'minecraft:entity', pools: [] }); // his loot is the raid's reward, per participant

// ---------------------------------------------------------------- serum textures (16x16 bottles)
function bottle(name, liquidTop, liquidBottom, sparkle) {
	const W = 16, px = Buffer.alloc(W * W * 4);
	const set = (x, y, c, a = 255) => { const i = (y * W + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = a; };
	const glass = [200, 220, 235], dark = [70, 80, 95], cork = [150, 110, 70];
	for (let y = 1; y < 4; y++) for (let x = 6; x < 10; x++) set(x, y, y === 1 ? shadeC(cork, 0.8) : cork);
	for (let x = 5; x < 11; x++) set(x, 4, dark);
	for (let y = 5; y < 7; y++) { set(6, y, glass); set(9, y, glass); set(7, y, liquidTop); set(8, y, liquidTop); }
	for (let y = 7; y < 15; y++) {
		const half = y < 9 ? 3 + (y - 7) : y > 13 ? 4 : 5;
		for (let x = 8 - half; x < 8 + half; x++) {
			const edge = x === 8 - half || x === 8 + half - 1 || y === 14;
			const t = (y - 7) / 7;
			set(x, y, edge ? dark : mixC(liquidTop, liquidBottom, t));
		}
	}
	set(5, 9, [255, 255, 255]); set(5, 10, [230, 240, 255]);
	for (const [x, y] of sparkle) set(x, y, [255, 255, 255]);
	fs.writeFileSync(ASSETS + 'textures/item/' + name + '.png', encode(W, W, px));
	w(ASSETS + 'models/item/' + name + '.json', { parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/' + name } });
}
function shadeC(c, k) { return c.map(v => Math.round(v * k)); }
function mixC(a, b, t) { return a.map((v, i) => Math.round(v + (b[i] - v) * t)); }
bottle('prismatic_serum', [255, 90, 200], [80, 200, 255], [[7, 9], [9, 11], [6, 12]]);
bottle('mutagenic_serum', [150, 255, 80], [30, 140, 40], [[8, 10]]);
bottle('heroic_serum', [255, 230, 120], [220, 140, 20], [[7, 9], [9, 12]]);

// ---------------------------------------------------------------- lang
// (v0.13.18 playtest: titles never wrap, so long spec phrases are subtitles under a short title -- see the overrides below)
const L = {
	// entities & items
	'entity.projecthero.darkseid': 'Darkseid',
	'entity.projecthero.parademon': 'Parademon',
	'entity.projecthero.mother_box': 'Mother Box',
	'entity.projecthero.boom_tube': 'Boom Tube',
	'entity.projecthero.omega_beam': 'Omega Beam',
	'entity.projecthero.parademon_bolt': 'Parademon Bolt',
	'entity.projecthero.mother_box.active': '◆ MOTHER BOX ◆',
	'entity.projecthero.mother_box.disrupting': 'DISRUPTING %s%%',
	'entity.projecthero.mother_box.overloading': '⚠ OVERLOADING ⚠',
	'entity.projecthero.mother_box.disabled': 'Mother Box (disabled)',
	'item.projecthero.boom_tube_beacon': 'Boom Tube Beacon',
	'item.projecthero.boom_tube_beacon.hint': 'Use it to open the way for Apokolips -- the Darkseid Raid begins where you stand.',
	'item.projecthero.boom_tube_beacon.warning': 'Everyone nearby is drawn in (up to %s heroes). There is no turning back.',
	'item.projecthero.omega_core': "Darkseid's Omega Core",
	'item.projecthero.omega_core.hint': 'The still-burning heart of the Omega Effect. A powerful crafting material.',
	'item.projecthero.omega_shard': 'Omega Shard',
	'item.projecthero.omega_shard.hint': 'A splinter of crystallised Omega energy. Used in advanced crafting.',
	'item.projecthero.mother_box': 'Mother Box',
	'item.projecthero.mother_box.hint': 'Sneak + use to attune it to this spot; use it to open a Boom Tube home.',
	'item.projecthero.mother_box.anchor': 'Attuned to %s, %s, %s (%s)',
	'item.projecthero.omega_relic': "Darkseid's Omega Relic",
	'item.projecthero.omega_relic.hint': 'Use: fire a pair of homing Omega Beams at what you are looking at.',
	'item.projecthero.omega_relic.charges': 'Omega charges: %s / %s',
	'item.projecthero.darkseid_spawn_egg': 'Darkseid Spawn Egg',
	'item.projecthero.parademon_spawn_egg': 'Parademon Spawn Egg',
	'item.projecthero.prismatic_serum': 'Prismatic Serum',
	'item.projecthero.prismatic_serum.hint': 'Grants a random power you do not have -- any power at all.',
	'item.projecthero.mutagenic_serum': 'Mutagenic Serum',
	'item.projecthero.mutagenic_serum.hint': 'Grants a random Experimental power you do not have.',
	'item.projecthero.heroic_serum': 'Heroic Serum',
	'item.projecthero.heroic_serum.hint': 'Grants a random Hero-Tier power you do not have.',
	'item.projecthero.random_serum.rule': 'Adds to your powers: mutations need a free slot, a hero power takes a Primary slot.',
	'message.projecthero.random_serum.nothing': 'The serum finds nothing new to awaken in you.',
	'message.projecthero.random_serum.granted': 'A new power awakens: %s!',
	'projecthero.hero_tier.thor': 'The Power of Thor',
	'projecthero.hero_tier.iron_man': 'Tony Stark (Iron Man)',
	'projecthero.hero_tier.spider_man': 'Spider-Man',
	'projecthero.hero_tier.max_steel': 'Max Steel',
	'projecthero.hero_tier.punisher': 'The Punisher',
	'projecthero.hero_tier.green_lantern': 'Green Lantern',
	'projecthero.hero_tier.wolverine': 'Wolverine',
	'projecthero.hero_tier.titan_shifter': 'Titan Shifter',
	'projecthero.hero_tier.all_might': 'All Might (One For All)',
	'projecthero.hero_tier.hulk': 'The Hulk',
	'projecthero.hero_tier.symbiote': 'The Symbiote',
	'message.projecthero.boom_tube_beacon.busy': 'The Boom Tube will not open here -- an Apokolips Invasion is already underway nearby.',
	'message.projecthero.mother_box.attuned': 'The Mother Box hums: it will bring you back here.',
	'message.projecthero.mother_box.unattuned': 'The Mother Box is not attuned. Sneak + use to attune it.',
	'message.projecthero.mother_box.lost': 'The Mother Box cannot find its way to that place any more.',
	'message.projecthero.mother_box.channel_start': 'Disrupting the Mother Box -- stay close!',
	'message.projecthero.mother_box.channel': 'Disrupting %s %s%%',
	'message.projecthero.mother_box.channel_lost': 'Disruption interrupted -- get back to the Mother Box!',
	'message.projecthero.mother_box.hint': 'Right-click the Mother Box and stay beside it to disrupt it.',
	// boss / combat
	'boss.projecthero.darkseid': 'DARKSEID — LORD OF APOKOLIPS',
	'boss.projecthero.darkseid.arriving': 'DARKSEID — LORD OF APOKOLIPS',
	'boss.projecthero.darkseid.shielded': 'DARKSEID — SHIELDED (%s%%)',
	'boss.projecthero.darkseid.annihilation': 'DARKSEID — OMEGA ANNIHILATION (interrupt %s%%)',
	'boss.projecthero.darkseid.staggered': 'DARKSEID — STAGGERED',
	'boss.projecthero.darkseid.reshielded': 'DARKSEID — MOTHER BOX SHIELD %s%%',
	'message.projecthero.darkseid.shielded': 'DARKSEID IS SHIELDED (%s%%) — disable the Mother Boxes!',
	'message.projecthero.darkseid.omega_mark': '⚠ OMEGA MARK — his beams are locked on you! Break line of sight!',
	'message.projecthero.darkseid.grip_warn': "⚠ DARKSEID'S GRIP — get out of his sight!",
	'message.projecthero.darkseid.gripped': 'You are in his grip! Your team can break it by hitting him!',
	'message.projecthero.darkseid.grip_broken': "Darkseid's grip is broken!",
	'message.projecthero.darkseid.sweep_warn': '⚠ OMEGA BEAM SWEEP — jump it, fly over it, or take cover!',
	'message.projecthero.darkseid.annihilation_marked': 'YOU ARE MARKED FOR ANNIHILATION — take cover!',
	'title.projecthero.darkseid.annihilation': 'OMEGA ANNIHILATION',
	'title.projecthero.darkseid.annihilation.sub': '%s is marked — hit Darkseid hard to interrupt him!',
	'title.projecthero.darkseid.staggered': 'DARKSEID IS STAGGERED',
	'title.projecthero.darkseid.staggered.sub': 'Strike now!',
	'death.attack.projecthero.omega_beam': "%1$s was erased by %2$s's Omega Beams",
	'death.attack.projecthero.omega_beam.player': "%1$s was erased by %2$s's Omega Beams",
	// raid
	'event.projecthero.darkseid_raid': 'Apokolips Invasion',
	'title.projecthero.darkseid_raid.invasion': 'APOKOLIPS INVASION',
	'title.projecthero.darkseid_raid.approaches': 'THE LORD OF APOKOLIPS APPROACHES',
	'title.projecthero.darkseid_raid.wave': 'WAVE %s',
	'title.projecthero.darkseid_raid.wave1.sub': 'Parademons pour through the Boom Tubes',
	'title.projecthero.darkseid_raid.wave2.sub': 'Parademon gunners take to the skies',
	'title.projecthero.darkseid_raid.wave3.sub': 'Elites and Brutes lead the final assault',
	'title.projecthero.darkseid_raid.darkseid': 'DARKSEID',
	'title.projecthero.darkseid_raid.darkseid.sub': 'LORD OF APOKOLIPS',
	'title.projecthero.darkseid_raid.mother_boxes': 'MOTHER BOXES HAVE AWAKENED',
	'title.projecthero.darkseid_raid.mother_boxes.sub': 'Disable all four to break his shield',
	'title.projecthero.darkseid_raid.box_disabled': 'MOTHER BOX DISABLED',
	'title.projecthero.darkseid_raid.box_disabled.sub': 'DARKSEID SHIELD %s%%',
	'title.projecthero.darkseid_raid.shield_fallen': "DARKSEID'S SHIELD HAS FALLEN",
	'title.projecthero.darkseid_raid.shield_fallen.sub': 'He is vulnerable — strike!',
	'title.projecthero.darkseid_raid.overload': 'MOTHER BOX OVERLOAD',
	'title.projecthero.darkseid_raid.overload.sub': 'Darkseid is restored — do not ignore the Mother Boxes!',
	'title.projecthero.darkseid_raid.phase2': 'DARKSEID HAS UNLEASHED THE OMEGA EFFECT',
	'title.projecthero.darkseid_raid.phase2.sub': 'Beware the Omega Beam Sweep',
	'title.projecthero.darkseid_raid.phase3': 'DARKSEID ENTERS OMEGA RAGE',
	'title.projecthero.darkseid_raid.phase3.sub': 'Survive the Omega Annihilation',
	'title.projecthero.darkseid_raid.enrage': "DARKSEID'S POWER CONTINUES TO GROW",
	'title.projecthero.darkseid_raid.enrage.sub': 'Enrage %s — the Mother Boxes stir again',
	'title.projecthero.darkseid_raid.victory': 'APOKOLIPS HAS FALLEN',
	'title.projecthero.darkseid_raid.victory.sub': 'The Lord of Apokolips retreats through the Boom Tube',
	'title.projecthero.darkseid_raid.defeat': 'APOKOLIPS HAS CLAIMED THIS WORLD',
	'title.projecthero.darkseid_raid.defeat.sub': 'Every hero has fallen',
	'message.projecthero.darkseid_raid.roster': 'Apokolips Invasion: %s of %s heroes answer the call.',
	'message.projecthero.darkseid_raid.creative': 'Creative-mode players are not raid participants -- switch to survival to fight.',
	'message.projecthero.darkseid_raid.joined': '%s joins the fight against Apokolips.',
	'message.projecthero.darkseid_raid.sealed_outsider': 'The Apokolips Invasion roster is sealed -- you can fight, but you are not an official participant and will not be rewarded.',
	'message.projecthero.darkseid_raid.full_outsider': 'The Apokolips Invasion already has its full roster -- you can fight, but will not be rewarded.',
	'message.projecthero.darkseid_raid.reentry': 'You may re-enter the raid in %ss',
	'message.projecthero.darkseid_raid.boundary': '⚠ RETURN TO THE ARENA — %ss',
	'message.projecthero.darkseid_raid.pulled_back': 'The Boom Tube pulls you back into the fight.',
	'message.projecthero.darkseid_raid.defeat': 'Every participant has fallen. The Apokolips Invasion is lost.',
	'message.projecthero.darkseid_raid.fallen': '%s has fallen! They may return to the fight in %s seconds.',
	'message.projecthero.darkseid_raid.wave_cleared': 'Wave %s repelled!',
	'message.projecthero.darkseid_raid.reinforcements': 'Darkseid calls Parademon reinforcements through the Boom Tubes!',
	'message.projecthero.darkseid_raid.darkness': 'The sky burns red. Something vast steps toward this world...',
	'message.projecthero.darkseid_raid.mother_box_help': 'Right-click a Mother Box and stay beside it for 10 seconds to disrupt it. A neglected box overloads.',
	'message.projecthero.darkseid_raid.box_disabled_by': '%s disabled a Mother Box (%s/4).',
	'message.projecthero.darkseid_raid.sealed': 'Darkseid is at half strength -- the raid roster is now sealed.',
	'message.projecthero.darkseid_raid.box_reactivated': 'A MOTHER BOX HAS REACTIVATED!',
	'message.projecthero.darkseid_raid.falling': 'Darkseid falls to one knee... the Omega Effect tears loose...',
	'message.projecthero.darkseid_raid.refunded': 'The Apokolips Invasion was cancelled -- your Boom Tube Beacon has been returned.',
	'message.projecthero.darkseid_raid.reward': 'Raid reward: %sx %s',
	'message.projecthero.darkseid_raid.relic': "You claimed Darkseid's Omega Relic!",
	'message.projecthero.darkseid_raid.mother_box_drop': 'You claimed a Mother Box!',
	'bar.projecthero.darkseid_raid.preparation': 'APOKOLIPS INVASION — the Boom Tubes open in %ss',
	'bar.projecthero.darkseid_raid.wave': 'APOKOLIPS INVASION — Wave %s/3 · %s Parademons',
	'bar.projecthero.darkseid_raid.wave_cleared': 'Wave %s repelled — next in %ss',
	'bar.projecthero.darkseid_raid.entrance': 'THE LORD OF APOKOLIPS APPROACHES',
	'bar.projecthero.darkseid_raid.mother_boxes': 'MOTHER BOXES %s/4 DISABLED — DARKSEID SHIELD %s%%',
	'bar.projecthero.darkseid_raid.fight': 'Phase %s · %s Parademons · Enrage in %s',
	'bar.projecthero.darkseid_raid.enraged': 'Phase %s · %s Parademons · ENRAGED %s',
	'bar.projecthero.darkseid_raid.victory': 'APOKOLIPS HAS FALLEN',
	// advancements
	'advancement.projecthero.darkseid.anti_life.title': 'Anti-Life',
	'advancement.projecthero.darkseid.anti_life.description': 'Defeat Darkseid, Lord of Apokolips',
	'advancement.projecthero.darkseid.apokolips_falls.title': 'Apokolips Falls',
	'advancement.projecthero.darkseid.apokolips_falls.description': 'Defeat Darkseid without letting a single Mother Box overload',
	// guide
	'projecthero.guide.darkseid_raid': 'Apokolips Invasion (Darkseid)',
	'projecthero.guide.darkseid_raid.body': 'An endgame co-op raid for up to 8 heroes. Parademon waves, four Mother Boxes powering Darkseid’s shield, then three phases against the Lord of Apokolips himself. It never happens on its own -- you start it.',
	'projecthero.guide.darkseid_raid.start': 'Starting it',
	'projecthero.guide.darkseid_raid.start.body': 'Craft a Boom Tube Beacon (Nether Star, 4 Supervillain Tokens, 2 Echo Shards, 3 Crying Obsidian -- or a Nether Star with 4 Omega Shards and 4 Crying Obsidian) and use it. Every survival player within 48 blocks joins the roster (max 8). Others can join while Darkseid is above half health; after that the roster is sealed. The arena is 64 blocks across the beacon -- stray outside and you are warned, then pulled back after a few seconds. Flight is never disabled.',
	'projecthero.guide.darkseid_raid.waves': 'The invasion',
	'projecthero.guide.darkseid_raid.waves.body': 'Three waves pour through Boom Tubes: Parademons; then Parademons with Ranged gunners (they hover level with flyers and lead their shots); then Elites, Brutes and gunners. Parademons take to the air after flying heroes.',
	'projecthero.guide.darkseid_raid.mother_boxes': 'The Mother Boxes',
	'projecthero.guide.darkseid_raid.mother_boxes.body': 'Darkseid arrives shielded; four Mother Boxes around the arena each power 25% of the shield and he takes no damage until all four are down. Right-click a box and stay within 4 blocks for 10 seconds to disrupt it (hits do not interrupt you -- walking away, or his Grip, does). A box left alone for 90 seconds OVERLOADS: Darkseid heals, the box explodes, Parademons pour out and the ground burns.',
	'projecthero.guide.darkseid_raid.phase1': 'Phase 1',
	'projecthero.guide.darkseid_raid.phase1.body': 'Fists and a two-handed hammer up close; the Godly Ground Slam (the ring drawn on the ground is its reach -- it also hits flyers up to 10 blocks); OMEGA BEAMS (you glow and get a warning -- two beams curve after you; blocks stop them and sharp turns make them overshoot); the Omega Barrage (warning circles where you are and where you are running -- leave them); DARKSEID’S GRIP (a purple line -- break line of sight before it closes; hitting him hard frees a gripped ally); the Omega Teleport (stay far away and he comes to you, even in the air); the Apokoliptian Charge (the lane is drawn on the ground -- step out of it); and Boom Tube reinforcements.',
	'projecthero.guide.darkseid_raid.phase2': 'Phase 2 -- Omega Empowered (60%)',
	'projecthero.guide.darkseid_raid.phase2.body': 'Faster, stronger, shorter cooldowns, more reinforcements, and the OMEGA BEAM SWEEP: a knee-high beam that turns a full circle around him. Jump it, fly over it, or put a block between you and him.',
	'projecthero.guide.darkseid_raid.phase3': 'Phase 3 -- Omega Rage (25%)',
	'projecthero.guide.darkseid_raid.phase3.body': 'The arena burns and he is at his most dangerous (two sweep beams at once). About every 45 seconds he charges OMEGA ANNIHILATION on one hero for 5 seconds: the whole team must hit him hard (the boss bar shows how close you are) to interrupt it and stagger him for a big damage window. Fail and an arena-wide Omega blast hits everyone -- cover halves it, and it is worst for the marked hero.',
	'projecthero.guide.darkseid_raid.death': 'Death and defeat',
	'projecthero.guide.darkseid_raid.death.body': 'Dying does not end the raid: respawn and return after 25 seconds. The raid is lost only if every participant is down at the same time. After 15 minutes of fighting him he soft-enrages -- stronger every few minutes, more Parademons, and Mother Boxes switching back on (each takes 25% off the damage he takes until you disable it again).',
	'projecthero.guide.darkseid_raid.rewards': 'Rewards',
	'projecthero.guide.darkseid_raid.rewards.body': 'Every official participant gets Darkseid’s Omega Core, likely 1-3 Omega Shards, a rare Mother Box (a personal Boom Tube home) and a very rare Omega Relic (24 charges of homing Omega Beams), plus a lot of experience. Advancements: Anti-Life, and Apokolips Falls for a raid with no overloads. Nobody gets Darkseid’s powers.',
	'projecthero.guide.mutation.random_serums': 'Random-power serums (v0.13.18): the Mutagenic Serum grants a random Experimental power, the Heroic Serum a random Hero-Tier power, and the Prismatic Serum any power at all -- always one you do not have yet. Like stacking a power: a mutation needs a free slot, a hero power takes a Primary slot. If nothing new can be granted, the serum is not used up.',
};
const EVENTS = ['darkseid_entrance', 'darkseid_step', 'darkseid_punch', 'darkseid_slam', 'omega_beam_charge', 'omega_beam_fire', 'omega_impact',
	'darkseid_grip', 'darkseid_teleport', 'boom_tube', 'mother_box_hum', 'mother_box_activate', 'mother_box_disable', 'mother_box_overload',
	'darkseid_phase', 'omega_annihilation', 'darkseid_hurt', 'darkseid_death', 'apokolips_victory', 'apokolips_defeat', 'parademon_screech',
	'parademon_hurt', 'parademon_death', 'parademon_wings', 'apokolips_music'];
const SUB = {
	darkseid_entrance: 'Darkseid arrives', darkseid_step: 'Darkseid strides', darkseid_punch: 'Darkseid strikes', darkseid_slam: 'The ground shatters',
	omega_beam_charge: 'Omega energy builds', omega_beam_fire: 'Omega Beams fire', omega_impact: 'Omega impact', darkseid_grip: "Darkseid's Grip",
	darkseid_teleport: 'Darkseid vanishes', boom_tube: 'A Boom Tube opens', mother_box_hum: 'Mother Box hums', mother_box_activate: 'Mother Box awakens',
	mother_box_disable: 'Mother Box goes dark', mother_box_overload: 'Mother Box overloads', darkseid_phase: 'Darkseid roars', omega_annihilation: 'Omega Annihilation charges',
	darkseid_hurt: 'Darkseid hurts', darkseid_death: 'Darkseid falls', apokolips_victory: 'Apokolips falls', apokolips_defeat: 'Apokolips triumphs',
	parademon_screech: 'Parademon screeches', parademon_hurt: 'Parademon hurts', parademon_death: 'Parademon dies', parademon_wings: 'Wings beat', apokolips_music: 'Music plays',
};
for (const e of EVENTS) L['subtitles.projecthero.' + e] = SUB[e];
Object.assign(L, {
 "title.projecthero.darkseid_raid.mother_boxes": "MOTHER BOXES",
 "title.projecthero.darkseid_raid.mother_boxes.sub": "MOTHER BOXES HAVE AWAKENED — disable all four",
 "title.projecthero.darkseid_raid.box_disabled": "BOX DISABLED",
 "title.projecthero.darkseid_raid.box_disabled.sub": "MOTHER BOX DISABLED — DARKSEID SHIELD %s%%",
 "title.projecthero.darkseid_raid.shield_fallen": "STRIKE NOW",
 "title.projecthero.darkseid_raid.shield_fallen.sub": "DARKSEID'S SHIELD HAS FALLEN",
 "title.projecthero.darkseid_raid.overload": "OVERLOAD",
 "title.projecthero.darkseid_raid.overload.sub": "MOTHER BOX OVERLOAD — Darkseid is restored!",
 "title.projecthero.darkseid_raid.phase2": "OMEGA EFFECT",
 "title.projecthero.darkseid_raid.phase2.sub": "DARKSEID HAS UNLEASHED THE OMEGA EFFECT",
 "title.projecthero.darkseid_raid.phase3": "OMEGA RAGE",
 "title.projecthero.darkseid_raid.phase3.sub": "DARKSEID ENTERS OMEGA RAGE",
 "title.projecthero.darkseid_raid.enrage": "ENRAGED %s",
 "title.projecthero.darkseid_raid.enrage.sub": "DARKSEID'S POWER CONTINUES TO GROW",
 "title.projecthero.darkseid_raid.victory": "VICTORY",
 "title.projecthero.darkseid_raid.victory.sub": "APOKOLIPS HAS FALLEN",
 "title.projecthero.darkseid_raid.defeat": "DEFEAT",
 "title.projecthero.darkseid_raid.defeat.sub": "APOKOLIPS HAS CLAIMED THIS WORLD",
 "title.projecthero.darkseid_raid.invasion": "APOKOLIPS INVASION",
 "title.projecthero.darkseid.annihilation": "OMEGA ANNIHILATION",
 "title.projecthero.darkseid.annihilation.sub": "%s is marked — hit Darkseid to interrupt!",
 "title.projecthero.darkseid.staggered": "STAGGERED",
 "title.projecthero.darkseid.staggered.sub": "Darkseid is staggered — strike now!"
});

const LANG = ASSETS + 'lang/en_us.json';
const raw = fs.readFileSync(LANG, 'utf8');
const lang = JSON.parse(raw);
Object.assign(lang, L);
fs.writeFileSync(LANG, JSON.stringify(lang, null, 2) + '\n');

// ---------------------------------------------------------------- verify every translatable key in the new code
const files = [];
const walk = d => { for (const f of fs.readdirSync(d)) { const p = path.join(d, f); if (fs.statSync(p).isDirectory()) walk(p); else if (p.endsWith('.java')) files.push(p); } };
walk(path.join(REPO, 'src/main/java/com/projecthero/mod/darkseid'));
files.push(path.join(REPO, 'src/main/java/com/projecthero/mod/hero/item/RandomPowerSerumItem.java'));
let missing = 0;
for (const f of files) {
	for (const m of fs.readFileSync(f, 'utf8').matchAll(/translatable\("([a-z0-9_.]+)"/g)) {
		if (!m[1].endsWith(".") && !(m[1] in lang)) { console.error('MISSING LANG', m[1], 'in', path.basename(f)); missing++; }
	}
}
for (const k of ['entity.projecthero.darkseid', 'entity.projecthero.parademon']) if (!(k in lang)) missing++;
for (let n = 1; n <= 3; n++) if (!('title.projecthero.darkseid_raid.wave' + n + '.sub' in lang)) missing++;
if (missing) { console.error(missing + ' missing keys'); process.exit(1); }
console.log('data + lang written; ' + Object.keys(L).length + ' lang keys, all referenced keys present');
