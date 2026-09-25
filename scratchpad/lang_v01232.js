// Lang updates for v0.12.32 (Thor armour + perks, Titan Energy / H key / new key layout, Spider-Man Impact Web cocoon).
const fs = require('fs');
const f = 'src/main/resources/assets/projecthero/lang/en_us.json';
const j = JSON.parse(fs.readFileSync(f, 'utf8'));
function set(k, v, mustExist) { if (mustExist && !(k in j)) throw new Error('missing key ' + k); j[k] = v; }
function sub(k, a, b) { if (!(k in j)) throw new Error('missing key ' + k); if (!j[k].includes(a)) throw new Error('missing text in ' + k + ': ' + a); j[k] = j[k].replace(a, b); }

// ---- Spider-Man ----
set('projecthero.spider_man.ability.impact_web.desc', 'Combat Mode. One heavy ball of webbing: 10 damage, a hard knockback, and whatever it hits is wrapped in a web cocoon for 12 seconds (bosses only briefly). A target thrown into a wall is pinned to it as well. 1 second cooldown.', true);
set('projecthero.guide.spider_man.combat.z', 'Z  Impact Web -- one heavy ball of webbing: 10 damage and a hard knockback, and whatever it hits is wrapped in a web cocoon for 12 seconds; a mob knocked into a wall is pinned to it too. 1-second cooldown.', true);

// ---- Thor ----
sub('projecthero.guide.thor.body', '(+10 melee, +10 hearts, permanent Resistance III, 250 Storm Energy)', '(+11 melee, +10 hearts, 80% less damage from everything, permanent Regeneration I, 250 Storm Energy)');
set('projecthero.guide.thor.perks', 'The Power of Thor');
set('projecthero.guide.thor.perks.body', 'Being bound to Mjolnir gives you, permanently and whether or not the hammer is in your hand: +11 melee damage bare-handed, +10 hearts, +18% speed, some knockback resistance, 80% less damage from every source (falls and lightning cannot hurt you at all) and Regeneration I at all times. Mjolnir itself hits for 11, swung or thrown. Your ability bar stays on screen for as long as you are bound to a hammer, even when Mjolnir is not in your hands.');
set('projecthero.guide.thor.armour', "Thor's Armour");
set('projecthero.guide.thor.armour.body', "Press H while bound to Mjolnir: lightning strikes down on you and Thor's Armour forms on your body -- a black-and-crimson chestplate, leggings and boots (anything you were wearing in those slots goes into your inventory). Press H again to dismiss it. The armour is conjured, not crafted, so it can never leave you: if it falls out of your inventory or you die, it simply despawns. Shift+H still opens the power wheel.");
set('item.projecthero.thor_armor_chestplate', "Thor's Armour");
set('item.projecthero.thor_armor_leggings', "Thor's Greaves");
set('item.projecthero.thor_armor_boots', "Thor's Boots");
set('item.projecthero.thor_armor_chestplate.desc', 'Conjured by pressing H while bound to Mjolnir. Vanishes if it leaves your inventory or you die.');
set('message.projecthero.thor_armor.equipped', "Lightning answers your call -- Thor's Armour forms around you.");
set('message.projecthero.thor_armor.dismissed', 'The armour crackles away.');

// ---- Titan Shifter ----
set('item.projecthero.titan_serum.desc2', 'Use to drink. Press H afterwards to transform (needs 90% Titan Energy).', true);
set('message.projecthero.titan_shifter.unlocked_hint', 'Press H to transform (you need 90% Titan Energy). Inside the Titan: R Punch (Shift+R Kick), G Heavy Smash, Z Stomp, X Leap (Shift+X Roar), V Roar, C Regeneration (Shift+C Hardening).', true);
set('message.projecthero.titan_shifter.transformed', 'You are a Titan. Press H to change back.', true);
set('message.projecthero.titan_shifter.low_energy', 'Not enough Titan Energy: %s / %s needed. It refills 1% a second while you are human.');
set('hud.projecthero.titan_shifter.energy', 'Titan Energy %s / %s');
set('hud.projecthero.titan_shifter.charging', 'Charging Titan Energy...');
delete j['key.projecthero.titan_shift'];
set('projecthero.guide.titan_shifter.shift_key', 'Titan Shift -- transform / change back');
sub('projecthero.guide.titan_shifter.body', 'burst into an 11-block giant -- a real creature', 'burst into an 11-block giant -- a regular player-shaped body scaled up, and a real creature');
set('projecthero.guide.titan_shifter.transform.body', 'Press H on open ground with at least 90% Titan Energy (the Titan needs room -- leaves and plants in the way are blown aside, solid blocks are not). Lightning flashes without harm, bystanders are shoved back, and for about 3 seconds you are helpless while the Titan swells up around you. Press H again to change back (2 seconds of steam). Shift+H still opens the power wheel while you are human. There is no lockout after reverting -- the Titan Energy bar is the limit.', true);
set('projecthero.guide.titan_shifter.energy', 'Titan Energy');
set('projecthero.guide.titan_shifter.energy.body', "A 100-point bar under your HUD (the white tick is the 90% you need to transform). Changing back -- or being defeated or forced out -- empties it, and it then refills 1% every second while you are human. Inside the Titan it does not refill: the Titan's base regeneration heals 3 HP every second whenever it is hurt and spends 2 Titan Energy a second to do it, and stops when the bar runs dry.");
set('projecthero.guide.titan_shifter.ability.punch', 'R: a heavy swing: 20 damage and strong knockback, 0.8 s recharge. The third swing of a combo is a Heavy Punch (35). Hold Shift while pressing it for a Titan Kick (30, a wide low sweep).', true);
set('projecthero.guide.titan_shifter.ability.stomp', 'Z: lifts a foot and stamps: 25 damage and knockback to everything within 6 blocks, dust and a tremor. 6 s recharge.', true);
set('projecthero.guide.titan_shifter.ability.leap', 'X: a crouch, then a huge jump (about three times a normal jump, about 20 blocks forward). Landing hits everything within 5 blocks for 20. 5 s recharge. Shift+X is the Titan Roar.', true);
set('projecthero.guide.titan_shifter.ability.roar', 'V (or Shift+X): a deafening roar: everything within 12 blocks is slowed and weakened, weaker mobs are thrown back and flee. Bosses only take a quarter of the effect and are not knocked back. 15 s recharge.', true);
set('projecthero.guide.titan_shifter.ability.regeneration', 'C: the Titan heals 10 HP per second for 10 seconds on top of its base regeneration (steam pours off it). 45 s recharge. Useless at full health.', true);
set('projecthero.guide.titan_shifter.ability.hardening', 'Shift+C: the skin crystallises for 8 seconds -- 60% less damage and the Titan looks like hardened crystal. 30 s recharge.', true);
sub('projecthero.guide.titan_shifter.ability.heavy_smash', 'The Titan raises', 'G: the Titan raises');
sub('projecthero.guide.titan_shifter.stats.body', '11 blocks tall and 4 wide.', "11 blocks tall and about 3.7 wide (a regular player's hit-box, scaled up).");
sub('projecthero.guide.titan_shifter.defeat.body', 'and locked out of shifting for 60 seconds.', 'and your Titan Energy is empty, so you must recharge it (1% a second) before shifting again.');

fs.writeFileSync(f, JSON.stringify(j, null, 2) + '\n');
console.log('lang updated');
