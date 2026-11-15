// v0.17.5 Tier 1 Finds: adds / updates en_us keys one by one (never rewrites the file's other keys). Handles CRLF.
// Run from the repo root: node scratchpad/lang_finds_v0175.js
const fs = require('fs');
const file = 'src/main/resources/assets/projecthero/lang/en_us.json';
let s = fs.readFileSync(file, 'utf8');
const nl = s.includes('\r\n') ? '\r\n' : '\n';

const keys = {
  'structure.projecthero.crashed_drone': 'Crashed Drone',
  'structure.projecthero.hidden_cache': 'Hidden Cache',
  'structure.projecthero.hero_outpost': 'Abandoned Hero Outpost',
  'entity.projecthero.supply_drop': 'Supply Drop',
  'projecthero.find.hidden_cache.sign1': 'X',
  'projecthero.find.hidden_cache.sign2': 'dig here',
  'projecthero.find.hero_outpost.journal.intro0': 'Day one of retirement. Hung up the cape, kept the notes.\n\nIf you found this place, you have the itch too. Read on, kid, and do better than I did.',
  'projecthero.find.hero_outpost.journal.intro1': 'Twenty years they called me a hero. My knees call me other things now.\n\nI left this outpost to whoever comes next. That is you, I guess.',
  'projecthero.find.hero_outpost.journal.intro2': 'Last entry before I go. The suit is buried. The bad guys are not.\n\nSomeone has to keep the trail warm, so here is what I learned.',
  'projecthero.find.hero_outpost.journal.p2': 'The world is full of crashed tech and old caches like this one. Crack the Hero Vaults: everyone gets their own share, and Salvaged Tech is where real gear starts.',
  'projecthero.find.hero_outpost.journal.p3': 'Now and then a vault hands you a Locator. Right-click it and follow the needle to a Site: the old labs and craters. Bigger vault. Guards that fight back.',
  'projecthero.find.hero_outpost.journal.p4': 'Sites point to Hideouts. Every villain crew keeps a lair. Clear one, smash its core, and their raids go quiet round there for a while.',
  'projecthero.find.hero_outpost.journal.p5': 'Under it all is the Vault, a prison for the worst there is. I never went down.\n\nBring friends, bring supplies, and watch the sky for drops.',
  'projecthero.supply_drop.incoming': 'A supply drop is coming down to the %s, about %s blocks away. Look for the smoke!',
  'projecthero.supply_drop.dir.n': 'north',
  'projecthero.supply_drop.dir.ne': 'north-east',
  'projecthero.supply_drop.dir.e': 'east',
  'projecthero.supply_drop.dir.se': 'south-east',
  'projecthero.supply_drop.dir.s': 'south',
  'projecthero.supply_drop.dir.sw': 'south-west',
  'projecthero.supply_drop.dir.w': 'west',
  'projecthero.supply_drop.dir.nw': 'north-west',
  'projecthero.supply_drop.sent': 'Sent %s supply drop(s).',
  'projecthero.supply_drop.failed.disabled': 'Supply drops are turned off.',
  'projecthero.supply_drop.failed.not_overworld': '%s is not in the Overworld.',
  'projecthero.supply_drop.failed.no_spot': 'No dry, loaded ground 40-80 blocks from %s.',
  'gamerule.projectheroSupplyDrops': 'Supply drops',
  'gamerule.projectheroSupplyDrops.description': 'Every few in-game days a Hero Vault crate parachutes down near each Overworld player.',
  'gamerule.projectheroSupplyDropDays': 'Days between supply drops',
  'gamerule.projectheroSupplyDropDays.description': 'Each player gets a supply drop every N to N+1 in-game days.',
  'projecthero.guide.loot.tier1': 'Tier 1 Finds: crashed drones, hidden caches, abandoned hero outposts and supply drops. A Hero Cache inside.',
  'projecthero.guide.loot.finds': 'Tier 1 Finds',
  'projecthero.guide.loot.finds.body': 'Small places all over the Overworld, common enough to run into on any trip. Each holds a Hero Cache (a Tier 1 vault) with Salvaged Tech, a supply or two and sometimes a Site Locator.',
  'projecthero.guide.loot.find.crashed_drone': 'Crashed Drone: a wrecked tech drone in a scorched crater, its engine still smoking. The vault sits in its open cargo bay. Anywhere on land.',
  'projecthero.guide.loot.find.hidden_cache': 'Hidden Cache: a cairn, a red flag or a sign marked X on a patch of churned earth. Dig three blocks straight down under it to find a small room with the vault. Plains, forests and deserts.',
  'projecthero.guide.loot.find.hero_outpost': 'Abandoned Hero Outpost: a ruined, overgrown hideout with a cot, a map table and a lectern holding a retired hero\'s field notes. A few zombies and skeletons still linger there.',
  'projecthero.guide.loot.find.supply_drop': 'Supply Drop: every two to three in-game days a crate parachutes down 40 to 80 blocks from you, trailing smoke, and chat tells you roughly which way. Usually a Hero Cache, sometimes a Site Vault. It is collected again 20 minutes after landing (5 minutes after someone first opens it). Turn them off with /gamerule projectheroSupplyDrops false.',
  'projecthero.jei.finds_where': 'Hero Caches (Tier 1) are in Crashed Drones, Hidden Caches, Abandoned Hero Outposts and Supply Drops. Site Vaults are in the old sites.',
};

function esc(v) {
  return JSON.stringify(v);
}

let added = 0, updated = 0;
for (const [k, v] of Object.entries(keys)) {
  const re = new RegExp('^(\\s*)' + esc(k).replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\s*:\\s*"(?:[^"\\\\]|\\\\.)*"', 'm');
  if (re.test(s)) {
    s = s.replace(re, (m, ind) => ind + esc(k) + ': ' + esc(v));
    updated++;
  } else {
    // append before the closing brace
    const end = s.lastIndexOf('}');
    let head = s.slice(0, end).replace(/\s*$/, '');
    s = head + ',' + nl + '  ' + esc(k) + ': ' + esc(v) + nl + '}' + s.slice(end + 1);
    added++;
  }
}
JSON.parse(s);
fs.writeFileSync(file, s);
console.log('added', added, 'updated', updated);
