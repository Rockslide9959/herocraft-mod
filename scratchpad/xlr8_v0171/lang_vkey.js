// v0.17.1: lang for the N revert, every alien's V move and the XLR8 revamp. node scratchpad/xlr8_v0171/lang_vkey.js
// Parse -> assign -> stringify (2-space indent, CRLF, the trailing newline kept), the way the repo's lang tooling writes it.
'use strict';
const fs = require('fs'), path = require('path');
const f = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'lang', 'en_us.json');
const raw = fs.readFileSync(f, 'utf8');
const o = JSON.parse(raw);
const set = (k, v) => { o[k] = v; };
const del = k => { delete o[k]; };
const O = 'projecthero.omnitrix.';
const M = 'message.projecthero.omnitrix.';

// ---- the keys: N turns back, V is a kit key
set('projecthero.guide.omnitrix.controls.body', "Press R and you raise your wrist to your face: the core pops up and the face changes to the selected alien's dial icon. Turn the dial with the scroll wheel, a quarter turn per click (the hotbar stays put). Click (left or right) to slam the core down: the green flash bursts round you and you become that alien. Press R again to lower the watch without transforming. As an alien, R, G, X, Z, V and C are your alien's moves (V is each alien's utility or mode move) and N turns you back early.");
set('projecthero.guide.omnitrix.timer.body', o['projecthero.guide.omnitrix.timer.body'].replace('(V shows the face red with the time left)', '(raising it with R shows the face red with the time left)'));
set('projecthero.guide.omnitrix.key.activate', 'raise / lower the watch (human)');
set('screen.projecthero.power_info.omnitrix.keys', 'R raises the watch, scroll turns the dial, click transforms; as an alien V is a move and N turns you back.');
set(O + 'info.key.kit', 'As an alien, R, G, X (movement), Z, V (utility / mode) and C and their Shift versions are its moves; attack and use do something too.');
set(O + 'info.roster.how', "Scroll the dial to pick one. Each entry shows the alien's role and its six tap moves.");

// ---- V moves
set(O + 'heatblast.heat_aura', 'Heat Aura');
set(O + 'heatblast.heat_aura.desc', 'Toggle: burn white-hot. Creatures within 3.5 blocks catch fire (1 a second), snow and ice round you melt, and items dropped near you come out smelted. Water puts it out.');
set(M + 'heatblast.aura_on', 'Heat Aura on: a walking furnace.');
set(M + 'heatblast.aura_off', 'Heat Aura off.');
set(O + 'four_arms.wall_climb', 'Wall Climb');
set(O + 'four_arms.wall_climb.desc', 'Toggle: four hands grip any wall. Walk into one to climb it (Shift to let go). No fall damage while it is on.');
set(M + 'four_arms.climb_on', 'Wall Climb on: walk into a wall to climb it.');
set(M + 'four_arms.climb_off', 'Wall Climb off.');
set(O + 'diamondhead.crystal_regrowth', 'Crystal Regrowth');
set(O + 'diamondhead.crystal_regrowth.desc', 'Root yourself for 3 s while green crystal knits over every crack: 1.5 health every half second (9 in all) and half damage meanwhile.');
set(M + 'diamondhead.whole', 'Not a crack on you.');
set(O + 'ghostfreak.spirit_sight', 'Spirit Sight');
set(O + 'ghostfreak.spirit_sight.desc', 'Toggle: see in the dark, and every living thing within 32 blocks glows through walls. Only you see the outlines.');
set(M + 'ghostfreak.sight_on', 'Spirit Sight: the living glow.');
set(M + 'ghostfreak.sight_off', 'Spirit Sight off.');
set(O + 'stinkfly.lift', 'Lift');
set(O + 'stinkfly.lift.desc', 'Snatch the creature in front of you (4.5 blocks) up in your legs and carry it under you as you fly. V again drops it, from wherever you are. Bosses and big creatures are too heavy.');
set(M + 'stinkfly.no_target', 'Nothing in reach to lift.');
set(M + 'stinkfly.too_heavy', 'Too heavy to lift.');
set(O + 'ripjaws.water_bubble', 'Water Bubble');
set(O + 'ripjaws.water_bubble.desc', 'Gulp a bubble of water round your head: for 30 s dry land is like rain to you (the hydration meter slowly refills instead of draining).');
set(M + 'ripjaws.bubble_on', 'Water Bubble: 30 s of breathing easy.');
set(M + 'ripjaws.bubble_off', 'The Water Bubble bursts.');
set(O + 'grey_matter.pocket_workshop', 'Pocket Workshop');
set(O + 'grey_matter.pocket_workshop.desc', 'A Galvan needs no table: a crafting grid opens wherever you stand. Anything left in it comes back to you.');
set('container.projecthero.omnitrix.pocket_workshop', 'Pocket Workshop');
set(O + 'upgrade.overclock', 'Overclock');
set(O + 'upgrade.overclock.desc', 'Flow into the tool or weapon in your hand for 15 s: +100% mining speed, +4 damage, and its wear mends a point a second. Needs something in your main hand.');
set(M + 'upgrade.needs_gear', 'Hold a tool or weapon to overclock.');
set(M + 'upgrade.overclock_on', 'Overclocked: %s.');

// ---- XLR8 revamp
for (const k of ['dash_flurry', 'sonic_punch', 'wall_run']) {
	del(O + 'xlr8.' + k);
	del(O + 'xlr8.' + k + '.desc');
}
del(M + 'xlr8.wall_run_on');
del(M + 'xlr8.wall_run_off');
set(O + 'xlr8.rapid_assault', 'Rapid Assault');
set(O + 'xlr8.rapid_assault.desc', 'Four punches of 5 in a blur on everything in front of you; every blow lands in full.');
set(O + 'xlr8.mach_punch', 'Mach Punch');
set(O + 'xlr8.mach_punch.desc', 'One punch at the speed of sound: 20 and a huge knockback on the creature in front, a 6-damage shockwave round it. The faster you run, the farther it flies.');
set(O + 'xlr8.blitz', 'Blitz');
set(O + 'xlr8.blitz.desc', 'Zip to the creature you aim at (24 blocks) in a streak of afterimages and hit it for 20; everything round it takes 10. No target, no cooldown.');
set(O + 'xlr8.tornado_run', 'Tornado Run');
set(O + 'xlr8.tornado_run.desc', 'Run rings round where you stand for 3 s: a twister lifts and spins everything within 6 blocks, then flings it out (4).');
set(O + 'xlr8.blur_dash', 'Blur Dash');
set(O + 'xlr8.blur_dash.desc', 'A 10-block dash that leaves afterimages behind and hits what you pass (3).');
set(O + 'xlr8.speed_sweep', 'Speed Sweep');
set(O + 'xlr8.speed_sweep.desc', 'Blink from foe to foe through everything hostile within 50 blocks (15 each), then back to exactly where you stood. Shift+X again calls it off.');
set(O + 'xlr8.phase', 'Phase');
set(O + 'xlr8.phase.desc', 'Hold X: vibrate your molecules and run through walls on your own level, untouchable, for up to 5 s. Letting go puts you back outside the blocks.');
set(O + 'xlr8.speed_mode', 'Speed Mode');
set(O + 'xlr8.speed_mode.desc', 'Toggle: the visor slams down and you run flat out (twice as fast again), leaving an after-image trail. Run straight up walls and across water (Shift to stop or sink), step over a block and a half, no fall damage, slow healing.');
set(O + 'xlr8.afterimage_decoys', 'Afterimage Decoys');
set(O + 'xlr8.afterimage_decoys.desc', 'Three afterimages of you stay behind for 6 s; every foe after you goes for them instead.');
set(O + 'xlr8.snatch', 'Snatch');
set(O + 'xlr8.snatch.desc', "Right-click a mob with an empty hand: the item in its hand is yours before it can blink (2 damage).");
set(M + 'xlr8.speed_on', 'Speed Mode: visor down.');
set(M + 'xlr8.speed_off', 'Speed Mode off: visor up.');
set('projecthero.guide.omnitrix.alien.xlr8', "A Kineceleran: a sleek blue-and-black raptor of a speedster with a visor helmet, a tail and ball feet, and the Speedster's moves -- Rapid Assault, Mach Punch, Blitz, Speed Sweep, Phase and Afterimage Decoys. V drops the visor into Speed Mode: flat-out speed with an after-image trail, up walls and across water. Quick hands, but light armour.");
set(O + 'info.xlr8.role', "Speedster: the plain Speedster's moves, blinding speed and rapid strikes.");
set(O + 'info.xlr8.passive.1', 'Faster still while sprinting; quick hands (+2 attack speed).');
set(O + 'info.xlr8.passive.2', 'Speed Mode (V): the visor down, twice as fast again, an after-image trail, wall and water running, step assist, no fall damage, slow healing.');
set(O + 'info.xlr8.passive.3', 'Phase (hold X): through walls and untouchable while it lasts.');

fs.writeFileSync(f, JSON.stringify(o, null, 2).replace(/\n/g, '\r\n') + (raw.endsWith('\r\n') ? '\r\n' : ''));
console.log('lang written');
