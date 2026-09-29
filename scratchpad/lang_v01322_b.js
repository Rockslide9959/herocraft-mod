// v0.13.22 mutation revamp, batch B lang: Geokinesis, Crystalkinesis, Pyrokinesis, Cryokinesis, Water Manipulation.
// Every ability name + desc (the guide and the "Your Power" screen read them), the new H / N abilities, the power
// descriptions, new action-bar messages, the Ice Blade item and the new entities. Also removes the keys of the
// abilities the revamp dropped (Boulder Lift, Crystal Spikes, Crystal Barrier, Flame Dash).
// Run from the repo root: node scratchpad/lang_v01322_b.js
const fs = require('fs');
const path = require('path');
const FILE = path.join(__dirname, '../src/main/resources/assets/projecthero/lang/en_us.json');

// ---- drop the retired ability keys (line-level, keeps the file's formatting) ----
const RETIRED = [
	'projecthero.power.power_05_geokinesis.ability.boulder_lift',
	'projecthero.power.power_05_geokinesis.ability.boulder_lift.desc',
	'projecthero.power.power_06_crystalkinesis.ability.crystal_spikes',
	'projecthero.power.power_06_crystalkinesis.ability.crystal_spikes.desc',
	'projecthero.power.power_06_crystalkinesis.ability.crystal_barrier',
	'projecthero.power.power_06_crystalkinesis.ability.crystal_barrier.desc',
	'projecthero.power.power_08_pyrokinesis.ability.flame_dash',
	'projecthero.power.power_08_pyrokinesis.ability.flame_dash.desc',
	'message.projecthero.ability.boulder_ready',
	'message.projecthero.crystal.skate_on',
	'message.projecthero.crystal.skate_off',
];
{
	let text = fs.readFileSync(FILE, 'utf8');
	const eol = text.includes('\r\n') ? '\r\n' : '\n';
	const keyRe = /^\s*"((?:[^"\\]|\\.)*)"\s*:/;
	const lines = text.split(/\r?\n/).filter(l => {
		const m = l.match(keyRe);
		return !(m && RETIRED.includes(m[1]));
	});
	// the last entry before the closing brace must not keep a trailing comma
	let close = lines.length - 1;
	while (close > 0 && lines[close].trim() !== '}') close--;
	let prev = close - 1;
	while (prev > 0 && lines[prev].trim() === '') prev--;
	lines[prev] = lines[prev].replace(/,\s*$/, '');
	const out = lines.join(eol);
	JSON.parse(out);
	fs.writeFileSync(FILE, out);
}

const G = 'projecthero.power.power_05_geokinesis';
const C = 'projecthero.power.power_06_crystalkinesis';
const P = 'projecthero.power.power_08_pyrokinesis';
const I = 'projecthero.power.power_09_cryokinesis';
const W = 'projecthero.power.power_25_water_manipulation';

require('./langset.js')([
	// ======================= 05 Geokinesis =======================
	{ anchor: G + '.name', entries: {
		[G + '.desc']: 'The ground under you is your ammo: every rock, spike, quake and pillar is torn from what you stand on, and the material changes the hit — deepslate is heavier and slower, sand throws a blinding cloud, netherrack sets things alight, ore veins hit far harder, ice slows. The HUD names the ground you are drawing on.',
	} },
	{ anchor: G + '.trigger', entries: {
		[G + '.ability.rock_shot']: 'Rock Shot',
		[G + '.ability.rock_shot.desc']: 'R: tear a chunk out of the ground you stand on and hurl it — a real flying rock that looks like that block — for 11 damage (x ground: deepslate x1.35 and +knockback, ore x1.45, sand x0.85 plus a blinding dust cloud, netherrack sets alight, ice slows, x0.75 with no earth underfoot). 1.7s cooldown (x1.25 on deepslate). Sneak + R shakes the ground in a 10-block cone: 11 damage and Slowness II to all caught, 8.5s cooldown.',
		[G + '.ability.earth_spike']: 'Earth Spike',
		[G + '.ability.earth_spike.desc']: 'G: a line of stone spikes races 16 blocks along the ground the way you face, two blocks a tick — each creature it erupts under takes 15 (x ground), is thrown upward and slowed. The spikes are the local stone tipped with dripstone and crumble after 2.5s. Sneak + G auto-tracks every enemy in a 10-block cone instead (18 damage, a dripstone spike under each, 19s cooldown). 4.3s cooldown.',
		[G + '.ability.rock_surf']: 'Rock Surf',
		[G + '.ability.rock_surf.desc']: 'X: ride a slab of the ground across it — you are carried the way you face at a gallop (faster on ice and sand, slower on deepslate), hopping single-block steps; anything you plough into takes 7 (x ground) and is bowled aside. Ends on a second press, after 15s, in water, or 1.5s after leaving the ground (4.3s cooldown). Sneak + X: Earth Swim — phase into the earth and swim through it fast for 12s, immune to everything but the void (25s cooldown).',
		[G + '.ability.earthquake']: 'Earthquake',
		[G + '.ability.earthquake.desc']: 'Z: hold for 4.25s to charge, then a seismic pulse for 54 damage (x ground) across a 25-block radius, with nausea and slowness, that rips deep ravines out of the ground around you (a pad under your feet stays solid). 55s cooldown. Sneak as you press Z to charge a Colossal Rock instead — a boulder gathers overhead, then you hurl it: a huge projectile that detonates into a crater for 60 damage. 75s cooldown.',
		[G + '.ability.stone_wall']: 'Stone Wall',
		[G + '.ability.stone_wall.desc']: 'V: raise a 4x4 wall of the local stone for 25s — look up for a dome (left-click it to drop it), look down for a 20-block bridge. On sand it is sandstone, in the Nether netherrack, on deepslate cobbled deepslate, on ice packed ice. 7s cooldown. Sneak + V: Seismic Sense — every creature within 30 blocks glows for 12s.',
		[G + '.ability.earth_armor']: 'Earth Armor',
		[G + '.ability.earth_armor.desc']: 'C: toggle a thick shell of stone skin (tinted by the ground you draw on) — Resistance II, 60% knockback resistance, 25% slower, +10 ability damage and +8 melee. Drains a strain bar (575, ~29s) that recharges once dropped; dropping it puts the slot on a 20s cooldown.',
		[G + '.ability.sinkhole']: 'Sinkhole',
		[G + '.ability.sinkhole.desc']: 'H: the ground beneath whatever you look at (20 blocks) drops away — a pit three deep, sized to the target, that closes back up after 6s and lifts anything still inside back out. The target takes 10 (x ground), is dragged down and rooted for 3s. Players and boss-sized creatures are rooted without the dig. 12s cooldown.',
		[G + '.ability.tectonic_pillar']: 'Tectonic Pillar',
		[G + '.ability.tectonic_pillar.desc']: 'N: a column of the local stone erupts. Look at a creature (20 blocks) and it bursts up under them — 12 damage (x ground) and a launch straight into the air. Look at nothing and it erupts under you, throwing you about 9 blocks up with 8s of no fall damage. The column stands for 4s. 7.7s cooldown.',
	} },
	{ anchor: G + '.passive.mining', entries: {
		[G + '.passive.mining']: 'Iron-tool hands whatever you hold; earth-family blocks break 50% faster; a bare-handed hit on an ore mines the whole vein; Haste while standing on earth. Sneak + right-click the ground empty-handed (or Sneak + V) for Seismic Sense.',
		[G + '.earth_swim']: 'Earth Swim',
	} },

	// ======================= 06 Crystalkinesis =======================
	{ anchor: C + '.name', entries: {
		[C + '.desc']: 'Plant crystal nodes, then shatter them: every Crystal Shard grows a glowing amethyst node where it lands (up to 8 at once, 30s each), and your other moves detonate, split and empower them. Reflective amethyst armor, a crystal rail to glide on and a turret spire round out the kit.',
	} },
	{ anchor: C + '.trigger', entries: {
		[C + '.ability.crystal_shard']: 'Crystal Shard',
		[C + '.ability.crystal_shard.desc']: 'R: fire a flying amethyst shard for 13 damage and Slowness II — wherever it lands it grows a crystal node (on the block face it struck, or at the feet of the creature it hit). 1.7s cooldown. Sneak + R fires a volley of 5 shards (7 damage each), each planting its own node, 8.5s cooldown.',
		[C + '.ability.shatter']: 'Shatter',
		[C + '.ability.shatter.desc']: 'G: detonate every crystal node you own — each bursts for 16 damage to everything within 3 blocks (a Resonance Spire bursts half again as hard and wide). 4s cooldown; needs at least one node.',
		[C + '.ability.crystal_path']: 'Crystal Path',
		[C + '.ability.crystal_path.desc']: 'X: glide along your aim — climbing or diving up to 35 degrees — while a rail of amethyst grows beneath you (solid for 3s, so others can follow) with a line of light running ahead. Up to 10s; press again to stop. No fall damage for 3s after. 5.1s cooldown.',
		[C + '.ability.crystal_eruption']: 'Crystal Eruption',
		[C + '.ability.crystal_eruption.desc']: 'Z: hold for 4.25s to charge, then a burst for 48 damage across a 20-block radius that raises a dense field of amethyst pillars — and every node you own shatters along with it. 60s cooldown. Sneak as you press Z to charge a Colossal Crystal instead: a huge crystal hurled at speed that explodes for 66 damage and leaves three nodes behind. 85s cooldown.',
		[C + '.ability.crystal_prison']: 'Crystal Prison',
		[C + '.ability.crystal_prison.desc']: 'V: seal a target in an amethyst cage for 7s with Slowness X and a jump lock — they cannot move at all — and grow a crystal node beside the cage for a Shatter follow-up. 10.2s cooldown.',
		[C + '.ability.crystal_armor']: 'Crystal Armor',
		[C + '.ability.crystal_armor.desc']: 'C: toggle thick amethyst plating with crystal spikes at the shoulders and spine — Resistance I, knockback resistance, +10 ability damage and +8 melee — that turns projectiles back: an arrow or fireball that hits you is cancelled and fired back at its shooter as a crystal shard (30 strain each). Drains a strain bar (575, ~29s) that recharges once dropped; 20s cooldown once dropped.',
		[C + '.ability.resonance_spire']: 'Resonance Spire',
		[C + '.ability.resonance_spire.desc']: 'H: grow a great crystal spire where you aim (16 blocks) — for 12s it fires a crystal shard (8 damage) at any hostile creature it can see within 14 blocks, about 1.7 times a second. It counts as a node: Shatter it for a bigger burst. 17s cooldown.',
		[C + '.ability.refract']: 'Refract',
		[C + '.ability.refract.desc']: 'N: fire a beam of refracted light (32 blocks, 11 damage). If it passes through one of your nodes first, the node splits it into three beams (9 damage each) aimed at the nearest enemies it can see — fanned out ahead if there are none — and the node itself splits into three. 4.3s cooldown.',
	} },
	{ anchor: C + '.passive.crystal_immunity', entries: {
		[C + '.passive.crystal_immunity']: 'Your crystal nodes, spires and shards never harm you; nodes crack away on their own after 30s, when you leave, or when you plant a ninth.',
	} },

	// ======================= 08 Pyrokinesis =======================
	{ anchor: P + '.name', entries: {
		[P + '.desc']: 'One Heat bar: every attack stokes it, and the hotter you run the harder you hit (up to +40%). Above 75% your flames burn blue — Fireball becomes a Flame Laser, Inferno a blue inferno, Flame Body scorches everything alight around you. At 100% you overheat: 1 damage a second and no heat-building move until you cool below 60% — or vent it all with Heat Wave. Heat bleeds away while you are not channelling, three times faster in water or rain. You never catch fire yourself.',
	} },
	{ anchor: P + '.trigger', entries: {
		[P + '.ability.fireball']: 'Fireball',
		[P + '.ability.fireball.desc']: 'R: hurl a ghast fireball that explodes and scatters fire — a direct hit deals 14 fire damage (x heat, +5 in the Nether, +10 in Flame Body). +9 heat. Above 75% heat it becomes a Flame Laser instead: an instant 40-block beam of blue fire for 22 (x heat) around where it lands, with a blocks-only blast. +12 heat. 1.7s cooldown.',
		[P + '.ability.flamethrower']: 'Flamethrower',
		[P + '.ability.flamethrower.desc']: 'G: hold to pour a 7-block stream of flame — 7 fire damage a hit (x heat) and 4s of burning to everything in the cone, setting the surfaces it washes over alight. Builds heat steadily (~14s from cold to overheat); cuts out if you overheat.',
		[P + '.ability.jet_flight']: 'Jet Flight',
		[P + '.ability.jet_flight.desc']: 'X: hold to fly on twin jets of flame, driven along wherever you look at about 18 blocks a second — let go to coast, with no fall damage for 5s after. Builds heat fast (overheating or diving into water cuts the jets). With Flight also owned, it leaves a trail of fire.',
		[P + '.ability.inferno']: 'Inferno',
		[P + '.ability.inferno.desc']: 'Z: hold for 4.25s to charge (the fireball swells over your head), then loose a colossal flying fireball — a huge blast, and an afterburn of 14 fire damage (x heat) and 7s of burning within 5.5 blocks of where it bursts. Loosed above 75% heat it is a blue inferno with a bigger blast. +30 heat. 47s cooldown.',
		[P + '.ability.flame_wall']: 'Flame Spark',
		[P + '.ability.flame_wall.desc']: 'V: a flint-and-steel in your hand — light fires, TNT, campfires and nether portals where you look. Sneak + V pulls in every fire within 5 blocks (each one stokes your heat), cooks every raw item in your hand at once and turns held logs into coal blocks, planks into charcoal. 1s cooldown.',
		[P + '.ability.flame_body']: 'Flame Body',
		[P + '.ability.flame_body.desc']: 'C: wreathe yourself in living flame — an emissive fire body that shifts from orange to white-gold to blue as your heat climbs. +10 ability damage, +8 melee (+5 more in the Nether), melee hits and attackers catch fire, everything within 3 blocks (4 when blue) is set alight, and your heat never drops below half. Blue: anything you have set burning within 24 blocks takes extra fire damage. 20s cooldown once dropped.',
		[P + '.ability.heat_wave']: 'Heat Wave',
		[P + '.ability.heat_wave.desc']: 'H: vent every degree of heat at once as a rolling ring of fire — radius 4 + 6 x heat, damage 8 + 24 x heat (+ Nether / Flame Body bonuses), knockback and 5s of burning — leaving you stone cold and ending an overheat. Needs at least 10% heat. 10s cooldown.',
		[P + '.ability.fire_whip']: 'Fire Whip',
		[P + '.ability.fire_whip.desc']: 'N: crack a 9-block lash of living flame that uncurls in an S along your aim — everything it touches takes 11 fire damage (x heat), burns for 4s and is yanked toward you. +7 heat. 2.6s cooldown.',
	} },
	{ anchor: P + '.passive.fire_resist', entries: {
		[P + '.passive.fire_resist']: 'Complete fire immunity — you are never set alight, even by your own flames; your Heat bar cools on its own when you stop channelling.',
	} },

	// ======================= 09 Cryokinesis =======================
	{ anchor: I + '.name', entries: {
		[I + '.desc']: 'Frost stacks, then frozen solid: every Cryokinesis hit adds frost stacks to its target (shown as creeping frost and a ring of snowflakes). At 5 stacks the target is frozen solid — sealed in a shell of ice and pinned in place for 3s (1.5s for players), unable to move, jump or strike, and the freeze shatters for 6. Stacks fade one at a time after 3s without a new one.',
	} },
	{ anchor: I + '.trigger', entries: {
		[I + '.ability.ice_bolt']: 'Ice Bolt',
		[I + '.ability.ice_bolt.desc']: 'R: a bolt of frost — 8.5 damage (+10 in a cold biome or snow, +10 in Frozen Armor), a frost stack and brief slowness. 1.7s cooldown. Sneak + R opens a weapon wheel to shape the ice tool of your choice: iron mining level, 32 uses.',
		[I + '.ability.freeze_beam']: 'Freeze Beam',
		[I + '.ability.freeze_beam.desc']: 'G: hold a freezing beam (16 blocks) — 6 damage and a frost stack every half second, heavy slowness, and it snuffs fire, turns lava it washes over to obsidian (cobblestone if flowing) and lays powder snow where it lands. The beam chills you too: a frostbite gauge (575) fills in ~5s and forces it off until it thaws.',
		[I + '.ability.ice_slide']: 'Ice Ramp',
		[I + '.ability.ice_slide.desc']: 'X: the Iceman slide — a 3x3 platform of packed ice forms under you wherever you go (over air and water too) and you skate 150% faster. Look up while moving and the ice builds into a rising ramp; tap Sneak to drop a level. No fall damage while sliding. Toggle.',
		[I + '.ability.absolute_zero']: 'Absolute Zero',
		[I + '.ability.absolute_zero.desc']: 'Z: hold for 4.25s to charge (snow swirls up around you), then a 20-block flash-freeze for 42 damage that freezes every target solid at once, blankets the area in snow and sheets water with ice. 38s cooldown.',
		[I + '.ability.ice_wall']: 'Glacier Wall',
		[I + '.ability.ice_wall.desc']: 'V: raise a curved glacier wall — a 7-wide arc of packed ice three high, crested with blue ice, bowed around you 3.5 blocks out — for 15s; anything where it rises is shoved away and takes a frost stack. Sneak + V: a field of jagged ice spikes ahead (10 damage, 2 frost stacks, tossed into the air). 7s cooldown.',
		[I + '.ability.frozen_armor']: 'Frozen Armor',
		[I + '.ability.frozen_armor.desc']: 'C: toggle thick frost plating with icicles at the shoulders and spine — Resistance I, knockback resistance, +10 ability damage, +8 melee; everything within 4 blocks takes a frost stack every 2s, anything that strikes you takes one, water you walk over freezes and snow dusts the ground. Drains a cold reserve (575, ~35s); 20s cooldown once dropped.',
		[I + '.ability.flash_freeze']: 'Flash Freeze',
		[I + '.ability.flash_freeze.desc']: 'H: an instant cold snap 8 blocks around you — exposed water freezes to ice and lava to obsidian for 20s, every fire goes out (yours and your squad\'s burning too), and every creature caught takes 5 and two frost stacks. 12s cooldown.',
		[I + '.ability.ice_blade']: 'Ice Blade',
		[I + '.ability.ice_blade.desc']: 'N: conjure a sword of ice into your hand (whatever you held moves to a free slot) — 10 attack damage and a frost stack per hit. It melts after 30s, or the instant it leaves your hand; it can never be stored, dropped or kept. Press N again to shatter it early. 32s cooldown.',
	} },
	{ anchor: I + '.passive.freeze_resist', entries: {
		[I + '.passive.freeze_resist']: 'Immune to freezing damage; your own cold never builds up on you',
	} },

	// ======================= 25 Water Manipulation =======================
	{ anchor: W + '.name', entries: {
		[W + '.desc']: 'A carried water supply: the Water bar (575) fuels every move and nothing refills it for free — stand in water (fast), in rain (slowly), right-click a water bottle (+20%) or Sneak + right-click a water bucket (+50%); a trickle of moisture from the air refills it very slowly anywhere but the Nether. Aquatic Form halves every cost. Extremely capable underwater.',
	} },
	{ anchor: W + '.trigger', entries: {
		[W + '.ability.water_shot']: 'Water Shot',
		[W + '.ability.water_shot.desc']: 'R: a high-pressure jet for 10 damage (+5 wet, +10 in Aquatic Form) with knockback that puts out fire and soaks the target. 25 water, 1.7s cooldown. Sneak + hold R to spray a stream instead — 6 damage a hit, puts out fire and pushes, 2.5 water a tick.',
		[W + '.ability.water_whip']: 'Water Whip',
		[W + '.ability.water_whip.desc']: 'G: a tendril of water uncurls from your hand along your aim (12 blocks), sways and snaps — everything it touches takes 19 and is hauled toward you, soaked. 40 water, 5.1s cooldown.',
		[W + '.ability.riptide']: 'Riptide',
		[W + '.ability.riptide.desc']: 'X: fire yourself along a jet of water in a fast dash — further while wet — bowling over anything in the way (7 damage). Free in water or rain, otherwise 30 water. 5.1s cooldown.',
		[W + '.ability.tidal_wave']: 'Tidal Wave',
		[W + '.ability.tidal_wave.desc']: 'Z: hold for 4.25s to gather a sphere of water, then send a wall of real water — 7 wide, 2 high — rolling 18 blocks out along your aim: 42 damage to everything it sweeps over, carrying them with it. The water only fills air and drains right behind the crest. 200 water, 38s cooldown.',
		[W + '.ability.water_prison']: 'Water Prison',
		[W + '.ability.water_prison.desc']: 'V: seal a target in a shell of water for 8s — pinned in place, weakened, but not drowned; the water never spreads and drains the moment it ends. Press again to free them early. 60 water, 10.2s cooldown. Sneak + hold V for 4s to shape a temporary water source where you look (60s) — or right-click a water source while shaping to siphon it into your supply.',
		[W + '.ability.aquatic_form']: 'Aquatic Form',
		[W + '.ability.aquatic_form.desc']: 'C: toggle a shimmering film of living water over your body — doubled water buffs, +10 ability damage, +8 melee, every water cost halved and the supply refills 50% faster. 20s cooldown once dropped.',
		[W + '.ability.healing_water']: 'Healing Water',
		[W + '.ability.healing_water.desc']: 'H: pour healing water over yourself and everyone on your side within 6 blocks — squadmates and your own tamed animals — for 6 health each, 5s of Regeneration I, and any fire put out. 120 water, 12s cooldown.',
		[W + '.ability.geyser']: 'Geyser',
		[W + '.ability.geyser.desc']: 'N: a geyser erupts. Look at a creature (20 blocks) and it blasts up under them — 12 damage and a launch straight up, soaked. Look at nothing and it erupts under you, throwing you about 8 blocks up with 8s of no fall damage. 50 water, 7.5s cooldown.',
	} },
	{ anchor: W + '.passive.no_drown', entries: {
		[W + '.passive.no_drown']: 'Water Breathing and Dolphin\'s Grace always; Night Vision, extra speed and endless air while in water',
		[W + '.passive.supply']: 'Water supply: refills in water, in rain, from bottles and buckets, and very slowly from the air',
	} },

	// ======================= messages / items / entities =======================
	{ anchor: 'message.projecthero.geo.seismic', entries: {
		'message.projecthero.geo.no_target': 'No target in sight',
		'message.projecthero.crystal.no_nodes': 'No crystal nodes to shatter — plant some with Crystal Shard',
		'message.projecthero.pyro.overheat': 'OVERHEATED! Vent with Heat Wave or cool below 60%',
		'message.projecthero.pyro.overheated': 'Too hot — you\'re overheated. Vent with Heat Wave or cool down first',
		'message.projecthero.pyro.doused': 'Your jets won\'t light underwater',
		'message.projecthero.pyro.too_cold': 'Not enough heat to vent (needs 10%)',
		'message.projecthero.cryo.hands_full': 'No free slot to move what you are holding',
		'item.projecthero.ice_blade': 'Ice Blade',
		'entity.projecthero.geo_rock': 'Rock Shot',
		'entity.projecthero.crystal_shard': 'Crystal Shard',
		'entity.projecthero.crystal_node': 'Crystal Node',
	} },
]);
