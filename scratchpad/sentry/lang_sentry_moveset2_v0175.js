// v0.17.5 Sentry & the Void, moveset 2 (2026-11-18): adds / updates / removes the en_us keys one by one (never rewrites the file
// wholesale: other branches touch it too). Keeps the file's line endings and 2-space indent.
//   node scratchpad/sentry/lang_sentry_moveset2_v0175.js
'use strict';
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero/lang/en_us.json');

const A = 'projecthero.sentry.ability.';
const K = {
  // the Sentry
  [A + 'golden_fury']: 'Golden Fury',
  [A + 'golden_fury.desc']: 'A three-punch combo (22, 22). Out of reach (up to 9 blocks) you lunge in on a golden streak. The third punch is the Haymaker (34): it launches the creature far, and if it slams into a wall it takes 12 more. Costs 2 Sun a punch.',
  [A + 'solar_gaze']: 'Solar Gaze',
  [A + 'solar_gaze.desc']: 'Hold: twin beams of light from your eyes, 40 blocks, 7 every 4 ticks and they set targets alight. After 2 s held they burn hotter (x1.5). Up to 5 s, 5 Sun a second. It can clash with other beams.',
  [A + 'disassemble']: 'Disassemble',
  [A + 'disassemble.desc']: 'Hold G on a creature (20 blocks): it is lifted in a golden field (bosses are pinned) and starts to unravel (2 every 5 ticks). Keep holding for 2 s and it comes apart (50, +1.7 Void); a kill turns it into golden light. Let go sooner to throw it (12). Costs 10 Sun.',
  [A + 'skyward_hurl']: 'Skyward Hurl',
  [A + 'skyward_hurl.desc']: 'Grab the creature in reach (6 blocks) and throw it straight up into the sky (16, then the fall). Bosses are too big to throw: they are slammed instead (30 and slowed). Costs 14 Sun, 10 s cooldown.',
  [A + 'sunfall']: 'Sunfall',
  [A + 'sunfall.desc']: 'From the ground you leap high first; in the air you dive at once. Fist first into the ground: a sonic-boom slam, 34 within 6 blocks and 12 out to 11. Costs 12 Sun, 7 s cooldown.',
  [A + 'million_suns.desc']: 'A 4 s wind-up rising into the air (you take half damage meanwhile), then an explosion of light: 150 at the centre to 70 at 14 blocks, and everything burns. Costs 60 Sun, +8 Void. 60 s cooldown.',
  [A + 'golden_guardian']: 'Golden Guardian',
  [A + 'golden_guardian.desc']: 'A pulse of healing light (10 blocks): you, your squad, your pets, villagers and golems heal 8, lose bad effects and get Regeneration and Absorption. The undead in the light burn (12). Lowers the Void by 8, and 1 more for each one helped. Costs 20 Sun.',
  [A + 'molecular_shield']: 'Molecular Shield',
  [A + 'molecular_shield.desc']: 'A golden aura for 8 s: enemy projectiles that reach it turn into light (+2 Sun each), you take 40% less damage, and anything that hits you up close is thrown back (6). Costs 15 Sun.',
  // the Void
  [A + 'tendril_lash']: 'Tendril Lash',
  [A + 'tendril_lash.desc']: 'Two whips of shadow tendrils through everything in front of you (6 blocks, 16 each), then the Impale: one tendril spears a creature up to 9 blocks out (30), pins it and drags it in. +0.3 Void a swing.',
  [A + 'erase.desc']: 'Seize the creature you look at (24 blocks): it freezes and darkens, and a moment later it is wiped into a burnt shadow (110). A kill leaves its shadow on the ground. +2 Void, 20 s cooldown.',
  [A + 'shadow_pool']: 'Shadow Pool',
  [A + 'shadow_pool.desc']: 'Shadow spreads over the ground where you look (24 blocks), out to 6 blocks, for 6 s. Anything standing in it is held down (no jumping, slowed) and takes 4 every half second. +1 Void.',
  [A + 'void_sphere']: 'Swallow',
  [A + 'void_sphere.desc']: 'Shut the creature you look at (16 blocks) inside a black void sphere for 3 s: held, blind, 6 every half second, then spat out (18). If it dies inside you absorb it (+6 health). +1.7 Void.',
  [A + 'nightmare']: 'Nightmare',
  [A + 'nightmare.desc']: 'A wave of horror (12 blocks, 8 each): figures of shadow rise among them, mobs drop what they were doing and flee for 5 s, players see darkness and reel. All are weakened. +1.7 Void.',
  [A + 'shadow_eclipse']: 'Shadow Eclipse',
  [A + 'shadow_eclipse.desc']: 'You rise as the dark gathers, then a black sun opens over you for 10 s: everything within 16 blocks takes 6 a second and sees darkness while you regenerate 2 a second. It ends in a collapse (40). +3 Void, 60 s cooldown.',
  [A + 'blot_out']: 'Blot Out',
  [A + 'blot_out.desc']: 'Drain the life and light around you (10 blocks, 10 each): you heal 3 for each one (up to 15), and whatever Sun you have left burns into health too (1 per 5 Sun). +1 Void.',
  [A + 'shadow_clones']: 'Shadow Clones',
  [A + 'shadow_clones.desc']: 'Three shadow doubles rise from your shadow for 8 s and hunt what is near you (16 blocks), striking for 10 every second. +2 Void, 30 s cooldown.',
  [A + 'shadow_step.desc']: 'Blink up to 24 blocks along your look through a smear of shadow (it stops short of walls). Anything it passes through takes 10 and sees darkness. +0.35 Void.',
  // HUD, guide, JEI
  'hud.projecthero.sentry.eclipse': 'Eclipse',
  'item.projecthero.sentry_serum.hint': "The Sentry Serum only comes from the sealed O.X.E. serum vat in The Vault (a Tier 4 dungeon), in the vault room behind Warden Kross's arena. The vat opens when Kross falls, and gives every player one serum. You can't craft it and it isn't in any other loot. Drinking it makes you the Sentry and replaces your Primary power: flying punches that launch foes, beams of light from your eyes, molecular disassembly, hurling foes into the sky, a sonic-boom dive, healing light, a golden shield and A Million Suns. Merged with the Void: shadow tendrils, Erase, shadow pools, the void sphere, nightmares, shadow clones and the Shadow Eclipse. The Guidebook has every number.",
  'projecthero.guide.sentry.takeover.body': 'At 100 Void your body is not yours. Your whole body turns black, suit and cape too, with two tiny white eyes. The camera pulls far out behind you and the Void flies you on its own: it rises about 50 blocks above the ground and hangs there, looking down at the world it is eating. It only comes down, slowly, for players and strong enemies (bosses, and anything with 40 or more health or heavy armour) within 64 blocks: then it fights with the Void moves at full strength and its fists, but never more than one attack every 2 seconds, and goes back up when they are gone. It attacks everyone that way, your squad and your pets too. Weaker creatures are left to the spreading destruction, which hurts them every second. It never gets stuck: walls in its way, and any hole it ends up in, fade away in front of it. Creatures it kills are absorbed and heal it.',
  'projecthero.guide.sentry.body': 'Bob Reynolds has the power of a million exploding suns, and the Void is his other half. You fight with flying punches, light from your eyes, molecular control and healing light while keeping a darkness inside you in check. Let it grow too far and it takes your body.',
};

/** Keys no longer used (moveset 1's moves, Shadow Step's old light rule, the old Sun Dive message). */
const REMOVE = [
  'golden_strike', 'sunbeam', 'molecular_grip', 'unmake', 'sun_dive', 'healing_light', 'sentinel_shield', 'shadow_lash', 'grasp', 'void_rift',
  'nightfall',
].flatMap((id) => [A + id, A + id + '.desc']).concat(['hud.projecthero.sentry.nightfall', 'message.projecthero.sentry.too_bright',
  'message.projecthero.sentry.dive_ground']);

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
