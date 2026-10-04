// v0.14.21: Iron Man recipe cost pass -- the guidebook's Fabricator paragraph was stale (it still said
// components are never table-craftable and that the Fabricator only runs on Reactor Cores). Re-runnable:
// it sets the value outright, so a second run is a no-op.
const fs = require('fs');
const f = 'src/main/resources/assets/projecthero/lang/en_us.json';
const j = JSON.parse(fs.readFileSync(f, 'utf8'));
const set = (key, value) => {
	if (j[key] === undefined) throw new Error('missing ' + key);
	j[key] = value;
};
set('projecthero.guide.iron_man.recipes',
	'Stark components craft at a normal crafting table or in the Stark Fabricator (same ingredients either way, and most make 2-4 at a time). '
	+ 'Every suit piece from the Mark 2 up is fabricated only, with that mark\'s blueprint in the slot -- pick the piece and "View more" lists exactly what it needs. '
	+ 'Each piece uses the Fabricator\'s full energy buffer, which refills on its own in 2.5 minutes (a Reactor Core or Arc Reactor in an input slot tops it up faster). '
	+ 'The Mark 1 is built at a normal crafting table from Metal Plating.');
fs.writeFileSync(f, JSON.stringify(j, null, 2) + '\n');
console.log('ok');
