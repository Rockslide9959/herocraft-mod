// v0.14.21: the Pillager Spy marks the PLAYER (Supervillain's Mark -> Supervillain Omen -> raid), like Bad Omen.
// Idempotent: sets each key to its final value. Run from the repo root: node scratchpad/lang_v01421_spy_mark.js
const fs = require('fs');
const f = 'src/main/resources/assets/projecthero/lang/en_us.json';
const j = JSON.parse(fs.readFileSync(f, 'utf8'));

const set = {
	'effect.projecthero.supervillain_mark': "Supervillain's Mark",
	'effect.projecthero.supervillain_mark.desc':
		'A Pillager Spy marked you. Enter a village and a Supervillain Raid follows. Milk clears it.',
	'effect.projecthero.supervillain_omen': 'Supervillain Omen',
	'effect.projecthero.supervillain_omen.desc':
		'The Supervillain has found this village. When this runs out, the raid begins.',
	'event.projecthero.supervillain_raid.spy_marked': "A Pillager Spy has marked you.",
	'event.projecthero.supervillain_raid.omen': 'YOU WERE FOLLOWED',
	'event.projecthero.supervillain_raid.omen.sub': 'Supervillain Raid in %s seconds',
	'event.projecthero.supervillain_raid.omen.warn': 'Supervillain Raid in %s seconds!',
	'projecthero.guide.supervillain_raid.spy.body':
		'Every so often a lone Pillager appears in the wild, sometimes with an escort, and heads for the nearest '
		+ 'village. Look closely: the robe is darker, a faint purple particle drifts from it now and then, and its '
		+ 'name shows on your crosshair. It is scouting for a Supervillain, and it is after you: it ignores villagers '
		+ 'and leaves alone anyone it has already marked.',
	'projecthero.guide.supervillain_raid.mark': "The Supervillain's Mark",
	'projecthero.guide.supervillain_raid.mark.body':
		"Like Bad Omen: if the Spy's bolt hits you, or you kill the Spy, you get the Supervillain's Mark (100 "
		+ 'minutes, milk clears it). Walk into a village with it and it becomes a 30-second Supervillain Omen, then '
		+ 'the village is Marked for Attack. If a Supervillain Raid is already running there, you keep the mark until '
		+ 'it is over. Nothing happens on Peaceful. An ordinary Pillager does nothing.',
	'projecthero.guide.supervillain_raid.prepare.body':
		'Once the village is Marked for Attack, you have 10 minutes before the first wave. Warnings sound at 10, 5 '
		+ 'and 1 minute and count down the final seconds. Use the time to build defences, protect the villagers, '
		+ 'gather supplies, equip your gear and call for help. Leaving the village does not cancel the attack.',
};
for (const [k, v] of Object.entries(set)) j[k] = v;
fs.writeFileSync(f, JSON.stringify(j, null, 2) + '\n');
console.log('set ' + Object.keys(set).length + ' keys');
