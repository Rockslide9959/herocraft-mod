// v0.16.7 Rena Rouge Mirage revamp: lang keys. Inserts after anchor lines, keeps CRLF, JSON.parse-checks.
const fs = require("fs");
const f = process.argv[2];
let s = fs.readFileSync(f, "utf8");
const esc = (v) => JSON.stringify(v);

function setValue(key, value) {
	const re = new RegExp(`(\\r\\n  "${key.replace(/\./g, "\\.")}": )"(?:[^"\\\\]|\\\\.)*"`);
	if (!re.test(s)) throw new Error("no key " + key);
	s = s.replace(re, `$1${esc(value)}`);
}

function insertAfter(anchorKey, entries) {
	const lines = s.split("\r\n");
	const i = lines.findIndex((l) => l.startsWith(`  "${anchorKey}":`));
	if (i < 0) throw new Error("no anchor " + anchorKey);
	const add = [];
	for (const [k, v] of entries) {
		if (s.includes(`\r\n  "${k}":`)) throw new Error("duplicate " + k);
		add.push(`  ${esc(k)}: ${esc(v)},`);
	}
	if (!lines[i].endsWith(",")) throw new Error("anchor is last line");
	lines.splice(i + 1, 0, ...add);
	s = lines.join("\r\n");
}

setValue("projecthero.miraculous.ability.rena_rouge_mirage.desc",
	"Opens a wheel of 8 illusions: pick one and play the Flute. The bubble pops into it for 30 s. Starts the 3-minute countdown. 2-minute cooldown, so twice a transformation.");

insertAfter("message.projecthero.rena_rouge.mirage_running", [
	["message.projecthero.rena_rouge.pit_refused", "The ground there won't give: only natural ground you may build on can open."],
	["entity.projecthero.rena_rouge_fox", "Illusion Fox"],
	["entity.projecthero.rena_rouge_mirage_fx", "Mirage"],
	["screen.projecthero.rena_mirage.title", "Mirage"],
	["screen.projecthero.rena_mirage.pick", "Point at an illusion"],
	["screen.projecthero.rena_mirage.hint", "Move the mouse to one and click, or let go of Z. Esc: nothing spent."],
	["screen.projecthero.rena_mirage.foxes", "Army of Foxes"],
	["screen.projecthero.rena_mirage.foxes.hint", "Copies of you, a fake wall and 8 foxes that bite for you."],
	["screen.projecthero.rena_mirage.monster", "Monster"],
	["screen.projecthero.rena_mirage.monster.hint", "A giant beast rears up: everything near panics and runs."],
	["screen.projecthero.rena_mirage.vanish", "Vanishing Act"],
	["screen.projecthero.rena_mirage.vanish.hint", "Mobs lose you and your squad; copies run off to lead them away."],
	["screen.projecthero.rena_mirage.zone", "Mirage Zone"],
	["screen.projecthero.rena_mirage.zone.hint", "Fake fire and cracks where you aim: mobs leave and won't come in."],
	["screen.projecthero.rena_mirage.lure", "Golden Lure"],
	["screen.projecthero.rena_mirage.lure.hint", "A heap of treasure where you aim: every mob near crowds round it."],
	["screen.projecthero.rena_mirage.pitfall", "Pitfall"],
	["screen.projecthero.rena_mirage.pitfall.hint", "A real pit where you aim, hidden by a fake floor. Mobs walk in."],
	["screen.projecthero.rena_mirage.eclipse", "Eclipse"],
	["screen.projecthero.rena_mirage.eclipse.hint", "Night round you: enemies inside can't see past 4 blocks."],
	["screen.projecthero.rena_mirage.mirrors", "Hall of Mirrors"],
	["screen.projecthero.rena_mirage.mirrors.hint", "Every enemy near sees the others as you, and they fight."],
]);

insertAfter("projecthero.guide.rena_rouge.food.body", [
	["projecthero.guide.rena_rouge.mirage", "MIRAGE: the wheel"],
	["projecthero.guide.rena_rouge.mirage.body", "Shift+Z opens a round wheel of eight illusions. Point the mouse at one and click (or let go of Z over it): you play the Flute, the orange bubble floats off and pops, and the illusion runs for 30 seconds. The first one starts the 3-minute countdown. The cooldown is 2 minutes from the pick, so you can cast it twice a transformation. Escape, or closing the wheel, spends nothing. Bosses are fooled too; one that can't really move just stops attacking while it lasts. When an illusion ends it shatters in an orange flash and the mobs it fooled stay dazed (slow, no attacks) for 3 seconds."],
	["projecthero.guide.rena_rouge.mirage_foxes", "Army of Foxes"],
	["projecthero.guide.rena_rouge.mirage_foxes.body", "The classic Mirage: five copies of you that swing when you swing, a wall of fake blocks, and the monsters within 20 blocks confused into fighting each other. A copy that is hit bursts into orange mist that blinds and confuses whoever hit it. Eight illusory foxes leap out too: they chase the nearest enemies, bite for 6, pull the bitten onto themselves, take 3 hits before popping and never touch you or your squad."],
	["projecthero.guide.rena_rouge.mirage_monster", "Monster"],
	["projecthero.guide.rena_rouge.mirage_monster.body", "A huge orange fox-beast rears up beside you, for everyone to see. Every mob within 24 blocks panics: it drops its target and runs from it."],
	["projecthero.guide.rena_rouge.mirage_vanish", "Vanishing Act"],
	["projecthero.guide.rena_rouge.mirage_vanish.body", "You and your squad vanish for the mobs: they lose you and can't pick you again unless you hit them. Three copies of you sprint off in other directions to lead them away."],
	["projecthero.guide.rena_rouge.mirage_zone", "Mirage Zone"],
	["projecthero.guide.rena_rouge.mirage_zone.body", "Where you aim, 12 blocks round, the ground seems to crack and burn. Mobs inside try to get out; mobs outside won't walk or chase into it. Nothing really burns."],
	["projecthero.guide.rena_rouge.mirage_lure", "Golden Lure"],
	["projecthero.guide.rena_rouge.mirage_lure.body", "A dazzling heap of treasure appears where you aim. Every mob within 30 blocks drops its target and crowds round it, staring."],
	["projecthero.guide.rena_rouge.mirage_pitfall", "Pitfall"],
	["projecthero.guide.rena_rouge.mirage_pitfall.body", "A real 4x4 pit, 3 deep, opens where you aim, covered by a fake floor. Mobs near it walk straight across and fall in. It only digs natural ground (never chests, other block entities or bedrock), respects build protection, never opens under you or a squadmate (and lifts you out if you fall in), and fills back exactly when it ends."],
	["projecthero.guide.rena_rouge.mirage_eclipse", "Eclipse"],
	["projecthero.guide.rena_rouge.mirage_eclipse.body", "A dome of night 20 blocks across follows you. Enemies inside are almost blind: they lose track of anything farther than 4 blocks, and their arrows and fireballs fly wide. You and your squad see normally."],
	["projecthero.guide.rena_rouge.mirage_mirrors", "Hall of Mirrors"],
	["projecthero.guide.rena_rouge.mirage_mirrors.body", "A ring of mirrors turns round you. Every enemy within 20 blocks sees the others as you: they turn on each other the whole time and won't go for you or your squad."],
]);

JSON.parse(s);
fs.writeFileSync(f, s);
console.log("ok");
