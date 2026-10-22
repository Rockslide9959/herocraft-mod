// v0.16.7 (user): "right now its an all might power, change it to a one for all power please, change all instances of it
// besides the all might clothes" -- every player-facing "All Might" that names the POWER becomes "One For All". The
// costume (All Might's Costume / Trousers / Costume Locker, its guide page, the armour-refused line) keeps the name, and
// internal ids (all_might, one_for_all) stay so saves keep working. Usage: node scratchpad/rename_allmight_to_ofa_v0167.js
'use strict';
const fs = require('fs');
const path = require('path');
const f = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'lang', 'en_us.json');
let s = fs.readFileSync(f, 'utf8');
const KEEP = /chestplate|leggings|locker|costume|armour_refused/;
const EXACT = {
	'projecthero.guide.quirk.one_for_all': 'One For All',
	'projecthero.quirk.one_for_all.hero': 'One For All',
	'projecthero.hero_tier.all_might': 'One For All',
	'projecthero.guide.all_might': 'One For All',
	'projecthero.squad.identity.all_might': 'One For All',
	'item.projecthero.one_for_all_vestige.desc1': 'A stockpile of power handed down through generations. Use it to inherit One For All.',
};
let n = 0;
s = s.replace(/^(\s*")([^"]+)(": ")((?:[^"\\]|\\.)*)(",?\r?)$/gm, (m, a, k, b, v, e) => {
	if (KEEP.test(k) || !v.includes('All Might')) {
		return m;
	}
	const nv = EXACT[k] != null ? EXACT[k]
		: v.replace(/One For All \(All Might\)/g, 'One For All').replace(/All Might's Power Form/g, "One For All's Power Form")
			.replace(/All Might/g, 'One For All');
	if (nv !== v) {
		n++;
		console.log(k + ' => ' + nv.slice(0, 110));
	}
	return a + k + b + nv + e;
});
JSON.parse(s);
fs.writeFileSync(f, s);
console.log('changed ' + n);
