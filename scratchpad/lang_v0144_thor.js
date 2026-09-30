// Lang updates for v0.14.4 Thor (squad safety, piece-by-piece armour, move animations).
// Re-runnable: it only ever sets keys, so it can be run again after a merge conflict in en_us.json.
// Run from the repo root: node scratchpad/lang_v0144_thor.js
const fs = require('fs');
const f = 'src/main/resources/assets/projecthero/lang/en_us.json';
const raw = fs.readFileSync(f, 'utf8');
const crlf = raw.includes('\r\n');
const j = JSON.parse(raw);
function set(k, v) { j[k] = v; }

// ---- ability descriptions (the Guidebook's Thor chapter and the power info screen both read these) ----
set('projecthero.guide.thor.ability.lightning_strike',
	'Mjolnir is thrust to the sky and a 22-damage lightning bolt crashes down on whatever you are aiming at -- with a soft aim assist onto the nearest enemy if your aim is slightly off -- then arcs on to nearby hostile mobs. The bolt goes straight past a squadmate standing in the way.');
set('projecthero.guide.thor.ability.lightning_beam',
	'Brace the hammer out in front of you and channel a thick, crackling beam of lightning from its head for 8 damage per tick while held. It passes through your squadmates without touching them.');
set('projecthero.guide.thor.ability.god_of_thunders_wrath',
	'Hold for 5 seconds to charge (a bar shows the buildup): you raise Mjolnir to the sky, lightning gathers on it and, past half charge, bolts reach down into it. On release you bring it down and a barrage of lightning hits the point you are aiming at -- 100 damage and a shockwave rolling out across 7 blocks. Drains 100 Storm Energy. Your squad is never caught in the blast.');
set('projecthero.guide.thor.ability.thunderclap',
	'A two-handed overhead slam: a 7-block shockwave races out over the ground, dealing 22 damage and knocking back and slowing every enemy around you. Costs 10 Storm Energy. Squadmates are left completely alone -- no damage, no shove, no slow.');
set('projecthero.guide.thor.ability.hammer_volley',
	'Fling Mjolnir up and away: it autonomously strikes every enemy within 25 blocks in sequence, closest first -- never your squadmates or their pets. If only one is in range, it orbits and re-strikes them every 2 seconds instead; with nothing in range it circles you at a 3-block radius until a target appears. Lasts 12 seconds, or until you call the hammer back early. 32-second cooldown.');
set('projecthero.guide.thor.ability.chain_lightning',
	'Thrust the hammer forward and a crackling bolt strikes everyone in a cone in front of you for 18 damage, arcing from target to target. It skips your squadmates and their pets.');
set('projecthero.guide.thor.ability.call_mjolnir',
	'Summons Mjolnir to your hand from anywhere. A plain right-click throws it instead -- no key needed -- and a thrown hammer flies straight past your squadmates. If it is already somewhere in your inventory, it swaps straight into your main hand and whatever you were holding takes its old slot. A recalled hammer lands in your main hand if it is free, otherwise the next open inventory slot -- never the off hand -- and if your hand and inventory are both full, it goes into your hand anyway and drops what you were holding. Also ends Hammer Volley early.');

// ---- perks ----
set('projecthero.guide.thor.perk.squad',
	'Squad-safe: none of your lightning, shockwaves or hammer throws ever hurt, shove, slow or burn your squadmates or their pets');

// ---- Thor's Armour ----
set('projecthero.guide.thor.armour.body',
	"Press H while bound to Mjolnir: you raise the hammer to the sky, lightning strikes down on you and Thor's Armour forms piece by piece -- the boots, then the greaves, then the black-and-crimson chestplate and cape -- each one sweeping up your body behind a crackling edge of light as its own bolt lands. You are protected by the whole set from the first moment (anything you were wearing in those slots goes into your inventory). Press H again and it crackles away, chest first. The armour is conjured, not crafted, so it can never leave you: if it falls out of your inventory or you die, it simply despawns. Shift+H still opens the power wheel.");
set('message.projecthero.thor_armor.equipped', "Lightning answers your call -- Thor's Armour forms around you, piece by piece.");

let out = JSON.stringify(j, null, 2) + '\n';
if (crlf) {
	out = out.replace(/\n/g, '\r\n');
}
fs.writeFileSync(f, out);
console.log('lang v0.14.4 Thor: done');
