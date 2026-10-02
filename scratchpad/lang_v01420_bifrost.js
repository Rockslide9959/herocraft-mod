// v0.14.20 Stormbreaker Bifrost screen: idempotent en_us.json update.
// Replaces existing keys in place, drops the two keys the old aim-at-a-block Bifrost used, and inserts new keys after
// "message.projecthero.bifrost.carried". Text-based, so the rest of the file's formatting is untouched.
// Run from the repo root: node scratchpad/lang_v01420_bifrost.js
const fs = require("fs");
const FILE = "src/main/resources/assets/projecthero/lang/en_us.json";

const ENTRIES = {
	"item.projecthero.stormbreaker.ability.bifrost": "Shift + Right-click: the Bifrost menu",
	"item.projecthero.stormbreaker.ability.bifrost2": "  Travel to coordinates or 3 saved",
	"item.projecthero.stormbreaker.ability.bifrost3": "  waypoints, with your squad (60 s)",

	"message.projecthero.bifrost.carried": "%s carried you across the Bifrost",
	"message.projecthero.bifrost.saved": "Bifrost waypoint saved: %s",
	"message.projecthero.bifrost.travelled": "The Bifrost opens -- ready again in %s s",
	"message.projecthero.bifrost.travelled_with": "The Bifrost carries you and %s squadmate(s) -- ready again in %s s",
	"message.projecthero.bifrost.fail.not_holding": "Hold Stormbreaker, and be worthy of it, to open the Bifrost",
	"message.projecthero.bifrost.fail.cooldown": "The Bifrost is recharging -- %s s",
	"message.projecthero.bifrost.fail.wrong_dimension": "That waypoint is in another dimension -- the Bifrost only travels within this one",
	"message.projecthero.bifrost.fail.out_of_bounds": "Those coordinates are outside the world border or build height",
	"message.projecthero.bifrost.fail.no_landing": "No safe place to land at those coordinates",
	"message.projecthero.bifrost.fail.empty_waypoint": "That waypoint is empty",

	"screen.projecthero.bifrost.title": "The Bifrost",
	"screen.projecthero.bifrost.coords": "Destination",
	"screen.projecthero.bifrost.travel": "Open Bifrost",
	"screen.projecthero.bifrost.waypoints": "Waypoints",
	"screen.projecthero.bifrost.name": "Waypoint name",
	"screen.projecthero.bifrost.save": "Save here",
	"screen.projecthero.bifrost.save.tip": "Save where you stand now, under this name",
	"screen.projecthero.bifrost.use": "Go",
	"screen.projecthero.bifrost.clear": "Clear this waypoint",
	"screen.projecthero.bifrost.empty": "Empty",
	"screen.projecthero.bifrost.elsewhere": "Another dimension",
	"screen.projecthero.bifrost.other_dimension": "Saved in %s -- the Bifrost only travels within the dimension you are in",
	"screen.projecthero.bifrost.ready": "Bifrost ready",
	"screen.projecthero.bifrost.recharging": "Recharging -- %s s",

	"projecthero.guide.thor.stormbreaker.bifrost": "Shift + Right-click: open the Bifrost menu. Type X, Y and Z (it starts on where you stand) and press Open Bifrost, or use one of three waypoints: name a slot and press Save here to store where you are standing, Go to travel there, and the cross to clear it. Waypoints are yours for good -- they survive relogging, dying and losing the axe. The rainbow bridge carries you and every squadmate within 8 blocks of you (a squadmate can sneak to stay behind); each keeps their place around you and lands safely -- on solid ground with room to stand, never in a wall, lava or the void (aim at a column with nowhere safe and nothing happens). No distance limit inside the world border, but same dimension only: a waypoint saved in another dimension is greyed out. 1-minute cooldown of its own, shown in the menu -- it no longer holds the throw. Shift + Right-clicking a block within reach with something in your off hand is left alone, so you can still sneak-place blocks.",
};
const REMOVE = ["message.projecthero.bifrost.no_target", "message.projecthero.bifrost.no_room"];
const ANCHOR = "message.projecthero.bifrost.carried";

let text = fs.readFileSync(FILE, "utf8");
const eol = text.includes("\r\n") ? "\r\n" : "\n";
let lines = text.split(/\r?\n/);
const keyOf = (line) => {
	const m = line.match(/^\s*"((?:[^"\\]|\\.)*)"\s*:/);
	return m ? m[1] : null;
};
const lineFor = (key, value) => `  ${JSON.stringify(key)}: ${JSON.stringify(value)},`;

lines = lines.filter((l) => !REMOVE.includes(keyOf(l)));
const pending = [];
for (const [key, value] of Object.entries(ENTRIES)) {
	const i = lines.findIndex((l) => keyOf(l) === key);
	if (i >= 0) {
		const trailingComma = lines[i].trimEnd().endsWith(",");
		lines[i] = lineFor(key, value).replace(/,$/, trailingComma ? "," : "");
	} else {
		pending.push(lineFor(key, value));
	}
}
if (pending.length) {
	const at = lines.findIndex((l) => keyOf(l) === ANCHOR);
	if (at < 0) throw new Error("anchor key missing: " + ANCHOR);
	if (!lines[at].trimEnd().endsWith(",")) throw new Error("anchor is the last key");
	lines.splice(at + 1, 0, ...pending);
}
const out = lines.join(eol);
JSON.parse(out); // still valid JSON
fs.writeFileSync(FILE, out);
console.log(`lang: ${Object.keys(ENTRIES).length - pending.length} updated, ${pending.length} inserted`);
