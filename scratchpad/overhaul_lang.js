// Inserts the Overhaul lang block right after the anchor key (CRLF kept, JSON validated, no duplicate keys).
const fs = require('fs');
const file = process.argv[2];
const A = 'projecthero.quirk.ability.quirk_overhaul_';
const entries = [
  [A + 'disassemble', 'Disassemble'],
  [A + 'disassemble.desc', 'A bare-hand touch: 14 damage as the body bursts apart. 12 Focus.'],
  [A + 'shrapnel', 'Shrapnel Burst'],
  [A + 'shrapnel.desc', 'The touched body bursts: 10 to it, 8 to everything within 4.5 blocks. 22 Focus.'],
  [A + 'spike_wave', 'Spike Wave'],
  [A + 'spike_wave.desc', 'A line of stone spikes erupts toward the target, 12 blocks: 9 damage and a toss. 18 Focus.'],
  [A + 'spike_field', 'Spike Field'],
  [A + 'spike_field.desc', 'Two rings of spikes erupt around you: 8 damage to all within 5 blocks. 28 Focus.'],
  [A + 'wall', 'Reshape Wall'],
  [A + 'wall.desc', 'Rip the ground up into a 5x3 wall ahead for 10 s. 18 Focus.'],
  [A + 'ramp', 'Launch Ramp'],
  [A + 'ramp.desc', 'The ground ahead rises into a ramp and flings you forward. No fall damage after. 12 Focus.'],
  [A + 'restore', 'Restore'],
  [A + 'restore.desc', 'Rebuild yourself or the ally you look at: full health, debuffs gone. Squadmates within 5 blocks too. 35 Focus.'],
  [A + 'reassemble', 'Reassemble Armour'],
  [A + 'reassemble.desc', 'Rebuild your worn armour to full durability and gain 8 Absorption for 30 s. 25 Focus.'],
  [A + 'field', 'Overhaul Field'],
  [A + 'field.desc', 'Everything around you shatters into a forest of spikes, 7 blocks out: 12 damage each. 55 Focus.'],
  [A + 'pit', 'Collapse Pit'],
  [A + 'pit.desc', 'The ground under the target drops 2 blocks and closes over it for 4 s: 6 damage. Not bosses. 30 Focus.'],
  [A + 'fusion', 'Fusion'],
  [A + 'fusion.desc', 'Absorb the ground: 30 s as a bigger stone giant with extra arms. +8 damage, +12 armour, more reach. Again: end. 45 Focus.'],
  [A + 'shard', 'Shard Shot'],
  [A + 'shard.desc', 'Break the block you look at into a cone of shards: 9 damage, 10 blocks. The block comes back. 15 Focus.'],
  ['message.projecthero.overhaul.bare_hand', 'Overhaul works by touch: empty your main hand.'],
  ['message.projecthero.overhaul.focus', 'Not enough Focus (%s needed).'],
  ['message.projecthero.overhaul.no_target', 'Nothing in reach to touch.'],
  ['message.projecthero.overhaul.no_ground', 'The ground here will not reshape.'],
  ['message.projecthero.overhaul.too_big', 'Too big to bury.'],
  ['message.projecthero.overhaul.no_block', 'Look at a block to break apart.'],
  ['message.projecthero.overhaul.restored', 'Restored: %s.'],
];
const text = fs.readFileSync(file, 'utf8');
const json = JSON.parse(text);
for (const [k] of entries) {
  if (k in json) {
    console.log('already present, nothing inserted: ' + k);
    process.exit(0);
  }
}
const anchor = '"message.projecthero.quirk.overloaded.overhaul"';
const lines = text.split('\r\n');
const i = lines.findIndex(l => l.trimStart().startsWith(anchor));
if (i < 0) throw new Error('anchor not found');
if (!lines[i].trimEnd().endsWith(',')) lines[i] = lines[i] + ',';
const block = entries.map(([k, v]) => '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + ',');
lines.splice(i + 1, 0, ...block);
const out = lines.join('\r\n');
JSON.parse(out);
fs.writeFileSync(file, out);
console.log('inserted ' + entries.length);
