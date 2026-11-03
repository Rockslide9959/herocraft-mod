// v0.17.2: Upgrade's machine-form merge messages (node scratchpad/lang_upgrade_machines_v0172.js, from the repo root)
const fs = require("fs");
const f = "src/main/resources/assets/projecthero/lang/en_us.json";
let s = fs.readFileSync(f, "utf8");
const set = {
	"message.projecthero.omnitrix.upgrade.nothing_to_merge": "Nothing to merge with (a furnace, blast furnace, smoker, dispenser, dropper, piston, note block, jukebox, observer, hopper, golem or robot, minecart, boat, or a squad mate).",
	"message.projecthero.omnitrix.upgrade.merged.furnace": "Merged: you are the Furnace Walker. R: cook what you hold at once, or a blast of fire. C to leave.",
	"message.projecthero.omnitrix.upgrade.merged.dispenser": "Merged: you are the Dispenser Turret. R: a three-arrow volley. C to leave.",
	"message.projecthero.omnitrix.upgrade.merged.piston": "Merged: you are the Piston Driver. R: a punch that hurls things (sticky: yanks). C to leave.",
	"message.projecthero.omnitrix.upgrade.merged.minecart": "Merged: you are the Hover-Cart. Faster, steps up blocks; R: ram. C to leave.",
	"message.projecthero.omnitrix.upgrade.merged.boat": "Merged: you are the Hover-Boat. Skims water; R: ram. C to leave.",
	"message.projecthero.omnitrix.upgrade.merged.note_block": "Merged: you are the Sonic Speaker. R: a sonic blast. C to leave.",
	"message.projecthero.omnitrix.upgrade.merged.jukebox": "Merged: you are the Boombox. Your music speeds up you and your squad; R: a bass drop. C to leave.",
	"message.projecthero.omnitrix.upgrade.merged.observer": "Merged: you are the Watcher. Night vision; R: scan -- everything near glows. C to leave.",
	"message.projecthero.omnitrix.upgrade.merged.hopper": "Merged: you are the Vacuum. Items drift to you; R: pull in every item nearby. C to leave."
};
for (const [k, v] of Object.entries(set)) {
	const re = new RegExp('(  "' + k.replace(/\./g, "\\.") + '": )"(?:[^"\\\\]|\\\\.)*"');
	if (re.test(s)) s = s.replace(re, (_, a) => a + JSON.stringify(v));
	else s = s.replace('  "message.projecthero.omnitrix.upgrade.merged.boat"', '  "' + k + '": ' + JSON.stringify(v) + ',\n  "message.projecthero.omnitrix.upgrade.merged.boat"');
}
JSON.parse(s);
fs.writeFileSync(f, s);
console.log("ok");
