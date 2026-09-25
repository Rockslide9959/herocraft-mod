// Lang updates for v0.12.33 (All Might / One For All).
const fs = require('fs');
const f = 'src/main/resources/assets/projecthero/lang/en_us.json';
const j = JSON.parse(fs.readFileSync(f, 'utf8'));
function set(k, v) { j[k] = v; }

set('item.projecthero.one_for_all_vestige', 'Vestige of One For All');
set('item.projecthero.one_for_all_vestige.desc1', 'A stockpile of power handed down through generations. Use it to become All Might.');
set('item.projecthero.one_for_all_vestige.desc2', 'Grants the One For All power. H transforms, R/G/X/Z/V/C are the Smashes, N leaps.');
set('item.projecthero.all_might_helmet', "All Might's Hair and Smile");
set('item.projecthero.all_might_chestplate', "All Might's Costume");
set('item.projecthero.all_might_leggings', "All Might's Trousers");
set('item.projecthero.all_might_boots', "All Might's Boots");
set('projecthero.squad.identity.all_might', 'All Might');

set('projecthero.all_might.ability.transform', 'Transform (Full Power)');
set('projecthero.all_might.ability.detroit_smash', 'Detroit Smash');
set('projecthero.all_might.ability.texas_smash', 'Texas Smash');
set('projecthero.all_might.ability.carolina_smash', 'Carolina Smash');
set('projecthero.all_might.ability.new_hampshire_smash', 'New Hampshire Smash');
set('projecthero.all_might.ability.full_cowl', 'Full Cowl');
set('projecthero.all_might.ability.united_states_of_smash', 'United States of Smash');
set('projecthero.all_might.ability.all_might_leap', 'All Might Leap');

set('message.projecthero.all_might.acquired', 'One For All surges through you -- you are ALL MIGHT!');
set('message.projecthero.all_might.acquired_hint', 'H transforms into your full-power form. R Detroit, G Texas, Z Carolina, X New Hampshire, C Full Cowl, V United States of Smash, N Leap.');
set('message.projecthero.all_might.already', 'You already carry One For All.');
set('message.projecthero.all_might.full_power', 'FULL POWER!');
set('message.projecthero.all_might.contained', 'You rein the power back in.');
set('message.projecthero.all_might.cooldown', '%s is recharging (%ss)');
set('message.projecthero.all_might.low_ofa', 'Not enough OFA: %s needed, you have %s.');
set('message.projecthero.all_might.cowl_active', 'Full Cowl is already active.');
set('message.projecthero.all_might.cowl', 'FULL COWL!');
set('message.projecthero.all_might.armour_stowed', '%s was put in your inventory to make room for the costume.');
set('hud.projecthero.all_might.title', 'ONE FOR ALL');
set('hud.projecthero.all_might.form_full', 'FULL POWER');
set('hud.projecthero.all_might.form_base', 'Contained');
set('hud.projecthero.all_might.ofa', 'OFA: %s / %s');
set('hud.projecthero.all_might.cowl', 'Full Cowl: %ss');
set('hud.projecthero.all_might.transforming', 'Transforming...');

set('projecthero.guide.all_might', 'All Might / One For All');
set('projecthero.guide.all_might.tier', 'Hero Tier -- a Primary power (you can hold two).');
set('projecthero.guide.all_might.body', 'The strongest pure-strength hero in the pack: enormous punches that move air, shockwaves that flatten crowds, leaps that clear buildings and one ultimate Smash that levels a battlefield. He is faster, tougher and stronger than any mutation, and he wears his own costume -- blond hair, blue suit, white gloves, red boots and a cape -- that swells to a huge muscular silhouette in Full Power.');
set('projecthero.guide.all_might.progression', 'Getting it');
set('projecthero.guide.all_might.step.vestige', 'Craft a Vestige of One For All (4 Titanium-Gold Plates in the corners, 2 Enchanted Golden Apples above and below, 2 Diamond Blocks left and right, a Nether Star in the middle) and use it. Like every Primary power it replaces your oldest one if both slots are full. Admins: /projecthero allmight grant.');
set('projecthero.guide.all_might.ofa', 'OFA Power');
set('projecthero.guide.all_might.ofa.body', 'A 100-point reserve shown as OFA: 100 / 100 on the HUD. Every Smash spends some (Detroit 10, Texas 15, Carolina 20, New Hampshire 25, Full Cowl 20, United States of Smash 100, Leap 5) and you cannot cast without enough. It refills 1 point every 0.75 s, and 2.5x faster once you have gone 5 s without fighting.');
set('projecthero.guide.all_might.forms', 'Two forms (H)');
set('projecthero.guide.all_might.forms.body', 'Contained (default): +40 HP, +25 melee damage, +25% speed, 80% knockback resistance, jumps twice as high, 75% less fall damage and 35% less damage from everything. Full Power (press H, and again to change back -- no cooldown): +35 melee, +40% speed, total knockback immunity, jumps 2.5x as high, 90% less fall damage, 50% less damage, and your Smashes hit 15% harder. The transformation takes 1.5 s during which you cannot be hurt. Full Cowl (C) is separate: 10 s of +75% speed, +50% melee, extra jump height, +20% further damage reduction and a green-white aura.');
set('projecthero.guide.all_might.controls', 'Controls');
set('projecthero.guide.all_might.ability.transform', 'H: toggle between the Contained and Full Power forms. Persistent until you press H again.');
set('projecthero.guide.all_might.ability.detroit_smash', 'R: a devastating close-range punch -- 50 damage, 5 blocks long and 3 wide, huge knockback, breaks a few weak blocks. 10 OFA, 3 s cooldown.');
set('projecthero.guide.all_might.ability.texas_smash', 'G: a wide travelling air blast -- 70 damage to everything in a 10-block-long, 5-wide wave, very strong knockback. 15 OFA, 6 s cooldown.');
set('projecthero.guide.all_might.ability.carolina_smash', 'Z: a high-speed dash 10 blocks forward, hitting everything in your path for 60 with strong knockback and leaving a wind trail. It stops at walls. 20 OFA, 5 s cooldown.');
set('projecthero.guide.all_might.ability.new_hampshire_smash', 'X: launch 15 blocks up and forward, striking whatever you fly through for 80, then crash down in a 6-block shockwave. Works from the air too. 25 OFA, 8 s cooldown.');
set('projecthero.guide.all_might.ability.full_cowl', 'C: 10 s of One For All at its peak -- speed, punch, jump and defence all up, Smashes 10% stronger. Cannot be stacked. 20 OFA, 20 s cooldown.');
set('projecthero.guide.all_might.ability.united_states_of_smash', 'V: the ultimate. A 1.5 s charge (growing aura, building wind), then a gigantic punch: 250 damage in a 15-block cone with extreme knockback and a crater, followed by a 25-block outer shockwave. Bosses only take a fraction of their health per hit. 100 OFA (a full bar), 60 s cooldown.');
set('projecthero.guide.all_might.ability.all_might_leap', 'N: a huge leap -- about 17 blocks up with forward momentum -- and a landing shockwave. Cannot hurt you. 5 OFA, 5 s cooldown.');
set('projecthero.guide.all_might.passives', 'Passives');
set('projecthero.guide.all_might.passives.body', 'Ordinary punches already hit like a truck and knock enemies back, with an air burst; sprinting punches hit harder still. Hard landings from a real fall release a shockwave: a small one (2 damage, 2 blocks), a medium one (8, 4 blocks) or a heavy one (20, 7 blocks). His own launches can never hurt him. Bosses are never knocked back and lose at most 10% of their health to a single Smash. Block destruction (weak blocks only, never bedrock) can be switched off in AllMightConfig.');
set('projecthero.guide.all_might.commands', 'Admin commands');
set('projecthero.guide.all_might.commands.body', '/projecthero allmight grant | remove | form | ofa <0-100> | status  (op level 2). Every number above lives in AllMightConfig.');

j['projecthero.guide.overview.body']=j['projecthero.guide.overview.body'].replace('Wolverine and the Titan Shifter','Wolverine, the Titan Shifter and All Might');
fs.writeFileSync(f, JSON.stringify(j, null, 2) + '\n');
console.log('lang updated');
