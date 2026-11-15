// v0.17.5 Tier 2 Sites: adds / updates en_us keys one by one (never rewrites the file's other keys). Handles CRLF.
// Run from the repo root: node scratchpad/lang_sites_v0175.js
const fs = require('fs');
const file = 'src/main/resources/assets/projecthero/lang/en_us.json';
let s = fs.readFileSync(file, 'utf8');
const nl = s.includes('\r\n') ? '\r\n' : '\n';

const keys = {
  'entity.projecthero.site_agent': 'Government Agent',
  'entity.projecthero.site_soldier': 'Soldier',
  'entity.projecthero.site_cultist': 'Cultist',
  'projecthero.guide.loot.tier2': 'Tier 2 Sites: the 19 old labs, craters, temples and crash sites, each with a new annex, a guard squad and a Site Vault.',
  'projecthero.guide.loot.sites': 'Tier 2 Sites',
  'projecthero.guide.loot.sites.body': 'Every old site still does what it always did (its power, item or event is unchanged), but each now has an annex built beside it, facing the site: a barracks, lab wing, plant room, camp, field station, mausoleum, shrine or ice hut. Inside is a Site Vault (Tier 2) in a secured room, either behind an iron door (press the button beside it) or in a hidden basement under a rug: break the carpet, open the trapdoor and climb down. A squad of 3 to 6 guards that fits the site holds the annex; they never despawn and do not burn in daylight. Site Vaults often hold a Hideout Locator.',
  'projecthero.guide.loot.site.dig_site': 'Dig Site: a timber field camp with a hidden basement vault. Syndicate looters.',
  'projecthero.guide.loot.site.fallen_lantern_site': 'Fallen Lantern Site: a green-glassed field station, vault behind an iron door. Parademon scouts.',
  'projecthero.guide.loot.site.gamma_lab': 'Gamma Lab: a military barracks, vault behind an iron door. Soldiers.',
  'projecthero.guide.loot.site.geological_site': 'Geological Site: a miners\' camp with a hidden basement vault. Syndicate smugglers.',
  'projecthero.guide.loot.site.government_site': 'Government Site: a guard barracks, vault behind an iron door. Government agents and a rifleman.',
  'projecthero.guide.loot.site.graveyard': 'Graveyard: a mausoleum with a hidden crypt vault. Risen zombies and sword skeletons.',
  'projecthero.guide.loot.site.hydrostatic_facility': 'Hydrostatic Facility: a concrete pump house, vault behind an iron door. Syndicate scavengers.',
  'projecthero.guide.loot.site.kryptonite_crater': 'Kryptonite Crater: a government research station, vault behind an iron door. Agents.',
  'projecthero.guide.loot.site.meteor_impact': 'Meteor Impact: a field station, vault behind an iron door. Parademons.',
  'projecthero.guide.loot.site.mjolnir_crater': 'Mjolnir Crater: a Frost Giant ice hut with a hidden basement vault. Jotun Raiders and Rime Hounds.',
  'projecthero.guide.loot.site.nova_pod_site': 'Nova Pod Site: a salvage station, vault behind an iron door. Ultron drones.',
  'projecthero.guide.loot.site.power_station': 'Power Station: a generator room, vault behind an iron door. Ultron drones.',
  'projecthero.guide.loot.site.research_facility': 'Research Facility: a white lab wing, vault behind an iron door. Rogue Ultron drones.',
  'projecthero.guide.loot.site.steel_crash_site': 'Steel Crash Site: a field station, vault behind an iron door. Ultron drones.',
  'projecthero.guide.loot.site.symbiote_lab': 'Symbiote Lab: an overrun containment wing, vault behind an iron door. Symbiote hosts.',
  'projecthero.guide.loot.site.symbiote_meteor': 'Symbiote Meteor: an overrun research post with a hidden basement vault. Symbiote hosts.',
  'projecthero.guide.loot.site.temple_of_khonshu': 'Temple of Khonshu: a sandstone shrine with a hidden crypt vault. Cultists and Tomb Guardians.',
  'projecthero.guide.loot.site.tomb_of_nabu': 'Tomb of Nabu: a sandstone shrine with a hidden crypt vault. Cultists (on top of the tomb\'s own guardians).',
  'projecthero.guide.loot.site.vigilante_safehouse': 'Vigilante Safehouse: a Syndicate shack over the bunker, vault behind an iron door. Syndicate gunmen.',
};

const esc = v => JSON.stringify(v).slice(1, -1);
let added = 0, updated = 0;
for (const [k, v] of Object.entries(keys)) {
  const re = new RegExp('("' + k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '"\\s*:\\s*)"(?:[^"\\\\]|\\\\.)*"');
  if (re.test(s)) {
    s = s.replace(re, (_, a) => a + '"' + esc(v) + '"');
    updated++;
  } else {
    const end = s.lastIndexOf('}');
    let body = s.slice(0, end).replace(/\s+$/, '');
    s = body + ',' + nl + '  "' + k + '": "' + esc(v) + '"' + nl + '}' + nl;
    added++;
  }
}
JSON.parse(s);
fs.writeFileSync(file, s);
console.log('added', added, 'updated', updated);
