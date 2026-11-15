// v0.17.5 loot framework: adds the en_us keys key by key (never rewrites the file: other branches touch it too).
// Keeps the file's CRLF line endings and 2-space indent. Existing keys are left alone unless --force.
//   node scratchpad/lang_loot_foundation_v0175.js [root] [--force]
const fs = require('fs');
const path = require('path');
const args = process.argv.slice(2);
const force = args.includes('--force');
const root = args.find(a => !a.startsWith('--')) || process.cwd();
const file = path.join(root, 'src/main/resources/assets/projecthero/lang/en_us.json');

const K = {
  // rarity
  'projecthero.rarity.common': 'Common',
  'projecthero.rarity.uncommon': 'Uncommon',
  'projecthero.rarity.rare': 'Rare',
  'projecthero.rarity.epic': 'Epic',
  'projecthero.rarity.legendary': 'Legendary',
  // Hero Vault
  'block.projecthero.hero_vault': 'Hero Vault',
  'block.projecthero.hero_vault.tier1': 'Hero Cache',
  'block.projecthero.hero_vault.tier2': 'Site Vault',
  'block.projecthero.hero_vault.tier3': 'Hideout Vault',
  'block.projecthero.hero_vault.tier4': 'Dungeon Vault',
  'block.projecthero.hero_vault.hint': 'Right-click to take your share. Every player gets their own loot, once.',
  'block.projecthero.hero_vault.looted': 'You have already looted this vault.',
  'block.projecthero.hero_vault.opened': '%s opened!',
  // materials
  'projecthero.loot.material_tier.1': 'Tier I material',
  'projecthero.loot.material_tier.2': 'Tier II material',
  'projecthero.loot.material_tier.3': 'Tier III material',
  'projecthero.loot.material_tier.4': 'Tier IV material',
  'item.projecthero.salvaged_tech': 'Salvaged Tech',
  'item.projecthero.salvaged_tech.hint': 'Circuit boards and plating pulled from wrecks and caches. Four of them and an iron ingot make a Hero Alloy Ingot.',
  'item.projecthero.hero_alloy_scrap': 'Hero Alloy Scrap',
  'item.projecthero.hero_alloy_scrap.hint': 'Offcuts of Hero Alloy. Nine make an ingot.',
  'item.projecthero.hero_alloy_ingot': 'Hero Alloy Ingot',
  'item.projecthero.hero_alloy_ingot.hint': 'A light, tough alloy for Tier II hero gear. Found in Site Vaults, or crafted from Salvaged Tech and iron.',
  'item.projecthero.vibranium_shard': 'Vibranium Shard',
  'item.projecthero.vibranium_shard.hint': 'A splinter of Vibranium from villain hideouts. Four shards make a Vibranium Ingot.',
  'item.projecthero.celestial_core': 'Celestial Core',
  'item.projecthero.celestial_core.hint': 'A humming core of cosmic energy, only found in the deepest Dungeon Vaults. The heart of Tier IV gear.',
  // supplies
  'projecthero.loot.supply_use': 'Hold right-click to use.',
  'item.projecthero.energy_cell': 'Energy Cell',
  'item.projecthero.energy_cell.hint': "Refills 40% of your power's main meter (Kinetic Energy, Solar Charge, Ki, Ring Charge and so on).",
  'item.projecthero.energy_cell.used': 'Power meter recharged.',
  'item.projecthero.energy_cell.full': 'Your power meter is already full.',
  'item.projecthero.energy_cell.no_meter': 'Your power has no meter to recharge.',
  'item.projecthero.cooldown_stim': 'Cooldown Stim',
  'item.projecthero.cooldown_stim.hint': 'Halves the time left on every ability that is cooling down.',
  'item.projecthero.cooldown_stim.used': 'Cooldowns cut in half.',
  'item.projecthero.cooldown_stim.refused': 'None of your abilities are cooling down.',
  'item.projecthero.suit_repair_kit': 'Suit Repair Kit',
  'item.projecthero.suit_repair_kit.hint': 'Repairs every armour piece you wear by half its durability, and restores suit integrity (Iron Man) by half.',
  'item.projecthero.suit_repair_kit.used': 'Suit repaired.',
  'item.projecthero.suit_repair_kit.refused': 'Nothing you wear needs repairing.',
  'item.projecthero.med_kit': 'Med Kit',
  'item.projecthero.med_kit.hint': 'Heals 8 hearts over 4 seconds and cures Poison and Wither.',
  'item.projecthero.med_kit.used': 'Patched up.',
  'item.projecthero.med_kit.refused': "You're already in good shape.",
  // Locator
  'item.projecthero.locator': 'Locator',
  'item.projecthero.locator.named': 'Locator: %s',
  'projecthero.locator.tier2': 'Site',
  'projecthero.locator.tier3': 'Hideout',
  'projecthero.locator.tier4': 'Dungeon',
  'item.projecthero.locator.hint': 'Right-click to search for the nearest %s.',
  'item.projecthero.locator.hint_bound': 'Sneak + right-click to search again from here.',
  'item.projecthero.locator.target': 'Tracking: %s',
  'item.projecthero.locator.distance': '%s blocks away',
  'item.projecthero.locator.distance_other': 'In another dimension',
  'item.projecthero.locator.status': '%s: %s blocks away',
  'item.projecthero.locator.other_dimension': '%s is in another dimension.',
  'item.projecthero.locator.bound': 'Locked on: %s, %s blocks away.',
  'item.projecthero.locator.not_found': 'No %s within range.',
  'item.projecthero.locator.none_exist': 'No %s can be tracked yet.',
  'item.projecthero.locator.hud': '%s  %s m',
  // structures (Locator names)
  'structure.projecthero.city': 'City',
  'structure.projecthero.dig_site': 'Dig Site',
  'structure.projecthero.fallen_lantern_site': 'Fallen Lantern Site',
  'structure.projecthero.gamma_lab': 'Gamma Lab',
  'structure.projecthero.geological_site': 'Collapsed Geological Research Site',
  'structure.projecthero.government_site': 'Government Experiment Site',
  'structure.projecthero.graveyard': 'Graveyard',
  'structure.projecthero.hydrostatic_facility': 'Hydrostatic Test Facility',
  'structure.projecthero.kryptonite_crater': 'Kryptonite Crater',
  'structure.projecthero.meteor_impact': 'Meteor Impact Site',
  'structure.projecthero.mjolnir_crater': "Mjolnir's Crater",
  'structure.projecthero.nova_pod_site': 'Nova Pod Crash Site',
  'structure.projecthero.power_station': 'Abandoned Power Station',
  'structure.projecthero.research_facility': 'Abandoned Research Facility',
  'structure.projecthero.steel_crash_site': 'Steel Crash Site',
  'structure.projecthero.symbiote_lab': 'Symbiote Lab',
  'structure.projecthero.symbiote_meteor': 'Symbiote Meteor',
  'structure.projecthero.temple_of_khonshu': 'Temple of Khonshu',
  'structure.projecthero.tomb_of_nabu': 'Tomb of Nabu',
  'structure.projecthero.vigilante_safehouse': 'Vigilante Safehouse',
  'projecthero.jei.loot_found': 'Found in Hero Vaults (Hero Caches, Site, Hideout and Dungeon Vaults); better tiers hold more of the rarer items.',
  // Guidebook chapter
  'projecthero.guide.loot': 'Loot & Progression',
  'projecthero.guide.loot.body': 'The world hides four tiers of places, each with better loot than the last, and each one points you to the next. Nothing is locked: you can stumble into a high-tier place early, it is just much harder.',
  'projecthero.guide.loot.tiers': 'The Four Tiers',
  'projecthero.guide.loot.tier1': 'Tier 1 Finds: crashed tech, supply drops and hidden caches. A Hero Cache inside.',
  'projecthero.guide.loot.tier2': 'Tier 2 Sites: the old research sites, craters and labs. A Site Vault inside.',
  'projecthero.guide.loot.tier3': 'Tier 3 Hideouts: villain lairs with guards and a mini-boss. A Hideout Vault inside.',
  'projecthero.guide.loot.tier4': 'Tier 4 Dungeons: big multi-floor gauntlets with a boss. A Dungeon Vault inside.',
  'projecthero.guide.loot.rarity': 'Rarity',
  'projecthero.guide.loot.rarity.body': "Loot comes in five rarities, shown by the colour of an item's name and a line under it in the tooltip:",
  'projecthero.guide.loot.vaults': 'Hero Vaults',
  'projecthero.guide.loot.vaults.body': "Right-click a vault and it pops out your own share of the loot. Every player gets their own roll, once per vault, so a squad never has to split one chest. A vault you have already looted stops shimmering. The vault's trim shows its tier: iron for a Hero Cache, gold for a Site Vault, purple for a Hideout Vault, aqua for a Dungeon Vault.",
  'projecthero.guide.loot.materials': 'Gear Materials',
  'projecthero.guide.loot.materials.body': 'Each tier has its own material, the base of that tier\'s hero gear: Salvaged Tech (Tier I), Hero Alloy (Tier II: four Salvaged Tech and an iron ingot, or nine Hero Alloy Scrap), Vibranium Shards (Tier III: four make a Vibranium Ingot) and the Celestial Core (Tier IV).',
  'projecthero.guide.loot.supplies': 'Hero Supplies',
  'projecthero.guide.loot.supplies.body': 'Consumables for a fight, stackable to 16. Hold right-click to use one. A supply that would do nothing is refused and kept.',
  'projecthero.guide.loot.supply.energy_cell': 'Energy Cell: refills 40% of your power\'s main meter. Powers without a meter refuse it.',
  'projecthero.guide.loot.supply.cooldown_stim': 'Cooldown Stim: halves the time left on every ability that is cooling down.',
  'projecthero.guide.loot.supply.suit_repair_kit': 'Suit Repair Kit: repairs worn armour by half its durability and Iron Man suit integrity by half.',
  'projecthero.guide.loot.supply.med_kit': 'Med Kit: heals 8 hearts over 4 seconds and cures Poison and Wither.',
  'projecthero.guide.loot.locators': 'Locators',
  'projecthero.guide.loot.locators.body': "Vaults sometimes hold a Locator for the next tier (a Hero Cache gives a Site Locator, a Site Vault a Hideout Locator, a Hideout Vault a Dungeon Locator). Right-click it to search for the nearest place of that tier: it locks on like a lodestone compass, the needle points the way, and while you hold it a line on screen shows the place's name and distance. Sneak + right-click to search again from where you stand.",
};

let text = fs.readFileSync(file, 'utf8');
const eol = text.includes('\r\n') ? '\r\n' : '\n';
const existing = JSON.parse(text);
const add = [];
let replaced = 0;
for (const [k, v] of Object.entries(K)) {
  if (k in existing) {
    if (force && existing[k] !== v) {
      const re = new RegExp('(\\n  ' + JSON.stringify(k).replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ': )(".*?(?<!\\\\)")(,?)(\\r?)$', 'm');
      text = text.replace(re, (m, a, b, c, d) => a + JSON.stringify(v) + c + d);
      replaced++;
    }
    continue;
  }
  add.push(`  ${JSON.stringify(k)}: ${JSON.stringify(v)}`);
}
if (add.length) {
  // append before the closing brace: the current last entry gets a comma
  const close = text.lastIndexOf('}');
  let body = text.slice(0, close).replace(/\s+$/, '');
  body += ',' + eol + add.join(',' + eol) + eol;
  text = body + '}' + text.slice(close + 1);
}
JSON.parse(text); // still valid JSON
fs.writeFileSync(file, text);
console.log(`en_us: +${add.length} keys, ${replaced} replaced`);
