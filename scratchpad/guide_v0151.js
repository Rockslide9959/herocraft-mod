// v0.15.1 guidebook: an "Armour Rules" Iron Man page + GL dome text. Run from the repo root after builds finish.
const fs = require('fs');
const L = 'src/main/resources/assets/projecthero/lang/en_us.json';
const add = {
	'projecthero.guide.iron_man.armour_rules': 'Armour Rules',
	'projecthero.guide.iron_man.armour_rules.body': 'Iron Man armour only goes on with C or a suit deploy, and only comes off with C, a Suit Platform retrieve or a send-home -- it can\'t be dragged in or out of your armour slots like normal armour. Every suit is bulletproof (guns do nothing while the chestplate is on) and gives 70% knockback resistance. Repulsors reach 50 blocks, and from the Mark 3 up the mob highlight switches on by itself when you put the helmet on. Marks 3, 6 and 7 repair 1 integrity per second while worn.',
	'projecthero.guide.green_lantern.dome_model': 'Protective Dome',
	'projecthero.guide.green_lantern.dome_model.body': 'Sneak + Z throws up a glowing hard-light dome around you: a green globe with a lattice of light that grows out over a second and a half, pushes out anyone who isn\'t in your squad, and flickers when it is close to breaking.',
};
let t = fs.readFileSync(L, 'utf8');
const end = t.lastIndexOf('}');
let body = t.slice(0, end).replace(/\s+$/, '');
for (const [k, v] of Object.entries(add)) body += ',\r\n  ' + JSON.stringify(k) + ': ' + JSON.stringify(v);
t = body + '\r\n}\r\n';
JSON.parse(t);
fs.writeFileSync(L, t);

const G = 'src/main/java/com/projecthero/mod/hero/guide/HeroPackGuide.java';
let g = fs.readFileSync(G, 'utf8');
const old = '"remote_pilot"}) { // v0.14.29';
if (g.split(old).length !== 2) throw new Error('iron man section list not found');
g = g.replace(old, '"remote_pilot", "armour_rules"}) { // v0.15.1: + armour rules; v0.14.29');
fs.writeFileSync(G, g);
console.log('ok');
// GL chapter: add the dome page after the air-tank page
let g2 = fs.readFileSync(G, 'utf8');
const anchor = '\t\t\tpara(lines, "projecthero.guide.green_lantern.air_tank.body");';
if (g2.split(anchor).length !== 2) throw new Error('gl anchor');
g2 = g2.replace(anchor, anchor + '\r\n\t\t\thead(lines, "projecthero.guide.green_lantern.dome_model"); // v0.15.1\r\n\t\t\tpara(lines, "projecthero.guide.green_lantern.dome_model.body");');
fs.writeFileSync(G, g2);
console.log('gl ok');
