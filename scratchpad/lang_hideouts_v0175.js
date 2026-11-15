// v0.17.5 Tier 3 villain hideouts: adds the en_us keys key by key (never rewrites the file: other branches touch it too).
// Keeps the file's line endings and 2-space indent. Existing keys are left alone unless --force.
//   node scratchpad/lang_hideouts_v0175.js [root] [--force]
const fs = require('fs');
const path = require('path');
const args = process.argv.slice(2);
const force = args.includes('--force');
const root = args.find(a => !a.startsWith('--')) || process.cwd();
const file = path.join(root, 'src/main/resources/assets/projecthero/lang/en_us.json');

const K = {
  // places
  'structure.projecthero.kingpins_front': "Kingpin's Front",
  'structure.projecthero.ultron_forge': 'Ultron Forge',
  'structure.projecthero.apokolips_outpost': 'Apokolips Outpost',
  'structure.projecthero.symbiote_hive': 'Symbiote Hive',
  'structure.projecthero.frost_giant_hall': 'Frost Giant Hall',
  // factions (raid-calm messages)
  'projecthero.hideout.faction.kingpin': 'The Syndicate',
  'projecthero.hideout.faction.ultron': 'Ultron',
  'projecthero.hideout.faction.apokolips': 'Apokolips',
  'projecthero.hideout.faction.symbiote': 'The Symbiote brood',
  'projecthero.hideout.faction.frost': 'The Frost Giants',
  // mini-bosses
  'projecthero.hideout.boss.kingpin': "Kingpin's Enforcer",
  'projecthero.hideout.boss.ultron': 'Forge Overseer',
  'projecthero.hideout.boss.apokolips': 'Parademon Commander',
  'projecthero.hideout.boss.symbiote': 'Hive Mother',
  'projecthero.hideout.boss.frost': 'Frost Giant Jarl',
  'entity.projecthero.kingpins_enforcer': "Kingpin's Enforcer",
  'entity.projecthero.forge_overseer': 'Forge Overseer',
  'entity.projecthero.parademon_commander': 'Parademon Commander',
  'entity.projecthero.hive_mother': 'Hive Mother',
  'entity.projecthero.frost_giant_jarl': 'Frost Giant Jarl',
  // blocks
  'block.projecthero.hideout_core': 'Hideout Core',
  'block.projecthero.hideout_core.sealed': 'Sealed: %s still guards this core.',
  'block.projecthero.hideout_core.open': 'The seal is down. Break the core to clear %s.',
  'block.projecthero.hideout_seal': 'Sealed Hideout Vault',
  'block.projecthero.hideout_seal.locked': 'Sealed. Defeat %s and break the Hideout Core to open it.',
  'block.projecthero.hideout_alarm': 'Hideout Alarm',
  'block.projecthero.hideout_vent': 'Hideout Vent',
  'block.projecthero.hive_biomass': 'Hive Biomass',
  'block.projecthero.hive_pod': 'Hive Pod',
  // clearing
  'projecthero.hideout.boss_down': '%s has fallen! The core of %s is exposed: break it.',
  'projecthero.hideout.cleared.title': 'HIDEOUT CLEARED',
  'projecthero.hideout.cleared.sub': '%s',
  'projecthero.hideout.cleared.chat': '%s is cleared for good.',
  'projecthero.hideout.cleared.vault': 'The Hideout Vault is open: everyone gets their own share.',
  'projecthero.hideout.calm.started': '%s will lie low within 512 blocks of here for five days.',
  'projecthero.hideout.calm.refused': '%s is lying low around here since %s was cleared (%s days left).',
  // alarms
  'projecthero.hideout.alarm.kingpin': 'Alarm! Syndicate muscle is on the way.',
  'projecthero.hideout.alarm.ultron': 'Intruder alert: drones inbound.',
  'projecthero.hideout.alarm.apokolips': 'A Boom Tube tears open!',
  'projecthero.hideout.alarm.symbiote': 'The pods burst open!',
  'projecthero.hideout.alarm.frost': 'A war horn sounds through the hall!',
  // artifacts
  'projecthero.hideout.artifact.from': 'Artifact: %s',
  'item.projecthero.gilded_cane': "Kingpin's Gilded Cane",
  'item.projecthero.gilded_cane.tooltip': 'A heavy ivory-and-gold cane that hits hard and knocks enemies far. Use: Cane Sweep, a wide swing that hurts and throws back everything in front of you (6 s cooldown).',
  'item.projecthero.ultron_cortex_shard': 'Ultron Cortex Shard',
  'item.projecthero.ultron_cortex_shard.tooltip': 'Use: for 8 seconds every hostile within 32 blocks is outlined through walls, in your view only (45 s cooldown).',
  'item.projecthero.mother_box_fragment': 'Mother Box Fragment',
  'item.projecthero.mother_box_fragment.tooltip': 'Use: open a short Boom Tube and step out up to 30 blocks ahead, where there is ground to land on (30 s cooldown).',
  'item.projecthero.mother_box_fragment.blocked': 'The Boom Tube finds nowhere to land.',
  'item.projecthero.hive_heart': 'Hive Heart',
  'item.projecthero.hive_heart.tooltip': 'Carry it: while you are below half health you slowly regenerate (1 health every 2 seconds). Only one heart beats at a time.',
  'item.projecthero.frostbrand': 'Frostbrand',
  'item.projecthero.frostbrand.tooltip': 'A Jotun blade cut down to mortal size. Every hit slows the target and frosts it over.',
  // guidebook
  'projecthero.guide.hideouts': 'Villain Hideouts',
  'projecthero.guide.hideouts.body': "Five villain factions each run a hideout somewhere in the world: Tier 3 places, rarer than the old sites and far more dangerous. Each one is a real building with several rooms, guards that never despawn, traps, and a mini-boss guarding the faction's Hideout Core. A Hideout Locator (from Site Vaults) points to the nearest one.",
  'projecthero.guide.hideouts.clearing': 'Clearing a hideout',
  'projecthero.guide.hideouts.clearing.body': 'The Hideout Core in the boss room is sealed while the mini-boss lives: nothing can break it. Kill the mini-boss and the seal drops; break the core and the hideout is cleared for good. The sealed Hideout Vault in the boss room then opens into a Tier 3 Hero Vault, and every player gets their own share.',
  'projecthero.guide.hideouts.calm': 'The raid calm',
  'projecthero.guide.hideouts.calm.body': "A cleared hideout calms its faction for five in-game days within 512 blocks of it: that faction's raids and events can't start there (Syndicate Busts and street crimes, Ultron Uprisings, Apokolips Invasions, Frost Giant Assaults, and Carnage's meteor for the Symbiote Hive). Operator commands still work.",
  'projecthero.guide.hideouts.traps': 'Traps',
  'projecthero.guide.hideouts.traps.body': 'Tripwires are hooked to alarms (siren boxes, sensors, Boom Tube beacons, egg pods, war horns): snap one and a squad of guards arrives. Break the alarm first to disarm it. Some floors hide pressure plates that fire arrows from the walls, and vents that hiss for a moment, then erupt: step off fast.',
  'projecthero.guide.hideouts.mini_bosses': 'Mini-bosses',
  'projecthero.guide.hideouts.mini_bosses.body': 'Every mini-boss has a boss bar, turns on whoever is hurting it most, and always drops Vibranium Shards, with a 1 in 4 chance of a Dungeon Locator.',
  'projecthero.guide.hideouts.artifacts': 'Artifacts',
  'projecthero.guide.hideouts.artifacts.body': "Each hideout's vault can hold that faction's artifact (Epic or Legendary): a single special effect, never a full power. The roll is per player, so everyone who opens the vault has their own chance.",
  'projecthero.guide.hideouts.h.kingpin': "Kingpin's Front (plains)",
  'projecthero.guide.hideouts.h.kingpin.body': "A brick pawn shop, \"Fisk Imports\", with a warehouse behind it. Behind the shop counter a trapdoor hides a ladder down to the back offices, an arrow-trapped corridor and the vault room. Guards: Syndicate thugs, gunmen and an Enforcer. Mini-boss: Kingpin's Enforcer, a huge brute who charges, throws a two-jab-and-uppercut combo, stomps the floor (jump it) and calls his boys at half health.",
  'projecthero.guide.hideouts.h.ultron': 'Ultron Forge (mountains)',
  'projecthero.guide.hideouts.h.ultron.body': "A bunker sunk into a mountainside: entrance hall, an assembly hall of half-built robots, a drone bay with charging pods and plasma vents, and the core room. Guards: drones, Sentinel Drones, a Heavy and a Lancer. Mini-boss: the Forge Overseer, which fires homing missiles, stamps the ground, sweeps a slow red Overload Beam (break line of sight) and launches drones.",
  'projecthero.guide.hideouts.h.apokolips': 'Apokolips Outpost (badlands and deserts)',
  'projecthero.guide.hideouts.h.apokolips.body': 'A scorched crater with fire pits and basalt perches round a dark tower. The tower climbs past a barracks to the war room at the top. Guards: Parademons of every kind, gunners on the perches. Mini-boss: the Parademon Commander, who fires a fan of bolts, dives on you from above and opens Boom Tubes for reinforcements.',
  'projecthero.guide.hideouts.h.symbiote': 'Symbiote Hive (forests, underground)',
  'projecthero.guide.hideouts.h.symbiote.body': 'A sinkhole ringed with black biomass leads deep underground into a dripping cave hive: a great chamber, a nursery of egg pods and the Hive Mother\'s den. Guards: Symbiote-infested zombies, husks, skeletons and spiders. Mini-boss: the Hive Mother, a towering host who lashes and drags you with tendrils, fires black spikes and births infested spiders.',
  'projecthero.guide.hideouts.h.frost': 'Frost Giant Hall (snowy lands)',
  'projecthero.guide.hideouts.h.frost.body': 'A Norse longhall of dark timber and blue ice, with a long fire, side benches and a raised throne. Guards: Rime Hounds and Jotun Raiders. Mini-boss: the Frost Giant Jarl, who cleaves in a wide arc, plants his sword to send a ring of ice spikes outward (jump them) and rallies his warriors.',
  'projecthero.guide.hideouts.a.kingpin': "Kingpin's Gilded Cane: a hard-hitting knockback club; use it for a wide Cane Sweep.",
  'projecthero.guide.hideouts.a.ultron': 'Ultron Cortex Shard: use it to see every hostile nearby through walls for a few seconds.',
  'projecthero.guide.hideouts.a.apokolips': 'Mother Box Fragment: use it to Boom Tube about 30 blocks ahead.',
  'projecthero.guide.hideouts.a.symbiote': 'Hive Heart: carried, it slowly heals you while you are below half health.',
  'projecthero.guide.hideouts.a.frost': 'Frostbrand: a sword whose hits slow and frost the target.',
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
