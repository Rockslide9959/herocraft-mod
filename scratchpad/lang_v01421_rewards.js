// v0.14.21: lapis and other valuables in every horde and raid reward -- guidebook reward text + the Darkseid plunder
// chat line. Idempotent: run it as often as you like (each edit checks whether it is already in).
const fs = require('fs');
const f = 'src/main/resources/assets/projecthero/lang/en_us.json';
const j = JSON.parse(fs.readFileSync(f, 'utf8'));

/** Insert {@code add} before {@code anchor} in {@code key}, unless {@code marker} is already there. */
const insertBefore = (key, anchor, add, marker) => {
	const v = j[key];
	if (v === undefined) throw new Error('missing ' + key);
	if (v.includes(marker)) return;
	if (!v.includes(anchor)) throw new Error(key + ': anchor not found: ' + anchor);
	j[key] = v.replace(anchor, add + anchor);
};
const append = (key, add, marker) => {
	const v = j[key];
	if (v === undefined) throw new Error('missing ' + key);
	if (v.includes(marker)) return;
	j[key] = v + add;
};

insertBefore('projecthero.guide.hordes.rewards.body', ' More surviving fighters fill the chest further.',
	' Every chest also holds lapis lazuli, redstone and amethyst shards: the Skeleton chest a 40% chance of lapis blocks, the'
	+ ' Spider chest 2-4 lapis blocks and a 25% chance of ancient debris.', 'lapis lazuli, redstone and amethyst');

insertBefore('projecthero.guide.zombie_raid.rewards.body', ' Your first clear also yields',
	' Every Powered Zombie Boss also drops lapis, redstone, iron, gold, emeralds, amethyst and (from the middle waves on)'
	+ ' diamonds -- more the later the wave, and the final boss adds golden apples, a lapis block and an enchanted book. The'
	+ ' chest itself carries a full clear\'s haul of lapis (and lapis blocks), diamonds, gold, iron, redstone, emeralds,'
	+ ' amethyst, golden apples and an enchanted book, with a chance of netherite scrap and ancient debris; a bigger party'
	+ ' gets more (up to double for four or more).', 'Every Powered Zombie Boss also drops lapis');

insertBefore('projecthero.guide.supervillain_raid.rewards.body', ' Everyone who fought becomes Champion',
	' The victory also scatters lapis lazuli, redstone, iron, gold, diamonds, amethyst, golden apples and an enchanted book'
	+ ' (a 30% lapis block, a 5% enchanted golden apple) -- more for a bigger party.', 'The victory also scatters lapis');

insertBefore('projecthero.guide.darkseid_raid.rewards.body', ' Advancements:',
	' Each also gets a pile of Apokolips plunder: lapis and lapis blocks, diamonds, gold, iron, redstone, amethyst, emeralds,'
	+ ' golden apples and two enchanted books, with a chance of netherite scrap, ancient debris and an enchanted golden'
	+ ' apple (fewer invasion waves configured, less plunder).', 'Apokolips plunder');

append('projecthero.guide.titan.rewards.body',
	' It drops emeralds, diamonds, iron, lapis lazuli, redstone and gold, and sometimes a lapis block.', 'lapis lazuli, redstone and gold');

if (!j['message.projecthero.darkseid_raid.valuables']) {
	// insert next to the existing reward line so the file stays grouped
	const out = {};
	for (const [k, v] of Object.entries(j)) {
		out[k] = v;
		if (k === 'message.projecthero.darkseid_raid.reward') {
			out['message.projecthero.darkseid_raid.valuables'] = 'Apokolips plunder: %s ores, gems, golden apples and books';
		}
	}
	if (!out['message.projecthero.darkseid_raid.valuables']) {
		out['message.projecthero.darkseid_raid.valuables'] = 'Apokolips plunder: %s ores, gems, golden apples and books';
	}
	fs.writeFileSync(f, JSON.stringify(out, null, 2) + '\n');
} else {
	fs.writeFileSync(f, JSON.stringify(j, null, 2) + '\n');
}
console.log('ok');
