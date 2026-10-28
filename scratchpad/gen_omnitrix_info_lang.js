// v0.17.1 Omnitrix info pages (the Left-Alt move list, the I screen, the guide's alien pages): ONE contiguous lang block placed
// right after the main Omnitrix block (`_comment_omnitrix_end`). Re-running replaces it. Keeps the file's line endings (CRLF).
//   node scratchpad/gen_omnitrix_info_lang.js
'use strict';
const fs = require('fs');
const path = require('path');
const FILE = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'lang', 'en_us.json');

const I = 'projecthero.omnitrix.info.';
const L = {
	// ---------------------------------------------------------------- the Left-Alt move list
	'hud.projecthero.omnitrix.alt.raise': 'Raise the Omnitrix',
	'hud.projecthero.omnitrix.alt.lower': 'Lower the Omnitrix',
	'hud.projecthero.omnitrix.alt.recharging': 'Recharging: %s s',
	'hud.projecthero.omnitrix.alt.key.scroll': 'Scroll',
	'hud.projecthero.omnitrix.alt.scroll': 'Turn the dial',
	'hud.projecthero.omnitrix.alt.key.click': 'Click',
	'hud.projecthero.omnitrix.alt.slap': 'Slam the core down: transform',
	'hud.projecthero.omnitrix.alt.key.dial': 'Dial',
	'hud.projecthero.omnitrix.alt.selected': 'Selected: %s',
	'hud.projecthero.omnitrix.alt.key.attack': 'Attack',
	'hud.projecthero.omnitrix.alt.key.use': 'Use',
	'hud.projecthero.omnitrix.alt.key.sneak': 'Sneak',
	'hud.projecthero.omnitrix.alt.hold': 'Hold: %s',
	'hud.projecthero.omnitrix.alt.revert': 'Turn back',
	'hud.projecthero.omnitrix.alt.time_left': '%s left',
	// ---------------------------------------------------------------- the I screen
	'screen.projecthero.power_info.omnitrix.heading_alien': 'Omnitrix: %s',
	'screen.projecthero.power_info.omnitrix.alien_hint': 'Turn back (%s) to see every alien on the dial. The Guidebook\'s Omnitrix chapter has every alien\'s full page.',
	'screen.projecthero.power_info.omnitrix.human_hint': 'Transform and press this key again for the alien\'s full page. The Guidebook\'s Omnitrix chapter has every alien in full.',
	// ---------------------------------------------------------------- shared labels (OmnitrixInfo)
	[I + 'status']: 'Status',
	[I + 'as']: 'Transformed: %s',
	[I + 'time_left']: '%s left',
	[I + 'recharging']: 'Recharging: %s s',
	[I + 'ready']: 'Ready to transform.',
	[I + 'selected']: 'Selected on the dial: %s',
	[I + 'watch_up']: 'The watch is raised.',
	[I + 'controls']: 'The watch',
	[I + 'key.raise']: 'Raise / lower the Omnitrix (as a human)',
	[I + 'key.scroll_label']: 'Scroll',
	[I + 'key.scroll']: 'Turn the dial to the next alien (watch raised)',
	[I + 'key.click_label']: 'Click',
	[I + 'key.slap']: 'Slam the core down and transform (watch raised)',
	[I + 'key.revert']: 'As an alien: turn back early (%s s recharge)',
	[I + 'key.kit']: 'As an alien, R, G, X (movement), Z and C and their Shift versions are its moves; attack and use do something too.',
	[I + 'rules']: 'Omnitrix rules',
	[I + 'rule.timeout']: 'A transformation lasts %s. In the last %s s the watch flashes red and beeps, then you time out.',
	[I + 'rule.recharge']: 'Timing out leaves the watch recharging for %s s.',
	[I + 'rule.early']: 'Turning back early (%s) costs only a %s s recharge.',
	[I + 'rule.death']: 'Dying as an alien turns you back; the watch stays on.',
	[I + 'rule.locked']: 'Locked on: every other power is refused while you wear it.',
	[I + 'containment']: 'Containment warning',
	[I + 'containment.body']: 'Only an Omnitrix Containment Pod takes the watch off. The %s s extraction deals damage that ignores armour, enchantments, Resistance and totems: only a full-health player who just ate an Enchanted Golden Apple survives it, on 2 hearts.',
	[I + 'roster']: 'The aliens',
	[I + 'roster.how']: 'Scroll the dial to pick one. Each line shows the alien\'s role and its five tap moves.',
	[I + 'roster.selected']: '(selected)',
	[I + 'roster.stats']: 'Health %s, speed x%s, size x%s, armour +%s',
	[I + 'body']: 'Body',
	[I + 'stat.health']: 'Health', [I + 'stat.health.value']: '%s',
	[I + 'stat.speed']: 'Speed', [I + 'stat.speed.value']: 'x%s',
	[I + 'stat.size']: 'Size', [I + 'stat.size.value']: 'x%s',
	[I + 'stat.armor']: 'Armour', [I + 'stat.armor.value']: '+%s',
	[I + 'stat.toughness']: 'Toughness', [I + 'stat.toughness.value']: '+%s',
	[I + 'stat.kb']: 'Knockback resist', [I + 'stat.kb.value']: '%s%%',
	[I + 'stat.fist']: 'Fist', [I + 'stat.fist.value']: '%s dmg',
	[I + 'stat.reach']: 'Reach', [I + 'stat.reach.value']: '+%s',
	[I + 'stat.step']: 'Step', [I + 'stat.step.value']: '+%s',
	[I + 'stat.jump']: 'Jump', [I + 'stat.jump.value']: '+%s',
	[I + 'stat.safe_fall']: 'Safe fall', [I + 'stat.safe_fall.value']: '+%s blocks',
	[I + 'stat.gravity']: 'Gravity', [I + 'stat.gravity.value']: 'x%s',
	[I + 'passives']: 'Passives',
	[I + 'weaknesses']: 'Weaknesses',
	[I + 'none']: 'None.',
	[I + 'moves']: 'Moves',
	[I + 'moves.how']: 'Tap a key for its move; hold Shift for the second one.',
	[I + 'vanilla']: 'Attack / use',
	[I + 'shift']: 'Shift+%s',
	[I + 'hold']: 'Hold %s',
	[I + 'cooldown']: '%s s cooldown',
	[I + 'cooldown_left']: '(ready in %s s)',
};

// ---------------------------------------------------------------- the ten: role, passives, weaknesses
const ALIENS = {
	heatblast: {
		role: 'Ranged fire blaster and flier.',
		passive: ['Immune to fire, lava and magma.', 'Burning fists: punches set targets alight.', 'Melts blocks and smelts held items by touch.'],
		weakness: ['Water: when wet his fire goes out and fire moves fail.'],
	},
	four_arms: {
		role: 'Heavy brawler: huge punches, leaps and grabs.',
		passive: ['No fall damage from his own leaps and dives.', 'What he holds cannot hurt him.', 'Hard to knock back.'],
		weakness: ['Slower than a player.', 'A big target (x1.4 size).'],
	},
	xlr8: {
		role: 'Speedster: blinding speed and rapid strikes.',
		passive: ['Faster still while sprinting; quick hands (+2 attack speed).', 'Wall Run (Shift+X): runs up walls and across water.'],
		weakness: ['Light armour and only 22 health.'],
	},
	diamondhead: {
		role: 'Crystal tank: shards, blades and walls.',
		passive: ['40% of arrows and projectiles glance back off him.', 'Heavy armour and toughness.'],
		weakness: ['A little slow.', 'His crystal constructs are temporary.'],
	},
	upgrade: {
		role: 'Tech merger: beams, EMPs and piloting machines.',
		passive: ['Merged with a golem, it takes half the hit.', 'No suffocation while sliding through gaps.'],
		weakness: ['Merge needs a machine or vehicle within reach.'],
	},
	ghostfreak: {
		role: 'Phantom: phasing, possession and fear.',
		passive: ['Half damage from physical blows.', 'Never drowns; drifts down slowly with no fall damage to speak of.'],
		weakness: ['The sun burns him (1 every 2 s in direct sunlight).'],
	},
	stinkfly: {
		role: 'Flier: slime, toxic gas and stingers.',
		passive: ['Real flight (X or double-tap jump).', 'No fall damage and no fly-into-wall damage.'],
		weakness: ['Fragile: only 14 health and 1 armour.'],
	},
	wildvine: {
		role: 'Plant controller: vines, roots and healing.',
		passive: ['Hold still while sneaking to become a bush: mobs lose track of him.', 'Long vine reach (+1.5).', 'Regrowth heals faster in sunlight.'],
		weakness: ['Fire hurts him 35% more.'],
	},
	ripjaws: {
		role: 'Aquatic predator: bites and whirlpools.',
		passive: ['In water: fast, never drowns, sees and digs well, and hits harder (+3).'],
		weakness: ['On land he dries out: after 2 minutes he chokes until he gets wet (the meter over his keys).', 'A little slow on land.'],
	},
	grey_matter: {
		role: 'Tiny genius: pressure points, gadgets and repairs.',
		passive: ['A third of your size: fits through one-block gaps.', 'Mobs hunting him keep losing sight of him (not bosses).'],
		weakness: ['Only 8 health and no extra fist damage.'],
	},
};
for (const [id, a] of Object.entries(ALIENS)) {
	L[I + id + '.role'] = a.role;
	a.passive.forEach((t, i) => (L[I + id + '.passive.' + (i + 1)] = t));
	a.weakness.forEach((t, i) => (L[I + id + '.weakness.' + (i + 1)] = t));
}

let raw = fs.readFileSync(FILE, 'utf8');
const EOL = raw.includes('\r\n') ? '\r\n' : '\n';
let s = raw.replace(/\r\n/g, '\n');
const START = '  "_comment_omnitrix_info_start": "v0.17.1 Omnitrix info pages (Alt list, I screen, guide)",\n';
const END = '  "_comment_omnitrix_info_end": "",\n';
for (let i = s.indexOf(START); i >= 0; i = s.indexOf(START)) {
	s = s.slice(0, i) + s.slice(s.indexOf(END, i) + END.length);
}
const ANCHOR = '  "_comment_omnitrix_end": "",\n';
const at = s.indexOf(ANCHOR);
if (at < 0) throw new Error('main Omnitrix block end not found');
for (const k of Object.keys(L)) {
	if (s.includes('\n  ' + JSON.stringify(k) + ':')) throw new Error('duplicate key ' + k);
}
let block = START;
for (const [k, v] of Object.entries(L)) block += '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + ',\n';
block += END;
s = s.slice(0, at + ANCHOR.length) + block + s.slice(at + ANCHOR.length);
const parsed = JSON.parse(s);
fs.writeFileSync(FILE, s.replace(/\n/g, EOL));
console.log('lang lines', Object.keys(L).length, 'total keys', Object.keys(parsed).length, 'eol', JSON.stringify(EOL));
