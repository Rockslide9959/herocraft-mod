// v0.17.7 Thor x Super Soldier: merges the lent-weapon kit's player-facing text into en_us.json KEY BY KEY (CLAUDE.md rule):
// existing keys are replaced in place on their own line, new ones are appended before the closing brace. CRLF kept.
// usage: node scratchpad/v0177/worthy_lang.js src/main/resources/assets/projecthero/lang/en_us.json
const fs = require('fs');
const file = process.argv[2];
const L = {
  // ---- lending
  'message.projecthero.worthy.not_bound': '%s must be bound to you before you can lend it.',
  'message.projecthero.worthy.not_soldier': '%s is not a Super Soldier. Only a Super Soldier can carry your weapon.',
  'message.projecthero.worthy.not_squad': '%s is not in your squad.',
  'message.projecthero.worthy.lent_owner': 'You lent %1$s to %2$s. Call it back any time from your weapon menu (N).',
  'message.projecthero.worthy.lent_borrower': '%1$s lent you %2$s! You are worthy, for now. Its moves are on your keys (hold Left Alt for the list). Double-tap Space in the air to fly.',
  'message.projecthero.worthy.not_lent': '%s is not lent to anyone.',
  'message.projecthero.worthy.all_lent': 'Your weapons are lent to your squad. Call them back from the weapon menu (N).',
  'message.projecthero.worthy.dropped': "%s's owner is away. It stays here on the ground for them.",
  'message.projecthero.worthy.end.recall.owner': '%1$s flies back to you from %2$s.',
  'message.projecthero.worthy.end.recall.borrower': '%2$s called %1$s back.',
  'message.projecthero.worthy.end.squad.owner': '%2$s is no longer in your squad. %1$s comes back to you.',
  'message.projecthero.worthy.end.squad.borrower': "You are no longer in %2$s's squad. %1$s goes back to them.",
  'message.projecthero.worthy.end.power.owner': '%2$s is no longer a Super Soldier. %1$s comes back to you.',
  'message.projecthero.worthy.end.power.borrower': 'You are no longer a Super Soldier. %1$s goes back to %2$s.',
  'message.projecthero.worthy.end.death.owner': '%2$s fell. %1$s comes back to you.',
  'message.projecthero.worthy.end.death.borrower': '%1$s goes back to %2$s.',
  'message.projecthero.worthy.end.unbound.owner': '%1$s is no longer bound to you. The loan to %2$s is over.',
  'message.projecthero.worthy.end.unbound.borrower': '%1$s is no longer bound to %2$s. The loan is over.',
  // ---- the Thor weapon screen
  'screen.projecthero.thor_weapons.lent_to': 'Lent to %s',
  'screen.projecthero.thor_weapons.call_back': 'Call back',
  'screen.projecthero.thor_weapons.tip_call_back': 'Call %1$s back from %2$s. It flies home to you, from anywhere.',
  // ---- HUD
  'hud.projecthero.worthy.title.mjolnir': 'Mjolnir',
  'hud.projecthero.worthy.title.stormbreaker': 'Stormbreaker',
  'hud.projecthero.worthy.title.shield_mjolnir': 'Shield + Mjolnir',
  'hud.projecthero.worthy.title.shield_stormbreaker': 'Shield + Stormbreaker',
  'hud.projecthero.worthy.lent_by': "%s's",
  'hud.projecthero.worthy.flying': 'Flying',
  'hud.projecthero.worthy.boost': 'Boost',
  'hud.projecthero.worthy.charged': 'Charged',
  'hud.projecthero.worthy.move_list_flight': 'Fly (with the weapon in hand)',
  'hud.projecthero.worthy.move_list_rmb': 'Throw the weapon',
  // ---- moves: Mjolnir only
  'projecthero.worthy.ability.hammer_combo': 'Hammer Combo',
  'projecthero.worthy.ability.hammer_combo.desc': 'Three swings at the enemy in front (within 4.5 blocks): 7, 7, then an overhead smash for 11 that throws sparks into everyone near it (5). 5 s.',
  'projecthero.worthy.ability.thunder_uppercut': 'Thunder Uppercut',
  'projecthero.worthy.ability.thunder_uppercut.desc': 'A rising hammer blow: 12 and the enemy is launched into the air. 8 s.',
  'projecthero.worthy.ability.call_lightning': 'Call Lightning',
  'projecthero.worthy.ability.call_lightning.desc': 'Raise Mjolnir and call a bolt down on the enemy you aim at (or the spot, up to 24 blocks): 14 and a brief jolt that stops it, 6 to anyone next to it. 6 s.',
  'projecthero.worthy.ability.thunderclap': 'Thunderclap',
  'projecthero.worthy.ability.thunderclap.desc': 'Drive the hammer into the ground: a shockwave across 7 blocks in front, 10 and a hard knockback. 9 s.',
  'projecthero.worthy.ability.thunder_slam': 'Thunder Slam',
  'projecthero.worthy.ability.thunder_slam.desc': 'A hop and a hammer slam when you land: 14 within 5 blocks, less at the edge. No fall damage. 11 s.',
  'projecthero.worthy.ability.worthy_storm': 'Worthy Storm',
  'projecthero.worthy.ability.worthy_storm.desc': 'Ultimate. Hold Mjolnir up to the sky: lightning strikes the hammer, five bolts fall on enemies within 14 blocks (15 each), then you bring it down for 20 within 6. 90 s, shared by every lent-weapon ultimate.',
  'projecthero.worthy.ability.hammer_dash': 'Hammer Dash',
  'projecthero.worthy.ability.hammer_dash.desc': 'Pulled along by the hammer: a quick dash where you look. While flying, a Flight Boost instead (x1.5 for 2.5 s). 4 s.',
  'projecthero.worthy.ability.hammer_launch': 'Hammer Launch',
  'projecthero.worthy.ability.hammer_launch.desc': 'Whirl the hammer overhead and launch up and forward, straight into flight. No fall damage. 6 s.',
  'projecthero.worthy.ability.whirl_guard': 'Whirl Guard',
  'projecthero.worthy.ability.whirl_guard.desc': 'Spin the hammer in front of you for 1.5 s: every hit from the front is blocked, arrows are knocked back, and melee attackers get a shock (4). 10 s.',
  'projecthero.worthy.ability.rally_bolt': 'Rally Bolt',
  'projecthero.worthy.ability.rally_bolt.desc': 'Call a bolt down on yourself: you and your squad within 10 blocks get Strength I and Speed I for 8 s, and nearby enemies are blown back. 40 s.',
  'projecthero.worthy.ability.hammer_throw': 'Hammer Throw',
  'projecthero.worthy.ability.hammer_throw.desc': 'Throw Mjolnir where you aim (also right click): it goes through up to 3 enemies (12 each) and flies back to your hand. 3 s.',
  // ---- moves: Stormbreaker only
  'projecthero.worthy.ability.axe_cleaves': 'Axe Cleaves',
  'projecthero.worthy.ability.axe_cleaves.desc': 'Two heavy sweeping cleaves across everything in front (3.5 blocks): 9, then 13 with a knockback. 6 s.',
  'projecthero.worthy.ability.rising_cleave': 'Rising Cleave',
  'projecthero.worthy.ability.rising_cleave.desc': 'An upward chop: 13 and the enemy is launched into the air. 9 s.',
  'projecthero.worthy.ability.lightning_cleave': 'Lightning Cleave',
  'projecthero.worthy.ability.lightning_cleave.desc': 'An overhead chop into the ground that sends a line of lightning 12 blocks ahead: 14 to everyone on it, a bolt at the end. 7 s.',
  'projecthero.worthy.ability.executioners_chop': "Executioner's Chop",
  'projecthero.worthy.ability.executioners_chop.desc': 'Leap onto the enemy in front (within 9 blocks) and chop down: 16, or 20 if it is under 30% health, with a bolt. 11 s.',
  'projecthero.worthy.ability.storm_crash': 'Storm Crash',
  'projecthero.worthy.ability.storm_crash.desc': 'A hop and a crushing landing: 15 within 4.5 blocks, a big knockback, a bolt in front of you. 12 s.',
  'projecthero.worthy.ability.stormbreakers_verdict': "Stormbreaker's Verdict",
  'projecthero.worthy.ability.stormbreakers_verdict.desc': 'Ultimate. Hurl Stormbreaker into the enemy you aim at (within 24 blocks): it strikes in a bolt of lightning (20), then the storm bursts around it (14 within 6) and the axe flies home. 90 s, shared.',
  'projecthero.worthy.ability.axe_dash': 'Axe Dash',
  'projecthero.worthy.ability.axe_dash.desc': 'A heavier, shorter dash where you look. While flying, a Flight Boost instead (x1.5 for 2.5 s). 4 s.',
  'projecthero.worthy.ability.storm_leap': 'Storm Leap',
  'projecthero.worthy.ability.storm_leap.desc': 'Whirl the axe overhead and launch up and forward, straight into flight. No fall damage. 6 s.',
  'projecthero.worthy.ability.axe_guard': 'Axe Guard',
  'projecthero.worthy.ability.axe_guard.desc': 'Hold the axe across you for 1.5 s: every hit from the front is blocked, arrows are knocked back, and melee attackers get a shock (4). 10 s.',
  'projecthero.worthy.ability.axe_throw': 'Axe Throw',
  'projecthero.worthy.ability.axe_throw.desc': 'Throw Stormbreaker where you aim (also right click): it goes through up to 2 enemies (13 each, slowed for 2 s) and flies back to your hand. 4 s.',
  // ---- moves: shield + weapon
  'projecthero.worthy.ability.shield_and_weapon': 'Shield and Weapon',
  'projecthero.worthy.ability.shield_and_weapon.desc': 'A shield bash (8, the enemy staggered) straight into a blow with the weapon (12, 13 with the axe). 6 s.',
  'projecthero.worthy.ability.bash_launcher': 'Bash Launcher',
  'projecthero.worthy.ability.bash_launcher.desc': 'A shield uppercut that launches the enemy (10), then the weapon spikes it back down (8, 9 with the axe). 9 s.',
  'projecthero.worthy.ability.charged_shield_throw': 'Charged Shield Throw',
  'projecthero.worthy.ability.charged_shield_throw.desc': 'Your shield throw, charged with lightning: every enemy it hits sends lightning on to two more nearby (6 each). 8 s.',
  'projecthero.worthy.ability.twin_throw': 'Twin Throw',
  'projecthero.worthy.ability.twin_throw.desc': 'Throw the shield and the weapon together where you aim. Both come back to your hands. 10 s.',
  'projecthero.worthy.ability.lightning_shield_slam': 'Lightning Shield Slam',
  'projecthero.worthy.ability.lightning_shield_slam.desc': 'A hop and a shield slam when you land: 14 within 5 blocks (15 with the axe) and two bolts at your sides. No fall damage. 11 s.',
  'projecthero.worthy.ability.thunder_clap': 'Thunder Clap',
  'projecthero.worthy.ability.thunder_clap.desc': 'Ultimate. Raise the shield and strike it with the weapon: a thunderclap shockwave 10 blocks ahead, 18 (19 with the axe, less at the edge), a huge knockback and three bolts down the cone. 90 s, shared.',
  'projecthero.worthy.ability.lightning_guard': 'Lightning Guard',
  'projecthero.worthy.ability.lightning_guard.desc': 'Raise the shield, the weapon charged behind it, for 2.5 s: every hit from the front is blocked, arrows are knocked back, and melee attackers take 8. 10 s.',
  // ---- Guidebook: Thor chapter
  'projecthero.guide.thor.lend': 'Lending your weapons',
  'projecthero.guide.thor.lend.body': 'Hold Mjolnir or Stormbreaker and sneak + right-click a Super Soldier in your squad to lend it to them. It goes into their hand (whatever they held moves to their pack) and they are worthy of it, for now: it gives them its own moves and lets them fly. The weapon must be bound to you, and it stays yours. You can lend one weapon or both, to the same Super Soldier or to two.',
  'projecthero.guide.thor.lend.recall': 'Open your weapon menu (N) to see who has each weapon. Press Call back and it flies home to you whenever you want, from anywhere. R leaves a lent weapon alone.',
  'projecthero.guide.thor.lend.rules': 'A loan also ends by itself, and the weapon comes back to you, if the Super Soldier leaves your squad, stops being a Super Soldier, or falls in battle. If you are offline then, it is left on the ground where they stand, still yours. Loans last through logging out and server restarts.',
  // ---- Guidebook: Super Soldier chapter
  'projecthero.guide.super_soldier.worthy': "Thor's weapons",
  'projecthero.guide.super_soldier.worthy.body': 'A Thor in your squad can lend you Mjolnir or Stormbreaker (they sneak + right-click you). While you hold it, your keys become its moves. There are two sets: the weapon on its own, or your shield in the other hand (or in the Battle Suit slot) together with the weapon. Hold Left Alt for the list. Thor can call the weapon back at any time.',
  'projecthero.guide.super_soldier.worthy.flight': 'With a lent weapon in hand, double-tap Space in the air to fly: Mjolnir 15 blocks a second (30 sprinting), Stormbreaker 13 (26). X while flying is a Flight Boost. Landing or a second double-tap ends it.',
  'projecthero.guide.super_soldier.worthy.mjolnir': 'Mjolnir on its own',
  'projecthero.guide.super_soldier.worthy.stormbreaker': 'Stormbreaker on its own',
  'projecthero.guide.super_soldier.worthy.shield_mjolnir': 'Shield and Mjolnir (or Stormbreaker)',
};
let s = fs.readFileSync(file, 'utf8');
const crlf = s.includes('\r\n');
let lines = s.replace(/\r\n/g, '\n').split('\n');
const esc = (v) => JSON.stringify(v);
let added = 0, replaced = 0;
for (const [k, v] of Object.entries(L)) {
  const prefix = '  ' + esc(k) + ':';
  const i = lines.findIndex((l) => l.startsWith(prefix));
  if (i >= 0) {
    const comma = lines[i].trimEnd().endsWith(',');
    lines[i] = prefix + ' ' + esc(v) + (comma ? ',' : '');
    replaced++;
  } else {
    // append before the closing brace: the previous last entry gets a comma
    let end = lines.length - 1;
    while (end > 0 && lines[end].trim() !== '}') end--;
    let last = end - 1;
    while (last > 0 && lines[last].trim() === '') last--;
    if (!lines[last].trimEnd().endsWith(',') && lines[last].trim() !== '{') lines[last] = lines[last].trimEnd() + ',';
    lines.splice(end, 0, prefix + ' ' + esc(v));
    added++;
  }
}
let out = lines.join('\n');
JSON.parse(out); // must stay valid
if (crlf) out = out.replace(/\n/g, '\r\n');
fs.writeFileSync(file, out);
console.log(`added ${added}, replaced ${replaced}`);
