// v0.17.5 Sentry & the Void, round 3 (2026-11-18): adds / updates / removes the en_us keys one by one (never rewrites the file wholesale:
// other branches touch it too). Keeps the file's line endings and 2-space indent.
//   node scratchpad/sentry/lang_sentry_round3_v0175.js
'use strict';
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero/lang/en_us.json');

const A = 'projecthero.sentry.ability.';
const K = {
  // the Sentry: new and moved moves
  [A + 'solar_flare']: 'Solar Flare',
  [A + 'solar_flare.desc']: 'Shift+R: a burst of sunlight all round you (8 blocks): 24 at your side down to 12 at the edge, everything thrown back, mobs blinded for 3 s and lit up. The undead take 50% more and catch fire. Costs 18 Sun, +0.6 Void, 10 s cooldown.',
  [A + 'solar_gaze.desc']: 'Hold G: twin beams of light from your eyes, exactly where you look, 32 blocks. As strong as a Kryptonian\'s heat vision: 4 every quarter second, 5 s of fire, and blocks catch alight. No time limit: it lasts as long as you hold it and have Sun (5 a second). It can clash with other beams.',
  [A + 'disassemble.desc']: 'Hold Shift+G on a creature (20 blocks): it is lifted in a golden field (bosses are pinned) and starts to unravel (2 every 5 ticks). Keep holding for 2 s and it comes apart (50, +1.7 Void); a kill turns it into golden light. Let go sooner to throw it (12). Costs 10 Sun.',
  [A + 'skyward_hurl.desc']: 'Shift+C: grab the creature in reach (6 blocks) and throw it straight up into the sky (16, then the fall). Bosses are too big to throw: they are slammed instead (30 and slowed). Costs 14 Sun, 10 s cooldown.',
  [A + 'molecular_shield.desc']: 'V: a golden aura for 8 s. Enemy projectiles that reach it turn into light (+2 Sun each), you take 40% less damage, and anything that hits you up close is thrown back (6). Costs 15 Sun, 20 s cooldown.',
  [A + 'golden_sense']: 'Golden Sense',
  [A + 'golden_sense.desc']: 'Shift+V: your senses flare. Every creature within 40 blocks glows for 10 s, so you see it through walls, and you can see in the dark for a minute (Night Vision). Costs 10 Sun, 30 s cooldown.',
  [A + 'sonic_dash.desc']: 'X on the ground: a sonic-boom dash 18 blocks along your look, 14 to everything you pass (5 Sun, 4 s cooldown). X while flying is the Flight Boost instead.',
  [A + 'flight_boost']: 'Flight Boost',
  [A + 'flight_boost.desc']: 'X while flying: a sonic boom, then you fly 50% faster for 30 s (33 blocks a second, 66 sprinting) trailing golden light. Costs 10 Sun, 45 s cooldown from the start.',
  [A + 'let_it_out.desc']: 'H: start the trial. Keep your light inside the sun for a few seconds. Win and you merge with the Void: the Void moves, +50% damage, Void moves recharge 25% faster, but the Void rises faster. The red-and-black Void suit and a black cape appear on you, suit or not. Lose and the Void jumps by 15. H again brings you back.',
  [A + 'return.desc']: 'H: come back from the Void to being the Sentry. Only works below 90 Void. If you weren\'t wearing the Sentry Suit, the Void suit fades away.',
  // the Void: two new moves
  [A + 'umbral_shroud']: 'Umbral Shroud',
  [A + 'umbral_shroud.desc']: 'V: a cloak of darkness for 8 s. You take 40% less damage, projectiles that reach it are swallowed, and anything that hits you up close withers and goes blind for 3 s. +1 Void, 20 s cooldown.',
  [A + 'shadow_meld']: 'Shadow Meld',
  [A + 'shadow_meld.desc']: 'Shift+V: sink into the shadows for 6 s. You turn invisible and fast, and every mob hunting you within 32 blocks loses you. Using a Void attack brings you out. +0.6 Void, 30 s cooldown.',
  // HUD
  'hud.projecthero.sentry.boost': 'Boost',
  'hud.projecthero.sentry.sense': 'Sense',
  'hud.projecthero.sentry.shroud': 'Shroud',
  'hud.projecthero.sentry.meld': 'Meld',
  'hud.projecthero.sentry.x_both': 'Sonic Boom (flying: Flight Boost)',
  // messages
  'message.projecthero.sentry.acquired_hint': 'The Sun meter fuels you; keep the Void meter low. The Void only rises when you use your powers, and drains slowly; sleep and calm work (fishing, farming, trading) drain it faster. Double-tap Space to fly, H to let the Void out. Open the Guidebook for the full kit.',
  'message.projecthero.sentry.slept': 'A night\'s rest quiets the Void.',
  // guide
  'projecthero.guide.sentry.meters.body': 'Sun (the yellow bar) pays for the Sentry moves. It fills 6 a second in direct sunlight, 2 in daytime shade, 0.5 under the night sky and not at all in the dark. Sun also heals you slowly. Void (the red bar under it, darker the fuller it gets) is the danger, and it only rises when you use your powers: each move adds its own bit, and being merged with the Void adds 0.5 a second (every gain x1.5 while merged). Night, the dark, getting hit and killing things don\'t feed it. It drains slowly on its own (0.05 a second, 0.3 in sunlight), 1 a second while you sleep, and a whole night slept through takes 40 off. Calm work drains it too: a fishing bobber in the water (0.15 a second more), and each fish reeled in (2), ripe crop harvested (0.4), trade with a villager (1.5), animals bred (1.5) or music disc played (1). Golden Guardian lowers it as well. The Sentry Suit slows every gain to 60%.',
  'projecthero.guide.sentry.trial.body': 'H opens a short trial: a sun drifts up and down a dark shaft and your light falls unless you hold Space or the left mouse button. Keep the light inside the sun to fill the bar within 8 s (more Void makes the sun smaller and faster). Win and you merge with the Void: the Void moves (plus flight on double-tap Space), +50% damage, Void moves recharge 25% faster, and the Void rises faster. Lose (or close it) and the Void jumps by 15. H while merged brings you back, as long as the Void is below 90. Shift+H still opens the power wheel.',
  'projecthero.guide.sentry.ending.body': 'Clear all three rooms to throw the Void out: a golden flash, you drop to your knees, the Void resets to 40, and you can\'t use Let It Out (H) for 5 minutes. Dying also ends it (you respawn with 60 Void). Logging out ends it too, and the world still grows back.',
  'projecthero.guide.sentry.suit.body': 'The power doesn\'t dress you: craft the suit. The top (blue wool, gold ingots, a hero alloy ingot) covers your body and arms and brings a blue cape; the trousers cover your legs. Your own face and hair always show. Diamond-level protection, and it mends itself (1 durability every 3 s) so it never needs an anvil. Wearing both, the Void rises at 60% and sunlight charges the Sun 50% faster. When you merge with the Void the suit slowly fades into the red-and-black Void suit and the cape turns black; it fades back when you return. Without the suit, the Void suit and a black cape fade in over you when you merge, and fade away again when you return.',
  'projecthero.guide.sentry.body_stats.body': '40 max health (merged with the Void too), 70% less damage taken (75% merged), +12 melee (+16), 30% faster, no hunger. Merged with the Void your moves hit 50% harder. No fall damage while you fly or dive. Coming down hard from a fast flight is a superhero landing: a golden impact (a dark one merged) that hurts and throws back what is around you.',
  'projecthero.guide.sentry.takeover.body': 'At 100 Void your body is not yours. Your whole body turns black, suit and cape too, with two tiny glowing specks for eyes. The camera pulls far out behind you and the Void flies you on its own: it rises about 50 blocks above the ground and hangs there, looking down at the world it is eating. It only comes down, slowly, for players and strong enemies (bosses, and anything with 40 or more health or heavy armour) within 64 blocks: then it fights with the Void moves at full strength and its fists, but never more than one attack every 2 seconds, and goes back up when they are gone. It attacks everyone that way, your squad and your pets too. Weaker creatures are left to the spreading destruction, which hurts them every second. It never gets stuck: walls in its way, and any hole it ends up in, fade away in front of it. Creatures it kills are absorbed and heal it.',
  'item.projecthero.sentry_serum.hint': "The Sentry Serum only comes from the sealed O.X.E. serum vat in The Vault (a Tier 4 dungeon), in the vault room behind Warden Kross's arena. The vat opens when Kross falls, and gives every player one serum. You can't craft it and it isn't in any other loot. Drinking it makes you the Sentry and replaces your Primary power: flying punches that launch foes, a burst of sunlight, beams of light from your eyes, molecular disassembly, hurling foes into the sky, a sonic-boom dive, healing light, a golden shield, a golden sense and A Million Suns. Merged with the Void: shadow tendrils, Erase, shadow pools, the void sphere, nightmares, shadow clones, the Shadow Eclipse, a shroud of darkness and the shadow meld. The Guidebook has every number.",
  'item.projecthero.sentry_suit.hint': 'The Sentry Suit: a top (body, arms and a blue cape) and trousers, crafted from gold ingots, blue wool and a hero alloy ingot. Diamond-level protection. It mends 1 durability every 3 s, worn or in your inventory, so it never needs an anvil. Worn whole by the Sentry, every Void gain is cut to 60% and the Sun fills 50% faster. When he merges with the Void (Let It Out), the suit slowly fades into the Void suit and the cape turns black.',
};

/** Keys no longer used (round 3: Calm is gone). */
const REMOVE = [A + 'calm', A + 'calm.desc', 'hud.projecthero.sentry.calm', 'message.projecthero.sentry.calming'];

let text = fs.readFileSync(file, 'utf8');
const eol = text.includes('\r\n') ? '\r\n' : '\n';
const esc = (s) => JSON.stringify(s);
const keyRe = (k) => new RegExp('^  ' + esc(k).replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ': .*$', 'm');
let added = 0, changed = 0, removed = 0;
for (const k of REMOVE) {
  const re = new RegExp('^  ' + esc(k).replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ': .*(\\r?\\n)', 'm');
  if (re.test(text)) {
    text = text.replace(re, '');
    removed++;
  }
}
const lines = [];
for (const [k, v] of Object.entries(K)) {
  const line = '  ' + esc(k) + ': ' + esc(v);
  const re = keyRe(k);
  const m = text.match(re);
  if (m) {
    const comma = m[0].trimEnd().endsWith(',') ? ',' : '';
    text = text.replace(re, line + comma);
    changed++;
  } else {
    lines.push(line);
  }
}
if (lines.length) {
  // insert the new keys after the Sentry's ability block (the "return" move's desc), else before the closing brace
  const anchor = keyRe(A + 'return.desc');
  const m = text.match(anchor);
  if (m) {
    const at = text.indexOf(m[0]) + m[0].length;
    const tail = m[0].trimEnd().endsWith(',') ? '' : ',';
    text = text.slice(0, at) + tail + eol + lines.join(',' + eol) + (tail ? '' : ',') + text.slice(at);
  } else {
    throw new Error('anchor not found');
  }
  added = lines.length;
}
JSON.parse(text); // must still be valid
fs.writeFileSync(file, text);
console.log('added', added, 'changed', changed, 'removed', removed);
