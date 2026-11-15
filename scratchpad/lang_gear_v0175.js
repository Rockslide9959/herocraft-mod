// v0.17.5 gear tiers I-IV: adds the en_us keys key by key (never rewrites the file: other branches touch it too).
// Keeps the file's line endings and 2-space indent. Existing keys are left alone unless --force.
//   node scratchpad/lang_gear_v0175.js [root] [--force]
const fs = require('fs');
const path = require('path');
const args = process.argv.slice(2);
const force = args.includes('--force');
const root = args.find(a => !a.startsWith('--')) || process.cwd();
const file = path.join(root, 'src/main/resources/assets/projecthero/lang/en_us.json');

const K = {
  'projecthero.gear.tier.1': 'Tier I gear: Salvaged Tech',
  'projecthero.gear.tier.2': 'Tier II gear: Hero Alloy',
  'projecthero.gear.tier.3': 'Tier III gear: Vibranium',
  'projecthero.gear.tier.4': 'Tier IV gear: Celestial',
  // Tier I gadgets
  'item.projecthero.grapple_line': 'Grapple Line',
  'item.projecthero.grapple_line.hint': 'Right-click to fire a hook up to 24 blocks and reel yourself to the block it bites. Sneak to let go, fire again to re-hook in mid-air. Repair with Salvaged Tech.',
  'item.projecthero.smoke_bomb': 'Smoke Bomb',
  'item.projecthero.smoke_bomb.hint': 'Right-click to throw. Bursts into a smoke cloud (4 blocks, 7 s): mobs inside are blinded and lose track of their target.',
  'item.projecthero.flashbang': 'Flashbang',
  'item.projecthero.flashbang.hint': 'Right-click to throw. A blinding flash: mobs within 5 blocks are blinded for 5 s, lose their target and are stunned for 2 s.',
  'projecthero.gear.grapple.no_hold': 'Nothing in range to hook.',
  // Tier II
  'item.projecthero.hero_alloy_helmet': 'Hero Alloy Helmet',
  'item.projecthero.hero_alloy_chestplate': 'Hero Alloy Chestplate',
  'item.projecthero.hero_alloy_leggings': 'Hero Alloy Leggings',
  'item.projecthero.hero_alloy_boots': 'Hero Alloy Boots',
  'projecthero.gear.set.hero_alloy.hint': 'Light dark-steel plate, tougher than iron. Repair with Hero Alloy Ingots.',
  'item.projecthero.hero_alloy_sword': 'Hero Alloy Sword',
  'item.projecthero.hero_alloy_sword.hint': 'A balanced blade between iron and diamond. Upgrade it to a Vibranium Blade at a smithing table.',
  'item.projecthero.alloy_baton': 'Alloy Baton',
  'item.projecthero.alloy_baton.hint': 'A fast fighting stick. Every hit staggers the target (slowed for a moment).',
  'item.projecthero.utility_belt': 'Utility Belt',
  'item.projecthero.utility_belt.hint': 'Keep it anywhere in your inventory: every gadget gets one spare charge (use two in a row) and 30% shorter cooldowns.',
  // Tier III
  'item.projecthero.vibranium_helmet': 'Vibranium Helmet',
  'item.projecthero.vibranium_chestplate': 'Vibranium Chestplate',
  'item.projecthero.vibranium_leggings': 'Vibranium Leggings',
  'item.projecthero.vibranium_boots': 'Vibranium Boots',
  'projecthero.gear.set.vibranium.hint': 'Better than diamond, and Vibranium soaks impacts: each piece takes 12.5% off fall damage (a full set halves it).',
  'item.projecthero.vibranium_blade': 'Vibranium Blade',
  'item.projecthero.vibranium_blade.hint': 'A light, keen Vibranium sword that never dulls fast. Smithed from a Hero Alloy Sword and a Vibranium Ingot.',
  'item.projecthero.vibranium_grapple': 'Vibranium Grapple',
  'item.projecthero.vibranium_grapple.hint': 'The upgraded Grapple Line: 40 blocks of range, a faster reel, a shorter cooldown and far more uses. Repair with Vibranium Shards.',
  'item.projecthero.vibranium_smoke_bomb': 'Vibranium Smoke Bomb',
  'item.projecthero.vibranium_smoke_bomb.hint': 'A bigger, longer smoke cloud (6 blocks, 11 s): mobs inside are blinded and lose their target.',
  'item.projecthero.vibranium_flashbang': 'Vibranium Flashbang',
  'item.projecthero.vibranium_flashbang.hint': 'A wider flash: mobs within 8 blocks are blinded for 8 s, lose their target and are stunned for 3.5 s.',
  // Tier IV
  'item.projecthero.celestial_helmet': 'Celestial Helmet',
  'item.projecthero.celestial_chestplate': 'Celestial Chestplate',
  'item.projecthero.celestial_leggings': 'Celestial Leggings',
  'item.projecthero.celestial_boots': 'Celestial Boots',
  'projecthero.gear.set.celestial.hint': 'Plate forged around a Celestial Core: tougher than netherite, its trims glow, and like Vibranium each piece takes 12.5% off fall damage.',
  'projecthero.gear.set.celestial.bonus': 'Full set: +2 hearts, and immune to Blindness and Darkness.',
  'item.projecthero.celestial_warhammer': 'Celestial Warhammer',
  'item.projecthero.celestial_warhammer.hint': 'A huge, slow hammer. Hold right-click to raise it, let go to slam the ground: everything within 5 blocks takes up to 14 damage and is thrown back. 8 s cooldown.',
  'item.projecthero.celestial_upgrade_template': 'Celestial Upgrade',
  'item.projecthero.celestial_upgrade_template.hint': 'Smithing template, found only in Dungeon Vaults. With a Celestial Core it turns a Vibranium piece or the Vibranium Blade into Celestial gear. Copy it with a Celestial Core and seven Vibranium Shards.',
  // entities
  'entity.projecthero.grapple_hook': 'Grapple Hook',
  'entity.projecthero.thrown_gadget': 'Thrown Gadget',
  'entity.projecthero.smoke_cloud': 'Smoke Cloud',
  // Guidebook (a section of the Loot & Progression chapter)
  'projecthero.guide.loot.gear': 'Gear Tiers',
  'projecthero.guide.loot.gear.body': 'Hero gear is for anyone, with or without a power. Each tier is made from that tier\'s material, and each tier upgrades the one before it, so nothing you build is wasted. Vaults sometimes hold a piece, but crafting is the main road.',
  'projecthero.guide.loot.gear.tier1': 'Tier I, Salvaged Tech (Hero Caches): gadgets used from the hotbar with right-click, not the power keys. Grapple Line (tech, tripwire hook, string, iron), Smoke Bomb and Flashbang (four at a time from tech, gunpowder, string and coal or glowstone).',
  'projecthero.guide.loot.gear.tier2': 'Tier II, Hero Alloy (Site Vaults): a full armour set between iron and diamond, the Hero Alloy Sword, the Alloy Baton (every hit staggers) and the Utility Belt (carry it for a spare gadget charge and 30% shorter gadget cooldowns). Crafted from Hero Alloy Ingots like iron gear.',
  'projecthero.guide.loot.gear.tier3': 'Tier III, Vibranium (Hideout Vaults): smith a Hero Alloy piece with a Vibranium Ingot (four shards) for Vibranium armour, better than diamond and cutting fall damage, or the sword into the Vibranium Blade. A gadget plus one Vibranium Shard upgrades it (bombs also upgrade four at a time around a shard).',
  'projecthero.guide.loot.gear.tier4': 'Tier IV, Celestial (Dungeon Vaults): the Celestial Upgrade template, a Celestial Core and a Vibranium piece make Celestial armour, above netherite with glowing trims. Wear all four for +2 hearts and immunity to Blindness and Darkness. The Vibranium Blade becomes the Celestial Warhammer: hold right-click and let go to slam the ground.',
  'projecthero.jei.gear': 'Hero gear works for everyone, power or not. Crafted from the tier material; found now and then in Hero Vaults.',
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
  const close = text.lastIndexOf('}');
  let body = text.slice(0, close).replace(/\s+$/, '');
  body += ',' + eol + add.join(',' + eol) + eol;
  text = body + '}' + text.slice(close + 1);
}
JSON.parse(text);
fs.writeFileSync(file, text);
console.log(`en_us: +${add.length} keys, ${replaced} replaced`);
