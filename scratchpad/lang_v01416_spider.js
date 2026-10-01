// v0.14.16 Spider Horde: brood spider variant names, the rebuilt Brood Queen's phase names, the guidebook entry.
// Usage (from the repo root): node scratchpad/lang_v01416_spider.js   -- idempotent, keeps the file's CRLF + 2-space indent.
const fs = require('fs');
const path = require('path');
const p = path.join(__dirname, '..', 'src/main/resources/assets/projecthero/lang/en_us.json');
const text = fs.readFileSync(p, 'utf8');
const crlf = text.includes('\r\n');
const lang = JSON.parse(text);

const set = {
	'entity.projecthero.brood_spider': 'Brood Spider',
	'entity.projecthero.brood_spider.hunter': 'Hunter Spider',
	'entity.projecthero.brood_spider.brute': 'Ironback Brute',
	'entity.projecthero.brood_spider.venom': 'Venom Spitter',
	'entity.projecthero.brood_spider.burster': 'Acid Burster',
	'entity.projecthero.brood_spider.leaper': 'Trapdoor Leaper',
	'entity.projecthero.brood_spider.broodmother': 'Broodmother',
	'entity.projecthero.brood_spider.stalker': 'Shadow Stalker',
	'entity.projecthero.brood_spider.spiderling': 'Spiderling',
	'boss.projecthero.brood_queen.phase1': 'The Brood Queen',
	'boss.projecthero.brood_queen.phase2': 'The Brood Queen - Mother of the Swarm',
	'boss.projecthero.brood_queen.phase3': 'The Brood Queen - Frenzied',
	'projecthero.guide.hordes.spider.body': 'Crafting: fill the crafting grid with spider eyes. The hardest horde. '
		+ 'Web-spitting Horde Spiders and cave spiders come first, then the brood: fast orange Hunters, green Venom Spitters '
		+ 'that poison from range, purple Trapdoor Leapers (they crouch before they jump), yellow Acid Bursters (they hiss and '
		+ 'swell when close -- back off or kill them from range), iron-plated Ironback Brutes, invisible Shadow Stalkers (only '
		+ 'their eyes show, and their first bite hits double) and huge Broodmothers that burst into spiderlings when they die. '
		+ 'Boss: the Brood Queen, a five-block spider queen with 4,000 health and three phases, each opened by a shriek that '
		+ 'throws you back and darkens your sight. Watch her tells: she rears up before a Fang Lunge, a red ring marks a Leg '
		+ 'Sweep or where a Leap Slam lands, her abdomen rises before a Web Volley (the centre web cocoons you) and pumps '
		+ 'before Acid Rain (step off the bubbling marks), and it swells before an Egg Burst of spiderlings. From phase two '
		+ 'she climbs a web line into the sky and drops onto the ring beneath her; in phase three she lunges three times in a row.',
};
let changed = 0;
for (const [k, v] of Object.entries(set)) {
	if (lang[k] !== v) {
		lang[k] = v;
		changed++;
	}
}
let out = JSON.stringify(lang, null, 2) + '\n';
if (crlf) out = out.replace(/\n/g, '\r\n');
fs.writeFileSync(p, out);
console.log(`lang v0.14.16 spider: ${changed} keys set`);
