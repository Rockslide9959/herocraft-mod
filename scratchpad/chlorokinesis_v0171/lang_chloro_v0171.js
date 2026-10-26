// v0.17.1 Chlorokinesis revamp: lang keys. Replaces the exact existing strings named in REPLACE and inserts every key in
// ADD before the final '}' of en_us.json (keeps LF / the file's own layout; never re-serialises the file).
// Usage (from the repo root): node scratchpad/chlorokinesis_v0171/lang_chloro_v0171.js
'use strict';
const fs = require('fs');
const path = require('path');
const FILE = path.resolve(__dirname, '..', '..', 'src/main/resources/assets/projecthero/lang/en_us.json');
const P = 'projecthero.power.power_22_plant_manipulation_chlorokinesis';

const REPLACE = {
	[`${P}.desc`]: 'Command growth itself on a Chlorophyll meter the sun fills: thorns, whipping vines, roots that pin, spores, a walking Treant, healing blooms and an Overgrowth that ripens every field around you -- plus a green thumb for survival: instant harvests, felled trees, vine ladders, root bridges and a leaf shelter grown from nothing.',
	[`${P}.ability.overgrowth`]: 'Overgrowth',
	[`${P}.ability.overgrowth.desc`]: 'Gather for 1.5 s (rooted, roots coiling up you), then erupt across 12 blocks: 30 damage, Poison II for 6 s and every enemy rooted for 4 s (bosses slowed). Every crop and sapling in reach matures, bare dirt greens and flowers sprout. 60 Chlorophyll, 40 s cooldown. Shift+Z: Treant -- a walking tree ally fights for you for 45 s (80 HP, 10-damage slams, taunts enemies within 8 so they turn on it, heals on living ground). One at a time. 50 Chlorophyll, 60 s cooldown.',
	[`${P}.passive.bonemeal`]: 'Growth Aura: crops and saplings within 24 blocks grow faster.',
	[`${P}.passive.lush_regen`]: 'Minor regeneration in lush areas. Standing on or against grass, dirt or moss grants Regeneration and Haste. No fall damage onto living ground.',
};

const ADD = {
	// R
	[`${P}.ability.thorn_volley`]: 'Thorn Volley',
	[`${P}.ability.thorn_volley.desc`]: 'Flick three thorns in a narrow fan at the crosshair: 5 damage each and Poison I for 2.5 s. 5 Chlorophyll, 0.5 s cooldown. Shift+R: Vine Lash -- a thorned vine whips out up to 14 blocks for 12 damage and yanks the creature to your feet (a boss is not moved but takes +4). 12 Chlorophyll, 3 s cooldown.',
	[`${P}.ability.thorn_volley.shift`]: 'Vine Lash',
	// G
	[`${P}.ability.root_snare`]: 'Root Snare',
	[`${P}.ability.root_snare.desc`]: 'Roots erupt under the creature you look at (20 blocks) -- or, aimed at the ground, under every enemy within 2.5 of the spot: 6 damage, then pinned in place for 4 s, bleeding 2 a second (bosses are only slowed). 18 Chlorophyll, 6 s cooldown. Shift+G: Spore Cloud -- a 4.5-block cloud just ahead for 8 s: enemies inside are poisoned, blinded, slowed and lulled (they lose track of you); you and your squad heal. 22 Chlorophyll, 14 s cooldown.',
	[`${P}.ability.root_snare.shift`]: 'Spore Cloud',
	[`${P}.ability.root_snare.hold`]: 'Thorn Sentry',
	[`${P}.ability.root_snare.hold.desc`]: 'Hold G: plant a living turret where you look. For 15 s it spits thorns at the nearest hostile twice a second (5 damage and a short Poison) -- never at you, your squad or any player. One at a time. 30 Chlorophyll, 20 s cooldown.',
	// X
	[`${P}.ability.vine_grapple`]: 'Vine Grapple',
	[`${P}.ability.vine_grapple.desc`]: 'Shoot a vine at the block you look at (48 blocks) and it reels you in, with no fall damage for 10 s -- or, aimed at a creature within 30, reels it to you. 6 Chlorophyll, 0.75 s cooldown. Shift+X: Leaf Glide (toggle) -- a canopy of leaves opens over you for elytra-style gliding: steer with your look, dive for speed. Hops you up from the ground. 5 Chlorophyll to open, then 2 a second; it folds on landing, in water or when the meter runs dry.',
	[`${P}.ability.vine_grapple.shift`]: 'Leaf Glide',
	// Z (name + desc replaced above)
	[`${P}.ability.overgrowth.shift`]: 'Treant',
	// V
	[`${P}.ability.bloom_burst`]: 'Bloom Burst',
	[`${P}.ability.bloom_burst.desc`]: 'A ring of glowing flowers blooms around you for 6 s (6 blocks): you and your squad heal 2 a second, poison, wither and hunger are cleansed at once and crops in the ring are bone-mealed. As the flowers wilt they fruit: 2 sweet berries, sometimes an apple. 25 Chlorophyll, 14 s cooldown. Shift+V: Living Architecture -- grows what your look asks for: at a wall, a Vine Ladder up it (real vines, 16 high, 8); looking down, a Root Bridge of mangrove roots 10 blocks ahead (12); looking up, a Grow Shelter -- a lasting 5x5 leaf hut with a doorway and a light (40, 30 s cooldown); straight ahead at nothing, a Bramble Wall that hurts and slows enemies for 12 s (15).',
	[`${P}.ability.bloom_burst.shift`]: 'Living Architecture',
	// C
	[`${P}.ability.verdant_form`]: 'Verdant Form',
	[`${P}.ability.verdant_form.desc`]: 'Toggle: your skin turns to bark and leaves -- +6 armour, +2 toughness, harder to knock back, and anything that hits you in melee takes 3 thorns damage. Photosynthesis: in sunlight you heal 1 every 2 s and slowly refill hunger; out of the sun it costs 1 Chlorophyll a second. Shift+C: Root Sense -- your roots spread 24 blocks through the ground: crops (and how many are ripe), saplings and every ore are counted in chat, the nearest valuable ore is pointed out, and glowing sap trails only you can see run to the best 8 for 6 s. 15 Chlorophyll, 8 s cooldown.',
	[`${P}.ability.verdant_form.shift`]: 'Root Sense',
	// passives
	[`${P}.passive.chlorophyll`]: 'Chlorophyll (0-100) powers every move and refills itself: 8 a second in sunlight, 5 in the rain, 3 at night, 4 under a bright lamp and 2 in the dark -- half again on living ground and a quarter more in forests, jungles and swamps. Eating fruit gives +12 and extra food.',
	[`${P}.passive.green_thumb`]: 'Green Thumb (empty hand): right-click a ripe crop to harvest and replant it, a plant to bone-meal it twice, a flower to take a copy of it; Sneak + right-click dirt to grass it, stone to moss it, or an animal to feed it into love (and maybe tame it).',
	[`${P}.passive.tree_feller`]: 'Tree Feller: Sneak while breaking a log and the whole tree comes down at once, its wood dropped at the stump and a matching sapling replanted.',
	[`${P}.passive.living_ground`]: 'Your attacks are 20% stronger on living ground, and your Treant, sentries and spores never turn on you or your squad.',
	// messages
	'message.projecthero.chloro.low': 'Not enough Chlorophyll (%s needed)',
	'message.projecthero.chloro.no_target': 'Nothing there for the roots to take',
	'message.projecthero.chloro.no_grief': 'Living Architecture needs ability terrain changes turned on',
	'message.projecthero.chloro.no_room': 'No room to grow that here',
	'message.projecthero.chloro.verdant_wilts': 'Your bark wilts -- out of Chlorophyll',
	'message.projecthero.chloro.sense.head': 'Your roots spread %s blocks through the ground...',
	'message.projecthero.chloro.sense.plants': 'Crops: %s (%s ripe)  ·  Saplings: %s',
	'message.projecthero.chloro.sense.ores': 'Ores: %s',
	'message.projecthero.chloro.sense.no_ores': 'No ore within reach.',
	'message.projecthero.chloro.sense.nearest': 'Nearest: %s, %s blocks %s, %s -- follow the sap',
	'message.projecthero.chloro.sense.below': '%s below you',
	'message.projecthero.chloro.sense.above': '%s above you',
	'message.projecthero.chloro.sense.bar': 'Roots: %s crops, %s ore blocks',
	'message.projecthero.chloro.ore.ancient_debris': 'ancient debris',
	'message.projecthero.chloro.ore.diamond': 'diamond',
	'message.projecthero.chloro.ore.emerald': 'emerald',
	'message.projecthero.chloro.ore.gold': 'gold',
	'message.projecthero.chloro.ore.lapis': 'lapis',
	'message.projecthero.chloro.ore.redstone': 'redstone',
	'message.projecthero.chloro.ore.iron': 'iron',
	'message.projecthero.chloro.ore.copper': 'copper',
	'message.projecthero.chloro.ore.coal': 'coal',
	// entities
	'entity.projecthero.treant': 'Treant',
	'entity.projecthero.chloro_fx': 'Growth',
	// guidebook additions (the power chapter)
	'projecthero.guide.chlorokinesis.holds': 'Hold moves',
	'projecthero.guide.chlorokinesis.meter': 'Chlorophyll',
	'projecthero.guide.chlorokinesis.meter.body': 'Every move costs Chlorophyll and needs its full cost. The meter is a green bar above your keys and it refills on its own: fastest in sunlight, slower in rain, at night, underground or indoors (a bright lamp helps), half again faster standing on grass, moss or leaves and a quarter faster in forests, jungles and swamps. Eat fruit -- sweet berries, glow berries, apples, melon -- for a quick +12. Each key has a tap move and a Shift move with their own cooldowns: the tap move shows in the key box, the Shift move as the thin strip along its bottom.',
	'projecthero.guide.chlorokinesis.survival': 'Living off the land',
	'projecthero.guide.chlorokinesis.survival.body': 'With an empty hand: right-click ripe crops to harvest and replant them in one go, other plants to grow them, flowers to take a copy (endless dye). Sneak + right-click dirt to turn it to grass, stone to grow moss, animals to breed them (wolves, cats, parrots and horses may take to you). Sneak while chopping a log to fell the whole tree -- it replants itself. Overgrowth ripens every field around you, Bloom Burst fruits as it wilts, Verdant Form feeds you in the sun, Root Sense finds ore and crops, and Living Architecture grows vine ladders, root bridges and a leaf shelter for the night.',
};

let text = fs.readFileSync(FILE, 'utf8');
let replaced = 0;
for (const [k, v] of Object.entries(REPLACE)) {
	const re = new RegExp('^(\\s*)' + JSON.stringify(k).replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ':\\s*"(?:[^"\\\\]|\\\\.)*"', 'm');
	if (!re.test(text)) throw new Error('missing key to replace: ' + k);
	text = text.replace(re, (m, ws) => ws + JSON.stringify(k) + ': ' + JSON.stringify(v));
	replaced++;
}
const lines = [];
for (const [k, v] of Object.entries(ADD)) {
	if (text.includes(JSON.stringify(k) + ':')) {
		throw new Error('key already present: ' + k);
	}
	lines.push('  ' + JSON.stringify(k) + ': ' + JSON.stringify(v));
}
const end = text.lastIndexOf('}');
let head = text.slice(0, end).replace(/\s*$/, '');
if (!head.endsWith(',') && !head.endsWith('{')) head += ',';
text = head + '\n' + lines.join(',\n') + '\n}\n';
fs.writeFileSync(FILE, text);
JSON.parse(text);
console.log('replaced ' + replaced + ', added ' + lines.length);
