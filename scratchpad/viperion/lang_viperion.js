// v0.16.7: inserts Viperion's lang keys into en_us.json (CRLF kept, JSON.parse checked, no duplicate keys).
// Two blocks: the ability names / descs after the last Pegasus ability, everything else after the Pegasus guide food body.
// Also adds viperion to the shared Miraculous commands line. Usage: node scratchpad/viperion/lang_viperion.js
'use strict';
const fs = require('fs');
const path = require('path');
const FILE = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero/lang/en_us.json');

const ABILITIES = {
	'projecthero.miraculous.ability.viperion_lyre_strike': 'Lyre Strike',
	'projecthero.miraculous.ability.viperion_lyre_strike.desc': 'Three quick blows of the Lyre on what is in front of you: 7, 7, then 11 damage and a knockback. 1.5 s cooldown.',
	'projecthero.miraculous.ability.viperion_lyre_sweep': 'Lyre Sweep',
	'projecthero.miraculous.ability.viperion_lyre_sweep.desc': 'One wide swing: everything within 4.5 blocks in front of you takes 12 damage and is knocked back. 5 s cooldown.',
	'projecthero.miraculous.ability.viperion_sonic_chord': 'Sonic Chord',
	'projecthero.miraculous.ability.viperion_sonic_chord.desc': 'Strum once: a cone of sound 12 blocks long along your look. Everything in it takes 10 damage and is blown back. 6 s cooldown.',
	'projecthero.miraculous.ability.viperion_resonance': 'Resonance',
	'projecthero.miraculous.ability.viperion_resonance.desc': 'A ring of sound round you: everything within 6 blocks takes 4 damage and is stunned for 2 s. 15 s cooldown.',
	'projecthero.miraculous.ability.viperion_serpent_dash': 'Serpent Dash',
	'projecthero.miraculous.ability.viperion_serpent_dash.desc': 'Slither 12 blocks along the ground, low and fast, under swings and arrows. No fall damage on the landing. 5 s cooldown.',
	'projecthero.miraculous.ability.viperion_coil_leap': 'Coil Leap',
	'projecthero.miraculous.ability.viperion_coil_leap.desc': 'Coil and spring forward and up, about 14 blocks. No fall damage on the landing. 6 s cooldown.',
	'projecthero.miraculous.ability.viperion_venom_strike': 'Venom Strike',
	'projecthero.miraculous.ability.viperion_venom_strike.desc': 'A fang-like jab of the Lyre: 6 damage and strong Poison (Poison III, 8 s). 5 s cooldown.',
	'projecthero.miraculous.ability.viperion_second_chance': 'SECOND CHANCE',
	'projecthero.miraculous.ability.viperion_second_chance.desc': 'Set a checkpoint (it starts the countdown). For 90 s, Shift+Z again rewinds you, your squad near you, the mobs and the blocks within 32 blocks back to it. 3 rewinds. A killing blow rewinds by itself.',
	'projecthero.miraculous.ability.viperion_constrict': 'Constrict',
	'projecthero.miraculous.ability.viperion_constrict.desc': 'The Lyre\'s strings wrap the creature you aim at (14 blocks): rooted for 4 s and squeezed for 2.5 damage every half second. Bosses are only slowed. 12 s cooldown.',
	'projecthero.miraculous.ability.viperion_string_shield': 'String Shield',
	'projecthero.miraculous.ability.viperion_string_shield.desc': 'The Lyre spins before you for 6 s: hits and projectiles from the front are blocked (arrows bounce back). 15 s cooldown.',
	'projecthero.miraculous.ability.viperion_heat_sense': 'Heat Sense',
	'projecthero.miraculous.ability.viperion_heat_sense.desc': 'Toggle: snake infrared. Every living thing within 32 blocks glows through walls in warm colours, only for you. Press again to stop.',
	'projecthero.miraculous.ability.viperion_hiss': 'Hiss',
	'projecthero.miraculous.ability.viperion_hiss.desc': 'A loud hiss: every mob within 10 blocks runs from you for 5 s. Bosses are not scared. 20 s cooldown.',
	'message.projecthero.viperion.no_target': 'No creature in your sights.',
	'message.projecthero.viperion.checkpoint': 'Checkpoint set: Shift+Z rewinds to here for %s s (%s times).',
	'message.projecthero.viperion.rewind': 'Rewound. %s left.',
	'message.projecthero.viperion.rewind_spent': 'Rewound. Second Chance is spent.',
	'message.projecthero.viperion.rewind_auto': 'A killing blow... rewound! %s left.',
	'message.projecthero.viperion.rewind_auto_spent': 'A killing blow... rewound! Second Chance is spent.',
	'message.projecthero.viperion.checkpoint_gone': 'The checkpoint has slipped away.',
	'hud.projecthero.viperion.second_chance': 'x%s  %s',
};

const CORE = {
	'projecthero.calling.kwami.sass': 'Sass',
	'projecthero.calling.hero.viperion': 'Viperion',
	'entity.projecthero.kwami.sass': 'Sass',
	'projecthero.hero_tier.viperion': 'Viperion',
	'projecthero.squad.identity.viperion': 'Viperion',
	'projecthero.guide.viperion': 'Viperion',
	'item.projecthero.snake_bracelet': 'Snake Bracelet',
	'message.projecthero.miraculous.fed.sass': 'Thank you. An egg, and patience: the rest follows in its own time.',
	'message.projecthero.miraculous.tired.sass': 'Even a snake must rest. An egg, when you are ready... no hurry.',
	'message.projecthero.miraculous.intro.sass.1': 'Hello. I am Sass, Kwami of Intuition. The Snake Bracelet chose you, and so do I.',
	'message.projecthero.miraculous.intro.sass.2': 'Say "Sass, scales slither!" in chat and you will become Viperion, who is given a second chance.',
	'message.projecthero.miraculous.intro.sass.3': '"Scales rest!" brings you back. Afterwards I will be hungry: an egg, please. Choose wisely; time is precious.',
	'hud.projecthero.miraculous.hungry.sass': 'Sass is hungry: an egg, or 10 of any food',
	'projecthero.guide.viperion.tier': 'Hero-Tier power (Primary): the Miraculous of the Snake',
	'projecthero.guide.viperion.body': 'Sass, the Kwami of Intuition, lives in the Snake Bracelet. Say his phrase in chat and he turns you into Viperion, the teal musician-hero with a Lyre that is a harp and a club, with six moves on R, G, X, Z, V and C (each with a Shift version) -- and Second Chance, the power to turn time back.',
	'projecthero.guide.viperion.jewel': 'The Bracelet',
	'projecthero.guide.viperion.jewel.body': 'The Snake Bracelet is one of the jewels in the Miraculous Box. Right-click it: Sass pops out, says hello and Viperion becomes your Primary power, replacing any other. The jewel is used up. From then on you wear the bracelet on your right wrist: a band of green and black scales, the snake\'s head biting its tail, brighter while you are transformed.',
	'projecthero.guide.viperion.kwami': 'Sass',
	'projecthero.guide.viperion.kwami.body': 'While you are not transformed, Sass floats over your shoulder and goes wherever you go: caves, water, flying, riding. He looks where you look and drifts round you when you stand still, his tail swaying. Nothing can hurt him, and if he is ever left behind he comes straight back to you. Press N to tuck Sass away out of sight (N again calls them back out); you can only transform while Sass is out. Hold Shift+N for 5 seconds to take the Miraculous off: the Kwami goes back into its jewel, which lands in your inventory (not while transformed or while the Kwami is hungry).',
	'projecthero.guide.viperion.transform': 'Transforming',
	'projecthero.guide.viperion.transform.body': 'Type "Sass, scales slither!" -- or just "Scales slither!" -- in chat to transform (capitals and punctuation don\'t matter). Sass spins into the Bracelet, ribbons of teal light wrap round you and, with a flash, you are Viperion: the suit coils on from the bracelet, the Lyre across your back. Type "Scales rest!" (or "Sass, scales rest!") to change back. H does not transform.',
	'projecthero.guide.viperion.food': 'Feeding Sass',
	'projecthero.guide.viperion.food.body': 'Every time you change back (by the phrase, the countdown or dying), Sass is worn out and hungry, and won\'t transform you again until he has eaten an egg. Right-click him with it, or right-click with it in your hand while he is hungry and he eats it instead (it is not thrown). No favourite food on you? Right-click the Kwami with 10 pieces of any other food instead (hand them over a few at a time if you like). The favourite takes 10 seconds to eat, the 10 other pieces 22 seconds, and you can transform once the meal is finished.',
	'projecthero.guide.viperion.second_chance': 'Second Chance',
	'projecthero.guide.viperion.second_chance.body': 'Transformed, poison does not take on you. Shift+Z sets a checkpoint where you stand (a green flash, a ring on your HUD) and starts the Miraculous countdown. For the next 90 seconds Shift+Z again rewinds: you go back to the checkpoint with your health, hunger, effects, fire and air as they were then; squadmates who were near are put back too; every mob that was within 32 blocks returns to where it was with its health then, mobs killed since come back (never bosses), new hostile mobs and new drops vanish, and blocks broken or placed nearby are put back. Other players are never moved, and your inventory stays as it is. Three rewinds, then it is spent. If a killing blow lands while the checkpoint is live, it rewinds by itself instead (using one). The HUD shows the rewinds left and the time left.',
};

let s = fs.readFileSync(FILE, 'utf8');
const EOL = s.includes('\r\n') ? '\r\n' : '\n';
const existing = JSON.parse(s);
for (const k of [...Object.keys(ABILITIES), ...Object.keys(CORE)]) {
	if (k in existing) throw new Error('duplicate key ' + k);
}
const line = (k, v) => '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + ',';
function insertAfter(anchor, obj) {
	const lines = s.split(EOL);
	const i = lines.findIndex(l => l.startsWith('  "' + anchor + '":'));
	if (i < 0) throw new Error('anchor not found ' + anchor);
	if (!lines[i].endsWith(',')) throw new Error('anchor is the last line');
	lines.splice(i + 1, 0, ...Object.entries(obj).map(([k, v]) => line(k, v)));
	s = lines.join(EOL);
}
insertAfter('projecthero.miraculous.ability.pegasus_linked_portals.desc', ABILITIES);
insertAfter('projecthero.guide.pegasus.food.body', CORE);
s = s.replace('ladybug, cat_noir, rena_rouge, hawk_moth, carapace or pegasus,', 'ladybug, cat_noir, rena_rouge, hawk_moth, carapace, pegasus or viperion,');
const parsed = JSON.parse(s);
fs.writeFileSync(FILE, s);
console.log('ok, keys now ' + Object.keys(parsed).length);
