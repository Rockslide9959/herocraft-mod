// v0.17.1 round 2: lang keys for the mech's Construct Menu (hold C in the mech), the rebind lock and the three new mech
// combos (Meteor Mace, Wrecking Whirl, Lantern Cannon). Inserts new keys before the final } of en_us.json and REPLACES
// the values of the keys listed in UPDATE (the guide page). Usage: node scratchpad/gl_mech_v0171/lang_mech_combos2.js
'use strict';
const fs = require('fs');
const path = require('path');
const f = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'lang', 'en_us.json');
const K = {
	'message.projecthero.green_lantern.mech.no_rebind': 'You cannot change your keys inside the mech -- hold left click on a construct to summon it.',
	'message.projecthero.green_lantern.mech.move_cooldown': 'That mech combo needs %s s to cool down.',
	'screen.projecthero.gl_menu.mech_banner': 'MECH MODE: keys are locked. Hold left click on a construct to summon it.',
	'screen.projecthero.gl_menu.mech_combo': 'In the mech: %s',
	'screen.projecthero.gl_menu.mech_locked': 'Keys are locked inside the mech',
	'hud.projecthero.green_lantern.mech.move.meteor_mace': 'Meteor Mace',
	'hud.projecthero.green_lantern.mech.move.wrecking_whirl': 'Wrecking Whirl',
	'hud.projecthero.green_lantern.mech.move.lantern_cannon': 'Lantern Cannon',
	'hud.projecthero.green_lantern.mech.move.giga_drill.menu': 'Giga Drill',
	'hud.projecthero.green_lantern.mech.move.katana.menu': 'Hard-Light Katana',
	'projecthero.green_lantern.combo.mech_meteor_mace': 'Meteor Mace',
	'projecthero.green_lantern.combo.mech_meteor_mace.desc': 'Emerald Mace + Mech: an overhead smash that shakes the ground',
	'projecthero.green_lantern.combo.mech_wrecking_whirl': 'Wrecking Whirl',
	'projecthero.green_lantern.combo.mech_wrecking_whirl.desc': 'Wrecking Ball + Mech: a chained ball swung round twice',
	'projecthero.green_lantern.combo.mech_lantern_cannon': 'Lantern Cannon',
	'projecthero.green_lantern.combo.mech_lantern_cannon.desc': 'Emerald Cannon + Mech: the arm becomes a cannon -- one huge shell',
};
const UPDATE = {
	'projecthero.guide.green_lantern.mech_combos.body': 'Inside the Hard-Light Mech your constructs become mech-sized moves. Press Shift + the key your construct is bound to in the Construct Menu (bound to G or Shift+G, it is Shift+G in the mech). If it is not bound, or only to H, it uses its own key: Shift+G Giga Drill, Shift+X Katana, Shift+Z Meteor Mace, Shift+V Wrecking Whirl, Shift+C Lantern Cannon (or the first free one). You can also hold C in the mech to open the Construct Menu: your keys are locked there (you cannot bind or clear them while transformed), but holding left click on a construct summons it -- and a construct with a mech combo becomes that combo instead. The mech HUD tags those keys, and holding Shift shows their icons. GIGA DRILL (Mining Drill): hold the key and the right arm becomes a huge spiral drill that spins up over 2 seconds (120 charge, then 40 a second; aim with the camera). Let go and the mech charges 10 to 26 blocks along your aim, boring a tunnel its own size and drilling through enemies with up to 8 hits each. It never breaks bedrock, obsidian, containers or anything you could not break yourself (claims and spawn protection count). Let go at full spin for GIGA DRILL BREAK: a giant drill of light, a crater and a 70-damage finishing hit (from the menu it spins up to full by itself). 15 s cooldown. HARD-LIGHT KATANA (Energy Blade): tap the key to draw a giant katana (60 charge, 6 a second while drawn) and again to sheathe it. While it is drawn R is a 3-cut chain (hold R to keep cutting), Shift+R is an iaido draw-cut whose crescent of light flies 32 blocks through enemies and cuts grass and leaves (never terrain), and G is a spinning sweep 7.5 blocks round you. METEOR MACE (Emerald Mace): a spiked mace of light forms in the fist, goes up overhead in both hands and comes down 5 blocks ahead: 48 damage in a 6-block shockwave that throws everything up and back (140 charge, 11 s). WRECKING WHIRL (Wrecking Ball): a spiked ball of light on a chain, swung round the mech twice -- every hit flings enemies outward, 8.5 blocks round you (150 charge, 12 s). LANTERN CANNON (Emerald Cannon): the left forearm becomes a huge cannon that charges for a second while you aim, then fires one shell up to 64 blocks: a 60-damage blast 4.5 blocks wide that never breaks blocks (160 charge, 13 s). It is sheathed when you climb out. Every hit scales with your Oath, Lantern Rank and Willpower Surge, and landing a mech combo earns Willpower XP.',
};
let s = fs.readFileSync(f, 'utf8');
let updated = 0;
for (const [k, v] of Object.entries(UPDATE)) {
	const re = new RegExp('("' + k.replace(/\./g, '\\.') + '": )"(?:[^"\\\\]|\\\\.)*"');
	if (re.test(s)) {
		s = s.replace(re, (m0, a) => a + JSON.stringify(v));
		updated++;
	} else {
		K[k] = v;
	}
}
const end = s.lastIndexOf('}');
let body = s.slice(0, end).replace(/\s+$/, '');
let added = 0;
for (const [k, v] of Object.entries(K)) {
	if (s.includes('"' + k + '"')) continue;
	body += ',\n  ' + JSON.stringify(k) + ': ' + JSON.stringify(v);
	added++;
}
fs.writeFileSync(f, body + '\n}\n');
JSON.parse(fs.readFileSync(f, 'utf8'));
console.log('added', added, 'updated', updated);
