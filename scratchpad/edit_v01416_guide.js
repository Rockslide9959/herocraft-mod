// v0.14.16 guide: Stark Sorting Station chapter + a Flight section in the overview.
const fs=require("fs");
function edit(f,pairs){let s=fs.readFileSync(f,"utf8");const crlf=s.includes("\r\n");s=s.replace(/\r\n/g,"\n");
for(const [a,b] of pairs){if(!s.includes(a))throw new Error(f+" missing "+a.slice(0,80));s=s.replace(a,b);}
if(crlf)s=s.replace(/\n/g,"\r\n");fs.writeFileSync(f,s);}
const G="src/main/java/com/projecthero/mod/hero/guide/HeroPackGuide.java";
edit(G,[
["	private static final int CH_HORDES = 26;\n	private static final int CHAPTER_POWER_BASE = 27;",
 "	private static final int CH_HORDES = 26;\n	private static final int CH_STARK_SORTER = 27;\n	private static final int CHAPTER_POWER_BASE = 28;"],
["			lines.add(Component.translatable(\"projecthero.guide.controls.squad\").withStyle(ChatFormatting.GRAY));\n		}));",
 "			lines.add(Component.translatable(\"projecthero.guide.controls.squad\").withStyle(ChatFormatting.GRAY));\n			// v0.14.16: every flight in the mod steers the same way now\n			blank(lines);\n			head(lines, \"projecthero.guide.flight\");\n			para(lines, \"projecthero.guide.flight.body\");\n		}));"],
["				para(lines, \"projecthero.guide.hordes.\" + section + \".body\");\n			}\n		}));",
 "				para(lines, \"projecthero.guide.hordes.\" + section + \".body\");\n			}\n		}));\n		// v0.14.16: the Stark Sorting Station\n		out.add(chapter(\"projecthero.guide.stark_sorter\", lines -> {\n			para(lines, \"projecthero.guide.stark_sorter.body\");\n			for (String section : new String[]{\"recipe\", \"use\", \"plan\", \"safety\"}) {\n				blank(lines);\n				head(lines, \"projecthero.guide.stark_sorter.\" + section);\n				para(lines, \"projecthero.guide.stark_sorter.\" + section + \".body\");\n			}\n		}));"],
["		link(idx, \"projecthero.guide.devices\", CH_DEVICES);",
 "		link(idx, \"projecthero.guide.devices\", CH_DEVICES);\n		link(idx, \"projecthero.guide.stark_sorter\", CH_STARK_SORTER);"]]);
edit("src/gametest/java/com/projecthero/mod/gametest/HeroPackGameTests.java",[
["		// Apokolips Invasion, Moon Knight, Super Soldier, Kryptonian, Horde Blocks)",
 "		// Apokolips Invasion, Moon Knight, Super Soldier, Kryptonian, Horde Blocks, Stark Sorting Station)"],
["chapters.size() == 27 + Powers.enabled().size()","chapters.size() == 28 + Powers.enabled().size()"]]);
// lang
const keys={
"projecthero.guide.flight":"Flying",
"projecthero.guide.flight.body":"Every hero flight steers the same way (v0.14.16). Take off with your power's usual gesture (usually a double-tap of Jump). W flies toward where you are looking and S flies backward, away from it -- look up to climb, down to dive. A and D slide sideways, Space rises and Sneak sinks straight down. Let go of everything and you ease into a steady hover: no falling, no drift. Sprint flies faster, and some powers add their own boost (Green Lantern: Sneak+Sprint; Kryptonian: X in the air; the Flight mutation: keep sprinting to climb its speed tiers). Your flight's energy or stamina bar still applies.",
"projecthero.guide.stark_sorter":"Stark Sorting Station",
"projecthero.guide.stark_sorter.body":"A Stark-tech storage helper (v0.14.16). Fill its 54-slot store, press Sort, and a little red-and-gold Stark Sorter Bot launches from the pad and files everything away into the chests around it -- slowly enough that you can watch it work.",
"projecthero.guide.stark_sorter.recipe":"Crafting",
"projecthero.guide.stark_sorter.recipe.body":"Top row: Gold Ingot, Basic Circuit, Gold Ingot. Middle row: Mechanical Part, Chest, Mechanical Part. Bottom row: Iron Ingot, Block of Redstone, Iron Ingot. Anyone can use it -- no power needed.",
"projecthero.guide.stark_sorter.use":"Using it",
"projecthero.guide.stark_sorter.use.body":"Place it near your storage and right-click it. Put in the items you want tidied and press Sort. The bot scans every chest, trapped chest and barrel within 10 blocks (a double chest counts as one), flies to each, opens the lid and files up to 3 stacks per trip. A packed station takes about a minute; the screen shows its progress. Pressing Sort while it works just shows the status.",
"projecthero.guide.stark_sorter.plan":"How it decides",
"projecthero.guide.stark_sorter.plan.body":"The more containers it can reach, the finer it sorts. With 1-3 it uses four broad groups (Blocks / Tools & Combat / Food & Farming / Misc); with more it splits into up to 13 categories (Stone & Building, Wood, Decoration, Combat, Tools, Food, Farming & Plants, Ores & Minerals, Redstone, Mob Drops, Potions & Brewing, Project Hero Gear, Misc), and your biggest categories get extra chests. It keeps each chest's existing theme -- a chest that is mostly food stays the food chest -- and sends items to a chest already holding the same item first.",
"projecthero.guide.stark_sorter.safety":"Overflow and safety",
"projecthero.guide.stark_sorter.safety.body":"When a chest fills, the next chest of that category is used, then any chest with room. Anything that fits nowhere stays in the station and the bot tells you how much. Nothing is ever lost or duplicated: breaking the station drops its store and whatever the bot was carrying, and if the bot is unloaded mid-job its cargo goes straight back into the station."};
require("./langset.js")([{anchor:"projecthero.guide.controls.squad",entries:Object.fromEntries(Object.entries(keys).filter(([k])=>k.startsWith("projecthero.guide.flight")))},{anchor:"projecthero.guide.hordes.commands.body",entries:Object.fromEntries(Object.entries(keys).filter(([k])=>k.startsWith("projecthero.guide.stark")))}]);
require("./langset.js")([{entries:{"projecthero.guide.hordes.rewards.body":"Every chest holds diamonds, gold, iron, golden apples, emeralds and bottles of enchanting, and the harder the horde the more you get. Zombie: 3-6 diamonds, a 15% enchanted golden apple and an enchanted book. Skeleton: 5-9 diamonds, an enchanted bow, a stack of arrows, a 35% enchanted golden apple and a book. Spider: 8-14 diamonds, netherite scrap, a guaranteed enchanted golden apple (40% for two), a 50% totem of undying, a 30% block of diamond and two books. More surviving fighters fill the chest further. Since v0.14.16 the loot is scattered through the whole chest in split stacks, like a dungeon chest."}}]);
console.log("guide ok");
