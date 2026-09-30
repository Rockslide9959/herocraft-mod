// v0.14.4 lang: the boss trophy heads (wearable / placeable Grave Champion Head + Empowered Zombie Head, kill record,
// undead-disguise perk) and squad-mates fighting back against a rampaging Hulk. Run from the repo root:
//   node scratchpad/lang_v0144_grave_hulk.js
const fs = require('fs');
const path = require('path');
const FILE = path.join(__dirname, '../src/main/resources/assets/projecthero/lang/en_us.json');
const get = (k) => JSON.parse(fs.readFileSync(FILE, 'utf8'))[k];

const hulkControl = get('projecthero.guide.hulk.control.body');
const OLD_RAMPAGE = "A rampaging Hulk no longer knows friend from foe: his squad-mates are fair game too, and his blows hurt them like anyone else's (PvP permitting).";
if (!hulkControl.includes(OLD_RAMPAGE)) throw new Error('hulk control text changed -- update this script');
const squadsBody = get('projecthero.guide.squads.body');
const SQUAD_ADD = ' The one exception is a Hulk on a rampage: he stops being anyone\'s squad-mate until it ends, so he can hurt his squad and they can hurt him back (PvP permitting).';

require('./langset.js')([
	// ---------------------------------------------------------------- trophy heads
	{ anchor: 'block.projecthero.grave_champion_head', entries: {
		'block.projecthero.grave_champion_head': 'Grave Champion Head',
		'block.projecthero.grave_champion_wall_head': 'Grave Champion Head',
		'block.projecthero.empowered_zombie_head': 'Empowered Zombie Head',
		'block.projecthero.empowered_zombie_wall_head': 'Empowered Zombie Head',
	} },
	{ anchor: 'item.projecthero.boss_trophy.named', entries: {
		'item.projecthero.boss_trophy.hint': 'A trophy taken from a Powered Zombie Boss. Wear it, or place it on a floor or a wall.',
		'item.projecthero.boss_trophy.record': 'Slain by %s on day %s',
		'item.projecthero.boss_trophy.perk': 'Worn: zombies notice you from half as far away',
	} },
	{ anchor: 'item.projecthero.final_boss_trophy.named', entries: {
		'item.projecthero.final_boss_trophy.perk': 'Worn: all undead notice you from half as far away',
	} },
	// ---------------------------------------------------------------- guide: Zombie Raid
	{ anchor: 'projecthero.guide.zombie_raid.bosses.body', entries: {
		'projecthero.guide.zombie_raid.bosses.body': 'An Empowered Zombie fights with a real power, not a bigger health bar. It picks its targets, follows fliers, closes on archers and saves its area attacks for crowds. Kill one for a heap of Grave Essence and a chance at its head. The wave-12 boss always drops the Grave Champion Head.',
	} },
	{ anchor: 'projecthero.guide.zombie_raid.rewards.body', entries: {
		'projecthero.guide.zombie_raid.trophies': 'Trophy heads',
		'projecthero.guide.zombie_raid.trophies.body': 'A boss\'s head is named for its power ("Geokinetic Zombie Head") and remembers who took it and on which day. Its eyes (and the Champion\'s crown gem) glow in the colour of the power\'s family. Wear it (shift-click it into the head slot, right-click the air, or let a dispenser put it on you) and it sits on your head like a mob head: an Empowered Zombie Head makes zombies notice you from half as far away, and the horned, crowned Grave Champion Head does that for every undead. Place it on the floor or mount it on a wall to build a trophy wall. Right-click a placed head to read its plaque. Breaking it keeps its power and record.',
	} },
	// ---------------------------------------------------------------- Hulk + squads
	{ anchor: 'projecthero.guide.hulk.control', entries: {
		'projecthero.guide.hulk.control.body': hulkControl.replace(OLD_RAMPAGE,
			"A rampaging Hulk no longer knows friend from foe: squad protection between him and his squad-mates is off both ways until the rampage ends. His blows hurt them like anyone else's, and they can fight back with weapons and powers, every ability that normally spares a squad-mate included (PvP permitting). The rest of the squad still can't hurt each other."),
	} },
	{ anchor: 'projecthero.guide.squads.body', entries: {
		'projecthero.guide.squads.body': squadsBody.includes('rampage') ? squadsBody : squadsBody + SQUAD_ADD,
	} },
]);
console.log('lang v0.14.4 grave/hulk applied');
