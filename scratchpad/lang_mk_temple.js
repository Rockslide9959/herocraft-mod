// Moon Knight Phase 7 lang keys (Temple of Khonshu, Scarab, altar, ritual, advancement, guide).
// Run from the repo root: node scratchpad/lang_mk_temple.js
require('./langset.js')([
	{
		anchor: null,
		entries: {
			'item.projecthero.scarab_of_khonshu': 'Scarab of Khonshu',
			'item.projecthero.scarab_of_khonshu.desc1': 'A golden scarab bearing a silver crescent, cold as moonlight.',
			'item.projecthero.scarab_of_khonshu.desc2': "Lay it on the Altar of Khonshu beneath the night sky, then kneel upon the altar.",
			'item.projecthero.scarab_of_khonshu.desc3': 'Found only in the hidden chamber of a Temple of Khonshu.',
			'block.projecthero.khonshu_altar': 'Altar of Khonshu',

			'message.projecthero.khonshu.altar.empty_hint': 'A scarab-shaped hollow is carved into the altar. It waits for the night.',
			'message.projecthero.khonshu.altar.holding_hint': 'The scarab glows. Stand upon the altar and kneel (sneak).',
			'message.projecthero.khonshu.altar.spent_hint': 'The altar is cracked and silent. Its pact has been made.',
			'message.projecthero.khonshu.refuse.spent': 'The altar is cracked and silent. Khonshu will not answer here again.',
			'message.projecthero.khonshu.refuse.busy': 'A scarab already rests upon this altar.',
			'message.projecthero.khonshu.refuse.already': 'You already serve Khonshu.',
			'message.projecthero.khonshu.refuse.day': 'The altar is dark. Khonshu answers only at night.',
			'message.projecthero.khonshu.refuse.no_sky': 'The altar cannot see the sky. Khonshu cannot see you.',

			'message.projecthero.khonshu.speaks': 'Khonshu: %s',
			'message.projecthero.khonshu.ritual.placed': 'The scarab settles into the altar and begins to glow. Stand upon the altar and kneel.',
			'message.projecthero.khonshu.ritual.wait': 'Stand upon the altar and kneel (sneak) before Khonshu.',
			'message.projecthero.khonshu.ritual.kneel': 'Kneel before Khonshu %s',
			'message.projecthero.khonshu.ritual.line1': 'You who have fallen... do you hear me?',
			'message.projecthero.khonshu.ritual.line2': 'The moon sees what others hide.',
			'message.projecthero.khonshu.ritual.line3': 'I have watched you in the dark. You did not look away.',
			'message.projecthero.khonshu.ritual.line4': 'Rise, and protect those who travel at night.',
			'message.projecthero.khonshu.ritual.line5': 'Will you be my fist?',
			'message.projecthero.khonshu.ritual.death': 'Then die... and be reborn.',
			'message.projecthero.khonshu.ritual.rise': 'Rise, my Moon Knight. The night is yours.',
			'message.projecthero.khonshu.cancel.broke': 'You rose too soon. The scarab falls from the altar.',
			'message.projecthero.khonshu.cancel.left': 'You turned away. The scarab falls from the altar.',
			'message.projecthero.khonshu.cancel.dawn': 'Dawn breaks and the moon departs. The scarab falls from the altar.',
			'message.projecthero.khonshu.cancel.no_sky': 'The sky is hidden. The scarab falls from the altar.',
			'message.projecthero.khonshu.cancel.interrupted': 'The ritual was interrupted. The scarab falls from the altar.',

			'advancement.projecthero.moon_knight.pact.title': 'Fist of Khonshu',
			'advancement.projecthero.moon_knight.pact.description': "Die on Khonshu's altar and rise again as the moon's avenger",

			'projecthero.guide.structure.temple_of_khonshu': 'Temple of Khonshu',
			'projecthero.guide.structure.temple_of_khonshu.desc': "A rare sandstone temple found only in deserts, marked by a silver crescent over its door. Its roof is open to the moon above the Altar of Khonshu, and a hidden chamber beneath holds the Scarab of Khonshu. Lay the scarab on the altar at night and kneel to become Moon Knight.",
		},
	},
]);
