// v0.14.4: one Primary power at a time (was two since v0.11.15). Re-runnable: sets keys, keeps file formatting.
const fs = require('fs');
const P = 'src/main/resources/assets/projecthero/lang/en_us.json';
const raw = fs.readFileSync(P, 'utf8');
const j = JSON.parse(raw);
const set = {
	'message.projecthero.one_power_limit': 'Project Hero now allows one power at a time -- you keep your newest hero power.',
	'message.projecthero.mutation.replaced_hero_tier': 'The serum takes hold — your hero power gives way to the mutation.',
	'projecthero.guide.powerclass.body': 'Every power is either Primary or Secondary. Primary powers are who you are: Thor, Iron Man, Spider-Man, Max Steel, the Punisher, Green Lantern, Wolverine, the Titan Shifter, All Might, the Hulk, Moon Knight and the 27 mutations. You hold ONE Primary power at a time (v0.14.4). The mutations count as one group that shares that single slot (up to 3 of them stack, as before). Gaining any hero power -- however you get it, lifting Mjolnir included -- replaces the hero power or mutations you had: become Spider-Man, then lift Mjolnir, and you are Thor and nothing else. Gaining a mutation replaces your hero power. Secondary powers ride on top of a Primary one. The Symbiote is the only Secondary power for now. It shares a host only with Spider-Man (Black Suit) or the Punisher (Agent Venom), so gaining any other Primary power also removes the Symbiote. Worlds saved under the old two-power rule keep only their newest hero power the next time you join.',
	'projecthero.guide.mutation.capacity': 'You may permanently own up to 3 minor mutations (server-configurable). Only one occupies the six ability slots at a time — switch with the power wheel (H). Switching never resets cooldowns or duplicates passives. The mutations share your single Primary slot: a serum adds a mutation to the ones you carry, but replaces any hero power you hold (and removes a bonded Symbiote). In the other direction, gaining any hero power replaces every mutation you carry. A Power Suppressor strips everything — every hero power, every mutation and a bonded Symbiote.',
	'projecthero.guide.thor.bind': 'Binding makes Thor your one Primary power: every other hero power and any mutations you carry are replaced, and a bonded Symbiote is removed. It only ever happens at the moment of lifting -- once Hero of the Village runs out, an unbound hammer will not move for you again until you earn the effect again. Someone else who lifts a hammer already bound to another player may carry it while their own effect lasts, but it never binds to them and the owner is untouched. Once you are Thor you can pick up your own hammer freely, and Sneak + right-click still unbinds and rebinds it.',
	'projecthero.guide.titan_shifter.step.serum': 'Craft a Titan Serum (4 Titanium-Gold Plates in the corners, 2 Netherite Ingots above and below, 2 Magma Blocks left and right, a Golden Apple in the middle) and use it. It only UNLOCKS the power -- it does not transform you. Like every Primary power it replaces the power you had.',
	'projecthero.guide.all_might.step.vestige': 'Craft a Vestige of One For All (4 Titanium-Gold Plates in the corners, 2 Enchanted Golden Apples above and below, 2 Diamond Blocks left and right, a Totem of Undying in the middle) and use it. Like every Primary power it replaces the power you had. Admins: /projecthero power grant all_might.',
	'item.projecthero.random_serum.rule': 'Mutations need a free mutation slot and replace a hero power; a hero power replaces whatever you had.',
};
const replaceIn = {
	'projecthero.guide.hulk.origin.body': ['Like every hero power it replaces your oldest Primary power; it also takes Thor away, and becoming Thor takes it away.',
		'Like every hero power it replaces the power you had (Thor included).'],
	'projecthero.guide.mutation.random_serums': ['Like stacking a power: a mutation needs a free slot, a hero power takes a Primary slot.',
		'A mutation needs a free mutation slot and replaces any hero power; a hero power replaces whatever you had.'],
};
for (const k in set) j[k] = set[k];
for (const k in replaceIn) {
	const [a, b] = replaceIn[k];
	if (j[k].includes(a)) j[k] = j[k].replace(a, b);
	else if (!j[k].includes(b)) throw new Error('unexpected text in ' + k);
}
const indent = raw.match(/\n(\s+)"/)[1];
let out = JSON.stringify(j, null, indent.includes('\t') ? '\t' : indent.length);
if (raw.includes('\r\n')) out = out.replace(/\n/g, '\r\n');
if (/\r?\n$/.test(raw)) out += raw.includes('\r\n') ? '\r\n' : '\n';
fs.writeFileSync(P, out);
console.log('lang v0.14.4 one-power: ok');
