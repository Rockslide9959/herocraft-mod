// v0.17.5 Sentry rework: adds / updates the en_us keys one by one (never rewrites the file wholesale: other branches touch it too).
// Keeps the file's line endings and 2-space indent. Every key here is set (added if new, replaced if it exists).
//   node scratchpad/sentry/lang_sentry_rework_v0175.js
'use strict';
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero/lang/en_us.json');

const K = {
  // the suit
  'item.projecthero.sentry_suit_top': 'Sentry Suit',
  'item.projecthero.sentry_suit_trousers': 'Sentry Suit Trousers',
  'item.projecthero.sentry_suit.tooltip': 'Mends itself over time. Top + trousers: the Void rises slower and sunlight charges you faster (Sentry only).',
  'item.projecthero.sentry_suit.tooltip2': 'The top brings a blue cape. Merged with the Void, the suit fades into the Void suit.',
  'item.projecthero.sentry_suit.hint': 'The Sentry Suit: a top (body, arms and a blue cape) and trousers, crafted from gold ingots, blue wool and a hero alloy ingot. Diamond-level protection. It mends 1 durability every 3 s, worn or in your inventory, so it never needs an anvil. Worn whole by the Sentry, every Void gain is cut to 60% and the Sun fills 50% faster. When he merges with the Void (Let It Out), the suit slowly fades into the Void suit and the cape turns black.',
  // messages
  'message.projecthero.sentry.acquired_hint': 'The Sun meter fuels you; keep the Void meter low. Hold V to calm it, double-tap Space to fly. Craft the Sentry Suit to slow the Void. Open the Guidebook for the full kit.',
  'message.projecthero.sentry.trial_start': 'Hold the light...',
  'message.projecthero.sentry.trial_lost': 'The light slips. The Void grows (+%s).',
  // HUD
  'hud.projecthero.sentry.suit': 'Suit',
  'hud.projecthero.sentry.trial': 'Trial',
  'hud.projecthero.sentry.key_space': '2x Space',
  // the trial screen
  'screen.projecthero.let_it_out': 'LET IT OUT',
  'screen.projecthero.let_it_out.hint': 'Hold Space or the left mouse button to lift your light. Keep it inside the sun to fill the bar before the Void drags it down.',
  'screen.projecthero.let_it_out.light': 'Light %s  |  %s s',
  'screen.projecthero.let_it_out.won': 'You let the Void in, and you are still you.',
  'screen.projecthero.let_it_out.lost': 'The light slips away. The Void grows (+%s).',
  // abilities
  'projecthero.sentry.ability.flight.desc': 'Double-tap Space to take off or land: 22 blocks a second, 44 sprinting. Space rises, Shift sinks. The same while merged with the Void.',
  'projecthero.sentry.ability.sonic_dash.desc': 'X: a sonic-boom dash 18 blocks along your look, 14 to everything you pass. Works on the ground or in the air. Costs 5 Sun, 4 s cooldown.',
  'projecthero.sentry.ability.let_it_out.desc': 'Start the trial: keep your light inside the sun for a few seconds. Win and you merge with the Void: the Void moves, +25% damage, Void moves recharge 25% faster, but the Void rises faster. Lose and the Void jumps by 15. Press Shift+V again to come back.',
  'projecthero.sentry.ability.return.desc': 'Shift+V (or V): come back from the Void to being the Sentry. Only works below 90 Void.',
  'projecthero.sentry.ability.molecular_grip.desc': 'Lift the creature you look at (20 blocks) in a golden field. Let go of G to throw it (14). Keep holding G for 1.5 s to tear it apart (55, +1.7 Void). Costs 10 Sun.',
  'projecthero.sentry.ability.unmake.desc': 'Turns a 3x3 patch of the blocks you look at into drifting light. No drops. Never bedrock, chests or portals. Costs 8 Sun, +1 Void.',
  'projecthero.sentry.ability.million_suns.desc': 'A 3 s charge, hanging in the air, then an explosion of light: 140 at the centre to 70 at 12 blocks. Costs 60 Sun, +8 Void. 60 s cooldown.',
  'projecthero.sentry.ability.shadow_lash.desc': 'Tendrils whip everything in front of you (6 blocks): 20 each (+25% merged). +0.35 Void.',
  'projecthero.sentry.ability.erase.desc': 'Wipes the creature you look at (24 blocks) into a burnt shadow: 100 (+25% merged). A kill leaves its shadow on the ground. +2 Void, 20 s cooldown.',
  'projecthero.sentry.ability.grasp.desc': 'Tendrils drag up to five creatures within 14 blocks to you (6 each). +0.7 Void.',
  'projecthero.sentry.ability.void_rift.desc': 'A black hole where you look. For 4 s it pulls everything within 7 blocks in and crushes it (6 every half second). +1.7 Void.',
  'projecthero.sentry.ability.nightfall.desc': 'A dome of darkness (10 blocks, 10 s): enemies inside get Darkness and Weakness; you regenerate. +2 Void.',
  'projecthero.sentry.ability.shadow_step.desc': 'Teleport to any dark spot you look at (light 7 or less, 40 blocks). +0.35 Void.',
  // the guide
  'projecthero.guide.sentry.meters.body': 'Sun (the gold Gauge) pays for the Sentry moves. It fills 6 a second in direct sunlight, 2 in daytime shade, 0.5 under the night sky and not at all in the dark. Sun also heals you slowly. Void (the black Hairline under it) is the danger, and it builds slowly. It rises a little with the big moves, heavy hits (8 damage or more), every kill, standing at night (0.08 a second) and standing in the dark (0.17 a second). It falls 1 a second in daylight, when you heal others, and 6 a second while you hold V. Merged with the Void it rises faster (0.5 a second of its own, and every gain x1.5). The Sentry Suit slows every gain to 60%.',
  'projecthero.guide.sentry.body_stats.body': '40 max health (merged with the Void too), 70% less damage taken (75% merged), +12 melee (+16), 30% faster, no hunger. No fall damage while you fly or dive.',
  'projecthero.guide.sentry.suit': 'The Sentry Suit',
  'projecthero.guide.sentry.suit.body': 'The power doesn\'t dress you: craft the suit. The top (blue wool, gold ingots, a hero alloy ingot) covers your body and arms and brings a blue cape; the trousers cover your legs. Your own face and hair always show. Diamond-level protection, and it mends itself (1 durability every 3 s) so it never needs an anvil. Wearing both, the Void rises at 60% and sunlight charges the Sun 50% faster. When you merge with the Void the suit slowly fades into the Void suit and the cape turns black; it fades back when you return.',
  'projecthero.guide.sentry.trial': 'Let It Out: the trial',
  'projecthero.guide.sentry.trial.body': 'Shift+V opens a short trial: a sun drifts up and down a dark shaft and your light falls unless you hold Space or the left mouse button. Keep the light inside the sun to fill the bar within 8 s (more Void makes the sun smaller and faster). Win and you merge with the Void: the Void moves (plus flight on double-tap Space), +25% damage, Void moves recharge 25% faster, and the Void rises faster. Lose (or close it) and the Void jumps by 15. Shift+V while merged brings you back, as long as the Void is below 90.',
  'projecthero.guide.sentry.void_controls': 'Merged with the Void (Void Form) controls',
  'projecthero.guide.sentry.takeover.body': 'At 100 Void your body is not yours. Your whole body turns black, suit and cape too, with two tiny white eyes. The camera pulls out behind you and the Void flies you on its own. It attacks everything living around it: monsters, animals, other players, your squad and your pets. It uses the Void Form moves at full strength, hunts the nearest living thing, and drifts toward players and villages when nothing is near. Creatures it kills are absorbed and heal it.',
};

let text = fs.readFileSync(file, 'utf8');
const eol = text.includes('\r\n') ? '\r\n' : '\n';
const esc = (s) => JSON.stringify(s);
let added = 0, changed = 0;
for (const [k, v] of Object.entries(K)) {
  const re = new RegExp('^(\\s*)' + esc(k).replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\s*:\\s*"(?:[^"\\\\]|\\\\.)*"(,?)\\r?$', 'm');
  const m = text.match(re);
  if (m) {
    const line = m[1] + esc(k) + ': ' + esc(v) + m[2];
    if (m[0].replace(/\r$/, '') !== line) { text = text.replace(re, line); changed++; }
  } else {
    // add before the closing brace: give the last entry a comma first
    const close = text.lastIndexOf('}');
    let head = text.slice(0, close).replace(/\s*$/, '');
    if (!head.endsWith(',') && !head.endsWith('{')) head += ',';
    text = head + eol + '  ' + esc(k) + ': ' + esc(v) + eol + '}' + text.slice(close + 1);
    added++;
  }
}
JSON.parse(text); // must still be valid
fs.writeFileSync(file, text);
console.log(`en_us: ${added} added, ${changed} changed`);
