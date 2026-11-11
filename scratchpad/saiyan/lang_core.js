// v0.17.3 Saiyan revamp -- the core's lang. node scratchpad/saiyan/lang_core.js <path to en_us.json>
// Drops every old Saiyan key except the keep list, then writes the new core keys where the old block stood.
// Normalises line endings (the file is CRLF), refuses duplicate keys, validates the JSON before writing.
const fs = require("fs");
const file = process.argv[2];
const raw = fs.readFileSync(file, "utf8");
const lines = raw.replace(/\r\n/g, "\n").split("\n");

const KEEP = new Set([
	"subtitles.projecthero.saiyan_ki_charge_start", "subtitles.projecthero.saiyan_ki_charge_loop",
	"subtitles.projecthero.saiyan_aura_loop_gold", "subtitles.projecthero.saiyan_aura_loop_red",
	"subtitles.projecthero.saiyan_transform_burst", "subtitles.projecthero.saiyan_power_down",
	"subtitles.projecthero.saiyan_exhausted",
	"item.projecthero.saiyan_blood", "item.projecthero.saiyan_blood.desc1", "item.projecthero.saiyan_blood.desc2",
	"projecthero.hero_tier.saiyan", "projecthero.squad.identity.saiyan",
	"projecthero.saiyan.form.base", "projecthero.saiyan.form.base.short", "projecthero.saiyan.form.ssj", "projecthero.saiyan.form.ssj.short",
	"message.projecthero.saiyan.title", "message.projecthero.saiyan.subtitle", "message.projecthero.saiyan.acquired",
	"message.projecthero.saiyan.already", "message.projecthero.saiyan.no_ki", "message.projecthero.saiyan.cant",
	"projecthero.guide.saiyan.getting.body",
	// v0.17.3 revamp: the kit's move names / descriptions are merged from scratchpad/saiyan/lang_kit.json separately
]);
const isKit = k => /^projecthero\.saiyan\.ability\.(combo|martial|ki_blast|beam|strong|spirit_bomb|dash|rush|aoe|aoe2|utility|utility2)(\.desc)?$/.test(k);

const ADD = {
	"subtitles.projecthero.saiyan_ki_dash": "Saiyan takes off",
	"projecthero.saiyan.ability.charge": "Charge Ki",
	"projecthero.saiyan.ability.charge.desc": "Tap V to charge until your Ki is full (tap again to stop), or hold V to charge while you hold it. 12 Ki a second. You slow right down while charging, and pressing any move stops the charge.",
	"projecthero.saiyan.ability.kaioken": "Kaioken",
	"projecthero.saiyan.ability.kaioken.desc": "Hold Shift+V for 3 seconds to flare up a red aura: 40% more damage, 25% faster movement and attacks. It burns 1 health every 1.5 seconds (no regeneration while it lasts) and ends on its own at 2 health, so it never kills you. Hold Shift+V again, or tap it, to end it. 30 second cooldown after it ends. Not in Super Saiyan.",
	"projecthero.saiyan.ability.kaioken.row": "Shift+hold 3s: Kaioken",
	"projecthero.saiyan.ability.super_saiyan": "Super Saiyan",
	"projecthero.saiyan.ability.super_saiyan.desc": "When your Rage bar is full, press H to transform for exactly 3 minutes: 35% more damage, 20% faster, +4 armour and double Ki regeneration. Afterwards you are exhausted for 60 seconds and your Rage starts again from 0.",
	"projecthero.saiyan.ability.flight": "Flight",
	"projecthero.saiyan.ability.flight.desc": "Double-tap jump in the air. Fly where you look (Sprint to go faster). Costs 0.5 Ki a second and no Ki comes back while airborne; ends at 0 Ki, on landing or on another double-tap.",
	"hud.projecthero.saiyan.ki": "Ki",
	"hud.projecthero.saiyan.rage": "Rage",
	"hud.projecthero.saiyan.exhausted": "Exhausted",
	"hud.projecthero.saiyan.kaioken": "Kaioken",
	"hud.projecthero.saiyan.zenkai": "Zenkai",
	"hud.projecthero.saiyan.strong_foe": "Stronger foe",
	"message.projecthero.saiyan.acquired_hint": "Tap or hold V to charge Ki. Fight to fill your Rage bar, then press H to go Super Saiyan. Hold Shift+V for Kaioken. Double-tap jump to fly.",
	"message.projecthero.saiyan.rage_full": "Your Rage is full! Press H to go Super Saiyan",
	"message.projecthero.saiyan.rage_not_full": "Your Rage isn't full yet (%2$s/100)",
	"message.projecthero.saiyan.already_ssj": "You are already a Super Saiyan",
	"message.projecthero.saiyan.locked_out": "Too exhausted to transform (%1$ss)",
	"message.projecthero.saiyan.ssj_over": "Super Saiyan fades: exhausted for 60 seconds",
	"message.projecthero.saiyan.kaioken_on": "KAIOKEN!",
	"message.projecthero.saiyan.kaioken_off": "Kaioken ends",
	"message.projecthero.saiyan.kaioken_spent": "Kaioken burns out: your body can't take more",
	"message.projecthero.saiyan.kaioken_ssj": "Kaioken can't be used as a Super Saiyan",
	"message.projecthero.saiyan.kaioken_cooldown": "Kaioken is ready in %1$ss",
	"message.projecthero.saiyan.kaioken_weak": "Too hurt to use Kaioken",
	"message.projecthero.saiyan.zenkai": "ZENKAI BOOST! 50% stronger for a minute",
	"message.projecthero.saiyan.strong_foe": "A stronger foe: your Saiyan blood rises to it",
	"projecthero.guide.saiyan": "Saiyan",
	"projecthero.guide.saiyan.tier": "Hero-Tier Primary power",
	"projecthero.guide.saiyan.body": "A warrior race that grows stronger in battle. Your Ki powers every move, your Rage builds as you fight, and a full Rage bar lets you become a Super Saiyan.",
	"projecthero.guide.saiyan.getting": "Getting it",
	"projecthero.guide.saiyan.traits": "A Saiyan body",
	"projecthero.guide.saiyan.traits.body": "While you have the power: 40 max health, 30% faster movement, +7 melee damage, 30% faster attacks, 30% knockback resistance, a 2-block jump, Regeneration I (1 health every 2.5 seconds), and food saturation that lasts 30% longer.",
	"projecthero.guide.saiyan.ki": "Ki",
	"projecthero.guide.saiyan.ki.body": "Your Ki bar holds 100 and pays for all your moves. It comes back at 1 a second (twice as fast as a Super Saiyan, none while flying). Tap V to charge it until full or hold V to charge while held (12 a second). Landing 3 melee hits in a row gives 5 Ki.",
	"projecthero.guide.saiyan.rage": "Rage and Super Saiyan",
	"projecthero.guide.saiyan.rage.body": "Your Rage bar fills as you fight: 2 for every health you lose and 1 for every hit you land, half as much again below half health. Out of combat for 15 seconds it slowly drains. At 100, press H to become a Super Saiyan for exactly 3 minutes: 35% more damage, 20% faster, +4 armour and double Ki regeneration, with golden hair and aura. Rage empties when you transform. When the time is up you are exhausted for 60 seconds, and Rage must fill again from 0.",
	"projecthero.guide.saiyan.kaioken": "Kaioken",
	"projecthero.guide.saiyan.kaioken.body": "Hold Shift+V for 3 seconds: a red aura, 40% more damage, 25% faster movement and attacks. It burns 1 health every 1.5 seconds and ends by itself at 2 health. Hold Shift+V again (or tap it) to end it early. 30 second cooldown. It can't be used as a Super Saiyan, and transforming ends it.",
	"projecthero.guide.saiyan.zenkai": "Zenkai Boost",
	"projecthero.guide.saiyan.zenkai.body": "Stay below 3 hearts for 10 seconds and you heal 3 hearts and become 50% stronger (melee and Ki moves) for 1 minute. Then it needs 5 minutes to come back. The HUD shows it.",
	"projecthero.guide.saiyan.strong": "A stronger foe",
	"projecthero.guide.saiyan.strong.body": "Fighting a boss, or anything with at least 1.5 times your max health, makes you take 10% less damage, regain Ki 10% faster and hit 10% harder in melee, for 10 seconds after you last traded blows. The HUD shows \"Stronger foe\".",
	"projecthero.guide.saiyan.flight": "Flight",
	"projecthero.guide.saiyan.flight.body": "Double-tap jump in the air to fly where you look (Sprint to go faster, 20% faster as a Super Saiyan). It costs 0.5 Ki a second and ends at 0 Ki, on landing or on another double-tap. No fall damage while flying.",
	"projecthero.guide.saiyan.controls": "Controls",
	"projecthero.guide.saiyan.commands": "Commands",
	"projecthero.guide.saiyan.commands.body": "Operators: /projecthero power tune saiyan ki <0-100>, rage <0-100>, ssj (transform now), end_ssj, kaioken.",
};

const keyOf = l => { const m = /^\s*"((?:[^"\\]|\\.)*)"\s*:/.exec(l); return m ? m[1] : null; };
// duplicate check on the original
const seen = new Map();
lines.forEach((l, i) => { const k = keyOf(l); if (k) { if (seen.has(k)) throw new Error("duplicate key already in file: " + k); seen.set(k, i); } });

let insertAt = -1;
const out = [];
for (const l of lines) {
	const k = keyOf(l);
	if (k && /saiyan/i.test(k) && !KEEP.has(k) && !isKit(k)) {
		if (insertAt < 0) insertAt = out.length;
		continue;
	}
	if (k && ADD[k] !== undefined) throw new Error("new key already present (kept): " + k);
	out.push(l);
}
if (insertAt < 0) throw new Error("no Saiyan block found");
const esc = v => JSON.stringify(v);
const added = Object.entries(ADD).map(([k, v]) => "  " + JSON.stringify(k) + ": " + esc(v) + ",");
out.splice(insertAt, 0, ...added);
// fix a trailing comma before the closing brace if the block ended the file
let text = out.join("\n").replace(/,(\s*\n\s*\})/, "$1");
const parsed = JSON.parse(text); // validates
const n = Object.keys(parsed).length;
const keyCount = text.split("\n").filter(keyOf).length;
if (n !== keyCount) throw new Error("duplicate keys after edit: " + keyCount + " lines vs " + n + " keys");
fs.writeFileSync(file, raw.includes("\r\n") ? text.replace(/\n/g, "\r\n") : text);
console.log("ok, keys:", n, "added:", added.length);
