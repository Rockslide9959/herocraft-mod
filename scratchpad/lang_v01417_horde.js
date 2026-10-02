// v0.14.17: Bone Tyrant 2,000 health, Brood Queen 2,400, the Tyrant's arrows vanish after 5 s.
const fs = require('fs');
const path = require('path');
const lang = JSON.parse(fs.readFileSync(path.join(__dirname, '../src/main/resources/assets/projecthero/lang/en_us.json'), 'utf8'));
function swap(key, from, to) {
	const v = lang[key];
	if (v.includes(to)) return v;
	if (!v.includes(from)) throw new Error(key + ' does not contain: ' + from);
	return v.replace(from, to);
}
require('./langset.js')([{ entries: {
	'projecthero.guide.hordes.spider.body': swap('projecthero.guide.hordes.spider.body', 'with 4,000 health', 'with 2,400 health'),
	'projecthero.guide.hordes.skeleton.body': swap('projecthero.guide.hordes.skeleton.body',
		'Boss: the Bone Tyrant, a seven-block lich-king stronger than the Titan --',
		'Boss: the Bone Tyrant, a seven-block lich-king with 2,000 health, stronger than the Titan --'),
} }]);
const f = path.join(__dirname, '../docs/CURSEFORGE_DESCRIPTION.md');
let d = fs.readFileSync(f, 'utf8');
d = d.replace('a 4,000-health spider queen', 'a 2,400-health spider queen')
	.replace('**the Bone Tyrant**, a 7-block lich-king with ten', '**the Bone Tyrant**, a 2,000-health, 7-block lich-king with ten');
fs.writeFileSync(f, d);
console.log('horde text ok');
