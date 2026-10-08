// v0.15.15 Green Lantern: Shift+N suit screen (four suit styles + Remove Ring, which replaced Shift + hold N), plain N =
// Clear Constructs, H = Suit Up/Down (was V), V = Giant Hand (was H) / Shift+V Ring Scan, and the suit now spreading out
// of the ring hand. Run from the repo root: node scratchpad/lang_v01515_gl.js
const fs = require('fs');
const path = require('path');
const cur = JSON.parse(fs.readFileSync(path.join(__dirname, '../src/main/resources/assets/projecthero/lang/en_us.json'), 'utf8'));
// in-text swaps on existing strings (idempotent: only applied while the old wording is still there)
const swap = (key, from, to) => (cur[key] || '').includes(from) ? cur[key].replace(from, to) : cur[key];
require('./langset.js')([
	{
		anchor: 'projecthero.guide.green_lantern.dome_model.body',
		entries: {
			'projecthero.guide.green_lantern.suits': 'Suits',
			'projecthero.guide.green_lantern.suits.body': 'Press Sneak + N to open the suit screen and pick the suit your ring forms: the Default suit, the Corps Uniform, John Stewart or Classic Hal Jordan. Each card shows you wearing it. Your own face shows through every suit; the Corps Uniform and Classic Hal Jordan add a green domino mask, and John Stewart leaves your hands bare. The choice is saved. Pick one while suited and the ring re-forms the suit in the new style at once, at no charge. The Remove Ring button at the bottom of the screen (click it twice) takes the Power Ring off: the power leaves you and the ring goes back into your inventory, ready to be put on again or handed to someone else.',
		},
	},
	{
		anchor: 'projecthero.guide.green_lantern.ability.suit.desc',
		entries: {
			'projecthero.guide.green_lantern.ability.suit': 'Suit Up/Down',
			'projecthero.guide.green_lantern.ability.suit.desc': 'H forms or retracts the hard-light suit (it was on V). You hold the ring out in front of you and watch it as the suit pours out of it: it spreads up your ring arm and out over your body behind a bright green edge while the ring blazes, and recedes back into the ring when you take it off. Pick which suit it forms with Sneak + N. Full diamond-level protection (20 armour, 2.0 toughness) plus a flat +10 melee damage bonus while worn. Costs 10 charge to summon, plus 1 charge every 5 seconds while worn — running out of charge forces it to retract.',
		},
	},
	{
		anchor: 'projecthero.guide.green_lantern.ability.giant_hand.desc',
		entries: {
			'projecthero.guide.green_lantern.ability.giant_hand': 'Giant Hand / Shift: Ring Scan',
			'projecthero.guide.green_lantern.ability.giant_hand.desc': 'V (it was on H): a huge hand of light closes round whatever is under your crosshair (up to 28 blocks), lifts it in front of you and crushes it (6 damage every half-second). Press V again — or wait 6 seconds — to hurl it where you look; whatever it slams into takes 22, and so does it (10s cooldown, 50 charge). Sneak + V casts Ring Scan: every living creature within 50 blocks glows through walls for 6 seconds, red for hostiles and green for everything else — visible only to you, on a 5-second cooldown.',
		},
	},
	{
		anchor: 'projecthero.guide.green_lantern.ability.dismiss.desc',
		entries: {
			'projecthero.guide.green_lantern.ability.dismiss': 'Dismiss Constructs / Shift: Suits & Ring',
			'projecthero.guide.green_lantern.ability.dismiss.desc': 'N dismisses every construct you have up; if you are carrying something on the Rescue Tether, the first press sets it down gently. Sneak + N opens the suit screen: pick your suit, or take the Power Ring off with its Remove Ring button (see Suits).',
			// an earlier draft of this script moved Clear Constructs to Sneak + N -- put the plain-N wording back
			'projecthero.guide.green_lantern.constructs.body': swap('projecthero.guide.green_lantern.constructs.body', 'Sneak + N dismisses everything.', 'N dismisses everything.'),
		},
	},
	{
		anchor: 'projecthero.green_lantern.construct.rescue_tether.desc',
		entries: {
			'projecthero.green_lantern.construct.rescue_tether.desc': swap('projecthero.green_lantern.construct.rescue_tether.desc', 'Sneak + N sets it down gently.', 'N sets it down gently.'),
		},
	},
	{
		anchor: 'message.projecthero.green_lantern.suited_up',
		entries: {
			'message.projecthero.green_lantern.suit_style_set': 'Suit: %s (forms on your next suit-up, H)',
			'message.projecthero.green_lantern.suit_style_reform': 'Suit: %s',
			'message.projecthero.green_lantern.suit_style_same': 'Already wearing %s',
			'screen.projecthero.green_lantern_suit.title': 'Lantern Suits',
			'screen.projecthero.green_lantern_suit.intro': 'Choose the suit your ring forms. If you are suited, it re-forms at once.',
			'screen.projecthero.green_lantern_suit.footer': 'Click or 1-4 to pick. N to close.',
			'screen.projecthero.green_lantern_suit.remove_ring': 'Remove Ring',
			'screen.projecthero.green_lantern_suit.remove_ring.confirm': 'Click again to remove',
			'screen.projecthero.green_lantern_suit.remove_ring.tip': 'Take the Power Ring off: the power leaves you and the ring goes back into your inventory. Click twice.',
			'screen.projecthero.green_lantern_suit.default': 'Default',
			'screen.projecthero.green_lantern_suit.default.desc': "The ring's own hard-light suit",
			'screen.projecthero.green_lantern_suit.corps': 'Corps Uniform',
			'screen.projecthero.green_lantern_suit.corps.desc': 'Corps colours and a mask',
			'screen.projecthero.green_lantern_suit.stewart': 'John Stewart',
			'screen.projecthero.green_lantern_suit.stewart.desc': 'No mask, bare hands',
			'screen.projecthero.green_lantern_suit.classic': 'Classic Hal Jordan',
			'screen.projecthero.green_lantern_suit.classic.desc': 'The original, with a mask',
		},
	},
]);
