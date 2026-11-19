// v0.17.5 Symbiote weapons (Blade / Axe / Mace / Whip) + Symbiote Wings: rewrites the Symbiote keys whose meaning changed and inserts the
// new ones after an anchor, key by key (never the whole file). Keeps the file's line endings; JSON.parse checked before writing.
// Re-runnable (existing keys are replaced in place, missing ones inserted).
//   node scratchpad/symbiote/lang_symbiote_weapons_wings_v0175.js
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero/lang/en_us.json');
let text = fs.readFileSync(file, 'utf8');
const EOL = text.includes('\r\n') ? '\r\n' : '\n';

const replace = {
  'projecthero.symbiote.ability.form.desc': "V: change Form -- Normal, Blade, Axe, Mace, Whip, Thorns, then back to Normal. Each weapon grows out of your forearm (more melee damage, its own Z move, feeds on 0.3 Biomass a second). Thorns: spikes that hurt anything hitting you in melee. With an item in your hand the weapons are skipped. Shift+V: Symbiote Shield.",
  'projecthero.symbiote.ability.barrage.desc': "Z (tap): a flurry of tendrils from alternating hands for over a second, each flying up to 30 blocks down your aim for 4 damage. It can miss. With a weapon out, Z is that weapon's move instead (Blade Slash, Cleave, Ground Smash, Lash). Hold Z: Ground Eruption. Shift + hold Z: Symbiote Onslaught.",
  'projecthero.symbiote.ability.blade.desc': "The Blade form (V cycles to it): a huge crescent scythe grows out of your forearm -- +5 melee, swings 50% faster, +0.5 reach, bites past armour. Z: Blade Slash. It feeds on 0.3 Biomass a second and dissolves when the Biomass runs out. Nothing can be held in that hand while it is out.",
  'message.projecthero.symbiote.form.blade_skipped': "Your hand is full: no weapon -- Thorns form",
  'projecthero.guide.symbiote.s.move.barrage': "Tap: a second-long flurry of tendrils from both hands, 4 damage a hit, 30 blocks. With a weapon out, Z is that weapon's own move (below).",
  'projecthero.guide.symbiote.s.move.blade': "Blade form: a huge crescent scythe grows from your forearm -- +5 melee, 50% faster swings, +0.5 reach, and every hit bites 2 more past armour. Feeds on 0.3 Biomass a second; nothing else fits in that hand.",
  'projecthero.guide.symbiote.s.biomass.costs': "Costs: any weapon form 0.3 a second, Shield 0.5 a second, the Wings 10 each flight, healing you about 2 a second, catching an arrow 3. Maw Bite wins back 12, a heavy bite 20, Devour 40.",
  'projecthero.guide.symbiote.s.tip.biomass': "Put your weapon and the Shield away when you are not using them, so the Biomass can grow back -- or feed: bite things.",
  'hud.projecthero.symbiote.move_list.air': "In the air: %s",
};

const insertAfter = 'message.projecthero.symbiote.form.blade_skipped';
const add = [
  ['projecthero.symbiote.form.axe', 'Axe'],
  ['projecthero.symbiote.form.mace', 'Mace'],
  ['projecthero.symbiote.form.whip', 'Whip'],
  ['projecthero.symbiote.ability.axe', 'Symbiote Axe'],
  ['projecthero.symbiote.ability.axe.desc', "The Axe form: a heavy bearded axe head grows over your fist -- +8 melee but 25% slower swings, and a punch knocks a raised shield out of a player's hands. Z: Cleave."],
  ['projecthero.symbiote.ability.mace', 'Symbiote Mace'],
  ['projecthero.symbiote.ability.mace.desc', "The Mace form: a spiked ball of hardened symbiote on a thick tendril -- +6 melee, 15% slower swings, and every punch knocks back. Z: Ground Smash."],
  ['projecthero.symbiote.ability.whip', 'Symbiote Whip'],
  ['projecthero.symbiote.ability.whip.desc', "The Whip form: a barbed tendril coils round your forearm -- +3 melee, 10% faster swings and 2.5 blocks more reach. Z: Lash."],
  ['projecthero.symbiote.ability.blade_slash', 'Blade Slash'],
  ['projecthero.symbiote.ability.cleave', 'Cleave'],
  ['projecthero.symbiote.ability.ground_smash', 'Ground Smash'],
  ['projecthero.symbiote.ability.lash', 'Lash'],
  ['projecthero.symbiote.ability.wings', 'Symbiote Wings'],
  ['projecthero.symbiote.ability.wings.desc', "Double-tap Space in the air with the suit on: giant bat wings grow out of your back and you fly for up to 6 seconds the way you look -- Space to climb, Shift to dive -- then glide down until you land. 10 Biomass, 20-second cooldown."],
  ['projecthero.guide.symbiote.s.move.axe', "Axe form: a heavy axe head grows over your fist -- +8 melee, 25% slower swings, and a punch knocks a raised shield out of a player's hands."],
  ['projecthero.guide.symbiote.s.move.mace', "Mace form: a spiked symbiote ball on a thick tendril -- +6 melee, 15% slower swings, every punch knocks back."],
  ['projecthero.guide.symbiote.s.move.whip', "Whip form: a barbed tendril coiled round your arm -- +3 melee, 10% faster swings, 2.5 blocks more reach."],
  ['projecthero.guide.symbiote.s.move.blade_slash', "A wide slash in front of you (5 blocks): 8 damage plus 2 past armour and a knockback."],
  ['projecthero.guide.symbiote.s.move.cleave', "An overhead chop down a 4.5-block line ahead: 16 damage plus 4 past armour, and it knocks a raised shield away first."],
  ['projecthero.guide.symbiote.s.move.ground_smash', "The mace slams the ground just ahead: 14 damage to everything within 4 blocks, thrown out and up, Slowness II for 2 seconds."],
  ['projecthero.guide.symbiote.s.move.lash', "The whip cracks 7 blocks down your aim: 12 damage to the first thing it hits, which is yanked in toward you (not bosses)."],
  ['projecthero.guide.symbiote.s.move.wings', "Double-tap Space in the air, suit on: giant bat wings grow from your back. Fly up to 6 s the way you look (Space climbs, Shift dives), then glide until you land; they melt back in. 10 Biomass."],
  ['message.projecthero.symbiote.wings.on', 'The Symbiote spreads its wings'],
  ['message.projecthero.symbiote.wings.glide', 'The wings tire -- gliding'],
  ['message.projecthero.symbiote.wings.no_biomass', 'Not enough Biomass for the wings (10)'],
  ['hud.projecthero.symbiote.wings.flying', 'Wings %ss'],
  ['hud.projecthero.symbiote.wings.gliding', 'Gliding'],
  ['hud.projecthero.symbiote.wings.cooldown', 'Wings %ss'],
  ['hud.projecthero.symbiote.move_list.air_wings', 'In the air: %s; Space x2: Wings'],
  ['hud.projecthero.symbiote.move_list.weapon_z', 'Hold: %s; weapon out: its own Z'],
];

function esc(s) { return JSON.stringify(s); }
let lines = text.split(EOL);
for (const [k, v] of Object.entries(replace)) {
  const i = lines.findIndex((l) => l.trimStart().startsWith(esc(k) + ':'));
  if (i < 0) { console.log('missing (skipped replace)', k); continue; }
  const comma = lines[i].trimEnd().endsWith(',') ? ',' : '';
  lines[i] = '  ' + esc(k) + ': ' + esc(v) + comma;
}
let anchor = lines.findIndex((l) => l.trimStart().startsWith(esc(insertAfter) + ':'));
if (anchor < 0) throw new Error('anchor missing');
for (const [k, v] of add) {
  const i = lines.findIndex((l) => l.trimStart().startsWith(esc(k) + ':'));
  if (i >= 0) {
    const comma = lines[i].trimEnd().endsWith(',') ? ',' : '';
    lines[i] = '  ' + esc(k) + ': ' + esc(v) + comma;
    continue;
  }
  lines.splice(anchor + 1, 0, '  ' + esc(k) + ': ' + esc(v) + ',');
  anchor++;
}
const out = lines.join(EOL);
JSON.parse(out);
fs.writeFileSync(file, out);
console.log('lang ok');
