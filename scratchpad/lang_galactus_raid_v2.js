// v0.17.2: Galactus raid v2 lang (node scratchpad/lang_galactus_raid_v2.js, from the repo root). Sets or adds each key.
const fs = require("fs");
const f = "src/main/resources/assets/projecthero/lang/en_us.json";
let s = fs.readFileSync(f, "utf8");
const anchor = '  "title.projecthero.galactus.nullified"';
const set = {
	"entity.projecthero.devourer": "Galactus",
	"entity.projecthero.devourer_part": "Galactus",
	"item.projecthero.cosmic_cell": "Cosmic Cell",
	"item.projecthero.cosmic_cell.hint": "Right-click an unshielded Element Converter to overload it. You can't fly while you carry it, and a hard hit knocks it loose.",
	"item.projecthero.cosmic_shard": "Cosmic Shard",
	"item.projecthero.cosmic_shard.hint": "A splinter of the Devourer's fist. Four of them and a beacon make a Herald's Beacon.",
	"hud.projecthero.galactus.skip": "Hold Sneak to skip",

	"subtitle.projecthero.galactus.herald": "\"This world has been chosen.\"",
	"subtitle.projecthero.galactus.hunger": "\"I HUNGER.\"",
	"subtitle.projecthero.galactus.surfer_turns": "\"I have served him a thousand worlds. Not this one.\"",
	"subtitle.projecthero.galactus.rise": "\"YOU WOULD DENY ME? THEN BE DEVOURED WITH IT.\"",
	"subtitle.projecthero.galactus.ending1": "\"THE NULLIFIER... NO...\"",
	"subtitle.projecthero.galactus.ending2": "\"The world is yours. Guard it well.\"",
	"subtitle.projecthero.galactus.consumed": "\"THIS WORLD IS FINISHED.\"",

	"title.projecthero.galactus.kneel": "THE HERALD KNEELS",
	"title.projecthero.galactus.kneel.sub": "Something far bigger is coming",
	"title.projecthero.galactus.stage1": "STAGE 1: THE SHATTERED ORBIT",
	"title.projecthero.galactus.stage1.sub": "Dodge his fists. Hit them while they're stuck.",
	"title.projecthero.galactus.stage2": "STAGE 2: THE FEAST",
	"title.projecthero.galactus.stage2.sub": "Carry Cosmic Cells into his converters and overload them",
	"title.projecthero.galactus.stage3": "STAGE 3: THE DEVOURER",
	"title.projecthero.galactus.stage3.sub": "Head x2, break his gauntlets, open his core",
	"title.projecthero.galactus.stagger": "STAGGERED! %s / %s",
	"title.projecthero.galactus.consumption": "World Consumption %s%%",
	"title.projecthero.galactus.consumption.sub": "The Earth is being drained",
	"title.projecthero.galactus.cracker": "PLANET CRACKER",
	"title.projecthero.galactus.cracker.sub": "Break BOTH of his hands before it fires!",
	"title.projecthero.galactus.cracker_broken": "THE SPHERE COLLAPSES",
	"title.projecthero.galactus.devour": "DEVOUR",
	"title.projecthero.galactus.devour.sub": "Run, hide behind a pillar, or hit his head to choke him",
	"title.projecthero.galactus.gag": "HE'S CHOKING!",
	"title.projecthero.galactus.gag.sub": "His head is down: triple damage",
	"title.projecthero.galactus.gauntlet_r": "RIGHT GAUNTLET BROKEN",
	"title.projecthero.galactus.gauntlet_l": "LEFT GAUNTLET BROKEN",
	"title.projecthero.galactus.core_open": "HIS CORE IS OPEN",
	"title.projecthero.galactus.core_open.sub": "Hit his chest: x1.5 damage",
	"title.projecthero.galactus.cracked": "HIS HELMET CRACKS",
	"title.projecthero.galactus.cracked.sub": "He's furious, and faster",
	"title.projecthero.galactus.nullifier": "THE ULTIMATE NULLIFIER",
	"title.projecthero.galactus.nullifier.sub": "Hold right-click on his head for 4 seconds. Protect the holder!",

	"event.projecthero.galactus.bar.arrival_v2": "The Herald approaches",
	"event.projecthero.galactus.bar.devourer": "The Devourer wakes",
	"event.projecthero.galactus.bar.orbit": "Galactus: The Devourer Descends",
	"event.projecthero.galactus.bar.stage1": "Stage 1: The Shattered Orbit | Staggers %s / %s",
	"event.projecthero.galactus.bar.stage2": "Stage 2: The Feast | World Consumption %s%% | Converters reversed %s / %s",
	"event.projecthero.galactus.bar.stage3": "Stage 3: The Devourer",
	"event.projecthero.galactus.bar.cracker": "PLANET CRACKER | right hand %s%% | left hand %s%%",
	"event.projecthero.galactus.bar.devour": "DEVOUR | hit his head to choke him!",
	"event.projecthero.galactus.bar.finale": "THE ULTIMATE NULLIFIER | aim at his head",

	"message.projecthero.galactus.grave_returned": "Your belongings were kept safe in the orbit and returned to you.",
	"message.projecthero.galactus.fist_stuck": "HIS FIST IS STUCK! HIT IT!",
	"message.projecthero.galactus.sweep": "BACKHAND! Get off that row!",
	"message.projecthero.galactus.sees_you": "GALACTUS SEES YOU. Get down!",
	"message.projecthero.galactus.cell_spawned": "A Cosmic Cell broke off a feed beam!",
	"message.projecthero.galactus.carrier": "You carry a Cosmic Cell: take it to an unshielded converter",
	"message.projecthero.galactus.cell_dropped": "The Cosmic Cell was knocked out of your hands!",
	"message.projecthero.galactus.need_cell": "It needs a Cosmic Cell",
	"message.projecthero.galactus.overloading": "%s socketed a Cosmic Cell: defend the converter!",
	"message.projecthero.galactus.converter_reversed": "A converter's beam turns on him! %s / %s reversed",
	"message.projecthero.galactus.converter_cell": "Weapons won't do it: overload it with a Cosmic Cell",
	"message.projecthero.galactus.core_sealed": "Sealed: break both gauntlets first",
	"message.projecthero.galactus.grabbed": "Caught in his hand! Your allies must hit the hand to free you, and you can hit his face.",
	"message.projecthero.galactus.grab_ally": "%s is in his grip: hit the hand!",
	"message.projecthero.galactus.freed": "Free!",
	"message.projecthero.galactus.nullifier_given": "The Surfer gives you the Ultimate Nullifier: hold right-click on his head!",
	"message.projecthero.galactus.nullifier_to": "The Silver Surfer hands the Ultimate Nullifier to %s. Protect them!",
	"message.projecthero.galactus.rift": "The Rift lifts you into orbit",
	"message.projecthero.galactus.consumed": "The Earth was drained. Galactus has fed.",
	"message.projecthero.galactus.worldbreaker": "%s turned the Ultimate Nullifier on Galactus. The world is saved.",

	"boss.projecthero.galactus.surfer.say.kneel": "You fight... for a world already lost. He is here.",
	"boss.projecthero.galactus.surfer.say.catch": "...Not yet.",
	"boss.projecthero.galactus.surfer.say.turn_orbit": "I have served him a thousand worlds. Not this one. I fight with you.",
	"boss.projecthero.galactus.surfer.say.nullifier": "Now! I have made the Nullifier whole from the parts you won. End this!",
	"boss.projecthero.galactus.galactus.say.hunger": "I HUNGER.",
	"boss.projecthero.galactus.galactus.say.stage1": "STAND ON YOUR LITTLE STONES. I WILL BREAK THEM ALL.",
	"boss.projecthero.galactus.galactus.say.stagger1": "INSECTS. YOU STING.",
	"boss.projecthero.galactus.galactus.say.stagger2": "ENOUGH OF THIS.",
	"boss.projecthero.galactus.galactus.say.stagger3": "THEN I WILL FEED, AND YOU WILL WATCH.",
	"boss.projecthero.galactus.galactus.say.rise": "YOU WOULD DENY ME? THEN BE DEVOURED WITH IT.",
	"boss.projecthero.galactus.galactus.say.finale": "NO... NOT BY SUCH AS YOU...",
	"boss.projecthero.galactus.galactus.say.consumed": "THIS WORLD IS FINISHED."
};
for (const [k, v] of Object.entries(set)) {
	const re = new RegExp('(  "' + k.replace(/\./g, "\\.") + '": )"(?:[^"\\\\]|\\\\.)*"');
	if (re.test(s)) s = s.replace(re, (_, a) => a + JSON.stringify(v));
	else s = s.replace(anchor, '  "' + k + '": ' + JSON.stringify(v) + ',\n' + anchor);
}
JSON.parse(s);
fs.writeFileSync(f, s);
console.log("ok");
