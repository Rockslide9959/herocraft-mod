// v0.17.3 Undead kit: the guidebook's Z / V rows, the katana's skills table and the shoulder sword / meteor sections.
const fs = require('fs');
const f = 'src/main/java/com/projecthero/mod/hero/guide/HeroPackGuide.java';
let t = fs.readFileSync(f, 'utf8');
const crlf = t.includes('\r\n');
t = t.replace(/\r\n/g, '\n');
function rep(a, b) {
	if (!t.includes(a)) throw new Error('not found: ' + a.slice(0, 90));
	t = t.replace(a, b);
}
rep(`"undead", "passives", "bone_sword", "suit", "victor"}`, `"undead", "passives", "bone_sword", "meteor", "shoulder_sword", "suit", "victor"}`);
rep(`"undead", "passives", "bone_sword", "suit"}) {`, `"undead", "passives", "bone_sword", "meteor", "shoulder_sword", "suit"}) {`);
rep(`					{ "Shift+Z", "parts_bullet_head" }, { "V", "reform" }, { "Hold V", "bone_sword" }, { "Shift+V", victorOn ? "pull_the_card" : "regen_speed" }, { "Shift + hold V", "regen_menu" },`,
	`					{ "Shift + hold Z", "unluck_meteor" }, { "V", "reform" }, { "Hold V", "bone_sword" }, { "Shift+V", "rib_cage_guard" },
					{ "Shift + right click", "shoulder_sword" },`);
rep(`			head(lines, "projecthero.guide.undead.sword_controls"); // v0.17.3: the keys while the Bone Sword is out
			for (String[] row : new String[][] { { "R", "bone_slash" }, { "Shift+R", "marrow_thrust" }, { "G", "blood_edge_wave" }, { "Shift+G", "rib_cage_guard" },
					{ "X", "blood_jet_dash" }, { "Z", "bone_splinter_volley" }, { "Shift+Z", "impale_throw" }, { "C", "bone_parry" }, { "V", "bone_sword_away" },
					{ "Shift+V", "regen_speed" } }) {`,
	`			head(lines, "projecthero.guide.undead.sword_controls"); // v0.17.3: the keys while the katana is out
			for (String[] row : new String[][] { { "R", "katana_crescent_moon" }, { "G", "katana_great_crescent_moon" }, { "Z", "crimson_flash" },
					{ "C", "blood_whirl" }, { "V / Shift+V", "bone_sword_away" } }) {`);
if (crlf) t = t.replace(/\n/g, '\r\n');
fs.writeFileSync(f, t);
console.log('ok');
