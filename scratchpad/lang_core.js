// v0.16.7 core: inserts the core Quirk lang block right after "message.projecthero.power.erased" (CRLF kept, no dupes)
const fs = require("fs");
const FILE = "src/main/resources/assets/projecthero/lang/en_us.json";
const ANCHOR = "\"message.projecthero.power.erased\"";

const Q = {
	hellflame: ["Hellflame", "Endeavor", "Your body burns hotter than any flame. Every blast builds Heat; let it vent or you Overheat."],
	overhaul: ["Overhaul", "Overhaul", "Touch to take apart, touch to put back together. Moves spend Focus; touch moves need an empty hand."],
	engine: ["Engine", "Ingenium", "Engines in your calves: Speed I and step assist, always. Push the Recipro moves too hard and you Stall."],
	dark_shadow: ["Dark Shadow", "Tsukuyomi", "A living shadow at your shoulder, weak in light and strong in the dark. Full Shadow in the dark: it goes Berserk."],
	erasure: ["Erasure", "Eraser Head", "Stare to cancel a power, any power: players, mobs, a boss's special moves. Dry Eyes drains while you stare."],
	explosion: ["Explosion", "Ground Zero", "Your sweat is like nitroglycerin. More Sweat, bigger blasts; overdo the big ones and your arms strain."],
	zero_gravity: ["Zero Gravity", "Uravity", "Touch to make things weightless. Floating weight builds Nausea; at full you get sick and everything drops."],
};

const add = {};
for (const [id, [name, hero, intro]] of Object.entries(Q)) {
	add["projecthero.hero_tier.quirk_" + id] = name + " (" + hero + ")";
	add["projecthero.quirk." + id + ".intro"] = intro;
	add["item.projecthero.quirk_serum_" + id] = "Quirk Serum: " + name;
}
Object.assign(add, {
	"item.projecthero.quirk_serum_random": "Quirk Trigger",
	"item.projecthero.quirk_serum.hint": "Awakens the %s Quirk (%s).",
	"item.projecthero.quirk_serum_random.hint": "Awakens a random Quirk you don't have.",
	"item.projecthero.quirk_serum.rule": "Replaces your Primary power.",
	"message.projecthero.quirk.serum_nothing": "Nothing happens. You already have that Quirk.",
	"effect.projecthero.erased": "Erased",
	"hud.projecthero.erased": "ERASED",
	"hud.projecthero.erased.sub": "Your power has been erased",
	"hud.projecthero.erased.timed": "Your power has been erased (%ss)",
	"hud.projecthero.quirk.overloaded": "OVERLOADED %ss",
	"screen.projecthero.power_info.quirk.meter": "Meter: %s",
	"screen.projecthero.power_info.quirk.costume": "Your hero costume is drawn over your skin.",
	"screen.projecthero.power_info.quirk.shift": "Shift+%s",
	"message.projecthero.calling.quirk_title": "A Quirk awakens in you: %s!",
	"message.projecthero.calling.quirk_body": "Your %s Quirk stirs. Will you rise as %s?",
	"message.projecthero.calling.quirk_refused": "The feeling fades. Your Quirk stays dormant.",
	"message.projecthero.calling.quirk_left": "The feeling faded. Your Quirk stays dormant.",
});

let s = fs.readFileSync(FILE, "utf8");
const nl = s.includes("\r\n") ? "\r\n" : "\n";
const existing = JSON.parse(s);
const lines = s.split(nl);
const i = lines.findIndex((l) => l.trimStart().startsWith(ANCHOR));
if (i < 0) throw new Error("anchor missing");
const block = [];
for (const [k, v] of Object.entries(add)) {
	if (k in existing) {
		console.log("skip existing", k);
		continue;
	}
	block.push("  " + JSON.stringify(k) + ": " + JSON.stringify(v) + ",");
}
if (!lines[i].trimEnd().endsWith(",")) lines[i] = lines[i] + ",";
// if the anchor was the last entry, the block's final comma must go
const isLast = lines.slice(i + 1).every((l) => l.trim() === "" || l.trim() === "}");
if (isLast && block.length) block[block.length - 1] = block[block.length - 1].replace(/,$/, "");
lines.splice(i + 1, 0, ...block);
const out = lines.join(nl);
JSON.parse(out);
fs.writeFileSync(FILE, out);
console.log("added", block.length);
